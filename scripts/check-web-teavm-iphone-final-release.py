#!/usr/bin/env python3
"""Audit final iPhone web release evidence before shipping."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_json(path: Path, label: str, failures: list[str]) -> dict[str, Any]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        failures.append(f"{label} JSON could not be read from {path}: {exc}")
        return {}
    if not isinstance(data, dict):
        failures.append(f"{label} JSON must be an object: {path}")
        return {}
    return data


def section_text(text: str, heading: str) -> str | None:
    match = re.search(rf"^##\s+{re.escape(heading)}\s*$", text, re.MULTILINE)
    if not match:
        return None
    section = text[match.end():]
    next_heading = re.search(r"^##\s+", section, re.MULTILINE)
    if next_heading:
        section = section[:next_heading.start()]
    return section


def extract_deployment_summary(report_path: Path, failures: list[str]) -> dict[str, Any]:
    try:
        text = report_path.read_text(encoding="utf-8")
    except Exception as exc:
        failures.append(f"QA report could not be read from {report_path}: {exc}")
        return {}
    section = section_text(text, "Deployment Verification")
    if section is None:
        failures.append("QA report is missing ## Deployment Verification")
        return {}
    match = re.search(r"```json\s*(.*?)\s*```", section, re.DOTALL | re.IGNORECASE)
    if not match:
        failures.append("QA report Deployment Verification section is missing fenced JSON")
        return {}
    raw = match.group(1).strip()
    if not raw:
        failures.append("QA report Deployment Verification JSON is empty")
        return {}
    try:
        summary = json.loads(raw)
    except json.JSONDecodeError as exc:
        failures.append(f"QA report Deployment Verification JSON is invalid: {exc}")
        return {}
    if not isinstance(summary, dict):
        failures.append("QA report Deployment Verification JSON must be an object")
        return {}
    return summary


def add_check(checks: list[dict[str, Any]], name: str, passed: bool, detail: str = "") -> None:
    checks.append({"name": name, "status": "passed" if passed else "failed", "detail": detail})


def nested_dict(data: dict[str, Any], *keys: str) -> dict[str, Any]:
    value: Any = data
    for key in keys:
        if not isinstance(value, dict):
            return {}
        value = value.get(key)
    return value if isinstance(value, dict) else {}


def validate_hidden_control(
    control: dict[str, Any],
    label: str,
    checks: list[dict[str, Any]],
    failures: list[str],
) -> None:
    hidden = control.get("display") == "none" and control.get("visible") is False
    add_check(checks, f"controls overlay {label} hidden", hidden, repr({
        "display": control.get("display"),
        "visible": control.get("visible"),
    }))
    if not hidden:
        failures.append(f"controls smoke overlay proof must show {label} hidden")


def validate_visible_hittable_control(
    control: dict[str, Any],
    label: str,
    checks: list[dict[str, Any]],
    failures: list[str],
) -> None:
    visible = control.get("display") == "flex" and control.get("visible") is True and control.get("hitSelf") is True
    rect = control.get("rect")
    if not isinstance(rect, dict):
        rect = {}
    target_sized = int(rect.get("width") or 0) >= 44 and int(rect.get("height") or 0) >= 44
    ok = visible and target_sized
    add_check(checks, f"controls overlay {label} visible hittable", ok, repr({
        "display": control.get("display"),
        "visible": control.get("visible"),
        "hitSelf": control.get("hitSelf"),
        "rect": rect,
    }))
    if not ok:
        failures.append(f"controls smoke overlay proof must show {label} visible, hittable, and at least 44x44")


def validate_controls_overlay_evidence(
    steps_by_name: dict[str, dict[str, Any]],
    checks: list[dict[str, Any]],
    failures: list[str],
) -> None:
    controls_step = steps_by_name.get("controls-smoke") or {}
    artifact = controls_step.get("artifact")
    if not isinstance(artifact, str) or not artifact:
        failures.append("local preflight controls-smoke step must include an artifact directory")
        add_check(checks, "controls smoke artifact recorded", False)
        return
    artifact_dir = Path(artifact)
    summary_path = artifact_dir / "summary.json"
    if not summary_path.is_file():
        failures.append(f"controls smoke summary not found: {summary_path}")
        add_check(checks, "controls smoke summary exists", False, str(summary_path))
        return
    add_check(checks, "controls smoke summary exists", True, str(summary_path))
    summary = load_json(summary_path, "controls smoke summary", failures)
    if not summary:
        return

    overlay = nested_dict(summary, "controls", "overlaySuppression")
    overlay_present = bool(overlay)
    add_check(checks, "controls smoke overlay suppression evidence present", overlay_present, str(summary_path))
    if not overlay_present:
        failures.append(
            "controls smoke summary must include controls.overlaySuppression; rerun "
            "scripts/check-web-teavm-iphone-release.sh with current controls smoke tooling"
        )
        return

    normal = nested_dict(overlay, "normal")
    normal_ui = nested_dict(normal, "ui")
    normal_controls = nested_dict(normal, "controls")
    normal_panel_mode_ok = normal_ui.get("panelAccessMode") == "canvas"
    normal_chat_mode_ok = normal_ui.get("chatAccessMode") == "canvas"
    add_check(checks, "controls overlay normal panelAccessMode canvas", normal_panel_mode_ok, repr(normal_ui.get("panelAccessMode")))
    add_check(checks, "controls overlay normal chatAccessMode canvas", normal_chat_mode_ok, repr(normal_ui.get("chatAccessMode")))
    if not normal_panel_mode_ok:
        failures.append("controls smoke overlay proof must show normal panelAccessMode: canvas")
    if not normal_chat_mode_ok:
        failures.append("controls smoke overlay proof must show normal chatAccessMode: canvas")
    validate_hidden_control(nested_dict(normal_controls, "panelButton"), "normal DOM panel button", checks, failures)
    validate_hidden_control(nested_dict(normal_controls, "panelDrawer"), "normal DOM panel drawer", checks, failures)
    validate_hidden_control(nested_dict(normal_controls, "chatButton"), "normal DOM chat button", checks, failures)
    validate_hidden_control(nested_dict(normal_controls, "chatTray"), "normal DOM chat tray", checks, failures)
    validate_visible_hittable_control(nested_dict(normal_controls, "keyboardButton"), "normal Aa helper", checks, failures)
    validate_visible_hittable_control(nested_dict(normal_controls, "actionButton"), "normal context helper", checks, failures)

    world_map = nested_dict(overlay, "worldMap")
    world_map_controls = nested_dict(world_map, "controls")
    world_map_input = nested_dict(world_map, "input")
    body_class = str(world_map.get("bodyClass") or "")
    world_map_class_ok = "world-map-open" in body_class.split()
    world_map_input_ok = world_map_input.get("worldMap") is True
    add_check(checks, "controls overlay world map body class", world_map_class_ok, body_class)
    add_check(checks, "controls overlay world map input mode", world_map_input_ok, repr(world_map_input.get("worldMap")))
    if not world_map_class_ok:
        failures.append("controls smoke overlay proof must show world-map-open body class")
    if not world_map_input_ok:
        failures.append("controls smoke overlay proof must show world-map input mode active")
    validate_hidden_control(nested_dict(world_map_controls, "keyboardButton"), "world-map Aa helper", checks, failures)
    validate_hidden_control(nested_dict(world_map_controls, "actionButton"), "world-map context helper", checks, failures)
    validate_hidden_control(nested_dict(world_map_controls, "panelButton"), "world-map DOM panel button", checks, failures)
    validate_hidden_control(nested_dict(world_map_controls, "chatButton"), "world-map DOM chat button", checks, failures)


def validate_world_map_mobile_modal_evidence(
    steps_by_name: dict[str, dict[str, Any]],
    checks: list[dict[str, Any]],
    failures: list[str],
) -> None:
    world_map_step = steps_by_name.get("world-map-smoke") or {}
    artifact = world_map_step.get("artifact")
    if not isinstance(artifact, str) or not artifact:
        failures.append("local preflight world-map-smoke step must include an artifact directory")
        add_check(checks, "world map smoke artifact recorded", False)
        return
    artifact_dir = Path(artifact)
    summary_path = artifact_dir / "summary.json"
    if not summary_path.is_file():
        failures.append(f"world map smoke summary not found: {summary_path}")
        add_check(checks, "world map smoke summary exists", False, str(summary_path))
        return
    add_check(checks, "world map smoke summary exists", True, str(summary_path))
    summary = load_json(summary_path, "world map smoke summary", failures)
    world_map = summary.get("worldMap")
    if not isinstance(world_map, dict):
        failures.append("world map smoke summary must include a worldMap object")
        add_check(checks, "world map evidence present", False, str(summary_path))
        return
    add_check(checks, "world map evidence present", True, str(summary_path))

    canvas = world_map.get("canvas")
    opened = world_map.get("opened")
    if not isinstance(canvas, dict):
        canvas = {}
    if not isinstance(opened, dict):
        opened = {}
    window = opened.get("window")
    if not isinstance(window, dict):
        window = {}
    try:
        canvas_width = int(canvas.get("width") or 0)
        canvas_height = int(canvas.get("height") or 0)
        x = int(window.get("x") or -1)
        y = int(window.get("y") or -1)
        width = int(window.get("width") or 0)
        height = int(window.get("height") or 0)
    except (TypeError, ValueError):
        canvas_width = canvas_height = width = height = 0
        x = y = -1

    canvas_ok = canvas_width == 512 and canvas_height >= 600
    add_check(checks, "world map mobile canvas dimensions", canvas_ok, repr(canvas))
    if not canvas_ok:
        failures.append("world map smoke must prove an iPhone portrait-style 512-wide canvas with useful height")

    geometry_ok = (
        opened.get("visible") is True
        and canvas_width > 0
        and canvas_height > 0
        and x >= 0
        and y >= 50
        and width >= int(canvas_width * 0.9)
        and height >= int(canvas_height * 0.78)
        and x <= 24
        and x + width <= canvas_width
        and y + height <= canvas_height
    )
    add_check(
        checks,
        "world map mobile modal near full canvas",
        geometry_ok,
        repr({"canvas": canvas, "window": window, "visible": opened.get("visible")}),
    )
    if not geometry_ok:
        failures.append(
            "world map smoke must prove the iPhone mobile modal is near-full-canvas "
            "(not the older centered 75% desktop-shaped dialog)"
        )

    search_center = opened.get("searchCenter")
    close_center = opened.get("closeCenter")
    controls_inside = True
    for label, point in (("searchCenter", search_center), ("closeCenter", close_center)):
        if not isinstance(point, dict):
            controls_inside = False
            break
        try:
            px = int(point.get("x") or -1)
            py = int(point.get("y") or -1)
        except (TypeError, ValueError):
            controls_inside = False
            break
        if not (x <= px <= x + width and y <= py <= y + height):
            controls_inside = False
            break
    add_check(
        checks,
        "world map mobile modal controls inside window",
        controls_inside,
        repr({"searchCenter": search_center, "closeCenter": close_center, "window": window}),
    )
    if not controls_inside:
        failures.append("world map smoke must prove search and close controls remain inside the mobile map window")

    pinch = world_map.get("pinchZoom")
    if not isinstance(pinch, dict):
        pinch = {}
    pinch_before = nested_dict(pinch, "before")
    pinch_after = nested_dict(pinch, "after")
    pinch_events = pinch.get("events")
    walker_after = nested_dict(pinch_after, "walker", "lastRequest")
    pinch_ok = (
        isinstance(pinch_events, list)
        and any(str(event).startswith("g,0,") for event in pinch_events)
        and any(str(event).startswith("g,3,") for event in pinch_events)
        and any(str(event).startswith("w,") for event in pinch_events)
        and pinch_before.get("zoom") != pinch_after.get("zoom")
        and int(walker_after.get("at") or 0) == 0
    )
    add_check(
        checks,
        "world map pinch zoom without walker request",
        pinch_ok,
        repr({
            "beforeZoom": pinch_before.get("zoom"),
            "afterZoom": pinch_after.get("zoom"),
            "events": pinch_events,
            "walkerAfter": walker_after,
        }),
    )
    if not pinch_ok:
        failures.append("world map smoke must prove pinch zoom changes zoom without issuing a walker request")

    walker = world_map.get("walker")
    if not isinstance(walker, dict):
        walker = {}
    success = nested_dict(walker, "success")
    route = nested_dict(success, "route")
    walker_ok = success.get("moved") is True and route.get("ok") is True and int(route.get("count") or 0) > 0
    add_check(
        checks,
        "world map walker route and movement",
        walker_ok,
        repr({"route": route, "moved": success.get("moved")}),
    )
    if not walker_ok:
        failures.append("world map smoke must prove a successful world-walker route and player movement")


def validate_local_preflight(
    summary_path: Path,
    package_manifest: dict[str, Any],
    checks: list[dict[str, Any]],
    failures: list[str],
    require_simulator_video: bool = False,
    min_simulator_video_seconds: float = 90,
) -> dict[str, Any]:
    if not summary_path.is_file():
        failures.append(f"local preflight summary not found: {summary_path}")
        add_check(checks, "local preflight summary exists", False, str(summary_path))
        return {}
    add_check(checks, "local preflight summary exists", True, str(summary_path))
    summary = load_json(summary_path, "local preflight summary", failures)
    if not summary:
        return {}

    passed = summary.get("passed") is True and int(summary.get("failureCount") or 0) == 0
    add_check(checks, "local preflight passed", passed, f"failureCount={summary.get('failureCount')!r}")
    if not passed:
        failures.append("local iPhone web preflight must pass before final release")

    steps = summary.get("steps")
    if not isinstance(steps, list):
        failures.append("local preflight summary steps must be a list")
        add_check(checks, "local preflight has steps", False)
        return summary
    by_name = {str(step.get("name") or ""): step for step in steps if isinstance(step, dict)}
    required_passed_steps = (
        "prerequisites",
        "controls-smoke",
        "login-smoke",
        "world-map-smoke",
        "chat-smoke",
        "https-wss-smoke",
        "package",
        "package-verify",
        "simulator",
    )
    for name in required_passed_steps:
        status = (by_name.get(name) or {}).get("status")
        ok = status == "passed"
        add_check(checks, f"local preflight step {name}", ok, f"status={status!r}")
        if not ok:
            failures.append(f"local preflight step {name} must be passed, got {status!r}")

    validate_controls_overlay_evidence(by_name, checks, failures)
    validate_world_map_mobile_modal_evidence(by_name, checks, failures)

    expected_package_sha = str(package_manifest.get("sha256") or "")
    package_step = by_name.get("package") or {}
    package_artifact = package_step.get("artifact")
    if isinstance(package_artifact, str) and package_artifact:
        preflight_manifest_path = Path(package_artifact) / "voidscape-web-build.json"
        if preflight_manifest_path.is_file():
            preflight_sha = sha256_file(preflight_manifest_path)
            same_package = bool(expected_package_sha) and preflight_sha == expected_package_sha
            add_check(
                checks,
                "local preflight package matches release package",
                same_package,
                f"{preflight_sha} vs {expected_package_sha}",
            )
            if not same_package:
                failures.append(
                    "local preflight package artifact must match the exact package directory being released"
                )
        else:
            failures.append(f"local preflight package manifest not found: {preflight_manifest_path}")
            add_check(checks, "local preflight package manifest exists", False, str(preflight_manifest_path))
    else:
        failures.append("local preflight package step must include an artifact directory")
        add_check(checks, "local preflight package artifact recorded", False)

    package_verify = by_name.get("package-verify") or {}
    artifact = package_verify.get("artifact")
    if isinstance(artifact, str) and artifact:
        artifact_summary = Path(artifact) / "summary.json"
        if artifact_summary.is_file():
            verifier = load_json(artifact_summary, "local package verifier summary", failures)
            verifier_expected_sha = str(verifier.get("expectedBuildManifestSha256") or "")
            verifier_hosted_sha = str(verifier.get("buildManifestSha256") or "")
            deep_ok = (
                verifier.get("buildManifestMatchesExpected") is True
                and bool(expected_package_sha)
                and verifier_expected_sha == expected_package_sha
                and verifier_hosted_sha == expected_package_sha
                and verifier.get("deepManifestChecked") is True
                and int(verifier.get("deepManifestFileCount") or 0) > 0
                and int(verifier.get("deepManifestVerifiedCount") or -1) == int(verifier.get("deepManifestFileCount") or 0)
                and int(verifier.get("deepManifestFailureCount") or 0) == 0
                and not verifier.get("failures")
            )
            add_check(
                checks,
                "local package verifier deep manifest",
                deep_ok,
                f"{verifier.get('deepManifestVerifiedCount')}/{verifier.get('deepManifestFileCount')}",
            )
            if not deep_ok:
                failures.append(
                    "local package verifier must include passing deep-manifest proof for the exact "
                    f"package directory being released: {artifact_summary}"
                )
        else:
            failures.append(f"local package verifier summary not found: {artifact_summary}")
            add_check(checks, "local package verifier summary exists", False, str(artifact_summary))
    else:
        failures.append("local preflight package-verify step must include an artifact directory")
        add_check(checks, "local package verifier artifact recorded", False)

    simulator = by_name.get("simulator") or {}
    simulator_artifact = simulator.get("artifact")
    if isinstance(simulator_artifact, str) and simulator_artifact:
        simulator_dir = Path(simulator_artifact)
        run_metadata_path = simulator_dir / "simulator-run.json"
        screenshot_checks_path = simulator_dir / "simulator-screenshot-checks.json"
        http_checks_path = simulator_dir / "simulator-http-checks.json"
        if run_metadata_path.is_file():
            run_metadata = load_json(run_metadata_path, "simulator run metadata", failures)
            release_settings = (
                ("schema", run_metadata.get("schema") == "voidscape.iphoneSimulatorRun.v1", run_metadata.get("schema")),
                ("diagnosticsMode", run_metadata.get("diagnosticsMode") is True, run_metadata.get("diagnosticsMode")),
                ("screenshotEnabled", run_metadata.get("screenshotEnabled") is True, run_metadata.get("screenshotEnabled")),
                ("orientationMatrix", run_metadata.get("orientationMatrix") is True, run_metadata.get("orientationMatrix")),
                ("remoteMode", run_metadata.get("remoteMode") is False, run_metadata.get("remoteMode")),
                ("openOnly", run_metadata.get("openOnly") is False, run_metadata.get("openOnly")),
                ("stableStatusBarApplied", run_metadata.get("stableStatusBarApplied") is True, run_metadata.get("stableStatusBarApplied")),
            )
            for name, ok, value in release_settings:
                add_check(checks, f"simulator run metadata {name}", ok, repr(value))
                if not ok:
                    failures.append(
                        f"simulator run metadata must report release preflight {name}; "
                        f"rerun check-web-teavm-iphone-release.sh with current simulator tooling"
                    )
            if require_simulator_video:
                record_video_ok = run_metadata.get("recordVideo") is True
                add_check(checks, "simulator run metadata video requested", record_video_ok, repr(run_metadata.get("recordVideo")))
                if not record_video_ok:
                    failures.append("simulator run metadata must report recordVideo: true when --require-simulator-video is used")
        else:
            failures.append(f"simulator run metadata not found: {run_metadata_path}")
            add_check(checks, "simulator run metadata exists", False, str(run_metadata_path))

        if screenshot_checks_path.is_file():
            screenshot_checks = load_json(screenshot_checks_path, "simulator screenshot checks", failures)
            entries = screenshot_checks.get("checks")
            screenshots_ok = isinstance(entries, list) and bool(entries) and all(
                isinstance(entry, dict) and entry.get("passed") is True for entry in entries
            )
            add_check(checks, "simulator screenshot checks passed", screenshots_ok, str(screenshot_checks_path))
            if not screenshots_ok:
                failures.append(f"simulator screenshot checks must all pass: {screenshot_checks_path}")
        else:
            failures.append(f"simulator screenshot checks not found: {screenshot_checks_path}")
            add_check(checks, "simulator screenshot checks exist", False, str(screenshot_checks_path))

        if http_checks_path.is_file():
            http_checks = load_json(http_checks_path, "simulator HTTP checks", failures)
            required = http_checks.get("required")
            failures_value = http_checks.get("failures")
            http_ok = (
                isinstance(required, list)
                and bool(required)
                and all(
                    isinstance(entry, dict)
                    and entry.get("matched") is True
                    and int(entry.get("status") or 0) == 200
                    for entry in required
                )
                and isinstance(failures_value, list)
                and not failures_value
            )
            add_check(checks, "simulator HTTP asset checks passed", http_ok, str(http_checks_path))
            if not http_ok:
                failures.append(f"simulator HTTP asset checks must pass: {http_checks_path}")
        else:
            failures.append(f"simulator HTTP checks not found: {http_checks_path}")
            add_check(checks, "simulator HTTP checks exist", False, str(http_checks_path))

        video_checks_path = simulator_dir / "simulator-video-checks.json"
        if video_checks_path.is_file():
            video_checks = load_json(video_checks_path, "simulator video checks", failures)
            video_failures = video_checks.get("failures")
            try:
                minimum_bytes = int(video_checks.get("minimumBytes") or 0)
            except (TypeError, ValueError):
                minimum_bytes = -1
            try:
                size_bytes = int(video_checks.get("sizeBytes") or 0)
            except (TypeError, ValueError):
                size_bytes = -1
            try:
                manual_seconds = float(video_checks.get("manualSeconds") or 0)
            except (TypeError, ValueError):
                manual_seconds = -1
            video_path_raw = video_checks.get("path")
            video_path = None
            video_exists_now = False
            actual_size_bytes = -1
            if isinstance(video_path_raw, str) and video_path_raw:
                video_path = Path(video_path_raw)
                if not video_path.is_absolute():
                    video_path = video_checks_path.parent / video_path
                video_exists_now = video_path.is_file()
                actual_size_bytes = video_path.stat().st_size if video_exists_now else -1
            video_ok = (
                video_checks.get("exists") is True
                and video_checks.get("quicktimeLike") is True
                and video_exists_now
                and actual_size_bytes == size_bytes
                and size_bytes >= max(minimum_bytes, 1)
                and isinstance(video_failures, list)
                and not video_failures
            )
            add_check(
                checks,
                "simulator video checks passed",
                video_ok,
                f"sizeBytes={video_checks.get('sizeBytes')!r}, actualSizeBytes={actual_size_bytes!r}",
            )
            if not video_ok:
                failures.append(f"simulator video checks must pass: {video_checks_path}")
            if require_simulator_video:
                duration_ok = manual_seconds >= min_simulator_video_seconds
                add_check(
                    checks,
                    "simulator video manual duration",
                    duration_ok,
                    f"manualSeconds={manual_seconds!r}, minimum={min_simulator_video_seconds!r}",
                )
                if not duration_ok:
                    failures.append(
                        "simulator video manualSeconds must be at least "
                        f"{min_simulator_video_seconds:g}, got {manual_seconds!r}"
                    )
        elif require_simulator_video:
            failures.append(f"simulator video checks not found: {video_checks_path}")
            add_check(checks, "simulator video checks exist", False, str(video_checks_path))
    else:
        failures.append("local preflight simulator step must include an artifact directory")
        add_check(checks, "simulator artifact recorded", False)

    return summary


def validate_package_manifest(
    package_dir: Path,
    deployment_summary: dict[str, Any],
    checks: list[dict[str, Any]],
    failures: list[str],
) -> dict[str, Any]:
    manifest_path = package_dir / "voidscape-web-build.json"
    if not manifest_path.is_file():
        failures.append(f"package manifest not found: {manifest_path}")
        add_check(checks, "package manifest exists", False, str(manifest_path))
        return {}
    add_check(checks, "package manifest exists", True, str(manifest_path))
    digest = sha256_file(manifest_path)
    manifest = load_json(manifest_path, "package manifest", failures)

    expected_sha = str(deployment_summary.get("expectedBuildManifestSha256") or "")
    hosted_sha = str(deployment_summary.get("buildManifestSha256") or "")
    expected_ok = expected_sha == digest
    hosted_ok = hosted_sha == digest
    add_check(checks, "deployment expected manifest matches package", expected_ok, f"{expected_sha} vs {digest}")
    add_check(checks, "hosted manifest hash matches package", hosted_ok, f"{hosted_sha} vs {digest}")
    if not expected_ok:
        failures.append("deployment summary expectedBuildManifestSha256 must match the package manifest being released")
    if not hosted_ok:
        failures.append("deployment summary buildManifestSha256 must match the package manifest being released")

    file_count = manifest.get("fileCount")
    files = manifest.get("files")
    count_ok = isinstance(files, list) and isinstance(file_count, int) and file_count == len(files) and file_count > 0
    add_check(checks, "package manifest file count", count_ok, f"fileCount={file_count!r}")
    if not count_ok:
        failures.append("package manifest must have a positive fileCount matching files length")

    verified_count = 0
    file_failures: list[str] = []
    if isinstance(files, list):
        for index, entry in enumerate(files):
            if not isinstance(entry, dict):
                file_failures.append(f"files[{index}] must be an object")
                continue
            raw_path = entry.get("path")
            expected_size = entry.get("size")
            expected_sha = entry.get("sha256")
            if not isinstance(raw_path, str) or not raw_path or "\\" in raw_path:
                file_failures.append(f"files[{index}] has invalid path {raw_path!r}")
                continue
            relative_path = PurePosixPath(raw_path)
            if relative_path.is_absolute() or ".." in relative_path.parts:
                file_failures.append(f"files[{index}] has unsafe path {raw_path!r}")
                continue
            if not isinstance(expected_size, int) or expected_size < 0:
                file_failures.append(f"{raw_path}: invalid size {expected_size!r}")
                continue
            if not isinstance(expected_sha, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
                file_failures.append(f"{raw_path}: invalid sha256 {expected_sha!r}")
                continue
            package_file = package_dir.joinpath(*relative_path.parts)
            if not package_file.is_file():
                file_failures.append(f"{raw_path}: package file missing")
                continue
            actual_size = package_file.stat().st_size
            if actual_size != expected_size:
                file_failures.append(f"{raw_path}: size {actual_size} != {expected_size}")
                continue
            actual_sha = sha256_file(package_file)
            if actual_sha != expected_sha:
                file_failures.append(f"{raw_path}: sha256 {actual_sha} != {expected_sha}")
                continue
            verified_count += 1
    files_verified_ok = count_ok and verified_count == file_count and not file_failures
    add_check(
        checks,
        "package manifest files verified",
        files_verified_ok,
        f"{verified_count}/{file_count!r}",
    )
    if file_failures:
        shown = "; ".join(file_failures[:8])
        extra = len(file_failures) - 8
        if extra > 0:
            shown = f"{shown}; ... {extra} more"
        failures.append(f"package manifest file verification failed: {shown}")
    elif not files_verified_ok:
        failures.append("package manifest files must all match their recorded size and SHA-256")

    return {
        "path": str(manifest_path),
        "sha256": digest,
        "fileCount": file_count,
        "verifiedFileCount": verified_count,
    }


def validate_deployment_summary(summary: dict[str, Any], checks: list[dict[str, Any]], failures: list[str]) -> None:
    base_url = str(summary.get("baseUrl") or "")
    parsed = urlparse(base_url)
    https_ok = parsed.scheme == "https" and bool(parsed.netloc)
    add_check(checks, "deployment baseUrl is HTTPS", https_ok, base_url)
    if not https_ok:
        failures.append(f"deployment baseUrl must be an absolute https:// URL, got {base_url!r}")

    production_flags = (
        ("allowHttp", "deployment verifier must not use --allow-http for final release"),
        ("insecureTls", "deployment verifier must not use --insecure for final release"),
        ("allowDebug", "deployment verifier must not use --allow-debug for final release"),
        ("allowInsecureWs", "deployment verifier must not use --allow-insecure-ws for final release"),
    )
    for key, message in production_flags:
        value = summary.get(key)
        ok = value is False
        add_check(checks, f"deployment production flag {key}", ok, repr(value))
        if not ok:
            failures.append(message)

    smoke_ok = summary.get("smokeRequested") is True
    add_check(checks, "deployment smoke requested", smoke_ok)
    if not smoke_ok:
        failures.append("deployment verifier summary must come from a --smoke run")
    smoke_ran_ok = summary.get("smokeRan") is True
    add_check(checks, "deployment smoke ran", smoke_ran_ok)
    if not smoke_ran_ok:
        failures.append("deployment verifier summary must report smokeRan: true")
    smoke_passed_ok = summary.get("smokePassed") is True
    add_check(checks, "deployment smoke passed", smoke_passed_ok)
    if not smoke_passed_ok:
        failures.append("deployment verifier summary must report smokePassed: true")

    failures_value = summary.get("failures")
    no_failures = isinstance(failures_value, list) and not failures_value
    add_check(checks, "deployment verifier failures empty", no_failures, repr(failures_value))
    if not no_failures:
        failures.append(f"deployment verifier failures must be empty, got {failures_value!r}")

    manifest_ok = summary.get("buildManifestMatchesExpected") is True
    add_check(checks, "deployment build manifest matched expected", manifest_ok)
    if not manifest_ok:
        failures.append("deployment verifier must report buildManifestMatchesExpected: true")

    deep_ok = (
        summary.get("deepManifestRequested") is True
        and summary.get("deepManifestChecked") is True
        and int(summary.get("deepManifestFileCount") or 0) > 0
        and int(summary.get("deepManifestVerifiedCount") or -1) == int(summary.get("deepManifestFileCount") or 0)
        and int(summary.get("deepManifestFailureCount") or 0) == 0
    )
    add_check(
        checks,
        "deployment deep manifest passed",
        deep_ok,
        f"{summary.get('deepManifestVerifiedCount')}/{summary.get('deepManifestFileCount')}",
    )
    if not deep_ok:
        failures.append("deployment verifier must report passing deep-manifest proof")

    try:
        cache_policy_failure_count = int(summary.get("cachePolicyFailureCount"))
    except (TypeError, ValueError):
        cache_policy_failure_count = -1
    cache_policy_ok = summary.get("cachePolicyChecked") is True and cache_policy_failure_count == 0
    add_check(
        checks,
        "deployment cache policy passed",
        cache_policy_ok,
        f"failureCount={summary.get('cachePolicyFailureCount')!r}",
    )
    if not cache_policy_ok:
        failures.append("deployment verifier must report passing production Cache-Control policy proof")


def run_qa_validator(report_path: Path, checks: list[dict[str, Any]], failures: list[str]) -> dict[str, Any]:
    validator = ROOT / "scripts" / "validate-web-teavm-iphone-qa-report.py"
    command = [sys.executable, str(validator), str(report_path)]
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    ok = result.returncode == 0
    add_check(checks, "physical iPhone QA report validator", ok, f"exitCode={result.returncode}")
    if not ok:
        failures.append("physical iPhone QA report validator failed")
    return {
        "command": command,
        "exitCode": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit final Voidscape iPhone web release evidence.")
    parser.add_argument(
        "--qa-report",
        default=str(ROOT / "tmp" / "iphone-web-qa" / "iphone-safari-qa-report.md"),
        help="Filled physical iPhone QA report. Default: tmp/iphone-web-qa/iphone-safari-qa-report.md",
    )
    parser.add_argument(
        "--local-preflight",
        default=str(ROOT / "tmp" / "web-teavm-iphone-release-preflight" / "summary.json"),
        help="Local automated preflight summary.json. Default: tmp/web-teavm-iphone-release-preflight/summary.json",
    )
    parser.add_argument(
        "--package-dir",
        default=str(ROOT / "dist" / "web-teavm"),
        help="Exact static package directory uploaded to production. Default: dist/web-teavm",
    )
    parser.add_argument(
        "--out",
        default=str(ROOT / "tmp" / "web-teavm-iphone-final-release-audit"),
        help="Output directory for final-release-audit-summary.json.",
    )
    parser.add_argument(
        "--require-simulator-video",
        action="store_true",
        help=(
            "Require the local preflight simulator artifact to include passing simulator-video-checks.json. "
            "Use this for iPhone UI/control changes before physical iPhone QA."
        ),
    )
    parser.add_argument(
        "--min-simulator-video-seconds",
        type=float,
        default=90,
        help=(
            "Minimum simulator manualSeconds accepted when --require-simulator-video is set. "
            "Default: 90."
        ),
    )
    args = parser.parse_args()
    if args.min_simulator_video_seconds < 0:
        parser.error("--min-simulator-video-seconds must be non-negative")

    qa_report = Path(args.qa_report).expanduser().resolve()
    local_preflight = Path(args.local_preflight).expanduser().resolve()
    package_dir = Path(args.package_dir).expanduser().resolve()
    out_dir = Path(args.out).expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    checks: list[dict[str, Any]] = []
    failures: list[str] = []

    if not qa_report.is_file():
        failures.append(f"QA report not found: {qa_report}")
        add_check(checks, "QA report exists", False, str(qa_report))
        deployment_summary: dict[str, Any] = {}
        qa_validator_result = {"command": [], "exitCode": 1, "stdout": "", "stderr": "QA report not found"}
    else:
        add_check(checks, "QA report exists", True, str(qa_report))
        deployment_summary = extract_deployment_summary(qa_report, failures)
        qa_validator_result = run_qa_validator(qa_report, checks, failures)

    if deployment_summary:
        validate_deployment_summary(deployment_summary, checks, failures)
    package_manifest = validate_package_manifest(package_dir, deployment_summary, checks, failures)
    local_summary = validate_local_preflight(
        local_preflight,
        package_manifest,
        checks,
        failures,
        require_simulator_video=args.require_simulator_video,
        min_simulator_video_seconds=args.min_simulator_video_seconds,
    )

    summary = {
        "createdAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "passed": not failures,
        "qaReport": str(qa_report),
        "localPreflightSummary": str(local_preflight),
        "packageDir": str(package_dir),
        "requireSimulatorVideo": args.require_simulator_video,
        "minSimulatorVideoSeconds": args.min_simulator_video_seconds,
        "packageManifest": package_manifest,
        "deployment": {
            "baseUrl": deployment_summary.get("baseUrl"),
            "wsUrl": deployment_summary.get("wsUrl"),
            "smokeRequested": deployment_summary.get("smokeRequested"),
            "smokeRan": deployment_summary.get("smokeRan"),
            "smokePassed": deployment_summary.get("smokePassed"),
            "smokeSummary": deployment_summary.get("smokeSummary"),
            "buildManifestSha256": deployment_summary.get("buildManifestSha256"),
            "expectedBuildManifestSha256": deployment_summary.get("expectedBuildManifestSha256"),
            "buildManifestMatchesExpected": deployment_summary.get("buildManifestMatchesExpected"),
            "deepManifestFileCount": deployment_summary.get("deepManifestFileCount"),
            "deepManifestVerifiedCount": deployment_summary.get("deepManifestVerifiedCount"),
            "deepManifestFailureCount": deployment_summary.get("deepManifestFailureCount"),
            "cachePolicyChecked": deployment_summary.get("cachePolicyChecked"),
            "cachePolicyFailureCount": deployment_summary.get("cachePolicyFailureCount"),
        },
        "localPreflight": {
            "passed": local_summary.get("passed"),
            "failureCount": local_summary.get("failureCount"),
            "outDir": local_summary.get("outDir"),
        },
        "qaValidator": qa_validator_result,
        "checks": checks,
        "failures": failures,
    }

    summary_path = out_dir / "final-release-audit-summary.json"
    summary_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}", file=sys.stderr)
        print(f"iPhone web final release audit failed: {summary_path}", file=sys.stderr)
        return 1

    print("iPhone web final release audit passed.")
    print(f"  Summary: {summary_path}")
    print(f"  QA report: {qa_report}")
    print(f"  Package: {package_dir}")
    print(f"  Local preflight: {local_preflight}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
