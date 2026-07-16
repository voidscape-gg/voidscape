from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image, ImageDraw


SCRIPT_ROOT = Path(__file__).resolve().parent
REPO = SCRIPT_ROOT.parents[2]
if len(sys.argv) != 2:
    raise SystemExit("usage: paperdoll_v2_proof.py <tmp-workspace>")
ROOT = Path(sys.argv[1]).resolve()
try:
    relative_root = ROOT.relative_to((REPO / "tmp").resolve())
except ValueError as exc:
    raise SystemExit("proof art may only be written beneath repository tmp/") from exc
if not relative_root.parts or not (ROOT / "workspace.json").is_file():
    raise SystemExit("target must be an initialized Paperdoll V2 tmp workspace")

sys.path.insert(0, str(REPO / "tools/appearance-studio"))
from appearance_studio.v2_template import MASTERS, load_v2_template  # noqa: E402
from appearance_studio.v2_workspace import semantic_skin_shade  # noqa: E402


# Original six-direction pixel construction. This is deliberately inspired by
# high-energy anime silhouettes rather than copied from a character sheet. The
# front/rear/profile shapes share spike roots so runtime mirrors stay coherent.
HAIR_SILHOUETTES = {
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

HAIR_PLANES = {
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

HAIR_STRANDS = {
    "north": [
        [(-17, 9), (-11, 3), (-10, 13)], [(-8, 4), (-4, -9), (-2, 8)],
        [(1, 3), (4, -13), (6, 7)], [(9, 3), (15, -8), (13, 10)],
        [(-13, 17), (-7, 13), (0, 16), (7, 13), (14, 17)],
        [(3, 8), (0, 15)],
    ],
    "north-west": [
        [(-18, 9), (-11, 1), (-10, 14)], [(-8, 3), (-3, -11), (0, 9)],
        [(2, 3), (8, -11), (9, 10)], [(10, 5), (18, -5), (15, 13)],
        [(-13, 18), (-6, 13), (2, 17), (10, 13), (16, 18)],
        [(10, 9), (14, 17)],
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


def rgba_canvas(size: tuple[int, int]) -> Image.Image:
    return Image.new("RGBA", size, (0, 0, 0, 0))


def translated(points, crown):
    return [(crown[0] + x, crown[1] + y) for x, y in points]


def faithful_head(pose: str, size: tuple[int, int]) -> tuple[Image.Image, Image.Image]:
    """Split the untouched nearest-2x bald guide; never redraw the face."""
    with Image.open(ROOT / f"guides/{pose}/base.png") as source:
        guide = source.convert("RGBA")
    if guide.size != size:
        raise ValueError(f"guide size changed for {pose}: {guide.size} != {size}")
    skin = rgba_canvas(size)
    fixed = rgba_canvas(size)
    skin_pixels = []
    fixed_pixels = []
    for red, green, blue, alpha in guide.getdata():
        is_skin = alpha and red == 255 and green == blue
        shade = semantic_skin_shade(green) if is_skin else 0
        skin_pixels.append((shade, shade, shade, alpha) if is_skin else (0, 0, 0, 0))
        fixed_pixels.append((red, green, blue, alpha) if alpha and not is_skin else (0, 0, 0, 0))
    skin.putdata(skin_pixels)
    fixed.putdata(fixed_pixels)
    return skin, fixed


def clip_to_mask(image: Image.Image, path: Path) -> Image.Image:
    with Image.open(path) as source:
        mask = source.convert("L")
    output = rgba_canvas(image.size)
    output.putdata([
        pixel if allowed else (0, 0, 0, 0)
        for pixel, allowed in zip(image.getdata(), mask.getdata())
    ])
    return output


def rare_hair(pose: str, size: tuple[int, int], crown: tuple[int, int]) -> Image.Image:
    shape_pose = "west" if pose == "combat-west" else pose
    image = rgba_canvas(size)
    draw = ImageDraw.Draw(image)
    silhouette = translated(HAIR_SILHOUETTES[shape_pose], crown)
    draw.polygon(silhouette, fill=(184, 184, 184, 255))

    # Four large directional planes give the black tint readable volume. These
    # stay neutral grayscale so every future hair colour uses identical art.
    plane_shades = (102, 136, 218, 160)
    for shade, plane in zip(plane_shades, HAIR_PLANES[shape_pose]):
        draw.polygon(translated(plane, crown), fill=(shade, shade, shade, 255))
    for index, strand in enumerate(HAIR_STRANDS[shape_pose]):
        shade = 252 if index < 2 else 232
        draw.line(translated(strand, crown), fill=(shade, shade, shade, 255), width=2)

    # Small soft-alpha tips prove ARGB preservation without blurring the form.
    for dx, dy in [(-24, 7), (4, -18), (24, 7)]:
        x, y = crown[0] + dx, crown[1] + dy
        if 0 <= x < size[0] and 0 <= y < size[1]:
            image.putpixel((x, y), (220, 220, 220, 176))
    silhouette_mask = Image.new("1", size, 0)
    ImageDraw.Draw(silhouette_mask).polygon(silhouette, fill=1)
    clipped = rgba_canvas(size)
    clipped.putdata([
        pixel if inside else (0, 0, 0, 0)
        for pixel, inside in zip(image.getdata(), silhouette_mask.getdata())
    ])
    return clip_to_mask(clipped, ROOT / f"guides/{pose}/masks/hair_allowed.png")


def save(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def main() -> None:
    template = load_v2_template()
    for pose in MASTERS:
        frame = next(item for item in template.frames if item.master == pose)
        skin, fixed = faithful_head(pose, frame.size)
        hair = rare_hair(pose, frame.size, frame.crown)
        save(ROOT / f"masters/native_head/skin/{pose}.png", skin)
        save(ROOT / f"masters/native_head/fixed/{pose}.png", fixed)
        save(ROOT / f"masters/hair_rare_spikes/hair/{pose}.png", hair)


if __name__ == "__main__":
    main()
