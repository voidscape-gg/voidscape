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
