#!/usr/bin/env python3
"""Generate the original low-poly source mesh for the Void market shelter.

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
SOURCE_DIR = REPO_ROOT / "content/custom/void_market_shelter/art/source"
OBJ_PATH = SOURCE_DIR / "void_market_shelter.obj"
MTL_PATH = SOURCE_DIR / "void_market_shelter.mtl"

# Insertion order is also the stable order in the generated MTL.
MATERIALS: dict[str, tuple[int, int, int]] = {
    "voidwood_dark": (52, 45, 57),
    "gunmetal": (88, 86, 98),
    "bruised_violet_cloth": (105, 59, 119),
    "charcoal_cloth_patch": (71, 64, 78),
    "supply_wood": (88, 68, 65),
    "weathered_rope": (127, 103, 85),
    "charm_violet": (190, 98, 220),
}

# These constants make --check protect the approved source, not merely any
# internally valid market-shelter mesh.
EXPECTED_MATERIAL_COUNTS: Counter[str] = Counter(
    {
        "voidwood_dark": 96,
        "gunmetal": 108,
        "bruised_violet_cloth": 25,
        "charcoal_cloth_patch": 25,
        "supply_wood": 36,
        "weathered_rope": 24,
        "charm_violet": 16,
    }
)
EXPECTED_GROUP_COUNTS: Counter[str] = Counter(
    {
        "post_01_front_left": 26,
        "post_02_front_right": 26,
        "post_03_rear_left": 26,
        "post_04_rear_right": 26,
        "frame_front": 12,
        "frame_rear": 12,
        "frame_left": 12,
        "frame_right": 12,
        "canopy_left_panel": 12,
        "canopy_right_panel": 12,
        "canopy_repair_patch": 5,
        "canopy_torn_tab": 1,
        "quartermaster_counter": 12,
        "counter_ledge": 12,
        "counter_legs": 20,
        "supply_shelf_supports": 20,
        "supply_shelf_planks": 24,
        "supply_bundle_01": 12,
        "supply_bundle_02": 8,
        "hanging_rope_01": 12,
        "void_charm_01": 8,
        "hanging_rope_02": 12,
        "void_charm_02": 8,
    }
)
EXPECTED_OBJ_SHA256 = "b921c7a13329e1092229e338b39f1c73c33163ad8dae5af5bd2ea78b09b0e026"
EXPECTED_MTL_SHA256 = "63fe6594c8b51f9f370233e371c74fbe9fe967db9ab3f5ba19aaccd17b639146"


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
        return len(self.vertices)  # OBJ indices are one-based.

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
class PostSpec:
    group: str
    base: tuple[float, float]
    top: tuple[float, float]
    height: float
    half_width: float
    half_depth: float
    layer_materials: tuple[str, str, str]


POSTS = (
    PostSpec(
        "post_01_front_left",
        (-1.350, -0.720),
        (-1.385, -0.710),
        2.110,
        0.125,
        0.105,
        ("voidwood_dark", "gunmetal", "voidwood_dark"),
    ),
    PostSpec(
        "post_02_front_right",
        (1.340, -0.715),
        (1.370, -0.695),
        2.145,
        0.105,
        0.120,
        ("gunmetal", "voidwood_dark", "gunmetal"),
    ),
    PostSpec(
        "post_03_rear_left",
        (-1.335, 0.715),
        (-1.360, 0.745),
        2.430,
        0.135,
        0.110,
        ("voidwood_dark", "voidwood_dark", "gunmetal"),
    ),
    PostSpec(
        "post_04_rear_right",
        (1.330, 0.725),
        (1.300, 0.755),
        2.410,
        0.115,
        0.100,
        ("gunmetal", "gunmetal", "voidwood_dark"),
    ),
)


def vector_subtract(
    a: tuple[float, float, float], b: tuple[float, float, float]
) -> tuple[float, float, float]:
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def vector_add(
    a: tuple[float, float, float], b: tuple[float, float, float]
) -> tuple[float, float, float]:
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def vector_scale(
    vector: tuple[float, float, float], scale: float
) -> tuple[float, float, float]:
    return (vector[0] * scale, vector[1] * scale, vector[2] * scale)


def dot(a: tuple[float, float, float], b: tuple[float, float, float]) -> float:
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(
    a: tuple[float, float, float], b: tuple[float, float, float]
) -> tuple[float, float, float]:
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def normalized(vector: tuple[float, float, float]) -> tuple[float, float, float]:
    length = math.sqrt(dot(vector, vector))
    if length <= 1e-12:
        raise ValueError("cannot normalize a zero-length vector")
    return vector_scale(vector, 1.0 / length)


def add_box(
    mesh: Mesh,
    *,
    bounds: tuple[float, float, float, float, float, float],
    material: str,
    group: str,
    include_bottom: bool = True,
) -> None:
    """Add a rectangular furnishing, optionally open where it meets terrain."""
    x1, x2, y1, y2, z1, z2 = bounds
    bottom = (
        mesh.vertex(x1, y1, z1),
        mesh.vertex(x2, y1, z1),
        mesh.vertex(x2, y2, z1),
        mesh.vertex(x1, y2, z1),
    )
    top = (
        mesh.vertex(x1, y1, z2),
        mesh.vertex(x2, y1, z2),
        mesh.vertex(x2, y2, z2),
        mesh.vertex(x1, y2, z2),
    )
    b0, b1, b2, b3 = bottom
    t0, t1, t2, t3 = top
    mesh.quad(t0, t1, t2, t3, material, group)
    if include_bottom:
        mesh.quad(b3, b2, b1, b0, material, group, flip_diagonal=True)
    mesh.quad(b0, b1, t1, t0, material, group)
    mesh.quad(b1, b2, t2, t1, material, group, flip_diagonal=True)
    mesh.quad(b2, b3, t3, t2, material, group)
    mesh.quad(b3, b0, t0, t3, material, group, flip_diagonal=True)


def add_beam_between(
    mesh: Mesh,
    *,
    start: tuple[float, float, float],
    end: tuple[float, float, float],
    radius: float,
    material: str,
    group: str,
) -> None:
    """Add a capped square beam between arbitrary points."""
    axis = normalized(vector_subtract(end, start))
    helper = (0.0, 0.0, 1.0) if abs(axis[2]) < 0.90 else (1.0, 0.0, 0.0)
    side_u = normalized(cross(axis, helper))
    side_v = normalized(cross(axis, side_u))
    ring_offsets: list[tuple[float, float, float]] = []
    for side in range(4):
        angle = math.pi / 4.0 + math.tau * side / 4.0
        offset = vector_add(
            vector_scale(side_u, radius * math.cos(angle)),
            vector_scale(side_v, radius * math.sin(angle)),
        )
        ring_offsets.append(offset)
    start_ring = [mesh.vertex(*vector_add(start, offset)) for offset in ring_offsets]
    end_ring = [mesh.vertex(*vector_add(end, offset)) for offset in ring_offsets]
    for side in range(4):
        next_side = (side + 1) % 4
        mesh.quad(
            start_ring[side],
            start_ring[next_side],
            end_ring[next_side],
            end_ring[side],
            material,
            group,
            flip_diagonal=side % 2 == 1,
        )
    mesh.quad(
        start_ring[3],
        start_ring[2],
        start_ring[1],
        start_ring[0],
        material,
        group,
    )
    mesh.quad(
        end_ring[0],
        end_ring[1],
        end_ring[2],
        end_ring[3],
        material,
        group,
        flip_diagonal=True,
    )


def add_mismatched_post(mesh: Mesh, spec: PostSpec) -> None:
    ring_heights = (0.0, spec.height * 0.34, spec.height * 0.70, spec.height)
    rings: list[list[int]] = []
    for ring_index, height in enumerate(ring_heights):
        progress = height / spec.height
        center_x = spec.base[0] + (spec.top[0] - spec.base[0]) * progress
        center_y = spec.base[1] + (spec.top[1] - spec.base[1]) * progress
        taper = 1.0 - 0.12 * progress
        skew = 0.010 * math.sin((ring_index + 1) * (len(rings) + 0.7))
        half_width = spec.half_width * taper
        half_depth = spec.half_depth * taper
        rings.append(
            [
                mesh.vertex(center_x - half_width + skew, center_y - half_depth, height),
                mesh.vertex(center_x + half_width + skew, center_y - half_depth, height),
                mesh.vertex(center_x + half_width - skew, center_y + half_depth, height),
                mesh.vertex(center_x - half_width - skew, center_y + half_depth, height),
            ]
        )

    for layer, material in enumerate(spec.layer_materials):
        lower = rings[layer]
        upper = rings[layer + 1]
        for side in range(4):
            next_side = (side + 1) % 4
            mesh.quad(
                lower[side],
                lower[next_side],
                upper[next_side],
                upper[side],
                material,
                spec.group,
                flip_diagonal=(layer + side) % 2 == 1,
            )
    mesh.quad(*rings[-1], spec.layer_materials[-1], spec.group)


def add_frame(mesh: Mesh) -> None:
    beams = (
        (
            "frame_front",
            (-1.395, -0.715, 2.075),
            (1.385, -0.700, 2.105),
            "voidwood_dark",
        ),
        (
            "frame_rear",
            (-1.370, 0.750, 2.390),
            (1.315, 0.760, 2.375),
            "gunmetal",
        ),
        (
            "frame_left",
            (-1.390, -0.715, 2.090),
            (-1.365, 0.750, 2.395),
            "gunmetal",
        ),
        (
            "frame_right",
            (1.375, -0.700, 2.110),
            (1.310, 0.760, 2.385),
            "voidwood_dark",
        ),
    )
    for group, start, end, material in beams:
        add_beam_between(
            mesh,
            start=start,
            end=end,
            radius=0.050,
            material=material,
            group=group,
        )


def add_canopy_grid(
    mesh: Mesh,
    *,
    group: str,
    rows: tuple[tuple[tuple[float, float, float], ...], ...],
    patched_cells: frozenset[tuple[int, int]],
) -> None:
    vertex_rows = [[mesh.vertex(*vertex) for vertex in row] for row in rows]
    for row in range(len(vertex_rows) - 1):
        for column in range(len(vertex_rows[row]) - 1):
            material = (
                "charcoal_cloth_patch"
                if (row, column) in patched_cells
                else "bruised_violet_cloth"
            )
            mesh.quad(
                vertex_rows[row][column],
                vertex_rows[row][column + 1],
                vertex_rows[row + 1][column + 1],
                vertex_rows[row + 1][column],
                material,
                group,
                flip_diagonal=(row + column) % 2 == 1,
            )


def add_patched_canopy(mesh: Mesh) -> None:
    left_rows = (
        (
            (-1.620, -0.950, 2.085),
            (-1.065, -1.020, 2.035),
            (-0.555, -0.900, 2.095),
            (-0.080, -0.985, 2.065),
        ),
        (
            (-1.615, -0.035, 2.255),
            (-1.060, 0.025, 2.285),
            (-0.550, -0.020, 2.275),
            (-0.075, 0.010, 2.300),
        ),
        (
            (-1.600, 0.965, 2.440),
            (-1.055, 1.000, 2.460),
            (-0.540, 0.930, 2.425),
            (-0.060, 0.985, 2.445),
        ),
    )
    right_rows = (
        (
            (-0.035, -0.970, 2.070),
            (0.535, -0.920, 2.095),
            (1.075, -1.010, 2.045),
            (1.620, -0.890, 2.090),
        ),
        (
            (-0.030, 0.015, 2.305),
            (0.540, -0.020, 2.270),
            (1.080, 0.035, 2.295),
            (1.610, -0.010, 2.260),
        ),
        (
            (-0.020, 0.980, 2.445),
            (0.545, 0.940, 2.420),
            (1.085, 1.000, 2.455),
            (1.620, 0.950, 2.435),
        ),
    )
    add_canopy_grid(
        mesh,
        group="canopy_left_panel",
        rows=left_rows,
        patched_cells=frozenset({(0, 1), (1, 0)}),
    )
    add_canopy_grid(
        mesh,
        group="canopy_right_panel",
        rows=right_rows,
        patched_cells=frozenset({(0, 2), (1, 1)}),
    )

    # A deliberately visible five-sided repair patch sits a few millimeters
    # above the cloth plane so flat colors survive the source import.
    repair_points = (
        (-0.930, 0.055, 2.304),
        (-0.410, 0.100, 2.324),
        (-0.330, 0.520, 2.385),
        (-0.780, 0.655, 2.407),
        (-1.035, 0.370, 2.350),
    )
    repair = [mesh.vertex(*point) for point in repair_points]
    repair_center = mesh.vertex(
        sum(point[0] for point in repair_points) / len(repair_points),
        sum(point[1] for point in repair_points) / len(repair_points),
        sum(point[2] for point in repair_points) / len(repair_points) + 0.006,
    )
    for index in range(len(repair)):
        mesh.triangle(
            repair_center,
            repair[index],
            repair[(index + 1) % len(repair)],
            "charcoal_cloth_patch",
            "canopy_repair_patch",
        )

    # A single low-hanging torn tab breaks the otherwise roof-like front edge.
    torn = (
        mesh.vertex(0.955, -1.005, 2.055),
        mesh.vertex(1.290, -0.955, 2.075),
        mesh.vertex(1.175, -0.965, 1.825),
    )
    mesh.triangle(*torn, "bruised_violet_cloth", "canopy_torn_tab")


def add_counter_and_supplies(mesh: Mesh) -> None:
    add_box(
        mesh,
        bounds=(-0.880, 0.880, 0.540, 0.780, 0.580, 0.870),
        material="supply_wood",
        group="quartermaster_counter",
    )
    add_box(
        mesh,
        bounds=(-0.950, 0.950, 0.475, 0.585, 0.820, 0.920),
        material="gunmetal",
        group="counter_ledge",
    )
    for x1, x2 in ((-0.765, -0.650), (0.650, 0.765)):
        add_box(
            mesh,
            bounds=(x1, x2, 0.585, 0.735, 0.0, 0.580),
            material="voidwood_dark",
            group="counter_legs",
            include_bottom=False,
        )

    for x1, x2 in ((0.835, 0.940), (1.165, 1.270)):
        add_box(
            mesh,
            bounds=(x1, x2, 0.395, 0.675, 0.0, 0.625),
            material="gunmetal",
            group="supply_shelf_supports",
            include_bottom=False,
        )
    for z1, z2 in ((0.300, 0.365), (0.600, 0.665)):
        add_box(
            mesh,
            bounds=(0.790, 1.300, 0.380, 0.690, z1, z2),
            material="supply_wood",
            group="supply_shelf_planks",
        )
    add_box(
        mesh,
        bounds=(0.845, 1.035, 0.425, 0.625, 0.665, 0.900),
        material="charcoal_cloth_patch",
        group="supply_bundle_01",
    )

    # A small faceted sack keeps the shelf inhabited without becoming visual
    # clutter or blocking player sightlines.
    center_x, center_y = (1.165, 0.525)
    lower_z, middle_z, upper_z = (0.665, 0.805, 0.925)
    lower = mesh.vertex(center_x, center_y, lower_z)
    ring = (
        mesh.vertex(center_x - 0.105, center_y, middle_z),
        mesh.vertex(center_x, center_y - 0.090, middle_z),
        mesh.vertex(center_x + 0.105, center_y, middle_z),
        mesh.vertex(center_x, center_y + 0.090, middle_z),
    )
    upper = mesh.vertex(center_x - 0.015, center_y + 0.005, upper_z)
    for side in range(4):
        next_side = (side + 1) % 4
        mesh.triangle(
            lower,
            ring[next_side],
            ring[side],
            "bruised_violet_cloth",
            "supply_bundle_02",
        )
        mesh.triangle(
            upper,
            ring[side],
            ring[next_side],
            "bruised_violet_cloth",
            "supply_bundle_02",
        )


def add_octahedral_charm(
    mesh: Mesh,
    *,
    center: tuple[float, float, float],
    radius: float,
    height: float,
    group: str,
) -> None:
    cx, cy, cz = center
    ring = (
        mesh.vertex(cx - radius, cy, cz),
        mesh.vertex(cx, cy - radius * 0.65, cz),
        mesh.vertex(cx + radius, cy, cz),
        mesh.vertex(cx, cy + radius * 0.65, cz),
    )
    top = mesh.vertex(cx, cy, cz + height)
    bottom = mesh.vertex(cx, cy, cz - height)
    for side in range(4):
        next_side = (side + 1) % 4
        mesh.triangle(top, ring[side], ring[next_side], "charm_violet", group)
        mesh.triangle(bottom, ring[next_side], ring[side], "charm_violet", group)


def add_hanging_charms(mesh: Mesh) -> None:
    hangings = (
        (
            "01",
            (-0.955, -0.715, 2.085),
            (-0.930, -0.700, 1.565),
            0.075,
            0.130,
        ),
        (
            "02",
            (0.985, 0.715, 2.345),
            (1.005, 0.700, 1.680),
            0.065,
            0.115,
        ),
    )
    for suffix, start, end, radius, charm_height in hangings:
        add_beam_between(
            mesh,
            start=start,
            end=end,
            radius=0.012,
            material="weathered_rope",
            group=f"hanging_rope_{suffix}",
        )
        add_octahedral_charm(
            mesh,
            center=(end[0], end[1], end[2] - charm_height),
            radius=radius,
            height=charm_height,
            group=f"void_charm_{suffix}",
        )


def build_mesh() -> Mesh:
    mesh = Mesh()
    for post in POSTS:
        add_mismatched_post(mesh, post)
    add_frame(mesh)
    add_patched_canopy(mesh)
    add_counter_and_supplies(mesh)
    add_hanging_charms(mesh)
    validate_mesh(mesh)
    return mesh


def triangle_area_squared(mesh: Mesh, face: Face) -> float:
    a, b, c = (mesh.vertices[index - 1] for index in face.indices)
    ab = vector_subtract(b, a)
    ac = vector_subtract(c, a)
    normal = cross(ab, ac)
    return dot(normal, normal) / 4.0


def projected_xy_area(mesh: Mesh, face: Face) -> float:
    a, b, c = (mesh.vertices[index - 1] for index in face.indices)
    return abs(
        a[0] * (b[1] - c[1])
        + b[0] * (c[1] - a[1])
        + c[0] * (a[1] - b[1])
    ) / 2.0


def mesh_bounds(
    mesh: Mesh,
) -> tuple[tuple[float, float], tuple[float, float], tuple[float, float]]:
    return tuple(
        (
            min(vertex[axis] for vertex in mesh.vertices),
            max(vertex[axis] for vertex in mesh.vertices),
        )
        for axis in range(3)
    )  # type: ignore[return-value]


def face_bounds(
    mesh: Mesh, face: Face
) -> tuple[tuple[float, float], tuple[float, float], tuple[float, float]]:
    vertices = [mesh.vertices[index - 1] for index in face.indices]
    return tuple(
        (min(vertex[axis] for vertex in vertices), max(vertex[axis] for vertex in vertices))
        for axis in range(3)
    )  # type: ignore[return-value]


def intervals_overlap(a: tuple[float, float], b: tuple[float, float]) -> bool:
    return a[0] < b[1] and b[0] < a[1]


def validate_openness(mesh: Mesh) -> None:
    """Protect the open stall silhouette and player sight corridor."""
    passage = ((-0.550, 0.550), (-0.420, 0.420), (0.015, 1.780))
    intruding = []
    for face in mesh.faces:
        bounds = face_bounds(mesh, face)
        if all(intervals_overlap(bounds[axis], passage[axis]) for axis in range(3)):
            intruding.append(face)
    if intruding:
        groups = sorted({face.group for face in intruding})
        raise ValueError("central player/sight corridor is obstructed by " + ", ".join(groups))

    floor_like = [
        face
        for face in mesh.faces
        if max(mesh.vertices[index - 1][2] for index in face.indices) <= 0.010
        and projected_xy_area(mesh, face) > 0.025
    ]
    if floor_like:
        raise ValueError(f"model contains {len(floor_like)} broad floor-like ground faces")

    canopy_faces = [face for face in mesh.faces if face.group.startswith("canopy_")]
    if not canopy_faces or min(
        min(mesh.vertices[index - 1][2] for index in face.indices)
        for face in canopy_faces
    ) < 1.800:
        raise ValueError("canopy must remain wholly above the open player volume")

    # Below eye level, no single triangle may approximate a large wall panel.
    wall_like = []
    for face in mesh.faces:
        bounds = face_bounds(mesh, face)
        width = bounds[0][1] - bounds[0][0]
        depth = bounds[1][1] - bounds[1][0]
        height = bounds[2][1] - bounds[2][0]
        if bounds[2][0] < 1.780 and height > 0.600 and max(width, depth) > 0.800:
            wall_like.append(face)
    if wall_like:
        groups = sorted({face.group for face in wall_like})
        raise ValueError("model contains opaque wall-like faces in " + ", ".join(groups))


def validate_mesh(mesh: Mesh) -> None:
    if not 250 <= len(mesh.faces) <= 450:
        raise ValueError(f"expected 250-450 faces, got {len(mesh.faces)}")
    if len(mesh.faces) > 600:
        raise ValueError(f"hard face cap exceeded: {len(mesh.faces)}")
    if any(face.material not in MATERIALS for face in mesh.faces):
        raise ValueError("mesh references an undeclared material")
    if any(
        index < 1 or index > len(mesh.vertices)
        for face in mesh.faces
        for index in face.indices
    ):
        raise ValueError("mesh has an invalid OBJ vertex index")
    degenerate = [face for face in mesh.faces if triangle_area_squared(mesh, face) <= 1e-14]
    if degenerate:
        raise ValueError(f"mesh has {len(degenerate)} degenerate triangles")

    x_bounds, y_bounds, z_bounds = mesh_bounds(mesh)
    dimensions = (
        x_bounds[1] - x_bounds[0],
        y_bounds[1] - y_bounds[0],
        z_bounds[1] - z_bounds[0],
    )
    if not 3.00 <= dimensions[0] <= 3.40:
        raise ValueError(f"expected model width of 3.00-3.40 tiles, got {dimensions[0]:.6f}")
    if not 1.80 <= dimensions[1] <= 2.20:
        raise ValueError(f"expected model depth of 1.80-2.20 tiles, got {dimensions[1]:.6f}")
    if not 2.20 <= dimensions[2] <= 2.60:
        raise ValueError(f"expected model height of 2.20-2.60 tiles, got {dimensions[2]:.6f}")
    if abs((x_bounds[0] + x_bounds[1]) / 2.0) > 0.001:
        raise ValueError(f"model is not centered on X: {x_bounds}")
    if abs((y_bounds[0] + y_bounds[1]) / 2.0) > 0.015:
        raise ValueError(f"model is not centered on Y: {y_bounds}")
    if abs(z_bounds[0]) > 1e-9:
        raise ValueError(f"model is not grounded at Z=0: {z_bounds}")

    groups = Counter(face.group for face in mesh.faces)
    post_groups = {group for group in groups if group.startswith("post_")}
    canopy_groups = {group for group in groups if group.startswith("canopy_")}
    rope_groups = {group for group in groups if group.startswith("hanging_rope_")}
    charm_groups = {group for group in groups if group.startswith("void_charm_")}
    if len(post_groups) != 4:
        raise ValueError(f"expected four mismatched posts, got {len(post_groups)}")
    if canopy_groups != {
        "canopy_left_panel",
        "canopy_right_panel",
        "canopy_repair_patch",
        "canopy_torn_tab",
    }:
        raise ValueError("canopy must contain two panels, a repair patch, and a torn tab")
    if len(rope_groups) != 2 or len(charm_groups) != 2:
        raise ValueError("shelter must contain exactly two rope-and-charm hangings")
    required_furnishings = {
        "quartermaster_counter",
        "counter_ledge",
        "counter_legs",
        "supply_shelf_supports",
        "supply_shelf_planks",
        "supply_bundle_01",
        "supply_bundle_02",
    }
    if not required_furnishings.issubset(groups):
        raise ValueError("market shelter is missing one or more low furnishing groups")

    validate_openness(mesh)

    material_counts = Counter(face.material for face in mesh.faces)
    if set(material_counts) != set(MATERIALS):
        raise ValueError("every declared flat material must be used")
    if material_counts["charm_violet"] > 20:
        raise ValueError("bright violet must remain a small accent")
    if EXPECTED_MATERIAL_COUNTS and material_counts != EXPECTED_MATERIAL_COUNTS:
        raise ValueError(
            f"material counts changed: expected {EXPECTED_MATERIAL_COUNTS}, got {material_counts}"
        )
    if EXPECTED_GROUP_COUNTS and groups != EXPECTED_GROUP_COUNTS:
        raise ValueError(
            f"component counts changed: expected {EXPECTED_GROUP_COUNTS}, got {groups}"
        )


def format_float(value: float) -> str:
    if abs(value) < 0.0000005:
        value = 0.0
    return f"{value:.6f}"


def render_obj(mesh: Mesh) -> str:
    lines = [
        "# Original Voidscape Void market shelter source mesh.",
        "# Generated deterministically by scripts/generate-void-market-shelter-source.py.",
        "# Blender-style coordinates: Z up, one source unit equals one game tile.",
        "mtllib void_market_shelter.mtl",
        "o void_market_shelter",
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
        "# Flat colors for the original Voidscape Void market shelter source mesh.",
        "# Textures, transparency, specular maps, and external assets are intentionally absent.",
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
    material_counts = Counter(face.material for face in mesh.faces)
    group_counts = Counter(face.group for face in mesh.faces)
    print(f"Void market shelter source {mode}")
    print(f"vertices: {len(mesh.vertices)}")
    print(f"faces: {len(mesh.faces)} triangles")
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
    print("components:")
    for group, count in group_counts.items():
        print(f"  {group}: {count} faces")
    print(f"obj sha256: {sha256(obj_text)}")
    print(f"mtl sha256: {sha256(mtl_text)}")


def check_committed(obj_text: str, mtl_text: str) -> None:
    missing = [path for path in (OBJ_PATH, MTL_PATH) if not path.is_file()]
    if missing:
        raise ValueError("missing generated output: " + ", ".join(str(path) for path in missing))
    mismatched = []
    if OBJ_PATH.read_text(encoding="utf-8") != obj_text:
        mismatched.append(str(OBJ_PATH))
    if MTL_PATH.read_text(encoding="utf-8") != mtl_text:
        mismatched.append(str(MTL_PATH))
    if mismatched:
        raise ValueError(
            "generated output is stale: "
            + ", ".join(mismatched)
            + "; rerun scripts/generate-void-market-shelter-source.py"
        )
    actual_obj_hash = sha256(obj_text)
    actual_mtl_hash = sha256(mtl_text)
    if EXPECTED_OBJ_SHA256 and actual_obj_hash != EXPECTED_OBJ_SHA256:
        raise ValueError(
            f"OBJ hash changed: expected {EXPECTED_OBJ_SHA256}, got {actual_obj_hash}"
        )
    if EXPECTED_MTL_SHA256 and actual_mtl_hash != EXPECTED_MTL_SHA256:
        raise ValueError(
            f"MTL hash changed: expected {EXPECTED_MTL_SHA256}, got {actual_mtl_hash}"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="validate geometry and verify the committed OBJ/MTL byte-for-byte",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        # Building twice makes accidental non-determinism fail even before the
        # generated files are compared with the committed source.
        mesh = build_mesh()
        obj_text = render_obj(mesh)
        mtl_text = render_mtl()
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
