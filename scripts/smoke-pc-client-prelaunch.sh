#!/usr/bin/env bash
# Screenshot-backed prelaunch smoke for the desktop Java client.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${VOIDSCAPE_PC_SMOKE_OUT:-$ROOT/tmp/pc-client-prelaunch-smoke-$(date +%Y%m%d-%H%M%S)}"
CLIENT_HOST="${VOIDSCAPE_PC_SMOKE_HOST:-127.0.0.1}"
CLIENT_PORT="${VOIDSCAPE_PC_SMOKE_PORT:-}"
WORKBENCH_PORT="${VOIDSCAPE_WORKBENCH_PORT:-0}"
AUTH_USER="${VOIDSCAPE_PC_SMOKE_USER:-}"
AUTH_PASS="${VOIDSCAPE_PC_SMOKE_PASS:-}"
TIMEOUT_SECONDS="${VOIDSCAPE_PC_SMOKE_TIMEOUT:-75}"
PANELS="${VOIDSCAPE_PC_SMOKE_PANELS:-inventory,stats,map,account}"
SKIP_LOGIN=0
KEEP_CLIENT="${VOIDSCAPE_PC_SMOKE_KEEP_CLIENT:-0}"
FRESH_CACHE="${VOIDSCAPE_PC_SMOKE_FRESH_CACHE:-1}"

usage() {
	cat <<'EOF'
Usage: scripts/smoke-pc-client-prelaunch.sh [options]

Options:
  --host HOST             Game server host. Default: 127.0.0.1.
  --port PORT             Game server port. Default: server/local.conf server_port.
  --workbench-port PORT   Workbench HTTP port. Default: choose a free port.
  --out DIR               Output directory for logs/screenshots/manifest.
  --login USER:PASS       Login as a real game account.
  --user USER             Login username.
  --pass PASS             Login password.
  --panels LIST           Comma-separated panels to screenshot. Default: inventory,stats,map,account.
  --timeout SECONDS       Time to wait for client/workbench/login. Default: 75.
  --skip-login            Only capture the branded login screen.
  --use-existing-cache    Do not temporarily clear saved desktop credentials.
  --keep-client           Leave the desktop client running after the smoke.
  -h, --help              Show this help.

The smoke launches scripts/run-workbench-client.sh, waits for its loopback
workbench, optionally logs in through the dev auto-login path, captures the
rendered game frame, opens key UI panels, and writes manifest.json.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--host)
			[[ $# -ge 2 ]] || { echo "error: --host needs a value" >&2; exit 2; }
			CLIENT_HOST="$2"
			shift 2
			;;
		--port)
			[[ $# -ge 2 ]] || { echo "error: --port needs a value" >&2; exit 2; }
			CLIENT_PORT="$2"
			shift 2
			;;
		--workbench-port)
			[[ $# -ge 2 ]] || { echo "error: --workbench-port needs a value" >&2; exit 2; }
			WORKBENCH_PORT="$2"
			shift 2
			;;
		--out)
			[[ $# -ge 2 ]] || { echo "error: --out needs a value" >&2; exit 2; }
			OUT_DIR="$2"
			shift 2
			;;
		--login)
			[[ $# -ge 2 ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
			AUTH_USER="${2%%:*}"
			AUTH_PASS="${2#*:}"
			[[ "$AUTH_USER" != "$2" ]] || { echo "error: --login needs USER:PASS" >&2; exit 2; }
			shift 2
			;;
		--user)
			[[ $# -ge 2 ]] || { echo "error: --user needs a value" >&2; exit 2; }
			AUTH_USER="$2"
			shift 2
			;;
		--pass|--password)
			[[ $# -ge 2 ]] || { echo "error: $1 needs a value" >&2; exit 2; }
			AUTH_PASS="$2"
			shift 2
			;;
		--panels)
			[[ $# -ge 2 ]] || { echo "error: --panels needs a value" >&2; exit 2; }
			PANELS="$2"
			shift 2
			;;
		--timeout)
			[[ $# -ge 2 ]] || { echo "error: --timeout needs a value" >&2; exit 2; }
			TIMEOUT_SECONDS="$2"
			shift 2
			;;
		--skip-login)
			SKIP_LOGIN=1
			shift
			;;
		--use-existing-cache)
			FRESH_CACHE=0
			shift
			;;
		--keep-client)
			KEEP_CLIENT=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "error: unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$CLIENT_PORT" ]]; then
	CLIENT_PORT=43594
	if [[ -f "$ROOT/server/local.conf" ]]; then
		PORT_FROM_CONF=$(grep -E '^[[:space:]]*server_port:' "$ROOT/server/local.conf" | head -1 | awk '{print $2}' || true)
		CLIENT_PORT="${PORT_FROM_CONF:-$CLIENT_PORT}"
	fi
fi

if [[ "$SKIP_LOGIN" -eq 0 ]]; then
	if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
		echo "error: provide --user/--pass, --login, or set VOIDSCAPE_PC_SMOKE_USER/PASS. Use --skip-login for login-screen-only capture." >&2
		exit 2
	fi
fi

if [[ "$WORKBENCH_PORT" == "0" ]]; then
	WORKBENCH_PORT="$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)"
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
WORKBENCH_DIR="$OUT_DIR/workbench"
CLIENT_LOG="$OUT_DIR/client.log"
CACHE_DIR="$ROOT/Client_Base/Cache"
CACHE_BACKUP_DIR="$OUT_DIR/cache-backup"
CACHE_FILES=("accounts.txt" "credentials.txt")

CLIENT_PID=""

kill_tree() {
	local pid="$1"
	local child
	if ! kill -0 "$pid" >/dev/null 2>&1; then
		return
	fi
	for child in $(pgrep -P "$pid" 2>/dev/null || true); do
		kill_tree "$child"
	done
	kill "$pid" >/dev/null 2>&1 || true
}

kill_children_tree() {
	local pid="$1"
	local child
	for child in $(pgrep -P "$pid" 2>/dev/null || true); do
		kill_tree "$child"
	done
}

prepare_fresh_cache() {
	if [[ "$FRESH_CACHE" != "1" ]]; then
		return
	fi
	mkdir -p "$CACHE_DIR" "$CACHE_BACKUP_DIR"
	local file
	for file in "${CACHE_FILES[@]}"; do
		if [[ -f "$CACHE_DIR/$file" ]]; then
			mv "$CACHE_DIR/$file" "$CACHE_BACKUP_DIR/$file"
		fi
	done
}

restore_fresh_cache() {
	if [[ "$FRESH_CACHE" != "1" ]]; then
		return
	fi
	local file
	for file in "${CACHE_FILES[@]}"; do
		rm -f "$CACHE_DIR/$file"
		if [[ -f "$CACHE_BACKUP_DIR/$file" ]]; then
			mv "$CACHE_BACKUP_DIR/$file" "$CACHE_DIR/$file"
		fi
	done
}

cleanup() {
	if [[ "$KEEP_CLIENT" == "1" ]]; then
		return
	fi
	if [[ "${CLIENT_PID:-}" =~ ^[0-9]+$ ]]; then
		kill_children_tree "$CLIENT_PID"
		sleep 1
		if kill -0 "$CLIENT_PID" >/dev/null 2>&1; then
			kill_tree "$CLIENT_PID"
		fi
		wait "$CLIENT_PID" >/dev/null 2>&1 || true
	fi
	restore_fresh_cache
}
trap cleanup EXIT

export VOIDSCAPE_WORKBENCH_PORT="$WORKBENCH_PORT"
export VOIDSCAPE_WORKBENCH_DIR="$WORKBENCH_DIR"
if [[ "$SKIP_LOGIN" -eq 0 ]]; then
	export VOIDSCAPE_AUTO_LOGIN_USER="$AUTH_USER"
	export VOIDSCAPE_AUTO_LOGIN_PASS="$AUTH_PASS"
else
	unset VOIDSCAPE_AUTO_LOGIN_USER || true
	unset VOIDSCAPE_AUTO_LOGIN_PASS || true
fi

echo "==> Launching desktop workbench client"
echo "==> Client target: $CLIENT_HOST:$CLIENT_PORT"
echo "==> Workbench: http://127.0.0.1:$WORKBENCH_PORT"
echo "==> Output: $OUT_DIR"
if [[ "$FRESH_CACHE" == "1" ]]; then
	echo "==> Fresh-cache mode: temporarily clearing saved desktop accounts"
fi

prepare_fresh_cache
"$ROOT/scripts/run-workbench-client.sh" \
	--host "$CLIENT_HOST" \
	--port "$CLIENT_PORT" \
	--workbench-port "$WORKBENCH_PORT" \
	--out "$WORKBENCH_DIR" \
	>"$CLIENT_LOG" 2>&1 &
CLIENT_PID=$!

export OUT_DIR WORKBENCH_PORT CLIENT_PID CLIENT_HOST CLIENT_PORT AUTH_USER TIMEOUT_SECONDS PANELS SKIP_LOGIN CLIENT_LOG FRESH_CACHE
python3 - <<'PY'
import json
import os
import pathlib
import subprocess
import sys
import time
import urllib.error
import urllib.request

out_dir = pathlib.Path(os.environ["OUT_DIR"])
port = os.environ["WORKBENCH_PORT"]
client_pid = int(os.environ["CLIENT_PID"])
timeout = float(os.environ["TIMEOUT_SECONDS"])
panels = [p.strip() for p in os.environ["PANELS"].split(",") if p.strip()]
skip_login = os.environ["SKIP_LOGIN"] == "1"
base = f"http://127.0.0.1:{port}"

def process_alive(pid):
    return subprocess.run(["kill", "-0", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0

def request(method, path, payload=None, timeout_seconds=5):
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))

deadline = time.time() + timeout
last_error = None
health = None
while time.time() < deadline:
    if not process_alive(client_pid):
        print(f"ERROR: desktop client exited before workbench became ready. See {os.environ['CLIENT_LOG']}", file=sys.stderr)
        sys.exit(1)
    try:
        health = request("GET", "/health", timeout_seconds=1)
        if health.get("ok"):
            break
    except Exception as exc:
        last_error = exc
    time.sleep(0.5)
else:
    print(f"ERROR: workbench did not become ready: {last_error}. See {os.environ['CLIENT_LOG']}", file=sys.stderr)
    sys.exit(1)

captures = []
checks = {
    "health": health,
    "target": {
        "host": os.environ["CLIENT_HOST"],
        "port": int(os.environ["CLIENT_PORT"]),
    },
    "loginSkipped": skip_login,
    "freshCache": os.environ["FRESH_CACHE"] == "1",
}

def require_capture(capture, label):
    if capture.get("ok") is False:
        raise RuntimeError(f"{label} capture returned ok=false")
    png = pathlib.Path(capture["pngPath"])
    if not png.exists() or png.stat().st_size < 256:
        raise RuntimeError(f"{label} screenshot missing or empty: {png}")
    if int(capture.get("width", 0)) <= 0 or int(capture.get("height", 0)) <= 0:
        raise RuntimeError(f"{label} screenshot has invalid dimensions: {capture}")
    captures.append({
        "label": label,
        "pngPath": str(png),
        "statePath": capture.get("statePath"),
        "width": capture.get("width"),
        "height": capture.get("height"),
    })

if skip_login:
    # Give the login canvas a moment to draw before the visual capture.
    time.sleep(2)
    state = request("GET", "/state")
    checks["state"] = state
    login_capture = request("GET", "/screenshot", timeout_seconds=8)
    require_capture(login_capture, "login-screen")
else:
    ready = request("POST", "/dev/ready", {}, timeout_seconds=max(10, int(timeout)))
    if not ready.get("ok"):
        raise RuntimeError(f"/dev/ready failed: {ready}")
    state = ready.get("state") or {}
    player = state.get("player")
    if not player or not str(player.get("accountName", "")).strip():
        raise RuntimeError(f"Login did not produce a local player: {state}")
    checks["ready"] = ready
    checks["player"] = {
        "displayName": player.get("displayName"),
        "accountName": player.get("accountName"),
        "worldX": player.get("worldX"),
        "worldZ": player.get("worldZ"),
        "level": player.get("level"),
    }
    game_capture = request("GET", "/screenshot", timeout_seconds=8)
    require_capture(game_capture, "in-game")
    for panel in panels:
        panel_result = request("POST", "/dev/ui-panel", {"panel": panel}, timeout_seconds=12)
        if not panel_result.get("ok"):
            raise RuntimeError(f"Panel {panel!r} failed: {panel_result}")
        capture = panel_result.get("capture")
        if not capture:
            raise RuntimeError(f"Panel {panel!r} did not return a capture: {panel_result}")
        require_capture(capture, f"panel-{panel}")

manifest = {
    "ok": True,
    "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "workbenchUrl": base,
    "clientLog": os.environ["CLIENT_LOG"],
    "checks": checks,
    "captures": captures,
}
manifest_path = out_dir / "manifest.json"
manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(f"PC_CLIENT_PRELAUNCH_SMOKE_OK manifest={manifest_path}")
for capture in captures:
    print(f"PC_CLIENT_PRELAUNCH_CAPTURE {capture['label']} {capture['pngPath']}")
PY
