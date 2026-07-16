from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.compiler import compose_look, expand_rigid_layer
from appearance_studio.geometry import derive_sprite, expand_crop, runtime_mirror
from appearance_studio.paths import REPO_ROOT
from appearance_studio.template import load_template


TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


def masters(template, kind):
    result = {}
    for frame in template.frames:
        if frame.master in result:
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
            point = next(
                (x, y) for y in range(image.height) for x in range(image.width)
                if allowed.getpixel((x, y)) and contact.getpixel((x, y))
                and not protected.getpixel((x, y)) and not (clearance and clearance.getpixel((x, y)))
                and not (neck and neck.getpixel((x, y)))
            )
            image.putpixel(point, (132, 132, 132, 255))
        result[frame.master] = image
    return result


class CompilerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = load_template(TEMPLATE)

    def test_locked_template_and_integer_combat_propagation(self):
        self.assertEqual(list(range(18)), [frame.offset for frame in self.template.frames])
        result = expand_rigid_layer(masters(self.template, "hair"), "hair", "hair-mask", self.template)
        self.assertFalse(result.findings)
        boxes = [result.frames[offset].canvas.getbbox() for offset in (15, 16, 17)]
        self.assertEqual((5, 0, 5, 0), tuple(b - a for a, b in zip(boxes[0], boxes[1])))
        self.assertEqual((11, 0, 11, 0), tuple(b - a for a, b in zip(boxes[0], boxes[2])))

    def test_non_rigid_head_propagation_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "unsupported propagation mode 'pose-warp'.*rigid-head"):
            expand_rigid_layer({}, "hair", "hair-mask", self.template, propagation="pose-warp")
        with self.assertRaisesRegex(ValueError, "no pose-mask contract for kind 'hat'"):
            expand_rigid_layer({}, "hat", "hair-mask", self.template)

    def test_sidecar_crop_roundtrip_and_mirror(self):
        canvas = Image.new("RGBA", (64, 102), (0, 0, 0, 0))
        canvas.putpixel((7, 11), (0, 0, 0, 200))
        sprite = derive_sprite(canvas)
        self.assertEqual((1, 1), sprite.image.size)
        self.assertEqual(7, sprite.sidecar["xShift"])
        self.assertEqual(11, sprite.sidecar["yShift"])
        self.assertEqual((1, 0, 0, 255), sprite.image.getpixel((0, 0)))
        self.assertEqual((7, 11, 8, 12), expand_crop(sprite.image, sprite.sidecar).getbbox())
        self.assertEqual(canvas.size, runtime_mirror(runtime_mirror(canvas)).size)

    def test_synthetic_hair_mustache_look_is_18_frames_and_deterministic(self):
        hair = expand_rigid_layer(masters(self.template, "hair"), "hair", "hair-mask", self.template)
        mustache = expand_rigid_layer(
            masters(self.template, "facial-hair"), "facial-hair", "hair-mask", self.template,
            hidden_masters={"south-west", "south"},
        )
        self.assertFalse(hair.findings + mustache.findings)
        base = [Image.new("RGBA", frame.size, (0, 0, 0, 0)) for frame in self.template.frames]
        first = compose_look(base, [hair, mustache])
        second = compose_look(base, [hair, mustache])
        self.assertEqual(18, len(first))
        self.assertEqual([image.tobytes() for image in first], [image.tobytes() for image in second])
        self.assertTrue(all(len(image.getcolors()) >= 2 for image in first))

    def test_overlap_and_palette_fail_closed(self):
        bad = masters(self.template, "hair")
        frame = next(frame for frame in self.template.frames if frame.master == "north")
        protected = Image.open(self.template.pose_profiles["north"].masks["protected_anatomy"].path)
        point = next((x, y) for y in range(frame.size[1]) for x in range(frame.size[0]) if protected.getpixel((x, y)))
        bad["north"].putpixel(point, (1, 2, 3, 255))
        result = expand_rigid_layer(bad, "hair", "hair-mask", self.template)
        self.assertTrue(any("protected anatomy" in finding for finding in result.findings))
        self.assertTrue(any("primary shade" in finding for finding in result.findings))

    def test_hair_face_clearance_mask_is_forbidden(self):
        bad = masters(self.template, "hair")
        profile = self.template.pose_profiles["north"]
        allowed = Image.open(profile.masks["hair_allowed"].path)
        clearance = Image.open(profile.masks["face_clearance"].path)
        point = next(
            (x, y) for y in range(allowed.height) for x in range(allowed.width)
            if allowed.getpixel((x, y)) and clearance.getpixel((x, y))
        )
        bad["north"].putpixel(point, (132, 132, 132, 255))
        result = expand_rigid_layer(bad, "hair", "hair-mask", self.template)
        self.assertTrue(any("protected anatomy" in finding for finding in result.findings))

    def test_optional_layer_hidden_masters_compile_as_empty_frames(self):
        result = expand_rigid_layer(
            masters(self.template, "facial-hair"), "facial-hair", "hair-mask", self.template,
            hidden_masters={"south-west", "south"},
        )
        self.assertFalse(result.findings)
        self.assertTrue(all(result.frames[offset].sprite is None for offset in range(9, 15)))
        self.assertTrue(all(result.frames[offset].canvas.getbbox() is None for offset in range(9, 15)))


if __name__ == "__main__": unittest.main()
