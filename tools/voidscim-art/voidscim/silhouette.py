"""Silhouette enforcement: ref alpha → 1024² mask, mask AI output, IoU scoring.

The ref sprite is small (e.g. 45×25). NEAREST-upscale its alpha to a centered
rectangle inside a 1024×1024 canvas. Everything outside the silhouette is
forced transparent in the AI output, guaranteeing the AI cannot draw shapes
the original didn't have.
"""
from __future__ import annotations
import numpy as np
from PIL import Image


def upscale_alpha_nearest(ref_native_rgba: Image.Image, target: int = 1024) -> Image.Image:
    """Take native-size RGBA, NEAREST-upscale alpha to a centered TxT canvas.
    Returns L-mode mask (0 = outside silhouette, 255 = inside).
    """
    rgba = ref_native_rgba.convert("RGBA")
    alpha = rgba.split()[-1]
    nw, nh = rgba.size
    scale = min(target // max(nw, 1), target // max(nh, 1))
    if scale < 1:
        scale = 1
    new_w, new_h = nw * scale, nh * scale
    scaled = alpha.resize((new_w, new_h), Image.NEAREST)
    out = Image.new("L", (target, target), 0)
    ox = (target - new_w) // 2
    oy = (target - new_h) // 2
    out.paste(scaled, (ox, oy))
    return out


def derive_alpha_silhouette(rgba: Image.Image) -> Image.Image:
    """Return an L-mode mask from the alpha channel of an RGBA image."""
    alpha = rgba.convert("RGBA").split()[-1]
    arr = np.array(alpha)
    return Image.fromarray((arr >= 128).astype(np.uint8) * 255, mode="L")


def iou(mask_a: Image.Image, mask_b: Image.Image) -> float:
    a = np.array(mask_a.convert("L")) > 0
    b = np.array(mask_b.convert("L")) > 0
    inter = int(np.logical_and(a, b).sum())
    union = int(np.logical_or(a, b).sum())
    return inter / union if union > 0 else 0.0


def apply_mask(rgba: Image.Image, mask_L: Image.Image) -> Image.Image:
    """Force pixels outside `mask_L` to be fully transparent in `rgba`."""
    out = rgba.convert("RGBA").copy()
    arr = np.array(out)
    m = np.array(mask_L.convert("L"))
    arr[m == 0, 3] = 0
    arr[m == 0, 0] = 0
    arr[m == 0, 1] = 0
    arr[m == 0, 2] = 0
    return Image.fromarray(arr, mode="RGBA")
