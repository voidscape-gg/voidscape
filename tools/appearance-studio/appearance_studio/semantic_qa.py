from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping

from PIL import Image

from .compiler import CompileResult, compose_look
from .geometry import derive_sprite, runtime_mirror
from .look import (
    AuthoringLayerManifest, LookManifest, load_authoring_layer, load_locked_base_frames, load_look_manifest,
)
from .paths import REPO_ROOT
from .registry import safe_repo_path
from .template import PaperdollTemplate, PoseMaskReference, load_template
from .workbench_report import expected_states


REPORT_SCHEMA = "voidscape-semantic-art-qa/v2"
CONTRACT = "voidscape-rigid-head-semantics/v1"
MULLET_THRESHOLDS = {
    "north": {"opaque": (45, 111), "scalp": 6, "nape": 0},
    "north-west": {"opaque": (54, 147), "scalp": 7, "nape": 4},
    "west": {"opaque": (53, 157), "scalp": 7, "nape": 6},
    "south-west": {"opaque": (102, 332), "scalp": 12, "nape": 20},
    "south": {"opaque": (106, 375), "scalp": 12, "nape": 25},
    "combat-west": {"opaque": (51, 160), "scalp": 7, "nape": 6},
}
MUSTACHE_THRESHOLDS = {
    "north": {"contact": 3, "span": 5},
    "north-west": {"contact": 3, "span": 5},
    "west": {"contact": 4, "span": 4},
    "combat-west": {"contact": 4, "span": 4},
}
THRESHOLD_CONTRACT = {
    "mullet": {"poses": MULLET_THRESHOLDS, "primarySharePercent": 90,
               "debrisPixels": 1, "connectivity": 8},
    "mustache": {"poses": MUSTACHE_THRESHOLDS, "lobeCount": (1, 2),
                  "minimumLobePixels": 2, "sidePixels": 2,
                  "envelopeDilation": (2, 1), "connectivity": 8},
    "look-conflicts": {"crossLayerOverlap": 0},
    "composed-look": {"crossLayerOverlap": 0, "baseChangedOutsidePixels": 0,
                      "occludedPixels": 0},
}


@dataclass(frozen=True)
class SemanticFinding:
    severity: str
    code: str
    layer: str
    master: str | None
    offset: int | None
    details: Mapping[str, int | str]

    def to_dict(self) -> dict[str, Any]:
        return {
            "severity": self.severity, "code": self.code, "layer": self.layer, "master": self.master,
            "offset": self.offset, "details": dict(sorted(self.details.items())),
        }


@dataclass(frozen=True)
class SemanticQaReport:
    semantic_profile: str
    findings: tuple[SemanticFinding, ...]
    metrics: tuple[Mapping[str, Any], ...]
    runtime_metrics: tuple[Mapping[str, Any], ...]
    provenance: Mapping[str, Any]

    @property
    def valid(self) -> bool:
        return not any(finding.severity == "error" for finding in self.findings)

    def to_dict(self) -> dict[str, Any]:
        return {
            "schema": REPORT_SCHEMA,
            "contract": CONTRACT,
            "semanticProfile": self.semantic_profile,
            "shipping": False,
            "automatedContractOnly": True,
            "humanVisualApproval": False,
            "thresholds": json.loads(json.dumps(THRESHOLD_CONTRACT[self.semantic_profile])),
            "valid": self.valid,
            "findings": [finding.to_dict() for finding in self.findings],
            "metrics": [dict(metric) for metric in self.metrics],
            "runtimeMetrics": [dict(metric) for metric in self.runtime_metrics],
            "provenance": dict(self.provenance),
        }


def _digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _provenance(template: PaperdollTemplate, manifest_path: Path, *, manifest_key: str,
                manifest_type: str, look_layers=(), base_frames=()) -> dict[str, Any]:
    result = {
        "templatePath": str(template.path.relative_to(REPO_ROOT)),
        "templateSha256": template.digest,
        "referenceDigests": dict(sorted(template.reference_digests.items())),
        "maskDigests": {
            master: {
                role: None if reference is None else reference.sha256
                for role, reference in sorted(profile.masks.items())
            }
            for master, profile in sorted(template.pose_profiles.items())
        },
        "manifestPath": str(manifest_path.relative_to(REPO_ROOT)),
        "manifestSha256": _digest(manifest_path),
        "manifestKey": manifest_key,
        "manifestType": manifest_type,
        "layerManifestDigests": {
            layer.key: _digest(layer.manifest_path) for layer in look_layers
        },
        "layerManifestPaths": {
            layer.key: str(layer.manifest_path.relative_to(REPO_ROOT)) for layer in look_layers
        },
    }
    if base_frames:
        result["baseFrameDigests"] = [hashlib.sha256(frame.convert("RGBA").tobytes()).hexdigest()
                                      for frame in base_frames]
    return result


def _meets_primary_share(primary_pixels: int, opaque_pixels: int) -> bool:
    return opaque_pixels > 0 and primary_pixels * 100 >= opaque_pixels * 90


def _opaque(image: Image.Image) -> set[tuple[int, int]]:
    rgba = image.convert("RGBA")
    return {
        (x, y) for y in range(rgba.height) for x in range(rgba.width)
        if rgba.getpixel((x, y))[3] >= 128
    }


def _mask(reference: PoseMaskReference | None) -> set[tuple[int, int]]:
    if reference is None:
        return set()
    with Image.open(reference.path) as image:
        return {
            (x, y) for y in range(image.height) for x in range(image.width)
            if image.getpixel((x, y))
        }


def _translate(points: set[tuple[int, int]], dx: int, dy: int) -> set[tuple[int, int]]:
    return {(x + dx, y + dy) for x, y in points}


def _components(points: set[tuple[int, int]]) -> list[set[tuple[int, int]]]:
    remaining = set(points)
    output = []
    while remaining:
        pending = [min(remaining, key=lambda point: (point[1], point[0]))]
        remaining.remove(pending[0])
        component = set()
        while pending:
            point = pending.pop()
            component.add(point)
            x, y = point
            for neighbour in (
                (x - 1, y - 1), (x, y - 1), (x + 1, y - 1),
                (x - 1, y), (x + 1, y),
                (x - 1, y + 1), (x, y + 1), (x + 1, y + 1),
            ):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    pending.append(neighbour)
        output.append(component)
    return sorted(output, key=lambda value: (-len(value), min(y for _, y in value), min(x for x, _ in value)))


def _finding(code: str, layer: str, master: str | None, offset: int | None,
             *, severity: str = "error", **details) -> SemanticFinding:
    return SemanticFinding(severity, code, layer, master, offset, details)


def _sort_findings(findings: list[SemanticFinding]) -> tuple[SemanticFinding, ...]:
    return tuple(sorted(findings, key=lambda item: (
        item.severity, item.code, item.layer, item.master or "", -1 if item.offset is None else item.offset,
        json.dumps(dict(item.details), sort_keys=True),
    )))


def _runtime_mirror_metrics(frames_by_offset: Mapping[int, Image.Image], layer: str,
                            findings: list[SemanticFinding]) -> tuple[Mapping[str, Any], ...]:
    metrics = []
    for state in expected_states():
        source = frames_by_offset[state["spriteOffset"]]
        rendered = runtime_mirror(source) if state["mirrorX"] else source.copy()
        source_box = source.getbbox()
        expected_box = None if source_box is None else (
            source.width - source_box[2], source_box[1], source.width - source_box[0], source_box[3]
        ) if state["mirrorX"] else source_box
        box = rendered.getbbox()
        sprite = derive_sprite(rendered) if box else None
        expected_x_shift = None if expected_box is None else expected_box[0]
        if box != expected_box or (sprite is not None and sprite.sidecar["xShift"] != expected_x_shift):
            findings.append(_finding("runtime-mirror-crop-mismatch", layer, None, state["spriteOffset"],
                                     direction=state["direction"], frame=state["frame"]))
        if state["mirrorX"] and runtime_mirror(rendered).tobytes() != source.tobytes():
            findings.append(_finding("runtime-mirror-mismatch", layer, None, state["spriteOffset"],
                                     direction=state["direction"], frame=state["frame"]))
        metrics.append({
            **state,
            "bbox": None if box is None else list(box),
            "opaquePixels": len(_opaque(rendered)),
            "cropXShift": None if sprite is None else sprite.sidecar["xShift"],
            "cropYShift": None if sprite is None else sprite.sidecar["yShift"],
            "cropWidth": None if sprite is None else sprite.image.width,
            "cropHeight": None if sprite is None else sprite.image.height,
        })
    return tuple(metrics)


def validate_semantic_layer(
    result: CompileResult, layer: AuthoringLayerManifest, template: PaperdollTemplate,
) -> SemanticQaReport:
    if layer.propagation != "rigid-head" or layer.semantic_profile not in {"mullet", "mustache"}:
        raise ValueError("semantic QA v2 supports only rigid-head mullet and mustache profiles")
    if len(result.frames) != 18:
        raise ValueError("semantic QA v2 requires exactly 18 compiled frames")
    by_offset = {frame.offset: frame for frame in result.frames}
    if set(by_offset) != set(range(18)):
        raise ValueError("semantic QA v2 requires offsets 0..17 exactly once")
    hidden = set(layer.hidden_masters)
    findings: list[SemanticFinding] = [
        _finding("compiler-finding", layer.key, None, None, message=message)
        for message in result.findings
    ]
    metrics: list[Mapping[str, Any]] = []
    source_by_master = {}
    normalized_by_master: dict[str, set[tuple[int, int]]] = {}
    for spec in template.frames:
        source_by_master.setdefault(spec.master, spec)
        frame = by_offset[spec.offset]
        if frame.master != spec.master or frame.canvas.size != spec.size:
            findings.append(_finding("frame-geometry-mismatch", layer.key, spec.master, spec.offset))
            continue
        points = _opaque(frame.canvas)
        source = source_by_master[spec.master]
        dx, dy = spec.crown[0] - source.crown[0], spec.crown[1] - source.crown[1]
        normalized = {(x - dx, y - dy) for x, y in points}
        baseline = normalized_by_master.setdefault(spec.master, normalized)
        if normalized != baseline:
            findings.append(_finding("propagation-flicker", layer.key, spec.master, spec.offset,
                                     changedPixels=len(normalized ^ baseline)))
        profile = template.pose_profiles[spec.master]
        is_hidden = spec.master in hidden
        if is_hidden:
            if points:
                findings.append(_finding("hidden-master-visible", layer.key, spec.master, spec.offset,
                                         opaquePixels=len(points)))
            metrics.append({"offset": spec.offset, "master": spec.master, "hidden": True,
                            "opaquePixels": len(points), "componentCount": len(_components(points))})
            continue
        if not points:
            findings.append(_finding("layer-empty", layer.key, spec.master, spec.offset))
            metrics.append({"offset": spec.offset, "master": spec.master, "hidden": False,
                            "opaquePixels": 0, "componentCount": 0})
            continue

        if layer.kind == "hair":
            allowed_role, attachment_role = "hair_allowed", "scalp_attachment"
            forbidden_roles = ("face_clearance", "protected_anatomy", "neck_clearance")
        else:
            allowed_role, attachment_role = "facial_hair_allowed", "upper_lip_attachment"
            forbidden_roles = ("protected_anatomy",)
        allowed = _translate(_mask(profile.masks[allowed_role]), dx, dy)
        attachment = _translate(_mask(profile.masks[attachment_role]), dx, dy)
        forbidden = set().union(*(
            _translate(_mask(profile.masks[role]), dx, dy) for role in forbidden_roles
        ))
        outside = points - allowed
        blocked = points & forbidden
        if outside:
            findings.append(_finding("allowed-containment", layer.key, spec.master, spec.offset,
                                     outsidePixels=len(outside)))
        if blocked:
            findings.append(_finding("forbidden-overlap", layer.key, spec.master, spec.offset,
                                     forbiddenPixels=len(blocked)))
        components = _components(points)
        primary = components[0]
        detached = []

        nape_pixels = 0
        landmark_distance = -1
        center_delta_twice = 0
        primary_pixels = len(primary)
        span = left_pixels = right_pixels = envelope_outside = 0
        chin_overlap = upper_intrusion = lower_intrusion = 0
        forward = rearward = -1
        if layer.semantic_profile == "mullet":
            if not primary & attachment:
                findings.append(_finding("attachment-disconnected", layer.key, spec.master, spec.offset,
                                         detachedComponents=1, detachedPixels=len(primary)))
            debris = [component for component in components[1:] if len(component) == 1]
            if debris:
                findings.append(_finding("mullet-debris", layer.key, spec.master, spec.offset,
                                         components=len(debris), pixels=len(debris)))
            accents = [component for component in components[1:] if len(component) >= 2]
            if accents:
                findings.append(_finding("mullet-accent-components", layer.key, spec.master, spec.offset,
                                         severity="warning", components=len(accents),
                                         pixels=sum(map(len, accents))))
            if not _meets_primary_share(len(primary), len(points)):
                findings.append(_finding("mullet-primary-share", layer.key, spec.master, spec.offset,
                                         primaryPixels=len(primary), opaquePixels=len(points), minimumPercent=90))
            thresholds = MULLET_THRESHOLDS[spec.master]
            minimum, maximum = thresholds["opaque"]
            if not minimum <= len(points) <= maximum:
                findings.append(_finding("mullet-opaque-range", layer.key, spec.master, spec.offset,
                                         opaquePixels=len(points), minimum=minimum, maximum=maximum))
            scalp_pixels = len(primary & attachment)
            if scalp_pixels < thresholds["scalp"]:
                findings.append(_finding("mullet-scalp-contact", layer.key, spec.master, spec.offset,
                                         contactPixels=scalp_pixels, minimum=thresholds["scalp"]))
            nape = (_translate(_mask(profile.masks["nape_tail"]), dx, dy) & allowed) - forbidden
            nape_points = primary & nape
            nape_pixels = len(nape_points)
            if profile.masks["nape_tail"] is not None and nape_pixels < thresholds["nape"]:
                findings.append(_finding("mullet-nape-missing", layer.key, spec.master, spec.offset,
                                         napePixels=nape_pixels, minimum=thresholds["nape"]))
                findings.append(_finding("mullet-helmet-like", layer.key, spec.master, spec.offset,
                                         severity="warning",
                                         verticalSpan=max(y for _, y in points) - min(y for _, y in points) + 1))
            relative_nape = profile.landmarks["nape"]
            if profile.masks["nape_tail"] is not None and relative_nape is not None:
                nape_y = spec.crown[1] + relative_nape[1]
                deepest = max((y for _, y in nape_points), default=-1)
                landmark_distance = deepest - nape_y if deepest >= 0 else -1
                if deepest < nape_y:
                    findings.append(_finding("mullet-nape-depth", layer.key, spec.master, spec.offset,
                                             deepestY=deepest, requiredY=nape_y))
        else:
            if not 1 <= len(components) <= 2:
                findings.append(_finding("mustache-lobe-count", layer.key, spec.master, spec.offset,
                                         lobes=len(components), minimum=1, maximum=2))
            small_lobes = [component for component in components if len(component) < 2]
            if small_lobes:
                findings.append(_finding("mustache-lobe-too-small", layer.key, spec.master, spec.offset,
                                         lobes=len(small_lobes), pixels=sum(map(len, small_lobes))))
            detached = [component for component in components if not component & attachment]
            if detached:
                findings.append(_finding("attachment-disconnected", layer.key, spec.master, spec.offset,
                                         detachedComponents=len(detached), detachedPixels=sum(map(len, detached))))
            relative_lip = profile.landmarks["upper_lip"]
            if relative_lip is None:
                findings.append(_finding("mustache-visible-without-upper-lip", layer.key, spec.master, spec.offset))
            else:
                lip = spec.crown[0] + relative_lip[0], spec.crown[1] + relative_lip[1]
                attachment_source = _mask(profile.masks["upper_lip_attachment"])
                envelope_source = {
                    (x + offset_x, y + offset_y)
                    for x, y in attachment_source
                    for offset_y in range(-1, 2)
                    for offset_x in range(-2, 3)
                }
                envelope = _translate(envelope_source, dx, dy) & allowed - forbidden
                chin = profile.landmarks["chin"]
                if chin is not None:
                    chin_y = spec.crown[1] + chin[1]
                    envelope = {(x, y) for x, y in envelope if y < chin_y}
                envelope_escape = points - envelope
                envelope_outside = len(envelope_escape)
                if envelope_escape:
                    findings.append(_finding("mustache-envelope", layer.key, spec.master, spec.offset,
                                             outsidePixels=len(envelope_escape)))
                landmark_distance = min(max(abs(x - lip[0]), abs(y - lip[1])) for x, y in points)
                if landmark_distance > 1:
                    findings.append(_finding("mustache-upper-lip-not-adjacent", layer.key, spec.master, spec.offset,
                                             distance=landmark_distance))
                left, right = min(x for x, _ in points), max(x for x, _ in points)
                thresholds = MUSTACHE_THRESHOLDS[spec.master]
                contact_pixels = len(points & attachment)
                if contact_pixels < thresholds["contact"]:
                    findings.append(_finding("mustache-lip-contact", layer.key, spec.master, spec.offset,
                                             contactPixels=contact_pixels, minimum=thresholds["contact"]))
                span = right - left + 1
                if span < thresholds["span"]:
                    findings.append(_finding("mustache-span", layer.key, spec.master, spec.offset,
                                             span=span, minimum=thresholds["span"]))
                center_delta_twice = left + right - 2 * lip[0]
                if abs(center_delta_twice) > 4:
                    findings.append(_finding("mustache-off-center", layer.key, spec.master, spec.offset,
                                             centerDeltaTwice=center_delta_twice))
                if profile.visual_pose in {"front", "three-quarter-front"}:
                    left_pixels = sum(x < lip[0] for x, _ in points)
                    right_pixels = sum(x > lip[0] for x, _ in points)
                    if left_pixels < 2 or right_pixels < 2:
                        findings.append(_finding("mustache-both-sides", layer.key, spec.master, spec.offset,
                                                 leftPixels=left_pixels, rightPixels=right_pixels, minimum=2))
                if profile.visual_pose in {"profile", "combat-profile"}:
                    nose = profile.landmarks["nose_tip"]
                    sign = 1 if nose is not None and nose[0] > relative_lip[0] else -1
                    forward = max(sign * (x - lip[0]) for x, _ in points)
                    rearward = max(-sign * (x - lip[0]) for x, _ in points)
                    if forward < rearward:
                        findings.append(_finding("mustache-wrong-profile-side", layer.key, spec.master, spec.offset,
                                                 forwardReach=forward, rearwardReach=rearward))
                if chin is not None:
                    chin_y = spec.crown[1] + chin[1]
                    chin_overlap = sum(y >= chin_y for _, y in points)
                    if chin_overlap:
                        findings.append(_finding("mustache-chin-overlap", layer.key, spec.master, spec.offset,
                                                 pixels=chin_overlap))
                upper_intrusion = sum(y < lip[1] - 2 for _, y in points)
                lower_intrusion = sum(y > lip[1] + 2 for _, y in points)
                if upper_intrusion:
                    findings.append(_finding("mustache-nose-region-overlap", layer.key, spec.master, spec.offset,
                                             pixels=upper_intrusion))
                if lower_intrusion:
                    findings.append(_finding("mustache-mouth-region-overlap", layer.key, spec.master, spec.offset,
                                             pixels=lower_intrusion))

        mirrored = runtime_mirror(frame.canvas)
        if runtime_mirror(mirrored).tobytes() != frame.canvas.tobytes() or len(_opaque(mirrored)) != len(points):
            findings.append(_finding("runtime-mirror-mismatch", layer.key, spec.master, spec.offset))
        if frame.sprite is not None:
            expected = derive_sprite(frame.canvas)
            if frame.sprite.sidecar != expected.sidecar or frame.sprite.image.tobytes() != expected.image.tobytes():
                findings.append(_finding("stored-sidecar-mismatch", layer.key, spec.master, spec.offset))

        metrics.append({
            "offset": spec.offset, "master": spec.master, "hidden": False,
            "opaquePixels": len(points), "componentCount": len(components),
            "detachedComponents": len(components) - 1,
            "detachedPixels": sum(len(component) for component in components[1:]),
            "componentPixels": [len(component) for component in components],
            "outsidePixels": len(outside),
            "forbiddenPixels": len(blocked),
            "attachmentPixels": len(primary & attachment) if layer.semantic_profile == "mullet"
            else len(points & attachment),
            "napePixels": nape_pixels, "landmarkDistance": landmark_distance,
            "centerDeltaTwice": center_delta_twice,
            "primaryPixels": primary_pixels,
            "primarySharePercent": primary_pixels * 100 // len(points),
            "span": span, "leftPixels": left_pixels, "rightPixels": right_pixels,
            "envelopeOutsidePixels": envelope_outside,
            "chinOverlapPixels": chin_overlap, "noseIntrusionPixels": upper_intrusion,
            "mouthIntrusionPixels": lower_intrusion, "forwardReach": forward,
            "rearwardReach": rearward,
        })
    runtime_metrics = _runtime_mirror_metrics(
        {offset: frame.canvas for offset, frame in by_offset.items()}, layer.key, findings
    )
    return SemanticQaReport(
        layer.semantic_profile, _sort_findings(findings), tuple(metrics), runtime_metrics,
        _provenance(template, layer.path, manifest_key=layer.key, manifest_type="authoring-layer"),
    )


def validate_look_conflicts(
    layer_results: Mapping[str, CompileResult], look: LookManifest, template: PaperdollTemplate,
) -> SemanticQaReport:
    expected = {layer.key for layer in look.layers}
    if set(layer_results) != expected:
        raise ValueError(f"semantic Look QA layers must be exactly {sorted(expected)}")
    findings: list[SemanticFinding] = []
    metrics: list[Mapping[str, Any]] = []
    frames_by_layer = {
        key: {frame.offset: frame for frame in result.frames}
        for key, result in layer_results.items()
    }
    if any(set(frames) != set(range(18)) for frames in frames_by_layer.values()):
        raise ValueError("semantic Look QA requires offsets 0..17 exactly once per layer")
    for spec in template.frames:
        offset = spec.offset
        occupancy = {
            layer.key: _opaque(frames_by_layer[layer.key][offset].canvas)
            for layer in look.layers
        }
        collisions = 0
        for first_index, first in enumerate(look.layers):
            for second in look.layers[first_index + 1:]:
                overlap = occupancy[first.key] & occupancy[second.key]
                if overlap:
                    collisions += len(overlap)
                    findings.append(_finding(
                        "cross-layer-collision", f"{first.key}+{second.key}", spec.master, offset,
                        pixels=len(overlap), firstOrder=first.order, secondOrder=second.order,
                    ))
                    findings.append(_finding(
                        "cross-layer-occlusion", f"{first.key}+{second.key}", spec.master, offset,
                        pixels=len(overlap), occludedLayer=first.key,
                    ))
        metrics.append({"offset": offset, "master": spec.master, "collisionPixels": collisions})
    blank_runtime = _runtime_mirror_metrics(
        {offset: Image.new("RGBA", template.frames[offset].size) for offset in range(18)},
        look.key, findings,
    )
    return SemanticQaReport(
        "look-conflicts", _sort_findings(findings), tuple(metrics), blank_runtime,
        _provenance(template, look.path, manifest_key=look.key, manifest_type="look", look_layers=look.layers),
    )


def validate_composed_look(
    layer_results: Mapping[str, CompileResult], look: LookManifest, template: PaperdollTemplate,
    base_frames: tuple[Image.Image, ...] | list[Image.Image],
) -> SemanticQaReport:
    expected = {layer.key for layer in look.layers}
    if set(layer_results) != expected or len(base_frames) != 18:
        raise ValueError("composed Look QA requires exact manifest layers and 18 base frames")
    locked_base = load_locked_base_frames(template)
    for offset, (provided, locked) in enumerate(zip(base_frames, locked_base)):
        if provided.size != locked.size or provided.convert("RGBA").tobytes() != locked.tobytes():
            raise ValueError(f"composed Look QA base frame {offset} differs from the locked base")
    by_layer = {
        key: {frame.offset: frame for frame in result.frames}
        for key, result in layer_results.items()
    }
    if any(set(frames) != set(range(18)) for frames in by_layer.values()):
        raise ValueError("composed Look QA requires offsets 0..17 exactly once per layer")
    ordered = [layer_results[layer.key] for layer in look.layers]
    composed = compose_look(list(base_frames), ordered)
    findings: list[SemanticFinding] = []
    metrics: list[Mapping[str, Any]] = []
    for spec in template.frames:
        offset = spec.offset
        base = base_frames[offset].convert("RGBA")
        if base.size != spec.size or composed[offset].size != spec.size:
            findings.append(_finding("frame-geometry-mismatch", look.key, spec.master, offset))
            continue
        occupancies = {
            layer.key: _opaque(by_layer[layer.key][offset].canvas) for layer in look.layers
        }
        authored_union = set().union(*occupancies.values())
        collision_pixels = 0
        occluded_by_layer = {layer.key: 0 for layer in look.layers}
        for index, first in enumerate(look.layers):
            for second in look.layers[index + 1:]:
                overlap = occupancies[first.key] & occupancies[second.key]
                if overlap:
                    collision_pixels += len(overlap)
                    occluded_by_layer[first.key] += len(overlap)
                    findings.append(_finding("cross-layer-collision", f"{first.key}+{second.key}",
                                             spec.master, offset, pixels=len(overlap)))
                    findings.append(_finding("cross-layer-occlusion", f"{first.key}+{second.key}",
                                             spec.master, offset, pixels=len(overlap),
                                             occludedLayer=first.key))
        changed_outside = 0
        composed_rgba = composed[offset].convert("RGBA")
        for y in range(spec.size[1]):
            for x in range(spec.size[0]):
                if (x, y) not in authored_union and composed_rgba.getpixel((x, y)) != base.getpixel((x, y)):
                    changed_outside += 1
        if changed_outside:
            findings.append(_finding("base-changed-outside-authored-union", look.key, spec.master, offset,
                                     pixels=changed_outside))
        sprite = derive_sprite(composed_rgba) if composed_rgba.getbbox() else None
        metrics.append({
            "offset": offset, "master": spec.master,
            "layerOrder": [layer.key for layer in look.layers],
            "authoredPixels": len(authored_union), "collisionPixels": collision_pixels,
            "opaquePixels": len(_opaque(composed_rgba)),
            "baseChangedOutsidePixels": changed_outside,
            "layerVisibility": {
                layer.key: {
                    "authoredPixels": len(occupancies[layer.key]),
                    "occludedPixels": occluded_by_layer[layer.key],
                    "visiblePixels": len(occupancies[layer.key]) - occluded_by_layer[layer.key],
                }
                for layer in look.layers
            },
            "composedBbox": None if composed_rgba.getbbox() is None else list(composed_rgba.getbbox()),
            "cropXShift": None if sprite is None else sprite.sidecar["xShift"],
            "cropYShift": None if sprite is None else sprite.sidecar["yShift"],
        })
    runtime_base = _runtime_mirror_metrics(
        {offset: image for offset, image in enumerate(composed)}, look.key, findings
    )
    stored_metrics = {item["offset"]: item for item in metrics}
    runtime = tuple({
        **item,
        "collisionPixels": stored_metrics[item["spriteOffset"]]["collisionPixels"],
        "baseChangedOutsidePixels": stored_metrics[item["spriteOffset"]]["baseChangedOutsidePixels"],
        "layerVisibility": stored_metrics[item["spriteOffset"]]["layerVisibility"],
    } for item in runtime_base)
    return SemanticQaReport(
        "composed-look", _sort_findings(findings), tuple(metrics), runtime,
        _provenance(template, look.path, manifest_key=look.key, manifest_type="look",
                    look_layers=look.layers, base_frames=base_frames),
    )


def require_semantic_report(payload: Mapping[str, Any], *, repo_root: Path = REPO_ROOT) -> None:
    if payload.get("schema") != REPORT_SCHEMA or payload.get("contract") != CONTRACT:
        raise ValueError(
            f"semantic QA evidence requires schema {REPORT_SCHEMA!r} and contract {CONTRACT!r}"
        )
    if payload.get("semanticProfile") not in {"mullet", "mustache", "look-conflicts", "composed-look"}:
        raise ValueError("semantic QA evidence has an unsupported semantic profile")
    findings = payload.get("findings")
    metrics = payload.get("metrics")
    runtime = payload.get("runtimeMetrics")
    if not isinstance(findings, list) or not isinstance(metrics, list) or not isinstance(runtime, list):
        raise ValueError("semantic QA evidence findings and metrics must be arrays")
    profile = payload["semanticProfile"]
    normalized_thresholds = json.loads(json.dumps(THRESHOLD_CONTRACT[profile]))
    if payload.get("thresholds") != normalized_thresholds:
        raise ValueError("semantic QA evidence thresholds differ from the locked contract")
    if payload.get("shipping") is not False or payload.get("automatedContractOnly") is not True \
            or payload.get("humanVisualApproval") is not False:
        raise ValueError("semantic QA evidence must be non-shipping automated contract evidence")
    if len(metrics) != 18 or {item.get("offset") for item in metrics if isinstance(item, dict)} != set(range(18)):
        raise ValueError("semantic QA evidence requires complete stored-18 metrics")
    expected_runtime = expected_states()
    if len(runtime) != 30:
        raise ValueError("semantic QA evidence requires complete runtime-30 metrics")
    for index, expected in enumerate(expected_runtime):
        if not isinstance(runtime[index], dict) or any(runtime[index].get(key) != value for key, value in expected.items()):
            raise ValueError(f"semantic QA runtime metric {index} differs from the canonical state contract")
        if not {"bbox", "opaquePixels", "cropXShift", "cropYShift", "cropWidth", "cropHeight"} <= set(runtime[index]):
            raise ValueError(f"semantic QA runtime metric {index} lacks mirror/crop evidence")
    finding_keys = {"severity", "code", "layer", "master", "offset", "details"}
    if any(not isinstance(item, dict) or set(item) != finding_keys
           or item.get("severity") not in {"error", "warning"}
           or not isinstance(item.get("code"), str) or not item["code"]
           or not isinstance(item.get("layer"), str) or not item["layer"]
           or (item.get("master") is not None and not isinstance(item["master"], str))
           or (item.get("offset") is not None and (isinstance(item["offset"], bool)
                                                   or not isinstance(item["offset"], int)
                                                   or not 0 <= item["offset"] < 18))
           or not isinstance(item.get("details"), dict) for item in findings):
        raise ValueError("semantic QA evidence contains malformed findings")
    calculated_valid = not any(item["severity"] == "error" for item in findings)
    if payload.get("valid") is not calculated_valid:
        raise ValueError("semantic QA evidence valid flag disagrees with error findings")
    if not calculated_valid:
        raise ValueError("semantic QA evidence contains error findings")
    for item in metrics:
        common = {"offset", "master"}
        if not common <= set(item):
            raise ValueError("semantic QA evidence contains blank stored metrics")
        if profile in {"mullet", "mustache"}:
            if not {"hidden", "opaquePixels", "componentCount"} <= set(item):
                raise ValueError("semantic QA layer metrics are incomplete")
            if not item["hidden"] and not {
                "outsidePixels", "forbiddenPixels", "attachmentPixels", "primaryPixels",
                "primarySharePercent", "napePixels", "landmarkDistance", "centerDeltaTwice",
            } <= set(item):
                raise ValueError("semantic QA visible layer metrics are incomplete")
            if item["hidden"]:
                if item["opaquePixels"] != 0 or item["componentCount"] != 0:
                    raise ValueError("semantic QA hidden layer metrics contain visible pixels")
                continue
            numeric = ("opaquePixels", "componentCount", "outsidePixels", "forbiddenPixels",
                       "attachmentPixels", "primaryPixels", "primarySharePercent", "detachedComponents",
                       "detachedPixels", "napePixels", "landmarkDistance", "centerDeltaTwice",
                       "span", "leftPixels", "rightPixels", "envelopeOutsidePixels",
                       "chinOverlapPixels", "noseIntrusionPixels", "mouthIntrusionPixels",
                       "forwardReach", "rearwardReach")
            if any(isinstance(item.get(key), bool) or not isinstance(item.get(key), int) or item[key] < 0
                   for key in numeric if key not in {"landmarkDistance", "centerDeltaTwice", "forwardReach", "rearwardReach"}) \
                    or any(isinstance(item.get(key), bool) or not isinstance(item.get(key), int)
                           for key in {"landmarkDistance", "centerDeltaTwice", "forwardReach", "rearwardReach"}) \
                    or not isinstance(item.get("componentPixels"), list):
                raise ValueError("semantic QA layer metric values are malformed")
            components = item["componentPixels"]
            if any(isinstance(value, bool) or not isinstance(value, int) or value <= 0 for value in components) \
                    or len(components) != item["componentCount"] or sum(components) != item["opaquePixels"] \
                    or components != sorted(components, reverse=True) or components[0] != item["primaryPixels"] \
                    or item["detachedComponents"] != len(components) - 1 \
                    or item["detachedPixels"] != sum(components[1:]) \
                    or item["primarySharePercent"] != item["primaryPixels"] * 100 // item["opaquePixels"]:
                raise ValueError("semantic QA component metrics are incoherent")
            if item["outsidePixels"] != 0 or item["forbiddenPixels"] != 0:
                raise ValueError("semantic QA evidence contains containment failures")
            pose_threshold = (MULLET_THRESHOLDS if profile == "mullet" else MUSTACHE_THRESHOLDS)[item["master"]]
            if profile == "mullet":
                minimum, maximum = pose_threshold["opaque"]
                if not minimum <= item["opaquePixels"] <= maximum \
                        or item["attachmentPixels"] < pose_threshold["scalp"] \
                        or item["attachmentPixels"] > item["primaryPixels"] \
                        or item["napePixels"] < pose_threshold["nape"] \
                        or item["napePixels"] > item["primaryPixels"] \
                        or not _meets_primary_share(item["primaryPixels"], item["opaquePixels"]) \
                        or any(value == 1 for value in components[1:]) \
                        or (item["master"] != "north" and item["landmarkDistance"] < 0):
                    raise ValueError("semantic QA mullet metrics violate hard thresholds")
            else:
                if item["detachedComponents"] != 0 or not 1 <= item["componentCount"] <= 2 \
                        or any(value < 2 for value in components) \
                        or item["attachmentPixels"] < pose_threshold["contact"] \
                        or item["attachmentPixels"] > item["opaquePixels"] \
                        or item.get("span", 0) < pose_threshold["span"] \
                        or item.get("envelopeOutsidePixels") != 0 \
                        or not 0 <= item["landmarkDistance"] <= 1 or abs(item["centerDeltaTwice"]) > 4 \
                        or item["chinOverlapPixels"] != 0 or item["noseIntrusionPixels"] != 0 \
                        or item["mouthIntrusionPixels"] != 0:
                    raise ValueError("semantic QA mustache metrics violate topology/envelope thresholds")
                if item["master"] in {"north", "north-west"} \
                        and (item.get("leftPixels", 0) < 2 or item.get("rightPixels", 0) < 2):
                    raise ValueError("semantic QA mustache metrics violate both-side coverage")
                if item["master"] in {"west", "combat-west"} and item["forwardReach"] < item["rearwardReach"]:
                    raise ValueError("semantic QA mustache metrics violate profile-side placement")
        elif profile in {"look-conflicts", "composed-look"}:
            if "collisionPixels" not in item:
                raise ValueError("semantic QA Look metrics are incomplete")
            if item["collisionPixels"] != 0:
                raise ValueError("semantic QA evidence contains cross-layer collisions")
            if profile == "composed-look":
                if item.get("baseChangedOutsidePixels") != 0 or not isinstance(item.get("layerVisibility"), dict):
                    raise ValueError("composed Look evidence contains base changes or incomplete visibility")
                if any(value.get("occludedPixels") != 0 for value in item["layerVisibility"].values()):
                    raise ValueError("composed Look evidence contains layer occlusion")
    if profile == "composed-look":
        for item in runtime:
            if item.get("collisionPixels") != 0 or item.get("baseChangedOutsidePixels") != 0 \
                    or not isinstance(item.get("layerVisibility"), dict) \
                    or any(value.get("occludedPixels") != 0 for value in item["layerVisibility"].values()):
                raise ValueError("composed Look runtime metrics contain collision, base change, or occlusion")
    provenance = payload.get("provenance")
    if not isinstance(provenance, dict):
        raise ValueError("semantic QA evidence provenance is missing")
    template_path = safe_repo_path(provenance.get("templatePath"), repo_root=repo_root)
    manifest_path = safe_repo_path(provenance.get("manifestPath"), repo_root=repo_root)
    if _digest(template_path) != provenance.get("templateSha256") or _digest(manifest_path) != provenance.get("manifestSha256"):
        raise ValueError("semantic QA evidence template/manifest digest changed")
    template = load_template(template_path, repo_root=repo_root)
    expected_masks = {
        master: {role: None if reference is None else reference.sha256
                 for role, reference in sorted(pose.masks.items())}
        for master, pose in sorted(template.pose_profiles.items())
    }
    if provenance.get("referenceDigests") != dict(sorted(template.reference_digests.items())) \
            or provenance.get("maskDigests") != expected_masks:
        raise ValueError("semantic QA evidence mask/reference digests differ from the locked template")
    for index, item in enumerate(runtime):
        width, height = template.frames[item["spriteOffset"]].size
        bbox = item["bbox"]
        if isinstance(item.get("opaquePixels"), bool) or not isinstance(item.get("opaquePixels"), int) \
                or item["opaquePixels"] < 0:
            raise ValueError(f"semantic QA runtime metric {index} has malformed opaque count")
        crop_values = (item["cropXShift"], item["cropYShift"], item["cropWidth"], item["cropHeight"])
        if bbox is None:
            if any(value is not None for value in crop_values) or item["opaquePixels"] != 0:
                raise ValueError(f"semantic QA runtime metric {index} has incoherent empty crop")
        elif not isinstance(bbox, list) or len(bbox) != 4 \
                or any(isinstance(value, bool) or not isinstance(value, int) for value in bbox) \
                or not (0 <= bbox[0] < bbox[2] <= width and 0 <= bbox[1] < bbox[3] <= height) \
                or crop_values != (bbox[0], bbox[1], bbox[2] - bbox[0], bbox[3] - bbox[1]):
            raise ValueError(f"semantic QA runtime metric {index} has malformed mirror/crop geometry")
    stored_by_offset = {item["offset"]: item for item in metrics}
    for offset, item in stored_by_offset.items():
        if item["master"] != template.frames[offset].master:
            raise ValueError("semantic QA stored metric master differs from template offset")
    unmirrored_bbox = {}
    for item in runtime:
        if not item["mirrorX"]:
            unmirrored_bbox.setdefault(item["spriteOffset"], item["bbox"])
    for item in runtime:
        stored = stored_by_offset[item["spriteOffset"]]
        if item["opaquePixels"] != stored.get("opaquePixels", item["opaquePixels"]):
            raise ValueError("semantic QA runtime opaque count differs from stored metric")
        source_box = unmirrored_bbox.get(item["spriteOffset"])
        width = template.frames[item["spriteOffset"]].size[0]
        expected_box = None if source_box is None else (
            [width - source_box[2], source_box[1], width - source_box[0], source_box[3]]
            if item["mirrorX"] else source_box
        )
        if item["bbox"] != expected_box:
            raise ValueError("semantic QA runtime bbox differs from stored mirror relation")
    paths = provenance.get("layerManifestPaths", {})
    digests = provenance.get("layerManifestDigests", {})
    if set(paths) != set(digests):
        raise ValueError("semantic QA evidence layer manifest provenance is inconsistent")
    for key, raw_path in paths.items():
        if _digest(safe_repo_path(raw_path, repo_root=repo_root)) != digests[key]:
            raise ValueError(f"semantic QA evidence layer manifest {key} digest changed")
    manifest_type = provenance.get("manifestType")
    manifest_key = provenance.get("manifestKey")
    if profile in {"mullet", "mustache"}:
        if manifest_type != "authoring-layer" or paths or digests:
            raise ValueError("semantic QA layer evidence has inconsistent manifest provenance")
        manifest = load_authoring_layer(manifest_path, repo_root=repo_root)
        if manifest.key != manifest_key or manifest.semantic_profile != profile:
            raise ValueError("semantic QA layer evidence does not match its parsed manifest")
    else:
        if manifest_type != "look":
            raise ValueError("semantic QA Look evidence has inconsistent manifest type")
        manifest = load_look_manifest(manifest_path, repo_root=repo_root)
        expected_paths = {layer.key: str(layer.manifest_path.relative_to(repo_root)) for layer in manifest.layers}
        expected_digests = {layer.key: _digest(layer.manifest_path) for layer in manifest.layers}
        if manifest.key != manifest_key or paths != expected_paths or digests != expected_digests:
            raise ValueError("semantic QA Look evidence does not match declared layer manifests")
        if profile == "composed-look":
            ordered_layer_keys = [layer.key for layer in manifest.layers]
            layer_keys = set(ordered_layer_keys)
            stored_visibility = {}
            for item in metrics:
                visibility = item["layerVisibility"]
                if set(visibility) != layer_keys:
                    raise ValueError("composed Look evidence visibility keys differ from manifest layers")
                if item.get("layerOrder") != ordered_layer_keys:
                    raise ValueError("composed Look evidence layer order differs from parsed manifest")
                for key in ("authoredPixels", "opaquePixels", "cropXShift", "cropYShift"):
                    if isinstance(item.get(key), bool) or not isinstance(item.get(key), int) or item[key] < 0:
                        raise ValueError("composed Look evidence stored numeric metrics are malformed")
                bbox = item.get("composedBbox")
                if not isinstance(bbox, list) or len(bbox) != 4 \
                        or any(isinstance(value, bool) or not isinstance(value, int) for value in bbox) \
                        or bbox != unmirrored_bbox[item["offset"]] \
                        or item["cropXShift"] != bbox[0] or item["cropYShift"] != bbox[1]:
                    raise ValueError("composed Look stored bbox/crop differs from canonical runtime")
                for value in visibility.values():
                    if set(value) != {"authoredPixels", "occludedPixels", "visiblePixels"} \
                            or any(isinstance(value[key], bool) or not isinstance(value[key], int) or value[key] < 0
                                   for key in value) \
                            or value["occludedPixels"] != 0 \
                            or value["visiblePixels"] != value["authoredPixels"] - value["occludedPixels"]:
                        raise ValueError("composed Look evidence visibility metrics are incoherent")
                if item["authoredPixels"] != sum(value["authoredPixels"] for value in visibility.values()):
                    raise ValueError("composed Look authored union disagrees with layer visibility")
                stored_visibility[item["offset"]] = visibility
            for item in runtime:
                if item["layerVisibility"] != stored_visibility[item["spriteOffset"]]:
                    raise ValueError("composed Look runtime visibility differs from stored offset")
            locked = load_locked_base_frames(template)
            expected_base = [hashlib.sha256(frame.tobytes()).hexdigest() for frame in locked]
            if provenance.get("baseFrameDigests") != expected_base:
                raise ValueError("composed Look evidence locked base digests differ")


__all__ = [
    "CONTRACT", "MULLET_THRESHOLDS", "MUSTACHE_THRESHOLDS", "REPORT_SCHEMA",
    "SemanticFinding", "SemanticQaReport",
    "require_semantic_report", "validate_composed_look", "validate_look_conflicts", "validate_semantic_layer",
]
