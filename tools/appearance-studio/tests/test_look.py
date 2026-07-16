from __future__ import annotations

import copy
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path

from PIL import Image
import yaml

from appearance_studio.compiler import expand_rigid_layer
from appearance_studio.look import compile_look, load_authoring_layer, load_look_manifest, recommend_look_allocation
from appearance_studio.paths import REGISTRY_PATH, REPO_ROOT
from appearance_studio.registry import load_registry
from appearance_studio.template import load_template
from appearance_studio import __main__ as cli


TOOL_ROOT = Path(__file__).resolve().parents[1]
LOOK = TOOL_ROOT / "tests/fixtures/look_mullet_mustache.yaml"
PROPOSAL = REPO_ROOT / "content/appearance/proposals/future_mullet_mustache.yaml"
TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


def _masters(template, kind):
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


class LookTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.look = load_look_manifest(LOOK)
        cls.template = load_template(TEMPLATE)
        cls.registry, findings = load_registry(REGISTRY_PATH)
        if cls.registry is None or findings:
            raise AssertionError(findings)
        cls.unreserved_registry = replace(
            cls.registry,
            entries=tuple(entry for entry in cls.registry.entries if entry.key != "future_mullet_mustache"),
        )

    def write_variant(self, mutate):
        payload = yaml.safe_load(LOOK.read_text())
        mutate(payload)
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "look.yaml"
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        return path

    def test_proposed_look_is_reference_manifest_and_not_publishable(self):
        self.assertEqual("proposed", self.look.status)
        self.assertEqual("locked-bald-head", self.look.base_key)
        self.assertEqual(("future_mullet", "future_mustache"), tuple(layer.key for layer in self.look.layers))
        self.assertEqual((10, 20), tuple(layer.order for layer in self.look.layers))
        self.assertEqual(("rigid-head", "rigid-head"), tuple(layer.manifest.propagation for layer in self.look.layers))
        self.assertEqual(("mullet", "mustache"), tuple(layer.manifest.semantic_profile for layer in self.look.layers))
        self.assertEqual(
            ({"scalp_attachment", "nape_tail"}, {"upper_lip_attachment"}),
            tuple(set(layer.manifest.required_masks) for layer in self.look.layers),
        )
        self.assertEqual(1, self.look.runtime["char_colour"])
        self.assertEqual(5, self.look.runtime["gender_model"])
        self.assertEqual(0, self.look.runtime["paperdoll_slot"])
        self.assertEqual(8, self.look.compatibility["retro_fallback_appearance_id"])
        self.assertEqual("unsupported", self.look.compatibility["custom_sprites"])
        self.assertFalse(self.look.publishable)
        self.assertFalse(self.look.preset["selectable"])

    def test_repository_proposal_loads_but_confers_no_ownership(self):
        proposal = load_look_manifest(PROPOSAL)
        self.assertFalse(proposal.publishable)
        self.assertEqual("draft", proposal.status)
        self.assertEqual("reserved", proposal.allocation["state"])
        self.assertTrue(proposal.approval.get("allocation_approved", False))
        self.assertTrue(proposal.approval.get("art_brief_approved", False))
        self.assertFalse(proposal.approval.get("production_pixels_approved", False))
        self.assertTrue(all(layer.manifest.status in {"proposed", "draft"} for layer in proposal.layers))

        plan = recommend_look_allocation(proposal, self.registry)
        self.assertEqual("reserved", plan["allocation_state"])
        self.assertTrue(plan["manifest_proposal_matches"])
        self.assertNotIn("registry-reservation-missing", plan["blockers"])
        self.assertFalse(plan["publishable"])
        self.assertEqual([], plan["writes"])

    def test_allocation_advice_is_exact_deterministic_and_read_only(self):
        registry_before = REGISTRY_PATH.read_bytes()
        first = recommend_look_allocation(self.look, self.unreserved_registry)
        second = recommend_look_allocation(self.look, self.unreserved_registry)
        self.assertEqual(first, second)
        self.assertEqual(247, first["proposal"]["appearance_id"])
        self.assertEqual(3705, first["proposal"]["sprite_base"])
        self.assertEqual([3705, 3731], first["proposal"]["reservation"])
        self.assertTrue(first["manifest_proposal_matches"])
        self.assertEqual("advisory-only", first["allocation_state"])
        self.assertTrue(first["archive_occupancy_verified"])
        self.assertFalse(first["publishable"])
        self.assertEqual([], first["writes"])
        self.assertEqual(self.look.compatibility, first["compatibility"])
        self.assertEqual(registry_before, REGISTRY_PATH.read_bytes())
        self.assertNotIn("future_mullet_mustache", {entry.key for entry in self.unreserved_registry.entries})
        reserved = next(entry for entry in self.registry.entries if entry.key == "future_mullet_mustache")
        self.assertEqual("reserved", reserved.state)

    def test_allocation_advice_skips_an_unowned_archive_collision(self):
        occupied = set(range(3705, 3732))
        plan = recommend_look_allocation(
            self.look,
            self.unreserved_registry,
            occupied_sprite_indices=occupied,
        )
        self.assertEqual(3732, plan["proposal"]["sprite_base"])
        self.assertFalse(plan["manifest_proposal_matches"])

    def test_synthetic_components_compile_to_one_deterministic_head_block(self):
        hair = expand_rigid_layer(_masters(self.template, "hair"), "hair", "hair-mask", self.template)
        mustache = expand_rigid_layer(
            _masters(self.template, "facial-hair"), "facial-hair", "hair-mask", self.template,
            hidden_masters={"south-west", "south"},
        )
        first = compile_look(
            self.look,
            self.template,
            {"future_mullet": hair, "future_mustache": mustache},
        )
        second = compile_look(
            self.look,
            self.template,
            {"future_mullet": hair, "future_mustache": mustache},
        )
        self.assertEqual(("future_mullet", "future_mustache"), first.layer_order)
        self.assertEqual(18, len(first.frames))
        self.assertEqual(
            [frame.sprite.image.tobytes() for frame in first.frames],
            [frame.sprite.image.tobytes() for frame in second.frames],
        )
        self.assertTrue(all(frame.sprite.sidecar["something2"] == 102 for frame in first.frames))

    def test_missing_or_extra_compiled_component_fails_closed(self):
        hair = expand_rigid_layer(_masters(self.template, "hair"), "hair", "hair-mask", self.template)
        with self.assertRaisesRegex(ValueError, "compiled layers must be exactly"):
            compile_look(self.look, self.template, {"future_mullet": hair})
        with self.assertRaisesRegex(ValueError, "compiled layers must be exactly"):
            compile_look(
                self.look,
                self.template,
                {"future_mullet": hair, "future_mustache": hair, "unexpected": hair},
            )

    def test_proposed_or_artless_look_cannot_enter_selectable_presets(self):
        path = self.write_variant(lambda payload: payload["preset"].update(selectable=True))
        with self.assertRaisesRegex(ValueError, "only an active Look"):
            load_look_manifest(path)

        def approve_without_art(payload):
            payload["status"] = "approved"
            payload["allocation"]["state"] = "reserved"
            for key in payload["approval"]:
                payload["approval"][key] = True

        path = self.write_variant(approve_without_art)
        with self.assertRaisesRegex(ValueError, "requires allocation, art"):
            load_look_manifest(path)

    def test_duplicate_order_and_escaping_component_paths_are_rejected(self):
        path = self.write_variant(lambda payload: payload["layers"][1].update(order=10))
        with self.assertRaisesRegex(ValueError, "duplicate Look layer order"):
            load_look_manifest(path)

        path = self.write_variant(lambda payload: payload["layers"][0].update(manifest="../escape.yaml"))
        with self.assertRaisesRegex(ValueError, "escapes repository root"):
            load_look_manifest(path)

    def test_layer_semantic_profile_contract_fails_closed(self):
        payload = yaml.safe_load((TOOL_ROOT / "tests/fixtures/hair_layer.yaml").read_text())
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        path = Path(temporary.name) / "layer.yaml"
        payload["required_masks"] = ["upper_lip_attachment"]
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "mullet.*required masks.*nape_tail.*scalp_attachment"):
            load_authoring_layer(path)

        payload["required_masks"] = ["scalp_attachment", "nape_tail"]
        payload["kind"] = "facial-hair"
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "mullet semantic profile requires kind hair"):
            load_authoring_layer(path)

    def test_look_plan_cli_is_advisory_only(self):
        args = cli.build_parser().parse_args(["look-plan", "--manifest", str(LOOK)])
        self.assertEqual(str(LOOK), args.manifest)
        self.assertIs(args.func, cli._look_plan)


if __name__ == "__main__":
    unittest.main()
