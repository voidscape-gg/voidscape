# Docs-hardening brief (pre-launch, Fable session)

One-shot task brief for a fresh session. Goal: make the `docs/` tree good enough that
**cheaper models (Opus/Sonnet) can execute voidscape tasks from documentation alone**,
reserving Fable for judgment work. Delete this file when the work is done.

## Why

Launch is close and weekly Fable budget is limited — docs are the force multiplier
that converts one expensive mapping pass into permanently cheaper future sessions.
Evidence from the 2026-07-04 onboarding session: work moved fast wherever docs existed
(`docs/bot-api.md`, `docs/subsystems/*`, `docs/CONFIG-MATRIX.md`) and burned tokens
re-deriving core contracts wherever they didn't.

## Ground rules

- Docs describe what **is**, never what should be. No feature work, no refactors.
- A wrong doc is worse than a missing one — agents trust docs blindly. Every claim you
  keep must be re-verified against code (cite file:line while auditing; the shipped doc
  text itself stays clean and skimmable per repo style).
- Keep `CLAUDE.md` files terse maps that point into `docs/` (repo convention). Depth
  goes in `docs/subsystems/` and `docs/recipes/`.
- Use ultracode workflows for the fan-out phases; run **validation** agents on cheaper
  models. Budget target for the whole job: ~10–15% of the weekly Fable allowance.
- Commit in reviewable slices (audit fixes / invariants / recipes / validation patches).

## Phase 1 — Audit what exists (fan-out, one agent per doc cluster)

Sweep every claim in: `docs/ARCHITECTURE.md`, `docs/CODEMAP.md`, `docs/DEVELOPMENT.md`,
`docs/GLOSSARY.md`, `docs/CONFIG-MATRIX.md`, `docs/SERVER-PRESETS.md`,
`docs/OPERATIONS.md`, `docs/subsystems/*.md`, `docs/recipes/*.md`, `docs/bot-api.md`,
root/`server/`/`server/plugins/`/`Client_Base/` `CLAUDE.md`. Flag stale coordinates,
dead flags, renamed files, drifted behavior; fix or delete. Known-drifted examples to
start from: `bake-voidscape-worldmap.py`'s `void_island_tiles()` mask diverged from the
terrain patcher's; `docs/bot-api.md` listed `design-character` before it was
implemented (it now exists — verify the documented flags match `voidbotd.py`).

## Phase 2 — Mine and document the tripwires (the core deliverable)

Create `docs/INVARIANTS.md` (linked from root and server CLAUDE.md): the silent-failure
contracts a model cannot infer locally. Verify each in code before writing. Seed list
discovered the hard way on 2026-07-04 (commit `f7cd9872` and its session):

1. A new plugin trigger interface needs a matching entry in
   `server/src/com/openrsc/server/model/states/Action.java` — without it PluginTask
   construction throws and the trigger **silently never fires** (swallowed at
   `PluginHandler` catch).
2. `multi()`/`npcsay()`/`delay()`/`mes()` only work inside a PluginTask. Packet
   handlers and `GameTickEvent.run()` (tick thread) must never call them; core code
   fires dialogs via `pluginHandler.handlePlugin(TriggerClass, ...)` and `block*` must
   return `true` or `on*` never runs (also true for `PlayerLogoutTrigger` cleanup).
3. Scoped safe death (`Player.SAFE_DEATH_RESPAWN_ATTRIBUTE`) requires instanceId != 0,
   an NPC killer, and non-wilderness — meaning the area must be a registered
   `SAFE_ZONE` in `Point.java` or deaths silently become real.
4. Client `EntityHandler` NPC/item def lists are **positional** (`i++`); server def
   ids and client append order must match exactly, and any client-visible def change
   needs the `CLIENT_VERSION`/`client_version` lockstep bump (all preset confs +
   gitignored `local.conf` + `tools/voidbot/protocol.py`).
5. Landscape tile bytes: overlay = TileDef+1 decides walkability; wall bytes are
   direction-inverted (east-west wall → byte 5 `verticalWall`); walls render+block only
   if DoorDef `unknown==0 && doorType!=0`. Terrain ships to BOTH
   `server/conf/server/data/Custom_Landscape.orsc` and
   `Client_Base/Cache/video/Custom_Landscape.orsc` (patcher writes both), plus
   worldmap PNG/TSVs and an `MD5.SUM` regen at deploy.
6. Never rewrite conf JSON with `json.dump` — formatting churn swamps the diff; edit
   entries surgically. `player.getCache()` is a flat global K/V — namespace keys.
7. No hot reload anywhere: plugins, static defs, and landscape all need restart;
   config isn't hot-read either.
8. F2P NPC filter treats x ≥ 431 as members territory (`Formulae.isP2P`) — custom
   F2P-visible spawns must sit below it.
9. Ground items: owner-scoped `GroundItem`s are invisible to others for 64s;
   construction registers them; `Functions.createGroundItemDelayedRemove` schedules
   despawn. Loot beams are item-ID based (`LootBeamSettings`), not provenance based.
10. Sweep the 2026-07-04 `docs/DIVERGENCE.md` entries and this session's subsystem doc
    (`docs/subsystems/void-island-starter.md`) for more; then mine the OTHER custom
    subsystems (arena, siege, auction, titles, rifts) for their equivalents the same
    way — read the core code they touch, not just their plugins.

## Phase 3 — Recipes in task shape

Check `docs/recipes/` for coverage; add/upgrade, each as numbered steps + verification:

- `carve-custom-terrain.md` — derive from `scripts/patch-void-enclave-landscape.py` +
  the Tutorial Isle work in commit `f7cd9872` as the worked example (sector math, tile
  bytes, safe-zone + client rects + worldmap + loc-JSON registration checklist).
- `add-gated-area.md` — walk-handler gating pattern (`VoidTutorialIsle`,
  `VoidStarterIntro.blocksUnseenIntroPath`), leave-blocks, stage cache keys.
- `qa-fresh-accounts.md` — voidbot `register` + `design-character` + workbench
  `--user/--pass`; onboarding flags are one-way so it's one fresh account per run;
  gotchas: staged `goto` on islands, batch actions swallowing commands, cook prompts
  are menus. (Seed: memory file `project-fresh-account-e2e-testing.md`.)
- `add-scripted-fight.md` — instanced NPC fight with safe death
  (`VoidGuidedFight` as the canonical example; suppress-default-death attribute).
- Verify the existing `add-npc.md` / `add-item.md` / `add-quest.md` against current
  code (they predate several conventions above).

## Phase 4 — Validate with a cheaper model (what makes this real)

Pick 2–3 representative tasks (e.g. "add a fishing spot + rare-drop NPC to the Void
Enclave", "add a new player command with a cache-backed toggle", "carve a tiny scenic
islet"). For each, launch a **Sonnet** agent (Agent tool `model: sonnet`) in a
worktree, instructed to plan the task **using only docs/** (it may read code to
implement, but every convention it needs must have come from docs). Grade the plan
against the invariants; every stumble = a docs patch, not a criticism of the model.
Iterate once. Do NOT keep the implementations — the docs patches are the deliverable.

## Done means

- [ ] Phase-1 audit fixes committed; no doc claim contradicts code.
- [ ] `docs/INVARIANTS.md` exists, verified, linked from CLAUDE.md maps.
- [ ] Recipes above exist and reference real files/commits.
- [ ] `docs/CODEMAP.md` covers the onboarding/isle/content classes added this week.
- [ ] Validation runs logged (what stumbled → what was patched) in the final summary.
- [ ] This brief deleted.
