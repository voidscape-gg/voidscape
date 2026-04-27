"""Path constants and engine-derived sprite/render constants.

Resolved relative to package location:
    <repo>/tools/voidscim-art/voidscim/paths.py   <-- this file
    <repo>/tools/voidscim-art/                    <-- TOOL_DIR
    <repo>/                                       <-- REPO_ROOT
"""
from __future__ import annotations
from pathlib import Path

PKG_DIR = Path(__file__).resolve().parent
TOOL_DIR = PKG_DIR.parent
REPO_ROOT = TOOL_DIR.parent.parent

ARCHIVE_PATH = REPO_ROOT / "Client_Base" / "Cache" / "video" / "Authentic_Sprites.orsc"
ITEMDEFS_DIR = REPO_ROOT / "server" / "conf" / "server" / "defs"
ITEMDEFS_FILES = [
    ITEMDEFS_DIR / "ItemDefs.json",
    ITEMDEFS_DIR / "ItemDefsPatch18.json",
    ITEMDEFS_DIR / "ItemDefsCustom.json",
]

REGISTRY_PATH = PKG_DIR / "registry.yaml"
ITEMS_DIR = PKG_DIR / "items"
INSPECT_DIR = ITEMS_DIR / "_inspect"
STYLE_DIR = PKG_DIR / "style"
STYLE_LOCK_PNG = STYLE_DIR / "style_lock.png"
STYLE_LOCK_MD = STYLE_DIR / "style_lock.md"

# Sprite archive block bases — Client_Base/src/orsc/mudclient.java:61-66.
# Items occupy [SPRITE_ITEM, SPRITE_LOGO).
SPRITE_MEDIA = 2000
SPRITE_UTIL = 2100
SPRITE_ITEM = 2150
SPRITE_LOGO = 3150
ITEM_SLOT_RANGE = (SPRITE_ITEM, SPRITE_LOGO)

# Inventory rendering — Client_Base/src/orsc/mudclient.java:8060-8359.
SLOT_W, SLOT_H = 49, 34
SPRITE_DRAW_W, SPRITE_DRAW_H = 48, 32
SLOT_BG_RGB = (181, 181, 181)
SLOT_BG_ALPHA = 128
STACK_TEXT_RGB = (0xFF, 0xFF, 0x00)
STACK_TEXT_OFFSET = (1, 10)

# Quality gates.
IOU_THRESHOLD = 0.95
CHROMA_KEY_HEX = "#FF00FF"
CHROMA_TOL = 24
