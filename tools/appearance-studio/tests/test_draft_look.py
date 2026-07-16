from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from appearance_studio.draft_look import build_draft_look
from appearance_studio.paths import REPO_ROOT


LOOK = REPO_ROOT / "content/appearance/proposals/future_mullet_mustache.yaml"
TEMPLATE = REPO_ROOT / "content/appearance/templates/rsc-player-v1/template.yaml"


class DraftLookTest(unittest.TestCase):
    def test_build_fails_closed_before_writing_revoked_v1_evidence(self):
        temporary_root = REPO_ROOT / "tmp" / "appearance-studio"
        temporary_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=temporary_root) as temporary:
            root = Path(temporary)
            with self.assertRaisesRegex(
                ValueError,
                "draft-look generation.*voidscape-draft-look/v1.*disabled.*pose-specific anatomical anchors",
            ):
                build_draft_look(LOOK, TEMPLATE, root)
            self.assertEqual([], list(root.iterdir()))

    def test_build_still_rejects_output_outside_repository_tmp(self):
        with tempfile.TemporaryDirectory() as temporary:
            with self.assertRaisesRegex(ValueError, "under repository tmp"):
                build_draft_look(LOOK, TEMPLATE, Path(temporary) / "forbidden")


if __name__ == "__main__":
    unittest.main()
