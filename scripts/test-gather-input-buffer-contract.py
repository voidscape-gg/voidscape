#!/usr/bin/env python3
"""Static integration checks for VS-083 gathering input buffering."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]


class GatherInputBufferContractTests(unittest.TestCase):
    def test_busy_entity_prewalk_does_not_cancel_but_point_walk_does(self):
        walk = (
            ROOT / "server/src/com/openrsc/server/net/rsc/handlers/WalkRequest.java"
        ).read_text(encoding="utf-8")
        busy_start = walk.index(
            "if (player.isBusy() && player.getMenuHandler() == null)"
        )
        busy_end = walk.index("if (player.inCombat())", busy_start)
        busy_block = walk[busy_start:busy_end]
        self.assertIn("packetOpcode == OpcodeIn.WALK_TO_POINT", busy_block)
        self.assertIn("batch.supportsGatherRepeat()", busy_block)
        self.assertIn("GatherInputPolicy.shouldInterruptBusyWalk", busy_block)
        self.assertIn("player.interruptPlugins();", busy_block)
        self.assertIn("return;", busy_block)

    def test_object_handler_buffers_only_supported_gathering_actions(self):
        action = (
            ROOT / "server/src/com/openrsc/server/net/rsc/handlers/GameObjectAction.java"
        ).read_text(encoding="utf-8")
        self.assertIn("queueGatherRepeat", action)
        self.assertIn("batch.queueGatherRepeat(object, click)", action)
        self.assertIn("batch.isAwaitingGatherObjectCommand()", action)
        self.assertIn("player.interruptPlugins();", action)

    def test_unpaired_entity_prelude_preserves_other_action_cancellation(self):
        manager = (
            ROOT / "server/src/com/openrsc/server/net/rsc/PayloadProcessorManager.java"
        ).read_text(encoding="utf-8")
        self.assertIn("cancelUnpairedGatherEntityPrelude", manager)
        self.assertIn("batch.isAwaitingGatherObjectCommand()", manager)
        self.assertIn("opcode == OpcodeIn.OBJECT_COMMAND", manager)
        self.assertIn("opcode == OpcodeIn.OBJECT_COMMAND2", manager)
        self.assertIn("player.interruptPlugins();", manager)

    def test_batch_binds_consumes_and_clears_one_manual_repeat(self):
        batch = (
            ROOT / "server/src/com/openrsc/server/plugins/Batch.java"
        ).read_text(encoding="utf-8")
        functions = (
            ROOT / "server/src/com/openrsc/server/plugins/Functions.java"
        ).read_text(encoding="utf-8")
        player = (
            ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
        ).read_text(encoding="utf-8")
        context = (
            ROOT / "server/src/com/openrsc/server/model/entity/player/ScriptContext.java"
        ).read_text(encoding="utf-8")

        self.assertIn("GatherRepeatBuffer", batch)
        self.assertIn("resolveAttemptBoundary", batch)
        self.assertIn("boundary.getCurrentProgress()", batch)
        self.assertIn("boundary.getTotalBatch()", batch)
        self.assertIn("batch.bindObjectInteraction", functions)
        self.assertIn("startskillbatch(GameObject gatherTarget", functions)
        self.assertIn("governingSkill == Skill.WOODCUTTING.id()", functions)
        self.assertIn("governingSkill == Skill.FISHING.id()", functions)
        self.assertIn("governingSkill == Skill.MINING.id()", functions)
        self.assertIn("clearGatherRepeat", player)
        self.assertIn("clearGatherRepeat", context)

        plugin_calls = {
            "authentic/skills/woodcutting/Woodcutting.java":
                "startskillbatch(object, Skill.WOODCUTTING.id())",
            "authentic/skills/fishing/Fishing.java":
                "startskillbatch(object, Skill.FISHING.id())",
            "authentic/skills/mining/Mining.java":
                "startskillbatch(rock, Skill.MINING.id())",
            "authentic/skills/mining/GemMining.java":
                "startskillbatch(obj, Skill.MINING.id())",
        }
        for relative, call in plugin_calls.items():
            source = (
                ROOT / "server/plugins/com/openrsc/server/plugins" / relative
            ).read_text(encoding="utf-8")
            self.assertIn(call, source)

    def test_cancel_batch_handler_clears_the_buffer_via_interruption(self):
        handler = (
            ROOT
            / "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java"
        ).read_text(encoding="utf-8")
        cancel_start = handler.index("case CANCEL_BATCH:")
        cancel_end = handler.index("break;", cancel_start)
        self.assertIn("player.interruptPlugins();", handler[cancel_start:cancel_end])

        player = (
            ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
        ).read_text(encoding="utf-8")
        interrupt_start = player.index("public void interruptPlugins()")
        interrupt_end = player.index("public boolean checkAttack", interrupt_start)
        self.assertIn("activeBatch.clearGatherRepeat();", player[interrupt_start:interrupt_end])

    def test_all_mining_opening_delays_honor_explicit_cancellation(self):
        for relative in ("Mining.java", "GemMining.java"):
            source = (
                ROOT
                / "server/plugins/com/openrsc/server/plugins/authentic/skills/mining"
                / relative
            ).read_text(encoding="utf-8")
            delay = source.index("delay(3);", source.index("private void batchMining"))
            guard = source.index("if (ifinterrupted())", delay)
            self.assertLess(guard - delay, 100)

    def test_voidbot_object_actions_use_entity_prewalk(self):
        daemon = (ROOT / "tools/voidbot/voidbotd.py").read_text(encoding="utf-8")
        action_start = daemon.index('if cmd == "object-action":')
        action_end = daemon.index('if cmd == "cast-object":', action_start)
        action_block = daemon[action_start:action_end]
        self.assertEqual(2, action_block.count('self.send("WALK_TO_ENTITY"'))
        self.assertNotIn('self.send("WALK_TO_POINT"', action_block)

        walk_start = daemon.index('if cmd == "walk-step":')
        walk_end = daemon.index('if cmd == "npc-talk":', walk_start)
        self.assertIn('self.send("WALK_TO_POINT"', daemon[walk_start:walk_end])


if __name__ == "__main__":
    unittest.main()
