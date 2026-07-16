from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
import zipfile
from dataclasses import replace
from pathlib import Path

from PIL import Image
import yaml

from appearance_studio.art_qa import ArtThresholds, compile_and_validate_draft, validate_compiled_layer, validate_draft_root
from appearance_studio.compiler import CompileResult, expand_rigid_layer
from appearance_studio.geometry import derive_sprite
from appearance_studio.look import load_locked_base_frames, load_look_manifest
from appearance_studio.paths import CLIENT_ARCHIVE, MD5_FILE, REGISTRY_PATH, REPO_ROOT, SERVER_ARCHIVE
from appearance_studio.registry import load_registry
from appearance_studio.template import load_template


TEMPLATE_PATH = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"
LOOK_PATH = REPO_ROOT / "content/appearance/proposals/future_mullet_mustache.yaml"
DRAFT_ROOT = REPO_ROOT / "tmp/appearance-studio/gate5/mullet-mustache-draft"


def _masters(template, kind):
    masters = {}
    for frame in template.frames:
        if frame.master in masters:
            continue
        image = Image.new("RGBA", frame.size, (0, 0, 0, 0))
        profile = template.pose_profiles[frame.master]
        allowed_role, contact_role = ("facial_hair_allowed", "upper_lip_attachment") \
            if kind == "facial-hair" else ("hair_allowed", "scalp_attachment")
        if profile.masks[allowed_role] is not None:
            allowed = Image.open(profile.masks[allowed_role].path)
            contact = Image.open(profile.masks[contact_role].path)
            protected = Image.open(profile.masks["protected_anatomy"].path)
            clearance_ref = profile.masks["face_clearance"]
            clearance = Image.open(clearance_ref.path) if kind == "hair" and clearance_ref is not None else None
            neck_ref = profile.masks["neck_clearance"]
            neck = Image.open(neck_ref.path) if kind == "hair" and neck_ref is not None else None
            points = [
                (x, y) for y in range(image.height) for x in range(image.width)
                if allowed.getpixel((x, y)) and contact.getpixel((x, y))
                and not protected.getpixel((x, y)) and not (clearance and clearance.getpixel((x, y)))
                and not (neck and neck.getpixel((x, y)))
            ]
            for x, y in points[:10]:
                image.putpixel((x, y), (132, 132, 132, 255))
        masters[frame.master] = image
    return masters


class ArtQaTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = load_template(TEMPLATE_PATH)
        cls.base = load_locked_base_frames(cls.template)

    def test_quantitative_hair_and_mustache_drafts_pass(self):
        for kind in ("hair", "facial-hair"):
            hidden = ("south-west", "south") if kind == "facial-hair" else ()
            result = expand_rigid_layer(
                _masters(self.template, kind), kind, "hair-mask", self.template, hidden_masters=set(hidden)
            )
            report = validate_compiled_layer(
                result, kind, self.template, self.base, hidden_masters=hidden,
                thresholds=ArtThresholds(1, 100, 3, 1, 0.1, 0.0),
            )
            self.assertTrue(report["valid"], report["findings"])
            self.assertEqual(18, len(report["frames"]))
            self.assertEqual(12 if kind == "hair" else 12, len(report["mirrors"]))
            self.assertTrue(all(frame["hidden"] or frame["contact_ratio"] > 0 for frame in report["frames"]))
            if hidden:
                self.assertTrue(all(frame["opaque_pixels"] == 0 for frame in report["frames"] if frame["hidden"]))

    def test_flicker_and_excess_detached_components_fail(self):
        result = expand_rigid_layer(_masters(self.template, "hair"), "hair", "hair-mask", self.template)
        frames = list(result.frames)
        changed = frames[1].canvas.copy()
        for point in ((frames[1].canvas.width - 1, 0), (0, 0), (0, frames[1].canvas.height - 1)):
            changed.putpixel(point, (132, 132, 132, 255))
        frames[1] = replace(frames[1], canvas=changed, sprite=derive_sprite(changed))
        report = validate_compiled_layer(CompileResult(tuple(frames), result.findings), "hair", self.template, self.base)
        self.assertFalse(report["valid"])
        self.assertTrue(any("flicker" in finding for finding in report["findings"]))
        self.assertTrue(any("components exceeds" in finding for finding in report["findings"]))

    def test_tmp_master_directory_is_read_only_and_deterministic(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name, image in _masters(self.template, "facial-hair").items():
                image.save(root / f"{name}.png")
            before = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in root.iterdir()}
            first, first_report = compile_and_validate_draft(
                root, "facial-hair", "hair-mask", self.template, self.base,
                hidden_masters=("south-west", "south"),
            )
            second, second_report = compile_and_validate_draft(
                root, "facial-hair", "hair-mask", self.template, self.base,
                hidden_masters=("south-west", "south"),
            )
            after = {path.name: hashlib.sha256(path.read_bytes()).hexdigest() for path in root.iterdir()}
            self.assertEqual(before, after)
            self.assertEqual(first_report, second_report)
            self.assertEqual(
                [frame.canvas.tobytes() for frame in first.frames],
                [frame.canvas.tobytes() for frame in second.frames],
            )

    def test_gate5_v1_draft_outputs_are_revoked_when_present(self):
        if not DRAFT_ROOT.is_dir():
            self.skipTest("Gate 5 deterministic draft has not been generated")
        with self.assertRaisesRegex(ValueError, "voidscape-draft-look/v1.*revoked"):
            validate_draft_root(DRAFT_ROOT, LOOK_PATH, TEMPLATE_PATH)

    def test_reconstructed_v1_art_qa_is_revoked_and_proposal_stays_inactive(self):
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as temporary:
            root = Path(temporary)
            (root / "report.json").write_text(json.dumps({
                "schema": "voidscape-draft-look/v1",
                "geometryContract": "voidscape-paperdoll-geometry/v1",
                "shipping": False,
            }))
            with self.assertRaisesRegex(ValueError, "voidscape-draft-look/v1.*revoked"):
                validate_draft_root(root, LOOK_PATH, TEMPLATE_PATH)

        look = load_look_manifest(LOOK_PATH)
        self.assertFalse(look.publishable)
        self.assertFalse(look.preset["selectable"])
        self.assertIsNone(look.art)

    def test_proposal_cannot_touch_production_or_activate_preset(self):
        look = load_look_manifest(LOOK_PATH)
        registry, findings = load_registry(REGISTRY_PATH)
        self.assertIsNotNone(registry)
        self.assertFalse(findings)
        self.assertFalse(look.publishable)
        self.assertFalse(look.preset["selectable"])
        self.assertIsNone(look.art)
        reserved = next(entry for entry in registry.entries if entry.key == look.key)
        self.assertEqual("reserved", reserved.state)
        self.assertEqual(look.allocation["appearance_id"], reserved.appearance_id)
        self.assertEqual(look.allocation["sprite_base"], reserved.sprite_base)
        for generated in (
            REPO_ROOT / "Client_Base/src/com/openrsc/client/entityhandling/GeneratedLookPresets.java",
            REPO_ROOT / "server/src/com/openrsc/server/appearance/GeneratedLookPresets.java",
        ):
            source = generated.read_text()
            self.assertIn(look.key, source)
            self.assertIn(
                f'new Entry({reserved.appearance_id}, "{look.key}", "{look.name}", false, '
                f'{look.compatibility["retro_fallback_appearance_id"]})',
                source,
            )
        first = look.allocation["sprite_base"]
        last = first + look.allocation["reservation_size"] - 1
        for archive_path in (CLIENT_ARCHIVE, SERVER_ARCHIVE):
            with zipfile.ZipFile(archive_path) as archive:
                names = set(archive.namelist())
                self.assertFalse({str(index) for index in range(first, last + 1)} & names)
        client_md5 = hashlib.md5(CLIENT_ARCHIVE.read_bytes()).hexdigest()
        matching = [line for line in MD5_FILE.read_text().splitlines()
                    if line.endswith("*./video/Authentic_Sprites.orsc")]
        self.assertEqual([f"{client_md5} *./video/Authentic_Sprites.orsc"], matching)


if __name__ == "__main__":
    unittest.main()
