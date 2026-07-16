#!/usr/bin/env bash
# Run one real headless game session. The controller is a separate service.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HELPER="$SCRIPT_DIR/headless-player-helper.py"
VOIDBOTD="$REPO_ROOT/tools/voidbot/voidbotd.py"
PYTHON="${HEADLESS_PYTHON:-${VOIDBOT_PYTHON:-python3}}"

ROSTER="${HEADLESS_PLAYERS_ROSTER:-$REPO_ROOT/tools/headless_players/roster.json}"
PASSWORD_FILE="${HEADLESS_PASSWORD_FILE:-}"
GAME_HOST="${HEADLESS_GAME_HOST:-127.0.0.1}"
GAME_PORT="${HEADLESS_GAME_PORT:-43596}"
CONTROL_PORT_BASE="${HEADLESS_CONTROL_PORT_BASE:-19020}"
DEFS_DIR="${HEADLESS_DEFS_DIR:-$REPO_ROOT/server/conf/server/defs}"
STAGGER_PER_SLOT="${HEADLESS_STAGGER_SECONDS_PER_SLOT:-1.25}"

usage() {
	cat <<'EOF'
Usage: scripts/run-headless-player.sh <profile-id> --password-file FILE [options]

Options:
  --roster FILE
  --password-file FILE       Required; the raw password is never accepted on the command line.
  --host HOST
  --game-port PORT
  --control-port-base PORT   Slot N uses base + N (default 19020).
  --defs DIR
  --no-stagger               Skip the normal slot-based login delay.
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
	usage
	exit 0
fi
PROFILE_ID="${1:-}"
if [[ -z "$PROFILE_ID" || "$PROFILE_ID" == -* ]]; then
	usage >&2
	exit 2
fi
shift
NO_STAGGER=0
while [[ $# -gt 0 ]]; do
	case "$1" in
		--roster) ROSTER="$2"; shift 2 ;;
		--password-file) PASSWORD_FILE="$2"; shift 2 ;;
		--host) GAME_HOST="$2"; shift 2 ;;
		--game-port) GAME_PORT="$2"; shift 2 ;;
		--control-port-base) CONTROL_PORT_BASE="$2"; shift 2 ;;
		--defs) DEFS_DIR="$2"; shift 2 ;;
		--no-stagger) NO_STAGGER=1; shift ;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ -z "$PASSWORD_FILE" ]]; then
	echo "ERROR: --password-file is required" >&2
	exit 2
fi
if [[ ! -d "$DEFS_DIR" ]]; then
	echo "ERROR: item/object definitions directory not found: $DEFS_DIR" >&2
	exit 2
fi
if [[ ! "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "ERROR: game port must be from 1 to 65535" >&2
	exit 2
fi
if [[ ! "$CONTROL_PORT_BASE" =~ ^[0-9]+$ ]] \
	|| (( CONTROL_PORT_BASE < 1 || CONTROL_PORT_BASE + 9 > 65535 )); then
	echo "ERROR: control port base must reserve ten ports within 1..65535" >&2
	exit 2
fi
if ! "$PYTHON" -c 'import math,sys; value=float(sys.argv[1]); assert math.isfinite(value) and 0 <= value <= 60' "$STAGGER_PER_SLOT" 2>/dev/null; then
	echo "ERROR: slot stagger must be from 0 to 60 seconds" >&2
	exit 2
fi

profile_line="$("$PYTHON" "$HELPER" roster-lookup --roster "$ROSTER" --id "$PROFILE_ID")"
IFS=$'\t' read -r slot resolved_id username <<<"$profile_line"
if (( NO_STAGGER == 0 )); then
	delay="$("$PYTHON" -c 'import sys; print(float(sys.argv[1]) * int(sys.argv[2]))' "$STAGGER_PER_SLOT" "$slot")"
	if [[ "$delay" != "0.0" ]]; then
		echo "==> $username: staggering login by ${delay}s (slot $slot)"
		sleep "$delay"
	fi
fi

export PYTHONDONTWRITEBYTECODE=1
exec "$PYTHON" "$VOIDBOTD" \
	--host "$GAME_HOST" \
	--game-port "$GAME_PORT" \
	--ctrl-port "$((CONTROL_PORT_BASE + slot))" \
	--user "$username" \
	--password-file "$PASSWORD_FILE" \
	--defs "$DEFS_DIR"
