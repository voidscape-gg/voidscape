#!/usr/bin/env python3

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "server/src/com/openrsc/server/content/PlayerTitle.java"
DEFINITIONS = ROOT / "server/conf/server/defs/PlayerTitleDefs.json"
TITLE_PATTERN = re.compile(
    r'^\s*[A-Z][A-Z0-9_]*\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"',
    re.MULTILINE,
)


def main() -> None:
    source_text = SOURCE.read_text(encoding="utf-8")
    enum_constants = source_text.partition(
        "public static final String ACTIVE_TITLE_CACHE"
    )[0]
    constant_names = re.findall(
        r"^\s*([A-Z][A-Z0-9_]*)\s*\(", enum_constants, re.MULTILINE
    )
    expected = TITLE_PATTERN.findall(enum_constants)
    if not expected:
        raise SystemExit("ERROR: no PlayerTitle definitions found in Java source")
    if len(expected) != len(constant_names):
        raise SystemExit("ERROR: could not parse every PlayerTitle Java enum constant")
    if len({title_id for title_id, _ in expected}) != len(expected):
        raise SystemExit("ERROR: duplicate PlayerTitle id in Java source")

    payload = json.loads(DEFINITIONS.read_text(encoding="utf-8"))
    rows = payload.get("titles") if isinstance(payload, dict) else None
    if not isinstance(rows, list) or not rows:
        raise SystemExit("ERROR: PlayerTitleDefs.json must contain a non-empty titles list")
    actual: list[tuple[str, str]] = []
    for row in rows:
        if not isinstance(row, dict) or set(row) != {"id", "displayName"}:
            raise SystemExit("ERROR: invalid PlayerTitleDefs.json row")
        title_id = row.get("id")
        display_name = row.get("displayName")
        if not isinstance(title_id, str) or not title_id or not isinstance(display_name, str) or not display_name:
            raise SystemExit("ERROR: invalid PlayerTitleDefs.json id/displayName")
        actual.append((title_id, display_name))
    if len({title_id for title_id, _ in actual}) != len(actual):
        raise SystemExit("ERROR: duplicate id in PlayerTitleDefs.json")
    if actual != expected:
        raise SystemExit("ERROR: PlayerTitleDefs.json has drifted from PlayerTitle.java")

    print(f"Portal player-title definitions match all {len(actual)} Java titles")


if __name__ == "__main__":
    main()
