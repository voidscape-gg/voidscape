from __future__ import annotations

import hashlib
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
GENERATOR = ROOT / "tools/appearance-studio/proof_art/paperdoll_v2_hairstyles.py"
CONTENT = ROOT / "content/appearance/v2/hairstyles"


def load_generator():
    spec = importlib.util.spec_from_file_location("paperdoll_v2_hairstyles", GENERATOR)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot import {GENERATOR}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def tree_sha256(root: Path) -> dict[str, str]:
    return {
        str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(root.rglob("*"))
        if path.is_file()
    }


class PaperdollV2HairstyleArtTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.art = load_generator()

    def relative_points(self, style_id: str, master: str) -> set[tuple[int, int]]:
        image = self.art.rasterize(style_id, master)
        frame = next(frame for frame in self.art.load_v2_template().frames if frame.master == master)
        return {
            (x - frame.crown[0], y - frame.crown[1])
            for x, y in self.art._points(image)
        }

    def test_collection_has_six_stable_style_ids_and_masters(self) -> None:
        self.assertEqual(
            tuple(self.art.STYLES),
            (
                "rare_spikes",
                "faded_buzzcut",
                "mohawk",
                "textured_crop",
                "slick_back_undercut",
                "high_topknot",
            ),
        )
        for style in self.art.STYLES.values():
            self.assertEqual(set(style.poses), set(self.art.MASTERS))
        self.assertFalse((CONTENT / "compact_high_ponytail").exists())

    def test_every_master_passes_locked_template_contract(self) -> None:
        for style_id in self.art.STYLES:
            for master in self.art.MASTERS:
                image = self.art.rasterize(style_id, master)
                report = self.art.validate_master(style_id, master, image)
                self.assertEqual(report["fourConnectedComponents"], 1)
                self.assertGreater(report["scalpAttachmentPixels"], 0)
                self.assertEqual(report["outsideAllowedPixels"], 0)
                if style_id != "rare_spikes":
                    self.assertEqual(report["protectedAnatomyPixels"], 0)

    def test_rare_spikes_raw_rgba_is_frozen(self) -> None:
        for master, expected in self.art.RARE_RGBA_SHA256.items():
            actual = hashlib.sha256(self.art.rasterize("rare_spikes", master).tobytes()).hexdigest()
            self.assertEqual(actual, expected, master)

    def test_style_silhouettes_keep_their_distinguishing_features(self) -> None:
        buzz = self.relative_points("faded_buzzcut", "north")
        crop = self.relative_points("textured_crop", "north")
        rare = self.relative_points("rare_spikes", "north")
        self.assertGreater(min(y for _, y in buzz), min(y for _, y in crop))
        self.assertGreater(min(y for _, y in buzz), min(y for _, y in rare))

        mohawk_front = self.art.rasterize("mohawk", "north")
        front_frame = next(
            frame for frame in self.art.load_v2_template().frames if frame.master == "north"
        )
        opaque_above_crown = {
            (x - front_frame.crown[0], y - front_frame.crown[1])
            for y in range(mohawk_front.height)
            for x in range(mohawk_front.width)
            if mohawk_front.getpixel((x, y))[3] == 255 and y < front_frame.crown[1]
        }
        # The accepted c60 front ridge is nine V2 pixels wide: broad enough to
        # avoid the rejected needle-horn read, while still narrow head-on.
        self.assertLessEqual(max(x for x, _ in opaque_above_crown) - min(x for x, _ in opaque_above_crown), 9)
        self.assertTrue(any(0 < alpha < 255 for *_, alpha in mohawk_front.getdata()))

        topknot_west = self.relative_points("high_topknot", "west")
        self.assertLessEqual(min(x for x, _ in topknot_west), -24)
        self.assertLessEqual(min(y for _, y in topknot_west), -3)
        self.assertGreaterEqual(max(y for x, y in topknot_west if x <= -18), 7)

    def test_generation_is_repeatable_and_matches_content(self) -> None:
        (ROOT / "tmp").mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix="v2-hairstyle-art-", dir=ROOT / "tmp") as temp:
            output = Path(temp) / "collection"
            first = self.art.generate_collection(output)
            first_tree = tree_sha256(output)
            second = self.art.generate_collection(output)
            second_tree = tree_sha256(output)
            self.assertTrue(first["valid"])
            self.assertTrue(second["valid"])
            self.assertEqual(first_tree, second_tree)
            self.assertEqual(len(first_tree), 42)
            for style_id in self.art.STYLES:
                for master in self.art.MASTERS:
                    generated = output / style_id / "masters" / f"{master}.png"
                    checked_in = CONTENT / style_id / "masters" / f"{master}.png"
                    self.assertEqual(generated.read_bytes(), checked_in.read_bytes())


if __name__ == "__main__":
    unittest.main()
