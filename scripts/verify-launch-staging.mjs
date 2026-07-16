#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
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
	if (!args.runSignup && !args.skipSignup) {
		throw new Error("Choose --run-signup for the real staged signup, or --skip-signup for a non-mutating dry gate.");
	}
	if (args.runSignup && args.skipSignup) {
		throw new Error("--run-signup and --skip-signup are mutually exclusive.");
	}

	await verifyServerConfig();
	await verifyPortalHealth();
	await verifyPublicLaunchPayload();
	await verifyLauncherUpdateChannel();
	await verifyDisabledProviderSurfaces();
	await verifyLaunchPasswordContract();
	if (args.runSignup) {
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
	assertCheck("portal-first registration", body.worldRules && body.worldRules.registration === "portal-first", JSON.stringify(body.worldRules || null));
	assertCheck("public web packet registration off", body.worldRules && body.worldRules.packetRegistration === false, JSON.stringify(body.worldRules || null));
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
			await probeChannelUrl(`launcher manifest file reachable: ${file.path}`, target);
		} else {
			fail(`launcher manifest file reachable: ${file.path}`, `Unresolvable file URL: ${target}`);
		}
	}
	if (values["launcher.url"] && channelUrlOk(values["launcher.url"])) {
		await probeChannelUrl("launcher self-update jar reachable", values["launcher.url"]);
	}
}

async function probeChannelUrl(name, urlText) {
	const head = await request(urlText, { method: "HEAD", expectJson: false });
	if (head.status === 200 || head.status === 206) {
		pass(name, `HEAD ${head.status} ${urlText}`);
		return;
	}
	if (head.status === 405 || head.status === 501) {
		const ranged = await request(urlText, {
			method: "GET",
			headers: { range: "bytes=0-0" },
			expectJson: false
		});
		assertCheck(name, ranged.status === 200 || ranged.status === 206, `HEAD ${head.status}, ranged GET ${ranged.status} ${urlText}`);
		return;
	}
	fail(name, `HEAD ${head.status} ${urlText}`);
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

	const adminWithoutToken = await request("/api/admin/signups", {
		expectJson: false
	});
	assertCheck(
		"admin endpoint protected or disabled",
		adminWithoutToken.status === 403 || adminWithoutToken.status === 503,
		`HTTP ${adminWithoutToken.status}`
	);
	if (args.adminToken) {
		const adminWithToken = await request("/api/admin/signups", {
			headers: { "x-portal-admin-token": args.adminToken }
		});
		assertCheck("admin endpoint accepts configured token", adminWithToken.status === 200 && Array.isArray(adminWithToken.body.signups || adminWithToken.body.founders), `HTTP ${adminWithToken.status}`);
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
				password
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
	signupUser = clipUsername(signupUser);
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
			password: signupPassword
		}
	});
	assertCheck("staged signup status", signup.status === 201 || signup.status === 202, `HTTP ${signup.status} ${safeJson(signup.body)}`);
	if (signup.status === 202) {
		assertCheck("staged signup awaits verified email", signup.body.verificationRequired === true && !signup.body.token, safeJson(signup.body));
		warn("staged signup verification pending", `Open the message sent to ${signupEmail}, complete verification, then run the web login smoke with that character's credentials.`);
		return;
	}
	if (signup.status !== 201) return;
	assertCheck("staged signup issued token", Boolean(signup.body.token), safeJson({ token: signup.body.token }));
	assertAccountPayload("staged signup account payload", signup.body);
	signupReadyForGameSmoke = true;

	const token = signup.body.token;
	const account = await request("/api/account", {
		headers: { authorization: `Bearer ${token}` }
	});
	assertCheck("created account readback status", account.status === 200, `HTTP ${account.status}`);
	assertAccountPayload("created account readback", account.body);

	const login = await request("/api/accounts/login", {
		method: "POST",
		body: {
			email: signupEmail,
			password: signupPassword
		}
	});
	assertCheck("created account login status", login.status === 200, `HTTP ${login.status}`);
	assertCheck("created account login issued token", Boolean(login.body.token), safeJson({ token: login.body.token }));
	assertAccountPayload("created account login payload", login.body);
}

function assertAccountPayload(name, body) {
	const characters = Array.isArray(body.characters) ? body.characters : [];
	const first = characters[0] || {};
	assertCheck(`${name}: email`, body.account && body.account.email === signupEmail, safeJson(body.account || null));
	assertCheck(`${name}: one used character slot`, characters.length === 1, safeJson(characters));
	assertCheck(`${name}: first character name`, first.name === signupUser, safeJson(first));
	assertCheck(`${name}: real OpenRSC save`, first.source === "openrsc-sqlite-created", safeJson(first));
	assertCheck(`${name}: linked save`, first.linkStatus === "linked" && Number.isInteger(first.playerId), safeJson(first));
	assertCheck(`${name}: starter card waiting`, body.rewards && body.rewards.starterCardStatus === "waiting", safeJson(body.rewards || null));
	assertCheck(`${name}: one starter card`, body.rewards && body.rewards.starterSubscriptionCards === 1, safeJson(body.rewards || null));
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
	const text = await response.text();
	let parsed = text;
	if (options.expectJson !== false) {
		try {
			parsed = text ? JSON.parse(text) : {};
		} catch {
			parsed = { raw: text };
		}
	}
	const artifactName = safeArtifactName(`${options.method || "GET"}-${url.pathname}`);
	await writeFile(resolve(args.out, `${artifactName}-${Date.now()}.json`), JSON.stringify({
		url: url.href,
		status: response.status,
		body: redactSensitive(parsed)
	}, null, 2));
	return { status: response.status, body: parsed, text };
}

function parseArgs(argv) {
	const parsed = {
		out: defaultOut,
		launchAt: "2026-07-18T18:00:00.000Z"
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
			case "--chrome":
				parsed.chrome = value();
				break;
			case "--playwright-core":
				parsed.playwrightCore = value();
				break;
			case "--run-signup":
				parsed.runSignup = true;
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
	console.log(`Usage: scripts/verify-launch-staging.mjs --portal-url URL --web-url URL --server-config FILE --connections-config FILE (--run-signup|--skip-signup) [options]

Verifies the production-like launch gate for a staged Voidscape deployment:
  - portal health uses public launch-signup mode, durable storage, and an OpenRSC DB bridge
  - /api/public exposes web portal-first registration metadata, hybrid member world, Google hidden by default
  - /api/launcher/manifest.properties is a well-formed update channel (required keys,
    64-hex sha256 values, https URLs, clientVersion matching the server config) and its
	    every file entry plus the launcher self-update jar respond to HEAD
  - payment and Google provider surfaces are disabled/hidden unless explicitly allowed
	  - launch signup accepts 8-20 letters and numbers only, rejects symbols and spaces, and, with --run-signup, creates one real linked OpenRSC character plus a waiting starter card
  - /play static deployment matches dist/web-teavm/voidscape-web-build.json and optionally runs web login smoke
  - deployed server config copy has exactly one of every value in the reviewed
    launch contract; deployed connections config selects the SQLite launch backend

Required for the full gate:
  --portal-url URL              Staged portal URL, e.g. https://staging.voidscape.gg/
  --web-url URL                 Staged web client URL, e.g. https://staging.voidscape.gg/play/
  --server-config FILE          Local copy of the deployed server config.
  --connections-config FILE     Local copy of the deployed connections config.
  --run-signup                  Create a real staged account/first character.

Useful options:
  --ws URL                      WSS URL for web smoke.
  --expected-build-manifest FILE Default: dist/web-teavm/voidscape-web-build.json.
  --signup-username NAME        Default: generated Stg* name.
  --signup-email EMAIL          Default: staging+<name>@voidscape.gg.
  --signup-password PASSWORD    Default: Launchpass1 (letters and numbers only; symbols and spaces are rejected).
  --admin-token TOKEN           Also prove token-gated admin access works.
  --no-web-smoke                Static/deep web verification only.
  --skip-web-verify             Portal/config rehearsal only.
  --skip-server-config          Do not check the launch config contract or DB backend.
  --server-config-only          Check both deployed config copies without network requests.
  --skip-signup                 Non-mutating dry gate.
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
		webUrl: args.webUrl || "",
		wsUrl: args.wsUrl || "",
		serverConfig: args.serverConfig || "",
		connectionsConfig: args.connectionsConfig || "",
		serverConfigOnly: Boolean(args.serverConfigOnly),
		signup: args.runSignup ? {
			username: signupUser,
			email: signupEmail
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
	const redactNext = new Set(["--pass", "--qa-pass", "--signup-password"]);
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
