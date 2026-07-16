from __future__ import annotations

from typing import Any, Mapping

from .v2_archive import encode_properties, parse_properties


PROPERTIES_SCHEMA = "voidscape-paperdoll-v2-selector-registry-properties/v1"
PROPERTIES_NAME = "selector-registry.properties"


def selector_registry_properties_values(payload: Mapping[str, Any]) -> dict[str, str]:
    """Project the JSON registry into the strict machine-only ASCII runtime contract."""
    if payload.get("schema") != "voidscape-paperdoll-v2-selector-registry/v1":
        raise ValueError("unsupported Paperdoll V2 selector registry JSON")
    if payload.get("defaultEnabled") is not False:
        raise ValueError("Paperdoll V2 selector registry must remain default-off")
    namespace = payload["namespace"]
    profiles = payload["baseProfiles"]
    profile_ids = [profile["id"] for profile in profiles]
    if not profile_ids or len(profile_ids) != len(set(profile_ids)):
        raise ValueError("Paperdoll V2 selector registry base ids must be unique")
    entries = payload["entries"]
    selector_ids = [entry["selectorId"] for entry in entries]
    if selector_ids != list(range(len(entries))) or selector_ids[0] != namespace["classicId"]:
        raise ValueError("Paperdoll V2 selector registry ids must be ordered Classic 0 then contiguous")

    values = {
        "schema": PROPERTIES_SCHEMA,
        "default.enabled": "false",
        "catalog.path": payload["catalog"]["path"],
        "catalog.sha256": payload["catalog"]["sha256"],
        "template.sha256": payload["template"]["sha256"],
        "template.source_v1.sha256": payload["template"]["sourceV1Sha256"],
        "template.derived_masks.sha256": payload["template"]["derivedMaskTreeSha256"],
        "pack.path": payload["pack"]["path"],
        "pack.sha256": payload["pack"]["sha256"],
        "namespace.field": namespace["field"],
        "namespace.classic_id": str(namespace["classicId"]),
        "namespace.minimum": str(namespace["minimum"]),
        "namespace.maximum": str(namespace["maximum"]),
        "namespace.stability_policy": namespace["stabilityPolicy"],
        "base.ids": ",".join(profile_ids),
        "selector.ids": ",".join(str(selector_id) for selector_id in selector_ids),
        "selector.unknown.route": "legacy",
        "hat.v2_allowed_ids": "0",
        "hat.nonzero.route": "legacy",
        "hat.unknown.route": "legacy",
    }
    for profile in profiles:
        profile_id = profile["id"]
        identity = profile["legacyIdentity"]
        values[f"base.{profile_id}.gender"] = profile["gender"]
        values[f"base.{profile_id}.head_appearance_id"] = str(identity["headAppearanceId"])
        values[f"base.{profile_id}.body_appearance_id"] = str(identity["bodyAppearanceId"])
        values[f"base.{profile_id}.legs_appearance_id"] = str(identity["legsAppearanceId"])

    for entry in entries:
        selector_id = entry["selectorId"]
        prefix = f"selector.{selector_id}"
        bases = entry["baseProfiles"]
        if not bases or not set(bases).issubset(profile_ids):
            raise ValueError(f"Paperdoll V2 selector {selector_id} has invalid base profiles")
        qa_controls = entry["qaControlStackByBase"]
        if set(qa_controls) != set(bases):
            raise ValueError(f"Paperdoll V2 selector {selector_id} has incomplete QA controls")
        route = entry["route"]
        stacks = entry["stackByBase"]
        if selector_id == namespace["classicId"]:
            if route != "legacy" or stacks:
                raise ValueError("Paperdoll V2 selector 0 must route to legacy without a V2 stack")
        elif route != "v2" or set(stacks) != set(bases):
            raise ValueError(f"Paperdoll V2 selector {selector_id} must map every eligible base to V2")
        values[f"{prefix}.style"] = entry["style"]
        values[f"{prefix}.route"] = route
        values[f"{prefix}.base_profiles"] = ",".join(bases)
        values[f"{prefix}.eligibility.state"] = entry["eligibility"]["state"]
        values[f"{prefix}.eligibility.platforms"] = ",".join(entry["eligibility"]["platforms"])
        for base in bases:
            values[f"{prefix}.qa_control.{base}"] = qa_controls[base]
            if route == "v2":
                values[f"{prefix}.stack.{base}"] = stacks[base]

    policy = payload["hatOcclusionPolicy"]
    rules = policy.get("rules", [])
    if not (
        policy.get("strategy") == "explicit-first-match"
        and len(rules) == 2
        and rules[0].get("match") == {"hatAppearanceIds": [0]}
        and rules[0].get("action") == "allow-v2"
        and rules[1].get("match") == {"anyNonzeroHat": True}
        and rules[1].get("action") == "fallback-legacy"
    ):
        raise ValueError("Paperdoll V2 selector registry hat routes must fail closed")
    return values


def selector_registry_properties_bytes(payload: Mapping[str, Any]) -> bytes:
    return encode_properties(selector_registry_properties_values(payload))


def validate_selector_registry_properties(
    data: bytes,
    payload: Mapping[str, Any],
) -> Mapping[str, str]:
    actual = parse_properties(data)
    expected = selector_registry_properties_values(payload)
    if actual != expected:
        unknown = sorted(set(actual) - set(expected))
        missing = sorted(set(expected) - set(actual))
        changed = sorted(key for key in set(actual) & set(expected) if actual[key] != expected[key])
        raise ValueError(
            "Paperdoll V2 selector properties differ from their JSON/catalog/pack binding; "
            f"unknown={unknown}, missing={missing}, changed={changed}"
        )
    return actual


__all__ = [
    "PROPERTIES_NAME", "PROPERTIES_SCHEMA", "selector_registry_properties_bytes",
    "selector_registry_properties_values", "validate_selector_registry_properties",
]
