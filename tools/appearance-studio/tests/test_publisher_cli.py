from __future__ import annotations

from argparse import Namespace
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from appearance_studio import __main__ as cli
from appearance_studio.model import Registry


class PublisherCliTest(unittest.TestCase):
    def test_publish_plan_connects_staged_outputs_to_transaction_bundle(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            root.mkdir()
            bundle = root / "tmp/appearance-studio/run-1"
            registry = Registry(
                path=root / "registry.yaml",
                profile="authentic",
                reservation_size=27,
                managed_namespace={},
                entries=(),
                external_reservations=(),
                tombstones=(),
            )

            def stage(_registry, staging_root, *, repo_root):
                self.assertEqual(root, repo_root)
                path = staging_root / "generated/output.bin"
                path.parent.mkdir(parents=True)
                path.write_bytes(b"candidate")
                return {
                    "bytePreserving": True,
                    "outputs": [{"path": "generated/output.bin"}],
                }

            captured = {}

            def build(repo_root, writes, bundle_dir, *, profile):
                captured.update(repo_root=repo_root, writes=writes, bundle=bundle_dir, profile=profile)
                return {"operations": [{}], "plan_id": "a" * 64, "profile": profile}

            args = Namespace(registry=str(registry.path), bundle=str(bundle))
            with (
                patch.object(cli, "REPO_ROOT", root),
                patch.object(cli, "_load", return_value=(registry, [])),
                patch.object(cli, "stage_candidate", side_effect=stage),
                patch.object(cli, "build_candidate", side_effect=build),
            ):
                with redirect_stdout(StringIO()):
                    self.assertEqual(0, cli._publish_plan(args))
            self.assertEqual(root, captured["repo_root"])
            self.assertEqual(bundle.resolve(), captured["bundle"])
            self.assertEqual("authentic", captured["profile"])
            self.assertEqual(
                bundle.resolve() / "candidate/generated/output.bin",
                captured["writes"]["generated/output.bin"],
            )

    def test_parser_requires_explicit_profile_for_apply_and_undo(self):
        parser = cli.build_parser()
        with redirect_stderr(StringIO()):
            with self.assertRaises(SystemExit):
                parser.parse_args(["apply", "--plan", "plan.json"])
            with self.assertRaises(SystemExit):
                parser.parse_args(["undo", "--manifest", "undo.json"])
        applied = parser.parse_args(["apply", "--plan", "plan.json", "--profile", "authentic"])
        undone = parser.parse_args([
            "undo", "--manifest", "undo.json", "--profile", "authentic", "--force"
        ])
        self.assertEqual("authentic", applied.profile)
        self.assertTrue(undone.force)


if __name__ == "__main__":
    unittest.main()
