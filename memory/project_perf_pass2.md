---
name: project-perf-pass2
description: "2026-07-03 tick-loop perf pass — results, invariants that must not be broken, and the next scaling wall"
metadata: 
  node_type: memory
  type: project
  originSessionId: e244d0c5-9b34-41f7-b3a7-6322eaabc167
---

2026-07-03 performance pass (docs/PERFORMANCE.md "2026-07-03 Pass", docs/DIVERGENCE.md same date) cut idle tick p50 24-39ms → ~8ms and made 301 co-located loadbots run with zero skipped ticks (p95 ~500ms → ~165ms). Server now launches via `runserverzgc` (ZGC, 2g heap, rotating logs/gc.log) from scripts/run-server.sh. Ryan accepted the pass as done 2026-07-03; declared good for 100-player load. Prod launch flags FIXED 2026-07-03: VPS java is 17.0.19; prod systemd unit runs /opt/voidscape/scripts/run-server.sh (NOT Deployment_Scripts/run.sh), which now calls runserverzgc; VPS build.xml updated to match repo (ZGC -Xms1g -Xmx2g + gc.log; backups *.bak-20260703 beside both files). Boot-verified ("Voidscape started in 3538ms", ZGC flags on cmdline, gc.log rotating), then service returned to its stopped prelaunch state. Also fixed pre-existing root-owned server/logs/default.log (source of 18 log4j Permission-denied boot errors since Jun 24). **Remaining for launch: deploy the perf-pass core.jar/plugins.jar to the VPS (documented flow in beta_deployments.md: stop, backup DB, rsync jars, start) once the working tree is committed — prod jars predate the perf pass.**

Invariants introduced (breaking these silently regresses gameplay or perf):
- **NPC `StatRestorationEvent`s park at full stats** and are re-armed only via `Skills` mutators (`rearmNpcRestoration()` → `Mob.ensureStatRestorationActive()`). Any new code that writes `Skills.levels[]` directly without going through a Skills mutator will leave damaged NPCs permanently unhealed.
- **Events run inline on the game thread** when the event pool is single-threaded (`GameEventHandler.singleThreaded`); code must not assume events run on the "EventHandler" thread.
- **Never call `System.gc()` on the game thread** — the late-tick monitoring path used to, and it self-amplified (~371ms full-GC per late tick).
- SQLite runs WAL: bare-file .db backups need `PRAGMA wal_checkpoint(TRUNCATE)` first (clean shutdown checkpoints automatically).
- `Region.getNpc/getPlayer` and `RegionManager.getVisibleRegions` are order-preserving by contract (aggro target selection + packet order depend on it).

Next scaling wall: update-packet generation (~90% of tick at 301 co-located players; `update` stage 95-148ms p95). Planned lever: share per-tick region scans across entity classes / per-tick snapshot cache keyed by ordered region list — see PERFORMANCE.md Remaining Plan.

Measurement gotchas: idle tick times on the dev Mac drift ~2.5x with efficiency-core scheduling (not a leak — Linux staging is the only valid regression baseline). scripts/perf-load.sh uses AppleScript keystrokes (violates the voidbot rule) — drive `::loadbots` via `voidbot admin` instead. First post-restart tests/smoke.sh run often flakes (documented warm-up pattern); rerun before believing a failure.
