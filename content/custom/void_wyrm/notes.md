# Void Wyrm

## Decisions

- Release status: archived. The boss, fourth floor, private encounter, and reward path are not active gameplay.
- Original full-body boss art rather than a recoloured existing dragon.
- Obsidian armour, a bone crown and plated spine, with restrained acid-green core and fissure accents.
- Five directional views plus one three-frame side attack strip after concept approval.
- The crown-heavy right-hand concept was approved; production uses `art/source/generated/sheet-v1/variant_01.png`.
- Simplify the tallest crown branches during sprite production so the silhouette remains readable at 105x200.

## Test Notes

- The deterministic importer emits 15 walking frames, three attack frames, frame sidecars, an import manifest, and `art/final/void-wyrm-proof.png`.
- The final frames use hard alpha, no more than 24 colours per frame, and stable logical canvases across each animation group.
- Client and server `Authentic_Sprites.orsc` entries `1917..1934` remain byte-identical but dormant.
- The former server runtime is stored under `archive/server` with `.java.disabled` extensions. NPC id `869`, animation index `245`, active definitions, drops, spawn, and plugin loading are removed.
