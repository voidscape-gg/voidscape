from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image
import jsonschema
import yaml

from appearance_studio.paths import REPO_ROOT, TOOL_DIR
from appearance_studio.v2_template import (
    DEFAULT_TEMPLATE, MASTERS, UPPER_HAIR_ENVELOPES, load_v2_template,
)


class PaperdollV2TemplateTest(unittest.TestCase):
    def variant(self, mutate) -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        payload = yaml.safe_load(DEFAULT_TEMPLATE.read_text())
        mutate(payload)
        path = Path(temporary.name) / "template.yaml"
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        return path

    def test_every_new_schema_is_valid_json_and_draft_2020_12(self):
        schemas = sorted((TOOL_DIR / "schema").glob("paperdoll-v2-*.json"))
        self.assertGreaterEqual(len(schemas), 3)
        for path in schemas:
            schema = json.loads(path.read_text())
            jsonschema.Draft202012Validator.check_schema(schema)

    def test_template_is_exact_two_x_of_digest_locked_v1(self):
        template = load_v2_template()
        self.assertEqual("rsc-player-2x-v1", template.key)
        self.assertEqual(hashlib.sha256(template.source.path.read_bytes()).hexdigest(), template.source_digest)
        self.assertEqual(list(range(18)), [frame.offset for frame in template.frames])
        for v2, v1 in zip(template.frames, template.source.frames):
            self.assertEqual((v1.size[0] * 2, v1.size[1] * 2), v2.size)
            self.assertEqual((v1.crown[0] * 2, v1.crown[1] * 2), v2.crown)
            self.assertEqual(v1.master, v2.master)
        for master in MASTERS:
            v2_profile = template.pose_profiles[master]
            v1_profile = template.source.pose_profiles[master]
            self.assertEqual(v1_profile.visual_pose, v2_profile.visual_pose)
            for name, point in v1_profile.landmarks.items():
                expected = None if point is None else (point[0] * 2, point[1] * 2)
                self.assertEqual(expected, v2_profile.landmarks[name])
            for role, reference in v1_profile.masks.items():
                actual = v2_profile.masks[role]
                if reference is None:
                    self.assertIsNone(actual)
                    self.assertIsNone(v2_profile.mask_sha256[role])
                    continue
                with Image.open(reference.path) as image:
                    expected = image.resize((image.width * 2, image.height * 2), Image.Resampling.NEAREST).convert("1")
                self.assertEqual(expected.size, actual.size)
                if role == "hair_allowed":
                    self.assertTrue(all(
                        not expected.getpixel((x, y)) or actual.getpixel((x, y))
                        for y in range(actual.height) for x in range(actual.width)
                    ))
                    frame = next(item for item in template.frames if item.master == master)
                    for dx, dy in UPPER_HAIR_ENVELOPES[master]:
                        self.assertTrue(actual.getpixel((frame.crown[0] + dx, frame.crown[1] + dy)))
                    self.assertTrue(any(actual.getpixel((x, 0)) for x in range(actual.width)))
                    self.assertLessEqual(min(x for x in range(actual.width) for y in range(frame.crown[1] + 9)
                                             if actual.getpixel((x, y))), frame.crown[0] - 24)
                    self.assertGreaterEqual(max(x for x in range(actual.width) for y in range(frame.crown[1] + 9)
                                                if actual.getpixel((x, y))), frame.crown[0] + 24)
                else:
                    self.assertEqual(expected.tobytes(), actual.tobytes())

    def test_schema_validates_canonical_template(self):
        schema = json.loads((TOOL_DIR / "schema/paperdoll-v2-template.schema.json").read_text())
        jsonschema.validate(yaml.safe_load(DEFAULT_TEMPLATE.read_text()), schema)

    def test_source_digest_crown_anchor_and_mask_drift_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "source template changed"):
            load_v2_template(self.variant(
                lambda value: value["source_template"].update(sha256="0" * 64)
            ))
        with self.assertRaisesRegex(ValueError, "crowns.*exact doubled"):
            load_v2_template(self.variant(
                lambda value: value["groups"][5]["crowns"][1].__setitem__(0, 43)
            ))
        with self.assertRaisesRegex(ValueError, "pose profile west differs"):
            load_v2_template(self.variant(
                lambda value: value["pose_profiles"]["west"]["landmarks"].update(nose_tip=[15, 18])
            ))
        with self.assertRaisesRegex(ValueError, "derived 2x mask tree digest changed"):
            load_v2_template(self.variant(
                lambda value: value["derived_masks"].update(tree_sha256="f" * 64)
            ))
        with self.assertRaisesRegex(ValueError, "upper hair envelopes differ"):
            load_v2_template(self.variant(
                lambda value: value["upper_hair_envelopes"]["north"][0].__setitem__(1, -17)
            ))


if __name__ == "__main__":
    unittest.main()
