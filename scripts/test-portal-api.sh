#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

PORT="${PORT:-8799}"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-api.XXXXXX")"
fixture_db="$tmp_dir/openrsc-fixture.db"
base_fixture_db="$tmp_dir/openrsc-base-fixture.db"
launch_fixture_db="$tmp_dir/openrsc-launch-fixture.db"
default_android_apk="Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
public_android_apk=""
if [[ -f "$default_android_apk" ]]; then
	public_android_apk="$default_android_apk"
fi
public_admin_token="portal-public-admin-token-fixture-1234567890"
public_abuse_salt="portal-public-abuse-salt-fixture-1234567890"
server_pid=""
captcha_pid=""
launch_window_pid=""
post_cutoff_pid=""
public_pid=""
no_db_pid=""
world_live_pid=""
verify_pid=""
drain_pid=""
drain_restart_pid=""
drain_request_pid=""
vs096_pid=""
vs096_request_pid=""
vs096_google_jwks_pid=""

cleanup() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" >/dev/null 2>&1 || true
		wait "$server_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$captcha_pid" ]]; then
		kill "$captcha_pid" >/dev/null 2>&1 || true
		wait "$captcha_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$launch_window_pid" ]]; then
		kill "$launch_window_pid" >/dev/null 2>&1 || true
		wait "$launch_window_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$post_cutoff_pid" ]]; then
		kill "$post_cutoff_pid" >/dev/null 2>&1 || true
		wait "$post_cutoff_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$public_pid" ]]; then
		kill "$public_pid" >/dev/null 2>&1 || true
		wait "$public_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$no_db_pid" ]]; then
		kill "$no_db_pid" >/dev/null 2>&1 || true
		wait "$no_db_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$world_live_pid" ]]; then
		kill "$world_live_pid" >/dev/null 2>&1 || true
		wait "$world_live_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$verify_pid" ]]; then
		kill "$verify_pid" >/dev/null 2>&1 || true
		wait "$verify_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$drain_request_pid" ]]; then
		kill "$drain_request_pid" >/dev/null 2>&1 || true
		wait "$drain_request_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$drain_pid" ]]; then
		kill "$drain_pid" >/dev/null 2>&1 || true
		wait "$drain_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$drain_restart_pid" ]]; then
		kill "$drain_restart_pid" >/dev/null 2>&1 || true
		wait "$drain_restart_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$vs096_request_pid" ]]; then
		kill "$vs096_request_pid" >/dev/null 2>&1 || true
		wait "$vs096_request_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$vs096_pid" ]]; then
		kill "$vs096_pid" >/dev/null 2>&1 || true
		wait "$vs096_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$vs096_google_jwks_pid" ]]; then
		kill "$vs096_google_jwks_pid" >/dev/null 2>&1 || true
		wait "$vs096_google_jwks_pid" >/dev/null 2>&1 || true
	fi
	rm -rf "$tmp_dir"
}
trap cleanup EXIT

initialize_public_store() {
	PORTAL_DATA_DIR="$1" node web/portal/dev-server.mjs --initialize-store >/dev/null
}

expect_invalid_android_play_url() {
	local url="$1"
	local slug="$2"
	local log="$tmp_dir/android-play-invalid-${slug}.log"
	local pid
	PORT=0 \
		PORTAL_DATA_DIR="$tmp_dir/android-play-invalid-${slug}" \
		PORTAL_ANDROID_PLAY_URL="$url" \
		node web/portal/dev-server.mjs >"$log" 2>&1 &
	pid="$!"
	for _ in {1..20}; do
		if ! kill -0 "$pid" >/dev/null 2>&1; then
			break
		fi
		sleep 0.05
	done
	if kill -0 "$pid" >/dev/null 2>&1; then
		kill "$pid" >/dev/null 2>&1 || true
		wait "$pid" >/dev/null 2>&1 || true
		echo "PORTAL_ANDROID_PLAY_URL should reject $url"
		exit 1
	fi
	if wait "$pid" >/dev/null 2>&1; then
		echo "invalid PORTAL_ANDROID_PLAY_URL should fail startup: $url"
		exit 1
	fi
	grep -q 'must be the official https://play.google.com/store/apps/details?id=com.voidscape.gg listing' "$log" || {
		echo "invalid PORTAL_ANDROID_PLAY_URL should explain the accepted listing"
		exit 1
	}
}

expect_invalid_android_play_url "http://play.google.com/store/apps/details?id=com.voidscape.gg" "http"
expect_invalid_android_play_url "https://example.com/store/apps/details?id=com.voidscape.gg" "host"
expect_invalid_android_play_url "https://play.google.com/store/apps/details?id=com.example.wrong" "package"
expect_invalid_android_play_url "https://play.google.com/apps/internaltest/4700211566888170922" "internal-test"

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
	cameraauto tinyint(1) DEFAULT 0,
	creation_date int(10) NOT NULL DEFAULT 0,
	creation_ip varchar(255) NOT NULL DEFAULT '0.0.0.0',
	banned bigint(20) NOT NULL DEFAULT 0
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
CREATE TABLE player_security_changes (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	playerID int(10) NOT NULL,
	eventAlias varchar(20) NOT NULL,
	date int(10) NOT NULL DEFAULT 0,
	ip varchar(255) DEFAULT '0.0.0.0',
	message text DEFAULT NULL
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
CREATE TABLE bank (
	playerID int(10) NOT NULL,
	itemID int(10) NOT NULL,
	slot int(5) NOT NULL,
	PRIMARY KEY (playerID, slot)
);
CREATE TABLE quests (
	playerID int(10) NOT NULL,
	questID int(10) NOT NULL,
	stage int(5) NOT NULL DEFAULT 0,
	PRIMARY KEY (playerID, questID)
);
CREATE TABLE live_feeds (
	id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
	username varchar(12) NOT NULL,
	message varchar(255) NOT NULL,
	time int(10) NOT NULL
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
INSERT INTO players (id, username, group_id, email, pass, salt, skill_total, npc_kills, deaths, creation_date)
VALUES
	(79, 'HiddenHero', 10, 'hidden@example.com', 'fixture-pass', '', 1500, 900, 40, strftime('%s', 'now', '-100 days')),
	(80, 'StaffBoss', 2, 'staff@example.com', 'fixture-pass', '', 1782, 9999, 999, strftime('%s', 'now', '-200 days')),
	(81, 'BannedHero', 10, 'banned@example.com', 'fixture-pass', '', 1700, 8888, 888, strftime('%s', 'now', '-150 days'));
UPDATE players SET banned = 4102444800000 WHERE id = 81;
INSERT INTO maxstats (playerID, attack, defense, strength, hits, ranged, prayer, magic, cooking, woodcut, fletching, fishing, firemaking, crafting, smithing, mining, herblaw, agility, thieving) VALUES
	(77, 99, 70, 71, 72, 73, 74, 75, 76, 77, 78, 79, 80, 81, 82, 83, 84, 85, 86),
	(79, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99),
	(80, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99),
	(81, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99);
INSERT INTO experience (playerID, attack, defense, strength, hits, ranged, prayer, magic, cooking, woodcut, fletching, fishing, firemaking, crafting, smithing, mining, herblaw, agility, thieving) VALUES
	(77, 4000, 800, 804, 808, 812, 816, 820, 824, 828, 832, 836, 840, 844, 848, 852, 856, 860, 864),
	(79, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000),
	(80, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000, 12000),
	(81, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000, 16000);
INSERT INTO player_cache (playerID, type, key, value) VALUES
	(77, 1, 'player_title_active', 'founder'),
	(77, 1, 'player_honorific_active', 'saint'),
	(77, 1, 'pt_u_paragon', '1'),
	(0, 0, 'pt_first_date_paragon', strftime('%s', 'now', '-2 days')),
	(79, 2, 'setting_hide_scores', 'true'),
	(79, 1, 'pt_u_trailblazer', '1'),
	(0, 0, 'pt_first_date_trailblazer', strftime('%s', 'now', '-3 days')),
	(77, 0, 'void_path', '1'),
	(0, 3, 'char_sub:77', '4102444800000');
INSERT INTO itemstatuses (itemID, catalogID, amount, noted, wielded, durability, kill_log) VALUES
	(9001, 77, 1, 0, 1, 100, NULL),
	(9002, 117, 1, 0, 1, 100, NULL),
	(9003, 77, 0, 0, 0, 100, NULL),
	(9010, 10, 5000, 0, 0, 100, NULL),
	(9011, 10, 9000, 0, 0, 100, NULL),
	(9012, 10, 999999, 0, 0, 100, NULL);
INSERT INTO invitems (playerID, itemID, slot) VALUES
	(77, 9001, 0),
	(77, 9002, 1);
INSERT INTO bank (playerID, itemID, slot) VALUES
	(77, 9010, 0),
	(79, 9011, 0),
	(80, 9012, 0);
INSERT INTO quests (playerID, questID, stage) VALUES
	(77, 1, -1),
	(79, 1, -1),
	(80, 1, -1);
INSERT INTO live_feeds (username, message, time) VALUES
	('SmokeHero', 'has completed the Dragon Slayer quest and now has 47 quest points!', strftime('%s', 'now')),
	('HiddenHero', 'has achieved level-99 in Attack, the maximum possible! Congratulations!', strftime('%s', 'now', '-1 minute')),
	('StaffBoss', 'has obtained a Dragon axe!', strftime('%s', 'now', '-2 minutes')),
	('SmokeHero', 'has PKed HiddenHero', strftime('%s', 'now', '-3 minutes'));
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
cp "$fixture_db" "$base_fixture_db"
sqlite3 "$base_fixture_db" <<'SQL'
DELETE FROM bank WHERE playerID IN (79, 80, 81);
DELETE FROM quests WHERE playerID IN (79, 80, 81);
DELETE FROM maxstats WHERE playerID IN (79, 80, 81);
DELETE FROM experience WHERE playerID IN (79, 80, 81);
DELETE FROM player_cache WHERE playerID IN (79, 80, 81) OR (playerID = 0 AND key = 'pt_first_date_trailblazer');
DELETE FROM live_feeds WHERE username IN ('HiddenHero', 'StaffBoss');
DELETE FROM itemstatuses WHERE itemID IN (9011, 9012);
DELETE FROM players WHERE id IN (79, 80, 81);
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
PORTAL_SIGNUP_IP_DAILY_LIMIT=3 \
PORTAL_CHARACTER_IP_DAILY_LIMIT=2 \
PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
PORTAL_LAUNCH_FREE_CARD_HOURS=24 \
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
world_live_db="$tmp_dir/openrsc-world-live-fixture.db"
cp "$fixture_db" "$world_live_db"
PORT="$PORT" PORTAL_ADMIN_TOKEN="dev-admin" PORTAL_SIGNUP_IP_DAILY_LIMIT=3 PORTAL_CHARACTER_IP_DAILY_LIMIT=2 node web/portal/api-smoke.mjs

# The same endpoint must drop every fictional identity at the launch boundary and
# resume the privacy-filtered saved-world ledger. A separate post-launch process
# keeps the main prelaunch signup fixture untouched.
world_live_port=$((PORT + 27))
initialize_public_store "$tmp_dir/world-live-store"
PORT="$world_live_port" \
	PORTAL_DATA_DIR="$tmp_dir/world-live-store" \
	PORTAL_OPENRSC_DB="$world_live_db" \
	PORTAL_LAUNCH_AT="2000-01-01T00:00:00Z" \
	node web/portal/dev-server.mjs >"$tmp_dir/world-live.log" 2>&1 &
world_live_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${world_live_port}/api/health" >/dev/null 2>&1; then break; fi
	if ! kill -0 "$world_live_pid" >/dev/null 2>&1; then
		cat "$tmp_dir/world-live.log" >&2
		echo "post-launch world fixture exited before becoming healthy" >&2
		exit 1
	fi
	sleep 0.1
done
world_live_payload="$(curl -fsS "http://127.0.0.1:${world_live_port}/api/world")"
node -e '
const world = JSON.parse(process.argv[1]);
if (world.live !== true || world.demo === true || world.source !== "openrsc-sqlite") throw new Error("post-launch world must contain only saved-world data");
if (JSON.stringify(world).includes("Astravale")) throw new Error("post-launch world must clear fictional prelaunch identities");
if (world.pulse.accounts !== 3 || world.pulse.totalGp !== 14000) throw new Error("live aggregates must preserve eligibility and hidden-player totals");
if (world.highscores.hiddenCount !== 1 || world.highscores.overall.length !== 1 || world.highscores.overall[0].player !== "SmokeHero") throw new Error("live boards must enforce privacy and eligibility");
if (world.highscores.skills.attack[0].xp !== 1000) throw new Error("live boards must convert fixed-point experience");
if (world.highscores.overall[0].honorific !== "Saint" || world.highscores.overall[0].epithet !== "the Founder") throw new Error("live boards must preserve title placement");
if (world.feed.length !== 1 || world.feed[0].player !== "SmokeHero" || JSON.stringify(world.feed).includes("HiddenHero")) throw new Error("live feed must suppress hidden identities");
const anonymousFirst = world.records.find((record) => record.key === "first_max");
const visibleFirst = world.records.find((record) => record.key === "first_total");
if (!anonymousFirst || !anonymousFirst.anonymous || anonymousFirst.holder !== "An unseen adventurer") throw new Error("live records must anonymize hidden holders");
if (!visibleFirst || visibleFirst.holder !== "SmokeHero" || visibleFirst.honorific !== "Saint") throw new Error("live records must decorate visible holders");
if (Object.keys(world.highscores.skills).length !== 18 || !Array.isArray(world.facts) || !world.facts.length) throw new Error("live world contract is incomplete");
' "$world_live_payload"
kill "$world_live_pid" >/dev/null 2>&1 || true
wait "$world_live_pid" >/dev/null 2>&1 || true
world_live_pid=""

initialize_public_store "$tmp_dir/world-prelaunch-no-db-store"
PORT="$world_live_port" \
	PORTAL_DATA_DIR="$tmp_dir/world-prelaunch-no-db-store" \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	node web/portal/dev-server.mjs >"$tmp_dir/world-prelaunch-no-db.log" 2>&1 &
world_live_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${world_live_port}/api/health" >/dev/null 2>&1; then break; fi
	if ! kill -0 "$world_live_pid" >/dev/null 2>&1; then
		cat "$tmp_dir/world-prelaunch-no-db.log" >&2
		echo "prelaunch no-database world fixture exited before becoming healthy" >&2
		exit 1
	fi
	sleep 0.1
done
world_no_db_payload="$(curl -fsS "http://127.0.0.1:${world_live_port}/api/world")"
node -e '
const world = JSON.parse(process.argv[1]);
if (!world.demo || world.source !== "prelaunch-fiction" || world.highscores.overall.length !== 10) {
	throw new Error("prelaunch Chronicle should remain fully populated without a game database");
}
' "$world_no_db_payload"
kill "$world_live_pid" >/dev/null 2>&1 || true
wait "$world_live_pid" >/dev/null 2>&1 || true
world_live_pid=""

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

node - "$tmp_dir/dev-store.json" "$fixture_db" <<'NODE'
const { execFileSync } = require("child_process");
const fs = require("fs");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const database = process.argv[3];
const normalize = (code) => String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 20);
const scalar = (sql) => Number(execFileSync("sqlite3", [database, sql], { encoding: "utf8" }).trim() || 0);
const values = (sql) => {
	const output = execFileSync("sqlite3", [database, sql], { encoding: "utf8" }).trim();
	return output ? output.split(/\r?\n/) : [];
};
for (const founder of store.founders || []) {
	const baseCode = normalize(founder.signupCodeNormalized || founder.signupCode);
	if (baseCode) {
		const key = `base_tag:${baseCode}`;
		const accountKey = `base_acct:${baseCode}`;
		if (key.length > 32) throw new Error("founder base tag exceeds player_cache key limit");
		const tagValues = values(`SELECT value FROM player_cache WHERE playerID=0 AND key='${key}' ORDER BY dbid;`);
		if (tagValues.length !== 1 || !/^(0|[1-9]\d*)$/.test(tagValues[0]) || Number(tagValues[0]) > 2147483647) {
			throw new Error(`founder base code ${baseCode} must have exactly one classifier tag`);
		}
		const accountValues = values(`SELECT value FROM player_cache WHERE playerID=0 AND key='${accountKey}' ORDER BY dbid;`);
		if (accountValues.length !== 1 || !/^(0|[1-9]\d*)$/.test(accountValues[0]) || Number(accountValues[0]) > 2147483647) {
			throw new Error(`founder base code ${baseCode} must have exactly one account classifier`);
		}
	}
	for (const reward of founder.referralRewardCodes || []) {
		const referralCode = normalize(reward && reward.code);
		if (referralCode && scalar(`SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key IN ('base_tag:${referralCode}','base_acct:${referralCode}');`) !== 0) {
			throw new Error(`referral reward ${referralCode} must remain independent of founder base tags`);
		}
	}
}
NODE

starter_card_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter:1:%' AND value='1';")"
if [[ "$starter_card_count" != "0" ]]; then
	echo "portal admin must not create founder-card markers outside the cutoff finalizer, got ${starter_card_count:-0}"
	exit 1
fi
legacy_starter_card_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")"
if [[ "$legacy_starter_card_count" != "0" ]]; then
	echo "portal runtime must not create legacy account-wide starter-card markers"
	exit 1
fi
unbound_starter_card_count="$(sqlite3 "$fixture_db" <<'SQL'
SELECT COUNT(*)
FROM player_cache reward
WHERE reward.playerID = 0
  AND reward.key LIKE 'starter:1:%'
  AND NOT EXISTS (
    SELECT 1
    FROM players p
    JOIN player_cache link
      ON link.playerID = p.id
     AND link.key = 'web_account_id'
     AND link.value = '1'
    WHERE reward.key = 'starter:1:' || p.id
  );
SQL
)"
if [[ "$unbound_starter_card_count" != "0" ]]; then
	echo "every reconciled founder-card marker must bind to a linked player ID"
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

# ---- CAPTCHA-gated public signup ----
captcha_port=$((PORT + 4))
initialize_public_store "$tmp_dir/captcha-store"
PORT="$captcha_port" \
	PORTAL_DATA_DIR="$tmp_dir/captcha-store" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_CAPTCHA_REQUIRED=1 \
	PORTAL_CAPTCHA_BYPASS_TOKEN="captcha-smoke-token" \
	PORTAL_CAPTCHA_SITE_KEY="captcha-smoke-site-key" \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-captcha-smoke.log 2>&1 &
captcha_pid="$!"

for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${captcha_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done

captcha_health_payload="$(curl -fsS "http://127.0.0.1:${captcha_port}/api/health")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.config || payload.config.publicReady !== true) throw new Error('captcha health should report public config ready');
if (!payload.config.captcha || payload.config.captcha.configured !== true) throw new Error('captcha health should report configured captcha');
if (payload.config.captcha.signupRequired !== true) throw new Error('captcha health should report signup captcha required');
if (payload.config.captcha.bypassConfigured !== true) throw new Error('captcha health should report configured bypass for smoke tests');
if ((payload.config.issues || []).length) throw new Error('captcha health should not report config issues: ' + JSON.stringify(payload.config.issues));
" "$captcha_health_payload"

captcha_public_payload="$(curl -fsS "http://127.0.0.1:${captcha_port}/api/public")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.captcha || payload.captcha.signupRequired !== true) throw new Error('/api/public should expose signup captcha requirement');
if (payload.captcha.configured !== true) throw new Error('/api/public should expose configured captcha state');
if (payload.captcha.siteKey !== 'captcha-smoke-site-key') throw new Error('/api/public should expose the public captcha site key');
" "$captcha_public_payload"

captcha_missing="$(curl -s -w '\n%{http_code}' -X POST "http://127.0.0.1:${captcha_port}/api/founder/reservations" -H 'content-type: application/json' -d '{"username":"CaptchaNo","email":"captcha-no@example.com"}')"
captcha_missing_status="$(tail -n1 <<<"$captcha_missing")"
if [[ "$captcha_missing_status" != "403" ]] || ! grep -q '"error": "captcha_required"' <<<"$captcha_missing"; then
	echo "captcha-gated signup should reject missing captcha tokens"
	exit 1
fi

captcha_bad="$(curl -s -w '\n%{http_code}' -X POST "http://127.0.0.1:${captcha_port}/api/founder/reservations" -H 'content-type: application/json' -d '{"username":"CaptchaBad","email":"captcha-bad@example.com","captchaToken":"bad-token"}')"
captcha_bad_status="$(tail -n1 <<<"$captcha_bad")"
if [[ "$captcha_bad_status" != "403" ]] || ! grep -q '"error": "captcha_failed"' <<<"$captcha_bad"; then
	echo "captcha-gated signup should reject bad captcha tokens"
	exit 1
fi

captcha_ok="$(curl -fsS -X POST "http://127.0.0.1:${captcha_port}/api/founder/reservations" -H 'content-type: application/json' -d '{"username":"CaptchaOk","email":"captcha-ok@example.com","captchaToken":"captcha-smoke-token"}')"
grep -q '"CaptchaOk"' <<<"$captcha_ok" || { echo "captcha-gated signup should accept the bypass token in smoke tests"; exit 1; }

kill "$captcha_pid" >/dev/null 2>&1 || true
wait "$captcha_pid" >/dev/null 2>&1 || true
captcha_pid=""

# Founder reservations freeze when launch opens, while every portal-created
# character still receives the separate launch card through the next 24 hours.
launch_window_port=$((PORT + 8))
launch_window_db="$tmp_dir/openrsc-launch-window-fixture.db"
launch_window_at="$(node -e 'process.stdout.write(new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString())')"
cp "$base_fixture_db" "$launch_window_db"
initialize_public_store "$tmp_dir/launch-window-store"
PORT="$launch_window_port" \
	PORTAL_DATA_DIR="$tmp_dir/launch-window-store" \
	PORTAL_OPENRSC_DB="$launch_window_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="$launch_window_at" \
	PORTAL_LAUNCH_FREE_CARD_HOURS=24 \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >"$tmp_dir/launch-window.log" 2>&1 &
launch_window_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${launch_window_port}/api/health" >/dev/null 2>&1; then break; fi
	sleep 0.1
done
launch_window_public="$(curl -fsS "http://127.0.0.1:${launch_window_port}/api/public")"
node -e '
const payload = JSON.parse(process.argv[1]);
if (!payload.launch || payload.launch.locked !== false) throw new Error("launch-window fixture should be post-launch");
if (!payload.launch.starterCard || payload.launch.starterCard.open !== true) throw new Error("24-hour launch-card window should remain open");
' "$launch_window_public"
launch_window_founder="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${launch_window_port}/api/founder/reservations" \
	-H 'content-type: application/json' \
	-d '{"username":"LateFounder","email":"late-founder@example.com"}')"
if [[ "$(tail -n1 <<<"$launch_window_founder")" != "410" ]] || ! grep -q '"error": "founder_program_closed"' <<<"$launch_window_founder"; then
	echo "founder reservations must freeze when launch opens"
	exit 1
fi
launch_window_signup="$(curl -fsS -X POST "http://127.0.0.1:${launch_window_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"WindowOne","email":"window-one@example.com","password":"Windowpass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
launch_window_token="$(node -e '
const payload = JSON.parse(process.argv[1]);
if (!payload.token || !payload.founder || payload.founder.starterCardUnlocked !== false) throw new Error("post-launch account should not enter the frozen founder cohort");
if (!payload.rewards || payload.rewards.starterSubscriptionCards !== 1) throw new Error("first launch-window character should receive one card");
if (!payload.rewards.cards.some((card) => card.source === "launch_24h_card")) throw new Error("launch-window reward must use the launch marker");
process.stdout.write(payload.token);
' "$launch_window_signup")"
launch_window_second="$(curl -fsS -X POST "http://127.0.0.1:${launch_window_port}/api/characters" \
	-H "authorization: Bearer ${launch_window_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"WindowTwo","gamePassword":"Wind2"}')"
node -e '
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 2) throw new Error("launch-window account should create a second character");
if (!payload.rewards || payload.rewards.starterSubscriptionCards !== 2) throw new Error("each launch-window character should receive its own card");
if (!payload.rewards.cards.every((card) => card.source === "launch_24h_card")) throw new Error("launch-window cards must remain separate per-character launch markers");
' "$launch_window_second"
assert_launch_window_markers="$(sqlite3 "$launch_window_db" "SELECT COUNT(*) FROM player_cache WHERE playerID IN (SELECT id FROM players WHERE username IN ('WindowOne','WindowTwo')) AND key='launch_24h_card' AND value='1';")"
if [[ "$assert_launch_window_markers" != "2" ]]; then
	echo "every portal character created during the 24-hour launch window needs a launch card marker"
	exit 1
fi
kill "$launch_window_pid" >/dev/null 2>&1 || true
wait "$launch_window_pid" >/dev/null 2>&1 || true
launch_window_pid=""

# After the 24-hour launch promotion ends, normal account/character creation
# remains open, but founder reservations and starter-card qualification close.
post_cutoff_port=$((PORT + 7))
post_cutoff_db="$tmp_dir/openrsc-post-cutoff-fixture.db"
cp "$base_fixture_db" "$post_cutoff_db"
initialize_public_store "$tmp_dir/post-cutoff-store"
PORT="$post_cutoff_port" \
	PORTAL_DATA_DIR="$tmp_dir/post-cutoff-store" \
	PORTAL_OPENRSC_DB="$post_cutoff_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2000-01-01T00:00:00Z" \
	PORTAL_LAUNCH_FREE_CARD_HOURS=24 \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >"$tmp_dir/post-cutoff.log" 2>&1 &
post_cutoff_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${post_cutoff_port}/api/health" >/dev/null 2>&1; then break; fi
	sleep 0.1
done
post_cutoff_public="$(curl -fsS "http://127.0.0.1:${post_cutoff_port}/api/public")"
node -e '
const payload = JSON.parse(process.argv[1]);
if (!payload.launch || !payload.launch.starterCard) throw new Error("post-cutoff launch state is missing starter-card metadata");
if (payload.launch.starterCard.open !== false || payload.launch.starterCard.remainingMs !== 0) {
	throw new Error("starter-card qualification must close after the configured 24-hour launch window");
}
' "$post_cutoff_public"
post_cutoff_founder="$(curl -sS -w '\n%{http_code}' -X POST "http://127.0.0.1:${post_cutoff_port}/api/founder/reservations" \
	-H 'content-type: application/json' \
	-d '{"username":"LateFounder","email":"late-founder@example.com"}')"
if [[ "$(tail -n1 <<<"$post_cutoff_founder")" != "410" ]] || ! grep -q '"error": "founder_program_closed"' <<<"$post_cutoff_founder"; then
	echo "public founder reservations must close at the launch cutoff"
	exit 1
fi
post_cutoff_signup="$(curl -fsS -X POST "http://127.0.0.1:${post_cutoff_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"LatePlayer","email":"late-player@example.com","password":"Latepass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
node -e '
const payload = JSON.parse(process.argv[1]);
if (!payload.token || !Array.isArray(payload.characters) || payload.characters.length !== 1) {
	throw new Error("post-cutoff registration should still create an account and first character");
}
if (!payload.founder || payload.founder.starterCardUnlocked !== false) {
	throw new Error("post-cutoff founder state must remain unqualified");
}
if (!payload.rewards || payload.rewards.starterSubscriptionCards !== 0) {
	throw new Error("post-cutoff registration must not grant a starter subscription card");
}
' "$post_cutoff_signup"
node - "$tmp_dir/post-cutoff-store/dev-store.json" <<'NODE'
const fs = require("fs");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const account = (store.accounts || []).find((row) => row.emailCanonical === "late-player@example.com");
if (!account) throw new Error("post-cutoff account is missing");
if ((store.entitlements || []).some((row) => row.accountId === account.id && row.type === "starter_free_subscription")) {
	throw new Error("post-cutoff account persisted an ineligible founder entitlement");
}
if (!account.starterCardReview || account.starterCardReview.status !== "closed") {
	throw new Error("post-cutoff account should record the closed qualification window");
}
NODE
kill "$post_cutoff_pid" >/dev/null 2>&1 || true
wait "$post_cutoff_pid" >/dev/null 2>&1 || true
post_cutoff_pid=""

# ---- PORTAL_PUBLIC_MODE lockdown ----
public_port=$((PORT + 1))
initialize_public_store "$tmp_dir/public-store"
private_build_commit="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
cat >"$tmp_dir/public-build-meta.json" <<JSON
{
	"sourcePublicationStatus": "publication_pending",
	"sourceRepositoryUrl": "",
	"sourceCommit": "",
	"privateBuildCommit": "$private_build_commit",
	"privateBuildShortCommit": "aaaaaaaaaaaa",
	"sourceDirty": true,
	"generatedAt": "2026-07-15T00:00:00.000Z"
}
JSON
	PORT="$public_port" \
	PORTAL_DATA_DIR="$tmp_dir/public-store" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_BUILD_META="$tmp_dir/public-build-meta.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_AT="2026-07-18T18:00:00Z" \
	PORTAL_ANDROID_APK="$public_android_apk" \
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

for public_static_path in \
	/dev-server.mjs /%64ev-server.mjs /api-smoke.mjs /README.md \
	/schema/sqlite/001_web_accounts.sql /build-meta.json \
	/public-static-contract.json /.env /.git/config \
	/assets/generated/buttons-tabs.png /assets/private.json
do
	expect_status 404 --path-as-is -I "http://127.0.0.1:${public_port}${public_static_path}"
done
expect_status 200 -I "http://127.0.0.1:${public_port}/styles.css"

expect_invalid_game_password() {
	local response_file="$tmp_dir/invalid-game-password.json"
	local actual
	actual="$(curl -sS -o "$response_file" -w '%{http_code}' "$@")"
	if [[ "$actual" != "400" ]]; then
		echo "password contract: expected HTTP 400 from $*, got $actual"
		exit 1
	fi
	node -e '
const payload = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
if (payload.error !== "invalid_game_password") {
	throw new Error("expected invalid_game_password, got " + JSON.stringify(payload));
}
' "$response_file"
}

expect_terms_acceptance_required() {
	local response_file="$tmp_dir/terms-acceptance-required.json"
	local actual
	actual="$(curl -sS -o "$response_file" -w '%{http_code}' "$@")"
	if [[ "$actual" != "400" ]]; then
		echo "terms contract: expected HTTP 400 from $*, got $actual"
		exit 1
	fi
	node -e '
const payload = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
if (payload.error !== "terms_acceptance_required") {
	throw new Error("expected terms_acceptance_required, got " + JSON.stringify(payload));
}
' "$response_file"
}

# A restart must drain a roster transaction that has written OpenRSC rows but
# has not yet renamed the matching portal store. The test-only pause makes that
# normally tiny cross-store boundary deterministic.
drain_db="$tmp_dir/openrsc-drain-fixture.db"
drain_store="$tmp_dir/drain-store"
drain_marker="$tmp_dir/drain-write-ready"
drain_release="$tmp_dir/drain-write-release"
drain_response="$tmp_dir/drain-signup-response.json"
drain_status_file="$tmp_dir/drain-signup-status"
drain_log="$tmp_dir/drain-server.log"
drain_port=$((PORT + 12))
cp "$base_fixture_db" "$drain_db"
initialize_public_store "$drain_store"
PORT="$drain_port" \
	PORTAL_DATA_DIR="$drain_store" \
	PORTAL_OPENRSC_DB="$drain_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	PORTAL_ENABLE_TEST_FAULTS=1 \
	PORTAL_TEST_STORE_PAUSE_MARKER="$drain_marker" \
	PORTAL_TEST_STORE_PAUSE_RELEASE="$drain_release" \
	PORTAL_TEST_STORE_PAUSE_TIMEOUT_MS=10000 \
	PORTAL_SHUTDOWN_TIMEOUT_MS=5000 \
	node web/portal/dev-server.mjs >"$drain_log" 2>&1 &
drain_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${drain_port}/api/health" >/dev/null 2>&1; then break; fi
	sleep 0.1
done

curl -sS -o "$drain_response" -w '%{http_code}' \
	-X POST "http://127.0.0.1:${drain_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"DrainGuy","email":"drain-guy@example.com","password":"Drainpass1","termsAccepted":true,"termsVersion":"2026-07-16"}' \
	>"$drain_status_file" &
drain_request_pid="$!"
for _ in {1..200}; do
	[[ -f "$drain_marker" ]] && break
	if ! kill -0 "$drain_pid" >/dev/null 2>&1; then
		echo "drain fixture server exited before reaching the cross-store pause"
		exit 1
	fi
	sleep 0.02
done
[[ -f "$drain_marker" ]] || { echo "in-flight roster write did not reach the deterministic pause"; exit 1; }

kill -TERM "$drain_pid"
sleep 0.15
if ! kill -0 "$drain_pid" >/dev/null 2>&1; then
	echo "SIGTERM exited before the in-flight roster write drained"
	exit 1
fi
drain_listener_closed=0
for _ in {1..50}; do
	if ! curl -fsS --max-time 0.2 "http://127.0.0.1:${drain_port}/api/health" >/dev/null 2>&1; then
		drain_listener_closed=1
		break
	fi
	sleep 0.02
done
[[ "$drain_listener_closed" == "1" ]] || { echo "shutdown should stop accepting new requests while draining"; exit 1; }

: >"$drain_release"
if ! wait "$drain_request_pid"; then
	echo "in-flight roster request failed while the service was draining"
	exit 1
fi
drain_request_pid=""
if ! wait "$drain_pid"; then
	echo "portal should exit cleanly after draining the in-flight roster request"
	exit 1
fi
drain_pid=""
[[ "$(cat "$drain_status_file")" == "201" ]] || { echo "drained roster request should complete with HTTP 201"; exit 1; }
grep -q 'portal_shutdown_complete' "$drain_log" || { echo "graceful shutdown should log a completed drain"; exit 1; }

node - "$drain_store/dev-store.json" "$drain_db" <<'NODE'
const fs = require("fs");
const { execFileSync } = require("child_process");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const account = store.accounts.find((row) => row.emailCanonical === "drain-guy@example.com");
if (!account) throw new Error("drained signup is missing from the portal store");
const character = store.characters.find((row) => row.accountId === account.id && row.name === "DrainGuy");
if (!character || !Number(character.playerId)) throw new Error("drained signup is missing its portal character link");
const rows = execFileSync("sqlite3", ["-separator", "|", process.argv[3], "SELECT p.id,p.username,pc.value FROM players p JOIN player_cache pc ON pc.playerID=p.id AND pc.key='web_account_id' WHERE p.username='DrainGuy';"], { encoding: "utf8" }).trim().split("|");
if (Number(rows[0]) !== Number(character.playerId) || rows[1] !== "DrainGuy" || Number(rows[2]) !== Number(account.id)) {
	throw new Error("drained roster write does not match across portal and OpenRSC stores");
}
const orphanCount = Number(execFileSync("sqlite3", [process.argv[3], "SELECT COUNT(*) FROM player_cache pc LEFT JOIN players p ON p.id=pc.playerID WHERE pc.key='web_account_id' AND p.id IS NULL;"], { encoding: "utf8" }).trim());
if (orphanCount !== 0) throw new Error("drained roster write left an orphaned OpenRSC account link");
NODE

# A clean replacement can read and authenticate the drained account.
PORT="$drain_port" \
	PORTAL_DATA_DIR="$drain_store" \
	PORTAL_OPENRSC_DB="$drain_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >>"$drain_log" 2>&1 &
drain_restart_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${drain_port}/api/health" >/dev/null 2>&1; then break; fi
	sleep 0.1
done
drain_login="$(curl -fsS -X POST "http://127.0.0.1:${drain_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"drain-guy@example.com","password":"Drainpass1"}')"
node -e "const payload=JSON.parse(process.argv[1]); if(!payload.token || !(payload.characters||[]).some((row)=>row.name==='DrainGuy')) throw new Error('replacement portal could not read the drained account');" "$drain_login"
kill -TERM "$drain_restart_pid"
wait "$drain_restart_pid"
drain_restart_pid=""

# A hard crash can land on either side of the two durable portal writes used by
# launch provisioning. Each case starts from the same empty public store and
# retries the exact same direct-password signup after a clean restart.
run_vs096_signup_crash_case() {
	local slug="$1"
	local offset="$2"
	local pause_phase="$3"
	local pause_at="$4"
	local username="$5"
	local next_username="$6"
	local expected_game_rows="$7"
	local store_dir="$tmp_dir/vs096-${slug}-store"
	local db="$tmp_dir/vs096-${slug}.db"
	local marker="$tmp_dir/vs096-${slug}-pause"
	local release="$tmp_dir/vs096-${slug}-release"
	local response="$tmp_dir/vs096-${slug}-response.json"
	local status_file="$tmp_dir/vs096-${slug}-status"
	local curl_log="$tmp_dir/vs096-${slug}-curl.log"
	local log="$tmp_dir/vs096-${slug}.log"
	local port=$((PORT + offset))
	local email="vs096-${slug}@example.com"
	local next_email="vs096-${slug}-next@example.com"
	local password="Crashpass1"
	local next_password="Nextpass1"
	local expected_crash_source="openrsc-sqlite-provisioning"
	local anchor_ids
	local anchor_account_id
	local anchor_character_id
	local retry_status
	local next_status

	if [[ "$pause_phase" == "before" && "$pause_at" == "1" ]]; then
		expected_crash_source="none"
	elif [[ "$pause_phase" == "after" && "$pause_at" == "2" ]]; then
		expected_crash_source="openrsc-sqlite-created"
	fi
	cp "$base_fixture_db" "$db"
	initialize_public_store "$store_dir"
	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	PORTAL_ENABLE_TEST_FAULTS=1 \
	PORTAL_TEST_STORE_PAUSE_MARKER="$marker" \
	PORTAL_TEST_STORE_PAUSE_RELEASE="$release" \
	PORTAL_TEST_STORE_PAUSE_PHASE="$pause_phase" \
	PORTAL_TEST_STORE_PAUSE_AT="$pause_at" \
	PORTAL_TEST_STORE_PAUSE_TIMEOUT_MS=10000 \
	node web/portal/dev-server.mjs >"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 ${slug} fixture exited during startup"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 ${slug} fixture did not become healthy"
		cat "$log"
		exit 1
	fi

	curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/register" \
		-H 'content-type: application/json' \
		-d "{\"username\":\"${username}\",\"email\":\"${email}\",\"password\":\"${password}\",\"termsAccepted\":true,\"termsVersion\":\"2026-07-16\"}" \
		>"$status_file" 2>"$curl_log" &
	vs096_request_pid="$!"
	for _ in {1..300}; do
		[[ -f "$marker" ]] && break
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 ${slug} fixture exited before its crash boundary"
			cat "$log"
			exit 1
		fi
		sleep 0.02
	done
	if [[ ! -f "$marker" ]]; then
		echo "VS-096 ${slug} fixture did not reach ${pause_phase} store write ${pause_at}"
		cat "$log"
		exit 1
	fi
	kill -KILL "$vs096_pid"
	wait "$vs096_pid" >/dev/null 2>&1 || true
	vs096_pid=""
	wait "$vs096_request_pid" >/dev/null 2>&1 || true
	vs096_request_pid=""

	anchor_ids="$(node - "$store_dir/dev-store.json" "$email" "$username" "$password" "$expected_crash_source" <<'NODE'
const fs = require("fs");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const email = process.argv[3];
const username = process.argv[4];
const password = process.argv[5];
const expectedSource = process.argv[6];
const accounts = (store.accounts || []).filter((row) => row.emailCanonical === email);
if (expectedSource === "none") {
	if (accounts.length !== 0 || (store.characters || []).some((row) => row.name === username)
		|| Number(store.nextIds.account) !== 1 || Number(store.nextIds.character) !== 1) {
		throw new Error("pre-intent crash changed the empty portal store");
	}
	if (JSON.stringify(store).includes(password)) throw new Error("pre-intent store retained the plaintext game password");
	process.stdout.write("1 1");
	process.exit(0);
}
if (accounts.length !== 1 || accounts[0].status !== "provisioning") {
	throw new Error("crash must retain exactly one provisioning account: " + JSON.stringify(accounts));
}
const characters = (store.characters || []).filter((row) => Number(row.accountId) === Number(accounts[0].id));
if (characters.length !== 1 || characters[0].name !== username || !characters[0].initialSignup) {
	throw new Error("crash must retain exactly one initial-character ownership anchor: " + JSON.stringify(characters));
}
if (characters[0].source !== expectedSource
	|| characters[0].linkStatus !== (expectedSource === "openrsc-sqlite-created" ? "linked" : "provisioning")) {
	throw new Error("crash retained the wrong provisioning phase: " + JSON.stringify(characters[0]));
}
if (JSON.stringify(store).includes(password)) throw new Error("portal store retained the plaintext game password");
if (Number(store.nextIds.account) <= Number(accounts[0].id)
	|| Number(store.nextIds.character) <= Number(characters[0].id)) {
	throw new Error("durable ownership IDs were not advanced past their reserved values");
}
process.stdout.write(`${accounts[0].id} ${characters[0].id}`);
NODE
)"
	read -r anchor_account_id anchor_character_id <<<"$anchor_ids"
	if [[ "$(sqlite3 "$db" "SELECT COUNT(*) FROM players WHERE username='${username}';")" != "$expected_game_rows" ]]; then
		echo "VS-096 ${slug} crash left an unexpected number of game players"
		exit 1
	fi

	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >>"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 ${slug} replacement refused a valid ownership anchor"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 ${slug} replacement did not become healthy"
		cat "$log"
		exit 1
	fi

	node - "$store_dir/dev-store.json" "$email" "$username" "$expected_game_rows" "$expected_crash_source" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
const account = (store.accounts || []).find((row) => row.emailCanonical === process.argv[3]);
const character = account && (store.characters || []).find((row) => Number(row.accountId) === Number(account.id) && row.name === process.argv[4]);
if (process.argv[6] === "none") {
	if (account || character || Number(store.nextIds.account) !== 1 || Number(store.nextIds.character) !== 1) {
		throw new Error("restart changed a pre-intent empty store");
	}
	process.exit(0);
}
if (!account || !character) throw new Error("restart lost the durable ownership anchor");
const gameWasCommitted = process.argv[5] === "1";
if (gameWasCommitted && (character.source !== "openrsc-sqlite-created" || character.linkStatus !== "linked" || !Number(character.playerId))) {
	throw new Error("startup did not reconcile the committed game player: " + JSON.stringify(character));
}
if (!gameWasCommitted && (character.source !== "openrsc-sqlite-provisioning" || character.linkStatus !== "provisioning" || character.playerId !== null)) {
	throw new Error("startup changed a retryable intent with no game player: " + JSON.stringify(character));
}
NODE

	retry_status="$(curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/register" \
		-H 'content-type: application/json' \
		-d "{\"username\":\"${username}\",\"email\":\"${email}\",\"password\":\"${password}\",\"termsAccepted\":true,\"termsVersion\":\"2026-07-16\"}")"
	if [[ "$retry_status" != "201" ]]; then
		echo "VS-096 ${slug} retry should complete with HTTP 201, got $retry_status"
		cat "$response"
		cat "$log"
		exit 1
	fi
	next_status="$(curl -sS -o "$tmp_dir/vs096-${slug}-next.json" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/register" \
		-H 'content-type: application/json' \
		-d "{\"username\":\"${next_username}\",\"email\":\"${next_email}\",\"password\":\"${next_password}\",\"termsAccepted\":true,\"termsVersion\":\"2026-07-16\"}")"
	if [[ "$next_status" != "201" ]]; then
		echo "VS-096 ${slug} follow-up signup should complete with HTTP 201, got $next_status"
		cat "$tmp_dir/vs096-${slug}-next.json"
		exit 1
	fi

	node - "$store_dir/dev-store.json" "$db" "$email" "$username" "$anchor_account_id" "$anchor_character_id" "$next_email" "$next_username" "$password" "$next_password" <<'NODE'
const fs = require("fs");
const { execFileSync } = require("child_process");
const storePath = process.argv[2];
const db = process.argv[3];
const email = process.argv[4];
const username = process.argv[5];
const anchorAccountId = Number(process.argv[6]);
const anchorCharacterId = Number(process.argv[7]);
const nextEmail = process.argv[8];
const nextUsername = process.argv[9];
const passwords = process.argv.slice(10);
const storeText = fs.readFileSync(storePath, "utf8");
const store = JSON.parse(storeText);
for (const password of passwords) {
	if (storeText.includes(password)) throw new Error("portal store retained a plaintext game password");
}
const accounts = (store.accounts || []).filter((row) => row.emailCanonical === email);
if (accounts.length !== 1 || Number(accounts[0].id) !== anchorAccountId || accounts[0].status !== "active") {
	throw new Error("retry changed or duplicated the anchored account: " + JSON.stringify(accounts));
}
const characters = (store.characters || []).filter((row) => Number(row.accountId) === anchorAccountId && row.name === username);
if (characters.length !== 1 || Number(characters[0].id) !== anchorCharacterId
	|| characters[0].source !== "openrsc-sqlite-created" || characters[0].linkStatus !== "linked"
	|| !Number(characters[0].playerId)) {
	throw new Error("retry changed or duplicated the anchored character: " + JSON.stringify(characters));
}
const nextAccount = (store.accounts || []).find((row) => row.emailCanonical === nextEmail);
const nextCharacter = nextAccount && (store.characters || []).find((row) => Number(row.accountId) === Number(nextAccount.id) && row.name === nextUsername);
if (!nextAccount || !nextCharacter || Number(nextAccount.id) <= anchorAccountId || Number(nextCharacter.id) <= anchorCharacterId) {
	throw new Error("a later signup reused a durably reserved account or character ID");
}
for (const [rows, nextKey] of [[store.accounts || [], "account"], [store.characters || [], "character"]]) {
	const ids = rows.map((row) => Number(row.id));
	if (new Set(ids).size !== ids.length || Number(store.nextIds[nextKey]) <= Math.max(...ids)) {
		throw new Error(`${nextKey} IDs are duplicated or their sequence was not advanced`);
	}
}
const sql = (statement) => execFileSync("sqlite3", [db, statement], { encoding: "utf8" }).trim();
const escapedUsername = username.replaceAll("'", "''");
const playerRows = sql(`SELECT id FROM players WHERE username='${escapedUsername}'`).split(/\s+/).filter(Boolean);
if (playerRows.length !== 1 || Number(playerRows[0]) !== Number(characters[0].playerId)) {
	throw new Error("retry did not preserve exactly one matching game player");
}
const playerId = Number(playerRows[0]);
for (const table of ["curstats", "maxstats", "experience", "capped_experience"]) {
	if (sql(`SELECT COUNT(*) FROM ${table} WHERE playerID=${playerId}`) !== "1") {
		throw new Error(`retry did not preserve exactly one ${table} row`);
	}
}
const markers = sql(`SELECT value FROM player_cache WHERE playerID=${playerId} AND key='web_account_id'`).split(/\s+/).filter(Boolean);
if (markers.length !== 1 || Number(markers[0]) !== anchorAccountId) {
	throw new Error("retry did not preserve exactly one matching ownership marker");
}
NODE

	if [[ "$slug" == "final" ]]; then
		sqlite3 "$db" <<SQL
INSERT INTO players (id, username, group_id, email, pass, salt, creation_date)
VALUES (997, 'AlienLink', 10, 'alien-link@example.com', 'fixture-pass', '', strftime('%s', 'now'));
INSERT INTO player_cache (playerID, type, key, value)
VALUES (997, 0, 'web_account_id', '${anchor_account_id}');
SQL
		local backfill_dry_run
		local store_hash_before
		local store_hash_after
		local backfill_apply_status
		backfill_dry_run="$(curl -fsS -X POST "http://127.0.0.1:${port}/api/admin/accounts/backfill-native" \
			-H "x-portal-admin-token: ${public_admin_token}" \
			-H 'content-type: application/json' \
			-d '{"dryRun":true}')"
		node -e '
const payload = JSON.parse(process.argv[1]);
const conflict = (payload.conflicts || []).find((row) => Number(row.playerId) === 997);
if (!conflict || conflict.reason !== "web_account_link_unexplained") {
	throw new Error("dry-run must report the unexplained pre-link conflict: " + JSON.stringify(payload.conflicts));
}
' "$backfill_dry_run"
		store_hash_before="$(shasum -a 256 "$store_dir/dev-store.json" | awk '{print $1}')"
		backfill_apply_status="$(curl -sS -o "$tmp_dir/vs096-backfill-apply.json" -w '%{http_code}' \
			-X POST "http://127.0.0.1:${port}/api/admin/accounts/backfill-native" \
			-H "x-portal-admin-token: ${public_admin_token}" \
			-H 'content-type: application/json' \
			-d '{"apply":true}')"
		if [[ "$backfill_apply_status" != "409" ]]; then
			echo "native backfill apply must refuse an unexplained pre-link, got HTTP ${backfill_apply_status}"
			cat "$tmp_dir/vs096-backfill-apply.json"
			exit 1
		fi
		store_hash_after="$(shasum -a 256 "$store_dir/dev-store.json" | awk '{print $1}')"
		if [[ "$store_hash_before" != "$store_hash_after" ]]; then
			echo "refused native backfill changed the portal store"
			exit 1
		fi
		node - "$store_dir/dev-store.json" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
if ((store.characters || []).some((row) => row.name === "AlienLink" || Number(row.playerId) === 997)) {
	throw new Error("refused native backfill silently attached the unexplained game player");
}
NODE
	fi

	kill -TERM "$vs096_pid"
	wait "$vs096_pid"
	vs096_pid=""
}

run_vs096_signup_crash_case "preintent" 19 "before" 1 "CrashBefore" "NextBefore" 0
run_vs096_signup_crash_case "intent" 20 "after" 1 "CrashIntent" "NextIntent" 0
run_vs096_signup_crash_case "game" 21 "before" 2 "CrashGame" "NextGame" 1
run_vs096_signup_crash_case "final" 22 "after" 2 "CrashFinal" "NextFinal" 1

# Additional-character retries have a different public contract from initial
# signup recovery: the retry confirms the already-created game player with 409
# after durably clearing its private completion marker.
run_vs096_additional_character_crash_case() {
	local store_dir="$tmp_dir/vs096-additional-store"
	local db="$tmp_dir/vs096-additional.db"
	local marker="$tmp_dir/vs096-additional-pause"
	local release="$tmp_dir/vs096-additional-release"
	local response="$tmp_dir/vs096-additional-response.json"
	local status_file="$tmp_dir/vs096-additional-status"
	local log="$tmp_dir/vs096-additional.log"
	local account_file="$tmp_dir/vs096-additional-account.json"
	local port=$((PORT + 23))
	local email="vs096-additional@example.com"
	local account_name="ExtraBase"
	local account_password="Basepass1"
	local character_name="ExtraCrash"
	local character_password="Extrapass1"
	local signup_status
	local login_payload
	local token
	local retry_status

	cp "$base_fixture_db" "$db"
	initialize_public_store "$store_dir"
	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 additional-character base fixture exited during startup"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 additional-character base fixture did not become healthy"
		cat "$log"
		exit 1
	fi

	signup_status="$(curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/register" \
		-H 'content-type: application/json' \
		-d "{\"username\":\"${account_name}\",\"email\":\"${email}\",\"password\":\"${account_password}\",\"termsAccepted\":true,\"termsVersion\":\"2026-07-16\"}")"
	if [[ "$signup_status" != "201" ]]; then
		echo "VS-096 additional-character base signup should return HTTP 201, got ${signup_status}"
		cat "$response"
		exit 1
	fi
	login_payload="$(curl -fsS -X POST "http://127.0.0.1:${port}/api/accounts/login" \
		-H 'content-type: application/json' \
		-d "{\"email\":\"${email}\",\"password\":\"${account_password}\"}")"
	token="$(node -e 'const p=JSON.parse(process.argv[1]); if(!p.token) throw new Error("base login did not issue a session"); process.stdout.write(p.token);' "$login_payload")"
	kill -TERM "$vs096_pid"
	wait "$vs096_pid"
	vs096_pid=""

	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	PORTAL_ENABLE_TEST_FAULTS=1 \
	PORTAL_TEST_STORE_PAUSE_MARKER="$marker" \
	PORTAL_TEST_STORE_PAUSE_RELEASE="$release" \
	PORTAL_TEST_STORE_PAUSE_PHASE=after \
	PORTAL_TEST_STORE_PAUSE_AT=2 \
	PORTAL_TEST_STORE_PAUSE_TIMEOUT_MS=10000 \
	node web/portal/dev-server.mjs >>"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 additional-character fault fixture exited during startup"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done

	curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/characters" \
		-H "authorization: Bearer ${token}" \
		-H 'content-type: application/json' \
		-d "{\"name\":\"${character_name}\",\"gamePassword\":\"${character_password}\"}" \
		>"$status_file" 2>/dev/null &
	vs096_request_pid="$!"
	for _ in {1..300}; do
		[[ -f "$marker" ]] && break
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 additional-character fixture exited before its linked-save boundary"
			cat "$log"
			exit 1
		fi
		sleep 0.02
	done
	if [[ ! -f "$marker" ]]; then
		echo "VS-096 additional-character fixture did not reach after store write 2"
		cat "$log"
		exit 1
	fi
	kill -KILL "$vs096_pid"
	wait "$vs096_pid" >/dev/null 2>&1 || true
	vs096_pid=""
	wait "$vs096_request_pid" >/dev/null 2>&1 || true
	vs096_request_pid=""

	node - "$store_dir/dev-store.json" "$email" "$character_name" "$account_password" "$character_password" <<'NODE'
const fs = require("fs");
const text = fs.readFileSync(process.argv[2], "utf8");
const store = JSON.parse(text);
const account = (store.accounts || []).find((row) => row.emailCanonical === process.argv[3]);
const matches = account && (store.characters || []).filter((row) =>
	Number(row.accountId) === Number(account.id) && row.name === process.argv[4]);
if (!account || matches.length !== 1 || matches[0].source !== "openrsc-sqlite-created"
	|| matches[0].linkStatus !== "linked" || !Number(matches[0].playerId)
	|| matches[0].provisioningCompletionPending !== true) {
	throw new Error("crash did not retain exactly one linked pending additional character: " + JSON.stringify(matches));
}
if (text.includes(process.argv[5]) || text.includes(process.argv[6])) {
	throw new Error("additional-character crash store retained a plaintext password");
}
NODE

	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >>"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 additional-character replacement refused the valid linked save"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 additional-character replacement did not become healthy"
		cat "$log"
		exit 1
	fi

	retry_status="$(curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/characters" \
		-H "authorization: Bearer ${token}" \
		-H 'content-type: application/json' \
		-d "{\"name\":\"${character_name}\",\"gamePassword\":\"${character_password}\"}")"
	if [[ "$retry_status" != "409" ]]; then
		echo "recovered additional-character retry should return HTTP 409, got ${retry_status}"
		cat "$response"
		exit 1
	fi
	node -e 'const p=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); if(p.error!=="character_already_created") throw new Error("retry returned the wrong conflict: "+JSON.stringify(p));' "$response"
	curl -fsS -H "authorization: Bearer ${token}" "http://127.0.0.1:${port}/api/account" >"$account_file"

	node - "$store_dir/dev-store.json" "$account_file" "$db" "$email" "$character_name" "$account_password" "$character_password" <<'NODE'
const fs = require("fs");
const { execFileSync } = require("child_process");
const storeText = fs.readFileSync(process.argv[2], "utf8");
const store = JSON.parse(storeText);
const publicState = JSON.parse(fs.readFileSync(process.argv[3], "utf8"));
const db = process.argv[4];
const email = process.argv[5];
const characterName = process.argv[6];
if (storeText.includes(process.argv[7]) || storeText.includes(process.argv[8])) {
	throw new Error("recovered additional-character store retained a plaintext password");
}
const account = (store.accounts || []).find((row) => row.emailCanonical === email);
const stored = account && (store.characters || []).filter((row) =>
	Number(row.accountId) === Number(account.id) && row.name === characterName);
if (!account || stored.length !== 1 || stored[0].source !== "openrsc-sqlite-created"
	|| stored[0].linkStatus !== "linked" || !Number(stored[0].playerId)
	|| Object.prototype.hasOwnProperty.call(stored[0], "provisioningCompletionPending")) {
	throw new Error("retry did not durably complete exactly one additional character: " + JSON.stringify(stored));
}
const exposed = (publicState.characters || []).filter((row) => row.name === characterName);
if (exposed.length !== 1 || exposed[0].source !== "openrsc-sqlite-created"
	|| exposed[0].linkStatus !== "linked" || Number(exposed[0].playerId) !== Number(stored[0].playerId)) {
	throw new Error("GET /api/account did not expose exactly one linked additional character: " + JSON.stringify(exposed));
}
const sql = (statement) => execFileSync("sqlite3", [db, statement], { encoding: "utf8" }).trim();
const escapedName = characterName.replaceAll("'", "''");
const playerIds = sql(`SELECT id FROM players WHERE username='${escapedName}'`).split(/\s+/).filter(Boolean);
if (playerIds.length !== 1 || Number(playerIds[0]) !== Number(stored[0].playerId)) {
	throw new Error("recovered additional character does not have exactly one game player");
}
const playerId = Number(playerIds[0]);
for (const table of ["curstats", "maxstats", "experience", "capped_experience"]) {
	if (sql(`SELECT COUNT(*) FROM ${table} WHERE playerID=${playerId}`) !== "1") {
		throw new Error(`recovered additional character does not have exactly one ${table} row`);
	}
}
const markers = sql(`SELECT value FROM player_cache WHERE playerID=${playerId} AND key='web_account_id'`).split(/\s+/).filter(Boolean);
if (markers.length !== 1 || Number(markers[0]) !== Number(account.id)) {
	throw new Error("recovered additional character does not have exactly one ownership marker");
}
NODE

	kill -TERM "$vs096_pid"
	wait "$vs096_pid"
	vs096_pid=""
}

run_vs096_additional_character_crash_case

mint_vs096_google_token() {
	local private_jwk="$1"
	local nonce="$2"
	local client_id="$3"
	local subject="$4"
	local email="$5"
	local display_name="$6"
	node -- - "$private_jwk" "$nonce" "$client_id" "$subject" "$email" "$display_name" <<'NODE'
const fs = require("fs");
const { createPrivateKey, sign } = require("crypto");
const privateJwk = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
const issuedAt = Math.floor(Date.now() / 1000);
const header = encode({ alg: "RS256", typ: "JWT", kid: privateJwk.kid });
const claims = encode({
	iss: "https://accounts.google.com",
	aud: process.argv[4],
	sub: process.argv[5],
	email: process.argv[6],
	email_verified: true,
	name: process.argv[7],
	nonce: process.argv[3],
	iat: issuedAt,
	exp: issuedAt + 600
});
const signed = `${header}.${claims}`;
const signature = sign("RSA-SHA256", Buffer.from(signed), createPrivateKey({ key: privateJwk, format: "jwk" }));
process.stdout.write(`${signed}.${signature.toString("base64url")}`);
NODE
}

# Exercise the real Google launch-signup route with a local RS256 issuer. The
# nonce save is store write 1, the ownership anchor is write 2, and the linked
# character save is write 3; killing before write 3 leaves a committed game row
# whose portal ownership can be reconciled and retried without trusting an ID.
run_vs096_google_signup_crash_case() {
	local store_dir="$tmp_dir/vs096-google-store"
	local db="$tmp_dir/vs096-google.db"
	local marker="$tmp_dir/vs096-google-pause"
	local release="$tmp_dir/vs096-google-release"
	local response="$tmp_dir/vs096-google-response.json"
	local status_file="$tmp_dir/vs096-google-status"
	local request_log="$tmp_dir/vs096-google-curl.log"
	local log="$tmp_dir/vs096-google.log"
	local port=$((PORT + 24))
	local jwks_port=$((PORT + 25))
	local mismatch_port=$((PORT + 26))
	local private_jwk="$tmp_dir/vs096-google-private.jwk"
	local jwks_ready="$tmp_dir/vs096-google-jwks-ready"
	local jwks_log="$tmp_dir/vs096-google-jwks.log"
	local google_client_id="vs096-google-client"
	local google_subject="vs096-google-subject"
	local email="vs096-google@example.com"
	local username="GoogleCrash"
	local original_password="GooglePass1"
	local wrong_password="WrongPass2"
	local nonce_payload
	local nonce
	local credential
	local player_id
	local mismatch_store_dir="$tmp_dir/vs096-google-mismatch-store"
	local mismatch_db="$tmp_dir/vs096-google-mismatch.db"
	local mismatch_log="$tmp_dir/vs096-google-mismatch.log"
	local store_hash_before
	local store_hash_after
	local db_hash_before
	local db_hash_after
	local db_logical_hash_before
	local db_logical_hash_after
	local mismatch_exit
	local wrong_status
	local success_status

	cp "$base_fixture_db" "$db"
	initialize_public_store "$store_dir"
	node -- - "$jwks_port" "$private_jwk" "$jwks_ready" >"$jwks_log" 2>&1 <<'NODE' &
const fs = require("fs");
const http = require("http");
const { generateKeyPairSync } = require("crypto");
const port = Number(process.argv[2]);
const privatePath = process.argv[3];
const readyPath = process.argv[4];
const kid = "vs096-google-key";
const { privateKey, publicKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
const privateJwk = { ...privateKey.export({ format: "jwk" }), kid, alg: "RS256", use: "sig" };
const publicJwk = { ...publicKey.export({ format: "jwk" }), kid, alg: "RS256", use: "sig" };
fs.writeFileSync(privatePath, `${JSON.stringify(privateJwk)}\n`, { mode: 0o600 });
const server = http.createServer((_request, response) => {
	response.writeHead(200, {
		"content-type": "application/json",
		"cache-control": "public, max-age=60"
	});
	response.end(JSON.stringify({ keys: [publicJwk] }));
});
server.listen(port, "127.0.0.1", () => fs.writeFileSync(readyPath, `${process.pid}\n`));
process.on("SIGTERM", () => server.close(() => process.exit(0)));
NODE
	vs096_google_jwks_pid="$!"
	for _ in {1..60}; do
		[[ -f "$jwks_ready" ]] && break
		if ! kill -0 "$vs096_google_jwks_pid" >/dev/null 2>&1; then
			echo "VS-096 Google JWKS fixture exited before readiness"
			cat "$jwks_log"
			exit 1
		fi
		sleep 0.05
	done
	if [[ ! -f "$jwks_ready" ]]; then
		echo "VS-096 Google JWKS fixture did not become ready"
		cat "$jwks_log"
		exit 1
	fi

	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	PORTAL_GOOGLE_CLIENT_ID="$google_client_id" \
	PORTAL_GOOGLE_JWKS_URL="http://127.0.0.1:${jwks_port}/jwks" \
	PORTAL_ENABLE_TEST_FAULTS=1 \
	PORTAL_TEST_STORE_PAUSE_MARKER="$marker" \
	PORTAL_TEST_STORE_PAUSE_RELEASE="$release" \
	PORTAL_TEST_STORE_PAUSE_PHASE=before \
	PORTAL_TEST_STORE_PAUSE_AT=3 \
	PORTAL_TEST_STORE_PAUSE_TIMEOUT_MS=10000 \
	node web/portal/dev-server.mjs >"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 Google crash fixture exited during startup"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 Google crash fixture did not become healthy"
		cat "$log"
		exit 1
	fi

	nonce_payload="$(curl -fsS -X POST "http://127.0.0.1:${port}/api/oauth/google/nonce" -H 'content-type: application/json' -d '{}')"
	nonce="$(node -e 'const p=JSON.parse(process.argv[1]); if(!p.nonce) throw new Error("nonce missing"); process.stdout.write(p.nonce);' "$nonce_payload")"
	credential="$(mint_vs096_google_token "$private_jwk" "$nonce" "$google_client_id" "$google_subject" "$email" "$username")"
	curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/google" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({credential:process.argv[1],nonce:process.argv[2],username:process.argv[3],gamePassword:process.argv[4],termsAccepted:true,termsVersion:"2026-07-16"}))' "$credential" "$nonce" "$username" "$original_password")" \
		>"$status_file" 2>"$request_log" &
	vs096_request_pid="$!"
	for _ in {1..300}; do
		[[ -f "$marker" ]] && break
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 Google fixture exited before the post-game crash boundary"
			cat "$log"
			exit 1
		fi
		sleep 0.02
	done
	if [[ ! -f "$marker" ]]; then
		echo "VS-096 Google fixture did not reach before store write 3"
		cat "$log"
		exit 1
	fi
	kill -KILL "$vs096_pid"
	wait "$vs096_pid" >/dev/null 2>&1 || true
	vs096_pid=""
	wait "$vs096_request_pid" >/dev/null 2>&1 || true
	vs096_request_pid=""

	node - "$store_dir/dev-store.json" "$email" "$username" "$google_subject" "$original_password" <<'NODE'
const fs = require("fs");
const text = fs.readFileSync(process.argv[2], "utf8");
const store = JSON.parse(text);
const email = process.argv[3];
const username = process.argv[4];
const subject = process.argv[5];
const accounts = (store.accounts || []).filter((row) => row.emailCanonical === email);
if (accounts.length !== 1 || accounts[0].status !== "provisioning" || accounts[0].passwordHash !== null) {
	throw new Error("Google crash must retain one passwordless provisioning account: " + JSON.stringify(accounts));
}
const accountId = Number(accounts[0].id);
const identities = (store.identities || []).filter((row) => Number(row.accountId) === accountId);
if (identities.length !== 1 || identities[0].provider !== "google" || identities[0].providerSubject !== subject) {
	throw new Error("Google crash must retain exactly one matching identity: " + JSON.stringify(identities));
}
const characters = (store.characters || []).filter((row) => Number(row.accountId) === accountId);
if (characters.length !== 1 || characters[0].name !== username
	|| characters[0].source !== "openrsc-sqlite-provisioning"
	|| characters[0].linkStatus !== "provisioning" || characters[0].playerId !== null
	|| characters[0].initialSignup !== true) {
	throw new Error("Google crash must retain exactly one ownership anchor: " + JSON.stringify(characters));
}
if ((store.founders || []).filter((row) => row.emailCanonical === email).length !== 1) {
	throw new Error("Google crash must retain exactly one matching founder reservation");
}
if ((store.sessions || []).some((row) => Number(row.accountId) === accountId)) {
	throw new Error("crashed Google signup must not issue a session");
}
if (text.includes(process.argv[6])) throw new Error("Google crash store retained the plaintext game password");
NODE
	player_id="$(sqlite3 "$db" "SELECT id FROM players WHERE username='${username}';")"
	if [[ ! "$player_id" =~ ^[1-9][0-9]*$ ]]; then
		echo "VS-096 Google crash did not retain exactly one game player"
		exit 1
	fi
	if [[ "$(sqlite3 "$db" "SELECT COUNT(*) FROM player_cache WHERE playerID=${player_id} AND key='web_account_id' AND value='1';")" != "1" ]]; then
		echo "VS-096 Google crash did not retain the anchored ownership marker"
		exit 1
	fi

	# A mismatched clone must fail before binding the listener and must not mutate
	# either durable store while diagnosing the ownership conflict.
	cp -R "$store_dir" "$mismatch_store_dir"
	sqlite3 "$db" ".backup '$mismatch_db'"
	sqlite3 "$mismatch_db" "UPDATE players SET email='mismatch-owner@example.com' WHERE id=${player_id};"
	store_hash_before="$(shasum -a 256 "$mismatch_store_dir/dev-store.json" | awk '{print $1}')"
	db_hash_before="$(shasum -a 256 "$mismatch_db" | awk '{print $1}')"
	db_logical_hash_before="$(sqlite3 "$mismatch_db" .dump | shasum -a 256 | awk '{print $1}')"
	PORT="$mismatch_port" \
	PORTAL_DATA_DIR="$mismatch_store_dir" \
	PORTAL_OPENRSC_DB="$mismatch_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	node web/portal/dev-server.mjs >"$mismatch_log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${mismatch_port}/api/health" >/dev/null 2>&1; then
			echo "ownership-mismatched Google clone bound its public listener"
			kill "$vs096_pid" >/dev/null 2>&1 || true
			wait "$vs096_pid" >/dev/null 2>&1 || true
			vs096_pid=""
			exit 1
		fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then break; fi
		sleep 0.1
	done
	if kill -0 "$vs096_pid" >/dev/null 2>&1; then
		echo "ownership-mismatched Google clone did not fail startup"
		kill "$vs096_pid" >/dev/null 2>&1 || true
		wait "$vs096_pid" >/dev/null 2>&1 || true
		vs096_pid=""
		exit 1
	fi
	if wait "$vs096_pid"; then mismatch_exit=0; else mismatch_exit="$?"; fi
	vs096_pid=""
	if [[ "$mismatch_exit" == "0" ]]; then
		echo "ownership-mismatched Google clone exited successfully"
		exit 1
	fi
	grep -q 'portal_ownership_reconciliation_failed' "$mismatch_log" || { cat "$mismatch_log"; echo "mismatched Google clone did not report ownership reconciliation failure"; exit 1; }
	grep -q 'provisioning_link_mismatch' "$mismatch_log" || { cat "$mismatch_log"; echo "mismatched Google clone reported the wrong reconciliation reason"; exit 1; }
	store_hash_after="$(shasum -a 256 "$mismatch_store_dir/dev-store.json" | awk '{print $1}')"
	db_hash_after="$(shasum -a 256 "$mismatch_db" | awk '{print $1}')"
	db_logical_hash_after="$(sqlite3 "$mismatch_db" .dump | shasum -a 256 | awk '{print $1}')"
	if [[ "$store_hash_before" != "$store_hash_after" || "$db_hash_before" != "$db_hash_after" || "$db_logical_hash_before" != "$db_logical_hash_after" ]]; then
		echo "failed ownership reconciliation mutated its portal store or game database"
		exit 1
	fi

	PORT="$port" \
	PORTAL_DATA_DIR="$store_dir" \
	PORTAL_OPENRSC_DB="$db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	PORTAL_GOOGLE_CLIENT_ID="$google_client_id" \
	PORTAL_GOOGLE_JWKS_URL="http://127.0.0.1:${jwks_port}/jwks" \
	node web/portal/dev-server.mjs >>"$log" 2>&1 &
	vs096_pid="$!"
	for _ in {1..60}; do
		if curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then break; fi
		if ! kill -0 "$vs096_pid" >/dev/null 2>&1; then
			echo "VS-096 Google replacement refused a valid ownership anchor"
			cat "$log"
			exit 1
		fi
		sleep 0.1
	done
	if ! curl -fsS "http://127.0.0.1:${port}/api/health" >/dev/null 2>&1; then
		echo "VS-096 Google replacement did not become healthy"
		cat "$log"
		exit 1
	fi

	nonce_payload="$(curl -fsS -X POST "http://127.0.0.1:${port}/api/oauth/google/nonce" -H 'content-type: application/json' -d '{}')"
	nonce="$(node -e 'const p=JSON.parse(process.argv[1]); if(!p.nonce) throw new Error("nonce missing"); process.stdout.write(p.nonce);' "$nonce_payload")"
	credential="$(mint_vs096_google_token "$private_jwk" "$nonce" "$google_client_id" "$google_subject" "$email" "$username")"
	wrong_status="$(curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/google" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({credential:process.argv[1],nonce:process.argv[2],username:process.argv[3],gamePassword:process.argv[4],termsAccepted:true,termsVersion:"2026-07-16"}))' "$credential" "$nonce" "$username" "$wrong_password")")"
	if [[ "$wrong_status" != "409" ]]; then
		echo "Google retry with a different game password should return HTTP 409, got ${wrong_status}"
		cat "$response"
		exit 1
	fi
	node -e 'const p=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); if(p.error!=="game_password_mismatch") throw new Error("wrong Google retry error: "+JSON.stringify(p));' "$response"
	node - "$store_dir/dev-store.json" "$email" "$username" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
const account = (store.accounts || []).find((row) => row.emailCanonical === process.argv[3]);
const characters = account && (store.characters || []).filter((row) => Number(row.accountId) === Number(account.id) && row.name === process.argv[4]);
if (!account || account.status !== "provisioning" || characters.length !== 1
	|| characters[0].source !== "openrsc-sqlite-created" || characters[0].linkStatus !== "linked"
	|| characters[0].provisioningCompletionPending !== true) {
	throw new Error("mismatched-password retry changed the provisioning state: " + JSON.stringify({ account, characters }));
}
NODE

	nonce_payload="$(curl -fsS -X POST "http://127.0.0.1:${port}/api/oauth/google/nonce" -H 'content-type: application/json' -d '{}')"
	nonce="$(node -e 'const p=JSON.parse(process.argv[1]); if(!p.nonce) throw new Error("nonce missing"); process.stdout.write(p.nonce);' "$nonce_payload")"
	credential="$(mint_vs096_google_token "$private_jwk" "$nonce" "$google_client_id" "$google_subject" "$email" "$username")"
	success_status="$(curl -sS -o "$response" -w '%{http_code}' \
		-X POST "http://127.0.0.1:${port}/api/accounts/google" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({credential:process.argv[1],nonce:process.argv[2],username:process.argv[3],gamePassword:process.argv[4],termsAccepted:true,termsVersion:"2026-07-16"}))' "$credential" "$nonce" "$username" "$original_password")")"
	if [[ "$success_status" != "200" ]]; then
		echo "Google retry with the original game password should return HTTP 200, got ${success_status}"
		cat "$response"
		cat "$log"
		exit 1
	fi
	node -e 'const p=JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")); if(!p.token || !p.auth || p.auth.googleConnected!==true || !p.account || p.account.status!=="active" || !(p.characters||[]).some((row)=>row.name===process.argv[2])) throw new Error("completed Google retry did not return an active Google session");' "$response" "$username"

	node - "$store_dir/dev-store.json" "$db" "$email" "$username" "$google_subject" "$original_password" "$wrong_password" <<'NODE'
const fs = require("fs");
const { execFileSync } = require("child_process");
const storeText = fs.readFileSync(process.argv[2], "utf8");
const store = JSON.parse(storeText);
const db = process.argv[3];
const email = process.argv[4];
const username = process.argv[5];
const subject = process.argv[6];
if (storeText.includes(process.argv[7]) || storeText.includes(process.argv[8])) {
	throw new Error("completed Google retry retained a plaintext game password");
}
const accounts = (store.accounts || []).filter((row) => row.emailCanonical === email);
if (accounts.length !== 1 || accounts[0].status !== "active" || accounts[0].passwordHash !== null) {
	throw new Error("completed Google retry did not preserve one passwordless active account: " + JSON.stringify(accounts));
}
const accountId = Number(accounts[0].id);
const identities = (store.identities || []).filter((row) => Number(row.accountId) === accountId);
if (identities.length !== 1 || identities[0].provider !== "google" || identities[0].providerSubject !== subject) {
	throw new Error("completed Google retry did not preserve one identity: " + JSON.stringify(identities));
}
const characters = (store.characters || []).filter((row) => Number(row.accountId) === accountId && row.name === username);
if (characters.length !== 1 || characters[0].source !== "openrsc-sqlite-created"
	|| characters[0].linkStatus !== "linked" || !Number(characters[0].playerId)
	|| Object.prototype.hasOwnProperty.call(characters[0], "provisioningCompletionPending")) {
	throw new Error("completed Google retry did not preserve one linked character: " + JSON.stringify(characters));
}
const sql = (statement) => execFileSync("sqlite3", [db, statement], { encoding: "utf8" }).trim();
const escapedUsername = username.replaceAll("'", "''");
const playerIds = sql(`SELECT id FROM players WHERE username='${escapedUsername}'`).split(/\s+/).filter(Boolean);
if (playerIds.length !== 1 || Number(playerIds[0]) !== Number(characters[0].playerId)) {
	throw new Error("completed Google retry did not preserve one matching game player");
}
const playerId = Number(playerIds[0]);
for (const table of ["curstats", "maxstats", "experience", "capped_experience"]) {
	if (sql(`SELECT COUNT(*) FROM ${table} WHERE playerID=${playerId}`) !== "1") {
		throw new Error(`completed Google retry did not preserve one ${table} row`);
	}
}
const markers = sql(`SELECT value FROM player_cache WHERE playerID=${playerId} AND key='web_account_id'`).split(/\s+/).filter(Boolean);
if (markers.length !== 1 || Number(markers[0]) !== accountId) {
	throw new Error("completed Google retry did not preserve one ownership marker");
}
NODE

	kill -TERM "$vs096_pid"
	wait "$vs096_pid"
	vs096_pid=""
	kill -TERM "$vs096_google_jwks_pid"
	wait "$vs096_google_jwks_pid"
	vs096_google_jwks_pid=""
}

run_vs096_google_signup_crash_case

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
grep -q '"openAt": "2026-07-18T18:00:00.000Z"' <<<"$public_payload" || { echo "public-mode /api/public should expose the configured launch timestamp"; exit 1; }
grep -q '"Mobile web client"' <<<"$public_payload" || { echo "public-mode /api/public should expose the mobile web client action"; exit 1; }
grep -q '"iOS and Android browsers"' <<<"$public_payload" || { echo "public-mode /api/public should label the web client as mobile-only"; exit 1; }
grep -q '"Voidscape launcher"' <<<"$public_payload" || { echo "public-mode /api/public should expose the launcher download"; exit 1; }
grep -q '"Android APK"' <<<"$public_payload" || { echo "public-mode /api/public should expose the Android APK download"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
const source = payload.integrity && payload.integrity.build && payload.integrity.build.source || {};
if (source.status !== 'publication_pending') throw new Error('pending packaged source metadata should remain publication_pending');
for (const field of ['repositoryUrl', 'commit', 'shortCommit', 'branch']) {
	if (source[field] !== '') throw new Error('pending source proof must keep ' + field + ' blank');
}
if (source.dirty !== false) throw new Error('pending source proof must not expose private build dirty state');
const serialized = JSON.stringify(payload);
if (serialized.includes('privateBuildCommit') || serialized.includes(process.argv[2])) {
	throw new Error('public payload exposed private build provenance');
}
" "$public_payload" "$private_build_commit"
node -e "
const payload = JSON.parse(process.argv[1]);
const apkBuilt = process.argv[2] === '1';
const android = (payload.downloads || []).find((row) => row.slug === 'android-apk');
const play = (payload.downloads || []).find((row) => row.slug === 'android-play');
const apkHasSidecar = process.argv[3] === '1';
if (play) throw new Error('unset PORTAL_ANDROID_PLAY_URL must not expose a Google Play row');
if (!android) throw new Error('public payload should keep an Android APK row for launch-open UI copy');
if (android.label !== 'Android APK' || android.fallback === true) throw new Error('unset Play URL should preserve the APK-only contract');
if (apkBuilt) {
	if (android.available !== true) throw new Error('built Android APK should be public in the launch-open chooser');
	if (android.url !== '/downloads/android-apk') throw new Error('built Android APK should link to /downloads/android-apk');
	if (!/^[0-9a-f]{64}$/.test(android.sha256 || '')) throw new Error('built Android APK should expose sha256');
	if (apkHasSidecar && !Number.isInteger(android.clientVersion)) throw new Error('built Android APK sidecar should expose clientVersion');
} else {
	if (android.available === true) throw new Error('missing Android APK should not be marked available');
	if (android.url !== '#') throw new Error('missing Android APK row should not link to a download');
}
" "$public_payload" "$([[ -f "$default_android_apk" ]] && printf 1 || printf 0)" "$([[ -f "${default_android_apk}.json" ]] && printf 1 || printf 0)"
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
	grep -q '<title>Voidscape - Reserve your name</title>' <<<"$landing_html" || { echo "landing page should be launch-branded"; exit 1; }
	grep -q 'Launch opens in' <<<"$landing_html" || { echo "landing page should include the launch countdown"; exit 1; }
	if grep -q 'href="https://voidscape.gg/play/"' <<<"$landing_html"; then
		echo "prelaunch landing should not send visitors to the web client before launch"
		exit 1
	fi
	grep -q 'Reserve + claim free week' <<<"$landing_html" || { echo "landing page should prioritize the reserve/free-card CTA"; exit 1; }
	grep -q 'data-signin' <<<"$landing_html" || { echo "landing page should include a visible account sign-in CTA"; exit 1; }
	grep -q 'web, desktop &amp; mobile' <<<"$landing_html" || { echo "landing page should explain platform support without play buttons"; exit 1; }
	grep -q 'href="/features"' <<<"$landing_html" || { echo "landing page should link to the full feature guide"; exit 1; }
	grep -q 'href="/transparency"' <<<"$landing_html" || { echo "landing page should link to the transparency page"; exit 1; }
	grep -q 'href="/privacy"' <<<"$landing_html" || { echo "landing page should link to the privacy policy"; exit 1; }
	grep -q 'href="/data-deletion"' <<<"$landing_html" || { echo "landing page should link to data deletion"; exit 1; }
	grep -q 'Source publication pending' <<<"$landing_html" || { echo "landing page should report deferred source publication"; exit 1; }
	if grep -q 'href="https://github.com/voidscape-gg/voidscape"' <<<"$landing_html"; then
		echo "landing page must not advertise the older mirror as current source"
		exit 1
	fi
	if grep -q 'Open-RSC/Core-Framework' <<<"$landing_html"; then
		echo "landing page should not use upstream-only source disclosure"
		exit 1
	fi
	grep -q 'Desktop launcher' <<<"$landing_html" || { echo "landing page should include install help"; exit 1; }
	grep -q 'id="play-block" hidden' <<<"$landing_html" || { echo "landing page should keep post-launch play actions hidden before launch"; exit 1; }
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
	if grep -q 'class="landing-integrity' <<<"$landing_html"; then
		echo "landing page should not embed the full transparency dashboard"
		exit 1
	fi
	transparency_html="$(curl -fsS "http://127.0.0.1:${public_port}/transparency")"
	grep -q 'transparency.js' <<<"$transparency_html" || { echo "/transparency should load the standalone transparency script"; exit 1; }
	grep -q 'trust-staff-board' <<<"$transparency_html" || { echo "/transparency should include the staff ledger board"; exit 1; }
	grep -q 'trust-source-board' <<<"$transparency_html" || { echo "/transparency should include the build proof board"; exit 1; }
	expect_status 200 "http://127.0.0.1:${public_port}/privacy"
	expect_status 200 "http://127.0.0.1:${public_port}/community-rules"
	community_rules_html="$(curl -fsS "http://127.0.0.1:${public_port}/community-rules")"
	grep -q '<title>Voidscape Community Rules</title>' <<<"$community_rules_html" || { echo "/community-rules should serve the current policy"; exit 1; }
	grep -q 'version 2026-07-16' <<<"$community_rules_html" || { echo "/community-rules should identify the registration terms version"; exit 1; }
	expect_status 302 "http://127.0.0.1:${public_port}/portal?auth=register"
	expect_status 302 -I "http://127.0.0.1:${public_port}/portal?auth=register"
	registration_headers="$(curl -sS -D - -o /dev/null "http://127.0.0.1:${public_port}/portal?auth=register&ref=VOID-ABC123")"
	grep -Eqi '^location: /\?auth=register&ref=VOID-ABC123\r?$' <<<"$registration_headers" || {
		echo "/portal?auth=register should redirect to the rules-gated landing signup and preserve a valid referral"
		exit 1
	}
	registration_html="$(curl -fsSL "http://127.0.0.1:${public_port}/portal?auth=register")"
	grep -q 'id="reserve-form"' <<<"$registration_html" || { echo "registration redirect should land on the signup form"; exit 1; }
	grep -q 'id="reserve-terms" type="checkbox" value="2026-07-16"' <<<"$registration_html" || { echo "registration redirect should retain the current Community Rules acceptance gate"; exit 1; }
	grep -q 'href="/community-rules"' <<<"$registration_html" || { echo "registration redirect should expose the Community Rules link"; exit 1; }
	expect_status 200 "http://127.0.0.1:${public_port}/data-deletion"
	privacy_html="$(curl -fsS "http://127.0.0.1:${public_port}/privacy")"
	grep -q 'Voidscape account data should stay boring and explainable.' <<<"$privacy_html" || { echo "/privacy should render the policy copy"; exit 1; }
	grep -q 'Source publication pending' <<<"$privacy_html" || { echo "/privacy should report deferred source publication"; exit 1; }
	if grep -q 'Open-RSC/Core-Framework' <<<"$privacy_html"; then
		echo "/privacy should not use upstream-only source disclosure"
		exit 1
	fi
	deletion_html="$(curl -fsS "http://127.0.0.1:${public_port}/data-deletion")"
	grep -q 'Request account data deletion.' <<<"$deletion_html" || { echo "/data-deletion should render the deletion flow"; exit 1; }
	grep -q 'Source publication pending' <<<"$deletion_html" || { echo "/data-deletion should report deferred source publication"; exit 1; }
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
initialize_public_store "$tmp_dir/android-public-store"
android_release_apk="$tmp_dir/voidscape-release.apk"
dd if=/dev/zero of="$android_release_apk" bs=2048 count=1 >/dev/null 2>&1
android_release_sha="$(shasum -a 256 "$android_release_apk" | awk '{print $1}')"
cat >"${android_release_apk}.json" <<JSON
{"clientVersion":10123,"sha256":"$android_release_sha","sizeBytes":2048,"builtAt":"2026-07-06T00:00:00.000Z","gitCommit":"test-fixture","buildType":"debug"}
JSON
	PORT="$android_public_port" \
	PORTAL_DATA_DIR="$tmp_dir/android-public-store" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_ANDROID_PLAY_URL="https://play.google.com/store/apps/details?hl=en&id=com.voidscape.gg&utm_source=portal-smoke" \
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
const play = (payload.downloads || []).find((row) => row.slug === 'android-play');
if (!play || play.available !== true || play.platformPrimary !== true || play.platform !== 'android' || play.external !== true) {
	throw new Error('configured Google Play listing should be the primary external Android choice');
}
if (play.url !== 'https://play.google.com/store/apps/details?id=com.voidscape.gg') {
	throw new Error('Google Play URL should be canonicalized, got ' + (play && play.url));
}
if (!android || android.available !== true) throw new Error('explicit public Android APK should be available');
if (android.label !== 'Signed APK fallback' || android.fallback !== true || android.platform !== 'android') {
	throw new Error('configured Play listing should keep the signed APK as an explicit fallback');
}
if (android.url !== '/downloads/android-apk') throw new Error('explicit public Android APK should link to /downloads/android-apk');
if (!/^[0-9a-f]{64}$/.test(android.sha256 || '')) throw new Error('explicit public Android APK should expose sha256');
if (android.clientVersion !== 10123) throw new Error('explicit public Android APK should expose sidecar clientVersion');
if ((payload.downloads || []).indexOf(play) > (payload.downloads || []).indexOf(android)) throw new Error('Google Play should appear before its APK fallback');
if ((payload.integrity && payload.integrity.build && payload.integrity.build.artifacts || []).some((row) => row.slug === 'android-play')) {
	throw new Error('external Play listing must not appear as a file-hash build artifact');
}
" "$android_public_payload"
expect_status 200 "http://127.0.0.1:${android_public_port}/downloads/android-apk"

kill "$android_public_pid" >/dev/null 2>&1 || true
wait "$android_public_pid" >/dev/null 2>&1 || true
trap cleanup EXIT

# ---- PORTAL_LAUNCH_SIGNUP_MODE public account flow ----
cp "$base_fixture_db" "$launch_fixture_db"
launch_port=$((PORT + 2))
initialize_public_store "$tmp_dir/launch-store"
PORT="$launch_port" \
	PORTAL_DATA_DIR="$tmp_dir/launch-store" \
	PORTAL_OPENRSC_DB="$launch_fixture_db" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=2 \
	PORTAL_LAUNCH_AT="2099-07-18T18:00:00Z" \
	PORTAL_GOOGLE_CLIENT_ID="test-google-client" \
	PORTAL_EMAIL_PROVIDER=resend \
	PORTAL_EMAIL_DRY_RUN=1 \
	PORTAL_EMAIL_FROM="Voidscape <launch@voidscape.gg>" \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
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
launch_portal_headers="$(curl -fsSI "http://127.0.0.1:${launch_port}/portal")"
grep -qi '^content-security-policy:' <<<"$launch_portal_headers" || { echo "portal HTML should send a Content-Security-Policy header"; exit 1; }
grep -qi '^x-frame-options: DENY' <<<"$launch_portal_headers" || { echo "portal HTML should deny framing"; exit 1; }
grep -qi '^x-content-type-options: nosniff' <<<"$launch_portal_headers" || { echo "portal HTML should send nosniff"; exit 1; }
launch_api_headers="$(curl -fsS -D - -o /dev/null "http://127.0.0.1:${launch_port}/api/health")"
grep -qi '^content-security-policy:' <<<"$launch_api_headers" || { echo "portal JSON should send a Content-Security-Policy header"; exit 1; }
grep -qi '^referrer-policy:' <<<"$launch_api_headers" || { echo "portal JSON should send a Referrer-Policy header"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.publicMode) throw new Error('launch health should report public mode');
if (!payload.launchSignupMode) throw new Error('launch health should report launch-signup mode');
if (!payload.storage || payload.storage.durable !== true) throw new Error('launch health should report durable portal storage');
if (!payload.openRscDb || payload.openRscDb.configured !== true) throw new Error('launch health should report the OpenRSC DB bridge');
if (!payload.config || payload.config.publicReady !== true) throw new Error('launch health should report public config ready');
if (payload.config.abuseHashSaltConfigured !== true) throw new Error('launch health should report a configured abuse hash salt');
if (payload.config.adminTokenConfigured !== true) throw new Error('launch health should report a configured admin token');
if (!payload.config.email || payload.config.email.provider !== 'dry-run') throw new Error('launch health should report dry-run email provider');
if (payload.config.email.configured !== true) throw new Error('launch health should report configured email delivery');
if (payload.config.email.dryRun !== true) throw new Error('launch health should report email dry run');
if ((payload.config.issues || []).length) throw new Error('launch health should not report config issues: ' + JSON.stringify(payload.config.issues));
" "$launch_health_payload"
grep -q '"launchSignupMode": true' <<<"$launch_public_payload" || { echo "launch-signup mode should be exposed to the frontend"; exit 1; }
grep -q '"memberWorld": true' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose the global members-world flag"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
const rules = payload.worldRules || {};
if (rules.registration !== 'hybrid') throw new Error('launch metadata should describe hybrid registration');
if (rules.webRegistration !== 'portal-first') throw new Error('web registration should remain portal-first');
if (rules.desktopRegistration !== 'packet') throw new Error('desktop registration should use the packet flow');
if (rules.nativeAndroidRegistration !== 'portal-first') throw new Error('native Android registration should use the policy-gated portal flow');
if (rules.packetRegistration !== true) throw new Error('launch metadata should report packet registration enabled');
if (rules.communityTermsVersion !== '2026-07-16') throw new Error('launch metadata should expose the current registration terms version');
if (rules.communityTermsUrl !== '/community-rules') throw new Error('launch metadata should link the current community rules');
" "$launch_public_payload"
grep -q '"oauth": {' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose OAuth config"; exit 1; }
grep -q '"clientId": "test-google-client"' <<<"$launch_public_payload" || { echo "launch-signup /api/public should expose the Google client id when configured"; exit 1; }
node -e "
const payload = JSON.parse(process.argv[1]);
const downloads = Array.isArray(payload.downloads) ? payload.downloads : [];
const webClient = downloads.find((row) => row && row.slug === 'web-client');
if (!webClient || webClient.url !== 'https://voidscape.gg/play/?phone=1') {
	throw new Error('web-client should force the phone shell, got ' + (webClient && webClient.url));
}
const bad = downloads.filter((row) => row && row.available && row.publicDownload && String(row.url || '').startsWith('http://'));
if (bad.length) throw new Error('public downloads must not use http URLs: ' + bad.map((row) => row.slug).join(', '));
for (const row of downloads) {
	if (!row || !row.available || !row.publicDownload || row.slug === 'web-client') continue;
	if (!String(row.url || '').startsWith('https://voidscape.gg/downloads/')) {
		throw new Error(row.slug + ' should use the configured HTTPS public origin, got ' + row.url);
	}
}
" "$launch_public_payload"
if [[ -f "Client_Base/Open_RSC_Client.jar" ]]; then
	launch_manifest="$(curl -fsS "http://127.0.0.1:${launch_port}/api/launcher/manifest.properties")"
	grep -q '^baseUrl=https://voidscape\.gg/downloads/cache/$' <<<"$launch_manifest" || { echo "launch-signup launcher manifest should use the configured HTTPS cache origin"; exit 1; }
	grep -q '^file\.1\.url=https://voidscape\.gg/downloads/client-runtime$' <<<"$launch_manifest" || { echo "launch-signup launcher manifest should use the configured HTTPS client URL"; exit 1; }
	if [[ -f "PC_Launcher/OpenRSC.jar" ]]; then
		grep -q '^launcher\.url=https://voidscape\.gg/downloads/launcher$' <<<"$launch_manifest" || { echo "launch-signup launcher self-update URL should use the configured HTTPS public origin"; exit 1; }
	fi
fi

# Launch signup shares one password with the first game character. It accepts
# only letters and numbers and keeps the launch-specific 8-20 limit.
expect_terms_acceptance_required -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"NoTerms","email":"no-terms@example.com","password":"NoTerms123"}'
expect_terms_acceptance_required -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"OldTerms","email":"old-terms@example.com","password":"OldTerms123","termsAccepted":true,"termsVersion":"2026-07-15"}'
expect_status 400 -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"BadLaunch","email":"bad-launch@example.com","password":"Ab12345","termsAccepted":true,"termsVersion":"2026-07-16"}'
for invalid_launch_password in \
	'A12345678901234567890' \
	'Bad!Pass1' \
	'Bad Pass1' \
	'Bad`Pass1' \
	'Bad£Pass1' \
	'Bad💥Pass1' \
	$'Bad\nPass1'; do
	expect_invalid_game_password -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({username:"BadLaunch",email:"bad-launch@example.com",password:process.argv[1],termsAccepted:true,termsVersion:"2026-07-16"}))' "$invalid_launch_password")"
done

launch_signup="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"LaunchGuy","email":"launch-guy@example.com","password":"Launchpass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
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
node - "$tmp_dir/launch-store/dev-store.json" <<'NODE'
const fs = require("fs");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const account = (store.accounts || []).find((row) => row.emailCanonical === "launch-guy@example.com");
if (!account) throw new Error("launch signup account is missing from the portal store");
if (account.communityTermsVersion !== "2026-07-16") throw new Error("launch signup did not persist the accepted terms version");
if (!Number.isFinite(Date.parse(account.communityTermsAcceptedAt || ""))) throw new Error("launch signup did not persist the terms acceptance timestamp");
NODE
synthetic_signup="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"NativeTest","email":"native-player-test@native.voidscape.invalid","password":"Nativepass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
grep -q '"NativeTest"' <<<"$synthetic_signup" || { echo "synthetic-email fixture account should still register"; exit 1; }
synthetic_reset_request="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/password-reset/request" \
	-H 'content-type: application/json' \
	-d '{"identifier":"NativeTest"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.accepted !== true) throw new Error('synthetic-email recovery should keep the generic accepted response');
if (payload.maskedEmail) throw new Error('synthetic-email recovery must not claim that a reset email was sent');
" "$synthetic_reset_request"
for _ in {1..30}; do
	launch_emails="$(curl -fsS -H "x-portal-admin-token: ${public_admin_token}" "http://127.0.0.1:${launch_port}/api/admin/emails")"
	if node -e "
const payload = JSON.parse(process.argv[1]);
const event = (payload.events || []).find((row) => row.type === 'signup_confirmation' && row.email === 'launch-guy@example.com');
if (!event || event.status !== 'sent' || event.provider !== 'dry-run') process.exit(1);
" "$launch_emails"; then
		break
	fi
	sleep 0.1
done
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.config || payload.config.provider !== 'dry-run' || payload.config.configured !== true) throw new Error('admin email list should expose safe email config');
const event = (payload.events || []).find((row) => row.type === 'signup_confirmation' && row.email === 'launch-guy@example.com');
if (!event) throw new Error('launch signup should queue a confirmation email event');
if (event.status !== 'sent') throw new Error('dry-run confirmation email should be marked sent, got ' + event.status);
if (event.username !== 'LaunchGuy') throw new Error('confirmation email event should include the reserved username');
if ((payload.events || []).some((row) => String(row.email || '').endsWith('.invalid'))) throw new Error('synthetic .invalid accounts must not queue email');
" "$launch_emails"
launch_email_backfill="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/emails/signup-confirmations" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"dryRun":true}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.dryRun !== true) throw new Error('confirmation backfill dry run should not send');
if (payload.eligible !== 1) throw new Error('confirmation backfill should exclude synthetic .invalid accounts');
if (payload.queued !== 0 || payload.sent !== 0) throw new Error('confirmation backfill dry run should queue/send nothing');
" "$launch_email_backfill"
launch_live_email_dry="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/emails/launch-live" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"dryRun":true}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.dryRun !== true) throw new Error('launch-live dry run should not send');
if (payload.eligible !== 1) throw new Error('launch-live dry run should exclude synthetic .invalid accounts');
if (payload.queued !== 0 || payload.sent !== 0) throw new Error('launch-live dry run should queue/send nothing');
" "$launch_live_email_dry"
launch_48h_email_dry="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/emails/launch-48h" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"dryRun":true}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.dryRun !== true) throw new Error('launch-48h dry run should not send');
if (payload.type !== 'launch_48h') throw new Error('launch-48h dry run should report the launch_48h type');
if (payload.eligible !== 1) throw new Error('launch-48h dry run should exclude synthetic .invalid accounts');
if (payload.queued !== 0 || payload.sent !== 0) throw new Error('launch-48h dry run should queue/send nothing');
" "$launch_48h_email_dry"

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
	-d '{"currentPassword":"Launchpass1"}')"
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
if (payload.ok !== true) throw new Error('launch-signup password recovery should complete successfully');
if (payload.token) throw new Error('launch-signup password recovery should return to sign-in instead of issuing a session');
" "$launch_recovered"
for _ in {1..10}; do
	expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/recover-password" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 203.0.113.88' \
		-d '{"email":"recovery-spray@example.com","code":"VOID-BAD-BAD","newPassword":"Recoverpass1"}'
done
expect_status 429 -X POST "http://127.0.0.1:${launch_port}/api/accounts/recover-password" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 203.0.113.88' \
	-d '{"email":"recovery-spray@example.com","code":"VOID-BAD-BAD","newPassword":"Recoverpass1"}'
expect_status 401 -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -H "authorization: Bearer ${launch_login_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass1"}'
launch_login_after_recovery="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.token) throw new Error('launch-signup recovered password should support returning login');
if (!payload.security || !payload.security.recoveryCodes || payload.security.recoveryCodes.activeCount !== 0) {
  throw new Error('password recovery should revoke every remaining recovery code');
}
" "$launch_login_after_recovery"
launch_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_login_after_recovery")"

# VS-049: per-IP login throttle. X-Forwarded-For from a loopback peer is trusted by
# clientIp(), so a synthetic remote IP can accrue failures while the battery's own
# loopback logins stay exempt.
for _ in {1..10}; do
	expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 203.0.113.77' \
		-d '{"email":"launch-guy@example.com","password":"WrongPass1"}'
done
expect_status 429 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 203.0.113.77' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}'
launch_other_ip_login="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 203.0.113.78' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}')"
grep -q '"token":' <<<"$launch_other_ip_login" || { echo "login throttle must be per-IP; a clean IP should still log in"; exit 1; }
launch_local_login="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}')"
grep -q '"token":' <<<"$launch_local_login" || { echo "loopback without x-forwarded-for should stay exempt from the login throttle"; exit 1; }
launch_local_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_local_login")"
if [[ -z "$launch_local_token" ]]; then
	echo "loopback login returned an empty token"
	exit 1
fi
launch_logout="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/logout" \
	-H "authorization: Bearer ${launch_local_token}")"
grep -q '"ok": true' <<<"$launch_logout" || { echo "logout endpoint should return ok"; exit 1; }
expect_status 401 -H "authorization: Bearer ${launch_local_token}" "http://127.0.0.1:${launch_port}/api/account"
for i in {1..9}; do
	expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
		-H 'content-type: application/json' \
		-H "x-forwarded-for: 198.51.100.${i}" \
		-d '{"email":"launch-guy@example.com","password":"WrongPass2"}'
done
expect_status 429 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.250' \
	-d '{"email":"launch-guy@example.com","password":"Launchpass2"}'

# VS-051: a garbage Google credential must fail on the credential (401
# invalid_google_token), not trip the username check first (the old ordering
# answered 400 invalid_username).
expect_terms_acceptance_required -X POST "http://127.0.0.1:${launch_port}/api/accounts/google" \
	-H 'content-type: application/json' \
	-d '{"credential":"garbage"}'
expect_terms_acceptance_required -X POST "http://127.0.0.1:${launch_port}/api/accounts/google" \
	-H 'content-type: application/json' \
	-d '{"credential":"garbage","termsAccepted":true,"termsVersion":"2026-07-15"}'
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/google" \
	-H 'content-type: application/json' \
	-d '{"credential":"garbage","termsAccepted":true,"termsVersion":"2026-07-16"}'

launch_player_count="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players WHERE username='LaunchGuy' AND group_id=10;")"
if [[ "$launch_player_count" != "1" ]]; then
	echo "launch-signup registration should create one normal User-ranked OpenRSC player"
	exit 1
fi
launch_link_count="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players p JOIN player_cache pc ON pc.playerID=p.id AND pc.key='web_account_id' AND pc.value='1' WHERE p.username='LaunchGuy';")"
if [[ "$launch_link_count" != "1" ]]; then
	echo "launch-signup OpenRSC player should be linked to the web account"
	exit 1
fi

launch_starter_card_count="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND (key LIKE 'starter:1:%' OR key='starter_card:1');")"
if [[ "$launch_starter_card_count" != "0" ]]; then
	echo "launch signup should record qualification without minting the frozen per-character ledger"
	exit 1
fi

launch_admin_waiting="$(curl -fsS -H "x-portal-admin-token: ${public_admin_token}" "http://127.0.0.1:${launch_port}/api/admin/accounts/1")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('admin account state should show starter card waiting');
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('admin account state should expose one waiting starter card');
if (!Array.isArray(payload.rewards.cards) || payload.rewards.cards[0].source !== 'launch_24h_card') throw new Error('pre-freeze character should expose its launch-window card marker');
" "$launch_admin_waiting"
launch_starter_marker_total_before_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND (key LIKE 'starter:1:%' OR key='starter_card:1');")"

# Game-only creation keeps its 4-20 limit and accepts letters and numbers only.
for invalid_game_password in 'A12' 'A12345678901234567890' 'Bad!' 'Bad Pass1' 'Bad`Pass1' 'Bad£Pass1' 'Bad💥Pass1' $'Bad\nPass1'; do
	expect_invalid_game_password -X POST "http://127.0.0.1:${launch_port}/api/characters" \
		-H "authorization: Bearer ${launch_token}" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({name:"BadAlt",gamePassword:process.argv[1]}))' "$invalid_game_password")"
done
launch_extra_character="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchAlt","gamePassword":"A1b2"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 2) throw new Error('creating a second launch character should use a second character slot');
const extra = payload.characters.find((character) => character.name === 'LaunchAlt');
if (!extra) throw new Error('second launch character should appear in the account roster');
if (extra.source !== 'openrsc-sqlite-created') throw new Error('second launch character should create a real OpenRSC save');
if (extra.linkStatus !== 'linked') throw new Error('second launch character should be linked to the account');
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('second character creation should leave the starter card waiting');
if (payload.rewards.starterSubscriptionCards !== 2) throw new Error('second character creation should grant that character its own launch card');
if (!Array.isArray(payload.rewards.cards) || payload.rewards.cards.length !== 2) throw new Error('second character creation should expose two per-character launch cards');
" "$launch_extra_character"
launch_player_count_after_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players WHERE username IN ('LaunchGuy', 'LaunchAlt') AND group_id=10;")"
if [[ "$launch_player_count_after_extra" != "2" ]]; then
	echo "second launch character creation should create exactly two normal User-ranked OpenRSC players"
	exit 1
fi
launch_extra_link_count="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players p JOIN player_cache pc ON pc.playerID=p.id AND pc.key='web_account_id' AND pc.value='1' WHERE p.username='LaunchAlt';")"
if [[ "$launch_extra_link_count" != "1" ]]; then
	echo "second launch OpenRSC player should be linked to the same web account"
	exit 1
fi
expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchAlt","gamePassword":"A1b2"}'
if [[ "$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players WHERE username='LaunchAlt';")" != "1" ]]; then
	echo "completed additional-character duplicates must not create another game player"
	exit 1
fi
launch_starter_marker_count_after_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter:1:%';")"
if [[ "$launch_starter_marker_count_after_extra" != "0" ]]; then
	echo "second launch character creation must not enter the frozen founder-card manifest"
	exit 1
fi
launch_starter_marker_total_after_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND (key LIKE 'starter:1:%' OR key='starter_card:1');")"
if [[ "$launch_starter_marker_total_after_extra" != "$launch_starter_marker_total_before_extra" ]]; then
	echo "second launch character creation should not mint additional starter-card markers"
	exit 1
fi
launch_extra_id="$(node -e "
const payload = JSON.parse(process.argv[1]);
const extra = payload.characters.find((character) => character.name === 'LaunchAlt');
if (!extra || !extra.id) throw new Error('second launch character should expose a portal id');
process.stdout.write(String(extra.id));
" "$launch_extra_character")"
launch_guy_player_id="$(sqlite3 "$launch_fixture_db" "SELECT id FROM players WHERE username='LaunchGuy' LIMIT 1;")"
launch_extra_player_id="$(sqlite3 "$launch_fixture_db" "SELECT id FROM players WHERE username='LaunchAlt' LIMIT 1;")"
if [[ -z "$launch_guy_player_id" || -z "$launch_extra_player_id" ]]; then
	echo "launch founder-ledger fixture requires stable player IDs"
	exit 1
fi
launch_extra_password_before="$(sqlite3 -separator '|' "$launch_fixture_db" "SELECT pass, salt FROM players WHERE username='LaunchAlt' LIMIT 1;")"
node - "$launch_extra_password_before" <<'NODE'
const { spawnSync } = require("child_process");
const [stored, salt] = process.argv[2].split("|");
const encode = (value) => Buffer.from(value, "utf8").toString("base64url");
const result = spawnSync("java", [
	"-cp",
	"server/core.jar",
	"com.openrsc.server.util.rsc.PortalPasswordHasher"
], {
	input: `check\n${encode("A1b2")}\n${encode(salt)}\n${encode(stored)}\n`,
	encoding: "utf8"
});
if (result.status !== 0 || result.stdout.trim() !== "true") {
	throw new Error("additional-character password should survive into the canonical game hash: " + result.stderr);
}
NODE
for invalid_game_password in 'N!2w' 'N 2w'; do
	expect_invalid_game_password -X POST "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}/game-password" \
		-H "authorization: Bearer ${launch_token}" \
		-H 'content-type: application/json' \
		-d "$(node -e 'process.stdout.write(JSON.stringify({currentPassword:"Launchpass2",newPassword:process.argv[1]}))' "$invalid_game_password")"
done
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}/game-password" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"currentPassword":"Wrongpass1","newPassword":"N2w3"}'
launch_extra_password_after_bad="$(sqlite3 -separator '|' "$launch_fixture_db" "SELECT pass, salt FROM players WHERE username='LaunchAlt' LIMIT 1;")"
if [[ "$launch_extra_password_after_bad" != "$launch_extra_password_before" ]]; then
	echo "wrong account-password confirmation must not change a game password"
	exit 1
fi
sqlite3 "$launch_fixture_db" "UPDATE players SET online=1 WHERE username='LaunchAlt';"
expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}/game-password" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"currentPassword":"Launchpass2","newPassword":"N2w3"}'
sqlite3 "$launch_fixture_db" "UPDATE players SET online=0 WHERE username='LaunchAlt';"
launch_game_password_reset="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}/game-password" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"currentPassword":"Launchpass2","newPassword":"N2w3"}')"
grep -q '"ok": true' <<<"$launch_game_password_reset" || { echo "game-password reset should report success for the owned character"; exit 1; }
launch_extra_password_after="$(sqlite3 -separator '|' "$launch_fixture_db" "SELECT pass, salt FROM players WHERE username='LaunchAlt' LIMIT 1;")"
if [[ "$launch_extra_password_after" == "$launch_extra_password_before" ]]; then
	echo "game-password reset should rotate the OpenRSC password hash"
	exit 1
fi
launch_extra_salt_before="${launch_extra_password_before#*|}"
launch_extra_salt_after="${launch_extra_password_after#*|}"
if [[ "$launch_extra_salt_after" != "$launch_extra_salt_before" ]]; then
	echo "game-password reset must preserve the shared OpenRSC recovery salt"
	exit 1
fi
node - "$launch_extra_password_after" <<'NODE'
const { spawnSync } = require("child_process");
const [stored, salt] = process.argv[2].split("|");
const encode = (value) => Buffer.from(value, "utf8").toString("base64url");
if (!stored.startsWith("$2y$10$")) throw new Error("game-password reset should upgrade to canonical OpenRSC bcrypt");
const result = spawnSync("java", [
	"-cp",
	"server/core.jar",
	"com.openrsc.server.util.rsc.PortalPasswordHasher"
], {
	input: `check\n${encode("N2w3")}\n${encode(salt)}\n${encode(stored)}\n`,
	encoding: "utf8"
});
if (result.status !== 0 || result.stdout.trim() !== "true") {
	throw new Error("game-password reset should pass DataConversions.checkPassword: " + result.stderr);
}
NODE
launch_password_audit="$(sqlite3 -separator '|' "$launch_fixture_db" "SELECT eventAlias, message FROM player_security_changes psc JOIN players p ON p.id=psc.playerID WHERE p.username='LaunchAlt' ORDER BY psc.id DESC LIMIT 1;")"
if [[ "$launch_password_audit" != "pass_change|Portal account game-password reset" ]]; then
	echo "game-password reset should write one secret-free OpenRSC security audit"
	exit 1
fi
expect_status 401 -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}"
launch_other_account="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-d '{"username":"OtherGuy","email":"other-guy@example.com","password":"Otherpass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
launch_other_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_other_account")"
launch_other_account_id="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(String(payload.account && payload.account.id || ''));" "$launch_other_account")"
if [[ -z "$launch_other_token" || -z "$launch_other_account_id" ]]; then
	echo "second launch account should return token and account id"
	exit 1
fi
expect_status 404 -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_other_token}"
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}/game-password" \
	-H "authorization: Bearer ${launch_other_token}" \
	-H 'content-type: application/json' \
	-d '{"currentPassword":"Otherpass1","newPassword":"Stolenpass1"}'
curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/${launch_other_account_id}/status" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"status":"review","note":"test review login lock"}' >/dev/null
expect_status 401 -H "authorization: Bearer ${launch_other_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"other-guy@example.com","password":"Otherpass1"}'
curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/${launch_other_account_id}/status" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"status":"active"}' >/dev/null
launch_other_relogin="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"other-guy@example.com","password":"Otherpass1"}')"
launch_other_relogin_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$launch_other_relogin")"
if [[ -z "$launch_other_relogin_token" ]]; then
	echo "reactivated account should be able to log in"
	exit 1
fi
curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/${launch_other_account_id}/status" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"status":"locked","note":"test locked login block"}' >/dev/null
expect_status 401 -H "authorization: Bearer ${launch_other_relogin_token}" "http://127.0.0.1:${launch_port}/api/account"
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"other-guy@example.com","password":"Otherpass1"}'
sqlite3 "$launch_fixture_db" "UPDATE players SET online=1 WHERE username='LaunchAlt';"
expect_status 409 -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_token}"
launch_alt_online_still_present="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players WHERE username='LaunchAlt' AND online=1;")"
if [[ "$launch_alt_online_still_present" != "1" ]]; then
	echo "blocked online delete should leave the OpenRSC player row untouched"
	exit 1
fi
sqlite3 "$launch_fixture_db" "UPDATE players SET online=0 WHERE username='LaunchAlt';"
sqlite3 "$launch_fixture_db" "UPDATE player_cache SET value='${launch_other_account_id}' WHERE key='web_account_id' AND playerID=(SELECT id FROM players WHERE username='LaunchAlt');"
expect_status 409 -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_token}"
sqlite3 "$launch_fixture_db" "UPDATE player_cache SET value='1' WHERE key='web_account_id' AND playerID=(SELECT id FROM players WHERE username='LaunchAlt');"
no_db_port=$((PORT + 5))
mkdir -m 0700 "$tmp_dir/launch-no-db-store"
cp "$tmp_dir/launch-store/dev-store.json" "$tmp_dir/launch-no-db-store/dev-store.json"
PORT="$no_db_port" \
	PORTAL_DATA_DIR="$tmp_dir/launch-no-db-store" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_EMAIL_PROVIDER=resend \
	PORTAL_EMAIL_DRY_RUN=1 \
	PORTAL_EMAIL_FROM="Voidscape <launch@voidscape.gg>" \
	node web/portal/dev-server.mjs >/tmp/voidscape-portal-no-db-delete-smoke.log 2>&1 &
no_db_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${no_db_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done
expect_status 503 -X DELETE "http://127.0.0.1:${no_db_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_token}"
kill "$no_db_pid" >/dev/null 2>&1 || true
wait "$no_db_pid" >/dev/null 2>&1 || true
no_db_pid=""

# Qualification stays open through the advertised launch moment, but neither
# staff nor routine portal paths may freeze an account early and thereby omit a
# character created during the remaining prelaunch window.
expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"grant"}'
launch_prefreeze_rows="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter:1:%';")"
if [[ "$launch_prefreeze_rows" != "0" ]]; then
	echo "admin reconciliation must not materialize the founder freeze before launch cutoff"
	exit 1
fi

# Simulate the immutable launch freeze after both eligible characters exist. These
# global markers must survive deletion, and routine replacement creation must not
# mint a new one while the promotional window is still open.
sqlite3 "$launch_fixture_db" <<SQL
INSERT INTO player_cache (playerID, type, key, value) VALUES
  (0, 0, 'starter:1:${launch_guy_player_id}', '1'),
  (0, 0, 'starter:1:${launch_extra_player_id}', '1');
SQL
launch_delete_extra="$(curl -fsS -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_token}")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('deleting the second launch character should free the roster slot');
if (payload.characters.some((character) => character.name === 'LaunchAlt')) throw new Error('deleted launch character should leave the account roster');
if (!payload.characters.some((character) => character.name === 'LaunchGuy')) throw new Error('deleting the second launch character should keep the first character');
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('character deletion should leave the waiting starter card alone');
if (payload.rewards.starterSubscriptionCards !== 2) throw new Error('character deletion must preserve both frozen lifetime issuance rows');
" "$launch_delete_extra"
launch_alt_after_delete="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM players WHERE username='LaunchAlt';")"
if [[ "$launch_alt_after_delete" != "0" ]]; then
	echo "deleting a launch-created character should remove the OpenRSC player row"
	exit 1
fi
launch_alt_link_after_delete="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE key='web_account_id' AND playerID NOT IN (SELECT id FROM players);")"
if [[ "$launch_alt_link_after_delete" != "0" ]]; then
	echo "deleting a launch-created character should not leave orphaned web-account links"
	exit 1
fi
launch_deleted_tombstone="$(sqlite3 "$launch_fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:${launch_extra_player_id}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_deleted_tombstone" != "1" ]]; then
	echo "deleting a frozen character must preserve its global founder-card tombstone"
	exit 1
fi

launch_replacement="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchRepl","gamePassword":"R3pl"}')"
launch_replacement_portal_id="$(node -e "
const payload = JSON.parse(process.argv[1]);
const row = payload.characters.find((character) => character.name === 'LaunchRepl');
if (!row || !row.id) throw new Error('replacement character should be created');
if (payload.rewards.starterSubscriptionCards !== 3) throw new Error('replacement creation should receive its separate launch-window card without expanding the founder ledger');
process.stdout.write(String(row.id));
" "$launch_replacement")"
launch_replacement_player_id="$(sqlite3 "$launch_fixture_db" "SELECT id FROM players WHERE username='LaunchRepl' LIMIT 1;")"
launch_replacement_marker="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter:1:${launch_replacement_player_id}';")"
if [[ "$launch_replacement_marker" != "0" ]]; then
	echo "a replacement character must not inherit or replenish a frozen founder issuance"
	exit 1
fi
launch_grant_existing="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"grant"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterSubscriptionCards !== 3) throw new Error('admin grant must leave the founder and launch ledgers unchanged');
const replacement = payload.rewards.cards.find((card) => Number(card.playerId) === Number(process.argv[2]));
if (!replacement || replacement.source !== 'launch_24h_card') throw new Error('replacement must remain launch-only after admin reconciliation');
" "$launch_grant_existing" "$launch_replacement_player_id"
launch_replacement_marker_after_grant="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter:1:${launch_replacement_player_id}';")"
if [[ "$launch_replacement_marker_after_grant" != "0" ]]; then
	echo "admin reconciliation must not expand an immutable founder freeze"
	exit 1
fi
expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"revoke"}'
launch_waiting_after_rejected_revoke="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter:1:%' AND value='1';")"
if [[ "$launch_waiting_after_rejected_revoke" != "2" ]]; then
	echo "unsupported admin revoke must leave the immutable freeze untouched"
	exit 1
fi
curl -fsS -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_replacement_portal_id}" \
	-H "authorization: Bearer ${launch_token}" >/dev/null

sqlite3 "$launch_fixture_db" "UPDATE player_cache SET value='2' WHERE playerID=0 AND key='starter:1:${launch_guy_player_id}';"
launch_account_claimed="$(curl -fsS -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('the deleted sibling still has one waiting lifetime issuance');
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('one sibling marker should remain waiting');
if (payload.rewards.starterSubscriptionCardsClaimed !== 1) throw new Error('claimed starter card count should be exposed');
" "$launch_account_claimed"

expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"revoke"}'
launch_claimed_after_rejected_revoke="$(sqlite3 "$launch_fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:${launch_guy_player_id}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_claimed_after_rejected_revoke" != "2" ]]; then
	echo "unsupported admin revoke must preserve claimed audit truth"
	exit 1
fi

# Claimed legacy history remains visible as independent audit metadata even when
# valid composite rows exist; it becomes a display-only fallback only when no
# valid composite survives.
sqlite3 "$launch_fixture_db" "INSERT INTO player_cache (playerID,type,key,value) VALUES (0,0,'starter_card:1','2');"
launch_composite_with_legacy="$(curl -fsS -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.cards.length !== 2 || payload.rewards.cards.some((card) => card.legacy)) throw new Error('valid composites remain the visible reward rows');
if (payload.rewards.starterCardLedger.legacyStatus !== 'claimed') throw new Error('claimed legacy audit state must remain independently visible');
if (payload.rewards.starterCardLedger.legacyFallback) throw new Error('legacy should not double-count beside valid composites');
" "$launch_composite_with_legacy"
sqlite3 "$launch_fixture_db" <<SQL
DELETE FROM player_cache WHERE playerID=0 AND key LIKE 'starter:1:%';
DELETE FROM player_cache WHERE playerID=${launch_guy_player_id} AND key='launch_24h_card';
INSERT INTO player_cache (playerID,type,key,value) VALUES (0,0,'starter:1:2147483648','1');
SQL
expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"grant"}'
launch_legacy_only="$(curl -fsS -H "authorization: Bearer ${launch_token}" "http://127.0.0.1:${launch_port}/api/account")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'claimed') throw new Error('claimed legacy audit truth must remain visible');
if (payload.rewards.starterSubscriptionCardsClaimed !== 1) throw new Error('claimed legacy fallback should count exactly once');
if (!payload.rewards.starterCardLedger.legacyFallback) throw new Error('claimed legacy reward should identify its display-only fallback');
if (payload.rewards.cards.length !== 1 || payload.rewards.cards[0].legacy !== true) throw new Error('out-of-range composite IDs must be ignored');
" "$launch_legacy_only"

expect_status 409 -X POST "http://127.0.0.1:${launch_port}/api/characters" \
	-H "authorization: Bearer ${launch_token}" \
	-H 'content-type: application/json' \
	-d '{"name":"LaunchGuy","gamePassword":"PlayPass1"}'
google_nonce_payload="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/oauth/google/nonce" -H 'content-type: application/json' -d '{}')"
grep -q '"nonce":' <<<"$google_nonce_payload" || { echo "launch-signup Google nonce endpoint should mint a nonce when configured"; exit 1; }
google_nonce="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.nonce || '');" "$google_nonce_payload")"
if [[ -z "$google_nonce" ]]; then
	echo "launch-signup Google nonce was empty"
	exit 1
fi
expect_status 401 -X POST "http://127.0.0.1:${launch_port}/api/accounts/google" \
	-H 'content-type: application/json' \
	-d "{\"credential\":\"bad-token\",\"nonce\":\"${google_nonce}\",\"username\":\"GoogleGuy\",\"gamePassword\":\"GooglePass1\",\"termsAccepted\":true,\"termsVersion\":\"2026-07-16\"}"
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/accounts/google/dev" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/founder/simulate-referral" -H 'content-type: application/json' -d '{}'
expect_status 404 -X POST "http://127.0.0.1:${launch_port}/api/character-links/simulate-verify" -H 'content-type: application/json' -d '{}'
launch_availability="$(curl -fsS "http://127.0.0.1:${launch_port}/api/openrsc/characters/LaunchGuy?availability=1")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.available !== false) throw new Error('public launch availability check should report existing character unavailable');
" "$launch_availability"
expect_status 404 "http://127.0.0.1:${launch_port}/api/openrsc/characters/LaunchGuy"

kill "$launch_pid" >/dev/null 2>&1 || true
wait "$launch_pid" >/dev/null 2>&1 || true
trap cleanup EXIT


# ---- Launch signup with required email verification ----
verify_db="$tmp_dir/openrsc-verify.db"
cp "$base_fixture_db" "$verify_db"
sqlite3 "$verify_db" "UPDATE players SET creation_date=strftime('%s','now'), creation_ip='198.51.100.70' WHERE username='SmokeHero';"
verify_log="$tmp_dir/email-verification.log"
verify_real_sqlite3="$(command -v sqlite3)"
verify_sqlite_shim_dir="$tmp_dir/verify-sqlite-shim"
verify_sqlite_fault_marker="$tmp_dir/verify-sqlite-fault-fired"
mkdir -p "$verify_sqlite_shim_dir"
node -- - "$verify_sqlite_shim_dir/sqlite3" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
fs.writeFileSync(path, `#!/usr/bin/env node
const fs = require("fs");
const { spawnSync } = require("child_process");
const args = process.argv.slice(2);
const query = args.find((arg) => arg.includes("INSERT INTO players (") && arg.includes("'VerifyGuy'"));
const marker = process.env.VS075_SQLITE_FAULT_MARKER || "";
if (query && marker && !fs.existsSync(marker)) {
  fs.writeFileSync(marker, "fired\\n");
  process.stderr.write("Error: stepping, attempt to write a readonly database (8)\\n");
  process.exit(8);
}
const child = spawnSync(process.env.VS075_REAL_SQLITE3, args, { stdio: "inherit" });
if (child.error) process.exit(127);
process.exit(Number.isInteger(child.status) ? child.status : 1);
`);
NODE
chmod 0755 "$verify_sqlite_shim_dir/sqlite3"
verify_port=$((PORT + 6))
initialize_public_store "$tmp_dir/verify-store"
PORT="$verify_port" \
	PORTAL_DATA_DIR="$tmp_dir/verify-store" \
	PORTAL_OPENRSC_DB="$verify_db" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_EMAIL_PROVIDER=resend \
	PORTAL_EMAIL_DRY_RUN=1 \
	PORTAL_EMAIL_FROM="Voidscape <launch@voidscape.gg>" \
	PORTAL_EMAIL_VERIFICATION_REQUIRED=1 \
	PORTAL_EMAIL_VERIFICATION_IP_LIMIT=3 \
	PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT=3 \
	PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60 \
	PORTAL_SIGNUP_IP_HOURLY_LIMIT=1 \
	PORTAL_SIGNUP_IP_DAILY_LIMIT=1 \
	PORTAL_CHARACTER_IP_HOURLY_LIMIT=1 \
	PORTAL_CHARACTER_IP_DAILY_LIMIT=1 \
	PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=1 \
	PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT=1 \
	PORTAL_PUBLIC_ORIGIN="https://voidscape.gg" \
	VS075_REAL_SQLITE3="$verify_real_sqlite3" \
	VS075_SQLITE_FAULT_MARKER="$verify_sqlite_fault_marker" \
	PATH="$verify_sqlite_shim_dir:$PATH" \
	node web/portal/dev-server.mjs >"$verify_log" 2>&1 &
verify_pid="$!"
for _ in {1..60}; do
	if curl -fsS "http://127.0.0.1:${verify_port}/api/health" >/dev/null 2>&1; then
		break
	fi
	sleep 0.1
done
verify_signup="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.70' \
	-d '{"username":"VerifyGuy","email":"verify-guy@example.com","password":"VerifyPass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.verificationRequired !== true) throw new Error('verified launch signup should require email verification');
if (payload.token) throw new Error('pending email verification signup must not issue a session token');
if (payload.email !== 'verify-guy@example.com') throw new Error('pending signup should echo the target email');
const ttlHours = (Date.parse(payload.expiresAt || '') - Date.now()) / 3600000;
if (ttlHours < 47.9 || ttlHours > 48.1) throw new Error('default email verification TTL should be 48 hours, got ' + ttlHours);
" "$verify_signup"

# Sending verification mail is independently throttled and must not consume the
# completed-account signup allowance for a shared IP.
verify_second_pending="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.70' \
	-d '{"username":"PendingTwo","email":"pending-two@example.com","password":"PendingPass2","termsAccepted":true,"termsVersion":"2026-07-16"}')"
node -e "const p=JSON.parse(process.argv[1]); if(p.verificationRequired!==true) throw new Error('verification send should not double-count as a completed signup');" "$verify_second_pending"

verify_pending_before_retry="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const p=(s.emailVerifications||[]).find(x=>x.emailCanonical==='verify-guy@example.com'&&x.status==='pending'); process.stdout.write(JSON.stringify({username:p.username,passwordHash:p.passwordHash,gamePasswordSealed:p.gamePasswordSealed,tokenHash:p.tokenHash,expiresAt:p.expiresAt}));" "$tmp_dir/verify-store/dev-store.json")"
verify_retry_signup="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/register" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.74' \
	-d '{"username":"HijackName","email":"verify-guy@example.com","password":"Hijackpass1","termsAccepted":true,"termsVersion":"2026-07-16"}')"
node -e "const p=JSON.parse(process.argv[1]); if(p.verificationRequired!==true || p.username!=='VerifyGuy') throw new Error('an active pending signup should be reused without accepting replacement credentials');" "$verify_retry_signup"
verify_pending_after_retry="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const p=(s.emailVerifications||[]).find(x=>x.emailCanonical==='verify-guy@example.com'&&x.status==='pending'); process.stdout.write(JSON.stringify({username:p.username,passwordHash:p.passwordHash,gamePasswordSealed:p.gamePasswordSealed,tokenHash:p.tokenHash,expiresAt:p.expiresAt}));" "$tmp_dir/verify-store/dev-store.json")"
if [[ "$verify_pending_after_retry" != "$verify_pending_before_retry" ]]; then
	echo "retrying registration must not replace an active pending signup's username, credentials, token, or expiry"
	exit 1
fi

verify_event_count_before_resend="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); process.stdout.write(String((s.emailEvents||[]).filter(e=>e.type==='email_verification').length));" "$tmp_dir/verify-store/dev-store.json")"
verify_unknown_resend="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email/resend" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.71' \
	-d '{"email":"not-pending@example.com"}')"
verify_known_resend="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email/resend" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.71' \
	-d '{"email":"verify-guy@example.com"}')"
node -e "
const unknown = JSON.parse(process.argv[1]);
const known = JSON.parse(process.argv[2]);
if (JSON.stringify(unknown) !== JSON.stringify({accepted:true})) throw new Error('unknown resend should return only the generic accepted state');
if (JSON.stringify(known) !== JSON.stringify(unknown)) throw new Error('known and unknown resend responses must be indistinguishable');
" "$verify_unknown_resend" "$verify_known_resend"
verify_event_count_after_resend="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); process.stdout.write(String((s.emailEvents||[]).filter(e=>e.type==='email_verification').length));" "$tmp_dir/verify-store/dev-store.json")"
if [[ "$verify_event_count_after_resend" != "$((verify_event_count_before_resend + 1))" ]]; then
	echo "only the known pending signup should queue a resend email"
	exit 1
fi

# The per-email limit cannot be bypassed by rotating source IPs. The original
# request, safe registration retry, and explicit resend consumed three sends.
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email/resend" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.73' \
	-d '{"email":"verify-guy@example.com"}'

# The same endpoint also limits one source that rotates target addresses.
for suffix in 1 2 3; do
	curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email/resend" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 198.51.100.80' \
		-d "{\"email\":\"unknown-${suffix}@example.com\"}" >/dev/null
done
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email/resend" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.80' \
	-d '{"email":"unknown-4@example.com"}'
grep -q '"event":"portal_rate_limit_decision"' "$verify_log" || { echo "rate-limit decisions should be logged"; exit 1; }
if grep -Eq '198\.51\.100\.(73|80)|verify-guy@example\.com|unknown-4@example\.com' "$verify_log"; then
	echo "rate-limit decision logs must not contain raw IP or email values"
	exit 1
fi
verify_before_players="$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM players WHERE username='VerifyGuy';")"
if [[ "$verify_before_players" != "0" ]]; then
	echo "pending email verification signup must not create an OpenRSC player"
	exit 1
fi
verify_before_accounts="$(node -e "const store = JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8')); process.stdout.write(String((store.accounts || []).filter((account) => account.emailCanonical === 'verify-guy@example.com').length));" "$tmp_dir/verify-store/dev-store.json")"
if [[ "$verify_before_accounts" != "0" ]]; then
	echo "pending email verification signup must not create a portal account"
	exit 1
fi
node - "$tmp_dir/verify-store/dev-store.json" "$public_abuse_salt" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const pending = (store.emailVerifications || []).find((row) =>
	row.status === "pending" && row.emailCanonical === "verify-guy@example.com"
);
if (!pending || !pending.gamePasswordSealed) throw new Error("pending signup game-password seal not found");
if (pending.communityTermsVersion !== "2026-07-16") throw new Error("pending signup did not preserve the accepted terms version");
if (!Number.isFinite(Date.parse(pending.communityTermsAcceptedAt || ""))) throw new Error("pending signup did not preserve the terms acceptance timestamp");
const [version, ivText, tagText, encryptedText] = String(pending.gamePasswordSealed).split(".");
if (version !== "v1" || !ivText || !tagText || !encryptedText) throw new Error("game-password seal should use v1 format");
const key = createHash("sha256").update(`${process.argv[3]}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
const password = Buffer.concat([
	decipher.update(Buffer.from(encryptedText, "base64url")),
	decipher.final()
]).toString("utf8");
if (password !== "VerifyPass1") throw new Error("pending signup must seal the game password byte-for-byte");
NODE
verify_token="$(node - "$tmp_dir/verify-store/dev-store.json" "$public_abuse_salt" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const salt = process.argv[3];
const pending = (store.emailVerifications || []).find((row) =>
	row.status === "pending" && row.emailCanonical === "verify-guy@example.com"
);
if (!pending || !pending.verificationTokenSealed) {
	throw new Error("pending signup with sealed verification token not found");
}
const [version, ivText, tagText, encryptedText] = String(pending.verificationTokenSealed).split(".");
if (version !== "v1" || !ivText || !tagText || !encryptedText) {
	throw new Error("verification token seal should use v1 format");
}
const key = createHash("sha256").update(`${salt}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
process.stdout.write(Buffer.concat([
	decipher.update(Buffer.from(encryptedText, "base64url")),
	decipher.final()
]).toString("utf8"));
NODE
)"
if [[ -z "$verify_token" ]]; then
	echo "email verification dry-run token could not be decrypted"
	exit 1
fi
verify_redirect_headers="$(curl -sS -D - -o /dev/null "http://127.0.0.1:${verify_port}/api/accounts/verify-email?token=${verify_token}")"
if ! grep -qE '^HTTP/[0-9.]+ 303' <<<"$verify_redirect_headers"; then
	echo "email verification GET should be a non-mutating 303 confirmation redirect"
	exit 1
fi
if ! grep -qiE '^location: /portal\?auth=verify#verify=' <<<"$verify_redirect_headers"; then
	echo "email verification GET should redirect to the fragment-based confirmation screen"
	exit 1
fi
verify_after_scanner_accounts="$(node -e "const store = JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8')); process.stdout.write(String((store.accounts || []).filter((account) => account.emailCanonical === 'verify-guy@example.com').length));" "$tmp_dir/verify-store/dev-store.json")"
if [[ "$verify_after_scanner_accounts" != "0" ]]; then
	echo "email verification GET must not create an account before explicit confirmation"
	exit 1
fi
verify_failure_status="$(curl -sS -o "$tmp_dir/verify-sqlite-failure-response.json" -w '%{http_code}' \
	-X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.70' \
	-d "{\"token\":\"${verify_token}\"}")"
node -- - "$verify_log" "$verify_token" "$verify_db" <<'NODE'
const fs = require("fs");
const [logPath, token, databasePath] = process.argv.slice(2);
const log = fs.readFileSync(logPath, "utf8");
const forbidden = [
	"verify-guy@example.com",
	"VerifyGuy",
	"VerifyPass1",
	"198.51.100.70",
	token,
	databasePath,
	"INSERT INTO players",
	"BEGIN IMMEDIATE",
	"pass, salt",
	"creation_ip",
	"sqlite3 -cmd"
];
for (const value of forbidden) {
	if (value && log.includes(value)) throw new Error(`SQLite failure log exposed forbidden value: ${value}`);
}
if (/\$2y\$10\$[./A-Za-z0-9]{53}/.test(log)) {
	throw new Error("SQLite failure log exposed a generated game-password hash");
}
const prefix = "openrsc_sqlite_child_failed ";
const lines = log.split(/\r?\n/).filter((line) => line.startsWith(prefix));
if (lines.length !== 1) throw new Error(`expected one sanitized SQLite child record, got ${lines.length}`);
if (Buffer.byteLength(lines[0], "utf8") > 256) throw new Error("sanitized SQLite child record exceeded 256 bytes");
const record = JSON.parse(lines[0].slice(prefix.length));
if (Object.keys(record).sort().join(",") !== "code,operation,summary") {
	throw new Error("sanitized SQLite child record contains unexpected fields");
}
if (record.operation !== "create_openrsc_player" || record.code !== 8 || record.summary !== "readonly_database") {
	throw new Error(`unexpected sanitized SQLite child record: ${JSON.stringify(record)}`);
}
NODE
if [[ "$verify_failure_status" != "503" ]] || ! grep -q '"openrsc_db_unavailable"' "$tmp_dir/verify-sqlite-failure-response.json"; then
	echo "injected SQLite write failure should return retryable openrsc_db_unavailable"
	exit 1
fi
node - "$tmp_dir/verify-store/dev-store.json" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
const pending = (store.emailVerifications || []).find((row) => row.emailCanonical === "verify-guy@example.com");
const accounts = (store.accounts || []).filter((row) => row.emailCanonical === "verify-guy@example.com");
const characters = accounts.length
	? (store.characters || []).filter((row) => Number(row.accountId) === Number(accounts[0].id))
	: [];
if (!pending || pending.status !== "pending"
	|| accounts.length !== 1 || accounts[0].status !== "provisioning"
	|| characters.length !== 1
	|| characters[0].source !== "openrsc-sqlite-provisioning"
	|| characters[0].linkStatus !== "provisioning"
	|| characters[0].playerId !== null) {
	throw new Error("SQLite failure should retain one retryable ownership anchor: "
		+ JSON.stringify({ pending, accounts, characters }));
}
if (JSON.stringify(characters[0]).includes("VerifyPass1")) {
	throw new Error("provisioning anchor retained the game password");
}
NODE
if [[ "$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM players WHERE username='VerifyGuy';")" != "0" ]]; then
	echo "injected SQLite write failure should leave no game player before retry"
	exit 1
fi
verify_complete="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.70' \
	-d "{\"token\":\"${verify_token}\"}")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.token) throw new Error('email verification should issue a session token');
if (!payload.account || payload.account.email !== 'verify-guy@example.com') throw new Error('email verification should create the portal account');
if (!Array.isArray(payload.characters) || !payload.characters.some((character) => character.name === 'VerifyGuy' && character.source === 'openrsc-sqlite-created')) {
	throw new Error('email verification should create and link the first OpenRSC character');
}
if (!payload.rewards || payload.rewards.starterCardStatus !== 'waiting') throw new Error('email verification should reserve the starter card');
" "$verify_complete"
verify_session_token="$(node -e "const payload = JSON.parse(process.argv[1]); process.stdout.write(payload.token || '');" "$verify_complete")"
verify_game_password="$(sqlite3 -separator '|' "$verify_db" "SELECT pass, salt FROM players WHERE username='VerifyGuy' LIMIT 1;")"
node - "$verify_game_password" <<'NODE'
const { spawnSync } = require("child_process");
const [stored, salt] = process.argv[2].split("|");
const encode = (value) => Buffer.from(value, "utf8").toString("base64url");
const result = spawnSync("java", [
	"-cp",
	"server/core.jar",
	"com.openrsc.server.util.rsc.PortalPasswordHasher"
], {
	input: `check\n${encode("VerifyPass1")}\n${encode(salt)}\n${encode(stored)}\n`,
	encoding: "utf8"
});
if (result.status !== 0 || result.stdout.trim() !== "true") {
	throw new Error("verified signup password should survive into the canonical game hash: " + result.stderr);
}
NODE
node -e "
const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8'));
const p=(s.emailVerifications||[]).find(x=>x.emailCanonical==='verify-guy@example.com');
if(!p || p.status!=='verified') throw new Error('verification row should be marked verified');
if(p.passwordHash || p.gamePasswordSealed || p.tokenHash || p.verificationTokenSealed) throw new Error('used verification credentials should be cleared');
const account=(s.accounts||[]).find(x=>x.emailCanonical==='verify-guy@example.com');
if(!account || account.communityTermsVersion!=='2026-07-16') throw new Error('verified account should inherit the accepted terms version');
if(!Number.isFinite(Date.parse(account.communityTermsAcceptedAt||''))) throw new Error('verified account should inherit the terms acceptance timestamp');
if((s.emailEvents||[]).filter(e=>e.pendingSignupId===p.id).some(e=>e.metadata&&e.metadata.verificationTokenSealed)) throw new Error('verification email events should not retain used token material');
" "$tmp_dir/verify-store/dev-store.json"
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/characters" \
	-H "authorization: Bearer ${verify_session_token}" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.70' \
	-d '{"name":"VerifyTwo","gamePassword":"VerifyPass2"}'

# A clean signup IP gets one full additional-character allowance after the
# initial character, proving the initial marker is subtracted from the bucket.
pending_two_token="$(node - "$tmp_dir/verify-store/dev-store.json" "$public_abuse_salt" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const pending = (store.emailVerifications || []).find((row) =>
	row.status === "pending" && row.emailCanonical === "pending-two@example.com"
);
if (!pending || !pending.verificationTokenSealed) throw new Error("second pending signup token not found");
const [version, ivText, tagText, encryptedText] = pending.verificationTokenSealed.split(".");
if (version !== "v1") throw new Error("second pending signup token seal should use v1");
const key = createHash("sha256").update(`${process.argv[3]}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
process.stdout.write(Buffer.concat([
	decipher.update(Buffer.from(encryptedText, "base64url")),
	decipher.final()
]).toString("utf8"));
NODE
)"
pending_two_complete="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.90' \
	-d "{\"token\":\"${pending_two_token}\"}")"
pending_two_session="$(node -e "const p=JSON.parse(process.argv[1]); if(!p.characters.some(c=>c.name==='PendingTwo')) throw new Error('second pending signup should create its initial character'); process.stdout.write(p.token||'');" "$pending_two_complete")"
pending_two_additional="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/characters" \
	-H "authorization: Bearer ${pending_two_session}" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.90' \
	-d '{"name":"PendingExtra","gamePassword":"ExtraPass3"}')"
node -e "const p=JSON.parse(process.argv[1]); if(!p.characters.some(c=>c.name==='PendingExtra')) throw new Error('first additional character should not be consumed by the signup character');" "$pending_two_additional"
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/characters" \
	-H "authorization: Bearer ${pending_two_session}" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.90' \
	-d '{"name":"PendingThird","gamePassword":"ThirdPass4"}'
verify_reset_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/request" \
	-H 'content-type: application/json' \
	-d '{"identifier":"VerifyGuy"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.accepted !== true) throw new Error('username recovery should return the generic accepted state');
if (payload.maskedEmail !== 'v***@e***.com') throw new Error('username recovery should expose only the approved masked email hint');
" "$verify_reset_request"
verify_unknown_reset="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/request" \
	-H 'content-type: application/json' \
	-d '{"identifier":"NobodyHere"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.accepted !== true || payload.maskedEmail !== '') throw new Error('unknown username recovery should stay generic');
" "$verify_unknown_reset"
verify_reset_token="$(node - "$tmp_dir/verify-store/dev-store.json" "$public_abuse_salt" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const salt = process.argv[3];
const event = (store.emailEvents || []).find((row) =>
	row.type === "password_reset" && row.emailCanonical === "verify-guy@example.com"
);
if (!event || !event.metadata || !event.metadata.resetTokenSealed) {
	throw new Error("password-reset email event with sealed token not found");
}
const [version, ivText, tagText, encryptedText] = String(event.metadata.resetTokenSealed).split(".");
if (version !== "v1" || !ivText || !tagText || !encryptedText) {
	throw new Error("password-reset token seal should use v1 format");
}
const key = createHash("sha256").update(`${salt}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
const token = Buffer.concat([
	decipher.update(Buffer.from(encryptedText, "base64url")),
	decipher.final()
]).toString("utf8");
const reset = (store.passwordResetTokens || []).find((row) => row.id === event.passwordResetTokenId);
if (!reset || reset.tokenHash !== createHash("sha256").update(token).digest("base64url")) {
	throw new Error("password-reset store should retain only the token hash");
}
if (JSON.stringify(reset).includes(token)) throw new Error("password-reset row must not retain plaintext token material");
process.stdout.write(token);
NODE
)"
if [[ -z "$verify_reset_token" ]]; then
	echo "password-reset dry-run token could not be decrypted"
	exit 1
fi
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/complete" \
	-H 'content-type: application/json' \
	-d '{"token":"invalid-token-value-that-is-long-enough-12345","newPassword":"Verifynew1"}'
verify_reset_complete="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${verify_reset_token}\",\"newPassword\":\"Verifynew1\"}")"
grep -q '"ok": true' <<<"$verify_reset_complete" || { echo "password-reset token should rotate the portal password"; exit 1; }
if grep -q '"token":' <<<"$verify_reset_complete"; then
	echo "password-reset completion should return to sign-in without issuing a session"
	exit 1
fi
expect_status 401 -H "authorization: Bearer ${verify_session_token}" "http://127.0.0.1:${verify_port}/api/account"
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${verify_reset_token}\",\"newPassword\":\"Verifyagain1\"}"
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"verify-guy@example.com","password":"VerifyPass1"}'
verify_reset_login="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"verify-guy@example.com","password":"Verifynew1"}')"
grep -q '"token":' <<<"$verify_reset_login" || { echo "password-reset account should sign in explicitly with the new password"; exit 1; }
expect_status 400 -X POST "http://127.0.0.1:${verify_port}/api/accounts/verify-email" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${verify_token}\"}"
verify_after_players="$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM players WHERE username='VerifyGuy';")"
if [[ "$verify_after_players" != "1" ]]; then
	echo "email verification should create exactly one OpenRSC player"
	exit 1
fi
verify_waiting_card="$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND (key LIKE 'starter:1:%' OR key='starter_card:1');")"
if [[ "$verify_waiting_card" != "0" ]]; then
	echo "email verification should record founder qualification without expanding the launch freeze"
	exit 1
fi

# ---- Older native-character account claim ----
native_backfill="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/admin/accounts/backfill-native" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"apply":true,"grantMissingStarterCard":true}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.createdAccounts !== 2) throw new Error('native backfill should create two synthetic portal accounts, got ' + payload.createdAccounts);
if (payload.createdCharacters !== 2) throw new Error('native backfill should create two native character links, got ' + payload.createdCharacters);
if (payload.linkedPlayers !== 2) throw new Error('native backfill should stamp two ownership markers, got ' + payload.linkedPlayers);
if (payload.starterCardsGranted !== 0) throw new Error('native backfill must not mint founder-card rows outside the launch freeze');
if (payload.subscriptionsMigrated !== 1 || !payload.gameWrites || payload.gameWrites.accountSubscriptionsUpserted !== 1) {
	throw new Error('native backfill should migrate the active character subscription to its portal account');
}
" "$native_backfill"
native_backfill_starter_rows="$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND (key LIKE 'starter:%' OR key LIKE 'starter_card:%');")"
if [[ "$native_backfill_starter_rows" != "0" ]]; then
	echo "native ownership backfill must not expand the founder freeze manifest"
	exit 1
fi

legacy_store="$tmp_dir/verify-store/dev-store.json"
legacy_before="$(node - "$legacy_store" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
const character = (store.characters || []).find((row) => row.name === "SmokeHero");
const account = character && (store.accounts || []).find((row) => row.id === character.accountId);
if (!account || account.syntheticEmail !== true || account.requiresEmailUpgrade !== true || account.passwordHash) {
	throw new Error("SmokeHero should start as a passwordless synthetic account");
}
const entitlements = (store.entitlements || []).filter((row) => row.accountId === account.id);
process.stdout.write(JSON.stringify({
	accountId: account.id,
	characterId: character.id,
	playerId: character.playerId,
	characterSource: character.source,
	entitlements: entitlements.length,
	accountSource: account.source,
	subscriptionExpiresAt: account.subscriptionExpiresAt
}));
NODE
)"
legacy_account_id="$(node -e "process.stdout.write(String(JSON.parse(process.argv[1]).accountId));" "$legacy_before")"
legacy_subscription_marker="$(sqlite3 "$verify_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='acct_sub:${legacy_account_id}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$legacy_subscription_marker" != "4102444800000" ]]; then
	echo "native backfill must preserve an active character subscription in the account-level marker"
	exit 1
fi

legacy_reset_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/password-reset/request" \
	-H 'content-type: application/json' \
	-d '{"identifier":"SmokeHero"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.accepted !== true || payload.maskedEmail !== '') {
	throw new Error('older native recovery must not claim that mail was sent to a synthetic address');
}
" "$legacy_reset_request"

expect_status 400 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"SmokeHero","currentGamePassword":"","email":"legacy-owner@example.com","newPassword":"LegacyWeb1!"}'
expect_status 400 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"not-an-email","newPassword":"LegacyWeb1!"}'
expect_status 400 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"legacy-owner@example.com","newPassword":"short"}'
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.20' \
	-d '{"username":"SmokeHero","currentGamePassword":"wrong-pass","email":"legacy-owner@example.com","newPassword":"LegacyWeb1!"}'
expect_status 409 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.20' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"verify-guy@example.com","newPassword":"LegacyWeb1!"}'

legacy_claim_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.20' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"legacy-owner@example.com","newPassword":"LegacyWeb1!"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.accepted !== true || payload.verificationRequired !== true || !payload.expiresAt) {
	throw new Error('valid older-account proof should queue email verification');
}
if (payload.token) throw new Error('claim request must not expose its verification token');
" "$legacy_claim_request"

legacy_taken_collision_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.21' \
	-d '{"username":"TakenHero","currentGamePassword":"fixture-pass","email":"legacy-owner@example.com","newPassword":"TakenLegacy1!"}')"
grep -q '"verificationRequired": true' <<<"$legacy_taken_collision_request" || { echo "second pending legacy claim should reach email verification"; exit 1; }

legacy_claim_token_for_email() {
	local email="$1"
	local username="${2:-}"
	node - "$legacy_store" "$public_abuse_salt" "$email" "$username" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const salt = process.argv[3];
const email = process.argv[4];
const username = process.argv[5];
const character = username ? (store.characters || []).find((row) => row.name === username) : null;
const claim = (store.legacyAccountClaims || []).filter((row) =>
	row.targetEmailCanonical === email && (!character || row.characterId === character.id)
).at(-1);
const event = claim && (store.emailEvents || []).find((row) => row.legacyAccountClaimId === claim.id);
if (!event || !event.metadata || !event.metadata.claimTokenSealed) {
	throw new Error("legacy claim email event with sealed token not found for " + email);
}
if (!claim || claim.status !== "pending" || !String(claim.passwordHash || "").startsWith("scrypt$")) {
	throw new Error("pending legacy claim should retain only the new website password hash");
}
const [version, ivText, tagText, encryptedText] = String(event.metadata.claimTokenSealed).split(".");
if (version !== "v1" || !ivText || !tagText || !encryptedText) throw new Error("legacy claim token seal should use v1 format");
const key = createHash("sha256").update(`${salt}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
const token = Buffer.concat([
	decipher.update(Buffer.from(encryptedText, "base64url")),
	decipher.final()
]).toString("utf8");
if (claim.tokenHash !== createHash("sha256").update(token).digest("base64url")) {
	throw new Error("legacy claim should retain only the token hash");
}
if (JSON.stringify(claim).includes(token)) throw new Error("legacy claim row must not retain plaintext token material");
process.stdout.write(token);
NODE
}

legacy_claim_token="$(legacy_claim_token_for_email legacy-owner@example.com SmokeHero)"
legacy_taken_collision_token="$(node - "$legacy_store" "$public_abuse_salt" <<'NODE'
const fs = require("fs");
const { createHash, createDecipheriv } = require("crypto");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const salt = process.argv[3];
const taken = (store.characters || []).find((row) => row.name === "TakenHero");
const claim = (store.legacyAccountClaims || []).filter((row) =>
	row.characterId === taken.id && row.targetEmailCanonical === "legacy-owner@example.com" && row.status === "pending"
).at(-1);
const event = claim && (store.emailEvents || []).find((row) => row.legacyAccountClaimId === claim.id);
if (!event || !event.metadata || !event.metadata.claimTokenSealed) throw new Error("TakenHero collision token not found");
const [version, ivText, tagText, encryptedText] = event.metadata.claimTokenSealed.split(".");
if (version !== "v1") throw new Error("invalid TakenHero token seal");
const key = createHash("sha256").update(`${salt}:portal-seal`).digest();
const decipher = createDecipheriv("aes-256-gcm", key, Buffer.from(ivText, "base64url"));
decipher.setAuthTag(Buffer.from(tagText, "base64url"));
process.stdout.write(Buffer.concat([decipher.update(Buffer.from(encryptedText, "base64url")), decipher.final()]).toString("utf8"));
NODE
)"
if [[ -z "$legacy_claim_token" || -z "$legacy_taken_collision_token" ]]; then
	echo "legacy claim dry-run tokens could not be decrypted"
	exit 1
fi
legacy_superseded_token="$legacy_claim_token"
legacy_replacement_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.20' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"legacy-owner@example.com","newPassword":"LegacyWeb2!"}')"
grep -q '"verificationRequired": true' <<<"$legacy_replacement_request" || { echo "replacement legacy claim should queue a new verification"; exit 1; }
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_superseded_token}\"}"
legacy_claim_token="$(legacy_claim_token_for_email legacy-owner@example.com SmokeHero)"
if grep -qE 'fixture-pass|LegacyWeb1!|LegacyWeb2!|TakenLegacy1!' "$legacy_store"; then
	echo "legacy claim store must not retain plaintext game or website passwords"
	exit 1
fi

legacy_claim_redirect="$(curl -sS -D - -o /dev/null "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/verify?token=${legacy_claim_token}")"
if ! grep -qE '^HTTP/[0-9.]+ 303' <<<"$legacy_claim_redirect"; then
	echo "legacy claim GET should be a non-mutating 303 confirmation redirect"
	exit 1
fi
if ! grep -qiE '^location: /portal\?auth=claim-confirm#claim=' <<<"$legacy_claim_redirect"; then
	echo "legacy claim GET should redirect to a fragment-based confirmation screen"
	exit 1
fi
legacy_before_confirm="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const c=s.characters.find(x=>x.name==='SmokeHero'); const a=s.accounts.find(x=>x.id===c.accountId); process.stdout.write(String(a.syntheticEmail===true && a.requiresEmailUpgrade===true && !a.passwordHash));" "$legacy_store")"
if [[ "$legacy_before_confirm" != "true" ]]; then
	echo "legacy claim GET must not mutate the synthetic account"
	exit 1
fi

legacy_game_pass_before="$(sqlite3 "$verify_db" "SELECT pass || '|' || salt FROM players WHERE id=77;")"
legacy_marker_before="$(sqlite3 "$verify_db" "SELECT value FROM player_cache WHERE playerID=77 AND key='web_account_id' ORDER BY dbid DESC LIMIT 1;")"
legacy_claim_complete="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_claim_token}\"}")"
grep -q '"ok": true' <<<"$legacy_claim_complete" || { echo "legacy claim confirmation should upgrade the account"; exit 1; }
if grep -q '"token":' <<<"$legacy_claim_complete"; then
	echo "legacy claim completion should return to sign-in without issuing a session"
	exit 1
fi
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_claim_token}\"}"
expect_status 409 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_taken_collision_token}\"}"

expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"SmokeHero","currentGamePassword":"fixture-pass","email":"another-owner@example.com","newPassword":"AnotherWeb1!"}'
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"legacy-owner@example.com","password":"wrong-password"}'
legacy_login="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/login" \
	-H 'content-type: application/json' \
	-d '{"email":"legacy-owner@example.com","password":"LegacyWeb2!"}')"
legacy_login_token="$(node -e "const p=JSON.parse(process.argv[1]); if(!p.token) throw new Error('claimed account should require and accept a clean login'); process.stdout.write(p.token);" "$legacy_login")"
legacy_account_state="$(curl -fsS -H "authorization: Bearer ${legacy_login_token}" "http://127.0.0.1:${verify_port}/api/account")"
node -e "
const before = JSON.parse(process.argv[1]);
const state = JSON.parse(process.argv[2]);
if (!state.account || state.account.id !== before.accountId || state.account.email !== 'legacy-owner@example.com') throw new Error('claim must preserve the account id and expose the verified email');
const character = (state.characters || []).find((row) => row.id === before.characterId);
if (!character || character.playerId !== before.playerId || character.source !== before.characterSource) throw new Error('claim must preserve the native character link and source');
if (!state.rewards || state.rewards.starterSubscriptionCards !== before.entitlements) throw new Error('claim must preserve the existing founder-reward count without minting through native backfill');
" "$legacy_before" "$legacy_account_state"
legacy_after="$(node - "$legacy_store" <<'NODE'
const store = JSON.parse(require("fs").readFileSync(process.argv[2], "utf8"));
const character = store.characters.find((row) => row.name === "SmokeHero");
const account = store.accounts.find((row) => row.id === character.accountId);
const entitlements = store.entitlements.filter((row) => row.accountId === account.id);
const claim = store.legacyAccountClaims.find((row) => row.accountId === account.id && row.status === "used");
process.stdout.write(JSON.stringify({
	accountId: account.id,
	characterId: character.id,
	playerId: character.playerId,
	characterSource: character.source,
	entitlements: entitlements.length,
	accountSource: account.source,
	subscriptionExpiresAt: account.subscriptionExpiresAt,
	syntheticEmail: account.syntheticEmail,
	requiresEmailUpgrade: account.requiresEmailUpgrade,
	claimPasswordCleared: Boolean(claim && !claim.passwordHash && !claim.credentialFingerprint)
}));
NODE
)"
node -e "
const before = JSON.parse(process.argv[1]);
const after = JSON.parse(process.argv[2]);
for (const key of ['accountId','characterId','playerId','characterSource','entitlements','accountSource','subscriptionExpiresAt']) {
	if (after[key] !== before[key]) throw new Error('claim changed preserved field ' + key);
}
if (after.syntheticEmail !== false || after.requiresEmailUpgrade !== false) throw new Error('claim should clear synthetic upgrade flags');
if (after.claimPasswordCleared !== true) throw new Error('used claim should clear pending credential material');
" "$legacy_before" "$legacy_after"
legacy_game_pass_after="$(sqlite3 "$verify_db" "SELECT pass || '|' || salt FROM players WHERE id=77;")"
legacy_marker_after="$(sqlite3 "$verify_db" "SELECT value FROM player_cache WHERE playerID=77 AND key='web_account_id' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$legacy_game_pass_after" != "$legacy_game_pass_before" || "$legacy_marker_after" != "$legacy_marker_before" ]]; then
	echo "legacy claim must not change the game password, salt, or ownership marker"
	exit 1
fi

legacy_changed_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"TakenHero","currentGamePassword":"fixture-pass","email":"changed-owner@example.com","newPassword":"ChangedWeb1!"}')"
grep -q '"verificationRequired": true' <<<"$legacy_changed_request" || { echo "credential-change claim fixture should queue verification"; exit 1; }
legacy_changed_token="$(legacy_claim_token_for_email changed-owner@example.com TakenHero)"
sqlite3 "$verify_db" "UPDATE players SET pass='changed-pass' WHERE id=78;"
expect_status 409 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_changed_token}\"}"
sqlite3 "$verify_db" "UPDATE players SET pass='fixture-pass' WHERE id=78;"

legacy_expired_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"TakenHero","currentGamePassword":"fixture-pass","email":"expired-owner@example.com","newPassword":"ExpiredWeb1!"}')"
grep -q '"verificationRequired": true' <<<"$legacy_expired_request" || { echo "expired claim fixture should queue verification"; exit 1; }
legacy_expired_token="$(legacy_claim_token_for_email expired-owner@example.com TakenHero)"
node - "$legacy_store" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const store = JSON.parse(fs.readFileSync(path, "utf8"));
const claim = (store.legacyAccountClaims || []).find((row) => row.targetEmailCanonical === "expired-owner@example.com" && row.status === "pending");
if (!claim) throw new Error("pending expiry fixture claim not found");
claim.expiresAtMs = Date.now() - 1000;
claim.expiresAt = new Date(claim.expiresAtMs).toISOString();
const temporary = path + ".expiry-test.tmp";
fs.writeFileSync(temporary, JSON.stringify(store, null, 2) + "\n", { mode: 0o600 });
fs.renameSync(temporary, path);
NODE
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_expired_token}\"}"
node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const c=s.legacyAccountClaims.find(x=>x.targetEmailCanonical==='expired-owner@example.com'); if(!c || c.status!=='expired' || c.passwordHash || c.credentialFingerprint) throw new Error('expired claim should clear pending credential material');" "$legacy_store"

legacy_marker_request="$(curl -fsS -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-d '{"username":"TakenHero","currentGamePassword":"fixture-pass","email":"marker-owner@example.com","newPassword":"MarkerWeb1!"}')"
grep -q '"verificationRequired": true' <<<"$legacy_marker_request" || { echo "ownership-marker claim fixture should queue verification"; exit 1; }
legacy_marker_token="$(legacy_claim_token_for_email marker-owner@example.com TakenHero)"
sqlite3 "$verify_db" "INSERT INTO player_cache (playerID,type,key,value) VALUES (78,0,'web_account_id','999');"
expect_status 409 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-d "{\"token\":\"${legacy_marker_token}\"}"

for _ in {1..5}; do
	expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 198.51.100.30' \
		-d '{"username":"ClaimGhost","currentGamePassword":"fixture-pass","email":"ghost@example.com","newPassword":"GhostWeb1!"}'
done
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.30' \
	-d '{"username":"ClaimGhost","currentGamePassword":"fixture-pass","email":"ghost@example.com","newPassword":"GhostWeb1!"}'
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.32' \
	-d '{"username":"ClaimGhost","currentGamePassword":"fixture-pass","email":"ghost@example.com","newPassword":"GhostWeb1!"}'
for suffix in {1..10}; do
	expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 198.51.100.31' \
		-d "{\"username\":\"Ghost${suffix}\",\"currentGamePassword\":\"fixture-pass\",\"email\":\"ghost${suffix}@example.com\",\"newPassword\":\"GhostWeb1!\"}"
done
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.31' \
	-d '{"username":"GhostEleven","currentGamePassword":"fixture-pass","email":"ghost11@example.com","newPassword":"GhostWeb1!"}'

for _ in {1..10}; do
	expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
		-H 'content-type: application/json' \
		-H 'x-forwarded-for: 198.51.100.40' \
		-d '{"token":"invalid-legacy-claim-token-value-1234567890"}'
done
expect_status 429 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/complete" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.40' \
	-d '{"token":"invalid-legacy-claim-token-value-1234567890"}'

node - "$legacy_store" <<'NODE'
const fs = require("fs");
const path = process.argv[2];
const store = JSON.parse(fs.readFileSync(path, "utf8"));
const event = (store.emailEvents || []).find((row) => row.type === "legacy_account_claim");
if (!event) throw new Error("legacy claim email fixture not found");
event.status = "sending";
event.updatedAt = new Date(Date.now() - 20 * 60 * 1000).toISOString();
const temporary = path + ".interrupted-email-test.tmp";
fs.writeFileSync(temporary, JSON.stringify(store, null, 2) + "\n", { mode: 0o600 });
fs.renameSync(temporary, path);
NODE
expect_status 401 -X POST "http://127.0.0.1:${verify_port}/api/accounts/legacy-claim/request" \
	-H 'content-type: application/json' \
	-H 'x-forwarded-for: 198.51.100.41' \
	-d '{"username":"MailRecovery","currentGamePassword":"fixture-pass","email":"mail-recovery@example.com","newPassword":"MailWeb1!"}'
node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const e=s.emailEvents.find(x=>x.type==='legacy_account_claim'); if(!e || e.status!=='failed' || e.lastError!=='email_delivery_interrupted') throw new Error('interrupted email delivery should recover to a retryable failure');" "$legacy_store"

kill "$verify_pid" >/dev/null 2>&1 || true
wait "$verify_pid" >/dev/null 2>&1 || true
verify_pid=""

# A base signup code and its founder classifier are one atomic unit. Force the
# classifier insert to abort and prove the signup row is rolled back too.
sqlite3 "$fixture_db" <<'SQL'
CREATE TRIGGER fail_founder_base_tag_insert
BEFORE INSERT ON player_cache
WHEN NEW.playerID=0 AND NEW.key LIKE 'base_tag:%'
BEGIN
  SELECT RAISE(ABORT, 'forced base tag failure');
END;
SQL
atomic_founder="$(curl -fsS -X POST "http://127.0.0.1:${PORT}/api/founder/reservations" \
	-H 'content-type: application/json' \
	-d '{"username":"AtomicFail","email":"atomic-fail@example.com"}')"
atomic_founder_code="$(node -e "
const payload = JSON.parse(process.argv[1]);
if (!payload.signup || payload.signup.syncedToGame !== false) throw new Error('forced classifier failure should leave the portal code unsynced');
process.stdout.write(String(payload.signup.code || '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 20));
" "$atomic_founder")"
atomic_founder_rows="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key IN ('signup_code:${atomic_founder_code}','base_tag:${atomic_founder_code}','base_acct:${atomic_founder_code}');")"
if [[ "$atomic_founder_rows" != "0" ]]; then
	echo "founder signup code and classifier must roll back together"
	exit 1
fi
sqlite3 "$fixture_db" "DROP TRIGGER fail_founder_base_tag_insert;"

# Simulate one in-game base-code redemption with an unbound legacy classifier.
# The routine admin repair must preserve that ambiguity rather than binding the
# code to whichever same-name character happens to exist now. Also remove one
# clean referral row and contaminate another with a founder tag so repair must
# recreate only the clean reward and fail closed on the contaminated one.
read -r redeemed_base_code repair_referral_code contaminated_referral_code < <(node - "$tmp_dir/dev-store.json" <<'NODE'
const fs = require("fs");
const store = JSON.parse(fs.readFileSync(process.argv[2], "utf8"));
const normalize = (code) => String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 20);
const founder = (store.founders || []).find((entry) => entry.signupCode && Array.isArray(entry.referralRewardCodes) && entry.referralRewardCodes.length);
if (!founder) throw new Error("founder with referral rewards not found");
const referrals = founder.referralRewardCodes.filter((entry) => entry && entry.code && entry.syncedAt);
if (referrals.length < 2) throw new Error("two synced referral reward fixtures not found");
process.stdout.write(`${normalize(founder.signupCode)} ${normalize(referrals[0].code)} ${normalize(referrals[1].code)}\n`);
NODE
)
sqlite3 "$fixture_db" <<SQL
UPDATE player_cache SET value='2' WHERE playerID=0 AND key='signup_code:${redeemed_base_code}';
DELETE FROM player_cache WHERE playerID=0 AND key='base_tag:${redeemed_base_code}';
DELETE FROM player_cache WHERE playerID=0 AND key='signup_code:${repair_referral_code}';
DELETE FROM player_cache WHERE playerID=0 AND key='signup_code:${contaminated_referral_code}';
INSERT INTO player_cache (playerID,type,key,value) VALUES (0,0,'base_tag:${contaminated_referral_code}','0');
SQL

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
if (result.referralSynced < 1) throw new Error('sync should repair the clean missing referral reward row');
if (result.referralFailed !== 1) throw new Error('sync should fail closed on the contaminated referral classifier');
" "$sync_result"; then
	echo "admin signup sync failed: $sync_result"
	exit 1
fi
redeemed_base_tag="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:${redeemed_base_code}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$redeemed_base_tag" != "0" ]]; then
	echo "routine sync must not infer a positive player binding for an already-redeemed legacy base code"
	exit 1
fi
repaired_referral_value="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:${repair_referral_code}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$repaired_referral_value" != "1" ]]; then
	echo "admin signup sync should restore a missing referral reward code"
	exit 1
fi
repaired_referral_tag_count="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key IN ('base_tag:${repair_referral_code}','base_acct:${repair_referral_code}');")"
if [[ "$repaired_referral_tag_count" != "0" ]]; then
	echo "admin signup sync must keep referral rewards independent of founder base tags"
	exit 1
fi
contaminated_referral_rows="$(sqlite3 "$fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:${contaminated_referral_code}';")"
if [[ "$contaminated_referral_rows" != "0" ]]; then
	echo "referral repair must not activate a code carrying a founder classifier"
	exit 1
fi

# Once staff removes the invalid classifier, the same repair safely restores the
# referral. Founder tags must also reject out-of-range and mismatched bindings.
sqlite3 "$fixture_db" "DELETE FROM player_cache WHERE playerID=0 AND key='base_tag:${contaminated_referral_code}';"
referral_repair_retry="$(curl -fsS -X POST -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups/sync")"
node -e "
const result = JSON.parse(process.argv[1]);
if (result.failed !== 0 || result.referralFailed !== 0) throw new Error('corrected signup ledgers should repair cleanly');
if (result.referralSynced < 2) throw new Error('forced repair should reconcile every referral reward');
" "$referral_repair_retry"
assert_repaired_contaminated="$(sqlite3 "$fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:${contaminated_referral_code}' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$assert_repaired_contaminated" != "1" ]]; then
	echo "corrected referral reward should be restored as available"
	exit 1
fi

sqlite3 "$fixture_db" "UPDATE player_cache SET value='2147483648' WHERE playerID=0 AND key='base_tag:${redeemed_base_code}';"
out_of_range_sync="$(curl -fsS -X POST -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups/sync")"
node -e "const result=JSON.parse(process.argv[1]); if(result.failed < 1) throw new Error('out-of-range founder tag must fail sync');" "$out_of_range_sync"
sqlite3 "$fixture_db" "UPDATE player_cache SET value='2147483647' WHERE playerID=0 AND key='base_tag:${redeemed_base_code}';"
mismatched_sync="$(curl -fsS -X POST -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups/sync")"
node -e "const result=JSON.parse(process.argv[1]); if(result.failed < 1) throw new Error('wrong positive founder binding must fail sync');" "$mismatched_sync"
sqlite3 "$fixture_db" "UPDATE player_cache SET value='0' WHERE playerID=0 AND key='base_tag:${redeemed_base_code}';"
final_sync="$(curl -fsS -X POST -H 'x-portal-admin-token: dev-admin' "http://127.0.0.1:${PORT}/api/admin/signups/sync")"
node -e "const result=JSON.parse(process.argv[1]); if(result.failed !== 0 || result.referralFailed !== 0) throw new Error('restored canonical ledgers should pass final sync');" "$final_sync"
