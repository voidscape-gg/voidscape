#!/usr/bin/env python3
"""gen-void-dungeon.py — generate the Void Dungeon layout (boundaries, scenery, NPC spawns, floor mask).

The Void Dungeon is a SHARED, persistent multi-room dungeon on the black-void underground (Floor 2).
It is built entirely from server-side JSON rendered by already-existing client defs (void walls 214/215,
rift 1306, mobs 854-860) plus a dark-floor landscape patch — see scripts/patch-void-enclave-landscape.py,
which reads the floor mask this script emits.

Layout = a set of ROOMS (rectangles) joined by CORRIDORS (rectangles). The walkable area is their union;
walls are auto-placed on every edge between a walkable and a non-walkable tile, so doorways form
automatically wherever a corridor meets a room. Players teleport in at the south entrance and fight a
south->north difficulty gradient of dense void-mob packs up to the Void Demon boss in the Maw of the Void.

Stays within Floor-2 sectors h2x49y48/49/50 (sectionX 49 => worldX 48..95; sectionY 48..50 =>
worldY 2416..2559), which already exist in the landscape archive.

Run:  python3 scripts/gen-void-dungeon.py   (then re-run the landscape patcher for the floor)
"""
import json
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
LOCS = REPO / "server/conf/server/defs/locs"
FLOOR_MASK = REPO / "server/conf/server/data/void_dungeon_floor.json"

VOID_WALL = 214       # inner obsidian void wall
VOID_HIGHWALL = 215   # tall void wall (perimeter)
RIFT = 1306           # void rift (entry/exit portal)
TORCH = 51            # torch (light)
PILLAR = 20           # stone post / pillar

NPC = {"spider": 854, "giant": 855, "wolf": 856, "demon": 857,
       "ogre": 858, "wizard": 859, "unicorn": 860, "knight": 853}

# --- Rooms (x0,y0,x1,y1 inclusive) — a winding descent, south (high Y) -> north (low Y, boss) ---------
ROOMS = {
    "entrance":      (64, 2546, 80, 2557),  # arrival hall (south)
    "spider_warren": (50, 2516, 92, 2544),  # big swarm room
    "spider_nest":   (50, 2517, 62, 2533),  # (west alcove, overlaps warren edge)
    "howling_den":   (52, 2488, 80, 2513),  # wolves
    "beast_pit":     (80, 2490, 93, 2510),  # east branch: wolves + unicorns
    "brute_hall":    (52, 2456, 90, 2486),  # ogres + giants
    "giants_rest":   (50, 2458, 62, 2478),  # west branch: giants
    "void_sanctum":  (54, 2432, 86, 2454),  # wizards + knights
    "antechamber":   (62, 2424, 78, 2433),  # short hall before the boss
    "maw":           (50, 2406, 90, 2423),  # boss chamber: Void Demon + adds
}
# --- Corridors join the rooms (each overlaps the two rooms it connects) -------------------------------
CORRIDORS = [
    (70, 2543, 74, 2547),   # entrance -> warren
    (64, 2512, 68, 2518),   # warren -> howling den
    (78, 2498, 82, 2502),   # howling den -> beast pit
    (60, 2485, 64, 2490),   # howling den -> brute hall
    (54, 2476, 58, 2488),   # brute hall <-> giants rest
    (68, 2453, 72, 2458),   # brute hall -> sanctum
    (68, 2431, 72, 2435),   # sanctum -> antechamber
    (66, 2421, 74, 2426),   # antechamber -> maw
]

ENTRANCE_ROOM = "entrance"
ARRIVAL = (72, 2552)            # inside the entrance hall
EXIT_RIFT = (72, 2554)          # dungeon exit rift
ENCLAVE_RIFT = (108, 322)       # entry rift in the Void Enclave
ENCLAVE_RETURN = (108, 323)

# --- Build the walkable tile set ---------------------------------------------------------------------
def rect_tiles(r):
    x0, y0, x1, y1 = r
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}

walkable = set()
for r in ROOMS.values():
    walkable |= rect_tiles(r)
for c in CORRIDORS:
    walkable |= rect_tiles(c)

# --- Walls: every edge between a walkable and a non-walkable tile (doorways form automatically) -------
boundaries = []
seen_walls = set()
def wall(x, y, direction, wid):
    key = (x, y, direction)
    if key in seen_walls:
        return
    seen_walls.add(key)
    boundaries.append({"id": wid, "pos": {"X": x, "Y": y}, "direction": direction})

# perimeter (touches the void) gets the tall highwall; interior edges (shouldn't occur for a single
# blob, but harmless) get the normal wall.
for (x, y) in walkable:
    for nx, ny, wx, wy, d in (
        (x, y - 1, x, y, 0),
        (x, y + 1, x, y + 1, 0),
        (x - 1, y, x, y, 1),
        (x + 1, y, x + 1, y, 1),
    ):
        if (nx, ny) not in walkable:
            wall(wx, wy, d, VOID_HIGHWALL)

# --- Scenery: rifts, torches (ring each room), pillars (room centres) --------------------------------
sceneries = []
def scenery(sid, x, y, direction=0):
    sceneries.append({"id": sid, "pos": {"X": x, "Y": y}, "direction": direction})

scenery(RIFT, *EXIT_RIFT)
scenery(RIFT, *ENCLAVE_RIFT)
for name, (x0, y0, x1, y1) in ROOMS.items():
    # torches at the four inner corners of each room
    for tx, ty in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):
        if (tx, ty) in walkable:
            scenery(TORCH, tx, ty)
    # a couple of pillars in the larger rooms
    if (x1 - x0) >= 24 and (y1 - y0) >= 18:
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        for px, py in ((cx - 7, cy), (cx + 7, cy)):
            if (px, py) in walkable:
                scenery(PILLAR, px, py)

# --- Spawns: dense packs scattered through each room -------------------------------------------------
npclocs = []
def spawn(npc, x, y, wander=3):
    npclocs.append({"id": NPC[npc], "start": {"X": x, "Y": y},
                    "min": {"X": x - wander, "Y": y - wander},
                    "max": {"X": x + wander, "Y": y + wander}})

def scatter(room, count, step=3):
    """Deterministic spread of `count` points across a room's interior."""
    x0, y0, x1, y1 = room
    grid = [(x, y) for y in range(y0 + 2, y1 - 1, step) for x in range(x0 + 2, x1 - 1, step)]
    if not grid:
        grid = [((x0 + x1) // 2, (y0 + y1) // 2)]
    # stride through the grid so points spread out even when count < len(grid)
    stride = max(1, len(grid) // max(1, count))
    return [grid[(k * stride) % len(grid)] for k in range(count)]

# (room, [(mob, count), ...])  — dense, tiered
PACKS = {
    "spider_warren": [("spider", 18)],
    "spider_nest":   [("spider", 6)],
    "howling_den":   [("wolf", 10), ("unicorn", 2)],
    "beast_pit":     [("wolf", 6), ("unicorn", 4)],
    "brute_hall":    [("ogre", 7), ("giant", 4)],
    "giants_rest":   [("giant", 4)],
    "void_sanctum":  [("wizard", 7), ("knight", 5)],
    "maw":           [("demon", 2), ("wizard", 5), ("knight", 3)],
}
# scatter spiders down the corridors too, for ambient density
for c in CORRIDORS[:3]:
    cx, cy = (c[0] + c[2]) // 2, (c[1] + c[3]) // 2
    spawn("spider", cx, cy, 1)

for room, packs in PACKS.items():
    r = ROOMS[room]
    for mob, count in packs:
        for (x, y) in scatter(r, count):
            spawn(mob, x, y, 2 if mob in ("giant", "demon", "ogre") else 3)

# --- Write files -------------------------------------------------------------------------------------
def write(name, key, items):
    (LOCS / name).write_text(json.dumps({key: items}, indent=2) + "\n")
    print(f"  {name}: {len(items)} entries")

print("Void Dungeon (multi-room):")
write("BoundaryLocsVoidDungeon.json", "boundaries", boundaries)
write("SceneryLocsVoidDungeon.json", "sceneries", sceneries)
write("NpcLocsVoidDungeon.json", "npclocs", npclocs)
FLOOR_MASK.write_text(json.dumps({"tiles": sorted([x, y] for x, y in walkable)}) + "\n")
print(f"  void_dungeon_floor.json: {len(walkable)} floor tiles")
print(f"  rooms={len(ROOMS)} corridors={len(CORRIDORS)} mobs={len(npclocs)} arrival={ARRIVAL}")
