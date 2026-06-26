# iPhone Web Client Next Chat Context

Last updated: 2026-06-26 after deploying the mobile right-click menu placement/selection package to live `/play/`.

This handoff exists because the prior Codex chat ran for more than 58 hours and context is no longer trustworthy. Treat this file as the current clean starting point for the next chat.

## Current Goal

Get `https://voidscape.gg/play/` ready for real iPhone beta testing using the real shared Voidscape Java client compiled through TeaVM. Do not restart from the old failed JavaScript-renderer idea. The working direction is shared renderer, shared protocol, shared assets, with a browser/mobile shell only for Safari/iPhone affordances.

## What Is Proven Live

- `https://voidscape.gg/play/` loads the TeaVM web client.
- The live server WebSocket path is now `wss://voidscape.gg/play/ws/`.
- Bare `/play/` now defaults to that subpath WSS endpoint when served from `https://voidscape.gg/play/`.
- Hosted static/deep manifest verification passed at:
  - `tmp/web-teavm-deployment-verify-play-default-endpoint-v1`
- Hosted login/chat smoke with explicit WSS passed at:
  - `tmp/web-teavm-hosted-only-chat-after-wss-fix-v1`
- Hosted login/chat smoke with no explicit `--ws` also passed. This proves clean `/play/` chooses the right default WSS endpoint:
  - `tmp/web-teavm-hosted-only-chat-default-endpoint-v1`
  - Screenshot: `tmp/web-teavm-hosted-only-chat-default-endpoint-v1/iphone-chat-diagnostics.png`
- The latest desktop `512x346` / mobile right-click placement/selection package is deployed on `/play/` and static/deep verified:
  - `scripts/package-web-teavm.sh --output-dir dist/web-teavm`
  - Manifest: `dist/web-teavm/voidscape-web-build.json`
  - Client version: `10120`
  - Generated at: `2026-06-26T00:58:07Z`
  - File count: `422`
  - Asset token: `cache-5f34d4087bf307f39cb6`
  - Manifest SHA-256: `6444b9af7171fec5ba530b7b2ec4fd835d6bf52a56fa086c52d4331209f88ecd`
  - `voidscape-web-client.js` SHA-256: `49363d8080708f29d143d44b0471c20efc446990208b451a24f076a50aeadada`
  - `Cache/video/Authentic_Sprites.orsc` SHA-256: `29cb58b5b3e4d2876fe0064bfdc416f395f97c7b8a27e80443207e3fe9ddddca`
- Hosted verifier artifact: `tmp/web-teavm-deployment-verify-10120-rightclick-menu-select-v1`
- Live backup before this deploy: `/opt/voidscape/backups/deploy-10120-rightclick-menu-select-pre-20260626T005830Z`
- Local deploy archive: `tmp/deploy/web-teavm-10120-rightclick-menu-select-20260626T005830Z.tgz`
- This package keeps the desktop navigation-keydown Java text-buffer guard and tiny desktop overlay safe-area clamping so custom bank, legacy bank, and `::onlinelist` title/close rows stay below the top HUD tabs. It also keeps the mobile long-press/right-click wobble tolerance, anchors the top mouse menu near the long-press point instead of forcing it down by chat, and converts a tap inside the opened menu into a normal left-click menu selection.
- Local right-click controls proof passed at `tmp/web-teavm-controls-rightclick-menu-select-v1`; focused local proof passed at `tmp/web-teavm-iphone-context-menu-local-v6`. The focused proof verifies a 250ms long press, secondary down `p,3`, release-clear `p,4`, `anchoredNearPress: true`, menu stability after release, and a tapped option row producing `selectEvents: ["m,256,506"]`.
- Live focused right-click proof passed at `tmp/web-teavm-iphone-context-menu-live-v2` against `https://voidscape.gg/play/` plus `wss://voidscape.gg/play/ws/`, proving the deployed client opens the menu near the press and accepts an option tap.
- Hosted focused key-buffer proof passed at `tmp/web-teavm-hosted-nav-keys-focused-v1`. It proved default `wss://voidscape.gg/play/ws/`, native non-stretched `512x346` desktop framebuffer/CSS canvas, PageUp/PageDown/ArrowLeft/ArrowUp/ArrowRight/ArrowDown queue `k,1`/`k,0` events only, and Java `chatMessageInput` / `inputTextCurrent` stay unchanged even when legacy printable keypresses (`!`, `"`, `%`, `&`, `'`, `(`) are dispatched afterward. A standalone printable `%` keypress still queues `t,37`.
- The latest broad hosted desktop smoke attempt at `tmp/web-teavm-hosted-desktop-nav-keydown-text-guard-v1` was blocked by the live `test` account still being logged in, so `tmp/web-teavm-hosted-desktop-arrow-legacy-percent-v1` remains the latest full logged-in desktop mouse/terrain proof from the immediately preceding package.
- The web client now appends the package asset token to `Cache/` resource URLs. This is required because nginx serves `Cache/` files with `Cache-Control: public, max-age=31536000, immutable`; the token ensures changed sprites/projectiles such as `Authentic_Sprites.orsc` are fetched even in browsers that cached the old URL.
- Normal local visual QA passed before deployment:
  - `tmp/web-teavm-visual-polish-pass-v5-final-normal`
  - Reviewed mobile portrait/landscape contact sheets show the `Normal` / `GFX off` buttons in the bottom-right account-button lane in portrait and left of the account button in landscape instead of over chat/XP text.
- Local-only forced-selector visual QA also passed before deployment:
  - `tmp/web-teavm-combat-selector-forced-visual-v4`
  - It proved the combat selector no longer blocks the top status lane and phone-landscape loose chat shifts beside it. The debug forcing was reverted and was not deployed.
- Earlier hosted focused chat/default-WSS smoke remains useful proof for the same shared path, but it predates the final visual-polish package:
  - `tmp/web-teavm-hosted-iphone-chat-shell-resource-v1`
- Earlier full hosted broad iPhone smoke also predates the final visual-polish package:
  - `tmp/web-teavm-hosted-iphone-broad-shell-resource-v2`
  - It proved real mobile-keyboard login, username preservation through Done/checkmark/password focus, portal handoff, canvas chat tabs, canvas top panels, Magic/Prayer scroll, `Aa` public chat, terrain movement, camera controls/drag/pinch, landscape fill with loose chat text, portrait return, and post-resume movement.
- Earlier compact side-panel local menu/contact-sheet visual QA passed at `tmp/web-teavm-menu-compact-side-panels-v2`; the current normal visual-polish pass is `tmp/web-teavm-visual-polish-pass-v5-final-normal`.
- Earlier live Mobile Safari Simulator orientation pass also passed against `https://voidscape.gg/play/`, but predates the final visual-polish package:
  - `tmp/web-teavm-simulator-shell-resource-live-v1`
  - Screenshots reviewed: `simulator-safari-portrait.png` and `simulator-safari-landscape-right-upright.png`.
- Latest local proof before this deploy:
  - Controls/resource/runtime/right-click smoke: `tmp/web-teavm-controls-rightclick-menu-select-v1`
  - Focused local right-click option-selection smoke: `tmp/web-teavm-iphone-context-menu-local-v6`
  - Focused live right-click option-selection smoke: `tmp/web-teavm-iphone-context-menu-live-v2`
  - Desktop mouse/keyboard smoke: `tmp/web-teavm-desktop-compact-side-panels-v1`
  - Local visual menu pass: `tmp/web-teavm-visual-polish-pass-v5-final-normal`
  - Local forced-selector visual pass: `tmp/web-teavm-combat-selector-forced-visual-v4`
- Important version gotcha: the packaged web client `CLIENT_VERSION` must match the live server's expected `client_version`. A prior temporary newer package was static-verified but rejected login with "Voidscape has been updated" because the server still expected an older version. The current live package/server pair is `10120`.
- Important package-permission gotcha: `scripts/package-web-teavm.sh` now normalizes staged directories to mode `755` and files to `644` before manifest generation. Keep this; otherwise cache files copied from restrictive local modes can produce hosted `403` responses even when local deep verification passes.

## Live Ops Fix Already Done

The earlier "unable to connect to server" report was real and had two causes:

1. The managed `voidscape` systemd service was not the process serving the game; an orphan root Java/Ant process was holding ports.
2. After switching back to systemd, the `voidscape` service user could not read Let's Encrypt cert/key files, so WebSocket TLS failed and `/play/ws/` returned 502.

Fix applied live:

- Orphan root Java process was stopped.
- `systemctl start/restart voidscape` was used.
- ACLs were added so user `voidscape` can read `/etc/letsencrypt/live/voidscape.gg/fullchain.pem` and `privkey.pem` through the archive symlink path.
- Default ACLs were added to the cert directory for renewals.
- Verified `https://voidscape.gg/play/ws/` returns `101 Switching Protocols`.

Do not redo this unless the connection breaks again. If it does break, first check `systemctl status voidscape`, `ss -ltnp`, nginx logs, and cert ACLs.

## Latest Live Compact Package

The user tested on a physical iPhone and reported:

> I loaded it, saw login screen, typed username, and it deleted username when I clicked password area.

The first local bridge fix moved Safari keyboard refocus from touch `pointerdown` until after the pointer release queued the shared game tap. The user then reported the username could still disappear when tapping out of username or pressing the iOS keyboard checkmark. The current deployed fix preserves the shared Java login panel text/focus across the web login panel rebuild that can happen during Safari keyboard/viewport changes.

The user then reported physical-phone issues: landscape Safari did not fill sideways, chat via `Aa` was not intuitive, Magic/Prayer rows were too tiny, the side `...` was wasting space, Low resource mode felt bad, side-opened menus were too tall, desktop mouse/keyboard needed to work, desktop default size needed one step larger, and then the larger native desktop pass still felt laggy while arrow keys typed weird characters into chat. The current live package fixes those in the shared Java/TeaVM path: phone landscape fills width, `Aa` enters public chat during plain gameplay, the side rail is `Inv` / `Aa` / `Mag` / `Pray` with no `...` or `Best`, side-opened Magic/Prayer use compact default-height lists with larger `Lv` text, Low is removed from player-facing controls, desktop/tablet use a shell with `Normal` / `GFX off` only, desktop is back to classic native non-stretched `512x346` to reduce TeaVM render/upload load, and desktop navigation keydowns are handled before Java text input so arrows/PageUp/PageDown rotate camera or set paging flags without writing `%`, `&`, etc. into chat.

- `Client_Base/src/orsc/mudclient.java`
  - Preserves existing login username, password, status text, and focused field when `createVoidscapeLoginPanels()` rebuilds the login panels.
  - Exposes web-only diagnostics for login username text, password length, status text, and login field focus.
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
  - Keeps the earlier post-pointerup keyboard refocus behavior.
  - Publishes `window.__voidscapeLoginState` without exposing the password, only password length.
  - Adds the phone-landscape fill-width sizing branch and queues `c,compose-main` when `Aa` is tapped during plain gameplay.
  - Binds `.mobile-quick-button` controls to existing shared `u,<panel>` / side-panel events for inventory, magic, and prayer.
  - Keeps pending desktop click/down state intact during zero-button hover moves after pointer release so terrain movement works with a mouse.
  - Publishes side-panel state so phone `Inv`/`Mag`/`Pray` quick buttons can stay visible while their side-opened shared panel is active.
  - Adds a short `__voidscapeKeyboardClosingUntil` viewport-freeze grace after keyboard blur.
  - Uses a native `512x346` desktop framebuffer, a faster `Uint32Array` canvas upload path, suppresses legacy printable keypresses that follow navigation keydowns, and handles navigation keydowns before `handleKeyInput(...)` so Java text buffers do not receive arrow/Page key character codes.
  - Appends `window.__voidscapeAssetToken` to `Cache/` resource URLs to break immutable browser cache after sprite/cache changes.
- `Web_Client_TeaVM/src/main/webapp/index.html`
  - Shows the new login diagnostics when diagnostics are enabled.
  - Adds the phone-only `Inv`, `Mag`, and `Pray` side rail controls around `Aa`, hidden over world-map/blocking-dialog states and in phone landscape/tablet/desktop.
  - Does not expose a side `Best` button.
  - Adds the desktop/tablet `Voidscape` page shell with runtime label and `Normal` / `GFX off` render controls.
  - Publishes quick-button, keyboard-close, shell, and resource-control diagnostics.
  - Publishes the packaged cache asset token used by `openCacheResource(...)`.
- `Client_Base/src/orsc/graphics/gui/Panel.java`
  - Supports optional enlarged touch row height for scrolling lists while preserving normal desktop row rendering.
- `Client_Base/src/orsc/mudclient.java`
  - Uses compact side-opened Magic/Prayer panel geometry with larger text, short `Lv` labels, and default-height visible lists.
  - Adds `ui.phoneLandscapeLooseChat` / `voidscapeUsePhoneLandscapeLooseChat()` and uses it to hide the large chat frame/background in phone landscape while keeping portrait framed chat unchanged.
- `scripts/smoke-web-teavm-iphone.sh`
  - Login smoke now tests username entry, blur/checkmark/viewport-resize behavior, tapping the password field, typing password, and preserving username.
  - Post-login smoke now verifies tapping `Aa` in plain gameplay queues `c,compose-main`, focuses public chat, types a real chat message, records landscape fill sizing, and asserts `ui.phoneLandscapeLooseChat` true in landscape then false after portrait return.
- `scripts/smoke-web-teavm-iphone-controls.sh`
  - Replaced the old landscape letterbox surface check with `landscapeFill`, proving a 932x430 phone landscape viewport gets a `750x346` framebuffer and no side gutters.
  - Proves `u,magic` and `u,prayer` from the side rail, phone-only visibility, no side `Best` button, shared-panel hiding, world-map/dialog hiding, and keyboard-close viewport grace.
- `scripts/smoke-web-teavm-desktop.sh`
  - Proves desktop runtime mode has no phone rail, shell render buttons switch modes without queuing game pointer events, `GFX off` shows the overlay, native `512x346` desktop rendering is active, real mouse input reaches the shared client when login is available, and navigation keys do not enqueue browser text events or mutate Java text buffers.
- `scripts/screenshot-web-teavm-menus.sh`
  - Captures phone portrait, phone landscape, tablet, and desktop contact sheets for the HUD, side Mag/Pray, every shared panel, and every shared chat tab.

Validation completed and deployed:

- `bash -n scripts/smoke-web-teavm-iphone-controls.sh scripts/smoke-web-teavm-desktop.sh scripts/smoke-web-teavm-iphone.sh scripts/screenshot-web-teavm-menus.sh` passed.
- `scripts/build-web-teavm-spike.sh` passed.
- `scripts/package-web-teavm.sh --skip-build --output-dir dist/web-teavm` passed and currently produces manifest SHA-256 `187cd30f0540c9e2fd585c8f603e64d65897adab014c35fad5b848169d6b11fd` with asset token `cache-5f34d4087bf307f39cb6`.
- Local controls smoke passed at `tmp/web-teavm-controls-rightclick-wobble-v1`, including runtime-mode/resource behavior, compact side-panel checks, and the right-click phone-scale thumb wobble case.
- Desktop mouse/keyboard smoke passed at `tmp/web-teavm-desktop-compact-side-panels-v1`.
- Normal local visual menu screenshot pass passed at `tmp/web-teavm-visual-polish-pass-v5-final-normal` across phone portrait, phone landscape, tablet, and desktop; reviewed render controls now sit beside the account button in portrait and left of it in landscape instead of over chat/XP text.
- Local-only forced combat-selector screenshot pass passed at `tmp/web-teavm-combat-selector-forced-visual-v4`; reviewed combat selector lower placement, loose landscape chat avoidance, and diagnostics camera pad/status separation. The debug force was reverted before deploy.
- The package was uploaded to live `/var/www/html/play/`.
- Hosted static/deep verification for the current desktop `512x346`/cache-token/navigation-keydown/overlay-safe/right-click-wobble package passed at `tmp/web-teavm-deployment-verify-10120-rightclick-wobble-v1`.
- Hosted focused key-buffer proof passed at `tmp/web-teavm-hosted-nav-keys-focused-v1`, proving native `512x346`, default `wss://voidscape.gg/play/ws/`, PageUp/PageDown/arrows plus legacy printable keypresses without Java text-buffer mutation, and a standalone `%` still queues text.
- Hosted broad desktop smoke for this exact package was blocked at login because the live `test` account is already logged in; artifact `tmp/web-teavm-hosted-desktop-nav-keydown-text-guard-v1`. The previous package's `tmp/web-teavm-hosted-desktop-arrow-legacy-percent-v1` remains the latest full logged-in desktop mouse/terrain proof.
- Hosted focused chat/default-WSS smoke passed earlier at `tmp/web-teavm-hosted-iphone-chat-shell-resource-v1`; it remains useful history but was not rerun after the user asked to stop screenshot passes and test live directly.
- The hosted visual menu screenshot rerun was canceled when the user asked to stop screenshot passes and test live directly.

Validation not yet completed:

- The fix has not been verified on the user's physical iPhone. Automated hosted proof is green, but real Mobile Safari/Home Screen touch feel and keyboard behavior still need user-side confirmation.
- Fresh deployed physical-QA template for the current live URL is `tmp/iphone-web-qa-live-shell-resource-current/iphone-safari-qa-report.md`.

## Latest Live Compact Side-Panel UI

The user asked for a final polish pass after the live package: remove Low from player controls, revert the tall Magic/Prayer side-panel feel, remove the side `...`, keep Mag/Pray buttons visible while their side panels are open, connect side-opened panels to the side button, add side `Inv`, move NPC/rift options above the chat box, hide side quick panel buttons in landscape, make spell/prayer usage feel more intuitive, and show current/max Hits and Prayer under FPS. This is implemented, validated, and deployed to live `/play/`.

- Live package: `dist/web-teavm`
- Package manifest: `clientVersion: 10120`, `fileCount: 422`
- Build: `scripts/build-web-teavm-spike.sh` passed.
- Broad iPhone smoke: `tmp/web-teavm-iphone-ui-polish-v3`
- Controls smoke: `tmp/web-teavm-controls-compact-side-panels-v1`
- Desktop mouse/keyboard smoke: `tmp/web-teavm-desktop-compact-side-panels-v1`
- Menu/contact-sheet visual QA: `tmp/web-teavm-menu-compact-side-panels-v2`, with reviewed `contact-mobile-portrait.png`, `contact-mobile-landscape.png`, `contact-tablet.png`, and `contact-desktop.png`.

Live behavior now includes:

- Phone gameplay shows compact `Normal` and `GFX off` resource controls; login/non-game screens hide them, and legacy Low aliases normalize to normal.
- The side rail is `Inv`, `Aa`, `Mag`, `Pray`; the old `...` button is gone and there is still no side `Best`.
- `Inv`, `Mag`, and `Pray` open side-connected shared Java panels with a short connector arrow and remain visible while that side-opened panel is active.
- Phone landscape hides `Inv`, `Mag`, and `Pray` because the top HUD tabs are close enough there; tablet/desktop still have no phone rail.
- Side-opened Magic/Prayer panels use a compact default-height list with larger text and short `Lv` labels instead of a tall 20-row list.
- NPC/rift/context option menus are placed above the chat box on mobile.
- Mobile spell selection prompts for tapping a target instead of leaving the player guessing.
- The FPS overlay in mobile game view now includes current/max Hits and Prayer on separate lines.

## Immediate Next Steps

## Objective Checklist

- [x] Keep the shared TeaVM/Java renderer; no separate JavaScript renderer.
- [x] Fix live `/play/` default WSS endpoint and keep clean `/play/` on `wss://voidscape.gg/play/ws/`.
- [x] Keep package client version live-compatible; current live package/server pair is `10120`.
- [x] Preserve username across username -> blur/Done/checkmark -> password field in automated smoke.
- [x] Fill phone landscape width by widening the shared framebuffer instead of leaving side gutters.
- [x] Suppress the large framed chat box in phone landscape and draw loose OpenRSC-style chat text.
- [x] Make `Aa` enter public chat during plain gameplay, while leaving it as a keyboard focus helper when a menu/modal/panel owns input.
- [x] Add phone-only `Mag` and `Pray` side shortcuts, remove the temporary side `Best`, and keep Bestiary/Loot on the shared Java player-info/top-panel path.
- [x] Locally restore Magic/Prayer list row spacing to the original shared-client spacing after the larger-row pass caused layout issues.
- [x] Hide phone-only controls on desktop/tablet; add desktop/tablet page shell and render controls.
- [x] Add AFK `GFX off` mode and expose only `Normal` / `GFX off` in desktop/tablet shell and phone gameplay.
- [x] Remove the side `...` button and add side `Inv`.
- [x] Connect side-opened `Inv` / `Mag` / `Pray` panels to their side button with a short connector.
- [x] Move mobile NPC/rift option menus above the chat box.
- [x] Hide side quick panel buttons in phone landscape.
- [x] Add mobile spell tap-target messaging.
- [x] Add current/max Hits and Prayer below FPS on separate lines.
- [x] Make side-opened Magic/Prayer compact with larger text instead of a tall 20-row list.
- [x] Prove desktop mouse movement on live `/play/`.
- [x] Screenshot every shared menu/chat tab locally after iteration; stop further screenshot passes unless the user asks for them again.
- [x] Add Mobile Safari Simulator visual evidence for live `/play/` portrait/landscape behavior.
- [ ] Physical iPhone Safari/Home Screen retry and QA report.
- [x] Full hosted broad iPhone smoke/terrain-movement follow-up.
- [ ] Final release audit after physical QA evidence exists.

Ask the user to retry on physical iPhone:

- Open `https://voidscape.gg/play/?endpoint=reset`
- Tap Existing User if needed.
- Tap the username field or `Aa`, type username.
- Tap outside username or press the iOS keyboard checkmark/Done, confirm username stays visible.
- Tap password field, confirm username stays visible.
- Type password, submit.
- Rotate to landscape in game, confirm the game fills the sideways viewport instead of keeping side gutters.
- While in plain gameplay with no panel/menu/modal open, tap `Aa`; it should enter public chat directly, so typed text sends as normal chat.
- Tap `Inv`, `Mag`, and `Pray` on the side rail; each should open a connected shared panel, keep the side button visible while side-opened, and Magic/Prayer should show a compact default-height list with larger text.
- Confirm there is no side `Best` button; open Bestiary/Loot through the shared Java player-info/top-panel path instead.
- Confirm NPC/rift option menus appear above chat and the mobile FPS overlay shows current/max Hits and Prayer.
- In portrait, type with the keyboard, close it with Done/checkmark or by tapping out, and confirm the game does not visibly shrink/bounce during keyboard dismissal.
- On desktop/tablet, confirm the page shell is visible and the `Normal` / `GFX off` buttons are outside the game canvas; on phone, confirm compact resource controls appear only after entering gameplay.

2. Run the final release audit only after the physical iPhone QA report exists.

## UI Direction

The user originally considered OSRS-like mobile UI, then clarified the near-term goal:

- Do not build a totally different OSRS-style mobile HUD yet.
- First mimic the Android app behavior: same game, same shared Java HUD, same panels/chat/map state.
- Then the user will test on a real phone and decide what should move.
- Avoid duplicate HUD owners. The browser shell should not duplicate inventory/chat/world-map/account UI that the Java client already owns.

Known ugly current UI:

- Browser helper buttons (`...`, `Aa`, diagnostics `i`, `copy`) are visually too loud in some modal/login states.
- Diagnostics should be hidden for normal players. If `diag=1` was saved in Safari, use `?diag=0` to clear it.
- The screenshot from hosted smoke still showed a character-design modal with large visible helper buttons. This is not final UI quality.

## Product Questions Already Handled

The user asked:

> Can we make it so the page detects if you're on desktop / tablet or mobile and have it act accordingly?

Implemented, packaged, deployed, and static-verified after the login-tap validation:

- `index.html` now resolves one `runtimeMode` object with `mode: "desktop" | "phone" | "tablet"`.
- Detection order is explicit URL override first (`?mode=phone|tablet|desktop`, `?phone=1`, `?tablet=1`, `?desktop=1`, and legacy `?mobile=1/0`), then user-agent / pointer / touch / viewport heuristics.
- Only `phone` enables the Android-parity mobile Safari shell and Java Android profile, so desktop no longer shows phone-only helpers by default. Current local source uses `Aa` plus portrait `Inv` / `Mag` / `Pray`; the old side `...` is gone locally.
- `tablet` is deliberate but conservative for now: it is named in diagnostics and classes, hides phone-only controls, shows the same page shell/resource controls as desktop, and can be tuned after iPad testing.
- Diagnostics now include `runtimeMode`, `touchProfile`, `tabletProfile`, shell control rectangles, and resource-control state.
- Local validation originally passed at `tmp/web-teavm-controls-runtime-mode-v2` and `tmp/web-teavm-iphone-runtime-mode-login-v1`; the latest shell-resource package retained this behavior and passed `tmp/web-teavm-controls-shell-resource-v1`.
- Hosted static/deep verification for the latest package passed at `tmp/web-teavm-deployment-verify-shell-resource-static-v1`.
- Hosted desktop shell/resource/mouse smoke for the latest package passed at `tmp/web-teavm-hosted-desktop-shell-resource-v1`.
- Hosted focused chat/default-WSS smoke for the latest package passed at `tmp/web-teavm-hosted-iphone-chat-shell-resource-v1`.
- Hosted visual menu QA for the latest package passed and was reviewed at `tmp/web-teavm-hosted-menu-visual-shell-resource-v1` across phone portrait, phone landscape, tablet, and desktop.

The user then showed physical-phone screenshots and asked for landscape to fill the sideways screen and for `Aa` to type into chat when clicked into the main game:

- Phone landscape now computes a wider shared Java framebuffer from viewport aspect at full height `346`, so Mobile Safari fills the width without side gutters.
- `Aa` now queues `c,compose-main` only during plain gameplay with no menu/modal/map/panel open; Java validates the same guard and focuses public chat in the real shared chat panel.
- Local landscape/chat proof first passed at `tmp/web-teavm-controls-landscape-chat-v1` and `tmp/web-teavm-iphone-landscape-chat-v2`; the latest shell-resource package retains those guarantees, and live static/deep plus hosted focused chat proof are the latest artifacts above.

Likely files:

- `Client_Base/src/orsc/mudclient.java`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientMain.java`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `scripts/smoke-web-teavm-iphone-controls.sh`
- `scripts/smoke-web-teavm-iphone.sh`
- deployment verifier scripts if they assert mobile classes
- `docs/subsystems/iphone-web-client.md`
- `docs/DIVERGENCE.md`

## Simulator Notes

Use the existing helper, not ad hoc simulator commands:

```bash
scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --orientation-matrix --out tmp/web-teavm-simulator-next
```

Earlier direct `xcrun simctl list devices available` failed inside sandbox with `CoreSimulatorService connection became invalid` / permission errors. The simulator helper may need escalated execution from Codex. The helper is still the right path because it opens Mobile Safari, captures screenshots, writes `simulator-run.json`, screenshot checks, and HTTP asset checks.

## Broad Remaining Gates

- Verify the username preservation fix on a physical iPhone, including username -> blur/checkmark/Done -> password tap.
- Verify phone landscape fill, loose landscape chat text, hidden landscape side quick buttons, and `Aa` public-chat compose on a physical iPhone in Mobile Safari/Home Screen mode.
- Verify the compact side-panel UI on physical iPhone against live `/play/`.
- Run physical iPhone Safari/Home Screen QA.
- Run final release audit only after hosted verifier and physical QA report exist.

## Prompt For The Next Chat

Paste this into a fresh Codex chat:

```text
We are continuing the Voidscape iPhone web client work in /Users/s/Desktop/voidscape-github-latest. First read AGENTS.md, memory/iphone_web_client.md, docs/subsystems/iphone-web-client.md, docs/OPERATIONS.md around iPhone web deployment, and docs/iphone-web-client-next-chat-context.md. Do not restart from scratch and do not build a separate JS renderer. The live /play connection issue is fixed and clean https://voidscape.gg/play/ defaults to wss://voidscape.gg/play/ws/. The latest live package is client version 10120 with manifest SHA-256 db120b87be882c97be4762a2206fd337269761dfcd6619d41dc7b7a6a81f44dc, generated at 2026-06-25T23:53:52Z, fileCount 422, asset token cache-5f34d4087bf307f39cb6, voidscape-web-client.js SHA-256 5e156be0f61b4e23e8dfdd853218bd24d429737455e1c1316878962538069ae7, and Cache/video/Authentic_Sprites.orsc SHA-256 29cb58b5b3e4d2876fe0064bfdc416f395f97c7b8a27e80443207e3fe9ddddca. It is static/deep verified at tmp/web-teavm-deployment-verify-10120-desktop-overlay-safe-v1; live backup before that deploy is /opt/voidscape/backups/deploy-10120-desktop-overlay-safe-pre-20260625T235501Z. The last hosted focused key-buffer proof remains tmp/web-teavm-hosted-nav-keys-focused-v1, proving native non-stretched desktop 512x346, default wss://voidscape.gg/play/ws/, PageUp/PageDown/arrow key events without Java chatMessageInput/inputTextCurrent mutation, and standalone `%` text input still queues as text. The broad hosted desktop smoke for the current/near-current package remains blocked by the live test account already being logged in; tmp/web-teavm-hosted-desktop-arrow-legacy-percent-v1 remains the latest full logged-in desktop mouse/terrain proof from the prior package. Cache resources get the package asset token appended in WebClientPort.openCacheResource, which is required because live Cache/ responses are immutable for a year; this forces browsers to fetch changed sprite/projectile archives. Current live behavior: desktop/tablet shell with Normal/GFX off only, desktop default native 512x346 for lower TeaVM render/upload load after the larger native pass felt laggy, tiny desktop overlay safe-area clamping so custom bank/legacy bank/::onlinelist close rows stay below the top HUD tabs, phone in-game Normal/GFX off controls only, no Low button, render buttons in the bottom-right account-button lane in portrait and left of the account button in landscape, side Inv/Aa/Mag/Pray with no side ... or Best, side-connected panels reserved farther left from the icon rail, tapping the active side icon closes its panel, compact side-opened Magic/Prayer lists with larger Lv text instead of tall 20-row lists, side Mag becomes a selected-spell shortcut after selecting a spell from the regular spellbook, side quick buttons hidden in landscape, mobile long-press/right-click release does not immediately close the context menu, mobile combat selector and options above the lower chat area, phone-landscape loose chat shifts beside the combat selector when it is visible, mobile spell tap-target messaging plus empty target-click cancel, and current/max Hits plus Prayer under FPS. Remaining gates are physical iPhone retry of username -> blur/checkmark/Done -> password field, physical iPhone landscape fill/loose-chat/chat-compose/side controls/Magic-Prayer/combat-selector feel, keyboard-close bounce, desktop bank/onlinelist close reach on live testers' browsers, physical Safari/Home Screen QA, and final release audit. Use scripts, keep docs updated, and clearly separate proven-live from local-only changes.
```
