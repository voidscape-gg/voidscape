# Voidscape Hairstyle Art Tool

This is a small local pipeline for AI-assisted RSC hairstyle sprites. It does
not change the client, server, database, protocol, or sprite archive unless you
explicitly run the `pack --commit` command.

## Why this exists

Classic RSC player hair is not a separate runtime overlay. A default hairstyle
is a full head animation block in `Client_Base/Cache/video/Authentic_Sprites.orsc`.
Voidscape currently ships this classic path: every default head/beard shape uses
the original grayscale hair mask and the shared `Hair` palette. The modern
transparent PNG overlay path in `Client_Base/Cache/voidscape/hair/style_XX/`
remains available for prototypes, but the live character designer clamps
`hairStyle` to Classic until overlay shapes are approved for every intended head
type.

Each selectable head style currently uses 18 frames:

- frames `00..14`: normal direction and walking poses
- frames `15..17`: combat-facing poses

The client recolors sprite pixels at draw time:

- grayscale pixels become hair/clothing color
- pixels where `R=255` and `G=B` become skin color
- pixel value `0` is transparent

The safest authoring workflow is a local "wig compositor": lock a vanilla bald
head as the immutable base, edit only transparent hair pixels, preview the result
on the actual player paperdoll, then bake the hair back into full head frames for
a shipped classic head option. Neutral shaded PNG overlays can still be exported
for prototype work. This prevents image models from shrinking the face, moving
the neck, or adding unrecolorable anti-aliased pixels.

## Wig compositor workflow

Start by extracting a locked bald base plus a hair-only canvas from an existing
vanilla hairstyle:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-bootstrap \
  --source-style head1 \
  --theme "a compact RSC-authentic swept-back hairstyle" \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig
```

Outputs:

- `locked_bald_base/frame_00.png` through `frame_17.png`
- `hair_canvas/frame_00.png` through `frame_17.png`
- `wig_hair_canvas_grid.png` for ChatGPT/image generation
- `wig_labeled_reference_grid.png` for human inspection
- `wig_manifest.json` with exact slicing and metadata
- `wig_chatgpt_prompt.txt`

Upload `wig_hair_canvas_grid.png` plus `wig_chatgpt_prompt.txt` to the image
model. The returned sheet should contain hair only, on pure green or transparent
background. Import it with:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py import-wig-grid \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --grid ~/Downloads/generated-hair-only-grid.png \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair
```

Validate and preview before baking:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-validate \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair

python3 tools/hairstyle-art/hairstyle_tool.py wig-preview \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair \
  --out tools/hairstyle-art/out/head1_swept_back_wig/wig_player_preview.png
```

For pixel iteration, run the live preview server and open its printed URL:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-live \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair
```

The browser preview re-renders from disk every second. Edit a hair frame PNG,
save, and refresh/auto-refresh shows the result without packing an archive or
restarting the game.

For hand-authored hair, use the manual editor instead:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-editor \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/manual_hair
```

The editor creates missing transparent hair frames, shows every locked bald
frame, lets you paint/erase/pick the three legal hair-mask shades, nudge or copy
frames, import and place a PNG into the current frame, save directly to disk,
validate the mask pixels, and refresh the player-scale preview. PNG import trims
faint transparent padding, then lets you fit, scale, nudge, apply, or cancel
before committing pixels to the tiny RSC frame.

When the preview is good, bake the hair onto the locked base:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-bake \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig/baked_head
```

The baked output is a normal full-head frame set. Use its `baked_manifest.json`
with `player-preview` and `pack`.

## Bootstrap a reference grid

This older whole-head workflow remains available for reference, but the wig
compositor above should be the default for new Voidscape hairstyles.

```bash
python3 tools/hairstyle-art/hairstyle_tool.py bootstrap \
  --style head1 \
  --theme "a messy swept-back adventurer hairstyle, RSC-authentic and compact" \
  --out-dir tools/hairstyle-art/out/head1_swept_back
```

Outputs:

- `frames/frame_00.png` through `frames/frame_17.png`
- `head1_ai_grid.png` for ChatGPT/image generation
- `head1_labeled_reference_grid.png` for human inspection
- `head1_manifest.json` with exact slicing and metadata
- `head1_chatgpt_prompt.txt`

## ChatGPT / image model loop

Upload `head1_ai_grid.png` to ChatGPT and paste the generated prompt text.
Ask for a single returned PNG sprite sheet. Save the returned image locally,
then import it:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py import-grid \
  --manifest tools/hairstyle-art/out/head1_swept_back/head1_manifest.json \
  --grid ~/Downloads/generated-hairstyle-grid.png \
  --out-dir tools/hairstyle-art/out/head1_swept_back/imported
```

By default, import normalizes pixels into RSC-safe masks. Use
`--no-normalize` only when you are intentionally testing raw generated pixels.

## Validate and preview

```bash
python3 tools/hairstyle-art/hairstyle_tool.py validate \
  --manifest tools/hairstyle-art/out/head1_swept_back/head1_manifest.json \
  --frames-dir tools/hairstyle-art/out/head1_swept_back/imported

python3 tools/hairstyle-art/hairstyle_tool.py preview \
  --manifest tools/hairstyle-art/out/head1_swept_back/head1_manifest.json \
  --frames-dir tools/hairstyle-art/out/head1_swept_back/imported \
  --out tools/hairstyle-art/out/head1_swept_back/preview.png
```

Validation should show low or zero `full` pixel counts. A few dark detail pixels
are normal on authentic heads; large full-color counts mean the sprite will not
recolor correctly in game.

## Dry-run pack

Use a discovered runtime archive slot. For a future appended head AnimationDef,
verify the slot in the client before committing.

```bash
python3 tools/hairstyle-art/hairstyle_tool.py pack \
  --manifest tools/hairstyle-art/out/head1_swept_back/head1_manifest.json \
  --frames-dir tools/hairstyle-art/out/head1_swept_back/imported \
  --start-index 1809
```

Dry-run writes an `.orsc` file into the frames directory. It does not modify the
real archive. `--commit` requires a `.bak` next to the real archive.

## Known constraints

This tool remains useful for locked-base extraction and historical hairstyle
experiments. New production hair/facial-hair work should be imported as
transparent layers into Appearance Studio's `rsc-player-v1` template, where
master propagation, anchors, palette/overlap checks, registry allocation,
transactional publishing, and Workbench QA share one manifest.

- Adding a hairstyle to character creation still requires client and server code
  changes after the art is ready.
- Server validation currently accepts only appearance IDs `1, 4, 6, 7, 8`.
- The avatar generator has a mirrored animation list and should be updated when
  a new committed hairstyle becomes real game content.
- Old launchers/clients will not know about new packed sprite frames, so a real
  hairstyle release should be paired with normal launcher/client-version rollout.
