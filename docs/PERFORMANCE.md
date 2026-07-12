# Voidscape Performance Map

This note captures the current server/game performance baseline after the optimization passes on 2026-04-30 and 2026-07-03. It is intentionally practical: what is vanilla, what voidscape changed, where latency comes from, and what to optimize next.

## Plain English Result

The server now has a built-in way to stress the game loop without needing dozens of real people online. `::loadbots` creates fake in-world players that still exercise walking, regions, player ticks, nearby entity scans, NPC/player update generation, and event overhead.

At 50 synthetic moving players plus the real admin client, the server stayed comfortably below its `640ms` tick budget: warm tick `p95` stayed around `13-17ms`, no ticks were late, no ticks were skipped, and cleanup returned the world to one real player. This means the current game loop is not close to lagging at 50 nearby moving players. The next useful test is to raise synthetic load in steps until telemetry shows the first real bottleneck.

## Baseline

Voidscape is a hard fork of OpenRSC Core-Framework at vendored SHA `fc74d38e2ead0a5864b48ae191b7184a391777cf`, preserved in `upstream/openrsc-snapshot/`. Vanilla OpenRSC already provides:

- A single authoritative game thread ticking at `game_tick: 640`.
- Netty TCP/WebSocket networking with packet queues drained during each player tick.
- Async login/save work through `LoginExecutor`.
- SQLite/MySQL persistence through one synchronized JDBC connection.
- Reflection-based plugin triggers for content.
- Region-based AOI over 48x48 sectors.

Voidscape keeps that architecture and layers on QoL/content:

- Client QoL toggles: roofs off, XP drops, ground names, keyboard menu labels, batch skilling, bank/loadout UX, zoom/fog changes.
- Custom protocol: world-map walk request/route and item examine request.
- World-map autowalk: sparse A*, cross-floor telepoint graph, auto-open route obstacles, route overlay.
- Custom content: Home teleport, Edgar, Void Island starter flow, Void Enclave, auctioneer, PK catching simulator, void/cursed items, item kill memory.
- Runtime config: local SQLite DB, F2P world, `game_tick: 640`, `auto_save: 120000`, packet capture now off by default.

## Hot Paths

The most important runtime paths are:

- Tick loop: `Server.run()` -> `GameStateUpdater` -> `Player.processTick()` / `sendUpdates()`.
- Network egress: `ActionSender.tryFinalizeAndSendPacket()` -> `Player.write()` -> `Player.processOutgoingPackets()`.
- Network ingress: Netty decoder -> `Player.addToPacketQueue()` -> `Player.processIncomingPackets()`.
- Plugin dispatch: `PluginHandler.handlePlugin()` and `PluginTickEvent` execution.
- Autowalk: `WorldWalkRequest` -> `WorldPathfinder` -> optional `WaypointGraph` guide -> `AutoWalkEvent` -> `WalkingQueue`.
- Persistence: autosave/logout -> `PlayerSaveRequest` -> `LoginExecutor` -> `PlayerService.savePlayer()`.

## 2026-04-30 Pass

Completed changes:

- Batch Netty writes per player tick and flush once after all queued packets are written.
- Reuse stateless outbound payload generators in `ActionSender`.
- Cache the inbound payload parser per player after login.
- Cache plugin trigger method resolution and move per-plugin action traces from INFO to DEBUG.
- Log missing trigger types once instead of on every action.
- Prebuild `TelePointGraph` and preload `WaypointGraph` at world load so the first world-map click does not pay those costs.
- Trim `WorldPathfinder` allocations for F2P checks and auto-open probing.
- Keep waypoint-guided routing collision-authoritative by validating direct graph legs with server collision and using bounded local A* only when a leg is not directly walkable.
- Keep `PcapLoggerService` stopped when packet capture is disabled.
- Set local `want_pcap_logging: false`.
- Correct the world tick-loop docs to `Constants.REGION_SIZE = 48`.
- Add an in-process synthetic player swarm (`::loadbots`) that exercises real player ticks, walking, region membership, AOI scans, NPC/player update generation, and event overhead without auth/socket/database cost.
- Keep synthetic load players out of admin saves, autosaves, and database-backed logout saves.

## Telemetry

The local server config enables:

- `perf_telemetry: true`
- `perf_telemetry_interval_seconds: 30`
- `perf_telemetry_window_ticks: 512`

When enabled, the server emits compact `PERF` log lines with rolling tick latency, late/skipped ticks, packet counts, queue pressure, and stage `p95` timings. Watch them with:

```bash
./scripts/perf-watch.sh
```

Run a quick local pulse test through the visible Java client with:

```bash
./scripts/perf-smoke.sh
```

That script sends two `::pf` pathfinder probes plus `::saveall`, waits for the next telemetry interval, and prints the recent `PERF`/late-tick lines.

Run a local synthetic player swarm with:

```bash
./scripts/perf-load.sh 50 70 18 2
```

That command uses the visible logged-in Java client to send `::loadbots start 50 18 2`, holds the swarm for 70 seconds, sends `::loadbots stop`, then prints recent telemetry. The command arguments are `count`, `durationSeconds`, `radius`, and `intervalTicks`.

You can also drive it manually in-game:

```text
::loadbots start <count> [radius] [intervalTicks]
::loadbots status
::loadbots stop
```

The harness is intentionally in-process, not a full TCP/auth swarm. It is the fastest way to stress game-thread work and update generation; use a true client swarm later if the bottleneck shifts to connection handling, auth, socket backpressure, or login/logout churn.

The two lines to care about first are:

- `PERF ticks=... tick_ms p50/p95/p99/max=... late_ticks=... skipped_ticks=... queues ...`
- `PERF stages_p95_ms world/npc/player/events/walk/msg/update/in/out/cleanup=...`

Use the stage line to pick the next real bottleneck. For example, high `update` points at client update packet generation, high `out` points at Netty egress pressure, high `player/events` points at gameplay/plugin execution, and nonzero `save`/`sql` queues point at persistence pressure.

Verification:

- `./scripts/build.sh` passes.
- `./scripts/run-server.sh` boots successfully.
- Server boot after the pass: TCP `43596`, WS `43496`, `TelePointGraph` and `WaypointGraph` warm during startup.
- `./scripts/perf-smoke.sh` passes with 0 late/skipped ticks.
- `./scripts/perf-load.sh 50 130 18 2` held 50 synthetic players plus the real admin client (`players=51`) across the autosave boundary with 0 late/skipped ticks. During warm intervals, tick `p95` stayed in the `12.6-16.5ms` range and update-generation `p95` stayed in the `3.6-6.7ms` range. The only save-queue blip was one real-player save, and the first full interval after stop returned to `players=1`.

## 2026-07-03 Pass

Escalating `::loadbots` measurement found the loop had regressed since April (content growth: Void Dungeon mobs, sieges, arenas), and an 11-dimension multi-agent audit with adversarial verification identified the causes. Full change list and authenticity notes: `docs/DIVERGENCE.md` (2026-07-03 tick-loop entry). Highlights:

- Deleted the `System.gc()` that ran on the game thread on every late tick (~371ms full-GC stall on an already-late tick, self-amplifying; observed p99 spikes over 1.2s).
- Events run inline on the game thread when the event pool is single-threaded (the default) — no more ~3,700 FutureTask handoffs per tick through `invokeAll`.
- Per-NPC `StatRestorationEvent`s park when the NPC has nothing to restore; re-armed by a centralized `Skills` mutator hook. Idle event population drops ~99%.
- NPC roam phase jittered at spawn (the whole boot cohort used to path-build in lockstep every 5 ticks — that WAS the idle npc-stage p95).
- Collision checks (`PathValidation.checkBlocking`/`isMobBlocking`) resolve the Region once and use non-stream first-match tile lookups; `Path.addStep` probes diagonals lazily; `RegionManager` lookups are 2 lock-free reads instead of 5; `getVisibleRegions` allocates no boxed lists; `getLocalPlayers` short-circuits at 0 players online.
- Update generation: scenery region scan shared between scenery+boundary passes; appearance dedup is a map lookup instead of an O(localPlayers²) scan.
- SQLite: WAL + synchronous=NORMAL + busy_timeout; connection-level `ReentrantLock` held across whole `atomically()` transactions (closes a save-corruption interleaving window).
- Runtime: `run-server.sh` now uses the `runserverzgc` target (ZGC, 2g fixed heap); both ant targets write rotating `logs/gc.log`; G1 fallback loses the dead `UseBiasedLocking`/64m-young-gen pins.
- Client: bounded 32-packet drain per frame in `mudclient.checkConnection` — a server tick's batched flush now renders in one frame instead of smearing over several 20ms frames.

Measured (same machine, same world, 3,658 NPCs; warm intervals, `tick_ms p50/p95`):

| Players | Before (2026-07-03 AM) | After | Skipped ticks |
|---|---|---|---|
| 0 (idle) | 24-39 / 44-84 | **8 / 14** | 0 → 0 |
| 51 | 42-49 / 85-126 | **15-20 / 22-28** | 0 → 0 |
| 151 | 100-117 / 168-322 | **42-52 / 66-69** | 2-3 → **0** |
| 301 | ~220 / ~500 | **98-116 / 155-176** | 3+ → **0** |

Idle stage p95s: npc 47-62ms → ~11ms, events 13-15ms → ~0.1ms. At 301 players the dominant stage is now `update` (95-148ms, ~90% of the tick) — that is the next scaling wall.

Also diagnosed, no code change: the "idle tick creep" over uptime on the dev Mac is efficiency-core scheduling of the mostly-idle game thread, not a leak (heap flat over 25 min; the step reverses without restart). Treat Linux staging as the only regression baseline for absolute idle numbers.

Verification: `scripts/build.sh` green; client `ant compile` green; server boots on ZGC with `logs/gc.log` rotating; `tests/smoke.sh` 26/26 (first post-restart run flaked 25/26, the documented warm-up pattern); NPC heal-cycle regression check (damage NPC, verify wall-clock heal cadence) — see BUGS.md/DIVERGENCE if it ever regresses.

## Remaining Plan

Highest-value next work:

1. **Update-packet generation is the scaling wall** (95-148ms p95 at 301 co-located players; ~90% of tick). Next lever: share the per-tick visible-region scans across the four entity classes and cache per-tick entity snapshots for co-located observers (ordered-region-list keyed, per the verified audit finding), or profile `updatePlayers`/`updateNpcs` inner loops.
2. Note that 301 players in one spot is a deliberately brutal worst case — 301 spread across the map costs far less. Re-test with distributed bots before optimizing further.
3. Review SQLite save pressure under real clients (WAL landed 2026-07-03; if saves still bunch, batch the game-logger drain into one transaction per 50ms flush).
4. Build a true headless/light TCP client swarm only when we need to test auth, socket backpressure, login/logout churn, or WAN-like connection behavior.
5. Prod deploy: `Deployment_Scripts/run.sh` still forces the g1gc arg — drop it only after confirming the VPS java is 17+ (ZGC does not exist on Java 8; see ant_launcher.sh comment).
6. Make boot warnings actionable or quiet: missing query registrations, SLF4J binder warning, missing word filter files.
