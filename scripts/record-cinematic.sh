#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

DURATION_SECONDS="${1:-45}"
ACTORS="${2:-18}"
BOSS_NPC_ID="${3:-477}"
RADIUS="${4:-5}"
DISPLAY_ID="${DISPLAY_ID:-1}"
OUTPUT_DIR="${OUTPUT_DIR:-output/cinematics}"
START_SCENE="${START_SCENE:-1}"

mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="${OUTPUT_FILE:-$OUTPUT_DIR/voidscape-cinematic-$(date +%Y%m%d-%H%M%S).mov}"

if ! pgrep -f 'orsc.OpenRSC' >/dev/null; then
	echo "No Java client process found. Start the client and log in as an admin before recording." >&2
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
	if [[ "$START_SCENE" == "1" ]]; then
		send_command "::cinematic stop" || true
	fi
}
trap cleanup EXIT

if [[ "$START_SCENE" == "1" ]]; then
	echo "Starting cinematic boss fight: actors=${ACTORS}, bossNpcId=${BOSS_NPC_ID}, radius=${RADIUS}"
	send_command "::cinematic bossfight ${ACTORS} ${BOSS_NPC_ID} ${RADIUS}"
	sleep 2
fi

echo "Recording display ${DISPLAY_ID} for ${DURATION_SECONDS}s to ${OUTPUT_FILE}"
echo "Tip: use F4/F5/F8/F9 in the client before or during recording for camera/HUD shots."
screencapture -x -v -V"${DURATION_SECONDS}" -D"${DISPLAY_ID}" "$OUTPUT_FILE"

trap - EXIT
cleanup
echo "Saved ${OUTPUT_FILE}"
