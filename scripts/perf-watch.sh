#!/usr/bin/env bash
set -euo pipefail

LOG_FILE="${1:-server/logs/codex-server-run.log}"

mkdir -p "$(dirname "$LOG_FILE")"
touch "$LOG_FILE"

echo "Watching $LOG_FILE for PERF summaries and late-tick warnings."
echo "Enable with perf_telemetry: true in the active server config. Press Ctrl-C to stop."

tail -n "${PERF_TAIL_LINES:-120}" -F "$LOG_FILE" | awk '
	/PERF/ || /Tick [0-9]+ is late/ || /behind\. Skipping/ {
		print;
		fflush();
	}
'
