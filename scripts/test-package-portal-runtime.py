#!/usr/bin/env python3

from __future__ import annotations

import json
import os
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
PACKAGER = ROOT / "scripts" / "package-portal-runtime.py"
CONTRACT = ROOT / "web" / "portal" / "public-static-contract.json"


class PortalRuntimePackageTest(unittest.TestCase):
    def test_package_contains_only_allowlisted_portal_runtime(self):
        contract = json.loads(CONTRACT.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "portal"
            subprocess.run(
                [
                    "python3",
                    str(PACKAGER),
                    "--repo-root",
                    str(ROOT),
                    "--target",
                    str(target),
                ],
                check=True,
            )
            actual_root_files = {
                path.name for path in target.iterdir() if path.is_file()
            }
            self.assertEqual(
                {
                    "dev-server.mjs",
                    "public-static-contract.json",
                    *contract["rootFiles"],
                },
                actual_root_files,
            )
            for forbidden in (
                "api-smoke.mjs",
                "README.md",
                "schema/mysql/001_web_accounts.sql",
                "schema/sqlite/001_web_accounts.sql",
                "build-meta.json",
            ):
                self.assertFalse((target / forbidden).exists(), forbidden)
            packaged_assets = sorted(
                path.relative_to(target).as_posix()
                for path in (target / "assets").rglob("*")
                if path.is_file()
            )
            tracked_assets = subprocess.check_output(
                ["git", "-C", str(ROOT), "ls-files", "web/portal/assets"],
                text=True,
            ).splitlines()
            tracked_asset_relatives = {
                path.removeprefix("web/portal/") for path in tracked_assets
            }
            expected_assets = set(contract["assetFiles"])
            expected_assets.update(
                path
                for path in tracked_asset_relatives
                if str(Path(path).parent).replace("\\", "/")
                in contract["numericPngDirectories"]
                and re.fullmatch(r"[0-9]+\.png", Path(path).name)
            )
            self.assertEqual(sorted(expected_assets), packaged_assets)
            expected_files = {
                "dev-server.mjs",
                "public-static-contract.json",
                *contract["rootFiles"],
                *expected_assets,
            }
            actual_files = {
                path.relative_to(target).as_posix()
                for path in target.rglob("*")
                if path.is_file()
            }
            self.assertEqual(expected_files, actual_files)

            expected_directories = {
                parent.as_posix()
                for relative in expected_files
                for parent in Path(relative).parents
                if parent.as_posix() != "."
            }
            actual_directories = {
                path.relative_to(target).as_posix()
                for path in target.rglob("*")
                if path.is_dir()
            }
            self.assertEqual(expected_directories, actual_directories)
            for directory, names, files in os.walk(target, followlinks=False):
                for name in [*names, *files]:
                    entry = Path(directory) / name
                    self.assertFalse(entry.is_symlink(), entry)
                    self.assertTrue(entry.is_dir() or entry.is_file(), entry)
            self.assertGreater(len(packaged_assets), 400)
            self.assertIn("assets/loot-editor-data.json", packaged_assets)
            self.assertNotIn("assetExtensions", contract)


if __name__ == "__main__":
    unittest.main(verbosity=2)
