"""voidbot wire protocol for the voidscape custom RSC client (client_version 10120).

Empirically validated 2026-06-10 against a live capture (see docs/bot-api.md and
the project memory voidbot-protocol-spec). The custom client is the server's
`authenticClient == -1` path: plaintext framing, NO ISAAC, NO opcode rotation,
RSA only at login.

Framing:
  C2S  [u16 BE len][opcode][payload]   len = opcode+payload (EXCLUSIVE of len bytes)
  S2C  [u16 BE len][opcode][payload]   len INCLUSIVE of the 2 len bytes
The single login-response byte is sent raw (unframed) right after config.
"""
import os
import socket
import struct

# ---- Outbound opcode wire values (Client_Base/src/orsc/net/Opcodes.java `Out`) ----
OUT = {
    "LOGIN": 0,
    "REGISTER_ACCOUNT": 2,           # login-phase only; needs want_packet_register: true
    "PING": 67,
    "WALK_TO_POINT": 187,
    "WALK_TO_ENTITY": 16,
    "WORLD_WALK_REQUEST": 35,        # voidscape: server-side pathfind to absolute (x,y)
    "VOID_SCOUT_CANCEL": 37,         # voidscape: return from Void Sparrow scout mode
    "NPC_TALK_TO": 153,
    "NPC_COMMAND1": 202,
    "NPC_COMMAND2": 203,
    "NPC_ATTACK1": 190,
    "NPC_USE_ITEM": 135,
    "ITEM_USE_ITEM": 91,             # use one inventory item on another (combine/craft)
    "GROUND_ITEM_USE_ITEM": 53,      # use a carried item on a ground item (e.g. light logs)
    "PLAYER_ATTACK": 171,
    "PLAYER_FOLLOW": 165,
    "PLAYER_USE_ITEM": 113,
    "QUESTION_DIALOG_ANSWER": 116,   # menu/dialog option reply
    "CHAT_MESSAGE": 216,
    "COMMAND": 38,                   # ::admin commands (no '::' prefix)
    "INTERFACE_OPTIONS": 199,        # multiplexed; sub-option 9 = input-box reply
    "GROUND_ITEM_TAKE": 247,
    "ITEM_COMMAND": 90,              # inventory item op ("eat"/"redeem"/etc.)
    "ITEM_DROP": 246,
    "ITEM_EQUIP_FROM_INVENTORY": 169,
    "ITEM_UNEQUIP_FROM_EQUIPMENT": 168,
    "ITEM_UNEQUIP_FROM_INVENTORY": 170,
    "OBJECT_COMMAND1": 136,
    "OBJECT_COMMAND2": 79,
    "CAST_ON_SCENERY": 99,
    "OBJECT_USE_ITEM": 115,
    "WALL_OBJECT_COMMAND1": 14,
    "WALL_OBJECT_COMMAND2": 127,
    "COMBAT_STYLE_CHANGED": 29,
    "PRAYER_ACTIVATED": 60,
    "PRAYER_DEACTIVATED": 254,
    "BANK_WITHDRAW": 22,
    "BANK_DEPOSIT": 23,
    "BANK_DEPOSIT_ALL_FROM_INVENTORY": 24,
    "BANK_CLOSE": 212,
    "SHOP_BUY": 236,
    "SHOP_SELL": 221,
    "SHOP_CLOSE": 166,
    "LOGOUT": 102,
    "CONFIRM_LOGOUT": 31,
    "ITEM_EXAMINE_REQUEST": 36,
}

# ---- Inbound opcode names (Client_Base/src/orsc/PacketHandler.java map) ----
IN = {
    4: "CLOSE_CONNECTION_NOTIFY", 5: "QUEST_STATUS", 15: "TRADE_ACCEPT",
    20: "CONFIRM_TRADE", 25: "FLOOR_SET", 30: "DUEL_SETTINGS", 33: "UPDATE_XP",
    42: "OPEN_BANK", 48: "SCENERY_HANDLER", 51: "PRIVACY_SETTINGS",
    52: "SYSTEM_UPDATE", 53: "SET_INVENTORY", 59: "APPEARANCE_CHANGE",
    79: "NPC_COORDS", 83: "DEATH", 84: "WAKE_UP", 87: "SEND_PM",
    89: "DIALOGUE_MSG_NOTTOP", 90: "SET_INVENTORY_SLOT", 91: "BOUNDARY_HANDLER",
    92: "INITIATE_TRADE", 97: "TRADE_ITEMS", 99: "GROUNDITEM_HANDLER",
    101: "SHOP_OPEN", 104: "UPDATE_NPC", 109: "SET_IGNORE", 110: "INPUT_BOX",
    111: "TUTORIAL_DONE",
    103: "VOID_SCOUT_STATE",
    114: "FATIGUE", 117: "FALL_ASLEEP", 120: "RECEIVE_PM",
    123: "REMOVE_INVENTORY_SLOT", 128: "CONCLUDE_TRADE", 131: "SEND_MESSAGE",
    137: "EXIT_SHOP", 149: "UPDATE_FRIEND", 153: "EQUIP_STATS", 156: "SET_STATS",
    159: "UPDATE_STAT", 165: "CLOSE_CONNECTION", 172: "CONFIRM_DUEL",
    176: "DIALOGUE_DUEL", 182: "WELCOME", 183: "DENY_LOGOUT",
    191: "PLAYER_COORDS", 194: "INCORRECT_SLEEPWORD", 203: "CLOSE_BANK",
    204: "PLAY_SOUND", 206: "SET_PRAYERS", 211: "UPDATE_ENTITIES",
    213: "NOOP_APPEARANCE", 222: "DIALOGUE_MSG_TOP", 225: "CANCEL_DUEL",
    234: "UPDATE_PLAYERS", 240: "GAME_SETTINGS", 244: "FATIGUE_SLEEPING",
    245: "DIALOGUE_MENU", 249: "BANK_UPDATE", 252: "DISABLE_OPTION_MENU",
}

# Constant UID(8)+limitations trailer last captured for client_version 10088 (re-capture
# if the client cache/version changes — see capture-proxy.py).
LOGIN_TRAILER = bytes.fromhex(
    "000000000000000000f3000006430000035c0000051a000d0030110006004100"
    "1c000000df0100060000002f000000110000001600630000002504147fffffff"
    "66656533396431306639636235643630323762616331623332666133333566330a00"
)
# Login client version. Matches the real client (Client_Base/src/orsc/Config.java
# CLIENT_VERSION); env-overridable so QA can probe server version gates (e.g. paths
# gated to specific client versions).
CLIENT_VERSION = int(os.environ.get("VOIDBOT_CLIENT_VERSION", "10120"))


class BitWriter:
    def __init__(self):
        self.b = bytearray()

    def u8(self, v):  self.b.append(v & 0xFF); return self
    def u16(self, v): self.b += struct.pack(">H", v & 0xFFFF); return self
    def i16(self, v): self.b += struct.pack(">h", v); return self
    def u32(self, v): self.b += struct.pack(">I", v & 0xFFFFFFFF); return self
    def i8(self, v):  self.b += struct.pack(">b", v); return self
    def raw(self, data): self.b += data; return self

    def rsstr(self, s):
        """RSBuffer.putString: raw bytes + 0x0A terminator."""
        self.b += s.encode("latin1"); self.b.append(0x0A); return self


_CHAT_INIT = (
    22, 22, 22, 22, 22, 22, 21, 22, 22, 20, 22, 22, 22, 21, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    3, 8, 22, 16, 22, 16, 17, 7, 13, 13, 13, 16, 7, 10, 6, 16,
    10, 11, 12, 12, 12, 12, 13, 13, 14, 14, 11, 14, 19, 15, 17, 8,
    11, 9, 10, 10, 10, 10, 11, 10, 9, 7, 12, 11, 10, 10, 9, 10,
    10, 12, 10, 9, 8, 12, 12, 9, 14, 8, 12, 17, 16, 17, 22, 13,
    21, 4, 7, 6, 5, 3, 6, 6, 5, 4, 10, 7, 5, 6, 4, 4,
    6, 10, 5, 4, 4, 5, 7, 6, 10, 6, 10, 22, 19, 22, 14, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22, 22,
    22, 22, 22, 22, 22, 22, 22, 21, 22, 21, 22, 22, 22, 21, 22, 22,
)
assert len(_CHAT_INIT) == 256

_SPECIAL_CHARS = (
    "\u20ac", "\u0000", "\u201a", "\u0192", "\u201e", "\u2026", "\u2020", "\u2021",
    "\u02c6", "\u2030", "\u0160", "\u2039", "\u0152", "\u0000", "\u017d", "\u0000",
    "\u0000", "\u2018", "\u2019", "\u201c", "\u201d", "\u2022", "\u2013", "\u2014",
    "\u02dc", "\u2122", "\u0161", "\u203a", "\u0153", "\u0000", "\u017e", "\u0178",
)
_SPECIAL_TO_BYTE = {ch: 128 + i for i, ch in enumerate(_SPECIAL_CHARS) if ch != "\u0000"}


def _build_chat_blocks():
    blocks = [0] * len(_CHAT_INIT)
    builder = [0] * 33
    next_index = 0
    for ch, bit_count in enumerate(_CHAT_INIT):
        if bit_count == 0:
            continue
        bit_selector = 1 << (32 - bit_count)
        builder_value = builder[bit_count] & 0xFFFFFFFF
        blocks[ch] = builder_value
        if builder_value & bit_selector:
            new_value = builder[bit_count - 1] & 0xFFFFFFFF
        else:
            for counter in range(bit_count - 1, 0, -1):
                value2 = builder[counter] & 0xFFFFFFFF
                if builder_value != value2:
                    break
                selector2 = 1 << (32 - counter)
                if value2 & selector2:
                    builder[counter] = builder[counter - 1] & 0xFFFFFFFF
                    break
                builder[counter] = (value2 | selector2) & 0xFFFFFFFF
            new_value = (builder_value | bit_selector) & 0xFFFFFFFF
        builder[bit_count] = new_value
        for counter in range(bit_count + 1, 33):
            if (builder[counter] & 0xFFFFFFFF) == builder_value:
                builder[counter] = new_value

        dictionary_index = 0
        for counter in range(bit_count):
            selector = (0x80000000 >> counter) & 0xFFFFFFFF
            if (builder_value & selector) == 0:
                dictionary_index += 1
            else:
                # We only need blocks for encoding, but mirror the Java table builder so
                # future decode checks can reuse the same construction.
                pass
        if dictionary_index >= next_index:
            next_index = dictionary_index + 1
    return blocks


_CHAT_BLOCKS = _build_chat_blocks()


def _smart08_16(value):
    if 0 <= value < 128:
        return bytes([value])
    if 0 <= value < 32768:
        return struct.pack(">H", value + 32768)
    raise ValueError("smart08_16 out of range: %d" % value)


def _rsc_string_bytes(text):
    out = bytearray()
    for ch in text:
        code = ord(ch)
        if 0 < code < 128 or 160 <= code <= 255:
            out.append(code)
        else:
            out.append(_SPECIAL_TO_BYTE.get(ch, ord("?")))
    return bytes(out)


def encrypted_chat_payload(text):
    """RSBufferUtils.putEncryptedString payload for CHAT_MESSAGE."""
    data = _rsc_string_bytes(text)
    bit_count = sum(_CHAT_INIT[b] for b in data)
    encoded = bytearray((bit_count + 7) // 8)
    bit_pos = 0
    for value in data:
        block = _CHAT_BLOCKS[value]
        width = _CHAT_INIT[value]
        if width == 0:
            raise ValueError("unencodable chat byte: %d" % value)
        for i in range(width):
            if block & (0x80000000 >> i):
                encoded[bit_pos >> 3] |= 1 << (7 - (bit_pos & 7))
            bit_pos += 1
    return _smart08_16(len(data)) + bytes(encoded)


class BitReader:
    """MSB-first bit reader over a byte buffer (RSBuffer_Bits.getBitMask)."""
    def __init__(self, data):
        self.data = data
        self.bitpos = 0

    def bits(self, n):
        v = 0
        for _ in range(n):
            byte = self.data[self.bitpos >> 3]
            bit = (byte >> (7 - (self.bitpos & 7))) & 1
            v = (v << 1) | bit
            self.bitpos += 1
        return v


def rsa_block(plaintext: bytes, exponent: int, modulus: int) -> bytes:
    """Reproduce RSBuffer.encodeWithRSA: BigInteger(signed BE).modPow then toByteArray."""
    m = int.from_bytes(plaintext, "big")  # plaintext high bit is never set for our inputs
    c = pow(m, exponent, modulus)
    blen = (c.bit_length() + 8) // 8       # +8 reserves a sign byte like Java toByteArray
    out = c.to_bytes(blen, "big").lstrip(b"\x00") or b"\x00"
    if out[0] & 0x80:
        out = b"\x00" + out
    return out


def pad_password(pw: str) -> str:
    """DataOperations.addCharacters(pw, 20): 20 chars, alnum kept, others -> '_', pad spaces."""
    out = []
    for j in range(20):
        if j >= len(pw):
            out.append(" ")
        else:
            c = pw[j]
            out.append(c if c.isalnum() and c.isascii() else "_")
    return "".join(out)


class Connection:
    """TCP connection with voidscape framing. Send EXCLUSIVE-length, recv INCLUSIVE-length."""
    def __init__(self, host, port, timeout=20):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.buf = bytearray()

    def send_packet(self, opcode: int, payload: bytes = b""):
        body = bytes([opcode]) + payload
        self.sock.sendall(struct.pack(">H", len(body)) + body)

    def send_raw(self, data: bytes):
        self.sock.sendall(data)

    def _fill(self, n):
        while len(self.buf) < n:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise ConnectionError("server closed connection")
            self.buf += chunk

    def recv_byte(self) -> int:
        self._fill(1)
        b = self.buf[0]; del self.buf[0:1]; return b

    def recv_packet(self):
        """Return (opcode, payload) for one S2C frame. Blocks until a full frame."""
        self._fill(2)
        total = (self.buf[0] << 8) | self.buf[1]   # INCLUSIVE of the 2 len bytes
        self._fill(total)
        frame = bytes(self.buf[2:total]); del self.buf[0:total]
        if not frame:
            return (None, b"")
        return (frame[0], frame[1:])

    def settimeout(self, t):
        self.sock.settimeout(t)

    def close(self):
        try: self.sock.close()
        except OSError: pass


def build_register(username: str, password: str, email: str) -> bytes:
    """REGISTER_ACCOUNT body for the custom-client path (server classifies our framing
    as authenticClient == -1 -> the plain-strings branch in LoginPacketHandler ~L750):
    three 0x0A-terminated strings. Password travels plaintext — loopback dev use only.
    Server replies with ONE raw byte: 0=created, 2=name taken, 4=packet registration
    disabled (want_packet_register: false), 5=throttled/recently-registered/error,
    6=bad email or DB failure, 7=username not 2-12 chars, 8=disallowed name or bad
    password length. No reply at all = dropped by the 2/s login throttle."""
    w = BitWriter()
    w.u8(OUT["REGISTER_ACCOUNT"])
    w.rsstr(username)
    w.rsstr(password)
    w.rsstr(email)
    return bytes(w.b)


def build_login(username: str, password: str, exponent: int, modulus: int,
                reconnect=False) -> bytes:
    """Full LOGIN packet body (opcode + fields); caller frames it."""
    w = BitWriter()
    w.u8(OUT["LOGIN"])
    w.u8(1 if reconnect else 0)
    w.u32(CLIENT_VERSION)
    w.rsstr(username)
    w.u8(1)  # encryption version: RSA
    pw_plain = (pad_password(password) + "\n").encode("latin1")
    pw_cipher = rsa_block(pw_plain, exponent, modulus)
    w.u16(len(pw_cipher)); w.raw(pw_cipher)
    det_plain = ("voidbot/voidbot.jar" + "\n").encode("latin1")
    det_cipher = rsa_block(det_plain, exponent, modulus)
    w.u16(len(det_cipher)); w.raw(det_cipher)
    w.raw(LOGIN_TRAILER)
    return bytes(w.b)
