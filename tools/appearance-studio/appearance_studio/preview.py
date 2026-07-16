from __future__ import annotations

from PIL import Image, ImageDraw

from .compiler import CompileResult
from .geometry import runtime_mirror


def render_layer_matrix(result: CompileResult, *, scale: int = 4) -> Image.Image:
    cell_w, cell_h = 84 * scale + 8, 102 * scale + 18
    sheet = Image.new("RGBA", (6 * cell_w, 3 * cell_h), (22, 25, 30, 255))
    draw = ImageDraw.Draw(sheet)
    for frame in result.frames:
        image = frame.canvas.resize((frame.canvas.width * scale, frame.canvas.height * scale), Image.Resampling.NEAREST)
        column, row = frame.offset % 6, frame.offset // 6
        sheet.alpha_composite(image, (column * cell_w + 4, row * cell_h + 15))
        draw.text((column * cell_w + 3, row * cell_h + 2), str(frame.offset), fill=(240, 230, 205, 255))
    return sheet


def runtime_preview(result: CompileResult, stored_offset: int, mirror: bool) -> Image.Image:
    canvas = result.frames[stored_offset].canvas
    return runtime_mirror(canvas) if mirror else canvas.copy()
