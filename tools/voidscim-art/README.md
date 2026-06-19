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

`pack.py` looks for `<input>.json` next to each input PNG and uses it as a
per-image header sidecar. Falls back to `--sidecar PATH` for shared metadata,
or zeros if neither is provided.

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
- N×4 bytes: pixels (N = width×height, each int32 ARGB packed)

ZIP entry name = `str(index)`.

## Cost note

Each `gpt-image-2` call at quality=high costs roughly $0.02–0.05 per variant.
4 variants × 5 iterations ≈ $1. Monitor at https://platform.openai.com/usage.
