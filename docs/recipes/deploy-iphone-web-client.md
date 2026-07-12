# Deploy iPhone Web Client

This recipe publishes the TeaVM iPhone client as static files over HTTPS and forwards browser WebSocket traffic to the normal Voidscape game server `ws_server_port`.

## Build the static root

```bash
scripts/package-web-teavm.sh
```

Upload `dist/web-teavm/` to the host that serves the iPhone client. Do not upload runtime files from a local cache directory; the package script already excludes files such as `accounts.txt`, `credentials.txt`, `uid.dat`, `ip.txt`, `port.txt`, and `config.txt`.

The server config must enable WebSockets:

```text
want_feature_websockets: true
ws_server_port: 43496
```

Keep `ws_server_port` private to the host or VPC when TLS terminates at a reverse proxy.

## Caddy same-host HTTPS and WSS

This shape supports the web client's default HTTPS behavior: normal HTTPS requests serve static files, while WebSocket upgrade requests to the same host are proxied to the game server.

```caddyfile
play.example.com {
	root * /srv/voidscape/web-teavm

	@websocket {
		header Connection *Upgrade*
		header Upgrade websocket
	}
	reverse_proxy @websocket 127.0.0.1:43496

	header /index.html Cache-Control "no-cache"
	header /manifest.webmanifest Cache-Control "no-cache"
	header /voidscape-web-client.js Cache-Control "no-cache"
	header /Cache/* Cache-Control "public, max-age=31536000, immutable"

	try_files {path} /index.html
	file_server
}
```

Launch URL:

```text
https://play.example.com/index.html?mobile=1
```

Expected diagnostics:

```text
endpoint.source: default
effectiveWs: wss://play.example.com:443/
```

## `voidscape.gg/play/` Subpath Shape

If the beta lives at `https://voidscape.gg/play/` instead of a dedicated `play.` hostname, keep the trailing slash in every base URL. The web package uses relative `index.html`, `manifest.webmanifest`, `voidscape-web-client.js`, icon, and `Cache/...` paths, so it is safe to serve from `/play/` as long as the proxy preserves that directory shape.

For Caddy on the main site, one clean shape is:

```caddyfile
voidscape.gg {
	@play_websocket {
		path /play/ws/*
		header Connection *Upgrade*
		header Upgrade websocket
	}
	reverse_proxy @play_websocket 127.0.0.1:43496

	handle_path /play/* {
		root * /srv/voidscape/web-teavm
		header /index.html Cache-Control "no-cache"
		header /manifest.webmanifest Cache-Control "no-cache"
		header /voidscape-web-client.js Cache-Control "no-cache"
		header /Cache/* Cache-Control "public, max-age=31536000, immutable"
		try_files {path} /index.html
		file_server
	}
}
```

Launch URL:

```text
https://voidscape.gg/play/index.html?mobile=1&ws=wss://voidscape.gg/play/ws/
```

After the first launch, Safari/Home Screen keeps the explicit WSS endpoint in browser storage. The clean player URL can then be:

```text
https://voidscape.gg/play/
```

Verification must also use the trailing slash:

```bash
scripts/verify-web-teavm-deployment.sh https://voidscape.gg/play/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://voidscape.gg/play/ws/ --portal https://voidscape.gg/ --smoke --user <qa-user> --pass <qa-pass>
scripts/run-web-teavm-iphone-qa.sh --base-url https://voidscape.gg/play/ --ws wss://voidscape.gg/play/ws/ --portal https://voidscape.gg/
```

## Account and recovery portal links

The iPhone web client keeps account creation and recovery outside the game renderer. The web build's `orsc.osConfig` uses sentinel values and the browser resolves them at runtime, so production can change portal URLs without rebuilding Java.

If the account and recovery flows live on the same portal host, launch once with a generic `portal` URL:

```text
https://play.example.com/index.html?mobile=1&portal=https://voidscape.com/
```

The client derives:

```text
Create Account  -> https://voidscape.com/#account
Recover account -> https://voidscape.com/#security
```

Use explicit URLs when the flows have dedicated routes:

```text
https://play.example.com/index.html?mobile=1&portalAccountUrl=https://voidscape.com/#account&portalRecoveryUrl=https://voidscape.com/recover
```

Aliases `accountUrl` and `recoveryUrl` are also accepted. Query-configured portal URLs are saved in browser storage for later Home Screen launches; clear stale tester values with `?resetPortal=1` or `?portalReset=1`. Only `http`, `https`, and same-origin relative URLs are accepted. The login-screen handoff navigates the current tab rather than opening a popup so Mobile Safari does not block it.

## Resource modes and beta profiles

The browser shell has web-only render controls for testers who want to pause drawing without changing server tick rate, packet flow, or the shared Java game loop:

```text
https://play.example.com/index.html?mobile=1&resource=gfx-off
```

`resource=gfx-off` stops normal canvas uploads after one transition frame and shows a small `GFX off / Resume` overlay; network, input polling, lifecycle tracking, diagnostics, and game state continue running. Player-facing controls expose only `Normal` and `GFX off`; legacy `low`, `battery-saver`, and `low-resource` requests normalize to `normal`. The same modes can be toggled from browser diagnostics with `window.__voidscapeSetResourceMode('gfx-off')` or `window.__voidscapeSetResourceMode('normal')`.

`scripts/package-web-teavm.sh` normalizes packaged directory modes to `755` and file modes to `644` before manifest generation. Keep that behavior if the package is copied through tar/rsync; production static hosts must be able to read every cache file listed in `voidscape-web-build.json`, or deep manifest verification can pass locally but hit hosted `403` responses.

For players testing more than one account in separate tabs/windows, use a browser profile namespace:

```text
https://play.example.com/index.html?mobile=1&profile=main
https://play.example.com/index.html?mobile=1&profile=alt1
```

Named profiles isolate saved endpoint and portal settings. They do not save or autofill game usernames/passwords; the actual account login is still the normal in-game login flow. `endpoint=reset` and `resetPortal=1` clear only the active profile's saved web settings.

## Nginx with explicit WebSocket path

Nginx is simplest when the WebSocket endpoint has its own path. Use `?ws=` so the client connects to that path instead of the default root WebSocket URL.

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

server {
    listen 443 ssl http2;
    server_name play.example.com;

    ssl_certificate /etc/letsencrypt/live/play.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/play.example.com/privkey.pem;

    root /srv/voidscape/web-teavm;
    index index.html;

    location /ws/ {
        proxy_pass http://127.0.0.1:43496/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
    }

    location /Cache/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
    }

    location = /index.html {
        add_header Cache-Control "no-cache" always;
    }

    location = /manifest.webmanifest {
        add_header Cache-Control "no-cache" always;
    }

    location = /voidscape-web-client.js {
        add_header Cache-Control "no-cache" always;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

Launch URL:

```text
https://play.example.com/index.html?mobile=1&ws=wss://play.example.com/ws/
```

After that first explicit launch, Home Screen launches from `index.html?mobile=1` should keep using the saved `wss://play.example.com/ws/` endpoint.

## Nginx with separate WebSocket host

A separate WebSocket hostname also avoids path ambiguity.

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

server {
    listen 443 ssl http2;
    server_name play.example.com;

    ssl_certificate /etc/letsencrypt/live/play.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/play.example.com/privkey.pem;

    root /srv/voidscape/web-teavm;
    index index.html;

    location /Cache/ {
        try_files $uri =404;
        add_header Cache-Control "public, max-age=31536000, immutable" always;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 443 ssl http2;
    server_name ws.play.example.com;

    ssl_certificate /etc/letsencrypt/live/ws.play.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/ws.play.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:43496;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
        proxy_send_timeout 3600s;
        proxy_buffering off;
    }
}
```

Launch URL:

```text
https://play.example.com/index.html?mobile=1&ws=wss://ws.play.example.com/
```

## Verify

```bash
scripts/verify-web-teavm-deployment.sh https://play.example.com/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest
```

That checks the required static files, deployed `voidscape-web-build.json`, production `Cache-Control` policy, iPhone/PWA markers, icons, current client hooks including copied diagnostics, profile-scoped endpoint storage, resource-mode controls, `controlsHistory`, custom-HUD `uiHistory`, scroll-routing `scrollHistory`, `postResumeProof`, and mobile endpoint state, and rejects exposed runtime cache or TeaVM debug files. With `--expected-build-manifest`, it confirms the hosted build manifest matches the local package manifest; with `--deep-manifest`, it fetches every manifest-listed static file and verifies size/SHA-256 plus immutable `/Cache/*` headers. Add `--smoke` before sending testers to it so the same command also runs the Chrome iPhone-emulation login smoke. For same-host Caddy-style WSS:

```bash
scripts/verify-web-teavm-deployment.sh https://play.example.com/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --portal https://voidscape.com/ --smoke --user <qa-user> --pass <qa-pass>
```

For explicit Nginx `/ws/` routing:

```bash
scripts/verify-web-teavm-deployment.sh https://play.example.com/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://play.example.com/ws/ --portal https://voidscape.com/ --smoke --user <qa-user> --pass <qa-pass>
```

Use `--portal-account-url` and `--portal-recovery-url` instead of `--portal` when those flows have dedicated production routes.

Create the real-device Safari report before handing the link to an iPhone tester. For same-host Caddy-style WSS:

```bash
scripts/run-web-teavm-iphone-qa.sh --base-url https://play.example.com/ --portal https://voidscape.com/
```

For explicit Nginx `/ws/` routing:

```bash
scripts/run-web-teavm-iphone-qa.sh --base-url https://play.example.com/ --ws wss://play.example.com/ws/ --portal https://voidscape.com/
```

After the tester pastes the diagnostics JSON, fills every device/tester field with the physical iPhone model, iOS version, network, tester, Home Screen mode, and external-keyboard yes/no result, and checks the smoke items, validate the report:

```bash
scripts/validate-web-teavm-iphone-qa-report.py tmp/iphone-web-qa/iphone-safari-qa-report.md
```

The final report also includes a `Deployment Verification` JSON section. Paste the `summary.json` from `scripts/verify-web-teavm-deployment.sh --smoke` there so the report validator can prove the uploaded HTTPS/WSS build was verified before the physical iPhone run, including `smokeRan: true`, `smokePassed: true`, `allowHttp: false`, `insecureTls: false`, `allowDebug: false`, `allowInsecureWs: false`, the hosted `voidscape-web-build.json` fingerprint, `buildManifestMatchesExpected: true`, `deepManifestChecked: true`, equal `deepManifestVerifiedCount` / `deepManifestFileCount`, `deepManifestFailureCount: 0`, `cachePolicyChecked: true`, and `cachePolicyFailureCount: 0`. Use `--allow-no-deployment-verification` only for local LAN/development fixtures.

Before calling the iPhone web release complete, run the final evidence audit:

```bash
scripts/check-web-teavm-iphone-final-release.py --qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --package-dir dist/web-teavm
```

That command reruns the no-bypass physical QA report validator, confirms the hosted verifier summary was a passing HTTPS/WSS `--smoke` run with expected/deep manifest proof, confirms the hosted manifest hash matches the package manifest in `dist/web-teavm`, verifies every manifest-listed file in the local package by size and SHA-256, confirms the local preflight package artifact matches that exact release package, and confirms the local automated preflight summary passed prerequisites, controls, broad login, focused world-map/walker, focused chat, HTTPS/WSS, package, package-verifier, and simulator gates. The simulator gate must include current `simulator-run.json` metadata proving the preflight used local-mode Simulator Safari with diagnostics, screenshots, orientation matrix, and stable status bar enabled, plus passing screenshot and HTTP asset checks. When the release includes iPhone UI/control changes, run the local preflight with `--simulator-video <seconds>` and add `--require-simulator-video` to the final audit so `simulator-video-checks.json` is required alongside the simulator run metadata, screenshot checks, and HTTP simulator checks. The audit requires at least 90 manual video seconds by default.

Before upload, the same-host HTTPS/WSS shape can be exercised locally against a running game server:

```bash
scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir dist/web-teavm
scripts/smoke-web-teavm-iphone-https-wss.sh --no-build
```

The aggregate preflight writes `tmp/web-teavm-iphone-release-preflight/summary.json` with a free-disk/Chrome/`playwright-core` prerequisites step, the local controls smoke, local real-login smoke, local HTTPS/WSS smoke, package staging, local static-package verifier results, and iPhone Simulator Safari orientation-matrix artifacts. Use `--package-dir dist/web-teavm` for release candidates so the preflight creates and verifies the exact static directory that will be uploaded. The default free-disk floor is 512 MB; override it with `--min-free-mb <MB>` only when you understand the artifact/build-space tradeoff. Video runs add a recording-space estimate before the browser smokes start: 512 MB base plus 10 MB for each requested wait/manual/cushion second, so `--simulator-video 90` needs about 1.6 GB free. Use `scripts/clean-web-teavm-iphone-artifacts.sh` to dry-run cleanup of generated iPhone web artifacts and ignored build output before large video runs. The final release audit requires the prerequisites step plus the simulator step, current `simulator-run.json` metadata, screenshot-check JSON, and HTTP asset-check JSON, so use `--with-simulator` for release preflight and rerun older preflights created before the JSON metadata existed. Use `--simulator-video <seconds>` instead when you need final-audit-enforced hands-on Simulator evidence for UI/control changes. It is a local release preflight, not the hosted deployment proof or physical iPhone report.

For hands-on iOS Safari layout checks on the Mac, open either the local build or the deployed URL in the iPhone Simulator:

```bash
scripts/run-web-teavm-iphone-simulator.sh --no-build
scripts/run-web-teavm-iphone-simulator.sh --base-url https://play.example.com/ --portal https://voidscape.com/ --orientation-matrix --exit-after-open
scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --record-video --manual-seconds 90 --pasteboard "test"
```

Use `--diag` for the diagnostics overlay, `--orientation-matrix` for portrait, raw Landscape Right, and upright Landscape Right captures, `--record-video --manual-seconds <seconds>` for a timed hands-on Simulator Safari session saved as `simulator-session.mov`, `--pasteboard "text"` to preload the iOS Simulator pasteboard for keyboard/paste testing, `--device` or `--udid` for a specific simulator, and check `tmp/web-teavm-simulator/simulator-safari*.png`, `simulator-run.md`, `simulator-run.json`, `simulator-home-screen-checklist.md`, `simulator-session.mov` plus `simulator-video-checks.json` when recorded, `simulator-screenshot-checks.json`, and local-mode `simulator-http-checks.json` after the run. Screenshot captures pin the Simulator status bar to `9:41`, Wi-Fi, and 100% charged for stable review artifacts, then clear the override on exit; use `--no-stable-status-bar` if the live status bar matters. The runner fails if the saved screenshots look blank, mostly black, or too low-variation to be real rendered game content, or if the recorded video is missing/empty/not a QuickTime-style movie; in local mode it also fails if Safari did not request the TeaVM JS, manifest, core cache archives, and Voidscape login art. The generated Home Screen checklist follows the launch mode: normal player-view runs give visual QA steps, while `--diag` runs give the exact stored-diagnostics and Home Screen iteration expectations. The captured screenshot surface is Mobile Safari browser, so Safari chrome/tabs can appear in artifacts; use the screenshots and video to inspect the game viewport and web controls, not to certify Home Screen fullscreen behavior. Simulator Safari is useful for fast viewport, keyboard, safe-area, orientation, and overlay-control iteration, but it does not replace the real iPhone Home Screen report.

For hands-on iPhone Safari QA before upload, serve the LAN client and save a report:

```bash
scripts/run-web-teavm-iphone-qa.sh --no-build
scripts/validate-web-teavm-iphone-qa-report.py tmp/iphone-web-qa/iphone-safari-qa-report.md
```

Then open the diagnostics client on iPhone once to save the endpoint and diagnostics mode:

```text
https://play.example.com/index.html?mobile=1&diag=1
```

Fill the physical iPhone model, iOS version, network, tester, Home Screen mode, and external-keyboard yes/no report fields. Add the app to Home Screen, launch it from the manifest/Home Screen URL without `diag=1`, log in, copy the blocking-dialog diagnostics while the welcome/wilderness modal is still open, and confirm the modal text and close button are not covered by web controls. Then close the modal, tap the shared canvas chat tabs `All`, `Chat`, `Quest`, and `Private`, tap every shared top HUD icon, swipe-scroll an opened top HUD panel, and close an opened panel to confirm the Android-parity shared HUD accepts touch and records state changes. Open the world map, pan/zoom/search, press browser Back while search is focused, intentionally world-walk to a nearby reachable destination, keep the map open after the route succeeds, and paste copied diagnostics into the report's `World Map Diagnostics` section so the validator can prove the near-full-canvas map geometry and route diagnostics on the real phone. Background the Home Screen app, return to it, perform tap-to-move or chat so diagnostics records post-resume gameplay input and movement, rotate once through portrait and landscape so diagnostics records both overlay-control layouts, then open the `i` panel and copy final diagnostics from that resumed Home Screen launch. If a paired physical keyboard is available, press Escape once in-game and confirm it behaves like mobile Back rather than typing into chat or leaving the page; record `External keyboard tested: yes` only when that passes, otherwise record `no`. The final validator rejects missing physical device/tester metadata, missing deployed-verifier proof, stored-diagnostics Home Screen proof, blocking-dialog proof, world-map-open geometry/route proof, custom HUD `uiHistory` proof, top-panel `scrollHistory` proof, and post-resume gameplay proof by default; `--allow-no-deployment-verification` is only for non-release development fixtures.

Confirm the diagnostics panel shows:

- The intended `effectiveWs`.
- The intended `portal.accountUrl` and `portal.recoveryUrl`.
- `standalone: true` after launching from Home Screen.
- `href` does not include `diag=1` or `debug=1`, and `diagnostics.source: "stored"` proves diagnostics survived the manifest/Home Screen launch from saved state.
- `lifecycle.resumeCount > 0` after backgrounding and returning to the Home Screen app.
- `postResumeProof.inputAfterResume: true` and `postResumeProof.movementAfterResume: true` after a post-resume gameplay action.
- `touchProfile: true`.
- `ui.webBuild: true`, `ui.androidProfile: true`, and `ui.customUi: true`.
- `uiHistory.messageTabs` includes `ALL`, `CHAT`, `QUEST`, and `PRIVATE`, and `uiHistory.events` includes top HUD `showUiTab` ids `1` through `6` plus a later `showUiTab: 0`.
- `scrollHistory` includes at least one nonzero scrollable top HUD panel gesture with `scrollableUi: true` and `showUiTab` in `1` through `6`.
- `ui.blockingDialog` / `ui.blockingDialogName` are present in the shared UI diagnostics, and when a welcome or wilderness dialog is open the web overlay controls do not cover the modal text or close button.
- A nonzero canvas framebuffer such as `512x872` in portrait.
- `client.hasLocalPlayer: true` with nonzero `client.worldX/worldY`.
- `controls.keyboardButton`, `controls.actionButton`, `controls.cameraControls`, `controls.diagnosticsButton`, and `controls.diagnosticsCopyButton` visible, hit-tested, inside the viewport, non-overlapping, clear of the bottom/top HUD reserves, and large enough for iPhone touch QA.
- `controlsHistory.portrait` and `controlsHistory.landscape` both present, each with visible and hit-tested overlay controls.
- No unexpected recent errors.
- The diagnostics `i` / `copy` controls remain reachable in-game and do not cover the top custom HUD icons in portrait or landscape.

Confirm the blocking-dialog diagnostics section shows:

- `bodyClass` contains `dialog-open`.
- `href` does not include `diag=1` or `debug=1`, and `diagnostics.source: "stored"`.
- `ui.blockingDialog: true`.
- `ui.blockingDialogName` is non-empty.
- `controls.cameraControls.display: "none"`.
- `controls.keyboardButton`, `controls.actionButton`, `controls.diagnosticsButton`, and `controls.diagnosticsCopyButton` remain visible, hit-tested, inside the viewport, non-overlapping, clear of the bottom HUD reserve, and at least 44px tall/wide where applicable.

Use the diagnostics `copy` button if the tester needs to send the exact report from iPhone Safari. If Safari blocks clipboard access and the button changes to `select`, select/copy the JSON field that appears instead.

If a tester has a stale saved endpoint, clear it with:

```text
https://play.example.com/index.html?mobile=1&endpoint=reset
```
