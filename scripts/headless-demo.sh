#!/usr/bin/env bash
# Disposable, loopback-only first-login demonstration for the ordinary headless fleet.

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TESTING="${HEADLESS_DEMO_TESTING:-0}"

if [[ -n "${HEADLESS_DEMO_REPO_ROOT:-}" && "$TESTING" != "1" ]]; then
	echo "ERROR: HEADLESS_DEMO_REPO_ROOT is test-only" >&2
	exit 2
fi
if [[ -n "${HEADLESS_DEMO_SNAPSHOT:-}${HEADLESS_DEMO_EXPECTED_SNAPSHOT_SHA256:-}" \
	&& "$TESTING" != "1" ]]; then
	echo "ERROR: snapshot overrides are test-only; the demo seed is pinned" >&2
	exit 2
fi

REPO_ROOT="${HEADLESS_DEMO_REPO_ROOT:-$DEFAULT_REPO_ROOT}"
SERVER_DIR="$REPO_ROOT/server"
STATE_DIR="$REPO_ROOT/run-state/headless-demo"
FLEET_STATE_DIR="$STATE_DIR/fleet"
SERVER_PID_FILE="$STATE_DIR/server.pid"
SERVER_LISTENER_PID_FILE="$STATE_DIR/server-listener.pid"
SERVER_OWNED_PIDS_FILE="$STATE_DIR/server-owned.pids"
SERVER_LOG="$STATE_DIR/server.log"
OPERATION_LOCK_DIR="$STATE_DIR/.operation.lock"
FLEET_STARTED_MARKER="$STATE_DIR/fleet-started.once"
DEMO_CONFIG="$SERVER_DIR/headless-demo.conf"
DEMO_DATABASE="$SERVER_DIR/inc/sqlite/headless_demo.db"
SOURCE_CONFIG="$SERVER_DIR/local.conf"
SNAPSHOT="${HEADLESS_DEMO_SNAPSHOT:-$SERVER_DIR/inc/sqlite/promo_video-pre-headless-20260715T001201Z.db}"
APPROVED_SNAPSHOT_SHA256="${HEADLESS_DEMO_EXPECTED_SNAPSHOT_SHA256:-78ec1b255910a1ebd9bfbe962922d285f70e7c8a8418590f53c614a866232012}"
ROSTER="$REPO_ROOT/tools/headless_players/roster.json"
CONNECTIONS_CONFIG="$SERVER_DIR/connections.conf"
CREDENTIAL_DIR="${HEADLESS_CREDENTIAL_DIR:-$HOME/.config/voidscape/headless-players}"
PASSWORD_HELPER="$SERVER_DIR/core.jar"
DEFS_DIR="$SERVER_DIR/conf/server/defs"
RUN_SERVER="$REPO_ROOT/scripts/run-server.sh"
PROVISIONER="$REPO_ROOT/scripts/provision-headless-players.sh"
FLEET_SUPERVISOR="$REPO_ROOT/scripts/headless-players.sh"
PYTHON="${HEADLESS_PYTHON:-${VOIDBOT_PYTHON:-python3}}"

GAME_HOST="127.0.0.1"
GAME_PORT=43606
WS_PORT=43506
CONTROL_PORT_BASE=19020
PROTECTED_PORTS=(43596 43597)
PREPARE_IN_PROGRESS=0
SERVER_STARTED_HERE=0
OPERATION_LOCK_HELD=0
REMOVE_STATE_AFTER_UNLOCK=0

usage() {
	cat <<'EOF'
Usage: scripts/headless-demo.sh <prepare|fleet-start|fleet-stop|reset|stop|status|destroy|plan> [options]

Commands:
  prepare                 Rebuild the disposable DB, provision without login, and start the demo world.
  fleet-start             Start the ten first-login sessions after the observer client is online.
  fleet-stop              Stop only the isolated demo fleet.
  reset                   Stop the demo, restore the snapshot, provision, and start a fresh demo world.
  stop                    Stop the isolated demo fleet and demo world, preserving disposable state.
  status                  Show only isolated demo server/fleet state.
  destroy                 Stop the demo and remove only generated demo config/database/state.
  plan                    Print the fixed isolation contract without changing anything.

Options for fleet-start:
  --stagger SECONDS       Per-slot login delay (default 3; slots enter at N * SECONDS).

The observer client is deliberately separate. Start it against 127.0.0.1:43606,
wait until wbtest is visibly online, then run `fleet-start`. This wrapper never
signals or changes the worlds on 43596 or 43597 and never contacts a remote host.
EOF
}

die() {
	echo "ERROR: $*" >&2
	exit 1
}

require_file() {
	[[ -f "$1" && ! -L "$1" ]] || die "required regular file is missing or unsafe: $1"
}

require_command() {
	command -v "$1" >/dev/null 2>&1 || die "required command is unavailable: $1"
}

acquire_operation_lock() {
	local owner
	mkdir -p "$STATE_DIR"
	chmod 700 "$STATE_DIR"
	if ! mkdir "$OPERATION_LOCK_DIR" 2>/dev/null; then
		owner="$(sed -n '1p' "$OPERATION_LOCK_DIR/owner.pid" 2>/dev/null || true)"
		if pid_is_live "$owner"; then
			die "another headless-demo operation is active (pid $owner)"
		fi
		die "stale headless-demo operation lock: $OPERATION_LOCK_DIR (inspect and remove it manually)"
	fi
	printf '%s\n' "$$" >"$OPERATION_LOCK_DIR/owner.pid"
	OPERATION_LOCK_HELD=1
}

release_operation_lock() {
	if (( OPERATION_LOCK_HELD == 1 )); then
		rm -f "$OPERATION_LOCK_DIR/owner.pid"
		rmdir "$OPERATION_LOCK_DIR" 2>/dev/null || true
		OPERATION_LOCK_HELD=0
	fi
	if (( REMOVE_STATE_AFTER_UNLOCK == 1 )); then
		rmdir "$STATE_DIR" 2>/dev/null || true
	fi
}

listener_pids() {
	local port="$1"
	if [[ "$TESTING" == "1" ]]; then
		return 0
	fi
	lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | sort -nu || true
}

port_is_free() {
	[[ -z "$(listener_pids "$1")" ]]
}

protected_listener_snapshot() {
	local port pids
	if [[ "$TESTING" == "1" ]]; then
		printf '%s\n' test-only
		return
	fi
	for port in "${PROTECTED_PORTS[@]}"; do
		pids="$(listener_pids "$port" | tr '\n' ',' | sed 's/,$//')"
		printf '%s:%s\n' "$port" "$pids"
	done
}

verify_protected_listeners() {
	local before="$1" after
	after="$(protected_listener_snapshot)"
	[[ "$after" == "$before" ]] || die \
		"protected world listeners changed during the demo operation (before: $before; after: $after)"
}

pid_value() {
	local path="$1"
	sed -n '1p' "$path" 2>/dev/null || true
}

pid_is_live() {
	local pid="$1"
	[[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null
}

server_pid_matches() {
	local pid="$1" command
	pid_is_live "$pid" || return 1
	if [[ "$TESTING" == "1" ]]; then
		return 0
	fi
	command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
	[[ "$command" == *"$RUN_SERVER"* && "$command" == *"--config headless-demo"* ]] \
		|| [[ "$command" == *"org.apache.tools.ant"* && "$command" == *"headless-demo"* ]]
}

listener_pid_matches() {
	local pid="$1" command
	pid_is_live "$pid" || return 1
	if [[ "$TESTING" == "1" ]]; then
		return 0
	fi
	command="$(ps -p "$pid" -o command= 2>/dev/null || true)"
	[[ "$command" == *"com.openrsc.server.Server"* && "$command" == *"headless-demo.conf"* ]]
}

descendant_pids() {
	local root_pid="$1"
	if [[ "$TESTING" == "1" ]]; then
		printf '%s\n' "$root_pid"
		return
	fi
	"$PYTHON" - "$root_pid" <<'PY'
import subprocess
import sys

root = int(sys.argv[1])
rows = subprocess.run(
    ["ps", "-axo", "pid=,ppid="], capture_output=True, text=True, check=True
).stdout.splitlines()
children = {}
for row in rows:
    fields = row.split()
    if len(fields) != 2:
        continue
    pid, parent = map(int, fields)
    children.setdefault(parent, []).append(pid)
pending = [root]
seen = set()
while pending:
    pid = pending.pop()
    if pid in seen:
        continue
    seen.add(pid)
    pending.extend(children.get(pid, ()))
for pid in sorted(seen):
    print(pid)
PY
}

pid_is_descendant() {
	local child="$1" parent="$2" candidate
	while IFS= read -r candidate; do
		[[ "$candidate" == "$child" ]] && return 0
	done < <(descendant_pids "$parent")
	return 1
}

record_owned_pids() {
	local launcher_pid="$1" temporary
	temporary="$SERVER_OWNED_PIDS_FILE.new.$$"
	{
		[[ -f "$SERVER_OWNED_PIDS_FILE" ]] && sed -n '/^[0-9][0-9]*$/p' "$SERVER_OWNED_PIDS_FILE"
		descendant_pids "$launcher_pid"
	} | sort -nu >"$temporary"
	mv -f "$temporary" "$SERVER_OWNED_PIDS_FILE"
}

owned_pids_are_stopped() {
	local pid
	[[ -f "$SERVER_OWNED_PIDS_FILE" ]] || return 0
	while IFS= read -r pid; do
		[[ -n "$pid" ]] || continue
		pid_is_live "$pid" && return 1
	done <"$SERVER_OWNED_PIDS_FILE"
	return 0
}

server_is_running() {
	local listener_pid game_listener ws_listener
	listener_pid="$(pid_value "$SERVER_LISTENER_PID_FILE")"
	if [[ "$TESTING" == "1" ]]; then
		listener_pid_matches "$listener_pid"
		return
	fi
	listener_pid_matches "$listener_pid" || return 1
	game_listener="$(listener_pids "$GAME_PORT")"
	ws_listener="$(listener_pids "$WS_PORT")"
	[[ "$game_listener" == "$listener_pid" && "$ws_listener" == "$listener_pid" ]]
}

wait_for_listener() {
	local port="$1" attempt
	if [[ "$TESTING" == "1" ]]; then
		if [[ "$port" == "$WS_PORT" && "${HEADLESS_DEMO_TEST_FAIL_WS:-0}" == "1" ]]; then
			return 1
		fi
		return 0
	fi
	for ((attempt = 0; attempt < 180; attempt++)); do
		if ! pid_is_live "$(pid_value "$SERVER_PID_FILE")"; then
			echo "ERROR: demo server launcher exited; inspect $SERVER_LOG" >&2
			return 1
		fi
		[[ -n "$(listener_pids "$port")" ]] && return 0
		sleep 0.5
	done
	echo "ERROR: timed out waiting for demo listener on 127.0.0.1:$port" >&2
	return 1
}

assert_demo_ports_free() {
	local port
	for port in "$GAME_PORT" "$WS_PORT"; do
		port_is_free "$port" || die "demo port $port is already occupied"
	done
	for ((port = CONTROL_PORT_BASE; port < CONTROL_PORT_BASE + 10; port++)); do
		port_is_free "$port" || die "demo control port $port is already occupied"
	done
}

assert_demo_stopped() {
	local pid_file
	if [[ -e "$SERVER_PID_FILE" || -e "$SERVER_LISTENER_PID_FILE" \
		|| -e "$SERVER_OWNED_PIDS_FILE" ]]; then
		die "demo server state already exists; run '$0 stop' or '$0 reset' first"
	fi
	[[ ! -e "$FLEET_STARTED_MARKER" ]] \
		|| die "this demo has already started once; use '$0 reset' before preparing another run"
	for pid_file in "$FLEET_STATE_DIR"/pids/*.pid; do
		[[ -e "$pid_file" ]] || continue
		die "demo fleet state already exists; run '$0 fleet-stop' or '$0 reset' first"
	done
	assert_demo_ports_free
}

validate_snapshot() {
	local database="$1" expected="$2"
	"$PYTHON" - "$database" "$ROSTER" "$expected" <<'PY'
import json
import sqlite3
import sys
from pathlib import Path
from urllib.parse import quote

database = Path(sys.argv[1]).resolve()
roster_path = Path(sys.argv[2]).resolve()
expected = sys.argv[3]
players = json.loads(roster_path.read_text(encoding="utf-8")).get("players", [])
usernames = [str(player.get("username", "")).strip() for player in players]
if len(usernames) != 10 or len({name.casefold() for name in usernames}) != 10 or any(not name for name in usernames):
    raise SystemExit("roster must contain exactly ten unique non-empty usernames")

immutable = "&immutable=1" if expected == "empty" else ""
uri = "file:%s?mode=ro%s" % (quote(str(database), safe="/"), immutable)
with sqlite3.connect(uri, uri=True) as connection:
    if connection.execute("PRAGMA quick_check").fetchone()[0] != "ok":
        raise SystemExit("SQLite quick_check failed")
    columns = {row[1].casefold() for row in connection.execute("PRAGMA table_info(players)")}
    if not {"id", "username", "online"}.issubset(columns):
        raise SystemExit("players table lacks id/username/online columns")
    placeholders = ",".join("?" for _ in usernames)
    has_login_date = "login_date" in columns
    selected_columns = "id, username, online" + (", login_date" if has_login_date else "")
    rows = connection.execute(
        "SELECT %s FROM players WHERE lower(username) IN (%s)" % (selected_columns, placeholders),
        [name.casefold() for name in usernames],
    ).fetchall()
    observer = connection.execute(
        "SELECT online FROM players WHERE lower(username) = 'wbtest'"
    ).fetchall()
    announcement_markers = []
    if expected in {"provisioned", "observer-online"}:
        cache_columns = {
            row[1].casefold() for row in connection.execute("PRAGMA table_info(player_cache)")
        }
        if not {"playerid", "key"}.issubset(cache_columns):
            raise SystemExit("player_cache table lacks playerID/key columns")
        fleet_ids = [int(row[0]) for row in rows]
        global_keys = ["new_join_char:%d" % player_id for player_id in fleet_ids]
        marker_placeholders = ",".join("?" for _ in global_keys)
        announcement_markers.extend(connection.execute(
            "SELECT playerID, key FROM player_cache "
            "WHERE playerID = 0 AND key IN (%s)" % marker_placeholders,
            global_keys,
        ).fetchall())
        id_placeholders = ",".join("?" for _ in fleet_ids)
        announcement_markers.extend(connection.execute(
            "SELECT playerID, key FROM player_cache "
            "WHERE playerID IN (%s) AND key = 'void_announce_new_player'" % id_placeholders,
            fleet_ids,
        ).fetchall())

observer_online = 1 if expected == "observer-online" else 0
if len(observer) != 1 or int(observer[0][0]) != observer_online:
    state = "online" if observer_online else "offline"
    raise SystemExit("demo database must contain exactly one %s wbtest observer" % state)
if expected == "empty" and rows:
    raise SystemExit("snapshot already contains one or more fleet accounts")
if expected in {"provisioned", "observer-online"}:
    found = {str(row[1]).casefold(): int(row[2]) for row in rows}
    missing = [name for name in usernames if name.casefold() not in found]
    online = [name for name in usernames if found.get(name.casefold(), 0) != 0]
    if missing or len(rows) != 10:
        raise SystemExit("demo database does not contain exactly one row for each fleet account")
    if online:
        raise SystemExit("fleet accounts must remain offline before the first-login demonstration")
    if has_login_date:
        consumed = [str(row[1]) for row in rows if int(row[3] or 0) != 0]
        if consumed:
            raise SystemExit("fleet first-login state was already consumed: %s" % ", ".join(consumed))
    if announcement_markers:
        raise SystemExit("fleet join-announcement state was already consumed")
PY
}

file_sha256() {
	"$PYTHON" - "$1" <<'PY'
import hashlib
import sys
from pathlib import Path

digest = hashlib.sha256()
with Path(sys.argv[1]).open("rb") as source:
    for chunk in iter(lambda: source.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
}

restore_snapshot() {
	local source_hash target_hash temporary
	require_file "$SNAPSHOT"
	[[ ! -e "$SNAPSHOT-wal" && ! -e "$SNAPSHOT-shm" ]] \
		|| die "approved seed has SQLite -wal/-shm sidecars; refusing a non-atomic snapshot"
	[[ ! -L "$DEMO_DATABASE" ]] || die "refusing symlink demo database: $DEMO_DATABASE"
	validate_snapshot "$SNAPSHOT" empty
	source_hash="$(file_sha256 "$SNAPSHOT")"
	[[ "$source_hash" == "$APPROVED_SNAPSHOT_SHA256" ]] \
		|| die "source snapshot does not match the approved pre-fleet SHA-256"
	temporary="$DEMO_DATABASE.new.$$"
	rm -f "$temporary" "$DEMO_DATABASE-wal" "$DEMO_DATABASE-shm"
	cp -p "$SNAPSHOT" "$temporary"
	chmod 600 "$temporary"
	target_hash="$(file_sha256 "$temporary")"
	[[ "$target_hash" == "$source_hash" ]] || {
		rm -f "$temporary"
		die "snapshot copy checksum mismatch"
	}
	mv -f "$temporary" "$DEMO_DATABASE"
	[[ "$(file_sha256 "$SNAPSHOT")" == "$source_hash" ]] || die "source snapshot changed during copy"
}

render_config() {
	local phase="$1"
	[[ "$phase" == "registration" || "$phase" == "runtime" ]] \
		|| die "invalid config phase: $phase"
	require_file "$SOURCE_CONFIG"
	[[ ! -L "$DEMO_CONFIG" ]] || die "refusing symlink demo config: $DEMO_CONFIG"
	"$PYTHON" - "$SOURCE_CONFIG" "$DEMO_CONFIG" "$phase" <<'PY'
import os
import re
import sys
from pathlib import Path

source = Path(sys.argv[1])
destination = Path(sys.argv[2])
phase = sys.argv[3]
updates = {
    "db_name": "headless_demo",
    "server_name": "Voidscape Headless Demo",
    "server_name_welcome": "Voidscape Headless Demo",
    "server_port": "43606",
    "ws_server_port": "43506",
    "max_connections_per_ip": "20",
    "max_players_per_ip": "20",
    "avatar_generator": "false",
    "want_registration_limit": "false" if phase == "registration" else "true",
    "connection_limit": "20",
    "is_localhost_restricted": "false",
    "monitor_online": "false",
    "want_packet_register": "true" if phase == "registration" else "false",
    "want_world_announcements": "true",
    "want_world_new_player_announcements": "true",
}
counts = {key: 0 for key in updates}
rendered = []
pattern = re.compile(r"^(\s*)([A-Za-z0-9_]+)(\s*:\s*)(.*?)(\r?\n)?$")
source_lines = source.read_text(encoding="utf-8").splitlines(keepends=True)
bind_count = sum(
    1 for line in source_lines
    if (match := pattern.match(line)) and match.group(2) == "server_bind_address"
)
if bind_count > 1:
    raise SystemExit("source config contains server_bind_address more than once")
world_headers = sum(1 for line in source_lines if re.match(r"^world\s*:\s*(?:#.*)?$", line.rstrip("\r\n")))
if bind_count == 0 and world_headers != 1:
    raise SystemExit("source config must contain exactly one world section for loopback binding")

for line in source_lines:
    match = pattern.match(line)
    if not match:
        rendered.append(line)
        if bind_count == 0 and re.match(r"^world\s*:\s*(?:#.*)?$", line.rstrip("\r\n")):
            rendered.append("\tserver_bind_address: 127.0.0.1\n")
        continue
    key = match.group(2)
    replacement = updates.get(key)
    drop_comment = False
    if key == "server_bind_address":
        replacement = "127.0.0.1"
    elif key.startswith("want_discord_"):
        replacement = "false"
    elif key.startswith("discord_") and key.endswith("_webhook_url"):
        replacement = "null"
        drop_comment = True
    if replacement is None:
        rendered.append(line)
        if bind_count == 0 and re.match(r"^world\s*:\s*(?:#.*)?$", line.rstrip("\r\n")):
            rendered.append("\tserver_bind_address: 127.0.0.1\n")
        continue
    if key in counts:
        counts[key] += 1
    old_value = match.group(4)
    comment = ""
    if not drop_comment and "#" in old_value:
        comment = " #" + old_value.split("#", 1)[1].rstrip("\r\n")
    newline = match.group(5) or "\n"
    rendered.append(f"{match.group(1)}{key}: {replacement}{comment}{newline}")

bad = {key: count for key, count in counts.items() if count != 1}
if bad:
    raise SystemExit("source config must contain each demo override exactly once: %r" % bad)
temporary = destination.with_name(destination.name + ".new.%d" % os.getpid())
temporary.write_text("".join(rendered), encoding="utf-8")
temporary.chmod(0o600)
os.replace(temporary, destination)
PY
}

start_server() {
	local phase="$1" launcher_pid listener_pid ws_listener_pid
	[[ -x "$RUN_SERVER" ]] || die "explicit server launcher is unavailable: $RUN_SERVER"
	port_is_free "$GAME_PORT" || die "demo game port $GAME_PORT is occupied"
	port_is_free "$WS_PORT" || die "demo websocket port $WS_PORT is occupied"
	mkdir -p "$STATE_DIR"
	chmod 700 "$STATE_DIR"
	printf '\n==> %s phase\n' "$phase" >>"$SERVER_LOG"
	nohup env -u DISCORD_BUG_REPORTS_WEBHOOK_URL -u DISCORD_GLOBAL_CHAT_WEBHOOK_URL \
		"$RUN_SERVER" --config headless-demo --explicit-config >>"$SERVER_LOG" 2>&1 </dev/null &
	launcher_pid=$!
	printf '%s\n' "$launcher_pid" >"$SERVER_PID_FILE"
	record_owned_pids "$launcher_pid"
	SERVER_STARTED_HERE=1
	if ! wait_for_listener "$GAME_PORT"; then
		return 1
	fi
	if [[ "$TESTING" == "1" ]]; then
		listener_pid="$launcher_pid"
	else
		listener_pid="$(listener_pids "$GAME_PORT")"
		[[ "$listener_pid" =~ ^[0-9]+$ ]] || die "demo game port must have exactly one listener"
	fi
	listener_pid_matches "$listener_pid" \
		|| die "43606 listener is not the explicit headless-demo Java server"
	pid_is_descendant "$listener_pid" "$launcher_pid" \
		|| die "43606 listener is not owned by the recorded demo launcher"
	printf '%s\n' "$listener_pid" >"$SERVER_LISTENER_PID_FILE"
	record_owned_pids "$launcher_pid"
	if ! wait_for_listener "$WS_PORT"; then
		return 1
	fi
	if [[ "$TESTING" == "1" ]]; then
		ws_listener_pid="$listener_pid"
	else
		ws_listener_pid="$(listener_pids "$WS_PORT")"
	fi
	[[ "$ws_listener_pid" == "$listener_pid" ]] \
		|| die "43606 and 43506 must be owned by the same headless-demo Java process"
	echo "==> Demo server started for $phase on $GAME_HOST:$GAME_PORT"
}

stop_server() {
	local launcher_pid listener_pid candidate attempt failed=0
	launcher_pid="$(pid_value "$SERVER_PID_FILE")"
	listener_pid="$(pid_value "$SERVER_LISTENER_PID_FILE")"
	if [[ -z "$launcher_pid" && -z "$listener_pid" && ! -e "$SERVER_OWNED_PIDS_FILE" ]]; then
		if ! port_is_free "$GAME_PORT" || ! port_is_free "$WS_PORT"; then
			die "demo port is occupied without owned PID state; refusing to signal it"
		fi
		return 0
	fi
	if pid_is_live "$launcher_pid"; then
		server_pid_matches "$launcher_pid" \
			|| die "recorded demo launcher pid $launcher_pid does not match the explicit demo command"
		record_owned_pids "$launcher_pid"
		if ! pid_is_live "$listener_pid"; then
			listener_pid=""
			while IFS= read -r candidate; do
				if listener_pid_matches "$candidate"; then
					[[ -z "$listener_pid" ]] \
						|| die "multiple owned headless-demo Java processes found"
					listener_pid="$candidate"
				fi
			done < <(descendant_pids "$launcher_pid")
			if [[ -n "$listener_pid" ]]; then
				printf '%s\n' "$listener_pid" >"$SERVER_LISTENER_PID_FILE"
			fi
		fi
	fi

	if pid_is_live "$listener_pid"; then
		listener_pid_matches "$listener_pid" \
			|| die "recorded Java listener is not the explicit headless-demo server"
		if pid_is_live "$launcher_pid"; then
			pid_is_descendant "$listener_pid" "$launcher_pid" \
				|| die "recorded Java listener is not owned by the demo launcher"
		fi
		if [[ "$TESTING" != "1" && "$(listener_pids "$GAME_PORT")" != "$listener_pid" ]]; then
			die "recorded demo listener no longer owns $GAME_PORT; refusing to signal pid $listener_pid"
		fi
		if [[ "$TESTING" != "1" && "$(listener_pids "$WS_PORT")" != "$listener_pid" ]]; then
			die "recorded demo listener no longer owns $WS_PORT; refusing to signal pid $listener_pid"
		fi
		kill -TERM "$listener_pid" 2>/dev/null || failed=1
	fi
	if [[ "$launcher_pid" != "$listener_pid" ]] && pid_is_live "$launcher_pid"; then
		kill -TERM "$launcher_pid" 2>/dev/null || failed=1
	fi
	for ((attempt = 0; attempt < 150; attempt++)); do
		if ! pid_is_live "$listener_pid" && ! pid_is_live "$launcher_pid" \
			&& owned_pids_are_stopped; then
			break
		fi
		sleep 0.2
	done
	if pid_is_live "$listener_pid" || pid_is_live "$launcher_pid" \
		|| ! owned_pids_are_stopped; then
		die "demo server or an owned descendant did not exit after SIGTERM; PID state was preserved"
	fi
	(( failed == 0 )) || die "could not stop the owned demo server"
	rm -f "$SERVER_PID_FILE" "$SERVER_LISTENER_PID_FILE" "$SERVER_OWNED_PIDS_FILE"
	SERVER_STARTED_HERE=0
	echo "==> Demo server stopped"
}

fleet_stop() {
	[[ -x "$FLEET_SUPERVISOR" ]] || die "fleet supervisor is unavailable: $FLEET_SUPERVISOR"
	"$FLEET_SUPERVISOR" stop \
		--state-dir "$FLEET_STATE_DIR" \
		--credentials-dir "$CREDENTIAL_DIR" \
		--config "$DEMO_CONFIG" \
		--host "$GAME_HOST" \
		--game-port "$GAME_PORT" \
		--control-port-base "$CONTROL_PORT_BASE"
}

mark_fleet_started_once() {
	mkdir -p "$STATE_DIR"
	if ! (set -o noclobber; printf 'created_by_pid=%s\n' "$$" >"$FLEET_STARTED_MARKER") 2>/dev/null; then
		die "this first-login demo has already started; use '$0 reset' to replay it"
	fi
}

prepare_demo() {
	local protected_before
	protected_before="$(protected_listener_snapshot)"
	assert_demo_stopped
	for path in "$ROSTER" "$CONNECTIONS_CONFIG" "$PASSWORD_HELPER"; do
		require_file "$path"
	done
	[[ -d "$DEFS_DIR" ]] || die "definitions directory is missing: $DEFS_DIR"
	[[ -x "$PROVISIONER" ]] || die "provisioner is unavailable: $PROVISIONER"
	[[ -d "$CREDENTIAL_DIR" ]] || die "credential directory is missing: $CREDENTIAL_DIR"
	mkdir -p "$STATE_DIR" "$(dirname "$DEMO_DATABASE")"
	chmod 700 "$STATE_DIR"
	PREPARE_IN_PROGRESS=1
	restore_snapshot
	render_config registration
	start_server registration
	"$PROVISIONER" \
		--roster "$ROSTER" \
		--credentials-dir "$CREDENTIAL_DIR" \
		--config "$DEMO_CONFIG" \
		--connections-config "$CONNECTIONS_CONFIG" \
		--database "$DEMO_DATABASE" \
		--password-helper "$PASSWORD_HELPER" \
		--host "$GAME_HOST" \
		--port "$GAME_PORT" \
		--stagger 0.75
	stop_server
	validate_snapshot "$DEMO_DATABASE" provisioned
	render_config runtime
	start_server runtime
	verify_protected_listeners "$protected_before"
	PREPARE_IN_PROGRESS=0
	SERVER_STARTED_HERE=0
	echo "==> Fresh demo ready. Log wbtest into $GAME_HOST:$GAME_PORT before fleet-start."
}

cleanup() {
	local rc=$?
	trap - EXIT INT TERM
	if (( rc != 0 )) && (( PREPARE_IN_PROGRESS == 1 )); then
		set +e
		stopped=1
		if (( SERVER_STARTED_HERE == 1 )); then
			( stop_server ) || stopped=0
		fi
		if (( stopped == 1 )); then
			rm -f "$DEMO_CONFIG" "$DEMO_DATABASE" "$DEMO_DATABASE".new.* \
				"$DEMO_DATABASE-wal" "$DEMO_DATABASE-shm" "$FLEET_STARTED_MARKER"
		fi
	fi
	release_operation_lock
	exit "$rc"
}

trap cleanup EXIT
trap 'exit 130' INT TERM

COMMAND="${1:-}"
if [[ "$COMMAND" == "-h" || "$COMMAND" == "--help" ]]; then
	usage
	exit 0
fi
[[ -n "$COMMAND" ]] || { usage >&2; exit 2; }
shift

STAGGER=3
STAGGER_SET=0
while [[ $# -gt 0 ]]; do
	case "$1" in
		--stagger)
			[[ $# -ge 2 && -n "$2" ]] || { echo "ERROR: --stagger needs a value" >&2; exit 2; }
			STAGGER="$2"
			STAGGER_SET=1
			shift 2
			;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if (( STAGGER_SET == 1 )) && [[ "$COMMAND" != "fleet-start" ]]; then
	die "--stagger is valid only with fleet-start"
fi

if [[ "$TESTING" != "1" ]]; then
	require_command lsof
fi

case "$COMMAND" in
	prepare|fleet-start|fleet-stop|reset|stop|destroy) acquire_operation_lock ;;
esac

case "$COMMAND" in
	prepare)
		prepare_demo
		;;
	fleet-start)
		"$PYTHON" -c 'import math,sys; value=float(sys.argv[1]); assert math.isfinite(value) and 0.5 <= value <= 60' "$STAGGER" 2>/dev/null \
			|| die "--stagger must be from 0.5 to 60 seconds"
		server_is_running || die "owned demo server is not running on $GAME_HOST:$GAME_PORT"
		[[ ! -e "$FLEET_STARTED_MARKER" ]] \
			|| die "this first-login demo has already started; use '$0 reset' to replay it"
		require_file "$DEMO_CONFIG"
		validate_snapshot "$DEMO_DATABASE" observer-online
		grep -Eq '^[[:space:]]*want_packet_register:[[:space:]]*false([[:space:]]|#|$)' "$DEMO_CONFIG" \
			|| die "demo runtime config still permits packet registration"
		protected_before="$(protected_listener_snapshot)"
		mark_fleet_started_once
		"$FLEET_SUPERVISOR" start \
			--state-dir "$FLEET_STATE_DIR" \
			--credentials-dir "$CREDENTIAL_DIR" \
			--config "$DEMO_CONFIG" \
			--host "$GAME_HOST" \
			--game-port "$GAME_PORT" \
			--control-port-base "$CONTROL_PORT_BASE" \
			--defs "$DEFS_DIR" \
			--stagger "$STAGGER"
		verify_protected_listeners "$protected_before"
		echo "==> Demo fleet entering in roster order every ${STAGGER}s"
		;;
	fleet-stop)
		fleet_stop
		;;
	reset)
		fleet_stop
		stop_server
		rm -f "$FLEET_STARTED_MARKER"
		prepare_demo
		;;
	stop)
		fleet_stop
		stop_server
		;;
	status)
		if server_is_running; then
			echo "Demo server: running on $GAME_HOST:$GAME_PORT"
		else
			echo "Demo server: stopped"
		fi
		set +e
		"$FLEET_SUPERVISOR" status \
			--state-dir "$FLEET_STATE_DIR" \
			--credentials-dir "$CREDENTIAL_DIR" \
			--config "$DEMO_CONFIG" \
			--host "$GAME_HOST" \
			--game-port "$GAME_PORT" \
			--control-port-base "$CONTROL_PORT_BASE"
		fleet_status_rc=$?
		set -e
		exit "$fleet_status_rc"
		;;
	destroy)
		fleet_stop
		stop_server
		rm -f "$DEMO_CONFIG" "$DEMO_DATABASE" "$DEMO_DATABASE".new.* \
			"$DEMO_DATABASE-wal" "$DEMO_DATABASE-shm" "$FLEET_STARTED_MARKER"
		rm -rf "$FLEET_STATE_DIR"
		rm -f "$SERVER_LOG" "$SERVER_OWNED_PIDS_FILE"
		REMOVE_STATE_AFTER_UNLOCK=1
		echo "==> Disposable headless demo removed; source snapshot and credentials preserved"
		;;
	plan)
		cat <<EOF
Demo game:       $GAME_HOST:$GAME_PORT
Demo websocket:  $GAME_HOST:$WS_PORT
Fleet controls:  $GAME_HOST:$CONTROL_PORT_BASE-$((CONTROL_PORT_BASE + 9))
Demo database:   $DEMO_DATABASE
Source snapshot: $SNAPSHOT (read-only source)
Fleet state:     $FLEET_STATE_DIR
Protected worlds: 127.0.0.1:43596 and 127.0.0.1:43597
Server launch:   scripts/run-server.sh --config headless-demo --explicit-config
EOF
		;;
	*)
		usage >&2
		exit 2
		;;
esac
