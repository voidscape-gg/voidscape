from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


TRANSPARENT_FILL = 32767
MIN_I16 = -32768
MAX_I16 = 32767


@dataclass(frozen=True)
class Ob3Face:
    indices: tuple[int, ...]
    front_fill: int
    back_fill: int
    lighting: int = 0


@dataclass(frozen=True)
class Ob3Model:
    vertices: tuple[tuple[int, int, int], ...]
    faces: tuple[Ob3Face, ...]


def rgb_to_rsc(r: int, g: int, b: int) -> int:
    r5 = (r & 0xFF) >> 3
    g5 = (g & 0xFF) >> 3
    b5 = (b & 0xFF) >> 3
    return -1 - ((r5 << 10) | (g5 << 5) | b5)


def rsc_to_rgb(fill: int) -> tuple[int, int, int] | None:
    if fill == TRANSPARENT_FILL or fill >= 0:
        return None
    rgb555 = -1 - fill
    r = ((rgb555 & 0x7C00) >> 10) << 3
    g = ((rgb555 & 0x03E0) >> 5) << 3
    b = (rgb555 & 0x001F) << 3
    return r, g, b


def read_u16be(data: bytes | bytearray | memoryview, offset: int) -> int:
    return (data[offset] << 8) | data[offset + 1]


def read_i16be(data: bytes | bytearray | memoryview, offset: int) -> int:
    value = read_u16be(data, offset)
    if value > 32767:
        value -= 65536
    return value


def write_u16be(value: int) -> bytes:
    if not 0 <= value <= 0xFFFF:
        raise ValueError(f"uint16 out of range: {value}")
    return bytes(((value >> 8) & 0xFF, value & 0xFF))


def write_i16be(value: int) -> bytes:
    if not MIN_I16 <= value <= MAX_I16:
        raise ValueError(f"int16 out of range: {value}")
    return write_u16be(value & 0xFFFF)


def parse_ob3(data: bytes | bytearray | memoryview) -> Ob3Model:
    if len(data) < 4:
        raise ValueError("OB3 data is too short")
    vertex_count = read_u16be(data, 0)
    face_count = read_u16be(data, 2)
    offset = 4

    xs: list[int] = []
    ys: list[int] = []
    zs: list[int] = []
    for _ in range(vertex_count):
        xs.append(read_i16be(data, offset))
        offset += 2
    for _ in range(vertex_count):
        ys.append(read_i16be(data, offset))
        offset += 2
    for _ in range(vertex_count):
        zs.append(read_i16be(data, offset))
        offset += 2

    if offset + face_count > len(data):
        raise ValueError("OB3 face vertex count block overruns data")
    face_sizes = [data[offset + i] for i in range(face_count)]
    offset += face_count

    front_fills: list[int] = []
    back_fills: list[int] = []
    for _ in range(face_count):
        front_fills.append(read_i16be(data, offset))
        offset += 2
    for _ in range(face_count):
        back_fills.append(read_i16be(data, offset))
        offset += 2

    if offset + face_count > len(data):
        raise ValueError("OB3 lighting block overruns data")
    lightings = [data[offset + i] for i in range(face_count)]
    offset += face_count

    index_size = 1 if vertex_count < 256 else 2
    faces: list[Ob3Face] = []
    for face_index, size in enumerate(face_sizes):
        if size < 3:
            raise ValueError(f"OB3 face {face_index} has fewer than 3 vertices")
        indices: list[int] = []
        for _ in range(size):
            if offset + index_size > len(data):
                raise ValueError("OB3 index block overruns data")
            if index_size == 1:
                value = data[offset]
                offset += 1
            else:
                value = read_u16be(data, offset)
                offset += 2
            if value >= vertex_count:
                raise ValueError(f"OB3 face index {value} >= vertex count {vertex_count}")
            indices.append(value)
        faces.append(
            Ob3Face(
                indices=tuple(indices),
                front_fill=front_fills[face_index],
                back_fill=back_fills[face_index],
                lighting=lightings[face_index],
            )
        )

    vertices = tuple(zip(xs, ys, zs))
    return Ob3Model(vertices=vertices, faces=tuple(faces))


def write_ob3(model: Ob3Model) -> bytes:
    vertex_count = len(model.vertices)
    face_count = len(model.faces)
    if vertex_count > 0xFFFF:
        raise ValueError(f"too many vertices for OB3: {vertex_count}")
    if face_count > 0xFFFF:
        raise ValueError(f"too many faces for OB3: {face_count}")
    if vertex_count >= 256 and any(index > 0xFFFF for face in model.faces for index in face.indices):
        raise ValueError("OB3 uint16 face index overflow")

    out = bytearray()
    out += write_u16be(vertex_count)
    out += write_u16be(face_count)

    for axis in range(3):
        for vertex in model.vertices:
            value = vertex[axis]
            if not MIN_I16 <= value <= MAX_I16:
                raise ValueError(f"vertex coordinate out of int16 range: {value}")
            out += write_i16be(value)

    for face in model.faces:
        if not 3 <= len(face.indices) <= 0xFF:
            raise ValueError(f"OB3 face vertex count out of range: {len(face.indices)}")
        out.append(len(face.indices))

    for face in model.faces:
        out += write_i16be(face.front_fill)
    for face in model.faces:
        out += write_i16be(face.back_fill)
    for face in model.faces:
        if not 0 <= face.lighting <= 0xFF:
            raise ValueError(f"lighting flag out of range: {face.lighting}")
        out.append(face.lighting)

    wide_indices = vertex_count >= 256
    for face in model.faces:
        for index in face.indices:
            if index < 0 or index >= vertex_count:
                raise ValueError(f"face index {index} out of range for {vertex_count} vertices")
            if wide_indices:
                out += write_u16be(index)
            else:
                if index > 0xFF:
                    raise ValueError(f"uint8 face index overflow: {index}")
                out.append(index)
    return bytes(out)


def ob3_bounds(model: Ob3Model) -> tuple[tuple[int, int], tuple[int, int], tuple[int, int]]:
    if not model.vertices:
        raise ValueError("cannot measure bounds of an empty model")
    axes = list(zip(*model.vertices))
    return tuple((min(axis), max(axis)) for axis in axes)  # type: ignore[return-value]


def max_dimension_from_bounds(bounds: Iterable[tuple[int, int]]) -> int:
    return max(high - low for low, high in bounds)


def read_ob3_file(path: Path) -> Ob3Model:
    return parse_ob3(path.read_bytes())


def write_ob3_file(path: Path, model: Ob3Model) -> None:
    path.write_bytes(write_ob3(model))
