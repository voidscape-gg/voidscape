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


def tutorial_isle_tiles() -> set[tuple[int, int]]:
    # Mask copied verbatim from patch-void-enclave-landscape.py
    # (tutorial_isle_tiles) — keep the two in lockstep.
    chambers = ((34, 45, 31, 40), (34, 45, 23, 30), (37, 43, 18, 22))
    land = set()
    for min_x, max_x, min_y, max_y in chambers:
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                if x in (min_x, max_x) and y in (min_y, max_y):
                    continue
                land.add((x, y))
    return land


def draw_tutorial_isle(draw: ImageDraw.ImageDraw) -> None:
    land = tutorial_isle_tiles()
    for x, y in land:
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        pad = abs(x - 40) <= 1 and abs(y - 37) <= 1
        ring = abs(x - 40) <= 1 and abs(y - 27) <= 1
        scar = y <= 22
        lane = x == 40
        gate = y in (31, 23)
        color = (VOID_EDGE if edge else VOID_ACCENT if (pad or ring) else VOID_DEEP if scar
                 else VOID_PURPLE if (lane or gate) else VOID_MID)
        draw_tile(draw, x, y, color)


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
    if not (98 <= x <= 128 and 300 <= y <= 330):
        return False
    if x < 103 and y < 305 and (103 - x) + (305 - y) > 6:
        return False
    if x < 103 and y > 325 and (103 - x) + (y - 325) > 6:
        return False
    if x > 123 and y < 305 and (x - 123) + (305 - y) > 6:
        return False
    if x > 123 and y > 325 and (x - 123) + (y - 325) > 6:
        return False
    return True


def enclave_floor_tiles() -> set[tuple[int, int]]:
    return {(x, y) for x in range(98, 129) for y in range(300, 331) if is_enclave_floor(x, y)}


def enclave_floor_color(x: int, y: int) -> tuple[int, int, int, int]:
    center_x, center_y = 113, 315
    dx = abs(x - center_x)
    dy = abs(y - center_y)
    dist2 = dx * dx + dy * dy
    if (dx <= 1 and dy <= 1) or (dx == 0 and dy <= 4) or (dy == 0 and dx <= 4):
        return VOID_ACCENT
    if 8 <= dist2 <= 18 or x == center_x or y == center_y:
        return VOID_PURPLE
    if (x, y) in ((106, 306), (120, 306), (105, 315), (121, 315), (110, 326), (116, 326)):
        return VOID_ACCENT
    return VOID_DEEP


def enclave_wall_tiles() -> set[tuple[int, int]]:
    walls = set()
    floor = enclave_floor_tiles()
    gates = {(113, 300), (113, 331), (98, 315), (129, 315)}
    for x, y in floor:
        for nx, ny, wx, wy in (
            (x, y - 1, x, y),
            (x, y + 1, x, y + 1),
            (x - 1, y, x, y),
            (x + 1, y, x + 1, y),
        ):
            if (nx, ny) not in floor and (wx, wy) not in gates:
                walls.add((wx, wy))

    for x in range(105, 122):
        walls.add((x, 303))
        if x < 111 or x > 115:
            walls.add((x, 310))
    for y in range(303, 310):
        walls.add((105, y))
        walls.add((122, y))
    for x in range(101, 109):
        walls.add((x, 311))
        walls.add((x, 320))
    for y in range(311, 320):
        walls.add((101, y))
        if y not in (315, 316):
            walls.add((109, y))
    for x in range(117, 125):
        walls.add((x, 311))
        walls.add((x, 320))
    for y in range(311, 320):
        if y not in (315, 316):
            walls.add((117, y))
        walls.add((125, y))
    for x in range(107, 120):
        if x < 111 or x > 115:
            walls.add((x, 323))
        walls.add((x, 329))
    for y in range(323, 329):
        walls.add((107, y))
        walls.add((120, y))
    return walls


def draw_void_enclave(draw: ImageDraw.ImageDraw) -> None:
    # Clear the previous baked enclave footprint first. The TSV block is
    # idempotent, but the PNG itself is a baked raster layer.
    for x in range(208, 231):
        for y in range(341, 363):
            draw_tile(draw, x, y, VOID_CLEAR)
    for x in range(96, 131):
        for y in range(298, 333):
            draw_tile(draw, x, y, VOID_CLEAR)
    for x, y in enclave_floor_tiles():
        draw_tile(draw, x, y, enclave_floor_color(x, y))
    for x, y in enclave_wall_tiles():
        draw_tile(draw, x, y, VOID_PURPLE)
    for x, y in ((113, 300), (113, 331), (98, 315), (129, 315)):
        draw_plus(draw, x, y, VOID_ACCENT)
    for x, y in ((105, 316), (112, 305), (122, 315), (112, 326), (217, 442), (210, 439), (217, 460), (214, 437)):
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
        label_row("Tutorial\\nIsle", 40, 15, 10, 1),
        label_row("Void\\nDungeon", 112, 292, 10, 1),
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
        point_row("bank", 105, 316),           # Enclave bank shrine
        point_row("altar", 112, 305),          # Enclave altar
        point_row("dungeon", 112, 296),        # Void Dungeon rift
        point_row("combat-practice", 409, 72),
    ]
    points = strip_custom_block(POINTS)
    POINTS.write_text(points + CUSTOM_BEGIN + "\n" + "\n".join(custom_points) + "\n" + CUSTOM_END + "\n")


def main() -> None:
    img = Image.open(PLANE_0).convert("RGBA")
    draw = ImageDraw.Draw(img)
    draw_void_island(draw)
    draw_tutorial_isle(draw)
    draw_catchsim_islands(draw)
    draw_void_enclave(draw)
    img.save(PLANE_0)
    bake_tsv()
    print(f"Baked Voidscape overlays into {PLANE_0.relative_to(REPO_ROOT)}")
    print(f"Updated {LABELS.relative_to(REPO_ROOT)} and {POINTS.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
