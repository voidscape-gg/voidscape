from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import shutil
from typing import Any, Mapping

from PIL import Image

from .client_preview import ANIM_DIR_LAYER_TO_CHAR_LAYER
from .v2_archive import PACK_NAME, encode_properties, parse_properties
from .v2_compiler import V2CompileResult, V2CompiledAsset
from .v2_selector_registry import validate_selector_registry_properties
from .v2_workspace import DEFAULT_TINTS


SCHEMA = "voidscape-paperdoll-v2-legacy-compatibility/v1"
EXPORT_DIR = "legacy-compatibility"
RUNTIME_PROPERTIES_NAME = "runtime.properties"
RUNTIME_PROPERTIES_SCHEMA = (
    "voidscape-paperdoll-v2-legacy-compatibility-runtime-properties/v1"
)
REPRESENTATIVE_OFFSET = 1
REGIONS_1X = {"head": [0, 30], "body": [30, 62], "legs": [62, 102]}
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _raw_rgba_sha256(image: Image.Image) -> str:
    digest = hashlib.sha256()
    digest.update(image.width.to_bytes(4, "big"))
    digest.update(image.height.to_bytes(4, "big"))
    digest.update(image.convert("RGBA").tobytes())
    return digest.hexdigest()


def _png(path: Path, image: Image.Image) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=False, compress_level=9)


def _json(path: Path, payload: Mapping[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")


def _require_sha256(value: Any, label: str) -> str:
    if not isinstance(value, str) or not SHA256_RE.fullmatch(value):
        raise ValueError(f"legacy compatibility {label} is not a lowercase SHA-256 digest")
    return value


def legacy_runtime_properties_values(
    payload: Mapping[str, Any],
    manifest_sha256: str,
) -> dict[str, str]:
    """Project the JSON compatibility manifest into a strict PC-dev runtime contract."""
    _require_sha256(manifest_sha256, "manifest binding")
    if payload.get("schema") != SCHEMA:
        raise ValueError("unsupported Paperdoll V2 legacy compatibility manifest")
    if payload.get("shipping") is not False or payload.get("activationApproved") is not False:
        raise ValueError("legacy compatibility runtime projection must remain non-shipping")
    if payload.get("outputScope") != "workspace-build-only" or payload.get("runtimeWrites") != {
            "clientCache": False, "serverAvatars": False, "maxSelectors": False}:
        raise ValueError("legacy compatibility runtime projection escaped build-only scope")
    runtime_contract = payload.get("runtimeContract")
    if runtime_contract != {
            "path": f"build/{EXPORT_DIR}/{RUNTIME_PROPERTIES_NAME}",
            "schema": RUNTIME_PROPERTIES_SCHEMA,
    }:
        raise ValueError("legacy compatibility runtime properties declaration changed")
    fallback = payload.get("runtimeFallbackContract")
    if fallback != {
            "defaultEnabled": False,
            "platforms": ["pc-workbench"],
            "compatibleHeadAppearanceId": 8,
            "hatAllowedAppearanceIds": [0],
            "nonzeroHatAction": "suppress-overlay",
            "frameCount": 18,
            "styleFailurePolicy": "reject-whole-style",
    }:
        raise ValueError("legacy compatibility fallback safety contract changed")
    downscale = payload.get("downscale")
    if downscale != {
            "algorithm": "explicit-nearest-2-to-1", "samplePhase": [0, 0],
            "postProcess": {
                "type": "retain-largest-4-connected", "discardedComponentPixels": 1,
                "maxDetachedPixelsRemovedPerFrame": 1,
            },
    }:
        raise ValueError("legacy compatibility runtime downscale contract changed")

    pack = payload["pack"]
    catalog = payload["catalog"]
    template = payload["template"]
    selector_registry = payload["selectorRegistry"]
    overlays = payload["overlays"]
    selector_ids = [overlay["selectorId"] for overlay in overlays]
    if (
        not selector_ids
        or selector_ids != sorted(selector_ids)
        or selector_ids[0] <= 0
        or len(selector_ids) != len(set(selector_ids))
    ):
        raise ValueError("legacy compatibility runtime selectors must be ordered unique positives")

    values = {
        "schema": RUNTIME_PROPERTIES_SCHEMA,
        "shipping": "false",
        "activation.approved": "false",
        "default.enabled": "false",
        "output.scope": "workspace-build-only",
        "runtime.platforms": "pc-workbench",
        "runtime.compatible_head_appearance_id": "8",
        "runtime.hat.allowed_appearance_ids": "0",
        "runtime.hat.nonzero.action": "suppress-overlay",
        "runtime.style_failure_policy": "reject-whole-style",
        "runtime.write.client_cache": "false",
        "runtime.write.server_avatars": "false",
        "runtime.write.max_selectors": "false",
        "manifest.schema": SCHEMA,
        "manifest.path": f"build/{EXPORT_DIR}/manifest.json",
        "manifest.sha256": manifest_sha256,
        "catalog.path": catalog["path"],
        "catalog.sha256": _require_sha256(catalog["sha256"], "catalog binding"),
        "template.path": template["path"],
        "template.sha256": _require_sha256(template["sha256"], "template binding"),
        "template.source_v1.sha256": _require_sha256(
            template["sourceV1Sha256"], "legacy-source binding"
        ),
        "template.derived_masks.sha256": _require_sha256(
            template["derivedMaskTreeSha256"], "derived-mask binding"
        ),
        "pack.path": pack["path"],
        "pack.sha256": _require_sha256(pack["sha256"], "pack binding"),
        "selector_registry.json.path": "build/selector-registry.json",
        "selector_registry.json.sha256": _require_sha256(
            selector_registry["jsonSha256"], "selector JSON binding"
        ),
        "selector_registry.properties.path": "build/selector-registry.properties",
        "selector_registry.properties.sha256": _require_sha256(
            selector_registry["propertiesSha256"], "selector properties binding"
        ),
        "downscale.algorithm": "explicit-nearest-2-to-1",
        "downscale.sample_phase": "0,0",
        "downscale.post_process": "retain-largest-4-connected",
        "downscale.max_detached_pixels_removed_per_frame": "1",
        "frame.count": "18",
        "sidecar.keys": "requiresShift,something1,something2,xShift,yShift",
        "selector.count": str(len(selector_ids)),
        "selector.ids": ",".join(str(selector_id) for selector_id in selector_ids),
        "overlay.frame_count": str(payload["overlayFrameCount"]),
        "overlay.file_count": str(payload["overlayFrameCount"] * 2),
        "avatar.thumbnail_count": str(payload["avatarThumbnailCount"]),
        "avatar.role": payload["avatarContract"]["role"],
    }
    expected_overlay_frames = 0
    for overlay in overlays:
        selector_id = overlay["selectorId"]
        prefix = f"selector.{selector_id}"
        expected_style_path = f"voidscape/hair/style_{selector_id:02d}"
        if overlay["path"] != expected_style_path:
            raise ValueError(f"legacy compatibility selector {selector_id} path changed")
        frames = overlay["frames"]
        if [frame["offset"] for frame in frames] != list(range(18)):
            raise ValueError(f"legacy compatibility selector {selector_id} frames are incomplete")
        values[f"{prefix}.style"] = overlay["style"]
        values[f"{prefix}.asset"] = overlay["asset"]
        values[f"{prefix}.path"] = expected_style_path
        values[f"{prefix}.frame.count"] = "18"
        values[f"{prefix}.frame.ids"] = ",".join(f"{offset:02d}" for offset in range(18))
        for frame in frames:
            offset = frame["offset"]
            frame_prefix = f"{prefix}.frame.{offset:02d}"
            expected_png = f"{expected_style_path}/frame_{offset:02d}.png"
            expected_sidecar = f"{expected_style_path}/frame_{offset:02d}.properties"
            if frame["png"] != expected_png or frame["properties"] != expected_sidecar:
                raise ValueError(
                    f"legacy compatibility selector {selector_id} frame {offset} path changed"
                )
            logical_width, logical_height = frame["logicalSize"]
            expected_width = 84 if offset >= 15 else 64
            if (logical_width, logical_height) != (expected_width, 102):
                raise ValueError(
                    f"legacy compatibility selector {selector_id} frame {offset} geometry changed"
                )
            crop = frame.get("crop")
            if not isinstance(crop, Mapping) or set(crop) != {"x", "y", "width", "height"}:
                raise ValueError(
                    f"legacy compatibility selector {selector_id} frame {offset} crop is incomplete"
                )
            crop_x, crop_y = crop["x"], crop["y"]
            crop_width, crop_height = crop["width"], crop["height"]
            if (
                any(not isinstance(value, int) for value in (
                    crop_x, crop_y, crop_width, crop_height
                ))
                or min(crop_x, crop_y) < 0
                or min(crop_width, crop_height) <= 0
                or crop_x + crop_width > logical_width
                or crop_y + crop_height > logical_height
            ):
                raise ValueError(
                    f"legacy compatibility selector {selector_id} frame {offset} crop escapes canvas"
                )
            if frame.get("requiresShift") is not True or frame["fourConnectedComponents"] != 1:
                raise ValueError(
                    f"legacy compatibility selector {selector_id} frame {offset} sidecar changed"
                )
            values[f"{frame_prefix}.png.path"] = expected_png
            values[f"{frame_prefix}.png.sha256"] = _require_sha256(
                frame["pngSha256"], f"selector {selector_id} frame {offset} PNG"
            )
            values[f"{frame_prefix}.sidecar.path"] = expected_sidecar
            values[f"{frame_prefix}.sidecar.sha256"] = _require_sha256(
                frame["propertiesSha256"], f"selector {selector_id} frame {offset} sidecar"
            )
            values[f"{frame_prefix}.logical_rgba.sha256"] = _require_sha256(
                frame["logicalRgbaSha256"], f"selector {selector_id} frame {offset} logical RGBA"
            )
            values[f"{frame_prefix}.source_decimated_rgba.sha256"] = _require_sha256(
                frame["sourceDecimatedRgbaSha256"],
                f"selector {selector_id} frame {offset} source decimation",
            )
            values[f"{frame_prefix}.logical.width"] = str(logical_width)
            values[f"{frame_prefix}.logical.height"] = str(logical_height)
            values[f"{frame_prefix}.crop.x"] = str(crop_x)
            values[f"{frame_prefix}.crop.y"] = str(crop_y)
            values[f"{frame_prefix}.crop.width"] = str(crop_width)
            values[f"{frame_prefix}.crop.height"] = str(crop_height)
            values[f"{frame_prefix}.sidecar.requires_shift"] = "true"
            values[f"{frame_prefix}.four_connected_components"] = "1"
            values[f"{frame_prefix}.detached_pixels_removed"] = str(
                frame["detachedPixelsRemoved"]
            )
            expected_overlay_frames += 1
    if expected_overlay_frames != payload["overlayFrameCount"]:
        raise ValueError("legacy compatibility runtime overlay count changed")
    return values


def legacy_runtime_properties_bytes(
    payload: Mapping[str, Any],
    manifest_sha256: str,
) -> bytes:
    return encode_properties(legacy_runtime_properties_values(payload, manifest_sha256))


def validate_legacy_runtime_properties(root: Path, payload: Mapping[str, Any]) -> Mapping[str, str]:
    manifest_path = root / "manifest.json"
    runtime_path = root / RUNTIME_PROPERTIES_NAME
    actual = parse_properties(runtime_path.read_bytes())
    expected = legacy_runtime_properties_values(payload, _sha256(manifest_path))
    if actual != expected:
        unknown = sorted(set(actual) - set(expected))
        missing = sorted(set(expected) - set(actual))
        changed = sorted(
            key for key in set(actual) & set(expected) if actual[key] != expected[key]
        )
        raise ValueError(
            "legacy compatibility runtime properties differ from their manifest/provenance binding; "
            f"unknown={unknown}, missing={missing}, changed={changed}"
        )
    return actual


def nearest_downscale_2_to_1(image: Image.Image) -> Image.Image:
    """Deterministic nearest decimation using the topology-preserving (even, even) phase."""
    rgba = image.convert("RGBA")
    if rgba.width % 2 or rgba.height % 2 or min(rgba.size) <= 0:
        raise ValueError("Paperdoll V2 legacy export requires positive even 2x geometry")
    source = rgba.load()
    output = Image.new("RGBA", (rgba.width // 2, rgba.height // 2), (0, 0, 0, 0))
    target = output.load()
    for y in range(output.height):
        for x in range(output.width):
            target[x, y] = source[x * 2, y * 2]
    return output


def _asset_canvas(asset: V2CompiledAsset, offset: int) -> Image.Image:
    size = asset.channels[0].frames[offset].canvas.size
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    for channel in asset.channels:
        frame = channel.frames[offset]
        if frame.canvas.size != size:
            raise ValueError(f"Paperdoll V2 asset {asset.id} channel geometry differs")
        canvas = Image.alpha_composite(canvas, frame.canvas.convert("RGBA"))
    return canvas


def _tint(image: Image.Image, role: str) -> Image.Image:
    rgba = image.convert("RGBA")
    if role == "fixed":
        return rgba
    value = DEFAULT_TINTS[role]
    transform = value >> 16 & 255, value >> 8 & 255, value & 255
    output = Image.new("RGBA", rgba.size, (0, 0, 0, 0))
    output.putdata([
        (red * transform[0] // 255, green * transform[1] // 255,
         blue * transform[2] // 255, alpha)
        for red, green, blue, alpha in rgba.getdata()
    ])
    return output


def _tinted_asset_canvas(asset: V2CompiledAsset, offset: int) -> Image.Image:
    size = asset.channels[0].frames[offset].canvas.size
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    for channel in asset.channels:
        canvas = Image.alpha_composite(canvas, _tint(channel.frames[offset].canvas, channel.tint_role))
    return canvas


def _stack(result: V2CompileResult, stack_id: str) -> Mapping[str, Any]:
    try:
        return next(stack for stack in result.manifest["renderStacks"] if stack["id"] == stack_id)
    except StopIteration as exc:
        raise ValueError(f"legacy export names unknown stack {stack_id}") from exc


def _hair_asset(result: V2CompileResult, entry: Mapping[str, Any]) -> V2CompiledAsset:
    hair_ids = set()
    for stack_id in entry["stackByBase"].values():
        stack = _stack(result, stack_id)
        hair_ids.update(
            asset_id for asset_id in stack["assets"] if result.asset(asset_id).kind == "hair"
        )
    if len(hair_ids) != 1:
        raise ValueError(f"selector {entry['selectorId']} must resolve to one shared hair asset")
    return result.asset(next(iter(hair_ids)))


def _render_avatar(
    result: V2CompileResult,
    entry: Mapping[str, Any],
    base: str,
) -> Image.Image:
    control_stack = _stack(result, entry["qaControlStackByBase"][base])
    assets = [result.asset(asset_id) for asset_id in control_stack["assets"]]
    by_slot = {slot: [asset for asset in assets if asset.paperdoll_slot == slot] for slot in range(12)}
    canvas = Image.new("RGBA", (128, 204), (0, 0, 0, 0))
    for slot in ANIM_DIR_LAYER_TO_CHAR_LAYER[0]:
        for asset in by_slot[slot]:
            rendered = _tinted_asset_canvas(asset, REPRESENTATIVE_OFFSET)
            if rendered.size != canvas.size:
                raise ValueError(f"avatar asset {asset.id} differs from the locked walk canvas")
            canvas = Image.alpha_composite(canvas, rendered)
    avatar = nearest_downscale_2_to_1(canvas)
    if entry["route"] == "v2":
        hair, _, _ = _legacy_hair_frame(_hair_asset(result, entry), REPRESENTATIVE_OFFSET)
        avatar = Image.alpha_composite(avatar, _tint(hair, "hair"))
    return avatar


def _region_counts(image: Image.Image) -> dict[str, int]:
    alpha = image.convert("RGBA").getchannel("A")
    return {
        name: sum(value > 0 for value in alpha.crop((0, start, alpha.width, end)).getdata())
        for name, (start, end) in REGIONS_1X.items()
    }


def _component_sets(image: Image.Image) -> list[set[tuple[int, int]]]:
    alpha = image.convert("RGBA").getchannel("A")
    remaining = {
        (x, y) for y in range(alpha.height) for x in range(alpha.width)
        if alpha.getpixel((x, y)) > 0
    }
    components = []
    while remaining:
        component = {remaining.pop()}
        pending = list(component)
        while pending:
            x, y = pending.pop()
            for neighbour in ((x, y - 1), (x - 1, y), (x + 1, y), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    component.add(neighbour)
                    pending.append(neighbour)
        components.append(component)
    return sorted(components, key=lambda item: (-len(item), min(item)))


def _four_connected_components(image: Image.Image) -> int:
    return len(_component_sets(image))


def _prune_single_pixel_components(image: Image.Image) -> tuple[Image.Image, int]:
    rgba = image.convert("RGBA")
    components = _component_sets(rgba)
    if not components:
        raise ValueError("Paperdoll V2 legacy overlay frame is empty")
    discarded = components[1:]
    removed = sum(len(component) for component in discarded)
    if any(len(component) != 1 for component in discarded) or removed > 1:
        raise ValueError(
            "Paperdoll V2 legacy overlay decimation detached more than one single pixel"
        )
    if not discarded:
        return rgba, 0
    output = rgba.copy()
    pixels = output.load()
    for component in discarded:
        for x, y in component:
            pixels[x, y] = (0, 0, 0, 0)
    return output, removed


def _legacy_hair_frame(asset: V2CompiledAsset, offset: int) -> tuple[Image.Image, int, Image.Image]:
    decimated = nearest_downscale_2_to_1(_asset_canvas(asset, offset))
    pruned, removed = _prune_single_pixel_components(decimated)
    return pruned, removed, decimated


def _selector_payload(build: Path) -> Mapping[str, Any]:
    json_path = build / "selector-registry.json"
    properties_path = build / "selector-registry.properties"
    payload = json.loads(json_path.read_text())
    validate_selector_registry_properties(properties_path.read_bytes(), payload)
    return payload


def _export_root(result: V2CompileResult, build: Path) -> Path:
    expected = (result.root / "build").resolve()
    if build.resolve() != expected:
        raise ValueError("Paperdoll V2 legacy compatibility export must remain in workspace build")
    return build / EXPORT_DIR


def _overlay_sidecar(full: Image.Image) -> tuple[Image.Image, dict[str, str]]:
    bbox = full.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Paperdoll V2 legacy overlay frame is empty")
    left, top, right, bottom = bbox
    return full.crop(bbox), {
        "requiresShift": "true",
        "xShift": str(left),
        "yShift": str(top),
        "something1": str(full.width),
        "something2": str(full.height),
    }


def validate_v2_legacy_export(
    result: V2CompileResult,
    build: Path,
    pack_path: Path,
) -> Mapping[str, Any]:
    root = _export_root(result, build)
    payload = json.loads((root / "manifest.json").read_text())
    if payload.get("schema") != SCHEMA or payload.get("shipping") is not False \
            or payload.get("activationApproved") is not False:
        raise ValueError("unsupported Paperdoll V2 legacy compatibility manifest")
    selector = _selector_payload(build)
    if payload["catalog"] != selector["catalog"] or payload["template"] != selector["template"]:
        raise ValueError("legacy compatibility provenance differs from selector registry")
    expected_pack = {"path": str(pack_path.relative_to(result.root)), "sha256": _sha256(pack_path)}
    if payload["pack"] != expected_pack or selector["pack"] != expected_pack:
        raise ValueError("legacy compatibility pack binding changed")
    if payload["downscale"] != {
        "algorithm": "explicit-nearest-2-to-1", "samplePhase": [0, 0],
        "postProcess": {
            "type": "retain-largest-4-connected", "discardedComponentPixels": 1,
            "maxDetachedPixelsRemovedPerFrame": 1,
        },
    }:
        raise ValueError("legacy compatibility downscale contract changed")
    if payload.get("outputScope") != "workspace-build-only" or payload.get("runtimeWrites") != {
            "clientCache": False, "serverAvatars": False, "maxSelectors": False}:
        raise ValueError("legacy compatibility output escaped its build-only contract")
    if payload.get("selectorRegistry") != {
        "jsonSha256": _sha256(build / "selector-registry.json"),
        "propertiesSha256": _sha256(build / "selector-registry.properties"),
    }:
        raise ValueError("legacy compatibility selector-registry binding changed")

    entries = selector["entries"]
    if (root / "voidscape/hair/style_00").exists():
        raise ValueError("Classic selector 0 must not emit a legacy overlay")
    overlay_count = 0
    detached_total = 0
    expected_overlays = []
    for entry in entries:
        selector_id = entry["selectorId"]
        if selector_id == 0:
            if entry["route"] != "legacy":
                raise ValueError("Classic selector 0 legacy route changed")
            continue
        asset = _hair_asset(result, entry)
        style_dir = root / "voidscape/hair" / f"style_{selector_id:02d}"
        frames = []
        for offset in range(18):
            expected_full, detached_removed, source_decimated = _legacy_hair_frame(asset, offset)
            expected_logical = (84, 102) if offset >= 15 else (64, 102)
            if expected_full.size != expected_logical:
                raise ValueError(f"selector {selector_id} frame {offset} logical geometry changed")
            png_path = style_dir / f"frame_{offset:02d}.png"
            properties_path = style_dir / f"frame_{offset:02d}.properties"
            with Image.open(png_path) as source:
                if source.format != "PNG" or source.mode != "RGBA":
                    raise ValueError(f"legacy overlay {selector_id}/{offset} must be RGBA PNG")
                cropped = source.copy()
            properties = parse_properties(properties_path.read_bytes())
            expected_cropped, expected_properties = _overlay_sidecar(expected_full)
            if properties != expected_properties or cropped.tobytes() != expected_cropped.tobytes():
                raise ValueError(f"legacy overlay {selector_id}/{offset} differs from deterministic 2:1 export")
            reconstructed = Image.new("RGBA", expected_full.size, (0, 0, 0, 0))
            reconstructed.paste(cropped, (int(properties["xShift"]), int(properties["yShift"])))
            if reconstructed.tobytes() != expected_full.tobytes():
                raise ValueError(f"legacy overlay {selector_id}/{offset} sidecar reconstruction lost RGBA")
            if any(alpha and not red == green == blue for red, green, blue, alpha in cropped.getdata()):
                raise ValueError(f"legacy overlay {selector_id}/{offset} is not neutral grayscale")
            components = _four_connected_components(expected_full)
            if components != 1:
                raise ValueError(
                    f"legacy overlay {selector_id}/{offset} has {components} four-connected components"
                )
            frames.append({
                "offset": offset, "png": str(png_path.relative_to(root)),
                "pngSha256": _sha256(png_path),
                "properties": str(properties_path.relative_to(root)),
                "propertiesSha256": _sha256(properties_path),
                "requiresShift": True,
                "crop": {
                    "x": int(expected_properties["xShift"]),
                    "y": int(expected_properties["yShift"]),
                    "width": expected_cropped.width,
                    "height": expected_cropped.height,
                },
                "logicalRgbaSha256": _raw_rgba_sha256(expected_full),
                "sourceDecimatedRgbaSha256": _raw_rgba_sha256(source_decimated),
                "logicalSize": list(expected_full.size),
                "fourConnectedComponents": components,
                "detachedPixelsRemoved": detached_removed,
            })
            overlay_count += 1
            detached_total += detached_removed
        expected_overlays.append({
            "selectorId": selector_id, "style": entry["style"], "asset": asset.id,
            "path": str(style_dir.relative_to(root)), "frames": frames,
        })
    if payload.get("overlays") != expected_overlays:
        raise ValueError("legacy compatibility overlay manifest mapping changed")
    if payload.get("detachedPixelsRemoved") != detached_total:
        raise ValueError("legacy compatibility detached-pixel accounting changed")

    avatar_count = 0
    expected_avatars = []
    for entry in entries:
        for base in entry["baseProfiles"]:
            expected = _render_avatar(result, entry, base)
            avatar_path = root / "avatars" / f"selector_{entry['selectorId']:02d}" / f"{base}.png"
            with Image.open(avatar_path) as source:
                if source.format != "PNG" or source.mode != "RGBA" or source.size != (64, 102):
                    raise ValueError("legacy avatar QA thumbnail must be a transparent-capable 64x102 PNG")
                actual = source.copy()
            if actual.tobytes() != expected.tobytes():
                raise ValueError(f"legacy avatar QA thumbnail differs for selector {entry['selectorId']} {base}")
            counts = _region_counts(actual)
            if any(value == 0 for value in counts.values()):
                raise ValueError(f"legacy avatar QA thumbnail lacks a figure region for selector {entry['selectorId']} {base}")
            expected_avatars.append({
                "selectorId": entry["selectorId"], "style": entry["style"], "baseProfile": base,
                "role": "qa-thumbnail-non-runtime", "path": str(avatar_path.relative_to(root)),
                "sha256": _sha256(avatar_path), "rawRgbaSha256": _raw_rgba_sha256(actual),
                "nonzeroRegionPixels": counts,
            })
            avatar_count += 1
    if payload.get("avatars") != expected_avatars or payload.get("avatarContract") != {
        "role": "qa-thumbnail-non-runtime", "width": 64, "height": 102,
        "state": {"kind": "walk", "direction": "north", "frame": 1,
                  "spriteOffset": REPRESENTATIVE_OFFSET},
        "regions": REGIONS_1X,
    }:
        raise ValueError("legacy compatibility avatar QA manifest mapping changed")

    actual_artifacts = [
        {"path": str(path.relative_to(root)), "sha256": _sha256(path), "bytes": path.stat().st_size}
        for path in sorted(
            item for item in root.rglob("*")
            if item.is_file() and item.name not in {"manifest.json", RUNTIME_PROPERTIES_NAME}
        )
    ]
    if payload["artifacts"] != actual_artifacts:
        raise ValueError("legacy compatibility artifact inventory changed")
    if payload["overlayFrameCount"] != overlay_count or payload["avatarThumbnailCount"] != avatar_count:
        raise ValueError("legacy compatibility output counts changed")
    validate_legacy_runtime_properties(root, payload)
    return payload


def write_v2_legacy_export(
    result: V2CompileResult,
    build: Path,
    pack_path: Path,
) -> dict[str, Any] | None:
    if "selectorRegistry" not in result.manifest:
        return None
    root = _export_root(result, build)
    if root.exists():
        shutil.rmtree(root)
    root.mkdir(parents=True)
    selector = _selector_payload(build)
    if selector["pack"] != {"path": str(pack_path.relative_to(result.root)), "sha256": _sha256(pack_path)}:
        raise ValueError("selector registry is not bound to the legacy export pack")

    overlay_count = 0
    detached_total = 0
    overlays = []
    for entry in selector["entries"]:
        if entry["selectorId"] == 0:
            continue
        asset = _hair_asset(result, entry)
        frames = []
        style_dir = root / "voidscape/hair" / f"style_{entry['selectorId']:02d}"
        for offset in range(18):
            full, detached_removed, source_decimated = _legacy_hair_frame(asset, offset)
            cropped, sidecar = _overlay_sidecar(full)
            png_path = style_dir / f"frame_{offset:02d}.png"
            properties_path = style_dir / f"frame_{offset:02d}.properties"
            _png(png_path, cropped)
            properties_path.write_bytes(encode_properties(sidecar))
            frames.append({
                "offset": offset, "png": str(png_path.relative_to(root)),
                "pngSha256": _sha256(png_path),
                "properties": str(properties_path.relative_to(root)),
                "propertiesSha256": _sha256(properties_path),
                "requiresShift": True,
                "crop": {
                    "x": int(sidecar["xShift"]), "y": int(sidecar["yShift"]),
                    "width": cropped.width, "height": cropped.height,
                },
                "logicalRgbaSha256": _raw_rgba_sha256(full),
                "sourceDecimatedRgbaSha256": _raw_rgba_sha256(source_decimated),
                "logicalSize": list(full.size),
                "fourConnectedComponents": _four_connected_components(full),
                "detachedPixelsRemoved": detached_removed,
            })
            overlay_count += 1
            detached_total += detached_removed
        overlays.append({
            "selectorId": entry["selectorId"], "style": entry["style"],
            "asset": asset.id, "path": str(style_dir.relative_to(root)), "frames": frames,
        })

    avatars = []
    avatar_count = 0
    for entry in selector["entries"]:
        for base in entry["baseProfiles"]:
            image = _render_avatar(result, entry, base)
            path = root / "avatars" / f"selector_{entry['selectorId']:02d}" / f"{base}.png"
            _png(path, image)
            avatars.append({
                "selectorId": entry["selectorId"], "style": entry["style"], "baseProfile": base,
                "role": "qa-thumbnail-non-runtime", "path": str(path.relative_to(root)),
                "sha256": _sha256(path), "rawRgbaSha256": _raw_rgba_sha256(image),
                "nonzeroRegionPixels": _region_counts(image),
            })
            avatar_count += 1

    artifacts = [
        {"path": str(path.relative_to(root)), "sha256": _sha256(path), "bytes": path.stat().st_size}
        for path in sorted(item for item in root.rglob("*") if item.is_file())
    ]
    payload = {
        "schema": SCHEMA,
        "shipping": False,
        "activationApproved": False,
        "outputScope": "workspace-build-only",
        "runtimeWrites": {"clientCache": False, "serverAvatars": False, "maxSelectors": False},
        "runtimeContract": {
            "path": f"build/{EXPORT_DIR}/{RUNTIME_PROPERTIES_NAME}",
            "schema": RUNTIME_PROPERTIES_SCHEMA,
        },
        "runtimeFallbackContract": {
            "defaultEnabled": False,
            "platforms": ["pc-workbench"],
            "compatibleHeadAppearanceId": 8,
            "hatAllowedAppearanceIds": [0],
            "nonzeroHatAction": "suppress-overlay",
            "frameCount": 18,
            "styleFailurePolicy": "reject-whole-style",
        },
        "catalog": selector["catalog"],
        "template": selector["template"],
        "pack": selector["pack"],
        "selectorRegistry": {
            "jsonSha256": _sha256(build / "selector-registry.json"),
            "propertiesSha256": _sha256(build / "selector-registry.properties"),
        },
        "downscale": {
            "algorithm": "explicit-nearest-2-to-1", "samplePhase": [0, 0],
            "postProcess": {
                "type": "retain-largest-4-connected", "discardedComponentPixels": 1,
                "maxDetachedPixelsRemovedPerFrame": 1,
            },
        },
        "overlayFrameCount": overlay_count,
        "detachedPixelsRemoved": detached_total,
        "overlays": overlays,
        "avatarThumbnailCount": avatar_count,
        "avatarContract": {
            "role": "qa-thumbnail-non-runtime", "width": 64, "height": 102,
            "state": {"kind": "walk", "direction": "north", "frame": 1,
                      "spriteOffset": REPRESENTATIVE_OFFSET},
            "regions": REGIONS_1X,
        },
        "avatars": avatars,
        "artifacts": artifacts,
    }
    manifest_path = root / "manifest.json"
    runtime_path = root / RUNTIME_PROPERTIES_NAME
    _json(manifest_path, payload)
    runtime_path.write_bytes(legacy_runtime_properties_bytes(payload, _sha256(manifest_path)))
    validated = validate_v2_legacy_export(result, build, pack_path)
    return {
        "valid": True,
        "path": str(manifest_path.relative_to(result.root)),
        "sha256": _sha256(manifest_path),
        "runtimePropertiesPath": str(runtime_path.relative_to(result.root)),
        "runtimePropertiesSha256": _sha256(runtime_path),
        "overlayFrameCount": validated["overlayFrameCount"],
        "detachedPixelsRemoved": validated["detachedPixelsRemoved"],
        "avatarThumbnailCount": validated["avatarThumbnailCount"],
        "activationApproved": False,
    }


__all__ = [
    "EXPORT_DIR", "REPRESENTATIVE_OFFSET", "RUNTIME_PROPERTIES_NAME",
    "RUNTIME_PROPERTIES_SCHEMA", "SCHEMA", "legacy_runtime_properties_bytes",
    "legacy_runtime_properties_values", "nearest_downscale_2_to_1",
    "validate_legacy_runtime_properties", "validate_v2_legacy_export",
    "write_v2_legacy_export",
]
