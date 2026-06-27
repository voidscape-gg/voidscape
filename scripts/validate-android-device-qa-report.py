#!/usr/bin/env python3
"""Validate a filled physical Android QA report."""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


REQUIRED_FIELDS = [
    "Tester",
    "Date",
    "Device model",
    "Android version",
    "Screen size/density",
    "RAM/storage class",
    "Network tested",
    "APK/build under test",
    "Distribution channel",
    "Game endpoint",
    "Portal URL",
    "Result",
    "Screenshots/videos",
    "Logcat summary",
    "Tester sign-off",
    "Release owner decision",
]

PLACEHOLDERS = {"", "todo", "tbd", "unknown", "n/a", "na", "none", "-"}
NON_PHYSICAL_DEVICE_WORDS = {"emulator", "simulator", "avd", "android studio"}


def parse_fields(text: str) -> dict[str, str]:
    fields: dict[str, str] = {}
    pattern = re.compile(r"^\s*-\s+\*\*(.+?):\*\*\s*(.*)\s*$")
    for line in text.splitlines():
        match = pattern.match(line)
        if match:
            fields[match.group(1).strip()] = match.group(2).strip()
    return fields


def is_placeholder(value: str) -> bool:
    normalized = value.strip().lower()
    return normalized in PLACEHOLDERS


def validate(path: Path, allow_hold: bool) -> list[str]:
    text = path.read_text(encoding="utf-8")
    fields = parse_fields(text)
    errors: list[str] = []

    for field in REQUIRED_FIELDS:
        value = fields.get(field, "")
        if is_placeholder(value):
            errors.append(f"required field is blank or placeholder: {field}")

    device_model = fields.get("Device model", "").lower()
    if any(word in device_model for word in NON_PHYSICAL_DEVICE_WORDS):
        errors.append("Device model must identify real Android hardware, not an emulator/simulator")

    result = fields.get("Result", "").strip().upper()
    owner_decision = fields.get("Release owner decision", "").strip().upper()
    if allow_hold:
        if result not in {"PASS", "HOLD"}:
            errors.append("Result must be PASS or HOLD when --allow-hold is used")
        if owner_decision not in {"PASS", "HOLD"}:
            errors.append("Release owner decision must be PASS or HOLD when --allow-hold is used")
    else:
        if result != "PASS":
            errors.append("Result must be PASS")
        if owner_decision != "PASS":
            errors.append("Release owner decision must be PASS")

    unchecked = [
        line.strip()
        for line in text.splitlines()
        if re.match(r"^\s*-\s+\[\s\]\s+", line)
    ]
    if unchecked:
        errors.append(f"{len(unchecked)} mandatory check(s) are still unchecked")

    checked = [
        line.strip()
        for line in text.splitlines()
        if re.match(r"^\s*-\s+\[[xX]\]\s+", line)
    ]
    if len(checked) < 18:
        errors.append("expected at least 18 completed mandatory checks")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path)
    parser.add_argument("--allow-hold", action="store_true", help="Allow HOLD result for draft review")
    args = parser.parse_args()

    if not args.report.is_file():
        print(f"ERROR: report not found: {args.report}", file=sys.stderr)
        return 2

    errors = validate(args.report, args.allow_hold)
    if errors:
        print("Android device QA report is not release-ready:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1

    print(f"Android device QA report passed: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
