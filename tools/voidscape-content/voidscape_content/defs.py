from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class DefArray:
    path: Path
    key: str
    rows: list[dict[str, Any]]


def load_def_array(path: Path) -> DefArray:
    with path.open() as fh:
        data = json.load(fh)
    if not isinstance(data, dict) or not data:
        raise ValueError(f"{path} must contain a non-empty JSON object")
    key = next(iter(data))
    rows = data[key]
    if not isinstance(rows, list):
        raise ValueError(f"{path}:{key} must be a JSON array")
    typed_rows: list[dict[str, Any]] = []
    for idx, row in enumerate(rows):
        if not isinstance(row, dict):
            raise ValueError(f"{path}:{key}[{idx}] must be an object")
        typed_rows.append(row)
    return DefArray(path=path, key=key, rows=typed_rows)


def ids(rows: list[dict[str, Any]]) -> list[int]:
    out: list[int] = []
    for row in rows:
        value = row.get("id")
        if isinstance(value, int):
            out.append(value)
    return out


def by_id(rows: list[dict[str, Any]]) -> dict[int, dict[str, Any]]:
    out: dict[int, dict[str, Any]] = {}
    for row in rows:
        value = row.get("id")
        if isinstance(value, int):
            out[value] = row
    return out
