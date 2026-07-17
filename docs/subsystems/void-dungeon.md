# Void Dungeon

## Scope

The Void Dungeon is a shared high-tier Wilderness route entered through the surface rift at `(112,296)`. Each of its three underground stages is 1v1-only for PvP. Ordinary player-versus-NPC combat, NPC aggression, Wilderness levels, and surface PvP behavior remain unchanged. There is no active fourth floor, boss, private phase, or boss reward path.

The three dungeon exit rifts and feature-disabled login recovery use the adjacent clear surface tile `(111,297)`; the surface rift object itself does not move. Authoritative generated coordinate policy lives in `server/src/com/openrsc/server/content/voiddungeon/VoidDungeonLayout.java`. Regenerate it, the location JSON, and the floor mask with `scripts/gen-void-dungeon.py`, then repack the matching server/client landscape archives with `scripts/patch-void-enclave-landscape.py`. Do not hand-edit generated Java or JSON outputs.

## Route

| Stage | Floor | Role | Arrival | Forward ladder | Exit rift |
|---|---:|---|---:|---:|---:|
| Riftworks | 3 | Landing, spider works, wolf foundry, transition room | `(72,3252)` | `(72,3197)` | `(68,3252)` |
| Broken Menagerie | 2 | Central spine with paired unicorn, ogre, and giant rooms | `(72,2308)` | `(72,2253)` | `(76,2308)` |
| Null Sanctum | 1 | Processional nave, knight and wizard chapels, crossing, demon seal | `(72,1364)` | None | `(76,1364)` |

Every generated ladder has an exact reverse transition. Rift and ladder interactions snapshot the player tile and instance, delay for the normal animation, then revalidate login, death, combat, movement, source object, and instance before teleporting. The final demon seal is a destination room, not a disguised entrance to retired content.

The route retains 41 ordinary NPC spawns: 7 spiders, 5 wolves, 5 unicorns, 5 ogres, 5 giants, 6 knights, 6 wizards, and 2 demons. Void Knights and Void Unicorns are aggressive; the other NPC behavior and ordinary supply drops remain unchanged. Only the complete Void-gear unique slots are retuned below.

## Traversal Grace

Killing an NPC underground arms a five-tick, session-only traversal opportunity. Ordinary NPC auto-aggro waits for one decision scan because NPC AI is processed before queued player movement; if the player remains stationary, normal AFK re-aggro resumes on the following scan. An accepted ground or minimap walk during the opportunity activates five ticks of ordinary auto-aggro immunity so the player can move through a dense room. Ground clicks that produce no path and entity-follow clicks do not activate it, and world-map autowalk is intentionally outside the rule.

Manual melee, ranged, throwing, and offensive magic remain usable and clear the opportunity or active grace. New combat, expiry, or leaving the generated dungeon footprint also clears it, while explicit force-chase NPCs bypass it. The state is not persisted and does not reuse the PvP retreat timer.

## Loot Membership

The dungeon remains F2P Wilderness for combat and item use. On the launch members-enabled world, however, all legitimate NPC table rolls can appear and be collected, including members-only herbs, resources, and equipment. Members-only loot can be carried out but cannot be equipped, consumed, identified, or otherwise used until the player leaves F2P Wilderness. A true non-members server preset still suppresses members-only drops entirely. Membership filtering, quantities, and ownership timers are unchanged.

## Void Gear

- Void Wizard: Void Amulet `1/4096`.
- Void Ogre: Void Sceptre `1/4096`.
- Void Giant: Void Scimitar and Void Shortbow `1/8192` each (`1/4096` for either).
- Void Demon: one complete-set roll at `1/1024`, selecting each of the four pieces equally (`1/4096` each).
- Ordinary Void Knights and low-tier spiders do not drop complete Void gear.

The nested unique rolls preserve every outer-table coin, rune, supply, key, and Ring of Wealth branch probability. The disabled DeathMatch Void Knight is also pre-retuned to a `1/1024` equal complete-set roll before it can ever be re-enabled. All four pieces are tradable and receive top death-sort priority under the ordinary skull/keep/Protect Item rules; their base and alchemy prices are unchanged.

## Admission

`VoidDungeonAdmission` owns two integer player-cache values:

- `void_dungeon_admission`: present while the 100,000-coin admission is valid.
- `void_dungeon_depth`: deepest admission-scoped surface shortcut, initially stage 1 and advanced by traversal.

The first entry waits at most 10 game ticks to reserve the player's save lane. If acquired, it removes exactly 100,000 coins, writes both cache values, and waits at most 20 game ticks for the completion-aware atomic save. Failure, timeout, or a player-lifecycle interruption rolls back both cache values, returns the coins, and always releases the reservation. If a started save finishes after rollback, its completion requests a persistent repair save.

Exiting, re-entering, logging out, and restarting do not clear admission. A death whose captured death tile is in the generated underground footprint clears admission and shortcut depth, then requests a persistent save. The next entry costs 100,000 coins again.

## PvP Rule

If either participant is in the exact generated underground footprint, a player can have only one active or recently marked PvP opponent. The same pair may continue using melee, ranged, throwing, or offensive magic; a third party receives `The Void Dungeon only allows one-on-one player fights.` The recent-pair lock expires with the existing `pvp_reattack_timer`. NPC opponents never claim this lock.

The rule is server-side and does not alter combat-level checks, safe-zone policy, or surface Wilderness behavior. See `docs/subsystems/combat-system.md` for the attack-path enforcement points.

## Terrain And Maps

The generated mask contains 4,482 floor tiles and 1,010 perimeter walls across the three stages. `scripts/patch-void-enclave-landscape.py` bakes both into `Custom_Landscape.orsc`, keeping server collision and the client minimap on the same geometry. `BoundaryLocsVoidDungeon.json` remains the generated wall source and is loaded at runtime only when custom landscape is disabled, preventing duplicate boundaries in the normal configuration.

`Client_Base/Cache/worldmap/plane-1.png`, `plane-2.png`, and `plane-3.png` provide the three underground maps. The world-map panel follows the player's current plane when opened inside the generated dungeon bounds; other upstairs locations retain the Surface map. It names the selected stage in its title and uses a fitted `1.5x` underground default so the complete floor remains readable. Floor controls center on the selected dungeon footprint, or on the surface entrance, without changing the player's location. The retired fourth-floor sectors are restored from `Authentic_Landscape.orsc` during every landscape bake.

## Recovery And Archive

A character saved inside the exact former fourth-floor footprint is moved to the Null Sanctum arrival `(72,1364)` in instance `0` on the next enabled login. Admission and discovered depth are preserved. If `WANT_VOID_DUNGEON` is disabled, any generated underground login is instead evacuated to `(111,297)` without clearing admission. These immediate-login recoveries replace the initial location before world packets are sent.

The Void Wyrm runtime is archived under `content/custom/void_wyrm/archive/` with non-compiling `.java.disabled` sources. NPC `869`, animation `245`, active definitions, drops, spawn, obelisk action, and compiled plugin loading are removed. Its source art, importer/tests, appearance reservation, and dormant packed sprite bytes remain available for a future redesign.

Generic instance isolation remains in core entity lookup, combat, delayed projectiles, offensive magic, trade, and item-on-player paths because other minigames use it. Removing the boss does not remove those shared safety guarantees.

## Release Checks

- Run `python3 scripts/gen-void-dungeon.py` and `python3 scripts/patch-void-enclave-landscape.py`; both must be deterministic and report three connected, disjoint stages.
- Confirm server/client `Custom_Landscape.orsc` files are byte-identical and the retired fourth-floor members match `Authentic_Landscape.orsc`.
- Run `scripts/build.sh`.
- Use separate voidbot control ports to verify paid first entry, free re-entry, death reset, both forward/reverse ladders, stage shortcuts, all three exits, exact 1v1 denial, NPC combat, retired-coordinate recovery, and feature-disabled evacuation.
- Use the AI workbench to verify each underground minimap and world-map plane, including automatic current-floor selection and manual floor controls.
- Client version `10131` contains the three-floor landscape/map cohort and the synchronized Void gear identity text. No opcode, packet payload, configuration key, or database schema changed.
