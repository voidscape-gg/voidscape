# Release checklist

Use this before any player-facing test, public weekend, or real launch. The goal is not ceremony; it is catching avoidable drift before players do.

## Release target

- [ ] Release name / tag:
- [ ] Target audience: private test / friends test / public beta / public launch
- [ ] Server preset: local / staging / production
- [ ] Database backend: SQLite / MariaDB
- [ ] Client version:
- [ ] Build commit:
- [ ] Operator:

## Git and workspace

- [ ] `git fetch --prune origin`
- [ ] `git status -sb` shows no accidental runtime files.
- [ ] `git rev-list --left-right --count HEAD...origin/main` is `0 0`, unless intentionally releasing from a branch.
- [ ] All release changes are committed.
- [ ] `docs/DIVERGENCE.md` has entries for non-trivial Voidscape behavior changes.
- [ ] New player commands are reflected in `Commands.md`.
- [ ] New content recipes or subsystem lessons were added to `docs/recipes/` or `docs/subsystems/` when reusable.
- [ ] No secrets are in the tree: `git grep -n "sk-|DISCORD_BOT_TOKEN|password|webhook"` reviewed.

## Build

- [ ] `scripts/build.sh` passes.
- [ ] Android, if shipping APK publicly: `scripts/build-android.sh --release` passes on JDK 17 with upload signing configured; debug APKs are only for internal QA/emulator smoke.
- [ ] Generated client/server cache assets are intentionally committed when they are part of the release.
- [ ] Build artifacts remain untracked: `server/*.jar`, `Client_Base/Open_RSC_Client.jar`, `PC_Launcher/OpenRSC.jar`.

## Config

- [ ] `server/local.conf` or production config matches `docs/CONFIG-MATRIX.md`.
- [ ] `Client_Base/src/orsc/Config.java` `CLIENT_VERSION` matches server `client_version`.
- [ ] `enforce_custom_client_version: true` is enabled for any non-local release.
- [ ] `server_port` / `ws_server_port` are the intended public ports.
- [ ] `server_name`, welcome text, and Discord/community URLs are correct.
- [ ] `member_world` is intentional and documented.
- [ ] `want_beta_onboarding_guide: false` for public launch unless this is an explicitly trusted beta window.
- [ ] `want_void_colossus: false` and `want_void_dungeon: false` for launch unless those systems have a dedicated release pass.
- [ ] `production_command_lockdown: true` for public launch, then verified with owner and non-owner staff accounts.
- [ ] XP rates and hit-XP timing flags are intentional and documented.
- [ ] `restrict_item_id` allows the current custom item range.
- [ ] `want_pcap_logging` is off unless actively debugging packet capture.
- [ ] Any local-only convenience flags are removed or documented as deliberate launch behavior.

## Database

- [ ] Fresh DB import path is tested: `scripts/reset-db.sh` for dev, or the production import procedure in `docs/OPERATIONS.md`.
- [ ] Required addon tables exist: bank presets, auction house, item status fields, and any active feature tables.
- [ ] Migrations are idempotent or safe to apply once.
- [ ] Backup taken before migration or public test.
- [ ] Account creation and login tested on a clean player.
- [ ] Newly created public accounts have user rank, not Admin or another staff rank.
- [ ] Existing-account login tested after migration.
- [ ] Player save/logout/relogin tested.

## Server smoke test

- [ ] `scripts/run-server.sh` boots without fatal errors.
- [ ] Server reports the expected TCP and WebSocket ports.
- [ ] Entity definitions load without JSON/XML errors.
- [ ] Plugin handler loads without missing critical content.
- [ ] PERF telemetry, if enabled, shows zero skipped ticks at idle.
- [ ] `scripts/perf-smoke.sh` passes when a logged-in local client is available.

## Client smoke test

- [ ] `scripts/run-client.sh` connects to the intended host/port.
- [ ] Login screen renders Voidscape branding correctly.
- [ ] Portal-first account creation works and launch clients send Create Account/Recover account traffic to the account manager instead of relying on packet registration.
- [ ] Existing account login works.
- [ ] Desktop launcher visual smoke passes: `scripts/smoke-launcher-prelaunch.sh --jar <bundle>/launcher/VoidscapeLauncher-staging.jar --use-packaged-config --host <game-host> --port <game-port> --portal-url https://<portal-host> --out tmp/launcher-prelaunch`, proving the packaged jar renders and writes the intended endpoint.
- [ ] Desktop launcher update-pipeline smoke passes: `scripts/smoke-launcher-update.sh`, covering fresh install, idempotent re-run, corruption repair, prune of removed files, launcher self-update staging plus the `--relaunched` loop guard, offline fallback, and plain-http refusal against a hermetic local channel.
- [ ] Desktop Java visual smoke passes: `scripts/smoke-pc-client-prelaunch.sh --user <qa-user> --pass <qa-pass> --out tmp/pc-client-prelaunch`, with fresh-cache screenshots proving login, in-game HUD, inventory, stats, minimap, and account drawer.
- [ ] Basic movement, chat, inventory, bank, shop, combat, and logout work.
- [ ] Wrench/profile/advanced settings open and persist.
- [ ] World map opens, pans/zooms, and autowalk route returns.
- [ ] Rare-drop beams and ground-item labels respect settings.
- [ ] Void Glass bank opens, searches/clears, deposits, withdraws, closes, and loadouts save/load from mobile-sized controls with `want_custom_ui:true`, `want_custom_banks:false`, and `want_bank_presets:true`.
- [ ] Android, if shipping APK: fresh emulator install reaches `Ready to play`, `Play` connects to the intended public endpoint, and the advanced long-press server picker still supports emulator/manual testing.
- [ ] Android, if shipping APK: touch movement, long-press menu, login/chat keyboard, six-band glass chat tabs including Global, inventory, Void Glass bank scrolling/search/clear/loadouts/Close via `scripts/android-smoke.sh --only-auth-bank` (`renderer=voidGlass`), camera rotate, zoom, background/resume, logout, portrait gameplay framing, and settings-panel spacing are smoke-tested.
- [ ] Android, if shipping APK publicly: a physical-device report generated by `scripts/run-android-device-qa.sh` is filled on real Android hardware and passes `scripts/validate-android-device-qa-report.py`.
- [ ] iPhone web, if shipping: `scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir dist/web-teavm` passes against a running local server and saves `tmp/web-teavm-iphone-release-preflight/summary.json` with prerequisites, local controls, login, HTTPS/WSS, package, local package-verifier, and iPhone Simulator Safari orientation-matrix results for the exact upload package.
- [ ] iPhone web, if shipping: `scripts/smoke-web-teavm-iphone-controls.sh` passes and saves a current mobile-controls screenshot/summary, including safe portal URL resolution/rejection, small/standard/large portrait and short/wide landscape viewport coverage, dialog-safe blocking-dialog overlay placement, plus keyboard beforeinput/paste/composition coverage.
- [ ] iPhone web, if shipping: `scripts/smoke-web-teavm-iphone.sh` passes against a local server and saves current portrait/landscape diagnostics screenshots proving mobile login Create Account/Recover account portal handoff, mobile-keyboard login, first-login dialog-safe overlay state, Android-parity shared canvas top-tab panel access plus copied `uiHistory` proof, shared six-band canvas chat-tab access including Global with no normal framed chat-history container, hidden redundant DOM drawer/chat tray controls, keyboard-first browser Back behavior, focused world-map-search Back behavior, automated external-keyboard Escape routing, context action, camera pad/drag/pinch shared camera state changes, real shared-panel swipe-scroll routing plus copied `scrollHistory` proof, compact Safari `...`/`Aa` helpers only as input scaffolding, orientation resizing, lifecycle resume viewport refresh, and post-resume input plus movement proof in `postResumeProof`.
- [ ] iPhone web, if shipping: `WEB_TEA_SMOKE_AUTH_DB=server/inc/sqlite/preservation.db scripts/smoke-web-teavm-iphone.sh --no-build --only-bank --user <qa-user> --pass <qa-pass> --out tmp/mobile-qc-web-bank` passes against a local server, opens the shared bank on phone `/play`, reports `ui.bankOpen: true`, `ui.bankRenderer: "voidGlass"`, and live `ui.bank` geometry while open, proves touch Close/search/clear/scroll/deposit/withdraw/loadout behavior, and restores its bank fixture; physical iPhone QA should still cover Home Screen touch feel.
- [ ] iPhone web, if shipping: `scripts/smoke-web-teavm-iphone-https-wss.sh` passes against a local server and proves same-host HTTPS/WSS default login.
- [ ] iPhone web, if shipping: `scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --orientation-matrix --exit-after-open` opens the current TeaVM client in iOS Simulator Safari and saves fresh portrait/raw-landscape/upright-landscape screenshots with a stable `9:41`/Wi-Fi/100% status bar, `simulator-run.md`, current `simulator-run.json` proving local-mode diagnostics/orientation-matrix capture, `simulator-home-screen-checklist.md`, passing `simulator-screenshot-checks.json`, and passing local-mode `simulator-http-checks.json` proving Simulator Safari requested the TeaVM JS, manifest, core cache archives, and Voidscape login art.
- [ ] iPhone web, if shipping UI/control changes: `scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --record-video --manual-seconds 90` records a hands-on Mobile Safari session as `simulator-session.mov`, with passing `simulator-video-checks.json`, for keyboard, touch controls, dialog placement, orientation, and custom HUD feel before the physical iPhone pass; the aggregate `--simulator-video 90` preflight needs about 1.6 GB free by default.
- [ ] iPhone web, if shipping UI/control changes and disk is tight: `scripts/clean-web-teavm-iphone-artifacts.sh` is reviewed in dry-run mode, then rerun with `--apply` only for acceptable generated artifacts before `--simulator-video 90`.
- [ ] iPhone web, if shipping: `scripts/package-web-teavm.sh` produces the static web root, writes `voidscape-web-build.json`, and excludes runtime cache files (`accounts.txt`, `credentials.txt`, `uid.dat`, `ip.txt`, `port.txt`, `config.txt`).
- [ ] iPhone web, if shipping: `scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest` passes against the uploaded HTTPS static root, proving required assets, hosted build-manifest hash/size checks, `buildManifestMatchesExpected: true`, `deepManifestChecked: true`, `deepManifestVerifiedCount == deepManifestFileCount`, `cachePolicyChecked: true`, `cachePolicyFailureCount: 0`, expected iPhone web-client hooks including copied diagnostics, `controlsHistory`, custom-HUD `uiHistory`, scroll-routing `scrollHistory`, `postResumeProof`, mobile endpoint state, icon validity, and no exposed runtime/debug files.
- [ ] iPhone web, if shipping: reverse proxy follows `docs/recipes/deploy-iphone-web-client.md` or an equivalent HTTPS/WSS setup, with `ws_server_port` not publicly exposed unless intentionally serving WSS directly.
- [ ] iPhone web, if shipping: production launch URL configures the intended account and recovery portal flows with `portal`, `portalAccountUrl`, or `portalRecoveryUrl`; copied diagnostics show the expected `snapshot.portal`, and `resetPortal=1` clears stale tester portal URLs.
- [ ] iPhone web, if shipping: `scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --smoke --user <qa-user> --pass <qa-pass>` or `scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://<host>/<path> --smoke --user <qa-user> --pass <qa-pass>` passes against the uploaded HTTPS/WSS deployment, and its `summary.json` records `smokeRan: true`, `smokePassed: true`, `allowHttp: false`, `insecureTls: false`, `allowDebug: false`, and `allowInsecureWs: false`.
- [ ] iPhone web, if shipping: WSS connects from Safari without mixed-content errors, using same-host `wss://<host>:443/` or explicit `?host=&port=` / `?ws=` settings; Home Screen relaunch with only `?mobile=1` preserves the saved endpoint, and `?endpoint=reset` clears it.
- [ ] iPhone web, if shipping: blocking-dialog diagnostics copied from a real Home Screen launch while the welcome/wilderness modal is open report an `href` without `diag=1` / `debug=1`, `diagnostics.source: "stored"`, `bodyClass` containing `dialog-open`, `ui.blockingDialog: true`, non-empty `ui.blockingDialogName`, hidden camera controls, and reachable 44px-class `Aa`/`...`/diagnostics controls that do not cover the modal text or close button.
- [ ] iPhone web, if shipping: final diagnostics copied from a real Home Screen launch after tapping the custom HUD `All` / `Chat` / `Quest` / `Global` / `Private` chat tabs, opening every top HUD panel, swipe-scrolling an opened top HUD panel, closing an opened panel, background/resume, post-resume tap-to-move or chat, and a portrait/landscape rotation pass report an `href` without `diag=1` / `debug=1`, `diagnostics.source: "stored"`, the intended endpoint, effective WebSocket URL, portal account/recovery URLs, `standalone: true`, `lifecycle.resumeCount > 0`, `postResumeProof.inputAfterResume: true`, `postResumeProof.movementAfterResume: true`, viewport, canvas framebuffer/CSS size, keyboard state, input hints, current hit-tested mobile overlay control rectangles with 44px-class action/diagnostics targets, `controlsHistory.portrait`, `controlsHistory.landscape`, shared-client local player tile/camera state, shared UI/custom HUD state including boolean `blockingDialog` and string `blockingDialogName`, `uiHistory` with `ALL`, `CHAT`, `QUEST`, `GLOBAL`, `PRIVATE`, top HUD `showUiTab` ids `1` through `6`, a later `showUiTab: 0`, `scrollHistory` with a nonzero scrollable top HUD panel gesture, and no unexpected recent JavaScript/console/resource errors; the diagnostics `copy` button produces a shareable JSON report or, when clipboard is blocked, opens a selectable JSON report, and the overlay controls remain reachable without covering the top HUD or bottom HUD.
- [ ] iPhone web, if shipping: `scripts/run-web-teavm-iphone-qa.sh --base-url https://<host>/ --ws wss://<host>/<path> --portal https://<portal-host>/ --deployment-summary <hosted-verifier-summary.json>` has produced a filled `iphone-safari-qa-report.md` from a real iPhone Safari/Home Screen run, the report fills iPhone model, iOS version, network, tester, Home Screen mode, and external-keyboard yes/no fields, includes the matching `verify-web-teavm-deployment.sh --smoke` `summary.json` with `smokeRan: true`, `smokePassed: true`, and all production verifier flags false in its `Deployment Verification` section, includes checked real-device world-map pinch/no-walk proof plus pasted `World Map Diagnostics` while the map is open showing near-full-canvas geometry and successful route diagnostics, and `scripts/validate-web-teavm-iphone-qa-report.py tmp/iphone-web-qa/iphone-safari-qa-report.md` passes without development bypass flags.
- [ ] iPhone web, if shipping: `scripts/check-web-teavm-iphone-final-release.py --qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --package-dir dist/web-teavm` passes, tying the local preflight prerequisites, controls, broad login, focused world-map/walker, focused chat, HTTPS/WSS, package artifact, uploaded package manifest and local package file hashes, hosted verifier summary, simulator run metadata/screenshots/HTTP checks, and physical iPhone QA report into one final release audit; add `--require-simulator-video` when shipping UI/control changes so the audit requires the documented 90-second simulator video session.
- [ ] iPhone web, if shipping: real Mobile Safari Create Account/Recover account portal handoff, login through the iPhone keyboard/`Aa` path, first-login/wilderness modal dialogs not covered by web controls, movement, chat keyboard, keyboard open/close viewport stability, public-chat browser Back closes only the keyboard first, the next browser Back reaches shared mobile Back, focused world-map-search browser Back closes search while leaving the map open, optional external-keyboard Escape with a paired physical keyboard if available, paste/composition/autocorrect input, long-press/`...` context actions and opened-menu row selection, one-finger camera rotate/zoom, camera pad, pinch zoom, world-map pinch zoom without accidental world-walk, swipe scrolling for bank/settings/spell/friends/chat panels, custom HUD chat-tab and all top-panel touch selection, web overlay controls not covering the top or bottom HUD, Home Screen launch, background/resume, post-resume gameplay input/movement, and orientation/viewport behavior are smoke-tested.

## Content smoke test

- [ ] Void Island starter path can be chosen once, grants the matching starter kit once, and shows the post-choice welcome box.
- [ ] Home teleport works and respects wilderness restrictions.
- [ ] Ordinary Void Rifts route only between the approved hubs: Void Enclave, Edgeville, Varrock, Falador, and Ardougne.
- [ ] Starter-area Void Rift at `139 636` shows the same approved hub list and does not offer Lumbridge.
- [ ] Void Arena can be entered only from the Void Enclave rift, and `::arena enter` tells players to use that rift instead of teleporting them.
- [ ] World-map saved slots persist per account locally and use auto-walk, not teleport.
- [ ] Void Enclave safe zone blocks PvP and amenities work: bank, altar, healing pool, waystones, store.
- [ ] Release-disabled content remains closed: Void Dungeon entrance, Void Colossus entrance, Void Knight ladder, Void Rush, and Undead Siege all deny public entry.
- [ ] Void chest requires a Void Key and rolls rewards.
- [ ] Auction House opens, lists, buys, cancels, collects, and handles expiry.
- [ ] Player titles command opens and active title appears in chat.
- [ ] Karamja Fishmonger notes supported raw/cooked fish without cooking them.
- [ ] Desktop, Android, and web clients send Create Account/recovery traffic to the portal-first account manager; packet registration remains disabled unless explicitly enabled for a private beta/dev build.
- [ ] Lumbridge Subscription Vendor grants one reserved starter subscription card per linked account, does not open a shop, marks the portal reward as claimed, and redeemed cards apply the intended account-wide XP rates.
- [ ] PK Catching Simulator starts, scores, `::leave` exits, and highscores persist.
- [ ] Void Rush starts with bots, eliminates players, rewards one winner, and cleans up.
- [ ] Dragon sword components drop/source/assemble as intended.
- [ ] Custom items render in inventory and when wielded.
- [ ] Launch command audit spot-check: non-owner staff cannot run `::item`, `::noteditem`, `::spawnnpc`, `::setstats`, `::goto`, `::loadbots`, or `::workbenchauctionfixture`; regular players cannot run `::beta`, `::farmkit`, `::farmsim`, `::farmcal`, `::codes`, or `::refcodes`; owner can still run break-glass commands.

## Performance

- [ ] `scripts/perf-watch.sh` shows clean telemetry during local smoke.
- [ ] Synthetic load test run at the expected scale: `scripts/perf-load.sh <count> <duration> <radius> <intervalTicks>`.
- [ ] No repeatable skipped ticks or sustained tick `p95` over the threshold chosen for the test.
- [ ] Logs do not show save queue, SQL queue, or packet queue pressure under expected load.

## Community and launch surface

- [ ] Discord access gate listener is running if using the announcement gate.
- [ ] `docs/community/discord-server-setup.md` permissions still match the live server.
- [ ] Release machine has enough free space for launch bundles, logs, and screenshots; the aggregate runner defaults to a 512 MiB floor before writing evidence.
- [ ] Non-device prelaunch readiness report passes and is archived:
  `scripts/check-prelaunch-readiness.sh --out tmp/prelaunch-readiness --host <game-host> --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws-url wss://<portal-host>/play/ws/`
- [ ] Final prelaunch readiness report, after physical-device QA is available, includes pass rows for device evidence:
  `scripts/check-prelaunch-readiness.sh --out tmp/prelaunch-readiness-final --host <game-host> --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws-url wss://<portal-host>/play/ws/ --android-device-report tmp/android-device-qa-current/android-device-qa-report.md --iphone-qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md --iphone-local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --iphone-package-dir dist/web-teavm`
- [ ] Visual game/client evidence board is generated and reviewed:
  `scripts/build-prelaunch-visual-board.mjs --out tmp/prelaunch-visual-board`
- [ ] Launch staging bundle is generated with the target host URLs:
  `scripts/package-launch-staging.sh --host <game-host> --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws-url wss://<portal-host>/play/ws/`
- [ ] Generated `RELEASE-HANDOFF.md` from the launch-staging bundle is filled with actual backup paths, rollback owner, and AGPL/source-disclosure confirmation, then archived with `MANIFEST.txt` and verifier output.
- [ ] If Android is included in the public bundle, launch staging is generated with `--android-release`; the bundle `MANIFEST.txt` records `android_apk_type=release`.
- [ ] Android public-channel decision is recorded. Current direction is to restore the APK to the post-launch download chooser; production should prefer a release-signed APK and physical Android QA before broad promotion.
- [ ] Public Android APK channel, if chosen, has a passing physical Android QA report archived with the release handoff.
- [ ] If Android APK links are public, `/api/public` shows the Android row with a SHA-256 hash and `/downloads/android-apk` returns the APK. Set `PORTAL_ANDROID_APK` when the public APK lives outside the default build output.
- [ ] Prelaunch portal signup is backed by production persistence, creates linked game characters, and disables/redirects client packet registration.
- [ ] Public `/api/health.config.publicReady` is `true`, `abuseHashSaltConfigured` is `true`, and `issues` is empty; endpoint output exposes only booleans, never secret values.
- [ ] Hosted launch staging verifier passes against the deployed portal, web client, WSS endpoint, and deployed server config:
  `scripts/verify-launch-staging.mjs --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws wss://<portal-host>/play/ws/ --server-config <deployed-server.conf> --run-signup`
- [ ] Recovery-code password reset is tested, old sessions are revoked, and support knows the fallback path for players without codes.
- [ ] Starter-card abuse controls have a stable hash salt, tuned IP bucket limit, and staff review/grant process.
- [ ] Staff account tools are protected by production identity/RBAC, not the local `PORTAL_ADMIN_TOKEN` prototype guard.
- [ ] Google OAuth and subscription-card payment checkout/webhooks are wired or intentionally disabled with clear player-facing copy.
- [ ] Public links, invite URLs, support instructions, and rules are current.
- [ ] AGPL source-disclosure plan is ready before any public distribution; `/`, `/privacy`, `/data-deletion`, `/transparency`, and `/api/integrity` point at the Voidscape source mirror rather than only upstream OpenRSC.

## Rollback

- [ ] Previous known-good commit/tag identified.
- [ ] Database backup path verified.
- [ ] Cache/client rollback path verified (previous `update/` dir kept by the rsync-to-temp+mv publish step in `docs/OPERATIONS.md`).
- [ ] Discord/community announcement draft ready if a public test is interrupted.

## Sign-off

- [ ] Build verified.
- [ ] Server verified.
- [ ] Client verified.
- [ ] Content smoke verified.
- [ ] Backup verified.
- [ ] Release notes posted or queued.
