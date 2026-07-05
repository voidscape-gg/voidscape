# Voidscape Portal Prototype

Static click-through prototype for the Voidscape pre-release landing, account-management, and subscription portal.

## Prelaunch signup flow (current landing)

The launch landing has two public modes:

- `PORTAL_PUBLIC_MODE=1 PORTAL_LAUNCH_SIGNUP_MODE=1` is the ad-facing launch flow. A visitor enters username, email, and an 8-20 letter/number password; the portal creates the web account immediately, creates the first real OpenRSC character with the same username and password, links it to slot 1 of 10, reserves one starter Subscription card for the Lumbridge vendor, and queues a confirmation email when email delivery is configured. In launch-signup mode, `PORTAL_OPENRSC_DB` must point at the game SQLite DB so signup cannot silently fall back to a preview-only roster card. The account dashboard reads the `starter_card:<webAccountId>` game marker so a card shows as waiting until the vendor marks it claimed. If `PORTAL_GOOGLE_CLIENT_ID` is configured, the same form also shows Google sign-in: Google owns the web login, while the submitted password becomes the first character login until the clients support web-account SSO.
- `PORTAL_PUBLIC_MODE=1` without launch signup keeps the older code-only reservation flow. A visitor enters an email + desired username, the username is reserved, and a one-time `VOID-XXXX-XXXX` signup code is shown on-screen. The code is written into the game DB as a global `player_cache` row (`signup_code:<CODE>` = 1) when `PORTAL_OPENRSC_DB` is set. In-game, the player talks to the Void Subscription Vendor in Lumbridge, types the code into the popup he opens (custom client 10087+), and receives one tradable Subscription card.

One reservation/code is minted per canonical email in code-only mode; re-signing up with the same email shows the same code. Username reservation checks the founder ledger, existing portal roster cards, and the configured OpenRSC `players` table before showing a name as reserved; an existing OpenRSC player may refresh the reservation only with the same canonical email. If the signup credits another founder's invite code during beta, the referrer also receives one additional referral reward code per credited referral. Referral reward codes use the same `signup_code:<CODE>` ledger and vendor redemption path. Codes are bearer tokens; anyone who knows the email can re-view the signup code, which is accepted for prelaunch stakes. Codes are minted only on the code-only public route; registered portal accounts keep using the account-bound `starter_card:<id>` marker instead.

Signup-list export (token-gated):

```bash
curl -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" http://127.0.0.1:8788/api/admin/signups
curl -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" "http://127.0.0.1:8788/api/admin/signups?format=csv" > signups.csv
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" http://127.0.0.1:8788/api/admin/signups/sync  # re-push unsynced codes
```

Email queue/admin actions (token-gated):

```bash
curl -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" http://127.0.0.1:8788/api/admin/emails
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"dryRun":true}' http://127.0.0.1:8788/api/admin/emails/signup-confirmations
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"dryRun":true}' http://127.0.0.1:8788/api/admin/emails/launch-live
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"retryFailed":true}' http://127.0.0.1:8788/api/admin/emails/send-pending
```

## Launching the public prelaunch site

Set `PORTAL_PUBLIC_MODE=1` for any internet-facing prelaunch deployment. With only that flag, the server is code-only and locks down to exactly: static files, `GET /api/health`, `GET /api/public` (sanitized — no fake world stats, but with launcher/APK availability, global launch-rule metadata, and optional launch countdown metadata), `GET /api/integrity` (public-safe transparency aggregates), public launcher/APK downloads, `GET /api/launcher/manifest.properties`, `POST /api/founder/reservations`, and the token-gated `/api/admin/*`. Everything else — registration, login, dev Google sign-in, character creation, link simulation, subscription redeem, snapshots, and avatars — returns 404. The UI pins itself to the landing view (deep links like `#admin` or `#dashboard` land on the signup page).

For launch ads, add `PORTAL_LAUNCH_SIGNUP_MODE=1`. This keeps dev/payment/link-simulation/admin surfaces locked down, but opens the safe account path needed by real players: `POST /api/accounts/register`, optional Google ID-token signup through `POST /api/oauth/google/nonce` and `POST /api/accounts/google`, `POST /api/accounts/login`, `GET /api/account`, `POST /api/characters`, recovery-code password reset, and authenticated security actions. It also exposes `launchSignupMode: true` plus public OAuth metadata through `/api/public`, enables the Account/Characters/Subscription/Security views in public mode, and turns the landing form into "create account + first character + reserve starter card" copy. The launch form password is deliberately limited to letters and numbers because it is also the first character's game login until the clients support web-account SSO.

Returning players use the visible `Sign in` action in the landing nav, hero, or reserve card. The card switches to email/password login, then routes the player to the Account dashboard; an already-valid session keeps the landing page available but changes those auth CTAs to `Manage Account` and shows the claimed account state.

The launch countdown defaults to **Saturday, July 11, 2026 at 11:00 AM Pacific / 2:00 PM Eastern / 7:00 PM UK** (`2026-07-11T18:00:00Z`). Set `PORTAL_LAUNCH_AT=2026-07-11T18:00:00Z` explicitly in production so the date is obvious in deployment config. `PORTAL_BETA_OPEN_AT` remains accepted as a backward-compatible fallback, but launch deployments should use `PORTAL_LAUNCH_AT`.

Set `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/` if the mobile browser client moves. Prelaunch landing copy mentions Web/Desktop/iOS/Android support, but the public page keeps the clickable funnel on reserving the account name until the world opens. Once `PORTAL_LAUNCH_AT` is in the past, the same landing switches into the launch-open surface: desktop launcher download, mobile web client, Android APK availability, live build proof, and account-management CTAs render from `/api/public` download metadata.

The public site also serves `/privacy` and `/data-deletion`. Keep those reachable before enabling Google sign-in or Android distribution, and update the support mailbox if production support uses a different address.

Recommended shape: the portal bound to loopback on the host, with Caddy (or nginx/Cloudflare) in front doing TLS. A same-host proxy is auto-trusted for `x-forwarded-for`, so the per-IP signup limit sees real visitor IPs with no extra config.

```bash
# /etc/systemd/system/voidscape-portal.service (adjust paths/user)
[Unit]
Description=Voidscape prelaunch signup portal
After=network.target

[Service]
User=voidscape
WorkingDirectory=/opt/voidscape
Environment=PORT=8788
Environment=PORTAL_PUBLIC_MODE=1
Environment=PORTAL_LAUNCH_SIGNUP_MODE=1
Environment=PORTAL_LAUNCH_AT=2026-07-11T18:00:00Z
Environment=PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/
Environment=PORTAL_DATA_DIR=/var/lib/voidscape-portal
Environment=PORTAL_OPENRSC_DB=/opt/voidscape/server/inc/sqlite/voidscape.db
Environment=PORTAL_INTEGRITY_SNAPSHOT=/var/lib/voidscape-portal/integrity-summary.json
Environment=PORTAL_ADMIN_TOKEN=<long random secret>
Environment=PORTAL_ABUSE_HASH_SALT=<long random secret, set once, never change>
Environment=PORTAL_SIGNUP_IP_DAILY_LIMIT=10
Environment=PORTAL_PUBLIC_ORIGIN=https://voidscape.gg
Environment=PORTAL_EMAIL_PROVIDER=resend
Environment="PORTAL_EMAIL_FROM=Voidscape <launch@voidscape.gg>"
Environment=PORTAL_EMAIL_REPLY_TO=support@voidscape.gg
Environment=PORTAL_RESEND_API_KEY=<resend api key>
ExecStart=/usr/bin/node web/portal/dev-server.mjs
Restart=always

[Install]
WantedBy=multi-user.target
```

```
# Caddyfile — automatic HTTPS once DNS points at the box
yourdomain.com {
	reverse_proxy 127.0.0.1:8788
}
```

Notes:
- The deployment needs `web/portal/` from this repo plus Node 18+. Launch-signup mode needs a real game DB bridge; if `PORTAL_OPENRSC_DB` is not available, keep `PORTAL_LAUNCH_SIGNUP_MODE` off and use the older code-only reservation flow until the portal and game persistence boundary are on the same host/service.
- Transparency data is public-safe by construction. `GET /api/integrity` reads `$PORTAL_INTEGRITY_SNAPSHOT` when present, otherwise it can aggregate local SQLite `staff_logs` and `item_provenance_events` from `PORTAL_OPENRSC_DB` in dev, otherwise it reports `waiting_for_game_snapshot`.
- Export the public-safe integrity snapshot and private economy findings from the game machine, then copy/sync the public snapshot to the portal host:
  `PORTAL_OPENRSC_DB=/path/to/voidscape.db PORTAL_INTEGRITY_SNAPSHOT=/var/lib/voidscape-portal/integrity-summary.json PORTAL_INTEGRITY_FINDINGS=/var/lib/voidscape-portal/integrity-findings.json node scripts/export-integrity-summary.mjs`
- `integrity-summary.json` is safe for the website. `integrity-findings.json` is staff-only and powers `::integrity` on the game server.
- On the single-host beta VPS, install `Deployment_Scripts/systemd/voidscape-integrity-export.{service,timer}` into `/etc/systemd/system/` to refresh those files every five minutes:

```bash
sudo cp Deployment_Scripts/systemd/voidscape-integrity-export.service /etc/systemd/system/
sudo cp Deployment_Scripts/systemd/voidscape-integrity-export.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now voidscape-integrity-export.timer
sudo systemctl start voidscape-integrity-export.service
systemctl list-timers 'voidscape-integrity-export.timer'
journalctl -u voidscape-integrity-export.service -n 20 --no-pager
```

- Back up the signup list: it is one file, `$PORTAL_DATA_DIR/dev-store.json` (atomic writes). A cron copy or the CSV export both work as backups.
- Pull the list any time: `curl -H "x-portal-admin-token: $TOKEN" "https://yourdomain.com/api/admin/signups?format=csv"`.
- Google sign-in is optional. Set `PORTAL_GOOGLE_CLIENT_ID` to a Google Identity Services web client id to show the button; leave it unset to keep the email/password path only. The redirect-style `/api/oauth/google/start` and `/api/oauth/google/callback` endpoints are still placeholders.
- Email delivery supports Resend. New launch-signup accounts queue `signup_confirmation` emails, and staff can backfill confirmations, send/retry pending events, or send `launch_live` emails through the token-gated admin API. Use `PORTAL_EMAIL_DRY_RUN=1` for staging/smoke tests. Turnstile and checkout remain plug-in-later provider slots; the portal does not require Turnstile tokens or sell subscription-card checkout until those handlers are implemented and tested.
- Code-only launch day, to make codes redeemable in-game: copy `dev-store.json` from the public host into a `PORTAL_DATA_DIR` on the game machine, run the portal there with `PORTAL_OPENRSC_DB=/path/to/voidscape.db` (loopback is fine, no public mode needed), and `curl -X POST -H "x-portal-admin-token: $TOKEN" http://127.0.0.1:8788/api/admin/signups/sync`. Already-redeemed codes are skipped; the endpoint is idempotent.
- If bot signups become a problem, put Cloudflare in front with a managed challenge on `POST /api/founder/reservations` — the app-side per-IP cap is the backstop, not the whole defense.

After deploying a launch-signup staging build, verify it with the hosted release gate:

```bash
scripts/verify-launch-staging.mjs \
  --portal-url https://yourdomain.com/ \
  --web-url https://yourdomain.com/play/ \
  --ws wss://yourdomain.com/play/ws/ \
  --server-config <copy-of-deployed-server.conf> \
  --run-signup
```

The verifier reads `/api/health` and `/api/public`, so those endpoints expose only public-safe booleans for durable storage, OpenRSC DB bridge status, and launch config readiness, not filesystem paths or credentials. In public mode, `/api/health.config.publicReady` requires a non-placeholder `PORTAL_ABUSE_HASH_SALT`; `adminTokenConfigured` reports only whether the admin token looks configured, never its value.

Binding non-loopback without `PORTAL_DATA_DIR` refuses to start (the signup list would live in $TMPDIR); `PORTAL_ALLOW_TMPDIR=1` overrides. `x-forwarded-for` is only trusted from loopback peers (same-host reverse proxy) or with `PORTAL_TRUST_PROXY=1` — only set that when the origin is reachable exclusively through the proxy. `PORTAL_SIGNUP_IP_DAILY_LIMIT` (default 10) caps signups per non-local IP per day (429 past it).

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

- reserved username/email flow, including the Google-first pre-release username flow
- normal portal register/sign-in UI in non-public mode, backed by `POST /api/accounts/register` and `POST /api/accounts/login`
- account-first public launch signup mode that creates a web account and first real OpenRSC character in one step when `PORTAL_OPENRSC_DB` is configured
- invite-code referrals that mint one referral reward subscription-card code per credited beta invite, plus a dev-only referral simulation shortcut
- legacy public status, rates, news, highscores, market intel, and activity feed payloads for API compatibility; the current prelaunch landing uses platform-support proof copy instead of public play/download CTAs
- local download endpoints for built PC client and launcher jars when `scripts/build.sh` has produced them
- launcher update manifest (contract v2) at `GET /api/launcher/manifest.properties`, including SHA-256 and byte-size entries for `Open_RSC_Client.jar` and non-runtime `Client_Base/Cache` files served through `/downloads/client-runtime` and `/downloads/cache/...`; also emits `clientVersion` (from `PORTAL_CLIENT_VERSION` or parsed from `Client_Base/src/orsc/Config.java`, prefixed onto `version` when known) and `launcher.sha256`/`launcher.url`/`launcher.size`/`launcher.version` self-update keys when the launcher jar is built — the launcher jar never appears among `file.N.*` entries
- web account registration/login with `scrypt` password hashing
- local dev Google sign-in through `POST /api/accounts/google/dev`, plus launch-mode Google Identity Services ID-token signup through `POST /api/accounts/google` when `PORTAL_GOOGLE_CLIENT_ID` is configured; both are backed by `web_account_identities` in the schema
- bearer sessions stored as token hashes
- account security controls for password rotation, recovery-code generation, recovery-code password reset, and ending other sessions
- privacy-preserving abuse signals for starter-card grants; account creation stays active, but the free starter card is held for review after repeated grants from the same non-local IP bucket
- username-reservation hardening across founder reservations, portal roster cards, and existing OpenRSC player rows, so public code-only signups cannot squat names that are already created
- token-gated local admin endpoints for account lookup, status review/lock, subscription grants/clears, starter-card grants/revokes, and session revocation
- explicit redirect-style Google OAuth and payment checkout stubs that return `501` until those provider flows are configured
- account roster reads
- API-enforced 10-character cap
- character creation previews with title/location/gear/appearance state when no game DB is configured
- OpenRSC SQLite-backed character creation when `PORTAL_OPENRSC_DB` is configured, including `players`, `curstats`, `maxstats`, `experience`, `capped_experience`, and `web_account_id` cache rows
- launch signup that asks for an 8-20 character password, creates the web account and first linked OpenRSC save immediately, then shows the account as 1 of 10 character slots used
- character link challenges for proving ownership of existing OpenRSC saves
- subscription-card redemption previews
- starter reward wallet state for the free weekly subscription-card prize
- optional read-only OpenRSC SQLite character snapshots for saved character state

Run the API smoke test:

```bash
scripts/test-portal-api.sh
```

Validate the portal account-management schema contract:

```bash
scripts/test-portal-schema.sh
```

Capture the prelaunch/account visual gate against a running launch-signup portal:

```bash
scripts/smoke-portal-prelaunch-visual.sh --portal-url http://127.0.0.1:8788/
scripts/smoke-portal-prelaunch-visual.sh --portal-url http://127.0.0.1:8788/ --skip-signup --expect-launch-open
```

The first command screenshots desktop/mobile landing, creates one real launch-signup account when `PORTAL_OPENRSC_DB` is configured, and verifies dashboard, roster, subscription, security, and mobile account navigation. The second command is for a portal started with `PORTAL_LAUNCH_AT` in the past; it verifies the countdown takeover, launch proof panel, and client chooser copy.

The data store defaults to `/tmp/voidscape-portal-api/dev-store.json`. Set `PORTAL_DATA_DIR=/path/to/data` if you want a persistent local store.

Useful local API environment variables:

- `PORTAL_OPENRSC_DB=/path/to/voidscape.db` enables the SQLite game bridge.
- `PORTAL_ADMIN_TOKEN=...` enables `/api/admin/*`; without it the admin API returns `admin_not_configured`.
- `PORTAL_SIGNUP_IP_DAILY_LIMIT=10` caps prelaunch landing signups per non-local IP per day (429 `rate_limited` past it).
- `PORTAL_STARTER_IP_DAILY_LIMIT=10` controls how many starter-card grants a non-local IP bucket can receive per day before new accounts are left active but their free card is marked for review. Default matches `PORTAL_SIGNUP_IP_DAILY_LIMIT` so every accepted prelaunch signup gets the promised starter card unless ops intentionally sets a lower review threshold.
- `PORTAL_PUBLIC_MODE=1` locks the server to the prelaunch signup surface (see "Launching the public prelaunch site").
- `PORTAL_LAUNCH_SIGNUP_MODE=1` opens the public account-first launch flow while keeping dev-only, redirect-OAuth, payment, and link-simulation surfaces hidden; use only with `PORTAL_PUBLIC_MODE=1`.
- `PORTAL_GOOGLE_CLIENT_ID=...` enables the public Google Identity Services button and ID-token verification for launch signup. Without it, Google signup endpoints return `google_oauth_not_configured` and the button stays hidden. `PORTAL_GOOGLE_JWKS_URL` defaults to Google's public cert endpoint and exists only as a test/ops override.
- `PORTAL_PUBLIC_ORIGIN=https://voidscape.gg` sets the public origin used in outgoing emails. If unset, the portal derives it from request headers.
- `PORTAL_EMAIL_PROVIDER=resend` enables live email delivery through Resend when `PORTAL_RESEND_API_KEY` and `PORTAL_EMAIL_FROM` are configured. `PORTAL_EMAIL_REPLY_TO` defaults to `support@voidscape.gg`; `PORTAL_EMAIL_DRY_RUN=1` marks events sent without calling Resend; `PORTAL_REQUIRE_EMAIL=1` makes `/api/health.config.publicReady` fail until email delivery is configured.
- `PORTAL_LAUNCH_AT=...` exposes a public-safe launch countdown through `/api/public`; use ISO-8601 with timezone.
- `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/` controls the release mobile web-client URL used by API metadata and post-launch surfaces; the prelaunch landing does not link play/download actions.
- `PORTAL_ANDROID_APK=/path/to/voidscape.apk` overrides the APK served by `/downloads/android-apk`. If unset, the portal uses the standard Android build output when it exists. The prelaunch landing still hides download buttons until the countdown opens the post-launch chooser.
- `PORTAL_BIND_HOST=127.0.0.1` sets the listen address; non-loopback binds require `PORTAL_DATA_DIR` (or `PORTAL_ALLOW_TMPDIR=1`).
- `PORTAL_TRUST_PROXY=1` trusts `x-forwarded-for` from non-loopback peers (only set behind a real proxy).
- `PORTAL_ABUSE_HASH_SALT=...` salts stored IP/email/identity hashes. Set a stable private value before using persistent data; public launch verification fails if this is missing, still the dev fallback, shorter than 16 characters, or a `CHANGE_ME` placeholder.

To let the Characters screen load real saved character state from a local OpenRSC SQLite database, start the server with:

```bash
PORTAL_OPENRSC_DB=/Users/s/Desktop/voidscape/server/inc/sqlite/voidscape.db scripts/run-portal.sh
```

The snapshot endpoint is read-only. It reads safe character fields from `players`, `player_cache`, `invitems`, `itemstatuses`, optional `equipped`, and item/title definitions; it does not create accounts, mutate game data, or prove web ownership.

When the local API is running with `PORTAL_OPENRSC_DB`, the Characters screen can start a link challenge for an existing saved character. The API returns a one-time `::link <code>` command and stores only the code hash. The prototype's `Simulate verified` button exercises the verification path locally and replaces the starter preview with the saved character state. Production still needs an in-game command or signed game-server callback before real ownership is trusted.

The production schema draft lives in `web/portal/schema/` with SQLite and MySQL/MariaDB variants. It defines web accounts with account-wide subscription expiry, hashed sessions, hashed recovery codes, character links, link challenges, founder reservations/referrals, referral reward codes, entitlements, audit events, and abuse-signal buckets. These files are not auto-applied by the OpenRSC server.

## Scope

- Pre-release account landing that recreates the generated launch mockup with layered assets, a muted looping MP4 background, poster fallback, real form fields, account-first username reservation, game-password onboarding, and an after-claim web/launcher/Android handoff
- Visible returning-player account path: landing nav/hero/form `Sign in` controls switch the founder card into login mode, successful login opens Account Management, and loaded sessions show `Manage Account` CTAs
- Launch countdown and reservation-first prelaunch copy, driven by `PORTAL_LAUNCH_AT` with the July 11 launch timestamp as the default
- Launch-open landing takeover after the countdown expires, driven by `/api/public` download availability and account-management state rather than hard-coded static links
- Streamlined first reservation screen: countdown first, signup card directly underneath, then platform proof; redundant live-status and early-reservation benefit strips are intentionally removed
- Platform-support proof copy for Web, Desktop, iOS, and Android while keeping the prelaunch CTA focused on reserving a name and starter card
- Simple Account dashboard for signed-in players, with visible register/sign-in controls, account identity, subscription state, character count, reserved-card state, and security state
- Extremely simple character roster and create-character flow with a 10-character web-account cap
- Character roster cards that only show the saved character render, account name, level, and subscription state
- Subscription status panel focused only on the current account state and whether a starter subscription card is waiting at the Lumbridge vendor
- API-backed Security tab for one-time recovery-code generation and session review, with password controls shown only for password-enabled accounts
- Recovery-code password reset through `POST /api/accounts/recover-password`; one code is consumed, existing sessions are revoked, and a fresh session is issued
- Local prototype persistence through `localStorage`, with API-backed state when served through `scripts/run-portal.sh`
- Web character creation only asks for username and game password; starter path, spawn choice, appearance, and onboarding remain in-game
- Character renders use the OpenRSC avatar PNG generated by the game server on logout when available, with an RSC sprite fallback before the first in-game save/logout
- Optional saved-character snapshot loading when `PORTAL_OPENRSC_DB` points to a local SQLite database
- Account reads refresh linked/created character snapshots from `PORTAL_OPENRSC_DB`, so logout-saved appearance, equipment, levels, and avatar image update the dashboard
- Optional starter-card bridge into `PORTAL_OPENRSC_DB` through account-level OpenRSC `player_cache` markers for the Lumbridge Subscription Vendor
- Dev-safe saved-character link challenges that merge a verified OpenRSC save into the web-account roster
- Local staff API for account review, support grants, starter-card correction, and session revocation
- Staff tab for local token-gated account lookup, status/subscription/starter-card/session actions, roster review, audit events, and abuse-signal context
- Portal account schema contract for web accounts, up to 10 linked game characters, starter-card rewards, account-wide subscription expiry, and audit/abuse tracking
- Retired public/content views remain hidden while the pre-release account landing and account-management surfaces are the active portal scope

## Not wired yet

- Production Google OAuth redirect/callback handling
- Production payment provider checkout/webhook handling
- Production-hosted game-database account creation outside the local `PORTAL_OPENRSC_DB` bridge
- Real in-game `::link` command or signed game-server verification callback
- Real founder-pass email verification, Turnstile, email delivery, or production referral risk scoring
- Production-safe OpenRSC character ownership/linking
- Production admin identity/RBAC beyond the local bearer token

The generated concept references used for earlier prototype passes live locally in `.codex-artifacts/` and are excluded from git status through `.git/info/exclude`. The shipped pre-release landing assets live in `web/portal/assets/prelaunch/`; foreground layers are chroma-keyed PNGs and the animated background is an audio-free H.264 MP4 with a poster image.

The production account/character ownership plan is documented in `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`.
