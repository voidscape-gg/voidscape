"""Focused wire tests for VS-083 gathering input and cancellation packets."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import protocol as P
from voidbotd import Daemon, GameState


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.st.x = 140
        self.st.y = 640
        self.object_defs = {1: {"width": 1, "height": 1}}
        self.sent = []

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


class GatherInputPacketTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_explicit_object_prewalk_uses_entity_packet(self):
        result = self.bot.handle({
            "cmd": "object-action",
            "args": {"x": "138", "y": "639", "which": "1",
                     "walk_x": "139", "walk_y": "639"},
        })
        self.assertTrue(result["ok"])
        self.assertEqual(
            [
                ("WALK_TO_ENTITY", P.BitWriter().u16(139).u16(639).b),
                ("OBJECT_COMMAND1", P.BitWriter().u16(138).u16(639).b),
            ],
            self.bot.sent,
        )

    def test_inferred_object_prewalk_uses_entity_packet(self):
        result = self.bot.handle({
            "cmd": "object-action",
            "args": {"x": "138", "y": "639", "which": "2", "id": "1"},
        })
        self.assertTrue(result["ok"])
        self.assertEqual("WALK_TO_ENTITY", self.bot.sent[0][0])
        self.assertEqual("OBJECT_COMMAND2", self.bot.sent[1][0])

    def test_walk_step_remains_explicit_point_cancellation(self):
        result = self.bot.handle({
            "cmd": "walk-step", "args": {"x": "139", "y": "639"},
        })
        self.assertTrue(result["ok"])
        self.assertEqual(
            [("WALK_TO_POINT", P.BitWriter().u16(139).u16(639).b)],
            self.bot.sent,
        )


if __name__ == "__main__":
    unittest.main()
