---
name: Project voidscape
description: What voidscape is and where it sits in development
type: project
---

Voidscape is a **private RuneScape Classic server**, hard-forked from OpenRSC Core-Framework. Goal: mostly authentic RSC, with quality-of-life additions and small customization room. Local-only at the moment; not running publicly.

**Foundation complete (2026-04-24).** Vendored OpenRSC at SHA `fc74d38e`. Full docs hierarchy in `docs/` (ARCHITECTURE, CODEMAP, DEVELOPMENT, SERVER-PRESETS, GLOSSARY, DIVERGENCE, recipes/, subsystems/). Scripts in `scripts/` (`build.sh`, `run-server.sh`, `run-client.sh`, `reset-db.sh`, `fetch-upstream-snapshot.sh`). Memory + `.claude/` config + per-subsystem `CLAUDE.md` briefs in place. Build verified on JDK 25 (~5s); server boot, SQLite seed, registration, login, and plugin trigger chain (`onPlayerLogin`) all validated end-to-end with test player "void". Pushed to private GitHub repo `srez92/voidscape`.

**Current preset** (`server/local.conf`, gitignored — see `docs/DIVERGENCE.md` for bootstrap):
- `want_fatigue: false` (QoL)
- `member_world: false` (F2P world)
- `is_localhost_restricted: false` (dev only)
- Authentic preserved: `game_tick: 640`, `combat_exp_rate: 1`, `skilling_exp_rate: 1`, `location_data: 1`.

**Ryan's roadmap (stated 2026-07-03), in order:**
1. **Bug-fix everything in the game** — the autonomous ledger loop (`/fix-bugs`,
   docs/BUGS.md + docs/BUGFIX-LOOP.md) is the vehicle; it survives context clears by
   design, so no handoff prompt is required — `/fix-bugs` alone resumes.
2. **Standardize the UI** — beautiful, easily usable, readable. Feature-scale: use
   `/feature` with Ryan's sign-off on direction before restyling broadly (the Void
   Glass bank is the existing quality bar / design reference).
3. **The absolute best Android APK of any private server.** Feature-scale, physical-
   device QA gates apply.
Preferred working rhythm: fix bugs / add a feature → durable-state update → session
clear is fine at any time. Sessions should lean on the ledger/memory, not on pasted
	handoffs. (A launch-readiness sweep of docs/RELEASE-CHECKLIST.md was queued 2026-07-03
	ahead of phase 1's tail. The public launch was moved one week to 2026-07-18 at
	18:00 UTC on 2026-07-10.)

**Why this matters for future sessions:** the foundation phase is done. The next session should NOT be re-bootstrapping or re-exploring the codebase — that work is captured in `docs/`. It should be doing real work.

**How to apply:**
- For "what is voidscape / where are we?" — answer from this memory + root `CLAUDE.md` + `docs/DIVERGENCE.md`. Don't re-explore.
- For real changes — point at `docs/recipes/` (concrete `add-item`/`add-npc`/`add-quest`/`add-skill-action`/etc. playbooks) and the relevant `docs/subsystems/*.md`.
- For build/run — use `scripts/*.sh`. Bootstrap `server/local.conf` per `docs/DIVERGENCE.md` if missing on a fresh checkout.
- If you find yourself recreating any foundation piece, something's wrong — read it instead.
