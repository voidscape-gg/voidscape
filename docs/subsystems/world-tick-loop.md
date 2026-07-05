# World, Tick Loop, and Entity Model

Server core: boot sequence, tick loop, world singleton, entity hierarchy, scheduler, region system. Excludes networking (see `networking-protocol.md`), combat (see `combat-system.md`), persistence (see `persistence-db.md`), and plugins (see `scripting-plugins.md`).

## Entry point and boot sequence

**Entry point**: `Server.main()` in `server/src/com/openrsc/server/Server.java:198`.

Boot order:

1. **Configuration load** (`Server.java:253–255`, constructor): `ServerConfiguration.initConfig(confName)` loads the `*.conf` for the chosen preset.
2. **Singletons** (`Server.java:260–292`, constructor): `RSCPacketFilter`, `PluginHandler`, `CombatScriptLoader`, `Constants`, `GameDatabase` (MySQL or SQLite), `World`, `GameEventHandler`, `GameStateUpdater`, `LoginExecutor`. Thread pools created for SQL, logging, online monitoring.
3. **Game thread schedule** (`Server.java:344–350`, start of `start()`): single-threaded `ScheduledExecutorService` runs `Server.run()` every **10ms** (the tick gate, not the tick itself — see below). This fires before the database is open, but ticks are effectively no-ops until the world/players exist.
4. **Database open + patching** (`Server.java:358–375`): opens the connection, runs `JDBCPatchApplier` migrations.
5. **Asset loading** (`Server.java:379–413`):
   - Prerendered captchas
   - Entity definitions (NPCs, items, scenery) via `EntityHandler.load()`
   - `GameStateUpdater.load()`, `GameEventHandler.load()`, `CombatScriptLoader.load()`, `World.load()`, `PluginHandler.load()`
6. **Service startup** (`Server.java:419–439`): `LoginExecutor.start()`, `DiscordService.start()`, `GameLogger.start()`, `PcapLogger.start()`, `RSCPacketFilter.load()`.
7. **Network bind** (`Server.java:441–554`): `Crypto.init()`, then Netty `ServerBootstrap` on configured TCP and (optional) WebSocket ports.

## Tick loop

The 10ms scheduler is a **gate**; the actual game tick fires when accumulated elapsed nanoseconds reach `GAME_TICK * 1_000_000` ns. Default `GAME_TICK = 640ms` (`ServerConfiguration.GAME_TICK`).

`Server.run()` (`Server.java:660`) executes per-tick:

1. Process non-player events (`GameEventHandler`)
2. Update world state (`GameStateUpdater.updateWorld`)
3. Process NPCs (`GameStateUpdater.processNpcs`)
4. Per-player, in PID order:
   - `Player.processTick()` — inbound packets, walk-to actions, player events, movement, message queue
   - `Player.processLogout()` — handles deferred logout requests
   - `Player.sendUpdates()` — flushes outbound state packets to client
5. `GameStateUpdater.executePidlessCatching` — late-arriving processing
6. Global message queue
7. Internet reachability check
8. Cleanup + housekeeping
9. Advance tick counter (`Server.java:735`)

Each phase is benchmarked via `getServer().bench()`; lateness logged.

## World singleton

`server/src/com/openrsc/server/model/world/World.java`.

Owns:
- `PlayerList players` (capacity ~2000)
- `EntityList<Npc> npcs` (capacity ~4000)
- `RegionManager`
- Ground items, objects, shops, quests, minigames, clans, parties
- Combat odyssey data, fishing trawler instances
- Optional avatar generator

Key methods:
- `registerPlayer(Player)` / `unregisterPlayer(Player)` — adds/removes, updates region, logs online flag
- `registerNpc(Npc)` / `unregisterNpc(Npc)` — spawn/despawn
- `getPlayers()`, `getNpcs()` — concurrent-safe live lists

## Entity hierarchy

```
Entity (abstract)
└── Mob (abstract) — combat/movement state
    ├── Player
    └── Npc
```

**Entity** — `server/src/com/openrsc/server/model/entity/Entity.java`
- `AtomicReference<Point> location`
- `AtomicReference<Region> region` (cached for visibility)
- `ConcurrentHashMap<String, Object> attributes` — generic key-value scratch
- Methods: `withinRange()`, `updateRegion()`, `getAttribute()`, `setAttribute()`

**Mob** — `server/src/com/openrsc/server/model/entity/Mob.java`
- Skills, walking queue, combat event, poison, stat restoration
- `combatWith`, `following`, `possessing` references
- `updatePosition()`, `hasMoved`, `lastMovementTime`
- Abstract — no direct instantiation

**Player** — `server/src/com/openrsc/server/model/entity/player/Player.java`
- Session ID, inventory, bank, equipment, skills, quests, clan, party
- Trade, duel, social (friends/ignores)
- Walk-to-action queue, appearance, activity tracking
- `processTick()` — per-tick entry
- `processLogout()` — deferred unregister gate
- `sendUpdates()` — outbound state flush

**Npc** — `server/src/com/openrsc/server/model/entity/npc/Npc.java`
- `NPCDef`, `NPCLoc` (spawn bounds)
- Combat damagers map (who hit, with what)
- `NpcBehavior` plugin event handler
- No own `processTick()`; movement via `GameStateUpdater.processNpcs()`

## Player session lifecycle

**Connect**
Netty channel accepted → `RSCConnectionHandler` reads login payload (see `networking-protocol.md`).

**Login** — `server/src/com/openrsc/server/login/LoginRequest.java`
1. Queued in `LoginExecutor.loginRequests` (rate-limited per tick by `MAX_LOGINS_PER_SERVER_PER_TICK`).
2. Processed async in `LoginExecutor` thread (50ms fixed rate).
3. Database load: stats, inventory, quests, settings.
4. Player instantiated, attributes set.
5. `World.registerPlayer(player)` → added to player list, region updated, social alerts sent.
6. Response packet sent with map data and player info.

**In-world** (each tick)
- `Player.processTick()` — packet handling, position, events, message queue
- `Player.sendUpdates()` — appearance/motion/inventory updates serialized

**Save** — `PlayerSaveRequest` queued on logout or autosave interval (default 30s); processed in `saveRequests` queue ahead of any logout.

**Disconnect** — `Player.unregister()`
1. Marks `UnregisterRequest` (waits for combat end if mid-fight).
2. Next tick: `Player.processLogout()` reads flag, calls `World.unregisterPlayer(player)`.
3. Unregister:
   - Logs offline flag to DB
   - Generates avatar (optional)
   - Logs player logout
   - Queues channel close via `DelayedEvent` (2.5s — allows logout packet delivery)
4. Channel freed, player removed from world.

## Scheduler / event system

Base class: `GameTickEvent` — `server/src/com/openrsc/server/event/rsc/GameTickEvent.java`
- `Mob owner` (player, NPC, or null for world events)
- `int delayTicks` — countdown until run
- `abstract run()` — subclass action
- `doRun()` — checks readiness, calls `run()`, resets countdown
- `shouldRemove()` — cleanup flag (`!running`)

Subclasses:
- `DelayedEvent` — `server/src/com/openrsc/server/event/DelayedEvent.java`. Converts ms delay to tick count; owned by player.
- `FinitePeriodicEvent` — runs N times then stops.
- `SingleTickEvent` — runs once, next tick.
- `ImmediateEvent` — runs immediately, no delay.
- `PluginTickEvent` — wraps a plugin callable.

Handler: `GameEventHandler` — `server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java`
- Stores events in `GameTickEventStore` (per-owner lookup)
- Configurable executor (single-threaded by default; respects PID order)
- `processNonPlayerEvents()` — runs world/NPC events
- `runPlayerEvents(Player)` — runs player-specific events
- `cleanupEvents()` — removes `shouldRemove() == true` events

Example (3-second delay):
```java
new DelayedEvent(world, player, 3000, "Do something") {
    public void run() { /* action */ }
};
world.getServer().getGameEventHandler().add(event);
```

## Threading model

- **Main game thread**: single `ScheduledExecutorService` ("GameThread") runs `Server.run()` every 10ms. `synchronized(running)` block in `run()` gates tick execution. No per-player locking — uses `AtomicReference` and `ConcurrentHashMap` for location/region.
- **Event executor** (`GameEventHandler`): `ThreadPoolExecutor`, 1 thread by default. Configurable via `want_threading__break_pid_priority` (`WANT_THREADING__BREAK_PID_PRIORITY`) — if true, parallel execution; PID order **not** guaranteed (race risk on shared scenery).
- **LoginExecutor**: separate `ScheduledExecutorService`, 50ms interval. Processes `LoginRequest`, `PlayerSaveRequest`, etc. — DB lookups happen off main thread.
- **DB threads**: `sqlThreadPool` (1 thread, batched queries), `sqlLoggingThreadPool` (1 thread, game logs), `onlineMonitorThreadPool` (1 thread, reachability).
- **Netty I/O threads**: Boss + Worker `NioEventLoopGroup`. Non-blocking; delegates parse to `PayloadProcessorManager`.

## Region / chunk system

`RegionManager` — `server/src/com/openrsc/server/model/world/region/RegionManager.java`
- 2D hash: `ConcurrentHashMap<Integer, ConcurrentHashMap<Integer, Region>>`
- Each region is a 48×48 tile grid (`Constants.REGION_SIZE = 48`), matching RSC sector size.
- Indexed by region X, Y.

`Region` — `server/src/com/openrsc/server/model/world/region/Region.java`
- Per-tile multimaps: players, NPCs, objects, ground items
- `TileValue[][]` — collision/decoration flags
- Synchronized via Guava `Multimaps.synchronizedMultimap()`

Visibility (AOI):
- `RegionManager.getVisibleRegions(Point)` — regions within `VIEW_DISTANCE` (config key `view_distance`, default `2`; player/NPC visibility range is `VIEW_DISTANCE * 8` tiles)
- `getLocalPlayers()`, `getLocalNpcs()`, `getLocalObjects()` — filter visible
- Used in `GameStateUpdater.updatePlayers()` to build update packets

Entity registration:
- `entity.setLocation(Point)` calls `updateRegion(oldLocation)` — removes from old, adds to new
- Triggers: login, movement, teleport, despawn

## Pitfalls / non-obvious

1. **Logout is deferred.** `Player.unregister()` sets a flag; actual removal happens in `processLogout()` at tick-end. Removing earlier can cause NPE in mid-tick operations.
2. **Event cleanup is post-tick.** Events marked `shouldRemove()` aren't deleted until tick-end cleanup; accessing dead events can fail.
3. **Region updates are not atomic.** Movement = read old → remove → add new. Concurrent `getLocalPlayers()` can race during transition.
4. **PID order non-determinism.** With `SHUFFLE_PID_ORDER = true`, player iteration order changes per cycle; plugins assuming stable order misbehave.
5. **LoginExecutor rate-limits logins.** `MAX_LOGINS_PER_SERVER_PER_TICK` caps logins per tick; queue backs up under load. Async, doesn't block game thread.
6. **Tick cadence math is in nanoseconds.** Game loop checks `if (timeLate >= GAME_TICK * 1_000_000)`. The 10ms scheduler is separate; tick only fires after 640ms+. Lag → skipped ticks.
7. **No transaction isolation across tick.** Player saves are queued but not atomic with mid-tick state changes. A crash mid-tick mid-save can lose data.
8. **Parallel event mode is dangerous.** If `WANT_THREADING__BREAK_PID_PRIORITY` is true, plugin code that touches shared scenery/multi-access entities can race.
9. **Netty backpressure.** If the main thread can't keep up, packets queue on I/O threads. Doesn't lose data but adds latency.

## Glossary candidates

- **Tick** — one iteration of the game loop. 640ms by default. All entities update once per tick.
- **PID** — Player ID, used for ordering. OpenRSC respects registration order for consistency (unless shuffled).
- **Mob** — actor with combat/movement state (Player or NPC).
- **DelayedEvent** — `GameTickEvent` that waits N ticks before `run()`.
- **DuplicationStrategy** — policy for adding a second event with same owner (`ALLOW_MULTIPLE`, `ONE_PER_SERVER`, etc.).
- **Region** — 48×48 tile grid; world is partitioned for visibility queries.
- **AOI** — Area of Interest. Set of entities visible to a player; computed via region overlap + range check.
- **LoginExecutor** — async thread pool for login/save processing; decoupled from main game thread.
- **UnregisterRequest** — deferred logout flag; prevents mid-tick player removal races.
- **Walk-to-action** — queued pathfind + action (e.g. walk to object then interact).
