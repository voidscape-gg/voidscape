# voidscape-content

Production tooling for custom Voidscape content.

This is the supported entry point:

```bash
scripts/content.sh report
scripts/content.sh validate
scripts/content.sh new boss void_behemoth --name "Void Behemoth"
scripts/content.sh voidscim inspect 2233
scripts/content.sh ui validate
scripts/content.sh ui preview
```

The tool is deliberately layered above `tools/voidscim-art`. Existing item
sprite commands still work through `scripts/content.sh voidscim ...`, while new
commands add content-pack manifests, ID reports, and repository validation.

## Current slice

- `new` scaffolds a content pack under `content/custom/<slug>/`.
- `report` prints the next safe item, NPC, and item-sprite allocation points.
- `validate` checks the current repo for the fragile OpenRSC alignment rules:
  sequential server IDs, client/server item and NPC coverage, sprite archive
  availability, and content-pack shape.
- `voidscim` delegates to the existing item-art pipeline so old commands keep
  working while this tool grows.
- `ui` validates and previews filesystem PNG UI skin assets, starting with the
  Voidscape top-tab icons.

## Intended pipeline

```bash
scripts/content.sh new item void_relic --name "Void relic" --like 10
scripts/content.sh voidscim new-icon "a cracked void relic"
scripts/content.sh voidscim fit path/to/chosen_cell.png --lanczos
scripts/content.sh voidscim register --png path/to/fit.png --name "Void relic" --description "..." --like 10 --commit
scripts/content.sh validate
scripts/build.sh
```

## UI asset pipeline

Top HUD/menu icons live as PNGs under `Client_Base/Cache/voidscape/ui/skin`,
not inside `Authentic_Sprites.orsc`. The tracked spec is
`content/ui/voidscape-topbar-icons.json`.

```bash
scripts/content.sh ui spec
scripts/content.sh ui validate
scripts/content.sh ui preview
scripts/content.sh ui ingest-sheet concepts/topbar.png --cell-size 1024 --columns 3 --bg-key "#ff00ff"
```

`ingest-sheet` stages output under `tools/voidscape-content/out/ui-ingest` by
default. Pass `--commit` only when the staged icons look right and should
replace the live HUD skin files.

## UI concept loop

Menu redesign concepts use the PC workbench for exact in-game screenshots, then
the OpenAI Images API directly when `OPENAI_API_KEY` is visible in the shell:

```bash
scripts/content.sh ui capture-panels --out content/ui/menu-redesign/captures/latest.json
scripts/content.sh ui concepts --manifest content/ui/menu-redesign/captures/latest.json
scripts/content.sh ui concepts --manifest content/ui/menu-redesign/captures/latest.json --call
scripts/content.sh ui asset-prompts --panel inventory --concept content/ui/menu-redesign/concepts/<run>/inventory/concept.png
scripts/content.sh ui asset-prompts --panel inventory --concept content/ui/menu-redesign/concepts/<run>/inventory/concept.png --call
scripts/content.sh ui asset-prompts --panel hud --concept content/ui/menu-redesign/concepts/<run>/hud/concept.png --common-only --call
scripts/content.sh ui asset-prompts --panel skills --concept content/ui/menu-redesign/concepts/<run>/skills/concept.png --no-common --call
```

`concepts` prepares one prompt per captured panel and, with `--call`, asks
`gpt-image-2` for full-screenshot concepts that preserve the live geometry.
`asset-prompts` takes an approved concept and prepares one green-screen prompt
per reusable frame/button/row/scrollbar asset so the assets can be reviewed,
cut out, and implemented one at a time. Use `--common-only` for the shared
frame/tab/scrollbar kit and `--no-common` for panel-specific pieces; use
`--only-asset` to retry one bad extraction without rerunning the whole panel.

NPCs, bosses, arenas, and textures use the same content-pack shape first, then
gain dedicated registration commands as those flows become safe enough to
automate.
