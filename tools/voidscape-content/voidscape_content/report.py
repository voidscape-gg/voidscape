from __future__ import annotations

import zipfile

from .defs import ids, load_def_array
from .java_parse import load_client_items, load_client_npcs, load_client_version
from .paths import (
    AUTHENTIC_SPRITES,
    CUSTOM_CONTENT_DIR,
    ITEM_DEFS,
    ITEM_DEFS_CUSTOM,
    NPC_DEFS,
    NPC_DEFS_CUSTOM,
    SPRITE_ITEM,
)


def _next_archive_slot(start_sprite_id: int) -> tuple[int, int, list[int]]:
    sprite_id = start_sprite_id
    skipped: list[int] = []
    if not AUTHENTIC_SPRITES.exists():
        return sprite_id, SPRITE_ITEM + sprite_id, skipped
    with zipfile.ZipFile(AUTHENTIC_SPRITES, "r") as archive:
        occupied = set(archive.namelist())
    while str(SPRITE_ITEM + sprite_id) in occupied:
        skipped.append(SPRITE_ITEM + sprite_id)
        sprite_id += 1
    return sprite_id, SPRITE_ITEM + sprite_id, skipped


def print_report() -> int:
    base_items = load_def_array(ITEM_DEFS)
    custom_items = load_def_array(ITEM_DEFS_CUSTOM)
    item_ids = ids(base_items.rows) + ids(custom_items.rows)
    client_items = load_client_items()

    base_npcs = load_def_array(NPC_DEFS)
    custom_npcs = load_def_array(NPC_DEFS_CUSTOM)
    npc_ids = ids(base_npcs.rows) + ids(custom_npcs.rows)
    client_npcs = load_client_npcs()

    max_item_id = max(item_ids)
    max_client_item_id = max(item.id for item in client_items)
    max_sprite_id = max(item.sprite_id for item in client_items if item.sprite_id >= 0)
    next_sprite_id, next_archive_index, skipped = _next_archive_slot(max_sprite_id + 1)

    max_npc_id = max(npc_ids)
    client_version = load_client_version()
    packs = sorted(p for p in CUSTOM_CONTENT_DIR.iterdir() if p.is_dir()) if CUSTOM_CONTENT_DIR.exists() else []

    print("Voidscape content report")
    print("")
    print(f"CLIENT_VERSION: {client_version if client_version is not None else 'unknown'}")
    print("")
    print("Items")
    print(f"  server item range: 0..{max_item_id} ({len(item_ids)} defs)")
    print(f"  client item range: 0..{max_client_item_id} ({len(client_items)} parsed defs)")
    print(f"  next server/client item id: {max(max_item_id, max_client_item_id) + 1}")
    print(f"  next item spriteID: {next_sprite_id}")
    print(f"  next item archive index: {next_archive_index}")
    if skipped:
        print(f"  skipped occupied archive slots: {', '.join(str(v) for v in skipped[:12])}")
    print("")
    print("NPCs")
    print(f"  server npc range: 0..{max_npc_id} ({len(npc_ids)} defs)")
    print(f"  client NPCDef count: {len(client_npcs)}")
    print(f"  next server npc id: {max_npc_id + 1}")
    print("")
    print("Content packs")
    if packs:
        for pack in packs:
            print(f"  - {pack.name}")
    else:
        print("  none yet")
    print("")
    print("Legacy item-art bridge")
    print("  scripts/content.sh voidscim new-icon \"a void relic\"")
    print("  scripts/content.sh voidscim fit path/to/cell.png --lanczos")
    print("  scripts/content.sh voidscim register --png path/to/fit.png --name \"...\" --description \"...\" --commit")
    return 0
