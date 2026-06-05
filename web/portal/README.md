# Voidscape Portal Prototype

Static click-through prototype for the Voidscape account-management and subscription portal.

Open directly in a browser:

```bash
open web/portal/index.html
```

Or serve it with the local API scaffold:

```bash
scripts/run-portal.sh
```

Then open `http://127.0.0.1:8788/`.

For static-only review without API behavior:

```bash
python3 -m http.server 8788 --directory web/portal
```

## Local API

`dev-server.mjs` is a zero-dependency local prototype API plus static file server. It is not production auth, but it exercises the account-management contract with real server-side state:

- founder username/email reservation
- invite-code referrals and free weekly subscription-card unlock, plus a dev-only referral simulation shortcut
- legacy public status, rates, news, built-artifact download metadata, highscores, market intel, and activity feed payloads for API compatibility; the current UI keeps those surfaces hidden
- local download endpoints for built PC client and launcher jars when `scripts/build.sh` has produced them
- web account registration/login with `scrypt` password hashing
- local dev Google sign-in through `POST /api/accounts/google/dev`, backed by `web_account_identities` in the schema
- bearer sessions stored as token hashes
- account security controls for password rotation, recovery-code generation, and ending other sessions
- account roster reads
- API-enforced 10-character cap
- character creation previews with title/location/gear/appearance state, including default appearance data
- character link challenges for proving ownership of existing OpenRSC saves
- subscription-card redemption previews
- founder reward wallet state and one-click local redemption of the free weekly subscription-card prize
- optional read-only OpenRSC SQLite character snapshots for saved character state

Run the API smoke test:

```bash
scripts/test-portal-api.sh
```

Validate the portal account-management schema contract:

```bash
scripts/test-portal-schema.sh
```

The data store defaults to `/tmp/voidscape-portal-api/dev-store.json`. Set `PORTAL_DATA_DIR=/path/to/data` if you want a persistent local store.

To let the Characters screen load real saved character state from a local OpenRSC SQLite database, start the server with:

```bash
PORTAL_OPENRSC_DB=/Users/s/Desktop/voidscape/server/inc/sqlite/voidscape.db scripts/run-portal.sh
```

The snapshot endpoint is read-only. It reads safe character fields from `players`, `player_cache`, `invitems`, `itemstatuses`, optional `equipped`, and item/title definitions; it does not create accounts, mutate game data, or prove web ownership.

When the local API is running with `PORTAL_OPENRSC_DB`, the Characters screen can start a link challenge for an existing saved character. The API returns a one-time `::link <code>` command and stores only the code hash. The prototype's `Simulate verified` button exercises the verification path locally and replaces the starter preview with the saved character state. Production still needs an in-game command or signed game-server callback before real ownership is trusted.

The production schema draft lives in `web/portal/schema/` with SQLite and MySQL/MariaDB variants. It defines web accounts, hashed sessions, hashed recovery codes, character links, link challenges, founder reservations/referrals, entitlements, audit events, and abuse-signal buckets. These files are not auto-applied by the OpenRSC server.

## Scope

- Simple Account tab for creating or signing into a local portal account with email/password or dev Google sign-in
- Character roster and create-character flow mockup with a 10-character web-account cap
- Character roster cards with linked/preview state badges, gear-token loadouts, and a 10-slot roster rail
- Subscription status and redeem-card mockup
- API-backed Security tab for password rotation, one-time recovery-code generation, and session review
- Local prototype persistence through `localStorage`, with API-backed state when served through `scripts/run-portal.sh`
- Character state readout for title, level, location, appearance summary, and equipped gear
- Paper-doll character preview that reflects starter-path appearance defaults and saved OpenRSC appearance/equipment state when available
- Optional saved-character snapshot loading when `PORTAL_OPENRSC_DB` points to a local SQLite database
- Dev-safe saved-character link challenges that merge a verified OpenRSC save into the web-account roster
- Portal account schema contract for web accounts, up to 10 linked game characters, founder rewards, and audit/abuse tracking
- Retired public/content views remain hidden while account management is the active portal scope

## Not wired yet

- Production Google OAuth redirect/callback handling and ID-token verification
- Game-database-backed account creation
- Game-database-backed character creation
- Real in-game `::link` command or signed game-server verification callback
- Real founder-pass email verification, Turnstile, or production referral risk scoring
- Production password reset, recovery-code consumption, or email recovery flow
- Production-safe OpenRSC character ownership/linking
- Admin permission checks

The generated concept and asset references used for this prototype live locally in `.codex-artifacts/` and are excluded from git status through `.git/info/exclude`. The latest ChatGPT concept batch is in `.codex-artifacts/chatgpt-concepts/`; it is a design reference set, not shipped site art.

The production account/character ownership plan is documented in `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`.
