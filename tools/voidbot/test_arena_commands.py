"""Focused wire/state tests for voidbot's existing Void Arena protocol."""
import contextlib
import io
import os
import sys
import time
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import cli as C
import protocol as P
from voidbotd import Daemon, GameState


class FakeDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.sent = []

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


def arena_message(payload):
    # SEND_MESSAGE: icon:i32, type:u8, info:u8, message:LF string.
    return b"\x00" * 6 + (P.ARENA_CONTROL_PREFIX + payload).encode("ascii") + b"\n"


class ArenaCommandWireTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_existing_interface_option_contract(self):
        self.assertEqual(199, P.OUT["INTERFACE_OPTIONS"])
        self.assertEqual(18, P.ARENA_INTERFACE_OPTION)

        result = self.bot.handle({
            "cmd": "arena-challenge", "args": {"server_index": "321"},
        })
        self.assertEqual({"ok": True, "server_index": 321}, result)
        self.assertEqual(
            ("INTERFACE_OPTIONS", bytes.fromhex("12 00 01 0141")),
            self.bot.sent[-1],
        )

    def test_rules_and_setup_actions_use_stock_five_byte_payload(self):
        self.bot.decode(131, arena_message(
            "setup|321|Rival|1|0|0|0|0|0|1"))

        result = self.bot.handle({
            "cmd": "arena-rules",
            "args": {"f2p_only": True, "prayer": True, "magic": True},
        })
        self.assertTrue(result["ok"])
        self.assertEqual(0x16, result["rules"]["mask"])
        self.assertEqual(
            ("INTERFACE_OPTIONS", bytes.fromhex("12 02 16 0141")),
            self.bot.sent[-1],
        )

        for command, action in (("arena-accept", 3), ("arena-confirm", 4),
                                ("arena-decline", 1)):
            self.bot.handle({"cmd": command, "args": {}})
            self.assertEqual(
                ("INTERFACE_OPTIONS", bytes([18, action, 1, 1, 65])),
                self.bot.sent[-1],
            )

    def test_ranked_rules_canonicalize_other_toggles(self):
        self.bot.decode(131, arena_message(
            "setup|7|Rival|0|0|0|0|0|0|1"))
        result = self.bot.handle({
            "cmd": "arena-rules",
            "args": {"ranked": True, "f2p_only": False, "prayer": False},
        })
        self.assertEqual(1, result["rules"]["mask"])
        self.assertTrue(result["rules"]["f2p_only"])
        self.assertEqual(bytes.fromhex("12 02 01 0007"), self.bot.sent[-1][1])

    def test_missing_or_invalid_target_fails_before_send(self):
        for args in ({}, {"server_index": 65536}, {"server_index": "nope"}):
            result = self.bot.handle({"cmd": "arena-challenge", "args": args})
            self.assertFalse(result["ok"])
            self.assertTrue(result["error"].startswith("usage:"))
        self.assertEqual([], self.bot.sent)


class ArenaStateTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_setup_confirm_countdown_start_and_end(self):
        self.bot.decode(131, arena_message(
            "setup|321|Rival|1|0|1|0|0|0|1"))
        arena = self.bot.snapshot("arena")["arena"]
        self.assertEqual("setup", arena["phase"])
        self.assertEqual(321, arena["opponent_server_index"])
        self.assertTrue(arena["rules"]["ranked"])
        self.assertTrue(arena["opponent_accepted"])
        self.assertEqual([], self.bot.st.messages)  # hidden client control
        self.assertTrue(self.bot.wait({
            "condition": "arena-setup", "timeout": 0.1,
        })["matched"])

        self.bot.decode(131, arena_message(
            "setup|321|Rival|1|1|1|1|0|1|1"))
        self.assertEqual("confirm", self.bot.snapshot("arena")["arena"]["phase"])
        self.assertTrue(self.bot.wait({
            "condition": "arena-confirm", "timeout": 0.1,
        })["matched"])

        # The server closes setup immediately before sending countdown; this must
        # not create a false arena-ended match observation.
        self.bot.decode(131, arena_message("close"))
        self.assertEqual("closed", self.bot.snapshot("arena")["arena"]["phase"])
        self.bot.decode(131, arena_message("countdown|5"))
        self.assertEqual("countdown", self.bot.snapshot("arena")["arena"]["phase"])

        with self.bot.st.lock:
            self.bot.st.arena["countdown_end"] = time.time() - 1
        started = self.bot.wait({"condition": "arena-started", "timeout": 0.1})
        self.assertTrue(started["matched"])
        self.assertEqual("started", started["arena"]["phase"])

        self.bot.decode(131, arena_message("close"))
        ended = self.bot.wait({"condition": "arena-ended", "timeout": 0.1})
        self.assertTrue(ended["matched"])
        self.assertEqual("ended", ended["arena"]["phase"])

    def test_clear_and_malformed_controls_fail_safely(self):
        self.bot.decode(131, arena_message("setup|bad|Rival|1|0|0|0|0|0|1"))
        self.assertEqual("none", self.bot.st.arena["phase"])
        self.assertEqual("decode_error", self.bot.st.events[-1]["kind"])

        self.bot.decode(131, arena_message(
            "setup|7|Rival|0|0|0|0|0|0|1"))
        self.bot.decode(131, arena_message("clear"))
        self.assertEqual("none", self.bot.snapshot("arena")["arena"]["phase"])


class ArenaCliSugarTests(unittest.TestCase):
    def _request_for(self, *argv):
        sender = mock.Mock(return_value={"ok": True})
        stdout = io.StringIO()
        with mock.patch.object(C, "send", sender), \
                mock.patch.object(sys, "argv", ["cli.py", *argv]), \
                contextlib.redirect_stdout(stdout), \
                self.assertRaises(SystemExit) as raised:
            C.main()
        self.assertEqual(0, raised.exception.code)
        return sender.call_args.args[0]

    def test_challenge_and_decline_positionals(self):
        self.assertEqual(
            {"cmd": "arena-challenge", "args": {"server_index": "321"}},
            self._request_for("arena-challenge", "321"),
        )
        self.assertEqual(
            {"cmd": "arena-decline", "args": {"server_index": "321"}},
            self._request_for("arena-decline", "321"),
        )


if __name__ == "__main__":
    unittest.main()
