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

# DoorDef IDs
HIGHWALL = 7
HIGH_DOOR = 8
WALL = 0
DOOR = 2
WEB = 24               # vanilla spider web — CutWeb.java handles cutting (kept for fallback)
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
    """Choose a floor overlay for the V3 generated ritual-court pattern."""
    center_x, center_y = 113, 315
    dx = abs(x - center_x)
    dy = abs(y - center_y)
    dist2 = dx * dx + dy * dy

    if (dx <= 1 and dy <= 1) or (dx == 0 and dy <= 4) or (dy == 0 and dx <= 4):
        return FLOOR_RITUAL
    if 8 <= dist2 <= 18:
        return FLOOR_MID
    if x == center_x or y == center_y:
        return FLOOR_MID
    if (x, y) in (
        (106, 306), (120, 306),
        (105, 315), (121, 315),
        (110, 326), (116, 326),
    ):
        return FLOOR_RITUAL
    return FLOOR_V3_STONE


def enclave_roof_tiles():
    """Return tiles covered by V3 generated roofs when the client shows roofs."""
    roof = set()
    # North sanctum.
    for x in range(106, 121):
        for y in range(304, 310):
            roof.add((x, y))
    # West bank/shop hut.
    for x in range(102, 109):
        for y in range(312, 320):
            roof.add((x, y))
    # East healing-pool hut.
    for x in range(118, 125):
        for y in range(312, 320):
            roof.add((x, y))
    # South ritual hall.
    for x in range(108, 119):
        for y in range(324, 329):
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
                wall_id = VOID_V3_SIGIL if (wx, wy, direction) in gate_flanks else VOID_V3_BASTION
                b(wx, wy, direction, wall_id)

    # North sanctum. Larger shrine with a five-tile south entrance.
    for x in range(105, 122):
        b(x, 303, 0, VOID_V3_SIGIL if x in (112, 113, 114) else VOID_V3_WALL)
        if x < 111 or x > 115:
            b(x, 310, 0, VOID_V3_WALL)
    for y in range(303, 310):
        b(105, y, 1, VOID_V3_WINDOW if y in (305, 307) else VOID_V3_WALL)
        b(122, y, 1, VOID_V3_WINDOW if y in (305, 307) else VOID_V3_WALL)

    # West bank/shop hut. Open east into the courtyard with a broad mouth.
    for x in range(101, 109):
        b(x, 311, 0, VOID_V3_SIGIL if x in (104, 105) else VOID_V3_WALL)
        b(x, 320, 0, VOID_V3_WALL)
    for y in range(311, 320):
        b(101, y, 1, VOID_V3_WINDOW if y in (314, 317) else VOID_V3_WALL)
        if y not in (315, 316):
            b(109, y, 1, VOID_V3_WALL)

    # East healing-pool hut. Open west into the courtyard.
    for x in range(117, 125):
        b(x, 311, 0, VOID_V3_SIGIL if x in (121, 122) else VOID_V3_WALL)
        b(x, 320, 0, VOID_V3_WALL)
    for y in range(311, 320):
        if y not in (315, 316):
            b(117, y, 1, VOID_V3_WALL)
        b(125, y, 1, VOID_V3_WINDOW if y in (314, 317) else VOID_V3_WALL)

    # South ritual hall.
    for x in range(107, 120):
        if x < 111 or x > 115:
            b(x, 323, 0, VOID_V3_WALL)
        b(x, 329, 0, VOID_V3_SIGIL if x in (112, 113, 114) else VOID_V3_WALL)
    for y in range(323, 329):
        b(107, y, 1, VOID_V3_WINDOW if y == 326 else VOID_V3_WALL)
        b(120, y, 1, VOID_V3_WINDOW if y == 326 else VOID_V3_WALL)

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


# === Void Dungeon floor (Floor 3 underground Wilderness black-void) ===
# Gives the shared Void Dungeon a dark void floor + minimap instead of pure black. The walkable-tile
# mask is emitted by scripts/gen-void-dungeon.py (server/conf/server/data/void_dungeon_floor.json), so
# the floor always matches the generated room/corridor layout exactly. Sector name -> world base:
# sectionX = worldX//48 + 48, sectionY = (worldY%944)//48 + 37, height = worldY//944 (per
# WorldLoader.loadSection). Only the dungeon's own tiles are touched; the rest stays black void.
DUNGEON_FLOOR_MASK = REPO / "server/conf/server/data/void_dungeon_floor.json"


def _tile_sector(x: int, y: int) -> str:
    return f"h{y // 944}x{x // 48 + 48}y{(y % 944) // 48 + 37}"


def _sector_base(name: str) -> tuple:
    height = int(name[1])
    sx = int(name[name.index("x") + 1:name.index("y")])
    sy = int(name[name.index("y") + 1:])
    return (sx - 48) * 48, height * 944 + (sy - 37) * 48


def load_dungeon_floor() -> dict:
    """Read the generated walkable mask, grouped by sector name -> [(x,y), ...]. {} if absent."""
    if not DUNGEON_FLOOR_MASK.exists():
        return {}
    by_sector = {}
    for x, y in json.loads(DUNGEON_FLOOR_MASK.read_text())["tiles"]:
        by_sector.setdefault(_tile_sector(x, y), []).append((x, y))
    return by_sector


def patch_dungeon_sector(sector_bytes: bytes, sector_name: str, tiles: list) -> bytes:
    """Bake a dark void floor (Colossus-plaza recipe: 2x2 FLOOR_INDOOR/FLOOR_V3_STONE checker) into the
    given dungeon tiles within this sector. Walls are JSON boundaries, so no wall bytes."""
    assert len(sector_bytes) == 48 * 48 * 10, f"expected 23040 bytes, got {len(sector_bytes)}"
    buf = bytearray(sector_bytes)
    base_x, base_y = _sector_base(sector_name)
    for x, y in tiles:
        tx, ty = x - base_x, y - base_y
        if not (0 <= tx < 48 and 0 <= ty < 48):
            continue
        off = (tx * 48 + ty) * 10
        buf[off + 0] = 66
        buf[off + 1] = (106 + ((x * 9 + y * 7) % 26)) & 0xFF
        buf[off + 2] = FLOOR_INDOOR if ((x // 2) + (y // 2)) % 2 == 0 else FLOOR_V3_STONE
        buf[off + 3] = 0
        buf[off + 4] = 0
        buf[off + 5] = 0
        buf[off + 6:off + 10] = b"\x00\x00\x00\x00"
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
        dungeon_floor = load_dungeon_floor()
        dungeon_sources = {sector: z.read(sector) for sector in dungeon_floor}
        legacy_sources = {sector: z.read(sector) for sector in LEGACY_VOID_ISLAND_SECTORS}
        legacy_enclave_sources = {sector: z.read(sector) for sector in LEGACY_ENCLAVE_SECTORS}
        legacy_deathmatch_sources = {sector: z.read(sector) for sector in LEGACY_DEATHMATCH_SECTORS}
    print(f"Read {len(enclave_source)} bytes from {AUTHENTIC.name}!{ENCLAVE_SECTOR}")
    print(f"Read {len(island_source)} bytes from {AUTHENTIC.name}!{VOID_ISLAND_SECTOR}")
    for sector, source in catchsim_sources.items():
        print(f"Read {len(source)} bytes from {AUTHENTIC.name}!{sector}")
    print(f"Read {len(deathmatch_source)} bytes from {AUTHENTIC.name}!{DEATHMATCH_SECTOR}")
    print(f"Read {len(voidrush_source)} bytes from {AUTHENTIC.name}!{VOIDRUSH_SECTOR}")

    # 2. Apply patches
    walls = enclave_walls()
    patched_sectors = {
        ENCLAVE_SECTOR: patch_enclave_sector(enclave_source),
        VOID_ISLAND_SECTOR: patch_void_island_sector(island_source),
        DEATHMATCH_SECTOR: patch_deathmatch_sector(deathmatch_source),
        VOIDRUSH_SECTOR: patch_voidrush_sector(voidrush_source),
        COLOSSUS_SECTOR: patch_colossus_sector(colossus_source),
    }
    for sector, tiles in dungeon_floor.items():
        patched_sectors[sector] = patch_dungeon_sector(dungeon_sources[sector], sector, tiles)
    for sector, base_x, base_y, offset_x, offset_y in CATCHSIM_SECTORS:
        patched_sectors[sector] = patch_catchsim_island_sector(
            catchsim_sources[sector], sector, base_x, base_y, offset_x, offset_y
        )
    patched_sectors.update(legacy_sources)
    patched_sectors.update(legacy_enclave_sources)
    patched_sectors.update(legacy_deathmatch_sources)
    print(f"Patched {len(walls)} enclave walls into sector {ENCLAVE_SECTOR}")
    print(f"Restored {len(LEGACY_ENCLAVE_SECTORS)} legacy enclave sector(s) to authentic terrain")
    print(f"Restored {len(LEGACY_DEATHMATCH_SECTORS)} legacy deathmatch sector(s) to authentic terrain")
    print(f"Patched isolated Void Island into sector {VOID_ISLAND_SECTOR}")
    print(f"Patched {len(CATCHSIM_SECTORS)} PK Catching Simulator islands")
    print(f"Patched Death Match basement and altar arena into sector {DEATHMATCH_SECTOR}")
    print(f"Patched Void Rush waiting room and arena floor into sector {VOIDRUSH_SECTOR}")
    print(f"Patched Void Colossus raid plaza into sector {COLOSSUS_SECTOR}")
    print(f"Patched Void Dungeon dark floor ({sum(len(t) for t in dungeon_floor.values())} tiles) into sectors {', '.join(sorted(dungeon_floor))}")

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
