#!/usr/bin/env python3
"""Focused CLI contract tests for scripts/run-server.sh explicit config mode."""

from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
WRAPPER = ROOT / "scripts" / "run-server.sh"


class RunServerExplicitConfigTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="voidscape-explicit-config-")
        self.addCleanup(self.temporary.cleanup)
        root = Path(self.temporary.name)
        fake_bin = root / "bin"
        fake_bin.mkdir()
        self.capture = root / "ant-arguments.txt"
        fake_ant = fake_bin / "ant"
        fake_ant.write_text(
            "#!/bin/sh\n"
            ": \"${VOIDSCAPE_TEST_ANT_CAPTURE:?}\"\n"
            "printf '%s\\n' \"$@\" > \"$VOIDSCAPE_TEST_ANT_CAPTURE\"\n",
            encoding="utf-8",
        )
        fake_ant.chmod(0o755)
        self.environment = os.environ.copy()
        self.environment["PATH"] = f"{fake_bin}{os.pathsep}{self.environment['PATH']}"
        self.environment["VOIDSCAPE_TEST_ANT_CAPTURE"] = str(self.capture)

    def invoke(self, *arguments: str) -> tuple[subprocess.CompletedProcess[str], list[str] | None]:
        self.capture.unlink(missing_ok=True)
        result = subprocess.run(
            [str(WRAPPER), *arguments],
            cwd=ROOT,
            env=self.environment,
            check=False,
            capture_output=True,
            text=True,
        )
        captured = self.capture.read_text(encoding="utf-8").splitlines() \
            if self.capture.exists() else None
        return result, captured

    def test_explicit_basename_is_forwarded_fail_closed(self) -> None:
        result, captured = self.invoke(
            "--config", "preservation", "--explicit-config"
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ["runserverzgc", "-DconfFile=preservation", "-DexplicitConfig=true"],
            captured,
        )
        self.assertIn("preservation.conf, explicit-only", result.stdout)

    def test_optional_conf_suffix_is_normalized(self) -> None:
        result, captured = self.invoke(
            "--explicit-config", "--config", "preservation.conf"
        )
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            ["runserverzgc", "-DconfFile=preservation", "-DexplicitConfig=true"],
            captured,
        )

    def test_missing_named_config_fails_before_ant(self) -> None:
        result, captured = self.invoke(
            "--config", "definitely-missing-voidscape-config", "--explicit-config"
        )
        self.assertEqual(1, result.returncode)
        self.assertIsNone(captured)
        self.assertIn("explicit server config not found", result.stderr)

    def test_unsafe_config_names_are_rejected_before_ant(self) -> None:
        for config_name in ("../preservation", "/tmp/preservation", "nested/preservation"):
            with self.subTest(config_name=config_name):
                result, captured = self.invoke(
                    "--config", config_name, "--explicit-config"
                )
                self.assertEqual(2, result.returncode)
                self.assertIsNone(captured)
                self.assertIn("safe basename", result.stderr)

    def test_explicit_flags_must_be_paired(self) -> None:
        for arguments in (("--config", "preservation"), ("--explicit-config",)):
            with self.subTest(arguments=arguments):
                result, captured = self.invoke(*arguments)
                self.assertEqual(2, result.returncode)
                self.assertIsNone(captured)
                self.assertIn("require both", result.stderr)

    def test_zero_argument_behavior_remains_local_only(self) -> None:
        result, captured = self.invoke()
        if (ROOT / "server" / "local.conf").is_file():
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(["runserverzgc", "-DconfFile=local"], captured)
            self.assertIn("local.conf, ZGC", result.stdout)
        else:
            self.assertEqual(1, result.returncode)
            self.assertIsNone(captured)
            self.assertIn("server/local.conf not found", result.stderr)

    def test_help_does_not_invoke_ant(self) -> None:
        result, captured = self.invoke("--help")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIsNone(captured)
        self.assertIn("--config NAME --explicit-config", result.stdout)


if __name__ == "__main__":
    unittest.main()
