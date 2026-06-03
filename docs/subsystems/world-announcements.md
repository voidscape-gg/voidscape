# World announcements

Voidscape's world-announcement layer sends styled, server-side chat broadcasts for major social events without changing client packets.

## Entry points

- `server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java` owns formatting, config checks, and milestone duplicate prevention.
- `server/src/com/openrsc/server/model/world/World.java` creates the service and exposes it through `getWorldAnnouncementService()`.
- `server/src/com/openrsc/server/model/Skills.java` calls the service when a player's max skill level increases.
- `server/plugins/com/openrsc/server/plugins/custom/misc/WorldAnnouncements.java` listens to `PlayerKilledPlayerTrigger` for Wilderness PK broadcasts.
- `server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java` provides `::announcepreview [skill|total|pk]` for local visual checks.

## Config

The feature is controlled by server-only config keys:

- `want_world_announcements`
- `want_world_milestone_announcements`
- `want_world_skulled_pk_announcements`

All three must be enabled for both milestone and skulled-PK broadcasts to appear. These keys are intentionally not sent in the server-config packet; no client version bump is required.

## Milestones

Skill announcements fire when a player crosses level `90`, `95`, `99`, or the configured `player_level_limit`.

Total-level announcements fire when a player crosses `500`, `750`, `1000`, `1250`, `1500`, `1750`, or `2000` total level.

Each announcement is persisted through a player cache key such as `void_announce_skill_14_99` or `void_announce_total_1000`, so a player does not repeat the same broadcast after relogging or after a server restart.

## Wilderness PKs

The PK announcement only fires when:

1. The killer is a player.
2. The defeated player is in the Wilderness at death time.
3. The defeated player is currently skulled.
4. `want_world_announcements` and `want_world_skulled_pk_announcements` are enabled.

The passive plugin returns `false` from `blockPlayerKilledPlayer`, matching the existing passive trigger pattern. It records the social event but does not take over death, loot, skull, or kill-count handling.
