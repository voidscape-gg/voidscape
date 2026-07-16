#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESET="$SCRIPT_DIR/reset-launch-game-db.mjs"
FREEZE_AT="2026-07-18T18:00:00.000Z"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-reset-launch-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
	echo "reset-launch-game-db test: $*" >&2
	exit 1
}

assert_eq() {
	local expected="$1"
	local actual="$2"
	local label="$3"
	[[ "$actual" == "$expected" ]] || fail "$label: expected '$expected', got '$actual'"
}

sql_value() {
	sqlite3 -noheader "$1" "$2"
}

run_reset() {
	"$RESET" --freeze-at "$FREEZE_AT" "$@"
}

create_fixture_db() {
	local db="$1"
	sqlite3 "$db" <<'SQL'
PRAGMA journal_mode = DELETE;
CREATE TABLE players (
	id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	username TEXT NOT NULL DEFAULT '',
	group_id INTEGER DEFAULT 10,
	email TEXT DEFAULT NULL,
	pass TEXT NOT NULL,
	salt TEXT NOT NULL DEFAULT '',
	creation_date INTEGER NOT NULL DEFAULT 0,
	creation_ip TEXT NOT NULL DEFAULT '0.0.0.0',
	banned TEXT NOT NULL DEFAULT '0',
	offences INTEGER NOT NULL DEFAULT 0,
	muted TEXT NOT NULL DEFAULT '0'
);
CREATE TABLE player_cache (
	playerID INTEGER NOT NULL,
	type INTEGER NOT NULL,
	key TEXT NOT NULL,
	value TEXT NOT NULL,
	dbid INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT
);
CREATE TABLE maxstats (playerID INTEGER NOT NULL PRIMARY KEY);
CREATE TABLE curstats (playerID INTEGER NOT NULL PRIMARY KEY);
CREATE TABLE experience (playerID INTEGER NOT NULL PRIMARY KEY, hits INTEGER NOT NULL DEFAULT 4616);
CREATE TABLE capped_experience (playerID INTEGER NOT NULL PRIMARY KEY);
CREATE TABLE item_provenance_events (
	eventID INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
	actorID INTEGER NOT NULL DEFAULT 0,
	source TEXT NOT NULL,
	command TEXT NOT NULL DEFAULT '',
	extra TEXT NOT NULL DEFAULT ''
);
CREATE TABLE invitems (playerID INTEGER NOT NULL, itemID INTEGER NOT NULL);
CREATE TABLE objects (id INTEGER NOT NULL PRIMARY KEY, label TEXT NOT NULL);
SQL
}

make_main_fixture() {
	local db="$1"
	local store="$2"
	node - "$store" <<'NODE' | sqlite3 "$db"
const { writeFileSync } = require("node:fs");
const storePath = process.argv[2];
const accounts = [];
const founders = [];
const characters = [];
const entitlements = [];
const createdAt = "2026-07-18T17:00:00.000Z";
const sql = ["BEGIN IMMEDIATE;"];
const quote = (value) => `'${String(value).replace(/'/g, "''")}'`;
const pad = (value) => String(value).padStart(3, "0");

let characterId = 1;
for (let accountId = 1; accountId <= 190; accountId += 1) {
	const suffix = pad(accountId);
	const email = `founder${suffix}@example.test`;
	const firstName = `F${suffix}A`;
	accounts.push({
		id: accountId,
		emailCanonical: email,
		emailDisplay: email,
		status: "active",
		createdAt
	});
	const founder = {
		id: accountId,
		username: firstName,
		normalizedName: firstName.toLowerCase(),
		emailCanonical: email,
		signupCode: `VOIDF${suffix}X${suffix}`,
		signupCodeCreatedAt: createdAt,
		createdAt,
		status: "dev_verified"
	};
	if (accountId % 2 === 0) founder.accountId = accountId;
	if (accountId === 1) {
		founder.referralRewardCodes = [{ code: "VOIDREFRZ001", createdAt }];
	}
	founders.push(founder);
	entitlements.push({
		id: accountId,
		accountId,
		type: "starter_free_subscription",
		status: "granted",
		createdAt
	});

	const characterCount = accountId <= 188 ? 1 : accountId === 189 ? 2 : 10;
	for (let slot = 1; slot <= characterCount; slot += 1) {
		const playerId = accountId * 100 + slot;
		const name = slot === 1 ? firstName : `F${suffix}B${slot}`;
		sql.push(`INSERT INTO players (id, username, group_id, email, pass, salt, creation_date, creation_ip) VALUES (${playerId}, ${quote(name)}, 10, ${quote(email)}, 'hash', 'salt', 1, '127.0.0.1');`);
		sql.push(`INSERT INTO player_cache (playerID, type, key, value) VALUES (${playerId}, 0, 'web_account_id', ${quote(accountId)});`);
		characters.push({
			id: characterId++,
			accountId,
			playerId,
			name,
			normalizedName: name.toLowerCase(),
			linkStatus: "linked",
			source: "openrsc-sqlite-created"
		});
	}
}

accounts.push({
	id: 777,
	emailCanonical: "linked-nonfounder@example.test",
	emailDisplay: "linked-nonfounder@example.test",
	status: "active",
	createdAt
});
sql.push("INSERT INTO players (id, username, group_id, email, pass, salt, creation_date, creation_ip) VALUES (900002, 'LinkedNonFounder', 10, 'linked-nonfounder@example.test', 'hash', 'salt', 1, '127.0.0.1');");
sql.push("INSERT INTO player_cache (playerID, type, key, value) VALUES (900002, 0, 'web_account_id', '777');");
characters.push({
	id: characterId++,
	accountId: 777,
	playerId: 900002,
	name: "LinkedNonFounder",
	normalizedName: "linkednonfounder",
	linkStatus: "linked",
	source: "openrsc-sqlite-created"
});

for (const player of [
	{ id: 900001, name: "NativeUsed" },
	{ id: 900003, name: "NativeFresh" }
]) {
	sql.push(`INSERT INTO players (id, username, group_id, email, pass, salt, creation_date, creation_ip) VALUES (${player.id}, ${quote(player.name)}, 10, ${quote(`${player.name.toLowerCase()}@example.test`)}, 'hash', 'salt', 1, '127.0.0.1');`);
}

sql.push(
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:5', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:188', '1');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:189', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:777', '1');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter:4:401', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:VOIDF001X001', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:VOIDREFRZ001', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:SERVERONLYZ999', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_tag:VOIDF001X001', '101');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_tag:SERVERONLYZ999', '777777');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (201, 0, 'launch_24h_card', '2');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (301, 0, 'launch_24h_card', '1');",
	"INSERT INTO player_cache (playerID, type, key, value) VALUES (900001, 0, 'launch_24h_card', '2');",
	"INSERT INTO item_provenance_events (actorID, source, command, extra) VALUES (18901, 'subscription_vendor', 'subscription_card_grant', 'grant=starter_card');",
	"INSERT INTO invitems (playerID, itemID) VALUES (101, 1602);",
	"INSERT INTO objects (id, label) VALUES (1, 'static-world-row');",
	"COMMIT;"
);

writeFileSync(storePath, `${JSON.stringify({ accounts, founders, characters, entitlements }, null, 2)}\n`);
process.stdout.write(`${sql.join("\n")}\n`);
NODE
}

assert_main_report() {
	local report="$1"
	node - "$report" <<'NODE'
const { readFileSync } = require("node:fs");
const report = JSON.parse(readFileSync(process.argv[2], "utf8"));
const expect = (condition, label) => { if (!condition) throw new Error(label); };
expect(report.founders === 190, "expected 190 founders");
expect(report.linkedFounders === 190, "expected every founder linked");
expect(report.starterCharacterMarkers === 200, "expected 200 exact character markers");
expect(report.starterCharacterClaimed === 5, "expected five migrated claimed markers");
expect(report.nativeLaunchCardMarkers === 3, "expected claimed, fresh, and linked non-founder launch markers");
expect(report.nativeLaunchCardMarkersMinted === 2, "expected fresh native and linked non-founder markers");
expect(report.suppressedNativeLaunchCardMarkers === 2, "expected founder/native overlap suppression");
expect(report.signupCodeMarkers === 192, "expected founder, referral, and server-only signup codes");
expect(report.baseTags === 191, "expected founder tags plus the server-only tag tombstone");
expect(report.baseAccounts === 190, "expected one account classifier per founder base code");
expect(report.founderCohortMarkers === 190, "expected one durable marker per founder");
expect(report.errors.length === 0 && report.ambiguities.length === 0, "expected clean reconciliation");
for (const reason of [
	"preserved_server_only_signup_code",
	"preserved_server_only_base_tag",
	"ignored_nonfounder_legacy_starter"
]) {
	expect(report.warnings.some((entry) => entry.reason === reason), `missing warning ${reason}`);
}
NODE
}

MAIN_SOURCE="$TMP_DIR/main-source.db"
MAIN_STORE="$TMP_DIR/main-store.json"
MAIN_DRY="$TMP_DIR/main-dry.json"
MAIN_OUT="$TMP_DIR/main-launch.db"
MAIN_APPLY="$TMP_DIR/main-apply.json"
create_fixture_db "$MAIN_SOURCE"
make_main_fixture "$MAIN_SOURCE" "$MAIN_STORE"

if "$RESET" --source-db "$MAIN_SOURCE" --portal-store "$MAIN_STORE" >"$TMP_DIR/missing-cutoff-stdout" 2>"$TMP_DIR/missing-cutoff-stderr"; then
	fail "reset accepted a run without an explicit freeze cutoff"
fi
grep -q -- '--freeze-at' "$TMP_DIR/missing-cutoff-stderr" \
	|| fail "missing-cutoff rejection did not explain --freeze-at"

SOURCE_CHECKSUM="$(cksum <"$MAIN_SOURCE")"
if run_reset --source-db "$MAIN_SOURCE" --portal-store "$MAIN_STORE" --out-db "$MAIN_SOURCE" --apply --force >"$TMP_DIR/same-path-stdout" 2>"$TMP_DIR/same-path-stderr"; then
	fail "reset accepted the source DB as its output path"
fi
assert_eq "$SOURCE_CHECKSUM" "$(cksum <"$MAIN_SOURCE")" "same-path source protection"
grep -q 'source DB is never overwritten' "$TMP_DIR/same-path-stderr" \
	|| fail "same-path rejection did not explain source protection"
HARDLINK_OUT="$TMP_DIR/main-source-hardlink.db"
ln "$MAIN_SOURCE" "$HARDLINK_OUT"
if run_reset --source-db "$MAIN_SOURCE" --portal-store "$MAIN_STORE" --out-db "$HARDLINK_OUT" --apply --force >"$TMP_DIR/hardlink-stdout" 2>"$TMP_DIR/hardlink-stderr"; then
	fail "reset accepted a hard link to the source DB as its output path"
fi
assert_eq "$SOURCE_CHECKSUM" "$(cksum <"$MAIN_SOURCE")" "hard-link source protection"
rm "$HARDLINK_OUT"

run_reset --source-db "$MAIN_SOURCE" --portal-store "$MAIN_STORE" >"$MAIN_DRY"
assert_main_report "$MAIN_DRY"
run_reset --source-db "$MAIN_SOURCE" --portal-store "$MAIN_STORE" --out-db "$MAIN_OUT" --apply >"$MAIN_APPLY"
assert_main_report "$MAIN_APPLY"

assert_eq "ok" "$(sql_value "$MAIN_OUT" "PRAGMA integrity_check;")" "main integrity"
assert_eq "203" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM players;")" "preserved players"
assert_eq "200" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:*:*';")" "founder marker total"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:1:*';")" "single-character founder marker count"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:189:*';")" "two-character founder marker count"
assert_eq "10" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:190:*';")" "ten-character founder marker count"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:101';")" "claimed founder base route"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:2:201';")" "claimed native overlap"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:3:301';")" "available native overlap"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:4:401';")" "existing composite state"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDF004X004';")" "tagged available code state preserved beside claimed composite"
assert_eq "401" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDF004X004';")" "claimed composite base binding"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:5:501';")" "single-character legacy claim"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:189:18901';")" "provenance-attributed sibling claim"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:189:18902';")" "unclaimed sibling"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key LIKE 'starter_card:%';")" "legacy marker removal"
assert_eq "101" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDF001X001';")" "founder base binding"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDF001X001';")" "linked founder base account classifier"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDREFRZ001';")" "referral remains untagged"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDREFRZ001';")" "referral remains without a base account classifier"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDREFRZ001';")" "referral redeemed state"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:SERVERONLYZ999';")" "server-only code state"
assert_eq "777777" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:SERVERONLYZ999';")" "server-only base tombstone"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID IN (201,301) AND key='launch_24h_card';")" "founder/native no-stack"
assert_eq "2" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=900001 AND key='launch_24h_card';")" "claimed native preservation"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=900002 AND key='launch_24h_card';")" "linked non-founder receives universal launch marker"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT value FROM player_cache WHERE playerID=900003 AND key='launch_24h_card';")" "fresh native mint"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM invitems;")" "economy reset"
assert_eq "1" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM objects;")" "static world copy"
assert_eq "0" "$(sql_value "$MAIN_OUT" "SELECT COUNT(*) FROM player_cache WHERE length(key) > 32;")" "cache key bound"

# Delete the original founder character after the freeze and create a replacement.
# The global player-0 composite and base tag are lifetime tombstones; neither may move.
MUTATED_SOURCE="$TMP_DIR/mutated-source.db"
MUTATED_STORE="$TMP_DIR/mutated-store.json"
MUTATED_OUT="$TMP_DIR/mutated-launch.db"
MUTATED_REPORT="$TMP_DIR/mutated-report.json"
cp "$MAIN_OUT" "$MUTATED_SOURCE"
cp "$MAIN_STORE" "$MUTATED_STORE"
sqlite3 "$MUTATED_SOURCE" <<'SQL'
BEGIN IMMEDIATE;
DELETE FROM player_cache WHERE playerID=101;
DELETE FROM maxstats WHERE playerID=101;
DELETE FROM curstats WHERE playerID=101;
DELETE FROM experience WHERE playerID=101;
DELETE FROM capped_experience WHERE playerID=101;
DELETE FROM players WHERE id=101;
INSERT INTO players (id, username, group_id, email, pass, salt, creation_date, creation_ip)
	VALUES (990002, 'F001New', 10, 'founder001@example.test', 'hash2', 'salt2', 2, '127.0.0.2');
INSERT INTO maxstats (playerID) VALUES (990002);
INSERT INTO curstats (playerID) VALUES (990002);
INSERT INTO experience (playerID, hits) VALUES (990002, 4000);
INSERT INTO capped_experience (playerID) VALUES (990002);
INSERT INTO player_cache (playerID, type, key, value) VALUES (990002, 0, 'web_account_id', '1');
COMMIT;
SQL
node - "$MUTATED_STORE" <<'NODE'
const { readFileSync, writeFileSync } = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(readFileSync(path, "utf8"));
store.characters = store.characters.filter((character) => Number(character.playerId) !== 101);
store.characters.push({
	id: 999999,
	accountId: 1,
	playerId: 990002,
	name: "F001New",
	normalizedName: "f001new",
	linkStatus: "linked",
	source: "openrsc-sqlite-created"
});
writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`);
NODE

run_reset --source-db "$MUTATED_SOURCE" --portal-store "$MUTATED_STORE" --out-db "$MUTATED_OUT" --apply >"$MUTATED_REPORT"
node - "$MUTATED_REPORT" <<'NODE'
const { readFileSync } = require("node:fs");
const report = JSON.parse(readFileSync(process.argv[2], "utf8"));
if (report.starterCharacterMarkers !== 200) throw new Error("replacement changed freeze size");
if (report.nativeLaunchCardMarkers !== 4) throw new Error("replacement did not receive the universal launch marker");
if (!report.warnings.some((entry) => entry.reason === "postfreeze_character_not_added" && entry.playerId === 990002)) {
	throw new Error("replacement omission was not reported");
}
NODE
assert_eq "0" "$(sql_value "$MUTATED_OUT" "SELECT COUNT(*) FROM players WHERE id=101;")" "deleted player stays deleted"
assert_eq "2" "$(sql_value "$MUTATED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:101';")" "deleted founder tombstone"
assert_eq "101" "$(sql_value "$MUTATED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDF001X001';")" "deleted founder base binding"
assert_eq "0" "$(sql_value "$MUTATED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter:1:990002';")" "replacement receives no founder card"
assert_eq "1" "$(sql_value "$MUTATED_OUT" "SELECT value FROM player_cache WHERE playerID=990002 AND key='launch_24h_card';")" "replacement still receives universal launch marker"
assert_eq "1" "$(sql_value "$MUTATED_OUT" "SELECT value FROM player_cache WHERE playerID=990002 AND key='web_account_id';")" "replacement account link"

# A second reset over the post-freeze state must be byte-for-byte stable at the reward/link ledger level.
RERUN_OUT="$TMP_DIR/rerun-launch.db"
run_reset --source-db "$MUTATED_OUT" --portal-store "$MUTATED_STORE" --out-db "$RERUN_OUT" --apply >"$TMP_DIR/rerun-report.json"
diff -u \
	<(sqlite3 -noheader "$MUTATED_OUT" "SELECT playerID || '|' || type || '|' || key || '|' || value FROM player_cache ORDER BY playerID, key, value;") \
	<(sqlite3 -noheader "$RERUN_OUT" "SELECT playerID || '|' || type || '|' || key || '|' || value FROM player_cache ORDER BY playerID, key, value;") \
	|| fail "repeat reset changed reward/link ledger"

# Code-only founders bind once by unique username or remain explicitly unassigned.
UNLINKED_SOURCE="$TMP_DIR/unlinked-source.db"
UNLINKED_STORE="$TMP_DIR/unlinked-store.json"
UNLINKED_OUT="$TMP_DIR/unlinked-launch.db"
create_fixture_db "$UNLINKED_SOURCE"
sqlite3 "$UNLINKED_SOURCE" <<'SQL'
INSERT INTO players (id, username, group_id, email, pass, salt) VALUES (11, 'SoloName', 10, 'solo@example.test', 'hash', 'salt');
INSERT INTO players (id, username, group_id, email, pass, salt) VALUES (12, 'NativeOther', 10, 'other@example.test', 'hash', 'salt');
INSERT INTO players (id, username, group_id, email, pass, salt) VALUES (13, 'ClaimedFounder', 10, 'claimed@example.test', 'hash', 'salt');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:VOIDUNLINKA003', '2');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_tag:VOIDUNLINKA003', '13');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_acct:VOIDUNLINKA003', '0');
INSERT INTO item_provenance_events (actorID, source, command, extra)
	VALUES (13, 'subscription_signup_code', 'subscription_card_grant', 'grant=founder_base_code code_suffix=A003');
SQL
node - "$UNLINKED_STORE" <<'NODE'
const { writeFileSync } = require("node:fs");
writeFileSync(process.argv[2], `${JSON.stringify({
	accounts: [],
	characters: [],
	founders: [
		{ id: 1, username: "SoloName", normalizedName: "soloname", emailCanonical: "solo@example.test", signupCode: "VOIDUNLINKA001", signupCodeCreatedAt: "2026-07-18T17:00:00.000Z", createdAt: "2026-07-18T17:00:00.000Z" },
		{ id: 2, username: "MissingName", normalizedName: "missingname", emailCanonical: "missing@example.test", signupCode: "VOIDUNLINKA002", signupCodeCreatedAt: "2026-07-18T17:00:00.000Z", createdAt: "2026-07-18T17:00:00.000Z" },
		{ id: 3, username: "ClaimedFounder", normalizedName: "claimedfounder", emailCanonical: "claimed@example.test", signupCode: "VOIDUNLINKA003", signupCodeCreatedAt: "2026-07-18T17:00:00.000Z", createdAt: "2026-07-18T17:00:00.000Z" }
	]
}, null, 2)}\n`);
NODE
run_reset --source-db "$UNLINKED_SOURCE" --portal-store "$UNLINKED_STORE" --out-db "$UNLINKED_OUT" --apply >"$TMP_DIR/unlinked-report.json"
assert_eq "0" "$(sql_value "$UNLINKED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:*:*';")" "code-only founder composite count"
assert_eq "0" "$(sql_value "$UNLINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDUNLINKA001';")" "available code-only founder stays unbound"
assert_eq "0" "$(sql_value "$UNLINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDUNLINKA001';")" "code-only founder base account stays unlinked"
assert_eq "0" "$(sql_value "$UNLINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDUNLINKA002';")" "unassigned code-only founder"
assert_eq "2" "$(sql_value "$UNLINKED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID>0 AND key='launch_24h_card' AND value='1';")" "unlinked native launch program"
assert_eq "13" "$(sql_value "$UNLINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDUNLINKA003';")" "claimed code-only founder binding"
assert_eq "0" "$(sql_value "$UNLINKED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=13 AND key='launch_24h_card';")" "claimed code-only founder suppresses launch duplicate"

# A code-only founder reward may legitimately be claimed by a character that
# later belongs to a portal account. The durable base tag still owns that route,
# so an official-freeze rerun must preserve it without minting a launch duplicate.
UNLINKED_LINKED_STORE="$TMP_DIR/unlinked-linked-store.json"
UNLINKED_LINKED_OUT="$TMP_DIR/unlinked-linked-launch.db"
cp "$UNLINKED_STORE" "$UNLINKED_LINKED_STORE"
sqlite3 "$UNLINKED_OUT" "INSERT INTO player_cache (playerID, type, key, value) VALUES (13, 0, 'web_account_id', '77');"
node - "$UNLINKED_LINKED_STORE" <<'NODE'
const { readFileSync, writeFileSync } = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(readFileSync(path, "utf8"));
store.accounts.push({
	id: 77,
	emailCanonical: "linked-actor@example.test",
	status: "active",
	createdAt: "2026-07-18T18:30:00.000Z"
});
store.characters.push({
	id: 77,
	accountId: 77,
	playerId: 13,
	name: "ClaimedFounder",
	normalizedName: "claimedfounder",
	linkStatus: "linked"
});
writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`);
NODE
run_reset --source-db "$UNLINKED_OUT" --portal-store "$UNLINKED_LINKED_STORE" --out-db "$UNLINKED_LINKED_OUT" --apply >"$TMP_DIR/unlinked-linked-report.json"
assert_eq "13" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDUNLINKA003';")" "linked actor keeps code-only founder binding"
assert_eq "0" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDUNLINKA003';")" "linked actor keeps code-only founder classifier"
assert_eq "2" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDUNLINKA003';")" "linked actor keeps claimed founder code"
assert_eq "0" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter:77:13';")" "code-only founder is not promoted to a composite route"
assert_eq "0" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=13 AND key='launch_24h_card';")" "linked code-only founder actor still suppresses launch duplicate"
assert_eq "77" "$(sql_value "$UNLINKED_LINKED_OUT" "SELECT value FROM player_cache WHERE playerID=13 AND key='web_account_id';")" "linked code-only founder actor keeps portal ownership"

# A claimed legacy account marker with two frozen siblings and no exact actor must fail closed.
AMBIG_SOURCE="$TMP_DIR/ambiguous-source.db"
AMBIG_STORE="$TMP_DIR/ambiguous-store.json"
AMBIG_OUT="$TMP_DIR/ambiguous-launch.db"
create_fixture_db "$AMBIG_SOURCE"
sqlite3 "$AMBIG_SOURCE" <<'SQL'
INSERT INTO players (id, username, group_id, email, pass, salt) VALUES (101, 'Ambig', 10, 'ambig@example.test', 'hash', 'salt');
INSERT INTO players (id, username, group_id, email, pass, salt) VALUES (102, 'Sibling', 10, 'ambig@example.test', 'hash', 'salt');
INSERT INTO player_cache (playerID, type, key, value) VALUES (101, 0, 'web_account_id', '1');
INSERT INTO player_cache (playerID, type, key, value) VALUES (102, 0, 'web_account_id', '1');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:1', '2');
SQL
node - "$AMBIG_STORE" <<'NODE'
const { writeFileSync } = require("node:fs");
writeFileSync(process.argv[2], `${JSON.stringify({
	accounts: [{ id: 1, emailCanonical: "ambig@example.test", status: "active", createdAt: "2026-07-18T17:00:00.000Z" }],
	founders: [{ id: 1, username: "Ambig", normalizedName: "ambig", emailCanonical: "ambig@example.test", signupCode: "VOIDAMBIGA001", signupCodeCreatedAt: "2026-07-18T17:00:00.000Z", createdAt: "2026-07-18T17:00:00.000Z" }],
	entitlements: [{ id: 1, accountId: 1, type: "starter_free_subscription", status: "granted", createdAt: "2026-07-18T17:00:00.000Z" }],
	characters: [
		{ id: 1, accountId: 1, playerId: 101, name: "Ambig", normalizedName: "ambig", linkStatus: "linked" },
		{ id: 2, accountId: 1, playerId: 102, name: "Sibling", normalizedName: "sibling", linkStatus: "linked" }
	]
}, null, 2)}\n`);
NODE
if run_reset --source-db "$AMBIG_SOURCE" --portal-store "$AMBIG_STORE" >"$TMP_DIR/ambiguous-stdout" 2>"$TMP_DIR/ambiguous-stderr"; then
	fail "ambiguous claimed legacy marker unexpectedly passed dry-run"
fi
grep -q 'claimed_legacy_account_actor_ambiguous' "$TMP_DIR/ambiguous-stderr" \
	|| fail "ambiguous dry-run did not identify the claimed legacy actor"
if run_reset --source-db "$AMBIG_SOURCE" --portal-store "$AMBIG_STORE" --out-db "$AMBIG_OUT" --apply >"$TMP_DIR/ambiguous-apply-stdout" 2>"$TMP_DIR/ambiguous-apply-stderr"; then
	fail "ambiguous claimed legacy marker unexpectedly applied"
fi
[[ ! -e "$AMBIG_OUT" ]] || fail "ambiguous apply wrote an output DB"

# An admin revoke-all removes every available composite and leaves a portal
# suppression tombstone. A later reset must stop for review, never infer that
# an empty game manifest means it should recreate the revoked cards.
SUPPRESSED_SOURCE="$TMP_DIR/suppressed-source.db"
SUPPRESSED_STORE="$TMP_DIR/suppressed-store.json"
SUPPRESSED_OUT="$TMP_DIR/suppressed-launch.db"
cp "$AMBIG_SOURCE" "$SUPPRESSED_SOURCE"
cp "$AMBIG_STORE" "$SUPPRESSED_STORE"
sqlite3 "$SUPPRESSED_SOURCE" "UPDATE player_cache SET value='1' WHERE playerID=0 AND key='starter_card:1';"
node - "$SUPPRESSED_STORE" <<'NODE'
const { readFileSync, writeFileSync } = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(readFileSync(path, "utf8"));
store.accounts[0].starterCardLegacySuppressedAt = "2026-07-16T12:00:00.000Z";
writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`);
NODE
if run_reset --source-db "$SUPPRESSED_SOURCE" --portal-store "$SUPPRESSED_STORE" >"$TMP_DIR/suppressed-stdout" 2>"$TMP_DIR/suppressed-stderr"; then
	fail "admin-suppressed founder account unexpectedly passed dry-run"
fi
grep -q 'founder_account_admin_suppressed' "$TMP_DIR/suppressed-stderr" \
	|| fail "admin suppression tombstone was not identified"
if run_reset --source-db "$SUPPRESSED_SOURCE" --portal-store "$SUPPRESSED_STORE" --out-db "$SUPPRESSED_OUT" --apply >"$TMP_DIR/suppressed-apply-stdout" 2>"$TMP_DIR/suppressed-apply-stderr"; then
	fail "admin-suppressed founder account unexpectedly applied"
fi
[[ ! -e "$SUPPRESSED_OUT" ]] || fail "admin-suppressed apply wrote an output DB"

# Canonical marker spelling, referral classification, and legacy account IDs are
# release ledger boundaries; malformed variants must stop the reset.
NEGATIVE_SOURCE="$TMP_DIR/negative-source.db"
NEGATIVE_STORE="$TMP_DIR/negative-store.json"
cp "$AMBIG_SOURCE" "$NEGATIVE_SOURCE"
cp "$AMBIG_STORE" "$NEGATIVE_STORE"
sqlite3 "$NEGATIVE_SOURCE" <<'SQL'
UPDATE player_cache SET value='1' WHERE playerID=0 AND key='starter_card:1';
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter:01:0101', '1');
SQL
if run_reset --source-db "$NEGATIVE_SOURCE" --portal-store "$NEGATIVE_STORE" >"$TMP_DIR/noncanonical-stdout" 2>"$TMP_DIR/noncanonical-stderr"; then
	fail "noncanonical composite marker unexpectedly passed"
fi
grep -q 'noncanonical_starter_manifest_key' "$TMP_DIR/noncanonical-stderr" \
	|| fail "noncanonical composite marker was not identified"

sqlite3 "$NEGATIVE_SOURCE" <<'SQL'
DELETE FROM player_cache WHERE playerID=0 AND key='starter:01:0101';
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:VOIDREFNEGA001', '1');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_tag:VOIDREFNEGA001', '0');
SQL
node - "$NEGATIVE_STORE" <<'NODE'
const { readFileSync, writeFileSync } = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(readFileSync(path, "utf8"));
store.founders[0].referralRewardCodes = [{ code: "VOIDREFNEGA001", createdAt: "2026-07-18T17:00:00.000Z" }];
writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`);
NODE
if run_reset --source-db "$NEGATIVE_SOURCE" --portal-store "$NEGATIVE_STORE" >"$TMP_DIR/referral-tag-stdout" 2>"$TMP_DIR/referral-tag-stderr"; then
	fail "portal-known referral base tag unexpectedly passed"
fi
grep -q 'referral_code_has_base_tag' "$TMP_DIR/referral-tag-stderr" \
	|| fail "portal-known referral base tag was not identified"

sqlite3 "$NEGATIVE_SOURCE" <<'SQL'
DELETE FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDREFNEGA001';
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:notanid', '1');
SQL
if run_reset --source-db "$NEGATIVE_SOURCE" --portal-store "$NEGATIVE_STORE" >"$TMP_DIR/malformed-legacy-stdout" 2>"$TMP_DIR/malformed-legacy-stderr"; then
	fail "malformed legacy account marker unexpectedly passed"
fi
grep -q 'invalid_legacy_starter_key' "$TMP_DIR/malformed-legacy-stderr" \
	|| fail "malformed legacy account marker was not identified"

sqlite3 "$NEGATIVE_SOURCE" <<'SQL'
DELETE FROM player_cache WHERE playerID=0 AND key='starter_card:notanid';
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'starter_card:01', '2');
SQL
if run_reset --source-db "$NEGATIVE_SOURCE" --portal-store "$NEGATIVE_STORE" >"$TMP_DIR/noncanonical-legacy-stdout" 2>"$TMP_DIR/noncanonical-legacy-stderr"; then
	fail "claimed leading-zero legacy account marker unexpectedly passed"
fi
grep -q 'noncanonical_legacy_starter_key' "$TMP_DIR/noncanonical-legacy-stderr" \
	|| fail "claimed leading-zero legacy account marker was not identified"

# The official cutoff is a time-bounded cohort, not merely the set of rows that
# happen to exist when the reset runs. A partial pre-freeze composite must be
# completed on the first run; after the durable marker exists, no new identity
# may enter even if its portal timestamps are later backdated.
CUTOFF_SOURCE="$TMP_DIR/cutoff-source.db"
CUTOFF_STORE="$TMP_DIR/cutoff-store.json"
CUTOFF_OUT="$TMP_DIR/cutoff-launch.db"
CUTOFF_RERUN="$TMP_DIR/cutoff-rerun.db"
CUTOFF_EPOCH="$(node -e "process.stdout.write(String(Date.parse('$FREEZE_AT') / 1000))")"
BEFORE_EPOCH="$((CUTOFF_EPOCH - 1))"
AFTER_EPOCH="$((CUTOFF_EPOCH + 1))"
LAUNCH_BEFORE_EPOCH="$((CUTOFF_EPOCH + 86400 - 1))"
LAUNCH_CUTOFF_EPOCH="$((CUTOFF_EPOCH + 86400))"
LAUNCH_AFTER_EPOCH="$((CUTOFF_EPOCH + 86400 + 1))"
create_fixture_db "$CUTOFF_SOURCE"
sqlite3 "$CUTOFF_SOURCE" <<SQL
BEGIN IMMEDIATE;
INSERT INTO players (id, username, group_id, email, pass, salt, creation_date) VALUES
	(101, 'BeforeA', 10, 'one@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(102, 'BeforeB', 10, 'one@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(103, 'AfterChar', 10, 'one@example.test', 'hash', 'salt', $AFTER_EPOCH),
	(201, 'LateAccount', 10, 'two@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(301, 'LateFounder', 10, 'three@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(401, 'MissingGrant', 10, 'four@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(501, 'LateGrant', 10, 'five@example.test', 'hash', 'salt', $BEFORE_EPOCH),
	(701, 'ExactFounder', 10, 'seven@example.test', 'hash', 'salt', $CUTOFF_EPOCH),
	(801, 'LaunchBefore', 10, 'launch-before@example.test', 'hash', 'salt', $LAUNCH_BEFORE_EPOCH),
	(802, 'LaunchAt', 10, 'launch-at@example.test', 'hash', 'salt', $LAUNCH_CUTOFF_EPOCH),
	(803, 'LaunchAfter', 10, 'launch-after@example.test', 'hash', 'salt', $LAUNCH_AFTER_EPOCH);
INSERT INTO player_cache (playerID, type, key, value) VALUES
	(101, 0, 'web_account_id', '1'),
	(102, 0, 'web_account_id', '1'),
	(103, 0, 'web_account_id', '1'),
	(201, 0, 'web_account_id', '2'),
	(301, 0, 'web_account_id', '3'),
	(401, 0, 'web_account_id', '4'),
	(501, 0, 'web_account_id', '5'),
	(701, 0, 'web_account_id', '7'),
	(0, 0, 'starter:1:101', '2'),
	(0, 0, 'base_acct:VOIDCUTBASE1', '0');
COMMIT;
SQL
node - "$CUTOFF_STORE" <<'NODE'
const { writeFileSync } = require("node:fs");
const before = "2026-07-18T17:59:59.000Z";
const exact = "2026-07-18T18:00:00.000Z";
const after = "2026-07-18T18:00:01.000Z";
const accounts = [
	{ id: 1, emailCanonical: "one@example.test", status: "active", createdAt: before },
	{ id: 2, emailCanonical: "two@example.test", status: "active", createdAt: after },
	{ id: 3, emailCanonical: "three@example.test", status: "active", createdAt: before },
	{ id: 4, emailCanonical: "four@example.test", status: "active", createdAt: before },
	{ id: 5, emailCanonical: "five@example.test", status: "active", createdAt: before },
	{ id: 7, emailCanonical: "seven@example.test", status: "active", createdAt: exact }
];
const founder = (id, username, email, createdAt = before) => ({
	id,
	accountId: id,
	username,
	normalizedName: username.toLowerCase(),
	emailCanonical: email,
	signupCode: `VOIDCUTBASE${id}`,
	signupCodeCreatedAt: createdAt,
	createdAt
});
const founders = [
	{ ...founder(1, "BeforeA", "one@example.test"), referralRewardCodes: [
		{ code: "VOIDCUTEARLY1", createdAt: before },
		{ code: "VOIDCUTLATE01", createdAt: after }
	] },
	founder(2, "LateAccount", "two@example.test"),
	founder(3, "LateFounder", "three@example.test", after),
	founder(4, "MissingGrant", "four@example.test"),
	founder(5, "LateGrant", "five@example.test"),
	founder(7, "ExactFounder", "seven@example.test", exact)
];
const characters = [
	[1, 101, "BeforeA"], [1, 102, "BeforeB"], [1, 103, "AfterChar"],
	[2, 201, "LateAccount"], [3, 301, "LateFounder"],
	[4, 401, "MissingGrant"], [5, 501, "LateGrant"],
	[7, 701, "ExactFounder"]
].map(([accountId, playerId, name], index) => ({
	id: index + 1,
	accountId,
	playerId,
	name,
	normalizedName: name.toLowerCase(),
	linkStatus: "linked"
}));
const entitlements = [
	{ id: 1, accountId: 1, type: "starter_free_subscription", status: "granted", createdAt: before },
	{ id: 2, accountId: 2, type: "starter_free_subscription", status: "granted", createdAt: before },
	{ id: 3, accountId: 3, type: "starter_free_subscription", status: "granted", createdAt: before },
	{ id: 5, accountId: 5, type: "starter_free_subscription", status: "granted", createdAt: after },
	{ id: 7, accountId: 7, type: "starter_free_subscription", status: "granted", createdAt: exact }
];
writeFileSync(process.argv[2], `${JSON.stringify({ accounts, founders, characters, entitlements }, null, 2)}\n`);
NODE

run_reset --source-db "$CUTOFF_SOURCE" --portal-store "$CUTOFF_STORE" --out-db "$CUTOFF_OUT" --apply >"$TMP_DIR/cutoff-report.json"
assert_eq "$FREEZE_AT" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='founder_freeze_at';")" "durable founder cutoff"
assert_eq "4" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'founder_cohort:*';")" "precutoff founder cohort"
assert_eq "2" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:1:*';")" "partial manifest completed with eligible sibling"
assert_eq "2" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:101';")" "partial manifest claim retained"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='starter:1:102';")" "just-before-cutoff sibling included"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='starter:1:103';")" "postcutoff character excluded"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=103 AND key='launch_24h_card';")" "postcutoff founder exclusion still receives universal launch marker"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:2:*';")" "postcutoff account excluded"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:4:*';")" "missing qualification excluded"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:5:*';")" "late qualification excluded"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:7:*';")" "exact-cutoff founder excluded"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=701 AND key='launch_24h_card';")" "exact-launch character receives launch promotion"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDCUTBASE3';")" "postcutoff founder reward excluded"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDCUTBASE7';")" "exact-cutoff founder reward excluded"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDCUTEARLY1';")" "precutoff referral reward included"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDCUTLATE01';")" "postcutoff referral reward excluded"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDCUTBASE1';")" "linked cutoff founder classifier"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDCUTBASE2';")" "postcutoff account founder remains code-only"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='base_tag:VOIDCUTEARLY1';")" "referral has no player classifier"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='base_acct:VOIDCUTEARLY1';")" "referral has no account classifier"
assert_eq "1" "$(sql_value "$CUTOFF_OUT" "SELECT value FROM player_cache WHERE playerID=801 AND key='launch_24h_card';")" "just-before launch-card cutoff included"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=802 AND key='launch_24h_card';")" "exact launch-card cutoff excluded"
assert_eq "0" "$(sql_value "$CUTOFF_OUT" "SELECT COUNT(*) FROM player_cache WHERE playerID=803 AND key='launch_24h_card';")" "post launch-card cutoff excluded"

if "$RESET" --source-db "$CUTOFF_OUT" --portal-store "$CUTOFF_STORE" --freeze-at "2026-07-18T18:00:01Z" >"$TMP_DIR/cutoff-mismatch-stdout" 2>"$TMP_DIR/cutoff-mismatch-stderr"; then
	fail "official freeze accepted a different cutoff"
fi
grep -q 'founder_freeze_cutoff_mismatch' "$TMP_DIR/cutoff-mismatch-stderr" \
	|| fail "cutoff mismatch was not identified"

sqlite3 "$CUTOFF_OUT" <<SQL
INSERT INTO players (id, username, group_id, email, pass, salt, creation_date) VALUES (601, 'LateAddition', 10, 'six@example.test', 'hash', 'salt', $BEFORE_EPOCH);
INSERT INTO maxstats (playerID) VALUES (601);
INSERT INTO curstats (playerID) VALUES (601);
INSERT INTO experience (playerID, hits) VALUES (601, 4000);
INSERT INTO capped_experience (playerID) VALUES (601);
INSERT INTO player_cache (playerID, type, key, value) VALUES (601, 0, 'web_account_id', '6');
INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'signup_code:VOIDCUTBASE6', '1');
SQL
node - "$CUTOFF_STORE" <<'NODE'
const { readFileSync, writeFileSync } = require("node:fs");
const path = process.argv[2];
const store = JSON.parse(readFileSync(path, "utf8"));
const before = "2026-07-18T17:59:59.000Z";
store.accounts.push({ id: 6, emailCanonical: "six@example.test", status: "active", createdAt: before });
store.founders.push({ id: 6, accountId: 6, username: "LateAddition", normalizedName: "lateaddition", emailCanonical: "six@example.test", signupCode: "VOIDCUTBASE6", signupCodeCreatedAt: before, createdAt: before });
store.characters.push({ id: 6, accountId: 6, playerId: 601, name: "LateAddition", normalizedName: "lateaddition", linkStatus: "linked" });
store.entitlements.push({ id: 6, accountId: 6, type: "starter_free_subscription", status: "granted", createdAt: before });
writeFileSync(path, `${JSON.stringify(store, null, 2)}\n`);
NODE
run_reset --source-db "$CUTOFF_OUT" --portal-store "$CUTOFF_STORE" --out-db "$CUTOFF_RERUN" --apply >"$TMP_DIR/cutoff-rerun-report.json"
assert_eq "4" "$(sql_value "$CUTOFF_RERUN" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'founder_cohort:*';")" "rerun founder cohort remains frozen"
assert_eq "0" "$(sql_value "$CUTOFF_RERUN" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key GLOB 'starter:6:*';")" "rerun does not add founder account"
assert_eq "0" "$(sql_value "$CUTOFF_RERUN" "SELECT COUNT(*) FROM player_cache WHERE playerID=0 AND key='signup_code:VOIDCUTBASE6';")" "rerun does not add founder code"
assert_eq "1" "$(sql_value "$CUTOFF_RERUN" "SELECT value FROM player_cache WHERE playerID=601 AND key='launch_24h_card';")" "non-composite rerun player receives universal launch marker"

# Both base classifiers are immutable cohort metadata. A mismatched account or
# a referral classifier is contamination and must stop before writing output.
CLASSIFIER_BAD="$TMP_DIR/classifier-bad.db"
cp "$CUTOFF_RERUN" "$CLASSIFIER_BAD"
sqlite3 "$CLASSIFIER_BAD" "UPDATE player_cache SET value='2' WHERE playerID=0 AND key='base_acct:VOIDCUTBASE1';"
if run_reset --source-db "$CLASSIFIER_BAD" --portal-store "$CUTOFF_STORE" >"$TMP_DIR/classifier-mismatch-stdout" 2>"$TMP_DIR/classifier-mismatch-stderr"; then
	fail "mismatched founder base account classifier unexpectedly passed"
fi
grep -q 'founder_base_account_mismatch' "$TMP_DIR/classifier-mismatch-stderr" \
	|| fail "base account mismatch was not identified"

CLASSIFIER_REFERRAL="$TMP_DIR/classifier-referral.db"
cp "$CUTOFF_RERUN" "$CLASSIFIER_REFERRAL"
sqlite3 "$CLASSIFIER_REFERRAL" "INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 0, 'base_acct:VOIDCUTEARLY1', '1');"
if run_reset --source-db "$CLASSIFIER_REFERRAL" --portal-store "$CUTOFF_STORE" >"$TMP_DIR/classifier-referral-stdout" 2>"$TMP_DIR/classifier-referral-stderr"; then
	fail "referral base account contamination unexpectedly passed"
fi
grep -q 'referral_code_has_base_account' "$TMP_DIR/classifier-referral-stderr" \
	|| fail "referral base account contamination was not identified"

echo "reset-launch-game-db: synthetic founder freeze tests passed"
