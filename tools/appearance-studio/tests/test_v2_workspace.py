from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image
import jsonschema

from appearance_studio.paths import REPO_ROOT, TOOL_DIR
from appearance_studio.v2_template import MASTERS, load_v2_template
from appearance_studio.v2_workspace import (
    REQUIRED_PROOF_ASSET_IDS, init_v2_workspace, load_v2_workspace, semantic_skin_shade,
)


class PaperdollV2WorkspaceTest(unittest.TestCase):
    def workspace(self) -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name) / "proof"

    def test_init_materializes_canonical_poses_guides_controls_and_native_masters(self):
        root = self.workspace()
        manifest = init_v2_workspace("proof", root)
        schema = json.loads((TOOL_DIR / "schema/paperdoll-v2-workspace.schema.json").read_text())
        jsonschema.validate(manifest, schema)
        self.assertFalse(manifest["shipping"])
        self.assertEqual(list(MASTERS), [pose["id"] for pose in manifest["poses"]])
        self.assertEqual(list(REQUIRED_PROOF_ASSET_IDS), [asset["id"] for asset in manifest["assets"]])
        self.assertEqual(["control", "rare_hair"], [stack["id"] for stack in manifest["renderStacks"]])
        self.assertEqual(0x3D3D48, manifest["palette"]["defaultRgb"]["hair"])
        self.assertEqual([0x76, 0xB0, 0xFF],
                         [semantic_skin_shade(value) for value in (99, 100, 180)])
        with Image.open(root / "controls/legacy_head/skin/frame_00.png") as control_skin:
            colours = {(red, green, blue) for red, green, blue, alpha in control_skin.getdata() if alpha}
        self.assertTrue(colours)
        self.assertTrue(all(red == green == blue and red in {0x76, 0xB0, 0xFF}
                            for red, green, blue in colours))
        template = load_v2_template()
        for pose in manifest["poses"]:
            master = pose["id"]
            frame = next(item for item in template.frames if item.master == master)
            with Image.open(root / pose["guides"]["base"]) as guide:
                self.assertEqual("RGBA", guide.mode)
                self.assertEqual(frame.size, guide.size)
            for role, relative in pose["guides"]["masks"].items():
                if relative is None:
                    continue
                with Image.open(root / relative) as mask:
                    self.assertEqual("1", mask.mode)
                    self.assertEqual(frame.size, mask.size)
            for anchor, value in pose["anchors"].items():
                point = template.pose_profiles[master].landmarks[anchor]
                if point is None:
                    self.assertIsNone(value)
                else:
                    self.assertEqual(list(point), value["relative"])
                    self.assertEqual([frame.crown[0] + point[0], frame.crown[1] + point[1]], value["absolute"])
        _, loaded, loaded_template = load_v2_workspace(root)
        self.assertEqual(manifest, loaded)
        self.assertEqual(template.digest, loaded_template.digest)

    def test_guide_mask_base_and_source_png_tampering_fail_before_compilation(self):
        root = self.workspace()
        init_v2_workspace("proof", root)
        mask_path = root / "guides/north/masks/hair_allowed.png"
        with Image.open(mask_path) as source:
            mask = source.copy()
        mask.putpixel((0, 0), 0 if mask.getpixel((0, 0)) else 255)
        mask.save(mask_path, format="PNG")
        with self.assertRaisesRegex(ValueError, "mask guide north.hair_allowed digest changed"):
            load_v2_workspace(root)

        init_v2_workspace("proof", root, force=True)
        base_path = root / "guides/west/base.png"
        with Image.open(base_path) as source:
            base = source.copy()
        base.putpixel((0, 0), (255, 0, 255, 255))
        base.save(base_path, format="PNG")
        with self.assertRaisesRegex(ValueError, "base guide west differs"):
            load_v2_workspace(root)

        init_v2_workspace("proof", root, force=True)
        master = root / "masters/hair_rare_spikes/hair/north.png"
        Image.new("RGBA", (127, 204)).save(master, format="PNG")
        with self.assertRaisesRegex(ValueError, "RGBA PNG of size 128x204"):
            load_v2_workspace(root)

        init_v2_workspace("proof", root, force=True)
        master = root / "masters/native_head/skin/north.png"
        with Image.open(master) as source:
            non_neutral = source.copy()
        non_neutral.putpixel((0, 0), (255, 128, 96, 255))
        non_neutral.save(master, format="PNG")
        with self.assertRaisesRegex(ValueError, "neutral grayscale pixels"):
            load_v2_workspace(root)

    def test_missing_sources_existing_workspace_and_non_tmp_roots_fail_closed(self):
        root = self.workspace()
        init_v2_workspace("proof", root)
        with self.assertRaises(FileExistsError):
            init_v2_workspace("proof", root)
        (root / "masters/hair_rare_spikes/hair/west.png").unlink()
        with self.assertRaises(FileNotFoundError):
            load_v2_workspace(root)
        with self.assertRaisesRegex(ValueError, "must remain under"):
            init_v2_workspace("bad", REPO_ROOT / "paperdoll-v2-outside-tmp")

    def test_generic_future_facial_hair_and_hat_assets_remain_supported_but_not_required(self):
        root = self.workspace()
        manifest = init_v2_workspace("proof", root)
        hair = next(asset for asset in manifest["assets"] if asset["id"] == "hair_rare_spikes")
        master_paths = dict(hair["channels"][0]["masters"])
        manifest["assets"].extend([
            {
                "id": "future_beard", "label": "Future beard", "kind": "facial-hair",
                "paperdollSlot": 0, "sourceMode": "native", "propagation": "rigid-head",
                "channels": [{"id": "facial_hair", "label": "Facial Hair", "tintRole": "facial-hair",
                              "editable": True, "masters": master_paths}],
            },
            {
                "id": "future_hat", "label": "Future hat", "kind": "hat",
                "paperdollSlot": 5, "sourceMode": "native", "propagation": "rigid-head",
                "channels": [{"id": "primary", "label": "Primary", "tintRole": "primary",
                              "editable": True, "masters": master_paths}],
            },
        ])
        manifest["renderStacks"].append({
            "id": "future_combo", "label": "Future facial hair and hat", "mode": "live-controls",
            "assets": ["legacy_legs", "legacy_body", "native_head", "future_beard", "future_hat"],
        })
        (root / "workspace.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
        _, loaded, _ = load_v2_workspace(root)
        self.assertEqual(["future_beard", "future_hat"], [asset["id"] for asset in loaded["assets"][-2:]])


if __name__ == "__main__":
    unittest.main()
