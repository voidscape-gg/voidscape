import { createServer } from "node:http";
import { createReadStream } from "node:fs";
import { mkdir, readFile, readdir, rename, stat, writeFile } from "node:fs/promises";
import { execFile as execFileCallback } from "node:child_process";
import { createHash, randomBytes, scrypt as scryptCallback, timingSafeEqual } from "node:crypto";
import { promisify } from "node:util";
import { basename, dirname, extname, join, normalize, relative, resolve } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const scrypt = promisify(scryptCallback);
const execFile = promisify(execFileCallback);
const rootDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(rootDir, "../..");
const port = Number(process.env.PORT || 8788);
const dataDir = process.env.PORTAL_DATA_DIR || join(tmpdir(), "voidscape-portal-api");
const storePath = join(dataDir, "dev-store.json");
const openRscDbPath = process.env.PORTAL_OPENRSC_DB || process.env.OPENRSC_SQLITE_DB || "";
const maxCharacters = 10;
const sessionTtlMs = 1000 * 60 * 60 * 24 * 14;
const linkChallengeTtlMs = 1000 * 60 * 15;
const scryptParams = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const openRscAccountIdCacheKey = "web_account_id";
const starterCardCachePrefix = "starter_card:";
const starterCardAvailable = 1;
const starterCardClaimed = 2;
const accountSubscriptionCachePrefix = "acct_sub:";
const starterFreeSubscriptionType = "starter_free_subscription";
const starterIpDailyLimit = Math.max(1, Number(process.env.PORTAL_STARTER_IP_DAILY_LIMIT || 5));
const abuseSignalTtlMs = 1000 * 60 * 60 * 24 * 90;
const abuseHashSalt = process.env.PORTAL_ABUSE_HASH_SALT || "voidscape-portal-dev";
const adminToken = process.env.PORTAL_ADMIN_TOKEN || "";
const downloadArtifacts = [
	{
		slug: "pc-client",
		label: "PC client",
		filename: "Open_RSC_Client.jar",
		path: join(repoRoot, "Client_Base/Open_RSC_Client.jar")
	},
	{
		slug: "launcher",
		label: "Launcher",
		filename: "OpenRSC.jar",
		path: join(repoRoot, "PC_Launcher/OpenRSC.jar")
	}
];
const clientCacheDir = join(repoRoot, "Client_Base/Cache");
const launcherRuntimeFiles = new Set([
	"credentials.txt",
	"uid.dat",
	"ip.txt",
	"port.txt",
	"hideip.txt",
	"config.txt",
	"client.properties",
	"discord_inuse.txt",
	"launchersettings.conf",
	"voidscapelauncher.properties"
]);

const kits = {
	warrior: {
		path: "Warrior",
		image: "assets/rsc-knight.png",
		gear: ["Iron 2h sword", "Bronze plate body", "Bronze medium helm", "Bronze legs"],
		appearance: "Male, bronze plate set, iron two-hander",
		appearanceData: { hairColour: 2, topColour: 4, trouserColour: 8, skinColour: 1 },
		combat: 8
	},
	arcanist: {
		path: "Arcanist",
		image: "assets/rsc-mage.png",
		gear: ["Shortbow", "Blue wizard robe", "50 bronze arrows", "Air runes"],
		appearance: "Bright robe top, light gear, bow and arrows",
		appearanceData: { hairColour: 5, topColour: 10, trouserColour: 6, skinColour: 0 },
		combat: 5
	},
	forager: {
		path: "Forager",
		image: "assets/rsc-ranger.png",
		gear: ["Bronze axe", "Tinderbox", "Small fishing net", "100 gp"],
		appearance: "Field kit, tools, light travelling clothes",
		appearanceData: { hairColour: 3, topColour: 7, trouserColour: 3, skinColour: 2 },
		combat: 5
	}
};

const publicContent = {
	news: [
		{ type: "rare", text: "Void Enclave boss tuning moved into live difficulty controls.", time: "Today" },
		{ type: "market", text: "Weekly subscription cards and portal redeem flow are now represented.", time: "Today" },
		{ type: "title", text: "Character title state is part of the account portal contract.", time: "Jun 3" }
	],
	downloads: [
		{ label: "Android notes", state: "Requires Android SDK", url: "#" },
		{ label: "Server status", state: "Local world heartbeat", url: "#" }
	],
	highscores: [
		{ rank: "#1", player: "Maeve", combat: 99, xp: "13.1m" },
		{ rank: "#2", player: "Zamak42", combat: 87, xp: "8.7m" },
		{ rank: "#3", player: "WildRanger", combat: 61, xp: "3.2m" },
		{ rank: "#4", player: "VoidMage", combat: 54, xp: "2.4m" },
		{ rank: "#5", player: "DarkWarden", combat: 51, xp: "2.1m" },
		{ rank: "#6", player: "CoinMule17", combat: 43, xp: "1.2m" }
	],
	market: [
		{ item: "Rune 2h sword", average: "48,220 gp", movement: "+8.1%", depth: "High" },
		{ item: "Dragonstone amulet", average: "138,000 gp", movement: "+3.6%", depth: "Thin" },
		{ item: "Subscription card", average: "10,000 gp", movement: "0.0%", depth: "Vendor" },
		{ item: "Law rune", average: "312 gp", movement: "-2.4%", depth: "Active" },
		{ item: "Raw lobster", average: "88 gp", movement: "+1.2%", depth: "Stable" },
		{ item: "Void scimitar", average: "Rare", movement: "No sales", depth: "Watch" }
	],
	activity: [
		{ type: "rare", text: "Maeve received Dragonstone amulet from the rare table.", time: "2m" },
		{ type: "kill", text: "VoidSeeker defeated DarkWarden in level 32 Wilderness.", time: "8m" },
		{ type: "title", text: "Zamak42 equipped The Conqueror.", time: "14m" },
		{ type: "market", text: "Rune 2h sword buy orders rose above 48,000 gp.", time: "26m" },
		{ type: "rare", text: "WildRanger found Void scimitar at the Void Knight.", time: "41m" },
		{ type: "kill", text: "GraveTax escaped with 19 lobsters and a skull.", time: "1h" }
	]
};

let writeQueue = Promise.resolve();
let itemDefinitionsPromise = null;
let titleDefinitionsPromise = null;

class HttpError extends Error {
	constructor(status, message) {
		super(message);
		this.status = status;
	}
}

const server = createServer((request, response) => {
	handleRequest(request, response).catch((error) => {
		const status = error.status || 500;
		json(response, status, {
			error: status === 500 ? "internal_error" : error.message
		});
		if (status === 500) {
			console.error(error);
		}
	});
});

server.listen(port, "127.0.0.1", () => {
	console.log(`Voidscape portal dev server: http://127.0.0.1:${port}/`);
	console.log(`Portal API data: ${storePath}`);
});

async function handleRequest(request, response) {
	const url = new URL(request.url, `http://${request.headers.host || "127.0.0.1"}`);
	if (url.pathname === "/api/launcher/manifest.properties") {
		if (!["GET", "HEAD"].includes(request.method || "GET")) {
			throw new HttpError(405, "method_not_allowed");
		}
		await serveLauncherManifest(request, response);
		return;
	}
	if (url.pathname.startsWith("/api/")) {
		await handleApi(request, response, url);
		return;
	}
	if (url.pathname.startsWith("/downloads/")) {
		await serveDownload(request, response, url.pathname);
		return;
	}
	if (url.pathname.startsWith("/openrsc/avatar/")) {
		await serveOpenRscAvatar(response, url.pathname);
		return;
	}
	await serveStatic(request, response, url.pathname);
}

async function handleApi(request, response, url) {
	const method = request.method || "GET";

	if (method === "GET" && url.pathname === "/api/health") {
		json(response, 200, { ok: true, service: "voidscape-portal-dev" });
		return;
	}

	if (method === "GET" && url.pathname === "/api/public") {
		const store = await loadStore();
		json(response, 200, await publicState(store));
		return;
	}

	if (method === "GET" && url.pathname === "/api/oauth/google/start") {
		throw new HttpError(501, "google_oauth_not_configured");
		return;
	}

	if (method === "POST" && url.pathname === "/api/oauth/google/callback") {
		throw new HttpError(501, "google_oauth_not_configured");
		return;
	}

	if (method === "POST" && url.pathname === "/api/payments/subscription-cards/checkout") {
		throw new HttpError(501, "payments_not_configured");
		return;
	}

	if (method === "GET" && url.pathname === "/api/account") {
		const store = await loadStore();
		const { account, session } = requireSession(request, store);
		json(response, 200, await accountState(store, account, session));
		return;
	}

	if (method === "GET" && url.pathname.startsWith("/api/openrsc/characters/")) {
		const username = decodeURIComponent(url.pathname.slice("/api/openrsc/characters/".length));
		const snapshot = await openRscCharacterSnapshot(username);
		json(response, 200, { character: snapshot });
		return;
	}

	if (method === "POST" && url.pathname === "/api/founder/reservations") {
		const payload = await readJson(request);
		const result = await updateStore((store) => {
			const founder = reserveFounder(store, payload);
			return { founder: founderState(founder) };
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/founder/simulate-referral") {
		const payload = await readJson(request);
		const result = await updateStore((store) => {
			const code = normalizeCode(payload.code || "");
			const founder = store.founders.find((entry) => entry.code === code);
			if (!founder) throw new HttpError(404, "founder_not_found");
			founder.creditedReferrals = Math.min(2, (founder.creditedReferrals || 0) + 1);
			founder.updatedAt = now();
			if (founder.creditedReferrals >= 2) {
				grantStarterCardEntitlement(store, founder, "referral_simulated_dev");
			}
			audit(store, "founder_referral_simulated", { founderId: founder.id });
			return { founder: founderState(founder) };
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/register") {
		const payload = await readJson(request);
		const passwordHash = await hashPassword(requirePassword(payload.password));
		const result = await updateStore(async (store) => {
			const emailCanonical = canonicalEmail(payload.email || "");
			if (!emailCanonical) throw new HttpError(400, "invalid_email");
			if (store.accounts.some((account) => account.emailCanonical === emailCanonical)) {
				throw new HttpError(409, "account_exists");
			}

			const founder = reserveFounder(store, payload);
			const account = {
				id: nextId(store, "account"),
				emailCanonical,
				emailDisplay: String(payload.email || "").trim(),
				passwordHash,
				status: "active",
				subscriptionExpiresAt: 0,
				createdAt: now(),
				updatedAt: now()
			};
			store.accounts.push(account);
			createCharacter(store, account.id, founder.username, "warrior");
			await grantStarterCardIfEligible(store, account, founder, "prelaunch_signup", request, {
				emailCanonical,
				provider: "password"
			});
			const token = createSession(store, account.id);
			audit(store, "account_registered", { accountId: account.id });
			return { token, ...(await accountState(store, account)) };
		});
		json(response, 201, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/google/dev") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const profile = googleDevProfile(payload);
			const account = await upsertGoogleAccount(store, profile, payload, request);
			const token = createSession(store, account.id);
			audit(store, "account_google_login_dev", {
				accountId: account.id,
				email: profile.emailCanonical
			});
			return {
				token,
				auth: {
					provider: "google",
					devMode: true,
					emailVerified: profile.emailVerified
				},
				...(await accountState(store, account))
			};
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/recover-password") {
		const payload = await readJson(request);
		const passwordHash = await hashPassword(requirePassword(payload.newPassword));
		const result = await updateStore(async (store) => {
			const emailCanonical = canonicalEmail(payload.email || "");
			const code = normalizeCode(payload.code || payload.recoveryCode || "");
			if (!emailCanonical || !code) {
				recordAbuseSignal(store, {
					accountId: null,
					signalType: "recovery_code_failed",
					signalValue: emailCanonical || clientIp(request),
					bucket: dailyBucket(),
					metadata: { hasAccount: false }
				});
				return { error: "invalid_recovery_code" };
			}
			const account = store.accounts.find((entry) => entry.emailCanonical === emailCanonical);
			const recoveryCode = account ? store.recoveryCodes.find((entry) =>
				entry.accountId === account.id &&
				entry.status === "active" &&
				entry.codeHash === hashToken(code)
			) : null;
			if (!account || account.status !== "active" || !recoveryCode) {
				recordAbuseSignal(store, {
					accountId: account ? account.id : null,
					signalType: "recovery_code_failed",
					signalValue: emailCanonical || clientIp(request),
					bucket: dailyBucket(),
					metadata: { hasAccount: Boolean(account) }
				});
				return { error: "invalid_recovery_code" };
			}
			recoveryCode.status = "used";
			recoveryCode.usedAt = now();
			recoveryCode.revokedAt = null;
			account.passwordHash = passwordHash;
			account.passwordChangedAt = now();
			account.updatedAt = now();
			let revoked = 0;
			store.sessions.forEach((entry) => {
				if (entry.accountId === account.id && entry.expiresAt > Date.now() && !entry.revokedAt) {
					entry.expiresAt = Date.now();
					entry.revokedAt = now();
					revoked += 1;
				}
			});
			const token = createSession(store, account.id);
			audit(store, "account_recovered_with_code", { accountId: account.id, revoked });
			return { token, ...(await accountState(store, account)) };
		});
		if (result.error) throw new HttpError(401, result.error);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/login") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const emailCanonical = canonicalEmail(payload.email || "");
			const account = store.accounts.find((entry) => entry.emailCanonical === emailCanonical);
			if (!account || !(await verifyPassword(String(payload.password || ""), account.passwordHash))) {
				throw new HttpError(401, "invalid_credentials");
			}
			const token = createSession(store, account.id);
			audit(store, "account_login", { accountId: account.id });
			return { token, ...(await accountState(store, account)) };
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/characters") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const account = requireAccount(request, store);
			await createAccountCharacter(store, account, payload, request);
			account.updatedAt = now();
			audit(store, "character_created", { accountId: account.id });
			return accountState(store, account);
		});
		json(response, 201, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/character-links/start") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const account = requireAccount(request, store);
			const snapshot = await openRscCharacterSnapshot(payload.username || "");
			const challenge = startLinkChallenge(store, account, snapshot);
			audit(store, "character_link_started", {
				accountId: account.id,
				playerId: snapshot.id,
				username: snapshot.name
			});
			return {
				challenge: linkChallengeState(challenge.challenge, challenge.code),
				character: snapshot
			};
		});
		json(response, 201, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/character-links/simulate-verify") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const account = requireAccount(request, store);
			const challenge = requireLinkChallenge(store, account, payload);
			const snapshot = await openRscCharacterSnapshot(challenge.username);
			await linkOpenRscPlayerToAccount(snapshot.id, account.id);
			linkSnapshotCharacter(store, account, challenge, snapshot);
			challenge.status = "verified";
			challenge.verifiedAt = now();
			challenge.updatedAt = now();
			account.updatedAt = now();
			audit(store, "character_link_verified_dev", {
				accountId: account.id,
				playerId: snapshot.id,
				username: snapshot.name
			});
			return accountState(store, account);
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/subscriptions/redeem") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const account = requireAccount(request, store);
			const code = String(payload.code || "").trim();
			if (code.length < 4) throw new HttpError(400, "invalid_redeem_code");
			const base = Math.max(Date.now(), account.subscriptionExpiresAt || 0);
			account.subscriptionExpiresAt = base + 1000 * 60 * 60 * 24 * 7;
			await syncAccountSubscriptionToOpenRsc(account);
			account.updatedAt = now();
			store.entitlements.push({
				id: nextId(store, "entitlement"),
				accountId: account.id,
				type: "weekly_subscription_card",
				status: "consumed",
				source: "redeem_code",
				codeHint: code.slice(0, 8),
				createdAt: now(),
				consumedAt: now()
			});
			audit(store, "subscription_redeemed", { accountId: account.id });
			return accountState(store, account);
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/subscriptions/redeem-starter") {
		throw new HttpError(409, "claim_subscription_card_in_game");
		return;
	}

	if (method === "POST" && url.pathname === "/api/security/password") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const { account, session } = requireSession(request, store);
			if (!(await verifyPassword(String(payload.currentPassword || ""), account.passwordHash))) {
				throw new HttpError(401, "invalid_current_password");
			}
			const passwordHash = await hashPassword(requirePassword(payload.newPassword));
			account.passwordHash = passwordHash;
			account.passwordChangedAt = now();
			account.updatedAt = now();
			audit(store, "account_password_changed", { accountId: account.id });
			return accountState(store, account, session);
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/security/recovery-codes") {
		const result = await updateStore(async (store) => {
			const { account, session } = requireSession(request, store);
			store.recoveryCodes
				.filter((entry) => entry.accountId === account.id && entry.status === "active")
				.forEach((entry) => {
					entry.status = "revoked";
					entry.revokedAt = now();
				});
			const codes = Array.from({ length: 8 }, () => makeRecoveryCode());
			codes.forEach((code) => {
				store.recoveryCodes.push({
					id: nextId(store, "recoveryCode"),
					accountId: account.id,
					codeHash: hashToken(code),
					codeHint: code.slice(-4),
					status: "active",
					createdAt: now(),
					usedAt: null,
					revokedAt: null
				});
			});
			account.recoveryCodesGeneratedAt = now();
			account.updatedAt = now();
			audit(store, "account_recovery_codes_generated", { accountId: account.id, count: codes.length });
			return { codes, ...(await accountState(store, account, session)) };
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/security/sessions/revoke-others") {
		const result = await updateStore(async (store) => {
			const { account, session } = requireSession(request, store);
			let revoked = 0;
			store.sessions.forEach((entry) => {
				if (entry.accountId === account.id && entry.id !== session.id && entry.expiresAt > Date.now() && !entry.revokedAt) {
					entry.expiresAt = Date.now();
					entry.revokedAt = now();
					revoked += 1;
				}
			});
			audit(store, "account_sessions_revoked", { accountId: account.id, revoked });
			return accountState(store, account, session);
		});
		json(response, 200, result);
		return;
	}

	if (method === "GET" && url.pathname === "/api/admin/accounts") {
		requireAdmin(request);
		const store = await loadStore();
		const email = canonicalEmail(url.searchParams.get("email") || "");
		const accountId = Number(url.searchParams.get("accountId") || 0);
		const status = String(url.searchParams.get("status") || "").trim();
		const accounts = store.accounts
			.filter((account) => !email || account.emailCanonical === email)
			.filter((account) => !accountId || account.id === accountId)
			.filter((account) => !status || account.status === status)
			.slice(0, 25);
		json(response, 200, {
			accounts: await Promise.all(accounts.map((account) => adminAccountState(store, account)))
		});
		return;
	}

	const adminAccountMatch = /^\/api\/admin\/accounts\/(\d+)(?:\/([^/]+))?$/.exec(url.pathname);
	if (adminAccountMatch) {
		requireAdmin(request);
		const accountId = Number(adminAccountMatch[1]);
		const action = adminAccountMatch[2] || "";
		if (method === "GET" && !action) {
			const store = await loadStore();
			const account = requireAdminAccount(store, accountId);
			json(response, 200, await adminAccountState(store, account));
			return;
		}
		if (method === "POST" && action === "status") {
			const payload = await readJson(request);
			const result = await updateStore(async (store) => {
				const account = requireAdminAccount(store, accountId);
				const status = String(payload.status || "").trim();
				if (!["active", "locked", "review"].includes(status)) throw new HttpError(400, "invalid_account_status");
				account.status = status;
				account.adminNote = String(payload.note || payload.reason || "").trim().slice(0, 240);
				account.updatedAt = now();
				if (status !== "active") {
					revokeAccountSessions(store, account.id);
				}
				audit(store, "admin_account_status_changed", { accountId: account.id, status });
				return adminAccountState(store, account);
			});
			json(response, 200, result);
			return;
		}
		if (method === "POST" && action === "subscription") {
			const payload = await readJson(request);
			const result = await updateStore(async (store) => {
				const account = requireAdminAccount(store, accountId);
				if (payload.action === "clear") {
					account.subscriptionExpiresAt = 0;
				} else if (payload.expiresAt) {
					const expiresAt = Number(payload.expiresAt);
					if (!Number.isFinite(expiresAt) || expiresAt < 0) throw new HttpError(400, "invalid_subscription_expiry");
					account.subscriptionExpiresAt = expiresAt;
				} else {
					const days = Number(payload.days || 0);
					if (!Number.isFinite(days) || days <= 0 || days > 366) throw new HttpError(400, "invalid_subscription_days");
					const base = Math.max(Date.now(), Number(account.subscriptionExpiresAt || 0));
					account.subscriptionExpiresAt = base + Math.round(days * 1000 * 60 * 60 * 24);
				}
				account.updatedAt = now();
				await syncAccountSubscriptionToOpenRsc(account);
				audit(store, "admin_subscription_changed", {
					accountId: account.id,
					expiresAt: account.subscriptionExpiresAt
				});
				return adminAccountState(store, account);
			});
			json(response, 200, result);
			return;
		}
		if (method === "POST" && action === "starter-card") {
			const payload = await readJson(request);
			const result = await updateStore(async (store) => {
				const account = requireAdminAccount(store, accountId);
				const founder = store.founders.find((entry) => entry.emailCanonical === account.emailCanonical);
				if (!founder) throw new HttpError(404, "founder_not_found");
				const actionName = String(payload.action || "grant").trim();
				if (actionName === "grant") {
					grantStarterCardEntitlement(store, founder, "admin_grant");
					await syncStarterCardToOpenRsc(account);
					account.starterCardReview = null;
				} else if (actionName === "revoke") {
					store.entitlements
						.filter((entry) => entry.accountId === account.id && entry.type === starterFreeSubscriptionType && entry.status === "granted")
						.forEach((entry) => {
							entry.status = "revoked";
							entry.revokedAt = now();
						});
				} else {
					throw new HttpError(400, "invalid_starter_card_action");
				}
				account.updatedAt = now();
				audit(store, "admin_starter_card_changed", { accountId: account.id, action: actionName });
				return adminAccountState(store, account);
			});
			json(response, 200, result);
			return;
		}
		if (method === "POST" && action === "sessions") {
			const result = await updateStore(async (store) => {
				const account = requireAdminAccount(store, accountId);
				const revoked = revokeAccountSessions(store, account.id);
				audit(store, "admin_sessions_revoked", { accountId: account.id, revoked });
				return adminAccountState(store, account);
			});
			json(response, 200, result);
			return;
		}
	}

	throw new HttpError(404, "not_found");
}

async function serveStatic(request, response, pathname) {
	const targetPath = pathname === "/" ? "/index.html" : decodeURIComponent(pathname);
	const resolved = resolve(rootDir, `.${targetPath}`);
	const isInsideRoot = relative(rootDir, resolved).split(/[\\/]/)[0] !== "..";
	if (!isInsideRoot) throw new HttpError(403, "forbidden");

	let filePath = normalize(resolved);
	let fileStat;
	try {
		fileStat = await stat(filePath);
		if (fileStat.isDirectory()) {
			filePath = join(filePath, "index.html");
			fileStat = await stat(filePath);
		}
	} catch (error) {
		throw new HttpError(404, "not_found");
	}

	const type = contentType(filePath);
	const range = request.headers.range;
	if (range && fileStat.size > 0) {
		const match = /^bytes=(\d*)-(\d*)$/.exec(range);
		if (!match) {
			response.writeHead(416, {
				"content-range": `bytes */${fileStat.size}`,
				"cache-control": "no-store"
			});
			response.end();
			return;
		}
		let start = match[1] ? Number(match[1]) : 0;
		let end = match[2] ? Number(match[2]) : fileStat.size - 1;
		if (!match[1] && match[2]) {
			const suffixLength = Number(match[2]);
			start = Math.max(0, fileStat.size - suffixLength);
			end = fileStat.size - 1;
		}
		if (!Number.isInteger(start) || !Number.isInteger(end) || start < 0 || end < start || start >= fileStat.size) {
			response.writeHead(416, {
				"content-range": `bytes */${fileStat.size}`,
				"cache-control": "no-store"
			});
			response.end();
			return;
		}
		end = Math.min(end, fileStat.size - 1);
		response.writeHead(206, {
			"content-type": type,
			"content-length": end - start + 1,
			"content-range": `bytes ${start}-${end}/${fileStat.size}`,
			"accept-ranges": "bytes",
			"cache-control": "no-store"
		});
		createReadStream(filePath, { start, end }).pipe(response);
		return;
	}

	const body = await readFile(filePath);
	response.writeHead(200, {
		"content-type": type,
		"content-length": body.length,
		"accept-ranges": "bytes",
		"cache-control": "no-store"
	});
	response.end(body);
}

async function serveDownload(request, response, pathname) {
	if (pathname.startsWith("/downloads/cache/")) {
		await serveCacheDownload(request, response, pathname);
		return;
	}

	const slug = decodeURIComponent(pathname.slice("/downloads/".length));
	const artifact = downloadArtifacts.find((entry) => entry.slug === slug);
	if (!artifact) throw new HttpError(404, "download_not_found");
	await sendFile(request, response, artifact.path, {
		contentType: "application/java-archive",
		contentDisposition: `attachment; filename="${artifact.filename}"`,
		notFound: "download_not_built"
	});
}

async function serveCacheDownload(request, response, pathname) {
	let requestedPath = "";
	try {
		requestedPath = decodeURIComponent(pathname.slice("/downloads/cache/".length));
	} catch (error) {
		throw new HttpError(400, "invalid_download_path");
	}

	const resolved = resolve(clientCacheDir, requestedPath);
	const isInsideCache = relative(clientCacheDir, resolved).split(/[\\/]/)[0] !== "..";
	if (!isInsideCache || isRuntimeCachePath(requestedPath)) {
		throw new HttpError(403, "forbidden");
	}

	await sendFile(request, response, resolved, {
		contentType: contentType(resolved),
		contentDisposition: `attachment; filename="${basename(resolved)}"`,
		notFound: "download_not_built"
	});
}

async function serveLauncherManifest(request, response) {
	const body = await launcherManifest(request);
	response.writeHead(200, {
		"content-type": "text/plain; charset=utf-8",
		"content-length": Buffer.byteLength(body),
		"cache-control": "no-store"
	});
	if (request.method === "HEAD") {
		response.end();
		return;
	}
	response.end(body);
}

async function launcherManifest(request) {
	const origin = publicOrigin(request);
	const clientArtifact = downloadArtifacts.find((entry) => entry.slug === "pc-client");
	let clientStat;
	try {
		clientStat = await stat(clientArtifact.path);
		if (!clientStat.isFile()) throw new Error("not_file");
	} catch (error) {
		throw new HttpError(404, "launcher_manifest_not_built");
	}

	const entries = [{
		path: clientArtifact.filename,
		url: `${origin}/downloads/${clientArtifact.slug}`,
		sha256: await sha256File(clientArtifact.path),
		mtimeMs: clientStat.mtimeMs
	}];
	entries.push(...await clientCacheManifestEntries(origin));

	const latestMtime = Math.max(...entries.map((entry) => entry.mtimeMs));
	const version = new Date(latestMtime).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
	const lines = [
		propertyLine("version", version),
		propertyLine("baseUrl", `${origin}/downloads/cache/`)
	];
	entries.forEach((entry, index) => {
		const prefix = `file.${index + 1}`;
		lines.push(propertyLine(`${prefix}.path`, entry.path));
		lines.push(propertyLine(`${prefix}.sha256`, entry.sha256));
		lines.push(propertyLine(`${prefix}.url`, entry.url));
	});
	return `${lines.join("")}`;
}

async function clientCacheManifestEntries(origin) {
	let cacheStat;
	try {
		cacheStat = await stat(clientCacheDir);
		if (!cacheStat.isDirectory()) return [];
	} catch (error) {
		return [];
	}

	const files = await listClientCacheFiles(clientCacheDir);
	const entries = [];
	for (const filePath of files) {
		const relativePath = relative(clientCacheDir, filePath).replace(/\\/g, "/");
		if (isRuntimeCachePath(relativePath)) {
			continue;
		}
		const fileStat = await stat(filePath);
		if (!fileStat.isFile()) {
			continue;
		}
		entries.push({
			path: relativePath,
			url: `${origin}/downloads/cache/${encodeRelativeUrlPath(relativePath)}`,
			sha256: await sha256File(filePath),
			mtimeMs: fileStat.mtimeMs
		});
	}
	return entries.sort((a, b) => a.path.localeCompare(b.path));
}

async function listClientCacheFiles(directory) {
	const rows = [];
	const entries = await readdir(directory, { withFileTypes: true });
	for (const entry of entries) {
		const fullPath = join(directory, entry.name);
		if (entry.isDirectory()) {
			rows.push(...await listClientCacheFiles(fullPath));
		} else if (entry.isFile()) {
			rows.push(fullPath);
		}
	}
	return rows;
}

async function sendFile(request, response, filePath, options = {}) {
	let fileStat;
	try {
		fileStat = await stat(filePath);
		if (!fileStat.isFile()) throw new Error("not_file");
	} catch (error) {
		throw new HttpError(404, options.notFound || "file_not_found");
	}
	response.writeHead(200, {
		"content-type": options.contentType || contentType(filePath),
		"content-length": String(fileStat.size),
		"content-disposition": options.contentDisposition || `attachment; filename="${basename(filePath)}"`,
		"cache-control": "no-store"
	});
	if (request.method === "HEAD") {
		response.end();
		return;
	}
	createReadStream(filePath).pipe(response);
}

async function sha256File(filePath) {
	const body = await readFile(filePath);
	return createHash("sha256").update(body).digest("hex");
}

function publicOrigin(request) {
	const forwardedProto = firstHeaderValue(request.headers["x-forwarded-proto"]);
	const forwardedHost = firstHeaderValue(request.headers["x-forwarded-host"]);
	const proto = forwardedProto || (request.socket && request.socket.encrypted ? "https" : "http");
	const host = forwardedHost || request.headers.host || `127.0.0.1:${port}`;
	return `${proto}://${host}`;
}

function firstHeaderValue(value) {
	if (Array.isArray(value)) {
		return value[0] || "";
	}
	return String(value || "").split(",")[0].trim();
}

function encodeRelativeUrlPath(relativePath) {
	return relativePath.split("/").map((part) => encodeURIComponent(part)).join("/");
}

function isRuntimeCachePath(relativePath) {
	return launcherRuntimeFiles.has(basename(relativePath).toLowerCase());
}

function propertyLine(key, value) {
	return `${key}=${escapePropertyValue(value)}\n`;
}

function escapePropertyValue(value) {
	return String(value)
		.replace(/\\/g, "\\\\")
		.replace(/\r/g, "\\r")
		.replace(/\n/g, "\\n");
}

async function serveOpenRscAvatar(response, pathname) {
	const match = /^\/openrsc\/avatar\/(\d+)\.png$/.exec(pathname);
	if (!match) throw new HttpError(404, "avatar_not_found");
	const filePath = avatarFilePath(match[1]);
	let fileStat;
	try {
		fileStat = await stat(filePath);
		if (!fileStat.isFile()) throw new Error("not_file");
	} catch (error) {
		throw new HttpError(404, "avatar_not_found");
	}
	response.writeHead(200, {
		"content-type": "image/png",
		"content-length": fileStat.size,
		"cache-control": "no-store"
	});
	createReadStream(filePath).pipe(response);
}

function reserveFounder(store, payload) {
	const username = cleanUsername(payload.username || payload.name || "");
	const normalizedName = normalizeUsername(username);
	const emailCanonical = canonicalEmail(payload.email || "");
	if (!normalizedName) throw new HttpError(400, "invalid_username");
	if (!emailCanonical) throw new HttpError(400, "invalid_email");

	const nameOwner = store.founders.find((entry) => entry.normalizedName === normalizedName && entry.emailCanonical !== emailCanonical);
	if (nameOwner) throw new HttpError(409, "username_reserved");

	let founder = store.founders.find((entry) => entry.emailCanonical === emailCanonical);
	if (!founder) {
		founder = {
			id: nextId(store, "founder"),
			username,
			normalizedName,
			emailCanonical,
			emailDisplay: String(payload.email || "").trim(),
			code: makeCode(username),
			creditedReferrals: 0,
			status: "dev_verified",
			createdAt: now(),
			updatedAt: now()
		};
		store.founders.push(founder);
		audit(store, "founder_reserved", { founderId: founder.id });
	} else {
		founder.username = username;
		founder.normalizedName = normalizedName;
		founder.emailDisplay = String(payload.email || "").trim();
		founder.updatedAt = now();
		audit(store, "founder_updated", { founderId: founder.id });
	}

	creditFounderReferral(store, founder, payload.referrerCode || payload.referralCode || payload.ref || "");
	return founder;
}

function creditFounderReferral(store, founder, codeInput) {
	const referrerCode = normalizeCode(codeInput || "");
	if (!referrerCode) return null;
	if (referrerCode === founder.code) {
		throw new HttpError(400, "self_referral_not_allowed");
	}

	const referrer = store.founders.find((entry) => entry.code === referrerCode && entry.status !== "released");
	if (!referrer) throw new HttpError(404, "referrer_not_found");
	if (referrer.id === founder.id || referrer.emailCanonical === founder.emailCanonical) {
		throw new HttpError(400, "self_referral_not_allowed");
	}

	const existing = store.referrals.find((entry) =>
		entry.referrerFounderId === referrer.id &&
		entry.referredFounderId === founder.id &&
		entry.status === "credited"
	);
	if (existing) return existing;

	const alreadyReferred = store.referrals.find((entry) =>
		entry.referredFounderId === founder.id &&
		entry.status === "credited"
	);
	if (alreadyReferred) return alreadyReferred;

	const referral = {
		id: nextId(store, "referral"),
		referrerFounderId: referrer.id,
		referredFounderId: founder.id,
		referrerCode,
		status: "credited",
		riskScore: 0,
		createdAt: now(),
		creditedAt: now()
	};
	store.referrals.push(referral);
	founder.referredByCode = referrer.code;
	founder.referredByUsername = referrer.username;
	founder.referredAt = now();
	founder.updatedAt = now();
	referrer.creditedReferrals = Math.max(referrer.creditedReferrals || 0, countCreditedReferrals(store, referrer.id));
	referrer.updatedAt = now();
	if (referrer.creditedReferrals >= 2) {
		grantStarterCardEntitlement(store, referrer, "referral_2_verified_dev");
	}
	audit(store, "founder_referral_credited", {
		referrerFounderId: referrer.id,
		referredFounderId: founder.id
	});
	return referral;
}

function countCreditedReferrals(store, founderId) {
	return store.referrals.filter((entry) =>
		entry.referrerFounderId === founderId &&
		entry.status === "credited"
	).length;
}

function createCharacter(store, accountId, name, path) {
	const username = cleanUsername(name || "");
	const normalizedName = normalizeUsername(username);
	if (!normalizedName) throw new HttpError(400, "invalid_character_name");
	if (store.characters.some((character) => character.normalizedName === normalizedName)) {
		throw new HttpError(409, "character_name_taken");
	}

	const kit = kits[path] || kits.warrior;
	const character = {
		id: nextId(store, "character"),
		accountId,
		name: username,
		normalizedName,
		path: kit.path,
		image: kit.image,
		combat: kit.combat,
		total: "34",
		quest: 0,
		kills: 0,
		status: "Void Island",
		title: "No title equipped",
		lastLogin: "New",
		appearance: kit.appearance,
		gear: kit.gear.slice(),
		appearanceData: kit.appearanceData || null,
		equipment: [],
		playerId: null,
		linkStatus: "preview",
		source: "portal-preview",
		createdAt: now(),
		updatedAt: now()
	};
	store.characters.push(character);
	return character;
}

async function createAccountCharacter(store, account, payload, request) {
	const username = cleanUsername(payload.name || payload.username || "");
	const normalizedName = normalizeUsername(username);
	if (!normalizedName) throw new HttpError(400, "invalid_character_name");
	const existingCharacter = store.characters.find((character) => character.normalizedName === normalizedName);
	const reservedForAccount = existingCharacter
		&& existingCharacter.accountId === account.id
		&& existingCharacter.source === "founder-reserved";
	if (existingCharacter && !reservedForAccount) {
		throw new HttpError(409, "character_name_taken");
	}
	const currentCount = store.characters.filter((character) => character.accountId === account.id).length;
	if (!reservedForAccount && currentCount >= maxCharacters) {
		throw new HttpError(409, "character_limit_reached");
	}

	if (!openRscDbPath) {
		if (reservedForAccount) {
			const kit = kits.warrior;
			Object.assign(existingCharacter, {
				path: kit.path,
				image: kit.image,
				combat: kit.combat,
				total: "34",
				quest: 0,
				kills: 0,
				status: "Void Island",
				title: "No title equipped",
				lastLogin: "New",
				appearance: kit.appearance,
				gear: kit.gear.slice(),
				appearanceData: kit.appearanceData || null,
				equipment: [],
				playerId: null,
				linkStatus: "preview",
				source: "portal-preview",
				updatedAt: now()
			});
			return existingCharacter;
		}
		return createCharacter(store, account.id, username, "warrior");
	}

	const password = requireGamePassword(payload.gamePassword || payload.characterPassword || "");
	if (await openRscPlayerExists(normalizedName)) {
		throw new HttpError(409, "character_name_taken");
	}

	const playerId = await createOpenRscPlayer({
		accountId: account.id,
		username,
		email: account.emailDisplay || account.emailCanonical || "",
		password,
		ip: clientIp(request)
	});
	const snapshot = await openRscCharacterSnapshot(username);
	const character = {
		id: reservedForAccount ? existingCharacter.id : nextId(store, "character"),
		accountId: account.id,
		playerId,
		name: snapshot.name,
		normalizedName,
		path: snapshot.path,
		image: snapshot.image,
		combat: snapshot.combat,
		total: snapshot.total,
		quest: snapshot.quest,
		kills: snapshot.kills,
		status: snapshot.status,
		title: snapshot.title,
		subscription: snapshot.subscription,
		lastLogin: snapshot.lastLogin,
		appearance: snapshot.appearance,
		gear: snapshot.gear,
		appearanceData: snapshot.appearanceData || null,
		equipment: Array.isArray(snapshot.equipment) ? snapshot.equipment : [],
		linkStatus: "linked",
		source: "openrsc-sqlite-created",
		createdAt: reservedForAccount ? existingCharacter.createdAt : now(),
		linkedAt: now(),
		updatedAt: now()
	};
	if (reservedForAccount) {
		const index = store.characters.findIndex((entry) => entry.id === existingCharacter.id);
		store.characters.splice(index, 1, character);
	} else {
		store.characters.push(character);
	}
	return character;
}

async function accountState(store, account, currentSession) {
	await syncAccountSubscriptionFromOpenRsc(account);
	await refreshAccountCharactersFromOpenRsc(store, account);
	const founder = store.founders.find((entry) => entry.emailCanonical === account.emailCanonical) || null;
	return {
		account: {
			id: account.id,
			email: account.emailDisplay,
			displayName: founder ? founder.username : account.emailDisplay,
			status: account.status,
			subscription: subscriptionState(account)
		},
		auth: authState(store, account),
		founder: founder ? founderState(founder) : null,
		characters: store.characters
			.filter((character) => character.accountId === account.id)
			.map((character) => ({
				id: character.id,
				name: character.name,
				path: character.path,
				image: character.image,
				combat: character.combat,
				total: character.total,
				quest: character.quest,
				kills: character.kills,
				status: character.status,
				title: character.title,
				subscription: character.subscription || subscriptionState(account).label,
				lastLogin: character.lastLogin,
				appearance: character.appearance,
				gear: character.gear,
				appearanceData: character.appearanceData || null,
				equipment: Array.isArray(character.equipment) ? character.equipment : [],
				playerId: character.playerId || null,
				linkStatus: character.linkStatus || "preview",
				source: character.source || "portal-preview"
			})),
		linkChallenges: store.linkChallenges
			.filter((challenge) => challenge.accountId === account.id && challenge.status === "pending" && challenge.expiresAt > Date.now())
			.map((challenge) => linkChallengeState(challenge)),
		rewards: rewardState(store, account),
		security: securityState(store, account, currentSession, founder),
		abuse: accountAbuseState(account)
	};
}

async function refreshAccountCharactersFromOpenRsc(store, account) {
	if (!openRscDbPath || !account) return;
	const accountCharacters = store.characters.filter((character) =>
		character.accountId === account.id &&
		character.source !== "founder-reserved" &&
		(character.playerId || character.source === "openrsc-sqlite-created" || character.source === "openrsc-sqlite" || character.linkStatus === "linked")
	);
	for (const character of accountCharacters) {
		try {
			const snapshot = await openRscCharacterSnapshot(character.name);
			Object.assign(character, {
				playerId: snapshot.id,
				name: snapshot.name,
				normalizedName: normalizeUsername(snapshot.name),
				path: snapshot.path,
				image: snapshot.image,
				combat: snapshot.combat,
				total: snapshot.total,
				quest: snapshot.quest,
				kills: snapshot.kills,
				status: snapshot.status,
				title: snapshot.title,
				subscription: snapshot.subscription,
				lastLogin: snapshot.lastLogin,
				appearance: snapshot.appearance,
				gear: snapshot.gear,
				appearanceData: snapshot.appearanceData || null,
				equipment: Array.isArray(snapshot.equipment) ? snapshot.equipment : [],
				linkStatus: "linked",
				source: character.source === "openrsc-sqlite-created" ? character.source : snapshot.source,
				updatedAt: now()
			});
		} catch (error) {
			if (error instanceof HttpError && (error.status === 404 || error.status === 503)) {
				continue;
			}
			throw error;
		}
	}
}

function authState(store, account) {
	const identities = store.identities
		.filter((identity) => identity.accountId === account.id)
		.map((identity) => ({
			provider: identity.provider,
			email: identity.emailDisplay,
			displayName: identity.displayName || "",
			avatarUrl: identity.avatarUrl || "",
			emailVerified: Boolean(identity.emailVerified),
			lastLoginAt: identity.lastLoginAt || null
		}));
	return {
		passwordEnabled: Boolean(account.passwordHash),
		googleConnected: identities.some((identity) => identity.provider === "google"),
		providers: identities
	};
}

function rewardState(store, account) {
	const starterCards = store.entitlements.filter((entry) =>
		entry.accountId === account.id &&
		entry.type === starterFreeSubscriptionType &&
		entry.status === "granted"
	);
	return {
		starterSubscriptionCards: starterCards.length,
		cards: starterCards.map((entry) => ({
			id: entry.id,
			type: entry.type,
			status: entry.status,
			source: entry.source,
			label: "Starter subscription card reserved in Lumbridge",
			createdAt: entry.createdAt
		}))
	};
}

function accountAbuseState(account) {
	const starterCardReview = account.starterCardReview || null;
	return {
		starterCard: starterCardReview ? {
			status: starterCardReview.status || "review",
			reasons: Array.isArray(starterCardReview.reasons) ? starterCardReview.reasons : [],
			createdAt: starterCardReview.createdAt || null
		} : {
			status: "clear",
			reasons: [],
			createdAt: null
		}
	};
}

function securityState(store, account, currentSession, founder) {
	const activeRecoveryCodes = store.recoveryCodes.filter((entry) => entry.accountId === account.id && entry.status === "active");
	const activeSessions = store.sessions
		.filter((session) => session.accountId === account.id && session.expiresAt > Date.now() && !session.revokedAt)
		.sort((a, b) => String(b.lastSeenAt).localeCompare(String(a.lastSeenAt)));
	const auth = authState(store, account);
	const emailVerified = Boolean(founder && (founder.status === "verified" || founder.status === "dev_verified"));
	const hasRecoveryCodes = activeRecoveryCodes.length > 0;
	const passwordChanged = Boolean(account.passwordChangedAt);
	const score = Math.min(100,
		40 +
		(emailVerified || auth.googleConnected ? 20 : 0) +
		(hasRecoveryCodes ? 20 : 0) +
		(passwordChanged || auth.googleConnected ? 10 : 0) +
		(activeSessions.length <= 2 ? 10 : 5)
	);

	return {
		score,
		emailVerified: emailVerified || auth.googleConnected,
		auth,
		recoveryCodes: {
			activeCount: activeRecoveryCodes.length,
			lastGeneratedAt: account.recoveryCodesGeneratedAt || null
		},
		passwordChangedAt: account.passwordChangedAt || null,
		sessions: activeSessions.map((session) => ({
			id: session.id,
			current: Boolean(currentSession && session.id === currentSession.id),
			createdAt: session.createdAt,
			lastSeenAt: session.lastSeenAt,
			expiresAt: session.expiresAt,
			client: "Portal API",
			location: "Local dev"
		}))
	};
}

async function publicState(store) {
	const founderUnlocks = store.founders.filter((founder) =>
		Boolean(founder.starterCardUnlocked) || founder.creditedReferrals >= 2
	).length;
	return {
		status: {
			world: "World 1",
			online: true,
			playersOnline: 247,
			patch: "0.8.7",
			lastSave: "2 min ago"
		},
		rates: {
			baseCombat: 7,
			baseSkill: 4,
			subscribedCombat: 10,
			subscribedSkill: 6
		},
		founderStats: {
			reservations: store.founders.length,
			starterCardsUnlocked: founderUnlocks
		},
		downloads: (await downloadState()).concat(publicContent.downloads),
		news: publicContent.news,
		highscores: publicContent.highscores,
		market: publicContent.market,
		activity: dynamicActivity(store).concat(publicContent.activity).slice(0, 8)
	};
}

async function downloadState() {
	const rows = [];
	for (const artifact of downloadArtifacts) {
		try {
			const fileStat = await stat(artifact.path);
			rows.push({
				label: artifact.label,
				state: `Built ${formatBytes(fileStat.size)}`,
				url: `/downloads/${artifact.slug}`,
				available: true,
				sizeBytes: fileStat.size,
				updatedAt: fileStat.mtime.toISOString()
			});
		} catch (error) {
			rows.push({
				label: artifact.label,
				state: "Run scripts/build.sh",
				url: "#",
				available: false
			});
		}
	}
	return rows;
}

function dynamicActivity(store) {
	const rows = [];
	const latestFounder = store.founders[store.founders.length - 1];
	const latestCharacter = store.characters[store.characters.length - 1];
	if (latestFounder) {
		const cardReserved = Boolean(latestFounder.starterCardUnlocked) || latestFounder.creditedReferrals >= 2;
		rows.push({
			type: cardReserved ? "rare" : "title",
			text: `${latestFounder.username} reserved a founder pass${cardReserved ? " and a Lumbridge subscription card" : ""}.`,
			time: "Now"
		});
	}
	if (latestCharacter) {
		rows.push({
			type: "title",
			text: `${latestCharacter.name} joined the roster as a ${latestCharacter.path}.`,
			time: "Now"
		});
	}
	return rows;
}

function startLinkChallenge(store, account, snapshot) {
	const normalizedName = normalizeUsername(snapshot.name);
	if (!normalizedName) throw new HttpError(400, "invalid_username");

	const existingForAccount = store.characters.find((character) =>
		character.accountId === account.id &&
		(character.playerId === snapshot.id || character.normalizedName === normalizedName)
	);
	const currentCount = store.characters.filter((character) => character.accountId === account.id).length;
	if (!existingForAccount && currentCount >= maxCharacters) {
		throw new HttpError(409, "character_limit_reached");
	}

	const alreadyLinked = store.characters.find((character) =>
		character.accountId !== account.id &&
		character.playerId === snapshot.id &&
		character.linkStatus === "linked"
	);
	if (alreadyLinked) {
		throw new HttpError(409, "character_already_linked");
	}

	store.linkChallenges
		.filter((challenge) => challenge.accountId === account.id && challenge.normalizedName === normalizedName && challenge.status === "pending")
		.forEach((challenge) => {
			challenge.status = "revoked";
			challenge.updatedAt = now();
		});

	const code = makeLinkCode();
	const challenge = {
		id: nextId(store, "linkChallenge"),
		accountId: account.id,
		playerId: snapshot.id,
		username: snapshot.name,
		normalizedName,
		codeHash: hashToken(code),
		codeHint: code.slice(-6),
		status: "pending",
		expiresAt: Date.now() + linkChallengeTtlMs,
		createdAt: now(),
		updatedAt: now(),
		verifiedAt: null
	};
	store.linkChallenges.push(challenge);
	return { challenge, code };
}

function requireLinkChallenge(store, account, payload) {
	const id = Number(payload.challengeId || payload.id || 0);
	const code = normalizeCode(payload.code || "");
	const challenge = store.linkChallenges.find((entry) => entry.id === id && entry.accountId === account.id);
	if (!challenge) throw new HttpError(404, "link_challenge_not_found");
	if (challenge.status !== "pending") throw new HttpError(409, "link_challenge_not_pending");
	if (challenge.expiresAt <= Date.now()) {
		challenge.status = "expired";
		challenge.updatedAt = now();
		throw new HttpError(410, "link_challenge_expired");
	}
	if (!code || challenge.codeHash !== hashToken(code)) {
		throw new HttpError(400, "invalid_link_code");
	}
	return challenge;
}

function linkSnapshotCharacter(store, account, challenge, snapshot) {
	const normalizedName = normalizeUsername(snapshot.name);
	const linkedByOther = store.characters.find((character) =>
		character.accountId !== account.id &&
		character.playerId === snapshot.id &&
		character.linkStatus === "linked"
	);
	if (linkedByOther) throw new HttpError(409, "character_already_linked");

	const currentCount = store.characters.filter((character) => character.accountId === account.id).length;
	const existingIndex = store.characters.findIndex((character) =>
		character.accountId === account.id &&
		(character.playerId === snapshot.id || character.normalizedName === normalizedName)
	);
	if (existingIndex === -1 && currentCount >= maxCharacters) {
		throw new HttpError(409, "character_limit_reached");
	}

	const existing = existingIndex >= 0 ? store.characters[existingIndex] : null;
	const linkedCharacter = {
		id: existing ? existing.id : nextId(store, "character"),
		accountId: account.id,
		playerId: snapshot.id,
		name: snapshot.name,
		normalizedName,
		path: snapshot.path,
		image: snapshot.image,
		combat: snapshot.combat,
		total: snapshot.total,
		quest: snapshot.quest,
		kills: snapshot.kills,
		status: snapshot.status,
		title: snapshot.title,
		subscription: snapshot.subscription,
		lastLogin: snapshot.lastLogin,
		appearance: snapshot.appearance,
		gear: snapshot.gear,
		appearanceData: snapshot.appearanceData || null,
		equipment: Array.isArray(snapshot.equipment) ? snapshot.equipment : [],
		linkStatus: "linked",
		source: snapshot.source,
		linkedAt: now(),
		createdAt: existing ? existing.createdAt : now(),
		updatedAt: now()
	};

	if (existingIndex >= 0) {
		store.characters.splice(existingIndex, 1, linkedCharacter);
	} else {
		store.characters.push(linkedCharacter);
	}
	return linkedCharacter;
}

function linkChallengeState(challenge, code) {
	const visibleCode = code || "";
	return {
		id: challenge.id,
		username: challenge.username,
		playerId: challenge.playerId,
		status: challenge.status,
		expiresAt: challenge.expiresAt,
		minutesRemaining: Math.max(0, Math.ceil((challenge.expiresAt - Date.now()) / (1000 * 60))),
		code: visibleCode,
		codeHint: challenge.codeHint,
		command: visibleCode ? `::link ${visibleCode}` : ""
	};
}

async function openRscPlayerExists(normalizedName) {
	if (!openRscDbPath) return false;
	const rows = await sqliteJson(`
		SELECT id
		FROM players
		WHERE lower(username) = ${sqlString(normalizedName)}
		LIMIT 1
	`);
	return rows.length > 0;
}

async function syncStarterCardToOpenRsc(account) {
	if (!openRscDbPath || !account) return;
	const key = starterCardCacheKey(account.id);
	if (!key) return;

	const current = await sqliteJson(`
		SELECT value
		FROM player_cache
		WHERE playerID = 0 AND key = ${sqlString(key)}
		ORDER BY dbid DESC
		LIMIT 1
	`);
	if (current.length && Number(current[0].value) === starterCardClaimed) {
		return;
	}

	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)};
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (0, 0, ${sqlString(key)}, ${sqlString(starterCardAvailable)});
		COMMIT;
		SELECT value FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)} LIMIT 1;
	`);
}

function starterCardCacheKey(accountId) {
	const id = Number(accountId);
	return Number.isInteger(id) && id > 0 ? `${starterCardCachePrefix}${id}` : "";
}

function accountSubscriptionCacheKey(accountId) {
	const id = Number(accountId);
	return Number.isInteger(id) && id > 0 ? `${accountSubscriptionCachePrefix}${id}` : "";
}

async function syncAccountSubscriptionToOpenRsc(account) {
	if (!openRscDbPath || !account) return;
	const key = accountSubscriptionCacheKey(account.id);
	if (!key) return;
	const expiresAt = Number(account.subscriptionExpiresAt || 0);
	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)};
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (0, 3, ${sqlString(key)}, ${sqlString(expiresAt)});
		COMMIT;
		SELECT value FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)} LIMIT 1;
	`);
}

async function syncAccountSubscriptionFromOpenRsc(account) {
	if (!openRscDbPath || !account) return;
	const key = accountSubscriptionCacheKey(account.id);
	if (!key) return;
	const rows = await sqliteJson(`
		SELECT value
		FROM player_cache
		WHERE playerID = 0 AND key = ${sqlString(key)}
		ORDER BY dbid DESC
		LIMIT 1
	`);
	if (!rows.length) return;
	const expiresAt = Number(rows[0].value || 0);
	if (Number.isFinite(expiresAt) && expiresAt !== Number(account.subscriptionExpiresAt || 0)) {
		account.subscriptionExpiresAt = expiresAt;
		account.updatedAt = now();
	}
}

async function linkOpenRscPlayerToAccount(playerId, accountId) {
	if (!openRscDbPath || !playerId || !accountId) return;
	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache
			WHERE playerID = ${Number(playerId)}
			  AND key = ${sqlString(openRscAccountIdCacheKey)};
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (${Number(playerId)}, 0, ${sqlString(openRscAccountIdCacheKey)}, ${sqlString(accountId)});
		COMMIT;
		SELECT value FROM player_cache
			WHERE playerID = ${Number(playerId)}
			  AND key = ${sqlString(openRscAccountIdCacheKey)}
			LIMIT 1;
	`);
}

async function createOpenRscPlayer({ accountId, username, email, password, ip }) {
	const passwordState = legacyGamePasswordHash(password);
	const createdAt = Math.floor(Date.now() / 1000);
	const rows = await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		INSERT INTO players (
			username, email, pass, salt, creation_date, creation_ip, group_id
		) VALUES (
			${sqlString(username)},
			${sqlString(email || "")},
			${sqlString(passwordState.hash)},
			${sqlString(passwordState.salt)},
			${createdAt},
			${sqlString(ip || "127.0.0.1")},
			1
		);
		INSERT INTO maxstats (playerID)
			SELECT id FROM players WHERE lower(username) = ${sqlString(normalizeUsername(username))} LIMIT 1;
		INSERT INTO curstats (playerID)
			SELECT id FROM players WHERE lower(username) = ${sqlString(normalizeUsername(username))} LIMIT 1;
		INSERT INTO experience (playerID, hits)
			SELECT id, 4000 FROM players WHERE lower(username) = ${sqlString(normalizeUsername(username))} LIMIT 1;
		INSERT INTO capped_experience (playerID)
			SELECT id FROM players WHERE lower(username) = ${sqlString(normalizeUsername(username))} LIMIT 1;
		INSERT INTO player_cache (playerID, type, key, value)
			SELECT id, 0, ${sqlString(openRscAccountIdCacheKey)}, ${sqlString(accountId)}
			FROM players
			WHERE lower(username) = ${sqlString(normalizeUsername(username))}
			LIMIT 1;
		COMMIT;
		SELECT id FROM players WHERE lower(username) = ${sqlString(normalizeUsername(username))} LIMIT 1;
	`);
	const playerId = Number(rows[0] && rows[0].id);
	if (!playerId) throw new HttpError(500, "openrsc_character_create_failed");
	return playerId;
}

async function openRscCharacterSnapshot(username) {
	if (!openRscDbPath) {
		throw new HttpError(503, "openrsc_db_not_configured");
	}
	const normalized = normalizeUsername(username);
	if (!normalized) {
		throw new HttpError(400, "invalid_username");
	}

	const playerRows = await sqliteJson(`
		SELECT id, username, group_id, combat, skill_total, x, y, kills, npc_kills, deaths,
		       quest_points, login_date, online, male, haircolour, topcolour, trousercolour,
		       skincolour, headsprite, bodysprite
		FROM players
		WHERE lower(username) = ${sqlString(normalized)}
		LIMIT 1
	`);
	const player = playerRows[0];
	if (!player) {
		throw new HttpError(404, "character_not_found");
	}

	const cacheRows = await sqliteJson(`
		SELECT key, value, type
		FROM player_cache
		WHERE playerID = ${Number(player.id)}
		  AND key IN ('player_title_active', 'void_path', ${sqlString(openRscAccountIdCacheKey)})
	`);
	const cache = Object.fromEntries(cacheRows.map((row) => [row.key, row.value]));
	const equipmentRows = await loadOpenRscEquipment(Number(player.id));
	const itemDefs = await loadItemDefinitions();
	const titleDefs = await loadTitleDefinitions();
	const equipment = equipmentRows.map((row) => {
		const catalogId = Number(row.catalogID);
		const def = itemDefs.get(catalogId) || {};
		return {
			slot: wearSlotLabel(def.wearSlot),
			itemId: Number(row.itemID),
			catalogId,
			name: def.name || `Item ${catalogId}`,
			amount: Number(row.amount || 1),
			wearSlot: Number(def.wearSlot),
			appearanceId: Number(def.appearanceID || 0),
			wearableId: Number(def.wearableID || 0)
		};
	});
	const titleId = cache.player_title_active || "";
	const title = titleId && titleDefs.get(titleId) ? titleDefs.get(titleId) : "";
	const subscription = await openRscSubscriptionState(cache);
	const location = locationState(Number(player.x), Number(player.y));
	const lastLogin = Number(player.login_date || 0);
	const avatar = await openRscAvatarUrl(Number(player.id));

	return {
		id: Number(player.id),
		name: player.username,
		path: pathLabel(cache.void_path),
		image: avatar || pathImage(cache.void_path, player),
		combat: Number(player.combat || 3),
		total: String(player.skill_total || 27),
		quest: Number(player.quest_points || 0),
		kills: Number(player.kills || 0),
		npcKills: Number(player.npc_kills || 0),
		deaths: Number(player.deaths || 0),
		status: location.label,
		location,
		title: title || "No title equipped",
		subscription: subscription.label,
		subscriptionState: subscription,
		lastLogin: lastLogin ? formatUnixTime(lastLogin) : "Never",
		appearance: appearanceSummary(player),
		appearanceData: {
			male: Number(player.male) === 1,
			hairColour: Number(player.haircolour),
			topColour: Number(player.topcolour),
			trouserColour: Number(player.trousercolour),
			skinColour: Number(player.skincolour),
			headSprite: Number(player.headsprite),
			bodySprite: Number(player.bodysprite)
		},
		gear: equipment.length ? equipment.map((item) => item.name) : ["No wielded equipment saved"],
		equipment,
		source: "openrsc-sqlite",
		sourceLabel: "OpenRSC SQLite"
	};
}

async function loadOpenRscEquipment(playerId) {
	const inventoryRows = await sqliteJson(`
		SELECT s.itemID, s.catalogID, s.amount, i.slot
		FROM invitems i
		JOIN itemstatuses s ON i.itemID = s.itemID
		WHERE i.playerID = ${playerId}
		  AND s.wielded = 1
		ORDER BY i.slot ASC
	`);
	const hasEquippedTable = (await sqliteJson("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'equipped'")).length > 0;
	if (!hasEquippedTable) {
		return inventoryRows;
	}
	const equippedRows = await sqliteJson(`
		SELECT s.itemID, s.catalogID, s.amount, NULL AS slot
		FROM equipped e
		JOIN itemstatuses s ON e.itemID = s.itemID
		WHERE e.playerID = ${playerId}
	`);
	const byItemId = new Map();
	inventoryRows.concat(equippedRows).forEach((row) => {
		byItemId.set(Number(row.itemID), row);
	});
	return Array.from(byItemId.values());
}

async function sqliteJson(query) {
	try {
		const { stdout } = await execFile("sqlite3", ["-readonly", "-json", openRscDbPath, query], {
			maxBuffer: 1024 * 1024
		});
		return stdout.trim() ? JSON.parse(stdout) : [];
	} catch (error) {
		if (error.code === "ENOENT") {
			throw new HttpError(503, "sqlite3_not_available");
		}
		if (error.stderr && error.stderr.includes("no such table")) {
			throw new HttpError(500, "openrsc_schema_missing");
		}
		throw error;
	}
}

async function sqliteWriteJson(query) {
	try {
		const { stdout } = await execFile("sqlite3", ["-json", openRscDbPath, query], {
			maxBuffer: 1024 * 1024
		});
		return stdout.trim() ? JSON.parse(stdout) : [];
	} catch (error) {
		if (error.code === "ENOENT") {
			throw new HttpError(503, "sqlite3_not_available");
		}
		if (error.stderr && error.stderr.includes("no such table")) {
			throw new HttpError(500, "openrsc_schema_missing");
		}
		if (error.stderr && error.stderr.includes("constraint")) {
			throw new HttpError(409, "openrsc_character_constraint_failed");
		}
		throw error;
	}
}

async function loadItemDefinitions() {
	if (!itemDefinitionsPromise) {
		itemDefinitionsPromise = (async () => {
			const files = ["ItemDefs.json", "ItemDefsCustom.json", "ItemDefsPatch18.json"];
			const map = new Map();
			for (const file of files) {
				const raw = await readFile(join(repoRoot, "server/conf/server/defs", file), "utf8");
				const parsed = JSON.parse(raw);
				const rows = parsed.item || parsed.items || [];
				rows.forEach((row) => map.set(Number(row.id), row));
			}
			return map;
		})();
	}
	return itemDefinitionsPromise;
}

async function loadTitleDefinitions() {
	if (!titleDefinitionsPromise) {
		titleDefinitionsPromise = (async () => {
			const raw = await readFile(join(repoRoot, "server/src/com/openrsc/server/content/PlayerTitle.java"), "utf8");
			const map = new Map();
			const pattern = /^\s*[A-Z0-9_]+\("([^"]+)",\s*"([^"]+)"/gm;
			let match;
			while ((match = pattern.exec(raw)) !== null) {
				map.set(match[1], match[2]);
			}
			return map;
		})();
	}
	return titleDefinitionsPromise;
}

async function openRscSubscriptionState(cache) {
	const accountId = Number(cache[openRscAccountIdCacheKey] || 0);
	let expiresAt = 0;
	if (accountId > 0) {
		const key = accountSubscriptionCacheKey(accountId);
		const rows = await sqliteJson(`
			SELECT value
			FROM player_cache
			WHERE playerID = 0 AND key = ${sqlString(key)}
			ORDER BY dbid DESC
			LIMIT 1
		`);
		expiresAt = Number(rows[0] && rows[0].value || 0);
	}
	const active = expiresAt > Date.now();
	return {
		active,
		expiresAt,
		label: active && expiresAt > Date.now() ? formatRemaining(expiresAt) : active ? "Subscribed" : "Unsubscribed",
		combatXpRate: active ? 10 : 7,
		skillXpRate: active ? 6 : 4
	};
}

function sqlString(value) {
	return `'${String(value).replace(/'/g, "''")}'`;
}

function wearSlotLabel(slot) {
	return {
		0: "Head",
		1: "Body",
		2: "Legs",
		3: "Shield",
		4: "Weapon",
		5: "Hands",
		6: "Feet",
		7: "Cape",
		8: "Neck",
		9: "Ring",
		10: "Ammo",
		11: "Two-handed",
		12: "Hair",
		13: "Aura"
	}[Number(slot)] || "Wielded";
}

function pathLabel(value) {
	return {
		1: "Warrior",
		2: "Forager",
		3: "Arcanist"
	}[Number(value)] || "OpenRSC save";
}

function pathImage(value, player) {
	const explicit = {
		1: "assets/rsc-knight.png",
		2: "assets/rsc-ranger.png",
		3: "assets/rsc-mage.png"
	}[Number(value)];
	if (explicit) return explicit;
	if (Number(player.combat || 0) >= 40) return "assets/rsc-knight.png";
	return "assets/rsc-ranger.png";
}

async function openRscAvatarUrl(playerId) {
	try {
		const fileStat = await stat(avatarFilePath(playerId));
		if (!fileStat.isFile()) return "";
		return `/openrsc/avatar/${Number(playerId)}.png?v=${Math.floor(fileStat.mtimeMs)}`;
	} catch (error) {
		return "";
	}
}

function avatarFilePath(playerId) {
	return join(repoRoot, "server/avatars", `${openRscDbName()}+${Number(playerId)}.png`);
}

function openRscDbName() {
	if (process.env.PORTAL_OPENRSC_DB_NAME) return process.env.PORTAL_OPENRSC_DB_NAME;
	const source = openRscDbPath ? basename(openRscDbPath) : "voidscape.db";
	return source.replace(/\.[^.]+$/, "") || "voidscape";
}

function locationState(x, y) {
	const floor = y >= 944 ? Math.floor(y / 944) : 0;
	const localY = floor ? y - floor * 944 : y;
	const label = locationLabel(x, localY, floor);
	return { x, y, floor, localY, label };
}

function locationLabel(x, y, floor) {
	if (x >= 115 && x <= 145 && y >= 495 && y <= 535) return "Lumbridge";
	if (x >= 190 && x <= 235 && y >= 430 && y <= 470) return "Void Island";
	if (x >= 85 && x <= 115 && y >= 505 && y <= 535) return "Draynor";
	if (x >= 115 && x <= 155 && y >= 465 && y <= 495) return "Al Kharid";
	if (x >= 120 && x <= 170 && y >= 330 && y <= 390) return "Varrock";
	if (x >= 185 && x <= 230 && y >= 300 && y <= 350) return "Edgeville";
	if (x >= 180 && y <= 300) return "Wilderness";
	return floor ? `Floor ${floor} at ${x}, ${y}` : `${x}, ${y}`;
}

function formatUnixTime(seconds) {
	return new Date(seconds * 1000).toLocaleString("en-US", {
		month: "short",
		day: "numeric",
		year: "numeric",
		hour: "numeric",
		minute: "2-digit"
	});
}

function appearanceSummary(player) {
	const body = Number(player.male) === 1 ? "Male" : "Female";
	return [
		body,
		`hair ${Number(player.haircolour)}`,
		`top ${Number(player.topcolour)}`,
		`trousers ${Number(player.trousercolour)}`,
		`skin ${Number(player.skincolour)}`,
		`head sprite ${Number(player.headsprite)}`,
		`body sprite ${Number(player.bodysprite)}`
	].join(", ");
}

function founderState(founder) {
	const creditedReferrals = founder.creditedReferrals || 0;
	return {
		username: founder.username,
		email: founder.emailDisplay,
		code: founder.code,
		creditedReferrals,
		requiredReferrals: 2,
		starterCardUnlocked: Boolean(founder.starterCardUnlocked) || creditedReferrals >= 2,
		referredBy: founder.referredByCode ? {
			code: founder.referredByCode,
			username: founder.referredByUsername || ""
		} : null,
		status: founder.status
	};
}

function grantStarterCardEntitlement(store, founder, source = "prelaunch_signup") {
	if (!founder) return null;
	founder.starterCardUnlocked = true;
	founder.updatedAt = now();
	const account = store.accounts.find((entry) => entry.emailCanonical === founder.emailCanonical);
	if (!account) return null;
	const exists = store.entitlements.find((entry) => entry.accountId === account.id && entry.type === starterFreeSubscriptionType && entry.status !== "revoked");
	if (exists) return exists;
	const entitlement = {
		id: nextId(store, "entitlement"),
		accountId: account.id,
		type: starterFreeSubscriptionType,
		status: "granted",
		source,
		createdAt: now(),
		claimedAt: null,
		consumedAt: null
	};
	store.entitlements.push(entitlement);
	return entitlement;
}

async function grantStarterCardIfEligible(store, account, founder, source, request, context) {
	recordSignupSignals(store, account, request, context);
	const decision = starterCardDecision(store, account, request, context);
	if (!decision.allowed) {
		account.starterCardReview = {
			status: "review",
			reasons: decision.reasons,
			createdAt: now()
		};
		audit(store, "starter_card_review_required", {
			accountId: account.id,
			reasons: decision.reasons
		});
		return null;
	}

	const entitlement = grantStarterCardEntitlement(store, founder, source);
	recordStarterGrantSignals(store, account, request, context);
	await syncStarterCardToOpenRsc(account);
	return entitlement;
}

function starterCardDecision(store, account, request, context = {}) {
	const reasons = [];
	const ip = clientIp(request);
	const since = Date.now() - 1000 * 60 * 60 * 24;
	const ipGrantCount = countAbuseSignals(store, "starter_card_granted_ip", ip, since);
	if (!isLocalIp(ip) && ipGrantCount >= starterIpDailyLimit) {
		reasons.push("starter_card_ip_daily_limit");
	}

	const emailCanonical = context.emailCanonical || account.emailCanonical || "";
	const existingEmailGrants = countAbuseSignals(store, "starter_card_granted_email", emailCanonical, 0);
	if (existingEmailGrants > 0) {
		reasons.push("starter_card_email_already_granted");
	}

	if (context.provider && context.providerSubject) {
		const identityKey = `${context.provider}:${context.providerSubject}`;
		const existingIdentityGrants = countAbuseSignals(store, "starter_card_granted_identity", identityKey, 0);
		if (existingIdentityGrants > 0) {
			reasons.push("starter_card_identity_already_granted");
		}
	}

	return {
		allowed: reasons.length === 0,
		reasons
	};
}

function recordSignupSignals(store, account, request, context = {}) {
	const ip = clientIp(request);
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "account_signup_ip",
		signalValue: ip,
		bucket: dailyBucket(),
		metadata: { provider: context.provider || "password", local: isLocalIp(ip) }
	});
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "account_signup_email",
		signalValue: context.emailCanonical || account.emailCanonical || "",
		bucket: emailDomainBucket(context.emailCanonical || account.emailCanonical || ""),
		metadata: { provider: context.provider || "password" }
	});
	if (context.provider && context.providerSubject) {
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "account_signup_identity",
			signalValue: `${context.provider}:${context.providerSubject}`,
			bucket: context.provider,
			metadata: { provider: context.provider }
		});
	}
}

function recordStarterGrantSignals(store, account, request, context = {}) {
	const ip = clientIp(request);
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "starter_card_granted_ip",
		signalValue: ip,
		bucket: dailyBucket(),
		metadata: { provider: context.provider || "password", local: isLocalIp(ip) }
	});
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "starter_card_granted_email",
		signalValue: context.emailCanonical || account.emailCanonical || "",
		bucket: emailDomainBucket(context.emailCanonical || account.emailCanonical || ""),
		metadata: { provider: context.provider || "password" }
	});
	if (context.provider && context.providerSubject) {
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "starter_card_granted_identity",
			signalValue: `${context.provider}:${context.providerSubject}`,
			bucket: context.provider,
			metadata: { provider: context.provider }
		});
	}
}

function recordAbuseSignal(store, { accountId = null, signalType, signalValue, bucket = "", metadata = {} }) {
	if (!signalType || !signalValue) return null;
	const signal = {
		id: nextId(store, "abuseSignal"),
		accountId,
		signalType,
		signalHash: abuseHash(signalValue),
		bucket,
		metadata,
		createdAt: now(),
		expiresAt: new Date(Date.now() + abuseSignalTtlMs).toISOString()
	};
	store.abuseSignals.push(signal);
	return signal;
}

function countAbuseSignals(store, signalType, signalValue, sinceMs) {
	const signalHash = abuseHash(signalValue);
	const sinceTime = Number(sinceMs || 0);
	return store.abuseSignals.filter((entry) =>
		entry.signalType === signalType &&
		entry.signalHash === signalHash &&
		Date.parse(entry.createdAt || 0) >= sinceTime
	).length;
}

function abuseHash(value) {
	return hashToken(`${abuseHashSalt}:${String(value || "").trim().toLowerCase()}`);
}

function dailyBucket() {
	return new Date().toISOString().slice(0, 10);
}

function emailDomainBucket(emailCanonical) {
	const domain = String(emailCanonical || "").split("@")[1] || "";
	return domain || "unknown";
}

function isLocalIp(ip) {
	return ip === "127.0.0.1" || ip === "::1" || ip === "::ffff:127.0.0.1";
}

function googleDevProfile(payload) {
	const requestedUsername = requireReservationUsername(payload.username || payload.reservedUsername || "");
	const fallbackEmail = `${requestedUsername.toLowerCase().replace(/[^a-z0-9]+/g, ".").replace(/^\.+|\.+$/g, "") || "player"}@google.voidscape.local`;
	const emailDisplay = String(payload.email || payload.googleEmail || fallbackEmail).trim();
	const emailCanonical = canonicalEmail(emailDisplay);
	if (!emailCanonical) throw new HttpError(400, "invalid_google_email");
	const displayName = cleanUsername(payload.displayName || payload.name || requestedUsername || emailDisplay.split("@")[0] || "Voidscape player");
	const subjectInput = String(payload.sub || payload.subject || payload.googleSub || `dev:${emailCanonical}`).trim();
	if (subjectInput.length < 3) throw new HttpError(400, "invalid_google_subject");
	const username = requestedUsername;
	return {
		provider: "google",
		providerSubject: subjectInput,
		emailCanonical,
		emailDisplay,
		displayName,
		avatarUrl: String(payload.avatarUrl || payload.picture || "").trim(),
		emailVerified: payload.emailVerified === false ? false : true,
		username
	};
}

function requireReservationUsername(value) {
	const username = cleanUsername(value || "");
	if (!/^[a-zA-Z0-9 ]{2,12}$/.test(username)) {
		throw new HttpError(400, "invalid_username");
	}
	return username;
}

async function upsertGoogleAccount(store, profile, payload, request) {
	let identity = store.identities.find((entry) =>
		entry.provider === profile.provider &&
		entry.providerSubject === profile.providerSubject
	);
	let account = identity ? store.accounts.find((entry) => entry.id === identity.accountId) : null;
	if (!account) {
		account = store.accounts.find((entry) => entry.emailCanonical === profile.emailCanonical) || null;
	}

	if (!account) {
		const founder = reserveFounder(store, {
			username: profile.username,
			email: profile.emailDisplay,
			referrerCode: payload.referrerCode || payload.referralCode || payload.ref || ""
		});
		account = {
			id: nextId(store, "account"),
			emailCanonical: profile.emailCanonical,
			emailDisplay: profile.emailDisplay,
			passwordHash: null,
			status: "active",
			subscriptionExpiresAt: 0,
			createdAt: now(),
			updatedAt: now()
		};
		store.accounts.push(account);
		const reservedCharacter = createCharacter(store, account.id, founder.username, "warrior");
		reservedCharacter.source = "founder-reserved";
		reservedCharacter.status = "Reserved username";
		await grantStarterCardIfEligible(store, account, founder, "prelaunch_google_signup_dev", request, {
			emailCanonical: profile.emailCanonical,
			provider: profile.provider,
			providerSubject: profile.providerSubject
		});
		audit(store, "account_google_registered_dev", { accountId: account.id });
	}

	const accountGoogleIdentity = store.identities.find((entry) =>
		entry.accountId === account.id &&
		entry.provider === profile.provider
	);
	if (accountGoogleIdentity && accountGoogleIdentity.providerSubject !== profile.providerSubject) {
		throw new HttpError(409, "google_identity_conflict");
	}

	if (!identity) {
		identity = {
			id: nextId(store, "identity"),
			accountId: account.id,
			provider: profile.provider,
			providerSubject: profile.providerSubject,
			emailCanonical: profile.emailCanonical,
			emailDisplay: profile.emailDisplay,
			displayName: profile.displayName,
			avatarUrl: profile.avatarUrl,
			emailVerified: profile.emailVerified,
			createdAt: now(),
			updatedAt: now(),
			lastLoginAt: now()
		};
		store.identities.push(identity);
		audit(store, "account_google_identity_linked_dev", { accountId: account.id });
	} else {
		identity.emailCanonical = profile.emailCanonical;
		identity.emailDisplay = profile.emailDisplay;
		identity.displayName = profile.displayName;
		identity.avatarUrl = profile.avatarUrl;
		identity.emailVerified = profile.emailVerified;
		identity.updatedAt = now();
		identity.lastLoginAt = now();
	}

	account.emailDisplay = profile.emailDisplay;
	account.updatedAt = now();
	return account;
}

function createSession(store, accountId) {
	const token = randomBytes(32).toString("base64url");
	store.sessions.push({
		id: nextId(store, "session"),
		accountId,
		tokenHash: hashToken(token),
		createdAt: now(),
		expiresAt: Date.now() + sessionTtlMs,
		lastSeenAt: now()
	});
	return token;
}

function requireSession(request, store) {
	const auth = request.headers.authorization || "";
	const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
	if (!token) throw new HttpError(401, "missing_session");
	const tokenHash = hashToken(token);
	const session = store.sessions.find((entry) => entry.tokenHash === tokenHash && entry.expiresAt > Date.now() && !entry.revokedAt);
	if (!session) throw new HttpError(401, "invalid_session");
	session.lastSeenAt = now();
	const account = store.accounts.find((entry) => entry.id === session.accountId);
	if (!account || account.status !== "active") throw new HttpError(401, "invalid_account");
	return { account, session };
}

function requireAccount(request, store) {
	const { account } = requireSession(request, store);
	return account;
}

function requireAdmin(request) {
	if (!adminToken) throw new HttpError(503, "admin_not_configured");
	const headerToken = String(request.headers["x-portal-admin-token"] || "").trim();
	const auth = String(request.headers.authorization || "");
	const bearerToken = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
	const provided = headerToken || bearerToken;
	if (!provided || !constantTimeMatches(provided, adminToken)) {
		throw new HttpError(403, "admin_forbidden");
	}
	return { actorType: "admin", actorId: "local-admin" };
}

function requireAdminAccount(store, accountId) {
	const account = store.accounts.find((entry) => entry.id === accountId);
	if (!account) throw new HttpError(404, "account_not_found");
	return account;
}

async function adminAccountState(store, account) {
	const base = await accountState(store, account);
	const recentAudit = store.audit
		.filter((entry) => entry.metadata && entry.metadata.accountId === account.id)
		.slice(-20)
		.reverse()
		.map((entry) => ({
			id: entry.id,
			type: entry.type,
			metadata: entry.metadata,
			createdAt: entry.createdAt
		}));
	const abuseSignals = store.abuseSignals
		.filter((entry) => entry.accountId === account.id)
		.slice(-20)
		.reverse()
		.map((entry) => ({
			id: entry.id,
			signalType: entry.signalType,
			signalHashPrefix: String(entry.signalHash || "").slice(0, 12),
			bucket: entry.bucket,
			metadata: entry.metadata || {},
			createdAt: entry.createdAt,
			expiresAt: entry.expiresAt
		}));
	return {
		...base,
		admin: {
			accountId: account.id,
			status: account.status,
			note: account.adminNote || "",
			recentAudit,
			abuseSignals
		}
	};
}

function revokeAccountSessions(store, accountId) {
	let revoked = 0;
	store.sessions.forEach((entry) => {
		if (entry.accountId === accountId && entry.expiresAt > Date.now() && !entry.revokedAt) {
			entry.expiresAt = Date.now();
			entry.revokedAt = now();
			revoked += 1;
		}
	});
	return revoked;
}

async function updateStore(mutator) {
	const next = writeQueue.then(async () => {
		const store = await loadStore();
		const result = await mutator(store);
		await saveStore(store);
		return result;
	});
	writeQueue = next.catch(() => undefined);
	return next;
}

async function loadStore() {
	try {
		const raw = await readFile(storePath, "utf8");
		return normalizeStore(JSON.parse(raw));
	} catch (error) {
		return normalizeStore({});
	}
}

async function saveStore(store) {
	await mkdir(dataDir, { recursive: true });
	const tmpPath = `${storePath}.${process.pid}.tmp`;
	await writeFile(tmpPath, `${JSON.stringify(normalizeStore(store), null, 2)}\n`);
	await rename(tmpPath, storePath);
}

function normalizeStore(store) {
	return {
		nextIds: {
			account: Number(store.nextIds && store.nextIds.account) || 1,
			character: Number(store.nextIds && store.nextIds.character) || 1,
			founder: Number(store.nextIds && store.nextIds.founder) || 1,
			referral: Number(store.nextIds && store.nextIds.referral) || 1,
			session: Number(store.nextIds && store.nextIds.session) || 1,
			entitlement: Number(store.nextIds && store.nextIds.entitlement) || 1,
			linkChallenge: Number(store.nextIds && store.nextIds.linkChallenge) || 1,
			recoveryCode: Number(store.nextIds && store.nextIds.recoveryCode) || 1,
			identity: Number(store.nextIds && store.nextIds.identity) || 1,
			audit: Number(store.nextIds && store.nextIds.audit) || 1,
			abuseSignal: Number(store.nextIds && store.nextIds.abuseSignal) || 1
		},
		accounts: Array.isArray(store.accounts) ? store.accounts : [],
		identities: Array.isArray(store.identities) ? store.identities : [],
		sessions: Array.isArray(store.sessions) ? store.sessions : [],
		founders: Array.isArray(store.founders) ? store.founders : [],
		referrals: Array.isArray(store.referrals) ? store.referrals : [],
		characters: Array.isArray(store.characters) ? store.characters : [],
		entitlements: Array.isArray(store.entitlements) ? store.entitlements : [],
		linkChallenges: Array.isArray(store.linkChallenges) ? store.linkChallenges : [],
		recoveryCodes: Array.isArray(store.recoveryCodes) ? store.recoveryCodes : [],
		audit: Array.isArray(store.audit) ? store.audit : [],
		abuseSignals: Array.isArray(store.abuseSignals) ? store.abuseSignals : []
	};
}

function nextId(store, key) {
	const id = store.nextIds[key] || 1;
	store.nextIds[key] = id + 1;
	return id;
}

function audit(store, type, metadata) {
	store.audit.push({
		id: nextId(store, "audit"),
		type,
		metadata,
		createdAt: now()
	});
}

async function hashPassword(password) {
	const salt = randomBytes(16).toString("base64url");
	const derived = await scrypt(password, salt, 64, scryptParams);
	return `scrypt$${scryptParams.N}$${scryptParams.r}$${scryptParams.p}$${salt}$${derived.toString("base64url")}`;
}

async function verifyPassword(password, encoded) {
	const parts = String(encoded || "").split("$");
	if (parts.length !== 6 || parts[0] !== "scrypt") return false;
	const options = {
		N: Number(parts[1]),
		r: Number(parts[2]),
		p: Number(parts[3]),
		maxmem: 64 * 1024 * 1024
	};
	const expected = Buffer.from(parts[5], "base64url");
	const actual = await scrypt(password, parts[4], expected.length, options);
	return expected.length === actual.length && timingSafeEqual(expected, actual);
}

function requirePassword(password) {
	const text = String(password || "");
	if (text.length < 8 || text.length > 128) {
		throw new HttpError(400, "invalid_password");
	}
	return text;
}

function requireGamePassword(password) {
	const text = String(password || "");
	if (text.length < 4 || text.length > 20) {
		throw new HttpError(400, "invalid_game_password");
	}
	return text;
}

function legacyGamePasswordHash(password) {
	const salt = randomBytes(15).toString("base64url").slice(0, 20);
	const md5 = createHash("md5").update(password).digest("hex");
	return {
		salt,
		hash: createHash("sha512").update(salt + md5).digest("hex")
	};
}

function clientIp(request) {
	const forwarded = String(request.headers["x-forwarded-for"] || "").split(",")[0].trim();
	const raw = forwarded || request.socket.remoteAddress || "127.0.0.1";
	return raw === "::1" || raw === "::ffff:127.0.0.1" ? "127.0.0.1" : raw;
}

function subscriptionState(account) {
	const expiresAt = account.subscriptionExpiresAt || 0;
	const active = expiresAt > Date.now();
	return {
		active,
		expiresAt,
		label: active ? formatRemaining(expiresAt) : "Unsubscribed",
		combatXpRate: active ? 10 : 7,
		skillXpRate: active ? 6 : 4
	};
}

function formatRemaining(expiresAt) {
	const remaining = Math.max(0, expiresAt - Date.now());
	const days = Math.floor(remaining / (1000 * 60 * 60 * 24));
	const hours = Math.floor(remaining / (1000 * 60 * 60)) % 24;
	return `${days} days ${hours} hours`;
}

function formatBytes(size) {
	const value = Number(size || 0);
	if (value >= 1024 * 1024) return `${(value / (1024 * 1024)).toFixed(1)} MB`;
	if (value >= 1024) return `${Math.round(value / 1024)} KB`;
	return `${value} B`;
}

function cleanUsername(value) {
	return String(value).trim().replace(/\s+/g, " ");
}

function normalizeUsername(value) {
	const clean = cleanUsername(value);
	if (!/^[a-zA-Z0-9 ]{2,12}$/.test(clean)) return "";
	return clean.toLowerCase();
}

function canonicalEmail(value) {
	const email = String(value).trim().toLowerCase();
	const match = /^([^\s@]+)@([^\s@]+\.[^\s@]+)$/.exec(email);
	if (!match) return "";
	let local = match[1].split("+")[0];
	let domain = match[2];
	if (domain === "googlemail.com") domain = "gmail.com";
	if (domain === "gmail.com") {
		local = local.replace(/\./g, "");
	}
	return local && domain ? `${local}@${domain}` : "";
}

function makeCode(username) {
	const stem = username.toUpperCase().replace(/[^A-Z0-9]+/g, "").slice(0, 7) || "VOID";
	return `${stem}-${randomBytes(3).toString("hex").toUpperCase()}`;
}

function makeLinkCode() {
	return `VLINK-${randomBytes(4).toString("hex").toUpperCase()}`;
}

function makeRecoveryCode() {
	return `VOID-${randomBytes(3).toString("hex").toUpperCase()}-${randomBytes(3).toString("hex").toUpperCase()}`;
}

function normalizeCode(code) {
	return String(code).toUpperCase().replace(/[^A-Z0-9-]/g, "").slice(0, 18);
}

function hashToken(token) {
	return createHash("sha256").update(token).digest("base64url");
}

function constantTimeMatches(actual, expected) {
	const actualHash = Buffer.from(hashToken(actual));
	const expectedHash = Buffer.from(hashToken(expected));
	return actualHash.length === expectedHash.length && timingSafeEqual(actualHash, expectedHash);
}

async function readJson(request) {
	const chunks = [];
	let size = 0;
	for await (const chunk of request) {
		size += chunk.length;
		if (size > 1024 * 1024) throw new HttpError(413, "payload_too_large");
		chunks.push(chunk);
	}
	if (!chunks.length) return {};
	try {
		return JSON.parse(Buffer.concat(chunks).toString("utf8"));
	} catch (error) {
		throw new HttpError(400, "invalid_json");
	}
}

function json(response, status, body) {
	response.writeHead(status, {
		"content-type": "application/json; charset=utf-8",
		"cache-control": "no-store"
	});
	response.end(`${JSON.stringify(body, null, 2)}\n`);
}

function contentType(filePath) {
	const ext = extname(filePath).toLowerCase();
	return {
		".html": "text/html; charset=utf-8",
		".css": "text/css; charset=utf-8",
		".js": "application/javascript; charset=utf-8",
		".mjs": "application/javascript; charset=utf-8",
		".json": "application/json; charset=utf-8",
		".properties": "text/plain; charset=utf-8",
		".png": "image/png",
		".jpg": "image/jpeg",
		".jpeg": "image/jpeg",
		".webp": "image/webp",
		".svg": "image/svg+xml",
		".mp4": "video/mp4",
		".webm": "video/webm"
	}[ext] || "application/octet-stream";
}

function now() {
	return new Date().toISOString();
}
