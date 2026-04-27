"""variations <item_id> [description] — generate variations of an existing item.

Uses a single source sprite as the reference and asks gpt-image-1.5 for a 2×2
grid of variations. Per the AI image pipeline lessons, grid framing keeps the
model in pixel-art icon mode rather than fantasy painting mode, and a 2×2 grid
costs the same as a single 1024² image but yields 4 usable variants.

Output:
  tools/voidscim-art/new_items/variations_<item_slug>/
    reference.png        single source sprite, NEAREST-upscaled on magenta BG
    prompt.txt           text prompt
    raw_1..N.png         full 1024² API returns (each is a 2×2 grid)
    cell_1..(4N).png     individual cells, magenta BG
    cell_1..(4N)_keyed.png  chroma-keyed transparent
    preview.png          all keyed cells in a 4×N grid
"""
from __future__ import annotations
import re
import time
import zipfile

from PIL import Image

from . import itemdefs
from .new_icon_cmd import NEW_ITEMS_DIR, _build_preview, _chroma_key_magenta
from .openai_api import call_edits_high_fidelity
from .paths import ARCHIVE_PATH, SPRITE_ITEM
from .sprite_io import decode

CANVAS_PX = 1024
MAGENTA_RGBA = (0xFF, 0x00, 0xFF, 0xFF)

DEFAULT_DESCRIPTION = (
    "diverse variations of this item: different metals, materials, hilts, "
    "blade shapes, and ornamental details — keeping the same general "
    "silhouette and icon scale"
)


def _slug(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "x"


def _build_reference(sprite: Image.Image) -> Image.Image:
    """Single source sprite, NEAREST-upscaled to fill ~80% of 1024² on magenta."""
    canvas = Image.new("RGBA", (CANVAS_PX, CANVAS_PX), MAGENTA_RGBA)
    target = int(CANVAS_PX * 0.80)
    int_scale = max(1, min(target // sprite.width, target // sprite.height))
    new_w = sprite.width * int_scale
    new_h = sprite.height * int_scale
    scaled = sprite.resize((new_w, new_h), Image.NEAREST)
    ox = (CANVAS_PX - new_w) // 2
    oy = (CANVAS_PX - new_h) // 2
    canvas.paste(scaled, (ox, oy), scaled)
    return canvas


def _build_prompt(item_name: str, description: str) -> str:
    return (
        f"This is a RuneScape Classic '{item_name}' inventory icon, NEAREST-upscaled "
        "from its native pixel-art form, on a magenta (#FF00FF) background.\n\n"
        f"Output a 2×2 grid (so 4 cells, evenly tiled) of variations of this item. "
        f"Variations should be: {description}.\n\n"
        "Hard requirements for every cell:\n"
        "- chunky pixel-art rendering with hard, integer-pixel edges (NO soft "
        "anti-aliasing, NO painterly blur, NO HD glow)\n"
        "- silhouette and on-screen scale the same as the source — same orientation, "
        "same icon footprint\n"
        "- outline weight, palette saturation, lighting direction, shading style "
        "consistent with classic RSC inventory icons\n"
        "- solid magenta (#FF00FF) background filling the cell\n"
        "- no text, no labels, no borders, no signatures\n\n"
        "All 4 cells should clearly be siblings of the source icon, distinguishable "
        "from each other but unmistakably the same item type."
    )


def _split_2x2(img: Image.Image) -> list[Image.Image]:
    w, h = img.size
    half_w, half_h = w // 2, h // 2
    return [
        img.crop((0, 0, half_w, half_h)),
        img.crop((half_w, 0, w, half_h)),
        img.crop((0, half_h, half_w, h)),
        img.crop((half_w, half_h, w, h)),
    ]


def cmd_variations(item_id: int, description: str | None = None,
                   n: int = 1, dry_run: bool = False) -> int:
    if not ARCHIVE_PATH.exists():
        print(f"error: archive not found: {ARCHIVE_PATH}")
        return 1

    item = itemdefs.find_by_id(item_id)
    if not item:
        print(f"error: item id {item_id} not found in client EntityHandler")
        return 1
    archive_idx = item["sprite_id"] + SPRITE_ITEM
    print(f"source: id={item['id']} name={item['name']!r} sprite={item['sprite_id']} idx={archive_idx}")

    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
        try:
            data = zf.read(str(archive_idx))
        except KeyError:
            print(f"error: archive entry {archive_idx} missing")
            return 1
    sprite, sidecar = decode(data)
    print(f"source sprite: {sidecar['width']}×{sidecar['height']}")

    desc = description or DEFAULT_DESCRIPTION
    base_slug = _slug(item["name"])
    out_dir = NEW_ITEMS_DIR / f"variations_{base_slug}"
    out_dir.mkdir(parents=True, exist_ok=True)

    reference = _build_reference(sprite)
    ref_path = out_dir / "reference.png"
    reference.save(ref_path)
    print(f"reference: {ref_path}")

    prompt = _build_prompt(item["name"], desc)
    (out_dir / "prompt.txt").write_text(prompt)

    if dry_run:
        print("\ndry-run: reference.png + prompt.txt saved, no API call")
        return 0

    print(f"\ncalling gpt-image-1.5 (n={n}, → {n * 4} variants) ...")
    t0 = time.time()
    images = call_edits_high_fidelity(
        reference=ref_path,
        prompt=prompt,
        n=n,
        background="opaque",
    )
    print(f"  → {len(images)} image(s) in {time.time() - t0:.1f}s")

    keyed_cells: list[Image.Image] = []
    cell_n = 0
    for i, b in enumerate(images, start=1):
        raw_path = out_dir / f"raw_{i}.png"
        raw_path.write_bytes(b)
        full = Image.open(raw_path).convert("RGBA")
        for q in _split_2x2(full):
            cell_n += 1
            q.save(out_dir / f"cell_{cell_n}.png")
            keyed = _chroma_key_magenta(q)
            keyed.save(out_dir / f"cell_{cell_n}_keyed.png")
            keyed_cells.append(keyed)

    preview = _build_preview(keyed_cells)
    preview.save(out_dir / "preview.png")

    print(f"\noutput: {out_dir}")
    print(f"  reference.png       upscaled source on magenta BG")
    print(f"  prompt.txt          full text prompt")
    print(f"  raw_1..{n}.png         full 1024² API returns (each a 2×2 grid)")
    print(f"  cell_1..{cell_n}.png        individual cells (magenta BG)")
    print(f"  cell_1..{cell_n}_keyed.png  chroma-keyed transparent")
    print(f"  preview.png         all {cell_n} keyed cells side-by-side")
    return 0
