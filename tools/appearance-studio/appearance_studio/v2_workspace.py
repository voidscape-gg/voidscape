from __future__ import annotations

import hashlib
import json
import re
import shutil
import struct
from pathlib import Path
from typing import Any, Mapping

from PIL import Image
import jsonschema

from .client_preview import load_reference_frames
from .geometry import expand_crop
from .paths import REPO_ROOT, TOOL_DIR
from .registry import safe_repo_path
from .v2_catalog import (
    DEFAULT_CATALOG, PaperdollV2Catalog, catalog_source_frames,
    generated_collection_contract, load_v2_catalog, style_master_paths,
)
from .v2_template import DEFAULT_TEMPLATE, MASTERS, TINT_ROLES, PaperdollV2Template, load_v2_template


SCHEMA = "voidscape-paperdoll-v2-workspace/v1"
SCHEMA_PATH = TOOL_DIR / "schema/paperdoll-v2-workspace.schema.json"
BACKGROUND_RGB = 0x121820
DEFAULT_TINTS = {
    "skin": 0xECDED0,
    "hair": 0x3D3D48,
    "facial-hair": 0x805030,
    "top": 0x345C94,
    "bottom": 0x5C442C,
    "primary": 0x294A73,
    "secondary": 0xD5C36A,
    "fixed": 0xFFFFFF,
}
PROOF_ASSETS = (
    ("legacy_head", "Legacy-upscaled head control", "legacy-control", 0, "legacy-upscaled", "explicit-frames", (("skin", "skin"), ("hair", "hair"), ("fixed", "fixed"))),
    ("legacy_body", "Legacy-upscaled body control", "legacy-control", 1, "legacy-upscaled", "explicit-frames", (("skin", "skin"), ("top", "top"), ("fixed", "fixed"))),
    ("legacy_legs", "Legacy-upscaled legs control", "legacy-control", 2, "legacy-upscaled", "explicit-frames", (("skin", "skin"), ("bottom", "bottom"), ("fixed", "fixed"))),
    ("native_head", "Native 2x bald head", "head", 0, "native", "rigid-head", (("skin", "skin"), ("fixed", "fixed"))),
    ("hair_rare_spikes", "Rare dramatic native 2x high-spike hair", "hair", 0, "native", "rigid-head", (("hair", "hair"),)),
)
REQUIRED_PROOF_ASSET_IDS = tuple(asset[0] for asset in PROOF_ASSETS)
PROOF_STACKS = (
    ("control", "Legacy-upscaled control", "pack-only", ("legacy_legs", "legacy_body", "legacy_head")),
    ("rare_hair", "Faithful native head with rare dramatic high-spike hair", "live-controls", ("legacy_legs", "legacy_body", "native_head", "hair_rare_spikes")),
)


def qa_cases_for_manifest(payload: Mapping[str, Any]) -> list[dict[str, Any]]:
    """Return declared QA cases, or a name-agnostic projection for legacy proof workspaces."""
    declared = payload.get("qaCases")
    if declared is not None:
        return [dict(case) for case in declared]
    assets = {asset["id"]: asset for asset in payload["assets"]}
    controls = [stack for stack in payload["renderStacks"] if stack["mode"] == "pack-only"]
    comparison = controls[0]["id"] if controls else None
    cases: list[dict[str, Any]] = []
    for stack in payload["renderStacks"]:
        native = [assets[asset_id] for asset_id in stack["assets"]
                  if assets[asset_id]["sourceMode"] == "native"]
        mask_rules = []
        for asset in native:
            if asset["kind"] == "hair":
                mask_rules.append({
                    "asset": asset["id"], "allowedMask": "hair_allowed",
                    "requiredAttachment": "scalp_attachment", "connectivity": 4,
                    "maxComponents": 1,
                })
            elif asset["kind"] == "facial-hair":
                mask_rules.append({
                    "asset": asset["id"], "allowedMask": "facial_hair_allowed",
                    "requiredAttachment": "upper_lip_attachment", "connectivity": 4,
                    "maxComponents": 2,
                })
        cases.append({
            "id": stack["id"],
            "baseProfile": "legacy_proof",
            "stack": stack["id"],
            "selectorId": None,
            "requiredAssets": [asset["id"] for asset in native],
            "maskRules": mask_rules,
            "comparisonStack": None if stack["mode"] == "pack-only" else comparison,
            "tintDiagnostics": any(asset["kind"] in {"hair", "facial-hair"} for asset in native),
            "oracleScope": "pack-only-full" if stack["mode"] == "pack-only" else "slot0-slot5-only",
        })
    return cases


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def _png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _relative(path: Path) -> str:
    try:
        return str(path.resolve().relative_to(REPO_ROOT.resolve()))
    except ValueError as exc:
        raise ValueError(f"path must remain inside the repository: {path}") from exc


def tmp_workspace_root(path: Path) -> Path:
    root = path.resolve()
    tmp = (REPO_ROOT / "tmp").resolve()
    try:
        relative = root.relative_to(tmp)
    except ValueError as exc:
        raise ValueError(f"Paperdoll V2 workspaces must remain under {tmp}") from exc
    if not relative.parts:
        raise ValueError("refusing to use the repository tmp root as a workspace")
    return root


def safe_workspace_path(root: Path, relative: str) -> Path:
    candidate = Path(relative)
    if candidate.is_absolute() or ".." in candidate.parts or not candidate.parts:
        raise ValueError(f"unsafe workspace-relative path: {relative!r}")
    resolved = (root / candidate).resolve()
    try:
        resolved.relative_to(root.resolve())
    except ValueError as exc:
        raise ValueError(f"workspace path escapes root: {relative!r}") from exc
    return resolved


def _rgb(value: int) -> tuple[int, int, int]:
    return value >> 16 & 255, value >> 8 & 255, value & 255


def semantic_skin_shade(legacy_shade: int) -> int:
    """Normalize an RSC ``(255, shade, shade)`` skin mask to the V2 neutral ramp."""
    if isinstance(legacy_shade, bool) or not isinstance(legacy_shade, int) or not 0 <= legacy_shade <= 255:
        raise ValueError("legacy skin shade must be an integer from 0 through 255")
    if legacy_shade < 100:
        return 0x76
    if legacy_shade < 180:
        return 0xB0
    return 0xFF


def _legacy_channels(image: Image.Image, primary_role: str) -> dict[str, Image.Image]:
    """Split a V1 mask sprite into tintable semantic RGBA channels before nearest 2x."""
    if primary_role not in {"hair", "top", "bottom"}:
        raise ValueError(f"unsupported legacy primary role {primary_role}")
    pixels: dict[str, list[tuple[int, int, int, int]]] = {
        "skin": [], primary_role: [], "fixed": [],
    }
    for red, green, blue, alpha in image.convert("RGBA").getdata():
        if alpha < 128:
            selected = None
        elif red == green == blue:
            selected = primary_role
        elif red == 255 and green == blue:
            selected = "skin"
        else:
            selected = "fixed"
        for role in pixels:
            colour = (red, green, blue, 255)
            if role == selected == "skin":
                shade = semantic_skin_shade(green)
                colour = (shade, shade, shade, 255)
            pixels[role].append(
                colour if role == selected else (0, 0, 0, 0)
            )
    output = {}
    for role, values in pixels.items():
        channel = Image.new("RGBA", image.size)
        channel.putdata(values)
        output[role] = channel
    return output


def _write_guides(root: Path, template: PaperdollV2Template) -> list[dict[str, Any]]:
    heads = load_reference_frames(template.source.reference_dirs["head4"])
    poses: list[dict[str, Any]] = []
    for master in MASTERS:
        frame = next(item for item in template.frames if item.master == master)
        source_frame = template.source.frames[frame.offset]
        guide = expand_crop(heads[frame.offset].image, dict(heads[frame.offset].sidecar)).resize(
            frame.size, Image.Resampling.NEAREST,
        )
        base_path = f"guides/{master}/base.png"
        _png(root / base_path, guide)
        profile = template.pose_profiles[master]
        mask_paths: dict[str, str | None] = {}
        for role, mask in profile.masks.items():
            if mask is None:
                mask_paths[role] = None
                continue
            path = f"guides/{master}/masks/{role}.png"
            _png(root / path, mask)
            mask_paths[role] = path
        anchors = {
            name: None if point is None else {
                "relative": list(point),
                "absolute": [frame.crown[0] + point[0], frame.crown[1] + point[1]],
            }
            for name, point in profile.landmarks.items()
        }
        poses.append({
            "id": master,
            "label": master.replace("-", " ").title(),
            "visualPose": profile.visual_pose,
            "canvas": list(frame.size),
            "frameOffsets": [item.offset for item in template.frames if item.master == master],
            "crown": list(frame.crown),
            "sourceV1Crown": list(source_frame.crown),
            "anchors": anchors,
            "guides": {"base": base_path, "masks": mask_paths},
        })
    return poses


def _write_controls(root: Path, template: PaperdollV2Template) -> dict[str, dict[str, dict[str, str]]]:
    sources = {
        "legacy_head": ("head4", "hair"),
        "legacy_body": ("body", "top"),
        "legacy_legs": ("legs", "bottom"),
    }
    result: dict[str, dict[str, dict[str, str]]] = {}
    for asset_id, (reference, primary_role) in sources.items():
        frames = load_reference_frames(template.source.reference_dirs[reference])
        paths: dict[str, dict[str, str]] = {role: {} for role in ("skin", primary_role, "fixed")}
        for offset, (source, spec) in enumerate(zip(frames, template.frames)):
            expanded = expand_crop(source.image, dict(source.sidecar))
            for role, control in _legacy_channels(expanded, primary_role).items():
                control = control.resize(spec.size, Image.Resampling.NEAREST)
                relative = f"controls/{asset_id}/{role}/frame_{offset:02d}.png"
                _png(root / relative, control)
                paths[role][f"{offset:02d}"] = relative
        result[asset_id] = paths
    return result


def init_v2_workspace(
    name: str,
    out: Path,
    *,
    template_path: Path = DEFAULT_TEMPLATE,
    force: bool = False,
) -> dict[str, Any]:
    if not re.fullmatch(r"[a-z0-9][a-z0-9_-]*", name):
        raise ValueError("name must use lowercase letters, numbers, hyphens, or underscores")
    if template_path.resolve() != DEFAULT_TEMPLATE.resolve():
        raise ValueError(f"Paperdoll V2 workspaces require the canonical template {DEFAULT_TEMPLATE}")
    root = tmp_workspace_root(out)
    if root.exists():
        if not force:
            raise FileExistsError(f"workspace already exists: {root}; pass --force to replace it")
        shutil.rmtree(root)
    root.mkdir(parents=True)
    template = load_v2_template(template_path)
    poses = _write_guides(root, template)
    controls = _write_controls(root, template)

    assets: list[dict[str, Any]] = []
    master_sizes = {pose["id"]: tuple(pose["canvas"]) for pose in poses}
    for asset_id, label, kind, slot, source_mode, propagation, channel_specs in PROOF_ASSETS:
        channels = []
        for channel_id, tint_role in channel_specs:
            channel: dict[str, Any] = {
                "id": channel_id,
                "label": channel_id.replace("_", " ").title(),
                "tintRole": tint_role,
                "editable": propagation == "rigid-head",
            }
            if propagation == "rigid-head":
                masters = {}
                for master in MASTERS:
                    relative = f"masters/{asset_id}/{channel_id}/{master}.png"
                    _png(root / relative, Image.new("RGBA", master_sizes[master], (0, 0, 0, 0)))
                    masters[master] = relative
                channel["masters"] = masters
            else:
                channel["frames"] = controls[asset_id][channel_id]
            channels.append(channel)
        assets.append({
            "id": asset_id,
            "label": label,
            "kind": kind,
            "paperdollSlot": slot,
            "sourceMode": source_mode,
            "propagation": propagation,
            "channels": channels,
        })

    render_stacks = [
        {"id": stack_id, "label": label, "mode": mode, "assets": list(asset_ids)}
        for stack_id, label, mode, asset_ids in PROOF_STACKS
    ]
    manifest = {
        "schema": SCHEMA,
        "shipping": False,
        "name": name,
        "template": {
            "path": _relative(template.path),
            "sha256": template.digest,
            "sourceV1Sha256": template.source_digest,
            "derivedMaskTreeSha256": template.derived_mask_tree_sha256,
        },
        "poses": poses,
        "assets": assets,
        "renderStacks": render_stacks,
        "preview": {
            "width": 176, "height": 224, "drawX": 24, "drawY": 4,
            "drawWidth": 128, "drawHeight": 204, "backgroundRgb": BACKGROUND_RGB,
        },
        "palette": {
            "defaultRgb": DEFAULT_TINTS,
            "grayscale": {"arbitrary": True, "range": [0, 255]},
            "alpha": {"preserved": True, "range": [0, 255]},
        },
        "instructions": "Paint only native master channels. Build outputs remain non-shipping under this tmp workspace.",
    }
    manifest["qaCases"] = qa_cases_for_manifest(manifest)
    validate_v2_workspace_payload(manifest, root, template, require_files=True)
    _json(root / "workspace.json", manifest)
    return manifest


def _write_catalog_explicit_frames(
    root: Path,
    template: PaperdollV2Template,
    asset_id: str,
    frames,
    primary_role: str,
    directory: str,
) -> dict[str, dict[str, str]]:
    paths: dict[str, dict[str, str]] = {
        role: {} for role in ("skin", primary_role, "fixed")
    }
    for offset, (source, spec) in enumerate(zip(frames, template.frames)):
        expanded = expand_crop(source.image, dict(source.sidecar))
        channels = _legacy_channels(expanded, primary_role)
        for role, channel in channels.items():
            relative = f"{directory}/{asset_id}/{role}/frame_{offset:02d}.png"
            _png(root / relative, channel.resize(spec.size, Image.Resampling.NEAREST))
            paths[role][f"{offset:02d}"] = relative
    return paths


def _write_catalog_native_head(
    root: Path,
    template: PaperdollV2Template,
    asset_id: str,
    frames,
) -> dict[str, dict[str, str]]:
    paths: dict[str, dict[str, str]] = {"skin": {}, "fixed": {}}
    for master in MASTERS:
        spec = next(frame for frame in template.frames if frame.master == master)
        source = frames[spec.offset]
        expanded = expand_crop(source.image, dict(source.sidecar)).resize(
            spec.size, Image.Resampling.NEAREST,
        )
        channels = _legacy_channels(expanded, "hair")
        for role in ("skin", "fixed"):
            relative = f"masters/{asset_id}/{role}/{master}.png"
            _png(root / relative, channels[role])
            paths[role][master] = relative
    return paths


def init_v2_collection_workspace(
    out: Path,
    *,
    catalog_path: Path = DEFAULT_CATALOG,
    force: bool = False,
) -> dict[str, Any]:
    """Materialize the content catalog as a deterministic, non-shipping tmp workspace."""
    root = tmp_workspace_root(out)
    if root.exists():
        if not force:
            raise FileExistsError(f"workspace already exists: {root}; pass --force to replace it")
        shutil.rmtree(root)
    catalog = load_v2_catalog(catalog_path)
    root.mkdir(parents=True)
    template = catalog.template
    poses = _write_guides(root, template)
    contract = generated_collection_contract(catalog)
    profiles = {profile["id"]: profile for profile in catalog.base_profiles}
    source_frames = {
        profile_id: {
            part: catalog_source_frames(catalog, profile["sources"][part])
            for part in ("head", "body", "legs")
        }
        for profile_id, profile in profiles.items()
    }
    styles = {style["id"]: style for style in catalog.hairstyles}

    assets: list[dict[str, Any]] = []
    for spec in contract["assets"]:
        generator = spec["generator"]
        generated_paths: dict[str, dict[str, str]]
        if generator["type"] == "profile-control":
            part = generator["part"]
            primary = {"head": "hair", "body": "top", "legs": "bottom"}[part]
            generated_paths = _write_catalog_explicit_frames(
                root, template, spec["id"], source_frames[generator["profile"]][part],
                primary, "controls",
            )
        elif generator["type"] == "profile-native-head":
            generated_paths = _write_catalog_native_head(
                root, template, spec["id"], source_frames[generator["profile"]]["head"],
            )
        elif generator["type"] == "profile-native-part":
            part = generator["part"]
            primary = {"body": "top", "legs": "bottom"}[part]
            generated_paths = _write_catalog_explicit_frames(
                root, template, spec["id"], source_frames[generator["profile"]][part],
                primary, "native",
            )
        elif generator["type"] == "catalog-hairstyle":
            style = styles[generator["style"]]
            masters = style_master_paths(catalog, style)
            generated_paths = {"hair": {}}
            for master in MASTERS:
                relative = f"masters/{spec['id']}/hair/{master}.png"
                with Image.open(masters[master]) as source:
                    _png(root / relative, source.copy())
                generated_paths["hair"][master] = relative
        else:  # generated_collection_contract owns this closed set
            raise ValueError(f"unsupported catalog asset generator {generator['type']!r}")

        channels = []
        for channel_id, tint_role in spec["channels"]:
            channel = {
                "id": channel_id,
                "label": channel_id.replace("_", " ").title(),
                "tintRole": tint_role,
                "editable": generator["type"] == "catalog-hairstyle",
            }
            key = "masters" if spec["propagation"] == "rigid-head" else "frames"
            channel[key] = generated_paths[channel_id]
            channels.append(channel)
        assets.append({
            key: value for key, value in spec.items()
            if key not in {"channels", "generator"}
        } | {"channels": channels})

    manifest = {
        "schema": SCHEMA,
        "shipping": False,
        "name": "catalog_collection",
        "catalog": {"path": _relative(catalog.path), "sha256": catalog.digest},
        "template": {
            "path": _relative(template.path),
            "sha256": template.digest,
            "sourceV1Sha256": template.source_digest,
            "derivedMaskTreeSha256": template.derived_mask_tree_sha256,
        },
        "poses": poses,
        "assets": assets,
        "renderStacks": contract["renderStacks"],
        "baseProfiles": contract["baseProfiles"],
        "selectorRegistry": contract["selectorRegistry"],
        "hatOcclusionPolicy": contract["hatOcclusionPolicy"],
        "qaCases": contract["qaCases"],
        "preview": {
            "width": 176, "height": 224, "drawX": 24, "drawY": 4,
            "drawWidth": 128, "drawHeight": 204, "backgroundRgb": BACKGROUND_RGB,
        },
        "palette": {
            "defaultRgb": DEFAULT_TINTS,
            "grayscale": {"arbitrary": True, "range": [0, 255]},
            "alpha": {"preserved": True, "range": [0, 255]},
        },
        "instructions": (
            "Catalog projection only. Edit hairstyle master channels in tmp; base controls remain locked. "
            "Build outputs are non-shipping."
        ),
    }
    validate_v2_workspace_payload(manifest, root, template, require_files=True)
    _json(root / "workspace.json", manifest)
    return manifest


def _schema_validate(payload: Mapping[str, Any]) -> None:
    schema = json.loads(SCHEMA_PATH.read_text())
    jsonschema.Draft202012Validator.check_schema(schema)
    try:
        jsonschema.validate(payload, schema)
    except jsonschema.ValidationError as exc:
        location = ".".join(str(item) for item in exc.absolute_path) or "<root>"
        raise ValueError(f"Paperdoll V2 workspace schema error at {location}: {exc.message}") from exc


def validate_v2_workspace_payload(
    payload: Mapping[str, Any], root: Path, template: PaperdollV2Template, *, require_files: bool,
) -> None:
    _schema_validate(payload)
    template_ref = payload["template"]
    if template_ref != {
        "path": _relative(template.path),
        "sha256": template.digest,
        "sourceV1Sha256": template.source_digest,
        "derivedMaskTreeSha256": template.derived_mask_tree_sha256,
    }:
        raise ValueError("workspace template binding changed")
    poses = payload["poses"]
    if [pose.get("id") for pose in poses] != list(MASTERS):
        raise ValueError("workspace poses must use the six canonical V2 masters in order")
    for pose in poses:
        master = pose["id"]
        frame = next(item for item in template.frames if item.master == master)
        profile = template.pose_profiles[master]
        expected_anchors = {
            name: None if point is None else {
                "relative": list(point),
                "absolute": [frame.crown[0] + point[0], frame.crown[1] + point[1]],
            }
            for name, point in profile.landmarks.items()
        }
        if pose.get("canvas") != list(frame.size) or pose.get("crown") != list(frame.crown):
            raise ValueError(f"workspace pose {master} geometry differs from template")
        if pose.get("frameOffsets") != [item.offset for item in template.frames if item.master == master]:
            raise ValueError(f"workspace pose {master} offsets differ from template")
        if pose.get("anchors") != expected_anchors:
            raise ValueError(f"workspace pose {master} anchors differ from template")
        guides = pose.get("guides", {})
        expected_masks = {
            role: None if mask is None else f"guides/{master}/masks/{role}.png"
            for role, mask in profile.masks.items()
        }
        if guides != {"base": f"guides/{master}/base.png", "masks": expected_masks}:
            raise ValueError(f"workspace pose {master} guide paths differ from template")
        if require_files:
            base_path = safe_workspace_path(root, guides["base"])
            if not base_path.is_file():
                raise FileNotFoundError(f"workspace guide is missing: {guides['base']}")
            heads = load_reference_frames(template.source.reference_dirs["head4"])
            expected_base = expand_crop(
                heads[frame.offset].image, dict(heads[frame.offset].sidecar),
            ).resize(frame.size, Image.Resampling.NEAREST).convert("RGBA")
            with Image.open(base_path) as image:
                if image.format != "PNG" or image.mode != "RGBA" or image.size != frame.size:
                    raise ValueError(f"workspace base guide {master} must be an exact-size RGBA PNG")
                if image.tobytes() != expected_base.tobytes():
                    raise ValueError(f"workspace base guide {master} differs from digest-locked derivation")
            for role, relative in expected_masks.items():
                if relative is None:
                    continue
                mask_path = safe_workspace_path(root, relative)
                if not mask_path.is_file():
                    raise FileNotFoundError(f"workspace guide is missing: {relative}")
                with Image.open(mask_path) as image:
                    if image.format != "PNG" or image.mode != "1" or image.size != frame.size:
                        raise ValueError(f"workspace mask guide {master}.{role} must be an exact-size 1-bit PNG")
                    digest = hashlib.sha256()
                    digest.update(struct.pack(">II", *image.size))
                    digest.update(image.tobytes())
                    if digest.hexdigest() != profile.mask_sha256[role]:
                        raise ValueError(f"workspace mask guide {master}.{role} digest changed")

    catalog_contract = None
    if "catalog" in payload:
        catalog_ref = payload["catalog"]
        catalog_path = safe_repo_path(catalog_ref["path"], repo_root=REPO_ROOT)
        catalog = load_v2_catalog(catalog_path)
        if catalog.digest != catalog_ref["sha256"]:
            raise ValueError("workspace catalog binding changed")
        catalog_contract = generated_collection_contract(catalog)
        for key in ("baseProfiles", "selectorRegistry", "hatOcclusionPolicy", "qaCases"):
            if payload.get(key) != catalog_contract[key]:
                raise ValueError(f"workspace catalog projection changed for {key}")

    assets = payload["assets"]
    asset_ids = [asset["id"] for asset in assets]
    if len(set(asset_ids)) != len(asset_ids):
        raise ValueError("workspace asset ids must be unique")
    if catalog_contract is None and tuple(asset_ids[:len(REQUIRED_PROOF_ASSET_IDS)]) != REQUIRED_PROOF_ASSET_IDS:
        raise ValueError(f"workspace must begin with the proof assets {list(REQUIRED_PROOF_ASSET_IDS)}")
    by_id = {asset["id"]: asset for asset in assets}
    expected_spec = {item[0]: item for item in PROOF_ASSETS} if catalog_contract is None else {}
    catalog_assets = {} if catalog_contract is None else {
        item["id"]: item for item in catalog_contract["assets"]
    }
    if catalog_contract is not None and asset_ids != [item["id"] for item in catalog_contract["assets"]]:
        raise ValueError("workspace catalog asset order/ids changed")
    valid_slots = {
        "head": {0}, "hair": {0}, "facial-hair": {0}, "body": {1},
        "legs": {2}, "hat": {5}, "legacy-control": {0, 1, 2, 5},
    }
    for asset in assets:
        asset_id = asset["id"]
        kind, slot, propagation = asset["kind"], asset["paperdollSlot"], asset["propagation"]
        actual_channels = tuple((item["id"], item["tintRole"]) for item in asset["channels"])
        if len({item["id"] for item in asset["channels"]}) != len(actual_channels):
            raise ValueError(f"workspace channels changed for {asset_id}")
        if asset_id in expected_spec:
            _, _, kind, slot, source_mode, propagation, channel_specs = expected_spec[asset_id]
            if (asset["kind"], asset["paperdollSlot"], asset["sourceMode"], asset["propagation"]) != (
                    kind, slot, source_mode, propagation):
                raise ValueError(f"workspace asset contract changed for {asset_id}")
            if actual_channels != tuple(channel_specs):
                raise ValueError(f"workspace channels changed for {asset_id}")
        elif asset_id in catalog_assets:
            expected = catalog_assets[asset_id]
            if (kind, slot, asset["sourceMode"], propagation) != (
                    expected["kind"], expected["paperdollSlot"], expected["sourceMode"],
                    expected["propagation"]):
                raise ValueError(f"workspace catalog asset contract changed for {asset_id}")
            if actual_channels != tuple(expected["channels"]):
                raise ValueError(f"workspace catalog channels changed for {asset_id}")
            editable = expected["generator"]["type"] == "catalog-hairstyle"
            if any(channel["editable"] != editable for channel in asset["channels"]):
                raise ValueError(f"workspace catalog editability changed for {asset_id}")
        else:
            if kind not in valid_slots or slot not in valid_slots[kind]:
                raise ValueError(f"workspace kind/slot mismatch for {asset_id}")
            if propagation == "rigid-head" and kind not in {"head", "hair", "facial-hair", "hat"}:
                raise ValueError(f"workspace rigid-head propagation is invalid for {asset_id}")
            if propagation == "explicit-frames" and kind not in {"body", "legs", "legacy-control"}:
                raise ValueError(f"workspace explicit-frame propagation is invalid for {asset_id}")
        for channel in asset["channels"]:
            if channel["tintRole"] not in TINT_ROLES:
                raise ValueError(f"unsupported tint role for {asset_id}.{channel['id']}")
            paths = channel["masters"] if propagation == "rigid-head" else channel["frames"]
            expected_keys = set(MASTERS) if propagation == "rigid-head" else {f"{offset:02d}" for offset in range(18)}
            if set(paths) != expected_keys:
                raise ValueError(f"workspace source paths are incomplete for {asset_id}.{channel['id']}")
            if require_files:
                for key, relative in paths.items():
                    source_path = safe_workspace_path(root, relative)
                    if not source_path.is_file():
                        raise FileNotFoundError(f"workspace source is missing: {relative}")
                    expected_size = (
                        next(item.size for item in template.frames if item.master == key)
                        if propagation == "rigid-head"
                        else template.frames[int(key)].size
                    )
                    with Image.open(source_path) as image:
                        if image.format != "PNG" or image.mode != "RGBA" or image.size != expected_size:
                            raise ValueError(
                                f"workspace source {asset_id}.{channel['id']}.{key} must be "
                                f"an RGBA PNG of size {expected_size[0]}x{expected_size[1]}"
                            )
                        if asset["sourceMode"] == "native" and channel["tintRole"] != "fixed":
                            if any(alpha and not red == green == blue
                                   for red, green, blue, alpha in image.getdata()):
                                raise ValueError(
                                    f"workspace source {asset_id}.{channel['id']}.{key} must use "
                                    "neutral grayscale pixels for semantic tinting"
                                )

    stacks = payload["renderStacks"]
    stack_contracts = ([(item[0], item[2], item[3]) for item in PROOF_STACKS]
                       if catalog_contract is None else [])
    actual_contracts = [(item["id"], item["mode"], tuple(item["assets"])) for item in stacks]
    if stack_contracts and actual_contracts[:len(stack_contracts)] != stack_contracts:
        raise ValueError("workspace must begin with the required proof render stacks")
    if catalog_contract is not None and stacks != catalog_contract["renderStacks"]:
        raise ValueError("workspace catalog render-stack projection changed")
    if len({stack["id"] for stack in stacks}) != len(stacks):
        raise ValueError("workspace render stack ids must be unique")
    stack_by_id = {stack["id"]: stack for stack in stacks}
    for stack in stacks:
        if len(stack["assets"]) != len(set(stack["assets"])) or any(item not in by_id for item in stack["assets"]):
            raise ValueError(f"workspace render stack {stack['id']} is invalid")
        stack_assets = [by_id[item] for item in stack["assets"]]
        if stack["mode"] == "pack-only":
            if any(asset["sourceMode"] != "legacy-upscaled" for asset in stack_assets):
                raise ValueError(f"workspace pack-only stack {stack['id']} contains a native asset")
        else:
            if not any(asset["paperdollSlot"] == 0 and asset["sourceMode"] == "native"
                       for asset in stack_assets):
                raise ValueError(f"workspace live-controls stack {stack['id']} lacks a native head")
            if any(asset["paperdollSlot"] in {0, 5} and asset["sourceMode"] == "legacy-upscaled"
                   for asset in stack_assets):
                raise ValueError(f"workspace live-controls stack {stack['id']} mixes legacy head/hat assets")

    qa_cases = qa_cases_for_manifest(payload)
    if len({case["id"] for case in qa_cases}) != len(qa_cases):
        raise ValueError("workspace QA case ids must be unique")
    for case in qa_cases:
        stack = stack_by_id.get(case["stack"])
        if stack is None:
            raise ValueError(f"workspace QA case {case['id']} names an unknown stack")
        if case["comparisonStack"] is not None and case["comparisonStack"] not in stack_by_id:
            raise ValueError(f"workspace QA case {case['id']} names an unknown comparison stack")
        if any(asset not in stack["assets"] for asset in case["requiredAssets"]):
            raise ValueError(f"workspace QA case {case['id']} requires an asset outside its stack")
        if any(rule["asset"] not in stack["assets"] for rule in case["maskRules"]):
            raise ValueError(f"workspace QA case {case['id']} validates an asset outside its stack")
        scope = case["oracleScope"]
        if (scope == "pack-only-full") != (stack["mode"] == "pack-only"):
            raise ValueError(f"workspace QA case {case['id']} oracle scope disagrees with stack mode")
        if scope.startswith("native-slots-"):
            expected_slots = {0, 1, 2} | ({5} if scope.endswith("-5") else set())
            native_slots = {by_id[item]["paperdollSlot"] for item in stack["assets"]
                            if by_id[item]["sourceMode"] == "native"}
            if not expected_slots.issubset(native_slots):
                raise ValueError(f"workspace QA case {case['id']} lacks its declared native slots")


def load_v2_workspace(root: Path) -> tuple[Path, dict[str, Any], PaperdollV2Template]:
    workspace_root = tmp_workspace_root(root)
    manifest_path = workspace_root / "workspace.json"
    payload = json.loads(manifest_path.read_text())
    if not isinstance(payload, dict):
        raise ValueError("Paperdoll V2 workspace manifest must be an object")
    declared_template = payload.get("template", {}).get("path")
    expected_template = _relative(DEFAULT_TEMPLATE)
    if declared_template != expected_template:
        raise ValueError(f"workspace template path must be {expected_template}")
    template_path = REPO_ROOT / declared_template
    template = load_v2_template(template_path)
    validate_v2_workspace_payload(payload, workspace_root, template, require_files=True)
    return workspace_root, payload, template


__all__ = [
    "BACKGROUND_RGB", "DEFAULT_TINTS", "PROOF_ASSETS", "PROOF_STACKS", "SCHEMA",
    "init_v2_collection_workspace", "init_v2_workspace", "load_v2_workspace",
    "qa_cases_for_manifest", "safe_workspace_path", "semantic_skin_shade",
    "tmp_workspace_root",
    "validate_v2_workspace_payload",
]
