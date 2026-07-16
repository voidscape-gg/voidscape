#!/usr/bin/env python3
"""Static regression checks for VS-082 cooperative gather cancellation."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
PLUGIN_ROOT = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills"


class GatherInterruptionContractTests(unittest.TestCase):
    CASES = (
        (
            PLUGIN_ROOT / "woodcutting/Woodcutting.java",
            "private void batchWoodcutting",
            "\n\t@Override\n\tpublic void onOpLoc",
            3,
            (
                "GuaranteedResources.shouldGuarantee",
                "getLog(def",
                "getInventory().add(log)",
                "incExp(Skill.WOODCUTTING.id()",
            ),
        ),
        (
            PLUGIN_ROOT / "mining/Mining.java",
            "private void batchMining",
            "\n\t@Override\n\tpublic boolean blockOpLoc",
            3,
            (
                "GuaranteedResources.shouldGuarantee",
                "getOre(def",
                "getInventory().add(ore)",
                "incExp(Skill.MINING.id()",
            ),
        ),
        (
            PLUGIN_ROOT / "fishing/Fishing.java",
            "private void batchFishing",
            "\n\tprivate ObjectFishDef firstEligibleFish",
            4,
            (
                "getFish(def",
                "doBigNetFishingRoll",
                "inventory.add(",
                "incExp(Skill.FISHING.id()",
            ),
        ),
    )

    @staticmethod
    def method_body(path: Path, start_marker: str, end_marker: str) -> str:
        source = path.read_text(encoding="utf-8")
        start = source.index(start_marker)
        end = source.index(end_marker, start)
        return source[start:end]

    def test_opening_delay_is_immediately_followed_by_interrupt_exit(self):
        for path, start, end, delay_ticks, _ in self.CASES:
            with self.subTest(skill=path.parent.name):
                body = self.method_body(path, start, end)
                guarded_delay = re.compile(
                    rf"delay\({delay_ticks}\);\s*"
                    r"if\s*\(ifinterrupted\(\)\)\s*\{\s*return;\s*\}"
                )
                self.assertEqual(
                    1,
                    len(guarded_delay.findall(body)),
                    "opening animation delay must have one immediate interruption exit",
                )

    def test_interrupt_exit_precedes_every_success_roll_and_reward(self):
        for path, start, end, delay_ticks, sensitive_tokens in self.CASES:
            with self.subTest(skill=path.parent.name):
                body = self.method_body(path, start, end)
                delay_index = body.index(f"delay({delay_ticks});")
                guard_index = body.index("if (ifinterrupted())", delay_index)
                self.assertGreater(guard_index, delay_index)
                for token in sensitive_tokens:
                    self.assertGreater(
                        body.index(token),
                        guard_index,
                        f"{token!r} must remain behind the interruption exit",
                    )

    def test_walk_interrupt_reaches_the_script_context_flag(self):
        walk = (
            ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WalkRequest.java"
        ).read_text(encoding="utf-8")
        player = (
            ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
        ).read_text(encoding="utf-8")
        functions = (
            ROOT / "server/src/com/openrsc/server/plugins/Functions.java"
        ).read_text(encoding="utf-8")

        busy_start = walk.index(
            "if (player.isBusy() && player.getMenuHandler() == null)"
        )
        reset_start = walk.index("player.cancelAutoWalk();", busy_start)
        busy_block = walk[busy_start:reset_start]
        self.assertIn("player.interruptPlugins();", busy_block)
        self.assertIn("return;", busy_block)

        interrupt_start = player.index("public void interruptPlugins()")
        interrupt_end = player.index("public boolean checkAttack", interrupt_start)
        self.assertIn(
            "ownedPlugin.getScriptContext().setInterrupted(true);",
            player[interrupt_start:interrupt_end],
        )
        self.assertIn("return scriptContext.getInterrupted();", functions)


if __name__ == "__main__":
    unittest.main()
