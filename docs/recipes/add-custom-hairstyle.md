# Recipe: add a custom hairstyle

Voidscape currently ships the default/classic head path. Default heads bake hair
and beards into the head animation sprite, and the client recolours their
grayscale hair pixels with the selected `Hair` palette entry. Paperdoll V2 now
has a guarded PC evaluation catalog with six reviewed hairstyles, but production
still clamps `hairStyle` to Classic. Never use the evaluation path with a
production or reusable player database.

## Preferred 2× workflow (approval-gated)

For new premium/rare hairstyles, use the Paperdoll V2 workspace instead of
forcing the design into a 16x18 legacy head. Authoring, compilation, preview,
validation, deterministic packing, and staff-only PC evaluation are available.
Production publication is not.

```bash
scripts/appearance-v2.sh init rare-spikes tmp/appearance-studio/rare-spikes
scripts/appearance-v2.sh editor tmp/appearance-studio/rare-spikes
scripts/appearance-v2.sh validate tmp/appearance-studio/rare-spikes
scripts/appearance-v2.sh build tmp/appearance-studio/rare-spikes
scripts/appearance-v2.sh validate-pack tmp/appearance-studio/rare-spikes/build/Paperdoll_V2.orsc
```

Author only transparent hair pixels over the locked native head. Preserve the
face, ears, neck, and base head exactly; use the crown/scalp/face-clearance masks
and individual actual-size panels as the geometry gate. Six masters cover
north, north-west, west, south-west, south, and combat-west; the compiler owns
three-phase propagation and east-facing mirrors. The current rare-spike fixture
demonstrates forehead locks and sideburns while keeping each master one
4-connected scalp-attached component.

AI-generated material is concept or import-stamp input only. It never defines
final frame geometry, propagation, attachment, sidecars, or approval. Commit
only pixels placed on the locked master canvases and validated/reviewed through
the deterministic compiler.

Use a disposable workspace until the six masters pass individual-panel human
review. A content-owned style consists of six master PNGs under
`content/appearance/v2/hairstyles/<style>/masters/`, a digest-bound `art.json`,
and one permanent entry in `content/appearance/v2/catalog.yaml`. Never reuse a
selector ID. The catalog loader verifies all master digests and both base-profile
routes before it can materialize a collection workspace:

```bash
scripts/appearance-v2.sh validate-template
scripts/appearance-v2.sh validate-catalog
scripts/appearance-v2.sh collection tmp/appearance-studio/my-collection
scripts/appearance-v2.sh validate tmp/appearance-studio/my-collection
scripts/appearance-v2.sh build tmp/appearance-studio/my-collection
scripts/appearance-v2.sh validate-pack tmp/appearance-studio/my-collection/build/Paperdoll_V2.orsc
scripts/appearance-v2.sh test
```

Build the collection twice in clean workspaces and compare the pack, selector
registry, compatibility manifest/properties, and report hashes. Then compare the
Python output with the Java Workbench oracle and inspect every selector/profile/
pose panel individually. A passing automated report is not an art approval.

Do not copy the pack into a production cache. The catalog build emits build-only
1x overlay fallbacks and 64x102 QA thumbnails under its workspace, but it never
writes either to the client cache or `server/avatars/`. The current runtime
accepts only an explicit pack below repository `tmp/`. Native hats/equipment
occlusion, Android, TeaVM, live `AvatarGenerator`, production packaging, and
saved-selector migration remain later approval gates.

### Staff-only runtime evaluation gate

The only approved live use is a loopback Workbench client connected to a
disposable SQLite `*_qa` server. The server must have an exact evaluation maximum
of `6`, `-Dvoidscape.paperdollV2.evaluationServer=true`, creation mode `0`,
production command lockdown off, and `avatar_generator:false`. The client must
load the explicit pack, its sibling selector registry, the compatibility
properties, and the designer-evaluation flag. The player must be a server
developer.

Only head `8`, male body `2` or female body `5`, legs `3`, and selectors `1..6`
are eligible. Any hat forces the complete legacy player and suppresses the 1x
compatibility hairstyle. Plate/head-slot equipment that replaces the required
base blocks the designer. Always test accept, disconnect-save, fresh-login
hydration, both genders, a remote observer, ordinary and Cowboy hats, and
forced/missing/rejected-pack fallback.

Rollback is to remove the client/server evaluation flags and discard the QA
database. No archive, MD5, schema, or protocol rollback is required because
none is changed by this workflow.

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

The checklist below documents the legacy manual integration path. New 2x
hairstyles should instead enter `rsc-player-2x-v1` and the content-owned V2
catalog. The R9 collection is QA-ready for guarded PC evaluation, not approved
for a production apply. Legacy baked heads and Looks remain on the separate
`rsc-player-v1` compiler/publisher path.

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

For a V2 production proposal, add the additional release gates omitted by the
legacy checklist: deterministic A/B collection builds, strict pack/adversarial
tests, selector/profile resolution, complete active and fallback matrices,
allocation and performance evidence, disposable-QA persistence/multiplayer
tests, avatar parity, target-platform parity, version/cohort policy, saved-style
downgrade policy, and an exercised rollback. Do not raise production
`MAX_HAIR_STYLE` or the server evaluation maximum as a shortcut.
