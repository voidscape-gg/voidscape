#!/usr/bin/env python3
"""Generate the three-stage shared Wilderness Void Dungeon map slice.

The authored rectangles below are the source of truth for location JSON, the
landscape floor mask, and VoidDungeonLayout.java. Run this script before
scripts/patch-void-enclave-landscape.py.
"""
import json
import re
import zipfile
from pathlib import Path


REPO = Path(__file__).resolve().parent.parent
LOCS = REPO / "server/conf/server/defs/locs"
FLOOR_MASK = REPO / "server/conf/server/data/void_dungeon_floor.json"
AUTHENTIC_LANDSCAPE = REPO / "server/conf/server/data/Authentic_Landscape.orsc"
JAVA_LAYOUT = (
    REPO
    / "server/src/com/openrsc/server/content/voiddungeon/VoidDungeonLayout.java"
)

FLOOR_HEIGHT = 944

VOID_HIGHWALL = 215
VOID_BASTION = 219
VOID_INNER_WALL = 220
VOID_SIGIL_PANEL = 221
VOID_RIFT_WINDOW = 223

LADDER_UP = 5
LADDER_DOWN = 6
PILLAR = 46
TORCH = 51
VOID_CRYSTAL = 1300
RIFT = 1306

NPC = {
    "knight": 853,
    "spider": 854,
    "giant": 855,
    "wolf": 856,
    "demon": 857,
    "ogre": 858,
    "wizard": 859,
    "unicorn": 860,
}

SURFACE_RIFT = (112, 296)
SURFACE_RETURN = (111, 297)


def floor_y(floor, local_y):
    return floor * FLOOR_HEIGHT + local_y


def layout_rect(name, kind, x0, y0, x1, y1):
    if x0 > x1 or y0 > y1:
        raise ValueError("invalid rectangle {}".format(name))
    return {
        "name": name,
        "kind": kind,
        "local": (x0, y0, x1, y1),
    }


def decoration(scenery_id, x, local_y, direction=0):
    return (scenery_id, x, local_y, direction)


# Every stage owns one connected union of deliberate rooms and corridors.
STAGES = [
    {
        "id": 1,
        "key": "riftworks",
        "java": "RIFTWORKS",
        "name": "Riftworks",
        "floor": 3,
        "bounds": (54, 361, 90, 424),
        "pattern": "riftworks_rubble",
        "wall": VOID_HIGHWALL,
        "rooms": [
            layout_rect("riftworks_landing", "room", 66, 414, 78, 424),
            layout_rect("riftworks_spider_works", "room", 56, 397, 88, 410),
            layout_rect("riftworks_wolf_foundry", "room", 56, 379, 88, 392),
            layout_rect("riftworks_transition", "room", 64, 361, 80, 374),
        ],
        "corridors": [
            layout_rect("riftworks_landing_spine", "corridor", 69, 408, 75, 416),
            layout_rect("riftworks_middle_spine", "corridor", 69, 390, 75, 399),
            layout_rect("riftworks_upper_spine", "corridor", 69, 372, 75, 381),
        ],
        "buffers": ("riftworks_landing", "riftworks_transition"),
        "arrival": (72, 420),
        "exit_rift": (68, 420),
        "transition": (72, 365),
        "boundary_accents": [
            (56, 404, 1, VOID_RIFT_WINDOW),
            (89, 404, 1, VOID_RIFT_WINDOW),
            (56, 386, 1, VOID_RIFT_WINDOW),
            (89, 386, 1, VOID_RIFT_WINDOW),
        ],
        "decorations": [
            decoration(TORCH, 67, 416),
            decoration(TORCH, 77, 416),
            decoration(TORCH, 56, 404),
            decoration(TORCH, 88, 404),
            decoration(TORCH, 56, 386),
            decoration(TORCH, 88, 386),
            decoration(TORCH, 65, 362),
            decoration(TORCH, 79, 362),
            decoration(PILLAR, 66, 397),
            decoration(PILLAR, 78, 379),
            decoration(VOID_CRYSTAL, 64, 366),
            decoration(VOID_CRYSTAL, 80, 366),
        ],
    },
    {
        "id": 2,
        "key": "broken_menagerie",
        "java": "BROKEN_MENAGERIE",
        "name": "Broken Menagerie",
        "floor": 2,
        "bounds": (54, 361, 90, 424),
        "pattern": "menagerie_symmetry",
        "wall": VOID_INNER_WALL,
        "rooms": [
            layout_rect("menagerie_landing", "room", 66, 414, 78, 424),
            layout_rect("menagerie_unicorn_west", "room", 54, 399, 66, 410),
            layout_rect("menagerie_unicorn_east", "room", 78, 399, 90, 410),
            layout_rect("menagerie_ogre_west", "room", 54, 383, 66, 395),
            layout_rect("menagerie_ogre_east", "room", 78, 383, 90, 395),
            layout_rect("menagerie_giant_west", "room", 54, 368, 66, 380),
            layout_rect("menagerie_giant_east", "room", 78, 368, 90, 380),
            layout_rect("menagerie_transition", "room", 66, 361, 78, 367),
        ],
        "corridors": [
            layout_rect("menagerie_spine", "corridor", 70, 365, 74, 416),
            layout_rect("menagerie_unicorn_bridge", "corridor", 64, 403, 80, 406),
            layout_rect("menagerie_ogre_bridge", "corridor", 64, 387, 80, 390),
            layout_rect("menagerie_giant_bridge", "corridor", 64, 372, 80, 375),
        ],
        "buffers": ("menagerie_landing", "menagerie_transition"),
        "arrival": (72, 420),
        "exit_rift": (76, 420),
        "transition": (72, 365),
        "boundary_accents": [
            (54, 404, 1, VOID_SIGIL_PANEL),
            (91, 404, 1, VOID_SIGIL_PANEL),
            (54, 388, 1, VOID_SIGIL_PANEL),
            (91, 388, 1, VOID_SIGIL_PANEL),
            (54, 373, 1, VOID_SIGIL_PANEL),
            (91, 373, 1, VOID_SIGIL_PANEL),
        ],
        "decorations": [
            decoration(TORCH, 67, 416),
            decoration(TORCH, 77, 416),
            decoration(TORCH, 54, 404),
            decoration(TORCH, 90, 404),
            decoration(TORCH, 54, 388),
            decoration(TORCH, 90, 388),
            decoration(TORCH, 54, 373),
            decoration(TORCH, 90, 373),
            decoration(TORCH, 67, 362),
            decoration(TORCH, 77, 362),
            decoration(PILLAR, 60, 399),
            decoration(PILLAR, 84, 399),
            decoration(PILLAR, 60, 368),
            decoration(PILLAR, 84, 368),
        ],
    },
    {
        "id": 3,
        "key": "null_sanctum",
        "java": "NULL_SANCTUM",
        "name": "Null Sanctum",
        "floor": 1,
        "bounds": (54, 361, 90, 424),
        "pattern": "sanctum_processional",
        "wall": VOID_BASTION,
        "rooms": [
            layout_rect("sanctum_landing", "room", 66, 414, 78, 424),
            layout_rect("sanctum_processional_nave", "room", 66, 386, 78, 416),
            layout_rect("sanctum_knight_chapel", "room", 54, 392, 66, 406),
            layout_rect("sanctum_wizard_chapel", "room", 78, 392, 90, 406),
            layout_rect("sanctum_crossing", "room", 60, 378, 84, 390),
            layout_rect("sanctum_demon_seal", "room", 58, 361, 86, 375),
        ],
        "corridors": [
            layout_rect("sanctum_west_transept", "corridor", 64, 396, 70, 400),
            layout_rect("sanctum_east_transept", "corridor", 74, 396, 80, 400),
            layout_rect("sanctum_seal_passage", "corridor", 69, 373, 75, 388),
        ],
        "buffers": ("sanctum_landing",),
        "arrival": (72, 420),
        "exit_rift": (76, 420),
        "transition": None,
        "boundary_accents": [
            (54, 399, 1, VOID_RIFT_WINDOW),
            (91, 399, 1, VOID_RIFT_WINDOW),
            (62, 361, 0, VOID_SIGIL_PANEL),
            (72, 361, 0, VOID_SIGIL_PANEL),
            (82, 361, 0, VOID_SIGIL_PANEL),
        ],
        "decorations": [
            decoration(TORCH, 67, 416),
            decoration(TORCH, 77, 416),
            decoration(TORCH, 54, 399),
            decoration(TORCH, 66, 399),
            decoration(TORCH, 78, 399),
            decoration(TORCH, 90, 399),
            decoration(TORCH, 60, 379),
            decoration(TORCH, 84, 379),
            decoration(PILLAR, 66, 386),
            decoration(PILLAR, 78, 386),
            decoration(VOID_CRYSTAL, 60, 362),
            decoration(VOID_CRYSTAL, 84, 362),
        ],
    },
]

STAGE_BY_KEY = {stage["key"]: stage for stage in STAGES}


def global_rect(stage, rect):
    x0, y0, x1, y1 = rect["local"]
    return (x0, floor_y(stage["floor"], y0), x1, floor_y(stage["floor"], y1))


def global_point(stage, point):
    return (point[0], floor_y(stage["floor"], point[1]))


def rect_tiles(rect):
    x0, y0, x1, y1 = rect
    return {
        (x, y)
        for x in range(x0, x1 + 1)
        for y in range(y0, y1 + 1)
    }


def stage_rectangles(stage):
    return stage["rooms"] + stage["corridors"]


STAGE_TILES = {}
ROOMS = {}
for stage in STAGES:
    tiles = set()
    for rect in stage_rectangles(stage):
        tiles.update(rect_tiles(global_rect(stage, rect)))
        if rect["kind"] == "room":
            if rect["name"] in ROOMS:
                raise ValueError("duplicate room name {}".format(rect["name"]))
            ROOMS[rect["name"]] = (stage["key"], global_rect(stage, rect))
    STAGE_TILES[stage["key"]] = tiles

WALKABLE = set().union(*STAGE_TILES.values())


def stage_at(point):
    matches = [key for key, tiles in STAGE_TILES.items() if point in tiles]
    if len(matches) > 1:
        raise ValueError("tile {} belongs to multiple stages {}".format(point, matches))
    return matches[0] if matches else None


# Exact physical links. Each link emits two directional Transition records and
# one ladder object at each endpoint.
LINKS = [
    {
        "forward_name": "RIFTWORKS_TO_BROKEN_MENAGERIE",
        "reverse_name": "BROKEN_MENAGERIE_TO_RIFTWORKS",
        "source_stage": "riftworks",
        "source": global_point(STAGE_BY_KEY["riftworks"], (72, 365)),
        "target_stage": "broken_menagerie",
        "target": global_point(STAGE_BY_KEY["broken_menagerie"], (72, 420)),
    },
    {
        "forward_name": "BROKEN_MENAGERIE_TO_NULL_SANCTUM",
        "reverse_name": "NULL_SANCTUM_TO_BROKEN_MENAGERIE",
        "source_stage": "broken_menagerie",
        "source": global_point(STAGE_BY_KEY["broken_menagerie"], (72, 365)),
        "target_stage": "null_sanctum",
        "target": global_point(STAGE_BY_KEY["null_sanctum"], (72, 420)),
    },
]

TRANSITIONS = []
for link in LINKS:
    TRANSITIONS.append({
        "name": link["forward_name"],
        "object_id": LADDER_UP,
        "source_stage": link["source_stage"],
        "source": link["source"],
        "target_stage": link["target_stage"],
        "target": link["target"],
    })
    TRANSITIONS.append({
        "name": link["reverse_name"],
        "object_id": LADDER_DOWN,
        "source_stage": link["target_stage"],
        "source": link["target"],
        "target_stage": link["source_stage"],
        "target": link["source"],
    })


boundaries = []
seen_walls = set()
accent_walls = {}
for stage in STAGES:
    for x, local_y, direction, boundary_id in stage["boundary_accents"]:
        key = (stage["key"], x, floor_y(stage["floor"], local_y), direction)
        if key in accent_walls:
            raise ValueError("duplicate boundary accent {}".format(key))
        accent_walls[key] = boundary_id

for stage in STAGES:
    key = stage["key"]
    for x, y in sorted(STAGE_TILES[key]):
        for nx, ny, wx, wy, direction in (
            (x, y - 1, x, y, 0),
            (x, y + 1, x, y + 1, 0),
            (x - 1, y, x, y, 1),
            (x + 1, y, x + 1, y, 1),
        ):
            if (nx, ny) in STAGE_TILES[key]:
                continue
            wall_key = (wx, wy, direction)
            if wall_key in seen_walls:
                continue
            seen_walls.add(wall_key)
            boundary_id = accent_walls.get(
                (key, wx, wy, direction), stage["wall"]
            )
            boundaries.append({
                "id": boundary_id,
                "pos": {"X": wx, "Y": wy},
                "direction": direction,
            })


scenery_records = []


def add_scenery(scenery_id, x, y, direction=0, stage_key=None, role="decoration"):
    scenery_records.append({
        "stage": stage_key,
        "role": role,
        "json": {
            "id": scenery_id,
            "pos": {"X": x, "Y": y},
            "direction": direction,
        },
    })


add_scenery(RIFT, *SURFACE_RIFT, stage_key=None, role="surface_rift")
for stage in STAGES:
    exit_x, exit_y = global_point(stage, stage["exit_rift"])
    add_scenery(RIFT, exit_x, exit_y, stage_key=stage["key"], role="exit_rift")

for link in LINKS:
    add_scenery(
        LADDER_UP,
        *link["source"],
        stage_key=link["source_stage"],
        role="transition",
    )
    add_scenery(
        LADDER_DOWN,
        *link["target"],
        stage_key=link["target_stage"],
        role="transition",
    )

for stage in STAGES:
    for scenery_id, x, local_y, direction in stage["decorations"]:
        add_scenery(
            scenery_id,
            x,
            floor_y(stage["floor"], local_y),
            direction=direction,
            stage_key=stage["key"],
        )

def room_grid(room_rect, wander, step):
    x0, y0, x1, y1 = room_rect
    xs = list(range(x0 + wander + 1, x1 - wander, step))
    ys = list(range(y0 + wander + 1, y1 - wander, step))
    return [(x, y) for y in ys for x in xs]


def spread_points(room_rect, count, wander, step):
    grid = room_grid(room_rect, wander, step)
    if len(grid) < count:
        raise ValueError(
            "room {} has only {} spawn cells for {} NPCs".format(
                room_rect, len(grid), count
            )
        )
    if count == 1:
        return [grid[len(grid) // 2]]
    indexes = [
        (index * (len(grid) - 1)) // (count - 1)
        for index in range(count)
    ]
    return [grid[index] for index in indexes]


SPAWN_PACKS = [
    ("riftworks", "riftworks_spider_works", "spider", 7, 2, 3),
    ("riftworks", "riftworks_wolf_foundry", "wolf", 5, 2, 3),
    ("broken_menagerie", "menagerie_unicorn_west", "unicorn", 3, 2, 3),
    ("broken_menagerie", "menagerie_unicorn_east", "unicorn", 2, 2, 3),
    ("broken_menagerie", "menagerie_ogre_west", "ogre", 3, 2, 3),
    ("broken_menagerie", "menagerie_ogre_east", "ogre", 2, 2, 3),
    ("broken_menagerie", "menagerie_giant_west", "giant", 3, 2, 3),
    ("broken_menagerie", "menagerie_giant_east", "giant", 2, 2, 3),
    ("null_sanctum", "sanctum_knight_chapel", "knight", 6, 2, 3),
    ("null_sanctum", "sanctum_wizard_chapel", "wizard", 6, 2, 3),
    ("null_sanctum", "sanctum_demon_seal", "demon", 2, 1, 4),
]

npc_records = []
for stage_key, room_name, npc_name, count, wander, step in SPAWN_PACKS:
    room_stage, room_rect = ROOMS[room_name]
    if room_stage != stage_key:
        raise ValueError("spawn pack stage does not own room {}".format(room_name))
    for x, y in spread_points(room_rect, count, wander, step):
        npc_records.append({
            "stage": stage_key,
            "room": room_name,
            "npc": npc_name,
            "json": {
                "id": NPC[npc_name],
                "start": {"X": x, "Y": y},
                "min": {"X": x - wander, "Y": y - wander},
                "max": {"X": x + wander, "Y": y + wander},
            },
        })


def connected_tiles(tiles):
    start = next(iter(tiles))
    seen = {start}
    stack = [start]
    while stack:
        x, y = stack.pop()
        for neighbor in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if neighbor in tiles and neighbor not in seen:
                seen.add(neighbor)
                stack.append(neighbor)
    return seen


def validate_surface_rift_footprint():
    """Prove the 2x2 rift and adjacent return tile sit on open terrain."""
    footprint = {
        (SURFACE_RIFT[0] + dx, SURFACE_RIFT[1] + dy)
        for dx in range(2)
        for dy in range(2)
    }
    if SURFACE_RETURN in footprint:
        raise ValueError("surface return overlaps the blocking rift footprint")
    if min(
        max(abs(SURFACE_RETURN[0] - x), abs(SURFACE_RETURN[1] - y))
        for x, y in footprint
    ) != 1:
        raise ValueError("surface return must be adjacent to the rift footprint")

    sectors = {}
    with zipfile.ZipFile(AUTHENTIC_LANDSCAPE) as archive:
        for x, y in sorted(footprint | {SURFACE_RETURN}):
            sector_name = "h{}x{}y{}".format(
                y // FLOOR_HEIGHT,
                x // 48 + 48,
                (y % FLOOR_HEIGHT) // 48 + 37,
            )
            if sector_name not in sectors:
                sectors[sector_name] = archive.read(sector_name)
            base_x = (x // 48) * 48
            base_y = (y // FLOOR_HEIGHT) * FLOOR_HEIGHT + ((y % FLOOR_HEIGHT) // 48) * 48
            offset = ((x - base_x) * 48 + (y - base_y)) * 10
            tile = sectors[sector_name][offset:offset + 10]
            if tile[2] != 0 or any(tile[4:10]):
                raise ValueError(
                    "surface rift/return tile {} is not open authentic terrain".format(
                        (x, y)
                    )
                )


def npc_roam_tiles(loc):
    return {
        (x, y)
        for x in range(loc["min"]["X"], loc["max"]["X"] + 1)
        for y in range(loc["min"]["Y"], loc["max"]["Y"] + 1)
    }


def java_source():
    lines = [
        "package com.openrsc.server.content.voiddungeon;",
        "",
        "/**",
        " * Generated authoritative coordinate policy for the shared Void Dungeon.",
        " * Regenerate with scripts/gen-void-dungeon.py; do not edit by hand.",
        " */",
        "public final class VoidDungeonLayout {",
        "\tpublic static final int STAGE_NONE = 0;",
    ]
    for stage in STAGES:
        lines.append(
            "\tpublic static final int STAGE_{} = {};".format(
                stage["java"], stage["id"]
            )
        )

    lines.extend([
        "",
        "\tpublic static final int VOID_RIFT_ID = {};".format(RIFT),
        "\tpublic static final int LADDER_UP_ID = {};".format(LADDER_UP),
        "\tpublic static final int LADDER_DOWN_ID = {};".format(LADDER_DOWN),
        "",
        "\tpublic static final int SURFACE_RIFT_X = {};".format(SURFACE_RIFT[0]),
        "\tpublic static final int SURFACE_RIFT_Y = {};".format(SURFACE_RIFT[1]),
        "\tpublic static final int SURFACE_RETURN_X = {};".format(SURFACE_RETURN[0]),
        "\tpublic static final int SURFACE_RETURN_Y = {};".format(SURFACE_RETURN[1]),
    ])

    for stage in STAGES:
        prefix = stage["java"]
        arrival = global_point(stage, stage["arrival"])
        exit_rift = global_point(stage, stage["exit_rift"])
        lines.extend([
            "",
            "\tpublic static final int {}_FLOOR = {};".format(prefix, stage["floor"]),
            "\tpublic static final int {}_ARRIVAL_X = {};".format(prefix, arrival[0]),
            "\tpublic static final int {}_ARRIVAL_Y = {};".format(prefix, arrival[1]),
            "\tpublic static final int {}_EXIT_RIFT_X = {};".format(prefix, exit_rift[0]),
            "\tpublic static final int {}_EXIT_RIFT_Y = {};".format(prefix, exit_rift[1]),
        ])
        if stage["transition"] is not None:
            transition = global_point(stage, stage["transition"])
            lines.extend([
                "\tpublic static final int {}_TRANSITION_X = {};".format(prefix, transition[0]),
                "\tpublic static final int {}_TRANSITION_Y = {};".format(prefix, transition[1]),
            ])

    for stage in STAGES:
        lines.extend([
            "",
            "\tprivate static final int[][] {}_RECTS = {{".format(stage["java"]),
        ])
        for rect in stage_rectangles(stage):
            x0, y0, x1, y1 = global_rect(stage, rect)
            lines.append(
                "\t\t{{{}, {}, {}, {}}}, // {}: {}".format(
                    x0, y0, x1, y1, rect["kind"], rect["name"]
                )
            )
        lines.append("\t};")

    lines.append("")
    for transition in TRANSITIONS:
        source_stage = STAGE_BY_KEY[transition["source_stage"]]
        target_stage = STAGE_BY_KEY[transition["target_stage"]]
        source_x, source_y = transition["source"]
        target_x, target_y = transition["target"]
        lines.append(
            "\tpublic static final Transition {} = new Transition({}, STAGE_{}, {}, {}, STAGE_{}, {}, {});".format(
                transition["name"],
                transition["object_id"],
                source_stage["java"],
                source_x,
                source_y,
                target_stage["java"],
                target_x,
                target_y,
            )
        )

    lines.extend([
        "",
        "\tprivate static final Transition[] ALL_TRANSITIONS = {",
    ])
    for transition in TRANSITIONS:
        lines.append("\t\t{},".format(transition["name"]))
    lines.extend([
        "\t};",
        "",
        "\tprivate VoidDungeonLayout() {",
        "\t}",
        "",
        "\tpublic static boolean contains(int x, int y) {",
        "\t\treturn stageAt(x, y) != STAGE_NONE;",
        "\t}",
        "",
        "\tpublic static int stageAt(int x, int y) {",
    ])
    for stage in STAGES:
        lines.extend([
            "\t\tif (inRectangles(x, y, {}_RECTS)) {{".format(stage["java"]),
            "\t\t\treturn STAGE_{};".format(stage["java"]),
            "\t\t}",
        ])
    lines.extend([
        "\t\treturn STAGE_NONE;",
        "\t}",
        "",
        "\tpublic static String stageName(int stage) {",
        "\t\tswitch (stage) {",
    ])
    for stage in STAGES:
        lines.extend([
            "\t\t\tcase STAGE_{}:".format(stage["java"]),
            "\t\t\t\treturn \"{}\";".format(stage["name"]),
        ])
    lines.extend([
        "\t\t\tdefault:",
        "\t\t\t\treturn null;",
        "\t\t}",
        "\t}",
        "",
        "\tpublic static boolean isExitRift(int x, int y) {",
    ])
    exit_conditions = []
    for stage in STAGES:
        prefix = stage["java"]
        exit_conditions.append(
            "(x == {}_EXIT_RIFT_X && y == {}_EXIT_RIFT_Y)".format(prefix, prefix)
        )
    lines.append("\t\treturn {};".format("\n\t\t\t|| ".join(exit_conditions)))
    lines.extend([
        "\t}",
        "",
        "\tpublic static Transition transitionAt(int objectId, int x, int y) {",
        "\t\tfor (Transition transition : ALL_TRANSITIONS) {",
        "\t\t\tif (transition.matches(objectId, x, y)) {",
        "\t\t\t\treturn transition;",
        "\t\t\t}",
        "\t\t}",
        "\t\treturn null;",
        "\t}",
        "",
        "\tpublic static Transition[] getTransitions() {",
        "\t\treturn ALL_TRANSITIONS.clone();",
        "\t}",
        "",
        "\tprivate static boolean inRectangles(int x, int y, int[][] rectangles) {",
        "\t\tfor (int[] rectangle : rectangles) {",
        "\t\t\tif (x >= rectangle[0] && x <= rectangle[2]",
        "\t\t\t\t&& y >= rectangle[1] && y <= rectangle[3]) {",
        "\t\t\t\treturn true;",
        "\t\t\t}",
        "\t\t}",
        "\t\treturn false;",
        "\t}",
        "",
        "\tpublic static final class Transition {",
        "\t\tprivate final int objectId;",
        "\t\tprivate final int sourceStage;",
        "\t\tprivate final int sourceX;",
        "\t\tprivate final int sourceY;",
        "\t\tprivate final int targetStage;",
        "\t\tprivate final int targetX;",
        "\t\tprivate final int targetY;",
        "",
        "\t\tprivate Transition(int objectId, int sourceStage, int sourceX, int sourceY,",
        "\t\t\t\tint targetStage, int targetX, int targetY) {",
        "\t\t\tthis.objectId = objectId;",
        "\t\t\tthis.sourceStage = sourceStage;",
        "\t\t\tthis.sourceX = sourceX;",
        "\t\t\tthis.sourceY = sourceY;",
        "\t\t\tthis.targetStage = targetStage;",
        "\t\t\tthis.targetX = targetX;",
        "\t\t\tthis.targetY = targetY;",
        "\t\t}",
        "",
        "\t\tpublic int getObjectId() { return objectId; }",
        "\t\tpublic int getSourceStage() { return sourceStage; }",
        "\t\tpublic int getSourceX() { return sourceX; }",
        "\t\tpublic int getSourceY() { return sourceY; }",
        "\t\tpublic int getTargetStage() { return targetStage; }",
        "\t\tpublic int getTargetX() { return targetX; }",
        "\t\tpublic int getTargetY() { return targetY; }",
        "",
        "\t\tpublic boolean matches(int candidateObjectId, int x, int y) {",
        "\t\t\treturn objectId == candidateObjectId && sourceX == x && sourceY == y;",
        "\t\t}",
        "\t}",
        "}",
        "",
    ])
    return "\n".join(lines)


def validate(java):
    expected = [
        ("Riftworks", 3, (54, 361, 90, 424)),
        ("Broken Menagerie", 2, (54, 361, 90, 424)),
        ("Null Sanctum", 1, (54, 361, 90, 424)),
    ]
    actual = [(stage["name"], stage["floor"], stage["bounds"]) for stage in STAGES]
    if actual != expected:
        raise ValueError("stage contract changed: {}".format(actual))

    validate_surface_rift_footprint()

    for stage in STAGES:
        key = stage["key"]
        tiles = STAGE_TILES[key]
        if not tiles:
            raise ValueError("stage {} has no walkable tiles".format(stage["name"]))
        seen = connected_tiles(tiles)
        if seen != tiles:
            raise ValueError(
                "stage {} is disconnected ({} of {} tiles)".format(
                    stage["name"], len(seen), len(tiles)
                )
            )
        min_x, min_local_y, max_x, max_local_y = stage["bounds"]
        for x, y in tiles:
            local_y = y - stage["floor"] * FLOOR_HEIGHT
            if not (min_x <= x <= max_x and min_local_y <= local_y <= max_local_y):
                raise ValueError("stage {} tile {} is outside bounds".format(stage["name"], (x, y)))

    for index, stage in enumerate(STAGES):
        for other in STAGES[index + 1:]:
            overlap = STAGE_TILES[stage["key"]] & STAGE_TILES[other["key"]]
            if overlap:
                raise ValueError(
                    "stages {} and {} overlap at {}".format(
                        stage["name"], other["name"], min(overlap)
                    )
                )

    allowed_npcs = {
        "riftworks": {"spider", "wolf"},
        "broken_menagerie": {"unicorn", "ogre", "giant"},
        "null_sanctum": {"knight", "wizard", "demon"},
    }
    all_roam = set()
    for record in npc_records:
        stage_key = record["stage"]
        if record["npc"] not in allowed_npcs[stage_key]:
            raise ValueError("{} cannot spawn in {}".format(record["npc"], stage_key))
        room_stage, room_rect = ROOMS[record["room"]]
        if room_stage != stage_key:
            raise ValueError("NPC room ownership mismatch")
        loc = record["json"]
        start = (loc["start"]["X"], loc["start"]["Y"])
        roam = npc_roam_tiles(loc)
        room_tiles = rect_tiles(room_rect)
        if start not in room_tiles or not roam <= room_tiles:
            raise ValueError(
                "NPC {} leaves declared room {}".format(loc["id"], record["room"])
            )
        if not roam <= STAGE_TILES[stage_key]:
            raise ValueError("NPC {} leaves stage {}".format(loc["id"], stage_key))
        all_roam.update(roam)

    for stage in STAGES:
        buffer_tiles = set()
        for room_name in stage["buffers"]:
            room_stage, room_rect = ROOMS[room_name]
            if room_stage != stage["key"]:
                raise ValueError("buffer room belongs to another stage")
            buffer_tiles.update(rect_tiles(room_rect))
        overlap = buffer_tiles & all_roam
        if overlap:
            raise ValueError(
                "stage {} landing/transition buffer has NPC roam tile {}".format(
                    stage["name"], min(overlap)
                )
            )

    scenery_keys = set()
    for record in scenery_records:
        loc = record["json"]
        point = (loc["pos"]["X"], loc["pos"]["Y"])
        unique = (loc["id"], point[0], point[1])
        if unique in scenery_keys:
            raise ValueError("duplicate scenery {}".format(unique))
        scenery_keys.add(unique)
        if record["stage"] is None:
            if record["role"] != "surface_rift" or point != SURFACE_RIFT:
                raise ValueError("only the fixed surface rift may be outside the dungeon mask")
            continue
        stage_tiles = STAGE_TILES[record["stage"]]
        width, height = (2, 2) if loc["id"] == RIFT else (1, 1)
        footprint = {
            (point[0] + dx, point[1] + dy)
            for dx in range(width)
            for dy in range(height)
        }
        if not footprint <= stage_tiles:
            raise ValueError(
                "scenery {} footprint {} is outside stage {}".format(
                    loc["id"], sorted(footprint), record["stage"]
                )
            )

    transition_sources = {
        (loc["id"], loc["pos"]["X"], loc["pos"]["Y"])
        for record in scenery_records
        for loc in (record["json"],)
        if record["role"] == "transition"
    }
    for transition in TRANSITIONS:
        if stage_at(transition["source"]) != transition["source_stage"]:
            raise ValueError("transition {} source stage mismatch".format(transition["name"]))
        if stage_at(transition["target"]) != transition["target_stage"]:
            raise ValueError("transition {} target stage mismatch".format(transition["name"]))
        source_key = (
            transition["object_id"],
            transition["source"][0],
            transition["source"][1],
        )
        if source_key not in transition_sources:
            raise ValueError("transition {} has no exact scenery source".format(transition["name"]))

    # Validate accent positions against generated perimeter keys directly.
    perimeter_with_stage = set()
    for stage in STAGES:
        key = stage["key"]
        for x, y in STAGE_TILES[key]:
            for nx, ny, wx, wy, direction in (
                (x, y - 1, x, y, 0),
                (x, y + 1, x, y + 1, 0),
                (x - 1, y, x, y, 1),
                (x + 1, y, x + 1, y, 1),
            ):
                if (nx, ny) not in STAGE_TILES[key]:
                    perimeter_with_stage.add((key, wx, wy, direction))
    missing_accents = set(accent_walls) - perimeter_with_stage
    if missing_accents:
        raise ValueError("boundary accents are not on perimeter: {}".format(sorted(missing_accents)))

    # Parse the emitted Java rectangle rows, rather than merely trusting the
    # Python source structures, and compare their exact union to the flat mask.
    java_rects = [
        tuple(map(int, match))
        for match in re.findall(
            r"^\s*\{(\d+), (\d+), (\d+), (\d+)\}, // (?:room|corridor):",
            java,
            flags=re.MULTILINE,
        )
    ]
    expected_rect_count = sum(len(stage_rectangles(stage)) for stage in STAGES)
    if len(java_rects) != expected_rect_count:
        raise ValueError(
            "parsed {} Java rectangles, expected {}".format(
                len(java_rects), expected_rect_count
            )
        )
    java_tiles = set()
    for rect in java_rects:
        java_tiles.update(rect_tiles(rect))
    if java_tiles != WALKABLE:
        raise ValueError(
            "Java rectangles differ from floor mask (missing {}, extra {})".format(
                len(WALKABLE - java_tiles), len(java_tiles - WALKABLE)
            )
        )


def stage_metadata(stage):
    min_x, min_local_y, max_x, max_local_y = stage["bounds"]
    rectangles = []
    for rect in stage_rectangles(stage):
        x0, y0, x1, y1 = global_rect(stage, rect)
        _, ly0, _, ly1 = rect["local"]
        rectangles.append({
            "name": rect["name"],
            "kind": rect["kind"],
            "minX": x0,
            "minY": y0,
            "maxX": x1,
            "maxY": y1,
            "minLocalY": ly0,
            "maxLocalY": ly1,
        })
    metadata = {
        "id": stage["id"],
        "key": stage["key"],
        "constant": "STAGE_{}".format(stage["java"]),
        "name": stage["name"],
        "floor": stage["floor"],
        "pattern": stage["pattern"],
        "bounds": {
            "minX": min_x,
            "maxX": max_x,
            "minLocalY": min_local_y,
            "maxLocalY": max_local_y,
            "minY": floor_y(stage["floor"], min_local_y),
            "maxY": floor_y(stage["floor"], max_local_y),
        },
        "arrival": dict(zip(("x", "y"), global_point(stage, stage["arrival"]))),
        "exitRift": dict(zip(("x", "y"), global_point(stage, stage["exit_rift"]))),
        "tileCount": len(STAGE_TILES[stage["key"]]),
        "rectangles": rectangles,
    }
    if stage["transition"] is not None:
        metadata["outgoingTransition"] = dict(
            zip(("x", "y"), global_point(stage, stage["transition"]))
        )
    return metadata


def write_json(path, key, items):
    path.write_text(json.dumps({key: items}, indent=2) + "\n", encoding="ascii")
    print("  {}: {} entries".format(path.name, len(items)))


def main():
    java = java_source()
    validate(java)

    write_json(LOCS / "BoundaryLocsVoidDungeon.json", "boundaries", boundaries)
    write_json(
        LOCS / "SceneryLocsVoidDungeon.json",
        "sceneries",
        [record["json"] for record in scenery_records],
    )
    write_json(
        LOCS / "NpcLocsVoidDungeon.json",
        "npclocs",
        [record["json"] for record in npc_records],
    )

    floor_payload = {
        "schemaVersion": 2,
        "surfaceRift": {"x": SURFACE_RIFT[0], "y": SURFACE_RIFT[1]},
        "stages": [stage_metadata(stage) for stage in STAGES],
        "transitions": [
            {
                "name": transition["name"],
                "objectId": transition["object_id"],
                "sourceStage": transition["source_stage"],
                "source": {"x": transition["source"][0], "y": transition["source"][1]},
                "targetStage": transition["target_stage"],
                "target": {"x": transition["target"][0], "y": transition["target"][1]},
            }
            for transition in TRANSITIONS
        ],
        "tiles": sorted([x, y] for x, y in WALKABLE),
    }
    FLOOR_MASK.write_text(json.dumps(floor_payload) + "\n", encoding="ascii")
    JAVA_LAYOUT.parent.mkdir(parents=True, exist_ok=True)
    JAVA_LAYOUT.write_text(java, encoding="ascii")

    print("Void Dungeon (three-stage generated map slice):")
    print("  void_dungeon_floor.json: {} floor tiles".format(len(WALKABLE)))
    print("  VoidDungeonLayout.java: {} exact rectangles".format(
        sum(len(stage_rectangles(stage)) for stage in STAGES)
    ))
    for stage in STAGES:
        arrival = global_point(stage, stage["arrival"])
        exit_rift = global_point(stage, stage["exit_rift"])
        transition = (
            global_point(stage, stage["transition"])
            if stage["transition"] is not None
            else None
        )
        print(
            "  stage {} {}: floor={} tiles={} arrival={} exit={} transition={}".format(
                stage["id"],
                stage["name"],
                stage["floor"],
                len(STAGE_TILES[stage["key"]]),
                arrival,
                exit_rift,
                transition,
            )
        )
    print(
        "  boundaries={} scenery={} mobs={} transitions={}".format(
            len(boundaries),
            len(scenery_records),
            len(npc_records),
            len(TRANSITIONS),
        )
    )
    print("  validation: connected, disjoint, exact policy mask, NPC/scenery buffers OK")


if __name__ == "__main__":
    main()
