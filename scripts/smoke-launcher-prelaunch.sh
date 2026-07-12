#!/usr/bin/env bash
# Screenshot-backed prelaunch smoke for the desktop launcher.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${VOIDSCAPE_LAUNCHER_SMOKE_OUT:-$ROOT/tmp/launcher-prelaunch-smoke-$(date +%Y%m%d-%H%M%S)}"
GAME_HOST="${VOIDSCAPE_LAUNCHER_SMOKE_HOST:-voidscape.gg}"
GAME_PORT="${VOIDSCAPE_LAUNCHER_SMOKE_PORT:-43596}"
PORTAL_URL="${VOIDSCAPE_LAUNCHER_SMOKE_PORTAL_URL:-https://voidscape.gg}"
JAR_PATH="${VOIDSCAPE_LAUNCHER_SMOKE_JAR:-$ROOT/PC_Launcher/OpenRSC.jar}"
BUILD=1
DELAY_MS="${VOIDSCAPE_LAUNCHER_SMOKE_DELAY_MS:-2600}"
USE_PACKAGED_CONFIG=0

usage() {
	cat <<'EOF'
Usage: scripts/smoke-launcher-prelaunch.sh [options]

Options:
  --host HOST             Game server host shown/written by launcher. Default: voidscape.gg.
  --port PORT             Game server port shown/written by launcher. Default: 43596.
  --portal-url URL        Portal/website base URL. Default: https://voidscape.gg.
  --jar FILE              Use an existing launcher jar instead of PC_Launcher/OpenRSC.jar.
  --no-build              Reuse the selected launcher jar.
  --use-packaged-config   Do not override launcher host/portal system properties.
  --out DIR               Output directory for screenshot/logs/manifest.
  --delay-ms MS           Delay before screenshot. Default: 2600.
  -h, --help              Show this help.

The smoke builds or reuses the launcher, runs it with a temporary cache
directory and smoke screenshot property, verifies the launcher wrote the
expected endpoint files, and writes manifest.json.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--host)
			[[ $# -ge 2 ]] || { echo "error: --host needs a value" >&2; exit 2; }
			GAME_HOST="$2"
			shift 2
			;;
		--port)
			[[ $# -ge 2 ]] || { echo "error: --port needs a value" >&2; exit 2; }
			GAME_PORT="$2"
			shift 2
			;;
		--portal-url)
			[[ $# -ge 2 ]] || { echo "error: --portal-url needs a value" >&2; exit 2; }
			PORTAL_URL="$2"
			shift 2
			;;
		--jar)
			[[ $# -ge 2 ]] || { echo "error: --jar needs a value" >&2; exit 2; }
			JAR_PATH="$2"
			BUILD=0
			shift 2
			;;
		--no-build)
			BUILD=0
			shift
			;;
		--use-packaged-config)
			USE_PACKAGED_CONFIG=1
			shift
			;;
		--out)
			[[ $# -ge 2 ]] || { echo "error: --out needs a value" >&2; exit 2; }
			OUT_DIR="$2"
			shift 2
			;;
		--delay-ms)
			[[ $# -ge 2 ]] || { echo "error: --delay-ms needs a value" >&2; exit 2; }
			DELAY_MS="$2"
			shift 2
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

if [[ -z "$GAME_HOST" || -z "$PORTAL_URL" ]]; then
	echo "error: host and portal URL must be non-empty." >&2
	exit 2
fi
if ! [[ "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "error: --port must be a number from 1 to 65535." >&2
	exit 2
fi
if ! [[ "$DELAY_MS" =~ ^[0-9]+$ ]] || (( DELAY_MS < 250 )); then
	echo "error: --delay-ms must be at least 250." >&2
	exit 2
fi

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
SCREENSHOT="$OUT_DIR/launcher.png"
LOG="$OUT_DIR/launcher.log"
CACHE_DIR="$OUT_DIR/cache"

if [[ "$BUILD" -eq 1 ]]; then
	(cd "$ROOT/PC_Launcher" && ant compile)
fi

if [[ ! -f "$JAR_PATH" ]]; then
	echo "error: launcher jar not found: $JAR_PATH" >&2
	exit 1
fi

echo "==> Launching desktop launcher smoke"
echo "==> Launcher target: $GAME_HOST:$GAME_PORT"
echo "==> Portal: $PORTAL_URL"
echo "==> Output: $OUT_DIR"

JAVA_PROPS=(
	-Dvoidscape.launcher.smoke.out="$SCREENSHOT" \
	-Dvoidscape.launcher.smoke.delayMs="$DELAY_MS" \
	-Dvoidscape.launcher.smoke.exit=true
)
if [[ "$USE_PACKAGED_CONFIG" != "1" ]]; then
	JAVA_PROPS+=(
		-Dvoidscape.serverHost="$GAME_HOST"
		-Dvoidscape.serverPort="$GAME_PORT"
		-Dvoidscape.portalUrl="$PORTAL_URL"
		-Dvoidscape.websiteUrl="$PORTAL_URL"
		-Dvoidscape.manifestUrl="$PORTAL_URL/api/launcher/manifest.properties"
	)
fi

java "${JAVA_PROPS[@]}" \
	-jar "$JAR_PATH" \
	--dir "$CACHE_DIR" \
	>"$LOG" 2>&1

export OUT_DIR SCREENSHOT LOG CACHE_DIR GAME_HOST GAME_PORT PORTAL_URL JAR_PATH USE_PACKAGED_CONFIG
python3 - <<'PY'
import json
import os
import pathlib
import struct
import sys
import time

out_dir = pathlib.Path(os.environ["OUT_DIR"])
screenshot = pathlib.Path(os.environ["SCREENSHOT"])
cache_dir = pathlib.Path(os.environ["CACHE_DIR"])
log = pathlib.Path(os.environ["LOG"])

if not screenshot.exists() or screenshot.stat().st_size < 1024:
    print(f"ERROR: launcher screenshot missing or too small: {screenshot}", file=sys.stderr)
    sys.exit(1)

with screenshot.open("rb") as handle:
    header = handle.read(24)
if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n":
    print(f"ERROR: launcher screenshot is not a PNG: {screenshot}", file=sys.stderr)
    sys.exit(1)
width, height = struct.unpack(">II", header[16:24])
if width != 820 or height != 560:
    print(f"ERROR: launcher screenshot dimensions were {width}x{height}, expected 820x560", file=sys.stderr)
    sys.exit(1)

ip_path = cache_dir / "ip.txt"
port_path = cache_dir / "port.txt"
actual_host = ip_path.read_text(encoding="utf-8").strip() if ip_path.exists() else ""
actual_port = port_path.read_text(encoding="utf-8").strip() if port_path.exists() else ""
if actual_host != os.environ["GAME_HOST"] or actual_port != os.environ["GAME_PORT"]:
    print(f"ERROR: launcher endpoint files were {actual_host}:{actual_port}", file=sys.stderr)
    sys.exit(1)

manifest = {
    "ok": True,
    "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    "jarPath": os.environ["JAR_PATH"],
    "portalUrl": os.environ["PORTAL_URL"],
    "usedPackagedConfig": os.environ["USE_PACKAGED_CONFIG"] == "1",
    "endpoint": {
        "host": actual_host,
        "port": int(actual_port),
    },
    "screenshot": {
        "pngPath": str(screenshot),
        "width": width,
        "height": height,
    },
    "log": str(log),
}
(out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(f"LAUNCHER_PRELAUNCH_SMOKE_OK manifest={out_dir / 'manifest.json'}")
print(f"LAUNCHER_PRELAUNCH_CAPTURE launcher {screenshot}")
PY
