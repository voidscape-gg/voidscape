"""generate <item_key> — produce N HD variants for an item.

Locked config: gpt-image-1.5 + input_fidelity=high + background=transparent.
Silhouette enforcement is OFF in v1 — input_fidelity preserves identity, the
hard mask was over-cropping. Scoring (palette / halo) is informational only.
"""
from __future__ import annotations
import io
import json
from datetime import datetime

import numpy as np
from PIL import Image

from . import score
from .openai_api import call_edits_high_fidelity
from .paths import ITEMS_DIR
from .registry import load_registry


def _dry_run_placeholder(idx: int, w: int = 1024, h: int = 1024) -> bytes:
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if abs((x - y) - (idx * 30)) < 80:
                px[x, y] = (180, 200, 220, 255)
            elif abs((x + y) - (1024 - idx * 30)) < 30:
                px[x, y] = (210, 175, 55, 255)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def _binary_alpha_threshold(rgba: Image.Image, t: int = 128) -> Image.Image:
    """Force alpha to 0 or 255 to kill semi-transparent edge pixels (which read as blur)."""
    arr = np.array(rgba.convert("RGBA"))
    arr[:, :, 3] = np.where(arr[:, :, 3] >= t, 255, 0).astype(np.uint8)
    return Image.fromarray(arr, mode="RGBA")


def cmd_generate(item_key: str, n: int, dry_run: bool = False, confirm_cost: bool = False) -> int:
    reg = load_registry()
    if item_key not in reg:
        print(f"error: registry has no entry {item_key!r}; run `init` first")
        return 1
    entry = reg[item_key]
    item_dir = ITEMS_DIR / item_key

    if n > 4 and not confirm_cost:
        print(f"error: --n={n} > 4 requires --confirm-cost (~${n*0.05:.2f} estimated)")
        return 2

    prompt_path = item_dir / "prompt.txt"
    if not prompt_path.exists():
        print(f"error: prompt missing: {prompt_path}")
        return 1
    prompt = prompt_path.read_text().strip()

    ref_path = item_dir / "ref.png"
    if not ref_path.exists():
        print(f"error: ref missing: {ref_path}")
        return 1

    run_id = datetime.now().strftime("%Y%m%d-%H%M%S")
    run_dir = item_dir / "attempts" / run_id
    run_dir.mkdir(parents=True, exist_ok=True)

    print(f"generating n={n} for {item_key} → {run_dir}")
    print(f"  model=gpt-image-1.5  input_fidelity=high  background=transparent")
    if dry_run:
        raw_pngs = [_dry_run_placeholder(i) for i in range(n)]
    else:
        try:
            raw_pngs = call_edits_high_fidelity(ref_path, prompt, n=n)
        except Exception as e:
            print(f"error: API call failed: {e}")
            return 1

    target_w, target_h = entry.target_size
    scored_results: list[dict] = []

    # The prompt asks gpt-image-1.5 for a 2×2 grid (4 cells per 1024² image).
    # Same API cost as n=1, but we get 4 variants per call. The grid framing
    # also keeps the model in "tiny icon pixel-art" rendering mode rather than
    # "big fantasy weapon" mode.
    GRID_COLS, GRID_ROWS = 2, 2
    cell_w, cell_h = 1024 // GRID_COLS, 1024 // GRID_ROWS

    variant_idx = 0
    for i, raw in enumerate(raw_pngs):
        raw_path = run_dir / f"raw_{i:02d}.png"
        raw_path.write_bytes(raw)
        ai_1024 = Image.open(io.BytesIO(raw)).convert("RGBA")

        for row in range(GRID_ROWS):
            for col in range(GRID_COLS):
                cell = ai_1024.crop((col * cell_w, row * cell_h,
                                     (col + 1) * cell_w, (row + 1) * cell_h))
                cell_path = run_dir / f"cell_{i:02d}_{row}{col}.png"
                cell.save(cell_path)

                downscaled = cell.resize((target_w, target_h), Image.LANCZOS)
                variant = _binary_alpha_threshold(downscaled)
                variant_path = run_dir / f"variant_{variant_idx:02d}.png"
                variant.save(variant_path)

                result = score.score_variant(
                    variant,
                    iou_value=1.0,
                    palette_anchors=entry.palette_anchors,
                    palette_tol=entry.palette_tol,
                    iou_min=0.0,
                )
                result["index"] = variant_idx
                result["variant_path"] = str(variant_path)
                result["raw_path"] = str(raw_path)
                result["cell_path"] = str(cell_path)
                result["grid_cell"] = f"r{row}c{col}"
                scored_results.append(result)

                halo_pct = result["halo_residue"] * 100
                pal_summary = "  ".join(f"{ph['anchor']}={ph['fraction']*100:.0f}%" for ph in result["palette_hits"])
                print(f"  variant {variant_idx:02d} (r{row}c{col}): halo={halo_pct:.2f}%  {pal_summary}")
                variant_idx += 1

    (run_dir / "scored.json").write_text(json.dumps(scored_results, indent=2))
    print(f"\nrun complete: {run_dir}")
    print(f"next: python -m voidscim review {item_key}")
    return 0
