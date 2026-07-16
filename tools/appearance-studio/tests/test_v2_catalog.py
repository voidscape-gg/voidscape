from __future__ import annotations

import json
import hashlib
import shutil
import tempfile
import unittest
from pathlib import Path

from PIL import Image
import jsonschema
import yaml

from appearance_studio.paths import REPO_ROOT
from appearance_studio.v2_archive import validate_v2_pack, write_v2_pack
from appearance_studio.v2_build import build_v2_workspace
from appearance_studio.v2_catalog import (
    DEFAULT_CATALOG, generated_collection_contract, load_v2_catalog,
)
from appearance_studio.v2_compiler import compile_v2_workspace
from appearance_studio.v2_preview import render_v2_stack
from appearance_studio.v2_selector_registry import (
    selector_registry_properties_bytes, validate_selector_registry_properties,
)
from appearance_studio.v2_workspace import init_v2_collection_workspace, load_v2_workspace
from appearance_studio.workbench_report import expected_states


STYLE_IDS = [
    "rare_spikes", "faded_buzzcut", "mohawk", "textured_crop",
    "slick_back_undercut", "high_topknot",
]


class PaperdollV2CatalogTest(unittest.TestCase):
    def workspace(self, name: str = "collection") -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name) / name

    def catalog_copy(self) -> Path:
        root = self.workspace("catalog-copy")
        root.mkdir(parents=True)
        path = root / "catalog.yaml"
        shutil.copyfile(DEFAULT_CATALOG, path)
        return path

    def test_catalog_owns_stable_selectors_full_bases_qa_and_fail_closed_hats(self):
        catalog = load_v2_catalog()
        contract = generated_collection_contract(catalog)
        self.assertEqual(["male", "female"], [item["id"] for item in catalog.base_profiles])
        self.assertEqual(STYLE_IDS, [item["id"] for item in catalog.hairstyles])
        self.assertEqual(list(range(1, 7)), [item["selectorId"] for item in catalog.hairstyles])
        self.assertTrue(all(
            item["eligibility"]["baseProfiles"] == ["male", "female"]
            for item in catalog.hairstyles
        ))
        self.assertEqual(18, len(contract["assets"]))
        self.assertEqual(16, len(contract["renderStacks"]))
        self.assertEqual(16, len(contract["qaCases"]))
        self.assertEqual(
            [stack["id"] for stack in contract["renderStacks"]],
            [case["stack"] for case in contract["qaCases"]],
        )
        self.assertEqual(7, len(contract["selectorRegistry"]["entries"]))
        entries = contract["selectorRegistry"]["entries"]
        self.assertEqual(
            [(0, "classic")] + list(zip(range(1, 7), STYLE_IDS)),
            [(item["selectorId"], item["style"]) for item in entries],
        )
        for entry in entries:
            self.assertEqual(["male", "female"], entry["baseProfiles"])
            self.assertEqual(
                {base: f"{base}_control" for base in ("male", "female")},
                entry["qaControlStackByBase"],
            )
            if entry["selectorId"] == 0:
                self.assertEqual("legacy", entry["route"])
                self.assertEqual({}, entry["stackByBase"])
                continue
            self.assertEqual("v2", entry["route"])
            self.assertEqual({"male", "female"}, set(entry["stackByBase"]))
            expected_suffix = entry["style"]
            self.assertEqual(
                {base: f"{base}_{expected_suffix}" for base in ("male", "female")},
                entry["stackByBase"],
            )
        self.assertTrue(all(
            entry["eligibility"]["state"] == "qa-ready"
            and entry["eligibility"]["platforms"] == ["pc"]
            for entry in entries[1:]
        ))
        for profile in contract["baseProfiles"]:
            native = {profile["assets"][name] for name in ("nativeHead", "nativeBody", "nativeLegs")}
            stack = next(item for item in contract["renderStacks"]
                         if item["id"] == f"{profile['id']}_native_base")
            self.assertTrue(native.issubset(stack["assets"]))
        self.assertEqual(
            ["allow-v2", "fallback-legacy"],
            [rule["action"] for rule in contract["hatOcclusionPolicy"]["rules"]],
        )
        self.assertEqual({"anyNonzeroHat": True}, contract["hatOcclusionPolicy"]["rules"][1]["match"])

        registry_payload = {
            **contract["selectorRegistry"],
            "catalog": {
                "path": str(catalog.path.relative_to(REPO_ROOT)),
                "sha256": catalog.digest,
            },
            "template": {
                "path": str(catalog.template.path.relative_to(REPO_ROOT)),
                "sha256": catalog.template.digest,
                "sourceV1Sha256": catalog.template.source_digest,
                "derivedMaskTreeSha256": catalog.template.derived_mask_tree_sha256,
            },
            "pack": {"path": "build/Paperdoll_V2.orsc", "sha256": "1" * 64},
            "baseProfiles": contract["baseProfiles"],
            "hatOcclusionPolicy": contract["hatOcclusionPolicy"],
            "renderStacks": contract["renderStacks"],
        }
        registry_schema = json.loads((
            REPO_ROOT / "tools/appearance-studio/schema/paperdoll-v2-selector-registry.schema.json"
        ).read_text())
        jsonschema.validate(registry_payload, registry_schema)
        properties = selector_registry_properties_bytes(registry_payload)
        self.assertEqual(properties, selector_registry_properties_bytes(registry_payload))
        values = validate_selector_registry_properties(properties, registry_payload)
        self.assertEqual("legacy", values["selector.0.route"])
        self.assertNotIn("selector.0.stack.male", values)
        for selector_id, style in enumerate(STYLE_IDS, 1):
            self.assertEqual("v2", values[f"selector.{selector_id}.route"])
            self.assertEqual(f"male_{style}", values[f"selector.{selector_id}.stack.male"])
            self.assertEqual(f"female_{style}", values[f"selector.{selector_id}.stack.female"])
        corrupted = properties.replace(b"pack.sha256=" + b"1" * 64, b"pack.sha256=" + b"2" * 64)
        with self.assertRaisesRegex(ValueError, "changed=.*pack.sha256"):
            validate_selector_registry_properties(corrupted, registry_payload)

    def test_collection_materializes_full_male_female_sources_and_deterministic_pack(self):
        first = self.workspace("first")
        second = self.workspace("second")
        first_manifest = init_v2_collection_workspace(first)
        second_manifest = init_v2_collection_workspace(second)
        self.assertEqual(first_manifest, second_manifest)
        self.assertEqual(
            ["male", "female"], [item["id"] for item in first_manifest["baseProfiles"]],
        )
        self.assertEqual(16, len(first_manifest["renderStacks"]))
        self.assertEqual(16, len(first_manifest["qaCases"]))
        self.assertEqual(list(range(7)), [
            item["selectorId"] for item in first_manifest["selectorRegistry"]["entries"]
        ])

        rare_source = REPO_ROOT / "content/appearance/v2/hairstyles/rare_spikes/masters/north.png"
        with Image.open(rare_source) as source, Image.open(
            first / "masters/hair_rare_spikes/hair/north.png"
        ) as generated:
            self.assertEqual(source.convert("RGBA").tobytes(), generated.convert("RGBA").tobytes())
        with Image.open(first / "native/female_native_body/top/frame_00.png") as female, Image.open(
            first / "native/male_native_body/top/frame_00.png"
        ) as male:
            self.assertNotEqual(female.convert("RGBA").tobytes(), male.convert("RGBA").tobytes())

        _, loaded, _ = load_v2_workspace(first)
        self.assertEqual(first_manifest, loaded)
        first_result = compile_v2_workspace(first)
        second_result = compile_v2_workspace(second)
        background_value = first_manifest["preview"]["backgroundRgb"]
        background = (
            background_value >> 16 & 255,
            background_value >> 8 & 255,
            background_value & 255,
        )
        for stack in first_manifest["renderStacks"]:
            if stack["mode"] != "live-controls":
                continue
            for state in (expected_states()[0], expected_states()[6]):
                image = render_v2_stack(first_result, stack["id"], state).convert("RGB")
                for start, end in ((4, 60), (60, 124), (124, 208)):
                    self.assertGreater(sum(
                        pixel != background for pixel in
                        image.crop((0, start, image.width, end)).getdata()
                    ), 0, f"{stack['id']} lacks figure pixels in rows {start}:{end}")
        first_pack = write_v2_pack(first_result, first / "first.orsc")
        second_pack = write_v2_pack(second_result, second / "second.orsc")
        self.assertEqual(first_pack.sha256, second_pack.sha256)
        self.assertTrue(validate_v2_pack(first_pack.path)["valid"])

        first_report = build_v2_workspace(first)
        second_report = build_v2_workspace(second)
        self.assertEqual(first_report, second_report)

        def build_tree(root: Path) -> dict[str, str]:
            return {
                path.relative_to(root).as_posix(): hashlib.sha256(path.read_bytes()).hexdigest()
                for path in sorted(item for item in root.rglob("*") if item.is_file())
            }

        # This intentionally includes report.json and every evidence artifact:
        # independent workspace roots must produce byte-identical full trees.
        self.assertEqual(build_tree(first / "build"), build_tree(second / "build"))
        for path in (first / "build/compiled").rglob("frame_*.json"):
            source_path = Path(json.loads(path.read_text())["sourcePath"])
            self.assertFalse(source_path.is_absolute())
            self.assertTrue((first / source_path).is_file())

    def test_selector_archive_and_hat_policy_drift_fail_closed(self):
        path = self.catalog_copy()
        payload = yaml.safe_load(path.read_text())
        payload["hairstyles"][1]["selectorId"] = 1
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "selector ids must be unique"):
            load_v2_catalog(path)

        path = self.catalog_copy()
        payload = yaml.safe_load(path.read_text())
        payload["baseProfiles"][1]["sources"]["body"]["entrySha256"][0] = "0" * 64
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "entry 108 digest changed"):
            load_v2_catalog(path)

        path = self.catalog_copy()
        payload = yaml.safe_load(path.read_text())
        payload["hatOcclusionPolicy"]["rules"][1]["action"] = "allow-v2"
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "hat policy must fail closed"):
            load_v2_catalog(path)

        path = self.catalog_copy()
        payload = yaml.safe_load(path.read_text())
        payload["qaCases"][0], payload["qaCases"][1] = payload["qaCases"][1], payload["qaCases"][0]
        path.write_text(yaml.safe_dump(payload, sort_keys=False))
        with self.assertRaisesRegex(ValueError, "QA case order must match"):
            load_v2_catalog(path)


if __name__ == "__main__":
    unittest.main()
