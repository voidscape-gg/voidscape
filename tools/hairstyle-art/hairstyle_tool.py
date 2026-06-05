#!/usr/bin/env python3
"""RSC hairstyle sprite grid helper.

This tool wraps the legacy Authentic_Sprites.orsc format in a repeatable
workflow for AI-assisted hairstyle edits:

1. Extract an existing head animation into an 18-frame grid.
2. Send the grid plus prompt to an image model.
3. Slice the returned grid back into frames.
4. Normalize/validate mask colors.
5. Preview or dry-run pack those frames.
"""
from __future__ import annotations

import argparse
import base64
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
import math
import os
import struct
import sys
import tempfile
from urllib.parse import urlparse
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_ARCHIVE = REPO_ROOT / "Client_Base" / "Cache" / "video" / "Authentic_Sprites.orsc"

HEAD_STYLES = {
    "head1": {"base": 0, "appearance_id": 1, "label": "short hair"},
    "fhead1": {"base": 81, "appearance_id": 4, "label": "long hair"},
    "head2": {"base": 135, "appearance_id": 6, "label": "short hair variant"},
    "head3": {"base": 162, "appearance_id": 7, "label": "long bearded head"},
    "head4": {"base": 189, "appearance_id": 8, "label": "bald head"},
}

BASE_PLAYER_ANIMS = {
    "head": 0,
    "body": 27,
    "legs": 54,
}

PLAYER_CLOTHING_COLOURS = (
    0xFF0000, 0xFF8000, 0xFFE000, 0xA0E000, 0x00E000, 0x008000,
    0x00A080, 0x00B0FF, 0x0080FF, 0x0030F0, 0xE000E0, 0x303030,
    0x604000, 0x805000, 0xFFFFFF,
)
PLAYER_HAIR_COLOURS = (
    0xFFC030, 0xFFA040, 0x805030, 0x604020, 0x303030, 0xFF6020,
    0xFF4000, 0xFFFFFF, 0x00FF00, 0x00FFFF,
)
PLAYER_SKIN_COLOURS = (
    0xECDED0, 0xCCB366, 0xB38C40, 0x997326, 0x906020,
)

ANIM_DIR_LAYER_TO_CHAR_LAYER = (
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4),
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3, 4),
    (11, 3, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4),
    (3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (3, 4, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (4, 3, 2, 9, 7, 1, 6, 10, 8, 11, 0, 5),
    (11, 4, 2, 9, 7, 1, 6, 10, 0, 5, 8, 3),
    (11, 2, 9, 7, 1, 6, 10, 0, 5, 8, 4, 3),
)

FRAME_COUNT = 18
DEFAULT_COLS = 6
GRID_BG = (0, 255, 0, 255)
GRID_LINE = (255, 0, 255, 255)
HAIR_MASK_SHADES = (103, 132, 160)
SKIN_MASK_COLOURS = ((255, 124, 124), (255, 181, 181), (255, 205, 205))
DETAIL_COLOUR = (41, 24, 24)


@dataclass
class SpriteFrame:
    index: int
    image: Image.Image
    sidecar: dict


def rgba_tuple(value: Iterable[int]) -> tuple[int, int, int, int]:
    items = list(value)
    if len(items) == 3:
        items.append(255)
    return tuple(int(max(0, min(255, v))) for v in items[:4])


def parse_colour(text: str) -> tuple[int, int, int]:
    text = text.strip()
    if text.startswith("#"):
        text = text[1:]
    if len(text) != 6:
        raise argparse.ArgumentTypeError("expected hex colour like #ffcc00")
    try:
        return (int(text[0:2], 16), int(text[2:4], 16), int(text[4:6], 16))
    except ValueError as exc:
        raise argparse.ArgumentTypeError("expected hex colour like #ffcc00") from exc


def decode_sprite(data: bytes) -> tuple[Image.Image, dict]:
    if len(data) < 25:
        raise ValueError("sprite too short: header missing")
    width, height, req_shift, x_shift, y_shift, s1, s2 = struct.unpack_from(">iiBiiii", data, 0)
    pixel_count = width * height
    if len(data) < 25 + pixel_count * 4:
        raise ValueError(f"sprite truncated for {width}x{height}")

    src = data[25:25 + pixel_count * 4]
    rgba = bytearray(pixel_count * 4)
    for i in range(0, len(src), 4):
        if src[i] == 0 and src[i + 1] == 0 and src[i + 2] == 0 and src[i + 3] == 0:
            rgba[i] = rgba[i + 1] = rgba[i + 2] = rgba[i + 3] = 0
        else:
            rgba[i] = src[i + 1]
            rgba[i + 1] = src[i + 2]
            rgba[i + 2] = src[i + 3]
            rgba[i + 3] = 255

    sidecar = {
        "width": width,
        "height": height,
        "requiresShift": bool(req_shift),
        "xShift": x_shift,
        "yShift": y_shift,
        "something1": s1,
        "something2": s2,
    }
    return Image.frombytes("RGBA", (width, height), bytes(rgba)), sidecar


def encode_sprite(img: Image.Image, sidecar: dict) -> bytes:
    img = img.convert("RGBA")
    width, height = img.size
    header = struct.pack(
        ">iiBiiii",
        width,
        height,
        1 if sidecar.get("requiresShift") else 0,
        int(sidecar.get("xShift", 0)),
        int(sidecar.get("yShift", 0)),
        int(sidecar.get("something1", 0)),
        int(sidecar.get("something2", 0)),
    )
    rgba = img.tobytes()
    out = bytearray(len(rgba))
    for i in range(0, len(rgba), 4):
        r, g, b, a = rgba[i], rgba[i + 1], rgba[i + 2], rgba[i + 3]
        if a < 128:
            out[i] = out[i + 1] = out[i + 2] = out[i + 3] = 0
        else:
            if r == 0 and g == 0 and b == 0:
                r = 1
            out[i] = 0
            out[i + 1] = r
            out[i + 2] = g
            out[i + 3] = b
    return header + bytes(out)


def rewrite_archive(src: Path, dst: Path, replacements: dict[str, bytes]) -> None:
    seen = set()
    with zipfile.ZipFile(src, "r") as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename in replacements:
                zout.writestr(info.filename, replacements[info.filename])
                seen.add(info.filename)
            else:
                zout.writestr(info, zin.read(info.filename))
        for name, data in replacements.items():
            if name not in seen:
                zout.writestr(name, data)


def read_frames(archive: Path, base: int, count: int = FRAME_COUNT) -> list[SpriteFrame]:
    frames: list[SpriteFrame] = []
    with zipfile.ZipFile(archive, "r") as zf:
        for offset in range(count):
            index = base + offset
            try:
                image, sidecar = decode_sprite(zf.read(str(index)))
            except KeyError as exc:
                raise FileNotFoundError(f"archive entry {index} is missing from {archive}") from exc
            frames.append(SpriteFrame(index=index, image=image, sidecar=sidecar))
    return frames


def frame_manifest(style: str, archive: Path, frames: list[SpriteFrame], args: argparse.Namespace) -> dict:
    cell_w = args.max_width * args.scale + (args.pad * 2)
    cell_h = args.max_height * args.scale + (args.pad * 2) + args.label_height
    rows = math.ceil(len(frames) / args.cols)
    return {
        "format": "voidscape-hairstyle-grid-v1",
        "style": style,
        "styleLabel": HEAD_STYLES.get(style, {}).get("label", style),
        "appearanceId": HEAD_STYLES.get(style, {}).get("appearance_id"),
        "archive": str(archive),
        "baseIndex": frames[0].index,
        "frameCount": len(frames),
        "columns": args.cols,
        "rows": rows,
        "scale": args.scale,
        "pad": args.pad,
        "gutter": args.gutter,
        "labelHeight": args.label_height,
        "maxWidth": args.max_width,
        "maxHeight": args.max_height,
        "cellWidth": cell_w,
        "cellHeight": cell_h,
        "gridWidth": args.cols * cell_w + (args.cols + 1) * args.gutter,
        "gridHeight": rows * cell_h + (rows + 1) * args.gutter,
        "background": list(GRID_BG),
        "frames": [
            {
                "offset": n,
                "archiveIndex": frame.index,
                "width": frame.image.width,
                "height": frame.image.height,
                "sidecar": frame.sidecar,
            }
            for n, frame in enumerate(frames)
        ],
    }


def cell_origin(manifest: dict, offset: int) -> tuple[int, int]:
    col = offset % manifest["columns"]
    row = offset // manifest["columns"]
    x = manifest["gutter"] + col * (manifest["cellWidth"] + manifest["gutter"])
    y = manifest["gutter"] + row * (manifest["cellHeight"] + manifest["gutter"])
    return x, y


def frame_slot(manifest: dict, offset: int, frame_w: int, frame_h: int) -> tuple[int, int, int, int]:
    cell_x, cell_y = cell_origin(manifest, offset)
    frame_area_w = manifest["maxWidth"] * manifest["scale"]
    frame_area_h = manifest["maxHeight"] * manifest["scale"]
    slot_w = frame_w * manifest["scale"]
    slot_h = frame_h * manifest["scale"]
    x = cell_x + manifest["pad"] + (frame_area_w - slot_w) // 2
    y = cell_y + manifest["labelHeight"] + manifest["pad"] + (frame_area_h - slot_h) // 2
    return x, y, slot_w, slot_h


def write_frame_files(frames: list[SpriteFrame], out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    for offset, frame in enumerate(frames):
        path = out_dir / f"frame_{offset:02d}.png"
        frame.image.save(path)
        (out_dir / f"frame_{offset:02d}.png.json").write_text(json.dumps(frame.sidecar, indent=2) + "\n")


def make_grid(frames: list[SpriteFrame], manifest: dict, out_path: Path, labels: bool) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    grid = Image.new("RGBA", (manifest["gridWidth"], manifest["gridHeight"]), GRID_BG)
    draw = ImageDraw.Draw(grid)

    for offset, frame in enumerate(frames):
        cell_x, cell_y = cell_origin(manifest, offset)
        draw.rectangle(
            (cell_x - 1, cell_y - 1, cell_x + manifest["cellWidth"], cell_y + manifest["cellHeight"]),
            outline=GRID_LINE,
            width=1,
        )
        if labels:
            label = f"{offset:02d} / {frame.index}"
            draw.text((cell_x + 4, cell_y + 3), label, fill=(0, 0, 0, 255))
        x, y, slot_w, slot_h = frame_slot(manifest, offset, frame.image.width, frame.image.height)
        sprite = frame.image.resize((slot_w, slot_h), Image.Resampling.NEAREST)
        grid.alpha_composite(sprite, (x, y))

    grid.save(out_path)


def write_prompt(path: Path, manifest: dict, theme: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = f"""Use the attached Voidscape RuneScape Classic hairstyle sprite sheet as an exact template.

Return one PNG sprite sheet with the same {manifest["columns"]}x{manifest["rows"]} grid, same frame order, same poses, same sprite scale, same cell placement, and the same overall image size: {manifest["gridWidth"]}x{manifest["gridHeight"]} pixels.

Change only the hairstyle shape to: {theme}

Rules:
- Keep the face, skin area, head size, frame silhouettes, and animation poses aligned to the source.
- Preserve all 18 frames. Frames 00-14 are movement/direction frames; frames 15-17 are combat-facing frames.
- Use crisp low-resolution pixel art. Do not add painterly shading, blur, shadows, labels, signatures, UI, or extra background art.
- Outside each sprite, use pure bright green #00ff00 or transparency only.
- Hair pixels should use neutral grayscale mask shades only, so the game can recolor them later. Good shades: #676767, #848484, #a0a0a0.
- Skin pixels should stay in the original RSC skin-mask family, such as #ff7c7c, #ffb5b5, #ffcdcd.
- Small dark facial detail pixels may stay dark, but do not introduce colorful full-detail pixels.

The result must be a sprite sheet, not a character portrait. It should look like the same tiny RSC character head frames with a different hairstyle.
"""
    path.write_text(text)


def write_wig_prompt(path: Path, manifest: dict, theme: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    text = f"""Use the attached Voidscape RuneScape Classic hair-only sprite sheet as an exact template.

Return one PNG sprite sheet with the same {manifest["columns"]}x{manifest["rows"]} grid, same frame order, same sprite scale, same cell placement, and the same overall image size: {manifest["gridWidth"]}x{manifest["gridHeight"]} pixels.

Change only the hair shape to: {theme}

Rules:
- Draw hair only. Do not draw the face, eyes, mouth, ears, neck, clothing, labels, UI, shadows, or background art.
- Preserve all 18 frames. Frames 00-14 are movement/direction frames; frames 15-17 are combat-facing frames.
- Keep every frame aligned in the exact same cell position as the source.
- Use crisp low-resolution pixel art. Do not add blur, anti-aliasing, semi-transparent pixels, soft shadows, or painterly shading.
- Outside the hair, use pure bright green #00ff00 or transparency only.
- Hair pixels should use neutral grayscale mask shades only, so the game can recolor them later. Good shades: #676767, #848484, #a0a0a0.
- The result must be a sprite sheet, not a character portrait.

The locked bald head will be composited locally after this. Never redraw the head itself.
"""
    path.write_text(text)


def is_bg(pixel: tuple[int, int, int, int], tolerance: int) -> bool:
    r, g, b, a = pixel
    if a == 0:
        return True
    return abs(r - GRID_BG[0]) <= tolerance and abs(g - GRID_BG[1]) <= tolerance and abs(b - GRID_BG[2]) <= tolerance


def is_skin_like(rgb: tuple[int, int, int]) -> bool:
    r, g, b = rgb
    return r >= 190 and abs(g - b) <= 36 and r > g + 24


def nearest_colour(rgb: tuple[int, int, int], palette: Iterable[tuple[int, int, int]]) -> tuple[int, int, int]:
    return min(palette, key=lambda c: sum((rgb[i] - c[i]) ** 2 for i in range(3)))


def normalize_pixel(rgb: tuple[int, int, int], ref: tuple[int, int, int, int] | None = None) -> tuple[int, int, int]:
    r, g, b = rgb
    if ref is not None and ref[3] >= 128:
        rr, rg, rb = ref[:3]
        if not (rr == rg == rb) and not (rr == 255 and rg == rb):
            if max(r, g, b) < 120:
                return (rr, rg, rb)

    if is_skin_like(rgb):
        return nearest_colour(rgb, SKIN_MASK_COLOURS)

    if max(rgb) < 85 and not (abs(r - g) <= 8 and abs(g - b) <= 8):
        return DETAIL_COLOUR

    lum = int((r * 0.299) + (g * 0.587) + (b * 0.114))
    shade = min(HAIR_MASK_SHADES, key=lambda s: abs(lum - s))
    return (shade, shade, shade)


def normalize_image(img: Image.Image, ref: Image.Image | None, tolerance: int) -> Image.Image:
    img = img.convert("RGBA")
    ref = ref.convert("RGBA") if ref is not None else None
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    ref_src = ref.load() if ref is not None and ref.size == img.size else None
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            p = src[x, y]
            if is_bg(p, tolerance):
                continue
            ref_p = ref_src[x, y] if ref_src is not None else None
            dst[x, y] = (*normalize_pixel(p[:3], ref_p), 255)
    return out


def normalize_wig_image(img: Image.Image, tolerance: int) -> Image.Image:
    """Convert a returned hair-only canvas into transparent + grayscale mask pixels."""
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            p = src[x, y]
            if is_bg(p, tolerance):
                continue
            r, g, b = p[:3]
            lum = int((r * 0.299) + (g * 0.587) + (b * 0.114))
            shade = min(HAIR_MASK_SHADES, key=lambda s: abs(lum - s))
            dst[x, y] = (shade, shade, shade, 255)
    return out


def extract_hair_overlay(source: Image.Image) -> Image.Image:
    """Extract only RSC hair-mask pixels from a baked head frame."""
    source = source.convert("RGBA")
    out = Image.new("RGBA", source.size, (0, 0, 0, 0))
    src = source.load()
    dst = out.load()
    for y in range(source.height):
        for x in range(source.width):
            r, g, b, a = src[x, y]
            if a >= 128 and r == g == b:
                dst[x, y] = (r, g, b, 255)
    return out


def merge_wig_frame(base: SpriteFrame, hair: SpriteFrame, tolerance: int = 0) -> SpriteFrame:
    if base.image.size != hair.image.size:
        raise ValueError(f"hair frame size {hair.image.size} does not match locked base {base.image.size}")
    overlay = normalize_wig_image(hair.image, tolerance) if tolerance else hair.image.convert("RGBA")
    merged = base.image.convert("RGBA").copy()
    merged.alpha_composite(overlay)
    return SpriteFrame(index=base.index, image=merged, sidecar=base.sidecar)


def load_wig_base_frames(manifest: dict, archive: Path) -> list[SpriteFrame]:
    base_dir_text = manifest.get("baseFrameDir")
    if base_dir_text:
        base_dir = Path(base_dir_text)
        if not base_dir.is_absolute():
            base_dir = REPO_ROOT / base_dir
        return load_frame_dir(base_dir, manifest)

    base_style = manifest.get("baseStyle", "head4")
    if base_style not in HEAD_STYLES:
        raise ValueError(f"unknown wig base style {base_style}")
    return read_frames(archive, HEAD_STYLES[base_style]["base"])


def load_wig_hair_frames(hair_dir: Path, manifest: dict, bg_tolerance: int = 0) -> list[SpriteFrame]:
    frames = load_frame_dir(hair_dir, manifest)
    if bg_tolerance:
        return [
            SpriteFrame(frame.index, normalize_wig_image(frame.image, bg_tolerance), frame.sidecar)
            for frame in frames
        ]
    return frames


def bake_wig_frames(manifest: dict, hair_dir: Path, archive: Path, bg_tolerance: int = 0) -> list[SpriteFrame]:
    base_frames = load_wig_base_frames(manifest, archive)
    hair_frames = load_wig_hair_frames(hair_dir, manifest, bg_tolerance)
    return [merge_wig_frame(base, hair) for base, hair in zip(base_frames, hair_frames)]


def manifest_from_path(path: Path) -> dict:
    data = json.loads(path.read_text())
    if data.get("format") != "voidscape-hairstyle-grid-v1":
        raise ValueError(f"{path} is not a hairstyle grid manifest")
    return data


def cmd_bootstrap(args: argparse.Namespace) -> int:
    style = args.style
    if style not in HEAD_STYLES:
        raise SystemExit(f"unknown style {style}; choices: {', '.join(HEAD_STYLES)}")
    archive = args.archive.resolve()
    frames = read_frames(archive, HEAD_STYLES[style]["base"])
    args.max_width = max(frame.image.width for frame in frames)
    args.max_height = max(frame.image.height for frame in frames)
    args.label_height = 18 if args.labels else 0

    out_dir = args.out_dir
    frame_dir = out_dir / "frames"
    write_frame_files(frames, frame_dir)

    manifest = frame_manifest(style, archive, frames, args)
    manifest["frameDir"] = str(frame_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = out_dir / f"{style}_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    make_grid(frames, manifest, out_dir / f"{style}_ai_grid.png", labels=False)
    label_manifest = dict(manifest)
    extra_label_height = 0 if manifest["labelHeight"] else 18
    label_manifest["labelHeight"] = manifest["labelHeight"] + extra_label_height
    label_manifest["cellHeight"] = manifest["cellHeight"] + extra_label_height
    label_manifest["gridHeight"] = manifest["rows"] * label_manifest["cellHeight"] + (manifest["rows"] + 1) * manifest["gutter"]
    make_grid(frames, label_manifest, out_dir / f"{style}_labeled_reference_grid.png", labels=True)
    write_prompt(out_dir / f"{style}_chatgpt_prompt.txt", manifest, args.theme)

    print(f"wrote frames: {frame_dir}")
    print(f"wrote AI grid: {out_dir / f'{style}_ai_grid.png'}")
    print(f"wrote labeled reference: {out_dir / f'{style}_labeled_reference_grid.png'}")
    print(f"wrote manifest: {manifest_path}")
    print(f"wrote prompt: {out_dir / f'{style}_chatgpt_prompt.txt'}")
    return 0


def load_reference_images(manifest: dict) -> list[Image.Image] | None:
    frame_dir_text = manifest.get("frameDir")
    if not frame_dir_text:
        return None
    frame_dir = Path(frame_dir_text)
    if not frame_dir.is_absolute():
        frame_dir = REPO_ROOT / frame_dir
    refs: list[Image.Image] = []
    for offset in range(manifest["frameCount"]):
        path = frame_dir / f"frame_{offset:02d}.png"
        if not path.exists():
            return None
        refs.append(Image.open(path).convert("RGBA"))
    return refs


def cmd_import_grid(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    grid = Image.open(args.grid).convert("RGBA")
    expected_size = (manifest["gridWidth"], manifest["gridHeight"])
    if grid.size != expected_size:
        print(f"resizing generated grid from {grid.size} to expected {expected_size}")
        grid = grid.resize(expected_size, Image.Resampling.BOX)

    refs = load_reference_images(manifest)
    out_frames: list[SpriteFrame] = []
    for frame_info in manifest["frames"]:
        offset = frame_info["offset"]
        x, y, slot_w, slot_h = frame_slot(manifest, offset, frame_info["width"], frame_info["height"])
        crop = grid.crop((x, y, x + slot_w, y + slot_h))
        resample = Image.Resampling.NEAREST if args.resample == "nearest" else Image.Resampling.BOX
        small = crop.resize((frame_info["width"], frame_info["height"]), resample)
        if args.normalize:
            ref = refs[offset] if refs else None
            small = normalize_image(small, ref, args.bg_tolerance)
        else:
            small = chroma_to_alpha(small, args.bg_tolerance)
        out_frames.append(SpriteFrame(index=frame_info["archiveIndex"], image=small, sidecar=frame_info["sidecar"]))

    write_frame_files(out_frames, args.out_dir)
    imported_manifest = dict(manifest)
    imported_manifest["frameDir"] = str(args.out_dir)
    imported_manifest["sourceGrid"] = str(args.grid)
    (args.out_dir / "import_manifest.json").write_text(json.dumps(imported_manifest, indent=2) + "\n")
    print(f"wrote imported frames: {args.out_dir}")
    print(f"wrote imported manifest: {args.out_dir / 'import_manifest.json'}")
    return 0


def chroma_to_alpha(img: Image.Image, tolerance: int) -> Image.Image:
    img = img.convert("RGBA")
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.load()
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            p = src[x, y]
            if not is_bg(p, tolerance):
                dst[x, y] = (p[0], p[1], p[2], 255)
    return out


def load_frame_dir(frames_dir: Path, manifest: dict) -> list[SpriteFrame]:
    frames: list[SpriteFrame] = []
    for frame_info in manifest["frames"]:
        offset = frame_info["offset"]
        img_path = frames_dir / f"frame_{offset:02d}.png"
        sidecar_path = frames_dir / f"frame_{offset:02d}.png.json"
        if not img_path.exists():
            raise FileNotFoundError(img_path)
        sidecar = frame_info["sidecar"]
        if sidecar_path.exists():
            sidecar = json.loads(sidecar_path.read_text())
        frames.append(SpriteFrame(frame_info["archiveIndex"], Image.open(img_path).convert("RGBA"), sidecar))
    return frames


def mask_stats(img: Image.Image) -> dict[str, int]:
    stats = {"transparent": 0, "hairMask": 0, "skinMask": 0, "detail": 0, "fullColour": 0}
    for p in img.convert("RGBA").getdata():
        r, g, b, a = p
        if a < 128:
            stats["transparent"] += 1
        elif r == g == b:
            stats["hairMask"] += 1
        elif r == 255 and g == b:
            stats["skinMask"] += 1
        elif max(r, g, b) < 130:
            stats["detail"] += 1
        else:
            stats["fullColour"] += 1
    return stats


def cmd_validate(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    frames = load_frame_dir(args.frames_dir, manifest)
    failed = False
    totals = {"transparent": 0, "hairMask": 0, "skinMask": 0, "detail": 0, "fullColour": 0}
    for frame_info, frame in zip(manifest["frames"], frames):
        expected_size = (frame_info["width"], frame_info["height"])
        stats = mask_stats(frame.image)
        for key, value in stats.items():
            totals[key] += value
        notes = []
        if frame.image.size != expected_size:
            failed = True
            notes.append(f"BAD SIZE expected={expected_size} actual={frame.image.size}")
        if stats["fullColour"] > args.max_full_colour:
            failed = True
            notes.append(f"FULL-COLOUR>{args.max_full_colour}")
        print(
            f"frame {frame_info['offset']:02d} size={frame.image.width}x{frame.image.height} "
            f"hair={stats['hairMask']:3d} skin={stats['skinMask']:3d} "
            f"detail={stats['detail']:3d} full={stats['fullColour']:3d}"
            + (f"  {'; '.join(notes)}" if notes else "")
        )
    print(
        "totals "
        + " ".join(f"{key}={value}" for key, value in totals.items())
    )
    return 1 if failed else 0


def recolor_sprite(img: Image.Image, hair: tuple[int, int, int], skin: tuple[int, int, int]) -> Image.Image:
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    src = img.convert("RGBA").load()
    dst = out.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a < 128:
                continue
            if r == g == b:
                dst[x, y] = ((r * hair[0]) >> 8, (g * hair[1]) >> 8, (b * hair[2]) >> 8, 255)
            elif r == 255 and g == b:
                dst[x, y] = ((r * skin[0]) >> 8, (g * skin[1]) >> 8, (b * skin[2]) >> 8, 255)
            else:
                dst[x, y] = (r, g, b, 255)
    return out


def colour_int_to_rgb(colour: int) -> tuple[int, int, int]:
    return ((colour >> 16) & 0xFF, (colour >> 8) & 0xFF, colour & 0xFF)


def mask_pixel(
    pixel: tuple[int, int, int, int],
    mask1: tuple[int, int, int],
    mask2: tuple[int, int, int],
    blue_mask: tuple[int, int, int] | None = None,
) -> tuple[int, int, int, int] | None:
    r, g, b, a = pixel
    if a < 128:
        return None
    if r == g == b:
        return ((r * mask1[0]) >> 8, (g * mask1[1]) >> 8, (b * mask1[2]) >> 8, 255)
    if r == 255 and g == b:
        return ((r * mask2[0]) >> 8, (g * mask2[1]) >> 8, (b * mask2[2]) >> 8, 255)
    if blue_mask and blue_mask != (255, 255, 255) and r == g and b != g:
        shifter = r * b
        return ((blue_mask[0] * shifter) >> 16, (blue_mask[1] * shifter) >> 16, (blue_mask[2] * shifter) >> 16, 255)
    return (r, g, b, 255)


def client_draw_sprite(
    canvas: Image.Image,
    frame: SpriteFrame,
    x: int,
    y: int,
    width: int,
    height: int,
    mask1: tuple[int, int, int],
    mask2: tuple[int, int, int],
    *,
    mirror_x: bool = False,
    top_pixel_skew: int = 0,
    blue_mask: tuple[int, int, int] | None = None,
) -> None:
    """Approximate GraphicsController.drawSpriteClipping for player sprites."""
    img = frame.image.convert("RGBA")
    sidecar = frame.sidecar
    sprite_w, sprite_h = img.size
    if sprite_w <= 0 or sprite_h <= 0 or width <= 0 or height <= 0:
        return

    src_start_x = 0
    src_start_y = 0
    dest_first_col = top_pixel_skew << 16
    scale_x = (sprite_w << 16) // width
    scale_y = (sprite_h << 16) // height
    dest_col_skew_per_row = -((top_pixel_skew << 16) // height) if height else 0

    if sidecar.get("requiresShift"):
        logical_w = int(sidecar.get("something1", 0))
        logical_h = int(sidecar.get("something2", 0))
        if logical_w == 0 or logical_h == 0:
            return
        scale_x = (logical_w << 16) // width
        scale_y = (logical_h << 16) // height
        x_shift = int(sidecar.get("xShift", 0))
        if mirror_x:
            x_shift = logical_w - sprite_w - x_shift
        y_shift = int(sidecar.get("yShift", 0))

        x += (logical_w + x_shift * width - 1) // logical_w
        y_delta = (y_shift * height + logical_h - 1) // logical_h
        y += y_delta
        dest_first_col += y_delta * dest_col_skew_per_row

        if (x_shift * width) % logical_w != 0:
            src_start_x = ((logical_w - (width * x_shift) % logical_w) << 16) // width
        if (y_shift * height) % logical_h != 0:
            src_start_y = ((logical_h - (height * y_shift) % logical_h) << 16) // height

        width = (scale_x + ((sprite_w << 16) - (src_start_x + 1))) // scale_x
        height = ((sprite_h << 16) - src_start_y - (1 - scale_y)) // scale_y

    if width <= 0 or height <= 0:
        return

    src = img.load()
    dst = canvas.load()
    canvas_w, canvas_h = canvas.size
    for row in range(height):
        dest_y = y + row
        if dest_y < 0 or dest_y >= canvas_h:
            continue
        dest_x_start = (dest_first_col + (row * dest_col_skew_per_row)) >> 16
        src_y = (src_start_y + row * scale_y) >> 16
        if src_y < 0 or src_y >= sprite_h:
            continue
        for col in range(width):
            dest_x = x + dest_x_start + col
            if dest_x < 0 or dest_x >= canvas_w:
                continue
            if mirror_x:
                src_x = ((sprite_w << 16) - (src_start_x + 1) - col * scale_x) >> 16
            else:
                src_x = (src_start_x + col * scale_x) >> 16
            if src_x < 0 or src_x >= sprite_w:
                continue
            colour = mask_pixel(src[src_x, src_y], mask1, mask2, blue_mask)
            if colour is not None:
                dst[dest_x, dest_y] = colour


def render_base_player(
    head: SpriteFrame,
    body: SpriteFrame,
    legs: SpriteFrame,
    offset: int,
    hair: tuple[int, int, int],
    top: tuple[int, int, int],
    bottom: tuple[int, int, int],
    skin: tuple[int, int, int],
) -> Image.Image:
    canvas = Image.new("RGBA", (88, 112), (0, 0, 0, 0))
    x = 12
    y = 2
    width = 64
    height = 102
    wanted_dir = 2 if offset >= 15 else min(offset // 3, 4)
    layer_frames = {
        0: (head, hair),
        1: (body, top),
        2: (legs, bottom),
    }

    for mapped_layer in ANIM_DIR_LAYER_TO_CHAR_LAYER[wanted_dir]:
        layer = layer_frames.get(mapped_layer)
        if layer is None:
            continue
        frame, mask = layer
        client_draw_sprite(canvas, frame, x, y, width, height, mask, skin)
    return canvas


def trim_alpha(img: Image.Image, pad: int = 2) -> Image.Image:
    bbox = img.getbbox()
    if bbox is None:
        return img
    left = max(0, bbox[0] - pad)
    top = max(0, bbox[1] - pad)
    right = min(img.width, bbox[2] + pad)
    bottom = min(img.height, bbox[3] + pad)
    return img.crop((left, top, right, bottom))


def render_player_preview_sheet(
    head_frames: list[SpriteFrame],
    body_frames: list[SpriteFrame],
    leg_frames: list[SpriteFrame],
    hair_colours: list[tuple[int, int, int]],
    top: tuple[int, int, int],
    bottom: tuple[int, int, int],
    skin: tuple[int, int, int],
    background: tuple[int, int, int],
    *,
    scale: int,
    cols: int,
    actual_strip: bool,
) -> Image.Image:
    rendered: list[tuple[str, int, Image.Image, Image.Image]] = []
    for hair in hair_colours:
        label = f"hair #{hair[0]:02x}{hair[1]:02x}{hair[2]:02x}"
        for offset in range(FRAME_COUNT):
            actual = render_base_player(
                head_frames[offset],
                body_frames[offset],
                leg_frames[offset],
                offset,
                hair,
                top,
                bottom,
                skin,
            )
            crop = trim_alpha(actual, pad=2)
            rendered.append((label, offset, actual, crop))

    max_crop_w = max(crop.width for _, _, _, crop in rendered)
    max_crop_h = max(crop.height for _, _, _, crop in rendered)
    rows = math.ceil(len(rendered) / cols)
    pad = 10
    label_h = 16
    actual_strip_h = max_crop_h + 8 if actual_strip else 0
    cell_w = max_crop_w * scale + pad * 2
    cell_h = label_h + max_crop_h * scale + actual_strip_h + pad * 3
    sheet = Image.new("RGBA", (cols * cell_w, rows * cell_h), (*background, 255))
    draw = ImageDraw.Draw(sheet)

    for idx, (label, offset, actual, crop) in enumerate(rendered):
        col = idx % cols
        row = idx // cols
        cell_x = col * cell_w
        cell_y = row * cell_h
        if col == 0:
            draw.text((cell_x + 4, cell_y + 3), label, fill=(255, 255, 255, 255))
        draw.text((cell_x + cell_w - 24, cell_y + 3), f"{offset:02d}", fill=(210, 210, 210, 255))

        zoom = crop.resize((crop.width * scale, crop.height * scale), Image.Resampling.NEAREST)
        zoom_x = cell_x + (cell_w - zoom.width) // 2
        zoom_y = cell_y + label_h + pad
        sheet.alpha_composite(zoom, (zoom_x, zoom_y))

        if actual_strip:
            tiny = trim_alpha(actual, pad=0)
            tiny_x = cell_x + (cell_w - tiny.width) // 2
            tiny_y = cell_y + label_h + pad + max_crop_h * scale + pad
            sheet.alpha_composite(tiny, (tiny_x, tiny_y))

    return sheet


def cmd_player_preview(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    archive = args.archive.resolve()
    head_frames = load_frame_dir(args.frames_dir, manifest)
    body_frames = read_frames(archive, BASE_PLAYER_ANIMS["body"])
    leg_frames = read_frames(archive, BASE_PLAYER_ANIMS["legs"])

    sheet = render_player_preview_sheet(
        head_frames,
        body_frames,
        leg_frames,
        [parse_colour(c) for c in args.hair],
        parse_colour(args.top),
        parse_colour(args.bottom),
        parse_colour(args.skin),
        parse_colour(args.background),
        scale=args.scale,
        cols=args.cols,
        actual_strip=args.actual_strip,
    )
    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print(f"wrote player preview: {args.out}")
    return 0


def cmd_wig_bootstrap(args: argparse.Namespace) -> int:
    if args.base_style not in HEAD_STYLES:
        raise SystemExit(f"unknown base style {args.base_style}; choices: {', '.join(HEAD_STYLES)}")
    if args.source_style not in HEAD_STYLES:
        raise SystemExit(f"unknown source style {args.source_style}; choices: {', '.join(HEAD_STYLES)}")

    archive = args.archive.resolve()
    base_frames = read_frames(archive, HEAD_STYLES[args.base_style]["base"])
    source_frames = read_frames(archive, HEAD_STYLES[args.source_style]["base"])
    hair_frames: list[SpriteFrame] = []
    for base, source in zip(base_frames, source_frames):
        if base.image.size != source.image.size:
            raise ValueError(
                f"{args.base_style} frame {base.index} is {base.image.size}, "
                f"but {args.source_style} frame {source.index} is {source.image.size}"
            )
        hair_frames.append(SpriteFrame(base.index, extract_hair_overlay(source.image), base.sidecar))

    args.max_width = max(frame.image.width for frame in base_frames)
    args.max_height = max(frame.image.height for frame in base_frames)
    args.label_height = 18 if args.labels else 0

    out_dir = args.out_dir
    base_dir = out_dir / "locked_bald_base"
    hair_dir = out_dir / "hair_canvas"
    write_frame_files(base_frames, base_dir)
    write_frame_files(hair_frames, hair_dir)

    manifest = frame_manifest(
        f"wig_{args.base_style}_from_{args.source_style}",
        archive,
        hair_frames,
        args,
    )
    manifest.update({
        "workflow": "wig-compositor",
        "baseStyle": args.base_style,
        "sourceStyle": args.source_style,
        "baseFrameDir": str(base_dir),
        "frameDir": str(hair_dir),
        "lockedBase": True,
    })

    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = out_dir / "wig_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")

    make_grid(hair_frames, manifest, out_dir / "wig_hair_canvas_grid.png", labels=False)
    label_manifest = dict(manifest)
    extra_label_height = 0 if manifest["labelHeight"] else 18
    label_manifest["labelHeight"] = manifest["labelHeight"] + extra_label_height
    label_manifest["cellHeight"] = manifest["cellHeight"] + extra_label_height
    label_manifest["gridHeight"] = manifest["rows"] * label_manifest["cellHeight"] + (manifest["rows"] + 1) * manifest["gutter"]
    make_grid(hair_frames, label_manifest, out_dir / "wig_labeled_reference_grid.png", labels=True)
    write_wig_prompt(out_dir / "wig_chatgpt_prompt.txt", manifest, args.theme)

    baked_source_dir = out_dir / "baked_source_reference"
    baked_frames = [merge_wig_frame(base, hair) for base, hair in zip(base_frames, hair_frames)]
    write_frame_files(baked_frames, baked_source_dir)
    baked_manifest = dict(manifest)
    baked_manifest["workflow"] = "wig-baked-reference"
    baked_manifest["frameDir"] = str(baked_source_dir)
    (baked_source_dir / "baked_manifest.json").write_text(json.dumps(baked_manifest, indent=2) + "\n")

    print(f"wrote locked bald base: {base_dir}")
    print(f"wrote hair-only canvas: {hair_dir}")
    print(f"wrote AI grid: {out_dir / 'wig_hair_canvas_grid.png'}")
    print(f"wrote labeled reference: {out_dir / 'wig_labeled_reference_grid.png'}")
    print(f"wrote manifest: {manifest_path}")
    print(f"wrote prompt: {out_dir / 'wig_chatgpt_prompt.txt'}")
    print(f"wrote baked source reference: {baked_source_dir}")
    return 0


def cmd_import_wig_grid(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    grid = Image.open(args.grid).convert("RGBA")
    expected_size = (manifest["gridWidth"], manifest["gridHeight"])
    if grid.size != expected_size:
        print(f"resizing generated grid from {grid.size} to expected {expected_size}")
        grid = grid.resize(expected_size, Image.Resampling.BOX)

    out_frames: list[SpriteFrame] = []
    for frame_info in manifest["frames"]:
        offset = frame_info["offset"]
        x, y, slot_w, slot_h = frame_slot(manifest, offset, frame_info["width"], frame_info["height"])
        crop = grid.crop((x, y, x + slot_w, y + slot_h))
        resample = Image.Resampling.NEAREST if args.resample == "nearest" else Image.Resampling.BOX
        small = crop.resize((frame_info["width"], frame_info["height"]), resample)
        small = normalize_wig_image(small, args.bg_tolerance)
        out_frames.append(SpriteFrame(index=frame_info["archiveIndex"], image=small, sidecar=frame_info["sidecar"]))

    write_frame_files(out_frames, args.out_dir)
    imported_manifest = dict(manifest)
    imported_manifest["frameDir"] = str(args.out_dir)
    imported_manifest["sourceGrid"] = str(args.grid)
    (args.out_dir / "import_manifest.json").write_text(json.dumps(imported_manifest, indent=2) + "\n")
    print(f"wrote imported hair canvas frames: {args.out_dir}")
    print(f"wrote imported manifest: {args.out_dir / 'import_manifest.json'}")
    return 0


def wig_mask_stats(hair: Image.Image, base: Image.Image) -> dict[str, int]:
    stats = {
        "transparent": 0,
        "hairMask": 0,
        "nonMask": 0,
        "baseSkinOverlap": 0,
        "baseDetailOverlap": 0,
    }
    hair = hair.convert("RGBA")
    base = base.convert("RGBA")
    hair_px = hair.load()
    base_px = base.load()
    for y in range(hair.height):
        for x in range(hair.width):
            r, g, b, a = hair_px[x, y]
            if a < 128:
                stats["transparent"] += 1
                continue
            if r == g == b:
                stats["hairMask"] += 1
            else:
                stats["nonMask"] += 1

            br, bg, bb, ba = base_px[x, y]
            if ba >= 128:
                if br == 255 and bg == bb:
                    stats["baseSkinOverlap"] += 1
                else:
                    stats["baseDetailOverlap"] += 1
    return stats


def cmd_wig_validate(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    base_frames = load_wig_base_frames(manifest, args.archive.resolve())
    hair_frames = load_wig_hair_frames(args.hair_dir, manifest, args.bg_tolerance)
    failed = False
    totals = {
        "transparent": 0,
        "hairMask": 0,
        "nonMask": 0,
        "baseSkinOverlap": 0,
        "baseDetailOverlap": 0,
    }
    for frame_info, base, hair in zip(manifest["frames"], base_frames, hair_frames):
        expected_size = (frame_info["width"], frame_info["height"])
        stats = wig_mask_stats(hair.image, base.image)
        for key, value in stats.items():
            totals[key] += value
        notes = []
        if hair.image.size != expected_size:
            failed = True
            notes.append(f"BAD SIZE expected={expected_size} actual={hair.image.size}")
        if stats["nonMask"] > args.max_non_mask:
            failed = True
            notes.append(f"NON-MASK>{args.max_non_mask}")
        if stats["baseDetailOverlap"] > args.max_detail_overlap:
            failed = True
            notes.append(f"DETAIL-OVERLAP>{args.max_detail_overlap}")
        print(
            f"frame {frame_info['offset']:02d} size={hair.image.width}x{hair.image.height} "
            f"hair={stats['hairMask']:3d} nonMask={stats['nonMask']:3d} "
            f"skinOverlap={stats['baseSkinOverlap']:3d} detailOverlap={stats['baseDetailOverlap']:3d}"
            + (f"  {'; '.join(notes)}" if notes else "")
        )
    print("totals " + " ".join(f"{key}={value}" for key, value in totals.items()))
    return 1 if failed else 0


def cmd_wig_bake(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    baked_frames = bake_wig_frames(manifest, args.hair_dir, args.archive.resolve(), args.bg_tolerance)
    write_frame_files(baked_frames, args.out_dir)
    baked_manifest = dict(manifest)
    baked_manifest["workflow"] = "wig-baked-head"
    baked_manifest["frameDir"] = str(args.out_dir)
    baked_manifest["sourceWigManifest"] = str(args.manifest)
    baked_manifest["sourceHairDir"] = str(args.hair_dir)
    manifest_path = args.out_dir / "baked_manifest.json"
    manifest_path.write_text(json.dumps(baked_manifest, indent=2) + "\n")
    print(f"wrote baked head frames: {args.out_dir}")
    print(f"wrote baked manifest: {manifest_path}")
    return 0


def render_wig_preview(args: argparse.Namespace) -> Image.Image:
    manifest = manifest_from_path(args.manifest)
    archive = args.archive.resolve()
    head_frames = bake_wig_frames(manifest, args.hair_dir, archive, args.bg_tolerance)
    return render_player_preview_sheet(
        head_frames,
        read_frames(archive, BASE_PLAYER_ANIMS["body"]),
        read_frames(archive, BASE_PLAYER_ANIMS["legs"]),
        [parse_colour(c) for c in args.hair],
        parse_colour(args.top),
        parse_colour(args.bottom),
        parse_colour(args.skin),
        parse_colour(args.background),
        scale=args.scale,
        cols=args.cols,
        actual_strip=args.actual_strip,
    )


def cmd_wig_preview(args: argparse.Namespace) -> int:
    sheet = render_wig_preview(args)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print(f"wrote wig player preview: {args.out}")
    return 0


def cmd_wig_live(args: argparse.Namespace) -> int:
    args.manifest = args.manifest.resolve()
    args.hair_dir = args.hair_dir.resolve()
    args.archive = args.archive.resolve()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            if self.path.startswith("/preview.png"):
                try:
                    image = render_wig_preview(args)
                    buf = BytesIO()
                    image.save(buf, format="PNG")
                    data = buf.getvalue()
                    self.send_response(200)
                    self.send_header("Content-Type", "image/png")
                    self.send_header("Cache-Control", "no-store")
                    self.send_header("Content-Length", str(len(data)))
                    self.end_headers()
                    self.wfile.write(data)
                except Exception as exc:
                    body = f"render failed: {exc}\n".encode("utf-8")
                    self.send_response(500)
                    self.send_header("Content-Type", "text/plain; charset=utf-8")
                    self.send_header("Content-Length", str(len(body)))
                    self.end_headers()
                    self.wfile.write(body)
                return

            body = f"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <title>Voidscape Wig Preview</title>
  <style>
    html, body {{
      margin: 0;
      min-height: 100%;
      background: #101010;
      color: #e8e8e8;
      font-family: system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
    }}
    main {{ padding: 16px; }}
    img {{
      image-rendering: pixelated;
      image-rendering: crisp-edges;
      max-width: 100%;
      border: 1px solid #333;
      background: #123c1b;
    }}
    .meta {{ color: #aaa; font-size: 13px; margin-bottom: 10px; }}
  </style>
</head>
<body>
  <main>
    <div class="meta">Auto-refreshing every {args.refresh_ms}ms. Edit the hair frame PNGs, save, and the preview will redraw from disk.</div>
    <img id="preview" src="/preview.png">
  </main>
  <script>
    const img = document.getElementById("preview");
    setInterval(() => {{
      img.src = "/preview.png?t=" + Date.now();
    }}, {args.refresh_ms});
  </script>
</body>
</html>
""".encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format: str, *values: object) -> None:
            return

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    host, port = server.server_address
    print(f"wig live preview: http://{host}:{port}")
    print("press Ctrl-C to stop")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


WIG_EDITOR_HTML = r"""<!doctype html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Voidscape Wig Editor</title>
  <style>
    :root {
      color-scheme: dark;
      --bg: #0f1115;
      --panel: #171b22;
      --panel2: #202632;
      --line: #343b49;
      --text: #f1f4f8;
      --muted: #9ca7b8;
      --accent: #69d2ff;
      --ok: #71e0a6;
      --warn: #ffcc66;
    }
    * { box-sizing: border-box; }
    html, body {
      margin: 0;
      min-height: 100%;
      background: var(--bg);
      color: var(--text);
      font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    body { overflow: hidden; }
    button, input {
      font: inherit;
      color: inherit;
    }
    button {
      border: 1px solid var(--line);
      background: var(--panel2);
      border-radius: 6px;
      padding: 8px 10px;
      cursor: pointer;
      min-height: 34px;
    }
    button:hover { border-color: var(--accent); }
    button.active {
      border-color: var(--accent);
      box-shadow: 0 0 0 1px color-mix(in srgb, var(--accent) 55%, transparent);
    }
    .app {
      height: 100vh;
      display: grid;
      grid-template-rows: auto 1fr auto;
    }
    .topbar, .statusbar {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: #11151c;
      border-bottom: 1px solid var(--line);
      min-width: 0;
    }
    .statusbar {
      border-top: 1px solid var(--line);
      border-bottom: 0;
      color: var(--muted);
      font-size: 13px;
      min-height: 38px;
    }
    .brand {
      font-weight: 750;
      letter-spacing: 0;
      margin-right: 8px;
      white-space: nowrap;
    }
    .chip {
      font-size: 12px;
      color: var(--muted);
      padding: 5px 8px;
      border: 1px solid var(--line);
      border-radius: 999px;
      background: #121720;
      max-width: 360px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .workspace {
      min-height: 0;
      display: grid;
      grid-template-columns: 220px minmax(460px, 1fr) 420px;
    }
    .frames {
      min-height: 0;
      overflow: auto;
      padding: 12px;
      background: #10141b;
      border-right: 1px solid var(--line);
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      align-content: start;
      gap: 10px;
    }
    .frameBtn {
      padding: 7px;
      background: var(--panel);
      display: grid;
      gap: 5px;
      justify-items: center;
      min-height: 94px;
    }
    .frameBtn span {
      font-size: 11px;
      color: var(--muted);
    }
    .frameBtn canvas {
      image-rendering: pixelated;
      image-rendering: crisp-edges;
      width: 56px;
      height: 64px;
    }
    .editorPane {
      min-width: 0;
      min-height: 0;
      display: grid;
      grid-template-rows: auto 1fr;
      background:
        linear-gradient(45deg, #151a22 25%, transparent 25%),
        linear-gradient(-45deg, #151a22 25%, transparent 25%),
        linear-gradient(45deg, transparent 75%, #151a22 75%),
        linear-gradient(-45deg, transparent 75%, #151a22 75%);
      background-size: 24px 24px;
      background-position: 0 0, 0 12px, 12px -12px, -12px 0;
    }
    .tools {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 8px;
      padding: 10px 12px;
      background: rgba(18, 22, 30, 0.94);
      border-bottom: 1px solid var(--line);
    }
    .swatch {
      width: 32px;
      height: 32px;
      padding: 0;
      border-radius: 4px;
    }
    .controlGroup {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding-left: 8px;
      border-left: 1px solid var(--line);
    }
    .toolHint {
      color: var(--muted);
      font-size: 12px;
      white-space: nowrap;
    }
    .brushInput {
      width: 64px;
      height: 34px;
      border-radius: 6px;
      border: 1px solid var(--line);
      background: var(--panel2);
      padding: 0 8px;
    }
    .stageWrap {
      min-height: 0;
      overflow: auto;
      display: grid;
      place-items: center;
      padding: 24px;
    }
    .canvasShell {
      padding: 18px;
      background: #0b0d11;
      border: 1px solid var(--line);
      border-radius: 8px;
      box-shadow: 0 22px 70px rgba(0, 0, 0, .35);
    }
    #editorCanvas {
      display: block;
      image-rendering: pixelated;
      image-rendering: crisp-edges;
      cursor: crosshair;
      background: #00ff00;
    }
    .side {
      min-width: 0;
      min-height: 0;
      display: grid;
      grid-template-rows: auto 1fr;
      background: #10141b;
      border-left: 1px solid var(--line);
    }
    .sideActions {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      padding: 12px;
      border-bottom: 1px solid var(--line);
    }
    .previewWrap {
      min-height: 0;
      overflow: auto;
      padding: 12px;
    }
    #previewImage {
      image-rendering: pixelated;
      image-rendering: crisp-edges;
      max-width: 100%;
      border: 1px solid var(--line);
      background: #123c1b;
    }
    pre {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      color: var(--muted);
      font-size: 12px;
      line-height: 1.35;
      margin: 10px 0 0;
    }
    .hidden { display: none; }
    @media (max-width: 1040px) {
      body { overflow: auto; }
      .app { height: auto; min-height: 100vh; }
      .workspace { grid-template-columns: 1fr; grid-template-rows: auto auto auto; }
      .frames { grid-template-columns: repeat(6, minmax(82px, 1fr)); border-right: 0; border-bottom: 1px solid var(--line); }
      .side { border-left: 0; border-top: 1px solid var(--line); }
    }
  </style>
</head>
<body>
  <div class="app">
    <div class="topbar">
      <div class="brand">Voidscape Wig Editor</div>
      <div id="frameLabel" class="chip">frame</div>
      <div id="pathLabel" class="chip">hair dir</div>
    </div>
    <div class="workspace">
      <div id="frameGrid" class="frames"></div>
      <main class="editorPane">
        <div class="tools">
          <button id="paintTool" class="active" title="Paint hair pixels">Paint</button>
          <button id="eraseTool" title="Erase hair pixels">Erase</button>
          <button id="sampleTool" title="Pick a hair shade from the canvas">Pick</button>
          <div class="controlGroup" id="swatches"></div>
          <div class="controlGroup">
            <input id="brushSize" class="brushInput" type="number" min="1" max="4" value="1" title="Brush size">
          </div>
          <div class="controlGroup">
            <button id="nudgeLeft" title="Nudge hair left">←</button>
            <button id="nudgeUp" title="Nudge hair up">↑</button>
            <button id="nudgeDown" title="Nudge hair down">↓</button>
            <button id="nudgeRight" title="Nudge hair right">→</button>
          </div>
          <div class="controlGroup">
            <button id="copyPrev">Copy prev</button>
            <button id="copyNext">Copy next</button>
            <button id="clearFrame">Clear</button>
          </div>
          <div class="controlGroup">
            <button id="saveFrame">Save frame</button>
            <button id="saveAll">Save all</button>
            <label>
              <input id="importFrame" class="hidden" type="file" accept="image/png,image/webp,image/jpeg">
              <button id="importButton" type="button">Import PNG</button>
            </label>
          </div>
          <div id="importControls" class="controlGroup hidden">
            <span class="toolHint">Place import</span>
            <button id="importFit" type="button" title="Fit imported art to this frame">Fit</button>
            <button id="importScaleDown" type="button" title="Shrink imported art">-</button>
            <button id="importScaleUp" type="button" title="Grow imported art">+</button>
            <button id="importApply" type="button" title="Commit imported art to this frame">Apply</button>
            <button id="importCancel" type="button" title="Cancel imported art">Cancel</button>
          </div>
        </div>
        <div class="stageWrap">
          <div class="canvasShell">
            <canvas id="editorCanvas"></canvas>
          </div>
        </div>
      </main>
      <aside class="side">
        <div class="sideActions">
          <button id="refreshPreview">Save + preview</button>
          <button id="validateButton">Validate</button>
        </div>
        <div class="previewWrap">
          <img id="previewImage" alt="Player preview">
          <pre id="validationOutput"></pre>
        </div>
      </aside>
    </div>
    <div id="status" class="statusbar">Loading editor...</div>
  </div>
  <script>
    const HAIR_COLORS = {
      dark: [103, 103, 103, 255],
      mid: [132, 132, 132, 255],
      light: [160, 160, 160, 255],
    };
    const state = {
      frames: [],
      bases: [],
      hairs: [],
      thumbs: [],
      current: 0,
      tool: "paint",
      color: HAIR_COLORS.mid,
      zoom: 24,
      dirty: new Set(),
      drawing: false,
      importing: null,
    };
    const $ = (id) => document.getElementById(id);
    const canvas = $("editorCanvas");
    const ctx = canvas.getContext("2d");
    ctx.imageSmoothingEnabled = false;

    function setStatus(text, tone) {
      const el = $("status");
      el.textContent = text;
      el.style.color = tone === "ok" ? "var(--ok)" : tone === "warn" ? "var(--warn)" : "var(--muted)";
    }

    function loadImage(url) {
      return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => resolve(img);
        img.onerror = reject;
        img.src = url + (url.includes("?") ? "&" : "?") + "t=" + Date.now();
      });
    }

    function makeLayer(width, height) {
      const c = document.createElement("canvas");
      c.width = width;
      c.height = height;
      c.getContext("2d").imageSmoothingEnabled = false;
      return c;
    }

    async function init() {
      const response = await fetch("/api/state");
      const data = await response.json();
      state.frames = data.frames;
      state.zoom = data.zoom;
      $("pathLabel").textContent = data.hairDir;
      buildSwatches();
      buildFrameGrid();
      for (const frame of state.frames) {
        const base = await loadImage(frame.baseUrl);
        const hair = await loadImage(frame.hairUrl);
        const layer = makeLayer(frame.width, frame.height);
        layer.getContext("2d").drawImage(hair, 0, 0, frame.width, frame.height);
        state.bases[frame.offset] = base;
        state.hairs[frame.offset] = layer;
        renderThumb(frame.offset);
      }
      selectFrame(0);
      await refreshPreview(false);
      setStatus("Ready", "ok");
    }

    function buildSwatches() {
      const wrap = $("swatches");
      Object.entries(HAIR_COLORS).forEach(([name, rgba]) => {
        const b = document.createElement("button");
        b.className = "swatch" + (name === "mid" ? " active" : "");
        b.title = name;
        b.style.background = `rgb(${rgba[0]}, ${rgba[1]}, ${rgba[2]})`;
        b.addEventListener("click", () => {
          state.color = rgba;
          document.querySelectorAll(".swatch").forEach(x => x.classList.remove("active"));
          b.classList.add("active");
          setTool("paint");
        });
        wrap.appendChild(b);
      });
    }

    function buildFrameGrid() {
      const grid = $("frameGrid");
      state.frames.forEach((frame) => {
        const btn = document.createElement("button");
        btn.className = "frameBtn";
        btn.dataset.frame = frame.offset;
        const c = document.createElement("canvas");
        c.width = 56;
        c.height = 64;
        const label = document.createElement("span");
        label.textContent = String(frame.offset).padStart(2, "0");
        btn.appendChild(c);
        btn.appendChild(label);
        btn.addEventListener("click", () => selectFrame(frame.offset));
        grid.appendChild(btn);
        state.thumbs[frame.offset] = c;
      });
    }

    function selectFrame(offset) {
      if (state.importing) {
        state.importing = null;
        syncImportControls();
      }
      state.current = offset;
      document.querySelectorAll(".frameBtn").forEach(btn => {
        btn.classList.toggle("active", Number(btn.dataset.frame) === offset);
      });
      const frame = state.frames[offset];
      $("frameLabel").textContent = `frame ${String(offset).padStart(2, "0")} - ${frame.width}x${frame.height}`;
      canvas.width = frame.width * state.zoom;
      canvas.height = frame.height * state.zoom;
      renderEditor();
    }

    function renderEditor() {
      const frame = state.frames[state.current];
      ctx.imageSmoothingEnabled = false;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = "#00ff00";
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.drawImage(state.bases[state.current], 0, 0, frame.width * state.zoom, frame.height * state.zoom);
      ctx.drawImage(state.hairs[state.current], 0, 0, frame.width * state.zoom, frame.height * state.zoom);
      if (state.importing) {
        const item = state.importing;
        const w = item.canvas.width * item.scale;
        const h = item.canvas.height * item.scale;
        ctx.save();
        ctx.imageSmoothingEnabled = false;
        ctx.globalAlpha = 0.86;
        ctx.drawImage(
          item.canvas,
          item.x * state.zoom,
          item.y * state.zoom,
          w * state.zoom,
          h * state.zoom
        );
        ctx.globalAlpha = 1;
        ctx.strokeStyle = "rgba(255, 231, 118, .95)";
        ctx.lineWidth = 2;
        ctx.strokeRect(
          item.x * state.zoom + 1,
          item.y * state.zoom + 1,
          w * state.zoom - 2,
          h * state.zoom - 2
        );
        ctx.restore();
      }
      if (state.zoom >= 12) {
        ctx.strokeStyle = "rgba(255,255,255,.16)";
        ctx.lineWidth = 1;
        for (let x = 0; x <= frame.width; x++) {
          ctx.beginPath();
          ctx.moveTo(x * state.zoom + 0.5, 0);
          ctx.lineTo(x * state.zoom + 0.5, canvas.height);
          ctx.stroke();
        }
        for (let y = 0; y <= frame.height; y++) {
          ctx.beginPath();
          ctx.moveTo(0, y * state.zoom + 0.5);
          ctx.lineTo(canvas.width, y * state.zoom + 0.5);
          ctx.stroke();
        }
      }
    }

    function renderThumb(offset) {
      const frame = state.frames[offset];
      const c = state.thumbs[offset];
      if (!c || !state.bases[offset] || !state.hairs[offset]) return;
      const t = c.getContext("2d");
      t.imageSmoothingEnabled = false;
      t.clearRect(0, 0, c.width, c.height);
      t.fillStyle = "#00ff00";
      t.fillRect(0, 0, c.width, c.height);
      const scale = Math.floor(Math.min(c.width / frame.width, c.height / frame.height));
      const x = Math.floor((c.width - frame.width * scale) / 2);
      const y = Math.floor((c.height - frame.height * scale) / 2);
      t.drawImage(state.bases[offset], x, y, frame.width * scale, frame.height * scale);
      t.drawImage(state.hairs[offset], x, y, frame.width * scale, frame.height * scale);
    }

    function setTool(tool) {
      state.tool = tool;
      $("paintTool").classList.toggle("active", tool === "paint");
      $("eraseTool").classList.toggle("active", tool === "erase");
      $("sampleTool").classList.toggle("active", tool === "sample");
    }

    function pointFromEvent(ev) {
      const rect = canvas.getBoundingClientRect();
      return {
        x: Math.floor((ev.clientX - rect.left) / state.zoom),
        y: Math.floor((ev.clientY - rect.top) / state.zoom),
      };
    }

    function markDirty(offset = state.current) {
      state.dirty.add(offset);
      renderThumb(offset);
      if (offset === state.current) renderEditor();
    }

    function paintAt(x, y) {
      const frame = state.frames[state.current];
      if (x < 0 || y < 0 || x >= frame.width || y >= frame.height) return;
      const size = Math.max(1, Math.min(4, Number($("brushSize").value) || 1));
      const layer = state.hairs[state.current];
      const hctx = layer.getContext("2d");
      hctx.imageSmoothingEnabled = false;
      if (state.tool === "sample") {
        const data = hctx.getImageData(x, y, 1, 1).data;
        if (data[3] >= 128) state.color = [data[0], data[1], data[2], 255];
        setTool("paint");
        return;
      }
      const start = -Math.floor((size - 1) / 2);
      hctx.fillStyle = state.tool === "erase"
        ? "rgba(0,0,0,0)"
        : `rgba(${state.color[0]},${state.color[1]},${state.color[2]},1)`;
      if (state.tool === "erase") {
        hctx.clearRect(x + start, y + start, size, size);
      } else {
        hctx.fillRect(x + start, y + start, size, size);
      }
      markDirty();
    }

    function nudge(dx, dy) {
      if (state.importing) {
        state.importing.x += dx;
        state.importing.y += dy;
        syncImportControls();
        renderEditor();
        return;
      }
      const frame = state.frames[state.current];
      const current = state.hairs[state.current];
      const next = makeLayer(frame.width, frame.height);
      next.getContext("2d").drawImage(current, dx, dy);
      state.hairs[state.current] = next;
      markDirty();
    }

    function copyFrame(sourceOffset) {
      if (!state.hairs[sourceOffset]) return;
      const frame = state.frames[state.current];
      const next = makeLayer(frame.width, frame.height);
      next.getContext("2d").drawImage(state.hairs[sourceOffset], 0, 0);
      state.hairs[state.current] = next;
      markDirty();
    }

    function clearCurrent() {
      const layer = state.hairs[state.current];
      layer.getContext("2d").clearRect(0, 0, layer.width, layer.height);
      markDirty();
    }

    async function saveFrame(offset = state.current) {
      const png = state.hairs[offset].toDataURL("image/png");
      const res = await fetch(`/api/save-frame/${offset}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ png }),
      });
      if (!res.ok) throw new Error(await res.text());
      state.dirty.delete(offset);
      renderThumb(offset);
      if (offset === state.current) renderEditor();
    }

    async function saveAllFrames() {
      const frames = state.frames.map(frame => ({
        offset: frame.offset,
        png: state.hairs[frame.offset].toDataURL("image/png"),
      }));
      const res = await fetch("/api/save-all", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ frames }),
      });
      if (!res.ok) throw new Error(await res.text());
      state.dirty.clear();
      setStatus("Saved all frames", "ok");
    }

    async function refreshPreview(saveFirst = true) {
      if (saveFirst) await saveAllFrames();
      $("previewImage").src = "/api/preview.png?t=" + Date.now();
    }

    async function validate() {
      await saveAllFrames();
      const res = await fetch("/api/validate");
      const data = await res.json();
      $("validationOutput").textContent = data.text;
      setStatus(data.failed ? "Validation has issues" : "Validation passed", data.failed ? "warn" : "ok");
    }

    function trimImageToAlpha(img, threshold = 32) {
      const source = makeLayer(img.naturalWidth || img.width, img.naturalHeight || img.height);
      const sctx = source.getContext("2d");
      sctx.imageSmoothingEnabled = false;
      sctx.drawImage(img, 0, 0);
      const data = sctx.getImageData(0, 0, source.width, source.height);
      let minX = source.width;
      let minY = source.height;
      let maxX = -1;
      let maxY = -1;
      for (let y = 0; y < source.height; y++) {
        for (let x = 0; x < source.width; x++) {
          const a = data.data[(y * source.width + x) * 4 + 3];
          if (a >= threshold) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
          }
        }
      }
      if (maxX < minX || maxY < minY) return source;
      const trimmed = makeLayer(maxX - minX + 1, maxY - minY + 1);
      const tctx = trimmed.getContext("2d");
      tctx.imageSmoothingEnabled = false;
      tctx.drawImage(
        source,
        minX,
        minY,
        trimmed.width,
        trimmed.height,
        0,
        0,
        trimmed.width,
        trimmed.height
      );
      return trimmed;
    }

    function fitImportToFrame() {
      if (!state.importing) return;
      const frame = state.frames[state.current];
      const item = state.importing;
      const targetW = Math.max(1, frame.width - 2);
      const targetH = Math.max(1, Math.ceil(frame.height * 0.68));
      item.scale = Math.min(targetW / item.canvas.width, targetH / item.canvas.height);
      const w = item.canvas.width * item.scale;
      item.x = Math.round((frame.width - w) / 2);
      item.y = 0;
      syncImportControls();
      renderEditor();
    }

    function syncImportControls() {
      $("importControls").classList.toggle("hidden", !state.importing);
    }

    function scaleImport(multiplier) {
      if (!state.importing) return;
      const item = state.importing;
      const oldW = item.canvas.width * item.scale;
      const oldH = item.canvas.height * item.scale;
      const centerX = item.x + oldW / 2;
      const centerY = item.y + oldH / 2;
      item.scale = Math.max(0.002, Math.min(0.08, item.scale * multiplier));
      item.x = Math.round(centerX - (item.canvas.width * item.scale) / 2);
      item.y = Math.round(centerY - (item.canvas.height * item.scale) / 2);
      syncImportControls();
      renderEditor();
    }

    async function importCurrent(file) {
      const img = await new Promise((resolve, reject) => {
        const i = new Image();
        i.onload = () => resolve(i);
        i.onerror = reject;
        i.src = URL.createObjectURL(file);
      });
      state.importing = { canvas: trimImageToAlpha(img), x: 0, y: 0, scale: 1 };
      fitImportToFrame();
      setStatus(`Placing ${file.name}; nudge with arrows, scale with -/+, then Apply`, "warn");
    }

    function applyImport() {
      if (!state.importing) return;
      const frame = state.frames[state.current];
      const item = state.importing;
      const next = makeLayer(frame.width, frame.height);
      const nctx = next.getContext("2d");
      nctx.imageSmoothingEnabled = false;
      nctx.drawImage(state.hairs[state.current], 0, 0);
      nctx.drawImage(
        item.canvas,
        item.x,
        item.y,
        item.canvas.width * item.scale,
        item.canvas.height * item.scale
      );
      state.hairs[state.current] = next;
      state.importing = null;
      syncImportControls();
      markDirty();
      setStatus(`Imported art into frame ${state.current}`, "ok");
    }

    function cancelImport() {
      state.importing = null;
      syncImportControls();
      renderEditor();
      setStatus("Import cancelled", "ok");
    }

    canvas.addEventListener("pointerdown", (ev) => {
      if (state.importing) return;
      state.drawing = true;
      canvas.setPointerCapture(ev.pointerId);
      const p = pointFromEvent(ev);
      paintAt(p.x, p.y);
    });
    canvas.addEventListener("pointermove", (ev) => {
      if (!state.drawing) return;
      if (state.importing) return;
      const p = pointFromEvent(ev);
      paintAt(p.x, p.y);
    });
    canvas.addEventListener("pointerup", () => { state.drawing = false; });
    canvas.addEventListener("pointerleave", () => { state.drawing = false; });

    $("paintTool").addEventListener("click", () => setTool("paint"));
    $("eraseTool").addEventListener("click", () => setTool("erase"));
    $("sampleTool").addEventListener("click", () => setTool("sample"));
    $("nudgeLeft").addEventListener("click", () => nudge(-1, 0));
    $("nudgeRight").addEventListener("click", () => nudge(1, 0));
    $("nudgeUp").addEventListener("click", () => nudge(0, -1));
    $("nudgeDown").addEventListener("click", () => nudge(0, 1));
    $("copyPrev").addEventListener("click", () => copyFrame(Math.max(0, state.current - 1)));
    $("copyNext").addEventListener("click", () => copyFrame(Math.min(state.frames.length - 1, state.current + 1)));
    $("clearFrame").addEventListener("click", clearCurrent);
    $("saveFrame").addEventListener("click", async () => {
      await saveFrame();
      setStatus(`Saved frame ${state.current}`, "ok");
    });
    $("saveAll").addEventListener("click", saveAllFrames);
    $("refreshPreview").addEventListener("click", async () => {
      await refreshPreview(true);
      setStatus("Preview refreshed", "ok");
    });
    $("validateButton").addEventListener("click", validate);
    $("importButton").addEventListener("click", () => $("importFrame").click());
    $("importFrame").addEventListener("change", async (ev) => {
      if (ev.target.files && ev.target.files[0]) await importCurrent(ev.target.files[0]);
      ev.target.value = "";
    });
    $("importFit").addEventListener("click", fitImportToFrame);
    $("importScaleDown").addEventListener("click", () => scaleImport(0.9));
    $("importScaleUp").addEventListener("click", () => scaleImport(1.1));
    $("importApply").addEventListener("click", applyImport);
    $("importCancel").addEventListener("click", cancelImport);

    window.addEventListener("keydown", async (ev) => {
      if (state.importing) {
        if (ev.key === "ArrowLeft") { ev.preventDefault(); nudge(-1, 0); }
        if (ev.key === "ArrowRight") { ev.preventDefault(); nudge(1, 0); }
        if (ev.key === "ArrowUp") { ev.preventDefault(); nudge(0, -1); }
        if (ev.key === "ArrowDown") { ev.preventDefault(); nudge(0, 1); }
        if (ev.key === "Enter") { ev.preventDefault(); applyImport(); }
        if (ev.key === "Escape") { ev.preventDefault(); cancelImport(); }
      }
      if (ev.key === "[" && state.current > 0) selectFrame(state.current - 1);
      if (ev.key === "]" && state.current < state.frames.length - 1) selectFrame(state.current + 1);
      if ((ev.metaKey || ev.ctrlKey) && ev.key.toLowerCase() === "s") {
        ev.preventDefault();
        await saveFrame();
        setStatus(`Saved frame ${state.current}`, "ok");
      }
    });

    init().catch((err) => {
      console.error(err);
      setStatus(err.message || String(err), "warn");
    });
  </script>
</body>
</html>
"""


def sanitize_hair_layer(img: Image.Image, size: tuple[int, int]) -> Image.Image:
    if img.size != size:
        img = img.resize(size, Image.Resampling.NEAREST)
    return normalize_wig_image(img, tolerance=0)


def image_png_bytes(img: Image.Image) -> bytes:
    buf = BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


def parse_data_url_png(data_url: str) -> Image.Image:
    if "," not in data_url:
        raise ValueError("expected data URL")
    return Image.open(BytesIO(base64.b64decode(data_url.split(",", 1)[1]))).convert("RGBA")


def ensure_editor_hair_frames(hair_dir: Path, manifest: dict, base_frames: list[SpriteFrame]) -> None:
    hair_dir.mkdir(parents=True, exist_ok=True)
    for frame_info, base in zip(manifest["frames"], base_frames):
        offset = frame_info["offset"]
        img_path = hair_dir / f"frame_{offset:02d}.png"
        sidecar_path = hair_dir / f"frame_{offset:02d}.png.json"
        if not img_path.exists():
            Image.new("RGBA", base.image.size, (0, 0, 0, 0)).save(img_path)
        if not sidecar_path.exists():
            sidecar_path.write_text(json.dumps(base.sidecar, indent=2) + "\n")


def write_editor_hair_frame(hair_dir: Path, offset: int, image: Image.Image, sidecar: dict) -> None:
    hair_dir.mkdir(parents=True, exist_ok=True)
    image.save(hair_dir / f"frame_{offset:02d}.png")
    (hair_dir / f"frame_{offset:02d}.png.json").write_text(json.dumps(sidecar, indent=2) + "\n")


def cmd_wig_editor(args: argparse.Namespace) -> int:
    args.manifest = args.manifest.resolve()
    args.hair_dir = args.hair_dir.resolve()
    args.archive = args.archive.resolve()
    manifest = manifest_from_path(args.manifest)
    base_frames = load_wig_base_frames(manifest, args.archive)
    ensure_editor_hair_frames(args.hair_dir, manifest, base_frames)
    sidecars = {info["offset"]: frame.sidecar for info, frame in zip(manifest["frames"], base_frames)}
    sizes = {info["offset"]: base_frames[n].image.size for n, info in enumerate(manifest["frames"])}

    def read_hair(offset: int) -> Image.Image:
        path = args.hair_dir / f"frame_{offset:02d}.png"
        return sanitize_hair_layer(Image.open(path).convert("RGBA"), sizes[offset])

    def validation_text() -> tuple[str, bool]:
        hair_frames = load_wig_hair_frames(args.hair_dir, manifest, bg_tolerance=0)
        failed = False
        totals = {"transparent": 0, "hairMask": 0, "nonMask": 0, "baseSkinOverlap": 0, "baseDetailOverlap": 0}
        lines: list[str] = []
        for frame_info, base, hair in zip(manifest["frames"], base_frames, hair_frames):
            stats = wig_mask_stats(hair.image, base.image)
            for key, value in stats.items():
                totals[key] += value
            notes = []
            if stats["nonMask"] > args.max_non_mask:
                failed = True
                notes.append(f"nonMask>{args.max_non_mask}")
            if stats["baseDetailOverlap"] > args.max_detail_overlap:
                failed = True
                notes.append(f"detailOverlap>{args.max_detail_overlap}")
            lines.append(
                f"frame {frame_info['offset']:02d} hair={stats['hairMask']:3d} "
                f"nonMask={stats['nonMask']:3d} skinOverlap={stats['baseSkinOverlap']:3d} "
                f"detailOverlap={stats['baseDetailOverlap']:3d}"
                + (f"  {'; '.join(notes)}" if notes else "")
            )
        lines.append("totals " + " ".join(f"{key}={value}" for key, value in totals.items()))
        return "\n".join(lines), failed

    class Handler(BaseHTTPRequestHandler):
        def send_bytes(self, data: bytes, content_type: str, status: int = 200) -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def send_json(self, payload: dict, status: int = 200) -> None:
            self.send_bytes(json.dumps(payload).encode("utf-8"), "application/json; charset=utf-8", status)

        def send_error_text(self, text: str, status: int = 500) -> None:
            self.send_bytes((text + "\n").encode("utf-8"), "text/plain; charset=utf-8", status)

        def do_GET(self) -> None:
            path = urlparse(self.path).path
            try:
                if path == "/":
                    self.send_bytes(WIG_EDITOR_HTML.encode("utf-8"), "text/html; charset=utf-8")
                    return
                if path == "/api/state":
                    self.send_json({
                        "manifest": str(args.manifest),
                        "hairDir": str(args.hair_dir),
                        "zoom": args.editor_zoom,
                        "frames": [
                            {
                                "offset": info["offset"],
                                "width": sizes[info["offset"]][0],
                                "height": sizes[info["offset"]][1],
                                "baseUrl": f"/frame/{info['offset']}/base.png",
                                "hairUrl": f"/frame/{info['offset']}/hair.png",
                            }
                            for info in manifest["frames"]
                        ],
                    })
                    return
                if path == "/api/preview.png":
                    image = render_wig_preview(args)
                    self.send_bytes(image_png_bytes(image), "image/png")
                    return
                if path == "/api/validate":
                    text, failed = validation_text()
                    self.send_json({"text": text, "failed": failed})
                    return
                if path.startswith("/frame/") and path.endswith(".png"):
                    _, _, offset_text, name = path.split("/", 3)
                    offset = int(offset_text)
                    if name == "base.png":
                        frame = base_frames[offset]
                        self.send_bytes(image_png_bytes(frame.image), "image/png")
                        return
                    if name == "hair.png":
                        self.send_bytes(image_png_bytes(read_hair(offset)), "image/png")
                        return
                self.send_error_text("not found", 404)
            except Exception as exc:
                self.send_error_text(str(exc), 500)

        def do_POST(self) -> None:
            path = urlparse(self.path).path
            try:
                length = int(self.headers.get("Content-Length", "0"))
                payload = json.loads(self.rfile.read(length).decode("utf-8"))
                if path.startswith("/api/save-frame/"):
                    offset = int(path.rsplit("/", 1)[1])
                    image = sanitize_hair_layer(parse_data_url_png(payload["png"]), sizes[offset])
                    write_editor_hair_frame(args.hair_dir, offset, image, sidecars[offset])
                    self.send_json({"ok": True, "offset": offset})
                    return
                if path == "/api/save-all":
                    for item in payload.get("frames", []):
                        offset = int(item["offset"])
                        image = sanitize_hair_layer(parse_data_url_png(item["png"]), sizes[offset])
                        write_editor_hair_frame(args.hair_dir, offset, image, sidecars[offset])
                    self.send_json({"ok": True})
                    return
                self.send_error_text("not found", 404)
            except Exception as exc:
                self.send_error_text(str(exc), 500)

        def log_message(self, format: str, *values: object) -> None:
            return

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    host, port = server.server_address
    print(f"wig editor: http://{host}:{port}")
    print(f"hair frames: {args.hair_dir}")
    print("press Ctrl-C to stop")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


def cmd_preview(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    frames = load_frame_dir(args.frames_dir, manifest)
    hair_colours = [parse_colour(c) for c in args.hair]
    skin = parse_colour(args.skin)
    scale = args.scale
    max_w = max(frame.image.width for frame in frames)
    max_h = max(frame.image.height for frame in frames)
    cols = manifest["columns"]
    rows = len(hair_colours) * manifest["rows"]
    pad = 8
    cell_w = max_w * scale + pad * 2
    cell_h = max_h * scale + pad * 2
    out = Image.new("RGBA", (cols * cell_w, rows * cell_h), (32, 32, 32, 255))
    draw = ImageDraw.Draw(out)
    for hair_idx, hair in enumerate(hair_colours):
        for offset, frame in enumerate(frames):
            col = offset % cols
            row = hair_idx * manifest["rows"] + offset // cols
            sprite = recolor_sprite(frame.image, hair, skin)
            sprite = sprite.resize((frame.image.width * scale, frame.image.height * scale), Image.Resampling.NEAREST)
            x = col * cell_w + pad + (max_w * scale - sprite.width) // 2
            y = row * cell_h + pad + (max_h * scale - sprite.height) // 2
            out.alpha_composite(sprite, (x, y))
        draw.text((4, hair_idx * manifest["rows"] * cell_h + 4), f"hair #{hair[0]:02x}{hair[1]:02x}{hair[2]:02x}", fill=(255, 255, 255, 255))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    out.save(args.out)
    print(f"wrote preview: {args.out}")
    return 0


def cmd_pack(args: argparse.Namespace) -> int:
    manifest = manifest_from_path(args.manifest)
    frames = load_frame_dir(args.frames_dir, manifest)
    start = args.start_index
    replacements = {
        str(start + offset): encode_sprite(frame.image, frame.sidecar)
        for offset, frame in enumerate(frames)
    }
    archive = args.archive.resolve()
    if args.commit:
        bak = archive.with_suffix(archive.suffix + ".bak")
        if not bak.exists():
            print(f"refusing to commit: backup not found at {bak}", file=sys.stderr)
            print(f"create one first: cp {archive} {bak}", file=sys.stderr)
            return 1
        fd, tmp_name = tempfile.mkstemp(suffix=".orsc", dir=str(archive.parent))
        os.close(fd)
        tmp = Path(tmp_name)
        try:
            rewrite_archive(archive, tmp, replacements)
            os.replace(tmp, archive)
        except Exception:
            tmp.unlink(missing_ok=True)
            raise
        print(f"committed {len(replacements)} frames to {archive} at {start}..{start + len(frames) - 1}")
    else:
        out = args.out_archive or (args.frames_dir / f"dryrun_{start}_{start + len(frames) - 1}.orsc")
        out.parent.mkdir(parents=True, exist_ok=True)
        rewrite_archive(archive, out, replacements)
        print(f"dry-run wrote {out}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Voidscape hairstyle sprite grid workflow")
    sub = parser.add_subparsers(dest="command", required=True)

    boot = sub.add_parser("bootstrap", help="extract frames, grids, manifest, and ChatGPT prompt")
    boot.add_argument("--style", default="head1", choices=sorted(HEAD_STYLES))
    boot.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    boot.add_argument("--out-dir", type=Path, required=True)
    boot.add_argument("--theme", default="a new RSC-authentic hairstyle with a clean silhouette")
    boot.add_argument("--scale", type=int, default=8)
    boot.add_argument("--cols", type=int, default=DEFAULT_COLS)
    boot.add_argument("--pad", type=int, default=12)
    boot.add_argument("--gutter", type=int, default=8)
    boot.add_argument("--labels", action="store_true", help="reserve label height in the AI grid too")
    boot.set_defaults(func=cmd_bootstrap)

    imp = sub.add_parser("import-grid", help="slice a returned grid back into 18 frames")
    imp.add_argument("--manifest", type=Path, required=True)
    imp.add_argument("--grid", type=Path, required=True)
    imp.add_argument("--out-dir", type=Path, required=True)
    imp.add_argument("--bg-tolerance", type=int, default=18)
    imp.add_argument("--resample", choices=("nearest", "box"), default="nearest")
    imp.add_argument("--no-normalize", dest="normalize", action="store_false")
    imp.set_defaults(func=cmd_import_grid, normalize=True)

    val = sub.add_parser("validate", help="validate imported frames against RSC mask rules")
    val.add_argument("--manifest", type=Path, required=True)
    val.add_argument("--frames-dir", type=Path, required=True)
    val.add_argument("--max-full-colour", type=int, default=12)
    val.set_defaults(func=cmd_validate)

    prev = sub.add_parser("preview", help="render a recolored preview grid")
    prev.add_argument("--manifest", type=Path, required=True)
    prev.add_argument("--frames-dir", type=Path, required=True)
    prev.add_argument("--out", type=Path, required=True)
    prev.add_argument("--scale", type=int, default=8)
    prev.add_argument("--skin", default="#ecded0")
    prev.add_argument("--hair", nargs="+", default=["#ffc030", "#303030", "#6a0dad"])
    prev.set_defaults(func=cmd_preview)

    player_prev = sub.add_parser("player-preview", help="render imported heads on the base player body at client scale")
    player_prev.add_argument("--manifest", type=Path, required=True)
    player_prev.add_argument("--frames-dir", type=Path, required=True)
    player_prev.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    player_prev.add_argument("--out", type=Path, required=True)
    player_prev.add_argument("--scale", type=int, default=4)
    player_prev.add_argument("--cols", type=int, default=6)
    player_prev.add_argument("--skin", default="#ecded0")
    player_prev.add_argument("--top", default="#0080ff")
    player_prev.add_argument("--bottom", default="#ffffff")
    player_prev.add_argument("--background", default="#123c1b")
    player_prev.add_argument("--hair", nargs="+", default=["#ffc030", "#303030", "#6a0dad"])
    player_prev.add_argument("--no-actual-strip", dest="actual_strip", action="store_false")
    player_prev.set_defaults(func=cmd_player_preview, actual_strip=True)

    wig_boot = sub.add_parser("wig-bootstrap", help="extract a locked bald base plus a hair-only canvas")
    wig_boot.add_argument("--base-style", default="head4", choices=sorted(HEAD_STYLES), help="locked head base")
    wig_boot.add_argument("--source-style", default="head1", choices=sorted(HEAD_STYLES), help="existing hair source")
    wig_boot.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_boot.add_argument("--out-dir", type=Path, required=True)
    wig_boot.add_argument("--theme", default="a new RSC-authentic hairstyle with a clean silhouette")
    wig_boot.add_argument("--scale", type=int, default=8)
    wig_boot.add_argument("--cols", type=int, default=DEFAULT_COLS)
    wig_boot.add_argument("--pad", type=int, default=12)
    wig_boot.add_argument("--gutter", type=int, default=8)
    wig_boot.add_argument("--labels", action="store_true", help="reserve label height in the AI grid too")
    wig_boot.set_defaults(func=cmd_wig_bootstrap)

    wig_imp = sub.add_parser("import-wig-grid", help="slice a returned hair-only grid into transparent wig frames")
    wig_imp.add_argument("--manifest", type=Path, required=True)
    wig_imp.add_argument("--grid", type=Path, required=True)
    wig_imp.add_argument("--out-dir", type=Path, required=True)
    wig_imp.add_argument("--bg-tolerance", type=int, default=18)
    wig_imp.add_argument("--resample", choices=("nearest", "box"), default="nearest")
    wig_imp.set_defaults(func=cmd_import_wig_grid)

    wig_val = sub.add_parser("wig-validate", help="validate hair-only wig frames against the locked base")
    wig_val.add_argument("--manifest", type=Path, required=True)
    wig_val.add_argument("--hair-dir", type=Path, required=True)
    wig_val.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_val.add_argument("--bg-tolerance", type=int, default=0)
    wig_val.add_argument("--max-non-mask", type=int, default=0)
    wig_val.add_argument("--max-detail-overlap", type=int, default=12)
    wig_val.set_defaults(func=cmd_wig_validate)

    wig_bake = sub.add_parser("wig-bake", help="bake transparent hair onto the locked base head frames")
    wig_bake.add_argument("--manifest", type=Path, required=True)
    wig_bake.add_argument("--hair-dir", type=Path, required=True)
    wig_bake.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_bake.add_argument("--out-dir", type=Path, required=True)
    wig_bake.add_argument("--bg-tolerance", type=int, default=0)
    wig_bake.set_defaults(func=cmd_wig_bake)

    wig_prev = sub.add_parser("wig-preview", help="render transparent wig frames on the locked base and player body")
    wig_prev.add_argument("--manifest", type=Path, required=True)
    wig_prev.add_argument("--hair-dir", type=Path, required=True)
    wig_prev.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_prev.add_argument("--out", type=Path, required=True)
    wig_prev.add_argument("--bg-tolerance", type=int, default=0)
    wig_prev.add_argument("--scale", type=int, default=4)
    wig_prev.add_argument("--cols", type=int, default=6)
    wig_prev.add_argument("--skin", default="#ecded0")
    wig_prev.add_argument("--top", default="#0080ff")
    wig_prev.add_argument("--bottom", default="#ffffff")
    wig_prev.add_argument("--background", default="#123c1b")
    wig_prev.add_argument("--hair", nargs="+", default=["#ffc030", "#303030", "#6a0dad"])
    wig_prev.add_argument("--no-actual-strip", dest="actual_strip", action="store_false")
    wig_prev.set_defaults(func=cmd_wig_preview, actual_strip=True)

    wig_live = sub.add_parser("wig-live", help="serve a live auto-refreshing wig preview in a browser")
    wig_live.add_argument("--manifest", type=Path, required=True)
    wig_live.add_argument("--hair-dir", type=Path, required=True)
    wig_live.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_live.add_argument("--bg-tolerance", type=int, default=0)
    wig_live.add_argument("--scale", type=int, default=4)
    wig_live.add_argument("--cols", type=int, default=6)
    wig_live.add_argument("--skin", default="#ecded0")
    wig_live.add_argument("--top", default="#0080ff")
    wig_live.add_argument("--bottom", default="#ffffff")
    wig_live.add_argument("--background", default="#123c1b")
    wig_live.add_argument("--hair", nargs="+", default=["#ffc030", "#303030", "#6a0dad"])
    wig_live.add_argument("--no-actual-strip", dest="actual_strip", action="store_false")
    wig_live.add_argument("--host", default="127.0.0.1")
    wig_live.add_argument("--port", type=int, default=8765)
    wig_live.add_argument("--refresh-ms", type=int, default=1000)
    wig_live.set_defaults(func=cmd_wig_live, actual_strip=True)

    wig_editor = sub.add_parser("wig-editor", help="serve a manual pixel editor for locked-base wig frames")
    wig_editor.add_argument("--manifest", type=Path, required=True)
    wig_editor.add_argument("--hair-dir", type=Path, required=True)
    wig_editor.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    wig_editor.add_argument("--bg-tolerance", type=int, default=0)
    wig_editor.add_argument("--scale", type=int, default=4, help="player-preview scale")
    wig_editor.add_argument("--cols", type=int, default=6)
    wig_editor.add_argument("--skin", default="#ecded0")
    wig_editor.add_argument("--top", default="#0080ff")
    wig_editor.add_argument("--bottom", default="#ffffff")
    wig_editor.add_argument("--background", default="#123c1b")
    wig_editor.add_argument("--hair", nargs="+", default=["#ffc030", "#303030", "#6a0dad"])
    wig_editor.add_argument("--no-actual-strip", dest="actual_strip", action="store_false")
    wig_editor.add_argument("--host", default="127.0.0.1")
    wig_editor.add_argument("--port", type=int, default=8787)
    wig_editor.add_argument("--editor-zoom", type=int, default=24)
    wig_editor.add_argument("--max-non-mask", type=int, default=0)
    wig_editor.add_argument("--max-detail-overlap", type=int, default=12)
    wig_editor.set_defaults(func=cmd_wig_editor, actual_strip=True)

    pack = sub.add_parser("pack", help="dry-run or commit frames into Authentic_Sprites.orsc")
    pack.add_argument("--manifest", type=Path, required=True)
    pack.add_argument("--frames-dir", type=Path, required=True)
    pack.add_argument("--start-index", type=int, required=True)
    pack.add_argument("--archive", type=Path, default=DEFAULT_ARCHIVE)
    pack.add_argument("--out-archive", type=Path)
    pack.add_argument("--commit", action="store_true")
    pack.set_defaults(func=cmd_pack)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
