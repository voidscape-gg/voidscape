"""init <item_id> — scaffold a registry entry and extract the reference sprite."""
from __future__ import annotations
import json
import re

from . import itemdefs
from .archive_io import read_entry
from .paths import ARCHIVE_PATH, ITEMS_DIR, SPRITE_ITEM
from .registry import Entry, add_or_update, load_registry, save_registry
from .sprite_io import decode


def slugify(name: str) -> str:
    s = name.lower().strip()
    s = re.sub(r"[^a-z0-9]+", "_", s)
    return s.strip("_")


def cmd_init(item_id: int, key: str | None = None) -> int:
    if not ARCHIVE_PATH.exists():
        print(f"error: archive not found: {ARCHIVE_PATH}")
        return 1

    item = itemdefs.find_by_id(item_id)
    if item is None:
        print(f"error: no client item with id={item_id} (parsed from EntityHandler.java)")
        return 1

    sprite_id = item["sprite_id"]
    archive_index = sprite_id + SPRITE_ITEM
    linked = itemdefs.find_by_sprite_id(sprite_id)
    item_key = key or slugify(item["name"])

    if not item_key:
        print(f"error: could not derive a slug from name {item['name']!r}; pass --key")
        return 1

    # Extract reference sprite from archive
    try:
        data = read_entry(archive_index)
    except KeyError:
        print(f"error: archive entry {archive_index} not found")
        return 1
    img, sidecar = decode(data)

    item_dir = ITEMS_DIR / item_key
    item_dir.mkdir(parents=True, exist_ok=True)
    ref_path = item_dir / "ref.png"
    img.save(ref_path)
    (item_dir / "ref.sidecar.json").write_text(json.dumps(sidecar, indent=2))

    reg = load_registry()
    if item_key in reg:
        print(f"warning: registry already has entry {item_key!r}; overwriting")

    entry = Entry(
        item_key=item_key,
        appearance_id=sprite_id,  # naming retained for backwards-compat in the registry
        archive_index=archive_index,
        name=item["name"],
        is_stackable=bool(item["is_stackable"]),
        linked_item_ids=[it["id"] for it in linked],
        palette_anchors=[],
        palette_tol=28,
        silhouette_iou_min=0.95,
        target_size=[sidecar["width"], sidecar["height"]],
        sidecar=sidecar,
    )
    add_or_update(reg, entry)
    save_registry(reg)

    # Report
    print(f"item:             id={item_id}  {item['name']!r}")
    print(f"client spriteID:  {sprite_id}")
    print(f"archive entry:    {archive_index}")
    print(f"slug (item_key):  {item_key}")
    print(f"sprite size:      {sidecar['width']}×{sidecar['height']}")
    print(f"is_stackable:     {entry.is_stackable}")
    if len(linked) > 1:
        print(f"shared icon: {len(linked)} items render this sprite. Repacking will change ALL of them:")
        for it in linked:
            marker = "  <-- target" if it["id"] == item_id else ""
            print(f"  id={it['id']:<5d} {it['name']!r}{marker}")
    print(f"saved ref:        {ref_path}")
    print(f"saved sidecar:    {item_dir / 'ref.sidecar.json'}")
    print()
    print("next steps:")
    print(f"  1. edit registry.yaml: set palette_anchors for {item_key!r} (e.g. [\"#4DD2C2\"])")
    print(f"  2. drop a style/style_lock.png into {ITEMS_DIR.parent / 'style'}")
    print(f"  3. python -m voidscim generate {item_key}")
    return 0
