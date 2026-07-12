#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8088}"
BIND_HOST="${BIND_HOST:-0.0.0.0}"
WS_PORT="${WS_PORT:-43496}"
BUILD=1

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--port)
			PORT="$2"
			shift 2
			;;
		--bind)
			BIND_HOST="$2"
			shift 2
			;;
		--ws-port)
			WS_PORT="$2"
			shift 2
			;;
		*)
			echo "Unknown option: $1" >&2
			echo "Usage: scripts/run-web-teavm-local.sh [--no-build] [--port 8088] [--bind 0.0.0.0] [--ws-port 43496]" >&2
			exit 2
			;;
	esac
done

if [[ "$BUILD" == "1" ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ ! -f "$TARGET/index.html" ]]; then
	echo "Missing web client output at $TARGET/index.html; run scripts/build-web-teavm-spike.sh first." >&2
	exit 1
fi

LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)"
if [[ -z "$LAN_IP" ]]; then
	LAN_IP="$(ipconfig getifaddr en1 2>/dev/null || true)"
fi
if [[ -z "$LAN_IP" ]]; then
	LAN_IP="127.0.0.1"
fi

cat <<EOF
==> Serving Voidscape TeaVM web client
    Web root: $TARGET
    Bind:     $BIND_HOST:$PORT

Keep scripts/run-server.sh running in another terminal.

Mac URL:
    http://127.0.0.1:$PORT/index.html?mobile=1&host=127.0.0.1&port=$WS_PORT

iPhone URL on the same Wi-Fi:
    http://$LAN_IP:$PORT/index.html?mobile=1&host=$LAN_IP&port=$WS_PORT

The explicit endpoint is saved in the browser. After opening that iPhone URL once,
Home Screen launches from index.html?mobile=1 should keep using $LAN_IP:$WS_PORT.
To clear the saved endpoint:
    http://$LAN_IP:$PORT/index.html?mobile=1&endpoint=reset

Diagnostics URL for real-device QA:
    http://$LAN_IP:$PORT/index.html?mobile=1&diag=1&host=$LAN_IP&port=$WS_PORT

EOF

python3 -u -m http.server "$PORT" --bind "$BIND_HOST" -d "$TARGET"
