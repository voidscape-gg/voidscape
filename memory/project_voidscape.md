---
name: Project voidscape
description: What voidscape is and where it sits in development
type: project
---

Voidscape is a **private RuneScape Classic server**, hard-forked from OpenRSC Core-Framework. Goal: mostly authentic RSC, with quality-of-life additions and small customization room. Local-only at the moment; not yet running publicly.

**Current focus:** establishing the foundation — vendoring upstream, building docs/architecture maps, setting up scripts and Claude Code config so future sessions don't have to re-explore the codebase.

**Why:** RSC private servers are massive codebases. Without a real foundation, every Claude session burns context re-discovering the same code map.

**How to apply:** Treat the architecture/recipe docs in `docs/` as the canonical source of truth for "how things work here." Read them before exploring. When you discover something not yet documented, add it to the right doc rather than only fixing the immediate task — the foundation is still being built.
