#!/usr/bin/env python3
"""Generate the static loot editor baseline from authoritative Java/JSON sources."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parent.parent
GENERATOR = Path(__file__).resolve()
NPC_DROPS = REPO_ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
NPC_ID = REPO_ROOT / "server/src/com/openrsc/server/constants/NpcId.java"
ITEM_ID = REPO_ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
ITEM_DEFS = REPO_ROOT / "server/conf/server/defs/ItemDefs.json"
ITEM_DEFS_CUSTOM = REPO_ROOT / "server/conf/server/defs/ItemDefsCustom.json"
NPC_DEFS = REPO_ROOT / "server/conf/server/defs/NpcDefs.json"
NPC_DEFS_CUSTOM = REPO_ROOT / "server/conf/server/defs/NpcDefsCustom.json"
VOID_CHEST = REPO_ROOT / "server/plugins/com/openrsc/server/plugins/custom/misc/VoidChest.java"
DEFAULT_OUTPUT = REPO_ROOT / "web/portal/assets/loot-editor-data.json"

SOURCE_PATHS = (
    GENERATOR,
    NPC_DROPS,
    NPC_ID,
    ITEM_ID,
    ITEM_DEFS,
    ITEM_DEFS_CUSTOM,
    NPC_DEFS,
    NPC_DEFS_CUSTOM,
    VOID_CHEST,
)

NPC_SOURCE = "server/src/com/openrsc/server/constants/NpcDrops.java"
VOID_CHEST_SOURCE = "server/plugins/com/openrsc/server/plugins/custom/misc/VoidChest.java"


@dataclass
class Drop:
    kind: str
    weight: int
    line: int
    item_enum: str | None = None
    item_id: int | None = None
    amount: int | None = None
    noted: bool = False
    table: "DropTable | None" = None
    table_var: str | None = None
    derived_empty: bool = False


@dataclass(eq=False)
class DropTable:
    description: str
    line: int
    drops: list[Drop] = field(default_factory=list)

    @property
    def total_weight(self) -> int:
        return sum(drop.weight for drop in self.drops)

    def clone(self, description: str | None, line: int) -> "DropTable":
        return DropTable(
            self.description if description is None else description,
            line,
            [Drop(**drop.__dict__) for drop in self.drops],
        )


def relative(path: Path) -> str:
    return path.relative_to(REPO_ROOT).as_posix()


def sha256_bytes(data: bytes) -> str:
    return f"sha256:{hashlib.sha256(data).hexdigest()}"


def source_manifest() -> tuple[str, list[dict[str, str]]]:
    digest = hashlib.sha256()
    files: list[dict[str, str]] = []
    for path in SOURCE_PATHS:
        data = path.read_bytes()
        rel = relative(path)
        digest.update(rel.encode("utf-8"))
        digest.update(b"\0")
        digest.update(data)
        digest.update(b"\0")
        files.append({"path": rel, "sha256": sha256_bytes(data)})
    return f"sha256:{digest.hexdigest()}", files


def parse_enum(path: Path) -> dict[str, int]:
    values: dict[str, int] = {}
    pattern = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\(\s*(-?\d+)\s*[,)]")
    for line in path.read_text(encoding="utf-8").splitlines():
        match = pattern.search(line)
        if match:
            values[match.group(1)] = int(match.group(2))
    if not values:
        raise ValueError(f"No enum values found in {relative(path)}")
    return values


def load_defs(paths: tuple[Path, ...], keys: tuple[str, ...]) -> dict[int, dict[str, Any]]:
    records: dict[int, dict[str, Any]] = {}
    for path in paths:
        raw = json.loads(path.read_text(encoding="utf-8"))
        rows: Any = raw
        if isinstance(raw, dict):
            rows = next((raw[key] for key in keys if isinstance(raw.get(key), list)), [])
        if not isinstance(rows, list):
            raise ValueError(f"Expected a definition list in {relative(path)}")
        for row in rows:
            if isinstance(row, dict) and "id" in row:
                records[int(row["id"])] = row
    return records


def find_matching_brace(text: str, open_brace: int) -> int:
    depth = 0
    in_string = False
    escaped = False
    for index in range(open_brace, len(text)):
        char = text[index]
        if escaped:
            escaped = False
            continue
        if in_string and char == "\\":
            escaped = True
            continue
        if char == '"':
            in_string = not in_string
            continue
        if in_string:
            continue
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("Unbalanced braces while evaluating WANT_OPENPK_POINTS")


def blank_preserving_lines(chars: list[str], start: int, end: int) -> None:
    for index in range(start, end):
        if chars[index] not in "\r\n":
            chars[index] = " "


def choose_openpk_false(text: str) -> str:
    """Keep false branches while retaining original source line numbers."""
    pattern = re.compile(r"if\s*\(\s*config\.WANT_OPENPK_POINTS\s*\)\s*\{")
    chars = list(text)
    search_text = text
    while True:
        match = pattern.search(search_text)
        if not match:
            return "".join(chars)
        true_open = match.end() - 1
        true_close = find_matching_brace(text, true_open)
        cursor = true_close + 1
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if not text.startswith("else", cursor):
            blank_preserving_lines(chars, match.start(), true_close + 1)
            search_chars = list(search_text)
            blank_preserving_lines(search_chars, match.start(), true_close + 1)
            search_text = "".join(search_chars)
            continue
        false_open = text.find("{", cursor + 4)
        if false_open < 0:
            raise ValueError("Missing false-branch brace for WANT_OPENPK_POINTS")
        false_close = find_matching_brace(text, false_open)
        blank_preserving_lines(chars, match.start(), false_open + 1)
        blank_preserving_lines(chars, false_close, false_close + 1)
        search_chars = list(search_text)
        blank_preserving_lines(search_chars, match.start(), false_close + 1)
        search_text = "".join(search_chars)


def split_java_args(value: str) -> list[str]:
    args: list[str] = []
    start = 0
    depth = 0
    quote: str | None = None
    escaped = False
    for index, char in enumerate(value):
        if escaped:
            escaped = False
            continue
        if quote is not None:
            if char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in "\"'":
            quote = char
        elif char in "([{":
            depth += 1
        elif char in ")]}":
            depth -= 1
        elif char == "," and depth == 0:
            args.append(value[start:index].strip())
            start = index + 1
    args.append(value[start:].strip())
    return args


def iter_java_statements(text: str):
    """Yield semicolon-terminated Java statements without losing source offsets."""
    start = 0
    paren_depth = 0
    bracket_depth = 0
    quote: str | None = None
    escaped = False
    line_comment = False
    block_comment = False
    index = 0
    while index < len(text):
        char = text[index]
        following = text[index + 1] if index + 1 < len(text) else ""
        if line_comment:
            if char == "\n":
                line_comment = False
            index += 1
            continue
        if block_comment:
            if char == "*" and following == "/":
                block_comment = False
                index += 2
            else:
                index += 1
            continue
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            index += 1
            continue
        if char == "/" and following == "/":
            line_comment = True
            index += 2
            continue
        if char == "/" and following == "*":
            block_comment = True
            index += 2
            continue
        if char in "\"'":
            quote = char
        elif char == "(":
            paren_depth += 1
        elif char == ")":
            paren_depth -= 1
        elif char == "[":
            bracket_depth += 1
        elif char == "]":
            bracket_depth -= 1
        elif char == ";" and paren_depth == 0 and bracket_depth == 0:
            yield text[start : index + 1], start
            start = index + 1
        index += 1


def source_line(text: str, absolute_offset: int) -> int:
    return text.count("\n", 0, absolute_offset) + 1


def parse_int(value: str) -> int:
    normalized = value.strip()
    ternary = re.fullmatch(
        r"config\.WANT_OPENPK_POINTS\s*\?\s*([^:]+)\s*:\s*(.+)", normalized
    )
    if ternary:
        normalized = ternary.group(2).strip()
    normalized = normalized.replace("_", "")
    try:
        return int(normalized, 0)
    except ValueError as error:
        raise ValueError(f"Could not parse Java integer expression {value!r}") from error


def parse_java_string(value: str) -> str:
    try:
        parsed = json.loads(value)
    except json.JSONDecodeError as error:
        raise ValueError(f"Could not parse Java string {value!r}") from error
    if not isinstance(parsed, str):
        raise ValueError(f"Expected Java string, got {value!r}")
    return parsed


def parse_npc_tables(
    item_ids: dict[str, int], npc_ids: dict[str, int]
) -> tuple[dict[str, DropTable], list[DropTable]]:
    text = choose_openpk_false(NPC_DROPS.read_text(encoding="utf-8"))
    table_by_var: dict[str, DropTable] = {}
    npc_to_table: dict[str, DropTable] = {}
    all_tables: list[DropTable] = []

    new_pattern = re.compile(
        r"(?:final\s+)?(?:DropTable\s+)?([A-Za-z_]\w*)\s*=\s*new\s+DropTable\(\s*(\"(?:\\.|[^\"\\])*\")"
    )
    clone_pattern = re.compile(
        r"(?:DropTable\s+)?([A-Za-z_]\w*)\s*=\s*([A-Za-z_]\w*)\.clone\(\s*(?:(\"(?:\\.|[^\"\\])*\"))?\s*\)\s*;",
        re.DOTALL,
    )
    item_pattern = re.compile(r"([A-Za-z_]\w*)\.addItemDrop\((.*)\)\s*;", re.DOTALL)
    table_pattern = re.compile(r"([A-Za-z_]\w*)\.addTableDrop\((.*)\)\s*;", re.DOTALL)
    derived_empty_pattern = re.compile(
        r"([A-Za-z_]\w*)\.addEmptyDrop\(\s*(\d+)\s*-\s*\1\.getTotalWeight\(\)\s*\)"
    )
    fixed_empty_pattern = re.compile(r"([A-Za-z_]\w*)\.addEmptyDrop\(\s*(\d+)\s*\)")
    put_pattern = re.compile(
        r"this\.npcDrops\.put\(\s*NpcId\.([A-Z0-9_]+)\.id\(\)\s*,\s*([A-Za-z_]\w*)\s*\)"
    )
    inline_clone_pattern = re.compile(
        r"([A-Za-z_]\w*)\.clone\(\s*(?:(\"(?:\\.|[^\"\\])*\"))?\s*\)"
    )

    for statement, statement_offset in iter_java_statements(text):
        match = new_pattern.search(statement)
        if match:
            line_number = source_line(text, statement_offset + match.start())
            table = DropTable(parse_java_string(match.group(2)), line_number)
            table_by_var[match.group(1)] = table
            all_tables.append(table)
            continue

        match = clone_pattern.search(statement)
        if match:
            line_number = source_line(text, statement_offset + match.start())
            source = table_by_var.get(match.group(2))
            if source is None:
                raise ValueError(f"Unknown clone source {match.group(2)!r} on line {line_number}")
            description = parse_java_string(match.group(3)) if match.group(3) else ""
            table = source.clone(description, line_number)
            table_by_var[match.group(1)] = table
            all_tables.append(table)
            continue

        match = item_pattern.search(statement)
        if match and match.group(1) in table_by_var:
            line_number = source_line(text, statement_offset + match.start())
            args = split_java_args(match.group(2))
            if len(args) < 3:
                raise ValueError(f"Incomplete addItemDrop on line {line_number}")
            item_match = re.fullmatch(r"ItemId\.([A-Z0-9_]+)\.id\(\)", args[0].strip())
            if not item_match:
                raise ValueError(f"Unsupported item expression {args[0]!r} on line {line_number}")
            item_enum = item_match.group(1)
            if item_enum not in item_ids:
                raise ValueError(f"Unknown ItemId.{item_enum} on line {line_number}")
            table_by_var[match.group(1)].drops.append(
                Drop(
                    kind="item",
                    item_enum=item_enum,
                    item_id=item_ids[item_enum],
                    amount=parse_int(args[1]),
                    weight=parse_int(args[2]),
                    noted=len(args) >= 4 and args[3].strip().lower() == "true",
                    line=line_number,
                )
            )
            continue

        match = table_pattern.search(statement)
        if match and match.group(1) in table_by_var:
            line_number = source_line(text, statement_offset + match.start())
            args = split_java_args(match.group(2))
            if len(args) < 2:
                raise ValueError(f"Incomplete addTableDrop on line {line_number}")
            child_expression = args[0].strip()
            clone = inline_clone_pattern.fullmatch(child_expression)
            if clone:
                source = table_by_var.get(clone.group(1))
                if source is None:
                    raise ValueError(f"Unknown inline clone source on line {line_number}")
                description = parse_java_string(clone.group(2)) if clone.group(2) else ""
                child = source.clone(description, line_number)
                all_tables.append(child)
            else:
                child = table_by_var.get(child_expression)
            table_by_var[match.group(1)].drops.append(
                Drop(
                    kind="table",
                    table=child,
                    table_var=None if child is not None else child_expression,
                    weight=parse_int(args[1]),
                    line=line_number,
                )
            )
            continue

        match = derived_empty_pattern.search(statement)
        if match and match.group(1) in table_by_var:
            line_number = source_line(text, statement_offset + match.start())
            table = table_by_var[match.group(1)]
            target = int(match.group(2))
            remainder = target - table.total_weight
            if remainder < 0:
                raise ValueError(
                    f"{table.description!r} exceeds its target weight by {-remainder} on line {line_number}"
                )
            table.drops.append(
                Drop(kind="nothing", weight=remainder, line=line_number, derived_empty=True)
            )
            continue

        match = fixed_empty_pattern.search(statement)
        if match and match.group(1) in table_by_var:
            line_number = source_line(text, statement_offset + match.start())
            table_by_var[match.group(1)].drops.append(
                Drop(kind="nothing", weight=int(match.group(2)), line=line_number)
            )
            continue

        match = put_pattern.search(statement)
        if match:
            line_number = source_line(text, statement_offset + match.start())
            npc_enum, table_var = match.groups()
            if npc_enum not in npc_ids:
                raise ValueError(f"Unknown NpcId.{npc_enum} on line {line_number}")
            table = table_by_var.get(table_var)
            if table is None:
                raise ValueError(f"Unknown NPC table {table_var!r} on line {line_number}")
            npc_to_table[npc_enum] = table

    for table in all_tables:
        for drop in table.drops:
            if drop.kind == "table" and drop.table is None:
                drop.table = table_by_var.get(drop.table_var or "")
                if drop.table is None:
                    raise ValueError(
                        f"Unknown nested table {drop.table_var!r} on line {drop.line}"
                    )

    if len(npc_to_table) < 100:
        raise ValueError(f"Only parsed {len(npc_to_table)} NPC mappings; expected at least 100")
    return npc_to_table, all_tables


def prettify_enum(value: str) -> str:
    return value.replace("_", " ").title()


def item_sprite(item_id: int) -> str:
    return f"assets/npc-database/item/{item_id}.png"


def npc_sprite(npc_id: int) -> str:
    return f"assets/npc-database/npc/{npc_id}.png"


def make_items(item_defs: dict[int, dict[str, Any]]) -> list[dict[str, Any]]:
    return [
        {
            "id": item_id,
            "name": str(definition.get("name") or f"Item {item_id}"),
            "sprite": item_sprite(item_id),
        }
        for item_id, definition in sorted(item_defs.items())
        if item_id >= 0
    ]


def make_npc_groups(
    npc_to_table: dict[str, DropTable],
    npc_ids: dict[str, int],
    npc_defs: dict[int, dict[str, Any]],
    item_defs: dict[int, dict[str, Any]],
) -> list[dict[str, Any]]:
    grouped: dict[DropTable, list[str]] = {}
    for npc_enum, table in npc_to_table.items():
        grouped.setdefault(table, []).append(npc_enum)

    groups: list[dict[str, Any]] = []
    seen_group_ids: set[str] = set()
    seen_row_ids: set[str] = set()
    ordered = sorted(grouped.items(), key=lambda pair: min(npc_ids[name] for name in pair[1]))
    for table, enum_names in ordered:
        enum_names.sort(key=lambda name: (npc_ids[name], name))
        min_npc_id = npc_ids[enum_names[0]]
        group_id = f"npc-table-{min_npc_id}-l{table.line}"
        if group_id in seen_group_ids:
            raise ValueError(f"Duplicate groupId {group_id}")
        seen_group_ids.add(group_id)

        npcs: list[dict[str, Any]] = []
        for enum_name in enum_names:
            npc_id = npc_ids[enum_name]
            definition = npc_defs.get(npc_id)
            if definition is None:
                raise ValueError(f"NpcId.{enum_name} ({npc_id}) has no NPC definition")
            npcs.append(
                {
                    "id": npc_id,
                    "enum": enum_name,
                    "name": str(definition.get("name") or prettify_enum(enum_name)),
                    "combatLevel": int(definition.get("combatlvl", 0)),
                    "sprite": npc_sprite(npc_id),
                }
            )

        title = table.description.strip()
        if not title:
            title = ", ".join(f"{npc['name']} ({npc['id']})" for npc in npcs)

        rows: list[dict[str, Any]] = []
        line_occurrences: dict[int, int] = {}
        for drop in table.drops:
            line_occurrences[drop.line] = line_occurrences.get(drop.line, 0) + 1
            row_id = f"{group_id}-row-l{drop.line}-{line_occurrences[drop.line]}"
            if row_id in seen_row_ids:
                raise ValueError(f"Duplicate rowId {row_id}")
            seen_row_ids.add(row_id)
            if drop.kind == "item":
                if drop.item_id is None or drop.item_id not in item_defs:
                    raise ValueError(
                        f"ItemId.{drop.item_enum} ({drop.item_id}) has no item definition"
                    )
                item_name = str(item_defs[drop.item_id].get("name") or prettify_enum(drop.item_enum or ""))
                editable = drop.weight > 0
                row: dict[str, Any] = {
                    "rowId": row_id,
                    "kind": "item",
                    "editable": editable,
                    "source": {"file": NPC_SOURCE, "line": drop.line},
                    "itemId": drop.item_id,
                    "itemName": item_name,
                    "amount": drop.amount,
                    "weight": drop.weight,
                    "noted": drop.noted,
                    "sprite": item_sprite(drop.item_id),
                }
                if not editable:
                    row["lockedReason"] = "Guaranteed rows are reference-only in this editor."
                rows.append(row)
            elif drop.kind == "table":
                target = drop.table.description.strip() if drop.table else "Nested table"
                rows.append(
                    {
                        "rowId": row_id,
                        "kind": "table",
                        "editable": False,
                        "source": {"file": NPC_SOURCE, "line": drop.line},
                        "weight": drop.weight,
                        "targetGroupId": target or "Nested table",
                        "lockedReason": "Nested and shared table rolls are reference-only in this editor.",
                    }
                )
            elif drop.kind == "nothing":
                rows.append(
                    {
                        "rowId": row_id,
                        "kind": "nothing",
                        "editable": False,
                        "source": {"file": NPC_SOURCE, "line": drop.line},
                        "weight": drop.weight,
                        "derived": drop.derived_empty,
                        "lockedReason": (
                            "This outcome is calculated from the unused roll weight."
                            if drop.derived_empty
                            else "This fixed Nothing outcome is reference-only in this editor."
                        ),
                    }
                )

        denominator = table.total_weight
        editable = denominator > 0 and any(row["kind"] == "item" and row["weight"] > 0 for row in rows)
        groups.append(
            {
                "groupId": group_id,
                "kind": "npc",
                "title": title,
                "scope": "direct",
                "editable": editable,
                "source": {"file": NPC_SOURCE, "line": table.line},
                "denominator": denominator,
                "npcs": npcs,
                "rows": rows,
            }
        )
    return groups


def make_void_chest(
    item_ids: dict[str, int], item_defs: dict[int, dict[str, Any]]
) -> dict[str, Any]:
    text = VOID_CHEST.read_text(encoding="utf-8")
    rewards_match = re.search(
        r"private\s+static\s+final\s+Reward\[\]\s+REWARDS\s*=\s*\{(.*?)\};",
        text,
        re.DOTALL,
    )
    if not rewards_match:
        raise ValueError("Could not find the Void Chest REWARDS array")
    rewards_body = rewards_match.group(1)
    rewards_offset = rewards_match.start(1)
    rows: list[dict[str, Any]] = []
    pattern = re.compile(
        r"new\s+Reward\(\s*ItemId\.([A-Z0-9_]+)\.id\(\)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)(?:\s*,\s*(true|false))?\s*\)",
        re.DOTALL,
    )
    matches = list(pattern.finditer(rewards_body))
    candidate_count = len(re.findall(r"new\s+Reward\s*\(", rewards_body))
    if len(matches) != candidate_count:
        raise ValueError(
            f"Parsed {len(matches)} of {candidate_count} Void Chest reward declarations"
        )
    line_occurrences: dict[int, int] = {}
    for match in matches:
        line_number = source_line(text, rewards_offset + match.start())
        line_occurrences[line_number] = line_occurrences.get(line_number, 0) + 1
        item_enum, minimum, maximum, weight, noted = match.groups()
        item_id = item_ids.get(item_enum)
        if item_id is None:
            raise ValueError(f"Unknown ItemId.{item_enum} in VoidChest.java line {line_number}")
        definition = item_defs.get(item_id)
        if definition is None:
            raise ValueError(f"Void Chest item {item_enum} ({item_id}) has no item definition")
        minimum_value = int(minimum)
        maximum_value = int(maximum)
        weight_value = int(weight)
        if minimum_value < 1 or maximum_value < minimum_value or weight_value < 1:
            raise ValueError(f"Invalid Void Chest reward values on line {line_number}")
        rows.append(
            {
                "rowId": f"void-chest-row-l{line_number}-{line_occurrences[line_number]}",
                "source": {"file": VOID_CHEST_SOURCE, "line": line_number},
                "itemId": item_id,
                "itemName": str(definition.get("name") or prettify_enum(item_enum)),
                "minAmount": minimum_value,
                "maxAmount": maximum_value,
                "weight": weight_value,
                "noted": noted == "true",
                "sprite": item_sprite(item_id),
            }
        )
    total_weight = sum(row["weight"] for row in rows)
    if not rows or total_weight < 1:
        raise ValueError("Void Chest must contain at least one positive-weight reward")
    return {
        "groupId": "void-chest",
        "title": "Void Chest",
        "source": {
            "file": VOID_CHEST_SOURCE,
            "line": source_line(text, rewards_match.start()),
        },
        "totalWeight": total_weight,
        "rows": rows,
    }


def generate() -> dict[str, Any]:
    item_ids = parse_enum(ITEM_ID)
    npc_ids = parse_enum(NPC_ID)
    item_defs = load_defs((ITEM_DEFS, ITEM_DEFS_CUSTOM), ("item", "items"))
    npc_defs = load_defs((NPC_DEFS, NPC_DEFS_CUSTOM), ("npcs", "npc"))
    npc_to_table, _ = parse_npc_tables(item_ids, npc_ids)
    groups = make_npc_groups(npc_to_table, npc_ids, npc_defs, item_defs)
    fingerprint, files = source_manifest()
    return {
        "schemaVersion": 1,
        "source": {
            "fingerprint": fingerprint,
            "generatedBy": "tools/generate-loot-editor-data.py",
            "assumptions": [
                "WANT_OPENPK_POINTS=false",
                "Source-wide npcDrops.put declarations, including custom feature-gated tables, matching /npcs",
            ],
            "files": files,
        },
        "items": make_items(item_defs),
        "groups": groups,
        "voidChest": make_void_chest(item_ids, item_defs),
    }


def render(data: dict[str, Any]) -> str:
    return f"{json.dumps(data, ensure_ascii=False, indent=2)}\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true", help="fail if the generated file is stale")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output = args.output if args.output.is_absolute() else REPO_ROOT / args.output
    generated = render(generate())
    if args.check:
        if not output.exists():
            print(f"missing generated loot editor data: {relative(output)}", file=sys.stderr)
            return 1
        if output.read_text(encoding="utf-8") != generated:
            print(f"stale generated loot editor data: {relative(output)}", file=sys.stderr)
            return 1
        print(f"loot editor data is current: {relative(output)}")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(generated, encoding="utf-8")
    data = json.loads(generated)
    print(
        f"wrote {relative(output)} with {len(data['groups'])} NPC tables, "
        f"{sum(len(group['npcs']) for group in data['groups'])} NPC mappings, "
        f"and {len(data['voidChest']['rows'])} Void Chest rewards"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
