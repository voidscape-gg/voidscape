from __future__ import annotations

from pathlib import Path

PKG_DIR = Path(__file__).resolve().parent
TOOL_DIR = PKG_DIR.parent
REPO_ROOT = TOOL_DIR.parent.parent

CONTENT_DIR = REPO_ROOT / "content"
CUSTOM_CONTENT_DIR = CONTENT_DIR / "custom"
UI_CONTENT_DIR = CONTENT_DIR / "ui"
UI_TOPBAR_SPEC = UI_CONTENT_DIR / "voidscape-topbar-icons.json"
TOOL_OUT_DIR = TOOL_DIR / "out"

CLIENT_ENTITY_HANDLER = (
    REPO_ROOT
    / "Client_Base"
    / "src"
    / "com"
    / "openrsc"
    / "client"
    / "entityhandling"
    / "EntityHandler.java"
)
CLIENT_CONFIG = REPO_ROOT / "Client_Base" / "src" / "orsc" / "Config.java"

DEFS_DIR = REPO_ROOT / "server" / "conf" / "server" / "defs"
ITEM_DEFS = DEFS_DIR / "ItemDefs.json"
ITEM_DEFS_CUSTOM = DEFS_DIR / "ItemDefsCustom.json"
ITEM_DEFS_PATCH18 = DEFS_DIR / "ItemDefsPatch18.json"
NPC_DEFS = DEFS_DIR / "NpcDefs.json"
NPC_DEFS_CUSTOM = DEFS_DIR / "NpcDefsCustom.json"

AUTHENTIC_SPRITES = REPO_ROOT / "Client_Base" / "Cache" / "video" / "Authentic_Sprites.orsc"
VOIDSCAPE_SKIN_DIR = REPO_ROOT / "Client_Base" / "Cache" / "voidscape" / "ui" / "skin"

SPRITE_ITEM = 2150
SPRITE_LOGO = 3150
