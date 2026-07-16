from __future__ import annotations

import hashlib
import re
import struct
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from PIL import Image

from .v2_compiler import V2CompileResult, V2CompiledFrame, V2Sidecar


PACK_SCHEMA = "voidscape-paperdoll-v2-pack/v1"
PACK_NAME = "Paperdoll_V2.orsc"
ID_RE = re.compile(r"^[a-z][a-z0-9_]*$")
HEX_RE = re.compile(r"^[0-9a-f]{64}$")
FIXED_ZIP_TIME = (1980, 1, 1, 0, 0, 0)


@dataclass(frozen=True)
class DecodedV2Sprite:
    image: Image.Image
    sidecar: V2Sidecar


@dataclass(frozen=True)
class V2PackResult:
    path: Path
    sha256: str
    registry: Mapping[str, str]
    entry_sha256: Mapping[str, str]


def _sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def encode_v2_sprite(frame: V2CompiledFrame) -> bytes:
    image = frame.cropped.convert("RGBA")
    sidecar = frame.sidecar
    header = struct.pack(
        ">iiBiiii",
        image.width,
        image.height,
        1 if sidecar.requires_shift else 0,
        sidecar.x_shift,
        sidecar.y_shift,
        sidecar.logical_width,
        sidecar.logical_height,
    )
    pixels = bytearray(image.width * image.height * 4)
    cursor = 0
    for red, green, blue, alpha in image.getdata():
        struct.pack_into(">I", pixels, cursor, (alpha << 24) | (red << 16) | (green << 8) | blue)
        cursor += 4
    return header + bytes(pixels)


def decode_v2_sprite(data: bytes) -> DecodedV2Sprite:
    if len(data) < 25:
        raise ValueError("V2 sprite header is truncated")
    width, height, shift, x_shift, y_shift, logical_width, logical_height = struct.unpack_from(
        ">iiBiiii", data, 0,
    )
    if shift != 1 or min(width, height, logical_width, logical_height) <= 0:
        raise ValueError("V2 sprite header geometry is invalid")
    if x_shift < 0 or y_shift < 0 or x_shift + width > logical_width or y_shift + height > logical_height:
        raise ValueError("V2 sprite crop falls outside logical canvas")
    expected = 25 + width * height * 4
    if len(data) != expected:
        raise ValueError("V2 sprite payload is truncated or has trailing bytes")
    rgba = bytearray(width * height * 4)
    cursor = 0
    for offset in range(25, len(data), 4):
        argb = struct.unpack_from(">I", data, offset)[0]
        rgba[cursor:cursor + 4] = bytes((argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >> 24 & 255))
        cursor += 4
    sidecar = V2Sidecar(bool(shift), x_shift, y_shift, logical_width, logical_height)
    return DecodedV2Sprite(Image.frombytes("RGBA", (width, height), bytes(rgba)), sidecar)


def encode_properties(values: Mapping[str, str]) -> bytes:
    for key, value in values.items():
        if not key or any(character in key for character in "\r\n=:"):
            raise ValueError(f"unsafe registry property key {key!r}")
        if any(character in value for character in "\r\n"):
            raise ValueError(f"unsafe registry property value for {key}")
        try:
            key.encode("ascii")
            value.encode("ascii")
        except UnicodeEncodeError as exc:
            raise ValueError("registry.properties must remain ASCII") from exc
    return ("".join(f"{key}={values[key]}\n" for key in sorted(values))).encode("ascii")


def parse_properties(data: bytes) -> dict[str, str]:
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError as exc:
        raise ValueError("registry.properties must be ASCII") from exc
    if not text.endswith("\n") or "\r" in text:
        raise ValueError("registry.properties must use terminal LF and no CR characters")
    values: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.startswith(("#", "!")) or "=" not in line:
            raise ValueError("registry.properties contains an unsupported line")
        key, value = line.split("=", 1)
        if key in values:
            raise ValueError(f"duplicate registry property {key}")
        values[key] = value
    if data != encode_properties(values):
        raise ValueError("registry.properties keys must be sorted canonically")
    return values


def _registry_and_entries(result: V2CompileResult) -> tuple[dict[str, str], dict[str, bytes]]:
    registry: dict[str, str] = {
        "schema": PACK_SCHEMA,
        "template": result.template.key,
        "template.sha256": result.template.digest,
        "template.source_v1.sha256": result.template.source_digest,
        "template.derived_masks.sha256": result.template.derived_mask_tree_sha256,
        "frame.count": "18",
        "canvas.walk.width": "128",
        "canvas.combat.width": "168",
        "canvas.height": "204",
        "preview.width": "176",
        "preview.height": "224",
        "preview.draw_x": "24",
        "preview.draw_y": "4",
        "preview.draw_width": "128",
        "preview.draw_height": "204",
        "asset.ids": ",".join(asset.id for asset in result.assets),
    }
    entries: dict[str, bytes] = {}
    for asset in result.assets:
        if not ID_RE.fullmatch(asset.id):
            raise ValueError(f"unsafe V2 asset id {asset.id!r}")
        prefix = f"asset.{asset.id}"
        registry[f"{prefix}.kind"] = asset.kind
        registry[f"{prefix}.paperdoll_slot"] = str(asset.paperdoll_slot)
        registry[f"{prefix}.source_mode"] = asset.source_mode
        registry[f"{prefix}.propagation"] = asset.propagation
        registry[f"{prefix}.channels"] = ",".join(channel.id for channel in asset.channels)
        for channel in asset.channels:
            if not ID_RE.fullmatch(channel.id):
                raise ValueError(f"unsafe V2 channel id {channel.id!r}")
            registry[f"{prefix}.channel.{channel.id}.tint"] = channel.tint_role
            for frame in channel.frames:
                entry = f"sprites/{asset.id}/{channel.id}/frame_{frame.offset:02d}"
                payload = encode_v2_sprite(frame)
                if entry in entries:
                    raise ValueError(f"duplicate V2 archive entry {entry}")
                entries[entry] = payload
                frame_prefix = f"{prefix}.frame.{frame.offset:02d}.{channel.id}"
                registry[f"{frame_prefix}.entry"] = entry
                registry[f"{frame_prefix}.sha256"] = _sha256_bytes(payload)
                registry[f"{frame_prefix}.empty"] = "true" if frame.empty else "false"
    stacks = result.manifest["renderStacks"]
    registry["render.stack.ids"] = ",".join(stack["id"] for stack in stacks)
    for stack in stacks:
        registry[f"render.stack.{stack['id']}.assets"] = ",".join(stack["assets"])
        registry[f"render.stack.{stack['id']}.mode"] = stack["mode"]
    return registry, entries


def _zip_info(name: str) -> zipfile.ZipInfo:
    info = zipfile.ZipInfo(name, FIXED_ZIP_TIME)
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    return info


def write_v2_pack(result: V2CompileResult, output: Path) -> V2PackResult:
    registry, entries = _registry_and_entries(result)
    all_entries = {"registry.properties": encode_properties(registry), **entries}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", allowZip64=False) as archive:
        for name in sorted(all_entries):
            archive.writestr(_zip_info(name), all_entries[name])
    validate_v2_pack(output)
    return V2PackResult(
        output.resolve(), _sha256(output), registry,
        {name: _sha256_bytes(payload) for name, payload in entries.items()},
    )


def _csv(value: str, label: str) -> list[str]:
    items = value.split(",") if value else []
    if not items or any(not ID_RE.fullmatch(item) for item in items) or len(items) != len(set(items)):
        raise ValueError(f"registry {label} must be unique strict ids")
    return items


def _required(registry: Mapping[str, str], key: str) -> str:
    try:
        return registry[key]
    except KeyError as exc:
        raise ValueError(f"registry property is missing: {key}") from exc


def validate_v2_pack(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)) or names != sorted(names):
            raise ValueError("V2 archive entries must be unique and sorted")
        if archive.comment:
            raise ValueError("V2 archive comment must be empty")
        for info in archive.infolist():
            if (info.date_time != FIXED_ZIP_TIME or info.compress_type != zipfile.ZIP_STORED or info.extra
                    or info.create_system != 3 or info.external_attr != (0o100644 << 16)):
                raise ValueError(f"V2 archive metadata is not deterministic for {info.filename}")
        try:
            registry = parse_properties(archive.read("registry.properties"))
        except KeyError as exc:
            raise ValueError("V2 archive is missing registry.properties") from exc
        if _required(registry, "schema") != PACK_SCHEMA or _required(registry, "frame.count") != "18":
            raise ValueError("unsupported V2 pack registry")
        fixed = {
            "canvas.walk.width": "128", "canvas.combat.width": "168", "canvas.height": "204",
            "preview.width": "176", "preview.height": "224", "preview.draw_x": "24",
            "preview.draw_y": "4", "preview.draw_width": "128", "preview.draw_height": "204",
        }
        if any(_required(registry, key) != value for key, value in fixed.items()):
            raise ValueError("V2 pack geometry differs from the locked contract")
        asset_ids = _csv(_required(registry, "asset.ids"), "asset.ids")
        expected_entries = {"registry.properties"}
        asset_slots: dict[str, int] = {}
        asset_source_modes: dict[str, str] = {}
        for asset_id in asset_ids:
            prefix = f"asset.{asset_id}"
            kind = _required(registry, f"{prefix}.kind")
            try:
                slot = int(_required(registry, f"{prefix}.paperdoll_slot"))
            except ValueError as exc:
                raise ValueError(f"registry {prefix}.paperdoll_slot is not an integer") from exc
            valid_slots = {
                "head": {0}, "hair": {0}, "facial-hair": {0}, "body": {1},
                "legs": {2}, "hat": {5}, "legacy-control": {0, 1, 2, 5},
            }
            if kind not in valid_slots or slot not in valid_slots[kind]:
                raise ValueError(f"registry kind/slot mismatch for {asset_id}")
            asset_slots[asset_id] = slot
            source_mode = _required(registry, f"{prefix}.source_mode")
            if source_mode not in {"native", "legacy-upscaled"}:
                raise ValueError(f"registry source mode is invalid for {asset_id}")
            asset_source_modes[asset_id] = source_mode
            if _required(registry, f"{prefix}.propagation") not in {"rigid-head", "explicit-frames"}:
                raise ValueError(f"registry propagation is invalid for {asset_id}")
            channels = _csv(_required(registry, f"{prefix}.channels"), f"{prefix}.channels")
            for channel in channels:
                tint = _required(registry, f"{prefix}.channel.{channel}.tint")
                if tint not in {"skin", "hair", "facial-hair", "top", "bottom", "primary", "secondary", "fixed"}:
                    raise ValueError(f"registry tint role is invalid for {asset_id}.{channel}")
                for offset in range(18):
                    frame_prefix = f"{prefix}.frame.{offset:02d}.{channel}"
                    entry = _required(registry, f"{frame_prefix}.entry")
                    expected_name = f"sprites/{asset_id}/{channel}/frame_{offset:02d}"
                    if entry != expected_name or entry in expected_entries:
                        raise ValueError(f"registry entry name is invalid or duplicated: {entry}")
                    expected_entries.add(entry)
                    try:
                        data = archive.read(entry)
                    except KeyError as exc:
                        raise ValueError(f"V2 pack frame/channel is missing: {entry}") from exc
                    digest = _required(registry, f"{frame_prefix}.sha256")
                    if not HEX_RE.fullmatch(digest) or _sha256_bytes(data) != digest:
                        raise ValueError(f"V2 pack entry digest mismatch: {entry}")
                    sprite = decode_v2_sprite(data)
                    expected_logical = (168, 204) if offset >= 15 else (128, 204)
                    if (sprite.sidecar.logical_width, sprite.sidecar.logical_height) != expected_logical:
                        raise ValueError(f"V2 pack logical dimensions differ for {entry}")
                    empty = _required(registry, f"{frame_prefix}.empty")
                    if empty not in {"true", "false"}:
                        raise ValueError(f"V2 pack empty flag is invalid for {entry}")
                    nonzero = sprite.image.getchannel("A").getbbox() is not None
                    if empty == "true" and (sprite.image.size != (1, 1) or nonzero):
                        raise ValueError(f"V2 pack empty frame encoding is invalid for {entry}")
                    if empty == "false" and not nonzero:
                        raise ValueError(f"V2 pack non-empty frame has no alpha: {entry}")
        stack_ids = _csv(_required(registry, "render.stack.ids"), "render.stack.ids")
        referenced_assets: set[str] = set()
        for stack_id in stack_ids:
            stack_assets = _csv(_required(registry, f"render.stack.{stack_id}.assets"), f"render.stack.{stack_id}.assets")
            if any(asset not in asset_slots for asset in stack_assets):
                raise ValueError(f"render stack {stack_id} names an unknown asset")
            referenced_assets.update(stack_assets)
            mode = _required(registry, f"render.stack.{stack_id}.mode")
            if mode not in {"pack-only", "live-controls"}:
                raise ValueError(f"render stack mode is invalid for {stack_id}")
            if mode == "pack-only" and any(asset_source_modes[asset] != "legacy-upscaled" for asset in stack_assets):
                raise ValueError(f"pack-only render stack {stack_id} contains a native asset")
            if mode == "live-controls":
                if not any(asset_slots[asset] == 0 and asset_source_modes[asset] == "native"
                           for asset in stack_assets):
                    raise ValueError(f"live-controls render stack {stack_id} lacks a native slot-0 replacement")
                if any(asset_slots[asset] in {0, 5} and asset_source_modes[asset] == "legacy-upscaled"
                       for asset in stack_assets):
                    raise ValueError(f"live-controls render stack {stack_id} mixes a legacy slot-0/5 asset")
        if referenced_assets != set(asset_ids):
            raise ValueError(f"V2 pack contains assets unused by every render stack: {sorted(set(asset_ids) - referenced_assets)}")
        if set(names) != expected_entries:
            extras = sorted(set(names) - expected_entries)
            missing = sorted(expected_entries - set(names))
            raise ValueError(f"V2 archive entries disagree with registry; extra={extras}, missing={missing}")
        referenced_keys = {
            "schema", "template", "template.sha256", "template.source_v1.sha256",
            "template.derived_masks.sha256", "frame.count", *fixed, "asset.ids", "render.stack.ids",
        }
        for asset_id in asset_ids:
            prefix = f"asset.{asset_id}"
            referenced_keys.update({
                f"{prefix}.kind", f"{prefix}.paperdoll_slot", f"{prefix}.source_mode",
                f"{prefix}.propagation", f"{prefix}.channels",
            })
            for channel in _csv(registry[f"{prefix}.channels"], f"{prefix}.channels"):
                referenced_keys.add(f"{prefix}.channel.{channel}.tint")
                for offset in range(18):
                    base = f"{prefix}.frame.{offset:02d}.{channel}"
                    referenced_keys.update({f"{base}.entry", f"{base}.sha256", f"{base}.empty"})
        for stack_id in stack_ids:
            referenced_keys.update({f"render.stack.{stack_id}.assets", f"render.stack.{stack_id}.mode"})
        if set(registry) != referenced_keys:
            raise ValueError(f"registry has unknown properties: {sorted(set(registry) - referenced_keys)}")
    return {
        "valid": True,
        "archiveSha256": _sha256(path),
        "assetCount": len(asset_ids),
        "entryCount": len(expected_entries) - 1,
        "stackCount": len(stack_ids),
    }


__all__ = [
    "DecodedV2Sprite", "PACK_NAME", "PACK_SCHEMA", "V2PackResult", "decode_v2_sprite",
    "encode_properties", "encode_v2_sprite", "parse_properties", "validate_v2_pack", "write_v2_pack",
]
