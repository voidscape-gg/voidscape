from __future__ import annotations

import hashlib
import json
import shutil
import zipfile
from pathlib import Path
from typing import Any, Mapping

from PIL import Image
import jsonschema

from .client_preview import state_contact_sheet
from .paths import TOOL_DIR
from .v2_archive import PACK_NAME, decode_v2_sprite, validate_v2_pack, write_v2_pack
from .v2_compiler import (
    V2CompileResult, alpha_statistics, compile_v2_workspace, grayscale_statistics,
)
from .v2_legacy_export import write_v2_legacy_export
from .v2_preview import (
    comparison_panel, raw_rgb_sha256, render_v2_stack, tint_diagnostic_sets,
)
from .v2_selector_registry import (
    PROPERTIES_NAME, selector_registry_properties_bytes,
    validate_selector_registry_properties,
)
from .v2_workspace import qa_cases_for_manifest
from .workbench_report import expected_states


REPORT_SCHEMA = "voidscape-paperdoll-v2-build/v1"
SELECTOR_REGISTRY_SCHEMA_PATH = TOOL_DIR / "schema/paperdoll-v2-selector-registry.schema.json"


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def _png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _artifacts(root: Path, build: Path) -> list[dict[str, Any]]:
    return [
        {"path": str(path.relative_to(root)), "sha256": _sha256(path), "bytes": path.stat().st_size}
        for path in sorted(item for item in build.rglob("*") if item.is_file() and item.name != "report.json")
    ]


def _frame_sidecar(frame, workspace_root: Path) -> dict[str, Any]:
    try:
        source_path = frame.source_path.relative_to(workspace_root).as_posix()
    except ValueError as exc:
        raise ValueError("compiled frame source escaped the Paperdoll V2 workspace") from exc
    return {
        "requiresShift": frame.sidecar.requires_shift,
        "xShift": frame.sidecar.x_shift,
        "yShift": frame.sidecar.y_shift,
        "logicalWidth": frame.sidecar.logical_width,
        "logicalHeight": frame.sidecar.logical_height,
        "empty": frame.empty,
        # Build evidence must be reproducible across independently materialized
        # tmp roots. The source is already confined to the workspace, so record
        # its canonical manifest-relative identity instead of the host path.
        "sourcePath": source_path,
        "sourceSha256": frame.source_sha256,
    }


def _write_compiled_frames(result: V2CompileResult, build: Path) -> None:
    for asset in result.assets:
        for channel in asset.channels:
            for frame in channel.frames:
                path = build / "compiled" / asset.id / channel.id / f"frame_{frame.offset:02d}.png"
                _png(path, frame.cropped)
                _json(path.with_suffix(".json"), _frame_sidecar(frame, result.root))


def _connected_components(points: set[tuple[int, int]]) -> list[set[tuple[int, int]]]:
    remaining = set(points)
    components = []
    while remaining:
        seed = remaining.pop()
        component = {seed}
        pending = [seed]
        while pending:
            x, y = pending.pop()
            # Four-way topology deliberately rejects a one-pixel spike tip that
            # only touches the silhouette diagonally; those read as floating
            # islands at native size even though an 8-way flood fill joins them.
            for neighbour in ((x, y - 1), (x - 1, y), (x + 1, y), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.add(neighbour)
                    pending.append(neighbour)
        components.append(component)
    return sorted(components, key=len, reverse=True)


def _mask_validation(result: V2CompileResult) -> dict[str, Any]:
    poses = []
    cases = []
    valid = True
    for case in qa_cases_for_manifest(result.manifest):
        case_valid = True
        for rule in case["maskRules"]:
            asset = result.asset(rule["asset"])
            for master in ("north", "north-west", "west", "south-west", "south", "combat-west"):
                frame = next(item for item in result.template.frames if item.master == master)
                profile = result.template.pose_profiles[master]
                occupied = set()
                for channel in asset.channels:
                    alpha = channel.frames[frame.offset].canvas.getchannel("A")
                    occupied.update((x, y) for y in range(alpha.height) for x in range(alpha.width)
                                    if alpha.getpixel((x, y)) > 0)
                allowed = profile.masks[rule["allowedMask"]]
                outside = len(occupied) if allowed is None else sum(
                    not allowed.getpixel(point) for point in occupied
                )
                attachment = profile.masks[rule["requiredAttachment"]]
                attached = bool(occupied) and attachment is not None and any(
                    attachment.getpixel(point) for point in occupied
                )
                components = _connected_components(occupied)
                detached_pixels = sum(len(component) for component in components[1:])
                pose_valid = (
                    outside == 0 and attached and len(components) <= rule["maxComponents"]
                )
                case_valid &= pose_valid
                poses.append({
                    "qaCase": case["id"], "asset": asset.id, "pose": master,
                    "allowedMask": rule["allowedMask"],
                    "requiredAttachment": rule["requiredAttachment"],
                    "connectivity": rule["connectivity"], "occupiedPixels": len(occupied),
                    "outsideAllowedPixels": outside, "attachmentContact": attached,
                    "connectedComponents": len(components), "detachedComponentPixels": detached_pixels,
                    "maxComponents": rule["maxComponents"], "valid": pose_valid,
                })
        valid &= case_valid
        cases.append({"id": case["id"], "ruleCount": len(case["maskRules"]), "valid": case_valid})
    return {"valid": valid, "cases": cases, "poses": poses}


def _art_completeness(result: V2CompileResult) -> dict[str, Any]:
    poses = []
    cases = []
    valid = True
    for case in qa_cases_for_manifest(result.manifest):
        case_valid = True
        for asset_id in case["requiredAssets"]:
            asset = result.asset(asset_id)
            for master in ("north", "north-west", "west", "south-west", "south", "combat-west"):
                offset = next(item.offset for item in result.template.frames if item.master == master)
                occupied = set()
                for channel in asset.channels:
                    alpha = channel.frames[offset].canvas.getchannel("A")
                    occupied.update((x, y) for y in range(alpha.height) for x in range(alpha.width)
                                    if alpha.getpixel((x, y)) > 0)
                pose_valid = len(occupied) > 0
                case_valid &= pose_valid
                poses.append({
                    "qaCase": case["id"], "asset": asset_id, "pose": master,
                    "nonzeroAlphaPixels": len(occupied), "valid": pose_valid,
                })
        valid &= case_valid
        cases.append({"id": case["id"], "requiredAssetCount": len(case["requiredAssets"]),
                      "valid": case_valid})
    return {"valid": valid, "cases": cases, "poses": poses}


def _asset_reports(result: V2CompileResult, pack_path: Path) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    reports = []
    total_soft = total_nonzero = 0
    round_trip = True
    with zipfile.ZipFile(pack_path) as archive:
        for asset in result.assets:
            channels = []
            for channel in asset.channels:
                frame_reports = []
                for frame in channel.frames:
                    alpha = alpha_statistics(frame.canvas)
                    grayscale = grayscale_statistics(frame.canvas)
                    entry = f"sprites/{asset.id}/{channel.id}/frame_{frame.offset:02d}"
                    decoded = decode_v2_sprite(archive.read(entry))
                    exact = (
                        decoded.image.tobytes() == frame.cropped.tobytes()
                        and decoded.sidecar == frame.sidecar
                    )
                    round_trip &= exact
                    total_soft += alpha["softAlphaPixels"]
                    total_nonzero += alpha["nonzeroPixels"]
                    frame_reports.append({
                        "offset": frame.offset, "empty": frame.empty,
                        "sourceSha256": frame.source_sha256,
                        "croppedRgbaSha256": hashlib.sha256(frame.cropped.tobytes()).hexdigest(),
                        "alpha": alpha, "grayscale": grayscale, "packRoundTripExact": exact,
                    })
                channels.append({"id": channel.id, "tintRole": channel.tint_role, "frames": frame_reports})
            reports.append({
                "id": asset.id, "kind": asset.kind, "paperdollSlot": asset.paperdoll_slot,
                "sourceMode": asset.source_mode, "propagation": asset.propagation, "channels": channels,
            })
    return reports, {
        "packRoundTripExact": round_trip,
        "softAlphaPixels": total_soft,
        "nonzeroPixels": total_nonzero,
        "arbitraryGrayscalePreserved": True,
    }


def _write_previews(result: V2CompileResult, build: Path) -> tuple[list[dict[str, Any]], dict[str, list[Image.Image]]]:
    states = expected_states()
    stacks = []
    images_by_stack: dict[str, list[Image.Image]] = {}
    stack_by_id = {stack["id"]: stack for stack in result.manifest["renderStacks"]}
    ordered_stack_ids = []
    for case in qa_cases_for_manifest(result.manifest):
        for stack_id in (case["comparisonStack"], case["stack"]):
            if stack_id is not None and stack_id not in ordered_stack_ids:
                ordered_stack_ids.append(stack_id)
    for stack_id in ordered_stack_ids:
        stack = stack_by_id[stack_id]
        captures = []
        images = []
        for index, state in enumerate(states):
            image = render_v2_stack(result, stack["id"], state)
            filename = f"{index:02d}-{state['kind']}-{state['direction']}-{state['frame']}.png"
            path = build / "previews" / stack["id"] / filename
            _png(path, image)
            captures.append({
                **state,
                "path": str(path.relative_to(result.root)),
                "pngSha256": _sha256(path),
                "rawRgbSha256": raw_rgb_sha256(image),
            })
            images.append(image)
        sheet_path = build / "previews" / f"{stack['id']}-contact-sheet.png"
        _png(sheet_path, state_contact_sheet(images, states, scale=1))
        stacks.append({
            "id": stack["id"], "mode": stack["mode"], "frameCount": len(captures),
            "contactSheet": {"path": str(sheet_path.relative_to(result.root)), "sha256": _sha256(sheet_path)},
            "captures": captures,
        })
        images_by_stack[stack["id"]] = images
    return stacks, images_by_stack


def _composite_region_validation(
    result: V2CompileResult,
    images: Mapping[str, list[Image.Image]],
) -> dict[str, Any]:
    """Reject live native/style previews missing any locked player-figure vertical region."""
    preview = result.manifest["preview"]
    draw_top = int(preview["drawY"])
    regions = {
        "head": [draw_top, draw_top + 56],
        "body": [draw_top + 56, draw_top + 120],
        "legs": [draw_top + 120, draw_top + int(preview["drawHeight"])],
    }
    background_value = int(preview["backgroundRgb"])
    background = (
        background_value >> 16 & 255,
        background_value >> 8 & 255,
        background_value & 255,
    )
    stack_modes = {stack["id"]: stack["mode"] for stack in result.manifest["renderStacks"]}
    states = expected_states()
    cases = []
    valid = True
    for case in qa_cases_for_manifest(result.manifest):
        if stack_modes[case["stack"]] != "live-controls":
            continue
        stack_images = images.get(case["stack"], [])
        if len(stack_images) != len(states):
            raise ValueError(
                f"Paperdoll V2 composite validation lacks 30 previews for {case['stack']}"
            )
        captures = []
        case_valid = True
        for state, image in zip(states, stack_images):
            rgb = image.convert("RGB")
            counts = {
                name: sum(pixel != background for pixel in
                          rgb.crop((0, start, rgb.width, end)).getdata())
                for name, (start, end) in regions.items()
            }
            capture_valid = all(count > 0 for count in counts.values())
            case_valid &= capture_valid
            captures.append({**state, "nonBackgroundPixels": counts, "valid": capture_valid})
        valid &= case_valid
        cases.append({
            "id": case["id"], "stack": case["stack"], "stateCount": len(captures),
            "valid": case_valid, "captures": captures,
        })
    return {"valid": valid, "backgroundRgb": background_value, "regions": regions, "cases": cases}


def _write_comparisons(result: V2CompileResult, build: Path, images: Mapping[str, list[Image.Image]]) -> dict[str, Any]:
    comparisons = []
    case_reports = []
    states = expected_states()
    for case in qa_cases_for_manifest(result.manifest):
        control_id = case["comparisonStack"]
        if control_id is None:
            continue
        captures = []
        for index, (state, control, native) in enumerate(
                zip(states, images[control_id], images[case["stack"]])):
            panel = comparison_panel(control, native)
            path = build / "comparisons" / case["id"] / (
                f"{index:02d}-{state['direction']}-{state['frame']}.png"
            )
            _png(path, panel)
            capture = {
                "qaCase": case["id"], "controlStack": control_id,
                "candidateStack": case["stack"], **state,
                "path": str(path.relative_to(result.root)), "sha256": _sha256(path),
            }
            captures.append(capture)
            comparisons.append(capture)
        case_reports.append({
            "id": case["id"], "controlStack": control_id, "candidateStack": case["stack"],
            "frameCount": len(captures), "captures": captures,
        })
    return {"frameCount": len(comparisons), "caseCount": len(case_reports),
            "cases": case_reports, "captures": comparisons}


def _write_tints(result: V2CompileResult, build: Path) -> list[dict[str, Any]]:
    state = expected_states()[0]
    output = []
    for case in qa_cases_for_manifest(result.manifest):
        if not case["tintDiagnostics"]:
            continue
        for name, tints in tint_diagnostic_sets().items():
            image = render_v2_stack(result, case["stack"], state, tints=tints)
            path = build / "diagnostics" / case["id"] / f"tint-{name}.png"
            _png(path, image)
            output.append({
                "qaCase": case["id"], "stack": case["stack"], "name": name,
                "tints": tints, "path": str(path.relative_to(result.root)),
                "sha256": _sha256(path), "rawRgbSha256": raw_rgb_sha256(image),
            })
    return output


def _write_selector_registry(
    result: V2CompileResult,
    build: Path,
    pack_path: Path,
) -> dict[str, Any] | None:
    registry = result.manifest.get("selectorRegistry")
    if registry is None:
        return None
    payload = {
        **registry,
        "catalog": dict(result.manifest["catalog"]),
        "template": dict(result.manifest["template"]),
        "pack": {
            "path": str(pack_path.relative_to(result.root)),
            "sha256": _sha256(pack_path),
        },
        "baseProfiles": result.manifest["baseProfiles"],
        "hatOcclusionPolicy": result.manifest["hatOcclusionPolicy"],
        "renderStacks": result.manifest["renderStacks"],
    }
    schema = json.loads(SELECTOR_REGISTRY_SCHEMA_PATH.read_text())
    jsonschema.Draft202012Validator.check_schema(schema)
    try:
        jsonschema.validate(payload, schema)
    except jsonschema.ValidationError as exc:
        location = ".".join(str(item) for item in exc.absolute_path) or "<root>"
        raise ValueError(
            f"Paperdoll V2 selector registry schema error at {location}: {exc.message}"
        ) from exc
    path = build / "selector-registry.json"
    _json(path, payload)
    properties_path = build / PROPERTIES_NAME
    properties_data = selector_registry_properties_bytes(payload)
    properties_path.write_bytes(properties_data)
    validate_selector_registry_properties(properties_path.read_bytes(), payload)
    return {"path": str(path.relative_to(result.root)), "sha256": _sha256(path),
            "propertiesPath": str(properties_path.relative_to(result.root)),
            "propertiesSha256": _sha256(properties_path),
            "entryCount": len(registry["entries"]), "defaultEnabled": False}


def _oracle_parity_contract(result: V2CompileResult) -> dict[str, Any]:
    labels = {
        "pack-only-full": "complete pack-only raster",
        "slot0-slot5-only": "V2 native slots 0/5 only",
        "native-slots-0-1-2": "V2 native base slots 0/1/2",
        "native-slots-0-1-2-5": "V2 native base/equipment slots 0/1/2/5",
    }
    output: dict[str, Any] = {}
    for case in qa_cases_for_manifest(result.manifest):
        isolated = case["oracleScope"] != "pack-only-full"
        output[case["id"]] = {
            "stack": case["stack"], "scope": labels[case["oracleScope"]],
            "oracleScope": case["oracleScope"], "expectedPixelExact": True,
            **({"fullLivePanelCompared": False} if isolated else {}),
        }
    output["reason"] = (
        "Each manifest-declared QA case states its authoritative pack/full or native-slot oracle scope; "
        "legacy live controls outside that scope are excluded."
    )
    return output


def build_v2_workspace(root: Path) -> dict[str, Any]:
    result = compile_v2_workspace(root)
    build = result.root / "build"
    if build.exists():
        shutil.rmtree(build)
    build.mkdir(parents=True)
    _write_compiled_frames(result, build)
    pack = write_v2_pack(result, build / PACK_NAME)
    pack_validation = validate_v2_pack(pack.path)
    asset_reports, alpha_report = _asset_reports(result, pack.path)
    mask_report = _mask_validation(result)
    completeness_report = _art_completeness(result)
    stack_reports, images = _write_previews(result, build)
    composite_report = _composite_region_validation(result, images)
    comparison = _write_comparisons(result, build, images)
    tint_reports = _write_tints(result, build)
    selector_registry = _write_selector_registry(result, build, pack.path)
    legacy_compatibility = write_v2_legacy_export(result, build, pack.path)
    qa_cases = qa_cases_for_manifest(result.manifest)
    native_assets = [asset.id for asset in result.assets if asset.source_mode == "native"]
    legacy_assets = [asset.id for asset in result.assets if asset.source_mode == "legacy-upscaled"]
    valid = bool(pack_validation["valid"] and alpha_report["packRoundTripExact"]
                 and mask_report["valid"] and completeness_report["valid"]
                 and composite_report["valid"]
                 and (legacy_compatibility is None or legacy_compatibility["valid"]))
    report = {
        "schema": REPORT_SCHEMA,
        "valid": valid,
        "shipping": False,
        "humanVisualApproval": False,
        "workspace": result.manifest["name"],
        "template": {
            "key": result.template.key,
            "sha256": result.template.digest,
            "sourceV1Sha256": result.template.source_digest,
            "derivedMaskTreeSha256": result.template.derived_mask_tree_sha256,
        },
        "pack": {
            "path": str(pack.path.relative_to(result.root)), "sha256": pack.sha256,
            **pack_validation,
        },
        "frameCount": 18,
        "stateCount": 30,
        "assets": asset_reports,
        "stacks": stack_reports,
        "qaCases": qa_cases,
        "nativeVsFallback": {
            "nativeAssets": native_assets, "legacyUpscaledControlAssets": legacy_assets,
            "explicitlyLabeled": True,
        },
        "tintDiagnostics": tint_reports,
        "alphaPreservation": alpha_report,
        "maskValidation": mask_report,
        "artCompleteness": completeness_report,
        "compositeRegionValidation": composite_report,
        "oneXComparison": comparison,
        "oracleParity": _oracle_parity_contract(result),
        "artifacts": [],
    }
    if "catalog" in result.manifest:
        report["catalog"] = dict(result.manifest["catalog"])
        report["selectorRegistry"] = selector_registry
        report["legacyCompatibility"] = legacy_compatibility
    report["artifacts"] = _artifacts(result.root, build)
    _json(build / "report.json", report)
    if not valid:
        raise ValueError(
            "Paperdoll V2 build failed strict archive/alpha/mask/art-completeness/composite validation"
        )
    return report


__all__ = ["REPORT_SCHEMA", "build_v2_workspace"]
