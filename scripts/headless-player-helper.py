#!/usr/bin/env python3
"""Small, secret-safe helper for the headless-player operations scripts.

The tracked roster contains no credentials. Passwords are read from files and
are never accepted on this helper's command line.
"""

import argparse
import base64
import hashlib
import json
import os
import re
import secrets
import socket
import sqlite3
import stat
import string
import subprocess
import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent
PROFILE_ID_RE = re.compile(r"^[a-z0-9][a-z0-9-]*$")
USERNAME_RE = re.compile(r"^[A-Za-z0-9 ]{1,12}$")
PASSWORD_ALPHABET = string.ascii_letters + string.digits
PASSWORD_LENGTH = 20
EXPECTED_IDS_BY_SLOT = (
    "fireee",
    "ch0p",
    "ultraz",
    "vinny",
    "six-seven",
    "college",
    "pknskate",
    "p-h-i-s-h",
    "fulani",
    "az",
)
BOOLEAN_CONFIG_RE = re.compile(
    r"^\s*(want_packet_register|want_registration_limit|is_localhost_restricted)\s*:\s*(true|false)\b",
    re.IGNORECASE,
)
CONFIG_VALUE_RE = re.compile(r"^\s*([a-z_]+)\s*:\s*([^#]*?)\s*(?:#.*)?$", re.IGNORECASE)


class HelperError(RuntimeError):
    pass


def load_roster(path):
    roster_path = Path(path).resolve()
    try:
        payload = json.loads(roster_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise HelperError("cannot read roster %s: %s" % (roster_path, exc))

    if not isinstance(payload, dict) or payload.get("schema") != 1:
        raise HelperError("roster must be an object with schema: 1")
    raw_players = payload.get("players")
    if not isinstance(raw_players, list) or not raw_players:
        raise HelperError("roster must contain a non-empty players array")

    players = []
    ids = set()
    usernames = set()
    slots = set()
    for index, raw in enumerate(raw_players):
        if not isinstance(raw, dict):
            raise HelperError("roster player %d must be an object" % (index + 1))
        profile_id = raw.get("id")
        username = raw.get("username")
        slot = raw.get("slot")
        if not isinstance(profile_id, str) or not PROFILE_ID_RE.fullmatch(profile_id):
            raise HelperError("roster player %d has an invalid id" % (index + 1))
        if not isinstance(username, str) or not USERNAME_RE.fullmatch(username):
            raise HelperError("roster player %d has an invalid username" % (index + 1))
        if not isinstance(slot, int) or isinstance(slot, bool) or slot < 0 or slot > 9:
            raise HelperError("roster player %d slot must be an integer from 0 to 9" % (index + 1))
        username_key = " ".join(username.lower().split())
        if profile_id in ids:
            raise HelperError("duplicate roster id: %s" % profile_id)
        if username_key in usernames:
            raise HelperError("duplicate roster username: %s" % username)
        if slot in slots:
            raise HelperError("duplicate roster slot: %d" % slot)
        ids.add(profile_id)
        usernames.add(username_key)
        slots.add(slot)
        players.append({"id": profile_id, "username": username, "slot": slot})

    if len(players) != 10 or slots != set(range(10)):
        raise HelperError("headless roster must contain exactly slots 0 through 9")
    players = sorted(players, key=lambda player: player["slot"])
    actual_ids = tuple(player["id"] for player in players)
    if actual_ids != EXPECTED_IDS_BY_SLOT:
        raise HelperError("roster ids/slots do not match the ten systemd fleet instances")
    return players


def find_player(roster, profile_id):
    for player in roster:
        if player["id"] == profile_id:
            return player
    raise HelperError("unknown headless-player id: %s" % profile_id)


def ensure_secret_file(path):
    secret_path = Path(path)
    try:
        info = secret_path.stat()
    except OSError as exc:
        raise HelperError("cannot stat credential file %s: %s" % (secret_path, exc))
    if not stat.S_ISREG(info.st_mode):
        raise HelperError("credential path is not a regular file: %s" % secret_path)
    if info.st_mode & 0o077:
        raise HelperError("credential file must not be accessible by group or other: %s" % secret_path)


def read_password(path):
    ensure_secret_file(path)
    try:
        password = Path(path).read_text(encoding="ascii").strip()
    except (OSError, UnicodeError) as exc:
        raise HelperError("cannot read credential file %s: %s" % (path, exc))
    if len(password) < 4 or len(password) > 20 or any(char.isspace() for char in password):
        raise HelperError("credential file must contain one 4-20 character password")
    return password


def generate_password_file(path):
    target = Path(path)
    target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    password = "".join(secrets.choice(PASSWORD_ALPHABET) for _ in range(PASSWORD_LENGTH))
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
    try:
        descriptor = os.open(str(target), flags, 0o600)
    except FileExistsError:
        ensure_secret_file(target)
        return False
    try:
        with os.fdopen(descriptor, "w", encoding="ascii") as handle:
            handle.write(password + "\n")
            handle.flush()
            os.fsync(handle.fileno())
    except Exception:
        try:
            target.unlink()
        except OSError:
            pass
        raise
    return True


def check_credentials_directory(path):
    target = Path(path).resolve()
    try:
        target.relative_to(REPO_ROOT)
    except ValueError:
        pass
    else:
        raise HelperError("credential directory must be outside the repository")
    target.mkdir(mode=0o700, parents=True, exist_ok=True)
    info = target.stat()
    if not stat.S_ISDIR(info.st_mode):
        raise HelperError("credential path is not a directory: %s" % target)
    if info.st_uid != os.geteuid():
        raise HelperError("credential directory must be owned by the invoking user: %s" % target)
    if info.st_mode & 0o077:
        raise HelperError("credential directory must have mode 0700 or stricter: %s" % target)
    return target


def _path_contains(parent, child):
    try:
        child.relative_to(parent)
    except ValueError:
        return False
    return True


def _check_runtime_entry(path, expected_type):
    try:
        info = path.lstat()
    except OSError as exc:
        raise HelperError("cannot inspect runtime state entry %s: %s" % (path, exc))
    if stat.S_ISLNK(info.st_mode):
        raise HelperError("runtime state must not contain symlinks: %s" % path)
    if info.st_uid != os.geteuid():
        raise HelperError("runtime state must be owned by the invoking user: %s" % path)
    if expected_type == "directory" and not stat.S_ISDIR(info.st_mode):
        raise HelperError("runtime state entry must be a directory: %s" % path)
    if expected_type == "file" and not stat.S_ISREG(info.st_mode):
        raise HelperError("runtime state entry must be a regular file: %s" % path)


def check_runtime_state_directory(path, credential_dir, roster):
    raw_target = Path(path)
    if not raw_target.is_absolute():
        raise HelperError("runtime state directory must be an absolute path")
    if raw_target.is_symlink():
        raise HelperError("runtime state directory must not be a symlink: %s" % raw_target)

    target = raw_target.resolve()
    repository = REPO_ROOT.resolve()
    repository_runtime = (repository / "run-state").resolve()
    credential_path = Path(credential_dir).resolve()
    roster_path = Path(roster).resolve()
    players = load_roster(roster_path)

    if target.parent == target:
        raise HelperError("runtime state directory cannot be the filesystem root")
    if target == repository_runtime:
        raise HelperError("runtime state directory must be a dedicated child of %s" % repository_runtime)
    if _path_contains(repository, target) and not _path_contains(repository_runtime, target):
        raise HelperError("runtime state inside the repository must live under %s" % repository_runtime)
    if _path_contains(target, repository):
        raise HelperError("runtime state directory must not overlap the repository root")
    for protected, label in (
        (credential_path, "credential directory"),
        (roster_path, "roster"),
    ):
        if _path_contains(target, protected) or _path_contains(protected, target):
            raise HelperError("runtime state directory must not overlap the %s" % label)

    if not target.exists():
        return target
    _check_runtime_entry(target, "directory")

    allowed_runtime_files = {
        "pids": {"controller.pid"} | {player["id"] + ".pid" for player in players},
        "logs": {"controller.log"} | {player["id"] + ".log" for player in players},
        ".supervisor.lock": {"owner.pid"},
    }
    for entry in target.iterdir():
        if entry.name.startswith("roster."):
            _check_runtime_entry(entry, "file")
            continue
        expected_names = allowed_runtime_files.get(entry.name)
        if expected_names is None:
            raise HelperError("runtime state directory is not dedicated: unexpected %s" % entry)
        _check_runtime_entry(entry, "directory")
        for child in entry.iterdir():
            if child.name not in expected_names:
                raise HelperError("runtime state directory has an unexpected entry: %s" % child)
            _check_runtime_entry(child, "file")
    return target


def check_control_ports(host, base, count=10):
    if not isinstance(base, int) or not isinstance(count, int) or count < 1:
        raise HelperError("control port base/count must be integers")
    if base < 1 or base + count - 1 > 65535:
        raise HelperError("control port range must be within 1..65535")

    reservations = []
    conflicts = []
    try:
        for port in range(base, base + count):
            listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            try:
                listener.bind((host, port))
            except OSError as exc:
                listener.close()
                conflicts.append("%s:%d (%s)" % (host, port, exc))
            else:
                reservations.append(listener)
    finally:
        for listener in reservations:
            listener.close()
    if conflicts:
        raise HelperError("control port(s) unavailable: %s" % ", ".join(conflicts))


def check_registration_config(path):
    config_path = Path(path).resolve()
    try:
        lines = config_path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise HelperError("cannot read active server config %s: %s" % (config_path, exc))

    values = {}
    for line in lines:
        match = BOOLEAN_CONFIG_RE.match(line)
        if not match:
            continue
        key = match.group(1).lower()
        value = match.group(2).lower() == "true"
        if key in values:
            raise HelperError(
                "active server config has duplicate %s keys: %s" % (key, config_path)
            )
        values[key] = value

    # These match ServerConfiguration defaults, but packet registration must be
    # explicitly enabled for this maintenance operation.
    packet_registration = values.get("want_packet_register", False)
    registration_limit = values.get("want_registration_limit", False)
    localhost_restricted = values.get("is_localhost_restricted", True)
    if not packet_registration:
        raise HelperError(
            "packet registration is disabled in %s. Maintenance window: set "
            "want_packet_register: true, keep the registration endpoint loopback-only, "
            "restart the server, provision, then restore the original setting and restart."
            % config_path
        )
    if registration_limit and localhost_restricted:
        raise HelperError(
            "loopback registration is blocked by want_registration_limit: true plus "
            "is_localhost_restricted: true in %s. Maintenance window: temporarily set "
            "want_registration_limit: false (preferred), keep the registration endpoint "
            "loopback-only, restart the server, provision, then restore the original "
            "setting and restart." % config_path
        )
    return config_path


def read_config_value(path, key, default=None):
    config_path = Path(path).resolve()
    try:
        lines = config_path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise HelperError("cannot read config %s: %s" % (config_path, exc))
    values = []
    for line in lines:
        match = CONFIG_VALUE_RE.match(line)
        if match and match.group(1).lower() == key.lower():
            values.append(match.group(2).strip())
    if len(values) > 1:
        raise HelperError("config has duplicate %s keys: %s" % (key, config_path))
    if not values:
        return default
    return values[0]


def check_fleet_runtime_config(path):
    config_path = Path(path).resolve()
    required = (
        ("right_click_bank", "true", "banker command 1"),
        ("want_fatigue", "false", "unattended skilling"),
        ("want_bank_notes", "true", "voidbot packet decoding"),
    )
    for key, expected, reason in required:
        value = str(read_config_value(config_path, key, "")).lower()
        if value not in ("true", "false"):
            raise HelperError("%s must be explicitly true or false in %s" % (key, config_path))
        if value != expected:
            raise HelperError(
                "headless fleet %s requires %s: %s in %s; "
                "set it in the active world config and restart the server before starting the fleet"
                % (reason, key, expected, config_path)
            )
    return config_path


def _password_helper_input(mode, password, salt, encoded_hash=""):
    def encode(value):
        return base64.urlsafe_b64encode(value.encode("utf-8")).decode("ascii")

    return "\n".join((mode, encode(password), encode(salt), encode(encoded_hash))) + "\n"


def check_canonical_password(password, salt, encoded_hash, classpath, java="java"):
    helper_classpath = Path(classpath).resolve()
    if not helper_classpath.is_file():
        raise HelperError("password helper classpath not found: %s" % helper_classpath)
    command = [java, "-cp", str(helper_classpath),
               "com.openrsc.server.util.rsc.PortalPasswordHasher"]
    try:
        result = subprocess.run(
            command,
            input=_password_helper_input("check", password, salt, encoded_hash),
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as exc:
        raise HelperError("canonical password helper unavailable: %s" % exc)
    output = result.stdout.strip()
    if result.returncode != 0 or output not in ("true", "false"):
        raise HelperError("canonical password helper failed closed")
    return output == "true"


def resolve_sqlite_database(connections_config, server_config, database=None):
    db_type = str(read_config_value(connections_config, "db_type", "sqlite")).lower()
    if db_type != "sqlite":
        raise HelperError(
            "offline credential recovery supports SQLite only (active db_type is %s); "
            "refusing any pre-onboarding login" % db_type
        )
    db_name = str(read_config_value(server_config, "db_name", "preservation"))
    if not re.fullmatch(r"[A-Za-z0-9_-]+", db_name):
        raise HelperError("active db_name is unsafe or unsupported: %s" % db_name)
    database_path = Path(database).resolve() if database else (
        REPO_ROOT / "server" / "inc" / "sqlite" / (db_name + ".db")
    ).resolve()
    if not database_path.is_file():
        raise HelperError("active SQLite database not found: %s" % database_path)
    return database_path


def check_sqlite_credential_store(
    connections_config,
    server_config,
    classpath,
    database=None,
    java="java",
):
    database_path = resolve_sqlite_database(connections_config, server_config, database)
    try:
        connection = sqlite3.connect(database_path.as_uri() + "?mode=ro", uri=True, timeout=5)
        try:
            connection.execute(
                "SELECT pass, COALESCE(salt, ''), COALESCE(online, 0) FROM players LIMIT 0"
            ).fetchall()
        finally:
            connection.close()
    except sqlite3.Error as exc:
        raise HelperError("active SQLite player table unavailable: %s" % exc)
    if not check_canonical_password("preflight", "", "preflight", classpath, java):
        raise HelperError("canonical password helper failed its preflight vector")
    return database_path


def check_sqlite_roster_offline(
    roster,
    connections_config,
    server_config,
    database=None,
):
    """Require one offline ordinary account row for every configured profile."""
    players = load_roster(roster)
    database_path = resolve_sqlite_database(connections_config, server_config, database)
    try:
        connection = sqlite3.connect(database_path.as_uri() + "?mode=ro", uri=True, timeout=5)
        try:
            failures = []
            for player in players:
                rows = connection.execute(
                    "SELECT COALESCE(online, 0) FROM players WHERE lower(username) = lower(?)",
                    (player["username"],),
                ).fetchall()
                if len(rows) != 1:
                    failures.append("%s has %d rows" % (player["username"], len(rows)))
                elif int(rows[0][0] or 0) != 0:
                    failures.append("%s is online" % player["username"])
        finally:
            connection.close()
    except sqlite3.Error as exc:
        raise HelperError("cannot verify offline fleet rows: %s" % exc)
    if failures:
        raise HelperError("fleet database snapshot is not offline-safe: %s" % "; ".join(failures))
    return database_path


def sqlite_credential_status(
    connections_config,
    server_config,
    username,
    password_file,
    classpath,
    database=None,
    java="java",
):
    database_path = resolve_sqlite_database(connections_config, server_config, database)
    if not isinstance(username, str) or not USERNAME_RE.fullmatch(username):
        raise HelperError("invalid username for offline credential verification")

    try:
        connection = sqlite3.connect(database_path.as_uri() + "?mode=ro", uri=True, timeout=5)
        try:
            rows = connection.execute(
                "SELECT pass, COALESCE(salt, ''), COALESCE(online, 0) "
                "FROM players WHERE lower(username) = lower(?)",
                (username,),
            ).fetchall()
        finally:
            connection.close()
    except sqlite3.Error as exc:
        raise HelperError("cannot read active SQLite player account: %s" % exc)
    if not rows:
        return "missing"
    if len(rows) != 1:
        raise HelperError("active SQLite database has duplicate canonical username: %s" % username)
    encoded_hash, salt, online = rows[0]
    if int(online or 0) != 0:
        return "online"
    password = read_password(password_file)
    if check_canonical_password(password, str(salt or ""), str(encoded_hash or ""), classpath, java):
        return "verified"
    return "mismatch"


def password_fingerprint(path):
    password = read_password(path)
    return hashlib.sha256(password.encode("ascii")).hexdigest()


def receipt_matches(path, profile_id, username, password_file):
    receipt = Path(path)
    if not receipt.exists():
        return False
    ensure_secret_file(receipt)
    try:
        payload = json.loads(receipt.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise HelperError("invalid provisioning receipt %s: %s" % (receipt, exc))
    expected = {
        "id": profile_id,
        "password_sha256": password_fingerprint(password_file),
        "username": username,
    }
    if payload != expected:
        raise HelperError("provisioning receipt does not match roster: %s" % receipt)
    return True


def write_receipt(path, profile_id, username, password_file):
    target = Path(path)
    payload = {
        "id": profile_id,
        "password_sha256": password_fingerprint(password_file),
        "username": username,
    }
    data = (json.dumps(payload, sort_keys=True) + "\n").encode("utf-8")
    descriptor = os.open(str(target), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as handle:
        handle.write(data)
        handle.flush()
        os.fsync(handle.fileno())


def build_parser():
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    roster = subparsers.add_parser("roster-list")
    roster.add_argument("--roster", required=True)

    lookup = subparsers.add_parser("roster-lookup")
    lookup.add_argument("--roster", required=True)
    lookup.add_argument("--id", required=True)

    check_dir = subparsers.add_parser("check-credential-dir")
    check_dir.add_argument("--path", required=True)

    state_dir = subparsers.add_parser("check-runtime-state-dir")
    state_dir.add_argument("--path", required=True)
    state_dir.add_argument("--credentials-dir", required=True)
    state_dir.add_argument("--roster", required=True)

    check_ports = subparsers.add_parser("check-control-ports")
    check_ports.add_argument("--host", required=True)
    check_ports.add_argument("--base", required=True, type=int)
    check_ports.add_argument("--count", default=10, type=int)

    check_config = subparsers.add_parser("check-registration-config")
    check_config.add_argument("--path", required=True)

    runtime_config = subparsers.add_parser("check-fleet-runtime-config")
    runtime_config.add_argument("--path", required=True)

    credential_status = subparsers.add_parser("sqlite-credential-status")
    credential_status.add_argument("--connections-config", required=True)
    credential_status.add_argument("--server-config", required=True)
    credential_status.add_argument("--username", required=True)
    credential_status.add_argument("--password-file", required=True)
    credential_status.add_argument("--classpath", required=True)
    credential_status.add_argument("--database")
    credential_status.add_argument("--java", default="java")

    credential_store = subparsers.add_parser("check-sqlite-credential-store")
    credential_store.add_argument("--connections-config", required=True)
    credential_store.add_argument("--server-config", required=True)
    credential_store.add_argument("--classpath", required=True)
    credential_store.add_argument("--database")
    credential_store.add_argument("--java", default="java")

    roster_offline = subparsers.add_parser("check-sqlite-roster-offline")
    roster_offline.add_argument("--roster", required=True)
    roster_offline.add_argument("--connections-config", required=True)
    roster_offline.add_argument("--server-config", required=True)
    roster_offline.add_argument("--database")

    generate = subparsers.add_parser("generate-password")
    generate.add_argument("--path", required=True)

    receipt_check = subparsers.add_parser("receipt-check")
    receipt_check.add_argument("--path", required=True)
    receipt_check.add_argument("--id", required=True)
    receipt_check.add_argument("--username", required=True)
    receipt_check.add_argument("--password-file", required=True)

    receipt_write = subparsers.add_parser("receipt-write")
    receipt_write.add_argument("--path", required=True)
    receipt_write.add_argument("--id", required=True)
    receipt_write.add_argument("--username", required=True)
    receipt_write.add_argument("--password-file", required=True)

    return parser


def main():
    args = build_parser().parse_args()
    if args.command == "roster-list":
        for player in load_roster(args.roster):
            print("%d\t%s\t%s" % (player["slot"], player["id"], player["username"]))
        return 0
    if args.command == "roster-lookup":
        player = find_player(load_roster(args.roster), args.id)
        print("%d\t%s\t%s" % (player["slot"], player["id"], player["username"]))
        return 0
    if args.command == "check-credential-dir":
        print(check_credentials_directory(args.path))
        return 0
    if args.command == "check-runtime-state-dir":
        print(check_runtime_state_directory(
            args.path,
            args.credentials_dir,
            args.roster,
        ))
        return 0
    if args.command == "check-control-ports":
        check_control_ports(args.host, args.base, args.count)
        return 0
    if args.command == "check-registration-config":
        print(check_registration_config(args.path))
        return 0
    if args.command == "check-fleet-runtime-config":
        print(check_fleet_runtime_config(args.path))
        return 0
    if args.command == "sqlite-credential-status":
        status = sqlite_credential_status(
            args.connections_config,
            args.server_config,
            args.username,
            args.password_file,
            args.classpath,
            args.database,
            args.java,
        )
        print(status)
        return {"verified": 0, "missing": 3, "online": 4, "mismatch": 5}[status]
    if args.command == "check-sqlite-credential-store":
        print(check_sqlite_credential_store(
            args.connections_config,
            args.server_config,
            args.classpath,
            args.database,
            args.java,
        ))
        return 0
    if args.command == "check-sqlite-roster-offline":
        print(check_sqlite_roster_offline(
            args.roster,
            args.connections_config,
            args.server_config,
            args.database,
        ))
        return 0
    if args.command == "generate-password":
        created = generate_password_file(args.path)
        print(json.dumps({"ok": True, "created": created, "path": str(Path(args.path).resolve())}))
        return 0
    if args.command == "receipt-check":
        return 0 if receipt_matches(args.path, args.id, args.username, args.password_file) else 1
    if args.command == "receipt-write":
        write_receipt(args.path, args.id, args.username, args.password_file)
        return 0
    raise HelperError("unsupported command")


if __name__ == "__main__":
    try:
        sys.exit(main())
    except HelperError as exc:
        print("ERROR: %s" % exc, file=sys.stderr)
        sys.exit(2)
