---
name: prelaunch-priorities
description: Ryan-approved pre-launch work order (2026-07-03) — exploit sweep first, then device QA, triage, soak, jar deploy
metadata:
  type: project
---

Agreed launch-prep order as of 2026-07-03 (perf pass done and accepted, see [[project-perf-pass2]]; portal/packaging gates already green per prelaunch_readiness.md):

1. **Dupe/exploit sweep of custom economy paths** — adversarial multi-agent audit + live voidbot probes. Surfaces: auction house, bank notes + presets + over-cap edge (VS-048 area), subscription cards (item 1602 / starter_card claim path), trade/duel cancel + logout races, death-drop timing, batch-skilling interrupts, ::item/admin item creation (VS-002 binding note). Rationale: dupes are the only bug class that forces a wipe.
2. **Physical device QA (Ryan's hands)** — iPhone Safari packet at tmp/iphone-web-qa-live-prelaunch-v1/iphone-safari-qa-report.md; Android via scripts/run-android-device-qa.sh.
3. **/fix-bugs triage pass** — 17 raw Intake bullets + ~18 open bugs in docs/BUGS.md; classify showstopper vs post-launch. VS-011 (mobile web tap misrouting) and VS-001 (desktop UI umbrella) sit on the new-player golden path — prioritize those.
4. **Golden-path /qa-campaign** focused on the new-player first hour (portal signup → client → Void Island starter → Lumbridge → first fight/death/bank/save), all three clients — not the whole game.
5. **24h soak** — ~100 loadbots overnight, watch gc.log heap trend + PERF tick drift for the day-3-crash class.
6. **Prod jar deploy** — blocked on Ryan committing the working tree (it mixes the perf pass with in-flight cinematic/portal work). Flow in [[beta-deployments]]; prod launch flags already fixed and boot-verified, service left stopped.

Explicitly deprioritized before launch: more perf work (update-gen wall only matters at ~300 co-located players), new content/features, cosmetic polish (bank drag-reorder).