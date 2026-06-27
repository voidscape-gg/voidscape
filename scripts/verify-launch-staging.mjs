#!/usr/bin/env node
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
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
let signupEmail = args.signupEmail || `staging+${signupUser.toLowerCase()}@voidscape.gg`;
let signupPassword = args.signupPassword || "Launchpass1";

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

	if (!args.serverConfig && !args.skipServerConfig) {
		throw new Error("Missing --server-config. Pass the deployed server config copy, or --skip-server-config for portal-only rehearsal.");
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
	await verifyDisabledProviderSurfaces();
	await verifyBadLaunchPassword();
	if (args.runSignup) {
		await verifySignupFlow();
	} else {
		warn("staged signup skipped", "No account was created because --skip-signup was used.");
	}
	await verifyWebDeployment();
}

async function verifyServerConfig() {
	if (args.skipServerConfig) {
		warn("server config skipped", "Command lockdown and packet-register config were not checked.");
		return;
	}
	const configPath = resolve(root, args.serverConfig);
	const text = await readFile(configPath, "utf8");
	const expectedClientVersion = args.expectedClientVersion || await readClientVersion();
	const values = {
		client_version: configValue(text, "client_version"),
		enforce_custom_client_version: configValue(text, "enforce_custom_client_version"),
		want_packet_register: configValue(text, "want_packet_register"),
		production_command_lockdown: configValue(text, "production_command_lockdown"),
		member_world: configValue(text, "member_world"),
		want_feature_websockets: configValue(text, "want_feature_websockets")
	};
	assertCheck("server client version", values.client_version === expectedClientVersion, `${values.client_version} vs expected ${expectedClientVersion}`);
	assertCheck("server enforces custom client version", values.enforce_custom_client_version === "true", String(values.enforce_custom_client_version));
	assertCheck("server packet registration disabled", values.want_packet_register === "false", String(values.want_packet_register));
	assertCheck("server command lockdown enabled", values.production_command_lockdown === "true", String(values.production_command_lockdown));
	assertCheck("server member world enabled globally", values.member_world === "true", String(values.member_world));
	assertCheck("server websockets enabled", values.want_feature_websockets === "true", String(values.want_feature_websockets));
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
}

async function verifyPublicLaunchPayload() {
	const { status, body } = await request("/api/public");
	assertCheck("public payload status", status === 200, `HTTP ${status}`);
	assertCheck("public mode", body.publicMode === true, JSON.stringify(body));
	assertCheck("launch signup mode", body.launchSignupMode === true, JSON.stringify(body));
	assertCheck("launch timestamp", iso(body.launch && body.launch.openAt) === iso(args.launchAt || "2026-07-11T18:00:00.000Z"), String(body.launch && body.launch.openAt));
	assertCheck("portal-first registration", body.worldRules && body.worldRules.registration === "portal-first", JSON.stringify(body.worldRules || null));
	assertCheck("packet registration off", body.worldRules && body.worldRules.packetRegistration === false, JSON.stringify(body.worldRules || null));
	assertCheck("hybrid member world", body.worldRules && body.worldRules.memberWorld === true, JSON.stringify(body.worldRules || null));
	assertCheck("subscription does not grant members", body.worldRules && body.worldRules.subscriptionGrantsMembers === false, JSON.stringify(body.worldRules || null));
	assertCheck("no fake public player count", body.status && body.status.playersOnline === 0, JSON.stringify(body.status || null));
	const google = body.oauth && body.oauth.google || {};
	if (args.allowGoogle) {
		assertCheck("google configured intentionally", google.enabled === true && Boolean(google.clientId), JSON.stringify(google));
	} else {
		assertCheck("google hidden", google.enabled === false && !google.clientId, JSON.stringify(google));
	}
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

async function verifyBadLaunchPassword() {
	const badUsername = clipUsername(`${signupUser}Bad`);
	const response = await request("/api/accounts/register", {
		method: "POST",
		body: {
			username: badUsername,
			email: `staging+${badUsername.toLowerCase()}@voidscape.gg`,
			password: "bad-pass-1"
		}
	});
	assertCheck(
		"launch password rejects punctuation",
		response.status === 400 && response.body.error === "invalid_game_password",
		`HTTP ${response.status} ${JSON.stringify(response.body)}`
	);
}

async function verifySignupFlow() {
	signupUser = clipUsername(signupUser);
	if (!/^[A-Za-z0-9 ]{1,12}$/.test(signupUser)) {
		throw new Error(`Signup username must be 1-12 client-safe characters: ${signupUser}`);
	}
	if (!/^[A-Za-z0-9]{8,20}$/.test(signupPassword)) {
		throw new Error("Signup password must be 8-20 letters/numbers.");
	}
	const signup = await request("/api/accounts/register", {
		method: "POST",
		body: {
			username: signupUser,
			email: signupEmail,
			password: signupPassword
		}
	});
	assertCheck("staged signup status", signup.status === 201, `HTTP ${signup.status} ${JSON.stringify(signup.body)}`);
	assertCheck("staged signup issued token", Boolean(signup.body.token), JSON.stringify(signup.body));
	assertAccountPayload("staged signup account payload", signup.body);

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
	assertCheck("created account login issued token", Boolean(login.body.token), JSON.stringify(login.body));
	assertAccountPayload("created account login payload", login.body);
}

function assertAccountPayload(name, body) {
	const characters = Array.isArray(body.characters) ? body.characters : [];
	const first = characters[0] || {};
	assertCheck(`${name}: email`, body.account && body.account.email === signupEmail, JSON.stringify(body.account || null));
	assertCheck(`${name}: one used character slot`, characters.length === 1, JSON.stringify(characters));
	assertCheck(`${name}: first character name`, first.name === signupUser, JSON.stringify(first));
	assertCheck(`${name}: real OpenRSC save`, first.source === "openrsc-sqlite-created", JSON.stringify(first));
	assertCheck(`${name}: linked save`, first.linkStatus === "linked" && Number.isInteger(first.playerId), JSON.stringify(first));
	assertCheck(`${name}: starter card waiting`, body.rewards && body.rewards.starterCardStatus === "waiting", JSON.stringify(body.rewards || null));
	assertCheck(`${name}: one starter card`, body.rewards && body.rewards.starterSubscriptionCards === 1, JSON.stringify(body.rewards || null));
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
		const smokeUser = args.qaUser || (args.runSignup ? signupUser : "");
		const smokePass = args.qaPass || (args.runSignup ? signupPassword : "");
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
	await writeFile(resolve(outDir, "verify-command.txt"), `${command.map(shellQuote).join(" ")}\n`);
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
		if (!args.skipWebSmoke && (args.qaUser || args.runSignup)) {
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
		body: parsed
	}, null, 2));
	return { status: response.status, body: parsed, text };
}

function parseArgs(argv) {
	const parsed = {
		out: defaultOut,
		launchAt: "2026-07-11T18:00:00.000Z"
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
			default:
				throw new Error(`Unknown option: ${arg}`);
		}
	}
	return parsed;
}

function usage() {
	console.log(`Usage: scripts/verify-launch-staging.mjs --portal-url URL --web-url URL --server-config FILE (--run-signup|--skip-signup) [options]

Verifies the production-like launch gate for a staged Voidscape deployment:
  - portal health uses public launch-signup mode, durable storage, and an OpenRSC DB bridge
  - /api/public exposes portal-first registration, packet registration off, hybrid member world, Google hidden by default
  - payment and Google provider surfaces are disabled/hidden unless explicitly allowed
  - launch signup rejects punctuation passwords and, with --run-signup, creates one real linked OpenRSC character plus a waiting starter card
  - /play static deployment matches dist/web-teavm/voidscape-web-build.json and optionally runs web login smoke
  - deployed server config copy has production_command_lockdown: true and want_packet_register: false

Required for the full gate:
  --portal-url URL              Staged portal URL, e.g. https://staging.voidscape.gg/
  --web-url URL                 Staged web client URL, e.g. https://staging.voidscape.gg/play/
  --server-config FILE          Local copy of the deployed server config.
  --run-signup                  Create a real staged account/first character.

Useful options:
  --ws URL                      WSS URL for web smoke.
  --expected-build-manifest FILE Default: dist/web-teavm/voidscape-web-build.json.
  --signup-username NAME        Default: generated Stg* name.
  --signup-email EMAIL          Default: staging+<name>@voidscape.gg.
  --signup-password PASSWORD    Default: Launchpass1.
  --admin-token TOKEN           Also prove token-gated admin access works.
  --no-web-smoke                Static/deep web verification only.
  --skip-web-verify             Portal/config rehearsal only.
  --skip-server-config          Do not check command lockdown or packet-register config.
  --skip-signup                 Non-mutating dry gate.
  --allow-google                Expect Google to be intentionally configured.
  --allow-payment               Do not fail if payment endpoint is configured.
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

function configValue(text, key) {
	const match = text.match(new RegExp(`(?:^|\\n)\\s*${escapeRegExp(key)}\\s*:\\s*([^#\\n]+)`));
	return match ? match[1].trim() : "";
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

function escapeRegExp(value) {
	return String(value).replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
