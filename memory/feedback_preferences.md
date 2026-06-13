---
name: Working preferences
description: Conventions and preferences confirmed for the voidscape project
type: feedback
---

Established 2026-04-24 during foundation setup:

- **Mostly-authentic RSC target with QoL room.** Lean toward upstream's `preservation.conf` family of presets when in doubt; document any deviation in `docs/DIVERGENCE.md`.
  - Why: the user explicitly chose this scope ("mostly authentic, QoL and maybe small customization") rather than a custom-meta server.
  - How to apply: when proposing changes, prefer the version that preserves authentic RSC behavior unless QoL is the explicit goal.

- **Foundation-first, code-later.** Before adding any features, ensure `docs/`, scripts, and Claude Code config are solid. Don't get pulled into "let me just add this NPC real quick" while the map of the codebase is incomplete.
  - Why: skipping the foundation is exactly the failure mode the user wanted to avoid.
  - How to apply: if asked to do feature work but the relevant `docs/recipes/` or `docs/subsystems/` doc is missing, write the doc first, then do the work.

- **Hard fork strategy** — voidscape diverges freely from OpenRSC. Re-syncing with upstream is manual and rare.
  - Why: scope is custom enough that automated sync would create constant merge pain.
  - How to apply: don't suggest "let's pull upstream's fix" without checking `docs/DIVERGENCE.md` for prior decisions about that area.

- **Use `scripts/*.sh` over raw `gradlew`.** Standardized entry points reduce drift.
  - Why: when each session improvises its own build invocation, the project's command surface fragments.
  - How to apply: if a build/run step doesn't have a script, add one.

- **Voidscape UI should stay compact and transparent where RSC benefits from it.** Utility panels such as inventory, friends/ignore, magic, and prayer should keep a clear glass/tinted content surface so the world remains visible and the client does not feel claustrophobic. Prefer smaller panels with tasteful borders over large nested frames; stats/loot/options can be more opaque when readability needs it.
  - Why: the user explicitly prefers the classic roomy RuneScape feel over heavy fantasy UI chrome.
  - How to apply: fit UI changes one element at a time, avoid raw-swapping generated art into slice-sensitive assets, and verify in-game screenshots before keeping a panel chrome change.
