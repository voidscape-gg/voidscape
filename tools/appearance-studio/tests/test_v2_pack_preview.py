from __future__ import annotations

import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from PIL import Image
import jsonschema

from appearance_studio.paths import REPO_ROOT, TOOL_DIR
from appearance_studio.v2_archive import (
    decode_v2_sprite, parse_properties, validate_v2_pack, write_v2_pack,
)
from appearance_studio.v2_build import _connected_components, build_v2_workspace
from appearance_studio.v2_compiler import (
    V2CompiledFrame, V2Sidecar, compile_v2_workspace, expand_rgba,
)
from appearance_studio.v2_preview import draw_argb_sprite_clipping, render_v2_stack
from appearance_studio.v2_template import MASTERS, load_v2_template
from appearance_studio.v2_workspace import init_v2_workspace
from appearance_studio.workbench_report import expected_states


class PaperdollV2PackPreviewTest(unittest.TestCase):
    def workspace(self) -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name) / "proof"

    @staticmethod
    def _first_overlap(left: Image.Image, right: Image.Image) -> tuple[int, int]:
        for y in range(left.height):
            for x in range(left.width):
                if left.getpixel((x, y)) and right.getpixel((x, y)):
                    return x, y
        raise AssertionError("expected masks to overlap")

    def paint_valid_proof_pixels(self, root: Path, manifest: dict) -> None:
        template = load_v2_template()
        assets = {asset["id"]: asset for asset in manifest["assets"]}
        for master in MASTERS:
            spec = next(frame for frame in template.frames if frame.master == master)
            profile = template.pose_profiles[master]
            # A soft-alpha, non-legacy grayscale proves arbitrary RGBA survives.
            hair_point = self._first_overlap(profile.masks["hair_allowed"], profile.masks["scalp_attachment"])
            hair_path = root / assets["hair_rare_spikes"]["channels"][0]["masters"][master]
            with Image.open(hair_path) as source:
                hair = source.copy()
            hair.putpixel(hair_point, (117, 117, 117, 127))
            hair.save(hair_path, format="PNG")

            head_skin = root / assets["native_head"]["channels"][0]["masters"][master]
            with Image.open(head_skin) as source:
                skin = source.copy()
            skin.putpixel((spec.crown[0], spec.crown[1] + 20), (255, 255, 255, 255))
            skin.save(head_skin, format="PNG")

    def test_compiler_preserves_soft_alpha_grayscale_crop_and_doubled_crown_deltas(self):
        root = self.workspace()
        manifest = init_v2_workspace("proof", root)
        self.paint_valid_proof_pixels(root, manifest)
        result = compile_v2_workspace(root)
        hair = result.asset("hair_rare_spikes").channels[0]
        self.assertEqual((117, 117, 117, 127), hair.frames[15].cropped.getpixel((0, 0)))
        source_point = hair.frames[15].sidecar.x_shift, hair.frames[15].sidecar.y_shift
        self.assertEqual((source_point[0] + 10, source_point[1]),
                         (hair.frames[16].sidecar.x_shift, hair.frames[16].sidecar.y_shift))
        self.assertEqual((source_point[0] + 22, source_point[1]),
                         (hair.frames[17].sidecar.x_shift, hair.frames[17].sidecar.y_shift))
        for frame in hair.frames:
            self.assertEqual(frame.canvas.tobytes(), expand_rgba(frame.cropped, frame.sidecar).tobytes())

    def test_pack_is_complete_deterministic_digest_bound_and_raw_argb(self):
        root = self.workspace()
        manifest = init_v2_workspace("proof", root)
        self.paint_valid_proof_pixels(root, manifest)
        result = compile_v2_workspace(root)
        first = write_v2_pack(result, root / "first.orsc")
        second = write_v2_pack(result, root / "second.orsc")
        self.assertEqual(first.sha256, second.sha256)
        self.assertEqual((root / "first.orsc").read_bytes(), (root / "second.orsc").read_bytes())
        validation = validate_v2_pack(first.path)
        self.assertEqual(216, validation["entryCount"])
        with zipfile.ZipFile(first.path) as archive:
            self.assertTrue(all(item.date_time == (1980, 1, 1, 0, 0, 0) for item in archive.infolist()))
            registry = parse_properties(archive.read("registry.properties"))
            self.assertEqual("legacy_head,legacy_body,legacy_legs,native_head,hair_rare_spikes",
                             registry["asset.ids"])
            self.assertEqual("control,rare_hair", registry["render.stack.ids"])
            entry = registry["asset.hair_rare_spikes.frame.00.hair.entry"]
            decoded = decode_v2_sprite(archive.read(entry))
            self.assertEqual((117, 117, 117, 127), decoded.image.getpixel((0, 0)))

        tampered = root / "missing.orsc"
        with zipfile.ZipFile(first.path) as source, zipfile.ZipFile(tampered, "w") as output:
            for info in source.infolist():
                if info.filename != "sprites/hair_rare_spikes/hair/frame_17":
                    output.writestr(info, source.read(info.filename))
        with self.assertRaisesRegex(ValueError, "sorted|missing"):
            validate_v2_pack(tampered)

    def test_argb_renderer_matches_java_alpha_and_tint_integer_rules(self):
        pixel = Image.new("RGBA", (1, 1), (200, 100, 50, 128))
        frame = V2CompiledFrame(
            "test", "hair", "hair", 0, pixel, pixel,
            V2Sidecar(False, 0, 0, 1, 1), False, Path("test.png"), "0" * 64,
        )
        target = Image.new("RGB", (1, 1), (10, 20, 30))
        draw_argb_sprite_clipping(
            target, frame, 0, 0, 1, 1, mirror_x=False, colour_transform=0xFF804020,
        )
        tinted = (200 * 128 // 255, 100 * 64 // 255, 50 * 32 // 255)
        expected = tuple((old * 127 + new * 129) >> 8 for old, new in zip((10, 20, 30), tinted))
        self.assertEqual(expected, target.getpixel((0, 0)))

    def test_neutral_skin_ramp_matches_live_trial_skin_body_within_rounding(self):
        trial_skins = (0xF2C8BA, 0xD99A8F, 0xA76A45, 0x6D3F2C, 0x9B9290)
        for skin in trial_skins:
            for shade in (0x76, 0xB0, 0xFF):
                pixel = Image.new("RGBA", (1, 1), (shade, shade, shade, 255))
                frame = V2CompiledFrame(
                    "skin-test", "skin", "skin", 0, pixel, pixel,
                    V2Sidecar(False, 0, 0, 1, 1), False, Path("skin-test.png"), "0" * 64,
                )
                target = Image.new("RGB", (1, 1), (0, 0, 0))
                draw_argb_sprite_clipping(
                    target, frame, 0, 0, 1, 1, mirror_x=False,
                    colour_transform=0xFF000000 | skin,
                )
                v2 = target.getpixel((0, 0))
                ramp = tuple(component * shade // 255 for component in (
                    skin >> 16 & 255, skin >> 8 & 255, skin & 255,
                ))
                legacy = tuple(((component * 255 >> 8) * 255) >> 8 for component in ramp)
                self.assertTrue(all(abs(left - right) <= 2 for left, right in zip(v2, legacy)),
                                (hex(skin), shade, v2, legacy))

    def test_hair_topology_rejects_corner_only_floating_pixels(self):
        self.assertEqual([1, 1], [len(item) for item in _connected_components({(0, 0), (1, 1)})])
        self.assertEqual([2], [len(item) for item in _connected_components({(0, 0), (1, 0)})])

    def test_blank_native_proof_cannot_be_reported_as_a_valid_build(self):
        root = self.workspace()
        init_v2_workspace("blank", root)
        with self.assertRaisesRegex(ValueError, "art-completeness"):
            build_v2_workspace(root)
        report = json.loads((root / "build/report.json").read_text())
        self.assertFalse(report["valid"])
        self.assertFalse(report["artCompleteness"]["valid"])

    def test_build_writes_two_exact_30_state_sets_report_and_one_x_comparisons(self):
        root = self.workspace()
        manifest = init_v2_workspace("proof", root)
        self.paint_valid_proof_pixels(root, manifest)
        first = build_v2_workspace(root)
        first_report = (root / "build/report.json").read_bytes()
        first_pack = (root / "build/Paperdoll_V2.orsc").read_bytes()
        self.assertTrue(first["valid"])
        self.assertTrue(first["alphaPreservation"]["packRoundTripExact"])
        self.assertGreater(first["alphaPreservation"]["softAlphaPixels"], 0)
        self.assertTrue(first["maskValidation"]["valid"])
        self.assertTrue(first["compositeRegionValidation"]["valid"])
        self.assertEqual([30, 30], [len(stack["captures"]) for stack in first["stacks"]])
        self.assertEqual(30, first["oneXComparison"]["frameCount"])
        self.assertFalse(first["oracleParity"]["rare_hair"]["fullLivePanelCompared"])
        for stack in first["stacks"]:
            for capture in stack["captures"]:
                with Image.open(root / capture["path"]) as image:
                    self.assertEqual((176, 224), image.size)
        schema = json.loads((TOOL_DIR / "schema/paperdoll-v2-build-report.schema.json").read_text())
        jsonschema.validate(first, schema)

        second = build_v2_workspace(root)
        self.assertEqual(first_pack, (root / "build/Paperdoll_V2.orsc").read_bytes())
        self.assertEqual(first_report, (root / "build/report.json").read_bytes())
        self.assertEqual(first["pack"]["sha256"], second["pack"]["sha256"])

        result = compile_v2_workspace(root)
        state = expected_states()[0]
        self.assertEqual((176, 224), render_v2_stack(result, "rare_hair", state, v2_only=True).size)
        walk_states = expected_states()[:24]
        for direction_index in range(8):
            phases = walk_states[direction_index * 3:direction_index * 3 + 3]
            rasters = [render_v2_stack(result, "rare_hair", item, v2_only=True).tobytes()
                       for item in phases]
            self.assertEqual(rasters[0], rasters[1], phases[0]["direction"])
            self.assertEqual(rasters[0], rasters[2], phases[0]["direction"])


if __name__ == "__main__":
    unittest.main()
