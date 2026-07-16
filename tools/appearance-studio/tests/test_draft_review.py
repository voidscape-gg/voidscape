from __future__ import annotations

import tempfile
import unittest
import json
from pathlib import Path

from appearance_studio.draft_review import DraftReview
from appearance_studio.paths import REPO_ROOT


class DraftReviewTest(unittest.TestCase):
    def test_reconstructed_v1_draft_is_revoked_before_review(self):
        parent = REPO_ROOT / "tmp/appearance-studio"
        parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as temporary:
            root = Path(temporary)
            (root / "report.json").write_text(json.dumps({
                "schema": "voidscape-draft-look/v1",
                "geometryContract": "voidscape-paperdoll-geometry/v1",
                "shipping": False,
            }))
            with self.assertRaisesRegex(
                ValueError,
                "draft evidence schema 'voidscape-draft-look/v1' is revoked.*pose-specific anatomical anchors",
            ):
                DraftReview(root)

    def test_review_root_must_remain_under_tmp(self):
        with tempfile.TemporaryDirectory() as outside:
            with self.assertRaises(ValueError):
                DraftReview(Path(outside))


if __name__ == "__main__":
    unittest.main()
