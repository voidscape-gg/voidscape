#!/usr/bin/env node
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, realpathSync, rmSync, statSync } from "node:fs";
import { resolve } from "node:path";

const args = parseArgs(process.argv.slice(2));
const javaIntMax = 2147483647;
const starterAvailable = 1;
const starterClaimed = 2;
const launchCardKey = "launch_24h_card";
const webAccountKey = "web_account_id";
const signupCodePrefix = "signup_code:";
const baseTagPrefix = "base_tag:";
const baseAccountPrefix = "base_acct:";
const founderFreezeKey = "founder_freeze_at";
const founderCohortPrefix = "founder_cohort:";
const launchCardWindowMs = 24 * 60 * 60 * 1000;

if (args.help) {
	usage(0);
}

const sourceDb = args["source-db"] ? resolve(String(args["source-db"])) : "";
const portalStorePath = args["portal-store"] ? resolve(String(args["portal-store"])) : "";
const outDb = args["out-db"] ? resolve(String(args["out-db"])) : "";
const apply = Boolean(args.apply);
const force = Boolean(args.force);
let freezeAt = "";
if (args["freeze-at"]) {
	try {
		freezeAt = parseFreezeAt(args["freeze-at"]);
	} catch (_error) {
		usage(1, "--freeze-at must be an ISO-8601 timestamp with an explicit timezone");
	}
}
const freezeAtMs = Date.parse(freezeAt);
const launchCardCutoffMs = freezeAtMs + launchCardWindowMs;
const launchCardCutoff = Number.isFinite(launchCardCutoffMs)
	? new Date(launchCardCutoffMs).toISOString()
	: "";

if (!sourceDb || !portalStorePath || !args["freeze-at"]) {
	usage(1, "--source-db, --portal-store, and --freeze-at are required");
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
if (apply && sameExistingFile(sourceDb, outDb)) {
	fail("--out-db must be a separate file from --source-db; the source DB is never overwritten");
}
if (apply && existsSync(outDb) && !force) {
	fail(`output DB already exists: ${outDb} (pass --force to replace it)`);
}

const sourceTables = tableSet(sourceDb);
for (const requiredTable of ["players", "player_cache", "maxstats", "curstats", "experience", "capped_experience"]) {
	if (!sourceTables.has(requiredTable)) fail(`source DB is missing required table: ${requiredTable}`);
}

const players = sqliteJson(sourceDb, `
	SELECT id, username, group_id, email, pass, salt, creation_date, creation_ip
	FROM players
	ORDER BY id
`);
if (!players.length) {
	fail("source DB has no players to preserve");
}

const sourceCacheRows = sqliteJson(sourceDb, `
	SELECT playerID, key, value, dbid
	FROM player_cache
	WHERE key = '${webAccountKey}'
	   OR key = '${launchCardKey}'
	   OR (playerID = 0 AND (
		key LIKE 'starter_card:%'
		OR key LIKE 'starter:%'
		OR key LIKE '${signupCodePrefix}%'
		OR key LIKE '${baseTagPrefix}%'
		OR key LIKE '${baseAccountPrefix}%'
		OR key = '${founderFreezeKey}'
		OR key LIKE '${founderCohortPrefix}%'
	   ))
	ORDER BY dbid
`);
const provenanceRows = loadRewardProvenance(sourceDb, sourceTables);

let portalStore;
try {
	portalStore = JSON.parse(readFileSync(portalStorePath, "utf8"));
} catch (error) {
	fail(`portal store is not valid JSON: ${error && error.message || error}`);
}

const plan = buildPlan({
	players,
	sourceCacheRows,
	provenanceRows,
	store: portalStore,
	freezeAt,
	freezeAtMs,
	launchCardCutoff,
	launchCardCutoffMs
});
const dryRunReport = buildReport(plan);

if (plan.errors.length || plan.ambiguities.length) {
	console.error(JSON.stringify(dryRunReport, null, 2));
	fail("portal/game founder reconciliation failed; no reset DB was written");
}

if (!apply) {
	console.log(JSON.stringify(dryRunReport, null, 2));
	process.exit(0);
}

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

function buildPlan({ players, sourceCacheRows, provenanceRows, store, freezeAt, freezeAtMs,
	launchCardCutoff, launchCardCutoffMs }) {
	const errors = [];
	const ambiguities = [];
	const warnings = [];
	const playersById = new Map();
	for (const row of players) {
		const playerId = positiveJavaInt(row.id);
		const normalizedName = normalizeUsername(row.username);
		if (!playerId || !normalizedName) {
			errors.push({ reason: "invalid_player_row", playerId: Number(row.id) || null });
			continue;
		}
		if (playersById.has(playerId)) {
			errors.push({ reason: "duplicate_player_id", playerId });
			continue;
		}
		playersById.set(playerId, row);
	}

	const cache = indexSourceCache(sourceCacheRows, errors);
	const hasOfficialFreeze = Boolean(cache.founderFreezeRow);
	if (cache.founderFreezeRow) {
		const storedFreezeAt = parseStoredFreezeAt(cache.founderFreezeRow.value, errors);
		if (storedFreezeAt && storedFreezeAt !== freezeAt) {
			errors.push({ reason: "founder_freeze_cutoff_mismatch", expected: storedFreezeAt, requested: freezeAt });
		}
	} else if (cache.founderCohortRows.size) {
		errors.push({ reason: "founder_cohort_without_freeze_marker", cohortMarkers: cache.founderCohortRows.size });
	}
	const storedCohort = hasOfficialFreeze
		? parseFounderCohort(cache.founderCohortRows, errors)
		: new Map();
	const accounts = Array.isArray(store && store.accounts) ? store.accounts : [];
	const characters = Array.isArray(store && store.characters) ? store.characters : [];
	const founders = Array.isArray(store && store.founders) ? store.founders : [];
	const entitlements = Array.isArray(store && store.entitlements) ? store.entitlements : [];
	const allAccountsById = new Map();
	const accountsById = new Map();
	const accountsByEmail = new Map();
	for (const account of accounts) {
		const accountId = positiveJavaInt(account && account.id);
		if (!accountId) {
			errors.push({ reason: "invalid_portal_account_id" });
			continue;
		}
		if (allAccountsById.has(accountId)) {
			errors.push({ reason: "duplicate_portal_account_id", accountId });
			continue;
		}
		allAccountsById.set(accountId, account);
		if (String(account.status || "active").toLowerCase() === "deleted") continue;
		accountsById.set(accountId, account);
		const email = canonicalStoredEmail(account && (account.emailCanonical || account.emailDisplay));
		if (email) {
			const matches = accountsByEmail.get(email) || [];
			matches.push(account);
			accountsByEmail.set(email, matches);
		}
	}
	const qualifyingAccountIds = new Set();
	for (const account of accountsById.values()) {
		const accountId = positiveJavaInt(account.id);
		const active = String(account.status || "").toLowerCase() === "active";
		const accountCreatedInTime = timestampBefore(account.createdAt, freezeAtMs);
		const grantedInTime = entitlements.some((entry) =>
			positiveJavaInt(entry && entry.accountId) === accountId
			&& (entry.type === "starter_free_subscription" || entry.type === "starter_subscription_card")
			&& entry.status === "granted"
			&& timestampBefore(entry.createdAt, freezeAtMs));
		if (active && accountCreatedInTime && grantedInTime) qualifyingAccountIds.add(accountId);
	}

	const webLinks = buildWebLinks(cache.webLinkRows, playersById, accountsById, errors);
	const linkedCharacters = buildLinkedCharacters({
		characters,
		playersById,
		accountsById,
		webLinks,
		errors
	});
	const portalLinkedPlayerIds = new Set(linkedCharacters.map((character) => character.playerId));
	for (const [playerId, accountId] of webLinks) {
		if (!portalLinkedPlayerIds.has(playerId)) {
			errors.push({ reason: "source_web_link_missing_portal_character", playerId, accountId });
		}
	}
	const linkedByAccount = new Map();
	for (const character of linkedCharacters) {
		const rows = linkedByAccount.get(character.accountId) || [];
		rows.push(character);
		linkedByAccount.set(character.accountId, rows);
	}

	const codeOwners = new Map();
	const allPortalCodes = new Set();
	for (const founder of founders) {
		const baseCode = normalizeSignupCode(founder && founder.signupCode);
		if (baseCode) allPortalCodes.add(baseCode);
		for (const record of founderReferralCodeRecords(founder)) {
			const code = normalizeSignupCode(record.code);
			if (code) allPortalCodes.add(code);
		}
	}
	const founderIds = new Set();
	const founderEmails = new Map();
	const founderNames = new Map();
	const founderAccounts = new Map();
	const founderRecords = [];
	const rawFounders = hasOfficialFreeze
		? Array.from(storedCohort.values()).map((entry) => ({
			...entry,
			accountId: entry.accountId || null,
			officialCohort: true
		}))
		: founders.filter((founder) => {
			if (timestampBefore(founder && founder.createdAt, freezeAtMs)) return true;
			warnings.push({ reason: "founder_created_after_cutoff_or_missing", founderId: positiveJavaInt(founder && founder.id) || null });
			return false;
		});
	for (const rawFounder of rawFounders) {
		const founderId = positiveJavaInt(rawFounder && rawFounder.id);
		const email = canonicalStoredEmail(rawFounder && (rawFounder.emailCanonical || rawFounder.emailDisplay));
		const normalizedName = normalizeUsername(rawFounder && (rawFounder.normalizedName || rawFounder.username));
		if (!founderId || !email || !normalizedName) {
			errors.push({ reason: "invalid_founder_identity", founderId: founderId || null });
			continue;
		}
		if (founderIds.has(founderId)) {
			errors.push({ reason: "duplicate_founder_id", founderId });
			continue;
		}
		founderIds.add(founderId);
		registerFounderIdentity(founderEmails, email, founderId, "duplicate_founder_email", errors);
		registerFounderIdentity(founderNames, normalizedName, founderId, "duplicate_founder_name", errors);

		const explicitAccountIdRaw = rawFounder && rawFounder.accountId;
		const hasExplicitAccountId = explicitAccountIdRaw !== undefined
			&& explicitAccountIdRaw !== null
			&& String(explicitAccountIdRaw).trim() !== "";
		const explicitAccountId = hasExplicitAccountId ? positiveJavaInt(explicitAccountIdRaw) : 0;
		let account = null;
		if (hasExplicitAccountId) {
			if (!explicitAccountId || !accountsById.has(explicitAccountId)) {
				errors.push({ reason: "founder_explicit_account_missing", founderId, accountId: explicitAccountId || null });
			} else {
				account = accountsById.get(explicitAccountId);
				const accountEmail = canonicalStoredEmail(account.emailCanonical || account.emailDisplay);
				if (accountEmail !== email) {
					errors.push({ reason: "founder_explicit_account_email_conflict", founderId, accountId: explicitAccountId });
				}
			}
		} else if (!rawFounder.officialCohort) {
			const matches = accountsByEmail.get(email) || [];
			if (matches.length > 1) {
				errors.push({ reason: "founder_account_email_ambiguous", founderId, matches: matches.length });
			} else if (matches.length === 1) {
				account = matches[0];
			}
		}
		if (account && !rawFounder.officialCohort && !qualifyingAccountIds.has(positiveJavaInt(account.id))) {
			warnings.push({
				reason: "founder_account_not_qualified_at_cutoff",
				founderId,
				accountId: positiveJavaInt(account.id)
			});
			account = null;
		}
		const accountId = account ? positiveJavaInt(account.id) : 0;
		if (accountId) {
			if (account.starterCardLegacySuppressedAt) {
				errors.push({
					reason: "founder_account_admin_suppressed",
					founderId,
					accountId
				});
			}
			const previousFounder = founderAccounts.get(accountId);
			if (previousFounder && previousFounder !== founderId) {
				errors.push({ reason: "duplicate_founder_account", founderId, previousFounder, accountId });
			} else {
				founderAccounts.set(accountId, founderId);
			}
		}

		const baseCode = rawFounder.officialCohort
			? normalizeSignupCode(rawFounder.baseCode)
			: timestampBefore(rawFounder && rawFounder.signupCodeCreatedAt, freezeAtMs)
				? normalizeSignupCode(rawFounder && rawFounder.signupCode)
				: "";
		if (baseCode) registerCodeOwner(codeOwners, baseCode, founderId, "base", errors);
		const referralCodes = [];
		const rawReferralCodes = rawFounder.officialCohort
			? (rawFounder.referralCodes || []).map((code) => ({ code, createdAt: freezeAt }))
			: founderReferralCodeRecords(rawFounder).filter((record) => timestampBefore(record.createdAt, freezeAtMs));
		for (const record of rawReferralCodes) {
			const code = normalizeSignupCode(record.code);
			if (!code) continue;
			registerCodeOwner(codeOwners, code, founderId, "referral", errors);
			referralCodes.push(code);
		}
		if (!accountId && !baseCode) {
			errors.push({ reason: "unlinked_founder_missing_base_code", founderId });
		}
		founderRecords.push({
			founderId,
			email,
			normalizedName,
			accountId,
			baseCode,
			referralCodes,
			cohortPlayerIds: rawFounder.officialCohort
				? (rawFounder.playerIds || []).map(positiveJavaInt).filter(Boolean)
				: null
		});
	}
	for (const [key, row] of cache.legacyStarterRows) {
		const match = /^starter_card:(\d+)$/.exec(key);
		const accountId = match ? positiveJavaInt(match[1]) : 0;
		if (!accountId) {
			errors.push({ reason: "invalid_legacy_starter_key", marker: safeMarkerLabel(key) });
			continue;
		}
		if (key !== `starter_card:${accountId}`) {
			errors.push({ reason: "noncanonical_legacy_starter_key", marker: safeMarkerLabel(key) });
			continue;
		}
		if (!founderAccounts.has(accountId)) {
			rewardState(row.value, key, errors);
			warnings.push({ reason: "ignored_nonfounder_legacy_starter", accountId });
		}
	}

	const existingManifestByAccount = new Map();
	const manifestPlayerOwners = new Map();
	for (const [key, row] of cache.compositeStarterRows) {
		const parsed = parseStarterKey(key);
		const state = rewardState(row.value, key, errors);
		if (!parsed) {
			errors.push({ reason: "invalid_starter_manifest_key", marker: safeMarkerLabel(key) });
			continue;
		}
		if (key !== starterKey(parsed.accountId, parsed.playerId)) {
			errors.push({ reason: "noncanonical_starter_manifest_key", marker: safeMarkerLabel(key) });
			continue;
		}
		if (!state) continue;
		const founderId = founderAccounts.get(parsed.accountId);
		if (!founderId) {
			errors.push({ reason: "starter_manifest_missing_founder", accountId: parsed.accountId, playerId: parsed.playerId });
			continue;
		}
		const sourceAccountId = webLinks.get(parsed.playerId) || 0;
		if (sourceAccountId && sourceAccountId !== parsed.accountId) {
			errors.push({
				reason: "starter_manifest_player_relinked",
				accountId: parsed.accountId,
				playerId: parsed.playerId,
				sourceAccountId
			});
			continue;
		}
		const previousOwner = manifestPlayerOwners.get(parsed.playerId);
		if (previousOwner && previousOwner !== parsed.accountId) {
			errors.push({
				reason: "starter_manifest_player_issued_twice",
				playerId: parsed.playerId,
				accountId: parsed.accountId,
				previousAccountId: previousOwner
			});
			continue;
		}
		manifestPlayerOwners.set(parsed.playerId, parsed.accountId);
		const sourcePlayer = playersById.get(parsed.playerId);
		const rows = existingManifestByAccount.get(parsed.accountId) || [];
		rows.push({
			key,
			accountId: parsed.accountId,
			playerId: parsed.playerId,
			founderId,
			state,
			characterName: sourcePlayer ? normalizeUsername(sourcePlayer.username) : null,
			tombstone: !sourcePlayer
		});
		existingManifestByAccount.set(parsed.accountId, rows);
	}

	const starterMarkers = new Map();
	const eligibleByPlayer = new Map();
	const eligibleByAccount = new Map();
	for (const founder of founderRecords) {
		if (!founder.accountId) continue;
		const currentLinked = (linkedByAccount.get(founder.accountId) || []).slice()
			.filter((character) => playerCreatedBefore(playersById.get(character.playerId), freezeAtMs))
			.sort((left, right) => left.playerId - right.playerId);
		const existingManifest = (existingManifestByAccount.get(founder.accountId) || []).slice()
			.sort((left, right) => left.playerId - right.playerId);
		let eligible;
		if (hasOfficialFreeze) {
			const cohortPlayerIds = new Set(founder.cohortPlayerIds || []);
			for (const issuance of existingManifest) {
				if (!cohortPlayerIds.has(issuance.playerId)) {
					errors.push({
						reason: "starter_manifest_outside_frozen_cohort",
						accountId: founder.accountId,
						playerId: issuance.playerId
					});
				}
			}
			const existingPlayerIds = new Set(existingManifest.map((issuance) => issuance.playerId));
			for (const playerId of cohortPlayerIds) {
				if (!existingPlayerIds.has(playerId)) {
					errors.push({ reason: "frozen_cohort_manifest_missing", accountId: founder.accountId, playerId });
				}
			}
			eligible = existingManifest.filter((issuance) => cohortPlayerIds.has(issuance.playerId));
		} else {
			const linkedPlayerIds = new Set(currentLinked.map((character) => character.playerId));
			for (const issuance of existingManifest) {
				if (!linkedPlayerIds.has(issuance.playerId)) {
					errors.push({
						reason: "preexisting_manifest_outside_freeze_cohort",
						accountId: founder.accountId,
						playerId: issuance.playerId,
						state: issuance.state
					});
				}
			}
			const existingByPlayer = new Map(existingManifest.map((issuance) => [issuance.playerId, issuance]));
			eligible = currentLinked.map((character) => existingByPlayer.get(character.playerId) || ({
				key: starterKey(founder.accountId, character.playerId),
				accountId: founder.accountId,
				playerId: character.playerId,
				founderId: founder.founderId,
				state: starterAvailable,
				characterName: character.normalizedName,
				tombstone: false
			}));
		}
		if (eligible.length > 10) {
			errors.push({ reason: "founder_character_limit_exceeded", founderId: founder.founderId, accountId: founder.accountId, count: eligible.length });
			continue;
		}
		if (hasOfficialFreeze) {
			const frozenPlayerIds = new Set(founder.cohortPlayerIds || []);
			for (const character of currentLinked) {
				if (!frozenPlayerIds.has(character.playerId)) {
					warnings.push({
						reason: "postfreeze_character_not_added",
						accountId: founder.accountId,
						playerId: character.playerId
					});
				}
			}
		}
		eligibleByAccount.set(founder.accountId, eligible);
		for (const issuance of eligible) {
			if (eligibleByPlayer.has(issuance.playerId)) {
				errors.push({ reason: "founder_player_issued_twice", playerId: issuance.playerId });
				continue;
			}
			starterMarkers.set(issuance.key, issuance);
			eligibleByPlayer.set(issuance.playerId, issuance);
		}
	}
	const cohortMarkers = new Map();
	for (const founder of founderRecords) {
		const playerIds = founder.accountId
			? (eligibleByAccount.get(founder.accountId) || []).map((issuance) => issuance.playerId).sort((a, b) => a - b)
			: [];
		const key = `${founderCohortPrefix}${founder.founderId}`;
		cohortMarkers.set(key, founderCohortValue(founder, playerIds));
	}

	const starterClaimActors = starterClaimActorIds(provenanceRows);
	for (const founder of founderRecords) {
		if (!founder.accountId) continue;
		const legacyRow = cache.legacyStarterRows.get(`starter_card:${founder.accountId}`);
		if (!legacyRow) continue;
		const legacyState = rewardState(legacyRow.value, `starter_card:${founder.accountId}`, errors);
		if (legacyState !== starterClaimed) continue;
		const eligible = eligibleByAccount.get(founder.accountId) || [];
		if (!eligible.length) {
			ambiguities.push({ reason: "claimed_legacy_account_has_no_frozen_character", accountId: founder.accountId });
			continue;
		}
		if (eligible.length === 1) {
			starterMarkers.get(starterKey(founder.accountId, eligible[0].playerId)).state = starterClaimed;
			continue;
		}
		const candidates = new Set();
		for (const character of eligible) {
			const key = starterKey(founder.accountId, character.playerId);
			const sourceRow = cache.compositeStarterRows.get(key);
			if (sourceRow && Number(sourceRow.value) === starterClaimed) candidates.add(character.playerId);
			if (starterClaimActors.has(character.playerId)) candidates.add(character.playerId);
		}
		if (candidates.size !== 1) {
			ambiguities.push({
				reason: "claimed_legacy_account_actor_ambiguous",
				accountId: founder.accountId,
				eligibleCharacters: eligible.length,
				actorCandidates: candidates.size
			});
			continue;
		}
		const playerId = Array.from(candidates)[0];
		starterMarkers.get(starterKey(founder.accountId, playerId)).state = starterClaimed;
	}

	const signupCodes = new Map();
	for (const [code, row] of cache.signupCodeRows) {
		const state = rewardState(row.value, `${signupCodePrefix}${code}`, errors);
		if (state) {
			if (!codeOwners.has(code) && allPortalCodes.has(code)) {
				if (state === starterClaimed) {
					errors.push({ reason: "claimed_signup_code_outside_frozen_cohort", codeHint: code.slice(-4) });
				} else {
					warnings.push({ reason: "postfreeze_signup_code_not_added", codeHint: code.slice(-4) });
				}
				continue;
			}
			signupCodes.set(code, state);
			if (!codeOwners.has(code)) {
				warnings.push({ reason: "preserved_server_only_signup_code", codeHint: code.slice(-4) });
			}
		}
	}
	for (const code of codeOwners.keys()) {
		if (!signupCodes.has(code)) signupCodes.set(code, starterAvailable);
	}

	const codeSuffixCounts = new Map();
	for (const code of signupCodes.keys()) {
		const suffix = code.slice(-4);
		codeSuffixCounts.set(suffix, (codeSuffixCounts.get(suffix) || 0) + 1);
	}
	const codeClaimActors = signupCodeClaimActors(provenanceRows, codeSuffixCounts);
	const foundersById = new Map(founderRecords.map((founder) => [founder.founderId, founder]));
	const baseAccounts = new Map();
	for (const [code, row] of cache.baseAccountRows) {
		const accountId = baseAccountValue(row.value, code, errors);
		if (accountId === null) continue;
		let frozenAccountId = accountId;
		const owner = codeOwners.get(code);
		if (owner && owner.kind === "referral") {
			errors.push({
				reason: "referral_code_has_base_account",
				founderId: owner.founderId,
				codeHint: code.slice(-4),
				accountId
			});
			continue;
		}
		if (!owner && allPortalCodes.has(code)) {
			errors.push({ reason: "base_account_outside_frozen_cohort", codeHint: code.slice(-4), accountId });
			continue;
		}
		if (owner) {
			const expectedAccountId = foundersById.get(owner.founderId).accountId || 0;
			if (accountId !== expectedAccountId) {
				if (!hasOfficialFreeze && accountId === 0 && expectedAccountId > 0) {
					frozenAccountId = expectedAccountId;
					warnings.push({
						reason: "promoted_founder_base_account_at_freeze",
						founderId: owner.founderId,
						codeHint: code.slice(-4),
						accountId: expectedAccountId
					});
				} else {
					errors.push({
						reason: "founder_base_account_mismatch",
						founderId: owner.founderId,
						codeHint: code.slice(-4),
						expectedAccountId,
						accountId
					});
				}
			}
		} else {
			warnings.push({ reason: "preserved_server_only_base_account", codeHint: code.slice(-4), accountId });
		}
		baseAccounts.set(code, frozenAccountId);
	}
	for (const founder of founderRecords) {
		if (!founder.baseCode) continue;
		if (!baseAccounts.has(founder.baseCode)) baseAccounts.set(founder.baseCode, founder.accountId || 0);
	}
	const baseTags = new Map();
	for (const [code, row] of cache.baseTagRows) {
		const playerId = baseTagValue(row.value, code, errors);
		if (playerId === null) continue;
		if (!codeOwners.has(code) && allPortalCodes.has(code)) {
			if (playerId > 0) {
				errors.push({ reason: "bound_base_tag_outside_frozen_cohort", codeHint: code.slice(-4), playerId });
			} else {
				warnings.push({ reason: "postfreeze_base_tag_not_added", codeHint: code.slice(-4) });
			}
			continue;
		}
		baseTags.set(code, playerId);
		const owner = codeOwners.get(code);
		if (owner && owner.kind === "referral") {
			errors.push({
				reason: "referral_code_has_base_tag",
				founderId: owner.founderId,
				codeHint: code.slice(-4),
				playerId
			});
		} else if (!owner) {
			warnings.push({
				reason: "preserved_server_only_base_tag",
				codeHint: code.slice(-4),
				playerId
			});
		}
	}
	const rewardRoutesByPlayer = new Map();
	for (const issuance of starterMarkers.values()) {
		rewardRoutesByPlayer.set(issuance.playerId, {
			founderId: issuance.founderId,
			kind: "composite"
		});
	}
	for (const founder of founderRecords) {
		if (!founder.baseCode) continue;
		const state = signupCodes.get(founder.baseCode) || starterAvailable;
		const existingTag = baseTags.has(founder.baseCode) ? baseTags.get(founder.baseCode) : null;
		let boundPlayerId = existingTag && existingTag > 0 ? existingTag : 0;
		const eligible = founder.accountId ? (eligibleByAccount.get(founder.accountId) || []) : [];

		if (founder.accountId) {
			if (boundPlayerId && !eligible.some((character) => character.playerId === boundPlayerId)) {
				errors.push({ reason: "linked_founder_base_tag_outside_account", founderId: founder.founderId, accountId: founder.accountId, playerId: boundPlayerId });
				boundPlayerId = 0;
			}
			if (!boundPlayerId && state === starterClaimed) {
				boundPlayerId = uniqueCodeClaimActor(founder.baseCode, codeClaimActors, eligible.map((row) => row.playerId), ambiguities, founder.founderId);
			}
			if (!boundPlayerId && state === starterAvailable) {
				const named = eligible.filter((character) => character.characterName === founder.normalizedName);
				if (named.length !== 1) {
					ambiguities.push({ reason: "linked_founder_base_code_binding_ambiguous", founderId: founder.founderId, accountId: founder.accountId, matches: named.length });
				} else {
					boundPlayerId = named[0].playerId;
				}
			}
			if (boundPlayerId) {
				baseTags.set(founder.baseCode, boundPlayerId);
				if (state === starterClaimed) {
					const issuance = eligibleByPlayer.get(boundPlayerId);
					if (issuance) issuance.state = starterClaimed;
				}
			}
		} else {
			if (boundPlayerId && state === starterAvailable) {
				errors.push({
					reason: "available_unlinked_base_code_is_prebound",
					founderId: founder.founderId,
					codeHint: founder.baseCode.slice(-4),
					playerId: boundPlayerId
				});
			}
			if (!boundPlayerId && state === starterClaimed) {
				boundPlayerId = uniqueCodeClaimActor(founder.baseCode, codeClaimActors, null, ambiguities, founder.founderId);
			}
			if (boundPlayerId) {
				const existingRoute = rewardRoutesByPlayer.get(boundPlayerId);
				if (existingRoute) {
					errors.push({
						reason: "unlinked_founder_base_tag_route_conflict",
						founderId: founder.founderId,
						playerId: boundPlayerId,
						previousFounderId: existingRoute.founderId,
						previousKind: existingRoute.kind
					});
				} else {
					rewardRoutesByPlayer.set(boundPlayerId, { founderId: founder.founderId, kind: "base_code" });
				}
			}
			baseTags.set(founder.baseCode, boundPlayerId || 0);
		}
	}

	const nativeLaunchMarkers = new Map();
	let suppressedNativeLaunchMarkers = 0;
	let nativeLaunchMarkersMinted = 0;
	let nativeLaunchMarkersSkippedAfterCutoff = 0;
	for (const [playerId] of cache.nativeLaunchRows) {
		if (!playersById.has(playerId)) {
			errors.push({ reason: "native_launch_marker_missing_player", playerId });
		}
	}
	for (const player of players) {
		const playerId = Number(player.id);
		const row = cache.nativeLaunchRows.get(playerId);
		const state = row ? rewardState(row.value, launchCardKey, errors) : 0;
		const rewardRoute = rewardRoutesByPlayer.get(playerId);
		if (rewardRoute) {
			if (row) suppressedNativeLaunchMarkers += 1;
			const issuance = eligibleByPlayer.get(playerId);
			if (issuance && state === starterClaimed) issuance.state = starterClaimed;
			continue;
		}
		if (state) {
			nativeLaunchMarkers.set(playerId, state);
		} else if (playerCreatedBefore(player, launchCardCutoffMs)) {
			nativeLaunchMarkers.set(playerId, starterAvailable);
			nativeLaunchMarkersMinted += 1;
		} else {
			nativeLaunchMarkersSkippedAfterCutoff += 1;
		}
	}

	return {
		errors,
		ambiguities,
		warnings,
		freezeAt,
		launchCardCutoff,
		hasOfficialFreeze,
		cohortMarkers,
		players,
		webLinks,
		founders: founderRecords,
		linkedFounderCount: founderRecords.filter((founder) => founder.accountId).length,
		unlinkedFounderCount: founderRecords.filter((founder) => !founder.accountId).length,
		portalCharacterLinks: linkedCharacters.length,
		starterMarkers,
		signupCodes,
		baseTags,
		baseAccounts,
		nativeLaunchMarkers,
		nativeLaunchMarkersMinted,
		nativeLaunchMarkersSkippedAfterCutoff,
		suppressedNativeLaunchMarkers,
		staticTablesCopied: ["db_patches", "npclocs", "objects"].filter((name) => sourceTables.has(name))
	};
}

function indexSourceCache(rows, errors) {
	const webLinkRows = [];
	const nativeLaunchRows = new Map();
	const legacyStarterRows = new Map();
	const compositeStarterRows = new Map();
	const signupCodeRows = new Map();
	const baseTagRows = new Map();
	const baseAccountRows = new Map();
	let founderFreezeRow = null;
	const founderCohortRows = new Map();
	for (const row of rows) {
		const playerId = Number(row.playerID);
		const key = String(row.key || "");
		if (key === webAccountKey) {
			if (playerId > 0) webLinkRows.push(row);
			else errors.push({ reason: "invalid_source_web_account_marker", playerId });
		} else if (key === launchCardKey) {
			if (playerId > 0) putUniqueCacheRow(nativeLaunchRows, playerId, row, "native_launch", errors);
			else errors.push({ reason: "invalid_native_launch_marker_player", playerId });
		} else if (playerId === 0 && key.startsWith("starter_card:")) {
			putUniqueCacheRow(legacyStarterRows, key, row, "legacy_starter", errors);
		} else if (playerId === 0 && key.startsWith("starter:")) {
			putUniqueCacheRow(compositeStarterRows, key, row, "starter_manifest", errors);
		} else if (playerId === 0 && key.startsWith(signupCodePrefix)) {
			const code = normalizeSignupCode(key.slice(signupCodePrefix.length));
			if (!code) errors.push({ reason: "invalid_signup_code_cache_key" });
			else if (key !== `${signupCodePrefix}${code}`) errors.push({ reason: "noncanonical_signup_code_cache_key", codeHint: code.slice(-4) });
			else putUniqueCacheRow(signupCodeRows, code, row, "signup_code", errors);
		} else if (playerId === 0 && key.startsWith(baseTagPrefix)) {
			const code = normalizeSignupCode(key.slice(baseTagPrefix.length));
			if (!code) errors.push({ reason: "invalid_base_tag_cache_key" });
			else if (key !== `${baseTagPrefix}${code}`) errors.push({ reason: "noncanonical_base_tag_cache_key", codeHint: code.slice(-4) });
			else putUniqueCacheRow(baseTagRows, code, row, "base_tag", errors);
		} else if (playerId === 0 && key.startsWith(baseAccountPrefix)) {
			const code = normalizeSignupCode(key.slice(baseAccountPrefix.length));
			if (!code) errors.push({ reason: "invalid_base_account_cache_key" });
			else if (key !== `${baseAccountPrefix}${code}`) errors.push({ reason: "noncanonical_base_account_cache_key", codeHint: code.slice(-4) });
			else putUniqueCacheRow(baseAccountRows, code, row, "base_account", errors);
		} else if (playerId === 0 && key === founderFreezeKey) {
			if (founderFreezeRow) errors.push({ reason: "duplicate_source_cache_marker", kind: "founder_freeze", marker: founderFreezeKey });
			else founderFreezeRow = row;
		} else if (playerId === 0 && key.startsWith(founderCohortPrefix)) {
			const founderId = positiveJavaInt(key.slice(founderCohortPrefix.length));
			if (!founderId) errors.push({ reason: "invalid_founder_cohort_key", marker: safeMarkerLabel(key) });
			else if (key !== `${founderCohortPrefix}${founderId}`) errors.push({ reason: "noncanonical_founder_cohort_key", marker: safeMarkerLabel(key) });
			else putUniqueCacheRow(founderCohortRows, founderId, row, "founder_cohort", errors);
		}
	}
	return {
		webLinkRows,
		nativeLaunchRows,
		legacyStarterRows,
		compositeStarterRows,
		signupCodeRows,
		baseTagRows,
		baseAccountRows,
		founderFreezeRow,
		founderCohortRows
	};
}

function putUniqueCacheRow(index, key, row, kind, errors) {
	if (index.has(key)) {
		errors.push({ reason: "duplicate_source_cache_marker", kind, marker: safeMarkerLabel(String(row.key || key)) });
		return;
	}
	index.set(key, row);
}

function buildWebLinks(rows, playersById, accountsById, errors) {
	const candidates = new Map();
	const counts = new Map();
	for (const row of rows) {
		const playerId = positiveJavaInt(row.playerID);
		const accountId = positiveJavaInt(row.value);
		if (!playerId || !playersById.has(playerId) || !accountId || !accountsById.has(accountId)) {
			errors.push({ reason: "invalid_source_web_account_link", playerId: playerId || null, accountId: accountId || null });
			continue;
		}
		const values = candidates.get(playerId) || new Set();
		values.add(accountId);
		candidates.set(playerId, values);
		counts.set(playerId, (counts.get(playerId) || 0) + 1);
	}
	const webLinks = new Map();
	for (const [playerId, values] of candidates) {
		if (counts.get(playerId) !== 1) {
			errors.push({ reason: "duplicate_source_web_account_links", playerId, rows: counts.get(playerId) });
			continue;
		}
		if (values.size !== 1) {
			errors.push({ reason: "conflicting_source_web_account_links", playerId, accounts: Array.from(values).sort((a, b) => a - b) });
			continue;
		}
		webLinks.set(playerId, Array.from(values)[0]);
	}
	return webLinks;
}

function buildLinkedCharacters({ characters, playersById, accountsById, webLinks, errors }) {
	const linked = [];
	const seenPlayers = new Map();
	for (const character of characters) {
		const linkStatus = String(character && character.linkStatus || "").toLowerCase();
		if (linkStatus !== "linked") continue;
		const playerId = positiveJavaInt(character && character.playerId);
		const accountId = positiveJavaInt(character && character.accountId);
		const normalizedName = normalizeUsername(character && (character.normalizedName || character.name));
		if (!playerId || !accountId || !accountsById.has(accountId) || !playersById.has(playerId)) {
			errors.push({
				reason: "portal_link_missing_source_identity",
				characterId: positiveJavaInt(character && character.id) || null,
				playerId: playerId || null,
				accountId: accountId || null
			});
			continue;
		}
		const sourcePlayer = playersById.get(playerId);
		if (!normalizedName || normalizeUsername(sourcePlayer.username) !== normalizedName) {
			errors.push({ reason: "portal_link_username_mismatch", playerId, accountId });
			continue;
		}
		if (webLinks.get(playerId) !== accountId) {
			errors.push({ reason: "portal_link_game_owner_mismatch", playerId, accountId, sourceAccountId: webLinks.get(playerId) || null });
			continue;
		}
		if (seenPlayers.has(playerId)) {
			errors.push({ reason: "duplicate_portal_player_link", playerId, accountId, previousAccountId: seenPlayers.get(playerId) });
			continue;
		}
		seenPlayers.set(playerId, accountId);
		linked.push({ playerId, accountId, normalizedName });
	}
	return linked;
}

function registerFounderIdentity(index, value, founderId, reason, errors) {
	if (!index.has(value)) {
		index.set(value, founderId);
		return;
	}
	errors.push({ reason, founderId, previousFounderId: index.get(value) });
}

function registerCodeOwner(index, code, founderId, kind, errors) {
	const previous = index.get(code);
	if (previous) {
		errors.push({
			reason: "duplicate_founder_or_referral_code",
			founderId,
			previousFounderId: previous.founderId,
			codeHint: code.slice(-4)
		});
		return;
	}
	index.set(code, { founderId, kind });
}

function founderReferralCodeRecords(founder) {
	const values = [];
	for (const field of ["referralRewardCodes", "rewardCodes"]) {
		for (const raw of Array.isArray(founder && founder[field]) ? founder[field] : []) {
			values.push(raw && typeof raw === "object"
				? { code: raw.code, createdAt: raw.createdAt }
				: { code: raw, createdAt: null });
		}
	}
	return values;
}

function parseStoredFreezeAt(raw, errors) {
	try {
		return parseFreezeAt(raw);
	} catch (_error) {
		errors.push({ reason: "invalid_stored_founder_freeze_cutoff" });
		return "";
	}
}

function parseFounderCohort(rows, errors) {
	const cohort = new Map();
	const playerOwners = new Map();
	const codeOwners = new Map();
	for (const [keyFounderId, row] of rows) {
		let value;
		try {
			value = JSON.parse(String(row.value || ""));
		} catch (_error) {
			errors.push({ reason: "invalid_founder_cohort_value", founderId: keyFounderId });
			continue;
		}
		const founderId = positiveJavaInt(value && value.founderId);
		const email = canonicalStoredEmail(value && value.email);
		const normalizedName = normalizeUsername(value && value.normalizedName);
		const accountId = Number(value && value.accountId) === 0 ? 0 : positiveJavaInt(value && value.accountId);
		const baseCode = normalizeSignupCode(value && value.baseCode);
		const rawPlayerIds = Array.isArray(value && value.playerIds) ? value.playerIds : [];
		const playerIds = rawPlayerIds.map(positiveJavaInt).filter(Boolean);
		const rawReferralCodes = Array.isArray(value && value.referralCodes) ? value.referralCodes : [];
		const referralCodes = rawReferralCodes.map(normalizeSignupCode).filter(Boolean);
		const canonicalShape = value && typeof value === "object" && !Array.isArray(value)
			&& value.v === 1
			&& Number.isInteger(value.founderId)
			&& typeof value.email === "string" && value.email === email
			&& typeof value.normalizedName === "string" && value.normalizedName === normalizedName
			&& Number.isInteger(value.accountId)
			&& typeof value.baseCode === "string" && value.baseCode === baseCode
			&& rawPlayerIds.every((playerId) => Number.isInteger(playerId))
			&& rawReferralCodes.every((code) => typeof code === "string" && code === normalizeSignupCode(code))
			&& JSON.stringify(playerIds) === JSON.stringify(playerIds.slice().sort((left, right) => left - right))
			&& JSON.stringify(referralCodes) === JSON.stringify(referralCodes.slice().sort());
		if (!canonicalShape || founderId !== keyFounderId || !email || !normalizedName
			|| (value.accountId !== 0 && !accountId)
			|| playerIds.length !== rawPlayerIds.length
			|| referralCodes.length !== rawReferralCodes.length
			|| (!accountId && playerIds.length)
			|| playerIds.length > 10
			|| new Set(playerIds).size !== playerIds.length
			|| new Set(referralCodes).size !== referralCodes.length) {
			errors.push({ reason: "invalid_founder_cohort_value", founderId: keyFounderId });
			continue;
		}
		for (const playerId of playerIds) {
			const previousFounderId = playerOwners.get(playerId);
			if (previousFounderId) {
				errors.push({ reason: "founder_cohort_player_issued_twice", founderId, previousFounderId, playerId });
			} else {
				playerOwners.set(playerId, founderId);
			}
		}
		for (const code of [baseCode, ...referralCodes].filter(Boolean)) {
			const previousFounderId = codeOwners.get(code);
			if (previousFounderId) {
				errors.push({ reason: "founder_cohort_code_issued_twice", founderId, previousFounderId, codeHint: code.slice(-4) });
			} else {
				codeOwners.set(code, founderId);
			}
		}
		cohort.set(founderId, {
			id: founderId,
			emailCanonical: email,
			emailDisplay: email,
			normalizedName,
			username: normalizedName,
			accountId,
			baseCode,
			referralCodes,
			playerIds
		});
	}
	return cohort;
}

function founderCohortValue(founder, playerIds) {
	return JSON.stringify({
		v: 1,
		founderId: founder.founderId,
		email: founder.email,
		normalizedName: founder.normalizedName,
		accountId: founder.accountId || 0,
		playerIds,
		baseCode: founder.baseCode || "",
		referralCodes: founder.referralCodes.slice().sort()
	});
}

function timestampBefore(raw, cutoffMs) {
	const timestamp = Date.parse(String(raw || ""));
	return Number.isFinite(timestamp) && timestamp < cutoffMs;
}

function playerCreatedBefore(player, cutoffMs) {
	const createdSeconds = Number(player && player.creation_date);
	return Number.isFinite(createdSeconds) && createdSeconds >= 0 && createdSeconds * 1000 < cutoffMs;
}

function starterClaimActorIds(rows) {
	return new Set(rows
		.filter((row) => row.source === "subscription_vendor"
			&& row.command === "subscription_card_grant"
			&& /(?:^|\s)grant=starter_card(?:\s|$)/.test(row.extra))
		.map((row) => positiveJavaInt(row.actorID))
		.filter(Boolean));
}

function signupCodeClaimActors(rows, suffixCounts) {
	const actors = new Map();
	for (const row of rows) {
		if (row.source !== "subscription_signup_code" || row.command !== "subscription_card_grant") continue;
		const suffixMatch = /(?:^|\s)code_suffix=([A-Z0-9]{1,4})(?:\s|$)/i.exec(row.extra);
		const actorId = positiveJavaInt(row.actorID);
		if (!suffixMatch || !actorId) continue;
		const suffix = suffixMatch[1].toUpperCase();
		if (suffixCounts.get(suffix) !== 1) continue;
		const values = actors.get(suffix) || new Set();
		values.add(actorId);
		actors.set(suffix, values);
	}
	return actors;
}

function uniqueCodeClaimActor(code, actorsBySuffix, allowedPlayerIds, ambiguities, founderId) {
	const actors = actorsBySuffix.get(code.slice(-4)) || new Set();
	const allowed = allowedPlayerIds ? new Set(allowedPlayerIds) : null;
	const candidates = Array.from(actors).filter((playerId) => !allowed || allowed.has(playerId));
	if (candidates.length === 1) return candidates[0];
	ambiguities.push({
		reason: "redeemed_founder_base_code_actor_ambiguous",
		founderId,
		codeHint: code.slice(-4),
		actorCandidates: candidates.length
	});
	return 0;
}

function rewardState(raw, label, errors) {
	const value = Number(raw);
	if (value !== starterAvailable && value !== starterClaimed) {
		errors.push({ reason: "invalid_reward_state", marker: safeMarkerLabel(label), value: String(raw) });
		return 0;
	}
	return value;
}

function baseTagValue(raw, code, errors) {
	const value = Number(raw);
	if (!Number.isInteger(value) || value < 0 || value > javaIntMax) {
		errors.push({ reason: "invalid_base_tag_value", codeHint: code.slice(-4) });
		return null;
	}
	return value;
}

function baseAccountValue(raw, code, errors) {
	const value = Number(raw);
	if (!Number.isInteger(value) || value < 0 || value > javaIntMax) {
		errors.push({ reason: "invalid_base_account_value", codeHint: code.slice(-4) });
		return null;
	}
	return value;
}

function starterKey(accountId, playerId) {
	const account = positiveJavaInt(accountId);
	const player = positiveJavaInt(playerId);
	if (!account || !player) return "";
	return `starter:${account}:${player}`;
}

function parseStarterKey(key) {
	const match = /^starter:(\d+):(\d+)$/.exec(String(key || ""));
	if (!match) return null;
	const accountId = positiveJavaInt(match[1]);
	const playerId = positiveJavaInt(match[2]);
	return accountId && playerId ? { accountId, playerId } : null;
}

function buildReport(plan) {
	let available = 0;
	let claimed = 0;
	for (const issuance of plan.starterMarkers.values()) {
		if (issuance.state === starterClaimed) claimed += 1;
		else available += 1;
	}
	let baseTagsBound = 0;
	let baseTagsUnassigned = 0;
	for (const playerId of plan.baseTags.values()) {
		if (playerId > 0) baseTagsBound += 1;
		else baseTagsUnassigned += 1;
	}
	return {
		apply,
		freezeAt: plan.freezeAt,
		launchCardCutoff: plan.launchCardCutoff,
		freezeMode: plan.hasOfficialFreeze ? "rerun" : "initial",
		sourceDb,
		portalStore: portalStorePath,
		outDb: apply ? outDb : null,
		playersPreserved: plan.players.length,
		founders: plan.founders.length,
		linkedFounders: plan.linkedFounderCount,
		unlinkedFounders: plan.unlinkedFounderCount,
		portalCharactersLinked: plan.portalCharacterLinks,
		webAccountLinks: plan.webLinks.size,
		starterCharacterMarkers: plan.starterMarkers.size,
		starterCharacterAvailable: available,
		starterCharacterClaimed: claimed,
		signupCodeMarkers: plan.signupCodes.size,
		baseTags: plan.baseTags.size,
		baseAccounts: plan.baseAccounts.size,
		baseTagsBound,
		baseTagsUnassigned,
		founderCohortMarkers: plan.cohortMarkers.size,
		nativeLaunchCardMarkers: plan.nativeLaunchMarkers.size,
		nativeLaunchCardMarkersMinted: plan.nativeLaunchMarkersMinted,
		nativeLaunchCardMarkersSkippedAfterCutoff: plan.nativeLaunchMarkersSkippedAfterCutoff,
		suppressedNativeLaunchCardMarkers: plan.suppressedNativeLaunchMarkers,
		staticTablesCopied: plan.staticTablesCopied,
		ambiguities: plan.ambiguities,
		errors: plan.errors,
		warnings: plan.warnings
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
		sql.push(cacheInsert(playerId, 0, webAccountKey, String(accountId)));
	}
	sql.push(cacheInsert(0, 0, founderFreezeKey, plan.freezeAt));
	for (const [key, value] of Array.from(plan.cohortMarkers.entries()).sort((a, b) => a[0].localeCompare(b[0]))) {
		sql.push(cacheInsert(0, 0, key, value));
	}
	for (const issuance of Array.from(plan.starterMarkers.values()).sort((a, b) => a.key.localeCompare(b.key))) {
		sql.push(cacheInsert(0, 0, issuance.key, String(issuance.state)));
	}
	for (const [code, state] of Array.from(plan.signupCodes.entries()).sort((a, b) => a[0].localeCompare(b[0]))) {
		sql.push(cacheInsert(0, 0, `${signupCodePrefix}${code}`, String(state)));
	}
	for (const [code, playerId] of Array.from(plan.baseTags.entries()).sort((a, b) => a[0].localeCompare(b[0]))) {
		sql.push(cacheInsert(0, 0, `${baseTagPrefix}${code}`, String(playerId)));
	}
	for (const [code, accountId] of Array.from(plan.baseAccounts.entries()).sort((a, b) => a[0].localeCompare(b[0]))) {
		sql.push(cacheInsert(0, 0, `${baseAccountPrefix}${code}`, String(accountId)));
	}
	for (const [playerId, state] of Array.from(plan.nativeLaunchMarkers.entries()).sort((a, b) => a[0] - b[0])) {
		sql.push(cacheInsert(playerId, 0, launchCardKey, String(state)));
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
		webAccountLinks: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID > 0 AND key = '${webAccountKey}'`),
		starterCharacterCards: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key GLOB 'starter:*:*'"),
		starterCharacterAvailable: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key GLOB 'starter:*:*' AND value = '1'"),
		starterCharacterClaimed: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key GLOB 'starter:*:*' AND value = '2'"),
		legacyStarterCards: scalar("SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE 'starter_card:%'"),
		baseTags: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE '${baseTagPrefix}%'`),
		baseAccounts: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE '${baseAccountPrefix}%'`),
		founderFreezeMarkers: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key = '${founderFreezeKey}'`),
		founderCohortMarkers: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE '${founderCohortPrefix}%'`),
		nativeLaunchCards: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID > 0 AND key = '${launchCardKey}'`),
		signupCodes: scalar(`SELECT COUNT(*) AS value FROM player_cache WHERE playerID = 0 AND key LIKE '${signupCodePrefix}%'`),
		npclocs: maybeCount("npclocs"),
		objects: maybeCount("objects"),
		dbPatches: maybeCount("db_patches"),
		economyCounts,
		integrityCheck
	};
}

function loadRewardProvenance(db, tables) {
	if (!tables.has("item_provenance_events")) return [];
	const columns = new Set(tableInfo(db, "item_provenance_events").map((column) => column.name));
	if (!["actorID", "source", "command", "extra"].every((column) => columns.has(column))) return [];
	return sqliteJson(db, `
		SELECT actorID, source, command, extra
		FROM item_provenance_events
		WHERE (source = 'subscription_vendor' AND extra LIKE '%grant=starter_card%')
		   OR (source = 'subscription_signup_code' AND extra LIKE '%code_suffix=%')
	`);
}

function normalizeSignupCode(raw) {
	const normalized = String(raw || "").toUpperCase().replace(/[^A-Z0-9]/g, "");
	return normalized && normalized.length <= 20 ? normalized : "";
}

function normalizeUsername(raw) {
	return String(raw || "").trim().toLowerCase().replace(/\s+/g, " ");
}

function canonicalStoredEmail(raw) {
	const value = String(raw || "").trim().toLowerCase();
	return value.includes("@") && value.length <= 320 ? value : "";
}

function positiveJavaInt(raw) {
	const value = Number(raw);
	return Number.isInteger(value) && value > 0 && value <= javaIntMax ? value : 0;
}

function parseFreezeAt(raw) {
	const value = String(raw || "").trim();
	if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
		throw new Error("invalid ISO timestamp");
	}
	const timestamp = Date.parse(value);
	if (!Number.isFinite(timestamp)) throw new Error("invalid ISO timestamp");
	return new Date(timestamp).toISOString();
}

function safeMarkerLabel(value) {
	const text = String(value || "");
	if (text.startsWith(signupCodePrefix) || text.startsWith(baseTagPrefix) || text.startsWith(baseAccountPrefix)) {
		return `${text.split(":", 1)[0]}:*${text.slice(-4)}`;
	}
	return text.slice(0, 80);
}

function cacheInsert(playerId, type, key, value) {
	if (String(key).length > 32) throw new Error(`player_cache key exceeds 32 characters: ${safeMarkerLabel(key)}`);
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

function sameExistingFile(left, right) {
	if (!left || !right) return false;
	if (resolve(left) === resolve(right)) return true;
	if (!existsSync(left) || !existsSync(right)) return false;
	try {
		if (realpathSync(left) === realpathSync(right)) return true;
		const leftStat = statSync(left);
		const rightStat = statSync(right);
		return leftStat.dev === rightStat.dev && leftStat.ino === rightStat.ino;
	} catch (_error) {
		return false;
	}
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
	if (message) console.error(`Error: ${message}\n`);
	console.error(`Usage:
	  scripts/reset-launch-game-db.mjs --source-db DB --portal-store dev-store.json --freeze-at ISO [--out-db DB --apply] [--force]

Dry-run (default):
	  scripts/reset-launch-game-db.mjs --source-db /opt/voidscape/server/inc/sqlite/voidscape.db --portal-store /var/lib/voidscape-portal/dev-store.json --freeze-at 2026-07-18T18:00:00Z

Build a separate launch DB (never overwrites source):
	  scripts/reset-launch-game-db.mjs --source-db live.db --portal-store frozen-dev-store.json --freeze-at 2026-07-18T18:00:00Z --out-db launch.db --apply

The first successful apply writes a durable cutoff plus per-founder cohort markers.
Every rerun must use the identical cutoff; the stored cohort remains authoritative.`);
	process.exit(code);
}

function fail(message) {
	console.error(`Error: ${message}`);
	process.exit(1);
}
