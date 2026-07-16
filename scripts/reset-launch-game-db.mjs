#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, resolve } from "node:path";

const repoRoot = resolve(dirname(new URL(import.meta.url).pathname), "..");
const args = parseArgs(process.argv.slice(2));

if (args.help) {
	usage(0);
}

const sourceDb = args["source-db"] ? resolve(String(args["source-db"])) : "";
const portalStorePath = args["portal-store"] ? resolve(String(args["portal-store"])) : "";
const outDb = args["out-db"] ? resolve(String(args["out-db"])) : "";
const apply = Boolean(args.apply);
const force = Boolean(args.force);

if (!sourceDb || !portalStorePath) {
	usage(1, "--source-db and --portal-store are required");
}
if (!existsSync(sourceDb)) {
	fail(`source DB not found: ${sourceDb}`);
}
if (!existsSync(portalStorePath)) {
	fail(`portal store not found: ${portalStorePath}`);
}
if (apply && !outDb) {
	usage(1, "--out-db is required with --apply");
}
if (apply && existsSync(outDb) && !force) {
	fail(`output DB already exists: ${outDb} (pass --force to replace it)`);
}

const sourceTables = tableSet(sourceDb);
const players = sqliteJson(sourceDb, `
	SELECT id, username, group_id, email, pass, salt, creation_date, creation_ip
	FROM players
	ORDER BY id
`);
if (!players.length) {
	fail("source DB has no players to preserve");
}

const sourceWebLinks = sourceTables.has("player_cache")
	? sqliteJson(sourceDb, `
		SELECT playerID, value
		FROM player_cache
		WHERE playerID > 0 AND key = 'web_account_id'
	`)
	: [];
const sourceSignupKeys = sourceTables.has("player_cache")
	? sqliteJson(sourceDb, `
		SELECT DISTINCT key
		FROM player_cache
		WHERE playerID = 0 AND key LIKE 'signup_code:%'
	`)
	: [];

const portalStore = JSON.parse(readFileSync(portalStorePath, "utf8"));
const plan = buildPlan({
	players,
	sourceWebLinks,
	sourceSignupKeys,
	store: portalStore
});

const dryRunReport = {
	apply,
	sourceDb,
	portalStore: portalStorePath,
	outDb: apply ? outDb : null,
	playersPreserved: players.length,
	portalAccounts: plan.portalAccountIds.size,
	portalCharactersLinked: plan.portalCharacterLinks,
	webAccountLinks: plan.webLinks.size,
	starterCardMarkers: plan.starterAccountIds.size,
	nativeLaunchCardMarkers: plan.nativeLaunchPlayerIds.length,
	signupCodeMarkers: plan.signupCodeKeys.size,
	missingLinkedCharacters: plan.missingLinkedCharacters,
	linkConflicts: plan.linkConflicts,
	staticTablesCopied: ["db_patches", "npclocs", "objects"].filter((name) => sourceTables.has(name))
};

if (plan.missingLinkedCharacters.length || plan.linkConflicts.length) {
	console.error(JSON.stringify(dryRunReport, null, 2));
	fail("portal/game link validation failed; no reset DB was written");
}

if (!apply) {
	console.log(JSON.stringify(dryRunReport, null, 2));
	process.exit(0);
}

const tempDir = mkdtempSync(`${tmpdir()}/voidscape-launch-reset.`);
try {
	if (existsSync(outDb) && force) {
		rmSync(outDb);
	}
	const schema = execFileSync("sqlite3", [sourceDb, ".schema --nosys"], { encoding: "utf8" });
	sqliteExec(outDb, schema);
	sqliteExec(outDb, buildApplySql(sourceDb, sourceTables, plan));
	const outputSummary = summarizeOutput(outDb);
	console.log(JSON.stringify({
		...dryRunReport,
		integrityCheck: outputSummary.integrityCheck,
		output: outputSummary
	}, null, 2));
} finally {
	rmSync(tempDir, { recursive: true, force: true });
}

function buildPlan({ players, sourceWebLinks, sourceSignupKeys, store }) {
	const playerIds = new Set(players.map((row) => Number(row.id)));
	const playersByName = new Map(players.map((row) => [normalizeUsername(row.username), Number(row.id)]));
	const accounts = Array.isArray(store.accounts) ? store.accounts : [];
	const characters = Array.isArray(store.characters) ? store.characters : [];
	const founders = Array.isArray(store.founders) ? store.founders : [];
	const portalAccountIds = new Set(accounts
		.filter((account) => {
			const status = String(account && account.status || "active").toLowerCase();
			return Number.isInteger(Number(account && account.id)) && status !== "deleted";
		})
		.map((account) => Number(account.id)));
	const webLinks = new Map();
	const linkConflicts = [];
	const missingLinkedCharacters = [];

	function addWebLink(playerId, accountId, source) {
		const pid = Number(playerId);
		const aid = Number(accountId);
		if (!Number.isInteger(pid) || pid <= 0 || !Number.isInteger(aid) || aid <= 0) return;
		if (!playerIds.has(pid)) {
			missingLinkedCharacters.push({ source, playerId: pid, accountId: aid });
			return;
		}
		const previous = webLinks.get(pid);
		if (previous && previous !== aid) {
			linkConflicts.push({ playerId: pid, previousAccountId: previous, accountId: aid, source });
			return;
		}
		webLinks.set(pid, aid);
	}

	for (const row of sourceWebLinks) {
		addWebLink(row.playerID, row.value, "source_player_cache");
	}

	let portalCharacterLinks = 0;
	for (const character of characters) {
		const accountId = Number(character && character.accountId);
		if (!Number.isInteger(accountId) || accountId <= 0) continue;
		let playerId = Number(character.playerId);
		if (!Number.isInteger(playerId) || playerId <= 0 || !playerIds.has(playerId)) {
			playerId = playersByName.get(normalizeUsername(character.normalizedName || character.name || "")) || 0;
		}
		if (!playerId) {
			missingLinkedCharacters.push({
				source: "portal_store",
				characterId: character.id || null,
				accountId,
				name: character.name || character.normalizedName || "",
				playerId: character.playerId || null
			});
			continue;
		}
		addWebLink(playerId, accountId, "portal_store");
		portalCharacterLinks += 1;
	}

	const starterAccountIds = new Set(portalAccountIds);
	for (const accountId of webLinks.values()) {
		if (Number.isInteger(accountId) && accountId > 0) starterAccountIds.add(accountId);
	}

	const signupCodeKeys = new Set();
	for (const row of sourceSignupKeys) {
		const key = String(row.key || "").trim();
		if (key.startsWith("signup_code:") && key.length <= 32) signupCodeKeys.add(key);
	}
	for (const founder of founders) {
		addSignupCode(signupCodeKeys, founder && (founder.signupCodeNormalized || founder.signupCode));
		for (const code of founder && Array.isArray(founder.referralRewardCodes) ? founder.referralRewardCodes : []) {
			addSignupCode(signupCodeKeys, code);
		}
		for (const code of founder && Array.isArray(founder.rewardCodes) ? founder.rewardCodes : []) {
			addSignupCode(signupCodeKeys, code);
		}
	}

	const nativeLaunchPlayerIds = players
		.map((row) => Number(row.id))
		.filter((playerId) => Number.isInteger(playerId) && playerId > 0 && !webLinks.has(playerId));

	return {
		portalAccountIds,
		portalCharacterLinks,
		webLinks,
		starterAccountIds,
		signupCodeKeys,
		nativeLaunchPlayerIds,
		missingLinkedCharacters,
		linkConflicts
	};
}

function buildApplySql(sourceDb, sourceTables, plan) {
	const sql = [];
	sql.push("PRAGMA foreign_keys = OFF;");
	sql.push("BEGIN IMMEDIATE;");
	sql.push(`ATTACH DATABASE ${quote(sourceDb)} AS src;`);

	for (const table of ["db_patches", "npclocs", "objects"]) {
		if (sourceTables.has(table)) {
			sql.push(`INSERT INTO ${ident(table)} SELECT * FROM src.${ident(table)};`);
		}
	}

	const playerColumns = tableInfo(sourceDb, "players").map((column) => column.name);
	const keepColumns = [
		"id", "username", "group_id", "email", "pass", "salt",
		"creation_date", "creation_ip", "banned", "offences", "muted"
	].filter((column) => playerColumns.includes(column));
	const selectExpressions = keepColumns.map((column) => {
		if (column === "group_id") {
			return "CASE WHEN group_id > 0 AND group_id < 10 THEN group_id ELSE 10 END";
		}
		if (column === "banned" || column === "muted") {
			return `COALESCE(${ident(column)}, '0')`;
		}
		if (column === "offences") {
			return `COALESCE(${ident(column)}, 0)`;
		}
		return ident(column);
	});
	sql.push(`INSERT INTO players (${keepColumns.map(ident).join(", ")}) SELECT ${selectExpressions.join(", ")} FROM src.players ORDER BY id;`);
	sql.push("INSERT INTO maxstats (playerID) SELECT id FROM players ORDER BY id;");
	sql.push("INSERT INTO curstats (playerID) SELECT id FROM players ORDER BY id;");
	sql.push("INSERT INTO experience (playerID, hits) SELECT id, 4000 FROM players ORDER BY id;");
	sql.push("INSERT INTO capped_experience (playerID) SELECT id FROM players ORDER BY id;");

	for (const [playerId, accountId] of Array.from(plan.webLinks.entries()).sort((a, b) => a[0] - b[0])) {
		sql.push(cacheInsert(playerId, 0, "web_account_id", String(accountId)));
	}
	for (const accountId of Array.from(plan.starterAccountIds).sort((a, b) => a - b)) {
		sql.push(cacheInsert(0, 0, `starter_card:${accountId}`, "1"));
	}
	for (const key of Array.from(plan.signupCodeKeys).sort()) {
		sql.push(cacheInsert(0, 0, key, "1"));
	}
	for (const playerId of plan.nativeLaunchPlayerIds.sort((a, b) => a - b)) {
		sql.push(cacheInsert(playerId, 0, "launch_24h_card", "1"));
	}

	sql.push("COMMIT;");
	sql.push("DETACH DATABASE src;");
	sql.push("VACUUM;");
	return sql.join("\n");
}

function summarizeOutput(db) {
	const integrityRows = sqliteJson(db, "PRAGMA integrity_check;");
	const integrityCheck = integrityRows
		.map((row) => row.integrity_check || row.integrity_check_ || Object.values(row)[0])
		.join(", ");
	const scalar = (sql) => {
		const rows = sqliteJson(db, sql);
		return Number(rows[0] && rows[0].value || 0);
	};
	const maybeCount = (table) => tableSet(db).has(table) ? scalar(`SELECT COUNT(*) AS value FROM ${ident(table)}`) : null;
	const economyTables = [
		"invitems", "equipped", "bank", "bankpresets", "itemstatuses",
		"auctions", "auction_sales", "expired_auctions", "quests", "npckills",
		"bestiaryloot", "grounditems", "droplogs", "trade_logs", "item_provenance_events"
	];
	const economyCounts = {};
	for (const table of economyTables) {
		const count = maybeCount(table);
		if (count !== null) economyCounts[table] = count;
	}
	return {
		path: db,
		players: maybeCount("players"),
		playerCache: maybeCount("player_cache"),
		webAccountLinks: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID > 0 AND key = 'web_account_id'"),
		starterCards: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE 'starter_card:%' AND value = '1'"),
		nativeLaunchCards: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID > 0 AND key = 'launch_24h_card' AND value = '1'"),
		signupCodes: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE 'signup_code:%' AND value = '1'"),
		npclocs: maybeCount("npclocs"),
		objects: maybeCount("objects"),
		dbPatches: maybeCount("db_patches"),
		economyCounts,
		integrityCheck
	};
}

function addSignupCode(keys, raw) {
	const normalized = normalizeSignupCode(raw);
	if (!normalized) return;
	const key = `signup_code:${normalized}`;
	if (key.length <= 32) keys.add(key);
}

function normalizeSignupCode(raw) {
	const normalized = String(raw || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
	return normalized && normalized.length <= 20 ? normalized : "";
}

function normalizeUsername(raw) {
	return String(raw || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function cacheInsert(playerId, type, key, value) {
	return `INSERT INTO player_cache (playerID, type, key, value) VALUES (${Number(playerId)}, ${Number(type)}, ${quote(key)}, ${quote(value)});`;
}

function sqliteJson(db, sql) {
	const output = sqliteRaw(db, sql);
	return output.trim() ? JSON.parse(output) : [];
}

function sqliteRaw(db, sql) {
	return execFileSync("sqlite3", ["-json", db, sql], { encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
}

function sqliteExec(db, sql) {
	execFileSync("sqlite3", [db], { input: sql, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
}

function tableSet(db) {
	return new Set(sqliteJson(db, "SELECT name FROM sqlite_master WHERE type = 'table'").map((row) => row.name));
}

function tableInfo(db, table) {
	return sqliteJson(db, `PRAGMA table_info(${ident(table)})`);
}

function ident(value) {
	return `"${String(value).replace(/"/g, "\"\"")}"`;
}

function quote(value) {
	if (value === null || value === undefined) return "NULL";
	return `'${String(value).replace(/'/g, "''")}'`;
}

function parseArgs(argv) {
	const result = {};
	for (let i = 0; i < argv.length; i += 1) {
		const arg = argv[i];
		if (arg === "--help" || arg === "-h") {
			result.help = true;
		} else if (arg === "--apply" || arg === "--force") {
			result[arg.slice(2)] = true;
		} else if (arg.startsWith("--")) {
			const key = arg.slice(2);
			const value = argv[i + 1];
			if (!value || value.startsWith("--")) usage(1, `${arg} requires a value`);
			result[key] = value;
			i += 1;
		} else {
			usage(1, `unknown argument: ${arg}`);
		}
	}
	return result;
}

function usage(code, message = "") {
	if (message) console.error(message);
	console.error(`Usage:
  scripts/reset-launch-game-db.mjs --source-db DB --portal-store dev-store.json [--out-db DB --apply] [--force]

Dry-run:
  scripts/reset-launch-game-db.mjs --source-db /opt/voidscape/server/inc/sqlite/voidscape.db --portal-store /var/lib/voidscape-portal/dev-store.json

Apply to a new DB:
  scripts/reset-launch-game-db.mjs --source-db live.db --portal-store dev-store.json --out-db launch.db --apply`);
	process.exit(code);
}

function fail(message) {
	console.error(`${basename(process.argv[1])}: ${message}`);
	process.exit(1);
}
