# Website Account Management Architecture

Voidscape's game server currently treats one `players` row as both the login identity and the playable character. The website target is different: one web account should own up to 10 game characters, while the existing PC/Android client login keeps working during the transition.

This document records the safe path from the current OpenRSC model to the future account portal.

## Current Model

Authoritative schema lives in `server/database/sqlite/core.sqlite` and `server/database/mysql/core.sql`.

- `players` stores identity, password hash, salt, email, rank, position, appearance, combat, totals, privacy settings, and login metadata.
- `curstats`, `maxstats`, `experience`, and `capped_experience` store skill state by `playerID`.
- `invitems`, `bank`, `itemstatuses`, and optional `equipped` store item state. Without the equipment-tab addon, wielded items are inferred through inventory item status.
- `player_cache` stores custom Voidscape state such as titles, subscription expiry, and Void Island path state.
- Login flow in `LoginRequest` and `CharacterCreateRequest` looks up or creates by username directly.

Implication: adding web accounts must not rename or split `players` until the client login contract is deliberately changed.

## Target Model

Add web ownership alongside existing characters instead of replacing game login first.

Suggested tables:

### `web_accounts`

- `id`
- `email_canonical`
- `email_display`
- `password_hash`
- `status`: `active`, `locked`, `review`
- `created_at`
- `updated_at`

Constraints:

- unique `email_canonical`

### `web_account_sessions`

- `id`
- `account_id`
- `session_token_hash`
- `created_at`
- `expires_at`
- `last_seen_at`
- `ip_hash`
- `user_agent_hash`

### `web_account_characters`

- `account_id`
- `player_id`
- `slot`
- `is_primary`
- `created_at`

Constraints:

- unique `player_id`
- unique `(account_id, slot)`
- max 10 rows per `account_id`, enforced in application code and checked transactionally before insert

### `web_entitlements`

- `id`
- `account_id`
- `type`: `weekly_subscription_card`, `founder_free_subscription`
- `status`: `granted`, `consumed`, `revoked`
- `source`
- `created_at`
- `consumed_at`

Founder-pass tables from `docs/PRELAUNCH-FOUNDER-PASS.md` can either share `web_accounts` once launched or stay separate until migration.

The current schema draft lives in `web/portal/schema/`:

- `sqlite/001_web_accounts.sql`
- `mysql/001_web_accounts.sql`

It is intentionally portal-owned reference SQL and is not auto-applied by the OpenRSC server. The schema adds `web_character_link_challenges`, `web_founder_reservations`, `web_founder_referrals`, `web_audit_events`, and `web_abuse_signals` alongside the core account/session/character/entitlement tables. `scripts/test-portal-schema.sh` applies the SQLite variant and verifies the important constraints, including roster slots `0-9`, unique linked `player_id`, and unique active founder names.

## Compatibility Strategy

Phase 1 keeps game login untouched:

1. A player can still log into the PC client with the character username and existing password.
2. The website authenticates by web account email/password.
3. Characters are linked to a web account after proof of control, initially by matching existing credentials or by an in-game verification code.
4. Web-created characters still create normal `players` rows and then link them in `web_account_characters`.
5. Subscription state should eventually live at the web-account level, then mirror to character `player_cache` on login until the game server reads account entitlements directly.

Phase 2 can optionally introduce a character-picker login, but that is a protocol/client feature and must follow `docs/subsystems/networking-protocol.md`.

## Character State Contract

The portal character card should be backed by one API response shaped roughly like this:

```json
{
  "id": 123,
  "username": "Zamak42",
  "title": "The Conqueror",
  "combatLevel": 87,
  "totalLevel": 1194,
  "questPoints": 47,
  "wildernessKills": 28,
  "lastLoginAt": 1780539120,
  "location": { "x": 122, "y": 648, "label": "Lumbridge" },
  "appearance": {
    "male": true,
    "hairColour": 2,
    "topColour": 8,
    "trouserColour": 14,
    "skinColour": 0,
    "headSprite": 1,
    "bodySprite": 2
  },
  "equipment": [
    { "slot": "weapon", "itemId": 81, "catalogId": 81, "name": "Rune 2h sword" }
  ],
  "subscription": {
    "active": true,
    "expiresAt": 1781139120,
    "combatXpRate": 10,
    "skillXpRate": 6
  }
}
```

Data sources:

- `players`: username, rank, combat, skill_total, x/y, login_date, appearance columns, kills, quest_points.
- `player_cache`: active title and subscription expiry.
- `invitems` joined to `itemstatuses`: currently wielded items when the equipment tab is disabled.
- `equipped` joined to `itemstatuses`: equipment-tab path when enabled.
- item definitions: item display names and wear slots.

If avatar rendering is not ready, the API should still return exact appearance/equipment data and the frontend can render a clear placeholder.

## API Slice Order

1. Static portal prototype with local state and exact data contract.
2. Local prototype API for public status, founder-pass state, account sessions, character roster, character creation previews, and subscription redeem previews.
3. `web_accounts` and session authentication with secure password hashing.
4. Character linking and roster read APIs.
5. Web character creation endpoint that creates a normal `players` row and links it transactionally.
6. Founder entitlement to weekly subscription card conversion.
7. Optional client login changes only after the web account model is stable.

## Local Prototype API

`web/portal/dev-server.mjs` is the current API scaffold. It is intentionally local-only and uses a JSON store under `/tmp/voidscape-portal-api` by default, or `PORTAL_DATA_DIR` when provided.

It currently proves:

- founder reservation shape
- referral reward state for the free weekly subscription card
- public site payloads for status, XP rates, news, downloads, highscores, market intel, and activity feed
- web account registration/login flow
- `scrypt` password hashing with per-password salts
- bearer sessions stored server-side as token hashes
- local account security controls for password rotation, hashed recovery-code generation, and ending other active sessions
- server-side 10-character roster cap
- character-state API responses shaped for the portal
- local character link challenges with hashed one-time codes and a dev simulation path
- subscription-card redemption state
- optional read-only OpenRSC SQLite saved-character snapshots when `PORTAL_OPENRSC_DB` or `OPENRSC_SQLITE_DB` is configured
- portal account-management schema constraints through `scripts/test-portal-schema.sh`

It does not yet prove:

- production email verification
- Cloudflare Turnstile or rate limits
- production-safe OpenRSC character ownership/linking
- OpenRSC database writes
- real in-game `::link` command handling or signed game-server verification callback
- production password reset, recovery-code consumption, or email recovery
- staff/admin authorization

### Read-only OpenRSC snapshot endpoint

`GET /api/openrsc/characters/:username` is a local development bridge for UI iteration. It is available only when `PORTAL_OPENRSC_DB=/path/to/server/inc/sqlite/voidscape.db` or `OPENRSC_SQLITE_DB=/path/to/db` is set.

The endpoint uses `sqlite3 -readonly -json` and returns portal-ready character state:

- `players`: id, username, combat, skill total, x/y, kills, NPC kills, deaths, quest points, last login, online flag, and appearance columns
- `player_cache`: `player_title_active`, `void_sub_expires`, legacy `void_subscription`, and `void_path`
- `invitems` joined to `itemstatuses` for wielded equipment
- optional `equipped` joined to `itemstatuses` when that table exists
- `server/conf/server/defs/ItemDefs*.json` for item names, wear slots, and appearance IDs
- `server/src/com/openrsc/server/content/PlayerTitle.java` for active title display names

It does not authenticate ownership or mutate game data. Production account linking should require proof of character control before exposing private saved-state data outside local development.

### Local link challenge flow

The prototype endpoints are:

- `POST /api/character-links/start`
- `POST /api/character-links/simulate-verify`

Starting a challenge requires an authenticated portal session and a configured OpenRSC SQLite DB. The API loads a read-only snapshot, verifies the account has an open roster slot or matching preview row, revokes older pending challenges for the same account/name pair, generates a random `VLINK-...` code, stores only its SHA-256 hash, and returns the one-time `::link <code>` command.

The simulate endpoint is deliberately local-only. It checks the submitted code against the stored hash, reloads the OpenRSC snapshot, then merges that saved character into the account roster with `linkStatus: "linked"` and `playerId` set to the OpenRSC `players.id`. Production should replace this simulate endpoint with an in-game `::link` command or a signed internal callback from the game server.

## Risks

- Password handling must be modern. Do not reuse the legacy RSC password hash for website authentication.
- `players.username` uniqueness remains the game identity constraint.
- A roster cap must be enforced in a database transaction, not only in JavaScript.
- Account-level subscription changes need careful migration from the current per-character `player_cache` expiry.
- Character-picker login would be a protocol/client change and is intentionally deferred.
