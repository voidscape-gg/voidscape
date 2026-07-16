"""Compatibility-only evidence for the adopted, known-bad Cowboy hat.

This module is deliberately not imported by the compiler, semantic authoring QA,
candidate staging, or publisher.  It explains legacy geometry without making hats
authorable or publishable.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

from PIL import Image, ImageChops, ImageDraw

from .calibration import grayscale_mask, sprite_canvas
from .client_preview import load_composite_fixture, pixel_mismatch_count, render_composite
from .cowboy_compare import compare_cowboy
from .paths import REPO_ROOT
from .template import PaperdollTemplate
from .workbench_report import expected_states, validate_report


SCHEMA = "voidscape-adopted-appearance-diagnostic/v1"
MASTER_OFFSETS = {
    "north": 0, "north-west": 3, "west": 6,
    "south-west": 9, "south": 12, "combat-west": 15,
}
MASTER_DIRECTIONS = {
    "north": "north", "north-west": "north-west", "west": "west",
    "south-west": "south-west", "south": "south", "combat-west": "combat-a",
}
REFERENCE_ROOT = REPO_ROOT / "content/custom/cowboy_hat/art/source/references"
COWBOY_ROOT = REPO_ROOT / "content/custom/cowboy_hat/art/final/worn"


def _sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _repo_path(path: Path) -> str:
    return str(path.resolve().relative_to(REPO_ROOT.resolve()))


def _points(mask: Image.Image) -> set[tuple[int, int]]:
    mono = mask.convert("1")
    return {(x, y) for y in range(mono.height) for x in range(mono.width) if mono.getpixel((x, y))}


def _logical_alpha(directory: Path, offset: int, size: tuple[int, int]) -> Image.Image:
    path = directory / f"frame_{offset:02d}.png"
    sidecar = json.loads(path.with_suffix(".png.json").read_text())
    source = Image.open(path).convert("RGBA")
    canvas = Image.new("RGBA", size)
    canvas.alpha_composite(source, (sidecar["xShift"], sidecar["yShift"]))
    return canvas.getchannel("A").point(lambda value: 255 if value else 0, mode="1")


def _relative(points: Iterable[tuple[int, int]], crown: tuple[int, int]) -> set[tuple[int, int]]:
    return {(x - crown[0], y - crown[1]) for x, y in points}


def _bbox(points: set[tuple[int, int]]) -> list[int] | None:
    if not points:
        return None
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    # Half-open bounds match Pillow crops and the legacy sidecar convention.
    return [min(xs), min(ys), max(xs) + 1, max(ys) + 1]


def _ratio(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 4) if denominator else 0.0


def frame_metrics(directory: Path, template: PaperdollTemplate, master: str) -> dict[str, Any]:
    offset = MASTER_OFFSETS[master]
    frame = template.frames[offset]
    hat = _points(_logical_alpha(directory, offset, frame.size))
    scalp_ref = template.pose_profiles[master].masks["scalp_attachment"]
    assert scalp_ref is not None
    scalp = _points(Image.open(scalp_ref.path))
    short_hair = _points(grayscale_mask(sprite_canvas("head1", offset, frame.size)))
    long_hair = _points(grayscale_mask(sprite_canvas("fhead1", offset, frame.size)))
    rows: list[tuple[int, int, float]] = []
    for y in sorted({point[1] for point in hat}):
        xs = [x for x, row in hat if row == y]
        rows.append((len(xs), y, (min(xs) + max(xs)) / 2))
    span, span_y, span_center = max(rows, default=(0, frame.crown[1], float(frame.crown[0])))
    relative = _relative(hat, frame.crown)
    return {
        "master": master,
        "visualPose": template.pose_profiles[master].visual_pose,
        "storedOffset": offset,
        "opaquePixels": len(hat),
        "crownRelativeBbox": _bbox(relative),
        "scalpCoverageRatio": _ratio(len(hat & scalp), len(scalp)),
        "shortHairExposureRatio": _ratio(len(short_hair - hat), len(short_hair)),
        "longHairExposureRatio": _ratio(len(long_hair - hat), len(long_hair)),
        "maxHorizontalSpan": span,
        "maxSpanYFromCrown": span_y - frame.crown[1],
        "maxSpanCenterXFromCrown": round(span_center - frame.crown[0], 2),
        "_relativePoints": relative,
    }


def silhouette_comparison(left: Mapping[str, Any], right: Mapping[str, Any]) -> dict[str, Any]:
    a, b = set(left["_relativePoints"]), set(right["_relativePoints"])
    union = a | b
    jaccard = _ratio(len(a & b), len(union))
    if not a or not b:
        hausdorff = None
    else:
        distance = lambda point, other: min(abs(point[0] - q[0]) + abs(point[1] - q[1]) for q in other)
        hausdorff = max(max(distance(point, b) for point in a), max(distance(point, a) for point in b))
    return {
        "leftMaster": left["master"], "rightMaster": right["master"],
        "opaquePixelRatio": round(right["opaquePixels"] / left["opaquePixels"], 4),
        "normalizedJaccard": jaccard, "manhattanHausdorff": hausdorff,
        "leftOpaquePixels": left["opaquePixels"], "rightOpaquePixels": right["opaquePixels"],
        "leftCrownRelativeBbox": left["crownRelativeBbox"],
        "rightCrownRelativeBbox": right["crownRelativeBbox"],
    }


def _public(metrics: Mapping[str, Any]) -> dict[str, Any]:
    return {key: value for key, value in metrics.items() if not key.startswith("_")}


def _finding(code: str, master: str, metric: str, actual: Any, expectation: str) -> dict[str, Any]:
    return {
        "severity": "warning", "code": code, "master": master,
        "metric": metric, "actual": actual, "expectation": expectation,
    }


def diagnostic_findings(metrics: Mapping[str, Mapping[str, Any]], comparison: Mapping[str, Any]) -> list[dict[str, Any]]:
    findings: list[dict[str, Any]] = []
    for master, item in metrics.items():
        if item["scalpCoverageRatio"] < 0.9:
            findings.append(_finding("HAT_SCALP_EXPOSED", master, "scalpCoverageRatio",
                                    item["scalpCoverageRatio"], ">= 0.90 declared full-cover diagnostic target"))
        if item["shortHairExposureRatio"] > 0.1:
            findings.append(_finding("HAT_SHORT_HAIR_EXPOSED", master, "shortHairExposureRatio",
                                    item["shortHairExposureRatio"], "<= 0.10 declared full-cover diagnostic target"))
        if item["maxHorizontalSpan"] < 18:
            findings.append(_finding("HAT_BRIM_NARROW", master, "maxHorizontalSpan",
                                    item["maxHorizontalSpan"], ">= 18 pixels for the adopted Cowboy silhouette"))
        if abs(item["maxSpanCenterXFromCrown"]) > 2:
            findings.append(_finding("HAT_BRIM_OFF_AXIS", master, "maxSpanCenterXFromCrown",
                                    item["maxSpanCenterXFromCrown"], "within 2 pixels of the locked crown axis"))
    if (comparison["normalizedJaccard"] < 0.8 or comparison["manhattanHausdorff"] > 2
            or not 0.75 <= comparison["opaquePixelRatio"] <= 1.25):
        findings.append(_finding(
            "HAT_PROFILE_COMBAT_DIVERGENCE", "west/combat-west", "normalized silhouette",
            {key: comparison[key] for key in ("opaquePixelRatio", "normalizedJaccard", "manhattanHausdorff")},
            "ratio 0.75..1.25, Jaccard >= 0.80, Hausdorff <= 2",
        ))
    rear_delta = abs(metrics["south-west"]["shortHairExposureRatio"] - metrics["south"]["shortHairExposureRatio"])
    if rear_delta > 0.1:
        findings.append(_finding("HAT_REAR_COVERAGE_DIVERGENCE", "south-west/south",
                                "shortHairExposureRatioDelta", round(rear_delta, 4), "<= 0.10"))
    return findings


def _overlay(template: PaperdollTemplate, master: str, hat_root: Path) -> Image.Image:
    offset = MASTER_OFFSETS[master]
    frame = template.frames[offset]
    base = sprite_canvas("head1", offset, frame.size).convert("RGBA")
    hat_path = hat_root / f"frame_{offset:02d}.png"
    sidecar = json.loads(hat_path.with_suffix(".png.json").read_text())
    hat = Image.open(hat_path).convert("RGBA")
    hat_mask = _logical_alpha(hat_root, offset, frame.size)
    hair = grayscale_mask(sprite_canvas("head1", offset, frame.size))
    exposed = ImageChops.logical_and(hair, ImageChops.invert(hat_mask.convert("L")).convert("1"))
    scalp_ref = template.pose_profiles[master].masks["scalp_attachment"]
    assert scalp_ref is not None
    scalp = Image.open(scalp_ref.path).convert("1")
    covered = ImageChops.logical_and(scalp, hat_mask)
    canvas = Image.new("RGBA", frame.size, (14, 18, 25, 255))
    canvas.alpha_composite(base)
    canvas.alpha_composite(hat, (sidecar["xShift"], sidecar["yShift"]))
    for mask, colour in ((covered, (40, 220, 110, 185)), (exposed, (255, 30, 180, 230))):
        layer = Image.new("RGBA", frame.size, colour)
        layer.putalpha(mask.convert("L").point(lambda value: colour[3] if value else 0))
        canvas.alpha_composite(layer)
    draw = ImageDraw.Draw(canvas)
    crown = frame.crown
    draw.line((crown[0] - 2, crown[1], crown[0] + 2, crown[1]), fill=(255, 230, 70, 255))
    draw.line((crown[0], crown[1] - 2, crown[0], crown[1] + 2), fill=(255, 230, 70, 255))
    for name in ("forehead", "occiput", "nose_tip"):
        point = template.pose_profiles[master].landmarks[name]
        if point is not None:
            x, y = crown[0] + point[0], crown[1] + point[1]
            draw.point((x, y), fill=(70, 210, 255, 255))
    return canvas


def _panel(composite: Image.Image, overlay: Image.Image, metric: Mapping[str, Any], findings: list[Mapping[str, Any]]) -> Image.Image:
    panel = Image.new("RGB", (1100, 490), (18, 21, 28))
    draw = ImageDraw.Draw(panel)
    draw.text((14, 12), f"{metric['master']} / {metric['visualPose']} / stored {metric['storedOffset']}", fill=(245, 235, 210))
    exact = composite.resize((264, 336), Image.Resampling.NEAREST)
    diagnostic = overlay.convert("RGB").resize((overlay.width * 4, overlay.height * 4), Image.Resampling.NEAREST)
    panel.paste(exact, (14, 48)); panel.paste(diagnostic, (310, 48))
    draw.text((14, 398), "exact R2 compositor", fill=(170, 190, 220))
    draw.text((310, 398), "green covered scalp / magenta exposed short hair", fill=(170, 190, 220))
    x, y = 670, 52
    lines = [
        f"opaque: {metric['opaquePixels']}", f"bbox rel crown: {metric['crownRelativeBbox']}",
        f"scalp coverage: {metric['scalpCoverageRatio']:.2f}",
        f"short-hair exposure: {metric['shortHairExposureRatio']:.2f}",
        f"long-hair exposure (context): {metric['longHairExposureRatio']:.2f}",
        f"brim/span: {metric['maxHorizontalSpan']} px @ y {metric['maxSpanYFromCrown']:+d}",
        f"span center from crown: {metric['maxSpanCenterXFromCrown']:+.1f}", "", "diagnostic warnings:",
    ] + ([item["code"] for item in findings] or ["none"])
    for line in lines:
        draw.text((x, y), line, fill=(235, 225, 205) if not line.startswith("HAT_") else (255, 135, 120))
        y += 25
    return panel


def _diff_image(left: Mapping[str, Any], right: Mapping[str, Any]) -> Image.Image:
    points = set(left["_relativePoints"]) | set(right["_relativePoints"])
    min_x, max_x = min(x for x, _ in points) - 3, max(x for x, _ in points) + 3
    min_y, max_y = min(y for _, y in points) - 3, max(y for _, y in points) + 3
    scale = 8
    image = Image.new("RGB", ((max_x - min_x + 1) * scale + 520, (max_y - min_y + 1) * scale + 70), (18, 21, 28))
    draw = ImageDraw.Draw(image)
    draw.text((12, 10), "profile vs combat-profile crown-normalized XOR", fill=(245, 235, 210))
    a, b = set(left["_relativePoints"]), set(right["_relativePoints"])
    for x, y in a | b:
        colour = (210, 210, 210) if (x, y) in a & b else (255, 80, 150) if (x, y) in a else (80, 210, 255)
        px, py = 12 + (x - min_x) * scale, 46 + (y - min_y) * scale
        draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=colour)
    comparison = silhouette_comparison(left, right)
    tx, ty = image.width - 490, 55
    for line in (f"profile opaque: {comparison['leftOpaquePixels']}",
                 f"combat opaque: {comparison['rightOpaquePixels']}",
                 f"pixel ratio: {comparison['opaquePixelRatio']:.3f}",
                 f"Jaccard: {comparison['normalizedJaccard']:.3f}",
                 f"Manhattan Hausdorff: {comparison['manhattanHausdorff']}"):
        draw.text((tx, ty), line, fill=(240, 225, 205)); ty += 28
    return image


def _controls_image(controls: Mapping[str, Any]) -> Image.Image:
    image = Image.new("RGB", (1000, 520), (18, 21, 28)); draw = ImageDraw.Draw(image)
    draw.text((14, 12), "Authentic observational controls (not generic hat publishing rules)", fill=(245, 235, 210))
    y = 50
    for name, control in controls.items():
        draw.text((14, y), name, fill=(100, 220, 190)); y += 24
        compare = control["profileCombatComparison"]
        draw.text((34, y), f"profile/combat ratio {compare['opaquePixelRatio']:.3f}, Jaccard {compare['normalizedJaccard']:.3f}, Hausdorff {compare['manhattanHausdorff']}", fill=(230, 220, 205)); y += 22
        profiles = control["masterMetrics"]
        draw.text((34, y), "short-hair exposure: " + ", ".join(f"{key}={value['shortHairExposureRatio']:.2f}" for key, value in profiles.items()), fill=(190, 205, 225)); y += 42
    return image


def write_cowboy_diagnostic(fixture_path: Path, template: PaperdollTemplate, output_root: Path) -> dict[str, Any]:
    output_root = output_root.resolve()
    try:
        output_root.relative_to((REPO_ROOT / "tmp").resolve())
    except ValueError as exc:
        raise ValueError("adopted appearance diagnostic evidence must remain under repository tmp/") from exc
    fixture = load_composite_fixture(fixture_path)
    cowboy_layer = next((layer for layer in fixture.layers.values() if layer.appearance_id == 245), None)
    if cowboy_layer is None or cowboy_layer.name != "cowboyhat" or cowboy_layer.sprite_base != 1890:
        raise ValueError("composite fixture does not contain adopted Cowboy appearance 245/base 1890")
    oracle = json.loads(fixture.oracle_report.read_text())
    errors = validate_report(oracle)
    if errors:
        raise ValueError("invalid Workbench oracle: " + "; ".join(errors))

    rendered: dict[str, Image.Image] = {}
    mismatch_frames = mismatch_pixels = 0
    for state, capture in zip(expected_states(), oracle["captures"]):
        result = render_composite(fixture, state)
        mismatches = pixel_mismatch_count(result.image, Image.open(capture["isolatedRaster"]["pngPath"]))
        mismatch_frames += int(bool(mismatches)); mismatch_pixels += mismatches
        if state["frame"] == 0 and state["direction"] in MASTER_DIRECTIONS.values():
            master = next(key for key, direction in MASTER_DIRECTIONS.items() if direction == state["direction"])
            rendered[master] = result.image
    if mismatch_frames:
        raise ValueError(f"exact compositor no longer matches Workbench oracle: {mismatch_frames} frames/{mismatch_pixels} pixels")

    cowboy = {master: frame_metrics(COWBOY_ROOT, template, master) for master in MASTER_OFFSETS}
    profile_combat = silhouette_comparison(cowboy["west"], cowboy["combat-west"])
    findings = diagnostic_findings(cowboy, profile_combat)
    controls: dict[str, Any] = {}
    for name in ("mediumhelm", "partyhat", "wizardshat"):
        values = {master: frame_metrics(REFERENCE_ROOT / name, template, master) for master in MASTER_OFFSETS}
        controls[name] = {
            "role": "authentic-observational-control",
            "masterMetrics": {master: _public(value) for master, value in values.items()},
            "profileCombatComparison": silhouette_comparison(values["west"], values["combat-west"]),
        }

    output_root.mkdir(parents=True, exist_ok=True)
    panels_dir = output_root / "panels"; panels_dir.mkdir(exist_ok=True)
    panels = []
    for master in MASTER_OFFSETS:
        relevant = [finding for finding in findings if master in finding["master"].split("/")]
        panel = _panel(rendered[master], _overlay(template, master, COWBOY_ROOT), cowboy[master], relevant)
        path = panels_dir / f"{master}.png"; panel.save(path, optimize=False)
        panels.append((master, path, panel))
    sheet = Image.new("RGB", (1100, 490 * 6), (12, 15, 20))
    for index, (_, _, panel) in enumerate(panels): sheet.paste(panel, (0, index * 490))
    sheet_path = output_root / "cowboy-panel-diagnostics.png"; sheet.save(sheet_path, optimize=False)
    diff_path = output_root / "profile-combat-diff.png"
    _diff_image(cowboy["west"], cowboy["combat-west"]).save(diff_path, optimize=False)
    controls_path = output_root / "authentic-controls.png"; _controls_image(controls).save(controls_path, optimize=False)

    comparison = compare_cowboy(COWBOY_ROOT)
    report = {
        "schema": SCHEMA, "shipping": False, "diagnosticOnly": True,
        "authoringEnabled": False, "publishable": False, "appearanceKey": "cowboy_hat",
        "appearanceId": 245, "knownBad": True, "visuallyAcceptable": False,
        "humanVisualApproval": False,
        "mechanicallyValid": comparison["changedFrames"] == [] and mismatch_frames == 0,
        "mechanicalEvidence": {
            "legacyFramesMatchBothArchives": comparison["changedFrames"] == [],
            "exactCompositorMatchesWorkbenchOracle": mismatch_frames == 0,
            "mismatchedFrames": mismatch_frames, "mismatchedPixels": mismatch_pixels,
            "fixture": _repo_path(fixture.path), "fixtureSha256": fixture.digest,
            "oracleReport": _repo_path(fixture.oracle_report), "oracleReportSha256": fixture.oracle_report_digest,
            "spriteArchive": _repo_path(fixture.archive), "spriteArchiveSha256": fixture.archive_digest,
        },
        "template": {"key": template.key, "path": _repo_path(template.path),
                     "sha256": template.digest},
        "sourceFrames": {str(path.relative_to(COWBOY_ROOT)): _sha(path) for path in sorted(COWBOY_ROOT.glob("frame_*"))},
        "masterMetrics": {master: _public(value) for master, value in cowboy.items()},
        "profileCombatComparison": profile_combat, "controls": controls, "findings": findings,
        "warningCount": len(findings),
        "artifacts": {
            "panelContactSheet": {"path": sheet_path.name, "sha256": _sha(sheet_path)},
            "profileCombatDiff": {"path": diff_path.name, "sha256": _sha(diff_path)},
            "authenticControls": {"path": controls_path.name, "sha256": _sha(controls_path)},
            "panels": {name: {"path": str(path.relative_to(output_root)), "sha256": _sha(path)} for name, path, _ in panels},
        },
        "boundary": "Warnings explain adopted legacy geometry only; this report is not consumed by compiler, candidate staging, or publisher.",
    }
    (output_root / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")
    return report


__all__ = ["SCHEMA", "diagnostic_findings", "frame_metrics", "silhouette_comparison", "write_cowboy_diagnostic"]
