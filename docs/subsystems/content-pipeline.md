# Content Pipeline

Voidscape custom content is moving toward a content-pack workflow so items,
NPCs, bosses, arenas, and custom art do not start as scattered edits across
server JSON, client Java, sprite archives, docs, and beta packaging.

## Entry Point

Use:

```bash
scripts/content.sh report
scripts/content.sh validate
scripts/content.sh new item void_relic --name "Void relic"
```

`scripts/content.sh` sets up the Python path for `tools/voidscape-content` and
the older `tools/voidscim-art` package.

## Content Packs

Packs live under `content/custom/<slug>/`:

```text
content.yaml
notes.md
art/
  prompts/
  source/
  working/
  final/
```

`content.yaml` is the manifest for design intent, gameplay inheritance,
selected art, integration targets, and rollout notes. The art folders keep
ChatGPT concepts, source references, working crops, fitted sprites, and final
assets together.

## Existing Art Bridge

The existing item-sprite pipeline remains available through:

```bash
scripts/content.sh voidscim new-icon "a void relic"
scripts/content.sh voidscim fit path/to/cell.png --lanczos
scripts/content.sh voidscim register --png path/to/fit.png --name "Void relic" --description "..." --commit
```

This keeps the proven Void Scimitar/Subscription Card tooling alive while the
general content factory grows around it.

The legacy `.orsc` sprite packer keeps hard-mask transparency by default: pixel
value `0` is transparent and every non-zero pixel is opaque. Use
`tools/voidscim-art/pack.py --preserve-alpha` only for assets with a matching
ARGB-aware client render path; the elemental Blast projectile sprites are the
first such projectile assets.

## UI Asset Bridge

Voidscape HUD skin assets that are already filesystem PNGs should not go
through `Authentic_Sprites.orsc`. The first supported UI flow targets the
top-tab menu icons:

```bash
scripts/content.sh ui spec
scripts/content.sh ui validate
scripts/content.sh ui preview
scripts/content.sh ui ingest-sheet path/to/sheet.png --cell-size 1024 --columns 3
```

The top-tab contract is tracked in `content/ui/voidscape-topbar-icons.json`.
The tool validates 68x68 masters plus baked `20/24/30/32/34/36/40/42` variants,
renders a quick preview contact sheet, and stages concept-sheet crops under
`tools/voidscape-content/out/ui-ingest` unless `--commit` is passed.

For larger menu redesign passes, use the workbench-backed concept loop:

```bash
scripts/content.sh ui capture-panels --out content/ui/menu-redesign/captures/latest.json
scripts/content.sh ui concepts --manifest content/ui/menu-redesign/captures/latest.json
scripts/content.sh ui asset-prompts --panel inventory --concept path/to/approved/concept.png
scripts/content.sh ui asset-prompts --panel hud --concept path/to/approved/hud-concept.png --common-only
scripts/content.sh ui asset-prompts --panel loot --concept path/to/approved/loot-concept.png --no-common
```

`capture-panels` asks the PC workbench to open and screenshot each visible
Voidscape menu panel. `concepts` writes one `gpt-image-2` prompt per screenshot
and can call the Images API with `--call` when `OPENAI_API_KEY` is visible.
`asset-prompts` then turns an approved concept into one green-screen prompt per
individual reusable UI asset. Generate the shared kit once with `--common-only`,
then run panel-specific pieces with `--no-common`; `--only-asset` retries a
single bad extraction. Concepts are review material; only approved, fitted
assets should be committed into `Client_Base/Cache/voidscape/ui/skin`. Before
generating or fitting menu chrome, read
`content/ui/menu-redesign/BLUEPRINT.md`; the current design direction keeps
inventory, friends/ignore, magic, and prayer compact with clear glass/tinted
content surfaces rather than heavy opaque nested frames.

## Validation Rules

`scripts/content.sh validate` checks the rules that most often break custom
OpenRSC content:

- Server item IDs in `ItemDefs.json` plus `ItemDefsCustom.json` must be
  sequential.
- Server NPC IDs in `NpcDefs.json` plus `NpcDefsCustom.json` must be
  sequential.
- New visible custom items need matching client `ItemDef` entries.
- Server NPC count must not exceed the client `NPCDef` table.
- Custom client item sprites must stay inside the item sprite archive block and
  exist in `Client_Base/Cache/video/Authentic_Sprites.orsc`.
- Content packs should include `content.yaml` and the standard art folders.

Warnings are allowed for inherited historical drift. Errors indicate a change
that would likely break beta players.

## ChatGPT Concept Rules

Use `tools/voidscape-content/templates/voidscape-art-brief.md` when asking
ChatGPT for Voidscape art. Generated concepts are source material, not automatic
game assets. For production, extract individual assets, fit them to RSC
constraints, validate the repo, then build/package through the normal scripts.
