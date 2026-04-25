# PC_Client/ — desktop client wrapper

Thin Swing wrapper around `Client_Base/`. **Most client work belongs in `Client_Base/`** — only PC-specific UI (window scaling, Discord RPC) lives here.

- Entry: `src/orsc/OpenRSC.java` — `main()`, extends `ORSCApplet`.
- Build: `ant compile` (Java 1.8).
- Output: `Open_RSC_Client.jar` lands in `Client_Base/`.

For shared client guidance, see `Client_Base/CLAUDE.md`. For runtime architecture, see `docs/subsystems/client-cache.md`.
