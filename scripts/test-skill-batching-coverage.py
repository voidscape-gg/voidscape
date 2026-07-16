#!/usr/bin/env python3
"""Regression guard for earned skill batching coverage."""

from collections import Counter
from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = ROOT / "server/plugins"


class SkillBatchingCoverageTests(unittest.TestCase):
    ALLOWED_RAW_BATCHES = {
        "com/openrsc/server/plugins/authentic/itemactions/Refill.java": 1,
        "com/openrsc/server/plugins/authentic/itemactions/SandPit.java": 1,
        "com/openrsc/server/plugins/authentic/itemactions/SoilMound.java": 1,
        "com/openrsc/server/plugins/authentic/misc/DragonstoneAmulet.java": 1,
        "com/openrsc/server/plugins/authentic/misc/Pick.java": 1,
        "com/openrsc/server/plugins/authentic/misc/PickFromTree.java": 1,
        "com/openrsc/server/plugins/authentic/misc/Sheep.java": 1,
        "com/openrsc/server/plugins/authentic/npcs/varrock/Apothecary.java": 1,
        "com/openrsc/server/plugins/authentic/skills/cooking/ObjectCooking.java": 1,
        "com/openrsc/server/plugins/authentic/skills/mining/GemMining.java": 1,
        "com/openrsc/server/plugins/authentic/skills/mining/Mining.java": 1,
        "com/openrsc/server/plugins/custom/itemactions/CustomInvUseOnItem.java": 4,
        "com/openrsc/server/plugins/custom/itemactions/LeatherTanning.java": 1,
        "com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java": 1,
    }

    def test_raw_batches_are_only_reviewed_utility_or_authentic_fallbacks(self):
        observed = Counter()
        for path in PLUGIN_ROOT.rglob("*.java"):
            count = len(re.findall(r"\bstartbatch\s*\(", path.read_text(encoding="utf-8")))
            if count:
                observed[str(path.relative_to(PLUGIN_ROOT))] = count
        self.assertEqual(Counter(self.ALLOWED_RAW_BATCHES), observed)

    def test_legacy_front_loaded_repeat_formula_is_gone(self):
        offenders = []
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if "getRepeatTimes" in path.read_text(encoding="utf-8"):
                    offenders.append(str(path.relative_to(ROOT)))
        self.assertEqual([], offenders)

    def test_skill_batches_use_the_central_helper(self):
        total = 0
        for path in PLUGIN_ROOT.rglob("*.java"):
            total += len(re.findall(r"\bstartskillbatch\s*\(", path.read_text(encoding="utf-8")))
        self.assertEqual(47, total)


if __name__ == "__main__":
    unittest.main()
