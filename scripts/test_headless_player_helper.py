#!/usr/bin/env python3
"""Focused tests for headless-player credential and service invariants."""

import importlib.util
import json
import os
import socket
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
HELPER_PATH = REPO_ROOT / "scripts" / "headless-player-helper.py"
SPEC = importlib.util.spec_from_file_location("headless_player_helper", HELPER_PATH)
HELPER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HELPER)


class HeadlessPlayerHelperTest(unittest.TestCase):
    def test_runtime_state_directory_is_absolute_dedicated_and_isolated(self):
        roster = REPO_ROOT / "tools" / "headless_players" / "roster.json"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            credentials = directory / "credentials"
            state = directory / "runtime" / "fleet"

            self.assertEqual(
                state.resolve(),
                HELPER.check_runtime_state_directory(state, credentials, roster),
            )
            with self.assertRaisesRegex(HELPER.HelperError, "absolute path"):
                HELPER.check_runtime_state_directory(
                    Path("relative/runtime"), credentials, roster,
                )
            with self.assertRaisesRegex(HELPER.HelperError, "credential directory"):
                HELPER.check_runtime_state_directory(
                    credentials / "runtime", credentials, roster,
                )
            with self.assertRaisesRegex(HELPER.HelperError, "credential directory"):
                HELPER.check_runtime_state_directory(
                    directory, directory / "nested-credentials", roster,
                )

            state.mkdir(parents=True)
            (state / "unexpected.txt").write_text("not supervisor state\n", encoding="utf-8")
            with self.assertRaisesRegex(HELPER.HelperError, "not dedicated"):
                HELPER.check_runtime_state_directory(state, credentials, roster)

    def test_runtime_state_directory_rejects_source_paths_files_and_symlinks(self):
        roster = REPO_ROOT / "tools" / "headless_players" / "roster.json"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            credentials = directory / "credentials"
            state_file = directory / "state-file"
            state_file.write_text("not a directory\n", encoding="utf-8")
            with self.assertRaisesRegex(HELPER.HelperError, "must be a directory"):
                HELPER.check_runtime_state_directory(state_file, credentials, roster)

            state_target = directory / "state-target"
            state_target.mkdir()
            state_link = directory / "state-link"
            state_link.symlink_to(state_target, target_is_directory=True)
            with self.assertRaisesRegex(HELPER.HelperError, "must not be a symlink"):
                HELPER.check_runtime_state_directory(state_link, credentials, roster)

        for unsafe in (
            REPO_ROOT,
            REPO_ROOT / "scripts" / "headless-runtime",
            REPO_ROOT / "run-state",
            roster.parent,
        ):
            with self.subTest(path=unsafe):
                with self.assertRaises(HELPER.HelperError):
                    HELPER.check_runtime_state_directory(
                        unsafe, "/etc/voidscape/headless-players", roster,
                    )

    def test_supervisor_custom_state_status_and_stop_are_isolated_and_idempotent(self):
        supervisor = REPO_ROOT / "scripts" / "headless-players.sh"
        default_state = REPO_ROOT / "run-state" / "headless-players"
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            state = directory / "demo" / "fleet-state"
            credentials = directory / "credentials"
            environment = dict(os.environ, HEADLESS_PYTHON=sys.executable)
            command = [
                str(supervisor),
                "status",
                "--state-dir", str(state),
                "--credentials-dir", str(credentials),
            ]
            status = subprocess.run(
                command,
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            self.assertEqual(1, status.returncode, status.stdout + status.stderr)
            self.assertIn("Fleet: 0/11 processes running", status.stdout)
            self.assertTrue((state / "pids").is_dir())
            self.assertTrue((state / "logs").is_dir())
            self.assertFalse((state / ".supervisor.lock").exists())
            self.assertNotEqual(default_state.resolve(), state.resolve())

            stop = subprocess.run(
                [*command[:1], "stop", *command[2:]],
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            self.assertEqual(0, stop.returncode, stop.stdout + stop.stderr)
            self.assertIn("Headless fleet stopped", stop.stdout)
            self.assertFalse((state / ".supervisor.lock").exists())

    def test_supervisor_state_option_rejects_missing_and_relative_values(self):
        supervisor = REPO_ROOT / "scripts" / "headless-players.sh"
        environment = dict(os.environ, HEADLESS_PYTHON=sys.executable)
        missing = subprocess.run(
            [str(supervisor), "status", "--state-dir"],
            cwd=REPO_ROOT,
            env=environment,
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
        self.assertEqual(2, missing.returncode)
        self.assertIn("requires an absolute directory", missing.stderr)
        self.assertNotIn("unbound variable", missing.stderr)

        with tempfile.TemporaryDirectory() as temporary:
            relative = subprocess.run(
                [str(supervisor), "status", "--state-dir", "relative-state"],
                cwd=temporary,
                env=environment,
                capture_output=True,
                text=True,
                timeout=10,
                check=False,
            )
            self.assertEqual(2, relative.returncode)
            self.assertIn("must be an absolute path", relative.stderr)
            self.assertFalse((Path(temporary) / "relative-state").exists())

        help_result = subprocess.run(
            [str(supervisor), "--help"],
            cwd=REPO_ROOT,
            env=environment,
            capture_output=True,
            text=True,
            timeout=10,
            check=True,
        )
        self.assertIn("--state-dir DIR", help_result.stdout)
        self.assertIn("default run-state/headless-players", help_result.stdout)

    def test_receipt_rejects_replaced_password(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            password = directory / "six-seven.password"
            receipt = directory / "six-seven.provisioned"
            HELPER.generate_password_file(password)
            HELPER.write_receipt(receipt, "six-seven", "Six Seven", password)
            self.assertTrue(HELPER.receipt_matches(receipt, "six-seven", "Six Seven", password))

            password.unlink()
            HELPER.generate_password_file(password)
            with self.assertRaisesRegex(HELPER.HelperError, "does not match roster"):
                HELPER.receipt_matches(receipt, "six-seven", "Six Seven", password)

    def test_control_port_collision_is_rejected(self):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
            listener.bind(("127.0.0.1", 0))
            port = listener.getsockname()[1]
            with self.assertRaisesRegex(HELPER.HelperError, "control port.*unavailable"):
                HELPER.check_control_ports("127.0.0.1", port, 1)

    def test_registration_config_rejects_blocked_loopback_combination(self):
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "local.conf"
            config.write_text(
                "want_packet_register: true\n"
                "want_registration_limit: true\n"
                "is_localhost_restricted: true\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(HELPER.HelperError, "temporarily set want_registration_limit"):
                HELPER.check_registration_config(config)

            config.write_text(
                "want_packet_register: true\n"
                "want_registration_limit: false\n"
                "is_localhost_restricted: true\n",
                encoding="utf-8",
            )
            self.assertEqual(config.resolve(), HELPER.check_registration_config(config))

    def test_registration_config_requires_packet_registration(self):
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "local.conf"
            config.write_text("want_packet_register: false\n", encoding="utf-8")
            with self.assertRaisesRegex(HELPER.HelperError, "want_packet_register: true"):
                HELPER.check_registration_config(config)

    def test_fleet_runtime_config_requires_wire_and_skilling_contract(self):
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "local.conf"
            valid = (
                "right_click_bank: true\n"
                "want_fatigue: false\n"
                "want_bank_notes: true\n"
            )
            config.write_text(
                valid.replace("right_click_bank: true", "right_click_bank: false"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(HELPER.HelperError, "right_click_bank: true"):
                HELPER.check_fleet_runtime_config(config)
            config.write_text(
                valid.replace("want_fatigue: false", "want_fatigue: true"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(HELPER.HelperError, "want_fatigue: false"):
                HELPER.check_fleet_runtime_config(config)
            config.write_text(
                valid.replace("want_bank_notes: true", "want_bank_notes: false"),
                encoding="utf-8",
            )
            with self.assertRaisesRegex(HELPER.HelperError, "want_bank_notes: true"):
                HELPER.check_fleet_runtime_config(config)
            config.write_text(valid, encoding="utf-8")
            self.assertEqual(config.resolve(), HELPER.check_fleet_runtime_config(config))

    def test_sqlite_credential_verification_is_offline_and_fail_closed(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            connections = directory / "connections.conf"
            server_config = directory / "local.conf"
            database = directory / "fleet.db"
            password_file = directory / "six-seven.password"
            classpath = REPO_ROOT / "server" / "core.jar"
            password = "OfflineProof42"
            connections.write_text("db_type: sqlite\n", encoding="utf-8")
            server_config.write_text("db_name: fleet\n", encoding="utf-8")
            password_file.write_text(password + "\n", encoding="ascii")
            password_file.chmod(0o600)

            hashed = subprocess.run(
                ["java", "-cp", str(classpath),
                 "com.openrsc.server.util.rsc.PortalPasswordHasher"],
                input=HELPER._password_helper_input("hash", password, "fleet-salt"),
                text=True,
                capture_output=True,
                check=True,
            ).stdout.strip()
            with sqlite3.connect(database) as connection:
                connection.execute(
                    "CREATE TABLE players (username TEXT, pass TEXT, salt TEXT, online INTEGER)"
                )
                connection.execute(
                    "INSERT INTO players VALUES (?, ?, ?, 0)",
                    ("Six Seven", hashed, "fleet-salt"),
                )

            self.assertEqual(
                database.resolve(),
                HELPER.check_sqlite_credential_store(
                    connections, server_config, classpath, database,
                ),
            )
            self.assertEqual(
                "verified",
                HELPER.sqlite_credential_status(
                    connections, server_config, "Six Seven", password_file,
                    classpath, database,
                ),
            )
            with sqlite3.connect(database) as connection:
                connection.execute("UPDATE players SET online = 1")
            self.assertEqual(
                "online",
                HELPER.sqlite_credential_status(
                    connections, server_config, "Six Seven", password_file,
                    classpath, database,
                ),
            )
            with sqlite3.connect(database) as connection:
                connection.execute("UPDATE players SET online = 0, pass = 'not-the-password'")
            self.assertEqual(
                "mismatch",
                HELPER.sqlite_credential_status(
                    connections, server_config, "Six Seven", password_file,
                    classpath, database,
                ),
            )

    def test_roster_backup_gate_requires_exactly_one_offline_row_per_profile(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            connections = directory / "connections.conf"
            server_config = directory / "local.conf"
            database = directory / "fleet.db"
            roster = REPO_ROOT / "tools" / "headless_players" / "roster.json"
            connections.write_text("db_type: sqlite\n", encoding="utf-8")
            server_config.write_text("db_name: fleet\n", encoding="utf-8")
            players = HELPER.load_roster(roster)
            with sqlite3.connect(database) as connection:
                connection.execute("CREATE TABLE players (username TEXT, online INTEGER)")
                connection.executemany(
                    "INSERT INTO players VALUES (?, 0)",
                    [(player["username"],) for player in players],
                )

            self.assertEqual(
                database.resolve(),
                HELPER.check_sqlite_roster_offline(
                    roster, connections, server_config, database,
                ),
            )
            with sqlite3.connect(database) as connection:
                connection.execute(
                    "UPDATE players SET online = 1 WHERE lower(username) = lower(?)",
                    ("Ultraz",),
                )
            with self.assertRaisesRegex(HELPER.HelperError, "Ultraz is online"):
                HELPER.check_sqlite_roster_offline(
                    roster, connections, server_config, database,
                )

            with sqlite3.connect(database) as connection:
                connection.execute("DELETE FROM players WHERE lower(username) = lower(?)", ("Az",))
            with self.assertRaisesRegex(HELPER.HelperError, "Az has 0 rows"):
                HELPER.check_sqlite_roster_offline(
                    roster, connections, server_config, database,
                )

    def test_systemd_instances_match_roster_and_credentials(self):
        players = HELPER.load_roster(REPO_ROOT / "tools" / "headless_players" / "roster.json")
        expected_units = {
            "Wants=voidscape-headless@%s.service" % player["id"] for player in players
        }
        target_lines = set(
            (REPO_ROOT / "Deployment_Scripts" / "systemd" / "voidscape-headless.target")
            .read_text(encoding="utf-8")
            .splitlines()
        )
        self.assertEqual(expected_units, {line for line in target_lines if line.startswith("Wants=voidscape-headless@")})

        player_unit = (
            REPO_ROOT / "Deployment_Scripts" / "systemd" / "voidscape-headless@.service"
        ).read_text(encoding="utf-8")
        controller_unit = (
            REPO_ROOT / "Deployment_Scripts" / "systemd" / "voidscape-headless-controller.service"
        ).read_text(encoding="utf-8")
        self.assertIn("LoadCredential=player-password:/etc/voidscape/headless-players/%i.password", player_unit)
        self.assertIn("ExecStart=/opt/voidscape/scripts/run-headless-player.sh %i --password-file %d/player-password", player_unit)
        self.assertIn("PartOf=voidscape-headless.target", player_unit)
        self.assertIn("PartOf=voidscape-headless.target", controller_unit)
        self.assertIn("check-fleet-runtime-config", player_unit)
        self.assertIn("check-fleet-runtime-config", controller_unit)
        self.assertIn("Environment=VOIDBOT_WANT_BANK_NOTES=1", player_unit)
        self.assertIn("Environment=VOIDBOT_CLIENT_VERSION=10139", player_unit)
        self.assertIn("EnvironmentFile=-/etc/voidscape/headless.env", player_unit)
        self.assertIn("EnvironmentFile=-/etc/voidscape/headless.env", controller_unit)
        self.assertIn("--path ${HEADLESS_SERVER_CONFIG}", player_unit)
        self.assertIn("--path ${HEADLESS_SERVER_CONFIG}", controller_unit)
        self.assertNotIn("voidscape.service", player_unit)
        self.assertNotIn("voidscape.service", controller_unit)
        self.assertNotIn("voidscape.service", "\n".join(target_lines))
        self.assertIn("User=voidscape-headless-%i", player_unit)
        self.assertNotIn("\nUser=voidscape\n", player_unit)
        self.assertIn("LimitCORE=0", player_unit)
        self.assertIn("LimitCORE=0", controller_unit)

    def test_launch_staging_package_contains_fleet_contract(self):
        package = (REPO_ROOT / "scripts" / "package-launch-staging.sh").read_text(
            encoding="utf-8"
        )
        launch_contract = json.loads(
            (REPO_ROOT / "scripts" / "launch-config-contract.json").read_text(
                encoding="utf-8"
            )
        )
        self.assertEqual("true", launch_contract["right_click_bank"])
        self.assertEqual("false", launch_contract["want_fatigue"])
        self.assertEqual("true", launch_contract["want_bank_notes"])
        self.assertIn("scripts/generate-launch-server-config.py", package)
        self.assertIn("server/launch-config-contract.json", package)
        for packaged_path in (
            '$OUTPUT_DIR/server/conf/server/defs',
            '$OUTPUT_DIR/scripts/$name',
            '$OUTPUT_DIR/tools/voidbot/$name',
            '$OUTPUT_DIR/tools/headless_players/$name',
            '$OUTPUT_DIR/Deployment_Scripts/systemd/$name',
            '$OUTPUT_DIR/Deployment_Scripts/systemd/voidscape-headless.env',
        ):
            self.assertIn(packaged_path, package)
        for contract in (
            "HEADLESS_GAME_PORT=$GAME_PORT",
            "VOIDBOT_CLIENT_VERSION=$VERSION",
            "headless_env_sha256=",
            "HEADLESS_DATABASE=/opt/voidscape/server/inc/sqlite/voidscape.db",
            "HEADLESS_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/portal-password-helper.jar",
            "check-sqlite-roster-offline",
            "install -d -o root -g voidscape -m 0710 /etc/voidscape",
            "grep -Ec '^headless-players/[^/]+[.](password|provisioned)\\$'",
            "-eq 20",
        ):
            self.assertIn(contract, package)

    def test_provision_recovers_response_two_only_by_offline_credential_proof(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            credentials = directory / "credentials"
            credentials.mkdir(mode=0o700)
            server_config = directory / "local.conf"
            connections = directory / "connections.conf"
            database = directory / "fleet.db"
            fake_log = directory / "fake-voidbot.jsonl"
            fake_voidbot = directory / "fake-voidbot"
            server_config.write_text(
                "db_name: fleet\n"
                "want_packet_register: true\n"
                "want_registration_limit: false\n"
                "is_localhost_restricted: true\n"
                "right_click_bank: true\n"
                "want_fatigue: false\n"
                "want_bank_notes: true\n",
                encoding="utf-8",
            )
            connections.write_text("db_type: sqlite\n", encoding="utf-8")
            fake_voidbot.write_text(
                "#!%s\n"
                "import json, os, sys\n"
                "with open(os.environ['FAKE_VOIDBOT_LOG'], 'a', encoding='utf-8') as handle:\n"
                "    handle.write(json.dumps(sys.argv[1:]) + '\\n')\n"
                "print(json.dumps({'ok': False, 'response': 2, 'detail': 'username already taken'}))\n"
                "raise SystemExit(1)\n" % sys.executable,
                encoding="utf-8",
            )
            fake_voidbot.chmod(0o700)

            passwords = []
            players = HELPER.load_roster(
                REPO_ROOT / "tools" / "headless_players" / "roster.json"
            )
            with sqlite3.connect(database) as connection:
                connection.execute(
                    "CREATE TABLE players (username TEXT, pass TEXT, salt TEXT, online INTEGER)"
                )
                for player in players:
                    password_file = credentials / (player["id"] + ".password")
                    HELPER.generate_password_file(password_file)
                    password = HELPER.read_password(password_file)
                    passwords.append(password)
                    # A legacy unsalted row still goes through the canonical Java
                    # check path; the focused test above covers the bcrypt branch.
                    connection.execute(
                        "INSERT INTO players VALUES (?, ?, '', 0)",
                        (player["username"], password),
                    )

            environment = dict(
                os.environ,
                HEADLESS_PYTHON=sys.executable,
                HEADLESS_VOIDBOT=str(fake_voidbot),
                FAKE_VOIDBOT_LOG=str(fake_log),
            )
            provision_command = [
                str(REPO_ROOT / "scripts" / "provision-headless-players.sh"),
                "--credentials-dir", str(credentials),
                "--config", str(server_config),
                "--connections-config", str(connections),
                "--database", str(database),
                "--password-helper", str(REPO_ROOT / "server" / "core.jar"),
                "--port", "1",
                "--stagger", "0.5",
            ]
            result = subprocess.run(
                provision_command,
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            calls = [json.loads(line) for line in fake_log.read_text(encoding="utf-8").splitlines()]
            self.assertEqual(10, len(calls))
            self.assertTrue(all(call and call[0] == "register" for call in calls))
            combined = result.stdout + result.stderr + fake_log.read_text(encoding="utf-8")
            for password in passwords:
                self.assertNotIn(password, combined)
            for player in players:
                receipt = credentials / (player["id"] + ".provisioned")
                self.assertTrue(receipt.is_file())
                self.assertEqual(0, receipt.stat().st_mode & 0o077)

            # Existing receipts must still prove against the active database;
            # they are not blind "already provisioned" markers.
            second = subprocess.run(
                provision_command,
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            self.assertEqual(0, second.returncode, second.stdout + second.stderr)
            self.assertEqual(
                10,
                len(fake_log.read_text(encoding="utf-8").splitlines()),
                "verified receipts must not send another registration packet",
            )

            with sqlite3.connect(database) as connection:
                connection.execute("UPDATE players SET online = 1 WHERE username = 'Fireee'")
            online = subprocess.run(
                provision_command,
                cwd=REPO_ROOT,
                env=environment,
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
            self.assertNotEqual(0, online.returncode)
            self.assertIn("marked online", online.stderr)
            self.assertEqual(10, len(fake_log.read_text(encoding="utf-8").splitlines()))


if __name__ == "__main__":
    unittest.main()
