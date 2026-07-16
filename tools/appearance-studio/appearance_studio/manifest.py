from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

from .model import AppearanceEntry, Finding
from .paths import REPO_ROOT
from .registry import safe_repo_path


def _validate_look_manifest(entry: AppearanceEntry, *, repo_root: Path) -> list[Finding]:
    from .look import load_look_manifest

    assert entry.manifest is not None
    try:
        look = load_look_manifest(entry.manifest, repo_root=repo_root)
    except (OSError, ValueError) as exc:
        return [Finding("error", "manifest-schema", str(exc), str(entry.manifest))]
    findings: list[Finding] = []
    expected = {
        "key": entry.key,
        "profile": "authentic",
        "allocation.appearance_id": entry.appearance_id,
        "allocation.sprite_base": entry.sprite_base,
        "allocation.reservation_size": 27,
        "runtime.animation_name": entry.animation_name,
        "runtime.category": entry.category,
        "runtime.char_colour": entry.char_colour,
        "runtime.blue_mask": entry.blue_mask,
        "runtime.gender_model": entry.gender_model,
        "runtime.has_a": entry.has_a,
        "runtime.has_f": entry.has_f,
        "runtime.paperdoll_slot": entry.paperdoll_slot,
    }
    actual = {
        "key": look.key,
        "profile": look.profile,
        "allocation.appearance_id": look.allocation["appearance_id"],
        "allocation.sprite_base": look.allocation["sprite_base"],
        "allocation.reservation_size": look.allocation["reservation_size"],
        **{f"runtime.{key}": value for key, value in look.runtime.items()},
    }
    for field, value in expected.items():
        if actual.get(field) != value:
            findings.append(Finding(
                "error", "manifest-registry-mismatch",
                f"Look manifest {field} {actual.get(field)!r} != registry {value!r}",
                str(entry.manifest),
            ))
    expected_allocation_state = "reserved" if entry.state == "reserved" else "active"
    if look.allocation["state"] != expected_allocation_state:
        findings.append(Finding(
            "error", "manifest-registry-mismatch",
            f"Look allocation state {look.allocation['state']!r} != registry state {entry.state!r}",
            str(entry.manifest),
        ))
    if entry.frame_count != 18 or look.frame_profile != "walk-combat-18":
        findings.append(Finding(
            "error", "manifest-registry-mismatch", "Look must compile to exactly 18 legacy frames",
            str(entry.manifest),
        ))
    if entry.item_id is not None:
        findings.append(Finding(
            "error", "look-item-forbidden", "compile-time Looks cannot grant or define equipment items",
            str(entry.manifest),
        ))
    if entry.state == "reserved" and (look.publishable or look.preset["selectable"]):
        findings.append(Finding(
            "error", "reserved-look-activated",
            "reserved Look must remain non-publishable and absent from selectable presets",
            str(entry.manifest),
        ))
    return findings


def validate_manifest(entry: AppearanceEntry, *, repo_root: Path = REPO_ROOT) -> list[Finding]:
    if entry.manifest is None:
        return []
    try:
        payload = yaml.safe_load(entry.manifest.read_text())
    except (OSError, yaml.YAMLError) as exc:
        return [Finding("error", "manifest-read", str(exc), str(entry.manifest))]
    if isinstance(payload, dict) and payload.get("schema") == "voidscape-look/v1":
        if entry.kind != "look":
            return [Finding("error", "manifest-registry-mismatch", "Look manifest requires registry kind 'look'", str(entry.manifest))]
        return _validate_look_manifest(entry, repo_root=repo_root)
    if not isinstance(payload, dict) or payload.get("schema") != "voidscape-appearance/v1":
        return [Finding("error", "manifest-schema", "unsupported or missing appearance manifest schema", str(entry.manifest))]
    findings: list[Finding] = []
    expected = (("key", entry.key), ("kind", entry.kind), ("profile", "authentic"))
    for field, value in expected:
        if payload.get(field) != value:
            findings.append(Finding("error", "manifest-registry-mismatch", f"manifest {field} {payload.get(field)!r} != registry {value!r}", str(entry.manifest)))
    registry = payload.get("registry")
    if not isinstance(registry, dict):
        findings.append(Finding("error", "manifest-schema", "manifest.registry must be a mapping", str(entry.manifest)))
    else:
        for field, value in (("appearance_id", entry.appearance_id), ("sprite_base", entry.sprite_base), ("reservation_size", 27)):
            if registry.get(field) != value:
                findings.append(Finding("error", "manifest-registry-mismatch", f"manifest registry.{field} {registry.get(field)!r} != {value}", str(entry.manifest)))
    integration = payload.get("integration")
    if not isinstance(integration, dict):
        findings.append(Finding("error", "manifest-schema", "manifest.integration must be a mapping", str(entry.manifest)))
    elif entry.item_id is not None:
        item = integration.get("item")
        if not isinstance(item, dict):
            findings.append(Finding("error", "manifest-schema", "manifest.integration.item must be a mapping", str(entry.manifest)))
        else:
            for field, value in (("id", entry.item_id), ("wear_slot", entry.paperdoll_slot), ("wearable", True)):
                if item.get(field) != value:
                    findings.append(Finding("error", "manifest-registry-mismatch", f"manifest integration.item.{field} {item.get(field)!r} != {value!r}", str(entry.manifest)))
            icon_file = item.get("inventory_icon_file")
            if not isinstance(icon_file, str):
                findings.append(Finding("error", "manifest-path", "integration.item.inventory_icon_file must be a repository-relative string", str(entry.manifest)))
            else:
                try:
                    safe_repo_path(icon_file, repo_root=repo_root)
                except ValueError as exc:
                    findings.append(Finding("error", "manifest-path", str(exc), str(entry.manifest)))
    for section_name in ("legacy", "authoring"):
        section = payload.get(section_name, {})
        if not isinstance(section, dict):
            findings.append(Finding("error", "manifest-schema", f"manifest.{section_name} must be a mapping", str(entry.manifest)))
            continue
        for key, value in section.items():
            if key.endswith(("_dir", "_file", "_archive")) or key in {"frames_dir", "layer_files"}:
                values = value if isinstance(value, list) else [value]
                for raw in values:
                    if not isinstance(raw, str):
                        findings.append(Finding("error", "manifest-path", f"{section_name}.{key} must contain repository-relative strings", str(entry.manifest)))
                        continue
                    try:
                        safe_repo_path(raw, repo_root=repo_root)
                    except ValueError as exc:
                        findings.append(Finding("error", "manifest-path", str(exc), str(entry.manifest)))
    return findings
