"""Focused wire and NPC-frame tests for PK Catching Simulator QA."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import protocol as P
from voidbotd import Daemon, GameState


def packed_bits(*fields):
    """Pack (value, width) pairs MSB-first like PacketBuilder.writeBits."""
    bit_count = sum(width for _, width in fields)
    payload = bytearray((bit_count + 7) // 8)
    bit_position = 0
    for value, width in fields:
        value &= (1 << width) - 1
        for shift in range(width - 1, -1, -1):
            if value & (1 << shift):
                payload[bit_position >> 3] |= 1 << (7 - (bit_position & 7))
            bit_position += 1
    return bytes(payload)


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.st.x = 140
        self.st.y = 640
        self.sent = []

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


class PkCatchingVoidbotTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()
        # New NPC 321/804 at bot-relative (+3, -2), absolute (143, 638).
        self.bot.decode(79, packed_bits(
            (0, 8),
            (321, 12), (3, 6), (-2, 6), (4, 4), (804, 10),
        ))

    def test_attack_npc_sends_stock_entity_prewalk_then_attack(self):
        result = self.bot.handle({
            "cmd": "attack-npc", "args": {"server_index": "321"},
        })

        self.assertTrue(result["ok"])
        self.assertEqual(
            [
                ("WALK_TO_ENTITY", P.BitWriter().u16(143).u16(638).b),
                ("NPC_ATTACK1", P.BitWriter().u16(321).b),
            ],
            self.bot.sent,
        )

    def test_npc_frames_distinguish_two_stationary_ticks_from_resume(self):
        self.bot.st.events.clear()

        # Two retained, unchanged frames followed by direction 3 (+1, +1).
        self.bot.decode(79, packed_bits((1, 8), (0, 1)))
        self.bot.decode(79, packed_bits((1, 8), (0, 1)))
        self.bot.decode(79, packed_bits(
            (1, 8), (1, 1), (0, 1), (3, 3),
        ))

        frames = [event for event in self.bot.st.events
                  if event["kind"] == "npc_frame"]
        self.assertEqual(3, len(frames))
        self.assertEqual([1, 1, 1], [frame["count"] for frame in frames])
        self.assertEqual([[], [], []], [frame["removed"] for frame in frames])
        self.assertEqual(
            [
                [],
                [],
                [{
                    "server_index": 321,
                    "id": 804,
                    "from_x": 143,
                    "from_y": 638,
                    "x": 144,
                    "y": 639,
                }],
            ],
            [frame["moved"] for frame in frames],
        )
        npc = self.bot.snapshot("npcs")["npcs"][0]
        self.assertEqual((144, 639), (npc["x"], npc["y"]))


if __name__ == "__main__":
    unittest.main()
