# Voidscape Portal Prototype

Static click-through prototype for the Voidscape pre-release landing, account-management, and subscription portal.

## Prelaunch signup flow (current landing)

The launch landing has two public modes:

- `PORTAL_PUBLIC_MODE=1 PORTAL_LAUNCH_SIGNUP_MODE=1` is the ad-facing launch flow. Public password signup keeps `PORTAL_EMAIL_VERIFICATION_REQUIRED=1`: a visitor enters username, email, and an 8-20 letter/number password, then the portal stores a pending signup for 48 hours and sends a verification link. Before launch, explicit verification creates the web account, first real game character, slot-1 roster link, and founder-card campaign qualification. The exact founder cohort freezes at `2026-07-18T18:00:00Z`. Every character created before the separate launch-promotion cutoff at `2026-07-19T18:00:00Z` still qualifies for one `launch_24h_card`, with vendor deduplication if a founder route also applies. `PORTAL_OPENRSC_DB` must point at the game SQLite DB so signup cannot silently fall back to a preview-only roster card. The initial character bypasses repeat-character velocity limits but still passes the exact-IP blocklist, name availability, configured-DB, and max-10 checks. If `PORTAL_GOOGLE_CLIENT_ID` is configured, the same form also shows Google sign-in: Google owns the web login, while the submitted password becomes the first character login until the clients support web-account SSO.
- `PORTAL_PUBLIC_MODE=1` without launch signup keeps the older code-only founder reservation flow until the founder cutoff. A visitor enters an email + desired username, the username is reserved, and a one-time `VOID-XXXX-XXXX` signup code is shown on-screen. The code is written into the game DB as a global `player_cache` row (`signup_code:<CODE>` = 1) when `PORTAL_OPENRSC_DB` is set. Public founder reservations close at `2026-07-18T18:00:00Z`. In-game, the player talks to the Void Subscription Vendor in Lumbridge, types the code into the popup he opens (custom client 10087+), and receives one tradable Subscription card.

One reservation/code is minted per canonical email in code-only mode; re-signing up with the same email shows the same code. Username reservation checks the founder ledger, existing portal roster cards, and the configured game `players` table before showing a name as reserved; an existing game character may refresh the reservation only with the same canonical email. If the signup credits another founder's invite code during beta, the referrer also receives one additional referral reward code per credited referral. Referral reward codes use the same `signup_code:<CODE>` ledger and vendor redemption path. Codes are bearer tokens; anyone who knows the email can re-view the signup code, which is accepted for prelaunch stakes. Codes are minted only on the code-only public route; registered accounts qualify for exact per-character founder markers at the founder freeze, while characters created before the launch-24h cutoff receive their own `launch_24h_card` marker.

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
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"dryRun":true}' http://127.0.0.1:8788/api/admin/emails/launch-48h
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"dryRun":true}' http://127.0.0.1:8788/api/admin/emails/launch-live
curl -X POST -H "x-portal-admin-token: $PORTAL_ADMIN_TOKEN" -H 'content-type: application/json' -d '{"retryFailed":true}' http://127.0.0.1:8788/api/admin/emails/send-pending
```

## Launching the public prelaunch site

Set `PORTAL_PUBLIC_MODE=1` for any internet-facing prelaunch deployment. With only that flag, the server is code-only and locks down to exactly: static files, `GET /api/health`, `GET /api/public` (sanitized — no fake world stats, but with launcher/APK availability, global launch-rule metadata, and optional launch countdown metadata), `GET /api/integrity` (public-safe transparency aggregates), public launcher/APK downloads, `GET /api/launcher/manifest.properties`, `POST /api/founder/reservations`, `POST /api/presence/heartbeat`, and the token-gated `/api/admin/*`. Everything else — registration, login, dev Google sign-in, character creation, link simulation, subscription redeem, snapshots, and avatars — returns 404. The UI pins itself to the landing view (deep links like `#admin` or `#dashboard` land on the signup page).

For launch ads, add `PORTAL_LAUNCH_SIGNUP_MODE=1`. This keeps dev/payment/link-simulation/admin surfaces locked down, but opens the safe account path needed by real players: registration and email verification, optional Google ID-token signup, login/logout, account reads, character creation/deletion, emailed and recovery-code website-password reset, authenticated game-password reset, and the remaining security actions. It also exposes `launchSignupMode: true` plus public OAuth metadata through `/api/public`, enables the authenticated Account/Characters/Subscription/Security views, and turns the landing form into "create account + first character + reserve starter card" copy. The launch form password is deliberately limited to letters and numbers because it is also the first character's game login until the clients support web-account SSO.

Returning players use the landing-nav `Sign in` action, which opens the dedicated `/portal?auth=login` account-access screen. Signed-out protected deep links resolve to that screen without rendering private account data. A valid login opens `/portal#dashboard`; a valid landing-page session changes the action to `Manage Account` and replaces signup with launch downloads. Logout revokes the server session and returns to `/`.

The launch countdown and founder freeze are **Saturday, July 18, 2026 at 11:00 AM Pacific / 2:00 PM Eastern / 7:00 PM UK** (`2026-07-18T18:00:00Z`). Set `PORTAL_LAUNCH_AT=2026-07-18T18:00:00Z` and `PORTAL_LAUNCH_FREE_CARD_HOURS=24` explicitly in production. Founder reservations stop at launch; the separate launch-card promotion remains open until, but not including, `2026-07-19T18:00:00Z`. `PORTAL_BETA_OPEN_AT` remains accepted as a backward-compatible fallback, but launch deployments should use `PORTAL_LAUNCH_AT`.

Set `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1` if the mobile browser client moves. Prelaunch landing copy mentions Web/Desktop/iOS/Android support, but the public page keeps the clickable funnel on reserving the account name until the world opens. Once `PORTAL_LAUNCH_AT` is in the past, the same landing switches into the launch-open surface: desktop launcher download, mobile web client, Android APK availability, live build proof, and account-management CTAs render from `/api/public` download metadata.

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
Environment=PORTAL_LAUNCH_AT=2026-07-18T18:00:00Z
Environment=PORTAL_LAUNCH_FREE_CARD_HOURS=24
Environment=PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1
Environment=PORTAL_DATA_DIR=/var/lib/voidscape-portal
Environment=PORTAL_OPENRSC_DB=/opt/voidscape/server/inc/sqlite/voidscape.db
Environment=PORTAL_GAME_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/core.jar
Environment=PORTAL_INTEGRITY_SNAPSHOT=/var/lib/voidscape-portal/integrity-summary.json
Environment=PORTAL_ADMIN_TOKEN=<long random secret>
Environment=PORTAL_ABUSE_HASH_SALT=<long random secret, set once, never change>
Environment=PORTAL_SIGNUP_IP_HOURLY_LIMIT=3
Environment=PORTAL_SIGNUP_IP_DAILY_LIMIT=5
Environment=PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT=20
Environment=PORTAL_SIGNUP_SUBNET_DAILY_LIMIT=50
Environment=PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=5
Environment=PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT=10
Environment=PORTAL_CHARACTER_IP_HOURLY_LIMIT=10
Environment=PORTAL_CHARACTER_IP_DAILY_LIMIT=20
Environment=PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT=50
Environment=PORTAL_CHARACTER_SUBNET_DAILY_LIMIT=100
Environment=PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT=1
Environment=PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT=1
Environment=PORTAL_PUBLIC_ORIGIN=https://voidscape.gg
Environment=PORTAL_EMAIL_VERIFICATION_REQUIRED=1
Environment=PORTAL_EMAIL_VERIFICATION_TTL_HOURS=48
Environment=PORTAL_EMAIL_VERIFICATION_IP_LIMIT=3
Environment=PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT=3
Environment=PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60
Environment=PORTAL_REQUIRE_EMAIL=1
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
- Email delivery supports Resend. Verified signup, older native-account claim, and self-service website-password recovery use the same durable email queue; durable rows keep hashed one-use tokens and only the email event carries a temporarily sealed delivery copy. Staff can also backfill confirmations, send/retry pending events, and send launch campaigns through the token-gated admin API. Use `PORTAL_EMAIL_DRY_RUN=1` only for staging/smoke tests. Turnstile can gate founder reservations, password signup, Google signup, and optionally authenticated character creation when the `PORTAL_CAPTCHA_*` settings below are enabled. Checkout remains a plug-in-later provider slot until that handler is implemented and tested.
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

Binding non-loopback without `PORTAL_DATA_DIR` refuses to start (the signup list would live in $TMPDIR); `PORTAL_ALLOW_TMPDIR=1` overrides. `x-forwarded-for` is only trusted from loopback peers (same-host reverse proxy) or with `PORTAL_TRUST_PROXY=1` — only set that when the origin is reachable exclusively through the proxy, and configure the proxy to overwrite rather than append client-supplied `X-Forwarded-For`. Launch mode defaults completed signup velocity to 3/hour and 5/day per non-local IP plus 20/hour and 50/day per IPv4 `/24`. Verification sends use independent 3/hour per-IP and per-email limits, so sends and resends do not consume completed-signup velocity. Additional characters default to account 5/hour and 10/day, IP 10/hour and 20/day, and subnet 50/hour and 100/day; the signup's initial character bypasses those repeat-character limits while retaining the exact-IP block, name, DB, and max-10 checks.

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

## Loot change editor

`/loot-editor` is a browser-only visual editor for the source-wide NPC drop tables shown on `/npcs` and the Void Chest reward table. The portal dev server provides that clean route; the basic Python static server uses `/loot-editor.html`. It does not sign in, call a mutation API, or update the game server. Drafts stay in that browser's `localStorage`; **Export changes** downloads a review-only JSON file containing changed tables, before/after rows, source locations, affected NPCs, and the source fingerprint. Attach that JSON file in Codex and ask Codex to review and apply it.

`/loot-editor-guide` is the short, illustrated, plain-English guide intended for a nontechnical editor. The Loot Editor links to it from **How to use**.

Regenerate the static baseline after changing NPC drops, NPC/item definitions, or Void Chest rewards:

```bash
scripts/generate-loot-editor-data.sh
scripts/generate-loot-editor-data.sh --check
scripts/test-loot-editor-static.mjs
```

The generator matches the public `/npcs` report: it evaluates `WANT_OPENPK_POINTS` as false and includes source-declared tables behind custom feature gates. It parses each table's real total instead of assuming 128, groups shared NPC tables by object identity, and locks guaranteed rows and nested table rolls in the editor.

## Local API

`dev-server.mjs` is the current zero-dependency single-host portal API plus static file server. Its durable JSON account store needs the backup discipline in `docs/OPERATIONS.md`; the endpoint and security behavior below are the live account-management contract:

- reserved username/email flow, including the Google-first pre-release username flow
- normal portal register/sign-in UI in non-public mode, backed by `POST /api/accounts/register` and `POST /api/accounts/login`
- account-first public launch signup mode that creates a web account and first real game character after required email verification when `PORTAL_OPENRSC_DB` is configured
- invite-code referrals that mint one referral reward subscription-card code per credited beta invite, plus a dev-only referral simulation shortcut
- legacy public status, rates, news, highscores, market intel, and activity feed payloads for API compatibility; the current prelaunch landing uses platform-support proof copy instead of public play/download CTAs
- local download endpoints for built PC client and launcher jars when `scripts/build.sh` has produced them
- launcher update manifest (contract v2) at `GET /api/launcher/manifest.properties`, including SHA-256 and byte-size entries for `Open_RSC_Client.jar` and non-runtime `Client_Base/Cache` files served through `/downloads/client-runtime` and `/downloads/cache/...`; also emits `clientVersion` (from `PORTAL_CLIENT_VERSION` or parsed from `Client_Base/src/orsc/Config.java`, prefixed onto `version` when known) and `launcher.sha256`/`launcher.url`/`launcher.size`/`launcher.version` self-update keys when the launcher jar is built — the launcher jar never appears among `file.N.*` entries
- web account registration/login with `scrypt` password hashing
- local dev Google sign-in through `POST /api/accounts/google/dev`, plus launch-mode Google Identity Services ID-token signup through `POST /api/accounts/google` when `PORTAL_GOOGLE_CLIENT_ID` is configured; both are backed by `web_account_identities` in the schema
- bearer sessions stored as token hashes
- account security controls for password rotation, password-confirmed recovery-code generation, email-or-character-username website-password reset, recovery-code fallback, game-password reset, and ending other sessions
- privacy-preserving abuse signals and pre-creation rate limits for founder reservations, account signup, and real game-character creation; accepted prelaunch accounts keep founder qualification by default, while launch-24h markers follow the separate character cutoff
- privacy-preserving website presence tracking for portal-served pages: `presence.js` sends a browser-id heartbeat to `POST /api/presence/heartbeat`, and the Staff tab reads unique-browser totals/page groups through token-gated `GET /api/admin/presence`
- username-reservation hardening across founder reservations, portal roster cards, and existing game player rows, so public code-only signups cannot squat names that are already created
- token-gated local admin endpoints for account lookup, status review/lock, subscription grants/clears, immutable founder-freeze reconciliation, and session revocation; founder-card revoke is unsupported
- explicit redirect-style Google OAuth and payment checkout stubs that return `501` until those provider flows are configured
- account roster reads
- API-enforced 10-character cap
- character creation previews with title/location/gear/appearance state when no game DB is configured
- game SQLite-backed character creation when `PORTAL_OPENRSC_DB` is configured, including `players`, `curstats`, `maxstats`, `experience`, `capped_experience`, and `web_account_id` cache rows
- launch signup that asks for an 8-20 character password, waits for required email verification, then creates the web account and first linked game save and shows 1 of 10 character slots used
- character link challenges for proving ownership of existing game saves
- subscription-card redemption previews
- per-character reward state for founder, launch-24h, and merged founder/launch card routes
- optional read-only game SQLite character snapshots for saved character state

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
- `PORTAL_SIGNUP_IP_HOURLY_LIMIT=3` / `PORTAL_SIGNUP_IP_DAILY_LIMIT=5` and `PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT=20` / `PORTAL_SIGNUP_SUBNET_DAILY_LIMIT=50` are the launch defaults for completed account creation and code-only founder reservations. Pending verification sends do not add completed-signup signals.
- The initial launch-signup character bypasses repeat-character account/IP/subnet and proxy velocity checks. It still enforces `PORTAL_BLOCKED_IP_CIDRS`, reserved and duplicate name checks in both portal and game data, configured `PORTAL_OPENRSC_DB`, and the max-10 roster control; an initial-signup signal keeps it out of later repeat-character counts.
- Additional characters default to `PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=5` / `PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT=10`, `PORTAL_CHARACTER_IP_HOURLY_LIMIT=10` / `PORTAL_CHARACTER_IP_DAILY_LIMIT=20`, and `PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT=50` / `PORTAL_CHARACTER_SUBNET_DAILY_LIMIT=100`.
- `PORTAL_BLOCKED_IP_CIDRS=...` rejects configured IPv4 CIDR ranges with `403 ip_blocked`; use `/32` entries for targeted exact-IP blocks. `PORTAL_PROXY_IP_CIDRS=...` keeps targeted high-risk ranges on the tighter `PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT=1` and `PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT=1` overrides, each of which bounds both hourly and daily IP checks.
- `PORTAL_LOGIN_IP_FAILURE_LIMIT=10`, `PORTAL_LOGIN_EMAIL_FAILURE_LIMIT=20`, and `PORTAL_LOGIN_FAILURE_WINDOW_MINUTES=15` throttle password login failures by both source IP and account email. Throttled credentials return `429 rate_limited` before password verification.
- `PORTAL_RECOVERY_FAILURE_LIMIT=10` and `PORTAL_RECOVERY_FAILURE_WINDOW_MINUTES=15` throttle bad recovery-code attempts by source IP and account email.
- `PORTAL_PASSWORD_RESET_TTL_MINUTES=30` controls the one-use emailed reset lifetime. `PORTAL_PASSWORD_RESET_IP_LIMIT=5`, `PORTAL_PASSWORD_RESET_ACCOUNT_LIMIT=3`, and `PORTAL_PASSWORD_RESET_WINDOW_MINUTES=60` throttle requests without revealing whether an email/username exists.
- `PORTAL_LEGACY_CLAIM_TTL_MINUTES=30` controls the one-use email confirmation lifetime for older native accounts. `PORTAL_LEGACY_CLAIM_IP_LIMIT=10`, `PORTAL_LEGACY_CLAIM_CHARACTER_LIMIT=5`, and `PORTAL_LEGACY_CLAIM_WINDOW_MINUTES=60` throttle current-game-password proofs globally per source IP and per source-IP/character pair before an email can be queued.
- `PORTAL_SENSITIVE_ACTION_WINDOW_MINUTES=10` is the recent-login window for passwordless federated accounts. Password accounts confirm the current website password before recovery-code rotation or game-password reset.
- `PORTAL_GAME_PASSWORD_RESET_LIMIT=5` and `PORTAL_GAME_PASSWORD_RESET_WINDOW_MINUTES=60` cap character-password reset attempts per source IP and account. `PORTAL_GAME_PASSWORD_HELPER_CLASSPATH` defaults to `server/core.jar`; production uses the isolated `/opt/voidscape/server/portal-password-helper.jar`. The selected character must be portal-created, linked to the latest account ownership marker, and offline. Older-account claims reuse the helper's password-check mode and recheck the latest ownership marker without changing the game password.
- `PORTAL_CAPTCHA_REQUIRED=1` or `PORTAL_CAPTCHA_SIGNUP_REQUIRED=1` requires a CAPTCHA token for founder reservations, password signup, and Google signup. `PORTAL_CAPTCHA_CHARACTER_REQUIRED=1` also requires it for authenticated `/api/characters` creation. Turnstile uses `PORTAL_CAPTCHA_SITE_KEY` and `PORTAL_CAPTCHA_SECRET`; tests can use `PORTAL_CAPTCHA_BYPASS_TOKEN`.
- `PORTAL_STARTER_IP_DAILY_LIMIT=0` is disabled by default so every accepted prelaunch account receives founder-card qualification. Set a positive value only for an intentional staff-review mode before the founder freeze; this setting does not suppress per-character launch-24h markers.
- `PORTAL_PUBLIC_MODE=1` locks the server to the prelaunch signup surface (see "Launching the public prelaunch site").
- `PORTAL_LAUNCH_SIGNUP_MODE=1` opens the public account-first launch flow while keeping dev-only, redirect-OAuth, payment, and link-simulation surfaces hidden; use only with `PORTAL_PUBLIC_MODE=1`.
- `PORTAL_GOOGLE_CLIENT_ID=...` enables the public Google Identity Services button and ID-token verification for launch signup. Without it, Google signup endpoints return `google_oauth_not_configured` and the button stays hidden. `PORTAL_GOOGLE_JWKS_URL` defaults to Google's public cert endpoint and exists only as a test/ops override.
- `PORTAL_PUBLIC_ORIGIN=https://voidscape.gg` sets the canonical public origin used in outgoing emails, public download metadata, and launcher manifest URLs. Set it on production so APK/launcher links stay HTTPS even if the loopback reverse proxy omits forwarded-proto headers. If unset, the portal derives the origin from request headers; public mode with required email verification fails closed until this is configured.
- `PORTAL_EMAIL_PROVIDER=resend` enables live email delivery through Resend when `PORTAL_RESEND_API_KEY` and `PORTAL_EMAIL_FROM` are configured. `PORTAL_EMAIL_REPLY_TO` defaults to `support@voidscape.gg`; `PORTAL_EMAIL_DRY_RUN=1` marks events sent without calling Resend; `PORTAL_REQUIRE_EMAIL=1` makes `/api/health.config.publicReady` fail until email delivery is configured.
- `PORTAL_EMAIL_VERIFICATION_REQUIRED=1` remains required for public password registration. The portal stores the pending signup for `PORTAL_EMAIL_VERIFICATION_TTL_HOURS` (default `48`) and does not create the account, first OpenRSC character, or founder-card qualification until the fragment-based confirmation screen explicitly posts the emailed token to `/api/accounts/verify-email`. Founder qualification requires completion before `2026-07-18T18:00:00Z`; a character completed afterward but before `2026-07-19T18:00:00Z` uses the separate launch-card promotion. A scanner-style GET cannot consume it. Google signup still relies on Google's verified-email ID token.
- `POST /api/accounts/verify-email/resend` is enumeration-safe: it returns the same `202 {"accepted":true}` whether a live pending signup exists, unless a generic limit is reached. Initial and resend verification requests share independent `PORTAL_EMAIL_VERIFICATION_IP_LIMIT=3` and `PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT=3` counters over `PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60`; these counters are separate from completed signup velocity.
- `PORTAL_LAUNCH_AT=...` exposes a public-safe launch countdown through `/api/public`; use ISO-8601 with timezone.
- `PORTAL_LAUNCH_FREE_CARD_HOURS=24` keeps the separate launch-card promotion open for 24 hours after `PORTAL_LAUNCH_AT`; it does not extend the founder reservation cohort past launch. Launch packaging pins this value.
- `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1` controls the release mobile web-client URL used by API metadata and post-launch surfaces; the portal normalizes bare `/play/` values to include `?phone=1` so Android browser links force the phone shell. The prelaunch landing does not link play/download actions.
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

The production schema draft lives in `web/portal/schema/` with SQLite and MySQL/MariaDB variants. It defines web accounts with account-wide subscription expiry, hashed sessions, hashed recovery codes and password-reset tokens, character links, link challenges, founder reservations/referrals, referral reward codes, entitlements, audit events, and abuse-signal buckets. These files are not auto-applied by the OpenRSC server.

## Scope

- Pre-release account landing that recreates the generated launch mockup with layered assets, a muted looping MP4 background, poster fallback, real form fields, account-first username reservation, game-password onboarding, and an after-claim web/launcher/Android handoff
- Coherent returning-player path: landing `Sign in` opens dedicated account access, valid sessions open the real manager, and logout revokes the session and returns to the default landing page
- Launch countdown and reservation-first prelaunch copy, driven by `PORTAL_LAUNCH_AT` with the July 18 launch timestamp as the default
- Launch-open landing takeover after the countdown expires, driven by `/api/public` download availability and account-management state rather than hard-coded static links
- Streamlined first reservation screen: countdown first, signup card directly underneath, then platform proof; redundant live-status and early-reservation benefit strips are intentionally removed
- Platform-support proof copy for Web, Desktop, iOS, and Android while keeping the prelaunch CTA focused on reserving a name and starter card
- Simple authenticated Account dashboard with account identity, subscription state, character count, reserved-card state, and security state; registration and sign-in controls stay outside private manager views
- Extremely simple character roster and create-character flow with a 10-character web-account cap
- Character roster cards that only show the saved character render, account name, level, and subscription state
- Subscription status panel showing account subscription state plus each character's waiting or claimed founder, launch-24h, or merged card route
- API-backed Security tab for one-time recovery-code generation and session review, with password controls shown only for password-enabled accounts
- Email/username and recovery-code website-password reset; all existing sessions and recovery credentials are revoked and the player signs in cleanly afterward
- Signed-out older-character claim using the current game password plus a verified new email; the existing account ID, native character link, and reward-ledger state are preserved, and completion returns to normal sign-in
- Website-password-confirmed game-password reset for offline portal-created linked characters, with compare-and-swap ownership checks and secret-free audit records
- Local prototype persistence through `localStorage`, with API-backed state when served through `scripts/run-portal.sh`
- Web character creation only asks for username and game password; starter path, spawn choice, appearance, and onboarding remain in-game
- Character renders use the avatar PNG generated by the game server on logout when available, with an RSC sprite fallback before the first in-game save/logout
- Optional saved-character snapshot loading when `PORTAL_OPENRSC_DB` points to a local SQLite database
- Account reads refresh linked/created character snapshots from `PORTAL_OPENRSC_DB`, so logout-saved appearance, equipment, levels, and avatar image update the dashboard
- Starter-card bridge into `PORTAL_OPENRSC_DB` through exact per-character founder and launch-24h `player_cache` markers for the Lumbridge Subscription Vendor
- Dev-safe saved-character link challenges that merge a verified game save into the web-account roster
- Local staff API for account review, support grants, immutable founder-freeze reconciliation, and session revocation
- Staff tab for local token-gated account lookup, status/subscription/session actions, frozen reward-state review, roster review, audit events, and abuse-signal context
- Portal account schema contract for web accounts, up to 10 linked game characters, founder qualification, per-character reward display, account-wide subscription expiry, and audit/abuse tracking
- Retired public/content views remain hidden while the pre-release account landing and account-management surfaces are the active portal scope

## Not wired yet

- Production Google OAuth redirect/callback handling
- Production payment provider checkout/webhook handling
- Production-hosted game-database account creation outside the local `PORTAL_OPENRSC_DB` bridge
- Real in-game `::link` command or signed game-server verification callback
- Production referral risk scoring beyond the current verification and rate-limit controls
- Production-safe game character ownership/linking
- Production admin identity/RBAC beyond the local bearer token

The generated concept references used for earlier prototype passes live locally in `.codex-artifacts/` and are excluded from git status through `.git/info/exclude`. The shipped pre-release landing assets live in `web/portal/assets/prelaunch/`; foreground layers are chroma-keyed PNGs and the animated background is an audio-free H.264 MP4 with a poster image.

The production account/character ownership plan is documented in `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`.
