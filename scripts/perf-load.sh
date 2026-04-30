#!/usr/bin/env bash
set -euo pipefail

COUNT="${1:-50}"
DURATION_SECONDS="${2:-120}"
RADIUS="${3:-18}"
INTERVAL_TICKS="${4:-2}"
LOG_FILE="${LOG_FILE:-server/logs/codex-server-run.log}"

if ! pgrep -f 'orsc.OpenRSC' >/dev/null; then
	echo "No Java client process found. Start the client and log in as an admin before running this load test." >&2
	exit 1
fi

send_command() {
	local command="$1"
	osascript \
		-e 'tell application "System Events" to set frontmost of process "java" to true' \
		-e 'delay 0.2' \
		-e "tell application \"System Events\" to keystroke \"${command}\"" \
		-e 'tell application "System Events" to key code 36'
}

cleanup() {
	send_command "::loadbots stop" || true
}
trap cleanup EXIT

echo "Starting ${COUNT} synthetic load bots for ${DURATION_SECONDS}s (radius=${RADIUS}, intervalTicks=${INTERVAL_TICKS})..."
send_command "::loadbots start ${COUNT} ${RADIUS} ${INTERVAL_TICKS}"

sleep "$DURATION_SECONDS"

echo "Stopping synthetic load bots..."
send_command "::loadbots stop"
trap - EXIT

echo "Recent PERF / late-tick lines:"
rg 'PERF|Tick [0-9]+ is late|behind\. Skipping' "$LOG_FILE" | tail -n 20
