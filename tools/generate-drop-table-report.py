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
CLIENT_BASE = REPO_ROOT / "Client_Base"
CLIENT_JAR = CLIENT_BASE / "Open_RSC_Client.jar"
REPORT_DIR = REPO_ROOT / "docs" / "reports"
ASSET_DIR = REPORT_DIR / "drop-table-assets"
NPC_ASSET_DIR = ASSET_DIR / "npc"
ITEM_ASSET_DIR = ASSET_DIR / "item"
REPORT_PATH = REPORT_DIR / "drop-table-balance.html"

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


@dataclass
class Drop:
    kind: str
    name: str
    amount: str
    weight: int
    item_id: int | None = None
    noted: bool = False


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
        new_table = re.search(r"(currentNpcDrops|voidDrop)\s*=\s*new\s+DropTable\(\"([^\"]+)\"", line)
        if new_table:
            table_by_var[new_table.group(1)] = ParsedTable(new_table.group(2), line_no)
            continue

        item_drop = re.search(r"(currentNpcDrops|voidDrop)\.addItemDrop\((.*)\)\s*;", line)
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

        table_drop = re.search(r"(currentNpcDrops|voidDrop)\.addTableDrop\(([^,]+),\s*(\d+)\)", line)
        if table_drop and table_drop.group(1) in table_by_var:
            table_by_var[table_drop.group(1)].drops.append(
                Drop(kind="table", name=prettify(table_drop.group(2)), amount="", weight=int(table_drop.group(3)))
            )
            continue

        put = re.search(r"this\.npcDrops\.put\(NpcId\.([A-Z0-9_]+)\.id\(\),\s*(currentNpcDrops|voidDrop)\)", line)
        if put and put.group(2) in table_by_var:
            npc_to_table[put.group(1)] = table_by_var[put.group(2)]
            continue

        empty = re.search(r"(currentNpcDrops|voidDrop)\.addEmptyDrop\((\d+) - \1\.getTotalWeight\(\)\)", line)
        if empty and empty.group(1) in table_by_var:
            table_by_var[empty.group(1)].target_weight = int(empty.group(2))

    return npc_to_table


def prettify(name: str) -> str:
    name = re.sub(r"([a-z])([A-Z])", r"\1 \2", name)
    name = name.replace("ItemId.", "").replace(".id()", "")
    return name.replace("_", " ").title()


def display_npc_name(name: str) -> str:
    return " ".join(part[:1].upper() + part[1:] for part in name.split())


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


def ensure_npc_sprite(npc_id: int) -> Path:
    dest = NPC_ASSET_DIR / f"{npc_id}.png"
    if dest.exists():
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


def ensure_item_sprite(item_id: int, sprite_id: int) -> Path:
    dest = ITEM_ASSET_DIR / f"{item_id}.png"
    if dest.exists():
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


def main() -> int:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    NPC_ASSET_DIR.mkdir(parents=True, exist_ok=True)
    ITEM_ASSET_DIR.mkdir(parents=True, exist_ok=True)

    npc_ids = parse_enum(NPC_ID)
    item_ids = parse_enum(ITEM_ID)
    client_items = {item.id: item for item in load_client_items()}
    server_npcs = load_server_npcs()
    item_names = {item_id: client_items[item_id].name for item_id in client_items}
    tables = parse_drop_tables(item_ids, item_names)

    needed_items: set[int] = set()
    for _, npc_name in REPORT_NPCS:
        table = tables.get(npc_name)
        if table:
            needed_items.update(drop.item_id for drop in table.drops if drop.item_id is not None)

    item_sprite_by_id: dict[int, Path] = {}
    for item_id in sorted(needed_items):
        item = client_items.get(item_id)
        if item is None:
            continue
        try:
            item_sprite_by_id[item_id] = ensure_item_sprite(item_id, item.sprite_id)
        except KeyError:
            pass

    cards_by_section: dict[str, list[str]] = {}
    for section, npc_name in REPORT_NPCS:
        npc_id = npc_ids[npc_name]
        npc = server_npcs.get(npc_id)
        table = tables.get(npc_name)
        if table is None:
            continue
        npc_sprite = ensure_npc_sprite(npc_id)
        drops = list(table.drops)
        if table.empty_weight:
            drops.append(Drop(kind="empty", name="Nothing", amount="", weight=table.empty_weight))
        denominator = table.denominator
        drop_html = "\n".join(render_drop(drop, item_sprite_by_id, denominator) for drop in drops)
        empty_pct = table.empty_weight / denominator * 100
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
    REPORT_PATH.write_text(html_output)
    print(REPORT_PATH)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
