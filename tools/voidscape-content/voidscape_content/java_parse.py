from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

from .paths import CLIENT_CONFIG, CLIENT_ENTITY_HANDLER


@dataclass(frozen=True)
class ClientItem:
    id: int
    name: str
    sprite_id: int
    line: int


@dataclass(frozen=True)
class ClientNpc:
    id: int
    name: str
    line: int


_ITEM_RE = re.compile(r"items\.add\(\s*new\s+ItemDef\((.*?)\)\s*\)\s*;", re.S)
_NPC_RE = re.compile(r"npcs\.add\(\s*new\s+NPCDef\((.*?)\)\s*\)\s*;", re.S)
_CLIENT_VERSION_RE = re.compile(r"CLIENT_VERSION\s*=\s*(\d+)")


def split_java_args(arg_text: str) -> list[str]:
    out: list[str] = []
    buf: list[str] = []
    in_string = False
    depth = 0
    i = 0
    while i < len(arg_text):
        c = arg_text[i]
        if c == "\\" and in_string and i + 1 < len(arg_text):
            buf.append(c)
            buf.append(arg_text[i + 1])
            i += 2
            continue
        if c == '"':
            in_string = not in_string
            buf.append(c)
        elif not in_string and c == "(":
            depth += 1
            buf.append(c)
        elif not in_string and c == ")":
            depth -= 1
            buf.append(c)
        elif not in_string and c == "," and depth == 0:
            out.append("".join(buf).strip())
            buf = []
        else:
            buf.append(c)
        i += 1
    if buf:
        out.append("".join(buf).strip())
    return out


def parse_java_string(value: str) -> str:
    value = value.strip()
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1].replace('\\"', '"').replace("\\\\", "\\")
    return value


def parse_java_int(value: str) -> int | None:
    value = value.strip().replace("_", "")
    try:
        return int(value, 0)
    except ValueError:
        return None


def _line_for(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def load_client_items(path: Path = CLIENT_ENTITY_HANDLER) -> list[ClientItem]:
    text = path.read_text()
    items: list[ClientItem] = []
    for match in _ITEM_RE.finditer(text):
        args = split_java_args(match.group(1))
        if len(args) < 14:
            continue
        item_id = parse_java_int(args[13])
        sprite_id = parse_java_int(args[4])
        if item_id is None or sprite_id is None:
            continue
        items.append(
            ClientItem(
                id=item_id,
                name=parse_java_string(args[0]),
                sprite_id=sprite_id,
                line=_line_for(text, match.start()),
            )
        )
    return items


def load_client_npcs(path: Path = CLIENT_ENTITY_HANDLER) -> list[ClientNpc]:
    text = path.read_text()
    npcs: list[ClientNpc] = []
    for index, match in enumerate(_NPC_RE.finditer(text)):
        args = split_java_args(match.group(1))
        if not args:
            continue
        npcs.append(
            ClientNpc(
                id=index,
                name=parse_java_string(args[0]),
                line=_line_for(text, match.start()),
            )
        )
    return npcs


def load_client_version(path: Path = CLIENT_CONFIG) -> int | None:
    text = path.read_text()
    match = _CLIENT_VERSION_RE.search(text)
    if not match:
        return None
    return int(match.group(1))
