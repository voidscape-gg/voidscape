#!/usr/bin/env python3
"""Validate a filled Voidscape iPhone Safari QA report."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urljoin, urlparse, urlunparse


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def nested(data: dict[str, Any], path: str) -> Any:
    current: Any = data
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def parse_diagnostics_json(raw: str, label: str) -> dict[str, Any]:
    raw = raw.strip()
    if not raw:
        fail(f"{label} JSON block is empty")
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        fail(f"{label} JSON is invalid: {exc}")
    if not isinstance(parsed, dict):
        fail(f"{label} JSON must be an object")
    snapshot = parsed.get("snapshot", parsed)
    if not isinstance(snapshot, dict):
        fail(f"{label} JSON must contain an object snapshot")
    return {"report": parsed, "snapshot": snapshot}


def parse_json_object(raw: str, label: str) -> dict[str, Any]:
    raw = raw.strip()
    if not raw:
        fail(f"{label} JSON block is empty")
    try:
        parsed = json.loads(raw)
    except json.JSONDecodeError as exc:
        fail(f"{label} JSON is invalid: {exc}")
    if not isinstance(parsed, dict):
        fail(f"{label} JSON must be an object")
    return parsed


def extract_json_block(text: str, label: str = "diagnostics") -> dict[str, Any]:
    match = re.search(r"```json\s*(.*?)\s*```", text, re.DOTALL | re.IGNORECASE)
    if not match:
        fail(f"report does not contain a fenced ```json {label} block")
    return parse_diagnostics_json(match.group(1), label)


def section_text(text: str, heading: str) -> str | None:
    heading_match = re.search(rf"^##\s+{re.escape(heading)}\s*$", text, re.MULTILINE)
    if not heading_match:
        return None
    section = text[heading_match.end():]
    next_heading = re.search(r"^##\s+", section, re.MULTILINE)
    if next_heading:
        section = section[:next_heading.start()]
    return section


def extract_section_json_block(text: str, heading: str, label: str) -> dict[str, Any]:
    section = section_text(text, heading)
    if section is None:
        fail(f"report is missing required section: ## {heading}")
    match = re.search(r"```json\s*(.*?)\s*```", section, re.DOTALL | re.IGNORECASE)
    if not match:
        fail(f"section ## {heading} does not contain a fenced ```json {label} block")
    return parse_diagnostics_json(match.group(1), label)


def extract_section_json_object(
    text: str,
    heading: str,
    label: str,
    required: bool = True,
) -> dict[str, Any] | None:
    section = section_text(text, heading)
    if section is None:
        if required:
            fail(f"report is missing required section: ## {heading}")
        return None
    match = re.search(r"```json\s*(.*?)\s*```", section, re.DOTALL | re.IGNORECASE)
    if not match:
        if required:
            fail(f"section ## {heading} does not contain a fenced ```json {label} block")
        return None
    raw = match.group(1).strip()
    if not raw and not required:
        return None
    return parse_json_object(raw, label)


def diagnostics_url(text: str) -> str:
    match = re.search(r"- iPhone diagnostics URL: <([^>]+)>", text)
    return match.group(1) if match else ""


def default_port_for_scheme(scheme: str) -> int:
    if scheme == "https" or scheme == "wss":
        return 443
    if scheme == "http":
        return 43496
    return 80


def host_for_url(host: str) -> str:
    if ":" in host and not host.startswith("["):
        return f"[{host}]"
    return host


def resolve_http_url(value: str, page_url: str) -> str:
    absolute = urljoin(page_url, value)
    parsed = urlparse(absolute)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        fail(f"portal URL must resolve to http(s): {value}")
    return urlunparse(parsed)


def portal_url_from_base(value: str, page_url: str, fragment: str) -> str:
    parsed = urlparse(resolve_http_url(value or "/", page_url))
    return urlunparse(parsed._replace(fragment=fragment))


def portal_registration_url_from_base(value: str, page_url: str) -> str:
    parsed = urlparse(resolve_http_url(value or "/", page_url))
    return urlunparse(parsed._replace(query="auth=register", fragment=""))


def infer_expected_from_diagnostics_url(url: str) -> dict[str, Any]:
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname:
        fail("iPhone diagnostics URL must be an absolute http(s) URL")

    params = parse_qs(parsed.query)
    ws_values = params.get("ws") or []
    if ws_values:
        ws = ws_values[0]
        ws_parsed = urlparse(ws)
        if ws_parsed.scheme not in ("ws", "wss") or not ws_parsed.netloc:
            fail(f"diagnostics URL has invalid ws endpoint: {ws}")
        expected: dict[str, Any] = {
            "effective_ws": ws,
            "endpoint": {
                "mode": "ws",
                "source": "query",
                "ws": ws,
            },
        }
    else:
        host_values = params.get("host") or []
        port_values = params.get("port") or []
        if host_values or port_values:
            host = host_values[0] if host_values else parsed.hostname
            port = int(port_values[0] if port_values else default_port_for_scheme(parsed.scheme))
            ws_scheme = "wss" if parsed.scheme == "https" else "ws"
            expected = {
                "effective_ws": f"{ws_scheme}://{host_for_url(host)}:{port}/",
                "endpoint": {
                    "mode": "hostport",
                    "source": "query",
                    "host": host,
                    "port": port,
                },
            }
        else:
            host = parsed.hostname
            if parsed.scheme == "https":
                port = parsed.port or 443
                ws_scheme = "wss"
            else:
                port = 43496
                ws_scheme = "ws"
            expected = {
                "effective_ws": f"{ws_scheme}://{host_for_url(host)}:{port}/",
                "endpoint": {
                    "mode": "default",
                    "source": "default",
                    "host": host,
                    "port": port,
                },
            }

    page_url = urlunparse(parsed._replace(query="", fragment=""))
    portal_values = params.get("portal") or params.get("portalUrl") or []
    account_values = params.get("portalAccountUrl") or params.get("accountUrl") or []
    recovery_values = params.get("portalRecoveryUrl") or params.get("recoveryUrl") or []
    portal_base = portal_values[0] if portal_values else "/"
    expected["portal"] = {
        "accountUrl": resolve_http_url(account_values[0], page_url)
        if account_values else portal_registration_url_from_base(portal_base, page_url),
        "recoveryUrl": resolve_http_url(recovery_values[0], page_url)
        if recovery_values else portal_url_from_base(portal_base, page_url, "security"),
    }
    return expected


def extract_expected(text: str) -> dict[str, Any]:
    diag_url = diagnostics_url(text)
    if not diag_url:
        fail("report is missing the iPhone diagnostics URL")

    expected = infer_expected_from_diagnostics_url(diag_url)
    match = re.search(r'`effectiveWs:\s*"([^"]+)"`', text)
    if match:
        expected["effective_ws"] = match.group(1)
    account_match = re.search(r'`portal\.accountUrl:\s*"([^"]+)"`', text)
    recovery_match = re.search(r'`portal\.recoveryUrl:\s*"([^"]+)"`', text)
    if account_match or recovery_match:
        portal = dict(expected.get("portal") or {})
        if account_match:
            portal["accountUrl"] = account_match.group(1)
        if recovery_match:
            portal["recoveryUrl"] = recovery_match.group(1)
        expected["portal"] = portal
    return expected


def checked_items(text: str) -> tuple[int, int]:
    total = len(re.findall(r"^- \[[ xX]\] ", text, re.MULTILINE))
    checked = len(re.findall(r"^- \[[xX]\] ", text, re.MULTILINE))
    return checked, total


def report_field(text: str, label: str) -> str:
    match = re.search(rf"^- {re.escape(label)}:\s*(.*?)\s*$", text, re.MULTILINE)
    return match.group(1).strip() if match else ""


def field_is_filled(value: str) -> bool:
    normalized = value.strip().lower()
    return bool(normalized) and normalized not in {"n/a", "na", "none", "todo", "tbd", "unknown", "-"}


REQUIRED_CUSTOM_HUD_MESSAGE_TABS = ("ALL", "CHAT", "QUEST", "PRIVATE")
REQUIRED_CUSTOM_HUD_TOP_TABS = (1, 2, 3, 4, 5, 6)
REQUIRED_MOBILE_PANEL_SHORTCUTS = ("inventory", "map", "magic", "prayer", "skills", "quests", "friends", "options")
REQUIRED_MOBILE_CHAT_SHORTCUTS = ("all", "chat", "quest", "private", "compose")

REQUIRED_CHECKLIST_ITEMS = (
    "After Home Screen background/resume, tap-to-move or chat still works and copied diagnostics record post-resume input plus movement in `postResumeProof`.",
    "Voidscape canvas top tabs open every real shared panel, hidden DOM drawer controls stay hidden in normal play, and copied diagnostics record those changes in `uiHistory`.",
    "Canvas chat owns the visible chat frame/tabs, hidden DOM chat tray controls stay hidden in normal play, and `Aa` opens the keyboard without leaving duplicate chat launchers on screen.",
    "Compact Safari `...` and `Aa` helpers stay reachable in normal portrait play without feeling like a second HUD.",
    "In-game diagnostics `i` and `copy` controls stay reachable in portrait and landscape and do not cover the active gameplay HUD.",
    "Copied diagnostics include both `controlsHistory.portrait` and `controlsHistory.landscape` after rotating through both orientations.",
    "After sending public chat with the keyboard still open, browser Back closes only the keyboard first; a second browser Back reaches shared mobile Back.",
    "Blocking-dialog diagnostics were copied while the welcome/wilderness modal was open and show `ui.blockingDialog: true` with a non-empty `ui.blockingDialogName`.",
    "Welcome/wilderness modal text and close button are not covered by web overlay controls.",
    "Canvas-tab-opened shared panels can be swipe-scrolled by touch, and copied diagnostics record a nonzero scrollable panel gesture in `scrollHistory`; bank/settings/friends/chat panels can also be swipe-scrolled where available.",
    "World map opens, pans, zooms, searches, and world-walks without the `...` or `Aa` helpers covering the map/search/walker controls.",
    "World-map pinch zooms in/out and does not start world-walking unless you intentionally tap a destination.",
    "With world-map search focused, browser Back closes the search field while leaving the map open; search text does not leak into chat.",
    "World-map diagnostics were copied while the map was open after pan/zoom/search/world-walk, and show a near-full-canvas mobile map window with search/close controls inside it.",
)


def numeric_rect(value: Any, label: str, errors: list[str]) -> dict[str, int] | None:
    if not isinstance(value, dict):
        errors.append(f"{label}.rect must be an object")
        return None
    rect: dict[str, int] = {}
    for key in ("left", "top", "right", "bottom", "width", "height"):
        raw = value.get(key)
        if not isinstance(raw, (int, float)):
            errors.append(f"{label}.rect.{key} must be numeric, got {raw!r}")
            return None
        rect[key] = int(raw)
    if rect["width"] <= 0 or rect["height"] <= 0:
        errors.append(f"{label}.rect should have nonzero size, got {rect!r}")
    if rect["right"] <= rect["left"] or rect["bottom"] <= rect["top"]:
        errors.append(f"{label}.rect edges are inconsistent, got {rect!r}")
    return rect


def control_rect(
    controls: dict[str, Any],
    key: str,
    expected_display: str,
    errors: list[str],
    label_prefix: str = "controls",
) -> dict[str, int] | None:
    value = controls.get(key)
    label = f"{label_prefix}.{key}"
    if not isinstance(value, dict):
        errors.append(f"{label} must be an object")
        return None
    if value.get("present") is not True:
        errors.append(f"{label}.present must be true")
    if value.get("visible") is not True:
        errors.append(f"{label}.visible must be true")
    if value.get("display") != expected_display:
        errors.append(f"{label}.display should be {expected_display!r}, got {value.get('display')!r}")
    if value.get("visibility") == "hidden":
        errors.append(f"{label}.visibility must not be hidden")
    if value.get("pointerEvents") == "none":
        errors.append(f"{label}.pointerEvents must not be none")
    if value.get("hitSelf") is not True:
        errors.append(f"{label}.hitSelf must be true, got {value.get('hitSelf')!r} hit={value.get('hitSelector')!r}")
    return numeric_rect(value.get("rect"), label, errors)


def viewport_bounds(viewport: dict[str, Any]) -> tuple[int, int, int, int]:
    viewport_width = int(viewport.get("innerWidth") or 0)
    viewport_height = int(viewport.get("innerHeight") or 0)
    visual = viewport.get("visualViewport")
    if isinstance(visual, dict):
        visual_width = int(visual.get("width") or 0)
        visual_height = int(visual.get("height") or 0)
        if visual_width > 0 and visual_height > 0:
            left = int(visual.get("offsetLeft") or 0)
            top = int(visual.get("offsetTop") or 0)
            return left, top, left + visual_width, top + visual_height
    return 0, 0, viewport_width, viewport_height


def rect_inside_viewport(rect: dict[str, int], bounds: tuple[int, int, int, int], label: str, errors: list[str]) -> None:
    left, top, right, bottom = bounds
    if rect["left"] < left or rect["top"] < top or rect["right"] > right or rect["bottom"] > bottom:
        errors.append(f"{label} should fit inside visible viewport bounds {bounds!r}, got {rect!r}")


def rects_overlap(a: dict[str, int], b: dict[str, int]) -> bool:
    return a["left"] < b["right"] and a["right"] > b["left"] and a["top"] < b["bottom"] and a["bottom"] > b["top"]


def no_overlap(a: dict[str, int] | None, b: dict[str, int] | None, label: str, errors: list[str]) -> None:
    if a is None or b is None:
        return
    if rects_overlap(a, b):
        errors.append(f"{label} should not overlap, got {a!r} vs {b!r}")


def min_rect_size(rect: dict[str, int] | None, min_width: int, min_height: int, label: str, errors: list[str]) -> None:
    if rect is None:
        return
    if rect["width"] < min_width or rect["height"] < min_height:
        errors.append(f"{label} should be at least {min_width}x{min_height}, got {rect!r}")


def require_shortcut_controls(
    controls: dict[str, Any],
    key: str,
    attr: str,
    expected: tuple[str, ...],
    errors: list[str],
    label_prefix: str,
) -> None:
    entries = controls.get(key)
    label = f"{label_prefix}.{key}"
    if not isinstance(entries, list):
        errors.append(f"{label} must be a list")
        return
    selectors = []
    for index, entry in enumerate(entries):
        if not isinstance(entry, dict):
            errors.append(f"{label}[{index}] must be an object")
            continue
        selector = str(entry.get("selector") or "")
        selectors.append(selector)
        if entry.get("present") is not True:
            errors.append(f"{label}[{index}].present must be true for {selector!r}")
        if entry.get("display") != "flex":
            errors.append(f"{label}[{index}].display should be 'flex', got {entry.get('display')!r}")
    for value in expected:
        expected_selector = f'data-{attr}="{value}"'
        if not any(expected_selector in selector for selector in selectors):
            errors.append(f"{label} must include shortcut {value!r}, got selectors {selectors!r}")


def chat_access_mode_from_ui(ui: Any) -> str:
    if isinstance(ui, dict):
        mode = ui.get("chatAccessMode")
        if isinstance(mode, str) and mode:
            return mode
    return "canvas"


def panel_access_mode_from_ui(ui: Any) -> str:
    if isinstance(ui, dict):
        mode = ui.get("panelAccessMode")
        if isinstance(mode, str) and mode:
            return mode
    return "canvas"


def validate_control_set(
    controls: Any,
    viewport: Any,
    errors: list[str],
    label_prefix: str = "controls",
    chat_access_mode: str = "canvas",
    panel_access_mode: str = "canvas",
) -> None:
    if not isinstance(controls, dict):
        errors.append(f"{label_prefix} must be an object with mobile overlay rectangles")
        return
    if not isinstance(viewport, dict):
        errors.append(f"{label_prefix} viewport must be an object")
        return
    viewport_width = int(viewport.get("innerWidth") or 0)
    viewport_height = int(viewport.get("innerHeight") or 0)
    if viewport_width <= 0 or viewport_height <= 0:
        return
    bounds = viewport_bounds(viewport)

    rects = {
        "keyboardButton": control_rect(controls, "keyboardButton", "flex", errors, label_prefix),
        "actionButton": control_rect(controls, "actionButton", "flex", errors, label_prefix),
        "cameraControls": control_rect(controls, "cameraControls", "grid", errors, label_prefix),
        "diagnosticsButton": control_rect(controls, "diagnosticsButton", "flex", errors, label_prefix),
        "diagnosticsCopyButton": control_rect(controls, "diagnosticsCopyButton", "flex", errors, label_prefix),
    }
    if panel_access_mode in {"canvas", "canvas-rail"}:
        hidden_control_rect(controls, "panelButton", errors, label_prefix)
        rects["panelButton"] = None
    else:
        rects["panelButton"] = control_rect(controls, "panelButton", "flex", errors, label_prefix)
    if chat_access_mode == "collapsed-helper":
        rects["chatButton"] = control_rect(controls, "chatButton", "flex", errors, label_prefix)
    else:
        hidden_control_rect(controls, "chatButton", errors, label_prefix)
        rects["chatButton"] = None
    hidden_control_rect(controls, "panelDrawer", errors, label_prefix)
    hidden_control_rect(controls, "chatTray", errors, label_prefix)
    require_shortcut_controls(controls, "panelButtons", "panel", REQUIRED_MOBILE_PANEL_SHORTCUTS, errors, label_prefix)
    require_shortcut_controls(controls, "chatButtons", "chat", REQUIRED_MOBILE_CHAT_SHORTCUTS, errors, label_prefix)
    for key, rect in rects.items():
        if rect is not None:
            rect_inside_viewport(rect, bounds, f"{label_prefix}.{key}", errors)

    for key in ("keyboardButton", "actionButton", "panelButton", "chatButton", "diagnosticsButton", "diagnosticsCopyButton"):
        min_rect_size(rects.get(key), 44, 44, f"{label_prefix}.{key}", errors)
    min_rect_size(rects.get("cameraControls"), 64, 80, f"{label_prefix}.cameraControls", errors)

    bottom_hud_reserve = 72 if viewport_width > viewport_height else 84
    for key in ("keyboardButton", "actionButton", "panelButton", "chatButton", "cameraControls", "diagnosticsButton", "diagnosticsCopyButton"):
        rect = rects.get(key)
        if rect is not None and rect["bottom"] >= viewport_height - bottom_hud_reserve:
            errors.append(
                f"{label_prefix}.{key} should stay above bottom HUD reserve {bottom_hud_reserve}px, "
                f"got {rect!r} in viewport {viewport_width}x{viewport_height}"
            )

    top_custom_hud_reserve = 72
    for key in ("diagnosticsButton", "diagnosticsCopyButton"):
        rect = rects.get(key)
        if rect is not None and rect["top"] < top_custom_hud_reserve:
            errors.append(
                f"{label_prefix}.{key} should stay below top custom HUD reserve {top_custom_hud_reserve}px, "
                f"got {rect!r}"
            )

    pairs = (
        ("keyboardButton", "actionButton", "keyboard/action controls"),
        ("keyboardButton", "panelButton", "keyboard/panel controls"),
        ("actionButton", "panelButton", "action/panel controls"),
        ("keyboardButton", "chatButton", "keyboard/chat controls"),
        ("actionButton", "chatButton", "action/chat controls"),
        ("panelButton", "chatButton", "panel/chat controls"),
        ("cameraControls", "actionButton", "camera/action controls"),
        ("cameraControls", "keyboardButton", "camera/keyboard controls"),
        ("cameraControls", "panelButton", "camera/panel controls"),
        ("cameraControls", "chatButton", "camera/chat controls"),
        ("cameraControls", "diagnosticsButton", "camera/diagnostics controls"),
        ("cameraControls", "diagnosticsCopyButton", "camera/diagnostics copy controls"),
        ("diagnosticsButton", "actionButton", "diagnostics/action controls"),
        ("diagnosticsButton", "keyboardButton", "diagnostics/keyboard controls"),
        ("diagnosticsButton", "panelButton", "diagnostics/panel controls"),
        ("diagnosticsButton", "chatButton", "diagnostics/chat controls"),
        ("diagnosticsCopyButton", "actionButton", "diagnostics copy/action controls"),
        ("diagnosticsCopyButton", "keyboardButton", "diagnostics copy/keyboard controls"),
        ("diagnosticsCopyButton", "panelButton", "diagnostics copy/panel controls"),
        ("diagnosticsCopyButton", "chatButton", "diagnostics copy/chat controls"),
        ("diagnosticsCopyButton", "diagnosticsButton", "diagnostics controls"),
    )
    for first, second, label in pairs:
        no_overlap(rects.get(first), rects.get(second), f"{label_prefix} {label}", errors)


def hidden_control_rect(
    controls: dict[str, Any],
    key: str,
    errors: list[str],
    label_prefix: str,
) -> None:
    value = controls.get(key)
    label = f"{label_prefix}.{key}"
    if not isinstance(value, dict):
        errors.append(f"{label} must be an object")
        return
    if value.get("present") is not True:
        errors.append(f"{label}.present must be true")
    if value.get("display") != "none":
        errors.append(f"{label}.display should be 'none' while this web fallback/control is hidden, got {value.get('display')!r}")
    if value.get("visible") is not False:
        errors.append(f"{label}.visible should be false while this web fallback/control is hidden, got {value.get('visible')!r}")
    rect = value.get("rect")
    if not isinstance(rect, dict):
        errors.append(f"{label}.rect must be an object")
        return
    for key_name in ("left", "top", "right", "bottom", "width", "height"):
        raw = rect.get(key_name)
        if not isinstance(raw, (int, float)):
            errors.append(f"{label}.rect.{key_name} must be numeric, got {raw!r}")
            return
    if int(rect.get("width") or 0) != 0 or int(rect.get("height") or 0) != 0:
        errors.append(f"{label}.rect should have zero size while display:none, got {rect!r}")


def validate_dialog_control_set(
    controls: Any,
    viewport: Any,
    errors: list[str],
    label_prefix: str = "blockingDialog.controls",
) -> None:
    if not isinstance(controls, dict):
        errors.append(f"{label_prefix} must be an object with mobile overlay rectangles")
        return
    if not isinstance(viewport, dict):
        errors.append(f"{label_prefix} viewport must be an object")
        return
    viewport_width = int(viewport.get("innerWidth") or 0)
    viewport_height = int(viewport.get("innerHeight") or 0)
    if viewport_width <= 0 or viewport_height <= 0:
        errors.append(f"{label_prefix} viewport should have nonzero size, got {viewport!r}")
        return

    bounds = viewport_bounds(viewport)
    rects = {
        "keyboardButton": control_rect(controls, "keyboardButton", "flex", errors, label_prefix),
        "actionButton": control_rect(controls, "actionButton", "flex", errors, label_prefix),
        "diagnosticsButton": control_rect(controls, "diagnosticsButton", "flex", errors, label_prefix),
        "diagnosticsCopyButton": control_rect(controls, "diagnosticsCopyButton", "flex", errors, label_prefix),
    }
    hidden_control_rect(controls, "cameraControls", errors, label_prefix)
    hidden_control_rect(controls, "panelButton", errors, label_prefix)
    hidden_control_rect(controls, "panelDrawer", errors, label_prefix)
    hidden_control_rect(controls, "chatButton", errors, label_prefix)
    hidden_control_rect(controls, "chatTray", errors, label_prefix)

    for key, rect in rects.items():
        if rect is not None:
            rect_inside_viewport(rect, bounds, f"{label_prefix}.{key}", errors)
            min_rect_size(rect, 44, 44, f"{label_prefix}.{key}", errors)

    bottom_hud_reserve = 72 if viewport_width > viewport_height else 84
    for key, rect in rects.items():
        if rect is not None and rect["bottom"] >= viewport_height - bottom_hud_reserve:
            errors.append(
                f"{label_prefix}.{key} should stay above bottom HUD reserve {bottom_hud_reserve}px "
                f"while a blocking dialog is open, got {rect!r} in viewport {viewport_width}x{viewport_height}"
            )

    pairs = (
        ("keyboardButton", "actionButton", "keyboard/action controls"),
        ("diagnosticsButton", "actionButton", "diagnostics/action controls"),
        ("diagnosticsButton", "keyboardButton", "diagnostics/keyboard controls"),
        ("diagnosticsCopyButton", "actionButton", "diagnostics copy/action controls"),
        ("diagnosticsCopyButton", "keyboardButton", "diagnostics copy/keyboard controls"),
        ("diagnosticsCopyButton", "diagnosticsButton", "diagnostics controls"),
    )
    for first, second, label in pairs:
        no_overlap(rects.get(first), rects.get(second), f"{label_prefix} {label}", errors)


def validate_blocking_dialog_snapshot(snapshot: dict[str, Any], errors: list[str], allow_browser_mode: bool) -> None:
    if snapshot.get("touchProfile") is not True:
        errors.append("blocking-dialog diagnostics touchProfile must be true")
    if snapshot.get("inGame") is not True:
        errors.append("blocking-dialog diagnostics must be copied after login while a game modal is open")
    if snapshot.get("standalone") is not True and not allow_browser_mode:
        errors.append("blocking-dialog diagnostics standalone must be true; copy it from the Home Screen launch")
    body_classes = set(str(snapshot.get("bodyClass") or "").split())
    if "dialog-open" not in body_classes:
        errors.append(f"blocking-dialog diagnostics bodyClass must include dialog-open, got {snapshot.get('bodyClass')!r}")
    href = str(snapshot.get("href") or "")
    diagnostics = snapshot.get("diagnostics")
    if not isinstance(diagnostics, dict):
        errors.append("blocking-dialog diagnostics diagnostics must be an object")
    else:
        if diagnostics.get("enabled") is not True:
            errors.append(f"blocking-dialog diagnostics.enabled must be true, got {diagnostics!r}")
        if not allow_browser_mode:
            if diagnostics.get("source") != "stored":
                errors.append(
                    f"blocking-dialog diagnostics.source must be 'stored' from the Home Screen launch, got {diagnostics.get('source')!r}"
                )
            if diagnostics.get("stored") is not True:
                errors.append(f"blocking-dialog diagnostics.stored must be true, got {diagnostics!r}")
            if "diag=1" in href or "debug=1" in href:
                errors.append(f"blocking-dialog diagnostics href should come from the manifest/Home Screen URL without diag=1/debug=1, got {href!r}")

    ui = snapshot.get("ui")
    if not isinstance(ui, dict):
        errors.append("blocking-dialog diagnostics ui must be an object")
    else:
        if ui.get("blockingDialog") is not True:
            errors.append(f"blocking-dialog diagnostics ui.blockingDialog must be true, got {ui.get('blockingDialog')!r}")
        dialog_name = ui.get("blockingDialogName")
        if not isinstance(dialog_name, str) or not dialog_name.strip():
            errors.append(f"blocking-dialog diagnostics ui.blockingDialogName must be a non-empty string, got {dialog_name!r}")
        if ui.get("webBuild") is not True:
            errors.append(f"blocking-dialog diagnostics ui.webBuild must be true, got {ui!r}")
        if ui.get("androidProfile") is not True:
            errors.append(f"blocking-dialog diagnostics ui.androidProfile must be true, got {ui!r}")
        if ui.get("customUi") is not True:
            errors.append(f"blocking-dialog diagnostics ui.customUi must be true, got {ui!r}")

    viewport = snapshot.get("viewport")
    validate_dialog_control_set(snapshot.get("controls"), viewport, errors)


def point_inside_rect(point: Any, rect: dict[str, int], label: str, errors: list[str]) -> None:
    if not isinstance(point, dict):
        errors.append(f"{label} must be an object")
        return
    try:
        x = int(point.get("x"))
        y = int(point.get("y"))
    except (TypeError, ValueError):
        errors.append(f"{label}.x/y must be numeric, got {point!r}")
        return
    if not (rect["left"] <= x <= rect["right"] and rect["top"] <= y <= rect["bottom"]):
        errors.append(f"{label} should be inside world-map window {rect!r}, got {point!r}")


def validate_world_map_snapshot(snapshot: dict[str, Any], errors: list[str], allow_browser_mode: bool) -> None:
    if snapshot.get("touchProfile") is not True:
        errors.append("world-map diagnostics touchProfile must be true")
    if snapshot.get("inGame") is not True:
        errors.append("world-map diagnostics must be copied after login while the map is open")
    if snapshot.get("standalone") is not True and not allow_browser_mode:
        errors.append("world-map diagnostics standalone must be true; copy it from the Home Screen launch")

    diagnostics = snapshot.get("diagnostics")
    href = str(snapshot.get("href") or "")
    if not isinstance(diagnostics, dict):
        errors.append("world-map diagnostics diagnostics must be an object")
    else:
        if diagnostics.get("enabled") is not True:
            errors.append(f"world-map diagnostics.enabled must be true, got {diagnostics!r}")
        if not allow_browser_mode:
            if diagnostics.get("source") != "stored":
                errors.append(
                    f"world-map diagnostics.source must be 'stored' from the Home Screen launch, got {diagnostics.get('source')!r}"
                )
            if diagnostics.get("stored") is not True:
                errors.append(f"world-map diagnostics.stored must be true, got {diagnostics!r}")
            if "diag=1" in href or "debug=1" in href:
                errors.append(f"world-map diagnostics href should come from the Home Screen URL without diag=1/debug=1, got {href!r}")

    body_classes = set(str(snapshot.get("bodyClass") or "").split())
    if "world-map-open" not in body_classes:
        errors.append(f"world-map diagnostics bodyClass must include world-map-open, got {snapshot.get('bodyClass')!r}")

    canvas = snapshot.get("canvas")
    if not isinstance(canvas, dict):
        errors.append("world-map diagnostics canvas must be an object")
        canvas = {}
    canvas_width = int(canvas.get("width") or 0)
    canvas_height = int(canvas.get("height") or 0)
    if canvas_width != 512:
        errors.append(f"world-map diagnostics canvas.width should be 512, got {canvas.get('width')!r}")
    if canvas_height < 600:
        errors.append(f"world-map diagnostics canvas.height should be at least 600 for portrait mobile proof, got {canvas.get('height')!r}")

    world_map = snapshot.get("worldMap")
    if not isinstance(world_map, dict):
        errors.append("world-map diagnostics must include worldMap object")
        return
    if world_map.get("visible") is not True:
        errors.append(f"world-map diagnostics worldMap.visible must be true, got {world_map.get('visible')!r}")

    window = world_map.get("window")
    if not isinstance(window, dict):
        errors.append("world-map diagnostics worldMap.window must be an object")
        return
    try:
        left = int(window.get("x"))
        top = int(window.get("y"))
        width = int(window.get("width"))
        height = int(window.get("height"))
    except (TypeError, ValueError):
        errors.append(f"world-map diagnostics worldMap.window values must be numeric, got {window!r}")
        return
    rect = {
        "left": left,
        "top": top,
        "right": left + width,
        "bottom": top + height,
        "width": width,
        "height": height,
    }
    if left < 0 or top < 50:
        errors.append(f"world-map diagnostics window should start inside the phone canvas and below the top HUD, got {window!r}")
    if canvas_width > 0 and width < int(canvas_width * 0.9):
        errors.append(f"world-map diagnostics window should be near-full canvas width, got {window!r} on canvas {canvas!r}")
    if canvas_height > 0 and height < int(canvas_height * 0.78):
        errors.append(f"world-map diagnostics window should be near-full canvas height, got {window!r} on canvas {canvas!r}")
    if canvas_width > 0 and rect["right"] > canvas_width:
        errors.append(f"world-map diagnostics window should fit within canvas width {canvas_width}, got {window!r}")
    if canvas_height > 0 and rect["bottom"] > canvas_height:
        errors.append(f"world-map diagnostics window should fit within canvas height {canvas_height}, got {window!r}")

    point_inside_rect(world_map.get("searchCenter"), rect, "worldMap.searchCenter", errors)
    point_inside_rect(world_map.get("closeCenter"), rect, "worldMap.closeCenter", errors)

    walker = world_map.get("walker")
    if not isinstance(walker, dict):
        errors.append("world-map diagnostics worldMap.walker must be an object")
    else:
        last_request = walker.get("lastRequest")
        if not isinstance(last_request, dict) or int(last_request.get("at") or 0) <= 0:
            errors.append(f"world-map diagnostics must be copied after an intentional map world-walk request, got {last_request!r}")
        last_route = walker.get("lastRoute")
        if not isinstance(last_route, dict):
            errors.append("world-map diagnostics worldMap.walker.lastRoute must be an object")
        elif last_route.get("ok") is not True or int(last_route.get("count") or 0) <= 0:
            errors.append(f"world-map diagnostics must show a successful world-walk route with count > 0, got {last_route!r}")

    controls = snapshot.get("controls")
    if isinstance(controls, dict):
        for key in ("keyboardButton", "actionButton", "panelButton", "chatButton"):
            hidden_control_rect(controls, key, errors, "worldMap.controls")
    else:
        errors.append("world-map diagnostics controls must be an object")


def validate_control_layout(snapshot: dict[str, Any], viewport: dict[str, Any] | None, errors: list[str]) -> None:
    ui = snapshot.get("ui")
    validate_control_set(
        snapshot.get("controls"),
        viewport,
        errors,
        chat_access_mode=chat_access_mode_from_ui(ui),
        panel_access_mode=panel_access_mode_from_ui(ui),
    )


def validate_control_history(snapshot: dict[str, Any], errors: list[str]) -> None:
    history = snapshot.get("controlsHistory")
    if not isinstance(history, dict):
        errors.append("controlsHistory must be an object with portrait and landscape control layouts")
        return
    for orientation in ("portrait", "landscape"):
        entry = history.get(orientation)
        label_prefix = f"controlsHistory.{orientation}.controls"
        if not isinstance(entry, dict):
            errors.append(f"controlsHistory.{orientation} must be present; rotate through {orientation} before copying diagnostics")
            continue
        if entry.get("orientation") != orientation:
            errors.append(f"controlsHistory.{orientation}.orientation should be {orientation!r}, got {entry.get('orientation')!r}")
        if not entry.get("updatedAt"):
            errors.append(f"controlsHistory.{orientation}.updatedAt must be present")
        viewport = entry.get("viewport")
        if not isinstance(viewport, dict):
            errors.append(f"controlsHistory.{orientation}.viewport must be an object")
            continue
        width = int(viewport.get("innerWidth") or 0)
        height = int(viewport.get("innerHeight") or 0)
        if orientation == "portrait" and width > height:
            errors.append(f"controlsHistory.portrait viewport should be portrait, got {width}x{height}")
        if orientation == "landscape" and width <= height:
            errors.append(f"controlsHistory.landscape viewport should be landscape, got {width}x{height}")
        if entry.get("panelAccessMode") != "canvas":
            errors.append(f"controlsHistory.{orientation}.panelAccessMode must be 'canvas', got {entry.get('panelAccessMode')!r}")
        if entry.get("mobilePanelShell") is not False:
            errors.append(f"controlsHistory.{orientation}.mobilePanelShell must be false, got {entry.get('mobilePanelShell')!r}")
        if entry.get("canvasTopTabsVisible") is not True:
            errors.append(f"controlsHistory.{orientation}.canvasTopTabsVisible must be true, got {entry.get('canvasTopTabsVisible')!r}")
        if entry.get("canvasPanelRailVisible") is not False:
            errors.append(f"controlsHistory.{orientation}.canvasPanelRailVisible must be false, got {entry.get('canvasPanelRailVisible')!r}")
        if entry.get("canvasPanelDockVisible") is not False:
            errors.append(f"controlsHistory.{orientation}.canvasPanelDockVisible must be false, got {entry.get('canvasPanelDockVisible')!r}")
        validate_control_set(
            entry.get("controls"),
            viewport,
            errors,
            label_prefix,
            chat_access_mode=str(entry.get("chatAccessMode") or "canvas"),
            panel_access_mode=str(entry.get("panelAccessMode") or "canvas"),
        )


def as_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float) and value.is_integer():
        return int(value)
    if isinstance(value, str):
        text = value.strip()
        if re.fullmatch(r"-?\d+", text):
            return int(text)
    return None


def validate_custom_hud_ui_history(snapshot: dict[str, Any], errors: list[str]) -> None:
    history = snapshot.get("uiHistory")
    if not isinstance(history, dict):
        errors.append(
            "uiHistory must be an object; tap the Voidscape canvas top-tab panel icons, "
            "close the panels, then copy final diagnostics"
        )
        return

    events = history.get("events")
    if not isinstance(events, list):
        errors.append("uiHistory.events must be an array of custom HUD state changes")
        return

    message_tabs: set[str] = set()
    show_ui_tabs: set[int] = set()
    saw_any_top_panel_open = False
    saw_close_after_top_panel_open = False

    summary_tabs = history.get("messageTabs")
    if isinstance(summary_tabs, list):
        message_tabs.update(str(value) for value in summary_tabs if str(value))

    summary_panels = history.get("showUiTabs")
    if isinstance(summary_panels, list):
        for value in summary_panels:
            numeric = as_int(value)
            if numeric is not None:
                show_ui_tabs.add(numeric)

    for index, event in enumerate(events):
        if not isinstance(event, dict):
            errors.append(f"uiHistory.events[{index}] must be an object")
            continue
        kind = event.get("kind") or event.get("type")
        if kind == "messageTab":
            tab = event.get("messageTab") or event.get("value")
            if isinstance(tab, str) and tab.strip():
                message_tabs.add(tab)
        elif kind == "showUiTab":
            numeric = as_int(event.get("showUiTab"))
            if numeric is None:
                numeric = as_int(event.get("value"))
            if numeric is None:
                errors.append(f"uiHistory.events[{index}] showUiTab value must be numeric, got {event!r}")
                continue
            show_ui_tabs.add(numeric)
            if numeric != 0:
                saw_any_top_panel_open = True
            elif saw_any_top_panel_open:
                saw_close_after_top_panel_open = True

    for tab in REQUIRED_CUSTOM_HUD_MESSAGE_TABS:
        if tab not in message_tabs:
            errors.append(f"uiHistory must record selecting the {tab} chat mode, got message tabs {sorted(message_tabs)!r}")
    for tab_id in REQUIRED_CUSTOM_HUD_TOP_TABS:
        if tab_id not in show_ui_tabs:
            errors.append(f"uiHistory must record opening shared panel showUiTab {tab_id} from the Android-parity canvas tabs, got {sorted(show_ui_tabs)!r}")
    if not saw_close_after_top_panel_open:
        errors.append("uiHistory must record showUiTab 0 after opening a shared panel; close a panel before copying final diagnostics")


def validate_scroll_history(snapshot: dict[str, Any], errors: list[str]) -> None:
    history = snapshot.get("scrollHistory")
    if not isinstance(history, list):
        errors.append("scrollHistory must be a list; swipe-scroll a canvas-tab-opened shared panel before copying final diagnostics")
        return
    panel_scrolls: list[dict[str, Any]] = []
    for index, event in enumerate(history):
        if not isinstance(event, dict):
            errors.append(f"scrollHistory[{index}] must be an object")
            continue
        amount = as_int(event.get("amount"))
        show_ui_tab = as_int(event.get("showUiTab"))
        if amount is None:
            errors.append(f"scrollHistory[{index}].amount must be numeric, got {event!r}")
            continue
        if show_ui_tab is None:
            errors.append(f"scrollHistory[{index}].showUiTab must be numeric, got {event!r}")
            continue
        if event.get("scrollableUi") is True and show_ui_tab in REQUIRED_CUSTOM_HUD_TOP_TABS and amount != 0:
            panel_scrolls.append(event)
    if not panel_scrolls:
        errors.append(
            "scrollHistory must record at least one nonzero scrollable shared-panel gesture "
            f"with showUiTab in {REQUIRED_CUSTOM_HUD_TOP_TABS!r}, got {history!r}"
        )


def validate_post_resume_proof(
    snapshot: dict[str, Any],
    errors: list[str],
    allow_browser_mode: bool,
) -> None:
    proof = snapshot.get("postResumeProof")
    if proof is None and allow_browser_mode:
        return
    if not isinstance(proof, dict):
        errors.append(
            "postResumeProof must be an object; after Home Screen background/resume, "
            "tap-to-move or chat, then copy final diagnostics"
        )
        return
    if not proof.get("lastResumeAt"):
        errors.append(f"postResumeProof.lastResumeAt must be present, got {proof!r}")
    if int(proof.get("resumeCount") or 0) <= 0:
        errors.append(f"postResumeProof.resumeCount must be > 0, got {proof!r}")
    if proof.get("inputAfterResume") is not True:
        errors.append(f"postResumeProof.inputAfterResume must be true after post-resume touch/chat, got {proof!r}")
    if proof.get("movementAfterResume") is not True:
        errors.append(f"postResumeProof.movementAfterResume must be true after post-resume tap-to-move, got {proof!r}")
    if not proof.get("lastInputAfterResumeAt"):
        errors.append(f"postResumeProof.lastInputAfterResumeAt must be present, got {proof!r}")
    if not proof.get("lastMovementAfterResumeAt"):
        errors.append(f"postResumeProof.lastMovementAfterResumeAt must be present, got {proof!r}")
    events = proof.get("events")
    if not isinstance(events, list):
        errors.append(f"postResumeProof.events must be a list, got {events!r}")
        return
    kinds = {str(event.get("kind")) for event in events if isinstance(event, dict)}
    if "input" not in kinds:
        errors.append(f"postResumeProof.events must include an input event, got {events!r}")
    if "movement" not in kinds:
        errors.append(f"postResumeProof.events must include a movement event, got {events!r}")
    if not isinstance(proof.get("lastMovementFrom"), dict) or not isinstance(proof.get("lastMovementTo"), dict):
        errors.append(f"postResumeProof must include movement endpoints, got {proof!r}")


def normalize_directory_url(value: str) -> str:
    parsed = urlparse(value)
    path = parsed.path or "/"
    if not path.endswith("/"):
        path += "/"
    return urlunparse(parsed._replace(path=path, query="", fragment=""))


def url_without_query_or_fragment(value: str) -> str:
    parsed = urlparse(value)
    return urlunparse(parsed._replace(query="", fragment=""))


def validate_deployment_summary(
    summary: dict[str, Any],
    expected: dict[str, Any],
    diagnostics_launch_url: str,
    errors: list[str],
    relaxed: bool,
) -> None:
    base_url = str(summary.get("baseUrl") or "")
    parsed_base = urlparse(base_url)
    if parsed_base.scheme not in ("http", "https") or not parsed_base.netloc:
        errors.append(f"deployment verification baseUrl must be an absolute http(s) URL, got {base_url!r}")
        return
    if parsed_base.scheme != "https" and not relaxed:
        errors.append(f"deployment verification baseUrl must be https:// for final release, got {base_url!r}")

    if not relaxed:
        production_flags = (
            ("allowHttp", "deployment verification must not use --allow-http for final release"),
            ("insecureTls", "deployment verification must not use --insecure for final release"),
            ("allowDebug", "deployment verification must not use --allow-debug for final release"),
            ("allowInsecureWs", "deployment verification must not use --allow-insecure-ws for final release"),
        )
        for key, message in production_flags:
            if summary.get(key) is not False:
                errors.append(f"{message}; rerun verify-web-teavm-deployment.sh with the current verifier")

    normalized_base = normalize_directory_url(base_url)
    expected_page_url = urljoin(normalized_base, "index.html")
    actual_page_url = url_without_query_or_fragment(diagnostics_launch_url)
    if expected_page_url != actual_page_url:
        errors.append(
            f"deployment verification baseUrl should match the iPhone diagnostics URL root: "
            f"expected page {expected_page_url}, diagnostics page {actual_page_url}"
        )

    if summary.get("smokeRequested") is not True and not relaxed:
        errors.append("deployment verification summary must come from verify-web-teavm-deployment.sh --smoke for final release")
    if not relaxed:
        if summary.get("smokeRan") is not True:
            errors.append("deployment verification smokeRan must be true for final release")
        if summary.get("smokePassed") is not True:
            errors.append("deployment verification smokePassed must be true for final release")

    failures = summary.get("failures")
    if not isinstance(failures, list):
        errors.append("deployment verification failures must be a list")
    elif failures:
        errors.append(f"deployment verification failures must be empty, got {failures!r}")

    results = summary.get("results")
    if not isinstance(results, list):
        errors.append("deployment verification results must be a list")
        return

    by_path: dict[str, dict[str, Any]] = {}
    forbidden_paths: set[str] = set()
    for index, row in enumerate(results):
        if not isinstance(row, dict):
            errors.append(f"deployment verification results[{index}] must be an object")
            continue
        path = str(row.get("path") or "")
        if path:
            by_path[path] = row
        if row.get("kind") == "forbidden":
            forbidden_paths.add(path)

    required_paths = (
        "index.html",
        "manifest.webmanifest",
        "voidscape-web-client.js",
        "icon-192.png",
        "icon-512.png",
        "apple-touch-icon.png",
        "favicon.png",
        "Cache/MD5.SUM",
        "voidscape-web-build.json",
    )
    for path in required_paths:
        row = by_path.get(path)
        if not isinstance(row, dict):
            errors.append(f"deployment verification results must include {path}")
            continue
        if str(row.get("status")) != "200":
            errors.append(f"deployment verification {path} should return HTTP 200, got {row.get('status')!r}")
        curl_code = as_int(row.get("curlCode"))
        if curl_code != 0:
            errors.append(f"deployment verification {path} curlCode should be 0, got {row.get('curlCode')!r}")
        size = as_int(row.get("size"))
        if size is None or size <= 0:
            errors.append(f"deployment verification {path} size should be nonzero, got {row.get('size')!r}")

    for path in ("accounts.txt", "Cache/accounts.txt", "voidscape-web-client.js.map", "voidscape-web-client.js.teavmdbg"):
        if path not in forbidden_paths:
            errors.append(f"deployment verification results must include forbidden-path check for {path}")

    build_manifest_sha = str(summary.get("buildManifestSha256") or "")
    if not re.fullmatch(r"[0-9a-f]{64}", build_manifest_sha):
        errors.append(f"deployment verification buildManifestSha256 must be a SHA-256 hex digest, got {build_manifest_sha!r}")
    build_manifest_matches_expected = summary.get("buildManifestMatchesExpected")
    if build_manifest_matches_expected is False:
        errors.append("deployment verification buildManifestMatchesExpected must not be false; upload the exact package being verified")
    elif build_manifest_matches_expected is not True and not relaxed:
        errors.append(
            "deployment verification buildManifestMatchesExpected must be true for final release; "
            "run verify-web-teavm-deployment.sh with --expected-build-manifest"
        )

    deep_failure_count = as_int(summary.get("deepManifestFailureCount"))
    if deep_failure_count is not None and deep_failure_count > 0:
        errors.append(f"deployment verification deepManifestFailureCount must be 0, got {deep_failure_count}")

    deep_requested = summary.get("deepManifestRequested")
    deep_checked = summary.get("deepManifestChecked")
    deep_file_count = as_int(summary.get("deepManifestFileCount"))
    deep_verified_count = as_int(summary.get("deepManifestVerifiedCount"))
    if deep_requested is not True and not relaxed:
        errors.append(
            "deployment verification deepManifestRequested must be true for final release; "
            "run verify-web-teavm-deployment.sh with --deep-manifest"
        )
    if deep_checked is not True and not relaxed:
        errors.append("deployment verification deepManifestChecked must be true for final release")
    if not relaxed:
        if deep_file_count is None or deep_file_count <= 0:
            errors.append(f"deployment verification deepManifestFileCount must be positive, got {summary.get('deepManifestFileCount')!r}")
        if deep_verified_count is None or deep_verified_count <= 0:
            errors.append(
                f"deployment verification deepManifestVerifiedCount must be positive, "
                f"got {summary.get('deepManifestVerifiedCount')!r}"
            )
        elif deep_file_count is not None and deep_verified_count != deep_file_count:
            errors.append(
                "deployment verification deepManifestVerifiedCount must equal deepManifestFileCount, "
                f"got {deep_verified_count} of {deep_file_count}"
            )
    elif deep_checked is True:
        if deep_file_count is None or deep_file_count <= 0:
            errors.append(f"deployment verification deepManifestFileCount must be positive when checked, got {summary.get('deepManifestFileCount')!r}")
        if deep_verified_count is None or deep_verified_count < 0:
            errors.append(
                "deployment verification deepManifestVerifiedCount must be present when checked, "
                f"got {summary.get('deepManifestVerifiedCount')!r}"
            )
        elif deep_file_count is not None and deep_verified_count != deep_file_count and deep_failure_count in (None, 0):
            errors.append(
                "deployment verification deepManifestVerifiedCount should equal deepManifestFileCount when no failures are reported, "
                f"got {deep_verified_count} of {deep_file_count}"
            )

    cache_policy_failure_count = as_int(summary.get("cachePolicyFailureCount"))
    if summary.get("cachePolicyChecked") is not True and not relaxed:
        errors.append("deployment verification cachePolicyChecked must be true for final release")
    if cache_policy_failure_count is not None and cache_policy_failure_count > 0:
        errors.append(f"deployment verification cachePolicyFailureCount must be 0, got {cache_policy_failure_count}")
    elif not relaxed and cache_policy_failure_count is None:
        errors.append(
            "deployment verification cachePolicyFailureCount must be present for final release; "
            "rerun verify-web-teavm-deployment.sh with the current verifier"
        )

    ws_url = str(summary.get("wsUrl") or "")
    if ws_url and ws_url != expected["effective_ws"]:
        errors.append(f"deployment verification wsUrl should match expected effectiveWs {expected['effective_ws']}, got {ws_url!r}")


def checklist_item_texts(text: str) -> set[str]:
    return {
        match.group(1).strip()
        for match in re.finditer(r"^- \[[ xX]\] (.*?)\s*$", text, re.MULTILINE)
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validate a filled Voidscape iPhone Safari QA diagnostics report."
    )
    parser.add_argument("report", help="Path to iphone-safari-qa-report.md")
    parser.add_argument(
        "--allow-unchecked",
        action="store_true",
        help="Do not fail when smoke checklist boxes are still unchecked.",
    )
    parser.add_argument(
        "--allow-recent-errors",
        action="store_true",
        help="Do not fail when diagnostics recentErrors is non-empty.",
    )
    parser.add_argument(
        "--allow-browser-mode",
        action="store_true",
        help="Do not require Home Screen standalone diagnostics. Use only for browser-only development fixtures.",
    )
    parser.add_argument(
        "--allow-no-external-keyboard",
        action="store_true",
        help="Compatibility no-op. External hardware keyboard QA is optional; the report field still must say yes or no.",
    )
    parser.add_argument(
        "--allow-no-deployment-verification",
        action="store_true",
        help="Do not require pasted verify-web-teavm-deployment.sh summary JSON. Use only for local/LAN development fixtures.",
    )
    args = parser.parse_args()

    path = Path(args.report)
    if not path.is_file():
        fail(f"report not found: {path}")

    text = path.read_text(encoding="utf-8")
    extracted = extract_section_json_block(text, "Required Diagnostics", "final diagnostics")
    blocking_dialog_extracted = extract_section_json_block(
        text,
        "Blocking Dialog Diagnostics",
        "blocking-dialog diagnostics",
    )
    world_map_extracted = extract_section_json_block(
        text,
        "World Map Diagnostics",
        "world-map diagnostics",
    )
    deployment_summary = extract_section_json_object(
        text,
        "Deployment Verification",
        "deployment verification",
        required=not args.allow_no_deployment_verification,
    )
    report = extracted["report"]
    snapshot = extracted["snapshot"]
    blocking_dialog_snapshot = blocking_dialog_extracted["snapshot"]
    world_map_snapshot = world_map_extracted["snapshot"]
    expected = extract_expected(text)
    diag_launch_url = diagnostics_url(text)
    expected_ws = expected["effective_ws"]
    expected_endpoint = expected["endpoint"]

    errors: list[str] = []

    if nested(snapshot, "touchProfile") is not True:
        errors.append("touchProfile must be true")
    if nested(snapshot, "inGame") is not True:
        errors.append("inGame must be true; copy diagnostics after a successful login")
    if snapshot.get("standalone") is not True and not args.allow_browser_mode:
        errors.append("standalone must be true; copy final diagnostics from the Home Screen launch")

    endpoint = snapshot.get("endpoint")
    if not isinstance(endpoint, dict):
        errors.append("endpoint must be an object")
    else:
        expected_mode = expected_endpoint["mode"]
        expected_source = expected_endpoint["source"]
        actual_source = endpoint.get("source")
        source_matches = actual_source == expected_source
        stored_home_screen_source = (
            snapshot.get("standalone") is True
            and actual_source == "stored"
            and expected_source == "query"
        )
        if endpoint.get("mode") != expected_mode:
            errors.append(f"endpoint.mode should be {expected_mode}, got {endpoint.get('mode')!r}")
        if not source_matches and not stored_home_screen_source:
            errors.append(f"endpoint.source should be {expected_source}, got {actual_source!r}")
        if expected_mode == "ws":
            if endpoint.get("ws") != expected_endpoint["ws"]:
                errors.append(f"endpoint.ws should be {expected_endpoint['ws']}, got {endpoint.get('ws')!r}")
        else:
            if endpoint.get("host") != expected_endpoint["host"]:
                errors.append(f"endpoint.host should be {expected_endpoint['host']}, got {endpoint.get('host')!r}")
            if int(endpoint.get("port") or 0) != expected_endpoint["port"]:
                errors.append(f"endpoint.port should be {expected_endpoint['port']}, got {endpoint.get('port')!r}")

    if snapshot.get("effectiveWs") != expected_ws:
        errors.append(f"effectiveWs should be {expected_ws}, got {snapshot.get('effectiveWs')!r}")

    if deployment_summary is not None:
        validate_deployment_summary(
            deployment_summary,
            expected,
            diag_launch_url,
            errors,
            relaxed=args.allow_browser_mode or args.allow_no_deployment_verification,
        )

    expected_portal = expected.get("portal") or {}
    portal = snapshot.get("portal")
    if not isinstance(portal, dict):
        errors.append("portal must be an object with accountUrl and recoveryUrl")
    else:
        account_url = expected_portal.get("accountUrl")
        recovery_url = expected_portal.get("recoveryUrl")
        if account_url and portal.get("accountUrl") != account_url:
            errors.append(f"portal.accountUrl should be {account_url}, got {portal.get('accountUrl')!r}")
        if recovery_url and portal.get("recoveryUrl") != recovery_url:
            errors.append(f"portal.recoveryUrl should be {recovery_url}, got {portal.get('recoveryUrl')!r}")
        if portal.get("source") not in ("default", "query", "stored", "global"):
            errors.append(f"portal.source should be default, query, stored, or global, got {portal.get('source')!r}")

    href = str(snapshot.get("href") or "")
    if "mobile=1" not in href:
        errors.append("href should include mobile=1")

    canvas = snapshot.get("canvas")
    if not isinstance(canvas, dict):
        errors.append("canvas must be an object")
    else:
        if int(canvas.get("width") or 0) != 512:
            errors.append(f"canvas.width should be 512, got {canvas.get('width')!r}")
        if int(canvas.get("height") or 0) <= 0:
            errors.append(f"canvas.height should be > 0, got {canvas.get('height')!r}")
        if int(canvas.get("cssWidth") or 0) <= 0 or int(canvas.get("cssHeight") or 0) <= 0:
            errors.append(f"canvas CSS size should be nonzero, got {canvas!r}")

    viewport = snapshot.get("viewport")
    if not isinstance(viewport, dict):
        errors.append("viewport must be an object")
    else:
        if int(viewport.get("scrollX") or 0) != 0 or int(viewport.get("scrollY") or 0) != 0:
            errors.append(f"viewport scroll should be 0,0, got {viewport.get('scrollX')},{viewport.get('scrollY')}")
        if int(viewport.get("innerWidth") or 0) <= 0 or int(viewport.get("innerHeight") or 0) <= 0:
            errors.append(f"viewport inner size should be nonzero, got {viewport!r}")

    validate_control_layout(snapshot, viewport if isinstance(viewport, dict) else None, errors)
    validate_control_history(snapshot, errors)
    validate_blocking_dialog_snapshot(blocking_dialog_snapshot, errors, args.allow_browser_mode)
    validate_world_map_snapshot(world_map_snapshot, errors, args.allow_browser_mode)

    client = snapshot.get("client")
    if not isinstance(client, dict):
        errors.append("client must be an object; copy diagnostics after the web client publishes local player state")
    else:
        if client.get("hasLocalPlayer") is not True:
            errors.append("client.hasLocalPlayer must be true; copy diagnostics after login has fully loaded")
        if int(client.get("worldX") or 0) <= 0 or int(client.get("worldY") or 0) <= 0:
            errors.append(f"client world tile should be nonzero, got {client!r}")
        local_x = client.get("localX")
        local_z = client.get("localZ")
        if not isinstance(local_x, (int, float)) or not isinstance(local_z, (int, float)) or int(local_x) < 0 or int(local_z) < 0:
            errors.append(f"client local tile should be nonnegative, got {client!r}")
        for key in ("cameraRotation", "cameraZoom", "lastZoom"):
            value = client.get(key)
            if not isinstance(value, (int, float)):
                errors.append(f"client.{key} must be numeric camera state, got {value!r}")
        if canvas and isinstance(canvas, dict) and int(client.get("gameWidth") or 0) != int(canvas.get("width") or 0):
            errors.append(f"client.gameWidth should match canvas.width, got {client.get('gameWidth')!r} vs {canvas.get('width')!r}")

    ui = snapshot.get("ui")
    if not isinstance(ui, dict):
        errors.append("ui must be an object; copy diagnostics after the web client publishes shared UI state")
    else:
        if ui.get("webBuild") is not True:
            errors.append(f"ui.webBuild must be true, got {ui!r}")
        if ui.get("androidProfile") is not True:
            errors.append(f"ui.androidProfile must be true in mobile Safari mode, got {ui!r}")
        if ui.get("customUi") is not True:
            errors.append(f"ui.customUi must be true so the Voidscape HUD skin is active, got {ui!r}")
        if not isinstance(ui.get("blockingDialog"), bool):
            errors.append(f"ui.blockingDialog must be boolean so dialog-safe state is diagnosable, got {ui!r}")
        if not isinstance(ui.get("blockingDialogName"), str):
            errors.append(f"ui.blockingDialogName must be a string so dialog-safe state is diagnosable, got {ui!r}")
        if not isinstance(ui.get("chatPanelHidden"), bool):
            errors.append(f"ui.chatPanelHidden must be boolean so chat helper state is diagnosable, got {ui!r}")
        if ui.get("chatAccessMode") not in {"canvas", "collapsed-helper", "dom-helper"}:
            errors.append(f"ui.chatAccessMode must be canvas/collapsed-helper/dom-helper, got {ui!r}")
        if ui.get("panelAccessMode") != "canvas":
            errors.append(f"ui.panelAccessMode must be 'canvas' for Android-parity iPhone panel access, got {ui!r}")
        if ui.get("mobilePanelShell") is not False:
            errors.append(f"ui.mobilePanelShell must be false so the shared canvas HUD owns panel access, got {ui!r}")
        if ui.get("canvasTopTabsVisible") is not True:
            errors.append(f"ui.canvasTopTabsVisible must be true so Android-parity canvas tabs own iPhone panels, got {ui!r}")
        if ui.get("canvasPanelRailVisible") is not False:
            errors.append(f"ui.canvasPanelRailVisible must be false because Android-parity canvas tabs own panel access on iPhone, got {ui!r}")
        if ui.get("canvasPanelDockVisible") is not False:
            errors.append(f"ui.canvasPanelDockVisible must be false because the old web-only bottom dock is disabled on iPhone, got {ui!r}")

    validate_custom_hud_ui_history(snapshot, errors)
    validate_scroll_history(snapshot, errors)
    validate_post_resume_proof(snapshot, errors, args.allow_browser_mode)

    diagnostics = snapshot.get("diagnostics")
    if not isinstance(diagnostics, dict):
        errors.append("diagnostics must be an object; copy diagnostics from a current web client")
    else:
        if diagnostics.get("enabled") is not True:
            errors.append(f"diagnostics.enabled must be true, got {diagnostics!r}")
        if diagnostics.get("source") not in ("query", "stored"):
            errors.append(f"diagnostics.source should be query or stored, got {diagnostics!r}")
        if not args.allow_browser_mode:
            if diagnostics.get("source") != "stored":
                errors.append(f"diagnostics.source must be 'stored' from the Home Screen launch, got {diagnostics!r}")
            if diagnostics.get("stored") is not True:
                errors.append(f"diagnostics.stored must be true after opening the diagnostics URL once, got {diagnostics!r}")
            if "diag=1" in href or "debug=1" in href:
                errors.append(f"href should come from the manifest/Home Screen URL without diag=1/debug=1, got {href!r}")
        if "diag=1" not in href and diagnostics.get("source") != "stored":
            errors.append("diagnostics should come from diag=1 or stored Home Screen diagnostics state")

    lifecycle = snapshot.get("lifecycle")
    if not isinstance(lifecycle, dict):
        errors.append("lifecycle must be an object; copy diagnostics from a current web client after background/resume")
    else:
        if not isinstance(lifecycle.get("hidden"), bool):
            errors.append(f"lifecycle.hidden must be boolean, got {lifecycle.get('hidden')!r}")
        if not isinstance(lifecycle.get("visibilityState"), str):
            errors.append(f"lifecycle.visibilityState must be a string, got {lifecycle.get('visibilityState')!r}")
        if int(lifecycle.get("viewportUpdateCount") or 0) <= 0:
            errors.append(f"lifecycle.viewportUpdateCount should be > 0, got {lifecycle!r}")
        if not lifecycle.get("lastViewportUpdateAt"):
            errors.append(f"lifecycle.lastViewportUpdateAt must be present, got {lifecycle!r}")
        if not args.allow_browser_mode:
            if int(lifecycle.get("resumeCount") or 0) <= 0:
                errors.append("lifecycle.resumeCount must be > 0; background the Home Screen app, return, then copy diagnostics")
            if not lifecycle.get("lastResumeAt"):
                errors.append("lifecycle.lastResumeAt must be present after Home Screen background/resume")

    recent_errors = snapshot.get("recentErrors")
    if not isinstance(recent_errors, list):
        errors.append("recentErrors must be a list")
    elif recent_errors and not args.allow_recent_errors:
        errors.append(f"recentErrors must be empty, got {recent_errors!r}")

    ua = str(report.get("userAgent") or "")
    if "iPhone" not in ua and "Mobile" not in ua:
        errors.append("userAgent should look like Mobile Safari/iPhone")
    for label in ("iPhone model", "iOS version", "Network", "Tester"):
        if not field_is_filled(report_field(text, label)):
            errors.append(f"Device field '{label}' must be filled for final validation")
    iphone_model = report_field(text, "iPhone model")
    if field_is_filled(iphone_model) and "simulator" in iphone_model.lower() and not args.allow_browser_mode:
        errors.append("Device field 'iPhone model' must describe a physical iPhone, not Simulator, for final validation")
    device_mode = report_field(text, "Safari or Home Screen")
    if not device_mode:
        errors.append("Device field 'Safari or Home Screen' must be filled")
    elif "home screen" not in device_mode.lower() and not args.allow_browser_mode:
        errors.append("Device field 'Safari or Home Screen' must indicate Home Screen for final validation")
    external_keyboard = report_field(text, "External keyboard tested").lower()
    yes_values = {"yes", "y", "true"}
    no_values = {"no", "n", "false"}
    if not external_keyboard:
        errors.append("Device field 'External keyboard tested' must be filled with yes or no")
    elif external_keyboard not in yes_values | no_values:
        errors.append("Device field 'External keyboard tested' must be yes or no")

    checked, total = checked_items(text)
    if total == 0:
        errors.append("report has no smoke checklist items")
    elif checked != total and not args.allow_unchecked:
        errors.append(f"smoke checklist is incomplete: {checked}/{total} checked")
    checklist_texts = checklist_item_texts(text)
    for required_item in REQUIRED_CHECKLIST_ITEMS:
        if required_item not in checklist_texts:
            errors.append(f"report is missing required smoke checklist item: {required_item}")

    if errors:
        for error in errors:
            print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"iPhone QA report passed: {path}")
    print(f"  effectiveWs: {expected_ws}")
    if expected.get("portal"):
        print(f"  portal.accountUrl: {expected['portal'].get('accountUrl', '')}")
        print(f"  portal.recoveryUrl: {expected['portal'].get('recoveryUrl', '')}")
    if isinstance(snapshot.get("lifecycle"), dict):
        print(f"  lifecycle.resumeCount: {snapshot['lifecycle'].get('resumeCount', '')}")
    print(f"  checklist: {checked}/{total} checked")
    print(f"  title: {snapshot.get('title', '')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
