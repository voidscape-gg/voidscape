from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.client_preview import ANIM_DIR_LAYER_TO_CHAR_LAYER
from appearance_studio.paths import REPO_ROOT
from appearance_studio.v2_compiler import compile_v2_workspace
from appearance_studio.v2_archive import write_v2_pack
from appearance_studio.v2_oracle import compare_v2_java_oracle
from appearance_studio.v2_preview import raw_rgb_sha256, render_v2_stack
from appearance_studio.v2_workspace import DEFAULT_TINTS, init_v2_workspace
from appearance_studio.workbench_report import expected_states


class PaperdollV2OracleTest(unittest.TestCase):
    def root(self) -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name)

    def test_comparator_uses_pack_only_control_and_v2_only_live_stacks(self):
        parent = self.root()
        workspace = parent / "workspace"
        init_v2_workspace("oracle", workspace)
        result = compile_v2_workspace(workspace)
        pack = write_v2_pack(result, workspace / "build/Paperdoll_V2.orsc")
        captures = []
        for stack in result.manifest["renderStacks"]:
            stack_assets = [result.asset(asset_id) for asset_id in stack["assets"]]
            v2_only = stack["mode"] == "live-controls"
            for index, state in enumerate(expected_states()):
                image = render_v2_stack(result, stack["id"], state, v2_only=v2_only)
                path = parent / "java" / "v2-only" / stack["id"] / f"{index:02d}.png"
                path.parent.mkdir(parents=True, exist_ok=True)
                image.save(path, format="PNG", optimize=False, compress_level=9)
                slots = [
                    slot for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]]
                    if any(asset.paperdoll_slot == slot for asset in stack_assets)
                    and (not v2_only or slot in {0, 5})
                ]
                captures.append({
                    "stackId": stack["id"], **state,
                    "renderInputs": {"tintRgb": DEFAULT_TINTS},
                    "v2Only": {
                        "pngPath": str(path), "pngSha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                        "rawRgbSha256": raw_rgb_sha256(image), "width": 176, "height": 224,
                        "crop": {"x": 0, "y": 0, "width": 176, "height": 224},
                        "paperdollSlots": slots,
                    },
                })
        java_report = parent / "java/report.json"
        java_report.write_text(json.dumps({
            "ok": True, "scenario": "paperdoll-v2-frames", "captures": captures,
            "archivePath": str(pack.path), "archiveSha256": pack.sha256,
            "workspacePath": str(workspace.resolve()),
            "pack": {"templateSha256": result.template.digest,
                     "derivedMasksSha256": result.template.derived_mask_tree_sha256,
                     "sourceV1Sha256": result.template.source_digest},
        }, indent=2, sort_keys=True) + "\n")
        comparison = compare_v2_java_oracle(workspace, java_report, parent / "comparison")
        self.assertTrue(comparison["valid"])
        self.assertEqual(0, comparison["mismatchedFrames"])
        self.assertEqual("V2 native slots 0/5; Java live legacy controls excluded",
                         comparison["parityScope"]["rare_hair"])
        self.assertTrue(all(
            item["fullLivePanelCompared"] is False
            for item in comparison["captures"] if item["stackId"] == "rare_hair"
        ))

        archive_mode_report = json.loads(java_report.read_text())
        archive_mode_report["workspacePath"] = str(pack.path.parent)
        java_report.write_text(json.dumps(archive_mode_report, indent=2, sort_keys=True) + "\n")
        self.assertTrue(compare_v2_java_oracle(
            workspace, java_report, parent / "comparison-archive-mode",
        )["valid"])

        target = Path(captures[30]["v2Only"]["pngPath"])
        with Image.open(target) as source:
            tampered = source.copy()
        tampered.putpixel((0, 0), (255, 0, 255))
        tampered.save(target, format="PNG")
        captures[30]["v2Only"]["pngSha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
        captures[30]["v2Only"]["rawRgbSha256"] = raw_rgb_sha256(tampered)
        java_report.write_text(json.dumps({
            "ok": True, "scenario": "paperdoll-v2-frames", "captures": captures,
            "archivePath": str(pack.path), "archiveSha256": pack.sha256,
            "workspacePath": str(workspace.resolve()),
            "pack": {"templateSha256": result.template.digest,
                     "derivedMasksSha256": result.template.derived_mask_tree_sha256,
                     "sourceV1Sha256": result.template.source_digest},
        }, indent=2, sort_keys=True) + "\n")
        with self.assertRaisesRegex(ValueError, "parity differs"):
            compare_v2_java_oracle(workspace, java_report, parent / "comparison-bad")


if __name__ == "__main__":
    unittest.main()
