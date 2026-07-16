from __future__ import annotations

import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from PIL import Image

from appearance_studio.paths import CLIENT_ARCHIVE, REPO_ROOT, SERVER_ARCHIVE
from appearance_studio.calibration import render_evidence


REFERENCES = {
    "head1": 0,
    "body": 27,
    "legs": 54,
    "fhead1": 81,
    "head3": 162,
    "head4": 189,
}
REFERENCE_ROOT = REPO_ROOT / "content/appearance/templates/rsc-player-v1/references"


def encode_authentic_sprite(image: Image.Image, sidecar: dict) -> bytes:
    image = image.convert("RGBA")
    width, height = image.size
    header = struct.pack(
        ">iiBiiii",
        width,
        height,
        1 if sidecar["requiresShift"] else 0,
        sidecar["xShift"],
        sidecar["yShift"],
        sidecar["something1"],
        sidecar["something2"],
    )
    pixels = bytearray()
    for red, green, blue, alpha in image.getdata():
        if alpha < 128:
            pixels.extend((0, 0, 0, 0))
        else:
            if (red, green, blue) == (0, 0, 0):
                red = 1
            pixels.extend((0, red, green, blue))
    return header + bytes(pixels)


class CalibrationReferenceTest(unittest.TestCase):
    def test_canonical_references_roundtrip_to_both_authentic_archives(self):
        with zipfile.ZipFile(CLIENT_ARCHIVE) as client, zipfile.ZipFile(SERVER_ARCHIVE) as server:
            for name, base in REFERENCES.items():
                directory = REFERENCE_ROOT / name
                self.assertEqual(18, len(list(directory.glob("frame_*.png"))))
                self.assertEqual(18, len(list(directory.glob("frame_*.png.json"))))
                for offset in range(18):
                    path = directory / f"frame_{offset:02d}.png"
                    sidecar = json.loads(path.with_suffix(".png.json").read_text())
                    encoded = encode_authentic_sprite(Image.open(path), sidecar)
                    entry = str(base + offset)
                    self.assertEqual(client.read(entry), encoded, f"client {name} frame {offset}")
                    self.assertEqual(server.read(entry), encoded, f"server {name} frame {offset}")

    def test_calibration_evidence_is_deterministic_and_tmp_only(self):
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as first, tempfile.TemporaryDirectory(dir=parent) as second:
            first_report = render_evidence(Path(first))
            second_report = render_evidence(Path(second))
            self.assertEqual(first_report["sheet_sha256"], second_report["sheet_sha256"])
            self.assertEqual(
                {key: value["sha256"] for key, value in first_report["panels"].items()},
                {key: value["sha256"] for key, value in second_report["panels"].items()},
            )
            self.assertEqual(
                {"north": 15, "north-west": 15, "west": 16, "south-west": None,
                 "south": None, "combat-west": 16},
                first_report["usable_upper_lip_pixels"],
            )
        with tempfile.TemporaryDirectory() as outside:
            with self.assertRaisesRegex(ValueError, "under repository tmp"):
                render_evidence(Path(outside))


if __name__ == "__main__":
    unittest.main()
