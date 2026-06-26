#!/usr/bin/env bash
# Open the TeaVM iPhone web client in iOS Simulator Safari.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
WEB_PORT="${WEB_TEA_SIM_PORT:-0}"
WS_PORT="${WEB_TEA_SIM_WS_PORT:-43496}"
HOST="${WEB_TEA_SIM_HOST:-127.0.0.1}"
BASE_URL="${WEB_TEA_SIM_BASE_URL:-}"
EXACT_URL="${WEB_TEA_SIM_URL:-}"
WS_URL="${WEB_TEA_SIM_WS_URL:-}"
PORTAL_URL="${WEB_TEA_SIM_PORTAL_URL:-/iphone-account/}"
PORTAL_ACCOUNT_URL="${WEB_TEA_SIM_PORTAL_ACCOUNT_URL:-}"
PORTAL_RECOVERY_URL="${WEB_TEA_SIM_PORTAL_RECOVERY_URL:-}"
DEVICE_NAME="${WEB_TEA_SIM_DEVICE:-iPhone 17 Pro}"
DEVICE_UDID="${WEB_TEA_SIM_UDID:-}"
OUT_DIR="${WEB_TEA_SIM_OUT:-$ROOT/tmp/web-teavm-simulator}"
DIAG=0
SCREENSHOT=1
OPEN_ONLY=0
EXIT_AFTER_OPEN=0
WAIT_SECONDS="${WEB_TEA_SIM_WAIT:-15}"
ORIENTATION_MATRIX="${WEB_TEA_SIM_ORIENTATION_MATRIX:-0}"
ORIENTATION_WAIT_SECONDS="${WEB_TEA_SIM_ORIENTATION_WAIT:-2}"
STABLE_STATUS_BAR="${WEB_TEA_SIM_STABLE_STATUS_BAR:-1}"
MANUAL_SECONDS="${WEB_TEA_SIM_MANUAL_SECONDS:-0}"
RECORD_VIDEO="${WEB_TEA_SIM_RECORD_VIDEO:-0}"
PASTEBOARD_TEXT="${WEB_TEA_SIM_PASTEBOARD_TEXT:-}"

usage() {
	cat <<'EOF'
Usage: scripts/run-web-teavm-iphone-simulator.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --url URL               Open an exact URL; skips local static server and URL generation.
  --base-url URL          Open an already served web root instead of starting a local static server.
  --web-port PORT         Local static-server port. Default: choose a free port.
  --host HOST             WebSocket host passed to the client. Default: 127.0.0.1.
  --ws-port PORT          WebSocket port passed to the client. Default: 43496.
  --ws URL                Full WebSocket URL passed as ?ws=.
  --portal URL            Portal base URL for Create Account/Recover account.
  --portal-account-url URL
                          Explicit Create Account portal URL.
  --portal-recovery-url URL
                          Explicit Recover account portal URL.
  --device NAME           iOS Simulator device name. Default: iPhone 17 Pro.
  --udid UDID             Exact Simulator UDID. Overrides --device.
  --out DIR               Output directory for screenshot and run metadata.
  --diag                  Open with diagnostics enabled.
  --no-screenshot         Do not capture a simulator screenshot.
  --no-stable-status-bar  Do not override the Simulator status bar for screenshots.
  --orientation-matrix    Capture Portrait, raw Landscape Right, and upright Landscape Right screenshots.
  --open-only             Open URL in an already-booted simulator; do not boot/wait/capture.
  --exit-after-open       In local-server mode, stop the static server after opening/capture.
  --wait SECONDS          Seconds to wait before screenshot. Default: 15.
  --orientation-wait SECONDS
                          Seconds to wait after changing Simulator orientation. Default: 2.
  --manual-seconds SECONDS
                          After the initial load wait, keep the Simulator open for manual interaction before screenshots.
  --record-video          Record the Simulator display to simulator-session.mov during the run.
  --pasteboard TEXT       Preload the iOS Simulator pasteboard before opening Safari.
  -h, --help              Show this help.

Default local mode builds the TeaVM output, starts a loopback static server,
boots the chosen iPhone Simulator, opens Mobile Safari, captures a screenshot,
then keeps the static server running until Ctrl-C.

Run scripts/run-server.sh in another terminal for real login/gameplay.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--url)
			EXACT_URL="${2:-}"
			BUILD=0
			shift 2
			;;
		--base-url)
			BASE_URL="${2:-}"
			BUILD=0
			shift 2
			;;
		--web-port)
			WEB_PORT="${2:-}"
			shift 2
			;;
		--host)
			HOST="${2:-}"
			shift 2
			;;
		--ws-port)
			WS_PORT="${2:-}"
			shift 2
			;;
		--ws)
			WS_URL="${2:-}"
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
		--device)
			DEVICE_NAME="${2:-}"
			shift 2
			;;
		--udid)
			DEVICE_UDID="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--diag)
			DIAG=1
			shift
			;;
		--no-screenshot)
			SCREENSHOT=0
			shift
			;;
		--no-stable-status-bar)
			STABLE_STATUS_BAR=0
			shift
			;;
		--orientation-matrix)
			ORIENTATION_MATRIX=1
			shift
			;;
		--open-only)
			OPEN_ONLY=1
			BUILD=0
			SCREENSHOT=0
			shift
			;;
		--exit-after-open)
			EXIT_AFTER_OPEN=1
			shift
			;;
		--wait)
			WAIT_SECONDS="${2:-}"
			shift 2
			;;
		--orientation-wait)
			ORIENTATION_WAIT_SECONDS="${2:-}"
			shift 2
			;;
		--manual-seconds)
			MANUAL_SECONDS="${2:-}"
			shift 2
			;;
		--record-video)
			RECORD_VIDEO=1
			shift
			;;
		--pasteboard)
			PASTEBOARD_TEXT="${2:-}"
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

if [[ -z "$HOST" || -z "$WS_PORT" || -z "$OUT_DIR" || -z "$WAIT_SECONDS" ]]; then
	echo "ERROR: host, ws-port, out, and wait must be non-empty." >&2
	exit 2
fi
if [[ -n "$EXACT_URL" && -n "$BASE_URL" ]]; then
	echo "ERROR: use either --url or --base-url, not both." >&2
	exit 2
fi
if ! [[ "$WAIT_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "ERROR: --wait must be a number of seconds." >&2
	exit 2
fi
if ! [[ "$ORIENTATION_WAIT_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "ERROR: --orientation-wait must be a number of seconds." >&2
	exit 2
fi
if ! [[ "$MANUAL_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
	echo "ERROR: --manual-seconds must be a number of seconds." >&2
	exit 2
fi
if [[ "$ORIENTATION_MATRIX" -eq 1 && "$SCREENSHOT" -eq 0 ]]; then
	echo "ERROR: --orientation-matrix requires screenshots; remove --no-screenshot." >&2
	exit 2
fi
if [[ "$STABLE_STATUS_BAR" != "0" && "$STABLE_STATUS_BAR" != "1" ]]; then
	echo "ERROR: WEB_TEA_SIM_STABLE_STATUS_BAR must be 0 or 1." >&2
	exit 2
fi
if [[ "$RECORD_VIDEO" != "0" && "$RECORD_VIDEO" != "1" ]]; then
	echo "ERROR: WEB_TEA_SIM_RECORD_VIDEO must be 0 or 1." >&2
	exit 2
fi
if [[ "$OPEN_ONLY" -eq 1 && ( "$RECORD_VIDEO" -eq 1 || ( "$MANUAL_SECONDS" != "0" && "$MANUAL_SECONDS" != "0.0" ) ) ]]; then
	echo "ERROR: --open-only cannot be combined with --record-video or --manual-seconds because it intentionally skips waits/capture." >&2
	exit 2
fi
if ! command -v xcrun >/dev/null 2>&1; then
	echo "ERROR: xcrun was not found. Install/select Xcode before using the iPhone Simulator." >&2
	exit 1
fi

REMOTE_MODE=0
if [[ -n "$BASE_URL" || -n "$EXACT_URL" ]]; then
	REMOTE_MODE=1
fi

if [[ "$REMOTE_MODE" -eq 0 && "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ "$REMOTE_MODE" -eq 0 && ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: missing TeaVM output at $TARGET/index.html. Run scripts/build-web-teavm-spike.sh first." >&2
	exit 1
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
rm -f \
	"$OUT_DIR/simulator-run.md" \
		"$OUT_DIR/simulator-run.json" \
		"$OUT_DIR/simulator-safari.png" \
		"$OUT_DIR/simulator-safari-portrait.png" \
		"$OUT_DIR/simulator-safari-landscape-right.png" \
		"$OUT_DIR/simulator-safari-landscape-right-upright.png" \
		"$OUT_DIR/simulator-session.mov" \
		"$OUT_DIR/simulator-session.log" \
		"$OUT_DIR/simulator-video-checks.json" \
		"$OUT_DIR/simulator-screenshot-checks.json" \
		"$OUT_DIR/simulator-http-checks.json" \
		"$OUT_DIR/simulator-home-screen-checklist.md" \
		"$OUT_DIR/http-server.log"

if [[ "$REMOTE_MODE" -eq 0 && "$WEB_PORT" == "0" ]]; then
	WEB_PORT="$(python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
)"
fi

TARGET_URL="$(python3 - "$BASE_URL" "$EXACT_URL" "$HOST" "$WS_PORT" "$WS_URL" "$PORTAL_URL" "$PORTAL_ACCOUNT_URL" "$PORTAL_RECOVERY_URL" "$DIAG" "$REMOTE_MODE" "$WEB_PORT" <<'PY'
from urllib.parse import urlencode, urljoin, urlparse, urlunparse
import sys

(
    base_url,
    exact_url,
    host,
    ws_port,
    ws_url,
    portal_url,
    portal_account_url,
    portal_recovery_url,
    diag,
    remote_mode,
    web_port,
) = [arg.strip() for arg in sys.argv[1:]]

if exact_url:
    parsed = urlparse(exact_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        print("ERROR: --url must be an absolute http(s) URL.", file=sys.stderr)
        raise SystemExit(2)
    print(exact_url)
    raise SystemExit

if base_url:
    parsed = urlparse(base_url)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        print("ERROR: --base-url must be an absolute http(s) URL.", file=sys.stderr)
        raise SystemExit(2)
    if parsed.query or parsed.fragment:
        print("ERROR: --base-url must not include query or fragment.", file=sys.stderr)
        raise SystemExit(2)
    if not parsed.path.endswith("/"):
        parsed = parsed._replace(path=(parsed.path or "") + "/")
    base = parsed.geturl()
else:
    base = f"http://127.0.0.1:{web_port}/"

index_url = urljoin(base, "index.html")
params = {"mobile": "1"}
if diag == "1":
    params["diag"] = "1"
if ws_url:
    ws_parsed = urlparse(ws_url)
    if ws_parsed.scheme not in {"ws", "wss"} or not ws_parsed.netloc:
        print("ERROR: --ws must be an absolute ws:// or wss:// URL.", file=sys.stderr)
        raise SystemExit(2)
    params["ws"] = ws_url
elif remote_mode != "1":
    params["host"] = host
    params["port"] = ws_port
if portal_url:
    params["portal"] = portal_url
if portal_account_url:
    params["portalAccountUrl"] = portal_account_url
if portal_recovery_url:
    params["portalRecoveryUrl"] = portal_recovery_url

print(index_url + "?" + urlencode(params))
PY
)"
HOME_SCREEN_URL="$(python3 - "$TARGET_URL" <<'PY'
from urllib.parse import urljoin
import sys

print(urljoin(sys.argv[1], "index.html?mobile=1"))
PY
)"

DEVICE_INFO="$(python3 - "$DEVICE_NAME" "$DEVICE_UDID" <<'PY'
import json
import subprocess
import sys

device_name = sys.argv[1].strip()
device_udid = sys.argv[2].strip()

try:
    raw = subprocess.check_output(["xcrun", "simctl", "list", "devices", "available", "--json"], text=True)
except subprocess.CalledProcessError as exc:
    print(f"ERROR: failed to list simulators: {exc}", file=sys.stderr)
    raise SystemExit(1)

devices_by_runtime = json.loads(raw).get("devices", {})
iphones = []
for runtime, devices in devices_by_runtime.items():
    if "iOS" not in runtime:
        continue
    for device in devices:
        if device.get("isAvailable") and device.get("name", "").startswith("iPhone"):
            iphones.append(
                {
                    "name": device.get("name", ""),
                    "udid": device.get("udid", ""),
                    "state": device.get("state", ""),
                    "runtime": runtime,
                }
            )

chosen = None
if device_udid:
    chosen = next((device for device in iphones if device["udid"] == device_udid), None)
    if not chosen:
        print(f"ERROR: no available iPhone Simulator has UDID {device_udid}", file=sys.stderr)
        raise SystemExit(1)
elif device_name:
    chosen = next((device for device in iphones if device["name"] == device_name), None)
    if not chosen:
        chosen = next((device for device in iphones if device_name.lower() in device["name"].lower()), None)

if not chosen and iphones:
    chosen = iphones[0]

if not chosen:
    print("ERROR: no available iPhone Simulator device was found.", file=sys.stderr)
    raise SystemExit(1)

print(json.dumps(chosen, sort_keys=True))
PY
)"
DEVICE_NAME_ACTUAL="$(python3 - "$DEVICE_INFO" <<'PY'
import json
import sys
print(json.loads(sys.argv[1])["name"])
PY
)"
DEVICE_UDID_ACTUAL="$(python3 - "$DEVICE_INFO" <<'PY'
import json
import sys
print(json.loads(sys.argv[1])["udid"])
PY
)"

HTTP_LOG="$OUT_DIR/http-server.log"
HTTP_PID=
STATUS_BAR_OVERRIDDEN=0
VIDEO_PID=
VIDEO_PATH=
VIDEO_LOG=
if [[ "$REMOTE_MODE" -eq 0 ]]; then
	python3 -u -m http.server "$WEB_PORT" --bind 127.0.0.1 -d "$TARGET" >"$HTTP_LOG" 2>&1 &
	HTTP_PID=$!
fi

cleanup() {
	if [[ "${VIDEO_PID:-}" =~ ^[0-9]+$ ]] && kill -0 "$VIDEO_PID" >/dev/null 2>&1; then
		kill -INT "$VIDEO_PID" >/dev/null 2>&1 || true
		wait "$VIDEO_PID" >/dev/null 2>&1 || true
	fi
	if [[ "${STATUS_BAR_OVERRIDDEN:-0}" -eq 1 ]]; then
		xcrun simctl status_bar "$DEVICE_UDID_ACTUAL" clear >/dev/null 2>&1 || true
	fi
	if [[ "${HTTP_PID:-}" =~ ^[0-9]+$ ]]; then
		kill "$HTTP_PID" >/dev/null 2>&1 || true
		wait "$HTTP_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

if [[ "$REMOTE_MODE" -eq 0 && ( "$SCREENSHOT" -eq 1 || "$RECORD_VIDEO" -eq 1 || ( "$MANUAL_SECONDS" != "0" && "$MANUAL_SECONDS" != "0.0" ) ) ]]; then
	python3 - "$WEB_PORT" <<'PY'
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
print(f"ERROR: static web server did not become ready: {last_error}", file=sys.stderr)
raise SystemExit(1)
PY
fi

if [[ "$OPEN_ONLY" -eq 0 ]]; then
	open -a Simulator --args -CurrentDeviceUDID "$DEVICE_UDID_ACTUAL" >/dev/null 2>&1 || true
	xcrun simctl boot "$DEVICE_UDID_ACTUAL" >/dev/null 2>&1 || true
	xcrun simctl bootstatus "$DEVICE_UDID_ACTUAL" -b >/dev/null
else
	BOOTED_COUNT="$(xcrun simctl list devices booted | grep -c "$DEVICE_UDID_ACTUAL" || true)"
	if [[ "$BOOTED_COUNT" -eq 0 ]]; then
		echo "ERROR: --open-only requires the selected simulator to already be booted." >&2
		exit 1
	fi
fi

if [[ "$SCREENSHOT" -eq 1 && "$STABLE_STATUS_BAR" -eq 1 ]]; then
	if ! xcrun simctl status_bar "$DEVICE_UDID_ACTUAL" override \
		--time 9:41 \
		--dataNetwork wifi \
		--wifiMode active \
		--wifiBars 3 \
		--cellularMode notSupported \
		--batteryState charged \
		--batteryLevel 100 >/dev/null; then
		echo "ERROR: failed to set a stable Simulator status bar. Re-run with --no-stable-status-bar to keep the live Simulator status bar." >&2
		exit 1
	fi
	STATUS_BAR_OVERRIDDEN=1
fi

if [[ -n "$PASTEBOARD_TEXT" ]]; then
	printf "%s" "$PASTEBOARD_TEXT" | xcrun simctl pbcopy "$DEVICE_UDID_ACTUAL"
fi

if [[ "$RECORD_VIDEO" -eq 1 ]]; then
	VIDEO_PATH="$OUT_DIR/simulator-session.mov"
	VIDEO_LOG="$OUT_DIR/simulator-session.log"
	xcrun simctl io "$DEVICE_UDID_ACTUAL" recordVideo --force "$VIDEO_PATH" >"$VIDEO_LOG" 2>&1 &
	VIDEO_PID=$!
	sleep 1
	if ! kill -0 "$VIDEO_PID" >/dev/null 2>&1; then
		echo "ERROR: failed to start Simulator video recording. See $VIDEO_LOG." >&2
		wait "$VIDEO_PID" >/dev/null 2>&1 || true
		VIDEO_PID=
		exit 1
	fi
fi

xcrun simctl launch "$DEVICE_UDID_ACTUAL" com.apple.mobilesafari >/dev/null 2>&1 || true
python3 - "$DEVICE_UDID_ACTUAL" "$TARGET_URL" <<'PY'
import subprocess
import sys
import time

udid, url = sys.argv[1:]
last_error = None
for attempt in range(1, 4):
    try:
        subprocess.run(
            ["xcrun", "simctl", "openurl", udid, url],
            check=True,
            timeout=15,
        )
        raise SystemExit(0)
    except subprocess.TimeoutExpired as exc:
        print(
            "WARN: simctl openurl timed out after sending the URL; "
            "continuing so the screenshot can show the actual Simulator state.",
            file=sys.stderr,
        )
        raise SystemExit(0)
    except subprocess.CalledProcessError as exc:
        last_error = exc
        if attempt < 3:
            time.sleep(2)

print(f"ERROR: failed to open Simulator URL after retries: {last_error}", file=sys.stderr)
raise SystemExit(1)
PY

set_simulator_orientation() {
	local orientation="$1"
	osascript - "$orientation" >/dev/null <<'APPLESCRIPT'
on run argv
	set targetOrientation to item 1 of argv
	tell application "Simulator" to activate
	delay 0.2
	tell application "System Events"
		tell process "Simulator"
			tell menu bar item "Device" of menu bar 1
				tell menu 1
					tell menu item "Orientation"
						tell menu 1
							click menu item targetOrientation
						end tell
					end tell
				end tell
			end tell
		end tell
	end tell
end run
APPLESCRIPT
}

SCREENSHOT_PATH=
PORTRAIT_SCREENSHOT_PATH=
LANDSCAPE_SCREENSHOT_PATH=
LANDSCAPE_UPRIGHT_SCREENSHOT_PATH=
SCREENSHOT_CHECKS_PATH=
VIDEO_CHECKS_PATH=
HTTP_CHECKS_PATH=
if [[ "$SCREENSHOT" -eq 1 || "$RECORD_VIDEO" -eq 1 || ( "$MANUAL_SECONDS" != "0" && "$MANUAL_SECONDS" != "0.0" ) ]]; then
	sleep "$WAIT_SECONDS"
	if [[ "$MANUAL_SECONDS" != "0" && "$MANUAL_SECONDS" != "0.0" ]]; then
		cat <<EOF
==> Manual Simulator interaction window
    Duration:   ${MANUAL_SECONDS}s
    Suggested:  use Aa for keyboard, log in, close dialogs, move, chat, try context/camera, then rotate if needed.
EOF
		sleep "$MANUAL_SECONDS"
	fi
fi
if [[ "$SCREENSHOT" -eq 1 ]]; then
	if [[ "$ORIENTATION_MATRIX" -eq 1 ]]; then
		if ! set_simulator_orientation "Portrait"; then
			echo "ERROR: failed to set Simulator orientation to Portrait. macOS may need Accessibility permission for Simulator UI scripting." >&2
			exit 1
		fi
		sleep "$ORIENTATION_WAIT_SECONDS"
		PORTRAIT_SCREENSHOT_PATH="$OUT_DIR/simulator-safari-portrait.png"
		xcrun simctl io "$DEVICE_UDID_ACTUAL" screenshot "$PORTRAIT_SCREENSHOT_PATH" >/dev/null

		if ! set_simulator_orientation "Landscape Right"; then
			echo "ERROR: failed to set Simulator orientation to Landscape Right. macOS may need Accessibility permission for Simulator UI scripting." >&2
			exit 1
		fi
		sleep "$ORIENTATION_WAIT_SECONDS"
		LANDSCAPE_SCREENSHOT_PATH="$OUT_DIR/simulator-safari-landscape-right.png"
		xcrun simctl io "$DEVICE_UDID_ACTUAL" screenshot "$LANDSCAPE_SCREENSHOT_PATH" >/dev/null
		LANDSCAPE_UPRIGHT_SCREENSHOT_PATH="$OUT_DIR/simulator-safari-landscape-right-upright.png"
		if ! command -v sips >/dev/null 2>&1; then
			echo "ERROR: sips was not found; cannot write upright landscape screenshot." >&2
			exit 1
		fi
		sips -r 90 "$LANDSCAPE_SCREENSHOT_PATH" --out "$LANDSCAPE_UPRIGHT_SCREENSHOT_PATH" >/dev/null
		SCREENSHOT_PATH="$PORTRAIT_SCREENSHOT_PATH"

		if ! set_simulator_orientation "Portrait"; then
			echo "WARN: failed to restore Simulator orientation to Portrait." >&2
		fi
	else
		SCREENSHOT_PATH="$OUT_DIR/simulator-safari.png"
		xcrun simctl io "$DEVICE_UDID_ACTUAL" screenshot "$SCREENSHOT_PATH" >/dev/null
	fi
fi

SCREENSHOT_ARGS=()
if [[ -n "$PORTRAIT_SCREENSHOT_PATH" ]]; then
	SCREENSHOT_ARGS+=("portrait=$PORTRAIT_SCREENSHOT_PATH")
fi
if [[ -n "$LANDSCAPE_SCREENSHOT_PATH" ]]; then
	SCREENSHOT_ARGS+=("landscapeRight=$LANDSCAPE_SCREENSHOT_PATH")
fi
if [[ -n "$LANDSCAPE_UPRIGHT_SCREENSHOT_PATH" ]]; then
	SCREENSHOT_ARGS+=("landscapeRightUpright=$LANDSCAPE_UPRIGHT_SCREENSHOT_PATH")
fi
if [[ "${#SCREENSHOT_ARGS[@]}" -eq 0 && -n "$SCREENSHOT_PATH" ]]; then
	SCREENSHOT_ARGS+=("screenshot=$SCREENSHOT_PATH")
fi
if [[ "${#SCREENSHOT_ARGS[@]}" -gt 0 ]]; then
	SCREENSHOT_CHECKS_PATH="$OUT_DIR/simulator-screenshot-checks.json"
	python3 - "$SCREENSHOT_CHECKS_PATH" "${SCREENSHOT_ARGS[@]}" <<'PY'
from __future__ import annotations

import json
import struct
import sys
import zlib
from pathlib import Path


def fail(message: str) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def read_png(path: Path) -> tuple[int, int, int, list[bytes]]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        fail(f"{path} is not a PNG file")
    pos = 8
    width = height = bit_depth = color_type = None
    idat: list[bytes] = []
    while pos < len(data):
        if pos + 8 > len(data):
            fail(f"{path} has a truncated PNG chunk header")
        length = struct.unpack(">I", data[pos:pos + 4])[0]
        pos += 4
        chunk_type = data[pos:pos + 4]
        pos += 4
        chunk = data[pos:pos + length]
        pos += length + 4
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, compression, png_filter, interlace = struct.unpack(">IIBBBBB", chunk)
            if bit_depth != 8 or compression != 0 or png_filter != 0 or interlace != 0:
                fail(f"{path} uses unsupported PNG settings")
        elif chunk_type == b"IDAT":
            idat.append(chunk)
        elif chunk_type == b"IEND":
            break
    if width is None or height is None or color_type is None:
        fail(f"{path} is missing PNG IHDR")
    channels_by_type = {0: 1, 2: 3, 4: 2, 6: 4}
    if color_type not in channels_by_type:
        fail(f"{path} uses unsupported PNG color type {color_type}")
    channels = channels_by_type[color_type]
    raw = zlib.decompress(b"".join(idat))
    stride = width * channels
    previous = bytearray(stride)
    rows: list[bytes] = []
    offset = 0
    for _ in range(height):
        filter_type = raw[offset]
        offset += 1
        current = bytearray(raw[offset:offset + stride])
        offset += stride
        for i in range(stride):
            left = current[i - channels] if i >= channels else 0
            up = previous[i]
            up_left = previous[i - channels] if i >= channels else 0
            if filter_type == 1:
                current[i] = (current[i] + left) & 255
            elif filter_type == 2:
                current[i] = (current[i] + up) & 255
            elif filter_type == 3:
                current[i] = (current[i] + ((left + up) // 2)) & 255
            elif filter_type == 4:
                prediction = left + up - up_left
                left_distance = abs(prediction - left)
                up_distance = abs(prediction - up)
                up_left_distance = abs(prediction - up_left)
                predictor = left if left_distance <= up_distance and left_distance <= up_left_distance else up if up_distance <= up_left_distance else up_left
                current[i] = (current[i] + predictor) & 255
            elif filter_type != 0:
                fail(f"{path} uses unsupported PNG row filter {filter_type}")
        rows.append(bytes(current))
        previous = current
    return width, height, channels, rows


def screenshot_metrics(path: Path) -> dict[str, object]:
    width, height, channels, rows = read_png(path)
    step_x = max(1, width // 80)
    step_y = max(1, height // 120)
    total = nonblack = colorful = 0
    buckets: set[tuple[int, int, int]] = set()
    for y in range(0, height, step_y):
        row = rows[y]
        for x in range(0, width, step_x):
            index = x * channels
            if channels == 1:
                r = g = b = row[index]
            else:
                r, g, b = row[index], row[index + 1], row[index + 2]
            total += 1
            maximum = max(r, g, b)
            minimum = min(r, g, b)
            if maximum > 24:
                nonblack += 1
            if maximum > 40 and maximum - minimum > 30:
                colorful += 1
            buckets.add((r // 32, g // 32, b // 32))
    return {
        "path": str(path),
        "width": width,
        "height": height,
        "sampleCount": total,
        "nonblackRatio": round(nonblack / total, 6) if total else 0,
        "colorfulRatio": round(colorful / total, 6) if total else 0,
        "colorBuckets": len(buckets),
    }


out_path = Path(sys.argv[1])
checks = []
errors = []
for raw in sys.argv[2:]:
    label, value = raw.split("=", 1)
    path = Path(value)
    metrics = screenshot_metrics(path)
    metrics["label"] = label
    passed = (
        metrics["width"] >= 300
        and metrics["height"] >= 300
        and metrics["nonblackRatio"] >= 0.08
        and metrics["colorfulRatio"] >= 0.01
        and metrics["colorBuckets"] >= 16
    )
    metrics["passed"] = passed
    checks.append(metrics)
    if not passed:
        errors.append(
            f"{label} screenshot does not look like rendered game content: "
            f"{metrics}"
        )

out_path.write_text(json.dumps({"checks": checks}, indent=2) + "\n", encoding="utf-8")
for check in checks:
    print(
        "Screenshot check "
        f"{check['label']}: {check['width']}x{check['height']}, "
        f"colorfulRatio={check['colorfulRatio']}, passed={check['passed']}"
    )
if errors:
    for error in errors:
        print(f"ERROR: {error}", file=sys.stderr)
    raise SystemExit(1)
PY
fi

if [[ "${VIDEO_PID:-}" =~ ^[0-9]+$ ]] && kill -0 "$VIDEO_PID" >/dev/null 2>&1; then
	kill -INT "$VIDEO_PID" >/dev/null 2>&1 || true
	wait "$VIDEO_PID" >/dev/null 2>&1 || true
	VIDEO_PID=
fi

if [[ "$RECORD_VIDEO" -eq 1 ]]; then
	VIDEO_CHECKS_PATH="$OUT_DIR/simulator-video-checks.json"
	python3 - "$VIDEO_PATH" "$VIDEO_LOG" "$VIDEO_CHECKS_PATH" "$WAIT_SECONDS" "$MANUAL_SECONDS" <<'PY'
from __future__ import annotations

import json
import sys
from pathlib import Path

video_path = Path(sys.argv[1])
log_path = Path(sys.argv[2])
out_path = Path(sys.argv[3])
wait_seconds = sys.argv[4]
manual_seconds = sys.argv[5]
minimum_bytes = 4096

failures: list[str] = []
exists = video_path.exists()
size = video_path.stat().st_size if exists else 0
header = video_path.read_bytes()[:16] if exists else b""
quicktime_like = len(header) >= 12 and header[4:8] == b"ftyp"
if not exists:
    failures.append(f"video file was not created: {video_path}")
if size < minimum_bytes:
    failures.append(f"video file is too small: {size} bytes")
if exists and not quicktime_like:
    failures.append("video file does not look like an ISO/QuickTime movie")

result = {
    "path": str(video_path),
    "log": str(log_path),
    "exists": exists,
    "sizeBytes": size,
    "minimumBytes": minimum_bytes,
    "quicktimeLike": quicktime_like,
    "waitSeconds": wait_seconds,
    "manualSeconds": manual_seconds,
    "failures": failures,
}
out_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(
    f"Video check: sizeBytes={size}, quicktimeLike={quicktime_like}, "
    f"failures={len(failures)}"
)
if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)
PY
fi

if [[ "$REMOTE_MODE" -eq 0 && ( "$SCREENSHOT" -eq 1 || "$RECORD_VIDEO" -eq 1 || ( "$MANUAL_SECONDS" != "0" && "$MANUAL_SECONDS" != "0.0" ) ) ]]; then
	HTTP_CHECKS_PATH="$OUT_DIR/simulator-http-checks.json"
	python3 - "$HTTP_LOG" "$HTTP_CHECKS_PATH" <<'PY'
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from urllib.parse import urlparse

log_path = Path(sys.argv[1])
out_path = Path(sys.argv[2])

required_paths = (
    "/index.html",
    "/manifest.webmanifest",
    "/voidscape-web-client.js",
    "/Cache/video/library.orsc",
    "/Cache/video/Authentic_Sprites.orsc",
    "/Cache/video/Custom_Landscape.orsc",
    "/Cache/login/voidscape-login-background.png",
)

request_pattern = re.compile(r'"GET ([^" ]+) HTTP/[^"]+" ([0-9]{3}) ')
requests: list[dict[str, object]] = []
failures: list[str] = []
for line in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
    match = request_pattern.search(line)
    if not match:
        continue
    raw_url, status_text = match.groups()
    status = int(status_text)
    path = urlparse(raw_url).path
    requests.append({"path": path, "status": status})
    if status >= 400:
        failures.append(f"{path} returned HTTP {status}")

required = []
for path in required_paths:
    matches = [request for request in requests if request["path"] == path and int(request["status"]) < 400]
    required.append({"path": path, "matched": bool(matches), "status": matches[-1]["status"] if matches else None})
    if not matches:
        failures.append(f"required Simulator Safari request was not observed: {path}")

result = {
    "log": str(log_path),
    "required": required,
    "requestCount": len(requests),
    "failures": failures,
}
out_path.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(f"HTTP asset check: {len(requests)} requests observed, failures={len(failures)}")
if failures:
    for failure in failures:
        print(f"ERROR: {failure}", file=sys.stderr)
    raise SystemExit(1)
PY
fi

RUN_REPORT="$OUT_DIR/simulator-run.md"
RUN_METADATA="$OUT_DIR/simulator-run.json"
HOME_SCREEN_CHECKLIST="$OUT_DIR/simulator-home-screen-checklist.md"
cat > "$HOME_SCREEN_CHECKLIST" <<EOF
# Voidscape Simulator Home Screen Iteration Checklist

Created: $(date -u '+%Y-%m-%dT%H:%M:%SZ')

This is a simulator-only checklist for faster UI iteration. It is useful for
checking viewport, safe-area, keyboard, orientation, and overlay-control feel
closer to the Home Screen path, but it is not release proof. Final release
still requires the physical iPhone QA report.

## URLs

- Initial $([[ "$DIAG" -eq 1 ]] && echo "diagnostics" || echo "player") URL: <$TARGET_URL>
- Expected Home Screen URL after saved state: <$HOME_SCREEN_URL>

## Steps
EOF

if [[ "$DIAG" -eq 1 ]]; then
	cat >> "$HOME_SCREEN_CHECKLIST" <<EOF
1. Open the initial diagnostics URL in Simulator Safari.
2. Wait for the Voidscape login art and the \`i\` / \`copy\` diagnostics controls.
3. Tap Share, then Add to Home Screen, then Add.
4. Launch the new Voidscape icon from the Simulator Home Screen.
5. Confirm the game opens without Safari address/tab chrome.
6. Open diagnostics with \`i\`, tap \`copy\`, and paste/save the JSON with this run.
7. Background the Home Screen app, return, then tap-to-move or chat once.
8. Rotate through portrait and landscape, then copy diagnostics again.
9. If \`scripts/run-server.sh\` is running, log in and test \`Aa\`, \`...\`, camera, context menu, movement, chat, and the custom HUD.

## Expected Simulator Diagnostics

- \`touchProfile: true\`
- \`standalone: true\` if launched from the Home Screen icon
- \`diagnostics.enabled: true\`
- \`diagnostics.source: "stored"\` after the initial diagnostics URL was opened once
- \`endpoint.source: "stored"\` when the Home Screen URL omits explicit endpoint query parameters
- \`href\` should not include \`diag=1\` or \`debug=1\` on the Home Screen launch
- \`postResumeProof.inputAfterResume: true\` and \`postResumeProof.movementAfterResume: true\` after post-resume gameplay
- \`controlsHistory.portrait\` and \`controlsHistory.landscape\` should both exist after rotation
- \`recentErrors: []\` after a successful login and normal interaction
EOF
else
	cat >> "$HOME_SCREEN_CHECKLIST" <<EOF
1. Open the initial player URL in Simulator Safari.
2. Wait for the Voidscape login art and the \`Aa\` keyboard control.
3. Use this run for visual viewport, safe-area, login-art, and basic Safari chrome inspection.
4. For stored-diagnostics or Home Screen QA iteration, rerun this helper with \`--diag\`, then use that diagnostics URL before adding to Home Screen.
5. If \`scripts/run-server.sh\` is running, log in and test \`Aa\`, \`...\`, camera, context menu, movement, chat, and the custom HUD.

## Expected Simulator State

- \`touchProfile: true\`
- Diagnostics controls are intentionally hidden unless diagnostics were already stored from a previous \`--diag\` launch.
- The screenshot should show the Voidscape login art, the mobile \`Aa\` control, and no Safari page scrolling.
- This non-diagnostics run is not enough to produce \`diagnostics.source: "stored"\`, \`postResumeProof\`, \`controlsHistory\`, \`uiHistory\`, or \`scrollHistory\` evidence.
EOF
fi

cat >> "$HOME_SCREEN_CHECKLIST" <<EOF

## Caveats

- Simulator Safari screenshots from this script are Mobile Safari browser captures, so Safari chrome/tabs may be visible.
- Direct \`simctl launch com.apple.webapp <url>\` is not enough without an installed webclip; it can launch a blank Web shell.
- Real iPhone touch feel, performance, hardware keyboard behavior, and standalone/Home Screen release proof still need the physical-device report.
EOF

cat > "$RUN_REPORT" <<EOF
# Voidscape iPhone Simulator Run

Created: $(date -u '+%Y-%m-%dT%H:%M:%SZ')

- Device: \`$DEVICE_NAME_ACTUAL\`
- UDID: \`$DEVICE_UDID_ACTUAL\`
- URL: <$TARGET_URL>
- Web root: \`$([[ "$REMOTE_MODE" -eq 0 ]] && echo "$TARGET" || echo "${BASE_URL:-exact URL}")\`
- Static server: \`$([[ "$REMOTE_MODE" -eq 0 ]] && echo "127.0.0.1:$WEB_PORT" || echo "not started")\`
- WebSocket hint: \`${WS_URL:-$HOST:$WS_PORT}\`
- Diagnostics mode: \`$([[ "$DIAG" -eq 1 ]] && echo enabled || echo disabled)\`
- Capture surface: \`Mobile Safari browser in iOS Simulator; Safari chrome/tabs may be visible and this is not Home Screen/PWA proof\`
- Orientation matrix: \`$([[ "$ORIENTATION_MATRIX" -eq 1 ]] && echo enabled || echo disabled)\`
- Orientation settle wait: \`${ORIENTATION_WAIT_SECONDS}s\`
- Manual interaction wait: \`${MANUAL_SECONDS}s\`
- Pasteboard preloaded: \`$([[ -n "$PASTEBOARD_TEXT" ]] && echo yes || echo no)\`
- Video recording: \`${VIDEO_PATH:-not captured}\`
- Video log: \`${VIDEO_LOG:-not captured}\`
- Video checks: \`${VIDEO_CHECKS_PATH:-not captured}\`
- Stable status bar: \`$([[ "$SCREENSHOT" -eq 1 && "$STABLE_STATUS_BAR" -eq 1 ]] && echo "enabled (9:41, Wi-Fi, 100% charged)" || echo "not applied")\`
- Screenshot: \`${SCREENSHOT_PATH:-not captured}\`
- Portrait screenshot: \`${PORTRAIT_SCREENSHOT_PATH:-not captured}\`
- Landscape screenshot: \`${LANDSCAPE_SCREENSHOT_PATH:-not captured}\`
- Landscape upright screenshot: \`${LANDSCAPE_UPRIGHT_SCREENSHOT_PATH:-not captured}\`
- Screenshot checks: \`${SCREENSHOT_CHECKS_PATH:-not captured}\`
- HTTP asset checks: \`${HTTP_CHECKS_PATH:-not captured}\`
- Simulator Home Screen checklist: \`$HOME_SCREEN_CHECKLIST\`
- HTTP log: \`$([[ "$REMOTE_MODE" -eq 0 ]] && echo "$HTTP_LOG" || echo "not captured")\`

Run \`scripts/run-server.sh\` in another terminal for real login/gameplay.
For manual UI iteration, rerun with \`--record-video --manual-seconds 90\` and interact with the Simulator while the timer runs.
For closer fullscreen iteration, follow \`$HOME_SCREEN_CHECKLIST\` after the initial diagnostics URL opens.
Use the physical iPhone QA report for final standalone/Home Screen evidence.
EOF

python3 - \
	"$RUN_METADATA" \
	"$DEVICE_INFO" \
	"$TARGET_URL" \
	"$HOME_SCREEN_URL" \
	"$([[ "$REMOTE_MODE" -eq 0 ]] && echo "$TARGET" || echo "${BASE_URL:-exact URL}")" \
	"$([[ "$REMOTE_MODE" -eq 0 ]] && echo "127.0.0.1:$WEB_PORT" || echo "not started")" \
	"$REMOTE_MODE" \
	"$BASE_URL" \
	"$EXACT_URL" \
	"$HOST" \
	"$WS_PORT" \
	"$WS_URL" \
	"$PORTAL_URL" \
	"$PORTAL_ACCOUNT_URL" \
	"$PORTAL_RECOVERY_URL" \
	"$DIAG" \
	"$SCREENSHOT" \
	"$ORIENTATION_MATRIX" \
	"$WAIT_SECONDS" \
	"$ORIENTATION_WAIT_SECONDS" \
	"$MANUAL_SECONDS" \
	"$RECORD_VIDEO" \
	"$([[ -n "$PASTEBOARD_TEXT" ]] && echo 1 || echo 0)" \
	"$([[ "$SCREENSHOT" -eq 1 && "$STABLE_STATUS_BAR" -eq 1 ]] && echo 1 || echo 0)" \
	"$OPEN_ONLY" \
	"$EXIT_AFTER_OPEN" \
	"$RUN_REPORT" \
	"$HOME_SCREEN_CHECKLIST" \
	"${VIDEO_PATH:-}" \
	"${VIDEO_LOG:-}" \
	"${VIDEO_CHECKS_PATH:-}" \
	"${SCREENSHOT_PATH:-}" \
	"${PORTRAIT_SCREENSHOT_PATH:-}" \
	"${LANDSCAPE_SCREENSHOT_PATH:-}" \
	"${LANDSCAPE_UPRIGHT_SCREENSHOT_PATH:-}" \
	"${SCREENSHOT_CHECKS_PATH:-}" \
	"${HTTP_CHECKS_PATH:-}" \
	"$([[ "$REMOTE_MODE" -eq 0 ]] && echo "$HTTP_LOG" || echo "")" <<'PY'
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path


(
    out_path,
    device_info_json,
    target_url,
    home_screen_url,
    web_root,
    static_server,
    remote_mode,
    base_url,
    exact_url,
    host,
    ws_port,
    ws_url,
    portal_url,
    portal_account_url,
    portal_recovery_url,
    diag,
    screenshot,
    orientation_matrix,
    wait_seconds,
    orientation_wait_seconds,
    manual_seconds,
    record_video,
    pasteboard_preloaded,
    stable_status_bar_applied,
    open_only,
    exit_after_open,
    run_report,
    home_screen_checklist,
    video_path,
    video_log,
    video_checks_path,
    screenshot_path,
    portrait_screenshot_path,
    landscape_screenshot_path,
    landscape_upright_screenshot_path,
    screenshot_checks_path,
    http_checks_path,
    http_log,
) = sys.argv[1:]

metadata = {
    "schema": "voidscape.iphoneSimulatorRun.v1",
    "createdAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "device": json.loads(device_info_json),
    "targetUrl": target_url,
    "homeScreenUrl": home_screen_url,
    "webRoot": web_root,
    "staticServer": static_server,
    "remoteMode": remote_mode == "1",
    "baseUrl": base_url,
    "exactUrl": exact_url,
    "webSocket": {
        "host": host,
        "port": ws_port,
        "url": ws_url,
        "hint": ws_url or f"{host}:{ws_port}",
    },
    "portal": {
        "portalUrl": portal_url,
        "portalAccountUrl": portal_account_url,
        "portalRecoveryUrl": portal_recovery_url,
    },
    "diagnosticsMode": diag == "1",
    "screenshotEnabled": screenshot == "1",
    "orientationMatrix": orientation_matrix == "1",
    "waitSeconds": wait_seconds,
    "orientationWaitSeconds": orientation_wait_seconds,
    "manualSeconds": manual_seconds,
    "recordVideo": record_video == "1",
    "pasteboardPreloaded": pasteboard_preloaded == "1",
    "stableStatusBarApplied": stable_status_bar_applied == "1",
    "openOnly": open_only == "1",
    "exitAfterOpen": exit_after_open == "1",
    "captureSurface": "Mobile Safari browser in iOS Simulator; not Home Screen/PWA release proof",
    "artifacts": {
        "runReport": run_report,
        "homeScreenChecklist": home_screen_checklist,
        "video": video_path,
        "videoLog": video_log,
        "videoChecks": video_checks_path,
        "screenshot": screenshot_path,
        "portraitScreenshot": portrait_screenshot_path,
        "landscapeScreenshot": landscape_screenshot_path,
        "landscapeUprightScreenshot": landscape_upright_screenshot_path,
        "screenshotChecks": screenshot_checks_path,
        "httpChecks": http_checks_path,
        "httpLog": http_log,
    },
}

Path(out_path).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY

cat <<EOF
==> Opened Voidscape in iPhone Simulator Safari
    Device:     $DEVICE_NAME_ACTUAL
    UDID:       $DEVICE_UDID_ACTUAL
    URL:        $TARGET_URL
    Report:     $RUN_REPORT
    Metadata:   $RUN_METADATA
    Surface:    Mobile Safari browser (not Home Screen/PWA proof)
    Diagnostics:$([[ "$DIAG" -eq 1 ]] && echo " enabled" || echo " disabled")
    Manual:    ${MANUAL_SECONDS}s
    Video:      ${VIDEO_PATH:-not captured}
    Video chk:  ${VIDEO_CHECKS_PATH:-not captured}
    Status bar: $([[ "$SCREENSHOT" -eq 1 && "$STABLE_STATUS_BAR" -eq 1 ]] && echo "stable 9:41/Wi-Fi/100%" || echo "not applied")
    Home Screen checklist: $HOME_SCREEN_CHECKLIST
EOF

if [[ -n "$SCREENSHOT_PATH" ]]; then
	cat <<EOF
    Screenshot: $SCREENSHOT_PATH
EOF
fi
if [[ -n "$PORTRAIT_SCREENSHOT_PATH" || -n "$LANDSCAPE_SCREENSHOT_PATH" ]]; then
	cat <<EOF
    Portrait:   ${PORTRAIT_SCREENSHOT_PATH:-not captured}
    Landscape:  ${LANDSCAPE_SCREENSHOT_PATH:-not captured}
    Upright:    ${LANDSCAPE_UPRIGHT_SCREENSHOT_PATH:-not captured}
EOF
fi

if [[ "$REMOTE_MODE" -eq 0 ]]; then
	if lsof -nP -iTCP:"$WS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
		echo "    WebSocket:  listening on TCP $WS_PORT"
	else
		echo "    WebSocket:  not listening on TCP $WS_PORT; login will fail until scripts/run-server.sh is running"
	fi
	if [[ -n "$HTTP_CHECKS_PATH" ]]; then
		echo "    HTTP check: $HTTP_CHECKS_PATH"
	fi
	cat <<EOF
    Static:     http://127.0.0.1:$WEB_PORT/
EOF
fi

if [[ "$REMOTE_MODE" -eq 0 && "$EXIT_AFTER_OPEN" -eq 0 ]]; then
	cat <<'EOF'

Static server is still running for Simulator iteration. Press Ctrl-C to stop it.
EOF
	wait "$HTTP_PID"
fi
