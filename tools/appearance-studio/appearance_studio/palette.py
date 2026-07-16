from __future__ import annotations

from PIL import Image


def classify(rgb: tuple[int, int, int], *, blue_mask: bool = False) -> str:
    r, g, b = rgb
    if r == g == b and r != 0:
        return "primary"
    if r == 255 and g == b:
        return "skin"
    if blue_mask and r == g and b != g:
        return "secondary"
    return "literal"


def validate_palette(image: Image.Image, policy: str, *, whitelist: set[tuple[int, int, int]] | None = None) -> list[str]:
    errors: list[str] = []
    allowed_primary = {103, 132, 160}
    for index, (r, g, b, a) in enumerate(image.convert("RGBA").getdata()):
        if a not in {0, 255}:
            errors.append(f"pixel {index} has soft alpha {a}")
            continue
        if a == 0:
            continue
        kind = classify((r, g, b), blue_mask=policy == "dual-mask")
        if policy in {"hair-mask", "primary-mask"} and not (kind == "primary" and r in allowed_primary):
            errors.append(f"pixel {index} is not a declared primary shade")
        elif policy == "skin-mask" and kind != "skin":
            errors.append(f"pixel {index} is not a skin mask")
        elif policy == "fixed-whitelist" and ((r, g, b) not in (whitelist or set()) or kind != "literal"):
            errors.append(f"pixel {index} is not a safe fixed colour")
    return errors
