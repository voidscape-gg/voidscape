import { createServer } from "node:http";
import { createReadStream } from "node:fs";
import { mkdir, readFile, readdir, rename, stat, unlink as unlinkFile, writeFile } from "node:fs/promises";
import { execFile as execFileCallback, spawn } from "node:child_process";
import { createCipheriv, createDecipheriv, createHash, createHmac, createPublicKey, randomBytes, scrypt as scryptCallback, timingSafeEqual, verify as verifySignature } from "node:crypto";
import { promisify } from "node:util";
import { basename, dirname, extname, join, normalize, relative, resolve } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";

const scrypt = promisify(scryptCallback);
const execFile = promisify(execFileCallback);
const rootDir = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(rootDir, "../..");
const port = Number(process.env.PORT || 8788);
const bindHost = process.env.PORTAL_BIND_HOST || "127.0.0.1";
const trustProxyHeader = process.env.PORTAL_TRUST_PROXY === "1";
const defaultLaunchAtIso = "2026-07-18T18:00:00Z";
// Prelaunch lockdown: only the signup flow (plus token-gated admin) is reachable.
const publicMode = process.env.PORTAL_PUBLIC_MODE === "1";
const launchSignupMode = publicMode && process.env.PORTAL_LAUNCH_SIGNUP_MODE === "1";
const betaMode = process.env.PORTAL_BETA_MODE === "1" || process.env.PORTAL_PUBLIC_BETA === "1";
const launchOpenAtIso = configuredIsoTimestamp("PORTAL_LAUNCH_AT", process.env.PORTAL_LAUNCH_AT || process.env.PORTAL_BETA_OPEN_AT || defaultLaunchAtIso);
const launchFreeCardHours = configuredNonNegativeInteger("PORTAL_LAUNCH_FREE_CARD_HOURS", process.env.PORTAL_LAUNCH_FREE_CARD_HOURS || "0");
const betaOpenAtIso = configuredIsoTimestamp("PORTAL_BETA_OPEN_AT", process.env.PORTAL_BETA_OPEN_AT || process.env.PORTAL_LAUNCH_AT || "");
const betaSignupCounterBase = configuredNonNegativeInteger("PORTAL_BETA_COUNTER_BASE", process.env.PORTAL_BETA_COUNTER_BASE || "132");
const betaSignupCounterStartedAtIso = configuredBetaSignupCounterStartedAt();
const betaSignupCounterSeed = process.env.PORTAL_BETA_COUNTER_SEED || "voidscape-public-beta";
const dataDir = process.env.PORTAL_DATA_DIR || join(tmpdir(), "voidscape-portal-api");
const storePath = join(dataDir, "dev-store.json");
const testStoreFaultPhase = String(process.env.PORTAL_TEST_STORE_FAULT || "").trim().toLowerCase();
const testStoreFaultAt = Number(process.env.PORTAL_TEST_STORE_FAULT_AT || 1);
const testStoreFaultsEnabled = process.env.PORTAL_ENABLE_TEST_FAULTS === "1";
let storeSaveAttempts = 0;
if (testStoreFaultPhase) {
	if (!testStoreFaultsEnabled) {
		throw new Error("PORTAL_TEST_STORE_FAULT requires PORTAL_ENABLE_TEST_FAULTS=1");
	}
	if (!["write", "rename"].includes(testStoreFaultPhase)
		|| !Number.isInteger(testStoreFaultAt)
		|| testStoreFaultAt <= 0) {
		throw new Error("invalid PORTAL_TEST_STORE_FAULT configuration");
	}
}
const integritySnapshotPath = process.env.PORTAL_INTEGRITY_SNAPSHOT || join(dataDir, "integrity-summary.json");
const buildMetadataPath = process.env.PORTAL_BUILD_META || join(rootDir, "build-meta.json");
const sourceRepositoryUrl = process.env.PORTAL_SOURCE_URL || process.env.PORTAL_REPOSITORY_URL || "";
const openRscDbPath = process.env.PORTAL_OPENRSC_DB || process.env.OPENRSC_SQLITE_DB || "";
const legendsSeasonId = configuredSeasonId("PORTAL_LEGENDS_SEASON_ID", process.env.PORTAL_LEGENDS_SEASON_ID || "launch-2026");
const gamePasswordHelperClasspath = process.env.PORTAL_GAME_PASSWORD_HELPER_CLASSPATH || join(repoRoot, "server/core.jar");
const gamePasswordJavaBin = process.env.PORTAL_JAVA_BIN || "java";
const legacyClaimDummyPasswordHash = "$2y$10$hwWyOlWjtLF2.3WvF64jl.RFQxNGz2k/iwGXJkhZJcbI187bacx46";
const portalSessionStorageKey = "voidscape.portal.sessionToken";
const maxCharacters = 10;
const sessionTtlMs = 1000 * 60 * 60 * 24 * 14;
const oauthStateTtlMs = 1000 * 60 * 10;
const linkChallengeTtlMs = 1000 * 60 * 15;
const scryptParams = { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 };
const openRscAccountIdCacheKey = "web_account_id";
const starterCardCachePrefix = "starter_card:";
const starterCardAvailable = 1;
const starterCardClaimed = 2;
const launchCharacterCardCachePrefix = "launch_subcard_2026:";
const launchCharacterCardCompletionKey = "launch_subcard_2026:done";
const launchCharacterCardAvailable = 1;
const launchCharacterCardClaimed = 2;
const launchCharacterCardCutoverComplete = 1;
const accountSubscriptionCachePrefix = "acct_sub:";
const playerSubscriptionCachePrefix = "char_sub:";
const signupCodeCachePrefix = "signup_code:";
const signupCodeAvailable = 1;
const signupCodeRedeemed = 2;
const starterFreeSubscriptionType = "starter_free_subscription";
const nativeBackfillCohortPolicy = "native-portal-launch-cutover-v2";
const baseCombatXpRate = 10;
const baseSkillXpRate = 1.5;
const sha256Cache = new Map();
const presenceVisitors = new Map();
const presenceHeartbeatSeconds = 15;
const presenceActiveWindowMs = 60 * 1000;
const presenceRecentWindowMs = 5 * 60 * 1000;
const presenceRetentionMs = 30 * 60 * 1000;
const presenceMaxVisitors = 5000;
const subscriptionXpBonus = 1;
const signupIpDailyLimit = configuredPositiveInteger("PORTAL_SIGNUP_IP_DAILY_LIMIT", process.env.PORTAL_SIGNUP_IP_DAILY_LIMIT || (launchSignupMode ? "5" : "10"));
const signupIpHourlyLimit = configuredPositiveInteger("PORTAL_SIGNUP_IP_HOURLY_LIMIT", process.env.PORTAL_SIGNUP_IP_HOURLY_LIMIT || (launchSignupMode ? "3" : String(signupIpDailyLimit)));
const signupSubnetDailyLimit = configuredNonNegativeInteger("PORTAL_SIGNUP_SUBNET_DAILY_LIMIT", process.env.PORTAL_SIGNUP_SUBNET_DAILY_LIMIT || (launchSignupMode ? "50" : "0"));
const signupSubnetHourlyLimit = configuredNonNegativeInteger("PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT", process.env.PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT || (launchSignupMode ? "20" : "0"));
const characterIpDailyLimit = configuredPositiveInteger("PORTAL_CHARACTER_IP_DAILY_LIMIT", process.env.PORTAL_CHARACTER_IP_DAILY_LIMIT || (launchSignupMode ? "20" : String(signupIpDailyLimit)));
const characterIpHourlyLimit = configuredPositiveInteger("PORTAL_CHARACTER_IP_HOURLY_LIMIT", process.env.PORTAL_CHARACTER_IP_HOURLY_LIMIT || (launchSignupMode ? "10" : String(characterIpDailyLimit)));
const characterAccountDailyLimit = configuredPositiveInteger("PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT", process.env.PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT || (launchSignupMode ? "10" : String(maxCharacters)));
const characterAccountHourlyLimit = configuredPositiveInteger("PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT", process.env.PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT || (launchSignupMode ? "5" : String(characterAccountDailyLimit)));
const characterSubnetDailyLimit = configuredNonNegativeInteger("PORTAL_CHARACTER_SUBNET_DAILY_LIMIT", process.env.PORTAL_CHARACTER_SUBNET_DAILY_LIMIT || (launchSignupMode ? "100" : "0"));
const characterSubnetHourlyLimit = configuredNonNegativeInteger("PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT", process.env.PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT || (launchSignupMode ? "50" : "0"));
const proxySignupIpDailyLimit = configuredPositiveInteger("PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT", process.env.PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT || "1");
const proxyCharacterIpDailyLimit = configuredPositiveInteger("PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT", process.env.PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT || "1");
const starterIpDailyLimit = configuredNonNegativeInteger("PORTAL_STARTER_IP_DAILY_LIMIT", process.env.PORTAL_STARTER_IP_DAILY_LIMIT || "0");
const loginIpFailureLimit = Math.max(1, Number(process.env.PORTAL_LOGIN_IP_FAILURE_LIMIT || 10));
const loginEmailFailureLimit = Math.max(1, Number(process.env.PORTAL_LOGIN_EMAIL_FAILURE_LIMIT || 20));
const loginFailureWindowMs = Math.max(1, Number(process.env.PORTAL_LOGIN_FAILURE_WINDOW_MINUTES || 15)) * 60 * 1000;
const recoveryFailureLimit = Math.max(1, Number(process.env.PORTAL_RECOVERY_FAILURE_LIMIT || 10));
const recoveryFailureWindowMs = Math.max(1, Number(process.env.PORTAL_RECOVERY_FAILURE_WINDOW_MINUTES || 15)) * 60 * 1000;
const passwordResetTtlMs = Math.max(5, configuredPositiveInteger("PORTAL_PASSWORD_RESET_TTL_MINUTES", process.env.PORTAL_PASSWORD_RESET_TTL_MINUTES || "30")) * 60 * 1000;
const passwordResetRequestIpLimit = configuredPositiveInteger("PORTAL_PASSWORD_RESET_IP_LIMIT", process.env.PORTAL_PASSWORD_RESET_IP_LIMIT || "5");
const passwordResetRequestAccountLimit = configuredPositiveInteger("PORTAL_PASSWORD_RESET_ACCOUNT_LIMIT", process.env.PORTAL_PASSWORD_RESET_ACCOUNT_LIMIT || "3");
const passwordResetRequestWindowMs = configuredPositiveInteger("PORTAL_PASSWORD_RESET_WINDOW_MINUTES", process.env.PORTAL_PASSWORD_RESET_WINDOW_MINUTES || "60") * 60 * 1000;
const legacyAccountClaimTtlMs = Math.max(5, configuredPositiveInteger("PORTAL_LEGACY_CLAIM_TTL_MINUTES", process.env.PORTAL_LEGACY_CLAIM_TTL_MINUTES || "30")) * 60 * 1000;
const legacyAccountClaimIpLimit = configuredPositiveInteger("PORTAL_LEGACY_CLAIM_IP_LIMIT", process.env.PORTAL_LEGACY_CLAIM_IP_LIMIT || "10");
const legacyAccountClaimCharacterLimit = configuredPositiveInteger("PORTAL_LEGACY_CLAIM_CHARACTER_LIMIT", process.env.PORTAL_LEGACY_CLAIM_CHARACTER_LIMIT || "5");
const legacyAccountClaimWindowMs = configuredPositiveInteger("PORTAL_LEGACY_CLAIM_WINDOW_MINUTES", process.env.PORTAL_LEGACY_CLAIM_WINDOW_MINUTES || "60") * 60 * 1000;
const sensitiveActionWindowMs = configuredPositiveInteger("PORTAL_SENSITIVE_ACTION_WINDOW_MINUTES", process.env.PORTAL_SENSITIVE_ACTION_WINDOW_MINUTES || "10") * 60 * 1000;
const gamePasswordResetLimit = configuredPositiveInteger("PORTAL_GAME_PASSWORD_RESET_LIMIT", process.env.PORTAL_GAME_PASSWORD_RESET_LIMIT || "5");
const gamePasswordResetWindowMs = configuredPositiveInteger("PORTAL_GAME_PASSWORD_RESET_WINDOW_MINUTES", process.env.PORTAL_GAME_PASSWORD_RESET_WINDOW_MINUTES || "60") * 60 * 1000;
const abuseSignalTtlMs = 1000 * 60 * 60 * 24 * 90;
const checkoutRateWindowMs = 10 * 60 * 1000;
const checkoutAccountRateLimit = 5;
const checkoutIpRateLimit = 8;
const blockedIpCidrs = cidrList(process.env.PORTAL_BLOCKED_IP_CIDRS || "");
const proxyIpCidrs = cidrList(process.env.PORTAL_PROXY_IP_CIDRS || process.env.PORTAL_REVIEW_IP_CIDRS || "");
const captchaSignupRequired = process.env.PORTAL_CAPTCHA_REQUIRED === "1" || process.env.PORTAL_CAPTCHA_SIGNUP_REQUIRED === "1";
const captchaCharacterRequired = process.env.PORTAL_CAPTCHA_CHARACTER_REQUIRED === "1";
const captchaProvider = String(process.env.PORTAL_CAPTCHA_PROVIDER || "turnstile").trim().toLowerCase();
const captchaSiteKey = String(process.env.PORTAL_CAPTCHA_SITE_KEY || process.env.PORTAL_TURNSTILE_SITE_KEY || "").trim();
const captchaSecret = String(process.env.PORTAL_CAPTCHA_SECRET || process.env.PORTAL_TURNSTILE_SECRET || "").trim();
const captchaBypassToken = String(process.env.PORTAL_CAPTCHA_BYPASS_TOKEN || "").trim();
const captchaVerifyUrl = process.env.PORTAL_CAPTCHA_VERIFY_URL || "https://challenges.cloudflare.com/turnstile/v0/siteverify";
const defaultAbuseHashSalt = "voidscape-portal-dev";
const abuseHashSaltInput = process.env.PORTAL_ABUSE_HASH_SALT || "";
const abuseHashSalt = abuseHashSaltInput || defaultAbuseHashSalt;
const adminToken = process.env.PORTAL_ADMIN_TOKEN || "";
const abuseHashSaltConfigured = configuredSecret(abuseHashSaltInput, defaultAbuseHashSalt);
const adminTokenConfigured = configuredSecret(adminToken, "");
const tebexPublicToken = String(process.env.PORTAL_TEBEX_PUBLIC_TOKEN || "").trim();
const tebexPrivateKey = String(process.env.PORTAL_TEBEX_PRIVATE_KEY || "").trim();
const tebexWebhookSecret = String(process.env.PORTAL_TEBEX_WEBHOOK_SECRET || "").trim();
const tebexSubscriptionCardPackageId = String(process.env.PORTAL_TEBEX_SUBSCRIPTION_CARD_PACKAGE_ID || "").trim();
const tebexExpectedCurrency = String(process.env.PORTAL_TEBEX_EXPECTED_CURRENCY || "").trim().toUpperCase();
const tebexExpectedPriceMinorText = String(process.env.PORTAL_TEBEX_EXPECTED_PRICE_MINOR || "").trim();
const tebexExpectedPriceMinor = /^\d+$/.test(tebexExpectedPriceMinorText)
	? Number(tebexExpectedPriceMinorText)
	: null;
const officialTebexApiBase = "https://headless.tebex.io/api";
const tebexTestApiBase = String(process.env.PORTAL_TEBEX_API_BASE || "").trim().replace(/\/+$/, "");
const tebexApiBase = tebexTestApiBase || officialTebexApiBase;
const tebexTestWebhookFailOnce = process.env.PORTAL_TEST_TEBEX_WEBHOOK_FAIL_ONCE === "1";
if (tebexTestApiBase && !testStoreFaultsEnabled) {
	throw new Error("PORTAL_TEBEX_API_BASE requires PORTAL_ENABLE_TEST_FAULTS=1");
}
if (tebexTestWebhookFailOnce && !testStoreFaultsEnabled) {
	throw new Error("PORTAL_TEST_TEBEX_WEBHOOK_FAIL_ONCE requires PORTAL_ENABLE_TEST_FAULTS=1");
}
const discordApiBase = process.env.PORTAL_DISCORD_API_BASE || "https://discord.com/api/v10";
const discordClientId = process.env.PORTAL_DISCORD_CLIENT_ID || "";
const discordClientSecret = process.env.PORTAL_DISCORD_CLIENT_SECRET || "";
const discordRedirectUri = process.env.PORTAL_DISCORD_REDIRECT_URI || "";
const discordGuildId = process.env.PORTAL_DISCORD_GUILD_ID || "";
const discordBotToken = process.env.PORTAL_DISCORD_BOT_TOKEN || "";
const discordMemberRoleId = process.env.PORTAL_DISCORD_MEMBER_ROLE_ID || "";
const discordBetaRoleId = process.env.PORTAL_DISCORD_BETA_ROLE_ID || "";
const discordOauthScopes = (process.env.PORTAL_DISCORD_SCOPES || "identify guilds.join")
	.split(/[,\s]+/)
	.map((scope) => scope.trim())
	.filter(Boolean);
const googleClientId = process.env.PORTAL_GOOGLE_CLIENT_ID || "";
const googleJwksUrl = process.env.PORTAL_GOOGLE_JWKS_URL || "https://www.googleapis.com/oauth2/v3/certs";
const googleIdentityProvider = "google";
let googleJwksCache = { expiresAt: 0, keys: new Map() };
const publicSiteOrigin = normalizedOrigin(process.env.PORTAL_PUBLIC_ORIGIN || "");
const webClientUrl = mobileWebClientUrl(process.env.PORTAL_WEB_CLIENT_URL || "https://voidscape.gg/play/");
const androidPlayUrl = configuredAndroidPlayUrl("PORTAL_ANDROID_PLAY_URL", process.env.PORTAL_ANDROID_PLAY_URL);
const discordInviteUrl = process.env.PORTAL_DISCORD_INVITE_URL || "https://discord.gg/f6uQmrRv4";
const emailProvider = String(process.env.PORTAL_EMAIL_PROVIDER || "").trim().toLowerCase();
const emailDryRun = process.env.PORTAL_EMAIL_DRY_RUN === "1";
const requireEmailDelivery = process.env.PORTAL_REQUIRE_EMAIL === "1";
const resendApiKey = String(process.env.PORTAL_RESEND_API_KEY || "").trim();
const emailFrom = String(process.env.PORTAL_EMAIL_FROM || "Voidscape <launch@voidscape.gg>").trim();
const emailReplyTo = String(process.env.PORTAL_EMAIL_REPLY_TO || "support@voidscape.gg").trim();
const signupConfirmationEmailType = "signup_confirmation";
const emailVerificationEmailType = "email_verification";
const passwordResetEmailType = "password_reset";
const legacyAccountClaimEmailType = "legacy_account_claim";
const interruptedEmailRecoveryMs = 15 * 60 * 1000;
const emailVerificationRequired = process.env.PORTAL_EMAIL_VERIFICATION_REQUIRED === "1" || process.env.PORTAL_REQUIRE_EMAIL_VERIFICATION === "1";
const emailVerificationTtlHours = configuredPositiveInteger("PORTAL_EMAIL_VERIFICATION_TTL_HOURS", process.env.PORTAL_EMAIL_VERIFICATION_TTL_HOURS || "48");
const emailVerificationTtlMs = emailVerificationTtlHours * 60 * 60 * 1000;
const emailVerificationRequestIpLimit = configuredPositiveInteger("PORTAL_EMAIL_VERIFICATION_IP_LIMIT", process.env.PORTAL_EMAIL_VERIFICATION_IP_LIMIT || "3");
const emailVerificationRequestEmailLimit = configuredPositiveInteger("PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT", process.env.PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT || "3");
const emailVerificationRequestWindowMs = configuredPositiveInteger("PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES", process.env.PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES || "60") * 60 * 1000;
const launchReminderEmailType = "launch_48h";
const launchLiveEmailType = "launch_live";
const downloadArtifacts = [
	{
		slug: "client-runtime",
		label: "Voidscape client runtime",
		filename: "Open_RSC_Client.jar",
		path: configuredDownloadPath("PORTAL_PC_CLIENT_JAR", join(repoRoot, "Client_Base/Open_RSC_Client.jar")),
		contentType: "application/java-archive",
		publicDownload: false
	},
	{
		slug: "launcher",
		label: "Voidscape launcher",
		filename: "VoidscapeLauncher.jar",
		path: configuredDownloadPath("PORTAL_LAUNCHER_JAR", join(repoRoot, "PC_Launcher/OpenRSC.jar")),
		contentType: "application/java-archive"
	},
	{
		slug: "android-apk",
		label: "Android APK",
		filename: "Voidscape-Android-Beta.apk",
		path: configuredDownloadPath("PORTAL_ANDROID_APK", ""),
		contentType: "application/vnd.android.package-archive",
		unavailableState: "Awaiting a promoted Android release"
	}
];
const funnelEvents = new Set([
	"play_web",
	"download_launcher",
	"download_android",
	"download",
	"reserve_submit",
	"discord_rewards",
	"transparency",
	"click"
]);
const clientCacheDir = configuredDownloadPath("PORTAL_CLIENT_CACHE_DIR", join(repoRoot, "Client_Base/Cache"));
const launcherRuntimeFiles = new Set([
	"accounts.txt",
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
const clientVersionOverride = process.env.PORTAL_CLIENT_VERSION
	? configuredNonNegativeInteger("PORTAL_CLIENT_VERSION", process.env.PORTAL_CLIENT_VERSION)
	: null;
const clientVersionSourcePath = join(repoRoot, "Client_Base/src/orsc/Config.java");
let clientVersionCache;

function configuredDownloadPath(envName, fallbackPath) {
	const configuredPath = process.env[envName];
	return configuredPath ? resolve(repoRoot, configuredPath) : fallbackPath;
}

function configuredAndroidPlayUrl(envName, value) {
	const text = String(value || "").trim();
	if (!text) return "";
	try {
		const parsed = new URL(text);
		const ids = parsed.searchParams.getAll("id");
		const pathname = parsed.pathname.replace(/\/+$/g, "");
		if (parsed.protocol !== "https:"
			|| parsed.hostname !== "play.google.com"
			|| parsed.port
			|| parsed.username
			|| parsed.password
			|| pathname !== "/store/apps/details"
			|| ids.length !== 1
			|| ids[0] !== "com.voidscape.gg") {
			throw new Error("invalid listing");
		}
	} catch (error) {
		throw new Error(`${envName} must be the official https://play.google.com/store/apps/details?id=com.voidscape.gg listing`);
	}
	return "https://play.google.com/store/apps/details?id=com.voidscape.gg";
}

function configuredIsoTimestamp(envName, value) {
	if (!value) return "";
	const timestamp = Date.parse(value);
	if (!Number.isFinite(timestamp)) {
		throw new Error(`${envName} must be an ISO-8601 date/time`);
	}
	return new Date(timestamp).toISOString();
}

function configuredNonNegativeInteger(envName, value) {
	const number = Number(value);
	if (!Number.isInteger(number) || number < 0) {
		throw new Error(`${envName} must be a non-negative integer`);
	}
	return number;
}

function configuredSeasonId(envName, value) {
	const seasonId = String(value || "").trim().toLowerCase();
	if (!/^[a-z0-9][a-z0-9_-]{0,31}$/.test(seasonId)) {
		throw new Error(`${envName} must use 1-32 letters, numbers, hyphens, or underscores`);
	}
	return seasonId;
}

function configuredPositiveInteger(envName, value) {
	const number = configuredNonNegativeInteger(envName, value);
	if (number < 1) {
		throw new Error(`${envName} must be a positive integer`);
	}
	return number;
}

function configuredSecret(value, fallback) {
	const text = String(value || "").trim();
	if (!text || text === fallback) return false;
	const lowered = text.toLowerCase();
	if (lowered === "dev" || lowered === "test" || lowered === "dev-admin") return false;
	if (lowered.includes("change_me") || lowered.includes("changeme")) return false;
	return text.length >= 16;
}

function emailConfigured() {
	if (!emailFrom) return false;
	if (emailDryRun) return true;
	return emailProvider === "resend" && Boolean(resendApiKey);
}

function emailProviderLabel() {
	if (emailDryRun) return "dry-run";
	return emailProvider || "disabled";
}

function tebexConfigHealth() {
	const publicTokenConfigured = Boolean(tebexPublicToken);
	const privateKeyConfigured = configuredSecret(tebexPrivateKey, "");
	const webhookSecretConfigured = configuredSecret(tebexWebhookSecret, "");
	const packageConfigured = /^\d+$/.test(tebexSubscriptionCardPackageId) && Number(tebexSubscriptionCardPackageId) > 0;
	const currencyConfigured = /^[A-Z]{3}$/.test(tebexExpectedCurrency);
	const priceConfigured = Number.isSafeInteger(tebexExpectedPriceMinor) && tebexExpectedPriceMinor > 0;
	const databaseConfigured = Boolean(openRscDbPath);
	const publicOriginConfigured = Boolean(publicSiteOrigin);
	return {
		configured: publicTokenConfigured
			&& privateKeyConfigured
			&& webhookSecretConfigured
			&& packageConfigured
			&& currencyConfigured
			&& priceConfigured
			&& databaseConfigured
			&& publicOriginConfigured,
		publicTokenConfigured,
		privateKeyConfigured,
		webhookSecretConfigured,
		packageConfigured,
		currencyConfigured,
		priceConfigured,
		databaseConfigured,
		publicOriginConfigured
	};
}

function tebexCheckoutConfigured() {
	return tebexConfigHealth().configured;
}

function tebexWebhookConfigured() {
	return tebexConfigHealth().webhookSecretConfigured;
}

async function tebexOperationalHealth() {
	const config = tebexConfigHealth();
	let databaseReadable = false;
	let schemaReady = false;
	if (openRscDbPath) {
		try {
			await sqliteCommerceRead("SELECT 1 AS ok;");
			databaseReadable = true;
			schemaReady = await commerceSchemaAvailable();
		} catch (error) {
			databaseReadable = false;
			schemaReady = false;
		}
	}
	return {
		...config,
		databaseReadable,
		schemaReady,
		operational: config.configured && databaseReadable && schemaReady
	};
}

function portalConfigHealth() {
	const issues = [];
	if (publicMode && !abuseHashSaltConfigured) {
		issues.push("abuse_hash_salt_not_configured");
	}
	if (adminToken && !adminTokenConfigured) {
		issues.push("admin_token_weak");
	}
	if (requireEmailDelivery && !emailConfigured()) {
		issues.push("email_not_configured");
	}
	if (emailVerificationRequired && !emailConfigured()) {
		issues.push("email_verification_not_configured");
	}
	if (publicMode && emailVerificationRequired && !publicSiteOrigin) {
		issues.push("public_origin_not_configured");
	}
	if ((captchaSignupRequired || captchaCharacterRequired) && !captchaConfigured()) {
		issues.push("captcha_not_configured");
	}
	const commerce = tebexConfigHealth();
	const commercePartiallyConfigured = Boolean(
		tebexPublicToken
		|| tebexPrivateKey
		|| tebexWebhookSecret
		|| tebexSubscriptionCardPackageId
		|| tebexExpectedCurrency
		|| tebexExpectedPriceMinorText
	);
	if (commercePartiallyConfigured && !commerce.configured) {
		issues.push("tebex_commerce_not_configured");
	}
	return {
		publicReady: issues.length === 0,
		abuseHashSaltConfigured,
		adminTokenConfigured,
		publicOriginConfigured: Boolean(publicSiteOrigin),
		captcha: {
			provider: captchaProvider,
			configured: captchaConfigured(),
			signupRequired: captchaSignupRequired,
			characterRequired: captchaCharacterRequired,
			siteKeyConfigured: Boolean(captchaSiteKey),
			secretConfigured: Boolean(captchaSecret),
			bypassConfigured: Boolean(captchaBypassToken)
		},
		email: {
			provider: emailProviderLabel(),
			configured: emailConfigured(),
			dryRun: emailDryRun,
			requireConfigured: requireEmailDelivery,
			verificationRequired: emailVerificationRequired,
			fromConfigured: Boolean(emailFrom)
		},
		commerce,
		issues
	};
}

function configuredBetaSignupCounterStartedAt() {
	const explicit = configuredIsoTimestamp("PORTAL_BETA_COUNTER_STARTED_AT", process.env.PORTAL_BETA_COUNTER_STARTED_AT || "");
	if (explicit) return explicit;
	if (betaOpenAtIso) {
		return new Date(Date.parse(betaOpenAtIso) - 72 * 60 * 60 * 1000).toISOString();
	}
	return "2026-06-15T00:00:00.000Z";
}

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
		{ type: "rare", text: "Three-floor Void Dungeon routes are ready for launch.", time: "Today" },
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

const betaContent = {
	commands: [
		{ group: "Report", access: "All testers", command: "::bug <what happened>", note: "Sends your name, location, and report to the Discord bug feed. One report per minute." },
		{ group: "Chat", access: "All testers", command: "::g <message>", note: "Global chat with country flag styling. Alias: ::pk." },
		{ group: "Chat", access: "All testers", command: "::p <message>", note: "Party chat when testing group flows." },
		{ group: "Info", access: "All testers", command: "::gameinfo", note: "Shows account/player/server details useful for support screenshots." },
		{ group: "Info", access: "All testers", command: "::commands", note: "Shows the built-in player command help pages." },
		{ group: "Info", access: "All testers", command: "::coords", note: "Shows your current world coordinates for repro notes." },
		{ group: "Info", access: "All testers", command: "::online", note: "Shows the current online count." },
		{ group: "Info", access: "All testers", command: "::onlinelist", note: "Lists online testers for repro groups." },
		{ group: "Info", access: "All testers", command: "::onlinelistlocs", note: "Lists online testers with locations when location visibility is enabled." },
		{ group: "Info", access: "All testers", command: "::uniqueonline", note: "Shows unique online player count." },
		{ group: "Info", access: "All testers", command: "::time", note: "Prints server date/time." },
		{ group: "Void Arena", access: "All testers", command: "::arena help", note: "Shows Void Arena command help. Alias: ::voidarena." },
		{ group: "Void Arena", access: "All testers", command: "::arena enter", note: "Teleport to the ranked Death Match lobby at 600, 2914." },
		{ group: "Void Arena", access: "All testers", command: "::arena challenge <player>", note: "Challenge an online player. Alias: ::arena fight <player>. Ranked fights expect maxed melee stats." },
		{ group: "Void Arena", access: "All testers", command: "::arena stats", note: "Shows your rating, wins, and losses after 5 placement matches." },
		{ group: "Void Arena", access: "All testers", command: "::arena top", note: "Shows the ranked leaderboard." },
		{ group: "Void Arena", access: "All testers", command: "::arena leave", note: "Leave the Void Arena lobby." },
		{ group: "Progression", access: "All testers", command: "::rested", note: "Shows rested-XP pool and cap status." },
		{ group: "Progression", access: "All testers", command: "::titles", note: "Open the title catalogue UI." },
		{ group: "Progression", access: "All testers", command: "::titles unlocked", note: "Show unlocked title count and choices." },
		{ group: "Progression", access: "All testers", command: "::titles unique", note: "Show unique one-owner titles." },
		{ group: "Progression", access: "All testers", command: "::titles rarest", note: "Show rare title page." },
		{ group: "Progression", access: "All testers", command: "::title <id or name>", note: "Equip an unlocked title." },
		{ group: "Progression", access: "All testers", command: "::title clear", note: "Remove your active title." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam list", note: "Show current rare-drop beam settings." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam defaults", note: "Show default beam-worthy items." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam add <item id/name>", note: "Add a custom item to your loot beam list." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam remove <item id/name>", note: "Remove or hide an item from beam settings." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam mode default|custom", note: "Choose default-plus-custom or custom-only beams." },
		{ group: "Loot beams", access: "All testers", command: "::lootbeam reset", note: "Restore default rare-drop beam behavior." },
		{ group: "Minigames", access: "All testers", command: "::leave", note: "Exit PK Catching Simulator early." },
		{ group: "Settings", access: "All testers", command: "::togglereceipts", note: "Toggle shop receipt messages." },
		{ group: "Settings", access: "All testers", command: "::toggleglobalchat", note: "Toggle global chat display." },
		{ group: "Settings", access: "All testers", command: "::toggleblockchat", note: "Toggle local chat blocking." },
		{ group: "Settings", access: "All testers", command: "::toggleblockprivate", note: "Toggle private message blocking while testing social flows." },
		{ group: "Settings", access: "All testers", command: "::toggleblocktrade", note: "Toggle trade request blocking." },
		{ group: "Settings", access: "All testers", command: "::toggleblockduel", note: "Toggle duel request blocking." },
	],
	checklist: [
		"Create a fresh character through the portal account flow, finish appearance, and choose a Void Island starter path.",
		"Confirm the starter kit appears once, survives relog, and does not repeat on relog.",
		"Claim the free subscription card from the Lumbridge Subscription Vendor with your starter code.",
		"Redeem the Subscription card and confirm wrench/profile XP rates change to subscribed rates.",
		"Use Edgeville Auction House through the in-game NPC: browse, list, buy, and inspect market intel.",
		"Open ::titles, page the catalogue, inspect requirements, equip a title, then clear it.",
		"Customize rare drop beams with ::lootbeam and confirm ground-item visuals work.",
		"Send ::g test and toggle country flag/global chat settings from the client.",
		"Run a Void Arena lobby/challenge/stats/top test with another online player.",
		"Visit Void Enclave, Void Dungeon, Void Knight chamber, Wilderness hobgoblins, and PK Catching Trainer.",
		"Try desktop launcher update/play states and Android APK on a safe test device if available.",
		"Report blockers with ::bug first, then add screenshots, client/platform, and repro steps in Discord."
	],
	coords: [
		{ group: "Onboarding", label: "Void Council intro", value: "24, 37", note: "Fresh-character intro clearing." },
		{ group: "Onboarding", label: "Void Island Herald", value: "24, 24", note: "Choose a starter path and review launch guidance." },
		{ group: "Home", label: "Lumbridge home", value: "120, 648", note: "Respawn/home flow." },
		{ group: "Home", label: "Subscription Vendor", value: "126, 649", note: "Claim release-valid starter card code." },
		{ group: "Market", label: "Edgeville bank", value: "217, 449", note: "Bank, PvP access, nearby Auction House." },
		{ group: "Market", label: "Void Auctioneer", value: "217, 460", note: "Auction House UI, listings, market intel." },
		{ group: "Travel", label: "City Void Rift", value: "139, 636", note: "Rift menu to Varrock, Falador, Draynor, Lumbridge, and Edgeville." },
		{ group: "Void", label: "Void Rift", value: "192, 443", note: "Enter prompt to Void Enclave." },
		{ group: "Void", label: "Void Enclave", value: "113, 314", note: "Safe hub, amenities, waystones, boss entry." },
		{ group: "Void", label: "Enclave bank", value: "103, 313", note: "Hub banking." },
		{ group: "Void", label: "Enclave altar", value: "112, 305", note: "Prayer restore." },
		{ group: "Void", label: "Enclave pool", value: "122, 315", note: "Healing/restoration check." },
		{ group: "Void", label: "Void Quartermaster", value: "105, 315", note: "Hub NPC/shop check." },
		{ group: "Void", label: "Void chest", value: "105, 316", note: "Void Key reward testing." },
		{ group: "Void", label: "Void Dungeon entrance", value: "112, 296", note: "Unsafe Wilderness rift; coin-gated entry." },
		{ group: "Void", label: "Void Dungeon", value: "72, 3252", note: "Shared underground Wilderness cave." },
		{ group: "Void", label: "Void Dungeon exit", value: "72, 3250", note: "Returns to 112, 297." },
		{ group: "Boss", label: "Void Knight boss ladder", value: "122, 313", note: "Climb down from Enclave." },
		{ group: "Boss", label: "Void Knight chamber", value: "984, 667", note: "Attack the Void Knight to start solo fight." },
		{ group: "Void Arena", label: "Death Match lobby", value: "600, 2914", note: "Arrive with ::arena enter." },
		{ group: "Void Arena", label: "Arena Herald", value: "600, 2915", note: "Lobby NPC." },
		{ group: "Void Arena", label: "Arena bank chest", value: "596, 2915", note: "Gear prep before challenges." },
		{ group: "Wilderness", label: "Wilderness hobgoblins", value: "217, 255", note: "Dynamic spawns and faster respawns." },
		{ group: "PvP", label: "PK Catching Trainer", value: "214, 437", note: "Five-minute catching drill and highscores." },
		{ group: "Smoke routes", label: "Varrock area", value: "122, 509", note: "Varrock route target." },
		{ group: "Smoke routes", label: "Draynor area", value: "214, 632", note: "Draynor route target." },
		{ group: "Smoke routes", label: "Falador area", value: "304, 542", note: "Falador route target." }
	],
	items: [
		{ group: "Currency", id: 10, name: "Coins", note: "Use for vendor/rift/economy smoke tests." },
		{ group: "Runes", id: 31, name: "Fire-Rune", note: "Arcanist starter and magic tests." },
		{ group: "Runes", id: 33, name: "Air-Rune", note: "Arcanist starter and magic tests." },
		{ group: "Runes", id: 35, name: "Mind-Rune", note: "Arcanist starter and magic tests." },
		{ group: "Runes", id: 42, name: "Law-Rune", note: "Teleport/magic supply checks." },
		{ group: "Melee", id: 77, name: "Iron 2-handed Sword", note: "Warrior starter weapon." },
		{ group: "Melee", id: 81, name: "Rune 2-handed Sword", note: "High-risk melee/PvP smoke tests." },
		{ group: "Melee", id: 93, name: "Rune battle Axe", note: "High-tier melee baseline." },
		{ group: "Armor", id: 104, name: "Medium Bronze Helmet", note: "Warrior starter armor." },
		{ group: "Armor", id: 117, name: "Bronze Plate Mail Body", note: "Warrior starter armor." },
		{ group: "Armor", id: 206, name: "Bronze Plate Mail Legs", note: "Warrior starter armor." },
		{ group: "Food", id: 138, name: "Bread", note: "Starter kit food." },
		{ group: "Skilling", id: 156, name: "Bronze Pickaxe", note: "Forager starter tool." },
		{ group: "Skilling", id: 166, name: "Tinderbox", note: "Forager starter utility." },
		{ group: "Magic", id: 184, name: "Wizards robe", note: "Arcanist starter robe." },
		{ group: "Magic", id: 185, name: "wizardshat", note: "Arcanist starter hat." },
		{ group: "Ranged", id: 189, name: "Shortbow", note: "Arcanist starter weapon." },
		{ group: "Food", id: 373, name: "Lobster", note: "Common PvP/PvM food." },
		{ group: "Skilling", id: 376, name: "Net", note: "Forager starter fishing tool." },
		{ group: "Skilling", id: 377, name: "Fishing rod", note: "Forager starter fishing tool." },
		{ group: "Skilling", id: 380, name: "Fishing bait", note: "Forager starter supply." },
		{ group: "Voidscape", id: 1593, name: "Void Scimitar", note: "Custom void melee weapon." },
		{ group: "Voidscape", id: 1594, name: "Void Shortbow", note: "Custom void ranged weapon." },
		{ group: "Voidscape", id: 1595, name: "Void Amulet", note: "Void drop/stackable utility." },
		{ group: "Voidscape", id: 1596, name: "Void Mace", note: "Custom void crush weapon." },
		{ group: "Voidscape", id: 1598, name: "Dragon sword hilt", note: "Dragon Sword smithing part." },
		{ group: "Voidscape", id: 1599, name: "Dragon sword blade", note: "Dragon Sword smithing part." },
		{ group: "Voidscape", id: 1600, name: "Dragon sword tip", note: "Dragon Sword smithing part." },
		{ group: "Voidscape", id: 1601, name: "Void Key", note: "Void reward/chest testing." },
		{ group: "Release reward", id: 1602, name: "Subscription card", note: "One-week account subscription reward." },
		{ group: "Voidscape", id: 1603, name: "Void Sparrow", note: "Wilderness scouting/custom item smoke tests." },
		{ group: "Ash offerings", id: 1604, name: "Warm ashes", note: "Prayer offering item." },
		{ group: "Ash offerings", id: 1605, name: "Bright ashes", note: "Prayer offering item." },
		{ group: "Ash offerings", id: 1606, name: "Sacred ashes", note: "Prayer offering item." },
		{ group: "Ash offerings", id: 1607, name: "Blessed ashes", note: "Prayer offering item." },
		{ group: "Voidscape", id: 1608, name: "Void ashes", note: "Void resource/drop checks." }
	],
	policies: {
		progress: "Prelaunch test character/world progress may be wiped before launch.",
		codes: "Starter card codes are release-valid and must be preserved in the portal ledger, then resynced to the release game database."
	}
};

let writeQueue = Promise.resolve();
let itemDefinitionsPromise = null;
let titleDefinitionsPromise = null;
let tebexWebhookFailureInjected = false;

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

const loopbackBind = bindHost === "127.0.0.1" || bindHost === "::1" || bindHost === "localhost";
if (!loopbackBind && !process.env.PORTAL_DATA_DIR && process.env.PORTAL_ALLOW_TMPDIR !== "1") {
	console.error(
		`Refusing to bind ${bindHost} with the default temp data dir: the signup list would be lost on reboot.\n` +
		"Set PORTAL_DATA_DIR=/path/to/durable/dir (recommended) or PORTAL_ALLOW_TMPDIR=1 to override."
	);
	process.exit(1);
}

server.listen(port, bindHost, () => {
	const modeLabel = betaMode
		? " (PUBLIC BETA MODE)"
		: launchSignupMode
			? " (PUBLIC LAUNCH SIGNUP MODE)"
			: publicMode
				? " (PUBLIC PRELAUNCH MODE)"
				: "";
	console.log(`Voidscape portal dev server: http://${bindHost}:${port}/${modeLabel}`);
	console.log(`Portal API data: ${storePath}`);
});

async function handleRequest(request, response) {
	const url = new URL(request.url, `http://${request.headers.host || "127.0.0.1"}`);
	if (publicMode && !betaMode && !launchSignupMode && (
		url.pathname.startsWith("/openrsc/avatar/")
	)) {
		throw new HttpError(404, "not_available_during_prelaunch");
	}
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

	if (publicMode) {
		const allowed =
			(method === "GET" && url.pathname === "/api/health")
			|| (method === "GET" && url.pathname === "/api/public")
			|| (method === "GET" && url.pathname === "/api/integrity")
			|| (method === "GET" && url.pathname === "/api/legends")
			|| (method === "GET" && url.pathname.startsWith("/api/openrsc/characters/") && url.searchParams.get("availability") === "1")
			|| (method === "POST" && url.pathname === "/api/funnel/click")
			|| (method === "POST" && url.pathname === "/api/presence/heartbeat")
			|| (method === "POST" && url.pathname === "/api/payments/tebex/webhook")
			|| (!betaMode && method === "POST" && url.pathname === "/api/founder/reservations")
			|| (launchSignupMode && method === "GET" && url.pathname === "/api/account")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/register")
			|| (launchSignupMode && method === "GET" && url.pathname === "/api/accounts/verify-email")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/verify-email")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/verify-email/resend")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/google")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/login")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/logout")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/recover-password")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/password-reset/request")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/password-reset/complete")
			|| (launchSignupMode && method === "GET" && url.pathname === "/api/accounts/legacy-claim/verify")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/legacy-claim/request")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/legacy-claim/complete")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/oauth/google/nonce")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/characters")
			|| (launchSignupMode && method === "POST" && /^\/api\/characters\/\d+\/game-password$/.test(url.pathname))
			|| (launchSignupMode && method === "DELETE" && /^\/api\/characters\/\d+$/.test(url.pathname))
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/password")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/recovery-codes")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/sessions/revoke-others")
			|| ((launchSignupMode || betaMode) && method === "POST" && url.pathname === "/api/payments/subscription-cards/checkout")
			|| (betaMode && method === "GET" && url.pathname === "/api/account")
			|| (betaMode && method === "GET" && url.pathname === "/api/oauth/discord/start")
			|| (betaMode && method === "GET" && url.pathname === "/api/oauth/discord/callback")
			|| url.pathname.startsWith("/api/admin/");
		if (!allowed) {
			throw new HttpError(404, "not_available_during_prelaunch");
		}
	}

	if (method === "GET" && url.pathname === "/api/health") {
		const storageHealth = await portalStorageHealth();
		const configHealth = portalConfigHealth();
		const commerceHealth = await tebexOperationalHealth();
		configHealth.commerce = commerceHealth;
		if (commerceHealth.configured && !commerceHealth.operational) {
			configHealth.publicReady = false;
			if (!configHealth.issues.includes("tebex_commerce_not_operational")) {
				configHealth.issues = [...configHealth.issues, "tebex_commerce_not_operational"];
			}
		}
		if (!storageHealth.readable) {
			configHealth.publicReady = false;
			configHealth.issues = [...configHealth.issues, "portal_store_unreadable"];
		}
		json(response, 200, {
			ok: storageHealth.readable,
			service: "voidscape-portal-dev",
			publicMode,
			launchSignupMode,
			storage: {
				durable: Boolean(process.env.PORTAL_DATA_DIR),
				tempDirOverride: process.env.PORTAL_ALLOW_TMPDIR === "1",
				readable: storageHealth.readable
			},
			openRscDb: {
				configured: Boolean(openRscDbPath)
			},
			config: configHealth
		});
		return;
	}

	if (method === "GET" && url.pathname === "/api/public") {
		const store = await loadStore();
		json(response, 200, await publicState(store));
		return;
	}

	if (method === "GET" && url.pathname === "/api/integrity") {
		const store = await loadStore();
		json(response, 200, await integrityState(store));
		return;
	}

	if (method === "GET" && url.pathname === "/api/legends") {
		json(response, 200, await legendsState());
		return;
	}

	if (method === "POST" && url.pathname === "/api/funnel/click") {
		const payload = await readJson(request);
		const result = await recordFunnelClick(request, payload);
		json(response, 202, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/presence/heartbeat") {
		const payload = await readJson(request);
		const result = recordPresenceHeartbeat(payload);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/payments/tebex/webhook") {
		await handleTebexWebhook(request, response);
		return;
	}

	if (method === "POST" && url.pathname === "/api/oauth/google/nonce") {
		requireGoogleConfigured();
		const nonce = randomBytes(24).toString("base64url");
		await updateStore((store) => {
			pruneOauthStates(store);
			store.oauthStates.push({
				provider: "google_id_token",
				stateHash: hashToken(nonce),
				ipHash: abuseHash(clientIp(request)),
				createdAt: now(),
				expiresAt: Date.now() + oauthStateTtlMs
			});
		});
		json(response, 200, { nonce, expiresIn: Math.floor(oauthStateTtlMs / 1000) });
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

	if (method === "GET" && url.pathname === "/api/oauth/discord/start") {
		await startDiscordOAuth(request, response, url);
		return;
	}

	if (method === "GET" && url.pathname === "/api/oauth/discord/callback") {
		await completeDiscordOAuth(request, response, url);
		return;
	}

	if (method === "POST" && url.pathname === "/api/payments/subscription-cards/checkout") {
		requireTebexCheckoutConfigured();
		const payload = await readJson(request);
		if (Object.keys(payload).length !== 0) throw new HttpError(400, "invalid_checkout_request");
		const account = await reserveTebexCheckoutAttempt(request);
		json(response, 201, await createTebexSubscriptionCardCheckout(account, request));
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
		if (url.searchParams.get("availability") === "1") {
			json(response, 200, await openRscCharacterAvailability(username));
			return;
		}
		const snapshot = await openRscCharacterSnapshot(username);
		json(response, 200, { character: snapshot });
		return;
	}

	if (method === "POST" && url.pathname === "/api/founder/reservations") {
		if (!founderReservationsOpen()) {
			throw new HttpError(404, "not_found");
		}
		const payload = await readJson(request);
		await requireCaptcha(request, payload, "signup");
		const ip = clientIp(request);
		const result = await updateStore(async (store, transaction) => {
			assertSignupAllowed(store, request);
			const founder = await reserveFounder(store, payload);
			// Codes are minted only on this public landing route: registered portal
			// accounts get their starter card through the account-bound marker instead.
			ensureFounderSignupCode(store, founder);
			recordAbuseSignal(store, {
				signalType: "founder_signup_ip",
				signalValue: ip,
				bucket: dailyBucket(),
				metadata: { local: isLocalIp(ip) }
			});
			const subnetKey = ipv4SubnetKey(ip);
			if (subnetKey) {
				recordAbuseSignal(store, {
					signalType: "founder_signup_subnet",
					signalValue: subnetKey,
					bucket: dailyBucket(),
					metadata: { local: isLocalIp(ip) }
				});
			}
			try {
				if (await syncSignupCodeToOpenRsc(founder, transaction) && !founder.signupCodeSyncedAt) {
					founder.signupCodeSyncedAt = now();
				}
			} catch (error) {
				audit(store, "signup_code_sync_failed", { founderId: founder.id, error: String(error && error.message || error) });
			}
			const rewardReferrer = founder.referredByCode
				? store.founders.find((entry) => entry.code === founder.referredByCode)
				: null;
			try {
				await syncReferralRewardCodesToOpenRsc(rewardReferrer, transaction);
			} catch (error) {
				audit(store, "referral_reward_code_sync_failed", {
					founderId: rewardReferrer && rewardReferrer.id,
					error: String(error && error.message || error)
				});
			}
			return { founder: founderState(founder), signup: signupState(founder) };
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
		await requireCaptcha(request, payload, "signup");
		const password = requirePassword(payload.password);
		if (launchSignupMode) {
			requireLaunchFirstGamePassword(password);
		}
		const passwordHash = await hashPassword(password);
		const result = await updateStore(async (store, transaction) => {
			if (optionalSession(request, store)) {
				throw new HttpError(409, "already_signed_in");
			}
			assertAccountSignupIpAllowed(store, request);
			const emailCanonical = canonicalEmail(payload.email || "");
			if (!emailCanonical) throw new HttpError(400, "invalid_email");
			if (store.accounts.some((account) => account.emailCanonical === emailCanonical)) {
				throw new HttpError(409, "account_exists");
			}
			if (emailVerificationRequired) {
				if (!emailConfigured()) throw new HttpError(503, "email_verification_not_configured");
				assertEmailVerificationRequestAllowed(store, emailCanonical, request);
				const pendingResult = await queuePendingEmailVerificationSignup(store, payload, passwordHash, password, request);
				const pending = pendingResult.pending;
				recordEmailVerificationRequestSignals(store, pending.emailCanonical, request, pending.id);
				const queuedEmail = queueEmailVerification(store, pending, {
					origin: requestPublicOrigin(request)
				}, { force: true });
				audit(store, "email_verification_requested", {
					pendingSignupId: pending.id,
					reused: !pendingResult.created
				});
				return {
					verificationRequired: true,
					email: pending.emailDisplay || pending.emailCanonical,
					username: pending.username,
					expiresAt: pending.expiresAt,
					emailEventId: queuedEmail && queuedEmail.event.id
				};
			}

			const founder = await reserveFounder(store, payload);
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
			recordSignupSignals(store, account, request, {
				emailCanonical,
				provider: "password"
			});
			if (launchSignupMode) {
				await createLaunchFirstCharacter(store, account, {
					username: founder.username,
					gamePassword: password
				}, request, transaction);
			} else {
				const reservedCharacter = createCharacter(store, account.id, founder.username, "warrior");
				reservedCharacter.source = "founder-reserved";
				reservedCharacter.status = "Reserved username";
			}
			await grantStarterCardIfEligible(store, account, founder, "prelaunch_signup", request, {
				emailCanonical,
				provider: "password",
				signupSignalsRecorded: true
			}, transaction);
			const token = createSession(store, account.id);
			const queuedEmail = queueAccountEmail(store, account, signupConfirmationEmailType, {
				origin: requestPublicOrigin(request)
			});
			audit(store, "account_registered", { accountId: account.id });
			return {
				emailEventId: queuedEmail && queuedEmail.event.id,
				token,
				...(await accountState(store, account))
			};
		});
		const emailEventId = result.emailEventId;
		delete result.emailEventId;
		scheduleEmailDelivery(emailEventId);
		json(response, result.verificationRequired ? 202 : 201, result);
		return;
	}

	if (method === "GET" && url.pathname === "/api/accounts/verify-email") {
		const token = String(url.searchParams.get("token") || "").trim();
		const location = `/portal?auth=verify#verify=${encodeURIComponent(token)}`;
		response.writeHead(303, {
			location,
			"cache-control": "no-store",
			...securityHeaders("text/plain")
		});
		response.end();
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/verify-email") {
		const payload = await readJson(request);
		const result = await verifyEmailSignupToken(payload.token || "", request);
		json(response, 200, result.state);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/verify-email/resend") {
		const payload = await readJson(request);
		const emailCanonical = canonicalEmail(payload.email || "");
		const result = await updateStore((store) => {
			pruneEmailVerifications(store);
			const profile = clientAbuseProfile(request);
			assertClientIpNotBlocked(profile);
			const decision = emailVerificationRequestDecision(store, emailCanonical, request);
			const pending = emailCanonical
				? store.emailVerifications.find((entry) =>
					entry.emailCanonical === emailCanonical &&
					entry.status === "pending" &&
					Number(entry.expiresAtMs || 0) > Date.now()
				)
				: null;
			recordEmailVerificationRequestSignals(store, emailCanonical, request, pending && pending.id);
			if (!decision.allowed) {
				return { rateLimited: true, rule: decision.rule };
			}
			const queuedEmail = emailVerificationRequired && emailConfigured() && pending
				? queueEmailVerification(store, pending, {
					origin: requestPublicOrigin(request),
					source: "verification_resend"
				}, { force: true })
				: null;
			audit(store, "email_verification_resend_requested", {
				pendingSignupId: pending ? pending.id : null,
				queued: Boolean(queuedEmail && queuedEmail.event)
			});
			return {
				rateLimited: false,
				emailEventId: queuedEmail && queuedEmail.event.id
			};
		});
		if (result.rateLimited) {
			rejectRateLimit(request, result.rule);
		}
		scheduleEmailDelivery(result.emailEventId);
		json(response, 202, { accepted: true });
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/google") {
		requireGoogleConfigured();
		const payload = await readJson(request);
		await requireCaptcha(request, payload, "signup");
		const profile = await googleProfileFromCredential(payload);
		const result = await updateStore(async (store, transaction) => {
				consumeGoogleNonce(store, payload.nonce || "", request);
				const wasNewAccount = !findGoogleAccount(store, profile);
				const account = await upsertGoogleAccount(store, profile, payload, request, transaction);
				if (account.status !== "active") throw new HttpError(401, "invalid_account");
				if (launchSignupMode || payload.gamePassword || payload.characterPassword) {
					await createLaunchFirstCharacter(store, account, {
						username: payload.username || payload.reservedUsername || profile.username,
					gamePassword: payload.gamePassword || payload.characterPassword
				}, request, transaction);
			}
			const token = createSession(store, account.id);
			const queuedEmail = wasNewAccount
				? queueAccountEmail(store, account, signupConfirmationEmailType, {
					origin: requestPublicOrigin(request)
				})
				: null;
			audit(store, "account_google_login", {
				accountId: account.id,
				email: profile.emailCanonical
			});
			return {
				emailEventId: queuedEmail && queuedEmail.event.id,
				token,
				auth: {
					provider: googleIdentityProvider,
					emailVerified: profile.emailVerified
				},
				...(await accountState(store, account))
			};
		});
		const emailEventId = result.emailEventId;
		delete result.emailEventId;
		scheduleEmailDelivery(emailEventId);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/google/dev") {
		const payload = await readJson(request);
		const result = await updateStore(async (store, transaction) => {
				const profile = googleDevProfile(payload);
				const wasNewAccount = !findGoogleAccount(store, profile);
				const account = await upsertGoogleAccount(store, profile, payload, request, transaction);
				if (account.status !== "active") throw new HttpError(401, "invalid_account");
				const token = createSession(store, account.id);
			const queuedEmail = wasNewAccount
				? queueAccountEmail(store, account, signupConfirmationEmailType, {
					origin: requestPublicOrigin(request)
				})
				: null;
			audit(store, "account_google_login_dev", {
				accountId: account.id,
				email: profile.emailCanonical
			});
			return {
				emailEventId: queuedEmail && queuedEmail.event.id,
				token,
				auth: {
					provider: "google",
					devMode: true,
					emailVerified: profile.emailVerified
				},
				...(await accountState(store, account))
			};
		});
		const emailEventId = result.emailEventId;
		delete result.emailEventId;
		scheduleEmailDelivery(emailEventId);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/logout") {
		await updateStore(async (store) => {
			revokeCurrentSession(request, store);
		});
		json(response, 200, { ok: true });
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/password-reset/request") {
		if (!emailConfigured()) throw new HttpError(503, "password_reset_email_unavailable");
		const payload = await readJson(request);
		const identifier = String(payload.identifier || payload.email || payload.username || "").trim().slice(0, 254);
		const ip = clientIp(request);
		const origin = requestPublicOrigin(request);
		const result = await updateStore(async (store) => {
			prunePasswordResetTokens(store);
			const resolution = resolvePasswordResetAccount(store, identifier);
			const since = Date.now() - passwordResetRequestWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "password_reset_request_ip", ip, since) >= passwordResetRequestIpLimit) {
				throw new HttpError(429, "rate_limited");
			}
			const accountSignal = resolution.account ? String(resolution.account.id) : resolution.signalValue;
			if (accountSignal && countAbuseSignals(store, "password_reset_request_account", accountSignal, since) >= passwordResetRequestAccountLimit) {
				throw new HttpError(429, "rate_limited");
			}
			recordAbuseSignal(store, {
				accountId: resolution.account ? resolution.account.id : null,
				signalType: "password_reset_request_ip",
				signalValue: ip,
				bucket: dailyBucket(),
				metadata: { local: isLocalIp(ip), matched: Boolean(resolution.account) }
			});
			if (accountSignal) recordAbuseSignal(store, {
				accountId: resolution.account ? resolution.account.id : null,
				signalType: "password_reset_request_account",
				signalValue: accountSignal,
				bucket: dailyBucket(),
				metadata: { matched: Boolean(resolution.account), identifierType: resolution.identifierType }
			});

			let emailEventId = null;
			if (resolution.account && resolution.account.status === "active" && deliverableAccountEmail(resolution.account)) {
				const queued = queuePasswordResetEmail(store, resolution.account, {
					origin,
					requestIp: ip,
					identifierType: resolution.identifierType
				});
				emailEventId = queued && queued.event.id;
			}
			audit(store, "account_password_reset_requested", {
				accountId: resolution.account ? resolution.account.id : null,
				identifierType: resolution.identifierType,
				queued: Boolean(emailEventId)
			});
			return {
				accepted: true,
				maskedEmail: resolution.identifierType === "username" && emailEventId
					? maskEmailAddress(resolution.account.emailDisplay || resolution.account.emailCanonical)
					: "",
				emailEventId
			};
		});
		const emailEventId = result.emailEventId;
		delete result.emailEventId;
		scheduleEmailDelivery(emailEventId);
		json(response, 202, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/password-reset/complete") {
		const payload = await readJson(request);
		const token = requirePasswordResetToken(payload.token || "");
		const ip = clientIp(request);
		const result = await updateStore(async (store) => {
			prunePasswordResetTokens(store);
			const since = Date.now() - recoveryFailureWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "password_reset_failed_ip", ip, since) >= recoveryFailureLimit) {
				throw new HttpError(429, "rate_limited");
			}
			const tokenHash = hashToken(token);
			const reset = store.passwordResetTokens.find((entry) => entry.tokenHash === tokenHash);
			const account = reset ? store.accounts.find((entry) => entry.id === reset.accountId) : null;
			if (!reset || reset.status !== "pending" || Number(reset.expiresAtMs || 0) <= Date.now() || !account || account.status !== "active") {
				recordAbuseSignal(store, {
					accountId: account ? account.id : null,
					signalType: "password_reset_failed_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { local: isLocalIp(ip), matched: Boolean(reset) }
				});
				return { error: "invalid_password_reset_token" };
			}

			const passwordHash = await hashPassword(requirePassword(payload.newPassword));
			const changedAt = now();
			reset.status = "used";
			reset.usedAt = changedAt;
			reset.updatedAt = changedAt;
			store.passwordResetTokens.forEach((entry) => {
				if (entry.accountId === account.id && entry.id !== reset.id && entry.status === "pending") {
					entry.status = "revoked";
					entry.revokedAt = changedAt;
					entry.updatedAt = changedAt;
				}
			});
			revokeRecoveryCodes(store, account.id, changedAt);
			account.passwordHash = passwordHash;
			account.passwordChangedAt = changedAt;
			account.emailVerifiedAt = account.emailVerifiedAt || changedAt;
			account.updatedAt = changedAt;
			const revoked = revokeAccountSessions(store, account.id);
			audit(store, "account_password_reset_completed", { accountId: account.id, revoked });
			return { ok: true };
		});
		if (result.error) throw new HttpError(401, result.error);
		json(response, 200, result);
		return;
	}

	if (method === "GET" && url.pathname === "/api/accounts/legacy-claim/verify") {
		const token = String(url.searchParams.get("token") || "").trim();
		const location = `/portal?auth=claim-confirm#claim=${encodeURIComponent(token)}`;
		response.writeHead(303, {
			location,
			"cache-control": "no-store",
			...securityHeaders("text/plain")
		});
		response.end();
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/legacy-claim/request") {
		if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
		if (!emailConfigured()) throw new HttpError(503, "legacy_claim_email_unavailable");
		const payload = await readJson(request);
		const username = cleanUsername(payload.username || payload.characterUsername || "");
		const normalizedName = normalizeUsername(username);
		if (!normalizedName) throw new HttpError(400, "invalid_username");
		const currentGamePassword = requireLegacyGamePassword(payload.currentGamePassword || payload.gamePassword || "");
		const targetEmailDisplay = String(payload.email || payload.newEmail || "").trim();
		if (targetEmailDisplay.length > 254) throw new HttpError(400, "invalid_email");
		const targetEmailCanonical = canonicalEmail(targetEmailDisplay);
		if (!targetEmailCanonical || !deliverableEmailAddress(targetEmailCanonical)) {
			throw new HttpError(400, "invalid_email");
		}
		const newWebsitePassword = requirePassword(payload.newPassword || payload.websitePassword || "");
		const ip = clientIp(request);
		const origin = requestPublicOrigin(request);
		const result = await updateStore(async (store) => {
			if (optionalSession(request, store)) throw new HttpError(409, "already_signed_in");
			pruneLegacyAccountClaims(store);
			const since = Date.now() - legacyAccountClaimWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "legacy_claim_request_ip", ip, since) >= legacyAccountClaimIpLimit) {
				throw new HttpError(429, "rate_limited");
			}
			const characterIpSignal = `${normalizedName}:${ip}`;
			if (countAbuseSignals(store, "legacy_claim_request_character_ip", characterIpSignal, since) >= legacyAccountClaimCharacterLimit) {
				throw new HttpError(429, "rate_limited");
			}
			recordAbuseSignal(store, {
				signalType: "legacy_claim_request_ip",
				signalValue: ip,
				bucket: dailyBucket(),
				metadata: { local: isLocalIp(ip) }
			});
			recordAbuseSignal(store, {
				signalType: "legacy_claim_request_character_ip",
				signalValue: characterIpSignal,
				bucket: dailyBucket(),
				metadata: {}
			});

			let subject;
			try {
				subject = await legacyAccountClaimSubject(store, normalizedName);
			} catch (error) {
				if (error instanceof HttpError && error.status >= 500) {
					return { systemError: { status: error.status, message: error.message } };
				}
				throw error;
			}
			let passwordMatches = false;
			try {
				passwordMatches = await verifyCanonicalGamePassword(
					currentGamePassword,
					subject ? subject.player.salt : "",
					subject ? subject.player.pass : legacyClaimDummyPasswordHash
				);
				if (subject && !subject.player.pass.startsWith("$2y$10$")) {
					await verifyCanonicalGamePassword(currentGamePassword, "", legacyClaimDummyPasswordHash);
				}
			} catch (error) {
				if (error instanceof HttpError && error.status >= 500) {
					return { systemError: { status: error.status, message: error.message } };
				}
				throw error;
			}
			if (!subject || !passwordMatches) return { claimError: "invalid_legacy_claim", status: 401 };
			if (store.accounts.some((account) =>
				account.emailCanonical === targetEmailCanonical && Number(account.id) !== Number(subject.account.id)
			)) {
				return { claimError: "legacy_claim_unavailable", status: 409 };
			}

			const passwordHash = await hashPassword(newWebsitePassword);
			const queued = queueLegacyAccountClaim(store, subject, {
				targetEmailCanonical,
				targetEmailDisplay,
				passwordHash,
				requestIp: ip,
				origin
			});
			audit(store, "legacy_account_claim_requested", {
				accountId: subject.account.id,
				characterId: subject.character.id,
				playerId: subject.player.id,
				claimId: queued.claim.id
			});
			return {
				accepted: true,
				verificationRequired: true,
				expiresAt: queued.claim.expiresAt,
				emailEventId: queued.event.id
			};
		});
		if (result.systemError) throw new HttpError(result.systemError.status, result.systemError.message);
		if (result.claimError) throw new HttpError(result.status || 401, result.claimError);
		const emailEventId = result.emailEventId;
		delete result.emailEventId;
		scheduleEmailDelivery(emailEventId);
		json(response, 202, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/legacy-claim/complete") {
		if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
		const payload = await readJson(request);
		const token = requireLegacyAccountClaimToken(payload.token || "");
		const tokenHash = hashToken(token);
		const ip = clientIp(request);
		const previewStore = await loadStore();
		const lockGameWrites = previewStore.legacyAccountClaims.some((entry) =>
			entry.tokenHash === tokenHash
			&& entry.status === "pending"
			&& Number(entry.expiresAtMs || 0) > Date.now()
		);
		const result = await updateStore(async (store) => {
			if (optionalSession(request, store)) throw new HttpError(409, "already_signed_in");
			pruneLegacyAccountClaims(store);
			const since = Date.now() - recoveryFailureWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "legacy_claim_complete_failed_ip", ip, since) >= recoveryFailureLimit) {
				throw new HttpError(429, "rate_limited");
			}
			const claim = store.legacyAccountClaims.find((entry) => entry.tokenHash === tokenHash);
			const account = claim ? store.accounts.find((entry) => Number(entry.id) === Number(claim.accountId)) : null;
			if (!claim || claim.status !== "pending" || Number(claim.expiresAtMs || 0) <= Date.now() || !account) {
				recordAbuseSignal(store, {
					accountId: account ? account.id : null,
					signalType: "legacy_claim_complete_failed_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { local: isLocalIp(ip), matched: Boolean(claim) }
				});
				return { claimError: "invalid_legacy_claim_token", status: 401 };
			}

			let subject;
			try {
				subject = await legacyAccountClaimSubject(store, claim.normalizedName);
			} catch (error) {
				if (error instanceof HttpError && error.status >= 500) {
					return { systemError: { status: error.status, message: error.message } };
				}
				throw error;
			}
			const subjectChanged = !subject
				|| Number(subject.account.id) !== Number(claim.accountId)
				|| Number(subject.character.id) !== Number(claim.characterId)
				|| Number(subject.player.id) !== Number(claim.playerId)
				|| !constantTimeMatches(subject.credentialFingerprint, claim.credentialFingerprint);
			if (subjectChanged) {
				revokeLegacyAccountClaim(claim, "revoked");
				audit(store, "legacy_account_claim_rejected_changed", {
					accountId: account.id,
					claimId: claim.id
				});
				return { claimError: "legacy_claim_changed", status: 409 };
			}
			if (store.accounts.some((entry) =>
				entry.emailCanonical === claim.targetEmailCanonical && Number(entry.id) !== Number(account.id)
			)) {
				revokeLegacyAccountClaim(claim, "revoked");
				return { claimError: "legacy_claim_unavailable", status: 409 };
			}

			const changedAt = now();
			const oldEmailCanonical = account.emailCanonical;
			const pendingPasswordHash = claim.passwordHash;
			claim.status = "used";
			claim.usedAt = changedAt;
			claim.updatedAt = changedAt;
			claim.passwordHash = null;
			claim.credentialFingerprint = "";
			account.emailCanonical = claim.targetEmailCanonical;
			account.emailDisplay = claim.targetEmailDisplay || claim.targetEmailCanonical;
			account.passwordHash = pendingPasswordHash;
			account.emailVerifiedAt = changedAt;
			account.passwordChangedAt = changedAt;
			account.syntheticEmail = false;
			account.requiresEmailUpgrade = false;
			account.legacyClaimedAt = changedAt;
			account.updatedAt = changedAt;
			for (const founder of store.founders) {
				if (Number(founder.accountId || 0) !== Number(account.id)) continue;
				founder.emailCanonical = account.emailCanonical;
				founder.emailDisplay = account.emailDisplay;
				founder.updatedAt = changedAt;
			}
			revokeLegacyAccountClaims(store, account.id, changedAt, claim.id);
			revokePasswordResetTokens(store, account.id, changedAt);
			revokeRecoveryCodes(store, account.id, changedAt);
			const revokedSessions = revokeAccountSessions(store, account.id);
			audit(store, "legacy_account_claim_completed", {
				accountId: account.id,
				characterId: claim.characterId,
				playerId: claim.playerId,
				claimId: claim.id,
				revokedSessions,
				replacedSyntheticEmail: Boolean(oldEmailCanonical && oldEmailCanonical.endsWith(".invalid"))
			});
			return { ok: true };
		}, lockGameWrites ? { lockOpenRscWrites: true } : {});
		if (result.systemError) throw new HttpError(result.systemError.status, result.systemError.message);
		if (result.claimError) throw new HttpError(result.status || 401, result.claimError);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/recover-password") {
		const payload = await readJson(request);
		const ip = clientIp(request);
		const result = await updateStore(async (store) => {
			const emailCanonical = canonicalEmail(payload.email || "");
			const code = normalizeCode(payload.code || payload.recoveryCode || "");
			const since = Date.now() - recoveryFailureWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "recovery_code_failed_ip", ip, since) >= recoveryFailureLimit) {
				throw new HttpError(429, "rate_limited");
			}
			if (emailCanonical && countAbuseSignals(store, "recovery_code_failed_email", emailCanonical, since) >= recoveryFailureLimit) {
				throw new HttpError(429, "rate_limited");
			}
			if (!emailCanonical || !code) {
				recordAbuseSignal(store, {
					accountId: null,
					signalType: "recovery_code_failed_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { hasAccount: false, local: isLocalIp(ip) }
				});
				if (emailCanonical) recordAbuseSignal(store, {
					accountId: null,
					signalType: "recovery_code_failed_email",
					signalValue: emailCanonical,
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
					signalType: "recovery_code_failed_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { hasAccount: Boolean(account), local: isLocalIp(ip) }
				});
				recordAbuseSignal(store, {
					accountId: account ? account.id : null,
					signalType: "recovery_code_failed_email",
					signalValue: emailCanonical,
					bucket: dailyBucket(),
					metadata: { hasAccount: Boolean(account) }
				});
				return { error: "invalid_recovery_code" };
			}
			const passwordHash = await hashPassword(requirePassword(payload.newPassword));
			const changedAt = now();
			recoveryCode.status = "used";
			recoveryCode.usedAt = changedAt;
			recoveryCode.revokedAt = null;
			revokeRecoveryCodes(store, account.id, changedAt, recoveryCode.id);
			revokePasswordResetTokens(store, account.id, changedAt);
			account.passwordHash = passwordHash;
			account.passwordChangedAt = changedAt;
			account.updatedAt = changedAt;
			const revoked = revokeAccountSessions(store, account.id);
			audit(store, "account_recovered_with_code", { accountId: account.id, revoked });
			return { ok: true };
		});
		if (result.error) throw new HttpError(401, result.error);
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/accounts/login") {
		const payload = await readJson(request);
		const ip = clientIp(request);
		const emailCanonical = canonicalEmail(payload.email || "");
		const result = await updateStore(async (store) => {
			if (optionalSession(request, store)) throw new HttpError(409, "already_signed_in");
			const since = Date.now() - loginFailureWindowMs;
			if (!isLocalIp(ip)) {
				if (countAbuseSignals(store, "login_failure_ip", ip, since) >= loginIpFailureLimit) {
					throw new HttpError(429, "rate_limited");
				}
			}
			if (emailCanonical && countAbuseSignals(store, "login_failure_email", emailCanonical, since) >= loginEmailFailureLimit) {
				throw new HttpError(429, "rate_limited");
			}
			const account = store.accounts.find((entry) => entry.emailCanonical === emailCanonical);
			if (!account || !(await verifyPassword(String(payload.password || ""), account.passwordHash)) || account.status !== "active") {
				// updateStore only persists when the mutator resolves, so the failure
				// signal must ride a returned marker; the 401 is thrown outside.
				recordAbuseSignal(store, {
					signalType: "login_failure_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { local: isLocalIp(ip) }
				});
				if (emailCanonical) recordAbuseSignal(store, {
					accountId: account ? account.id : null,
					signalType: "login_failure_email",
					signalValue: emailCanonical,
					bucket: dailyBucket(),
					metadata: { hasAccount: Boolean(account), accountStatus: account ? account.status || "" : "" }
				});
				return { loginFailed: true };
			}
			const token = createSession(store, account.id);
			audit(store, "account_login", { accountId: account.id });
			return { token, ...(await accountState(store, account)) };
		});
		if (result && result.loginFailed) throw new HttpError(401, "invalid_credentials");
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/characters") {
		const payload = await readJson(request);
		await requireCaptcha(request, payload, "character");
		const result = await updateStore(async (store, transaction) => {
			const account = requireAccount(request, store);
			await createAccountCharacter(store, account, payload, request, { transaction });
			account.updatedAt = now();
			audit(store, "character_created", { accountId: account.id });
			return accountState(store, account);
		});
		json(response, 201, result);
		return;
	}

	const deleteCharacterMatch = /^\/api\/characters\/(\d+)$/.exec(url.pathname);
	if (method === "DELETE" && deleteCharacterMatch) {
		const characterId = Number(deleteCharacterMatch[1]);
		const result = await updateStore(async (store, transaction) => {
			const account = requireAccount(request, store);
			const deleted = await deleteAccountCharacter(store, account, characterId, transaction);
			account.updatedAt = now();
			audit(store, "character_deleted", {
				accountId: account.id,
				characterId: deleted.id,
				playerId: deleted.playerId || null,
				source: deleted.source || "portal-preview",
				openRscDeleted: Boolean(deleted.openRscDeleted)
			});
			return accountState(store, account);
		});
		json(response, 200, result);
		return;
	}

	const gamePasswordMatch = /^\/api\/characters\/(\d+)\/game-password$/.exec(url.pathname);
	if (method === "POST" && gamePasswordMatch) {
		const characterId = Number(gamePasswordMatch[1]);
		const payload = await readJson(request);
		const newGamePassword = requireGamePassword(payload.newPassword || payload.gamePassword || "");
		const ip = clientIp(request);
		const authorization = await updateStore(async (store) => {
			const { account, session } = requireSession(request, store);
			const since = Date.now() - gamePasswordResetWindowMs;
			if (!isLocalIp(ip) && countAbuseSignals(store, "game_password_reset_ip", ip, since) >= gamePasswordResetLimit) {
				throw new HttpError(429, "rate_limited");
			}
			if (countAbuseSignals(store, "game_password_reset_account", String(account.id), since) >= gamePasswordResetLimit) {
				throw new HttpError(429, "rate_limited");
			}
			recordAbuseSignal(store, {
				accountId: account.id,
				signalType: "game_password_reset_ip",
				signalValue: ip,
				bucket: dailyBucket(),
				metadata: { local: isLocalIp(ip), characterId }
			});
			recordAbuseSignal(store, {
				accountId: account.id,
				signalType: "game_password_reset_account",
				signalValue: String(account.id),
				bucket: dailyBucket(),
				metadata: { characterId }
			});
			try {
				await requireSensitiveAccountAuth(store, account, session, payload.currentPassword || "");
			} catch (error) {
				if (error instanceof HttpError) return { error: error.message, status: error.status };
				throw error;
			}
			const character = store.characters.find((entry) =>
				Number(entry.id) === characterId && Number(entry.accountId) === Number(account.id)
			);
			if (!character) return { error: "character_not_found", status: 404 };
			if (character.source !== "openrsc-sqlite-created" || character.linkStatus !== "linked") {
				return { error: "character_game_password_reset_unsupported", status: 409 };
			}
			if (!Number(character.playerId)) return { error: "character_game_login_unavailable", status: 409 };
			return {
				accountId: account.id,
				character: {
					id: character.id,
					name: character.name,
					playerId: character.playerId,
					source: character.source,
					linkStatus: character.linkStatus
				}
			};
		});
		if (authorization.error) throw new HttpError(authorization.status || 400, authorization.error);
		const changedAt = await updateOpenRscPlayerPassword(authorization.character, authorization.accountId, newGamePassword, ip);
		const result = await updateStore((store) => {
			const account = store.accounts.find((entry) => entry.id === authorization.accountId);
			const character = store.characters.find((entry) =>
				Number(entry.id) === characterId && Number(entry.accountId) === Number(authorization.accountId)
			);
			if (character) {
				character.gamePasswordChangedAt = changedAt;
				character.updatedAt = changedAt;
			}
			if (account) account.updatedAt = changedAt;
			audit(store, "character_game_password_changed", {
				accountId: authorization.accountId,
				characterId,
				playerId: authorization.character.playerId
			});
			return { ok: true, characterId, changedAt };
		});
		json(response, 200, result);
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
			const revoked = revokeOtherAccountSessions(store, account.id, session.id);
			revokePasswordResetTokens(store, account.id);
			audit(store, "account_password_changed", { accountId: account.id, revoked });
			return accountState(store, account, session);
		});
		json(response, 200, result);
		return;
	}

	if (method === "POST" && url.pathname === "/api/security/recovery-codes") {
		const payload = await readJson(request);
		const result = await updateStore(async (store) => {
			const { account, session } = requireSession(request, store);
			await requireSensitiveAccountAuth(store, account, session, payload.currentPassword || "");
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

	if (method === "GET" && url.pathname === "/api/admin/funnel") {
		requireAdmin(request);
		const store = await loadStore();
		json(response, 200, funnelSummary(store));
		return;
	}

	if (method === "GET" && url.pathname === "/api/admin/presence") {
		requireAdmin(request);
		json(response, 200, presenceSummary());
		return;
	}

	if (method === "GET" && url.pathname === "/api/admin/commerce") {
		requireAdmin(request);
		json(response, 200, await commerceAdminList(commerceAdminLimit(url.searchParams.get("limit") || 100)));
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/commerce/reconcile") {
		requireAdmin(request);
		json(response, 200, await commerceReconcileReport());
		return;
	}

	if (method === "GET" && url.pathname === "/api/admin/signups") {
		requireAdmin(request);
		const store = await loadStore();
		const gameValues = await signupCodeGameValues();
		const rows = store.founders.map((founder) => {
			const rewardCodes = Array.isArray(founder.referralRewardCodes)
				? founder.referralRewardCodes.filter((reward) => reward && reward.code).map((reward) => reward.code)
				: [];
			return {
				id: founder.id,
				username: founder.username,
				email: founder.emailDisplay || founder.emailCanonical,
				betaTester: Boolean(founder.betaTester),
				discordUserId: founder.discordUserId || "",
				discordDisplayName: founder.discordDisplayName || "",
				code: founder.signupCode || "",
				status: signupCodeStatus(founder, gameValues),
				referralRewardCodeCount: rewardCodes.length,
				referralRewardCodes: rewardCodes.join("|"),
				createdAt: founder.createdAt
			};
		});
		if ((url.searchParams.get("format") || "").toLowerCase() === "csv") {
			const header = "id,username,email,betaTester,discordUserId,discordDisplayName,code,status,referralRewardCodeCount,referralRewardCodes,createdAt";
			const lines = rows.map((row) =>
				[
					row.id,
					row.username,
					row.email,
					row.betaTester,
					row.discordUserId,
					row.discordDisplayName,
					row.code,
					row.status,
					row.referralRewardCodeCount,
					row.referralRewardCodes,
					row.createdAt
				].map(csvCell).join(","));
			response.writeHead(200, {
				"content-type": "text/csv; charset=utf-8",
				"content-disposition": "attachment; filename=\"voidscape-signups.csv\"",
				"cache-control": "no-store",
				...securityHeaders("text/csv")
			});
			response.end([header, ...lines].join("\n") + "\n");
			return;
		}
		json(response, 200, { signups: rows });
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/signups/sync") {
		requireAdmin(request);
		if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
		const result = await updateStore(async (store) => {
			let synced = 0;
			let skippedRedeemed = 0;
			let failed = 0;
			let noCode = 0;
			const gameValues = await signupCodeGameValues();
			for (const founder of store.founders) {
				if (!founder.signupCode) {
					noCode += 1;
					continue;
				}
				if (gameValues.get(signupCodeCacheKey(founder.signupCode)) === signupCodeRedeemed) {
					skippedRedeemed += 1;
					continue;
				}
				try {
					await syncSignupCodeToOpenRsc(founder);
					if (!founder.signupCodeSyncedAt) founder.signupCodeSyncedAt = now();
					synced += 1;
				} catch (error) {
					failed += 1;
				}
			}
			audit(store, "signup_codes_resynced", { synced, skippedRedeemed, failed, noCode });
			return { synced, skippedRedeemed, failed, noCode };
		});
		json(response, 200, result);
		return;
	}

	if (method === "GET" && url.pathname === "/api/admin/emails") {
		requireAdmin(request);
		const store = await loadStore();
		const status = String(url.searchParams.get("status") || "").trim();
		const type = String(url.searchParams.get("type") || "").trim();
		const limit = adminEmailLimit(url.searchParams.get("limit") || 100);
		const events = store.emailEvents
			.filter((event) => !status || event.status === status)
			.filter((event) => !type || event.type === type)
			.slice(-limit)
			.reverse()
			.map((event) => emailEventState(store, event));
		json(response, 200, {
			config: portalConfigHealth().email,
			summary: emailEventSummary(store),
			events
		});
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/emails/send-pending") {
		requireAdmin(request);
		const payload = await readJson(request);
		const limit = adminEmailLimit(payload.limit || 100);
		const retryFailed = Boolean(payload.retryFailed);
		const eventIds = await updateStore((store) => {
			const eligible = store.emailEvents
				.filter((event) => event.status === "pending" || (retryFailed && event.status === "failed"))
				.slice(0, limit);
			audit(store, "admin_email_pending_requested", {
				count: eligible.length,
				retryFailed
			});
			return eligible.map((event) => event.id);
		});
		const delivery = await deliverEmailEvents(eventIds);
		json(response, 200, { requested: eventIds.length, ...delivery });
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/emails/signup-confirmations") {
		requireAdmin(request);
		const payload = await readJson(request);
		const result = await queueAdminBulkEmail(request, signupConfirmationEmailType, payload, {
			requireFounder: true,
			auditType: "admin_signup_confirmation_email_requested"
		});
		const delivery = result.dryRun ? { sent: 0, failed: 0, skipped: 0 } : await deliverEmailEvents(result.eventIds);
		delete result.eventIds;
		json(response, 200, { ...result, ...delivery });
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/emails/launch-48h") {
		requireAdmin(request);
		const payload = await readJson(request);
		const result = await queueAdminBulkEmail(request, launchReminderEmailType, payload, {
			requireFounder: false,
			auditType: "admin_launch_48h_email_requested"
		});
		const delivery = result.dryRun ? { sent: 0, failed: 0, skipped: 0 } : await deliverEmailEvents(result.eventIds);
		delete result.eventIds;
		json(response, 200, { ...result, ...delivery });
		return;
	}

	if (method === "POST" && url.pathname === "/api/admin/emails/launch-live") {
		requireAdmin(request);
		const payload = await readJson(request);
		const result = await queueAdminBulkEmail(request, launchLiveEmailType, payload, {
			requireFounder: false,
			auditType: "admin_launch_live_email_requested"
		});
		const delivery = result.dryRun ? { sent: 0, failed: 0, skipped: 0 } : await deliverEmailEvents(result.eventIds);
		delete result.eventIds;
		json(response, 200, { ...result, ...delivery });
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

	if (method === "POST" && url.pathname === "/api/admin/accounts/backfill-native") {
		requireAdmin(request);
		const payload = await readJson(request);
		const dryRun = payload.apply !== true;
		const grantMissingStarterCard = payload.grantMissingStarterCard !== false;
		const grantLaunchCharacterCards = payload.grantLaunchCharacterCards !== false;
		const asOfMs = normalizeNativeBackfillAsOfMs(payload.asOfMs, dryRun);
		const approvedExceptionPlayerIds = normalizePositiveIntegerList(payload.approvedExceptionPlayerIds);
		const excludedExceptionPlayerIds = normalizePositiveIntegerList(payload.excludedExceptionPlayerIds);
		const reviewToken = String(payload.reviewToken || "").trim();
		if (dryRun) {
			const store = await loadStore();
			const result = await backfillNativePortalAccounts(cloneJson(store), {
				dryRun: true,
				grantMissingStarterCard,
				grantLaunchCharacterCards,
				asOfMs,
				approvedExceptionPlayerIds,
				excludedExceptionPlayerIds
			});
			json(response, 200, result);
			return;
		}
		const result = await updateStore(async (store, transaction) => {
			const backfill = await backfillNativePortalAccounts(store, {
				dryRun: false,
				grantMissingStarterCard,
				grantLaunchCharacterCards,
				asOfMs,
				approvedExceptionPlayerIds,
				excludedExceptionPlayerIds,
				reviewToken,
				deferGameWrites: true
			});
			const pendingGameWrites = backfill.pendingGameWrites || {};
			delete backfill.pendingGameWrites;
			transaction.afterPersist(() => applyNativeBackfillGameWrites(pendingGameWrites));
			if (backfill.createdAccounts || backfill.createdCharacters || backfill.updatedCharacters
				|| backfill.linkedPlayers || backfill.starterCardsGranted || backfill.launchCharacterCardsSeeded
				|| backfill.launchCharacterCardCutoverWillSeal || backfill.subscriptionsMigrated || backfill.conflicts.length) {
				audit(store, "admin_native_accounts_backfilled", {
					createdAccounts: backfill.createdAccounts,
					createdCharacters: backfill.createdCharacters,
					updatedCharacters: backfill.updatedCharacters,
					linkedPlayers: backfill.linkedPlayers,
					starterCardsGranted: backfill.starterCardsGranted,
					starterCardsSkippedAfterLaunchSeal: backfill.starterCardsSkippedAfterLaunchSeal,
					grantLaunchCharacterCards: backfill.grantLaunchCharacterCards,
					launchCharacterCardsSeeded: backfill.launchCharacterCardsSeeded,
					launchCharacterCardsAlreadyPresent: backfill.launchCharacterCardsAlreadyPresent,
					launchCharacterCardsSkippedAfterSeal: backfill.launchCharacterCardsSkippedAfterSeal,
					launchCharacterCardCutoverWillSeal: backfill.launchCharacterCardCutoverWillSeal,
					launchCharacterCardCutoverSealed: backfill.launchCharacterCardCutoverSealed,
					launchCharacterCardCutoverAlreadySealed: backfill.launchCharacterCardCutoverAlreadySealed,
					asOfMs: backfill.cohort.asOfMs,
					subscriptionsMigrated: backfill.subscriptionsMigrated,
					cohortPolicy: backfill.cohort.policy,
					cohortReviewTokenPrefix: backfill.cohort.reviewToken.slice(0, 12),
					approvedExceptions: backfill.cohort.approvedExceptionPlayerIds.length,
					excludedExceptions: backfill.cohort.excludedExceptionPlayerIds.length,
					conflicts: backfill.conflicts.length
				});
			}
			return backfill;
		});
		json(response, 200, result);
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
					await revokeStarterCardFromOpenRsc(account);
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

async function recordFunnelClick(request, payload) {
	const event = sanitizeFunnelEvent(payload && payload.event);
	const metadata = {
		event,
		target: sanitizeFunnelText(payload && payload.target, 80),
		href: sanitizeFunnelHref(payload && payload.href),
		page: sanitizeFunnelPath(payload && payload.page),
		referrer: sanitizeFunnelReferrer(payload && payload.referrer),
		utm: sanitizeFunnelUtm(payload && payload.utm),
		publicMode: Boolean(publicMode),
		betaMode: Boolean(betaMode),
		userAgent: sanitizeFunnelText(request.headers["user-agent"], 120)
	};
	await updateStore((store) => {
		audit(store, "funnel_click", metadata);
		trimFunnelClicks(store);
		return null;
	});
	return { ok: true, event };
}

function recordPresenceHeartbeat(payload) {
	const visitorId = sanitizePresenceVisitorId(payload && payload.visitorId);
	const observedAtMs = Date.now();
	const observedAt = now();
	prunePresence(observedAtMs);

	const page = sanitizePresencePath(payload && payload.page);
	const pageInfo = presencePageInfo(page);
	const visitorHash = hashToken(`${abuseHashSalt}:presence:${visitorId}`);
	const entry = presenceVisitors.get(visitorHash) || {
		visitorHash,
		firstSeenAt: observedAt,
		firstSeenAtMs: observedAtMs
	};
	entry.page = page;
	entry.pageKey = pageInfo.key;
	entry.pageLabel = pageInfo.label;
	entry.title = sanitizeFunnelText(payload && payload.title, 80);
	entry.visible = payload && payload.visible !== false;
	entry.lastSeenAt = observedAt;
	entry.lastSeenAtMs = observedAtMs;
	presenceVisitors.set(visitorHash, entry);
	return {
		ok: true,
		heartbeatSeconds: presenceHeartbeatSeconds,
		activeWindowSeconds: Math.round(presenceActiveWindowMs / 1000),
		recentWindowSeconds: Math.round(presenceRecentWindowMs / 1000)
	};
}

function presenceSummary() {
	const observedAtMs = Date.now();
	prunePresence(observedAtMs);
	const rows = Array.from(presenceVisitors.values());
	const activeRows = rows.filter((entry) => entry.lastSeenAtMs >= observedAtMs - presenceActiveWindowMs);
	const recentRows = rows.filter((entry) => entry.lastSeenAtMs >= observedAtMs - presenceRecentWindowMs);
	return {
		generatedAt: now(),
		heartbeatSeconds: presenceHeartbeatSeconds,
		windows: {
			activeSeconds: Math.round(presenceActiveWindowMs / 1000),
			recentSeconds: Math.round(presenceRecentWindowMs / 1000)
		},
		active: summarizePresenceRows(activeRows),
		recent: summarizePresenceRows(recentRows),
		visitors: activeRows
			.slice()
			.sort((left, right) => right.lastSeenAtMs - left.lastSeenAtMs)
			.slice(0, 20)
			.map((entry) => ({
				page: entry.page,
				pageLabel: entry.pageLabel,
				visible: Boolean(entry.visible),
				firstSeenAt: entry.firstSeenAt,
				lastSeenAt: entry.lastSeenAt,
				ageSeconds: Math.max(0, Math.round((observedAtMs - entry.lastSeenAtMs) / 1000))
			}))
	};
}

function summarizePresenceRows(rows) {
	const pages = new Map();
	for (const entry of rows) {
		const key = entry.pageKey || "other";
		const current = pages.get(key) || {
			key,
			label: entry.pageLabel || "Other",
			count: 0,
			visible: 0,
			lastSeenAt: ""
		};
		current.count += 1;
		if (entry.visible) current.visible += 1;
		if (!current.lastSeenAt || Date.parse(entry.lastSeenAt || "") > Date.parse(current.lastSeenAt || "")) {
			current.lastSeenAt = entry.lastSeenAt;
		}
		pages.set(key, current);
	}
	return {
		total: rows.length,
		visible: rows.filter((entry) => entry.visible).length,
		pages: Array.from(pages.values())
			.sort((left, right) => right.count - left.count || left.label.localeCompare(right.label))
	};
}

function prunePresence(observedAtMs) {
	for (const [visitorHash, entry] of presenceVisitors.entries()) {
		if (!entry || entry.lastSeenAtMs < observedAtMs - presenceRetentionMs) {
			presenceVisitors.delete(visitorHash);
		}
	}
	if (presenceVisitors.size <= presenceMaxVisitors) return;
	const stale = Array.from(presenceVisitors.values())
		.sort((left, right) => left.lastSeenAtMs - right.lastSeenAtMs)
		.slice(0, presenceVisitors.size - presenceMaxVisitors);
	for (const entry of stale) {
		presenceVisitors.delete(entry.visitorHash);
	}
}

function sanitizePresenceVisitorId(value) {
	const text = String(value || "").trim();
	if (!/^[A-Za-z0-9_-]{16,96}$/.test(text)) {
		throw new HttpError(400, "invalid_visitor_id");
	}
	return text;
}

function sanitizePresencePath(value) {
	const text = sanitizeFunnelText(value, 220);
	if (!text) return "/";
	try {
		const parsed = new URL(text, "https://voidscape.gg");
		return normalizePresencePath(parsed.pathname || "/");
	} catch (error) {
		return normalizePresencePath(text.split(/[?#]/)[0] || "/");
	}
}

function normalizePresencePath(value) {
	let path = String(value || "/").trim();
	if (!path.startsWith("/")) path = "/";
	path = path.replace(/\/+$/g, "");
	return path || "/";
}

function presencePageInfo(path) {
	if (path === "/" || path === "/index.html") return { key: "landing", label: "Landing" };
	if (path === "/portal" || path === "/portal.html") return { key: "portal", label: "Account manager" };
	if (path === "/features" || path === "/features.html") return { key: "features", label: "Features" };
	if (path === "/legends" || path === "/legends.html") return { key: "legends", label: "Legends" };
	if (path === "/npcs" || path === "/npcs.html" || path === "/drops" || path === "/drops.html") return { key: "npcs", label: "NPC drops" };
	if (path === "/transparency" || path === "/transparency.html") return { key: "transparency", label: "Transparency" };
	if (path === "/privacy" || path === "/privacy.html") return { key: "privacy", label: "Privacy" };
	if (path === "/data-deletion" || path === "/data-deletion.html") return { key: "data_deletion", label: "Data deletion" };
	if (path === "/discord" || path === "/discord.html") return { key: "discord", label: "Discord" };
	return { key: "other", label: "Other" };
}

function requestPublicOrigin(request) {
	if (publicMode && emailVerificationRequired && !publicSiteOrigin) {
		throw new HttpError(503, "public_origin_not_configured");
	}
	return publicSiteOrigin || normalizedOrigin(publicOrigin(request)) || "https://voidscape.gg";
}

function normalizedOrigin(value) {
	const text = String(value || "").trim();
	if (!text) return "";
	try {
		const parsed = new URL(text);
		return parsed.origin;
	} catch (error) {
		return text.replace(/\/+$/g, "");
	}
}

function joinUrl(origin, path) {
	const base = normalizedOrigin(origin) || "https://voidscape.gg";
	return new URL(path, `${base}/`).toString();
}

function mobileWebClientUrl(value) {
	const text = String(value || "").trim() || "https://voidscape.gg/play/";
	try {
		const parsed = new URL(text, `${publicSiteOrigin || "https://voidscape.gg"}/`);
		const playPath = parsed.pathname.replace(/\/+$/g, "");
		const isPlayClient = playPath === "/play" || playPath.endsWith("/play");
		const hasExplicitMode = parsed.searchParams.has("phone")
			|| parsed.searchParams.has("mobile")
			|| parsed.searchParams.has("mode")
			|| parsed.searchParams.has("tablet")
			|| parsed.searchParams.has("desktop");
		if (isPlayClient && !hasExplicitMode) {
			parsed.searchParams.set("phone", "1");
		}
		return /^[a-z][a-z0-9+.-]*:/i.test(text)
			? parsed.toString()
			: `${parsed.pathname}${parsed.search}${parsed.hash}`;
	} catch (error) {
		return text;
	}
}

function publicDownloadUrl(path) {
	return publicSiteOrigin ? joinUrl(publicSiteOrigin, path) : path;
}

async function queuePendingEmailVerificationSignup(store, payload, passwordHash, password, request) {
	pruneEmailVerifications(store);
	const emailCanonical = canonicalEmail(payload.email || "");
	const emailDisplay = String(payload.email || "").trim();
	if (!emailCanonical) throw new HttpError(400, "invalid_email");
	if (store.accounts.some((account) => account.emailCanonical === emailCanonical)) {
		throw new HttpError(409, "account_exists");
	}
	const existingPending = store.emailVerifications.find((entry) =>
		entry.emailCanonical === emailCanonical &&
		entry.status === "pending" &&
		Number(entry.expiresAtMs || 0) > Date.now()
	);
	if (existingPending) {
		return { pending: existingPending, created: false };
	}

	const username = cleanUsername(payload.username || payload.name || "");
	const normalizedName = normalizeUsername(username);
	if (!normalizedName) throw new HttpError(400, "invalid_username");
	await assertFounderUsernameAvailable(store, normalizedName, emailCanonical);

	const token = randomBytes(32).toString("base64url");
	const pending = {
		id: nextId(store, "emailVerification"),
		emailCanonical,
		createdAt: now()
	};
	store.emailVerifications.push(pending);
	const expiresAtMs = Date.now() + emailVerificationTtlMs;
	Object.assign(pending, {
		emailDisplay,
		username,
		normalizedName,
		passwordHash,
		gamePasswordSealed: launchSignupMode ? sealText(password) : "",
		referrerCode: String(payload.referrerCode || payload.referralCode || payload.ref || "").trim().slice(0, 40),
		status: "pending",
		tokenHash: hashToken(token),
		expiresAtMs,
		expiresAt: new Date(expiresAtMs).toISOString(),
		requestIpHash: abuseHash(clientIp(request)),
		verificationTokenSealed: sealText(token),
		updatedAt: now()
	});
	return { pending, created: true };
}

function queueEmailVerification(store, pending, metadata = {}, options = {}) {
	if (!pending || !deliverableEmailAddress(pending.emailCanonical)) return null;
	if (!store.emailEvents) store.emailEvents = [];
	const duplicate = !options.force && store.emailEvents.find((event) =>
		event.pendingSignupId === pending.id &&
		event.type === emailVerificationEmailType &&
		["pending", "sending", "sent"].includes(event.status)
	);
	if (duplicate) {
		return { event: duplicate, created: false };
	}
	const event = {
		id: nextId(store, "emailEvent"),
		accountId: null,
		pendingSignupId: pending.id,
		emailCanonical: pending.emailCanonical,
		emailDisplay: pending.emailDisplay || pending.emailCanonical,
		type: emailVerificationEmailType,
		status: "pending",
		provider: emailProviderLabel(),
		attempts: 0,
		providerMessageId: "",
		lastError: "",
		metadata: {
			origin: normalizedOrigin(metadata.origin || publicSiteOrigin),
			source: String(metadata.source || "password_signup").trim().slice(0, 80),
			requestedBy: String(metadata.requestedBy || "").trim().slice(0, 80),
			verificationTokenSealed: pending.verificationTokenSealed || ""
		},
		createdAt: now(),
		updatedAt: now(),
		sentAt: null
	};
	store.emailEvents.push(event);
	audit(store, "email_event_queued", {
		emailEventId: event.id,
		pendingSignupId: pending.id,
		type: event.type
	});
	return { event, created: true };
}

async function verifyEmailSignupToken(token, request) {
	const normalizedToken = String(token || "").trim();
	if (!/^[A-Za-z0-9_-]{32,120}$/.test(normalizedToken)) {
		throw new HttpError(400, "invalid_email_verification_token");
	}
	const result = await updateStore(async (store, transaction) => {
		pruneEmailVerifications(store);
		const tokenHash = hashToken(normalizedToken);
		const pending = store.emailVerifications.find((entry) =>
			entry.status === "pending" &&
			entry.tokenHash === tokenHash
		);
		if (!pending) throw new HttpError(400, "invalid_email_verification_token");
		if (Number(pending.expiresAtMs || 0) <= Date.now()) {
			pending.status = "expired";
			pending.updatedAt = now();
			throw new HttpError(410, "email_verification_expired");
		}
		if (store.accounts.some((account) => account.emailCanonical === pending.emailCanonical)) {
			pending.status = "used";
			pending.updatedAt = now();
			throw new HttpError(409, "account_exists");
		}

		const founder = await reserveFounder(store, {
			username: pending.username,
			email: pending.emailDisplay || pending.emailCanonical,
			referrerCode: pending.referrerCode || ""
		});
		const account = {
			id: nextId(store, "account"),
			emailCanonical: pending.emailCanonical,
			emailDisplay: pending.emailDisplay || pending.emailCanonical,
			passwordHash: pending.passwordHash,
			status: "active",
			emailVerifiedAt: now(),
			subscriptionExpiresAt: 0,
			createdAt: now(),
			updatedAt: now()
		};
		store.accounts.push(account);
		recordSignupSignals(store, account, request, {
			emailCanonical: pending.emailCanonical,
			provider: "password_email_verified"
		});
		if (launchSignupMode) {
			await createLaunchFirstCharacter(store, account, {
				username: founder.username,
				gamePassword: unsealText(pending.gamePasswordSealed || "")
			}, request, transaction);
		} else {
			const reservedCharacter = createCharacter(store, account.id, founder.username, "warrior");
			reservedCharacter.source = "founder-reserved";
			reservedCharacter.status = "Reserved username";
		}
		await grantStarterCardIfEligible(store, account, founder, "email_verified_signup", request, {
			emailCanonical: pending.emailCanonical,
			provider: "password_email_verified",
			signupSignalsRecorded: true
		}, transaction);
		const confirmation = queueAccountEmail(store, account, signupConfirmationEmailType, {
			origin: requestPublicOrigin(request)
		});
		const sessionToken = createSession(store, account.id);
		pending.status = "verified";
		pending.accountId = account.id;
		pending.verifiedAt = now();
		pending.updatedAt = now();
		clearEmailVerificationSecrets(store, pending, "email_verification_completed");
		audit(store, "account_email_verified", { accountId: account.id, pendingSignupId: pending.id });
		audit(store, "account_registered", { accountId: account.id, emailVerified: true });
		return {
			token: sessionToken,
			account,
			confirmationEmailEventId: confirmation && confirmation.event.id,
			state: {
				token: sessionToken,
				...(await accountState(store, account))
			}
		};
	});
	scheduleEmailDelivery(result.confirmationEmailEventId);
	return result;
}

function pruneEmailVerifications(store) {
	if (!Array.isArray(store.emailVerifications)) store.emailVerifications = [];
	const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
	store.emailVerifications = store.emailVerifications.filter((entry) => {
		if (!entry) return false;
		if (entry.status === "pending" && Number(entry.expiresAtMs || 0) <= Date.now()) {
			entry.status = "expired";
			entry.updatedAt = now();
			entry.expiredAt = entry.updatedAt;
			clearEmailVerificationSecrets(store, entry, "email_verification_expired");
			return true;
		}
		return entry.status === "pending" || Number(entry.expiresAtMs || 0) >= cutoff;
	});
}

function clearEmailVerificationSecrets(store, pending, skippedReason) {
	if (!pending) return;
	pending.passwordHash = null;
	pending.gamePasswordSealed = "";
	pending.tokenHash = "";
	pending.verificationTokenSealed = "";
	for (const event of store.emailEvents || []) {
		if (event.pendingSignupId !== pending.id || event.type !== emailVerificationEmailType) continue;
		if (event.metadata) event.metadata.verificationTokenSealed = "";
		if (["pending", "failed"].includes(event.status)) {
			event.status = "skipped";
			event.lastError = skippedReason;
			event.updatedAt = now();
		}
	}
}

function emailVerificationRequestDecision(store, emailCanonical, request) {
	const profile = clientAbuseProfile(request);
	if (profile.local) return { allowed: true, rule: "" };
	const since = Date.now() - emailVerificationRequestWindowMs;
	if (countAbuseSignals(store, "email_verification_ip", profile.ip, since) >= emailVerificationRequestIpLimit) {
		return { allowed: false, rule: "email_verification_ip_window" };
	}
	if (emailCanonical && countAbuseSignals(store, "email_verification_email", emailCanonical, since) >= emailVerificationRequestEmailLimit) {
		return { allowed: false, rule: "email_verification_email_window" };
	}
	return { allowed: true, rule: "" };
}

function assertEmailVerificationRequestAllowed(store, emailCanonical, request) {
	const decision = emailVerificationRequestDecision(store, emailCanonical, request);
	if (!decision.allowed) rejectRateLimit(request, decision.rule);
}

function recordEmailVerificationRequestSignals(store, emailCanonical, request, pendingSignupId = null) {
	const ip = clientIp(request);
	const subnetKey = ipv4SubnetKey(ip);
	recordAbuseSignal(store, {
		signalType: "email_verification_ip",
		signalValue: ip,
		bucket: dailyBucket(),
		metadata: { local: isLocalIp(ip), pendingSignupId }
	});
	recordAbuseSignal(store, {
		signalType: "email_verification_email",
		signalValue: emailCanonical,
		bucket: emailDomainBucket(emailCanonical),
		metadata: { pendingSignupId }
	});
	if (subnetKey) {
		recordAbuseSignal(store, {
			signalType: "email_verification_subnet",
			signalValue: subnetKey,
			bucket: dailyBucket(),
			metadata: { local: isLocalIp(ip), pendingSignupId }
		});
	}
}

function deliverableEmailAddress(email) {
	const canonical = canonicalEmail(email);
	return Boolean(
		canonical &&
		!canonical.endsWith(".invalid") &&
		!canonical.endsWith(".local") &&
		!canonical.endsWith("@discord.voidscape.local")
	);
}

function adminEmailLimit(value) {
	const limit = Number(value || 500);
	if (!Number.isInteger(limit) || limit <= 0) return 500;
	return Math.min(limit, 1000);
}

function deliverableAccountEmail(account) {
	const email = account && (account.emailDisplay || account.emailCanonical || "");
	const canonical = account && account.emailCanonical || canonicalEmail(email);
	if (!email || !canonical) return false;
	return deliverableEmailAddress(canonical);
}

function resolvePasswordResetAccount(store, identifier) {
	const input = String(identifier || "").trim();
	const emailCanonical = input.includes("@") ? canonicalEmail(input) : "";
	if (emailCanonical) {
		return {
			identifierType: "email",
			signalValue: emailCanonical,
			account: store.accounts.find((entry) => entry.emailCanonical === emailCanonical) || null
		};
	}
	const normalizedName = normalizeUsername(input);
	if (normalizedName) {
		const character = store.characters.find((entry) =>
			(entry.normalizedName || normalizeUsername(entry.name || "")) === normalizedName
		);
		const founder = character ? null : store.founders.find((entry) => entry.normalizedName === normalizedName);
		const account = character
			? store.accounts.find((entry) => Number(entry.id) === Number(character.accountId)) || null
			: founder
				? store.accounts.find((entry) => entry.emailCanonical === founder.emailCanonical) || null
				: null;
		return {
			identifierType: "username",
			signalValue: normalizedName,
			account
		};
	}
	return {
		identifierType: "unknown",
		signalValue: input.toLowerCase().slice(0, 120),
		account: null
	};
}

function maskEmailAddress(value) {
	const email = canonicalEmail(value);
	if (!email) return "";
	const [local, domain] = email.split("@");
	const parts = domain.split(".");
	const host = parts.shift() || "";
	const suffix = parts.length ? `.${parts.join(".")}` : "";
	return `${local.slice(0, 1)}***@${host.slice(0, 1)}***${suffix}`;
}

function queuePasswordResetEmail(store, account, metadata = {}) {
	if (!account || !deliverableAccountEmail(account)) return null;
	const createdAt = now();
	revokePasswordResetTokens(store, account.id, createdAt);
	store.emailEvents
		.filter((event) => event.accountId === account.id && event.type === passwordResetEmailType && ["pending", "failed"].includes(event.status))
		.forEach((event) => {
			event.status = "skipped";
			event.lastError = "password_reset_superseded";
			event.updatedAt = createdAt;
		});

	const token = randomBytes(32).toString("base64url");
	const expiresAtMs = Date.now() + passwordResetTtlMs;
	const reset = {
		id: nextId(store, "passwordResetToken"),
		accountId: account.id,
		tokenHash: hashToken(token),
		status: "pending",
		requestIpHash: abuseHash(metadata.requestIp || ""),
		identifierType: String(metadata.identifierType || "").slice(0, 20),
		expiresAtMs,
		expiresAt: new Date(expiresAtMs).toISOString(),
		createdAt,
		updatedAt: createdAt,
		usedAt: null,
		revokedAt: null
	};
	store.passwordResetTokens.push(reset);
	const event = {
		id: nextId(store, "emailEvent"),
		accountId: account.id,
		passwordResetTokenId: reset.id,
		emailCanonical: account.emailCanonical,
		emailDisplay: account.emailDisplay || account.emailCanonical,
		type: passwordResetEmailType,
		status: "pending",
		provider: emailProviderLabel(),
		attempts: 0,
		providerMessageId: "",
		lastError: "",
		metadata: {
			origin: normalizedOrigin(metadata.origin || publicSiteOrigin),
			source: "account_recovery",
			requestedBy: "self_service",
			resetTokenSealed: sealText(token)
		},
		createdAt,
		updatedAt: createdAt,
		sentAt: null
	};
	store.emailEvents.push(event);
	audit(store, "email_event_queued", {
		emailEventId: event.id,
		accountId: account.id,
		passwordResetTokenId: reset.id,
		type: event.type
	});
	return { event, reset };
}

function requirePasswordResetToken(value) {
	const token = String(value || "").trim();
	if (!/^[A-Za-z0-9_-]{32,120}$/.test(token)) {
		throw new HttpError(401, "invalid_password_reset_token");
	}
	return token;
}

function prunePasswordResetTokens(store) {
	if (!Array.isArray(store.passwordResetTokens)) store.passwordResetTokens = [];
	const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
	store.passwordResetTokens = store.passwordResetTokens.filter((entry) => {
		if (!entry) return false;
		if (entry.status === "pending" && Number(entry.expiresAtMs || 0) <= Date.now()) {
			entry.status = "expired";
			entry.updatedAt = now();
		}
		return entry.status === "pending" || Number(entry.expiresAtMs || 0) >= cutoff;
	});
}

function revokePasswordResetTokens(store, accountId, revokedAt = now(), exceptId = null) {
	for (const entry of store.passwordResetTokens || []) {
		if (entry.accountId === accountId && entry.id !== exceptId && entry.status === "pending") {
			entry.status = "revoked";
			entry.revokedAt = revokedAt;
			entry.updatedAt = revokedAt;
		}
	}
}

async function legacyAccountClaimSubject(store, normalizedName) {
	const character = store.characters.find((entry) =>
		(entry.normalizedName || normalizeUsername(entry.name || "")) === normalizedName
		&& entry.linkStatus === "linked"
		&& entry.source === "openrsc-sqlite-native"
		&& Number(entry.playerId || 0) > 0
	) || null;
	if (!character) return null;
	const account = store.accounts.find((entry) => Number(entry.id) === Number(character.accountId)) || null;
	if (!account
		|| account.status !== "active"
		|| account.source !== "native-client-backfill"
		|| account.syntheticEmail !== true
		|| account.requiresEmailUpgrade !== true
		|| account.passwordHash
		|| store.identities.some((entry) => Number(entry.accountId) === Number(account.id))
		|| deliverableAccountEmail(account)
		|| (Number(account.nativePlayerId || 0) > 0 && Number(account.nativePlayerId) !== Number(character.playerId))) {
		return null;
	}
	const rows = await sqliteJson(`
		SELECT p.id, p.username, p.pass, COALESCE(p.salt, '') AS salt,
		       (
			SELECT pc.value
			FROM player_cache pc
			WHERE pc.playerID = p.id
			  AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
			ORDER BY pc.dbid DESC
			LIMIT 1
		       ) AS webAccountId
		FROM players p
		WHERE p.id = ${Number(character.playerId)}
		  AND lower(p.username) = ${sqlString(normalizedName)}
		LIMIT 1
	`);
	const row = rows[0];
	if (!row
		|| !String(row.pass || "")
		|| String(row.webAccountId || "") !== String(account.id)) {
		return null;
	}
	const player = {
		id: Number(row.id),
		username: String(row.username || ""),
		pass: String(row.pass || ""),
		salt: String(row.salt || ""),
		webAccountId: String(row.webAccountId || "")
	};
	return {
		account,
		character,
		player,
		credentialFingerprint: legacyCredentialFingerprint(player)
	};
}

function legacyCredentialFingerprint(player) {
	return hashToken([
		String(player && player.id || ""),
		String(player && player.pass || ""),
		String(player && player.salt || ""),
		String(player && player.webAccountId || "")
	].join("\u0000"));
}

function queueLegacyAccountClaim(store, subject, details) {
	const createdAt = now();
	revokeLegacyAccountClaims(store, subject.account.id, createdAt);
	const token = randomBytes(32).toString("base64url");
	const expiresAtMs = Date.now() + legacyAccountClaimTtlMs;
	const claim = {
		id: nextId(store, "legacyAccountClaim"),
		accountId: subject.account.id,
		characterId: subject.character.id,
		playerId: subject.player.id,
		normalizedName: subject.character.normalizedName || normalizeUsername(subject.character.name || ""),
		targetEmailCanonical: details.targetEmailCanonical,
		targetEmailDisplay: details.targetEmailDisplay || details.targetEmailCanonical,
		passwordHash: details.passwordHash,
		credentialFingerprint: subject.credentialFingerprint,
		ownershipMarker: String(subject.player.webAccountId || ""),
		tokenHash: hashToken(token),
		status: "pending",
		requestIpHash: abuseHash(details.requestIp || ""),
		expiresAtMs,
		expiresAt: new Date(expiresAtMs).toISOString(),
		createdAt,
		updatedAt: createdAt,
		usedAt: null,
		revokedAt: null
	};
	store.legacyAccountClaims.push(claim);
	const event = {
		id: nextId(store, "emailEvent"),
		accountId: subject.account.id,
		legacyAccountClaimId: claim.id,
		emailCanonical: claim.targetEmailCanonical,
		emailDisplay: claim.targetEmailDisplay,
		type: legacyAccountClaimEmailType,
		status: "pending",
		provider: emailProviderLabel(),
		attempts: 0,
		providerMessageId: "",
		lastError: "",
		metadata: {
			origin: normalizedOrigin(details.origin || publicSiteOrigin),
			source: "legacy_account_claim",
			requestedBy: "self_service",
			claimTokenSealed: sealText(token)
		},
		createdAt,
		updatedAt: createdAt,
		sentAt: null
	};
	store.emailEvents.push(event);
	audit(store, "email_event_queued", {
		emailEventId: event.id,
		accountId: subject.account.id,
		legacyAccountClaimId: claim.id,
		type: event.type
	});
	return { claim, event };
}

function requireLegacyAccountClaimToken(value) {
	const token = String(value || "").trim();
	if (!/^[A-Za-z0-9_-]{32,120}$/.test(token)) {
		throw new HttpError(401, "invalid_legacy_claim_token");
	}
	return token;
}

function pruneLegacyAccountClaims(store) {
	if (!Array.isArray(store.legacyAccountClaims)) store.legacyAccountClaims = [];
	const cutoff = Date.now() - 7 * 24 * 60 * 60 * 1000;
	store.legacyAccountClaims = store.legacyAccountClaims.filter((entry) => {
		if (!entry) return false;
		if (entry.status === "pending" && Number(entry.expiresAtMs || 0) <= Date.now()) {
			revokeLegacyAccountClaim(entry, "expired");
		}
		return entry.status === "pending" || Number(entry.expiresAtMs || 0) >= cutoff;
	});
}

function revokeLegacyAccountClaim(claim, status = "revoked", changedAt = now()) {
	if (!claim || claim.status !== "pending") return false;
	claim.status = status;
	claim.updatedAt = changedAt;
	claim.passwordHash = null;
	claim.credentialFingerprint = "";
	if (status === "expired") claim.expiredAt = changedAt;
	else claim.revokedAt = changedAt;
	return true;
}

function revokeLegacyAccountClaims(store, accountId, changedAt = now(), exceptId = null) {
	const revokedIds = new Set();
	for (const claim of store.legacyAccountClaims || []) {
		if (Number(claim.accountId) === Number(accountId) && claim.id !== exceptId && revokeLegacyAccountClaim(claim, "revoked", changedAt)) {
			revokedIds.add(claim.id);
		}
	}
	if (!revokedIds.size) return;
	for (const event of store.emailEvents || []) {
		if (!revokedIds.has(event.legacyAccountClaimId) || !["pending", "failed"].includes(event.status)) continue;
		event.status = "skipped";
		event.lastError = "legacy_claim_superseded";
		event.updatedAt = changedAt;
	}
}

function revokeRecoveryCodes(store, accountId, revokedAt = now(), exceptId = null) {
	for (const entry of store.recoveryCodes || []) {
		if (entry.accountId === accountId && entry.id !== exceptId && entry.status === "active") {
			entry.status = "revoked";
			entry.revokedAt = revokedAt;
		}
	}
}

function findFounderForAccount(store, account) {
	return account ? store.founders.find((entry) => entry.emailCanonical === account.emailCanonical) || null : null;
}

function findGoogleAccount(store, profile) {
	const identity = store.identities.find((entry) =>
		entry.provider === profile.provider &&
		entry.providerSubject === profile.providerSubject
	);
	if (identity) {
		const account = store.accounts.find((entry) => entry.id === identity.accountId);
		if (account) return account;
	}
	return store.accounts.find((entry) => entry.emailCanonical === profile.emailCanonical) || null;
}

function queueAccountEmail(store, account, type, metadata = {}, options = {}) {
	if (!deliverableAccountEmail(account)) return null;
	if (!store.emailEvents) store.emailEvents = [];
	const duplicate = !options.force && store.emailEvents.find((event) =>
		event.accountId === account.id &&
		event.type === type &&
		["pending", "sending", "sent"].includes(event.status)
	);
	if (duplicate) {
		return { event: duplicate, created: false };
	}
	const event = {
		id: nextId(store, "emailEvent"),
		accountId: account.id,
		emailCanonical: account.emailCanonical,
		emailDisplay: account.emailDisplay || account.emailCanonical,
		type,
		status: "pending",
		provider: emailProviderLabel(),
		attempts: 0,
		providerMessageId: "",
		lastError: "",
		metadata: {
			origin: normalizedOrigin(metadata.origin || publicSiteOrigin),
			source: String(metadata.source || "").trim().slice(0, 80),
			requestedBy: String(metadata.requestedBy || "").trim().slice(0, 80)
		},
		createdAt: now(),
		updatedAt: now(),
		sentAt: null
	};
	store.emailEvents.push(event);
	audit(store, "email_event_queued", {
		emailEventId: event.id,
		accountId: account.id,
		type
	});
	return { event, created: true };
}

async function queueAdminBulkEmail(request, type, payload, options) {
	const dryRun = Boolean(payload && payload.dryRun);
	const force = Boolean(payload && payload.force);
	const limit = adminEmailLimit(payload && payload.limit);
	const origin = requestPublicOrigin(request);
	return await updateStore((store) => {
		const candidates = store.accounts
			.filter((account) => account.status === "active")
			.filter((account) => deliverableAccountEmail(account))
			.filter((account) => !options.requireFounder || findFounderForAccount(store, account));
		const selected = candidates.slice(0, limit);
		if (dryRun) {
			audit(store, options.auditType, {
				type,
				dryRun: true,
				eligible: candidates.length,
				selected: selected.length
			});
			return {
				dryRun: true,
				type,
				eligible: candidates.length,
				selected: selected.length,
				queued: 0,
				existing: 0,
				eventIds: [],
				config: portalConfigHealth().email
			};
		}
		const eventIds = [];
		let queued = 0;
		let existing = 0;
		for (const account of selected) {
			const queuedEmail = queueAccountEmail(store, account, type, {
				origin,
				requestedBy: "admin"
			}, { force });
			if (!queuedEmail) continue;
			if (queuedEmail.created) queued += 1;
			else existing += 1;
			if (["pending", "failed"].includes(queuedEmail.event.status)) {
				eventIds.push(queuedEmail.event.id);
			}
		}
		audit(store, options.auditType, {
			type,
			dryRun: false,
			eligible: candidates.length,
			selected: selected.length,
			queued,
			existing,
			force
		});
		return {
			dryRun: false,
			type,
			eligible: candidates.length,
			selected: selected.length,
			queued,
			existing,
			eventIds,
			config: portalConfigHealth().email
		};
	});
}

function scheduleEmailDelivery(emailEventId) {
	if (!emailEventId) return;
	setTimeout(() => {
		deliverQueuedEmail(emailEventId).catch((error) => {
			console.error("email_delivery_failed", sanitizeEmailError(error));
		});
	}, 0);
}

async function deliverEmailEvents(eventIds) {
	const totals = { sent: 0, failed: 0, skipped: 0 };
	for (const eventId of eventIds || []) {
		const result = await deliverQueuedEmail(eventId);
		totals.sent += result.sent || 0;
		totals.failed += result.failed || 0;
		totals.skipped += result.skipped || 0;
	}
	return totals;
}

async function deliverQueuedEmail(emailEventId) {
	let message = null;
	let skippedReason = "";
	await updateStore((store) => {
		pruneLegacyAccountClaims(store);
		const event = store.emailEvents.find((entry) => entry.id === emailEventId);
		if (!event) {
			skippedReason = "email_event_not_found";
			return null;
		}
		if (!["pending", "failed"].includes(event.status)) {
			skippedReason = `email_event_${event.status}`;
			return null;
		}
		if (!emailConfigured()) {
			event.lastError = "email_not_configured";
			event.updatedAt = now();
			skippedReason = "email_not_configured";
			return null;
		}
		if (event.type === emailVerificationEmailType) {
			const pending = store.emailVerifications.find((entry) => entry.id === event.pendingSignupId);
			if (!pending || pending.status !== "pending" || Number(pending.expiresAtMs || 0) <= Date.now()) {
				event.status = "skipped";
				event.lastError = "pending_signup_not_deliverable";
				event.updatedAt = now();
				skippedReason = "pending_signup_not_deliverable";
				return null;
			}
			message = buildEmailVerificationMessage(event, pending);
		} else if (event.type === legacyAccountClaimEmailType) {
			const claim = store.legacyAccountClaims.find((entry) => entry.id === event.legacyAccountClaimId);
			const account = claim ? store.accounts.find((entry) => Number(entry.id) === Number(claim.accountId)) : null;
			if (!claim
				|| claim.status !== "pending"
				|| Number(claim.expiresAtMs || 0) <= Date.now()
				|| !account
				|| account.status !== "active"
				|| account.syntheticEmail !== true
				|| account.requiresEmailUpgrade !== true
				|| claim.targetEmailCanonical !== event.emailCanonical
				|| !deliverableEmailAddress(event.emailCanonical)) {
				event.status = "skipped";
				event.lastError = "legacy_claim_not_deliverable";
				event.updatedAt = now();
				skippedReason = "legacy_claim_not_deliverable";
				return null;
			}
			message = buildLegacyAccountClaimMessage(event, account, claim);
		} else {
			const account = store.accounts.find((entry) => entry.id === event.accountId);
			if (!account || account.status !== "active" || !deliverableAccountEmail(account)) {
				event.status = "skipped";
				event.lastError = "account_not_deliverable";
				event.updatedAt = now();
				skippedReason = "account_not_deliverable";
				return null;
			}
			if (event.type === passwordResetEmailType) {
				const reset = store.passwordResetTokens.find((entry) => entry.id === event.passwordResetTokenId);
				if (!reset || reset.status !== "pending" || Number(reset.expiresAtMs || 0) <= Date.now()) {
					event.status = "skipped";
					event.lastError = "password_reset_not_deliverable";
					event.updatedAt = now();
					skippedReason = "password_reset_not_deliverable";
					return null;
				}
				message = buildPasswordResetMessage(event, account, reset);
			} else {
				const founder = findFounderForAccount(store, account);
				message = buildEmailMessage(event, account, founder);
			}
		}
		event.status = "sending";
		event.provider = emailProviderLabel();
		event.attempts = Number(event.attempts || 0) + 1;
		event.lastError = "";
		event.updatedAt = now();
		return null;
	});
	if (!message) return { skipped: 1, reason: skippedReason || "email_not_ready" };

	try {
		const providerResult = await sendEmailMessage(message);
		await updateStore((store) => {
			const event = store.emailEvents.find((entry) => entry.id === emailEventId);
			if (!event) return null;
			event.status = "sent";
			event.provider = providerResult.provider;
			event.providerMessageId = providerResult.id || "";
			event.lastError = "";
			event.updatedAt = now();
			event.sentAt = now();
			if (event.type === emailVerificationEmailType && event.metadata) {
				event.metadata.verificationTokenSealed = "";
			}
			audit(store, "email_event_sent", {
				emailEventId: event.id,
				accountId: event.accountId,
				type: event.type,
				provider: event.provider
			});
			return null;
		});
		return { sent: 1 };
	} catch (error) {
		const lastError = sanitizeEmailError(error);
		await updateStore((store) => {
			const event = store.emailEvents.find((entry) => entry.id === emailEventId);
			if (!event) return null;
			event.status = "failed";
			event.lastError = lastError;
			event.updatedAt = now();
			audit(store, "email_event_failed", {
				emailEventId: event.id,
				accountId: event.accountId,
				type: event.type,
				error: lastError
			});
			return null;
		});
		return { failed: 1 };
	}
}

function buildEmailMessage(event, account, founder) {
	const origin = normalizedOrigin(event.metadata && event.metadata.origin || publicSiteOrigin) || "https://voidscape.gg";
	const manageUrl = joinUrl(origin, "/");
	const playUrl = mobileWebClientUrl(joinUrl(origin, "/play/"));
	const launcherUrl = joinUrl(origin, "/downloads/launcher");
	const androidApkUrl = joinUrl(origin, "/downloads/android-apk");
	const androidPrimaryUrl = androidPlayUrl || androidApkUrl;
	const androidPrimaryLabel = androidPlayUrl ? "Google Play" : "Android APK";
	const androidEmailLinks = [
		{ url: androidPrimaryUrl, label: androidPrimaryLabel },
		...(androidPlayUrl ? [{ url: androidApkUrl, label: "Signed APK fallback" }] : [])
	];
	const username = founder && founder.username || account.displayName || account.emailDisplay || "your character";
	const reservedName = founder && founder.username ? founder.username : "your Voidscape character";
	if (event.type === launchReminderEmailType) {
		return emailMessage({
			event,
			account,
			subject: "Voidscape opens in 48 hours",
			heading: "Voidscape opens in 48 hours",
			intro: "The world goes live Saturday, July 18, 2026 at 11:00 AM Pacific / 2:00 PM Eastern / 7:00 PM UK. If you reserved a name during prelaunch, your account and first character are waiting in the portal.",
			bullets: [
				`Reserved username: ${reservedName}`,
				"Desktop players should use the Voidscape launcher.",
				androidPlayUrl
					? "Android players should install from Google Play; the signed APK is available as a direct fallback."
					: "Players can use the web client in a supported browser; Android players can also use the Android release.",
				"Eligible prelaunch accounts have a free 1-week subscription card reserved.",
				"Never share your password or signup code. Voidscape staff will never ask for it."
			],
			primaryUrl: manageUrl,
			primaryLabel: "Check account",
			secondaryUrl: launcherUrl,
			secondaryLabel: "Download launcher",
			extraLinks: [
				{ url: playUrl, label: "Web client" },
				...androidEmailLinks
			]
		});
	}
	if (event.type === launchLiveEmailType) {
		return emailMessage({
			event,
			account,
			subject: "Voidscape is live",
			heading: "Voidscape is live",
			intro: `Your reserved name ${reservedName} is ready. The desktop launcher and web client are live from the Voidscape site.`,
			bullets: [
				`Reserved username: ${reservedName}`,
				"Desktop players should use the launcher from the website.",
				"Players can use the web client from the play page in a supported browser.",
				androidPlayUrl
					? "Android players should install from Google Play; the signed APK remains available as a direct fallback."
					: "Android players can install the Android APK from the website.",
				"Your starter subscription card is waiting for eligible prelaunch accounts."
			],
			primaryUrl: manageUrl,
			primaryLabel: "Open Voidscape",
			secondaryUrl: playUrl,
			secondaryLabel: "Web client",
			extraLinks: androidEmailLinks
		});
	}
	return emailMessage({
		event,
		account,
		subject: "Your Voidscape name is reserved",
		heading: "Your Voidscape prelaunch account is ready",
		intro: `Your account is set up and ${reservedName} is reserved for launch.`,
		bullets: [
			`Reserved username: ${reservedName}`,
			"Your free 1-week subscription card is reserved on your account.",
			"When the world opens, talk to the Void Subscription Vendor in Lumbridge to claim it.",
			"We never include your password in email."
		],
		primaryUrl: manageUrl,
		primaryLabel: "Manage account",
		secondaryUrl: discordInviteUrl,
		secondaryLabel: "Join Discord"
	});
}

function buildEmailVerificationMessage(event, pending) {
	const origin = normalizedOrigin(event.metadata && event.metadata.origin || publicSiteOrigin) || "https://voidscape.gg";
	const token = unsealText(event.metadata && event.metadata.verificationTokenSealed || "");
	const verifyUrl = `${joinUrl(origin, "/portal?auth=verify")}#verify=${encodeURIComponent(token)}`;
	const username = pending.username || "your character";
	return emailMessage({
		event,
		account: {
			emailCanonical: pending.emailCanonical,
			emailDisplay: pending.emailDisplay || pending.emailCanonical
		},
		subject: "Verify your Voidscape email",
		heading: "Verify your Voidscape email",
		intro: `Confirm this email address to create your Voidscape account and first character, ${username}.`,
		bullets: [
			`Requested username: ${username}`,
			"Your account, first character, and starter subscription card are not created until you verify.",
			"On the verification page, press Verify email to finish creating your account.",
			`This link expires in ${emailVerificationTtlHours} hours.`,
			"If you did not request this, ignore this email."
		],
		primaryUrl: verifyUrl,
		primaryLabel: "Open verification page",
		secondaryUrl: joinUrl(origin, "/"),
		secondaryLabel: "Open Voidscape"
	});
}

function buildPasswordResetMessage(event, account, reset) {
	const origin = normalizedOrigin(event.metadata && event.metadata.origin || publicSiteOrigin) || "https://voidscape.gg";
	const token = unsealText(event.metadata && event.metadata.resetTokenSealed || "");
	const resetUrl = `${joinUrl(origin, "/portal?auth=reset")}#reset=${encodeURIComponent(token)}`;
	const minutes = Math.max(1, Math.ceil((Number(reset.expiresAtMs || 0) - Date.now()) / (60 * 1000)));
	return emailMessage({
		event,
		account,
		subject: "Reset your Voidscape password",
		heading: "Reset your Voidscape password",
		intro: "A password reset was requested for your Voidscape account.",
		bullets: [
			`This link expires in ${minutes} minutes and can be used once.`,
			"Resetting your website password signs out every portal session.",
			"Character game passwords are changed separately inside Character Manager.",
			"If you did not request this, ignore this email and your password will stay unchanged."
		],
		primaryUrl: resetUrl,
		primaryLabel: "Reset password",
		secondaryUrl: joinUrl(origin, "/"),
		secondaryLabel: "Open Voidscape",
		footer: "This message was sent because someone requested account recovery for this email address."
	});
}

function buildLegacyAccountClaimMessage(event, account, claim) {
	const origin = normalizedOrigin(event.metadata && event.metadata.origin || publicSiteOrigin) || "https://voidscape.gg";
	const token = unsealText(event.metadata && event.metadata.claimTokenSealed || "");
	const claimUrl = `${joinUrl(origin, "/portal?auth=claim-confirm")}#claim=${encodeURIComponent(token)}`;
	const minutes = Math.max(1, Math.ceil((Number(claim.expiresAtMs || 0) - Date.now()) / (60 * 1000)));
	const character = account.displayName || claim.normalizedName || "your older character";
	return emailMessage({
		event,
		account: {
			emailCanonical: claim.targetEmailCanonical,
			emailDisplay: claim.targetEmailDisplay || claim.targetEmailCanonical
		},
		subject: "Confirm your older Voidscape account",
		heading: "Confirm your Voidscape account",
		intro: `Confirm this email address to finish claiming ${character}.`,
		bullets: [
			`This link expires in ${minutes} minutes and can be used once.`,
			"Your existing character and starter subscription card stay on the same account.",
			"Your character game password will not be changed.",
			"After confirmation, sign in with this email and the new website password you chose.",
			"If you did not request this, ignore this email and nothing will change."
		],
		primaryUrl: claimUrl,
		primaryLabel: "Confirm account",
		secondaryUrl: joinUrl(origin, "/portal?auth=login"),
		secondaryLabel: "Open sign in",
		footer: "This message was sent because someone proved control of an older Voidscape character and requested an account claim."
	});
}

function emailMessage(details) {
	const extraLinkLines = (details.extraLinks || [])
		.map((link) => `${link.label}: ${link.url}`);
	const text = [
		"Hi,",
		"",
		details.intro,
		"",
		...details.bullets.map((line) => `- ${line}`),
		"",
		`${details.primaryLabel}: ${details.primaryUrl}`,
		details.secondaryUrl ? `${details.secondaryLabel}: ${details.secondaryUrl}` : "",
		...extraLinkLines,
		"",
		details.footer || "You are receiving this because this email was used for a Voidscape prelaunch signup.",
		"",
		"- Voidscape"
	].filter((line) => line !== "").join("\n");
	const htmlBullets = details.bullets
		.map((line) => `<li>${escapeHtml(line)}</li>`)
		.join("");
	const secondaryLink = details.secondaryUrl
		? `<p><a href="${escapeHtml(details.secondaryUrl)}">${escapeHtml(details.secondaryLabel)}</a></p>`
		: "";
	const extraLinksHtml = (details.extraLinks || [])
		.map((link) => `<p><a href="${escapeHtml(link.url)}">${escapeHtml(link.label)}</a></p>`)
		.join("");
	return {
		eventId: details.event.id,
		type: details.event.type,
		to: details.account.emailDisplay || details.account.emailCanonical,
		subject: details.subject,
		text,
		html: `<!doctype html>
<html>
<body style="margin:0;padding:0;background:#111319;color:#f2efe4;font-family:Arial,Helvetica,sans-serif;">
	<div style="max-width:560px;margin:0 auto;padding:28px 22px;">
		<h1 style="margin:0 0 14px;color:#f4d37d;font-size:24px;">${escapeHtml(details.heading)}</h1>
		<p style="font-size:16px;line-height:1.5;">${escapeHtml(details.intro)}</p>
		<ul style="font-size:15px;line-height:1.6;padding-left:22px;">${htmlBullets}</ul>
		<p style="margin:24px 0;">
			<a href="${escapeHtml(details.primaryUrl)}" style="background:#6f48d8;color:#ffffff;text-decoration:none;padding:12px 18px;border-radius:4px;display:inline-block;font-weight:bold;">${escapeHtml(details.primaryLabel)}</a>
		</p>
		${secondaryLink}
		${extraLinksHtml}
		<p style="font-size:13px;color:#b8b0c8;">${escapeHtml(details.footer || "You are receiving this because this email was used for a Voidscape prelaunch signup.")}</p>
	</div>
</body>
</html>`
	};
}

async function sendEmailMessage(message) {
	if (!emailConfigured()) throw new Error("email_not_configured");
	if (emailDryRun) {
		return { provider: "dry-run", id: `dry-run-${message.eventId}` };
	}
	if (emailProvider !== "resend") {
		throw new Error("email_provider_not_configured");
	}
	const body = {
		from: emailFrom,
		to: [message.to],
		subject: message.subject,
		html: message.html,
		text: message.text
	};
	if (emailReplyTo) {
		body.reply_to = emailReplyTo;
	}
	const resendResponse = await fetch("https://api.resend.com/emails", {
		method: "POST",
		headers: {
			authorization: `Bearer ${resendApiKey}`,
			"content-type": "application/json"
		},
		body: JSON.stringify(body)
	});
	const responseText = await resendResponse.text();
	let responseBody = {};
	try {
		responseBody = responseText ? JSON.parse(responseText) : {};
	} catch (error) {
		responseBody = { message: responseText };
	}
	if (!resendResponse.ok) {
		throw new Error(`resend_${resendResponse.status}:${String(responseBody.message || responseBody.error || responseText || "send_failed").slice(0, 180)}`);
	}
	return { provider: "resend", id: String(responseBody.id || "") };
}

function emailEventSummary(store) {
	const summary = {};
	for (const event of store.emailEvents || []) {
		const key = `${event.type}:${event.status}`;
		summary[key] = (summary[key] || 0) + 1;
	}
	return summary;
}

function emailEventState(store, event) {
	const account = store.accounts.find((entry) => entry.id === event.accountId);
	const founder = account ? findFounderForAccount(store, account) : null;
	return {
		id: event.id,
		accountId: event.accountId,
		email: event.emailDisplay || event.emailCanonical,
		username: founder && founder.username || "",
		type: event.type,
		status: event.status,
		provider: event.provider || "",
		attempts: event.attempts || 0,
		lastError: event.lastError || "",
		createdAt: event.createdAt,
		updatedAt: event.updatedAt,
		sentAt: event.sentAt || null
	};
}

function sanitizeEmailError(error) {
	const raw = String(error && error.message || error || "email_error");
	const redacted = resendApiKey ? raw.replaceAll(resendApiKey, "[redacted]") : raw;
	return redacted.slice(0, 240);
}

async function startDiscordOAuth(request, response, url) {
	requireDiscordOAuthConfig();
	requireDiscordBetaGuildConfig();
	const state = randomBytes(24).toString("base64url");
	const returnTo = safeReturnTo(url.searchParams.get("returnTo") || "/portal#dashboard");
	const authorizeUrl = new URL(`${discordApiBase}/oauth2/authorize`);
	authorizeUrl.searchParams.set("client_id", discordClientId);
	authorizeUrl.searchParams.set("redirect_uri", discordCallbackUri(request));
	authorizeUrl.searchParams.set("response_type", "code");
	authorizeUrl.searchParams.set("scope", discordOauthScopes.join(" "));
	authorizeUrl.searchParams.set("state", state);
	const browserAuthorizeUrl = authorizeUrl.toString();
	const appAuthorizeUrl = discordAppAuthorizeUrl(authorizeUrl);

	await updateStore((store) => {
		pruneOauthStates(store);
		store.oauthStates.push({
			provider: "discord",
			stateHash: hashToken(state),
			returnTo,
			ipHash: abuseHash(clientIp(request)),
			createdAt: now(),
			expiresAt: Date.now() + oauthStateTtlMs
		});
		audit(store, "discord_oauth_started", { returnTo });
	});

	if (url.searchParams.get("response") === "json") {
		json(response, 200, {
			authorizeUrl: browserAuthorizeUrl,
			appAuthorizeUrl,
			fallbackUrl: `/api/oauth/discord/start?returnTo=${encodeURIComponent(returnTo)}`
		});
		return;
	}

	response.writeHead(302, {
		location: browserAuthorizeUrl,
		"cache-control": "no-store",
		...securityHeaders()
	});
	response.end();
}

function discordAppAuthorizeUrl(authorizeUrl) {
	const query = authorizeUrl.searchParams.toString();
	return `discord://-/oauth2/authorize${query ? `?${query}` : ""}`;
}

async function completeDiscordOAuth(request, response, url) {
	try {
		requireDiscordOAuthConfig();
		requireDiscordBetaGuildConfig();
		const error = String(url.searchParams.get("error") || "");
		if (error) {
			throw new HttpError(400, `discord_${error}`);
		}
		const code = String(url.searchParams.get("code") || "").trim();
		const state = String(url.searchParams.get("state") || "").trim();
		if (!code || !state) throw new HttpError(400, "discord_oauth_missing_code");

		const oauthState = await updateStore((store) => consumeOauthState(store, "discord", state, request));
		const token = await exchangeDiscordCode(request, code);
		const profile = await fetchDiscordProfile(token.access_token);
		const membership = await ensureDiscordBetaMembership(profile.id, token.access_token);
		const result = await updateStore(async (store) => {
			const account = await upsertDiscordBetaAccount(store, profile, membership, request);
			const founder = store.founders.find((entry) => entry.emailCanonical === account.emailCanonical);
			if (founder) {
				ensureFounderSignupCode(store, founder);
				try {
					if (await syncSignupCodeToOpenRsc(founder) && !founder.signupCodeSyncedAt) {
						founder.signupCodeSyncedAt = now();
					}
				} catch (syncError) {
					audit(store, "discord_beta_code_sync_failed", {
						accountId: account.id,
						error: String(syncError && syncError.message || syncError)
					});
				}
			}
			const sessionToken = createSession(store, account.id);
			audit(store, "discord_beta_login", {
				accountId: account.id,
				discordUserId: profile.id,
				guildJoined: membership.guildJoined,
				roleIds: membership.roleIds
			});
			return {
				token: sessionToken,
				state: await accountState(store, account)
			};
		});
		sendDiscordOAuthPage(response, {
			token: result.token,
			returnTo: oauthState.returnTo,
			state: result.state
		});
	} catch (error) {
		const code = error instanceof HttpError ? error.message : "discord_oauth_failed";
		sendDiscordOAuthPage(response, {
			error: code,
			returnTo: "/portal#dashboard"
		});
		if (!(error instanceof HttpError)) {
			console.error(error);
		}
	}
}

function requireDiscordOAuthConfig() {
	if (!discordClientId || !discordClientSecret) {
		throw new HttpError(503, "discord_oauth_not_configured");
	}
}

function requireDiscordBetaGuildConfig() {
	if (!discordGuildId || !discordBotToken || !discordBetaRoleId) {
		throw new HttpError(503, "discord_beta_guild_not_configured");
	}
}

function discordCallbackUri(request) {
	return discordRedirectUri || `${publicOrigin(request)}/api/oauth/discord/callback`;
}

function consumeOauthState(store, provider, state, request) {
	pruneOauthStates(store);
	const stateHash = hashToken(state);
	const index = store.oauthStates.findIndex((entry) =>
		entry.provider === provider &&
		entry.stateHash === stateHash &&
		entry.expiresAt > Date.now()
	);
	if (index === -1) throw new HttpError(400, "discord_oauth_state_invalid");
	const [entry] = store.oauthStates.splice(index, 1);
	if (entry.ipHash && entry.ipHash !== abuseHash(clientIp(request))) {
		audit(store, "discord_oauth_ip_changed", { provider });
	}
	return entry;
}

function pruneOauthStates(store) {
	const cutoff = Date.now();
	store.oauthStates = store.oauthStates.filter((entry) => entry.expiresAt > cutoff);
}

async function exchangeDiscordCode(request, code) {
	const body = new URLSearchParams({
		client_id: discordClientId,
		client_secret: discordClientSecret,
		grant_type: "authorization_code",
		code,
		redirect_uri: discordCallbackUri(request)
	});
	const response = await fetch(`${discordApiBase}/oauth2/token`, {
		method: "POST",
		headers: { "content-type": "application/x-www-form-urlencoded" },
		body
	});
	const payload = await response.json().catch(() => ({}));
	if (!response.ok || !payload.access_token) {
		throw new HttpError(502, "discord_token_exchange_failed");
	}
	return payload;
}

async function fetchDiscordProfile(accessToken) {
	const response = await fetch(`${discordApiBase}/users/@me`, {
		headers: { authorization: `Bearer ${accessToken}` }
	});
	const profile = await response.json().catch(() => ({}));
	if (!response.ok || !profile.id) {
		throw new HttpError(502, "discord_profile_failed");
	}
	return profile;
}

async function ensureDiscordBetaMembership(discordUserId, accessToken) {
	const membership = {
		guildJoined: false,
		roleIds: [],
		updatedAt: now()
	};
	const authHeader = { authorization: `Bot ${discordBotToken}` };
	const memberUrl = `${discordApiBase}/guilds/${encodeURIComponent(discordGuildId)}/members/${encodeURIComponent(discordUserId)}`;
	const memberResponse = await fetch(memberUrl, {
		method: "PUT",
		headers: {
			...authHeader,
			"content-type": "application/json"
		},
		body: JSON.stringify({ access_token: accessToken })
	});
	if (![200, 201, 204].includes(memberResponse.status)) {
		throw new HttpError(502, "discord_guild_join_failed");
	}
	membership.guildJoined = true;

	const roleIds = [discordMemberRoleId, discordBetaRoleId].filter(Boolean);
	for (const roleId of roleIds) {
		const roleUrl = `${memberUrl}/roles/${encodeURIComponent(roleId)}`;
		const roleResponse = await fetch(roleUrl, {
			method: "PUT",
			headers: authHeader
		});
		if (![200, 201, 204].includes(roleResponse.status)) {
			throw new HttpError(502, "discord_role_grant_failed");
		}
		membership.roleIds.push(roleId);
	}
	return membership;
}

async function upsertDiscordBetaAccount(store, profile, membership, request) {
	const providerSubject = String(profile.id || "").trim();
	if (!providerSubject) throw new HttpError(400, "invalid_discord_profile");
	const emailCanonical = discordSyntheticEmail(providerSubject);
	const displayName = discordDisplayName(profile);
	const username = uniqueDiscordBetaUsername(store, providerSubject, emailCanonical);
	let identity = store.identities.find((entry) =>
		entry.provider === "discord" &&
		entry.providerSubject === providerSubject
	);
	let account = identity ? store.accounts.find((entry) => entry.id === identity.accountId) : null;
	if (!account) {
		account = store.accounts.find((entry) => entry.emailCanonical === emailCanonical) || null;
	}

	const founder = await reserveFounder(store, {
		username,
		email: emailCanonical
	});
	founder.betaTester = true;
	founder.betaCodeReleaseValid = true;
	founder.betaProgressPolicy = "wipe_progress_preserve_code";
	founder.discordUserId = providerSubject;
	founder.discordDisplayName = displayName;
	founder.discordUsername = String(profile.username || "").trim();
	founder.discordAvatarUrl = discordAvatarUrl(profile);
	founder.updatedAt = now();
	ensureFounderSignupCode(store, founder);

	if (!account) {
		account = {
			id: nextId(store, "account"),
			emailCanonical,
			emailDisplay: emailCanonical,
			displayName,
			passwordHash: null,
			status: "active",
			subscriptionExpiresAt: 0,
			betaTester: true,
			createdAt: now(),
			updatedAt: now()
		};
		store.accounts.push(account);
		recordSignupSignals(store, account, request, {
			emailCanonical,
			provider: "discord",
			providerSubject
		});
		audit(store, "account_discord_beta_registered", {
			accountId: account.id,
			discordUserId: providerSubject
		});
	}

	const accountDiscordIdentity = store.identities.find((entry) =>
		entry.accountId === account.id &&
		entry.provider === "discord"
	);
	if (accountDiscordIdentity && accountDiscordIdentity.providerSubject !== providerSubject) {
		throw new HttpError(409, "discord_identity_conflict");
	}

	if (!identity) {
		identity = {
			id: nextId(store, "identity"),
			accountId: account.id,
			provider: "discord",
			providerSubject,
			emailCanonical,
			emailDisplay: String(profile.email || emailCanonical).trim(),
			displayName,
			avatarUrl: discordAvatarUrl(profile),
			emailVerified: Boolean(profile.verified),
			guildJoined: Boolean(membership.guildJoined),
			roleIds: membership.roleIds.slice(),
			createdAt: now(),
			updatedAt: now(),
			lastLoginAt: now()
		};
		store.identities.push(identity);
		audit(store, "account_discord_identity_linked", { accountId: account.id });
	} else {
		identity.emailCanonical = emailCanonical;
		identity.emailDisplay = String(profile.email || emailCanonical).trim();
		identity.displayName = displayName;
		identity.avatarUrl = discordAvatarUrl(profile);
		identity.emailVerified = Boolean(profile.verified);
		identity.guildJoined = Boolean(membership.guildJoined);
		identity.roleIds = membership.roleIds.slice();
		identity.updatedAt = now();
		identity.lastLoginAt = now();
	}

	account.displayName = displayName;
	account.emailDisplay = account.emailDisplay || emailCanonical;
	account.betaTester = true;
	account.discordUserId = providerSubject;
	account.discordDisplayName = displayName;
	account.updatedAt = now();
	return account;
}

function sendDiscordOAuthPage(response, result) {
	const token = result.token || "";
	const returnTo = safeReturnTo(result.returnTo || "/portal#dashboard");
	const state = result.state || null;
	const error = result.error || "";
	const body = `<!doctype html>
<html lang="en">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<title>Voidscape Discord Login</title>
	<style>
		body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #090d13; color: #f4ead5; font: 16px/1.45 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
		main { max-width: 34rem; padding: 2rem; text-align: center; }
		strong { color: #f4c969; }
	</style>
</head>
<body>
	<main>
		<h1>${error ? "Discord login failed" : "Discord connected"}</h1>
		<p>${error ? `Return to Voidscape and try again. Error: <strong>${escapeHtml(error)}</strong>.` : "Your beta tester pass is ready. Returning to the portal..."}</p>
	</main>
	<script>
		(function () {
			var token = ${jsonScript(token)};
			var state = ${jsonScript(state)};
			var error = ${jsonScript(error)};
			if (token) {
				localStorage.setItem(${JSON.stringify(portalSessionStorageKey)}, token);
			}
			if (state) {
				sessionStorage.setItem("voidscape.portal.lastAccountState", JSON.stringify(state));
			}
			var target = ${jsonScript(returnTo)};
			if (error) {
				target = "/portal?auth=login&discord_error=" + encodeURIComponent(error);
			}
			window.location.replace(target);
		})();
	</script>
</body>
</html>`;
	response.writeHead(error ? 400 : 200, {
		"content-type": "text/html; charset=utf-8",
		"content-length": Buffer.byteLength(body),
		"cache-control": "no-store",
		...securityHeaders("text/html")
	});
	response.end(body);
}

function discordDisplayName(profile) {
	return cleanUsername(profile.global_name || profile.username || "Discord tester").slice(0, 40) || "Discord tester";
}

function discordSyntheticEmail(discordUserId) {
	return `discord-${String(discordUserId).replace(/[^0-9]/g, "")}@discord.voidscape.invalid`;
}

function discordAvatarUrl(profile) {
	if (!profile || !profile.id || !profile.avatar) return "";
	const ext = String(profile.avatar).startsWith("a_") ? "gif" : "png";
	return `https://cdn.discordapp.com/avatars/${encodeURIComponent(profile.id)}/${encodeURIComponent(profile.avatar)}.${ext}`;
}

function uniqueDiscordBetaUsername(store, discordUserId, emailCanonical) {
	const base36 = BigInt(String(discordUserId).replace(/[^0-9]/g, "") || "0").toString(36).toUpperCase();
	const base = cleanUsername(`B${base36.slice(-11)}`).slice(0, 12) || "BetaTester";
	let candidate = base;
	let suffix = 1;
	while (store.founders.some((entry) =>
		entry.normalizedName === normalizeUsername(candidate) &&
		entry.emailCanonical !== emailCanonical
	)) {
		const tail = String(suffix);
		candidate = `${base.slice(0, Math.max(2, 12 - tail.length))}${tail}`;
		suffix += 1;
	}
	return candidate;
}

function safeReturnTo(value) {
	const text = String(value || "").trim();
	if (!text || text.startsWith("//") || /^[a-z][a-z0-9+.-]*:/i.test(text)) return "/portal#dashboard";
	return text.startsWith("/") ? text : "/portal#dashboard";
}

async function serveStatic(request, response, pathname) {
	const targetPath = pathname === "/"
		? "/index.html"
		: pathname === "/portal" || pathname === "/portal/"
			? "/portal.html"
			: pathname === "/privacy"
				? "/privacy.html"
				: pathname === "/data-deletion"
					? "/data-deletion.html"
					: pathname === "/features"
						? "/features.html"
							: pathname === "/legends"
								? "/legends.html"
								: pathname === "/npcs" || pathname === "/drops"
									? "/npcs.html"
									: pathname === "/discord"
										? "/discord.html"
										: pathname === "/transparency"
											? "/transparency.html"
											: decodeURIComponent(pathname);
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
				"cache-control": "no-store",
				...securityHeaders(type)
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
				"cache-control": "no-store",
				...securityHeaders(type)
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
			"cache-control": "no-store",
			...securityHeaders(type)
		});
		createReadStream(filePath, { start, end }).pipe(response);
		return;
	}

	const body = await readFile(filePath);
	response.writeHead(200, {
		"content-type": type,
		"content-length": body.length,
		"accept-ranges": "bytes",
		"cache-control": "no-store",
		...securityHeaders(type)
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
		contentType: artifact.contentType || "application/octet-stream",
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
	if (requestedPath === "Open_RSC_Client.jar") {
		const clientArtifact = downloadArtifacts.find((entry) => entry.slug === "client-runtime");
		await sendFile(request, response, clientArtifact.path, {
			contentType: clientArtifact.contentType || "application/java-archive",
			contentDisposition: `attachment; filename="${clientArtifact.filename}"`,
			notFound: "download_not_built"
		});
		return;
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
		"cache-control": "no-store",
		...securityHeaders("text/plain")
	});
	if (request.method === "HEAD") {
		response.end();
		return;
	}
	response.end(body);
}

async function launcherManifest(request) {
	const origin = publicOrigin(request);
	const clientArtifact = downloadArtifacts.find((entry) => entry.slug === "client-runtime");
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
		size: clientStat.size,
		mtimeMs: clientStat.mtimeMs
	}];
	entries.push(...await clientCacheManifestEntries(origin));

	const clientVersion = await resolveClientVersion();
	const latestMtime = Math.max(...entries.map((entry) => entry.mtimeMs));
	const timestampLabel = compactUtcTimestamp(latestMtime);
	const version = clientVersion === null ? timestampLabel : `${clientVersion}-${timestampLabel}`;
	const lines = [
		propertyLine("version", version),
		propertyLine("baseUrl", `${origin}/downloads/cache/`)
	];
	if (clientVersion !== null) {
		lines.push(propertyLine("clientVersion", clientVersion));
	}
	entries.forEach((entry, index) => {
		const prefix = `file.${index + 1}`;
		lines.push(propertyLine(`${prefix}.path`, entry.path));
		lines.push(propertyLine(`${prefix}.sha256`, entry.sha256));
		lines.push(propertyLine(`${prefix}.url`, entry.url));
		lines.push(propertyLine(`${prefix}.size`, entry.size));
	});
	lines.push(...await launcherSelfUpdateLines(origin));
	return `${lines.join("")}`;
}

async function launcherSelfUpdateLines(origin) {
	const launcherArtifact = downloadArtifacts.find((entry) => entry.slug === "launcher");
	let launcherStat;
	try {
		launcherStat = await stat(launcherArtifact.path);
		if (!launcherStat.isFile()) return [];
	} catch (error) {
		return [];
	}
	return [
		propertyLine("launcher.sha256", await sha256File(launcherArtifact.path, launcherStat)),
		propertyLine("launcher.url", `${origin}/downloads/${launcherArtifact.slug}`),
		propertyLine("launcher.size", launcherStat.size),
		propertyLine("launcher.version", compactUtcTimestamp(launcherStat.mtimeMs))
	];
}

function compactUtcTimestamp(epochMs) {
	return new Date(epochMs).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z");
}

async function resolveClientVersion() {
	if (clientVersionOverride !== null) {
		return clientVersionOverride;
	}
	if (clientVersionCache === undefined) {
		clientVersionCache = await clientVersionFromSource();
	}
	return clientVersionCache;
}

async function clientVersionFromSource() {
	try {
		const source = await readFile(clientVersionSourcePath, "utf8");
		const match = /\bCLIENT_VERSION\s*=\s*(\d+)\s*;/.exec(source);
		return match ? Number.parseInt(match[1], 10) : null;
	} catch (error) {
		return null;
	}
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
			size: fileStat.size,
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
		"cache-control": "no-store",
		...securityHeaders(options.contentType || contentType(filePath))
	});
	if (request.method === "HEAD") {
		response.end();
		return;
	}
	createReadStream(filePath).pipe(response);
}

async function sha256File(filePath, knownStat) {
	const fileStat = knownStat || await stat(filePath);
	const cacheKey = `${resolve(filePath)}:${fileStat.size}:${fileStat.mtimeMs}`;
	if (sha256Cache.has(cacheKey)) {
		return sha256Cache.get(cacheKey);
	}
	const body = await readFile(filePath);
	const digest = createHash("sha256").update(body).digest("hex");
	sha256Cache.set(cacheKey, digest);
	return digest;
}

function publicOrigin(request) {
	if (publicSiteOrigin) return publicSiteOrigin;
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
		"cache-control": "no-store",
		...securityHeaders("image/png")
	});
	createReadStream(filePath).pipe(response);
}

async function reserveFounder(store, payload) {
	const username = cleanUsername(payload.username || payload.name || "");
	const normalizedName = normalizeUsername(username);
	const emailCanonical = canonicalEmail(payload.email || "");
	if (!normalizedName) throw new HttpError(400, "invalid_username");
	if (!emailCanonical) throw new HttpError(400, "invalid_email");

	await assertFounderUsernameAvailable(store, normalizedName, emailCanonical);

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

async function assertFounderUsernameAvailable(store, normalizedName, emailCanonical) {
	const nameOwner = store.founders.find((entry) =>
		entry.normalizedName === normalizedName &&
		entry.emailCanonical !== emailCanonical &&
		entry.status !== "released" &&
		entry.status !== "expired"
	);
	if (nameOwner) throw new HttpError(409, "username_reserved");

	const characterOwner = store.characters.find((character) => character.normalizedName === normalizedName);
	if (characterOwner && !characterBelongsToEmail(store, characterOwner, emailCanonical)) {
		throw new HttpError(409, "username_reserved");
	}

	const openRscOwnerEmail = await openRscPlayerOwnerEmail(normalizedName);
	if (openRscOwnerEmail && openRscOwnerEmail !== emailCanonical && !characterHeldByEmail(store, normalizedName, emailCanonical)) {
		throw new HttpError(409, "username_reserved");
	}
}

function characterHeldByEmail(store, normalizedName, emailCanonical) {
	return store.characters.some((character) =>
		character.normalizedName === normalizedName &&
		characterBelongsToEmail(store, character, emailCanonical)
	);
}

function characterBelongsToEmail(store, character, emailCanonical) {
	const account = store.accounts.find((entry) => entry.id === character.accountId);
	return Boolean(account && account.emailCanonical === emailCanonical);
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
	ensureReferralRewardCode(store, referrer, referral);
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

async function createLaunchFirstCharacter(store, account, payload, request, transaction = null) {
	const existingCharacters = store.characters.filter((character) => character.accountId === account.id);
	const linkedCharacter = existingCharacters.find((character) => character.source === "openrsc-sqlite-created");
	if (linkedCharacter) return linkedCharacter;
	const reservedCharacter = existingCharacters.find((character) => character.source === "founder-reserved");
	const username = cleanUsername(payload.username || payload.name || (reservedCharacter && reservedCharacter.name) || "");
	const gamePassword = launchSignupMode
		? requireLaunchFirstGamePassword(payload.gamePassword || payload.characterPassword || "")
		: requireGamePassword(payload.gamePassword || payload.characterPassword || "");
	return await createAccountCharacter(store, account, {
		name: username,
		gamePassword
	}, request, { initialSignup: true, transaction });
}

async function createAccountCharacter(store, account, payload, request, options = {}) {
	const username = cleanUsername(payload.name || payload.username || "");
	const normalizedName = normalizeUsername(username);
	if (!normalizedName) throw new HttpError(400, "invalid_character_name");
	const reservedByOther = store.founders.find((entry) =>
		entry.normalizedName === normalizedName && entry.emailCanonical !== account.emailCanonical);
	if (reservedByOther) {
		throw new HttpError(409, "username_reserved");
	}
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
		if (launchSignupMode) {
			throw new HttpError(503, "openrsc_db_not_configured");
		}
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
	if (options.initialSignup) {
		assertClientIpNotBlocked(clientAbuseProfile(request));
	} else {
		await assertCharacterCreationAllowed(store, account, request);
	}

	const createdPlayer = await createOpenRscPlayer({
		accountId: account.id,
		username,
		email: account.emailDisplay || account.emailCanonical || "",
		password,
		ip: clientIp(request)
	});
	const playerId = createdPlayer.playerId;
	if (options.transaction) {
		options.transaction.onRollback(() => deleteOpenRscPlayer({
			playerId,
			name: username
		}, account.id, {
			expectedCreationDate: createdPlayer.creationDate
		}));
	}
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
	recordCharacterCreationSignal(store, account, playerId, snapshot.name, request, {
		initialSignup: Boolean(options.initialSignup)
	});
	return character;
}

async function deleteAccountCharacter(store, account, characterId, transaction = null) {
	if (!Number.isInteger(characterId) || characterId <= 0) {
		throw new HttpError(400, "invalid_character_id");
	}
	const index = store.characters.findIndex((character) =>
		Number(character.id) === characterId && Number(character.accountId) === Number(account.id)
	);
	if (index < 0) {
		throw new HttpError(404, "character_not_found");
	}

	const character = store.characters[index];
	const deleted = {
		id: character.id,
		name: character.name,
		playerId: character.playerId || null,
		source: character.source || "portal-preview",
		openRscDeleted: character.source === "openrsc-sqlite-created"
	};

	if (Number(character.playerId) > 0) {
		if (!openRscDbPath) {
			throw new HttpError(503, "openrsc_db_not_configured");
		}
		if (character.source === "openrsc-sqlite-created") {
			const removePlayer = async () => {
				deleted.openRscDeleted = await deleteOpenRscPlayer(character, account.id);
			};
			if (transaction) transaction.afterPersist(removePlayer);
			else await removePlayer();
		} else {
			const unlinkPlayer = () => unlinkOpenRscPlayerFromAccount(character.playerId, account.id);
			if (transaction) transaction.afterPersist(unlinkPlayer);
			else await unlinkPlayer();
		}
	}

	store.characters.splice(index, 1);
	store.linkChallenges.forEach((challenge) => {
		if (challenge.accountId === account.id
			&& normalizeUsername(challenge.username) === normalizeUsername(character.name)
			&& challenge.status === "pending") {
			challenge.status = "revoked";
			challenge.revokedAt = now();
			challenge.updatedAt = now();
		}
	});
	return deleted;
}

async function accountState(store, account, currentSession) {
	await syncAccountSubscriptionFromOpenRsc(account);
	await refreshAccountCharactersFromOpenRsc(store, account);
	const founder = store.founders.find((entry) => entry.emailCanonical === account.emailCanonical) || null;
	const auth = authState(store, account);
	const displayName = account.displayName || (auth.discord && auth.discord.displayName) || (founder ? founder.username : account.emailDisplay);
	const downloads = auth.discordConnected ? await downloadState() : [];
	return {
		account: {
			id: account.id,
			email: account.emailDisplay,
			displayName,
			status: account.status,
			subscription: subscriptionState(account)
		},
		auth,
		founder: founder ? founderState(founder) : null,
		signup: founder ? signupState(founder) : null,
		beta: betaAccountState(founder, auth),
		downloads,
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
		rewards: await rewardState(store, account),
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
				source: ["openrsc-sqlite-created", "openrsc-sqlite-native"].includes(character.source)
					? character.source
					: snapshot.source,
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
			guildJoined: Boolean(identity.guildJoined),
			roleIds: Array.isArray(identity.roleIds) ? identity.roleIds : [],
			lastLoginAt: identity.lastLoginAt || null
		}));
	const discord = identities.find((identity) => identity.provider === "discord") || null;
	return {
		passwordEnabled: Boolean(account.passwordHash),
		googleConnected: identities.some((identity) => identity.provider === "google"),
		discordConnected: Boolean(discord),
		discord: discord ? {
			displayName: discord.displayName || "",
			avatarUrl: discord.avatarUrl || "",
			guildJoined: Boolean(discord.guildJoined),
			roleIds: Array.isArray(discord.roleIds) ? discord.roleIds : []
		} : null,
		providers: identities
	};
}

function betaAccountState(founder, auth) {
	const isBetaTester = Boolean(founder && founder.betaTester) || Boolean(auth && auth.discordConnected);
	if (!isBetaTester) return null;
	const resources = betaResourceState();
	return {
		tester: true,
		progressPolicy: betaContent.policies.progress,
		codePolicy: betaContent.policies.codes,
		schedule: resources.schedule || null,
		resources,
		discord: auth && auth.discord ? auth.discord : null
	};
}

function betaResourceState() {
	const schedule = betaScheduleState();
	if (!schedule) return betaContent;
	return {
		...betaContent,
		schedule
	};
}

function betaScheduleState() {
	if (!betaOpenAtIso) return null;
	const openAtMs = Date.parse(betaOpenAtIso);
	const remainingMs = Math.max(0, openAtMs - Date.now());
	return {
		openAt: betaOpenAtIso,
		now: new Date().toISOString(),
		remainingMs,
		locked: remainingMs > 0,
		manualOpen: true
	};
}

function launchScheduleState() {
	if (!launchOpenAtIso) return null;
	const openAtMs = Date.parse(launchOpenAtIso);
	const remainingMs = Math.max(0, openAtMs - Date.now());
	const starterCard = launchFreeCardWindowState();
	return {
		openAt: launchOpenAtIso,
		now: new Date().toISOString(),
		remainingMs,
		locked: remainingMs > 0,
		starterCard
	};
}

function founderReservationsOpen() {
	if (betaMode || launchSignupMode) return false;
	if (!publicMode) return true;
	const openAtMs = Date.parse(launchOpenAtIso || "");
	return Number.isFinite(openAtMs) && Date.now() < openAtMs;
}

function launchFreeCardWindowState() {
	if (!launchOpenAtIso) {
		return {
			open: true,
			endsAt: null,
			remainingMs: null
		};
	}
	const openAtMs = Date.parse(launchOpenAtIso);
	const endsAtMs = openAtMs + launchFreeCardHours * 60 * 60 * 1000;
	const nowMs = Date.now();
	return {
		openAt: launchOpenAtIso,
		endsAt: new Date(endsAtMs).toISOString(),
		open: nowMs < endsAtMs,
		prelaunch: nowMs < openAtMs,
		remainingMs: Math.max(0, endsAtMs - nowMs)
	};
}

function launchFreeCardWindowOpen() {
	return launchFreeCardWindowState().open;
}

async function rewardState(store, account) {
	const starterCards = store.entitlements.filter((entry) =>
		entry.accountId === account.id &&
		entry.type === starterFreeSubscriptionType &&
		entry.status === "granted"
	);
	const ledger = await starterCardLedgerState(account);
	const hasGameLedger = Boolean(ledger.configured);
	let starterSubscriptionCards = starterCards.length;
	let starterSubscriptionCardsClaimed = 0;
	let starterCardStatus = starterSubscriptionCards > 0 ? "waiting" : "none";

	if (hasGameLedger) {
		if (ledger.status === "claimed") {
			starterSubscriptionCards = 0;
			starterSubscriptionCardsClaimed = 1;
			starterCardStatus = "claimed";
		} else if (ledger.status === "waiting") {
			starterSubscriptionCards = Math.max(1, starterCards.length);
			starterCardStatus = "waiting";
		} else if (ledger.status === "unknown") {
			starterSubscriptionCards = 0;
			starterCardStatus = "unknown";
		} else {
			starterSubscriptionCards = 0;
			starterCardStatus = "none";
		}
	}

	const visibleCards = starterCards.length > 0
		? starterCards
		: (starterSubscriptionCards > 0 || starterSubscriptionCardsClaimed > 0)
			? [{
				id: null,
				type: starterFreeSubscriptionType,
				status: starterCardStatus === "claimed" ? "claimed" : "granted",
				source: "openrsc_ledger",
				createdAt: null
			}]
			: [];

	const paidSubscriptionCards = await paidSubscriptionCardState(account.id);
	return {
		starterSubscriptionCards,
		starterSubscriptionCardsClaimed,
		starterCardStatus,
		starterCardLedger: {
			synced: hasGameLedger,
			status: ledger.status
		},
		paidSubscriptionCards,
		cards: visibleCards.map((entry) => ({
			id: entry.id,
			type: entry.type,
			status: starterCardStatus === "claimed" ? "claimed" : entry.status,
			source: entry.source,
			label: starterCardStatus === "claimed"
				? "Starter subscription card claimed in game"
				: "Starter subscription card reserved in Lumbridge",
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
		(emailVerified || auth.googleConnected || auth.discordConnected ? 20 : 0) +
		(hasRecoveryCodes ? 20 : 0) +
		(passwordChanged || auth.googleConnected || auth.discordConnected ? 10 : 0) +
		(activeSessions.length <= 2 ? 10 : 5)
	);

	return {
		score,
		emailVerified: emailVerified || auth.googleConnected || auth.discordConnected,
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

async function legendsState() {
	if (!openRscDbPath) {
		throw new HttpError(503, "openrsc_db_not_configured");
	}

	try {
		const season = sqlString(legendsSeasonId);
		const [recordRows, streakRows] = await Promise.all([
			sqliteJson(`
				SELECT record_type AS "type",
				       player_name AS "playerName",
				       subject_id AS "subjectId",
				       value AS "value",
				       claimed_at_ms AS "achievedAtMs"
				FROM world_achievement_records
				WHERE season_id = ${season}
				  AND (
				    (record_type = 'first_skill' AND subject_id BETWEEN 0 AND 19 AND value IN (80, 90, 99))
				    OR (record_type = 'first_item' AND subject_id = 575 AND value = 1)
				  )
				ORDER BY claimed_at_ms ASC, record_type ASC, subject_id ASC, value ASC, record_key ASC
				LIMIT 200;
			`),
			sqliteJson(`
				SELECT player_name AS "playerName",
				       current_streak AS "currentStreak",
				       best_streak AS "bestStreak",
				       qualified_kills AS "qualifiedKills",
				       last_qualified_at_ms AS "lastQualifiedAtMs"
				FROM world_pk_streaks
				WHERE season_id = ${season}
				  AND qualified_kills > 0
				ORDER BY best_streak DESC,
				         qualified_kills DESC,
				         current_streak DESC,
				         lower(player_name) ASC,
				         player_name ASC,
				         player_id ASC
				LIMIT 200;
			`)
		]);

		const firsts = recordRows
			.map(publicLegendRecord)
			.filter(Boolean);
		const leaders = streakRows
			.map(publicLegendStreak)
			.filter(Boolean)
			.slice(0, 50)
			.map((entry, index) => ({ rank: index + 1, ...entry }));
		return {
			seasonId: legendsSeasonId,
			firsts,
			pvp: { leaders }
		};
	} catch (error) {
		console.error("legends_read_failed", error instanceof Error ? error.message : "unknown_error");
		throw new HttpError(503, "legends_unavailable");
	}
}

function publicLegendRecord(row) {
	const type = String(row && row.type || "");
	const playerName = publicLegendPlayerName(row && row.playerName);
	const subjectId = publicSafeNonNegativeInteger(row && row.subjectId);
	const value = publicSafeNonNegativeInteger(row && row.value);
	const achievedAtMs = publicSafeNonNegativeInteger(row && row.achievedAtMs);
	const validSkill = type === "first_skill"
		&& subjectId !== null && subjectId <= 19
		&& [80, 90, 99].includes(value);
	const validItem = type === "first_item" && subjectId === 575 && value === 1;
	if (!playerName || achievedAtMs === null || achievedAtMs <= 0 || (!validSkill && !validItem)) {
		return null;
	}
	return { type, playerName, subjectId, value, achievedAtMs };
}

function publicLegendStreak(row) {
	const playerName = publicLegendPlayerName(row && row.playerName);
	const currentStreak = publicSafeNonNegativeInteger(row && row.currentStreak);
	const bestStreak = publicSafeNonNegativeInteger(row && row.bestStreak);
	const qualifiedKills = publicSafeNonNegativeInteger(row && row.qualifiedKills);
	const lastQualifiedAtMs = publicSafeNonNegativeInteger(row && row.lastQualifiedAtMs);
	if (!playerName
		|| currentStreak === null || bestStreak === null || qualifiedKills === null
		|| lastQualifiedAtMs === null || lastQualifiedAtMs <= 0
		|| qualifiedKills <= 0 || bestStreak <= 0
		|| currentStreak > bestStreak || bestStreak > qualifiedKills) {
		return null;
	}
	return { playerName, currentStreak, bestStreak, qualifiedKills, lastQualifiedAtMs };
}

function publicLegendPlayerName(value) {
	const rawPlayerName = String(value || "");
	const playerName = cleanUsername(rawPlayerName);
	return playerName === rawPlayerName && normalizeUsername(playerName) ? playerName : "";
}

function publicSafeNonNegativeInteger(value) {
	if (typeof value !== "number" && typeof value !== "string") return null;
	if (typeof value === "string" && !/^(0|[1-9]\d*)$/.test(value)) return null;
	const number = Number(value);
	return Number.isSafeInteger(number) && number >= 0 ? number : null;
}

async function publicState(store) {
	const founderUnlocks = store.founders.filter((founder) =>
		Boolean(founder.starterCardUnlocked) || founder.creditedReferrals >= 2
	).length;
	const betaTesterCount = store.founders.filter((founder) => founder.betaTester).length;
	const integrity = await integrityState(store);
	const playersOnline = await openRscPlayersOnlineCount();
	const launchSchedule = launchScheduleState();
	if (betaMode) {
		const betaResources = betaResourceState();
		const betaSchedule = betaResources.schedule || null;
		const betaSignupCounter = betaSignupCounterState(betaTesterCount);
		return {
			publicMode: Boolean(publicMode),
			betaMode: true,
			launch: launchSchedule,
			worldRules: worldRules(),
			oauth: oauthPublicState(),
			captcha: captchaPublicState(),
			status: {
				world: betaSchedule && betaSchedule.locked ? "Launch Countdown" : "Public Beta",
				online: !(betaSchedule && betaSchedule.locked),
				playersOnline,
				patch: betaSchedule && betaSchedule.locked ? "countdown" : "beta",
				lastSave: ""
			},
			rates: xpRates(),
			founderStats: {
				reservations: store.founders.length,
				starterCardsUnlocked: founderUnlocks,
				betaTesters: betaTesterCount,
				betaSignupCounter: betaSignupCounter.count
			},
			downloads: await downloadState({ includePrivate: false }),
			integrity,
			beta: {
				...betaResources,
				signupCounter: betaSignupCounter
			},
			news: [],
			highscores: [],
			market: [],
			activity: []
		};
	}
	if (publicMode) {
		// No fake world stats on the public site; downloads stay open without auth.
		const launchOpen = launchSchedule && !launchSchedule.locked;
		return {
			publicMode: true,
			launchSignupMode,
			launch: launchSchedule,
			worldRules: worldRules(),
			oauth: oauthPublicState(),
			captcha: captchaPublicState(),
			status: {
				world: launchSchedule && launchSchedule.locked ? "Launch Countdown" : launchOpen ? "Launch Open" : "Voidscape",
				online: launchOpen,
				playersOnline: launchOpen ? playersOnline : 0,
				patch: launchOpen ? "launch" : "prelaunch",
				lastSave: ""
			},
			rates: xpRates(),
			founderStats: {
				reservations: store.founders.length,
				starterCardsUnlocked: founderUnlocks
			},
			downloads: await downloadState({ includePrivate: false }),
			integrity,
			news: [],
			highscores: [],
			market: [],
			activity: []
		};
	}
	return {
		launch: launchSchedule,
		worldRules: worldRules(),
		oauth: oauthPublicState(),
		captcha: captchaPublicState(),
		status: {
			world: "World 1",
			online: true,
			playersOnline: openRscDbPath ? playersOnline : 247,
			patch: "0.8.7",
			lastSave: "2 min ago"
		},
		rates: xpRates(),
		founderStats: {
			reservations: store.founders.length,
			starterCardsUnlocked: founderUnlocks
		},
		downloads: (await downloadState()).concat(publicContent.downloads),
		integrity,
		news: publicContent.news,
		highscores: publicContent.highscores,
		market: publicContent.market,
		activity: dynamicActivity(store).concat(publicContent.activity).slice(0, 8)
	};
}

async function openRscPlayersOnlineCount() {
	if (!openRscDbPath) return 0;
	try {
		const rows = await sqliteJson("SELECT COUNT(*) AS playersOnline FROM players WHERE online = 1");
		const count = Number(rows[0] && rows[0].playersOnline);
		return Number.isFinite(count) && count > 0 ? Math.floor(count) : 0;
	} catch (error) {
		console.warn(`Unable to read OpenRSC online count: ${error.message || error}`);
		return 0;
	}
}

async function openRscCharacterAvailability(username) {
	const normalized = normalizeUsername(username);
	if (!normalized) {
		throw new HttpError(400, "invalid_username");
	}
	if (!openRscDbPath) {
		return { available: null, reason: "openrsc_db_not_configured" };
	}
	try {
		const snapshot = await openRscCharacterSnapshot(username);
		return {
			available: false,
			character: {
				name: snapshot.name
			}
		};
	} catch (error) {
		if (error.status === 404 && error.message === "character_not_found") {
			return { available: true };
		}
		if (error.status === 503 && error.message === "openrsc_db_not_configured") {
			return { available: null, reason: "openrsc_db_not_configured" };
		}
		throw error;
	}
}

async function integrityState(store) {
	const snapshot = await readIntegritySnapshot();
	if (snapshot) {
		const normalized = normalizeIntegritySnapshot(snapshot, "snapshot");
		normalized.build = await buildIntegrity(normalized.build);
		return normalized;
	}

	let staffCommands = null;
	let itemProvenance = null;
	if (openRscDbPath) {
		try {
			staffCommands = await openRscIntegrityStaffCommands();
			itemProvenance = await openRscIntegrityItemProvenance();
		} catch (error) {
			staffCommands = emptyStaffCommandIntegrity("openrsc_unavailable", "openrsc-sqlite");
			itemProvenance = emptyItemProvenanceIntegrity("openrsc_unavailable", "openrsc-sqlite", "openrsc_query_failed");
		}
	}

	if (!staffCommands) {
		staffCommands = emptyStaffCommandIntegrity("waiting_for_game_snapshot", "portal-store");
	}
	if (!itemProvenance) {
		itemProvenance = emptyItemProvenanceIntegrity("not_recording", "portal-store", "waiting_for_game_snapshot");
	}

	return {
		generatedAt: now(),
		source: staffCommands.source,
		privacy: "Public integrity data excludes IP addresses, raw player identifiers, and full staff command arguments.",
		staffCommands,
		portalAudit: portalAuditIntegrity(store),
		build: await buildIntegrity(),
		itemProvenance,
		accountIntegrity: {
			status: "planned",
			lastScanAt: null,
			flagged: 0,
			review: 0
		},
		economyScans: {
			status: "planned",
			lastScanAt: null,
			flagged: 0,
			fixed: 0
		}
	};
}

async function readIntegritySnapshot() {
	try {
		return JSON.parse(await readFile(integritySnapshotPath, "utf8"));
	} catch (error) {
		return null;
	}
}

function normalizeIntegritySnapshot(snapshot, fallbackSource) {
	const staffCommands = normalizeStaffCommandIntegrity(
		snapshot && snapshot.staffCommands,
		fallbackSource || "snapshot"
	);
	return {
		generatedAt: validIsoTimestamp(snapshot && snapshot.generatedAt) || now(),
		source: sanitizeIntegrityLabel(snapshot && snapshot.source, fallbackSource || "snapshot"),
		privacy: "Public integrity data excludes IP addresses, raw player identifiers, and full staff command arguments.",
		staffCommands,
		portalAudit: normalizePortalAuditIntegrity(snapshot && snapshot.portalAudit),
		build: normalizeBuildIntegrity(snapshot && snapshot.build),
		itemProvenance: normalizeItemProvenanceIntegrity(snapshot && snapshot.itemProvenance, fallbackSource || "snapshot"),
		accountIntegrity: normalizeSimpleIntegrityBucket(snapshot && snapshot.accountIntegrity, "planned"),
		economyScans: normalizeSimpleIntegrityBucket(snapshot && snapshot.economyScans, "planned")
	};
}

async function openRscIntegrityStaffCommands() {
	const tableRows = await sqliteJson("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'staff_logs'");
	if (!tableRows.length) {
		return emptyStaffCommandIntegrity("missing_staff_log_table", "openrsc-sqlite");
	}
	const since = Math.floor(Date.now() / 1000) - 24 * 60 * 60;
	const rows = await sqliteJson(`
		SELECT time, extra
		FROM staff_logs
		WHERE action = 24
			AND extra LIKE 'integrity %'
			AND time >= ${since}
		ORDER BY time DESC
		LIMIT 250
	`);
	return staffCommandIntegrityFromRows(rows, "openrsc-sqlite");
}

async function openRscIntegrityItemProvenance() {
	const tableRows = await sqliteJson("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'item_provenance_events'");
	if (!tableRows.length) {
		return emptyItemProvenanceIntegrity("not_recording", "openrsc-sqlite", "missing_item_provenance_table");
	}
	const since24 = Math.floor(Date.now() / 1000) - 24 * 60 * 60;
	const since7d = Math.floor(Date.now() / 1000) - 7 * 24 * 60 * 60;
	const totals = await sqliteJson(`
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
	const rows = await sqliteJson(`
		SELECT time, event_type, source, destination, command, catalogID, amount, noted
		FROM item_provenance_events
		WHERE time >= ${since7d}
		ORDER BY time DESC, eventID DESC
		LIMIT 250
	`);
	return itemProvenanceIntegrityFromRows(rows, totals[0] || {}, "openrsc-sqlite");
}

function itemProvenanceIntegrityFromRows(rows, totals, source) {
	const destinations = new Map();
	const sources = new Map();
	const recent = [];
	for (const row of rows) {
		const eventType = sanitizeIntegrityLabel(row.event_type, "event");
		const provenanceSource = sanitizeIntegrityLabel(row.source, "unknown");
		const destination = sanitizeIntegrityLabel(row.destination, "unknown");
		sources.set(provenanceSource, (sources.get(provenanceSource) || 0) + 1);
		destinations.set(destination, (destinations.get(destination) || 0) + 1);
		if (recent.length < 8) {
			recent.push({
				at: unixSecondsToIso(row.time),
				eventType,
				source: provenanceSource,
				destination,
				command: sanitizeIntegrityLabel(row.command, "staff"),
				catalogId: nonNegativeInteger(row.catalogID),
				amount: nonNegativeInteger(row.amount),
				noted: Number(row.noted || 0) === 1
			});
		}
	}
	const totalEvents = nonNegativeInteger(totals.total_events);
	const staffMints24h = nonNegativeInteger(totals.staff_mints_24h);
	let status = "recording_no_staff_mints";
	if (staffMints24h > 0) status = "recording";
	else if (totalEvents > 0) status = "recording_no_recent_staff_mints";
	return {
		status,
		source,
		trackedItems: totalEvents,
		totalEvents,
		total24h: nonNegativeInteger(totals.total_24h),
		total7d: nonNegativeInteger(totals.total_7d),
		staffMints24h,
		staffMints7d: nonNegativeInteger(totals.staff_mints_7d),
		origins24h: nonNegativeInteger(totals.origins_24h),
		origins7d: nonNegativeInteger(totals.origins_7d),
		npcDrops24h: nonNegativeInteger(totals.npc_drops_24h),
		npcDrops7d: nonNegativeInteger(totals.npc_drops_7d),
		originAmount24h: nonNegativeInteger(totals.origin_amount_24h),
		transfers24h: nonNegativeInteger(totals.transfers_24h),
		transfers7d: nonNegativeInteger(totals.transfers_7d),
		amount24h: nonNegativeInteger(totals.amount_24h),
		catalogs24h: nonNegativeInteger(totals.catalogs_24h),
		sources: categoryCounts(sources),
		destinations: categoryCounts(destinations),
		recent
	};
}

function staffCommandIntegrityFromRows(rows, source) {
	const counts = new Map();
	const recent = [];
	let allowed24h = 0;
	let blocked24h = 0;
	for (const row of rows) {
		const fields = parseStaffAuditExtra(row.extra);
		const category = sanitizeIntegrityLabel(fields.category, "staff");
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
		source,
		total24h,
		allowed24h,
		blocked24h,
		categories: categoryCounts(counts),
		recent
	};
}

function parseStaffAuditExtra(extra) {
	const fields = {};
	String(extra || "").split(/\s+/).forEach((part) => {
		const separator = part.indexOf("=");
		if (separator <= 0) return;
		fields[part.slice(0, separator)] = part.slice(separator + 1);
	});
	return fields;
}

function normalizeStaffCommandIntegrity(input, source) {
	if (!input || typeof input !== "object") {
		return emptyStaffCommandIntegrity("waiting_for_game_snapshot", source);
	}
	return {
		status: sanitizeIntegrityLabel(input.status, "active"),
		source: sanitizeIntegrityLabel(input.source, source || "snapshot"),
		total24h: nonNegativeInteger(input.total24h),
		allowed24h: nonNegativeInteger(input.allowed24h),
		blocked24h: nonNegativeInteger(input.blocked24h),
		categories: normalizeIntegrityCategories(input.categories),
		recent: normalizeIntegrityRecent(input.recent)
	};
}

function emptyStaffCommandIntegrity(status, source) {
	return {
		status,
		source,
		total24h: 0,
		allowed24h: 0,
		blocked24h: 0,
		categories: [],
		recent: []
	};
}

function portalAuditIntegrity(store) {
	const since = Date.now() - 24 * 60 * 60 * 1000;
	const counts = new Map();
	for (const entry of store.audit) {
		const createdAt = Date.parse(entry.createdAt || "");
		if (!Number.isFinite(createdAt) || createdAt < since) continue;
		const category = sanitizeIntegrityLabel(entry.type, "portal");
		counts.set(category, (counts.get(category) || 0) + 1);
	}
	return {
		total24h: Array.from(counts.values()).reduce((sum, count) => sum + count, 0),
		categories: categoryCounts(counts).slice(0, 8)
	};
}

function normalizePortalAuditIntegrity(input) {
	if (!input || typeof input !== "object") {
		return { total24h: 0, categories: [] };
	}
	return {
		total24h: nonNegativeInteger(input.total24h),
		categories: normalizeIntegrityCategories(input.categories).slice(0, 8)
	};
}

function normalizeBuildIntegrity(input) {
	if (!input || typeof input !== "object") {
		return {
			status: "available",
			evidence: "Launcher manifests publish SHA-256 hashes for downloadable client artifacts."
		};
	}
	return {
		status: sanitizeIntegrityLabel(input.status, "available"),
		evidence: String(input.evidence || "").slice(0, 160),
		artifacts: normalizeBuildArtifacts(input.artifacts),
		manifest: normalizeBuildManifest(input.manifest),
		source: normalizeBuildSource(input.source)
	};
}

function normalizeBuildArtifacts(input) {
	if (!Array.isArray(input)) return [];
	return input.slice(0, 6).map((row) => ({
		slug: sanitizeIntegrityLabel(row.slug, "artifact"),
		label: String(row.label || "Build artifact").slice(0, 80),
		url: safeRelativeUrl(row.url),
		available: row.available === true,
		publicDownload: row.publicDownload !== false,
		sizeBytes: nonNegativeInteger(row.sizeBytes),
		updatedAt: validIsoTimestamp(row.updatedAt) || "",
		sha256: safeSha256(row.sha256)
	}));
}

function normalizeBuildManifest(input) {
	if (!input || typeof input !== "object") {
		return { status: "unknown", url: "/api/launcher/manifest.properties", fileCount: 0, clientSha256: "" };
	}
	return {
		status: sanitizeIntegrityLabel(input.status, "unknown"),
		url: safeRelativeUrl(input.url) || "/api/launcher/manifest.properties",
		version: String(input.version || "").slice(0, 40),
		fileCount: nonNegativeInteger(input.fileCount),
		clientSha256: safeSha256(input.clientSha256),
		updatedAt: validIsoTimestamp(input.updatedAt) || ""
	};
}

function normalizeBuildSource(input) {
	if (!input || typeof input !== "object") {
		return { status: "source_pending", repositoryUrl: "", commit: "", shortCommit: "", branch: "", dirty: false };
	}
	const status = sanitizeIntegrityLabel(input.status, "source_pending");
	const publicationPending = status === "publication_pending" || status === "source_pending";
	const commit = safeCommitHash(input.commit);
	return {
		status,
		repositoryUrl: publicationPending ? "" : (safeHttpUrl(input.repositoryUrl) || safeHttpUrl(sourceRepositoryUrl)),
		commit: publicationPending ? "" : commit,
		shortCommit: publicationPending ? "" : String(input.shortCommit || commit.slice(0, 12)).slice(0, 16),
		branch: publicationPending ? "" : sanitizeSourceText(input.branch, 64),
		dirty: publicationPending ? false : input.dirty === true,
		generatedAt: validIsoTimestamp(input.generatedAt) || ""
	};
}

async function buildIntegrity(input) {
	const normalized = normalizeBuildIntegrity(input);
	const proof = await buildProofState();
	return {
		...normalized,
		status: proof.status,
		evidence: proof.evidence,
		artifacts: proof.artifacts.length ? proof.artifacts : normalized.artifacts,
		manifest: proof.manifest.status !== "unknown" ? proof.manifest : normalized.manifest,
		source: proof.source.status !== "source_pending" ? proof.source : normalized.source
	};
}

function normalizeItemProvenanceIntegrity(input, source) {
	if (!input || typeof input !== "object") {
		return emptyItemProvenanceIntegrity("not_recording", source || "snapshot", "missing_item_provenance");
	}
	return {
		status: sanitizeIntegrityLabel(input.status, "recording_no_staff_mints"),
		source: sanitizeIntegrityLabel(input.source, source || "snapshot"),
		reason: input.reason ? sanitizeIntegrityLabel(input.reason, "") : "",
		trackedItems: nonNegativeInteger(input.trackedItems),
		totalEvents: nonNegativeInteger(input.totalEvents),
		total24h: nonNegativeInteger(input.total24h),
		total7d: nonNegativeInteger(input.total7d),
		staffMints24h: nonNegativeInteger(input.staffMints24h),
		staffMints7d: nonNegativeInteger(input.staffMints7d),
		origins24h: nonNegativeInteger(input.origins24h),
		origins7d: nonNegativeInteger(input.origins7d),
		npcDrops24h: nonNegativeInteger(input.npcDrops24h),
		npcDrops7d: nonNegativeInteger(input.npcDrops7d),
		originAmount24h: nonNegativeInteger(input.originAmount24h),
		transfers24h: nonNegativeInteger(input.transfers24h),
		transfers7d: nonNegativeInteger(input.transfers7d),
		amount24h: nonNegativeInteger(input.amount24h),
		catalogs24h: nonNegativeInteger(input.catalogs24h),
		sources: normalizeIntegrityCategories(input.sources),
		destinations: normalizeIntegrityCategories(input.destinations),
		recent: normalizeItemProvenanceRecent(input.recent)
	};
}

function emptyItemProvenanceIntegrity(status, source, reason) {
	return {
		status,
		source,
		reason: reason || "",
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

function normalizeSimpleIntegrityBucket(input, fallbackStatus) {
	if (!input || typeof input !== "object") {
		return { status: fallbackStatus };
	}
	const normalized = { status: sanitizeIntegrityLabel(input.status, fallbackStatus) };
	if (input.source !== undefined) normalized.source = sanitizeIntegrityLabel(input.source, "snapshot");
	if (input.trackedItems !== undefined) normalized.trackedItems = nonNegativeInteger(input.trackedItems);
	if (input.checkedPlayers !== undefined) normalized.checkedPlayers = nonNegativeInteger(input.checkedPlayers);
	if (input.lastScanAt !== undefined) normalized.lastScanAt = validIsoTimestamp(input.lastScanAt) || null;
	if (input.flagged !== undefined) normalized.flagged = nonNegativeInteger(input.flagged);
	if (input.review !== undefined) normalized.review = nonNegativeInteger(input.review);
	if (input.fixed !== undefined) normalized.fixed = nonNegativeInteger(input.fixed);
	if (input.highSeverity !== undefined) normalized.highSeverity = nonNegativeInteger(input.highSeverity);
	if (input.privilegedAccounts !== undefined) normalized.privilegedAccounts = nonNegativeInteger(input.privilegedAccounts);
	if (input.watchedCacheRows !== undefined) normalized.watchedCacheRows = nonNegativeInteger(input.watchedCacheRows);
	if (input.recentSensitiveCommands24h !== undefined) normalized.recentSensitiveCommands24h = nonNegativeInteger(input.recentSensitiveCommands24h);
	if (input.staffMints24h !== undefined) normalized.staffMints24h = nonNegativeInteger(input.staffMints24h);
	if (input.staffMintAmount24h !== undefined) normalized.staffMintAmount24h = nonNegativeInteger(input.staffMintAmount24h);
	if (input.categories !== undefined) normalized.categories = normalizeIntegrityCategories(input.categories);
	if (input.reviewCategories !== undefined) normalized.reviewCategories = normalizeIntegrityCategories(input.reviewCategories);
	if (input.privacy !== undefined) normalized.privacy = String(input.privacy || "").slice(0, 180);
	return normalized;
}

function normalizeIntegrityCategories(input) {
	if (!input) return [];
	const rows = Array.isArray(input)
		? input
		: Object.entries(input).map(([category, count]) => ({ category, count }));
	return rows
		.map((row) => ({
			category: sanitizeIntegrityLabel(row.category, "staff"),
			count: nonNegativeInteger(row.count)
		}))
		.filter((row) => row.count > 0)
		.sort((a, b) => b.count - a.count || a.category.localeCompare(b.category))
		.slice(0, 12);
}

function normalizeItemProvenanceRecent(input) {
	if (!Array.isArray(input)) return [];
	return input.slice(0, 8).map((row) => ({
		at: validIsoTimestamp(row.at) || now(),
		eventType: sanitizeIntegrityLabel(row.eventType, "event"),
		source: sanitizeIntegrityLabel(row.source, "unknown"),
		destination: sanitizeIntegrityLabel(row.destination, "unknown"),
		command: sanitizeIntegrityLabel(row.command, "staff"),
		catalogId: nonNegativeInteger(row.catalogId),
		amount: nonNegativeInteger(row.amount),
		noted: row.noted === true
	}));
}

function normalizeIntegrityRecent(input) {
	if (!Array.isArray(input)) return [];
	return input.slice(0, 8).map((row) => ({
		at: validIsoTimestamp(row.at) || now(),
		status: row.status === "blocked" ? "blocked" : "allowed",
		category: sanitizeIntegrityLabel(row.category, "staff")
	}));
}

function categoryCounts(counts) {
	return Array.from(counts.entries())
		.map(([category, count]) => ({ category, count }))
		.sort((a, b) => b.count - a.count || a.category.localeCompare(b.category))
		.slice(0, 12);
}

function sanitizeIntegrityLabel(value, fallback) {
	const label = String(value || fallback || "unknown")
		.toLowerCase()
		.replace(/[^a-z0-9_-]+/g, "_")
		.replace(/^_+|_+$/g, "")
		.slice(0, 32);
	return label || fallback || "unknown";
}

function safeSha256(value) {
	const digest = String(value || "").trim().toLowerCase();
	return /^[a-f0-9]{64}$/.test(digest) ? digest : "";
}

function safeCommitHash(value) {
	const commit = String(value || "").trim().toLowerCase();
	return /^[a-f0-9]{7,64}$/.test(commit) ? commit : "";
}

function sanitizeSourceText(value, limit) {
	return String(value || "")
		.replace(/[^a-zA-Z0-9._/-]+/g, "-")
		.replace(/^-+|-+$/g, "")
		.slice(0, limit || 64);
}

function safeRelativeUrl(value) {
	const url = String(value || "").trim();
	if (!url || url[0] !== "/" || url.startsWith("//") || /[\s<>]/.test(url)) return "";
	return url.slice(0, 180);
}

function safeHttpUrl(value) {
	const url = String(value || "").trim();
	if (!/^https:\/\/[a-z0-9.-]+(?:\/[^\s<>]*)?$/i.test(url)) return "";
	return url.slice(0, 220);
}

function nonNegativeInteger(value) {
	const number = Number(value);
	return Number.isFinite(number) && number > 0 ? Math.floor(number) : 0;
}

function validIsoTimestamp(value) {
	const timestamp = Date.parse(value || "");
	if (!Number.isFinite(timestamp)) return "";
	return new Date(timestamp).toISOString();
}

function unixSecondsToIso(value) {
	const seconds = Number(value);
	if (!Number.isFinite(seconds) || seconds <= 0) return now();
	return new Date(seconds * 1000).toISOString();
}

function betaSignupCounterState(realBetaTesterCount) {
	const startMs = Date.parse(betaSignupCounterStartedAtIso);
	const elapsedHours = Math.max(0, Math.floor((Date.now() - startMs) / (60 * 60 * 1000)));
	let hourlyLift = 0;
	for (let hour = 0; hour < elapsedHours; hour += 1) {
		hourlyLift += betaSignupHourlyIncrement(hour);
	}
	const displayCount = Math.max(
		Number(realBetaTesterCount || 0),
		betaSignupCounterBase + hourlyLift
	);
	return {
		count: displayCount,
		realCount: Number(realBetaTesterCount || 0),
		nextUpdateAt: new Date(startMs + (elapsedHours + 1) * 60 * 60 * 1000).toISOString()
	};
}

function betaSignupHourlyIncrement(hourIndex) {
	const digest = createHash("sha256")
		.update(`${betaSignupCounterSeed}:${hourIndex}`)
		.digest();
	return 1 + (digest[0] % 3);
}

async function downloadState(options = {}) {
	const includePrivate = options.includePrivate !== false;
	const rows = [{
		slug: "web-client",
		label: "Web client",
		state: "Supported browsers",
		url: webClientUrl,
		available: true,
		publicDownload: true,
		external: true,
		mobileOnly: true
	}];
	if (androidPlayUrl) {
		rows.push({
			slug: "android-play",
			label: "Google Play",
			state: "Recommended Android install",
			url: androidPlayUrl,
			available: true,
			publicDownload: true,
			external: true,
			platform: "android",
			platformPrimary: true
		});
	}
	for (const artifact of downloadArtifacts) {
		if (!includePrivate && artifact.publicDownload === false) {
			continue;
		}
		try {
			const fileStat = await stat(artifact.path);
			const sha256 = await sha256File(artifact.path, fileStat);
			const metadata = await downloadArtifactMetadata(artifact);
			const androidFallback = artifact.slug === "android-apk" && Boolean(androidPlayUrl);
			rows.push({
				slug: artifact.slug,
				label: androidFallback ? "Signed APK fallback" : artifact.label,
				state: androidFallback ? `Direct install fallback · ${formatBytes(fileStat.size)}` : `Built ${formatBytes(fileStat.size)}`,
				url: publicDownloadUrl(`/downloads/${artifact.slug}`),
				available: true,
				publicDownload: artifact.publicDownload !== false,
				primary: artifact.slug === "launcher",
				...(androidFallback ? { fallback: true, platform: "android" } : {}),
				sizeBytes: fileStat.size,
				updatedAt: fileStat.mtime.toISOString(),
				sha256,
				...metadata
			});
		} catch (error) {
			const androidFallback = artifact.slug === "android-apk" && Boolean(androidPlayUrl);
			rows.push({
				slug: artifact.slug,
				label: androidFallback ? "Signed APK fallback" : artifact.label,
				state: androidFallback ? "Direct install fallback unavailable" : (artifact.unavailableState || "Run scripts/build.sh"),
				url: "#",
				available: false,
				publicDownload: artifact.publicDownload !== false,
				primary: artifact.slug === "launcher",
				...(androidFallback ? { fallback: true, platform: "android" } : {})
			});
		}
	}
	return rows;
}

async function downloadArtifactMetadata(artifact) {
	if (artifact.slug !== "android-apk") return {};
	try {
		const body = await readFile(`${artifact.path}.json`, "utf8");
		const parsed = JSON.parse(body);
		const clientVersion = Number(parsed.clientVersion);
		if (Number.isInteger(clientVersion) && clientVersion >= 0) {
			return { clientVersion };
		}
	} catch (error) {
		// Sidecar metadata is optional; the release preflight enforces it for promotion.
	}
	return {};
}

async function buildProofState() {
	const artifacts = await downloadState({ includePrivate: true, publicSurface: true });
	const publicArtifacts = artifacts
		.filter((artifact) => artifact.publicDownload !== false && artifact.external !== true)
		.map((artifact) => ({
			slug: artifact.slug,
			label: artifact.label,
			url: artifact.url,
			available: artifact.available === true,
			publicDownload: true,
			sizeBytes: nonNegativeInteger(artifact.sizeBytes),
			updatedAt: validIsoTimestamp(artifact.updatedAt) || "",
			sha256: safeSha256(artifact.sha256),
			...(nonNegativeInteger(artifact.clientVersion) ? { clientVersion: nonNegativeInteger(artifact.clientVersion) } : {})
		}));
	const manifest = await launcherManifestProof();
	const source = await sourceProofState();
	const availableArtifacts = publicArtifacts.filter((artifact) => artifact.available).length;
	const status = availableArtifacts > 0 || manifest.status === "available" ? "available" : "waiting_for_artifacts";
	return {
		status,
		evidence: "Download and launcher-manifest hashes are generated from the files being served.",
		artifacts: publicArtifacts,
		manifest,
		source
	};
}

async function launcherManifestProof() {
	const clientArtifact = downloadArtifacts.find((entry) => entry.slug === "client-runtime");
	try {
		const fileStat = await stat(clientArtifact.path);
		if (!fileStat.isFile()) throw new Error("not_file");
		const cacheFileCount = await countClientCacheManifestFiles();
		const latestMtimeMs = Math.max(fileStat.mtimeMs, await latestClientCacheManifestMtime());
		return {
			status: "available",
			url: "/api/launcher/manifest.properties",
			version: new Date(latestMtimeMs).toISOString().replace(/[-:]/g, "").replace(/\.\d{3}Z$/, "Z"),
			fileCount: 1 + cacheFileCount,
			clientSha256: await sha256File(clientArtifact.path, fileStat),
			updatedAt: new Date(latestMtimeMs).toISOString()
		};
	} catch (error) {
		return {
			status: "waiting_for_artifacts",
			url: "/api/launcher/manifest.properties",
			fileCount: 0,
			clientSha256: ""
		};
	}
}

async function countClientCacheManifestFiles() {
	try {
		const files = await listClientCacheFiles(clientCacheDir);
		return files.filter((filePath) => {
			const relativePath = relative(clientCacheDir, filePath).replace(/\\/g, "/");
			return !isRuntimeCachePath(relativePath);
		}).length;
	} catch (error) {
		return 0;
	}
}

async function latestClientCacheManifestMtime() {
	try {
		const files = await listClientCacheFiles(clientCacheDir);
		let latest = 0;
		for (const filePath of files) {
			const relativePath = relative(clientCacheDir, filePath).replace(/\\/g, "/");
			if (isRuntimeCachePath(relativePath)) continue;
			const fileStat = await stat(filePath);
			if (fileStat.isFile()) latest = Math.max(latest, fileStat.mtimeMs);
		}
		return latest;
	} catch (error) {
		return 0;
	}
}

async function sourceProofState() {
	const metadata = await readBuildMetadata();
	const metadataStatus = sanitizeIntegrityLabel(metadata.status, "source_pending");
	if (metadataStatus === "publication_pending" || metadataStatus === "source_pending") {
		return normalizeBuildSource({
			status: metadataStatus,
			repositoryUrl: "",
			commit: "",
			shortCommit: "",
			branch: "",
			dirty: false,
			generatedAt: metadata.generatedAt || ""
		});
	}
	const commit = safeCommitHash(process.env.PORTAL_SOURCE_COMMIT || process.env.PORTAL_GIT_COMMIT || metadata.commit || await gitValue(["rev-parse", "HEAD"]));
	const branch = sanitizeSourceText(process.env.PORTAL_SOURCE_BRANCH || process.env.PORTAL_GIT_BRANCH || metadata.branch || await gitValue(["branch", "--show-current"]), 64);
	const dirty = metadata.dirty === true || (metadata.dirty !== false && await gitDirty());
	return normalizeBuildSource({
		status: commit ? (dirty ? "publish_pending" : "commit_recorded") : "source_pending",
		repositoryUrl: process.env.PORTAL_SOURCE_URL || metadata.repositoryUrl || sourceRepositoryUrl,
		commit,
		shortCommit: commit.slice(0, 12),
		branch,
		dirty,
		generatedAt: metadata.generatedAt || ""
	});
}

async function readBuildMetadata() {
	try {
		const metadata = JSON.parse(await readFile(buildMetadataPath, "utf8"));
		return metadata && typeof metadata === "object" ? metadata : {};
	} catch (error) {
		return {};
	}
}

async function gitValue(args) {
	try {
		const result = await execFile("git", ["-C", repoRoot, ...args], { timeout: 1500 });
		return String(result.stdout || "").trim();
	} catch (error) {
		return "";
	}
}

async function gitDirty() {
	try {
		const result = await execFile("git", ["-C", repoRoot, "status", "--porcelain"], { timeout: 1500 });
		return String(result.stdout || "").trim().length > 0;
	} catch (error) {
		return false;
	}
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

async function openRscPlayerOwnerEmail(normalizedName) {
	if (!openRscDbPath) return "";
	const rows = await sqliteJson(`
		SELECT email
		FROM players
		WHERE lower(username) = ${sqlString(normalizedName)}
		LIMIT 1
	`);
	if (!rows.length) return "";
	const email = canonicalEmail(rows[0].email || "");
	return email || "__unknown__";
}

async function backfillNativePortalAccounts(store, options = {}) {
	if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
	const dryRun = options.dryRun === true;
	const grantMissingStarterCard = options.grantMissingStarterCard !== false;
	const grantLaunchCharacterCards = options.grantLaunchCharacterCards !== false;
	const asOfMs = normalizeNativeBackfillAsOfMs(options.asOfMs, dryRun);
	const playerColumns = await sqliteJson("PRAGMA table_info(players);");
	const hasBannedColumn = playerColumns.some((column) => String(column.name || "").toLowerCase() === "banned");
	const players = await sqliteJson(`
		SELECT id, username, email, group_id AS groupId, ${hasBannedColumn ? "banned" : "'0'"} AS banned,
		       creation_date AS creationDate,
		       combat, skill_total, quest_points, kills, npc_kills, deaths,
		       login_date, male, haircolour, topcolour, trousercolour, skincolour, headsprite, bodysprite
		FROM players
		ORDER BY id
	`);
	const cacheRows = await sqliteJson(`
		SELECT playerID, type, key, value, dbid
		FROM player_cache
		WHERE key = ${sqlString(openRscAccountIdCacheKey)}
		   OR key = 'launch_24h_card'
		   OR substr(key, 1, ${launchCharacterCardCachePrefix.length}) = ${sqlString(launchCharacterCardCachePrefix)}
		   OR (playerID = 0 AND (
			key LIKE ${sqlString(`${starterCardCachePrefix}%`)}
			OR key LIKE ${sqlString(`${playerSubscriptionCachePrefix}%`)}
			OR key LIKE ${sqlString(`${accountSubscriptionCachePrefix}%`)}
		   ))
		ORDER BY dbid
	`);
	const launchCharacterCardMarkerRows = cacheRows.filter((row) =>
		String(row.key || "").startsWith(launchCharacterCardCachePrefix));
	const webAccountLinkSnapshot = cacheRows
		.filter((row) => String(row.key || "") === openRscAccountIdCacheKey && Number(row.playerID) > 0)
		.map((row) => ({
			dbid: Number(row.dbid),
			playerId: Number(row.playerID),
			accountId: String(row.value == null ? "" : row.value),
			type: Number(row.type)
		}));
	const launchCharacterCardIntegrity = inspectLaunchCharacterCardMarkers(launchCharacterCardMarkerRows);
	const launchCharacterCardCompletion = launchCharacterCardIntegrity.completion;
	const cohort = nativeBackfillCohort(players, {
		grantMissingStarterCard,
		grantLaunchCharacterCards,
		asOfMs,
		launchCharacterCardCompletion,
		launchCharacterCardMarkers: launchCharacterCardMarkerRows.map(normalizeLaunchCharacterCardMarkerRow),
		webAccountLinks: webAccountLinkSnapshot,
		approvedExceptionPlayerIds: options.approvedExceptionPlayerIds,
		excludedExceptionPlayerIds: options.excludedExceptionPlayerIds
	});
	if (!dryRun) {
		if (!launchCharacterCardIntegrity.ok) {
			throw new HttpError(409, "native_backfill_launch_card_markers_invalid");
		}
		if (!cohort.ready) throw new HttpError(409, "native_backfill_review_required");
		if (!options.reviewToken || String(options.reviewToken) !== cohort.reviewToken) {
			throw new HttpError(409, "native_backfill_review_token_mismatch");
		}
	}
	const includedPlayerIds = new Set(cohort.includedPlayerIds);
	const includedPlayers = players.filter((player) => includedPlayerIds.has(Number(player.id)));
	const webLinks = new Map();
	const launchCardMarkers = new Map();
	const starterCardMarkers = new Map();
	const playerSubscriptionMarkers = new Map();
	const accountSubscriptionMarkers = new Map();
	const launchCharacterCardMarkers = new Map(launchCharacterCardIntegrity.campaignMarkers.map((marker) =>
		[marker.playerId, marker.state]));
	for (const row of cacheRows) {
		const playerId = Number(row.playerID);
		const key = String(row.key || "");
		if (key === openRscAccountIdCacheKey && playerId > 0) {
			const accountId = Number(row.value);
			if (Number.isInteger(accountId) && accountId > 0) {
				webLinks.set(playerId, accountId);
			}
		} else if (key === "launch_24h_card" && playerId > 0) {
			launchCardMarkers.set(playerId, Number(row.value));
		} else if (playerId === 0 && key.startsWith(starterCardCachePrefix)) {
			starterCardMarkers.set(key, Number(row.value));
		} else if (playerId === 0 && key.startsWith(playerSubscriptionCachePrefix)) {
			const nativePlayerId = Number(key.slice(playerSubscriptionCachePrefix.length));
			if (nativePlayerId > 0) playerSubscriptionMarkers.set(nativePlayerId, Number(row.value || 0));
		} else if (playerId === 0 && key.startsWith(accountSubscriptionCachePrefix)) {
			const portalAccountId = Number(key.slice(accountSubscriptionCachePrefix.length));
			if (portalAccountId > 0) accountSubscriptionMarkers.set(portalAccountId, Number(row.value || 0));
		}
	}

	const accountsById = new Map(store.accounts.map((account) => [Number(account.id), account]));
	const charactersByPlayerId = new Map();
	for (const character of store.characters) {
		const playerId = Number(character.playerId);
		if (playerId > 0 && !charactersByPlayerId.has(playerId)) {
			charactersByPlayerId.set(playerId, character);
		}
	}

	const linkInserts = [];
	const starterInserts = [];
	const launchCharacterCardInserts = [];
	const subscriptionUpserts = new Map();
	const subscriptionMigrationAccounts = new Set();
	const result = {
		dryRun,
		grantMissingStarterCard,
		grantLaunchCharacterCards,
		players: players.length,
		cohort,
		alreadyLinked: 0,
		createdAccounts: 0,
		reusedSyntheticAccounts: 0,
		createdCharacters: 0,
		updatedCharacters: 0,
		linkedPlayers: 0,
		starterCardsGranted: 0,
		starterCardsSkippedAfterLaunchSeal: 0,
		launchCharacterCardsSeeded: 0,
		launchCharacterCardsAlreadyPresent: 0,
		launchCharacterCardsSkippedAfterSeal: 0,
		launchCharacterCardCutoverWillSeal: false,
		launchCharacterCardCutoverSealed: false,
		launchCharacterCardCutoverAlreadySealed: launchCharacterCardCompletion.sealed,
		launchCharacterCardCampaign: {
			prefix: launchCharacterCardCachePrefix,
			completionKey: launchCharacterCardCompletionKey,
			availableState: launchCharacterCardAvailable,
			claimedState: launchCharacterCardClaimed,
			integrity: launchCharacterCardIntegrity,
			markersBefore: launchCharacterCardIntegrity.campaignMarkers.length,
			completionBefore: launchCharacterCardCompletion
		},
		subscriptionsMigrated: 0,
		conflicts: [],
		samples: []
	};
	for (const anomaly of launchCharacterCardIntegrity.anomalies) {
		result.conflicts.push({ reason: "launch_character_card_marker_invalid", ...anomaly });
	}
	if (!launchCharacterCardCompletion.sealed) {
		const currentPlayerIds = new Set(players.map((player) => Number(player.id)).filter((id) => id > 0));
		for (const marker of launchCharacterCardIntegrity.campaignMarkers) {
			if (currentPlayerIds.has(marker.playerId) && !includedPlayerIds.has(marker.playerId)) {
				result.conflicts.push({
					reason: "launch_character_card_marker_for_excluded_player",
					playerId: marker.playerId,
					state: marker.state,
					key: marker.key
				});
			}
		}
	}

	for (const player of includedPlayers) {
		const playerId = Number(player.id);
		const username = cleanUsername(player.username || "");
		const normalizedName = normalizeUsername(username);
		if (!playerId || !normalizedName) {
			result.conflicts.push({ playerId, username, reason: "invalid_player_row" });
			continue;
		}

		let accountId = Number(webLinks.get(playerId) || 0);
		let account = accountId > 0 ? accountsById.get(accountId) : null;
		if (accountId > 0 && !account) {
			result.conflicts.push({ playerId, username, accountId, reason: "web_account_link_missing_account" });
			continue;
		}

		const existingCharacter = charactersByPlayerId.get(playerId);
		if (!account && existingCharacter) {
			account = accountsById.get(Number(existingCharacter.accountId)) || null;
			accountId = account ? Number(account.id) : 0;
		}

		if (!account) {
			const syntheticEmail = syntheticNativeEmail(playerId);
			account = store.accounts.find((entry) => entry.emailCanonical === syntheticEmail) || null;
			if (account) {
				result.reusedSyntheticAccounts += 1;
			} else {
				account = {
					id: nextId(store, "account"),
					emailCanonical: syntheticEmail,
					emailDisplay: syntheticEmail,
					displayName: username,
					passwordHash: null,
					status: "active",
					subscriptionExpiresAt: 0,
					source: "native-client-backfill",
					syntheticEmail: true,
					requiresEmailUpgrade: true,
					nativePlayerId: playerId,
					createdAt: now(),
					updatedAt: now()
				};
				store.accounts.push(account);
				accountsById.set(Number(account.id), account);
				result.createdAccounts += 1;
				if (!dryRun) {
					audit(store, "native_portal_account_backfilled", {
						accountId: account.id,
						playerId,
						username,
						syntheticEmail
					});
				}
			}
			accountId = Number(account.id);
		} else {
			result.alreadyLinked += webLinks.has(playerId) ? 1 : 0;
			if (!account.displayName) {
				account.displayName = username;
				account.updatedAt = now();
			}
		}

		if (!webLinks.has(playerId)) {
			linkInserts.push({ playerId, accountId });
			webLinks.set(playerId, accountId);
			result.linkedPlayers += 1;
		}

		const legacySubscriptionExpiresAt = Number(playerSubscriptionMarkers.get(playerId) || 0);
		const accountSubscriptionExpiresAt = Math.max(
			Number(account.subscriptionExpiresAt || 0),
			Number(accountSubscriptionMarkers.get(accountId) || 0)
		);
		const migratedSubscriptionExpiresAt = Math.max(accountSubscriptionExpiresAt, legacySubscriptionExpiresAt);
		if (migratedSubscriptionExpiresAt > Number(account.subscriptionExpiresAt || 0)) {
			account.subscriptionExpiresAt = migratedSubscriptionExpiresAt;
			account.updatedAt = now();
			subscriptionMigrationAccounts.add(accountId);
		}
		if (migratedSubscriptionExpiresAt > Number(accountSubscriptionMarkers.get(accountId) || 0)) {
			const existingUpsert = subscriptionUpserts.get(accountId);
			if (!existingUpsert || migratedSubscriptionExpiresAt > existingUpsert.expiresAt) {
				subscriptionUpserts.set(accountId, {
					accountId,
					key: accountSubscriptionCacheKey(accountId),
					expiresAt: migratedSubscriptionExpiresAt
				});
			}
			accountSubscriptionMarkers.set(accountId, migratedSubscriptionExpiresAt);
			subscriptionMigrationAccounts.add(accountId);
		}

		const character = existingCharacter || findCharacterByAccountAndName(store, accountId, normalizedName);
		if (character && Number(character.accountId) !== accountId) {
			result.conflicts.push({
				playerId,
				username,
				accountId,
				characterAccountId: Number(character.accountId),
				reason: "character_linked_to_different_account"
			});
			continue;
		}
		const nextCharacter = nativeCharacterState(store, player, accountId, character);
		if (character) {
			if (nativeCharacterNeedsUpdate(character, nextCharacter)) {
				Object.assign(character, nextCharacter, {
					id: character.id,
					createdAt: character.createdAt || nextCharacter.createdAt,
					linkedAt: character.linkedAt || nextCharacter.linkedAt
				});
				result.updatedCharacters += 1;
			}
		} else {
			store.characters.push(nextCharacter);
			charactersByPlayerId.set(playerId, nextCharacter);
			result.createdCharacters += 1;
		}

		if (grantMissingStarterCard) {
			const starterKey = starterCardCacheKey(accountId);
			const hasLaunchCard = launchCardMarkers.has(playerId);
			const hasStarterCard = starterKey && starterCardMarkers.has(starterKey);
			const belongsToSealedLaunchCohort = !launchCharacterCardCompletion.sealed
				|| launchCharacterCardMarkers.has(playerId);
			if (starterKey && !hasLaunchCard && !hasStarterCard && belongsToSealedLaunchCohort) {
				starterCardMarkers.set(starterKey, starterCardAvailable);
				starterInserts.push({ accountId, key: starterKey });
				ensureStarterEntitlement(store, accountId, "native_client_backfill");
				result.starterCardsGranted += 1;
			} else if (starterKey && !hasLaunchCard && !hasStarterCard
				&& launchCharacterCardCompletion.sealed) {
				result.starterCardsSkippedAfterLaunchSeal += 1;
			}
		}

		if (launchCharacterCardMarkers.has(playerId)) {
			result.launchCharacterCardsAlreadyPresent += 1;
		} else if (grantLaunchCharacterCards && !launchCharacterCardCompletion.sealed) {
			launchCharacterCardMarkers.set(playerId, launchCharacterCardAvailable);
			launchCharacterCardInserts.push({
				playerId,
				key: launchCharacterCardKey(playerId)
			});
			result.launchCharacterCardsSeeded += 1;
		} else if (grantLaunchCharacterCards && launchCharacterCardCompletion.sealed) {
			result.launchCharacterCardsSkippedAfterSeal += 1;
		}

		if (result.samples.length < 12 && (linkInserts.some((row) => row.playerId === playerId) || !character)) {
			result.samples.push({
				playerId,
				username,
				accountId,
				email: account.emailCanonical,
				cardSource: launchCardMarkers.has(playerId) ? "launch_24h_card" : starterCardCacheKey(accountId)
			});
		}
	}

	if (result.conflicts.length && !dryRun) {
		throw new HttpError(409, "native_backfill_conflicts");
	}
	result.subscriptionsMigrated = subscriptionMigrationAccounts.size;
	const sealLaunchCharacterCardCutover = grantLaunchCharacterCards && !launchCharacterCardCompletion.sealed;
	result.launchCharacterCardCutoverWillSeal = sealLaunchCharacterCardCutover;
	result.launchCharacterCardCutoverSealed = !dryRun && sealLaunchCharacterCardCutover;

	const pendingGameWrites = {
		linkInserts,
		starterInserts,
		launchCharacterCardCutover: {
			enabled: grantLaunchCharacterCards,
			seal: sealLaunchCharacterCardCutover,
			inserts: launchCharacterCardInserts,
			includedPlayerIds: cohort.includedPlayerIds,
			expectedMarkerRows: launchCharacterCardMarkerRows.map(normalizeLaunchCharacterCardMarkerRow)
		},
		subscriptionUpserts: Array.from(subscriptionUpserts.values())
	};
	if (!dryRun && !options.deferGameWrites) {
		await applyNativeBackfillGameWrites(pendingGameWrites);
	}
	if (!dryRun && options.deferGameWrites) result.pendingGameWrites = pendingGameWrites;

	result.gameWrites = {
		webAccountLinksInserted: linkInserts.length,
		starterCardsInserted: starterInserts.length,
		launchCharacterCardsInserted: launchCharacterCardInserts.length,
		launchCharacterCardCompletionInserted: sealLaunchCharacterCardCutover ? 1 : 0,
		accountSubscriptionsUpserted: subscriptionUpserts.size
	};
	return result;
}

function nativeBackfillCohort(players, options = {}) {
	const asOfMs = normalizeNativeBackfillAsOfMs(options.asOfMs, true);
	const approvedExceptionPlayerIds = normalizePositiveIntegerList(options.approvedExceptionPlayerIds);
	const excludedExceptionPlayerIds = normalizePositiveIntegerList(options.excludedExceptionPlayerIds);
	const approvedSet = new Set(approvedExceptionPlayerIds);
	const excludedSet = new Set(excludedExceptionPlayerIds);
	const rows = players.map((player) => nativeBackfillCohortRow(player, asOfMs));
	const exceptions = rows.filter((row) => row.classification !== "eligible");
	const exceptionIds = new Set(exceptions.map((row) => row.playerId).filter((id) => id > 0));
	const unexpectedApprovedPlayerIds = approvedExceptionPlayerIds.filter((id) => !exceptionIds.has(id));
	const unexpectedExcludedPlayerIds = excludedExceptionPlayerIds.filter((id) => !exceptionIds.has(id));
	const conflictingDecisionPlayerIds = approvedExceptionPlayerIds.filter((id) => excludedSet.has(id));
	const approvedExceptions = exceptions.filter((row) => row.approvable && approvedSet.has(row.playerId) && !excludedSet.has(row.playerId));
	const explicitlyExcludedExceptions = exceptions.filter((row) => excludedSet.has(row.playerId) && !approvedSet.has(row.playerId));
	const decidedExceptionIds = new Set(approvedExceptions.concat(explicitlyExcludedExceptions).map((row) => row.playerId));
	const pendingExceptions = exceptions.filter((row) => !decidedExceptionIds.has(row.playerId));
	const eligiblePlayerIds = rows
		.filter((row) => row.classification === "eligible")
		.map((row) => row.playerId);
	const includedPlayerIds = eligiblePlayerIds.concat(approvedExceptions.map((row) => row.playerId)).sort((a, b) => a - b);
	const tokenRows = rows.map((row) => ({
		playerId: row.playerId,
		username: row.snapshotUsername,
		email: row.email,
		groupId: row.groupId,
		banned: row.banned,
		creationDate: row.creationDate,
		classification: row.classification,
		reasons: row.reasons
	}));
	const reviewToken = hashToken(JSON.stringify({
		policy: nativeBackfillCohortPolicy,
		grantMissingStarterCard: options.grantMissingStarterCard !== false,
		grantLaunchCharacterCards: options.grantLaunchCharacterCards !== false,
		asOfMs,
		launchCharacterCardCompletion: options.launchCharacterCardCompletion || null,
		launchCharacterCardMarkers: options.launchCharacterCardMarkers || [],
		webAccountLinks: options.webAccountLinks || [],
		approvedExceptionPlayerIds,
		excludedExceptionPlayerIds,
		players: tokenRows
	}));
	return {
		policy: nativeBackfillCohortPolicy,
		reviewToken,
		asOfMs,
		asOf: new Date(asOfMs).toISOString(),
		ready: pendingExceptions.length === 0
			&& unexpectedApprovedPlayerIds.length === 0
			&& unexpectedExcludedPlayerIds.length === 0
			&& conflictingDecisionPlayerIds.length === 0,
		totalPlayers: rows.length,
		eligiblePlayers: eligiblePlayerIds.length,
		excludedPlayers: exceptions.filter((row) => row.classification === "excluded").length,
		ambiguousPlayers: exceptions.filter((row) => row.classification === "ambiguous").length,
		approvedExceptions: approvedExceptions.length,
		explicitlyExcludedExceptions: explicitlyExcludedExceptions.length,
		pendingExceptions: pendingExceptions.length,
		eligiblePlayerIds,
		approvedExceptionPlayerIds,
		excludedExceptionPlayerIds,
		unexpectedApprovedPlayerIds,
		unexpectedExcludedPlayerIds,
		conflictingDecisionPlayerIds,
		includedPlayerIds,
		exceptions: exceptions.map((row) => ({
			...row,
			decision: approvedSet.has(row.playerId) && !excludedSet.has(row.playerId)
				? "include"
				: excludedSet.has(row.playerId) && !approvedSet.has(row.playerId)
					? "exclude"
					: "pending"
		}))
	};
}

function nativeBackfillCohortRow(player, asOfMs) {
	const playerId = Number(player.id);
	const username = cleanUsername(player.username || "");
	const normalizedName = normalizeUsername(username);
	const email = String(player.email || "").trim().toLowerCase();
	const groupId = Number(player.groupId);
	const banned = String(player.banned == null ? "0" : player.banned).trim();
	const creationDate = String(player.creationDate == null ? "0" : player.creationDate);
	const reasons = [];
	let approvable = true;
	if (!Number.isInteger(playerId) || playerId <= 0 || !normalizedName) {
		reasons.push("invalid_player_row");
		approvable = false;
	}
	if (!Number.isInteger(groupId)) {
		reasons.push("invalid_group_id");
		approvable = false;
	} else if (groupId !== 10) {
		reasons.push("staff_or_privileged_group");
	}
	if (nativeBackfillBanActive(banned, asOfMs)) reasons.push("banned_account");
	if (nativeBackfillLooksNonPlayer(normalizedName, email)) reasons.push("suspected_dev_or_test_account");
	const excluded = reasons.includes("staff_or_privileged_group") || reasons.includes("banned_account");
	return {
		playerId: Number.isInteger(playerId) ? playerId : 0,
		username,
		snapshotUsername: String(player.username == null ? "" : player.username),
		email,
		groupId: Number.isInteger(groupId) ? groupId : null,
		banned,
		creationDate,
		classification: reasons.length ? (excluded ? "excluded" : "ambiguous") : "eligible",
		reasons,
		approvable
	};
}

function nativeBackfillBanActive(value, asOfMs) {
	const text = String(value == null ? "" : value).trim().toLowerCase();
	if (["", "0", "false", "none", "null"].includes(text)) return false;
	const expiresAt = Number(text);
	return expiresAt === -1 || (Number.isFinite(expiresAt) && expiresAt > Number(asOfMs));
}

function normalizeNativeBackfillAsOfMs(value, allowDefault) {
	if (value == null || value === "") {
		if (!allowDefault) throw new HttpError(400, "native_backfill_as_of_required");
		return Date.now();
	}
	const asOfMs = Number(value);
	if (!Number.isInteger(asOfMs) || asOfMs <= 0) {
		throw new HttpError(400, "invalid_native_backfill_as_of");
	}
	return asOfMs;
}

function launchCharacterCardKey(playerId) {
	const id = Number(playerId);
	return Number.isInteger(id) && id > 0 ? `${launchCharacterCardCachePrefix}${id}` : "";
}

function normalizeLaunchCharacterCardMarkerRow(row) {
	return {
		dbid: Number(row.dbid),
		playerID: Number(row.playerID),
		type: Number(row.type),
		key: String(row.key || ""),
		value: String(row.value == null ? "" : row.value)
	};
}

function inspectLaunchCharacterCardMarkers(rows) {
	const normalizedRows = rows.map(normalizeLaunchCharacterCardMarkerRow);
	const anomalies = [];
	const counts = new Map();
	for (const row of normalizedRows) counts.set(row.key, Number(counts.get(row.key) || 0) + 1);
	for (const [key, count] of counts) {
		if (count > 1) anomalies.push({ reason: "duplicate_marker", key, count });
	}

	const campaignMarkers = [];
	const completionRows = [];
	for (const row of normalizedRows) {
		if (row.key === launchCharacterCardCompletionKey) {
			completionRows.push(row);
			if (row.playerID !== 0 || row.type !== 0 || row.value !== String(launchCharacterCardCutoverComplete)) {
				anomalies.push({ reason: "invalid_completion_marker", row });
			}
			continue;
		}
		const suffix = row.key.slice(launchCharacterCardCachePrefix.length);
		const playerId = Number(suffix);
		const canonicalKey = launchCharacterCardKey(playerId);
		const state = Number(row.value);
		if (row.playerID !== 0 || row.type !== 0 || !canonicalKey || row.key !== canonicalKey
			|| ![launchCharacterCardAvailable, launchCharacterCardClaimed].includes(state)
			|| row.value !== String(state)) {
			anomalies.push({ reason: "invalid_campaign_marker", row });
			continue;
		}
		campaignMarkers.push({ playerId, state, dbid: row.dbid, key: row.key });
	}
	const completion = {
		rowCount: completionRows.length,
		state: completionRows.length === 1 ? completionRows[0].value : null,
		sealed: completionRows.length === 1
			&& completionRows[0].playerID === 0
			&& completionRows[0].type === 0
			&& completionRows[0].value === String(launchCharacterCardCutoverComplete),
		rows: completionRows
	};
	return {
		ok: anomalies.length === 0,
		rowCount: normalizedRows.length,
		campaignMarkerCount: campaignMarkers.length,
		campaignMarkers,
		completion,
		anomalies
	};
}

function nativeBackfillLooksNonPlayer(normalizedName, email) {
	const compactName = String(normalizedName || "").replace(/\s+/g, "").toLowerCase();
	const suspiciousName = /^(?:wbtest|test(?:user|account|player|char)?\d*|dev(?:user|account|player|char)?\d*|qa(?:user|account|player|char)?\d*|bot\d*|dummy\d*|staff\d*|admin\d*|owner\d*|mod(?:erator)?\d*)$/.test(compactName);
	const suspiciousEmail = Boolean(email) && (email.endsWith(".invalid") || email.endsWith("@localhost"));
	return suspiciousName || suspiciousEmail;
}

function normalizePositiveIntegerList(value) {
	if (value == null || value === "") return [];
	if (!Array.isArray(value)) throw new HttpError(400, "invalid_player_id_list");
	const result = [];
	for (const entry of value) {
		const id = Number(entry);
		if (!Number.isInteger(id) || id <= 0) throw new HttpError(400, "invalid_player_id_list");
		if (!result.includes(id)) result.push(id);
	}
	return result.sort((a, b) => a - b);
}

async function applyNativeBackfillGameWrites(writes = {}) {
	const linkInserts = Array.isArray(writes.linkInserts) ? writes.linkInserts : [];
	const starterInserts = Array.isArray(writes.starterInserts) ? writes.starterInserts : [];
	const launchCutover = writes.launchCharacterCardCutover || {};
	const launchInserts = Array.isArray(launchCutover.inserts) ? launchCutover.inserts : [];
	const subscriptionUpserts = Array.isArray(writes.subscriptionUpserts) ? writes.subscriptionUpserts : [];
	if (!linkInserts.length && !starterInserts.length && !subscriptionUpserts.length && !launchCutover.enabled) return;
	const sql = ["BEGIN IMMEDIATE;"];
	if (launchCutover.enabled) {
		const expectedRows = Array.isArray(launchCutover.expectedMarkerRows)
			? launchCutover.expectedMarkerRows.map(normalizeLaunchCharacterCardMarkerRow)
			: [];
		const expectedPredicate = expectedRows.length
			? expectedRows.map((row) => `(dbid = ${row.dbid} AND playerID = ${row.playerID} AND type = ${row.type} AND key = ${sqlString(row.key)} AND value = ${sqlString(row.value)})`).join(" OR ")
			: "0";
		sql.push(
			"CREATE TEMP TABLE voidscape_launch_card_guard (ok INTEGER NOT NULL CHECK (ok = 1));",
			`INSERT INTO voidscape_launch_card_guard (ok)
			 SELECT CASE WHEN COUNT(*) = ${expectedRows.length}
			  AND COALESCE(SUM(CASE WHEN ${expectedPredicate} THEN 1 ELSE 0 END), 0) = ${expectedRows.length}
			 THEN 1 ELSE 0 END
			 FROM player_cache
			 WHERE substr(key, 1, ${launchCharacterCardCachePrefix.length}) = ${sqlString(launchCharacterCardCachePrefix)};`
		);
	}
	for (const link of linkInserts) {
		sql.push(`
			INSERT INTO player_cache (playerID, type, key, value)
			SELECT ${Number(link.playerId)}, 0, ${sqlString(openRscAccountIdCacheKey)}, ${sqlString(link.accountId)}
			WHERE NOT EXISTS (
				SELECT 1 FROM player_cache
				WHERE playerID = ${Number(link.playerId)} AND key = ${sqlString(openRscAccountIdCacheKey)}
			);`);
	}
	for (const starter of starterInserts) {
		sql.push(`
			INSERT INTO player_cache (playerID, type, key, value)
			SELECT 0, 0, ${sqlString(starter.key)}, ${sqlString(starterCardAvailable)}
			WHERE NOT EXISTS (
				SELECT 1 FROM player_cache WHERE playerID = 0 AND key = ${sqlString(starter.key)}
			);`);
	}
	for (const marker of launchInserts) {
		sql.push(`
			INSERT INTO player_cache (playerID, type, key, value)
			SELECT 0, 0, ${sqlString(marker.key)}, ${sqlString(launchCharacterCardAvailable)}
			WHERE NOT EXISTS (
				SELECT 1 FROM player_cache WHERE key = ${sqlString(marker.key)}
			);`);
	}
	if (launchCutover.enabled && launchCutover.seal) {
		sql.push(`
			INSERT INTO player_cache (playerID, type, key, value)
			SELECT 0, 0, ${sqlString(launchCharacterCardCompletionKey)}, ${sqlString(launchCharacterCardCutoverComplete)}
			WHERE NOT EXISTS (
				SELECT 1 FROM player_cache WHERE key = ${sqlString(launchCharacterCardCompletionKey)}
			);`);
		for (const playerId of launchCutover.includedPlayerIds || []) {
			const key = launchCharacterCardKey(playerId);
			sql.push(`INSERT INTO voidscape_launch_card_guard (ok)
				SELECT CASE WHEN COUNT(*) = 1 THEN 1 ELSE 0 END
				FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)}
				  AND value IN (${sqlString(launchCharacterCardAvailable)}, ${sqlString(launchCharacterCardClaimed)});`);
		}
		sql.push(`INSERT INTO voidscape_launch_card_guard (ok)
			SELECT CASE WHEN COUNT(*) = 1 THEN 1 ELSE 0 END
			FROM player_cache WHERE playerID = 0 AND type = 0
			  AND key = ${sqlString(launchCharacterCardCompletionKey)}
			  AND value = ${sqlString(launchCharacterCardCutoverComplete)};`);
	}
	for (const subscription of subscriptionUpserts) {
		sql.push(
			`DELETE FROM player_cache WHERE playerID = 0 AND key = ${sqlString(subscription.key)};`,
			`INSERT INTO player_cache (playerID, type, key, value) VALUES (0, 3, ${sqlString(subscription.key)}, ${sqlString(subscription.expiresAt)});`
		);
	}
	if (launchCutover.enabled) sql.push("DROP TABLE voidscape_launch_card_guard;");
	sql.push("COMMIT;");
	await sqliteWriteJsonStrict(sql.join("\n"), "native_backfill_launch_card_write_conflict");
}

function findCharacterByAccountAndName(store, accountId, normalizedName) {
	return store.characters.find((character) =>
		Number(character.accountId) === Number(accountId) &&
		normalizeUsername(character.normalizedName || character.name || "") === normalizedName
	) || null;
}

function nativeCharacterState(store, player, accountId, existingCharacter) {
	const username = cleanUsername(player.username || "");
	const playerId = Number(player.id);
	const male = Number(player.male) !== 0;
	const appearanceData = {
		male,
		hairColour: Number(player.haircolour || 0),
		topColour: Number(player.topcolour || 0),
		trouserColour: Number(player.trousercolour || 0),
		skinColour: Number(player.skincolour || 0),
		headSprite: Number(player.headsprite || 0),
		bodySprite: Number(player.bodysprite || 0)
	};
	return {
		id: existingCharacter ? existingCharacter.id : nextId(store, "character"),
		accountId,
		playerId,
		name: username,
		normalizedName: normalizeUsername(username),
		path: "OpenRSC save",
		image: `/openrsc/avatar/${playerId}.png`,
		combat: Number(player.combat || 3),
		total: String(player.skill_total || 27),
		quest: Number(player.quest_points || 0),
		kills: Number(player.kills || 0),
		status: "Void Island",
		title: "No title equipped",
		subscription: "Unsubscribed",
		lastLogin: Number(player.login_date || 0) > 0
			? new Date(Number(player.login_date) * 1000).toISOString()
			: "Never",
		appearance: `${male ? "Male" : "Female"}, hair ${appearanceData.hairColour}, top ${appearanceData.topColour}, trousers ${appearanceData.trouserColour}, skin ${appearanceData.skinColour}, head sprite ${appearanceData.headSprite}, body sprite ${appearanceData.bodySprite}`,
		gear: ["No wielded equipment saved"],
		appearanceData,
		equipment: [],
		linkStatus: "linked",
		source: existingCharacter && existingCharacter.source === "openrsc-sqlite-created"
			? existingCharacter.source
			: "openrsc-sqlite-native",
		linkedAt: now(),
		createdAt: existingCharacter && existingCharacter.createdAt || now(),
		updatedAt: now()
	};
}

function nativeCharacterNeedsUpdate(character, nextCharacter) {
	const fields = [
		"accountId", "playerId", "name", "normalizedName", "path", "combat", "total",
		"quest", "kills", "status", "title", "subscription", "lastLogin", "appearance",
		"linkStatus", "source"
	];
	for (const field of fields) {
		if (String(character[field] == null ? "" : character[field])
			!== String(nextCharacter[field] == null ? "" : nextCharacter[field])) {
			return true;
		}
	}
	return JSON.stringify(character.appearanceData || null) !== JSON.stringify(nextCharacter.appearanceData || null)
		|| JSON.stringify(character.gear || []) !== JSON.stringify(nextCharacter.gear || [])
		|| JSON.stringify(character.equipment || []) !== JSON.stringify(nextCharacter.equipment || []);
}

function syntheticNativeEmail(playerId) {
	return `native-player-${Number(playerId)}@native.voidscape.invalid`;
}

function ensureStarterEntitlement(store, accountId, source) {
	const existing = store.entitlements.find((entry) =>
		Number(entry.accountId) === Number(accountId) &&
		entry.type === starterFreeSubscriptionType &&
		(entry.status === "granted" || entry.status === "consumed")
	);
	if (existing) return existing;
	const entitlement = {
		id: nextId(store, "entitlement"),
		accountId,
		type: starterFreeSubscriptionType,
		status: "granted",
		source,
		codeHint: null,
		startsAt: null,
		expiresAt: null,
		createdAt: now(),
		consumedAt: null
	};
	store.entitlements.push(entitlement);
	return entitlement;
}

async function syncStarterCardToOpenRsc(account) {
	if (!openRscDbPath || !account) return null;
	const key = starterCardCacheKey(account.id);
	if (!key) return null;

	const current = await starterCardLedgerState(account);
	if (current.status === "claimed") {
		return null;
	}
	const previousRows = await openRscCacheRows(0, key);

	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)};
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (0, 0, ${sqlString(key)}, ${sqlString(starterCardAvailable)});
		COMMIT;
		SELECT value FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)} LIMIT 1;
	`);
	return () => restoreOpenRscCacheRows(0, key, previousRows);
}

async function starterCardLedgerState(account) {
	const state = {
		configured: Boolean(openRscDbPath),
		status: "none",
		value: null
	};
	if (!openRscDbPath || !account) {
		state.configured = false;
		return state;
	}
	const key = starterCardCacheKey(account.id);
	if (!key) return state;
	const current = await sqliteJson(`
		SELECT value
		FROM player_cache
		WHERE playerID = 0 AND key = ${sqlString(key)}
		ORDER BY dbid DESC
		LIMIT 1
	`);
	if (!current.length) return state;
	const value = Number(current[0].value);
	state.value = Number.isFinite(value) ? value : current[0].value;
	if (value === starterCardClaimed) {
		state.status = "claimed";
	} else if (value === starterCardAvailable) {
		state.status = "waiting";
	} else {
		state.status = "unknown";
	}
	return state;
}

async function revokeStarterCardFromOpenRsc(account) {
	if (!openRscDbPath || !account) return starterCardLedgerState(account);
	const key = starterCardCacheKey(account.id);
	if (!key) return starterCardLedgerState(account);
	const current = await starterCardLedgerState(account);
	if (current.status === "claimed") return current;
	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache
		WHERE playerID = 0
		  AND key = ${sqlString(key)}
		  AND value = ${sqlString(starterCardAvailable)};
		COMMIT;
		SELECT 1 AS ok;
	`);
	return starterCardLedgerState(account);
}

async function syncSignupCodeToOpenRsc(founder, transaction = null) {
	if (!openRscDbPath || !founder || !founder.signupCode) return false;
	return syncSignupCodeValueToOpenRsc(founder.signupCode, transaction);
}

async function syncSignupCodeValueToOpenRsc(code, transaction = null) {
	if (!openRscDbPath || !code) return false;
	const key = signupCodeCacheKey(code);
	if (!key) return false;

	const previousRows = await openRscCacheRows(0, key);
	const current = previousRows[previousRows.length - 1];
	if (current && Number(current.value) === signupCodeRedeemed) {
		return true;
	}

	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)};
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (0, 0, ${sqlString(key)}, ${sqlString(signupCodeAvailable)});
		COMMIT;
		SELECT value FROM player_cache WHERE playerID = 0 AND key = ${sqlString(key)} LIMIT 1;
	`);
	if (transaction) {
		transaction.onRollback(() => restoreOpenRscCacheRows(0, key, previousRows));
	}
	return true;
}

async function syncReferralRewardCodesToOpenRsc(founder, transaction = null) {
	if (!openRscDbPath || !founder || !Array.isArray(founder.referralRewardCodes)) return 0;
	let synced = 0;
	for (const reward of founder.referralRewardCodes) {
		if (!reward || reward.syncedAt) continue;
		if (await syncSignupCodeValueToOpenRsc(reward.code, transaction)) {
			reward.syncedAt = now();
			synced += 1;
		}
	}
	return synced;
}

async function openRscCacheRows(playerId, key) {
	return sqliteJson(`
		SELECT type, value
		FROM player_cache
		WHERE playerID = ${Number(playerId)} AND key = ${sqlString(key)}
		ORDER BY dbid
	`);
}

async function restoreOpenRscCacheRows(playerId, key, rows) {
	const safePlayerId = Number(playerId);
	const restoreRows = rows.map((row) => `
		INSERT INTO player_cache (playerID, type, key, value)
			VALUES (${safePlayerId}, ${Number.isInteger(Number(row.type)) ? Number(row.type) : 0}, ${sqlString(key)}, ${sqlString(row.value)});`
	).join("\n");
	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache WHERE playerID = ${safePlayerId} AND key = ${sqlString(key)};
		${restoreRows}
		COMMIT;
		SELECT 1 AS ok;
	`);
}

async function signupCodeGameValues() {
	if (!openRscDbPath) return new Map();
	const rows = await sqliteJson(`
		SELECT key, value
		FROM player_cache
		WHERE playerID = 0 AND key LIKE '${signupCodeCachePrefix}%'
	`);
	return new Map(rows.map((row) => [row.key, Number(row.value)]));
}

function signupCodeStatus(founder, gameValues) {
	if (!founder.signupCode) return "no_code";
	if (!openRscDbPath) return founder.signupCodeSyncedAt ? "issued" : "not_synced";
	const value = gameValues.get(signupCodeCacheKey(founder.signupCode));
	if (value === undefined) return "not_synced";
	return value === signupCodeRedeemed ? "redeemed" : "issued";
}

function csvCell(value) {
	const text = String(value == null ? "" : value);
	return /[",\n]/.test(text) ? `"${text.replace(/"/g, "\"\"")}"` : text;
}

function escapeHtml(value) {
	return String(value == null ? "" : value)
		.replace(/&/g, "&amp;")
		.replace(/</g, "&lt;")
		.replace(/>/g, "&gt;")
		.replace(/"/g, "&quot;");
}

function jsonScript(value) {
	return JSON.stringify(value).replace(/[<>&]/g, (character) => ({
		"<": "\\u003c",
		">": "\\u003e",
		"&": "\\u0026"
	}[character]));
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

async function unlinkOpenRscPlayerFromAccount(playerId, accountId) {
	if (!openRscDbPath || !playerId || !accountId) return;
	await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		DELETE FROM player_cache
			WHERE playerID = ${Number(playerId)}
			  AND key = ${sqlString(openRscAccountIdCacheKey)}
			  AND value = ${sqlString(accountId)};
		COMMIT;
		SELECT 1 AS ok;
	`);
}

async function deleteOpenRscPlayer(character, accountId, options = {}) {
	const playerId = Number(character && character.playerId || 0);
	const normalizedName = normalizeUsername(character && character.name || "");
	const expectedCreationDate = Number(options.expectedCreationDate || 0);
	if (!playerId || !normalizedName) {
		throw new HttpError(400, "invalid_character_id");
	}

	const playerRows = await sqliteJson(`
		SELECT p.id, p.online, p.creation_date AS creationDate, pc.value AS webAccountId
		FROM players p
		LEFT JOIN player_cache pc
		  ON pc.playerID = p.id
		 AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
		WHERE p.id = ${playerId}
		  AND lower(p.username) = ${sqlString(normalizedName)}
		ORDER BY pc.dbid DESC
		LIMIT 1
	`);
	if (!playerRows.length) return false;
	if (!playerRows[0].webAccountId) {
		throw new HttpError(409, "character_link_missing");
	}
	if (Number(playerRows[0].webAccountId) !== Number(accountId)) {
		throw new HttpError(409, "character_link_mismatch");
	}
	if (expectedCreationDate > 0 && Number(playerRows[0].creationDate || 0) !== expectedCreationDate) {
		throw new HttpError(409, "character_delete_conflict");
	}
	if (Number(playerRows[0].online || 0) !== 0) {
		throw new HttpError(409, "character_online");
	}
	const creationDateGuard = expectedCreationDate > 0
		? " AND p.creation_date = " + expectedCreationDate
		: "";

	const tables = await openRscExistingTables([
		"auctions",
		"auction_sales",
		"bank",
		"bankpresets",
		"bestiaryloot",
		"capped_experience",
		"curstats",
		"droplogs",
		"equipped",
		"experience",
		"expired_auctions",
		"former_names",
		"friends",
		"ignores",
		"invitems",
		"ironman",
		"itemstatuses",
		"logins",
		"maxstats",
		"npckills",
		"player_cache",
		"player_change_recovery",
		"player_contact_details",
		"player_recovery",
		"player_security_changes",
		"quests",
		"recovery_attempts",
		"voidarena_ranked_matches",
		"voidarena_ranked_stats"
	]);
	const sql = [
		"BEGIN IMMEDIATE;",
		"CREATE TEMP TABLE portal_delete_guard (id INTEGER PRIMARY KEY);",
		`INSERT INTO portal_delete_guard
			SELECT p.id
			FROM players p
			JOIN player_cache pc
			  ON pc.playerID = p.id
			 AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
			 AND pc.value = ${sqlString(accountId)}
			WHERE p.id = ${playerId}
			  AND lower(p.username) = ${sqlString(normalizedName)}
			  AND p.online = 0${creationDateGuard};`,
		"CREATE TEMP TABLE portal_deleted_item_ids (itemID INTEGER PRIMARY KEY);"
	];
	if (tables.has("invitems")) {
		sql.push("INSERT OR IGNORE INTO portal_deleted_item_ids SELECT itemID FROM invitems WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("bank")) {
		sql.push("INSERT OR IGNORE INTO portal_deleted_item_ids SELECT itemID FROM bank WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("equipped")) {
		sql.push("INSERT OR IGNORE INTO portal_deleted_item_ids SELECT itemID FROM equipped WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("auctions")) {
		sql.push("INSERT OR IGNORE INTO portal_deleted_item_ids SELECT itemID FROM auctions WHERE seller IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("expired_auctions")) {
		sql.push("INSERT OR IGNORE INTO portal_deleted_item_ids SELECT item_id FROM expired_auctions WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("bank")) {
		sql.push("DELETE FROM bank WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("invitems")) {
		sql.push("DELETE FROM invitems WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("itemstatuses")) {
		sql.push("DELETE FROM itemstatuses WHERE itemID IN (SELECT itemID FROM portal_deleted_item_ids);");
	}
	if (tables.has("equipped")) {
		sql.push("DELETE FROM equipped WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("auctions")) {
		sql.push("DELETE FROM auctions WHERE seller IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("expired_auctions")) {
		sql.push("DELETE FROM expired_auctions WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("auction_sales")) {
		sql.push("DELETE FROM auction_sales WHERE seller IN (SELECT id FROM portal_delete_guard) OR buyer IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("bankpresets")) {
		sql.push("DELETE FROM bankpresets WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("droplogs")) {
		sql.push("DELETE FROM droplogs WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("former_names")) {
		sql.push("DELETE FROM former_names WHERE playerId IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("voidarena_ranked_matches")) {
		sql.push("DELETE FROM voidarena_ranked_matches WHERE winnerID IN (SELECT id FROM portal_delete_guard) OR loserID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("voidarena_ranked_stats")) {
		sql.push("DELETE FROM voidarena_ranked_stats WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	}
	if (tables.has("bestiaryloot")) sql.push("DELETE FROM bestiaryloot WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("capped_experience")) sql.push("DELETE FROM capped_experience WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("curstats")) sql.push("DELETE FROM curstats WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("experience")) sql.push("DELETE FROM experience WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("friends")) sql.push("DELETE FROM friends WHERE playerID IN (SELECT id FROM portal_delete_guard) OR friend IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("ignores")) sql.push("DELETE FROM ignores WHERE playerID IN (SELECT id FROM portal_delete_guard) OR \"ignore\" IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("ironman")) sql.push("DELETE FROM ironman WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("logins")) sql.push("DELETE FROM logins WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("maxstats")) sql.push("DELETE FROM maxstats WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("npckills")) sql.push("DELETE FROM npckills WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("player_cache")) sql.push("DELETE FROM player_cache WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("player_change_recovery")) sql.push("DELETE FROM player_change_recovery WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("player_contact_details")) sql.push("DELETE FROM player_contact_details WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("player_recovery")) sql.push("DELETE FROM player_recovery WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("player_security_changes")) sql.push("DELETE FROM player_security_changes WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("quests")) sql.push("DELETE FROM quests WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	if (tables.has("recovery_attempts")) sql.push("DELETE FROM recovery_attempts WHERE playerID IN (SELECT id FROM portal_delete_guard);");
	sql.push(
		`DELETE FROM players WHERE id IN (SELECT id FROM portal_delete_guard) AND lower(username) = ${sqlString(normalizedName)};`,
		"SELECT changes() AS playersDeleted;",
		"DROP TABLE portal_delete_guard;",
		"DROP TABLE portal_deleted_item_ids;",
		"COMMIT;"
	);
	const rows = await sqliteWriteJson(sql.join("\n"));
	if (Number(rows[0] && rows[0].playersDeleted || 0) !== 1) {
		throw new HttpError(409, "character_delete_conflict");
	}
	return true;
}

async function updateOpenRscPlayerPassword(character, accountId, password, ip) {
	if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
	const playerId = Number(character && character.playerId);
	const normalizedName = normalizeUsername(character && character.name || "");
	if (!playerId || !normalizedName) throw new HttpError(409, "character_link_missing");
	const preflight = await sqliteJson(`
		SELECT p.id, p.username, p.pass, COALESCE(p.salt, '') AS salt, p.online,
		       (
			SELECT pc.value
			FROM player_cache pc
			WHERE pc.playerID = p.id
			  AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
			ORDER BY pc.dbid DESC
			LIMIT 1
		       ) AS webAccountId
		FROM players p
		WHERE p.id = ${playerId}
		  AND lower(p.username) = ${sqlString(normalizedName)}
		LIMIT 1
	`);
	const player = preflight[0];
	if (!player || String(player.webAccountId || "") !== String(accountId)) {
		throw new HttpError(409, "character_link_mismatch");
	}
	if (Number(player.online || 0) !== 0) throw new HttpError(409, "character_online");
	const newHash = await canonicalGamePasswordHash(password, String(player.salt || ""));
	const changedAt = now();
	const changedAtSeconds = Math.floor(Date.parse(changedAt) / 1000);
	const rows = await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		CREATE TEMP TABLE portal_password_guard (playerID INTEGER PRIMARY KEY);
		INSERT INTO portal_password_guard (playerID)
		SELECT p.id
		FROM players p
		WHERE p.id = ${playerId}
		  AND lower(p.username) = ${sqlString(normalizedName)}
		  AND p.online = 0
		  AND p.pass = ${sqlString(String(player.pass || ""))}
		  AND COALESCE(p.salt, '') = ${sqlString(String(player.salt || ""))}
		  AND CAST((
			SELECT pc.value
			FROM player_cache pc
			WHERE pc.playerID = p.id
			  AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
			ORDER BY pc.dbid DESC
			LIMIT 1
		  ) AS TEXT) = ${sqlString(String(accountId))};
		UPDATE players
		SET pass = ${sqlString(newHash)}
		WHERE id IN (SELECT playerID FROM portal_password_guard);
		SELECT changes() AS playersUpdated;
		INSERT INTO player_security_changes (playerID, eventAlias, date, ip, message)
		SELECT playerID, 'pass_change', ${changedAtSeconds}, ${sqlString(ip || "0.0.0.0")}, 'Portal account game-password reset'
		FROM portal_password_guard;
		DROP TABLE portal_password_guard;
		COMMIT;
	`);
	if (Number(rows[0] && rows[0].playersUpdated || 0) !== 1) {
		throw new HttpError(409, "character_link_mismatch");
	}
	return changedAt;
}

async function createOpenRscPlayer({ accountId, username, email, password, ip }) {
	const passwordSalt = randomBytes(15).toString("base64url").slice(0, 20);
	const passwordHash = await canonicalGamePasswordHash(password, passwordSalt);
	const createdAt = Math.floor(Date.now() / 1000);
	const rows = await sqliteWriteJson(`
		BEGIN IMMEDIATE;
		INSERT INTO players (
			username, email, pass, salt, creation_date, creation_ip, group_id, cameraauto
		) VALUES (
			${sqlString(username)},
			${sqlString(email || "")},
			${sqlString(passwordHash)},
			${sqlString(passwordSalt)},
			${createdAt},
			${sqlString(ip || "127.0.0.1")},
			10,
			0
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
	return { playerId, creationDate: createdAt };
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
		sourceLabel: "Game SQLite"
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

function requireTebexCheckoutConfigured() {
	if (!tebexCheckoutConfigured()) {
		throw new HttpError(501, "payments_not_configured");
	}
}

function requireTebexWebhookConfigured() {
	if (!tebexWebhookConfigured()) {
		throw new HttpError(503, "payments_not_configured");
	}
}

async function reserveTebexCheckoutAttempt(request) {
	return updateStore((store) => {
		const { account } = requireSession(request, store);
		const since = Date.now() - checkoutRateWindowMs;
		const ip = clientIp(request);
		if (countAbuseSignals(store, "tebex_checkout_account", String(account.id), since) >= checkoutAccountRateLimit
			|| countAbuseSignals(store, "tebex_checkout_ip", ip, since) >= checkoutIpRateLimit) {
			throw new HttpError(429, "checkout_rate_limited");
		}
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "tebex_checkout_account",
			signalValue: String(account.id),
			bucket: dailyBucket(),
			metadata: { provider: "tebex" }
		});
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "tebex_checkout_ip",
			signalValue: ip,
			bucket: dailyBucket(),
			metadata: { provider: "tebex" }
		});
		return account;
	});
}

async function createTebexSubscriptionCardCheckout(account, request) {
	requireTebexCheckoutConfigured();
	if (!account || !Number.isInteger(Number(account.id)) || Number(account.id) <= 0) {
		throw new HttpError(401, "invalid_account");
	}

	if (!(await commerceSchemaAvailable())) throw new HttpError(503, "commerce_schema_missing");
	await verifyTebexSubscriptionCardPackage();
	const ipAddress = tebexCheckoutIpAddress(request);
	const intentId = randomBytes(32).toString("base64url");
	const createdAtMs = Date.now();
	// Tebex can retry delivery well after payment. The signed payment timestamp,
	// rather than webhook arrival time, is compared with this 24-hour window.
	const expiresAtMs = createdAtMs + 24 * 60 * 60 * 1000;
	await sqliteCommerceWrite(`
		BEGIN IMMEDIATE;
		INSERT INTO portal_commerce_checkout_intents (
			intent_id, account_id, provider, package_id, expected_currency,
			expected_amount_minor, status, created_at_ms, expires_at_ms, updated_at_ms
		) VALUES (
			${sqlString(intentId)}, ${Number(account.id)}, 'tebex', ${sqlString(tebexSubscriptionCardPackageId)},
			${sqlString(tebexExpectedCurrency)}, ${tebexExpectedPriceMinor}, 'created',
			${createdAtMs}, ${expiresAtMs}, ${createdAtMs}
		);
		COMMIT;
		SELECT intent_id, status FROM portal_commerce_checkout_intents
		WHERE intent_id = ${sqlString(intentId)};
	`);

	try {
		const returnBase = `${publicSiteOrigin}/portal`;
		const createdPayload = await tebexRequest(
			`/accounts/${encodeURIComponent(tebexPublicToken)}/baskets`,
			{
				method: "POST",
				body: {
					complete_url: `${returnBase}?checkout=success#subscription`,
					cancel_url: `${returnBase}?checkout=cancelled#subscription`,
					complete_auto_redirect: true,
					ip_address: ipAddress,
					custom: { voidscape_intent: intentId }
				}
			}
		);
		const createdBasket = tebexDataObject(createdPayload);
		const basketId = validateCreatedTebexBasket(createdBasket, intentId);
		await sqliteCommerceWrite(`
			BEGIN IMMEDIATE;
			UPDATE portal_commerce_checkout_intents
			SET basket_id = ${sqlString(basketId)}, updated_at_ms = ${Date.now()}
			WHERE intent_id = ${sqlString(intentId)} AND status = 'created' AND basket_id IS NULL;
			COMMIT;
			SELECT intent_id, basket_id, status FROM portal_commerce_checkout_intents
			WHERE intent_id = ${sqlString(intentId)};
		`);

		const basketPayload = await tebexRequest(`/baskets/${encodeURIComponent(basketId)}/packages`, {
			method: "POST",
			body: {
				package_id: Number(tebexSubscriptionCardPackageId),
				quantity: 1,
				custom: {
					voidscape_intent: intentId,
					voidscape_basket: basketId
				}
			}
		});
		const basket = tebexDataObject(basketPayload);
		const checkoutUrl = validateCompletedTebexBasket(basket, basketId, intentId);
		const rows = await sqliteCommerceWrite(`
			BEGIN IMMEDIATE;
			UPDATE portal_commerce_checkout_intents
			SET status = 'basket_created', last_error_code = NULL, updated_at_ms = ${Date.now()}
			WHERE intent_id = ${sqlString(intentId)}
			  AND basket_id = ${sqlString(basketId)}
			  AND status = 'created';
			COMMIT;
			SELECT intent_id, basket_id, status FROM portal_commerce_checkout_intents
			WHERE intent_id = ${sqlString(intentId)};
		`);
		if (!rows.length || rows[0].status !== "basket_created") {
			throw new HttpError(409, "checkout_intent_state_conflict");
		}
		return {
			status: "checkout_ready",
			links: { checkout: checkoutUrl }
		};
	} catch (error) {
		await markCommerceIntentFailed(intentId, commerceErrorCode(error));
		if (error instanceof HttpError) throw error;
		throw new HttpError(502, "tebex_checkout_failed");
	}
}

function tebexCheckoutIpAddress(request) {
	const raw = clientIp(request);
	const ipv4 = String(raw).startsWith("::ffff:") ? String(raw).slice("::ffff:".length) : String(raw);
	if (ipv4ToInt(ipv4) === null) {
		throw new HttpError(400, "tebex_ipv4_required");
	}
	return ipv4;
}

async function verifyTebexSubscriptionCardPackage() {
	const payload = await tebexRequest(
		`/accounts/${encodeURIComponent(tebexPublicToken)}/packages/${encodeURIComponent(tebexSubscriptionCardPackageId)}`,
		{ method: "GET" }
	);
	const packageRow = tebexDataObject(payload);
	if (String(packageRow.id || "") !== tebexSubscriptionCardPackageId) {
		throw new HttpError(502, "tebex_package_mismatch");
	}
	if (String(packageRow.type || "").toLowerCase() !== "single") {
		throw new HttpError(502, "tebex_package_not_single_payment");
	}
	if (packageRow.disable_quantity !== true || packageRow.disable_gifting !== true) {
		throw new HttpError(502, "tebex_package_checkout_controls_unlocked");
	}
	const currency = String(packageRow.currency || "").toUpperCase();
	const amountMinor = moneyToMinor(
		packageRow.base_price,
		currency
	);
	if (currency !== tebexExpectedCurrency || amountMinor !== tebexExpectedPriceMinor) {
		throw new HttpError(502, "tebex_package_price_mismatch");
	}
}

async function tebexRequest(pathname, options = {}) {
	const method = options.method || "GET";
	const headers = {
		accept: "application/json",
		authorization: `Basic ${Buffer.from(`${tebexPublicToken}:${tebexPrivateKey}`, "utf8").toString("base64")}`
	};
	const requestOptions = { method, headers };
	if (options.body !== undefined) {
		headers["content-type"] = "application/json";
		requestOptions.body = JSON.stringify(options.body);
	}
	const controller = new AbortController();
	const timeout = setTimeout(() => controller.abort(), 10000);
	requestOptions.signal = controller.signal;
	let response;
	try {
		response = await fetch(`${tebexApiBase}${pathname}`, requestOptions);
	} catch (error) {
		throw new HttpError(502, error && error.name === "AbortError" ? "tebex_timeout" : "tebex_unavailable");
	} finally {
		clearTimeout(timeout);
	}
	let payload;
	try {
		payload = JSON.parse(await response.text());
	} catch (error) {
		throw new HttpError(502, "tebex_invalid_response");
	}
	if (!response.ok) {
		throw new HttpError(502, "tebex_request_rejected");
	}
	return payload;
}

function tebexDataObject(payload) {
	let value = payload && Object.prototype.hasOwnProperty.call(payload, "data") ? payload.data : payload;
	if (Array.isArray(value)) value = value[0];
	if (!value || typeof value !== "object" || Array.isArray(value)) {
		throw new HttpError(502, "tebex_invalid_response");
	}
	return value;
}

function validateCreatedTebexBasket(basket, intentId) {
	const basketId = String(basket.ident || "").trim();
	if (!basketId || basketId.length > 128) {
		throw new HttpError(502, "tebex_basket_missing");
	}
	validateTebexBasketCustom(basket.custom, intentId);
	if (Array.isArray(basket.packages) && basket.packages.length !== 0) {
		throw new HttpError(502, "tebex_basket_not_empty");
	}
	return basketId;
}

function validateCompletedTebexBasket(basket, basketId, intentId) {
	if (String(basket.ident || "") !== basketId) {
		throw new HttpError(502, "tebex_basket_mismatch");
	}
	validateTebexBasketCustom(basket.custom, intentId);
	if (!Array.isArray(basket.packages) || basket.packages.length !== 1) {
		throw new HttpError(502, "tebex_basket_package_count_mismatch");
	}
	const packageRow = basket.packages[0] || {};
	const packageId = packageRow.id ?? packageRow.package_id ?? (packageRow.package && packageRow.package.id);
	if (packageId != null && String(packageId) !== tebexSubscriptionCardPackageId) {
		throw new HttpError(502, "tebex_basket_package_mismatch");
	}
	const quantityValue = packageRow.in_basket && packageRow.in_basket.quantity != null
		? packageRow.in_basket.quantity
		: packageRow.qty == null ? packageRow.quantity : packageRow.qty;
	const quantity = Number(quantityValue);
	if (quantity !== 1) {
		throw new HttpError(502, "tebex_basket_quantity_mismatch");
	}
	if (packageRow.type != null && String(packageRow.type).toLowerCase() !== "single") {
		throw new HttpError(502, "tebex_basket_not_single_payment");
	}
	const currency = String(basket.currency || "").toUpperCase();
	if (currency !== tebexExpectedCurrency
		|| moneyToMinor(basket.base_price, currency) !== tebexExpectedPriceMinor) {
		throw new HttpError(502, "tebex_basket_price_mismatch");
	}
	const checkoutUrl = String(basket.links && basket.links.checkout || "").trim();
	let parsedCheckout;
	try {
		parsedCheckout = new URL(checkoutUrl);
	} catch (error) {
		throw new HttpError(502, "tebex_checkout_link_missing");
	}
	if (tebexTestApiBase) {
		if (!["http:", "https:"].includes(parsedCheckout.protocol)) {
			throw new HttpError(502, "tebex_checkout_link_invalid");
		}
	} else if (parsedCheckout.protocol !== "https:" || parsedCheckout.hostname !== "checkout.tebex.io") {
		throw new HttpError(502, "tebex_checkout_link_invalid");
	}
	return parsedCheckout.toString();
}

function validateTebexBasketCustom(custom, intentId) {
	if (!custom || typeof custom !== "object" || Array.isArray(custom)) {
		throw new HttpError(502, "tebex_basket_custom_missing");
	}
	const keys = Object.keys(custom);
	if (keys.length !== 1 || keys[0] !== "voidscape_intent" || custom.voidscape_intent !== intentId) {
		throw new HttpError(502, "tebex_basket_custom_mismatch");
	}
}

function moneyToMinor(value, currency) {
	const digits = currencyMinorDigits(currency);
	const text = String(value == null ? "" : value).trim();
	const match = /^(\d+)(?:\.(\d+))?$/.exec(text);
	if (!match || (match[2] || "").length > digits) {
		throw new HttpError(422, "invalid_payment_amount");
	}
	const fraction = (match[2] || "").padEnd(digits, "0");
	const scale = 10 ** digits;
	const result = Number(match[1]) * scale + Number(fraction || 0);
	if (!Number.isSafeInteger(result)) throw new HttpError(422, "invalid_payment_amount");
	return result;
}

function currencyMinorDigits(currency) {
	try {
		return new Intl.NumberFormat("en", { style: "currency", currency }).resolvedOptions().maximumFractionDigits;
	} catch (error) {
		throw new HttpError(422, "invalid_payment_currency");
	}
}

async function markCommerceIntentFailed(intentId, errorCode) {
	try {
		await sqliteCommerceWrite(`
			BEGIN IMMEDIATE;
			UPDATE portal_commerce_checkout_intents
			SET status = 'failed', last_error_code = ${sqlString(safeCommerceCode(errorCode))}, updated_at_ms = ${Date.now()}
			WHERE intent_id = ${sqlString(intentId)} AND status <> 'completed';
			COMMIT;
			SELECT intent_id FROM portal_commerce_checkout_intents WHERE intent_id = ${sqlString(intentId)};
		`);
	} catch (error) {
		console.error("commerce_intent_failure_record_failed");
	}
}

function commerceErrorCode(error) {
	return safeCommerceCode(error && error.message || "tebex_checkout_failed");
}

function safeCommerceCode(value) {
	const normalized = String(value || "commerce_error").toLowerCase().replace(/[^a-z0-9_]+/g, "_").slice(0, 64);
	return normalized || "commerce_error";
}

async function sqliteCommerceRead(query) {
	return sqliteCommerceExec(["-readonly", "-cmd", "PRAGMA foreign_keys=ON;", "-json", openRscDbPath, query]);
}

async function sqliteCommerceWrite(query) {
	return sqliteCommerceExec(["-cmd", ".timeout 5000", "-cmd", "PRAGMA foreign_keys=ON;", "-json", openRscDbPath, query]);
}

async function sqliteCommerceExec(args) {
	if (!openRscDbPath) throw new HttpError(503, "openrsc_db_not_configured");
	try {
		const { stdout } = await execFile("sqlite3", args, { maxBuffer: 1024 * 1024 });
		return stdout.trim() ? JSON.parse(stdout) : [];
	} catch (error) {
		if (error.code === "ENOENT") throw new HttpError(503, "sqlite3_not_available");
		if (error.stderr && error.stderr.includes("no such table")) {
			throw new HttpError(503, "commerce_schema_missing");
		}
		if (error.stderr && (error.stderr.includes("unable to open database") || error.stderr.includes("database is locked"))) {
			throw new HttpError(503, "openrsc_db_unavailable");
		}
		if (error.stderr && (error.stderr.includes("constraint") || error.stderr.includes("UNIQUE"))) {
			throw new HttpError(409, "commerce_ledger_conflict");
		}
		throw error;
	}
}

async function handleTebexWebhook(request, response) {
	requireTebexWebhookConfigured();
	const rawBody = await readRawRequestBody(request);
	verifyTebexWebhookSignature(rawBody, request.headers["x-signature"]);
	const payloadHash = createHash("sha256").update(rawBody).digest("hex");
	let payload;
	try {
		payload = JSON.parse(rawBody.toString("utf8"));
	} catch (error) {
		throw new HttpError(400, "invalid_json");
	}
	if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
		throw new HttpError(400, "invalid_json");
	}
	const webhookId = webhookIdentifier(payload.id);
	const eventType = webhookEventType(payload.type);
	if (eventType === "validation.webhook") {
		if (!webhookId) throw new HttpError(422, "webhook_id_missing");
		json(response, 200, { id: webhookId });
		return;
	}

	requireTebexCheckoutConfigured();
	if (!webhookId) throw new HttpError(422, "webhook_id_missing");
	if (!eventType) throw new HttpError(422, "webhook_type_missing");
	const existing = await commerceWebhookReplayState(webhookId, payloadHash);
	if (existing.duplicate) {
		json(response, 200, { ok: true, duplicate: true });
		return;
	}

	const supportedPaymentEvents = new Set([
		"payment.completed",
		"payment.refunded",
		"payment.dispute.opened",
		"payment.dispute.won",
		"payment.dispute.lost",
		"payment.dispute.closed"
	]);
	if (!supportedPaymentEvents.has(eventType)) {
		await recordIgnoredCommerceWebhook({ webhookId, eventType, payloadHash });
		json(response, 200, { ok: true, ignored: true });
		return;
	}

	let facts;
	try {
		facts = extractTebexPaymentFacts(payload.subject);
		const intent = await verifiedCommerceIntent(facts);
		if (tebexTestWebhookFailOnce && !tebexWebhookFailureInjected && !existing.row) {
			tebexWebhookFailureInjected = true;
			await recordFailedCommerceWebhook({
				webhookId,
				eventType,
				payloadHash,
				transactionId: facts.transactionId,
				errorCode: "injected_webhook_failure"
			});
			throw new HttpError(503, "injected_webhook_failure");
		}
		if (eventType === "payment.completed") {
			await processCompletedCommerceWebhook({ webhookId, eventType, payloadHash, facts, intent });
		} else {
			await processReversalCommerceWebhook({ webhookId, eventType, payloadHash, facts, intent });
		}
	} catch (error) {
		if (!(error instanceof HttpError && error.message === "injected_webhook_failure")) {
			await recordFailedCommerceWebhook({
				webhookId,
				eventType,
				payloadHash,
				transactionId: facts && facts.transactionId || webhookTransactionId(payload.subject),
				errorCode: commerceErrorCode(error)
			});
		}
		if (error instanceof HttpError) throw error;
		throw new HttpError(503, "webhook_processing_failed");
	}
	json(response, 200, { ok: true, duplicate: false });
}

async function readRawRequestBody(request) {
	const chunks = [];
	let size = 0;
	for await (const chunk of request) {
		size += chunk.length;
		if (size > 1024 * 1024) throw new HttpError(413, "payload_too_large");
		chunks.push(chunk);
	}
	return Buffer.concat(chunks);
}

function verifyTebexWebhookSignature(rawBody, signatureHeader) {
	const bodyHash = createHash("sha256").update(rawBody).digest("hex");
	const expected = createHmac("sha256", tebexWebhookSecret).update(bodyHash, "utf8").digest("hex");
	const provided = String(signatureHeader || "").trim().toLowerCase();
	if (!/^[a-f0-9]{64}$/.test(provided) || !constantTimeMatches(provided, expected)) {
		throw new HttpError(401, "invalid_webhook_signature");
	}
}

function webhookIdentifier(value) {
	const result = String(value || "").trim();
	return result && result.length <= 128 ? result : "";
}

function webhookEventType(value) {
	const result = String(value || "").trim().toLowerCase();
	return result && result.length <= 64 ? result : "";
}

function webhookTransactionId(subject) {
	const value = subject && (subject.transaction_id ?? (subject.transaction && subject.transaction.id));
	const result = String(value || "").trim();
	return result && result.length <= 128 ? result : "";
}

function extractTebexPaymentFacts(subject) {
	if (!subject || typeof subject !== "object" || Array.isArray(subject)) {
		throw new HttpError(422, "webhook_subject_missing");
	}
	const transactionId = webhookTransactionId(subject);
	if (!transactionId) throw new HttpError(422, "payment_transaction_missing");
	const paidAtMs = Date.parse(String(subject.created_at || ""));
	if (!Number.isFinite(paidAtMs)) throw new HttpError(422, "payment_timestamp_missing");
	if (String(subject.payment_sequence || "").toLowerCase() !== "oneoff") {
		throw new HttpError(422, "payment_not_oneoff");
	}
	if (!Array.isArray(subject.products) || subject.products.length !== 1) {
		throw new HttpError(422, "payment_product_count_mismatch");
	}
	const product = subject.products[0];
	if (!product || typeof product !== "object" || Array.isArray(product)) {
		throw new HttpError(422, "payment_product_missing");
	}
	const packageId = String(product.id ?? product.package_id ?? (product.package && product.package.id) ?? "").trim();
	if (!packageId || packageId.length > 64) throw new HttpError(422, "payment_package_missing");
	const quantity = Number(product.quantity == null ? product.qty : product.quantity);
	if (quantity !== 1) throw new HttpError(422, "payment_quantity_mismatch");
	const transactionPrice = subject.price_paid || subject.price;
	if (!transactionPrice || typeof transactionPrice !== "object" || Array.isArray(transactionPrice)) {
		throw new HttpError(422, "payment_price_missing");
	}
	const currency = String(transactionPrice.currency || "").toUpperCase();
	const transactionPaidAmountMinor = moneyToMinor(transactionPrice.amount, currency);
	if (!product.base_price || typeof product.base_price !== "object" || Array.isArray(product.base_price)) {
		throw new HttpError(422, "payment_product_base_price_missing");
	}
	const baseCurrency = String(product.base_price.currency || "").toUpperCase();
	const expectedAmountMinor = moneyToMinor(product.base_price.amount, baseCurrency);
	if (!product.paid_price || typeof product.paid_price !== "object" || Array.isArray(product.paid_price)) {
		throw new HttpError(422, "payment_product_paid_price_missing");
	}
	const paidCurrency = String(product.paid_price.currency || "").toUpperCase();
	const productPaidAmountMinor = moneyToMinor(product.paid_price.amount, paidCurrency);
	if (currency !== baseCurrency || currency !== paidCurrency) {
		throw new HttpError(422, "payment_product_currency_mismatch");
	}
	const taxAndAdjustmentsMinor = transactionPaidAmountMinor - productPaidAmountMinor;
	// The signed transaction total may include jurisdictional tax, while the
	// signed product paid price may include a creator-configured discount. The
	// allowlisted package's product base price is the stable configured amount;
	// validate all three money objects/currencies but do not equate their totals.
	const intentId = extractTebexIntent(subject, product);
	const basketCandidates = [
		subject.basket && subject.basket.ident,
		subject.basket_ident,
		subject.basket_id,
		product.custom && product.custom.voidscape_basket
	].map((value) => String(value || "").trim()).filter(Boolean);
	const basketIds = Array.from(new Set(basketCandidates));
	if (basketIds.length !== 1 || basketIds.some((value) => value.length > 128)) {
		throw new HttpError(422, "payment_basket_mismatch");
	}
	return {
		transactionId,
		intentId,
		basketId: basketIds[0] || "",
		packageId,
		quantity,
		paymentSequence: "oneoff",
		currency,
		expectedAmountMinor,
		transactionPaidAmountMinor,
		productPaidAmountMinor,
		taxAndAdjustmentsMinor,
		paidAtMs,
		lineKey: `package:${packageId}:unit:1`
	};
}

function extractTebexIntent(subject, product) {
	// Headless documents basket custom data being returned with webhooks, while
	// deployed payloads have also surfaced it on an individual product. Support
	// both representations, require every supplied copy to agree, and fail closed
	// when neither representation contains our opaque key.
	const containers = [
		subject.custom,
		subject.basket && subject.basket.custom,
		subject.basket_custom,
		product.custom
	];
	const values = containers
		.filter((value) => value && typeof value === "object" && !Array.isArray(value))
		.map((value) => String(value.voidscape_intent || "").trim())
		.filter(Boolean);
	const unique = Array.from(new Set(values));
	if (unique.length !== 1 || unique[0].length > 64) {
		throw new HttpError(422, values.length ? "payment_intent_mismatch" : "payment_intent_missing");
	}
	return unique[0];
}

async function verifiedCommerceIntent(facts) {
	const rows = await sqliteCommerceRead(`
		SELECT intent_id, account_id, package_id, expected_currency, expected_amount_minor,
		       basket_id, status, created_at_ms, expires_at_ms
		FROM portal_commerce_checkout_intents
		WHERE intent_id = ${sqlString(facts.intentId)}
		LIMIT 1;
	`);
	const intent = rows[0];
	if (!intent) throw new HttpError(422, "payment_intent_unknown");
	if (!["basket_created", "completed", "expired"].includes(intent.status)) {
		throw new HttpError(422, "payment_intent_not_payable");
	}
	if (facts.paidAtMs < Number(intent.created_at_ms) - 5 * 60 * 1000) {
		throw new HttpError(422, "payment_before_intent");
	}
	if (String(intent.package_id) !== facts.packageId || facts.packageId !== tebexSubscriptionCardPackageId) {
		throw new HttpError(422, "payment_package_mismatch");
	}
	if (facts.quantity !== 1 || facts.paymentSequence !== "oneoff") {
		throw new HttpError(422, "payment_terms_mismatch");
	}
	if (String(intent.expected_currency).toUpperCase() !== facts.currency
		|| facts.currency !== tebexExpectedCurrency
		|| Number(intent.expected_amount_minor) !== facts.expectedAmountMinor
		|| facts.expectedAmountMinor !== tebexExpectedPriceMinor) {
		throw new HttpError(422, "payment_price_mismatch");
	}
	if (!intent.basket_id || String(intent.basket_id) !== facts.basketId) {
		throw new HttpError(422, "payment_basket_mismatch");
	}
	const store = await loadStore();
	if (!store.accounts.some((account) => Number(account.id) === Number(intent.account_id))) {
		throw new HttpError(422, "payment_account_unknown");
	}
	return {
		...intent,
		account_id: Number(intent.account_id),
		expected_amount_minor: Number(intent.expected_amount_minor),
		lateSettlement: facts.paidAtMs > Number(intent.expires_at_ms)
	};
}

async function commerceWebhookReplayState(webhookId, payloadHash) {
	const rows = await sqliteCommerceRead(`
		SELECT webhook_id, payload_sha256, status, attempt_count
		FROM portal_commerce_webhook_events
		WHERE webhook_id = ${sqlString(webhookId)} OR payload_sha256 = ${sqlString(payloadHash)}
		ORDER BY webhook_id;
	`);
	const exact = rows.find((row) => row.webhook_id === webhookId);
	if (exact && exact.payload_sha256 !== payloadHash) {
		throw new HttpError(409, "webhook_id_payload_mismatch");
	}
	const samePayload = rows.find((row) => row.payload_sha256 === payloadHash);
	if (samePayload && samePayload.webhook_id !== webhookId) {
		if (["processed", "ignored"].includes(samePayload.status)) return { duplicate: true, row: samePayload };
		throw new HttpError(409, "webhook_payload_id_mismatch");
	}
	return {
		duplicate: Boolean(exact && ["processed", "ignored"].includes(exact.status)),
		row: exact || null
	};
}

function commerceEventUpsertSql(event) {
	const timestamp = Date.now();
	return `
		INSERT INTO portal_commerce_webhook_events (
			webhook_id, provider, event_type, payload_sha256, transaction_id,
			status, attempt_count, created_at_ms, updated_at_ms
		) VALUES (
			${sqlString(event.webhookId)}, 'tebex', ${sqlString(event.eventType)}, ${sqlString(event.payloadHash)},
			${event.transactionId ? sqlString(event.transactionId) : "NULL"}, 'received', 1, ${timestamp}, ${timestamp}
		)
		ON CONFLICT(webhook_id) DO UPDATE SET
			attempt_count = portal_commerce_webhook_events.attempt_count + 1,
			status = 'received',
			last_error_code = NULL,
			updated_at_ms = excluded.updated_at_ms
		WHERE portal_commerce_webhook_events.payload_sha256 = excluded.payload_sha256
		  AND portal_commerce_webhook_events.status = 'failed';
	`;
}

async function processCompletedCommerceWebhook(event) {
	const { facts, intent } = event;
	const timestamp = Date.now();
	const rows = await sqliteCommerceWrite(`
		BEGIN IMMEDIATE;
		${commerceEventUpsertSql({ ...event, transactionId: facts.transactionId })}
		INSERT OR IGNORE INTO portal_commerce_payments (
			provider, transaction_id, intent_id, account_id, basket_id, package_id,
			quantity, payment_sequence, currency, expected_amount_minor,
			product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor,
			status, completed_at_ms, updated_at_ms
		)
		SELECT 'tebex', ${sqlString(facts.transactionId)}, intent_id, account_id, basket_id, package_id,
		       1, 'oneoff', expected_currency, expected_amount_minor,
		       ${facts.productPaidAmountMinor}, ${facts.transactionPaidAmountMinor}, ${facts.taxAndAdjustmentsMinor},
		       'complete', ${facts.paidAtMs}, ${timestamp}
		FROM portal_commerce_checkout_intents
		WHERE intent_id = ${sqlString(facts.intentId)}
		  AND account_id = ${intent.account_id}
		  AND package_id = ${sqlString(facts.packageId)}
		  AND expected_currency = ${sqlString(facts.currency)}
		  AND expected_amount_minor = ${facts.expectedAmountMinor}
		  AND basket_id = ${sqlString(String(intent.basket_id))}
		  AND ${facts.paidAtMs} >= created_at_ms - 300000
		  AND status IN ('basket_created', 'completed', 'expired')
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_webhook_events
			WHERE webhook_id = ${sqlString(event.webhookId)}
			  AND payload_sha256 = ${sqlString(event.payloadHash)}
			  AND status = 'received'
		  );
		INSERT OR IGNORE INTO portal_commerce_entitlements (
			payment_id, account_id, provider, transaction_id, line_key, package_id,
			unit_index, state, created_at_ms, updated_at_ms
		)
		SELECT id, account_id, provider, transaction_id, ${sqlString(facts.lineKey)}, package_id,
		       1, 'pending', ${timestamp}, ${timestamp}
		FROM portal_commerce_payments
		WHERE provider = 'tebex'
		  AND transaction_id = ${sqlString(facts.transactionId)}
		  AND intent_id = ${sqlString(facts.intentId)}
		  AND account_id = ${intent.account_id}
		  AND package_id = ${sqlString(facts.packageId)}
		  AND quantity = 1
		  AND payment_sequence = 'oneoff'
		  AND currency = ${sqlString(facts.currency)}
		  AND expected_amount_minor = ${facts.expectedAmountMinor}
		  AND product_paid_amount_minor = ${facts.productPaidAmountMinor}
		  AND transaction_paid_amount_minor = ${facts.transactionPaidAmountMinor}
		  AND tax_and_adjustments_minor = ${facts.taxAndAdjustmentsMinor};
		UPDATE portal_commerce_checkout_intents
		SET status = 'completed',
		    last_error_code = ${intent.lateSettlement ? "'late_signed_settlement'" : "NULL"},
		    updated_at_ms = ${timestamp}
		WHERE intent_id = ${sqlString(facts.intentId)}
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_payments
			WHERE provider = 'tebex' AND transaction_id = ${sqlString(facts.transactionId)}
			  AND intent_id = ${sqlString(facts.intentId)}
		  );
		UPDATE portal_commerce_webhook_events
		SET status = 'processed', processed_at_ms = ${timestamp}, last_error_code = NULL, updated_at_ms = ${timestamp}
		WHERE webhook_id = ${sqlString(event.webhookId)}
		  AND payload_sha256 = ${sqlString(event.payloadHash)}
		  AND status = 'received'
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_payments p
			JOIN portal_commerce_entitlements e ON e.payment_id = p.id
			WHERE p.provider = 'tebex'
			  AND p.transaction_id = ${sqlString(facts.transactionId)}
			  AND p.intent_id = ${sqlString(facts.intentId)}
			  AND p.account_id = ${intent.account_id}
			  AND p.package_id = ${sqlString(facts.packageId)}
			  AND p.quantity = 1 AND p.payment_sequence = 'oneoff'
			  AND p.currency = ${sqlString(facts.currency)}
			  AND p.expected_amount_minor = ${facts.expectedAmountMinor}
			  AND p.product_paid_amount_minor = ${facts.productPaidAmountMinor}
			  AND p.transaction_paid_amount_minor = ${facts.transactionPaidAmountMinor}
			  AND p.tax_and_adjustments_minor = ${facts.taxAndAdjustmentsMinor}
			  AND e.line_key = ${sqlString(facts.lineKey)} AND e.unit_index = 1
		  );
		UPDATE portal_commerce_webhook_events
		SET status = 'failed', last_error_code = 'commerce_atomic_validation_failed', updated_at_ms = ${timestamp}
		WHERE webhook_id = ${sqlString(event.webhookId)} AND status = 'received';
		COMMIT;
		SELECT webhook_id, status, last_error_code
		FROM portal_commerce_webhook_events WHERE webhook_id = ${sqlString(event.webhookId)};
	`);
	if (!rows.length || rows[0].status !== "processed") {
		throw new HttpError(422, rows[0] && rows[0].last_error_code || "webhook_processing_failed");
	}
}

function reversalTransition(eventType) {
	if (eventType === "payment.refunded") {
		return {
			paymentStatus: "CASE WHEN status = 'dispute_lost' THEN status ELSE 'refunded' END",
			entitlementPaymentPredicate: "status <> 'dispute_lost'",
			entitlementState: "CASE WHEN claimed_at_ms IS NOT NULL THEN 'review' ELSE 'revoked' END",
			reviewReason: "CASE WHEN claimed_at_ms IS NOT NULL THEN 'refund_after_claim' ELSE 'refund' END"
		};
	}
	if (eventType === "payment.dispute.opened") {
		return {
			paymentStatus: "CASE WHEN status IN ('refunded', 'dispute_lost') THEN status ELSE 'disputed' END",
			entitlementPaymentPredicate: "status NOT IN ('refunded', 'dispute_lost')",
			entitlementState: "CASE WHEN state = 'pending' THEN 'frozen' WHEN state = 'claimed' THEN 'review' ELSE state END",
			reviewReason: "CASE WHEN state = 'claimed' THEN 'dispute_after_claim' WHEN state = 'pending' THEN 'dispute_opened' ELSE review_reason END"
		};
	}
	if (eventType === "payment.dispute.won") {
		return {
			paymentStatus: "CASE WHEN status IN ('refunded', 'dispute_lost') THEN status ELSE 'restored' END",
			entitlementPaymentPredicate: "status NOT IN ('refunded', 'dispute_lost')",
			entitlementState: "CASE WHEN state = 'frozen' THEN 'pending' WHEN state = 'review' AND review_reason IN ('dispute_after_claim', 'dispute_closed_review') AND claimed_at_ms IS NOT NULL THEN 'claimed' WHEN state = 'review' AND review_reason IN ('dispute_opened', 'dispute_closed_review') AND claimed_at_ms IS NULL THEN 'pending' ELSE state END",
			reviewReason: "CASE WHEN state = 'frozen' THEN NULL WHEN state = 'review' AND review_reason IN ('dispute_after_claim', 'dispute_opened', 'dispute_closed_review') THEN NULL ELSE review_reason END"
		};
	}
	if (eventType === "payment.dispute.lost") {
		return {
			paymentStatus: "CASE WHEN status = 'refunded' THEN status ELSE 'dispute_lost' END",
			entitlementPaymentPredicate: "status <> 'refunded'",
			entitlementState: "CASE WHEN claimed_at_ms IS NOT NULL THEN 'review' ELSE 'revoked' END",
			reviewReason: "CASE WHEN claimed_at_ms IS NOT NULL THEN 'dispute_lost_after_claim' ELSE 'dispute_lost' END"
		};
	}
	return {
		paymentStatus: "CASE WHEN status IN ('refunded', 'dispute_lost', 'restored') THEN status ELSE 'review' END",
		entitlementPaymentPredicate: "status NOT IN ('refunded', 'dispute_lost', 'restored')",
		entitlementState: "CASE WHEN state <> 'revoked' THEN 'review' ELSE state END",
		reviewReason: "CASE WHEN state <> 'revoked' THEN 'dispute_closed_review' ELSE review_reason END"
	};
}

async function processReversalCommerceWebhook(event) {
	const { facts, intent } = event;
	const existingRows = await sqliteCommerceRead(`
		SELECT p.id, p.intent_id, p.account_id, p.basket_id, p.package_id, p.quantity,
		       p.payment_sequence, p.currency, p.expected_amount_minor,
		       p.product_paid_amount_minor, p.transaction_paid_amount_minor, p.tax_and_adjustments_minor,
		       e.line_key, e.unit_index
		FROM portal_commerce_payments p
		JOIN portal_commerce_entitlements e ON e.payment_id = p.id
		WHERE p.provider = 'tebex' AND p.transaction_id = ${sqlString(facts.transactionId)}
		LIMIT 1;
	`);
	const payment = existingRows[0];
	if (!payment
		|| payment.intent_id !== facts.intentId
		|| Number(payment.account_id) !== intent.account_id
		|| String(payment.basket_id) !== String(intent.basket_id)
		|| payment.package_id !== facts.packageId
		|| Number(payment.quantity) !== 1
		|| payment.payment_sequence !== "oneoff"
		|| payment.currency !== facts.currency
		|| Number(payment.expected_amount_minor) !== facts.expectedAmountMinor
		|| Number(payment.product_paid_amount_minor) !== facts.productPaidAmountMinor
		|| Number(payment.transaction_paid_amount_minor) !== facts.transactionPaidAmountMinor
		|| Number(payment.tax_and_adjustments_minor) !== facts.taxAndAdjustmentsMinor
		|| payment.line_key !== facts.lineKey
		|| Number(payment.unit_index) !== 1) {
		throw new HttpError(422, "payment_ledger_mismatch");
	}

	const transition = reversalTransition(event.eventType);
	const timestamp = Date.now();
	const rows = await sqliteCommerceWrite(`
		BEGIN IMMEDIATE;
		${commerceEventUpsertSql({ ...event, transactionId: facts.transactionId })}
		UPDATE portal_commerce_payments
		SET status = ${transition.paymentStatus}, updated_at_ms = ${timestamp}
		WHERE provider = 'tebex'
		  AND transaction_id = ${sqlString(facts.transactionId)}
		  AND intent_id = ${sqlString(facts.intentId)}
		  AND account_id = ${intent.account_id}
		  AND basket_id = ${sqlString(String(intent.basket_id))}
		  AND package_id = ${sqlString(facts.packageId)}
		  AND quantity = 1 AND payment_sequence = 'oneoff'
		  AND currency = ${sqlString(facts.currency)}
		  AND expected_amount_minor = ${facts.expectedAmountMinor}
		  AND product_paid_amount_minor = ${facts.productPaidAmountMinor}
		  AND transaction_paid_amount_minor = ${facts.transactionPaidAmountMinor}
		  AND tax_and_adjustments_minor = ${facts.taxAndAdjustmentsMinor}
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_webhook_events
			WHERE webhook_id = ${sqlString(event.webhookId)}
			  AND payload_sha256 = ${sqlString(event.payloadHash)}
			  AND status = 'received'
		  );
		UPDATE portal_commerce_entitlements
		SET review_reason = ${transition.reviewReason},
		    state = ${transition.entitlementState},
		    updated_at_ms = ${timestamp}
		WHERE provider = 'tebex'
		  AND transaction_id = ${sqlString(facts.transactionId)}
		  AND line_key = ${sqlString(facts.lineKey)}
		  AND unit_index = 1
		  AND account_id = ${intent.account_id}
		  AND package_id = ${sqlString(facts.packageId)}
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_payments
			WHERE id = portal_commerce_entitlements.payment_id
			  AND ${transition.entitlementPaymentPredicate}
		  );
		UPDATE portal_commerce_webhook_events
		SET status = 'processed', processed_at_ms = ${timestamp}, last_error_code = NULL, updated_at_ms = ${timestamp}
		WHERE webhook_id = ${sqlString(event.webhookId)}
		  AND payload_sha256 = ${sqlString(event.payloadHash)}
		  AND status = 'received'
		  AND EXISTS (
			SELECT 1 FROM portal_commerce_payments p
			JOIN portal_commerce_entitlements e ON e.payment_id = p.id
			WHERE p.provider = 'tebex' AND p.transaction_id = ${sqlString(facts.transactionId)}
			  AND p.intent_id = ${sqlString(facts.intentId)}
			  AND p.account_id = ${intent.account_id}
			  AND p.package_id = ${sqlString(facts.packageId)}
			  AND p.quantity = 1 AND p.payment_sequence = 'oneoff'
			  AND p.currency = ${sqlString(facts.currency)}
			  AND p.expected_amount_minor = ${facts.expectedAmountMinor}
			  AND p.product_paid_amount_minor = ${facts.productPaidAmountMinor}
			  AND p.transaction_paid_amount_minor = ${facts.transactionPaidAmountMinor}
			  AND p.tax_and_adjustments_minor = ${facts.taxAndAdjustmentsMinor}
			  AND e.line_key = ${sqlString(facts.lineKey)} AND e.unit_index = 1
		  );
		UPDATE portal_commerce_webhook_events
		SET status = 'failed', last_error_code = 'commerce_atomic_validation_failed', updated_at_ms = ${timestamp}
		WHERE webhook_id = ${sqlString(event.webhookId)} AND status = 'received';
		COMMIT;
		SELECT webhook_id, status, last_error_code
		FROM portal_commerce_webhook_events WHERE webhook_id = ${sqlString(event.webhookId)};
	`);
	if (!rows.length || rows[0].status !== "processed") {
		throw new HttpError(422, rows[0] && rows[0].last_error_code || "webhook_processing_failed");
	}
}

async function recordFailedCommerceWebhook(event) {
	const timestamp = Date.now();
	const errorCode = safeCommerceCode(event.errorCode);
	await sqliteCommerceWrite(`
		BEGIN IMMEDIATE;
		INSERT INTO portal_commerce_webhook_events (
			webhook_id, provider, event_type, payload_sha256, transaction_id,
			status, attempt_count, last_error_code, created_at_ms, updated_at_ms
		) VALUES (
			${sqlString(event.webhookId)}, 'tebex', ${sqlString(event.eventType)}, ${sqlString(event.payloadHash)},
			${event.transactionId ? sqlString(event.transactionId) : "NULL"}, 'failed', 1,
			${sqlString(errorCode)}, ${timestamp}, ${timestamp}
		)
		ON CONFLICT(webhook_id) DO UPDATE SET
			attempt_count = portal_commerce_webhook_events.attempt_count + 1,
			status = CASE WHEN portal_commerce_webhook_events.status = 'processed' THEN 'processed' ELSE 'failed' END,
			last_error_code = CASE WHEN portal_commerce_webhook_events.status = 'processed'
				THEN portal_commerce_webhook_events.last_error_code ELSE excluded.last_error_code END,
			updated_at_ms = excluded.updated_at_ms
		WHERE portal_commerce_webhook_events.payload_sha256 = excluded.payload_sha256;
		COMMIT;
		SELECT webhook_id, status FROM portal_commerce_webhook_events
		WHERE webhook_id = ${sqlString(event.webhookId)};
	`);
}

async function recordIgnoredCommerceWebhook(event) {
	const timestamp = Date.now();
	await sqliteCommerceWrite(`
		BEGIN IMMEDIATE;
		INSERT OR IGNORE INTO portal_commerce_webhook_events (
			webhook_id, provider, event_type, payload_sha256, transaction_id,
			status, attempt_count, created_at_ms, processed_at_ms, updated_at_ms
		) VALUES (
			${sqlString(event.webhookId)}, 'tebex', ${sqlString(event.eventType)}, ${sqlString(event.payloadHash)},
			NULL, 'ignored', 1, ${timestamp}, ${timestamp}, ${timestamp}
		);
		UPDATE portal_commerce_webhook_events
		SET status = 'ignored', processed_at_ms = ${timestamp}, updated_at_ms = ${timestamp}
		WHERE webhook_id = ${sqlString(event.webhookId)}
		  AND payload_sha256 = ${sqlString(event.payloadHash)}
		  AND status = 'failed';
		COMMIT;
		SELECT webhook_id, status FROM portal_commerce_webhook_events
		WHERE webhook_id = ${sqlString(event.webhookId)};
	`);
}

async function commerceSchemaAvailable() {
	if (!openRscDbPath) return false;
	const rows = await sqliteCommerceRead(`
		SELECT name FROM sqlite_master
		WHERE type = 'table' AND name IN (
			'portal_commerce_checkout_intents',
			'portal_commerce_webhook_events',
			'portal_commerce_payments',
			'portal_commerce_entitlements'
		)
		ORDER BY name;
	`);
	return rows.length === 4;
}

async function paidSubscriptionCardState(accountId) {
	const empty = {
		checkoutAvailable: false,
		pending: 0,
		claimed: 0,
		review: 0,
		frozen: 0,
		revoked: 0
	};
	if (!openRscDbPath) return empty;
	const schemaAvailable = await commerceSchemaAvailable();
	if (!schemaAvailable) {
		if (tebexCheckoutConfigured()) throw new HttpError(503, "commerce_schema_missing");
		return empty;
	}
	const rows = await sqliteCommerceRead(`
		SELECT
			COALESCE(SUM(CASE WHEN state = 'pending' THEN 1 ELSE 0 END), 0) AS pending,
			COALESCE(SUM(CASE WHEN state = 'claimed' THEN 1 ELSE 0 END), 0) AS claimed,
			COALESCE(SUM(CASE WHEN state = 'review' THEN 1 ELSE 0 END), 0) AS review,
			COALESCE(SUM(CASE WHEN state = 'frozen' THEN 1 ELSE 0 END), 0) AS frozen,
			COALESCE(SUM(CASE WHEN state = 'revoked' THEN 1 ELSE 0 END), 0) AS revoked
		FROM portal_commerce_entitlements
		WHERE account_id = ${Number(accountId)};
	`);
	const counts = rows[0] || {};
	return {
		checkoutAvailable: tebexCheckoutConfigured(),
		pending: Number(counts.pending || 0),
		claimed: Number(counts.claimed || 0),
		review: Number(counts.review || 0),
		frozen: Number(counts.frozen || 0),
		revoked: Number(counts.revoked || 0)
	};
}

function commerceAdminLimit(value) {
	const limit = Number(value || 100);
	if (!Number.isInteger(limit) || limit < 1 || limit > 200) {
		throw new HttpError(400, "invalid_limit");
	}
	return limit;
}

async function commerceAdminList(limit) {
	if (!(await commerceSchemaAvailable())) throw new HttpError(503, "commerce_schema_missing");
	const [summaryRows, intents, events, payments, entitlements] = await Promise.all([
		commerceSummaryRows(),
		sqliteCommerceRead(`
			SELECT intent_id, account_id, provider, package_id, expected_currency,
			       expected_amount_minor, basket_id, status, last_error_code,
			       created_at_ms, expires_at_ms, updated_at_ms
			FROM portal_commerce_checkout_intents
			ORDER BY created_at_ms DESC, intent_id DESC LIMIT ${limit};
		`),
		sqliteCommerceRead(`
			SELECT webhook_id, event_type, payload_sha256, transaction_id, status,
			       attempt_count, last_error_code, created_at_ms, processed_at_ms, updated_at_ms
			FROM portal_commerce_webhook_events
			ORDER BY created_at_ms DESC, webhook_id DESC LIMIT ${limit};
		`),
		sqliteCommerceRead(`
			SELECT id, provider, transaction_id, intent_id, account_id, basket_id,
			       package_id, quantity, payment_sequence, currency, expected_amount_minor,
			       product_paid_amount_minor, transaction_paid_amount_minor, tax_and_adjustments_minor,
			       status, completed_at_ms, updated_at_ms
			FROM portal_commerce_payments
			ORDER BY completed_at_ms DESC, id DESC LIMIT ${limit};
		`),
		sqliteCommerceRead(`
			SELECT id, payment_id, account_id, provider, transaction_id, line_key,
			       package_id, unit_index, state, claimed_player_id, claimed_item_id,
			       claimed_at_ms, review_reason, created_at_ms, updated_at_ms
			FROM portal_commerce_entitlements
			ORDER BY created_at_ms DESC, id DESC LIMIT ${limit};
		`)
	]);
	return {
		config: tebexConfigHealth(),
		summary: summaryRows[0] || {},
		intents,
		events,
		payments,
		entitlements
	};
}

async function commerceSummaryRows() {
	return sqliteCommerceRead(`
		SELECT
			(SELECT COUNT(*) FROM portal_commerce_checkout_intents) AS intents,
			(SELECT COUNT(*) FROM portal_commerce_webhook_events) AS webhook_events,
			(SELECT COUNT(*) FROM portal_commerce_webhook_events WHERE status = 'failed') AS failed_events,
			(SELECT COUNT(*) FROM portal_commerce_payments) AS payments,
			(SELECT COUNT(*) FROM portal_commerce_entitlements) AS entitlements,
			(SELECT COUNT(*) FROM portal_commerce_entitlements WHERE state = 'pending') AS pending,
			(SELECT COUNT(*) FROM portal_commerce_entitlements WHERE state = 'claimed') AS claimed,
			(SELECT COUNT(*) FROM portal_commerce_entitlements WHERE state = 'review') AS review,
			(SELECT COUNT(*) FROM portal_commerce_entitlements WHERE state = 'frozen') AS frozen,
			(SELECT COUNT(*) FROM portal_commerce_entitlements WHERE state = 'revoked') AS revoked;
	`);
}

async function commerceReconcileReport() {
	if (!(await commerceSchemaAvailable())) throw new HttpError(503, "commerce_schema_missing");
	const [summaryRows, completedWithoutPayment, paymentsWithoutEntitlement, unsafeEntitlements, ledgerMismatches, failedEvents] = await Promise.all([
		commerceSummaryRows(),
		sqliteCommerceRead(`
			SELECT intent_id FROM portal_commerce_checkout_intents i
			WHERE status = 'completed'
			  AND NOT EXISTS (SELECT 1 FROM portal_commerce_payments p WHERE p.intent_id = i.intent_id)
			ORDER BY intent_id;
		`),
		sqliteCommerceRead(`
			SELECT transaction_id FROM portal_commerce_payments p
			WHERE NOT EXISTS (SELECT 1 FROM portal_commerce_entitlements e WHERE e.payment_id = p.id)
			ORDER BY transaction_id;
		`),
		sqliteCommerceRead(`
			SELECT e.id, e.transaction_id, e.state, p.status AS payment_status,
			       e.claimed_player_id, e.claimed_item_id, e.claimed_at_ms
			FROM portal_commerce_entitlements e
			JOIN portal_commerce_payments p ON p.id = e.payment_id
			WHERE (e.state = 'pending' AND p.status NOT IN ('complete', 'restored'))
			   OR (e.state = 'frozen' AND p.status <> 'disputed')
			   OR (e.state = 'claimed' AND p.status NOT IN ('complete', 'restored'))
			   OR (e.state = 'review' AND p.status NOT IN ('disputed', 'refunded', 'dispute_lost', 'review'))
			   OR (e.state = 'revoked' AND p.status NOT IN ('refunded', 'dispute_lost'))
			   OR (e.state = 'claimed' AND (e.claimed_player_id IS NULL OR e.claimed_item_id IS NULL OR e.claimed_at_ms IS NULL))
			   OR (e.state IN ('pending', 'frozen', 'revoked') AND (e.claimed_player_id IS NOT NULL OR e.claimed_item_id IS NOT NULL OR e.claimed_at_ms IS NOT NULL))
			   OR (e.state = 'review' AND ((e.claimed_player_id IS NULL) <> (e.claimed_item_id IS NULL) OR (e.claimed_player_id IS NULL) <> (e.claimed_at_ms IS NULL)))
			ORDER BY e.id;
		`),
		sqliteCommerceRead(`
			SELECT 'payment_intent' AS kind, p.transaction_id AS entity_id
			FROM portal_commerce_payments p
			LEFT JOIN portal_commerce_checkout_intents i ON i.intent_id = p.intent_id
			WHERE i.intent_id IS NULL
			   OR p.account_id <> i.account_id OR p.basket_id <> i.basket_id
			   OR p.package_id <> i.package_id OR p.currency <> i.expected_currency
			   OR p.expected_amount_minor <> i.expected_amount_minor
			UNION ALL
			SELECT 'entitlement_payment' AS kind, CAST(e.id AS TEXT) AS entity_id
			FROM portal_commerce_entitlements e
			LEFT JOIN portal_commerce_payments p ON p.id = e.payment_id
			WHERE p.id IS NULL OR e.account_id <> p.account_id OR e.provider <> p.provider
			   OR e.transaction_id <> p.transaction_id OR e.package_id <> p.package_id
			ORDER BY kind, entity_id;
		`),
		sqliteCommerceRead(`
			SELECT webhook_id, event_type, transaction_id, attempt_count, last_error_code
			FROM portal_commerce_webhook_events
			WHERE status = 'failed'
			ORDER BY webhook_id;
		`)
	]);
	const anomalies = {
		completedIntentsWithoutPayment: completedWithoutPayment.map((row) => row.intent_id),
		paymentsWithoutEntitlement: paymentsWithoutEntitlement.map((row) => row.transaction_id),
		unsafeEntitlements,
		ledgerMismatches,
		failedEvents
	};
	return {
		ok: Object.values(anomalies).every((rows) => rows.length === 0),
		mutated: false,
		summary: summaryRows[0] || {},
		anomalies
	};
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

function withOpenRscWriteLock(callback) {
	if (!openRscDbPath) return Promise.reject(new HttpError(503, "openrsc_db_not_configured"));
	return new Promise((resolveLock, rejectLock) => {
		const child = spawn("sqlite3", ["-batch", openRscDbPath], {
			stdio: ["pipe", "pipe", "pipe"]
		});
		const readyMarker = "__VOIDSCAPE_PORTAL_WRITE_LOCKED__";
		let stdout = "";
		let stderr = "";
		let callbackStarted = false;
		let callbackResult;
		let callbackError = null;
		let settled = false;
		const acquisitionTimeout = setTimeout(() => {
			if (callbackStarted || settled) return;
			settled = true;
			child.kill("SIGKILL");
			rejectLock(new HttpError(503, "openrsc_claim_lock_unavailable"));
		}, 10000);

		child.stdout.setEncoding("utf8");
		child.stderr.setEncoding("utf8");
		child.stderr.on("data", (chunk) => {
			if (stderr.length < 4096) stderr += chunk;
		});
		child.stdout.on("data", (chunk) => {
			if (stdout.length < 4096) stdout += chunk;
			if (callbackStarted || !stdout.includes(readyMarker)) return;
			callbackStarted = true;
			clearTimeout(acquisitionTimeout);
			Promise.resolve()
				.then(callback)
				.then((result) => {
					callbackResult = result;
					child.stdin.end("ROLLBACK;\n");
				})
				.catch((error) => {
					callbackError = error;
					child.stdin.end("ROLLBACK;\n");
				});
		});
		child.on("error", (error) => {
			if (settled) return;
			settled = true;
			clearTimeout(acquisitionTimeout);
			rejectLock(error && error.code === "ENOENT"
				? new HttpError(503, "sqlite3_not_available")
				: new HttpError(503, "openrsc_claim_lock_unavailable"));
		});
		child.on("close", (code) => {
			if (settled) return;
			settled = true;
			clearTimeout(acquisitionTimeout);
			if (callbackError) {
				rejectLock(callbackError);
				return;
			}
			if (!callbackStarted || code !== 0) {
				if (stderr) console.error("openrsc_claim_lock_failed", stderr.trim().slice(0, 180));
				rejectLock(new HttpError(503, "openrsc_claim_lock_unavailable"));
				return;
			}
			resolveLock(callbackResult);
		});
		child.stdin.write(".bail on\n.timeout 5000\nBEGIN IMMEDIATE;\n.print " + readyMarker + "\n");
	});
}

async function sqliteWriteJson(query) {
	try {
		const { stdout } = await execFile("sqlite3", ["-cmd", ".timeout 5000", "-json", openRscDbPath, query], {
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

async function sqliteWriteJsonStrict(query, conflictCode) {
	try {
		const { stdout } = await execFile("sqlite3", [
			"-batch", "-cmd", ".timeout 5000", "-cmd", ".bail on", "-json", openRscDbPath, query
		], { maxBuffer: 1024 * 1024 });
		return stdout.trim() ? JSON.parse(stdout) : [];
	} catch (error) {
		if (error.code === "ENOENT") throw new HttpError(503, "sqlite3_not_available");
		if (error.stderr && error.stderr.includes("no such table")) {
			throw new HttpError(500, "openrsc_schema_missing");
		}
		const stderr = String(error.stderr || "");
		if (conflictCode && (stderr.includes("CHECK constraint failed: ok = 1")
			|| stderr.includes("voidscape_launch_card_guard"))) {
			throw new HttpError(409, conflictCode);
		}
		if (stderr.includes("constraint")) {
			throw new HttpError(409, "openrsc_character_constraint_failed");
		}
		throw error;
	}
}

async function openRscExistingTables(names) {
	if (!Array.isArray(names) || !names.length) return new Set();
	const rows = await sqliteJson(`
		SELECT name
		FROM sqlite_master
		WHERE type = 'table'
		  AND name IN (${names.map(sqlString).join(", ")})
	`);
	return new Set(rows.map((row) => row.name));
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
		combatXpRate: active ? baseCombatXpRate + subscriptionXpBonus : baseCombatXpRate,
		skillXpRate: active ? baseSkillXpRate + subscriptionXpBonus : baseSkillXpRate
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
	}[Number(value)] || "Game save";
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
	if (x >= 16 && x <= 35 && y >= 18 && y <= 45) return "Void Island";
	if (x >= 108 && x <= 147 && y >= 620 && y <= 670) return "Lumbridge";
	if (x >= 78 && x <= 175 && y >= 490 && y <= 537) return "Varrock";
	if (x >= 92 && x <= 150 && y >= 444 && y <= 490) return "Varrock";
	if (x >= 210 && x <= 233 && y >= 608 && y <= 659) return "Draynor";
	if (x >= 245 && x <= 341 && y >= 531 && y <= 583) return "Falador";
	if (x >= 48 && x <= 96 && y >= 659 && y <= 703) return "Al Kharid";
	if (x >= 198 && x <= 229 && y >= 427 && y <= 450) return "Edgeville";
	if (x >= 208 && x <= 227 && y >= 451 && y <= 472) return "Edgeville";
	if (x >= 100 && x <= 130 && y >= 290 && y <= 325) return "Void Enclave";
	if (x >= 70 && x <= 90 && y >= 3240 && y <= 3265) return "Void Dungeon";
	if (x >= 582 && x <= 616 && y >= 2910 && y <= 2916) return "Void Arena";
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
	const referralRewardCodes = Array.isArray(founder.referralRewardCodes)
		? founder.referralRewardCodes
			.filter((reward) => reward && reward.code)
			.map((reward) => ({
				code: reward.code,
				referralId: reward.referralId || null,
				referredFounderId: reward.referredFounderId || null,
				releaseValid: true,
				syncedToGame: Boolean(reward.syncedAt),
				createdAt: reward.createdAt || null
			}))
		: [];
	return {
		username: founder.username,
		email: founder.emailDisplay,
		code: founder.code,
		creditedReferrals,
		requiredReferrals: 2,
		referralRewardCodeCount: referralRewardCodes.length,
		referralRewardCodes,
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

async function grantStarterCardIfEligible(store, account, founder, source, request, context, transaction = null) {
	if (!context || !context.signupSignalsRecorded) {
		recordSignupSignals(store, account, request, context);
	}
	if (!launchFreeCardWindowOpen()) {
		account.starterCardReview = {
			status: "closed",
			reasons: ["starter_card_launch_window_closed"],
			createdAt: now()
		};
		audit(store, "starter_card_window_closed", {
			accountId: account.id,
			source
		});
		return null;
	}
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
	const rollbackStarterCard = await syncStarterCardToOpenRsc(account);
	if (transaction && rollbackStarterCard) transaction.onRollback(rollbackStarterCard);
	return entitlement;
}

function starterCardDecision(store, account, request, context = {}) {
	const reasons = [];
	const ip = clientIp(request);
	const since = Date.now() - 1000 * 60 * 60 * 24;
	const ipGrantCount = countAbuseSignals(store, "starter_card_granted_ip", ip, since);
	if (!isLocalIp(ip) && starterIpDailyLimit > 0 && ipGrantCount >= starterIpDailyLimit) {
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

function assertAccountSignupIpAllowed(store, request) {
	assertSignupAllowed(store, request);
}

function rejectRateLimit(request, rule, context = {}) {
	const accountId = Number(context.accountId || 0);
	console.warn(JSON.stringify({
		event: "portal_rate_limit_decision",
		decision: "deny",
		rule: String(rule || "unspecified").slice(0, 80),
		route: String(request && request.url || "").split("?", 1)[0].slice(0, 160),
		ipHashPrefix: abuseHash(clientIp(request)).slice(0, 12),
		...(accountId > 0 ? { accountId } : {}),
		createdAt: now()
	}));
	throw new HttpError(429, "rate_limited");
}

function assertSignupAllowed(store, request) {
	const profile = clientAbuseProfile(request);
	if (profile.local) return;
	assertClientIpNotBlocked(profile);
	const nowMs = Date.now();
	const hourlyLimit = profile.proxy ? Math.min(signupIpHourlyLimit, proxySignupIpDailyLimit) : signupIpHourlyLimit;
	const dailyLimit = profile.proxy ? Math.min(signupIpDailyLimit, proxySignupIpDailyLimit) : signupIpDailyLimit;
	if (countSignupSignalsByIp(store, profile.ip, nowMs - 1000 * 60 * 60) >= hourlyLimit) {
		rejectRateLimit(request, "signup_ip_hourly");
	}
	if (countSignupSignalsByIp(store, profile.ip, nowMs - 1000 * 60 * 60 * 24) >= dailyLimit) {
		rejectRateLimit(request, "signup_ip_daily");
	}
	if (profile.subnetKey && signupSubnetHourlyLimit > 0
		&& countSignupSignalsBySubnet(store, profile.subnetKey, nowMs - 1000 * 60 * 60) >= signupSubnetHourlyLimit) {
		rejectRateLimit(request, "signup_subnet_hourly");
	}
	if (profile.subnetKey && signupSubnetDailyLimit > 0
		&& countSignupSignalsBySubnet(store, profile.subnetKey, nowMs - 1000 * 60 * 60 * 24) >= signupSubnetDailyLimit) {
		rejectRateLimit(request, "signup_subnet_daily");
	}
}

async function assertCharacterCreationAllowed(store, account, request) {
	const profile = clientAbuseProfile(request);
	assertClientIpNotBlocked(profile);
	const nowMs = Date.now();
	const hourAgo = nowMs - 1000 * 60 * 60;
	const dayAgo = nowMs - 1000 * 60 * 60 * 24;
	if (!profile.local) {
		const hourlyIpLimit = profile.proxy ? Math.min(characterIpHourlyLimit, proxyCharacterIpDailyLimit) : characterIpHourlyLimit;
		const dailyIpLimit = profile.proxy ? Math.min(characterIpDailyLimit, proxyCharacterIpDailyLimit) : characterIpDailyLimit;
		if (await characterIpCount(store, profile.ip, hourAgo) >= hourlyIpLimit) {
			rejectRateLimit(request, "character_ip_hourly", { accountId: account.id });
		}
		if (await characterIpCount(store, profile.ip, dayAgo) >= dailyIpLimit) {
			rejectRateLimit(request, "character_ip_daily", { accountId: account.id });
		}
		if (profile.subnetKey && characterSubnetHourlyLimit > 0
			&& await characterSubnetCount(store, profile.subnetKey, hourAgo) >= characterSubnetHourlyLimit) {
			rejectRateLimit(request, "character_subnet_hourly", { accountId: account.id });
		}
		if (profile.subnetKey && characterSubnetDailyLimit > 0
			&& await characterSubnetCount(store, profile.subnetKey, dayAgo) >= characterSubnetDailyLimit) {
			rejectRateLimit(request, "character_subnet_daily", { accountId: account.id });
		}
	}
	if (await characterAccountCount(store, account.id, hourAgo) >= characterAccountHourlyLimit) {
		rejectRateLimit(request, "character_account_hourly", { accountId: account.id });
	}
	if (await characterAccountCount(store, account.id, dayAgo) >= characterAccountDailyLimit) {
		rejectRateLimit(request, "character_account_daily", { accountId: account.id });
	}
}

function countSignupSignalsByIp(store, ip, sinceMs) {
	return countAbuseSignals(store, "account_signup_ip", ip, sinceMs)
		+ countAbuseSignals(store, "founder_signup_ip", ip, sinceMs);
}

function countSignupSignalsBySubnet(store, subnetKey, sinceMs) {
	return countAbuseSignals(store, "account_signup_subnet", subnetKey, sinceMs)
		+ countAbuseSignals(store, "founder_signup_subnet", subnetKey, sinceMs);
}

async function characterIpCount(store, ip, sinceMs) {
	const signalCount = countAbuseSignals(store, "character_created_ip", ip, sinceMs);
	const openRscCount = await openRscCharacterCreationsByIp(ip, sinceMs);
	const initialSignupCount = countAbuseSignals(store, "character_signup_initial_ip", ip, sinceMs);
	return Math.max(0, Math.max(signalCount, openRscCount) - initialSignupCount);
}

async function characterSubnetCount(store, subnetKey, sinceMs) {
	const signalCount = countAbuseSignals(store, "character_created_subnet", subnetKey, sinceMs);
	const openRscCount = await openRscCharacterCreationsBySubnet(subnetKey, sinceMs);
	const initialSignupCount = countAbuseSignals(store, "character_signup_initial_subnet", subnetKey, sinceMs);
	return Math.max(0, Math.max(signalCount, openRscCount) - initialSignupCount);
}

async function characterAccountCount(store, accountId, sinceMs) {
	const signalCount = countAbuseSignals(store, "character_created_account", accountId, sinceMs);
	const openRscCount = await openRscCharacterCreationsByAccount(accountId, sinceMs);
	const initialSignupCount = countAbuseSignals(store, "character_signup_initial_account", accountId, sinceMs);
	return Math.max(0, Math.max(signalCount, openRscCount) - initialSignupCount);
}

async function openRscCharacterCreationsByIp(ip, sinceMs) {
	if (!openRscDbPath || !ip) return 0;
	const sinceSeconds = Math.floor(Number(sinceMs || 0) / 1000);
	const rows = await sqliteJson(`
		SELECT COUNT(*) AS count
		FROM players
		WHERE creation_ip = ${sqlString(ip)}
		  AND creation_date >= ${sinceSeconds}
	`);
	return Number(rows[0] && rows[0].count || 0);
}

async function openRscCharacterCreationsBySubnet(subnetKey, sinceMs) {
	if (!openRscDbPath || !subnetKey) return 0;
	const prefix = ipv4SubnetSqlPrefix(subnetKey);
	if (!prefix) return 0;
	const sinceSeconds = Math.floor(Number(sinceMs || 0) / 1000);
	const rows = await sqliteJson(`
		SELECT COUNT(*) AS count
		FROM players
		WHERE creation_ip LIKE ${sqlString(`${prefix}%`)}
		  AND creation_date >= ${sinceSeconds}
	`);
	return Number(rows[0] && rows[0].count || 0);
}

async function openRscCharacterCreationsByAccount(accountId, sinceMs) {
	if (!openRscDbPath || !Number(accountId)) return 0;
	const sinceSeconds = Math.floor(Number(sinceMs || 0) / 1000);
	const rows = await sqliteJson(`
		SELECT COUNT(DISTINCT p.id) AS count
		FROM players p
		JOIN player_cache pc
		  ON pc.playerID = p.id
		 AND pc.key = ${sqlString(openRscAccountIdCacheKey)}
		 AND pc.value = ${sqlString(accountId)}
		WHERE p.creation_date >= ${sinceSeconds}
	`);
	return Number(rows[0] && rows[0].count || 0);
}

function clientAbuseProfile(request) {
	const ip = clientIp(request);
	return {
		ip,
		local: isLocalIp(ip),
		subnetKey: ipv4SubnetKey(ip),
		blocked: cidrContains(blockedIpCidrs, ip),
		proxy: cidrContains(proxyIpCidrs, ip)
	};
}

function assertClientIpNotBlocked(profile) {
	if (profile && profile.blocked && !profile.local) {
		throw new HttpError(403, "ip_blocked");
	}
}

function ipv4SubnetKey(ip) {
	const intValue = ipv4ToInt(ip);
	if (intValue === null) return "";
	return `ipv4:${(intValue >>> 24) & 255}.${(intValue >>> 16) & 255}.${(intValue >>> 8) & 255}.0/24`;
}

function ipv4SubnetSqlPrefix(subnetKey) {
	const match = /^ipv4:(\d{1,3}\.\d{1,3}\.\d{1,3})\.0\/24$/.exec(String(subnetKey || ""));
	return match ? `${match[1]}.` : "";
}

function cidrList(value) {
	return String(value || "")
		.split(/[,\s]+/)
		.map((entry) => entry.trim())
		.filter(Boolean)
		.map(parseCidr)
		.filter(Boolean);
}

function parseCidr(value) {
	const text = String(value || "").trim();
	if (!text) return null;
	const parts = text.split("/");
	const base = ipv4ToInt(parts[0]);
	if (base === null) return null;
	const prefix = parts.length > 1 ? Number(parts[1]) : 32;
	if (!Number.isInteger(prefix) || prefix < 0 || prefix > 32) return null;
	const mask = prefix === 0 ? 0 : (0xffffffff << (32 - prefix)) >>> 0;
	return {
		base: (base & mask) >>> 0,
		mask,
		prefix
	};
}

function cidrContains(ranges, ip) {
	if (!Array.isArray(ranges) || !ranges.length) return false;
	const value = ipv4ToInt(ip);
	if (value === null) return false;
	return ranges.some((range) => ((value & range.mask) >>> 0) === range.base);
}

function ipv4ToInt(ip) {
	const text = String(ip || "").trim();
	const ipv4 = text.startsWith("::ffff:") ? text.slice("::ffff:".length) : text;
	const parts = ipv4.split(".");
	if (parts.length !== 4) return null;
	let value = 0;
	for (const part of parts) {
		if (!/^\d{1,3}$/.test(part)) return null;
		const number = Number(part);
		if (!Number.isInteger(number) || number < 0 || number > 255) return null;
		value = ((value << 8) + number) >>> 0;
	}
	return value >>> 0;
}

function recordSignupSignals(store, account, request, context = {}) {
	const ip = clientIp(request);
	const subnetKey = ipv4SubnetKey(ip);
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
	if (subnetKey) {
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "account_signup_subnet",
			signalValue: subnetKey,
			bucket: dailyBucket(),
			metadata: { provider: context.provider || "password", local: isLocalIp(ip) }
		});
	}
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

function recordCharacterCreationSignal(store, account, playerId, username, request, context = {}) {
	const ip = clientIp(request);
	const subnetKey = ipv4SubnetKey(ip);
	const metadata = {
		local: isLocalIp(ip),
		playerId: Number(playerId || 0),
		initialSignup: Boolean(context.initialSignup)
	};
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "character_created_ip",
		signalValue: ip,
		bucket: dailyBucket(),
		metadata
	});
	if (subnetKey) {
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "character_created_subnet",
			signalValue: subnetKey,
			bucket: dailyBucket(),
			metadata
		});
	}
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "character_created_account",
		signalValue: account.id,
		bucket: dailyBucket(),
		metadata
	});
	if (!context.initialSignup) return;
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "character_signup_initial_ip",
		signalValue: ip,
		bucket: dailyBucket(),
		metadata
	});
	if (subnetKey) {
		recordAbuseSignal(store, {
			accountId: account.id,
			signalType: "character_signup_initial_subnet",
			signalValue: subnetKey,
			bucket: dailyBucket(),
			metadata
		});
	}
	recordAbuseSignal(store, {
		accountId: account.id,
		signalType: "character_signup_initial_account",
		signalValue: account.id,
		bucket: dailyBucket(),
		metadata
	});
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

async function googleProfileFromCredential(payload) {
	// Authenticate the credential before validating request shape: a garbage token
	// must answer invalid_google_token, not complain about the (absent) username.
	const credential = String(payload.credential || payload.idToken || "").trim();
	if (!credential) throw new HttpError(401, "invalid_google_token");
	const claims = await verifyGoogleIdToken(credential, payload.nonce || "");
	const requestedUsername = requireReservationUsername(payload.username || payload.reservedUsername || "");
	const emailDisplay = String(claims.email || "").trim();
	const emailCanonical = canonicalEmail(emailDisplay);
	if (!emailCanonical) throw new HttpError(400, "invalid_google_email");
	if (claims.email_verified === false || claims.email_verified === "false") {
		throw new HttpError(401, "google_email_not_verified");
	}
	return {
		provider: googleIdentityProvider,
		providerSubject: String(claims.sub || ""),
		emailCanonical,
		emailDisplay,
		displayName: cleanUsername(claims.name || claims.given_name || requestedUsername || emailDisplay.split("@")[0] || "Voidscape player"),
		avatarUrl: String(claims.picture || "").trim(),
		emailVerified: true,
		username: requestedUsername
	};
}

async function verifyGoogleIdToken(credential, nonce) {
	requireGoogleConfigured();
	const parts = String(credential || "").split(".");
	if (parts.length !== 3) throw new HttpError(401, "invalid_google_token");
	let header;
	let claims;
	try {
		header = JSON.parse(Buffer.from(parts[0], "base64url").toString("utf8"));
		claims = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8"));
	} catch (error) {
		throw new HttpError(401, "invalid_google_token");
	}
	if (header.alg !== "RS256" || !header.kid) throw new HttpError(401, "invalid_google_token");
	const keys = await googleJwks();
	const jwk = keys.get(header.kid);
	if (!jwk) throw new HttpError(401, "invalid_google_token");
	const signed = Buffer.from(`${parts[0]}.${parts[1]}`);
	const signature = Buffer.from(parts[2], "base64url");
	const publicKey = createPublicKey({ key: jwk, format: "jwk" });
	const valid = verifySignature("RSA-SHA256", signed, publicKey, signature);
	if (!valid) throw new HttpError(401, "invalid_google_token");
	const issuer = String(claims.iss || "");
	if (issuer !== "https://accounts.google.com" && issuer !== "accounts.google.com") {
		throw new HttpError(401, "invalid_google_token");
	}
	if (String(claims.aud || "") !== googleClientId) throw new HttpError(401, "invalid_google_token");
	if (!claims.sub) throw new HttpError(401, "invalid_google_token");
	const expiresAt = Number(claims.exp || 0) * 1000;
	if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) throw new HttpError(401, "invalid_google_token");
	if (nonce && claims.nonce !== nonce) throw new HttpError(401, "invalid_google_token");
	return claims;
}

async function googleJwks() {
	if (googleJwksCache.expiresAt > Date.now() && googleJwksCache.keys.size) {
		return googleJwksCache.keys;
	}
	let response;
	try {
		response = await fetch(googleJwksUrl);
	} catch (error) {
		throw new HttpError(503, "google_jwks_unavailable");
	}
	if (!response || !response.ok) throw new HttpError(503, "google_jwks_unavailable");
	const body = await response.json();
	const keys = new Map();
	(body.keys || []).forEach((key) => {
		if (key && key.kid) keys.set(key.kid, key);
	});
	if (!keys.size) throw new HttpError(503, "google_jwks_unavailable");
	const cacheControl = String(response.headers.get("cache-control") || "");
	const maxAgeMatch = cacheControl.match(/max-age=(\d+)/i);
	const maxAgeMs = maxAgeMatch ? Number(maxAgeMatch[1]) * 1000 : 60 * 60 * 1000;
	googleJwksCache = {
		expiresAt: Date.now() + Math.max(60 * 1000, Math.min(maxAgeMs, 24 * 60 * 60 * 1000)),
		keys
	};
	return keys;
}

function consumeGoogleNonce(store, nonce, request) {
	pruneOauthStates(store);
	const text = String(nonce || "").trim();
	if (!text) throw new HttpError(400, "google_nonce_invalid");
	const nonceHash = hashToken(text);
	const index = store.oauthStates.findIndex((entry) =>
		entry.provider === "google_id_token" &&
		entry.stateHash === nonceHash &&
		entry.expiresAt > Date.now()
	);
	if (index === -1) throw new HttpError(400, "google_nonce_invalid");
	const [entry] = store.oauthStates.splice(index, 1);
	if (entry.ipHash && entry.ipHash !== abuseHash(clientIp(request))) {
		audit(store, "google_nonce_ip_changed", {});
	}
	return entry;
}

function requireGoogleConfigured() {
	if (!googleClientId) throw new HttpError(503, "google_oauth_not_configured");
}

function requireReservationUsername(value) {
	const username = cleanUsername(value || "");
	if (!/^[a-zA-Z0-9 ]{2,12}$/.test(username)) {
		throw new HttpError(400, "invalid_username");
	}
	return username;
}

async function upsertGoogleAccount(store, profile, payload, request, transaction = null) {
	let identity = store.identities.find((entry) =>
		entry.provider === profile.provider &&
		entry.providerSubject === profile.providerSubject
	);
	let account = identity ? store.accounts.find((entry) => entry.id === identity.accountId) : null;
	if (!account) {
		account = store.accounts.find((entry) => entry.emailCanonical === profile.emailCanonical) || null;
	}

	if (!account) {
		assertAccountSignupIpAllowed(store, request);
		const founder = await reserveFounder(store, {
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
		}, transaction);
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

function revokeCurrentSession(request, store) {
	const auth = request.headers.authorization || "";
	const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
	if (!token) return false;
	const tokenHash = hashToken(token);
	const session = store.sessions.find((entry) => entry.tokenHash === tokenHash && !entry.revokedAt);
	if (!session) return false;
	session.expiresAt = Date.now();
	session.revokedAt = now();
	session.lastSeenAt = now();
	audit(store, "account_logout", { accountId: session.accountId, sessionId: session.id });
	return true;
}

function revokeOtherAccountSessions(store, accountId, currentSessionId) {
	let revoked = 0;
	store.sessions.forEach((entry) => {
		if (entry.accountId === accountId && entry.id !== currentSessionId && entry.expiresAt > Date.now() && !entry.revokedAt) {
			entry.expiresAt = Date.now();
			entry.revokedAt = now();
			revoked += 1;
		}
	});
	return revoked;
}

async function requireSensitiveAccountAuth(store, account, session, currentPassword) {
	if (account.passwordHash) {
		if (!(await verifyPassword(String(currentPassword || ""), account.passwordHash))) {
			throw new HttpError(401, "invalid_current_password");
		}
		return "password";
	}
	const hasFederatedIdentity = store.identities.some((entry) =>
		entry.accountId === account.id && [googleIdentityProvider, "discord"].includes(entry.provider)
	);
	const sessionCreatedAt = Date.parse(session.createdAt || "");
	if (hasFederatedIdentity && Number.isFinite(sessionCreatedAt) && Date.now() - sessionCreatedAt <= sensitiveActionWindowMs) {
		return "recent_federated_login";
	}
	throw new HttpError(401, "reauthentication_required");
}

function optionalSession(request, store) {
	const auth = request.headers.authorization || "";
	const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
	if (!token) return null;
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

async function updateStore(mutator, options = {}) {
	const execute = async () => {
		const store = await loadStore();
		recoverInterruptedEmailEvents(store);
		const originalStore = cloneJson(store);
		const transaction = createStoreTransactionContext();
		let phase = "mutate";
		try {
			const result = await mutator(store, transaction);
			phase = "persist";
			await saveStore(store);
			phase = "after_persist";
			await transaction.runAfterPersist();
			transaction.clear();
			return result;
		} catch (error) {
			if (phase === "after_persist") {
				let restoreError = null;
				let rollbackError = null;
				try {
					await saveStore(originalStore);
				} catch (current) {
					restoreError = current;
				}
				try {
					await transaction.rollback();
				} catch (current) {
					rollbackError = current;
				}
				if (restoreError || rollbackError) {
					console.error("portal_store_reconciliation_failed", {
						restore: Boolean(restoreError),
						rollback: Boolean(rollbackError)
					});
					throw new HttpError(503, "portal_store_reconciliation_failed");
				}
				if (error instanceof HttpError) throw error;
				throw new HttpError(503, "portal_external_commit_failed");
			}

			try {
				await transaction.rollback();
			} catch (rollbackError) {
				console.error("portal_transaction_compensation_failed");
				throw new HttpError(503, "portal_transaction_compensation_failed");
			}
			if (phase === "persist") {
				throw new HttpError(503, "portal_store_unavailable");
			}
			throw error;
		}
	};
	const next = writeQueue.then(() => options.lockOpenRscWrites
		? withOpenRscWriteLock(execute)
		: execute());
	writeQueue = next.catch(() => undefined);
	return next;
}

function createStoreTransactionContext() {
	const rollbackActions = [];
	const afterPersistActions = [];
	return {
		onRollback(action) {
			if (typeof action !== "function") throw new TypeError("rollback action must be a function");
			rollbackActions.push(action);
		},
		afterPersist(action) {
			if (typeof action !== "function") throw new TypeError("after-persist action must be a function");
			afterPersistActions.push(action);
		},
		async rollback() {
			let firstError = null;
			for (const action of rollbackActions.slice().reverse()) {
				try {
					await action();
				} catch (error) {
					if (!firstError) firstError = error;
				}
			}
			rollbackActions.length = 0;
			if (firstError) throw firstError;
		},
		async runAfterPersist() {
			for (const action of afterPersistActions) {
				await action();
			}
			afterPersistActions.length = 0;
		},
		clear() {
			rollbackActions.length = 0;
			afterPersistActions.length = 0;
		}
	};
}

function recoverInterruptedEmailEvents(store) {
	const cutoff = Date.now() - interruptedEmailRecoveryMs;
	for (const event of store.emailEvents || []) {
		if (event.status !== "sending" || Date.parse(event.updatedAt || "") > cutoff) continue;
		event.status = "failed";
		event.lastError = "email_delivery_interrupted";
		event.updatedAt = now();
	}
}

async function loadStore() {
	try {
		const raw = await readFile(storePath, "utf8");
		return normalizeStore(JSON.parse(raw));
	} catch (error) {
		if (error && error.code === "ENOENT") return normalizeStore({});
		console.error("portal_store_load_failed", error && error.message ? error.message : "unknown_error");
		throw new HttpError(503, "portal_store_unavailable");
	}
}

async function portalStorageHealth() {
	try {
		await loadStore();
		return { readable: true };
	} catch (error) {
		return { readable: false };
	}
}

async function saveStore(store) {
	await mkdir(dataDir, { recursive: true });
	const tmpPath = `${storePath}.${process.pid}.tmp`;
	const attempt = ++storeSaveAttempts;
	try {
		injectStoreFault("write", attempt);
		await writeFile(tmpPath, `${JSON.stringify(normalizeStore(store), null, 2)}\n`);
		injectStoreFault("rename", attempt);
		await rename(tmpPath, storePath);
	} catch (error) {
		try {
			await unlinkFile(tmpPath);
		} catch (cleanupError) {
			if (!cleanupError || cleanupError.code !== "ENOENT") {
				console.error("portal_store_temp_cleanup_failed");
			}
		}
		throw error;
	}
}

function injectStoreFault(phase, attempt) {
	if (testStoreFaultPhase === phase && testStoreFaultAt === attempt) {
		const error = new Error(`injected_portal_store_${phase}_failure`);
		error.code = "PORTAL_TEST_STORE_FAULT";
		throw error;
	}
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
			abuseSignal: Number(store.nextIds && store.nextIds.abuseSignal) || 1,
			emailEvent: Number(store.nextIds && store.nextIds.emailEvent) || 1,
			emailVerification: Number(store.nextIds && store.nextIds.emailVerification) || 1,
			passwordResetToken: Number(store.nextIds && store.nextIds.passwordResetToken) || 1,
			legacyAccountClaim: Number(store.nextIds && store.nextIds.legacyAccountClaim) || 1
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
		oauthStates: Array.isArray(store.oauthStates) ? store.oauthStates : [],
		audit: Array.isArray(store.audit) ? store.audit : [],
		abuseSignals: Array.isArray(store.abuseSignals) ? store.abuseSignals : [],
		emailEvents: Array.isArray(store.emailEvents) ? store.emailEvents : [],
		emailVerifications: Array.isArray(store.emailVerifications) ? store.emailVerifications : [],
		passwordResetTokens: Array.isArray(store.passwordResetTokens) ? store.passwordResetTokens : [],
		legacyAccountClaims: Array.isArray(store.legacyAccountClaims) ? store.legacyAccountClaims : []
	};
}

function cloneJson(value) {
	return JSON.parse(JSON.stringify(value));
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

function funnelSummary(store) {
	const clicks = store.audit
		.filter((entry) => entry.type === "funnel_click")
		.map((entry) => ({
			...entry,
			createdAtMs: Date.parse(entry.createdAt || "")
		}))
		.filter((entry) => Number.isFinite(entry.createdAtMs));
	const nowMs = Date.now();
	return {
		generatedAt: now(),
		total: clicks.length,
		last24h: summarizeFunnelWindow(clicks, nowMs - 24 * 60 * 60 * 1000),
		last7d: summarizeFunnelWindow(clicks, nowMs - 7 * 24 * 60 * 60 * 1000),
		last30d: summarizeFunnelWindow(clicks, nowMs - 30 * 24 * 60 * 60 * 1000),
		recent: clicks
			.slice(-25)
			.reverse()
			.map((entry) => ({
				event: sanitizeFunnelEvent(entry.metadata && entry.metadata.event),
				target: sanitizeFunnelText(entry.metadata && entry.metadata.target, 80),
				page: sanitizeFunnelPath(entry.metadata && entry.metadata.page),
				referrer: sanitizeFunnelReferrer(entry.metadata && entry.metadata.referrer),
				utm: sanitizeFunnelUtm(entry.metadata && entry.metadata.utm),
				createdAt: entry.createdAt
			}))
	};
}

function summarizeFunnelWindow(clicks, sinceMs) {
	const rows = clicks.filter((entry) => entry.createdAtMs >= sinceMs);
	const counts = new Map();
	for (const entry of rows) {
		const event = sanitizeFunnelEvent(entry.metadata && entry.metadata.event);
		counts.set(event, (counts.get(event) || 0) + 1);
	}
	return {
		total: rows.length,
		events: Array.from(counts.entries())
			.map(([event, count]) => ({ event, count }))
			.sort((a, b) => b.count - a.count || a.event.localeCompare(b.event))
	};
}

function trimFunnelClicks(store) {
	let remaining = 5000;
	const keep = new Array(store.audit.length).fill(true);
	for (let index = store.audit.length - 1; index >= 0; index -= 1) {
		if (store.audit[index].type !== "funnel_click") continue;
		if (remaining > 0) {
			remaining -= 1;
		} else {
			keep[index] = false;
		}
	}
	store.audit = store.audit.filter((entry, index) => keep[index]);
}

function sanitizeFunnelEvent(value) {
	const event = sanitizeFunnelText(value, 48)
		.toLowerCase()
		.replace(/[^a-z0-9_-]+/g, "_")
		.replace(/^_+|_+$/g, "");
	return funnelEvents.has(event) ? event : "click";
}

function sanitizeFunnelText(value, limit) {
	return String(value || "")
		.replace(/[\u0000-\u001F\u007F]+/g, " ")
		.replace(/\s+/g, " ")
		.trim()
		.slice(0, limit);
}

function sanitizeFunnelPath(value) {
	const text = sanitizeFunnelText(value, 220);
	return text.startsWith("/") ? text : "";
}

function sanitizeFunnelHref(value) {
	const text = sanitizeFunnelText(value, 220);
	if (!text) return "";
	if (text.startsWith("/downloads/") || text === "/transparency") return text;
	if (text === "/play/" || text === "/play") return text;
	if (androidPlayUrl && text === androidPlayUrl) return androidPlayUrl;
	try {
		const url = new URL(text);
		const webUrl = new URL(webClientUrl);
		if (url.origin === webUrl.origin && url.pathname.replace(/\/$/, "") === webUrl.pathname.replace(/\/$/, "")) {
			return `${url.origin}${url.pathname}`.slice(0, 220);
		}
	} catch (error) {
		// Ignore non-URL text.
	}
	return "";
}

function sanitizeFunnelReferrer(value) {
	const text = sanitizeFunnelText(value, 220);
	if (!text) return "";
	try {
		const url = new URL(text);
		return `${url.origin}${url.pathname}`.slice(0, 220);
	} catch (error) {
		return "";
	}
}

function sanitizeFunnelUtm(input) {
	const allowed = ["utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term"];
	const output = {};
	if (!input || typeof input !== "object") return output;
	for (const key of allowed) {
		const value = sanitizeFunnelText(input[key], 80);
		if (value) output[key] = value;
	}
	return output;
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
	if (!/^[a-zA-Z0-9]{4,20}$/.test(text)) {
		throw new HttpError(400, "invalid_game_password");
	}
	return text;
}

function requireLegacyGamePassword(password) {
	const text = String(password || "").trim().replaceAll(" ", "_");
	if (!text || text.length > 20 || /[\u0000-\u001f\u007f]/.test(text)) {
		throw new HttpError(400, "invalid_game_password");
	}
	return text;
}

function requireLaunchFirstGamePassword(password) {
	const text = String(password || "");
	if (!/^[a-zA-Z0-9]{8,20}$/.test(text)) {
		throw new HttpError(400, "invalid_game_password");
	}
	return text;
}

function canonicalGamePasswordHash(password, salt) {
	return runGamePasswordHelper("hash", password, salt);
}

function verifyCanonicalGamePassword(password, salt, encodedHash) {
	return runGamePasswordHelper("check", password, salt, encodedHash);
}

function runGamePasswordHelper(mode, password, salt, encodedHash = "") {
	return new Promise((resolveResult, rejectResult) => {
		const child = spawn(gamePasswordJavaBin, [
			"-cp",
			gamePasswordHelperClasspath,
			"com.openrsc.server.util.rsc.PortalPasswordHasher"
		], {
			stdio: ["pipe", "pipe", "pipe"]
		});
		let stdout = "";
		let stderr = "";
		let settled = false;
		const timeout = setTimeout(() => {
			if (settled) return;
			settled = true;
			child.kill("SIGKILL");
			rejectResult(new HttpError(503, "game_password_hasher_unavailable"));
		}, 15000);
		child.stdout.setEncoding("utf8");
		child.stderr.setEncoding("utf8");
		child.stdout.on("data", (chunk) => {
			if (stdout.length < 4096) stdout += chunk;
		});
		child.stderr.on("data", (chunk) => {
			if (stderr.length < 4096) stderr += chunk;
		});
		child.on("error", () => {
			if (settled) return;
			settled = true;
			clearTimeout(timeout);
			rejectResult(new HttpError(503, "game_password_hasher_unavailable"));
		});
		child.on("close", (code) => {
			if (settled) return;
			settled = true;
			clearTimeout(timeout);
			const output = stdout.trim();
			const validOutput = mode === "hash"
				? /^\$2y\$10\$[./A-Za-z0-9]{53}$/.test(output)
				: mode === "check" && (output === "true" || output === "false");
			if (code !== 0 || !validOutput) {
				if (stderr) console.error("game_password_hasher_failed", stderr.trim().slice(0, 180));
				rejectResult(new HttpError(503, "game_password_hasher_unavailable"));
				return;
			}
			resolveResult(mode === "check" ? output === "true" : output);
		});
		const encode = (value) => Buffer.from(String(value || ""), "utf8").toString("base64url");
		const lines = [mode, encode(password), encode(salt)];
		if (mode === "check") lines.push(encode(encodedHash));
		child.stdin.end(`${lines.join("\n")}\n`);
	});
}

function clientIp(request) {
	const peer = String(request.socket.remoteAddress || "127.0.0.1");
	const peerIsLocal = peer === "127.0.0.1" || peer === "::1" || peer === "::ffff:127.0.0.1";
	// x-forwarded-for is attacker-controlled on a direct connection; only trust it
	// behind an explicit proxy opt-in or from a same-host (loopback) proxy.
	const forwarded = (trustProxyHeader || peerIsLocal)
		? String(request.headers["x-forwarded-for"] || "").split(",")[0].trim()
		: "";
	const raw = forwarded || peer;
	return raw === "::1" || raw === "::ffff:127.0.0.1" ? "127.0.0.1" : raw;
}

function subscriptionState(account) {
	const expiresAt = account.subscriptionExpiresAt || 0;
	const active = expiresAt > Date.now();
	return {
		active,
		expiresAt,
		label: active ? formatRemaining(expiresAt) : "Unsubscribed",
		combatXpRate: active ? baseCombatXpRate + subscriptionXpBonus : baseCombatXpRate,
		skillXpRate: active ? baseSkillXpRate + subscriptionXpBonus : baseSkillXpRate
	};
}

function xpRates() {
	return {
		baseCombat: baseCombatXpRate,
		baseSkill: baseSkillXpRate,
		subscribedCombat: baseCombatXpRate + subscriptionXpBonus,
		subscribedSkill: baseSkillXpRate + subscriptionXpBonus
	};
}

function worldRules() {
	return {
		mode: "hybrid-p2p-enabled",
		memberWorld: true,
		membershipAccess: "global",
		subscriptionGrantsMembers: false,
		registration: "portal-first",
		packetRegistration: false
	};
}

function oauthPublicState() {
	return {
		google: {
			enabled: Boolean(googleClientId),
			clientId: googleClientId || ""
		}
	};
}

function captchaPublicState() {
	return {
		provider: captchaProvider,
		signupRequired: captchaSignupRequired,
		characterRequired: captchaCharacterRequired,
		siteKey: captchaSiteKey,
		configured: captchaConfigured()
	};
}

function captchaConfigured() {
	if (captchaBypassToken) return true;
	return captchaProvider === "turnstile" && Boolean(captchaSecret && captchaSiteKey);
}

async function requireCaptcha(request, payload, purpose) {
	const required = purpose === "character" ? captchaCharacterRequired : captchaSignupRequired;
	if (!required) return;
	if (!captchaConfigured()) {
		throw new HttpError(503, "captcha_not_configured");
	}
	const token = String(payload.captchaToken || payload.turnstileToken || "").trim();
	if (!token) {
		throw new HttpError(403, "captcha_required");
	}
	if (captchaBypassToken && constantTimeMatches(token, captchaBypassToken)) {
		return;
	}
	if (captchaBypassToken && !captchaSecret) {
		throw new HttpError(403, "captcha_failed");
	}
	if (captchaProvider !== "turnstile" || !captchaSecret) {
		throw new HttpError(503, "captcha_not_configured");
	}
	let response;
	try {
		const body = new URLSearchParams();
		body.set("secret", captchaSecret);
		body.set("response", token);
		body.set("remoteip", clientIp(request));
		response = await fetch(captchaVerifyUrl, {
			method: "POST",
			headers: { "content-type": "application/x-www-form-urlencoded" },
			body
		});
	} catch (error) {
		throw new HttpError(503, "captcha_unavailable");
	}
	if (!response.ok) {
		throw new HttpError(503, "captcha_unavailable");
	}
	let result;
	try {
		result = await response.json();
	} catch (error) {
		throw new HttpError(503, "captcha_unavailable");
	}
	if (!result || result.success !== true) {
		throw new HttpError(403, "captcha_failed");
	}
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

// No 0/O/1/I/L/U: signup codes get typed into RSC chat from memory or paper.
const signupCodeAlphabet = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

function makeSignupCode() {
	const pick = (count) => Array.from(randomBytes(count))
		.map((byte) => signupCodeAlphabet[byte % signupCodeAlphabet.length])
		.join("");
	return `VOID-${pick(4)}-${pick(4)}`;
}

function normalizeSignupCode(code) {
	return String(code || "").toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 20);
}

function signupCodeCacheKey(code) {
	const normalized = normalizeSignupCode(code);
	return normalized ? `${signupCodeCachePrefix}${normalized}` : "";
}

function signupCodeInUse(store, code) {
	const normalized = normalizeSignupCode(code);
	return store.founders.some((entry) => {
		if (entry.signupCodeNormalized === normalized) return true;
		return Array.isArray(entry.referralRewardCodes) && entry.referralRewardCodes.some((reward) =>
			normalizeSignupCode(reward && reward.code) === normalized
		);
	});
}

function ensureFounderSignupCode(store, founder) {
	if (founder.signupCode) return founder;
	let code = makeSignupCode();
	while (signupCodeInUse(store, code)) {
		code = makeSignupCode();
	}
	founder.signupCode = code;
	founder.signupCodeNormalized = normalizeSignupCode(code);
	founder.signupCodeCreatedAt = now();
	founder.signupCodeSyncedAt = null;
	audit(store, "signup_code_minted", { founderId: founder.id });
	return founder;
}

function ensureReferralRewardCode(store, referrer, referral) {
	if (!referrer || !referral) return null;
	if (!Array.isArray(referrer.referralRewardCodes)) referrer.referralRewardCodes = [];
	const existing = referrer.referralRewardCodes.find((reward) => reward.referralId === referral.id);
	if (existing) return existing;
	let code = makeSignupCode();
	while (signupCodeInUse(store, code)) {
		code = makeSignupCode();
	}
	const reward = {
		code,
		referralId: referral.id,
		referredFounderId: referral.referredFounderId,
		createdAt: now(),
		syncedAt: null
	};
	referrer.referralRewardCodes.push(reward);
	audit(store, "referral_reward_code_minted", {
		referrerFounderId: referrer.id,
		referredFounderId: referral.referredFounderId
	});
	return reward;
}

function signupState(founder) {
	if (!founder || !founder.signupCode) return null;
	return {
		code: founder.signupCode,
		redeemHint: "Talk to the Void Subscription Vendor in Lumbridge and enter this code when he asks.",
		releaseValid: true,
		syncedToGame: Boolean(founder.signupCodeSyncedAt),
		createdAt: founder.signupCodeCreatedAt || null
	};
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

function sealText(value) {
	const iv = randomBytes(12);
	const key = createHash("sha256").update(`${abuseHashSalt}:portal-seal`).digest();
	const cipher = createCipheriv("aes-256-gcm", key, iv);
	const ciphertext = Buffer.concat([
		cipher.update(String(value || ""), "utf8"),
		cipher.final()
	]);
	const tag = cipher.getAuthTag();
	return [
		"v1",
		iv.toString("base64url"),
		tag.toString("base64url"),
		ciphertext.toString("base64url")
	].join(".");
}

function unsealText(value) {
	const parts = String(value || "").split(".");
	if (parts.length !== 4 || parts[0] !== "v1") throw new HttpError(400, "invalid_sealed_value");
	const key = createHash("sha256").update(`${abuseHashSalt}:portal-seal`).digest();
	const iv = Buffer.from(parts[1], "base64url");
	const tag = Buffer.from(parts[2], "base64url");
	const ciphertext = Buffer.from(parts[3], "base64url");
	const decipher = createDecipheriv("aes-256-gcm", key, iv);
	decipher.setAuthTag(tag);
	return Buffer.concat([
		decipher.update(ciphertext),
		decipher.final()
	]).toString("utf8");
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
	let parsed;
	try {
		parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
	} catch (error) {
		throw new HttpError(400, "invalid_json");
	}
	// Every caller reads named fields off the payload, so a non-object JSON body
	// (null, an array, or a bare primitive) must be rejected here — otherwise
	// `payload.password` on null throws an uncaught TypeError → 500.
	if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
		throw new HttpError(400, "invalid_json");
	}
	return parsed;
}

function json(response, status, body) {
	response.writeHead(status, {
		"content-type": "application/json; charset=utf-8",
		"cache-control": "no-store",
		...securityHeaders("application/json")
	});
	response.end(`${JSON.stringify(body, null, 2)}\n`);
}

function securityHeaders(contentTypeValue = "") {
	return {
		"content-security-policy": [
			"default-src 'self'",
			"script-src 'self' 'unsafe-inline' https://accounts.google.com https://apis.google.com",
			"style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
			"font-src 'self' https://fonts.gstatic.com data:",
			"img-src 'self' data: https:",
			"connect-src 'self' https://accounts.google.com https://www.googleapis.com https://challenges.cloudflare.com",
			"frame-src https://accounts.google.com",
			"frame-ancestors 'none'",
			"base-uri 'self'",
			"form-action 'self'",
			"object-src 'none'"
		].join("; "),
		"cross-origin-opener-policy": "same-origin-allow-popups",
		"permissions-policy": "camera=(), microphone=(), geolocation=(), payment=()",
		"referrer-policy": "strict-origin-when-cross-origin",
		"x-content-type-options": "nosniff",
		"x-frame-options": "DENY"
	};
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
		".ttf": "font/ttf",
		".woff2": "font/woff2",
		".txt": "text/plain; charset=utf-8",
		".md": "text/markdown; charset=utf-8",
		".mp4": "video/mp4",
		".webm": "video/webm"
	}[ext] || "application/octet-stream";
}

function now() {
	return new Date().toISOString();
}
