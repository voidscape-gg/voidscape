# Website Account Management Architecture

Voidscape's game server currently treats one `players` row as both the login identity and the playable character. The website target is different: one web account should own up to 10 game characters, and public signup should be portal-first because the server is not released yet.

This document records the safe path from the current OpenRSC model to the future account portal.

## Current Model

Authoritative schema lives in `server/database/sqlite/core.sqlite` and `server/database/mysql/core.sql`.

- `players` stores identity, password hash, salt, email, rank, position, appearance, combat, totals, privacy settings, and login metadata.
- `curstats`, `maxstats`, `experience`, and `capped_experience` store skill state by `playerID`.
- `invitems`, `bank`, `itemstatuses`, and optional `equipped` store item state. Without the equipment-tab addon, wielded items are inferred through inventory item status.
- `player_cache` stores custom Voidscape state such as titles, web-account links, account subscription mirrors, and Void Island path state.
- Login flow in `LoginRequest` and `CharacterCreateRequest` looks up or creates by username directly.

Implication: adding web accounts must not rename or split `players` yet, but client packet registration can be disabled so new players enter through the portal.

## Target Model

Add web ownership alongside existing characters instead of replacing game login first.

Suggested tables:

### `web_accounts`

- `id`
- `email_canonical`
- `email_display`
- `password_hash` (nullable for Google-only accounts)
- `status`: `active`, `locked`, `review`
- `subscription_expires_at`
- `created_at`
- `updated_at`

Constraints:

- unique `email_canonical`
- production canonicalization should collapse common alias forms before uniqueness checks; the local portal currently lowercases every address, strips `+tag` aliases, maps `googlemail.com` to `gmail.com`, and removes Gmail dots.

### `web_account_identities`

- `account_id`
- `provider`: currently `google`
- `provider_subject`: Google OpenID Connect `sub`
- `email_canonical`
- `email_display`
- `display_name`
- `avatar_url`
- `email_verified`
- `last_login_at`

Constraints:

- unique `(provider, provider_subject)`
- unique `(account_id, provider)`

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
- `type`: `weekly_subscription_card`, `starter_free_subscription`
- `status`: `granted`, `consumed`, `revoked`
- `source`
- `created_at`
- `consumed_at`

Founder-pass tables from `docs/PRELAUNCH-FOUNDER-PASS.md` can either share `web_accounts` once launched or stay separate until migration.

The current schema draft lives in `web/portal/schema/`:

- `sqlite/001_web_accounts.sql`
- `mysql/001_web_accounts.sql`

It is intentionally portal-owned reference SQL and is not auto-applied by the OpenRSC server. The schema adds `web_account_identities`, `web_character_link_challenges`, `web_founder_reservations`, `web_founder_referrals`, `web_audit_events`, and `web_abuse_signals` alongside the core account/session/character/entitlement tables. `scripts/test-portal-schema.sh` applies the SQLite variant and verifies the important constraints, including roster slots `0-9`, unique linked `player_id`, unique Google identity links, and unique active founder names.

## Compatibility Strategy

Phase 1 keeps game login untouched but makes signup portal-first:

1. A player logs into the PC client with the character username and game password created by the portal.
2. The website authenticates by web account email/password or Google OpenID Connect.
3. Web-created characters still create normal `players` rows and then link them in `web_account_characters`.
4. Public client packet registration is disabled; the client should direct new users to the portal.
5. Subscription state lives at the web-account level. The game bridge stores `web_account_id` on each character and `acct_sub:<webAccountId>` as the account-wide expiry in global `player_cache`.

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
- `player_cache`: active title, `web_account_id`, account subscription expiry bridge, and legacy per-character subscription fields for local snapshot compatibility.
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
6. Starter entitlement to weekly subscription card conversion.
7. Optional client login changes only after the web account model is stable.

## Local Prototype API

`web/portal/dev-server.mjs` is the current API scaffold. It is intentionally local-only and uses a JSON store under `/tmp/voidscape-portal-api` by default, or `PORTAL_DATA_DIR` when provided.

It currently proves:

- founder/reserved-name shape
- starter-card reward state for the free weekly subscription card
- public site payloads for status, XP rates, news, downloads, highscores, market intel, and activity feed
- web account registration/login flow
- local dev Google sign-in flow that stores a `google` provider identity and returns a normal portal bearer session
- `scrypt` password hashing with per-password salts
- bearer sessions stored server-side as token hashes
- local account security controls for password rotation, hashed recovery-code generation, and ending other active sessions
- local recovery-code password reset through `POST /api/accounts/recover-password`; a valid code is consumed, all old sessions are revoked, the password hash is rotated, and a fresh bearer session is issued
- server-side 10-character roster cap
- character-state API responses shaped for the portal
- OpenRSC SQLite-backed character creation through `POST /api/characters` when `PORTAL_OPENRSC_DB` is configured; the endpoint creates the normal `players`, `curstats`, `maxstats`, `experience`, and `capped_experience` rows, then stores the created player id in the local portal roster state
- local character link challenges with hashed one-time codes and a dev simulation path
- account-wide subscription-card redemption state
- starter-card abuse controls that store salted hashes for IP/email/identity signals, keep suspicious new accounts active, and withhold only the free starter card for staff review after repeated non-local IP grants
- token-gated local staff endpoints for account lookup, status changes, subscription grants/clears, starter-card grants/revokes, and session revocation
- explicit `501` stubs for production Google OAuth and subscription-card payment checkout
- optional read-only OpenRSC SQLite saved-character snapshots when `PORTAL_OPENRSC_DB` or `OPENRSC_SQLITE_DB` is configured
- portal account-management schema constraints through `scripts/test-portal-schema.sh`

It does not yet prove:

- production email verification
- Cloudflare Turnstile or rate limits
- production-safe OpenRSC character ownership/linking
- production OpenRSC database writes outside the local SQLite dev bridge
- real in-game `::link` command handling or signed game-server verification callback
- production Google OAuth redirect/callback handling and ID-token verification
- production payment checkout/webhook handling
- production email-delivered password reset or account-recovery support queue
- production staff identity/RBAC beyond the local bearer-token guard

### Starter-card abuse policy

The free subscription card is protected at the reward boundary rather than by adding heavy signup hurdles. Every new account can still be created and can still enter the game through the portal-created character path. The one-time starter card is granted only when the account passes server-side checks:

- one active starter-card entitlement per web account
- one canonical email per web account, with common alias normalization
- one Google provider subject per linked Google account
- salted abuse-signal hashes for signup IP, email, and identity
- a configurable daily limit for starter-card grants from the same non-local IP bucket

When the IP bucket limit is exceeded, the account remains `active`; only the free starter card is marked as review-required and not mirrored into the OpenRSC `starter_card:<webAccountId>` cache. Staff can inspect the hashed signal history and grant the card manually if the cluster is legitimate.

This keeps the normal launch path low friction while preventing throwaway accounts from reliably minting unlimited free subscription cards.

### Recovery and support policy

The low-friction recovery path is:

1. The player signs in while they still have access and generates one-time recovery codes.
2. If they later lose the password, `POST /api/accounts/recover-password` verifies a code hash plus canonical email, consumes that code, rotates the `scrypt` password hash, revokes old sessions, and returns a new session.
3. If they lose both login and recovery codes, staff support must verify evidence out of band and then use the admin API to review/lock, revoke sessions, or restore access.

Production still needs email delivery, provider-backed Google OAuth, and a staff identity/RBAC layer before this becomes an internet-facing recovery system.

### Read-only OpenRSC snapshot endpoint

`GET /api/openrsc/characters/:username` is a local development bridge for UI iteration. It is available only when `PORTAL_OPENRSC_DB=/path/to/server/inc/sqlite/voidscape.db` or `OPENRSC_SQLITE_DB=/path/to/db` is set.

The endpoint uses `sqlite3 -readonly -json` and returns portal-ready character state:

- `players`: id, username, combat, skill total, x/y, kills, NPC kills, deaths, quest points, last login, online flag, and appearance columns
- `player_cache`: `player_title_active`, `web_account_id`, global `acct_sub:<webAccountId>`, and `void_path`
- `invitems` joined to `itemstatuses` for wielded equipment
- optional `equipped` joined to `itemstatuses` when that table exists
- `server/conf/server/defs/ItemDefs*.json` for item names, wear slots, and appearance IDs
- `server/src/com/openrsc/server/content/PlayerTitle.java` for active title display names

It does not authenticate ownership or mutate game data. Production account linking should require proof of character control before exposing private saved-state data outside local development.

### Local OpenRSC character creation

When `PORTAL_OPENRSC_DB` points at a local SQLite game database, `POST /api/characters` creates a real game character instead of only a local preview. Because the current PC client still logs in with `character name + character password`, the request must include a 4-20 character `gamePassword`. The dev server stores that password using the legacy salted hash format accepted by `DataConversions.checkPassword`, inserts the normal player and skill rows, stamps the created player cache with `web_account_id`, snapshots the created character back through the existing OpenRSC read path, and records the created `playerId` in local portal roster state.

This is deliberately still a local bridge. It does not bypass Void Island starter-path onboarding, does not implement a web-account character picker in the client, and does not create production Google/OAuth account records in OpenRSC.

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
- Account-level subscription changes now use the portal account as source of truth; legacy per-character subscription fields should not be used for new players.
- Character-picker login would be a protocol/client change and is intentionally deferred.
