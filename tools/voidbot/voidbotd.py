"""voidbotd — headless voidscape session daemon.

Holds one logged-in game connection, decodes server packets into a live GameState,
and serves a JSON-lines control socket (TCP loopback) so the `voidbot` CLI can issue
actions, query state, and block on conditions. No GUI, no mouse, no screenshots.

Start:  python voidbotd.py --host 127.0.0.1 --game-port 43596 --ctrl-port 18900 \
                          --user wbtest --password-file /run/voidbot/wbtest.password \
                          [--defs <repo>/server/conf/server/defs]
Protocol details + validation: tools/voidbot/protocol.py, docs/bot-api.md.
"""
import argparse
import json
import os
import re
import secrets
import signal
import socket
import stat
import sys
import threading
import time
import xml.etree.ElementTree as ET

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import protocol as P

SKILL_NAMES = ["attack", "defense", "strength", "hits", "ranged", "prayer", "magic",
               "cooking", "woodcutting", "fletching", "fishing", "firemaking",
               "crafting", "smithing", "mining", "herblaw", "agility", "thieving",
               "runecraft", "harvesting"]

COMBAT_STYLES = {
    "controlled": 0,
    "aggressive": 1,
    "accurate": 2,
    "defensive": 3,
}

COMBAT_EVENT_LIMIT = 256


class UsageError(ValueError):
    """Command-shape error; the CLI maps its `usage:` prefix to exit code 2."""


def _is_protected_systemd_credential(path, metadata):
    """Recognize systemd's read-only, id-mapped LoadCredential view.

    Current systemd exposes credentials as root:root 0440 below a root:root 0550
    directory even though only the unit user can read the id-mapped mount. Older
    hosts may expose the equivalent owner-only 0400/0500 view. The read-only
    filesystem and exact CREDENTIALS_DIRECTORY parent are both required, so an
    ordinary group-readable password file never inherits this exception.
    """
    credentials_directory = os.environ.get("CREDENTIALS_DIRECTORY")
    if not credentials_directory:
        return False
    candidate = os.path.abspath(os.fspath(path))
    directory = os.path.abspath(credentials_directory)
    if os.path.dirname(candidate) != directory:
        return False
    try:
        directory_metadata = os.stat(directory, follow_symlinks=False)
        filesystem = os.statvfs(candidate)
    except OSError:
        return False
    file_mode = stat.S_IMODE(metadata.st_mode)
    directory_mode = stat.S_IMODE(directory_metadata.st_mode)
    valid_uids = {0, os.geteuid()}
    valid_gids = {0, os.getegid()}
    return (
        file_mode in (0o400, 0o440)
        and directory_mode in (0o500, 0o550)
        and stat.S_ISDIR(directory_metadata.st_mode)
        and metadata.st_uid in valid_uids
        and metadata.st_gid in valid_gids
        and directory_metadata.st_uid in valid_uids
        and directory_metadata.st_gid in valid_gids
        and bool(filesystem.f_flag & os.ST_RDONLY)
    )


def read_password_file(path):
    """Read one password line without ever echoing its contents.

    A single trailing line ending is accepted because secret files created by
    common provisioning tools normally end in one. Multiple lines are rejected
    so an accidentally pasted username/password pair cannot select a surprising
    credential.
    """
    try:
        with open(path, encoding="utf-8", newline=None) as fh:
            metadata = os.fstat(fh.fileno())
            if not stat.S_ISREG(metadata.st_mode):
                raise UsageError("password file %s must be a regular file" % path)
            if (
                metadata.st_mode & (stat.S_IRWXG | stat.S_IRWXO)
                and not _is_protected_systemd_credential(path, metadata)
            ):
                raise UsageError("password file %s must not grant group/other permissions" % path)
            lines = fh.read().splitlines()
    except OSError as e:
        raise UsageError("cannot read password file %s: %s" % (path, e))
    except UnicodeError:
        raise UsageError("password file %s is not valid UTF-8" % path)
    if len(lines) != 1 or not lines[0]:
        raise UsageError("password file %s must contain exactly one non-empty line" % path)
    return lines[0]


def command_flag(value):
    """Interpret the CLI's `--flag`/`--flag value` representation."""
    if value is None:
        return False
    if isinstance(value, bool):
        return value
    normalized = str(value).strip().lower()
    if normalized in ("1", "true", "yes", "on"):
        return True
    if normalized in ("0", "false", "no", "off"):
        return False
    raise UsageError("flag values must be true/false")


def combat_style_id(value):
    normalized = str(value).strip().lower()
    if normalized in COMBAT_STYLES:
        return COMBAT_STYLES[normalized]
    try:
        style = int(normalized)
    except ValueError:
        raise UsageError("combat-style requires controlled, aggressive, accurate, or defensive")
    if style not in COMBAT_STYLES.values():
        raise UsageError("combat-style must be 0..3")
    return style


def prayer_id(value):
    try:
        prayer = int(str(value).strip())
    except (TypeError, ValueError):
        raise UsageError("prayer id must be 0..%d" % (P.PRAYER_COUNT - 1))
    if prayer < 0 or prayer >= P.PRAYER_COUNT:
        raise UsageError("prayer id must be 0..%d" % (P.PRAYER_COUNT - 1))
    return prayer


def arena_rule_mask(args):
    """Encode the stock client's ranked/unranked Void Arena rule mask."""
    if command_flag(args.get("ranked")):
        return P.ARENA_RULE_RANKED
    mask = 0
    if command_flag(args.get("f2p_only")):
        mask |= P.ARENA_RULE_F2P_ONLY
    if command_flag(args.get("prayer")):
        mask |= P.ARENA_RULE_ALLOW_PRAYER
    if command_flag(args.get("ranged")):
        mask |= P.ARENA_RULE_ALLOW_RANGED
    if command_flag(args.get("magic")):
        mask |= P.ARENA_RULE_ALLOW_MAGIC
    return mask


def arena_rules_from_mask(mask):
    """Return the effective rules displayed by the stock arena client."""
    ranked = bool(mask & P.ARENA_RULE_RANKED)
    return {
        "mask": P.ARENA_RULE_RANKED if ranked else mask & P.ARENA_RULE_MASK,
        "ranked": ranked,
        "f2p_only": ranked or bool(mask & P.ARENA_RULE_F2P_ONLY),
        "allow_prayer": ranked or bool(mask & P.ARENA_RULE_ALLOW_PRAYER),
        "allow_ranged": ranked or bool(mask & P.ARENA_RULE_ALLOW_RANGED),
        "allow_magic": ranked or bool(mask & P.ARENA_RULE_ALLOW_MAGIC),
    }


def new_arena_state():
    return {
        "phase": "none",       # none|setup|confirm|closed|countdown|started|ended
        "opponent_server_index": None,
        "opponent_name": None,
        "rules": arena_rules_from_mask(0),
        "own_accepted": False,
        "opponent_accepted": False,
        "own_confirmed": False,
        "opponent_confirmed": False,
        "ranked_available": False,
        "countdown_seconds": None,
        "countdown_end": None,
        "ended_at": None,
    }


def parse_duel_offer_specs(specs):
    """Parse complete-offer rows encoded as catalog-id:amount[:noted]."""
    if specs is None:
        specs = []
    if not isinstance(specs, list):
        raise UsageError("duel-offer items must be catalog-id:amount[:noted]")
    if len(specs) > 8:
        raise UsageError("duel-offer accepts at most 8 item rows")
    items = []
    for spec in specs:
        parts = str(spec).split(":")
        if len(parts) not in (2, 3):
            raise UsageError("duel-offer item must be catalog-id:amount[:noted]")
        try:
            iid = int(parts[0])
            amount = int(parts[1])
        except ValueError:
            raise UsageError("duel-offer item id and amount must be integers")
        if iid < 0 or iid > 0xFFFF:
            raise UsageError("duel-offer item id must be 0..65535")
        if amount < 1 or amount > 0x7FFFFFFF:
            raise UsageError("duel-offer amount must be 1..2147483647")
        noted = False
        if len(parts) == 3:
            token = parts[2].strip().lower()
            if token in ("noted", "note", "1", "true", "yes"):
                noted = True
            elif token not in ("", "unnoted", "0", "false", "no"):
                raise UsageError("duel-offer noted marker must be noted or unnoted")
        items.append({"id": iid, "amount": amount, "noted": noted})
    return items


def parse_shop_sell_args(args):
    """Return the exact unsigned-short fields sent by the stock client."""
    missing = [key for key in ("id", "stock", "amount") if args.get(key) is None]
    if missing:
        raise UsageError("shop-sell requires id, stock, and amount")
    try:
        catalog_id = int(args["id"])
        stock = int(args["stock"])
        amount = int(args["amount"])
    except (TypeError, ValueError):
        raise UsageError("shop-sell id, stock, and amount must be integers")
    if catalog_id < 0 or catalog_id > 0xFFFF:
        raise UsageError("shop-sell item id must be 0..65535")
    if stock < 0 or stock > 0xFFFF:
        raise UsageError("shop-sell stock must be 0..65535")
    if amount < 1 or amount > 0xFFFF:
        raise UsageError("shop-sell amount must be 1..65535")
    return catalog_id, stock, amount


def read_based_config_data(defs_dir):
    """The server's based_config_data, which gates def patching (default 85).

    Read from server/local.conf (three levels up from the defs dir, matching
    scripts/run-server.sh); VOIDBOT_BASED_CONFIG_DATA overrides for probing.
    """
    override = os.environ.get("VOIDBOT_BASED_CONFIG_DATA")
    if override:
        return int(override)
    conf = os.path.normpath(os.path.join(defs_dir, "..", "..", "..", "local.conf"))
    try:
        with open(conf, encoding="utf-8") as fh:
            for line in fh:
                m = re.match(r"\s*based_config_data\s*:\s*(\d+)", line)
                if m:
                    return int(m.group(1))
    except OSError:
        pass
    return 85


def load_item_defs(defs_dir):
    """Return (stackable_set, name_by_id) from the server item defs.

    Mirrors EntityHandler: ItemDefs + ItemDefsCustom always load;
    ItemDefsPatch<N>.json applies only when based_config_data (N) < 85.
    patchObject only overrides truthy fields, so union-adding stackable
    ids from an applied patch matches the server's merge.
    """
    files = ["ItemDefs.json", "ItemDefsCustom.json"]
    based = read_based_config_data(defs_dir)
    if based < 85:
        files.append("ItemDefsPatch%d.json" % based)
    stackable, names = set(), {}
    for fn in files:
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


def load_object_defs(defs_dir):
    """Return id -> {name,width,height,type} from GameObjectDef.xml."""
    path = os.path.join(defs_dir, "GameObjectDef.xml")
    if not os.path.exists(path):
        return {}
    objects = {}
    root = ET.parse(path).getroot()
    for oid, obj in enumerate(root.findall("GameObjectDef")):
        def text(name, default):
            node = obj.find(name)
            return node.text.strip() if node is not None and node.text is not None else default
        objects[oid] = {
            "name": text("name", ""),
            "width": int(text("width", "1")),
            "height": int(text("height", "1")),
            "type": int(text("type", "0")),
        }
    return objects


class GameState:
    def __init__(self):
        self.lock = threading.RLock()
        self.connected = False
        self.logged_in = False
        self.login_response = None
        self.username = None
        self.appearance_open = False
        self.server_index = None
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
        self.friends = []           # [{name,former_name,online,same_world,status,world}]
        self.players = []           # nearby [{server_index,name,x,y,sprite,appearance}]
        self._players_initialized = False  # first complete PLAYER_COORDS frame decoded
        # Identity arrives separately from coordinates in SEND_UPDATE_PLAYERS type 5.
        # Keep a private-by-convention cache so either packet order can be handled.
        self._player_appearances = {}  # server_index -> {name,appearance}
        self.npcs = []              # ordered [{server_index,id,x,y}]
        self.npc_seen = {}          # server_index -> {id,x,y,t}; smooths decode flicker
        self.ground_items = []      # [{id,x,y}]
        self.messages = []          # [{seq,t,text,type}]
        self.world_walk_route = None  # {seq,t,ok,reason,count,route}
        self.world_walk_route_seq = 0
        self.combat_style = None
        self.prayers = {"states": None, "active": []}
        # Focused, bounded observation window for combat cadence assertions. The
        # complete event stream remains available separately through `events`.
        self.combat_events = []      # recent [{seq,t,kind,...}]
        self.duel = {
            "phase": "none",       # none|offer|confirm|closed|combat|complete
            "opponent_server_index": None,
            "own_offer": [],
            "opponent_offer": [],
            "settings": {
                "no_retreat": False,
                "no_magic": False,
                "no_prayer": False,
                "no_weapons": False,
            },
            "own_accepted": False,
            "opponent_accepted": False,
            "outcome": None,
        }
        self.arena = new_arena_state()
        self.events = []            # [{seq,t,kind,...}]
        self._seq = 0

    def event(self, kind, **kw):
        self._seq += 1
        e = {"seq": self._seq, "t": round(time.time(), 3), "kind": kind, **kw}
        self.events.append(e)
        if len(self.events) > 5000:
            self.events = self.events[-3000:]
        return e

    def combat_event(self, kind, **kw):
        with self.lock:
            event = self.event(kind, **kw)
            self.combat_events.append(dict(event))
            if len(self.combat_events) > COMBAT_EVENT_LIMIT:
                self.combat_events = self.combat_events[-COMBAT_EVENT_LIMIT:]
            return event


class Daemon:
    def __init__(self, args):
        self.args = args
        self.st = GameState()
        self.conn = None
        self.pending_conn = None
        self.control_socket = None
        self.send_lock = threading.Lock()
        # RLock avoids self-deadlock if a Python signal handler interrupts the
        # main thread while it is finalizing login under this lock.
        self.lifecycle_lock = threading.RLock()
        self.last_write = 0
        # Private handshake material is deliberately kept off GameState so it can
        # never appear in snapshots, events, messages, or CLI output.
        self._duel_proof = None
        self.stackable, self.names = (set(), {})
        self.object_defs = {}
        if args.defs and os.path.isdir(args.defs):
            self.stackable, self.names = load_item_defs(args.defs)
            self.object_defs = load_object_defs(args.defs)
        self.running = True
        self.exit_code = 0
        self.shutdown_requested = False
        # Wire-format flags that gate optional per-item bytes in the ground-item stream
        # (and the bank-withdraw noted byte). Must match the server's config: the
        # voidscape preset ships want_bank_notes: true, so default on; override with
        # VOIDBOT_WANT_BANK_NOTES=0 for a preset that has it off. The rare-drop-beam
        # byte is present for client versions >= 10030 (P.CLIENT_VERSION).
        self.want_bank_notes = os.environ.get("VOIDBOT_WANT_BANK_NOTES", "1") != "0"
        self.ground_has_beam = P.CLIENT_VERSION >= 10030

    # ---------------- connection / login ----------------
    def connect_and_login(self):
        # The real client uses TWO connections: a throwaway one to fetch server
        # configs (RSA key), which the server closes, then a fresh one for login +
        # the game session.
        if self.shutdown_requested:
            raise InterruptedError("shutdown requested")
        cfg = P.Connection(self.args.host, self.args.game_port)
        self.pending_conn = cfg
        if self.shutdown_requested:
            cfg.close()
            self.pending_conn = None
            raise InterruptedError("shutdown requested")
        cfg.send_packet(19)  # config request
        exp = mod = None
        deadline = time.time() + 15
        while time.time() < deadline:
            op, payload = cfg.recv_packet()
            if op == 19:
                exp, mod = self._parse_rsa(payload)
                break
        cfg.close()
        self.pending_conn = None
        if self.shutdown_requested:
            raise InterruptedError("shutdown requested")
        if not mod:
            raise RuntimeError("did not receive RSA key in config response")

        # Retry transient rejections: ACCOUNT_LOGGEDIN (4) and IP_IN_USE (6) clear a
        # few seconds after a previous session's channel closes and is unregistered.
        resp = None
        for attempt in range(6):
            if self.shutdown_requested:
                raise InterruptedError("shutdown requested")
            c = P.Connection(self.args.host, self.args.game_port)
            self.pending_conn = c
            if self.shutdown_requested:
                c.close()
                self.pending_conn = None
                raise InterruptedError("shutdown requested")
            body = P.build_login(self.args.user, self.args.password, exp, mod,
                                 max_item_id=max(self.names) if self.names else None)
            c.send_packet(body[0], body[1:])
            try:
                resp = c.recv_byte()
            except (ConnectionError, OSError):
                resp = -1
            if resp & 0x40 if isinstance(resp, int) and resp >= 0 else False:
                self.conn = c
                self.pending_conn = None
                # Authentication is complete. Do not retain the plaintext
                # credential for the lifetime of this long-running daemon.
                self.args.password = None
                break
            c.close()
            self.pending_conn = None
            if resp in (4, 6):           # already-logged-in / ip-in-use: wait + retry
                if self.shutdown_requested:
                    raise InterruptedError("shutdown requested")
                time.sleep(3)
                continue
            raise RuntimeError("login failed, response=%d" % resp)
        else:
            raise RuntimeError("login failed after retries, last response=%s" % resp)
        with self.lifecycle_lock:
            if self.shutdown_requested:
                interrupted = True
            else:
                interrupted = False
                with self.st.lock:
                    self.st.connected = True
                    self.st.login_response = resp
                    self.st.username = self.args.user
                    self.st.logged_in = bool(resp & 0x40)
                # A Python signal handler can run re-entrantly on this main
                # thread even while its RLock is held. Re-check after publishing
                # state so a stop in that tiny window cannot leave a live login.
                interrupted = self.shutdown_requested
        if interrupted:
            self._close_connections()
            raise InterruptedError("shutdown requested")
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
            except Exception as e:
                self._connection_ended("heartbeat failed: %s" % e)
                break

    # ---------------- process lifecycle ----------------
    def _close_connections(self):
        """Close game/control sockets; safe to call repeatedly from any thread."""
        seen = set()
        for conn in (self.conn, self.pending_conn):
            if conn is None or id(conn) in seen:
                continue
            seen.add(id(conn))
            try:
                conn.close()
            except Exception:
                pass
        self.conn = None
        self.pending_conn = None
        if self.control_socket is not None:
            try:
                self.control_socket.close()
            except OSError:
                pass
            self.control_socket = None

    def _finish_shutdown(self):
        """Finish an intentional stop after giving LOGOUT a moment to flush."""
        with self.lifecycle_lock:
            self.running = False
        with self.st.lock:
            self.st.connected = False
            self.st.logged_in = False
        self._close_connections()

    def request_shutdown(self, reason="shutdown"):
        """Request a clean logout and close, including from SIGTERM/SIGINT."""
        with self.lifecycle_lock:
            if self.shutdown_requested:
                return
            self.shutdown_requested = True
        self.st.event("shutdown", reason=reason)
        sent_logout = False
        try:
            with self.st.lock:
                logged_in = self.st.logged_in
            if logged_in and self.conn is not None:
                self.send("LOGOUT")
                sent_logout = True
        except Exception:
            # The receive loop may have observed the same broken socket first.
            pass
        if sent_logout:
            timer = threading.Timer(0.25, self._finish_shutdown)
            timer.daemon = True
            timer.start()
        else:
            self._finish_shutdown()

    def _connection_ended(self, detail, server_packet=False):
        """Stop the daemon so a supervisor can restart an unexpected session loss."""
        with self.lifecycle_lock:
            intentional = self.shutdown_requested
            if not intentional:
                self.exit_code = 1
            self.running = False
        self._clear_duel_proof()
        with self.st.lock:
            self.st.connected = False
            self.st.logged_in = False
        if server_packet:
            self.st.event("server_close", error=detail, intentional=intentional)
        else:
            self.st.event("disconnect", error=detail, intentional=intentional)
        self._close_connections()

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
            self._connection_ended(str(e))

    def decode(self, op, p):
        name = P.IN.get(op)
        st = self.st
        if name == "FLOOR_SET":
            if len(p) >= 2:
                server_index = int.from_bytes(p[0:2], "big")
                with st.lock:
                    st.server_index = server_index
                st.event("world_info", server_index=server_index)
        elif name == "PLAYER_COORDS":
            self._decode_players(p)
        elif name == "NPC_COORDS":
            self._decode_npcs(p)
        elif name == "UPDATE_NPC":
            self._decode_npc_update(p)
        elif name == "UPDATE_PLAYERS":
            self._decode_player_update(p)
        elif name == "UPDATE_FRIEND":
            self._decode_friend_update(p)
        elif name == "SET_INVENTORY":
            self._decode_inventory_full(p)
        elif name == "SET_INVENTORY_SLOT":
            self._decode_inventory_slot(p)
        elif name == "REMOVE_INVENTORY_SLOT":
            slot = p[0] if p else -1
            # Server compacts on removal (client PacketHandler.removeItem shifts every
            # higher slot down by one), so decrement slots above the removed one — else
            # the daemon's slot map goes stale and slot-addressed commands mis-target.
            with st.lock:
                newinv = []
                for it in st.inventory:
                    if it["slot"] == slot:
                        continue
                    if it["slot"] > slot:
                        it = dict(it, slot=it["slot"] - 1)
                    newinv.append(it)
                st.inventory = newinv
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
        elif name == "SET_PRAYERS":
            if len(p) != P.PRAYER_COUNT or any(value not in (0, 1) for value in p):
                st.event("decode_error", opcode=206,
                         error="invalid prayer-state payload")
            else:
                states = [bool(value) for value in p]
                active = [index for index, enabled in enumerate(states) if enabled]
                with st.lock:
                    st.prayers = {"states": states, "active": active}
                st.event("prayers", active=active)
        elif name == "SEND_MESSAGE":
            self._decode_message(p)
        elif name == "COMBAT_STYLE":
            if p and p[0] <= 3:
                with st.lock:
                    st.combat_style = p[0]
                st.event("combat_style", style=p[0])
        elif name == "DIALOGUE_DUEL":
            # A newly opened duel supersedes any completed handshake tombstone.
            self._clear_duel_proof()
            opponent = int.from_bytes(p[0:2], "big") if len(p) >= 2 else None
            with st.lock:
                st.duel = {
                    "phase": "offer",
                    "opponent_server_index": opponent,
                    "own_offer": [],
                    "opponent_offer": [],
                    "settings": {
                        "no_retreat": False,
                        "no_magic": False,
                        "no_prayer": False,
                        "no_weapons": False,
                    },
                    "own_accepted": False,
                    "opponent_accepted": False,
                    "outcome": None,
                }
            st.event("duel_open", opponent_server_index=opponent)
        elif name == "DUEL_ITEMS":
            self._decode_duel_items(p)
        elif name == "DUEL_SETTINGS":
            if len(p) >= 4:
                settings = {
                    "no_retreat": p[0] == 1,
                    "no_magic": p[1] == 1,
                    "no_prayer": p[2] == 1,
                    "no_weapons": p[3] == 1,
                }
                with st.lock:
                    st.duel["settings"] = settings
                    st.duel["own_accepted"] = False
                    st.duel["opponent_accepted"] = False
                st.event("duel_settings", settings=settings)
        elif name == "DUEL_ACCEPT":
            accepted = bool(p and p[0] == 1)
            with st.lock:
                st.duel["own_accepted"] = accepted
            st.event("duel_acceptance", side="own", accepted=accepted)
        elif name == "DUEL_OPPONENT_ACCEPT":
            accepted = bool(p and p[0] == 1)
            with st.lock:
                st.duel["opponent_accepted"] = accepted
            st.event("duel_acceptance", side="opponent", accepted=accepted)
        elif name == "CONFIRM_DUEL":
            end = p.find(b"\n")
            opponent_name = p[:end if end >= 0 else len(p)].decode("latin1", "replace")
            with st.lock:
                st.duel["phase"] = "confirm"
            st.event("duel_confirm", opponent_name=opponent_name)
        elif name == "CANCEL_DUEL":
            # The server uses this packet both for an aborted setup and to close the
            # confirmation window after a successful lock. Keep a locked witness
            # private through settlement; incomplete handshakes are safe to wipe.
            proof = getattr(self, "_duel_proof", None)
            if proof is None or proof.get("phase") != "lock":
                self._clear_duel_proof()
            with st.lock:
                previous = st.duel.get("phase")
                # Death cleanup closes the old duel window too. Preserve a terminal
                # result so a caller cannot miss `duel-ended` between packets.
                if previous != "complete":
                    st.duel["phase"] = "closed"
            st.event("duel_close", previous_phase=previous)
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
        elif name == "WORLD_WALK_ROUTE":
            self._decode_world_walk_route(p)
        elif name == "FATIGUE":
            if len(p) >= 2:
                with st.lock:
                    st.fatigue = int.from_bytes(p[0:2], "big")
        elif name == "DEATH":
            with st.lock:
                if st.duel.get("phase") == "combat":
                    st.duel["phase"] = "complete"
                    st.duel["outcome"] = "lost"
                    duel_lost = True
                else:
                    duel_lost = False
            st.event("death")
            if duel_lost:
                st.event("duel_complete", outcome="lost")
        elif name in ("CLOSE_CONNECTION", "CLOSE_CONNECTION_NOTIFY"):
            st.event("logout")
            self._connection_ended(name.lower(), server_packet=True)
        elif name == "APPEARANCE_CHANGE":
            with st.lock:
                st.appearance_open = True
            st.event("appearance_open")

    def _decode_friend_update(self, p):
        """Decode the custom client's incremental friend-list update (wire 149)."""
        def read_string(offset, label):
            end = p.find(b"\n", offset)
            if end < 0:
                raise ValueError("unterminated friend %s" % label)
            return p[offset:end].decode("latin1", "replace"), end + 1

        try:
            name, offset = read_string(0, "name")
            former_name, offset = read_string(offset, "former name")
            if offset >= len(p):
                raise ValueError("missing friend online status")
            status = p[offset]
            offset += 1
            world = None
            if status & 4:
                world, offset = read_string(offset, "world")
            if offset != len(p):
                raise ValueError("trailing friend update bytes")
        except ValueError as e:
            self.st.event("decode_error", opcode=149, error=str(e))
            return

        entry = {
            "name": name,
            "former_name": former_name,
            "online": bool(status & 4),
            "same_world": bool(status & 2),
            "status": status,
            "world": world,
        }
        renamed = bool(status & 1)
        updated = False
        with self.st.lock:
            match_name = former_name if renamed else name
            folded = match_name.casefold()
            for index, friend in enumerate(self.st.friends):
                if friend["name"].casefold() == folded:
                    self.st.friends[index] = entry
                    updated = True
                    break
            # Match the real client: a rename updates an existing row, while a
            # normal status packet is also the incremental add/list packet.
            if not updated and not renamed:
                self.st.friends.append(entry)
                updated = True
        self.st.event("friend_update", name=name, former_name=former_name,
                      online=entry["online"], same_world=entry["same_world"],
                      renamed=renamed, applied=updated)

    def _decode_npcs(self, p):
        br = P.BitReader(p)
        st = self.st
        with st.lock:
            cache = list(st.npcs)
            previous_by_index = {
                n["server_index"]: dict(n) for n in st.npcs
            }
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
            prev = set(previous_by_index)
            now = {n["server_index"] for n in new_list}
            st.npcs = new_list
            for n in new_list:
                if n["server_index"] >= 0 and 0 <= n["id"] < 1600:
                    st.npc_seen[n["server_index"]] = {
                        "id": n["id"], "x": n["x"], "y": n["y"], "t": now_t}
            st.npc_seen = {k: v for k, v in st.npc_seen.items()
                           if now_t - v["t"] <= 3.0}
        moved = []
        for npc in new_list:
            previous = previous_by_index.get(npc["server_index"])
            if (previous is None or previous["id"] != npc["id"]
                    or (previous["x"], previous["y"]) == (npc["x"], npc["y"])):
                continue
            moved.append({
                "server_index": npc["server_index"],
                "id": npc["id"],
                "from_x": previous["x"],
                "from_y": previous["y"],
                "x": npc["x"],
                "y": npc["y"],
            })
        removed = sorted(prev - now)
        st.event("npc_frame", count=len(new_list), moved=moved, removed=removed)
        for gone in removed:
            st.event("npc_removed", server_index=gone)

    @staticmethod
    def _step_position(x, y, direction):
        """Apply the RSC 3-bit walking direction to one absolute tile."""
        if direction in (1, 2, 3):
            x += 1
        if direction in (5, 6, 7):
            x -= 1
        if direction in (3, 4, 5):
            y += 1
        if direction in (0, 1, 7):
            y -= 1
        return x, y

    @staticmethod
    def _copy_player(player):
        """Detach a player record before returning it through the control API."""
        copied = dict(player)
        appearance = player.get("appearance")
        if appearance is not None:
            copied["appearance"] = dict(appearance)
            copied["appearance"]["worn_items"] = list(appearance.get("worn_items", []))
        return copied

    def _player_coordinate_error(self, error):
        # Stale state must not satisfy player-absent after a broken AOI frame.
        with self.st.lock:
            self.st._players_initialized = False
        self.st.event("decode_error", opcode=191, error=error)

    def _decode_players(self, p):
        """Decode the incremental SEND_PLAYER_COORDS nearby-player stream.

        The packet starts with this bot's absolute position and then updates the
        previous nearby-player list by position. New entries carry a server index
        and offsets; names and appearance arrive separately in update type 5.
        """
        total_bits = len(p) * 8
        if total_bits < 36:  # self x:11, y:13, sprite:4, cached count:8
            self._player_coordinate_error("truncated player coordinate header")
            return

        br = P.BitReader(p)
        x = br.bits(11)
        y = br.bits(13)
        br.bits(4)  # local player's sprite/direction
        cached_count = br.bits(8)

        with self.st.lock:
            old_x, old_y = self.st.x, self.st.y
            cache = [self._copy_player(player) for player in self.st.players]
            appearances = dict(self.st._player_appearances)

        if cached_count != len(cache):
            self._player_coordinate_error(
                "player coordinate cache count %d does not match %d"
                % (cached_count, len(cache)))
            return

        players = []
        for index in range(cached_count):
            if br.bitpos + 1 > total_bits:
                self._player_coordinate_error("truncated cached player update")
                return
            player = cache[index]
            if br.bits(1):
                if br.bitpos + 1 > total_bits:
                    self._player_coordinate_error(
                        "truncated cached player update type")
                    return
                if br.bits(1):
                    if br.bitpos + 2 > total_bits:
                        self._player_coordinate_error(
                            "truncated cached player animation")
                        return
                    sprite_high = br.bits(2)
                    if sprite_high == 3:
                        continue  # AOI removal marker; no low sprite bits follow
                    if br.bitpos + 2 > total_bits:
                        self._player_coordinate_error(
                            "truncated cached player animation")
                        return
                    player["sprite"] = (sprite_high << 2) | br.bits(2)
                else:
                    if br.bitpos + 3 > total_bits:
                        self._player_coordinate_error(
                            "truncated cached player movement")
                        return
                    direction = br.bits(3)
                    player["x"], player["y"] = self._step_position(
                        player["x"], player["y"], direction)
                    player["sprite"] = direction
            players.append(player)

        # Each newly visible custom-client player is exactly 27 bits. Bit access
        # pads the final byte with at most seven zero bits.
        while total_bits - br.bitpos >= 27:
            server_index = br.bits(11)
            dx = br.bits(6)
            dy = br.bits(6)
            if dx > 31:
                dx -= 64
            if dy > 31:
                dy -= 64
            sprite = br.bits(4)
            identity = appearances.get(server_index, {})
            players.append({
                "server_index": server_index,
                "name": identity.get("name"),
                "x": x + dx,
                "y": y + dy,
                "sprite": sprite,
                "appearance": identity.get("appearance"),
            })

        previous_indices = {player["server_index"] for player in cache}
        current_indices = {player["server_index"] for player in players}
        removed_indices = previous_indices - current_indices
        with self.st.lock:
            moved = (x, y) != (old_x, old_y)
            self.st.x, self.st.y = x, y
            self.st.players = players
            self.st._players_initialized = True
            for server_index in removed_indices:
                self.st._player_appearances.pop(server_index, None)
        if moved:
            self.st.event("move", x=x, y=y)
        for server_index in removed_indices:
            self.st.event("player_removed", server_index=server_index)

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
        # SEND_INVENTORY wire format (PayloadCustomGenerator SEND_INVENTORY / client
        # updateInventory): byte size, then per item: short catalogID (RAW — the
        # wielded bit is NOT packed into it here, unlike the single-slot update),
        # byte wielded, byte noted (always present), int amount (only when the item is
        # stackable or noted). The old decoder read the update-item layout (wielded bit
        # in the short, no separate wielded byte) and desynced after item 1.
        i = 0
        size = p[i]; i += 1
        inv = []
        for slot in range(size):
            iid = int.from_bytes(p[i:i+2], "big"); i += 2
            wielded = bool(p[i]); i += 1
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

    def _decode_npc_update(self, p):
        # SEND_UPDATE_NPC (GameStateUpdater.updateNpcAppearances, custom client):
        # short count, then per update: short serverIndex, byte type. Type 1 = overhead
        # chat (short recipientIndex, \n-terminated string) -> npc_say event + a "npc"
        # entry in messages so `wait message --regex` can assert NPC dialogue. Combat
        # types 2/10 (damage), 3/4 (projectile), and 7 (action bubble) are recorded
        # for cadence assertions. Types 5 (skull) and 6 (wield) remain fixed-size skips.
        if len(p) < 2:
            self.st.event("decode_error", opcode=104,
                          error="truncated npc update count")
            return
        count = int.from_bytes(p[0:2], "big")
        i = 2
        for _ in range(count):
            if i + 3 > len(p):
                self.st.event("decode_error", opcode=104,
                              error="truncated npc update header")
                return
            idx = int.from_bytes(p[i:i + 2], "big")
            i += 2
            ut = p[i]
            i += 1
            if ut == 1:
                if i + 2 > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc chat recipient")
                    return
                recipient = int.from_bytes(p[i:i + 2], "big", signed=True)
                i += 2
                end = p.find(b"\n", i)
                if end < 0:
                    self.st.event("decode_error", opcode=104,
                                  error="unterminated npc chat")
                    return
                text = p[i:end].decode("latin1", "replace")
                i = end + 1
                with self.st.lock:
                    self.st._seq += 1
                    self.st.messages.append({"seq": self.st._seq,
                                             "t": round(time.time(), 3),
                                             "text": text, "type": "npc",
                                             "npc_index": idx})
                    if len(self.st.messages) > 2000:
                        self.st.messages = self.st.messages[-1200:]
                self.st.event("npc_say", server_index=idx, text=text,
                              recipient=recipient)
            elif ut in (2, 10):
                needed = 8 if ut == 10 else 3
                if i + needed > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc damage update")
                    return
                damage = p[i]
                cur_hits = p[i + 1]
                max_hits = p[i + 2]
                attacker_type = None
                attacker = None
                attacker_max_hit = None
                if ut == 10:
                    attacker_type = p[i + 3]
                    attacker = int.from_bytes(p[i + 4:i + 6], "big")
                    attacker_max_hit = int.from_bytes(p[i + 6:i + 8], "big")
                i += needed
                self.st.combat_event(
                    "npc_damage", server_index=idx,
                    npc_id=self._npc_definition_id(idx), update_type=ut,
                    attacker_server_index=attacker,
                    attacker_type=attacker_type, damage=damage,
                    cur_hits=cur_hits, max_hits=max_hits,
                    attacker_max_hit=attacker_max_hit)
            elif ut in (3, 4):
                if i + 4 > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc projectile update")
                    return
                projectile_id = int.from_bytes(p[i:i + 2], "big")
                target = int.from_bytes(p[i + 2:i + 4], "big")
                i += 4
                target_type = "npc" if ut == 3 else "player"
                self.st.combat_event(
                    "npc_projectile", source_server_index=idx,
                    source_npc_id=self._npc_definition_id(idx),
                    target_server_index=target, target_type=target_type,
                    target_npc_id=(self._npc_definition_id(target)
                                   if target_type == "npc" else None),
                    projectile_id=projectile_id, update_type=ut)
            elif ut == 5:
                if i + 1 > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc skull update")
                    return
                i += 1
            elif ut == 6:
                if i + 2 > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc wield update")
                    return
                i += 2
            elif ut == 7:
                if i + 2 > len(p):
                    self.st.event("decode_error", opcode=104,
                                  error="truncated npc action bubble")
                    return
                item_id = int.from_bytes(p[i:i + 2], "big")
                i += 2
                self.st.combat_event(
                    "npc_action_bubble", server_index=idx,
                    npc_id=self._npc_definition_id(idx), item_id=item_id,
                    update_type=ut)
            else:
                self.st.event("decode_error", opcode=104,
                              error="unknown npc update type %d" % ut)
                return

    def _npc_definition_id(self, server_index):
        """Resolve an NPC's definition id from the exact or smoothed AOI state."""
        with self.st.lock:
            for npc in self.st.npcs:
                if npc["server_index"] == server_index:
                    return npc["id"]
            recent = self.st.npc_seen.get(server_index)
            return None if recent is None else recent["id"]

    def _decode_player_appearance(self, p, offset, target):
        """Decode one custom-client type-5 identity/appearance payload."""
        i = offset

        def take(count, label):
            nonlocal i
            if i + count > len(p):
                raise ValueError("truncated player appearance %s" % label)
            value = p[i:i + count]
            i += count
            return value

        def string(label):
            nonlocal i
            end = p.find(b"\n", i)
            if end < 0:
                raise ValueError("unterminated player appearance %s" % label)
            value = p[i:end].decode("latin1", "replace")
            i = end + 1
            return value

        try:
            name = string("name")
            worn_count = take(1, "worn-item count")[0]
            worn_items = [int.from_bytes(take(2, "worn item"), "big")
                          for _ in range(worn_count)]
            hair_colour, top_colour, trouser_colour, skin_colour = take(
                4, "colours")
            combat_level, overhead_type = take(2, "combat metadata")
            clan = string("clan") if take(1, "clan flag")[0] else None
            invisible, invulnerable, group_id = take(3, "visibility metadata")
            icon = int.from_bytes(take(4, "icon"), "big", signed=True)
            title = string("title") if P.CLIENT_VERSION >= 10052 else None
            if title == "":
                title = None
            title_tier = take(1, "title tier")[0] if P.CLIENT_VERSION >= 10123 else 0
            hair_style = take(1, "hair style")[0] if P.CLIENT_VERSION >= 10057 else 0
            honorific = string("honorific") if P.CLIENT_VERSION >= 10137 else None
            if honorific == "":
                honorific = None
            honorific_tier = take(1, "honorific tier")[0] if P.CLIENT_VERSION >= 10137 else 0
        except ValueError as error:
            self.st.event("decode_error", opcode=234, error=str(error))
            return None

        appearance = {
            "worn_items": worn_items,
            "hair_colour": hair_colour,
            "top_colour": top_colour,
            "trouser_colour": trouser_colour,
            "skin_colour": skin_colour,
            "combat_level": combat_level,
            "overhead_type": overhead_type,
            "clan": clan,
            "invisible": bool(invisible),
            "invulnerable": bool(invulnerable),
            "group_id": group_id,
            "icon": icon,
            "title": title,
            "title_tier": title_tier,
            "hair_style": hair_style,
            "honorific": honorific,
            "honorific_tier": honorific_tier,
        }
        found = False
        with self.st.lock:
            if target != self.st.server_index:
                identity = {"name": name, "appearance": appearance}
                self.st._player_appearances[target] = identity
                for player in self.st.players:
                    if player["server_index"] == target:
                        player["name"] = name
                        player["appearance"] = appearance
                        found = True
                        break
        if found:
            self.st.event("player_appearance", server_index=target, name=name)
        return i

    def _decode_player_update(self, p):
        """Decode custom-client player identity, appearance, and combat updates."""
        if len(p) < 2:
            self.st.event("decode_error", opcode=234,
                          error="truncated player update count")
            return
        count = int.from_bytes(p[0:2], "big")
        i = 2
        for _ in range(count):
            if i + 3 > len(p):
                self.st.event("decode_error", opcode=234,
                              error="truncated player update header")
                return
            target = int.from_bytes(p[i:i + 2], "big")
            update_type = p[i + 2]
            i += 3
            if update_type == 0:       # action bubble
                needed = 2
            elif update_type in (1, 6, 7):
                if update_type == 1:
                    i += 4             # packed chat crown
                elif update_type == 7:
                    i += 6             # crown + muted/tutorial flags
                if i > len(p):
                    self.st.event("decode_error", opcode=234,
                                  error="truncated player chat metadata")
                    return
                end = p.find(b"\n", i)
                if end < 0:
                    self.st.event("decode_error", opcode=234,
                                  error="unterminated player chat")
                    return
                i = end + 1
                continue
            elif update_type == 2:     # legacy damage update
                needed = 3
            elif update_type == 10:    # damage + attacker feedback
                needed = 8
            elif update_type in (3, 4):  # projectile
                needed = 4
            elif update_type == 8:     # heal
                needed = 3
            elif update_type == 9:     # hp synchronization
                needed = 2
            elif update_type == 5:
                i = self._decode_player_appearance(p, i, target)
                if i is None:
                    return
                continue
            else:
                self.st.event("decode_error", opcode=234,
                              error="unsupported player update type %d" % update_type)
                return
            if i + needed > len(p):
                self.st.event("decode_error", opcode=234,
                              error="truncated player update type %d" % update_type)
                return
            if update_type in (2, 10):
                damage = p[i]
                cur_hits = p[i + 1]
                max_hits = p[i + 2]
                attacker_type = None
                attacker = None
                attacker_max_hit = None
                if update_type == 10:
                    attacker_type = p[i + 3]
                    attacker = int.from_bytes(p[i + 4:i + 6], "big")
                    attacker_max_hit = int.from_bytes(p[i + 6:i + 8], "big")
                self.st.event("player_damage", target_server_index=target,
                              attacker_server_index=attacker,
                              attacker_type=attacker_type, damage=damage,
                              cur_hits=cur_hits, max_hits=max_hits,
                              attacker_max_hit=attacker_max_hit)
                if target == self.st.server_index:
                    with self.st.lock:
                        hits = self.st.skills.setdefault("hits", {})
                        hits["cur"] = cur_hits
                        hits["max"] = max_hits
            elif update_type == 9:
                self.st.event("player_hp", target_server_index=target,
                              cur_hits=p[i], max_hits=p[i + 1])
                if target == self.st.server_index:
                    with self.st.lock:
                        hits = self.st.skills.setdefault("hits", {})
                        hits["cur"] = p[i]
                        hits["max"] = p[i + 1]
            i += needed

    @staticmethod
    def _duel_proof_hex(text, byte_count):
        if (not isinstance(text, str) or len(text) != byte_count * 2
                or re.fullmatch(r"[0-9a-f]+", text) is None):
            raise ValueError("invalid duel proof hex")
        return bytes.fromhex(text)

    @staticmethod
    def _duel_proof_player_id(text):
        if (not isinstance(text, str) or re.fullmatch(r"[0-9]+", text) is None
                or (len(text) > 1 and text.startswith("0"))):
            raise ValueError("invalid duel proof player id")
        player_id = int(text)
        if player_id <= 0 or player_id > 0x7FFFFFFF:
            raise ValueError("invalid duel proof player id")
        return player_id

    @staticmethod
    def _duel_proof_decimal(text, minimum, maximum):
        if (not isinstance(text, str) or re.fullmatch(r"[0-9]+", text) is None
                or (len(text) > 1 and text.startswith("0"))):
            raise ValueError("invalid duel proof decimal")
        value = int(text)
        if value < minimum or value > maximum:
            raise ValueError("invalid duel proof decimal")
        return value

    @staticmethod
    def _wipe_duel_proof_bytes(value):
        if isinstance(value, bytearray):
            for index in range(len(value)):
                value[index] = 0

    def _clear_duel_proof(self):
        proof = getattr(self, "_duel_proof", None)
        if proof is not None:
            self._wipe_duel_proof_bytes(proof.get("client_seed"))
            proof["client_seed"] = None
            chunks = proof.get("context_chunks")
            if isinstance(chunks, list):
                for chunk in chunks:
                    self._wipe_duel_proof_bytes(chunk)
            proof["context_chunks"] = None
            self._wipe_duel_proof_bytes(proof.get("context_bytes"))
            proof["context_bytes"] = None
        self._duel_proof = None

    def _send_duel_proof(self, response):
        payload = P.BitWriter().u8(P.DUEL_PROOF_OPTION).rsstr(response).b
        self.send("INTERFACE_OPTIONS", payload)

    def _send_duel_proof_fail(self, proof_id, reason):
        if reason not in ("entropy", "malformed", "state"):
            reason = "malformed"
        try:
            self._duel_proof_hex(proof_id, 16)
        except ValueError:
            return
        self._send_duel_proof("v1|fail|%s|%s" % (proof_id, reason))

    def _reject_duel_proof(self, proof_id, reason):
        try:
            self._duel_proof_hex(proof_id, 16)
        except ValueError:
            return
        self._clear_duel_proof()
        if reason not in ("entropy", "malformed", "state"):
            reason = "malformed"
        self._duel_proof = {
            "phase": "rejected",
            "proof_id_text": proof_id,
            "rejected_reason": reason,
        }
        self._send_duel_proof_fail(proof_id, reason)

    def _handle_duel_proof_context(self, parts):
        if len(parts) != 6:
            raise ValueError("malformed context")
        proof_id_text = parts[2]
        self._duel_proof_hex(proof_id_text, 16)
        chunk_index = self._duel_proof_decimal(
            parts[3], 0, P.DUEL_PROOF_CONTEXT_MAX_CHUNKS - 1)
        chunk_total = self._duel_proof_decimal(
            parts[4], 1, P.DUEL_PROOF_CONTEXT_MAX_CHUNKS)
        encoded = parts[5]
        if (chunk_index >= chunk_total or not encoded or len(encoded) % 2 != 0
                or len(encoded) > P.DUEL_PROOF_CONTEXT_CHUNK_BYTES * 2
                or re.fullmatch(r"[0-9a-f]+", encoded) is None):
            raise ValueError("malformed context chunk")
        decoded = bytearray.fromhex(encoded)
        if (chunk_index < chunk_total - 1
                and len(decoded) != P.DUEL_PROOF_CONTEXT_CHUNK_BYTES):
            self._wipe_duel_proof_bytes(decoded)
            raise ValueError("non-canonical context chunk size")

        retained = False
        try:
            current = getattr(self, "_duel_proof", None)
            if (current is not None and current.get("proof_id_text") == proof_id_text
                    and current.get("phase") == "rejected"):
                self._send_duel_proof_fail(
                    proof_id_text, current.get("rejected_reason", "state"))
                return
            if current is None or current.get("proof_id_text") != proof_id_text:
                self._clear_duel_proof()
                current = {
                    "phase": "context",
                    "proof_id_text": proof_id_text,
                    "context_chunk_total": chunk_total,
                    "context_next_chunk": 0,
                    "context_byte_count": 0,
                    "context_chunks": [],
                }
                self._duel_proof = current
            elif current.get("phase") != "context":
                self._reject_duel_proof(proof_id_text, "state")
                return

            if current.get("context_chunk_total") != chunk_total:
                self._reject_duel_proof(proof_id_text, "state")
                return
            next_chunk = current.get("context_next_chunk", 0)
            if chunk_index < next_chunk:
                chunks = current.get("context_chunks") or []
                if (chunk_index >= len(chunks)
                        or not secrets.compare_digest(chunks[chunk_index], decoded)):
                    self._reject_duel_proof(proof_id_text, "state")
                return
            if chunk_index != next_chunk:
                self._reject_duel_proof(proof_id_text, "state")
                return
            byte_count = current.get("context_byte_count", 0)
            if byte_count > P.DUEL_PROOF_CONTEXT_MAX_BYTES - len(decoded):
                self._reject_duel_proof(proof_id_text, "state")
                return
            current["context_chunks"].append(decoded)
            current["context_next_chunk"] = next_chunk + 1
            current["context_byte_count"] = byte_count + len(decoded)
            retained = True
        finally:
            if not retained:
                self._wipe_duel_proof_bytes(decoded)

    def _handle_duel_proof_commit(self, control, parts):
        if len(parts) != 6:
            raise ValueError("malformed commit")
        proof_id_text = parts[2]
        proof_id = self._duel_proof_hex(proof_id_text, 16)
        context_hash = self._duel_proof_hex(parts[3], 32)
        server_commit = self._duel_proof_hex(parts[4], 32)
        if parts[5] not in ("0", "1"):
            raise ValueError("malformed ordinal")
        ordinal = int(parts[5])

        current = getattr(self, "_duel_proof", None)
        if current is not None:
            if (current.get("proof_id_text") == proof_id_text
                    and current.get("phase") == "rejected"):
                self._send_duel_proof_fail(
                    proof_id_text, current.get("rejected_reason", "state"))
                return
            if (current.get("proof_id_text") == proof_id_text
                    and current.get("commit_control") == control):
                self._send_duel_proof(current["commit_response"])
                return
            if current.get("proof_id_text") == proof_id_text:
                if current.get("phase") != "context":
                    self._reject_duel_proof(proof_id_text, "state")
                    return
            else:
                self._reject_duel_proof(proof_id_text, "state")
                return

        current = getattr(self, "_duel_proof", None)
        if (current is None or current.get("phase") != "context"
                or current.get("proof_id_text") != proof_id_text
                or current.get("context_next_chunk")
                != current.get("context_chunk_total")
                or current.get("context_byte_count", 0) <= 0):
            self._reject_duel_proof(proof_id_text, "state")
            return

        context_bytes = bytearray()
        try:
            for chunk in current.get("context_chunks") or []:
                context_bytes.extend(chunk)
            if len(context_bytes) != current.get("context_byte_count"):
                self._reject_duel_proof(proof_id_text, "state")
                return
            computed_hash = P.duel_proof_context_hash(context_bytes)
            if not secrets.compare_digest(computed_hash, context_hash):
                self._reject_duel_proof(proof_id_text, "state")
                return
            parsed_context = P.duel_proof_parse_context(context_bytes)
            if (not secrets.compare_digest(parsed_context["proof_id"], proof_id)
                    or parsed_context["participants"][ordinal]["ordinal"] != ordinal):
                self._reject_duel_proof(proof_id_text, "state")
                return
        finally:
            self._wipe_duel_proof_bytes(context_bytes)

        # Context bytes are no longer needed once their hash and structure are attested.
        self._clear_duel_proof()

        try:
            client_seed = bytearray(secrets.token_bytes(32))
            if len(client_seed) != 32:
                raise ValueError("secure entropy returned the wrong byte count")
        except Exception:
            self._reject_duel_proof(proof_id_text, "entropy")
            return
        client_commit = P.duel_proof_client_commitment(
            context_hash, server_commit, ordinal, client_seed)
        response = "v1|commit|%s|%s" % (proof_id_text, client_commit.hex())
        self._duel_proof = {
            "phase": "commit",
            "proof_id_text": proof_id_text,
            "proof_id": proof_id,
            "context_hash": context_hash,
            "server_commit": server_commit,
            "ordinal": ordinal,
            "client_seed": client_seed,
            "client_commit": client_commit,
            "commit_control": control,
            "commit_response": response,
        }
        self._send_duel_proof(response)

    def _handle_duel_proof_reveal(self, control, parts):
        if len(parts) != 7:
            raise ValueError("malformed reveal")
        proof_id_text = parts[2]
        self._duel_proof_hex(proof_id_text, 16)
        first_id = self._duel_proof_player_id(parts[3])
        first_commit = self._duel_proof_hex(parts[4], 32)
        second_id = self._duel_proof_player_id(parts[5])
        second_commit = self._duel_proof_hex(parts[6], 32)
        if first_id >= second_id:
            raise ValueError("non-canonical participant order")

        current = getattr(self, "_duel_proof", None)
        if current is None or current.get("proof_id_text") != proof_id_text:
            self._reject_duel_proof(proof_id_text, "state")
            return
        if current.get("reveal_control") == control:
            seed = current.get("client_seed")
            if seed is None:
                self._reject_duel_proof(proof_id_text, "state")
                return
            self._send_duel_proof("v1|reveal|%s|%s" % (proof_id_text, seed.hex()))
            return
        if current.get("phase") != "commit":
            self._reject_duel_proof(proof_id_text, "state")
            return

        own_commit = first_commit if current["ordinal"] == 0 else second_commit
        if not secrets.compare_digest(own_commit, current["client_commit"]):
            self._reject_duel_proof(proof_id_text, "state")
            return

        current.update({
            "phase": "reveal",
            "reveal_control": control,
            "first_id": first_id,
            "first_commit": first_commit,
            "second_id": second_id,
            "second_commit": second_commit,
        })
        self._send_duel_proof("v1|reveal|%s|%s" %
                              (proof_id_text, current["client_seed"].hex()))

    def _handle_duel_proof_lock(self, control, parts):
        if len(parts) != 10:
            raise ValueError("malformed lock")
        proof_id_text = parts[2]
        proof_id = self._duel_proof_hex(proof_id_text, 16)
        context_hash = self._duel_proof_hex(parts[3], 32)
        server_commit = self._duel_proof_hex(parts[4], 32)
        first_id = self._duel_proof_player_id(parts[5])
        first_commit = self._duel_proof_hex(parts[6], 32)
        second_id = self._duel_proof_player_id(parts[7])
        second_commit = self._duel_proof_hex(parts[8], 32)
        lock_hash = self._duel_proof_hex(parts[9], 32)
        if first_id >= second_id:
            raise ValueError("non-canonical participant order")

        current = getattr(self, "_duel_proof", None)
        if current is None or current.get("proof_id_text") != proof_id_text:
            self._reject_duel_proof(proof_id_text, "state")
            return
        if current.get("lock_control") == control:
            self._send_duel_proof(current["ack_response"])
            return
        if current.get("phase") != "reveal":
            self._reject_duel_proof(proof_id_text, "state")
            return
        if (not secrets.compare_digest(context_hash, current["context_hash"])
                or not secrets.compare_digest(server_commit, current["server_commit"])
                or first_id != current["first_id"]
                or second_id != current["second_id"]
                or not secrets.compare_digest(first_commit, current["first_commit"])
                or not secrets.compare_digest(second_commit, current["second_commit"])):
            self._reject_duel_proof(proof_id_text, "state")
            return

        expected_lock = P.duel_proof_final_lock(
            proof_id, context_hash, server_commit,
            first_id, first_commit, second_id, second_commit)
        if not secrets.compare_digest(lock_hash, expected_lock):
            self._reject_duel_proof(proof_id_text, "state")
            return

        response = "v1|ack|%s|%s" % (proof_id_text, expected_lock.hex())
        current.update({
            "phase": "lock",
            "lock_control": control,
            "ack_response": response,
        })
        self._send_duel_proof(response)

    def _handle_duel_proof_control(self, control):
        """Consume one hidden proof control without surfacing it as player chat."""
        parts = control.split("|") if isinstance(control, str) else []
        proof_id = parts[2] if len(parts) > 2 else None
        try:
            if (not isinstance(control, str) or not 0 < len(control) <= 512
                    or any(ord(character) < 0x20 or ord(character) > 0x7E
                           for character in control)):
                raise ValueError("non-canonical duel proof control text")
            if len(parts) < 2 or parts[0] != "v1":
                raise ValueError("unsupported duel proof control")
            phase = parts[1]
            if phase == "context":
                self._handle_duel_proof_context(parts)
            elif phase == "commit":
                self._handle_duel_proof_commit(control, parts)
            elif phase == "reveal":
                self._handle_duel_proof_reveal(control, parts)
            elif phase == "lock":
                self._handle_duel_proof_lock(control, parts)
            elif phase == "abort":
                if (len(parts) != 4 or parts[3] not in (
                        "timeout", "database", "entropy", "malformed", "state",
                        "disconnected", "cancelled", "unsupported", "items",
                        "unreachable", "server-restart", "retreat")):
                    raise ValueError("malformed abort")
                self._duel_proof_hex(parts[2], 16)
                current = getattr(self, "_duel_proof", None)
                if current is not None and current.get("proof_id_text") == parts[2]:
                    self._clear_duel_proof()
            else:
                raise ValueError("unsupported duel proof phase")
        except (IndexError, TypeError, ValueError):
            current = getattr(self, "_duel_proof", None)
            candidate = proof_id
            try:
                self._duel_proof_hex(candidate, 16)
            except (TypeError, ValueError):
                candidate = current.get("proof_id_text") if current is not None else None
            if candidate is not None:
                self._reject_duel_proof(candidate, "malformed")

    @staticmethod
    def _arena_flag(value):
        if value not in ("0", "1"):
            raise ValueError("invalid Void Arena boolean")
        return value == "1"

    def _refresh_arena_phase(self, now=None):
        now = time.time() if now is None else now
        started = False
        with self.st.lock:
            arena = self.st.arena
            if (arena["phase"] == "countdown"
                    and arena["countdown_end"] is not None
                    and now >= arena["countdown_end"]):
                arena["phase"] = "started"
                started = True
        if started:
            self.st.event("arena_started")

    def arena_snapshot(self):
        self._refresh_arena_phase()
        with self.st.lock:
            arena = dict(self.st.arena)
            arena["rules"] = dict(self.st.arena["rules"])
            return arena

    def _handle_arena_control(self, payload):
        """Decode the hidden @vsarena@ stream consumed by the stock client UI."""
        now = time.time()
        try:
            if payload == "clear":
                with self.st.lock:
                    self.st.arena = new_arena_state()
                self.st.event("arena_clear")
                return
            if payload == "close":
                self._refresh_arena_phase(now)
                with self.st.lock:
                    arena = self.st.arena
                    active = arena["phase"] in ("countdown", "started")
                    arena["phase"] = "ended" if active else "closed"
                    arena["own_accepted"] = False
                    arena["opponent_accepted"] = False
                    arena["own_confirmed"] = False
                    arena["opponent_confirmed"] = False
                    arena["countdown_seconds"] = None
                    arena["countdown_end"] = None
                    arena["ended_at"] = now if active else None
                self.st.event("arena_ended" if active else "arena_closed")
                return

            parts = payload.split("|", 10)
            if len(parts) >= 2 and parts[0] == "countdown":
                seconds = max(1, int(parts[1]))
                with self.st.lock:
                    self.st.arena["phase"] = "countdown"
                    self.st.arena["countdown_seconds"] = seconds
                    self.st.arena["countdown_end"] = now + seconds
                    self.st.arena["ended_at"] = None
                self.st.event("arena_countdown", seconds=seconds)
                return
            if len(parts) >= 10 and parts[0] == "setup":
                target = int(parts[1])
                mask = int(parts[3])
                if target < 0 or target > 0xFFFF or mask & ~P.ARENA_RULE_MASK:
                    raise ValueError("invalid Void Arena setup fields")
                confirm_phase = self._arena_flag(parts[6])
                arena = {
                    "phase": "confirm" if confirm_phase else "setup",
                    "opponent_server_index": target,
                    "opponent_name": parts[2],
                    "rules": arena_rules_from_mask(mask),
                    "own_accepted": self._arena_flag(parts[4]),
                    "opponent_accepted": self._arena_flag(parts[5]),
                    "own_confirmed": self._arena_flag(parts[7]),
                    "opponent_confirmed": self._arena_flag(parts[8]),
                    "ranked_available": self._arena_flag(parts[9]),
                    "countdown_seconds": None,
                    "countdown_end": None,
                    "ended_at": None,
                }
                with self.st.lock:
                    self.st.arena = arena
                self.st.event("arena_confirm" if confirm_phase else "arena_setup",
                              opponent_server_index=target, opponent_name=parts[2])
        except (TypeError, ValueError) as e:
            self.st.event("decode_error", opcode=131,
                          error="invalid Void Arena control: %s" % e)

    def _decode_message(self, p):
        # int icon, byte type, byte infobits, string msg, [sender,sender], [color]
        if len(p) < 6:
            return
        i = 4
        mtype = p[i]; i += 1
        info = p[i]; i += 1
        end = p.find(b"\n", i)
        if end < 0:
            end = len(p)
        text = p[i:end].decode("latin1", "replace")
        if text.startswith(P.ARENA_CONTROL_PREFIX):
            self._handle_arena_control(text[len(P.ARENA_CONTROL_PREFIX):])
            return
        if (mtype == 3 and (info & 1) == 0
                and text.startswith(P.DUEL_PROOF_PREFIX)):
            self._handle_duel_proof_control(text[len(P.DUEL_PROOF_PREFIX):])
            return
        plain_text = re.sub(r"@[A-Za-z0-9]{3}@", "", text).strip()
        duel_result = None
        with self.st.lock:
            self.st._seq += 1
            self.st.messages.append({"seq": self.st._seq, "t": round(time.time(), 3),
                                     "text": text, "type": mtype})
            if len(self.st.messages) > 2000:
                self.st.messages = self.st.messages[-1200:]
            if plain_text == "Commencing Duel!":
                self.st.duel["phase"] = "combat"
                self.st.duel["outcome"] = None
            elif (self.st.duel.get("phase") == "combat"
                  and plain_text.startswith("You have defeated ")
                  and plain_text.endswith("!")):
                self.st.duel["phase"] = "complete"
                self.st.duel["outcome"] = "won"
                duel_result = "won"
        self.st.event("message", text=text, type=mtype)
        if plain_text == "Commencing Duel!":
            self.st.event("duel_started",
                          opponent_server_index=self.st.duel.get("opponent_server_index"))
        elif duel_result is not None:
            self.st.event("duel_complete", outcome=duel_result)

    def _decode_duel_items(self, p):
        """Decode the server echo of the opponent's complete stake offer."""
        if not p:
            return
        count = p[0]
        i = 1
        items = []
        for slot in range(count):
            needed = 7 if self.want_bank_notes else 6
            if i + needed > len(p):
                self.st.event("decode_error", opcode=6, error="truncated duel item list")
                return
            iid = int.from_bytes(p[i:i + 2], "big"); i += 2
            noted = False
            if self.want_bank_notes:
                noted = p[i] == 1; i += 1
            amount = int.from_bytes(p[i:i + 4], "big"); i += 4
            items.append({"slot": slot, "id": iid, "name": self.names.get(iid, ""),
                          "amount": amount, "noted": noted})
        with self.st.lock:
            self.st.duel["opponent_offer"] = items
            self.st.duel["own_accepted"] = False
            self.st.duel["opponent_accepted"] = False
        self.st.event("duel_opponent_offer", items=items)

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
        # [short slot][short id][int amount]; amount 0 means the slot was emptied.
        # Slot was one byte before client 10121 (it wrapped mod 256 — VS-008), so
        # honor the negotiated version when probing older gates.
        if P.CLIENT_VERSION >= 10121:
            slot, off = int.from_bytes(p[0:2], "big"), 2
        else:
            slot, off = p[0], 1
        iid = int.from_bytes(p[off:off + 2], "big")
        amt = int.from_bytes(p[off + 2:off + 6], "big")
        with self.st.lock:
            bank = [b for b in self.st.bank if b["slot"] != slot]
            if amt > 0:
                bank.append({"slot": slot, "id": iid, "amount": amt,
                             "name": self.names.get(iid, "")})
            else:
                # amount 0 = remove + compact: the client (BankInterface.updateBank)
                # deletes the slot and shifts every later item down one (VS-031).
                for b in bank:
                    if b["slot"] > slot:
                        b["slot"] -= 1
            bank.sort(key=lambda b: b["slot"])
            self.st.bank = bank
        self.st.event("bank_update", slot=slot, id=iid, amount=amt)

    def _decode_grounditems(self, p):
        # Incremental add/remove delta stream (mirrors client PacketHandler
        # .drawGroundItems) — NOT a full snapshot, so we apply deltas to persistent
        # state instead of rebuilding. Per entry, peek one byte:
        #   != 255: short id (0x8000 bit = remove-this-item), byte dx, byte dy,
        #           [byte noted if want_bank_notes], [byte beam if client>=10030].
        #           x=localX+dx, y=localY+dy. id&0x8000 -> remove (x,y,id&0x7FFF);
        #           else add.
        #   == 255: byte dx, byte dy, [byte noted], [byte beam] — region-clear: drop
        #           every known item whose 8-tile region equals ((localX+dx)>>3,
        #           (localY+dy)>>3) (item scrolled out of range / was taken elsewhere).
        # The old decoder ignored the noted+beam bytes (desyncing every multi-entry
        # packet) and rebuilt the list each packet (so taken items never disappeared
        # and removes became phantom adds).
        st = self.st
        bx = st.x or 0
        by = st.y or 0
        i = 0
        n = len(p)
        with st.lock:
            ground = list(st.ground_items)
            try:
                while i < n:
                    marker = p[i]
                    if marker != 255:
                        raw = int.from_bytes(p[i:i+2], "big"); i += 2
                        dx = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                        dy = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                        noted = False
                        if self.want_bank_notes:
                            noted = p[i] == 1; i += 1
                        if self.ground_has_beam:
                            i += 1
                        x, y = bx + dx, by + dy
                        iid = raw & 0x7FFF
                        if raw & 0x8000:
                            ground = [g for g in ground if not
                                      (g["x"] == x and g["y"] == y and g["id"] == iid)]
                        else:
                            ground.append({"id": iid, "x": x, "y": y,
                                           "noted": noted, "name": self.names.get(iid, "")})
                    else:
                        i += 1  # consume the 255 marker
                        dx = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                        dy = p[i] - 256 if p[i] > 127 else p[i]; i += 1
                        if self.want_bank_notes:
                            i += 1
                        if self.ground_has_beam:
                            i += 1
                        rx, ry = (bx + dx) >> 3, (by + dy) >> 3
                        ground = [g for g in ground if not
                                  ((g["x"] >> 3) == rx and (g["y"] >> 3) == ry)]
            except IndexError:
                pass
            st.ground_items = ground
            count = len(ground)
        self.st.event("grounditems", count=count)

    def _decode_world_walk_route(self, p):
        if len(p) < 4:
            self.st.event("decode_error", opcode=100, error="short world-walk route")
            return
        ok = p[0] != 0
        reason = p[1]
        count = int.from_bytes(p[2:4], "big")
        route = []
        off = 4
        for _ in range(count):
            if off + 4 > len(p):
                self.st.event("decode_error", opcode=100, error="truncated world-walk route")
                return
            route.append({"x": int.from_bytes(p[off:off + 2], "big"),
                          "y": int.from_bytes(p[off + 2:off + 4], "big")})
            off += 4
        with self.st.lock:
            self.st.world_walk_route_seq += 1
            route_seq = self.st.world_walk_route_seq
            data = {
                "seq": route_seq,
                "t": round(time.monotonic(), 3),
                "ok": ok,
                "reason": reason,
                "count": count,
                "route": route,
            }
            self.st.world_walk_route = data
        self.st.event("world_walk_route", ok=ok, reason=reason, count=count,
                      route_seq=route_seq,
                      first=route[0] if route else None,
                      last=route[-1] if route else None,
                      route=route)

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

    def object_prewalk_tile(self, x, y, object_id=None, direction=0):
        if object_id is None or self.st.x is None or self.st.y is None:
            return None
        obj_def = self.object_defs.get(int(object_id))
        if not obj_def:
            return None
        width = obj_def["width"]
        height = obj_def["height"]
        if direction not in (0, 4):
            width, height = height, width
        min_x, min_y = int(x), int(y)
        max_x, max_y = min_x + width - 1, min_y + height - 1

        # The client walks to the edge of the object's footprint, then sends the
        # object command. Pick the nearest perimeter tile outside the footprint.
        candidates = []
        for tx in range(min_x - 1, max_x + 2):
            candidates.append((tx, min_y - 1))
            candidates.append((tx, max_y + 1))
        for ty in range(min_y, max_y + 1):
            candidates.append((min_x - 1, ty))
            candidates.append((max_x + 1, ty))
        candidates = [p for p in candidates if p[0] >= 0 and p[1] >= 0]
        candidates.sort(key=lambda p: abs(p[0] - self.st.x) + abs(p[1] - self.st.y))
        return candidates[0] if candidates else None

    def duel_snapshot(self):
        with self.st.lock:
            duel = dict(self.st.duel)
            duel["own_offer"] = [dict(item) for item in self.st.duel["own_offer"]]
            duel["opponent_offer"] = [dict(item) for item in self.st.duel["opponent_offer"]]
            duel["settings"] = dict(self.st.duel["settings"])
            return duel

    def _arena_target(self, args, command):
        target = args.get("server_index", args.get("serverIndex"))
        if target is None:
            with self.st.lock:
                target = self.st.arena.get("opponent_server_index")
        if target is None:
            raise UsageError("%s requires a player server index or active arena setup" % command)
        try:
            target = int(target)
        except (TypeError, ValueError):
            raise UsageError("%s server index must be an integer" % command)
        if target < 0 or target > 0xFFFF:
            raise UsageError("%s server index must be 0..65535" % command)
        return target

    def _send_arena_action(self, action, target, rule_mask):
        payload = (P.BitWriter().u8(P.ARENA_INTERFACE_OPTION).u8(action)
                   .u8(rule_mask).u16(target).b)
        self.send("INTERFACE_OPTIONS", payload)

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
                self.send("WALK_TO_ENTITY",
                          P.BitWriter().u16(n["x"]).u16(n["y"]).b)
                self.send("NPC_ATTACK1", P.BitWriter().u16(n["server_index"]).b)
                return {"ok": True, "npc": n}
            if cmd == "attack-player":
                self.send("PLAYER_ATTACK", P.BitWriter().u16(int(a["server_index"])).b)
                return {"ok": True}
            if cmd == "follow-player":
                server_index = int(a["server_index"])
                if server_index < 0 or server_index > 0xFFFF:
                    raise UsageError("follow-player server index must be 0..65535")
                self.send("PLAYER_FOLLOW", P.BitWriter().u16(server_index).b)
                return {"ok": True, "server_index": server_index}
            if cmd == "friend-add":
                name = str(a.get("name", "")).strip()
                if not name:
                    raise UsageError("friend-add requires a player name")
                self.send("SOCIAL_ADD_FRIEND", P.BitWriter().rsstr(name).b)
                return {"ok": True, "name": name}
            if cmd == "combat-style":
                style = combat_style_id(a.get("style"))
                self.send("COMBAT_STYLE_CHANGED", P.BitWriter().u8(style).b)
                with st.lock:
                    st.combat_style = style
                style_name = next(name for name, sid in COMBAT_STYLES.items() if sid == style)
                st.event("combat_style_sent", style=style, name=style_name)
                return {"ok": True, "style": style, "name": style_name}
            if cmd in ("prayer-on", "prayer-off"):
                prayer = prayer_id(a.get("id", a.get("prayer_id")))
                active = cmd == "prayer-on"
                self.send("PRAYER_ACTIVATED" if active else "PRAYER_DEACTIVATED",
                          P.BitWriter().u8(prayer).b)
                return {"ok": True, "prayer_id": prayer,
                        "name": P.PRAYER_NAMES[prayer], "active": active}
            if cmd == "duel-request":
                server_index = int(a["server_index"])
                if server_index < 0 or server_index > 0xFFFF:
                    raise UsageError("duel-request server index must be 0..65535")
                self.send("PLAYER_DUEL", P.BitWriter().u16(server_index).b)
                return {"ok": True, "server_index": server_index}
            if cmd == "duel-offer":
                items = parse_duel_offer_specs(a.get("items", []))
                w = P.BitWriter().u8(len(items))
                for item in items:
                    w.u16(item["id"]).u32(item["amount"]).u16(1 if item["noted"] else 0)
                self.send("DUEL_OFFER_ITEM", w.b)
                with st.lock:
                    st.duel["own_offer"] = [dict(item, slot=slot)
                                             for slot, item in enumerate(items)]
                    st.duel["own_accepted"] = False
                    st.duel["opponent_accepted"] = False
                return {"ok": True, "items": items}
            if cmd == "duel-settings":
                settings = {
                    "no_retreat": command_flag(a.get("no_retreat")),
                    "no_magic": command_flag(a.get("no_magic")),
                    "no_prayer": command_flag(a.get("no_prayer")),
                    "no_weapons": command_flag(a.get("no_weapons")),
                }
                self.send("DUEL_FIRST_SETTINGS_CHANGED",
                          P.BitWriter().u8(settings["no_retreat"])
                          .u8(settings["no_magic"])
                          .u8(settings["no_prayer"])
                          .u8(settings["no_weapons"]).b)
                return {"ok": True, "settings": settings}
            if cmd in ("duel-accept", "duel-first-accept"):
                self.send("DUEL_FIRST_ACCEPTED")
                return {"ok": True, "stage": "offer"}
            if cmd in ("duel-confirm", "duel-confirm-accept"):
                self.send("DUEL_SECOND_ACCEPTED")
                return {"ok": True, "stage": "confirm"}
            if cmd == "duel-decline":
                self.send("DUEL_DECLINED")
                return {"ok": True}
            if cmd == "arena-challenge":
                target = self._arena_target(a, cmd)
                self._send_arena_action(P.ARENA_ACTION_CHALLENGE, target,
                                        P.ARENA_RULE_RANKED)
                return {"ok": True, "server_index": target}
            if cmd == "arena-rules":
                target = self._arena_target(a, cmd)
                mask = arena_rule_mask(a)
                self._send_arena_action(P.ARENA_ACTION_UPDATE_RULES, target, mask)
                return {"ok": True, "server_index": target,
                        "rules": arena_rules_from_mask(mask)}
            if cmd in ("arena-accept", "arena-confirm", "arena-decline"):
                target = self._arena_target(a, cmd)
                with st.lock:
                    mask = st.arena["rules"]["mask"]
                action = {
                    "arena-accept": P.ARENA_ACTION_ACCEPT,
                    "arena-confirm": P.ARENA_ACTION_CONFIRM,
                    "arena-decline": P.ARENA_ACTION_DECLINE,
                }[cmd]
                self._send_arena_action(action, target, mask)
                return {"ok": True, "server_index": target}
            if cmd == "trade-request":
                server_index = int(a["server_index"])
                self.send("PLAYER_INIT_TRADE_REQUEST", P.BitWriter().u16(server_index).b)
                return {"ok": True, "server_index": server_index}
            if cmd == "take-item":
                self.send("GROUND_ITEM_TAKE",
                          P.BitWriter().u16(int(a["x"])).u16(int(a["y"])).u16(int(a["id"])).b)
                return {"ok": True}
            if cmd == "object-action":
                x = int(a["x"])
                y = int(a["y"])
                which = int(a.get("which", a.get("option", 1)))
                walk_tile = None
                if "walk_x" in a and "walk_y" in a:
                    walk_tile = (int(a["walk_x"]), int(a["walk_y"]))
                    self.send("WALK_TO_ENTITY", P.BitWriter().u16(int(a["walk_x"])).u16(int(a["walk_y"])).b)
                else:
                    walk_tile = self.object_prewalk_tile(x, y, a.get("id"), int(a.get("direction", 0)))
                    if walk_tile:
                        self.send("WALK_TO_ENTITY", P.BitWriter().u16(walk_tile[0]).u16(walk_tile[1]).b)
                opcode = "OBJECT_COMMAND1" if which == 1 else "OBJECT_COMMAND2"
                self.send(opcode, P.BitWriter().u16(x).u16(y).b)
                return {"ok": True, "x": x, "y": y, "which": which, "walk_tile": walk_tile}
            if cmd == "cast-object":
                self.send("CAST_ON_SCENERY", P.BitWriter().u16(int(a["spell"]))
                          .u16(int(a["x"])).u16(int(a["y"])).b)
                return {"ok": True, "spell": int(a["spell"]), "x": int(a["x"]), "y": int(a["y"])}
            if cmd == "cast-self":
                spell = int(a["spell"])
                self.send("CAST_ON_SELF", P.BitWriter().u16(spell).b)
                return {"ok": True, "spell": spell}
            if cmd == "cast-player":
                spell = int(a["spell"])
                server_index = int(a["server_index"])
                self.send("PLAYER_CAST_PVP", P.BitWriter().u16(spell).u16(server_index).b)
                return {"ok": True, "spell": spell, "server_index": server_index}
            if cmd == "cast-npc":
                spell = int(a["spell"])
                server_index = int(a["server_index"])
                self.send("CAST_ON_NPC", P.BitWriter().u16(spell).u16(server_index).b)
                return {"ok": True, "spell": spell, "server_index": server_index}
            if cmd == "use-on-item":
                # ITEM_USE_ITEM: combine two inventory items (herblaw, fletching, gem
                # cutting, firemaking tinderbox-on-logs, potion mixing). Payload: two
                # inventory slots (wire length 4).
                self.send("ITEM_USE_ITEM",
                          P.BitWriter().u16(int(a["slot1"])).u16(int(a["slot2"])).b)
                return {"ok": True, "slot1": int(a["slot1"]), "slot2": int(a["slot2"])}
            if cmd == "use-item-on-object":
                # USE_ITEM_ON_SCENERY (wire 115): use an inventory item on a scenery
                # object (smelting ore on furnace, smithing bar on anvil, cooking on a
                # range, crafting on a wheel). Prewalk to the object edge like the real
                # client, then send objectX, objectY, slot.
                x = int(a["x"]); y = int(a["y"]); slot = int(a["slot"])
                walk_tile = self.object_prewalk_tile(x, y, a.get("id"), int(a.get("direction", 0)))
                if walk_tile:
                    self.send("WALK_TO_POINT", P.BitWriter().u16(walk_tile[0]).u16(walk_tile[1]).b)
                self.send("OBJECT_USE_ITEM", P.BitWriter().u16(x).u16(y).u16(slot).b)
                return {"ok": True, "x": x, "y": y, "slot": slot, "walk_tile": walk_tile}
            if cmd == "use-item-on-npc":
                # NPC_USE_ITEM (wire 135): use an inventory item on an NPC (quests,
                # cert/note exchange). Payload: NPC server index, inventory slot.
                si = int(a.get("server_index", a.get("serverIndex")))
                self.send("NPC_USE_ITEM", P.BitWriter().u16(si).u16(int(a["slot"])).b)
                return {"ok": True, "server_index": si, "slot": int(a["slot"])}
            if cmd == "use-item-on-player":
                # PLAYER_USE_ITEM (wire 113): use an inventory item on a player.
                # Payload: target player server index, inventory slot.
                si = int(a.get("server_index", a.get("serverIndex")))
                slot = int(a["slot"])
                self.send("PLAYER_USE_ITEM", P.BitWriter().u16(si).u16(slot).b)
                return {"ok": True, "server_index": si, "slot": slot}
            if cmd == "use-item-on-ground":
                # GROUND_ITEM_USE_ITEM (wire 53): use a carried item on a ground item
                # (classic: light dropped logs with a tinderbox). Payload: groundX,
                # groundY, inventory slot, ground-item id. Walk onto the tile first.
                x = int(a["x"]); y = int(a["y"]); slot = int(a["slot"]); gid = int(a["ground_id"])
                self.send("WALK_TO_POINT", P.BitWriter().u16(x).u16(y).b)
                self.send("GROUND_ITEM_USE_ITEM",
                          P.BitWriter().u16(x).u16(y).u16(slot).u16(gid).b)
                return {"ok": True, "x": x, "y": y, "slot": slot, "ground_id": gid}
            if cmd == "say":
                txt = a["text"]
                self.send("CHAT_MESSAGE", P.encrypted_chat_payload(txt))
                return {"ok": True}
            if cmd == "admin" or cmd == "command":
                c = a["command"].lstrip(":")
                self.send("COMMAND", P.BitWriter().rsstr(c).b)
                return {"ok": True, "sent": c}
            if cmd == "design-character":
                # PayloadCustomParser PLAYER_APPEARANCE_CHANGE wire order.
                # Valid: headType 0/3/5/6/7, bodyType 1/4, hair 10-17,
                # top/trousers 0-22, skin 0-4, hairStyle 0.
                male = str(a.get("gender", "male")).lower() != "female"
                w = (P.BitWriter()
                     .u8(1 if male else 0)
                     .u8(int(a.get("head", 0)))
                     .u8(int(a.get("body", 1)))
                     .u8(2)
                     .u8(int(a.get("hair_colour", 10)))
                     .u8(int(a.get("top_colour", 8)))
                     .u8(int(a.get("trouser_colour", 14)))
                     .u8(int(a.get("skin_colour", 0)))
                     .u8(int(a.get("ironman", 0)))
                     .u8(int(a.get("one_xp", 0)))
                     .u8(int(a.get("hair_style", 0))))
                if P.CLIENT_VERSION >= 10125:
                    country = str(a.get("country", "")).strip().upper()
                    if country in ("", "NONE"):
                        w.u8(0).u8(0)
                    elif len(country) == 2 and country.isalpha():
                        w.u8(ord(country[0])).u8(ord(country[1]))
                    else:
                        return {"ok": False, "error": "country must be two letters or none"}
                self.send("PLAYER_APPEARANCE_CHANGE", w.b)
                with st.lock:
                    st.appearance_open = False
                st.event("appearance_submit")
                return {"ok": True, "country": a.get("country", "none")}
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
                # want_bank_notes servers always read a trailing noted byte, so send it
                # unconditionally (0 unless --noted); omitting it made the server parse
                # a short packet and silently drop un-noted withdrawals.
                w = P.BitWriter().u16(int(a["id"])).u32(int(a["amount"]))
                if self.want_bank_notes:
                    w.u8(1 if a.get("noted") else 0)
                elif a.get("noted"):
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
            if cmd == "shop-sell":
                catalog_id, stock, amount = parse_shop_sell_args(a)
                payload = P.BitWriter().u16(catalog_id).u16(stock).u16(amount).b
                self.send("SHOP_SELL", payload)
                return {"ok": True, "id": catalog_id, "stock": stock, "amount": amount}
            if cmd == "shop-close":
                self.send("SHOP_CLOSE")
                return {"ok": True}
            if cmd == "auction-delete":
                self.send("INTERFACE_OPTIONS", P.BitWriter().u8(10).u8(5).u32(int(a["id"])).b)
                return {"ok": True, "auction_id": int(a["id"])}
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
                self.request_shutdown("control")
                return {"ok": True, "shutdown": True}

            return {"ok": False, "error": "unknown command: %s" % cmd}
        except UsageError as e:
            return {"ok": False, "error": "usage: %s" % e}
        except Exception as e:
            return {"ok": False, "error": "%s: %s" % (type(e).__name__, e)}

    def snapshot(self, section):
        st = self.st
        # Keep the raw AOI frame available for exact observations, and expose the
        # existing short-lived cache separately for safety callers that must tolerate
        # a single dropped NPC_COORDS frame.
        with st.lock:
            # GameState uses an RLock, so reuse the canonical cache filter while the
            # same state lock keeps raw and recent views from crossing decode frames.
            recent_npcs = self._recent_npcs()
            duel = self.duel_snapshot()
            arena = self.arena_snapshot()
            full = {
                "connected": st.connected, "logged_in": st.logged_in,
                "login_response": st.login_response, "username": st.username,
                "appearance": {"open": st.appearance_open},
                "server_index": st.server_index,
                "position": {"x": st.x, "y": st.y, "server_index": st.server_index},
                "fatigue": st.fatigue,
                "combat_style": st.combat_style,
                "prayers": {
                    "states": (None if st.prayers["states"] is None
                               else list(st.prayers["states"])),
                    "active": list(st.prayers["active"]),
                },
                "combat_events": [dict(event) for event in st.combat_events],
                "duel": duel,
                "arena": arena,
                "skills": st.skills, "quest_points": st.quest_points,
                "inventory": list(st.inventory),
                "bank": {"open": st.bank_open, "items": list(st.bank)},
                "shop": {"open": st.shop_open},
                "dialog": {"open": st.dialog_open, "options": list(st.dialog_options),
                           "input_open": st.input_open, "input_prompt": st.input_prompt},
                "friends": [dict(friend) for friend in st.friends],
                "players": [self._copy_player(player) for player in st.players],
                "npcs": list(st.npcs),
                "recent_npcs": recent_npcs,
                "ground_items": list(st.ground_items),
                "world_walk_route": st.world_walk_route,
                "messages": st.messages[-30:],
            }
        if section in (None, "all"):
            return full
        aliases = {"position": "position", "pos": "position", "inventory": "inventory",
                   "inv": "inventory", "stats": "skills", "skills": "skills",
                   "players": "players", "npcs": "npcs",
                   "recent-npcs": "recent_npcs", "recent_npcs": "recent_npcs",
                   "ground-items": "ground_items", "bank": "bank",
                   "appearance": "appearance",
                   "friends": "friends",
                   "dialog": "dialog", "messages": "messages", "shop": "shop",
                   "duel": "duel", "arena": "arena", "combat-style": "combat_style",
                   "combat_style": "combat_style",
                   "prayers": "prayers", "combat-events": "combat_events",
                   "combat_events": "combat_events",
                   "world-walk-route": "world_walk_route",
                   "world_walk_route": "world_walk_route"}
        key = aliases.get(section, section)
        return {key: full.get(key)}

    WAIT_REQUIRED_ARGS = {
        "position": ("x", "y"), "near": ("x", "y"),
        "inventory-contains": ("id",), "inventory-lacks": ("id",),
        "message": ("regex",), "xp-gained": ("skill",),
        "npc-present": ("id",), "ground-item": ("id",),
        "appearance-open": (), "dialog-open": (), "input-open": (), "dialog-or-message": (),
        "bank-open": (), "logged-in": (), "npc-dead": (), "npc-gone": (),
        "friend-present": ("name",),
        "player-present": (), "player-absent": (), "player-near": (),
        "duel-open": (), "duel-confirm": (), "duel-started": (), "duel-ended": (),
        "arena-setup": (), "arena-confirm": (), "arena-started": (), "arena-ended": (),
    }

    @staticmethod
    def _matching_players(players, args):
        """Return nearby players matching an optional index and/or exact name."""
        server_index = args.get("server_index", args.get("serverIndex"))
        name = args.get("name")
        folded_name = str(name).casefold() if name is not None else None
        return [player for player in players
                if (server_index is None
                    or player["server_index"] == int(server_index))
                and (folded_name is None
                     or (player.get("name") is not None
                         and player["name"].casefold() == folded_name))]

    def wait(self, a):
        cond = a.get("condition")
        # Usage errors ("usage: ...") must fail fast with exit 2, not surface as raw
        # KeyErrors or spin the full timeout on a typo'd condition.
        if cond not in self.WAIT_REQUIRED_ARGS:
            return {"ok": False, "error": "usage: unknown wait condition: %s" % cond}
        missing = [k for k in self.WAIT_REQUIRED_ARGS[cond] if a.get(k) is None]
        if missing:
            return {"ok": False, "error": "usage: wait %s requires --%s"
                    % (cond, " --".join(missing))}
        if cond in ("npc-dead", "npc-gone") and a.get("id") is None and a.get("server_index") is None:
            return {"ok": False, "error": "usage: wait %s requires --id or --server_index" % cond}
        if (cond in ("player-present", "player-absent", "player-near")
                and a.get("name") is None
                and a.get("server_index", a.get("serverIndex")) is None):
            return {"ok": False,
                    "error": "usage: wait %s requires --name or --server-index" % cond}
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
                elif cond == "appearance-open":
                    if st.appearance_open:
                        return {"ok": True, "matched": True,
                                "appearance": {"open": True}}
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
                elif cond == "friend-present":
                    folded = str(a["name"]).casefold()
                    hit = [dict(friend) for friend in st.friends
                           if friend["name"].casefold() == folded]
                    if hit:
                        return {"ok": True, "matched": True, "friends": hit}
                elif cond == "player-present":
                    hit = self._matching_players(st.players, a)
                    if hit:
                        return {"ok": True, "matched": True,
                                "players": [self._copy_player(player) for player in hit]}
                elif cond == "player-absent":
                    # Absence is meaningful only after one complete AOI frame. A
                    # name selector must also wait for every current type-5 identity;
                    # otherwise an as-yet unnamed target would look falsely absent.
                    identities_ready = (a.get("name") is None
                                        or all(player.get("name") is not None
                                               for player in st.players))
                    if (st._players_initialized and identities_ready
                            and not self._matching_players(st.players, a)):
                        return {"ok": True, "matched": True}
                elif cond == "player-near":
                    if st.x is not None and st.y is not None:
                        radius = int(a.get("radius", 1))
                        nearby = []
                        for player in self._matching_players(st.players, a):
                            distance = abs(st.x - player["x"]) + abs(st.y - player["y"])
                            if distance <= radius:
                                result = self._copy_player(player)
                                result["distance"] = distance
                                nearby.append(result)
                        if nearby:
                            return {"ok": True, "matched": True,
                                    "distance": min(player["distance"] for player in nearby),
                                    "players": nearby}
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
                elif cond == "duel-open":
                    if st.duel.get("phase") == "offer":
                        return {"ok": True, "matched": True, "duel": self.duel_snapshot()}
                elif cond == "duel-confirm":
                    if st.duel.get("phase") == "confirm":
                        return {"ok": True, "matched": True, "duel": self.duel_snapshot()}
                elif cond == "duel-started":
                    if st.duel.get("phase") in ("combat", "complete"):
                        return {"ok": True, "matched": True, "duel": self.duel_snapshot()}
                elif cond == "duel-ended":
                    if st.duel.get("phase") == "complete":
                        return {"ok": True, "matched": True, "duel": self.duel_snapshot()}
                elif cond == "arena-setup":
                    arena = self.arena_snapshot()
                    if arena["phase"] == "setup":
                        return {"ok": True, "matched": True, "arena": arena}
                elif cond == "arena-confirm":
                    arena = self.arena_snapshot()
                    if arena["phase"] == "confirm":
                        return {"ok": True, "matched": True, "arena": arena}
                elif cond == "arena-started":
                    arena = self.arena_snapshot()
                    if arena["phase"] in ("started", "ended"):
                        return {"ok": True, "matched": True, "arena": arena}
                elif cond == "arena-ended":
                    arena = self.arena_snapshot()
                    if arena["phase"] == "ended":
                        return {"ok": True, "matched": True, "arena": arena}
            time.sleep(0.1)
        return {"ok": False, "matched": False, "timeout": timeout, "error": "wait timed out"}

    # ---------------- control server ----------------
    def bind_control(self):
        """Bind the control socket. Called BEFORE login so a port collision aborts
        the process instead of orphaning an uncontrollable logged-in session (the
        old behavior: bind died in a daemon thread while run() logged in anyway)."""
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            srv.bind(("127.0.0.1", self.args.ctrl_port))
        except OSError as e:
            print("voidbotd: FATAL: cannot bind control port %d (%s) — is another "
                  "voidbotd running? Set VOIDBOT_CTRL_PORT to a free port." %
                  (self.args.ctrl_port, e), flush=True)
            return None
        srv.listen(8)
        return srv

    def serve_control(self, srv):
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
        srv = self.bind_control()
        if srv is None:
            return 2
        self.control_socket = srv
        threading.Thread(target=self.serve_control, args=(srv,), daemon=True).start()
        try:
            self.connect_and_login()
        except Exception as e:
            if self.shutdown_requested:
                self._finish_shutdown()
                return self.exit_code
            self.st.event("login_failed", error=str(e))
            print("voidbotd: login failed: %s" % e, flush=True)
            self.exit_code = 1
            self.running = False
            self._close_connections()
            return self.exit_code
        threading.Thread(target=self.recv_loop, daemon=True).start()
        threading.Thread(target=self.heartbeat, daemon=True).start()
        while self.running:
            time.sleep(0.3)
        return self.exit_code


def do_register(args):
    """One-shot account registration; prints one JSON object, returns exit code."""
    if args.no_email and args.email:
        print(json.dumps({"ok": False, "error": "--email and --no-email are mutually exclusive"}))
        return 2
    email = None if args.no_email else (args.email or (args.user + "@voidscape.test"))
    detail = {0: "created", 2: "username already taken",
              4: "packet registration disabled (set want_packet_register: true in the active server config)",
              5: "throttled/recently-registered/server error",
              6: "invalid email or DB failure",
              7: "username must be 2-12 chars",
              8: "disallowed username or bad password length"}
    try:
        c = P.Connection(args.host, args.game_port, timeout=10)
        body = P.build_register(args.user, args.password, email)
        c.send_packet(body[0], body[1:])
        resp = c.recv_byte()
        c.close()
    except (ConnectionError, OSError) as e:
        print(json.dumps({"ok": False, "response": None,
                          "error": "no register response (%s) — server down, or request "
                                   "dropped by the 2 logins/sec throttle; retry after ~1s" % e}))
        return 1
    ok = resp == 0
    print(json.dumps({"ok": ok, "response": resp,
                      "detail": detail.get(resp, "unknown response"), "user": args.user}))
    return 0 if ok else 1


def parse_args(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="127.0.0.1")
    ap.add_argument("--game-port", type=int, default=43596)
    ap.add_argument("--ctrl-port", type=int, default=18900)
    ap.add_argument("--user", required=True)
    credentials = ap.add_mutually_exclusive_group(required=True)
    credentials.add_argument("--pass", dest="password")
    credentials.add_argument("--password-file", dest="password_file")
    ap.add_argument("--defs", default=None, help="server/conf/server/defs dir for item names")
    ap.add_argument("--register", action="store_true",
                    help="one-shot: register the account and exit (no daemon)")
    ap.add_argument("--email", default=None, help="email for --register (default <user>@voidscape.test)")
    ap.add_argument("--no-email", action="store_true", help="omit the optional register email field")
    args = ap.parse_args(argv)
    if args.password_file is not None:
        try:
            args.password = read_password_file(args.password_file)
        except UsageError as e:
            ap.error(str(e))
    return args


def install_signal_handlers(daemon):
    """Translate service/terminal stops into the same graceful shutdown path."""
    def handle_signal(signum, _frame):
        try:
            reason = signal.Signals(signum).name
        except ValueError:
            reason = "signal-%s" % signum
        daemon.request_shutdown(reason)

    signal.signal(signal.SIGTERM, handle_signal)
    signal.signal(signal.SIGINT, handle_signal)


def main(argv=None):
    args = parse_args(argv)
    if args.register:
        return do_register(args)
    daemon = Daemon(args)
    install_signal_handlers(daemon)
    return daemon.run()


if __name__ == "__main__":
    sys.exit(main())
