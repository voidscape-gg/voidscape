# Clients and Cache

Structural map of the client tree (PC, Android, Launcher), shared `Client_Base/`, asset cache, and the **critical client/server opcode contract**.

## PC_Client

Path: `PC_Client/`.

- **Entry**: `PC_Client/src/orsc/OpenRSC.java` — static `main()`.
- **Build**: Apache **Ant** (`build.xml`). Java target 1.8 (`source/target=1.8`).
- **Architecture**: extends `ORSCApplet`; launched as `JFrame` with Swing. DPI/scaling overrides applied pre-init.
- **Top-level packages**: just `orsc/` — PC-specific UI like `ScaledWindow.java`, `Discord.java`.
- **Libraries**: only `discord-rpc.jar` in `PC_Client/lib/`.
- **Build output**: `Open_RSC_Client.jar` (lands in `Client_Base/`, manifest main-class `orsc.OpenRSC`).

## Client_Base — shared core

Path: `Client_Base/`. ~116 Java files.

Single source of truth for all client platforms. Both PC_Client and Android_Client compile against this.

Package structure:
- `src/orsc/mudclient.java` — main game loop (80+ fields). Rendering, networking, entity updates, UI panels.
- `src/orsc/Config.java` — static constants:
  - `CLIENT_VERSION = 10010`
  - `SERVER_IP`, `SERVER_PORT`
  - Feature flags (`C_EXPERIENCE_DROPS`, `C_CUSTOM_UI`, …)
  - Server-defined config (`S_PLAYER_LEVEL_LIMIT`, `S_WANT_SKILL_MENUS`, …)
- `src/orsc/net/` — network layer:
  - `Network_Base.java`, `Network_Socket.java`
  - **`Opcodes.java` — client-side opcode enum**. `Out` (~40 client→server) with explicit int values; `In` is empty placeholder (server→client opcodes handled server-side only).
- `src/orsc/graphics/`:
  - `three/` — 3D rendering
  - `two/SpriteArchive/` — 2D sprite handling
  - `gui/Panel.java`, `Menu.java`, … — UI
- `src/orsc/enumerations/` — game data enums.
- `src/orsc/buffers/` — `RSBuffer*` packet serialization.
- `src/orsc/multiclient/ClientPort.java` — platform abstraction interface (rendering, input, cache access, sound). Implemented per-platform.
- `src/com/openrsc/` — entity defs: `ItemDef.java`, `NPCDef.java`, `SpellDef.java`, `AnimationDef.java`, …
- `src/res/` — icon resources.

Java target: 1.8. Android source uses the same target but requires the Android Gradle plugin to run on JDK 17; Android minSDK is 23.

## Android_Client

Path: `Android_Client/`.

- Build: Gradle 7.3.0 (Android Gradle Plugin). Project file: `Open RSC Android Client/build.gradle`.
- SDK: `minSdkVersion 23`, `targetSdkVersion 31`, `compileSdkVersion 31`.
- Entry: `src/main/java/com/openrsc/client/android/GameActivity.java` — Android `Activity`, implements `ClientPort`.
- Android-specific packages:
  - `com.openrsc.android.render.RSCBitmapSurfaceView` — Canvas/bitmap rendering.
  - `com.openrsc.android.render.InputImpl` — touch input mapping.
  - `com.openrsc.android.updater.CacheUpdater` — seeds bundled cache to Android filesystem and optionally applies remote cache updates.
- Source linkage: includes `Client_Base/src/` via Gradle `sourceSets`.
- Build output: `voidscape.apk`.
- Build helper: `scripts/build-android.sh` selects JDK 17 and runs Gradle; a local Android SDK is still required through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `Android_Client/local.properties`, or Homebrew `android-commandlinetools` at `/opt/homebrew/share/android-commandlinetools`.
- Cache seed: Gradle packages a generated asset copy of `Client_Base/Cache`, excluding mutable local files (`config.txt`, `credentials.txt`, `hideIp.txt`, `ip.txt`, `port.txt`, `uid.dat`). `CacheUpdater` copies that bundled cache into app-private storage before trying any optional remote cache URL.
- Server choices: emulator default `10.0.2.2:43596`, LAN placeholder `192.168.1.100:43596`, or manual host/port.
- Note: shares all `mudclient` logic with PC; differs only in input, rendering surface, server selection, and cache bootstrap strategy.

## PC_Launcher

Path: `PC_Launcher/`.

- Purpose: splash, launcher UI, client auto-updater, multi-world support.
- Main: `src/main/java/launcher/Main.java`.
- Build: Ant. Java 1.8. Output: `OpenRSC.jar`.
- Features:
  - Configurable cache directory via CLI args (`--dir`, `-d`); defaults to `./Cache`.
  - UI: `launcher/Fancy/MainWindow.java` — themed launcher with server cards, settings, update checks.
  - Auto-update: `launcher/Gameupdater/ClientUpdater.java`, `ClientDownloader.java` (MD5 verification).
  - Multi-world: `launcher/Utils/WorldPopulations.java` tracks server populations.
  - Version stamping: `launcher/Utils/Defaults.java` — `_CURRENT_VERSION` set via build-time Ant target.

## Cache: asset loading strategy

Runtime location: `Config.F_CACHE_DIR` — typically `./Cache/` relative to the launcher/client jar, or custom via CLI.

Expected contents:
- `ip.txt` — server IP (single line, e.g. `127.0.0.1`).
- `port.txt` — server port (single line, e.g. `43594`).
- `credentials.txt` — optional saved login.
- `hideIp.txt` — privacy setting.
- `video/spritepacks/` — custom sprite packs (symlinked from `PC_Launcher/SPRITEPACK_DIR`).
- Game asset `.dat` files — **not bundled in source**; downloaded at runtime from server, or distributed via launcher updates.

Loading flow:
1. Launcher initializes, reads `Cache/`.
2. Client (`mudclient.java`) connects to `(ip.txt, port.txt)`.
3. Server pushes game configs (sprites, models, animations) via `SEND_SERVER_CONFIGS`.
4. Client unpacks in-memory; nothing persisted in the JAR.

**Implication**: assets live server-side. Hot-patching content (new items, sprites) doesn't require client rebuild — just a server config push.

## Server connection config (dev vs prod)

Resolution priority in `mudclient.java`:
1. **Hardcoded** — `Config.SERVER_IP` and `Config.SERVER_PORT` if set in `Config.java`.
2. **Cache files** — `ClientPort.loadIP()` reads `Cache/ip.txt`; `ClientPort.loadPort()` reads `Cache/port.txt`.
3. **Server-sent** — after login, server can update via config packets.

For dev (local server):
```bash
mkdir -p Cache
echo "127.0.0.1" > Cache/ip.txt
echo "43594"     > Cache/port.txt
```
Then run `PC_Client` or the launcher; it'll connect to localhost.

For prod: the launcher distributes a pre-configured `Cache/`, or the user enters details via launcher UI.

## Cross-client constants — CRITICAL contract

**Server opcode definitions**: `server/src/com/openrsc/server/net/rsc/enums/{OpcodeIn,OpcodeOut}.java`.

**Client opcode definitions**: `Client_Base/src/orsc/net/Opcodes.java`. `Out` enum has explicit numeric values (e.g. `PING=67`, `LOGOUT=102`); `In` is empty (server→client opcodes are server-only).

Sync mechanism: **manual, ordinal-dependent**.

⚠ **Risk**: server's `OpcodeOut` is transmitted by **enum ordinal**. Inserting a new value mid-list shifts every subsequent ordinal — old clients then misinterpret all packets after the inserted point.

**Rule**: only **append** opcodes at the end of both files. Never reorder. Never insert in the middle.

There is no codegen and no shared schema. Devs must keep both files aligned manually.

`CLIENT_VERSION = 10010` in `Config.java` is checked by the server at login. Bump manually when protocol changes; mismatch → rejection.

## Build outputs

| Project | Output | Location | Purpose |
|---|---|---|---|
| PC_Launcher | `OpenRSC.jar` | `PC_Launcher/` | Launcher UI + auto-updater entry point |
| Client_Base + PC_Client | `Open_RSC_Client.jar` | `Client_Base/` | Standalone client (Main-Class: `orsc.OpenRSC`) |
| Android_Client | `voidscape.apk` | Gradle build dir | Android app |

## Run from source — connecting to local server

**Option 1 — via launcher**
```bash
cd PC_Launcher
ant compile
java -jar OpenRSC.jar
# Reads Cache/ip.txt + Cache/port.txt
```

**Option 2 — direct client (Cache configured)**
```bash
mkdir -p Cache
echo "127.0.0.1" > Cache/ip.txt
echo "43594"     > Cache/port.txt
cd Client_Base
ant compile-and-run
# Or: java -jar Open_RSC_Client.jar
```

**Option 3 — hardcoded override**
```java
// Client_Base/src/orsc/Config.java
public static String SERVER_IP = "127.0.0.1";
public static int    SERVER_PORT = 43594;
```
Recompile Client_Base.

## Pitfalls / non-obvious

1. **Opcode ordinal desync.** Server OpcodeOut transmitted by ordinal; client must use identical ordering. Always append, never insert mid-list. Same applies to OpcodeIn.
2. **Silent version mismatch.** `CLIENT_VERSION = 10010` is not auto-bumped. If you change protocol but forget to bump, server may accept the connection but corrupt state. Check server logs for version checks.
3. **Cache directory is CWD-relative.** `./Cache` relative to the working directory at launch — not the JAR location. Running from different dirs picks up different caches. Use absolute path or `--dir`.
4. **Android inherits Client_Base wholesale.** No separate Android opcode tracking. Bumping Client_Base requires APK rebuild.
5. **Android packages cache from the repo at build time.** Keep `Client_Base/Cache` populated before building the APK; local runtime files are excluded from packaging by Gradle.
6. **`ip.txt`, `port.txt`, `credentials.txt` are plaintext.** No encryption. Sensitive in shared environments.
7. **Build system mismatch across the tree.** PC_Launcher, PC_Client, Client_Base use Ant; Android uses Gradle. Cross-tree automation must handle both.
8. **Discord RPC is hard-coupled on PC.** `discord-rpc.jar` is always loaded there. Android stubs Discord.

## Glossary candidates

- **ClientPort** — platform abstraction interface; PC and Android each implement it for rendering, input, cache access, sound.
- **mudclient** — central game loop class; coordinates graphics, network, entities, UI panels.
- **Opcodes.Out** — client → server packet type enum (e.g. `WALK_TO_ENTITY`).
- **OpcodeOut / OpcodeIn** — server-side enum counterparts; transmitted by ordinal.
- **CACHE_VERSION** — internal cache format version (separate from `CLIENT_VERSION`); mismatch triggers cache wipe.
- **Config.F_CACHE_DIR** — runtime path to Cache directory.
- **ClientPort.loadIP / loadPort** — static methods reading `ip.txt` / `port.txt`.
- **SpriteArchive / Unpacker** — decompresses and deserializes sprite `.dat` files in memory.
- **Network_Socket** — TCP socket wrapper for server communication.
- **PacketHandler** — serializes/deserializes packets to/from network stream.
