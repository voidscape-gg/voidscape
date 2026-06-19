# Recipe: add a custom hairstyle

Voidscape currently ships the default/classic head path. Default heads bake hair
and beards into the head animation sprite, and the client recolours their
grayscale hair pixels with the selected `Hair` palette entry. The experimental
modern PNG overlay path remains available as tooling/prototype code under
`Client_Base/Cache/voidscape/hair/style_XX/`, but the live character designer
clamps `hairStyle` to Classic until overlay shapes are approved for every
default head type.

## Art pipeline

Use `tools/hairstyle-art/hairstyle_tool.py` to create a locked bald base and
hair-only reference grid:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-bootstrap \
  --source-style head1 \
  --theme "a compact RSC-authentic swept-back hairstyle" \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig
```

Upload `wig_hair_canvas_grid.png` plus `wig_chatgpt_prompt.txt` to ChatGPT or
another image model. The returned sheet should keep the same 6x3 grid, same
poses, and same cell placement, but it should draw hair only. The locked bald
base owns the face, jaw, ears, neck, and frame geometry.

Import, validate, and preview:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py import-wig-grid \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --grid ~/Downloads/generated-hair-only-grid.png \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair

python3 tools/hairstyle-art/hairstyle_tool.py wig-validate \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair

python3 tools/hairstyle-art/hairstyle_tool.py wig-preview \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair \
  --out tools/hairstyle-art/out/head1_swept_back_wig/wig_player_preview.png
```

For live art iteration, run:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-live \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair
```

The validator should show zero `nonMask` pixels. A small amount of
`detailOverlap` is normal on authentic side/back frames, but large values mean
the hair is covering locked face/detail pixels and should be fixed.

If image generation is fighting the constraints, switch to the manual editor:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-editor \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/manual_hair
```

This opens a local web editor with every locked bald frame, a transparent hair
layer, legal hair-mask swatches, per-frame save, validation, and player-scale
preview. Imported PNG art opens as a placeable stamp first, so crop padding,
scale, and position can be corrected before the pixels are committed to the
current RSC frame. The `hair-dir` it writes can be used directly with
`wig-preview` and `wig-bake`.

Once the wig preview looks right, bake it to normal full-head frames for the
shipped game. Keep transparent modern overlays as prototype-only unless the
Style selector is deliberately re-enabled in a versioned client update.

For a prototype modern overlay style, keep the hair-only PNG frames transparent and
convert the opaque hair pixels to neutral grayscale shading. Install them under
the next `Client_Base/Cache/voidscape/hair/style_XX/` directory with matching
`frame_NN.properties` sidecars. Do not duplicate one folder per colour: the
client applies Void, Frost, Blood, Ember, Gold, Toxic, Moon, Coal, and the
authentic colours at draw time through the shared hair-colour selector.

For a classic baked head, bake it to normal full-head frames:

```bash
python3 tools/hairstyle-art/hairstyle_tool.py wig-bake \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/wig_manifest.json \
  --hair-dir tools/hairstyle-art/out/head1_swept_back_wig/imported_hair \
  --out-dir tools/hairstyle-art/out/head1_swept_back_wig/baked_head

python3 tools/hairstyle-art/hairstyle_tool.py player-preview \
  --manifest tools/hairstyle-art/out/head1_swept_back_wig/baked_head/baked_manifest.json \
  --frames-dir tools/hairstyle-art/out/head1_swept_back_wig/baked_head \
  --archive server/conf/server/data/Authentic_Sprites.orsc \
  --out tools/hairstyle-art/out/head1_swept_back_wig/baked_player_preview.png
```

The preview uses client sprite shifts, base player body/legs, 64x102 player draw
size, recolouring masks, and actual-size strips. Use it as the art gate: if the
silhouette, head size, or attachment point looks wrong here, do not pack the
frames into a live archive.

## Game integration checklist

1. Decide whether this is a shipped classic baked head style or prototype modern overlay art.
2. For a prototype modern overlay, add a new `style_XX` cache folder locally. To ship it, raise `MAX_MODERN_HAIR_STYLE` on client/server, name the style in `mudclient.modernHairStyleLabel()`, re-enable the Style selector, prove it works against the intended head types, and bump the client/server version.
3. For a classic baked head, append a new head `AnimationDef` in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`.
4. For a classic baked head, discover the new runtime `animationDef.number` by instrumenting `mudclient.loadEntitiesAuthentic()` temporarily.
5. For a classic baked head, dry-run pack the imported frames at that runtime base, then commit only after a backup exists.
6. For a classic baked head, add the new server appearance ID to `PlayerAppearance.headSprites`.
7. Add a named `AppearanceId` entry if the id should be readable in server code.
8. Update `server/src/com/openrsc/server/avatargenerator/AvatarGenerator.java` if website/account avatars should render the new hairstyle.
9. Test appearance creation, relog persistence, all walking directions, combat frames, helmets/hats, and multiplayer appearance updates.

No opcode or packet change is needed for adding another head option as long as
the existing appearance-byte flow is used.
