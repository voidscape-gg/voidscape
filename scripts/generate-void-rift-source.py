#!/usr/bin/env python3
"""Generate the original low-poly source mesh for Voidscape's Void Rift.

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
SOURCE_DIR = REPO_ROOT / "content/custom/void_rift_structure/art/source"
OBJ_PATH = SOURCE_DIR / "void_rift_structure.obj"
MTL_PATH = SOURCE_DIR / "void_rift_structure.mtl"

# Insertion order is also the stable order in the generated MTL.
MATERIALS: dict[str, tuple[int, int, int]] = {
    "rift_core": (32, 8, 48),
    "voidstone_dark": (64, 56, 72),
    "gunmetal": (96, 88, 104),
    "bruised_violet": (128, 56, 152),
    "rift_violet": (184, 96, 216),
}


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
class SupportSpec:
    angle_degrees: float
    crown_rib: bool
    top_z: float
    phase_degrees: float
    tangent_direction: float
    width_scale: float
    top_radial_slope: float
    top_tangent_slope: float


SUPPORTS = (
    SupportSpec(4.0, True, 1.92, 8.0, 1.0, 1.00, 0.42, -0.24),
    SupportSpec(64.0, False, 0.89, 27.0, -1.0, 0.92, -0.34, 0.38),
    SupportSpec(126.0, True, 1.78, 49.0, -1.0, 1.06, -0.39, -0.25),
    SupportSpec(184.0, False, 1.03, 3.0, 1.0, 1.02, 0.32, -0.42),
    SupportSpec(244.0, True, 1.94, 36.0, 1.0, 0.96, 0.40, 0.28),
    SupportSpec(305.0, False, 0.78, 18.0, -1.0, 1.08, -0.36, -0.33),
)

RIFT_TEAR_GROUP = "upright_rift_tear"
RIFT_TEAR_ROTATION_DEGREES = 25.0


def polar(radius: float, angle: float, z: float) -> tuple[float, float, float]:
    return (radius * math.cos(angle), radius * math.sin(angle), z)


def add_segmented_plinth(mesh: Mesh) -> None:
    """Add twelve separated, uneven wedge blocks around the Rift basin."""
    count = 12
    angle_jitter = (-0.006, 0.004, -0.003, 0.007, -0.005, 0.002) * 2
    outer_radii = (0.94, 0.96, 0.92, 0.95, 0.93, 0.96, 0.94, 0.92, 0.95, 0.93, 0.96, 0.92)
    inner_radii = (0.63, 0.65, 0.62, 0.64, 0.63, 0.66, 0.64, 0.62, 0.65, 0.63, 0.66, 0.62)
    top_heights = (0.175, 0.205, 0.165, 0.195, 0.180, 0.220, 0.170, 0.190, 0.215, 0.160, 0.200, 0.180)
    gap = math.radians(3.2)
    segment_span = math.tau / count

    for index in range(count):
        group = f"sunken_plinth_{index + 1:02d}"
        center = index * segment_span + angle_jitter[index]
        start = center - (segment_span - gap) / 2.0
        end = center + (segment_span - gap) / 2.0
        inner = inner_radii[index]
        outer = outer_radii[index]
        base_z = 0.0
        top = top_heights[index]

        # Ordered counter-clockwise when viewed from above.
        corners = (
            polar(inner, start, base_z),
            polar(outer, start, base_z),
            polar(outer, end, base_z),
            polar(inner, end, base_z),
        )
        bottom = [mesh.vertex(*corner) for corner in corners]

        # Small corner-to-corner offsets keep the stones fractured and planar
        # enough to retain a chunky RSC silhouette.
        top_offsets = (
            0.006 * math.sin(index * 1.7),
            0.010 * math.cos(index * 1.3 + 0.4),
            0.008 * math.sin(index * 1.1 + 1.2),
            -0.007 * math.cos(index * 1.5 + 0.2),
        )
        top_vertices = [
            mesh.vertex(x, y, top + top_offsets[corner_index])
            for corner_index, (x, y, _z) in enumerate(corners)
        ]

        b0, b1, b2, b3 = bottom
        t0, t1, t2, t3 = top_vertices
        top_material = "gunmetal" if index % 3 == 0 else "voidstone_dark"
        inner_material = "bruised_violet" if index % 2 == 0 else "voidstone_dark"

        mesh.triangle(t0, t1, t2, top_material, group)
        mesh.triangle(t0, t2, t3, top_material, group)
        mesh.triangle(b0, b2, b1, "voidstone_dark", group)
        mesh.triangle(b0, b3, b2, "voidstone_dark", group)
        mesh.quad(b0, b1, t1, t0, "gunmetal", group, flip_diagonal=index % 2 == 0)
        mesh.quad(b1, b2, t2, t1, "voidstone_dark", group, flip_diagonal=index % 2 == 1)
        mesh.quad(b2, b3, t3, t2, "gunmetal", group, flip_diagonal=index % 2 == 0)
        mesh.quad(b3, b0, t0, t3, inner_material, group, flip_diagonal=index % 2 == 1)


def add_recessed_core(mesh: Mesh) -> None:
    """Add a broad, filled picking surface inside the plinth."""
    group = "recessed_rift_core"
    segments = 24
    radius = 0.585
    center = mesh.vertex(0.0, 0.0, 0.035)
    rim = [
        mesh.vertex(*polar(radius, math.tau * index / segments, 0.035))
        for index in range(segments)
    ]
    for index in range(segments):
        mesh.triangle(center, rim[index], rim[(index + 1) % segments], "rift_core", group)


def add_segmented_inner_lip(mesh: Mesh) -> None:
    """Frame the recessed aperture with a broken, luminous inner bevel."""
    group = "segmented_inner_lip"
    segments = 16
    gap = math.radians(1.5)
    segment_span = math.tau / segments
    inner_radius = 0.580
    outer_radius = 0.660

    for index in range(segments):
        center = index * segment_span
        start = center - (segment_span - gap) / 2.0
        end = center + (segment_span - gap) / 2.0
        inner_height = 0.052 + 0.004 * math.sin(index * 1.3)
        outer_height = 0.152 + 0.012 * math.cos(index * 1.1 + 0.2)
        inner_start = mesh.vertex(*polar(inner_radius, start, inner_height))
        outer_start = mesh.vertex(*polar(outer_radius, start, outer_height))
        outer_end = mesh.vertex(*polar(outer_radius, end, outer_height))
        inner_end = mesh.vertex(*polar(inner_radius, end, inner_height))

        if index % 4 == 0:
            material = "rift_violet"
        elif index % 2 == 0:
            material = "bruised_violet"
        else:
            material = "gunmetal"
        mesh.quad(
            inner_start,
            outer_start,
            outer_end,
            inner_end,
            material,
            group,
            flip_diagonal=index % 2 == 1,
        )


def add_upright_rift_tear(mesh: Mesh) -> None:
    """Add a closed jagged Rift tear that remains legible above the basin."""
    # Coordinates are (horizontal, height) in the tear's local upright plane,
    # ordered clockwise when viewed from its front. The inner loop follows the
    # outer fractures without overlapping the broken violet border.
    outer_loop = (
        (-0.050, 0.080),
        (-0.200, 0.280),
        (-0.330, 0.600),
        (-0.240, 0.920),
        (-0.270, 1.080),
        (-0.100, 1.230),
        (0.040, 1.330),
        (0.100, 1.140),
        (0.280, 0.950),
        (0.310, 0.670),
        (0.220, 0.400),
        (0.090, 0.170),
    )
    inner_loop = (
        (-0.020, 0.160),
        (-0.130, 0.330),
        (-0.250, 0.610),
        (-0.170, 0.900),
        (-0.180, 1.040),
        (-0.070, 1.150),
        (0.025, 1.240),
        (0.040, 1.080),
        (0.190, 0.900),
        (0.230, 0.670),
        (0.150, 0.430),
        (0.060, 0.240),
    )
    half_depth = 0.120
    rotation = math.radians(RIFT_TEAR_ROTATION_DEGREES)
    cos_rotation = math.cos(rotation)
    sin_rotation = math.sin(rotation)

    def tear_vertex(horizontal: float, depth: float, height: float) -> int:
        x = horizontal * cos_rotation - depth * sin_rotation
        y = horizontal * sin_rotation + depth * cos_rotation
        return mesh.vertex(x, y, height)

    front_outer = [tear_vertex(horizontal, half_depth, height) for horizontal, height in outer_loop]
    front_inner = [tear_vertex(horizontal, half_depth, height) for horizontal, height in inner_loop]
    back_outer = [tear_vertex(horizontal, -half_depth, height) for horizontal, height in outer_loop]
    back_inner = [tear_vertex(horizontal, -half_depth, height) for horizontal, height in inner_loop]
    inner_center_height = sum(height for _horizontal, height in inner_loop) / len(inner_loop)
    inner_center_horizontal = sum(horizontal for horizontal, _height in inner_loop) / len(inner_loop)
    front_center = tear_vertex(inner_center_horizontal, half_depth, inner_center_height)
    back_center = tear_vertex(inner_center_horizontal, -half_depth, inner_center_height)

    segments = len(outer_loop)
    accent_segments = {1, 5, 9}
    for index in range(segments):
        next_index = (index + 1) % segments
        border_material = "rift_violet" if index in accent_segments else "bruised_violet"

        # Front and back are distinct caps, not coplanar duplicates. Their
        # opposite winding keeps both broad faces available to scene lighting.
        mesh.quad(
            front_outer[index],
            front_outer[next_index],
            front_inner[next_index],
            front_inner[index],
            border_material,
            RIFT_TEAR_GROUP,
            flip_diagonal=index % 2 == 1,
        )
        mesh.triangle(
            front_center,
            front_inner[index],
            front_inner[next_index],
            "rift_core",
            RIFT_TEAR_GROUP,
        )
        mesh.quad(
            back_outer[next_index],
            back_outer[index],
            back_inner[index],
            back_inner[next_index],
            border_material,
            RIFT_TEAR_GROUP,
            flip_diagonal=index % 2 == 0,
        )
        mesh.triangle(
            back_center,
            back_inner[next_index],
            back_inner[index],
            "rift_core",
            RIFT_TEAR_GROUP,
        )

        thickness_material = "gunmetal" if index % 2 == 0 else "voidstone_dark"
        mesh.quad(
            front_outer[index],
            back_outer[index],
            back_outer[next_index],
            front_outer[next_index],
            thickness_material,
            RIFT_TEAR_GROUP,
            flip_diagonal=index % 3 == 0,
        )


def support_rings(spec: SupportSpec) -> tuple[tuple[float, float, float, float, float], ...]:
    """Return (z, radius, tangent offset, radial half-width, tangent half-width)."""
    if spec.crown_rib:
        return (
            (0.118, 0.760, 0.000, 0.200, 0.160),
            (0.560, 0.790, 0.024 * spec.tangent_direction, 0.180, 0.140),
            (1.100, 0.770, -0.018 * spec.tangent_direction, 0.150, 0.108),
            (spec.top_z, 0.690, 0.052 * spec.tangent_direction, 0.130, 0.098),
        )
    return (
        (0.118, 0.760, 0.000, 0.200, 0.160),
        (0.350, 0.790, -0.016 * spec.tangent_direction, 0.170, 0.135),
        (0.620, 0.770, 0.024 * spec.tangent_direction, 0.135, 0.100),
        (spec.top_z, 0.700, 0.056 * spec.tangent_direction, 0.112, 0.084),
    )


def add_support(mesh: Mesh, index: int, spec: SupportSpec) -> None:
    kind = "crown_rib" if spec.crown_rib else "broken_shard"
    group = f"support_{index + 1:02d}_{kind}"
    angle = math.radians(spec.angle_degrees)
    radial_axis = (math.cos(angle), math.sin(angle))
    tangent_axis = (-math.sin(angle), math.cos(angle))
    phase = math.radians(spec.phase_degrees)
    sides = 6
    rings: list[list[int]] = []

    ring_specs = support_rings(spec)
    for ring_index, (z, center_radius, tangent_offset, radial_width, tangent_width) in enumerate(ring_specs):
        ring: list[int] = []
        twist = phase + ring_index * math.radians(7.0 * spec.tangent_direction)
        for side in range(sides):
            theta = math.tau * side / sides + twist
            local_radial = math.cos(theta) * radial_width * spec.width_scale
            local_tangent = math.sin(theta) * tangent_width * spec.width_scale
            x = radial_axis[0] * (center_radius + local_radial) + tangent_axis[0] * (
                tangent_offset + local_tangent
            )
            y = radial_axis[1] * (center_radius + local_radial) + tangent_axis[1] * (
                tangent_offset + local_tangent
            )

            # The upper fracture is strongly sloped; lower cuts retain a much
            # smaller echo of that angle so no two supports are identical.
            slope_scale = 1.0 if ring_index == len(ring_specs) - 1 else 0.10
            vertex_z = z + slope_scale * (
                spec.top_radial_slope * local_radial
                + spec.top_tangent_slope * local_tangent
            )
            ring.append(mesh.vertex(x, y, vertex_z))
        rings.append(ring)

    # Assign each continuous side strip by its physical relationship to the
    # aperture. The palette therefore stays stable through every layer and on
    # every support: shadowed stone faces outward, metal catches the tangent
    # edges, and bruised violet marks the inward faces touched by the Rift.
    side_materials = []
    for side in range(sides):
        face_theta = phase + math.tau * (side + 0.5) / sides
        radial_component = math.cos(face_theta)
        tangent_component = math.sin(face_theta)
        if abs(tangent_component) > abs(radial_component):
            side_materials.append("gunmetal")
        elif radial_component > 0.0:
            side_materials.append("voidstone_dark")
        else:
            side_materials.append("bruised_violet")

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
                side_materials[side],
                group,
                flip_diagonal=(side + layer + index) % 2 == 1,
            )

    bottom_ring = rings[0]
    bottom_center = mesh.vertex(
        sum(mesh.vertices[vertex - 1][0] for vertex in bottom_ring) / sides,
        sum(mesh.vertices[vertex - 1][1] for vertex in bottom_ring) / sides,
        sum(mesh.vertices[vertex - 1][2] for vertex in bottom_ring) / sides,
    )
    top_ring = rings[-1]
    top_center = mesh.vertex(
        sum(mesh.vertices[vertex - 1][0] for vertex in top_ring) / sides,
        sum(mesh.vertices[vertex - 1][1] for vertex in top_ring) / sides,
        sum(mesh.vertices[vertex - 1][2] for vertex in top_ring) / sides,
    )
    for side in range(sides):
        next_side = (side + 1) % sides
        mesh.triangle(bottom_center, bottom_ring[next_side], bottom_ring[side], "voidstone_dark", group)
        top_material = "rift_violet" if side % 3 == 0 else "bruised_violet"
        mesh.triangle(top_center, top_ring[side], top_ring[next_side], top_material, group)


def build_mesh() -> Mesh:
    mesh = Mesh()
    add_segmented_plinth(mesh)
    add_recessed_core(mesh)
    add_segmented_inner_lip(mesh)
    add_upright_rift_tear(mesh)
    for index, support in enumerate(SUPPORTS):
        add_support(mesh, index, support)
    validate_mesh(mesh)
    return mesh


def triangle_area_squared(mesh: Mesh, face: Face) -> float:
    a, b, c = (mesh.vertices[index - 1] for index in face.indices)
    ab = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    ac = (c[0] - a[0], c[1] - a[1], c[2] - a[2])
    cross = (
        ab[1] * ac[2] - ab[2] * ac[1],
        ab[2] * ac[0] - ab[0] * ac[2],
        ab[0] * ac[1] - ab[1] * ac[0],
    )
    return sum(component * component for component in cross) / 4.0


def mesh_bounds(mesh: Mesh) -> tuple[tuple[float, float], tuple[float, float], tuple[float, float]]:
    return tuple(
        (min(vertex[axis] for vertex in mesh.vertices), max(vertex[axis] for vertex in mesh.vertices))
        for axis in range(3)
    )  # type: ignore[return-value]


def validate_mesh(mesh: Mesh) -> None:
    if not 350 <= len(mesh.faces) <= 600:
        raise ValueError(f"expected 350-600 faces, got {len(mesh.faces)}")
    if len(mesh.faces) > 800:
        raise ValueError(f"hard face cap exceeded: {len(mesh.faces)}")
    if any(face.material not in MATERIALS for face in mesh.faces):
        raise ValueError("mesh references an undeclared material")
    if any(index < 1 or index > len(mesh.vertices) for face in mesh.faces for index in face.indices):
        raise ValueError("mesh has an invalid OBJ vertex index")
    degenerate = [face for face in mesh.faces if triangle_area_squared(mesh, face) <= 1e-14]
    if degenerate:
        raise ValueError(f"mesh has {len(degenerate)} degenerate triangles")

    x_bounds, y_bounds, z_bounds = mesh_bounds(mesh)
    if max(abs(x_bounds[0]), abs(x_bounds[1]), abs(y_bounds[0]), abs(y_bounds[1])) > 0.980001:
        raise ValueError(f"XY footprint exceeds +/-0.98 tiles: x={x_bounds}, y={y_bounds}")
    height = z_bounds[1] - z_bounds[0]
    if not 1.75 <= height <= 2.05:
        raise ValueError(f"expected model height of 1.75-2.05 tiles, got {height:.6f}")

    support_groups = {face.group for face in mesh.faces if face.group.startswith("support_")}
    crown_groups = {group for group in support_groups if group.endswith("crown_rib")}
    if len(support_groups) != 6 or len(crown_groups) != 3:
        raise ValueError("mesh must contain six supports, including three crown ribs")
    core_faces = [face for face in mesh.faces if face.group == "recessed_rift_core"]
    if len(core_faces) != 24 or any(face.material != "rift_core" for face in core_faces):
        raise ValueError("recessed core must be a filled 24-triangle near-black surface")
    lip_faces = [face for face in mesh.faces if face.group == "segmented_inner_lip"]
    lip_materials = Counter(face.material for face in lip_faces)
    if len(lip_faces) != 32 or lip_materials != Counter(
        {"rift_violet": 8, "bruised_violet": 8, "gunmetal": 16}
    ):
        raise ValueError("inner lip must contain sixteen deterministic broken bevel segments")

    tear_faces = [face for face in mesh.faces if face.group == RIFT_TEAR_GROUP]
    tear_materials = Counter(face.material for face in tear_faces)
    expected_tear_materials = Counter(
        {
            "rift_core": 24,
            "voidstone_dark": 12,
            "gunmetal": 12,
            "bruised_violet": 36,
            "rift_violet": 12,
        }
    )
    if len(tear_faces) != 96 or tear_materials != expected_tear_materials:
        raise ValueError("upright Rift tear must contain 96 deterministic material-assigned faces")

    tear_indices = {index for face in tear_faces for index in face.indices}
    if len(tear_indices) != 50:
        raise ValueError(f"upright Rift tear must use 50 vertices, got {len(tear_indices)}")
    tear_vertices = [mesh.vertices[index - 1] for index in tear_indices]
    rotation = math.radians(RIFT_TEAR_ROTATION_DEGREES)
    cos_rotation = math.cos(rotation)
    sin_rotation = math.sin(rotation)
    local_horizontal = [x * cos_rotation + y * sin_rotation for x, y, _z in tear_vertices]
    local_depth = [-x * sin_rotation + y * cos_rotation for x, y, _z in tear_vertices]
    tear_width = max(local_horizontal) - min(local_horizontal)
    tear_depth = max(local_depth) - min(local_depth)
    tear_min_z = min(z for _x, _y, z in tear_vertices)
    tear_max_z = max(z for _x, _y, z in tear_vertices)
    tear_max_radius = max(math.hypot(x, y) for x, y, _z in tear_vertices)
    if not 0.60 <= tear_width <= 0.70:
        raise ValueError(f"upright Rift tear width must be 0.60-0.70 tiles, got {tear_width:.6f}")
    if not 0.20 <= tear_depth <= 0.26:
        raise ValueError(f"upright Rift tear depth must be 0.20-0.26 tiles, got {tear_depth:.6f}")
    if not 0.07 <= tear_min_z <= 0.10 or not 1.25 <= tear_max_z <= 1.35:
        raise ValueError(
            f"upright Rift tear height bounds are invalid: z=[{tear_min_z:.6f}, {tear_max_z:.6f}]"
        )
    if tear_max_radius >= 0.585:
        raise ValueError(f"upright Rift tear escapes the ground core: radius={tear_max_radius:.6f}")

    tear_edge_counts: Counter[tuple[int, int]] = Counter()
    for face in tear_faces:
        a, b, c = face.indices
        tear_edge_counts.update(
            (
                tuple(sorted((a, b))),
                tuple(sorted((b, c))),
                tuple(sorted((c, a))),
            )
        )
    non_manifold_edges = [edge for edge, count in tear_edge_counts.items() if count != 2]
    if non_manifold_edges:
        raise ValueError(f"upright Rift tear is not closed: {len(non_manifold_edges)} invalid edges")


def format_float(value: float) -> str:
    if abs(value) < 0.0000005:
        value = 0.0
    return f"{value:.6f}"


def render_obj(mesh: Mesh) -> str:
    lines = [
        "# Original Voidscape Void Rift source mesh.",
        "# Generated deterministically by scripts/generate-void-rift-source.py.",
        "# Blender-style coordinates: Z up, one source unit equals one game tile.",
        "mtllib void_rift_structure.mtl",
        "o void_rift_structure",
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
        "# Flat colors for the original Voidscape Void Rift source mesh.",
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
    print(f"Void Rift source {mode}")
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
            + "; rerun scripts/generate-void-rift-source.py"
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
