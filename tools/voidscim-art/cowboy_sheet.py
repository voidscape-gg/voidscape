#!/usr/bin/env python3
"""Build and import strict Cowboy Hat sprite grids.

This helper is intentionally art-only: it never edits definitions or archives.
It lays the 18 authentic wizard-hat frames out as a 6x3 ImageGen reference,
then slices a returned 6x3 sheet into the exact original frame dimensions,
removes the green screen, quantizes to the approved leather palette, copies the
per-frame sidecars, and writes an enlarged proof sheet.
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

from PIL import Image, ImageDraw


FRAME_COUNT = 18
COLS = 6
ROWS = 3
GREEN = (0, 255, 0, 255)
PROOF_BG = (28, 24, 22, 255)
PALETTE = (
    (0x00, 0x00, 0x01),
    (0x24, 0x15, 0x0D),
    (0x3E, 0x25, 0x16),
    (0x6B, 0x43, 0x25),
    (0x9B, 0x67, 0x36),
    (0xC2, 0x8B, 0x4A),
    (0x16, 0x10, 0x0D),
    (0xD6, 0xB4, 0x56),
)

# ImageGen supplies design views, while authentic RSC movement comes from the
# per-frame sidecars. Reusing one approved view for each three-frame direction
# prevents generated shape drift from flickering during walking.
CANONICAL_SOURCE_CELLS = (
    0, 0, 0,       # front
    2, 2, 2,       # front three-quarter
    3, 3, 3,       # side
    4, 4, 4,       # rear three-quarter
    15, 15, 15,    # rear
    6, 6, 6,       # combat-facing
)


def frame_path(root: Path, offset: int) -> Path:
    return root / f"frame_{offset:02d}.png"


def load_frames(root: Path) -> list[Image.Image]:
    frames: list[Image.Image] = []
    for offset in range(FRAME_COUNT):
        path = frame_path(root, offset)
        if not path.exists():
            raise FileNotFoundError(path)
        frames.append(Image.open(path).convert("RGBA"))
    return frames


def build_reference(frames_dir: Path, out: Path, cell: int = 128, scale: int = 4) -> None:
    frames = load_frames(frames_dir)
    sheet = Image.new("RGBA", (COLS * cell, ROWS * cell), GREEN)
    for offset, frame in enumerate(frames):
        sprite = frame.resize((frame.width * scale, frame.height * scale), Image.Resampling.NEAREST)
        col, row = offset % COLS, offset // COLS
        x = col * cell + (cell - sprite.width) // 2
        y = row * cell + (cell - sprite.height) // 2
        sheet.alpha_composite(sprite, (x, y))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(out)


def is_green(pixel: tuple[int, int, int, int], tolerance: int) -> bool:
    r, g, b, a = pixel
    if a < 128:
        return True
    distance = (r * r + (255 - g) * (255 - g) + b * b) ** 0.5
    return distance <= tolerance or (g >= 120 and g > r * 1.35 and g > b * 1.35)


def remove_green(image: Image.Image, tolerance: int) -> Image.Image:
    src = image.convert("RGBA")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    for x in range(src.width):
        for y in range(src.height):
            pixel = src.getpixel((x, y))
            if not is_green(pixel, tolerance):
                out.putpixel((x, y), (pixel[0], pixel[1], pixel[2], 255))
    return out


def nearest_palette(rgb: tuple[int, int, int]) -> tuple[int, int, int]:
    return min(PALETTE, key=lambda colour: sum((rgb[i] - colour[i]) ** 2 for i in range(3)))


def keep_largest_component(image: Image.Image) -> Image.Image:
    """Remove disconnected generation debris while preserving the hat silhouette."""
    rgba = image.convert("RGBA")
    opaque = {
        (x, y)
        for x in range(rgba.width)
        for y in range(rgba.height)
        if rgba.getpixel((x, y))[3] >= 128
    }
    components: list[set[tuple[int, int]]] = []
    while opaque:
        start = opaque.pop()
        component = {start}
        stack = [start]
        while stack:
            x, y = stack.pop()
            for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                neighbour = (x + dx, y + dy)
                if neighbour in opaque:
                    opaque.remove(neighbour)
                    component.add(neighbour)
                    stack.append(neighbour)
        components.append(component)
    if not components:
        return rgba
    keep = max(components, key=len)
    out = Image.new("RGBA", rgba.size, (0, 0, 0, 0))
    for point in keep:
        out.putpixel(point, rgba.getpixel(point))
    return out


def fit_cell(cell: Image.Image, target_size: tuple[int, int], tolerance: int) -> Image.Image:
    keyed = remove_green(cell, tolerance)
    bbox = keyed.getbbox()
    if bbox is None:
        raise ValueError("generated grid cell contains no hat pixels")
    crop = keyed.crop(bbox)
    target_w, target_h = target_size
    factor = min(target_w / crop.width, target_h / crop.height)
    width = max(1, min(target_w, round(crop.width * factor)))
    height = max(1, min(target_h, round(crop.height * factor)))
    small = crop.resize((width, height), Image.Resampling.BOX)
    quantized = Image.new("RGBA", small.size, (0, 0, 0, 0))
    for x in range(small.width):
        for y in range(small.height):
            r, g, b, a = small.getpixel((x, y))
            if a >= 128:
                colour = nearest_palette((r, g, b))
                quantized.putpixel((x, y), (*colour, 255))
    out = Image.new("RGBA", target_size, (0, 0, 0, 0))
    out.alpha_composite(quantized, ((target_w - width) // 2, target_h - height))
    return keep_largest_component(out)


def cell_box(size: tuple[int, int], col: int, row: int) -> tuple[int, int, int, int]:
    width, height = size
    return (
        round(col * width / COLS),
        round(row * height / ROWS),
        round((col + 1) * width / COLS),
        round((row + 1) * height / ROWS),
    )


def grid_cell(grid: Image.Image, offset: int) -> Image.Image:
    col, row = offset % COLS, offset // COLS
    return grid.crop(cell_box(grid.size, col, row))


def write_proof(frames: list[Image.Image], out: Path, scale: int = 8) -> None:
    max_w = max(frame.width for frame in frames)
    max_h = max(frame.height for frame in frames)
    label_h = 14
    cell_w = max_w * scale + 16
    cell_h = max_h * scale + label_h + 16
    sheet = Image.new("RGBA", (COLS * cell_w, ROWS * cell_h), PROOF_BG)
    draw = ImageDraw.Draw(sheet)
    for offset, frame in enumerate(frames):
        col, row = offset % COLS, offset // COLS
        zoom = frame.resize((frame.width * scale, frame.height * scale), Image.Resampling.NEAREST)
        x = col * cell_w + (cell_w - zoom.width) // 2
        y = row * cell_h + label_h + (cell_h - label_h - zoom.height) // 2
        sheet.alpha_composite(zoom, (x, y))
        draw.text((col * cell_w + 5, row * cell_h + 3), f"{offset:02d}", fill=(235, 224, 200, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)


def import_grid(grid_path: Path, reference_dir: Path, out_dir: Path, proof: Path,
                tolerance: int) -> None:
    grid = Image.open(grid_path).convert("RGBA")
    references = load_frames(reference_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    frames: list[Image.Image] = []
    for offset, reference in enumerate(references):
        source_cell = CANONICAL_SOURCE_CELLS[offset]
        output = fit_cell(grid_cell(grid, source_cell), reference.size, tolerance)
        output_path = frame_path(out_dir, offset)
        output.save(output_path)
        sidecar = frame_path(reference_dir, offset).with_suffix(".png.json")
        if not sidecar.exists():
            raise FileNotFoundError(sidecar)
        shutil.copy2(sidecar, output_path.with_suffix(".png.json"))
        frames.append(output)
    write_proof(frames, proof)
    manifest = {
        "format": "voidscape-cowboy-sheet-v1",
        "sourceGrid": str(grid_path),
        "referenceDir": str(reference_dir),
        "frameCount": FRAME_COUNT,
        "canonicalSourceCells": list(CANONICAL_SOURCE_CELLS),
        "palette": ["#%02X%02X%02X" % colour for colour in PALETTE],
    }
    (out_dir / "import_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


def make_icon(grid_path: Path, out: Path, source_cell: int, tolerance: int) -> None:
    grid = Image.open(grid_path).convert("RGBA")
    keyed = remove_green(grid_cell(grid, source_cell), tolerance)
    bbox = keyed.getbbox()
    if bbox is None:
        raise ValueError(f"generated grid cell {source_cell} contains no hat pixels")
    crop = keyed.crop(bbox)
    factor = min(44 / crop.width, 28 / crop.height)
    width = max(1, round(crop.width * factor))
    height = max(1, round(crop.height * factor))
    small = crop.resize((width, height), Image.Resampling.BOX)
    icon = Image.new("RGBA", (48, 32), (0, 0, 0, 0))
    for x in range(small.width):
        for y in range(small.height):
            r, g, b, a = small.getpixel((x, y))
            if a >= 128:
                colour = nearest_palette((r, g, b))
                icon.putpixel(((48 - width) // 2 + x, (32 - height) // 2 + y), (*colour, 255))
    out.parent.mkdir(parents=True, exist_ok=True)
    keep_largest_component(icon).save(out)


def validation_errors(reference_dir: Path, frames_dir: Path) -> list[str]:
    errors: list[str] = []
    allowed = set(PALETTE)
    for offset, reference in enumerate(load_frames(reference_dir)):
        path = frame_path(frames_dir, offset)
        sidecar = path.with_suffix(".png.json")
        reference_sidecar = frame_path(reference_dir, offset).with_suffix(".png.json")
        if not path.exists():
            errors.append(f"frame {offset:02d} is missing")
            continue
        image = Image.open(path).convert("RGBA")
        if image.size != reference.size:
            errors.append(f"frame {offset:02d} size {image.size} != {reference.size}")
        alpha_values = {pixel[3] for pixel in image.getdata()}
        if not alpha_values.issubset({0, 255}):
            errors.append(f"frame {offset:02d} alpha is not hard: {sorted(alpha_values)}")
        opaque = [pixel for pixel in image.getdata() if pixel[3] == 255]
        if not opaque:
            errors.append(f"frame {offset:02d} is empty")
        foreign = sorted({pixel[:3] for pixel in opaque if pixel[:3] not in allowed})
        if foreign:
            errors.append(f"frame {offset:02d} contains foreign colours: {foreign[:5]}")
        if not sidecar.exists() or not reference_sidecar.exists():
            errors.append(f"frame {offset:02d} sidecar is missing")
        elif sidecar.read_bytes() != reference_sidecar.read_bytes():
            errors.append(f"frame {offset:02d} sidecar differs from the authentic reference")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Prepare and import Cowboy Hat 18-frame sheets")
    sub = parser.add_subparsers(dest="command", required=True)
    reference = sub.add_parser("reference", help="build the strict 6x3 wizard-hat reference grid")
    reference.add_argument("--frames-dir", required=True, type=Path)
    reference.add_argument("--out", required=True, type=Path)
    reference.add_argument("--cell", type=int, default=128)
    reference.add_argument("--scale", type=int, default=4)
    importer = sub.add_parser("import", help="slice/key/fit/quantize a returned 6x3 grid")
    importer.add_argument("--grid", required=True, type=Path)
    importer.add_argument("--reference-dir", required=True, type=Path)
    importer.add_argument("--out-dir", required=True, type=Path)
    importer.add_argument("--proof", required=True, type=Path)
    importer.add_argument("--green-tolerance", type=int, default=80)
    icon = sub.add_parser("icon", help="fit one approved grid view into a 48x32 inventory icon")
    icon.add_argument("--grid", required=True, type=Path)
    icon.add_argument("--out", required=True, type=Path)
    icon.add_argument("--source-cell", type=int, default=2)
    icon.add_argument("--green-tolerance", type=int, default=80)
    validator = sub.add_parser("validate", help="validate exact sizes, hard alpha, palette, and sidecars")
    validator.add_argument("--reference-dir", required=True, type=Path)
    validator.add_argument("--frames-dir", required=True, type=Path)
    args = parser.parse_args()

    if args.command == "reference":
        build_reference(args.frames_dir, args.out, args.cell, args.scale)
    elif args.command == "import":
        import_grid(args.grid, args.reference_dir, args.out_dir, args.proof, args.green_tolerance)
    elif args.command == "icon":
        if args.source_cell < 0 or args.source_cell >= FRAME_COUNT:
            parser.error(f"--source-cell must be between 0 and {FRAME_COUNT - 1}")
        make_icon(args.grid, args.out, args.source_cell, args.green_tolerance)
    else:
        errors = validation_errors(args.reference_dir, args.frames_dir)
        if errors:
            for error in errors:
                print(f"FAIL: {error}")
            return 1
        print(f"PASS: {FRAME_COUNT} Cowboy Hat frames match authentic geometry and palette")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
