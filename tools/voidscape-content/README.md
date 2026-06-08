# voidscape-content

Production tooling for custom Voidscape content.

This is the supported entry point:

```bash
scripts/content.sh report
scripts/content.sh validate
scripts/content.sh new boss void_behemoth --name "Void Behemoth"
scripts/content.sh voidscim inspect 2233
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

## Intended pipeline

```bash
scripts/content.sh new item void_relic --name "Void relic" --like 10
scripts/content.sh voidscim new-icon "a cracked void relic"
scripts/content.sh voidscim fit path/to/chosen_cell.png --lanczos
scripts/content.sh voidscim register --png path/to/fit.png --name "Void relic" --description "..." --like 10 --commit
scripts/content.sh validate
scripts/build.sh
```

NPCs, bosses, arenas, and textures use the same content-pack shape first, then
gain dedicated registration commands as those flows become safe enough to
automate.
