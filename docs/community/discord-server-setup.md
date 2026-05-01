# Voidscape Discord Server Setup

This is a standalone Discord community plan. It does not use the legacy OpenRSC Discord code, game webhooks, cross-chat, in-game account pairing, or server config.

## Assets

Use these ready-to-upload files:

- Server icon: `assets/discord/server-icon-512.png`
- Server banner: `assets/discord/server-banner-960x540.png`
- High-res banner source: `assets/discord/server-banner-1920x1080.png`
- Invite splash/background: `assets/discord/invite-splash-1920x1080.png`

## Setup Order

You can do this manually or run the local one-shot setup bot in `scripts/setup-discord.js`. The bot path is faster and safer for a new server because it is dry-run by default and creates the baseline roles, channels, permissions, pinned messages, branding, and AutoMod rules.

Bot instructions: `docs/community/discord-setup-bot.md`.

Manual order:

1. Create the server as `Voidscape`.
2. Upload the server icon.
3. Enable Community: Server Settings > Enable Community.
4. Create roles in the order listed below.
5. Create categories and channels.
6. Apply category permissions before adding channel-specific overrides.
7. Enable Safety Setup, AutoMod, and Raid Protection.
8. Configure Onboarding.
9. Post the pinned messages.
10. Invite staff only, test with "View Server As Role", then publish the public invite.

Even if you use the bot, review Community, Onboarding, Safety Setup, Raid Protection, banner availability, and role order manually in Discord after it runs.

## Announcement Gate

Voidscape currently uses an optional access gate to put `#announcements` in front of new members before the rest of the server unlocks.

Flow:

1. New members can only see `#announcements`.
2. `#announcements` has a pinned `Enter Voidscape` button message.
3. Clicking the button grants the `Member` role.
4. `Member` unlocks the normal public server channels.

The gate is implemented by `scripts/discord-access-gate.js`. Run `--setup` to configure channel permissions and post/update the pinned gate message. Run `--serve` as a persistent process so button clicks can grant roles. If the persistent bot is not running, the button remains visible but cannot unlock users.

## Server Settings

Recommended baseline:

- Default Notifications: Only @mentions.
- Verification Level: Low for normal launch, Medium during raids or high-risk promo pushes.
- Explicit Media Content Filter: High.
- Disable @everyone/@here for all non-staff roles.
- Enable Community Onboarding instead of a bot verification wall.
- Enable Raid Protection alerts to `#raid-alerts`.
- Enable AutoMod alerts to `#automod-log`.

## Roles

Keep role hierarchy strict. Only Owner and Admin should have Administrator.

| Role | Color | Purpose | Key permissions |
| --- | --- | --- | --- |
| Owner | `#f4d06f` | Server owner | Administrator |
| Admin | `#d8d8df` | Full server ops | Administrator, Manage Server |
| Moderator | `#8f62ff` | Moderation | Kick, Ban, Timeout, Manage Messages, Manage Threads |
| Community Helper | `#31c48d` | Trusted support | Manage Threads in help channels, no punitive powers |
| Content Creator | `#ff5ca8` | Promo/media role | No staff permissions |
| Veteran | `#55a7ff` | Trusted long-term member | No staff permissions |
| Member | `#b8beca` | Default community access | Normal chat permissions |
| Muted | `#4a4f58` | Manual restriction fallback | Deny Send Messages, Create Posts, Add Reactions, Speak |

Optional interest roles for Onboarding:

- PvP
- PvE
- Trading
- Clans
- Guides
- Media
- Patch Notes
- Events
- Polls

## Channels

Create the categories and channels in this order.

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

Channel types:

- Make `#suggestions`, `#bug-reports`, `#guides`, and `#help-desk` Forum channels.
- Make `#announcements` an Announcement channel if Community enables it.
- Keep `#reports` staff-only. Public members should use Discord report tools, `#help-desk`, or direct staff contact for sensitive issues.

## Category Permissions

`START HERE`:

- `@everyone`: View Channel allowed.
- `@everyone`: Send Messages denied for `#welcome`, `#rules`, `#announcements`, `#downloads`, `#server-status`.
- Staff roles: Send Messages allowed.

`COMMUNITY` and `GAME`:

- `@everyone` or `Member`: View Channel, Send Messages, Read Message History, Add Reactions, Create Public Threads.
- Deny Mention @everyone, @here, and All Roles.
- Use slowmode where needed: `#general` 3s, `#trading` 10s, `#pking` 5s.

`VOICE`:

- `Member`: Connect, Speak, Use Voice Activity.
- Deny priority speaker except staff.

`SUPPORT`:

- `#help-desk`: Members can create posts. Helpers and staff can manage posts.
- `#appeals-info`: Read-only instructions.

`STAFF`:

- Private category.
- `@everyone`: no View Channel.
- Staff roles only.

## Forum Setup

`#suggestions`

- Post guidelines: "One idea per post. Explain the problem, the proposed change, and who it helps."
- Tags: `content`, `balance`, `ui`, `economy`, `pvp`, `quality-of-life`, `needs-review`, `accepted`, `declined`, `shipped`
- Default layout: List.
- Hide after inactivity: 1 week.

`#bug-reports`

- Post guidelines: "Use the template. Include steps to reproduce, what happened, what should happen, screenshots/video if useful, and whether it can be abused."
- Tags: `client`, `android`, `server`, `combat`, `bank`, `auction-house`, `wilderness`, `visual`, `abusable`, `fixed`
- Default layout: List.
- Hide after inactivity: 1 week.

`#guides`

- Post guidelines: "Use clear titles. Add requirements, route/setup, risk level, and screenshots when useful."
- Tags: `new-player`, `skilling`, `pvm`, `pvp`, `money-making`, `wilderness`, `systems`
- Default layout: List.
- Hide after inactivity: 1 week.

`#help-desk`

- Post guidelines: "Ask one question per post. Include your client type, device/OS, and what you already tried."
- Tags: `account`, `download`, `android`, `launcher`, `gameplay`, `resolved`
- Default layout: List.
- Hide after inactivity: 3 days.

## Onboarding

Default channels:

- `#rules`
- `#announcements`
- `#downloads`
- `#server-status`
- `#general`
- `#questions`
- `#media`
- `#suggestions`

This satisfies Discord's Onboarding requirement for at least 7 default channels while keeping at least 5 member-writable channels available.

Question 1: "What are you here for?"

- Playing Voidscape: assign `Member`, show `#general`, `#questions`, `#downloads`
- Trading: assign `Trading`, show `#trading`
- PvP and Wilderness: assign `PvP`, show `#pking`, `#clans`
- PvE and progression: assign `PvE`, show `#guides`, `#achievements`
- Creating videos/screenshots: assign `Media`, show `#media`
- Giving feedback: show `#suggestions`, `#bug-reports`

Ask before joining: yes. Allow multiple answers: yes. Required: yes.

Question 2: "Which updates do you want?"

- Patch notes: assign `Patch Notes`, show `#announcements`
- Events: assign `Events`, show `#announcements`, `#server-status`
- Polls and feedback: assign `Polls`, show `#suggestions`

Ask before joining: yes. Allow multiple answers: yes. Required: no.

Question 3: "How familiar are you with classic RSC-style games?"

- New
- Returning
- Experienced
- Mostly here for PvP

Ask before joining: yes. Allow multiple answers: no. Required: no.

Use this question mostly for member context. It does not need to grant permissions.

## AutoMod

Use Discord-native AutoMod only at launch.

Enable:

- Commonly Flagged Words: Insults and Slurs, Sexual Content, Severe Profanity.
- Block Spam Content: block and alert.
- Block Mention Spam: block and alert; start with a conservative mention limit.
- Custom Keyword Rule: alert-only for suspicious raid phrases at first.
- Block Words in Member Profile Names: add impersonation and scam terms.

Recommended alert channel: `#automod-log`.

Custom spam/scam starter terms:

```text
free nitro
discord.gift
steam gift
airdrop
crypto giveaway
claim reward
official moderator
voidscape admin
verify your wallet
download hack
dupe method
```

Keep slur lists out of the repo. Configure those directly inside Discord.

## Pinned Messages

### `#welcome`

```text
Welcome to Voidscape.

This is the official community hub for players, testers, creators, and staff. Start with #rules, grab the client from #downloads, and use #questions if anything is unclear.

Useful places:
- #announcements for major updates
- #server-status for uptime and maintenance
- #bug-reports for reproducible issues
- #suggestions for feature ideas
- #media for screenshots and clips
```

### `#rules`

```text
Voidscape Rules

1. Respect other players. No harassment, hate speech, threats, or personal attacks.
2. No doxxing, leaking private information, impersonation, phishing, or social engineering.
3. No spam, invite spam, scam links, malicious files, or disruptive self-promo.
4. Keep channels on topic. Use threads/forum posts for longer discussions.
5. Do not sell, buy, or advertise real-money trading, account sales, cheats, exploits, or stolen goods.
6. Report abusable bugs privately through #help-desk or staff contact. Do not post exploit steps publicly.
7. Follow Discord Terms and Community Guidelines.
8. Staff may remove content or restrict access when needed to protect the community.
```

### `#downloads`

```text
Downloads

Use this channel for current Voidscape client links and install notes.

Before posting a support request:
- Confirm you downloaded the latest build.
- Include your platform: Windows, macOS, Linux, or Android.
- Include screenshots of any error messages.

Never download Voidscape clients from random Discord DMs or unofficial mirrors.
```

### `#server-status`

```text
Server Status

Staff will post maintenance windows, restarts, and known incidents here.

Status format:
- Online
- Degraded
- Restarting
- Maintenance
- Investigating
```

### `#announcements`

```text
Announcement Template

Title:
Summary:
What changed:
Known issues:
Player action needed:
Date:
```

### `#bug-reports`

```text
Bug Report Template

Title:
Client/platform:
Character name:
Where it happened:
Steps to reproduce:
Expected result:
Actual result:
Screenshots/video:
Can this be abused? yes/no/unsure
```

### `#suggestions`

```text
Suggestion Template

Title:
Problem:
Proposed change:
Why it helps:
Possible downside:
Related screenshots/examples:
```

### `#trading`

```text
Trading Rules

- Be clear about item, quantity, and price.
- No real-money trading.
- No trust trades.
- Use in-game trade screens and verify items before accepting.
- Staff will not enforce vague verbal deals.
```

### `#pking`

```text
Wilderness and PKing

Use this channel for fights, clans, risk discussion, and Wilderness clips.

Keep it competitive, not personal. Trash talk is not a license to harass people across channels or DMs.
```

### `#appeals-info`

```text
Appeals

If you need to appeal a mute, timeout, kick, ban, or Discord moderation action, contact an Admin or Moderator privately.

Include:
- Discord username
- In-game character name, if relevant
- What happened
- Why you believe the action should be reviewed
- Any screenshots or context

Do not argue appeals in public channels.
```

## Moderation Workflow

Normal issue:

1. Remove the message if needed.
2. Warn or time out based on severity.
3. Log summary in `#mod-log`.
4. Move longer staff discussion to a thread.

Abusable bug:

1. Remove public exploit details.
2. Ask the reporter to continue in private.
3. Record summary in `#reports`.
4. Escalate to dev owner.
5. Post public notice only after risk is contained.

Raid:

1. Switch Verification Level to Medium or higher.
2. Enable stricter AutoMod responses.
3. Lock `#general` if needed.
4. Use Discord's raid alert/CAPTCHA flow if triggered.
5. Ban obvious raid accounts.
6. Post an internal recap in `#mod-log`.

## Launch Checklist

- Server icon uploaded.
- Banner uploaded if available.
- Invite splash uploaded if available.
- Community enabled.
- Safety Setup complete.
- AutoMod alerts routed to `#automod-log`.
- Raid Protection alerts routed to `#raid-alerts`.
- Roles created and ordered.
- Staff category private.
- Public categories tested with "View Server As Role".
- Onboarding preview tested.
- All pinned messages posted.
- Staff invited first.
- Public invite created with no expiry only after the server is reviewed.

## Official Discord References

- Community Onboarding: https://support.discord.com/hc/en-us/articles/11074987197975-Community-Onboarding-FAQ
- Forum Channels: https://support.discord.com/hc/en-us/articles/6208479917079-Forum-Channels-FAQ
- Roles and Permissions: https://support.discord.com/hc/en-us/articles/214836687-Discord-Roles-and-Permissions
- AutoMod: https://support.discord.com/hc/en-us/articles/4421269296535-AutoMod-FAQ
- Raid Protection: https://support.discord.com/hc/en-us/articles/10989121220631-How-to-Protect-Your-Server-from-Raids-101
- Server Banners: https://support.discord.com/hc/en-us/articles/360028716472-Server-Banners
