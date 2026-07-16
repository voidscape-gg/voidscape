from __future__ import annotations

from argparse import Namespace
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

from appearance_studio import __main__ as cli
from appearance_studio.paths import REPO_ROOT
from appearance_studio.semantic_evidence import BUNDLE_SCHEMA, write_semantic_evidence
from appearance_studio.semantic_qa import require_semantic_report
from appearance_studio.template import load_template


TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


class SemanticEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = load_template(TEMPLATE)

    def test_bundle_is_deterministic_json_only_and_rejection_bound(self):
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as first, tempfile.TemporaryDirectory(dir=parent) as second:
            left = write_semantic_evidence(self.template, Path(first))
            right = write_semantic_evidence(self.template, Path(second))
            self.assertEqual(left, right)
            self.assertEqual(BUNDLE_SCHEMA, left["schema"])
            self.assertEqual((False, True, False, False), (
                left["shipping"], left["syntheticContractFixturesOnly"],
                left["containsArt"], left["humanVisualApproval"],
            ))
            self.assertFalse(left["publishable"])
            self.assertEqual(6, len(left["artifacts"]))
            self.assertTrue(all(path.suffix == ".json" for path in Path(first).iterdir()))
            for name, artifact in left["artifacts"].items():
                path = Path(first) / artifact["path"]
                self.assertEqual(artifact["sha256"], hashlib.sha256(path.read_bytes()).hexdigest())
                payload = json.loads(path.read_text())
                if name.startswith("valid-"):
                    require_semantic_report(payload)
                else:
                    self.assertFalse(payload["valid"])
                    self.assertTrue(left["invalidEvidenceRejectionProof"][name]["rejected"])
                    with self.assertRaisesRegex(ValueError, "contains error findings"):
                        require_semantic_report(payload)
            self.assertTrue({"mullet-debris", "mullet-nape-missing", "mullet-nape-depth"} <= set(
                left["invalidEvidenceRejectionProof"]["invalid-detached-depth-cheat-hair"]["actualErrorCodes"]
            ))
            self.assertIn("mustache-both-sides", left["invalidEvidenceRejectionProof"]
                          ["invalid-one-sided-mustache"]["actualErrorCodes"])
            self.assertIn("cross-layer-collision", left["invalidEvidenceRejectionProof"]
                          ["invalid-cross-layer-collision"]["actualErrorCodes"])

    def test_bundle_refuses_output_outside_tmp(self):
        with tempfile.TemporaryDirectory() as outside:
            with self.assertRaisesRegex(ValueError, "under repository tmp"):
                write_semantic_evidence(self.template, Path(outside))

    def test_cli_surface_and_success_contract(self):
        args = cli.build_parser().parse_args(["semantic-evidence", "--out", "tmp/custom-semantic"])
        self.assertIs(args.func, cli._semantic_evidence)
        self.assertEqual("tmp/custom-semantic", args.out)
        summary = {
            "schema": BUNDLE_SCHEMA, "shipping": False, "containsArt": False,
            "humanVisualApproval": False, "artifacts": {"fixture": {}},
        }
        with mock.patch.object(cli, "load_template", return_value=self.template), \
                mock.patch.object(cli, "write_semantic_evidence", return_value=summary) as writer:
            self.assertEqual(0, cli._semantic_evidence(Namespace(
                template=str(TEMPLATE), out="tmp/custom-semantic",
            )))
            writer.assert_called_once_with(self.template, Path("tmp/custom-semantic"))


if __name__ == "__main__":
    unittest.main()
