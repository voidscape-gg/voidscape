#!/usr/bin/env python3
"""
Patch Custom_Landscape.orsc with Voidscape-specific terrain baked directly into
landscape tile bytes.

Why this exists: walls registered via BoundaryLocs*.json are runtime-only — they
collide and render in 3D, but they don't appear on the minimap and the engine
won't draw a roof above them. Both of those are read from the .orsc tile bytes
(see World.java:825 for minimap, Tile.java for the byte layout). To get a "real"
building experience we have to bake the geometry into the landscape archive.

This script is idempotent: it always reads the enclave's source sector from
Authentic_Landscape.orsc, applies patches, and writes to Custom_Landscape.orsc.
Other sectors in Custom_Landscape (OpenRSC's 7 custom additions) are preserved.

Tile byte layout (per server/src/com/openrsc/server/io/Tile.java):
    byte 0: groundElevation
    byte 1: groundTexture
    byte 2: groundOverlay
    byte 3: roofTexture
    byte 4: horizontalWall  (DoorDefId + 1, 0 = no wall)
    byte 5: verticalWall    (DoorDefId + 1, 0 = no wall)
    bytes 6-9: diagonalWalls (4-byte big-endian int)

Sector layout (per Sector.java:52): tile_index = x * 48 + y (column-major).

Coordinate transform: the enclave centered at worldX=113, worldY=315 fits
entirely in sector h0x50y43 which covers worldX=96..143, worldY=288..335.
Local tile coords: tx = worldX - 96, ty = worldY - 288.
"""

import io
import json
import shutil
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
AUTHENTIC = REPO / "server/conf/server/data/Authentic_Landscape.orsc"
CUSTOM    = REPO / "server/conf/server/data/Custom_Landscape.orsc"
# Client reads its own copy when S_WANT_CUSTOM_LANDSCAPE is true (World.java:80-81).
CLIENT_CUSTOM = REPO / "Client_Base/Cache/video/Custom_Landscape.orsc"
ENCLAVE_SECTOR = "h0x50y43"
ENCLAVE_SECTOR_BASE_X = 96    # worldX of local tx=0
ENCLAVE_SECTOR_BASE_Y = 288   # worldY of local ty=0
LEGACY_ENCLAVE_SECTORS = ("h0x52y44",)

VOID_ISLAND_SECTOR = "h0x48y37"
VOID_ISLAND_SECTOR_BASE_X = 0
VOID_ISLAND_SECTOR_BASE_Y = 0
VOID_ISLAND_CENTER_X = 24
VOID_ISLAND_CENTER_Y = 24
VOID_ISLAND_INTRO_CENTER_X = 24
VOID_ISLAND_INTRO_CENTER_Y = 37
LEGACY_VOID_ISLAND_SECTORS = ("h0x63y55",)

# Tutorial Isle: linear gated newbie area in the same ocean sector, west of
# Void Island (2-tile water channels on both sides; ocean is the perimeter).
# Three chambers south-to-north joined at the guide lane (x=40):
#   C1 "The Landing" (camp) -> gate y=31 -> C2 "The Ring" (spar) -> gate y=23
#   -> C3 "The Scar" (ambush). Gate gaps at the lane stay physically open;
# passage used to be script-gated by the retired guided onboarding runtime.
TUTORIAL_ISLE_LANE_X = 40
TUTORIAL_ISLE_RING_Y = 27
TUTORIAL_ISLE_PAD_Y = 37
TUTORIAL_ISLE_C1 = (34, 45, 31, 40)  # (minX, maxX, minY, maxY) "The Landing"
TUTORIAL_ISLE_C2 = (34, 45, 23, 30)  # "The Ring"
TUTORIAL_ISLE_C3 = (37, 43, 18, 22)  # "The Scar"
TUTORIAL_ISLE_GATE1_Y = 31
TUTORIAL_ISLE_GATE2_Y = 23

CATCHSIM_SECTORS = (
    # (sector name, sector base X, sector base Y, arena X offset, arena Y offset)
    ("h0x55y38", 336, 48, 0, 0),
    ("h0x56y38", 384, 48, 48, 0),
    ("h0x57y38", 432, 48, 96, 0),
)
CATCHSIM_ISLAND_MIN_X, CATCHSIM_ISLAND_MAX_X = 340, 382
CATCHSIM_ISLAND_MIN_Y, CATCHSIM_ISLAND_MAX_Y = 52, 90
CATCHSIM_ISLAND_CENTER_X = 361
CATCHSIM_ISLAND_CENTER_Y = 71
CATCHSIM_ISLAND_RADIUS_X = 21.0
CATCHSIM_ISLAND_RADIUS_Y = 19.0

DEATHMATCH_SECTOR = "h0x68y50"
DEATHMATCH_SECTOR_BASE_X = 960
DEATHMATCH_SECTOR_BASE_Y = 624
DEATHMATCH_ARENA_MIN_X, DEATHMATCH_ARENA_MAX_X = 976, 992
DEATHMATCH_ARENA_MIN_Y, DEATHMATCH_ARENA_MAX_Y = 635, 655
DEATHMATCH_CENTER_X = 984
DEATHMATCH_CENTER_Y = 645
DEATHMATCH_BASEMENT_MIN_X, DEATHMATCH_BASEMENT_MAX_X = 976, 992
DEATHMATCH_BASEMENT_MIN_Y, DEATHMATCH_BASEMENT_MAX_Y = 662, 670
DEATHMATCH_BASEMENT_CENTER_X = 984
DEATHMATCH_BASEMENT_CENTER_Y = 666
LEGACY_DEATHMATCH_SECTORS = ("h0x54y38", "h0x54y57")

COLOSSUS_SECTOR = "h0x59y38"
COLOSSUS_SECTOR_BASE_X = 528
COLOSSUS_SECTOR_BASE_Y = 48
COLOSSUS_CENTER_X = 552
COLOSSUS_CENTER_Y = 71
COLOSSUS_PLAZA_RADIUS = 14.0
# Portal landing on the south rim of the plaza; boss stands at center.
COLOSSUS_LANDING_X = 552
COLOSSUS_LANDING_Y = 82

VOIDRUSH_SECTOR = "h0x58y38"
VOIDRUSH_SECTOR_BASE_X = 480
VOIDRUSH_SECTOR_BASE_Y = 48
VOIDRUSH_PAD_MIN_X, VOIDRUSH_PAD_MAX_X = 486, 524
VOIDRUSH_PAD_MIN_Y, VOIDRUSH_PAD_MAX_Y = 54, 90
VOIDRUSH_ARENA_MIN_X, VOIDRUSH_ARENA_MAX_X = 488, 522
VOIDRUSH_ARENA_MIN_Y, VOIDRUSH_ARENA_MAX_Y = 56, 86
VOIDRUSH_CENTER_X = 505
VOIDRUSH_CENTER_Y = 71

UNDEAD_SIEGE_SECTOR = "h0x60y39"
UNDEAD_SIEGE_SECTOR_BASE_X = 576
UNDEAD_SIEGE_SECTOR_BASE_Y = 96
UNDEAD_SIEGE_CENTER_X = 600
UNDEAD_SIEGE_CENTER_Y = 120
UNDEAD_SIEGE_ISLAND_RADIUS_X = 21.0
UNDEAD_SIEGE_ISLAND_RADIUS_Y = 20.0
UNDEAD_SIEGE_HOUSE_MIN_X, UNDEAD_SIEGE_HOUSE_MAX_X = 587, 613
UNDEAD_SIEGE_HOUSE_MIN_Y, UNDEAD_SIEGE_HOUSE_MAX_Y = 106, 133

VOIDARENA_SECTOR = "h3x60y38"
VOIDARENA_SECTOR_BASE_X = 576
VOIDARENA_SECTOR_BASE_Y = 2880
VOIDARENA_HALL_MIN_X, VOIDARENA_HALL_MAX_X = 582, 616
VOIDARENA_HALL_MIN_Y, VOIDARENA_HALL_MAX_Y = 2910, 2916
VOIDARENA_BACKDROP_MIN_X = VOIDARENA_SECTOR_BASE_X
VOIDARENA_BACKDROP_MAX_X = VOIDARENA_SECTOR_BASE_X + 47
VOIDARENA_BACKDROP_MIN_Y = VOIDARENA_SECTOR_BASE_Y
VOIDARENA_BACKDROP_MAX_Y = VOIDARENA_SECTOR_BASE_Y + 47
VOIDARENA_CAGES = (
    (584, 2897, 590, 2909),
    (592, 2897, 598, 2909),
    (600, 2897, 606, 2909),
    (608, 2897, 614, 2909),
)
LEGACY_VOIDARENA_SECTORS = ("h0x60y38",)
VOIDARENA_SKY_SECTORS = (
    ("h3x59y37", 528, 2832),
    ("h3x60y37", 576, 2832),
    ("h3x61y37", 624, 2832),
    ("h3x59y38", 528, 2880),
    ("h3x61y38", 624, 2880),
    ("h3x59y39", 528, 2928),
    ("h3x60y39", 576, 2928),
    ("h3x61y39", 624, 2928),
)

# DoorDef IDs
HIGHWALL = 7
HIGH_DOOR = 8
WALL = 0
WINDOW = 3
RAILINGS = 5
DOOR = 2
WEB = 24               # vanilla spider web — CutWeb.java handles cutting (kept for fallback)
TIMBER_WALL = 14
TIMBER_WINDOW = 15
MOSSY_BRICKS = 18
CRUMBLED_WALL = 41
PLANKS_WINDOW = 126
LOW_FENCE = 127
PLANKS_TIMBER = 144
VOID_WALL = 214        # voidbricks-textured sanctum wall (DoorDef slot 214)
VOID_HIGHWALL = 215    # voidouter-textured perimeter wall, tall (DoorDef slot 215)
VOID_SIGIL_WALL = 216  # voidsigilwall accent (DoorDef slot 216) — pentagram mural
VOID_WEB = 217         # purple vanilla-style void web at gates
VOID_WINDOW = 218      # AI-textured stained-glass arched window with pentagram
VOID_V3_BASTION = 219  # generated jagged outer fortress stone, taller than v2 highwall
VOID_V3_WALL = 220     # generated refined inner obsidian brickwork
VOID_V3_SIGIL = 221    # generated ritual/sigil wall panel
VOID_V3_ROOF_EDGE = 222
VOID_V3_WINDOW = 223   # generated arched cyan/violet void window

# Tile texture IDs (empirically found from Authentic_Landscape buildings)
ROOF_STANDARD = 1     # most-common indoor roof texture (4535 tile occurrences)
ROOF_VOID_V3 = 7      # client ElevationDef id 6 -> TextureDef id 63 (voidv3roof)
# Overlay byte = TileDef index + 1.
FLOOR_INDOOR = 26     # Void Floor (TileDef 25): dark charcoal with subtle purple — main floor
FLOOR_RITUAL = 27     # Void Floor Accent (TileDef 26): bright magenta-purple — ritual circle + dots
FLOOR_MID    = 28     # Void Floor Mid (TileDef 27): mid-purple — ring around ritual circle
FLOOR_V3_STONE = 29   # Void V3 generated cracked-stone floor (TileDef 28 -> TextureDef 64)
FLOOR_LAVA = 11       # Stock lava tile (TileDef 10 -> TextureDef 31); used as non-walkable backdrop.
FLOOR_HOUSE = 3       # Stock building floor used by many RSC houses.
FLOOR_HOUSE_DARK = 5  # Stock darker building floor.
FLOOR_DIRT = 13       # Stock walkable dirt/ruins floor.
FLOOR_ASH = 15        # Stock walkable muted ground.
FLOOR_GRAVEL = 16     # Stock walkable stony ground.
FLOOR_SIEGE_PLANKS = 30
FLOOR_SIEGE_MOSSY_STONE = 31

# === Enclave footprint ===
ENCLAVE_MIN_X, ENCLAVE_MAX_X = 98, 128
ENCLAVE_MIN_Y, ENCLAVE_MAX_Y = 300, 330

# === Layout (mirrors what we generate into BoundaryLocsVoidEnclave.json today) ===

def is_enclave_floor(x: int, y: int) -> bool:
    """Stepped octagonal floor mask for the expanded Void Enclave citadel."""
    if not (ENCLAVE_MIN_X <= x <= ENCLAVE_MAX_X and ENCLAVE_MIN_Y <= y <= ENCLAVE_MAX_Y):
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


def enclave_floor_tiles():
    """Return the full set of walkable/courtyard tiles for the enclave."""
    return {
        (x, y)
        for x in range(ENCLAVE_MIN_X, ENCLAVE_MAX_X + 1)
        for y in range(ENCLAVE_MIN_Y, ENCLAVE_MAX_Y + 1)
        if is_enclave_floor(x, y)
    }


def enclave_floor_overlay(x: int, y: int) -> int:
    """Choose restrained district floors and readable circulation inlays."""
    # Small ritual accents identify the altar and both Rift pads without
    # turning the whole courtyard into the old high-contrast purple cross.
    if (y == 305 and 110 <= x <= 116) or (x == 113 and 303 <= y <= 307):
        return FLOOR_RITUAL
    if ((x in (115, 118) and 311 <= y <= 314)
            or (y in (311, 314) and 115 <= x <= 118)):
        return FLOOR_RITUAL
    if ((x in (112, 115) and 320 <= y <= 323)
            or (y in (320, 323) and 112 <= x <= 115)):
        return FLOOR_RITUAL
    if (x, y) in ((112, 314), (114, 314), (112, 316), (114, 316)):
        return FLOOR_RITUAL

    # Mid-tone stone appears only at the four gate aprons. A continuous floor
    # cross reads as oversized geometry at the classic camera scale.
    if ((112 <= x <= 114 and 300 <= y <= 302)
            or (112 <= x <= 114 and 329 <= y <= 330)
            or (98 <= x <= 100 and 314 <= y <= 316)
            or (126 <= x <= 128 and 314 <= y <= 316)):
        return FLOOR_MID

    # Only the surviving roof wings use an indoor floor. Every open court keeps
    # one cracked-stone ground language instead of rectangular floor patches.
    if 303 <= y <= 308 and (107 <= x <= 110 or 117 <= x <= 120):
        return FLOOR_INDOOR
    return FLOOR_V3_STONE


def enclave_roof_tiles():
    """Return the two surviving roof wings over the northern sanctuary."""
    roof = set()
    for min_x, max_x in ((107, 110), (117, 120)):
        for x in range(min_x, max_x + 1):
            for y in range(303, 309):
                roof.add((x, y))
    return roof


def enclave_walls():
    """Yield (worldX, worldY, direction, doorDefId) for every wall in the enclave."""
    walls = []
    seen = set()

    def b(x, y, direction, id_):
        key = (x, y, direction)
        if key in seen:
            return
        seen.add(key)
        walls.append((x, y, direction, id_))

    # Outer perimeter is generated from the stepped floor mask so walls, floor,
    # minimap, and collision always describe the same citadel silhouette.
    # Gate walls are left empty in the landscape: the clickable/cuttable Void
    # Breach boundaries live in BoundaryLocsVoidEnclave.json instead.
    floor = enclave_floor_tiles()
    gates = {
        (113, 300, 0),
        (113, 331, 0),
        (98, 315, 1),
        (129, 315, 1),
    }
    gate_flanks = {
        (112, 300, 0), (114, 300, 0),
        (112, 331, 0), (114, 331, 0),
        (98, 314, 1), (98, 316, 1),
        (129, 314, 1), (129, 316, 1),
    }
    for x, y in sorted(floor):
        for nx, ny, wx, wy, direction in (
            (x, y - 1, x, y, 0),
            (x, y + 1, x, y + 1, 0),
            (x - 1, y, x, y, 1),
            (x + 1, y, x + 1, y, 1),
        ):
            if (nx, ny) not in floor and (wx, wy, direction) not in gates:
                key = (wx, wy, direction)
                surviving_bastion = (
                    (wx <= 103 and wy <= 305)
                    or (wx >= 123 and wy <= 305)
                    or (wx >= 123 and wy >= 325)
                )
                near_gate = (
                    (direction == 0 and wy in (300, 331) and abs(wx - 113) <= 4)
                    or (direction == 1 and wx in (98, 129) and abs(wy - 315) <= 4)
                )
                if key in gate_flanks:
                    wall_id = VOID_V3_SIGIL
                elif surviving_bastion:
                    wall_id = VOID_V3_BASTION
                elif near_gate:
                    wall_id = VOID_V3_WALL
                else:
                    wall_id = VOID_V3_ROOF_EDGE
                b(wx, wy, direction, wall_id)

    # Hall of Oaths. Two roofed wings survive around a broken central aisle;
    # the broad south mouth and fractures in both side walls keep it readable.
    for x in list(range(106, 111)) + list(range(116, 121)):
        b(x, 302, 0, VOID_V3_SIGIL if x in (108, 118) else VOID_V3_WALL)
    for y in range(303, 309):
        if y == 306:
            continue
        b(106, y, 1, VOID_V3_WINDOW if y in (304, 308) else VOID_V3_WALL)
        b(121, y, 1, VOID_V3_WINDOW if y in (304, 308) else VOID_V3_WALL)
    for x in list(range(106, 109)) + list(range(119, 121)):
        b(x, 309, 0, VOID_V3_ROOF_EDGE)

    # Quartermaster Court. Low north/south remnants frame an open yard; the
    # west gate corridor and the whole courtyard-facing east side stay open.
    for x in range(100, 108):
        b(x, 311, 0, VOID_V3_SIGIL if x == 103 else VOID_V3_ROOF_EDGE)
        b(x, 320, 0, VOID_V3_SIGIL if x == 105 else VOID_V3_ROOF_EDGE)
    for y in (311, 312, 319, 320):
        b(100, y, 1, VOID_V3_WINDOW if y in (312, 319) else VOID_V3_ROOF_EDGE)

    # Mend-and-Descent Court. The low arcade leaves both the central approach
    # and the northern two-tile bypass to the east gate unobstructed.
    for x in range(119, 127):
        b(x, 310, 0, VOID_V3_SIGIL if x == 123 else VOID_V3_ROOF_EDGE)
        b(x, 320, 0, VOID_V3_SIGIL if x == 121 else VOID_V3_ROOF_EDGE)
    for y in (311, 312, 319, 320):
        b(127, y, 1, VOID_V3_WINDOW if y in (312, 319) else VOID_V3_ROOF_EDGE)

    # Riftward Yard. Broken low walls frame two full bypasses around the Arena
    # Rift and leave a five-tile opening toward the south gate.
    for x in list(range(107, 110)) + list(range(118, 121)):
        b(x, 323, 0, VOID_V3_ROOF_EDGE)
    for y in range(324, 329):
        if y == 326:
            continue
        b(107, y, 1, VOID_V3_WINDOW if y in (325, 327) else VOID_V3_ROOF_EDGE)
        b(121, y, 1, VOID_V3_WINDOW if y in (325, 327) else VOID_V3_ROOF_EDGE)
    for x in list(range(107, 111)) + list(range(116, 121)):
        b(x, 329, 0, VOID_V3_SIGIL if x in (108, 118) else VOID_V3_ROOF_EDGE)

    return walls


def patch_enclave_sector(sector_bytes: bytes) -> bytes:
    """Apply enclave wall + floor + roof patches to a 23040-byte sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - ENCLAVE_SECTOR_BASE_X
        ty = worldY - ENCLAVE_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {ENCLAVE_SECTOR}")
        return (tx * 48 + ty) * 10

    # 1. Walls
    for worldX, worldY, direction, doorDefId in enclave_walls():
        off = tile_offset(worldX, worldY)
        # Naming surprise: in vanilla data (e.g. Edgeville bank h0x52y46), walls running
        # east-west (north/south building faces) live in the verticalWall byte,
        # walls running north-south (east/west faces) live in horizontalWall.
        # Our JSON dir 0 = east-west wall (Y boundary), dir 1 = north-south (X boundary).
        # So: dir 0 -> verticalWall (byte 5), dir 1 -> horizontalWall (byte 4).
        buf[off + (5 if direction == 0 else 4)] = (doorDefId + 1) & 0xFF

    # 2. Floor: a stepped citadel slab with a ritual-court inlay and gate axes.
    for x, y in enclave_floor_tiles():
        buf[tile_offset(x, y) + 2] = enclave_floor_overlay(x, y)

    # 3. Roofs: default client config hides roofs, but when enabled these rooms
    #    now use a generated slate texture instead of the stock roof material.
    for x, y in enclave_roof_tiles():
        buf[tile_offset(x, y) + 3] = ROOF_VOID_V3

    return bytes(buf)


def patch_void_island_sector(sector_bytes: bytes) -> bytes:
    """Create a small isolated Void Island inside an otherwise ocean sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - VOID_ISLAND_SECTOR_BASE_X
        ty = worldY - VOID_ISLAND_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {VOID_ISLAND_SECTOR}")
        return (tx * 48 + ty) * 10

    land = set()
    for x in range(VOID_ISLAND_CENTER_X - 8, VOID_ISLAND_CENTER_X + 9):
        for y in range(VOID_ISLAND_CENTER_Y - 7, VOID_ISLAND_CENTER_Y + 8):
            dx = abs(x - VOID_ISLAND_CENTER_X)
            dy = abs(y - VOID_ISLAND_CENTER_Y)
            if (dx * 1.15) + dy <= 9.0:
                land.add((x, y))

    for x in range(VOID_ISLAND_INTRO_CENTER_X - 7, VOID_ISLAND_INTRO_CENTER_X + 8):
        for y in range(VOID_ISLAND_INTRO_CENTER_Y - 5, VOID_ISLAND_INTRO_CENTER_Y + 6):
            dx = abs(x - VOID_ISLAND_INTRO_CENTER_X)
            dy = abs(y - VOID_ISLAND_INTRO_CENTER_Y)
            if (dx * 1.10) + (dy * 1.35) <= 7.2:
                land.add((x, y))

    for y in range(VOID_ISLAND_CENTER_Y + 6, VOID_ISLAND_INTRO_CENTER_Y - 4):
        for x in range(VOID_ISLAND_CENTER_X - 2, VOID_ISLAND_CENTER_X + 3):
            if abs(x - VOID_ISLAND_CENTER_X) <= (2 if y in (31, 32, 33) else 1):
                land.add((x, y))

    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        main_inner_ring = abs(x - VOID_ISLAND_CENTER_X) + abs(y - VOID_ISLAND_CENTER_Y) in (2, 3)
        main_center = abs(x - VOID_ISLAND_CENTER_X) <= 1 and abs(y - VOID_ISLAND_CENTER_Y) <= 1
        intro_inner_ring = abs(x - VOID_ISLAND_INTRO_CENTER_X) + abs(y - VOID_ISLAND_INTRO_CENTER_Y) in (2, 3)
        intro_center = abs(x - VOID_ISLAND_INTRO_CENTER_X) <= 1 and abs(y - VOID_ISLAND_INTRO_CENTER_Y) <= 1

        buf[off + 0] = 52 if edge else 68
        buf[off + 1] = (112 + ((x * 7 + y * 3) % 20)) & 0xFF
        if main_center or intro_center:
            buf[off + 2] = FLOOR_RITUAL
        elif main_inner_ring or intro_inner_ring or (abs(x - VOID_ISLAND_CENTER_X) <= 1 and 30 <= y <= 34):
            buf[off + 2] = FLOOR_MID
        else:
            buf[off + 2] = FLOOR_INDOOR
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    return bytes(buf)


def tutorial_isle_tiles():
    """Land mask for the Tutorial Isle: three chamfered chamber rects."""
    land = set()
    for min_x, max_x, min_y, max_y in (TUTORIAL_ISLE_C1, TUTORIAL_ISLE_C2, TUTORIAL_ISLE_C3):
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                if x in (min_x, max_x) and y in (min_y, max_y):
                    continue  # chamfer corners for a less boxy silhouette
                land.add((x, y))
    return land


def patch_tutorial_isle_sector(sector_bytes: bytes) -> bytes:
    """Carve the gated Tutorial Isle into the Void Island ocean sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - VOID_ISLAND_SECTOR_BASE_X
        ty = worldY - VOID_ISLAND_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {VOID_ISLAND_SECTOR}")
        return (tx * 48 + ty) * 10

    land = tutorial_isle_tiles()
    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        arrival_pad = abs(x - TUTORIAL_ISLE_LANE_X) <= 1 and abs(y - TUTORIAL_ISLE_PAD_Y) <= 1
        ring_center = abs(x - TUTORIAL_ISLE_LANE_X) <= 1 and abs(y - TUTORIAL_ISLE_RING_Y) <= 1
        ring_halo = 4 <= (x - TUTORIAL_ISLE_LANE_X) ** 2 + (y - TUTORIAL_ISLE_RING_Y) ** 2 <= 9

        buf[off + 0] = 52 if edge else 68
        buf[off + 1] = (112 + ((x * 7 + y * 3) % 20)) & 0xFF
        if y <= TUTORIAL_ISLE_C3[3]:
            buf[off + 2] = FLOOR_V3_STONE  # the Scar: cracked stone
        elif arrival_pad or ring_center:
            buf[off + 2] = FLOOR_RITUAL
        elif ring_halo or x == TUTORIAL_ISLE_LANE_X:
            buf[off + 2] = FLOOR_MID
        else:
            buf[off + 2] = FLOOR_INDOOR
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    # Gate fences: dir-0 (east-west) walls live in the verticalWall byte (5),
    # boundary between (x, y-1) and (x, y). Lane tile stays open (script-gated);
    # sigil panels flank the gap, low fences span the rest.
    def fence_row(y, min_x, max_x):
        for x in range(min_x, max_x + 1):
            if x == TUTORIAL_ISLE_LANE_X:
                continue
            if (x, y) not in land or (x, y - 1) not in land:
                continue  # chamfered corners fall back to ocean, no fence needed
            door_id = VOID_V3_SIGIL if abs(x - TUTORIAL_ISLE_LANE_X) == 1 else LOW_FENCE
            buf[tile_offset(x, y) + 5] = (door_id + 1) & 0xFF

    fence_row(TUTORIAL_ISLE_GATE1_Y, TUTORIAL_ISLE_C1[0], TUTORIAL_ISLE_C1[1])
    fence_row(TUTORIAL_ISLE_GATE2_Y, TUTORIAL_ISLE_C3[0], TUTORIAL_ISLE_C3[1])

    return bytes(buf)


def patch_catchsim_island_sector(sector_bytes: bytes, sector_name: str, sector_base_x: int, sector_base_y: int,
                                 offset_x: int, offset_y: int) -> bytes:
    """Bake a large walkable ocean island for the PK Catching Simulator."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - sector_base_x
        ty = worldY - sector_base_y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {sector_name}")
        return (tx * 48 + ty) * 10

    land = set()
    min_x = CATCHSIM_ISLAND_MIN_X + offset_x
    max_x = CATCHSIM_ISLAND_MAX_X + offset_x
    min_y = CATCHSIM_ISLAND_MIN_Y + offset_y
    max_y = CATCHSIM_ISLAND_MAX_Y + offset_y
    center_x = CATCHSIM_ISLAND_CENTER_X + offset_x
    center_y = CATCHSIM_ISLAND_CENTER_Y + offset_y
    for x in range(min_x, max_x + 1):
        for y in range(min_y, max_y + 1):
            nx = abs(x - center_x) / CATCHSIM_ISLAND_RADIUS_X
            ny = abs(y - center_y) / CATCHSIM_ISLAND_RADIUS_Y
            # Superellipse keeps the island compact while preserving a broad,
            # rectangular practice floor for catching lines and corner turns.
            if (nx ** 4) + (ny ** 4) <= 1.0:
                land.add((x, y))

    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        center_lane = x == center_x or y == center_y

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (104 + ((x * 5 + y * 11) % 28)) & 0xFF
        buf[off + 2] = FLOOR_MID if center_lane else FLOOR_INDOOR
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    return bytes(buf)


def patch_deathmatch_sector(sector_bytes: bytes) -> bytes:
    """Bake the Death Match basement and compact altar arena into an underground sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - DEATHMATCH_SECTOR_BASE_X
        ty = worldY - DEATHMATCH_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {DEATHMATCH_SECTOR}")
        return (tx * 48 + ty) * 10

    def set_wall(worldX, worldY, direction, doorDefId):
        off = tile_offset(worldX, worldY)
        buf[off + (5 if direction == 0 else 4)] = (doorDefId + 1) & 0xFF

    land = set()
    rooms = (
        (DEATHMATCH_ARENA_MIN_X, DEATHMATCH_ARENA_MAX_X, DEATHMATCH_ARENA_MIN_Y, DEATHMATCH_ARENA_MAX_Y),
        (DEATHMATCH_BASEMENT_MIN_X, DEATHMATCH_BASEMENT_MAX_X, DEATHMATCH_BASEMENT_MIN_Y, DEATHMATCH_BASEMENT_MAX_Y),
    )
    for min_x, max_x, min_y, max_y in rooms:
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                land.add((x, y))

    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        center_lane = x == DEATHMATCH_CENTER_X or y == DEATHMATCH_CENTER_Y
        duel_core = abs(x - DEATHMATCH_CENTER_X) <= 2 and abs(y - DEATHMATCH_CENTER_Y) <= 2
        basement = y >= DEATHMATCH_BASEMENT_MIN_Y

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (106 + ((x * 9 + y * 7) % 26)) & 0xFF
        if basement:
            buf[off + 2] = FLOOR_V3_STONE
        elif duel_core:
            buf[off + 2] = FLOOR_RITUAL
        elif center_lane or (x + y) % 4 == 0:
            buf[off + 2] = FLOOR_MID
        else:
            buf[off + 2] = FLOOR_INDOOR
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    for min_x, max_x, min_y, max_y in rooms:
        for x in range(min_x, max_x + 1):
            set_wall(x, min_y, 0, VOID_V3_WALL)
            set_wall(x, max_y + 1, 0, VOID_V3_WALL)
        for y in range(min_y, max_y + 1):
            set_wall(min_x, y, 1, VOID_V3_WALL)
            set_wall(max_x + 1, y, 1, VOID_V3_WALL)

    for x, y, direction in (
        (DEATHMATCH_ARENA_MIN_X + 2, DEATHMATCH_ARENA_MIN_Y, 0),
        (DEATHMATCH_ARENA_MAX_X - 2, DEATHMATCH_ARENA_MIN_Y, 0),
        (DEATHMATCH_ARENA_MIN_X + 2, DEATHMATCH_ARENA_MAX_Y + 1, 0),
        (DEATHMATCH_ARENA_MAX_X - 2, DEATHMATCH_ARENA_MAX_Y + 1, 0),
        (DEATHMATCH_BASEMENT_CENTER_X, DEATHMATCH_BASEMENT_MIN_Y, 0),
        (DEATHMATCH_BASEMENT_CENTER_X, DEATHMATCH_BASEMENT_MAX_Y + 1, 0),
    ):
        set_wall(x, y, direction, VOID_V3_SIGIL)

    return bytes(buf)


def patch_voidrush_sector(sector_bytes: bytes) -> bytes:
    """Bake the Void Rush waiting room and arena floor into the ocean sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - VOIDRUSH_SECTOR_BASE_X
        ty = worldY - VOIDRUSH_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {VOIDRUSH_SECTOR}")
        return (tx * 48 + ty) * 10

    land = set()
    for x in range(VOIDRUSH_PAD_MIN_X, VOIDRUSH_PAD_MAX_X + 1):
        for y in range(VOIDRUSH_PAD_MIN_Y, VOIDRUSH_PAD_MAX_Y + 1):
            in_arena = VOIDRUSH_ARENA_MIN_X <= x <= VOIDRUSH_ARENA_MAX_X and VOIDRUSH_ARENA_MIN_Y <= y <= VOIDRUSH_ARENA_MAX_Y
            in_waiting_room = 499 <= x <= 511 and 87 <= y <= 90
            edge_shape = (
                (x in (VOIDRUSH_PAD_MIN_X, VOIDRUSH_PAD_MAX_X) and 58 <= y <= 86)
                or (y in (VOIDRUSH_PAD_MIN_Y, VOIDRUSH_PAD_MAX_Y) and 492 <= x <= 518)
            )
            if in_arena or in_waiting_room or edge_shape:
                land.add((x, y))

    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        center_lane = x == VOIDRUSH_CENTER_X or y == VOIDRUSH_CENTER_Y
        waiting_room = y >= 87

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (108 + ((x * 3 + y * 13) % 24)) & 0xFF
        if waiting_room:
            buf[off + 2] = FLOOR_V3_STONE
        elif center_lane:
            buf[off + 2] = FLOOR_RITUAL
        elif (x + y) % 5 == 0:
            buf[off + 2] = FLOOR_MID
        else:
            buf[off + 2] = FLOOR_INDOOR
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    return bytes(buf)


def patch_colossus_sector(sector_bytes: bytes) -> bytes:
    """Bake the Void Colossus raid plaza: a dark circular arena ringed by ocean.

    Visual language follows the concept art: checkered dark stone plaza, a bright
    ritual circle under the boss, and a mid-purple ring walkway around it. The
    surrounding ocean tiles are left untouched so the plaza is escape-proof
    without any wall bytes.
    """
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - COLOSSUS_SECTOR_BASE_X
        ty = worldY - COLOSSUS_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {COLOSSUS_SECTOR}")
        return (tx * 48 + ty) * 10

    land = set()
    r = COLOSSUS_PLAZA_RADIUS
    for x in range(int(COLOSSUS_CENTER_X - r), int(COLOSSUS_CENTER_X + r) + 1):
        for y in range(int(COLOSSUS_CENTER_Y - r), int(COLOSSUS_CENTER_Y + r) + 1):
            dx = x - COLOSSUS_CENTER_X
            dy = y - COLOSSUS_CENTER_Y
            if dx * dx + dy * dy <= r * r:
                land.add((x, y))

    for x, y in land:
        off = tile_offset(x, y)
        dx = x - COLOSSUS_CENTER_X
        dy = y - COLOSSUS_CENTER_Y
        dist2 = dx * dx + dy * dy
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (106 + ((x * 9 + y * 7) % 26)) & 0xFF
        if dist2 <= 4:
            # Boss platform: bright ritual core under the Colossus.
            buf[off + 2] = FLOOR_RITUAL
        elif 9 <= dist2 <= 20:
            # The ring walkway circling the boss platform (concept's glowing circle).
            buf[off + 2] = FLOOR_MID
        elif edge or dist2 >= (r - 1.5) * (r - 1.5):
            # Outer rim band so the plaza reads as a deliberate disc.
            buf[off + 2] = FLOOR_MID
        else:
            # Checkered dark plaza body, 2x2 squares like the concept art.
            buf[off + 2] = FLOOR_INDOOR if ((x // 2) + (y // 2)) % 2 == 0 else FLOOR_V3_STONE
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    return bytes(buf)


def undead_siege_island_tiles():
    """Return a broad but bounded island tile mask for the Undead Siege manor arena."""
    land = set()
    for x in range(UNDEAD_SIEGE_CENTER_X - 23, UNDEAD_SIEGE_CENTER_X + 24):
        for y in range(UNDEAD_SIEGE_CENTER_Y - 22, UNDEAD_SIEGE_CENTER_Y + 23):
            nx = abs(x - UNDEAD_SIEGE_CENTER_X) / UNDEAD_SIEGE_ISLAND_RADIUS_X
            ny = abs(y - UNDEAD_SIEGE_CENTER_Y) / UNDEAD_SIEGE_ISLAND_RADIUS_Y
            # The lower powers round the island enough to read as landmass, while
            # the slight noise breaks up the perfect arena-disc silhouette.
            wobble = ((x * 17 + y * 11) % 7 - 3) * 0.012
            if (nx ** 3.2) + (ny ** 3.2) <= 1.0 + wobble:
                land.add((x, y))
    return land


def undead_siege_house_tiles():
    """Return the ruined house floor, including the front porch deck."""
    house = {
        (x, y)
        for x in range(UNDEAD_SIEGE_HOUSE_MIN_X, UNDEAD_SIEGE_HOUSE_MAX_X + 1)
        for y in range(UNDEAD_SIEGE_HOUSE_MIN_Y, UNDEAD_SIEGE_HOUSE_MAX_Y + 1)
    }
    for x in range(593, 608):
        for y in range(132, 137):
            house.add((x, y))
    for x in range(596, 605):
        for y in range(104, 109):
            house.add((x, y))
    return house


def undead_siege_floor_overlay(x: int, y: int, house: bool, edge: bool) -> int:
    if house:
        porch_or_stoop = y >= 132 or y <= 108
        central_stain = x == UNDEAD_SIEGE_CENTER_X and y == UNDEAD_SIEGE_CENTER_Y
        north_south_hall = 598 <= x <= 602
        east_west_hall = 116 <= y <= 124
        if central_stain:
            return FLOOR_INDOOR
        if (x + y) % 17 == 0:
            return FLOOR_SIEGE_MOSSY_STONE
        if porch_or_stoop or north_south_hall or east_west_hall:
            return FLOOR_SIEGE_PLANKS
        return FLOOR_SIEGE_PLANKS
    path = (
        (598 <= x <= 602 and (101 <= y <= 109 or 131 <= y <= 139))
        or (117 <= y <= 123 and (581 <= x <= 590 or 611 <= x <= 619))
    )
    if path:
        return FLOOR_SIEGE_MOSSY_STONE
    if edge:
        return FLOOR_INDOOR
    if (x * 3 + y * 5) % 11 == 0:
        return FLOOR_SIEGE_MOSSY_STONE
    return FLOOR_INDOOR


def undead_siege_roof_tiles():
    """Give the large manor a broken roof silhouette when roofs are enabled."""
    roof = set()
    for x in range(UNDEAD_SIEGE_HOUSE_MIN_X + 1, UNDEAD_SIEGE_HOUSE_MAX_X):
        for y in range(UNDEAD_SIEGE_HOUSE_MIN_Y + 1, UNDEAD_SIEGE_HOUSE_MAX_Y):
            if 597 <= x <= 603 and 116 <= y <= 124:
                continue
            if (x + y) % 13 == 0:
                continue
            roof.add((x, y))
    return roof


def undead_siege_walls():
    """Yield (worldX, worldY, direction, doorDefId) for a zombie-house manor."""
    walls = []
    seen = set()

    def b(x, y, direction, id_):
        key = (x, y, direction)
        if key in seen:
            return
        seen.add(key)
        walls.append((x, y, direction, id_))

    def segment_id(x, y, direction):
        if (x + y + direction) % 11 == 0:
            return CRUMBLED_WALL
        if (x + y) % 7 == 0:
            return TIMBER_WINDOW
        if (x + y) % 5 == 0:
            return MOSSY_BRICKS
        return TIMBER_WALL

    # North/south faces. Wide gaps are deliberate zombie entry breaches.
    for x in range(UNDEAD_SIEGE_HOUSE_MIN_X, UNDEAD_SIEGE_HOUSE_MAX_X + 1):
        if x not in range(597, 604):
            b(x, UNDEAD_SIEGE_HOUSE_MIN_Y, 0, segment_id(x, UNDEAD_SIEGE_HOUSE_MIN_Y, 0))
        if x not in range(596, 605):
            b(x, UNDEAD_SIEGE_HOUSE_MAX_Y + 1, 0, segment_id(x, UNDEAD_SIEGE_HOUSE_MAX_Y + 1, 0))

    # West/east faces.
    for y in range(UNDEAD_SIEGE_HOUSE_MIN_Y, UNDEAD_SIEGE_HOUSE_MAX_Y + 1):
        if y not in range(117, 124):
            b(UNDEAD_SIEGE_HOUSE_MIN_X, y, 1, segment_id(UNDEAD_SIEGE_HOUSE_MIN_X, y, 1))
        if y not in range(116, 123):
            b(UNDEAD_SIEGE_HOUSE_MAX_X + 1, y, 1, segment_id(UNDEAD_SIEGE_HOUSE_MAX_X + 1, y, 1))

    # Broken interior rooms around a broad central lane. Door gaps stay wide so
    # the horde does not feel like it is fighting the pathfinder.
    for x in range(590, 610):
        if x not in range(597, 604):
            b(x, 116, 0, PLANKS_TIMBER if x % 4 else TIMBER_WINDOW)
        if x not in range(596, 605):
            b(x, 125, 0, PLANKS_TIMBER if x % 3 else TIMBER_WINDOW)
    for y in range(109, 132):
        if y not in range(115, 119) and y not in range(123, 127):
            b(596, y, 1, PLANKS_TIMBER if y % 4 else PLANKS_WINDOW)
        if y not in range(114, 118) and y not in range(122, 126):
            b(604, y, 1, PLANKS_TIMBER if y % 3 else PLANKS_WINDOW)

    # Porch railings and a rear stoop make the house outline read from the
    # isometric camera without enclosing the player.
    for x in range(593, 608):
        if x not in range(598, 603):
            b(x, 137, 0, LOW_FENCE)
        if x not in range(598, 603):
            b(x, 104, 0, LOW_FENCE)
    for y in range(132, 137):
        b(593, y, 1, LOW_FENCE)
        b(608, y, 1, LOW_FENCE)

    # Low barricades around the shoreline prevent accidental arena exits while
    # still letting the main play space feel like an outdoor ruin.
    land = undead_siege_island_tiles()

    def perimeter_id(x, y, direction):
        if (x + y + direction) % 17 == 0:
            return CRUMBLED_WALL
        if (x * 3 + y + direction) % 13 == 0:
            return RAILINGS
        return LOW_FENCE

    for x, y in sorted(land):
        if (x, y - 1) not in land:
            b(x, y, 0, perimeter_id(x, y, 0))
        if (x, y + 1) not in land:
            b(x, y + 1, 0, perimeter_id(x, y + 1, 0))
        if (x - 1, y) not in land:
            b(x, y, 1, perimeter_id(x, y, 1))
        if (x + 1, y) not in land:
            b(x + 1, y, 1, perimeter_id(x + 1, y, 1))

    return walls


def patch_undead_siege_sector(sector_bytes: bytes) -> bytes:
    """Bake an ocean-isolated island and large ruined manor for Undead Siege."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - UNDEAD_SIEGE_SECTOR_BASE_X
        ty = worldY - UNDEAD_SIEGE_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {UNDEAD_SIEGE_SECTOR}")
        return (tx * 48 + ty) * 10

    # Start with a dark, non-walkable void sea so the island silhouette reads
    # even when the source sector was ordinary mainland terrain.
    for tx in range(48):
        for ty in range(48):
            x = UNDEAD_SIEGE_SECTOR_BASE_X + tx
            y = UNDEAD_SIEGE_SECTOR_BASE_Y + ty
            off = (tx * 48 + ty) * 10
            buf[off + 0] = 14 + ((x * 5 + y * 3) % 6)
            buf[off + 1] = (42 + ((x * 7 + y * 11) % 18)) & 0xFF
            buf[off + 2] = FLOOR_LAVA
            buf[off + 3] = 0
            buf[off + 4] = 0
            buf[off + 5] = 0
            buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    land = undead_siege_island_tiles()
    house = undead_siege_house_tiles()
    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        in_house = (x, y) in house

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (104 + ((x * 5 + y * 11) % 28)) & 0xFF
        buf[off + 2] = undead_siege_floor_overlay(x, y, in_house, edge)
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    for x, y in undead_siege_roof_tiles():
        buf[tile_offset(x, y) + 3] = ROOF_STANDARD

    for worldX, worldY, direction, doorDefId in undead_siege_walls():
        off = tile_offset(worldX, worldY)
        buf[off + (5 if direction == 0 else 4)] = (doorDefId + 1) & 0xFF

    return bytes(buf)


def voidarena_floor_tiles():
    """Return the four public fight cages plus the long northern spectator hall."""
    floor = {
        (x, y)
        for x in range(VOIDARENA_HALL_MIN_X, VOIDARENA_HALL_MAX_X + 1)
        for y in range(VOIDARENA_HALL_MIN_Y, VOIDARENA_HALL_MAX_Y + 1)
    }
    for min_x, min_y, max_x, max_y in VOIDARENA_CAGES:
        for x in range(min_x, max_x + 1):
            for y in range(min_y, max_y + 1):
                floor.add((x, y))
    return floor


def voidarena_backdrop_tiles():
    """Return the underground cave plate around the walkable arena footprint."""
    return {
        (x, y)
        for x in range(VOIDARENA_BACKDROP_MIN_X, VOIDARENA_BACKDROP_MAX_X + 1)
        for y in range(VOIDARENA_BACKDROP_MIN_Y, VOIDARENA_BACKDROP_MAX_Y + 1)
    }


def voidarena_cage_for(x: int, y: int):
    for index, (min_x, min_y, max_x, max_y) in enumerate(VOIDARENA_CAGES):
        if min_x <= x <= max_x and min_y <= y <= max_y:
            return index, min_x, min_y, max_x, max_y
    return None


def voidarena_backdrop_overlay(x: int, y: int) -> int:
    """Use lava for all non-walkable tiles surrounding the arena footprint."""
    return FLOOR_LAVA


def voidarena_floor_overlay(x: int, y: int) -> int:
    cage = voidarena_cage_for(x, y)
    if cage is not None:
        _, min_x, min_y, max_x, max_y = cage
        center_x = (min_x + max_x) // 2
        center_y = 2903
        if abs(x - center_x) <= 1 and abs(y - center_y) <= 1:
            return FLOOR_RITUAL
        if x == center_x or y == center_y:
            return FLOOR_MID
        return FLOOR_INDOOR if ((x // 2) + (y // 2)) % 2 == 0 else FLOOR_V3_STONE

    if y in (VOIDARENA_HALL_MIN_Y, VOIDARENA_HALL_MAX_Y) or x in (VOIDARENA_HALL_MIN_X, VOIDARENA_HALL_MAX_X):
        return FLOOR_MID
    if x in (596, 600, 604):
        return FLOOR_RITUAL
    return FLOOR_V3_STONE if ((x // 2) + (y // 2)) % 2 == 0 else FLOOR_INDOOR


def voidarena_walls():
    """Yield (worldX, worldY, direction, doorDefId) for the ranked arena cages."""
    walls = []
    seen = set()

    def b(x, y, direction, id_):
        key = (x, y, direction)
        if key in seen:
            return
        seen.add(key)
        walls.append((x, y, direction, id_))

    # Put bar-style boundaries around the fight cages first. The general
    # perimeter pass below then keeps these slim jail bars instead of replacing
    # them with fortress stone.
    for _index, (min_x, min_y, max_x, max_y) in enumerate(VOIDARENA_CAGES):
        for x in range(min_x, max_x + 1):
            b(x, min_y, 0, RAILINGS)
            b(x, max_y + 1, 0, RAILINGS)
        for y in range(min_y, max_y + 1):
            b(min_x, y, 1, RAILINGS)
            b(max_x + 1, y, 1, RAILINGS)

    floor = voidarena_floor_tiles()
    for x, y in sorted(floor):
        for nx, ny, wx, wy, direction in (
            (x, y - 1, x, y, 0),
            (x, y + 1, x, y + 1, 0),
            (x - 1, y, x, y, 1),
            (x + 1, y, x + 1, y, 1),
        ):
            if (nx, ny) not in floor:
                b(wx, wy, direction, VOID_V3_BASTION)

    return walls


def patch_voidarena_sector(sector_bytes: bytes) -> bytes:
    """Bake a four-cage underground lava arena with a long spectator hallway."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)

    def tile_offset(worldX, worldY):
        tx = worldX - VOIDARENA_SECTOR_BASE_X
        ty = worldY - VOIDARENA_SECTOR_BASE_Y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise ValueError(f"({worldX}, {worldY}) outside sector {VOIDARENA_SECTOR}")
        return (tx * 48 + ty) * 10

    floor = voidarena_floor_tiles()
    backdrop = voidarena_backdrop_tiles()
    for x, y in backdrop:
        off = tile_offset(x, y)
        lava = voidarena_backdrop_overlay(x, y) == FLOOR_LAVA

        buf[off + 0] = 34 if lava else 44
        buf[off + 1] = (88 + ((x * 7 + y * 13) % 34)) & 0xFF
        buf[off + 2] = voidarena_backdrop_overlay(x, y)
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    for x, y in floor:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in floor for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))

        buf[off + 0] = 50 if edge else 66
        buf[off + 1] = (106 + ((x * 11 + y * 5) % 26)) & 0xFF
        buf[off + 2] = voidarena_floor_overlay(x, y)
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"

    for worldX, worldY, direction, doorDefId in voidarena_walls():
        off = tile_offset(worldX, worldY)
        buf[off + (5 if direction == 0 else 4)] = (doorDefId + 1) & 0xFF

    return bytes(buf)


def voidarena_sky_overlay(x: int, y: int) -> int:
    """Use lava for the unreachable backdrop sectors around the arena."""
    return FLOOR_LAVA


def patch_voidarena_sky_sector(sector_bytes: bytes, base_x: int, base_y: int) -> bytes:
    """Bake a low, dark void-sky matte around the underground arena."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)
    for tx in range(48):
        for ty in range(48):
            x = base_x + tx
            y = base_y + ty
            off = (tx * 48 + ty) * 10
            overlay = voidarena_sky_overlay(x, y)
            buf[off + 0] = 14 + ((x * 5 + y * 3) % 6)
            buf[off + 1] = (42 + ((x * 7 + y * 11) % 18)) & 0xFF
            buf[off + 2] = overlay
            buf[off + 3] = 0
            buf[off + 4] = 0
            buf[off + 5] = 0
            buf[off + 6:off + 10] = b"\x00\x00\x00\x00"
    return bytes(buf)


# === Void Dungeon floors (three underground Wilderness stages) ===
# Gives the shared Void Dungeon dark floor + minimap instead of pure black. The walkable-tile mask
# and stage metadata are emitted together by scripts/gen-void-dungeon.py, so terrain patterns and
# policy geometry always describe the same exact room/corridor union. Sector name -> world base:
# sectionX = worldX//48 + 48, sectionY = (worldY%944)//48 + 37, height = worldY//944 (per
# WorldLoader.loadSection). Only the dungeon's own tiles are touched; the rest stays black void.
DUNGEON_FLOOR_MASK = REPO / "server/conf/server/data/void_dungeon_floor.json"
DUNGEON_BOUNDARIES = REPO / "server/conf/server/defs/locs/BoundaryLocsVoidDungeon.json"
RETIRED_DUNGEON_SECTORS = ("h1x50y44", "h1x50y45")


def _tile_sector(x: int, y: int) -> str:
    return f"h{y // 944}x{x // 48 + 48}y{(y % 944) // 48 + 37}"


def _sector_base(name: str) -> tuple:
    height = int(name[1])
    sx = int(name[name.index("x") + 1:name.index("y")])
    sy = int(name[name.index("y") + 1:])
    return (sx - 48) * 48, height * 944 + (sy - 37) * 48


def _dungeon_rect_tiles(rect: dict) -> set:
    return {
        (x, y)
        for x in range(rect["minX"], rect["maxX"] + 1)
        for y in range(rect["minY"], rect["maxY"] + 1)
    }


def load_dungeon_floor() -> tuple:
    """Read and cross-check generated floor, stage, and baked-wall metadata."""
    if not DUNGEON_FLOOR_MASK.exists():
        return {}, {}, {}

    payload = json.loads(DUNGEON_FLOOR_MASK.read_text())
    flat_tiles = {tuple(tile) for tile in payload["tiles"]}
    stage_by_tile = {}
    stages = {}

    for stage in payload.get("stages", []):
        key = stage["key"]
        if key in stages:
            raise RuntimeError(f"duplicate Void Dungeon stage metadata: {key}")
        stages[key] = stage
        stage_tiles = set()
        for rect in stage["rectangles"]:
            stage_tiles.update(_dungeon_rect_tiles(rect))
        if len(stage_tiles) != stage["tileCount"]:
            raise RuntimeError(
                f"Void Dungeon stage {key} metadata has {len(stage_tiles)} tiles, "
                f"expected {stage['tileCount']}"
            )
        for point in stage_tiles:
            owner = stage_by_tile.setdefault(point, key)
            if owner != key:
                raise RuntimeError(
                    f"Void Dungeon tile {point} belongs to both {owner} and {key}"
                )

    metadata_tiles = set(stage_by_tile)
    if metadata_tiles != flat_tiles:
        raise RuntimeError(
            "Void Dungeon stage metadata differs from flat mask "
            f"(missing {len(flat_tiles - metadata_tiles)}, extra {len(metadata_tiles - flat_tiles)})"
        )

    by_sector = {}
    for x, y in sorted(flat_tiles):
        by_sector.setdefault(_tile_sector(x, y), []).append((x, y, stage_by_tile[(x, y)]))

    boundaries_by_sector = {}
    boundary_keys = set()
    boundary_payload = json.loads(DUNGEON_BOUNDARIES.read_text())
    for boundary in boundary_payload.get("boundaries", []):
        x = boundary["pos"]["X"]
        y = boundary["pos"]["Y"]
        direction = boundary["direction"]
        key = (x, y, direction)
        if key in boundary_keys:
            raise RuntimeError(f"duplicate Void Dungeon baked boundary {key}")
        boundary_keys.add(key)
        adjacent = ((x, y), (x, y - 1)) if direction == 0 else ((x, y), (x - 1, y))
        if not any(point in flat_tiles for point in adjacent):
            raise RuntimeError(f"Void Dungeon boundary {key} does not border its floor mask")
        boundaries_by_sector.setdefault(_tile_sector(x, y), []).append(
            (x, y, direction, boundary["id"])
        )
    return by_sector, stages, boundaries_by_sector


def dungeon_floor_overlay(stage: dict, x: int, y: int) -> int:
    """Choose a restrained, stage-specific pattern from existing Void floor tiles."""
    pattern = stage["pattern"]
    local_y = y % 944

    if pattern == "riftworks_rubble":
        # Cracked stone dominates, interrupted by old transverse work seams.
        if local_y % 14 in (11, 12) and x % 5 != 0:
            return FLOOR_INDOOR
        if (x + 2 * local_y) % 19 == 0:
            return FLOOR_MID
        return FLOOR_V3_STONE

    if pattern == "menagerie_symmetry":
        # Mirrored side bays read against the continuous central circulation line.
        distance = abs(x - 72)
        if distance <= 1:
            return FLOOR_MID
        if distance % 12 in (5, 6) or local_y % 16 in (14, 15):
            return FLOOR_V3_STONE
        return FLOOR_INDOOR

    if pattern == "sanctum_processional":
        # A dark central nave culminates in the broad final demon seal.
        if local_y <= 375:
            radius = max(abs(x - 72), abs(local_y - 368))
            if radius in (4, 5):
                return FLOOR_RITUAL
            if radius < 4:
                return FLOOR_MID
        if x in (71, 72, 73) and local_y >= 378:
            return FLOOR_MID
        return FLOOR_INDOOR if (local_y // 4) % 2 == 0 else FLOOR_V3_STONE

    raise RuntimeError(f"unknown Void Dungeon floor pattern: {pattern}")


def patch_dungeon_sector(
        sector_bytes: bytes, sector_name: str, tiles: list, walls: list, stages: dict) -> bytes:
    """Bake generated stage floors and perimeter walls into this sector."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)
    base_x, base_y = _sector_base(sector_name)
    for x, y, stage_key in tiles:
        tx, ty = x - base_x, y - base_y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            continue
        stage = stages[stage_key]
        off = (tx * 48 + ty) * 10
        buf[off + 0] = 64 + stage["id"]
        buf[off + 1] = (106 + ((x * 9 + y * 7 + stage["id"] * 13) % 26)) & 0xFF
        buf[off + 2] = dungeon_floor_overlay(stage, x, y)
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"
    for x, y, direction, boundary_id in walls:
        tx, ty = x - base_x, y - base_y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            raise RuntimeError(f"Void Dungeon boundary {(x, y)} is outside {sector_name}")
        off = (tx * 48 + ty) * 10
        buf[off + (5 if direction == 0 else 4)] = (boundary_id + 1) & 0xFF
    return bytes(buf)


def main():
    # 1. Read clean source sectors from Authentic
    with zipfile.ZipFile(AUTHENTIC) as z:
        enclave_source = z.read(ENCLAVE_SECTOR)
        island_source = z.read(VOID_ISLAND_SECTOR)
        catchsim_sources = {sector: z.read(sector) for sector, _, _, _, _ in CATCHSIM_SECTORS}
        deathmatch_source = z.read(DEATHMATCH_SECTOR)
        voidrush_source = z.read(VOIDRUSH_SECTOR)
        colossus_source = z.read(COLOSSUS_SECTOR)
        undead_siege_source = z.read(UNDEAD_SIEGE_SECTOR)
        voidarena_source = z.read(VOIDARENA_SECTOR)
        voidarena_sky_sources = {sector: z.read(sector) for sector, _, _ in VOIDARENA_SKY_SECTORS}
        dungeon_floor, dungeon_stages, dungeon_boundaries = load_dungeon_floor()
        dungeon_sectors = set(dungeon_floor) | set(dungeon_boundaries)
        dungeon_sources = {sector: z.read(sector) for sector in dungeon_sectors}
        retired_dungeon_sources = {sector: z.read(sector) for sector in RETIRED_DUNGEON_SECTORS}
        legacy_sources = {sector: z.read(sector) for sector in LEGACY_VOID_ISLAND_SECTORS}
        legacy_enclave_sources = {sector: z.read(sector) for sector in LEGACY_ENCLAVE_SECTORS}
        legacy_deathmatch_sources = {sector: z.read(sector) for sector in LEGACY_DEATHMATCH_SECTORS}
        legacy_voidarena_sources = {sector: z.read(sector) for sector in LEGACY_VOIDARENA_SECTORS}
    print(f"Read {len(enclave_source)} bytes from {AUTHENTIC.name}!{ENCLAVE_SECTOR}")
    print(f"Read {len(island_source)} bytes from {AUTHENTIC.name}!{VOID_ISLAND_SECTOR}")
    for sector, source in catchsim_sources.items():
        print(f"Read {len(source)} bytes from {AUTHENTIC.name}!{sector}")
    print(f"Read {len(deathmatch_source)} bytes from {AUTHENTIC.name}!{DEATHMATCH_SECTOR}")
    print(f"Read {len(voidrush_source)} bytes from {AUTHENTIC.name}!{VOIDRUSH_SECTOR}")
    print(f"Read {len(colossus_source)} bytes from {AUTHENTIC.name}!{COLOSSUS_SECTOR}")
    print(f"Read {len(undead_siege_source)} bytes from {AUTHENTIC.name}!{UNDEAD_SIEGE_SECTOR}")
    print(f"Read {len(voidarena_source)} bytes from {AUTHENTIC.name}!{VOIDARENA_SECTOR}")
    for sector, source in voidarena_sky_sources.items():
        print(f"Read {len(source)} bytes from {AUTHENTIC.name}!{sector}")

    # 2. Apply patches
    walls = enclave_walls()
    patched_sectors = {
        ENCLAVE_SECTOR: patch_enclave_sector(enclave_source),
        # Same sector: the Tutorial Isle is carved into the Void Island sector's ocean.
        VOID_ISLAND_SECTOR: patch_tutorial_isle_sector(patch_void_island_sector(island_source)),
        DEATHMATCH_SECTOR: patch_deathmatch_sector(deathmatch_source),
        VOIDRUSH_SECTOR: patch_voidrush_sector(voidrush_source),
        COLOSSUS_SECTOR: patch_colossus_sector(colossus_source),
        UNDEAD_SIEGE_SECTOR: patch_undead_siege_sector(undead_siege_source),
        VOIDARENA_SECTOR: patch_voidarena_sector(voidarena_source),
    }
    for sector, base_x, base_y in VOIDARENA_SKY_SECTORS:
        patched_sectors[sector] = patch_voidarena_sky_sector(voidarena_sky_sources[sector], base_x, base_y)
    for sector in dungeon_sectors:
        patched_sectors[sector] = patch_dungeon_sector(
            dungeon_sources[sector], sector, dungeon_floor.get(sector, []),
            dungeon_boundaries.get(sector, []), dungeon_stages
        )
    patched_sectors.update(retired_dungeon_sources)
    for sector, base_x, base_y, offset_x, offset_y in CATCHSIM_SECTORS:
        patched_sectors[sector] = patch_catchsim_island_sector(
            catchsim_sources[sector], sector, base_x, base_y, offset_x, offset_y
        )
    patched_sectors.update(legacy_sources)
    patched_sectors.update(legacy_enclave_sources)
    patched_sectors.update(legacy_deathmatch_sources)
    patched_sectors.update(legacy_voidarena_sources)
    print(f"Patched {len(walls)} enclave walls into sector {ENCLAVE_SECTOR}")
    print(f"Restored {len(LEGACY_ENCLAVE_SECTORS)} legacy enclave sector(s) to authentic terrain")
    print(f"Restored {len(LEGACY_DEATHMATCH_SECTORS)} legacy deathmatch sector(s) to authentic terrain")
    print(f"Restored {len(LEGACY_VOIDARENA_SECTORS)} legacy void arena sector(s) to authentic terrain")
    print(f"Patched isolated Void Island into sector {VOID_ISLAND_SECTOR}")
    print(f"Patched gated Tutorial Isle ({len(tutorial_isle_tiles())} tiles) into sector {VOID_ISLAND_SECTOR}")
    print(f"Patched {len(CATCHSIM_SECTORS)} PK Catching Simulator islands")
    print(f"Patched Death Match basement and altar arena into sector {DEATHMATCH_SECTOR}")
    print(f"Patched Void Rush waiting room and arena floor into sector {VOIDRUSH_SECTOR}")
    print(f"Patched Void Colossus raid plaza into sector {COLOSSUS_SECTOR}")
    print(f"Patched Undead Siege island manor into sector {UNDEAD_SIEGE_SECTOR}")
    print(f"Patched Void Arena underground lava fight hall into sector {VOIDARENA_SECTOR}")
    print(f"Patched {len(VOIDARENA_SKY_SECTORS)} Void Arena lava backdrop sector(s)")
    dungeon_stage_counts = {
        key: sum(1 for tiles in dungeon_floor.values() for _, _, stage_key in tiles if stage_key == key)
        for key in dungeon_stages
    }
    dungeon_stage_summary = ", ".join(
        f"{dungeon_stages[key]['name']}={dungeon_stage_counts[key]}"
        for key in sorted(dungeon_stages, key=lambda item: dungeon_stages[item]["id"])
    )
    print(
        f"Patched Void Dungeon floors ({sum(len(t) for t in dungeon_floor.values())} tiles; "
        f"{sum(len(w) for w in dungeon_boundaries.values())} baked walls; "
        f"{dungeon_stage_summary}) into sectors {', '.join(sorted(dungeon_sectors))}"
    )
    print(f"Restored {len(RETIRED_DUNGEON_SECTORS)} retired fourth-floor sector(s) to authentic terrain")

    # 3. Rebuild Custom_Landscape.orsc with this sector replaced. Apply to both the
    # server copy and the client cache copy (client reads its own when
    # Config.S_WANT_CUSTOM_LANDSCAPE is true — World.java:80-81).
    for target in (CUSTOM, CLIENT_CUSTOM):
        tmp = target.with_suffix(".orsc.tmp")
        with zipfile.ZipFile(target, "r") as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
            replaced = set()
            for info in src.infolist():
                if info.filename in patched_sectors:
                    dst.writestr(info, patched_sectors[info.filename])
                    replaced.add(info.filename)
                else:
                    dst.writestr(info, src.read(info.filename))
            missing = set(patched_sectors) - replaced
            if missing:
                raise RuntimeError(f"sector(s) {', '.join(sorted(missing))} missing from {target}")
        shutil.move(str(tmp), str(target))
        print(f"Wrote patched sectors into {target.relative_to(REPO)}")


if __name__ == "__main__":
    main()
