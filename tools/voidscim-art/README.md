# voidscim-art

Sprite-art pipeline for the Void Scimitar (and future custom voidscape weapons).

## Setup

```bash
pip install -r requirements.txt
export OPENAI_API_KEY="sk-..."   # required for live generate.py runs only
```

The key is read **only** from `OPENAI_API_KEY`. Never paste it on the CLI.

## Tools

### `extract_ref.py`
Pull a sprite out of `Authentic_Sprites.orsc` (or any RSC .orsc ZIP) as PNG + sidecar JSON.

```bash
# List entries
python extract_ref.py --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc --list

# Extract one. NOTE: archive index = CLIENT spriteID + 2150 (mudclient.spriteItem),
# where spriteID is the 5th constructor arg in Client_Base/.../EntityHandler.java's
# items.add(new ItemDef(...)) calls. The server JSON `appearanceID` is a DIFFERENT
# field and gives wrong answers. Use `python -m voidscim inspect <idx>` to look up
# the linked items at any archive index. E.g. rune Scimitar has client spriteID=83,
# so its inventory icon is at archive entry 83 + 2150 = 2233:
python extract_ref.py \
  --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc \
  --index 2233 \
  --out out/rune_scim_icon.png
```

The sidecar JSON (`out/rune_scim_icon.png.json`) preserves header metadata
(`requiresShift`, `xShift`, `yShift`, `something1`, `something2`) so the
roundtrip back through `pack.py` is byte-equal.

### `generate.py`
Generate void-themed art via `gpt-image-2` (or run dry-run for plumbing tests).

```bash
# Dry-run: solid-purple placeholder PNGs, no API calls
python generate.py --out-dir out/icon-attempts --variants 4 --target-size 48x32 --dry-run

# Live: real API call
python generate.py \
  --reference out/rune_scim_icon.png \
  --prompt-file prompts/void-scimitar-icon.txt \
  --out-dir out/icon-attempts \
  --variants 4 \
  --target-size 48x32
```

API outputs at 1024×1024 by default; `--target-size` controls the downscaled
output (LANCZOS + palette quantization). Raw originals are saved alongside
as `raw_NN.png` for debugging.

### `pack.py`
Encode PNG(s) into the RSC sprite binary format and insert into an .orsc ZIP.

```bash
# Dry-run: writes <archive>.new.orsc next to the original
python pack.py \
  --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc \
  --start-index 2709 \
  --inputs out/icon-attempts/variant_00.png

# Commit: atomically replace the original (requires .bak to exist)
cp ../../Client_Base/Cache/video/Authentic_Sprites.orsc ../../Client_Base/Cache/video/Authentic_Sprites.orsc.bak
python pack.py \
  --archive ../../Client_Base/Cache/video/Authentic_Sprites.orsc \
  --start-index 2709 \
  --inputs out/icon-attempts/variant_00.png \
  --commit
```

By default `pack.py` keeps the legacy RSC hard-mask behavior: `0` is
transparent, and every non-zero pixel is opaque `0x00RRGGBB`. Use
`--preserve-alpha` only for sprites whose client render path explicitly blends
ARGB pixels, such as the elemental Blast projectiles.

`pack.py` looks for `<input>.json` next to each input PNG and uses it as a
per-image header sidecar. Falls back to `--sidecar PATH` for shared metadata,
or zeros if neither is provided.

### `cowboy_sheet.py`

Build and import the Cowboy Hat's strict 18-frame, 6x3 wearable sheet. Image
generation supplies directional design material; this tool turns the approved
grid into deterministic RSC production sprites by reusing one source view per
three-frame direction, fitting the authentic wizard-hat dimensions, copying
its exact sidecars, applying the approved eight-colour palette, and removing
green-screen or disconnected debris.

Run these commands from the repository root:

```bash
python3 tools/voidscim-art/cowboy_sheet.py reference \
  --frames-dir content/custom/cowboy_hat/art/source/references/wizardshat \
  --out content/custom/cowboy_hat/art/source/references/wizardshat_ai_grid.png

python3 tools/voidscim-art/cowboy_sheet.py import \
  --grid content/custom/cowboy_hat/art/source/generated/cowboy_worn_grid.png \
  --reference-dir content/custom/cowboy_hat/art/source/references/wizardshat \
  --out-dir content/custom/cowboy_hat/art/final/worn \
  --proof content/custom/cowboy_hat/proof/cowboy_worn_frames.png

python3 tools/voidscim-art/cowboy_sheet.py icon \
  --grid content/custom/cowboy_hat/art/source/generated/cowboy_worn_grid.png \
  --out content/custom/cowboy_hat/art/final/icon/cowboy_hat.png

python3 tools/voidscim-art/cowboy_sheet.py validate \
  --reference-dir content/custom/cowboy_hat/art/source/references/wizardshat \
  --frames-dir content/custom/cowboy_hat/art/final/worn
```

### `validate-wielded`

Resolve a wearable's real zero-based client AnimationDef index, its one-based
server `appearanceID`, and the archive base assigned by the runtime loader.
Then decode and inspect every expected frame without modifying the archive:

```bash
PYTHONPATH=tools/voidscim-art python3 -m voidscim validate-wielded \
  --animation cowboyhat \
  --item-id 1609 \
  --archive Client_Base/Cache/video/Authentic_Sprites.orsc \
  --expect-runtime-index 244 \
  --expect-appearance-id 245 \
  --expect-base 1890
```

Before frame art is packed, add `--layout-only` to verify just the numbering
contract. Full validation also checks frame presence/decoding, stored and
logical dimensions, sidecar bounds, nonempty pixels, legacy hard-mask encoding,
and chroma-key residue.

For new player-paperdoll content, `voidscim` is now the low-level sprite codec
and diagnostic layer beneath Appearance Studio. Do not use its single-archive
commit paths as the release transaction: `scripts/content.sh appearance
publish-plan` owns registry identity, both worn archives, client-only inventory
icons, MD5, generated definitions, validation, rollback, and undo as one unit.

## Sprite binary format

See `Client_Base/src/com/openrsc/client/model/Sprite.java`. Big-endian:
- 4 bytes: width (i32)
- 4 bytes: height (i32)
- 1 byte: requiresShift (0 or 1)
- 4 bytes: xShift (i32)
- 4 bytes: yShift (i32)
- 4 bytes: something1 (i32)
- 4 bytes: something2 (i32)
- = 25-byte header
- N×4 bytes: pixels (N = width×height). Legacy sprites store `0x00RRGGBB`
  with `0` as transparent; opt-in alpha sprites store ARGB.

ZIP entry name = `str(index)`.

## Cost note

Each `gpt-image-2` call at quality=high costs roughly $0.02–0.05 per variant.
4 variants × 5 iterations ≈ $1. Monitor at https://platform.openai.com/usage.
