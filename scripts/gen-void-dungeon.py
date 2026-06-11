#!/usr/bin/env python3
"""gen-void-dungeon.py — generate the Void Dungeon layout (boundaries, scenery, NPC spawns, floor mask).

The Void Dungeon is a SHARED, persistent multi-room dungeon on the black-void underground (Floor 3).
It is built entirely from server-side JSON rendered by already-existing client defs (void walls 214/215,
rift 1306, mobs 854-860) plus a dark-floor landscape patch — see scripts/patch-void-enclave-landscape.py,
which reads the floor mask this script emits.

Layout = a set of compact ROOMS (rectangles) joined by CORRIDORS (rectangles). The walkable area is
their union; walls are auto-placed on every edge between a walkable and a non-walkable tile, so
doorways form automatically wherever a corridor meets a room. Players enter from an unsafe
Wilderness rift, land in a short dungeon vestibule, and fight a tight south->north difficulty
gradient of dense void-mob packs up to the Void Demon boss in the Maw of the Void.

Stays within Floor-3 sectors h3x49y42/43/44/45 (sectionX 49 => worldX 48..95; sectionY 42..45 =>
worldY 3072..3263), which already exist in the landscape archive and resolve as Wilderness.

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

# --- Rooms (x0,y0,x1,y1 inclusive) — compact descent, south (high Y) -> north (low Y, boss) ----------
ROOMS = {
    "threshold":     (66, 3246, 78, 3256),  # calm landing/exit vestibule, low Wilderness
    "web_cross":     (60, 3229, 84, 3245),  # first money-making room
    "western_den":   (54, 3216, 66, 3228),  # low-risk side pocket
    "eastern_den":   (78, 3216, 90, 3228),  # higher-value side pocket
    "void_chamber":  (61, 3202, 83, 3215),  # central caster/brute room
    "maw":           (64, 3189, 80, 3201),  # boss chamber: Void Demon + adds
}
# --- Corridors join the rooms (each overlaps the two rooms it connects) -------------------------------
CORRIDORS = [
    (70, 3242, 74, 3247),   # threshold -> web cross
    (62, 3226, 66, 3231),   # web cross -> western den
    (78, 3226, 82, 3231),   # web cross -> eastern den
    (70, 3212, 74, 3230),   # web cross -> void chamber
    (67, 3198, 77, 3204),   # void chamber -> maw
]

ENTRANCE_ROOM = "threshold"
ARRIVAL = (72, 3252)              # inside the entry vestibule
EXIT_RIFT = (72, 3250)            # dungeon exit rift, centered away from the chatbox edge
WILDERNESS_RIFT = (112, 296)      # unsafe surface entry, just north of the Void Enclave

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
scenery(RIFT, *WILDERNESS_RIFT)
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
    "web_cross":    [("spider", 10)],
    "western_den":  [("wolf", 4), ("unicorn", 2)],
    "eastern_den":  [("ogre", 3), ("giant", 2)],
    "void_chamber": [("wizard", 5), ("knight", 4), ("giant", 2)],
    "maw":          [("demon", 1), ("wizard", 3), ("knight", 2)],
}
# scatter spiders down the lower corridors too, for ambient density without camping the entry rift.
for c in CORRIDORS[1:3]:
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
