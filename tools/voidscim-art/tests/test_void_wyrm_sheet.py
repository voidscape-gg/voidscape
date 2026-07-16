import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


TOOLS_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS_ROOT))

import void_wyrm_sheet as wyrm


class VoidWyrmSheetTest(unittest.TestCase):
    def make_sheet(self, path: Path) -> None:
        sheet = Image.new("RGBA", (90, 120), (0, 0, 0, 0))
        for offset in range(wyrm.FRAME_COUNT):
            col = offset % wyrm.COLS
            row = offset // wyrm.COLS
            for y in range(row * 20 + 3, row * 20 + 18):
                for x in range(col * 30 + 5, col * 30 + 25):
                    sheet.putpixel((x, y), (40 + offset, 80, 60, 200))
            # Disconnected debris should not survive the importer.
            sheet.putpixel((col * 30, row * 20), (255, 0, 255, 255))
        sheet.save(path)

    def test_import_emits_exact_frame_contract(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            source = root / "sheet.png"
            frames = root / "frames"
            proof = root / "proof.png"
            self.make_sheet(source)

            wyrm.import_sheet(source, frames, proof)

            self.assertEqual([], wyrm.validation_errors(frames))
            self.assertTrue(proof.exists())
            self.assertEqual(wyrm.FRAME_COUNT, len(list(frames.glob("frame_*.png"))))
            walk = json.loads((frames / "frame_00.png.json").read_text())
            attack = json.loads((frames / "frame_15.png.json").read_text())
            self.assertEqual(list(wyrm.WALK_CANVAS), [walk["something1"], walk["something2"]])
            self.assertEqual(list(wyrm.ATTACK_CANVAS), [attack["something1"], attack["something2"]])
            self.assertNotIn((255, 0, 255, 255), Image.open(frames / "frame_00.png").getdata())

    def test_rejects_non_divisible_grid(self) -> None:
        image = Image.new("RGBA", (91, 120), (0, 0, 0, 0))
        with self.assertRaisesRegex(ValueError, "divide exactly"):
            wyrm.cell_box(image.size, 0)


if __name__ == "__main__":
    unittest.main()
