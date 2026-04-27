"""Quality scoring: silhouette IoU (computed upstream), palette adherence, halo residue."""
from __future__ import annotations
import numpy as np
from PIL import Image

from .paths import CHROMA_KEY_HEX


def hex_to_rgb(s: str) -> tuple[int, int, int]:
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16))


def palette_coverage(rgba: Image.Image, anchor_hex: str, tol: int) -> float:
    """Fraction of opaque pixels within `tol` (per-channel) of anchor color."""
    arr = np.array(rgba.convert("RGBA"))
    opaque = arr[:, :, 3] >= 128
    if not opaque.any():
        return 0.0
    target = hex_to_rgb(anchor_hex)
    r = arr[:, :, 0].astype(int)
    g = arr[:, :, 1].astype(int)
    b = arr[:, :, 2].astype(int)
    close = (np.abs(r - target[0]) <= tol) & (np.abs(g - target[1]) <= tol) & (np.abs(b - target[2]) <= tol) & opaque
    return float(close.sum()) / float(opaque.sum())


def halo_residue(rgba: Image.Image, key_hex: str = CHROMA_KEY_HEX, tol: int = 24) -> float:
    """Fraction of opaque pixels close to the chroma key. Should be ~0 post-key."""
    return palette_coverage(rgba, key_hex, tol)


def score_variant(
    variant: Image.Image,
    iou_value: float,
    palette_anchors: list[str],
    palette_tol: int,
    iou_min: float,
    halo_max: float = 0.005,
    palette_min_each: float = 0.01,
) -> dict:
    halo = halo_residue(variant)
    palette_hits = []
    for anchor in palette_anchors:
        cov = palette_coverage(variant, anchor, palette_tol)
        palette_hits.append({"anchor": anchor, "fraction": float(cov)})

    reasons: list[str] = []
    passes = True
    if iou_value < iou_min:
        reasons.append(f"silhouette IoU {iou_value:.3f} < {iou_min}")
        passes = False
    if halo > halo_max:
        reasons.append(f"chroma-key residue {halo*100:.2f}% > {halo_max*100:.2f}%")
        passes = False
    for hit in palette_hits:
        if hit["fraction"] < palette_min_each:
            reasons.append(f"palette {hit['anchor']} only {hit['fraction']*100:.2f}% (< {palette_min_each*100:.1f}%)")
            passes = False

    return {
        "iou": float(iou_value),
        "halo_residue": float(halo),
        "palette_hits": palette_hits,
        "passes": passes,
        "reasons": reasons,
    }
