"""review <item_key> — render scored comparison sheet for the latest run."""
from __future__ import annotations
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from .paths import ITEMS_DIR
from .registry import load_registry


def _upscale_to_cell(im: Image.Image, cell: int) -> Image.Image:
    w, h = im.size
    scale = max(1, min(cell // max(w, 1), cell // max(h, 1)))
    big = im.resize((w * scale, h * scale), Image.NEAREST)
    bg = Image.new("RGBA", (cell, cell), (40, 40, 40, 255))
    ox = (cell - big.width) // 2
    oy = (cell - big.height) // 2
    bg.paste(big, (ox, oy), big if big.mode == "RGBA" else None)
    return bg


def cmd_review(item_key: str) -> int:
    reg = load_registry()
    if item_key not in reg:
        print(f"error: no registry entry {item_key!r}")
        return 1
    item_dir = ITEMS_DIR / item_key
    attempts_dir = item_dir / "attempts"
    if not attempts_dir.exists() or not any(attempts_dir.iterdir()):
        print(f"error: no attempts for {item_key} (run `generate` first)")
        return 1
    run_dir = sorted(attempts_dir.iterdir())[-1]
    scored_path = run_dir / "scored.json"
    if not scored_path.exists():
        print(f"error: missing {scored_path}")
        return 1
    scored = json.loads(scored_path.read_text())

    cell = 256
    pad = 16
    cap_h = 84
    cols = 1 + len(scored)
    sheet_w = cols * (cell + pad) + pad
    sheet_h = pad + cell + cap_h + pad
    sheet = Image.new("RGBA", (sheet_w, sheet_h), (24, 24, 28, 255))
    draw = ImageDraw.Draw(sheet)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Arial.ttf", 13)
    except OSError:
        font = ImageFont.load_default()

    ref_path = item_dir / "ref.png"
    if ref_path.exists():
        sheet.paste(_upscale_to_cell(Image.open(ref_path).convert("RGBA"), cell), (pad, pad))
        draw.text((pad, pad + cell + 4), "Original", fill=(220, 220, 220, 255), font=font)

    for col, s in enumerate(scored, start=1):
        x = col * (cell + pad) + pad
        variant_path = Path(s["variant_path"])
        if variant_path.exists():
            sheet.paste(_upscale_to_cell(Image.open(variant_path).convert("RGBA"), cell), (x, pad))
        else:
            draw.rectangle([x, pad, x + cell, pad + cell], outline=(120, 60, 60, 255))
            draw.text((x + 8, pad + 8), "missing", fill=(200, 100, 100, 255), font=font)
        status = "PASS" if s["passes"] else "FAIL"
        color = (110, 220, 110, 255) if s["passes"] else (220, 110, 110, 255)
        cap = f"#{s['index']:02d}  {status}\nIoU {s['iou']:.3f}\nhalo {s['halo_residue']*100:.2f}%"
        if s.get("palette_hits"):
            pal = "  ".join(f"{ph['anchor']}={ph['fraction']*100:.0f}%" for ph in s["palette_hits"])
            cap += f"\n{pal}"
        draw.multiline_text((x, pad + cell + 4), cap, fill=color, font=font, spacing=2)

    out_path = run_dir / "_sheet.png"
    sheet.save(out_path)
    print(f"wrote {out_path}")
    if sys.platform == "darwin":
        subprocess.run(["open", str(out_path)])
    return 0
