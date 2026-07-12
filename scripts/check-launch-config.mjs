#!/usr/bin/env node
import { readFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const { configPath, expectedClientVersion } = parseArgs(process.argv.slice(2));
const absoluteConfigPath = resolve(root, configPath);
const failures = [];

const configText = await readFile(absoluteConfigPath, "utf8");
const values = parseConfig(configText);
const clientVersion = expectedClientVersion || await readClientVersion();

const required = new Map([
	["db_name", "voidscape"],
	["server_name", "Voidscape"],
	["server_name_welcome", "Voidscape"],
	["client_version", clientVersion],
	["enforce_custom_client_version", "true"],
	["game_tick", "640"],
	["idle_timer", "600000"],
	["idle_timer_subscriber", "900000"],
	["avatar_generator", "false"],
	["member_world", "true"],
	["want_pcap_logging", "false"],
	["combat_exp_rate", "10"],
	["skilling_exp_rate", "1.5"],
	["wilderness_spawn_multiplier", "1.5"],
	["want_fatigue", "false"],
	["aggro_range", "4"],
	["is_localhost_restricted", "true"],
	["restrict_item_id", "9999"],
	["character_creation_mode", "0"],
	["want_email", "false"],
	["ranged_gives_xp_hit", "true"],
	["melee_gives_xp_hit", "true"],
	["want_custom_ui", "true"],
	["spawn_auction_npcs", "true"],
	["want_world_announcements", "true"],
	["want_world_milestone_announcements", "true"],
	["want_world_skulled_pk_announcements", "true"],
	["want_world_achievements", "true"],
	["world_achievement_season_id", "launch-2026"],
	["world_pk_loot_minimum", "5000"],
	["want_cracker_campaign", "true"],
	["cracker_campaign_npc_kill_denominator", "500"],
	["cracker_campaign_skilling_denominator", "1000"],
	["want_beta_onboarding_guide", "false"],
	["production_command_lockdown", "true"],
	["want_void_enclave", "true"],
	["want_void_colossus", "false"],
	["want_void_dungeon", "true"],
	["want_global_chat", "true"],
	["want_global_chat_country_flags", "true"],
	["wilderness_npc_blocking", "0"],
	["custom_landscape", "true"],
	["want_equipment_tab", "false"],
	["want_bank_presets", "true"],
	["want_leftclick_webs", "true"],
	["more_shafts_per_better_log", "true"],
	["want_packet_register", "true"],
	["want_custom_banks", "false"],
	["want_bank_notes", "true"],
	["want_cert_as_notes", "true"]
]);

for (const [key, expected] of required) {
	const actual = values.get(key);
	if (actual !== expected) {
		failures.push(`${key}: expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual ?? null)}`);
	}
}

const launchCardCutoff = values.get("launch_subscription_card_until");
if (launchCardCutoff && launchCardCutoff !== "0") {
	failures.push("launch_subscription_card_until must be absent/empty; launch cards are reserved for the cutover cohort");
}

for (const [key, value] of values) {
	if (/discord_.*webhook_url/i.test(key) && value !== "null" && value !== "") {
		failures.push(`${key}: tracked launch configs must not contain webhook credentials`);
	}
}

if (/https:\/\/discord\.com\/api\/webhooks\//i.test(configText)) {
	failures.push("tracked launch config contains a Discord webhook URL");
}
if (/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/.test(configText)) {
	failures.push("tracked launch config contains a private key");
}

const result = {
	ok: failures.length === 0,
	config: configPath,
	clientVersion,
	checkedKeys: required.size,
	failures
};

console.log(JSON.stringify(result, null, 2));
if (failures.length) process.exit(1);

function parseConfig(text) {
	const parsed = new Map();
	for (const line of text.split(/\r?\n/)) {
		const match = line.match(/^\s*([A-Za-z0-9_]+)\s*:\s*([^#]*?)(?:\s+#.*)?$/);
		if (!match) continue;
		const key = match[1];
		const value = unquote(match[2].trim());
		if (parsed.has(key)) {
			throw new Error(`Duplicate config key ${key}`);
		}
		parsed.set(key, value);
	}
	return parsed;
}

function unquote(value) {
	if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) ||
		(value.startsWith("'") && value.endsWith("'")))) {
		return value.slice(1, -1);
	}
	return value;
}

async function readClientVersion() {
	const source = await readFile(resolve(root, "Client_Base/src/orsc/Config.java"), "utf8");
	const match = source.match(/CLIENT_VERSION\s*=\s*(\d+)/);
	if (!match) throw new Error("Could not read CLIENT_VERSION from Client_Base/src/orsc/Config.java");
	return match[1];
}

function parseArgs(argv) {
	let config = "server/voidscape-launch.conf";
	let version = "";
	for (let index = 0; index < argv.length; index += 1) {
		const arg = argv[index];
		if (arg === "--expected-client-version") {
			version = argv[++index] || "";
			if (!/^\d+$/.test(version)) throw new Error("--expected-client-version requires an integer");
		} else if (arg === "--help" || arg === "-h") {
			console.log("Usage: scripts/check-launch-config.mjs [config] [--expected-client-version VERSION]");
			process.exit(0);
		} else if (arg.startsWith("-")) {
			throw new Error(`Unknown option: ${arg}`);
		} else {
			config = arg;
		}
	}
	return { configPath: config, expectedClientVersion: version };
}
