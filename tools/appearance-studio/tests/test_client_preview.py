from __future__ import annotations

import hashlib
import json
import struct
import tempfile
import unittest
from pathlib import Path
import zipfile

from PIL import Image

from appearance_studio import client_preview as preview
from appearance_studio.paths import REPO_ROOT
from appearance_studio.template import load_template
from appearance_studio.workbench_report import expected_states


def encoded_pixel(rgb=(132, 132, 132), *, logical_width=64, logical_height=102, x_shift=0, y_shift=0):
    return struct.pack(">iiBiiii", 1, 1, 1, x_shift, y_shift, logical_width, logical_height) + bytes((0, *rgb))


class ClientPreviewTest(unittest.TestCase):
    def make_fixture(self):
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        archive = root / "sprites.orsc"
        layers = {}
        with zipfile.ZipFile(archive, "w") as output:
            for slot in range(12):
                base = 1000 + slot * 40
                layers[slot] = preview.AnimationLayer(slot, slot + 1, f"layer{slot}", base, 0x808080,
                                                      0, True, True)
                for offset in range(33):
                    logical_width = 84 if 15 <= offset <= 17 else 64
                    output.writestr(str(base + offset), encoded_pixel(
                        (20 + slot, 40 + slot, 80 + slot), logical_width=logical_width,
                        x_shift=logical_width // 2,
                    ))
        oracle = root / "oracle.json"
        oracle.write_text("{}\n")
        fixture = preview.CompositeFixture(
            root / "fixture.json", "fixture", oracle, hashlib.sha256(oracle.read_bytes()).hexdigest(),
            archive, hashlib.sha256(archive.read_bytes()).hexdigest(), tuple(range(1, 13)), layers,
            0x805030, 0x345C94, 0x5C442C, 0xECDED0,
            {"hair": 2, "top": 3, "bottom": 4, "skin": 0},
            0x121820, (88, 112), (12, 2), (64, 102), 0,
        )
        return root, fixture

    def test_all_30_states_use_exact_direction_order_width_and_combat_centering(self):
        _, fixture = self.make_fixture()
        hashes = hashlib.sha256()
        for index, state in enumerate(expected_states()):
            result = preview.render_composite(fixture, state)
            self.assertEqual(preview.ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]],
                             tuple(trace.slot for trace in result.traces))
            self.assertTrue(all(trace.stored_offset == state["spriteOffset"] +
                                (15 if state["mirrorX"] and 1 <= state["actualAnimDir"] <= 3 else 0)
                                for trace in result.traces))
            expected_width = 84 if state["kind"] == "combat" else 64
            expected_x = 2 if state["kind"] == "combat" else 12
            self.assertTrue(all(trace.width == expected_width and trace.x == expected_x for trace in result.traces))
            hashes.update(result.image.tobytes())
        self.assertEqual("57ca1a2e46e3dfcac260cd225240296b90420bd1b85032cfdf3f562f79b9fe58", hashes.hexdigest())

    def test_has_f_hands_combat_suppression_and_java_negative_truncation(self):
        state = next(item for item in expected_states() if item["direction"] == "east" and item["frame"] == 1)
        has_f = preview.AnimationLayer(3, 1, "f", 0, 0, 0, True, True)
        self.assertEqual((state["spriteOffset"] + 15, 0, 0), preview._layer_offset(has_f, state))
        hand3 = preview.AnimationLayer(3, 1, "hand3", 0, 0, 0, True, False)
        hand4 = preview.AnimationLayer(4, 1, "hand4", 0, 0, 0, True, False)
        self.assertEqual((7, 0, 8), preview._layer_offset(hand3, state))
        self.assertEqual((7, 0, -8), preview._layer_offset(hand4, state))
        combat = next(item for item in expected_states() if item["kind"] == "combat")
        no_combat = preview.AnimationLayer(0, 1, "no-a", 0, 0, 0, False, False)
        self.assertIsNone(preview._layer_offset(no_combat, combat))
        self.assertEqual(-2, preview._jdiv(-5, 2))

    def test_palette_branches_lusters_and_blue_mask_match_live_rules(self):
        red_mask = (255, 100, 100, 255)
        self.assertEqual((255, 100, 100), preview._masked_colour(
            red_mask, 0x123456, 0xFFFFFF, 0, two_masks=False
        ))
        self.assertEqual((0x80 * 255 >> 8, 0x60 * 100 >> 8, 0x40 * 100 >> 8), preview._masked_colour(
            red_mask, 0, 0x806040, 0, two_masks=True
        ))
        self.assertEqual(tuple(channel * 0x5C // 255 for channel in (0x6A, 0x0D, 0xAD)),
                         preview._masked_colour((103, 103, 103, 255), 0x6A0DAD, 0, 0, two_masks=True))
        self.assertEqual(tuple(channel * 0x8C // 255 for channel in (0x4A, 0x2C, 0x6F)),
                         preview._masked_colour((132, 132, 132, 255), 0x4A2C6F, 0, 0, two_masks=True))
        self.assertEqual(tuple(channel * 0xB0 // 255 for channel in (0xF2, 0xC8, 0xBA)),
                         preview._masked_colour((255, 120, 120, 255), 0, 0xF2C8BA, 0, two_masks=True))
        self.assertEqual((77, 0, 0), preview._masked_colour(
            (100, 100, 200, 255), 0, 0, 0xFF0000, two_masks=True
        ))

        single = Image.new("RGB", (1, 1), (64, 128, 192))
        single_frame = preview.SpriteFrame(Image.new("RGBA", (1, 1), red_mask), {"requiresShift": False})
        preview.draw_sprite_clipping(single, single_frame, 0, 0, 1, 1, 0x123456, 0xFFFFFF)
        self.assertEqual(tuple((((value * 255) >> 8) * 255 + old) >> 8
                               for value, old in zip((255, 100, 100), (64, 128, 192))), single.getpixel((0, 0)))

    def test_sidecar_mirror_is_exact_and_contact_sheet_cells_do_not_overlap(self):
        image = Image.new("RGBA", (2, 1)); image.putdata(((10, 20, 30, 255), (50, 60, 70, 255)))
        frame = preview.SpriteFrame(image, {
            "requiresShift": True, "xShift": 1, "yShift": 0, "something1": 6, "something2": 1,
        })
        normal = Image.new("RGB", (6, 1), (1, 2, 3)); mirrored = Image.new("RGB", (6, 1), (1, 2, 3))
        preview.draw_sprite_clipping(normal, frame, 0, 0, 6, 1, 0xFFFFFF, 0xFFFFFF)
        preview.draw_sprite_clipping(mirrored, frame, 0, 0, 6, 1, 0xFFFFFF, 0xFFFFFF, mirror_x=True)
        self.assertEqual(normal.transpose(Image.Transpose.FLIP_LEFT_RIGHT).tobytes(), mirrored.tobytes())

        first = Image.new("RGB", (84, 102), (255, 0, 0))
        second = Image.new("RGB", (88, 112), (0, 255, 0))
        sheet = preview.contact_sheet((first, second), ("first", "second"), scale=1, columns=2)
        self.assertEqual((192, 136), sheet.size)
        self.assertFalse(any(sheet.getpixel((x, y)) == (255, 0, 0) for x in range(96, 192) for y in range(136)))
        self.assertFalse(any(sheet.getpixel((x, y)) == (0, 255, 0) for x in range(96) for y in range(136)))

    def test_absolute_oracle_archive_extracts_to_repo_relative_digest_bound_fixture(self):
        root, fixture = self.make_fixture()
        layers = [
            {"slot": slot, "appearanceId": slot + 1, "definition": {
                "name": f"layer{slot}", "category": "player", "spriteBase": 1000 + slot * 40,
                "charColour": 0x808080, "blueMask": 0, "hasA": True, "hasF": True,
            }} for slot in range(12)
        ]
        captures = []
        for index, state in enumerate(expected_states()):
            inputs = {
                **{key: state[key] for key in ("wantedAnimDir", "actualAnimDir", "mirrorX", "spriteOffset")},
                "stepFrame": state["frame"] * 6 if state["kind"] == "walk" else 0,
                "x": 12, "y": 2, "width": 64, "height": 102, "topPixelSkew": 0,
                "overlayMovement": 0, "hairStyle": 0, "invisible": False, "invulnerable": False,
                "paletteIndices": fixture.palette_indices,
                "resolvedRgb": {"hair": fixture.hair_colour, "top": fixture.top_colour,
                                "bottom": fixture.bottom_colour, "skin": fixture.skin_colour},
                "layerAnimation": list(fixture.layer_animation),
                "layerOrder": list(preview.ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]]),
                "layers": layers,
            }
            oracle_image = preview.render_composite(fixture, state).image
            oracle_path = root / f"oracle-{index:02d}.png"; oracle_image.save(oracle_path)
            captures.append({**state, "renderInputs": inputs, "isolatedRaster": {
                "pngPath": str(oracle_path), "pngSha256": hashlib.sha256(oracle_path.read_bytes()).hexdigest(),
                "rawRgbSha256": preview.raw_rgb_sha256(oracle_image),
                "width": 88, "height": 112, "backgroundRgb": 0x121820,
                "crop": {"x": 0, "y": 0, "width": 88, "height": 112},
            }})
        report = {
            "spriteArchive": {"path": str(fixture.archive.resolve()), "sha256": fixture.archive_digest},
            "captures": captures,
        }
        report_path = root / "workbench.json"; report_path.write_text(json.dumps(report))
        fixture_path = root / "composite-fixture.json"
        extracted = preview.extract_composite_fixture(report_path, fixture_path)
        self.assertFalse(Path(extracted["spriteArchive"]["path"]).is_absolute())
        loaded = preview.load_composite_fixture(fixture_path)
        self.assertEqual(report_path.resolve(), loaded.oracle_report)
        self.assertEqual(fixture.archive.resolve(), loaded.archive)
        comparison = preview.write_oracle_comparison(
            fixture_path, report_path, root / "client-preview",
            load_template(REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"),
        )
        self.assertTrue(comparison["valid"])
        self.assertEqual((0, 0), (comparison["mismatchedFrames"], comparison["mismatchedPixels"]))
        with Image.open(root / "client-preview/contact-sheet.png") as sheet:
            self.assertEqual((2880, 1888), sheet.size)


if __name__ == "__main__":
    unittest.main()
