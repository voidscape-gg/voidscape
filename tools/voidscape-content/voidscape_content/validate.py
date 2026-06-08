from __future__ import annotations

import zipfile
from dataclasses import dataclass
from pathlib import Path

from .defs import by_id, ids, load_def_array
from .java_parse import load_client_items, load_client_npcs, load_client_version
from .paths import (
    AUTHENTIC_SPRITES,
    CUSTOM_CONTENT_DIR,
    ITEM_DEFS,
    ITEM_DEFS_CUSTOM,
    NPC_DEFS,
    NPC_DEFS_CUSTOM,
    SPRITE_ITEM,
    SPRITE_LOGO,
)


@dataclass(frozen=True)
class Finding:
    severity: str
    code: str
    message: str
    detail: str = ""


@dataclass(frozen=True)
class ValidationResult:
    findings: list[Finding]
    client_version: int | None

    @property
    def errors(self) -> list[Finding]:
        return [f for f in self.findings if f.severity == "ERROR"]

    @property
    def warnings(self) -> list[Finding]:
        return [f for f in self.findings if f.severity == "WARN"]


def _add(findings: list[Finding], severity: str, code: str, message: str, detail: str = "") -> None:
    findings.append(Finding(severity, code, message, detail))


def _check_sequential(
    findings: list[Finding],
    label: str,
    values: list[int],
    *,
    expected_start: int | None = None,
    severity: str = "ERROR",
) -> None:
    if not values:
        _add(findings, severity, f"{label.upper()}_EMPTY", f"{label} has no IDs")
        return
    duplicates = sorted({v for v in values if values.count(v) > 1})
    if duplicates:
        _add(
            findings,
            severity,
            f"{label.upper()}_DUPLICATE_IDS",
            f"{label} has duplicate IDs",
            ", ".join(str(v) for v in duplicates[:20]),
        )
    start = values[0] if expected_start is None else expected_start
    expected = list(range(start, start + len(values)))
    if values != expected:
        missing = sorted(set(expected) - set(values))
        extra = sorted(set(values) - set(expected))
        detail_bits = []
        if missing:
            detail_bits.append("missing " + ", ".join(str(v) for v in missing[:20]))
        if extra:
            detail_bits.append("unexpected " + ", ".join(str(v) for v in extra[:20]))
        _add(
            findings,
            severity,
            f"{label.upper()}_NON_SEQUENTIAL",
            f"{label} IDs must be sequential and ordered",
            "; ".join(detail_bits),
        )


def _check_range_gaps(
    findings: list[Finding],
    label: str,
    values: list[int],
    *,
    expected_start: int = 0,
    severity: str = "WARN",
) -> None:
    if not values:
        _add(findings, severity, f"{label.upper()}_EMPTY", f"{label} has no IDs")
        return
    duplicates = sorted({v for v in values if values.count(v) > 1})
    if duplicates:
        _add(
            findings,
            severity,
            f"{label.upper()}_DUPLICATE_IDS",
            f"{label} has duplicate IDs",
            ", ".join(str(v) for v in duplicates[:20]),
        )
    expected = set(range(expected_start, max(values) + 1))
    missing = sorted(expected - set(values))
    if missing:
        _add(
            findings,
            severity,
            f"{label.upper()}_GAPS",
            f"{label} has gaps before its current max ID",
            ", ".join(str(v) for v in missing[:30]),
        )


def _check_content_packs(findings: list[Finding]) -> None:
    if not CUSTOM_CONTENT_DIR.exists():
        _add(findings, "WARN", "CONTENT_DIR_MISSING", "content/custom does not exist yet")
        return
    for pack in sorted(p for p in CUSTOM_CONTENT_DIR.iterdir() if p.is_dir()):
        if pack.name.startswith("."):
            continue
        manifest = pack / "content.yaml"
        if not manifest.exists():
            _add(findings, "WARN", "CONTENT_MANIFEST_MISSING", f"{pack} has no content.yaml")
        for rel in ("art/prompts", "art/source", "art/working", "art/final"):
            if not (pack / rel).exists():
                _add(findings, "WARN", "CONTENT_DIR_INCOMPLETE", f"{pack} is missing {rel}")


def _check_items(findings: list[Finding]) -> None:
    base = load_def_array(ITEM_DEFS)
    custom = load_def_array(ITEM_DEFS_CUSTOM)
    base_ids = ids(base.rows)
    custom_ids = ids(custom.rows)
    _check_sequential(findings, "item_defs", base_ids, expected_start=0)
    if base_ids and custom_ids:
        _check_sequential(findings, "item_defs_custom", custom_ids, expected_start=max(base_ids) + 1)

    server_rows = base.rows + custom.rows
    server_by_id = by_id(server_rows)
    server_ids = sorted(server_by_id)
    _check_sequential(findings, "item_defs_combined", server_ids, expected_start=0)

    client_items = load_client_items()
    client_by_id = {item.id: item for item in client_items}
    client_ids = [item.id for item in client_items]
    _check_range_gaps(findings, "client_items", sorted(client_ids), expected_start=0, severity="WARN")

    if server_ids and client_ids and max(server_ids) > max(client_ids):
        _add(
            findings,
            "ERROR",
            "ITEM_CLIENT_TOO_OLD",
            "server has item IDs above the client item table",
            f"server max {max(server_ids)}, client max {max(client_ids)}",
        )

    missing_custom = [item_id for item_id in custom_ids if item_id not in client_by_id]
    if missing_custom:
        severity = "ERROR" if max(missing_custom) > max(client_ids, default=-1) else "WARN"
        _add(
            findings,
            severity,
            "ITEM_CUSTOM_MISSING_CLIENT_DEF",
            "some custom server items have no client ItemDef",
            ", ".join(str(v) for v in missing_custom[:30]),
        )

    extra_client_custom = [item.id for item in client_items if item.id >= min(custom_ids or [10**9]) and item.id not in server_by_id]
    if extra_client_custom:
        _add(
            findings,
            "WARN",
            "ITEM_CLIENT_EXTRA_CUSTOM_DEF",
            "client has custom ItemDefs not present on the server",
            ", ".join(str(v) for v in extra_client_custom[:30]),
        )

    mismatched_names = []
    for item_id in sorted(set(custom_ids) & set(client_by_id)):
        server_name = str(server_by_id[item_id].get("name", ""))
        client_name = client_by_id[item_id].name
        if server_name.lower() != client_name.lower():
            mismatched_names.append(f"{item_id}: server={server_name!r}, client={client_name!r}")
    if mismatched_names:
        _add(
            findings,
            "WARN",
            "ITEM_NAME_MISMATCH",
            "client/server custom item names differ",
            "; ".join(mismatched_names[:20]),
        )

    occupied: set[str] = set()
    if AUTHENTIC_SPRITES.exists():
        with zipfile.ZipFile(AUTHENTIC_SPRITES, "r") as archive:
            occupied = set(archive.namelist())
    else:
        _add(findings, "ERROR", "SPRITE_ARCHIVE_MISSING", f"{AUTHENTIC_SPRITES} does not exist")

    missing_sprites = []
    recent_missing_sprites = []
    out_of_range = []
    recent_floor = max(server_ids + client_ids) - 4 if server_ids or client_ids else 10**9
    for item in client_items:
        if item.id < min(custom_ids or [10**9]) or item.sprite_id < 0:
            continue
        archive_index = SPRITE_ITEM + item.sprite_id
        if archive_index >= SPRITE_LOGO:
            out_of_range.append(f"{item.id}:{item.name} spriteID={item.sprite_id} index={archive_index}")
        elif occupied and str(archive_index) not in occupied:
            entry = f"{item.id}:{item.name} index={archive_index}"
            if item.id >= recent_floor:
                recent_missing_sprites.append(entry)
            else:
                missing_sprites.append(entry)
    if out_of_range:
        _add(
            findings,
            "ERROR",
            "ITEM_SPRITE_OUT_OF_RANGE",
            "custom item spriteIDs must stay inside the item sprite archive block",
            "; ".join(out_of_range[:20]),
        )
    if missing_sprites:
        _add(
            findings,
            "WARN",
            "ITEM_SPRITE_MISSING_ARCHIVE_ENTRY_LEGACY",
            "older custom client item sprites are missing from Authentic_Sprites.orsc",
            "; ".join(missing_sprites[:20]),
        )
    if recent_missing_sprites:
        _add(
            findings,
            "ERROR",
            "ITEM_SPRITE_MISSING_ARCHIVE_ENTRY",
            "recent custom client item sprites are missing from Authentic_Sprites.orsc",
            "; ".join(recent_missing_sprites[:20]),
        )


def _check_npcs(findings: list[Finding]) -> None:
    base = load_def_array(NPC_DEFS)
    custom = load_def_array(NPC_DEFS_CUSTOM)
    base_ids = ids(base.rows)
    custom_ids = ids(custom.rows)
    _check_sequential(findings, "npc_defs", base_ids, expected_start=0)
    if base_ids and custom_ids:
        _check_sequential(findings, "npc_defs_custom", custom_ids, expected_start=max(base_ids) + 1)

    server_ids = sorted(by_id(base.rows + custom.rows))
    _check_sequential(findings, "npc_defs_combined", server_ids, expected_start=0)

    client_npcs = load_client_npcs()
    client_count = len(client_npcs)
    server_count = len(server_ids)
    if server_count > client_count:
        _add(
            findings,
            "ERROR",
            "NPC_CLIENT_TOO_OLD",
            "server has more NPC defs than the client table",
            f"server count {server_count}, client count {client_count}",
        )
    elif client_count > server_count:
        _add(
            findings,
            "WARN",
            "NPC_CLIENT_EXTRA_DEFS",
            "client has extra NPCDef entries beyond server JSON",
            f"server count {server_count}, client count {client_count}",
        )


def validate_repo() -> ValidationResult:
    findings: list[Finding] = []
    _check_content_packs(findings)
    _check_items(findings)
    _check_npcs(findings)
    client_version = load_client_version()
    if client_version is None:
        _add(findings, "ERROR", "CLIENT_VERSION_MISSING", "could not parse CLIENT_VERSION")
    return ValidationResult(findings=findings, client_version=client_version)


def print_validation(result: ValidationResult, *, strict: bool = False) -> int:
    for finding in result.findings:
        line = f"{finding.severity}: {finding.code}: {finding.message}"
        if finding.detail:
            line += f" ({finding.detail})"
        print(line)
    if not result.findings:
        print("OK: no validation findings")
    print(
        f"Summary: {len(result.errors)} error(s), {len(result.warnings)} warning(s), "
        f"CLIENT_VERSION={result.client_version if result.client_version is not None else 'unknown'}"
    )
    if result.errors or (strict and result.warnings):
        return 1
    return 0
