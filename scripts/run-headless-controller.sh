#!/usr/bin/env bash
# Run the fleet controller against ten independently supervised voidbot sessions.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PYTHON="${HEADLESS_PYTHON:-${VOIDBOT_PYTHON:-python3}}"

ROSTER="${HEADLESS_PLAYERS_ROSTER:-$REPO_ROOT/tools/headless_players/roster.json}"
CONTROLLER="${HEADLESS_CONTROLLER:-$REPO_ROOT/tools/headless_players/controller.py}"
CONTROL_HOST="${HEADLESS_CONTROL_HOST:-127.0.0.1}"
CONTROL_PORT_BASE="${HEADLESS_CONTROL_PORT_BASE:-19020}"

usage() {
	cat <<'EOF'
Usage: scripts/run-headless-controller.sh [options] [-- controller-options...]

Options:
  --roster FILE
  --controller FILE
  --control-host HOST
  --control-port-base PORT
  -h, --help
EOF
}

EXTRA=()
EXTRA_COUNT=0
while [[ $# -gt 0 ]]; do
	case "$1" in
		--roster) ROSTER="$2"; shift 2 ;;
		--controller) CONTROLLER="$2"; shift 2 ;;
		--control-host) CONTROL_HOST="$2"; shift 2 ;;
		--control-port-base) CONTROL_PORT_BASE="$2"; shift 2 ;;
		--) shift; EXTRA=("$@"); EXTRA_COUNT=$#; break ;;
		-h|--help) usage; exit 0 ;;
		*) echo "ERROR: unknown option: $1" >&2; usage >&2; exit 2 ;;
	esac
done

if [[ ! -f "$CONTROLLER" ]]; then
	echo "ERROR: headless controller not found: $CONTROLLER" >&2
	exit 2
fi
if [[ ! -f "$ROSTER" ]]; then
	echo "ERROR: headless roster not found: $ROSTER" >&2
	exit 2
fi
if [[ ! "$CONTROL_PORT_BASE" =~ ^[0-9]+$ ]] \
	|| (( CONTROL_PORT_BASE < 1 || CONTROL_PORT_BASE + 9 > 65535 )); then
	echo "ERROR: control port base must reserve ten ports within 1..65535" >&2
	exit 2
fi

export PYTHONDONTWRITEBYTECODE=1
if (( EXTRA_COUNT > 0 )); then
	exec "$PYTHON" "$CONTROLLER" \
		--roster "$ROSTER" \
		--control-host "$CONTROL_HOST" \
		--control-port-base "$CONTROL_PORT_BASE" \
		"${EXTRA[@]}"
fi
exec "$PYTHON" "$CONTROLLER" \
	--roster "$ROSTER" \
	--control-host "$CONTROL_HOST" \
	--control-port-base "$CONTROL_PORT_BASE"
