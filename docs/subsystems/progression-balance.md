# Progression Balance

Voidscape keeps global XP rates simple and familiar, then layers rewards and telemetry around them so tuning can be based on beta behavior.

## Earned Skill Batching

When `batch_progression` is enabled, repeated XP-granting actions use the permanent base level of their governing skill. Temporary boosts and drains never change the limit.

| Base level | Batch limit |
|---:|---:|
| 1-9 | 1 |
| 10-19 | 2 |
| 20-29 | 3 |
| 30-39 | 4 |
| 40-49 | 5 |
| 50-59 | 7 |
| 60-69 | 10 |
| 70-79 | 14 |
| 80-89 | 18 |
| 90-98 | 24 |
| 99+ | 30 |

Gathering, production, Prayer offerings, Runecraft, and Harvesting-related processing all use the same central policy. Resource-limited actions take the lower of the earned limit and the available materials, and quantity menus show that real maximum. Level-ups announce newly unlocked limits.

Inventory chores that award no skill XP remain utility batches, including filling containers, collecting sand/soil/fruit/wool, no-XP food preparation, fat trimming, Apothecary processing, Dragonstone charging, and seasonal present collection. Mining also preserves its original pickaxe-dependent retry count when `batch_progression` is disabled. This policy changes no XP values, success chances, depletion rules, packets, clients, or persistence.

## Starter Path Boosts

`server/src/com/openrsc/server/content/VoidPath.java` owns the one-time Void Island path choice. The selected path grants 2x XP only while each boosted skill is below level 50:

- Warrior: Attack, Defense, Strength
- Forager: Fishing, Cooking, Mining
- Arcanist: Ranged, Magic, Good Magic, Evil Magic

This preserves the useful early onboarding boost without creating permanent account-regret or alien per-skill rates. The selected path still persists in `player_cache` under `void_path`, and the starter kit guard remains `void_path_starter_kit`.

## Milestone Rewards

`server/src/com/openrsc/server/content/ProgressionMilestones.java` pays small coin rewards from the existing `Skills.addExperience(...)` level-up path.

Skill milestones:

- Levels `20, 30, 40, 50, 60, 70, 80, 90, 99`
- Coin rewards scale conservatively from early-game pocket money to 2,500 coins at level 99
- Duplicate prevention uses player-cache keys shaped like `pm_s_<skill>_<level>`

Total-level milestones:

- Totals `250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500`
- Coin reward is `total * 2`
- Duplicate prevention uses player-cache keys shaped like `pm_t_<total>`

Rewards are intentionally modest. They make progression feel acknowledged without replacing combat, skilling, shops, or NPC drops as the real sources of wealth.

## Rested XP

`server/src/com/openrsc/server/content/RestedExperience.java` gives players a capped 1.5x XP window for time spent offline.

- Offline accrual begins after 30 minutes away.
- The pool earns one rested second per offline second.
- The pool caps at `45 minutes`.
- While rested time remains, normal training awards 1.5x XP by adding a 50% bonus after normal server/subscription multipliers.
- Rested time drains by real logged-in session time, so the first 45 minutes of a session are boosted until the timer is empty.
- Quest XP, 1x mode, and OpenPK point conversion do not receive rested XP.
- Duplicate login farming is prevented with the `rested_xp_last_seen` player-cache key. The current pool persists under `rested_xp_pool`.

Player command:

- `::rested` or `::restedxp` shows current rested time, cap, and offline earn rate.

The intent is to help busy beta players return meaningfully without changing the familiar global combat/skilling XP rates or making every skill have a bespoke multiplier.

## Guaranteed Resource Nodes

`server/src/com/openrsc/server/content/GuaranteedResources.java` smooths long dry streaks on normal gathering actions and protects normal rocks/trees from depleting before they have produced a small minimum yield.

- Normal mining rocks and woodcutting trees stay available until they have produced at least 3 successful harvests from that node instance.
- Node yield tracking is process-local and keyed by object id/location/type, so a server restart clears the current counters.
- Node counters clear when the rock/tree actually depletes into its respawn state.
- Four consecutive eligible failures on the same resource makes the next attempt succeed.
- A successful attempt resets that resource's streak for the player.
- Failure-streak tracking is per player session using transient player attributes, not the database.
- Mining, modern woodcutting, and normal fishing use the guarantee.
- Tutorial mining/fishing and big-net fishing are left on their original special-case logic.
- Shared-resource depletion still wins: if a rock/tree/spot vanishes before the player receives the resource, the guarantee is not spent.

This is meant to remove frustrating early depletion and dry streaks while preserving the normal gathering rhythm, tool checks, fatigue checks, XP grants, depletion rolls after the minimum yield, and batch timing.

Admin diagnostic:

- `::gatherstreak <mining|woodcutting|fishing> <resource-key> [failures]` seeds the current player's transient streak for repeatable local/beta tests.
- Mining uses ore item IDs as keys, such as `150` for copper ore.
- Woodcutting uses log item IDs as keys, such as `14` for regular logs.
- Fishing uses the internal key shape `<netId>_<baitId>_<firstFishId>`.

## Balance Telemetry

`server/src/com/openrsc/server/content/BalanceTelemetry.java` keeps in-memory counters for the current telemetry window:

- Effective XP awarded by skill
- Effective XP awarded by player
- NPC kills by NPC id
- NPC item quantity by item id
- Rare-drop event count, using the same rarity test as rare drop beams

Hooks:

- `Skills.addExperience(...)` records actual post-multiplier XP awarded.
- `Npc.killedBy(...)` records NPC kills.
- `Npc.dropItems(...)` and the custom drop helpers record item quantities after stackable boosts such as Void Amulet and Ring of Splendor.

Admin command:

- `::balancereport` or `::balancestats` shows the summary.
- `::balancereport xp` shows top skills.
- `::balancereport players` shows top XP earners.
- `::balancereport npcs` shows top killed NPCs.
- `::balancereport drops` shows top NPC item quantities.
- `::balancereport reset` clears the in-memory window for a fresh playtest.

This does not add a DB schema, packet, opcode, client cache, or client-version change. Durable drop logs remain in the existing database logging path; telemetry is a fast beta-tuning dashboard.
