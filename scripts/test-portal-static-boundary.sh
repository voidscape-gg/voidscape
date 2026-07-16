#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-static.XXXXXX")"
PORTAL_ROOT="$TMP_ROOT/portal"
ARTIFACT_ROOT="$TMP_ROOT/artifacts"
CLIENT_JAR="$ARTIFACT_ROOT/Open_RSC_Client.jar"
LAUNCHER_JAR="$ARTIFACT_ROOT/VoidscapeLauncher.jar"
CLIENT_CACHE="$ARTIFACT_ROOT/Cache"
PORTS=( $(python3 - <<'PY'
import socket

sockets = []
try:
    for _ in range(3):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        sockets.append(probe)
    print(" ".join(str(probe.getsockname()[1]) for probe in sockets))
finally:
    for probe in sockets:
        probe.close()
PY
) )
PIDS=()

cleanup() {
	for pid in "${PIDS[@]:-}"; do
		[[ -n "$pid" ]] || continue
		kill "$pid" >/dev/null 2>&1 || true
		wait "$pid" >/dev/null 2>&1 || true
	done
	rm -rf "$TMP_ROOT"
}
trap cleanup EXIT

cd "$REPO_ROOT"
python3 "$SCRIPT_DIR/package-portal-runtime.py" \
	--repo-root "$REPO_ROOT" \
	--target "$PORTAL_ROOT"
cp web/portal/build-meta.json "$PORTAL_ROOT/build-meta.json"
mkdir -p "$CLIENT_CACHE"
touch "$CLIENT_JAR" "$LAUNCHER_JAR" "$CLIENT_CACHE/config85.jag"

for index in 0 1 2; do
	PORT="${PORTS[$index]}" PORTAL_DATA_DIR="$TMP_ROOT/data-$index" \
		PORTAL_PC_CLIENT_JAR="$CLIENT_JAR" \
		PORTAL_CLIENT_CACHE_DIR="$CLIENT_CACHE" \
		PORTAL_LAUNCHER_JAR="$LAUNCHER_JAR" \
		PORTAL_CLIENT_VERSION=10139 \
		node "$PORTAL_ROOT/dev-server.mjs" >"$TMP_ROOT/portal-$index.log" 2>&1 &
	PIDS+=("$!")
done
for index in 0 1 2; do
	port="${PORTS[$index]}"
	pid="${PIDS[$index]}"
	ready=0
	for _ in {1..80}; do
		if curl -fsS "http://127.0.0.1:$port/api/health" >/dev/null 2>&1; then
			ready=1
			break
		fi
		if ! kill -0 "$pid" >/dev/null 2>&1; then
			cat "$TMP_ROOT/portal-$index.log" >&2
			exit 1
		fi
		sleep 0.1
	done
	if [[ "$ready" -ne 1 ]]; then
		cat "$TMP_ROOT/portal-$index.log" >&2
		echo "portal static boundary: timed out waiting for packaged portal $index" >&2
		exit 1
	fi
done
PORT="${PORTS[0]}"

expect_status() {
	local expected="$1"
	local path="$2"
	shift 2
	local actual
	actual="$(curl --path-as-is -sS -o /dev/null -w '%{http_code}' "$@" "http://127.0.0.1:$PORT$path")"
	if [[ "$actual" != "$expected" ]]; then
		echo "portal static boundary: expected $expected for $path, got $actual" >&2
		exit 1
	fi
}

for path in \
	/ /portal /privacy /community-rules /data-deletion /features \
	/loot-editor /loot-editor-guide /npcs /drops /discord /transparency \
	/portal/ /loot-editor/ /loot-editor-guide/ \
	/index.html /styles.css /presence.js /assets/favicon.png \
	/assets/loot-editor-data.json /assets/whitepaper/feature-void-rift-current.png \
	/assets/npc-database/npc/0.png /assets/npc-database/item/0.png \
	/assets/alagard.ttf /assets/instrument-sans-latin-400-600.woff2
do
	expect_status 200 "$path"
done
expect_status 206 /assets/favicon.png -H 'Range: bytes=0-0'
expect_status 405 /styles.css -X POST
expect_status 404 /dev-server.mjs -X POST

for path in \
	/dev-server.mjs /%64ev-server.mjs /%2564ev-server.mjs /DEV-SERVER.MJS \
	'/dev-server.mjs?probe=1' /% /api-smoke.mjs /commerce-smoke.mjs /README.md \
	/schema /schema/sqlite/001_web_accounts.sql /build-meta.json \
	/server/conf/server/defs/PlayerTitleDefs.json \
	/server/src/com/openrsc/server/content/PlayerTitle.java \
	/public-static-contract.json /.env /.git/config /unknown.js /unknown.sql \
	/unknown.json /assets/private.mjs /assets/private.json /assets/ \
	/schema%2fsqlite%2f001_web_accounts.sql /assets/%2e%2e/dev-server.mjs \
	/assets/../index.html /assets/.hidden /assets//favicon.png \
	/assets/%66avicon.png /assets/generated/buttons-tabs.png \
	/assets/npc-database/npc/not-a-number.png \
	/assets/npc-database/npc/0.jpg
do
	expect_status 404 "$path"
done
expect_status 404 /dev-server.mjs -I

font_type="$(curl --path-as-is -sSI "http://127.0.0.1:$PORT/assets/alagard.ttf" | tr -d '\r' | awk -F ': ' 'tolower($1) == "content-type" { print $2 }')"
[[ "$font_type" == "font/ttf" ]] || { echo "portal static boundary: expected font/ttf, got ${font_type:-missing}" >&2; exit 1; }

node scripts/verify-launch-staging.mjs \
	--portal-url "http://127.0.0.1:${PORTS[0]}/" \
	--public-origin "http://127.0.0.1:${PORTS[1]}/" \
	--public-origin "http://127.0.0.1:${PORTS[2]}/" \
	--static-boundary-only \
	--allow-http \
	--out "$TMP_ROOT/all-origin-evidence"

echo "Portal static boundary tests passed"
