from __future__ import annotations

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from PIL import Image

from appearance_studio.paths import REPO_ROOT
from appearance_studio.v2_archive import encode_properties, parse_properties, write_v2_pack
from appearance_studio.v2_build import _write_selector_registry
from appearance_studio.v2_compiler import compile_v2_workspace
from appearance_studio.v2_legacy_export import (
    RUNTIME_PROPERTIES_NAME, RUNTIME_PROPERTIES_SCHEMA,
    legacy_runtime_properties_values, validate_legacy_runtime_properties,
    validate_v2_legacy_export, write_v2_legacy_export,
)
from appearance_studio.v2_workspace import init_v2_collection_workspace


def tree_digest(root: Path) -> str:
    digest = hashlib.sha256()
    if not root.exists():
        return digest.hexdigest()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        digest.update(str(path.relative_to(root)).encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
    return digest.hexdigest()


def component_count(image: Image.Image) -> int:
    alpha = image.convert("RGBA").getchannel("A")
    remaining = {
        (x, y) for y in range(alpha.height) for x in range(alpha.width)
        if alpha.getpixel((x, y)) > 0
    }
    components = 0
    while remaining:
        components += 1
        pending = [remaining.pop()]
        while pending:
            x, y = pending.pop()
            for neighbour in ((x, y - 1), (x - 1, y), (x + 1, y), (x, y + 1)):
                if neighbour in remaining:
                    remaining.remove(neighbour)
                    pending.append(neighbour)
    return components


class PaperdollV2LegacyExportTest(unittest.TestCase):
    def workspace(self) -> Path:
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        temporary = tempfile.TemporaryDirectory(dir=parent)
        self.addCleanup(temporary.cleanup)
        return Path(temporary.name) / "legacy-export"

    def test_export_is_build_only_complete_deterministic_and_corruption_checked(self):
        cache = REPO_ROOT / "Client_Base/Cache/voidscape/hair"
        server_avatars = REPO_ROOT / "server/avatars"
        external_before = tree_digest(cache), tree_digest(server_avatars)

        root = self.workspace()
        init_v2_collection_workspace(root)
        result = compile_v2_workspace(root)
        build = root / "build"
        build.mkdir()
        pack = write_v2_pack(result, build / "Paperdoll_V2.orsc")
        _write_selector_registry(result, build, pack.path)
        summary = write_v2_legacy_export(result, build, pack.path)
        self.assertTrue(summary["valid"])
        self.assertEqual(108, summary["overlayFrameCount"])
        self.assertEqual(9, summary["detachedPixelsRemoved"])
        self.assertEqual(14, summary["avatarThumbnailCount"])
        self.assertEqual(
            "build/legacy-compatibility/runtime.properties",
            summary["runtimePropertiesPath"],
        )

        export = build / "legacy-compatibility"
        manifest_path = export / "manifest.json"
        runtime_path = export / RUNTIME_PROPERTIES_NAME
        manifest = json.loads(manifest_path.read_text())
        runtime_bytes = runtime_path.read_bytes()
        runtime = parse_properties(runtime_bytes)
        expected_runtime = legacy_runtime_properties_values(
            manifest, hashlib.sha256(manifest_path.read_bytes()).hexdigest()
        )
        self.assertEqual(expected_runtime, runtime)
        self.assertEqual(runtime_bytes, encode_properties(runtime))
        self.assertEqual(RUNTIME_PROPERTIES_SCHEMA, runtime["schema"])
        self.assertEqual("false", runtime["shipping"])
        self.assertEqual("false", runtime["activation.approved"])
        self.assertEqual("false", runtime["default.enabled"])
        self.assertEqual("pc-workbench", runtime["runtime.platforms"])
        self.assertEqual("8", runtime["runtime.compatible_head_appearance_id"])
        self.assertEqual("0", runtime["runtime.hat.allowed_appearance_ids"])
        self.assertEqual("suppress-overlay", runtime["runtime.hat.nonzero.action"])
        self.assertEqual("reject-whole-style", runtime["runtime.style_failure_policy"])
        self.assertEqual("1,2,3,4,5,6", runtime["selector.ids"])
        self.assertEqual("108", runtime["overlay.frame_count"])
        self.assertEqual("216", runtime["overlay.file_count"])
        self.assertEqual(
            hashlib.sha256(runtime_bytes).hexdigest(), summary["runtimePropertiesSha256"]
        )
        self.assertEqual(
            {
                "path": "build/legacy-compatibility/runtime.properties",
                "schema": RUNTIME_PROPERTIES_SCHEMA,
            },
            manifest["runtimeContract"],
        )
        self.assertFalse(any(
            artifact["path"] == RUNTIME_PROPERTIES_NAME for artifact in manifest["artifacts"]
        ))
        self.assertEqual([0, 0], manifest["downscale"]["samplePhase"])
        self.assertEqual(9, manifest["detachedPixelsRemoved"])
        removed = {
            (overlay["selectorId"], frame["offset"]): frame["detachedPixelsRemoved"]
            for overlay in manifest["overlays"] for frame in overlay["frames"]
            if frame["detachedPixelsRemoved"]
        }
        self.assertEqual(
            {(1, offset): 1 for offset in (0, 1, 2, 9, 10, 11, 12, 13, 14)},
            removed,
        )
        for overlay in manifest["overlays"]:
            for frame in overlay["frames"]:
                if frame["detachedPixelsRemoved"] == 0:
                    self.assertEqual(
                        frame["sourceDecimatedRgbaSha256"], frame["logicalRgbaSha256"]
                    )
        self.assertFalse((export / "voidscape/hair/style_00").exists())
        for selector_id in range(1, 7):
            style = export / "voidscape/hair" / f"style_{selector_id:02d}"
            self.assertEqual(
                manifest["overlays"][selector_id - 1]["style"],
                runtime[f"selector.{selector_id}.style"],
            )
            self.assertEqual("18", runtime[f"selector.{selector_id}.frame.count"])
            self.assertEqual(18, len(list(style.glob("frame_*.png"))))
            self.assertEqual(18, len(list(style.glob("frame_*.properties"))))
            for offset in range(18):
                png = style / f"frame_{offset:02d}.png"
                sidecar_path = style / f"frame_{offset:02d}.properties"
                values = parse_properties(sidecar_path.read_bytes())
                prefix = f"selector.{selector_id}.frame.{offset:02d}"
                self.assertEqual("true", values["requiresShift"])
                self.assertEqual("102", values["something2"])
                self.assertEqual("84" if offset >= 15 else "64", values["something1"])
                self.assertEqual(
                    hashlib.sha256(png.read_bytes()).hexdigest(), runtime[f"{prefix}.png.sha256"]
                )
                self.assertEqual(
                    hashlib.sha256(sidecar_path.read_bytes()).hexdigest(),
                    runtime[f"{prefix}.sidecar.sha256"],
                )
                self.assertEqual(values["xShift"], runtime[f"{prefix}.crop.x"])
                self.assertEqual(values["yShift"], runtime[f"{prefix}.crop.y"])
                self.assertEqual(values["something1"], runtime[f"{prefix}.logical.width"])
                self.assertEqual(values["something2"], runtime[f"{prefix}.logical.height"])
                with Image.open(png) as image:
                    self.assertEqual(("PNG", "RGBA"), (image.format, image.mode))
                    self.assertEqual(str(image.width), runtime[f"{prefix}.crop.width"])
                    self.assertEqual(str(image.height), runtime[f"{prefix}.crop.height"])
                    self.assertTrue(image.getchannel("A").getbbox())
                    self.assertTrue(all(
                        not alpha or red == green == blue
                        for red, green, blue, alpha in image.getdata()
                    ))
                    logical = Image.new(
                        "RGBA", (int(values["something1"]), int(values["something2"])),
                        (0, 0, 0, 0),
                    )
                    logical.paste(image, (int(values["xShift"]), int(values["yShift"])))
                    self.assertEqual(1, component_count(logical))
                    self.assertEqual(
                        (int(values["xShift"]), int(values["yShift"]),
                         int(values["xShift"]) + image.width,
                         int(values["yShift"]) + image.height),
                        logical.getchannel("A").getbbox(),
                    )

        for selector_id in range(7):
            for base in ("male", "female"):
                with Image.open(export / "avatars" / f"selector_{selector_id:02d}" / f"{base}.png") as image:
                    self.assertEqual(("PNG", "RGBA", (64, 102)), (image.format, image.mode, image.size))
                    self.assertTrue(image.getchannel("A").getbbox())

        first_digest = tree_digest(export)
        write_v2_legacy_export(result, build, pack.path)
        self.assertEqual(first_digest, tree_digest(export))
        self.assertEqual(external_before, (tree_digest(cache), tree_digest(server_avatars)))
        for path in export.rglob("*"):
            path.resolve().relative_to(build.resolve())

        canonical_runtime = runtime_path.read_bytes()
        runtime_values = parse_properties(canonical_runtime)
        unknown = dict(runtime_values)
        unknown["unknown.key"] = "reject"
        runtime_path.write_bytes(encode_properties(unknown))
        with self.assertRaisesRegex(ValueError, "unknown=.*unknown.key"):
            validate_legacy_runtime_properties(export, manifest)
        runtime_path.write_bytes(canonical_runtime)

        missing = dict(runtime_values)
        missing.pop("selector.6.frame.17.crop.height")
        runtime_path.write_bytes(encode_properties(missing))
        with self.assertRaisesRegex(ValueError, "missing=.*selector.6.frame.17.crop.height"):
            validate_legacy_runtime_properties(export, manifest)
        runtime_path.write_bytes(canonical_runtime)

        changed = dict(runtime_values)
        changed["pack.sha256"] = "0" * 64
        runtime_path.write_bytes(encode_properties(changed))
        with self.assertRaisesRegex(ValueError, "changed=.*pack.sha256"):
            validate_legacy_runtime_properties(export, manifest)
        runtime_path.write_bytes(canonical_runtime)

        manifest_bytes = manifest_path.read_bytes()
        manifest_path.write_bytes(manifest_bytes + b" ")
        with self.assertRaisesRegex(ValueError, "changed=.*manifest.sha256"):
            validate_legacy_runtime_properties(export, manifest)
        manifest_path.write_bytes(manifest_bytes)
        self.assertEqual(runtime_values, validate_legacy_runtime_properties(export, manifest))

        properties = export / "voidscape/hair/style_01/frame_00.properties"
        data = properties.read_bytes()
        old_shift = int(parse_properties(data)["xShift"])
        properties.write_bytes(data.replace(
            f"xShift={old_shift}\n".encode("ascii"),
            f"xShift={old_shift + 1}\n".encode("ascii"),
        ))
        with self.assertRaisesRegex(ValueError, "differs from deterministic 2:1 export"):
            validate_v2_legacy_export(result, build, pack.path)


if __name__ == "__main__":
    unittest.main()
