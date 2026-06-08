---
name: Content pipeline
description: Current custom content/art workflow convention
type: project
---

Custom Voidscape content should start with `scripts/content.sh`, not scattered manual edits.

- `scripts/content.sh new <item|npc|boss|arena|texture> <slug>` creates a pack under `content/custom/<slug>/`.
- `scripts/content.sh report` prints current safe allocation points. As of the first slice: next item id `1603`, next NPC id `852`, next item spriteID `618` / archive index `2768`.
- `scripts/content.sh validate` checks item/NPC sequencing, client/server definition coverage, custom item sprite archive entries, content-pack shape, and client-version visibility.
- Historical OpenRSC/Voidscape drift is reported as warnings; new tail drift that would break beta clients should stay an error.
- Existing item icon/wielded sprite tooling remains available through `scripts/content.sh voidscim ...`.
- Use `tools/voidscape-content/templates/voidscape-art-brief.md` when asking ChatGPT for Voidscape concepts/assets.
