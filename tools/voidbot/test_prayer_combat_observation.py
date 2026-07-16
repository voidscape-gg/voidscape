"""Focused prayer-wire and NPC combat-observation tests for voidbot."""
import contextlib
import io
import os
import struct
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import cli as C
import protocol as P
from voidbotd import COMBAT_EVENT_LIMIT, Daemon, GameState


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.sent = []

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


def npc_updates(*entries):
    return struct.pack(">H", len(entries)) + b"".join(entries)


def npc_entry(server_index, update_type, payload):
    return struct.pack(">HB", server_index, update_type) + payload


class PrayerCommandWireTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_all_server_prayer_ids_use_the_existing_one_byte_packets(self):
        self.assertEqual(15, P.PRAYER_COUNT)
        self.assertEqual("protect_from_magic", P.PRAYER_NAMES[14])
        self.assertEqual(60, P.OUT["PRAYER_ACTIVATED"])
        self.assertEqual(254, P.OUT["PRAYER_DEACTIVATED"])

        enabled = self.bot.handle({"cmd": "prayer-on", "args": {"id": "0"}})
        self.assertEqual({
            "ok": True, "prayer_id": 0, "name": "thick_skin", "active": True,
        }, enabled)
        self.assertEqual(("PRAYER_ACTIVATED", b"\x00"), self.bot.sent[-1])

        disabled = self.bot.handle({"cmd": "prayer-off", "args": {"id": "14"}})
        self.assertEqual({
            "ok": True, "prayer_id": 14,
            "name": "protect_from_magic", "active": False,
        }, disabled)
        self.assertEqual(("PRAYER_DEACTIVATED", b"\x0e"), self.bot.sent[-1])

    def test_invalid_prayer_ids_fail_before_sending(self):
        for value in (None, -1, 15, "magic"):
            result = self.bot.handle({"cmd": "prayer-on", "args": {"id": value}})
            self.assertFalse(result["ok"])
            self.assertTrue(result["error"].startswith("usage:"))
        self.assertEqual([], self.bot.sent)

    def test_cli_maps_the_positional_prayer_id(self):
        sender = mock.Mock(return_value={"ok": True})
        stdout = io.StringIO()
        with mock.patch.object(C, "send", sender), \
                mock.patch.object(sys, "argv", ["cli.py", "prayer-on", "13"]), \
                contextlib.redirect_stdout(stdout), \
                self.assertRaises(SystemExit) as raised:
            C.main()
        self.assertEqual(0, raised.exception.code)
        self.assertEqual(
            {"cmd": "prayer-on", "args": {"id": "13"}},
            sender.call_args.args[0],
        )


class PrayerStateDecodeTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_server_prayer_echo_is_authoritative_state(self):
        states = bytearray(P.PRAYER_COUNT)
        states[10] = 1
        states[14] = 1
        self.bot.decode(206, bytes(states))

        snapshot = self.bot.snapshot("prayers")["prayers"]
        self.assertEqual([10, 14], snapshot["active"])
        self.assertTrue(snapshot["states"][10])
        self.assertTrue(snapshot["states"][14])
        self.assertEqual("prayers", self.bot.st.events[-1]["kind"])

    def test_malformed_prayer_echo_does_not_replace_last_good_state(self):
        self.bot.decode(206, bytes(P.PRAYER_COUNT))
        previous = self.bot.snapshot("prayers")

        self.bot.decode(206, bytes(P.PRAYER_COUNT - 1))
        self.assertEqual(previous, self.bot.snapshot("prayers"))
        self.assertEqual("decode_error", self.bot.st.events[-1]["kind"])

        malformed = bytearray(P.PRAYER_COUNT)
        malformed[3] = 2
        self.bot.decode(206, bytes(malformed))
        self.assertEqual(previous, self.bot.snapshot("prayers"))
        self.assertEqual("decode_error", self.bot.st.events[-1]["kind"])


class NpcCombatObservationTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()
        self.bot.st.npcs = [
            {"server_index": 44, "id": 513, "x": 100, "y": 200},
            {"server_index": 45, "id": 514, "x": 101, "y": 200},
        ]

    def test_damage_projectiles_and_action_bubbles_emit_timed_events(self):
        packet = npc_updates(
            npc_entry(44, 2, bytes((7, 42, 50))),
            npc_entry(44, 10, bytes((3, 39, 50, 1))
                      + struct.pack(">HH", 99, 12)),
            npc_entry(44, 4, struct.pack(">HH", 9, 5)),
            npc_entry(44, 3, struct.pack(">HH", 1, 45)),
            npc_entry(44, 7, struct.pack(">H", 224)),
        )
        with mock.patch("voidbotd.time.time",
                        side_effect=(1000.0, 1000.1, 1000.2, 1000.3, 1000.4)):
            self.bot.decode(104, packet)

        events = self.bot.snapshot("combat-events")["combat_events"]
        self.assertEqual([
            "npc_damage", "npc_damage", "npc_projectile",
            "npc_projectile", "npc_action_bubble",
        ], [event["kind"] for event in events])
        self.assertEqual([1000.0, 1000.1, 1000.2, 1000.3, 1000.4],
                         [event["t"] for event in events])

        legacy, feedback, to_player, to_npc, bubble = events
        self.assertEqual(513, legacy["npc_id"])
        self.assertEqual((7, 42, 50),
                         (legacy["damage"], legacy["cur_hits"], legacy["max_hits"]))
        self.assertIsNone(legacy["attacker_server_index"])
        self.assertEqual((1, 99, 12),
                         (feedback["attacker_type"],
                          feedback["attacker_server_index"],
                          feedback["attacker_max_hit"]))
        self.assertEqual((513, "player", 5, 9),
                         (to_player["source_npc_id"], to_player["target_type"],
                          to_player["target_server_index"],
                          to_player["projectile_id"]))
        self.assertEqual(("npc", 514),
                         (to_npc["target_type"], to_npc["target_npc_id"]))
        self.assertEqual((513, 224), (bubble["npc_id"], bubble["item_id"]))

        global_combat = [event for event in self.bot.st.events
                         if event["kind"].startswith("npc_")]
        self.assertEqual(events, global_combat)

    def test_skull_and_wield_skips_do_not_hide_a_following_bubble_or_chat(self):
        packet = npc_updates(
            npc_entry(44, 5, b"\x02"),
            npc_entry(44, 6, b"\x11\x22"),
            npc_entry(44, 7, struct.pack(">H", 373)),
            npc_entry(44, 1, struct.pack(">h", -1) + b"Ready.\n"),
        )
        self.bot.decode(104, packet)

        self.assertEqual(373, self.bot.st.combat_events[-1]["item_id"])
        self.assertEqual("Ready.", self.bot.st.messages[-1]["text"])
        self.assertFalse(any(event["kind"] == "decode_error"
                             for event in self.bot.st.events))

    def test_combat_state_is_bounded_to_the_documented_window(self):
        for item_id in range(COMBAT_EVENT_LIMIT + 44):
            self.bot.decode(104, npc_updates(
                npc_entry(44, 7, struct.pack(">H", item_id))))

        events = self.bot.snapshot("combat_events")["combat_events"]
        self.assertEqual(COMBAT_EVENT_LIMIT, len(events))
        self.assertEqual(44, events[0]["item_id"])
        self.assertEqual(COMBAT_EVENT_LIMIT + 43, events[-1]["item_id"])

    def test_truncated_combat_update_fails_closed(self):
        self.bot.decode(104, npc_updates(npc_entry(44, 10, b"\x01\x02")))
        self.assertEqual([], self.bot.st.combat_events)
        self.assertEqual("decode_error", self.bot.st.events[-1]["kind"])
        self.assertIn("truncated npc damage", self.bot.st.events[-1]["error"])


if __name__ == "__main__":
    unittest.main()
