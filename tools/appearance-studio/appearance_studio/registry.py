from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from .model import AppearanceEntry, Finding, Registry
from .paths import REPO_ROOT


SUPPORTED_PROFILE = "authentic"
VALID_STATES = {"active", "adopted", "reserved", "tombstoned"}
VALID_KINDS = {"hat", "hair", "facial-hair", "clothing", "equipment", "look", "npc"}


def _load_yaml(path: Path) -> dict[str, Any]:
    try:
        payload = yaml.safe_load(path.read_text())
    except (OSError, yaml.YAMLError) as exc:
        raise ValueError(f"could not read {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a YAML mapping")
    return payload


def safe_repo_path(raw: str, *, repo_root: Path = REPO_ROOT) -> Path:
    if not isinstance(raw, str) or not raw.strip():
        raise ValueError("path must be a non-empty string")
    path = Path(raw)
    if path.is_absolute():
        raise ValueError(f"absolute path is not allowed: {raw}")
    root = repo_root.resolve()
    resolved = (root / path).resolve()
    try:
        resolved.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"path escapes repository root: {raw}") from exc
    return resolved


def _integer(payload: dict[str, Any], key: str, context: str) -> int:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ValueError(f"{context}.{key} must be an integer")
    return value


def _entry(payload: Any, context: str, *, external: bool = False, repo_root: Path = REPO_ROOT) -> AppearanceEntry:
    if not isinstance(payload, dict):
        raise ValueError(f"{context} must be a mapping")
    key = payload.get("key")
    if not isinstance(key, str) or not key:
        raise ValueError(f"{context}.key must be a non-empty string")
    state = payload.get("state", "reserved") if external else payload.get("state")
    if state not in VALID_STATES:
        raise ValueError(f"{context}.state must be one of {sorted(VALID_STATES)}")
    kind = "npc" if external else payload.get("kind")
    if kind not in VALID_KINDS:
        raise ValueError(f"{context}.kind must be one of {sorted(VALID_KINDS)}")
    appearance_id = _integer(payload, "appearance_id", context)
    sprite_base = _integer(payload, "sprite_base", context)
    frame_count = _integer(payload, "frame_count", context)
    if appearance_id <= 0 or appearance_id > 256:
        raise ValueError(f"{context}.appearance_id must be in [1, 256]")
    if sprite_base < 0:
        raise ValueError(f"{context}.sprite_base must be non-negative")
    if frame_count not in {15, 18, 24, 27}:
        raise ValueError(f"{context}.frame_count must be one of 15, 18, 24, 27")
    animation_name = payload.get("animation_name")
    if not isinstance(animation_name, str) or not animation_name:
        raise ValueError(f"{context}.animation_name must be a non-empty string")
    category = payload.get("category", "npc" if external else None)
    if not isinstance(category, str) or not category:
        raise ValueError(f"{context}.category must be a non-empty string")
    char_colour = payload.get("char_colour", 0 if external else None)
    blue_mask = payload.get("blue_mask", 0 if external else None)
    gender_model = payload.get("gender_model", 0 if external else None)
    for field, value in (("char_colour", char_colour), ("blue_mask", blue_mask), ("gender_model", gender_model)):
        if isinstance(value, bool) or not isinstance(value, int) or value < 0:
            raise ValueError(f"{context}.{field} must be a non-negative integer")
    has_a = payload.get("has_a", frame_count >= 18 if external else None)
    has_f = payload.get("has_f", frame_count >= 24 if external else None)
    if not isinstance(has_a, bool) or not isinstance(has_f, bool):
        raise ValueError(f"{context}.has_a and has_f must be booleans")
    paperdoll_slot = payload.get("paperdoll_slot", -1 if external else None)
    if isinstance(paperdoll_slot, bool) or not isinstance(paperdoll_slot, int) or paperdoll_slot < -1 or paperdoll_slot > 11:
        raise ValueError(f"{context}.paperdoll_slot must be in [0, 11]")
    item_id = payload.get("item_id")
    if item_id is not None and (isinstance(item_id, bool) or not isinstance(item_id, int) or item_id < 0):
        raise ValueError(f"{context}.item_id must be a non-negative integer")
    manifest = None
    if payload.get("manifest") is not None:
        manifest = safe_repo_path(payload["manifest"], repo_root=repo_root)
    return AppearanceEntry(key, state, kind, appearance_id, animation_name, category, char_colour, blue_mask,
                           gender_model, has_a, has_f, paperdoll_slot, sprite_base, frame_count, item_id, manifest)


def load_registry(path: Path, *, repo_root: Path = REPO_ROOT) -> tuple[Registry | None, list[Finding]]:
    findings: list[Finding] = []
    try:
        payload = _load_yaml(path)
        if payload.get("schema") != "voidscape-appearance-registry/v1":
            raise ValueError("unsupported or missing registry schema")
        profile = payload.get("profile")
        if profile != SUPPORTED_PROFILE:
            raise ValueError(f"unsupported profile {profile!r}; Slice 1 supports only 'authentic'")
        reservation_size = _integer(payload, "reservation_size", "registry")
        if reservation_size != 27:
            raise ValueError("registry.reservation_size must be 27 for the legacy paperdoll")
        managed = payload.get("managed_namespace")
        if not isinstance(managed, dict):
            raise ValueError("registry.managed_namespace must be a mapping")
        if managed.get("status") not in {"proposed", "approved"}:
            raise ValueError("registry.managed_namespace.status must be 'proposed' or 'approved'")
        for field in ("base", "end", "reservation_count", "capacity_required"):
            _integer(managed, field, "registry.managed_namespace")
        expected_end = managed["base"] + managed["reservation_count"] * 27 - 1
        if managed["end"] != expected_end:
            raise ValueError(f"registry.managed_namespace.end must be {expected_end} for its base and reservation_count")
        if managed["capacity_required"] != managed["end"] + 1:
            raise ValueError("registry.managed_namespace.capacity_required must equal end + 1")
        if managed["base"] < 0 or managed["reservation_count"] <= 0:
            raise ValueError("registry.managed_namespace base/count must be non-negative/positive")
        entries = tuple(_entry(value, f"entries[{index}]", repo_root=repo_root) for index, value in enumerate(payload.get("entries", [])))
        external = tuple(_entry(value, f"external_reservations[{index}]", external=True, repo_root=repo_root) for index, value in enumerate(payload.get("external_reservations", [])))
        tombstones_raw = payload.get("tombstones", [])
        if not isinstance(tombstones_raw, list) or any(isinstance(v, bool) or not isinstance(v, int) for v in tombstones_raw):
            raise ValueError("registry.tombstones must be a list of integer appearance IDs")
        if any(value <= 0 or value > 256 for value in tombstones_raw):
            raise ValueError("registry.tombstones values must be in [1, 256]")
        registry = Registry(path, profile, reservation_size, managed, entries, external, tuple(tombstones_raw))
    except ValueError as exc:
        return None, [Finding("error", "registry-schema", str(exc), str(path))]

    all_entries = registry.entries + registry.external_reservations
    seen_keys: set[str] = set()
    seen_ids: dict[int, str] = {}
    for entry in all_entries:
        if entry.key in seen_keys:
            findings.append(Finding("error", "duplicate-key", f"duplicate registry key {entry.key!r}"))
        seen_keys.add(entry.key)
        if entry.appearance_id in seen_ids:
            findings.append(Finding("error", "duplicate-appearance-id", f"appearance ID {entry.appearance_id} is owned by both {seen_ids[entry.appearance_id]!r} and {entry.key!r}"))
        else:
            seen_ids[entry.appearance_id] = entry.key
        if entry.appearance_id in registry.tombstones:
            findings.append(Finding("error", "tombstone-reuse", f"{entry.key!r} reuses tombstoned appearance ID {entry.appearance_id}"))
        if entry.manifest is not None and not entry.manifest.exists():
            findings.append(Finding("error", "missing-manifest", f"manifest does not exist for {entry.key!r}", str(entry.manifest)))
    if len(set(registry.tombstones)) != len(registry.tombstones):
        findings.append(Finding("error", "duplicate-tombstone", "registry contains duplicate tombstone IDs"))
    for index, left in enumerate(all_entries):
        for right in all_entries[index + 1:]:
            if max(left.sprite_base, right.sprite_base) <= min(left.reserved_end, right.reserved_end):
                findings.append(Finding("error", "sprite-reservation-collision", f"27-slot reservations for {left.key!r} [{left.sprite_base}..{left.reserved_end}] and {right.key!r} [{right.sprite_base}..{right.reserved_end}] overlap"))
    managed_base = registry.managed_namespace["base"]
    managed_end = registry.managed_namespace["end"]
    for entry in registry.entries:
        if max(entry.sprite_base, managed_base) > min(entry.reserved_end, managed_end):
            continue
        if entry.sprite_base < managed_base or entry.reserved_end > managed_end:
            findings.append(Finding(
                "error", "managed-registry-boundary",
                f"managed reservation {entry.key!r} is not fully contained in [{managed_base}..{managed_end}]",
            ))
        elif (entry.sprite_base - managed_base) % registry.reservation_size != 0:
            findings.append(Finding(
                "error", "managed-registry-alignment",
                f"managed reservation {entry.key!r} does not start on a {registry.reservation_size}-slot boundary",
            ))
    for entry in registry.external_reservations:
        if max(entry.sprite_base, managed_base) <= min(entry.reserved_end, managed_end):
            findings.append(Finding(
                "error", "managed-registry-collision",
                f"managed namespace overlaps external reservation {entry.key!r}",
            ))
    return registry, findings
