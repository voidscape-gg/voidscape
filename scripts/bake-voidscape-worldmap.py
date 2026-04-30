#!/usr/bin/env python3
"""Bake Voidscape-only terrain, labels, and POIs onto the cached world map.

The in-game world map is not rendered from the live landscape archive at
runtime. It is a cached PNG vendored from rsc-world-map, with TSV overlays for
labels and POI icons. Any Voidscape custom landscape therefore needs an
explicit bake step here after the base world-map assets are refreshed.
"""

from pathlib import Path
from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parent.parent
WORLDMAP = REPO_ROOT / "Client_Base" / "Cache" / "worldmap"
PLANE_0 = WORLDMAP / "plane-0.png"
LABELS = WORLDMAP / "labels.tsv"
POINTS = WORLDMAP / "points.tsv"

TILE_PX = 3
X_ORIGIN = 2446
Y_OFFSET = -1

CUSTOM_BEGIN = "# voidscape custom overlay begin"
CUSTOM_END = "# voidscape custom overlay end"

VOID_PURPLE = (118, 58, 178, 255)
VOID_DEEP = (35, 22, 48, 255)
VOID_MID = (72, 48, 96, 255)
VOID_ACCENT = (200, 48, 160, 255)
VOID_EDGE = (15, 8, 28, 255)
VOID_TEXT = "rgb(200,48,160)"


def png_point(world_x: int, world_y: int) -> tuple[int, int]:
    return X_ORIGIN - world_x * TILE_PX, (world_y % 944) * TILE_PX + Y_OFFSET


def draw_tile(draw: ImageDraw.ImageDraw, world_x: int, world_y: int, color: tuple[int, int, int, int]) -> None:
    cx, cy = png_point(world_x, world_y)
    # Treat the transform as a tile centre. Adjacent world tiles are exactly
    # 3 px apart, so +/-1 gives a contiguous 3x3 tile footprint.
    draw.rectangle((cx - 1, cy - 1, cx + 1, cy + 1), fill=color)


def draw_plus(draw: ImageDraw.ImageDraw, world_x: int, world_y: int, color: tuple[int, int, int, int]) -> None:
    cx, cy = png_point(world_x, world_y)
    for dx, dy in ((0, -1), (-1, 0), (0, 0), (1, 0), (0, 1)):
        draw.point((cx + dx, cy + dy), fill=color)


def void_island_tiles() -> set[tuple[int, int]]:
    center_x, center_y = 24, 24
    land = set()
    for x in range(center_x - 8, center_x + 9):
        for y in range(center_y - 7, center_y + 8):
            dx = abs(x - center_x)
            dy = abs(y - center_y)
            if (dx * 1.15) + dy <= 9.0:
                land.add((x, y))
    return land


def catchsim_tiles(offset_x: int) -> set[tuple[int, int]]:
    min_x, max_x = 340 + offset_x, 382 + offset_x
    min_y, max_y = 52, 90
    center_x, center_y = 361 + offset_x, 71
    radius_x, radius_y = 21.0, 19.0
    land = set()
    for x in range(min_x, max_x + 1):
        for y in range(min_y, max_y + 1):
            nx = abs(x - center_x) / radius_x
            ny = abs(y - center_y) / radius_y
            if (nx ** 4) + (ny ** 4) <= 1.0:
                land.add((x, y))
    return land


def draw_void_island(draw: ImageDraw.ImageDraw) -> None:
    land = void_island_tiles()
    for x, y in land:
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        center = abs(x - 24) <= 1 and abs(y - 24) <= 1
        ring = abs(x - 24) + abs(y - 24) in (2, 3)
        draw_tile(draw, x, y, VOID_EDGE if edge else VOID_ACCENT if center else VOID_PURPLE if ring else VOID_MID)


def draw_catchsim_islands(draw: ImageDraw.ImageDraw) -> None:
    for offset_x in (0, 48, 96):
        land = catchsim_tiles(offset_x)
        center_x = 361 + offset_x
        for x, y in land:
            edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            lane = x == center_x or y == 71
            draw_tile(draw, x, y, VOID_EDGE if edge else VOID_PURPLE if lane else VOID_MID)


def enclave_wall_tiles() -> list[tuple[int, int]]:
    walls = []
    for x in range(208, 230):
        if x != 219:
            walls.append((x, 341))
            walls.append((x, 362))
    for y in range(341, 362):
        if y != 351:
            walls.append((208, y))
            walls.append((230, y))
    # Main sanctum and two shrines. This mirrors the landscape patcher enough
    # for the world map to read as the same compound shape.
    for x in range(214, 223):
        walls.append((x, 347))
        if x != 218:
            walls.append((x, 355))
    for y in range(347, 355):
        walls.append((214, y))
        walls.append((223, y))
    for x in range(210, 214):
        walls.append((x, 347))
        if x != 212:
            walls.append((x, 351))
    for y in range(347, 351):
        walls.append((210, y))
        walls.append((214, y))
    for x in range(224, 228):
        walls.append((x, 347))
        if x != 225:
            walls.append((x, 351))
    for y in range(347, 351):
        walls.append((224, y))
        walls.append((228, y))
    return walls


def draw_void_enclave(draw: ImageDraw.ImageDraw) -> None:
    for x in range(208, 230):
        for y in range(341, 362):
            draw_tile(draw, x, y, VOID_DEEP)
    for x, y in enclave_wall_tiles():
        draw_tile(draw, x, y, VOID_PURPLE)
    for x, y in ((219, 341), (219, 362), (208, 351), (230, 351)):
        draw_plus(draw, x, y, VOID_ACCENT)
    for x, y in ((212, 349), (217, 442), (225, 349), (210, 439), (217, 460), (214, 437)):
        draw_plus(draw, x, y, VOID_ACCENT)


def strip_custom_block(path: Path) -> str:
    if not path.exists():
        return ""
    lines = path.read_text().splitlines()
    out = []
    skipping = False
    for line in lines:
        if line == CUSTOM_BEGIN:
            skipping = True
            continue
        if line == CUSTOM_END:
            skipping = False
            continue
        if not skipping:
            out.append(line)
    return "\n".join(out).rstrip() + "\n"


def label_row(text: str, world_x: int, world_y: int, size: int = 10, bold: int = 0) -> str:
    px, py = png_point(world_x, world_y)
    return f"{text}\t{px}\t{py}\t{size}\tcenter\t{bold}\t{VOID_TEXT}"


def point_row(kind: str, world_x: int, world_y: int) -> str:
    px, py = png_point(world_x, world_y)
    return f"{kind}\t{px}\t{py}"


def bake_tsv() -> None:
    custom_labels = [
        label_row("Void\\nIsland", 24, 16, 10, 1),
        label_row("Void\\nEnclave", 219, 351, 10, 1),
        label_row("PK Catching\\nSimulator", 409, 72, 10, 1),
        label_row("Void\\nAuctioneer", 217, 460, 8, 0),
        label_row("Edgar", 124, 640, 8, 0),
        label_row("Voidling", 63, 363, 8, 0),
    ]
    labels = strip_custom_block(LABELS)
    LABELS.write_text(labels + CUSTOM_BEGIN + "\n" + "\n".join(custom_labels) + "\n" + CUSTOM_END + "\n")

    custom_points = [
        point_row("quest", 24, 24),             # Void Herald
        point_row("bank", 210, 439),           # Edgeville chest
        point_row("altar", 217, 442),          # Edgeville altar
        point_row("combat-practice", 214, 437),
        point_row("bank", 215, 347),           # Enclave bank shrine
        point_row("altar", 223, 348),          # Enclave altar
        point_row("combat-practice", 409, 72),
    ]
    points = strip_custom_block(POINTS)
    POINTS.write_text(points + CUSTOM_BEGIN + "\n" + "\n".join(custom_points) + "\n" + CUSTOM_END + "\n")


def main() -> None:
    img = Image.open(PLANE_0).convert("RGBA")
    draw = ImageDraw.Draw(img)
    draw_void_island(draw)
    draw_catchsim_islands(draw)
    draw_void_enclave(draw)
    img.save(PLANE_0)
    bake_tsv()
    print(f"Baked Voidscape overlays into {PLANE_0.relative_to(REPO_ROOT)}")
    print(f"Updated {LABELS.relative_to(REPO_ROOT)} and {POINTS.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
