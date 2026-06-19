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
	economyScans: result.snapshot.economyScans
};
if (quiet) {
	console.log(JSON.stringify({
		ok: true,
		generatedAt: result.snapshot.generatedAt,
		staffCommands: result.snapshot.staffCommands.status,
		itemProvenance: result.snapshot.itemProvenance.status,
		economyScans: result.snapshot.economyScans.status,
		flagged: result.snapshot.economyScans.flagged || 0,
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
			economyScans: economy.public
		},
		findings: economy.private
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
