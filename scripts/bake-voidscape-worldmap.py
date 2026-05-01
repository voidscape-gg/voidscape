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
VOID_CLEAR = (56, 32, 0, 255)
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


def is_enclave_floor(x: int, y: int) -> bool:
    if not (102 <= x <= 123 and 305 <= y <= 325):
        return False
    if x < 105 and y < 308 and (105 - x) + (308 - y) > 3:
        return False
    if x < 105 and y > 322 and (105 - x) + (y - 322) > 3:
        return False
    if x > 120 and y < 308 and (x - 120) + (308 - y) > 3:
        return False
    if x > 120 and y > 322 and (x - 120) + (y - 322) > 3:
        return False
    return True


def enclave_floor_tiles() -> set[tuple[int, int]]:
    return {(x, y) for x in range(102, 124) for y in range(305, 326) if is_enclave_floor(x, y)}


def enclave_floor_color(x: int, y: int) -> tuple[int, int, int, int]:
    center_x, center_y = 113, 315
    dx = abs(x - center_x)
    dy = abs(y - center_y)
    dist2 = dx * dx + dy * dy
    if (dx <= 1 and dy <= 1) or (dx == 0 and dy <= 4) or (dy == 0 and dx <= 4):
        return VOID_ACCENT
    if 8 <= dist2 <= 18 or x == center_x or y == center_y:
        return VOID_PURPLE
    if (x, y) in ((110, 312), (116, 312), (110, 318), (116, 318)):
        return VOID_ACCENT
    return VOID_DEEP


def enclave_wall_tiles() -> set[tuple[int, int]]:
    walls = set()
    floor = enclave_floor_tiles()
    gates = {(113, 305), (113, 326), (102, 315), (124, 315)}
    for x, y in floor:
        for nx, ny, wx, wy in (
            (x, y - 1, x, y),
            (x, y + 1, x, y + 1),
            (x - 1, y, x, y),
            (x + 1, y, x + 1, y),
        ):
            if (nx, ny) not in floor and (wx, wy) not in gates:
                walls.add((wx, wy))

    for x in range(108, 119):
        walls.add((x, 308))
        if x < 112 or x > 114:
            walls.add((x, 313))
    for y in range(308, 313):
        walls.add((108, y))
        walls.add((119, y))
    for x in range(104, 110):
        walls.add((x, 312))
        walls.add((x, 318))
    for y in range(312, 318):
        walls.add((104, y))
        if y not in (314, 315):
            walls.add((110, y))
    for x in range(116, 122):
        walls.add((x, 312))
        walls.add((x, 318))
    for y in range(312, 318):
        if y not in (314, 315):
            walls.add((116, y))
        walls.add((122, y))
    return walls


def draw_void_enclave(draw: ImageDraw.ImageDraw) -> None:
    # Clear the previous baked enclave footprint first. The TSV block is
    # idempotent, but the PNG itself is a baked raster layer.
    for x in range(208, 231):
        for y in range(341, 363):
            draw_tile(draw, x, y, VOID_CLEAR)
    for x in range(100, 127):
        for y in range(303, 329):
            draw_tile(draw, x, y, VOID_CLEAR)
    for x, y in enclave_floor_tiles():
        draw_tile(draw, x, y, enclave_floor_color(x, y))
    for x, y in enclave_wall_tiles():
        draw_tile(draw, x, y, VOID_PURPLE)
    for x, y in ((113, 305), (113, 326), (102, 315), (124, 315)):
        draw_plus(draw, x, y, VOID_ACCENT)
    for x, y in ((105, 314), (112, 309), (119, 314), (217, 442), (210, 439), (217, 460), (214, 437)):
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
        label_row("Void\\nEnclave", 113, 315, 10, 1),
        label_row("PK Catching\\nSimulator", 409, 72, 10, 1),
        label_row("Void\\nAuctioneer", 217, 460, 8, 0),
        label_row("Edgar", 124, 640, 8, 0),
    ]
    labels = strip_custom_block(LABELS)
    LABELS.write_text(labels + CUSTOM_BEGIN + "\n" + "\n".join(custom_labels) + "\n" + CUSTOM_END + "\n")

    custom_points = [
        point_row("quest", 24, 24),             # Void Herald
        point_row("bank", 210, 439),           # Edgeville chest
        point_row("altar", 217, 442),          # Edgeville altar
        point_row("combat-practice", 214, 437),
        point_row("bank", 105, 314),           # Enclave bank shrine
        point_row("altar", 112, 309),          # Enclave altar
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
