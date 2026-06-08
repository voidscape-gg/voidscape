---
name: Project layout
description: Where to find docs, scripts, memory, and OpenRSC code in voidscape
type: reference
---

Voidscape preserves OpenRSC's tree structure (`server/`, `PC_Client/`, `Client_Base/`, `Android_Client/`, `PC_Launcher/`, `Deployment_Scripts/`, etc.) and overlays voidscape-specific dirs:

- `CLAUDE.md` — root brief (always loaded). Points elsewhere.
- `docs/` — `ARCHITECTURE.md`, `CODEMAP.md`, `DEVELOPMENT.md`, `DIVERGENCE.md`, `GLOSSARY.md`, `SERVER-PRESETS.md`, plus:
  - `docs/subsystems/` — deep dives per subsystem (networking, world tick, combat, persistence, scripting/plugins, client).
  - `docs/recipes/` — step-by-step "how to add X" playbooks.
- `scripts/` — `build.sh`, `run-server.sh`, `run-client.sh`, `reset-db.sh`, `fetch-upstream-snapshot.sh`. Canonical entry points. **Use these, not raw `gradlew`.**
- `content/` — custom content-pack manifests and art workspaces. Start new custom items/NPCs/bosses/arenas/textures with `scripts/content.sh new`.
- `tools/voidscape-content/` — content-pack CLI, validators, and Voidscape ChatGPT art brief. Bridges legacy item art through `scripts/content.sh voidscim ...`.
- `.claude/` — Claude Code config (`settings.json` tracked, `settings.local.json` gitignored), custom subagents in `.claude/agents/`.
- `.agents/` — Codex skills/workflow scaffolding, including the discovery-first `feature` skill.
- `memory/` — persistent project memory (this directory).
- `upstream/openrsc-snapshot/` — frozen vendor reference (gitignored, recreate via script).

Subsystem-level CLAUDE.md files live at `server/CLAUDE.md`, `PC_Client/CLAUDE.md`, etc. — they auto-load when editing inside that tree.

**How to apply**: When you need to find something, read `docs/CODEMAP.md` first — it's the directory-by-directory index. Don't grep blindly across the whole tree.
