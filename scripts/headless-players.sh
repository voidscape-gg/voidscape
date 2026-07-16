#!/usr/bin/env bash
# Local/dev supervisor for the ten real headless sessions and their controller.
# Production uses Deployment_Scripts/systemd/voidscape-headless.target instead.

set -euo pipefail
umask 077

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HELPER="$SCRIPT_DIR/headless-player-helper.py"
RUN_PLAYER="$SCRIPT_DIR/run-headless-player.sh"
RUN_CONTROLLER="$SCRIPT_DIR/run-headless-controller.sh"
VOIDBOTD="$REPO_ROOT/tools/voidbot/voidbotd.py"
CONTROLLER="$REPO_ROOT/tools/headless_players/controller.py"
PYTHON="${HEADLESS_PYTHON:-${VOIDBOT_PYTHON:-python3}}"

ROSTER="${HEADLESS_PLAYERS_ROSTER:-$REPO_ROOT/tools/headless_players/roster.json}"
CREDENTIAL_DIR="${HEADLESS_CREDENTIAL_DIR:-/etc/voidscape/headless-players}"
SERVER_CONFIG="${HEADLESS_SERVER_CONFIG:-$REPO_ROOT/server/local.conf}"
GAME_HOST="${HEADLESS_GAME_HOST:-127.0.0.1}"
GAME_PORT="${HEADLESS_GAME_PORT:-43596}"
CONTROL_PORT_BASE="${HEADLESS_CONTROL_PORT_BASE:-19020}"
DEFS_DIR="${HEADLESS_DEFS_DIR:-$REPO_ROOT/server/conf/server/defs}"
STAGGER_PER_SLOT="${HEADLESS_STAGGER_SECONDS_PER_SLOT:-1.25}"
STOP_TIMEOUT="${HEADLESS_STOP_TIMEOUT_SECONDS:-15}"

STATE_DIR="$REPO_ROOT/run-state/headless-players"
PID_DIR=""
LOG_DIR=""
LOCK_DIR=""
LOCK_HELD=0
ROSTER_LIST=""
ROLLBACK_ON_ERROR=0
STARTED_PID_FILES=()
STARTED_COUNT=0

usage() {
	cat <<'EOF'
Usage: scripts/headless-players.sh <start|stop|status> [options]

Options:
  --roster FILE
  --credentials-dir DIR     Secure absolute directory outside the repository.
  --config FILE             Active world config; must satisfy the fleet runtime contract.
  --host HOST               Game server host (default 127.0.0.1).
  --game-port PORT          Game server port (default 43596).
  --control-port-base PORT  Ten loopback ports beginning here (default 19020).
  --defs DIR                Server definitions directory.
  --state-dir DIR           Absolute dedicated runtime directory
                            (default run-state/headless-players).
  --stagger SECONDS         Per-slot login stagger used by player wrappers.
  --stop-timeout SECONDS    SIGTERM wait before reporting failure (default 15).
  -h, --help

Runtime PIDs and logs live under run-state/headless-players/ by default; an
absolute --state-dir isolates another supervisor. Raw passwords are never
accepted here or placed in process arguments; only credential file paths are
passed to the individual player wrappers.
Every roster profile banks through Banker command 1, so start fails unless the
active config has right_click_bank: true, want_fatigue: false, and
want_bank_notes: true.
EOF
}

rollback_started() {
	local pid_file pid remaining attempt index
	for ((index = 0; index < STARTED_COUNT; index++)); do
		pid_file="${STARTED_PID_FILES[$index]}"
		if [[ -f "$pid_file" ]]; then
			pid="$(sed -n '1p' "$pid_file")"
			if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
				kill -TERM "$pid" 2>/dev/null || true
			fi
		fi
	done
	for ((attempt = 0; attempt < 20; attempt++)); do
		remaining=0
		for ((index = 0; index < STARTED_COUNT; index++)); do
			pid_file="${STARTED_PID_FILES[$index]}"
			if [[ -f "$pid_file" ]]; then
				pid="$(sed -n '1p' "$pid_file")"
				if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
					remaining=1
				fi
			fi
		done
		(( remaining == 0 )) && break
		sleep 0.1
	done
	for ((index = 0; index < STARTED_COUNT; index++)); do
		pid_file="${STARTED_PID_FILES[$index]}"
		if [[ -f "$pid_file" ]]; then
			pid="$(sed -n '1p' "$pid_file")"
			if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! kill -0 "$pid" 2>/dev/null; then
				rm -f "$pid_file"
			else
				echo "ERROR: rollback could not stop pid $pid; leaving $pid_file intact" >&2
			fi
		fi
	done
}

cleanup() {
	local rc=$?
	trap - EXIT INT TERM
	if (( rc != 0 && ROLLBACK_ON_ERROR == 1 )); then
		rollback_started
	fi
	[[ -n "$ROSTER_LIST" ]] && rm -f "$ROSTER_LIST"
	if (( LOCK_HELD == 1 )); then
		rm -f "$LOCK_DIR/owner.pid"
		rmdir "$LOCK_DIR" 2>/dev/null || true
	fi
	exit "$rc"
}

trap cleanup EXIT
trap 'exit 130' INT TERM

COMMAND="${1:-}"
if [[ "$COMMAND" == "-h" || "$COMMAND" == "--help" ]]; then
	usage
	exit 0
fi
case "$COMMAND" in
	start|stop|status) shift ;;
	*) usage >&2; exit 2 ;;
esac

while [[ $# -gt 0 ]]; do
	case "$1" in
		--roster) ROSTER="$2"; shift 2 ;;
		--credentials-dir) CREDENTIAL_DIR="$2"; shift 2 ;;
		--config) SERVER_CONFIG="$2"; shift 2 ;;
		--host) GAME_HOST="$2"; shift 2 ;;
		--game-port) GAME_PORT="$2"; shift 2 ;;
		--control-port-base) CONTROL_PORT_BASE="$2"; shift 2 ;;
		--defs) DEFS_DIR="$2"; shift 2 ;;
		--state-dir)
			if [[ $# -lt 2 || -z "$2" ]]; then
				echo "ERROR: --state-dir requires an absolute directory" >&2
				exit 2
			fi
			STATE_DIR="$2"
			shift 2
			;;
		--stagger) STAGGER_PER_SLOT="$2"; shift 2 ;;
		--stop-timeout) STOP_TIMEOUT="$2"; shift 2 ;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ ! "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "ERROR: game port must be from 1 to 65535" >&2
	exit 2
fi
if [[ ! "$CONTROL_PORT_BASE" =~ ^[0-9]+$ ]] \
	|| (( CONTROL_PORT_BASE < 1024 || CONTROL_PORT_BASE + 9 > 65535 )); then
	echo "ERROR: control port base must reserve ten ports within 1024..65535" >&2
	exit 2
fi
if ! "$PYTHON" -c 'import math,sys; value=float(sys.argv[1]); assert math.isfinite(value) and 0 <= value <= 60' "$STAGGER_PER_SLOT" 2>/dev/null; then
	echo "ERROR: --stagger must be from 0 to 60 seconds" >&2
	exit 2
fi
if [[ ! "$STOP_TIMEOUT" =~ ^[0-9]+$ ]] || (( STOP_TIMEOUT < 1 || STOP_TIMEOUT > 60 )); then
	echo "ERROR: --stop-timeout must be an integer from 1 to 60" >&2
	exit 2
fi

STATE_DIR="$("$PYTHON" "$HELPER" check-runtime-state-dir --path "$STATE_DIR" \
	--credentials-dir "$CREDENTIAL_DIR" --roster "$ROSTER")"
PID_DIR="$STATE_DIR/pids"
LOG_DIR="$STATE_DIR/logs"
LOCK_DIR="$STATE_DIR/.supervisor.lock"
mkdir -p "$PID_DIR" "$LOG_DIR"
chmod 700 "$STATE_DIR" "$PID_DIR" "$LOG_DIR"
if ! mkdir "$LOCK_DIR" 2>/dev/null; then
	owner="$(sed -n '1p' "$LOCK_DIR/owner.pid" 2>/dev/null || true)"
	if [[ "$owner" =~ ^[0-9]+$ ]] && kill -0 "$owner" 2>/dev/null; then
		echo "ERROR: another headless-player supervisor is active (pid $owner)" >&2
	else
		echo "ERROR: stale supervisor lock: $LOCK_DIR (inspect and remove it manually)" >&2
	fi
	exit 1
fi
LOCK_HELD=1
printf '%s\n' "$$" >"$LOCK_DIR/owner.pid"

ROSTER_LIST="$(mktemp "$STATE_DIR/roster.XXXXXX")"
"$PYTHON" "$HELPER" roster-list --roster "$ROSTER" >"$ROSTER_LIST"

process_command() {
	ps -p "$1" -o command= 2>/dev/null || true
}

pid_matches() {
	local label="$1" slot="$2" username="$3" pid="$4" command port
	command="$(process_command "$pid")"
	if [[ "$label" == "controller" ]]; then
		[[ "$command" == *"$RUN_CONTROLLER"* ]] \
			|| [[ "$command" == *"$CONTROLLER"* && "$command" == *"--control-port-base $CONTROL_PORT_BASE"* ]]
		return
	fi
	port=$((CONTROL_PORT_BASE + slot))
	[[ "$command" == *"$RUN_PLAYER $label"* ]] \
		|| [[ "$command" == *"$VOIDBOTD"* && "$command" == *"--ctrl-port $port"* \
			&& "$command" == *"--user $username"* ]]
}

status_one() {
	local label="$1" slot="$2" username="$3" pid_file="$4" pid
	if [[ ! -f "$pid_file" ]]; then
		printf '%-14s stopped\n' "$label"
		return 1
	fi
	pid="$(sed -n '1p' "$pid_file")"
	if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! kill -0 "$pid" 2>/dev/null; then
		printf '%-14s stale (%s)\n' "$label" "${pid:-invalid pid}"
		return 1
	fi
	if ! pid_matches "$label" "$slot" "$username" "$pid"; then
		printf '%-14s foreign pid %s\n' "$label" "$pid"
		return 1
	fi
	printf '%-14s running (pid %s)\n' "$label" "$pid"
	return 0
}

do_status() {
	local healthy=0 total=0 slot profile_id username
	while IFS=$'\t' read -r slot profile_id username; do
		total=$((total + 1))
		status_one "$profile_id" "$slot" "$username" "$PID_DIR/$profile_id.pid" && healthy=$((healthy + 1))
	done <"$ROSTER_LIST"
	total=$((total + 1))
	status_one controller -1 controller "$PID_DIR/controller.pid" && healthy=$((healthy + 1))
	echo "Fleet: $healthy/$total processes running"
	(( healthy == total ))
}

launch_player() {
	local slot="$1" profile_id="$2" username="$3" password_file pid_file log_file pid
	password_file="$CREDENTIAL_DIR/$profile_id.password"
	pid_file="$PID_DIR/$profile_id.pid"
	log_file="$LOG_DIR/$profile_id.log"
	: >"$log_file"
	HEADLESS_STAGGER_SECONDS_PER_SLOT="$STAGGER_PER_SLOT" \
		nohup "$RUN_PLAYER" "$profile_id" --roster "$ROSTER" \
		--password-file "$password_file" --host "$GAME_HOST" --game-port "$GAME_PORT" \
		--control-port-base "$CONTROL_PORT_BASE" --defs "$DEFS_DIR" \
		>"$log_file" 2>&1 </dev/null &
	pid=$!
	printf '%s\n' "$pid" >"$pid_file"
	STARTED_PID_FILES+=("$pid_file")
	STARTED_COUNT=$((STARTED_COUNT + 1))
	echo "==> started $username (pid $pid; control $((CONTROL_PORT_BASE + slot)))"
}

do_start() {
	local slot profile_id username pid_file password_file receipt_file pid failed
	for pid_file in "$PID_DIR"/*.pid; do
		[[ -e "$pid_file" ]] || continue
		pid="$(sed -n '1p' "$pid_file" 2>/dev/null || true)"
		if [[ "$pid" =~ ^[0-9]+$ ]] && kill -0 "$pid" 2>/dev/null; then
			echo "ERROR: live PID collision in $pid_file (pid $pid); refusing duplicate start" >&2
		else
			echo "ERROR: stale PID collision in $pid_file; run '$0 stop' before starting" >&2
		fi
		exit 1
	done

	"$PYTHON" "$HELPER" check-fleet-runtime-config --path "$SERVER_CONFIG" >/dev/null
	"$PYTHON" "$HELPER" check-control-ports --host 127.0.0.1 \
		--base "$CONTROL_PORT_BASE" --count 10
	CREDENTIAL_DIR="$("$PYTHON" "$HELPER" check-credential-dir --path "$CREDENTIAL_DIR")"
	while IFS=$'\t' read -r slot profile_id username; do
		password_file="$CREDENTIAL_DIR/$profile_id.password"
		receipt_file="$CREDENTIAL_DIR/$profile_id.provisioned"
		if ! "$PYTHON" "$HELPER" receipt-check --path "$receipt_file" --id "$profile_id" \
			--username "$username" --password-file "$password_file"; then
			echo "ERROR: credentials for $username are missing, unknown, or mismatched; provision first" >&2
			exit 2
		fi
	done <"$ROSTER_LIST"

	ROLLBACK_ON_ERROR=1
	while IFS=$'\t' read -r slot profile_id username; do
		launch_player "$slot" "$profile_id" "$username"
	done <"$ROSTER_LIST"

	: >"$LOG_DIR/controller.log"
	nohup "$RUN_CONTROLLER" --roster "$ROSTER" --control-host 127.0.0.1 \
		--control-port-base "$CONTROL_PORT_BASE" >"$LOG_DIR/controller.log" 2>&1 </dev/null &
	pid=$!
	printf '%s\n' "$pid" >"$PID_DIR/controller.pid"
	STARTED_PID_FILES+=("$PID_DIR/controller.pid")
	STARTED_COUNT=$((STARTED_COUNT + 1))
	echo "==> started fleet controller (pid $pid)"

	sleep 0.25
	failed=0
	for ((index = 0; index < STARTED_COUNT; index++)); do
		pid_file="${STARTED_PID_FILES[$index]}"
		pid="$(sed -n '1p' "$pid_file")"
		if ! kill -0 "$pid" 2>/dev/null; then
			echo "ERROR: $(basename "$pid_file" .pid) exited during startup; inspect $LOG_DIR" >&2
			failed=1
		fi
	done
	(( failed == 0 )) || exit 1
	ROLLBACK_ON_ERROR=0
	echo "==> Headless fleet started; logs: $LOG_DIR"
}

STOP_PIDS=()
STOP_FILES=()
STOP_COUNT=0

stop_one() {
	local label="$1" slot="$2" username="$3" pid_file="$4" pid
	[[ -f "$pid_file" ]] || return 0
	pid="$(sed -n '1p' "$pid_file")"
	if [[ ! "$pid" =~ ^[0-9]+$ ]] || ! kill -0 "$pid" 2>/dev/null; then
		echo "==> removing stale PID for $label"
		rm -f "$pid_file"
		return 0
	fi
	if ! pid_matches "$label" "$slot" "$username" "$pid"; then
		echo "ERROR: refusing to signal foreign pid $pid recorded for $label" >&2
		return 1
	fi
	if ! kill -TERM "$pid" 2>/dev/null; then
		echo "ERROR: could not send SIGTERM to $label (pid $pid)" >&2
		return 1
	fi
	STOP_PIDS+=("$pid")
	STOP_FILES+=("$pid_file")
	STOP_COUNT=$((STOP_COUNT + 1))
	echo "==> sent SIGTERM to $label (pid $pid)"
}

do_stop() {
	local failures=0 slot profile_id username attempt index alive pid
	stop_one controller -1 controller "$PID_DIR/controller.pid" || failures=1
	while IFS=$'\t' read -r slot profile_id username; do
		stop_one "$profile_id" "$slot" "$username" "$PID_DIR/$profile_id.pid" || failures=1
	done <"$ROSTER_LIST"

	for ((attempt = 0; attempt < STOP_TIMEOUT * 5; attempt++)); do
		alive=0
		for ((index = 0; index < STOP_COUNT; index++)); do
			pid="${STOP_PIDS[$index]}"
			kill -0 "$pid" 2>/dev/null && alive=1
		done
		(( alive == 0 )) && break
		sleep 0.2
	done

	for ((index = 0; index < STOP_COUNT; index++)); do
		pid="${STOP_PIDS[$index]}"
		if kill -0 "$pid" 2>/dev/null; then
			echo "ERROR: pid $pid did not exit after SIGTERM; leaving its PID file intact" >&2
			failures=1
		else
			rm -f "${STOP_FILES[$index]}"
		fi
	done
	if (( failures != 0 )); then
		return 1
	fi
	echo "==> Headless fleet stopped"
}

case "$COMMAND" in
	start) do_start ;;
	stop) do_stop ;;
	status) do_status ;;
esac
