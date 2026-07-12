#!/usr/bin/env python3
from __future__ import annotations

import html
import json
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass, field
from fractions import Fraction
from pathlib import Path

from PIL import Image

REPO_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO_ROOT / "tools" / "voidscape-content"))
sys.path.insert(0, str(REPO_ROOT / "tools" / "voidscim-art"))

from voidscape_content.java_parse import load_client_items, split_java_args
from voidscape_content.paths import AUTHENTIC_SPRITES, SPRITE_ITEM
from extract_ref import decode as decode_sprite

NPC_DROPS = REPO_ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "NpcDrops.java"
NPC_ID = REPO_ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "NpcId.java"
ITEM_ID = REPO_ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "ItemId.java"
NPC_DEFS = REPO_ROOT / "server" / "conf" / "server" / "defs" / "NpcDefs.json"
NPC_DEFS_CUSTOM = REPO_ROOT / "server" / "conf" / "server" / "defs" / "NpcDefsCustom.json"
NPC_LOCS_DIR = REPO_ROOT / "server" / "conf" / "server" / "defs" / "locs"
CLIENT_BASE = REPO_ROOT / "Client_Base"
CLIENT_JAR = CLIENT_BASE / "Open_RSC_Client.jar"
REPORT_DIR = REPO_ROOT / "docs" / "reports"
ASSET_DIR = REPORT_DIR / "drop-table-assets"
NPC_ASSET_DIR = ASSET_DIR / "npc"
ITEM_ASSET_DIR = ASSET_DIR / "item"
REPORT_PATH = REPORT_DIR / "drop-table-balance.html"
PORTAL_DIR = REPO_ROOT / "web" / "portal"
PORTAL_ASSET_DIR = PORTAL_DIR / "assets" / "npc-database"
PORTAL_NPC_ASSET_DIR = PORTAL_ASSET_DIR / "npc"
PORTAL_ITEM_ASSET_DIR = PORTAL_ASSET_DIR / "item"
PORTAL_NPCS_PATH = PORTAL_DIR / "npcs.html"

BG = (40, 40, 48)

REPORT_NPCS = [
    ("Void Dungeon", "VOID_SPIDER"),
    ("Void Dungeon", "VOID_UNICORN"),
    ("Void Dungeon", "VOID_WIZARD"),
    ("Void Dungeon", "VOID_WOLF"),
    ("Void Dungeon", "VOID_GIANT"),
    ("Void Dungeon", "VOID_OGRE"),
    ("Void Dungeon", "VOID_KNIGHT_VOIDBORN"),
    ("Void Dungeon", "VOID_DEMON"),
    ("Wilderness / Underground", "HOBGOBLIN_LVL32"),
    ("Wilderness / Underground", "GIANT"),
    ("Wilderness / Underground", "MOSS_GIANT"),
    ("Wilderness / Underground", "ICE_GIANT"),
    ("Wilderness / Underground", "ICE_WARRIOR"),
    ("Wilderness / Underground", "CHAOS_DWARF"),
    ("Wilderness / Underground", "GREATER_DEMON"),
    ("Wilderness / Underground", "BLACK_DEMON"),
    ("Wilderness / Underground", "EARTH_WARRIOR"),
    ("Wilderness / Underground", "SHADOW_WARRIOR"),
    ("Dragons", "RED_DRAGON"),
    ("Dragons", "BLUE_DRAGON"),
    ("Dragons", "BLACK_DRAGON"),
    ("Mid-Level Undead", "SKELETON_LVL21"),
    ("Mid-Level Undead", "ZOMBIE_LVL24_GEN"),
    ("Mid-Level Undead", "SKELETON_LVL25"),
    ("Mid-Level Undead", "SKELETON_LVL31"),
    ("Mid-Level Undead", "ZOMBIE_LVL32"),
]

WILDERNESS_NPCS = {
    "HOBGOBLIN_LVL32",
    "GIANT",
    "MOSS_GIANT",
    "ICE_GIANT",
    "ICE_WARRIOR",
    "CHAOS_DWARF",
    "GREATER_DEMON",
    "BLACK_DEMON",
    "EARTH_WARRIOR",
    "SHADOW_WARRIOR",
    "RED_DRAGON",
    "BLACK_DRAGON",
}

DRAGON_WEAPON_RARE_NPC_DROPS = {
    "LESSER_DEMON": (("DRAGON_SWORD_TIP", 1497),),
    "LESSER_DEMON_WMAZEKEY": (("DRAGON_SWORD_TIP", 1497),),
    "GREATER_DEMON": (("DRAGON_SWORD_HILT", 1248),),
    "FIRE_GIANT": (("DRAGON_SWORD_TIP", 999), ("DRAGON_MEDIUM_HELMET", 4995)),
    "BLUE_DRAGON": (("DRAGON_SWORD_BLADE", 999), ("LEFT_HALF_DRAGON_SQUARE_SHIELD", 4995)),
    "VOID_DEMON": (("DRAGON_SWORD_HILT", 999),),
    "RED_DRAGON": (("DRAGON_SWORD_BLADE", 749), ("DRAGON_MEDIUM_HELMET", 3745)),
    "BLACK_DEMON": (("DRAGON_SWORD_HILT", 749), ("DRAGON_AXE", 3745)),
    "BLACK_DRAGON": (
        ("DRAGON_SWORD_BLADE", 499),
        ("DRAGON_AXE", 2495),
        ("LEFT_HALF_DRAGON_SQUARE_SHIELD", 2495),
    ),
    "KING_BLACK_DRAGON": (
        ("DRAGON_SWORD_HILT", 100),
        ("DRAGON_SWORD_BLADE", 100),
        ("DRAGON_SWORD_TIP", 100),
        ("DRAGON_AXE", 500),
        ("DRAGON_MEDIUM_HELMET", 500),
        ("LEFT_HALF_DRAGON_SQUARE_SHIELD", 500),
    ),
}
DRAGON_WEAPON_RARE_NPCS = set(DRAGON_WEAPON_RARE_NPC_DROPS)


@dataclass
class Drop:
    kind: str
    name: str
    amount: str
    weight: int
    item_id: int | None = None
    noted: bool = False
    table_var: str | None = None


@dataclass
class ParsedTable:
    name: str
    line: int
    target_weight: int = 128
    drops: list[Drop] = field(default_factory=list)

    @property
    def roll_weight(self) -> int:
        return sum(drop.weight for drop in self.drops if drop.weight > 0)

    @property
    def empty_weight(self) -> int:
        return max(0, self.target_weight - self.roll_weight)

    @property
    def denominator(self) -> int:
        return max(self.target_weight, self.roll_weight + self.empty_weight)


def parse_enum(path: Path) -> dict[str, int]:
    out: dict[str, int] = {}
    for line in path.read_text().splitlines():
        match = re.search(r"^\s*([A-Z0-9_]+)\((\d+)\)", line)
        if match:
            out[match.group(1)] = int(match.group(2))
    return out


def load_server_npcs() -> dict[int, dict]:
    out: dict[int, dict] = {}
    for path in (NPC_DEFS, NPC_DEFS_CUSTOM):
        raw = json.loads(path.read_text())
        npcs = raw.get("npcs", raw if isinstance(raw, list) else [])
        for npc in npcs:
            out[int(npc["id"])] = npc
    return out


def loc_coord(point: dict, key: str) -> int | None:
    value = point.get(key)
    if value is None:
        value = point.get(key.lower())
    if value is None:
        return None
    return int(value)


def is_safe_zone(x: int, y: int) -> bool:
    safe_zones = (
        (97, 299, 129, 331),
        (15, 16, 33, 43),
        (32, 16, 47, 42),
    )
    return any(x > min_x and y > min_y and x < max_x and y < max_y for min_x, min_y, max_x, max_y in safe_zones)


def in_wilderness(x: int, y: int) -> bool:
    if is_safe_zone(x, y):
        return False
    wild = 2203 - (y + (1776 - (944 * int(y / 944))))
    if x + 2304 >= 2640:
        wild = -50
    return wild > 0 and (1 + wild // 6) >= 1


def iter_npc_locs(path: Path):
    try:
        raw = json.loads(path.read_text())
    except json.JSONDecodeError:
        return
    locs = raw.get("npclocs", raw if isinstance(raw, list) else [])
    for loc in locs:
        if not isinstance(loc, dict):
            continue
        npc_id = loc.get("id")
        start = loc.get("start") or {}
        x = loc_coord(start, "X")
        y = loc_coord(start, "Y")
        if npc_id is None or x is None or y is None:
            continue
        yield int(npc_id), x, y


def load_npc_loc_ids(path_predicate=None, loc_predicate=None) -> set[int]:
    out: set[int] = set()
    for path in sorted(NPC_LOCS_DIR.glob("NpcLocs*.json")):
        if path_predicate is not None and not path_predicate(path):
            continue
        for npc_id, x, y in iter_npc_locs(path):
            if loc_predicate is None or loc_predicate(npc_id, x, y):
                out.add(npc_id)
    return out


def parse_java_int(value: str) -> int | None:
    value = value.strip().replace("_", "")
    try:
        return int(value, 0)
    except ValueError:
        return None


def choose_openpk_false(text: str) -> str:
    pattern = re.compile(r"if\s*\(\s*config\.WANT_OPENPK_POINTS\s*\)\s*\{")
    while True:
        match = pattern.search(text)
        if not match:
            return text
        true_start = match.end()
        true_end = find_matching_brace(text, true_start - 1)
        rest = true_end + 1
        while rest < len(text) and text[rest].isspace():
            rest += 1
        if not text.startswith("else", rest):
            text = text[: match.start()] + text[true_end + 1 :]
            continue
        else_brace = text.find("{", rest)
        false_start = else_brace + 1
        false_end = find_matching_brace(text, else_brace)
        replacement = text[false_start:false_end]
        text = text[: match.start()] + replacement + text[false_end + 1 :]


def find_matching_brace(text: str, open_brace: int) -> int:
    depth = 0
    in_string = False
    escaped = False
    for i in range(open_brace, len(text)):
        char = text[i]
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
                return i
    raise ValueError("unbalanced braces while evaluating config block")


def amount_expr(value: str) -> str:
    value = value.strip()
    match = re.match(r"config\.WANT_OPENPK_POINTS\s*\?\s*([^:]+)\s*:\s*(.+)", value)
    if match:
        value = match.group(2).strip()
    return value


def weight_arg(value: str) -> int:
    parsed = parse_java_int(value)
    if parsed is None:
        raise ValueError(f"could not parse drop weight from {value!r}")
    return parsed


def noted_arg(args: list[str]) -> bool:
    return len(args) >= 4 and args[3].strip().lower() == "true"


def parse_drop_tables(item_ids: dict[str, int], item_names: dict[int, str]) -> dict[str, ParsedTable]:
    text = choose_openpk_false(NPC_DROPS.read_text())
    table_by_var: dict[str, ParsedTable] = {}
    npc_to_table: dict[str, ParsedTable] = {}

    for line_no, line in enumerate(text.splitlines(), 1):
        new_table = re.search(r"(?:DropTable\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*new\s+DropTable\(\"([^\"]+)\"", line)
        if new_table:
            table_by_var[new_table.group(1)] = ParsedTable(new_table.group(2), line_no)
            continue

        clone_table = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\.clone\((?:\"([^\"]*)\")?\)\s*;", line)
        if clone_table:
            dest_var = clone_table.group(1)
            source = table_by_var.get(clone_table.group(2))
            if source is not None:
                table_by_var[dest_var] = ParsedTable(
                    clone_table.group(3) or source.name,
                    source.line,
                    source.target_weight,
                    [Drop(**drop.__dict__) for drop in source.drops],
                )
            continue

        item_drop = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\.addItemDrop\((.*)\)\s*;", line)
        if item_drop and item_drop.group(1) in table_by_var:
            args = split_java_args(item_drop.group(2))
            item_match = re.search(r"ItemId\.([A-Z0-9_]+)\.id\(\)", args[0])
            if not item_match or len(args) < 3:
                continue
            item_name = item_match.group(1)
            item_id = item_ids.get(item_name)
            if item_id is None:
                continue
            table_by_var[item_drop.group(1)].drops.append(
                Drop(
                    kind="item",
                    name=item_names.get(item_id, prettify(item_name)),
                    amount=amount_expr(args[1]),
                    weight=weight_arg(args[2]),
                    item_id=item_id,
                    noted=noted_arg(args),
                )
            )
            continue

        table_drop = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\.addTableDrop\((.*)\)\s*;", line)
        if table_drop and table_drop.group(1) in table_by_var:
            args = split_java_args(table_drop.group(2))
            if len(args) < 2:
                continue
            table_var = args[0].strip()
            table_name = prettify(table_var)
            clone_arg = re.match(r"([A-Za-z_][A-Za-z0-9_]*)\.clone\(\"([^\"]+)\"\)", table_var)
            if clone_arg and clone_arg.group(1) in table_by_var:
                clone_var = f"{clone_arg.group(1)}_clone_{line_no}"
                source = table_by_var[clone_arg.group(1)]
                table_by_var[clone_var] = ParsedTable(
                    clone_arg.group(2),
                    source.line,
                    source.target_weight,
                    [Drop(**drop.__dict__) for drop in source.drops],
                )
                table_var = clone_var
                table_name = clone_arg.group(2)
            elif re.match(r"^[A-Za-z_][A-Za-z0-9_]*$", table_var):
                table_name = table_by_var.get(table_var, ParsedTable(prettify(table_var), line_no)).name
            else:
                table_var = None
            table_by_var[table_drop.group(1)].drops.append(
                Drop(kind="table", name=table_name, amount="", weight=weight_arg(args[1]), table_var=table_var)
            )
            continue

        put = re.search(r"this\.npcDrops\.put\(NpcId\.([A-Z0-9_]+)\.id\(\),\s*([A-Za-z_][A-Za-z0-9_]*)\)", line)
        if put and put.group(2) in table_by_var:
            npc_to_table[put.group(1)] = table_by_var[put.group(2)]
            continue

        empty = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\.addEmptyDrop\((\d+) - \1\.getTotalWeight\(\)\)", line)
        if empty and empty.group(1) in table_by_var:
            table_by_var[empty.group(1)].target_weight = int(empty.group(2))
            continue

        direct_empty = re.search(r"([A-Za-z_][A-Za-z0-9_]*)\.addEmptyDrop\((\d+)\)", line)
        if direct_empty and direct_empty.group(1) in table_by_var:
            table_by_var[direct_empty.group(1)].drops.append(
                Drop(kind="empty", name="Nothing", amount="", weight=int(direct_empty.group(2)))
            )

    return npc_to_table, table_by_var


def prettify(name: str) -> str:
    name = re.sub(r"([a-z])([A-Z])", r"\1 \2", name)
    name = name.replace("ItemId.", "").replace(".id()", "")
    return name.replace("_", " ").title()


def display_npc_name(name: str) -> str:
    return " ".join(part[:1].upper() + part[1:] for part in name.split())


def display_item_name(name: str) -> str:
    improved_toggle = re.fullmatch(r'Config\.S_IMPROVED_ITEM_OBJECT_NAMES\s*\?\s*"([^"]*)"\s*:\s*"([^"]*)"', name)
    if improved_toggle:
        return improved_toggle.group(1)
    improved_prefix = re.fullmatch(r'\(Config\.S_IMPROVED_ITEM_OBJECT_NAMES\s*\?\s*"([^"]*)"\s*:\s*""\)\s*\+\s*"([^"]*)"', name)
    if improved_prefix:
        return f"{improved_prefix.group(1)}{improved_prefix.group(2)}"
    return name


def crop_preview(src: Path, dest: Path) -> None:
    image = Image.open(src).convert("RGBA")
    pixels = image.load()
    bbox = None
    for y in range(image.height):
        for x in range(image.width):
            r, g, b, a = pixels[x, y]
            if a and (r, g, b) != BG:
                bbox = (x, y, x + 1, y + 1) if bbox is None else (
                    min(bbox[0], x), min(bbox[1], y), max(bbox[2], x + 1), max(bbox[3], y + 1)
                )
    if bbox is None:
        shutil.copyfile(src, dest)
        return
    pad = 12
    bbox = (
        max(0, bbox[0] - pad),
        max(0, bbox[1] - pad),
        min(image.width, bbox[2] + pad),
        min(image.height, bbox[3] + pad),
    )
    cropped = image.crop(bbox)
    data = []
    for r, g, b, a in cropped.getdata():
        data.append((r, g, b, 0) if (r, g, b) == BG else (r, g, b, a))
    cropped.putdata(data)
    dest.parent.mkdir(parents=True, exist_ok=True)
    cropped.save(dest)


def ensure_npc_sprite(
    npc_id: int,
    asset_dir: Path = NPC_ASSET_DIR,
    cache_dir: Path | None = None,
) -> Path:
    dest = asset_dir / f"{npc_id}.png"
    if dest.exists():
        return dest
    cached = cache_dir / dest.name if cache_dir is not None else None
    if cached is not None and cached.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cached, dest)
        return dest
    with tempfile.TemporaryDirectory(prefix="npc-preview-") as tmp:
        tmp_path = Path(tmp)
        subprocess.run(
            [
                "java",
                "-Djava.awt.headless=true",
                "-cp",
                str(CLIENT_JAR.name),
                "tools.NpcPreview",
                "Cache",
                "--dump",
                str(tmp_path),
                str(npc_id),
            ],
            cwd=CLIENT_BASE,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
        crop_preview(tmp_path / "dir4.png", dest)
    return dest


def ensure_item_sprite(
    item_id: int,
    sprite_id: int,
    asset_dir: Path = ITEM_ASSET_DIR,
    cache_dir: Path | None = None,
) -> Path:
    dest = asset_dir / f"{item_id}.png"
    if dest.exists():
        return dest
    cached = cache_dir / dest.name if cache_dir is not None else None
    if cached is not None and cached.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(cached, dest)
        return dest
    with zipfile.ZipFile(AUTHENTIC_SPRITES, "r") as archive:
        image, _ = decode_sprite(archive.read(str(SPRITE_ITEM + sprite_id)))
    dest.parent.mkdir(parents=True, exist_ok=True)
    image.save(dest)
    return dest


def rel(path: Path) -> str:
    return path.relative_to(REPORT_DIR).as_posix()


def chance_text(weight: int, denominator: int) -> tuple[str, str]:
    if weight <= 0:
        return "Guaranteed", "Guaranteed"
    one_in = max(1, int((denominator / weight) + 0.5))
    exact = f"{weight}/{denominator}"
    return f"1/{one_in}", f"{exact} exact table weight"


def fraction_chance_text(chance: Fraction) -> tuple[str, str]:
    if chance <= 0:
        return "0", "0 exact chance"
    if chance >= 1:
        return "Guaranteed", "Guaranteed"
    one_in = max(1, int((chance.denominator / chance.numerator) + 0.5))
    return f"1/{one_in}", f"{chance.numerator}/{chance.denominator} exact chance"


def flatten_table(
    table: ParsedTable,
    table_by_var: dict[str, ParsedTable],
    base_chance: Fraction = Fraction(1, 1),
    seen: tuple[str, ...] = (),
) -> list[dict]:
    rows: list[dict] = []
    denominator = table.denominator
    for drop in table.drops:
        if drop.kind == "empty":
            continue
        chance = base_chance
        if drop.weight > 0 and denominator > 0:
            chance *= Fraction(drop.weight, denominator)
        if drop.kind == "item":
            rows.append({
                "kind": "item",
                "name": drop.name,
                "amount": drop.amount,
                "item_id": drop.item_id,
                "noted": drop.noted,
                "chance": chance,
            })
            continue
        if drop.kind == "table" and drop.table_var:
            if drop.table_var in seen:
                continue
            child = table_by_var.get(drop.table_var)
            if child is not None:
                rows.extend(flatten_table(child, table_by_var, chance, seen + (drop.table_var,)))
                continue
        rows.append({
            "kind": "table",
            "name": drop.name,
            "amount": "",
            "item_id": None,
            "noted": False,
            "chance": chance,
        })
    return rows


def effective_empty_chance(
    table: ParsedTable,
    table_by_var: dict[str, ParsedTable],
    seen: tuple[str, ...] = (),
) -> Fraction:
    denominator = table.denominator
    if denominator <= 0:
        return Fraction(0, 1)
    chance = Fraction(table.empty_weight, denominator)
    for drop in table.drops:
        drop_chance = Fraction(drop.weight, denominator) if drop.weight > 0 else Fraction(1, 1)
        if drop.kind == "empty":
            chance += drop_chance
        elif drop.kind == "table" and drop.table_var and drop.table_var not in seen:
            child = table_by_var.get(drop.table_var)
            if child is not None:
                chance += drop_chance * effective_empty_chance(
                    child, table_by_var, seen + (drop.table_var,)
                )
    return min(Fraction(1, 1), chance)


def dragon_weapon_bonus_rows(
    enum_name: str,
    item_ids: dict[str, int],
    item_names: dict[int, str],
) -> list[dict]:
    rare_drops = DRAGON_WEAPON_RARE_NPC_DROPS.get(enum_name)
    if rare_drops is None:
        return []
    rows: list[dict] = []
    for item_name, denominator in rare_drops:
        item_id = item_ids.get(item_name)
        if item_id is None:
            continue
        display_name = item_names.get(item_id, prettify(item_name))
        if item_name == "LEFT_HALF_DRAGON_SQUARE_SHIELD":
            display_name = "Left half dragon square shield"
        rows.append({
            "kind": "item",
            "name": display_name,
            "amount": "1",
            "item_id": item_id,
            "noted": False,
            "chance": Fraction(1, denominator),
            "source": "Dragon equipment bonus roll",
        })
    return rows


def build_portal_drops(
    table: ParsedTable,
    table_by_var: dict[str, ParsedTable],
    item_sprite_by_id: dict[int, Path],
    bonus_rows: list[dict] | None = None,
) -> list[dict]:
    accumulator: dict[tuple, dict] = {}
    rows = flatten_table(table, table_by_var)
    if bonus_rows:
        rows.extend(bonus_rows)
    for row in rows:
        key = (row["kind"], row["item_id"], row["amount"], row["noted"], row["name"])
        current = accumulator.get(key)
        if current is None:
            current = dict(row)
            accumulator[key] = current
        else:
            current["chance"] += row["chance"]

    out: list[dict] = []
    for row in accumulator.values():
        chance, exact = fraction_chance_text(row["chance"])
        display_name = row["name"]
        if row["kind"] == "item":
            amount = "" if row["amount"] == "1" else f" x{row['amount']}"
            noted = " noted" if row["noted"] else ""
            display_name = f"{row['name']}{amount}{noted}"
        drop = {
            "kind": row["kind"],
            "name": row["name"],
            "displayName": display_name,
            "amount": row["amount"],
            "chance": chance,
            "exact": f"{exact}; {row['source']}" if row.get("source") else exact,
            "_sortChance": row["chance"],
        }
        item_id = row["item_id"]
        if item_id is not None:
            drop["itemId"] = item_id
            drop["noted"] = row["noted"]
            if item_id in item_sprite_by_id:
                drop["sprite"] = portal_asset_url("item", item_id)
        out.append(drop)

    out.sort(key=lambda row: (-float(row["_sortChance"]), row["name"].lower(), str(row.get("amount", ""))))
    for row in out:
        row.pop("_sortChance", None)
    return out


def render_drop(drop: Drop, item_sprite_by_id: dict[int, Path], denominator: int) -> str:
    if drop.kind == "empty":
        chance, title = chance_text(drop.weight, denominator)
        return f"""
        <li class="drop empty">
          <span class="sprite blank"></span>
          <span class="drop-name">Nothing</span>
          <span class="chance" title="{html.escape(title)}">{chance}</span>
        </li>"""
    if drop.kind == "table":
        chance, title = chance_text(drop.weight, denominator)
        return f"""
        <li class="drop table-roll">
          <span class="sprite table-icon">T</span>
          <span class="drop-name">{html.escape(drop.name)}</span>
          <span class="chance" title="{html.escape(title)}">{chance}</span>
        </li>"""
    sprite = item_sprite_by_id.get(drop.item_id or -1)
    img = f'<img src="{html.escape(rel(sprite))}" alt="">' if sprite else ""
    amount = "" if drop.amount == "1" else f" x{html.escape(drop.amount)}"
    noted = ' <span class="noted">noted</span>' if drop.noted else ""
    chance, title = chance_text(drop.weight, denominator)
    return f"""
        <li class="drop">
          <span class="sprite">{img}</span>
          <span class="drop-name">{html.escape(drop.name)}{amount}{noted}</span>
          <span class="chance" title="{html.escape(title)}">{chance}</span>
        </li>"""


def render_flat_drop(row: dict, item_sprite_by_id: dict[int, Path]) -> str:
    sprite = item_sprite_by_id.get(row.get("item_id") or -1)
    img = f'<img src="{html.escape(rel(sprite))}" alt="">' if sprite else ""
    amount = "" if row.get("amount") == "1" else f" x{html.escape(row.get('amount', ''))}"
    noted = ' <span class="noted">noted</span>' if row.get("noted") else ""
    chance, title = fraction_chance_text(row["chance"])
    css_class = "drop table-roll" if row.get("kind") == "table" else "drop"
    sprite_html = '<span class="sprite table-icon">T</span>' if row.get("kind") == "table" else f'<span class="sprite">{img}</span>'
    return f"""
        <li class="{css_class}">
          {sprite_html}
          <span class="drop-name">{html.escape(row['name'])}{amount}{noted}</span>
          <span class="chance" title="{html.escape(title)}">{chance}</span>
        </li>"""


def render_empty_chance(chance: Fraction) -> str:
    display, title = fraction_chance_text(chance)
    return f"""
        <li class="drop empty">
          <span class="sprite blank"></span>
          <span class="drop-name">Nothing</span>
          <span class="chance" title="{html.escape(title)}">{display}</span>
        </li>"""


def build_html(cards_by_section: dict[str, list[str]]) -> str:
    sections = "\n".join(
        f"""
        <section>
          <h2>{html.escape(section)}</h2>
          <div class="grid">
            {''.join(cards)}
          </div>
        </section>"""
        for section, cards in cards_by_section.items()
    )
    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Voidscape Drop Table Balance Report</title>
  <style>
    :root {{
      color-scheme: dark;
      --bg: #111418;
      --panel: #1b2027;
      --panel-2: #222934;
      --line: #35404e;
      --text: #edf1f5;
      --muted: #9faaaf;
      --accent: #89d18f;
      --void: #aeb4ff;
    }}
    * {{ box-sizing: border-box; }}
    body {{
      margin: 0;
      font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      background: var(--bg);
      color: var(--text);
    }}
    header {{
      padding: 28px clamp(18px, 4vw, 44px) 18px;
      border-bottom: 1px solid var(--line);
      background: #171b21;
    }}
    h1 {{ margin: 0 0 8px; font-size: clamp(24px, 4vw, 42px); font-weight: 760; }}
    p {{ margin: 0; color: var(--muted); max-width: 960px; line-height: 1.45; }}
    section {{ padding: 24px clamp(18px, 4vw, 44px); }}
    h2 {{ margin: 0 0 14px; font-size: 20px; letter-spacing: 0; }}
    .grid {{
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 16px;
      align-items: start;
    }}
    .card {{
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      overflow: hidden;
      min-width: 0;
    }}
    .npc-head {{
      display: grid;
      grid-template-columns: 112px minmax(0, 1fr);
      gap: 14px;
      padding: 14px;
      align-items: center;
      background: var(--panel-2);
      border-bottom: 1px solid var(--line);
    }}
    .npc-art {{
      width: 112px;
      height: 132px;
      display: grid;
      place-items: center;
      background: #12151a;
      border: 1px solid var(--line);
      border-radius: 6px;
      overflow: hidden;
    }}
    .npc-art img {{ max-width: 106px; max-height: 126px; object-fit: contain; image-rendering: pixelated; }}
    .npc-title {{ min-width: 0; }}
    .npc-title h3 {{ margin: 0 0 6px; font-size: 18px; line-height: 1.2; }}
    .meta {{ display: flex; flex-wrap: wrap; gap: 6px; color: var(--muted); font-size: 12px; }}
    .pill {{
      border: 1px solid var(--line);
      border-radius: 999px;
      padding: 3px 8px;
      background: #171b21;
    }}
    .pill.good {{ color: var(--accent); border-color: #4d7c55; }}
    .pill.void {{ color: var(--void); border-color: #6067a5; }}
    .drops {{
      list-style: none;
      margin: 0;
      padding: 8px 10px 12px;
      display: grid;
      gap: 6px;
    }}
    .drop {{
      display: grid;
      grid-template-columns: 30px minmax(0, 1fr) auto;
      gap: 8px;
      align-items: center;
      min-height: 34px;
      padding: 4px 6px;
      border-radius: 6px;
      background: #15191f;
    }}
    .sprite {{
      width: 28px;
      height: 28px;
      display: grid;
      place-items: center;
      overflow: hidden;
    }}
    .sprite img {{ max-width: 28px; max-height: 28px; image-rendering: pixelated; }}
    .blank {{ border: 1px dashed #535c68; border-radius: 4px; }}
    .table-icon {{
      border: 1px solid #65728a;
      border-radius: 4px;
      color: var(--muted);
      font-size: 12px;
      font-weight: 800;
    }}
    .drop-name {{
      min-width: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
    }}
    .chance {{
      color: var(--muted);
      font-variant-numeric: tabular-nums;
      font-size: 12px;
      white-space: nowrap;
    }}
    .noted {{
      color: var(--accent);
      font-size: 10px;
      font-weight: 800;
      margin-left: 4px;
      text-transform: uppercase;
    }}
    .empty {{ opacity: 0.58; }}
  </style>
</head>
<body>
  <header>
    <h1>Voidscape Drop Table Balance Report</h1>
    <p>Generated from <code>NpcDrops.java</code> with local OpenPK drops treated as disabled. Weight-based drops show rounded one-in odds; hover an odds value for its exact table weight.</p>
  </header>
  {sections}
</body>
</html>
"""


def portal_asset_url(kind: str, asset_id: int) -> str:
    return f"assets/npc-database/{kind}/{asset_id}.png"


def is_void_npc(npc_id: int, enum_name: str, void_npc_ids: set[int]) -> bool:
    return npc_id in void_npc_ids or enum_name.startswith("VOID_")


def is_wilderness_npc(npc_id: int, enum_name: str, wilderness_npc_ids: set[int], section: str = "") -> bool:
    return npc_id in wilderness_npc_ids or enum_name in WILDERNESS_NPCS or "Wilderness" in section


def npc_tags(section: str, enum_name: str, npc_id: int, wilderness_npc_ids: set[int], void_npc_ids: set[int]) -> list[str]:
    tags = ["all"]
    if is_void_npc(npc_id, enum_name, void_npc_ids):
        tags.append("void")
    if is_wilderness_npc(npc_id, enum_name, wilderness_npc_ids, section) and not is_void_npc(npc_id, enum_name, void_npc_ids):
        tags.append("wilderness")
    return tags


def npc_badges(section: str, enum_name: str, npc_id: int, wilderness_npc_ids: set[int], void_npc_ids: set[int]) -> list[str]:
    badges = []
    if section:
        badges.append(section)
    if is_void_npc(npc_id, enum_name, void_npc_ids):
        badges.append("Void")
    if is_wilderness_npc(npc_id, enum_name, wilderness_npc_ids, section) and not is_void_npc(npc_id, enum_name, void_npc_ids):
        badges.append("Wilderness")
    if "DRAGON" in enum_name:
        badges.append("Dragon")
    if not badges:
        badges.append("Standard")
    return list(dict.fromkeys(badges))


def drop_display_name(drop: Drop) -> str:
    if drop.kind == "empty":
        return "Nothing"
    if drop.kind == "table":
        return drop.name
    amount = "" if drop.amount == "1" else f" x{drop.amount}"
    noted = " noted" if drop.noted else ""
    return f"{drop.name}{amount}{noted}"


def build_portal_drop(drop: Drop, item_sprite_by_id: dict[int, Path], denominator: int) -> dict:
    chance, title = chance_text(drop.weight, denominator)
    out = {
        "kind": drop.kind,
        "name": drop.name,
        "displayName": drop_display_name(drop),
        "amount": drop.amount,
        "chance": chance,
        "exact": title,
        "weight": drop.weight,
        "denominator": denominator,
    }
    if drop.item_id is not None:
        out["itemId"] = drop.item_id
        out["noted"] = drop.noted
        if drop.item_id in item_sprite_by_id:
            out["sprite"] = portal_asset_url("item", drop.item_id)
    return out


def render_portal_html(data: dict) -> str:
    data_json = json.dumps(data, ensure_ascii=True, separators=(",", ":")).replace("</", "<\\/")
    return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Voidscape NPC Drops</title>
  <meta name="description" content="Search Voidscape NPC drop tables with NPC sprites, item sprites, odds, and tabs for Wilderness and Void NPCs.">
  <link rel="icon" href="assets/favicon.png">
  <link rel="preload" as="font" type="font/woff2" href="assets/instrument-sans-latin-400-600.woff2" crossorigin>
  <style>
    @font-face { font-family: InstrumentSans; src: url("assets/instrument-sans-latin-400-600.woff2") format("woff2"); font-weight: 400 700; font-display: swap; }
    :root {
      color-scheme: dark;
      --bg: #101113;
      --panel: #191b1f;
      --panel-2: #202329;
      --line: #363b43;
      --text: #f0eee7;
      --muted: #aaa69b;
      --gold: #d8b968;
      --teal: #69c9b4;
      --danger: #d86f62;
      --void: #b8a1ff;
    }
    * { box-sizing: border-box; }
    html { background: var(--bg); }
    body { margin: 0; font-family: InstrumentSans, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background: radial-gradient(circle at top left, rgba(105, 201, 180, 0.16), transparent 32rem), var(--bg); color: var(--text); }
    a { color: inherit; }
    .topbar { min-height: 72px; display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 18px clamp(16px, 4vw, 48px); border-bottom: 1px solid rgba(255,255,255,0.08); background: rgba(16,17,19,0.82); position: sticky; top: 0; z-index: 10; backdrop-filter: blur(16px); }
    .brand img { width: 190px; max-width: 44vw; display: block; }
    .nav { display: flex; align-items: center; gap: 16px; color: var(--muted); font-size: 14px; }
    .nav a { text-decoration: none; }
    .nav a:hover { color: var(--text); }
    main { width: min(1360px, calc(100% - 32px)); margin: 0 auto; padding: 34px 0 54px; }
    .hero { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 24px; align-items: end; margin-bottom: 22px; }
    h1 { margin: 0 0 10px; font-size: clamp(32px, 6vw, 64px); line-height: 0.95; letter-spacing: 0; }
    .lead { margin: 0; color: var(--muted); line-height: 1.55; max-width: 760px; }
    .stats { display: grid; grid-template-columns: repeat(3, minmax(92px, 1fr)); gap: 10px; min-width: min(420px, 100%); }
    .stat { border: 1px solid var(--line); background: rgba(25,27,31,0.88); border-radius: 8px; padding: 12px; }
    .stat strong { display: block; color: var(--gold); font-size: 24px; line-height: 1; }
    .stat span { display: block; margin-top: 6px; color: var(--muted); font-size: 12px; }
    .toolbar { display: grid; grid-template-columns: minmax(220px, 1fr) auto; gap: 12px; align-items: center; padding: 12px; border: 1px solid var(--line); border-radius: 8px; background: rgba(25,27,31,0.82); position: sticky; top: 73px; z-index: 9; backdrop-filter: blur(16px); }
    .search { display: flex; align-items: center; gap: 10px; min-width: 0; border: 1px solid #454a54; border-radius: 6px; background: #0f1012; padding: 0 12px; }
    .search svg { width: 18px; height: 18px; color: var(--muted); flex: 0 0 auto; }
    .search input { width: 100%; min-width: 0; height: 42px; border: 0; outline: 0; background: transparent; color: var(--text); font: inherit; }
    .tabs { display: flex; align-items: center; gap: 6px; padding: 4px; border: 1px solid #454a54; border-radius: 8px; background: #0f1012; }
    .tab { min-width: 92px; height: 34px; border: 0; border-radius: 6px; background: transparent; color: var(--muted); font: inherit; cursor: pointer; }
    .tab[aria-selected="true"] { color: #101113; background: var(--gold); }
    .summary { min-height: 24px; margin: 14px 2px 12px; color: var(--muted); font-size: 14px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 14px; align-items: start; }
    .card { min-width: 0; overflow: hidden; border: 1px solid var(--line); border-radius: 8px; background: rgba(25,27,31,0.94); box-shadow: 0 16px 36px rgba(0,0,0,0.24); }
    .npc-head { display: grid; grid-template-columns: 116px minmax(0, 1fr); gap: 14px; min-height: 148px; padding: 14px; background: linear-gradient(135deg, rgba(32,35,41,0.98), rgba(23,24,27,0.98)); border-bottom: 1px solid var(--line); }
    .npc-art { width: 116px; height: 124px; display: grid; place-items: center; border: 1px solid #454a54; border-radius: 6px; background: #111214; overflow: hidden; }
    .npc-art img { max-width: 110px; max-height: 118px; image-rendering: pixelated; object-fit: contain; }
    .npc-title { min-width: 0; display: flex; flex-direction: column; justify-content: center; gap: 10px; }
    .npc-title h2 { margin: 0; font-size: 22px; line-height: 1.1; letter-spacing: 0; overflow-wrap: anywhere; }
    .meta { display: flex; flex-wrap: wrap; gap: 6px; }
    .pill { display: inline-flex; align-items: center; min-height: 24px; padding: 3px 8px; border: 1px solid #474c55; border-radius: 999px; color: var(--muted); background: rgba(15,16,18,0.7); font-size: 12px; white-space: nowrap; }
    .pill.gold { color: var(--gold); border-color: rgba(216,185,104,0.55); }
    .pill.teal { color: var(--teal); border-color: rgba(105,201,180,0.5); }
    .pill.void { color: var(--void); border-color: rgba(184,161,255,0.5); }
    .drop-list { display: grid; gap: 5px; margin: 0; padding: 10px; list-style: none; }
    .drop { display: grid; grid-template-columns: 34px minmax(0, 1fr) auto; align-items: center; gap: 8px; min-height: 38px; padding: 5px 8px; border-radius: 6px; background: #121316; }
    .drop.empty { opacity: 0.58; }
    .sprite { width: 30px; height: 30px; display: grid; place-items: center; overflow: hidden; }
    .sprite img { max-width: 30px; max-height: 30px; image-rendering: pixelated; }
    .sprite.table { border: 1px solid #575d68; border-radius: 5px; color: var(--teal); font-size: 12px; font-weight: 700; }
    .sprite.blank { border: 1px dashed #575d68; border-radius: 5px; }
    .drop-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
    .chance { color: var(--gold); font-variant-numeric: tabular-nums; font-size: 13px; white-space: nowrap; }
    .empty-state { display: none; padding: 36px 18px; border: 1px solid var(--line); border-radius: 8px; background: rgba(25,27,31,0.85); color: var(--muted); text-align: center; }
    .empty-state.visible { display: block; }
    @media (max-width: 840px) {
      .hero { grid-template-columns: 1fr; }
      .stats { grid-template-columns: repeat(3, 1fr); min-width: 0; }
      .toolbar { grid-template-columns: 1fr; top: 69px; }
      .tabs { overflow-x: auto; }
      .tab { min-width: 86px; }
    }
    @media (max-width: 520px) {
      .topbar { align-items: flex-start; flex-direction: column; gap: 10px; }
      .nav { width: 100%; justify-content: space-between; gap: 8px; }
      main { width: min(100% - 20px, 1360px); padding-top: 24px; }
      .stats { grid-template-columns: 1fr; }
      .grid { grid-template-columns: 1fr; }
      .npc-head { grid-template-columns: 94px minmax(0, 1fr); min-height: 126px; gap: 10px; padding: 10px; }
      .npc-art { width: 94px; height: 106px; }
      .npc-art img { max-width: 88px; max-height: 100px; }
      .npc-title h2 { font-size: 19px; }
      .drop-name { font-size: 13px; }
    }
  </style>
</head>
<body>
  <header class="topbar">
    <a class="brand" href="/" aria-label="Voidscape home"><img src="assets/voidscape-wordmark.png" alt="Voidscape"></a>
    <nav class="nav" aria-label="Site navigation">
      <a href="/">Home</a>
      <a href="/features">Features</a>
      <a href="/discord">Discord</a>
    </nav>
  </header>
  <main>
    <section class="hero" aria-labelledby="page-title">
      <div>
        <h1 id="page-title">NPC Drops</h1>
        <p class="lead">Search the current Voidscape drop tables by NPC or item. Odds are rounded one-in values from the server weights; contextual extras like bones, ashes, Void Keys, Wilderness bonuses, and drop-only dragon equipment rolls sit on top of these tables in-game.</p>
      </div>
      <div class="stats" aria-label="Drop table totals">
        <div class="stat"><strong id="total-npcs">0</strong><span>NPC tables</span></div>
        <div class="stat"><strong id="total-void">0</strong><span>Void NPCs</span></div>
        <div class="stat"><strong id="total-wilderness">0</strong><span>Wilderness</span></div>
      </div>
    </section>
    <section class="toolbar" aria-label="NPC drop controls">
      <label class="search" for="search">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M10.5 18a7.5 7.5 0 1 1 5.3-2.2l4.2 4.2-1.4 1.4-4.2-4.2A7.47 7.47 0 0 1 10.5 18Zm0-2a5.5 5.5 0 1 0 0-11 5.5 5.5 0 0 0 0 11Z" fill="currentColor"/></svg>
        <input id="search" type="search" autocomplete="off" spellcheck="false" placeholder="Search NPCs, drops, item ids">
      </label>
      <div class="tabs" role="tablist" aria-label="NPC category">
        <button class="tab" type="button" role="tab" aria-selected="true" data-tab="all">All</button>
        <button class="tab" type="button" role="tab" aria-selected="false" data-tab="wilderness">Wilderness</button>
        <button class="tab" type="button" role="tab" aria-selected="false" data-tab="void">Void</button>
      </div>
    </section>
    <p class="summary" id="summary"></p>
    <section class="grid" id="grid" aria-live="polite"></section>
    <div class="empty-state" id="empty-state">No NPC drops match that search.</div>
  </main>
  <script type="application/json" id="npc-data">__NPC_DATA__</script>
  <script>
    const data = JSON.parse(document.getElementById("npc-data").textContent);
    const grid = document.getElementById("grid");
    const search = document.getElementById("search");
    const summary = document.getElementById("summary");
    const emptyState = document.getElementById("empty-state");
    const tabs = Array.from(document.querySelectorAll(".tab"));
    let activeTab = "all";

    document.getElementById("total-npcs").textContent = data.npcs.length;
    document.getElementById("total-void").textContent = data.npcs.filter((npc) => npc.tags.includes("void")).length;
    document.getElementById("total-wilderness").textContent = data.npcs.filter((npc) => npc.tags.includes("wilderness")).length;

    function escapeHtml(value) {
      return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
    }

    function normalize(value) {
      return String(value || "").toLowerCase();
    }

    function spriteHtml(drop) {
      if (drop.sprite) return `<span class="sprite"><img src="${escapeHtml(drop.sprite)}" alt=""></span>`;
      if (drop.kind === "table") return `<span class="sprite table">T</span>`;
      return `<span class="sprite blank"></span>`;
    }

    function cardHtml(npc) {
      const badgeHtml = npc.badges.map((badge) => {
        const key = normalize(badge);
        const cls = key.includes("void") ? " void" : key.includes("wilderness") ? " teal" : key.includes("dragon") ? " gold" : "";
        return `<span class="pill${cls}">${escapeHtml(badge)}</span>`;
      }).join("");
      const dropsHtml = npc.drops.map((drop) => `
        <li class="drop ${drop.kind === "empty" ? "empty" : ""}">
          ${spriteHtml(drop)}
          <span class="drop-name" title="${escapeHtml(drop.displayName)}">${escapeHtml(drop.displayName)}</span>
          <span class="chance" title="${escapeHtml(drop.exact)}">${escapeHtml(drop.chance)}</span>
        </li>`).join("");
      return `
        <article class="card" id="npc-${npc.id}">
          <div class="npc-head">
            <div class="npc-art"><img src="${escapeHtml(npc.sprite)}" alt=""></div>
            <div class="npc-title">
              <h2>${escapeHtml(npc.name)}</h2>
              <div class="meta">
                <span class="pill gold">NPC ${npc.id}</span>
                <span class="pill">lvl ${npc.combatLevel}</span>
                <span class="pill">empty ${npc.emptyPct}</span>
                ${badgeHtml}
              </div>
            </div>
          </div>
          <ul class="drop-list">${dropsHtml}</ul>
        </article>`;
    }

    function matches(npc, query) {
      if (!npc.tags.includes(activeTab)) return false;
      if (!query) return true;
      return npc.searchText.includes(query);
    }

    function render() {
      const query = normalize(search.value.trim());
      const visible = data.npcs.filter((npc) => matches(npc, query));
      grid.innerHTML = visible.map(cardHtml).join("");
      emptyState.classList.toggle("visible", visible.length === 0);
      const tabName = activeTab === "all" ? "all tracked NPCs" : `${activeTab} NPCs`;
      summary.textContent = `${visible.length} of ${data.npcs.length} ${tabName} shown. Source: ${data.source}.`;
    }

    search.addEventListener("input", render);
    tabs.forEach((tab) => {
      tab.addEventListener("click", () => {
        activeTab = tab.dataset.tab;
        tabs.forEach((candidate) => candidate.setAttribute("aria-selected", String(candidate === tab)));
        render();
      });
    });
    render();
  </script>
</body>
</html>
""".replace("__NPC_DATA__", data_json)


def generate_outputs(staging_root: Path) -> list[tuple[Path, Path]]:
    staged_report_assets = staging_root / "report-assets"
    staged_report_npcs = staged_report_assets / "npc"
    staged_report_items = staged_report_assets / "item"
    staged_report_html = staging_root / "drop-table-balance.html"
    staged_portal_assets = staging_root / "portal-assets"
    staged_portal_npcs = staged_portal_assets / "npc"
    staged_portal_items = staged_portal_assets / "item"
    staged_portal_html = staging_root / "npcs.html"
    for directory in (
        staged_report_npcs,
        staged_report_items,
        staged_portal_npcs,
        staged_portal_items,
    ):
        directory.mkdir(parents=True, exist_ok=True)

    npc_ids = parse_enum(NPC_ID)
    item_ids = parse_enum(ITEM_ID)
    client_items = {item.id: item for item in load_client_items()}
    server_npcs = load_server_npcs()
    item_names = {item_id: display_item_name(client_items[item_id].name) for item_id in client_items}
    tables, table_by_var = parse_drop_tables(item_ids, item_names)
    curated_sections = {npc_name: section for section, npc_name in REPORT_NPCS}
    wilderness_npc_ids = load_npc_loc_ids(loc_predicate=lambda _npc_id, x, y: in_wilderness(x, y))
    void_npc_ids = load_npc_loc_ids(lambda path: path.name == "NpcLocsVoidDungeon.json")

    curated_items: set[int] = set()
    for _, npc_name in REPORT_NPCS:
        table = tables.get(npc_name)
        if table:
            curated_items.update(
                row["item_id"]
                for row in flatten_table(table, table_by_var)
                if row.get("item_id") is not None
            )

    portal_items: set[int] = set()
    for table in tables.values():
        portal_items.update(
            row["item_id"]
            for row in flatten_table(table, table_by_var)
            if row.get("item_id") is not None
        )
    portal_items.update(
        item_ids[item_name]
        for rare_drops in DRAGON_WEAPON_RARE_NPC_DROPS.values()
        for item_name, _denominator in rare_drops
        if item_name in item_ids
    )

    item_sprite_by_id: dict[int, Path] = {}
    for item_id in sorted(curated_items):
        item = client_items.get(item_id)
        if item is None:
            continue
        try:
            ensure_item_sprite(item_id, item.sprite_id, staged_report_items, ITEM_ASSET_DIR)
            item_sprite_by_id[item_id] = ITEM_ASSET_DIR / f"{item_id}.png"
        except KeyError:
            pass

    portal_item_sprite_by_id: dict[int, Path] = {}
    for item_id in sorted(portal_items):
        item = client_items.get(item_id)
        if item is None:
            continue
        try:
            ensure_item_sprite(item_id, item.sprite_id, staged_portal_items, PORTAL_ITEM_ASSET_DIR)
            portal_item_sprite_by_id[item_id] = PORTAL_ITEM_ASSET_DIR / f"{item_id}.png"
        except KeyError:
            pass

    cards_by_section: dict[str, list[str]] = {}
    for section, npc_name in REPORT_NPCS:
        npc_id = npc_ids[npc_name]
        npc = server_npcs.get(npc_id)
        table = tables.get(npc_name)
        if table is None:
            continue
        ensure_npc_sprite(npc_id, staged_report_npcs, NPC_ASSET_DIR)
        npc_sprite = NPC_ASSET_DIR / f"{npc_id}.png"
        drop_rows = flatten_table(table, table_by_var)
        empty_chance = effective_empty_chance(table, table_by_var)
        drop_html = "\n".join(render_flat_drop(row, item_sprite_by_id) for row in drop_rows)
        if empty_chance:
            drop_html += "\n" + render_empty_chance(empty_chance)
        empty_pct = float(empty_chance) * 100
        void_class = " void" if section == "Void Dungeon" else ""
        card = f"""
          <article class="card">
            <div class="npc-head">
              <div class="npc-art"><img src="{html.escape(rel(npc_sprite))}" alt=""></div>
              <div class="npc-title">
                <h3>{html.escape(display_npc_name(npc["name"]) if npc else prettify(npc_name))}</h3>
                <div class="meta">
                  <span class="pill{void_class}">NPC {npc_id}</span>
                  {f'<span class="pill">lvl {int(npc["combatlvl"])}</span>' if npc and "combatlvl" in npc else ''}
                  <span class="pill">line {table.line}</span>
                  <span class="pill good">empty {empty_pct:.1f}%</span>
                </div>
              </div>
            </div>
            <ul class="drops">
              {drop_html}
            </ul>
          </article>"""
        cards_by_section.setdefault(section, []).append(card)

    html_output = "\n".join(line.rstrip() for line in build_html(cards_by_section).splitlines()) + "\n"
    staged_report_html.write_text(html_output)

    portal_npcs: list[dict] = []
    for enum_name, table in sorted(tables.items(), key=lambda row: npc_ids.get(row[0], 999999)):
        npc_id = npc_ids.get(enum_name)
        if npc_id is None:
            continue
        npc = server_npcs.get(npc_id)
        display_name = display_npc_name(npc["name"]) if npc else prettify(enum_name)
        section = curated_sections.get(enum_name, "")
        if not section:
            if is_void_npc(npc_id, enum_name, void_npc_ids):
                section = "Void"
            elif is_wilderness_npc(npc_id, enum_name, wilderness_npc_ids):
                section = "Wilderness"
            else:
                section = "Standard"
        ensure_npc_sprite(npc_id, staged_portal_npcs, PORTAL_NPC_ASSET_DIR)
        bonus_rows = dragon_weapon_bonus_rows(enum_name, item_ids, item_names)
        drops = build_portal_drops(table, table_by_var, portal_item_sprite_by_id, bonus_rows)
        empty_pct = float(effective_empty_chance(table, table_by_var)) * 100
        badges = npc_badges(section, enum_name, npc_id, wilderness_npc_ids, void_npc_ids)
        search_parts = [
            display_name,
            enum_name,
            str(npc_id),
            section,
            *badges,
            *(drop.get("displayName", "") for drop in drops),
            *(str(drop.get("itemId", "")) for drop in drops),
        ]
        portal_npcs.append({
            "id": npc_id,
            "enum": enum_name,
            "name": display_name,
            "combatLevel": int(npc["combatlvl"]) if npc and "combatlvl" in npc else 0,
            "section": section,
            "tags": npc_tags(section, enum_name, npc_id, wilderness_npc_ids, void_npc_ids),
            "badges": badges,
            "emptyPct": f"{empty_pct:.1f}%",
            "sprite": portal_asset_url("npc", npc_id),
            "drops": drops,
            "searchText": " ".join(search_parts).lower(),
        })

    portal_npcs.sort(key=lambda npc: (
        0 if "void" in npc["tags"] else 1 if "wilderness" in npc["tags"] else 2,
        npc["combatLevel"],
        npc["name"].lower(),
        npc["id"],
    ))
    portal_data = {
        "source": "NpcDrops.java + Npc.java bonus rolls",
        "generatedBy": "tools/generate-drop-table-report.py",
        "npcs": portal_npcs,
    }
    portal_html = "\n".join(line.rstrip() for line in render_portal_html(portal_data).splitlines()) + "\n"
    staged_portal_html.write_text(portal_html)
    return [
        (staged_report_assets, ASSET_DIR),
        (staged_report_html, REPORT_PATH),
        (staged_portal_assets, PORTAL_ASSET_DIR),
        (staged_portal_html, PORTAL_NPCS_PATH),
    ]


def remove_path(path: Path) -> None:
    if not path.exists():
        return
    if path.is_dir():
        shutil.rmtree(path)
    else:
        path.unlink()


def install_outputs(outputs: list[tuple[Path, Path]], backup_root: Path) -> None:
    """Install a complete generated set, restoring every prior target on any failure."""
    backup_root.mkdir(parents=True, exist_ok=True)
    records: list[tuple[Path, Path, bool]] = []
    try:
        for index, (staged, target) in enumerate(outputs):
            target.parent.mkdir(parents=True, exist_ok=True)
            backup = backup_root / str(index)
            had_target = target.exists()
            if had_target:
                target.replace(backup)
            records.append((target, backup, had_target))
            staged.replace(target)
    except BaseException:
        for target, backup, had_target in reversed(records):
            remove_path(target)
            if had_target and backup.exists():
                backup.replace(target)
        raise


def main() -> int:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    PORTAL_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".drop-report-", dir=REPO_ROOT) as tmp:
        staging_root = Path(tmp)
        outputs = generate_outputs(staging_root)
        install_outputs(outputs, staging_root / "backups")
    print(REPORT_PATH)
    print(PORTAL_NPCS_PATH)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
