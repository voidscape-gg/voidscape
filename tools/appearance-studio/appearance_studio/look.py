"""Manifest and compile-time support for legacy-compatible bundled Looks.

A Look is an authoring bundle, not a new runtime layer.  Slice 5 composes a
locked head plus ordered hair/facial-hair layers into one ordinary 18-frame
head block.  Allocation advice remains read-only until the Gate 5 reservation
and art approval are recorded elsewhere.
"""
from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

import jsonschema
from PIL import Image
import yaml

from .compiler import CompileResult, compose_look
from .geometry import DerivedSprite, derive_sprite, expand_crop
from .model import Registry
from .paths import REPO_ROOT, TOOL_DIR
from .registry import safe_repo_path
from .template import PaperdollTemplate
from .audit import numeric_members
from .paths import CLIENT_ARCHIVE, SERVER_ARCHIVE


LOOK_SCHEMA = "voidscape-look/v1"
LAYER_SCHEMA = "voidscape-authoring-layer/v2"
LEGACY_APPEARANCE_ID_FLOOR = 246
MASTER_NAMES = frozenset({"north", "north-west", "west", "south-west", "south", "combat-west"})
SEMANTIC_PROFILES = {
    "mullet": ("hair", frozenset({"scalp_attachment", "nape_tail"})),
    "mustache": ("facial-hair", frozenset({"upper_lip_attachment"})),
}


@dataclass(frozen=True)
class AuthoringLayerManifest:
    path: Path
    key: str
    kind: str
    status: str
    template: str
    masters: tuple[str, ...]
    hidden_masters: tuple[str, ...]
    propagation: str
    semantic_profile: str
    required_masks: tuple[str, ...]
    palette_policy: str
    art: dict[str, Any] | None


@dataclass(frozen=True)
class LookLayer:
    key: str
    kind: str
    manifest_path: Path
    order: int
    manifest: AuthoringLayerManifest


@dataclass(frozen=True)
class LookManifest:
    path: Path
    key: str
    name: str
    status: str
    profile: str
    template: str
    compile_target: str
    frame_profile: str
    base_key: str
    layers: tuple[LookLayer, ...]
    allocation: dict[str, Any]
    runtime: dict[str, Any]
    compatibility: dict[str, Any]
    preset: dict[str, Any]
    art: dict[str, Any] | None
    approval: dict[str, bool]

    @property
    def publishable(self) -> bool:
        return (
            self.status in {"approved", "active"}
            and self.allocation["state"] in {"reserved", "active"}
            and self.art is not None
            and all(
                self.approval.get(key, False)
                for key in (
                    "allocation_approved", "art_brief_approved",
                    "production_pixels_approved", "studio_approved",
                )
            )
            and all(layer.manifest.status in {"approved", "active"} and layer.manifest.art is not None for layer in self.layers)
        )


@dataclass(frozen=True)
class CompiledLookFrame:
    offset: int
    canvas: Image.Image
    sprite: DerivedSprite


@dataclass(frozen=True)
class CompiledLook:
    key: str
    frames: tuple[CompiledLookFrame, ...]
    layer_order: tuple[str, ...]


def _read_yaml(path: Path) -> dict[str, Any]:
    try:
        payload = yaml.safe_load(path.read_text())
    except (OSError, yaml.YAMLError) as exc:
        raise ValueError(f"could not read {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a YAML mapping")
    return payload


def _schema(name: str) -> dict[str, Any]:
    return json.loads((TOOL_DIR / "schema" / name).read_text())


def _validate_schema(payload: dict[str, Any], name: str, path: Path) -> None:
    try:
        jsonschema.Draft202012Validator(_schema(name)).validate(payload)
    except jsonschema.ValidationError as exc:
        location = ".".join(str(value) for value in exc.absolute_path)
        suffix = f" at {location}" if location else ""
        raise ValueError(f"invalid {path.name}{suffix}: {exc.message}") from exc


def load_authoring_layer(path: Path, *, repo_root: Path = REPO_ROOT) -> AuthoringLayerManifest:
    payload = _read_yaml(path)
    _validate_schema(payload, "authoring-layer.schema.json", path)
    masters = tuple(payload["masters"])
    if len(masters) != len(set(masters)) or set(masters) != MASTER_NAMES:
        raise ValueError(f"{path.name} must declare the six rsc-player-v1 masters exactly once")
    art = payload["art"]
    if art is not None:
        safe_repo_path(art["masters_dir"], repo_root=repo_root)
    if payload["status"] in {"approved", "active"} and art is None:
        raise ValueError(f"{path.name} cannot be {payload['status']} without approved master art")
    hidden_masters = tuple(payload.get("hidden_masters", ()))
    if not set(hidden_masters) <= set(masters):
        raise ValueError(f"{path.name} hidden masters must be declared masters")
    semantic_profile = payload["semantic_profile"]
    expected_kind, expected_landmarks = SEMANTIC_PROFILES[semantic_profile]
    required_masks = tuple(payload["required_masks"])
    if payload["kind"] != expected_kind:
        raise ValueError(f"{semantic_profile} semantic profile requires kind {expected_kind}")
    if set(required_masks) != expected_landmarks:
        raise ValueError(
            f"{semantic_profile} semantic profile required masks must be exactly "
            f"{sorted(expected_landmarks)}"
        )
    if semantic_profile == "mustache" and not {"south-west", "south"} <= set(hidden_masters):
        raise ValueError("mustache semantic profile must hide the rear masters south-west and south")
    return AuthoringLayerManifest(
        path.resolve(), payload["key"], payload["kind"], payload["status"], payload["template"],
        masters, hidden_masters, payload["propagation"], semantic_profile, required_masks,
        payload["palette_policy"], art,
    )


def load_look_manifest(path: Path, *, repo_root: Path = REPO_ROOT) -> LookManifest:
    payload = _read_yaml(path)
    _validate_schema(payload, "look.schema.json", path)
    layers: list[LookLayer] = []
    keys: set[str] = set()
    orders: set[int] = set()
    for item in payload["layers"]:
        if item["key"] in keys:
            raise ValueError(f"duplicate Look layer key {item['key']!r}")
        if item["order"] in orders:
            raise ValueError(f"duplicate Look layer order {item['order']}")
        keys.add(item["key"])
        orders.add(item["order"])
        layer_path = safe_repo_path(item["manifest"], repo_root=repo_root)
        layer = load_authoring_layer(layer_path, repo_root=repo_root)
        if layer.key != item["key"] or layer.kind != item["kind"]:
            raise ValueError(f"Look layer {item['key']!r} does not match its authoring manifest")
        if layer.template != payload["template"]:
            raise ValueError(f"Look layer {item['key']!r} uses a different paperdoll template")
        layers.append(LookLayer(item["key"], item["kind"], layer_path, item["order"], layer))
    layers.sort(key=lambda layer: (layer.order, layer.key))

    allocation = dict(payload["allocation"])
    art = payload["art"]
    approval = dict(payload.get("approval", {}))
    approvals_complete = all(
        approval.get(key, False)
        for key in (
            "allocation_approved", "art_brief_approved",
            "production_pixels_approved", "studio_approved",
        )
    )
    if payload["status"] in {"approved", "active"}:
        if allocation["state"] not in {"reserved", "active"} or art is None or not approvals_complete:
            raise ValueError("an approved/active Look requires allocation, art, production-pixel, and Studio approvals")
    if payload["status"] == "active" and not payload["preset"]["selectable"]:
        raise ValueError("an active Look must be selectable")
    if payload["preset"]["selectable"] and payload["status"] != "active":
        raise ValueError("only an active Look may enter selectable presets")
    return LookManifest(
        path.resolve(), payload["key"], payload["name"], payload["status"], payload["profile"],
        payload["template"], payload["compile_target"], payload["frame_profile"], payload["base"]["key"],
        tuple(layers), allocation, dict(payload["runtime"]), dict(payload["compatibility"]),
        dict(payload["preset"]), art, approval,
    )


def load_locked_base_frames(template: PaperdollTemplate) -> tuple[Image.Image, ...]:
    """Load the digest-locked bald-head reference onto full logical canvases."""
    frames: list[Image.Image] = []
    for frame in template.frames:
        png = template.reference_dir / f"frame_{frame.offset:02d}.png"
        sidecar = template.reference_dir / f"frame_{frame.offset:02d}.png.json"
        try:
            image = Image.open(png).convert("RGBA")
            metadata = json.loads(sidecar.read_text())
        except (OSError, json.JSONDecodeError) as exc:
            raise ValueError(f"could not load locked base frame {frame.offset}: {exc}") from exc
        canvas = expand_crop(image, metadata)
        if canvas.size != frame.size:
            raise ValueError(f"locked base frame {frame.offset} has logical size {canvas.size}, expected {frame.size}")
        frames.append(canvas)
    return tuple(frames)


def compile_look(
    look: LookManifest,
    template: PaperdollTemplate,
    layers: Mapping[str, CompileResult],
    *,
    base_frames: Sequence[Image.Image] | None = None,
) -> CompiledLook:
    """Compose validated component results; this function performs no writes."""
    if look.template != template.key or look.compile_target != "one-legacy-head-block":
        raise ValueError("Look and compiler template/target do not match")
    expected = {layer.key for layer in look.layers}
    if set(layers) != expected:
        raise ValueError(f"compiled layers must be exactly {sorted(expected)}")
    ordered: list[CompileResult] = []
    for layer in look.layers:
        result = layers[layer.key]
        if result.findings:
            raise ValueError(f"layer {layer.key!r} has validation findings: {'; '.join(result.findings)}")
        ordered.append(result)
    base = tuple(base_frames) if base_frames is not None else load_locked_base_frames(template)
    if len(base) != len(template.frames):
        raise ValueError("Look base must contain exactly 18 frames")
    for frame, image in zip(template.frames, base):
        if image.size != frame.size:
            raise ValueError(f"Look base frame {frame.offset} size {image.size} != {frame.size}")
    canvases = compose_look(list(base), ordered)
    compiled = tuple(CompiledLookFrame(offset, canvas, derive_sprite(canvas)) for offset, canvas in enumerate(canvases))
    return CompiledLook(look.key, compiled, tuple(layer.key for layer in look.layers))


def recommend_look_allocation(
    look: LookManifest,
    registry: Registry,
    *,
    occupied_sprite_indices: set[int] | None = None,
) -> dict[str, Any]:
    """Return deterministic Gate 5 advice without mutating the registry."""
    if look.profile != registry.profile:
        raise ValueError("Look and registry profiles do not match")
    all_entries = registry.entries + registry.external_reservations
    archive_occupied = occupied_sprite_indices
    if archive_occupied is None:
        archive_occupied = numeric_members(CLIENT_ARCHIVE) | numeric_members(SERVER_ARCHIVE)
    owner = next((entry for entry in registry.entries if entry.key == look.key), None)
    if owner is not None:
        next_id = owner.appearance_id
        candidate_base = owner.sprite_base
        if look.allocation["appearance_id"] != next_id or look.allocation["sprite_base"] != candidate_base:
            raise ValueError("Look allocation does not match its registry reservation")
        if owner.state == "reserved" and set(range(candidate_base, owner.reserved_end + 1)) & archive_occupied:
            raise ValueError("reserved Look block unexpectedly contains archive entries before art activation")
        allocation_state = owner.state
        blockers = ["production-art-not-approved", "art-activation-approval"]
        manifest_matches = look.allocation["state"] == owner.state
    else:
        used_ids = {entry.appearance_id for entry in all_entries} | set(registry.tombstones)
        next_id = max({LEGACY_APPEARANCE_ID_FLOOR, *used_ids}) + 1
        if next_id > 256:
            raise ValueError("no append-only character-appearance IDs remain in the one-byte selector")
        occupied = [(entry.sprite_base, entry.reserved_end) for entry in all_entries]
        base = registry.managed_namespace["base"]
        candidate_base = None
        for index in range(registry.managed_namespace["reservation_count"]):
            value = base + index * registry.reservation_size
            end = value + registry.reservation_size - 1
            if (
                all(end < start or value > occupied_end for start, occupied_end in occupied)
                and not set(range(value, end + 1)) & archive_occupied
            ):
                candidate_base = value
                break
        if candidate_base is None:
            raise ValueError("the approved managed sprite namespace has no unowned 27-slot block")
        allocation_state = "advisory-only"
        blockers = [
            "gate-5-allocation-approval",
            "production-art-not-approved",
            "registry-reservation-missing",
        ]
        manifest_matches = (
            look.allocation["state"] == "proposed"
            and look.allocation["appearance_id"] == next_id
            and look.allocation["sprite_base"] == candidate_base
        )

    return {
        "allocation_state": allocation_state,
        "archive_occupancy_verified": True,
        "blockers": blockers,
        "contracts": {
            "avatar_mapping": True,
            "character_designer_head": True,
            "equipment_grants": False,
            "frame_count": 18,
            "legacy_head_block": True,
            "retro_fallback_required": True,
            "workbench_states": 30,
        },
        "compatibility": dict(look.compatibility),
        "key": look.key,
        "profile": registry.profile,
        "proposal": {
            "appearance_id": next_id,
            "reservation": [candidate_base, candidate_base + registry.reservation_size - 1],
            "sprite_base": candidate_base,
        },
        "manifest_proposal_matches": manifest_matches,
        "publishable": False,
        "schema": "voidscape-look-allocation-advice/v1",
        "writes": [],
    }


__all__ = [
    "AuthoringLayerManifest",
    "CompiledLook",
    "LookManifest",
    "compile_look",
    "load_authoring_layer",
    "load_locked_base_frames",
    "load_look_manifest",
    "recommend_look_allocation",
]
