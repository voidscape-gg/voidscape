from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image

from .client_preview import ANIM_DIR_LAYER_TO_CHAR_LAYER, pixel_mismatch_count
from .v2_archive import PACK_NAME, validate_v2_pack
from .v2_compiler import compile_v2_workspace
from .v2_preview import raw_rgb_sha256, render_v2_stack
from .v2_workspace import DEFAULT_TINTS, qa_cases_for_manifest, tmp_workspace_root
from .workbench_report import expected_states


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compare_v2_java_oracle(
    workspace: Path,
    java_report: Path,
    output: Path,
    *,
    fail_on_mismatch: bool = True,
) -> dict[str, Any]:
    """Compare each QA case using its manifest-declared pack or native-slot scope."""
    result = compile_v2_workspace(workspace)
    report = json.loads(java_report.read_text())
    captures = report.get("captures")
    if report.get("ok") is not True or report.get("scenario") != "paperdoll-v2-frames":
        raise ValueError("unsupported Paperdoll V2 Java oracle report")
    pack_path = result.root / "build" / PACK_NAME
    if not pack_path.is_file():
        raise ValueError(f"Paperdoll V2 workspace has no built pack: {pack_path}")
    pack_validation = validate_v2_pack(pack_path)
    if (Path(str(report.get("archivePath", ""))).resolve() != pack_path.resolve()
            or report.get("archiveSha256") != pack_validation["archiveSha256"]):
        raise ValueError("Java oracle is not bound to this workspace Paperdoll V2 pack")
    reported_workspace = tmp_workspace_root(Path(str(report.get("workspacePath", ""))))
    if reported_workspace not in {result.root, pack_path.parent.resolve()}:
        raise ValueError("Java oracle workspace path is neither canonical workspace nor archive-mode build root")
    pack_report = report.get("pack", {})
    if (pack_report.get("templateSha256") != result.template.digest
            or pack_report.get("derivedMasksSha256") != result.template.derived_mask_tree_sha256
            or pack_report.get("sourceV1Sha256") != result.template.source_digest):
        raise ValueError("Java oracle template provenance differs from the compiled workspace")
    qa_cases = qa_cases_for_manifest(result.manifest)
    expected_capture_count = len(qa_cases) * 30
    if not isinstance(captures, list) or len(captures) != expected_capture_count:
        raise ValueError(
            f"Paperdoll V2 Java oracle must contain {len(qa_cases)} QA cases x 30 states"
        )
    output_root = tmp_workspace_root(output)
    frames_dir = output_root / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    states = expected_states()
    stack_contract = {item["id"]: item for item in result.manifest["renderStacks"]}
    scope_slots = {
        "pack-only-full": None,
        "slot0-slot5-only": {0, 5},
        "native-slots-0-1-2": {0, 1, 2},
        "native-slots-0-1-2-5": {0, 1, 2, 5},
    }
    evidence = []
    mismatch_frames = mismatch_pixels = 0
    for index, capture in enumerate(captures):
        case = qa_cases[index // 30]
        stack_id = capture.get("stackId")
        if stack_id != case["stack"] or stack_id not in stack_contract:
            raise ValueError(f"Java oracle capture {index} stackId differs from its QA case")
        if capture.get("qaCaseId", case["id"]) != case["id"]:
            raise ValueError(f"Java oracle capture {index} qaCaseId differs")
        state = states[index % 30]
        if any(capture.get(key) != value for key, value in state.items()):
            raise ValueError(f"Java oracle capture {index} state metadata differs from canonical order")
        inputs = capture.get("renderInputs", {})
        tint_rgb = inputs.get("tintRgb")
        if not isinstance(tint_rgb, dict) or set(tint_rgb) != set(DEFAULT_TINTS):
            raise ValueError(f"Java oracle capture {index} has incomplete tintRgb")
        if any(isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 0xFFFFFF
               for value in tint_rgb.values()):
            raise ValueError(f"Java oracle capture {index} has invalid tintRgb")
        isolated = capture.get("v2Only")
        if not isinstance(isolated, dict):
            raise ValueError(f"Java oracle capture {index} lacks authoritative v2Only raster")
        path = Path(str(isolated.get("pngPath", "")))
        if not path.is_file() or _sha256(path) != isolated.get("pngSha256"):
            raise ValueError(f"Java oracle capture {index} v2Only PNG is missing or digest-mismatched")
        oracle = Image.open(path).convert("RGB")
        if oracle.size != (176, 224) or (isolated.get("width"), isolated.get("height")) != oracle.size:
            raise ValueError(f"Java oracle capture {index} v2Only geometry differs")
        if raw_rgb_sha256(oracle) != isolated.get("rawRgbSha256"):
            raise ValueError(f"Java oracle capture {index} raw RGB digest differs")
        stack = stack_contract[stack_id]
        included_slots = scope_slots[case["oracleScope"]]
        stack_assets = [result.asset(asset_id) for asset_id in stack["assets"]]
        expected_slots = [
            slot for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[state["wantedAnimDir"]]
            if any(asset.paperdoll_slot == slot for asset in stack_assets)
            and (included_slots is None or slot in included_slots)
        ]
        if isolated.get("paperdollSlots") != expected_slots:
            raise ValueError(f"Java oracle capture {index} paperdollSlots differ from direction order")
        offline = render_v2_stack(
            result, stack_id, state, tints=tint_rgb, paperdoll_slots=included_slots,
        )
        mismatches = pixel_mismatch_count(offline, oracle)
        mismatch_frames += int(mismatches > 0)
        mismatch_pixels += mismatches
        target = frames_dir / f"{index:02d}-{stack_id}-{state['direction']}-{state['frame']}.png"
        offline.save(target, format="PNG", optimize=False, compress_level=9)
        evidence.append({
            "qaCaseId": case["id"], "stackId": stack_id, **state,
            "scope": case["oracleScope"],
            "fullLivePanelCompared": False if included_slots is not None else None,
            "path": str(target.relative_to(output_root)), "pngSha256": _sha256(target),
            "rawRgbSha256": raw_rgb_sha256(offline), "oracleRawRgbSha256": isolated["rawRgbSha256"],
            "mismatchedPixels": mismatches,
        })
    comparison = {
        "schema": "voidscape-paperdoll-v2-oracle-comparison/v1",
        "valid": mismatch_frames == 0,
        "javaReport": str(java_report.resolve()),
        "javaReportSha256": _sha256(java_report),
        "workspace": str(result.root),
        "templateSha256": result.template.digest,
        "mismatchedFrames": mismatch_frames,
        "mismatchedPixels": mismatch_pixels,
        "parityScope": {
            case["id"]: {
                "pack-only-full": "complete pack-only raster",
                "slot0-slot5-only": "V2 native slots 0/5; Java live legacy controls excluded",
                "native-slots-0-1-2": "V2 native slots 0/1/2; non-native live controls excluded",
                "native-slots-0-1-2-5": "V2 native slots 0/1/2/5; other live controls excluded",
            }[case["oracleScope"]]
            for case in qa_cases
        },
        "captures": evidence,
    }
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "report.json").write_text(json.dumps(comparison, indent=2, sort_keys=True) + "\n")
    if fail_on_mismatch and not comparison["valid"]:
        raise ValueError(
            f"Paperdoll V2 Python/Java parity differs in {mismatch_frames} frames / {mismatch_pixels} pixels"
        )
    return comparison


__all__ = ["compare_v2_java_oracle"]
