from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from argparse import Namespace
from unittest import mock

from appearance_studio import __main__ as cli
from appearance_studio.adopted_diagnostic import (
    COWBOY_ROOT, REFERENCE_ROOT, diagnostic_findings, frame_metrics,
    silhouette_comparison, write_cowboy_diagnostic,
)
from appearance_studio.paths import REPO_ROOT
from appearance_studio.template import load_template


TEMPLATE_PATH = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


class AdoptedCowboyDiagnosticTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.template = load_template(TEMPLATE_PATH)

    def test_cowboy_metrics_explain_panel_failures_objectively(self):
        values = {master: frame_metrics(COWBOY_ROOT, self.template, master) for master in (
            "north", "north-west", "west", "south-west", "south", "combat-west",
        )}
        self.assertEqual([236, 141, 103, 138, 235, 206],
                         [item["opaquePixels"] for item in values.values()])
        self.assertEqual([1.0, 0.76, 0.71, 0.87, 1.0, 1.0],
                         [round(item["scalpCoverageRatio"], 2) for item in values.values()])
        comparison = silhouette_comparison(values["west"], values["combat-west"])
        self.assertEqual(2.0, comparison["opaquePixelRatio"])
        self.assertEqual(0.4045, comparison["normalizedJaccard"])
        self.assertEqual(6, comparison["manhattanHausdorff"])
        codes = {item["code"] for item in diagnostic_findings(values, comparison)}
        self.assertTrue({"HAT_SCALP_EXPOSED", "HAT_SHORT_HAIR_EXPOSED",
                         "HAT_BRIM_NARROW", "HAT_PROFILE_COMBAT_DIVERGENCE",
                         "HAT_REAR_COVERAGE_DIVERGENCE"}.issubset(codes))

    def test_authentic_controls_separate_style_from_profile_consistency(self):
        expected = {
            "mediumhelm": (1.0, 0), "wizardshat": (1.0, 0), "partyhat": (0.7361, 1),
        }
        for name, (jaccard, hausdorff) in expected.items():
            west = frame_metrics(REFERENCE_ROOT / name, self.template, "west")
            combat = frame_metrics(REFERENCE_ROOT / name, self.template, "combat-west")
            comparison = silhouette_comparison(west, combat)
            self.assertEqual(jaccard, comparison["normalizedJaccard"], name)
            self.assertEqual(hausdorff, comparison["manhattanHausdorff"], name)

    def test_writer_is_tmp_only_and_contract_keeps_visual_and_mechanical_status_separate(self):
        fixture = REPO_ROOT / "tmp/workbench-r2/appearance-qa/appearance-245/composite-fixture.json"
        if not fixture.is_file():
            self.skipTest("R2 Workbench fixture is non-shipping tmp evidence")
        with tempfile.TemporaryDirectory(dir=REPO_ROOT / "tmp") as directory:
            alternate_path = Path(directory) / "alternate-template.yaml"
            alternate_path.write_bytes(TEMPLATE_PATH.read_bytes())
            alternate = load_template(alternate_path)
            report = write_cowboy_diagnostic(fixture, alternate, Path(directory))
            self.assertEqual("voidscape-adopted-appearance-diagnostic/v1", report["schema"])
            self.assertTrue(report["mechanicallyValid"])
            self.assertTrue(report["knownBad"])
            self.assertFalse(report["visuallyAcceptable"])
            self.assertFalse(report["humanVisualApproval"])
            self.assertFalse(report["shipping"])
            self.assertFalse(report["authoringEnabled"])
            self.assertFalse(report["publishable"])
            self.assertEqual(6, len(report["artifacts"]["panels"]))
            self.assertTrue(all(item["severity"] == "warning" for item in report["findings"]))
            self.assertEqual(str(alternate_path.relative_to(REPO_ROOT)), report["template"]["path"])
            self.assertEqual(alternate.digest, report["template"]["sha256"])
        with tempfile.TemporaryDirectory() as outside:
            with self.assertRaisesRegex(ValueError, "under repository tmp"):
                write_cowboy_diagnostic(fixture, self.template, Path(outside))

    def test_cli_fails_mechanical_invalidity_but_not_known_bad_visual_warnings(self):
        args = Namespace(fixture="fixture.json", template="template.yaml", out="tmp/evidence")
        base = {
            "schema": "voidscape-adopted-appearance-diagnostic/v1", "knownBad": True,
            "visuallyAcceptable": False, "warningCount": 11,
        }
        with mock.patch.object(cli, "load_template", return_value=self.template), \
                mock.patch.object(cli, "write_cowboy_diagnostic", return_value={**base, "mechanicallyValid": False}):
            self.assertEqual(1, cli._diagnose_adopted_cowboy(args))
        with mock.patch.object(cli, "load_template", return_value=self.template), \
                mock.patch.object(cli, "write_cowboy_diagnostic", return_value={**base, "mechanicallyValid": True}):
            self.assertEqual(0, cli._diagnose_adopted_cowboy(args))


if __name__ == "__main__":
    unittest.main()
