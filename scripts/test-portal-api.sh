#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PORT="${PORT:-8799}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-api.XXXXXX")"
fixture_db="$tmp_dir/openrsc-fixture.db"
public_admin_token="portal-public-admin-token-fixture-1234567890"
public_abuse_salt="portal-public-abuse-salt-fixture-1234567890"
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
CREATE TABLE equipped (
	playerID int(10) NOT NULL,
	itemID int(10) NOT NULL,
	PRIMARY KEY (playerID, itemID)
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
	CREATE TABLE item_provenance_events (
		eventID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
		itemID INTEGER NOT NULL DEFAULT 0,
		catalogID INTEGER NOT NULL,
		amount INTEGER NOT NULL DEFAULT 1,
		noted INTEGER NOT NULL DEFAULT 0,
		actorID INTEGER NOT NULL DEFAULT 0,
		actor_username TEXT NOT NULL DEFAULT '',
		targetID INTEGER NOT NULL DEFAULT 0,
		target_username TEXT NOT NULL DEFAULT '',
		event_type TEXT NOT NULL,
		source TEXT NOT NULL,
		destination TEXT NOT NULL DEFAULT '',
		command TEXT NOT NULL DEFAULT '',
		x INTEGER NOT NULL DEFAULT 0,
		y INTEGER NOT NULL DEFAULT 0,
		time INTEGER NOT NULL,
		extra TEXT NOT NULL DEFAULT ''
	);
	CREATE TABLE staff_logs (
		id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
		staff_username varchar(12) DEFAULT NULL,
	action tinyint(2) DEFAULT NULL,
	affected_player varchar(12) DEFAULT NULL,
	time int(10) NOT NULL,
	staff_x int(5) NOT NULL DEFAULT 0,
	staff_y int(5) DEFAULT 0,
	affected_x int(5) DEFAULT 0,
	affected_y int(5) DEFAULT 0,
	staff_ip varchar(15) DEFAULT '0.0.0.0',
	affected_ip varchar(15) DEFAULT '0.0.0.0',
	extra varchar(255) DEFAULT NULL
);
INSERT INTO players (
	id, username, group_id, email, pass, salt, combat, skill_total, x, y, kills, npc_kills, deaths,
	quest_points, login_date, online, male, haircolour, topcolour, trousercolour,
	skincolour, headsprite, bodysprite
) VALUES (
	77, 'SmokeHero', 10, 'smoke@example.com', 'fixture-pass', '', 87, 1194, 122, 509, 28, 319, 4,
	47, 1780539120, 0, 1, 2, 8, 14, 0, 1, 2
);
INSERT INTO players (id, username, group_id, email, pass, salt)
VALUES (78, 'TakenHero', 10, 'taken@example.com', 'fixture-pass', '');
INSERT INTO player_cache (playerID, type, key, value) VALUES
	(77, 1, 'player_title_active', 'conqueror'),
	(77, 0, 'void_path', '1');
INSERT INTO itemstatuses (itemID, catalogID, amount, noted, wielded, durability, kill_log) VALUES
	(9001, 77, 1, 0, 1, 100, NULL),
	(9002, 117, 1, 0, 1, 100, NULL),
	(9003, 77, 0, 0, 0, 100, NULL);
INSERT INTO invitems (playerID, itemID, slot) VALUES
	(77, 9001, 0),
	(77, 9002, 1);
INSERT INTO staff_logs (
	staff_username, action, affected_player, time, staff_x, staff_y, affected_x, affected_y,
	staff_ip, affected_ip, extra
	) VALUES
		('Owner', 24, '', strftime('%s', 'now'), 122, 509, 0, 0, '127.0.0.1', '0.0.0.0', 'integrity command=item status=blocked category=item argc=2'),
		('Owner', 24, '', strftime('%s', 'now', '-5 minutes'), 123, 509, 0, 0, '127.0.0.1', '0.0.0.0', 'integrity command=teleport status=allowed category=movement argc=2');
	INSERT INTO item_provenance_events (
		itemID, catalogID, amount, noted, actorID, actor_username, targetID, target_username,
		event_type, source, destination, command, x, y, time, extra
		) VALUES
			(0, 77, 1, 0, 1, 'Owner', 77, 'SmokeHero', 'staff_mint', 'staff_command', 'inventory', 'item', 122, 509, strftime('%s', 'now'), ''),
			(0, 117, 5, 0, 1, 'Owner', 77, 'SmokeHero', 'staff_mint', 'staff_command', 'bank', 'bankitem', 122, 509, strftime('%s', 'now', '-10 minutes'), ''),
			(0, 10, 3, 0, 0, '', 77, 'SmokeHero', 'item_origin', 'npc_drop', 'ground', 'npc_drop', 123, 510, strftime('%s', 'now', '-2 minutes'), 'npc_id=1 npc=rat rare=false'),
			(9001, 77, 1, 0, 77, 'SmokeHero', 77, 'SmokeHero', 'item_transfer', 'player_inventory', 'ground_player_drop', 'drop', 123, 510, strftime('%s', 'now', '-1 minutes'), 'manual_drop=true');
SQL

PORTAL_OPENRSC_DB="$fixture_db" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	node scripts/export-integrity-summary.mjs >/tmp/voidscape-integrity-export-smoke.log

	grep -q '"total24h": 2' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include two staff command rows"; exit 1; }
	grep -q '"blocked24h": 1' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include one blocked staff command"; exit 1; }
		grep -q '"staffMints24h": 2' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include two staff item mint rows"; exit 1; }
		grep -q '"origins24h": 1' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include one item origin row"; exit 1; }
		grep -q '"npcDrops24h": 1' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include one npc drop origin row"; exit 1; }
		grep -q '"originAmount24h": 3' "$tmp_dir/integrity-summary.json" || { echo "integrity export should sum item origin amounts"; exit 1; }
		grep -q '"transfers24h": 1' "$tmp_dir/integrity-summary.json" || { echo "integrity export should include one movement receipt row"; exit 1; }
		grep -q '"amount24h": 6' "$tmp_dir/integrity-summary.json" || { echo "integrity export should sum staff item mint amounts"; exit 1; }
	node -e "
const summary = JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8'));
const findings = JSON.parse(require('fs').readFileSync(process.argv[2], 'utf8'));
if (!summary.economyScans || summary.economyScans.flagged < 1) throw new Error('integrity export should flag fixture economy anomalies');
if (!summary.accountIntegrity || summary.accountIntegrity.flagged < 1) throw new Error('integrity export should flag fixture account anomalies');
if (!Number.isInteger(summary.accountIntegrity.review)) throw new Error('integrity export should include account review totals');
if (!findings.sections || !findings.sections.accountIntegrity) throw new Error('private findings should include the account integrity section');
if (!Array.isArray(findings.findings) || findings.findings.length < 1) throw new Error('integrity export should write private economy findings');
" "$tmp_dir/integrity-summary.json" "$tmp_dir/integrity-findings.json"

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

health_payload="$(curl -fsS "http://127.0.0.1:${PORT}/api/health")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.ok) throw new Error('health endpoint should report ok');
if (!payload.storage || payload.storage.durable !== true) throw new Error('health endpoint should report durable portal storage when PORTAL_DATA_DIR is set');
if (!payload.openRscDb || payload.openRscDb.configured !== true) throw new Error('health endpoint should report the OpenRSC DB bridge');
" "$health_payload"
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
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_AT="2026-07-11T18:00:00Z" \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-public-smoke.log 2>&1 &
public_pid="$!"
trap 'kill "$public_pid" >/dev/null 2>&1 || true; cleanup' EXIT

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${public_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

public_health_payload="$(curl -fsS "http://127.0.0.1:${public_port}/api/health")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.config || payload.config.publicReady !== true) throw new Error('public health should report launch config ready');
if (payload.config.abuseHashSaltConfigured !== true) throw new Error('public health should report a configured abuse hash salt');
if (payload.config.adminTokenConfigured !== true) throw new Error('public health should report a configured admin token');
if ((payload.config.issues || []).length) throw new Error('public health should not report config issues: ' + JSON.stringify(payload.config.issues));
" "$public_health_payload"

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

# launch-day public downloads stay open without requiring Discord or portal login
if [[ -f "Client_Base/Open_RSC_Client.jar" ]]; then
	expect_status 200 "http://127.0.0.1:${public_port}/api/launcher/manifest.properties"
	launcher_manifest="$(curl -fsS "http://127.0.0.1:${public_port}/api/launcher/manifest.properties")"
	grep -q '^file\.1\.path=Open_RSC_Client\.jar$' <<<"$launcher_manifest" || { echo "launcher manifest should download the client to the jar name used by Play"; exit 1; }
	expect_status 200 "http://127.0.0.1:${public_port}/downloads/client-runtime"
fi
if [[ -f "PC_Launcher/OpenRSC.jar" ]]; then
	expect_status 200 "http://127.0.0.1:${public_port}/downloads/launcher"
fi
default_android_apk="Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
if [[ -f "$default_android_apk" ]]; then
	expect_status 200 "http://127.0.0.1:${public_port}/downloads/android-apk"
else
	expect_status 404 "http://127.0.0.1:${public_port}/downloads/android-apk"
fi

# /api/public is sanitized: no fake live-world stats, flagged for the UI
public_payload="$(curl -fsS "http://127.0.0.1:${public_port}/api/public")"
grep -q '"publicMode": true' <<<"$public_payload" || { echo "public-mode /api/public should be flagged"; exit 1; }
grep -q '"playersOnline": 0' <<<"$public_payload" || { echo "public-mode /api/public should not report fake players online"; exit 1; }
grep -q '"mode": "hybrid-p2p-enabled"' <<<"$public_payload" || { echo "public-mode /api/public should expose the global launch world mode"; exit 1; }
grep -q '"subscriptionGrantsMembers": false' <<<"$public_payload" || { echo "public-mode /api/public should expose subscription as non-membership-gating"; exit 1; }
grep -q '"launch": {' <<<"$public_payload" || { echo "public-mode /api/public should expose launch countdown metadata"; exit 1; }
grep -q '"openAt": "2026-07-11T18:00:00.000Z"' <<<"$public_payload" || { echo "public-mode /api/public should expose the configured launch timestamp"; exit 1; }
grep -q '"Play in browser"' <<<"$public_payload" || { echo "public-mode /api/public should expose the web client action"; exit 1; }
grep -q '"Voidscape launcher"' <<<"$public_payload" || { echo "public-mode /api/public should expose the launcher download"; exit 1; }
grep -q '"Android APK"' <<<"$public_payload" || { echo "public-mode /api/public should expose the Android APK download"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
const apkBuilt = process.argv[2] === '1';
const android = (payload.downloads || []).find((row) => row.slug === 'android-apk');
if (!android) throw new Error('public payload should keep an Android APK row for launch-open UI copy');
if (apkBuilt) {
	if (android.available !== true) throw new Error('built Android APK should be public in the launch-open chooser');
	if (android.url !== '/downloads/android-apk') throw new Error('built Android APK should link to /downloads/android-apk');
	if (!/^[0-9a-f]{64}$/.test(android.sha256 || '')) throw new Error('built Android APK should expose sha256');
} else {
	if (android.available === true) throw new Error('missing Android APK should not be marked available');
	if (android.url !== '#') throw new Error('missing Android APK row should not link to a download');
}
" "$public_payload" "$([[ -f "$default_android_apk" ]] && printf 1 || printf 0)"
	grep -q '"blocked24h": 1' <<<"$public_payload" || { echo "public-mode /api/public should expose sanitized integrity counts"; exit 1; }
	grep -q '"staffMints24h": 2' <<<"$public_payload" || { echo "public-mode /api/public should expose sanitized item receipt counts"; exit 1; }
	grep -q '"npcDrops24h": 1' <<<"$public_payload" || { echo "public-mode /api/public should expose sanitized npc drop receipt counts"; exit 1; }
	grep -q '"transfers24h": 1' <<<"$public_payload" || { echo "public-mode /api/public should expose sanitized movement receipt counts"; exit 1; }
	grep -q '"economyScans"' <<<"$public_payload" || { echo "public-mode /api/public should expose economy scan summary"; exit 1; }
	grep -q '"accountIntegrity"' <<<"$public_payload" || { echo "public-mode /api/public should expose account integrity summary"; exit 1; }
	grep -q '"artifacts"' <<<"$public_payload" || { echo "public-mode /api/public should expose build artifact proof"; exit 1; }
	grep -q '"manifest"' <<<"$public_payload" || { echo "public-mode /api/public should expose launcher manifest proof"; exit 1; }
	if grep -q '"PC client"' <<<"$public_payload"; then
		echo "public-mode /api/public should not promote the raw PC client jar"
		exit 1
	fi

	landing_html="$(curl -fsS "http://127.0.0.1:${public_port}/")"
	grep -q '<title>Voidscape Prelaunch</title>' <<<"$landing_html" || { echo "landing page should be prelaunch-branded"; exit 1; }
	grep -q 'Launch opens in' <<<"$landing_html" || { echo "landing page should include the launch countdown"; exit 1; }
	if grep -q 'href="https://voidscape.gg/play/"' <<<"$landing_html"; then
		echo "prelaunch landing should not send visitors to the web client before launch"
		exit 1
	fi
	if grep -q 'data-funnel-event="play_web"' <<<"$landing_html"; then
		echo "prelaunch landing should not tag play-web clicks before launch"
		exit 1
	fi
	grep -q 'Reserve Name + Free Card' <<<"$landing_html" || { echo "landing page should prioritize the reserve/free-card CTA"; exit 1; }
	grep -q 'data-prelaunch-auth-cta' <<<"$landing_html" || { echo "landing page should include a visible account sign-in CTA"; exit 1; }
	grep -q 'One account. Every platform.' <<<"$landing_html" || { echo "landing page should explain platform support without play buttons"; exit 1; }
	grep -q 'href="/features"' <<<"$landing_html" || { echo "landing page should link to the full feature guide"; exit 1; }
	grep -q 'href="/transparency"' <<<"$landing_html" || { echo "landing page should link to the transparency page"; exit 1; }
	grep -q 'href="/privacy"' <<<"$landing_html" || { echo "landing page should link to the privacy policy"; exit 1; }
	grep -q 'href="/data-deletion"' <<<"$landing_html" || { echo "landing page should link to data deletion"; exit 1; }
	grep -q 'href="https://github.com/voidscape-gg/voidscape"' <<<"$landing_html" || { echo "landing page should link to the Voidscape AGPL source mirror"; exit 1; }
	if grep -q 'Open-RSC/Core-Framework' <<<"$landing_html"; then
		echo "landing page should not use upstream-only source disclosure"
		exit 1
	fi
	grep -q 'landing-install-help' <<<"$landing_html" || { echo "landing page should include install help"; exit 1; }
	grep -q 'id="download-actions" hidden' <<<"$landing_html" || { echo "landing page should keep post-launch download actions hidden before launch"; exit 1; }
	grep -q 'id="landing-launch-proof" hidden' <<<"$landing_html" || { echo "landing page should keep launch proof hidden before launch"; exit 1; }
	features_html="$(curl -fsS "http://127.0.0.1:${public_port}/features")"
	grep -q '<title>Voidscape Features</title>' <<<"$features_html" || { echo "/features should serve the feature guide"; exit 1; }
	grep -q 'Prelaunch feature guide' <<<"$features_html" || { echo "/features should use prelaunch wording"; exit 1; }
	if grep -q 'landing-live-basics' <<<"$landing_html"; then
		echo "prelaunch landing should not include the redundant live basics strip"
		exit 1
	fi
	if grep -q 'Why reserve early' <<<"$landing_html"; then
		echo "prelaunch landing should not include the redundant why-reserve strip"
		exit 1
	fi
	if grep -q 'data-funnel-event="download_launcher"' <<<"$landing_html"; then
		echo "prelaunch landing should not promote launcher downloads before launch"
		exit 1
	fi
	if grep -q 'class="landing-integrity' <<<"$landing_html"; then
		echo "landing page should not embed the full transparency dashboard"
		exit 1
	fi
	transparency_html="$(curl -fsS "http://127.0.0.1:${public_port}/transparency")"
	grep -q 'transparency.js' <<<"$transparency_html" || { echo "/transparency should load the standalone transparency script"; exit 1; }
	grep -q 'trust-staff-board' <<<"$transparency_html" || { echo "/transparency should include the staff ledger board"; exit 1; }
	grep -q 'trust-source-board' <<<"$transparency_html" || { echo "/transparency should include the build proof board"; exit 1; }
	expect_status 200 "http://127.0.0.1:${public_port}/privacy"
	expect_status 200 "http://127.0.0.1:${public_port}/data-deletion"
	privacy_html="$(curl -fsS "http://127.0.0.1:${public_port}/privacy")"
	grep -q 'Voidscape account data should stay boring and explainable.' <<<"$privacy_html" || { echo "/privacy should render the policy copy"; exit 1; }
	grep -q 'href="https://github.com/voidscape-gg/voidscape"' <<<"$privacy_html" || { echo "/privacy should link to the Voidscape AGPL source mirror"; exit 1; }
	if grep -q 'Open-RSC/Core-Framework' <<<"$privacy_html"; then
		echo "/privacy should not use upstream-only source disclosure"
		exit 1
	fi
	deletion_html="$(curl -fsS "http://127.0.0.1:${public_port}/data-deletion")"
	grep -q 'Request account data deletion.' <<<"$deletion_html" || { echo "/data-deletion should render the deletion flow"; exit 1; }
	grep -q 'href="https://github.com/voidscape-gg/voidscape"' <<<"$deletion_html" || { echo "/data-deletion should link to the Voidscape AGPL source mirror"; exit 1; }
	if grep -q 'Open-RSC/Core-Framework' <<<"$deletion_html"; then
		echo "/data-deletion should not use upstream-only source disclosure"
		exit 1
	fi

	integrity_payload="$(curl -fsS "http://127.0.0.1:${public_port}/api/integrity")"
	grep -q '"total24h": 2' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose exported staff command totals"; exit 1; }
	grep -q '"staffMints24h": 2' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose exported item receipt totals"; exit 1; }
	grep -q '"origins24h": 1' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose exported item origin totals"; exit 1; }
	grep -q '"transfers24h": 1' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose exported movement receipt totals"; exit 1; }
	grep -q '"flagged":' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose exported scan totals"; exit 1; }
	grep -q '"accountIntegrity"' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose account integrity totals"; exit 1; }
	grep -q '"sha256"' <<<"$integrity_payload" || { echo "public-mode /api/integrity should expose build hashes"; exit 1; }

	funnel_click="$(curl -fsS -X POST "http://127.0.0.1:${public_port}/api/funnel/click" -H 'content-type: application/json' -d '{"event":"transparency","target":"Trust","href":"/transparency","page":"/?utm_source=smoke&utm_campaign=portal","utm":{"utm_source":"smoke","utm_campaign":"portal"}}')"
	grep -q '"event": "transparency"' <<<"$funnel_click" || { echo "public-mode funnel click endpoint should accept transparency events"; exit 1; }

# admin stays available with the token, refused without
expect_status 403 "http://127.0.0.1:${public_port}/api/admin/signups"
admin_funnel="$(curl -fsS -H "x-portal-admin-token: ${public_admin_token}" "http://127.0.0.1:${public_port}/api/admin/funnel")"
grep -q '"event": "transparency"' <<<"$admin_funnel" || { echo "admin funnel summary should include tracked transparency clicks"; exit 1; }
admin_signups="$(curl -fsS -H "x-portal-admin-token: ${public_admin_token}" "http://127.0.0.1:${public_port}/api/admin/signups")"
grep -q '"PublicGuy"' <<<"$admin_signups" || { echo "public-mode admin signup list should include the signup"; exit 1; }

kill "$public_pid" >/dev/null 2>&1 || true
wait "$public_pid" >/dev/null 2>&1 || true
trap cleanup EXIT

# Public Android APK downloads can also be pointed at an explicit artifact path.
android_public_port=$((PORT + 3))
android_release_apk="$tmp_dir/voidscape-release.apk"
dd if=/dev/zero of="$android_release_apk" bs=2048 count=1 >/dev/null 2>&1
	PORT="$android_public_port" \
	PORTAL_DATA_DIR="$tmp_dir/android-public-store" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_ANDROID_APK="$android_release_apk" \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-android-public-smoke.log 2>&1 &
android_public_pid="$!"
trap 'kill "$android_public_pid" >/dev/null 2>&1 || true; cleanup' EXIT

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${android_public_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

android_public_payload="$(curl -fsS "http://127.0.0.1:${android_public_port}/api/public")"
node -e "
const payload = JSON.parse(process.argv[1]);
const android = (payload.downloads || []).find((row) => row.slug === 'android-apk');
if (!android || android.available !== true) throw new Error('explicit public Android APK should be available');
if (android.url !== '/downloads/android-apk') throw new Error('explicit public Android APK should link to /downloads/android-apk');
if (!/^[0-9a-f]{64}$/.test(android.sha256 || '')) throw new Error('explicit public Android APK should expose sha256');
" "$android_public_payload"
expect_status 200 "http://127.0.0.1:${android_public_port}/downloads/android-apk"

kill "$android_public_pid" >/dev/null 2>&1 || true
wait "$android_public_pid" >/dev/null 2>&1 || true
trap cleanup EXIT

# ---- PORTAL_LAUNCH_SIGNUP_MODE public account flow ----
launch_port=$((PORT + 2))
PORT="$launch_port" \
	PORTAL_DATA_DIR="$tmp_dir/launch-store" \
	PORTAL_OPENRSC_DB="$fixture_db" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2026-07-11T18:00:00Z" \
	PORTAL_GOOGLE_CLIENT_ID="test-google-client" \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-launch-signup-smoke.log 2>&1 &
launch_pid="$!"
trap 'kill "$launch_pid" >/dev/null 2>&1 || true; cleanup' EXIT

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${launch_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

launch_public_payload="$(curl -fsS "http://127.0.0.1:${launch_port}/api/public")"
launch_health_payload="$(curl -fsS "http://127.0.0.1:${launch_port}/api/health")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.publicMode) throw new Error('launch health should report public mode');
if (!payload.launchSignupMode) throw new Error('launch health should report launch-signup mode');
if (!payload.storage || payload.storage.durable !== true) throw new Error('launch health should report durable portal storage');
if (!payload.openRscDb || payload.openRscDb.configured !== true) throw new Error('launch health should report the OpenRSC DB bridge');
if (!payload.config || payload.config.publicReady !== true) throw new Error('launch health should report public config ready');
if (payload.config.abuseHashSaltConfigured !== true) throw new Error('launch health should report a configured abuse hash salt');
if (payload.config.adminTokenConfigured !== true) throw new Error('launch health should report a configured admin token');
if ((payload.config.issues || []).length) throw new Error('launch health should not report config issues: ' + JSON.stringify(payload.config.issues));
" "$launch_health_payload"
grep -q '"launchSignupMode": true' <<<"$launch_public_payload" || { echo "launch-signup mode should be exposed to the frontend"; exit 1; }
grep -q '"memberWorld": true' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose the global members-world flag"; exit 1; }
grep -q '"packetRegistration": false' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose portal-first registration"; exit 1; }
grep -q '"oauth": {' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose OAuth config"; exit 1; }
grep -q '"clientId": "test-google-client"' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose the Google client id when configured"; exit 1; }

launch_signup="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"LaunchGuy","email":"launch-guy@example.com","password":"Launchpass1"}')"
grep -q '"token":' <<<"$launch_signup" || { echo "launch-signup account registration should issue a session"; exit 1; }
grep -q '"LaunchGuy"' <<<"$launch_signup" || { echo "launch-signup account registration should create the requested username"; exit 1; }
grep -q '"source": "openrsc-sqlite-created"' <<<"$launch_signup" || { echo "launch-signup registration should create a real OpenRSC save immediately"; exit 1; }
grep -q '"linkStatus": "linked"' <<<"$launch_signup" || { echo "launch-signup registration should link the OpenRSC save immediately"; exit 1; }
grep -q '"starterSubscriptionCards": 1' <<<"$launch_signup" || { echo "launch-signup registration should reserve one starter subscription card"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('launch signup should use exactly one character slot');
if (payload.characters[0].name !== 'LaunchGuy') throw new Error('launch signup should return LaunchGuy as the first character');
if (payload.characters[0].source !== 'openrsc-sqlite-created') throw new Error('launch signup should return the OpenRSC-created source');
" "$launch_signup"
launch_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_signup")"
if [[ -z "$launch_token" ]]; then
	echo "launch-signup registration returned an empty token"
	exit 1
fi

launch_account="$(curl -fsS -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account")"
grep -q '"email": "launch-guy@example.com"' <<<"$launch_account" || { echo "launch-signup session should read the created account"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('launch account should keep exactly one character slot used');
" "$launch_account"
launch_login="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass1"}')"
grep -q '"token":' <<<"$launch_login" || { echo "launch-signup returning login should issue a session"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('returning login should expose one used character slot');
if (payload.characters[0].name !== 'LaunchGuy') throw new Error('returning login should expose the first character');
" "$launch_login"
launch_login_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_login")"
if [[ -z "$launch_login_token" ]]; then
	echo "launch-signup returning login returned an empty token"
	exit 1
fi

launch_recovery_codes="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/security/recovery-codes" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.codes) || payload.codes.length !== 8) throw new Error('launch-signup recovery endpoint should generate eight one-time codes');
if (!payload.security || !payload.security.recoveryCodes || payload.security.recoveryCodes.activeCount !== 8) throw new Error('launch-signup security state should count active recovery codes');
" "$launch_recovery_codes"
launch_recovery_code="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write((payload.codes && payload.codes[0]) || '');" "$launch_recovery_codes")"
if [[ -z "$launch_recovery_code" ]]; then
	echo "launch-signup recovery endpoint returned an empty code"
	exit 1
fi
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/recover-password" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","code":"VOID-BAD-BAD","newPassword":"Launchpass2"}'

launch_recovered="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/recover-password" \
	-H 'content-type: application/json' \
	-d "{\"email\":\"launch-guy@example.com\",\"code\":\"${launch_recovery_code}\",\"newPassword\":\"Launchpass2\"}")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.token) throw new Error('launch-signup password recovery should issue a new session');
if (!payload.security || !payload.security.recoveryCodes || payload.security.recoveryCodes.activeCount !== 7) throw new Error('launch-signup password recovery should consume exactly one recovery code');
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('launch-signup password recovery should preserve the character roster');
" "$launch_recovered"
launch_recovered_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_recovered")"
if [[ -z "$launch_recovered_token" || "$launch_recovered_token" == "$launch_token" || "$launch_recovered_token" == "$launch_login_token" ]]; then
	echo "launch-signup password recovery should create a fresh session token"
	exit 1
fi
expect_status 401 -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -H "authorization: Bearer ${launch_login_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass1"}'
launch_login_after_recovery="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}')"
grep -q '"token":' <<<"$launch_login_after_recovery" || { echo "launch-signup recovered password should support returning login"; exit 1; }
launch_token="$launch_recovered_token"

launch_player_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players WHERE username='LaunchGuy' AND group_id=10;")"
if [[ "$launch_player_count" != "1" ]]; then
	echo "launch-signup registration should create one normal User-ranked OpenRSC player"
	exit 1
fi
launch_link_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players p JOIN player_cache pc ON pc.playerID=p.id AND pc.key='web_account_id' AND pc.value='1' WHERE p.username='LaunchGuy';")"
if [[ "$launch_link_count" != "1" ]]; then
	echo "launch-signup OpenRSC player should be linked to the web account"
	exit 1
fi

launch_starter_card_state="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_starter_card_state" != "1" ]]; then
	echo "launch-signup registration should sync one waiting starter card marker"
	exit 1
fi

launch_admin_waiting="$(curl -fsS -H "x-portal-admin-token: ${public_admin_token}" "http://127.0.0.1:${launch_port}/api/admin/accounts/1")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('admin account state should show starter card waiting');
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('admin account state should expose one waiting starter card');
" "$launch_admin_waiting"
launch_starter_marker_total_before_extra="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")"

launch_extra_character="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchAlt","gamePassword":"Launchalt1"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 2) throw new Error('creating a second launch character should use a second character slot');
const extra = payload.characters.find((character) => character.name === 'LaunchAlt');
if (!extra) throw new Error('second launch character should appear in the account roster');
if (extra.source !== 'openrsc-sqlite-created') throw new Error('second launch character should create a real OpenRSC save');
if (extra.linkStatus !== 'linked') throw new Error('second launch character should be linked to the account');
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('second character creation should leave the starter card waiting');
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('second character creation should not grant another starter card');
if (!Array.isArray(payload.rewards.cards) || payload.rewards.cards.length !== 1) throw new Error('second character creation should leave exactly one visible starter-card entitlement');
" "$launch_extra_character"
launch_player_count_after_extra="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players WHERE username IN ('LaunchGuy', 'LaunchAlt') AND group_id=10;")"
if [[ "$launch_player_count_after_extra" != "2" ]]; then
	echo "second launch character creation should create exactly two normal User-ranked OpenRSC players"
	exit 1
fi
launch_extra_link_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM players p JOIN player_cache pc ON pc.playerID=p.id AND pc.key='web_account_id' AND pc.value='1' WHERE p.username='LaunchAlt';")"
if [[ "$launch_extra_link_count" != "1" ]]; then
	echo "second launch OpenRSC player should be linked to the same web account"
	exit 1
fi
launch_starter_marker_count_after_extra="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter_card:1' AND value='1';")"
if [[ "$launch_starter_marker_count_after_extra" != "1" ]]; then
	echo "second launch character creation should preserve exactly one waiting starter card marker"
	exit 1
fi
launch_starter_marker_total_after_extra="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")"
if [[ "$launch_starter_marker_total_after_extra" != "$launch_starter_marker_total_before_extra" ]]; then
	echo "second launch character creation should not mint additional starter-card markers"
	exit 1
fi

launch_revoke="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"revoke"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'none') throw new Error('admin revoke should clear an unclaimed starter card');
if (payload.rewards.starterSubscriptionCards !== 0) throw new Error('admin revoke should leave zero waiting starter cards');
" "$launch_revoke"
launch_waiting_after_revoke="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter_card:1' AND value='1';")"
if [[ "$launch_waiting_after_revoke" != "0" ]]; then
	echo "admin starter-card revoke should clear the unclaimed OpenRSC marker"
	exit 1
fi

launch_grant="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"grant"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('admin grant should restore starter card waiting state');
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('admin grant should expose one waiting starter card');
" "$launch_grant"
launch_waiting_after_grant="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_waiting_after_grant" != "1" ]]; then
	echo "admin starter-card grant should restore the OpenRSC marker"
	exit 1
fi

sqlite3 "$fixture_db" "UPDATE player_cache SET value='2' WHERE playerID=0 AND key='starter_card:1';"
launch_account_claimed="$(curl -fsS -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'claimed') throw new Error('account state should show a claimed starter card');
if (payload.rewards.starterSubscriptionCards !== 0) throw new Error('claimed starter cards should not remain waiting');
if (payload.rewards.starterSubscriptionCardsClaimed !== 1) throw new Error('claimed starter card count should be exposed');
" "$launch_account_claimed"

launch_revoke_claimed="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"revoke"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'claimed') throw new Error('admin revoke should not erase an already claimed starter card');
if (payload.rewards.starterSubscriptionCardsClaimed !== 1) throw new Error('claimed starter card should remain visible after revoke');
" "$launch_revoke_claimed"
launch_claimed_after_revoke="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_claimed_after_revoke" != "2" ]]; then
	echo "admin starter-card revoke should preserve an already claimed marker"
	exit 1
fi

expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchGuy","gamePassword":"playpass"}'
google_nonce_payload="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/oauth/google/nonce" -H 'content-type: application/json' -d '{}')"
grep -q '"nonce":' <<<"$google_nonce_payload" || { echo "launch-signup Google nonce endpoint should mint a nonce when configured"; exit 1; }
google_nonce="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.nonce || '');" "$google_nonce_payload")"
if [[ -z "$google_nonce" ]]; then
	echo "launch-signup Google nonce was empty"
	exit 1
fi
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/google" \
	-H 'content-type: application/json' \
	-d "{\"credential\":\"bad-token\",\"nonce\":\"${google_nonce}\",\"username\":\"GoogleGuy\",\"gamePassword\":\"googlepass\"}"
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/accounts/google/dev" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/founder/simulate-referral" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/character-links/simulate-verify" -H 'content-type: application/json' -d '{}'
expect_status 404 "http://127.0.0.1:${launch_port}/api/openrsc/characters/LaunchGuy"

kill "$launch_pid" >/dev/null 2>&1 || true
wait "$launch_pid" >/dev/null 2>&1 || true
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
if [[ "$csv_header" != "id,username,email,betaTester,discordUserId,discordDisplayName,code,status,referralRewardCodeCount,referralRewardCodes,createdAt" ]]; then
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
