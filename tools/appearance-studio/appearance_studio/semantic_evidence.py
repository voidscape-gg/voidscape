"""Deterministic, non-art contract fixtures for semantic paperdoll QA."""
from __future__ import annotations

from collections import deque
from dataclasses import replace
import hashlib
import json
from pathlib import Path
from typing import Any, Callable

from PIL import Image

from .compiler import CompileResult, CompiledFrame
from .geometry import derive_sprite
from .look import load_authoring_layer, load_locked_base_frames, load_look_manifest
from .paths import REPO_ROOT
from .semantic_qa import (
    MULLET_THRESHOLDS, require_semantic_report, validate_composed_look,
    validate_look_conflicts, validate_semantic_layer,
)
from .template import PaperdollTemplate, PoseMaskReference


BUNDLE_SCHEMA = "voidscape-semantic-evidence-bundle/v1"
MULLET_MANIFEST = REPO_ROOT / "content/appearance/proposals/future_mullet.yaml"
MUSTACHE_MANIFEST = REPO_ROOT / "content/appearance/proposals/future_mustache.yaml"
LOOK_MANIFEST = REPO_ROOT / "content/appearance/proposals/future_mullet_mustache.yaml"


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _mask(reference: PoseMaskReference | None) -> set[tuple[int, int]]:
    if reference is None:
        return set()
    with Image.open(reference.path) as image:
        return {
            (x, y) for y in range(image.height) for x in range(image.width)
            if image.getpixel((x, y))
        }


def _path(allowed: set[tuple[int, int]], starts: set[tuple[int, int]],
          targets: set[tuple[int, int]]) -> set[tuple[int, int]]:
    pending = deque(sorted(starts, key=lambda point: (point[1], point[0])))
    previous: dict[tuple[int, int], tuple[int, int] | None] = {point: None for point in pending}
    end = None
    while pending:
        point = pending.popleft()
        if point in targets:
            end = point
            break
        x, y = point
        for neighbour in (
            (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
            (x - 1, y), (x + 1, y),
            (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
        ):
            if neighbour in allowed and neighbour not in previous:
                previous[neighbour] = point
                pending.append(neighbour)
    if end is None:
        raise ValueError("locked masks have no semantic fixture path")
    output = set()
    while end is not None:
        output.add(end)
        end = previous[end]
    return output


def _valid_points(template: PaperdollTemplate, profile_name: str) -> dict[str, set[tuple[int, int]]]:
    output: dict[str, set[tuple[int, int]]] = {}
    for master, profile in template.pose_profiles.items():
        if profile_name == "mustache" and profile.landmarks["upper_lip"] is None:
            output[master] = set()
            continue
        if profile_name == "mullet":
            allowed = _mask(profile.masks["hair_allowed"])
            forbidden = set().union(*(
                _mask(profile.masks[role])
                for role in ("face_clearance", "protected_anatomy", "neck_clearance")
            ))
            valid = allowed - forbidden
            attachment = _mask(profile.masks["scalp_attachment"]) & valid
            threshold = MULLET_THRESHOLDS[master]
            selected: set[tuple[int, int]] = set()
            scalp_targets = sorted(attachment, key=lambda point: (point[1], point[0]))[:threshold["scalp"]]
            selected.add(scalp_targets[0])
            for target in scalp_targets[1:]:
                selected |= _path(valid, selected, {target})
            if profile.masks["nape_tail"] is not None:
                nape = _mask(profile.masks["nape_tail"]) & valid
                spec = next(frame for frame in template.frames if frame.master == master)
                nape_y = spec.crown[1] + profile.landmarks["nape"][1]
                deepest = max((point for point in nape if point[1] >= nape_y),
                              key=lambda point: (point[1], -point[0]))
                targets = sorted(nape, key=lambda point: (
                    abs(point[0] - deepest[0]) + abs(point[1] - deepest[1]), point[1], point[0]
                ))[:threshold["nape"]]
                if deepest not in targets:
                    targets[-1] = deepest
                for target in targets:
                    selected |= _path(valid, selected, {target})
            pending = deque(sorted(selected, key=lambda point: (point[1], point[0])))
            while len(selected) < threshold["opaque"][0] and pending:
                x, y = pending.popleft()
                for neighbour in (
                    (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
                    (x - 1, y), (x + 1, y),
                    (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
                ):
                    if neighbour in valid and neighbour not in selected:
                        selected.add(neighbour); pending.append(neighbour)
                        if len(selected) >= threshold["opaque"][0]:
                            break
            output[master] = selected
        else:
            valid = _mask(profile.masks["facial_hair_allowed"]) - _mask(profile.masks["protected_anatomy"])
            output[master] = _mask(profile.masks["upper_lip_attachment"]) & valid
    return output


def _compile(template: PaperdollTemplate,
             points_by_master: dict[str, set[tuple[int, int]]]) -> CompileResult:
    source = {}
    frames = []
    for spec in template.frames:
        source.setdefault(spec.master, spec)
        first = source[spec.master]
        dx, dy = spec.crown[0] - first.crown[0], spec.crown[1] - first.crown[1]
        canvas = Image.new("RGBA", spec.size)
        for x, y in points_by_master[spec.master]:
            canvas.putpixel((x + dx, y + dy), (132, 132, 132, 255))
        frames.append(CompiledFrame(
            spec.offset, spec.master, canvas, derive_sprite(canvas) if canvas.getbbox() else None,
        ))
    return CompileResult(tuple(frames), ())


def _mutate(result: CompileResult, template: PaperdollTemplate, master: str,
            function: Callable[[set[tuple[int, int]]], set[tuple[int, int]]]) -> CompileResult:
    source = next(spec for spec in template.frames if spec.master == master)
    frames = list(result.frames)
    for spec in (frame for frame in template.frames if frame.master == master):
        dx, dy = spec.crown[0] - source.crown[0], spec.crown[1] - source.crown[1]
        normalized = {
            (x - dx, y - dy) for y in range(frames[spec.offset].canvas.height)
            for x in range(frames[spec.offset].canvas.width)
            if frames[spec.offset].canvas.getpixel((x, y))[3] >= 128
        }
        changed = function(normalized)
        canvas = Image.new("RGBA", spec.size)
        for x, y in changed:
            canvas.putpixel((x + dx, y + dy), (132, 132, 132, 255))
        frames[spec.offset] = replace(
            frames[spec.offset], canvas=canvas,
            sprite=derive_sprite(canvas) if canvas.getbbox() else None,
        )
    return CompileResult(tuple(frames), result.findings)


def _invalid_hair(result: CompileResult, template: PaperdollTemplate,
                  valid_hair: dict[str, set[tuple[int, int]]]) -> CompileResult:
    master = "west"
    profile = template.pose_profiles[master]
    valid = _mask(profile.masks["hair_allowed"]) - set().union(*(
        _mask(profile.masks[role])
        for role in ("face_clearance", "protected_anatomy", "neck_clearance")
    ))
    nape = valid & _mask(profile.masks["nape_tail"])
    outside_nape = valid - nape
    scalp = outside_nape & _mask(profile.masks["scalp_attachment"])
    spec = next(frame for frame in template.frames if frame.master == master)
    nape_y = spec.crown[1] + profile.landmarks["nape"][1]
    connected = _path(outside_nape, scalp, {point for point in outside_nape if point[1] >= nape_y})
    deep_detached = max(nape, key=lambda point: (point[1], -point[0]))
    # The detached deep pixel must remain outside 8-neighbour contact with the
    # connected component so it cannot satisfy primary-component nape depth.
    if any(max(abs(deep_detached[0] - x), abs(deep_detached[1] - y)) <= 1 for x, y in connected):
        candidates = [point for point in nape if all(
            max(abs(point[0] - x), abs(point[1] - y)) > 1 for x, y in connected
        )]
        deep_detached = max(candidates, key=lambda point: (point[1], -point[0]))
    return _mutate(result, template, master, lambda _: connected | {deep_detached})


def _invalid_mustache(result: CompileResult, template: PaperdollTemplate,
                      valid_mustache: dict[str, set[tuple[int, int]]]) -> CompileResult:
    master = "north"
    profile = template.pose_profiles[master]
    spec = next(frame for frame in template.frames if frame.master == master)
    lip_x = spec.crown[0] + profile.landmarks["upper_lip"][0]
    points = valid_mustache[master]
    one_side = {point for point in points if point[0] < lip_x}
    one_side.add(min((point for point in points if point[0] > lip_x),
                     key=lambda point: (point[1], point[0])))
    return _mutate(result, template, master, lambda _: one_side)


def _write_report(path: Path, payload: dict[str, Any]) -> dict[str, Any]:
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n")
    return {"path": path.name, "sha256": _sha(path), "valid": payload["valid"]}


def write_semantic_evidence(template: PaperdollTemplate, output_root: Path) -> dict[str, Any]:
    output_root = output_root.resolve()
    try:
        output_root.relative_to((REPO_ROOT / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("semantic evidence must remain under repository tmp/") from exc
    output_root.mkdir(parents=True, exist_ok=True)
    mullet = load_authoring_layer(MULLET_MANIFEST)
    mustache = load_authoring_layer(MUSTACHE_MANIFEST)
    look = load_look_manifest(LOOK_MANIFEST)
    valid_hair_points = _valid_points(template, "mullet")
    valid_mustache_points = _valid_points(template, "mustache")
    hair = _compile(template, valid_hair_points)
    facial = _compile(template, valid_mustache_points)

    reports = {
        "valid-mullet": validate_semantic_layer(hair, mullet, template).to_dict(),
        "valid-mustache": validate_semantic_layer(facial, mustache, template).to_dict(),
        "valid-composed-look": validate_composed_look(
            {mullet.key: hair, mustache.key: facial}, look, template,
            load_locked_base_frames(template),
        ).to_dict(),
    }
    invalid_hair = _invalid_hair(hair, template, valid_hair_points)
    reports["invalid-detached-depth-cheat-hair"] = validate_semantic_layer(
        invalid_hair, mullet, template,
    ).to_dict()
    invalid_mustache = _invalid_mustache(facial, template, valid_mustache_points)
    reports["invalid-one-sided-mustache"] = validate_semantic_layer(
        invalid_mustache, mustache, template,
    ).to_dict()
    collision_point = next(iter(sorted(valid_mustache_points["north"], key=lambda p: (p[1], p[0]))))
    collision_hair = _mutate(hair, template, "north", lambda points: points | {collision_point})
    reports["invalid-cross-layer-collision"] = validate_look_conflicts(
        {mullet.key: collision_hair, mustache.key: facial}, look, template,
    ).to_dict()

    expected_codes = {
        "invalid-detached-depth-cheat-hair": {
            "mullet-debris", "mullet-nape-missing", "mullet-nape-depth",
        },
        "invalid-one-sided-mustache": {"mustache-both-sides"},
        "invalid-cross-layer-collision": {"cross-layer-collision", "cross-layer-occlusion"},
    }
    artifacts = {}
    rejection_proof = {}
    for name, payload in reports.items():
        actual_codes = {item["code"] for item in payload["findings"]}
        if name.startswith("valid-"):
            if not payload["valid"]:
                raise ValueError(f"synthetic positive semantic fixture {name} is invalid")
            require_semantic_report(payload)
        else:
            missing = expected_codes[name] - actual_codes
            if payload["valid"] or missing:
                raise ValueError(f"synthetic adversarial fixture {name} lacks expected failures {sorted(missing)}")
            try:
                require_semantic_report(payload)
            except ValueError as exc:
                rejection_proof[name] = {
                    "rejected": True, "error": str(exc),
                    "expectedCodes": sorted(expected_codes[name]),
                    "actualErrorCodes": sorted({
                        item["code"] for item in payload["findings"] if item["severity"] == "error"
                    }),
                }
            else:
                raise ValueError(f"require_semantic_report accepted invalid fixture {name}")
        artifacts[name] = _write_report(output_root / f"{name}.json", payload)

    summary = {
        "schema": BUNDLE_SCHEMA, "shipping": False, "syntheticContractFixturesOnly": True,
        "containsArt": False, "humanVisualApproval": False, "publishable": False,
        "template": {"path": str(template.path.relative_to(REPO_ROOT)), "sha256": template.digest},
        "artifacts": artifacts, "invalidEvidenceRejectionProof": rejection_proof,
        "boundary": "Synthetic mask-derived JSON contract fixtures only; no production pixels, images, archives, or publish inputs.",
    }
    summary_path = output_root / "summary.json"
    summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    return summary


__all__ = ["BUNDLE_SCHEMA", "write_semantic_evidence"]
