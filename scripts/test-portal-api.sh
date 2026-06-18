#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PORT="${PORT:-8799}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-api.XXXXXX")"
fixture_db="$tmp_dir/openrsc-fixture.db"
server_pid=""

cleanup() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" >/dev/null 2>&1 || true
		wait "$server_pid" >/dev/null 2>&1 || true
	fi
	rm -rf "$tmp_dir"
}
trap cleanup EXIT

sqlite3 "$fixture_db" <<'SQL'
CREATE TABLE players (
	id INTEGER PRIMARY KEY,
	username varchar(12) NOT NULL DEFAULT '',
	group_id int(10) DEFAULT 10,
	email varchar(255) DEFAULT NULL,
	pass varchar(512) NOT NULL DEFAULT '',
	salt varchar(250) NOT NULL DEFAULT '',
	combat int(10) DEFAULT 3,
	skill_total int(10) DEFAULT 27,
	x int(5) DEFAULT 216,
	y int(5) DEFAULT 451,
	kills int(10) NOT NULL DEFAULT 0,
	npc_kills int(10) NOT NULL DEFAULT 0,
	deaths int(10) DEFAULT 0,
	quest_points int(5) DEFAULT NULL,
	login_date int(10) DEFAULT 0,
	online tinyint(1) DEFAULT 0,
	male tinyint(1) DEFAULT 1,
	haircolour int(5) DEFAULT 2,
	topcolour int(5) DEFAULT 8,
	trousercolour int(5) DEFAULT 14,
	skincolour int(5) DEFAULT 0,
	headsprite int(5) DEFAULT 1,
	bodysprite int(5) DEFAULT 2,
	creation_date int(10) NOT NULL DEFAULT 0,
	creation_ip varchar(255) NOT NULL DEFAULT '0.0.0.0'
);
CREATE TABLE curstats (
	playerID int(10) NOT NULL PRIMARY KEY,
	attack tinyint(3) NOT NULL DEFAULT 1,
	defense tinyint(3) NOT NULL DEFAULT 1,
	strength tinyint(3) NOT NULL DEFAULT 1,
	hits tinyint(3) NOT NULL DEFAULT 10,
	ranged tinyint(3) NOT NULL DEFAULT 1,
	prayer tinyint(3) NOT NULL DEFAULT 1,
	magic tinyint(3) NOT NULL DEFAULT 1,
	cooking tinyint(3) NOT NULL DEFAULT 1,
	woodcut tinyint(3) NOT NULL DEFAULT 1,
	fletching tinyint(3) NOT NULL DEFAULT 1,
	fishing tinyint(3) NOT NULL DEFAULT 1,
	firemaking tinyint(3) NOT NULL DEFAULT 1,
	crafting tinyint(3) NOT NULL DEFAULT 1,
	smithing tinyint(3) NOT NULL DEFAULT 1,
	mining tinyint(3) NOT NULL DEFAULT 1,
	herblaw tinyint(3) NOT NULL DEFAULT 1,
	agility tinyint(3) NOT NULL DEFAULT 1,
	thieving tinyint(3) NOT NULL DEFAULT 1,
	harvesting tinyint(3) NOT NULL DEFAULT 1,
	runecraft tinyint(3) NOT NULL DEFAULT 1
);
CREATE TABLE maxstats (
	playerID int(10) NOT NULL PRIMARY KEY,
	attack tinyint(3) NOT NULL DEFAULT 1,
	defense tinyint(3) NOT NULL DEFAULT 1,
	strength tinyint(3) NOT NULL DEFAULT 1,
	hits tinyint(3) NOT NULL DEFAULT 10,
	ranged tinyint(3) NOT NULL DEFAULT 1,
	prayer tinyint(3) NOT NULL DEFAULT 1,
	magic tinyint(3) NOT NULL DEFAULT 1,
	cooking tinyint(3) NOT NULL DEFAULT 1,
	woodcut tinyint(3) NOT NULL DEFAULT 1,
	fletching tinyint(3) NOT NULL DEFAULT 1,
	fishing tinyint(3) NOT NULL DEFAULT 1,
	firemaking tinyint(3) NOT NULL DEFAULT 1,
	crafting tinyint(3) NOT NULL DEFAULT 1,
	smithing tinyint(3) NOT NULL DEFAULT 1,
	mining tinyint(3) NOT NULL DEFAULT 1,
	herblaw tinyint(3) NOT NULL DEFAULT 1,
	agility tinyint(3) NOT NULL DEFAULT 1,
	thieving tinyint(3) NOT NULL DEFAULT 1,
	harvesting tinyint(3) NOT NULL DEFAULT 1,
	runecraft tinyint(3) NOT NULL DEFAULT 1
);
CREATE TABLE experience (
	playerID int(10) NOT NULL PRIMARY KEY,
	attack int(9) NOT NULL DEFAULT 0,
	defense int(9) NOT NULL DEFAULT 0,
	strength int(9) NOT NULL DEFAULT 0,
	hits int(9) NOT NULL DEFAULT 4616,
	ranged int(9) NOT NULL DEFAULT 0,
	prayer int(9) NOT NULL DEFAULT 0,
	magic int(9) NOT NULL DEFAULT 0,
	cooking int(9) NOT NULL DEFAULT 0,
	woodcut int(9) NOT NULL DEFAULT 0,
	fletching int(9) NOT NULL DEFAULT 0,
	fishing int(9) NOT NULL DEFAULT 0,
	firemaking int(9) NOT NULL DEFAULT 0,
	crafting int(9) NOT NULL DEFAULT 0,
	smithing int(9) NOT NULL DEFAULT 0,
	mining int(9) NOT NULL DEFAULT 0,
	herblaw int(9) NOT NULL DEFAULT 0,
	agility int(9) NOT NULL DEFAULT 0,
	thieving int(9) NOT NULL DEFAULT 0,
	harvesting int(9) NOT NULL DEFAULT 0,
	runecraft int(9) NOT NULL DEFAULT 0
);
CREATE TABLE capped_experience (
	playerID int(10) NOT NULL PRIMARY KEY,
	attack int(10),
	defense int(10),
	strength int(10),
	hits int(10),
	ranged int(10),
	prayer int(10),
	magic int(10),
	cooking int(10),
	woodcut int(10),
	fletching int(10),
	fishing int(10),
	firemaking int(10),
	crafting int(10),
	smithing int(10),
	mining int(10),
	herblaw int(10),
	agility int(10),
	thieving int(10),
	harvesting int(10),
	runecraft int(10)
);
CREATE TABLE player_cache (
	playerID int(10) NOT NULL,
	type tinyint(1) NOT NULL,
	key varchar(32) NOT NULL,
	value varchar(150) NOT NULL,
	dbid INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL
);
CREATE TABLE invitems (
	playerID int(10) NOT NULL,
	itemID int(10) NOT NULL,
	slot int(5) NOT NULL,
	PRIMARY KEY (playerID, slot)
);
CREATE TABLE itemstatuses (
	itemID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	catalogID int(10) NOT NULL,
	amount int(10) NOT NULL DEFAULT 1,
	noted tinyint(1) NOT NULL DEFAULT 0,
	wielded tinyint(1) NOT NULL DEFAULT 0,
	durability int(5) NOT NULL DEFAULT 0,
	kill_log TEXT DEFAULT NULL
);
INSERT INTO players (
	id, username, group_id, email, pass, salt, combat, skill_total, x, y, kills, npc_kills, deaths,
	quest_points, login_date, online, male, haircolour, topcolour, trousercolour,
	skincolour, headsprite, bodysprite
) VALUES (
	77, 'SmokeHero', 10, 'smoke@example.com', 'fixture-pass', '', 87, 1194, 122, 509, 28, 319, 4,
	47, 1780539120, 0, 1, 2, 8, 14, 0, 1, 2
);
INSERT INTO player_cache (playerID, type, key, value) VALUES
	(77, 1, 'player_title_active', 'conqueror'),
	(77, 0, 'void_path', '1');
INSERT INTO itemstatuses (itemID, catalogID, amount, noted, wielded, durability, kill_log) VALUES
	(9001, 77, 1, 0, 1, 100, NULL),
	(9002, 117, 1, 0, 1, 100, NULL);
INSERT INTO invitems (playerID, itemID, slot) VALUES
	(77, 9001, 0),
	(77, 9002, 1);
SQL

PORT="$PORT" \
	PORTAL_DATA_DIR="$tmp_dir" \
	PORTAL_OPENRSC_DB="$fixture_db" \
	PORTAL_ADMIN_TOKEN="dev-admin" \
	PORTAL_STARTER_IP_DAILY_LIMIT=2 \
	PORTAL_SIGNUP_IP_DAILY_LIMIT=3 \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-api-smoke.log 2>&1 &
server_pid="$!"

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${PORT}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

curl -fsS "http://127.0.0.1:${PORT}/api/health" >/dev/null
PORT="$PORT" PORTAL_ADMIN_TOKEN="dev-admin" PORTAL_SIGNUP_IP_DAILY_LIMIT=3 node web/portal/api-smoke.mjs

signup_code_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'signup_code:VOID%' AND value='1';")"
if [[ "$signup_code_count" -lt 1 ]]; then
	echo "expected at least one available signup_code row in the OpenRSC cache, got ${signup_code_count:-0}"
	exit 1
fi

bad_signup_keys="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'signup_code:%' AND length(key) > 32;")"
if [[ "$bad_signup_keys" != "0" ]]; then
	echo "signup_code cache keys must fit player_cache.key varchar(32)"
	exit 1
fi

starter_card_state="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$starter_card_state" != "1" ]]; then
	echo "expected SmokeHero starter subscription card to be reserved in OpenRSC account cache, got ${starter_card_state:-empty}"
	exit 1
fi

account_subscription_state="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='acct_sub:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ -z "$account_subscription_state" || "$account_subscription_state" -le "$(node -e 'console.log(Date.now())')" ]]; then
	echo "expected SmokeHero account subscription expiry to be stored in OpenRSC account cache"
	exit 1
fi

created_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players WHERE username LIKE 'Smoke%' AND id <> 77;")"
if [[ "$created_count" != "9" ]]; then
	echo "expected 9 portal-created OpenRSC players, got $created_count"
	exit 1
fi

missing_account_links="$(sqlite3 "$fixture_db" <<'SQL'
SELECT COUNT(*)
FROM players p
LEFT JOIN player_cache pc
  ON pc.playerID = p.id
 AND pc.key = 'web_account_id'
 AND pc.value = '1'
WHERE p.username LIKE 'Smoke%'
  AND pc.playerID IS NULL;
SQL
)"
if [[ "$missing_account_links" != "0" ]]; then
	echo "expected all Smoke roster players to be linked to web account 1"
	exit 1
fi

missing_stat_rows="$(sqlite3 "$fixture_db" <<'SQL'
SELECT COUNT(*)
FROM players p
LEFT JOIN curstats c ON c.playerID = p.id
LEFT JOIN maxstats m ON m.playerID = p.id
LEFT JOIN experience e ON e.playerID = p.id
LEFT JOIN capped_experience ce ON ce.playerID = p.id
WHERE p.username LIKE 'Smoke%' AND p.id <> 77
  AND (c.playerID IS NULL OR m.playerID IS NULL OR e.playerID IS NULL OR ce.playerID IS NULL);
SQL
)"
if [[ "$missing_stat_rows" != "0" ]]; then
	echo "portal-created players are missing initialized stat rows"
	exit 1
fi

bad_hits_xp="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM experience e JOIN players p ON p.id = e.playerID WHERE p.username LIKE 'Smoke%' AND p.id <> 77 AND e.hits <> 4000;")"
if [[ "$bad_hits_xp" != "0" ]]; then
	echo "portal-created players should initialize hits xp to the Java createPlayer baseline"
	exit 1
fi

# Group.USER is 10; Group.ADMIN is 1. Portal-created players must never be staff.
elevated_players="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players WHERE username LIKE 'Smoke%' AND id <> 77 AND group_id < 10;")"
if [[ "$elevated_players" != "0" ]]; then
	echo "portal-created players must be created in the default USER group (10), not staff groups"
	exit 1
fi

# ---- PORTAL_PUBLIC_MODE lockdown ----
public_port=$((PORT + 1))
PORT="$public_port" \
	PORTAL_DATA_DIR="$tmp_dir/public-store" \
	PORTAL_ADMIN_TOKEN="dev-admin" \
	PORTAL_PUBLIC_MODE=1 \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-public-smoke.log 2>&1 &
public_pid="$!"
trap 'kill "$public_pid" >/dev/null 2>&1 || true; cleanup' EXIT

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${public_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

expect_status() {
	local expected="$1"; shift
	local actual
	actual="$(curl -s -o /dev/null -w '%{http_code}' "$@")"
	if [[ "$actual" != "$expected" ]]; then
		echo "public-mode lockdown: expected HTTP $expected from $*, got $actual"
		exit 1
	fi
}

# the one public write path still works and mints a code
public_signup="$(curl -fsS -X POST "http://127.0.0.1:${public_port}/api/founder/reservations" -H 'content-type: application/json' -d '{"username":"PublicGuy","email":"public-guy@example.com"}')"
grep -q '"code": "VOID-' <<<"$public_signup" || { echo "public-mode signup should mint a code"; exit 1; }

# everything dangerous is gone
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/accounts/register" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/accounts/login" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/accounts/google/dev" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/founder/simulate-referral" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/subscriptions/redeem" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/characters" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${public_port}/api/character-links/simulate-verify" -H 'content-type: application/json' -d '{}'
expect_status 404 "http://127.0.0.1:${public_port}/api/openrsc/characters/SmokeHero"
expect_status 404 "http://127.0.0.1:${public_port}/api/launcher/manifest.properties"
expect_status 404 "http://127.0.0.1:${public_port}/downloads/pc-client"
expect_status 404 "http://127.0.0.1:${public_port}/downloads/launcher"

# /api/public is sanitized: no fake live-world stats, flagged for the UI
public_payload="$(curl -fsS "http://127.0.0.1:${public_port}/api/public")"
grep -q '"publicMode": true' <<<"$public_payload" || { echo "public-mode /api/public should be flagged"; exit 1; }
grep -q '"playersOnline": 0' <<<"$public_payload" || { echo "public-mode /api/public should not report fake players online"; exit 1; }

# admin stays available with the token, refused without
expect_status 403 "http://127.0.0.1:${public_port}/api/admin/signups"
admin_signups="$(curl -fsS -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${public_port}/api/admin/signups")"
grep -q '"PublicGuy"' <<<"$admin_signups" || { echo "public-mode admin signup list should include the signup"; exit 1; }

kill "$public_pid" >/dev/null 2>&1 || true
wait "$public_pid" >/dev/null 2>&1 || true
trap cleanup EXIT

# Simulate one in-game redemption, then check the admin signup list reflects it.
sqlite3 "$fixture_db" "UPDATE player_cache SET value='2' WHERE playerID=0 AND key=(SELECT key FROM player_cache WHERE playerID=0 AND key LIKE 'signup_code:%' ORDER BY dbid LIMIT 1);"

signups_json="$(curl -fsS -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups")"
if ! grep -q '"redeemed"' <<<"$signups_json"; then
	echo "admin signup list should report the simulated redemption as redeemed"
	exit 1
fi
if ! grep -q '"issued"' <<<"$signups_json"; then
	echo "admin signup list should report unredeemed codes as issued"
	exit 1
fi

csv_header="$(curl -fsS -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups?format=csv" | head -1)"
if [[ "$csv_header" != "id,username,email,code,status,referralRewardCodeCount,referralRewardCodes,createdAt" ]]; then
	echo "admin signup CSV export should include the expected header, got: $csv_header"
	exit 1
fi

sync_result="$(curl -fsS -X POST -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups/sync")"
if ! node -e "
const result = JSON.parse(process.argv[1]);
if (result.skippedRedeemed < 1) throw new Error('sync should skip the redeemed code');
if (result.failed !== 0) throw new Error('sync should not fail against the fixture DB');
" "$sync_result"; then
	echo "admin signup sync failed: $sync_result"
	exit 1
fi
