# Dynamic Wilderness Spawns

Voidscape can adapt specific wilderness training spots to real player pressure without changing static NPC location data.

## Global wilderness density

`wilderness_spawn_multiplier` is a boot-time density pass for attackable NPC locs whose start tile is in the wilderness. `1` leaves authentic loc data unchanged. Decimal values are supported: the whole-number part adds guaranteed extra copies, and the fractional part adds a deterministic proportional subset of eligible locs. The Voidscape local/default launch target is `1.5`, so roughly every two eligible wilderness NPC locs produce one additional live copy.

The copied locs keep the original roam bounds and jitter their starting tile inside those bounds. Non-attackable wilderness service/quest NPCs are skipped.

## Wilderness hobgoblins

Current scope: level-32 hobgoblins in the surface wilderness starter zone around `(217,255)`.

- Zone bounds: `x=202..246`, `y=230..274`
- NPC id: `NpcId.HOBGOBLIN_LVL32` (`67`)
- Unique-player signal: distinct `Player.getCurrentIP()` values for logged-in players standing inside the zone and in wilderness
- Evaluation cadence: every 30 seconds from `World.run()`

Tiers:

| Unique IPs | Behavior |
|---:|---|
| 1-2 | Normal static spawns and normal respawn timing |
| 3 | Static zone hobgoblins respawn at half delay (`84s -> 42s`) |
| 4+ | Temporary extra hobgoblins spawn, one extra per IP above three, capped at five extras |
| 5+ | Static zone hobgoblins respawn at one-third delay (`84s -> 28s`) |

Temporary extras are runtime-only NPCs. They do not persist to `NpcLocs.json`, do not respawn after death, and are removed when the spot cools down. Idle extras despawn first; extras in combat are left alive until combat ends or they die.

When extra hobgoblins are actually spawned, logged-in players standing inside the zone receive a small purple quest-channel server message: `Void energy crackles nearby - more hobgoblins are drawn into the area.`

## Implementation

- Controller: `server/src/com/openrsc/server/content/wilderness/WildernessHobgoblinSpawnController.java`
- Tick hook: `server/src/com/openrsc/server/model/world/World.java`
- Respawn override: `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- Debug command: `::wildhobdebug [status|off|0-20]`

The respawn override is an in-memory NPC attribute read when `Npc.remove()` schedules the next respawn. It does not change NPC definitions, DB schema, packets, opcodes, client cache, or client version.
