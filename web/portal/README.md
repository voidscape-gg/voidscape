# Voidscape Portal Prototype

Static click-through prototype for the Voidscape pre-release landing, account-management, and subscription portal.

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

- founder username/email reservation, including the Google-first pre-release username flow
- invite-code referrals and free weekly subscription-card unlock, plus a dev-only referral simulation shortcut
- legacy public status, rates, news, built-artifact download metadata, highscores, market intel, and activity feed payloads for API compatibility; the current UI keeps those surfaces hidden
- local download endpoints for built PC client and launcher jars when `scripts/build.sh` has produced them
- web account registration/login with `scrypt` password hashing
- local dev Google sign-in through `POST /api/accounts/google/dev`, backed by `web_account_identities` in the schema
- bearer sessions stored as token hashes
- account security controls for password rotation, recovery-code generation, and ending other sessions
- account roster reads
- API-enforced 10-character cap
- character creation previews with title/location/gear/appearance state when no game DB is configured
- OpenRSC SQLite-backed character creation when `PORTAL_OPENRSC_DB` is configured, including `players`, `curstats`, `maxstats`, `experience`, and `capped_experience` rows
- Google reservation handoff that asks for a 4-20 character game password, converts the reserved roster card into a real OpenRSC save, then reveals the launcher download
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

- Pre-release account landing that recreates the generated launch mockup with layered assets, a muted looping MP4 background, poster fallback, real form fields, Google username reservation, game-password onboarding, and an after-claim launcher handoff
- Simple Account dashboard for signed-in players, with account identity, subscription state, character count, reserved-card state, and security state
- Extremely simple character roster and create-character flow with a 10-character web-account cap
- Character roster cards that only show the saved character render, account name, level, and subscription state
- Subscription status panel focused only on the current account state and whether a founder card is waiting at the Lumbridge vendor
- API-backed Security tab for one-time recovery-code generation and session review, with password controls shown only for password-enabled accounts
- Local prototype persistence through `localStorage`, with API-backed state when served through `scripts/run-portal.sh`
- Web character creation only asks for username and game password; starter path, spawn choice, appearance, and onboarding remain in-game
- Character renders use the OpenRSC avatar PNG generated by the game server on logout when available, with an RSC sprite fallback before the first in-game save/logout
- Optional saved-character snapshot loading when `PORTAL_OPENRSC_DB` points to a local SQLite database
- Account reads refresh linked/created character snapshots from `PORTAL_OPENRSC_DB`, so logout-saved appearance, equipment, levels, and avatar image update the dashboard
- Optional founder-card bridge into `PORTAL_OPENRSC_DB` through global OpenRSC `player_cache` markers for the Lumbridge Subscription Vendor
- Dev-safe saved-character link challenges that merge a verified OpenRSC save into the web-account roster
- Portal account schema contract for web accounts, up to 10 linked game characters, founder rewards, and audit/abuse tracking
- Retired public/content views remain hidden while the pre-release account landing and account-management surfaces are the active portal scope

## Not wired yet

- Production Google OAuth redirect/callback handling and ID-token verification
- Production-hosted game-database account creation outside the local `PORTAL_OPENRSC_DB` bridge
- Real in-game `::link` command or signed game-server verification callback
- Real founder-pass email verification, Turnstile, or production referral risk scoring
- Production password reset, recovery-code consumption, or email recovery flow
- Production-safe OpenRSC character ownership/linking
- Admin permission checks

The generated concept references used for earlier prototype passes live locally in `.codex-artifacts/` and are excluded from git status through `.git/info/exclude`. The shipped pre-release landing assets live in `web/portal/assets/prelaunch/`; foreground layers are chroma-keyed PNGs and the animated background is an audio-free H.264 MP4 with a poster image.

The production account/character ownership plan is documented in `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`.
