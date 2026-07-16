from __future__ import annotations

import argparse
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
import os
from pathlib import Path
import re
import secrets
from typing import Any, Mapping, Sequence
from urllib.parse import unquote, urlparse

from PIL import Image, UnidentifiedImageError

from .v2_workspace import load_v2_workspace, safe_workspace_path


WEB_ROOT = Path(__file__).resolve().parent.parent / "web"
STATIC_FILES = {
    "/": ("v2.html", "text/html; charset=utf-8"),
    "/v2.html": ("v2.html", "text/html; charset=utf-8"),
    "/v2.js": ("v2.js", "application/javascript; charset=utf-8"),
    "/v2.css": ("v2.css", "text/css; charset=utf-8"),
}
LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})
IDENTIFIER = re.compile(r"^[a-z][a-z0-9_-]{0,63}$")
FRAME_KEY = re.compile(r"^(?:0[0-9]|1[0-7])$")
DEFAULT_GRAYSCALE_SWATCHES = (32, 64, 96, 128, 160, 192, 224)
DEFAULT_ALPHA_SWATCHES = (255, 192, 128, 64, 0)
MAX_UPLOAD_BYTES = 8 * 1024 * 1024


def _swatches(values: Sequence[int], *, minimum: int, maximum: int, label: str) -> tuple[int, ...]:
    result = tuple(values)
    if not minimum <= len(result) <= maximum:
        raise ValueError(f"{label} must contain {minimum}..{maximum} values")
    if any(isinstance(value, bool) or not isinstance(value, int) or not 0 <= value <= 255 for value in result):
        raise ValueError(f"{label} values must be integers in 0..255")
    if len(set(result)) != len(result):
        raise ValueError(f"{label} values must be unique")
    return result


def _png_bytes(image: Image.Image) -> bytes:
    """Encode stable, metadata-free RGBA bytes for both HTTP and disk writes."""
    buffer = BytesIO()
    image.convert("RGBA").save(
        buffer, format="PNG", optimize=False, compress_level=9,
    )
    return buffer.getvalue()


@dataclass(frozen=True)
class V2EditorConfig:
    grayscale_swatches: tuple[int, ...] = DEFAULT_GRAYSCALE_SWATCHES
    alpha_swatches: tuple[int, ...] = DEFAULT_ALPHA_SWATCHES

    @classmethod
    def create(
        cls,
        grayscale_swatches: Sequence[int] = DEFAULT_GRAYSCALE_SWATCHES,
        alpha_swatches: Sequence[int] = DEFAULT_ALPHA_SWATCHES,
    ) -> "V2EditorConfig":
        return cls(
            _swatches(grayscale_swatches, minimum=5, maximum=7, label="grayscale swatches"),
            _swatches(alpha_swatches, minimum=3, maximum=8, label="alpha swatches"),
        )


class V2EditorWorkspace:
    """Narrow, tmp-only authoring facade over the canonical V2 workspace."""

    def __init__(self, root: Path, *, config: V2EditorConfig | None = None):
        self.root, self.manifest, self.template = load_v2_workspace(root)
        self.config = config or V2EditorConfig.create()
        self._poses = {pose["id"]: pose for pose in self.manifest["poses"]}
        self._assets = {asset["id"]: asset for asset in self.manifest["assets"]}
        self._channels: dict[tuple[str, str], Mapping[str, Any]] = {}
        for asset in self.manifest["assets"]:
            for channel in asset["channels"]:
                self._channels[(asset["id"], channel["id"])] = channel

    def _pose(self, pose_id: str) -> Mapping[str, Any]:
        if pose_id not in self._poses:
            raise KeyError(f"unknown pose: {pose_id}")
        return self._poses[pose_id]

    def _channel(self, asset_id: str, channel_id: str) -> Mapping[str, Any]:
        if asset_id not in self._assets or (asset_id, channel_id) not in self._channels:
            raise KeyError(f"unknown asset channel: {asset_id}.{channel_id}")
        return self._channels[(asset_id, channel_id)]

    def master_path(self, asset_id: str, channel_id: str, pose_id: str) -> Path:
        self._pose(pose_id)
        channel = self._channel(asset_id, channel_id)
        masters = channel.get("masters")
        if not isinstance(masters, dict) or pose_id not in masters:
            raise KeyError(f"channel has no master for pose: {asset_id}.{channel_id}.{pose_id}")
        return safe_workspace_path(self.root, masters[pose_id])

    def master_size(self, pose_id: str) -> tuple[int, int]:
        return tuple(self._pose(pose_id)["canvas"])

    def master_image(self, asset_id: str, channel_id: str, pose_id: str) -> Image.Image:
        path = self.master_path(asset_id, channel_id, pose_id)
        try:
            with Image.open(path) as image:
                image.load()
                rgba = image.convert("RGBA")
        except (FileNotFoundError, UnidentifiedImageError, OSError) as exc:
            raise FileNotFoundError(f"master image is unavailable: {path.name}") from exc
        expected = self.master_size(pose_id)
        if rgba.size != expected:
            raise ValueError(f"master image {asset_id}.{channel_id}.{pose_id} must be {expected}")
        return rgba

    def save_master(self, asset_id: str, channel_id: str, pose_id: str, image: Image.Image) -> Path:
        channel = self._channel(asset_id, channel_id)
        if not channel.get("editable", False):
            raise PermissionError(f"channel is read-only: {asset_id}.{channel_id}")
        expected = self.master_size(pose_id)
        rgba = image.convert("RGBA")
        if rgba.size != expected:
            raise ValueError(f"master image must be {expected[0]}x{expected[1]} pixels")
        path = self.master_path(asset_id, channel_id, pose_id)
        # Re-resolve immediately before writing so a replaced symlink cannot redirect
        # an editor save outside the already tmp-contained workspace.
        relative = str(path.relative_to(self.root))
        path = safe_workspace_path(self.root, relative)
        path.parent.mkdir(parents=True, exist_ok=True)
        data = _png_bytes(rgba)
        temporary = path.with_name(f".{path.name}.{secrets.token_hex(8)}.tmp")
        temporary.write_bytes(data)
        try:
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)
        return path

    def guide_image(self, pose_id: str, guide_kind: str, role: str | None = None) -> Image.Image:
        pose = self._pose(pose_id)
        guides = pose["guides"]
        if guide_kind == "base" and role is None:
            relative = guides["base"]
        elif guide_kind == "mask" and role is not None and role in guides["masks"]:
            relative = guides["masks"][role]
            if relative is None:
                raise FileNotFoundError(f"pose {pose_id} has no {role} mask")
        else:
            raise KeyError(f"unknown guide: {guide_kind}.{role or ''}")
        path = safe_workspace_path(self.root, relative)
        try:
            with Image.open(path) as image:
                image.load()
                rgba = image.convert("RGBA")
        except (FileNotFoundError, UnidentifiedImageError, OSError) as exc:
            raise FileNotFoundError(f"guide is unavailable: {pose_id}.{guide_kind}.{role or ''}") from exc
        expected = tuple(pose["canvas"])
        if rgba.size != expected:
            raise ValueError(f"guide {pose_id}.{guide_kind}.{role or ''} must be {expected}")
        return rgba

    def frame_image(self, asset_id: str, channel_id: str, frame_key: str) -> Image.Image:
        if not FRAME_KEY.fullmatch(frame_key):
            raise KeyError(f"unknown frame: {frame_key}")
        channel = self._channel(asset_id, channel_id)
        frames = channel.get("frames")
        if not isinstance(frames, dict) or frame_key not in frames:
            raise KeyError(f"channel has no explicit frame: {asset_id}.{channel_id}.{frame_key}")
        path = safe_workspace_path(self.root, frames[frame_key])
        try:
            with Image.open(path) as image:
                image.load()
                rgba = image.convert("RGBA")
        except (FileNotFoundError, UnidentifiedImageError, OSError) as exc:
            raise FileNotFoundError(f"frame is unavailable: {asset_id}.{channel_id}.{frame_key}") from exc
        spec = self.template.frames[int(frame_key)]
        if rgba.size != spec.size:
            raise ValueError(f"frame {asset_id}.{channel_id}.{frame_key} must be {spec.size}")
        return rgba

    def state(self, token: str) -> dict[str, Any]:
        # The workspace payload contains repository-relative metadata only. API URLs
        # are derived from validated identifiers, never from caller-supplied paths.
        payload = json.loads(json.dumps(self.manifest))
        for asset in payload["assets"]:
            for channel in asset["channels"]:
                asset_id, channel_id = asset["id"], channel["id"]
                if "masters" in channel:
                    channel["masterUrls"] = {
                        pose_id: f"/api/layer/{asset_id}/{channel_id}/{pose_id}.png"
                        for pose_id in channel["masters"]
                    }
                if "frames" in channel:
                    channel["frameUrls"] = {
                        frame_key: f"/api/frame/{asset_id}/{channel_id}/{frame_key}.png"
                        for frame_key in channel["frames"]
                    }
        for pose in payload["poses"]:
            pose_id = pose["id"]
            pose["guideUrls"] = {
                "base": f"/api/guide/{pose_id}/base.png",
                "masks": {
                    role: None if relative is None else f"/api/guide/{pose_id}/mask/{role}.png"
                    for role, relative in pose["guides"]["masks"].items()
                },
            }
        master_crowns = {pose["id"]: tuple(pose["crown"]) for pose in payload["poses"]}
        payload["editor"] = {
            "token": token,
            "writable": True,
            "grayscaleSwatches": list(self.config.grayscale_swatches),
            "alphaSwatches": list(self.config.alpha_swatches),
            "saveScope": "repository-tmp-only",
            "frames": [
                {
                    "offset": frame.offset,
                    "key": f"{frame.offset:02d}",
                    "poseId": frame.master,
                    "canvas": list(frame.size),
                    "crown": list(frame.crown),
                    "crownDelta": [
                        frame.crown[0] - master_crowns[frame.master][0],
                        frame.crown[1] - master_crowns[frame.master][1],
                    ],
                }
                for frame in self.template.frames
            ],
        }
        return payload


def _identifier(value: str) -> str:
    if not IDENTIFIER.fullmatch(value):
        raise KeyError(f"invalid identifier: {value!r}")
    return value


def _png_identifier(value: str) -> str:
    if not value.endswith(".png"):
        raise KeyError("PNG route must end in .png")
    return _identifier(value[:-4])


def _route_parts(raw_path: str) -> list[str]:
    decoded = unquote(urlparse(raw_path).path)
    if "\\" in decoded or "\x00" in decoded:
        raise ValueError("unsafe request path")
    parts = decoded.split("/")
    if any(part in {".", ".."} for part in parts):
        raise ValueError("unsafe request path")
    return [part for part in parts if part]


def _decode_upload(payload: bytes, expected_size: tuple[int, int]) -> Image.Image:
    try:
        with Image.open(BytesIO(payload)) as image:
            if image.format != "PNG":
                raise ValueError("request body is not a PNG image")
            if getattr(image, "n_frames", 1) != 1:
                raise ValueError("animated images are not accepted")
            # Check the tiny canonical canvas dimensions before decoding pixel data,
            # preventing a compressed image bomb from reaching Image.load().
            if image.size != expected_size:
                raise ValueError(f"master image must be {expected_size[0]}x{expected_size[1]} pixels")
            image.load()
            return image.convert("RGBA")
    except (UnidentifiedImageError, OSError) as exc:
        raise ValueError("request body is not a valid PNG image") from exc


def make_v2_editor_server(
    workspace: V2EditorWorkspace,
    host: str = "127.0.0.1",
    port: int = 0,
    *,
    token: str | None = None,
) -> ThreadingHTTPServer:
    if host not in LOOPBACK_HOSTS:
        raise ValueError("Paperdoll V2 editor must bind to loopback")
    editor_token = token or secrets.token_urlsafe(24)

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def _send(self, status: int, data: bytes, content_type: str) -> None:
            self.send_response(status)
            self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(data)

        def _error(self, status: int, message: str) -> None:
            self._send(
                status,
                (json.dumps({"error": message}, sort_keys=True) + "\n").encode("utf-8"),
                "application/json; charset=utf-8",
            )

        def _image(self, image: Image.Image) -> None:
            self._send(200, _png_bytes(image), "image/png")

        def do_HEAD(self) -> None:
            self.do_GET()

        def do_GET(self) -> None:
            try:
                parsed_path = urlparse(self.path).path
                if parsed_path in STATIC_FILES:
                    filename, content_type = STATIC_FILES[parsed_path]
                    path = (WEB_ROOT / filename).resolve()
                    path.relative_to(WEB_ROOT.resolve())
                    return self._send(200, path.read_bytes(), content_type)
                parts = _route_parts(self.path)
                if parts == ["api", "health"]:
                    return self._send(200, b'{"ok":true}\n', "application/json; charset=utf-8")
                if parts == ["api", "state"]:
                    data = json.dumps(workspace.state(editor_token), sort_keys=True).encode("utf-8")
                    return self._send(200, data, "application/json; charset=utf-8")
                if len(parts) == 5 and parts[:2] == ["api", "layer"]:
                    asset_id = _identifier(parts[2])
                    channel_id = _identifier(parts[3])
                    pose_id = _png_identifier(parts[4])
                    return self._image(workspace.master_image(asset_id, channel_id, pose_id))
                if len(parts) == 4 and parts[:2] == ["api", "guide"] and parts[3] == "base.png":
                    pose_id = _identifier(parts[2])
                    return self._image(workspace.guide_image(pose_id, "base"))
                if len(parts) == 5 and parts[:2] == ["api", "guide"] and parts[3] == "mask":
                    pose_id = _identifier(parts[2])
                    role = _png_identifier(parts[4])
                    return self._image(workspace.guide_image(pose_id, "mask", role))
                if len(parts) == 5 and parts[:2] == ["api", "frame"]:
                    asset_id = _identifier(parts[2])
                    channel_id = _identifier(parts[3])
                    frame_key = parts[4][:-4] if parts[4].endswith(".png") else ""
                    return self._image(workspace.frame_image(asset_id, channel_id, frame_key))
                if parsed_path.startswith("/api/"):
                    raise KeyError("unknown API route")
                if parsed_path not in STATIC_FILES:
                    raise ValueError("static path is not allowed")
            except ValueError as exc:
                self._error(403, str(exc))
            except (KeyError, FileNotFoundError) as exc:
                self._error(404, str(exc))
            except OSError as exc:
                self._error(500, str(exc))

        def do_PUT(self) -> None:
            try:
                parts = _route_parts(self.path)
                if not (len(parts) == 5 and parts[:2] == ["api", "layer"]):
                    raise KeyError("unknown API route")
                if self.headers.get("X-Appearance-Token") != editor_token:
                    return self._error(403, "invalid editor token")
                if self.headers.get_content_type() != "image/png":
                    return self._error(415, "Content-Type must be image/png")
                raw_length = self.headers.get("Content-Length")
                if raw_length is None:
                    return self._error(411, "Content-Length is required")
                try:
                    length = int(raw_length)
                except ValueError:
                    return self._error(400, "invalid Content-Length")
                if not 0 < length <= MAX_UPLOAD_BYTES:
                    return self._error(413, "invalid image body size")
                payload = self.rfile.read(length)
                if len(payload) != length:
                    return self._error(400, "incomplete request body")
                asset_id = _identifier(parts[2])
                channel_id = _identifier(parts[3])
                pose_id = _png_identifier(parts[4])
                # Resolve the manifest-owned target and expected canvas before
                # decoding any caller-provided pixels.
                workspace.master_path(asset_id, channel_id, pose_id)
                image = _decode_upload(payload, workspace.master_size(pose_id))
                workspace.save_master(asset_id, channel_id, pose_id, image)
                data = json.dumps({"ok": True, "bytes": len(_png_bytes(image))}, sort_keys=True).encode("utf-8")
                self._send(200, data, "application/json; charset=utf-8")
            except PermissionError as exc:
                self._error(403, str(exc))
            except (ValueError, UnidentifiedImageError) as exc:
                self._error(400, str(exc))
            except (KeyError, FileNotFoundError) as exc:
                self._error(404, str(exc))
            except OSError as exc:
                self._error(500, str(exc))

        def log_message(self, format: str, *args: object) -> None:
            return

    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    server.editor_token = editor_token  # type: ignore[attr-defined]
    server.editor_workspace = workspace  # type: ignore[attr-defined]
    return server


def serve_v2_editor(
    workspace: V2EditorWorkspace,
    host: str = "127.0.0.1",
    port: int = 0,
) -> None:
    server = make_v2_editor_server(workspace, host, port)
    address = server.server_address
    print(f"Voidscape Paperdoll V2 editor: http://{address[0]}:{address[1]}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


def _csv_swatches(value: str) -> tuple[int, ...]:
    try:
        return tuple(int(item.strip()) for item in value.split(",") if item.strip())
    except ValueError as exc:
        raise argparse.ArgumentTypeError("swatches must be comma-separated integers") from exc


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Open a tmp-only Paperdoll V2 browser editor")
    parser.add_argument("workspace", type=Path)
    parser.add_argument("--host", default="127.0.0.1", choices=sorted(LOOPBACK_HOSTS))
    parser.add_argument("--port", type=int, default=0)
    parser.add_argument("--grays", type=_csv_swatches, default=DEFAULT_GRAYSCALE_SWATCHES)
    parser.add_argument("--alphas", type=_csv_swatches, default=DEFAULT_ALPHA_SWATCHES)
    args = parser.parse_args(argv)
    config = V2EditorConfig.create(args.grays, args.alphas)
    serve_v2_editor(V2EditorWorkspace(args.workspace, config=config), args.host, args.port)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
