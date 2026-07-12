#!/usr/bin/env node
import { execFile as execFileCallback } from "node:child_process";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

const execFile = promisify(execFileCallback);
const args = parseArgs(process.argv.slice(2));
const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const dbPath = args.db || process.env.PORTAL_OPENRSC_DB || process.env.OPENRSC_SQLITE_DB || "";
const outputPath = args.out
	|| process.env.PORTAL_INTEGRITY_SNAPSHOT
	|| (process.env.PORTAL_DATA_DIR ? join(process.env.PORTAL_DATA_DIR, "integrity-summary.json") : "");
const findingsPath = args.findings
	|| process.env.PORTAL_INTEGRITY_FINDINGS
	|| (outputPath ? join(dirname(resolve(outputPath)), "integrity-findings.json") : "");
const hours = nonNegativeInteger(args.hours || process.env.PORTAL_INTEGRITY_HOURS || 24, 24);
const limit = nonNegativeInteger(args.limit || process.env.PORTAL_INTEGRITY_LIMIT || 500, 500);
const maxLevel = positiveInteger(args.maxLevel || process.env.PORTAL_INTEGRITY_MAX_LEVEL || 99, 99);
const maxXp = positiveInteger(args.maxXp || process.env.PORTAL_INTEGRITY_MAX_XP || 200000000, 200000000);
const maxStack = positiveInteger(args.maxStack || process.env.PORTAL_INTEGRITY_MAX_STACK || 2147483647, 2147483647);
const quiet = truthy(args.quiet || process.env.PORTAL_INTEGRITY_QUIET || "");

if (!dbPath || !outputPath) {
	console.error("Usage: node scripts/export-integrity-summary.mjs --db /path/to/voidscape.db --out /path/to/integrity-summary.json");
	console.error("Env alternatives: PORTAL_OPENRSC_DB/OPENRSC_SQLITE_DB, PORTAL_INTEGRITY_SNAPSHOT, and optional PORTAL_INTEGRITY_FINDINGS.");
	process.exit(1);
}

const result = await buildSnapshot(resolve(dbPath));
await writeSnapshot(resolve(outputPath), result.snapshot);
if (findingsPath) {
	await writeSnapshot(resolve(findingsPath), result.findings);
}
const output = {
	ok: true,
	outputPath: resolve(outputPath),
	findingsPath: findingsPath ? resolve(findingsPath) : null,
	staffCommands: result.snapshot.staffCommands,
	itemProvenance: result.snapshot.itemProvenance,
	accountIntegrity: result.snapshot.accountIntegrity,
	economyScans: result.snapshot.economyScans
};
if (quiet) {
	console.log(JSON.stringify({
		ok: true,
		generatedAt: result.snapshot.generatedAt,
		staffCommands: result.snapshot.staffCommands.status,
		itemProvenance: result.snapshot.itemProvenance.status,
		accountIntegrity: result.snapshot.accountIntegrity.status,
		economyScans: result.snapshot.economyScans.status,
		flagged: (result.snapshot.economyScans.flagged || 0) + (result.snapshot.accountIntegrity.flagged || 0),
		outputPath: resolve(outputPath),
		findingsPath: findingsPath ? resolve(findingsPath) : null
	}));
} else {
	console.log(JSON.stringify(output, null, 2));
}

async function buildSnapshot(sqliteDbPath) {
	const staffCommands = await staffCommandSummary(sqliteDbPath);
	const itemProvenance = await itemProvenanceSummary(sqliteDbPath);
	const economy = await economyScanSummary(sqliteDbPath, itemProvenance);
	const accountIntegrity = await accountIntegritySummary(sqliteDbPath);
	return {
		snapshot: {
			generatedAt: now(),
			source: "snapshot",
			privacy: "Public integrity data excludes IP addresses, raw player identifiers, and full staff command arguments.",
			staffCommands,
			build: {
				status: "available",
				evidence: "Launcher manifests publish SHA-256 hashes for downloadable client artifacts."
			},
			itemProvenance,
			accountIntegrity: accountIntegrity.public,
			economyScans: economy.public
		},
		findings: combinedFindingsEnvelope(economy.private, accountIntegrity.private)
	};
}

async function accountIntegritySummary(sqliteDbPath) {
	const scanAt = now();
	const requiredTables = ["players", "player_cache", "staff_logs", "maxstats", "curstats", "experience"];
	const tableRows = await sqliteJson(sqliteDbPath, `SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (${requiredTables.map((table) => sqlString(table)).join(",")})`);
	const present = new Set(tableRows.map((row) => String(row.name || "")));
	const missing = requiredTables.filter((table) => !present.has(table));
	if (missing.length) {
		const publicSummary = {
			status: "unavailable",
			source: "sqlite-readonly",
			lastScanAt: scanAt,
			flagged: missing.length,
			review: 0,
			checkedPlayers: 0,
			highSeverity: missing.length,
			privilegedAccounts: 0,
			watchedCacheRows: 0,
			recentSensitiveCommands24h: 0,
			categories: [{ category: "missing_table", count: missing.length }]
		};
		return {
			public: publicSummary,
			private: privateFindingsEnvelope(scanAt, publicSummary, missing.map((table) => ({
				severity: "high",
				category: "missing_table",
				message: `Required account-integrity table is missing: ${table}`,
				table
			})))
		};
	}

	const findings = [];
	const reviewFindings = [];
	const playerCount = firstInteger(await sqliteJson(sqliteDbPath, "SELECT COUNT(*) AS value FROM players"));
	const allowedGroups = [0, 1, 2, 3, 5, 7, 8, 9, 10];
	const allowedGroupsSql = allowedGroups.join(",");

	await appendRows(sqliteDbPath, findings, "unknown_group", "high", `
		SELECT id, username, group_id
		FROM players
		WHERE group_id NOT IN (${allowedGroupsSql})
		ORDER BY group_id ASC, username ASC
		LIMIT 50
	`, (row) => ({
		message: `Player has unknown group_id ${row.group_id}.`,
		playerID: safeInteger(row.id),
		username: safeUsername(row.username),
		groupID: safeInteger(row.group_id)
	}));

	await appendRows(sqliteDbPath, reviewFindings, "privileged_account", "review", `
		SELECT id, username, group_id, login_date
		FROM players
		WHERE group_id IN (0,1,2,3,5,7,8)
		ORDER BY group_id ASC, username ASC
		LIMIT 100
	`, (row) => ({
		message: `${safeUsername(row.username)} has ${groupName(row.group_id)} privileges.`,
		playerID: safeInteger(row.id),
		username: safeUsername(row.username),
		groupID: safeInteger(row.group_id),
		groupName: groupName(row.group_id),
		lastLoginAt: Number(row.login_date || 0) > 0 ? unixSecondsToIso(row.login_date) : ""
	}));

	for (const table of ["maxstats", "curstats", "experience"]) {
		await appendRows(sqliteDbPath, findings, "missing_skill_row", "high", `
			SELECT p.id, p.username
			FROM players p
			LEFT JOIN ${table} s ON s.playerID = p.id
			WHERE s.playerID IS NULL
			ORDER BY p.id ASC
			LIMIT 50
		`, (row) => ({
			message: `Player is missing a ${table} row.`,
			playerID: safeInteger(row.id),
			username: safeUsername(row.username),
			table
		}));
	}

	await scanSkillTotal(sqliteDbPath, findings);
	await scanPlayerSettings(sqliteDbPath, findings);

	const cacheRows = await sqliteJson(sqliteDbPath, `
		SELECT c.playerID, p.username, c.type, c.key, c.value
		FROM player_cache c
		LEFT JOIN players p ON p.id = c.playerID
		WHERE c.key IN ('web_account_id', 'xp_lock_mask', 'player_title_active', 'player_title_unique_claim')
			OR c.key LIKE 'acct_sub:%'
			OR c.key LIKE 'char_sub:%'
			OR c.key LIKE 'starter_card:%'
			OR c.key LIKE 'signup_code:%'
			OR c.key LIKE 'player_title_unlocked_%'
		ORDER BY c.playerID ASC, c.key ASC
		LIMIT 1000
	`);
	const playerIDs = new Set((await sqliteJson(sqliteDbPath, "SELECT id FROM players")).map((row) => safeInteger(row.id)));
	scanAccountCacheRows(findings, cacheRows, playerIDs);

	const sensitiveCommands = await sensitiveStaffCommands(sqliteDbPath);
	for (const row of sensitiveCommands.rows.slice(0, 25)) {
		reviewFindings.push({
			severity: "review",
			category: "recent_sensitive_staff_command",
			message: `Recent sensitive staff command: ${row.command || "unknown"} (${row.category || "staff"}).`,
			username: safeUsername(row.affected_player),
			staffUsername: safeUsername(row.staff_username),
			command: sanitizeLabel(row.command, "unknown"),
			commandCategory: sanitizeLabel(row.category, "staff"),
			at: unixSecondsToIso(row.time)
		});
	}

	const reviewFromFindings = findings.filter((finding) => finding.severity === "review");
	const hardFindings = findings.filter((finding) => finding.severity !== "review");
	const allReviewFindings = reviewFromFindings.concat(reviewFindings);
	const allFindings = hardFindings.concat(allReviewFindings);
	const categoryMap = new Map();
	const reviewCategoryMap = new Map();
	const severityMap = new Map();
	for (const finding of hardFindings) {
		categoryMap.set(finding.category, (categoryMap.get(finding.category) || 0) + 1);
		severityMap.set(finding.severity, (severityMap.get(finding.severity) || 0) + 1);
	}
	for (const finding of allReviewFindings) {
		reviewCategoryMap.set(finding.category, (reviewCategoryMap.get(finding.category) || 0) + 1);
	}
	const publicSummary = {
		status: hardFindings.length > 0 ? "flagged" : "clean",
		source: "sqlite-readonly",
		lastScanAt: scanAt,
		flagged: hardFindings.length,
		review: allReviewFindings.length,
		checkedPlayers: playerCount,
		highSeverity: safeInteger(severityMap.get("high")),
		privilegedAccounts: allReviewFindings.filter((finding) => finding.category === "privileged_account").length,
		watchedCacheRows: cacheRows.length,
		recentSensitiveCommands24h: sensitiveCommands.count,
		categories: categoryCounts(categoryMap),
		reviewCategories: categoryCounts(reviewCategoryMap),
		privacy: "Public account summary hides player names, staff names, cache keys, and raw command details."
	};

	return {
		public: publicSummary,
		private: privateFindingsEnvelope(scanAt, publicSummary, allFindings)
	};
}

async function economyScanSummary(sqliteDbPath, itemProvenance) {
	const scanAt = now();
	const requiredTables = ["players", "curstats", "maxstats", "experience", "itemstatuses", "invitems", "bank", "equipped"];
	const tableRows = await sqliteJson(sqliteDbPath, `SELECT name FROM sqlite_master WHERE type = 'table' AND name IN (${requiredTables.map((table) => sqlString(table)).join(",")})`);
	const present = new Set(tableRows.map((row) => String(row.name || "")));
	const missing = requiredTables.filter((table) => !present.has(table));
	if (missing.length) {
		const publicSummary = {
			status: "unavailable",
			source: "sqlite-readonly",
			lastScanAt: scanAt,
			flagged: missing.length,
			fixed: 0,
			trackedItems: 0,
			checkedPlayers: 0,
			highSeverity: missing.length,
			categories: [{ category: "missing_table", count: missing.length }]
		};
		return {
			public: publicSummary,
			private: privateFindingsEnvelope(scanAt, publicSummary, missing.map((table) => ({
				severity: "high",
				category: "missing_table",
				message: `Required table is missing: ${table}`,
				table
			})))
		};
	}

	const catalog = await loadItemCatalog();
	const findings = [];
	const itemCount = firstInteger(await sqliteJson(sqliteDbPath, "SELECT COUNT(*) AS value FROM itemstatuses"));
	const playerCount = firstInteger(await sqliteJson(sqliteDbPath, "SELECT COUNT(*) AS value FROM players"));
	const staffMintAmount24h = safeInteger(itemProvenance && itemProvenance.amount24h);
	const staffMints24h = safeInteger(itemProvenance && itemProvenance.staffMints24h);

	await appendRows(sqliteDbPath, findings, "invalid_item_amount", "high", `
		SELECT itemID, catalogID, amount, noted
		FROM itemstatuses
		WHERE amount < 1 OR amount > ${maxStack}
		ORDER BY amount DESC, itemID ASC
		LIMIT 50
	`, (row) => ({
		message: `Item status ${row.itemID} has invalid amount ${row.amount}.`,
		itemID: safeInteger(row.itemID),
		catalogID: safeInteger(row.catalogID),
		itemName: catalog.nameFor(row.catalogID),
		amount: safeInteger(row.amount),
		noted: Number(row.noted || 0) === 1
	}));

	if (catalog.maxId >= 0) {
		await appendRows(sqliteDbPath, findings, "invalid_item_catalog", "high", `
			SELECT itemID, catalogID, amount
			FROM itemstatuses
			WHERE catalogID < 0 OR catalogID > ${catalog.maxId}
			ORDER BY catalogID DESC, itemID ASC
			LIMIT 50
		`, (row) => ({
			message: `Item status ${row.itemID} references unknown item catalog id ${row.catalogID}.`,
			itemID: safeInteger(row.itemID),
			catalogID: safeInteger(row.catalogID),
			amount: safeInteger(row.amount)
		}));
	}

	for (const location of [
		{ table: "invitems", label: "inventory", slot: true },
		{ table: "bank", label: "bank", slot: true },
		{ table: "equipped", label: "equipment", slot: false }
	]) {
		await appendRows(sqliteDbPath, findings, "broken_item_reference", "high", `
			SELECT ${sqlString(location.label)} AS location, r.playerID, p.username, r.itemID${location.slot ? ", r.slot" : ", NULL AS slot"}
			FROM ${location.table} r
			LEFT JOIN players p ON p.id = r.playerID
			LEFT JOIN itemstatuses s ON s.itemID = r.itemID
			WHERE p.id IS NULL OR s.itemID IS NULL
			ORDER BY r.playerID ASC, r.itemID ASC
			LIMIT 50
		`, (row) => ({
			message: `${titleCase(location.label)} row references a missing player or item status.`,
			location: location.label,
			playerID: safeInteger(row.playerID),
			username: safeUsername(row.username),
			itemID: safeInteger(row.itemID),
			slot: row.slot === null || row.slot === undefined ? null : safeInteger(row.slot)
		}));
	}

	await appendRows(sqliteDbPath, findings, "duplicate_item_owner", "high", `
		WITH refs AS (
			SELECT itemID, playerID, 'inventory' AS location FROM invitems
			UNION ALL SELECT itemID, playerID, 'bank' AS location FROM bank
			UNION ALL SELECT itemID, playerID, 'equipment' AS location FROM equipped
		)
		SELECT itemID, COUNT(*) AS ref_count, COUNT(DISTINCT playerID) AS owner_count, GROUP_CONCAT(location || ':' || playerID, ',') AS refs
		FROM refs
		GROUP BY itemID
		HAVING ref_count > 1 OR owner_count > 1
		ORDER BY ref_count DESC, itemID ASC
		LIMIT 50
	`, (row) => ({
		message: `Item status ${row.itemID} is referenced by ${row.ref_count} owner rows.`,
		itemID: safeInteger(row.itemID),
		referenceCount: safeInteger(row.ref_count),
		ownerCount: safeInteger(row.owner_count),
		refs: String(row.refs || "").slice(0, 240)
	}));

	await appendRows(sqliteDbPath, findings, "orphan_item_status", "medium", `
		WITH refs AS (
			SELECT itemID FROM invitems
			UNION SELECT itemID FROM bank
			UNION SELECT itemID FROM equipped
		)
		SELECT s.itemID, s.catalogID, s.amount, s.noted
		FROM itemstatuses s
		LEFT JOIN refs r ON r.itemID = s.itemID
		WHERE r.itemID IS NULL
		ORDER BY s.itemID ASC
		LIMIT 50
	`, (row) => ({
		message: `Item status ${row.itemID} is not referenced by inventory, bank, or equipment.`,
		itemID: safeInteger(row.itemID),
		catalogID: safeInteger(row.catalogID),
		itemName: catalog.nameFor(row.catalogID),
		amount: safeInteger(row.amount),
		noted: Number(row.noted || 0) === 1
	}));

	await appendRows(sqliteDbPath, findings, "invalid_inventory_slot", "medium", `
		SELECT i.playerID, p.username, i.itemID, i.slot
		FROM invitems i
		LEFT JOIN players p ON p.id = i.playerID
		WHERE i.slot < 0 OR i.slot > 29
		ORDER BY i.playerID ASC, i.slot ASC
		LIMIT 50
	`, (row) => ({
		message: `Inventory slot ${row.slot} is outside the 0-29 range.`,
		playerID: safeInteger(row.playerID),
		username: safeUsername(row.username),
		itemID: safeInteger(row.itemID),
		slot: safeInteger(row.slot)
	}));

	await appendRows(sqliteDbPath, findings, "invalid_bank_slot", "medium", `
		SELECT b.playerID, p.username, b.itemID, b.slot
		FROM bank b
		LEFT JOIN players p ON p.id = b.playerID
		WHERE b.slot < 0 OR b.slot > 2000
		ORDER BY b.playerID ASC, b.slot ASC
		LIMIT 50
	`, (row) => ({
		message: `Bank slot ${row.slot} is outside the safe storage range.`,
		playerID: safeInteger(row.playerID),
		username: safeUsername(row.username),
		itemID: safeInteger(row.itemID),
		slot: safeInteger(row.slot)
	}));

	await scanSkillTable(sqliteDbPath, findings, "maxstats", "invalid_max_stat", maxLevel, "high");
	await scanSkillTable(sqliteDbPath, findings, "curstats", "invalid_current_stat", maxLevel + 20, "medium", 0);
	await scanExperience(sqliteDbPath, findings);

	const categoryMap = new Map();
	const severityMap = new Map();
	for (const finding of findings) {
		categoryMap.set(finding.category, (categoryMap.get(finding.category) || 0) + 1);
		severityMap.set(finding.severity, (severityMap.get(finding.severity) || 0) + 1);
	}
	const highSeverity = safeInteger(severityMap.get("high"));
	const publicSummary = {
		status: findings.length > 0 ? "flagged" : "clean",
		source: "sqlite-readonly",
		lastScanAt: scanAt,
		flagged: findings.length,
		fixed: 0,
		trackedItems: itemCount,
		checkedPlayers: playerCount,
		highSeverity,
		staffMints24h,
		staffMintAmount24h,
		categories: categoryCounts(categoryMap),
		privacy: "Public scan summary hides player names, item instance ids, IPs, and exact finding details."
	};

	return {
		public: publicSummary,
		private: privateFindingsEnvelope(scanAt, publicSummary, findings)
	};
}

async function appendRows(sqliteDbPath, findings, category, severity, query, toFinding) {
	const rows = await sqliteJson(sqliteDbPath, query);
	for (const row of rows) {
		findings.push({
			severity,
			category,
			...toFinding(row)
		});
	}
}

async function scanSkillTable(sqliteDbPath, findings, table, category, maxAllowed, severity, minAllowed = 1) {
	const skillColumns = await columnsFor(sqliteDbPath, table, ["playerID"]);
	if (!skillColumns.length) return;
	const where = skillColumns
		.map((column) => `${column} < ${minAllowed} OR ${column} > ${maxAllowed}`)
		.join(" OR ");
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT t.playerID, p.username, ${skillColumns.join(", ")}
		FROM ${table} t
		LEFT JOIN players p ON p.id = t.playerID
		WHERE ${where}
		ORDER BY t.playerID ASC
		LIMIT 50
	`);
	for (const row of rows) {
		const bad = skillColumns
			.filter((column) => Number(row[column]) < minAllowed || Number(row[column]) > maxAllowed)
			.map((column) => `${column}=${row[column]}`)
			.slice(0, 8);
		findings.push({
			severity,
			category,
			message: `${table} has out-of-range skill values: ${bad.join(", ")}.`,
			playerID: safeInteger(row.playerID),
			username: safeUsername(row.username),
			values: bad
		});
	}
}

async function scanExperience(sqliteDbPath, findings) {
	const skillColumns = await columnsFor(sqliteDbPath, "experience", ["playerID"]);
	if (!skillColumns.length) return;
	const where = skillColumns
		.map((column) => `${column} < 0 OR ${column} > ${maxXp}`)
		.join(" OR ");
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT e.playerID, p.username, ${skillColumns.join(", ")}
		FROM experience e
		LEFT JOIN players p ON p.id = e.playerID
		WHERE ${where}
		ORDER BY e.playerID ASC
		LIMIT 50
	`);
	for (const row of rows) {
		const bad = skillColumns
			.filter((column) => Number(row[column]) < 0 || Number(row[column]) > maxXp)
			.map((column) => `${column}=${row[column]}`)
			.slice(0, 8);
		findings.push({
			severity: "high",
			category: "invalid_experience",
			message: `Experience row has out-of-range values: ${bad.join(", ")}.`,
			playerID: safeInteger(row.playerID),
			username: safeUsername(row.username),
			values: bad
		});
	}
}

async function scanSkillTotal(sqliteDbPath, findings) {
	const skillColumns = (await columnsFor(sqliteDbPath, "maxstats", ["playerID"]))
		.filter((column) => !["harvesting", "runecraft"].includes(column));
	if (!skillColumns.length) return;
	const totalExpression = skillColumns.map((column) => `COALESCE(m.${column}, 0)`).join(" + ");
	await appendRows(sqliteDbPath, findings, "skill_total_mismatch", "medium", `
		SELECT p.id, p.username, p.skill_total, (${totalExpression}) AS computed_total
		FROM players p
		JOIN maxstats m ON m.playerID = p.id
		WHERE p.skill_total != (${totalExpression})
		ORDER BY ABS(p.skill_total - (${totalExpression})) DESC, p.id ASC
		LIMIT 50
	`, (row) => ({
		message: `Stored skill_total ${row.skill_total} does not match maxstats total ${row.computed_total}.`,
		playerID: safeInteger(row.id),
		username: safeUsername(row.username),
		storedTotal: safeInteger(row.skill_total),
		computedTotal: safeInteger(row.computed_total)
	}));
}

async function scanPlayerSettings(sqliteDbPath, findings) {
	const existingColumns = new Set(await columnsFor(sqliteDbPath, "players", []));
	const booleanColumns = ["online", "male", "block_chat", "block_private", "block_trade", "block_duel", "cameraauto", "onemouse", "soundoff"]
		.filter((column) => existingColumns.has(column));
	if (!booleanColumns.length) return;
	const allowedValues = (column) => column.indexOf("block_") === 0 ? [0, 1, 2] : [0, 1];
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT id, username, ${booleanColumns.join(", ")}
		FROM players
		WHERE ${booleanColumns.map((column) => `${column} NOT IN (${allowedValues(column).join(", ")})`).join(" OR ")}
		ORDER BY id ASC
		LIMIT 50
	`);
	for (const row of rows) {
		const bad = booleanColumns
			.filter((column) => !allowedValues(column).includes(Number(row[column])))
			.map((column) => `${column}=${row[column]}`)
			.slice(0, 8);
		findings.push({
			severity: "medium",
			category: "invalid_account_flag",
			message: `Player row has invalid boolean flags: ${bad.join(", ")}.`,
			playerID: safeInteger(row.id),
			username: safeUsername(row.username),
			values: bad
		});
	}
}

function scanAccountCacheRows(findings, rows, playerIDs) {
	const nowMillis = Date.now();
	const maxSubscriptionMillis = nowMillis + 370 * 24 * 60 * 60 * 1000;
	for (const row of rows) {
		const playerID = Number(row.playerID);
		const key = String(row.key || "");
		const value = String(row.value || "");
		if (playerID > 0 && !playerIDs.has(playerID)) {
			findings.push(cacheFinding("orphan_account_cache", "medium", row, `Player cache key ${key} references missing player ${playerID}.`));
			continue;
		}
		if (key === "web_account_id" && !isPositiveIntegerString(value)) {
			findings.push(cacheFinding("invalid_web_account_link", "high", row, `web_account_id is not a positive integer.`));
		} else if (key === "xp_lock_mask" && !isNonNegativeIntegerString(value)) {
			findings.push(cacheFinding("invalid_xp_lock_mask", "medium", row, `xp_lock_mask is not a non-negative integer.`));
		} else if (key.startsWith("acct_sub:")) {
			if (playerID !== -1) {
				findings.push(cacheFinding("subscription_cache_scope", "medium", row, `Account subscription cache row is not global.`));
			}
			if (!isPositiveIntegerString(key.slice("acct_sub:".length))) {
				findings.push(cacheFinding("invalid_subscription_key", "high", row, `Account subscription key has an invalid account id.`));
			}
			validateSubscriptionExpires(findings, row, value, nowMillis, maxSubscriptionMillis);
		} else if (key.startsWith("char_sub:")) {
			const suffix = key.slice("char_sub:".length);
			if (playerID !== -1) {
				findings.push(cacheFinding("subscription_cache_scope", "medium", row, `Character subscription cache row is not global.`));
			}
			if (!isPositiveIntegerString(suffix) || !playerIDs.has(Number(suffix))) {
				findings.push(cacheFinding("invalid_subscription_key", "high", row, `Character subscription key points at a missing player.`));
			}
			validateSubscriptionExpires(findings, row, value, nowMillis, maxSubscriptionMillis);
		} else if (key.startsWith("starter_card:") && !["1", "2"].includes(value)) {
			findings.push(cacheFinding("invalid_reward_state", "medium", row, `Starter-card reward state is not available/claimed.`));
		} else if (key.startsWith("signup_code:") && !["1", "2"].includes(value)) {
			findings.push(cacheFinding("invalid_reward_state", "medium", row, `Signup-code reward state is not available/redeemed.`));
		}
	}
}

function validateSubscriptionExpires(findings, row, value, nowMillis, maxSubscriptionMillis) {
	if (!isPositiveIntegerString(value)) {
		findings.push(cacheFinding("invalid_subscription_expiry", "high", row, `Subscription expiry is not a positive timestamp.`));
		return;
	}
	const expiresAt = Number(value);
	if (expiresAt > maxSubscriptionMillis) {
		findings.push(cacheFinding("long_subscription_expiry", "medium", row, `Subscription expiry is more than 370 days out.`));
	} else if (expiresAt < nowMillis - 30 * 24 * 60 * 60 * 1000) {
		findings.push(cacheFinding("stale_subscription_expiry", "review", row, `Expired subscription cache row is older than 30 days.`));
	}
}

function cacheFinding(category, severity, row, message) {
	return {
		severity,
		category,
		message,
		playerID: safeInteger(row.playerID),
		username: safeUsername(row.username),
		cacheKey: sanitizeCacheKey(row.key),
		cacheType: safeInteger(row.type)
	};
}

async function sensitiveStaffCommands(sqliteDbPath) {
	const since = Math.floor(Date.now() / 1000) - hours * 60 * 60;
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT staff_username, affected_player, time, extra
		FROM staff_logs
		WHERE action = 24
			AND extra LIKE 'integrity %'
			AND time >= ${since}
		ORDER BY time DESC, id DESC
		LIMIT ${limit}
	`);
	const sensitive = [];
	for (const row of rows) {
		const fields = parseAuditExtra(row.extra);
		const command = sanitizeLabel(fields.command, "");
		const category = sanitizeLabel(fields.category, "staff");
		if (isSensitiveCommand(command, category)) {
			sensitive.push({
				...row,
				command,
				category
			});
		}
	}
	return {
		count: sensitive.length,
		rows: sensitive
	};
}

function isSensitiveCommand(command, category) {
	if (category === "account" || category === "item") return true;
	return new Set([
		"setgroup", "group", "setrank", "rank", "setcache", "scache", "storecache",
		"deletecache", "dcache", "removecache", "rcache", "setquest", "queststage",
		"setqueststage", "resetquest", "resetq", "questcomplete", "questcom",
		"completeallquests", "stat", "stats", "setstat", "setstats", "xpstat",
		"xpstats", "setxpstat", "setxpstats", "setxp", "item", "bankitem",
		"bitem", "addbank", "fillbank", "ritem", "rbitem"
	]).has(command);
}

async function columnsFor(sqliteDbPath, table, excluded) {
	const rows = await sqliteJson(sqliteDbPath, `PRAGMA table_info(${table})`);
	const excludedSet = new Set(excluded);
	return rows
		.map((row) => String(row.name || ""))
		.filter((column) => column && !excludedSet.has(column) && /^[a-z_]+$/i.test(column));
}

function privateFindingsEnvelope(scanAt, publicSummary, findings) {
	return {
		generatedAt: scanAt,
		source: "sqlite-readonly",
		summary: publicSummary,
		findings: findings.slice(0, 500)
	};
}

function combinedFindingsEnvelope(economy, account) {
	const scanAt = now();
	const economyFindings = economy && Array.isArray(economy.findings) ? economy.findings : [];
	const accountFindings = account && Array.isArray(account.findings) ? account.findings : [];
	const allFindings = economyFindings.concat(accountFindings);
	const hardFindings = allFindings.filter((finding) => finding.severity !== "review");
	const highSeverity = hardFindings.filter((finding) => finding.severity === "high").length;
	const categoryMap = new Map();
	for (const finding of hardFindings) {
		categoryMap.set(finding.category, (categoryMap.get(finding.category) || 0) + 1);
	}
	const economySummary = economy && economy.summary ? economy.summary : {};
	const accountSummary = account && account.summary ? account.summary : {};
	const summary = {
		status: hardFindings.length > 0 ? "flagged" : "clean",
		source: "sqlite-readonly",
		lastScanAt: scanAt,
		flagged: hardFindings.length,
		review: allFindings.length - hardFindings.length,
		fixed: 0,
		trackedItems: safeInteger(economySummary.trackedItems),
		checkedPlayers: Math.max(safeInteger(economySummary.checkedPlayers), safeInteger(accountSummary.checkedPlayers)),
		highSeverity,
		economyFlagged: safeInteger(economySummary.flagged),
		accountFlagged: safeInteger(accountSummary.flagged),
		privilegedAccounts: safeInteger(accountSummary.privilegedAccounts),
		watchedCacheRows: safeInteger(accountSummary.watchedCacheRows),
		recentSensitiveCommands24h: safeInteger(accountSummary.recentSensitiveCommands24h),
		categories: categoryCounts(categoryMap)
	};
	return {
		generatedAt: scanAt,
		source: "sqlite-readonly",
		summary,
		sections: {
			economyScans: economySummary,
			accountIntegrity: accountSummary
		},
		findings: allFindings.slice(0, 500)
	};
}

async function loadItemCatalog() {
	const names = new Map();
	const files = [
		"server/conf/server/defs/ItemDefs.json",
		"server/conf/server/defs/ItemDefsCustom.json",
		"server/conf/server/defs/ItemDefsPatch18.json"
	];
	let maxId = -1;
	for (const file of files) {
		try {
			const payload = JSON.parse(await readFile(resolve(repoRoot, file), "utf8"));
			const rows = Array.isArray(payload) ? payload : Object.values(payload || {});
			for (const row of rows) {
				const id = Number(row.id);
				if (!Number.isInteger(id) || id < 0) continue;
				maxId = Math.max(maxId, id);
				if (row.name) names.set(id, String(row.name).slice(0, 80));
			}
		} catch (error) {
			// Missing optional definition files should not block a DB scan.
		}
	};
	return {
		maxId,
		nameFor(value) {
			const id = Number(value);
			return names.get(id) || "";
		}
	};
}

async function itemProvenanceSummary(sqliteDbPath) {
	const tableRows = await sqliteJson(sqliteDbPath, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'item_provenance_events'");
	if (!tableRows.length) {
		return emptyItemProvenanceSummary("not_recording", "missing_item_provenance_table");
	}
	const since24 = Math.floor(Date.now() / 1000) - hours * 60 * 60;
	const since7d = Math.floor(Date.now() / 1000) - 7 * 24 * 60 * 60;
	const totals = await sqliteJson(sqliteDbPath, `
		SELECT
			COUNT(*) AS total_events,
			SUM(CASE WHEN time >= ${since24} THEN 1 ELSE 0 END) AS total_24h,
			SUM(CASE WHEN time >= ${since7d} THEN 1 ELSE 0 END) AS total_7d,
				SUM(CASE WHEN event_type = 'staff_mint' AND time >= ${since24} THEN 1 ELSE 0 END) AS staff_mints_24h,
				SUM(CASE WHEN event_type = 'staff_mint' AND time >= ${since7d} THEN 1 ELSE 0 END) AS staff_mints_7d,
				SUM(CASE WHEN event_type = 'item_origin' AND time >= ${since24} THEN 1 ELSE 0 END) AS origins_24h,
				SUM(CASE WHEN event_type = 'item_origin' AND time >= ${since7d} THEN 1 ELSE 0 END) AS origins_7d,
				SUM(CASE WHEN event_type = 'item_origin' AND source = 'npc_drop' AND time >= ${since24} THEN 1 ELSE 0 END) AS npc_drops_24h,
				SUM(CASE WHEN event_type = 'item_origin' AND source = 'npc_drop' AND time >= ${since7d} THEN 1 ELSE 0 END) AS npc_drops_7d,
				SUM(CASE WHEN event_type = 'item_origin' AND time >= ${since24} THEN amount ELSE 0 END) AS origin_amount_24h,
				SUM(CASE WHEN event_type = 'item_transfer' AND time >= ${since24} THEN 1 ELSE 0 END) AS transfers_24h,
				SUM(CASE WHEN event_type = 'item_transfer' AND time >= ${since7d} THEN 1 ELSE 0 END) AS transfers_7d,
				SUM(CASE WHEN event_type = 'staff_mint' AND time >= ${since24} THEN amount ELSE 0 END) AS amount_24h,
				COUNT(DISTINCT CASE WHEN event_type = 'staff_mint' AND time >= ${since24} THEN catalogID END) AS catalogs_24h
			FROM item_provenance_events
	`);
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT time, event_type, source, destination, command, catalogID, amount, noted
		FROM item_provenance_events
		WHERE time >= ${since7d}
		ORDER BY time DESC, eventID DESC
		LIMIT ${limit}
	`);
	return itemProvenanceSummaryFromRows(rows, totals[0] || {});
}

function itemProvenanceSummaryFromRows(rows, totals) {
	const destinations = new Map();
	const sources = new Map();
	const recent = [];
	for (const row of rows) {
		const eventType = sanitizeLabel(row.event_type, "event");
		const source = sanitizeLabel(row.source, "unknown");
		const destination = sanitizeLabel(row.destination, "unknown");
		sources.set(source, (sources.get(source) || 0) + 1);
		destinations.set(destination, (destinations.get(destination) || 0) + 1);
		if (recent.length < 8) {
			recent.push({
				at: unixSecondsToIso(row.time),
				eventType,
				source,
				destination,
				command: sanitizeLabel(row.command, "staff"),
				catalogId: safeInteger(row.catalogID),
				amount: safeInteger(row.amount),
				noted: Number(row.noted || 0) === 1
			});
		}
	}
	const totalEvents = safeInteger(totals.total_events);
	const staffMints24h = safeInteger(totals.staff_mints_24h);
	let status = "recording_no_staff_mints";
	if (staffMints24h > 0) status = "recording";
	else if (totalEvents > 0) status = "recording_no_recent_staff_mints";
	return {
		status,
		source: "item_provenance_events",
		trackedItems: totalEvents,
		totalEvents,
		total24h: safeInteger(totals.total_24h),
			total7d: safeInteger(totals.total_7d),
			staffMints24h,
			staffMints7d: safeInteger(totals.staff_mints_7d),
			origins24h: safeInteger(totals.origins_24h),
			origins7d: safeInteger(totals.origins_7d),
			npcDrops24h: safeInteger(totals.npc_drops_24h),
			npcDrops7d: safeInteger(totals.npc_drops_7d),
			originAmount24h: safeInteger(totals.origin_amount_24h),
			transfers24h: safeInteger(totals.transfers_24h),
			transfers7d: safeInteger(totals.transfers_7d),
			amount24h: safeInteger(totals.amount_24h),
			catalogs24h: safeInteger(totals.catalogs_24h),
			sources: categoryCounts(sources),
			destinations: categoryCounts(destinations),
			recent
		};
}

function emptyItemProvenanceSummary(status, reason) {
	return {
		status,
		source: "item_provenance_events",
		reason,
		trackedItems: 0,
		totalEvents: 0,
		total24h: 0,
		total7d: 0,
		staffMints24h: 0,
		staffMints7d: 0,
		origins24h: 0,
		origins7d: 0,
		npcDrops24h: 0,
		npcDrops7d: 0,
		originAmount24h: 0,
		transfers24h: 0,
		transfers7d: 0,
		amount24h: 0,
		catalogs24h: 0,
		sources: [],
		destinations: [],
		recent: []
	};
}

async function staffCommandSummary(sqliteDbPath) {
	const tableRows = await sqliteJson(sqliteDbPath, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'staff_logs'");
	if (!tableRows.length) {
		return emptyStaffCommandSummary("missing_staff_log_table");
	}
	const since = Math.floor(Date.now() / 1000) - hours * 60 * 60;
	const rows = await sqliteJson(sqliteDbPath, `
		SELECT time, extra
		FROM staff_logs
		WHERE action = 24
			AND extra LIKE 'integrity %'
			AND time >= ${since}
		ORDER BY time DESC
		LIMIT ${limit}
	`);
	return staffCommandSummaryFromRows(rows);
}

function staffCommandSummaryFromRows(rows) {
	const counts = new Map();
	const recent = [];
	let allowed24h = 0;
	let blocked24h = 0;
	for (const row of rows) {
		const fields = parseAuditExtra(row.extra);
		const category = sanitizeLabel(fields.category, "staff");
		const status = fields.status === "blocked" ? "blocked" : "allowed";
		if (status === "blocked") blocked24h += 1;
		else allowed24h += 1;
		counts.set(category, (counts.get(category) || 0) + 1);
		if (recent.length < 8) {
			recent.push({
				at: unixSecondsToIso(row.time),
				status,
				category
			});
		}
	}
	const total24h = allowed24h + blocked24h;
	return {
		status: total24h > 0 ? "active" : "no_recent_staff_commands",
		source: "staff_logs",
		total24h,
		allowed24h,
		blocked24h,
		categories: categoryCounts(counts),
		recent
	};
}

function emptyStaffCommandSummary(status) {
	return {
		status,
		source: "staff_logs",
		total24h: 0,
		allowed24h: 0,
		blocked24h: 0,
		categories: [],
		recent: []
	};
}

function parseAuditExtra(extra) {
	const fields = {};
	String(extra || "").split(/\s+/).forEach((part) => {
		const separator = part.indexOf("=");
		if (separator <= 0) return;
		fields[part.slice(0, separator)] = part.slice(separator + 1);
	});
	return fields;
}

function categoryCounts(counts) {
	return Array.from(counts.entries())
		.map(([category, count]) => ({ category, count }))
		.sort((a, b) => b.count - a.count || a.category.localeCompare(b.category))
		.slice(0, 12);
}

async function sqliteJson(sqliteDbPath, query) {
	const { stdout } = await execFile("sqlite3", ["-readonly", "-json", sqliteDbPath, query], {
		maxBuffer: 1024 * 1024
	});
	return stdout.trim() ? JSON.parse(stdout) : [];
}

async function writeSnapshot(path, snapshot) {
	await mkdir(dirname(path), { recursive: true });
	const tmpPath = `${path}.${process.pid}.tmp`;
	await writeFile(tmpPath, `${JSON.stringify(snapshot, null, 2)}\n`);
	await rename(tmpPath, path);
}

function parseArgs(argv) {
	const parsed = {};
	for (let index = 0; index < argv.length; index += 1) {
		const arg = argv[index];
		if (!arg.startsWith("--")) continue;
		const key = arg.slice(2);
		const next = argv[index + 1];
		if (!next || next.startsWith("--")) {
			parsed[key] = "1";
		} else {
			parsed[key] = next;
			index += 1;
		}
	}
	return parsed;
}

function sanitizeLabel(value, fallback) {
	const label = String(value || fallback || "unknown")
		.toLowerCase()
		.replace(/[^a-z0-9_-]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.slice(0, 32);
	return label || fallback || "unknown";
}

function nonNegativeInteger(value, fallback) {
	const number = Number(value);
	return Number.isInteger(number) && number >= 0 ? number : fallback;
}

function positiveInteger(value, fallback) {
	const number = Number(value);
	return Number.isInteger(number) && number > 0 ? number : fallback;
}

function truthy(value) {
	return /^(1|true|yes|on)$/i.test(String(value || ""));
}

function safeInteger(value) {
	const number = Number(value);
	return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
}

function firstInteger(rows) {
	return Array.isArray(rows) && rows.length ? safeInteger(rows[0].value) : 0;
}

function safeUsername(value) {
	return String(value || "")
		.replace(/[^A-Za-z0-9 _-]+/g, "")
		.slice(0, 12);
}

function sanitizeCacheKey(value) {
	return String(value || "")
		.replace(/[^A-Za-z0-9:_-]+/g, "")
		.slice(0, 40);
}

function isPositiveIntegerString(value) {
	return /^[1-9][0-9]*$/.test(String(value || ""));
}

function isNonNegativeIntegerString(value) {
	return /^(0|[1-9][0-9]*)$/.test(String(value || ""));
}

function groupName(value) {
	switch (Number(value)) {
		case 0: return "Owner";
		case 1: return "Admin";
		case 2: return "Super Moderator";
		case 3: return "Moderator";
		case 5: return "Developer";
		case 7: return "Event";
		case 8: return "Player Moderator";
		case 9: return "Tester";
		case 10: return "User";
		default: return "Unknown";
	}
}

function sqlString(value) {
	return `'${String(value).replace(/'/g, "''")}'`;
}

function titleCase(value) {
	return String(value || "")
		.replace(/[_-]+/g, " ")
		.replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function unixSecondsToIso(value) {
	const seconds = Number(value);
	if (!Number.isFinite(seconds) || seconds <= 0) return now();
	return new Date(seconds * 1000).toISOString();
}

function now() {
	return new Date().toISOString();
}
