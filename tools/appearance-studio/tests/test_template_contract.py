from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image
import yaml

from appearance_studio.paths import REPO_ROOT
from appearance_studio.geometry import expand_crop
from appearance_studio.template import (
    COORDINATE_CONVENTION,
    LANDMARK_NAMES,
    MASK_NAMES,
    REFERENCE_NAMES,
    VISUAL_POSES,
    load_template,
)


TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


class TemplateContractTest(unittest.TestCase):
    def write_variant(self, mutate) -> Path:
        payload = yaml.safe_load(TEMPLATE.read_text())
        mutate(payload)
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "template.yaml"
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        return path

    def test_v2_template_exposes_locked_references_pose_anatomy_and_masks(self):
        template = load_template(TEMPLATE)
        self.assertEqual(REFERENCE_NAMES, set(template.reference_dirs))
        self.assertEqual(REFERENCE_NAMES, set(template.reference_digests))
        self.assertEqual(template.reference_dirs["head4"], template.locked_base_reference)
        self.assertEqual(COORDINATE_CONVENTION, template.coordinate_convention)
        self.assertEqual(VISUAL_POSES, {profile.visual_pose for profile in template.pose_profiles.values()})

        for master, profile in template.pose_profiles.items():
            self.assertEqual(LANDMARK_NAMES, set(profile.landmarks), master)
            self.assertEqual(MASK_NAMES, set(profile.masks), master)
            expected_size = next(frame.size for frame in template.frames if frame.master == master)
            for role, reference in profile.masks.items():
                if reference is None:
                    continue
                self.assertEqual(expected_size, reference.size, f"{master}.{role}")
                self.assertEqual(reference.sha256, hashlib.sha256(reference.path.read_bytes()).hexdigest())
                with Image.open(reference.path) as image:
                    self.assertEqual("PNG", image.format)
                    self.assertEqual("1", image.mode)
                    self.assertEqual(expected_size, image.size)

    def test_combat_landmarks_translate_only_by_crown_delta(self):
        template = load_template(TEMPLATE)
        profile = template.pose_profiles["combat-west"]
        point = profile.landmarks["upper_lip"]
        self.assertIsNotNone(point)
        frames = [frame for frame in template.frames if frame.master == "combat-west"]
        absolute = [(frame.crown[0] + point[0], frame.crown[1] + point[1]) for frame in frames]
        self.assertEqual((5, 0), (absolute[1][0] - absolute[0][0], absolute[1][1] - absolute[0][1]))
        self.assertEqual((11, 0), (absolute[2][0] - absolute[0][0], absolute[2][1] - absolute[0][1]))

    def test_mask_digest_and_coordinate_convention_fail_closed(self):
        payload = yaml.safe_load(TEMPLATE.read_text())
        target = next(
            (master, role)
            for master, profile in payload["pose_profiles"].items()
            for role, reference in profile["masks"].items()
            if reference is not None
        )

        def change_digest(value):
            master, role = target
            value["pose_profiles"][master]["masks"][role]["sha256"] = "0" * 64

        with self.assertRaisesRegex(ValueError, "mask .* digest changed"):
            load_template(self.write_variant(change_digest))

        with self.assertRaisesRegex(ValueError, "unsupported paperdoll coordinate convention"):
            load_template(self.write_variant(
                lambda value: value["coordinate_convention"].update(fractional_center_rounding="nearest")
            ))

    def test_group_and_runtime_mirror_contracts_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "master/offset/canvas contract"):
            load_template(self.write_variant(
                lambda value: value["groups"][0].update(offsets=[3, 4, 5])
            ))
        with self.assertRaisesRegex(ValueError, "runtime mirrors.*locked v2 contract"):
            load_template(self.write_variant(
                lambda value: value["runtime_mirrors"].update(east="north-west")
            ))

    def test_visible_and_rear_facial_contracts_fail_closed(self):
        with self.assertRaisesRegex(ValueError, "visible-face pose.*upper_lip.*required"):
            load_template(self.write_variant(
                lambda value: value["pose_profiles"]["west"]["landmarks"].update(upper_lip=None)
            ))
        with self.assertRaisesRegex(ValueError, "rear pose.*upper_lip.*must be null"):
            load_template(self.write_variant(
                lambda value: value["pose_profiles"]["south"]["landmarks"].update(upper_lip=[0, 12])
            ))

    def test_pose_profile_swap_and_in_bounds_landmark_drift_fail_closed(self):
        def swap_front_and_rear(value):
            value["pose_profiles"]["north"], value["pose_profiles"]["south"] = (
                value["pose_profiles"]["south"], value["pose_profiles"]["north"],
            )

        with self.assertRaisesRegex(ValueError, "pose profile north visual pose.*locked 'front'"):
            load_template(self.write_variant(swap_front_and_rear))
        with self.assertRaisesRegex(ValueError, "west landmarks differ from the locked v2 calibration"):
            load_template(self.write_variant(
                lambda value: value["pose_profiles"]["west"]["landmarks"].update(upper_lip=[10, 12])
            ))

    def test_authentic_long_hair_and_beard_fit_pose_envelopes(self):
        template = load_template(TEMPLATE)
        for master, profile in template.pose_profiles.items():
            frame = next(item for item in template.frames if item.master == master)
            hair_path = template.reference_dirs["fhead1"] / f"frame_{frame.offset:02d}.png"
            hair = expand_crop(
                Image.open(hair_path).convert("RGBA"),
                json.loads(hair_path.with_suffix(".png.json").read_text()),
            )
            with Image.open(profile.masks["hair_allowed"].path) as allowed:
                outside = [
                    (x, y) for y in range(hair.height) for x in range(hair.width)
                    if hair.getpixel((x, y))[3] >= 128
                    and len(set(hair.getpixel((x, y))[:3])) == 1
                    and not allowed.getpixel((x, y))
                ]
            self.assertEqual([], outside, f"{master} authentic long hair outside envelope")

            if profile.landmarks["upper_lip"] is None:
                continue
            beard_path = template.reference_dirs["head3"] / f"frame_{frame.offset:02d}.png"
            beard = expand_crop(
                Image.open(beard_path).convert("RGBA"),
                json.loads(beard_path.with_suffix(".png.json").read_text()),
            )
            lip_y = frame.crown[1] + profile.landmarks["upper_lip"][1]
            with Image.open(profile.masks["facial_hair_allowed"].path) as allowed:
                outside = [
                    (x, y) for y in range(max(0, lip_y - 2), beard.height) for x in range(beard.width)
                    if beard.getpixel((x, y))[3] >= 128
                    and len(set(beard.getpixel((x, y))[:3])) == 1
                    and not allowed.getpixel((x, y))
                ]
            self.assertEqual([], outside, f"{master} authentic beard outside envelope")


if __name__ == "__main__":
    unittest.main()
