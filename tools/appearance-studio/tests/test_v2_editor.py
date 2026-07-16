from __future__ import annotations

from http.client import HTTPConnection
from io import BytesIO
import json
from pathlib import Path
import shutil
import tempfile
import threading
import unittest

from PIL import Image

from appearance_studio.paths import REPO_ROOT
from appearance_studio.v2_editor_server import (
    V2EditorConfig,
    V2EditorWorkspace,
    make_v2_editor_server,
)
from appearance_studio.v2_workspace import init_v2_workspace


def png_bytes(image: Image.Image) -> bytes:
    output = BytesIO()
    image.save(output, format="PNG", optimize=False, compress_level=9)
    return output.getvalue()


class V2EditorWorkspaceTest(unittest.TestCase):
    def setUp(self) -> None:
        (REPO_ROOT / "tmp").mkdir(exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix="v2-editor-test-", dir=REPO_ROOT / "tmp"))
        init_v2_workspace("v2-editor-test", self.root, force=True)
        self.workspace = V2EditorWorkspace(self.root)
        self.hair_asset = next(asset for asset in self.workspace.manifest["assets"] if asset["kind"] == "hair")
        self.hair_channel = next(channel for channel in self.hair_asset["channels"] if channel["editable"])
        self.hair_ids = self.hair_asset["id"], self.hair_channel["id"]

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def test_workspace_is_confined_to_repository_tmp(self):
        outside = Path(tempfile.mkdtemp(prefix="v2-editor-outside-"))
        try:
            with self.assertRaisesRegex(ValueError, "must remain under"):
                V2EditorWorkspace(outside)

            target = outside / "escaped.png"
            target.write_bytes(b"do not replace")
            master = self.workspace.master_path(*self.hair_ids, "north")
            master.unlink()
            master.symlink_to(target)
            with self.assertRaisesRegex(ValueError, "escapes root"):
                self.workspace.save_master(
                    *self.hair_ids, "north",
                    Image.new("RGBA", (128, 204), (90, 90, 90, 127)),
                )
            self.assertEqual(b"do not replace", target.read_bytes())
        finally:
            shutil.rmtree(outside, ignore_errors=True)

    def test_rgba_save_is_alpha_preserving_and_deterministic(self):
        source = Image.new("RGBA", (128, 204), (0, 0, 0, 0))
        source.putpixel((4, 5), (73, 73, 73, 1))
        source.putpixel((6, 7), (131, 131, 131, 127))
        source.putpixel((8, 9), (241, 241, 241, 254))
        path = self.workspace.save_master(*self.hair_ids, "north", source)
        first = path.read_bytes()
        loaded = self.workspace.master_image(*self.hair_ids, "north")
        self.assertEqual(source.tobytes(), loaded.tobytes())
        self.workspace.save_master(*self.hair_ids, "north", source)
        self.assertEqual(first, path.read_bytes())

    def test_save_rejects_wrong_size_and_read_only_control(self):
        with self.assertRaisesRegex(ValueError, "128x204"):
            self.workspace.save_master(
                *self.hair_ids, "north", Image.new("RGBA", (64, 102)),
            )
        with self.assertRaisesRegex(PermissionError, "read-only"):
            self.workspace.save_master(
                "legacy_head", "fixed", "north", Image.new("RGBA", (128, 204)),
            )

    def test_editor_swatches_are_bounded_and_configurable(self):
        config = V2EditorConfig.create((10, 50, 90, 130, 170), (255, 200, 100, 0))
        state = V2EditorWorkspace(self.root, config=config).state("token")
        self.assertEqual([10, 50, 90, 130, 170], state["editor"]["grayscaleSwatches"])
        self.assertEqual([255, 200, 100, 0], state["editor"]["alphaSwatches"])
        with self.assertRaisesRegex(ValueError, "5..7"):
            V2EditorConfig.create((0, 64, 128, 255), (255, 128, 0))


class V2EditorHttpTest(unittest.TestCase):
    def setUp(self) -> None:
        (REPO_ROOT / "tmp").mkdir(exist_ok=True)
        self.root = Path(tempfile.mkdtemp(prefix="v2-editor-http-", dir=REPO_ROOT / "tmp"))
        init_v2_workspace("v2-editor-http", self.root, force=True)
        self.workspace = V2EditorWorkspace(self.root)
        self.hair_asset = next(asset for asset in self.workspace.manifest["assets"] if asset["kind"] == "hair")
        self.hair_channel = next(channel for channel in self.hair_asset["channels"] if channel["editable"])
        self.hair_route = f"/api/layer/{self.hair_asset['id']}/{self.hair_channel['id']}/north.png"
        self.server = make_v2_editor_server(
            self.workspace, "127.0.0.1", 0, token="fixed-test-token",
        )
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.host, self.port = self.server.server_address

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        shutil.rmtree(self.root, ignore_errors=True)

    def request(self, method: str, path: str, body: bytes | None = None,
                headers: dict[str, str] | None = None) -> tuple[int, dict[str, str], bytes]:
        connection = HTTPConnection(self.host, self.port, timeout=5)
        try:
            connection.request(method, path, body=body, headers=headers or {})
            response = connection.getresponse()
            payload = response.read()
            return response.status, dict(response.getheaders()), payload
        finally:
            connection.close()

    def test_http_state_static_and_png_basics(self):
        code, _, body = self.request("GET", "/api/health")
        self.assertEqual(200, code)
        self.assertEqual({"ok": True}, json.loads(body))

        code, headers, body = self.request("GET", "/")
        self.assertEqual(200, code)
        self.assertIn("text/html", headers["Content-Type"])
        self.assertIn(b"Paperdoll V2 Pixel Lab", body)

        code, _, body = self.request("GET", "/api/state")
        self.assertEqual(200, code)
        state = json.loads(body)
        self.assertEqual("voidscape-paperdoll-v2-workspace/v1", state["schema"])
        self.assertFalse(state["shipping"])
        self.assertEqual("fixed-test-token", state["editor"]["token"])
        self.assertEqual(18, len(state["editor"]["frames"]))
        state_hair = next(asset for asset in state["assets"] if asset["kind"] == "hair")
        state_hair_channel = next(channel for channel in state_hair["channels"] if channel["editable"])
        self.assertEqual(self.hair_route, state_hair_channel["masterUrls"]["north"])

        code, headers, body = self.request("GET", self.hair_route)
        self.assertEqual(200, code)
        self.assertEqual("image/png", headers["Content-Type"])
        with Image.open(BytesIO(body)) as result:
            self.assertEqual((128, 204), result.size)

        code, _, body = self.request("GET", "/api/guide/north/base.png")
        self.assertEqual(200, code)
        with Image.open(BytesIO(body)) as result:
            self.assertEqual((128, 204), result.size)

        code, _, body = self.request("GET", "/api/frame/legacy_head/fixed/00.png")
        self.assertEqual(200, code)
        with Image.open(BytesIO(body)) as result:
            self.assertEqual((128, 204), result.size)

    def test_http_put_preserves_soft_alpha_and_requires_token(self):
        source = Image.new("RGBA", (128, 204), (0, 0, 0, 0))
        source.putpixel((31, 47), (117, 117, 117, 93))
        body = png_bytes(source)
        path = self.hair_route
        code, _, _ = self.request(
            "PUT", path, body,
            {"Content-Type": "image/png", "X-Appearance-Token": "wrong"},
        )
        self.assertEqual(403, code)

        code, _, response = self.request(
            "PUT", path, body,
            {"Content-Type": "image/png", "X-Appearance-Token": "fixed-test-token"},
        )
        self.assertEqual(200, code, response)
        self.assertTrue(json.loads(response)["ok"])
        code, _, response = self.request("GET", path)
        self.assertEqual(200, code)
        with Image.open(BytesIO(response)) as result:
            self.assertEqual((117, 117, 117, 93), result.convert("RGBA").getpixel((31, 47)))

        wrong_size = png_bytes(Image.new("RGBA", (64, 102)))
        code, _, _ = self.request(
            "PUT", path, wrong_size,
            {"Content-Type": "image/png", "X-Appearance-Token": "fixed-test-token"},
        )
        self.assertEqual(400, code)
        jpeg = BytesIO()
        Image.new("RGB", (128, 204)).save(jpeg, format="JPEG")
        code, _, _ = self.request(
            "PUT", path, jpeg.getvalue(),
            {"Content-Type": "image/png", "X-Appearance-Token": "fixed-test-token"},
        )
        self.assertEqual(400, code)

    def test_http_rejects_encoded_traversal_and_unknown_static_paths(self):
        code, _, _ = self.request("GET", "/%2e%2e/v2.html")
        self.assertEqual(403, code)
        code, _, _ = self.request(
            "GET", f"/api/layer/{self.hair_asset['id']}/{self.hair_channel['id']}/%2e%2e/north.png",
        )
        self.assertEqual(403, code)
        code, _, _ = self.request("GET", "/workspace.json")
        self.assertEqual(403, code)

    def test_non_loopback_bind_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "loopback"):
            make_v2_editor_server(self.workspace, "0.0.0.0", 0)


if __name__ == "__main__":
    unittest.main()
