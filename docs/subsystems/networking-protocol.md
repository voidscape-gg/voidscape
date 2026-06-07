# Networking and Packet Protocol

Server-side networking layer: framework, pipeline, packet model, opcode dispatch, login flow, encryption, throttling, client/server contract.

## Network framework

**Netty 4.x**, bootstrapped in `Server.java:437–548`.

- Boss group: `NioEventLoopGroup(0, NamedThreadFactory("IOBossThread"))` — accepts inbound connections.
- Worker group: `NioEventLoopGroup(0, NamedThreadFactory("IOWorkerThread"))` — handles I/O.
- Dual-port: TCP (game protocol) + WebSocket (web client). Both `NioServerSocketChannel`.
- Optional SSL/TLS for WebSocket (`SSL_SERVER_CERT_PATH`, `SSL_SERVER_KEY_PATH`).
- Child options: `TCP_NODELAY=true`, `SO_KEEPALIVE=false`, `SO_RCVBUF=10000`, `SO_SNDBUF=10000`.

## Channel pipeline

**TCP** flow:
```
RSCMultiPortDecoder (DecoderMode.TCP)
  ↓ removes itself after first decode
  ↓ adds: RSCProtocolDecoder, RSCProtocolEncoder
RSCConnectionHandler  (login dispatch + queue to player)
```

**WebSocket** flow:
```
RSCMultiPortDecoder (DecoderMode.WS)
  ↓ removes itself
  ↓ adds: OptionalSslHandler, HttpServerCodec, HttpObjectAggregator, HttpRequestHandler,
          WebSocketServerCompressionHandler, WebSocketServerProtocolHandler, WebSocketFrameHandler
  ↓ adds: RSCProtocolDecoder, RSCProtocolWebEncoder
RSCConnectionHandler
```

Files:
- `RSCMultiPortDecoder.java:22–88` — protocol detection + pipeline setup
- `RSCConnectionHandler.java:21–192` — main business handler
- `RSCProtocolDecoder.java:1–364` — frame decoding, version detection, ISAAC decryption
- `RSCProtocolEncoder.java`, `RSCProtocolEncoderMain.java` — outbound framing

## Inbound packet model

`Packet.java`:
- **Opcode**: 1 byte (0–255), or `-1` for raw packets
- **Payload**: Netty `ByteBuf`, variable length
- **Packet number**: monotonic `long` counter (logging)

Payload format depends on detected client version (`RSCProtocolDecoder.decode()`):

- **Client ≥ 183 (modern, ISAAC)**: 1–2 byte length-prefix + ISAAC-encrypted opcode + payload.
  - `length < 160`: 1-byte length + opcode + payload
  - `length ≥ 160`: 2-byte length (`160 + high`, `low`) + opcode + payload
  - **Quirk**: last payload byte is placed *between* length and opcode (lines 75–77).
- **Client 93–182 (no ISAAC)**: same length format, opcode unencrypted.
- **Client 14–92**: 2-byte big-endian length (short) + opcode + payload.
- **Client < 0 (custom)**: 2-byte length + opcode + payload.

Opcode tables (server-only, authoritative):
- `server/src/com/openrsc/server/net/rsc/enums/OpcodeIn.java` — ~130 inbound (LOGIN, LOGOUT, CHAT_MESSAGE, ATTACK, NPC_TALK_TO, ITEM_DROP, BANK_WITHDRAW, PRAYER_ACTIVATED, …)
- `server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java` — ~100 outbound (SEND_LOGOUT, SEND_STATS, SEND_UPDATE_PLAYERS, SEND_INVENTORY, SEND_BOX, …)

Both are enums. **The client-side mirror lives in `Client_Base/src/orsc/net/Opcodes.java`** — a separate enum that must stay in sync (see "Client/server contract" below).

Client version detection (`LoginPacketHandler.java:115–141`):
- Length hints: 30 → v38, 34 → v61, 38 → v74; otherwise `(info << 8 | info2)` or `(info << 16 | short)`.
- ISAAC sync mitigation: loops up to 256 tries against version-specific payload parsers.

## Outbound packet builders

Pattern: `PayloadGenerator + Struct`.

1. **`ActionSender.getGenerator(Player)`** (`ActionSender.java:56–86`) — picks a version-specific generator:
   - `Payload38Generator`, `Payload69Generator`, `Payload115Generator`, `Payload140Generator`, `Payload177Generator`, `Payload196Generator`, `Payload198Generator`, `Payload199Generator`, `Payload201Generator`, `Payload202Generator`, `Payload203Generator`, `Payload235Generator`, `PayloadCustomGenerator` (fallback).
2. **`PacketBuilder.java`** — raw construction. `writeByte()`, `writeShort()`, `writeInt()`, `writeLong()`, `writeString()`, `writeZeroQuotedString()`. Bit-level: `startBitAccess()`, `writeBits()`, `finishBitAccess()`. `toPacket()` produces the `Packet`.
3. **Common outbound** (via `ActionSender` + payload structs):
   - **Login flow**: `sendInitialServerConfigs()`, world info, welcome message
   - **Stats / experience**: `SEND_STATS`, `SEND_EXPERIENCE`, `SEND_EQUIPMENT_STATS`
   - **Movement**: `SEND_PLAYER_COORDS`, `SEND_UPDATE_PLAYERS`
   - **Inventory**: `SEND_INVENTORY`, `SEND_INVENTORY_UPDATEITEM`, `SEND_INVENTORY_REMOVE_ITEM`
   - **Combat**: `SEND_COMBAT_STYLE`, `SEND_DEATH`
   - **Chat / social**: `SEND_PRIVATE_MESSAGE`, `SEND_FRIEND_LIST`, `SEND_BOX` (dialog)
   - **Shop / bank**: `SEND_SHOP_OPEN`, `SEND_BANK_OPEN`, `SEND_BANK_UPDATE`, `SEND_BANK_PRESET`
   - **NPC / objects**: `SEND_UPDATE_NPC`, `SEND_SCENERY_HANDLER`, `SEND_BOUNDARY_HANDLER`
   - **Misc**: `SEND_FATIGUE`, `SEND_PRAYERS_ACTIVE`, `SEND_SLEEP_FATIGUE`

Files:
- `PacketBuilder.java` — low-level
- `ActionSender.java:1–150+` — high-level factory + dispatcher
- `server/src/com/openrsc/server/net/rsc/generators/impl/*Generator.java` — per-version

## Opcode dispatch

**Inbound**:
1. `RSCConnectionHandler.channelRead()` receives decoded `Packet`.
2. Pre-login (opcode 19 for server configs; 0/2/4/8 for login): `LoginPacketHandler.processLogin()`. Maps opcode via `ReverseOpcodeLookup.getOpcode(opcode)` → `OpcodeIn`.
3. Post-login: queued via `player.addToPacketQueue()`. The game loop (`GameStateUpdater`) dequeues and dispatches to handlers in `server/src/com/openrsc/server/net/rsc/handlers/` (e.g. `ChatHandler`, `AttackHandler`, `BankHandler`, `ItemDropHandler`, `NpcTalkTo`).

**Outbound**: `ActionSender.tryFinalizeAndSendPacket(OpcodeOut, Struct, Player)` selects generator → encodes → `player.write(packet)`. Direct enum + generator polymorphism (no switch/map).

Files:
- `ReverseOpcodeLookup.java` — maps numeric opcode to `OpcodeIn` (pre-login only).
- `server/src/com/openrsc/server/net/rsc/handlers/*Handler.java` — packet handlers.

## Login flow

`LoginPacketHandler.processLogin()` (`LoginPacketHandler.java:76–250+`):

1. **Initial connection** (`RSCConnectionHandler.channelActive()`, `:37–45`). Server may send a random session ID (for 2002–2003 client compatibility); controlled by `RSCSessionIdSender` timer.
2. **Client version detection** (`:114–141`) — uses first 2–4 bytes to guess version.
3. **RSA handshake** (client ≥ 205, `:145–159`): client reads server's RSA public exponent + modulus from `Crypto` (logged at startup), encrypts the login block with the public key. Server decrypts via `Crypto.decryptRSA()`. The login block contains: 10-byte checksum, 4×4-byte XTEA keys, 20-byte password, 5×4-byte nonces.
4. **XTEA decryption** (`:175–193`): server decrypts username block with the client-supplied XTEA keys; username extracted (UTF-8, 25+ byte offset).
5. **ISAAC seeding**: both sides seed ciphers with identical nonces (6 from login block + 6 from XTEA block). Inbound cipher decodes opcodes; outbound cipher encodes them.
6. **Credential validation** (`:196–250+`): `LoginRequest` submitted to `LoginExecutor`. DB validates username/password. `loginValidated(response)` callback sends login response (0 = success, 2 = wrong password, …).
7. **World entry**: `ActionSender.sendWelcomeInfo()` plus initial state packets. Player spawned at `RESPAWN_LOCATION_X` / `Y` from config.

Files:
- `LoginPacketHandler.java:27–250+` — full sequence
- `Crypto.java:49–128` — RSA key gen/load, XTEA + RSA decrypt
- `ISAACContainer.java` — opcode shuffling
- `ConnectionAttachment.java` — per-channel state (ISAAC, version, player ref)

## Encryption

**ISAAC** (`ISAACCipher.java`) — Bob Jenkins PRNG.
- Seeded with 8×32-bit nonces from login.
- Inbound: `decodeOpcode(opcode) = (opcode - inCipher.next()) & 0xFF`.
- Outbound: `encodeOpcode(opcode) = (opcode + outCipher.next()) & 0xFF`.
- **Affects opcode only**, not payload.

**RSA** (`Crypto.java:40–128`) — 512-bit (low by modern standards but matches RSC protocol spec).
- Auto-generated if `client.pem`/`server.pem` missing.
- `client.pem` (X.509 public) shared with clients. `server.pem` (PKCS8 private) loaded at startup.
- `Crypto.decryptRSA()` uses `BigInteger.modPow`.
- Encrypts the login block.
- **Regenerate**: delete the .pem pair, restart server. Public exponent + modulus get logged. **Note**: regenerating changes the server's identity — clients with hard-coded keys must be updated.

**XTEA** (`Crypto.java:62–86`) — 32-round, 4×32-bit keys. Decrypts the username block. Keys themselves are inside the RSA-encrypted login block.

## Throttling and disconnect rules

`RSCPacketFilter.java:18–400+`:

1. **Packet flood (per-channel, `:239`)** — `MAX_PACKETS_PER_SECOND` threshold. Exceed → IP ban for `NETWORK_FLOOD_IP_BAN_MINUTES`.
2. **Connection rate (per-IP, `:273`)** — `MAX_CONNECTIONS_PER_SECOND`, `MAX_CONNECTIONS_PER_IP`. Exceed → IP ban.
3. **Login rate (per-IP, `:308`)** — `MAX_LOGINS_PER_SECOND`. Pre-flight in `LoginExecutor`.
4. **Password attempts (`:316`)** — `addPasswordAttempt()` tracks wrong-password submissions. Threshold deferred to game logic for actual lockout.
5. **IP bans (`:154–221`)** — persistent in `ipbans.txt`. Temp bans store expiration; permanent use `-1`. Admin whitelist bypasses filters.
6. **Idle timeouts (`:397`)** — `cleanIdleConnections()` is implemented but disabled (early return at `:398`).

## Client/server protocol contract

**Server-side** opcode definitions: `server/src/com/openrsc/server/net/rsc/enums/{OpcodeIn,OpcodeOut}.java`.

**Client-side** opcode definitions: `Client_Base/src/orsc/net/Opcodes.java` — separate enum with explicit numeric values for outbound (e.g. `PING=67`, `LOGOUT=102`).

**No automated sync**: there is no shared-source-of-truth or codegen between server and client opcodes. They are manually kept in sync.

⚠ **Critical risk** — `OpcodeOut` is transmitted by **enum ordinal** on the server side. Inserting a new value mid-list shifts every subsequent ordinal; old clients then misinterpret packets. **Always append new opcodes at the end** of both `OpcodeOut.java` (server) and `Opcodes.java` (client). Same applies to `OpcodeIn`.

**Protocol version** — `Client_Base/src/orsc/Config.java` `CLIENT_VERSION = 10070`. Server's `client_version` config key (e.g. `10070`) is checked at login. Mismatch → reject when the preset enforces custom client versions. Bump manually when protocol changes.

Voidscape custom-client packet notes:
- `10051`: Auction House market-intel payload was added to the existing custom Auction House packet.
- `10052`: custom `SEND_UPDATE_PLAYERS` appearance type `5` appends the active player-title string after the icon field. Authentic/retro clients are not sent this field.
- `10053`: client-visible definition bump for the Subscription card and Lumbridge Void Subscription Vendor. No packet shape changed.
- `10054`: custom `SEND_SHOP_OPEN` appends a 32-bit per-item display-price override after each shop row. This keeps the Subscription Vendor's doubled tier prices exact in the native shop UI.
- `10055`: custom `SEND_GAME_SETTINGS` appends subscription-active state plus effective combat/skilling XP-rate tenths after the existing profile stats block. Older custom clients do not receive these extra bytes.
- `10057`: custom `SEND_UPDATE_PLAYERS` appearance type `5` appends a one-byte modern hair style after the player-title string, and custom appearance packet `235` sends that same byte after the XP-mode choice. The current client draws styles greater than zero as ARGB PNG overlays from `Client_Base/Cache/voidscape/hair/style_XX/`.
- `10058`: expanded the modern hair cache/style set to recolor styles `01..08` and raised the accepted style cap. No packet shape changed from `10057`.
- `10059`: expanded the classic hair/beard colour palette with Void, Frost, Blood, Ember, Gold, Toxic, Moon, and Coal. No packet shape changed; the server now accepts hair colour indexes `0..17`.
- `10060`: constrained PNG modern hair overlays to the compatible base head (`head1`) so default head/beard styles remain pure classic recolours. Changing the default head or gender in the character designer resets the overlay selector to Classic.
- `10061`: split modern hair shape from hair colour. `hairStyle` now selects a neutral PNG shape (`0 = Classic`, `1 = Swept`) and the existing `hairColour` byte tints both classic head/beard sprites and modern PNG overlays. The duplicate colour-as-style cache folders were removed; legacy saved `hairstyle` values `2..8` are migrated to `hairstyle = 1` plus the matching palette colour.
- `10062`: disabled selectable modern PNG hair styles for the shipped character designer. `hairStyle` remains in the packet/save format for compatibility but is clamped to `0`, so every default head/beard shape uses only the shared classic hair palette.
- `10063`: custom Voidscape hair colours render through the side-swept overlay's three-shade colour mapping on grayscale hair-mask pixels. No packet shape changed; this is a renderer-only visual upgrade for hair colour indexes `10..17`.
- `10064`: character creation/change only exposes and accepts Voidscape hair colour indexes `10..17`; saved old hair colours are migrated to nearest custom-family colours.
- `10065`: character creation/change exposes trial Voidscape top/bottom colour indexes `15..22` and skin tone indexes `43..50`; the renderer applies the richer three-shade mapping to custom clothing and trial skin tones. No packet shape changed.
- `10066`: muted the trial Voidscape clothing RGB values and moved custom clothing to a softer five-step shade ramp. No packet shape changed.
- `10067`: removed the fantasy `Moon`, `Void`, and `Frost` trial skin tones from the selector and server acceptance, leaving grounded skin tone indexes `43..47`. No packet shape changed.
- `10068`: restored classic clothing colours to the Top/Bottom selectors while keeping the muted Voidscape clothing colours appended at indexes `15..22`. No packet shape changed.
- `10069`: custom `SEND_GAME_SETTINGS` appends one byte for the Global Chat country-flag visibility toggle after the HD visual settings block. The custom client also renders server-sent `@flg@CC` chat tokens as country-flag icons, used by simplified global chat formatting.
- `10070`: client-visible Void Council starter intro release. Adds Void Councilor NPC definitions, starter-island custom landscape/cache changes, and client-side starter-island visual polish/safe-zone overlay suppression. No packet shape changed.

Payload format specs are encoded in version-specific parsers (`Payload38Parser`, `Payload69Parser`, …, `Payload235Parser`) and generators. Derived from reverse-engineered RSC, no formal schema.

Compatibility layers:
- Clients 14–38: 2-byte length, simple framing
- Clients 93–182: same length, no opcode encryption
- Clients 183+: 1–2-byte length, ISAAC-encrypted opcode, payload byte reordering quirk
- Custom clients: 2-byte length (RSCL-style), unencrypted

## Pitfalls / non-obvious

1. **Opcode ordinal drift** — see contract section above. Adding an opcode in the middle silently corrupts every subsequent packet for older clients. Always append.
2. **ISAAC desync on flaky network** — connection drops mid-login leave ISAAC state diverged. Decoder's 256-try mitigation usually recovers via opcode-length validation.
3. **Payload byte reorder quirk (≥93)** — last payload byte is inserted between length and opcode. Custom client implementers must replicate.
4. **Session ID timing** — sending random session ID to pre-2004 clients can crash them. Server waits for client data first; `RSCSessionIdSender` only fires if `canSendSessionId` stays true.
5. **Login block checksum** — first byte after RSA decrypt should be 10. If not, decrypt likely failed — but server continues anyway (no hard reject).
6. **WebSocket vs TCP detection heuristic** — `RSCMultiPortDecoder` MIXED mode uses buffer size (>300 bytes ≈ HTTP headers) to differentiate. Edge cases possible.
7. **Inauthentic client routing** — `ReverseOpcodeLookup` only handles a handful of pre-login opcodes (0, 2, 4, 8, 19); post-login dispatch goes through `player.addToPacketQueue()`.
8. **PCAP logging** — `WANT_PCAP_LOGGING` writes every packet to disk; high-volume server can fill disk fast. Useful for debugging, costly in prod.
9. **RSA 512-bit weakness** — inherent to the RSC protocol; can't be made stronger without breaking the client. Don't expect it to resist a determined attacker.
10. **`cleanIdleConnections` is dead code** — early-returns at line 398. If you need idle disconnects, re-enable explicitly.

## Glossary candidates

- **ISAAC** — Indirection, Shift, Accumulate, Add, Count cipher. Stream PRNG used to obfuscate opcodes post-login.
- **XTEA** — Extended Tiny Encryption Algorithm. Symmetric block cipher used for the username block during login.
- **Nonce** — number used once. 6×32-bit values sent by client in login block; seed both ISAAC ciphers.
- **Opcode** — 1-byte packet identifier (0–255 or -1 for raw).
- **ConnectionAttachment** — Netty channel attribute storing per-connection state (player, ISAAC container, session ID, WebSocket flag).
- **Payload** — packet body following opcode; variable length, frame-delimited.
- **Authentic client** — original Jagex RSC mudclient binaries (v38, 69, 93–235); detected by version + packet structure.
- **Inauthentic / custom client** — RSCL or similar; simplified protocol, no ISAAC, may lack opcodes.
- **Multi-port decoder** — `RSCMultiPortDecoder`. Auto-detects TCP vs WebSocket and configures pipeline.
- **Protocol version / build number** — `CLIENT_VERSION` int the client sends so the server can refuse mismatched clients.
