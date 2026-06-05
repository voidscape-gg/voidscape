# Voidscape Friend Beta Playtest Guide

This beta flow is client-first against one hosted Voidscape server: friends download the launcher, click Play, create a character from the client, choose a starter path, and log in. No website account manager, email verification, Discord verification, or portal character creation is required for this pass.

## Host Setup

Before sending the launcher to friends, run the game server on a VPS or dedicated host and serve the launcher update folder from that same machine or another static file host.

1. On the server host, open the game TCP port from `server/local.conf`.

Default Voidscape local config uses:

```text
server_port: 43596
ws_server_port: 43496
```

Friends only need the TCP game port for the desktop client. The WebSocket port is for browser/web-client paths and can stay closed unless you intentionally use it.

2. Start the game server on the host.

```bash
scripts/build.sh
scripts/run-server.sh
```

3. From your build machine, create a friend beta launcher package.

```bash
scripts/package-friend-beta.sh \
  --host <server-host-or-ip> \
  --port 43596 \
  --base-url https://<server-host-or-ip>/voidscape/update \
  --discord-url <optional-discord-invite>
```

This produces:

```text
dist/friend-beta/VoidscapeLauncher.jar
dist/friend-beta/update/
```

4. Upload or sync `dist/friend-beta/update/` to the static URL used as `--base-url`.

Verify the manifest is reachable:

```bash
curl -I https://<server-host-or-ip>/voidscape/update/manifest.properties
```

5. Send friends only this file:

```text
dist/friend-beta/VoidscapeLauncher.jar
```

On first Play, the launcher writes the hosted server endpoint into its runtime cache, downloads the PC client/cache from the manifest, and starts the client.

6. For this friend beta, newly created characters are temporarily created as Admins automatically.

Manual SQLite rank example, if an existing character needs to be changed:

```bash
make rank-sqlite db=<db_name> group=1 username=<character_name>
```

In-game owner/admin example:

```text
::setrank <character_name> Admin
```

Useful group IDs:

| Group | ID |
|---|---:|
| Owner | 0 |
| Admin | 1 |
| User | 10 |

Use admin rank only for trusted testers. The commands below can spawn items, alter stats, and disrupt the economy.

## Tester Flow

1. Open `VoidscapeLauncher.jar`.
2. Click Play. First launch may download the client/cache.
3. In the client, click `New User`.
4. Pick a username and password.
5. Customize appearance.
6. On Void Island, talk to the Void Herald at `(24,24)`.
7. Choose one starter path.
8. After the welcome box, play normally or use the test commands below.

Starter paths:

| Path | Boost | Starter kit |
|---|---|---|
| Warrior | 2x Attack, Defense, Strength | Iron 2-handed sword, 10 cooked meat, bronze plate body, bronze medium helmet, bronze legs |
| Forager | 2x Fishing, Cooking, Mining | Net, fishing rod, 50 bait, bronze pickaxe, tinderbox, 100 coins, 2 cooked meat, 2 bread |
| Arcanist | 2x Ranged, Magic | Shortbow, 50 bronze arrows, blue wizard hat, wizard robe, 100 air runes, 50 mind runes, 25 fire runes, 2 bread |

## Player Commands

These do not require admin rank.

| Command | Use |
|---|---|
| `::g <message>` | Send simplified global chat with your IP country flag beside your name. |
| `::rested` | Show rested-XP pool and cap status. |
| `::titles` | Open the title catalogue UI. |
| `::titles unlocked` | Show unlocked titles. |
| `::titles unique` | Show unique one-owner titles and owners. |
| `::titles rarest` | Show the rarest title page. |
| `::titles count` | Show unlocked count out of the 100-title catalog. |
| `::title <id or name>` | Equip an unlocked title. |
| `::title clear` | Remove active title. |
| `::lootbeam list` | Show current rare-drop beam settings. |
| `::lootbeam defaults` | Show default beam items. |
| `::lootbeam add <item id/name>` | Add a custom beam item. |
| `::lootbeam remove <item id/name>` | Remove or hide a beam item. |
| `::lootbeam mode default` | Use default beam list plus custom tweaks. |
| `::lootbeam mode custom` | Only show beams for manually added items. |
| `::lootbeam reset` | Reset beam settings. |
| `::leave` | Exit PK Catching Simulator early. |

## Admin Test Commands

Use these for fast beta testing with the temporary auto-admin beta accounts.

| Command | Use |
|---|---|
| `::goto <x> <y>` | Teleport yourself to exact coordinates. |
| `::goto <town>` | Teleport to a named town if configured. |
| `::return` | Return after `::goto`/summon flows when available. |
| `::quickbank` | Open bank immediately. |
| `::quickauction` | Open Auction House immediately. |
| `::workbenchahfixture` | Seed deterministic Auction House test listings and intel rows. |
| `::beastmode` | Equip/prepare a high-power admin combat kit for boss/combat testing. |
| `::item <id/name> <amount>` | Spawn item into inventory. |
| `::item <id/name> <amount> <player>` | Spawn item for an online player. |
| `::certeditem <id/name> <amount>` | Spawn noted/certed item when noteable. |
| `::setstat <level>` | Set all your levels. |
| `::setstat <level> <skill>` | Set one level, such as `::setstat 60 attack`. |
| `::setxp <experience> <skill>` | Set one skill XP. |
| `::heal` | Restore hits. |
| `::recharge` | Restore prayer. |
| `::skull <player>` | Skull a player for Wilderness PK announcement testing. |
| `::unskull <player>` | Remove a player's skull. |
| `::announcepreview skill` | Preview a skill milestone world message. |
| `::announcepreview total` | Preview a total-level world message. |
| `::announcepreview pk` | Preview a skulled Wilderness PK world message. |
| `::balancereport` | Show current beta telemetry summary. |
| `::balancereport xp|players|npcs|drops` | Inspect XP, player, NPC-kill, or drop telemetry. |
| `::balancereport reset` | Clear the in-memory telemetry window. |
| `::gatherstreak <skill> <resource-key> [failures]` | Seed gathering dry-streak protection for local testing. |
| `::wildhobdebug status` | Show dynamic hobgoblin spawn state. |
| `::wildhobdebug <0-20>` | Simulate unique-IP pressure for wilderness hobgoblins. |
| `::wildhobdebug off` | Clear the simulated pressure. |
| `::dropwave <npc_id> <count> <radius>` | Spawn and credit-kill NPCs using their normal drop tables. |
| `::pf <x> <y>` | Check whether world pathfinding can route there. |
| `::pathto <x> <y>` | Autowalk to coordinates using server pathfinding. |
| `::loadbots start <count> <radius> <intervalTicks>` | Spawn synthetic load-test players. |
| `::loadbots status` | Show load-bot state. |
| `::loadbots stop` | Remove load-test players. |
| `::voidrushbots <count>` | Queue a Void Rush test with bots. |
| `::cinematic bossfight <actors> <bossNpcId> <radius>` | Spawn a staged cinematic boss scene. |
| `::cinematic stop` | Clean up the cinematic scene. |

Useful item examples:

```text
::item 10 10000      # coins
::item 1602 1        # subscription card
::item 1593 1        # Void Scimitar
::item 1594 1        # Void Shortbow
::item 1601 1        # Void Key
```

Useful drop test example:

```text
::dropwave 67 10 3   # level-32 hobgoblins
```

## Coordinates

Use exact coordinates with `::goto <x> <y>`.

| Place | Coordinates | What to test |
|---|---:|---|
| Void Island Herald | `24 24` | New-character starter choice. |
| Lumbridge home | `120 648` | Spawn, home flow, nearby vendor. |
| Subscription Vendor | `126 649` | Native shop with weekly subscription cards. |
| Edgeville bank | `217 449` | Banking, nearby Auction House, PvP access. |
| Void Auctioneer | `217 460` | Auction House UI, listing, buying, intel. |
| Void Rift | `192 443` | Enter prompt to Void Enclave. |
| Void Enclave | `113 314` | Safe zone, amenities, waystones, boss entry. |
| Void Knight boss ladder | `122 313` | Climb down from Enclave to boss chamber. |
| Void Knight chamber | `984 667` | Attack the Void Knight to start the solo fight. |
| Wilderness hobgoblins | `217 255` | Dynamic spawns and faster respawns. |
| PK Catching Trainer | `214 437` | Five-minute catching drill and highscores. |
| Varrock area | `214 632` | General smoke testing. |
| Draynor area | `122 509` | General smoke testing. |
| Falador area | `304 542` | General smoke testing. |

## Feature Checklist

Ask each tester to try a few of these instead of everyone doing the same route.

| Feature | Quick test |
|---|---|
| Client character creation | Create a fresh character from `New User`, complete appearance, and choose one Void Island path. |
| Starter kits | Confirm the chosen path gives the right gear once and does not repeat on relog. |
| Subscription cards | Buy from the Lumbridge vendor, redeem, and check the wrench/profile XP-rate display. |
| Auction House UI | Use the Edgeville auctioneer or `::quickauction`; browse categories, inspect item cards, seed fixture data if needed. |
| Titles | Open `::titles`, page through categories, click a title for requirements, equip one, and confirm overhead name stays one line. |
| Rare drop beam | Toggle client loot beams, add/remove items with `::lootbeam`, and spawn/drop beam-worthy items for visuals. |
| Appearance colors | Create or change appearance; verify only Voidscape hair colors appear, classic clothing colors remain, muted Voidscape clothing colors are appended, and grounded skin tones render cleanly. |
| Global chat flags | Send `::g test`, confirm the flag icon appears beside the username, then use the wrench settings panel to toggle Country flag off/on. |
| Dynamic wilderness spawns | Stand at `(217,255)`, use `::wildhobdebug 4` or real unique IPs, and watch for the purple area message. |
| World announcements | Use preview commands, then test a real skulled Wilderness kill if multiple players are available. |
| Void Enclave boss | Enter from `(122,313)`, attack the Void Knight, test mechanics, death/logout cleanup, and rewards. |
| PK Catching Simulator | Talk to the trainer at `(214,437)`, finish or `::leave`, and check highscores. |
| Void Rush | Queue normally through the Void Herald or use `::voidrushbots 10` for a bot-backed run. |
| Launcher | Confirm animation quality, Play button states, update status text, close/minimize, and social buttons. |

## Bug Report Template

Ask testers to send this shape in Discord so reports are easy to reproduce:

```text
Character:
Time:
Feature:
Location/coords:
What I did:
What happened:
What I expected:
Screenshot/video:
Can I reproduce it? yes/no
Extra notes:
```

## Beta Guardrails

- Use disposable beta passwords.
- Keep auto-admin access to trusted testers only, and revert the temporary beta default before public launch.
- Do not treat beta economy/progression as permanent unless explicitly announced.
- If the server starts feeling laggy, stop load bots with `::loadbots stop`.
- Before a bigger friend wave, run the release checklist in `docs/RELEASE-CHECKLIST.md`.
