#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const API_BASE = "https://discord.com/api/v10";
const REPO_ROOT = path.resolve(__dirname, "..");

const FLAGS = new Set(process.argv.slice(2));
const APPLY = FLAGS.has("--apply");
const HELP = FLAGS.has("--help") || FLAGS.has("-h");
const SKIP_BRANDING = FLAGS.has("--skip-branding");
const SKIP_AUTOMOD = FLAGS.has("--skip-automod");

const token = process.env.DISCORD_BOT_TOKEN;
const guildId = process.env.DISCORD_GUILD_ID;

const CHANNEL_TYPE = {
  GUILD_TEXT: 0,
  GUILD_VOICE: 2,
  GUILD_CATEGORY: 4,
  GUILD_ANNOUNCEMENT: 5,
  GUILD_FORUM: 15,
};

const PERM = {
  CREATE_INSTANT_INVITE: 1n << 0n,
  KICK_MEMBERS: 1n << 1n,
  BAN_MEMBERS: 1n << 2n,
  ADMINISTRATOR: 1n << 3n,
  MANAGE_CHANNELS: 1n << 4n,
  MANAGE_GUILD: 1n << 5n,
  ADD_REACTIONS: 1n << 6n,
  VIEW_AUDIT_LOG: 1n << 7n,
  PRIORITY_SPEAKER: 1n << 8n,
  VIEW_CHANNEL: 1n << 10n,
  SEND_MESSAGES: 1n << 11n,
  MANAGE_MESSAGES: 1n << 13n,
  EMBED_LINKS: 1n << 14n,
  ATTACH_FILES: 1n << 15n,
  READ_MESSAGE_HISTORY: 1n << 16n,
  MENTION_EVERYONE: 1n << 17n,
  CONNECT: 1n << 20n,
  SPEAK: 1n << 21n,
  USE_VAD: 1n << 25n,
  CHANGE_NICKNAME: 1n << 26n,
  MANAGE_NICKNAMES: 1n << 27n,
  MANAGE_ROLES: 1n << 28n,
  MANAGE_WEBHOOKS: 1n << 29n,
  MANAGE_THREADS: 1n << 34n,
  CREATE_PUBLIC_THREADS: 1n << 35n,
  CREATE_PRIVATE_THREADS: 1n << 36n,
  SEND_MESSAGES_IN_THREADS: 1n << 38n,
  MODERATE_MEMBERS: 1n << 40n,
};

const AUTO_MOD_EVENT_MESSAGE_SEND = 1;
const AUTO_MOD_TRIGGER_KEYWORD = 1;
const AUTO_MOD_TRIGGER_SPAM = 3;
const AUTO_MOD_TRIGGER_KEYWORD_PRESET = 4;
const AUTO_MOD_TRIGGER_MENTION_SPAM = 5;
const AUTO_MOD_ACTION_BLOCK_MESSAGE = 1;
const AUTO_MOD_ACTION_SEND_ALERT = 2;
const AUTO_MOD_ACTION_TIMEOUT = 3;
const AUTO_MOD_PRESET_PROFANITY = 1;
const AUTO_MOD_PRESET_SEXUAL_CONTENT = 2;
const AUTO_MOD_PRESET_SLURS = 3;

const plannedActions = [];
const manualFollowUps = new Set();

const roleDefs = [
  { name: "Owner", color: "#f4d06f", permissions: ["ADMINISTRATOR"], hoist: true },
  { name: "Admin", color: "#d8d8df", permissions: ["ADMINISTRATOR"], hoist: true },
  {
    name: "Moderator",
    color: "#8f62ff",
    hoist: true,
    permissions: [
      "KICK_MEMBERS",
      "BAN_MEMBERS",
      "MODERATE_MEMBERS",
      "MANAGE_MESSAGES",
      "MANAGE_THREADS",
      "VIEW_AUDIT_LOG",
    ],
  },
  { name: "Community Helper", color: "#31c48d", permissions: ["MANAGE_THREADS"] },
  { name: "Content Creator", color: "#ff5ca8", permissions: [] },
  { name: "Veteran", color: "#55a7ff", permissions: [] },
  { name: "Member", color: "#b8beca", permissions: [] },
  { name: "Muted", color: "#4a4f58", permissions: [] },
  { name: "PvP", color: "#ff4d5a", permissions: [] },
  { name: "PvE", color: "#43d17b", permissions: [] },
  { name: "Trading", color: "#f4c247", permissions: [] },
  { name: "Clans", color: "#4aa3ff", permissions: [] },
  { name: "Guides", color: "#9ee37d", permissions: [] },
  { name: "Media", color: "#ff78c6", permissions: [] },
  { name: "Patch Notes", color: "#9da7ff", permissions: [] },
  { name: "Events", color: "#ff9c3a", permissions: [] },
  { name: "Polls", color: "#56d6e5", permissions: [] },
];

const forumDefs = {
  suggestions: {
    tags: [
      "content",
      "balance",
      "ui",
      "economy",
      "pvp",
      "quality-of-life",
      "needs-review",
      "accepted",
      "declined",
      "shipped",
    ],
    starter: {
      title: "How to post suggestions",
      body: [
        "Use one idea per post.",
        "",
        "Template:",
        "```text",
        "Problem:",
        "Proposed change:",
        "Why it helps:",
        "Possible downside:",
        "Related screenshots/examples:",
        "```",
      ].join("\n"),
    },
  },
  "bug-reports": {
    tags: [
      "client",
      "android",
      "server",
      "combat",
      "bank",
      "auction-house",
      "wilderness",
      "visual",
      "abusable",
      "fixed",
    ],
    starter: {
      title: "Bug report template",
      body: [
        "Use this format so staff can reproduce the issue quickly.",
        "",
        "```text",
        "Client/platform:",
        "Character name:",
        "Where it happened:",
        "Steps to reproduce:",
        "Expected result:",
        "Actual result:",
        "Screenshots/video:",
        "Can this be abused? yes/no/unsure",
        "```",
      ].join("\n"),
    },
  },
  guides: {
    tags: ["new-player", "skilling", "pvm", "pvp", "money-making", "wilderness", "systems"],
    starter: {
      title: "Guide format",
      body: [
        "Good guide posts should include requirements, setup, route, risk level, and screenshots when useful.",
      ].join("\n"),
    },
  },
  "help-desk": {
    tags: ["account", "download", "android", "launcher", "gameplay", "resolved"],
    starter: {
      title: "How to use help-desk",
      body: [
        "Ask one question per post. Include your client type, device/OS, and what you already tried.",
      ].join("\n"),
    },
  },
};

const pinnedMessages = {
  welcome: [
    "Welcome to Voidscape.",
    "",
    "This is the official community hub for players, testers, creators, and staff. Start with #rules, grab the client from #downloads, and use #questions if anything is unclear.",
    "",
    "Useful places:",
    "- #announcements for major updates",
    "- #server-status for uptime and maintenance",
    "- #bug-reports for reproducible issues",
    "- #suggestions for feature ideas",
    "- #media for screenshots and clips",
  ].join("\n"),
  rules: [
    "Voidscape Rules",
    "",
    "1. Respect other players. No harassment, hate speech, threats, or personal attacks.",
    "2. No doxxing, leaking private information, impersonation, phishing, or social engineering.",
    "3. No spam, invite spam, scam links, malicious files, or disruptive self-promo.",
    "4. Keep channels on topic. Use threads/forum posts for longer discussions.",
    "5. Do not sell, buy, or advertise real-money trading, account sales, cheats, exploits, or stolen goods.",
    "6. Report abusable bugs privately through #help-desk or staff contact. Do not post exploit steps publicly.",
    "7. Follow Discord Terms and Community Guidelines.",
    "8. Staff may remove content or restrict access when needed to protect the community.",
  ].join("\n"),
  downloads: [
    "Downloads",
    "",
    "Use this channel for current Voidscape client links and install notes.",
    "",
    "Before posting a support request:",
    "- Confirm you downloaded the latest build.",
    "- Include your platform: Windows, macOS, Linux, or Android.",
    "- Include screenshots of any error messages.",
    "",
    "Never download Voidscape clients from random Discord DMs or unofficial mirrors.",
  ].join("\n"),
  "server-status": [
    "Server Status",
    "",
    "Staff will post maintenance windows, restarts, and known incidents here.",
    "",
    "Status format:",
    "- Online",
    "- Degraded",
    "- Restarting",
    "- Maintenance",
    "- Investigating",
  ].join("\n"),
  announcements: [
    "Announcement Template",
    "",
    "```text",
    "Title:",
    "Summary:",
    "What changed:",
    "Known issues:",
    "Player action needed:",
    "Date:",
    "```",
  ].join("\n"),
  trading: [
    "Trading Rules",
    "",
    "- Be clear about item, quantity, and price.",
    "- No real-money trading.",
    "- No trust trades.",
    "- Use in-game trade screens and verify items before accepting.",
    "- Staff will not enforce vague verbal deals.",
  ].join("\n"),
  pking: [
    "Wilderness and PKing",
    "",
    "Use this channel for fights, clans, risk discussion, and Wilderness clips.",
    "",
    "Keep it competitive, not personal. Trash talk is not a license to harass people across channels or DMs.",
  ].join("\n"),
  "appeals-info": [
    "Appeals",
    "",
    "If you need to appeal a mute, timeout, kick, ban, or Discord moderation action, contact an Admin or Moderator privately.",
    "",
    "Include:",
    "- Discord username",
    "- In-game character name, if relevant",
    "- What happened",
    "- Why you believe the action should be reviewed",
    "- Any screenshots or context",
    "",
    "Do not argue appeals in public channels.",
  ].join("\n"),
};

function usage() {
  console.log(`Voidscape Discord setup bot

Dry run:
  DISCORD_BOT_TOKEN='...' DISCORD_GUILD_ID='...' node scripts/setup-discord.js

Apply changes:
  DISCORD_BOT_TOKEN='...' DISCORD_GUILD_ID='...' node scripts/setup-discord.js --apply

Options:
  --apply           Create/update Discord objects. Without this, mutating calls are printed only.
  --skip-branding   Do not upload icon/banner/splash.
  --skip-automod    Do not create or update AutoMod rules.
  --help            Show this help.

The bot is standalone. It does not connect Discord to the game server or OpenRSC code.`);
}

if (HELP) {
  usage();
  process.exit(0);
}

if (!token || !guildId) {
  usage();
  console.error("\nMissing DISCORD_BOT_TOKEN or DISCORD_GUILD_ID.");
  process.exit(1);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function bitset(names) {
  return names.reduce((value, name) => {
    if (!PERM[name]) {
      throw new Error(`Unknown permission ${name}`);
    }
    return value | PERM[name];
  }, 0n).toString();
}

function colorInt(hex) {
  return Number.parseInt(hex.replace("#", ""), 16);
}

function overwrite(id, allowNames = [], denyNames = []) {
  return {
    id,
    type: 0,
    allow: bitset(allowNames),
    deny: bitset(denyNames),
  };
}

function normalizeName(name) {
  return name.toLowerCase();
}

function textChannelName(name) {
  return name.toLowerCase().replace(/[^a-z0-9-]+/g, "-").replace(/^-+|-+$/g, "");
}

function loadImageDataUri(relativePath) {
  const filePath = path.join(REPO_ROOT, relativePath);
  if (!fs.existsSync(filePath)) {
    manualFollowUps.add(`Upload ${relativePath} manually; the local file was not found.`);
    return undefined;
  }
  const data = fs.readFileSync(filePath);
  return `data:image/png;base64,${data.toString("base64")}`;
}

function summarizeBody(body) {
  if (!body) {
    return "";
  }
  const clone = JSON.parse(JSON.stringify(body));
  for (const key of ["icon", "banner", "splash"]) {
    if (clone[key]) {
      clone[key] = `<${key} image data>`;
    }
  }
  return ` ${JSON.stringify(clone, null, 2)}`;
}

class DiscordApi {
  constructor(botToken, dryRun) {
    this.botToken = botToken;
    this.dryRun = dryRun;
    this.fakeId = 1000000000000000000n;
  }

  nextFakeId() {
    this.fakeId += 1n;
    return `dry-run-${this.fakeId.toString()}`;
  }

  async request(method, route, body = undefined) {
    const mutating = method !== "GET";
    if (this.dryRun && mutating) {
      plannedActions.push(`${method} ${route}${summarizeBody(body)}`);
      if (method === "POST") {
        return { id: this.nextFakeId(), ...body };
      }
      return { id: route.split("/").pop(), ...body };
    }

    let attempt = 0;
    for (;;) {
      const response = await fetch(`${API_BASE}${route}`, {
        method,
        headers: {
          Authorization: `Bot ${this.botToken}`,
          ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
      });

      const text = await response.text();
      const data = text ? parseJson(text) : null;

      if (response.status === 429 && data && typeof data.retry_after === "number" && attempt < 5) {
        attempt += 1;
        await sleep(Math.ceil(data.retry_after * 1000) + 250);
        continue;
      }

      if (!response.ok) {
        const message = data ? JSON.stringify(data) : text;
        throw new Error(`${method} ${route} failed: HTTP ${response.status} ${message}`);
      }

      return data;
    }
  }
}

function parseJson(text) {
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function bestEffort(label, fn) {
  try {
    return await fn();
  } catch (error) {
    console.warn(`WARN: ${label}: ${error.message}`);
    manualFollowUps.add(`${label}: ${error.message}`);
    return null;
  }
}

function publicCategoryOverwrites(ids) {
  return [
    overwrite(guildId, ["VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "ADD_REACTIONS", "CREATE_PUBLIC_THREADS", "SEND_MESSAGES_IN_THREADS"], ["MENTION_EVERYONE"]),
    overwrite(ids.Muted, [], ["SEND_MESSAGES", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "SEND_MESSAGES_IN_THREADS", "ADD_REACTIONS", "SPEAK"]),
  ].filter((entry) => entry.id);
}

function startHereOverwrites(ids) {
  return [
    overwrite(guildId, ["VIEW_CHANNEL", "READ_MESSAGE_HISTORY"], ["SEND_MESSAGES", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "SEND_MESSAGES_IN_THREADS", "MENTION_EVERYONE"]),
    overwrite(ids.Owner, ["SEND_MESSAGES", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
    overwrite(ids.Admin, ["SEND_MESSAGES", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
    overwrite(ids.Moderator, ["SEND_MESSAGES", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
    overwrite(ids.Muted, [], ["SEND_MESSAGES", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "SEND_MESSAGES_IN_THREADS", "ADD_REACTIONS", "SPEAK"]),
  ].filter((entry) => entry.id);
}

function readOnlyOverwrites(ids) {
  return startHereOverwrites(ids);
}

function publicVoiceOverwrites(ids) {
  return [
    overwrite(guildId, ["VIEW_CHANNEL", "CONNECT", "SPEAK", "USE_VAD"], ["PRIORITY_SPEAKER"]),
    overwrite(ids.Muted, [], ["SPEAK"]),
  ].filter((entry) => entry.id);
}

function staffOverwrites(ids) {
  return [
    overwrite(guildId, [], ["VIEW_CHANNEL"]),
    overwrite(ids.Owner, ["VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
    overwrite(ids.Admin, ["VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
    overwrite(ids.Moderator, ["VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "MANAGE_MESSAGES", "MANAGE_THREADS"], []),
  ].filter((entry) => entry.id);
}

function supportCategoryOverwrites(ids) {
  return [
    overwrite(guildId, ["VIEW_CHANNEL", "SEND_MESSAGES", "READ_MESSAGE_HISTORY", "CREATE_PUBLIC_THREADS", "SEND_MESSAGES_IN_THREADS"], ["MENTION_EVERYONE"]),
    overwrite(ids.CommunityHelper, ["MANAGE_THREADS", "READ_MESSAGE_HISTORY", "SEND_MESSAGES"], []),
    overwrite(ids.Muted, [], ["SEND_MESSAGES", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "SEND_MESSAGES_IN_THREADS", "ADD_REACTIONS", "SPEAK"]),
  ].filter((entry) => entry.id);
}

function buildChannelPlan(roleIds, guild) {
  const hasCommunity = guild.features && guild.features.includes("COMMUNITY");
  const categories = [
    { name: "START HERE", key: "START HERE", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: startHereOverwrites(roleIds) },
    { name: "COMMUNITY", key: "COMMUNITY", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: publicCategoryOverwrites(roleIds) },
    { name: "GAME", key: "GAME", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: publicCategoryOverwrites(roleIds) },
    { name: "VOICE", key: "VOICE", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: publicVoiceOverwrites(roleIds) },
    { name: "SUPPORT", key: "SUPPORT", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: supportCategoryOverwrites(roleIds) },
    { name: "STAFF", key: "STAFF", type: CHANNEL_TYPE.GUILD_CATEGORY, permission_overwrites: staffOverwrites(roleIds) },
  ];

  const channels = [
    textDef("welcome", "START HERE", readOnlyOverwrites(roleIds), "Landing channel for new members."),
    textDef("rules", "START HERE", readOnlyOverwrites(roleIds), "Server rules and community expectations."),
    {
      ...textDef("announcements", "START HERE", readOnlyOverwrites(roleIds), "Major Voidscape announcements."),
      type: hasCommunity ? CHANNEL_TYPE.GUILD_ANNOUNCEMENT : CHANNEL_TYPE.GUILD_TEXT,
    },
    textDef("downloads", "START HERE", readOnlyOverwrites(roleIds), "Official client links and install notes."),
    textDef("server-status", "START HERE", readOnlyOverwrites(roleIds), "Maintenance, restarts, and uptime notes."),
    textDef("general", "COMMUNITY", null, "General Voidscape discussion.", 3),
    textDef("questions", "COMMUNITY", null, "Gameplay and setup questions."),
    textDef("media", "COMMUNITY", null, "Screenshots, clips, and promotional content."),
    forumDef("suggestions", "COMMUNITY", roleIds, "Feature ideas and balance discussions."),
    forumDef("bug-reports", "COMMUNITY", roleIds, "Reproducible bugs and client/server issues."),
    textDef("trading", "GAME", null, "Item trading and price checks.", 10),
    textDef("pking", "GAME", null, "Wilderness, PvP, risk, and clan fights.", 5),
    textDef("clans", "GAME", null, "Clan recruitment and group organization."),
    forumDef("guides", "GAME", roleIds, "Player-written guides and routes."),
    textDef("achievements", "GAME", null, "Drops, levels, unlocks, and kill screenshots."),
    voiceDef("Tavern", "VOICE", null),
    voiceDef("Dungeon Run", "VOICE", null),
    voiceDef("PK Trip", "VOICE", null),
    voiceDef("AFK", "VOICE", null),
    forumDef("help-desk", "SUPPORT", roleIds, "Support requests and setup help."),
    textDef("appeals-info", "SUPPORT", readOnlyOverwrites(roleIds), "Appeal instructions."),
    textDef("staff-chat", "STAFF", staffOverwrites(roleIds), "Private staff coordination."),
    textDef("mod-log", "STAFF", staffOverwrites(roleIds), "Manual moderation logs."),
    textDef("automod-log", "STAFF", staffOverwrites(roleIds), "Discord AutoMod alerts."),
    textDef("raid-alerts", "STAFF", staffOverwrites(roleIds), "Raid and safety alerts."),
    textDef("reports", "STAFF", staffOverwrites(roleIds), "Private reports and sensitive issue notes."),
    textDef("content-planning", "STAFF", staffOverwrites(roleIds), "Launch posts, media plans, and creator ops."),
  ];

  return { categories, channels };
}

function textDef(name, parentKey, overwrites, topic, slowmode = 0) {
  return {
    name,
    key: name,
    parentKey,
    type: CHANNEL_TYPE.GUILD_TEXT,
    topic,
    rate_limit_per_user: slowmode,
    permission_overwrites: overwrites,
  };
}

function voiceDef(name, parentKey, overwrites) {
  return {
    name,
    key: name,
    parentKey,
    type: CHANNEL_TYPE.GUILD_VOICE,
    permission_overwrites: overwrites,
  };
}

function forumDef(name, parentKey, roleIds, topic) {
  const forum = forumDefs[name];
  return {
    name,
    key: name,
    parentKey,
    type: CHANNEL_TYPE.GUILD_FORUM,
    topic,
    permission_overwrites: publicCategoryOverwrites(roleIds),
    default_auto_archive_duration: 10080,
    default_forum_layout: 1,
    default_sort_order: 0,
    available_tags: forum.tags.map((tag) => ({ name: tag, moderated: false })),
  };
}

function roleIdKey(name) {
  return name.replace(/[^A-Za-z0-9]/g, "");
}

async function fetchState(api) {
  const [guild, roles, channels] = await Promise.all([
    api.request("GET", `/guilds/${guildId}`),
    api.request("GET", `/guilds/${guildId}/roles`),
    api.request("GET", `/guilds/${guildId}/channels`),
  ]);
  return { guild, roles, channels };
}

function mapRoles(roles) {
  const byName = new Map();
  for (const role of roles) {
    byName.set(normalizeName(role.name), role);
  }
  return byName;
}

function mapChannels(channels) {
  const byNameAndType = new Map();
  const byName = new Map();
  for (const channel of channels) {
    byNameAndType.set(`${channel.type}:${normalizeName(channel.name)}`, channel);
    byName.set(normalizeName(channel.name), channel);
  }
  return { byNameAndType, byName };
}

async function ensureRoles(api, state) {
  console.log("Ensuring roles...");
  const byName = mapRoles(state.roles);

  for (const role of roleDefs) {
    const body = {
      name: role.name,
      color: colorInt(role.color),
      hoist: Boolean(role.hoist),
      mentionable: false,
      permissions: bitset(role.permissions),
    };
    const existing = byName.get(normalizeName(role.name));

    if (existing) {
      await api.request("PATCH", `/guilds/${guildId}/roles/${existing.id}`, body);
    } else {
      const created = await api.request("POST", `/guilds/${guildId}/roles`, body);
      byName.set(normalizeName(role.name), created);
    }
  }
}

function roleIdsFromState(roles) {
  const byName = mapRoles(roles);
  const ids = {};
  for (const role of roleDefs) {
    const existing = byName.get(normalizeName(role.name));
    if (existing) {
      ids[roleIdKey(role.name)] = existing.id;
    }
  }
  return ids;
}

async function ensureChannel(api, channelMaps, parentIds, def) {
  const name = def.type === CHANNEL_TYPE.GUILD_TEXT || def.type === CHANNEL_TYPE.GUILD_FORUM || def.type === CHANNEL_TYPE.GUILD_ANNOUNCEMENT
    ? textChannelName(def.name)
    : def.name;
  const matching = channelMaps.byNameAndType.get(`${def.type}:${normalizeName(name)}`);
  const sameName = channelMaps.byName.get(normalizeName(name));
  const body = {
    name,
    type: def.type,
    topic: def.topic,
    parent_id: def.parentKey ? parentIds[def.parentKey] : undefined,
    permission_overwrites: def.permission_overwrites || undefined,
    rate_limit_per_user: def.rate_limit_per_user || undefined,
    default_auto_archive_duration: def.default_auto_archive_duration || undefined,
    default_forum_layout: def.default_forum_layout,
    default_sort_order: def.default_sort_order,
    available_tags: def.available_tags,
  };

  Object.keys(body).forEach((key) => body[key] === undefined && delete body[key]);

  if (matching) {
    const patchBody = { ...body };
    delete patchBody.type;
    const updated = await api.request("PATCH", `/channels/${matching.id}`, patchBody);
    channelMaps.byNameAndType.set(`${def.type}:${normalizeName(name)}`, updated);
    channelMaps.byName.set(normalizeName(name), updated);
    return updated;
  }

  if (sameName && sameName.type !== def.type) {
    manualFollowUps.add(`#${name} already exists as a different channel type. Review it manually if you want it converted.`);
    return sameName;
  }

  const created = await bestEffort(`create #${name}`, () => api.request("POST", `/guilds/${guildId}/channels`, body));
  if (!created && def.type === CHANNEL_TYPE.GUILD_FORUM) {
    manualFollowUps.add(`#${name} should be a Forum channel; Discord rejected the API request, so create or convert it manually.`);
    return null;
  }
  if (!created && def.type === CHANNEL_TYPE.GUILD_ANNOUNCEMENT) {
    const fallback = await api.request("POST", `/guilds/${guildId}/channels`, { ...body, type: CHANNEL_TYPE.GUILD_TEXT });
    manualFollowUps.add(`#${name} was created as a text channel. Convert it to Announcement after Community is enabled.`);
    return fallback;
  }
  if (created) {
    channelMaps.byNameAndType.set(`${def.type}:${normalizeName(name)}`, created);
    channelMaps.byName.set(normalizeName(name), created);
  }
  return created;
}

async function ensureChannels(api, state, roleIds, options = {}) {
  console.log(options.coreOnly ? "Ensuring core Discord channels..." : "Ensuring categories and channels...");
  const plan = buildChannelPlan(roleIds, state.guild);
  const channelMaps = mapChannels(state.channels);
  const parentIds = {};
  const coreChannelKeys = new Set(["rules", "server-status", "automod-log", "raid-alerts"]);

  for (const category of plan.categories) {
    const channel = await ensureChannel(api, channelMaps, parentIds, category);
    if (channel) {
      parentIds[category.key] = channel.id;
    }
  }

  for (const channel of plan.channels) {
    if (options.coreOnly && !coreChannelKeys.has(channel.key)) {
      continue;
    }
    await ensureChannel(api, channelMaps, parentIds, channel);
  }
}

async function ensureCommunityAndBranding(api, state) {
  console.log("Applying server settings...");
  const channels = mapChannels(state.channels).byName;
  const rules = channels.get("rules");
  const status = channels.get("server-status");
  const raidAlerts = channels.get("raid-alerts");
  const guildBody = {
    name: "Voidscape",
    description: "Official Voidscape community hub for updates, support, PvP, trading, guides, and media.",
    default_message_notifications: 1,
    explicit_content_filter: 2,
    verification_level: 1,
    preferred_locale: "en-US",
  };

  await bestEffort("apply guild baseline settings", () => api.request("PATCH", `/guilds/${guildId}`, guildBody));

  if (rules && status) {
    const features = new Set(state.guild.features || []);
    features.add("COMMUNITY");
    const communityBody = {
      features: Array.from(features),
      rules_channel_id: rules.id,
      public_updates_channel_id: status.id,
    };
    if (raidAlerts) {
      communityBody.safety_alerts_channel_id = raidAlerts.id;
    }
    await bestEffort("apply community settings", () => api.request("PATCH", `/guilds/${guildId}`, communityBody));
  } else {
    manualFollowUps.add("Enable Community manually after #rules and #server-status exist.");
  }

  if (SKIP_BRANDING) {
    manualFollowUps.add("Branding skipped by --skip-branding.");
    return;
  }

  const brandingBody = {
    icon: loadImageDataUri("assets/discord/server-icon-512.png"),
    banner: loadImageDataUri("assets/discord/server-banner-960x540.png"),
    splash: loadImageDataUri("assets/discord/invite-splash-1920x1080.png"),
  };
  Object.keys(brandingBody).forEach((key) => brandingBody[key] === undefined && delete brandingBody[key]);

  if (Object.keys(brandingBody).length > 0) {
    await bestEffort("upload icon/banner/splash", () => api.request("PATCH", `/guilds/${guildId}`, brandingBody));
    manualFollowUps.add("If banner or invite splash did not appear, your server may not have that Discord feature yet; upload them manually when available.");
  }
}

async function postPinnedMessages(api, state) {
  console.log("Posting starter messages...");
  const channels = mapChannels(state.channels).byName;
  for (const [name, content] of Object.entries(pinnedMessages)) {
    const channel = channels.get(name);
    if (!channel || channel.type === CHANNEL_TYPE.GUILD_FORUM) {
      continue;
    }

    await bestEffort(`post pinned message in #${name}`, async () => {
      const existingPins = await api.request("GET", `/channels/${channel.id}/pins`);
      const alreadyPinned = Array.isArray(existingPins) && existingPins.some((message) => message.author?.bot && message.content === content);
      if (alreadyPinned) {
        return null;
      }
      const message = await api.request("POST", `/channels/${channel.id}/messages`, { content });
      await api.request("PUT", `/channels/${channel.id}/pins/${message.id}`);
      return message;
    });
  }
}

async function createForumStarterPosts(api, state) {
  console.log("Creating forum starter posts...");
  const channels = mapChannels(state.channels).byName;
  const activeThreads = await bestEffort("fetch active guild threads", () => api.request("GET", `/guilds/${guildId}/threads/active`));
  for (const [name, forum] of Object.entries(forumDefs)) {
    const channel = channels.get(name);
    if (!channel || channel.type !== CHANNEL_TYPE.GUILD_FORUM) {
      continue;
    }

    await bestEffort(`create starter post in #${name}`, async () => {
      const existing = activeThreads?.threads?.some((thread) => thread.parent_id === channel.id && thread.name === forum.starter.title);
      if (existing) {
        return null;
      }
      return api.request("POST", `/channels/${channel.id}/threads`, {
        name: forum.starter.title,
        auto_archive_duration: 10080,
        message: { content: forum.starter.body },
      });
    });
  }
}

async function ensureAutoMod(api, state) {
  if (SKIP_AUTOMOD) {
    manualFollowUps.add("AutoMod skipped by --skip-automod.");
    return;
  }

  console.log("Ensuring AutoMod rules...");
  const channels = mapChannels(state.channels).byName;
  const automodLog = channels.get("automod-log");
  if (!automodLog) {
    manualFollowUps.add("Create #automod-log before enabling AutoMod alert routing.");
    return;
  }

  const existingRules = await bestEffort("fetch AutoMod rules", () => api.request("GET", `/guilds/${guildId}/auto-moderation/rules`));
  if (!Array.isArray(existingRules)) {
    manualFollowUps.add("Review Discord AutoMod manually; the bot could not fetch rules.");
    return;
  }

  const byName = new Map(existingRules.map((rule) => [rule.name, rule]));
  const rules = [
    {
      name: "Voidscape - commonly flagged words",
      event_type: AUTO_MOD_EVENT_MESSAGE_SEND,
      trigger_type: AUTO_MOD_TRIGGER_KEYWORD_PRESET,
      trigger_metadata: {
        presets: [AUTO_MOD_PRESET_PROFANITY, AUTO_MOD_PRESET_SEXUAL_CONTENT, AUTO_MOD_PRESET_SLURS],
      },
      actions: [
        { type: AUTO_MOD_ACTION_BLOCK_MESSAGE, metadata: { custom_message: "That message was blocked by Voidscape AutoMod." } },
        { type: AUTO_MOD_ACTION_SEND_ALERT, metadata: { channel_id: automodLog.id } },
      ],
      enabled: true,
    },
    {
      name: "Voidscape - spam content",
      event_type: AUTO_MOD_EVENT_MESSAGE_SEND,
      trigger_type: AUTO_MOD_TRIGGER_SPAM,
      trigger_metadata: {},
      actions: [
        { type: AUTO_MOD_ACTION_BLOCK_MESSAGE, metadata: { custom_message: "Discord flagged that as spam." } },
        { type: AUTO_MOD_ACTION_SEND_ALERT, metadata: { channel_id: automodLog.id } },
      ],
      enabled: true,
    },
    {
      name: "Voidscape - mention spam",
      event_type: AUTO_MOD_EVENT_MESSAGE_SEND,
      trigger_type: AUTO_MOD_TRIGGER_MENTION_SPAM,
      trigger_metadata: { mention_total_limit: 8 },
      actions: [
        { type: AUTO_MOD_ACTION_BLOCK_MESSAGE, metadata: { custom_message: "Too many mentions at once." } },
        { type: AUTO_MOD_ACTION_TIMEOUT, metadata: { duration_seconds: 600 } },
        { type: AUTO_MOD_ACTION_SEND_ALERT, metadata: { channel_id: automodLog.id } },
      ],
      enabled: true,
    },
    {
      name: "Voidscape - scam and raid keywords",
      event_type: AUTO_MOD_EVENT_MESSAGE_SEND,
      trigger_type: AUTO_MOD_TRIGGER_KEYWORD,
      trigger_metadata: {
        keyword_filter: [
          "free nitro",
          "discord.gift",
          "steam gift",
          "airdrop",
          "crypto giveaway",
          "claim reward",
          "official moderator",
          "voidscape admin",
          "verify your wallet",
          "download hack",
          "dupe method",
        ],
      },
      actions: [
        { type: AUTO_MOD_ACTION_BLOCK_MESSAGE, metadata: { custom_message: "That phrase is blocked here." } },
        { type: AUTO_MOD_ACTION_SEND_ALERT, metadata: { channel_id: automodLog.id } },
      ],
      enabled: true,
    },
  ];

  for (const rule of rules) {
    const existing = byName.get(rule.name);
    if (existing) {
      await bestEffort(`update AutoMod rule ${rule.name}`, () => api.request("PATCH", `/guilds/${guildId}/auto-moderation/rules/${existing.id}`, rule));
    } else {
      await bestEffort(`create AutoMod rule ${rule.name}`, () => api.request("POST", `/guilds/${guildId}/auto-moderation/rules`, rule));
    }
  }
}

async function main() {
  console.log(`Voidscape Discord setup (${APPLY ? "apply" : "dry run"})`);
  const api = new DiscordApi(token, !APPLY);

  let state = await fetchState(api);
  await ensureRoles(api, state);

  state = APPLY ? await fetchState(api) : state;
  const roleIds = roleIdsFromState(state.roles);
  await ensureChannels(api, state, roleIds, { coreOnly: true });

  state = APPLY ? await fetchState(api) : state;
  await ensureCommunityAndBranding(api, state);

  state = APPLY ? await fetchState(api) : state;
  await ensureChannels(api, state, roleIds);

  state = APPLY ? await fetchState(api) : state;
  await postPinnedMessages(api, state);
  await createForumStarterPosts(api, state);
  await ensureAutoMod(api, state);

  manualFollowUps.add("Review Server Settings > Onboarding manually using docs/community/discord-server-setup.md.");
  manualFollowUps.add("Use Discord's View Server As Role to verify @everyone, Member, Muted, Moderator, and Admin permissions.");
  manualFollowUps.add("After setup, remove the setup bot from the server or regenerate the bot token.");

  if (!APPLY) {
    console.log("\nDry-run mutating calls:");
    for (const action of plannedActions) {
      console.log(`- ${action}`);
    }
    console.log("\nNo Discord changes were made. Re-run with --apply when ready.");
  }

  console.log("\nManual follow-ups:");
  for (const item of manualFollowUps) {
    console.log(`- ${item}`);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
