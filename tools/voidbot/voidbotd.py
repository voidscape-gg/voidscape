"""voidbotd — headless voidscape session daemon.

Holds one logged-in game connection, decodes server packets into a live GameState,
and serves a JSON-lines control socket (TCP loopback) so the `voidbot` CLI can issue
actions, query state, and block on conditions. No GUI, no mouse, no screenshots.

Start:  python voidbotd.py --host 127.0.0.1 --game-port 43596 --ctrl-port 18900 \
                          --user wbtest --pass voidtest123 [--defs <repo>/server/conf/server/defs]
Protocol details + validation: tools/voidbot/protocol.py, docs/bot-api.md.
"""
import argparse
import json
import os
import re
import socket
import sys
import threading
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import protocol as P

SKILL_NAMES = ["attack", "defense", "strength", "hits", "ranged", "prayer", "magic",
               "cooking", "woodcutting", "fletching", "fishing", "firemaking",
               "crafting", "smithing", "mining", "herblaw", "agility", "thieving",
               "runecraft", "harvesting"]


def load_item_defs(defs_dir):
    """Return (stackable_set, name_by_id) from the server item defs."""
    stackable, names = set(), {}
    for fn in ("ItemDefs.json", "ItemDefsCustom.json", "ItemDefsPatch18.json"):
        path = os.path.join(defs_dir, fn)
        if not os.path.exists(path):
            continue
        data = json.load(open(path, encoding="utf-8"))
        if isinstance(data, dict):
            items = data.get("item", next(iter(data.values()), []))
        else:
            items = data
        if isinstance(items, dict):
            items = list(items.values())
        for it in items:
            if not isinstance(it, dict) or "id" not in it:
                continue
            iid = it["id"]
            names[iid] = it.get("name", "")
            if it.get("isStackable"):
                stackable.add(iid)
    return stackable, names


class GameState:
    def __init__(self):
        self.lock = threading.RLock()
        self.connected = False
        self.logged_in = False
        self.login_response = None
        self.username = None
        self.x = None
        self.y = None
        self.fatigue = 0
        self.skills = {}            # name -> {cur,max,xp}
        self.quest_points = 0
        self.inventory = []         # [{slot,id,name,amount,wielded,noted}]
        self.equipment = []
        self.bank_open = False
        self.bank = []              # [{id,amount,name}]
        self.shop_open = False
        self.dialog_open = False
        self.dialog_options = []    # list[str]
        self.input_open = False     # server SEND_INPUT_BOX prompt is showing
        self.input_prompt = ""
        self.npcs = []              # ordered [{server_index,id,x,y}]
        self.npc_seen = {}          # server_index -> {id,x,y,t}; smooths decode flicker
        self.ground_items = []      # [{id,x,y}]
        self.messages = []          # [{seq,t,text,type}]
        self.events = []            # [{seq,t,kind,...}]
        self._seq = 0

    def event(self, kind, **kw):
        self._seq += 1
        e = {"seq": self._seq, "t": round(time.time(), 3), "kind": kind, **kw}
        self.events.append(e)
        if len(self.events) > 5000:
            self.events = self.events[-3000:]
        return e


class Daemon:
    def __init__(self, args):
        self.args = args
        self.st = GameState()
        self.conn = None
        self.send_lock = threading.Lock()
        self.last_write = 0
        self.stackable, self.names = (set(), {})
        if args.defs and os.path.isdir(args.defs):
            self.stackable, self.names = load_item_defs(args.defs)
        self.running = True

    # ---------------- connection / login ----------------
    def connect_and_login(self):
        # The real client uses TWO connections: a throwaway one to fetch server
        # configs (RSA key), which the server closes, then a fresh one for login +
        # the game session.
        cfg = P.Connection(self.args.host, self.args.game_port)
        cfg.send_packet(19)  # config request
        exp = mod = None
        deadline = time.time() + 15
        while time.time() < deadline:
            op, payload = cfg.recv_packet()
            if op == 19:
                exp, mod = self._parse_rsa(payload)
                break
        cfg.close()
        if not mod:
            raise RuntimeError("did not receive RSA key in config response")

        # Retry transient rejections: ACCOUNT_LOGGEDIN (4) and IP_IN_USE (6) clear a
        # few seconds after a previous session's channel closes and is unregistered.
        resp = None
        for attempt in range(6):
            c = P.Connection(self.args.host, self.args.game_port)
            body = P.build_login(self.args.user, self.args.password, exp, mod)
            c.send_packet(body[0], body[1:])
            try:
                resp = c.recv_byte()
            except (ConnectionError, OSError):
                resp = -1
            if resp & 0x40 if isinstance(resp, int) and resp >= 0 else False:
                self.conn = c
                break
            c.close()
            if resp in (4, 6):           # already-logged-in / ip-in-use: wait + retry
                time.sleep(3)
                continue
            raise RuntimeError("login failed, response=%d" % resp)
        else:
            raise RuntimeError("login failed after retries, last response=%s" % resp)
        with self.st.lock:
            self.st.connected = True
            self.st.login_response = resp
            self.st.username = self.args.user
            self.st.logged_in = bool(resp & 0x40)
        self.st.event("login", response=resp, ok=True)
        self.last_write = time.time()

    @staticmethod
    def _parse_rsa(payload):
        """Exponent then modulus are the last two \\n-terminated decimal strings."""
        nums = re.findall(rb"\d{4,}", payload)
        if len(nums) < 2:
            return None, None
        # modulus is the long one (154 digits); exponent the short one before it
        mod = int(nums[-1]); exp = int(nums[-2])
        return exp, mod

    # ---------------- sending ----------------
    def send(self, name, payload=b""):
        if self.conn is None:
            raise RuntimeError("not connected yet")
        op = P.OUT[name]
        with self.send_lock:
            self.conn.send_packet(op, payload)
            self.last_write = time.time()

    def heartbeat(self):
        while self.running:
            time.sleep(1)
            try:
                if self.st.logged_in and time.time() - self.last_write > 4.5:
                    self.send("PING")
            except Exception:
                break

    # ---------------- receive / decode ----------------
    def recv_loop(self):
        try:
            while self.running:
                op, payload = self.conn.recv_packet()
                if op is None:
                    continue
                try:
                    self.decode(op, payload)
                except Exception as e:
                    self.st.event("decode_error", opcode=op, error=str(e))
        except (ConnectionError, OSError) as e:
            with self.st.lock:
                self.st.connected = False
                self.st.logged_in = False
            self.st.event("disconnect", error=str(e))

    def decode(self, op, p):
        name = P.IN.get(op)
        st = self.st
        if name == "PLAYER_COORDS":
            br = P.BitReader(p)
            x = br.bits(11); y = br.bits(13); br.bits(4)
            with st.lock:
                moved = (x, y) != (st.x, st.y)
                st.x, st.y = x, y
            if moved:
                st.event("move", x=x, y=y)
        elif name == "NPC_COORDS":
            self._decode_npcs(p)
        elif name == "SET_INVENTORY":
            self._decode_inventory_full(p)
        elif name == "SET_INVENTORY_SLOT":
            self._decode_inventory_slot(p)
        elif name == "REMOVE_INVENTORY_SLOT":
            slot = p[0] if p else -1
            with st.lock:
                st.inventory = [it for it in st.inventory if it["slot"] != slot]
            st.event("inventory_remove", slot=slot)
        elif name == "SET_STATS":
            self._decode_stats(p)
        elif name == "UPDATE_STAT":
            self._decode_stat_one(p)
        elif name == "UPDATE_XP":
            if len(p) >= 5:
                sid = p[0]; xp = int.from_bytes(p[1:5], "big") // 4
                with st.lock:
                    nm = SKILL_NAMES[sid] if sid < len(SKILL_NAMES) else str(sid)
                    st.skills.setdefault(nm, {"cur": 0, "max": 0, "xp": 0})["xp"] = xp
                st.event("xp", skill=nm, xp=xp)
        elif name == "SEND_MESSAGE":
            self._decode_message(p)
        elif name in ("DIALOGUE_MENU",):
            self._decode_menu(p)
        elif name == "INPUT_BOX":
            prompt = p.split(b"\n")[0].decode("latin1", "replace") if p else ""
            with st.lock:
                st.input_open = True; st.input_prompt = prompt
            st.event("input_box", prompt=prompt)
        elif name == "DISABLE_OPTION_MENU":
            with st.lock:
                st.dialog_open = False; st.dialog_options = []
            st.event("dialog_close")
        elif name == "OPEN_BANK":
            self._decode_bank(p)
        elif name == "BANK_UPDATE":
            self._decode_bank_update(p)
        elif name == "CLOSE_BANK":
            with st.lock:
                st.bank_open = False
            st.event("bank_close")
        elif name == "SHOP_OPEN":
            with st.lock:
                st.shop_open = True
        elif name == "EXIT_SHOP":
            with st.lock:
                st.shop_open = False
        elif name == "GROUNDITEM_HANDLER":
            self._decode_grounditems(p)
        elif name == "FATIGUE":
            if len(p) >= 2:
                with st.lock:
                    st.fatigue = int.from_bytes(p[0:2], "big")
        elif name == "DEATH":
            st.event("death")
        elif name in ("CLOSE_CONNECTION", "CLOSE_CONNECTION_NOTIFY"):
            with st.lock:
                st.logged_in = False
            st.event("logout")

    def _decode_npcs(self, p):
        br = P.BitReader(p)
        st = self.st
        with st.lock:
            cache = list(st.npcs)
        count = br.bits(8)
        new_list = []
        total_bits = len(p) * 8
        for i in range(count):
            npc = cache[i] if i < len(cache) else {"server_index": -1, "id": -1, "x": 0, "y": 0}
            if br.bits(1) != 0:
                if br.bits(1) != 0:           # animation update
                    off = br.bits(2)
                    if off == 3:
                        continue              # removed / dead -> not re-added
                    br.bits(2)
                else:                         # movement
                    d = br.bits(3)
                    nx, ny = npc["x"], npc["y"]
                    if d in (1, 2, 3): nx += 1
                    if d in (5, 6, 7): nx -= 1
                    if d in (3, 4, 5): ny += 1
                    if d in (0, 1, 7): ny -= 1
                    npc = dict(npc, x=nx, y=ny)
            new_list.append(npc)
        while total_bits > br.bitpos + 34:
            sidx = br.bits(12)
            dx = br.bits(6);  dx -= 64 if dx > 31 else 0
            dy = br.bits(6);  dy -= 64 if dy > 31 else 0
            br.bits(4)                        # sprite/dir
            nid = br.bits(10)
            base_x = st.x if st.x is not None else 0
            base_y = st.y if st.y is not None else 0
            new_list.append({"server_index": sidx, "id": nid,
                             "x": base_x + dx, "y": base_y + dy})
        now_t = time.time()
        with st.lock:
            prev = {n["server_index"] for n in st.npcs}
            now = {n["server_index"] for n in new_list}
            st.npcs = new_list
            for n in new_list:
                if n["server_index"] >= 0 and 0 <= n["id"] < 1600:
                    st.npc_seen[n["server_index"]] = {
                        "id": n["id"], "x": n["x"], "y": n["y"], "t": now_t}
            st.npc_seen = {k: v for k, v in st.npc_seen.items()
                           if now_t - v["t"] <= 3.0}
        for gone in prev - now:
            st.event("npc_removed", server_index=gone)

    def _recent_npcs(self):
        """NPCs seen within ~3s, smoothing single-frame decode drops."""
        now_t = time.time()
        with self.st.lock:
            return [{"server_index": si, "id": v["id"], "x": v["x"], "y": v["y"]}
                    for si, v in self.st.npc_seen.items() if now_t - v["t"] <= 3.0]

    def _item_record(self, slot, raw_id, noted, br_amount_fn):
        wielded = bool(raw_id & 0x8000)
        iid = raw_id & 0x7FFF
        amount = 1
        if iid in self.stackable or noted:
            amount = br_amount_fn()
        return {"slot": slot, "id": iid, "name": self.names.get(iid, ""),
                "amount": amount, "wielded": wielded, "noted": bool(noted)}

    def _decode_inventory_full(self, p):
        i = 0
        size = p[i]; i += 1
        inv = []
        for slot in range(size):
            raw = int.from_bytes(p[i:i+2], "big"); i += 2
            wielded = bool(raw & 0x8000); iid = raw & 0x7FFF
            noted = p[i]; i += 1
            amount = 1
            if iid in self.stackable or noted:
                amount = int.from_bytes(p[i:i+4], "big"); i += 4
            inv.append({"slot": slot, "id": iid, "name": self.names.get(iid, ""),
                        "amount": amount, "wielded": wielded, "noted": bool(noted)})
        with self.st.lock:
            self.st.inventory = inv
        self.st.event("inventory_set", count=len(inv))

    def _decode_inventory_slot(self, p):
        slot = p[0]
        # Null marker (item removed): slot + short(0) + short(0) + int(0) = 9 bytes total.
        if len(p) >= 9 and int.from_bytes(p[1:3], "big") == 0 and int.from_bytes(p[3:5], "big") == 0:
            with self.st.lock:
                self.st.inventory = [it for it in self.st.inventory if it["slot"] != slot]
            self.st.event("inventory_update", slot=slot, removed=True)
            return
        i = 1
        raw = int.from_bytes(p[i:i+2], "big"); i += 2
        wielded = bool(raw & 0x8000); iid = raw & 0x7FFF
        noted = p[i]; i += 1
        amount = 1
        if iid in self.stackable or noted:
            amount = int.from_bytes(p[i:i+4], "big"); i += 4
        rec = {"slot": slot, "id": iid, "name": self.names.get(iid, ""),
               "amount": amount, "wielded": wielded, "noted": bool(noted)}
        with self.st.lock:
            inv = [it for it in self.st.inventory if it["slot"] != slot]
            inv.append(rec); inv.sort(key=lambda r: r["slot"])
            self.st.inventory = inv
        self.st.event("inventory_update", slot=slot, id=iid, amount=amount)

    def _decode_stats(self, p):
        n = len(SKILL_NAMES)
        # try full layout; trailing optional skills depend on config, so derive count
        # from payload: cur[k] + max[k] + xp[k]*4bytes + 1 qp  => k = (len-1)/6
        k = (len(p) - 1) // 6
        i = 0
        cur = [p[i+j] for j in range(k)]; i += k
        mx = [p[i+j] for j in range(k)]; i += k
        xp = []
        for j in range(k):
            xp.append(int.from_bytes(p[i:i+4], "big") // 4); i += 4
        qp = p[i] if i < len(p) else 0
        with self.st.lock:
            self.st.skills = {}
            for j in range(k):
                nm = SKILL_NAMES[j] if j < n else str(j)
                self.st.skills[nm] = {"cur": cur[j], "max": mx[j], "xp": xp[j]}
            self.st.quest_points = qp
        self.st.event("stats_set", skills=k)

    def _decode_stat_one(self, p):
        if len(p) < 7:
            return
        sid = p[0]; cur = p[1]; mx = p[2]; xp = int.from_bytes(p[3:7], "big") // 4
        nm = SKILL_NAMES[sid] if sid < len(SKILL_NAMES) else str(sid)
        with self.st.lock:
            self.st.skills[nm] = {"cur": cur, "max": mx, "xp": xp}
        self.st.event("stat", skill=nm, cur=cur, max=mx, xp=xp)

    def _decode_message(self, p):
        # int icon, byte type, byte infobits, string msg, [sender,sender], [color]
        i = 4
        mtype = p[i]; i += 1
        info = p[i]; i += 1
        end = p.find(b"\n", i)
        if end < 0:
            end = len(p)
        text = p[i:end].decode("latin1", "replace")
        with self.st.lock:
            self.st._seq += 1
            self.st.messages.append({"seq": self.st._seq, "t": round(time.time(), 3),
                                     "text": text, "type": mtype})
            if len(self.st.messages) > 2000:
                self.st.messages = self.st.messages[-1200:]
        self.st.event("message", text=text, type=mtype)

    def _decode_menu(self, p):
        i = 0
        count = p[i]; i += 1
        opts = []
        for _ in range(count):
            end = p.find(b"\n", i)
            if end < 0:
                end = len(p)
            opts.append(p[i:end].decode("latin1", "replace")); i = end + 1
        with self.st.lock:
            self.st.dialog_open = True; self.st.dialog_options = opts
        self.st.event("dialog_open", options=opts)

    def _decode_bank(self, p):
        i = 0
        count = int.from_bytes(p[i:i+2], "big"); i += 2
        int.from_bytes(p[i:i+2], "big"); i += 2  # max size
        items = []
        for slot in range(count):
            iid = int.from_bytes(p[i:i+2], "big"); i += 2
            amt = int.from_bytes(p[i:i+4], "big"); i += 4
            items.append({"slot": slot, "id": iid, "amount": amt,
                          "name": self.names.get(iid, "")})
        with self.st.lock:
            self.st.bank_open = True; self.st.bank = items
        self.st.event("bank_open", count=len(items))

    def _decode_bank_update(self, p):
        # [byte slot][short id][int amount]; amount 0 means the slot was emptied.
        slot = p[0]
        iid = int.from_bytes(p[1:3], "big")
        amt = int.from_bytes(p[3:7], "big")
        with self.st.lock:
            bank = [b for b in self.st.bank if b["slot"] != slot]
            if amt > 0:
                bank.append({"slot": slot, "id": iid, "amount": amt,
                             "name": self.names.get(iid, "")})
            bank.sort(key=lambda b: b["slot"])
            self.st.bank = bank
        self.st.event("bank_update", slot=slot, id=iid, amount=amt)

    def _decode_grounditems(self, p):
        # variable; entries: [short id][byte dx][byte dy]... 0xFF markers for removal.
        # We rebuild best-effort: ids with relative coords from player.
        items = []
        i = 0
        bx = self.st.x or 0
        by = self.st.y or 0
        try:
            while i + 3 <= len(p):
                if p[i] == 0xFF:
                    i += 3
                    continue
                iid = int.from_bytes(p[i:i+2], "big"); i += 2
                dx = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                dy = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                real = iid & 0x7FFF
                items.append({"id": real, "x": bx + dx, "y": by + dy,
                              "name": self.names.get(real, "")})
        except IndexError:
            pass
        with self.st.lock:
            # merge: new sightings add/refresh
            self.st.ground_items = items
        self.st.event("grounditems", count=len(items))

    # ---------------- command dispatch ----------------
    def _slot_amount(self, slot):
        with self.st.lock:
            for it in self.st.inventory:
                if it["slot"] == slot:
                    return it["amount"]
        return 1

    def find_npc(self, npc_id=None, server_index=None):
        pool = self._recent_npcs()
        sx, sy = self.st.x, self.st.y
        if server_index is not None:
            for n in pool:
                if n["server_index"] == int(server_index):
                    return n
            return None
        if npc_id is not None:
            cands = [n for n in pool if n["id"] == int(npc_id)]
            if cands and sx is not None:
                cands.sort(key=lambda n: abs(n["x"] - sx) + abs(n["y"] - sy))
            return cands[0] if cands else None
        return None

    def handle(self, req):
        cmd = req.get("cmd")
        a = req.get("args", {})
        st = self.st
        try:
            # ---- actions ----
            if cmd == "ping":
                return {"ok": True, "pong": True, "logged_in": st.logged_in}
            if cmd == "goto" or cmd == "walk":
                x = int(a["x"]); y = int(a["y"])
                self.send("WORLD_WALK_REQUEST", P.BitWriter().u16(x).u16(y).b)
                return {"ok": True, "x": x, "y": y}
            if cmd == "walk-step":
                x = int(a["x"]); y = int(a["y"])
                self.send("WALK_TO_POINT", P.BitWriter().u16(x).u16(y).b)
                return {"ok": True}
            if cmd == "npc-talk":
                n = self.find_npc(npc_id=a.get("id"), server_index=a.get("server_index"))
                if not n:
                    return {"ok": False, "error": "npc not found"}
                self.send("NPC_TALK_TO", P.BitWriter().u16(n["server_index"]).b)
                return {"ok": True, "npc": n}
            if cmd == "npc-command":
                n = self.find_npc(npc_id=a.get("id"), server_index=a.get("server_index"))
                if not n:
                    return {"ok": False, "error": "npc not found"}
                which = int(a.get("which", 1))
                self.send("NPC_COMMAND1" if which == 1 else "NPC_COMMAND2",
                          P.BitWriter().u16(n["server_index"]).b)
                return {"ok": True, "npc": n}
            if cmd == "attack-npc":
                n = self.find_npc(npc_id=a.get("id"), server_index=a.get("server_index"))
                if not n:
                    return {"ok": False, "error": "npc not found"}
                self.send("NPC_ATTACK1", P.BitWriter().u16(n["server_index"]).b)
                return {"ok": True, "npc": n}
            if cmd == "attack-player":
                self.send("PLAYER_ATTACK", P.BitWriter().u16(int(a["server_index"])).b)
                return {"ok": True}
            if cmd == "take-item":
                self.send("GROUND_ITEM_TAKE",
                          P.BitWriter().u16(int(a["x"])).u16(int(a["y"])).u16(int(a["id"])).b)
                return {"ok": True}
            if cmd == "say":
                # CHAT_MESSAGE expects RSC-encrypted text; admin chat via COMMAND is simpler.
                txt = a["text"]
                self.send("CHAT_MESSAGE", P.BitWriter().u8(len(txt)).raw(txt.encode("latin1")).b)
                return {"ok": True}
            if cmd == "admin" or cmd == "command":
                c = a["command"].lstrip(":")
                self.send("COMMAND", P.BitWriter().rsstr(c).b)
                return {"ok": True, "sent": c}
            if cmd == "menu-reply":
                self.send("QUESTION_DIALOG_ANSWER", P.BitWriter().u8(int(a["option"])).b)
                return {"ok": True}
            if cmd == "menu-cancel":
                self.send("QUESTION_DIALOG_ANSWER", P.BitWriter().i8(-1).b)
                return {"ok": True}
            if cmd == "input-reply":
                w = P.BitWriter().u8(9).rsstr(a.get("text", ""))
                self.send("INTERFACE_OPTIONS", w.b)
                with st.lock:
                    st.input_open = False
                return {"ok": True}
            if cmd == "item-command":
                # index:short, amount:int, commandIndex:byte (custom protocol)
                self.send("ITEM_COMMAND", P.BitWriter().i16(int(a["slot"]))
                          .u32(int(a.get("amount", 1))).u8(int(a.get("command", 0))).b)
                return {"ok": True}
            if cmd == "drop":
                # index:short, amount:int (voidscape has want_drop_x=true)
                amt = int(a.get("amount", self._slot_amount(int(a["slot"]))))
                self.send("ITEM_DROP", P.BitWriter().i16(int(a["slot"])).u32(amt).b)
                return {"ok": True}
            if cmd == "equip":
                self.send("ITEM_EQUIP_FROM_INVENTORY", P.BitWriter().u16(int(a["slot"])).b)
                return {"ok": True}
            if cmd == "unequip":
                self.send("ITEM_UNEQUIP_FROM_INVENTORY", P.BitWriter().u16(int(a["slot"])).b)
                return {"ok": True}
            if cmd == "bank-withdraw":
                w = P.BitWriter().u16(int(a["id"])).u32(int(a["amount"]))
                if a.get("noted"):
                    w.u8(1)
                self.send("BANK_WITHDRAW", w.b)
                return {"ok": True}
            if cmd == "bank-deposit":
                self.send("BANK_DEPOSIT", P.BitWriter().u16(int(a["id"])).u32(int(a["amount"])).b)
                return {"ok": True}
            if cmd == "bank-deposit-all":
                self.send("BANK_DEPOSIT_ALL_FROM_INVENTORY")
                return {"ok": True}
            if cmd == "bank-close":
                self.send("BANK_CLOSE")
                return {"ok": True}
            if cmd == "logout":
                self.send("LOGOUT")
                return {"ok": True}

            # ---- queries ----
            if cmd == "state":
                return {"ok": True, "state": self.snapshot(a.get("section", "all"))}

            # ---- waits ----
            if cmd == "wait":
                return self.wait(a)

            # ---- events ----
            if cmd == "events":
                since = int(a.get("since", 0))
                with st.lock:
                    evs = [e for e in st.events if e["seq"] > since]
                return {"ok": True, "events": evs,
                        "last": evs[-1]["seq"] if evs else since}

            if cmd == "shutdown":
                # Log out cleanly so the account is released promptly for the next run.
                try:
                    self.send("LOGOUT")
                    time.sleep(0.6)
                except Exception:
                    pass
                self.running = False
                try: self.conn.close()
                except Exception: pass
                return {"ok": True, "shutdown": True}

            return {"ok": False, "error": "unknown command: %s" % cmd}
        except Exception as e:
            return {"ok": False, "error": "%s: %s" % (type(e).__name__, e)}

    def snapshot(self, section):
        st = self.st
        with st.lock:
            full = {
                "connected": st.connected, "logged_in": st.logged_in,
                "login_response": st.login_response, "username": st.username,
                "position": {"x": st.x, "y": st.y},
                "fatigue": st.fatigue,
                "skills": st.skills, "quest_points": st.quest_points,
                "inventory": list(st.inventory),
                "bank": {"open": st.bank_open, "items": list(st.bank)},
                "shop": {"open": st.shop_open},
                "dialog": {"open": st.dialog_open, "options": list(st.dialog_options),
                           "input_open": st.input_open, "input_prompt": st.input_prompt},
                "npcs": list(st.npcs),
                "ground_items": list(st.ground_items),
                "messages": st.messages[-30:],
            }
        if section in (None, "all"):
            return full
        aliases = {"position": "position", "pos": "position", "inventory": "inventory",
                   "inv": "inventory", "stats": "skills", "skills": "skills",
                   "npcs": "npcs", "ground-items": "ground_items", "bank": "bank",
                   "dialog": "dialog", "messages": "messages", "shop": "shop"}
        key = aliases.get(section, section)
        return {key: full.get(key)}

    def wait(self, a):
        cond = a.get("condition")
        timeout = float(a.get("timeout", 10))
        deadline = time.time() + timeout
        st = self.st
        base_xp = None
        if cond == "xp-gained":
            sk = a["skill"]
            with st.lock:
                base_xp = st.skills.get(sk, {}).get("xp", 0)
        while time.time() < deadline:
            with st.lock:
                if cond == "position":
                    if st.x == int(a["x"]) and st.y == int(a["y"]):
                        return {"ok": True, "matched": True, "position": {"x": st.x, "y": st.y}}
                elif cond == "near":
                    if st.x is not None:
                        d = abs(st.x - int(a["x"])) + abs(st.y - int(a["y"]))
                        if d <= int(a.get("radius", 1)):
                            return {"ok": True, "matched": True, "distance": d,
                                    "position": {"x": st.x, "y": st.y}}
                elif cond == "inventory-contains":
                    iid = int(a["id"]); need = int(a.get("amount", 1))
                    have = sum(it["amount"] for it in st.inventory if it["id"] == iid)
                    if have >= need:
                        return {"ok": True, "matched": True, "have": have}
                elif cond == "inventory-lacks":
                    iid = int(a["id"])
                    if not any(it["id"] == iid for it in st.inventory):
                        return {"ok": True, "matched": True}
                elif cond == "message":
                    rx = re.compile(a["regex"])
                    for m in st.messages:
                        if m["t"] >= (deadline - timeout) and rx.search(m["text"]):
                            return {"ok": True, "matched": True, "message": m["text"]}
                elif cond == "dialog-open":
                    if st.dialog_open:
                        return {"ok": True, "matched": True, "options": list(st.dialog_options)}
                elif cond == "input-open":
                    if st.input_open:
                        return {"ok": True, "matched": True, "prompt": st.input_prompt}
                elif cond == "dialog-or-message":
                    if st.dialog_open or st.input_open or (
                            st.messages and st.messages[-1]["t"] >= (deadline - timeout)):
                        return {"ok": True, "matched": True}
                elif cond == "bank-open":
                    if st.bank_open:
                        return {"ok": True, "matched": True}
                elif cond == "npc-present":
                    nid = int(a["id"])
                    hit = [n for n in self._recent_npcs() if n["id"] == nid]
                    if hit:
                        return {"ok": True, "matched": True, "npcs": hit}
                elif cond == "ground-item":
                    iid = int(a["id"])
                    hit = [g for g in st.ground_items if g["id"] == iid]
                    if hit:
                        return {"ok": True, "matched": True, "ground_items": hit}
                elif cond == "npc-dead" or cond == "npc-gone":
                    si = a.get("server_index")
                    nid = a.get("id")
                    pool = self._recent_npcs()
                    present = any((si is not None and n["server_index"] == int(si)) or
                                  (nid is not None and n["id"] == int(nid)) for n in pool)
                    if not present:
                        return {"ok": True, "matched": True}
                elif cond == "xp-gained":
                    sk = a["skill"]
                    cur = st.skills.get(sk, {}).get("xp", 0)
                    if cur > base_xp:
                        return {"ok": True, "matched": True, "xp": cur, "gained": cur - base_xp}
                elif cond == "logged-in":
                    if st.logged_in and st.x is not None:
                        return {"ok": True, "matched": True}
            time.sleep(0.1)
        return {"ok": False, "matched": False, "timeout": timeout, "error": "wait timed out"}

    # ---------------- control server ----------------
    def serve_control(self):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind(("127.0.0.1", self.args.ctrl_port))
        srv.listen(8)
        print("voidbotd: control on 127.0.0.1:%d, game session %s" %
              (self.args.ctrl_port, self.args.user), flush=True)
        while self.running:
            try:
                cli, _ = srv.accept()
            except OSError:
                break
            threading.Thread(target=self._handle_client, args=(cli,), daemon=True).start()
        srv.close()

    def _handle_client(self, cli):
        f = cli.makefile("rwb")
        try:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    req = json.loads(line)
                except ValueError:
                    resp = {"ok": False, "error": "bad json"}
                else:
                    resp = self.handle(req)
                f.write((json.dumps(resp) + "\n").encode()); f.flush()
                if req.get("cmd") == "shutdown":
                    break
        except OSError:
            pass
        finally:
            try: cli.close()
            except OSError: pass

    def run(self):
        # Control server first so `state`/`wait logged-in` work while login retries.
        threading.Thread(target=self.serve_control, daemon=True).start()
        try:
            self.connect_and_login()
        except Exception as e:
            self.st.event("login_failed", error=str(e))
            print("voidbotd: login failed: %s" % e, flush=True)
            self.running = False
            return
        threading.Thread(target=self.recv_loop, daemon=True).start()
        threading.Thread(target=self.heartbeat, daemon=True).start()
        while self.running:
            time.sleep(0.3)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--game-port", type=int, default=43596)
    ap.add_argument("--ctrl-port", type=int, default=18900)
    ap.add_argument("--user", required=True)
    ap.add_argument("--pass", dest="password", required=True)
    ap.add_argument("--defs", default=None, help="server/conf/server/defs dir for item names")
    args = ap.parse_args()
    Daemon(args).run()


if __name__ == "__main__":
    main()
