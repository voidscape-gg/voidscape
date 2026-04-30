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

Coordinate transform: the enclave at worldX=208..230, worldY=341..362 fits
entirely in sector h0x52y44 which covers worldX=192..239, worldY=336..383.
Local tile coords: tx = worldX - 192, ty = worldY - 336.
"""

import io
import shutil
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
AUTHENTIC = REPO / "server/conf/server/data/Authentic_Landscape.orsc"
CUSTOM    = REPO / "server/conf/server/data/Custom_Landscape.orsc"
# Client reads its own copy when S_WANT_CUSTOM_LANDSCAPE is true (World.java:80-81).
CLIENT_CUSTOM = REPO / "Client_Base/Cache/video/Custom_Landscape.orsc"
ENCLAVE_SECTOR = "h0x52y44"
ENCLAVE_SECTOR_BASE_X = 192   # worldX of local tx=0
ENCLAVE_SECTOR_BASE_Y = 336   # worldY of local ty=0

VOID_ISLAND_SECTOR = "h0x48y37"
VOID_ISLAND_SECTOR_BASE_X = 0
VOID_ISLAND_SECTOR_BASE_Y = 0
VOID_ISLAND_CENTER_X = 24
VOID_ISLAND_CENTER_Y = 24
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

# DoorDef IDs
HIGHWALL = 7
HIGH_DOOR = 8
WALL = 0
DOOR = 2
WEB = 24               # vanilla spider web — CutWeb.java handles cutting (kept for fallback)
VOID_WALL = 214        # voidbricks-textured sanctum wall (DoorDef slot 214)
VOID_HIGHWALL = 215    # voidouter-textured perimeter wall, tall (DoorDef slot 215)
VOID_SIGIL_WALL = 216  # voidsigilwall accent (DoorDef slot 216) — pentagram mural
VOID_WEB = 217         # AI-textured glowing void cobweb at gates, tall (modelVar1=275)
VOID_WINDOW = 218      # AI-textured stained-glass arched window with pentagram

# Tile texture IDs (empirically found from Authentic_Landscape buildings)
ROOF_STANDARD = 1     # most-common indoor roof texture (4535 tile occurrences)
# Overlay byte = TileDef index + 1.
FLOOR_INDOOR = 26     # Void Floor (TileDef 25): dark charcoal with subtle purple — main floor
FLOOR_RITUAL = 27     # Void Floor Accent (TileDef 26): bright magenta-purple — ritual circle + dots
FLOOR_MID    = 28     # Void Floor Mid (TileDef 27): mid-purple — ring around ritual circle

# === Enclave footprint ===
ENCLAVE_MIN_X, ENCLAVE_MAX_X = 208, 229
ENCLAVE_MIN_Y, ENCLAVE_MAX_Y = 341, 361

# Building interiors (roof rendered above these tiles, indoor floor underneath)
BUILDING_INTERIORS = [
    # (minX, maxX, minY, maxY)
    (215, 222, 348, 354),  # central sanctum (8x7 floor)
    (211, 213, 348, 350),  # west bank shrine (3x3 floor)
    (225, 227, 348, 350),  # east pool shrine (3x3 floor)
]

# === Layout (mirrors what we generate into BoundaryLocsVoidEnclave.json today) ===

def enclave_walls():
    """Yield (worldX, worldY, direction, doorDefId) for every wall in the enclave."""
    walls = []

    def b(x, y, direction, id_):
        walls.append((x, y, direction, id_))

    # Outer perimeter (interior X=208..229, Y=341..361). Gates at N/S/W/E center.
    # Void Highwall = AI-textured imposing void stone. Gate tiles are LEFT EMPTY in
    # the landscape (no wall byte) — webs are placed as JSON BoundaryLocs entries
    # in BoundaryLocsVoidEnclave.json so they have GameObjects that CutWeb.java can
    # hook for the cut interaction. Landscape-baked walls have no GameObject and
    # are therefore unclickable.
    for x in range(208, 230):
        if x != 219:
            b(x, 341, 0, VOID_HIGHWALL)
            b(x, 362, 0, VOID_HIGHWALL)
    for y in range(341, 362):
        if y != 351:
            b(208, y, 1, VOID_HIGHWALL)
            b(230, y, 1, VOID_HIGHWALL)

    # Central sanctum, interior X=214..222, Y=347..354. Open south at X=218.
    # Most walls = voidbricks. North midpoint = pentagram mural (Sigil Wall).
    # West and East midpoints = stained-glass void windows for cathedral feel.
    SIGIL_NORTH_X = 218
    WINDOW_WEST_Y = 351
    WINDOW_EAST_Y = 351
    for x in range(214, 223):
        b(x, 347, 0, VOID_SIGIL_WALL if x == SIGIL_NORTH_X else VOID_WALL)
    for x in range(214, 223):
        if x == 218:
            continue   # south door opening
        b(x, 355, 0, VOID_WALL)
    for y in range(347, 355):
        b(214, y, 1, VOID_WINDOW if y == WINDOW_WEST_Y else VOID_WALL)
        b(223, y, 1, VOID_WINDOW if y == WINDOW_EAST_Y else VOID_WALL)

    # West bank shrine — same Void Wall (voidbricks) as the sanctum + 1 sigil mural
    # on the north wall midpoint. Interior X=210..213, Y=347..350. Open south at X=212.
    BANK_SIGIL_X = 212
    for x in range(210, 214):
        b(x, 347, 0, VOID_SIGIL_WALL if x == BANK_SIGIL_X else VOID_WALL)
    for x in range(210, 214):
        if x == 212:
            continue
        b(x, 351, 0, VOID_WALL)
    for y in range(347, 351):
        b(210, y, 1, VOID_WALL)
        b(214, y, 1, VOID_WALL)

    # East pool shrine — same Void Wall + 1 sigil mural on north wall midpoint.
    # Interior X=224..227, Y=347..350. Open south at X=225.
    POOL_SIGIL_X = 225
    for x in range(224, 228):
        b(x, 347, 0, VOID_SIGIL_WALL if x == POOL_SIGIL_X else VOID_WALL)
    for x in range(224, 228):
        if x == 225:
            continue
        b(x, 351, 0, VOID_WALL)
    for y in range(347, 351):
        b(224, y, 1, VOID_WALL)
        b(228, y, 1, VOID_WALL)

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

    # 2. Floor: plain grey across the whole enclave. No patterns, no accents — the
    # void walls + sigil murals + windows are the visual story.
    for x in range(ENCLAVE_MIN_X, ENCLAVE_MAX_X + 1):
        for y in range(ENCLAVE_MIN_Y, ENCLAVE_MAX_Y + 1):
            buf[tile_offset(x, y) + 2] = FLOOR_INDOOR

    # 3. Roofs: intentionally NOT set. The roof models in this engine read poorly
    #    inside RSC's small viewport when toggled on, so we leave the byte at 0.

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

    for x, y in land:
        off = tile_offset(x, y)
        edge = any((x + ox, y + oy) not in land for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        inner_ring = abs(x - VOID_ISLAND_CENTER_X) + abs(y - VOID_ISLAND_CENTER_Y) in (2, 3)
        center = abs(x - VOID_ISLAND_CENTER_X) <= 1 and abs(y - VOID_ISLAND_CENTER_Y) <= 1

        buf[off + 0] = 52 if edge else 68
        buf[off + 1] = (112 + ((x * 7 + y * 3) % 20)) & 0xFF
        if center:
            buf[off + 2] = FLOOR_RITUAL
        elif inner_ring:
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


def main():
    # 1. Read clean source sectors from Authentic
    with zipfile.ZipFile(AUTHENTIC) as z:
        enclave_source = z.read(ENCLAVE_SECTOR)
        island_source = z.read(VOID_ISLAND_SECTOR)
        catchsim_sources = {sector: z.read(sector) for sector, _, _, _, _ in CATCHSIM_SECTORS}
        legacy_sources = {sector: z.read(sector) for sector in LEGACY_VOID_ISLAND_SECTORS}
    print(f"Read {len(enclave_source)} bytes from {AUTHENTIC.name}!{ENCLAVE_SECTOR}")
    print(f"Read {len(island_source)} bytes from {AUTHENTIC.name}!{VOID_ISLAND_SECTOR}")
    for sector, source in catchsim_sources.items():
        print(f"Read {len(source)} bytes from {AUTHENTIC.name}!{sector}")

    # 2. Apply patches
    walls = enclave_walls()
    patched_sectors = {
        ENCLAVE_SECTOR: patch_enclave_sector(enclave_source),
        VOID_ISLAND_SECTOR: patch_void_island_sector(island_source),
    }
    for sector, base_x, base_y, offset_x, offset_y in CATCHSIM_SECTORS:
        patched_sectors[sector] = patch_catchsim_island_sector(
            catchsim_sources[sector], sector, base_x, base_y, offset_x, offset_y
        )
    patched_sectors.update(legacy_sources)
    print(f"Patched {len(walls)} enclave walls into sector {ENCLAVE_SECTOR}")
    print(f"Patched isolated Void Island into sector {VOID_ISLAND_SECTOR}")
    print(f"Patched {len(CATCHSIM_SECTORS)} PK Catching Simulator islands")

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
