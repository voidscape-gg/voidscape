#!/usr/bin/env python3
"""Import and validate the Void Wyrm's strict 3x6 RSC sprite sheet.

The first five rows are the three walking frames for the five rendered NPC
directions. The final row is the three-frame combat animation. Output frames
use the authentic dragon's logical canvases so direction changes and combat do
not resize or jump even though each encoded sprite is tightly cropped.
"""
from __future__ import annotations

import argparse
import json
from collections import deque
from pathlib import Path

from PIL import Image, ImageDraw


COLS = 3
ROWS = 6
FRAME_COUNT = COLS * ROWS
WALK_FRAME_COUNT = 15
WALK_CANVAS = (226, 163)
ATTACK_CANVAS = (335, 163)
CANVAS_MARGIN = 2
PROOF_BG = (23, 20, 29, 255)


def frame_path(root: Path, offset: int) -> Path:
    return root / f"frame_{offset:02d}.png"


def cell_box(size: tuple[int, int], offset: int) -> tuple[int, int, int, int]:
    width, height = size
    if width % COLS or height % ROWS:
        raise ValueError(
            f"sheet must divide exactly into {COLS}x{ROWS} cells; got {width}x{height}"
        )
    cell_width = width // COLS
    cell_height = height // ROWS
    col = offset % COLS
    row = offset // COLS
    return (
        col * cell_width,
        row * cell_height,
        (col + 1) * cell_width,
        (row + 1) * cell_height,
    )


def hard_mask(image: Image.Image) -> Image.Image:
    """Convert ImageGen's soft alpha edge to the legacy RSC binary mask."""
    rgba = image.convert("RGBA")
    pixels = []
    for red, green, blue, alpha in rgba.getdata():
        if alpha < 128:
            pixels.append((0, 0, 0, 0))
        else:
            # The archive's all-zero RGB value is reserved for transparency.
            if red == green == blue == 0:
                red = 1
            pixels.append((red, green, blue, 255))
    rgba.putdata(pixels)
    return rgba


def keep_largest_component(image: Image.Image) -> Image.Image:
    """Discard disconnected sheet debris without moving the approved pose."""
    rgba = image.convert("RGBA")
    opaque = {
        (x, y)
        for y in range(rgba.height)
        for x in range(rgba.width)
        if rgba.getpixel((x, y))[3] == 255
    }
    largest: set[tuple[int, int]] = set()
    while opaque:
        start = opaque.pop()
        component = {start}
        pending = deque([start])
        while pending:
            x, y = pending.popleft()
            for dx, dy in (
                (-1, -1), (0, -1), (1, -1),
                (-1, 0),           (1, 0),
                (-1, 1),  (0, 1),  (1, 1),
            ):
                neighbour = (x + dx, y + dy)
                if neighbour in opaque:
                    opaque.remove(neighbour)
                    component.add(neighbour)
                    pending.append(neighbour)
        if len(component) > len(largest):
            largest = component

    if not largest:
        raise ValueError("sheet cell contains no opaque Wyrm pixels")
    out = Image.new("RGBA", rgba.size, (0, 0, 0, 0))
    for point in largest:
        out.putpixel(point, rgba.getpixel(point))
    return out


def logical_canvas(offset: int) -> tuple[int, int]:
    return WALK_CANVAS if offset < WALK_FRAME_COUNT else ATTACK_CANVAS


def prepare_cell(cell: Image.Image, offset: int) -> tuple[Image.Image, dict[str, int | bool]]:
    keyed = keep_largest_component(hard_mask(cell))
    logical_width, logical_height = logical_canvas(offset)
    scale = min(
        (logical_width - 2 * CANVAS_MARGIN) / keyed.width,
        (logical_height - 2 * CANVAS_MARGIN) / keyed.height,
    )
    scaled_size = (
        max(1, round(keyed.width * scale)),
        max(1, round(keyed.height * scale)),
    )
    scaled = keyed.resize(scaled_size, Image.Resampling.NEAREST)
    canvas = Image.new("RGBA", (logical_width, logical_height), (0, 0, 0, 0))
    paste_x = (logical_width - scaled.width) // 2
    paste_y = logical_height - CANVAS_MARGIN - scaled.height
    canvas.alpha_composite(scaled, (paste_x, paste_y))

    bounds = canvas.getbbox()
    if bounds is None:
        raise ValueError(f"frame {offset:02d} became empty during import")
    left, top, right, bottom = bounds
    frame = canvas.crop(bounds)
    sidecar: dict[str, int | bool] = {
        "width": frame.width,
        "height": frame.height,
        "requiresShift": True,
        "xShift": left,
        "yShift": top,
        "something1": logical_width,
        "something2": logical_height,
    }
    return frame, sidecar


def write_proof(
    frames: list[Image.Image], frames_dir: Path, out: Path, scale: int = 2
) -> None:
    label_height = 18
    cell_width = ATTACK_CANVAS[0] * scale
    cell_height = WALK_CANVAS[1] * scale + label_height
    proof = Image.new("RGBA", (COLS * cell_width, ROWS * cell_height), PROOF_BG)
    draw = ImageDraw.Draw(proof)
    for offset, frame in enumerate(frames):
        sidecar = json.loads(
            frame_path(frames_dir, offset).with_suffix(".png.json").read_text()
        )
        logical_width, logical_height = logical_canvas(offset)
        logical = Image.new("RGBA", (logical_width, logical_height), (0, 0, 0, 0))
        logical.alpha_composite(frame, (sidecar["xShift"], sidecar["yShift"]))
        zoom = logical.resize(
            (logical_width * scale, logical_height * scale), Image.Resampling.NEAREST
        )
        col = offset % COLS
        row = offset // COLS
        x = col * cell_width + (cell_width - zoom.width) // 2
        y = row * cell_height + label_height
        proof.alpha_composite(zoom, (x, y))
        draw.text((col * cell_width + 5, row * cell_height + 3), f"{offset:02d}", fill="white")
    out.parent.mkdir(parents=True, exist_ok=True)
    proof.save(out)


def import_sheet(sheet_path: Path, out_dir: Path, proof_path: Path) -> None:
    sheet = Image.open(sheet_path).convert("RGBA")
    # Validate divisibility before creating any output.
    cell_box(sheet.size, 0)
    out_dir.mkdir(parents=True, exist_ok=True)
    frames: list[Image.Image] = []
    for offset in range(FRAME_COUNT):
        cell = sheet.crop(cell_box(sheet.size, offset))
        frame, sidecar = prepare_cell(cell, offset)
        output = frame_path(out_dir, offset)
        frame.save(output)
        output.with_suffix(".png.json").write_text(json.dumps(sidecar, indent=2) + "\n")
        frames.append(frame)

    manifest = {
        "format": "voidscape-void-wyrm-sheet-v1",
        "sourceSheet": str(sheet_path),
        "grid": {"columns": COLS, "rows": ROWS},
        "frameCount": FRAME_COUNT,
        "walkingFrames": WALK_FRAME_COUNT,
        "walkingLogicalCanvas": list(WALK_CANVAS),
        "attackLogicalCanvas": list(ATTACK_CANVAS),
        "alpha": "binary",
    }
    (out_dir / "import_manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    write_proof(frames, out_dir, proof_path)


def validation_errors(frames_dir: Path) -> list[str]:
    errors: list[str] = []
    for offset in range(FRAME_COUNT):
        path = frame_path(frames_dir, offset)
        sidecar_path = path.with_suffix(".png.json")
        if not path.exists():
            errors.append(f"frame {offset:02d} is missing")
            continue
        if not sidecar_path.exists():
            errors.append(f"frame {offset:02d} sidecar is missing")
            continue
        image = Image.open(path).convert("RGBA")
        sidecar = json.loads(sidecar_path.read_text())
        expected_canvas = logical_canvas(offset)
        if (sidecar.get("something1"), sidecar.get("something2")) != expected_canvas:
            errors.append(f"frame {offset:02d} has the wrong logical canvas")
        if (sidecar.get("width"), sidecar.get("height")) != image.size:
            errors.append(f"frame {offset:02d} sidecar dimensions do not match its PNG")
        if sidecar.get("requiresShift") is not True:
            errors.append(f"frame {offset:02d} must use positional shifts")
        alpha = {pixel[3] for pixel in image.getdata()}
        if not alpha.issubset({0, 255}):
            errors.append(f"frame {offset:02d} has soft alpha values")
        opaque = [pixel for pixel in image.getdata() if pixel[3] == 255]
        if not opaque:
            errors.append(f"frame {offset:02d} is empty")
        if any(pixel[:3] == (0, 0, 0) for pixel in opaque):
            errors.append(f"frame {offset:02d} uses the transparent RGB sentinel as a colour")
        if len({pixel[:3] for pixel in opaque}) > 24:
            errors.append(f"frame {offset:02d} exceeds the approved 24-colour palette")
        logical_width, logical_height = expected_canvas
        x_shift = sidecar.get("xShift", -1)
        y_shift = sidecar.get("yShift", -1)
        if (
            x_shift < CANVAS_MARGIN
            or y_shift < CANVAS_MARGIN
            or x_shift + image.width > logical_width - CANVAS_MARGIN
            or y_shift + image.height > logical_height - CANVAS_MARGIN
        ):
            errors.append(f"frame {offset:02d} escapes the logical canvas margin")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Import and validate Void Wyrm sprites")
    commands = parser.add_subparsers(dest="command", required=True)
    importer = commands.add_parser("import", help="slice and normalize a strict 3x6 sheet")
    importer.add_argument("--sheet", required=True, type=Path)
    importer.add_argument("--out-dir", required=True, type=Path)
    importer.add_argument("--proof", required=True, type=Path)
    validator = commands.add_parser("validate", help="validate imported frames and sidecars")
    validator.add_argument("--frames-dir", required=True, type=Path)
    args = parser.parse_args()

    if args.command == "import":
        import_sheet(args.sheet, args.out_dir, args.proof)
        return 0

    errors = validation_errors(args.frames_dir)
    if errors:
        for error in errors:
            print(f"FAIL: {error}")
        return 1
    print(f"PASS: {FRAME_COUNT} Void Wyrm frames satisfy the RSC sprite contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
