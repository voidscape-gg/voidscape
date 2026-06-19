"""voidscim registry — YAML-backed list of items being upgraded."""
from __future__ import annotations
from dataclasses import dataclass, field, asdict
from pathlib import Path
from typing import Any

import yaml

from .paths import REGISTRY_PATH

SCHEMA_VERSION = 1


@dataclass
class Entry:
    item_key: str
    appearance_id: int
    archive_index: int
    name: str
    is_stackable: bool
    linked_item_ids: list[int] = field(default_factory=list)
    palette_anchors: list[str] = field(default_factory=list)
    palette_tol: int = 28
    silhouette_iou_min: float = 0.95
    target_size: list[int] = field(default_factory=list)  # [w, h]
    sidecar: dict = field(default_factory=dict)
    approved_variant: str | None = None
    last_pack_timestamp: str | None = None
    last_pack_sha256: str | None = None
    pre_pack_backup_path: str | None = None


def load_registry(path: Path = REGISTRY_PATH) -> dict[str, Entry]:
    if not path.exists():
        return {}
    raw = yaml.safe_load(path.read_text()) or {}
    if not isinstance(raw, dict):
        raise ValueError(f"registry root must be a dict, got {type(raw).__name__}")
    sv = raw.get("schema_version", SCHEMA_VERSION)
    if sv != SCHEMA_VERSION:
        raise ValueError(f"registry schema_version={sv} unsupported (this build expects {SCHEMA_VERSION})")
    entries = raw.get("entries", {}) or {}
    return {k: Entry(**v) for k, v in entries.items()}


def save_registry(reg: dict[str, Entry], path: Path = REGISTRY_PATH) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    out: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "entries": {k: asdict(v) for k, v in reg.items()},
    }
    path.write_text(yaml.safe_dump(out, sort_keys=False))


def add_or_update(reg: dict[str, Entry], entry: Entry) -> None:
    reg[entry.item_key] = entry
