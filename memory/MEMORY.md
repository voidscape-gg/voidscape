# Memory index

This file is always loaded into context. Keep entries one line, under 150 characters. Detail goes in the linked file.

- [Project: voidscape](project_voidscape.md) — what voidscape is, current focus, status
- [Reference: OpenRSC upstream](reference_openrsc_upstream.md) — fork source, SHA, license
- [Reference: project layout](reference_layout.md) — where docs / scripts / memory live
- [Feedback: working preferences](feedback_preferences.md) — confirmed conventions for this project
- [Android: emulation QA](android_emulation.md) — AVD roles and current headless screenshot gotchas
- [Beta deployments](beta_deployments.md) — VPS beta deploy paths, runtime/migration sync, checks
- [Content pipeline](content_pipeline.md) — custom content/art starts with scripts/content.sh packs and validation
- [Custom wearables](custom_wearables.md) — one-based appearance IDs, dual archives, preset and noteability gotchas
- [Christmas cracker reel](christmas_cracker.md) — 60/20/20 server roll, trusted v1 envelope, shared reel/Workbench QA invariants
- [Discord posting](discord_posting.md) — canonical VoidBot command for bug-feed/release-note posts
- [iPhone web client](iphone_web_client.md) — TeaVM mobile status, HUD direction, chat/world-map blockers; next chat handoff: `docs/iphone-web-client-next-chat-context.md`
- [Prelaunch readiness](prelaunch_readiness.md) — portal policy/countdown/account-manager slice done; remaining live/client gates
- [Bug-fix loop](bug_loop.md) — docs/BUGS.md = living ledger + intake, docs/BUGFIX-LOOP.md = protocol, /fix-bugs runs it; ledger > memory
- [QA campaign](qa_campaign.md) — docs/QA-CAMPAIGN.md = parallel bot-fleet QA; /qa-campaign runs it; dev DB has ZERO accounts until Phase 0
- [Feedback: UI QA](feedback_ui_qa.md) — catch UI overlaps deterministically (scripts/ui-geometry-lint.py), not by eye; prefer original OpenRSC bank sizing
- [Project: bank UI rebuild](project_bank_ui_rebuild.md) — Void Glass bank SHIPPED 2026-07-02 (search/tabs/loadouts/note-mode, 6 viewports); open: drag-reorder for Ryan; skin-on test recipe inside
- [Feedback: bugfix autonomy](feedback_bugfix_autonomy.md) — run the bug-fix/QA loop straight through, no per-fix approval pauses
- [Perf pass 2](project_perf_pass2.md) — 2026-07-03 tick-loop pass: results, do-not-break invariants (NPC event parking, inline events, WAL), next wall = update generation
- [Prelaunch priorities](prelaunch_priorities.md) — agreed work order: exploit sweep → device QA → bug triage → golden-path QA → soak → jar deploy
- [Duel proof](duel_proof.md) — approved melee-only commit/reveal design, privacy limits, slice status, and remaining gates
- [Headless players](headless_players.md) — ordinary-account fleet lifecycle, route, credential, and systemd invariants
- [Player titles V2](player_titles_v2.md) — 64-row main-loop catalog, two worn slots, glass ceremony, Court, and 10137 tail
