# Cowboy Hat Source

- Directional concept and 18-cell source grid: generated with OpenAI's built-in
  image generation tool and approved during the feature review.
- Prompts: `art/prompts/item-concept.txt` and `art/prompts/worn-sheet.txt`.
- Preserved generated sources:
  - `art/source/generated/cowboy_directional_concept.png`
  - `art/source/generated/cowboy_worn_grid.png`
- Authentic scale/perspective references:
  - wizard hat archive entries `594..611`
  - medium helm archive entries `540..557`
  - partyhat archive entries `1377..1394`
  - bald `head4` archive entries `189..206` for composite fit checks
- Production frame geometry and sidecars derive from the authentic wizard hat.
  `tools/voidscim-art/cowboy_sheet.py` maps one approved source view across each
  three-frame movement direction so generated silhouette drift cannot flicker
  during walking, then green-keys, BOX-downscales, hard-masks, palette-quantizes,
  and retains only the main connected component.
- The resulting 18 worn sprites and 48x32 icon use only the approved leather
  palette recorded in `art/final/worn/import_manifest.json`; generated pixels
  are never packed directly.
- Player-facing render evidence is preserved in `proof/`, including an all-frame
  Workbench close-up sheet produced by `POST /scenario/cowboy-hat-frames`.
