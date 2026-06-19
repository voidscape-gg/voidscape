"""pack-wielded — pack recolored wielded frames + wire AnimationDef into the game.

Three coordinated edits, all gated by --commit:
  1. Pack frame_NN.png + sidecars into Authentic_Sprites.orsc at the next free
     AnimationDef block (= next runtime animationNumber, computed by simulating
     mudclient.loadEntitiesAuthentic's iteration loop).
  2. Append a new AnimationDef line to EntityHandler.java::loadAnimationDefinitions()
     just before the closing brace, with hasA/hasF copied from the source.
  3. Update the target item's `appearanceID` in ItemDefsCustom.json to the new
     AnimationDef's runtime list-position (= count of non-gated entries before it).

Inputs: a frames directory produced by `recolor-wielded`, a target item id
(must already be registered + wieldable, typically via `register --like <src>`),
and the source item id we're imitating (used to confirm hasA/hasF + frame count).

Per-call manifest stored at registrations/wielded_<item_id>.json so a future
`restore-wielded` slice can reverse it.
"""
from __future__ import annotations
import datetime as _dt
import json
import os
import re
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

from PIL import Image

from . import itemdefs
from .paths import ARCHIVE_PATH, ITEMDEFS_DIR, REPO_ROOT, TOOL_DIR
from .register_cmd import (
    ENTITY_HANDLER, ITEM_DEFS_CUSTOM, REGISTRATIONS_DIR, _find_server_item,
)
from .sprite_io import encode

ANIM_FN_OPEN_RE = re.compile(
    r'(?:private|public|protected)\s+(?:static\s+)?void\s+loadAnimationDefinitions\s*\(\s*\)\s*\{'
)
ANIM_ADD_RE = re.compile(
    r'animations\.add\(\s*new\s+AnimationDef\((.*?)\)\s*\)\s*;', re.DOTALL,
)


def _slug(s: str) -> str:
    out = re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")
    return out or "anim"


def _split_args(s: str) -> list[str]:
    """Top-level comma split that respects strings + nested parens."""
    out: list[str] = []
    buf: list[str] = []
    depth = 0
    in_str = False
    i = 0
    while i < len(s):
        c = s[i]
        if c == '\\' and in_str and i + 1 < len(s):
            buf.append(c); buf.append(s[i + 1]); i += 2; continue
        if c == '"':
            in_str = not in_str; buf.append(c)
        elif not in_str and c == '(':
            depth += 1; buf.append(c)
        elif not in_str and c == ')':
            depth -= 1; buf.append(c)
        elif not in_str and c == ',' and depth == 0:
            out.append(''.join(buf).strip()); buf = []
        else:
            buf.append(c)
        i += 1
    if buf:
        out.append(''.join(buf).strip())
    return out


@dataclass
class AnimEntry:
    name: str
    args_raw: str
    has_a: bool
    has_f: bool
    arg_count: int
    gated: bool
    number: int = -1  # filled by simulate()


def _parse_anim_args(args_raw: str) -> tuple[str, bool, bool, int]:
    args = _split_args(args_raw)
    name = args[0].strip().strip('"')
    if len(args) == 8:
        has_a = args[5].strip() == "true"
        has_f = args[6].strip() == "true"
    elif len(args) == 7:
        has_a = args[4].strip() == "true"
        has_f = args[5].strip() == "true"
    else:
        raise ValueError(f"unexpected AnimationDef arg count {len(args)}: {args_raw!r}")
    return name, has_a, has_f, len(args)


def parse_animations(text: str) -> tuple[list[AnimEntry], int]:
    """Walk loadAnimationDefinitions(), return (animations, fn_close_brace_pos).

    `gated` is True for entries inside any S_WANT_CUSTOM_SPRITES if-block.
    `fn_close_brace_pos` is the byte offset of the function's closing `}` so
    callers can splice new lines just before it.
    """
    m = ANIM_FN_OPEN_RE.search(text)
    if not m:
        raise RuntimeError("loadAnimationDefinitions() not found")
    body_start = m.end()
    n = len(text)

    pos = body_start
    brace_stack: list[bool] = []  # one entry per open brace inside the function: gated?
    animations: list[AnimEntry] = []
    fn_close = -1

    while pos < n:
        # line comment
        if text[pos:pos + 2] == "//":
            end = text.find("\n", pos)
            pos = end if end >= 0 else n
            continue
        # block comment
        if text[pos:pos + 2] == "/*":
            end = text.find("*/", pos)
            pos = (end + 2) if end >= 0 else n
            continue
        # string literal
        if text[pos] == '"':
            pos += 1
            while pos < n and text[pos] != '"':
                if text[pos] == "\\":
                    pos += 2
                else:
                    pos += 1
            pos += 1
            continue
        c = text[pos]
        if c == "{":
            stmt_start = pos
            while stmt_start > body_start:
                ch = text[stmt_start - 1]
                if ch in ";{}":
                    break
                stmt_start -= 1
            preceding = text[stmt_start:pos]
            this_gated = "S_WANT_CUSTOM_SPRITES" in preceding or any(brace_stack)
            brace_stack.append(this_gated)
            pos += 1
            continue
        if c == "}":
            if not brace_stack:
                fn_close = pos
                break
            brace_stack.pop()
            pos += 1
            continue
        match = ANIM_ADD_RE.match(text, pos)
        if match:
            name, has_a, has_f, argc = _parse_anim_args(match.group(1))
            animations.append(AnimEntry(
                name=name,
                args_raw=match.group(1),
                has_a=has_a,
                has_f=has_f,
                arg_count=argc,
                gated=any(brace_stack),
            ))
            pos = match.end()
            continue
        pos += 1

    if fn_close < 0:
        raise RuntimeError("could not find loadAnimationDefinitions() closing brace")
    return animations, fn_close


def simulate(animations: list[AnimEntry]) -> int:
    """Assign .number to each visible (non-gated) entry per the mudclient loop.

    Returns the next free animationNumber (the value `animationNumber` would
    hold AFTER all visible entries have been processed). This is where a NEW
    unique-named AnimationDef would land.
    """
    seen: dict[str, int] = {}
    n = 0
    for a in animations:
        if a.gated:
            continue
        name_lc = a.name.lower()
        if name_lc in seen:
            a.number = seen[name_lc]
            continue
        a.number = n
        seen[name_lc] = n
        n += 27
        if n == 1998:
            n = 3300
    return n


def _frame_count_for(has_a: bool, has_f: bool) -> int:
    return 15 + (3 if has_a else 0) + (9 if has_f else 0)


def _build_anim_line(name: str, source: AnimEntry) -> str:
    """Generate the new animations.add(...) line.

    Copies source args except: (1) name → our slug, (2) charColour → 0 (no
    runtime tint, since our pre-recolored frames already have final colors —
    matches voidneck/voidmace/voidling pattern in the existing tree).
    """
    args = _split_args(source.args_raw)
    args[0] = f'"{name}"'
    args[2] = "0"  # charColour
    return f'\t\tanimations.add(new AnimationDef({", ".join(args)}));'


def cmd_pack_wielded(target_item: int, source_item: int, frames_dir: str,
                     commit: bool = False) -> int:
    target_client = itemdefs.find_by_id(target_item)
    if not target_client:
        print(f"error: target item id {target_item} not found in client EntityHandler.java")
        return 1
    target_server, target_server_path = _find_server_item(target_item)
    if not target_server:
        print(f"error: target item id {target_item} not found in server JSON")
        return 1
    if target_server_path.name != "ItemDefsCustom.json":
        print(f"error: target lives in {target_server_path.name}; only ItemDefsCustom.json is editable here")
        return 1

    src_client = itemdefs.find_by_id(source_item)
    src_server, _ = _find_server_item(source_item)
    if not src_client or not src_server:
        print(f"error: source item id {source_item} not resolvable")
        return 1

    src_appearance_id = src_server.get("appearanceID")
    if src_appearance_id is None:
        print(f"error: source item has no appearanceID — can't determine wielded shape")
        return 1

    eh_text = ENTITY_HANDLER.read_text()
    animations, fn_close = parse_animations(eh_text)
    next_number = simulate(animations)
    visible = [a for a in animations if not a.gated]

    if src_appearance_id >= len(visible):
        print(f"error: source appearanceID {src_appearance_id} out of bounds (visible count={len(visible)})")
        return 1
    src_anim = visible[src_appearance_id]
    frame_count = _frame_count_for(src_anim.has_a, src_anim.has_f)

    new_name = _slug(target_client["name"])
    if any(a.name.lower() == new_name for a in visible):
        new_name = f"{new_name}_{target_item}"
    if any(a.name.lower() == new_name for a in visible):
        print(f"error: name collision unresolvable for {target_client['name']!r}")
        return 1

    new_appearance_id = len(visible)  # new entry will be appended at this list position
    new_archive_base = next_number
    new_archive_range = (new_archive_base, new_archive_base + frame_count - 1)
    new_anim_line = _build_anim_line(new_name, src_anim)

    print(f"target:    id={target_item} ({target_client['name']!r})")
    print(f"source:    id={source_item} ({src_anim.name!r}, appearanceID={src_appearance_id}, "
          f"hasA={src_anim.has_a}, hasF={src_anim.has_f}, frame_count={frame_count})")
    print(f"  source AnimationDef.number = {src_anim.number} (archive [{src_anim.number}..{src_anim.number+frame_count-1}])")
    print()
    print(f"allocation for new wielded sprite:")
    print(f"  new AnimationDef name      = {new_name!r}")
    print(f"  new AnimationDef list_pos  = {new_appearance_id} (= appearanceID for target)")
    print(f"  new AnimationDef.number    = {new_archive_base}")
    print(f"  archive range              = [{new_archive_range[0]}..{new_archive_range[1]}] ({frame_count} frames)")

    frames_path = Path(frames_dir).resolve()
    if not frames_path.exists() or not frames_path.is_dir():
        print(f"error: frames dir not found: {frames_path}")
        return 1

    expected = []
    for i in range(frame_count):
        png = frames_path / f"frame_{i:02d}.png"
        sidecar = frames_path / f"frame_{i:02d}.png.json"
        if not png.exists():
            print(f"error: missing frame {png.name} in {frames_path}")
            return 1
        if not sidecar.exists():
            print(f"error: missing sidecar {sidecar.name} in {frames_path}")
            return 1
        expected.append((png, sidecar))

    print(f"\nfound {frame_count} frames in {frames_path.name}/")

    print(f"\nplanned edits:")
    print(f"  1. Authentic_Sprites.orsc: pack {frame_count} entries at "
          f"[{new_archive_range[0]}..{new_archive_range[1]}]")
    print(f"  2. EntityHandler.java: append before loadAnimationDefinitions() closing brace:")
    for line in new_anim_line.strip().splitlines():
        print(f"       {line}")
    print(f"  3. ItemDefsCustom.json: change id={target_item} appearanceID "
          f"{target_server.get('appearanceID')} → {new_appearance_id}")

    if not commit:
        print(f"\ndry-run; pass --commit to apply")
        return 0

    bak = ARCHIVE_PATH.with_suffix(ARCHIVE_PATH.suffix + ".bak")
    if not bak.exists():
        print(f"\nrefusing to commit: backup not found at {bak}")
        return 1

    encoded: dict[str, bytes] = {}
    prior_bytes: dict[int, bytes | None] = {}
    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zin:
        existing = set(zin.namelist())
        for offset, (png, sidecar) in enumerate(expected):
            idx = new_archive_base + offset
            sc = json.loads(sidecar.read_text())
            img = Image.open(png).convert("RGBA")
            encoded[str(idx)] = encode(img, sc)
            prior_bytes[idx] = zin.read(str(idx)) if str(idx) in existing else None

    fd, tmp_archive = tempfile.mkstemp(suffix=".orsc", dir=str(ARCHIVE_PATH.parent))
    os.close(fd)
    tmp_archive = Path(tmp_archive)
    try:
        with zipfile.ZipFile(ARCHIVE_PATH, "r") as zin:
            with zipfile.ZipFile(tmp_archive, "w", zipfile.ZIP_DEFLATED) as zout:
                for info in zin.infolist():
                    if info.filename in encoded:
                        continue
                    zout.writestr(info, zin.read(info.filename))
                for name, data in encoded.items():
                    zout.writestr(name, data)
        os.replace(tmp_archive, ARCHIVE_PATH)
    except Exception:
        tmp_archive.unlink(missing_ok=True)
        raise
    print(f"  ✓ archive: packed {frame_count} frames at [{new_archive_range[0]}..{new_archive_range[1]}]")

    new_eh_text = eh_text[:fn_close] + new_anim_line + "\n\t" + eh_text[fn_close:]
    ENTITY_HANDLER.write_text(new_eh_text)
    print(f"  ✓ EntityHandler.java: appended AnimationDef line before closing brace")

    with ITEM_DEFS_CUSTOM.open() as f:
        defs = json.load(f)
    items_key = next(iter(defs))
    found = False
    for it in defs[items_key]:
        if it.get("id") == target_item:
            it["appearanceID"] = new_appearance_id
            found = True
            break
    if not found:
        print(f"  ! WARN: id={target_item} not found in {ITEM_DEFS_CUSTOM.name}")
    with ITEM_DEFS_CUSTOM.open("w") as f:
        json.dump(defs, f, indent=4)
        f.write("\n")
    print(f"  ✓ ItemDefsCustom.json: appearanceID set to {new_appearance_id}")

    REGISTRATIONS_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "kind": "wielded",
        "target_item_id": target_item,
        "source_item_id": source_item,
        "anim_name": new_name,
        "appearance_id": new_appearance_id,
        "prior_appearance_id": target_server.get("appearanceID"),
        "archive_base": new_archive_base,
        "frame_count": frame_count,
        "registered_at": _dt.datetime.now().isoformat(timespec="seconds"),
        "anim_line_inserted": new_anim_line + "\n\t",
        "archive_prior_b64": {
            str(k): __import__("base64").b64encode(v).decode() if v else None
            for k, v in prior_bytes.items()
        },
    }
    (REGISTRATIONS_DIR / f"wielded_{target_item}.json").write_text(json.dumps(manifest, indent=2))
    print(f"  ✓ undo manifest: registrations/wielded_{target_item}.json")

    print(f"\npacked. Build + restart server + client to see it in-game.")
    return 0
