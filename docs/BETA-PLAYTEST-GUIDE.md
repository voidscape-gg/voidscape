# Voidscape Friend Beta Playtest Guide

This beta flow runs against one hosted Voidscape server: friends can create their account and first character directly in the desktop client, or use the portal flow to reserve/create the account, claim the starter subscription card in-game, download the launcher, click Play, choose a starter path, and log in.

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

6. Newly created characters use the normal User rank. Promote only trusted testers manually when admin-only commands are needed.

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

1. Open the portal.
2. Reserve a username and create the first game login.
3. Download/open `VoidscapeLauncher.jar`.
4. Click Play. First launch may download the client/cache.
5. Log in with the character username and game password from the portal.
6. Customize appearance.
7. On Void Island, talk to the Void Herald at `(24,24)`.
8. Choose one starter path.
9. Pick a beta-guide topic from the starter menu, or close it and reopen it later with `::beta`.
10. Claim the starter subscription card from the Lumbridge Subscription Vendor, then redeem it when ready.
11. Play normally or use the test commands below.

Beta referral rewards:

- Share the invite link shown after reserving a username.
- When another beta signup uses that invite link, the referrer earns one additional `VOID-XXXX-XXXX` subscription-card code.
- The referrer can return to the signup page with the same email to view earned referral reward codes.
- Referral reward codes are redeemed at the Lumbridge Subscription Vendor, the same as the original signup code.

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
| `::beta` | Reopen the beta guide menu for commands, coords, items, and features to try. |
| `::farmkit 40` | Apply flat 40 Attack/Strength/Defense/Hits, reset ranged/prayer/magic to 1, and equip full adamant, adamant battle axe, and ruby strength amulet. |
| `::farmkit 60` | Apply flat 60 melee stats, reset ranged/prayer/magic to 1, and equip full adamant, adamant 2H, and ruby strength amulet. |
| `::farmkit 80` | Apply flat 80 melee stats, reset ranged/prayer/magic to 1, and equip full adamant, rune battle axe, and ruby strength amulet. |
| `::farmkit 99` | Apply flat 99 melee stats, reset ranged/prayer/magic to 1, and equip full rune, rune 2H, and ruby strength amulet. |
| `::farmsim start` | Reset your FarmSim sample before testing one area or NPC group. |
| `::farmsim` | Project your sampled NPC kills into a one-hour expected-loot popup with item sprites and quantities. |
| `::farmsim 30m` | Project the same sample over another duration from 5 minutes to 4 hours. |
| `::farmsim status` | Show what NPCs are currently in your FarmSim sample. |
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

Use these for fast beta testing only after manually promoting a trusted tester account.

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
| `::announcepreview newplayer` | Preview a first-join welcome world message. |
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

## FarmSim Drop-Rate Testing

Use this when tuning whether an NPC group feels worth farming for one hour. The projection uses your real sampled kill pace, so walking, spawn spacing, competition, missed hits, defense, and weapon speed all matter.

1. Pick one area or one NPC group to test.
2. Apply one melee kit: `::farmkit 40`, `::farmkit 60`, `::farmkit 80`, or `::farmkit 99`.
3. Stand where a normal player would farm that group.
4. Use `::farmsim start` if you want to reset without changing kits.
5. Kill a small representative sample. Aim for at least 5-10 low-level kills or 3-5 slower kills.
6. Run `::farmsim`.
7. Screenshot the popup and include the kit, location, NPCs, sample size, and whether the area felt too slow, too crowded, or too rewarding.

Good starter FarmSim routes:

| Kit | Suggested NPCs | Coordinates / area | What this checks |
|---|---|---:|---|
| 40 | Goblins | Varrock/Lumbridge outskirts | Low-level money, runes, arrows, and visible junk drops. |
| 40 | Cows or men | Lumbridge / nearby towns | Very early bones, hides, food pacing, and baseline starter farming. |
| 60 | Hobgoblins | Wilderness hobgoblins `(217,255)` | Midgame combat drops, spawn pressure, and Wilderness crowd tuning. |
| 60 | Skeletons | Edgeville dungeon-style routes | Bone drops plus rune/coin pacing for slower low-mid NPCs. |
| 80 | Lesser demons | Karamja / demon spots | Rare-table feel, rune/coin value, and longer kill samples. |
| 80 | Void dungeon NPCs | Void Dungeon `72,3252` | Custom route value and unsafe-area reward pressure. |
| 99 | Dragons or high demons | Existing boss/high-combat routes | High-stat hourly loot ceiling and long-respawn pain points. |
| 99 | Void Knight / custom boss routes | Void Enclave boss paths | Boss-adjacent pacing, supply friction, and reward expectations. |

Report FarmSim findings like this:

```text
FarmSim:
Kit:
Area/coords:
NPCs:
Sample kills:
Projection duration:
Screenshot:
Felt too low/fair/too high:
Notes on spawns, walking, banking, competition:
```

## Coordinates

Use exact coordinates with `::goto <x> <y>`.

| Place | Coordinates | What to test |
|---|---:|---|
| Void Island Herald | `24 24` | New-character starter choice. |
| Lumbridge home | `120 648` | Spawn, home flow, nearby vendor. |
| Subscription Vendor | `126 649` | Claim a reserved free subscription card; no shop opens. |
| Edgeville bank | `217 449` | Banking, nearby Auction House, PvP access. |
| Void Auctioneer | `217 460` | Auction House UI, listing, buying, intel. |
| Void Rift | `192 443` | Enter prompt to Void Enclave. |
| Void Enclave | `113 314` | Safe zone, amenities, waystones, boss entry. |
| Void Dungeon entrance | `112 296` | Unsafe Wilderness rift; requires 100,000 coins to enter. |
| Void Dungeon | `72 3252` | Compact shared underground Wilderness cave, void NPC route, exit rift at `72 3250` returns to `112 297`. |
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
| Client character creation | Create a fresh character through the portal/account flow, complete appearance, and choose one Void Island path. |
| Starter kits | Confirm the chosen path gives the right gear once and does not repeat on relog. |
| Subscription cards | Buy from the Lumbridge vendor, redeem, and check the wrench/profile XP-rate display. |
| Auction House UI | Use the Edgeville auctioneer or `::quickauction`; browse categories, inspect item cards, seed fixture data if needed. |
| Titles | Open `::titles`, page through categories, click a title for requirements, equip one, and confirm overhead name stays one line. |
| Rare drop beam | Toggle client loot beams, add/remove items with `::lootbeam`, and spawn/drop beam-worthy items for visuals. |
| FarmSim projections | Use `::farmkit`, kill a few NPCs in one area, run `::farmsim`, screenshot the item-sprite popup, and report whether the projected hour feels fair. |
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
- Keep staff-rank access to trusted testers only, and demote test accounts before public launch.
- Do not treat beta economy/progression as permanent unless explicitly announced.
- If the server starts feeling laggy, stop load bots with `::loadbots stop`.
- Before a bigger friend wave, run the release checklist in `docs/RELEASE-CHECKLIST.md`.
