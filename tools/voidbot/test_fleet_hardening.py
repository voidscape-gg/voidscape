"""Focused tests for voidbot fleet credentials, onboarding state, and lifecycle."""
import contextlib
import io
import json
import os
from pathlib import Path
import signal
import stat
import subprocess
import sys
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest import mock

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import protocol as P
from voidbotd import (Daemon, GameState, install_signal_handlers, parse_args,
                      read_password_file)


class RecordingConnection:
    def __init__(self, recv_error=None):
        self.recv_error = recv_error
        self.sent = []
        self.closed = False

    def send_packet(self, opcode, payload=b""):
        self.sent.append((opcode, bytes(payload)))

    def recv_packet(self):
        if self.recv_error:
            raise self.recv_error
        raise AssertionError("recv_packet should not have been called")

    def close(self):
        self.closed = True


def daemon_with_connection(connection=None):
    bot = Daemon(SimpleNamespace(defs=None))
    bot.conn = connection
    with bot.st.lock:
        bot.st.connected = connection is not None
        bot.st.logged_in = connection is not None
    return bot


class RecordingCommandDaemon(Daemon):
    def __init__(self):
        self.st = GameState()
        self.sent = []

    def send(self, name, payload=b""):
        self.sent.append((name, bytes(payload)))


class PasswordFileTests(unittest.TestCase):
    def test_password_file_is_one_non_empty_line(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "account.password")
            path.write_text("fleet-secret-42\n", encoding="utf-8")
            path.chmod(0o600)
            self.assertEqual("fleet-secret-42", read_password_file(path))

            path.write_text("first\nsecond\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "exactly one non-empty line"):
                read_password_file(path)

            path.write_text("fleet-secret-42\n", encoding="utf-8")
            path.chmod(0o640)
            with self.assertRaisesRegex(ValueError, "group/other permissions"):
                read_password_file(path)

    def test_systemd_read_only_credential_view_is_accepted_without_weakening_files(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "player-password")
            path.write_text("fleet-secret-42\n", encoding="utf-8")
            path.chmod(0o640)
            file_metadata = SimpleNamespace(
                st_mode=stat.S_IFREG | 0o440, st_uid=0, st_gid=0,
            )
            directory_metadata = SimpleNamespace(
                st_mode=stat.S_IFDIR | 0o550, st_uid=0, st_gid=0,
            )
            filesystem = SimpleNamespace(f_flag=os.ST_RDONLY)
            with (
                mock.patch.dict(os.environ, {"CREDENTIALS_DIRECTORY": tmp}),
                mock.patch("voidbotd.os.fstat", return_value=file_metadata),
                mock.patch("voidbotd.os.stat", return_value=directory_metadata),
                mock.patch("voidbotd.os.statvfs", return_value=filesystem),
            ):
                self.assertEqual("fleet-secret-42", read_password_file(path))

            # Merely naming an ordinary directory as CREDENTIALS_DIRECTORY is not
            # enough; without systemd's read-only mount the same mode still fails.
            with mock.patch.dict(os.environ, {"CREDENTIALS_DIRECTORY": tmp}):
                with self.assertRaisesRegex(ValueError, "group/other permissions"):
                    read_password_file(path)

    def test_daemon_parser_accepts_file_and_rejects_two_credential_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp, "account.password")
            secret = "secret-not-for-errors"
            path.write_text(secret + "\n", encoding="utf-8")
            path.chmod(0o600)
            args = parse_args(["--user", "worker", "--password-file", str(path)])
            self.assertEqual(secret, args.password)

            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr), self.assertRaises(SystemExit) as raised:
                parse_args(["--user", "worker", "--pass", secret,
                            "--password-file", str(path)])
            self.assertEqual(2, raised.exception.code)
            self.assertNotIn(secret, stderr.getvalue())

    def test_register_wrapper_passes_only_the_secret_file_path(self):
        wrapper = Path(__file__).with_name("voidbot")
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            password_path = tmp_path / "account.password"
            secret = "never-place-this-in-argv"
            password_path.write_text(secret + "\n", encoding="utf-8")
            password_path.chmod(0o600)
            fake_python = tmp_path / "fake-python"
            fake_python.write_text(
                "#!%s\nimport json, sys\nprint(json.dumps({'argv': sys.argv[1:]}))\n"
                % sys.executable,
                encoding="utf-8",
            )
            fake_python.chmod(0o700)
            env = dict(os.environ, VOIDBOT_PYTHON=str(fake_python))
            result = subprocess.run(
                [str(wrapper), "register", "--user", "worker",
                 "--password-file", str(password_path), "--no-email"],
                cwd=wrapper.parents[2], env=env, capture_output=True, text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertNotIn(secret, result.stdout + result.stderr)
            forwarded = json.loads(result.stdout)["argv"]
            self.assertIn("--password-file", forwarded)
            self.assertIn(str(password_path), forwarded)
            self.assertNotIn("--pass", forwarded)

    def test_successful_login_discards_plaintext_password_before_login_event(self):
        args = SimpleNamespace(
            host="127.0.0.1", game_port=43596, user="worker",
            password="discard-after-auth", password_file="/run/credentials/password",
            defs=None,
        )
        config_connection = RecordingConnection()
        config_connection.recv_packet = mock.Mock(return_value=(19, b"rsa"))
        game_connection = RecordingConnection()
        game_connection.recv_byte = mock.Mock(return_value=0x40)
        bot = Daemon(args)

        def record_login_event(kind, **fields):
            self.assertEqual("login", kind)
            self.assertIsNone(args.password)
            return {"kind": kind, **fields}

        bot.st.event = record_login_event
        with mock.patch("voidbotd.P.Connection",
                        side_effect=[config_connection, game_connection]), \
             mock.patch.object(bot, "_parse_rsa", return_value=(65537, 99991)), \
             mock.patch("voidbotd.P.build_login", return_value=b"\x00") as build_login:
            bot.connect_and_login()

        self.assertIsNone(args.password)
        self.assertIs(bot.conn, game_connection)
        build_login.assert_called_once()


class AppearanceStateTests(unittest.TestCase):
    def setUp(self):
        self.bot = RecordingCommandDaemon()

    def test_inbound_screen_packet_sets_snapshot_and_wait_state(self):
        self.bot.decode(59, b"")
        self.assertEqual({"appearance": {"open": True}},
                         self.bot.snapshot("appearance"))
        result = self.bot.wait({"condition": "appearance-open", "timeout": 0.1})
        self.assertTrue(result["matched"])
        self.assertEqual({"open": True}, result["appearance"])

    def test_design_submission_closes_local_appearance_state(self):
        self.bot.decode(59, b"")
        result = self.bot.handle({"cmd": "design-character", "args": {
            "gender": "female", "head": 1, "body": 5, "hair_colour": 14,
            "top_colour": 7, "trouser_colour": 11, "skin_colour": 1,
            "country": "none",
        }})
        self.assertTrue(result["ok"])
        self.assertFalse(self.bot.st.appearance_open)
        self.assertEqual("PLAYER_APPEARANCE_CHANGE", self.bot.sent[-1][0])


class RecentNpcStateTests(unittest.TestCase):
    def setUp(self):
        self.bot = RecordingCommandDaemon()

    def test_state_exposes_recent_npcs_for_only_the_existing_three_second_horizon(self):
        now = time.time()
        with self.bot.st.lock:
            self.bot.st.npcs = []
            self.bot.st.npc_seen = {
                41: {"id": 70, "x": 392, "y": 690, "t": now},
            }

        snapshot = self.bot.snapshot("all")
        self.assertEqual([], snapshot["npcs"])
        self.assertEqual(
            [{"server_index": 41, "id": 70, "x": 392, "y": 690}],
            snapshot["recent_npcs"],
        )
        self.assertEqual(
            {"recent_npcs": snapshot["recent_npcs"]},
            self.bot.snapshot("recent-npcs"),
        )

        with self.bot.st.lock:
            self.bot.st.npc_seen[41]["t"] = now - 3.1
        self.assertEqual({"recent_npcs": []}, self.bot.snapshot("recent-npcs"))


class WorldWalkRouteStateTests(unittest.TestCase):
    def setUp(self):
        self.bot = RecordingCommandDaemon()

    def test_each_route_response_gets_a_monotonic_state_sequence(self):
        self.bot.decode(100, bytes([0, 6, 0, 0]))
        rejected = self.bot.snapshot("world-walk-route")["world_walk_route"]
        self.assertEqual((1, False, 6), (
            rejected["seq"], rejected["ok"], rejected["reason"]
        ))
        self.assertIsInstance(rejected["t"], float)

        payload = (
            bytes([1, 0, 0, 1])
            + (140).to_bytes(2, "big")
            + (640).to_bytes(2, "big")
        )
        self.bot.decode(100, payload)
        accepted = self.bot.snapshot("world-walk-route")["world_walk_route"]
        self.assertEqual(2, accepted["seq"])
        self.assertTrue(accepted["ok"])
        self.assertEqual([{"x": 140, "y": 640}], accepted["route"])
        self.assertEqual(2, self.bot.st.events[-1]["route_seq"])


class FleetLifecycleTests(unittest.TestCase):
    def test_socket_disconnect_stops_with_failure_status(self):
        connection = RecordingConnection(ConnectionError("server vanished"))
        bot = daemon_with_connection(connection)
        bot.recv_loop()
        self.assertFalse(bot.running)
        self.assertEqual(1, bot.exit_code)
        self.assertFalse(bot.st.connected)
        self.assertTrue(connection.closed)

    def test_server_close_packet_stops_with_failure_status(self):
        connection = RecordingConnection()
        bot = daemon_with_connection(connection)
        bot.decode(165, b"")
        self.assertFalse(bot.running)
        self.assertEqual(1, bot.exit_code)
        self.assertFalse(bot.st.logged_in)
        self.assertTrue(connection.closed)
        self.assertEqual("server_close", bot.st.events[-1]["kind"])

    def test_intentional_shutdown_sends_logout_then_closes_successfully(self):
        connection = RecordingConnection()
        bot = daemon_with_connection(connection)
        bot.request_shutdown("test")
        deadline = time.time() + 1
        while bot.running and time.time() < deadline:
            time.sleep(0.01)
        self.assertFalse(bot.running)
        self.assertEqual(0, bot.exit_code)
        self.assertEqual([(P.OUT["LOGOUT"], b"")], connection.sent)
        self.assertTrue(connection.closed)

    def test_sigterm_and_sigint_use_graceful_shutdown_path(self):
        daemon = mock.Mock()
        handlers = {}

        def remember(sig, handler):
            handlers[sig] = handler

        with mock.patch("voidbotd.signal.signal", side_effect=remember):
            install_signal_handlers(daemon)
        handlers[signal.SIGTERM](signal.SIGTERM, None)
        handlers[signal.SIGINT](signal.SIGINT, None)
        self.assertEqual(
            [mock.call("SIGTERM"), mock.call("SIGINT")],
            daemon.request_shutdown.call_args_list,
        )


if __name__ == "__main__":
    unittest.main()
