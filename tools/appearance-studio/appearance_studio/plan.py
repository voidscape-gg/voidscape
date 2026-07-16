from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import yaml

from .audit import numeric_members
from .model import Registry
from .paths import REPO_ROOT


def _namespace_inventory(path: Path) -> dict[str, Any]:
    payload = yaml.safe_load(path.read_text())
    payload["fixed_consumers"] = sorted(payload.get("fixed_consumers", []), key=lambda value: (value["start"], value["key"]))
    return payload


def _block_status(
    base: int,
    occupied: set[int],
    fixed: list[dict[str, Any]],
    owners: list[str] | None = None,
) -> dict[str, Any]:
    indices = set(range(base, base + 27))
    archive_hits = sorted(indices & occupied)
    fixed_hits = [item["key"] for item in fixed if base <= item["end"] and base + 26 >= item["start"]]
    return {
        "archive_entries": archive_hits,
        "available": not archive_hits and not fixed_hits and not owners,
        "base": base,
        "end": base + 26,
        "fixed_consumers": sorted(fixed_hits),
        "registry_owners": sorted(owners or []),
    }


def build_plan(
    registry: Registry,
    *,
    repo_root: Path = REPO_ROOT,
    client_archive: Path | None = None,
    server_archive: Path | None = None,
    namespaces_path: Path | None = None,
) -> dict[str, Any]:
    client_archive = client_archive or repo_root / "Client_Base/Cache/video/Authentic_Sprites.orsc"
    server_archive = server_archive or repo_root / "server/conf/server/data/Authentic_Sprites.orsc"
    namespaces_path = namespaces_path or repo_root / "content/appearance/namespaces.yaml"
    occupied = numeric_members(client_archive) | numeric_members(server_archive)
    namespace_inventory = _namespace_inventory(namespaces_path)
    fixed = namespace_inventory["fixed_consumers"]
    managed_base = registry.managed_namespace.get("base")
    reservation_count = registry.managed_namespace.get("reservation_count")
    managed_blocks = []
    if isinstance(managed_base, int) and isinstance(reservation_count, int):
        for offset in range(reservation_count):
            base = managed_base + offset * 27
            owners = [
                entry.key for entry in registry.entries
                if max(base, entry.sprite_base) <= min(base + 26, entry.reserved_end)
            ]
            managed_blocks.append(_block_status(base, occupied, fixed, owners))
    managed = {
        "available": bool(managed_blocks) and all(block["available"] for block in managed_blocks),
        "base": managed_base,
        "blocks": managed_blocks,
        "end": registry.managed_namespace.get("end"),
        "reservation_count": reservation_count,
    }
    hazards = [_block_status(base, occupied, fixed) for base in (1944, 1971)]
    entries = [
        {
            "appearance_id": entry.appearance_id,
            "frame_count": entry.frame_count,
            "key": entry.key,
            "reservation": [entry.sprite_base, entry.reserved_end],
            "sprite_base": entry.sprite_base,
            "state": entry.state,
        }
        for entry in sorted(registry.entries, key=lambda item: (item.appearance_id, item.key))
    ]
    return {
        "changes": [],
        "entries": entries,
        "hazard_checks": hazards,
        "managed_namespace": {
            "approval": "pending-gate-1",
            "candidate": managed,
            "capacity_required": registry.managed_namespace.get("capacity_required"),
            "requires_runtime_capacity_change": bool(
                managed["end"] is not None
                and managed["end"] >= min(namespace_inventory.get("runtime_capacities", {}).values())
            ),
            "status": registry.managed_namespace.get("status"),
        },
        "profile": registry.profile,
        "read_only": True,
        "reservation_size": registry.reservation_size,
        "schema": "voidscape-appearance-plan/v1",
        "writes": [],
    }


def canonical_json(plan: dict[str, Any]) -> str:
    return json.dumps(plan, indent=2, sort_keys=True, separators=(",", ": ")) + "\n"
