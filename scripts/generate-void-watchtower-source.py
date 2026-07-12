#!/usr/bin/env python3
"""Generate Voidscape's original low-poly Void watchtower source mesh.

The output is deliberately plain OBJ/MTL: Blender-style Z-up coordinates,
triangular faces, flat material colors, no textures, and no external assets.
Run with --check to prove that the committed source matches this generator.
"""

from __future__ import annotations

import argparse
import hashlib
import math
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = REPO_ROOT / "content/custom/void_watchtower/art/source"
OBJ_PATH = SOURCE_DIR / "void_watchtower.obj"
MTL_PATH = SOURCE_DIR / "void_watchtower.mtl"

# Insertion order is the stable order in the generated MTL and report.
MATERIALS: dict[str, tuple[int, int, int]] = {
    "deep_void": (27, 21, 33),
    "voidstone_dark": (52, 48, 60),
    "voidstone_edge": (78, 71, 88),
    "gunmetal": (101, 94, 110),
    "bruised_violet": (126, 59, 148),
    "beacon_violet": (184, 97, 216),
}

# Filled after the first deterministic generation. These make --check reject
# even a self-consistent but unreviewed regeneration.
EXPECTED_OBJ_SHA256 = "149bf7093fae3e3e64288ada58e2196f561879d183f4efb6463f353b87af24ca"
EXPECTED_MTL_SHA256 = "9487387c50510d825b3dfea72c7ccaefcebad4b8f357bfe2af8eb20b2fad5c42"
EXPECTED_MATERIAL_FACE_COUNTS: Counter[str] = Counter(
    {
        "deep_void": 101,
        "voidstone_dark": 78,
        "voidstone_edge": 86,
        "gunmetal": 106,
        "bruised_violet": 69,
        "beacon_violet": 4,
    }
)


@dataclass(frozen=True)
class Face:
    indices: tuple[int, int, int]
    material: str
    group: str


class Mesh:
    def __init__(self) -> None:
        self.vertices: list[tuple[float, float, float]] = []
        self.faces: list[Face] = []

    def vertex(self, x: float, y: float, z: float) -> int:
        self.vertices.append((x, y, z))
        return len(self.vertices)

    def triangle(self, a: int, b: int, c: int, material: str, group: str) -> None:
        self.faces.append(Face((a, b, c), material, group))

    def quad(
        self,
        a: int,
        b: int,
        c: int,
        d: int,
        material: str,
        group: str,
        *,
        flip_diagonal: bool = False,
    ) -> None:
        if flip_diagonal:
            self.triangle(a, b, d, material, group)
            self.triangle(b, c, d, material, group)
        else:
            self.triangle(a, b, c, material, group)
            self.triangle(a, c, d, material, group)


@dataclass(frozen=True)
class PierSpec:
    name: str
    center_x: float
    center_y: float
    base_x_radius: float
    base_y_radius: float
    top_z: float
    rotation_degrees: float
    lean_x: float
    lean_y: float
    fracture_x: float
    fracture_y: float


PIERS = (
    PierSpec("pier_northwest", -0.500, 0.500, 0.270, 0.235, 2.205, 10.0, -0.025, 0.020, 0.16, -0.08),
    PierSpec("pier_northeast", 0.500, 0.500, 0.255, 0.270, 2.145, 29.0, 0.020, 0.030, -0.12, 0.17),
    PierSpec("pier_southeast", 0.500, -0.500, 0.275, 0.230, 1.545, 47.0, 0.045, -0.025, 0.20, 0.04),
    PierSpec("pier_southwest", -0.500, -0.500, 0.245, 0.270, 2.185, 68.0, -0.035, -0.015, -0.15, -0.12),
)

PLATFORM_SEGMENTS = (0, 1, 2, 3, 5, 6, 8)
CAGE_POST_SPECS = (
    (10.0, 3.025, 0.018),
    (77.0, 3.115, -0.020),
    (147.0, 3.000, 0.022),
    (220.0, 2.805, -0.026),
    (294.0, 3.070, 0.016),
)
CRENEL_SPECS = (
    (9.0, 2.935, (3.235, 3.345, 3.270, 3.165)),
    (78.0, 2.970, (3.310, 3.410, 3.245, 3.190)),
    (149.0, 2.925, (3.185, 3.305, 3.360, 3.220)),
    (221.0, 2.775, (3.035, 3.180, 3.105, 2.970)),
    (295.0, 2.950, (3.270, 3.205, 3.395, 3.305)),
)


def vector_add(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def vector_scale(vector: tuple[float, float, float], scale: float) -> tuple[float, float, float]:
    return (vector[0] * scale, vector[1] * scale, vector[2] * scale)


def vector_subtract(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a: tuple[float, float, float], b: tuple[float, float, float]) -> tuple[float, float, float]:
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def normalize(vector: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(sum(component * component for component in vector))
    if length <= 1e-12:
        raise ValueError("cannot normalize a zero-length vector")
    return tuple(component / length for component in vector)  # type: ignore[return-value]


def add_pier(mesh: Mesh, spec: PierSpec, pier_index: int) -> None:
    """Add one leaning five-sided voidstone pier with a slashed top."""
    sides = 5
    rotation = math.radians(spec.rotation_degrees)
    ring_specs = (
        (0.000, 1.000, 0.00),
        (0.350, 0.900, 0.12),
        (spec.top_z, 0.585, 1.00),
    )
    rings: list[list[int]] = []
    for ring_index, (base_z, radius_scale, lean_scale) in enumerate(ring_specs):
        ring: list[int] = []
        for side in range(sides):
            angle = rotation + math.tau * side / sides
            local_x = math.cos(angle) * spec.base_x_radius * radius_scale
            local_y = math.sin(angle) * spec.base_y_radius * radius_scale
            z = base_z
            if ring_index == len(ring_specs) - 1:
                z += spec.fracture_x * local_x + spec.fracture_y * local_y
            ring.append(
                mesh.vertex(
                    spec.center_x + spec.lean_x * lean_scale + local_x,
                    spec.center_y + spec.lean_y * lean_scale + local_y,
                    z,
                )
            )
        rings.append(ring)

    side_materials = (
        "voidstone_dark",
        "voidstone_edge",
        "gunmetal",
        "voidstone_dark",
        "deep_void",
    )
    for layer in range(len(rings) - 1):
        lower = rings[layer]
        upper = rings[layer + 1]
        for side in range(sides):
            next_side = (side + 1) % sides
            mesh.quad(
                lower[side],
                lower[next_side],
                upper[next_side],
                upper[side],
                side_materials[(side + pier_index) % len(side_materials)],
                spec.name,
                flip_diagonal=(side + layer + pier_index) % 2 == 1,
            )

    bottom = rings[0]
    top = rings[-1]
    bottom_center = mesh.vertex(
        sum(mesh.vertices[index - 1][0] for index in bottom) / sides,
        sum(mesh.vertices[index - 1][1] for index in bottom) / sides,
        0.0,
    )
    top_center = mesh.vertex(
        sum(mesh.vertices[index - 1][0] for index in top) / sides,
        sum(mesh.vertices[index - 1][1] for index in top) / sides,
        sum(mesh.vertices[index - 1][2] for index in top) / sides,
    )
    for side in range(sides):
        next_side = (side + 1) % sides
        mesh.triangle(bottom_center, bottom[next_side], bottom[side], "deep_void", spec.name)
        cap_material = "bruised_violet" if (side + pier_index) % 2 == 0 else "voidstone_edge"
        mesh.triangle(top_center, top[side], top[next_side], cap_material, spec.name)


def add_box_beam(
    mesh: Mesh,
    group: str,
    start: tuple[float, float, float],
    end: tuple[float, float, float],
    half_width: float,
    half_depth: float,
    side_materials: tuple[str, str, str, str],
    cap_material: str,
) -> None:
    """Add a closed eight-vertex beam along an arbitrary 3D segment."""
    direction = normalize(vector_subtract(end, start))
    reference = (0.0, 0.0, 1.0) if abs(direction[2]) < 0.92 else (1.0, 0.0, 0.0)
    side_axis = normalize(cross(direction, reference))
    depth_axis = normalize(cross(side_axis, direction))
    offsets = (
        vector_add(vector_scale(side_axis, -half_width), vector_scale(depth_axis, -half_depth)),
        vector_add(vector_scale(side_axis, half_width), vector_scale(depth_axis, -half_depth)),
        vector_add(vector_scale(side_axis, half_width), vector_scale(depth_axis, half_depth)),
        vector_add(vector_scale(side_axis, -half_width), vector_scale(depth_axis, half_depth)),
    )
    start_ring = [mesh.vertex(*vector_add(start, offset)) for offset in offsets]
    end_ring = [mesh.vertex(*vector_add(end, offset)) for offset in offsets]
    for side in range(4):
        next_side = (side + 1) % 4
        mesh.quad(
            start_ring[side],
            start_ring[next_side],
            end_ring[next_side],
            end_ring[side],
            side_materials[side],
            group,
            flip_diagonal=side % 2 == 1,
        )
    mesh.quad(start_ring[3], start_ring[2], start_ring[1], start_ring[0], cap_material, group)
    mesh.quad(end_ring[0], end_ring[1], end_ring[2], end_ring[3], cap_material, group, flip_diagonal=True)


def add_support_braces(mesh: Mesh) -> None:
    materials = ("deep_void", "gunmetal", "voidstone_edge", "gunmetal")
    add_box_beam(mesh, "brace_north", (-0.485, 0.500, 1.465), (0.495, 0.505, 1.525), 0.046, 0.064, materials, "bruised_violet")
    add_box_beam(mesh, "brace_west", (-0.500, 0.485, 1.720), (-0.515, -0.490, 1.645), 0.044, 0.060, materials, "voidstone_edge")
    add_box_beam(mesh, "brace_east_broken", (0.505, 0.490, 1.310), (0.535, -0.300, 1.175), 0.043, 0.057, materials, "bruised_violet")


def polar(radius: float, angle: float, z: float) -> tuple[float, float, float]:
    return (radius * math.cos(angle), radius * math.sin(angle), z)


def add_platform_segment(mesh: Mesh, segment_index: int) -> None:
    """Add one separated wedge in the open-center lookout platform."""
    group = f"platform_segment_{segment_index + 1:02d}"
    segment_span = math.tau / 9.0
    gap = math.radians(8.0)
    center = segment_index * segment_span + math.radians(4.0)
    start_angle = center - (segment_span - gap) / 2.0
    end_angle = center + (segment_span - gap) / 2.0
    inner_radius = 0.305
    outer_radius = 0.755 if segment_index not in {3, 6} else 0.710
    bottom_z = 2.105 + 0.010 * ((segment_index * 2) % 3)
    top_z = bottom_z + 0.135 + 0.008 * (segment_index % 2)
    corners = (
        polar(inner_radius, start_angle, bottom_z),
        polar(outer_radius, start_angle, bottom_z),
        polar(outer_radius, end_angle, bottom_z),
        polar(inner_radius, end_angle, bottom_z),
    )
    bottom = [mesh.vertex(*corner) for corner in corners]
    top_offsets = (0.000, 0.012 * math.sin(segment_index), -0.010 * math.cos(segment_index), 0.004)
    top = [
        mesh.vertex(x, y, top_z + top_offsets[index])
        for index, (x, y, _z) in enumerate(corners)
    ]
    b0, b1, b2, b3 = bottom
    t0, t1, t2, t3 = top
    top_material = "gunmetal" if segment_index % 3 == 0 else "voidstone_edge"
    mesh.quad(t0, t1, t2, t3, top_material, group, flip_diagonal=segment_index % 2 == 1)
    mesh.quad(b3, b2, b1, b0, "deep_void", group)
    mesh.quad(b0, b1, t1, t0, "gunmetal", group)
    mesh.quad(b1, b2, t2, t1, "voidstone_dark", group, flip_diagonal=True)
    mesh.quad(b2, b3, t3, t2, "gunmetal", group)
    mesh.quad(b3, b0, t0, t3, "bruised_violet", group, flip_diagonal=True)


def cage_post_endpoints(angle_degrees: float, top_z: float, lean: float) -> tuple[tuple[float, float, float], tuple[float, float, float]]:
    angle = math.radians(angle_degrees)
    tangent = (-math.sin(angle), math.cos(angle))
    start = (0.625 * math.cos(angle), 0.625 * math.sin(angle), 2.205)
    end = (
        0.625 * math.cos(angle) + tangent[0] * lean,
        0.625 * math.sin(angle) + tangent[1] * lean,
        top_z,
    )
    return start, end


def add_cage(mesh: Mesh) -> list[tuple[float, float, float]]:
    tops: list[tuple[float, float, float]] = []
    for index, (angle, top_z, lean) in enumerate(CAGE_POST_SPECS):
        start, end = cage_post_endpoints(angle, top_z, lean)
        tops.append(end)
        materials = (
            "gunmetal",
            "voidstone_dark",
            "voidstone_edge",
            "deep_void",
        )
        add_box_beam(
            mesh,
            f"cage_post_{index + 1:02d}",
            start,
            end,
            0.043,
            0.050,
            materials,
            "bruised_violet" if index in {1, 3} else "voidstone_edge",
        )

    rail_pairs = ((0, 1), (1, 2), (3, 4))
    for rail_index, (start_index, end_index) in enumerate(rail_pairs):
        start = vector_add(tops[start_index], (0.0, 0.0, -0.075))
        end = vector_add(tops[end_index], (0.0, 0.0, -0.075))
        add_box_beam(
            mesh,
            f"cage_rail_{rail_index + 1:02d}",
            start,
            end,
            0.035,
            0.040,
            ("deep_void", "gunmetal", "voidstone_edge", "gunmetal"),
            "bruised_violet",
        )
    return tops


def add_crenel(
    mesh: Mesh,
    index: int,
    angle_degrees: float,
    bottom_z: float,
    top_heights: tuple[float, float, float, float],
) -> None:
    group = f"jagged_crenel_{index + 1:02d}"
    angle = math.radians(angle_degrees)
    radial = (math.cos(angle), math.sin(angle))
    tangent = (-math.sin(angle), math.cos(angle))
    center_radius = 0.660
    radial_half = 0.092
    tangent_half = 0.115 if index != 3 else 0.090
    corners_2d = (
        (-radial_half, -tangent_half),
        (radial_half, -tangent_half),
        (radial_half, tangent_half),
        (-radial_half, tangent_half),
    )
    bottom: list[int] = []
    top: list[int] = []
    for corner_index, (radial_offset, tangent_offset) in enumerate(corners_2d):
        x = radial[0] * (center_radius + radial_offset) + tangent[0] * tangent_offset
        y = radial[1] * (center_radius + radial_offset) + tangent[1] * tangent_offset
        bottom.append(mesh.vertex(x, y, bottom_z))
        top.append(mesh.vertex(x, y, top_heights[corner_index]))
    b0, b1, b2, b3 = bottom
    t0, t1, t2, t3 = top
    mesh.quad(b3, b2, b1, b0, "deep_void", group)
    mesh.quad(t0, t1, t2, t3, "bruised_violet" if index % 2 == 0 else "voidstone_edge", group, flip_diagonal=True)
    mesh.quad(b0, b1, t1, t0, "voidstone_dark", group)
    mesh.quad(b1, b2, t2, t1, "gunmetal", group, flip_diagonal=True)
    mesh.quad(b2, b3, t3, t2, "voidstone_edge", group)
    mesh.quad(b3, b0, t0, t3, "deep_void", group, flip_diagonal=True)


def add_pier_sigil(
    mesh: Mesh,
    index: int,
    center: tuple[float, float, float],
    outward_angle_degrees: float,
) -> None:
    """Add a shallow diamond sigil plate to an inward-facing pier face."""
    group = f"pier_sigil_{index + 1:02d}"
    angle = math.radians(outward_angle_degrees)
    normal = (math.cos(angle), math.sin(angle), 0.0)
    tangent = (-math.sin(angle), math.cos(angle), 0.0)
    half_depth = 0.018
    width = 0.090
    height = 0.155
    plane_points = (
        vector_add(center, (0.0, 0.0, height)),
        vector_add(center, vector_scale(tangent, width)),
        vector_add(center, (0.0, 0.0, -height)),
        vector_add(center, vector_scale(tangent, -width)),
    )
    front = [mesh.vertex(*vector_add(point, vector_scale(normal, half_depth))) for point in plane_points]
    back = [mesh.vertex(*vector_add(point, vector_scale(normal, -half_depth))) for point in plane_points]
    f0, f1, f2, f3 = front
    b0, b1, b2, b3 = back
    front_material = "beacon_violet" if index == 0 else "bruised_violet"
    mesh.quad(f0, f1, f2, f3, front_material, group)
    mesh.quad(b3, b2, b1, b0, "deep_void", group, flip_diagonal=True)
    mesh.quad(f0, b0, b1, f1, "gunmetal", group)
    mesh.quad(f1, b1, b2, f2, "voidstone_dark", group, flip_diagonal=True)
    mesh.quad(f2, b2, b3, f3, "gunmetal", group)
    mesh.quad(f3, b3, b0, f0, "voidstone_dark", group, flip_diagonal=True)


def add_beacon(mesh: Mesh) -> None:
    """Add a restrained six-sided suspended crystal above the open cage."""
    group = "suspended_beacon_crystal"
    center_x = 0.035
    center_y = -0.020
    lower_z = 2.660
    ring_z = 3.060
    upper_z = 3.455
    radius_x = 0.125
    radius_y = 0.105
    lower = mesh.vertex(center_x, center_y, lower_z)
    upper = mesh.vertex(center_x, center_y, upper_z)
    ring = [
        mesh.vertex(
            center_x + radius_x * math.cos(math.tau * index / 6.0 + math.radians(10.0)),
            center_y + radius_y * math.sin(math.tau * index / 6.0 + math.radians(10.0)),
            ring_z + (0.018 if index % 2 == 0 else -0.018),
        )
        for index in range(6)
    ]
    for index in range(6):
        next_index = (index + 1) % 6
        lower_material = "bruised_violet" if index % 2 == 0 else "deep_void"
        upper_material = "beacon_violet" if index in {1, 4} else "bruised_violet"
        mesh.triangle(lower, ring[next_index], ring[index], lower_material, group)
        mesh.triangle(upper, ring[index], ring[next_index], upper_material, group)


def build_mesh() -> Mesh:
    mesh = Mesh()
    for index, spec in enumerate(PIERS):
        add_pier(mesh, spec, index)
    add_support_braces(mesh)
    for segment_index in PLATFORM_SEGMENTS:
        add_platform_segment(mesh, segment_index)
    add_cage(mesh)
    for index, (angle, bottom_z, top_heights) in enumerate(CRENEL_SPECS):
        add_crenel(mesh, index, angle, bottom_z, top_heights)
    add_pier_sigil(mesh, 0, (-0.365, 0.365, 0.790), -45.0)
    add_pier_sigil(mesh, 1, (0.360, 0.360, 1.145), -135.0)
    add_pier_sigil(mesh, 2, (-0.365, -0.365, 1.020), 45.0)
    add_beacon(mesh)
    validate_mesh(mesh)
    return mesh


def triangle_area_squared(mesh: Mesh, face: Face) -> float:
    a, b, c = (mesh.vertices[index - 1] for index in face.indices)
    ab = vector_subtract(b, a)
    ac = vector_subtract(c, a)
    area_cross = cross(ab, ac)
    return sum(component * component for component in area_cross) / 4.0


def mesh_bounds(mesh: Mesh) -> tuple[tuple[float, float], tuple[float, float], tuple[float, float]]:
    return tuple(
        (min(vertex[axis] for vertex in mesh.vertices), max(vertex[axis] for vertex in mesh.vertices))
        for axis in range(3)
    )  # type: ignore[return-value]


def expected_group_face_counts() -> dict[str, int]:
    expected = {spec.name: 30 for spec in PIERS}
    expected.update({name: 12 for name in ("brace_north", "brace_west", "brace_east_broken")})
    expected.update({f"platform_segment_{index + 1:02d}": 12 for index in PLATFORM_SEGMENTS})
    expected.update({f"cage_post_{index + 1:02d}": 12 for index in range(len(CAGE_POST_SPECS))})
    expected.update({f"cage_rail_{index + 1:02d}": 12 for index in range(3)})
    expected.update({f"jagged_crenel_{index + 1:02d}": 12 for index in range(len(CRENEL_SPECS))})
    expected.update({f"pier_sigil_{index + 1:02d}": 12 for index in range(3)})
    expected["suspended_beacon_crystal"] = 12
    return expected


def validate_mesh(mesh: Mesh) -> None:
    expected_groups = expected_group_face_counts()
    expected_faces = sum(expected_groups.values())
    if len(mesh.faces) != expected_faces or not 400 <= len(mesh.faces) <= 460:
        raise ValueError(f"expected {expected_faces} modest low-poly faces, got {len(mesh.faces)}")
    if len(mesh.faces) > 500:
        raise ValueError(f"hard face cap exceeded: {len(mesh.faces)}")
    if any(face.material not in MATERIALS for face in mesh.faces):
        raise ValueError("mesh references an undeclared material")
    if any(len(face.indices) != 3 for face in mesh.faces):
        raise ValueError("mesh contains a non-triangular face")
    if any(index < 1 or index > len(mesh.vertices) for face in mesh.faces for index in face.indices):
        raise ValueError("mesh has an invalid OBJ vertex index")

    degenerate = [face for face in mesh.faces if triangle_area_squared(mesh, face) <= 1e-14]
    if degenerate:
        raise ValueError(f"mesh has {len(degenerate)} degenerate triangles")

    actual_group_counts = Counter(face.group for face in mesh.faces)
    if actual_group_counts != Counter(expected_groups):
        missing = sorted(set(expected_groups) - set(actual_group_counts))
        extra = sorted(set(actual_group_counts) - set(expected_groups))
        raise ValueError(f"component groups differ: missing={missing}, extra={extra}, counts={actual_group_counts}")

    material_counts = Counter(face.material for face in mesh.faces)
    if set(material_counts) != set(MATERIALS):
        raise ValueError(f"all named flat materials must be used exactly once or more: {material_counts}")
    if EXPECTED_MATERIAL_FACE_COUNTS and material_counts != EXPECTED_MATERIAL_FACE_COUNTS:
        raise ValueError(
            f"material face counts changed: expected={EXPECTED_MATERIAL_FACE_COUNTS}, actual={material_counts}"
        )

    x_bounds, y_bounds, z_bounds = mesh_bounds(mesh)
    dimensions = tuple(high - low for low, high in (x_bounds, y_bounds, z_bounds))
    if not 1.50 <= dimensions[0] <= 1.64 or not 1.50 <= dimensions[1] <= 1.64:
        raise ValueError(f"expected about a 1.6 x 1.6 tile footprint, got {dimensions[:2]}")
    if max(abs(x_bounds[0]), abs(x_bounds[1]), abs(y_bounds[0]), abs(y_bounds[1])) > 0.820001:
        raise ValueError(f"XY footprint exceeds +/-0.82 tiles: x={x_bounds}, y={y_bounds}")
    if abs((x_bounds[0] + x_bounds[1]) / 2.0) > 0.045 or abs((y_bounds[0] + y_bounds[1]) / 2.0) > 0.045:
        raise ValueError(f"mesh is not centered: x={x_bounds}, y={y_bounds}")
    if abs(z_bounds[0]) > 1e-12:
        raise ValueError(f"mesh is not grounded at z=0: {z_bounds}")
    if not 3.20 <= dimensions[2] <= 3.60:
        raise ValueError(f"expected a 3.2-3.6 tile watchtower, got {dimensions[2]:.6f}")

    platform_faces = [face for face in mesh.faces if face.group.startswith("platform_segment_")]
    platform_indices = {index for face in platform_faces for index in face.indices}
    platform_min_radius = min(
        math.hypot(mesh.vertices[index - 1][0], mesh.vertices[index - 1][1])
        for index in platform_indices
    )
    if platform_min_radius < 0.299:
        raise ValueError(f"platform closes the player-readable center opening: radius={platform_min_radius:.6f}")
    if len({face.group for face in platform_faces}) != 7:
        raise ValueError("lookout platform must remain a seven-piece broken ring")

    beacon_faces = [face for face in mesh.faces if face.group == "suspended_beacon_crystal"]
    beacon_materials = Counter(face.material for face in beacon_faces)
    if beacon_materials["beacon_violet"] != 2 or sum(beacon_materials.values()) != 12:
        raise ValueError(f"beacon violet must remain restrained to two crystal facets: {beacon_materials}")


def format_float(value: float) -> str:
    if abs(value) < 0.0000005:
        value = 0.0
    return f"{value:.6f}"


def render_obj(mesh: Mesh) -> str:
    lines = [
        "# Original Voidscape Void watchtower source mesh.",
        "# Generated deterministically by scripts/generate-void-watchtower-source.py.",
        "# Blender-style coordinates: Z up, one source unit equals one game tile.",
        "mtllib void_watchtower.mtl",
        "o void_watchtower",
        "s off",
    ]
    lines.extend(
        f"v {format_float(x)} {format_float(y)} {format_float(z)}"
        for x, y, z in mesh.vertices
    )
    current_group: str | None = None
    current_material: str | None = None
    for face in mesh.faces:
        if face.group != current_group:
            lines.append(f"g {face.group}")
            current_group = face.group
            current_material = None
        if face.material != current_material:
            lines.append(f"usemtl {face.material}")
            current_material = face.material
        lines.append("f " + " ".join(str(index) for index in face.indices))
    return "\n".join(lines) + "\n"


def render_mtl() -> str:
    lines = [
        "# Flat colors for the original Voidscape Void watchtower source mesh.",
        "# Textures, UVs, transparency, and external assets are intentionally absent.",
    ]
    for name, rgb in MATERIALS.items():
        lines.extend(
            (
                "",
                f"newmtl {name}",
                "Ka 0.0000 0.0000 0.0000",
                "Kd " + " ".join(f"{component / 255.0:.4f}" for component in rgb),
                "Ks 0.0000 0.0000 0.0000",
                "illum 1",
            )
        )
    return "\n".join(lines) + "\n"


def sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def print_report(mesh: Mesh, obj_text: str, mtl_text: str, mode: str) -> None:
    bounds = mesh_bounds(mesh)
    dimensions = tuple(high - low for low, high in bounds)
    group_counts = Counter(face.group for face in mesh.faces)
    material_counts = Counter(face.material for face in mesh.faces)
    print(f"Void watchtower source {mode}")
    print(f"vertices: {len(mesh.vertices)}")
    print(f"faces: {len(mesh.faces)} triangles")
    print(f"components: {len(group_counts)} named groups")
    print(
        "bounds: "
        + ", ".join(
            f"{axis}[{low:.6f}, {high:.6f}]"
            for axis, (low, high) in zip("xyz", bounds)
        )
    )
    print("dimensions: " + " x ".join(f"{value:.6f}" for value in dimensions) + " tiles")
    print("materials:")
    for name, rgb in MATERIALS.items():
        print(f"  {name}: #{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}, {material_counts[name]} faces")
    print(f"obj sha256: {sha256(obj_text)}")
    print(f"mtl sha256: {sha256(mtl_text)}")


def check_committed(obj_text: str, mtl_text: str) -> None:
    missing = [path for path in (OBJ_PATH, MTL_PATH) if not path.is_file()]
    if missing:
        raise ValueError("missing generated output: " + ", ".join(str(path) for path in missing))

    committed_obj = OBJ_PATH.read_bytes()
    committed_mtl = MTL_PATH.read_bytes()
    if committed_obj != obj_text.encode("utf-8") or committed_mtl != mtl_text.encode("utf-8"):
        raise ValueError(
            "generated output is stale; rerun scripts/generate-void-watchtower-source.py"
        )

    obj_hash = hashlib.sha256(committed_obj).hexdigest()
    mtl_hash = hashlib.sha256(committed_mtl).hexdigest()
    if not EXPECTED_OBJ_SHA256 or not EXPECTED_MTL_SHA256:
        raise ValueError("reviewed generated hashes have not been recorded in the generator")
    if obj_hash != EXPECTED_OBJ_SHA256 or mtl_hash != EXPECTED_MTL_SHA256:
        raise ValueError(
            f"generated source hash changed: obj={obj_hash}, mtl={mtl_hash}"
        )
    if sha256(obj_text) != EXPECTED_OBJ_SHA256 or sha256(mtl_text) != EXPECTED_MTL_SHA256:
        raise ValueError("rendered source hashes do not match reviewed hash constants")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate geometry, hashes, and committed OBJ/MTL byte-for-byte",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        mesh = build_mesh()
        obj_text = render_obj(mesh)
        mtl_text = render_mtl()

        # Build twice so accidental non-determinism fails before disk checks.
        second_mesh = build_mesh()
        if render_obj(second_mesh) != obj_text or render_mtl() != mtl_text:
            raise ValueError("generator produced different output in one process")

        if args.check:
            check_committed(obj_text, mtl_text)
            mode = "check passed"
        else:
            SOURCE_DIR.mkdir(parents=True, exist_ok=True)
            OBJ_PATH.write_text(obj_text, encoding="utf-8")
            MTL_PATH.write_text(mtl_text, encoding="utf-8")
            mode = "generated"
        print_report(mesh, obj_text, mtl_text, mode)
        return 0
    except (OSError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
