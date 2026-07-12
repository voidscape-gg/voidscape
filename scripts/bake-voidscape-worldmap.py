#!/usr/bin/env python3
"""Bake Voidscape-only terrain, labels, and POIs onto the cached world map.

The in-game world map is not rendered from the live landscape archive at
runtime. It is a cached PNG vendored from rsc-world-map, with TSV overlays for
labels and POI icons. Any Voidscape custom landscape therefore needs an
explicit bake step here after the base world-map assets are refreshed.
"""

import json
from collections import Counter
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO_ROOT = Path(__file__).resolve().parent.parent
WORLDMAP = REPO_ROOT / "Client_Base" / "Cache" / "worldmap"
PLANE_0 = WORLDMAP / "plane-0.png"
LABELS = WORLDMAP / "labels.tsv"
POINTS = WORLDMAP / "points.tsv"
DUNGEON_FLOOR = REPO_ROOT / "server" / "conf" / "server" / "data" / "void_dungeon_floor.json"
DUNGEON_SCENERY = REPO_ROOT / "server" / "conf" / "server" / "defs" / "locs" / "SceneryLocsVoidDungeon.json"
DUNGEON_BOUNDARIES = REPO_ROOT / "server" / "conf" / "server" / "defs" / "locs" / "BoundaryLocsVoidDungeon.json"
DUNGEON_FONT = REPO_ROOT / "server" / "conf" / "server" / "fonts" / "runescape_uf.ttf"

TILE_PX = 3
X_ORIGIN = 2446
Y_OFFSET = -1
FLOOR_HEIGHT = 944
MAP_SIZE = (2448, 2736)
UNDERGROUND_FLOORS = (1, 2, 3)

CUSTOM_BEGIN = "# voidscape custom overlay begin"
CUSTOM_END = "# voidscape custom overlay end"

VOID_PURPLE = (118, 58, 178, 255)
VOID_DEEP = (35, 22, 48, 255)
VOID_MID = (72, 48, 96, 255)
VOID_INDOOR = (80, 80, 80, 255)
VOID_ACCENT = (200, 48, 160, 255)
VOID_EDGE = (15, 8, 28, 255)
VOID_CLEAR = (56, 32, 0, 255)
VOID_TEXT = "rgb(200,48,160)"

DUNGEON_VOID = (12, 14, 18, 255)
DUNGEON_GRID = (18, 20, 25, 255)
DUNGEON_ROOM = (66, 65, 72, 255)
DUNGEON_ROOM_DETAIL = (76, 74, 82, 255)
DUNGEON_CORRIDOR = (52, 51, 58, 255)
DUNGEON_CORRIDOR_DETAIL = (60, 58, 66, 255)
DUNGEON_WALL_SHADOW = (8, 9, 12, 255)
DUNGEON_WALL = (181, 177, 166, 255)
DUNGEON_WALL_ACCENT = (161, 109, 190, 255)
DUNGEON_SCENERY_MARK = (137, 132, 122, 255)
DUNGEON_TITLE = (226, 222, 211, 255)
DUNGEON_TITLE_RULE = (126, 82, 151, 255)
DUNGEON_LABEL_BG = (18, 20, 25, 235)
DUNGEON_LADDER = (224, 176, 87, 255)
DUNGEON_RIFT = (96, 207, 211, 255)
DUNGEON_RIFT_CORE = (194, 90, 177, 255)


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
    # Keep this classification in lockstep with enclave_floor_overlay() in
    # patch-void-enclave-landscape.py.
    if (y == 305 and 110 <= x <= 116) or (x == 113 and 303 <= y <= 307):
        return VOID_ACCENT
    if ((x in (115, 118) and 311 <= y <= 314)
            or (y in (311, 314) and 115 <= x <= 118)):
        return VOID_ACCENT
    if ((x in (112, 115) and 320 <= y <= 323)
            or (y in (320, 323) and 112 <= x <= 115)):
        return VOID_ACCENT
    if (x, y) in ((112, 314), (114, 314), (112, 316), (114, 316)):
        return VOID_ACCENT

    if ((112 <= x <= 114 and 300 <= y <= 302)
            or (112 <= x <= 114 and 329 <= y <= 330)
            or (98 <= x <= 100 and 314 <= y <= 316)
            or (126 <= x <= 128 and 314 <= y <= 316)):
        return VOID_PURPLE
    if 303 <= y <= 308 and (107 <= x <= 110 or 117 <= x <= 120):
        return VOID_INDOOR
    return VOID_DEEP


def enclave_wall_tiles() -> set[tuple[int, int]]:
    # Coordinate-only projection of enclave_walls() in
    # patch-void-enclave-landscape.py. The cached world map cannot express wall
    # direction, so multiple wall edges at one coordinate collapse to one tile.
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

    # Hall of Oaths: two surviving roof wings around the broken central aisle.
    for x in list(range(106, 111)) + list(range(116, 121)):
        walls.add((x, 302))
    for y in range(303, 309):
        if y == 306:
            continue
        walls.add((106, y))
        walls.add((121, y))
    for x in list(range(106, 109)) + list(range(119, 121)):
        walls.add((x, 309))

    # Quartermaster Court: low remnants around an otherwise open yard.
    for x in range(100, 108):
        walls.add((x, 311))
        walls.add((x, 320))
    for y in (311, 312, 319, 320):
        walls.add((100, y))

    # Mend-and-Descent Court: open arcade around the pool and ladder.
    for x in range(119, 127):
        walls.add((x, 310))
        walls.add((x, 320))
    for y in (311, 312, 319, 320):
        walls.add((127, y))

    # Riftward Yard: broken low walls around both Arena Rift bypasses.
    for x in list(range(107, 110)) + list(range(118, 121)):
        walls.add((x, 323))
    for y in range(324, 329):
        if y == 326:
            continue
        walls.add((107, y))
        walls.add((121, y))
    for x in list(range(107, 111)) + list(range(116, 121)):
        walls.add((x, 329))
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
    for x, y in ((105, 316), (112, 305), (122, 315), (113, 321), (217, 442), (210, 439), (217, 460), (214, 437)):
        draw_plus(draw, x, y, VOID_ACCENT)


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="ascii"))


def loc_point(record: dict) -> tuple[int, int]:
    pos = record["pos"]
    return int(pos["X"]), int(pos["Y"])


def rectangle_tiles(rectangle: dict) -> set[tuple[int, int]]:
    return {
        (x, y)
        for x in range(int(rectangle["minX"]), int(rectangle["maxX"]) + 1)
        for y in range(int(rectangle["minY"]), int(rectangle["maxY"]) + 1)
    }


def dungeon_projection(payload: dict) -> tuple[set[tuple[int, int]], dict[tuple[int, int], str]]:
    mask = {(int(x), int(y)) for x, y in payload["tiles"]}
    owners: dict[tuple[int, int], str] = {}
    tile_kinds: dict[tuple[int, int], str] = {}

    for stage in payload["stages"]:
        stage_tiles: set[tuple[int, int]] = set()
        for rectangle in stage["rectangles"]:
            tiles = rectangle_tiles(rectangle)
            stage_tiles.update(tiles)
            kind = str(rectangle.get("kind", "room"))
            for tile in tiles:
                if kind == "room" or tile not in tile_kinds:
                    tile_kinds[tile] = kind

        expected_count = int(stage.get("tileCount", len(stage_tiles)))
        if len(stage_tiles) != expected_count:
            raise ValueError(
                f"stage {stage['key']} metadata has {len(stage_tiles)} tiles, expected {expected_count}"
            )
        for tile in stage_tiles:
            prior = owners.get(tile)
            if prior is not None and prior != stage["key"]:
                raise ValueError(f"dungeon tile {tile} belongs to both {prior} and {stage['key']}")
            owners[tile] = str(stage["key"])

    projected = set(owners)
    if projected != mask:
        raise ValueError(
            "dungeon stage rectangles differ from mask "
            f"(missing {len(mask - projected)}, extra {len(projected - mask)})"
        )
    return mask, tile_kinds


def grid_point(world_x: int, world_y: int) -> tuple[int, int]:
    # Generated boundaries use grid intersections: direction 0 runs from
    # (x,y) to (x+1,y), direction 1 from (x,y) to (x,y+1).
    return X_ORIGIN + 1 - world_x * TILE_PX, (world_y % FLOOR_HEIGHT) * TILE_PX - 2


def draw_boundary(draw: ImageDraw.ImageDraw, record: dict, color: tuple[int, int, int, int]) -> None:
    world_x, world_y = loc_point(record)
    direction = int(record["direction"])
    start = grid_point(world_x, world_y)
    if direction == 0:
        end = grid_point(world_x + 1, world_y)
    elif direction == 1:
        end = grid_point(world_x, world_y + 1)
    else:
        raise ValueError(f"unsupported dungeon boundary direction {direction}")
    draw.line((start, end), fill=DUNGEON_WALL_SHADOW, width=3)
    draw.line((start, end), fill=color, width=1)


def dungeon_font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype(str(DUNGEON_FONT), size)
    except OSError:
        return ImageFont.load_default()


def text_box(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.ImageFont) -> tuple[int, int]:
    left, top, right, bottom = draw.textbbox((0, 0), text, font=font)
    return right - left, bottom - top


def boxes_overlap(first: tuple[int, int, int, int], second: tuple[int, int, int, int]) -> bool:
    return first[0] < second[2] and first[2] > second[0] and first[1] < second[3] and first[3] > second[1]


def draw_stage_title(
    draw: ImageDraw.ImageDraw,
    stage: dict,
    font: ImageFont.ImageFont,
    occupied: list[tuple[int, int, int, int]],
) -> None:
    bounds = stage["bounds"]
    center_x = (int(bounds["minX"]) + int(bounds["maxX"])) // 2
    _, anchor_y = png_point(center_x, int(bounds["minY"]))
    anchor_x, _ = png_point(center_x, int(bounds["minY"]))
    text = str(stage["name"])
    width, height = text_box(draw, text, font)
    x = anchor_x - width // 2
    y = anchor_y - height - 13
    box = (x - 4, y - 3, x + width + 4, y + height + 4)
    draw.rectangle(box, fill=DUNGEON_LABEL_BG)
    draw.text((x, y), text, fill=DUNGEON_TITLE, font=font)
    draw.line((x, y + height + 2, x + width, y + height + 2), fill=DUNGEON_TITLE_RULE, width=1)
    occupied.append(box)


def landmark_label_box(
    draw: ImageDraw.ImageDraw,
    center_x: int,
    center_y: int,
    label: str,
    font: ImageFont.ImageFont,
    occupied: list[tuple[int, int, int, int]],
) -> tuple[int, int, int, int]:
    width, height = text_box(draw, label, font)
    candidates = (
        (center_x + 9, center_y - height // 2),
        (center_x - width - 9, center_y - height // 2),
        (center_x - width // 2, center_y - height - 12),
        (center_x - width // 2, center_y + 10),
    )
    for x, y in candidates:
        box = (x - 3, y - 2, x + width + 3, y + height + 3)
        padded = (box[0] - 2, box[1] - 2, box[2] + 2, box[3] + 2)
        if (
            box[0] >= 0
            and box[1] >= 0
            and box[2] < MAP_SIZE[0]
            and box[3] < MAP_SIZE[1]
            and not any(boxes_overlap(padded, prior) for prior in occupied)
        ):
            return box
    x, y = candidates[-1]
    return (x - 3, y - 2, x + width + 3, y + height + 3)


def draw_landmark(
    draw: ImageDraw.ImageDraw,
    world_x: int,
    world_y: int,
    kind: str,
    font: ImageFont.ImageFont,
    occupied: list[tuple[int, int, int, int]],
) -> None:
    center_x, center_y = png_point(world_x, world_y)
    if kind == "ladder":
        label = "LADDER"
        color = DUNGEON_LADDER
    elif kind == "rift":
        label = "EXIT RIFT"
        color = DUNGEON_RIFT
    else:
        raise ValueError(f"unsupported dungeon landmark kind {kind}")

    box = landmark_label_box(draw, center_x, center_y, label, font, occupied)
    label_center_x = (box[0] + box[2]) // 2
    label_center_y = (box[1] + box[3]) // 2
    draw.line((center_x, center_y, label_center_x, label_center_y), fill=color, width=1)

    if kind == "ladder":
        draw.rectangle((center_x - 4, center_y - 5, center_x + 4, center_y + 5), fill=DUNGEON_WALL_SHADOW, outline=color)
        draw.line((center_x - 2, center_y - 3, center_x - 2, center_y + 3), fill=color)
        draw.line((center_x + 2, center_y - 3, center_x + 2, center_y + 3), fill=color)
        for rung_y in range(center_y - 3, center_y + 4, 2):
            draw.line((center_x - 2, rung_y, center_x + 2, rung_y), fill=color)
    elif kind == "rift":
        draw.ellipse((center_x - 5, center_y - 5, center_x + 5, center_y + 5), fill=DUNGEON_WALL_SHADOW, outline=color, width=2)
        draw.ellipse((center_x - 2, center_y - 2, center_x + 2, center_y + 2), fill=DUNGEON_RIFT_CORE)
    text_width, text_height = text_box(draw, label, font)
    text_x = box[0] + 3
    text_y = box[1] + 2
    draw.rectangle(box, fill=DUNGEON_LABEL_BG)
    draw.text((text_x, text_y), label, fill=color, font=font)
    occupied.append((box[0], box[1], box[0] + text_width + 6, box[1] + text_height + 5))


def dungeon_landmarks(payload: dict, scenery: list[dict]) -> tuple[set[tuple[int, int]], set[tuple[int, int]]]:
    scenery_records = {
        (int(record["id"]), *loc_point(record))
        for record in scenery
    }
    scenery_ids_by_point: dict[tuple[int, int], set[int]] = {}
    for record in scenery:
        scenery_ids_by_point.setdefault(loc_point(record), set()).add(int(record["id"]))

    surface_point = (int(payload["surfaceRift"]["x"]), int(payload["surfaceRift"]["y"]))
    rift_ids = scenery_ids_by_point.get(surface_point, set())
    if not rift_ids:
        raise ValueError(f"surface rift {surface_point} is absent from generated scenery")

    ladders: set[tuple[int, int]] = set()
    for transition in payload.get("transitions", []):
        source = (int(transition["source"]["x"]), int(transition["source"]["y"]))
        object_id = int(transition["objectId"])
        if (object_id, *source) not in scenery_records:
            raise ValueError(f"transition {transition['name']} is absent from generated scenery")
        ladders.add(source)

    rifts: set[tuple[int, int]] = set()
    for stage in payload["stages"]:
        exit_rift = (int(stage["exitRift"]["x"]), int(stage["exitRift"]["y"]))
        if not (scenery_ids_by_point.get(exit_rift, set()) & rift_ids):
            raise ValueError(f"stage {stage['key']} exit rift is absent from generated scenery")
        rifts.add(exit_rift)
    return ladders, rifts


def draw_dungeon_floor(
    floor: int,
    payload: dict,
    mask: set[tuple[int, int]],
    tile_kinds: dict[tuple[int, int], str],
    boundaries: list[dict],
    scenery: list[dict],
    ladders: set[tuple[int, int]],
    rifts: set[tuple[int, int]],
) -> Image.Image:
    image = Image.new("RGBA", MAP_SIZE, DUNGEON_VOID)
    draw = ImageDraw.Draw(image)

    for x in range(0, MAP_SIZE[0], 144):
        draw.line((x, 0, x, MAP_SIZE[1] - 1), fill=DUNGEON_GRID)
    for y in range(0, MAP_SIZE[1], 144):
        draw.line((0, y, MAP_SIZE[0] - 1, y), fill=DUNGEON_GRID)

    floor_tiles = sorted(
        (tile for tile in mask if tile[1] // FLOOR_HEIGHT == floor),
        key=lambda tile: (tile[1], tile[0]),
    )
    for world_x, world_y in floor_tiles:
        room = tile_kinds.get((world_x, world_y), "room") == "room"
        detailed = (world_x * 17 + world_y * 31) % 11 == 0
        if room:
            color = DUNGEON_ROOM_DETAIL if detailed else DUNGEON_ROOM
        else:
            color = DUNGEON_CORRIDOR_DETAIL if detailed else DUNGEON_CORRIDOR
        draw_tile(draw, world_x, world_y, color)

    boundary_counts = Counter(int(record["id"]) for record in boundaries)
    for record in boundaries:
        _, world_y = loc_point(record)
        if world_y // FLOOR_HEIGHT != floor:
            continue
        color = (
            DUNGEON_WALL_ACCENT
            if boundary_counts[int(record["id"])] <= 64
            else DUNGEON_WALL
        )
        draw_boundary(draw, record, color)

    landmark_points = ladders | rifts
    for record in scenery:
        world_x, world_y = loc_point(record)
        if world_y // FLOOR_HEIGHT != floor or (world_x, world_y) in landmark_points:
            continue
        center_x, center_y = png_point(world_x, world_y)
        draw.point((center_x, center_y), fill=DUNGEON_SCENERY_MARK)

    occupied: list[tuple[int, int, int, int]] = []
    title_font = dungeon_font(14)
    landmark_font = dungeon_font(9)
    stages = sorted(
        (stage for stage in payload["stages"] if int(stage["floor"]) == floor),
        key=lambda stage: (int(stage["bounds"]["minX"]), int(stage["id"])),
    )
    for stage in stages:
        draw_stage_title(draw, stage, title_font, occupied)

    for world_x, world_y in sorted(rifts, key=lambda point: (point[1], point[0])):
        if world_y // FLOOR_HEIGHT == floor:
            draw_landmark(draw, world_x, world_y, "rift", landmark_font, occupied)
    for world_x, world_y in sorted(ladders, key=lambda point: (point[1], point[0])):
        if world_y // FLOOR_HEIGHT == floor:
            draw_landmark(draw, world_x, world_y, "ladder", landmark_font, occupied)
    return image


def bake_underground_planes() -> None:
    payload = load_json(DUNGEON_FLOOR)
    boundaries = load_json(DUNGEON_BOUNDARIES)["boundaries"]
    scenery = load_json(DUNGEON_SCENERY)["sceneries"]
    mask, tile_kinds = dungeon_projection(payload)
    ladders, rifts = dungeon_landmarks(payload, scenery)

    for floor in UNDERGROUND_FLOORS:
        plane = draw_dungeon_floor(
            floor, payload, mask, tile_kinds, boundaries, scenery, ladders, rifts
        )
        output = WORLDMAP / f"plane-{floor}.png"
        plane.save(output, format="PNG", optimize=False, compress_level=9)
        tile_count = sum(1 for _, world_y in mask if world_y // FLOOR_HEIGHT == floor)
        stage_count = sum(1 for stage in payload["stages"] if int(stage["floor"]) == floor)
        print(
            f"Generated {output.relative_to(REPO_ROOT)} "
            f"({stage_count} stages, {tile_count} floor tiles)"
        )


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
    bake_underground_planes()
    print(f"Baked Voidscape overlays into {PLANE_0.relative_to(REPO_ROOT)}")
    print(f"Updated {LABELS.relative_to(REPO_ROOT)} and {POINTS.relative_to(REPO_ROOT)}")


if __name__ == "__main__":
    main()
