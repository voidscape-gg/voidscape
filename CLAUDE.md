# voidscape

Voidscape is a private **RuneScape Classic** server, hard-forked from [OpenRSC Core-Framework](https://github.com/Open-RSC/Core-Framework). Goal: **mostly authentic RSC** with quality-of-life additions and small custom touches. License: **AGPLv3** (inherited).

This file is loaded into every Claude Code session. Keep it terse — it's a map, not a manual. Detail lives in `docs/`.

---

## Project topology

OpenRSC's tree structure is preserved at the repo root (`server/`, `PC_Client/`, `Client_Base/`, `Android_Client/`, `PC_Launcher/`, `Deployment_Scripts/`, etc.). Voidscape's additions overlay it:

- `docs/` — architecture, recipes, glossary, divergence log. **Read these before exploring.**
- `scripts/` — canonical entry points (build, run, reset). Use these instead of raw `gradlew` calls.
- `memory/` — persistent project memory (loaded each session via `MEMORY.md`).
- `.agents/` — Codex skills/workflow scaffolding.
- `.claude/` — Claude Code config + custom subagents.
- `upstream/openrsc-snapshot/` — frozen reference copy of OpenRSC at the vendor SHA. **gitignored** (recreate via `scripts/fetch-upstream-snapshot.sh`). **Never edit this directory.**

Subsystem-specific guidance lives in `<subsystem>/CLAUDE.md` (e.g. `server/CLAUDE.md`, `PC_Client/CLAUDE.md`). Those auto-load when editing inside that tree, so we don't bloat this root file.

---

## Where to look first

| Question | Read |
|---|---|
| How do I build / run? | `docs/DEVELOPMENT.md` |
| What's the system architecture? | `docs/ARCHITECTURE.md` |
| Where in the codebase does X live? | `docs/CODEMAP.md` |
| How does subsystem X work? | `docs/subsystems/<x>.md` |
| How do I add an item / NPC / quest? | `docs/recipes/add-<x>.md` |
| What does this RSC term mean? | `docs/GLOSSARY.md` |
| What changed from upstream? | `docs/DIVERGENCE.md` |
| Server presets (preservation, 2001scape, etc.)? | `docs/SERVER-PRESETS.md` |
| What config should dev/staging/prod use? | `docs/CONFIG-MATRIX.md` |
| How do I operate or roll back a server? | `docs/OPERATIONS.md` |
| What must pass before inviting players? | `docs/RELEASE-CHECKLIST.md` |

---

## Hard rules

1. **Never edit `upstream/openrsc-snapshot/`** — it's a frozen reference for diffs.
2. **Don't reinvent build invocations.** Use `scripts/*.sh`. If a recipe is missing, add a script and document it. Raw `gradlew` calls bypass our standardization.
3. **Server and clients share opcodes.** Touching a packet on the server side is a contract change — check `docs/subsystems/networking-protocol.md` for the cross-client sync requirement before pushing.
4. **Don't commit runtime artifacts**: `server/avatars/`, `server/logs/`, DB dumps, `*.jar`, `Backups/*.sql`. Already gitignored — don't override.
5. **Java target is whatever `server/build.gradle` says** — not the system default. The local toolchain may need `jenv`/SDKMAN to switch. See `docs/DEVELOPMENT.md`.
6. **Document divergence as you go.** Any non-trivial voidscape change should get a one-paragraph entry in `docs/DIVERGENCE.md`.
7. **Don't add features beyond the task**, don't refactor on the side, don't introduce abstractions for hypothetical futures. RSC servers attract scope creep — resist it.

---

## Workflow

- **For new features, use `/feature <description>`** — runs discovery → plan → slices with user gates. Don't start coding a multi-subsystem feature without it.
- For a substantive change: read the relevant `docs/recipes/` or `docs/subsystems/` doc first.
- The build is the test. `scripts/build.sh` should pass before claiming work done.
- For UI / client changes: actually run the client and test the feature. Type-checking ≠ working.
- Commit messages: imperative, terse, focus on the *why* not the *what*.

---

## Quick reference

```bash
scripts/build.sh                  # Compile server + plugins
scripts/run-server.sh             # Run server (voidscape preset)
scripts/run-client.sh             # Run PC client against local server
scripts/reset-db.sh               # Wipe + reseed dev DB
scripts/fetch-upstream-snapshot.sh  # Recreate upstream reference
```

For anything not covered by a script, look it up in `docs/DEVELOPMENT.md` before improvising.

---

## Game interaction

Use **voidbot** for every interaction with the game. Never screenshot the client, never
send mouse or keyboard input, never compute click coordinates. Verify outcomes with
`voidbot wait` and `voidbot state`, not by looking at the screen. If an action has no
command, extend voidbot first (`docs/bot-api.md` is the spec), then continue.

`tools/voidbot/voidbot` is a headless client: a daemon holds a logged-in session and
decodes server packets into live state; the CLI issues actions, queries state, and blocks
on conditions. Every command prints JSON and sets an exit code (`0` ok/matched, `1`
not-ok/timeout, `2` usage/connection). Full table + protocol: `docs/bot-api.md`.
Acceptance test: `tests/smoke.sh` (session → walk → npc dialog → kill → loot → bank).

```bash
tools/voidbot/voidbot start --user wbtest --pass voidtest123   # log in, hold session
tools/voidbot/voidbot goto 120 648                              # WORLD_WALK_REQUEST
tools/voidbot/voidbot wait near --x 120 --y 648 --radius 2 --timeout 15
tools/voidbot/voidbot npc-talk --id 848                         # NPC_TALK_TO (nearest 848)
tools/voidbot/voidbot wait input-open --timeout 10
tools/voidbot/voidbot input-reply --text ""                     # answer/dismiss input box
tools/voidbot/voidbot attack-npc --server-index 3704            # NPC_ATTACK
tools/voidbot/voidbot wait npc-dead --server_index 3704 --timeout 40
tools/voidbot/voidbot take-item 127 651 20                      # GROUND_ITEM_TAKE
tools/voidbot/voidbot state inventory                           # JSON inventory
tools/voidbot/voidbot admin "::item 10 1000"                    # server admin command
tools/voidbot/voidbot wait inventory-contains --id 10 --amount 1000 --timeout 8
tools/voidbot/voidbot bank-deposit --id 10 --amount 1000        # BANK_DEPOSIT
tools/voidbot/voidbot events --since 0                          # timestamped event log
tools/voidbot/voidbot stop                                      # clean logout
```

Commands: actions (`goto`, `walk-step`, `npc-talk`, `npc-command`, `attack-npc`,
`attack-player`, `take-item`, `drop`, `item-command`, `equip`/`unequip`,
`bank-withdraw`/`bank-deposit`/`bank-deposit-all`/`bank-close`, `menu-reply`/`menu-cancel`,
`input-reply`, `say`, `admin`, `logout`); queries (`state position|inventory|skills|npcs|
ground-items|bank|dialog|messages|all`); waits (`logged-in`, `position`, `near`,
`inventory-contains`, `inventory-lacks`, `message --regex`, `dialog-open`, `input-open`,
`bank-open`, `npc-present`, `npc-dead`, `ground-item`, `xp-gained`); `events --since`.

---

## Memory

The `memory/` directory is loaded each session. If you discover something that should persist across sessions (a non-obvious decision, a gotcha that bit us, a stable convention), add a memory entry. See the **auto memory** section in your system prompt for the format.
