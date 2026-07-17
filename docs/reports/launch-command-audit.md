# Launch command audit

Date: 2026-06-26

## Scope

Audited typed in-game command entry through `CommandHandler` and the command plugins in:

- `server/plugins/com/openrsc/server/plugins/authentic/commands/RegularPlayer.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/PlayerModerator.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Moderator.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/SuperModerator.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java`
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Event.java`
- `server/plugins/com/openrsc/server/plugins/custom/commands/*.java`
- `server/plugins/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulator.java`

## Launch policy

Public launch should set:

```yaml
custom_features:
	production_command_lockdown: true
	want_beta_onboarding_guide: false
```

`production_command_lockdown` is deliberately separate from `want_beta_onboarding_guide`. The old beta guard only applied while the beta guide was enabled; launch safety now has its own switch.

## Owner-only under production lockdown

The central command router blocks non-owner use of high-risk command families before command plugins execute:

- Economy mutation: item/noted item spawns, bank item spawns, item removal, ground-item DB mutation, fill/unfill bank, stock groups, auction fixtures, drop farming/waves.
- NPC/world mutation: NPC spawning, global NPC events, holiday drops, wilderness rule/debug controls, world reloads, scenery reset events.
- Account/progression mutation: rank changes, cache writes/deletes, quest completion/stage edits, stat/XP setters, password copy, login/register simulation.
- Movement/combat state: direct teleports, returns, summons, group teleports, healing, damage/kill commands, skull toggles, freeze XP/current stat setters.
- Server/runtime: restart, shutdown, update, saveall, SSL reload, monitor tuning, IP/connections caps, load bots, cinematic/load-test helpers, idle connection cleanup.
- Staff/dev presentation and QA: invisibility/invulnerability, morph/possess/Lain/weird modes, staff icon spoofing, forced NPC/player speech, dev object/NPC/content test commands, Undead Siege/Void Colossus QA commands.

## Still allowed by normal rank gates

These remain available to the appropriate rank because they are operational rather than launch-economy destructive:

- Player moderation: mute/unmute, global mute/unmute, ban/unban, jail/release, kick.
- Support lookup: player info, inventory/bank inspection, alternate-character checks, quest/cache reads, IP-ban reads, uptime.
- Public commands: chat, the aggregate online count, rested XP, loot beams, privacy
  toggles, the duel journal, and PK Catching `::leave`.
- Admin read-only integrity tools: `::integrity`, `::receipts`, and `::balancereport` summary/read views. `::balancereport reset` is owner-only.

## Regular-user production restrictions

With production command lockdown enabled, exact `Group.USER` accounts are also
blocked from the reviewed privacy-sensitive, diagnostic, legacy-preference, and
shortcut command set. This includes unique/online lists and locations, server time
and coordinates, group and kill/minigame diagnostics, Void Arena commands, title and
quick-bank shortcuts, QoL/cert opt-outs, receipt/KC toggles, legacy trade/language/bank
PIN settings, tutorial skip, holiday-drop status aliases, and global message-color
customization. Every spelling is gated centrally before plugin dispatch; staff access
and non-production regular-player behavior are unchanged.

## Beta-only player commands

`::beta`, `::farmkit`, `::farmsim`, `::farmcal`, `::codes`, and `::refcodes` are blocked centrally when `want_beta_onboarding_guide` is off. If a trusted beta flag is accidentally left on while `production_command_lockdown` is enabled, those beta player commands are still blocked for every non-owner account. Public command help hides `::beta` and beta referral code help when the beta guide flag is off.

## Spot checks for final playthrough

- Non-owner admin: `::item 10 1`, `::noteditem 10 1`, `::spawnnpc 1`, `::setstats attack 99`, `::goto lumbridge`, `::loadbots status`, and `::workbenchauctionfixture` should all say the command is owner-only.
- Owner: the same commands should reach their normal syntax/action paths.
- Regular player: `::commands` should not list `::beta` or `::codes` on launch config.
- Regular player: representative restricted aliases such as `::uniqueonline`,
  `::arena stats`, `::titles`, `::b`, and `::drop` should return the regular-player
  production denial and should not appear in `::commands`.
- Regular player: `::bug test report from launch smoke` should still accept a normal bug report.
- Trusted beta/staging world with `want_beta_onboarding_guide: true` and `production_command_lockdown: false`: `::beta`, `::farmkit`, `::farmsim`, and `::codes` should remain available for trusted test windows.
- Production world with both flags accidentally true: a non-owner `::beta` / `::farmkit` / `::farmsim` / `::codes` attempt should say the beta command is disabled during public launch.
