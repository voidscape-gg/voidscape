# World achievements

Voidscape records launch-season world firsts and qualified Wilderness PK streaks in a durable, replay-safe ledger. The ledger is the private source of truth for announcements and the read-only Legends API; chat messages are presentation only.

## Launch policy

- Feature gate: `want_world_achievements`.
- Season namespace: `world_achievement_season_id`, locked to `launch-2026` for launch.
- There is no backfill. Only events committed while the feature is enabled can win a launch record.
- Only ordinary/default player characters may claim firsts or qualified kills. Staff, elevated, and special server users cannot win them.
- A season change creates a new namespace; it does not rewrite or delete the previous season.

## Architecture

- `server/src/com/openrsc/server/content/WorldAchievementService.java` atomically claims first-skill records.
- `server/src/com/openrsc/server/content/WorldPkEvaluation.java` performs deterministic pre-settlement PK checks and values the actual post-death ground-item list.
- `server/src/com/openrsc/server/content/WorldPkSettlementService.java` records each PvP death exactly once and updates streak projections in the same settled transaction.
- `server/src/com/openrsc/server/content/CrackerCampaignTransactions.java` claims the first campaign cracker inside the existing cracker award transaction.
- `server/src/com/openrsc/server/database/WorldAchievementLedger.java` is the shared MySQL/SQLite SQL contract.
- `server/database/{mysql,sqlite}/patches/2026_07_11_add_world_achievements.sql` creates the three prefix-safe tables.
- `server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java` formats post-commit messages. It never decides a winner.

Every mutating coordinator uses `atomicallySettled(...)`. A lost COMMIT acknowledgement is accepted only when the durable row and every affected projection exactly match the attempted after-state. A confirmed rollback publishes nothing; a mixed or malformed result is quarantined as unknown rather than guessed.

## Persistence contract

### `world_achievement_records`

Immutable season records keyed by `(season_id, record_key)`. A second uniqueness constraint on `(season_id, source, source_event_key, record_type)` deduplicates source events that have their own stable identity.

Current launch records are:

| Record | Key | Subject/value | Source |
|---|---|---|---|
| First skill threshold | `first:skill:<skill-id>:<level>` | skill ID / `80`, `90`, or `99` | `skill_level` |
| First campaign cracker | `first:item:575` | item `575` / `1` | `launch_cracker_campaign` |

### `world_pk_events`

An immutable audit row keyed by a canonical UUID `death_id`. It records both character identities, optional linked account IDs, unordered character-pair IDs, qualified/rejected outcome, a stable rejection reason, skull/direct-damage/loot/Wilderness evidence, streak result, ended victim streak, and event time.

Every audited real player-versus-player death is inserted, including rejected deaths. Replaying the same UUID with identical evidence returns the stored result without changing projections. Reusing a UUID with different evidence fails closed.

### `world_pk_streaks`

A mutable per-season/per-character projection containing current streak, best streak, lifetime qualified kills in that season, and last-qualified/update times. Every audited real PvP death resets the victim's current streak. Only a qualified kill increments the killer's current streak and qualified-kill count.

The records and PK tables are internal. They contain account-link IDs and rejection details needed for anti-abuse review; they do not store raw IP addresses.

## First skill thresholds

`Skills.addExperience(...)` checks `80`, `90`, and `99` only after the player's new maximum skill level has been saved successfully. OpenPK progression is excluded. If one XP award crosses more than one threshold, the service attempts each crossed record in ascending order in one transaction, then announces only the highest newly won threshold so ordinary milestone chat is not duplicated at the same level.

An existing record, failed save, rollback, invalid season/player identity, or elevated character produces no world-first announcement.

## Qualified Wilderness PKs

A player kill qualifies only when all of these are true:

1. Both characters have distinct valid identities, are ordinary/default users, and have combat level at least `10`.
2. Death occurred in the real base-world Wilderness: instance `0`, not a configured safe zone or safe-death activity, and not a duel.
3. The victim was skulled at the captured death boundary.
4. The victim dealt positive direct player-to-player damage to the killer. Direct melee, ranged, thrown, and magic damage count; recoil, poison, environmental damage, NPC damage, and blocked damage do not.
5. The characters are not linked to the same positive web account, are not on the same non-loopback current IP, and neither has the other on their friends list.
6. The immutable post-drop list contains at least `world_pk_loot_minimum` gp of killer-owned, tradeable death loot, using positive item amount times positive definition default price. Launch locks the floor to `5000`.
7. The same unordered character pair has not already produced a qualified kill in the preceding 30 minutes.

The first failed check becomes the stable private rejection reason. Pair cooldown and loot-floor checks happen inside the settlement transaction, after the exact post-drop list exists. Ordinary RSC kill/death counters and loot behavior remain separate from this qualification system.

Qualified kills publish one baseline Wilderness message. Streaks `3`, `5`, and `10` also publish their corresponding milestone message. A later death resets the victim projection even when that death is rejected for achievement credit.

### Direct-damage boundary

The current legacy combat tracker accumulates a source player's direct damage on the target and clears that source from all targets when the source dies. Consequently, the launch check proves that the eventual victim directly damaged the killer since the victim's preceding death; it is not yet a timestamp-bounded proof that the damage occurred during only the final encounter. The account/IP/friend checks, loot floor, skull requirement, and pair cooldown still apply. If stricter encounter isolation is desired later, add an explicit combat-session or recent-hit timestamp instead of inferring it in the public API.

## First campaign cracker

Only item `575` awarded by the finite launch Cracker Hunt can claim `first:item:575`. The record is inserted inside the same transaction that persists the exact inventory, decrements the global pool, and writes `launch_cracker_campaign` provenance. A normal campaign drop announcement is always attempted after a confirmed award; the distinct world-first message is attempted only by the transaction that inserted the record.

A later/replayed award, disabled achievements, invalid season/identity, or elevated character can still receive a legitimate campaign cracker but cannot publish or replace the first-item record. Other item firsts are intentionally deferred.

## Presentation and previews

Durable state and chat presentation are separately gated:

- `want_world_achievements` enables claims and PK settlement.
- `want_world_announcements` enables global presentation.
- `want_world_milestone_announcements` covers first-skill, first-cracker, and ordinary progression milestone messages.
- `want_world_skulled_pk_announcements` covers qualified Wilderness kill and PK-streak messages.

Owner preview commands render sample messages without changing the ledger, streaks, stats, inventory, pool, or provenance. See `Commands.md` and `docs/subsystems/world-announcements.md` for the current aliases.

## Public API boundary

`GET /api/legends` is the only public ledger projection. The portal chooses its active season from `PORTAL_LEGENDS_SEASON_ID` (default `launch-2026`); callers cannot select another season. A valid empty season returns empty arrays. An absent game-DB bridge returns `503 openrsc_db_not_configured`, while missing/unreadable achievement schema fails closed as `503 legends_unavailable`.

The response contains only:

- `seasonId` and deterministic `firsts`, each with `type`, public `playerName`, allowlisted `subjectId`/`value`, and `achievedAtMs`.
- `pvp.leaders`, ranked by best streak, qualified kills, current streak, and stable public-name/internal tie-breakers. Each row contains only `rank`, public `playerName`, `currentStreak`, `bestStreak`, `qualifiedKills`, and `lastQualifiedAtMs`.

The records query accepts only first-skill thresholds `80`, `90`, and `99` for skill IDs `0..19`, plus first campaign cracker item `575`. The PK query reads only the aggregate `world_pk_streaks` projection; it never reads or publishes raw `world_pk_events`. Invalid names/numbers and impossible streak projections are omitted. The endpoint never returns player/account IDs, victim data, death IDs, raw or derived network identity, rejection reasons, record keys, sources, source-event keys, private detail, update times, or internal anti-abuse evidence. Explicit SQL column lists and explicit output mapping keep future private schema additions private.

Responses are deterministic, `cache-control: no-store`, GET-only in public mode, and read through SQLite's `-readonly` path. `tests/portal-legends-api.sh` locks the exact projection, ordering, season isolation, private-data canaries, no-write database hash, and fail-closed behavior.

### Legends page

`/legends` is the public presentation of that exact API contract. It makes one uncached request on load and another only when the player explicitly retries; it does not poll or accept a caller-selected season. The standalone `legends.js` validates the allowlisted response again, maps stable skill IDs `0..19` to their public names, formats record times in UTC, and creates every player-controlled value with `createElement`/`textContent`. Unknown response fields and backend error details are never rendered.

The page keeps chronological world firsts and API-ranked Wilderness leaders separate, with public summary counts plus dedicated loading, valid-empty, and generic unavailable states. Desktop uses two columns; mobile uses stacked cards rather than a horizontally scrolling table. Browser evidence in `tmp/portal-legends-slice7-v1/summary.json` covers populated desktop `1440x1000`, populated/PK/empty/error mobile `390x844`, retry recovery, no horizontal overflow, no private-field text, and zero browser warnings/errors.

## Verification and operations

Focused deterministic suites:

```bash
tests/world-achievements-unit.sh
tests/world-pk-evaluation-unit.sh
tests/world-pk-settlement-unit.sh
tests/world-pk-integration-unit.sh
tests/pvp-evidence-boundary-unit.sh
tests/pvp-direct-damage-tracking-unit.sh
tests/cracker-campaign-first-announcement-unit.sh
```

Before changing a launch season, archive the existing season, confirm the new identifier is deliberate, and rerun the launch-config and database-migration checks. Disabling `want_world_achievements` stops new records and qualified-PK settlement but preserves existing rows; chat flags can be disabled independently without changing durable progression.
