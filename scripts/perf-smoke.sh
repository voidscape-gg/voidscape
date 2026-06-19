#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="${LOG_FILE:-server/logs/codex-server-run.log}"
WAIT_SECONDS="${WAIT_SECONDS:-35}"

if ! pgrep -f 'orsc.OpenRSC' >/dev/null; then
	echo "No Java client process found. Start the client and log in before running this smoke test." >&2
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

echo "Sending local perf smoke commands through the visible Java client..."
send_command "::pf 122 509"
sleep 2
send_command "::pf 220 440"
sleep 2
send_command "::saveall"

echo "Waiting ${WAIT_SECONDS}s for the next PERF interval..."
sleep "$WAIT_SECONDS"

echo "Recent PERF / late-tick lines:"
rg 'PERF|Tick [0-9]+ is late|behind\. Skipping' "$LOG_FILE" | tail -n 12
