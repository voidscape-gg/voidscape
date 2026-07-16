from __future__ import annotations

from dataclasses import dataclass

from PIL import Image


@dataclass(frozen=True)
class DerivedSprite:
    image: Image.Image
    sidecar: dict[str, int | bool]


def translate_integer(image: Image.Image, dx: int, dy: int, size: tuple[int, int]) -> Image.Image:
    if isinstance(dx, bool) or isinstance(dy, bool) or not isinstance(dx, int) or not isinstance(dy, int):
        raise ValueError("translations must be integers")
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.alpha_composite(image.convert("RGBA"), (dx, dy))
    return out


def hard_alpha(image: Image.Image) -> Image.Image:
    out = image.convert("RGBA")
    pixels = []
    for r, g, b, a in out.getdata():
        if a < 128:
            pixels.append((0, 0, 0, 0))
        else:
            pixels.append((1, 0, 0, 255) if (r, g, b) == (0, 0, 0) else (r, g, b, 255))
    out.putdata(pixels)
    return out


def derive_sprite(canvas: Image.Image) -> DerivedSprite:
    canvas = hard_alpha(canvas)
    box = canvas.getbbox()
    if box is None:
        raise ValueError("compiled frame is empty")
    left, top, right, bottom = box
    width, height = canvas.size
    return DerivedSprite(canvas.crop(box), {
        "width": right - left, "height": bottom - top,
        "requiresShift": box != (0, 0, width, height),
        "xShift": left, "yShift": top, "something1": width, "something2": height,
    })


def expand_crop(image: Image.Image, sidecar: dict) -> Image.Image:
    canvas = Image.new("RGBA", (int(sidecar["something1"]), int(sidecar["something2"])), (0, 0, 0, 0))
    canvas.alpha_composite(image.convert("RGBA"), (int(sidecar["xShift"]), int(sidecar["yShift"])))
    return canvas


def runtime_mirror(canvas: Image.Image) -> Image.Image:
    return canvas.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
