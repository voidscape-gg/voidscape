"""fit <keyed_png> — auto-fit a chroma-keyed cell into RSC inventory canvas.

Output is a native-size PNG plus a sidecar JSON ready for archive packing
(matches the format `pack.py:encode` consumes). All RSC item sprites share
the same canonical canvas (48×32) defined by the renderer's
`something1`/`something2` header fields; this verb wraps the geometry rules
documented in the discovery report.

Algorithm (fully determined by the renderer's rules):
  1. Crop the input PNG to its opaque bounding box.
  2. Downscale to fit within 48×32, preserving aspect ratio (NEAREST by
     default for hard pixel-art edges; --lanczos for smoother color blending).
  3. Center the bbox in the 48×32 canvas → compute xShift, yShift.
  4. Write <basename>_fit.png + <basename>_fit.png.json sidecar.
  5. Save an 8× NEAREST zoom + a slot mockup (sprite placed in 48×32 canvas
     on RSC's slot grey background) for visual inspection.

Output sidecar matches `pack.py:encode` expected schema:
  {width, height, requiresShift: true, xShift, yShift, something1: 48, something2: 32}
"""
from __future__ import annotations
import json
from pathlib import Path

import numpy as np
from PIL import Image

from .paths import SPRITE_DRAW_W, SPRITE_DRAW_H, SLOT_BG_RGB

CANVAS_W = SPRITE_DRAW_W   # 48
CANVAS_H = SPRITE_DRAW_H   # 32


def _opaque_bbox(img: Image.Image) -> tuple[int, int, int, int] | None:
    """Bounding box of pixels with alpha > 0. None if the image is empty."""
    arr = np.array(img.convert("RGBA"))
    alpha = arr[:, :, 3]
    if not alpha.any():
        return None
    rows = np.any(alpha > 0, axis=1)
    cols = np.any(alpha > 0, axis=0)
    y0, y1 = int(np.argmax(rows)), int(len(rows) - 1 - np.argmax(rows[::-1]))
    x0, x1 = int(np.argmax(cols)), int(len(cols) - 1 - np.argmax(cols[::-1]))
    return (x0, y0, x1 + 1, y1 + 1)


def _fit_dims(src_w: int, src_h: int, max_w: int, max_h: int) -> tuple[int, int]:
    """Largest (W, H) with src aspect ratio that fits in max_w × max_h."""
    ratio = min(max_w / src_w, max_h / src_h)
    new_w = max(1, int(round(src_w * ratio)))
    new_h = max(1, int(round(src_h * ratio)))
    new_w = min(new_w, max_w)
    new_h = min(new_h, max_h)
    return new_w, new_h


def _binary_alpha(img: Image.Image, threshold: int = 128) -> Image.Image:
    arr = np.array(img.convert("RGBA"))
    alpha = arr[:, :, 3]
    arr[:, :, 3] = np.where(alpha >= threshold, 255, 0).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def _slot_mockup(sprite: Image.Image, x_shift: int, y_shift: int) -> Image.Image:
    """48×32 RSC slot grey background with the sprite placed at (xShift, yShift),
    NEAREST-upscaled 8× for inspection."""
    canvas = Image.new("RGBA", (CANVAS_W, CANVAS_H), (*SLOT_BG_RGB, 255))
    canvas.paste(sprite, (x_shift, y_shift), sprite)
    zoom = canvas.resize((CANVAS_W * 8, CANVAS_H * 8), Image.NEAREST)
    return zoom


def cmd_fit(input_path: str, lanczos: bool = False) -> int:
    src_path = Path(input_path).resolve()
    if not src_path.exists():
        print(f"error: input not found: {src_path}")
        return 1

    src = Image.open(src_path).convert("RGBA")
    bbox = _opaque_bbox(src)
    if bbox is None:
        print("error: image is fully transparent — no opaque pixels to fit")
        return 1

    cropped = src.crop(bbox)
    print(f"input:    {src.size[0]}×{src.size[1]} → bbox {cropped.size[0]}×{cropped.size[1]}  "
          f"(at {bbox[0]},{bbox[1]}-{bbox[2]},{bbox[3]})")

    new_w, new_h = _fit_dims(cropped.size[0], cropped.size[1], CANVAS_W, CANVAS_H)
    print(f"target:   {new_w}×{new_h}  (canvas {CANVAS_W}×{CANVAS_H}, "
          f"aspect-preserving)")

    resample = Image.LANCZOS if lanczos else Image.NEAREST
    fitted = cropped.resize((new_w, new_h), resample)
    fitted = _binary_alpha(fitted, threshold=128)

    arr = np.array(fitted)
    opaque_count = int((arr[:, :, 3] > 0).sum())
    print(f"opaque:   {opaque_count}/{new_w * new_h} px after binary-threshold "
          f"({100 * opaque_count / (new_w * new_h):.0f}% coverage)")

    x_shift = (CANVAS_W - new_w) // 2
    y_shift = (CANVAS_H - new_h) // 2
    print(f"shift:    xShift={x_shift}, yShift={y_shift}  (centered in 48×32 canvas)")

    out_dir = src_path.parent
    base = src_path.stem
    fit_png = out_dir / f"{base}_fit.png"
    fit_sidecar = out_dir / f"{base}_fit.png.json"
    fit_zoom = out_dir / f"{base}_fit_8x.png"
    fit_mockup = out_dir / f"{base}_fit_slot.png"

    fitted.save(fit_png)
    fit_sidecar.write_text(json.dumps({
        "width": new_w,
        "height": new_h,
        "requiresShift": True,
        "xShift": x_shift,
        "yShift": y_shift,
        "something1": CANVAS_W,
        "something2": CANVAS_H,
    }, indent=2))

    zoom = fitted.resize((new_w * 8, new_h * 8), Image.NEAREST)
    zoom.save(fit_zoom)

    mockup = _slot_mockup(fitted, x_shift, y_shift)
    mockup.save(fit_mockup)

    print(f"\noutput:")
    print(f"  {fit_png.name}        native-size sprite ready for pack.encode")
    print(f"  {fit_sidecar.name}   header sidecar (paste into pack.py --sidecar)")
    print(f"  {fit_zoom.name}     8× NEAREST zoom of the fitted sprite")
    print(f"  {fit_mockup.name}   48×32 slot mockup (RSC grey BG), 8× zoom")
    return 0
