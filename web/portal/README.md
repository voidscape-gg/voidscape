# Voidscape Portal Prototype

Static click-through prototype for the Voidscape pre-release landing, account-management, and subscription portal.

## Prelaunch signup flow (current landing)

The launch landing has two public modes:

- `PORTAL_PUBLIC_MODE=1 PORTAL_LAUNCH_SIGNUP_MODE=1` is the ad-facing launch flow. Public password signup keeps `PORTAL_EMAIL_VERIFICATION_REQUIRED=1`: a visitor enters username, email, and an 8-20 letter/number password, then the portal stores a pending signup for 48 hours and sends a verification link. The web account, first real game character, slot-1 roster link, and starter Subscription card marker are created only after explicit verification. `PORTAL_OPENRSC_DB` must point at the game SQLite DB so signup cannot silently fall back to a preview-only roster card. The initial character bypasses repeat-character velocity limits but still passes the exact-IP blocklist, name availability, configured-DB, and max-10 checks. The account dashboard reads the `starter_card:<webAccountId>` game marker so a card shows as waiting until the vendor marks it claimed. If `PORTAL_GOOGLE_CLIENT_ID` is configured, the same form also shows Google sign-in: Google owns the web login, while the submitted password becomes the first character login until the clients support web-account SSO.
- `PORTAL_PUBLIC_MODE=1` without launch signup keeps the older code-only reservation flow only while `PORTAL_LAUNCH_AT` is still in the future. A visitor enters an email + desired username, the username is reserved, and a one-time `VOID-XXXX-XXXX` signup code is shown on-screen. The code is written into the game DB as a global `player_cache` row (`signup_code:<CODE>` = 1) when `PORTAL_OPENRSC_DB` is set. In-game, the player talks to the Void Subscription Vendor in Lumbridge, types the code into the popup he opens (custom client 10087+), and receives one tradable Subscription card. The reservation endpoint returns 404 once launch-account mode is enabled or the launch timestamp is reached, so it cannot mint legacy bearer-card codes after cutover.

One reservation/code is minted per canonical email in code-only mode; re-signing up with the same email shows the same code. Username reservation checks the founder ledger, existing portal roster cards, and the configured game `players` table before showing a name as reserved; an existing game character may refresh the reservation only with the same canonical email. If the signup credits another founder's invite code during beta, the referrer also receives one additional referral reward code per credited referral. Referral reward codes use the same `signup_code:<CODE>` ledger and vendor redemption path. Codes are bearer tokens; anyone who knows the email can re-view the signup code, which is accepted for prelaunch stakes. Codes are minted only on the code-only public route; registered portal accounts keep using the account-bound `starter_card:<id>` marker instead.

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

Set `PORTAL_PUBLIC_MODE=1` for any internet-facing prelaunch deployment. With only that flag, the server is code-only and locks down to exactly: static files, `GET /api/health`, `GET /api/public` (sanitized — no fake world stats, but with launcher/APK availability, global launch-rule metadata, and optional launch countdown metadata), `GET /api/integrity` (public-safe transparency aggregates), `GET /api/legends` (strict active-season achievement and PK-streak projection), public launcher/APK downloads, `GET /api/launcher/manifest.properties`, prelaunch-only `POST /api/founder/reservations`, `POST /api/presence/heartbeat`, and signed `POST /api/payments/tebex/webhook`. Backend `/api/admin/*` handlers exist only for bounded loopback maintenance: every public nginx origin must return `404`, and `PORTAL_ADMIN_TOKEN` stays unset during normal operation. Everything else — registration, login, dev Google sign-in, character creation, link simulation, subscription redeem, snapshots, and avatars — returns 404. The UI pins itself to the landing view (deep links like `#admin` or `#dashboard` land on the signup page).

For launch ads, add `PORTAL_LAUNCH_SIGNUP_MODE=1`. This closes the legacy founder-reservation/signup-code endpoint, keeps dev/link-simulation surfaces locked down, and opens the safe account path needed by real players: registration and email verification, optional Google ID-token signup, login/logout, account reads, character creation/deletion, emailed and recovery-code website-password reset, authenticated game-password reset, the remaining security actions, and authenticated one-card Tebex checkout when its full configuration is present. It also exposes `launchSignupMode: true` plus public OAuth metadata through `/api/public`, enables the authenticated Account/Characters/Subscription/Security views, and turns the landing form into "create account + first character + reserve starter card" copy. The launch form password is deliberately limited to letters and numbers because it is also the first character's game login until the clients support web-account SSO.

Returning players use the landing-nav `Sign in` action, which opens the dedicated `/portal?auth=login` account-access screen. Signed-out protected deep links resolve to that screen without rendering private account data. A valid login opens `/portal#dashboard`; a valid landing-page session changes the action to `Manage Account` and replaces signup with launch downloads. Logout revokes the server session and returns to `/`.

The launch countdown defaults to **Saturday, July 18, 2026 at 11:00 AM Pacific / 2:00 PM Eastern / 7:00 PM UK** (`2026-07-18T18:00:00Z`). Set `PORTAL_LAUNCH_AT=2026-07-18T18:00:00Z` explicitly in production so the date is obvious in deployment config. `PORTAL_BETA_OPEN_AT` remains accepted as a backward-compatible fallback, but launch deployments should use `PORTAL_LAUNCH_AT`.

`PORTAL_LAUNCH_FREE_CARD_HOURS=0` is the launch policy: eligible prelaunch accounts receive the promised card, and newly created accounts stop receiving it exactly when `PORTAL_LAUNCH_AT` is reached. A positive value deliberately extends the promotion after launch by that many hours and must not be used for the cutover release.

Set `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1` if the browser client moves. The July 18 launch surface advertises Web, Desktop, and Android; iPhone support is deferred until physical Safari/Home Screen QA is complete. Leave `PORTAL_ANDROID_PLAY_URL` unset while the Play release is still under review. After code `9 / 1.0.8` is publicly available, set it to `https://play.google.com/store/apps/details?id=com.voidscape.gg`; the portal then makes Google Play the primary Android action and labels the configured release-signed APK as the direct fallback. The public page keeps the clickable funnel on reserving the account name until the world opens. Once `PORTAL_LAUNCH_AT` is in the past, the same landing switches into the launch-open surface: desktop launcher download, web client, Android release availability, live build proof, and account-management CTAs render from `/api/public` download metadata.

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
Environment=PORTAL_LAUNCH_FREE_CARD_HOURS=0
Environment=PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1
# Add only after the production listing is publicly available:
# Environment=PORTAL_ANDROID_PLAY_URL=https://play.google.com/store/apps/details?id=com.voidscape.gg
Environment=PORTAL_DATA_DIR=/var/lib/voidscape-portal
Environment=PORTAL_OPENRSC_DB=/opt/voidscape/server/inc/sqlite/voidscape.db
Environment=PORTAL_LEGENDS_SEASON_ID=launch-2026
Environment=PORTAL_GAME_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/core.jar
Environment=PORTAL_INTEGRITY_SNAPSHOT=/var/lib/voidscape-portal/integrity-summary.json
# Keep PORTAL_ADMIN_TOKEN unset during normal public operation. A temporary
# maintenance token may be supplied out of band only while using 127.0.0.1:8788.
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
- For a bounded maintenance export, set a temporary token, query only `http://127.0.0.1:8788/api/admin/signups?format=csv`, then remove the token and restart the portal. Public HTTPS must return `404` regardless of headers.
- Google sign-in is optional. Set `PORTAL_GOOGLE_CLIENT_ID` to a Google Identity Services web client id to show the button; leave it unset to keep the email/password path only. The redirect-style `/api/oauth/google/start` and `/api/oauth/google/callback` endpoints are still placeholders.
- Email delivery supports Resend. Verified signup, older native-account claim, and self-service website-password recovery use the same durable email queue; durable rows keep hashed one-use tokens and only the email event carries a temporarily sealed delivery copy. Staff can also backfill confirmations, send/retry pending events, and send launch campaigns through the token-gated admin API. Use `PORTAL_EMAIL_DRY_RUN=1` only for staging/smoke tests. Turnstile can gate founder reservations, password signup, Google signup, and optionally authenticated character creation when the `PORTAL_CAPTCHA_*` settings below are enabled.
- Tebex checkout uses the Headless universal-store API and one allowlisted single-payment package. Basket custom data contains only a random 256-bit `voidscape_intent`; package custom data binds that intent to the returned basket id. Backend basket creation also sends Tebex the customer IPv4 address its API requires, but Voidscape does not store that raw address in the game commerce ledger. Only signed raw-body webhooks whose intent, account, basket, package, quantity, one-off sequence, currency, and money fields match create a pending card entitlement. The portal never writes player inventory or consumes Tebex's command queue.
- Code-only launch day, to make codes redeemable in-game: copy `dev-store.json` from the public host into a `PORTAL_DATA_DIR` on the game machine, run the portal there with `PORTAL_OPENRSC_DB=/path/to/voidscape.db` (loopback is fine, no public mode needed), and `curl -X POST -H "x-portal-admin-token: $TOKEN" http://127.0.0.1:8788/api/admin/signups/sync`. Already-redeemed codes are skipped; the endpoint is idempotent.
- If bot signups become a problem, put Cloudflare in front with a managed challenge on `POST /api/founder/reservations` — the app-side per-IP cap is the backstop, not the whole defense.

After deploying a launch-signup staging build, verify it with the hosted release gate:

```bash
scripts/verify-launch-staging.mjs \
  --portal-url https://yourdomain.com/ \
  --web-url https://yourdomain.com/play/ \
  --ws wss://yourdomain.com/play/ws/ \
  --server-config <copy-of-deployed-server.conf> \
  --admin-public-url https://yourdomain.com/ \
  --signup-username <new-qa-character> \
  --signup-email <email-you-can-open-now> \
  --signup-password <new-portal-and-game-password> \
  --run-signup
```

Start the command, open the delivered message, and verify that exact signup during the bounded wait. Through launch, production keeps and probes all three current origins: `voidscape.gg`, `www.voidscape.gg`, and the legacy sslip origin; the generated bundle verifier adds them automatically and requires the public admin-route guard on each. Revisit and remove the legacy alias only after launch with a separate verifier-contract change. The verifier reads `/api/health` and `/api/public`, so those endpoints expose only public-safe booleans for durable storage, OpenRSC DB bridge status, and launch config readiness, not filesystem paths or credentials. In public mode, `/api/health.config.publicReady` requires a non-placeholder `PORTAL_ABUSE_HASH_SALT`; normal production should report `adminTokenConfigured=false` because public administration is blocked and the token is unset.

Binding non-loopback without `PORTAL_DATA_DIR` refuses to start (the signup list would live in $TMPDIR); `PORTAL_ALLOW_TMPDIR=1` overrides. `x-forwarded-for` is only trusted from loopback peers (same-host reverse proxy) or with `PORTAL_TRUST_PROXY=1` — only set that when the origin is reachable exclusively through the proxy, and configure the proxy to overwrite rather than append client-supplied `X-Forwarded-For`. Launch mode defaults completed signup velocity to 3/hour and 5/day per non-local IP plus 20/hour and 50/day per IPv4 `/24`. Verification sends use independent 3/hour per-IP and per-email limits, so sends and resends do not consume completed-signup velocity. Additional characters default to account 5/hour and 10/day, IP 10/hour and 20/day, and subnet 50/hour and 100/day; the signup's initial character bypasses those repeat-character limits while retaining the exact-IP block, name, DB, and max-10 checks.

### One-shot native-account and launch-card cutover

The native-account backfill is a cutover operation, not a recurring service. It creates synthetic portal ownership records for real game accounts present at the cutover, preserves active subscription time, keeps older account-level starter promises intact, and seeds one `launch_subcard_2026:<playerId>` entitlement for every included final-roster character. Take verified backups of both the game DB and `$PORTAL_DATA_DIR/dev-store.json`, pause native and portal registration, then review the dry-run before applying:

```bash
PORTAL_ADMIN_TOKEN="$TOKEN" scripts/native-portal-backfill-sweep.sh --dry-run
# Review cohort.exceptions. Decide every flagged player explicitly:
PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS="12,34" \
PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS="56,78" \
PORTAL_ADMIN_TOKEN="$TOKEN" scripts/native-portal-backfill-sweep.sh --dry-run
# Archive that report, then copy its exact reviewToken into the separate apply:
PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN="$REVIEW_TOKEN" \
PORTAL_NATIVE_BACKFILL_AS_OF_MS="$REVIEWED_AS_OF_MS" \
PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS="12,34" \
PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS="56,78" \
PORTAL_ADMIN_TOKEN="$TOKEN" scripts/native-portal-backfill-sweep.sh --apply
```

The cohort report automatically includes ordinary user-group saves. Staff/privileged groups, actively banned saves, invalid rows, and obvious dev/test identities are exceptions; every exception needs a separate include or exclude decision before `cohort.ready` becomes true. Expired temporary bans use the same timestamp rule as game login and are not treated as active. The review token binds the frozen `asOfMs`, player id/name/creation date/group/ban state, account links, launch-card policy and seal state, and both decision lists, so a changed DB or changed decision requires a new dry-run. A later `--apply` invocation must receive both the archived token and that exact `asOfMs`; allowing the script to choose a new timestamp intentionally produces a different token. Apply also fails closed unless the authenticated request explicitly contains `{"apply":true}` and the matching token.

All character markers and the global `launch_subcard_2026:done = 1` seal are written in the same `BEGIN IMMEDIATE` transaction. Once sealed, later backfill reruns may repair portal ownership but never add a card for a character created after the snapshot. Duplicate or malformed campaign/seal rows block apply for reconciliation. The game-cache write remains the portal store's post-persist phase, so a normal SQLite failure restores the original portal JSON. Archive both the pre-apply dry-run and post-apply zero-pending report.

If the systemd unit is installed, put the reviewed token and exception decisions in its private `portal.env`, invoke it once with `systemctl start voidscape-native-portal-backfill.service`, then remove those one-use values. Do not enable the unit, and do not install a timer. Re-run `--dry-run` afterward and require zero pending changes plus a present cutover seal before reopening registration.

Before any player can claim, rollback may restore the coordinated game-DB and portal-store backups. After claims begin, do not restore an old entitlement database: that can duplicate already traded cards or lose claims. Preserve the marker ledger and reconcile forward instead. Deleting a launch character forfeits its unclaimed entitlement; replacement characters are intentionally outside the sealed cohort.

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

`dev-server.mjs` is the current zero-dependency single-host portal API plus static file server. Its durable JSON account store needs the backup discipline in `docs/OPERATIONS.md`; the endpoint and security behavior below are the live account-management contract:

- public `/legends`, a standalone responsive chronicle of launch-season world firsts and qualified Wilderness streak leaders with safe loading, empty, and unavailable states
- reserved username/email flow, including the Google-first pre-release username flow
- normal portal register/sign-in UI in non-public mode, backed by `POST /api/accounts/register` and `POST /api/accounts/login`
- account-first public launch signup mode that creates a web account and first real game character after required email verification when `PORTAL_OPENRSC_DB` is configured
- invite-code referrals that mint one referral reward subscription-card code per credited beta invite, plus a dev-only referral simulation shortcut
- legacy public status, rates, news, highscores, market intel, and activity feed payloads for API compatibility; the current prelaunch landing uses platform-support proof copy instead of public play/download CTAs
- local download endpoints for built PC client and launcher jars when `scripts/build.sh` has produced them
- launcher update manifest (contract v2) at `GET /api/launcher/manifest.properties`, including SHA-256 and byte-size entries for `Open_RSC_Client.jar` and non-runtime `Client_Base/Cache` files served through `/downloads/client-runtime` and `/downloads/cache/...`; the exact legacy MD5-table path `/downloads/cache/Open_RSC_Client.jar` safely aliases the configured client runtime without creating a duplicate v2 manifest entry; the manifest also emits `clientVersion` (from `PORTAL_CLIENT_VERSION` or parsed from `Client_Base/src/orsc/Config.java`, prefixed onto `version` when known) and `launcher.sha256`/`launcher.url`/`launcher.size`/`launcher.version` self-update keys when the launcher jar is built — the launcher jar never appears among `file.N.*` entries
- web account registration/login with `scrypt` password hashing
- local dev Google sign-in through `POST /api/accounts/google/dev`, plus launch-mode Google Identity Services ID-token signup through `POST /api/accounts/google` when `PORTAL_GOOGLE_CLIENT_ID` is configured; both are backed by `web_account_identities` in the schema
- bearer sessions stored as token hashes
- account security controls for password rotation, password-confirmed recovery-code generation, email-or-character-username website-password reset, recovery-code fallback, game-password reset, and ending other sessions
- privacy-preserving abuse signals and pre-creation rate limits for founder reservations, account signup, and real game-character creation; accepted promo accounts keep their starter card by default
- privacy-preserving website presence tracking for portal-served pages: `presence.js` sends a browser-id heartbeat to `POST /api/presence/heartbeat`, and the Staff tab reads unique-browser totals/page groups through token-gated `GET /api/admin/presence`
- username-reservation hardening across founder reservations, portal roster cards, and existing game player rows, so public code-only signups cannot squat names that are already created
- token-gated local admin endpoints for account lookup, status review/lock, subscription grants/clears, starter-card grants/revokes, and session revocation
- an explicit redirect-style Google OAuth stub, plus authenticated Tebex checkout that remains `501 payments_not_configured` until every required commerce setting is present
- account roster reads
- API-enforced 10-character cap
- character creation previews with title/location/gear/appearance state when no game DB is configured
- game SQLite-backed character creation when `PORTAL_OPENRSC_DB` is configured, including `players`, `curstats`, `maxstats`, `experience`, `capped_experience`, and `web_account_id` cache rows
- coordinated portal/game writes: a failed atomic JSON-store rename compensates newly created players and promo markers, while destructive character deletes run only after the portal roster has persisted; the API smoke suite injects write and rename failures to enforce both sides of this contract
- launch signup that asks for an 8-20 character password, waits for required email verification, then creates the web account and first linked game save and shows 1 of 10 character slots used
- character link challenges for proving ownership of existing game saves
- subscription-card redemption previews
- starter reward wallet state for the free weekly subscription-card prize
- optional read-only game SQLite character snapshots for saved character state
- GET-only `/api/legends`, a deterministic active-season allowlist of public first-achievement fields and aggregate qualified-PK leaders; it never exposes raw PK events or private ledger evidence
- exact-once Tebex payment/refund/dispute ingestion, account paid-card counts, and token-gated commerce list/reconcile reporting

Run the API smoke test:

```bash
scripts/test-portal-api.sh
tests/portal-legends-api.sh
```

The focused Legends suite also locks clean `/legends` routing, the page's API-only renderer, mobile breakpoints, safe DOM construction, and the absence of private ledger field names. The current desktop/mobile browser proof is archived under `tmp/portal-legends-slice7-v1/summary.json` with populated, empty, unavailable, and retry-recovery captures.

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

- `PORTAL_OPENRSC_DB=/path/to/voidscape.db` enables the SQLite game bridge. Failed SQLite child processes are logged only as a fixed operation label, bounded exit/code, and allowlisted failure category; the portal never logs the original child error, SQL command, stdout/stderr, database path, or query values.
- `PORTAL_LEGENDS_SEASON_ID=launch-2026` fixes `GET /api/legends` to one validated server-selected season. The default is `launch-2026`; query parameters cannot select historical/private seasons.
- `PORTAL_ADMIN_TOKEN=...` enables `/api/admin/*`; without it the admin API returns `admin_not_configured`.
- `PORTAL_TEBEX_PUBLIC_TOKEN`, `PORTAL_TEBEX_PRIVATE_KEY`, and `PORTAL_TEBEX_WEBHOOK_SECRET` are the three Tebex credentials. The private key and webhook secret are distinct secrets.
- `PORTAL_TEBEX_SUBSCRIPTION_CARD_PACKAGE_ID` must identify exactly one Tebex package configured as `single`. `PORTAL_TEBEX_EXPECTED_CURRENCY` is its three-letter currency and `PORTAL_TEBEX_EXPECTED_PRICE_MINOR` is its exact price in minor units (for example `500` for USD 5.00). Any mismatch fails closed without an entitlement.
- The package must have quantity changes and gifting disabled and must have zero game-server commands/RCON actions, gift-card delivery, Discord actions, or any other Tebex/provider deliverable. The portal entitlement is the sole delivery path. Tebex-authorized coupons/gift-card balance may change what was paid; the ledger separately records expected catalog price, signed product-paid, signed transaction-paid, and their `tax_and_adjustments` delta. It does not call that delta customer tax; Tebex remains the source of truth for tax/fee breakdowns. No browser/client price or discount input is accepted.
- Tebex must send `payment.completed`, `payment.refunded`, and all `payment.dispute.*` events to `https://<portal-host>/api/payments/tebex/webhook`. The handler also answers `validation.webhook`. Apply `server/database/<backend>/patches/2026_07_11_add_portal_commerce.sql` before enabling credentials.
- Hard enablement gate: complete one controlled real purchase and refund/dispute test before production. Prove that Headless preserves package custom `voidscape_intent` + `voidscape_basket` into the payment webhook, no command queue or external deliverable fires, and exactly one pending ledger entitlement/card is created. The current Tebex guide and OpenAPI disagree about package-custom response echo, so mock coverage alone is not approval to enable live credentials.
- Native clients open the direct handoff `/portal#subscription-buy`. Signed-out users retain that intent through login; signed-in users start one checkout. Neither the client, handoff, nor checkout-return URL fulfills a card.
- `PORTAL_TEBEX_API_BASE` is test-only and the process rejects it unless `PORTAL_ENABLE_TEST_FAULTS=1`; never set either value in staging or production. Production always calls `https://headless.tebex.io/api`.
- Staff can inspect the PII-free restricted ledger with `GET /api/admin/commerce` and run the deterministic read-only anomaly report with `POST /api/admin/commerce/reconcile`. Neither endpoint fulfills cards.
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
- `PORTAL_STARTER_IP_DAILY_LIMIT=0` is disabled by default so every accepted account in the promo window receives the starter card. Set a positive value only for an intentional staff-review mode after changing public promo expectations.
- `PORTAL_LAUNCH_FREE_CARD_HOURS=0` keeps the promo open before `PORTAL_LAUNCH_AT` and closes it exactly at launch. Positive values extend the post-launch minting window and are not part of the approved release policy.
- `PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS=...` and `PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS=...` are comma-separated player IDs recording explicit include/exclude decisions for every staff, actively banned, invalid, or suspected test exception in the one-shot cutover report. `PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN=...` and `PORTAL_NATIVE_BACKFILL_AS_OF_MS=...` are both required for `--apply` and must exactly match the separately reviewed dry-run. `PORTAL_NATIVE_BACKFILL_GRANT_LAUNCH_CHARACTER_CARDS=1` is the release default; setting it to `0` deliberately omits campaign seeding and the seal.
- `PORTAL_PUBLIC_MODE=1` locks the server to the prelaunch signup surface (see "Launching the public prelaunch site").
- `PORTAL_LAUNCH_SIGNUP_MODE=1` opens the public account-first launch flow while keeping dev-only, redirect-OAuth, payment, and link-simulation surfaces hidden; use only with `PORTAL_PUBLIC_MODE=1`.
- `PORTAL_GOOGLE_CLIENT_ID=...` enables the public Google Identity Services button and ID-token verification for launch signup. Without it, Google signup endpoints return `google_oauth_not_configured` and the button stays hidden. `PORTAL_GOOGLE_JWKS_URL` defaults to Google's public cert endpoint and exists only as a test/ops override.
- `PORTAL_PUBLIC_ORIGIN=https://voidscape.gg` sets the canonical public origin used in outgoing emails, public download metadata, and launcher manifest URLs. Set it on production so APK/launcher links stay HTTPS even if the loopback reverse proxy omits forwarded-proto headers. If unset, the portal derives the origin from request headers; public mode with required email verification fails closed until this is configured.
- `PORTAL_EMAIL_PROVIDER=resend` enables live email delivery through Resend when `PORTAL_RESEND_API_KEY` and `PORTAL_EMAIL_FROM` are configured. `PORTAL_EMAIL_REPLY_TO` defaults to `support@voidscape.gg`; `PORTAL_EMAIL_DRY_RUN=1` marks events sent without calling Resend; `PORTAL_REQUIRE_EMAIL=1` makes `/api/health.config.publicReady` fail until email delivery is configured.
- `PORTAL_EMAIL_VERIFICATION_REQUIRED=1` remains required for public password registration. The portal stores the pending signup for `PORTAL_EMAIL_VERIFICATION_TTL_HOURS` (default `48`) and does not create the account, first OpenRSC character, or starter-card marker until the fragment-based confirmation screen explicitly posts the emailed token to `/api/accounts/verify-email`. The email says it opens a verification page, and that page prominently asks the player to press `Verify email`; a scanner-style GET cannot consume the token. Retryable network/5xx failures retain the token and report temporary unavailability, while expired and invalid/used tokens keep distinct messages. Google signup still relies on Google's verified-email ID token.
- `POST /api/accounts/verify-email/resend` is enumeration-safe: it returns the same `202 {"accepted":true}` whether a live pending signup exists, unless a generic limit is reached. Initial and resend verification requests share independent `PORTAL_EMAIL_VERIFICATION_IP_LIMIT=3` and `PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT=3` counters over `PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60`; these counters are separate from completed signup velocity.
- `PORTAL_LAUNCH_AT=...` exposes a public-safe launch countdown through `/api/public`; use ISO-8601 with timezone.
- `PORTAL_WEB_CLIENT_URL=https://voidscape.gg/play/?phone=1` controls the release web-client URL used by API metadata and post-launch surfaces; the portal normalizes bare `/play/` values to include `?phone=1` for the existing phone-shell route. Keeping that route available is not an iPhone launch-support claim. The prelaunch landing does not link play/download actions.
- `PORTAL_ANDROID_PLAY_URL=https://play.google.com/store/apps/details?id=com.voidscape.gg` enables Google Play as the primary Android action. Leave it unset until Play has approved the production release: unset means no Play row and preserves the existing APK-only UI. The server accepts only the HTTPS `play.google.com` details page for package `com.voidscape.gg`, strips locale/campaign parameters to the canonical URL, and fails startup for any other host, path, scheme, duplicate package id, or package name. When enabled, the configured `PORTAL_ANDROID_APK` remains visible as `Signed APK fallback` when that artifact is available.
- `PORTAL_ANDROID_APK=/path/to/voidscape.apk` selects the exact APK served by `/downloads/android-apk`. Production must set it to the promoted checker-passed release artifact; when unset, the APK row is unavailable and the portal does not guess a build output. The prelaunch landing still hides post-launch play actions until the countdown opens the chooser.
- `PORTAL_CLIENT_CACHE_DIR=/path/to/Client_Base/Cache` overrides the cache root used by launcher-manifest hashing and `/downloads/cache/...`; the staged deployment sets it to `/opt/voidscape/client/Cache`. Runtime credential/config files remain excluded, traversal is rejected, and the exact legacy `Open_RSC_Client.jar` cache request aliases the separately configured client runtime rather than resolving inside this directory.
- `PORTAL_BIND_HOST=127.0.0.1` sets the listen address; non-loopback binds require `PORTAL_DATA_DIR` (or `PORTAL_ALLOW_TMPDIR=1`).
- `PORTAL_TRUST_PROXY=1` trusts `x-forwarded-for` from non-loopback peers (only set behind a real proxy).
- `PORTAL_ABUSE_HASH_SALT=...` salts stored IP/email/identity hashes. Set a stable private value before using persistent data; public launch verification fails if this is missing, still the dev fallback, shorter than 16 characters, or a `CHANGE_ME` placeholder.

To let the Characters screen load real saved character state from a local OpenRSC SQLite database, start the server with:

```bash
PORTAL_OPENRSC_DB=/Users/s/Desktop/voidscape/server/inc/sqlite/voidscape.db scripts/run-portal.sh
```

The snapshot endpoint is read-only. It reads safe character fields from `players`, `player_cache`, `invitems`, `itemstatuses`, optional `equipped`, and item/title definitions; it does not create accounts, mutate game data, or prove web ownership.

When the local API is running with `PORTAL_OPENRSC_DB`, the Characters screen can start a link challenge for an existing saved character. The API returns a one-time `::link <code>` command and stores only the code hash. The prototype's `Simulate verified` button exercises the verification path locally and replaces the starter preview with the saved character state. Production still needs an in-game command or signed game-server callback before real ownership is trusted.

The production schema draft lives in `web/portal/schema/` with SQLite and MySQL/MariaDB variants. It defines web accounts with account-wide subscription expiry, hashed sessions, hashed recovery codes and password-reset tokens, character links, link challenges, founder reservations/referrals, referral reward codes, entitlements, audit events, abuse-signal buckets, and the PII-free commerce ledger. The web-account drafts are not auto-applied by OpenRSC; matching `2026_07_11_add_portal_commerce.sql` game-database patches provide the shared paid-card claim boundary. `/api/health` reports only commerce booleans and fails public readiness unless the configured DB is readable and all four commerce tables exist.

## Scope

- Pre-release account landing that recreates the generated launch mockup with layered assets, a muted looping MP4 background, poster fallback, real form fields, account-first username reservation, game-password onboarding, and an after-claim web/launcher/Android handoff
- Coherent returning-player path: landing `Sign in` opens dedicated account access, valid sessions open the real manager, and logout revokes the session and returns to the default landing page
- Launch countdown and reservation-first prelaunch copy, driven by `PORTAL_LAUNCH_AT` with the July 18 launch timestamp as the default
- Launch-open landing takeover after the countdown expires, driven by `/api/public` download availability and account-management state rather than hard-coded static links
- Streamlined first reservation screen: countdown first, signup card directly underneath, then platform proof; redundant live-status and early-reservation benefit strips are intentionally removed
- Platform-support proof copy for Web, Desktop, and Android while keeping the prelaunch CTA focused on reserving a name and starter card; iPhone remains deferred pending physical Safari/Home Screen QA
- Simple authenticated Account dashboard with account identity, subscription state, character count, reserved-card state, and security state; registration and sign-in controls stay outside private manager views
- Extremely simple character roster and create-character flow with a 10-character web-account cap
- Character roster cards that only show the saved character render, account name, level, and subscription state
- Subscription status panel focused only on the current account state and whether a starter subscription card is waiting at the Lumbridge vendor
- API-backed Security tab for one-time recovery-code generation and session review, with password controls shown only for password-enabled accounts
- Email/username and recovery-code website-password reset; all existing sessions and recovery credentials are revoked and the player signs in cleanly afterward
- Signed-out older-character claim using the current game password plus a verified new email; the existing account ID, native character link, and starter-card entitlement are preserved, and completion returns to normal sign-in
- Website-password-confirmed game-password reset for offline portal-created linked characters, with compare-and-swap ownership checks and secret-free audit records
- Local prototype persistence through `localStorage`, with API-backed state when served through `scripts/run-portal.sh`
- Web character creation only asks for username and game password; starter path, spawn choice, appearance, and onboarding remain in-game
- Character renders use the avatar PNG generated by the game server on logout when available, with an RSC sprite fallback before the first in-game save/logout
- Optional saved-character snapshot loading when `PORTAL_OPENRSC_DB` points to a local SQLite database
- Account reads refresh linked/created character snapshots from `PORTAL_OPENRSC_DB`, so logout-saved appearance, equipment, levels, and avatar image update the dashboard
- Optional starter-card bridge into `PORTAL_OPENRSC_DB` through account-level game `player_cache` markers for the Lumbridge Subscription Vendor
- Dev-safe saved-character link challenges that merge a verified game save into the web-account roster
- Local staff API for account review, support grants, starter-card correction, and session revocation
- Staff tab for local token-gated account lookup, status/subscription/starter-card/session actions, roster review, audit events, and abuse-signal context
- Portal account schema contract for web accounts, up to 10 linked game characters, starter-card rewards, account-wide subscription expiry, and audit/abuse tracking
- Retired public/content views remain hidden while the pre-release account landing and account-management surfaces are the active portal scope

## Not wired yet

- Production Google OAuth redirect/callback handling
- Live Tebex creator-panel credentials, package provisioning, webhook registration, and a completed staging test purchase/refund (the adapter and mock integration tests are wired)
- Production-hosted game-database account creation outside the local `PORTAL_OPENRSC_DB` bridge
- Real in-game `::link` command or signed game-server verification callback
- Production referral risk scoring beyond the current verification and rate-limit controls
- Production-safe game character ownership/linking
- Production admin identity/RBAC beyond the local bearer token

The generated concept references used for earlier prototype passes live locally in `.codex-artifacts/` and are excluded from git status through `.git/info/exclude`. The shipped pre-release landing assets live in `web/portal/assets/prelaunch/`; foreground layers are chroma-keyed PNGs and the animated background is an audio-free H.264 MP4 with a poster image.

The production account/character ownership plan is documented in `docs/ACCOUNT-MANAGEMENT-ARCHITECTURE.md`.
