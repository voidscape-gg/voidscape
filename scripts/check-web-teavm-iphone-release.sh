#!/usr/bin/env bash
# Run the local automated iPhone web-client release preflight.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${WEB_TEA_IPHONE_PREFLIGHT_OUT:-$ROOT/tmp/web-teavm-iphone-release-preflight}"
PACKAGE_DIR_ARG="${WEB_TEA_PREFLIGHT_PACKAGE_DIR:-}"
CLIENT_VERSION_OVERRIDE="${WEB_TEA_PREFLIGHT_CLIENT_VERSION:-}"
BUILD=1
RUN_CONTROLS=1
RUN_LOGIN=1
RUN_WORLD_MAP=1
RUN_CHAT=1
RUN_HTTPS_WSS=1
RUN_PACKAGE=1
RUN_SIMULATOR=0
WS_HOST="${WEB_TEA_PREFLIGHT_WS_HOST:-127.0.0.1}"
WS_PORT="${WEB_TEA_PREFLIGHT_WS_PORT:-43496}"
AUTH_USER="${WEB_TEA_SMOKE_USER:-test}"
AUTH_PASS="${WEB_TEA_SMOKE_PASS:-test}"
PORTAL_URL="${WEB_TEA_PREFLIGHT_PORTAL_URL:-/iphone-account/}"
PORTAL_ACCOUNT_URL="${WEB_TEA_PREFLIGHT_PORTAL_ACCOUNT_URL:-}"
PORTAL_RECOVERY_URL="${WEB_TEA_PREFLIGHT_PORTAL_RECOVERY_URL:-}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
SIMULATOR_WAIT="${WEB_TEA_PREFLIGHT_SIMULATOR_WAIT:-5}"
SIMULATOR_MANUAL_SECONDS="${WEB_TEA_PREFLIGHT_SIMULATOR_MANUAL_SECONDS:-0}"
SIMULATOR_RECORD_VIDEO=0
MIN_FREE_MB="${WEB_TEA_PREFLIGHT_MIN_FREE_MB:-512}"
SIMULATOR_VIDEO_BASE_FREE_MB="${WEB_TEA_PREFLIGHT_VIDEO_BASE_FREE_MB:-512}"
SIMULATOR_VIDEO_MB_PER_SECOND="${WEB_TEA_PREFLIGHT_VIDEO_MB_PER_SECOND:-10}"

usage() {
	cat <<'EOF'
Usage: scripts/check-web-teavm-iphone-release.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --out DIR               Output directory for logs, artifacts, and summary.json.
  --ws-host HOST          Local game WebSocket host. Default: 127.0.0.1.
  --ws-port PORT          Local game WebSocket port. Default: 43496.
  --user USER             Login username for local smokes. Default: test.
  --pass PASS             Login password for local smokes. Default: test.
  --portal URL            Portal base URL for smoke handoff checks.
  --portal-account-url URL
                          Explicit Create Account portal URL.
  --portal-recovery-url URL
                          Explicit Recover account portal URL.
  --chrome PATH           Chrome/Chromium executable for Playwright smokes.
  --playwright-core DIR   Directory for playwright-core package.
  --skip-controls         Skip synthetic iPhone controls smoke.
  --skip-login            Skip local real-login iPhone smoke.
  --skip-world-map        Skip focused authenticated world-map/walker smoke.
  --skip-chat             Skip focused authenticated in-game chat smoke.
  --skip-https-wss        Skip local HTTPS/same-host WSS smoke.
  --skip-package          Skip static package and local package verifier.
  --package-dir DIR       Static package directory to create and verify.
                          Default: OUT_DIR/package. Use dist/web-teavm when
                          the local preflight should verify the release package.
  --client-version N      Build/package the web client with a temporary client
                          protocol version. The local server's client_version
                          must match this value for login smokes to pass.
  --with-simulator        Also run iPhone Simulator Safari screenshot preflight.
  --simulator-wait SEC    Seconds to wait before Simulator screenshot. Default: 5.
  --simulator-video SEC   Record a Simulator video with this manual wait duration.
  --min-free-mb MB        Minimum free space required before running. Default: 512.
                          Video runs also require an estimated recording budget.
  -h, --help              Show this help.

This is a local automated preflight. It does not replace:
  - hosted HTTPS/WSS verification with scripts/verify-web-teavm-deployment.sh --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --smoke
  - filled physical iPhone Home Screen QA report validation
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--ws-host)
			WS_HOST="${2:-}"
			shift 2
			;;
		--ws-port)
			WS_PORT="${2:-}"
			shift 2
			;;
		--user)
			AUTH_USER="${2:-}"
			shift 2
			;;
		--pass)
			AUTH_PASS="${2:-}"
			shift 2
			;;
		--portal)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--portal-account-url)
			PORTAL_ACCOUNT_URL="${2:-}"
			shift 2
			;;
		--portal-recovery-url)
			PORTAL_RECOVERY_URL="${2:-}"
			shift 2
			;;
		--chrome)
			CHROME_PATH="${2:-}"
			shift 2
			;;
		--playwright-core)
			PLAYWRIGHT_CORE_DIR="${2:-}"
			shift 2
			;;
		--skip-controls)
			RUN_CONTROLS=0
			shift
			;;
		--skip-login)
			RUN_LOGIN=0
			shift
			;;
		--skip-world-map)
			RUN_WORLD_MAP=0
			shift
			;;
		--skip-chat)
			RUN_CHAT=0
			shift
			;;
		--skip-https-wss)
			RUN_HTTPS_WSS=0
			shift
			;;
		--skip-package)
			RUN_PACKAGE=0
			shift
			;;
		--package-dir)
			PACKAGE_DIR_ARG="${2:-}"
			shift 2
			;;
		--client-version)
			CLIENT_VERSION_OVERRIDE="${2:-}"
			shift 2
			;;
		--with-simulator)
			RUN_SIMULATOR=1
			shift
			;;
		--simulator-wait)
			SIMULATOR_WAIT="${2:-}"
			shift 2
			;;
		--simulator-video)
			RUN_SIMULATOR=1
			SIMULATOR_RECORD_VIDEO=1
			SIMULATOR_MANUAL_SECONDS="${2:-}"
			shift 2
			;;
		--min-free-mb)
			MIN_FREE_MB="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$OUT_DIR" || -z "$WS_HOST" || -z "$WS_PORT" || -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
	echo "ERROR: out, ws-host, ws-port, user, and pass must be non-empty." >&2
	exit 2
fi
if ! [[ "$SIMULATOR_WAIT" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "ERROR: --simulator-wait must be a number of seconds." >&2
	exit 2
fi
if ! [[ "$SIMULATOR_MANUAL_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "ERROR: --simulator-video must be a number of seconds." >&2
	exit 2
fi
if ! [[ "$MIN_FREE_MB" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --min-free-mb must be a whole number of megabytes." >&2
	exit 2
fi
if [[ -n "$CLIENT_VERSION_OVERRIDE" && ! "$CLIENT_VERSION_OVERRIDE" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --client-version must be numeric." >&2
	exit 2
fi
if [[ "$BUILD" -eq 0 && -n "$CLIENT_VERSION_OVERRIDE" ]]; then
	echo "ERROR: --client-version cannot be used with --no-build; the smoke build and package manifest must use the same protocol version." >&2
	exit 2
fi
if ! [[ "$SIMULATOR_VIDEO_BASE_FREE_MB" =~ ^[0-9]+$ ]]; then
	echo "ERROR: WEB_TEA_PREFLIGHT_VIDEO_BASE_FREE_MB must be a whole number of megabytes." >&2
	exit 2
fi
if ! [[ "$SIMULATOR_VIDEO_MB_PER_SECOND" =~ ^[0-9]+$ ]]; then
	echo "ERROR: WEB_TEA_PREFLIGHT_VIDEO_MB_PER_SECOND must be a whole number of megabytes." >&2
	exit 2
fi

EFFECTIVE_MIN_FREE_MB="$MIN_FREE_MB"
if [[ "$SIMULATOR_RECORD_VIDEO" -eq 1 ]]; then
	EFFECTIVE_MIN_FREE_MB="$(python3 - "$MIN_FREE_MB" "$SIMULATOR_VIDEO_BASE_FREE_MB" "$SIMULATOR_VIDEO_MB_PER_SECOND" "$SIMULATOR_WAIT" "$SIMULATOR_MANUAL_SECONDS" <<'PY'
from __future__ import annotations

import math
import sys

minimum = int(sys.argv[1])
base = int(sys.argv[2])
per_second = int(sys.argv[3])
wait_seconds = float(sys.argv[4])
manual_seconds = float(sys.argv[5])

# The video recorder runs during page load, the manual interaction window, and
# the screenshot/orientation tail. The fixed cushion covers Simulator overhead.
video_seconds = max(0.0, wait_seconds) + max(0.0, manual_seconds) + 15.0
video_required = base + math.ceil(video_seconds * per_second)
print(max(minimum, video_required))
PY
)"
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
if [[ "$OUT_DIR" == "/" || "$OUT_DIR" == "$ROOT" ]]; then
	echo "ERROR: refusing to replace unsafe output directory: $OUT_DIR" >&2
	exit 1
fi
LOG_DIR="$OUT_DIR/logs"
STEPS_FILE="$OUT_DIR/steps.tsv"
SUMMARY_FILE="$OUT_DIR/summary.json"
rm -rf "$OUT_DIR"
mkdir -p "$LOG_DIR"
: >"$STEPS_FILE"

if [[ -z "$PACKAGE_DIR_ARG" ]]; then
	PACKAGE_DIR="$OUT_DIR/package"
else
	PACKAGE_DIR="$(mkdir -p "$PACKAGE_DIR_ARG" && cd "$PACKAGE_DIR_ARG" && pwd)"
	if [[ "$PACKAGE_DIR" == "/" || "$PACKAGE_DIR" == "$ROOT" || "$PACKAGE_DIR" == "$OUT_DIR" ]]; then
		echo "ERROR: refusing unsafe --package-dir: $PACKAGE_DIR" >&2
		exit 1
	fi
	case "$OUT_DIR/" in
		"$PACKAGE_DIR"/*)
			echo "ERROR: --out must not live inside --package-dir; package staging would delete the preflight logs." >&2
			exit 1
			;;
	esac
fi

failures=0
PACKAGE_HTTP_PID=

cleanup() {
	if [[ "${PACKAGE_HTTP_PID:-}" =~ ^[0-9]+$ ]]; then
		kill "$PACKAGE_HTTP_PID" >/dev/null 2>&1 || true
		wait "$PACKAGE_HTTP_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

shell_quote() {
	printf "%q" "$1"
}

join_command() {
	local first=1
	for arg in "$@"; do
		if [[ "$first" -eq 0 ]]; then
			printf " "
		fi
		shell_quote "$arg"
		first=0
	done
}

record_step() {
	local name="$1"
	local status="$2"
	local rc="$3"
	local log="$4"
	local artifact="$5"
	local command="$6"
	printf "%s\t%s\t%s\t%s\t%s\t%s\n" "$name" "$status" "$rc" "$log" "$artifact" "$command" >>"$STEPS_FILE"
}

run_step() {
	local name="$1"
	local artifact="$2"
	shift 2
	local log="$LOG_DIR/$name.log"
	local command
	command="$(join_command "$@")"
	echo "==> [$name] $command"
	set +e
	"$@" >"$log" 2>&1
	local rc=$?
	set -e
	if [[ "$rc" -eq 0 ]]; then
		echo "    passed: $log"
		record_step "$name" "passed" "$rc" "$log" "$artifact" "$command"
	else
		echo "    FAILED($rc): $log" >&2
		record_step "$name" "failed" "$rc" "$log" "$artifact" "$command"
		failures=$((failures + 1))
	fi
	return "$rc"
}

skip_step() {
	local name="$1"
	local reason="$2"
	echo "==> [$name] skipped: $reason"
	record_step "$name" "skipped" "0" "" "" "$reason"
}

check_preflight_prerequisites() {
	local log="$LOG_DIR/prerequisites.log"
	local command="check local disk, Chrome, and playwright-core prerequisites"
	: >"$log"
	echo "==> [prerequisites] $command"
	local ok=1
	local free_kib
	free_kib="$(df -Pk "$OUT_DIR" | awk 'NR == 2 { print $4 }')"
	if [[ ! "$free_kib" =~ ^[0-9]+$ ]]; then
		ok=0
		echo "ERROR: unable to determine free disk space for $OUT_DIR." >>"$log"
	else
		local min_free_kib=$((EFFECTIVE_MIN_FREE_MB * 1024))
		local free_mb=$((free_kib / 1024))
		echo "free disk: ${free_mb} MB (${free_kib} KiB) at $OUT_DIR" >>"$log"
		echo "requested free disk floor: ${MIN_FREE_MB} MB" >>"$log"
		if [[ "$SIMULATOR_RECORD_VIDEO" -eq 1 ]]; then
			{
				echo "simulator video budget: base=${SIMULATOR_VIDEO_BASE_FREE_MB} MB, perSecond=${SIMULATOR_VIDEO_MB_PER_SECOND} MB, wait=${SIMULATOR_WAIT}s, manual=${SIMULATOR_MANUAL_SECONDS}s"
				echo "effective free disk floor: ${EFFECTIVE_MIN_FREE_MB} MB (${min_free_kib} KiB)"
			} >>"$log"
		else
			echo "effective free disk floor: ${EFFECTIVE_MIN_FREE_MB} MB (${min_free_kib} KiB)" >>"$log"
		fi
		if (( free_kib < min_free_kib )); then
			ok=0
			echo "ERROR: only ${free_mb} MB free at $OUT_DIR; need at least ${EFFECTIVE_MIN_FREE_MB} MB for the iPhone web preflight." >>"$log"
		fi
	fi

	if [[ "$RUN_CONTROLS" -eq 1 || "$RUN_LOGIN" -eq 1 || "$RUN_WORLD_MAP" -eq 1 || "$RUN_CHAT" -eq 1 || "$RUN_HTTPS_WSS" -eq 1 ]]; then
		if [[ -z "$PLAYWRIGHT_CORE_DIR" ]]; then
			if [[ -d "$ROOT/node_modules/playwright-core" ]]; then
				PLAYWRIGHT_CORE_DIR="$ROOT/node_modules/playwright-core"
			elif [[ -d "/tmp/voidscape-playwright-smoke/node_modules/playwright-core" ]]; then
				PLAYWRIGHT_CORE_DIR="/tmp/voidscape-playwright-smoke/node_modules/playwright-core"
			fi
		fi

		if [[ -z "$CHROME_PATH" ]]; then
			for candidate in \
				"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
				"/Applications/Chromium.app/Contents/MacOS/Chromium" \
				"/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"; do
				if [[ -x "$candidate" ]]; then
					CHROME_PATH="$candidate"
					break
				fi
			done
		fi

		if [[ -z "$PLAYWRIGHT_CORE_DIR" || ! -d "$PLAYWRIGHT_CORE_DIR" ]]; then
			ok=0
			cat >>"$log" <<'EOF'
ERROR: playwright-core was not found.

Install it outside the repo or point --playwright-core / PLAYWRIGHT_CORE_DIR at an existing package:
  mkdir -p /tmp/voidscape-playwright-smoke
  cd /tmp/voidscape-playwright-smoke
  npm init -y
  npm install playwright-core
EOF
		fi
		if [[ -z "$CHROME_PATH" || ! -x "$CHROME_PATH" ]]; then
			ok=0
			cat >>"$log" <<'EOF'
ERROR: Chrome/Chromium executable was not found.

Install Google Chrome/Chromium/Microsoft Edge, pass --chrome PATH, or set CHROME_PATH.
EOF
		fi
	else
		echo "browser smokes skipped; Chrome/playwright-core not required for this run" >>"$log"
	fi

	if [[ "$ok" -eq 1 ]]; then
		{
			if [[ -n "$PLAYWRIGHT_CORE_DIR" ]]; then
				echo "playwright-core: $PLAYWRIGHT_CORE_DIR"
			fi
			if [[ -n "$CHROME_PATH" ]]; then
				echo "chrome: $CHROME_PATH"
			fi
		} >>"$log"
		echo "    passed: $log"
		record_step prerequisites "passed" "0" "$log" "" "$command"
		return 0
	fi

	echo "    FAILED(1): $log" >&2
	record_step prerequisites "failed" "1" "$log" "" "$command"
	failures=$((failures + 1))
	return 1
}

write_summary() {
	python3 - "$STEPS_FILE" "$SUMMARY_FILE" "$OUT_DIR" "$failures" "$CLIENT_VERSION_OVERRIDE" <<'PY'
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

steps_path = Path(sys.argv[1])
summary_path = Path(sys.argv[2])
out_dir = Path(sys.argv[3])
failure_count = int(sys.argv[4])

steps = []
for line in steps_path.read_text(encoding="utf-8").splitlines():
    name, status, rc, log, artifact, command = line.split("\t", 5)
    steps.append(
        {
            "name": name,
            "status": status,
            "exitCode": int(rc),
            "log": log or None,
            "artifact": artifact or None,
            "command": command,
        }
    )

summary = {
    "createdAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "outDir": str(out_dir),
    "passed": failure_count == 0,
    "failureCount": failure_count,
    "steps": steps,
    "scope": "local automated iPhone web preflight",
    "clientVersionOverride": None,
    "notCovered": [
        "hosted HTTPS/WSS deployment verification with --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --smoke",
        "physical iPhone Home Screen QA report validation",
        "real iPhone performance and touch feel",
    ],
}
client_version_override = None
if len(sys.argv) > 5 and sys.argv[5]:
    client_version_override = int(sys.argv[5])
summary["clientVersionOverride"] = client_version_override
summary_path.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
PY
}

if ! check_preflight_prerequisites; then
	skip_step build-web-teavm "prerequisites failed"
	skip_step controls-smoke "prerequisites failed"
	skip_step login-smoke "prerequisites failed"
	skip_step world-map-smoke "prerequisites failed"
	skip_step chat-smoke "prerequisites failed"
	skip_step https-wss-smoke "prerequisites failed"
	skip_step package "prerequisites failed"
	skip_step package-verify "prerequisites failed"
	skip_step simulator "prerequisites failed"
	write_summary
	echo "iPhone web local preflight failed: $SUMMARY_FILE" >&2
	exit 1
fi

build_ok=1
if [[ "$BUILD" -eq 1 ]]; then
	build_args=()
	if [[ -n "$CLIENT_VERSION_OVERRIDE" ]]; then
		build_args+=(--client-version "$CLIENT_VERSION_OVERRIDE")
	fi
	if ! run_step build-web-teavm "$OUT_DIR/build-web-teavm" "$ROOT/scripts/build-web-teavm-spike.sh" "${build_args[@]}"; then
		build_ok=0
	fi
else
	skip_step build-web-teavm "--no-build"
fi

if [[ "$build_ok" -eq 0 ]]; then
	skip_step controls-smoke "build failed"
	skip_step login-smoke "build failed"
	skip_step world-map-smoke "build failed"
	skip_step chat-smoke "build failed"
	skip_step https-wss-smoke "build failed"
	skip_step package "build failed"
	skip_step package-verify "build failed"
	skip_step simulator "build failed"
	write_summary
	echo "iPhone web local preflight failed: $SUMMARY_FILE" >&2
	exit 1
fi

common_smoke_args=(--no-build --user "$AUTH_USER" --pass "$AUTH_PASS")
if [[ -n "$CHROME_PATH" ]]; then
	common_smoke_args+=(--chrome "$CHROME_PATH")
fi
if [[ -n "$PLAYWRIGHT_CORE_DIR" ]]; then
	common_smoke_args+=(--playwright-core "$PLAYWRIGHT_CORE_DIR")
fi

portal_args=()
if [[ -n "$PORTAL_URL" ]]; then
	portal_args+=(--portal "$PORTAL_URL")
fi
if [[ -n "$PORTAL_ACCOUNT_URL" ]]; then
	portal_args+=(--portal-account-url "$PORTAL_ACCOUNT_URL")
fi
if [[ -n "$PORTAL_RECOVERY_URL" ]]; then
	portal_args+=(--portal-recovery-url "$PORTAL_RECOVERY_URL")
fi

if [[ "$RUN_CONTROLS" -eq 1 ]]; then
	controls_args=(--no-build --out "$OUT_DIR/controls-smoke")
	if [[ -n "$CHROME_PATH" ]]; then
		controls_args+=(--chrome "$CHROME_PATH")
	fi
	if [[ -n "$PLAYWRIGHT_CORE_DIR" ]]; then
		controls_args+=(--playwright-core "$PLAYWRIGHT_CORE_DIR")
	fi
	run_step controls-smoke "$OUT_DIR/controls-smoke" "$ROOT/scripts/smoke-web-teavm-iphone-controls.sh" "${controls_args[@]}" || true
else
	skip_step controls-smoke "--skip-controls"
fi

if [[ "$RUN_LOGIN" -eq 1 ]]; then
	login_args=("${common_smoke_args[@]}" --host "$WS_HOST" --ws-port "$WS_PORT" --out "$OUT_DIR/login-smoke" "${portal_args[@]}")
	run_step login-smoke "$OUT_DIR/login-smoke" "$ROOT/scripts/smoke-web-teavm-iphone.sh" "${login_args[@]}" || true
else
	skip_step login-smoke "--skip-login"
fi

if [[ "$RUN_WORLD_MAP" -eq 1 ]]; then
	world_map_args=("${common_smoke_args[@]}" --host "$WS_HOST" --ws-port "$WS_PORT" --out "$OUT_DIR/world-map-smoke" --only-world-map-search "${portal_args[@]}")
	run_step world-map-smoke "$OUT_DIR/world-map-smoke" "$ROOT/scripts/smoke-web-teavm-iphone.sh" "${world_map_args[@]}" || true
else
	skip_step world-map-smoke "--skip-world-map"
fi

if [[ "$RUN_CHAT" -eq 1 ]]; then
	chat_args=("${common_smoke_args[@]}" --host "$WS_HOST" --ws-port "$WS_PORT" --out "$OUT_DIR/chat-smoke" --only-chat "${portal_args[@]}")
	run_step chat-smoke "$OUT_DIR/chat-smoke" "$ROOT/scripts/smoke-web-teavm-iphone.sh" "${chat_args[@]}" || true
else
	skip_step chat-smoke "--skip-chat"
fi

if [[ "$RUN_HTTPS_WSS" -eq 1 ]]; then
	https_args=("${common_smoke_args[@]}" --ws-host "$WS_HOST" --ws-port "$WS_PORT" --out "$OUT_DIR/https-wss-smoke")
	run_step https-wss-smoke "$OUT_DIR/https-wss-smoke" "$ROOT/scripts/smoke-web-teavm-iphone-https-wss.sh" "${https_args[@]}" || true
else
	skip_step https-wss-smoke "--skip-https-wss"
fi

if [[ "$RUN_PACKAGE" -eq 1 ]]; then
	package_args=(--skip-build --output-dir "$PACKAGE_DIR")
	if [[ -n "$CLIENT_VERSION_OVERRIDE" ]]; then
		package_args=(--client-version "$CLIENT_VERSION_OVERRIDE" --output-dir "$PACKAGE_DIR")
	fi
	if run_step package "$PACKAGE_DIR" "$ROOT/scripts/package-web-teavm.sh" "${package_args[@]}"; then
		VERIFY_PORT="$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)"
		PACKAGE_HTTP_LOG="$LOG_DIR/package-http.log"
		python3 -u -m http.server "$VERIFY_PORT" --bind 127.0.0.1 -d "$PACKAGE_DIR" >"$PACKAGE_HTTP_LOG" 2>&1 &
		PACKAGE_HTTP_PID=$!
		python3 - "$VERIFY_PORT" <<'PY'
import sys
import time
import urllib.request

port = sys.argv[1]
url = f"http://127.0.0.1:{port}/index.html"
deadline = time.time() + 10
last_error = None
while time.time() < deadline:
    try:
        with urllib.request.urlopen(url, timeout=1) as response:
            if response.status == 200:
                raise SystemExit(0)
    except Exception as exc:
        last_error = exc
    time.sleep(0.2)
print(f"ERROR: packaged static server did not become ready: {last_error}", file=sys.stderr)
raise SystemExit(1)
PY
		run_step package-verify "$OUT_DIR/package-verify" "$ROOT/scripts/verify-web-teavm-deployment.sh" --allow-http --base-url "http://127.0.0.1:$VERIFY_PORT/" --expected-build-manifest "$PACKAGE_DIR/voidscape-web-build.json" --deep-manifest --out "$OUT_DIR/package-verify" || true
		kill "$PACKAGE_HTTP_PID" >/dev/null 2>&1 || true
		wait "$PACKAGE_HTTP_PID" >/dev/null 2>&1 || true
		PACKAGE_HTTP_PID=
	else
		skip_step package-verify "package failed"
	fi
else
	skip_step package "--skip-package"
	skip_step package-verify "--skip-package"
fi

if [[ "$RUN_SIMULATOR" -eq 1 ]]; then
	simulator_args=(--no-build --diag --orientation-matrix --exit-after-open --wait "$SIMULATOR_WAIT" --out "$OUT_DIR/simulator")
	if [[ "$SIMULATOR_RECORD_VIDEO" -eq 1 ]]; then
		simulator_args+=(--record-video --manual-seconds "$SIMULATOR_MANUAL_SECONDS")
	fi
	run_step simulator "$OUT_DIR/simulator" "$ROOT/scripts/run-web-teavm-iphone-simulator.sh" "${simulator_args[@]}" || true
else
	skip_step simulator "not requested; use --with-simulator or --simulator-video SEC"
fi

write_summary

if [[ "$failures" -eq 0 ]]; then
	cat <<EOF
==> iPhone web local preflight passed
    Summary: $SUMMARY_FILE

Still required before release:
  - scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --smoke ...
  - filled physical iPhone Home Screen QA report validated without bypass flags
EOF
	exit 0
fi

cat <<EOF >&2
==> iPhone web local preflight failed
    Failures: $failures
    Summary:  $SUMMARY_FILE
EOF
exit 1
