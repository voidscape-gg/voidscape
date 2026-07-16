#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { createHash } from "node:crypto";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const launchConfigContractPath = resolve(root, "scripts/launch-config-contract.json");
const startedAt = new Date();
const defaultOut = `tmp/launch-staging-verify-${stamp(startedAt)}`;

const args = parseArgs(process.argv.slice(2));
if (args.help) {
	usage();
	process.exit(0);
}

const checks = [];
const failures = [];
const warnings = [];
let signupUser = args.signupUsername || generatedUsername();
let signupEmail = args.signupEmail || `staging-${signupUser.toLowerCase()}@voidscape.gg`;
let signupPassword = args.signupPassword || "Launchpass1";
let serverClientVersion = "";
let signupReadyForGameSmoke = false;

try {
	await main();
} catch (error) {
	fail("fatal", error && error.stack ? error.stack : String(error));
}

await writeSummary();
if (failures.length) {
	console.error(`Launch staging verification failed with ${failures.length} failure(s).`);
	for (const failure of failures) console.error(`- ${failure.name}: ${failure.detail}`);
	process.exit(1);
}

console.log("Launch staging verification passed.");
if (warnings.length) {
	console.log("Warnings:");
	for (const warning of warnings) console.log(`- ${warning.name}: ${warning.detail}`);
}
console.log(`Artifacts: ${args.out}`);

async function main() {
	args.out = resolve(root, args.out || defaultOut);
	await mkdir(args.out, { recursive: true });
	if (args.serverConfigOnly) {
		if (!args.serverConfig || !args.connectionsConfig) {
			throw new Error("--server-config-only requires --server-config and --connections-config.");
		}
		await verifyServerConfig();
		return;
	}

	if (!args.portalUrl) {
		throw new Error("Missing --portal-url.");
	}
	args.portalUrl = normalizeBaseUrl(args.portalUrl, "portal");
	if (!args.allowHttp && args.portalUrl.protocol !== "https:") {
		throw new Error("Portal URL must be https:// unless --allow-http is set.");
	}
	pass("portal url", args.portalUrl.href);
	const adminPublicInputs = args.adminPublicUrls.length
		? [...args.adminPublicUrls]
		: [args.portalUrl.href];
	const productionAdminOrigins = [
		"https://voidscape.gg/",
		"https://www.voidscape.gg/",
		"https://voidscape.5.161.114.251.sslip.io/"
	];
	const targetsProduction = [args.portalUrl.href, ...adminPublicInputs]
		.some((value) => productionAdminOrigins.some((origin) => new URL(origin).hostname === new URL(value).hostname));
	if (targetsProduction) adminPublicInputs.push(...productionAdminOrigins);
	args.adminPublicUrls = [...new Set(adminPublicInputs.map((value) => normalizeBaseUrl(value, "admin public").href))];
	for (const value of args.adminPublicUrls) {
		const url = new URL(value);
		if (!args.allowHttp && url.protocol !== "https:") {
			throw new Error("Admin public URLs must be https:// unless --allow-http is set.");
		}
	}
	if (args.allowLocalAdminTokenGuard) {
		if (!args.allowHttp || args.adminPublicUrls.some((value) => !isLoopbackUrl(value))) {
			throw new Error("--allow-local-admin-token-guard is restricted to loopback URLs with --allow-http.");
		}
	} else if (args.adminToken) {
		throw new Error("--admin-token is allowed only with --allow-local-admin-token-guard; public admin routes must stay externally blocked.");
	}

	if (!args.skipWebVerify) {
		if (!args.webUrl) {
			throw new Error("Missing --web-url. Pass --skip-web-verify only for portal-only rehearsal.");
		}
		args.webUrl = normalizeBaseUrl(args.webUrl, "web").href;
		if (!args.allowHttp && !args.webUrl.startsWith("https://")) {
			throw new Error("Web URL must be https:// unless --allow-http is set.");
		}
	}

	if ((!args.serverConfig || !args.connectionsConfig) && !args.skipServerConfig) {
		throw new Error("Missing --server-config or --connections-config. Pass copies of both deployed configs, or --skip-server-config for portal-only rehearsal.");
	}
	const signupModes = [args.runSignup, args.pendingEmailRehearsal, args.skipSignup].filter(Boolean).length;
	if (signupModes !== 1) {
		throw new Error("Choose exactly one signup mode: --run-signup, --pending-email-rehearsal, or --skip-signup.");
	}
	if (args.runSignup && (!args.signupUsername || !args.signupEmail || !args.signupPassword)) {
		throw new Error("Final --run-signup requires explicit --signup-username, --signup-email, and --signup-password values for the exact account that will be email-verified.");
	}
	if (!Number.isFinite(args.verificationTimeoutSeconds) || args.verificationTimeoutSeconds < 1 || args.verificationTimeoutSeconds > 900) {
		throw new Error("--verification-timeout-seconds must be from 1 to 900.");
	}
	if (!Number.isFinite(args.verificationPollSeconds) || args.verificationPollSeconds < 1 || args.verificationPollSeconds > args.verificationTimeoutSeconds) {
		throw new Error("--verification-poll-seconds must be at least 1 and no greater than the verification timeout.");
	}
	if (Math.ceil(args.verificationTimeoutSeconds / args.verificationPollSeconds) + 1 > 9) {
		throw new Error("Verification polling is limited to nine login probes to stay below the production login-abuse threshold; increase --verification-poll-seconds.");
	}

	await verifyServerConfig();
	await verifyPortalHealth();
	await verifyPublicLaunchPayload();
	await verifyLauncherUpdateChannel();
	await verifyDisabledProviderSurfaces();
	await verifyLaunchPasswordContract();
	if (args.runSignup || args.pendingEmailRehearsal) {
		await verifySignupFlow();
	} else {
		warn("staged signup skipped", "No account was created because --skip-signup was used.");
	}
	await verifyWebDeployment();
}

async function verifyServerConfig() {
	if (args.skipServerConfig) {
		warn("server config skipped", "The launch config contract and SQLite backend were not checked.");
		return;
	}
	const configPath = resolve(root, args.serverConfig);
	const text = await readFile(configPath, "utf8");
	const connectionsPath = resolve(root, args.connectionsConfig);
	const connectionsText = await readFile(connectionsPath, "utf8");
	const contract = JSON.parse(await readFile(launchConfigContractPath, "utf8"));
	const expectedClientVersion = args.expectedClientVersion || await readClientVersion();
	const expected = { ...contract, client_version: expectedClientVersion };
	const values = {};
	for (const [key, expectedValue] of Object.entries(expected)) {
		const found = configValues(text, key);
		assertCheck(
			`server config ${key} unique`,
			found.length === 1,
			`${found.length} active rows in ${configPath}`
		);
		values[key] = found.length === 1 ? found[0] : "";
		assertCheck(
			`server config ${key}`,
			found.length === 1 && found[0] === expectedValue,
			`${found.length === 1 ? found[0] : "(missing or duplicate)"} vs expected ${expectedValue}`
		);
	}
	serverClientVersion = values.client_version;

	const dbTypes = configValues(connectionsText, "db_type");
	assertCheck(
		"connections config db_type unique",
		dbTypes.length === 1,
		`${dbTypes.length} active rows in ${connectionsPath}`
	);
	assertCheck(
		"connections config uses launch SQLite backend",
		dbTypes.length === 1 && dbTypes[0] === "sqlite",
		`${dbTypes.length === 1 ? dbTypes[0] : "(missing or duplicate)"} vs expected sqlite`
	);
}

async function verifyPortalHealth() {
	const { status, body } = await request("/api/health");
	assertCheck("portal health status", status === 200, `HTTP ${status}`);
	assertCheck("portal health ok", body.ok === true, JSON.stringify(body));
	assertCheck("portal health public mode", body.publicMode === true, JSON.stringify(body));
	assertCheck("portal health launch signup mode", body.launchSignupMode === true, JSON.stringify(body));
	assertCheck("portal durable storage", body.storage && body.storage.durable === true, JSON.stringify(body.storage || null));
	assertCheck("portal temp storage override off", !(body.storage && body.storage.tempDirOverride), JSON.stringify(body.storage || null));
	assertCheck("portal OpenRSC DB bridge configured", body.openRscDb && body.openRscDb.configured === true, JSON.stringify(body.openRscDb || null));
	assertCheck("portal public config ready", body.config && body.config.publicReady === true, JSON.stringify(body.config || null));
	assertCheck("portal abuse salt configured", body.config && body.config.abuseHashSaltConfigured === true, JSON.stringify(body.config || null));
	assertCheck("portal verified-email signup required", body.config && body.config.email && body.config.email.verificationRequired === true, JSON.stringify(body.config && body.config.email || null));
	assertCheck("portal recovery email delivery configured", body.config && body.config.email && body.config.email.configured === true, JSON.stringify(body.config && body.config.email || null));
}

async function verifyPublicLaunchPayload() {
	const { status, body } = await request("/api/public");
	assertCheck("public payload status", status === 200, `HTTP ${status}`);
	assertCheck("public mode", body.publicMode === true, JSON.stringify(body));
	assertCheck("launch signup mode", body.launchSignupMode === true, JSON.stringify(body));
	assertCheck("launch timestamp", iso(body.launch && body.launch.openAt) === iso(args.launchAt || "2026-07-18T18:00:00.000Z"), String(body.launch && body.launch.openAt));
	assertCheck("hybrid registration contract", body.worldRules && body.worldRules.registration === "hybrid", JSON.stringify(body.worldRules || null));
	assertCheck("web registration portal-first", body.worldRules && body.worldRules.webRegistration === "portal-first", JSON.stringify(body.worldRules || null));
	assertCheck("desktop packet registration", body.worldRules && body.worldRules.desktopRegistration === "packet", JSON.stringify(body.worldRules || null));
	assertCheck("Android registration policy-gated", body.worldRules && body.worldRules.nativeAndroidRegistration === "portal-first", JSON.stringify(body.worldRules || null));
	assertCheck("desktop packet registration enabled", body.worldRules && body.worldRules.packetRegistration === true, JSON.stringify(body.worldRules || null));
	assertCheck("current community terms", body.worldRules && body.worldRules.communityTermsVersion === "2026-07-16" && body.worldRules.communityTermsUrl === "/community-rules", JSON.stringify(body.worldRules || null));
	assertCheck("hybrid member world", body.worldRules && body.worldRules.memberWorld === true, JSON.stringify(body.worldRules || null));
	assertCheck("subscription does not grant members", body.worldRules && body.worldRules.subscriptionGrantsMembers === false, JSON.stringify(body.worldRules || null));
	assertCheck("no fake public player count", body.status && body.status.playersOnline === 0, JSON.stringify(body.status || null));
	const google = body.oauth && body.oauth.google || {};
	if (args.allowGoogle) {
		assertCheck("google configured intentionally", google.enabled === true && Boolean(google.clientId), JSON.stringify(google));
	} else {
		assertCheck("google hidden", google.enabled === false && !google.clientId, JSON.stringify(google));
	}
	const downloads = Array.isArray(body.downloads) ? body.downloads : [];
	const android = downloads.find((row) => row && row.slug === "android-apk") || {};
	assertCheck("android APK row present", Boolean(android.slug), JSON.stringify(android));
	if (android.available === true) {
		assertCheck("android APK public link", Boolean(android.url) && android.url !== "#", JSON.stringify(android));
		assertCheck("android APK hash", /^[0-9a-f]{64}$/.test(android.sha256 || ""), JSON.stringify(android));
	}
}

async function verifyLauncherUpdateChannel() {
	const manifest = await request("/api/launcher/manifest.properties", { expectJson: false });
	assertCheck("launcher manifest status", manifest.status === 200, `HTTP ${manifest.status}`);
	if (manifest.status !== 200) return;

	const values = parsePropertiesText(manifest.text);
	const files = [];
	for (let index = 1; values[`file.${index}.path`]; index += 1) {
		files.push({
			key: `file.${index}`,
			path: values[`file.${index}.path`],
			sha256: values[`file.${index}.sha256`] || "",
			url: values[`file.${index}.url`] || "",
			size: values[`file.${index}.size`] || ""
		});
	}

	assertCheck("launcher manifest version", Boolean(values.version), String(values.version || ""));
	assertCheck("launcher manifest baseUrl", Boolean(values.baseUrl) && channelUrlOk(values.baseUrl), String(values.baseUrl || ""));
	assertCheck("launcher manifest file entries", files.length > 0, `${files.length} contiguous file.N.path entries`);
	const requiredCachePaths = [
		"MD5.SUM",
		"video/Authentic_Sprites.orsc",
		"video/Custom_Landscape.orsc",
		"video/models.orsc",
		"worldmap/plane-0.png",
		"worldmap/plane-1.png",
		"worldmap/plane-2.png",
		"worldmap/plane-3.png"
	];
	const unsafePaths = files.filter((file) => /(^|\/)(?:[^/]+\.(?:bak|tmp)|[^/]+~)$/i.test(file.path)).map((file) => file.path);
	assertCheck("launcher manifest excludes backup files", unsafePaths.length === 0, unsafePaths.join(", ") || "no backup or temporary paths");
	const manifestPaths = new Set(files.map((file) => file.path));
	const missingCachePaths = requiredCachePaths.filter((path) => !manifestPaths.has(path));
	assertCheck(
		"launcher manifest launch cache",
		missingCachePaths.length === 0,
		missingCachePaths.length ? `missing ${missingCachePaths.join(", ")}` : "landscape, models, and all world-map planes present"
	);

	const badHashes = files.filter((file) => !/^[0-9a-f]{64}$/i.test(file.sha256)).map((file) => `${file.key}.sha256`);
	if (values["launcher.sha256"] && !/^[0-9a-f]{64}$/i.test(values["launcher.sha256"])) badHashes.push("launcher.sha256");
	assertCheck("launcher manifest sha256 format", badHashes.length === 0, badHashes.join(", ") || "all sha256 values are present 64-hex strings");

	const badSizes = files.filter((file) => !/^\d+$/.test(file.size)).map((file) => `${file.key}.size`);
	if (values["launcher.size"] && !/^\d+$/.test(values["launcher.size"])) badSizes.push("launcher.size");
	assertCheck("launcher manifest sizes", badSizes.length === 0, badSizes.join(", ") || "all v2 size values are present non-negative integers");

	const badUrls = files.filter((file) => file.url && !channelUrlOk(file.url)).map((file) => `${file.key}.url`);
	if (values["launcher.url"] && !channelUrlOk(values["launcher.url"])) badUrls.push("launcher.url");
	assertCheck("launcher manifest urls https", badUrls.length === 0, badUrls.join(", ") || "all explicit urls are https (or allowed local http)");

	if (values.clientVersion) {
		const expected = serverClientVersion || args.expectedClientVersion || await readClientVersion();
		assertCheck("launcher manifest client version", values.clientVersion === expected, `${values.clientVersion} vs expected ${expected}`);
	} else {
		warn("launcher manifest client version absent", "Pre-v2 manifest: clientVersion cannot be cross-checked against the server config.");
	}

	if (!values["launcher.sha256"] && !values["launcher.url"]) {
		warn("launcher self-update absent", "Manifest has no launcher.sha256/launcher.url; this channel does not offer launcher self-update.");
	} else {
		assertCheck(
			"launcher self-update pair",
			Boolean(values["launcher.sha256"]) && Boolean(values["launcher.url"]) && Boolean(values["launcher.size"]),
			`launcher.sha256=${values["launcher.sha256"] || "(absent)"} launcher.url=${values["launcher.url"] || "(absent)"} launcher.size=${values["launcher.size"] || "(absent)"}`
		);
	}

	for (const file of files) {
		const target = file.url || `${values.baseUrl || ""}${file.path}`;
		if (channelUrlOk(target)) {
			await probeChannelUrl(`launcher manifest file reachable: ${file.path}`, target, file.size);
		} else {
			fail(`launcher manifest file reachable: ${file.path}`, `Unresolvable file URL: ${target}`);
		}
	}
	const criticalPaths = new Set(["Open_RSC_Client.jar", ...requiredCachePaths]);
	for (const file of files.filter((entry) => criticalPaths.has(entry.path))) {
		const target = file.url || `${values.baseUrl || ""}${file.path}`;
		if (channelUrlOk(target)) {
			await verifyChannelArtifact(file.path, target, file.size, file.sha256);
		}
	}
	if (values["launcher.url"] && channelUrlOk(values["launcher.url"])) {
		await probeChannelUrl("launcher self-update jar reachable", values["launcher.url"], values["launcher.size"]);
		await verifyChannelArtifact("launcher self-update jar", values["launcher.url"], values["launcher.size"], values["launcher.sha256"]);
	}
}

async function probeChannelUrl(name, urlText, expectedSize) {
	const identityHeaders = { "accept-encoding": "identity" };
	const head = await request(urlText, { method: "HEAD", headers: identityHeaders, expectJson: false });
	if (head.status === 200 || head.status === 206) {
		const actualSize = responseObjectSize(head, head.status === 206);
		assertCheck(name, actualSize === Number(expectedSize), `HEAD ${head.status}; ${actualSize} bytes vs manifest ${expectedSize}; ${urlText}`);
		return;
	}
	if (head.status === 405 || head.status === 501) {
		const ranged = await request(urlText, {
			method: "GET",
			headers: { ...identityHeaders, range: "bytes=0-0" },
			expectJson: false
		});
		const actualSize = responseObjectSize(ranged, ranged.status === 206);
		assertCheck(
			name,
			(ranged.status === 200 || ranged.status === 206) && actualSize === Number(expectedSize),
			`HEAD ${head.status}, ranged GET ${ranged.status}; ${actualSize} bytes vs manifest ${expectedSize}; ${urlText}`
		);
		return;
	}
	fail(name, `HEAD ${head.status} ${urlText}`);
}

function responseObjectSize(response, partial) {
	if (partial) {
		const match = /\/([0-9]+)$/.exec(response.headers["content-range"] || "");
		if (match) return Number(match[1]);
	}
	const length = response.headers["content-length"];
	return /^\d+$/.test(length || "") ? Number(length) : -1;
}

async function verifyChannelArtifact(path, urlText, expectedSize, expectedSha256) {
	const response = await request(urlText, {
		method: "GET",
		headers: { "accept-encoding": "identity" },
		expectJson: false,
		binary: true
	});
	const bytes = response.bytes || Buffer.alloc(0);
	const actualSha256 = createHash("sha256").update(bytes).digest("hex");
	assertCheck(
		`launcher artifact bytes: ${path}`,
		response.status === 200
			&& bytes.length === Number(expectedSize)
			&& actualSha256 === expectedSha256,
		`HTTP ${response.status}; ${bytes.length} bytes vs ${expectedSize}; sha256 ${actualSha256} vs ${expectedSha256}`
	);
}

async function verifyDisabledProviderSurfaces() {
	const payment = await request("/api/payments/subscription-cards/checkout", {
		method: "POST",
		body: {}
	});
	const paymentDisabled = (payment.status === 404 && payment.body.error === "not_available_during_prelaunch")
		|| (payment.status === 501 && payment.body.error === "payments_not_configured");
	if (args.allowPayment) {
		warn("payment disabled check bypassed", `Payment endpoint returned HTTP ${payment.status}.`);
	} else {
		assertCheck("payment disabled or hidden", paymentDisabled, `HTTP ${payment.status} ${JSON.stringify(payment.body)}`);
	}

	if (!args.allowGoogle) {
		const googleNonce = await request("/api/oauth/google/nonce", {
			method: "POST",
			body: {}
		});
		assertCheck(
			"google nonce disabled",
			googleNonce.status === 503 && googleNonce.body.error === "google_oauth_not_configured",
			`HTTP ${googleNonce.status} ${JSON.stringify(googleNonce.body)}`
		);
	}

	for (const publicBase of args.adminPublicUrls) {
		const adminUrl = new URL("/api/admin/signups", publicBase).href;
		const label = new URL(publicBase).host;
		const adminWithoutToken = await request(adminUrl, { expectJson: false });
		const adminWithProbeToken = await request(adminUrl, {
			headers: { "x-portal-admin-token": "external-verifier-probe-not-a-real-token" },
			expectJson: false
		});
		if (args.allowLocalAdminTokenGuard) {
			assertCheck(
				`local admin guard: ${label}`,
				[403, 404, 503].includes(adminWithoutToken.status)
					&& [403, 404, 503].includes(adminWithProbeToken.status),
				`without token HTTP ${adminWithoutToken.status}; probe token HTTP ${adminWithProbeToken.status}`
			);
			if (args.adminToken) {
				const adminWithToken = await request(adminUrl, {
					headers: { "x-portal-admin-token": args.adminToken }
				});
				assertCheck(
					`local admin endpoint accepts configured token: ${label}`,
					adminWithToken.status === 200 && Array.isArray(adminWithToken.body.signups || adminWithToken.body.founders),
					`HTTP ${adminWithToken.status}`
				);
			}
		} else {
			assertCheck(
				`public admin route externally blocked: ${label}`,
				adminWithoutToken.status === 404 && adminWithProbeToken.status === 404,
				`without token HTTP ${adminWithoutToken.status}; probe token HTTP ${adminWithProbeToken.status}; expected 404/404`
			);
		}
	}
}

async function verifyLaunchPasswordContract() {
	const badUsername = clipUsername(`${signupUser}Bad`);
	for (const [label, password] of [
		["symbols", "Bad!Pass1"],
		["spaces", "Bad Pass1"]
	]) {
		const response = await request("/api/accounts/register", {
			method: "POST",
			body: {
				username: badUsername,
				email: `staging-${badUsername.toLowerCase()}@voidscape.gg`,
				password,
				termsAccepted: true,
				termsVersion: "2026-07-16"
			}
		});
		assertCheck(
			`launch password rejects ${label} with invalid_game_password`,
			response.status === 400 && response.body.error === "invalid_game_password",
			`HTTP ${response.status} ${JSON.stringify(response.body)}`
		);
	}
	for (const [label, password] of [
		["control characters", "Bad\nPass1"],
		["Unicode", "Bad💥Pass1"],
		["pound sign", "Bad£Pass1"],
		["backtick", "Bad`Pass1"]
	]) {
		assertCheck(`launch password rejects ${label}`, !isGamePassword(password, 8), JSON.stringify(password));
	}
	assertCheck("launch password accepts letters and numbers", isGamePassword("Launchpass1", 8), "Launchpass1");
}

async function verifySignupFlow() {
	if (!/^[A-Za-z0-9 ]{1,12}$/.test(signupUser)) {
		throw new Error(`Signup username must be 1-12 client-safe characters: ${signupUser}`);
	}
	if (!isGamePassword(signupPassword, 8)) {
		throw new Error("Signup password must be 8-20 letters and numbers only. Symbols and spaces are not allowed.");
	}
	const signup = await request("/api/accounts/register", {
		method: "POST",
		body: {
			username: signupUser,
			email: signupEmail,
			password: signupPassword,
			termsAccepted: true,
			termsVersion: "2026-07-16"
		}
	});
	assertCheck("staged signup awaits verified email", signup.status === 202 && signup.body.verificationRequired === true && !signup.body.token, `HTTP ${signup.status} ${safeJson(signup.body)}`);
	if (signup.status !== 202) return;
	if (args.pendingEmailRehearsal) {
		warn("non-final signup rehearsal pending", `The message sent to ${signupEmail} was not verified; this mode is intentionally not final release evidence.`);
		return;
	}
	console.log(
		`Signup is pending for ${signupEmail}. Open the delivered email and complete verification within ${args.verificationTimeoutSeconds} seconds; this verifier will poll every ${args.verificationPollSeconds} seconds.`
	);
	await waitForExactSignupVerification();
}

async function waitForExactSignupVerification() {
	const timeoutMs = args.verificationTimeoutSeconds * 1000;
	const pollMs = args.verificationPollSeconds * 1000;
	const deadline = Date.now() + timeoutMs;
	let attempts = 0;
	let lastStatus = 0;

	while (attempts < 9 && Date.now() <= deadline) {
		attempts += 1;
		const login = await request("/api/accounts/login", {
			method: "POST",
			body: {
				email: signupEmail,
				password: signupPassword
			}
		});
		lastStatus = login.status;
		if (login.status === 200) {
			assertCheck("exact verified signup login issued token", Boolean(login.body.token), safeJson({ token: login.body.token }));
			const loginValid = assertExactVerifiedSignupPayload("exact verified signup login", login.body);
			if (!login.body.token) return;

			const account = await request("/api/account", {
				headers: { authorization: `Bearer ${login.body.token}` }
			});
			assertCheck("exact verified signup readback status", account.status === 200, `HTTP ${account.status}`);
			const readbackValid = assertExactVerifiedSignupPayload("exact verified signup readback", account.body);
			if (loginValid && readbackValid && account.status === 200) signupReadyForGameSmoke = true;
			return;
		}
		if (login.status !== 401) {
			fail("exact signup verification login", `Unexpected HTTP ${login.status} after ${attempts} attempt(s); expected 401 while pending or 200 after verification.`);
			return;
		}

		const remaining = deadline - Date.now();
		if (remaining <= 0 || attempts >= 9) break;
		await delay(Math.min(pollMs, remaining));
	}

	fail(
		"exact signup verification completed",
		`The exact signup ${signupEmail} did not become a verified, active, linked account within ${args.verificationTimeoutSeconds}s (${attempts} login probes; last HTTP ${lastStatus}). Complete the delivered email verification and rerun.`
	);
}

function assertExactVerifiedSignupPayload(name, body) {
	const characters = Array.isArray(body.characters) ? body.characters : [];
	const linked = characters.filter((character) =>
		character
		&& character.name === signupUser
		&& character.linkStatus === "linked"
		&& Number.isInteger(character.playerId)
		&& character.source === "openrsc-sqlite-created"
	);
	const active = Boolean(body.account)
		&& String(body.account.email || "").toLowerCase() === signupEmail.toLowerCase()
		&& body.account.status === "active";
	const verified = Boolean(body.security) && body.security.emailVerified === true;
	assertCheck(`${name}: exact active email account`, active, safeJson(body.account || null));
	assertCheck(`${name}: email verified`, verified, safeJson(body.security || null));
	const exactCharacter = characters.length === 1 && linked.length === 1;
	assertCheck(`${name}: exact linked OpenRSC character`, exactCharacter, safeJson(characters));
	return active && verified && exactCharacter;
}

async function verifyWebDeployment() {
	if (args.skipWebVerify) {
		warn("web deployment skipped", "The /play static root and WSS smoke were not checked.");
		return;
	}
	const manifest = resolve(root, args.expectedBuildManifest || "dist/web-teavm/voidscape-web-build.json");
	assertCheck("expected web build manifest exists", existsSync(manifest), manifest);
	if (!existsSync(manifest)) return;

	const outDir = resolve(args.out, "web-deployment");
	const command = [
		resolve(root, "scripts/verify-web-teavm-deployment.sh"),
		args.webUrl,
		"--expected-build-manifest", manifest,
		"--deep-manifest",
		"--portal", args.portalUrl.href,
		"--out", outDir
	];
	if (args.wsUrl) command.push("--ws", args.wsUrl);
	if (args.allowHttp) command.push("--allow-http");
	if (args.insecure) command.push("--insecure");
	if (!args.skipWebSmoke) {
		const smokeUser = args.qaUser || (signupReadyForGameSmoke ? signupUser : "");
		const smokePass = args.qaPass || (signupReadyForGameSmoke ? signupPassword : "");
		if (smokeUser && smokePass) {
			command.push("--smoke", "--user", smokeUser, "--pass", smokePass);
			if (args.chrome) command.push("--chrome", args.chrome);
			if (args.playwrightCore) command.push("--playwright-core", args.playwrightCore);
		} else {
			warn("web smoke skipped", "No QA credentials were provided and no staged signup was run.");
		}
	} else {
		warn("web smoke skipped", "--no-web-smoke was used.");
	}

	const result = spawnSync(command[0], command.slice(1), {
		cwd: root,
		encoding: "utf8",
		maxBuffer: 1024 * 1024 * 20
	});
	await mkdir(outDir, { recursive: true });
	await writeFile(resolve(outDir, "verify-command.txt"), `${redactedCommand(command).map(shellQuote).join(" ")}\n`);
	await writeFile(resolve(outDir, "stdout.log"), result.stdout || "");
	await writeFile(resolve(outDir, "stderr.log"), result.stderr || "");
	assertCheck("web deployment verifier", result.status === 0, `exit ${result.status}; see ${outDir}`);

	const summaryPath = resolve(outDir, "summary.json");
	if (existsSync(summaryPath)) {
		const summary = JSON.parse(await readFile(summaryPath, "utf8"));
		assertCheck("web manifest matches expected", summary.buildManifestMatchesExpected === true, JSON.stringify(summary));
		assertCheck("web deep manifest verified", summary.deepManifestChecked === true && summary.deepManifestFailureCount === 0 && summary.deepManifestVerifiedCount === summary.deepManifestFileCount, JSON.stringify(summary));
		if (!args.allowHttp) {
			assertCheck("web cache policy verified", summary.cachePolicyChecked === true && summary.cachePolicyFailureCount === 0, JSON.stringify(summary));
		}
		if (!args.skipWebSmoke && (args.qaUser || signupReadyForGameSmoke)) {
			assertCheck("web smoke passed", summary.smokeRan === true && summary.smokePassed === true, JSON.stringify(summary));
		}
	}
}

async function request(path, options = {}) {
	const url = new URL(path, args.portalUrl);
	const headers = { ...(options.headers || {}) };
	let body;
	if (options.body !== undefined) {
		headers["content-type"] = "application/json";
		body = JSON.stringify(options.body);
	}
	const response = await fetch(url, {
		method: options.method || "GET",
		headers,
		body
	});
	const responseHeaders = Object.fromEntries(response.headers.entries());
	let bytes = null;
	let text = "";
	let parsed;
	if (options.binary) {
		bytes = Buffer.from(await response.arrayBuffer());
		parsed = {
			binaryBytes: bytes.length,
			sha256: createHash("sha256").update(bytes).digest("hex")
		};
	} else {
		text = await response.text();
		parsed = text;
	}
	if (!options.binary && options.expectJson !== false) {
		try {
			parsed = text ? JSON.parse(text) : {};
		} catch {
			parsed = { raw: text };
		}
	}
	const artifactName = safeArtifactName(`${options.method || "GET"}-${url.host}-${url.pathname}`);
	await writeFile(resolve(args.out, `${artifactName}-${Date.now()}.json`), JSON.stringify({
		url: url.href,
		status: response.status,
		headers: responseHeaders,
		body: redactSensitive(parsed)
	}, null, 2));
	return { status: response.status, body: parsed, text, bytes, headers: responseHeaders };
}

function parseArgs(argv) {
	const parsed = {
		out: defaultOut,
		launchAt: "2026-07-18T18:00:00.000Z",
		adminPublicUrls: [],
		verificationTimeoutSeconds: 240,
		verificationPollSeconds: 30
	};
	for (let i = 0; i < argv.length; i += 1) {
		const arg = argv[i];
		const value = () => {
			if (i + 1 >= argv.length) throw new Error(`Missing value for ${arg}`);
			i += 1;
			return argv[i];
		};
		switch (arg) {
			case "--help":
			case "-h":
				parsed.help = true;
				break;
			case "--portal":
			case "--portal-url":
				parsed.portalUrl = value();
				break;
			case "--web":
			case "--web-url":
				parsed.webUrl = value();
				break;
			case "--ws":
				parsed.wsUrl = value();
				break;
			case "--out":
				parsed.out = value();
				break;
			case "--server-config":
				parsed.serverConfig = value();
				break;
			case "--connections-config":
				parsed.connectionsConfig = value();
				break;
			case "--expected-build-manifest":
				parsed.expectedBuildManifest = value();
				break;
			case "--expected-client-version":
				parsed.expectedClientVersion = value();
				break;
			case "--launch-at":
				parsed.launchAt = value();
				break;
			case "--signup-username":
				parsed.signupUsername = value();
				break;
			case "--signup-email":
				parsed.signupEmail = value();
				break;
			case "--signup-password":
				parsed.signupPassword = value();
				break;
			case "--verification-timeout-seconds":
				parsed.verificationTimeoutSeconds = Number(value());
				break;
			case "--verification-poll-seconds":
				parsed.verificationPollSeconds = Number(value());
				break;
			case "--qa-user":
			case "--user":
				parsed.qaUser = value();
				break;
			case "--qa-pass":
			case "--pass":
				parsed.qaPass = value();
				break;
			case "--admin-token":
				parsed.adminToken = value();
				break;
			case "--admin-public-url":
				parsed.adminPublicUrls.push(value());
				break;
			case "--chrome":
				parsed.chrome = value();
				break;
			case "--playwright-core":
				parsed.playwrightCore = value();
				break;
			case "--run-signup":
				parsed.runSignup = true;
				break;
			case "--pending-email-rehearsal":
				parsed.pendingEmailRehearsal = true;
				break;
			case "--skip-signup":
				parsed.skipSignup = true;
				break;
			case "--skip-server-config":
				parsed.skipServerConfig = true;
				break;
			case "--server-config-only":
				parsed.serverConfigOnly = true;
				break;
			case "--skip-web-verify":
				parsed.skipWebVerify = true;
				break;
			case "--no-web-smoke":
				parsed.skipWebSmoke = true;
				break;
			case "--allow-http":
				parsed.allowHttp = true;
				break;
			case "--insecure":
				parsed.insecure = true;
				break;
			case "--allow-google":
				parsed.allowGoogle = true;
				break;
			case "--allow-payment":
				parsed.allowPayment = true;
				break;
			case "--allow-local-admin-token-guard":
				parsed.allowLocalAdminTokenGuard = true;
				break;
			case "--allow-android-apk":
				parsed.allowAndroidApk = true;
				break;
			default:
				throw new Error(`Unknown option: ${arg}`);
		}
	}
	return parsed;
}

function usage() {
	console.log(`Usage: scripts/verify-launch-staging.mjs --portal-url URL --web-url URL --server-config FILE --connections-config FILE (--run-signup|--pending-email-rehearsal|--skip-signup) [options]

Verifies the production-like launch gate for a staged Voidscape deployment:
  - portal health uses public launch-signup mode, durable storage, and an OpenRSC DB bridge
  - /api/public exposes the desktop-packet / web-and-Android-portal registration split, current Community Rules, hybrid member world, and Google hidden by default
  - /api/launcher/manifest.properties is a well-formed update channel (required keys,
    64-hex sha256 values, https URLs, clientVersion matching the server config) and its
	    every file entry has the declared hosted size; launch-critical files and the launcher self-update jar are downloaded and SHA-256 verified
  - payment and Google provider surfaces are disabled/hidden unless explicitly allowed
  - every supplied public host externally returns 404 for /api/admin/* with and without a fake token
  - launch signup accepts the current Community Rules, rejects invalid game passwords, and, with --run-signup, waits for delivered-email verification before proving the exact linked account
  - /play static deployment matches dist/web-teavm/voidscape-web-build.json and optionally runs web login smoke
  - deployed server config copy has exactly one of every value in the reviewed
    launch contract; deployed connections config selects the SQLite launch backend

Required for the full gate:
  --portal-url URL              Staged portal URL, e.g. https://staging.voidscape.gg/
  --web-url URL                 Staged web client URL, e.g. https://staging.voidscape.gg/play/
  --server-config FILE          Local copy of the deployed server config.
  --connections-config FILE     Local copy of the deployed connections config.
  --run-signup                  Final gate: initiate the explicit signup below,
                                wait for its email verification, then prove login.

Useful options:
  --ws URL                      WSS URL for web smoke.
  --expected-build-manifest FILE Default: dist/web-teavm/voidscape-web-build.json.
  --signup-username NAME        Exact new QA character; required for --run-signup.
  --signup-email EMAIL          Exact delivered-email address; required for --run-signup.
  --signup-password PASSWORD    Exact portal/game password; required for --run-signup.
  --verification-timeout-seconds N  Bounded email-completion wait. Default: 240.
  --verification-poll-seconds N     Login-probe interval. Default: 30; max 9 probes.
  --admin-public-url URL        Public origin whose /api/admin/* must return 404;
                                repeat for non-production aliases.
  --admin-token TOKEN           Loopback-only proof; requires --allow-local-admin-token-guard.
  --no-web-smoke                Static/deep web verification only.
  --skip-web-verify             Portal/config rehearsal only.
  --skip-server-config          Do not check the launch config contract or DB backend.
  --server-config-only          Check both deployed config copies without network requests.
  --skip-signup                 Non-mutating dry gate.
  --pending-email-rehearsal     Non-final: initiate verification but permit it to remain pending.
  --allow-local-admin-token-guard  Loopback fixture only; permits the backend token guard instead of external 404.
  --allow-google                Expect Google to be intentionally configured.
  --allow-payment               Do not fail if payment endpoint is configured.
  --allow-android-apk           Accepted for older package scripts; Android is verified from /api/public.
  --allow-http                  Permit http:// for local fixtures.
  --insecure                    Pass --insecure to the web deployment verifier.
  --out DIR                     Default: ${defaultOut}.
`);
}

function pass(name, detail = "") {
	checks.push({ name, status: "pass", detail });
}

function warn(name, detail = "") {
	warnings.push({ name, detail });
	checks.push({ name, status: "warning", detail });
}

function fail(name, detail = "") {
	failures.push({ name, detail });
	checks.push({ name, status: "fail", detail });
}

function assertCheck(name, condition, detail = "") {
	if (condition) pass(name, detail);
	else fail(name, detail);
}

async function writeSummary() {
	if (!args.out) args.out = resolve(root, defaultOut);
	await mkdir(args.out, { recursive: true });
	await writeFile(resolve(args.out, "summary.json"), JSON.stringify({
		startedAt: startedAt.toISOString(),
		finishedAt: new Date().toISOString(),
		portalUrl: args.portalUrl && args.portalUrl.href || args.portalUrl || "",
		adminPublicUrls: args.adminPublicUrls || [],
		webUrl: args.webUrl || "",
		wsUrl: args.wsUrl || "",
		serverConfig: args.serverConfig || "",
		connectionsConfig: args.connectionsConfig || "",
		serverConfigOnly: Boolean(args.serverConfigOnly),
		signupMode: args.runSignup ? "final" : args.pendingEmailRehearsal ? "pending-email-rehearsal" : "skipped",
		signup: args.runSignup || args.pendingEmailRehearsal ? {
			username: signupUser,
			email: signupEmail,
			exactSignupVerified: checks.some((check) => check.name === "exact verified signup readback: email verified" && check.status === "pass")
		} : null,
		checks,
		warnings,
		failures
	}, null, 2));
}

function normalizeBaseUrl(value, label) {
	const url = new URL(value);
	if (url.search || url.hash) throw new Error(`${label} URL must not include query or fragment.`);
	if (!url.pathname.endsWith("/")) url.pathname += "/";
	return url;
}

function isLoopbackUrl(value) {
	const hostname = new URL(value).hostname.toLowerCase();
	return hostname === "127.0.0.1" || hostname === "localhost" || hostname === "::1" || hostname === "[::1]";
}

function parsePropertiesText(text) {
	const values = {};
	for (const rawLine of String(text || "").split(/\r?\n/)) {
		const line = rawLine.trim();
		if (!line || line.startsWith("#") || line.startsWith("!")) continue;
		const separator = line.indexOf("=");
		if (separator <= 0) continue;
		values[line.slice(0, separator).trim()] = line.slice(separator + 1).trim();
	}
	return values;
}

function channelUrlOk(value) {
	let url;
	try {
		url = new URL(String(value || ""));
	} catch {
		return false;
	}
	if (url.protocol === "https:") return true;
	return url.protocol === "http:"
		&& Boolean(args.allowHttp)
		&& (url.hostname === "127.0.0.1" || url.hostname === "localhost");
}

function configValues(text, key) {
	const pattern = new RegExp(`^[\\t ]*${escapeRegExp(key)}[\\t ]*:[\\t ]*([^#\\r\\n]*)`, "gm");
	return [...text.matchAll(pattern)].map((match) => match[1].trim());
}

async function readClientVersion() {
	const config = await readFile(resolve(root, "Client_Base/src/orsc/Config.java"), "utf8");
	const match = config.match(/CLIENT_VERSION\s*=\s*([0-9]+)/);
	if (!match) throw new Error("Unable to read Client_Base CLIENT_VERSION.");
	return match[1];
}

function generatedUsername() {
	return clipUsername(`Stg${Date.now().toString(36).slice(-8)}`);
}

function clipUsername(value) {
	return String(value || "").replace(/[^A-Za-z0-9 ]/g, "").slice(0, 12) || "StagingUser";
}

function isGamePassword(value, minimumLength) {
	const text = String(value || "");
	return text.length >= minimumLength
		&& text.length <= 20
		&& /^[A-Za-z0-9]+$/.test(text);
}

function iso(value) {
	if (!value) return "";
	return new Date(value).toISOString();
}

function delay(milliseconds) {
	return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

function stamp(date) {
	return date.toISOString().replace(/[-:]/g, "").replace(/\..+$/, "").replace("T", "-");
}

function safeArtifactName(value) {
	return String(value).replace(/[^A-Za-z0-9_.-]+/g, "-").replace(/^-+|-+$/g, "").slice(0, 80) || "request";
}

function shellQuote(value) {
	const text = String(value);
	if (/^[A-Za-z0-9_./:=@+-]+$/.test(text)) return text;
	return `'${text.replace(/'/g, "'\\''")}'`;
}

function safeJson(value) {
	return JSON.stringify(redactSensitive(value));
}

function redactSensitive(value) {
	if (Array.isArray(value)) return value.map((item) => redactSensitive(item));
	if (typeof value === "string") return redactSensitiveString(value);
	if (!value || typeof value !== "object") return value;
	const redacted = {};
	for (const [key, item] of Object.entries(value)) {
		if (/^(token|sessionToken|session|authorization|password)$/i.test(key)) {
			redacted[key] = "[redacted]";
		} else {
			redacted[key] = redactSensitive(item);
		}
	}
	return redacted;
}

function redactSensitiveString(value) {
	return String(value)
		.replace(/("(?:token|sessionToken|session|authorization|password)"\s*:\s*")[^"]+"/gi, "$1[redacted]\"")
		.replace(/((?:token|sessionToken|session|authorization|password)=)[^\s&]+/gi, "$1[redacted]");
}

function redactedCommand(command) {
	const redactNext = new Set(["--pass", "--qa-pass", "--signup-password", "--admin-token"]);
	const redacted = [];
	let redact = false;
	for (const part of command) {
		if (redact) {
			redacted.push("[redacted]");
			redact = false;
			continue;
		}
		redacted.push(part);
		if (redactNext.has(part)) redact = true;
	}
	return redacted;
}

function escapeRegExp(value) {
	return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
