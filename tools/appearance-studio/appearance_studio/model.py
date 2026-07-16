from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    message: str
    path: str | None = None

    def as_dict(self) -> dict[str, Any]:
        result: dict[str, Any] = {
            "code": self.code,
            "message": self.message,
            "severity": self.severity,
        }
        if self.path is not None:
            result["path"] = self.path
        return result


@dataclass(frozen=True)
class AppearanceEntry:
    key: str
    state: str
    kind: str
    appearance_id: int
    animation_name: str
    category: str
    char_colour: int
    blue_mask: int
    gender_model: int
    has_a: bool
    has_f: bool
    paperdoll_slot: int
    sprite_base: int
    frame_count: int
    item_id: int | None
    manifest: Path | None

    @property
    def reserved_end(self) -> int:
        return self.sprite_base + 26


@dataclass(frozen=True)
class Registry:
    path: Path
    profile: str
    reservation_size: int
    managed_namespace: dict[str, Any]
    entries: tuple[AppearanceEntry, ...]
    external_reservations: tuple[AppearanceEntry, ...]
    tombstones: tuple[int, ...]
