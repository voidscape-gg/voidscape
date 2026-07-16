from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
from typing import Any, Mapping, Sequence

from PIL import Image

from .compiler import CompileResult, expand_rigid_layer
from .evidence_contract import require_current_evidence_contract
from .geometry import derive_sprite, runtime_mirror
from .template import PaperdollTemplate
from .paths import REPO_ROOT


@dataclass(frozen=True)
class ArtThresholds:
    min_pixels: int
    max_pixels: int
    max_components: int
    min_component_pixels: int
    min_contact_ratio: float
    min_base_overlap_ratio: float


THRESHOLDS = {
    "hair": ArtThresholds(18, 280, 3, 1, 0.15, 0.10),
    "facial-hair": ArtThresholds(3, 40, 2, 1, 0.50, 0.50),
}


def _opaque(image: Image.Image) -> set[tuple[int, int]]:
    rgba = image.convert("RGBA")
    return {
        (x, y)
        for y in range(rgba.height)
        for x in range(rgba.width)
        if rgba.getpixel((x, y))[3] >= 128
    }


def _components(points: set[tuple[int, int]]) -> list[int]:
    remaining = set(points)
    sizes: list[int] = []
    while remaining:
        pending = [remaining.pop()]
        size = 0
        while pending:
            x, y = pending.pop()
            size += 1
            for neighbour in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    pending.append(neighbour)
        sizes.append(size)
    return sorted(sizes, reverse=True)


def _relative_pixels(image: Image.Image, crown: tuple[int, int]) -> frozenset[tuple[int, int, int, int, int, int]]:
    rgba = image.convert("RGBA")
    values = []
    for x, y in _opaque(rgba):
        r, g, b, a = rgba.getpixel((x, y))
        values.append((x - crown[0], y - crown[1], r, g, b, a))
    return frozenset(values)


def validate_compiled_layer(
    result: CompileResult,
    kind: str,
    template: PaperdollTemplate,
    base_frames: Sequence[Image.Image],
    *,
    thresholds: ArtThresholds | None = None,
    hidden_masters: Sequence[str] = (),
) -> dict[str, Any]:
    """Quantify a deterministic draft layer without writing or publishing it."""
    if kind not in THRESHOLDS:
        raise ValueError(f"unsupported art QA kind {kind!r}")
    if len(result.frames) != 18 or len(base_frames) != 18:
        raise ValueError("art QA requires exactly 18 compiled and base frames")
    limits = thresholds or THRESHOLDS[kind]
    contact_role = {"hair": "scalp_attachment", "facial-hair": "upper_lip_attachment"}[kind]
    source_frames = {}
    for frame in template.frames:
        source_frames.setdefault(frame.master, frame)
    hidden = set(hidden_masters)
    unknown_hidden = hidden - {frame.master for frame in template.frames}
    if unknown_hidden:
        raise ValueError(f"hidden masters are unknown: {sorted(unknown_hidden)}")
    findings = list(result.findings)
    metrics: list[dict[str, Any]] = []
    normalized_by_master: dict[str, frozenset] = {}
    for spec, compiled, base in zip(template.frames, result.frames, base_frames):
        if compiled.offset != spec.offset or compiled.canvas.size != spec.size or base.size != spec.size:
            findings.append(f"frame {spec.offset}: frame/template/base geometry mismatch")
            continue
        points = _opaque(compiled.canvas)
        count = len(points)
        if spec.master in hidden:
            if count != 0:
                findings.append(f"frame {spec.offset}: hidden master {spec.master} contains {count} opaque pixels")
            metrics.append({
                "base_overlap_pixels": 0, "base_overlap_ratio": 0.0, "bbox": None,
                "components": [], "contact_pixels": 0, "contact_ratio": 0.0,
                "hidden": True, "master": spec.master, "offset": spec.offset, "opaque_pixels": count,
            })
            normalized = _relative_pixels(compiled.canvas, spec.crown)
            previous = normalized_by_master.setdefault(spec.master, normalized)
            if previous != normalized:
                findings.append(f"frame {spec.offset}: normalized pixels flicker within {spec.master} propagation group")
            continue
        contact_reference = template.pose_profiles[spec.master].masks[contact_role]
        if contact_reference is None:
            findings.append(f"frame {spec.offset}: pose profile has no applicable {contact_role} mask")
            contact = 0
        else:
            with Image.open(contact_reference.path) as mask:
                source = source_frames[spec.master]
                dx, dy = spec.crown[0] - source.crown[0], spec.crown[1] - source.crown[1]
                contact = sum(
                    0 <= x - dx < mask.width
                    and 0 <= y - dy < mask.height
                    and bool(mask.getpixel((x - dx, y - dy)))
                    for x, y in points
                )
        base_points = _opaque(base)
        base_overlap = len(points & base_points)
        components = _components(points)
        contact_ratio = contact / count if count else 0.0
        base_overlap_ratio = base_overlap / count if count else 0.0
        if not limits.min_pixels <= count <= limits.max_pixels:
            findings.append(
                f"frame {spec.offset}: opaque pixels {count} outside [{limits.min_pixels}, {limits.max_pixels}]"
            )
        if len(components) > limits.max_components:
            findings.append(f"frame {spec.offset}: {len(components)} components exceeds {limits.max_components}")
        if components and components[-1] < limits.min_component_pixels:
            findings.append(
                f"frame {spec.offset}: smallest component {components[-1]}px is below {limits.min_component_pixels}px"
            )
        if contact_ratio < limits.min_contact_ratio:
            findings.append(
                f"frame {spec.offset}: contact ratio {contact_ratio:.3f} is below {limits.min_contact_ratio:.3f}"
            )
        if base_overlap_ratio < limits.min_base_overlap_ratio:
            findings.append(
                f"frame {spec.offset}: locked-base overlap ratio {base_overlap_ratio:.3f} is below "
                f"{limits.min_base_overlap_ratio:.3f}"
            )
        normalized = _relative_pixels(compiled.canvas, spec.crown)
        previous = normalized_by_master.setdefault(spec.master, normalized)
        if previous != normalized:
            findings.append(f"frame {spec.offset}: normalized pixels flicker within {spec.master} propagation group")
        bbox = compiled.canvas.getbbox()
        metrics.append({
            "base_overlap_pixels": base_overlap,
            "base_overlap_ratio": round(base_overlap_ratio, 6),
            "bbox": list(bbox) if bbox else None,
            "components": components,
            "contact_pixels": contact,
            "contact_ratio": round(contact_ratio, 6),
            "master": spec.master,
            "offset": spec.offset,
            "opaque_pixels": count,
            "hidden": False,
        })

    mirrors: list[dict[str, Any]] = []
    by_master: dict[str, list[int]] = {}
    for spec in template.frames:
        by_master.setdefault(spec.master, []).append(spec.offset)
    for target, source in sorted(template.mirrors.items()):
        for offset in by_master[source]:
            canvas = result.frames[offset].canvas
            mirrored = runtime_mirror(canvas)
            original_box = canvas.getbbox()
            mirrored_box = mirrored.getbbox()
            expected_box = None if original_box is None else (
                canvas.width - original_box[2], original_box[1], canvas.width - original_box[0], original_box[3]
            )
            if mirrored_box != expected_box or len(_opaque(mirrored)) != len(_opaque(canvas)):
                findings.append(f"frame {offset}: runtime mirror geometry mismatch for {target}")
            if runtime_mirror(mirrored).tobytes() != canvas.tobytes():
                findings.append(f"frame {offset}: runtime mirror is not reversible for {target}")
            mirrored_sprite = derive_sprite(mirrored) if mirrored.getbbox() else None
            mirrors.append({
                "source": source,
                "target": target,
                "offset": offset,
                "opaque_pixels": len(_opaque(mirrored)),
                "bbox": list(mirrored_box) if mirrored_box else None,
                "x_shift": mirrored_sprite.sidecar["xShift"] if mirrored_sprite else None,
            })
    return {
        "findings": sorted(set(findings)),
        "frames": metrics,
        "kind": kind,
        "mirrors": mirrors,
        "schema": "voidscape-appearance-art-qa/v1",
        "valid": not findings,
    }


def load_draft_masters(directory: Path, template: PaperdollTemplate) -> dict[str, Image.Image]:
    masters: dict[str, Image.Image] = {}
    first_by_master = {}
    for frame in template.frames:
        first_by_master.setdefault(frame.master, frame)
    for name, spec in first_by_master.items():
        path = directory / f"{name}.png"
        if not path.is_file():
            raise ValueError(f"draft master is missing: {path}")
        image = Image.open(path).convert("RGBA")
        if image.size != spec.size:
            raise ValueError(f"draft master {name} size {image.size} != {spec.size}")
        masters[name] = image
    return masters


def compile_and_validate_draft(
    directory: Path,
    kind: str,
    policy: str,
    template: PaperdollTemplate,
    base_frames: Sequence[Image.Image],
    *,
    hidden_masters: Sequence[str] = (),
) -> tuple[CompileResult, dict[str, Any]]:
    masters = load_draft_masters(directory, template)
    result = expand_rigid_layer(masters, kind, policy, template, hidden_masters=set(hidden_masters))
    return result, validate_compiled_layer(
        result, kind, template, base_frames, hidden_masters=hidden_masters
    )


def validate_draft_root(
    root: Path,
    look_path: Path,
    template_path: Path,
    *,
    repo_root: Path = REPO_ROOT,
) -> dict[str, Any]:
    """Validate an existing deterministic tmp draft without modifying it."""
    from .look import load_locked_base_frames, load_look_manifest
    from .template import load_template

    errors: list[str] = []
    root = root.resolve()
    try:
        root.relative_to((repo_root / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("art QA root must remain under repository tmp/") from exc
    report = json.loads((root / "report.json").read_text())
    require_current_evidence_contract(report)
    if report.get("shipping") is not False:
        errors.append("draft report must be explicitly non-shipping")
    if report.get("generatedByAI") is not False:
        errors.append("draft report may not claim AI-generated final pixels")
    if report.get("findings") != []:
        errors.append("draft report contains generation findings")
    look = load_look_manifest(look_path, repo_root=repo_root)
    template = load_template(template_path, repo_root=repo_root)
    base = load_locked_base_frames(template)
    layer_reports: dict[str, dict[str, Any]] = {}
    for layer in look.layers:
        compiled, qa = compile_and_validate_draft(
            root / "masters" / layer.key,
            layer.kind,
            layer.manifest.palette_policy,
            template,
            base,
            hidden_masters=layer.manifest.hidden_masters,
        )
        layer_reports[layer.key] = qa
        errors.extend(f"{layer.key}: {finding}" for finding in qa["findings"])
        if report.get("artQa", {}).get(layer.key) != qa:
            errors.append(f"{layer.key}: report artQa metrics are stale or missing")
        for frame in compiled.frames:
            saved_path = root / "compiled/layers" / layer.key / f"frame_{frame.offset:02d}.png"
            try:
                saved = Image.open(saved_path).convert("RGBA")
            except OSError as exc:
                errors.append(f"{layer.key}: missing compiled frame {frame.offset}: {exc}")
                continue
            if frame.canvas.size != saved.size or frame.canvas.tobytes() != saved.tobytes():
                errors.append(f"{layer.key}: compiled frame {frame.offset} differs from deterministic masters")
    hashes = report.get("sha256")
    if not isinstance(hashes, dict):
        errors.append("draft report has no artifact hash map")
    else:
        for relative, expected in hashes.items():
            path = (root / relative).resolve()
            try:
                path.relative_to(root)
                actual = hashlib.sha256(path.read_bytes()).hexdigest()
            except (OSError, ValueError) as exc:
                errors.append(f"artifact {relative} cannot be verified: {exc}")
                continue
            if actual != expected:
                errors.append(f"artifact {relative} digest differs from report")
    return {
        "errors": sorted(set(errors)),
        "layers": layer_reports,
        "look": look.key,
        "root": str(root),
        "schema": "voidscape-appearance-art-qa-root/v1",
        "valid": not errors,
    }


__all__ = [
    "ArtThresholds",
    "THRESHOLDS",
    "compile_and_validate_draft",
    "load_draft_masters",
    "validate_draft_root",
    "validate_compiled_layer",
]
