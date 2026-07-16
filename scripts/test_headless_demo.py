#!/usr/bin/env python3
"""Fixture-only tests for the disposable headless first-login demo wrapper."""

import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from contextlib import closing
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
SCRIPT = REPO_ROOT / "scripts" / "headless-demo.sh"
FLEET = [
    ("fireee", "Fireee"),
    ("ch0p", "Ch0p"),
    ("ultraz", "Ultraz"),
    ("vinny", "Vinny"),
    ("six-seven", "Six Seven"),
    ("college", "College"),
    ("pknskate", "Pknskate"),
    ("p-h-i-s-h", "P H I S H"),
    ("fulani", "Fulani"),
    ("az", "Az"),
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


class HeadlessDemoTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name) / "repo"
        self.server = self.root / "server"
        self.scripts = self.root / "scripts"
        self.roster_dir = self.root / "tools" / "headless_players"
        self.credentials = Path(self.temporary.name) / "credentials"
        self.command_log = Path(self.temporary.name) / "commands.log"
        (self.server / "inc" / "sqlite").mkdir(parents=True)
        (self.server / "conf" / "server" / "defs").mkdir(parents=True)
        self.scripts.mkdir(parents=True)
        self.roster_dir.mkdir(parents=True)
        self.credentials.mkdir(mode=0o700)

        self.source_config = self.server / "local.conf"
        self.source_config.write_text(
            """database:
\tdb_name: promo_video # source stays untouched
world:
\tserver_name: Voidscape
\tserver_name_welcome: Voidscape
\tserver_port: 43596
\tws_server_port: 43496
\tmax_connections_per_ip: 20
\tmax_players_per_ip: 3
\tavatar_generator: true
\twant_registration_limit: true
\tconnection_limit: 10
\tis_localhost_restricted: false
\tmonitor_online: true
\twant_world_announcements: false
\twant_world_new_player_announcements: true
\tclient_version: 10138 # unrelated value must survive
server:
\twant_packet_register: true
\tright_click_bank: true
\twant_fatigue: false
\twant_bank_notes: true
discord:
\twant_discord_monitoring_updates: true
\twant_discord_global_chat_relay: true
\tdiscord_global_chat_webhook_url: https://example.invalid/fixture-token
\tdiscord_bug_reports_webhook_url: https://example.invalid/fixture-bug-token
""",
            encoding="utf-8",
        )
        (self.server / "connections.conf").write_text(
            "db_type: sqlite\n", encoding="utf-8"
        )
        (self.server / "core.jar").write_bytes(b"fixture only")
        self.snapshot = self.server / "inc" / "sqlite" / "pre-fleet.db"
        with closing(sqlite3.connect(self.snapshot)) as connection:
            with connection:
                connection.execute("PRAGMA journal_mode=WAL")
                connection.execute(
                    "CREATE TABLE players (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    "username TEXT NOT NULL, online INTEGER NOT NULL, "
                    "login_date INTEGER NOT NULL DEFAULT 0)"
                )
                connection.execute(
                    "INSERT INTO players (username, online) VALUES ('wbtest', 0)"
                )
                connection.execute(
                    "CREATE TABLE player_cache (playerID INTEGER NOT NULL, key TEXT NOT NULL)"
                )

        roster = {
            "schema": 1,
            "players": [
                {"slot": slot, "id": profile_id, "username": username}
                for slot, (profile_id, username) in enumerate(FLEET)
            ],
        }
        (self.roster_dir / "roster.json").write_text(
            json.dumps(roster), encoding="utf-8"
        )
        self._write_fixture_scripts()
        self.environment = os.environ.copy()
        self.environment.update(
            {
                "HEADLESS_DEMO_TESTING": "1",
                "HEADLESS_DEMO_REPO_ROOT": str(self.root),
                "HEADLESS_DEMO_SNAPSHOT": str(self.snapshot),
                "HEADLESS_DEMO_EXPECTED_SNAPSHOT_SHA256": sha256(self.snapshot),
                "HEADLESS_CREDENTIAL_DIR": str(self.credentials),
                "HEADLESS_PYTHON": sys.executable,
                "HEADLESS_DEMO_TEST_LOG": str(self.command_log),
            }
        )

    def tearDown(self):
        if hasattr(self, "environment"):
            subprocess.run(
                ["bash", str(SCRIPT), "destroy"],
                env=self.environment,
                capture_output=True,
                text=True,
                timeout=15,
            )
        self.temporary.cleanup()

    def _write_executable(self, name: str, content: str):
        path = self.scripts / name
        path.write_text(content, encoding="utf-8")
        path.chmod(0o755)

    def _write_fixture_scripts(self):
        self._write_executable(
            "run-server.sh",
            """#!/usr/bin/env bash
set -euo pipefail
printf 'server' >>"$HEADLESS_DEMO_TEST_LOG"
printf ' %q' "$@" >>"$HEADLESS_DEMO_TEST_LOG"
printf '\n' >>"$HEADLESS_DEMO_TEST_LOG"
trap 'exit 0' TERM INT
while :; do sleep 0.05; done
""",
        )
        self._write_executable(
            "provision-headless-players.sh",
            """#!/usr/bin/env bash
set -euo pipefail
printf 'provision' >>"$HEADLESS_DEMO_TEST_LOG"
printf ' %q' "$@" >>"$HEADLESS_DEMO_TEST_LOG"
printf '\n' >>"$HEADLESS_DEMO_TEST_LOG"
database=''
roster=''
while [[ $# -gt 0 ]]; do
  case "$1" in
    --database) database="$2"; shift 2 ;;
    --roster) roster="$2"; shift 2 ;;
    *) shift ;;
  esac
done
"$HEADLESS_PYTHON" - "$database" "$roster" <<'PY'
import json
import sqlite3
import sys
with sqlite3.connect(sys.argv[1]) as connection:
    players = json.load(open(sys.argv[2], encoding="utf-8"))["players"]
    connection.executemany(
        "INSERT INTO players (username, online) VALUES (?, 0)",
        [(player["username"],) for player in players],
    )
PY
""",
        )
        self._write_executable(
            "headless-players.sh",
            """#!/usr/bin/env bash
set -euo pipefail
printf 'fleet' >>"$HEADLESS_DEMO_TEST_LOG"
printf ' %q' "$@" >>"$HEADLESS_DEMO_TEST_LOG"
printf '\n' >>"$HEADLESS_DEMO_TEST_LOG"
exit 0
""",
        )

    def run_demo(self, *arguments, check=True):
        result = subprocess.run(
            ["bash", str(SCRIPT), *arguments],
            env=self.environment,
            capture_output=True,
            text=True,
            timeout=30,
        )
        if check and result.returncode != 0:
            self.fail(
                f"headless-demo {' '.join(arguments)} failed ({result.returncode})\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
            )
        return result

    def test_plan_is_non_mutating_and_names_only_isolated_ports(self):
        result = self.run_demo("plan")
        self.assertIn("127.0.0.1:43606", result.stdout)
        self.assertIn("127.0.0.1:43596 and 127.0.0.1:43597", result.stdout)
        self.assertIn("--config headless-demo --explicit-config", result.stdout)
        self.assertFalse((self.root / "run-state").exists())
        self.assertFalse((self.server / "headless-demo.conf").exists())

    def test_prepare_provisions_only_the_snapshot_copy_then_disables_registration(self):
        source_hash = sha256(self.snapshot)
        self.run_demo("prepare")

        self.assertEqual(source_hash, sha256(self.snapshot))
        self.assertFalse(Path(str(self.snapshot) + "-wal").exists())
        self.assertFalse(Path(str(self.snapshot) + "-shm").exists())
        config = (self.server / "headless-demo.conf").read_text(encoding="utf-8")
        for setting in (
            "db_name: headless_demo",
            "server_port: 43606",
            "ws_server_port: 43506",
            "want_packet_register: false",
            "want_world_new_player_announcements: true",
            "monitor_online: false",
            "server_bind_address: 127.0.0.1",
            "want_world_announcements: true",
            "client_version: 10138",
            "want_discord_monitoring_updates: false",
            "want_discord_global_chat_relay: false",
            "discord_global_chat_webhook_url: null",
            "discord_bug_reports_webhook_url: null",
        ):
            self.assertIn(setting, config)
        self.assertNotIn("fixture-token", config)
        self.assertNotIn("fixture-bug-token", config)

        demo_database = self.server / "inc" / "sqlite" / "headless_demo.db"
        with closing(sqlite3.connect(demo_database)) as connection:
            usernames = {
                row[0].casefold()
                for row in connection.execute("SELECT username FROM players")
            }
        self.assertEqual(
            {"wbtest", *(username.casefold() for _, username in FLEET)}, usernames
        )
        with closing(sqlite3.connect(self.snapshot)) as connection:
            self.assertEqual(
                [("wbtest",)], connection.execute("SELECT username FROM players").fetchall()
            )

        commands = self.command_log.read_text(encoding="utf-8")
        self.assertEqual(2, commands.count("server --config headless-demo --explicit-config"))
        self.assertEqual(1, commands.count("provision "))
        self.assertIn("--database", commands)
        self.assertIn("--port 43606", commands)

    def test_fleet_start_uses_demo_state_ports_and_requested_stagger(self):
        self.run_demo("prepare")
        demo_database = self.server / "inc" / "sqlite" / "headless_demo.db"
        with closing(sqlite3.connect(demo_database)) as connection:
            with connection:
                connection.execute(
                    "UPDATE players SET online = 1 WHERE lower(username) = 'wbtest'"
                )
        self.run_demo("fleet-start", "--stagger", "3")
        fleet_line = [
            line
            for line in self.command_log.read_text(encoding="utf-8").splitlines()
            if line.startswith("fleet start ")
        ][-1]
        self.assertIn(
            f"--state-dir {self.root / 'run-state' / 'headless-demo' / 'fleet'}",
            fleet_line,
        )
        self.assertIn("--host 127.0.0.1", fleet_line)
        self.assertIn("--game-port 43606", fleet_line)
        self.assertIn("--control-port-base 19020", fleet_line)
        self.assertIn("--stagger 3", fleet_line)
        self.assertNotIn("run-state/headless-players", fleet_line)
        self.run_demo("fleet-stop")
        second = self.run_demo("fleet-start", "--stagger", "3", check=False)
        self.assertNotEqual(0, second.returncode)
        self.assertIn("already started", second.stderr)
        self.run_demo("reset")
        self.assertFalse(
            (self.root / "run-state" / "headless-demo" / "fleet-started.once").exists()
        )

    def test_fleet_start_requires_online_observer_and_unconsumed_first_logins(self):
        self.run_demo("prepare")
        result = self.run_demo("fleet-start", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("online wbtest observer", result.stderr)

        demo_database = self.server / "inc" / "sqlite" / "headless_demo.db"
        with closing(sqlite3.connect(demo_database)) as connection:
            with connection:
                connection.execute(
                    "UPDATE players SET online = 1 WHERE lower(username) = 'wbtest'"
                )
                connection.execute(
                    "UPDATE players SET login_date = 1 WHERE lower(username) = 'fireee'"
                )
        result = self.run_demo("fleet-start", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("first-login state was already consumed", result.stderr)

    def test_fleet_start_rejects_both_join_announcement_marker_forms(self):
        self.run_demo("prepare")
        demo_database = self.server / "inc" / "sqlite" / "headless_demo.db"
        with closing(sqlite3.connect(demo_database)) as connection:
            with connection:
                connection.execute(
                    "UPDATE players SET online = 1 WHERE lower(username) = 'wbtest'"
                )
                player_id = connection.execute(
                    "SELECT id FROM players WHERE lower(username) = 'fireee'"
                ).fetchone()[0]
                connection.execute(
                    "INSERT INTO player_cache VALUES (0, ?)",
                    (f"new_join_char:{player_id}",),
                )
        result = self.run_demo("fleet-start", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("join-announcement state was already consumed", result.stderr)

        with closing(sqlite3.connect(demo_database)) as connection:
            with connection:
                connection.execute("DELETE FROM player_cache")
                connection.execute(
                    "INSERT INTO player_cache VALUES (?, 'void_announce_new_player')",
                    (player_id,),
                )
        result = self.run_demo("fleet-start", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("join-announcement state was already consumed", result.stderr)

    def test_reset_discards_prior_demo_state_but_preserves_source_snapshot(self):
        source_hash = sha256(self.snapshot)
        self.run_demo("prepare")
        demo_database = self.server / "inc" / "sqlite" / "headless_demo.db"
        with closing(sqlite3.connect(demo_database)) as connection:
            with connection:
                connection.execute(
                    "INSERT INTO players (username, online) VALUES ('contamination', 0)"
                )

        self.run_demo("reset")
        self.assertEqual(source_hash, sha256(self.snapshot))
        with closing(sqlite3.connect(demo_database)) as connection:
            contamination = connection.execute(
                "SELECT COUNT(*) FROM players WHERE username = 'contamination'"
            ).fetchone()[0]
            fleet_count = connection.execute(
                "SELECT COUNT(*) FROM players WHERE lower(username) != 'wbtest'"
            ).fetchone()[0]
        self.assertEqual(0, contamination)
        self.assertEqual(10, fleet_count)

    def test_prepare_refuses_snapshot_that_already_contains_a_fleet_account(self):
        with closing(sqlite3.connect(self.snapshot)) as connection:
            with connection:
                connection.execute(
                    "INSERT INTO players (username, online) VALUES ('Fireee', 0)"
                )
        result = self.run_demo("prepare", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("snapshot already contains", result.stderr)
        self.assertFalse((self.server / "headless-demo.conf").exists())
        self.assertFalse((self.server / "inc" / "sqlite" / "headless_demo.db").exists())

    def test_prepare_refuses_seed_with_sqlite_sidecars(self):
        Path(str(self.snapshot) + "-wal").write_bytes(b"not atomic")
        result = self.run_demo("prepare", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("-wal/-shm sidecars", result.stderr)
        self.assertFalse((self.server / "headless-demo.conf").exists())

    def test_repository_override_is_rejected_outside_fixture_mode(self):
        environment = self.environment.copy()
        environment.pop("HEADLESS_DEMO_TESTING")
        result = subprocess.run(
            ["bash", str(SCRIPT), "plan"],
            env=environment,
            capture_output=True,
            text=True,
            timeout=10,
        )
        self.assertEqual(2, result.returncode)
        self.assertIn("HEADLESS_DEMO_REPO_ROOT is test-only", result.stderr)

    def test_missing_stagger_value_is_a_clean_usage_error(self):
        result = self.run_demo("fleet-start", "--stagger", check=False)
        self.assertEqual(2, result.returncode)
        self.assertIn("--stagger needs a value", result.stderr)
        self.assertNotIn("unbound variable", result.stderr)

    def test_stagger_is_rejected_for_non_start_commands(self):
        result = self.run_demo("prepare", "--stagger", "3", check=False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("valid only with fleet-start", result.stderr)

    def test_mutating_operations_use_a_dedicated_lock(self):
        lock = self.root / "run-state" / "headless-demo" / ".operation.lock"
        lock.mkdir(parents=True)
        (lock / "owner.pid").write_text(f"{os.getpid()}\n", encoding="ascii")
        try:
            result = self.run_demo("fleet-stop", check=False)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("another headless-demo operation is active", result.stderr)
        finally:
            (lock / "owner.pid").unlink()
            lock.rmdir()

    def test_websocket_start_failure_stops_owned_server_before_removing_state(self):
        environment = self.environment.copy()
        environment["HEADLESS_DEMO_TEST_FAIL_WS"] = "1"
        result = subprocess.run(
            ["bash", str(SCRIPT), "prepare"],
            env=environment,
            capture_output=True,
            text=True,
            timeout=15,
        )
        self.assertNotEqual(0, result.returncode)
        state = self.root / "run-state" / "headless-demo"
        self.assertFalse((state / "server.pid").exists())
        self.assertFalse((state / "server-listener.pid").exists())
        self.assertFalse((state / "server-owned.pids").exists())
        self.assertFalse((self.server / "headless-demo.conf").exists())
        self.assertFalse((self.server / "inc" / "sqlite" / "headless_demo.db").exists())


if __name__ == "__main__":
    unittest.main()
