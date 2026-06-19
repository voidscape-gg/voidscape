#!/usr/bin/env python3
"""Bake scenery objects from upstream rsc-world-map onto our plane PNGs.

Mirrors upstream's `src/entity-canvas.js` + `src/entity-image.js` layer at
vendor time so the runtime client has nothing to render — the trees / rocks /
fences / fungus that give the upstream map its rich cartographic look are
already present in `Client_Base/Cache/worldmap/plane-{0..3}.png`.

Each object renders as a 3×3 "+" symbol coloured by id and zone:
  - id == 1 (regular tree)            → green  rgb(0,160,0)
  - id ∈ {4, 38, 70, 205} in wild box → brown  rgb(112,64,0)
  - everything else                   → orange rgb(175,95,0)

Wild box (PNG-pixel coords post-transform) is x∈[1440,2304], y∈[286,1286].

Coord transform from upstream's entity-canvas.js:
  pngX = imageWidth - worldX*3 - 2          (imageWidth = 2448)
  pngY = worldY*3 - 1 - plane*2832          (plane * 48 * 3 * 18 + plane * 240)

Usage:
  scripts/bake-worldmap-objects.py path/to/objects.json
"""
import json
import sys
from pathlib import Path
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
WORLDMAP = REPO_ROOT / "Client_Base" / "Cache" / "worldmap"

PNG_W = 2448
PNG_H = 2736
TILE_SIZE = 3
PLANE_Y_STEP = 2832  # 48 * 3 * 18 + 240
NUM_PLANES = 4

OBJECT_COLOR    = (175,  95,   0)  # orange, +symbol used by upstream for game objects
TREE_COLOR      = (  0, 160,   0)  # green, regular trees
WILD_TREE_COLOR = (112,  64,   0)  # brown, dead trees / fungus inside the wilderness
WILD_SCENERY    = {4, 38, 70, 205}

WILD_BOX = (1440, 286, 2304, 1286)  # x0, y0, x1, y1 in PNG coords


def in_wilderness(px: int, py: int) -> bool:
    x0, y0, x1, y1 = WILD_BOX
    return x0 <= px <= x1 and y0 <= py <= y1


def color_for(obj_id: int, px: int, py: int) -> tuple:
    if obj_id in WILD_SCENERY and in_wilderness(px, py):
        return WILD_TREE_COLOR
    if obj_id == 1:
        return TREE_COLOR
    return OBJECT_COLOR


# Pixel offsets for the 3×3 + symbol — matches makeObjectImage in entity-image.js
# (vertical bar at column 1, horizontal bar at row 1). Centre is at (px+1, py+1).
PLUS_OFFSETS = ((1, 0), (0, 1), (1, 1), (2, 1), (1, 2))


def bake(objects_path: Path) -> None:
    objects = json.loads(objects_path.read_text())
    print(f"Loaded {len(objects)} objects from {objects_path}")

    for plane in range(NUM_PLANES):
        png_path = WORLDMAP / f"plane-{plane}.png"
        if not png_path.is_file():
            print(f"  plane-{plane}.png missing, skipping")
            continue
        img = Image.open(png_path).convert("RGBA")
        pixels = img.load()
        drawn = 0
        for obj in objects:
            obj_id = obj["id"]
            wx = obj["x"]
            wy = obj["y"]
            px = PNG_W - wx * TILE_SIZE - 2
            py = wy * TILE_SIZE - 1 - plane * PLANE_Y_STEP
            if py < 0 or py + 2 >= PNG_H or px < 0 or px + 2 >= PNG_W:
                continue
            color = color_for(obj_id, px, py) + (255,)
            for dx, dy in PLUS_OFFSETS:
                pixels[px + dx, py + dy] = color
            drawn += 1
        img.save(png_path)
        print(f"  plane-{plane}.png: drew {drawn} objects")


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    bake(Path(sys.argv[1]))


if __name__ == "__main__":
    main()
