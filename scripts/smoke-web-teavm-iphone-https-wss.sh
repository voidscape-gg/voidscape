#!/usr/bin/env bash
# Smoke-test the production HTTPS + same-host WSS proxy shape locally.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD=1
HTTPS_PORT=0
WS_HOST="${WEB_TEA_HTTPS_WSS_HOST:-127.0.0.1}"
WS_PORT="${WEB_TEA_HTTPS_WSS_PORT:-43496}"
AUTH_USER="${WEB_TEA_SMOKE_USER:-test}"
AUTH_PASS="${WEB_TEA_SMOKE_PASS:-test}"
OUT_DIR="${WEB_TEA_HTTPS_WSS_OUT:-$ROOT/tmp/web-teavm-https-wss-smoke}"
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"

usage() {
	cat <<'EOF'
Usage: scripts/smoke-web-teavm-iphone-https-wss.sh [options]

Options:
  --no-build              Reuse existing Web_Client_TeaVM/target/teavm output.
  --https-port PORT       Local HTTPS/WSS proxy port. Default: choose a free port.
  --ws-host HOST          Upstream game WebSocket host. Default: 127.0.0.1.
  --ws-port PORT          Upstream game WebSocket port. Default: 43496.
  --user USER             Login username. Default: test.
  --pass PASS             Login password. Default: test.
  --out DIR               Output directory for certs/screenshots/logs.
  --chrome PATH           Chrome/Chromium executable path.
  --playwright-core DIR   Directory for playwright-core package.
  -h, --help              Show this help.

Requires a running Voidscape server with WebSockets enabled. The script starts a
temporary self-signed HTTPS static server from Web_Client_TeaVM/target/teavm and
proxies same-host WSS upgrade requests to the configured game WebSocket port.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--https-port)
			HTTPS_PORT="${2:-}"
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
		--out)
			OUT_DIR="${2:-}"
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

if [[ -z "$WS_HOST" || -z "$WS_PORT" || -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
	echo "ERROR: ws-host, ws-port, user, and pass must be non-empty." >&2
	exit 2
fi

if [[ "$BUILD" -eq 1 ]]; then
	"$ROOT/scripts/build-web-teavm-spike.sh"
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: missing TeaVM output at $TARGET/index.html. Run scripts/build-web-teavm-spike.sh first." >&2
	exit 1
fi

if ! lsof -nP -iTCP:"$WS_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
	echo "ERROR: no listener found on upstream WebSocket port $WS_PORT. Start scripts/run-server.sh first." >&2
	exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
	echo "ERROR: openssl is required to generate the temporary local HTTPS certificate." >&2
	exit 1
fi

if [[ "$HTTPS_PORT" == "0" ]]; then
	HTTPS_PORT="$(python3 - <<'PY'
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
CERT_DIR="$(mktemp -d "$OUT_DIR/cert.XXXXXX")"
CERT_FILE="$CERT_DIR/localhost.crt"
KEY_FILE="$CERT_DIR/localhost.key"
HTTPS_LOG="$OUT_DIR/https-wss-proxy.log"
READY_FILE="$OUT_DIR/https-wss-ready"
rm -f "$READY_FILE"

openssl req -x509 -newkey rsa:2048 -nodes \
	-keyout "$KEY_FILE" \
	-out "$CERT_FILE" \
	-days 1 \
	-subj "/CN=127.0.0.1" \
	-addext "subjectAltName=IP:127.0.0.1,DNS:localhost" >/dev/null 2>&1

export TARGET HTTPS_PORT WS_HOST WS_PORT CERT_FILE KEY_FILE READY_FILE
node <<'NODE' >"$HTTPS_LOG" 2>&1 &
const fs = require('fs');
const https = require('https');
const net = require('net');
const path = require('path');
const { URL } = require('url');

const root = process.env.TARGET;
const port = Number(process.env.HTTPS_PORT);
const wsHost = process.env.WS_HOST;
const wsPort = Number(process.env.WS_PORT);
const readyFile = process.env.READY_FILE;
const mime = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.txt': 'text/plain; charset=utf-8',
  '.sum': 'text/plain; charset=utf-8'
};

function send(response, status, body, headers = {}) {
  response.writeHead(status, headers);
  response.end(body);
}

function filePathFor(requestUrl) {
  const parsed = new URL(requestUrl, `https://127.0.0.1:${port}/`);
  let pathname = decodeURIComponent(parsed.pathname);
  if (pathname === '/') pathname = '/index.html';
  const resolved = path.resolve(root, `.${pathname}`);
  const rootResolved = path.resolve(root);
  if (resolved !== rootResolved && !resolved.startsWith(rootResolved + path.sep)) {
    return null;
  }
  return resolved;
}

const server = https.createServer({
  key: fs.readFileSync(process.env.KEY_FILE),
  cert: fs.readFileSync(process.env.CERT_FILE)
}, (request, response) => {
  const filePath = filePathFor(request.url);
  if (!filePath) {
    send(response, 403, 'Forbidden\n', { 'Content-Type': 'text/plain; charset=utf-8' });
    return;
  }
  fs.stat(filePath, (statError, stat) => {
    if (statError || !stat.isFile()) {
      send(response, 404, 'Not found\n', { 'Content-Type': 'text/plain; charset=utf-8' });
      return;
    }
    response.writeHead(200, {
      'Content-Type': mime[path.extname(filePath)] || 'application/octet-stream',
      'Cache-Control': path.basename(filePath) === 'index.html' ? 'no-cache' : 'public, max-age=60'
    });
    fs.createReadStream(filePath).pipe(response);
  });
});

server.on('upgrade', (request, socket, head) => {
  const upstream = net.connect(wsPort, wsHost, () => {
    const lines = [`${request.method} ${request.url || '/'} HTTP/${request.httpVersion}`];
    for (let i = 0; i < request.rawHeaders.length; i += 2) {
      lines.push(`${request.rawHeaders[i]}: ${request.rawHeaders[i + 1]}`);
    }
    upstream.write(lines.join('\r\n') + '\r\n\r\n');
    if (head && head.length) upstream.write(head);
    socket.pipe(upstream);
    upstream.pipe(socket);
  });
  upstream.on('error', (error) => {
    console.error(`upstream websocket error: ${error.message}`);
    socket.destroy();
  });
  socket.on('error', () => upstream.destroy());
});

server.listen(port, '127.0.0.1', () => {
  fs.writeFileSync(readyFile, String(port));
  console.log(`HTTPS/WSS proxy listening on https://127.0.0.1:${port}/ -> ${wsHost}:${wsPort}`);
});

process.on('SIGTERM', () => server.close(() => process.exit(0)));
process.on('SIGINT', () => server.close(() => process.exit(0)));
NODE
PROXY_PID=$!

cleanup() {
	if [[ "${PROXY_PID:-}" =~ ^[0-9]+$ ]]; then
		kill "$PROXY_PID" >/dev/null 2>&1 || true
		wait "$PROXY_PID" >/dev/null 2>&1 || true
	fi
}
trap cleanup EXIT

deadline=$((SECONDS + 10))
while [[ ! -f "$READY_FILE" && "$SECONDS" -lt "$deadline" ]]; do
	sleep 0.2
done
if [[ ! -f "$READY_FILE" ]]; then
	echo "ERROR: HTTPS/WSS proxy did not become ready. Log: $HTTPS_LOG" >&2
	exit 1
fi

smoke_args=(
	--base-url "https://127.0.0.1:$HTTPS_PORT/"
	--host 127.0.0.1
	--ws-port "$WS_PORT"
	--user "$AUTH_USER"
	--pass "$AUTH_PASS"
	--out "$OUT_DIR/login-smoke"
	--ignore-https-errors
)
if [[ -n "$CHROME_PATH" ]]; then
	smoke_args+=(--chrome "$CHROME_PATH")
fi
if [[ -n "$PLAYWRIGHT_CORE_DIR" ]]; then
	smoke_args+=(--playwright-core "$PLAYWRIGHT_CORE_DIR")
fi

"$ROOT/scripts/smoke-web-teavm-iphone.sh" "${smoke_args[@]}"

cat > "$OUT_DIR/summary.json" <<EOF
{
  "baseUrl": "https://127.0.0.1:$HTTPS_PORT/",
  "upstreamWebSocket": "$WS_HOST:$WS_PORT",
  "loginSmokeSummary": "$OUT_DIR/login-smoke/summary.json",
  "proxyLog": "$HTTPS_LOG",
  "certificate": "$CERT_FILE"
}
EOF

echo
echo "iPhone TeaVM HTTPS/WSS smoke passed."
echo "Artifacts: $OUT_DIR"
