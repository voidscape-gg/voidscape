"""Focused wire and CLI tests for ordinary shop selling and closing."""
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


class ShopCommandWireTests(unittest.TestCase):
    def setUp(self):
        self.bot = FakeDaemon()

    def test_opcodes_and_exact_payloads(self):
        self.assertEqual(221, P.OUT["SHOP_SELL"])
        self.assertEqual(166, P.OUT["SHOP_CLOSE"])

        result = self.bot.handle({
            "cmd": "shop-sell",
            "args": {"id": "526", "stock": "17", "amount": "3"},
        })
        self.assertEqual({"ok": True, "id": 526, "stock": 17, "amount": 3}, result)
        self.assertEqual(("SHOP_SELL", bytes.fromhex("020e 0011 0003")), self.bot.sent[-1])

        result = self.bot.handle({"cmd": "shop-close", "args": {}})
        self.assertEqual({"ok": True}, result)
        self.assertEqual(("SHOP_CLOSE", b""), self.bot.sent[-1])

    def test_invalid_sell_arguments_fail_before_sending(self):
        invalid = (
            {"id": "526", "stock": "17"},
            {"id": "item", "stock": "17", "amount": "1"},
            {"id": "-1", "stock": "17", "amount": "1"},
            {"id": "526", "stock": "65536", "amount": "1"},
            {"id": "526", "stock": "17", "amount": "0"},
            {"id": "526", "stock": "17", "amount": "65536"},
        )
        for args in invalid:
            with self.subTest(args=args):
                result = self.bot.handle({"cmd": "shop-sell", "args": args})
                self.assertFalse(result["ok"])
                self.assertTrue(result["error"].startswith("usage:"))
        self.assertEqual([], self.bot.sent)


class ShopCliSugarTests(unittest.TestCase):
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

    def test_sell_positionals_and_close(self):
        self.assertEqual(
            {"cmd": "shop-sell", "args": {
                "id": "526", "stock": "17", "amount": "3",
            }},
            self._request_for("shop-sell", "526", "17", "3"),
        )
        self.assertEqual(
            {"cmd": "shop-close", "args": {}},
            self._request_for("shop-close"),
        )


if __name__ == "__main__":
    unittest.main()
