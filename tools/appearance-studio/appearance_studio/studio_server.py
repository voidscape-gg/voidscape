from __future__ import annotations

from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from io import BytesIO
import json
from pathlib import Path
import secrets
from urllib.parse import urlparse

from PIL import Image

from .geometry import expand_crop
from .paths import REPO_ROOT
from .registry import safe_repo_path
from .template import PaperdollTemplate, load_template


WEB_ROOT = Path(__file__).resolve().parent.parent / "web"
MASTER_NAMES = ("north", "north-west", "west", "south-west", "south", "combat-west")


class StudioWorkspace:
    def __init__(self, template: PaperdollTemplate, root: Path, *, read_only: bool = False,
                 cowboy_frames: Path | None = None):
        if not read_only:
            raise ValueError(
                "production authoring Studio is disabled for geometry v2 until the pose-mask editor R4"
            )
        self.template, self.root, self.read_only, self.cowboy_frames = template, root.resolve(), read_only, cowboy_frames

    def image(self, master: str) -> Image.Image:
        if master not in MASTER_NAMES:
            raise ValueError("unknown master")
        frame = next(frame for frame in self.template.frames if frame.master == master)
        if self.cowboy_frames is not None:
            path = self.cowboy_frames / f"frame_{frame.offset:02d}.png"
            sidecar = json.loads(path.with_suffix(".png.json").read_text())
            return expand_crop(Image.open(path).convert("RGBA"), sidecar)
        path = (self.root / f"{master}.png").resolve()
        path.relative_to(self.root)
        return Image.open(path).convert("RGBA") if path.exists() else Image.new("RGBA", frame.size, (0, 0, 0, 0))

    def save(self, master: str, image: Image.Image) -> None:
        if self.read_only:
            raise PermissionError("workspace is read-only")
        if master not in MASTER_NAMES:
            raise ValueError("unknown master")
        expected = next(frame.size for frame in self.template.frames if frame.master == master)
        if image.size != expected:
            raise ValueError(f"image size must be {expected}")
        path = (self.root / f"{master}.png").resolve()
        path.relative_to(self.root)
        image.save(path)


def serve_studio(workspace: StudioWorkspace, host: str, port: int) -> None:
    if host not in {"127.0.0.1", "localhost"}:
        raise ValueError("Studio must bind to loopback")
    token = secrets.token_urlsafe(24)

    class Handler(BaseHTTPRequestHandler):
        def send_bytes(self, status: int, data: bytes, content_type: str) -> None:
            self.send_response(status); self.send_header("Content-Type", content_type)
            self.send_header("Cache-Control", "no-store"); self.send_header("Content-Length", str(len(data)))
            self.end_headers(); self.wfile.write(data)

        def do_GET(self) -> None:
            parsed = urlparse(self.path)
            if parsed.path == "/api/state":
                pose_profiles = {
                    master: {
                        "visualPose": profile.visual_pose,
                        "landmarks": profile.landmarks,
                        "masks": {
                            role: None if reference is None else {
                                "path": str(reference.path.relative_to(REPO_ROOT)),
                                "sha256": reference.sha256,
                                "size": reference.size,
                            }
                            for role, reference in profile.masks.items()
                        },
                    }
                    for master, profile in workspace.template.pose_profiles.items()
                }
                body = json.dumps({"masters": MASTER_NAMES, "readOnly": workspace.read_only, "token": token,
                                   "poseProfiles": pose_profiles}).encode()
                return self.send_bytes(200, body, "application/json")
            if parsed.path.startswith("/api/master/") and parsed.path.endswith(".png"):
                name = parsed.path[len("/api/master/"):-4]
                try: image = workspace.image(name)
                except (ValueError, OSError): return self.send_bytes(404, b"missing", "text/plain")
                buffer = BytesIO(); image.save(buffer, "PNG")
                return self.send_bytes(200, buffer.getvalue(), "image/png")
            name = "index.html" if parsed.path == "/" else parsed.path.lstrip("/")
            path = (WEB_ROOT / name).resolve()
            try: path.relative_to(WEB_ROOT.resolve())
            except ValueError: return self.send_bytes(403, b"forbidden", "text/plain")
            if not path.is_file(): return self.send_bytes(404, b"missing", "text/plain")
            kind = "text/html" if path.suffix == ".html" else "application/javascript" if path.suffix == ".js" else "text/css"
            self.send_bytes(200, path.read_bytes(), kind + "; charset=utf-8")

        def do_PUT(self) -> None:
            if not self.path.startswith("/api/master/") or not self.path.endswith(".png"):
                return self.send_bytes(404, b"missing", "text/plain")
            if self.headers.get("X-Appearance-Token") != token:
                return self.send_bytes(403, b"bad token", "text/plain")
            length = int(self.headers.get("Content-Length", "0"))
            if length <= 0 or length > 1_000_000:
                return self.send_bytes(413, b"invalid body", "text/plain")
            try:
                image = Image.open(BytesIO(self.rfile.read(length))).convert("RGBA")
                workspace.save(self.path[len("/api/master/"):-4], image)
            except PermissionError as exc: return self.send_bytes(403, str(exc).encode(), "text/plain")
            except (ValueError, OSError) as exc: return self.send_bytes(400, str(exc).encode(), "text/plain")
            self.send_bytes(200, b'{"ok":true}', "application/json")

        def log_message(self, format: str, *args) -> None: return

    server = ThreadingHTTPServer((host, port), Handler)
    print(f"Appearance Studio: http://{server.server_address[0]}:{server.server_address[1]}", flush=True)
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()


def open_workspace(template_path: Path, workspace_path: str, *, cowboy: bool = False) -> StudioWorkspace:
    template = load_template(template_path)
    if cowboy:
        frames = REPO_ROOT / "content/custom/cowboy_hat/art/final/worn"
        return StudioWorkspace(template, frames, read_only=True, cowboy_frames=frames)
    return StudioWorkspace(template, safe_repo_path(workspace_path))
