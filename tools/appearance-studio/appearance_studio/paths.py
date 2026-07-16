from __future__ import annotations

from pathlib import Path


PACKAGE_DIR = Path(__file__).resolve().parent
TOOL_DIR = PACKAGE_DIR.parent
REPO_ROOT = TOOL_DIR.parent.parent
REGISTRY_PATH = REPO_ROOT / "content" / "appearance" / "registry.yaml"
NAMESPACES_PATH = REPO_ROOT / "content" / "appearance" / "namespaces.yaml"
CLIENT_ARCHIVE = REPO_ROOT / "Client_Base" / "Cache" / "video" / "Authentic_Sprites.orsc"
SERVER_ARCHIVE = REPO_ROOT / "server" / "conf" / "server" / "data" / "Authentic_Sprites.orsc"
MD5_FILE = REPO_ROOT / "Client_Base" / "Cache" / "MD5.SUM"
ENTITY_HANDLER = REPO_ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
AVATAR_GENERATOR = REPO_ROOT / "server" / "src" / "com" / "openrsc" / "server" / "avatargenerator" / "AvatarGenerator.java"
