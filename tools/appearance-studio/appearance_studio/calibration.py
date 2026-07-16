#!/usr/bin/env python3
"""Rebuild R1 pose masks and render review-only calibration evidence."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import yaml
from PIL import Image, ImageChops, ImageDraw, ImageFilter

from .template import load_template


REPO = Path(__file__).resolve().parents[3]
TEMPLATE = REPO / "content/appearance/templates/rsc-player-v1/template.yaml"
MASK_ROOT = REPO / "content/appearance/templates/rsc-player-v1/masks"
REF_ROOT = REPO / "content/appearance/templates/rsc-player-v1/references"

POSES = {
    "north": {"offset": 0, "canvas": (64, 102), "crown": (32, 9), "nape": None},
    "north-west": {"offset": 3, "canvas": (64, 102), "crown": (30, 9), "nape": (18, 22, 27, 32)},
    "west": {"offset": 6, "canvas": (64, 102), "crown": (31, 9), "nape": (18, 21, 27, 33)},
    "south-west": {"offset": 9, "canvas": (64, 102), "crown": (30, 9), "nape": (20, 20, 38, 33)},
    "south": {"offset": 12, "canvas": (64, 102), "crown": (32, 9), "nape": (22, 20, 42, 34)},
    "combat-west": {"offset": 15, "canvas": (84, 102), "crown": (16, 9), "nape": (3, 21, 12, 33)},
}

LIP_RECTS = {
    "north": (29, 22, 35, 25),
    "north-west": (32, 22, 38, 25),
    "west": (33, 20, 39, 23),
    "combat-west": (18, 20, 24, 23),
}

LANDMARK_COLOURS = {
    "forehead": "#f9e2af", "face_center": "#89dceb", "nose_tip": "#fab387",
    "upper_lip": "#f38ba8", "chin": "#cba6f7", "ear": "#eba0ac",
    "occiput": "#94e2d5", "neck": "#a6e3a1", "nape": "#f5c2e7",
}


def file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sprite_canvas(reference: str, offset: int, size: tuple[int, int]) -> Image.Image:
    path = REF_ROOT / reference / f"frame_{offset:02d}.png"
    sprite = Image.open(path).convert("RGBA")
    sidecar = json.loads(path.with_suffix(".png.json").read_text())
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(sprite, (sidecar["xShift"], sidecar["yShift"]))
    return canvas


def alpha_mask(image: Image.Image) -> Image.Image:
    return image.getchannel("A").point(lambda value: 255 if value else 0, mode="1")


def grayscale_mask(image: Image.Image) -> Image.Image:
    result = Image.new("1", image.size)
    pixels = result.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            pixels[x, y] = bool(a and r == g == b)
    return result


def detail_mask(image: Image.Image) -> Image.Image:
    result = Image.new("1", image.size)
    pixels = result.load()
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = image.getpixel((x, y))
            pixels[x, y] = bool(a and not (r == 255 and g == b))
    return result


def dilate(mask: Image.Image, radius: int) -> Image.Image:
    return mask.convert("L").filter(ImageFilter.MaxFilter(radius * 2 + 1)).point(
        lambda value: 255 if value else 0, mode="1"
    )


def invert_mask(mask: Image.Image) -> Image.Image:
    return mask.convert("L").point(lambda value: 0 if value else 255, mode="1")


def rect_mask(size: tuple[int, int], rect: tuple[int, int, int, int] | None) -> Image.Image:
    mask = Image.new("1", size)
    if rect is not None:
        ImageDraw.Draw(mask).rectangle((rect[0], rect[1], rect[2] - 1, rect[3] - 1), fill=1)
    return mask


def derive_masks(master: str) -> dict[str, Image.Image | None]:
    spec = POSES[master]
    size, offset, crown = spec["canvas"], spec["offset"], spec["crown"]
    head4 = sprite_canvas("head4", offset, size)
    head1 = sprite_canvas("head1", offset, size)
    fhead1 = sprite_canvas("fhead1", offset, size)
    head3 = sprite_canvas("head3", offset, size)
    short_hair = grayscale_mask(head1)
    long_hair = grayscale_mask(fhead1)
    authentic_hair = ImageChops.lighter(short_hair, long_hair)
    bald = alpha_mask(head4)

    # Authentic hair plus a four-pixel upper-scalp design halo and the explicit
    # nape corridor. This admits longer authentic silhouettes without opening
    # the whole face/body canvas.
    upper_scalp = Image.new("1", size)
    upper_scalp.paste(bald, (0, 0))
    clip = Image.new("1", size)
    ImageDraw.Draw(clip).rectangle((0, 0, size[0] - 1, crown[1] + 12), fill=1)
    scalp_halo = ImageChops.logical_and(dilate(upper_scalp, 4), clip)
    hair_allowed = ImageChops.lighter(dilate(authentic_hair, 2), scalp_halo)
    if spec["nape"]:
        hair_allowed = ImageChops.lighter(hair_allowed, rect_mask(size, spec["nape"]))

    scalp_attachment = ImageChops.logical_and(authentic_hair, dilate(bald, 1))
    scalp_clip = Image.new("1", size)
    ImageDraw.Draw(scalp_clip).rectangle((0, crown[1], size[0] - 1, crown[1] + 9), fill=1)
    scalp_attachment = ImageChops.logical_and(scalp_attachment, scalp_clip)

    facial_applicable = master not in {"south-west", "south"}
    facial_allowed = None
    upper_lip = None
    if facial_applicable:
        beard = grayscale_mask(head3)
        lower_clip = Image.new("1", size)
        lip_y = LIP_RECTS[master][1]
        ImageDraw.Draw(lower_clip).rectangle((0, lip_y - 2, size[0] - 1, min(size[1] - 1, lip_y + 25)), fill=1)
        facial_allowed = ImageChops.logical_and(dilate(beard, 2), lower_clip)
        upper_lip = rect_mask(size, LIP_RECTS[master])

    face_clearance = None
    if facial_applicable:
        face_clip = Image.new("1", size)
        ImageDraw.Draw(face_clip).rectangle(
            (0, crown[1] + 5, size[0] - 1, crown[1] + (18 if master in {"north", "north-west"} else 15)), fill=1
        )
        face_clearance = ImageChops.logical_and(bald, face_clip)
        face_clearance = ImageChops.logical_and(face_clearance, invert_mask(dilate(authentic_hair, 1)))

    # Facial detail pixels are protected exactly. Dilation would consume the
    # adjacent upper-lip attachment band and make any valid mustache impossible.
    protected = detail_mask(head4)
    # Preserve the neck junction even in views without visible facial detail.
    neck = rect_mask(size, (crown[0] - 3, crown[1] + 16, crown[0] + 4, crown[1] + 20))
    protected = ImageChops.lighter(protected, ImageChops.logical_and(neck, bald))

    # Keep authored hair off the visible neck column while leaving the
    # rear-side scalp-to-nape route open for long styles.  The locked neck
    # landmark supplies the center; clipping to hair_allowed makes the role
    # describe only pixels an author could otherwise occupy.
    neck_relative = {
        "north": (0, 17), "north-west": (1, 17), "west": (-1, 14),
        "south-west": (0, 14), "south": (0, 15), "combat-west": (-1, 14),
    }[master]
    neck_x, neck_y = crown[0] + neck_relative[0], crown[1] + neck_relative[1]
    neck_clearance = ImageChops.logical_and(
        hair_allowed,
        rect_mask(size, (neck_x - 3, neck_y - 1, neck_x + 4, neck_y + 8)),
    )

    return {
        "hair_allowed": hair_allowed,
        "facial_hair_allowed": facial_allowed,
        "scalp_attachment": scalp_attachment,
        "upper_lip_attachment": upper_lip,
        "nape_tail": None if spec["nape"] is None else rect_mask(size, spec["nape"]),
        "face_clearance": face_clearance,
        "neck_clearance": neck_clearance,
        "protected_anatomy": protected,
    }


def masks_connect(left: Image.Image, right: Image.Image, allowed: Image.Image) -> bool:
    """Return whether any 8-connected route joins the two masks inside allowed."""
    starts = {
        (x, y) for y in range(allowed.height) for x in range(allowed.width)
        if left.getpixel((x, y)) and allowed.getpixel((x, y))
    }
    targets = {
        (x, y) for y in range(allowed.height) for x in range(allowed.width)
        if right.getpixel((x, y)) and allowed.getpixel((x, y))
    }
    pending = list(starts)
    seen = set(starts)
    while pending:
        x, y = pending.pop()
        if (x, y) in targets:
            return True
        for point in (
            (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
            (x - 1, y),                     (x + 1, y),
            (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
        ):
            px, py = point
            if (point not in seen and 0 <= px < allowed.width and 0 <= py < allowed.height
                    and allowed.getpixel(point)):
                seen.add(point)
                pending.append(point)
    return False


def write_masks() -> None:
    for master in POSES:
        masks = derive_masks(master)
        lip = masks["upper_lip_attachment"]
        if lip is not None:
            usable = ImageChops.logical_and(
                ImageChops.logical_and(lip, masks["facial_hair_allowed"]),
                invert_mask(masks["protected_anatomy"]),
            )
            if not usable.getbbox():
                raise ValueError(f"{master} has no usable upper-lip attachment pixels")
        clearance = masks["face_clearance"]
        if clearance is not None:
            spec = POSES[master]
            authentic_hair = ImageChops.lighter(
                grayscale_mask(sprite_canvas("head1", spec["offset"], spec["canvas"])),
                grayscale_mask(sprite_canvas("fhead1", spec["offset"], spec["canvas"])),
            )
            if ImageChops.logical_and(clearance, authentic_hair).getbbox():
                raise ValueError(f"{master} face clearance rejects authentic hair")
        nape = masks["nape_tail"]
        if nape is not None:
            allowed = ImageChops.logical_and(
                masks["hair_allowed"], invert_mask(masks["neck_clearance"]),
            )
            if not masks_connect(masks["scalp_attachment"], nape, allowed):
                raise ValueError(f"{master} neck clearance severs the scalp-to-nape corridor")
        for role, mask in masks.items():
            if mask is None:
                continue
            path = MASK_ROOT / master / f"{role}.png"
            path.parent.mkdir(parents=True, exist_ok=True)
            mask.convert("1").save(path, format="PNG", optimize=False, compress_level=9)


def overlay_mask(mask: Image.Image, colour: tuple[int, int, int, int]) -> Image.Image:
    layer = Image.new("RGBA", mask.size, colour)
    layer.putalpha(mask.convert("L").point(lambda value: colour[3] if value else 0))
    return layer


def render_tile(image: Image.Image, title: str, scale: int = 4) -> Image.Image:
    label = 20
    tile = Image.new("RGBA", (image.width * scale, image.height * scale + label), (20, 24, 32, 255))
    ImageDraw.Draw(tile).text((4, 4), title, fill="white")
    tile.alpha_composite(image.resize((image.width * scale, image.height * scale), Image.Resampling.NEAREST), (0, label))
    return tile


def calibration_panel(payload: dict, master: str) -> Image.Image:
    spec = POSES[master]
    size, offset, crown = spec["canvas"], spec["offset"], spec["crown"]
    profile = payload["pose_profiles"][master]
    masks = derive_masks(master)
    refs = [sprite_canvas(name, offset, size) for name in ("head4", "head1", "fhead1", "head3")]
    landmarked = refs[0].copy()
    draw = ImageDraw.Draw(landmarked)
    for name, point in profile["landmarks"].items():
        if point is None:
            continue
        x, y = crown[0] + point[0], crown[1] + point[1]
        colour = LANDMARK_COLOURS[name]
        draw.line((x - 2, y, x + 2, y), fill=colour, width=1)
        draw.line((x, y - 2, x, y + 2), fill=colour, width=1)

    player = Image.new("RGBA", size)
    for name in ("legs", "body", "head4"):
        player.alpha_composite(sprite_canvas(name, offset, size))
    tiles = [
        render_tile(landmarked, "head4 + landmarks"),
        render_tile(refs[1], "head1 short hair"),
        render_tile(refs[2], "fhead1 long hair"),
        render_tile(refs[3], "head3 long beard"),
        render_tile(player, "geometry composite (not renderer)"),
    ]
    colours = [(80, 180, 255, 140), (255, 130, 100, 140), (120, 255, 150, 150),
               (255, 100, 180, 170), (180, 120, 255, 150), (255, 220, 90, 140),
               (100, 235, 220, 170), (255, 70, 70, 170)]
    for (role, mask), colour in zip(masks.items(), colours):
        canvas = refs[0].copy()
        if mask is not None:
            canvas.alpha_composite(overlay_mask(mask, colour))
        tiles.append(render_tile(canvas, f"{role}: {'N/A' if mask is None else 'normative'}"))

    cell_w = max(tile.width for tile in tiles)
    cell_h = max(tile.height for tile in tiles)
    rows = (len(tiles) + 3) // 4
    panel = Image.new("RGBA", (cell_w * 4, cell_h * rows + 30), (12, 15, 22, 255))
    ImageDraw.Draw(panel).text((6, 7), f"{master} / {profile['visual_pose']} / logical {size[0]}x{size[1]}", fill="white")
    for index, tile in enumerate(tiles):
        panel.alpha_composite(tile, ((index % 4) * cell_w, 30 + (index // 4) * cell_h))
    return panel


def render_evidence(out: Path) -> dict:
    out = out.resolve()
    try:
        out.relative_to((REPO / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("calibration evidence must remain under repository tmp/") from exc
    load_template(TEMPLATE)
    payload = yaml.safe_load(TEMPLATE.read_text())
    for master in POSES:
        derived = derive_masks(master)
        for role, expected in derived.items():
            document = payload["pose_profiles"][master]["masks"][role]
            if expected is None:
                if document is not None:
                    raise ValueError(f"{master}.{role} must be null")
                continue
            if document is None:
                raise ValueError(f"{master}.{role} is missing")
            path = REPO / document["path"]
            if file_sha(path) != document["sha256"]:
                raise ValueError(f"{master}.{role} digest mismatch")
            checked = Image.open(path)
            if checked.mode != "1" or checked.size != expected.size:
                raise ValueError(f"{master}.{role} is not a mode-1 logical-canvas mask")
            if ImageChops.difference(checked.convert("L"), expected.convert("L")).getbbox():
                raise ValueError(f"{master}.{role} differs from deterministic derivation")
    out.mkdir(parents=True, exist_ok=True)
    panels = []
    for master in POSES:
        panel = calibration_panel(payload, master)
        path = out / f"{master}.png"
        panel.save(path)
        panels.append((master, panel, path))
    width = max(panel.width for _, panel, _ in panels) * 2
    height = max(panel.height for _, panel, _ in panels) * 3
    sheet = Image.new("RGBA", (width, height), (8, 10, 15, 255))
    for index, (_, panel, _) in enumerate(panels):
        sheet.alpha_composite(panel, ((index % 2) * panel.width, (index // 2) * panel.height))
    sheet_path = out / "calibration-sheet.png"
    sheet.save(sheet_path)
    usable_lip_pixels = {}
    authentic_hair_face_clearance_overlap = {}
    scalp_nape_corridor_connected = {}
    for master in POSES:
        masks = derive_masks(master)
        lip = masks["upper_lip_attachment"]
        if lip is None:
            usable_lip_pixels[master] = None
        else:
            usable = ImageChops.logical_and(
                ImageChops.logical_and(lip, masks["facial_hair_allowed"]),
                invert_mask(masks["protected_anatomy"]),
            )
            usable_lip_pixels[master] = sum(bool(value) for value in usable.getdata())
        clearance = masks["face_clearance"]
        if clearance is None:
            authentic_hair_face_clearance_overlap[master] = None
        else:
            spec = POSES[master]
            authentic_hair = ImageChops.lighter(
                grayscale_mask(sprite_canvas("head1", spec["offset"], spec["canvas"])),
                grayscale_mask(sprite_canvas("fhead1", spec["offset"], spec["canvas"])),
            )
            overlap = ImageChops.logical_and(clearance, authentic_hair)
            authentic_hair_face_clearance_overlap[master] = sum(bool(value) for value in overlap.getdata())
        nape = masks["nape_tail"]
        if nape is None:
            scalp_nape_corridor_connected[master] = None
        else:
            allowed = ImageChops.logical_and(
                masks["hair_allowed"], invert_mask(masks["neck_clearance"]),
            )
            scalp_nape_corridor_connected[master] = masks_connect(
                masks["scalp_attachment"], nape, allowed,
            )
    report = {
        "schema": "voidscape-appearance-calibration-evidence/v1",
        "shipping": False,
        "template": str(TEMPLATE.relative_to(REPO)),
        "template_sha256": file_sha(TEMPLATE),
        "sheet": str(sheet_path),
        "sheet_sha256": file_sha(sheet_path),
        "authentic_hair_face_clearance_overlap": authentic_hair_face_clearance_overlap,
        "scalp_nape_corridor_connected": scalp_nape_corridor_connected,
        "usable_upper_lip_pixels": usable_lip_pixels,
        "panels": {name: {"path": str(path), "sha256": file_sha(path)} for name, _, path in panels},
    }
    (out / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return report


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write-masks", action="store_true")
    parser.add_argument("--out", type=Path, default=REPO / "tmp/appearance-studio/r1/calibration")
    args = parser.parse_args()
    if args.write_masks:
        write_masks()
    print(json.dumps(render_evidence(args.out.resolve()), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
