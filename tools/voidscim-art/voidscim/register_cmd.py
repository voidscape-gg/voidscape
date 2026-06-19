"""register / restore — wire a fitted sprite into the game as a custom item.

`register` performs three coordinated edits, all dry-run by default:
  1. Pack the sprite into Authentic_Sprites.orsc at the next archive index.
  2. Insert an items.add(new ItemDef(...)) line in client EntityHandler.java
     immediately after the most recent items.add() in loadItemDefinitions().
  3. Append a JSON entry to server/conf/server/defs/ItemDefsCustom.json.

A per-item undo manifest is written to tools/voidscim-art/registrations/<id>.json
so `restore <id>` can reverse all three edits.

Voidscape allocations (from discovery 2026-04-27):
  - Custom item ids: 1290–1596 used; next = 1597
  - Custom spriteIDs: 606–610 used; next = 611
  - Archive indices for items: spriteID + 2150
  - Sprite lives in Authentic_Sprites.orsc (S_WANT_CUSTOM_SPRITES=false in voidscape)
  - Spawning works via ::item <id> as soon as ItemDefsCustom.json contains the entry
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
from .paths import ARCHIVE_PATH, ITEMDEFS_DIR, REPO_ROOT, SPRITE_ITEM, TOOL_DIR
from .sprite_io import encode

ENTITY_HANDLER = REPO_ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
ITEM_DEFS_CUSTOM = ITEMDEFS_DIR / "ItemDefsCustom.json"
REGISTRATIONS_DIR = TOOL_DIR / "registrations"

# Pattern for an items.add line that we can locate the last one of.
_ITEMS_ADD_RE = re.compile(r'^(\s*)items\.add\(new ItemDef\(.*\)\);\s*$', re.MULTILINE)


@dataclass
class Allocation:
    item_id: int
    sprite_id: int
    archive_index: int


def _allocate_ids() -> Allocation:
    """Compute next item_id, sprite_id, archive_index by scanning the codebase.

    sprite_id starts at max-registered+1 but is bumped past any orphan sprites
    already present in the archive at that slot. (Voidscape has at least one
    orphan from in-progress work — see archive index 2761.) The chosen
    archive_index is always an empty slot in Authentic_Sprites.orsc.
    """
    items = itemdefs.load_all_items()
    max_item_id = max(it["id"] for it in items)
    max_sprite_id = max(it["sprite_id"] for it in items)

    json_max = 0
    if ITEM_DEFS_CUSTOM.exists():
        with ITEM_DEFS_CUSTOM.open() as f:
            data = json.load(f)
        for it in data.get("item", []):
            if isinstance(it, dict) and isinstance(it.get("id"), int):
                json_max = max(json_max, it["id"])

    item_id = max(max_item_id, json_max) + 1

    sprite_id = max_sprite_id + 1
    if ARCHIVE_PATH.exists():
        with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
            occupied = set(zf.namelist())
        skipped: list[int] = []
        while str(sprite_id + SPRITE_ITEM) in occupied:
            skipped.append(sprite_id + SPRITE_ITEM)
            sprite_id += 1
        if skipped:
            print(f"note: skipped occupied archive slot(s) "
                  f"{', '.join(str(s) for s in skipped)} (likely orphan sprites)")
    return Allocation(item_id, sprite_id, sprite_id + SPRITE_ITEM)


def _java_bool(b: bool) -> str:
    return "true" if b else "false"


def _find_server_item(item_id: int) -> tuple[dict | None, Path | None]:
    """Search ItemDefs.json / Patch18 / Custom for the entry with this id."""
    candidates = [
        ITEMDEFS_DIR / "ItemDefs.json",
        ITEMDEFS_DIR / "ItemDefsPatch18.json",
        ITEMDEFS_DIR / "ItemDefsCustom.json",
    ]
    for path in candidates:
        if not path.exists():
            continue
        with path.open() as f:
            data = json.load(f)
        if not isinstance(data, dict) or not data:
            continue
        items_key = next(iter(data))
        for it in data[items_key]:
            if isinstance(it, dict) and it.get("id") == item_id:
                return it, path
    return None, None


def _build_inheritance(like_id: int) -> dict | None:
    """Resolve the wieldability + combat-stat inheritance from a source item.

    Returns None and prints the error path if the source can't be resolved.
    """
    src_client = itemdefs.find_by_id(like_id)
    if not src_client:
        print(f"error: source item id {like_id} not found in client EntityHandler.java")
        return None
    src_server, src_server_path = _find_server_item(like_id)
    if not src_server:
        print(f"error: source item id {like_id} not found in any server ItemDefs JSON")
        return None
    return {
        "src_name": src_client["name"],
        "src_id": like_id,
        "src_server_path": src_server_path.name,
        # client-side ItemDef bools / ints
        "is_stackable": src_client["is_stackable"],
        "is_wearable": src_client["is_wearable"],
        "wearable_id": src_client["wearable_id"],
        "is_female_only": src_client["is_female_only"],
        "is_members_only": src_client["is_members_only"],
        "is_noteable": src_client["is_noteable"],
        # server-side fields
        "appearance_id": src_server.get("appearanceID", 0),
        "wear_slot": src_server.get("wearSlot", -1),
        "required_level": src_server.get("requiredLevel", 0),
        "required_skill_id": src_server.get("requiredSkillID", -1),
        "secondary_required_level": src_server.get("secondaryRequiredLevel"),
        "secondary_required_skill_id": src_server.get("secondaryRequiredSkillID"),
        "armour_bonus": src_server.get("armourBonus", 0),
        "weapon_aim_bonus": src_server.get("weaponAimBonus", 0),
        "weapon_power_bonus": src_server.get("weaponPowerBonus", 0),
        "magic_bonus": src_server.get("magicBonus", 0),
        "prayer_bonus": src_server.get("prayerBonus", 0),
        "is_untradable": bool(src_server.get("isUntradable", 0)),
    }


def _build_entity_handler_block(name: str, description: str, alloc: Allocation,
                                base_price: int, is_stackable: bool,
                                is_members: bool, is_noteable: bool,
                                inherit: dict | None = None) -> str:
    """Comment + items.add(...) lines spliced into EntityHandler.java."""
    if inherit:
        is_stackable = inherit["is_stackable"]
        is_wearable = inherit["is_wearable"]
        wearable_id = inherit["wearable_id"]
        is_female = inherit["is_female_only"]
        is_members = inherit["is_members_only"]
        is_noteable = inherit["is_noteable"]
        kind_note = (f"Inherits wieldability from id {inherit['src_id']} "
                     f"({inherit['src_name']!r}).")
    else:
        is_wearable = False
        wearable_id = 0
        is_female = False
        kind_note = "Inventory-only (non-wearable)."

    comment = (
        f"\t\t// voidscape: {name} (id {alloc.item_id}). spriteID {alloc.sprite_id} "
        f"= AI-generated icon at archive index {alloc.archive_index}. {kind_note}"
    )
    # blueMask=0 so the new icon's own colors render as-is, no tint.
    add_line = (
        f'\t\titems.add(new ItemDef("{name}", "{description}", "", {base_price}, '
        f'{alloc.sprite_id}, "items:{alloc.sprite_id}", '
        f'{_java_bool(is_stackable)}, {_java_bool(is_wearable)}, {wearable_id}, 0, '
        f'{_java_bool(is_female)}, {_java_bool(is_members)}, {_java_bool(is_noteable)}, '
        f'{alloc.item_id}));'
    )
    return f"\n{comment}\n{add_line}"


def _build_json_entry(name: str, description: str, alloc: Allocation,
                      base_price: int, is_stackable: bool,
                      is_members: bool, is_noteable: bool,
                      inherit: dict | None = None) -> dict:
    if inherit:
        entry = {
            "id": alloc.item_id,
            "name": name,
            "description": description,
            "command": "",
            "isFemaleOnly": 1 if inherit["is_female_only"] else 0,
            "isMembersOnly": 1 if inherit["is_members_only"] else 0,
            "isStackable": 1 if inherit["is_stackable"] else 0,
            "isUntradable": 1 if inherit["is_untradable"] else 0,
            "isWearable": 1 if inherit["is_wearable"] else 0,
            "appearanceID": inherit["appearance_id"],
            "wearableID": inherit["wearable_id"],
            "wearSlot": inherit["wear_slot"],
            "requiredLevel": inherit["required_level"],
            "requiredSkillID": inherit["required_skill_id"],
            "armourBonus": inherit["armour_bonus"],
            "weaponAimBonus": inherit["weapon_aim_bonus"],
            "weaponPowerBonus": inherit["weapon_power_bonus"],
            "magicBonus": inherit["magic_bonus"],
            "prayerBonus": inherit["prayer_bonus"],
            "basePrice": base_price,
            "isNoteable": 1 if inherit["is_noteable"] else 0,
        }
        if inherit["secondary_required_level"] is not None:
            entry["secondaryRequiredLevel"] = inherit["secondary_required_level"]
        if inherit["secondary_required_skill_id"] is not None:
            entry["secondaryRequiredSkillID"] = inherit["secondary_required_skill_id"]
        return entry
    return {
        "id": alloc.item_id,
        "name": name,
        "description": description,
        "command": "",
        "isFemaleOnly": 0,
        "isMembersOnly": 1 if is_members else 0,
        "isStackable": 1 if is_stackable else 0,
        "isUntradable": 0,
        "isWearable": 0,
        "appearanceID": 0,
        "wearableID": 0,
        "wearSlot": -1,
        "requiredLevel": 0,
        "requiredSkillID": -1,
        "armourBonus": 0,
        "weaponAimBonus": 0,
        "weaponPowerBonus": 0,
        "magicBonus": 0,
        "prayerBonus": 0,
        "basePrice": base_price,
        "isNoteable": 1 if is_noteable else 0,
    }


def _find_last_items_add_pos(text: str) -> int:
    """Byte offset just after the final items.add(new ItemDef(...)); line."""
    last = None
    for m in _ITEMS_ADD_RE.finditer(text):
        last = m
    if last is None:
        raise RuntimeError("could not locate any items.add(new ItemDef(...)) line in EntityHandler.java")
    return last.end()


def _pack_sprite_into_archive(png_path: Path, sidecar: dict, archive_index: int,
                              archive_path: Path, dst: Path) -> bytes | None:
    """Write a copy of the archive to `dst` with the new sprite at archive_index.
    Returns the prior bytes at that index, or None if it didn't exist."""
    img = Image.open(png_path).convert("RGBA")
    encoded = encode(img, sidecar)

    prior_bytes: bytes | None = None
    with zipfile.ZipFile(archive_path, "r") as zin:
        names = set(zin.namelist())
        if str(archive_index) in names:
            prior_bytes = zin.read(str(archive_index))
        with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
            for info in zin.infolist():
                if info.filename == str(archive_index):
                    continue
                zout.writestr(info, zin.read(info.filename))
            zout.writestr(str(archive_index), encoded)
    return prior_bytes


def cmd_register(png_path: str, name: str, description: str,
                 base_price: int = 100, members: bool = False,
                 stackable: bool = False, noteable: bool = True,
                 like: int | None = None,
                 commit: bool = False) -> int:
    png = Path(png_path).resolve()
    sidecar_path = png.with_suffix(png.suffix + ".json")
    if not png.exists():
        print(f"error: png not found: {png}")
        return 1
    if not sidecar_path.exists():
        print(f"error: sidecar not found: {sidecar_path}")
        print(f"       expected to live next to {png.name} as {sidecar_path.name}")
        return 1
    sidecar = json.loads(sidecar_path.read_text())

    alloc = _allocate_ids()
    print(f"allocation:")
    print(f"  item_id       = {alloc.item_id}")
    print(f"  sprite_id     = {alloc.sprite_id}")
    print(f"  archive_index = {alloc.archive_index}")

    inherit = None
    if like is not None:
        inherit = _build_inheritance(like)
        if inherit is None:
            return 1
        print(f"\ninheriting from id={like} ({inherit['src_name']!r}, "
              f"server file: {inherit['src_server_path']}):")
        print(f"  isWearable={inherit['is_wearable']}, "
              f"wearableID={inherit['wearable_id']}, "
              f"wearSlot={inherit['wear_slot']}, "
              f"appearanceID={inherit['appearance_id']}")
        if inherit["required_level"]:
            print(f"  required: lvl {inherit['required_level']} skillID {inherit['required_skill_id']}")
        if inherit["secondary_required_level"]:
            print(f"  secondary: lvl {inherit['secondary_required_level']} skillID {inherit['secondary_required_skill_id']}")
        print(f"  bonuses: aim={inherit['weapon_aim_bonus']} "
              f"power={inherit['weapon_power_bonus']} "
              f"armour={inherit['armour_bonus']} "
              f"magic={inherit['magic_bonus']} "
              f"prayer={inherit['prayer_bonus']}")

    inserted_block = _build_entity_handler_block(
        name, description, alloc, base_price, stackable, members, noteable,
        inherit=inherit,
    )
    json_entry = _build_json_entry(
        name, description, alloc, base_price, stackable, members, noteable,
        inherit=inherit,
    )

    print(f"\nplanned edits:")
    print(f"  1. Authentic_Sprites.orsc: write entry {alloc.archive_index} "
          f"({sidecar['width']}×{sidecar['height']}, "
          f"xShift={sidecar['xShift']}, yShift={sidecar['yShift']})")
    print(f"  2. EntityHandler.java: insert after the last items.add line:")
    for line in inserted_block.strip().splitlines():
        print(f"       {line}")
    kind = (f"wearable (slot={inherit['wear_slot']}, appearanceID={inherit['appearance_id']})"
            if inherit else "non-wearable")
    print(f"  3. ItemDefsCustom.json: append entry id={alloc.item_id} "
          f"({kind}, basePrice={base_price})")

    if not commit:
        print(f"\ndry-run; pass --commit to apply")
        return 0

    bak = ARCHIVE_PATH.with_suffix(ARCHIVE_PATH.suffix + ".bak")
    if not bak.exists():
        print(f"\nrefusing to commit: backup not found at {bak}")
        print(f"create one first: cp {ARCHIVE_PATH} {bak}")
        return 1

    # 1. Pack sprite (write to temp, then atomic-replace)
    fd, tmp_archive = tempfile.mkstemp(suffix=".orsc", dir=str(ARCHIVE_PATH.parent))
    os.close(fd)
    tmp_archive = Path(tmp_archive)
    try:
        prior_bytes = _pack_sprite_into_archive(png, sidecar, alloc.archive_index,
                                                ARCHIVE_PATH, tmp_archive)
        os.replace(tmp_archive, ARCHIVE_PATH)
    except Exception:
        tmp_archive.unlink(missing_ok=True)
        raise
    print(f"  ✓ archive: wrote entry {alloc.archive_index} into {ARCHIVE_PATH.name}")

    # 2. Insert into EntityHandler.java
    eh_text = ENTITY_HANDLER.read_text()
    insert_pos = _find_last_items_add_pos(eh_text)
    new_eh_text = eh_text[:insert_pos] + inserted_block + eh_text[insert_pos:]
    ENTITY_HANDLER.write_text(new_eh_text)
    print(f"  ✓ EntityHandler.java: inserted 2 lines")

    # 3. Append to ItemDefsCustom.json. The server's loadItems() reads the
    # FIRST top-level key whatever its name (server/.../EntityHandler.java:372:
    # `object.getJSONArray(JSONObject.getNames(object)[0])`), so we must append
    # into the existing key rather than introducing a new one. ItemDefsCustom
    # uses "items" (plural); vanilla ItemDefs.json uses "item" (singular).
    with ITEM_DEFS_CUSTOM.open() as f:
        defs = json.load(f)
    if not defs:
        raise RuntimeError(f"{ITEM_DEFS_CUSTOM} is empty — cannot determine top-level array key")
    items_key = next(iter(defs))
    array = defs[items_key]
    if not isinstance(array, list):
        raise RuntimeError(f"top-level key {items_key!r} is not an array")
    array.append(json_entry)
    with ITEM_DEFS_CUSTOM.open("w") as f:
        json.dump(defs, f, indent=4)
        f.write("\n")
    print(f"  ✓ ItemDefsCustom.json: appended id={alloc.item_id} into {items_key!r} array")

    # 4. Save undo manifest
    REGISTRATIONS_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {
        "item_id": alloc.item_id,
        "sprite_id": alloc.sprite_id,
        "archive_index": alloc.archive_index,
        "name": name,
        "registered_at": _dt.datetime.now().isoformat(timespec="seconds"),
        "archive_entry_was_present_before": prior_bytes is not None,
        "archive_entry_prior_b64": (
            __import__("base64").b64encode(prior_bytes).decode() if prior_bytes else None
        ),
        "entity_handler_inserted_text": inserted_block,
    }
    (REGISTRATIONS_DIR / f"{alloc.item_id}.json").write_text(json.dumps(manifest, indent=2))
    print(f"  ✓ undo manifest: {REGISTRATIONS_DIR / f'{alloc.item_id}.json'}")

    print(f"\nregistered. To test in-game:")
    print(f"  scripts/build.sh && scripts/run-client.sh")
    print(f"  ::item {alloc.item_id}")
    return 0


def cmd_restore(item_id: int, commit: bool = False) -> int:
    manifest_path = REGISTRATIONS_DIR / f"{item_id}.json"
    if not manifest_path.exists():
        print(f"error: no registration manifest for item_id={item_id} at {manifest_path}")
        return 1
    manifest = json.loads(manifest_path.read_text())

    archive_index = manifest["archive_index"]
    inserted_text = manifest["entity_handler_inserted_text"]
    had_prior = manifest["archive_entry_was_present_before"]

    print(f"would restore: item_id={item_id} ({manifest.get('name', '?')})")
    print(f"  1. Authentic_Sprites.orsc: " +
          ("restore prior bytes at " if had_prior else "delete entry ") +
          str(archive_index))
    print(f"  2. EntityHandler.java: remove the 2 lines added")
    print(f"  3. ItemDefsCustom.json: remove entry id={item_id}")
    print(f"  4. delete manifest")

    if not commit:
        print(f"\ndry-run; pass --commit to apply")
        return 0

    # 1. Archive
    fd, tmp_archive = tempfile.mkstemp(suffix=".orsc", dir=str(ARCHIVE_PATH.parent))
    os.close(fd)
    tmp_archive = Path(tmp_archive)
    try:
        with zipfile.ZipFile(ARCHIVE_PATH, "r") as zin:
            with zipfile.ZipFile(tmp_archive, "w", zipfile.ZIP_DEFLATED) as zout:
                for info in zin.infolist():
                    if info.filename == str(archive_index):
                        continue
                    zout.writestr(info, zin.read(info.filename))
                if had_prior:
                    prior_bytes = __import__("base64").b64decode(manifest["archive_entry_prior_b64"])
                    zout.writestr(str(archive_index), prior_bytes)
        os.replace(tmp_archive, ARCHIVE_PATH)
    except Exception:
        tmp_archive.unlink(missing_ok=True)
        raise
    print(f"  ✓ archive: " +
          ("restored prior bytes at " if had_prior else "removed entry ") + str(archive_index))

    # 2. EntityHandler.java
    eh_text = ENTITY_HANDLER.read_text()
    if inserted_text not in eh_text:
        print(f"  ! EntityHandler.java: inserted text not found verbatim — manual cleanup needed")
    else:
        ENTITY_HANDLER.write_text(eh_text.replace(inserted_text, "", 1))
        print(f"  ✓ EntityHandler.java: removed inserted lines")

    # 3. ItemDefsCustom.json — find by id across whatever the top-level array key is
    with ITEM_DEFS_CUSTOM.open() as f:
        defs = json.load(f)
    items_key = next(iter(defs))
    array = defs[items_key]
    before = len(array)
    defs[items_key] = [it for it in array if it.get("id") != item_id]
    after = len(defs[items_key])
    with ITEM_DEFS_CUSTOM.open("w") as f:
        json.dump(defs, f, indent=4)
        f.write("\n")
    print(f"  ✓ ItemDefsCustom.json: removed {before - after} entr{'y' if before-after==1 else 'ies'} from {items_key!r}")

    manifest_path.unlink()
    print(f"  ✓ manifest deleted")
    return 0
