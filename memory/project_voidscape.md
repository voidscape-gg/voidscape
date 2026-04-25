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

**Why this matters for future sessions:** the foundation phase is done. The next session should NOT be re-bootstrapping or re-exploring the codebase — that work is captured in `docs/`. It should be doing real work.

**How to apply:**
- For "what is voidscape / where are we?" — answer from this memory + root `CLAUDE.md` + `docs/DIVERGENCE.md`. Don't re-explore.
- For real changes — point at `docs/recipes/` (concrete `add-item`/`add-npc`/`add-quest`/`add-skill-action`/etc. playbooks) and the relevant `docs/subsystems/*.md`.
- For build/run — use `scripts/*.sh`. Bootstrap `server/local.conf` per `docs/DIVERGENCE.md` if missing on a fresh checkout.
- If you find yourself recreating any foundation piece, something's wrong — read it instead.
