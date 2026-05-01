# Voidscape Discord Setup Bot

This is a local one-shot bot for building the standalone Voidscape Discord server. It does not connect Discord to the game server, OpenRSC Discord code, webhooks, cross-chat, account linking, or in-game config.

The bot can create or update roles, categories, channels, forum channels, permission overwrites, starter posts, pinned messages, server branding, and Discord-native AutoMod rules. Onboarding still needs a manual review in Discord because that flow is gated and easier to verify in the UI.

## Create The Bot

1. Open the Discord Developer Portal.
2. Create a new application named `Voidscape Setup`.
3. Open Bot, then create or reset the bot token.
4. Keep the token private. Do not paste it into code, Discord, or commits.
5. Open OAuth2 > URL Generator.
6. Select scope `bot`.
7. Select `Administrator` for the setup run.
8. Use the generated URL to invite the bot to the new `Voidscape` server.

Administrator is recommended only for the one-shot setup because it avoids permission ordering problems while the server is empty. After setup, remove the bot or regenerate the token.

## Get The Server ID

1. In Discord, enable User Settings > Advanced > Developer Mode.
2. Right-click the `Voidscape` server icon.
3. Click Copy Server ID.

## Dry Run

Run this first. It prints the mutating API calls without changing Discord.

```sh
DISCORD_BOT_TOKEN='paste-token-here' \
DISCORD_GUILD_ID='paste-server-id-here' \
node scripts/setup-discord.js
```

## Apply

Run this when the dry run looks right.

```sh
DISCORD_BOT_TOKEN='paste-token-here' \
DISCORD_GUILD_ID='paste-server-id-here' \
node scripts/setup-discord.js --apply
```

Optional flags:

- `--skip-branding`: do not upload the icon, banner, or invite splash.
- `--skip-automod`: do not create or update AutoMod rules.

## After The Bot Runs

Review these manually:

- Server Settings > Enable Community if Discord did not allow the API to enable it.
- Server Settings > Onboarding, using `docs/community/discord-server-setup.md`.
- Server Settings > Safety Setup and Raid Protection.
- Server Settings > Roles, especially role order and role colors.
- Server Settings > Overview, if banner or invite splash is unavailable on the server tier.
- Every private/staff channel with Discord's "View Server As Role" tool.

Then remove the setup bot from the server or regenerate the token in the Developer Portal.

## What It Creates

Roles:

- Owner
- Admin
- Moderator
- Community Helper
- Content Creator
- Veteran
- Member
- Muted
- PvP
- PvE
- Trading
- Clans
- Guides
- Media
- Patch Notes
- Events
- Polls

Categories and channels:

```text
START HERE
# welcome
# rules
# announcements
# downloads
# server-status

COMMUNITY
# general
# questions
# media
# suggestions
# bug-reports

GAME
# trading
# pking
# clans
# guides
# achievements

VOICE
Tavern
Dungeon Run
PK Trip
AFK

SUPPORT
# help-desk
# appeals-info

STAFF
# staff-chat
# mod-log
# automod-log
# raid-alerts
# reports
# content-planning
```

The bot makes `#suggestions`, `#bug-reports`, `#guides`, and `#help-desk` forum channels when Discord allows it. If Discord rejects forum or announcement creation because Community is not enabled yet, finish those conversions in the Discord UI.

## Safety Notes

- The script is dry-run by default.
- The token is read only from `DISCORD_BOT_TOKEN`.
- The script never prints the token.
- Existing roles and channels are matched by name to avoid duplicates.
- The script does not delete channels, roles, or messages.
- The script does not touch any game code or config.
