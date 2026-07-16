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
public_pid=""
no_db_pid=""
verify_pid=""
drain_pid=""
drain_restart_pid=""
drain_request_pid=""

cleanup() {
	if [[ -n "$server_pid" ]]; then
		kill "$server_pid" >/dev/null 2>&1 || true
		wait "$server_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$captcha_pid" ]]; then
		kill "$captcha_pid" >/dev/null 2>&1 || true
		wait "$captcha_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$public_pid" ]]; then
		kill "$public_pid" >/dev/null 2>&1 || true
		wait "$public_pid" >/dev/null 2>&1 || true
	fi
	if [[ -n "$no_db_pid" ]]; then
		kill "$no_db_pid" >/dev/null 2>&1 || true
		wait "$no_db_pid" >/dev/null 2>&1 || true
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
	rm -rf "$tmp_dir"
}
trap cleanup EXIT

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
	(77, 1, 'player_title_active', 'founder'),
	(77, 0, 'void_path', '1'),
	(0, 3, 'char_sub:77', '4102444800000');
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
cp "$fixture_db" "$base_fixture_db"

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
PORT="$PORT" PORTAL_ADMIN_TOKEN="dev-admin" PORTAL_SIGNUP_IP_DAILY_LIMIT=3 PORTAL_CHARACTER_IP_DAILY_LIMIT=2 node web/portal/api-smoke.mjs

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

# ---- CAPTCHA-gated public signup ----
captcha_port=$((PORT + 4))
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

# ---- PORTAL_PUBLIC_MODE lockdown ----
public_port=$((PORT + 1))
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
PORT="$launch_port" \
	PORTAL_DATA_DIR="$tmp_dir/launch-store" \
	PORTAL_OPENRSC_DB="$launch_fixture_db" \
	PORTAL_INTEGRITY_SNAPSHOT="$tmp_dir/integrity-summary.json" \
	PORTAL_ADMIN_TOKEN="$public_admin_token" \
	PORTAL_ABUSE_HASH_SALT="$public_abuse_salt" \
	PORTAL_PUBLIC_MODE=1 \
	PORTAL_LAUNCH_SIGNUP_MODE=1 \
	PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=2 \
	PORTAL_LAUNCH_AT="2026-07-18T18:00:00Z" \
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

launch_starter_card_state="$(sqlite3 "$launch_fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
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
launch_starter_marker_total_before_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")"

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
if (payload.rewards.starterSubscriptionCards !== 1) throw new Error('second character creation should not grant another starter card');
if (!Array.isArray(payload.rewards.cards) || payload.rewards.cards.length !== 1) throw new Error('second character creation should leave exactly one visible starter-card entitlement');
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
launch_starter_marker_count_after_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter_card:1' AND value='1';")"
if [[ "$launch_starter_marker_count_after_extra" != "1" ]]; then
	echo "second launch character creation should preserve exactly one waiting starter card marker"
	exit 1
fi
launch_starter_marker_total_after_extra="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")"
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
mkdir -p "$tmp_dir/launch-no-db-store"
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
launch_delete_extra="$(curl -fsS -X DELETE "http://127.0.0.1:${launch_port}/api/characters/${launch_extra_id}" \
	-H "authorization: Bearer ${launch_token}")"
node -e "
const payload = JSON.parse(process.argv[1]);
if (!Array.isArray(payload.characters) || payload.characters.length !== 1) throw new Error('deleting the second launch character should free the roster slot');
if (payload.characters.some((character) => character.name === 'LaunchAlt')) throw new Error('deleted launch character should leave the account roster');
if (!payload.characters.some((character) => character.name === 'LaunchGuy')) throw new Error('deleting the second launch character should keep the first character');
if (payload.rewards.starterCardStatus !== 'waiting') throw new Error('character deletion should leave the waiting starter card alone');
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

launch_revoke="$(curl -fsS -X POST "http://127.0.0.1:${launch_port}/api/admin/accounts/1/starter-card" \
	-H "x-portal-admin-token: ${public_admin_token}" \
	-H 'content-type: application/json' \
	-d '{"action":"revoke"}')"
node -e "
const payload = JSON.parse(process.argv[1]);
if (payload.rewards.starterCardStatus !== 'none') throw new Error('admin revoke should clear an unclaimed starter card');
if (payload.rewards.starterSubscriptionCards !== 0) throw new Error('admin revoke should leave zero waiting starter cards');
" "$launch_revoke"
launch_waiting_after_revoke="$(sqlite3 "$launch_fixture_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter_card:1' AND value='1';")"
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
launch_waiting_after_grant="$(sqlite3 "$launch_fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_waiting_after_grant" != "1" ]]; then
	echo "admin starter-card grant should restore the OpenRSC marker"
	exit 1
fi

sqlite3 "$launch_fixture_db" "UPDATE player_cache SET value='2' WHERE playerID=0 AND key='starter_card:1';"
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
launch_claimed_after_revoke="$(sqlite3 "$launch_fixture_db" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter_card:1' ORDER BY dbid DESC LIMIT 1;")"
if [[ "$launch_claimed_after_revoke" != "2" ]]; then
	echo "admin starter-card revoke should preserve an already claimed marker"
	exit 1
fi

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
verify_after_failure_state="$(node -e "const s=JSON.parse(require('fs').readFileSync(process.argv[1],'utf8')); const p=(s.emailVerifications||[]).find(x=>x.emailCanonical==='verify-guy@example.com'); process.stdout.write(JSON.stringify({pending:p&&p.status,accounts:(s.accounts||[]).filter(x=>x.emailCanonical==='verify-guy@example.com').length}));" "$tmp_dir/verify-store/dev-store.json")"
if [[ "$verify_after_failure_state" != '{"pending":"pending","accounts":0}' ]] || [[ "$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM players WHERE username='VerifyGuy';")" != "0" ]]; then
	echo "injected SQLite write failure should leave the pending signup retryable with no partial account or player"
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
verify_waiting_card="$(sqlite3 "$verify_db" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter_card:1' AND value='1';")"
if [[ "$verify_waiting_card" != "1" ]]; then
	echo "email verification should create one waiting starter-card marker"
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
if (payload.starterCardsGranted !== 2) throw new Error('native backfill should preserve the promotion for both older accounts');
if (payload.subscriptionsMigrated !== 1 || !payload.gameWrites || payload.gameWrites.accountSubscriptionsUpserted !== 1) {
	throw new Error('native backfill should migrate the active character subscription to its portal account');
}
" "$native_backfill"

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
if (!state.rewards || state.rewards.starterSubscriptionCards !== 1) throw new Error('claim must preserve the starter-card promotion');
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
fs.writeFileSync(temporary, JSON.stringify(store, null, 2) + "\n");
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
fs.writeFileSync(temporary, JSON.stringify(store, null, 2) + "\n");
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
