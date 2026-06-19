#!/usr/bin/env python3
"""Build the Void Colossus NPC animation frames from green-screen concept sheets.

The RSC entity renderer expects one animation block of 18 sprites:

  1755..1769: five directional views, three frames each
  1770..1772: combat side-view attack frames

The generated source sheets are larger presentation images. This script slices
the isolated figures, chroma-keys #00ff00, normalizes every frame onto the
105x200 legacy sprite canvas, writes pack.py sidecars, and emits a contact
sheet for visual QA before packing.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps


CANVAS_W = 105
CANVAS_H = 200
START_INDEX = 1755

# Preserve the current Colossus logical anchor. The renderer scales by
# something1/something2, so changing it makes camera sizing drift too.
SIDECAR = {
    "width": CANVAS_W,
    "height": CANVAS_H,
    "requiresShift": True,
    "xShift": 24,
    "yShift": 14,
    "something1": 153,
    "something2": 214,
}


def is_green(pixel: tuple[int, int, int]) -> bool:
    r, g, b = pixel
    return g >= 170 and r <= 110 and b <= 110 and g > r * 1.35 and g > b * 1.35


def key_to_alpha(img: Image.Image) -> Image.Image:
    rgb = img.convert("RGB")
    out = Image.new("RGBA", rgb.size, (0, 0, 0, 0))
    src = rgb.load()
    dst = out.load()
    for y in range(rgb.height):
        for x in range(rgb.width):
            r, g, b = src[x, y]
            if is_green((r, g, b)):
                continue
            # Soft despill on near-green edge pixels.
            if g > r and g > b:
                g = min(g, max(r, b) + 28)
            dst[x, y] = (r, g, b, 255)

    alpha = out.getchannel("A")
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3))
    out.putalpha(alpha)
    return out


def column_runs(img: Image.Image, expected: int) -> list[tuple[int, int]]:
    rgb = img.convert("RGB")
    px = rgb.load()
    active: list[int] = []
    for x in range(rgb.width):
        count = 0
        for y in range(rgb.height):
            if not is_green(px[x, y]):
                count += 1
        if count > 12:
            active.append(x)

    runs: list[tuple[int, int]] = []
    if active:
        start = prev = active[0]
        for x in active[1:]:
            if x - prev > 16:
                if prev - start > 24:
                    runs.append((start, prev + 1))
                start = x
            prev = x
        if prev - start > 24:
            runs.append((start, prev + 1))

    if len(runs) != expected:
        raise SystemExit(f"expected {expected} figure runs, found {len(runs)}: {runs}")
    return runs


def crop_figures(path: Path, expected: int) -> list[Image.Image]:
    src = Image.open(path).convert("RGB")
    figures: list[Image.Image] = []
    for x0, x1 in column_runs(src, expected):
        region = src.crop((max(0, x0 - 8), 0, min(src.width, x1 + 8), src.height))
        keyed = key_to_alpha(region)
        bbox = keyed.getbbox()
        if bbox is None:
            raise SystemExit(f"empty figure in {path}")
        figures.append(keyed.crop(bbox))
    return figures


def fit_to_canvas(fig: Image.Image, *, fill_ratio: float = 0.94, x_bias: int = 0) -> Image.Image:
    fig = fig.convert("RGBA")
    bbox = fig.getbbox()
    if bbox is None:
        return Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    fig = fig.crop(bbox)

    max_w = int(CANVAS_W * fill_ratio)
    max_h = int(CANVAS_H * fill_ratio)
    scale = min(max_w / fig.width, max_h / fig.height)
    new_w = max(1, round(fig.width * scale))
    new_h = max(1, round(fig.height * scale))
    fig = fig.resize((new_w, new_h), Image.Resampling.LANCZOS)

    out = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    x = (CANVAS_W - new_w) // 2 + x_bias
    # RSC feet read better when the boss sits a couple pixels above the bottom.
    y = CANVAS_H - new_h - 3
    out.alpha_composite(fig, (max(-new_w + 1, min(CANVAS_W - 1, x)), y))
    return out


def paste_cropped(dest: Image.Image, src: Image.Image, x: int, y: int) -> None:
    sx = max(0, -x)
    sy = max(0, -y)
    dx = max(0, x)
    dy = max(0, y)
    w = min(src.width - sx, dest.width - dx)
    h = min(src.height - sy, dest.height - dy)
    if w > 0 and h > 0:
        dest.alpha_composite(src.crop((sx, sy, sx + w, sy + h)), (dx, dy))


def fit_combat_to_canvas(fig: Image.Image, *, min_body_h: int, x_bias: int) -> Image.Image:
    fig = fig.convert("RGBA")
    bbox = fig.getbbox()
    if bbox is None:
        return Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    fig = fig.crop(bbox)

    scale = min((CANVAS_W * 0.98) / fig.width, (CANVAS_H * 0.97) / fig.height)
    if fig.height * scale < min_body_h:
        scale = min_body_h / fig.height

    new_w = max(1, round(fig.width * scale))
    new_h = max(1, round(fig.height * scale))
    fig = fig.resize((new_w, new_h), Image.Resampling.LANCZOS)

    out = Image.new("RGBA", (CANVAS_W, CANVAS_H), (0, 0, 0, 0))
    x = (CANVAS_W - new_w) // 2 + x_bias
    y = CANVAS_H - new_h - 3
    paste_cropped(out, fig, x, y)
    return out


def anchored_variant(base: Image.Image, *, dx: int = 0, body_dy: int = 0) -> Image.Image:
    """Make a tiny heavy-idle/walk variant without rubber-warping limbs."""
    if dx == 0 and body_dy == 0:
        return base.copy()
    bbox = base.getbbox()
    if bbox is None:
        return base.copy()
    crop = base.crop(bbox)
    out = Image.new("RGBA", base.size, (0, 0, 0, 0))
    # Keep the feet nearly anchored; only a one-pixel mass shift gives life.
    out.alpha_composite(crop, (bbox[0] + dx, bbox[1] + body_dy))
    return out


def combat_variants(figures: list[Image.Image]) -> list[Image.Image]:
    frames: list[Image.Image] = []
    # Combat_A is drawn unmirrored for the Colossus when it is on the left of a
    # combat scene, so source art needs to face screen-right. ChatGPT produced a
    # clean left-facing strip; flip it here and let Combat_B mirror it back.
    for i, fig in enumerate(figures):
        flipped = ImageOps.mirror(fig.convert("RGBA"))
        frames.append(fit_combat_to_canvas(flipped, min_body_h=120, x_bias=[0, -8, -16][i]))
    return frames


def make_contact(frames: list[Image.Image], out_path: Path) -> None:
    cols = 6
    cell_w = CANVAS_W + 24
    cell_h = CANVAS_H + 28
    rows = (len(frames) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (34, 34, 40, 255))
    draw = ImageDraw.Draw(sheet)
    for i, frame in enumerate(frames):
        cx = (i % cols) * cell_w
        cy = (i // cols) * cell_h
        draw.rectangle((cx + 11, cy + 17, cx + 11 + CANVAS_W, cy + 17 + CANVAS_H), outline=(90, 90, 100, 255))
        sheet.alpha_composite(frame, (cx + 12, cy + 18))
        draw.text((cx + 12, cy + 3), f"{START_INDEX + i}", fill=(245, 230, 160, 255))
    sheet.save(out_path)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--turnaround", type=Path, default=Path("tmp/colossus-repair/chatgpt/generated-1.png"))
    ap.add_argument("--combat", type=Path, default=Path("tmp/colossus-repair/chatgpt/generated-2.png"))
    ap.add_argument("--out", type=Path, default=Path("tmp/colossus-repair/final"))
    args = ap.parse_args()

    args.out.mkdir(parents=True, exist_ok=True)
    views = crop_figures(args.turnaround, 5)
    attacks = crop_figures(args.combat, 3)

    frames: list[Image.Image] = []
    for view in views:
        base = fit_to_canvas(view)
        frames.extend([
            anchored_variant(base, dx=0, body_dy=0),
            anchored_variant(base, dx=-1, body_dy=1),
            anchored_variant(base, dx=1, body_dy=0),
        ])
    frames.extend(combat_variants(attacks))

    if len(frames) != 18:
        raise SystemExit(f"expected 18 frames, built {len(frames)}")

    for offset, frame in enumerate(frames):
        path = args.out / f"f{START_INDEX + offset}.png"
        frame.save(path)
        path.with_suffix(path.suffix + ".json").write_text(json.dumps(SIDECAR, indent=2) + "\n")

    make_contact(frames, args.out / "contact.png")
    print(f"wrote {len(frames)} frames to {args.out}")
    print(f"contact sheet: {args.out / 'contact.png'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
