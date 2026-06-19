from __future__ import annotations

import base64
import json
import struct
import tempfile
import unittest
from pathlib import Path

from voidscape_content.jag import JagArchive, jag_hash
from voidscape_content.model_export import load_gltf, load_obj, source_mesh_to_ob3
from voidscape_content.ob3 import parse_ob3, rgb_to_rsc, rsc_to_rgb, write_ob3
from voidscape_content.paths import REPO_ROOT


MODELS_ORSC = REPO_ROOT / "Client_Base" / "Cache" / "video" / "models.orsc"


class ModelExportTest(unittest.TestCase):
    def test_stock_models_archive_rewrites_identically_when_unchanged(self) -> None:
        raw = MODELS_ORSC.read_bytes()
        archive = JagArchive.read(MODELS_ORSC)
        self.assertEqual(raw, archive.to_bytes(compress_outer=False))

    def test_stock_ob3_parse_write_round_trips_exact_bytes(self) -> None:
        archive = JagArchive.read(MODELS_ORSC)
        for name in ("crate", "chair", "tree", "well", "anvil"):
            with self.subTest(name=name):
                data = archive.get(f"{name}.ob3")
                self.assertIsNotNone(data)
                model = parse_ob3(data or b"")
                self.assertEqual(data, write_ob3(model))

    def test_rgb_to_rsc_round_trips_stock_flat_colors(self) -> None:
        archive = JagArchive.read(MODELS_ORSC)
        tested = 0
        for entry in archive.entries:
            model = parse_ob3(entry.data)
            for face in model.faces:
                for fill in (face.front_fill, face.back_fill):
                    rgb = rsc_to_rgb(fill)
                    if rgb is None:
                        continue
                    self.assertEqual(fill, rgb_to_rsc(*rgb))
                    tested += 1
        self.assertGreater(tested, 1000)

    def test_archive_insert_adds_hashed_entry(self) -> None:
        archive = JagArchive(entries=[])
        archive.put("void_test.ob3", b"abc")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "models.orsc"
            archive.write(path)
            reread = JagArchive.read(path)
        self.assertEqual(b"abc", reread.get("void_test.ob3"))
        self.assertEqual(jag_hash("void_test.ob3"), reread.entries[0].hash)

    def test_archive_write_preserves_outer_compression_mode(self) -> None:
        archive = JagArchive(entries=[], outer_was_compressed=True)
        archive.put("void_test.ob3", b"abc")
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "models.orsc"
            archive.write(path)
            raw = path.read_bytes()
            reread = JagArchive.read(path)
        unpacked = int.from_bytes(raw[0:3], "big")
        packed = int.from_bytes(raw[3:6], "big")
        self.assertNotEqual(unpacked, packed)
        self.assertEqual(b"abc", reread.get("void_test.ob3"))

    def test_obj_material_and_vertex_color_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "cube.mtl").write_text(
                "newmtl red\nKd 1 0 0\n",
                encoding="utf-8",
            )
            obj = root / "cube.obj"
            obj.write_text(
                "\n".join(
                    [
                        "mtllib cube.mtl",
                        "v 0 0 0 0 1 0",
                        "v 1 0 0 0 1 0",
                        "v 1 1 0 0 1 0",
                        "v 0 1 0 0 1 0",
                        "v 0 0 1 0 0 1",
                        "usemtl red",
                        "f 1 2 3 4",
                        "usemtl missing",
                        "f 1 2 5",
                    ]
                ),
                encoding="utf-8",
            )
            mesh = load_obj(obj)
        self.assertEqual(5, len(mesh.vertices))
        self.assertEqual((255, 0, 0), mesh.faces[0].color)
        self.assertEqual((0, 170, 85), mesh.faces[1].color)
        ob3 = source_mesh_to_ob3(mesh, scale=96, axis="rsc")
        self.assertEqual(2, len(ob3.faces))
        self.assertEqual(rgb_to_rsc(255, 0, 0), ob3.faces[0].front_fill)

    def test_gltf_material_color_import(self) -> None:
        positions = struct.pack(
            "<fffffffff",
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
            0.0,
            0.0,
            1.0,
            0.0,
        )
        uri = "data:application/octet-stream;base64," + base64.b64encode(positions).decode("ascii")
        doc = {
            "asset": {"version": "2.0"},
            "buffers": [{"uri": uri, "byteLength": len(positions)}],
            "bufferViews": [{"buffer": 0, "byteOffset": 0, "byteLength": len(positions)}],
            "accessors": [
                {
                    "bufferView": 0,
                    "componentType": 5126,
                    "count": 3,
                    "type": "VEC3",
                }
            ],
            "materials": [
                {"pbrMetallicRoughness": {"baseColorFactor": [0.25, 0.5, 1.0, 1.0]}}
            ],
            "meshes": [
                {
                    "primitives": [
                        {
                            "attributes": {"POSITION": 0},
                            "material": 0,
                            "mode": 4,
                        }
                    ]
                }
            ],
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "tri.gltf"
            path.write_text(json.dumps(doc), encoding="utf-8")
            mesh = load_gltf(path)
        self.assertEqual(3, len(mesh.vertices))
        self.assertEqual((64, 128, 255), mesh.faces[0].color)


if __name__ == "__main__":
    unittest.main()
