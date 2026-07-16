from __future__ import annotations

import tempfile
import unittest
import zipfile
from pathlib import Path

from PIL import Image

from voidscim.sprite_io import encode
from voidscim.validate_wielded_cmd import (
    WieldedLayout,
    WieldedValidationError,
    resolve_wielded_layout,
    validate_wielded_archive,
)


def _layout(*, base: int = 100) -> WieldedLayout:
    return WieldedLayout(
        animation_name="testhat",
        item_id=9000,
        item_name="Test hat",
        runtime_index=1,
        appearance_id=2,
        archive_base=base,
        has_a=True,
        has_f=False,
        frame_count=18,
    )


def _sprite_bytes(
    *,
    color: tuple[int, int, int, int] = (120, 70, 30, 255),
    empty: bool = False,
    preserve_alpha: bool = False,
    logical_width: int = 5,
    logical_height: int = 4,
    x_shift: int = 1,
    y_shift: int = 1,
) -> bytes:
    image = Image.new("RGBA", (3, 2), (0, 0, 0, 0))
    if not empty:
        image.putpixel((1, 0), color)
        image.putpixel((1, 1), color)
    return encode(image, {
        "requiresShift": True,
        "xShift": x_shift,
        "yShift": y_shift,
        "something1": logical_width,
        "something2": logical_height,
    }, preserve_alpha=preserve_alpha)


class WieldedLayoutResolutionTest(unittest.TestCase):
    def test_one_based_item_mapping_selects_real_animation_and_base(self) -> None:
        source = """
private static void loadAnimationDefinitions() {
    animations.add(new AnimationDef("body", "player", 1, 0, true, false, 0));
    animations.add(new AnimationDef("testhat", "equipment", 0, 0, true, false, 0));
}
"""
        layout = resolve_wielded_layout(
            "testhat",
            9000,
            entity_handler_text=source,
            server_item={
                "id": 9000,
                "name": "Test hat",
                "isWearable": 1,
                "appearanceID": 2,
            },
        )

        self.assertEqual(1, layout.runtime_index)
        self.assertEqual(2, layout.appearance_id)
        self.assertEqual(27, layout.archive_base)
        self.assertEqual(18, layout.frame_count)
        self.assertTrue(layout.has_a)
        self.assertFalse(layout.has_f)

    def test_item_mapping_mismatch_is_rejected_even_if_name_exists(self) -> None:
        source = """
private static void loadAnimationDefinitions() {
    animations.add(new AnimationDef("body", "player", 1, 0, true, false, 0));
    animations.add(new AnimationDef("testhat", "equipment", 0, 0, true, false, 0));
}
"""
        with self.assertRaisesRegex(WieldedValidationError, "maps to AnimationDef 'body'"):
            resolve_wielded_layout(
                "testhat",
                9000,
                entity_handler_text=source,
                server_item={
                    "id": 9000,
                    "name": "Test hat",
                    "isWearable": 1,
                    "appearanceID": 1,
                },
            )

    def test_active_cowboy_layout_contract(self) -> None:
        """Frame inspection may fail before packing; source numbering must not."""
        layout = resolve_wielded_layout("cowboyhat", 1609)

        self.assertEqual(244, layout.runtime_index)
        self.assertEqual(245, layout.appearance_id)
        self.assertEqual(1890, layout.archive_base)
        self.assertEqual(1907, layout.archive_end)
        self.assertEqual(18, layout.frame_count)


class WieldedArchiveValidationTest(unittest.TestCase):
    def _write_archive(
        self,
        path: Path,
        replacements: dict[int, bytes] | None = None,
        omissions: set[int] | None = None,
    ) -> None:
        replacements = replacements or {}
        omissions = omissions or set()
        layout = _layout()
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
            for index in range(layout.archive_base, layout.archive_end + 1):
                if index in omissions:
                    continue
                archive.writestr(str(index), replacements.get(index, _sprite_bytes()))

    def test_valid_hard_mask_archive_passes_all_frames(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "sprites.orsc"
            self._write_archive(archive)

            report = validate_wielded_archive(
                _layout(),
                archive,
                expected_runtime_index=1,
                expected_appearance_id=2,
                expected_archive_base=100,
            )

        self.assertTrue(report.ok, [issue.format() for issue in report.issues])
        self.assertEqual(18, report.decoded_frames)
        self.assertEqual(36, report.opaque_pixels)

    def test_collects_missing_empty_alpha_chroma_and_bounds_failures(self) -> None:
        layout = _layout()
        replacements = {
            100: _sprite_bytes(empty=True),
            101: _sprite_bytes(color=(255, 0, 255, 255)),
            102: _sprite_bytes(color=(120, 70, 30, 128), preserve_alpha=True),
            103: _sprite_bytes(logical_width=3, x_shift=1),
            104: _sprite_bytes(logical_width=0),
            106: b"not a complete sprite",
            107: _sprite_bytes(color=(0, 255, 0, 255)),
        }
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "sprites.orsc"
            self._write_archive(archive, replacements, omissions={105})

            report = validate_wielded_archive(layout, archive)

        codes = [issue.code for issue in report.issues]
        self.assertIn("empty-frame", codes)
        self.assertIn("chroma-residue", codes)
        self.assertIn("hard-mask", codes)
        self.assertIn("sidecar-bounds", codes)
        self.assertIn("logical-dimensions", codes)
        self.assertIn("missing-frame", codes)
        self.assertIn("decode", codes)
        chroma_messages = [
            issue.message for issue in report.issues if issue.code == "chroma-residue"
        ]
        self.assertTrue(any("#FF00FF" in message for message in chroma_messages))
        self.assertTrue(any("#00FF00" in message for message in chroma_messages))
        self.assertFalse(report.ok)
        self.assertEqual(16, report.decoded_frames)

    def test_expected_layout_values_are_enforced(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            archive = Path(tmp) / "sprites.orsc"
            self._write_archive(archive)

            report = validate_wielded_archive(
                _layout(),
                archive,
                expected_runtime_index=244,
                expected_appearance_id=245,
                expected_archive_base=1890,
            )

        expectations = [
            issue for issue in report.issues if issue.code == "layout-expectation"
        ]
        self.assertEqual(3, len(expectations))


if __name__ == "__main__":
    unittest.main()
