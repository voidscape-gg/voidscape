"""Focused wire/state tests for observer-side native social acceptance."""
import contextlib
import io
import os
import sys
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


class SocialCommandWireTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_custom_protocol_opcodes_and_payloads(self):
        self.assertEqual(165, P.OUT["PLAYER_FOLLOW"])
        self.assertEqual(195, P.OUT["SOCIAL_ADD_FRIEND"])

        result = self.bot.handle({
            "cmd": "follow-player", "args": {"server_index": "321"},
        })
        self.assertEqual({"ok": True, "server_index": 321}, result)
        self.assertEqual(("PLAYER_FOLLOW", bytes.fromhex("0141")), self.bot.sent[-1])

        result = self.bot.handle({
            "cmd": "friend-add", "args": {"name": "P H I S H"},
        })
        self.assertEqual({"ok": True, "name": "P H I S H"}, result)
        self.assertEqual(("SOCIAL_ADD_FRIEND", b"P H I S H\n"), self.bot.sent[-1])

    def test_invalid_arguments_fail_before_sending(self):
        result = self.bot.handle({
            "cmd": "follow-player", "args": {"server_index": "65536"},
        })
        self.assertFalse(result["ok"])
        self.assertTrue(result["error"].startswith("usage:"))

        result = self.bot.handle({"cmd": "friend-add", "args": {"name": "  "}})
        self.assertFalse(result["ok"])
        self.assertTrue(result["error"].startswith("usage:"))
        self.assertEqual([], self.bot.sent)


class FriendUpdateStateTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_update_populates_snapshot_and_friend_wait(self):
        self.bot.decode(149, b"Fireee\nOld Fire\n\x06Voidscape\n")
        expected = {
            "name": "Fireee", "former_name": "Old Fire", "online": True,
            "same_world": True, "status": 6, "world": "Voidscape",
        }
        self.assertEqual({"friends": [expected]}, self.bot.snapshot("friends"))

        result = self.bot.wait({
            "condition": "friend-present", "name": "fireee", "timeout": 0.1,
        })
        self.assertTrue(result["matched"])
        self.assertEqual([expected], result["friends"])

        # Repeated status updates replace rather than duplicate the row.
        self.bot.decode(149, b"Fireee\nOld Fire\n\x00")
        friends = self.bot.snapshot("friends")["friends"]
        self.assertEqual(1, len(friends))
        self.assertFalse(friends[0]["online"])
        self.assertIsNone(friends[0]["world"])

    def test_rename_and_malformed_update_follow_client_semantics(self):
        self.bot.decode(149, b"Fireee\n\n\x00")
        self.bot.decode(149, b"Flare\nFireee\n\x01")
        self.assertEqual("Flare", self.bot.snapshot("friends")["friends"][0]["name"])

        before = list(self.bot.st.friends)
        self.bot.decode(149, b"unterminated")
        self.assertEqual(before, self.bot.st.friends)
        self.assertEqual("decode_error", self.bot.st.events[-1]["kind"])


class SocialCliSugarTests(unittest.TestCase):
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

    def test_follow_and_spaced_friend_name_positionals(self):
        self.assertEqual(
            {"cmd": "follow-player", "args": {"server_index": "321"}},
            self._request_for("follow-player", "321"),
        )
        self.assertEqual(
            {"cmd": "friend-add", "args": {"name": "P H I S H"}},
            self._request_for("friend-add", "P", "H", "I", "S", "H"),
        )


if __name__ == "__main__":
    unittest.main()
