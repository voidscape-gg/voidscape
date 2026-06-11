from __future__ import annotations

import argparse
import json
import mimetypes
import sys
import webbrowser
from dataclasses import asdict
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
STATIC_DIR = SCRIPT_DIR / "static"
VOIDSCAPE_CONTENT = REPO_ROOT / "tools" / "voidscape-content"
VOIDSCIM_ART = REPO_ROOT / "tools" / "voidscim-art"

for path in (VOIDSCAPE_CONTENT, VOIDSCIM_ART):
    value = str(path)
    if value not in sys.path:
        sys.path.insert(0, value)

from voidscape_content.defs import ids, load_def_array  # noqa: E402
from voidscape_content.java_parse import (  # noqa: E402
    load_client_items,
    load_client_npcs,
    load_client_version,
)
from voidscape_content.manifest import (  # noqa: E402
    VALID_KINDS,
    scaffold_pack,
    title_from_slug,
)
from voidscape_content.paths import (  # noqa: E402
    CUSTOM_CONTENT_DIR,
    ITEM_DEFS,
    ITEM_DEFS_CUSTOM,
    NPC_DEFS,
    NPC_DEFS_CUSTOM,
)
from voidscape_content.report import _next_archive_slot  # noqa: E402
from voidscape_content.validate import validate_repo  # noqa: E402


MAX_BODY_BYTES = 64 * 1024


def _plain_yaml_value(raw: str) -> str:
    value = raw.strip()
    if not value or value == "null":
        return ""
    if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):
        try:
            parsed = json.loads(value) if value.startswith('"') else value[1:-1]
            return str(parsed)
        except json.JSONDecodeError:
            return value[1:-1]
    return value


def _manifest_summary(pack: Path) -> dict[str, str]:
    summary = {
        "slug": pack.name,
        "kind": "",
        "name": title_from_slug(pack.name),
        "status": "",
        "description": "",
    }
    manifest = pack / "content.yaml"
    if not manifest.exists():
        summary["status"] = "missing manifest"
        return summary
    for line in manifest.read_text(errors="replace").splitlines():
        if not line or line.startswith("#") or line.startswith(" "):
            continue
        key, sep, value = line.partition(":")
        if not sep:
            continue
        key = key.strip()
        if key in summary:
            summary[key] = _plain_yaml_value(value)
    return summary


def _content_packs() -> list[dict[str, str]]:
    if not CUSTOM_CONTENT_DIR.exists():
        return []
    packs = []
    for pack in sorted(CUSTOM_CONTENT_DIR.iterdir()):
        if pack.is_dir() and not pack.name.startswith("."):
            packs.append(_manifest_summary(pack))
    return packs


def _allocation_state() -> dict[str, Any]:
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

    return {
        "clientVersion": load_client_version(),
        "items": {
            "serverRange": f"0..{max_item_id}",
            "serverCount": len(item_ids),
            "clientRange": f"0..{max_client_item_id}",
            "clientCount": len(client_items),
            "nextId": max(max_item_id, max_client_item_id) + 1,
            "nextSpriteId": next_sprite_id,
            "nextArchiveIndex": next_archive_index,
            "skippedArchiveSlots": skipped[:12],
        },
        "npcs": {
            "serverRange": f"0..{max_npc_id}",
            "serverCount": len(npc_ids),
            "clientCount": len(client_npcs),
            "nextId": max_npc_id + 1,
        },
    }


def _validation_state() -> dict[str, Any]:
    result = validate_repo()
    return {
        "clientVersion": result.client_version,
        "errors": len(result.errors),
        "warnings": len(result.warnings),
        "findings": [asdict(finding) for finding in result.findings],
    }


def _state() -> dict[str, Any]:
    return {
        "repoRoot": str(REPO_ROOT),
        "validKinds": list(VALID_KINDS),
        "allocation": _allocation_state(),
        "validation": _validation_state(),
        "packs": _content_packs(),
        "commands": [
            'scripts/content.sh new item void_relic --name "Void relic"',
            'scripts/content.sh voidscim new-icon "a cracked void relic"',
            "scripts/content.sh voidscim fit path/to/cell.png --lanczos",
            'scripts/content.sh voidscim register --png path/to/fit.png --name "Void relic" --description "..." --commit',
            "scripts/content.sh validate",
        ],
    }


def _json_response(handler: BaseHTTPRequestHandler, status: HTTPStatus, payload: dict[str, Any]) -> None:
    body = json.dumps(payload, indent=2).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Cache-Control", "no-store")
    handler.send_header("Content-Length", str(len(body)))
    handler.end_headers()
    handler.wfile.write(body)


def _error(handler: BaseHTTPRequestHandler, status: HTTPStatus, message: str) -> None:
    _json_response(handler, status, {"ok": False, "error": message})


class ContentStudioHandler(BaseHTTPRequestHandler):
    server_version = "VoidscapeContentStudio/1"

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{self.address_string()} - {fmt % args}", file=sys.stderr)

    def do_GET(self) -> None:
        if self.path == "/api/state":
            try:
                _json_response(self, HTTPStatus.OK, {"ok": True, "state": _state()})
            except Exception as exc:
                _error(self, HTTPStatus.INTERNAL_SERVER_ERROR, str(exc))
            return
        self._serve_static()

    def do_HEAD(self) -> None:
        if self.path.startswith("/api/"):
            self.send_error(HTTPStatus.METHOD_NOT_ALLOWED)
            return
        self._serve_static(head_only=True)

    def do_POST(self) -> None:
        if self.path != "/api/packs":
            _error(self, HTTPStatus.NOT_FOUND, "unknown endpoint")
            return

        length = int(self.headers.get("Content-Length", "0") or "0")
        if length < 1 or length > MAX_BODY_BYTES:
            _error(self, HTTPStatus.BAD_REQUEST, "invalid request size")
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            _error(self, HTTPStatus.BAD_REQUEST, "invalid JSON")
            return

        try:
            kind = str(payload.get("kind", "")).strip()
            slug = str(payload.get("slug", "")).strip()
            name = str(payload.get("name", "")).strip() or None
            description = str(payload.get("description", "")).strip()
            like = str(payload.get("like", "")).strip() or None
            force = bool(payload.get("force", False))
            root = scaffold_pack(kind, slug, name=name, description=description, like=like, force=force)
            _json_response(
                self,
                HTTPStatus.OK,
                {
                    "ok": True,
                    "created": str(root),
                    "state": _state(),
                },
            )
        except (ValueError, FileExistsError) as exc:
            _error(self, HTTPStatus.BAD_REQUEST, str(exc))
        except Exception as exc:
            _error(self, HTTPStatus.INTERNAL_SERVER_ERROR, str(exc))

    def _serve_static(self, *, head_only: bool = False) -> None:
        request_path = self.path.split("?", 1)[0]
        if request_path in ("", "/"):
            target = STATIC_DIR / "index.html"
        elif request_path.startswith("/static/"):
            target = STATIC_DIR / request_path.removeprefix("/static/")
        else:
            target = STATIC_DIR / "index.html"

        try:
            target = target.resolve()
            target.relative_to(STATIC_DIR.resolve())
        except ValueError:
            self.send_error(HTTPStatus.FORBIDDEN)
            return
        if not target.exists() or not target.is_file():
            self.send_error(HTTPStatus.NOT_FOUND)
            return

        content_type = mimetypes.guess_type(str(target))[0] or "application/octet-stream"
        body = target.read_bytes()
        self.send_response(HTTPStatus.OK)
        self.send_header("Content-Type", content_type)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if not head_only:
            self.wfile.write(body)


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Voidscape Content Studio GUI.")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--open", action="store_true", help="open the GUI in the default browser")
    args = parser.parse_args()

    httpd = ThreadingHTTPServer((args.host, args.port), ContentStudioHandler)
    url = f"http://{args.host}:{args.port}/"
    print(f"Voidscape Content Studio: {url}")
    print("Press Ctrl-C to stop.")
    if args.open:
        webbrowser.open(url)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("")
    finally:
        httpd.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
