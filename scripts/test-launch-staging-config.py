#!/usr/bin/env python3
"""Focused regression tests for the launch config/package safety contract."""

from __future__ import annotations

import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
GENERATOR_PATH = ROOT / "scripts" / "generate-launch-server-config.py"
sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("launch_config_generator", GENERATOR_PATH)
assert SPEC and SPEC.loader
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class LaunchStagingConfigTest(unittest.TestCase):
    def test_preservation_base_renders_exact_launch_contract(self):
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "local.launch-staging.conf"
            expected = GENERATOR.render_config(
                ROOT / "server" / "preservation.conf",
                target,
                "10138",
                "43596",
                "43496",
            )
            text = target.read_text(encoding="utf-8")
            for key, value in expected.items():
                matches = list(GENERATOR.key_pattern(key).finditer(text))
                self.assertEqual(1, len(matches), key)
                self.assertEqual(value, matches[0].group("value").strip(), key)

    def test_duplicate_release_key_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "duplicate.conf"
            source.write_text("db_name: one\ndb_name: two\n", encoding="utf-8")
            with self.assertRaisesRegex(GENERATOR.LaunchConfigError, "duplicate.*db_name"):
                GENERATOR.render_config(
                    source,
                    directory / "out.conf",
                    "10138",
                    "43596",
                    "43496",
                )

    def test_sensitive_base_preset_fails_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            source = directory / "secret.conf"
            source.write_text(
                "db_name: preservation\n"
                "discord_global_chat_webhook_url: https://example.invalid/private\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(
                GENERATOR.LaunchConfigError, "sensitive setting.*webhook_url"
            ):
                GENERATOR.render_config(
                    source,
                    directory / "out.conf",
                    "10138",
                    "43596",
                    "43496",
                )

    def test_hosted_verifier_checks_configs_without_network_access(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            server_config = directory / "local.conf"
            connections_config = directory / "connections.conf"
            GENERATOR.render_config(
                ROOT / "server" / "preservation.conf",
                server_config,
                "10138",
                "43596",
                "43496",
            )
            connections_config.write_text("db_type: sqlite\n", encoding="utf-8")

            output = directory / "pass"
            command = [
                "node",
                "scripts/verify-launch-staging.mjs",
                "--server-config",
                str(server_config),
                "--connections-config",
                str(connections_config),
                "--expected-client-version",
                "10138",
                "--server-config-only",
                "--out",
                str(output),
            ]
            passed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(0, passed.returncode, passed.stdout + passed.stderr)
            summary = json.loads((output / "summary.json").read_text(encoding="utf-8"))
            self.assertTrue(summary["serverConfigOnly"])
            self.assertEqual([], summary["failures"])

            server_config.write_text(
                server_config.read_text(encoding="utf-8") + "\ndb_name: other\n",
                encoding="utf-8",
            )
            failed_output = directory / "duplicate"
            command[-1] = str(failed_output)
            failed = subprocess.run(command, cwd=ROOT, text=True, capture_output=True)
            self.assertEqual(1, failed.returncode, failed.stdout + failed.stderr)
            failed_summary = json.loads(
                (failed_output / "summary.json").read_text(encoding="utf-8")
            )
            duplicate_check = next(
                check
                for check in failed_summary["checks"]
                if check["name"] == "server config db_name unique"
            )
            self.assertEqual("fail", duplicate_check["status"])

    def test_bundle_database_paths_and_verifier_are_fail_closed(self):
        package = (ROOT / "scripts" / "package-launch-staging.sh").read_text(
            encoding="utf-8"
        )
        database = "/opt/voidscape/server/inc/sqlite/voidscape.db"
        self.assertGreaterEqual(package.count(database), 4)
        self.assertNotIn("/opt/voidscape/server/inc/sqlite/preservation.db", package)
        self.assertIn("VOIDSCAPE_DEPLOYED_SERVER_CONFIG", package)
        self.assertIn("VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG", package)
        self.assertIn("VOIDSCAPE_VERIFY_RUN_SIGNUP", package)
        self.assertIn("SIGNUP_MODE=(--skip-signup)", package)
        self.assertIn(
            "VOIDSCAPE_REPO_ROOT=/path/to/voidscape-source \\\\\n",
            package,
        )
        self.assertIn("\\`VOIDSCAPE_VERIFY_RUN_SIGNUP=1\\`", package)
        self.assertNotIn(
            '--server-config "\\$BUNDLE_DIR/server/local.launch-staging.conf"',
            package,
        )


if __name__ == "__main__":
    unittest.main(verbosity=2)
