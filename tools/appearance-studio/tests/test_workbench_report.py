from __future__ import annotations

import json
import hashlib
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.workbench_report import (
    expected_states, validate_report, verify_cowboy_immutability, write_evidence,
)
from appearance_studio.paths import REPO_ROOT


class WorkbenchReportTest(unittest.TestCase):
    def make_report(self, root: Path) -> dict:
        archive = root / "Authentic_Sprites.orsc"
        archive.write_bytes(b"fixture archive")
        captures = []
        layer_animation = list(range(1, 13))
        layers = [
            {"slot": slot, "appearanceId": appearance_id, "definition": {
                "name": f"layer{slot}", "category": "player", "spriteBase": slot * 27,
                "charColour": 0, "blueMask": 0, "hasA": True, "hasF": False,
            }}
            for slot, appearance_id in enumerate(layer_animation)
        ]
        for index, state in enumerate(expected_states()):
            png = root / f"world-{index}.png"
            sidecar = root / f"world-{index}.json"
            raster = root / f"raster-{index}.png"
            Image.new("RGB", (16, 16), (index, 0, 0)).save(png)
            Image.new("RGB", (88, 112), (18, 24, 32)).save(raster)
            sidecar.write_text("{}\n")
            raster_image = Image.open(raster).convert("RGB")
            raw = b"".join(bytes(pixel) for pixel in raster_image.getdata())
            captures.append({**state, "pngPath": str(png), "statePath": str(sidecar),
                "isolatedRaster": {"pngPath": str(raster),
                    "pngSha256": hashlib.sha256(raster.read_bytes()).hexdigest(),
                    "rawRgbSha256": hashlib.sha256(raw).hexdigest(), "width": 88, "height": 112,
                    "backgroundRgb": 0x121820, "crop": {"x": 20, "y": 2, "width": 40, "height": 100}},
                "renderInputs": {"wantedAnimDir": state["wantedAnimDir"],
                    "actualAnimDir": state["actualAnimDir"], "mirrorX": state["mirrorX"],
                    "spriteOffset": state["spriteOffset"],
                    "stepFrame": state["frame"] * 6 if state["kind"] == "walk" else 0,
                    "x": 12, "y": 2, "width": 64, "height": 102,
                    "topPixelSkew": 0, "overlayMovement": 0, "hairStyle": 0,
                    "invisible": False, "invulnerable": False,
                    "paletteIndices": {"hair": 1, "top": 2, "bottom": 3, "skin": 0},
                    "resolvedRgb": {"hair": 0x604020, "top": 0xff0000,
                                    "bottom": 0xffffff, "skin": 0xecded0},
                    "layerAnimation": layer_animation, "layerOrder": list(range(12)), "layers": layers}})
        return {"ok": True, "scenario": "appearance-frames", "appearanceId": 245,
                "appearanceKey": "cowboy_hat", "frameCount": 30, "restored": True,
                "spriteArchive": {"path": str(archive),
                                  "sha256": hashlib.sha256(archive.read_bytes()).hexdigest()},
                "captures": captures}

    def test_exact_30_state_matrix_and_evidence(self):
        with tempfile.TemporaryDirectory(dir=REPO_ROOT / "tmp") as temporary:
            root = Path(temporary)
            report = self.make_report(root)
            self.assertFalse(validate_report(report))
            report_path = root / "report.json"
            report_path.write_text(json.dumps(report))
            manifest, contact = write_evidence(report_path, root / "evidence")
            self.assertTrue(manifest.is_file())
            self.assertTrue(contact.is_file())
            evidence = json.loads(manifest.read_text())
            self.assertEqual(30, len(evidence["captures"]))
            self.assertEqual("isolatedRaster", evidence["authoritative"])
            fixture = json.loads((root / "evidence" / "composite-fixture.json").read_text())
            self.assertEqual(12, len(fixture["layerAnimation"]))
            self.assertEqual(88, fixture["draw"]["canvasWidth"])

    def test_wrong_mirror_or_missing_restore_fails(self):
        with tempfile.TemporaryDirectory(dir=REPO_ROOT / "tmp") as temporary:
            report = self.make_report(Path(temporary))
            report["restored"] = False
            report["captures"][15]["mirrorX"] = False
            errors = validate_report(report)
            self.assertTrue(any("not restored" in error for error in errors))
            self.assertTrue(any("mirrorX" in error for error in errors))

    def test_missing_isolated_raster_fails_closed(self):
        with tempfile.TemporaryDirectory(dir=REPO_ROOT / "tmp") as temporary:
            report = self.make_report(Path(temporary))
            del report["captures"][0]["isolatedRaster"]
            self.assertTrue(any("authoritative isolatedRaster" in error for error in validate_report(report)))

    def test_asset_immutability_compares_complete_snapshots(self):
        with tempfile.TemporaryDirectory(dir=REPO_ROOT / "tmp") as temporary:
            root = Path(temporary)
            snapshot = {"schema": "voidscape-cowboy-asset-snapshot/v1",
                        "frames": {"frame_00.png": "abc"}, "archives": {"client": "def", "server": "ghi"}}
            before, after, out = root / "before.json", root / "after.json", root / "report.json"
            before.write_text(json.dumps(snapshot))
            after.write_text(json.dumps(snapshot))
            self.assertTrue(verify_cowboy_immutability(before, after, out)["unchanged"])
            snapshot["frames"]["frame_00.png"] = "changed"
            after.write_text(json.dumps(snapshot))
            self.assertFalse(verify_cowboy_immutability(before, after, out)["unchanged"])


if __name__ == "__main__":
    unittest.main()
