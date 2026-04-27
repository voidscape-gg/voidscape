"""inspect <archive_index> — extract a sprite, name its linked items, save 8× zoom."""
from __future__ import annotations
import zipfile

from PIL import Image

from . import itemdefs
from .paths import ARCHIVE_PATH, INSPECT_DIR, ITEM_SLOT_RANGE, SPRITE_ITEM
from .sprite_io import decode


def cmd_inspect(archive_index: int) -> int:
    if not ARCHIVE_PATH.exists():
        print(f"error: archive not found: {ARCHIVE_PATH}")
        return 1

    with zipfile.ZipFile(ARCHIVE_PATH, "r") as zf:
        try:
            data = zf.read(str(archive_index))
        except KeyError:
            print(f"error: index {archive_index} not in archive")
            return 1

    img, sidecar = decode(data)
    INSPECT_DIR.mkdir(parents=True, exist_ok=True)
    out_path = INSPECT_DIR / f"{archive_index}.png"
    img.save(out_path)
    zoom = img.resize((img.width * 8, img.height * 8), Image.NEAREST)
    zoom_path = INSPECT_DIR / f"{archive_index}_8x.png"
    zoom.save(zoom_path)

    in_item_block = ITEM_SLOT_RANGE[0] <= archive_index < ITEM_SLOT_RANGE[1]
    sprite_id = archive_index - SPRITE_ITEM if in_item_block else None
    linked = itemdefs.find_by_sprite_id(sprite_id) if in_item_block else []

    print(f"archive entry:    {archive_index}")
    print(f"dimensions:       {sidecar['width']}×{sidecar['height']}")
    print(f"requiresShift:    {sidecar['requiresShift']}")
    print(f"xShift / yShift:  {sidecar['xShift']} / {sidecar['yShift']}")
    print(f"something1/2:     {sidecar['something1']} / {sidecar['something2']}")
    if in_item_block:
        print(f"client spriteID:  {sprite_id}  (idx = spriteID + {SPRITE_ITEM})")
        if linked:
            print(f"linked items ({len(linked)}):")
            for it in linked:
                stack = "stackable" if it["is_stackable"] else "non-stackable"
                print(f"  id={it['id']:<5d} {it['name']!r}  ({stack})")
        else:
            print("linked items:     none — empty/unused inventory slot")
    else:
        print(f"block:            outside item range [{ITEM_SLOT_RANGE[0]}, {ITEM_SLOT_RANGE[1]})")
    print(f"saved:            {out_path}")
    print(f"saved (8× zoom):  {zoom_path}")
    return 0
