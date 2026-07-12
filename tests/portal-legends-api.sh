#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

for command in curl node python3 sqlite3 shasum; do
	command -v "$command" >/dev/null || {
		echo "missing required command: $command" >&2
		exit 1
	}
done

tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-legends-api.XXXXXX")"
db_path="$tmp_dir/legends.db"
empty_db_path="$tmp_dir/empty.db"
server_pid=""

cleanup() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" >/dev/null 2>&1 || true
		wait "$server_pid" >/dev/null 2>&1 || true
	fi
	rm -rf "$tmp_dir"
}
trap cleanup EXIT

pick_port() {
	python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

wait_for_portal() {
	local base_url="$1"
	for _ in $(seq 1 100); do
		if curl -fsS "$base_url/api/health" >/dev/null 2>&1; then
			return 0
		fi
		if ! kill -0 "$server_pid" >/dev/null 2>&1; then
			echo "portal exited before becoming ready" >&2
			cat "$tmp_dir/portal.log" >&2 || true
			return 1
		fi
		sleep 0.05
	done
	echo "portal did not become ready" >&2
	cat "$tmp_dir/portal.log" >&2 || true
	return 1
}

stop_portal() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" >/dev/null 2>&1 || true
		wait "$server_pid" >/dev/null 2>&1 || true
		server_pid=""
	fi
}

start_portal() {
	local port="$1"
	local data_dir="$2"
	shift 2
	PORT="$port" \
	PORTAL_BIND_HOST=127.0.0.1 \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_DATA_DIR="$data_dir" \
	PORTAL_ABUSE_HASH_SALT=legends-api-test-salt-2026 \
	"$@" node web/portal/dev-server.mjs >"$tmp_dir/portal.log" 2>&1 &
	server_pid=$!
	wait_for_portal "http://127.0.0.1:$port"
}

sqlite3 "$db_path" <<'SQL'
CREATE TABLE world_achievement_records (
	season_id TEXT NOT NULL,
	record_key TEXT NOT NULL,
	record_type TEXT NOT NULL,
	player_id INTEGER NOT NULL,
	player_name TEXT NOT NULL,
	subject_id INTEGER NOT NULL,
	value INTEGER NOT NULL,
	source TEXT NOT NULL,
	source_event_key TEXT,
	claimed_at_ms INTEGER NOT NULL,
	detail TEXT NOT NULL,
	PRIMARY KEY (season_id, record_key)
);

CREATE TABLE world_pk_streaks (
	season_id TEXT NOT NULL,
	player_id INTEGER NOT NULL,
	player_name TEXT NOT NULL,
	current_streak INTEGER NOT NULL,
	best_streak INTEGER NOT NULL,
	qualified_kills INTEGER NOT NULL,
	last_qualified_at_ms INTEGER NOT NULL,
	updated_at_ms INTEGER NOT NULL,
	PRIMARY KEY (season_id, player_id)
);

CREATE TABLE world_pk_events (
	death_id TEXT PRIMARY KEY,
	season_id TEXT NOT NULL,
	killer_account_id INTEGER,
	victim_account_id INTEGER,
	victim_name TEXT NOT NULL,
	reject_reason TEXT NOT NULL,
	network_evidence TEXT NOT NULL
);

INSERT INTO world_achievement_records VALUES
	('launch-2026', 'first:skill:8:80', 'first_skill', 101, 'Alice', 8, 80,
	 'skill_level', 'SOURCE_EVENT_PRIVATE_CANARY', 1000, 'DETAIL_PRIVATE_CANARY'),
	('launch-2026', 'first:item:575', 'first_item', 102, 'Bob', 575, 1,
	 'launch_cracker_campaign', 'CRACKER_EVENT_PRIVATE_CANARY', 1100, 'ITEM_DETAIL_PRIVATE_CANARY'),
	('other-season', 'first:skill:8:80', 'first_skill', 999, 'OtherSeasonCanary', 8, 80,
	 'skill_level', 'OTHER_SEASON_EVENT_CANARY', 1, 'OTHER_SEASON_DETAIL_CANARY'),
	('launch-2026', 'malformed:level', 'first_skill', 201, 'BadLevel', 8, 85,
	 'skill_level', 'BAD_LEVEL_EVENT', 900, 'BAD_LEVEL_DETAIL'),
	('launch-2026', 'malformed:skill', 'first_skill', 202, 'BadSkill', 99, 80,
	 'skill_level', 'BAD_SKILL_EVENT', 901, 'BAD_SKILL_DETAIL'),
	('launch-2026', 'malformed:name', 'first_skill', 203, 'Bad_Name', 7, 80,
	 'skill_level', 'BAD_NAME_EVENT', 902, 'BAD_NAME_DETAIL'),
	('launch-2026', 'malformed:spaced-name', 'first_skill', 205, ' SpacedName', 7, 80,
	 'skill_level', 'SPACED_NAME_EVENT', 903, 'SPACED_NAME_DETAIL'),
	('launch-2026', 'malformed:time', 'first_skill', 204, 'BadTime', 7, 80,
	 'skill_level', 'BAD_TIME_EVENT', 0, 'BAD_TIME_DETAIL');

INSERT INTO world_pk_streaks VALUES
	('launch-2026', 301, 'Ace', 2, 5, 7, 4900, 5000),
	('launch-2026', 302, 'King', 2, 5, 7, 5000, 5100),
	('launch-2026', 303, 'Rival', 4, 4, 99, 5100, 5200),
	('launch-2026', 304, 'NoKills', 0, 0, 0, 0, 5300),
	('launch-2026', 305, 'BadStreak', 6, 5, 7, 5200, 5300),
	('launch-2026', 306, 'Bad_Name', 1, 1, 1, 5300, 5400),
	('launch-2026', 307, 'NoBest', 0, 0, 1, 5400, 5500),
	('other-season', 999, 'OtherLeader', 50, 50, 500, 9999, 9999);

INSERT INTO world_pk_events VALUES
	('DEATH_ID_PRIVATE_CANARY', 'launch-2026', 884422, 884423,
	 'VICTIM_PRIVATE_CANARY', 'REJECT_REASON_PRIVATE_CANARY', 'NETWORK_PRIVATE_CANARY');
SQL

before_hash="$(shasum -a 256 "$db_path" | awk '{print $1}')"
port="$(pick_port)"
start_portal "$port" "$tmp_dir/data-main" env PORTAL_OPENRSC_DB="$db_path" PORTAL_LEGENDS_SEASON_ID=launch-2026
base_url="http://127.0.0.1:$port"

curl -fsS -D "$tmp_dir/headers.txt" "$base_url/api/legends" -o "$tmp_dir/legends-1.json"
curl -fsS "$base_url/api/legends" -o "$tmp_dir/legends-2.json"
curl -fsS "$base_url/api/legends?season=other-season" -o "$tmp_dir/legends-query.json"
curl -fsS "$base_url/legends" -o "$tmp_dir/legends.html"
curl -fsS "$base_url/legends.css" -o "$tmp_dir/legends.css"
curl -fsS "$base_url/legends.js" -o "$tmp_dir/legends.js"

cmp "$tmp_dir/legends-1.json" "$tmp_dir/legends-2.json"
cmp "$tmp_dir/legends-1.json" "$tmp_dir/legends-query.json"
grep -qi '^cache-control: no-store' "$tmp_dir/headers.txt"

post_status="$(curl -sS -o "$tmp_dir/post.json" -w '%{http_code}' -X POST "$base_url/api/legends")"
[[ "$post_status" == "404" ]]

python3 - "$tmp_dir/legends-1.json" "$tmp_dir/post.json" \
	"$tmp_dir/legends.html" "$tmp_dir/legends.css" "$tmp_dir/legends.js" <<'PY'
import json
import sys

legends_path, post_path, html_path, css_path, js_path = sys.argv[1:]
with open(legends_path, encoding="utf-8") as handle:
    raw = handle.read()
payload = json.loads(raw)

expected = {
    "seasonId": "launch-2026",
    "firsts": [
        {"type": "first_skill", "playerName": "Alice", "subjectId": 8, "value": 80, "achievedAtMs": 1000},
        {"type": "first_item", "playerName": "Bob", "subjectId": 575, "value": 1, "achievedAtMs": 1100},
    ],
    "pvp": {
        "leaders": [
            {"rank": 1, "playerName": "Ace", "currentStreak": 2, "bestStreak": 5, "qualifiedKills": 7, "lastQualifiedAtMs": 4900},
            {"rank": 2, "playerName": "King", "currentStreak": 2, "bestStreak": 5, "qualifiedKills": 7, "lastQualifiedAtMs": 5000},
            {"rank": 3, "playerName": "Rival", "currentStreak": 4, "bestStreak": 4, "qualifiedKills": 99, "lastQualifiedAtMs": 5100},
        ]
    },
}
assert payload == expected, (payload, expected)

for forbidden_key in (
    "accountId", "playerId", "deathId", "victimName", "rejectReason",
    "source", "sourceEventKey", "detail", "networkEvidence", "updatedAtMs",
):
    assert forbidden_key not in raw, forbidden_key

for canary in (
    "SOURCE_EVENT_PRIVATE_CANARY", "CRACKER_EVENT_PRIVATE_CANARY",
    "DETAIL_PRIVATE_CANARY", "ITEM_DETAIL_PRIVATE_CANARY", "OTHER_SEASON",
    "DEATH_ID_PRIVATE_CANARY", "884422", "884423", "VICTIM_PRIVATE_CANARY",
    "REJECT_REASON_PRIVATE_CANARY", "NETWORK_PRIVATE_CANARY",
):
    assert canary not in raw, canary

with open(post_path, encoding="utf-8") as handle:
    assert json.load(handle) == {"error": "not_available_during_prelaunch"}

with open(html_path, encoding="utf-8") as handle:
    html = handle.read()
with open(css_path, encoding="utf-8") as handle:
    css = handle.read()
with open(js_path, encoding="utf-8") as handle:
    js = handle.read()

assert '<title>Legends of Voidscape</title>' in html
assert 'role="status" aria-live="polite"' in html
assert 'id="world-firsts-empty"' in html
assert 'id="wilderness-empty"' in html
assert 'id="legends-retry"' in html
assert 'src="presence.js" defer' in html
assert 'src="legends.js" defer' in html
assert '@media (max-width: 920px)' in css
assert '@media (max-width: 640px)' in css
assert 'fetch("/api/legends"' in js
assert 'season=' not in js
assert '.textContent' in js and 'document.createElement' in js
assert 'innerHTML' not in js
assert 'console.' not in js
for forbidden in (
    "accountId", "playerId", "victimName", "deathId", "rejectReason",
    "sourceEventKey", "networkEvidence",
):
    assert forbidden not in html + css + js, forbidden
PY

after_hash="$(shasum -a 256 "$db_path" | awk '{print $1}')"
[[ "$before_hash" == "$after_hash" ]]
stop_portal

no_db_port="$(pick_port)"
start_portal "$no_db_port" "$tmp_dir/data-no-db" env
no_db_status="$(curl -sS -o "$tmp_dir/no-db.json" -w '%{http_code}' "http://127.0.0.1:$no_db_port/api/legends")"
[[ "$no_db_status" == "503" ]]
python3 - "$tmp_dir/no-db.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    assert json.load(handle) == {"error": "openrsc_db_not_configured"}
PY
stop_portal

sqlite3 "$empty_db_path" 'CREATE TABLE unrelated (id INTEGER);'
missing_schema_port="$(pick_port)"
start_portal "$missing_schema_port" "$tmp_dir/data-missing-schema" env PORTAL_OPENRSC_DB="$empty_db_path"
missing_schema_status="$(curl -sS -o "$tmp_dir/missing-schema.json" -w '%{http_code}' "http://127.0.0.1:$missing_schema_port/api/legends")"
[[ "$missing_schema_status" == "503" ]]
python3 - "$tmp_dir/missing-schema.json" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    assert json.load(handle) == {"error": "legends_unavailable"}
PY
stop_portal

if PORT="$(pick_port)" \
	PORTAL_DATA_DIR="$tmp_dir/data-invalid-season" \
	PORTAL_LEGENDS_SEASON_ID='../private' \
	node web/portal/dev-server.mjs >"$tmp_dir/invalid-season.log" 2>&1; then
	echo "portal unexpectedly accepted an invalid Legends season" >&2
	exit 1
fi
grep -q 'PORTAL_LEGENDS_SEASON_ID must use 1-32 letters' "$tmp_dir/invalid-season.log"

echo "portal Legends API checks passed"
