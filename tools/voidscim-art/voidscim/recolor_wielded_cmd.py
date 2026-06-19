"""recolor-wielded — extract a source item's wielded sprite frames and recolor them.

Wielded sprites are stored at archive indices `[number, number + frame_count)`
where `number` is the AnimationDef's runtime field assigned in
`mudclient.loadEntitiesAuthentic()` (see add-custom-wielded-sprite.md). For
voidscape with `S_WANT_CUSTOM_SPRITES=false`, this is the path
`Authentic_Sprites.orsc` reads via `loadSprite(animationNumber, "entity", N)`.

Frame counts:
  - 15  base (always loaded)
  - +3  if hasA (combat poses)
  - +9  if hasF (special weapon-fighting poses; rare — zomb/skel/gob weapons)

This v1 only maps a small, validated set of source items to their archive
ranges. Extending to arbitrary items requires parsing
`loadAnimationDefinitions()` and simulating the assignment loop — TODO v2.

Output (per source frame):
  - <out>/frame_NN.png        recolored frame
  - <out>/frame_NN.png.json   sidecar copied from source (preserves xShift/yShift)
  - <out>/orig_NN.png         original frame for side-by-side reference
  - <out>/comparison.png      grid of (orig | recolored) at 4× zoom for review
"""
from __future__ import annotations
import json
import re
import zipfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from . import itemdefs
from .paths import ARCHIVE_PATH, TOOL_DIR
from .sprite_io import decode

NEW_ITEMS_DIR = TOOL_DIR / "new_items"

# Validated source-item → (archive_base, frame_count). Extend as we verify
# more items empirically (or replace with the parsing+simulation path).
WIELDED_SOURCES: dict[int, tuple[int, int]] = {
    81: (459, 18),  # rune 2-handed Sword (hasA → 15+3=18 frames at 459..476)
}


@dataclass
class ColorMap:
    pairs: list[tuple[tuple[int, int, int], tuple[int, int, int]]]

    def apply(self, rgba: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
        if rgba[3] == 0:
            return rgba
        rgb = rgba[:3]
        for src, dst in self.pairs:
            if rgb == src:
                return (*dst, rgba[3])
        return rgba


_HEX_RE = re.compile(r"^#?([0-9a-fA-F]{6})$")


def _parse_hex(s: str) -> tuple[int, int, int]:
    m = _HEX_RE.match(s.strip())
    if not m:
        raise ValueError(f"bad hex color: {s!r} (expected #RRGGBB or RRGGBB)")
    h = m.group(1)
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def _parse_color_map(s: str) -> ColorMap:
    """Parse `src1:dst1,src2:dst2,...` into a ColorMap."""
    pairs: list[tuple[tuple[int, int, int], tuple[int, int, int]]] = []
    for chunk in s.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        if ":" not in chunk:
            raise ValueError(f"bad color-map entry {chunk!r} (expected src:dst)")
        src, dst = chunk.split(":", 1)
        pairs.append((_parse_hex(src), _parse_hex(dst)))
    if not pairs:
        raise ValueError("color map is empty")
    return ColorMap(pairs)


def _recolor_frame(img: Image.Image, cmap: ColorMap) -> Image.Image:
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src_px = img.load()
    dst_px = out.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            dst_px[x, y] = cmap.apply(src_px[x, y])
    return out


def _build_comparison(orig: list[Image.Image], recol: list[Image.Image],
                      zoom: int = 4) -> Image.Image:
    """Vertical grid: each row is (orig | recolored), zoomed."""
    gap = 8
    pair_pad = 16
    max_w = max(img.size[0] for img in orig)
    cell_w = max_w * zoom + 2 * gap
    cell_h = max(img.size[1] for img in orig) * zoom + 2 * gap

    rows = len(orig)
    canvas_w = cell_w * 2 + pair_pad
    canvas_h = cell_h * rows
    canvas = Image.new("RGBA", (canvas_w, canvas_h), (40, 40, 40, 255))

    for i, (o, r) in enumerate(zip(orig, recol)):
        row_y = i * cell_h
        oz = o.resize((o.size[0] * zoom, o.size[1] * zoom), Image.NEAREST)
        rz = r.resize((r.size[0] * zoom, r.size[1] * zoom), Image.NEAREST)
        ox = (cell_w - oz.size[0]) // 2
        oy = row_y + (cell_h - oz.size[1]) // 2
        rx = cell_w + pair_pad + (cell_w - rz.size[0]) // 2
        ry = row_y + (cell_h - rz.size[1]) // 2
        canvas.paste(oz, (ox, oy), oz)
        canvas.paste(rz, (rx, ry), rz)
    return canvas


def cmd_recolor_wielded(source_item: int, color_map: str,
                        out_dir: str | None = None) -> int:
    if not ARCHIVE_PATH.exists():
        print(f"error: archive not found: {ARCHIVE_PATH}")
        return 1

    src_def = itemdefs.find_by_id(source_item)
    if not src_def:
        print(f"error: source item id {source_item} not found in EntityHandler.java")
        return 1

    if source_item not in WIELDED_SOURCES:
        print(f"error: no validated wielded-sprite mapping for source item id {source_item}")
        print(f"       known: {sorted(WIELDED_SOURCES)}")
        print(f"       (extending requires AnimationDef parsing + simulation; TODO v2)")
        return 1
    base_idx, frame_count = WIELDED_SOURCES[source_item]
    print(f"source item:   id={source_item} ({src_def['name']!r})")
    print(f"wielded frames: archive [{base_idx} .. {base_idx + frame_count - 1}] "
          f"({frame_count} frames)")

    cmap = _parse_color_map(color_map)
    print(f"color map ({len(cmap.pairs)} pair{'s' if len(cmap.pairs) != 1 else ''}):")
    for (sr, sg, sb), (dr, dg, db) in cmap.pairs:
        print(f"  #{sr:02x}{sg:02x}{sb:02x} → #{dr:02x}{dg:02x}{db:02x}")

    out_path = Path(out_dir) if out_dir else (
        NEW_ITEMS_DIR / f"wielded_recolor_from_{source_item}"
    )
    out_path.mkdir(parents=True, exist_ok=True)

    originals: list[Image.Image] = []
    recolored: list[Image.Image] = []
    sidecars: list[dict] = []
    color_hits = 0
    color_misses = set()

    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
        for offset in range(frame_count):
            idx = base_idx + offset
            try:
                data = zf.read(str(idx))
            except KeyError:
                print(f"error: archive entry {idx} missing — frame block incomplete")
                return 1
            img, sidecar = decode(data)
            originals.append(img)
            sidecars.append(sidecar)

            recol = _recolor_frame(img, cmap)
            recolored.append(recol)

            for px in img.getdata():
                if px[3] > 0:
                    rgb = px[:3]
                    if any(rgb == s for s, _ in cmap.pairs):
                        color_hits += 1
                    else:
                        color_misses.add(rgb)

    for offset, (orig, recol, sidecar) in enumerate(zip(originals, recolored, sidecars)):
        nn = f"{offset:02d}"
        recol.save(out_path / f"frame_{nn}.png")
        orig.save(out_path / f"orig_{nn}.png")
        (out_path / f"frame_{nn}.png.json").write_text(json.dumps(sidecar, indent=2))

    comp = _build_comparison(originals, recolored)
    comp.save(out_path / "comparison.png")

    print(f"\nwrote {frame_count} frames to {out_path}")
    print(f"  comparison.png  side-by-side (orig | recolored), 4× zoom")
    print(f"  frame_*.png     recolored frames (ready for pack-wielded slice)")
    print(f"  frame_*.png.json sidecars copied from source")
    print(f"  orig_*.png      originals for reference")

    if color_misses:
        misses_sample = sorted(color_misses)[:8]
        print(f"\nwarning: {len(color_misses)} unique pixel color(s) in source were NOT in your map "
              f"(left unchanged). Sample: " +
              ", ".join(f"#{r:02x}{g:02x}{b:02x}" for r, g, b in misses_sample))
        if len(color_misses) > 8:
            print(f"         (+ {len(color_misses) - 8} more)")
    else:
        print(f"\n✓ every opaque pixel matched a color-map entry — full coverage")
    return 0
