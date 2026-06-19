"""new-icon <description> — generate a brand-new RSC item icon in coherent style.

Slice 0 scope: produce 4 candidate icon variants for a described item; nothing
more. No item-id allocation, no archive packing, no server/client registration.

Style coherence approach:
  - Build a 3×3 montage at 1024×1024 with 8 curated authentic RSC item icons
    (NEAREST-upscaled with integer scaling for true pixel-art look) on a
    magenta (#FF00FF) background. The center cell is empty + marked
    "DRAW HERE" with a red dashed border.
  - Send to /v1/images/edits with input_fidelity=high so the model preserves
    the reference cells and only fills the empty one.
  - Crop the center cell from each variant; save both the magenta-BG version
    and a chroma-keyed transparent version. Build a side-by-side preview.

Output:
  tools/voidscim-art/new_items/<slug>/
    reference.png          montage sent to API
    prompt.txt             text prompt
    raw_1..N.png           full 1024² returns
    cell_1..N.png          cropped center cell, magenta BG
    cell_1..N_keyed.png    cropped + chroma-keyed transparent
    preview.png            side-by-side of keyed cells at 4× zoom
"""
from __future__ import annotations
import re
import sys
import time
import zipfile
from dataclasses import dataclass

import numpy as np
from PIL import Image, ImageDraw

from . import itemdefs
from .openai_api import call_edits_high_fidelity
from .paths import ARCHIVE_PATH, SPRITE_ITEM, TOOL_DIR
from .sprite_io import decode

NEW_ITEMS_DIR = TOOL_DIR / "new_items"

# Curated mix of authentic RSC item icons spanning categories. Names matched
# case-insensitively against client EntityHandler.java; verify with
# `python -m voidscim inspect <archive_index>`.
CURATED_REF_NAMES = [
    "Coins",            # stackable currency
    "Bronze long sword", # melee weapon
    "Wooden shield",    # armor
    "Bronze pickaxe",   # tool
    "Bread",            # food, basic
    "Lobster",          # food, advanced
    "Logs",             # raw resource
    "Bones",            # drop / prayer item
]

GRID_N = 3                 # 3×3 cells
CANVAS_PX = 1024           # API requires square 1024×1024
CELL_PX = CANVAS_PX // GRID_N  # 341
DRAW_CELL = 4              # row-major (row=1, col=1) center
MAGENTA_RGBA = (0xFF, 0x00, 0xFF, 0xFF)


def _slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "item"


@dataclass
class Ref:
    name: str
    item_id: int
    sprite_id: int
    archive_index: int
    img: Image.Image


def _resolve_refs(names: list[str]) -> list[Ref]:
    items = itemdefs.load_all_items()
    by_name = {it["name"].lower(): it for it in items}
    out: list[Ref] = []
    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
        for name in names:
            it = by_name.get(name.lower())
            if not it:
                print(f"warn: ref item not found in EntityHandler: {name!r}", file=sys.stderr)
                continue
            archive_idx = it["sprite_id"] + SPRITE_ITEM
            try:
                data = zf.read(str(archive_idx))
            except KeyError:
                print(f"warn: archive entry {archive_idx} missing for {name!r}", file=sys.stderr)
                continue
            img, _ = decode(data)
            out.append(Ref(it["name"], it["id"], it["sprite_id"], archive_idx, img))
    return out


def _paste_ref_into_cell(canvas: Image.Image, ref: Ref, cell_idx: int) -> None:
    row, col = divmod(cell_idx, GRID_N)
    x0, y0 = col * CELL_PX, row * CELL_PX

    margin = 32
    target = CELL_PX - 2 * margin
    int_scale = max(1, min(target // ref.img.width, target // ref.img.height))
    new_w = ref.img.width * int_scale
    new_h = ref.img.height * int_scale
    scaled = ref.img.resize((new_w, new_h), Image.NEAREST)

    cell = Image.new("RGBA", (CELL_PX, CELL_PX), MAGENTA_RGBA)
    ox = (CELL_PX - new_w) // 2
    oy = (CELL_PX - new_h) // 2
    cell.paste(scaled, (ox, oy), scaled)
    canvas.paste(cell, (x0, y0))


def _draw_empty_cell(canvas: Image.Image, cell_idx: int) -> None:
    row, col = divmod(cell_idx, GRID_N)
    x0, y0 = col * CELL_PX, row * CELL_PX
    d = ImageDraw.Draw(canvas)

    inset = 16
    rect = [x0 + inset, y0 + inset, x0 + CELL_PX - inset, y0 + CELL_PX - inset]
    for k in range(6):
        d.rectangle([rect[0] + k, rect[1] + k, rect[2] - k, rect[3] - k],
                    outline=(255, 0, 0, 255), width=1)
    label = "DRAW HERE"
    tx = x0 + CELL_PX // 2 - 36
    ty = y0 + CELL_PX // 2 - 6
    d.text((tx, ty), label, fill=(255, 0, 0, 255))


def _build_montage(refs: list[Ref]) -> Image.Image:
    canvas = Image.new("RGBA", (CANVAS_PX, CANVAS_PX), MAGENTA_RGBA)
    ref_iter = iter(refs)
    for cell_idx in range(GRID_N * GRID_N):
        if cell_idx == DRAW_CELL:
            _draw_empty_cell(canvas, cell_idx)
            continue
        try:
            ref = next(ref_iter)
        except StopIteration:
            break
        _paste_ref_into_cell(canvas, ref, cell_idx)
    return canvas


def _build_prompt(description: str) -> str:
    return (
        "This is a 3×3 grid of RuneScape Classic inventory icons on a magenta "
        "(#FF00FF) background. The CENTER cell, marked with a red dashed border "
        f"and the words 'DRAW HERE', is empty.\n\n"
        f"Fill ONLY the center cell with a new item icon: {description}.\n\n"
        "Match the surrounding icons in:\n"
        "- chunky pixel-art rendering, hard edges, no soft anti-aliasing or painterly blur\n"
        "- silhouette scale: occupy roughly the same visible area as the neighbors, "
        "edge-to-edge within the cell\n"
        "- outline weight, palette saturation, lighting direction, and shading style\n"
        "- background: solid magenta (#FF00FF) inside the cell\n\n"
        "Do NOT modify any of the surrounding 8 cells. Do NOT add labels or text. "
        "Do NOT add the red dashed border in the output. The new item must clearly "
        "read as belonging to the same icon set as the references."
    )


def _crop_cell(montage: Image.Image, cell_idx: int) -> Image.Image:
    row, col = divmod(cell_idx, GRID_N)
    box = (col * CELL_PX, row * CELL_PX,
           (col + 1) * CELL_PX, (row + 1) * CELL_PX)
    return montage.crop(box)


def _chroma_key_magenta(img: Image.Image) -> Image.Image:
    """Magenta chroma key with despill.

    Three stages on a single pass:
      1. KILL    — strong magenta (R>180, B>180, G<100): alpha → 0
      2. TAPER   — anti-aliased halo (high R+B, moderate G): fade alpha
                   linearly with the spill score, so soft edges blend out
                   instead of leaving a hard fringe
      3. DESPILL — residual magenta tint on still-opaque pixels: pull R and
                   B halfway toward G to neutralize the cast. Gated to only
                   trigger on pixels that are clearly magenta-leaning
                   (R>150 AND B>150), which spares legitimate purple/blue
                   art whose R or B is moderate.
    """
    arr = np.array(img.convert("RGBA"))
    r = arr[:, :, 0].astype(np.int16)
    g = arr[:, :, 1].astype(np.int16)
    b = arr[:, :, 2].astype(np.int16)
    a = arr[:, :, 3].copy()

    rb_min = np.minimum(r, b)
    spill = np.maximum(0, rb_min - g)

    kill = (r > 180) & (b > 180) & (g < 100)

    taper = (~kill) & (spill > 50) & (r > 130) & (b > 130)
    fade = np.clip(1.0 - (spill[taper] - 50) / 100.0, 0.0, 1.0)
    a[taper] = (a[taper].astype(np.float32) * fade).astype(np.uint8)
    a[kill] = 0

    despill = (~kill) & (spill > 40) & (r > 150) & (b > 150) & (a > 0)
    if np.any(despill):
        d = (spill[despill] // 2).astype(np.int16)
        r[despill] = np.clip(r[despill] - d, 0, 255)
        b[despill] = np.clip(b[despill] - d, 0, 255)

    out = np.stack([
        r.astype(np.uint8),
        g.astype(np.uint8),
        b.astype(np.uint8),
        a,
    ], axis=-1)
    return Image.fromarray(out, "RGBA")


def _build_preview(keyed_cells: list[Image.Image]) -> Image.Image:
    gap = 16
    bg = (40, 40, 40, 255)
    w = sum(c.width for c in keyed_cells) + gap * (len(keyed_cells) + 1)
    h = max(c.height for c in keyed_cells) + 2 * gap
    canvas = Image.new("RGBA", (w, h), bg)
    x = gap
    for c in keyed_cells:
        canvas.paste(c, (x, gap), c)
        x += c.width + gap
    return canvas


def cmd_new_icon(description: str, n: int = 4, dry_run: bool = False) -> int:
    if not ARCHIVE_PATH.exists():
        print(f"error: archive not found: {ARCHIVE_PATH}")
        return 1

    slug = _slug(description)
    out_dir = NEW_ITEMS_DIR / slug
    out_dir.mkdir(parents=True, exist_ok=True)

    refs = _resolve_refs(CURATED_REF_NAMES)
    if len(refs) < 4:
        print(f"error: only {len(refs)} reference items resolved; need at least 4 for a coherent style anchor")
        return 1

    montage = _build_montage(refs)
    ref_path = out_dir / "reference.png"
    montage.save(ref_path)
    print(f"reference montage: {ref_path}")
    print(f"  cells: {len(refs)} refs + 1 DRAW HERE")
    for r in refs:
        print(f"    {r.name!r}  (id={r.item_id}, sprite={r.sprite_id}, idx={r.archive_index})")

    prompt = _build_prompt(description)
    (out_dir / "prompt.txt").write_text(prompt)

    if dry_run:
        print("\ndry-run: reference.png + prompt.txt saved, no API call")
        return 0

    print(f"\ncalling gpt-image-1.5 (n={n}) ...")
    t0 = time.time()
    images = call_edits_high_fidelity(
        reference=ref_path,
        prompt=prompt,
        n=n,
        background="opaque",   # we want magenta preserved, not auto-transparent
    )
    print(f"  → {len(images)} image(s) in {time.time() - t0:.1f}s")

    keyed_cells: list[Image.Image] = []
    for i, b in enumerate(images, start=1):
        raw_path = out_dir / f"raw_{i}.png"
        raw_path.write_bytes(b)
        full = Image.open(raw_path).convert("RGBA")

        cell = _crop_cell(full, DRAW_CELL)
        cell.save(out_dir / f"cell_{i}.png")

        keyed = _chroma_key_magenta(cell)
        keyed.save(out_dir / f"cell_{i}_keyed.png")
        keyed_cells.append(keyed)

    preview = _build_preview(keyed_cells)
    preview.save(out_dir / "preview.png")

    print(f"\noutput: {out_dir}")
    print(f"  reference.png       montage sent to API")
    print(f"  prompt.txt          full text prompt")
    print(f"  raw_1..{n}.png         full 1024² API returns")
    print(f"  cell_1..{n}.png        cropped center cells (magenta BG)")
    print(f"  cell_1..{n}_keyed.png  cropped + chroma-keyed (transparent)")
    print(f"  preview.png         side-by-side of keyed cells")
    return 0
