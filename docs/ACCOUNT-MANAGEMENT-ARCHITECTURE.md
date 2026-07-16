# Website Account Management Architecture

Voidscape's game server currently treats one `players` row as both the login identity and the playable character. The website target is different: one web account should own up to 10 game characters, and web signup stays portal-first while desktop and native Android can create a single game character directly in-client.

This document records the safe path from the current OpenRSC model to the future account portal.

## Current Model

Authoritative schema lives in `server/database/sqlite/core.sqlite` and `server/database/mysql/core.sql`.

- `players` stores identity, password hash, salt, email, rank, position, appearance, combat, totals, privacy settings, and login metadata.
- `curstats`, `maxstats`, `experience`, and `capped_experience` store skill state by `playerID`.
- `invitems`, `bank`, `itemstatuses`, and optional `equipped` store item state. Without the equipment-tab addon, wielded items are inferred through inventory item status.
- `player_cache` stores custom Voidscape state such as titles, web-account links, account subscription mirrors, and Void Island path state.
- Login flow in `LoginRequest` and `CharacterCreateRequest` looks up or creates by username directly.

Implication: adding web accounts must not rename or split `players` yet. Desktop can use the existing packet registration path for one-character game logins, while native Android and web-account ownership use the portal.

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

### `web_password_reset_tokens`

- `account_id`
- `token_hash`
- `request_ip_hash`
- `identifier_type`: `email`, `username`
- `status`: `pending`, `used`, `revoked`, `expired`
- `expires_at`, `used_at`, `revoked_at`

Only the hash is durable. The raw token exists briefly inside the sealed email event and is placed in the URL fragment so link scanners do not consume it with an HTTP request.

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

It is intentionally portal-owned reference SQL and is not auto-applied by the OpenRSC server. The schema adds identities, hashed sessions, recovery codes, password-reset tokens, character links/challenges, founder records, entitlements, audit events, and abuse signals. `scripts/test-portal-schema.sh` applies the SQLite variant and verifies the important ownership, status, token-uniqueness, and roster-slot constraints.

## Compatibility Strategy

Phase 1 keeps game login untouched and splits signup by surface:

1. A desktop or native Android player can create a character in-client with only username/password, then log in with that character username and game password.
2. The website authenticates by web account email/password or Google OpenID Connect.
3. Web-created characters still create normal `players` rows and then link them in `web_account_characters`.
4. Public client packet registration is enabled for desktop; native Android and shipped web `/play` direct new users to the portal account manager.
5. Subscription state lives at the web-account level. The game bridge stores `web_account_id` on each character and `acct_sub:<webAccountId>` as the account-wide expiry in global `player_cache`.

Launch decision: `member_world` stays globally enabled on the server for every client surface. Subscription cards are an account-wide XP/card incentive, not a per-player P2P unlock. The server config remains the source of truth for F2P/P2P restrictions, so launcher, Android, and web clients do not need separate membership logic.

The shared game client uses the original packet registration form for desktop `Create Account`; release configs set `want_packet_register:true` and `want_email:false` so that form creates a game character without email. Native Android and web `/play` use the portal account route so Android signup records Community Rules acceptance; client recovery opens `https://voidscape.gg/portal?auth=recovery` (or the web-client recovery sentinel resolved by `/play/`). The server also defaults missing `want_packet_register` to `false`, so a missing config key fails closed instead of silently re-opening packet registration.

Founder-card reward display is source-of-truth checked against the game DB when `PORTAL_OPENRSC_DB` is configured. Each character in the founder cohort frozen at `2026-07-18T18:00:00Z` has a durable global `starter:<webAccountId>:<playerId>` row: `1` means waiting at the Lumbridge vendor and `2` means claimed. The frozen manifest is immutable: admin reconciliation cannot create or expand it, and founder-card revoke is unsupported. Waiting rows, claimed rows, and deletion tombstones remain lifetime audit state. The separate `launch_24h_card` promotion covers every character created before `2026-07-19T18:00:00Z`; the vendor gives at most one card when both routes apply. The old account-wide `starter_card:<webAccountId>` row is migration/display input only.

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
- public launch-signup mode (`PORTAL_PUBLIC_MODE=1 PORTAL_LAUNCH_SIGNUP_MODE=1`) that exposes the safe account path while keeping dev-only, redirect-OAuth, payment, and link-simulation surfaces hidden
- per-character reward state for founder, launch-24h, and merged founder/launch card routes
- public site payloads for status, XP rates, news, downloads, highscores, market intel, and activity feed
- web account registration/login flow with required public password-signup email verification, a 48-hour pending-signup lifetime, and scanner-safe explicit confirmation
- local dev Google sign-in flow and launch-mode Google Identity Services ID-token signup that store a `google` provider identity and return a normal portal bearer session
- `scrypt` password hashing with per-password salts
- bearer sessions stored server-side as token hashes
- account security controls for password rotation, password-confirmed recovery-code generation, and ending other active sessions
- email-or-character-username recovery through `POST /api/accounts/password-reset/request`, with generic responses, a masked hint for matched usernames, hashed one-use tokens, and per-IP/per-account request limits
- website-password completion through `POST /api/accounts/password-reset/complete` and recovery-code fallback through `POST /api/accounts/recover-password`; both revoke every session and recovery credential and require a clean sign-in afterward
- authenticated, stepped-up game-password changes for offline portal-created linked characters, using the canonical OpenRSC bcrypt compatibility format and transactionally rechecking ownership/offline state
- server-side 10-character roster cap
- character-state API responses shaped for the portal
- launch-mode account registration that creates the requested first character as a real linked OpenRSC save after email verification when `PORTAL_OPENRSC_DB` is configured, using the same username and password as the starter game login
- OpenRSC SQLite-backed character creation through `POST /api/characters` when `PORTAL_OPENRSC_DB` is configured; the endpoint creates the normal `players`, `curstats`, `maxstats`, `experience`, and `capped_experience` rows, then stores the created player id in the local portal roster state
- local character link challenges with hashed one-time codes and a dev simulation path
- account-wide subscription-card redemption state
- separate salted-signal velocity controls for completed signup, verification sends, and additional character creation, with targeted exact-IP blocks and proxy overrides
- founder-qualification abuse controls that keep accepted accounts active and default the IP review threshold off, without suppressing the separate launch-24h marker
- token-gated local staff endpoints for account lookup, status changes, subscription grants/clears, immutable founder-freeze reconciliation, and session revocation; founder-card revoke is unsupported
- explicit `501` stubs for redirect-style Google OAuth and subscription-card payment checkout
- optional read-only OpenRSC SQLite saved-character snapshots when `PORTAL_OPENRSC_DB` or `OPENRSC_SQLITE_DB` is configured
- portal account-management schema constraints through `scripts/test-portal-schema.sh`

It does not yet prove:

- production-safe OpenRSC character ownership/linking
- production OpenRSC database writes outside the local SQLite dev bridge
- real in-game `::link` command handling or signed game-server verification callback
- production Google OAuth redirect/callback handling
- production payment checkout/webhook handling
- production staff identity/RBAC beyond the local bearer-token guard

### Registration and character-creation friction

Public password signup keeps verified email mandatory. A pending signup lasts 48 hours by default; the account and first game character do not exist until the player explicitly posts the scanner-safe fragment token. Completion before `2026-07-18T18:00:00Z` also records founder qualification, while every character created before `2026-07-19T18:00:00Z` atomically receives its separate `launch_24h_card` marker. `POST /api/accounts/verify-email/resend` is enumeration-safe: absent, expired, and live pending emails receive the same accepted response unless a generic rate limit fires. Initial and resend sends share independent limits of 3 requests per hour per source IP and 3 per hour per canonical email through `PORTAL_EMAIL_VERIFICATION_IP_LIMIT`, `PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT`, and `PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60`.

Verification-request signals are separate from completed-signup signals. Sending or resending verification mail does not consume completed signup velocity; a password signup is recorded against that velocity only when verification creates the account. Launch defaults allow 3 completed signups per hour and 5 per day from one non-local IP, plus 20 per hour and 50 per day from one IPv4 `/24`.

The first game character created inside an accepted launch signup bypasses repeat-character account, IP, subnet, and proxy velocity gates. It still fails on a targeted blocked IP, a reserved or duplicate portal/game name, a missing game DB bridge, or the transactionally checked 10-character roster cap. Its successful creation is recorded with an initial-signup marker and excluded from later repeat-character rolling counts. Additional character defaults are 5/hour and 10/day per account, 10/hour and 20/day per non-local IP, and 50/hour and 100/day per IPv4 `/24`.

`PORTAL_BLOCKED_IP_CIDRS` remains the targeted blocklist; use `/32` entries for exact IPv4 addresses. `PORTAL_PROXY_IP_CIDRS` remains the targeted high-risk list, with `PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT` and `PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT` applying tighter bounds to both hourly and daily IP checks. These targeted controls do not replace the global launch defaults.

### Starter-card abuse policy

The free founder subscription cards are protected at the reward boundary rather than by adding heavy signup hurdles. Every new account can still be created and can still enter the game through the portal-created character path. Campaign qualification is recorded only when the account passes server-side checks, and the founder freeze turns an approved qualification into at most ten exact account/player issuances:

- one active campaign-qualification entitlement per web account
- one durable composite marker for each eligible character present in the freeze snapshot
- one canonical email per web account, with common alias normalization
- one Google provider subject per linked Google account
- salted abuse-signal hashes for signup IP, email, and identity
- an optional daily limit for founder qualification from the same non-local IP bucket, disabled by default

When operators intentionally set a positive IP bucket limit and it is exceeded, the account remains `active`; only founder-card qualification is marked as review-required. Staff must resolve legitimate reviews before the founder freeze. Routine signup, character creation/linking, and native backfill never add rows to the frozen `starter:<webAccountId>:<playerId>` manifest afterward; post-freeze admin reconciliation cannot expand or revoke it. The separate launch-24h marker follows character creation time and is not suppressed by this founder-review setting.

This keeps the normal launch path low friction while preventing throwaway accounts from reliably minting unlimited free subscription cards.

### Recovery and support policy

The low-friction recovery path is:

1. A signed-out player enters either the account email or a portal-owned character username. The API always returns an accepted response; a matched username receives a masked hint only when a deliverable reset email was actually queued. Synthetic and unknown usernames never display a fake destination.
2. The portal sends a one-use website-password reset link to the account email. Completion rotates the `scrypt` password, marks the email verified by possession, revokes all sessions/reset tokens/recovery codes, and returns to sign-in without creating a synthetic session.
3. A previously generated recovery code is the offline fallback. It is consumed once and performs the same credential/session revocation, but possession of a code alone does not mark email verified.
4. Character game passwords are separate. A signed-in owner confirms the website password (or has a recent passwordless federated session), selects an offline portal-created linked character, and changes only that `players.pass` value.
5. A passwordless account created by native-player backfill uses the separate signed-out claim path. The player proves the current game password, chooses a real email and new website password, then explicitly confirms a hashed one-use email token. Completion rechecks the latest game credential fingerprint and `web_account_id` while holding a short SQLite write lock through the portal-store save, upgrades the same portal account in place, revokes portal recovery/session material, and never changes `players.pass`, its salt, character ownership, active subscription time, or starter-card entitlements. Backfill migrates any legacy `char_sub:<playerId>` expiry to `acct_sub:<accountId>` before the new ownership marker makes account-level lookup authoritative.

Staff support remains the last resort for a player who has lost email/recovery access, does not know the older character's current game password, or needs an email collision reviewed. Claims never merge portal accounts automatically. Production still needs staff identity/RBAC beyond the local admin-token guard.

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

When `PORTAL_OPENRSC_DB` points at a local SQLite game database, `POST /api/characters` creates a real game character instead of only a local preview. Because the current PC client still logs in with `character name + character password`, the request must include a 4-20 letter/number `gamePassword`; the public launch form uses an 8-20 letter/number password because that first password is also the initial game login. The signup's initial character bypasses repeat-character velocity but retains the targeted IP block, name, DB, and max-10 controls described above; later `POST /api/characters` requests use the account/IP/subnet limits. `PortalPasswordHasher` applies OpenRSC's compatibility `SHA-512(salt + MD5(password))` prehash and bcrypt `$2y$10$` format without putting plaintext credentials in process arguments. The bridge inserts the normal player and skill rows, stamps `web_account_id`, snapshots the character, and records the linked `playerId` in portal state.

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
