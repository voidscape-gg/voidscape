# Voidscape TeaVM Web Client

This target compiles the shared `Client_Base` Java client to browser JavaScript with TeaVM.

The current goal is an iPhone Safari web client that keeps the real shared renderer, login flow, cache, and server protocol instead of recreating the game in a separate JavaScript renderer.

Build:

```bash
../scripts/build-web-teavm-spike.sh
```

Output:

```text
Web_Client_TeaVM/target/teavm/index.html
```

Run locally for Mac/iPhone testing:

```bash
../scripts/run-server.sh
../scripts/run-web-teavm-local.sh --no-build
```

The local web script prints a Mac URL and an iPhone URL. The iPhone must be on the same Wi-Fi as the Mac, and macOS must allow the Java server and Python static server through the firewall.

For faster UI iteration in actual iOS Safari, use the iPhone Simulator:

```bash
../scripts/run-server.sh
../scripts/run-web-teavm-iphone-simulator.sh --no-build
```

The simulator runner boots an available iPhone Simulator, opens Mobile Safari to the TeaVM client, captures `tmp/web-teavm-simulator/simulator-safari.png`, writes `tmp/web-teavm-simulator/simulator-run.md`, `tmp/web-teavm-simulator/simulator-run.json`, `tmp/web-teavm-simulator/simulator-home-screen-checklist.md`, `tmp/web-teavm-simulator/simulator-screenshot-checks.json`, and, in local mode, `tmp/web-teavm-simulator/simulator-http-checks.json`, then keeps the local static server alive until Ctrl-C. Use `--diag` when you want the diagnostics panel in the simulator, `--orientation-matrix` when you want portrait, raw Landscape Right, and upright Landscape Right screenshots (`simulator-safari-portrait.png`, `simulator-safari-landscape-right.png`, and `simulator-safari-landscape-right-upright.png`), `--record-video --manual-seconds 90` when you want a timed hands-on Mobile Safari video artifact (`simulator-session.mov` plus `simulator-video-checks.json`), `--pasteboard "text"` when you want test text available from the Simulator pasteboard, `--device "iPhone 17 Pro Max"` or `--udid <id>` for a specific simulator, and `--exit-after-open` for a one-shot capture. Screenshot runs pin the Simulator status bar to `9:41`, Wi-Fi, and 100% charged for comparable UI artifacts, then clear the override on exit; use `--no-stable-status-bar` when you need the live Simulator status bar. Simulator screenshots are checked for minimum dimensions, nonblack pixels, color variation, and palette spread so blank/stuck Safari captures fail the run; `simulator-run.json` records the machine-readable release settings; recorded videos are checked for a non-empty QuickTime/ISO movie file; local-mode HTTP checks also require Safari to request the TeaVM JS, manifest, core cache archives, and Voidscape login art. The generated Home Screen checklist now follows the launch mode: normal runs produce player-view visual QA steps, while `--diag` runs produce the stored-diagnostics/Home Screen iteration steps and expected diagnostics evidence. The captured screenshot surface is still Mobile Safari browser, so Safari chrome/tabs can appear in artifacts; use it to tune the game viewport, safe-area, keyboard, orientation, and control layout, while final full-screen release proof still comes from the real iPhone Home Screen QA report.

For real iPhone QA with a saved checklist/report:

```bash
../scripts/run-server.sh
../scripts/run-web-teavm-iphone-qa.sh --no-build
```

The QA script serves the same TeaVM output, prints normal/diagnostics/reset/Home Screen URLs, and writes `tmp/iphone-web-qa/iphone-safari-qa-report.md` with expected diagnostics values and the required Safari/Home Screen checklist. For final validation, fill the physical device/tester fields, open the diagnostics URL once to save diagnostics mode, add the app to Home Screen, launch from the manifest/Home Screen URL without `diag=1`, copy blocking-dialog diagnostics before closing the welcome/wilderness modal, tap the custom HUD `All` / `Chat` / `Quest` / `Private` chat tabs plus all six top HUD icons, swipe-scroll an opened top HUD panel, close an opened panel, background and return to the Home Screen app, perform post-resume tap-to-move or chat, pair a physical keyboard long enough to test Escape, then copy final diagnostics so the report proves `standalone: true`, `diagnostics.source: "stored"`, `lifecycle.resumeCount > 0`, post-resume input/movement in `postResumeProof`, custom HUD transitions in `uiHistory`, and top-panel scroll routing in `scrollHistory`.

For deployed real iPhone QA after upload:

```bash
../scripts/run-web-teavm-iphone-qa.sh --base-url https://<host>/ --portal https://<portal-host>/
../scripts/run-web-teavm-iphone-qa.sh --base-url https://<host>/ --ws wss://<host>/<path> --portal https://<portal-host>/
```

Use the first form for same-host WSS, and the second when the reverse proxy uses an explicit WebSocket path or hostname. Use `--portal-account-url` and `--portal-recovery-url` instead of `--portal` when the two portal flows have dedicated routes.

After pasting the hosted verifier `summary.json`, copied diagnostics JSON, filling the device fields, and checking completed items:

```bash
../scripts/validate-web-teavm-iphone-qa-report.py ../tmp/iphone-web-qa/iphone-safari-qa-report.md
```

For local LAN fixtures that do not have a deployed verifier summary yet, use `--allow-no-deployment-verification`; do not use that flag for release.

For real iPhone troubleshooting, append `&diag=1` or `&debug=1` to the URL. This shows a small `i` toggle that opens a live diagnostics panel with endpoint, WebSocket URL, viewport, canvas, keyboard, lifecycle resume counters, post-resume input/movement proof, input, local player tile/camera state, shared UI/custom HUD state, bounded custom HUD `uiHistory`, bounded scroll-routing `scrollHistory`, and recent JavaScript/console/resource error state. The adjacent `copy` button copies the same diagnostics snapshot as JSON so testers can send it without Safari dev tools; if Safari blocks clipboard access, the button changes to `select` and opens a selectable JSON field instead.

Account creation and recovery are browser portal handoffs on the web mobile profile. The TeaVM build uses web-only sentinel URLs from `orsc.osConfig` and resolves them at runtime so deploys do not need a Java rebuild:

```text
https://<host>/index.html?mobile=1&portal=https://<portal-host>/
https://<host>/index.html?mobile=1&portalAccountUrl=https://<portal-host>/portal%3Fauth%3Dregister&portalRecoveryUrl=https://<portal-host>/portal%3Fauth%3Drecovery
```

`portal` derives `?auth=register` on the portal base for Create Account and retains the `#security` recovery handoff. Production may use the canonical `/portal?auth=register` and `/portal?auth=recovery` aliases through the explicit overrides. Specific `portalAccountUrl` / `accountUrl` and `portalRecoveryUrl` / `recoveryUrl` override those defaults. Query-configured portal URLs are saved in browser storage for Home Screen launches; use `resetPortal=1` or `portalReset=1` to clear them. Only `http`, `https`, or same-origin relative URLs are accepted. The handoff navigates the current tab instead of calling `window.open`, which avoids Mobile Safari popup-blocking when the shared Java game loop handles the click.

Repeatable Chrome iPhone-emulation smoke:

```bash
../scripts/run-server.sh
../scripts/smoke-web-teavm-iphone.sh --no-build
```

The smoke starts a temporary static web server, verifies endpoint persistence, diagnostics behavior, a real `test/test` WebSocket login, and post-login mobile controls. It asserts that the mobile login `Create Account` and `Recover account` buttons resolve configured portal URLs through current-tab handoff, the login credentials go through the mobile keyboard bridge with text events plus Enter-to-advance/submit, the login keyboard closes after submit, first-login blocking dialogs put the overlay into dialog-safe mode, the visible `Aa`/`...`/camera shell works after login, chat keyboard text plus Enter events reach the shared client, local chat echo appears, browser Back and external-keyboard Escape both route to shared mobile Back without leaking raw Escape key events, one-shot context tap events fire, camera pad rotation, one-finger drag rotation/zoom, pinch zoom through shared camera state, touch terrain-tap movement through the shared local-player state, the real shared custom HUD render switch plus bottom chat-tab selection and all six top HUD panels, the mid-right `Aa`/`...` rail stays above the bottom Voidscape HUD in portrait and short landscape, portrait-to-landscape-to-portrait framebuffer resizing holds, synthetic Safari resume refreshes the viewport lifecycle state, page scroll stays zero, and recent diagnostics errors stay empty, then writes `summary.json`, a portrait screenshot, and a landscape screenshot under `tmp/web-teavm-smoke/`.

Synthetic iPhone control-regression smoke:

```bash
../scripts/smoke-web-teavm-iphone-controls.sh --no-build
```

This no-server smoke loads the real TeaVM browser input bridge and asserts mobile shell gating, safe portal URL resolution/rejection, keyboard beforeinput/paste/composition text normalization, keyboard viewport freeze, viewport-matrix placement for small/standard/large portrait and short/wide landscape iPhones, dialog-safe blocking-dialog control placement, normal/sloppy taps, timer-based long-press context actions, one-finger camera drags, scroll routing, pinch zoom, opened-menu touch release, the `...` context action, browser Back routing, external-keyboard Escape routing, lifecycle resume refresh, diagnostics copy/fallback-export behavior, and diagnostics capture of console/resource failures.

Local automated release preflight:

```bash
../scripts/run-server.sh
../scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir ../dist/web-teavm
```

This wrapper runs the local automatable gates and writes `tmp/web-teavm-iphone-release-preflight/summary.json`: prerequisite checks for free disk, Chrome, and `playwright-core`; synthetic controls smoke; local real-login iPhone smoke; local HTTPS/same-host WSS smoke; static packaging; local static-package deployment verification with build-manifest matching plus deep manifest asset checks; and the iPhone Simulator Safari matrix when `--with-simulator` is supplied. For release candidates, pass `--package-dir ../dist/web-teavm` so the local preflight packages and verifies the exact static directory that will be uploaded. Final release audit requires the simulator step plus `simulator-run.json`, screenshot checks, and HTTP asset-check artifacts, so use `--with-simulator` for release preflight and rerun old preflights created before that metadata existed; use `--simulator-video <seconds>` when you also want a timed hands-on video artifact. The preflight keeps the normal 512 MB disk floor for non-video runs, but video runs add an estimated recording budget of 10 MB per captured second plus a fixed cushion, so the documented 90-second UI/control pass needs about 1.6 GB free before it starts. Use `../scripts/clean-web-teavm-iphone-artifacts.sh` to dry-run safe generated-artifact cleanup, then add `--apply` to remove the listed candidates. The summary intentionally lists hosted HTTPS/WSS `--expected-build-manifest ... --deep-manifest --smoke` verification and the physical iPhone Home Screen QA report as not covered.

Local HTTPS/same-host WSS smoke:

```bash
../scripts/run-server.sh
../scripts/smoke-web-teavm-iphone-https-wss.sh --no-build
```

This starts a temporary self-signed HTTPS static server and proxies same-host WSS upgrades to the local game server, proving the production default `https://<host>/` -> `wss://<host>:443/` shape before upload.

Deployed HTTPS/WSS smoke:

```bash
../scripts/smoke-web-teavm-iphone.sh --base-url https://<host>/ --ws wss://<host>/<path> --portal https://<portal-host>/
```

Omit `--ws` only when the deployed page should use the same-host default `wss://<host>:443/`. Use `--portal-account-url` and `--portal-recovery-url` instead of `--portal` when the two flows have dedicated routes.

Before sending a hosted link to testers, verify the deployed static root and optional login smoke:

```bash
../scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest ../dist/web-teavm/voidscape-web-build.json --deep-manifest
../scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest ../dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://<host>/<path> --portal https://<portal-host>/ --smoke --user <qa-user> --pass <qa-pass>
```

The hosted `--smoke` summary pasted into the physical iPhone QA report must include `smokeRan: true`, `smokePassed: true`, `allowHttp: false`, `insecureTls: false`, `allowDebug: false`, and `allowInsecureWs: false`, not only `smokeRequested: true`.

After the physical iPhone Home Screen QA report is filled and validated, run the final release audit:

```bash
../scripts/check-web-teavm-iphone-final-release.py --qa-report ../tmp/iphone-web-qa/iphone-safari-qa-report.md --local-preflight ../tmp/web-teavm-iphone-release-preflight/summary.json --package-dir ../dist/web-teavm
```

That audit also checks that the local preflight prerequisites passed, the local preflight package artifact, the `--package-dir` being released, every manifest-listed local package file, the hosted verifier manifest hashes, and the simulator run metadata/screenshots all describe the expected release evidence. Add `--require-simulator-video` when the release includes iPhone UI/control changes so the local simulator artifact must include a passing `simulator-video-checks.json` from `scripts/check-web-teavm-iphone-release.sh --simulator-video <seconds>`; the audit requires at least 90 manual video seconds by default.

Package a deployable static web root:

```bash
../scripts/package-web-teavm.sh
```

Output:

```text
dist/web-teavm/
```

The production package excludes local runtime cache files such as `accounts.txt`, `credentials.txt`, `uid.dat`, `ip.txt`, `port.txt`, and `config.txt`. It also omits TeaVM source maps/debug files by default.

The default page now launches the real shared `mudclient` path. `scripts/probe-web-teavm-real-client.sh` remains as a compatibility wrapper for the old probe entry point.

Current state:

- Reaches the real shared-client Voidscape login screen in a browser canvas.
- Accepts browser pointer, touch, and mouse fallback input on the HTML mobile controls, with a touch-mode `Aa` keyboard toggle that syncs browser focus to the shared mobile keyboard state and a paste/composition-safe text bridge for Safari-style input.
- Logs the local `test` account into the live game scene through the real WebSocket login packet flow, with the current smoke proving username/password entry through the mobile keyboard bridge and Enter-to-advance/submit path.
- Uses a mobile profile on iPhone/touch devices, including full-viewport canvas layout, safe-area-aware page chrome, Android-style portrait framebuffer resizing, and landscape framebuffer widening while keeping the classic 346-pixel full-height frame.
- Can close the welcome dialog and wilderness warning on the mobile path in the current local smoke.
- Detects shared-client blocking dialogs such as welcome and wilderness warning panels, publishes the current dialog name in diagnostics as `snapshot.ui.blockingDialogName`, hides the camera pad while a blocking dialog is open, and moves the web `Aa`/`...`/diagnostics rail below or above the dialog depending on viewport orientation so web controls do not cover modal game text/buttons.
- Opens configured account-creation and recovery portal URLs from the mobile login screen on the web path. Runtime `portal`/`portalAccountUrl`/`portalRecoveryUrl` configuration is visible in diagnostics as `snapshot.portal`, persists for Home Screen launches, rejects unsafe URL schemes, and uses current-tab navigation to avoid Mobile Safari popup blocking.
- Proves tap-to-move in the current local iPhone-emulated smoke by recording a touch terrain tap and waiting for the shared client's local player world tile to change.
- Sends in-game chat through the real shared message-entry path in the current local iPhone-emulated smoke, which now records the browser event queue and requires a local server chat echo.
- Loads the Voidscape PNG login art and custom HUD skin in the browser, including lazy exact-name loading for sized UI sprites and bank/HUD assets. Copied diagnostics expose the shared UI state as `snapshot.ui`, including `ui.webBuild`, `ui.androidProfile`, and the actual custom HUD render switch `ui.customUi`; they also expose bounded `snapshot.uiHistory` proof for custom HUD chat-tab and top-panel state changes plus bounded `snapshot.scrollHistory` proof for scroll gestures routed through shared game UI. The authenticated iPhone smoke taps the bottom HUD `All`, `Chat`, `Quest`, and `Private` tabs, verifies `snapshot.ui.messageTab` changes through the shared client, taps all six top HUD icons, verifies `snapshot.ui.showUiTab` opens and closes through the shared client, swipe-scrolls an opened top panel, and asserts the copied diagnostics history records those interactions.
- Supports Android-style touch context actions: normal touch taps commit on release, long-press opens the in-game right-click menu, and the touch-mode `...` action button makes the next canvas tap a one-shot context action. Once a context menu is visible, touch release uses padded shared-menu bounds for row selection and menu touches do not leak into camera, scroll, pinch, or long-press gestures. The context button stays hidden on the login screen, and after login the `...` and `Aa` buttons sit in a mid-right rail instead of covering the bottom Voidscape HUD.
- Shows a touch-mode camera pad for iPhone play after login: `<`/`>` rotate the shared camera and `+`/`-` drive the existing shared zoom controls through the normal arrow-key path. One-finger canvas drags and two-finger pinch gestures also pulse the same shared rotate/zoom paths. The pad uses 44px controls in portrait and a compact 40px layout in short landscape so it stays out of the Voidscape chat frame.
- Routes iPhone vertical swipes over scrollable game UI through the shared `mudclient.runScroll(...)` path used by Android/desktop, so panels such as bank, settings, spell/prayer lists, friends, chat history, and world-map zoom are not stolen by the camera gesture layer. Copied diagnostics keep a bounded `scrollHistory` of nonzero routed scroll gestures for QA.
- Keeps small finger drift forgiving: touches that move enough to cancel long-press but not enough to become a consumed camera/scroll/pinch gesture still release as primary taps, while real gestures continue suppressing stray clicks.
- Routes iPhone browser Back/history navigation and external-keyboard Escape into the shared Android Back handler, so mobile web Back can close the keyboard, dialogs, menus, and other shared-client states instead of immediately leaving the page. Escape routing is covered by local iPhone smokes, but still needs real Safari/hardware QA.
- Includes an iPhone/PWA shell with a web app manifest, favicon, Apple touch icon, full-screen app metadata, fixed viewport sizing, Safari scroll/zoom guards around the game canvas, and a keyboard-open viewport freeze so the shared framebuffer does not resize while Safari's keyboard animates.
- Builds from a clean TeaVM target and can stage a static production web root without local runtime cache files or debug/source-map artifacts.
- Uses `ws://<host>:43496/` for local HTTP pages, defaults to `wss://<host>:443/` for HTTPS pages, and still supports explicit `?host=...&port=...` or `?ws=wss://...` overrides. Explicit endpoint overrides are saved in browser storage so Home Screen launches from `manifest.webmanifest`'s `?mobile=1` start URL keep using the intended game server; append `?endpoint=reset` or `?resetEndpoint=1` to clear the saved endpoint.
- Exposes `window.__voidscapeEndpoint` and `window.__voidscapeEffectiveWebSocketUrl` for deployment smoke tests and Safari debugging.
- Has an opt-in `?diag=1` / `?debug=1` iPhone diagnostics panel, `window.__voidscapeCollectDiagnostics()` snapshot, Safari lifecycle resume counters, bounded `postResumeProof` input/movement evidence, current and portrait/landscape-history hit-tested mobile overlay control rectangles, shared-client local player tile/camera state, shared UI/custom HUD state, bounded custom HUD `uiHistory`, bounded scroll-routing `scrollHistory`, recent JavaScript/console/resource error capture, and copyable or selectable JSON report for real-device QA without Safari dev tools. Once in-game, the diagnostics buttons sit beside the mobile action rail instead of covering the top custom HUD, and the diagnostics/copy buttons are sized as 44px-class touch targets for real-device QA.
- Includes `scripts/run-web-teavm-iphone-qa.sh` to serve the LAN client or prepare a deployed-host report, `scripts/validate-web-teavm-iphone-qa-report.py` to fail fast on mismatched `ws://` / `wss://` endpoint, missing physical device/tester metadata, missing deployment-verifier `summary.json` proof, deployment summaries generated with development verifier flags, missing deployed build-manifest/deep-manifest proof, missing production `Cache-Control` proof, missing explicit hosted `smokeRan` / `smokePassed` proof, missing Home Screen standalone/resume evidence, missing post-resume gameplay proof, missing login/local-player state, missing shared web/mobile/custom-HUD state, missing `uiHistory` interaction proof, missing `scrollHistory` top-panel scroll proof, missing blocking-dialog diagnostics, missing or overlapping/undersized mobile overlay controls, missing paired-keyboard Escape proof, stale QA report templates, bad canvas/viewport values, recent browser errors, or unchecked QA items, and `scripts/check-web-teavm-iphone-final-release.py` to tie the local preflight prerequisites/package artifact, simulator metadata/screenshots/video evidence, uploaded package manifest, local package file hashes, hosted verifier summary, and physical QA report into one final pass/fail audit.
- Includes `scripts/smoke-web-teavm-iphone.sh` for repeatable local and deployed Chrome iPhone-emulation coverage of endpoint persistence, diagnostics, real mobile-keyboard login, post-login mobile shell/input controls, custom HUD chat-tab/top-panel touch selection, top-panel swipe-scroll routing, shared camera/zoom state changes, and post-resume input/movement diagnostics; `scripts/smoke-web-teavm-iphone-controls.sh` for synthetic mobile-control regression coverage of beforeinput/paste/composition keyboard paths and touch controls without a running game server; `scripts/smoke-web-teavm-iphone-https-wss.sh` for local HTTPS/same-host WSS proxy coverage; `scripts/run-web-teavm-iphone-simulator.sh` for hands-on iOS Simulator Safari iteration with saved screenshot/run metadata, machine-readable `simulator-run.json`, a generated Home Screen iteration checklist, optional timed video capture with video sanity checks, optional Simulator pasteboard preload, stable screenshot status-bar state, and PNG sanity checks; and `scripts/check-web-teavm-iphone-release.sh` to aggregate the local automated preflight into one `summary.json`.
- Includes `scripts/verify-web-teavm-deployment.sh` to check a hosted static root, deployed `voidscape-web-build.json` fingerprint, production `Cache-Control` policy, optional deep verification of every manifest-listed static asset, expected iPhone web-client hooks including copied diagnostics, `controlsHistory`, custom-HUD `uiHistory`, `scrollHistory`, `postResumeProof`, and mobile endpoint state, Safari/Home Screen metadata, manifest shape, PNG icons, runtime-cache exclusion, TeaVM debug artifact exclusion, optional expected-package manifest matching, and optional deployed real-login smoke before sharing a URL.
- Starts cleanly in the browser without the prior non-blocking TeaVM `Cannot read properties of null (reading 'data')` sound-cache console error.

Still not production complete:

- One-finger camera drags, pinch gestures, one-handed control feel, the mid-right `Aa`/`...` rail placement, and iPhone real-device polish still need real Mobile Safari feel testing and tuning.
- iOS Simulator Safari can now reach post-login gameplay with the real renderer and custom bottom HUD, and the current Simulator pass proves the welcome modal is not covered by the web overlay controls while dialog-safe mode is active. Tune against Simulator for speed, then prove the final layout from a real Home Screen launch because regular Safari browser chrome reduces the usable viewport.
- Login/chat typing, paste, Backspace/Enter, committed composition text, and keyboard-open viewport freezing work in local Chrome iPhone emulation, but still need real Mobile Safari QA for native autocorrect, viewport resize feel, and hardware/soft-keyboard edge cases.
- Long-press, the one-shot context action button, and opened-menu row selection work in local iPhone-emulated smoke, but still need real Mobile Safari tuning for feel and edge cases.
- The camera pad, one-finger camera drags, forgiving tap thresholds, scroll gestures, pinch zoom, and portrait/landscape resizing work in local iPhone-emulated smoke, with shared camera/zoom state proof, but still need real Mobile Safari tuning for placement, accidental presses, gesture threshold, scroll feel, hold-repeat feel, and orientation feel.
- Browser Back/history and external-keyboard Escape routing work in local Chrome iPhone emulation and close the shared mobile Back path, but still need real Mobile Safari, Home Screen, and physical keyboard QA.
- The diagnostics panel works in local Chrome iPhone emulation, including copied/selectable shared UI/custom HUD state, hit-tested portrait/landscape overlay-control history, and JavaScript/console/resource error capture, but still needs real Mobile Safari QA for standalone/Home Screen reporting.
- Account/recovery portal handoff works in local Chrome iPhone emulation, but the final public account and recovery URLs still need to be set on the production host and checked on real Mobile Safari/Home Screen.
- The web package is deployable as static files, but the real production host/proxy, HTTPS certificate, WSS endpoint, and real Mobile Safari QA are still pending.
