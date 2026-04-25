---
name: OpenRSC upstream
description: Source repo, vendored SHA, license terms, snapshot location
type: reference
---

Voidscape is forked from **`Open-RSC/Core-Framework`** (GitHub).

- Vendored SHA: `fc74d38e2ead0a5864b48ae191b7184a391777cf`
- Vendored date: 2026-04-24
- License: **AGPLv3** (inherited — voidscape inherits AGPL obligations including source disclosure if distributed publicly).
- Frozen snapshot: `upstream/openrsc-snapshot/` (gitignored locally; recreate via `scripts/fetch-upstream-snapshot.sh`).
- Canonical record: `docs/DIVERGENCE.md`.

**Hard rule**: never edit `upstream/openrsc-snapshot/`. It's a diff reference, not a working tree.

When the user asks about "upstream behavior" or "what did OpenRSC do here", check the snapshot — it's the authoritative answer for the unmodified codebase.
