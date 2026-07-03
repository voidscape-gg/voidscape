import { createServer } from "node:http";
import { createReadStream } from "node:fs";
import { mkdir, readFile, readdir, rename, stat, writeFile } from "node:fs/promises";
import { execFile as execFileCallback } from "node:child_process";
import { createHash, createPublicKey, randomBytes, scrypt as scryptCallback, timingSafeEqual, verify as verifySignature } from "node:crypto";
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
const defaultLaunchAtIso = "2026-07-11T18:00:00Z";
// Prelaunch lockdown: only the signup flow (plus token-gated admin) is reachable.
const publicMode = process.env.PORTAL_PUBLIC_MODE === "1";
const launchSignupMode = publicMode && process.env.PORTAL_LAUNCH_SIGNUP_MODE === "1";
const betaMode = process.env.PORTAL_BETA_MODE === "1" || process.env.PORTAL_PUBLIC_BETA === "1";
const launchOpenAtIso = configuredIsoTimestamp("PORTAL_LAUNCH_AT", process.env.PORTAL_LAUNCH_AT || process.env.PORTAL_BETA_OPEN_AT || defaultLaunchAtIso);
const betaOpenAtIso = configuredIsoTimestamp("PORTAL_BETA_OPEN_AT", process.env.PORTAL_BETA_OPEN_AT || process.env.PORTAL_LAUNCH_AT || "");
const betaSignupCounterBase = configuredNonNegativeInteger("PORTAL_BETA_COUNTER_BASE", process.env.PORTAL_BETA_COUNTER_BASE || "132");
const betaSignupCounterStartedAtIso = configuredBetaSignupCounterStartedAt();
const betaSignupCounterSeed = process.env.PORTAL_BETA_COUNTER_SEED || "voidscape-public-beta";
const dataDir = process.env.PORTAL_DATA_DIR || join(tmpdir(), "voidscape-portal-api");
const storePath = join(dataDir, "dev-store.json");
const integritySnapshotPath = process.env.PORTAL_INTEGRITY_SNAPSHOT || join(dataDir, "integrity-summary.json");
const buildMetadataPath = process.env.PORTAL_BUILD_META || join(rootDir, "build-meta.json");
const sourceRepositoryUrl = process.env.PORTAL_SOURCE_URL || process.env.PORTAL_REPOSITORY_URL || "https://github.com/voidscape-gg/voidscape";
const openRscDbPath = process.env.PORTAL_OPENRSC_DB || process.env.OPENRSC_SQLITE_DB || "";
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
const accountSubscriptionCachePrefix = "acct_sub:";
const signupCodeCachePrefix = "signup_code:";
const signupCodeAvailable = 1;
const signupCodeRedeemed = 2;
const starterFreeSubscriptionType = "starter_free_subscription";
const baseCombatXpRate = 10;
const baseSkillXpRate = 2;
const sha256Cache = new Map();
const subscriptionXpBonus = 1;
const signupIpDailyLimit = Math.max(1, Number(process.env.PORTAL_SIGNUP_IP_DAILY_LIMIT || 10));
const starterIpDailyLimit = Math.max(1, Number(process.env.PORTAL_STARTER_IP_DAILY_LIMIT || signupIpDailyLimit));
const loginIpFailureLimit = Math.max(1, Number(process.env.PORTAL_LOGIN_IP_FAILURE_LIMIT || 10));
const loginFailureWindowMs = Math.max(1, Number(process.env.PORTAL_LOGIN_FAILURE_WINDOW_MINUTES || 15)) * 60 * 1000;
const abuseSignalTtlMs = 1000 * 60 * 60 * 24 * 90;
const defaultAbuseHashSalt = "voidscape-portal-dev";
const abuseHashSaltInput = process.env.PORTAL_ABUSE_HASH_SALT || "";
const abuseHashSalt = abuseHashSaltInput || defaultAbuseHashSalt;
const adminToken = process.env.PORTAL_ADMIN_TOKEN || "";
const abuseHashSaltConfigured = configuredSecret(abuseHashSaltInput, defaultAbuseHashSalt);
const adminTokenConfigured = configuredSecret(adminToken, "");
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
const webClientUrl = process.env.PORTAL_WEB_CLIENT_URL || "https://voidscape.gg/play/";
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
		path: configuredDownloadPath("PORTAL_ANDROID_APK", join(repoRoot, "Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk")),
		contentType: "application/vnd.android.package-archive"
	}
];
const funnelEvents = new Set([
	"play_web",
	"download_launcher",
	"download_android",
	"download",
	"discord_rewards",
	"transparency",
	"click"
]);
const clientCacheDir = join(repoRoot, "Client_Base/Cache");
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

function configuredDownloadPath(envName, fallbackPath) {
	const configuredPath = process.env[envName];
	return configuredPath ? resolve(repoRoot, configuredPath) : fallbackPath;
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

function configuredSecret(value, fallback) {
	const text = String(value || "").trim();
	if (!text || text === fallback) return false;
	const lowered = text.toLowerCase();
	if (lowered === "dev" || lowered === "test" || lowered === "dev-admin") return false;
	if (lowered.includes("change_me") || lowered.includes("changeme")) return false;
	return text.length >= 16;
}

function portalConfigHealth() {
	const issues = [];
	if (publicMode && !abuseHashSaltConfigured) {
		issues.push("abuse_hash_salt_not_configured");
	}
	if (adminToken && !adminTokenConfigured) {
		issues.push("admin_token_weak");
	}
	return {
		publicReady: issues.length === 0,
		abuseHashSaltConfigured,
		adminTokenConfigured,
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
		{ group: "Onboarding", label: "Void Island Herald", value: "24, 24", note: "Choose starter path; later routes to Void Rush." },
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
			|| (method === "POST" && url.pathname === "/api/funnel/click")
			|| (!betaMode && method === "POST" && url.pathname === "/api/founder/reservations")
			|| (launchSignupMode && method === "GET" && url.pathname === "/api/account")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/register")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/google")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/login")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/accounts/recover-password")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/oauth/google/nonce")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/characters")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/password")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/recovery-codes")
			|| (launchSignupMode && method === "POST" && url.pathname === "/api/security/sessions/revoke-others")
			|| (betaMode && method === "GET" && url.pathname === "/api/account")
			|| (betaMode && method === "GET" && url.pathname === "/api/oauth/discord/start")
			|| (betaMode && method === "GET" && url.pathname === "/api/oauth/discord/callback")
			|| url.pathname.startsWith("/api/admin/");
		if (!allowed) {
			throw new HttpError(404, "not_available_during_prelaunch");
		}
	}

	if (method === "GET" && url.pathname === "/api/health") {
		json(response, 200, {
			ok: true,
			service: "voidscape-portal-dev",
			publicMode,
			launchSignupMode,
			storage: {
				durable: Boolean(process.env.PORTAL_DATA_DIR),
				tempDirOverride: process.env.PORTAL_ALLOW_TMPDIR === "1"
			},
			openRscDb: {
				configured: Boolean(openRscDbPath)
			},
			config: portalConfigHealth()
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

	if (method === "POST" && url.pathname === "/api/funnel/click") {
		const payload = await readJson(request);
		const result = await recordFunnelClick(request, payload);
		json(response, 202, result);
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
		if (betaMode) {
			throw new HttpError(404, "discord_beta_required");
		}
		const payload = await readJson(request);
		const ip = clientIp(request);
		const result = await updateStore(async (store) => {
			if (!isLocalIp(ip)) {
				const since = Date.now() - 1000 * 60 * 60 * 24;
				if (countAbuseSignals(store, "founder_signup_ip", ip, since) >= signupIpDailyLimit) {
					throw new HttpError(429, "rate_limited");
				}
			}
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
			try {
				if (await syncSignupCodeToOpenRsc(founder) && !founder.signupCodeSyncedAt) {
					founder.signupCodeSyncedAt = now();
				}
			} catch (error) {
				audit(store, "signup_code_sync_failed", { founderId: founder.id, error: String(error && error.message || error) });
			}
			const rewardReferrer = founder.referredByCode
				? store.founders.find((entry) => entry.code === founder.referredByCode)
				: null;
			try {
				await syncReferralRewardCodesToOpenRsc(rewardReferrer);
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
		const password = requirePassword(payload.password);
		if (launchSignupMode) {
			requireLaunchFirstGamePassword(password);
		}
		const passwordHash = await hashPassword(password);
		const result = await updateStore(async (store) => {
			const emailCanonical = canonicalEmail(payload.email || "");
			if (!emailCanonical) throw new HttpError(400, "invalid_email");
			if (store.accounts.some((account) => account.emailCanonical === emailCanonical)) {
				throw new HttpError(409, "account_exists");
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
			if (launchSignupMode) {
				await createLaunchFirstCharacter(store, account, {
					username: founder.username,
					gamePassword: password
				}, request);
			} else {
				const reservedCharacter = createCharacter(store, account.id, founder.username, "warrior");
				reservedCharacter.source = "founder-reserved";
				reservedCharacter.status = "Reserved username";
			}
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

	if (method === "POST" && url.pathname === "/api/accounts/google") {
		requireGoogleConfigured();
		const payload = await readJson(request);
		const profile = await googleProfileFromCredential(payload);
		const result = await updateStore(async (store) => {
			consumeGoogleNonce(store, payload.nonce || "", request);
			const account = await upsertGoogleAccount(store, profile, payload, request);
			if (launchSignupMode || payload.gamePassword || payload.characterPassword) {
				await createLaunchFirstCharacter(store, account, {
					username: payload.username || payload.reservedUsername || profile.username,
					gamePassword: payload.gamePassword || payload.characterPassword
				}, request);
			}
			const token = createSession(store, account.id);
			audit(store, "account_google_login", {
				accountId: account.id,
				email: profile.emailCanonical
			});
			return {
				token,
				auth: {
					provider: googleIdentityProvider,
					emailVerified: profile.emailVerified
				},
				...(await accountState(store, account))
			};
		});
		json(response, 200, result);
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
		const ip = clientIp(request);
		const result = await updateStore(async (store) => {
			if (!isLocalIp(ip)) {
				const since = Date.now() - loginFailureWindowMs;
				if (countAbuseSignals(store, "login_failure_ip", ip, since) >= loginIpFailureLimit) {
					throw new HttpError(429, "rate_limited");
				}
			}
			const emailCanonical = canonicalEmail(payload.email || "");
			const account = store.accounts.find((entry) => entry.emailCanonical === emailCanonical);
			if (!account || !(await verifyPassword(String(payload.password || ""), account.passwordHash))) {
				// updateStore only persists when the mutator resolves, so the failure
				// signal must ride a returned marker; the 401 is thrown outside.
				recordAbuseSignal(store, {
					signalType: "login_failure_ip",
					signalValue: ip,
					bucket: dailyBucket(),
					metadata: { local: isLocalIp(ip) }
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

	if (method === "GET" && url.pathname === "/api/admin/funnel") {
		requireAdmin(request);
		const store = await loadStore();
		json(response, 200, funnelSummary(store));
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
				"cache-control": "no-store"
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

async function startDiscordOAuth(request, response, url) {
	requireDiscordOAuthConfig();
	requireDiscordBetaGuildConfig();
	const state = randomBytes(24).toString("base64url");
	const returnTo = safeReturnTo(url.searchParams.get("returnTo") || "/#account");
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
		"cache-control": "no-store"
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
			returnTo: "/#account"
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
	const returnTo = safeReturnTo(result.returnTo || "/#account");
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
				target = "/?discord_error=" + encodeURIComponent(error) + "#account";
			}
			window.location.replace(target);
		})();
	</script>
</body>
</html>`;
	response.writeHead(error ? 400 : 200, {
		"content-type": "text/html; charset=utf-8",
		"content-length": Buffer.byteLength(body),
		"cache-control": "no-store"
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
	if (!text || text.startsWith("//") || /^[a-z][a-z0-9+.-]*:/i.test(text)) return "/#account";
	return text.startsWith("/") ? text : "/#account";
}

async function serveStatic(request, response, pathname) {
	const targetPath = pathname === "/"
		? "/index.html"
		: pathname === "/privacy"
			? "/privacy.html"
			: pathname === "/data-deletion"
				? "/data-deletion.html"
				: pathname === "/features"
					? "/features.html"
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

async function createLaunchFirstCharacter(store, account, payload, request) {
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
	}, request);
}

async function createAccountCharacter(store, account, payload, request) {
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
	return {
		openAt: launchOpenAtIso,
		now: new Date().toISOString(),
		remainingMs,
		locked: remainingMs > 0
	};
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

	return {
		starterSubscriptionCards,
		starterSubscriptionCardsClaimed,
		starterCardStatus,
		starterCardLedger: {
			synced: hasGameLedger,
			status: ledger.status
		},
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
			status: {
				world: launchSchedule && launchSchedule.locked ? "Launch Countdown" : launchOpen ? "Launch Open" : "Voidscape",
				online: false,
				playersOnline: 0,
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
		return { status: "source_pending", repositoryUrl: sourceRepositoryUrl, commit: "", shortCommit: "", branch: "", dirty: false };
	}
	const commit = safeCommitHash(input.commit);
	return {
		status: sanitizeIntegrityLabel(input.status, "source_pending"),
		repositoryUrl: safeHttpUrl(input.repositoryUrl) || sourceRepositoryUrl,
		commit,
		shortCommit: String(input.shortCommit || commit.slice(0, 12)).slice(0, 16),
		branch: sanitizeSourceText(input.branch, 64),
		dirty: input.dirty === true,
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
		label: "Mobile web client",
		state: "iOS and Android browsers",
		url: webClientUrl,
		available: true,
		publicDownload: true,
		external: true,
		mobileOnly: true
	}];
	for (const artifact of downloadArtifacts) {
		if (!includePrivate && artifact.publicDownload === false) {
			continue;
		}
		try {
			const fileStat = await stat(artifact.path);
			const sha256 = await sha256File(artifact.path, fileStat);
			rows.push({
				slug: artifact.slug,
				label: artifact.label,
				state: `Built ${formatBytes(fileStat.size)}`,
				url: `/downloads/${artifact.slug}`,
				available: true,
				publicDownload: artifact.publicDownload !== false,
				primary: artifact.slug === "launcher",
				sizeBytes: fileStat.size,
				updatedAt: fileStat.mtime.toISOString(),
				sha256
			});
		} catch (error) {
			rows.push({
				slug: artifact.slug,
				label: artifact.label,
				state: "Run scripts/build.sh",
				url: "#",
				available: false,
				publicDownload: artifact.publicDownload !== false,
				primary: artifact.slug === "launcher"
			});
		}
	}
	return rows;
}

async function buildProofState() {
	const artifacts = await downloadState({ includePrivate: true, publicSurface: true });
	const publicArtifacts = artifacts
		.filter((artifact) => artifact.publicDownload !== false)
		.map((artifact) => ({
			slug: artifact.slug,
			label: artifact.label,
			url: artifact.url,
			available: artifact.available === true,
			publicDownload: true,
			sizeBytes: nonNegativeInteger(artifact.sizeBytes),
			updatedAt: validIsoTimestamp(artifact.updatedAt) || "",
			sha256: safeSha256(artifact.sha256)
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

async function syncStarterCardToOpenRsc(account) {
	if (!openRscDbPath || !account) return;
	const key = starterCardCacheKey(account.id);
	if (!key) return;

	const current = await starterCardLedgerState(account);
	if (current.status === "claimed") {
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

async function syncSignupCodeToOpenRsc(founder) {
	if (!openRscDbPath || !founder || !founder.signupCode) return false;
	return syncSignupCodeValueToOpenRsc(founder.signupCode);
}

async function syncSignupCodeValueToOpenRsc(code) {
	if (!openRscDbPath || !code) return false;
	const key = signupCodeCacheKey(code);
	if (!key) return false;

	const current = await sqliteJson(`
		SELECT value
		FROM player_cache
		WHERE playerID = 0 AND key = ${sqlString(key)}
		ORDER BY dbid DESC
		LIMIT 1
	`);
	if (current.length && Number(current[0].value) === signupCodeRedeemed) {
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
	return true;
}

async function syncReferralRewardCodesToOpenRsc(founder) {
	if (!openRscDbPath || !founder || !Array.isArray(founder.referralRewardCodes)) return 0;
	let synced = 0;
	for (const reward of founder.referralRewardCodes) {
		if (!reward || reward.syncedAt) continue;
		if (await syncSignupCodeValueToOpenRsc(reward.code)) {
			reward.syncedAt = now();
			synced += 1;
		}
	}
	return synced;
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
			10
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
		oauthStates: Array.isArray(store.oauthStates) ? store.oauthStates : [],
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

function requireLaunchFirstGamePassword(password) {
	const text = String(password || "");
	if (!/^[a-zA-Z0-9]{8,20}$/.test(text)) {
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
