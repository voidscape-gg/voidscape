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
- [x] Slice 8D release source is a credential-clean snapshot on the public-mirror lineage: private development ancestry, root `.env`, runtime SQLite databases, agent/developer memory, private deployment metadata, internal reports, and the omitted portable JDK binary are unreachable from the release branch; high-confidence source and package scans pass.
- [x] The historically tracked root `.env` is byte-identical to the frozen upstream OpenRSC scaffold and contains human-readable local placeholders, not a live secret. Production must never use its default MariaDB values.
- [ ] Before any public branch push, revoke or rotate the old Discord webhook and retire or reconstruct obsolete remote feature refs that expose private development history. Never push the private WIP ref.
- [ ] Confirm fixed QA identities such as `wbtest` and `qabot*` do not exist in the production database; their development-only passwords are intentionally visible in test scripts.

## Build

- [x] `scripts/build.sh` passes from the clean Slice 8D public-source checkout without a root `.env`; the canonical Makefile now treats that local credential file as optional for compile-only workflows.
- [ ] Android, if shipping APK publicly: `scripts/build-android.sh --release` passes on JDK 17 with upload signing configured; debug APKs are only for internal QA/emulator smoke.
- [x] Slice 8D Google Play artifact gate: `scripts/build-android.sh --play-release` and `scripts/check-android-play-release.sh --aab "Android_Client/Open RSC Android Client/build/outputs/bundle/release/voidscape.aab" --server-config server/voidscape-launch.conf --current-play-version-code 8 --expected-signer-sha256 3B:AE:B2:F4:6D:68:55:DD:9E:21:DA:27:80:8C:02:90:B1:32:9F:07:27:23:F4:39:DC:A2:07:A4:0A:14:2E:D7` pass for a clean-public-history, signed, provenance-bound `9 / 1.0.8` bundle. The matching release APK checker also passes. Play Console was audited on 2026-07-12: codes `1` through `8` are the only uploaded bundles and production runs `8 / 1.0.7`.
- [x] Slice 8E uploaded that exact AAB (SHA-256 `e956a14c1d8355127365f9ab50d7c52a31b78c2829c5c128768232f85d26534e`) to Internal Testing, where `9 / 1.0.8` is available; three tester accounts are authorized and one opt-in was verified. The same artifact is submitted for a 100% Production rollout across all targeted countries, with Play quick checks passed and the change now in Google review. Managed Publishing is off, so approval will publish automatically.
- [ ] Before production enforces client protocol `10132`, confirm Google has approved the Production change and ordinary players can receive `9 / 1.0.8`. Submission/in-review status is not public availability.
- [ ] Android day-one distribution: the verified Play Store listing URL is present in portal download metadata and points at the approved production track; direct APK remains an explicitly tested fallback, not the only advertised Android path.
- [x] Generated client/cache metadata is intentionally committed and the canonical 530-file manifest reproduces after the clean build.
- [x] Build artifacts remain untracked: `server/*.jar`, `Client_Base/Open_RSC_Client.jar`, `PC_Launcher/OpenRSC.jar`, APKs, and AABs.

## Config

- [ ] `scripts/check-launch-config.mjs <deployed-server.conf>` passes and the config matches `docs/CONFIG-MATRIX.md`.
- [ ] `Client_Base/src/orsc/Config.java` `CLIENT_VERSION` matches server `client_version`.
- [ ] `enforce_custom_client_version: true` is enabled for any non-local release.
- [ ] `server_port` / `ws_server_port` are the intended public ports.
- [ ] `server_name`, welcome text, and Discord/community URLs are correct.
- [ ] `member_world` is intentional and documented.
- [ ] `want_beta_onboarding_guide: false` for public launch unless this is an explicitly trusted beta window.
- [ ] `want_void_colossus: false`, `want_void_dungeon: true`, `want_beta_onboarding_guide: false`, and `custom_landscape: true`; the enabled dungeon is exactly the approved three-floor, boss-free build.
- [ ] `production_command_lockdown: true` for public launch, then verified with owner and non-owner staff accounts.
- [ ] `want_cracker_campaign: true`, NPC-kill denominator `500`, and skilling denominator `1000`; before activation, `::cracker N` is run once by the owner and the database contains exactly one `void_cracker_pool_remaining` row with that value.
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

## Portal account smoke test

- [ ] `scripts/test-portal-api.sh` and `scripts/test-portal-schema.sh` pass against the release source.
- [ ] `scripts/smoke-portal-prelaunch-visual.sh` passes at desktop and mobile widths against the staged portal.
- [ ] Signed-out `/portal` and `/portal#dashboard` show only account access; no dashboard, roster, account email, or private navigation flashes before session validation.
- [ ] A valid login opens `/portal#dashboard`; trying to log in while already authenticated is rejected until logout.
- [ ] Logout revokes the server session, clears browser state, returns to `/`, restores signup/download content, and a subsequent dashboard deep link shows sign-in only.
- [ ] Password recovery by email and by character username returns the same generic success response; the username path shows only a masked email hint.
- [ ] An email-verification link opens a fragment-based confirmation screen; a scanner-style `GET` redirects without creating the account, and only the explicit confirmation `POST` creates it.
- [ ] An emailed reset link works once, expires on schedule, stores no plaintext token, changes only the website password, and revokes all portal sessions, reset tokens, and recovery codes.
- [ ] Recovery-code reset works once, does not mark an unverified email verified, and also leaves the player signed out for a clean login.
- [ ] Recovery-code rotation requires the current website password, or a recent passwordless federated login.
- [ ] A portal-created linked character can reset its game password only while offline and after website-password confirmation; wrong-account, imported/unlinked, online, stale-owner, helper-failure, and rate-limit cases leave the game row unchanged.
- [ ] `PORTAL_EMAIL_VERIFICATION_REQUIRED=1`, Resend delivery, `PORTAL_PUBLIC_ORIGIN`, the game-password helper classpath, and all recovery limits match `docs/CONFIG-MATRIX.md` in the deployed env.

## Client smoke test

- [ ] `scripts/run-client.sh` connects to the intended host/port.
- [ ] Login screen renders Voidscape branding correctly.
- [ ] Desktop and native Android Create Account use in-client username/password registration; web `/play` Create Account and every recovery action remain portal-first.
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
- [ ] **OWNER-WAIVED / NOT PASSED for the July 18 launch:** no physical Android device report was produced. The owner accepted the release risk on 2026-07-12 and authorized Android promotion using the signed `9 / 1.0.8` artifact plus existing emulator evidence at `tmp/android-cracker-campaign-8b-accepted5`; emulator proof does not satisfy or turn this physical-device check into a pass.
- [x] Android physical-QA waiver decision is recorded for launch. This checkbox records the owner decision only, not a physical-device QA pass; any device-specific regression remains a launch risk and should be tested on real Android hardware as soon as available.
- [x] iPhone launch scope decision is recorded: iPhone web is deferred from the July 18 launch and removed from launch-ready advertising. This checkbox records de-scoping only; physical iPhone Safari/Home Screen QA and the final iPhone release audit remain not passed, and every `if shipping` check below stays post-launch work.
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
- [ ] Release-disabled content remains closed: Void Colossus entrance, Void Knight ladder, Void Rush, and Undead Siege all deny public entry.
- [ ] Void Dungeon follows its release gate: when enabled, entry costs 100,000 coins until an underground death, all three floors and maps work, PvP is 1v1-only, and no fourth floor or Void Wyrm path is exposed; when disabled, entry and stale underground logins fail closed.
- [ ] Void chest requires a Void Key and rolls rewards.
- [ ] Auction House opens, lists, buys, cancels, collects, and handles expiry.
- [ ] Player titles command opens the achievement board and active title appears beside the overhead name.
- [ ] Karamja Fishmonger notes supported raw/cooked fish without cooking them.
- [ ] Desktop and Android `Create Account` open in-client username/password character creation, then prefill Existing User; their recovery buttons still open the portal. Web `/play` keeps Create Account/recovery portal-first, and launch configs keep packet registration enabled with email disabled.
- [ ] Lumbridge Subscription Vendor grants one reserved starter subscription card per linked account, does not open a shop, marks the portal reward as claimed, and redeemed cards apply the intended account-wide XP rates.
- [ ] PK Catching Simulator starts, scores, `::leave` exits, and highscores persist.
- [ ] Void Rush, Undead Siege, Void Colossus, the retired fourth dungeon floor, and Void Wyrm have no player-reachable launch path.
- [ ] Dragon sword components drop/source/assemble as intended.
- [ ] Custom items render in inventory and when wielded.
- [x] Cracker campaign control plane passes owner/non-owner QA: `::cracker`, exact set, restart persistence, `::cracker 0`, operation-specific staff audit, and one canonical global-cache row. Evidence: `tmp/cracker-campaign-4a-qa/summary.json`.
- [x] `tests/cracker-campaign-unit.sh` and `scripts/build.sh` pass for Slice 4B, covering exact `1/500` and `1/1000` denominator injection, credited NPC-hook order, positive actual-XP/tick-deduped skilling eligibility, both tutorial exclusions, combat/Magic exclusion with Prayer eligible, atomic item `575` + pool + provenance settlement, full/unsupported/empty no-mutation paths, no ground spill, lifecycle/fault/lost-COMMIT handling, concurrent final-cracker winners, and post-commit message ordering.
- [x] Disposable live voidbot proof exercises both earning paths with controlled QA odds and verifies exact inventory, pool, provenance, private/global announcement, full-inventory no-decrement, zero-pool inactivity, cleanup, and database integrity. Evidence: `tmp/cracker-campaign-4b-qa/summary.json`; QA-only `1/1` odds and the isolated database were restored to their exact pre-run state.
- [x] Slice 4C shared HUD lifecycle and parser contracts pass: senderless `@vscrackercampaign@v1|remaining` is gated to custom clients `>=10132`, accepts only `0..1,000,000`, consumes recognized metadata before chat, hides on zero/malformed/unknown state, snapshots after normal/resumed login settings, publishes owner-set and post-commit changes, and clears stale client state across login/logout. No opcode or packet shape changed.
- [x] PC Workbench captures `1,000`, `999`, `1`, and hidden `0` at real presets `4` (`1024x768`) and `5` (`512x346`) with exact comma/singular labels, in-frame bounds, no top-tab/location overlap, kill-feed clearance, eight captures, and original-state restoration. Evidence: `tmp/workbench-cracker-campaign-4c/scenario-2.json`. This is compact PC proof, not native Android proof.
- [x] Slice 5A ledger contract and both database migrations pass focused tests: immutable season records, UUID-keyed PK events, per-season streak projections, prefix-safe MySQL/SQLite SQL, exact replay semantics, and settled lost-COMMIT verification. No raw IP is persisted.
- [x] Slice 5B deterministic first-skill tests pass: ordinary non-OpenPK characters can claim exact `80/90/99` thresholds only after the maximum-skill save succeeds; competing/replayed/elevated/disabled/invalid claims publish nothing, and an ordinary milestone at the same level is suppressed.
- [x] Slice 5C deterministic qualified-PK tests pass across evidence capture, every direct combat style, exclusion of recoil/poison/environment/NPC damage, preliminary anti-farm reasons, exact post-drop loot valuation, `5000` floor, unordered 30-minute pair cooldown, exact death replay, victim reset on every audited real PvP death, and killer increment only on qualification.
- [x] Slice 5D deterministic first-campaign-cracker tests pass: `first:item:575` settles in the same transaction as item/pool/provenance, only an ordinary launch-season winner receives the record, insert/replay/lost-COMMIT paths reconcile exactly, and normal plus distinct first-cracker announcements are post-commit best effort.
- [x] Slice 5E disposable live voidbot proof crosses a genuine level-80 XP boundary, earns a genuine campaign cracker through a normal NPC kill, completes a real two-player qualified Wilderness PK, and repeats the same pair inside 30 minutes. Exact rows/messages/restart persistence, pair-cooldown rejection, source-database hashes, two SQLite integrity checks, and process cleanup pass. Evidence: `tmp/world-achievements-5e-qa-20260711-v1/summary.json`. Exact duplicate-death UUID replay remains correctly exercised by `tests/world-pk-settlement-unit.sh` because gameplay generates UUIDs internally.
- [ ] Before launch activation, confirm `want_world_achievements: true`, `world_achievement_season_id: launch-2026`, and `world_pk_loot_minimum: 5000`; archive the empty/new-season baseline and confirm no backfill job has run.
- [x] Slice 6 public Legends API is a GET-only, server-season-scoped projection of allowlisted firsts and aggregate qualified-PK rankings. Exact schema/order, other-season exclusion, malformed-row filtering, private-data canaries, unchanged SQLite hash, missing-DB/schema failure, public method lockdown, full portal regression, syntax/diff checks, and `scripts/build.sh` pass via `tests/portal-legends-api.sh` and `scripts/test-portal-api.sh`. It exposes no account/player IDs, victim/death data, network identity, rejection reasons, record/source keys, private detail, or internal evidence.
- [x] Slice 7 public `/legends` page renders chronological firsts and aggregate Wilderness leaders from that API only, with no caller season selector or polling. Focused/static contracts, full portal regression, canonical build, and browser QA pass for populated desktop `1440x1000`, populated/PK/empty/error mobile `390x844`, generic failure copy, retry recovery, UTC times, zero horizontal overflow, no private-field text, and no browser warnings/errors. Evidence: `tmp/portal-legends-slice7-v1/summary.json`.
- [x] Slice 8B protocol-source activation coordinates the enforced launch preset, shared PC/native-Android/TeaVM client, and voidbot at `10132`; no opcode, enum ordinal, or packet shape changed.
- [x] Slice 8B local platform proof exercises TeaVM receipt/rendering over a real WebSocket login (`1000`, owner broadcasts `999/1/0`, five malformed states hidden, no chat leak) at `tmp/web-teavm-cracker-campaign-10132`, plus native Android emulator portrait/landscape/zero/malformed rendering, IME release, no overlap/chat leak, explicit logout, and exact delayed DB restoration at `tmp/android-cracker-campaign-8b-accepted5`. This does not replace hosted or physical-device proof.
- [x] Slice 8C seals the reviewed source, reproduces the cache manifest, produces the clean upload-key-signed `9 / 1.0.8` AAB, and passes the Play preflight against highest uploaded code `8` and `server/voidscape-launch.conf` at `10132`. It does not upload or roll out the bundle.
- [ ] Slice 8E Android promotion gate is complete only after Google approves the submitted 100% Production rollout and ordinary players can receive `9 / 1.0.8`. Internal Testing is already available, Play quick checks passed, physical Android is explicitly owner-waived/not passed, iPhone is deferred, and hosted TeaVM HTTPS/WSS verification is complete at `tmp/launch-staging-hosted-c519a-final-signup-chrome-pass/web-deployment/summary.json`.
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
- [ ] Final prelaunch readiness report is archived with Android physical QA explicitly owner-waived/not passed and the iPhone final audit not run because iPhone is deferred. Do not supply synthetic device reports to turn either row green:
  `scripts/check-prelaunch-readiness.sh --out tmp/prelaunch-readiness-final --host <game-host> --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws-url wss://<portal-host>/play/ws/`
- [ ] Visual game/client evidence board is generated and reviewed:
  `scripts/build-prelaunch-visual-board.mjs --out tmp/prelaunch-visual-board`
- [x] Launch staging bundle is generated locally with the production target URLs:
  `scripts/package-launch-staging.sh --host <game-host> --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws-url wss://<portal-host>/play/ws/ --skip-android`; `MANIFEST.txt` reports `promotable=true`, fresh server/client and web builds, and no blockers.
- [x] The local bundle's server config/database/client-cache checksum tables, direct artifact hashes, forbidden-file/secret scan, and all 538 TeaVM build-manifest files pass; evidence lives under `tmp/launch-staging-8d-final*`. No remote sync, service restart, signup, Play upload, or production mutation occurred.
- [ ] Generated `RELEASE-HANDOFF.md` from the launch-staging bundle is filled with actual backup paths, rollback owner, and AGPL/source-disclosure confirmation, then archived with `MANIFEST.txt` and verifier output.
- [ ] If Android is included in the public bundle, launch staging is generated with `--android-release`; the committed endpoint already matches, and `MANIFEST.txt` records `promotable=true`, `android_apk_type=release`, `android_apk_promotable=true`, and `android_release_check=passed`. Nothing under `android/rehearsal-only/` is published.
- [x] Android public-channel decision is recorded: Google Play is the launch path and the release-signed APK remains the explicit fallback. The owner accepts the physical-device waiver/not-passed state for this launch; the signed artifact and emulator proof do not erase that risk.
- [ ] **OWNER-WAIVED / NOT PASSED:** the public Android channel has no passing physical Android QA report. Archive the waiver, Play delivery proof, signed-artifact hashes, and emulator evidence with the release handoff instead of fabricating a device pass.
- [ ] If Android APK links are public, `/api/public` shows the Android row with a SHA-256 hash and `/downloads/android-apk` returns the APK. Set `PORTAL_ANDROID_APK` when the public APK lives outside the default build output.
- [ ] Prelaunch portal signup is backed by production persistence and creates linked game characters; web `/play` remains portal-first while desktop/native Android packet registration stays enabled with email disabled.
- [ ] Public `/api/health.config.publicReady` is `true`, `abuseHashSaltConfigured` is `true`, and `issues` is empty; endpoint output exposes only booleans, never secret values.
- [ ] Hosted launch staging verifier passes against the deployed portal, web client, WSS endpoint, and deployed server config:
  `scripts/verify-launch-staging.mjs --portal-url https://<portal-host>/ --web-url https://<portal-host>/play/ --ws wss://<portal-host>/play/ws/ --server-config <deployed-server.conf> --admin-public-url https://<portal-host>/ --signup-username <new-qa-character> --signup-email <email-you-can-open-now> --signup-password <new-portal-and-game-password> --run-signup`; the operator completes that exact delivered-email verification during the bounded wait. Through launch, the generated production verifier probes `voidscape.gg`, `www.voidscape.gg`, and the legacy sslip origin for admin-route `404`; removing that alias is deferred until the verifier contract changes after launch.
- [ ] Recovery-code password reset is tested, old sessions are revoked, and support knows the fallback path for players without codes.
- [ ] Older native-account claim is tested with a real backfill fixture: wrong game passwords are throttled without globally locking the username, email confirmation is explicit and one-use, game credentials/ownership markers and active subscription time stay unchanged, and staff has a no-auto-merge fallback for collisions or lost game passwords.
- [ ] `PORTAL_LAUNCH_FREE_CARD_HOURS=0`; registration is paused for the post-reset final-roster snapshot, and launch cards come only from the reconciled cutover cohort. The archived dry-run lists every character plus every staff/actively-banned/test/ambiguous exception, records an explicit include or exclude decision for each, and its exact `reviewToken` plus frozen `asOfMs` authorize the atomic marker apply and `launch_subcard_2026:done` seal. Verify one `launch_subcard_2026:<playerId>` marker per included character, none for exclusions or later replacement characters, and preserve older starter-card promises separately. Starter-card abuse controls retain a stable hash salt and staff review path.
- [ ] Public nginx returns `404` for `/api/admin/*` with and without an admin-token header on every public host; `PORTAL_ADMIN_TOKEN` is unset during normal public operation. Full staff identity/RBAC is deferred, and any bounded maintenance token is loopback-only and removed immediately afterward.
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
