#!/usr/bin/env node

const API_BASE = "https://discord.com/api/v10";
const GATE_BUTTON_ID = "voidscape:unlock:v1";

const FLAGS = new Set(process.argv.slice(2));
const HELP = FLAGS.has("--help") || FLAGS.has("-h");
const SETUP = FLAGS.has("--setup");
const SERVE = FLAGS.has("--serve");

const token = process.env.DISCORD_BOT_TOKEN;
const guildId = process.env.DISCORD_GUILD_ID;
const memberRoleName = process.env.DISCORD_MEMBER_ROLE || "Member";
const gateChannelName = process.env.DISCORD_GATE_CHANNEL || "announcements";

const CHANNEL_TYPE = {
  GUILD_TEXT: 0,
  GUILD_VOICE: 2,
  GUILD_CATEGORY: 4,
  GUILD_ANNOUNCEMENT: 5,
  GUILD_FORUM: 15,
};

const PERM = {
  VIEW_CHANNEL: 1n << 10n,
  SEND_MESSAGES: 1n << 11n,
  ADD_REACTIONS: 1n << 6n,
  READ_MESSAGE_HISTORY: 1n << 16n,
  MENTION_EVERYONE: 1n << 17n,
  CONNECT: 1n << 20n,
  SPEAK: 1n << 21n,
  USE_VAD: 1n << 25n,
  CREATE_PUBLIC_THREADS: 1n << 35n,
  CREATE_PRIVATE_THREADS: 1n << 36n,
  SEND_MESSAGES_IN_THREADS: 1n << 38n,
};

function usage() {
  console.log(`Voidscape Discord access gate

Setup permissions and post/update the unlock button:
  DISCORD_BOT_TOKEN='...' DISCORD_GUILD_ID='...' node scripts/discord-access-gate.js --setup

Run the persistent button listener:
  DISCORD_BOT_TOKEN='...' DISCORD_GUILD_ID='...' node scripts/discord-access-gate.js --serve

Run setup, then serve:
  DISCORD_BOT_TOKEN='...' DISCORD_GUILD_ID='...' node scripts/discord-access-gate.js --setup --serve

Environment:
  DISCORD_MEMBER_ROLE   Role granted by the button. Default: Member
  DISCORD_GATE_CHANNEL  Only channel visible before unlock. Default: announcements

This is standalone Discord community tooling. It does not connect Discord to the game server.`);
}

if (HELP || (!SETUP && !SERVE)) {
  usage();
  process.exit(HELP ? 0 : 1);
}

if (!token || !guildId) {
  usage();
  console.error("\nMissing DISCORD_BOT_TOKEN or DISCORD_GUILD_ID.");
  process.exit(1);
}

function bitset(names) {
  return names.reduce((value, name) => value | PERM[name], 0n).toString();
}

function normalizeName(name) {
  return name.toLowerCase();
}

async function api(method, route, body = undefined) {
  const response = await fetch(`${API_BASE}${route}`, {
    method,
    headers: {
      Authorization: `Bot ${token}`,
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw new Error(`${method} ${route} failed: HTTP ${response.status} ${text}`);
  }
  return data;
}

async function fetchGuildState() {
  const [roles, channels] = await Promise.all([
    api("GET", `/guilds/${guildId}/roles`),
    api("GET", `/guilds/${guildId}/channels`),
  ]);
  return { roles, channels };
}

function findRole(roles, name) {
  const role = roles.find((entry) => normalizeName(entry.name) === normalizeName(name));
  if (!role) {
    throw new Error(`Role not found: ${name}`);
  }
  return role;
}

function findChannel(channels, name) {
  const channel = channels.find((entry) => normalizeName(entry.name) === normalizeName(name));
  if (!channel) {
    throw new Error(`Channel not found: #${name}`);
  }
  return channel;
}

function mergeOverwrite(overwrites, id, allowNames, denyNames) {
  const next = (overwrites || []).filter((entry) => entry.id !== id);
  next.push({
    id,
    type: 0,
    allow: bitset(allowNames),
    deny: bitset(denyNames),
  });
  return next;
}

function publicMemberAllow(channel) {
  if (channel.type === CHANNEL_TYPE.GUILD_VOICE) {
    return ["VIEW_CHANNEL", "CONNECT", "SPEAK", "USE_VAD"];
  }
  if (channel.type === CHANNEL_TYPE.GUILD_CATEGORY && channel.name === "VOICE") {
    return ["VIEW_CHANNEL", "CONNECT", "SPEAK", "USE_VAD"];
  }
  return [
    "VIEW_CHANNEL",
    "SEND_MESSAGES",
    "ADD_REACTIONS",
    "READ_MESSAGE_HISTORY",
    "CREATE_PUBLIC_THREADS",
    "SEND_MESSAGES_IN_THREADS",
  ];
}

function isStaffScoped(channel, staffCategoryId) {
  return channel.id === staffCategoryId || channel.parent_id === staffCategoryId;
}

function isGateChannel(channel, gateChannel) {
  return channel.id === gateChannel.id;
}

function accessGateMessage() {
  return {
    content: "**Start Here: Read the announcements, then unlock the server**",
    allowed_mentions: { parse: [] },
    embeds: [
      {
        title: "Enter Voidscape",
        color: 0x8f45ea,
        description: [
          "New members start in announcements so the important information is not buried.",
          "",
          "Read through the feature posts, roadmap, growth plan, and current build notes above. When you are ready, click the button below to unlock the full Discord.",
        ].join("\n"),
        fields: [
          {
            name: "What unlocks",
            value: "General chat, questions, media, trading, PKing, clans, guides, suggestions, bug reports, help desk, and voice channels.",
          },
          {
            name: "Why we gate it",
            value: "Voidscape has a lot of custom systems already. This keeps new members from joining blind and missing the direction of the project.",
          },
        ],
      },
    ],
    components: [
      {
        type: 1,
        components: [
          {
            type: 2,
            style: 3,
            label: "Enter Voidscape",
            custom_id: GATE_BUTTON_ID,
          },
        ],
      },
    ],
  };
}

async function setupAccessGate() {
  console.log("Configuring access gate...");
  const { roles, channels } = await fetchGuildState();
  const memberRole = findRole(roles, memberRoleName);
  const gateChannel = findChannel(channels, gateChannelName);
  const staffCategory = channels.find((channel) => channel.type === CHANNEL_TYPE.GUILD_CATEGORY && channel.name === "STAFF");

  for (const channel of channels) {
    let overwrites = channel.permission_overwrites || [];

    if (isGateChannel(channel, gateChannel)) {
      overwrites = mergeOverwrite(
        overwrites,
        guildId,
        ["VIEW_CHANNEL", "READ_MESSAGE_HISTORY"],
        ["SEND_MESSAGES", "CREATE_PUBLIC_THREADS", "CREATE_PRIVATE_THREADS", "SEND_MESSAGES_IN_THREADS", "MENTION_EVERYONE"],
      );
    } else {
      overwrites = mergeOverwrite(overwrites, guildId, [], ["VIEW_CHANNEL"]);
    }

    if (!isStaffScoped(channel, staffCategory?.id) && !isGateChannel(channel, gateChannel)) {
      overwrites = mergeOverwrite(overwrites, memberRole.id, publicMemberAllow(channel), ["MENTION_EVERYONE"]);
    }

    await api("PATCH", `/channels/${channel.id}`, { permission_overwrites: overwrites });
  }

  await postOrUpdateGateMessage(gateChannel.id);
  console.log(`#${gateChannel.name} is now the public gate channel. Button grants ${memberRole.name}.`);
}

async function postOrUpdateGateMessage(channelId) {
  const messages = await api("GET", `/channels/${channelId}/messages?limit=50`);
  const existing = messages.find((message) => {
    return (message.components || []).some((row) => {
      return (row.components || []).some((component) => component.custom_id === GATE_BUTTON_ID);
    });
  });
  const body = accessGateMessage();

  if (existing) {
    await api("PATCH", `/channels/${channelId}/messages/${existing.id}`, body);
    console.log(`Updated existing gate message ${existing.id}.`);
  } else {
    const message = await api("POST", `/channels/${channelId}/messages`, body);
    await api("PUT", `/channels/${channelId}/pins/${message.id}`);
    console.log(`Posted gate message ${message.id}.`);
  }
}

async function serveAccessGate() {
  const { roles } = await fetchGuildState();
  const memberRole = findRole(roles, memberRoleName);
  const gateway = await api("GET", "/gateway/bot");
  const url = `${gateway.url}/?v=10&encoding=json`;

  setInterval(() => {}, 60000);

  let activeSocket = null;
  let heartbeatTimer = null;
  let sequence = null;
  let reconnectDelay = 1000;

  async function connect() {
    const ws = new WebSocket(url);
    activeSocket = ws;

    ws.addEventListener("open", () => {
      reconnectDelay = 1000;
    });

    ws.addEventListener("message", async (event) => {
      const payload = JSON.parse(event.data);
      if (payload.s !== null && payload.s !== undefined) {
        sequence = payload.s;
      }

      if (payload.op === 10) {
        heartbeatTimer = setInterval(() => {
          ws.send(JSON.stringify({ op: 1, d: sequence }));
        }, payload.d.heartbeat_interval);
        ws.send(JSON.stringify({
          op: 2,
          d: {
            token,
            intents: 1,
            properties: {
              os: process.platform,
              browser: "voidscape-access-gate",
              device: "voidscape-access-gate",
            },
          },
        }));
        return;
      }

      if (payload.op === 7) {
        ws.close();
        return;
      }

      if (payload.op === 0 && payload.t === "READY") {
        console.log(`Access gate ready as ${payload.d.user.username}.`);
        return;
      }

      if (payload.op === 0 && payload.t === "INTERACTION_CREATE") {
        await handleInteraction(payload.d, memberRole);
      }
    });

    ws.addEventListener("close", () => {
      console.error("Gateway connection closed; reconnecting.");
      if (activeSocket === ws) {
        activeSocket = null;
      }
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
      }
      setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 30000);
    });

    ws.addEventListener("error", (error) => {
      console.error(`Gateway error: ${error.message || error.type || error}`);
    });
  }

  await connect();
}

async function handleInteraction(interaction, memberRole) {
  if (interaction.type !== 3 || interaction.data?.custom_id !== GATE_BUTTON_ID) {
    return;
  }

  const userId = interaction.member?.user?.id || interaction.user?.id;
  if (!userId || interaction.guild_id !== guildId) {
    return;
  }

  try {
    await api("PUT", `/guilds/${guildId}/members/${userId}/roles/${memberRole.id}`);
    await api("POST", `/interactions/${interaction.id}/${interaction.token}/callback`, {
      type: 4,
      data: {
        flags: 64,
        content: "Access unlocked. Welcome to Voidscape.",
      },
    });
    console.log(`Granted ${memberRole.name} to ${interaction.member?.user?.username || userId}.`);
  } catch (error) {
    await api("POST", `/interactions/${interaction.id}/${interaction.token}/callback`, {
      type: 4,
      data: {
        flags: 64,
        content: "I could not unlock access automatically. Please ping staff.",
      },
    }).catch(() => {});
    console.error(`Failed to grant ${memberRole.name} to ${userId}: ${error.message}`);
  }
}

(async () => {
  if (SETUP) {
    await setupAccessGate();
  }
  if (SERVE) {
    await serveAccessGate();
  }
})().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
