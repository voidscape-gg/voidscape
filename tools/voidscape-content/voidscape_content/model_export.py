from __future__ import annotations

import base64
import json
import math
import re
import struct
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import unquote, urlparse

from .jag import JagArchive
from .ob3 import (
    MAX_I16,
    MIN_I16,
    TRANSPARENT_FILL,
    Ob3Face,
    Ob3Model,
    max_dimension_from_bounds,
    ob3_bounds,
    parse_ob3,
    rgb_to_rsc,
    write_ob3,
)
from .paths import REPO_ROOT


DEFAULT_MODELS_ARCHIVE = REPO_ROOT / "Client_Base" / "Cache" / "video" / "models.orsc"


@dataclass(frozen=True)
class SourceFace:
    indices: tuple[int, ...]
    color: tuple[int, int, int] | None


@dataclass(frozen=True)
class SourceMesh:
    vertices: tuple[tuple[float, float, float], ...]
    vertex_colors: tuple[tuple[int, int, int] | None, ...]
    faces: tuple[SourceFace, ...]


@dataclass(frozen=True)
class ScaleInfo:
    source_model: str
    source_bounds: tuple[tuple[int, int], tuple[int, int], tuple[int, int]]
    source_max_dimension: int
    input_max_dimension: float
    scale: float


def _parse_color_values(values: list[str]) -> tuple[int, int, int] | None:
    if len(values) < 3:
        return None
    channels = [float(values[0]), float(values[1]), float(values[2])]
    return _float_color_to_rgb(channels)


def _float_color_to_rgb(channels: list[float] | tuple[float, ...]) -> tuple[int, int, int] | None:
    if len(channels) < 3:
        return None
    alpha = channels[3] if len(channels) >= 4 else 1.0
    if alpha <= 0.0:
        return None
    rgb = list(channels[:3])
    if all(0.0 <= value <= 1.0 for value in rgb):
        return tuple(_clamp_u8(round(value * 255.0)) for value in rgb)
    return tuple(_clamp_u8(round(value)) for value in rgb)


def _clamp_u8(value: int) -> int:
    return max(0, min(255, value))


def _average_colors(colors: list[tuple[int, int, int] | None]) -> tuple[int, int, int] | None:
    usable = [color for color in colors if color is not None]
    if not usable:
        return None
    return tuple(round(sum(color[i] for color in usable) / len(usable)) for i in range(3))  # type: ignore[return-value]


def _resolve_obj_index(raw: str, count: int) -> int:
    value = int(raw)
    if value > 0:
        index = value - 1
    else:
        index = count + value
    if index < 0 or index >= count:
        raise ValueError(f"OBJ index {raw} out of range for {count} vertices")
    return index


def _triangulate_or_keep(face_indices: list[int]) -> list[tuple[int, ...]]:
    if len(face_indices) < 3:
        raise ValueError("face has fewer than 3 vertices")
    if len(face_indices) in (3, 4):
        return [tuple(face_indices)]
    return [(face_indices[0], face_indices[i], face_indices[i + 1]) for i in range(1, len(face_indices) - 1)]


def _load_mtl(path: Path) -> dict[str, tuple[int, int, int] | None]:
    materials: dict[str, tuple[int, int, int] | None] = {}
    if not path.exists():
        return materials
    current: str | None = None
    alpha = 1.0
    diffuse: tuple[int, int, int] | None = None

    def finish() -> None:
        if current is not None:
            materials[current] = diffuse if alpha > 0.0 else None

    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        keyword = parts[0].lower()
        if keyword == "newmtl":
            finish()
            current = " ".join(parts[1:])
            diffuse = None
            alpha = 1.0
        elif keyword == "kd" and current is not None:
            diffuse = _parse_color_values(parts[1:])
        elif keyword == "d" and current is not None and len(parts) >= 2:
            alpha = float(parts[1])
        elif keyword == "tr" and current is not None and len(parts) >= 2:
            alpha = 1.0 - float(parts[1])
    finish()
    return materials


def load_obj(path: Path) -> SourceMesh:
    vertices: list[tuple[float, float, float]] = []
    vertex_colors: list[tuple[int, int, int] | None] = []
    faces: list[SourceFace] = []
    materials: dict[str, tuple[int, int, int] | None] = {}
    current_material: str | None = None

    for raw_line in path.read_text(encoding="utf-8", errors="ignore").splitlines():
        line = raw_line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        keyword = parts[0].lower()
        if keyword == "mtllib":
            mtl_name = " ".join(parts[1:])
            materials.update(_load_mtl(path.parent / mtl_name))
        elif keyword == "usemtl":
            current_material = " ".join(parts[1:])
        elif keyword == "v":
            if len(parts) < 4:
                raise ValueError(f"OBJ vertex is missing coordinates: {raw_line}")
            vertices.append((float(parts[1]), float(parts[2]), float(parts[3])))
            vertex_colors.append(_parse_color_values(parts[4:]))
        elif keyword == "f":
            polygon: list[int] = []
            for token in parts[1:]:
                vertex_token = token.split("/", 1)[0]
                polygon.append(_resolve_obj_index(vertex_token, len(vertices)))
            material_color = materials.get(current_material) if current_material is not None else None
            for face_indices in _triangulate_or_keep(polygon):
                color = material_color
                if color is None:
                    color = _average_colors([vertex_colors[index] for index in face_indices])
                faces.append(SourceFace(indices=face_indices, color=color))

    if not vertices:
        raise ValueError(f"{path} contains no OBJ vertices")
    if not faces:
        raise ValueError(f"{path} contains no OBJ faces")
    return SourceMesh(vertices=tuple(vertices), vertex_colors=tuple(vertex_colors), faces=tuple(faces))


_GLTF_COMPONENT_FORMATS = {
    5120: ("b", 1, -128, 127),
    5121: ("B", 1, 0, 255),
    5122: ("h", 2, -32768, 32767),
    5123: ("H", 2, 0, 65535),
    5125: ("I", 4, 0, 4294967295),
    5126: ("f", 4, None, None),
}
_GLTF_TYPE_COUNTS = {
    "SCALAR": 1,
    "VEC2": 2,
    "VEC3": 3,
    "VEC4": 4,
    "MAT2": 4,
    "MAT3": 9,
    "MAT4": 16,
}


def _load_gltf_container(path: Path) -> tuple[dict, list[bytes]]:
    if path.suffix.lower() == ".glb":
        raw = path.read_bytes()
        if len(raw) < 20 or raw[:4] != b"glTF":
            raise ValueError(f"{path} is not a GLB file")
        version, total_len = struct.unpack_from("<II", raw, 4)
        if version != 2:
            raise ValueError(f"unsupported GLB version: {version}")
        offset = 12
        json_chunk: bytes | None = None
        bin_chunks: list[bytes] = []
        while offset + 8 <= min(len(raw), total_len):
            chunk_len, chunk_type = struct.unpack_from("<II", raw, offset)
            offset += 8
            chunk = raw[offset : offset + chunk_len]
            offset += chunk_len
            if chunk_type == 0x4E4F534A:
                json_chunk = chunk
            elif chunk_type == 0x004E4942:
                bin_chunks.append(bytes(chunk))
        if json_chunk is None:
            raise ValueError(f"{path} has no GLB JSON chunk")
        doc = json.loads(json_chunk.decode("utf-8"))
    else:
        doc = json.loads(path.read_text(encoding="utf-8"))
        bin_chunks = []

    buffers: list[bytes] = []
    for index, buffer_def in enumerate(doc.get("buffers", [])):
        uri = buffer_def.get("uri")
        if uri is None:
            if bin_chunks:
                buffers.append(bin_chunks.pop(0))
            else:
                raise ValueError(f"glTF buffer {index} has no URI and no GLB BIN chunk")
        elif uri.startswith("data:"):
            _, encoded = uri.split(",", 1)
            buffers.append(base64.b64decode(encoded))
        else:
            parsed = urlparse(uri)
            if parsed.scheme and parsed.scheme != "file":
                raise ValueError(f"unsupported glTF buffer URI: {uri}")
            buffer_path = path.parent / unquote(parsed.path if parsed.scheme == "file" else uri)
            buffers.append(buffer_path.read_bytes())
    return doc, buffers


def _read_accessor(doc: dict, buffers: list[bytes], accessor_index: int) -> list[tuple[float | int, ...]]:
    accessor = doc["accessors"][accessor_index]
    component_type = accessor["componentType"]
    fmt, size, min_value, max_value = _GLTF_COMPONENT_FORMATS[component_type]
    component_count = _GLTF_TYPE_COUNTS[accessor["type"]]
    count = accessor["count"]
    normalized = bool(accessor.get("normalized", False))
    buffer_view = doc["bufferViews"][accessor["bufferView"]]
    buffer_data = buffers[buffer_view["buffer"]]
    byte_offset = int(buffer_view.get("byteOffset", 0)) + int(accessor.get("byteOffset", 0))
    stride = int(buffer_view.get("byteStride", size * component_count))
    unpack_fmt = "<" + fmt * component_count
    rows: list[tuple[float | int, ...]] = []
    for i in range(count):
        values = struct.unpack_from(unpack_fmt, buffer_data, byte_offset + i * stride)
        if normalized and component_type != 5126:
            normed: list[float] = []
            for value in values:
                if min_value is not None and min_value < 0:
                    normed.append(max(-1.0, float(value) / float(max_value)))
                else:
                    normed.append(float(value) / float(max_value))
            rows.append(tuple(normed))
        else:
            rows.append(values)
    return rows


def load_gltf(path: Path) -> SourceMesh:
    doc, buffers = _load_gltf_container(path)
    materials = doc.get("materials", [])
    vertices: list[tuple[float, float, float]] = []
    vertex_colors: list[tuple[int, int, int] | None] = []
    faces: list[SourceFace] = []

    for mesh_def in doc.get("meshes", []):
        for primitive in mesh_def.get("primitives", []):
            mode = primitive.get("mode", 4)
            if mode not in (4, 5, 6):
                raise ValueError(f"unsupported glTF primitive mode: {mode}")
            attrs = primitive.get("attributes", {})
            if "POSITION" not in attrs:
                continue
            positions = _read_accessor(doc, buffers, attrs["POSITION"])
            colors = (
                _read_accessor(doc, buffers, attrs["COLOR_0"])
                if "COLOR_0" in attrs
                else [None] * len(positions)
            )
            base = len(vertices)
            for position in positions:
                vertices.append((float(position[0]), float(position[1]), float(position[2])))
            for color in colors:
                if color is None:
                    vertex_colors.append(None)
                else:
                    vertex_colors.append(_float_color_to_rgb(tuple(float(v) for v in color)))

            if "indices" in primitive:
                raw_indices = _read_accessor(doc, buffers, primitive["indices"])
                indices = [base + int(row[0]) for row in raw_indices]
            else:
                indices = [base + i for i in range(len(positions))]

            material_color: tuple[int, int, int] | None = None
            material_index = primitive.get("material")
            if material_index is not None and 0 <= material_index < len(materials):
                pbr = materials[material_index].get("pbrMetallicRoughness", {})
                if "baseColorFactor" in pbr:
                    material_color = _float_color_to_rgb(tuple(float(v) for v in pbr["baseColorFactor"]))

            primitive_faces: list[tuple[int, ...]]
            if mode == 4:
                if len(indices) % 3 != 0:
                    raise ValueError("glTF TRIANGLES primitive index count is not divisible by 3")
                primitive_faces = [tuple(indices[i : i + 3]) for i in range(0, len(indices), 3)]
            elif mode == 5:
                primitive_faces = []
                for i in range(len(indices) - 2):
                    tri = (indices[i], indices[i + 1], indices[i + 2])
                    if i % 2 == 1:
                        tri = (tri[1], tri[0], tri[2])
                    primitive_faces.append(tri)
            else:
                primitive_faces = [(indices[0], indices[i], indices[i + 1]) for i in range(1, len(indices) - 1)]

            for face_indices in primitive_faces:
                color = material_color
                if color is None:
                    color = _average_colors([vertex_colors[index] for index in face_indices])
                faces.append(SourceFace(indices=face_indices, color=color))

    if not vertices:
        raise ValueError(f"{path} contains no glTF vertices")
    if not faces:
        raise ValueError(f"{path} contains no glTF faces")
    return SourceMesh(vertices=tuple(vertices), vertex_colors=tuple(vertex_colors), faces=tuple(faces))


def load_source_mesh(path: Path) -> SourceMesh:
    suffix = path.suffix.lower()
    if suffix == ".obj":
        return load_obj(path)
    if suffix in (".gltf", ".glb"):
        return load_gltf(path)
    raise ValueError(f"unsupported model input format: {path.suffix}")


def _axis_transform(vertex: tuple[float, float, float], axis: str) -> tuple[float, float, float]:
    x, y, z = vertex
    if axis == "blender":
        return x, -z, y
    if axis == "rsc":
        return x, y, z
    match = re.fullmatch(r"([+-][xyz])([+-][xyz])([+-][xyz])", axis)
    if not match:
        raise ValueError(f"invalid axis mapping: {axis}")
    source = {"x": x, "y": y, "z": z}
    result = []
    for token in match.groups():
        sign = -1.0 if token[0] == "-" else 1.0
        result.append(sign * source[token[1]])
    return result[0], result[1], result[2]


def _float_bounds(vertices: list[tuple[float, float, float]]) -> tuple[tuple[float, float], tuple[float, float], tuple[float, float]]:
    axes = list(zip(*vertices))
    return tuple((min(axis), max(axis)) for axis in axes)  # type: ignore[return-value]


def _max_float_dimension(bounds: tuple[tuple[float, float], ...]) -> float:
    return max(high - low for low, high in bounds)


def derive_scale(
    *,
    archive_path: Path,
    stock_model: str,
    input_vertices: list[tuple[float, float, float]],
) -> ScaleInfo:
    archive = JagArchive.read(archive_path)
    stock_data = archive.get(stock_model + ".ob3")
    if stock_data is None:
        raise ValueError(f"stock model not found in archive: {stock_model}.ob3")
    stock_ob3 = parse_ob3(stock_data)
    stock_bounds = ob3_bounds(stock_ob3)
    stock_dim = max_dimension_from_bounds(stock_bounds)
    input_bounds = _float_bounds(input_vertices)
    input_dim = _max_float_dimension(input_bounds)
    if input_dim <= 0.0:
        raise ValueError("input model has zero-sized bounds")
    return ScaleInfo(
        source_model=stock_model,
        source_bounds=stock_bounds,
        source_max_dimension=stock_dim,
        input_max_dimension=input_dim,
        scale=stock_dim / input_dim,
    )


def source_mesh_to_ob3(
    mesh: SourceMesh,
    *,
    scale: float,
    axis: str = "blender",
    center: bool = True,
    ground: bool = True,
    double_sided: bool = True,
) -> Ob3Model:
    transformed = [_axis_transform(vertex, axis) for vertex in mesh.vertices]
    bounds = _float_bounds(transformed)
    center_x = (bounds[0][0] + bounds[0][1]) / 2.0 if center else 0.0
    center_z = (bounds[2][0] + bounds[2][1]) / 2.0 if center else 0.0
    ground_y = bounds[1][1] if ground else 0.0

    vertices: list[tuple[int, int, int]] = []
    for x, y, z in transformed:
        ix = _round_i16((x - center_x) * scale)
        iy = _round_i16((y - ground_y) * scale)
        iz = _round_i16((z - center_z) * scale)
        vertices.append((ix, iy, iz))

    faces: list[Ob3Face] = []
    for source_face in mesh.faces:
        fill = TRANSPARENT_FILL
        if source_face.color is not None:
            fill = rgb_to_rsc(*source_face.color)
        back_fill = fill if double_sided else TRANSPARENT_FILL
        faces.append(
            Ob3Face(
                indices=source_face.indices,
                front_fill=fill,
                back_fill=back_fill,
                lighting=0,
            )
        )
    return Ob3Model(vertices=tuple(vertices), faces=tuple(faces))


def _round_i16(value: float) -> int:
    rounded = int(round(value))
    if rounded < MIN_I16 or rounded > MAX_I16:
        raise ValueError(f"scaled coordinate out of int16 range: {rounded}")
    return rounded


def import_model_to_archive(
    input_path: Path,
    *,
    output_name: str,
    archive_path: Path = DEFAULT_MODELS_ARCHIVE,
    scale_from: str = "crate",
    scale: float | None = None,
    axis: str = "blender",
    center: bool = True,
    ground: bool = True,
    double_sided: bool = True,
    replace: bool = True,
) -> tuple[Ob3Model, bytes, ScaleInfo | None]:
    mesh = load_source_mesh(input_path)
    transformed = [_axis_transform(vertex, axis) for vertex in mesh.vertices]
    scale_info = None
    if scale is None:
        scale_info = derive_scale(
            archive_path=archive_path,
            stock_model=scale_from,
            input_vertices=transformed,
        )
        scale = scale_info.scale
    ob3 = source_mesh_to_ob3(
        mesh,
        scale=scale,
        axis=axis,
        center=center,
        ground=ground,
        double_sided=double_sided,
    )
    ob3_bytes = write_ob3(ob3)
    archive = JagArchive.read(archive_path)
    archive.put(output_name + ".ob3", ob3_bytes, replace=replace)
    archive.write(archive_path)
    return ob3, ob3_bytes, scale_info
