# Server Agent Guide

Server work lives in the OpenRSC server tree plus Voidscape's custom content overlays. Keep this file as a routing map for future agents; put deeper explanations in `docs/`.

## Read First

- Root rules: `../AGENTS.md`
- Build/run: `../docs/DEVELOPMENT.md`
- Architecture: `../docs/ARCHITECTURE.md`
- Code map: `../docs/CODEMAP.md`
- Plugin model: `../docs/subsystems/scripting-plugins.md`
- Network contract: `../docs/subsystems/networking-protocol.md`
- Starter flow: `../docs/subsystems/void-island-starter.md`
- Bug loop: `../docs/BUGS.md` and `../docs/BUGFIX-LOOP.md`
- Bot protocol: `../docs/bot-api.md`

For any substantive gameplay/content change, also read the relevant `../docs/subsystems/` or `../docs/recipes/` page before editing.

## Server Map

| Area | Primary files |
|---|---|
| Boot/config | `src/com/openrsc/server/Server.java`, `ServerConfiguration.java`, `*.conf` |
| Login/session | `src/com/openrsc/server/net/rsc/LoginPacketHandler.java`, `login/`, `service/PlayerService.java` |
| Persistence | `database/`, `src/com/openrsc/server/database/`, `src/com/openrsc/server/service/` |
| World/tick | `src/com/openrsc/server/model/world/`, `event/rsc/`, `GameStateUpdater` |
| Player state | `src/com/openrsc/server/model/entity/player/Player.java`, `model/Skills.java`, cache keys |
| Network packets | `src/com/openrsc/server/net/rsc/`, client opcodes in `../Client_Base/src/orsc/` |
| Plugin runtime | `src/com/openrsc/server/plugins/`, `plugins/com/openrsc/server/plugins/` |
| Custom content | `src/com/openrsc/server/content/`, `plugins/com/openrsc/server/plugins/custom/` |
| Definitions | `conf/server/defs/`, especially item/NPC/scenery/loc JSON/XML |
| Database schema | `database/mysql/`, `database/sqlite/`, query XML files |
| Tools/tests | `../scripts/`, `../tests/`, `../tools/voidbot/` |

## Hard Invariants

- Use root wrapper scripts first. The build gate is `../scripts/build.sh`; run from the repo root unless a doc says otherwise.
- Never edit `../upstream/openrsc-snapshot/`.
- Do not commit runtime artifacts: `avatars/`, `logs/`, `inc/sqlite/*.db`, DB dumps, generated jars, or backups.
- Packet/opcode changes are cross-client changes. Read `../docs/subsystems/networking-protocol.md`, update every affected client, and document the compatibility impact.
- Player cache is a flat namespace. Prefix custom keys (`void_*`, quest-specific names) and make one-time rewards idempotent.
- Plugin `block*` methods cancel/default-intercept actions. Read the trigger contract before adding a handler, especially where multiple plugins can match.
- Game-state mutations should stay on the game tick/plugin path. Avoid background threads mutating players, NPCs, regions, or containers directly.
- Clean up temporary NPCs, instances, attributes, and events on logout, death, timeout, and path cancellation.
- Server and client data definitions must stay aligned for visible custom NPCs/items/scenery.
- Non-trivial Voidscape divergence needs a short entry in `../docs/DIVERGENCE.md`.

## Verification

Preferred commands from repo root:

```bash
scripts/build.sh
scripts/run-server.sh
scripts/reset-db.sh
tests/smoke.sh
```

Use `tools/voidbot/voidbot` for gameplay interaction and assertions. Do not use screen clicks, screenshots, or coordinate guessing for gameplay. If voidbot cannot observe or perform the action, extend voidbot first using `../docs/bot-api.md`.

For client UI state that depends on the server, use the AI workbench route described in the root instructions and `../docs/subsystems/ai-workbench.md`.

## Parallel Agent Lanes

When using multiple agents, split by ownership boundary and give each agent a read-only or edit scope up front:

- Core systems: login, persistence, packets, tick loop, combat math.
- Plugin/content: NPC dialogue, skills, quests, minigames, onboarding, commands.
- Data/definitions: item/NPC/scenery definitions, loc files, drop tables, migrations.
- QA/repro: voidbot scenarios, smoke scripts, BUGS.md triage, tmp evidence.
- Docs/audit: CODEMAP, DIVERGENCE, subsystem docs, release checklist.

Do not let two agents edit the same hot file at once (`Player.java`, `Skills.java`, packet handlers, `VoidPath.java`, `docs/DIVERGENCE.md`, `docs/BUGS.md`). Have one coordinator merge findings and stage hunks selectively.

Good agent prompts include:

- The exact goal and whether edits are allowed.
- The files or directories in scope.
- Required docs from "Read First".
- Verification expected before reporting done.
- A reminder to avoid runtime artifacts and upstream snapshot edits.

## Server-Specific Gotchas

- `plugins.jar` has no hot reload. Plugin changes require rebuild and server restart.
- Trigger ordering is not a safe contract. Make handlers self-guard with IDs, locations, stages, and cache keys.
- Dialogue `delay()` blocks the plugin script. Keep long conversations simple and avoid doing tick-sensitive cleanup after long delays without rechecking state.
- Coordinate checks should use existing helpers in `Point.java` or add named helpers there when a zone becomes a rule boundary.
- Starter/onboarding flows are one-way by design. Treat every kit, teleport, stage bump, safe death, and demo drop as exploit-sensitive.
- SQLite dev DB state is disposable, but shared/staging/prod data is not. Do not touch remote databases without explicit user direction.
