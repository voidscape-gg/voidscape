#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
TMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-packaged-portal-flow.XXXXXX")"
GOOD_ROOT="$TMP_ROOT/good-bundle"
BAD_ROOT="$TMP_ROOT/missing-title-bundle"
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

for command in curl java node python3 sqlite3; do
	command -v "$command" >/dev/null 2>&1 || {
		echo "ERROR: packaged portal flow requires $command" >&2
		exit 1
	}
done
if [[ ! -f "$REPO_ROOT/server/core.jar" ]]; then
	echo "ERROR: missing server/core.jar; run scripts/build.sh first" >&2
	exit 1
fi

read -r GOOD_PORT BAD_PORT < <(python3 - <<'PY'
import socket

sockets = []
try:
    for _ in range(2):
        probe = socket.socket()
        probe.bind(("127.0.0.1", 0))
        sockets.append(probe)
    print(*(probe.getsockname()[1] for probe in sockets))
finally:
    for probe in sockets:
        probe.close()
PY
)

prepare_bundle() {
	local bundle_root="$1"
	local include_titles="$2"
	local portal_root="$bundle_root/portal/web-portal"
	local defs_root="$bundle_root/server/conf/server/defs"

	python3 "$SCRIPT_DIR/package-portal-runtime.py" \
		--repo-root "$REPO_ROOT" \
		--target "$portal_root"
	mkdir -p "$defs_root"
	for definition in ItemDefs.json ItemDefsCustom.json ItemDefsPatch18.json; do
		cp "$REPO_ROOT/server/conf/server/defs/$definition" "$defs_root/$definition"
	done
	if [[ "$include_titles" == "yes" ]]; then
		cp "$REPO_ROOT/server/conf/server/defs/PlayerTitleDefs.json" \
			"$defs_root/PlayerTitleDefs.json"
	fi
	cp "$REPO_ROOT/server/core.jar" "$bundle_root/server/core.jar"

	sqlite3 "$bundle_root/game.db" < "$REPO_ROOT/server/database/sqlite/core.sqlite"
	sqlite3 "$bundle_root/game.db" <<'SQL'
INSERT INTO players (username, email, pass, salt, creation_date, creation_ip)
VALUES ('ExistingV95', 'existing-v95@example.com', 'unused', 'unused', 0, '127.0.0.1');
INSERT INTO player_cache (playerID, type, key, value)
SELECT id, 0, 'player_title_active', 'founder'
FROM players WHERE username = 'ExistingV95';
SQL

	PORTAL_DATA_DIR="$bundle_root/portal-data" \
		node "$portal_root/dev-server.mjs" --initialize-store >/dev/null
}

start_portal() {
	local bundle_root="$1"
	local port="$2"
	local log_path="$3"
	local portal_root="$bundle_root/portal/web-portal"

	env \
		PORT="$port" \
		PORTAL_BIND_HOST=127.0.0.1 \
		PORTAL_PUBLIC_MODE=1 \
		PORTAL_LAUNCH_SIGNUP_MODE=1 \
		PORTAL_LAUNCH_AT=2099-01-01T00:00:00Z \
		PORTAL_LAUNCH_FREE_CARD_HOURS=24 \
		PORTAL_DATA_DIR="$bundle_root/portal-data" \
		PORTAL_OPENRSC_DB="$bundle_root/game.db" \
		PORTAL_GAME_PASSWORD_HELPER_CLASSPATH="$bundle_root/server/core.jar" \
		PORTAL_CLIENT_VERSION=10139 \
		PORTAL_ABUSE_HASH_SALT=vs095-packaged-portal-salt-2026 \
		PORTAL_PUBLIC_ORIGIN="http://127.0.0.1:$port" \
		PORTAL_EMAIL_DRY_RUN=1 \
		PORTAL_SHUTDOWN_TIMEOUT_MS=5000 \
		node "$portal_root/dev-server.mjs" >"$log_path" 2>&1 &
	LAST_PID=$!
	PIDS+=("$LAST_PID")
}

wait_for_health() {
	local port="$1"
	local pid="$2"
	local log_path="$3"
	for _ in {1..100}; do
		if curl -fsS "http://127.0.0.1:$port/api/health" >/dev/null 2>&1; then
			return 0
		fi
		if ! kill -0 "$pid" >/dev/null 2>&1; then
			cat "$log_path" >&2
			return 1
		fi
		sleep 0.1
	done
	cat "$log_path" >&2
	return 1
}

file_sha256() {
	python3 - "$1" <<'PY'
import hashlib
import sys

digest = hashlib.sha256()
with open(sys.argv[1], "rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        digest.update(chunk)
print(digest.hexdigest())
PY
}

prepare_bundle "$BAD_ROOT" no
BAD_DB_HASH_BEFORE="$(file_sha256 "$BAD_ROOT/game.db")"
BAD_STORE_HASH_BEFORE="$(file_sha256 "$BAD_ROOT/portal-data/dev-store.json")"
start_portal "$BAD_ROOT" "$BAD_PORT" "$TMP_ROOT/missing-title.log"
BAD_PID="$LAST_PID"
for _ in {1..100}; do
	if ! kill -0 "$BAD_PID" >/dev/null 2>&1; then
		break
	fi
	sleep 0.05
done
if kill -0 "$BAD_PID" >/dev/null 2>&1; then
	echo "packaged portal should refuse startup without private title definitions" >&2
	exit 1
fi
set +e
wait "$BAD_PID"
BAD_EXIT=$?
set -e
if [[ "$BAD_EXIT" -eq 0 ]]; then
	echo "missing title definitions should produce a non-zero startup exit" >&2
	exit 1
fi
grep -q 'portal_runtime_dependency_failed.*game_definitions_unavailable' \
	"$TMP_ROOT/missing-title.log" || {
	cat "$TMP_ROOT/missing-title.log" >&2
	echo "missing title startup failure was not explicit" >&2
	exit 1
}
if [[ "$(file_sha256 "$BAD_ROOT/game.db")" != "$BAD_DB_HASH_BEFORE" ]] \
	|| [[ "$(file_sha256 "$BAD_ROOT/portal-data/dev-store.json")" != "$BAD_STORE_HASH_BEFORE" ]]; then
	echo "missing private definitions must not mutate either durable store" >&2
	exit 1
fi

prepare_bundle "$GOOD_ROOT" yes
if [[ -d "$GOOD_ROOT/server/src" ]] || find "$GOOD_ROOT" -type f -name '*.java' -print -quit | grep -q .; then
	echo "packaged portal fixture must not contain Java source" >&2
	exit 1
fi
start_portal "$GOOD_ROOT" "$GOOD_PORT" "$TMP_ROOT/good.log"
GOOD_PID="$LAST_PID"
wait_for_health "$GOOD_PORT" "$GOOD_PID" "$TMP_ROOT/good.log"

availability="$(curl -fsS \
	"http://127.0.0.1:$GOOD_PORT/api/openrsc/characters/ExistingV95?availability=1")"
node -e '
const payload = JSON.parse(process.argv[1]);
if (payload.available !== false || !payload.character || payload.character.name !== "ExistingV95") {
	throw new Error("existing-character availability did not use the packaged definitions");
}
' "$availability"

for method in GET HEAD; do
	for path in \
		/server/conf/server/defs/PlayerTitleDefs.json \
		/server/src/com/openrsc/server/content/PlayerTitle.java
	do
		status="$(curl -sS -o /dev/null -w '%{http_code}' -X "$method" \
			"http://127.0.0.1:$GOOD_PORT$path")"
		if [[ "$status" != "404" ]]; then
			echo "private/source path should return 404 for $method $path, got $status" >&2
			exit 1
		fi
	done
done

signup_response="$TMP_ROOT/signup.json"
signup_status="$(curl -sS -o "$signup_response" -w '%{http_code}' \
	-X POST "http://127.0.0.1:$GOOD_PORT/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"PackagedV95","email":"packaged-v95@example.com","password":"PackagePass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
if [[ "$signup_status" != "201" ]]; then
	cat "$signup_response" >&2
	echo "packaged launch signup expected HTTP 201, got $signup_status" >&2
	exit 1
fi

PLAYER_ID="$(sqlite3 "$GOOD_ROOT/game.db" \
	"SELECT id FROM players WHERE username='PackagedV95' LIMIT 1;")"
if ! [[ "$PLAYER_ID" =~ ^[0-9]+$ ]]; then
	echo "packaged launch signup did not create one game player" >&2
	exit 1
fi
if [[ "$(sqlite3 "$GOOD_ROOT/game.db" \
	"SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='web_account_id' AND value='1';")" != "1" ]]; then
	echo "packaged launch signup did not create the game ownership marker" >&2
	exit 1
fi
if [[ "$(sqlite3 "$GOOD_ROOT/game.db" \
	"SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='launch_24h_card' AND value='1';")" != "1" ]]; then
	echo "packaged launch signup did not preserve launch-card eligibility" >&2
	exit 1
fi

node - "$signup_response" "$GOOD_ROOT/portal-data/dev-store.json" "$PLAYER_ID" <<'NODE'
const fs = require("fs");
const response = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const store = JSON.parse(fs.readFileSync(process.argv[3], "utf8"));
const playerId = Number(process.argv[4]);
if (!response.token || !Array.isArray(response.characters)) {
	throw new Error("packaged launch signup response is incomplete");
}
const responseCharacter = response.characters.find((row) => row.name === "PackagedV95");
if (!responseCharacter || responseCharacter.source !== "openrsc-sqlite-created") {
	throw new Error("packaged launch signup response is missing the linked character");
}
const account = store.accounts.find((row) => row.emailCanonical === "packaged-v95@example.com");
const character = store.characters.find((row) => row.name === "PackagedV95");
if (!account || !character || character.accountId !== account.id || Number(character.playerId) !== playerId) {
	throw new Error("packaged portal and game character are not linked consistently");
}
NODE

echo "Packaged portal character flow tests passed"
