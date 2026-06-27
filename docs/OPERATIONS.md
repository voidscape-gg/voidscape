# Operations

This is the practical runbook for operating Voidscape outside a one-off coding session. Development details live in `docs/DEVELOPMENT.md`; this file focuses on keeping a running world healthy.

## Operating modes

| Mode | Intended use | Database | Notes |
|---|---|---|---|
| Local dev | Daily development and quick smoke tests | SQLite | Use `scripts/*.sh`; okay to reset often. |
| Staging | Friends-only validation before a player-facing test | SQLite or MariaDB | Should use release-like config and backups. |
| Production | Public or semi-public world | MariaDB recommended | Needs backup, monitoring, update, and rollback discipline. |

SQLite is fine for local work and small private testing. Move to MariaDB before real public load, multi-operator work, or anything where file-copy backups are too informal.

For a launch-candidate staging deployment, run the hosted gate after syncing the portal, game server, and web client. The command intentionally requires an explicit signup flag because it creates a real staged account and first game character:

```bash
scripts/verify-launch-staging.mjs \
  --portal-url https://<staging-host>/ \
  --web-url https://<staging-host>/play/ \
  --ws wss://<staging-host>/play/ws/ \
  --server-config <copy-of-deployed-server.conf> \
  --run-signup
```

The verifier checks durable portal storage, the OpenRSC DB bridge, portal-first registration, packet registration off, command lockdown in the deployed server config, hidden Google by default, disabled payment checkout, a real account-first signup, and the uploaded `/play` package against `dist/web-teavm/voidscape-web-build.json`.

## Build and run

Canonical commands:

```bash
scripts/build.sh
scripts/run-server.sh
scripts/run-client.sh
```

`scripts/run-server.sh` always runs `server/local.conf`. For staging or production, keep the actual deployed config outside Git, but keep its intended values mirrored in `docs/CONFIG-MATRIX.md`.

Useful local stop pattern:

```bash
SERVER_PID=$(lsof -tiTCP:43596 -sTCP:LISTEN || true)
[ -n "$SERVER_PID" ] && kill "$SERVER_PID"
```

Avoid broad `pkill -f openrsc` patterns for the server. The running command line is usually generic Java/Ant text and may not contain `openrsc`.

## Friends-only hosted beta

For a private beta, the intended player flow is one hosted game server plus a preconfigured launcher. Friends should not need the website or any manual client configuration.

Server host:

- Run `scripts/build.sh`, then `scripts/run-server.sh` on the VPS or dedicated host.
- Open the configured TCP `server_port` in the host firewall and provider firewall. Voidscape local config currently uses `43596`.
- Keep `ws_server_port` closed unless a browser/web client is intentionally being tested.
- Back up the database before inviting testers and before every update.

Launcher/update packaging from a build machine:

```bash
scripts/package-friend-beta.sh \
  --host <server-host-or-ip> \
  --port 43596 \
  --base-url https://<server-host-or-ip>/voidscape/update \
  --discord-url <optional-discord-invite>
```

Upload or sync `dist/friend-beta/update/` to the URL used as `--base-url`, then share only `dist/friend-beta/VoidscapeLauncher.jar` with testers. The launcher embeds the game endpoint and manifest URL, downloads the PC client/cache on first Play, writes `ip.txt`/`port.txt` into its runtime cache, and launches the client.

External smoke checks:

```bash
curl -I https://<server-host-or-ip>/voidscape/update/manifest.properties
nc -vz <server-host-or-ip> 43596
```

Newly created characters use the normal User rank. Promote only trusted testers manually with `make rank-sqlite` / `make rank-mysql` or in-game `::setrank` from an owner/admin account when admin-only beta checks are needed.

## Launcher updates

Players should receive normal client/cache updates through the launcher, not by manually replacing the PC client jar.

Portal-hosted deployment:

- Run `scripts/build.sh` on the machine that serves the portal, or sync `Client_Base/Open_RSC_Client.jar`, `Client_Base/Cache/`, and `PC_Launcher/OpenRSC.jar` there after building.
- Serve the portal over HTTPS behind the final public hostname.
- Configure the launcher with `voidscape.portalUrl=https://<portal-host>`; it will derive `https://<portal-host>/api/launcher/manifest.properties` automatically. Set `voidscape.manifestUrl` only when the manifest lives somewhere else.
- Before announcing an update, verify:

```bash
curl -I https://<portal-host>/api/launcher/manifest.properties
curl -I https://<portal-host>/downloads/pc-client
```

Static beta deployment:

- Use `scripts/package-friend-beta.sh --base-url https://<host>/voidscape/update ...`.
- Upload/sync the generated `dist/friend-beta/update/` folder.
- Verify `curl -I https://<host>/voidscape/update/manifest.properties`.

In both modes, the launcher checks the manifest on Play, verifies SHA-256 hashes, downloads only changed files, preserves runtime files like credentials and endpoint settings, and then launches the cached `Open_RSC_Client.jar`.

## iPhone web client deployment

The TeaVM web client is a static web root plus a WebSocket connection to the normal game server. Build and stage it from a clean target:

```bash
scripts/package-web-teavm.sh
```

For local regression coverage before uploading, run the aggregate iPhone web preflight against a running local server. It writes `tmp/web-teavm-iphone-release-preflight/summary.json`, aggregating prerequisite checks for free disk, Chrome, and `playwright-core`; the synthetic controls smoke; local real-login smoke; local HTTPS/same-host WSS smoke; static package staging; local package deployment verification with build-manifest matching plus deep manifest asset checks; and iPhone Simulator Safari orientation-matrix artifacts. Pass `--package-dir dist/web-teavm` for release candidates so the preflight creates and verifies the exact static directory that will be uploaded. If the web package is built with a temporary `--client-version N`, do not use `--no-build` for the aggregate preflight; pass the same `--client-version N` and make sure the local server's `client_version` also equals `N`, or the local login smokes will fail before gameplay. The final release audit requires the simulator step plus `simulator-run.json`, screenshot-check JSON, and HTTP asset-check JSON, so use `--with-simulator` for release preflight and rerun old preflights created before that metadata existed. Non-video runs use the normal 512 MB free-disk floor; `--simulator-video <seconds>` adds an estimated recording budget before any browser work starts, and the documented 90-second UI/control pass needs about 1.6 GB free. Run `scripts/clean-web-teavm-iphone-artifacts.sh` first to dry-run generated-artifact cleanup, then rerun with `--apply` when the candidates are acceptable. The main smoke covers mobile login portal handoff, real login through the mobile keyboard bridge, first-login dialog-safe overlay state, post-login Android-parity canvas HUD, keyboard/chat, tap-to-move, Back and automated external-keyboard Escape routing, context action, camera pad/drag/pinch paths with shared camera/zoom state proof, shared canvas top-tab panel access, shared canvas chat-tab access, hidden redundant DOM drawer/chat tray controls, Safari keyboard/context input scaffolding, and portrait-to-landscape-to-portrait framebuffer resizing:

```bash
scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir dist/web-teavm
scripts/check-web-teavm-iphone-release.sh --client-version <N> --with-simulator --package-dir dist/web-teavm  # when testing a package override against a local server that also expects N
scripts/smoke-web-teavm-iphone-controls.sh --no-build
scripts/smoke-web-teavm-iphone.sh --no-build
scripts/smoke-web-teavm-iphone-https-wss.sh --no-build
```

Use the individual commands when iterating on one failing area. The controls smoke is the fast layout/input guard: it covers small, standard, and large portrait viewports plus short and wide landscape viewports, dialog-safe blocking-dialog overlay placement, safe portal URL resolution/rejection, beforeinput/paste/composition keyboard normalization, normal/sloppy taps, timer-based long-press context actions, and the other no-login mobile input paths before the slower real-login smoke runs. The local HTTPS/WSS smoke uses a self-signed certificate and Chrome's local ignore path; it is not proof that the production certificate/proxy is correct.

For hands-on iOS Safari layout iteration on this Mac, run the simulator helper after the web build exists:

```bash
scripts/run-web-teavm-iphone-simulator.sh --no-build
```

It boots an available iPhone Simulator, opens Safari to the local TeaVM client, captures screenshots, writes `simulator-run.md`, machine-readable `simulator-run.json`, `simulator-home-screen-checklist.md`, `simulator-screenshot-checks.json`, and local-mode `simulator-http-checks.json` under `tmp/web-teavm-simulator/`, and keeps the static server alive for reload/tuning until Ctrl-C. Use `--diag` for the diagnostics panel, `--orientation-matrix` for portrait, raw Landscape Right, and upright Landscape Right captures, `--record-video --manual-seconds 90` for a timed hands-on Mobile Safari session saved as `simulator-session.mov` with `simulator-video-checks.json`, `--pasteboard "text"` to preload text for keyboard/paste testing, `--device` or `--udid` for a specific simulator, and `--exit-after-open` for a one-shot capture. Screenshot captures pin the Simulator status bar to `9:41`, Wi-Fi, and 100% charged for stable UI review artifacts, then clear the override on exit; use `--no-stable-status-bar` when you need the live Simulator status bar. The run JSON proves the release preflight used local-mode Simulator Safari with diagnostics, screenshots, orientation matrix, and stable status bar enabled; the screenshot check file proves the captured PNGs have real rendered content rather than a blank/stuck Safari frame, the video check proves the `.mov` artifact exists and is not empty, and the HTTP check proves Simulator Safari requested the TeaVM JS, manifest, core cache archives, and Voidscape login art. The generated Home Screen checklist gives player-view visual QA steps for normal runs and stored-diagnostics/Home Screen iteration steps only for `--diag` runs. The captured screenshot surface is Mobile Safari browser, so Safari chrome/tabs can appear; judge the game viewport and controls, not standalone chrome. This is the fast Safari-feel preflight; physical iPhone/Home Screen QA is still required before release because the simulator does not prove real-device performance, touch feel, or standalone behavior.

Upload or sync `dist/web-teavm/` to the HTTPS site that should host the iPhone client. The package intentionally excludes local runtime cache files such as `accounts.txt`, `credentials.txt`, `uid.dat`, `ip.txt`, `port.txt`, and `config.txt`, omits TeaVM source maps/debug files unless `--include-debug` is passed, and normalizes packaged directories to mode `755` plus files to mode `644` so all manifest-listed cache files are readable by the static host.

Use `docs/recipes/deploy-iphone-web-client.md` for copy-ready Caddy and Nginx HTTPS/WSS examples.

Recommended production shape:

```text
https://<host>/        -> static dist/web-teavm/
wss://<host>/          -> reverse proxy to the server ws_server_port
```

For the intended beta URL, `https://voidscape.gg/play/`, serve the same static package under `/play/` and use a path-specific WSS endpoint such as `wss://voidscape.gg/play/ws/`. Keep the trailing slash when running verifier and QA helpers:

```bash
scripts/verify-web-teavm-deployment.sh https://voidscape.gg/play/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://voidscape.gg/play/ws/ --portal https://voidscape.gg/ --smoke --user <qa-user> --pass <qa-pass>
scripts/run-web-teavm-iphone-qa.sh --base-url https://voidscape.gg/play/ --ws wss://voidscape.gg/play/ws/ --portal https://voidscape.gg/
```

For same-host HTTPS/WSS, the client defaults to `wss://<host>:443/`. For a separate WebSocket endpoint, launch with query parameters:

```text
https://<host>/index.html?mobile=1&host=<ws-host>&port=<ws-port>
https://<host>/index.html?mobile=1&ws=wss://<ws-host>/<path>
```

Runtime layout mode is resolved separately from the server endpoint. Use explicit mode overrides when testing device-specific behavior: `?phone=1` or legacy `?mobile=1` forces the iPhone/mobile Safari shell, `?desktop=1` or `?mobile=0` forces desktop behavior, and `?tablet=1` forces the current deliberate tablet branch. Without an override, the page chooses `desktop`, `phone`, or `tablet` from user agent, pointer type, touch support, and viewport size. Copied diagnostics expose the decision under `runtimeMode`; only `phone` should show the phone-only `Aa` helper and portrait side `Inv` / `Mag` / `Pray` shortcuts by default.

Desktop and tablet modes should show the surrounding `/play` page shell instead of the phone-only Safari rail. The shell labels the resolved runtime mode and exposes only `Normal` and `GFX off` buttons wired to the existing web-only render modes. `GFX off` stops normal canvas uploads and shows the existing overlay, and `Normal` resumes full uploads; neither mode pauses networking, input, diagnostics, or the shared Java game loop. Current phone mode exposes the same two controls as compact in-game buttons near the bottom-right account area only after gameplay starts, while hiding them on login/non-game screens. Legacy `low-resource` requests should normalize to `normal`.

For phone-mode QA, portrait should keep the Android-like 512-wide game view, while landscape should fill Mobile Safari width with a wider shared framebuffer at classic full height `346`, not leave black side gutters. In plain gameplay with no menu, modal, map, or panel open, tapping `Aa` should focus public chat through the shared Java chat entry; when a menu/modal/panel is open, `Aa` should remain only the keyboard focus helper for that active shared state. The portrait side `Inv`, `Mag`, and `Pray` buttons should open the shared panels without opening a second DOM renderer, should stay visually connected to the side-opened panel, and should be hidden in phone landscape because the top tabs are close enough there. Bestiary/Loot stays on the shared Java player-info path, not a phone-only side button. Side-opened Magic/Prayer should use the compact mobile list height with larger text and short `Lv` labels instead of exposing a tall 20-row spell/prayer list; the normal top-tab path can keep the fuller shared-panel layout.

Phone landscape should also use loose chat text instead of the large framed portrait chat-history box. Copied diagnostics should show `ui.phoneLandscapeLooseChat: true` in phone landscape and `false` after rotating back to portrait. Keep the shared bottom chat tabs; only the landscape history frame/background is suppressed.

The packaged web client `CLIENT_VERSION` must match the live server's expected `client_version` unless the server is deployed at the same time. A static upload can verify successfully while login still fails with "Voidscape has been updated" if the web package is built with a newer client version than the running server. Always follow hosted static/deep verification with at least a focused hosted login/chat smoke before telling testers to retry.

Explicit `?host=&port=` and `?ws=` endpoint choices are saved in browser storage so iPhone Home Screen launches from the manifest `?mobile=1` start URL keep using the intended game server. Use `?endpoint=reset` or `?resetEndpoint=1` to clear the saved endpoint when moving a tester between environments. In Safari dev tools, `window.__voidscapeEndpoint` shows whether the current endpoint came from `query`, `stored`, or `default`, and `window.__voidscapeEffectiveWebSocketUrl` shows the actual socket URL.

The mobile login `Create Account` and `Recover account` buttons are also runtime-configured. Use a generic portal URL when both flows live on the same portal host:

```text
https://<host>/index.html?mobile=1&portal=https://<portal-host>/
```

That derives `https://<portal-host>/#account` and `https://<portal-host>/#security`. Use `portalAccountUrl` / `accountUrl` and `portalRecoveryUrl` / `recoveryUrl` for explicit flow URLs. Query-configured portal URLs are saved for Home Screen launches; clear them with `?resetPortal=1` or `?portalReset=1`. Copied diagnostics include `snapshot.portal`, and the handoff uses current-tab navigation so Mobile Safari does not block it as a popup.

For real-device troubleshooting without Safari dev tools, append `&diag=1` or `&debug=1` to the launch URL. The `i` toggle opens a live diagnostics panel with endpoint, effective WebSocket URL, standalone mode, viewport, canvas framebuffer/CSS size, keyboard freeze state, lifecycle resume counters, input hints, current and portrait/landscape-history hit-tested mobile overlay control rectangles, shared-client local player tile/camera state, shared UI/custom HUD state including `blockingDialog` / `blockingDialogName`, bounded custom HUD `uiHistory`, bounded scroll-routing `scrollHistory`, and recent JavaScript/console/resource errors. The `copy` button copies the same report as JSON, and if Safari blocks clipboard access it changes to `select` and opens a selectable JSON field instead. In-game overlay controls should stay reachable, hit-tested, 44px-class where they are buttons, and clear of the top HUD, bottom HUD, and blocking game dialogs in both portrait and landscape before final copy. Final iPhone QA should tap the custom HUD `All`, `Chat`, `Quest`, and `Private` chat tabs, open every top HUD panel, swipe-scroll an opened top panel, close a panel, then copy diagnostics so `uiHistory` and `scrollHistory` record those interactions. The same data is available from `window.__voidscapeCollectDiagnostics()`.

The game server must have `want_feature_websockets: true`. Either terminate TLS at a reverse proxy and forward to `ws_server_port`, or configure `ssl_server_cert_path` / `ssl_server_key_path` in `server/connections.conf` so the Netty WebSocket port can serve WSS directly. Before sharing the link, verify the hosted static root:

```bash
scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest
```

That command checks required files, the deployed `voidscape-web-build.json`, current iPhone web-client hooks including copied diagnostics, `controlsHistory`, custom-HUD `uiHistory`, scroll-routing `scrollHistory`, and mobile endpoint state, iPhone/PWA markers, icon validity, and rejects exposed runtime cache or TeaVM debug files. With `--expected-build-manifest`, it proves the hosted build manifest matches the local package manifest; with `--deep-manifest`, it fetches every manifest-listed file and verifies each size/SHA-256. Then run the Chrome iPhone-emulation smoke against the uploaded page through the same verifier. Use the first form for same-host WSS; use the second for an explicit WebSocket proxy path:

Production serves packaged `Cache/` assets with `Cache-Control: public, max-age=31536000, immutable`. `scripts/package-web-teavm.sh` stamps `index.html` with an `assetToken` derived from packaged `Cache/` contents, and the TeaVM web port appends that token to `Cache/` resource URLs at runtime. Keep that path intact when shipping cache/sprite changes; otherwise browsers can keep an old `Authentic_Sprites.orsc` and miss newly deployed projectile or sprite assets even though the server file is current.

```bash
scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --portal https://<portal-host>/ --smoke --user <qa-user> --pass <qa-pass>
scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://<host>/<path> --portal https://<portal-host>/ --smoke --user <qa-user> --pass <qa-pass>
```

Create the deployed real-device QA report before testing on Mobile Safari:

```bash
scripts/run-web-teavm-iphone-qa.sh --base-url https://<host>/ --portal https://<portal-host>/
scripts/run-web-teavm-iphone-qa.sh --base-url https://<host>/ --ws wss://<host>/<path> --portal https://<portal-host>/
```

Use the first form for same-host WSS, and the second for an explicit WebSocket proxy path or hostname. Paste the verifier `summary.json` from the matching `--smoke` run into the report's `Deployment Verification` section; it should include `smokeRan: true`, `smokePassed: true`, `allowHttp: false`, `insecureTls: false`, `allowDebug: false`, `allowInsecureWs: false`, `buildManifestSha256` from the hosted `voidscape-web-build.json`, `buildManifestMatchesExpected: true`, `deepManifestChecked: true`, matching `deepManifestVerifiedCount` / `deepManifestFileCount`, `deepManifestFailureCount: 0`, `cachePolicyChecked: true`, and `cachePolicyFailureCount: 0`. Fill the report's physical-device fields with the iPhone model, iOS version, network, tester, Home Screen mode, and external-keyboard yes/no result. Then smoke-test on real Mobile Safari: open `&diag=1` once so the endpoint, portal URLs, and diagnostics mode are saved, add to Home Screen, launch from the manifest/Home Screen URL without `diag=1`, and confirm saved endpoint launches still connect after query parameters are absent. Tap `Create Account` and `Recover account` from the mobile login screen once before final player testing to verify they reach the intended portal flows, then return to the client. Log in by entering username/password through the iPhone keyboard/`Aa` path, tap out of the username field and tap the password field to confirm username text persists, copy the blocking-dialog diagnostics while the welcome/wilderness modal is still open, confirm diagnostics report `diagnostics.source: "stored"` and the modal text/close button are not covered by web controls, then close those dialogs and continue. Background the Home Screen app and return to it, rotate through portrait and landscape before copying final diagnostics from the resumed Home Screen launch into the report, and confirm the reported `standalone`, stored diagnostics state, endpoint/portal/viewport/canvas/client tile/camera values, `lifecycle.resumeCount`, `controlsHistory.portrait`, `controlsHistory.landscape`, `ui.customUi`, `uiHistory`, `scrollHistory`, and dialog-state fields match the device. In landscape, confirm the game fills the sideways viewport instead of retaining side gutters and that side `Inv`/`Mag`/`Pray` are hidden. Move, type and paste chat with the `Aa` keyboard button, including a plain-gameplay `Aa` tap that should enter public chat directly, confirm keyboard open/close does not resize or bounce the game framebuffer during dismissal, use browser Back/Home Screen navigation to close shared mobile states before leaving the page, open a context menu with long-press, select menu rows after small finger drift, and verify NPC/rift option menus appear above the chat box rather than near the top plaque. Rotate/zoom with one-finger drags and the camera pad, pinch to zoom, use side `Inv`, `Mag`, and `Pray` in portrait to open the connected shared panels, confirm side-opened Magic/Prayer show a compact default-height list with larger text rather than a tall 20-row list, confirm a selected spell gives an intuitive tap-target flow, confirm no side `Best` button appears, open Bestiary/Loot through the shared Java player-info/top-panel path, swipe-scroll shared HUD/bank/settings/spell/friends/chat panels where available, confirm the compact phone `Normal`/`GFX off` buttons appear in-game but not on login, confirm the FPS overlay also shows current/max Hits and Prayer, confirm the Voidscape HUD skin loads, every shared canvas top tab opens its real shared panel, the shared canvas chat tabs switch `All` / `Chat` / `Quest` / `Private`, and the browser-only `Aa` plus side shortcuts remain input scaffolding into shared panels rather than a second HUD. If a paired physical keyboard is available, also press Escape once in-game and record `External keyboard tested: yes` only when it behaves like mobile Back; otherwise record `no`. Validate the filled report with:

```bash
scripts/validate-web-teavm-iphone-qa-report.py tmp/iphone-web-qa/iphone-safari-qa-report.md
```

Then run the final release audit so the local preflight prerequisites, controls, broad login, focused world-map/walker, focused chat, HTTPS/WSS, package artifact, package manifest, every manifest-listed local package file, hosted verifier summary pasted into the report, and physical iPhone QA report are checked together:

```bash
scripts/check-web-teavm-iphone-final-release.py --qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --package-dir dist/web-teavm
```

For releases with iPhone UI/control changes, run the local preflight with `--simulator-video <seconds>` and add `--require-simulator-video` to that final audit command so the hands-on Simulator recording sanity check is enforced before physical-device signoff. The audit requires at least 90 manual video seconds by default.

For local real-device QA before upload, use the LAN QA runner. It prints the iPhone URLs and writes a markdown report template for copied diagnostics, or selected diagnostics JSON when clipboard is blocked, plus checklist results:

```bash
scripts/run-web-teavm-iphone-qa.sh --no-build
scripts/validate-web-teavm-iphone-qa-report.py tmp/iphone-web-qa/iphone-safari-qa-report.md --allow-no-deployment-verification
```

For the short physical-device walkthrough, use `docs/recipes/test-iphone-web-client-local.md`.

## Server updates

When shipping a beta server update, deploy the runtime and migrations as one unit:

- Back up the active database before changing files.
- Sync `server/core.jar`, `server/plugins.jar`, `server/conf/`, and `server/database/`.
- Preserve runtime state such as `server/inc/sqlite/*.db`, `server/logs/`, `server/avatars/`, and `server/local.conf`.
- Set the deployed `server/local.conf` `client_version` to match `Client_Base/src/orsc/Config.java` before restart.
- Restart the server and watch the runner log until database patches finish and the game port opens.
- Verify both the public game port and the launcher manifest before sending testers back in.

## Config files

| File | Purpose | Git status |
|---|---|---|
| `server/local.conf` | Active local server preset | Ignored |
| `server/connections.conf` | DB backend and credentials | Tracked scaffold; review before deploy |
| `Client_Base/src/orsc/Config.java` | Client compile-time flags and `CLIENT_VERSION` | Tracked |
| `Client_Base/Cache/ip.txt`, `port.txt` | Per-developer client target | Ignored |
| `Client_Base/Cache/hideIp.txt` | Per-developer client UI preference | Ignored |

When client-visible definitions, packets, cache files, or login expectations change, bump `CLIENT_VERSION` in `Client_Base/src/orsc/Config.java` and set the matching server `client_version`.

## Database operations

### Local SQLite reset

```bash
scripts/reset-db.sh
```

This is destructive and intended for development.

### SQLite backup

Stop the server first or use SQLite's backup command:

```bash
sqlite3 server/inc/sqlite/voidscape.db ".backup 'Backups/voidscape-$(date +%Y%m%d-%H%M%S).sqlite'"
```

Do not commit backup files.

### MariaDB backup

Use the real database name and credentials for the deployed environment:

```bash
mysqldump -h <host> -u <user> -p <database> > "Backups/voidscape-$(date +%Y%m%d-%H%M%S).sql"
```

Restore only after stopping the game server:

```bash
mysql -h <host> -u <user> -p <database> < Backups/<backup>.sql
```

### Migrations

Server boot runs `JDBCPatchApplier`. Before a player-facing update:

- Back up the DB.
- Boot staging once against a copy.
- Confirm login, save, logout, and relogin.
- Confirm feature tables that came from addons or patches exist.

## Logs and telemetry

Server logs live under `server/logs/` and should remain untracked.

Performance helpers:

```bash
scripts/perf-watch.sh
scripts/perf-smoke.sh
scripts/perf-load.sh 50 130 18 2
```

Watch first for:

- skipped ticks
- sustained high tick `p95`
- save or SQL queue pressure
- packet queue pressure
- repeated plugin or definition errors

Packet capture is expensive and noisy. Keep `want_pcap_logging: false` unless actively debugging networking.

## Client and cache updates

PC client:

```bash
scripts/build.sh
```

The build produces `Client_Base/Open_RSC_Client.jar`, which is a build artifact and should stay untracked.

Android client:

```bash
scripts/build-android.sh
```

Android requires a local SDK and JDK 17. See `docs/DEVELOPMENT.md`.

Cache-backed changes need extra care:

- Client and server `Custom_Landscape.orsc` copies must both be regenerated when the landscape patcher changes.
- World-map overlays should be re-baked after custom map changes.
- `Client_Base/Cache/MD5.SUM` should be updated only when intentionally shipping cache changes.
- Client-visible definition changes usually require `CLIENT_VERSION` bumps.

## Discord operations

Community setup docs:

- `docs/community/discord-server-setup.md`
- `docs/community/discord-setup-bot.md`

Access gate:

```bash
scripts/discord-access-gate.js --setup
scripts/discord-access-gate.js --serve
```

The live listener must be running for the `Enter Voidscape` button to grant the member role. Tokens must come from the environment or keychain, never from committed files.

VoidBot-authored posts:

Use `scripts/post-voidbot-discord.py` for bot-authored Discord messages. Do not type release notes or bug-feed fix summaries through a personal Chrome/Discord session.

One-time local token setup, entered manually by an operator:

```bash
security add-generic-password -a VoidBot -s voidscape-voidbot-discord-token -w '<bot-token>' -U
```

Bug-feed fix summary flow:

```bash
scripts/post-voidbot-discord.py --channel bug-feed --dry-run --stdin < /tmp/fixes.txt
scripts/post-voidbot-discord.py --channel bug-feed --yes --stdin < /tmp/fixes.txt
```

Live-host bug-feed posting can use the bug-triage service environment file:

```bash
/opt/voidscape/scripts/post-voidbot-discord.py --channel bug-feed --env-file /etc/voidscape/discord-bug-triage.env --dry-run --stdin < /tmp/fixes.txt
/opt/voidscape/scripts/post-voidbot-discord.py --channel bug-feed --env-file /etc/voidscape/discord-bug-triage.env --yes --stdin < /tmp/fixes.txt
```

The script also accepts `--content "..."` or `--file path/to/message.txt`. It reads the token from `VOIDBOT_DISCORD_TOKEN`, `PORTAL_DISCORD_BOT_TOKEN`, `DISCORD_BOT_TOKEN`, an explicit `--env-file`, or the `voidscape-voidbot-discord-token` keychain service. Mentions are disabled by default; use `--allow-mentions` only when intentionally posting announcements.

To correct a VoidBot-authored post, edit it in place:

```bash
scripts/post-voidbot-discord.py --channel bug-feed --edit-message-id <message-id> --dry-run --stdin < /tmp/fixes.txt
scripts/post-voidbot-discord.py --channel bug-feed --edit-message-id <message-id> --yes --stdin < /tmp/fixes.txt
```

## Release procedure

1. Finish and commit the code/content slice.
2. Run `docs/RELEASE-CHECKLIST.md`.
3. Build with `scripts/build.sh`.
4. Back up the database.
5. Deploy server/client/cache artifacts.
6. Boot server and watch logs.
7. Smoke-test with a real client.
8. Post release notes or a test announcement.

## Rollback procedure

1. Stop the server.
2. Restore the previous known-good code/cache/client artifacts.
3. Restore the DB backup if the failed release applied migrations or corrupted state.
4. Boot server.
5. Log in with a test account.
6. Announce status if players were affected.

Prefer a boring rollback over trying to patch live state while players are online.

## Pre-public readiness gaps

Before inviting real public traffic, settle these:

- Production config values in `docs/CONFIG-MATRIX.md`.
- MariaDB backup/restore rehearsal.
- AGPL source-disclosure plan.
- Account moderation/support workflow.
- Basic uptime monitoring.
- Crash/restart policy.
- Rules and appeals process.
- Abuse policy for automation, multi-logging, RWT, bug exploitation, and Discord conduct.
