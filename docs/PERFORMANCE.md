# Voidscape Performance Map

This note captures the current server/game performance baseline after the first optimization pass on 2026-04-30. It is intentionally practical: what is vanilla, what voidscape changed, where latency comes from, and what to optimize next.

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

## Remaining Plan

Highest-value next work:

1. Push the synthetic load higher in controlled steps: 100, 200, then 300 bots. Stop at the first interval with nonzero skipped ticks or repeatable `p95` above roughly 200ms.
2. If `update` remains the dominant stage, profile `Player.sendUpdates()` and region-visible entity packet generation; that is the next likely multiplayer scaling limit.
3. Review SQLite save pressure under real clients; if saves bunch up, stagger autosaves or move public testing to MySQL/MariaDB.
4. Build a true headless/light TCP client swarm only when we need to test auth, socket backpressure, login/logout churn, or WAN-like connection behavior.
5. Move expensive one-shot jobs off the game thread if telemetry catches a repeatable stage spike.
6. Make boot warnings actionable or quiet: missing query registrations, SLF4J binder warning, missing word filter files.
