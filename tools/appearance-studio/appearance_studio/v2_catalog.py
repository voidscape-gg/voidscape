from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping
import zipfile

from PIL import Image
import jsonschema
import yaml

from .client_preview import SpriteFrame, decode_sprite, load_reference_frames
from .paths import REPO_ROOT, TOOL_DIR
from .registry import safe_repo_path
from .v2_template import DEFAULT_TEMPLATE, MASTERS, PaperdollV2Template, load_v2_template


SCHEMA = "voidscape-paperdoll-v2-catalog/v1"
DEFAULT_CATALOG = REPO_ROOT / "content/appearance/v2/catalog.yaml"
SCHEMA_PATH = TOOL_DIR / "schema/paperdoll-v2-catalog.schema.json"


@dataclass(frozen=True)
class PaperdollV2Catalog:
    path: Path
    digest: str
    payload: Mapping[str, Any]
    template: PaperdollV2Template
    client_archive: Path
    server_archive: Path

    @property
    def base_profiles(self) -> tuple[Mapping[str, Any], ...]:
        return tuple(self.payload["baseProfiles"])

    @property
    def hairstyles(self) -> tuple[Mapping[str, Any], ...]:
        return tuple(self.payload["hairstyles"])


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _schema_validate(payload: Mapping[str, Any]) -> None:
    schema = json.loads(SCHEMA_PATH.read_text())
    jsonschema.Draft202012Validator.check_schema(schema)
    try:
        jsonschema.validate(payload, schema)
    except jsonschema.ValidationError as exc:
        location = ".".join(str(item) for item in exc.absolute_path) or "<root>"
        raise ValueError(f"Paperdoll V2 catalog schema error at {location}: {exc.message}") from exc


def _archive_frames(
    client_archive: Path,
    server_archive: Path,
    source: Mapping[str, Any],
) -> tuple[SpriteFrame, ...]:
    base = int(source["spriteBase"])
    expected_hashes = source["entrySha256"]
    frames: list[SpriteFrame] = []
    with zipfile.ZipFile(client_archive) as client, zipfile.ZipFile(server_archive) as server:
        for offset, expected in enumerate(expected_hashes):
            entry = str(base + offset)
            try:
                client_bytes = client.read(entry)
                server_bytes = server.read(entry)
            except KeyError as exc:
                raise ValueError(f"catalog authentic source entry {entry} is missing") from exc
            if client_bytes != server_bytes:
                raise ValueError(f"catalog authentic source entry {entry} differs between client and server")
            if hashlib.sha256(client_bytes).hexdigest() != expected:
                raise ValueError(f"catalog authentic source entry {entry} digest changed")
            frames.append(decode_sprite(client_bytes))
    return tuple(frames)


def catalog_source_frames(
    catalog: PaperdollV2Catalog,
    source: Mapping[str, Any],
) -> tuple[SpriteFrame, ...]:
    if source["type"] == "template-reference":
        reference = source["reference"]
        directory = catalog.template.source.reference_dirs.get(reference)
        if directory is None:
            raise ValueError(f"catalog names unknown template reference {reference!r}")
        frames = load_reference_frames(directory)
    elif source["type"] == "authentic-archive":
        frames = _archive_frames(catalog.client_archive, catalog.server_archive, source)
    else:  # schema validation makes this unreachable
        raise ValueError(f"unsupported catalog source type {source['type']!r}")
    if len(frames) != 18:
        raise ValueError("catalog source must contain exactly 18 frames")
    for spec, frame in zip(catalog.template.frames, frames):
        logical = int(frame.sidecar["something1"]), int(frame.sidecar["something2"])
        expected = spec.size[0] // 2, spec.size[1] // 2
        if logical != expected:
            raise ValueError(
                f"catalog source frame {spec.offset} logical geometry {logical} != locked V1 {expected}"
            )
    return frames


def style_master_paths(
    catalog: PaperdollV2Catalog,
    style: Mapping[str, Any],
) -> dict[str, Path]:
    directory = safe_repo_path(style["source"]["directory"], repo_root=REPO_ROOT)
    expected = style["source"]["masterSha256"]
    paths: dict[str, Path] = {}
    for master in MASTERS:
        path = directory / f"{master}.png"
        if not path.is_file():
            raise FileNotFoundError(f"catalog hairstyle master is missing: {path}")
        if _sha256(path) != expected[master]:
            raise ValueError(f"catalog hairstyle {style['id']} master {master} digest changed")
        frame = next(item for item in catalog.template.frames if item.master == master)
        with Image.open(path) as image:
            if image.format != "PNG" or image.mode != "RGBA" or image.size != frame.size:
                raise ValueError(
                    f"catalog hairstyle {style['id']} master {master} must be an RGBA PNG "
                    f"of size {frame.size[0]}x{frame.size[1]}"
                )
            if any(alpha and not red == green == blue for red, green, blue, alpha in image.getdata()):
                raise ValueError(f"catalog hairstyle {style['id']} master {master} is not neutral grayscale")
        paths[master] = path
    return paths


def generated_collection_contract(catalog: PaperdollV2Catalog) -> dict[str, Any]:
    assets: list[dict[str, Any]] = []
    stacks: list[dict[str, Any]] = []
    base_profiles: list[dict[str, Any]] = []
    asset_ids: set[str] = set()

    def add_asset(spec: dict[str, Any]) -> None:
        if spec["id"] in asset_ids:
            raise ValueError(f"generated catalog asset id is duplicated: {spec['id']}")
        asset_ids.add(spec["id"])
        assets.append(spec)

    for profile in catalog.base_profiles:
        profile_id = profile["id"]
        generated = {
            "controlHead": f"{profile_id}_legacy_head",
            "controlBody": f"{profile_id}_legacy_body",
            "controlLegs": f"{profile_id}_legacy_legs",
            "nativeHead": f"{profile_id}_native_head",
            "nativeBody": f"{profile_id}_native_body",
            "nativeLegs": f"{profile_id}_native_legs",
        }
        base_profiles.append({
            "id": profile_id,
            "label": profile["label"],
            "gender": profile["gender"],
            "legacyIdentity": dict(profile["legacyIdentity"]),
            "eligibility": dict(profile["eligibility"]),
            "assets": generated,
        })
        add_asset({
            "id": generated["controlHead"], "label": f"{profile['label']} legacy head control",
            "kind": "legacy-control", "paperdollSlot": 0, "sourceMode": "legacy-upscaled",
            "propagation": "explicit-frames", "channels": (("skin", "skin"), ("hair", "hair"), ("fixed", "fixed")),
            "generator": {"type": "profile-control", "profile": profile_id, "part": "head"},
        })
        add_asset({
            "id": generated["controlBody"], "label": f"{profile['label']} legacy body control",
            "kind": "legacy-control", "paperdollSlot": 1, "sourceMode": "legacy-upscaled",
            "propagation": "explicit-frames", "channels": (("skin", "skin"), ("top", "top"), ("fixed", "fixed")),
            "generator": {"type": "profile-control", "profile": profile_id, "part": "body"},
        })
        add_asset({
            "id": generated["controlLegs"], "label": f"{profile['label']} legacy legs control",
            "kind": "legacy-control", "paperdollSlot": 2, "sourceMode": "legacy-upscaled",
            "propagation": "explicit-frames", "channels": (("skin", "skin"), ("bottom", "bottom"), ("fixed", "fixed")),
            "generator": {"type": "profile-control", "profile": profile_id, "part": "legs"},
        })
        add_asset({
            "id": generated["nativeHead"], "label": f"{profile['label']} native 2x bald head",
            "kind": "head", "paperdollSlot": 0, "sourceMode": "native",
            "propagation": "rigid-head", "channels": (("skin", "skin"), ("fixed", "fixed")),
            "generator": {"type": "profile-native-head", "profile": profile_id},
        })
        add_asset({
            "id": generated["nativeBody"], "label": f"{profile['label']} native 2x body",
            "kind": "body", "paperdollSlot": 1, "sourceMode": "native",
            "propagation": "explicit-frames", "channels": (("skin", "skin"), ("top", "top"), ("fixed", "fixed")),
            "generator": {"type": "profile-native-part", "profile": profile_id, "part": "body"},
        })
        add_asset({
            "id": generated["nativeLegs"], "label": f"{profile['label']} native 2x legs",
            "kind": "legs", "paperdollSlot": 2, "sourceMode": "native",
            "propagation": "explicit-frames", "channels": (("skin", "skin"), ("bottom", "bottom"), ("fixed", "fixed")),
            "generator": {"type": "profile-native-part", "profile": profile_id, "part": "legs"},
        })

        control = f"{profile_id}_control"
        native = f"{profile_id}_native_base"
        stacks.extend([
            {"id": control, "label": f"{profile['label']} legacy-upscaled control", "mode": "pack-only",
             "assets": [generated["controlLegs"], generated["controlBody"], generated["controlHead"]]},
            {"id": native, "label": f"{profile['label']} native 2x base", "mode": "live-controls",
             "assets": [generated["nativeLegs"], generated["nativeBody"], generated["nativeHead"]]},
        ])

    for style in catalog.hairstyles:
        add_asset({
            "id": style["assetId"], "label": style["label"], "kind": "hair", "paperdollSlot": 0,
            "sourceMode": "native", "propagation": "rigid-head", "channels": (("hair", "hair"),),
            "generator": {"type": "catalog-hairstyle", "style": style["id"]},
        })
        for profile_id in style["eligibility"]["baseProfiles"]:
            profile = next(item for item in base_profiles if item["id"] == profile_id)
            stacks.append({
                "id": f"{profile_id}_{style['id']}",
                "label": f"{profile['label']} / {style['label']}",
                "mode": "live-controls",
                "assets": [profile["assets"]["nativeLegs"], profile["assets"]["nativeBody"],
                           profile["assets"]["nativeHead"], style["assetId"]],
            })

    stack_ids = {stack["id"] for stack in stacks}
    style_by_selector = {style["selectorId"]: style for style in catalog.hairstyles}
    selectors = [{
        "selectorId": 0,
        "style": "classic",
        "label": "Classic",
        "route": "legacy",
        "baseProfiles": [profile["id"] for profile in base_profiles],
        "stackByBase": {},
        "qaControlStackByBase": {
            profile["id"]: f"{profile['id']}_control" for profile in base_profiles
        },
        "eligibility": {"state": "shipping", "platforms": ["pc", "android", "teavm"], "reasons": []},
    }]
    for selector_id in sorted(style_by_selector):
        style = style_by_selector[selector_id]
        eligible = list(style["eligibility"]["baseProfiles"])
        selectors.append({
            "selectorId": selector_id,
            "style": style["id"],
            "label": style["label"],
            "route": "v2",
            "baseProfiles": eligible,
            "stackByBase": {profile_id: f"{profile_id}_{style['id']}" for profile_id in eligible},
            "qaControlStackByBase": {
                profile_id: f"{profile_id}_control" for profile_id in eligible
            },
            "eligibility": dict(style["eligibility"]),
        })

    qa_cases = [dict(case) for case in catalog.payload["qaCases"]]
    expected_qa = {
        (profile["id"], f"{profile['id']}_control", 0)
        for profile in base_profiles
    } | {
        (profile["id"], f"{profile['id']}_native_base", None)
        for profile in base_profiles
    } | {
        (profile_id, f"{profile_id}_{style['id']}", style["selectorId"])
        for style in catalog.hairstyles
        for profile_id in style["eligibility"]["baseProfiles"]
    }
    actual_qa = {(case["baseProfile"], case["stack"], case["selectorId"]) for case in qa_cases}
    if actual_qa != expected_qa or len(actual_qa) != len(qa_cases):
        raise ValueError("catalog QA cases must cover every base control/native/style exactly once")
    if [case["stack"] for case in qa_cases] != [stack["id"] for stack in stacks]:
        raise ValueError("catalog QA case order must match generated render-stack order")
    for case in qa_cases:
        if case["id"] != case["stack"]:
            raise ValueError(f"catalog QA case {case['id']} id must match its generated stack")
        if case["baseProfile"] not in {profile["id"] for profile in base_profiles}:
            raise ValueError(f"catalog QA case {case['id']} has unknown base profile")
        if case["stack"] not in stack_ids:
            raise ValueError(f"catalog QA case {case['id']} has unknown generated stack")
        if case["comparisonStack"] is not None and case["comparisonStack"] not in stack_ids:
            raise ValueError(f"catalog QA case {case['id']} has unknown comparison stack")
        if any(asset not in asset_ids for asset in case["requiredAssets"]):
            raise ValueError(f"catalog QA case {case['id']} has unknown required asset")
        if any(rule["asset"] not in asset_ids for rule in case["maskRules"]):
            raise ValueError(f"catalog QA case {case['id']} has unknown mask-rule asset")
        if case["selectorId"] == 0 and case["stack"] != f"{case['baseProfile']}_control":
            raise ValueError(f"catalog QA case {case['id']} misroutes Classic selector 0")
        if case["selectorId"] is None and case["stack"] != f"{case['baseProfile']}_native_base":
            raise ValueError(f"catalog QA case {case['id']} misroutes native base proof")
        if case["selectorId"] not in {None, 0}:
            style = style_by_selector.get(case["selectorId"])
            if style is None or case["stack"] != f"{case['baseProfile']}_{style['id']}":
                raise ValueError(f"catalog QA case {case['id']} misroutes its stable selector")

    return {
        "assets": assets,
        "renderStacks": stacks,
        "baseProfiles": base_profiles,
        "selectorRegistry": {
            "schema": "voidscape-paperdoll-v2-selector-registry/v1",
            "defaultEnabled": False,
            "namespace": dict(catalog.payload["selectorNamespace"]),
            "entries": selectors,
        },
        "hatOcclusionPolicy": dict(catalog.payload["hatOcclusionPolicy"]),
        "qaCases": qa_cases,
    }


def load_v2_catalog(path: Path = DEFAULT_CATALOG) -> PaperdollV2Catalog:
    resolved = path.resolve()
    try:
        resolved.relative_to(REPO_ROOT.resolve())
    except ValueError as exc:
        raise ValueError("Paperdoll V2 catalog must remain inside the repository") from exc
    payload = yaml.safe_load(resolved.read_text())
    if not isinstance(payload, dict):
        raise ValueError("Paperdoll V2 catalog must be a mapping")
    _schema_validate(payload)
    if payload["schema"] != SCHEMA:
        raise ValueError("unsupported Paperdoll V2 catalog")
    template_path = safe_repo_path(payload["template"]["path"], repo_root=REPO_ROOT)
    if template_path.resolve() != DEFAULT_TEMPLATE.resolve():
        raise ValueError("Paperdoll V2 catalog must use the canonical 2x template")
    if _sha256(template_path) != payload["template"]["sha256"]:
        raise ValueError("Paperdoll V2 catalog template digest changed")
    template = load_v2_template(template_path)
    archives = payload["authenticArchives"]
    client_archive = safe_repo_path(archives["client"], repo_root=REPO_ROOT)
    server_archive = safe_repo_path(archives["server"], repo_root=REPO_ROOT)
    if not client_archive.is_file() or not server_archive.is_file():
        raise FileNotFoundError("Paperdoll V2 catalog Authentic archive is missing")

    profiles = payload["baseProfiles"]
    profile_ids = [profile["id"] for profile in profiles]
    if len(profile_ids) != len(set(profile_ids)) or {profile["gender"] for profile in profiles} != {"male", "female"}:
        raise ValueError("Paperdoll V2 catalog must define unique male and female base profiles")
    catalog = PaperdollV2Catalog(
        resolved, _sha256(resolved), payload, template, client_archive, server_archive,
    )
    for profile in profiles:
        for part in ("head", "body", "legs"):
            catalog_source_frames(catalog, profile["sources"][part])

    styles = payload["hairstyles"]
    selector_ids = [style["selectorId"] for style in styles]
    style_ids = [style["id"] for style in styles]
    asset_ids = [style["assetId"] for style in styles]
    if len(selector_ids) != len(set(selector_ids)) or len(style_ids) != len(set(style_ids)) \
            or len(asset_ids) != len(set(asset_ids)):
        raise ValueError("Paperdoll V2 hairstyle ids, asset ids, and selector ids must be unique")
    if selector_ids != list(range(1, len(selector_ids) + 1)):
        raise ValueError("Paperdoll V2 hairstyle selectors must be ordered contiguous stable ids after Classic 0")
    known_profiles = set(profile_ids)
    for style in styles:
        if not set(style["eligibility"]["baseProfiles"]).issubset(known_profiles):
            raise ValueError(f"catalog hairstyle {style['id']} names an unknown base profile")
        if style["assetId"] != f"hair_{style['id']}":
            raise ValueError(f"catalog hairstyle {style['id']} must own matching hair_ asset id")
        style_master_paths(catalog, style)

    policy = payload["hatOcclusionPolicy"]
    if policy != {
        "strategy": "explicit-first-match",
        "rules": [
            {"id": "no_hat", "match": {"hatAppearanceIds": [0]}, "action": "allow-v2",
             "reason": "No headwear can occlude native hair."},
            {"id": "unsupported_hat", "match": {"anyNonzeroHat": True}, "action": "fallback-legacy",
             "reason": "Per-hat native occlusion masks are not approved yet."},
        ],
    }:
        raise ValueError("Paperdoll V2 catalog hat policy must fail closed for every nonzero hat")

    contract = generated_collection_contract(catalog)
    qa_ids = [case["id"] for case in contract["qaCases"]]
    if len(qa_ids) != len(set(qa_ids)):
        raise ValueError("Paperdoll V2 catalog QA case ids must be unique")
    return catalog


__all__ = [
    "DEFAULT_CATALOG", "PaperdollV2Catalog", "SCHEMA", "catalog_source_frames",
    "generated_collection_contract", "load_v2_catalog", "style_master_paths",
]
