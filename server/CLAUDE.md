# server/ — game server

This file auto-loads when editing server code. Keep it skim-friendly. Detail in `docs/subsystems/*`.

## Build / run from here

- **Compile**: `ant clean compile_core compile_plugins` (or `scripts/build.sh` from repo root).
- **Run**: `ant runserver -DconfFile=local` (or `scripts/run-server.sh`). Always uses `server/local.conf`.
- **Plugins compile separately** — `compile_plugins` produces `plugins.jar`. Plugin changes need this re-run + server restart.
- **No hot-reload** for plugins or static defs (item/NPC JSON). Restart after content edits.

## Layout in 30 seconds

- `src/com/openrsc/server/` — server core. Boot is `Server.java`. World singleton in `model/world/World.java`. Entities in `model/entity/{player,npc}/`.
- `plugins/com/openrsc/server/plugins/` — content code (compiled separately). `authentic/` is vanilla RSC; `custom/` is OpenRSC additions.
- `conf/server/defs/` — static data (items, NPCs, scenery, skills, locations).
- `conf/server/data/maps/` — binary terrain (`.orsc`, `.osar`).
- `database/{mysql,sqlite}/` — schema + migrations.
- `lib/` — bundled JARs.
- `*.conf` — preset files. Voidscape's working config is `local.conf` (gitignored — copy from preservation.conf).

Full map: `docs/CODEMAP.md`. System flow: `docs/ARCHITECTURE.md`.

## Per-task pointers

| Editing… | Read… |
|---|---|
| Combat math | `docs/subsystems/combat-system.md` + `src/.../event/rsc/impl/combat/CombatFormula.java` |
| Tick loop / scheduler | `docs/subsystems/world-tick-loop.md` |
| Networking / opcodes | `docs/subsystems/networking-protocol.md` |
| Persistence / DB / data | `docs/subsystems/persistence-db.md` |
| Plugin / quest / skill action | `docs/subsystems/scripting-plugins.md` |

For "how do I add an item / NPC / quest", use the playbooks in `docs/recipes/`.

## Hard rules in this tree

1. **Cross-client opcodes.** Adding/changing entries in `src/.../net/rsc/enums/Opcode{In,Out}.java` requires a matching change in `Client_Base/src/orsc/net/Opcodes.java`. **Append only — never insert mid-list.** Ordinals are wire-format-significant.
2. **NPC drops are hardcoded.** Loot tables live in `src/.../constants/NpcDrops.java`, not JSON. Editing requires recompile.
3. **Static def changes need restart.** `conf/server/defs/*.json` is loaded at boot.
4. **PID order matters.** If you write code that iterates players, assume PID order may be shuffled per tick (`SHUFFLE_PID_ORDER`). Don't rely on stable order.
5. **Game thread vs LoginExecutor.** DB writes happen on `LoginExecutor`, not the game tick. Don't issue blocking DB calls from `processTick()`.
6. **Don't write content here.** Game content (skills, dialogue, quests, shops) belongs in `server/plugins/`, not `server/src/`. The exception is core mechanics (combat formulas, tick loop, packet protocol).

## Common pitfalls

- **`block*` triggers**: returning `true` cancels the action; `false` lets the chain continue. Easy to invert.
- **`delay()` blocks the script**, not the game tick — but long delays during peak load can backpressure the loop. Keep dialogues tight.
- **`player.getCache()` is global K/V** — namespace your keys (`questname_thing`).
- **`local.conf` not picked up**: confirm `-DconfFile=local`. Restart fully — config isn't hot-reloaded.
- **Java 25 builds** produce module-related warnings; harmless but noisy. Use JDK 11 to silence.
