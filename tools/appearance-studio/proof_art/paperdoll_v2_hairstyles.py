#!/usr/bin/env python3
"""Deterministically rasterize Voidscape's content-owned Paperdoll V2 hair masters.

The final geometry is explicit crown-relative pixel data.  There is no image
generation, interpolation, antialiasing, or per-style compiler path here: one
nearest-pixel rasterizer consumes all style records, clips them to the locked
``rsc-player-2x-v1`` masks, and writes six canonical RGBA masters per style.

The approved rare-spikes record intentionally duplicates the frozen Gate-1
geometry.  Its raw RGBA digests are asserted before anything is written so this
collection generator cannot silently "improve" already approved pixels.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import sys
from typing import Iterable, Mapping, Sequence

from PIL import Image, ImageDraw


SCRIPT = Path(__file__).resolve()
REPO_ROOT = SCRIPT.parents[3]
TOOL_ROOT = REPO_ROOT / "tools/appearance-studio"
if str(TOOL_ROOT) not in sys.path:
    sys.path.insert(0, str(TOOL_ROOT))

from appearance_studio.v2_template import MASTERS, load_v2_template  # noqa: E402


Point = tuple[int, int]


@dataclass(frozen=True)
class Fill:
    shade: int
    alpha: int
    points: tuple[Point, ...]


@dataclass(frozen=True)
class Stroke:
    shade: int
    alpha: int
    width: int
    points: tuple[Point, ...]


@dataclass(frozen=True)
class Pixel:
    shade: int
    alpha: int
    point: Point


@dataclass(frozen=True)
class PoseArt:
    fills: tuple[Fill, ...]
    strokes: tuple[Stroke, ...] = ()
    pixels: tuple[Pixel, ...] = ()
    clip_fill_count: int | None = None


@dataclass(frozen=True)
class StyleArt:
    label: str
    rationale: str
    poses: Mapping[str, PoseArt]


def fill(shade: int, points: Sequence[Point], alpha: int = 255) -> Fill:
    return Fill(shade, alpha, tuple(points))


def stroke(shade: int, points: Sequence[Point], width: int = 1, alpha: int = 255) -> Stroke:
    return Stroke(shade, alpha, width, tuple(points))


def pixel(shade: int, point: Point, alpha: int = 255) -> Pixel:
    return Pixel(shade, alpha, point)


def pose(
    fills: Sequence[Fill],
    strokes: Sequence[Stroke] = (),
    pixels: Sequence[Pixel] = (),
    *,
    clip_fill_count: int | None = None,
) -> PoseArt:
    return PoseArt(tuple(fills), tuple(strokes), tuple(pixels), clip_fill_count)


# Gate-1 rare-spikes geometry.  Keep these points and draw order byte-for-byte
# equivalent to paperdoll_v2_proof.py.
RARE_SILHOUETTES = {
    "north": [
        (-18, 24), (-20, 15), (-22, 10), (-27, 4), (-18, 3), (-23, -8), (-13, -4),
        (-11, -17), (-3, -7), (4, -18), (8, -6), (18, -13), (16, -2),
        (27, 2), (18, 7), (20, 14), (18, 24), (15, 20), (13, 11),
        (10, 9), (8, 15), (5, 10), (2, 12), (0, 16), (-2, 11),
        (-6, 14), (-8, 9), (-12, 12), (-15, 20),
    ],
    "north-west": [
        (-14, 28), (-22, 18), (-28, 8), (-18, 7), (-28, -2), (-16, -2),
        (-21, -14), (-10, -6), (-5, -18), (2, -6), (12, -12), (10, -1),
        (22, -1), (16, 5), (13, 9), (11, 13), (13, 18), (10, 16),
        (8, 10), (4, 13), (1, 9), (-3, 12), (-7, 12), (-9, 22),
    ],
    "west": [
        (-14, 29), (-21, 21), (-28, 14), (-18, 10), (-28, 1), (-17, 0),
        (-21, -10), (-12, -5), (-10, -18), (-1, -7), (8, -14), (8, -3),
        (18, -3), (14, 5), (10, 9), (8, 13), (13, 16), (8, 17),
        (5, 11), (1, 9), (-3, 11), (-5, 15), (-6, 25), (-11, 23),
    ],
    "south-west": [
        (-12, 29), (-22, 17), (-15, 11), (-24, 4), (-17, 3), (-20, -9),
        (-11, -3), (-9, -16), (-2, -6), (4, -14), (9, -4), (16, -9),
        (15, 2), (23, 5), (17, 11), (18, 18), (9, 29), (3, 25),
        (-2, 30), (-7, 26),
    ],
    "south": [
        (-13, 29), (-21, 16), (-15, 11), (-24, 4), (-17, 3), (-20, -9),
        (-11, -3), (-6, -16), (0, -6), (7, -14), (10, -3), (18, -8),
        (16, 2), (23, 5), (17, 11), (20, 17), (12, 29), (6, 25),
        (0, 30), (-6, 25),
    ],
}

RARE_PLANES = {
    "north": [
        [(-19, 17), (-20, 7), (-10, 1), (-11, 16), (-15, 21)],
        [(-9, 12), (-7, -9), (0, -3), (-1, 13)],
        [(1, 12), (4, -13), (10, -4), (9, 14)],
        [(10, 14), (19, -1), (20, 10), (15, 19)],
    ],
    "north-west": [
        [(-21, 19), (-21, 5), (-12, 0), (-12, 18), (-16, 23)],
        [(-10, 13), (-4, -12), (2, -3), (1, 14)],
        [(2, 13), (9, -11), (14, -2), (11, 16)],
        [(11, 15), (21, 3), (20, 12), (16, 21)],
    ],
    "west": [
        [(-22, 20), (-21, 6), (-12, 0), (-12, 19), (-17, 24)],
        [(-10, 14), (-7, -12), (0, -3), (-1, 15)],
        [(0, 12), (7, -11), (12, -1), (8, 12)],
    ],
    "south-west": [
        [(-21, 19), (-21, 5), (-12, 0), (-12, 19), (-17, 24)],
        [(-10, 14), (-4, -12), (2, -3), (1, 17)],
        [(2, 16), (9, -11), (14, -2), (12, 19)],
        [(11, 17), (21, 3), (20, 14), (16, 23)],
    ],
    "south": [
        [(-20, 18), (-20, 6), (-11, 0), (-11, 20), (-16, 24)],
        [(-9, 15), (-7, -12), (0, -3), (0, 20)],
        [(1, 19), (4, -13), (11, -3), (10, 20)],
        [(10, 18), (19, -1), (20, 13), (15, 24)],
    ],
}

RARE_STRANDS = {
    "north": [
        [(-17, 9), (-11, 3), (-10, 13)], [(-8, 4), (-4, -9), (-2, 8)],
        [(1, 3), (4, -13), (6, 7)], [(9, 3), (15, -8), (13, 10)],
        [(-13, 17), (-7, 13), (0, 16), (7, 13), (14, 17)], [(3, 8), (0, 15)],
    ],
    "north-west": [
        [(-18, 9), (-11, 1), (-10, 14)], [(-8, 3), (-3, -11), (0, 9)],
        [(2, 3), (8, -11), (9, 10)], [(10, 5), (18, -5), (15, 13)],
        [(-13, 18), (-6, 13), (2, 17), (10, 13), (16, 18)], [(10, 9), (14, 17)],
    ],
    "west": [
        [(-20, 11), (-12, 1), (-11, 16)], [(-9, 4), (-6, -12), (-1, 9)],
        [(1, 2), (7, -10), (8, 8)], [(-14, 20), (-8, 15), (-2, 18), (5, 12)],
        [(8, 9), (12, 15)],
    ],
    "south-west": [
        [(-18, 9), (-11, 1), (-10, 16)], [(-8, 3), (-3, -11), (0, 11)],
        [(2, 3), (8, -11), (9, 13)], [(10, 5), (18, -5), (16, 14)],
        [(-14, 19), (-7, 14), (0, 20), (7, 14), (14, 20)],
    ],
    "south": [
        [(-17, 9), (-11, 2), (-10, 16)], [(-8, 3), (-4, -9), (-2, 12)],
        [(1, 3), (4, -13), (6, 12)], [(9, 3), (15, -8), (14, 14)],
        [(-14, 19), (-7, 14), (0, 22), (7, 14), (14, 20)],
    ],
}

RARE_RGBA_SHA256 = {
    "north": "43f14bf1c4c2ef19b6602703fecde3ae5713cc6e9c052dfca1dbe8430dc438ad",
    "north-west": "12d6e100c628efe1603b42a963063658042f916b9f31a038e885006829997164",
    "west": "8aedbc254ce7decb1115252ef104ceb635e87be084992f2d9dfc918915c0c666",
    "south-west": "41346519b86a80187588b93b1a4439f80cf83aed7d44a130404218041b78aae1",
    "south": "28afdd736fb247135d2285194bb0302a6f49c74dbac4417861ded9dcfbb63c1f",
    "combat-west": "dfb4f487acf7a4dc1d2dd41d9ab523732b17f3a355e32efa5fa5c926f76d1f51",
}


def rare_pose(name: str) -> PoseArt:
    source = "west" if name == "combat-west" else name
    fills = [fill(184, RARE_SILHOUETTES[source])]
    fills.extend(fill(shade, plane) for shade, plane in zip((102, 136, 218, 160), RARE_PLANES[source]))
    strokes = [
        stroke(252 if index < 2 else 232, strand, width=2)
        for index, strand in enumerate(RARE_STRANDS[source])
    ]
    pixels = [pixel(220, point, alpha=176) for point in ((-24, 7), (4, -18), (24, 7))]
    return pose(fills, strokes, pixels, clip_fill_count=1)


# All following styles use the same restrained seven-shade neutral ramp.  Alpha
# below 255 is reserved for intentional stubble/fade transitions, never blur.
BUZZCUT = {
    "north": pose([
        # A shallow opaque crown supplies a clean, deliberately clipped
        # hairline.  The two darker temple bands are contiguous stubble tiers,
        # not pale translucent tails hanging below the skull.
        fill(132, [(-12, 7), (-13, 3), (-10, -1), (-5, -4), (1, -5),
                   (6, -3), (10, 0), (13, 4), (12, 7), (7, 6), (3, 7),
                   (-1, 6), (-5, 7), (-9, 6)]),
        fill(106, [(-15, 8), (-13, 5), (-10, 6), (-10, 11), (-12, 13),
                   (-15, 11)], 160),
        fill(78, [(-14, 10), (-11, 9), (-10, 12), (-12, 15), (-14, 14)], 160),
        fill(106, [(15, 8), (13, 5), (10, 6), (10, 11), (12, 13),
                   (15, 11)], 160),
        fill(78, [(14, 10), (11, 9), (10, 12), (12, 15), (14, 14)], 160),
        fill(166, [(-8, 3), (-4, -1), (0, -2), (-2, 3)]),
        fill(152, [(1, -2), (6, -1), (10, 2), (5, 3)]),
    ], [stroke(178, [(-6, 5), (-1, 3), (4, 4), (8, 3)], 1),
        stroke(92, [(-10, 10), (-12, 13)], 1, 208),
        stroke(92, [(10, 10), (12, 13)], 1, 208)]),
    "north-west": pose([
        fill(130, [(-13, 8), (-14, 3), (-10, -1), (-4, -4), (2, -5),
                   (8, -2), (12, 1), (14, 5), (12, 8), (7, 7), (2, 8),
                   (-3, 7), (-8, 9)]),
        fill(104, [(-16, 8), (-13, 5), (-10, 7), (-10, 12), (-12, 15),
                   (-15, 13)], 160),
        fill(76, [(-14, 11), (-11, 10), (-10, 13), (-12, 16), (-14, 15)], 160),
        fill(108, [(14, 7), (12, 5), (9, 7), (9, 11), (11, 13), (13, 11)], 160),
        fill(164, [(-8, 3), (-4, -1), (0, -2), (-2, 3)]),
        fill(150, [(1, -2), (7, -1), (10, 2), (5, 3)]),
    ], [stroke(176, [(-6, 5), (-1, 3), (4, 4), (8, 3)], 1),
        stroke(90, [(-10, 11), (-12, 15)], 1, 208)]),
    "west": pose([
        fill(128, [(-13, 8), (-14, 3), (-10, -1), (-4, -4), (2, -5),
                   (8, -2), (12, 1), (14, 5), (12, 8), (8, 7), (4, 6),
                   (0, 7), (-5, 8), (-9, 9)]),
        fill(102, [(-16, 8), (-13, 5), (-10, 7), (-10, 12), (-12, 15),
                   (-15, 13)], 160),
        fill(74, [(-14, 11), (-11, 10), (-10, 13), (-12, 16), (-14, 15)], 160),
        fill(162, [(-8, 3), (-4, -1), (0, -2), (-2, 3)]),
        fill(148, [(1, -2), (7, -1), (10, 2), (5, 3)]),
    ], [stroke(174, [(-6, 5), (-1, 3), (4, 4), (8, 3)], 1),
        stroke(88, [(-10, 11), (-12, 15)], 1, 208)]),
    "south-west": pose([
        fill(128, [(-13, 9), (-14, 4), (-11, 0), (-6, -4), (0, -5),
                   (6, -4), (11, 0), (14, 4), (13, 9), (9, 9), (5, 10),
                   (0, 10), (-5, 10), (-9, 9)]),
        fill(102, [(-15, 8), (-12, 6), (-10, 9), (-10, 13), (-12, 15),
                   (-14, 13)], 160),
        fill(76, [(-13, 11), (-10, 10), (-9, 13), (-11, 16), (-13, 15)], 160),
        fill(108, [(14, 8), (11, 6), (9, 9), (9, 13), (11, 14), (13, 12)], 160),
        # A continuous low nape taper replaces the old pair of pale wedges.
        fill(88, [(-7, 9), (6, 9), (5, 12), (2, 14), (-2, 15), (-5, 13)]),
        fill(162, [(-7, 4), (-3, -1), (1, -2), (-1, 4)]),
        fill(150, [(2, -2), (7, -1), (10, 2), (5, 4)]),
    ], [stroke(176, [(-6, 6), (-1, 3), (4, 4), (8, 3)], 1),
        stroke(78, [(-6, 13), (0, 14), (6, 13)], 1, 208)]),
    "south": pose([
        fill(128, [(-12, 9), (-13, 4), (-10, 0), (-5, -4), (0, -5),
                   (5, -4), (10, 0), (13, 4), (12, 9), (8, 9), (4, 10),
                   (0, 10), (-4, 10), (-8, 9)]),
        fill(102, [(-14, 8), (-11, 6), (-9, 9), (-9, 13), (-11, 15),
                   (-13, 13)], 160),
        fill(76, [(-12, 11), (-9, 10), (-8, 13), (-10, 16), (-12, 15)], 160),
        fill(108, [(14, 8), (11, 6), (9, 9), (9, 13), (11, 15), (13, 13)], 160),
        fill(88, [(-6, 9), (6, 9), (5, 12), (2, 14), (-2, 15), (-5, 12)]),
        fill(162, [(-7, 4), (-3, -1), (1, -2), (-1, 4)]),
        fill(150, [(2, -2), (7, -1), (10, 2), (5, 4)]),
    ], [stroke(176, [(-6, 6), (-1, 3), (4, 4), (8, 3)], 1),
        stroke(78, [(-6, 13), (0, 14), (6, 13)], 1, 208)]),
}
BUZZCUT["combat-west"] = BUZZCUT["west"]


MOHAWK = {
    "north": pose([
        # A low-alpha stubble cap leaves both shaved sides visibly scalp-toned.
        # The opaque ridge is deliberately short, broad-based and double-peaked:
        # from the front it must read as a strip of hair, never a forehead horn.
        fill(92, [(-13, 12), (-13, 8), (-10, 5), (-6, 3), (0, 2), (6, 3),
                  (10, 5), (13, 8), (13, 12), (9, 10), (5, 9), (0, 10),
                  (-5, 9), (-9, 10)], 56),
        fill(136, [(-6, 8), (-6, 2), (-4, 0), (-4, -4), (-3, -7),
                   (-1, -6), (0, -3), (2, -7), (4, -6), (4, -3),
                   (5, 0), (6, 2), (6, 7), (4, 8), (2, 7), (0, 9),
                   (-2, 7), (-4, 8)]),
        fill(76, [(-6, 7), (-6, 0), (-5, -4), (-3, -7), (-2, -6),
                  (-2, 6), (-4, 8)]),
        fill(164, [(1, -5), (2, -7), (4, -6), (4, -3), (2, -2)]),
        fill(148, [(3, -1), (6, 0), (5, 4), (3, 5)]),
    ], [stroke(168, [(-4, -4), (-3, -7), (-1, -6)], 1),
        stroke(132, [(1, 0), (2, 4), (2, 7)], 1),
        stroke(82, [(-10, 8), (-9, 11)], 1, 96)], clip_fill_count=2),
    "north-west": pose([
        fill(90, [(-14, 13), (-14, 9), (-11, 6), (-6, 3), (0, 2), (6, 3),
                  (11, 5), (13, 9), (11, 13), (7, 11), (1, 10), (-5, 11),
                  (-9, 14), (-12, 15)], 56),
        fill(134, [(-6, 9), (-6, 1), (-5, -3), (-4, -7), (-2, -9),
                   (0, -5), (2, -9), (4, -7), (6, -3), (7, 2),
                   (6, 8), (4, 10), (2, 8), (0, 10), (-2, 8),
                   (-4, 9)]),
        fill(74, [(-6, 8), (-6, 1), (-5, -3), (-4, -7), (-3, -8),
                  (-3, 7), (-5, 9)]),
        fill(162, [(1, -7), (2, -9), (4, -7), (5, -4), (3, -2)]),
        fill(146, [(4, -1), (7, 2), (6, 6), (4, 7)]),
    ], [stroke(166, [(-4, -6), (-2, -9), (0, -5)], 1),
        stroke(130, [(1, 0), (2, 4), (2, 8)], 1),
        stroke(80, [(-10, 9), (-9, 13)], 1, 96)], clip_fill_count=2),
    "west": pose([
        fill(88, [(-14, 13), (-14, 9), (-10, 5), (-5, 3), (1, 2), (7, 3),
                  (11, 6), (12, 9), (9, 11), (5, 10), (-2, 10), (-8, 13),
                  (-11, 15), (-13, 15)], 56),
        # Three uneven, slightly backswept peaks share a shallow scalp-hugging
        # base.  Deep valleys and the exposed stubble below prevent a dome,
        # helmet, cap, or umbrella silhouette in profile and combat.
        fill(136, [(-14, 6), (-15, 3), (-14, -2), (-11, -8), (-8, -13),
                   (-5, -11), (-4, -5), (-2, -2), (0, -8), (3, -15),
                   (6, -11), (6, -5), (8, -3), (10, -9), (12, -11),
                   (14, -6), (15, -1), (14, 3), (12, 5), (8, 4),
                   (4, 6), (0, 5), (-4, 6), (-8, 4), (-11, 6)]),
        fill(74, [(-14, 5), (-14, -2), (-11, -8), (-8, -13), (-6, -11),
                  (-7, 3), (-10, 5)]),
        fill(164, [(0, -8), (3, -15), (5, -11), (5, -7), (3, -4),
                   (1, -5)]),
        fill(154, [(9, -7), (12, -11), (13, -7), (13, -4), (11, -3)]),
    ], [stroke(168, [(-10, -9), (-8, -13), (-5, -11)], 1),
        stroke(164, [(9, -7), (12, -11), (14, -6)], 1),
        stroke(128, [(-6, 2), (-1, 3), (4, 3), (9, 2)], 1),
        stroke(78, [(-10, 10), (-9, 14)], 1, 96)], clip_fill_count=2),
    "south-west": pose([
        fill(90, [(-14, 14), (-14, 9), (-11, 6), (-6, 3), (0, 2), (6, 3),
                  (11, 5), (13, 9), (11, 14), (7, 13), (2, 14), (-3, 14),
                  (-8, 14)], 56),
        fill(134, [(-6, 10), (-6, 1), (-5, -3), (-4, -7), (-2, -9),
                   (0, -5), (2, -9), (4, -7), (6, -3), (6, 8),
                   (4, 10), (2, 9), (0, 11), (-2, 9), (-4, 10)]),
        fill(74, [(-6, 9), (-6, 1), (-5, -3), (-4, -7), (-3, -8),
                  (-3, 8), (-5, 10)]),
        fill(162, [(1, -7), (2, -9), (4, -7), (5, -4), (3, -2)]),
        fill(146, [(3, -1), (6, -2), (5, 5), (3, 7)]),
    ], [stroke(166, [(-4, -6), (-2, -9), (0, -5)], 1),
        stroke(130, [(1, 1), (2, 5), (2, 9)], 1),
        stroke(80, [(-8, 12), (-5, 14)], 1, 96)], clip_fill_count=2),
    "south": pose([
        fill(92, [(-13, 14), (-13, 9), (-10, 5), (-5, 2), (0, 1), (5, 2),
                  (10, 5), (13, 9), (11, 14), (7, 14), (2, 14), (-2, 15),
                  (-7, 14), (-10, 14)], 56),
        fill(136, [(-6, 10), (-6, 1), (-5, -3), (-4, -7), (-2, -9),
                   (0, -5), (2, -9), (4, -7), (6, -3), (6, 8),
                   (4, 10), (2, 9), (0, 11), (-2, 9), (-4, 10)]),
        fill(76, [(-6, 9), (-6, 1), (-5, -3), (-4, -7), (-3, -8),
                  (-3, 8), (-5, 10)]),
        fill(164, [(1, -7), (2, -9), (4, -7), (5, -4), (3, -2)]),
        fill(148, [(3, -1), (6, -2), (5, 5), (3, 7)]),
    ], [stroke(168, [(-4, -6), (-2, -9), (0, -5)], 1),
        stroke(132, [(1, 1), (2, 5), (2, 9)], 1),
        stroke(82, [(-4, 13), (0, 14), (4, 13)], 1, 104)], clip_fill_count=2),
}
MOHAWK["combat-west"] = MOHAWK["west"]


TEXTURED_CROP = {
    "north": pose([
        # Compact crown with broken, shallow fringe teeth.  The silhouette now
        # clears the eyes and terminates above the old bowl/bob perimeter.
        fill(158, [(-15, 11), (-16, 6), (-14, 1), (-10, -4), (-6, -5),
                   (-3, -9), (1, -7), (5, -10), (9, -6), (13, -4),
                   (16, 1), (16, 7), (12, 7), (9, 10), (6, 7), (3, 12),
                   (0, 8), (-3, 11), (-6, 7), (-9, 10), (-12, 7), (-14, 11)]),
        fill(88, [(-17, 12), (-17, 5), (-12, -1), (-10, 11), (-13, 15)]),
        fill(124, [(-9, 9), (-7, -4), (0, -6), (0, 10), (-4, 8)]),
        fill(208, [(1, 9), (3, -5), (9, -3), (14, 2), (11, 9), (7, 7)]),
        fill(180, [(10, 9), (15, 0), (17, 6), (14, 11)]),
    ], [stroke(238, [(-12, 4), (-7, -2), (-4, 7)], 1),
        stroke(232, [(-3, 1), (1, -5), (3, 7)], 1),
        stroke(220, [(5, 1), (10, -2), (12, 6)], 1),
        stroke(104, [(-13, 10), (-11, 13)], 1),
        stroke(104, [(13, 9), (11, 12)], 1)], clip_fill_count=1),
    "north-west": pose([
        fill(156, [(-16, 12), (-17, 6), (-14, 0), (-10, -4), (-6, -5),
                   (-3, -9), (1, -7), (5, -10), (10, -6), (14, -3),
                   (17, 2), (16, 8), (12, 8), (9, 11), (6, 8), (3, 12),
                   (0, 9), (-3, 12), (-6, 8), (-10, 11), (-13, 9), (-15, 13)]),
        fill(86, [(-18, 13), (-18, 5), (-12, -1), (-11, 12), (-14, 17)]),
        fill(122, [(-10, 10), (-7, -4), (0, -5), (1, 11), (-4, 8)]),
        fill(206, [(1, 9), (3, -4), (10, -2), (15, 3), (11, 10), (7, 7)]),
        fill(178, [(10, 10), (15, 1), (17, 7), (14, 12)]),
    ], [stroke(236, [(-12, 4), (-7, -2), (-4, 7)], 1),
        stroke(230, [(-3, 1), (1, -4), (4, 7)], 1),
        stroke(218, [(6, 1), (11, -1), (13, 6)], 1),
        stroke(102, [(-14, 11), (-12, 15)], 1)], clip_fill_count=1),
    "west": pose([
        fill(154, [(-17, 13), (-18, 7), (-15, 0), (-11, -4), (-7, -5),
                   (-4, -9), (0, -7), (4, -10), (9, -6), (13, -3),
                   (17, 2), (16, 8), (12, 10), (9, 8), (6, 11), (2, 8),
                   (-2, 12), (-6, 9), (-10, 12), (-14, 14)]),
        fill(84, [(-19, 14), (-19, 6), (-13, -1), (-12, 13), (-15, 18)]),
        fill(120, [(-11, 10), (-7, -4), (0, -5), (1, 10), (-4, 8)]),
        fill(204, [(1, 8), (4, -4), (10, -2), (15, 3), (11, 9), (7, 6)]),
    ], [stroke(234, [(-13, 4), (-7, -2), (-4, 7)], 1),
        stroke(228, [(-3, 1), (1, -4), (4, 7)], 1),
        stroke(216, [(6, 1), (11, -1), (13, 6)], 1),
        stroke(100, [(-15, 11), (-13, 16)], 1)], clip_fill_count=1),
    "south-west": pose([
        fill(154, [(-16, 13), (-17, 7), (-15, 1), (-11, -4), (-7, -5),
                   (-4, -9), (0, -7), (4, -10), (9, -6), (13, -3),
                   (16, 2), (16, 9), (12, 12), (7, 13), (2, 14), (-3, 14),
                   (-8, 13), (-12, 12)]),
        fill(84, [(-18, 15), (-18, 6), (-12, -1), (-11, 14), (-14, 18)]),
        fill(120, [(-10, 12), (-7, -4), (0, -5), (1, 14), (-4, 11)]),
        fill(204, [(1, 12), (4, -4), (10, -2), (15, 4), (11, 13), (7, 9)]),
        fill(176, [(10, 13), (15, 2), (17, 9), (14, 15)]),
    ], [stroke(234, [(-12, 4), (-7, -2), (-4, 8)], 1),
        stroke(228, [(-3, 1), (1, -4), (4, 9)], 1),
        stroke(216, [(6, 1), (11, -1), (13, 8)], 1),
        stroke(100, [(-8, 15), (0, 16), (8, 14)], 1)], clip_fill_count=1),
    "south": pose([
        fill(154, [(-15, 12), (-16, 6), (-14, 0), (-10, -4), (-6, -5),
                   (-3, -9), (1, -7), (5, -10), (9, -6), (13, -3),
                   (16, 2), (16, 8), (12, 12), (7, 13), (2, 14), (-3, 14),
                   (-8, 13), (-12, 11)]),
        fill(84, [(-17, 15), (-17, 6), (-11, -1), (-10, 14), (-13, 18)]),
        fill(120, [(-9, 12), (-6, -4), (1, -5), (1, 14), (-4, 11)]),
        fill(204, [(2, 12), (4, -4), (11, -2), (16, 4), (12, 13), (7, 9)]),
        fill(176, [(11, 13), (16, 2), (17, 9), (14, 15)]),
    ], [stroke(234, [(-11, 4), (-6, -2), (-3, 8)], 1),
        stroke(228, [(-2, 1), (2, -4), (5, 9)], 1),
        stroke(216, [(7, 1), (12, -1), (14, 8)], 1),
        stroke(100, [(-8, 15), (0, 16), (8, 14)], 1)], clip_fill_count=1),
}
TEXTURED_CROP["combat-west"] = TEXTURED_CROP["west"]


SLICK_BACK = {
    "north": pose([
        fill(102, [(-17, 18), (-18, 10), (-15, 3), (-8, 0), (0, -1), (8, 0),
                   (15, 4), (17, 11), (15, 18), (10, 14), (4, 12), (0, 13),
                   (-5, 12), (-11, 15)], 136),
        fill(166, [(-16, 13), (-16, 5), (-11, -1), (-4, -5), (4, -6), (11, -4),
                   (15, -1), (14, 5), (11, 9), (7, 8), (1, 6), (-5, 7), (-11, 11)]),
        fill(96, [(-16, 13), (-16, 5), (-11, 0), (-10, 11), (-13, 18)]),
        fill(132, [(-9, 9), (-8, -1), (-2, -4), (1, 7), (-4, 6)]),
        fill(216, [(1, 6), (2, -5), (8, -4), (14, -1), (12, 4), (7, 3)]),
    ], [stroke(250, [(-7, 1), (-1, -4), (6, -3), (12, 0)], 1),
        stroke(238, [(-9, 4), (-3, 0), (4, -1), (10, 2)], 1),
        stroke(224, [(-10, 7), (-4, 4), (3, 3), (8, 5)], 1),
        stroke(102, [(-13, 14), (-11, 22)], 2, 176), stroke(102, [(13, 14), (11, 22)], 2, 176)]),
    "north-west": pose([
        fill(92, [(-16, 17), (-16, 10), (-13, 5), (-7, 2), (0, 1), (7, 2),
                  (12, 5), (13, 10), (10, 14), (5, 11), (0, 10), (-5, 12),
                  (-9, 16), (-11, 19), (-14, 19)], 168),
        fill(162, [(-13, 12), (-15, 10), (-14, 8), (-17, 7), (-15, 5),
                   (-17, 3), (-14, 1), (-11, -2), (-6, -5), (0, -6),
                   (7, -4), (12, -1), (13, 3), (10, 6), (6, 5), (2, 7),
                   (-2, 6), (-5, 9), (-8, 7), (-10, 11), (-12, 9)]),
        fill(72, [(-15, 16), (-13, 11), (-10, 13), (-9, 17), (-11, 19), (-14, 18)], 160),
        fill(118, [(-13, 9), (-14, 4), (-10, 0), (-6, -3), (-5, 8), (-9, 7)]),
        fill(206, [(-3, 6), (-2, -5), (5, -4), (11, 0), (9, 5), (4, 3)]),
    ], [stroke(246, [(-11, 0), (-6, -4), (0, -5), (7, -3), (11, 0)], 1),
        stroke(232, [(-13, 4), (-7, 0), (-1, -1), (6, 1), (9, 3)], 1),
        stroke(216, [(-12, 7), (-7, 4), (-2, 3), (4, 4), (7, 5)], 1)]),
    "west": pose([
        fill(90, [(-15, 16), (-16, 10), (-13, 5), (-7, 2), (0, 1), (7, 2),
                  (11, 5), (11, 10), (7, 12), (2, 10), (-3, 11), (-7, 14),
                  (-9, 18), (-12, 18), (-14, 16)], 168),
        fill(162, [(-13, 13), (-15, 11), (-14, 9), (-17, 8), (-15, 6),
                   (-18, 5), (-16, 2), (-13, 0), (-9, -3), (-4, -5),
                   (1, -6), (7, -4), (12, -1), (13, 3), (10, 7), (6, 6),
                   (2, 8), (-2, 7), (-5, 10), (-8, 8), (-10, 12), (-12, 10)]),
        fill(70, [(-15, 15), (-13, 10), (-10, 12), (-9, 16), (-11, 18), (-14, 17)], 160),
        fill(116, [(-14, 9), (-15, 4), (-11, 0), (-6, -3), (-5, 8), (-9, 7)]),
        fill(204, [(-4, 6), (-3, -5), (4, -4), (11, 0), (9, 5), (3, 3)]),
    ], [stroke(246, [(-12, 0), (-7, -4), (-1, -5), (6, -3), (10, 0)], 1),
        stroke(232, [(-14, 4), (-8, 0), (-2, -1), (5, 1), (8, 3)], 1),
        stroke(216, [(-13, 7), (-8, 4), (-3, 3), (3, 4), (6, 5)], 1)]),
    "south-west": pose([
        fill(92, [(-15, 18), (-16, 11), (-13, 5), (-7, 2), (0, 1), (7, 2),
                  (13, 5), (14, 11), (12, 16), (8, 18), (4, 17), (0, 19),
                  (-4, 17), (-9, 18), (-12, 17)], 168),
        fill(162, [(-12, 13), (-14, 10), (-13, 6), (-15, 5), (-13, 3),
                   (-14, 1), (-11, 0), (-6, -5), (1, -6), (8, -4),
                   (13, -1), (15, 2), (13, 6), (10, 9), (6, 8), (3, 11),
                   (0, 9), (-3, 12), (-6, 9), (-9, 13)]),
        fill(72, [(-14, 15), (-12, 11), (-9, 13), (-8, 17), (-10, 19), (-13, 17)], 160),
        fill(118, [(-11, 10), (-11, 1), (-6, -3), (-2, 10), (-5, 8)]),
        fill(206, [(1, 9), (2, -5), (8, -3), (14, 1), (12, 7), (7, 5)]),
    ], [stroke(242, [(-7, -1), (-1, -5), (6, -3), (12, 0)], 1),
        stroke(228, [(-10, 4), (-4, 0), (3, 0), (9, 2)], 1),
        stroke(212, [(-9, 8), (-4, 4), (2, 3), (7, 5)], 1)]),
    "south": pose([
        fill(92, [(-14, 18), (-15, 11), (-12, 5), (-6, 2), (0, 1), (6, 2),
                  (12, 5), (14, 11), (12, 16), (8, 18), (4, 17), (0, 19),
                  (-4, 17), (-9, 18), (-12, 17)], 168),
        fill(162, [(-11, 13), (-13, 10), (-12, 6), (-14, 5), (-12, 3),
                   (-13, 1), (-10, 0), (-5, -5), (2, -6), (9, -4),
                   (14, -1), (16, 2), (14, 6), (11, 9), (7, 8), (4, 11),
                   (1, 9), (-2, 12), (-5, 9), (-8, 13)]),
        fill(72, [(-13, 15), (-11, 11), (-8, 13), (-7, 17), (-9, 19), (-12, 17)], 160),
        fill(118, [(-10, 10), (-10, 1), (-5, -3), (-1, 10), (-4, 8)]),
        fill(206, [(2, 9), (3, -5), (9, -3), (15, 1), (13, 7), (8, 5)]),
    ], [stroke(242, [(-6, -1), (0, -5), (7, -3), (13, 0)], 1),
        stroke(228, [(-9, 4), (-3, 0), (4, 0), (10, 2)], 1),
        stroke(212, [(-8, 8), (-3, 4), (3, 3), (8, 5)], 1)]),
}
SLICK_BACK["combat-west"] = SLICK_BACK["west"]


TOPKNOT = {
    "north": pose([
        fill(150, [(-14, 15), (-15, 9), (-13, 3), (-8, 0), (-2, -3), (5, -2),
                   (10, 0), (14, 4), (15, 9), (14, 15), (9, 12), (5, 9),
                   (0, 10), (-5, 9), (-10, 12)]),
        fill(92, [(-14, 14), (-14, 8), (-10, 2), (-10, 12), (-11, 16)]),
        fill(204, [(0, 9), (1, -2), (5, -1), (10, 2), (9, 8), (5, 6)]),
        # A six-pixel tied stem makes the elevated bun read as attached hair,
        # while the broad rounded knot survives native-size viewing.
        fill(72, [(-6, 5), (-6, -2), (6, -2), (6, 5)]),
        fill(180, [(-9, -1), (-10, -6), (-7, -11), (-2, -13), (4, -12),
                   (8, -8), (9, -3), (6, 1), (1, 3), (-5, 2)]),
        fill(104, [(-9, -2), (-9, -6), (-6, -10), (-4, 2), (-7, 1)]),
        fill(218, [(0, -11), (4, -10), (7, -7), (7, -4), (2, -5)]),
    ], [stroke(238, [(-5, -9), (-1, -12), (4, -10)], 1),
        stroke(228, [(-7, 2), (-2, -1), (4, 0), (9, 4)], 1),
        stroke(112, [(-10, 12), (-10, 16)], 1, 192)]),
    "north-west": pose([
        fill(148, [(-15, 16), (-16, 9), (-14, 3), (-9, -1), (-2, -3), (5, -2),
                   (11, 1), (14, 5), (15, 9), (12, 15), (8, 12), (4, 9),
                   (-1, 11), (-6, 11), (-10, 16)]),
        fill(90, [(-15, 15), (-15, 7), (-11, 2), (-10, 13), (-11, 17)]),
        fill(204, [(-1, 10), (0, -2), (6, -1), (11, 2), (10, 9), (6, 6)]),
        fill(72, [(-18, 8), (-16, -1), (-7, -2), (-5, 5), (-9, 9)]),
        fill(178, [(-24, -1), (-24, -6), (-19, -11), (-13, -12),
                   (-8, -9), (-6, -5), (-8, 0), (-12, 4), (-17, 3), (-21, 1)]),
        fill(102, [(-23, -2), (-22, -6), (-18, -10), (-15, 3), (-19, 2)]),
        fill(216, [(-14, -10), (-9, -8), (-7, -5), (-10, -2), (-14, -4)]),
    ], [stroke(238, [(-19, -8), (-15, -11), (-10, -8)], 1),
        stroke(226, [(-7, 1), (-2, -2), (4, -1), (10, 4)], 1),
        stroke(214, [(-9, 5), (-4, 2), (2, 2), (7, 5)], 1)]),
    "west": pose([
        fill(146, [(-15, 15), (-16, 9), (-14, 3), (-9, -1), (-2, -3), (5, -2),
                   (11, 1), (14, 5), (14, 10), (10, 13), (5, 10), (0, 9),
                   (-6, 11), (-10, 15)]),
        fill(88, [(-15, 14), (-15, 7), (-11, 2), (-10, 12), (-11, 16)]),
        fill(202, [(-2, 9), (-1, -2), (5, -1), (10, 2), (9, 8), (5, 5)]),
        # Rear-positioned tied stem and a large elevated knot.  The stem keeps
        # x<=-18 pixels through y=7, guaranteeing visible attachment in profile.
        fill(70, [(-18, 8), (-16, -1), (-7, -2), (-5, 5), (-9, 9)]),
        fill(176, [(-24, -1), (-24, -6), (-19, -11), (-13, -12),
                   (-8, -9), (-6, -5), (-8, 0), (-12, 4), (-17, 3), (-21, 1)]),
        fill(100, [(-23, -2), (-22, -6), (-18, -10), (-15, 3), (-19, 2)]),
        fill(214, [(-14, -10), (-9, -8), (-7, -5), (-10, -2), (-14, -4)]),
    ], [stroke(236, [(-19, -8), (-15, -11), (-10, -8)], 1),
        stroke(224, [(-8, 1), (-3, -2), (3, -1), (9, 4)], 1),
        stroke(212, [(-10, 5), (-5, 2), (1, 2), (6, 5)], 1)]),
    "south-west": pose([
        fill(146, [(-14, 17), (-15, 10), (-13, 3), (-8, -1), (-1, -3), (6, -2),
                   (11, 1), (14, 5), (15, 10), (12, 17), (7, 19), (2, 18),
                   (-3, 19), (-8, 18)]),
        fill(90, [(-14, 16), (-14, 8), (-10, 2), (-9, 14), (-9, 18)]),
        fill(202, [(1, 15), (1, -2), (6, -1), (11, 2), (10, 13), (6, 10)]),
        fill(70, [(-15, 8), (-14, -1), (-5, -2), (-3, 5), (-8, 9)]),
        fill(176, [(-20, -1), (-20, -6), (-16, -11), (-11, -12),
                   (-6, -9), (-4, -5), (-6, 0), (-10, 4), (-15, 3), (-19, 1)]),
        fill(100, [(-20, -2), (-19, -6), (-15, -10), (-13, 3), (-17, 2)]),
        fill(214, [(-12, -10), (-7, -8), (-5, -5), (-8, -2), (-12, -4)]),
    ], [stroke(236, [(-17, -8), (-13, -11), (-8, -8)], 1),
        stroke(224, [(-6, 1), (-1, -2), (5, -1), (11, 4)], 1),
        stroke(212, [(-8, 6), (-3, 3), (3, 3), (8, 7)], 1)]),
    "south": pose([
        fill(146, [(-13, 17), (-14, 10), (-12, 3), (-7, -1), (0, -3), (7, -1),
                   (12, 3), (14, 10), (13, 17), (8, 19), (4, 17), (0, 18),
                   (-4, 17), (-8, 19)]),
        fill(90, [(-13, 16), (-13, 8), (-9, 2), (-8, 15), (-8, 19)]),
        fill(202, [(1, 15), (1, -2), (6, -1), (11, 2), (10, 13), (6, 10)]),
        fill(70, [(-6, 5), (-6, -2), (6, -2), (6, 5)]),
        fill(180, [(-9, -1), (-10, -6), (-7, -11), (-2, -13), (4, -12),
                   (8, -8), (9, -3), (6, 1), (1, 3), (-5, 2)]),
        fill(104, [(-9, -2), (-9, -6), (-6, -10), (-4, 2), (-7, 1)]),
        fill(218, [(0, -11), (4, -10), (7, -7), (7, -4), (2, -5)]),
    ], [stroke(238, [(-5, -9), (-1, -12), (4, -10)], 1),
        stroke(224, [(-5, 1), (0, -2), (6, 0), (11, 5)], 1),
        stroke(212, [(-8, 6), (-3, 3), (3, 3), (8, 7)], 1)]),
}
TOPKNOT["combat-west"] = TOPKNOT["west"]


STYLES: Mapping[str, StyleArt] = {
    "rare_spikes": StyleArt(
        "Rare High Spikes",
        "Approved dramatic crown with forehead locks and sideburns; frozen Gate-1 pixels.",
        {master: rare_pose(master) for master in MASTERS},
    ),
    "faded_buzzcut": StyleArt(
        "Faded Buzzcut",
        "Close scalp cap, clean hairline, and two discrete semi-transparent temple/nape fade bands.",
        BUZZCUT,
    ),
    "mohawk": StyleArt(
        "Mohawk",
        "Narrow coherent ridge over visible shaved-side stubble; strong profile and rear stripe.",
        MOHAWK,
    ),
    "textured_crop": StyleArt(
        "Textured Crop",
        "Compact broken crown with several readable fringe teeth and restrained sideburns.",
        TEXTURED_CROP,
    ),
    "slick_back_undercut": StyleArt(
        "Slick-back Undercut",
        "Low swept top mass with directional highlight bands over a connected faded undercut.",
        SLICK_BACK,
    ),
    "high_topknot": StyleArt(
        "High Topknot",
        "Close swept cap with a compact tied knot high at the rear crown in every direction.",
        TOPKNOT,
    ),
}


def translated(points: Iterable[Point], crown: Point) -> list[Point]:
    return [(crown[0] + x, crown[1] + y) for x, y in points]


def _rgba(shade: int, alpha: int) -> tuple[int, int, int, int]:
    if not 0 <= shade <= 255 or not 1 <= alpha <= 255:
        raise ValueError("hair shades must be 0..255 and nonzero alpha must be 1..255")
    return shade, shade, shade, alpha


def _clip(image: Image.Image, allowed: Image.Image) -> Image.Image:
    output = Image.new("RGBA", image.size, (0, 0, 0, 0))
    output.putdata([
        value if permitted else (0, 0, 0, 0)
        for value, permitted in zip(image.getdata(), allowed.convert("1").getdata())
    ])
    return output


def _exclude(image: Image.Image, forbidden: Image.Image) -> Image.Image:
    output = Image.new("RGBA", image.size, (0, 0, 0, 0))
    output.putdata([
        (0, 0, 0, 0) if blocked else value
        for value, blocked in zip(image.getdata(), forbidden.convert("1").getdata())
    ])
    return output


def rasterize(style_id: str, master: str) -> Image.Image:
    if style_id not in STYLES:
        raise KeyError(f"unknown hairstyle {style_id}")
    if master not in MASTERS:
        raise KeyError(f"unknown master {master}")
    template = load_v2_template()
    spec = next(frame for frame in template.frames if frame.master == master)
    art = STYLES[style_id].poses[master]
    image = Image.new("RGBA", spec.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    silhouette = Image.new("1", spec.size, 0)
    silhouette_draw = ImageDraw.Draw(silhouette)
    clip_fills = art.fills if art.clip_fill_count is None else art.fills[:art.clip_fill_count]
    for command in art.fills:
        points = translated(command.points, spec.crown)
        draw.polygon(points, fill=_rgba(command.shade, command.alpha))
        if command in clip_fills:
            silhouette_draw.polygon(points, fill=1)
    for command in art.strokes:
        draw.line(
            translated(command.points, spec.crown),
            fill=_rgba(command.shade, command.alpha),
            width=command.width,
        )
    for command in art.pixels:
        x, y = spec.crown[0] + command.point[0], spec.crown[1] + command.point[1]
        if 0 <= x < image.width and 0 <= y < image.height:
            image.putpixel((x, y), _rgba(command.shade, command.alpha))

    # Detail strokes cannot grow beyond the authored fill union, and the fill
    # union cannot grow beyond the digest-locked canonical hair envelope.
    image = _clip(image, silhouette)
    allowed = template.pose_profiles[master].masks["hair_allowed"]
    if allowed is None:
        raise ValueError(f"canonical pose {master} lacks hair_allowed")
    image = _clip(image, allowed)
    # New art treats protected anatomy as a hard eraser.  Rare spikes stay on
    # their approved digest, including two conservative-mask edge pixels in the
    # profile masters.
    protected = template.pose_profiles[master].masks["protected_anatomy"]
    if style_id != "rare_spikes" and protected is not None:
        image = _exclude(image, protected)
    return image


def _points(image: Image.Image) -> set[Point]:
    alpha = image.getchannel("A")
    return {
        (x, y)
        for y in range(alpha.height)
        for x in range(alpha.width)
        if alpha.getpixel((x, y)) > 0
    }


def _components(points: set[Point]) -> list[set[Point]]:
    remaining = set(points)
    result: list[set[Point]] = []
    while remaining:
        seed = remaining.pop()
        component = {seed}
        pending = [seed]
        while pending:
            x, y = pending.pop()
            for neighbour in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.add(neighbour)
                    pending.append(neighbour)
        result.append(component)
    return sorted(result, key=len, reverse=True)


def validate_master(style_id: str, master: str, image: Image.Image) -> dict[str, object]:
    template = load_v2_template()
    spec = next(frame for frame in template.frames if frame.master == master)
    profile = template.pose_profiles[master]
    occupied = _points(image)
    if not occupied:
        raise ValueError(f"{style_id}.{master} is empty")
    components = _components(occupied)
    if len(components) != 1:
        raise ValueError(f"{style_id}.{master} has {len(components)} four-connected components")
    attachment = profile.masks["scalp_attachment"]
    attachment_pixels = 0 if attachment is None else sum(bool(attachment.getpixel(point)) for point in occupied)
    if attachment_pixels <= 0:
        raise ValueError(f"{style_id}.{master} misses scalp_attachment")
    protected = profile.masks["protected_anatomy"]
    protected_pixels = 0 if protected is None else sum(bool(protected.getpixel(point)) for point in occupied)
    # The approved rare-spikes west master has two digest-frozen sideburn-edge
    # pixels inside the conservative protected mask.  Preserve that art exactly;
    # every new style must remain completely outside protected anatomy.
    if protected_pixels and style_id != "rare_spikes":
        raise ValueError(f"{style_id}.{master} covers {protected_pixels} protected-anatomy pixels")
    allowed = profile.masks["hair_allowed"]
    outside = len(occupied) if allowed is None else sum(not bool(allowed.getpixel(point)) for point in occupied)
    if outside:
        raise ValueError(f"{style_id}.{master} has {outside} pixels outside hair_allowed")
    values = sorted({red for red, green, blue, alpha in image.getdata() if alpha})
    alphas = sorted({alpha for red, green, blue, alpha in image.getdata() if alpha})
    if any(red != green or green != blue for red, green, blue, alpha in image.getdata() if alpha):
        raise ValueError(f"{style_id}.{master} contains non-neutral tint pixels")
    if len(values) < 3:
        raise ValueError(f"{style_id}.{master} needs at least three grayscale detail values")
    box = image.getchannel("A").getbbox()
    assert box is not None
    relative_box = [
        box[0] - spec.crown[0], box[1] - spec.crown[1],
        box[2] - 1 - spec.crown[0], box[3] - 1 - spec.crown[1],
    ]
    rgba_sha256 = hashlib.sha256(image.tobytes()).hexdigest()
    if style_id == "rare_spikes" and rgba_sha256 != RARE_RGBA_SHA256[master]:
        raise ValueError(
            f"approved rare-spikes pixels changed for {master}: "
            f"{rgba_sha256} != {RARE_RGBA_SHA256[master]}"
        )
    return {
        "rgbaSha256": rgba_sha256,
        "occupiedPixels": len(occupied),
        "fourConnectedComponents": len(components),
        "scalpAttachmentPixels": attachment_pixels,
        "protectedAnatomyPixels": protected_pixels,
        "outsideAllowedPixels": outside,
        "relativeAlphaBoundsInclusive": relative_box,
        "grayscaleValues": values,
        "nonzeroAlphaValues": alphas,
    }


def _png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _safe_output(path: Path) -> Path:
    resolved = path.resolve()
    allowed_roots = [
        (REPO_ROOT / "content/appearance/v2/hairstyles").resolve(),
        (REPO_ROOT / "tmp").resolve(),
    ]
    if not any(resolved == root or root in resolved.parents for root in allowed_roots):
        raise ValueError("hairstyle output must remain under content/appearance/v2/hairstyles or tmp")
    return resolved


def generate_collection(output_root: Path) -> dict[str, object]:
    root = _safe_output(output_root)
    template = load_v2_template()
    generator_sha256 = hashlib.sha256(SCRIPT.read_bytes()).hexdigest()
    styles_report = []
    for style_id, style in STYLES.items():
        if set(style.poses) != set(MASTERS):
            raise ValueError(f"{style_id} does not define all six masters")
        style_root = root / style_id
        masters_report: dict[str, object] = {}
        for master in MASTERS:
            image = rasterize(style_id, master)
            validation = validate_master(style_id, master, image)
            path = style_root / "masters" / f"{master}.png"
            _png(path, image)
            masters_report[master] = {
                "path": f"masters/{master}.png",
                "fileSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                **validation,
            }
        manifest = {
            "schema": "voidscape-paperdoll-v2-hairstyle-art/v1",
            "id": style_id,
            "label": style.label,
            "rationale": style.rationale,
            "template": template.key,
            "templateSha256": template.digest,
            "generator": str(SCRIPT.relative_to(REPO_ROOT)),
            "generatorSha256": generator_sha256,
            "source": "explicit-crown-relative-grayscale-pixel-geometry",
            "masters": masters_report,
        }
        manifest_path = style_root / "art.json"
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        styles_report.append({
            "id": style_id,
            "manifest": str(manifest_path.relative_to(root)),
            "manifestSha256": hashlib.sha256(manifest_path.read_bytes()).hexdigest(),
        })
    return {
        "valid": True,
        "output": str(root),
        "template": template.key,
        "templateSha256": template.digest,
        "styleCount": len(styles_report),
        "masterCount": len(styles_report) * len(MASTERS),
        "styles": styles_report,
    }


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Generate content-owned Paperdoll V2 hairstyle masters")
    parser.add_argument(
        "--out",
        type=Path,
        default=REPO_ROOT / "content/appearance/v2/hairstyles",
    )
    args = parser.parse_args(argv)
    try:
        report = generate_collection(args.out)
    except (OSError, ValueError, KeyError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
