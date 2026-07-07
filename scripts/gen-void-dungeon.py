#!/usr/bin/env python3
"""Generate the Void Dungeon layout.

The Void Dungeon is a shared, persistent Wilderness dungeon built from static
location JSON plus a dark-floor landscape mask. This generator is the source of
truth for the dungeon's boundaries, scenery, NPC spawns, and floor mask.

Run: python3 scripts/gen-void-dungeon.py
Then run: python3 scripts/patch-void-enclave-landscape.py
"""
import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LOCS = REPO / "server/conf/server/defs/locs"
FLOOR_MASK = REPO / "server/conf/server/data/void_dungeon_floor.json"

VOID_HIGHWALL = 215
RIFT = 1306
TORCH = 51
PILLAR = 20
LADDER_UP = 5
LADDER_DOWN = 6

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

ENTRY_FLOOR = 3
UPPER_FLOOR = 2


def floor_y(floor, local_y):
    return floor * 944 + local_y


def room(floor, x0, y0, x1, y1):
    return (x0, floor_y(floor, y0), x1, floor_y(floor, y1))


# Entry floor: low/mid mobs by kind, with no combat at the arrival tile.
ROOMS = {
    "entry_vestibule": room(ENTRY_FLOOR, 66, 414, 78, 424),
    "spider_warren": room(ENTRY_FLOOR, 58, 397, 70, 410),
    "spider_nest": room(ENTRY_FLOOR, 72, 397, 86, 410),
    "wolf_den": room(ENTRY_FLOOR, 54, 381, 66, 394),
    "unicorn_grotto": room(ENTRY_FLOOR, 72, 381, 84, 394),
    "lower_stairwell": room(ENTRY_FLOOR, 66, 366, 78, 378),

    # Upper gallery: harder rooms split by mob family.
    "upper_landing": room(UPPER_FLOOR, 66, 414, 78, 424),
    "ogre_pit": room(UPPER_FLOOR, 54, 397, 66, 410),
    "giant_hall": room(UPPER_FLOOR, 78, 397, 90, 410),
    "knight_barracks": room(UPPER_FLOOR, 54, 381, 68, 394),
    "wizard_sanctum": room(UPPER_FLOOR, 72, 381, 88, 394),
    "maw": room(UPPER_FLOOR, 64, 361, 80, 375),
}

CORRIDORS = [
    room(ENTRY_FLOOR, 70, 408, 74, 416),  # entry -> spider rooms
    room(ENTRY_FLOOR, 66, 402, 72, 406),  # spider warren -> spider nest
    room(ENTRY_FLOOR, 60, 392, 64, 399),  # spider warren -> wolf den
    room(ENTRY_FLOOR, 76, 392, 80, 399),  # spider nest -> unicorn grotto
    room(ENTRY_FLOOR, 70, 376, 74, 398),  # mid rooms -> lower stairwell

    room(UPPER_FLOOR, 70, 402, 74, 416),  # upper landing -> brute split
    room(UPPER_FLOOR, 64, 402, 80, 406),  # ogres <-> giants
    room(UPPER_FLOOR, 60, 392, 64, 399),  # ogres -> knights
    room(UPPER_FLOOR, 80, 392, 84, 399),  # giants -> wizards
    room(UPPER_FLOOR, 70, 372, 74, 390),  # upper combat rooms -> maw
]

ARRIVAL = (72, floor_y(ENTRY_FLOOR, 420))
WILDERNESS_RIFT = (112, 296)
LOWER_STAIRS_UP = (72, floor_y(ENTRY_FLOOR, 372))
UPPER_STAIRS_DOWN = (72, floor_y(UPPER_FLOOR, 420))
EXIT_RIFTS = [
    (72, floor_y(ENTRY_FLOOR, 418)),  # entrance fallback
    (68, floor_y(ENTRY_FLOOR, 372)),  # checkpoint before the upper gallery
    (68, floor_y(UPPER_FLOOR, 420)),  # upper landing fallback
    (78, floor_y(UPPER_FLOOR, 363)),  # boss-room emergency exit
]


def rect_tiles(rect):
    x0, y0, x1, y1 = rect
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


walkable = set()
for rect in ROOMS.values():
    walkable |= rect_tiles(rect)
for rect in CORRIDORS:
    walkable |= rect_tiles(rect)

boundaries = []
seen_walls = set()


def wall(x, y, direction):
    key = (x, y, direction)
    if key in seen_walls:
        return
    seen_walls.add(key)
    boundaries.append({"id": VOID_HIGHWALL, "pos": {"X": x, "Y": y}, "direction": direction})


for (x, y) in walkable:
    for nx, ny, wx, wy, direction in (
        (x, y - 1, x, y, 0),
        (x, y + 1, x, y + 1, 0),
        (x - 1, y, x, y, 1),
        (x + 1, y, x + 1, y, 1),
    ):
        if (nx, ny) not in walkable:
            wall(wx, wy, direction)

sceneries = []


def scenery(sid, x, y, direction=0):
    sceneries.append({"id": sid, "pos": {"X": x, "Y": y}, "direction": direction})


scenery(RIFT, *WILDERNESS_RIFT)
for point in EXIT_RIFTS:
    scenery(RIFT, *point)
scenery(LADDER_UP, *LOWER_STAIRS_UP)
scenery(LADDER_DOWN, *UPPER_STAIRS_DOWN)

for name, (x0, y0, x1, y1) in ROOMS.items():
    for tx, ty in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        if (tx, ty) in walkable:
            scenery(TORCH, tx, ty)
    if name in ("spider_nest", "giant_hall", "wizard_sanctum", "maw"):
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for px, py in ((cx - 4, cy), (cx + 4, cy)):
            if (px, py) in walkable:
                scenery(PILLAR, px, py)

npclocs = []


def scatter(room_name, count, step=3):
    x0, y0, x1, y1 = ROOMS[room_name]
    grid = [(x, y) for y in range(y0 + 2, y1 - 1, step) for x in range(x0 + 2, x1 - 1, step)]
    if not grid:
        return [((x0 + x1) // 2, (y0 + y1) // 2)]
    stride = max(1, len(grid) // max(1, count))
    return [grid[(idx * stride) % len(grid)] for idx in range(count)]


def spawn_in_room(npc, room_name, x, y, wander=3):
    x0, y0, x1, y1 = ROOMS[room_name]
    min_x = max(x0 + 1, x - wander)
    min_y = max(y0 + 1, y - wander)
    max_x = min(x1 - 1, x + wander)
    max_y = min(y1 - 1, y + wander)
    npclocs.append({
        "id": NPC[npc],
        "start": {"X": x, "Y": y},
        "min": {"X": min_x, "Y": min_y},
        "max": {"X": max_x, "Y": max_y},
    })


def spawn_pack(room_name, npc, count, wander=3, step=3):
    for x, y in scatter(room_name, count, step):
        spawn_in_room(npc, room_name, x, y, wander)


spawn_pack("spider_warren", "spider", 7, 3)
spawn_pack("spider_nest", "spider", 9, 3)
spawn_pack("wolf_den", "wolf", 6, 3)
spawn_pack("unicorn_grotto", "unicorn", 3, 2)
spawn_pack("ogre_pit", "ogre", 5, 2)
spawn_pack("giant_hall", "giant", 5, 2)
spawn_pack("knight_barracks", "knight", 6, 2)
spawn_pack("wizard_sanctum", "wizard", 7, 3)

for npc, x, y, wander in (
    ("demon", 72, floor_y(UPPER_FLOOR, 365), 2),
    ("wizard", 67, floor_y(UPPER_FLOOR, 370), 2),
    ("wizard", 77, floor_y(UPPER_FLOOR, 370), 2),
    ("knight", 67, floor_y(UPPER_FLOOR, 364), 2),
    ("knight", 77, floor_y(UPPER_FLOOR, 364), 2),
):
    spawn_in_room(npc, "maw", x, y, wander)


def validate():
    for label, point in {
        "arrival": ARRIVAL,
        "lower stairs": LOWER_STAIRS_UP,
        "upper stairs": UPPER_STAIRS_DOWN,
        **{f"exit rift {idx + 1}": point for idx, point in enumerate(EXIT_RIFTS)},
    }.items():
        if point not in walkable:
            raise ValueError(f"{label} {point} is outside the walkable dungeon")

    for loc in npclocs:
        start = (loc["start"]["X"], loc["start"]["Y"])
        if start not in walkable:
            raise ValueError(f"NPC {loc['id']} start {start} is outside the walkable dungeon")
        for x in range(loc["min"]["X"], loc["max"]["X"] + 1):
            for y in range(loc["min"]["Y"], loc["max"]["Y"] + 1):
                if (x, y) not in walkable:
                    raise ValueError(f"NPC {loc['id']} roam tile {(x, y)} is outside the walkable dungeon")

    floors = {}
    for point in walkable:
        floors.setdefault(point[1] // 944, set()).add(point)
    for floor, points in floors.items():
        stack = [next(iter(points))]
        seen = {stack[0]}
        while stack:
            x, y = stack.pop()
            for neighbor in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbor in points and neighbor not in seen:
                    seen.add(neighbor)
                    stack.append(neighbor)
        if seen != points:
            raise ValueError(
                f"floor {floor} has disconnected walkable areas "
                f"({len(seen)} connected tiles of {len(points)})"
            )


def write(name, key, items):
    (LOCS / name).write_text(json.dumps({key: items}, indent=2) + "\n")
    print(f"  {name}: {len(items)} entries")


validate()
print("Void Dungeon (two-floor room redesign):")
write("BoundaryLocsVoidDungeon.json", "boundaries", boundaries)
write("SceneryLocsVoidDungeon.json", "sceneries", sceneries)
write("NpcLocsVoidDungeon.json", "npclocs", npclocs)
FLOOR_MASK.write_text(json.dumps({"tiles": sorted([x, y] for x, y in walkable)}) + "\n")
print(f"  void_dungeon_floor.json: {len(walkable)} floor tiles")
print(f"  rooms={len(ROOMS)} corridors={len(CORRIDORS)} mobs={len(npclocs)} arrival={ARRIVAL}")
