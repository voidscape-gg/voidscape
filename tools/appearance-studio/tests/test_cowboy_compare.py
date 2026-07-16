from __future__ import annotations

import unittest

from appearance_studio.cowboy_compare import compare_cowboy
from appearance_studio.paths import REPO_ROOT


class CowboyCompareTest(unittest.TestCase):
    def test_cowboy_is_read_only_and_byte_preserving(self):
        report = compare_cowboy(REPO_ROOT / "content/custom/cowboy_hat/art/final/worn")
        self.assertEqual([], report["changedFrames"])
        self.assertEqual([], report["changedSidecars"])
        self.assertEqual([], report["changedArchiveEntries"])
        self.assertTrue(report["readOnly"])


if __name__ == "__main__": unittest.main()
