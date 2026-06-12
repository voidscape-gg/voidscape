#!/usr/bin/env python3
"""Rebuild Voidscape top-tab icon size variants from the checked-in masters.

The client draws the top-bar glyphs at very small native sizes (32px at the
compact HUD, 42px otherwise). Plain Lanczos downscales look soft there, while
runtime nearest-neighbor scaling looks jagged. This script keeps the 68px
masters as the art source and bakes exact-size variants with small-icon
contrast, sharpening, and a subtle silhouette stroke.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance, ImageFilter


ROOT = Path(__file__).resolve().parents[1]
SKIN_DIR = ROOT / "Client_Base" / "Cache" / "voidscape" / "ui" / "skin"

TOP_ICONS = (
	"top-gear",
	"top-social-smiley",
	"top-stats-bars",
	"top-book",
	"top-map-scroll",
	"top-bag",
)

SIZES = (20, 24, 30, 32, 34, 36, 40, 42)


def bake_variant(master: Image.Image, size: int) -> Image.Image:
	icon = master.convert("RGBA").resize((size, size), Image.Resampling.LANCZOS)

	# Tiny UI art needs stronger contrast than the master or it reads as blur on
	# the dark tab frame. Keep this conservative so the icons do not turn crunchy.
	icon = ImageEnhance.Contrast(icon).enhance(1.12)
	icon = ImageEnhance.Sharpness(icon).enhance(2.05)
	icon = icon.filter(ImageFilter.UnsharpMask(radius=0.45, percent=120, threshold=2))

	alpha = icon.getchannel("A")
	stroke_alpha = alpha.filter(ImageFilter.MaxFilter(3))
	stroke_alpha = ImageChops.subtract(stroke_alpha, alpha)
	stroke_alpha = stroke_alpha.point(lambda px: int(px * 0.33))

	stroke = Image.new("RGBA", (size, size), (3, 3, 5, 0))
	stroke.putalpha(stroke_alpha)
	stroke.alpha_composite(icon)
	return stroke


def main() -> int:
	parser = argparse.ArgumentParser(description=__doc__)
	parser.add_argument(
		"--output-dir",
		type=Path,
		default=SKIN_DIR,
		help="Directory to write variants into. Defaults to the live skin directory.",
	)
	args = parser.parse_args()

	args.output_dir.mkdir(parents=True, exist_ok=True)
	for icon_name in TOP_ICONS:
		master_path = SKIN_DIR / f"{icon_name}.png"
		if not master_path.exists():
			raise FileNotFoundError(master_path)
		master = Image.open(master_path).convert("RGBA")
		for size in SIZES:
			bake_variant(master, size).save(args.output_dir / f"{icon_name}-{size}.png")
	return 0


if __name__ == "__main__":
	raise SystemExit(main())
