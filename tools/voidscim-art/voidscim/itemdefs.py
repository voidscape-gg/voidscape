"""Parse the CLIENT's hardcoded item table from EntityHandler.java.

Why client, not server JSON?
  - The renderer uses `ItemDef.spriteID` (client field). Sprite archive index =
    `spriteID + 2150` (mudclient.spriteItem).
  - The server JSON `appearanceID` field is decoupled from sprite indexing in
    this codebase — they happen to share names but are independent values.
  - Empirical: spinach roll has server appearanceID=0 but client spriteID=53,
    rendering at archive index 2203 (matches in-game observation).

This module parses `items.add(new ItemDef(...))` calls from EntityHandler.java
into structured records keyed by client spriteID.
"""
from __future__ import annotations
import re
from functools import lru_cache

from .paths import REPO_ROOT

ENTITY_HANDLER = REPO_ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"

# Constructor signature (Client_Base/src/com/openrsc/client/entityhandling/defs/ItemDef.java):
#   ItemDef(name, description, command, basePrice, spriteID, spriteLocation,
#           isStackable, isWearable, wearableID, blueMask,
#           isFemaleOnly, isMembersOnly, isNoteable, id)
# 14 positional args. Fields tracked: 0 name, 3 basePrice, 4 spriteID, 5 spriteLocation,
# 6 isStackable, 7 isWearable, 8 wearableID, 9 blueMask, 10 isFemaleOnly,
# 11 isMembersOnly, 12 isNoteable, 13 id.

_LINE_RE = re.compile(r'items\.add\(\s*new\s+ItemDef\((.*)\)\s*\)\s*;')


def _split_args(s: str) -> list[str]:
    """Split a Java argument list by top-level commas, respecting string literals."""
    out: list[str] = []
    buf: list[str] = []
    in_str = False
    depth = 0
    i = 0
    while i < len(s):
        c = s[i]
        if c == '\\' and in_str and i + 1 < len(s):
            buf.append(c); buf.append(s[i + 1])
            i += 2
            continue
        if c == '"':
            in_str = not in_str
            buf.append(c)
        elif not in_str and c == '(':
            depth += 1
            buf.append(c)
        elif not in_str and c == ')':
            depth -= 1
            buf.append(c)
        elif not in_str and c == ',' and depth == 0:
            out.append(''.join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    if buf:
        out.append(''.join(buf).strip())
    return out


def _strip_str(s: str) -> str:
    s = s.strip()
    if s.startswith('"') and s.endswith('"'):
        return s[1:-1]
    return s


def _parse_int(s: str) -> int | None:
    s = s.strip()
    try:
        return int(s)
    except ValueError:
        return None


def _parse_bool(s: str) -> bool | None:
    s = s.strip()
    if s == "true":
        return True
    if s == "false":
        return False
    return None  # dynamic Java expression (e.g. Config.S_WANT_EQUIPMENT_TAB)


@lru_cache(maxsize=1)
def load_all_items() -> tuple[dict, ...]:
    """Parse EntityHandler.java for all client ItemDef entries.

    Returns tuple of dicts with keys: id, name, sprite_id, is_stackable, sprite_location.
    Items whose spriteID can't be parsed (variable references, etc.) are skipped.
    """
    if not ENTITY_HANDLER.exists():
        raise FileNotFoundError(f"client EntityHandler not found: {ENTITY_HANDLER}")

    text = ENTITY_HANDLER.read_text()
    out: list[dict] = []
    for m in _LINE_RE.finditer(text):
        args = _split_args(m.group(1))
        if len(args) < 14:
            continue
        name = _strip_str(args[0])
        base_price = _parse_int(args[3])
        sprite_id = _parse_int(args[4])
        sprite_location = _strip_str(args[5])
        is_stackable = _parse_bool(args[6])
        is_wearable = _parse_bool(args[7])
        wearable_id = _parse_int(args[8])
        blue_mask = _parse_int(args[9])
        is_female_only = _parse_bool(args[10])
        is_members_only = _parse_bool(args[11])
        is_noteable = _parse_bool(args[12])
        item_id = _parse_int(args[13])
        if sprite_id is None or item_id is None:
            continue
        out.append({
            "id": item_id,
            "name": name,
            "base_price": base_price,
            "sprite_id": sprite_id,
            "sprite_location": sprite_location,
            "is_stackable": bool(is_stackable),
            "is_wearable": bool(is_wearable),
            "wearable_id": wearable_id or 0,
            "blue_mask": blue_mask or 0,
            "is_female_only": bool(is_female_only),
            "is_members_only": bool(is_members_only),
            "is_noteable": bool(is_noteable),
        })
    return tuple(out)


def find_by_id(item_id: int) -> dict | None:
    for it in load_all_items():
        if it["id"] == item_id:
            return it
    return None


def find_by_sprite_id(sprite_id: int) -> list[dict]:
    """All items rendering with this client spriteID (i.e., sharing the icon)."""
    return [it for it in load_all_items() if it["sprite_id"] == sprite_id]
