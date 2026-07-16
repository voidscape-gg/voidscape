from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image, ImageDraw

TOOL_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOL_DIR))

import cowboy_sheet  # noqa: E402


class CowboySheetTest(unittest.TestCase):
    def test_reference_import_validate_and_proof(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            refs, work, frames = root / "refs", root / "work", root / "frames"
            refs.mkdir()
            for offset in range(18):
                width, height = 20 + offset % 5, 18 + offset % 4
                image = Image.new("RGBA", (width, height), (0, 0, 0, 0))
                ImageDraw.Draw(image).rectangle((2, 2, width - 3, height - 3), fill=(90, 60, 30, 255))
                png = refs / f"frame_{offset:02d}.png"
                image.save(png)
                sidecar = {
                    "width": width, "height": height, "requiresShift": True,
                    "xShift": offset, "yShift": 0, "something1": 64, "something2": 102,
                }
                (refs / f"frame_{offset:02d}.png.json").write_text(json.dumps(sidecar, indent=2))

            reference_grid = work / "cowboy_hat_ai_reference.png"
            cowboy_sheet.build_reference(refs, reference_grid)
            self.assertTrue(reference_grid.is_file())

            generated = Image.new("RGBA", (600, 300), cowboy_sheet.GREEN)
            draw = ImageDraw.Draw(generated)
            for col in range(6):
                for row in range(3):
                    left, top, right, bottom = cowboy_sheet.cell_box(generated.size, col, row)
                    draw.rectangle((left + 20, top + 20, right - 21, bottom - 21), fill=(120, 70, 30, 255))
            sheet = root / "generated.png"
            generated.save(sheet)
            proof = root / "proof.png"
            cowboy_sheet.import_grid(sheet, refs, frames, proof, tolerance=80)

            self.assertEqual([], cowboy_sheet.validation_errors(refs, frames))
            self.assertEqual(
                (refs / "frame_07.png.json").read_bytes(),
                (frames / "frame_07.png.json").read_bytes(),
            )
            self.assertTrue(proof.is_file())

    def test_validation_rejects_soft_alpha_and_foreign_colour(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            refs = root / "refs"
            refs.mkdir()
            for offset in range(18):
                image = Image.new("RGBA", (4, 4), cowboy_sheet.PALETTE[0] + (255,))
                image.save(refs / f"frame_{offset:02d}.png")
                sidecar = {"width": 4, "height": 4, "requiresShift": True,
                           "xShift": 0, "yShift": 0, "something1": 64, "something2": 102}
                (refs / f"frame_{offset:02d}.png.json").write_text(json.dumps(sidecar))
            cowboy_sheet.build_reference(refs, root / "reference.png")
            frames = root / "frames"
            frames.mkdir()
            for path in refs.iterdir():
                if path.name.startswith("frame_"):
                    shutil_target = frames / path.name
                    shutil_target.write_bytes(path.read_bytes())
            bad = Image.new("RGBA", (4, 4), (1, 2, 3, 127))
            bad.save(frames / "frame_00.png")
            errors = cowboy_sheet.validation_errors(refs, frames)
            self.assertTrue(any("alpha is not hard" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
