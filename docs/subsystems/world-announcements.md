# World announcements

Voidscape's world-announcement layer sends styled server-side chat broadcasts for high-signal social events without changing client packets. Chat is presentation, not the source of truth: launch-season firsts and qualified PKs publish only after their durable transaction commits.

## Entry points

- `server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java` owns message formatting, presentation gates, ordinary milestone cache keys, and read-only preview fixtures.
- `server/src/com/openrsc/server/model/world/World.java` creates the service and calls it when a brand-new account enters the world.
- `server/src/com/openrsc/server/model/Skills.java` publishes committed first-skill records and ordinary skill/total milestones.
- `server/src/com/openrsc/server/content/CrackerCampaignService.java` publishes the normal cracker-drop line and, only for the inserting transaction, the first-campaign-cracker line.
- `server/plugins/com/openrsc/server/plugins/custom/misc/WorldAnnouncements.java` settles achievement-enabled PvP deaths only after the final drop list exists, then publishes qualified results.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java` provides deterministic preview modes through `::announcepreview` and `::worldannouncepreview`.

## Config

The feature uses server-only keys:

- `want_world_announcements` — master presentation switch.
- `want_world_milestone_announcements` — ordinary skill/total milestones plus committed first-skill and first-cracker messages.
- `want_world_skulled_pk_announcements` — legacy skulled-death fallback or achievement-enabled qualified-PK/streak messages.
- `want_world_new_player_announcements` — first-join welcome.
- `want_world_achievements` — durable first/qualified-PK progression, independent of presentation.

The base presentation key and the relevant family key must both be enabled. These keys are not sent in the server-config packet, so they require no client version or opcode change. Disabling presentation does not undo or pause a durable transaction that is otherwise enabled.

## Ordinary milestones

Ordinary skill announcements fire when a player crosses level `90`, `95`, `99`, or the configured `player_level_limit`. Total-level announcements fire when a player crosses `500`, `750`, `1000`, `1250`, `1500`, `1750`, or `2000`.

Each ordinary announcement is persisted through a player cache key such as `void_announce_skill_14_99` or `void_announce_total_1000`, preventing repeats after relog or restart. When a player wins a world-first record at the same level, the matching ordinary skill line is suppressed so the event is broadcast once at the higher-signal tier.

## Durable world firsts

First skill levels `80`, `90`, and `99` are announced only when `WorldAchievementService` returns a newly committed record after the maximum-skill save succeeds. Existing records, rollback, unknown settlement, staff/elevated characters, and invalid/disabled seasons publish nothing.

The first launch-campaign Christmas cracker is announced only when the award transaction inserts `first:item:575` alongside the persisted inventory, pool decrement, and provenance. The normal `[Cracker Hunt]` drop line is attempted once for every confirmed award; the separate `[World First]` line follows only for that inserting transaction. The two post-commit sends are independently best effort, so a chat failure cannot roll back or duplicate the item transaction.

See `docs/subsystems/world-achievements.md` for ledger and eligibility details.

## First joins

New-player announcements fire when a character with `login_date = 0` successfully enters the world. Duplicate prevention is stored in global `player_cache` with `new_join_acct:<webAccountId>` for portal-linked accounts or `new_join_char:<playerId>` for unlinked characters. This keeps the welcome message once per real portal account when possible and once per character as the fallback.

## Wilderness PKs

With `want_world_achievements: true`, the passive `PlayerDeathDropTrigger` waits until the immutable death-drop list exists, evaluates and settles the real PvP death, and broadcasts only a newly applied qualified kill. Replays and rejected deaths publish nothing. Every qualified result gets one baseline line containing killer, victim, exact qualified loot value, and Wilderness level; streak values `3`, `5`, and `10` get one additional milestone line.

With world achievements disabled, the pre-existing passive `PlayerKilledPlayerTrigger` remains as a compatibility fallback and publishes the simpler skull-has-fallen message when the defeated player was skulled in the Wilderness. Neither plugin takes over death, loot, skull, or ordinary kill-count handling.

## Read-only previews

`::announcepreview <mode>` and alias `::worldannouncepreview <mode>` force sample presentation even when announcement flags are off. They call message formatters only and do not write stats, cache, achievement records, PK events/streaks, inventory, campaign pool, or provenance.

| Mode | Sample output |
|---|---|
| `skill` | Existing level-99 Mining milestone |
| `total` | Existing 1000-total milestone |
| `pk` | Existing legacy skulled-Wilderness line |
| `newplayer` | Existing first-join welcome |
| `firstskill` | First level-80 Mining line |
| `qualifiedpk` | Qualified-PK baseline only: Test Rival, 5000 gp, level-20 Wilderness |
| `pk3`, `pk5`, `pk10` | The selected streak milestone only |
| `firstcracker` | First campaign-cracker line only |

Both commands are owner-only while `production_command_lockdown` is enabled because previews broadcast globally. On private QA worlds without production lockdown, the command plugin retains its normal admin-only access.

Focused preview coverage is `tests/world-announcement-preview-unit.sh`; authorization coverage is part of `tests/command-lockdown-unit.sh`.
