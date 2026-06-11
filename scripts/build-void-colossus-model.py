#!/usr/bin/env python3
"""Generate and pack the custom Void Colossus encounter art.

OpenRSC's model archive stores simple .ob3 files inside an uncompressed JAG-like
archive. This script creates low-poly object-space models and appends/replaces
them in Client_Base/Cache/video/models.orsc.

Projectile sprites live in Authentic_Sprites.orsc as raw Sprite.pack blobs inside
a zip archive. The two Colossus-only projectile slots are generated here too so
the whole boss visual pass is reproducible.
"""

from __future__ import annotations

import math
import os
import struct
import tempfile
import zipfile
from pathlib import Path
from typing import Iterable


REPO = Path(__file__).resolve().parents[1]
MODELS = REPO / "Client_Base/Cache/video/models.orsc"
SPRITES = REPO / "Client_Base/Cache/video/Authentic_Sprites.orsc"
MODEL_NAME = "voidcolossus3d.ob3"
SHARD_CHARGE_MODEL_NAME = "voidshardcharge.ob3"
CLAW_CHARGE_MODEL_NAME = "voidclawcharge.ob3"
VOID_SHARD_PROJECTILE_SPRITE = 3167
VOID_CLAW_PROJECTILE_SPRITE = 3168


def jag_hash(name: str) -> int:
    value = 0
    for char in name.upper():
        value = value * 61 + ord(char) - 32
    return value & 0xFFFFFFFF


def rgb555(red: int, green: int, blue: int) -> int:
    red = max(0, min(31, red))
    green = max(0, min(31, green))
    blue = max(0, min(31, blue))
    packed = (red << 10) | (green << 5) | blue
    return -(packed + 1)


VOID_BLACK = rgb555(3, 2, 5)
VOID_DARK = rgb555(7, 3, 11)
VOID_ARMOUR = rgb555(10, 5, 14)
VOID_PURPLE = rgb555(20, 5, 30)
VOID_GLOW = rgb555(29, 11, 31)
VOID_HOT = rgb555(31, 17, 31)
VOID_STONE = rgb555(8, 8, 10)
VOID_EDGE = rgb555(17, 9, 22)
VOID_METAL_LIGHT = rgb555(15, 10, 17)


class Model:
    def __init__(self) -> None:
        self.vertices: list[tuple[int, int, int]] = []
        self.faces: list[tuple[list[int], int, int, int]] = []

    def vertex(self, x: float, y: float, z: float) -> int:
        self.vertices.append((round(x), round(y), round(z)))
        return len(self.vertices) - 1

    def scale(self, sx: float, sy: float, sz: float) -> None:
        self.vertices = [(round(x * sx), round(y * sy), round(z * sz)) for x, y, z in self.vertices]

    def face(self, indices: Iterable[int], color: int, diffuse: int = 0) -> None:
        verts = list(indices)
        if len(verts) < 3:
            raise ValueError("faces need at least three vertices")
        self.faces.append((verts, color, color, diffuse))

    def prism(
        self,
        x1: float,
        x2: float,
        y1: float,
        y2: float,
        z1: float,
        z2: float,
        color: int,
        top_color: int | None = None,
    ) -> None:
        v = [
            self.vertex(x1, y1, z1),
            self.vertex(x2, y1, z1),
            self.vertex(x2, y1, z2),
            self.vertex(x1, y1, z2),
            self.vertex(x1, y2, z1),
            self.vertex(x2, y2, z1),
            self.vertex(x2, y2, z2),
            self.vertex(x1, y2, z2),
        ]
        top = top_color if top_color is not None else color
        self.face([v[0], v[1], v[2], v[3]], color)
        self.face([v[4], v[7], v[6], v[5]], top)
        self.face([v[0], v[4], v[5], v[1]], color)
        self.face([v[1], v[5], v[6], v[2]], color)
        self.face([v[2], v[6], v[7], v[3]], color)
        self.face([v[3], v[7], v[4], v[0]], color)

    def cylinder(
        self,
        y1: float,
        y2: float,
        r1x: float,
        r1z: float,
        r2x: float,
        r2z: float,
        color: int,
        segments: int = 10,
        cap: bool = True,
        x: float = 0,
        z: float = 0,
        alt_color: int | None = None,
    ) -> None:
        bottom: list[int] = []
        top: list[int] = []
        for i in range(segments):
            angle = 2.0 * math.pi * i / segments
            ca = math.cos(angle)
            sa = math.sin(angle)
            bottom.append(self.vertex(x + ca * r1x, y1, z + sa * r1z))
            top.append(self.vertex(x + ca * r2x, y2, z + sa * r2z))
        for i in range(segments):
            c = alt_color if alt_color is not None and i % 2 else color
            self.face([bottom[i], bottom[(i + 1) % segments], top[(i + 1) % segments], top[i]], c)
        if cap:
            self.face(list(reversed(bottom)), color)
            self.face(top, color)

    def cone(
        self,
        base_y: float,
        tip: tuple[float, float, float],
        rx: float,
        rz: float,
        color: int,
        segments: int = 8,
        x: float = 0,
        z: float = 0,
    ) -> None:
        ring = []
        for i in range(segments):
            angle = 2.0 * math.pi * i / segments
            ring.append(self.vertex(x + math.cos(angle) * rx, base_y, z + math.sin(angle) * rz))
        tip_i = self.vertex(*tip)
        for i in range(segments):
            self.face([ring[i], ring[(i + 1) % segments], tip_i], color)
        self.face(list(reversed(ring)), color)

    def diamond(
        self,
        center: tuple[float, float, float],
        rx: float,
        ry: float,
        rz: float,
        color: int,
        segments: int = 8,
        alt_color: int | None = None,
    ) -> None:
        cx, cy, cz = center
        ring = []
        for i in range(segments):
            angle = 2.0 * math.pi * i / segments
            ring.append(self.vertex(cx + math.cos(angle) * rx, cy, cz + math.sin(angle) * rz))
        top = self.vertex(cx, cy - ry, cz)
        bottom = self.vertex(cx, cy + ry, cz)
        for i in range(segments):
            c = alt_color if alt_color is not None and i % 2 else color
            self.face([ring[i], ring[(i + 1) % segments], top], c)
            self.face([ring[(i + 1) % segments], ring[i], bottom], c)

    def cylinder_between(
        self,
        start: tuple[float, float, float],
        end: tuple[float, float, float],
        radius: float,
        color: int,
        segments: int = 8,
    ) -> None:
        sx, sy, sz = start
        ex, ey, ez = end
        axis = (ex - sx, ey - sy, ez - sz)
        length = math.sqrt(sum(v * v for v in axis))
        if length <= 0:
            return
        ax = tuple(v / length for v in axis)
        helper = (0.0, 1.0, 0.0) if abs(ax[1]) < 0.92 else (1.0, 0.0, 0.0)
        u = cross(ax, helper)
        u_len = math.sqrt(sum(v * v for v in u))
        u = tuple(v / u_len for v in u)
        v = cross(ax, u)
        ring_a = []
        ring_b = []
        for i in range(segments):
            angle = 2.0 * math.pi * i / segments
            off = tuple((math.cos(angle) * u[j] + math.sin(angle) * v[j]) * radius for j in range(3))
            ring_a.append(self.vertex(sx + off[0], sy + off[1], sz + off[2]))
            ring_b.append(self.vertex(ex + off[0], ey + off[1], ez + off[2]))
        for i in range(segments):
            self.face([ring_a[i], ring_a[(i + 1) % segments], ring_b[(i + 1) % segments], ring_b[i]], color)

    def to_ob3(self) -> bytes:
        if len(self.vertices) > 65535:
            raise ValueError(f"model has {len(self.vertices)} vertices; .ob3 supports at most 65535")
        data = bytearray()
        data += len(self.vertices).to_bytes(2, "big")
        data += len(self.faces).to_bytes(2, "big")
        for axis in range(3):
            for vertex in self.vertices:
                data += int(vertex[axis]).to_bytes(2, "big", signed=True)
        for face, _front, _back, _diffuse in self.faces:
            data.append(len(face))
        for _face, front, _back, _diffuse in self.faces:
            data += int(front).to_bytes(2, "big", signed=True)
        for _face, _front, back, _diffuse in self.faces:
            data += int(back).to_bytes(2, "big", signed=True)
        for _face, _front, _back, diffuse in self.faces:
            data.append(1 if diffuse else 0)
        for face, _front, _back, _diffuse in self.faces:
            for index in face:
                if len(self.vertices) < 256:
                    data.append(index)
                else:
                    data += index.to_bytes(2, "big")
        return bytes(data)


def cross(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def build_model() -> bytes:
	model = Model()

	# The Colossus body is handled by the high-detail NPC sprite sheet. This scenery model is
	# only the physical rift/base under it, so it adds depth without fighting the sprite art.
	model.cylinder(12, -14, 310, 310, 294, 294, VOID_STONE, segments=24, alt_color=VOID_EDGE)
	model.cylinder(-12, -34, 272, 272, 248, 248, VOID_BLACK, segments=24, alt_color=VOID_STONE)
	model.cylinder(-32, -58, 218, 218, 196, 196, VOID_EDGE, segments=20, alt_color=VOID_PURPLE)
	model.cylinder(-56, -92, 168, 168, 124, 124, VOID_DARK, segments=18, alt_color=VOID_GLOW)

	# Low, bright rift core. Keep this short enough that it reads as a portal at the boss's feet,
	# not as a replacement body.
	model.cylinder(-84, -150, 112, 112, 68, 68, VOID_PURPLE, segments=16, alt_color=VOID_GLOW)
	model.cylinder(-142, -222, 72, 72, 34, 34, VOID_BLACK, segments=12, alt_color=VOID_PURPLE)
	model.diamond((0, -150, 0), 74, 86, 74, VOID_GLOW, segments=10, alt_color=VOID_HOT)
	model.diamond((0, -244, 0), 34, 56, 34, VOID_PURPLE, segments=8, alt_color=VOID_HOT)

	# Cracked platform slabs, roughly matching the arena language without trying to be organic.
	for x1, x2, z1, z2 in [
		(-290, -156, -42, 30),
		(150, 288, -30, 48),
		(-40, 46, -294, -156),
		(-52, 58, 154, 292),
		(-222, -126, 142, 238),
		(130, 226, -238, -140),
	]:
		model.prism(x1, x2, -6, -18, z1, z2, VOID_STONE, VOID_EDGE)

	# Jagged void crystals around the base add the purple silhouette from the concept art while
	# staying below the restored boss sprite's torso.
	for x, z, h, r in [
		(-265, -202, -132, 30),
		(262, -190, -142, 34),
		(-248, 208, -126, 28),
		(252, 204, -138, 32),
		(-112, -284, -104, 24),
		(116, 286, -112, 24),
		(-16, -316, -86, 20),
		(18, 318, -92, 20),
	]:
		model.cone(0, (x, h, z), r, r * 0.72, VOID_GLOW, segments=7, x=x * 0.96, z=z * 0.96)
		model.diamond((x * 0.97, h + 20, z * 0.97), r * 0.46, r * 0.72, r * 0.42,
					  VOID_PURPLE, segments=6, alt_color=VOID_HOT)

	# Four small binding pylons frame the target area and make the object's 4x4 footprint obvious.
	for x, z in [(-210, -210), (210, -210), (-210, 210), (210, 210)]:
		model.cylinder(0, -62, 26, 26, 21, 21, VOID_ARMOUR, segments=8, alt_color=VOID_EDGE, x=x, z=z)
		model.diamond((x, -92, z), 32, 38, 32, VOID_GLOW, segments=8, alt_color=VOID_HOT)

	return model.to_ob3()


def build_shard_charge_model() -> bytes:
    model = Model()
    model.cylinder(0, -18, 72, 72, 54, 54, VOID_PURPLE, segments=12, alt_color=VOID_GLOW)
    model.diamond((0, -88, 0), 54, 88, 54, VOID_GLOW, segments=8, alt_color=VOID_HOT)
    model.cone(-36, (0, -185, 0), 28, 28, VOID_HOT, segments=8, x=0, z=0)
    for x, z in [(-72, 0), (72, 0), (0, -72), (0, 72)]:
        model.cone(-8, (x, -98, z), 14, 14, VOID_PURPLE, segments=6, x=x * 0.45, z=z * 0.45)
    return model.to_ob3()


def build_claw_charge_model() -> bytes:
    model = Model()
    model.cylinder(0, -10, 92, 92, 92, 92, VOID_BLACK, segments=16, alt_color=VOID_PURPLE)
    model.cylinder(-10, -26, 70, 70, 38, 38, VOID_GLOW, segments=12, alt_color=VOID_HOT)
    for angle in (0, 2.0 * math.pi / 3.0, 4.0 * math.pi / 3.0):
        x = math.cos(angle) * 68
        z = math.sin(angle) * 68
        model.cone(-22, (x * 1.45, -125, z * 1.45), 16, 10, VOID_HOT, segments=6, x=x, z=z)
    model.diamond((0, -70, 0), 38, 62, 38, VOID_PURPLE, segments=8, alt_color=VOID_GLOW)
    return model.to_ob3()


def read_archive(path: Path) -> tuple[bytes, list[tuple[int, bytes]]]:
    raw = path.read_bytes()
    decmp_len = int.from_bytes(raw[0:3], "big")
    cmp_len = int.from_bytes(raw[3:6], "big")
    if decmp_len != cmp_len:
        raise RuntimeError("models.orsc is compressed; this script expects the current uncompressed archive")
    archive = raw[6:]
    count = int.from_bytes(archive[0:2], "big")
    offset = 2 + count * 10
    entries = []
    for index in range(count):
        header = 2 + index * 10
        file_hash = int.from_bytes(archive[header:header + 4], "big")
        file_len = int.from_bytes(archive[header + 4:header + 7], "big")
        cmp_file_len = int.from_bytes(archive[header + 7:header + 10], "big")
        if file_len != cmp_file_len:
            raise RuntimeError("compressed inner model entry found; not supported by this writer")
        entries.append((file_hash, archive[offset:offset + file_len]))
        offset += cmp_file_len
    return raw[:6], entries


def write_archive(path: Path, entries: list[tuple[int, bytes]]) -> None:
    inner = bytearray()
    inner += len(entries).to_bytes(2, "big")
    for file_hash, data in entries:
        inner += file_hash.to_bytes(4, "big")
        inner += len(data).to_bytes(3, "big")
        inner += len(data).to_bytes(3, "big")
    for _file_hash, data in entries:
        inner += data
    raw = bytearray()
    raw += len(inner).to_bytes(3, "big")
    raw += len(inner).to_bytes(3, "big")
    raw += inner
    path.write_bytes(raw)


def replace_model_entries(path: Path, replacements: dict[str, bytes]) -> None:
    _outer, entries = read_archive(path)
    hashes = {jag_hash(name): data for name, data in replacements.items()}
    replaced = {name: False for name in replacements}
    name_by_hash = {jag_hash(name): name for name in replacements}
    new_entries = []
    for file_hash, data in entries:
        if file_hash in hashes:
            new_entries.append((file_hash, hashes[file_hash]))
            replaced[name_by_hash[file_hash]] = True
        else:
            new_entries.append((file_hash, data))
    for name, data in replacements.items():
        if not replaced[name]:
            new_entries.append((jag_hash(name), data))
    write_archive(path, new_entries)
    for name, data in replacements.items():
        print(f"{'replaced' if replaced[name] else 'added'} {name}: {len(data)} bytes")
    print(f"models.orsc entries: {len(new_entries)}")


def pack_sprite(width: int, height: int, pixels: list[int]) -> bytes:
    if len(pixels) != width * height:
        raise ValueError(f"sprite has {len(pixels)} pixels, expected {width * height}")
    header = struct.pack(">iiBiiii", width, height, 1, 0, 0, 32, 32)
    return header + b"".join(struct.pack(">i", pixel) for pixel in pixels)


def rgb(red: int, green: int, blue: int) -> int:
    return (red << 16) | (green << 8) | blue


def build_void_shard_projectile() -> bytes:
    colors = {
        "core": rgb(245, 198, 255),
        "hot": rgb(204, 70, 255),
        "edge": rgb(119, 22, 188),
        "dark": rgb(48, 10, 82),
    }
    pixels: list[int] = []
    for y in range(32):
        for x in range(32):
            # Long diagonal shard with a bright comet head.
            diag = abs((y - 21) - (0.72 * (x - 5)))
            along = (x - 5) + (21 - y) * 0.72
            head = math.hypot(x - 23, y - 9)
            if head <= 3.2:
                pixels.append(colors["core"])
            elif head <= 5.6:
                pixels.append(colors["hot"])
            elif 1.2 < along < 26 and diag <= 1.5:
                pixels.append(colors["hot"])
            elif 0 < along < 28 and diag <= 3.1:
                pixels.append(colors["edge"])
            elif 0 < along < 24 and diag <= 4.4 and (x + y) % 2 == 0:
                pixels.append(colors["dark"])
            else:
                pixels.append(0)
    return pack_sprite(32, 32, pixels)


def build_void_claw_projectile() -> bytes:
    colors = {
        "core": rgb(252, 219, 255),
        "hot": rgb(213, 68, 255),
        "edge": rgb(115, 16, 191),
        "dark": rgb(39, 6, 70),
    }
    pixels: list[int] = []
    for y in range(32):
        for x in range(32):
            dx = x - 16
            dy = y - 16
            radius = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)
            spiral = abs(((radius * 0.58 + angle * 4.2) % (2.0 * math.pi)) - math.pi)
            prong = min(abs(angle - a) for a in (-2.25, -0.2, 1.85))
            if radius <= 3.0:
                pixels.append(colors["core"])
            elif 5.0 <= radius <= 11.5 and spiral < 0.34:
                pixels.append(colors["hot"])
            elif 8.0 <= radius <= 14.5 and (spiral < 0.52 or prong < 0.18):
                pixels.append(colors["edge"])
            elif 11.0 <= radius <= 15.5 and (spiral < 0.78 or prong < 0.25) and (x + y) % 2 == 0:
                pixels.append(colors["dark"])
            else:
                pixels.append(0)
    return pack_sprite(32, 32, pixels)


def write_projectile_sprites(path: Path) -> None:
    replacements = {
        str(VOID_SHARD_PROJECTILE_SPRITE): build_void_shard_projectile(),
        str(VOID_CLAW_PROJECTILE_SPRITE): build_void_claw_projectile(),
    }
    fd, temp_name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=str(path.parent))
    os.close(fd)
    temp_path = Path(temp_name)
    try:
        seen: set[str] = set()
        with zipfile.ZipFile(path, "r") as zin, zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                if info.filename in replacements:
                    zout.writestr(info.filename, replacements[info.filename])
                    seen.add(info.filename)
                else:
                    zout.writestr(info, zin.read(info.filename))
            for name, data in replacements.items():
                if name not in seen:
                    zout.writestr(name, data)
        temp_path.replace(path)
    finally:
        if temp_path.exists():
            temp_path.unlink()
    for name, data in replacements.items():
        print(f"packed projectile sprite {name}: {len(data)} bytes")


def main() -> None:
    replace_model_entries(MODELS, {
        MODEL_NAME: build_model(),
        SHARD_CHARGE_MODEL_NAME: build_shard_charge_model(),
        CLAW_CHARGE_MODEL_NAME: build_claw_charge_model(),
    })
    write_projectile_sprites(SPRITES)


if __name__ == "__main__":
    main()
