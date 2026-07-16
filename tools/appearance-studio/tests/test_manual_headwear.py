from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.client_preview import AnimationLayer, CompositeFixture, render_composite
from appearance_studio.geometry import expand_crop
from appearance_studio.manual_headwear import (
    BACKGROUND,
    DEFAULT_PREVIEW_MASK,
    MASTER_OFFSETS,
    build_headwear_workspace,
    init_headwear_workspace,
)
from appearance_studio.paths import CLIENT_ARCHIVE, REPO_ROOT, SERVER_ARCHIVE
from appearance_studio.workbench_report import expected_states


class ManualHeadwearTest(unittest.TestCase):
    def workspace(self, name: str = "test-hat") -> Path:
        parent = REPO_ROOT / "tmp"
        parent.mkdir(exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name) / name

    def fixture(self, *, headwear: bool) -> CompositeFixture:
        layers = {
            0: AnimationLayer(0, 8, "head4", 189, 1, 0, True, False),
            1: AnimationLayer(1, 2, "body1", 27, 2, 0, True, False),
            2: AnimationLayer(2, 3, "legs1", 54, 3, 0, True, False),
        }
        animation = [8, 2, 3, 0, 0, 0, 0, 0, 0, 0, 0, 0]
        if headwear:
            layers[5] = AnimationLayer(5, 150, "partyhat", 1377, DEFAULT_PREVIEW_MASK, 0, True, False)
            animation[5] = 150
        digest = hashlib.sha256(CLIENT_ARCHIVE.read_bytes()).hexdigest()
        return CompositeFixture(
            REPO_ROOT / "tmp/manual-headwear-test-fixture.json", "test", REPO_ROOT / "tmp/oracle.json", "test",
            CLIENT_ARCHIVE, digest, tuple(animation), layers,
            0x805030, 0x345C94, 0x5C442C, 0xECDED0,
            {"hair": 0, "top": 0, "bottom": 0, "skin": 0},
            BACKGROUND, (88, 112), (12, 2), (64, 102), 0,
        )

    def test_partyhat_seed_builds_exact_legacy_frames_and_30_actual_size_previews(self):
        root = self.workspace()
        before_archives = {
            path: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (CLIENT_ARCHIVE, SERVER_ARCHIVE)
        }
        manifest = init_headwear_workspace("test-hat", "partyhat", root)
        self.assertEqual(set(MASTER_OFFSETS), set(manifest["masters"]))
        for master, offset in MASTER_OFFSETS.items():
            expected = (84, 102) if offset == 15 else (64, 102)
            with Image.open(root / "masters" / f"{master}.png") as image:
                self.assertEqual(expected, image.size)
            with Image.open(root / "guides" / f"{master}.png") as image:
                self.assertEqual(expected, image.size)

        base_fixture = self.fixture(headwear=False)
        for master, offset in MASTER_OFFSETS.items():
            state = next(item for item in expected_states() if item["spriteOffset"] == offset and not item["mirrorX"])
            oracle = render_composite(base_fixture, state).image
            left = 2 if offset >= 15 else 12
            expected_guide = oracle.crop((left, 2, left + (84 if offset >= 15 else 64), 104))
            with Image.open(root / "guides" / f"{master}.png") as guide:
                self.assertEqual(expected_guide.tobytes(), guide.convert("RGB").tobytes())

        report = build_headwear_workspace(root)
        self.assertTrue(report["valid"])
        self.assertFalse(report["shipping"])
        self.assertFalse(report["humanVisualApproval"])
        self.assertTrue(report["seedRoundTrip"])
        self.assertEqual(18, report["frameCount"])
        self.assertEqual(30, report["previewCount"])
        for offset in range(18):
            path = root / "build/frames" / f"frame_{offset:02d}.png"
            sidecar = json.loads(path.with_suffix(".png.json").read_text())
            with Image.open(path) as sprite:
                expanded = expand_crop(sprite.convert("RGBA"), sidecar)
            master = next(name for name, first in MASTER_OFFSETS.items() if first <= offset <= first + 2)
            with Image.open(root / "masters" / f"{master}.png") as source:
                source_rgba = source.convert("RGBA")
            if offset >= 15:
                delta = (0, 5, 11)[offset - 15]
                expected = Image.new("RGBA", (84, 102))
                expected.alpha_composite(source_rgba, (delta, 0))
            else:
                expected = source_rgba
            self.assertEqual(expected.tobytes(), expanded.tobytes())
        headwear_fixture = self.fixture(headwear=True)
        for preview, state in zip(report["previews"], expected_states()):
            with Image.open(root / preview["path"]) as image:
                self.assertEqual((88, 112), image.size)
                self.assertEqual(render_composite(headwear_fixture, state).image.tobytes(), image.convert("RGB").tobytes())
        with Image.open(root / "build/contact-sheet.png") as sheet:
            self.assertEqual((768, 544), sheet.size)
        self.assertEqual(before_archives, {
            path: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in (CLIENT_ARCHIVE, SERVER_ARCHIVE)
        })

    def test_build_normalizes_soft_alpha_safe_black_and_applies_combat_crown_deltas(self):
        root = self.workspace()
        init_headwear_workspace("normalization", "partyhat", root)
        combat = Image.new("RGBA", (84, 102))
        combat.putpixel((10, 10), (0, 0, 0, 200))
        combat.save(root / "masters/combat-west.png")
        report = build_headwear_workspace(root)
        self.assertFalse(report["seedRoundTrip"])
        self.assertEqual(1, report["normalization"]["combat-west"]["softAlphaPixels"])
        self.assertEqual(1, report["normalization"]["combat-west"]["safeBlackPixels"])
        for offset, expected_x in zip((15, 16, 17), (10, 15, 21)):
            path = root / "build/frames" / f"frame_{offset:02d}.png"
            sidecar = json.loads(path.with_suffix(".png.json").read_text())
            self.assertEqual(expected_x, sidecar["xShift"])
            self.assertEqual(10, sidecar["yShift"])
            with Image.open(path) as image:
                self.assertEqual((1, 1), image.size)
                self.assertEqual((1, 0, 0, 255), image.convert("RGBA").getpixel((0, 0)))

    def test_rebuild_is_deterministic_and_workspace_boundaries_fail_closed(self):
        root = self.workspace()
        init_headwear_workspace("deterministic", "partyhat", root)
        first = build_headwear_workspace(root)
        first_report = (root / "build/report.json").read_bytes()
        first_hashes = {item["path"]: item["sha256"] for item in first["artifacts"]}
        second = build_headwear_workspace(root)
        self.assertEqual(first_hashes, {item["path"]: item["sha256"] for item in second["artifacts"]})
        self.assertEqual(first_report, (root / "build/report.json").read_bytes())

        with self.assertRaises(FileExistsError):
            init_headwear_workspace("deterministic", "partyhat", root)
        with self.assertRaisesRegex(ValueError, "must remain under"):
            init_headwear_workspace("unsafe", "partyhat", REPO_ROOT / "unsafe-headwear")

        manifest_path = root / "workspace.json"
        manifest = json.loads(manifest_path.read_text())
        manifest["reference"]["clientArchiveSha256"] = hashlib.sha256(b"drift").hexdigest()
        manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        with self.assertRaisesRegex(ValueError, "reference drifted"):
            build_headwear_workspace(root)

    def test_missing_empty_and_wrong_size_masters_are_rejected(self):
        root = self.workspace()
        init_headwear_workspace("invalid", "partyhat", root)
        (root / "masters/north.png").unlink()
        with self.assertRaises(FileNotFoundError):
            build_headwear_workspace(root)

        init_headwear_workspace("invalid", "partyhat", root, force=True)
        Image.new("RGBA", (63, 102)).save(root / "masters/north.png")
        with self.assertRaisesRegex(ValueError, "size"):
            build_headwear_workspace(root)

        init_headwear_workspace("invalid", "partyhat", root, force=True)
        Image.new("RGBA", (64, 102)).save(root / "masters/north.png")
        with self.assertRaisesRegex(ValueError, "empty"):
            build_headwear_workspace(root)


if __name__ == "__main__":
    unittest.main()
