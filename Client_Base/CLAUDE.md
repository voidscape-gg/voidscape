# Client_Base/ — shared client core

Auto-loads when editing the shared client. PC and Android both compile against this. Detail: `docs/subsystems/client-cache.md`.

## Build / run

- Build: `cd Client_Base && ant compile` → `Open_RSC_Client.jar` lands here.
- Run: `cd Client_Base && ant compile-and-run`, or `scripts/run-client.sh` from repo root (which also seeds `Cache/ip.txt` + `Cache/port.txt`).

## Layout cheat sheet

- `src/orsc/Config.java` — `CLIENT_VERSION`, server IP/port, feature flags.
- `src/orsc/mudclient.java` — main game loop. Big monolith; expect to scroll.
- `src/orsc/net/Network_*.java` — TCP socket layer.
- `src/orsc/net/Opcodes.java` — **client-side opcode enum**. Stays in sync with `server/src/.../net/rsc/enums/Opcode{In,Out}.java`.
- `src/orsc/graphics/{three,two,gui}/` — rendering.
- `src/orsc/multiclient/ClientPort.java` — platform abstraction. PC and Android implement it.
- `src/com/openrsc/{ItemDef,NPCDef,SpellDef,AnimationDef}.java` — entity defs.

## Hard rules

1. **`Opcodes.java` ↔ server `Opcode{In,Out}.java` must stay aligned.** **Append only — never insert mid-list.** Ordinals are wire-format-significant.
2. **`CLIENT_VERSION`** in `Config.java` (`= 10010` at vendor) must match `client_version` in `server/local.conf`. Bump both when protocol changes.
3. **Cache files are CWD-relative.** Tests/runs from different working dirs pick up different `Cache/`. The `scripts/run-client.sh` wrapper writes `Cache/ip.txt` + `Cache/port.txt` from `server/local.conf`.
4. **Client_Base is the canonical client code.** PC_Client and Android_Client are thin wrappers. New shared logic goes here, not in PC_Client.
5. **Java target is 1.8.** Don't introduce APIs newer than Java 8 (or Android won't compile).
