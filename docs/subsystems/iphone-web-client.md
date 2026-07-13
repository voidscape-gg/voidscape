# iPhone Web Client

This page is the durable handoff for the TeaVM iPhone Safari client. It captures the current state, the product direction, and the next technical slices so context compaction does not lose the important decisions.

## Current Position

Launch scope decision (revised 2026-07-12): iPhone Safari is a supported July 18 browser option through `https://voidscape.gg/play/`. The owner accepts that it works well enough for release despite imperfect phone polish. Physical iPhone Safari/Home Screen QA and the final iPhone audit remain **OWNER-WAIVED / NOT PASSED**; the decision to ship does not turn existing local, Simulator, or hosted evidence into physical-device proof. This is web-client support only—there is no native iOS application or App Store claim.

As of 2026-06-27, the iPhone web client is roughly 90% of a player-ready local beta. The hard renderer/protocol question is solved: the browser build uses the shared `Client_Base` Java client through TeaVM, reaches the real Voidscape login/game path, uses WebSockets for the normal server protocol, loads the Voidscape art/custom HUD assets, has local smoke, packaging, simulator, and release-audit tooling, and is now pivoted to an Android-parity mobile baseline. Phone portrait keeps the Android-like `512` logical width, while phone landscape deliberately widens the shared Java framebuffer from the viewport aspect at classic full height `346` so Mobile Safari fills the screen width instead of leaving side gutters. Desktop and tablet runtime modes stay separate branches, do not show phone-only helpers by default, and now get a small page shell around the shared canvas for runtime labeling plus render-resource controls. The remaining work is mostly mobile product quality: real-device Safari/Home Screen QA, iPhone-safe layout polish, and physical-play feel fixes.

The web shell now resolves an explicit runtime mode before starting the shared client: `desktop`, `phone`, or `tablet`. URL overrides win first (`?mode=phone|tablet|desktop`, `?phone=1`, `?tablet=1`, `?desktop=1`, with legacy `?mobile=1/0` preserved), then heuristics use user agent, pointer type, touch support, and viewport size. Only `phone` enables the Android-parity mobile Safari shell and Java Android profile; `desktop` and the initial deliberate `tablet` mode keep phone-only helpers hidden by default and show the desktop/tablet shell with `Normal` and `GFX off` render controls. Diagnostics publish `runtimeMode`, `touchProfile`, `tabletProfile`, `playShell`, and `resourceModeControl`.

Production launch evidence now tracks the deployed `/play/` package at protocol `10132` in `docs/LAUNCH-CRITICAL-CHECKLIST.md`; use that checklist rather than the dated deployment snapshots below for current hashes. Historically, Slice 8B first coordinated the TeaVM release source at `10132` before it was hosted. Its focused real-WebSocket proof at `tmp/web-teavm-cracker-campaign-10132` logged in through the normal protocol path, received the campaign snapshot `1000`, observed owner broadcasts `999`, `1`, and `0`, rendered the exact plural/singular labels, hid zero, and recorded no campaign metadata in chat. A separate compiled-parser check then proved five malformed envelopes fail closed to hidden without chat leakage. The accepted screenshots are `network-login-1000.png`, `network-broadcast-999.png`, `network-broadcast-1.png`, `network-broadcast-0.png`, and `local-malformed-hidden.png`; `summary.json` binds them to the `10132` run. The welcome-modal retry seen while stabilizing the run was a harness timing race, not a renderer or protocol change.

The June 27 `/play/` deployment snapshot used client version `10120`, generated at `2026-06-27T15:12:52Z`, with `fileCount: 421`, asset token `cache-356134644e1c926b5ee2`, manifest SHA-256 `004a449a721d370e5107259eefbfbba79173c2a7d9efcc7858e93479a72353f7`, and `voidscape-web-client.js` SHA-256 `95654226d5df9cf78a9d7575bca61fbcd31204f7ae84ece2b71e52bde1b4f0af`. The live backup before that deploy is `/opt/voidscape/backups/play-iphone-inventory-pre-20260627T151304Z.tgz`; hosted static/deep verification passed at `tmp/web-teavm-deployment-verify-iphone-inventory-fix` with all 421 manifest files verified and direct live manifest/JS hashes matching the local package. That package kept clean `/play/` on `wss://voidscape.gg/play/ws/`, preserved the shared Java renderer/protocol, carried the prelaunch mobile existing-user login ergonomics, and fixed iPhone Safari inventory taps by allowing small finger jitter inside open panels to still produce a release click unless a real scroll gesture was detected. The shared Java client also kept the inventory menu/action executor active while the Voidscape HUD marked the pointer inside the inventory panel. Authenticated hosted smoke was not rerun for that inventory deploy because the available default/local QA credentials were not valid in that session; the dated local no-login controls smoke and hosted static/deep deployment verification remain historical evidence.

The previous `/play/` package before the inventory-tap deploy was client version `10120`, generated at `2026-06-27T04:43:06Z`, with `fileCount: 421`, asset token `cache-356134644e1c926b5ee2`, manifest SHA-256 `ed5258a9582536093b29f86b12089c0ec8edeab4dc89e2c898e37e0b0b5a0212`, `voidscape-web-client.js` SHA-256 `8eedd1d267f79eb5ef45ed18dadc085580cebdb347eb79658b8676aa61d05502`, and `Cache/video/Authentic_Sprites.orsc` SHA-256 `29cb58b5b3e4d2876fe0064bfdc416f395f97c7b8a27e80443207e3fe9ddddca`. Static/deep hosted verification plus hosted iPhone smoke passed at `tmp/web-teavm-deployment-verify-prelaunch-mobile-login-fields-smoke-v1`; standalone hosted iPhone smoke passed at `tmp/web-teavm-hosted-prelaunch-mobile-login-fields-smoke-v2`; the live backup before that deploy is `/opt/voidscape/backups/play-prelaunch-mobile-login-fields-pre-20260627T044345Z`. That package kept the shared Java renderer/protocol and changed only Android/web-mobile existing-user login ergonomics: the mobile login frame was taller, username/password rows were farther apart, and both fields used 31px tap height while desktop kept the previous geometry. Visual marker QA proved a lower perceived username tap still edited the username and a password tap edited the password. The hosted smoke proved account and recovery portal handoffs to `https://voidscape.gg/#account` and `https://voidscape.gg/#security`, logged in live, recorded zero failed requests/unexpected console output, and proved post-resume input. Fresh accounts start in the Void Council intro at `(24,37)`; while that intro intentionally blocks terrain movement until the player speaks to the council, the smoke treats queued terrain taps plus post-resume input as valid onboarding proof instead of a movement regression.

The previous `/play/` package before the prelaunch mobile-login-field deploy was client version `10120`, generated at `2026-06-26T00:58:07Z`, with `fileCount: 422`, asset token `cache-5f34d4087bf307f39cb6`, manifest SHA-256 `6444b9af7171fec5ba530b7b2ec4fd835d6bf52a56fa086c52d4331209f88ecd`, `voidscape-web-client.js` SHA-256 `49363d8080708f29d143d44b0471c20efc446990208b451a24f076a50aeadada`, and `Cache/video/Authentic_Sprites.orsc` SHA-256 `29cb58b5b3e4d2876fe0064bfdc416f395f97c7b8a27e80443207e3fe9ddddca`. Static/deep hosted verification passed at `tmp/web-teavm-deployment-verify-10120-rightclick-menu-select-v1`; the live backup before this deploy is `/opt/voidscape/backups/deploy-10120-rightclick-menu-select-pre-20260626T005830Z`. That package kept desktop mode at the native non-stretched `512x346` framebuffer/CSS canvas, retained the desktop navigation-key text guard, kept the tiny-desktop overlay safe area for custom bank, legacy bank, and `::onlinelist`, and made Android-style mobile long-press/right-click use CSS-pixel movement tolerance so normal phone thumb wobble no longer cancelled the context menu before it fired. It also fixed the physical-phone context follow-up: the top mouse menu opened near the long-press point, survived release, and taps inside the menu activated a normal menu row. Local controls proof passed at `tmp/web-teavm-controls-rightclick-menu-select-v1`; focused local proof passed at `tmp/web-teavm-iphone-context-menu-local-v6`; live focused proof passed at `tmp/web-teavm-iphone-context-menu-live-v2` with `anchoredNearPress: true` and `selectEvents: ["m,256,506"]`. The last direct hosted key-buffer proof remains `tmp/web-teavm-hosted-nav-keys-focused-v1`, proving PageUp/PageDown/arrow keydowns no longer mutate Java `chatMessageInput` or `inputTextCurrent` even when a browser follows with legacy printable keypress codes such as `%`; a standalone printable `%` keypress still queues text. `tmp/web-teavm-hosted-desktop-arrow-legacy-percent-v1` remains the latest full logged-in desktop mouse/terrain proof from the prior package. The web package stamps cache-resource URLs from packaged `Cache/` contents, so immutable browser-cached assets such as `Authentic_Sprites.orsc` refresh when sprites/projectiles change. Normal local menu/contact-sheet visual QA passed earlier at `tmp/web-teavm-visual-polish-pass-v5-final-normal`. A local-only forced-selector visual pass at `tmp/web-teavm-combat-selector-forced-visual-v4` proved Java-side combat-style selector placement now uses the lower chat area instead of the top status lane on mobile and that phone-landscape loose chat shifts beside it; the debug forcing was reverted before the deployed build.

Current live phone behavior: phone gameplay shows compact `Normal` and `GFX off` controls only after entering gameplay, hides them on login/non-game screens, places them beside the account button instead of over chat/XP text, removes the side `...`, and uses portrait side `Inv` / `Aa` / `Mag` / `Pray` with no side `Best`. Phone landscape/tablet/desktop hide the side panel shortcuts. `Inv`, `Mag`, and `Pray` open shared Java panels through side-specific events, keep the top HUD tab path intact, reserve extra room away from the side rail, and draw a short connector toward the side button. Tapping the active side rail button again closes the panel. Side-opened Magic/Prayer panels now use a compact default-height list with larger text and short `Lv` labels instead of showing a tall 20-row spell/prayer list. Mobile combat-style and NPC/rift option menus use the lower chat area instead of the top status lane, mobile spell selection gives a tap-target prompt, plain empty target clicks clear selected spells on web mobile, and the mobile FPS overlay draws current/max Hits and Prayer on separate lines. Legacy low-resource requests normalize to `normal`; the player-facing Low button is intentionally removed.

Account creation/recovery on `/play/` is portal-first. The shared Java client emits `voidscape:account` and `voidscape:recovery` from `osConfig`; `index.html` resolves those sentinels through query/global portal settings before opening the account manager. Release `/play/` should therefore route Create Account to the launch-signup account manager rather than packet registration, while desktop Java and native Android use in-client username/password character creation.

The owner has accepted launch with the following external gates still open. Keep them visibly **not passed** and complete them after launch; do not use the launch-support decision as substitute evidence:

- Physical iPhone Safari/Home Screen QA from a real device, with filled iPhone model, iOS version, network, tester, Home Screen, and explicit external-keyboard yes/no fields. A paired hardware keyboard Escape pass is useful extra evidence, but it is not required for the iPhone mobile baseline.
- Final release audit with `scripts/check-web-teavm-iphone-final-release.py ...` after the physical QA report exists. The latest live package currently has static/deep hosted verification, local right-click/menu-select controls proof, live focused right-click option-selection proof, hosted focused key-buffer proof, normal local visual contact-sheet proof, and local-only forced combat-selector visual proof. The previous package remains the latest full logged-in hosted desktop mouse/terrain proof because the current broad smoke is blocked by the live QA account being already logged in. Older hosted chat, broad iPhone, Simulator, and menu-contact-sheet artifacts remain useful shared-path history, but they predate the final visual-polish package.

## Product Direction

The right near-term mobile direction is Android-app parity on iPhone Safari, not an OSRS-style redesign yet and not a separate renderer.

Keep:

- The shared Java renderer.
- Shared server protocol/opcodes.
- Shared gameplay state, pathing, panels, chat history, inventory, bank, map, and settings logic.
- Shared desktop/Android compatibility.

Change for iPhone/mobile presentation after Android parity is proven on real devices:

- Put the world canvas first, with mobile controls arranged around it.
- Keep the shared canvas top tabs and canvas chat tabs as the current phone-facing baseline because that matches the Android app's same-game-on-a-different-platform model.
- Keep the DOM `UI` drawer and DOM chat tray hidden as fallback/debug wiring, not as the visible production HUD.
- Treat world map as a mobile-first full-screen modal with Android-parity drag/tap-to-walk/search/zoom controls. iPhone pinch is an extra Safari affordance mapped into the same shared map zoom path, not a separate map implementation.
- Keep 44px-class touch targets and safe-area-aware layout.
- Preserve the Voidscape visual identity; only rearrange after physical iPhone play shows concrete pain points.

The mobile HUD goal is avoiding duplicate owners, not stacking another HUD over the canvas. The real shared Java client remains the owner of inventory, chat history, side panels, minimap/world map, account manager, settings, and gameplay state. The browser shell owns only phone-specific affordances: safe-area layout, soft-keyboard focus, browser Back behavior, diagnostics, and Safari-specific keyboard/context helpers. The current iPhone shape keeps the canvas top tabs and canvas chat tabs authoritative; the old DOM drawer/chat tray and the OSRS-style bottom dock experiment are superseded.

Do not repeat the previous failed approach of recreating the game with separate JavaScript rendering/assets. The viable approach is shared renderer plus mobile shell.

Canonical visual artifacts for the current direction:

- Current normal portrait: `tmp/web-teavm-iphone-release-preflight/login-smoke/iphone-portrait-clean.png`
- Current normal landscape: `tmp/web-teavm-iphone-release-preflight/login-smoke/iphone-landscape-clean.png`
- Historical hosted broad live phone proof before the final visual-polish package: `tmp/web-teavm-hosted-iphone-broad-shell-resource-v2/iphone-portrait-clean.png` and `iphone-landscape-clean.png`
- Historical hosted menu visual pass before the final visual-polish package: `tmp/web-teavm-hosted-menu-visual-shell-resource-v1/contact-mobile-portrait.png`, `contact-mobile-landscape.png`, `contact-tablet.png`, and `contact-desktop.png`
- Current world-map proof set: `tmp/web-teavm-iphone-release-preflight/world-map-smoke/`
- Current Simulator proof set: `tmp/web-teavm-iphone-release-preflight/simulator/`

Ignore older bottom-dock or mobile-drawer screenshots such as `tmp/iphone-ui-inventory-20260624/iphone-current-ui-portrait.png` when making near-term UI decisions. Those artifacts document the rejected OSRS-style/mobile-shell experiment, not the current Android-parity baseline.

## Immediate Blockers

Before a larger mobile HUD redesign, keep these shared-path areas under active QA:

1. In-game chat must feel reliable on real iPhone Safari/Home Screen, not just Chrome iPhone emulation; the local tray and send proofs are green.
2. World map must stay reliable across real touch, pinch, keyboard search, close/reopen, and orientation changes.
3. World walker/autowalk from world-map tap must be exercised on physical iPhone against a live server.
4. The canvas top tabs, canvas chat tabs, Safari `Aa` keyboard helper, and Safari context helper must be exercised on physical iPhone for thumb reach, orientation changes, and Home Screen behavior.

The local automated gates now prove chat, world map, and world walker on the shared TeaVM client path. They do not replace real iPhone Safari/Home Screen QA.

## Android-Parity Audit Snapshot

This is the current source-of-truth comparison for keeping iPhone Safari aligned with the Android app before any phone-specific redesign.

### Desktop UI Parity Checklist

| Desktop UI feature | `/play` phone state | Android APK state | Notes |
|---|---|---|---|
| Void Glass bank, deposit-all, and loadouts | Code fixed and smoke-proven through shared `BankInterface`: phone mode reaches the `C_CUSTOM_UI` Void Glass renderer, copied diagnostics publish `ui.bankOpen`, `ui.bankRenderer: "voidGlass"`, and live `ui.bank` geometry while the bank is open; `scripts/smoke-web-teavm-iphone.sh --only-bank --out tmp/mobile-qc-web-bank-current` passed search/clear, scroll, withdraw, deposit, deposit-all, loadout save/load, Close, and fixture restore. Physical/Home Screen bank proof is still required. | Code fixed and smoke-proven through shared `BankInterface`: native Android uses the same Void Glass renderer when `want_custom_ui:true`, `want_custom_banks:false`, and `want_bank_presets:true`; `scripts/android-smoke.sh --only-auth-bank --out tmp/mobile-qc-android-bank-current` passed with `renderer=voidGlass`, loadout save/load, deposit, deposit-all, Close, and fixture restore. | `want_custom_banks:false` remains the launch config. The older legacy `CustomBankInterface` renderer is only for non-custom-UI legacy presets and is not required for presets/loadouts. |
| Mobile bank search/clear/scroll ergonomics | Code fixed for phone mode with a mobile-only clear target in the Void Glass search field, larger Close/action/loadout controls, taller mobile menu rows, and TeaVM high-level touch-scroll ticks routed into the open Void Glass bank, keeping desktop visuals unchanged. | Code fixed and smoke-proven through the same Android-profile gate: focused bank smoke logs `closeW=54`, `closeH=22`, `loadoutW=38`, `actionH=30`, `menuRowH=28`, plus search/clear and mobile grid-drag scroll. | No bank packet/opcode/schema changes; all actions use the existing bank packets. |
| Glass chrome, HUD vitals, and shared modal kit | Shared from `Client_Base`; `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/mobile-ui-parity-web-controls` and `scripts/smoke-web-teavm-iphone.sh --no-build --user wbtest --pass voidtest123 --out tmp/mobile-ui-parity-web-iphone-wbtest` pass locally, covering the Android-parity canvas HUD, blocking-dialog controls, chat, Back/Escape routing, orientation, resume, and panel scroll. | Shared from `Client_Base`; Android Back delegates to `mudclient.handleAndroidBackButton()` and current smokes cover lifecycle/back/modal behavior. | Keep future tweaks in shared Java first unless the issue is browser-shell or native-wrapper specific. |
| Desktop chat strip and Global tab | Code fixed in the shared mobile chat path: `/play` now uses a six-band glass strip (`All`, `Chat`, `Quest`, `Global`, `PM`, `Rpt`) and loose recent-message text above it instead of the framed chat container. Verified by `tmp/mobile-qc-web-iphone-v2` with `ui.mobileLooseChat:true` and `uiHistory.messageTabs` including `GLOBAL`. | Code fixed through the same Android-profile renderer: native Android gets the six-band glass strip and loose recent-message text while keeping the existing keyboard/back ergonomics. `tmp/mobile-qc-android-chat-tabs-v1` proves `CHAT`, `QUEST`, `GLOBAL`, `PRIVATE`, and `ALL`. | Mobile keeps touch-sized geometry rather than the exact desktop band widths; no packet/opcode/schema change. |

| Area | Android source of truth | TeaVM/iPhone state | Remaining proof |
|---|---|---|---|
| Shared client ownership | `GameActivity` creates `RSCBitmapSurfaceView`, creates/reuses `mudclient`, starts the main thread, sets `osConfig.F_ANDROID_BUILD`, and installs `InputImpl` (`Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java:89`, `:106`, `:116`, `:125`). | TeaVM runs the shared `Client_Base` client and publishes Android/web UI state through `WebClientPort.publishUiState(...)` (`Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java:293`). | Covered by local login/preflight; hosted deployment still must prove the packaged client is the same build. |
| Framebuffer sizing | Android keeps logical width `512`, classic full height `346`, and portrait full height by surface aspect up to `1152` (`RSCBitmapSurfaceView.java:43`, `:45`, `:368`). Shared resize applies the full-height request back into `mudclient` (`Client_Base/src/orsc/mudclient.java:22122`). | TeaVM phone portrait keeps width `512` and viewport-derived full height. TeaVM phone landscape keeps full height `346` but computes logical width from the viewport aspect (`round(346 * cssWidth / cssHeight)`, capped at `1152`) so Mobile Safari fills the screen width. Desktop and tablet runtime modes are unaffected. | Local controls/authenticated smokes prove the new phone landscape sizing; physical iPhone must confirm Home Screen Safari does not introduce a viewport feedback edge case. |
| Surface scale and touch mapping | Android aspect-contains the framebuffer with `min(surfaceW/gameW, surfaceH/fullGameH)` and centered letterboxing (`RSCBitmapSurfaceView.java:325`), then inverse-transforms touch into shared client coordinates (`InputImpl.java:638`). | TeaVM maps browser pointer coordinates from CSS canvas rect into canvas pixels in `toPoint(...)`, listens on the full game surface, and current phone landscape fills width with no side gutters. The older landscape letterbox path is historical; edge taps now map into the widened framebuffer (`WebClientPort.java:1211`, `:1233`, `:1560`). | `tmp/web-teavm-controls-landscape-chat-v1` proves phone landscape fill and left/right edge hit mapping; physical touch feel still needs real phone proof. |
| Long press and context | Android defaults to hold-and-choose with `C_LONG_PRESS_TIMER = 5`, or 250ms (`Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java:20`, `:21`), posts delayed right-click on touch down, and suppresses the release tap after long press (`InputImpl.java:474`). | TeaVM publishes the same timer through `getAndroidLongPressMillis()` (`WebClientPort.java:702`) and queues secondary pointer clicks from JS long press (`WebClientPort.java:1376`). Shared canvas HUD bands are now excluded from long-press/context capture (`WebClientPort.java:1239`, `:1575`). | Physical iPhone must verify native long press and secondary/context selection feel natural on real targets. |
| Primary taps and top menu | Android primary tap sets `currentMouseButtonDown`, `lastMouseButtonDown`, records tap, and uses padded top-menu bounds (`InputImpl.java:553`, `:485`). | TeaVM pointer events set `mouseButtonClick`, `lastMouseButtonDown`, and add mouse clicks; top-menu releases use padded bounds (`WebClientPort.java:434`, `:541`). | Covered by smokes; physical test should verify context menu item selection with a thumb. |
| Keyboard and chat | Android opens raw IME with `TYPE_NULL` and `IME_FLAG_NO_FULLSCREEN` (`RSCBitmapSurfaceView.java:97`), toggles `F_SHOWING_KEYBOARD` in `GameActivity.drawKeyboard/closeKeyboard(...)` (`GameActivity.java:437`, `:451`), and updates `inputTextCurrent`, `chatMessageInput`, Backspace, and Enter commit (`InputImpl.java:307`). | TeaVM hidden textarea routes `beforeinput`, paste, composition, Backspace, and Enter into the same shared buffers (`WebClientPort.java:630`, `:663`; JS capture in `WebClientPort.java:934`). When `Aa` is tapped during plain gameplay with no menu/modal/panel open, the browser queues `c,compose-main`; Java validates the same conditions and focuses the real public-chat entry before text input. | Local typed/beforeinput/paste/composition chat smoke is green, and `tmp/web-teavm-iphone-landscape-chat-v2` proves `Aa` public-chat compose plus local echo. Real iPhone autocorrect/composition is still physical QA. |
| Back behavior | Android Back while IME is showing closes keyboard first (`RSCBitmapSurfaceView.java:107`); Activity Back delegates to `mudclient.handleAndroidBackButton()` before finishing (`GameActivity.java:174`, `:180`). Shared Back closes Input-X, world map, keyboard, login subpanels, and top menus (`Client_Base/src/orsc/mudclient.java:23304`). | TeaVM installs a browser history back trap. It now closes the browser keyboard first when the hidden textarea/IME is active, sends focused world-map-search Back as raw Escape to `WorldMapPanel.handleSearchKey(...)`, and only sends `b` into `handleAndroidBackButton()` once keyboard/search state is closed (`WebClientPort.java:572`, `:1184`, `:1298`). | Local focused chat/map and broad smoke now prove this; physical Safari/Home Screen must still verify browser Back and Home Screen resume behavior. |
| World map and walker | Android gives visible world map a dedicated touch path: touch must start inside `containsWindow`, movement over 3px is drag, and non-drag release holds button down for 90ms so shared `pollMouse` observes it (`InputImpl.java:560`). Search keys go directly to `WorldMapPanel.handleSearchKey(...)` (`InputImpl.java:237`; `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java:501`). | TeaVM uses dedicated `g,<phase>,x,y` map events, delayed release, pinch/wheel zoom, search-key routing, and published diagnostics. Pinch-start cancel now holds a moved touch briefly before release so the shared map cannot mistake a two-finger zoom for tap-to-walk (`WebClientPort.java:489`, `:1375`; `scripts/smoke-web-teavm-iphone.sh:1341`). | Local world-map/walker smoke proves route, movement, pinch zoom, and no walker request during pinch; physical iPhone must prove real touch/pinch/search feel. |
| Dialogs and blocking UI | Android closes welcome/server/wild warning dialogs from touch before normal input routing (`InputImpl.java:455`). | TeaVM mirrors dialog-close hit tests in `handlePointerEvent(...)` and diagnostics assert shell controls hide over blocking dialogs (`WebClientPort.java:444`). | Local smoke covers dialog-safe controls; physical QA should repeat on any server message/login dialog seen in normal play. |
| Panel/chat access | Android parity keeps shared Java HUD ownership: top tabs are visible and the TeaVM-only mobile panel shell is off (`Client_Base/src/orsc/mudclient.java:16794`), with shared compact top-tab and bottom chat-tab hit testing (`mudclient.java:17156`, `:18020`, `:18361`). | TeaVM currently uses shared canvas top tabs for panels and shared canvas chat tabs for chat; DOM drawer/tray is hidden in Android-parity mode (`WebClientPort.java:293`; `index.html:452`, `:457`). | This is the accepted baseline, not final mobile design. Rearrange only after phone play identifies concrete pain. |
| Bank renderer | Android now shares the Void Glass bank path for the launch config and logs `ANDROID_SMOKE_BANK_OPEN renderer=voidGlass` plus action geometry for focused smoke. | TeaVM phone mode now reports the active shared bank renderer through diagnostics as `ui.bankRenderer`; current launch config should show `voidGlass`, not legacy custom bank. | Physical iPhone/Home Screen QA should open a bank and paste diagnostics while `ui.bankOpen: true` before calling this mobile UI pass complete. |

## Relevant Code

Shared world-map panel:

- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java`
- `WorldMapPanel.pollMouse(...)` handles press, drag, release, close, zoom, reset, search focus, and map-tile selection.
- `WorldMapPanel.handleSearchKey(...)` consumes printable chars, Backspace, Enter, and Escape for map search.
- The map expects an old mouse model: press/hold/move/release with `currentMouseButtonDown == 1`, and only non-drag release returns `MAP_TILE`.

Shared world-map integration:

- `Client_Base/src/orsc/mudclient.java`
- `drawUi(...)` polls `worldMapPanel` every frame and calls `sendWorldWalkRequest(...)` on `MAP_TILE`.
- The minimap-side `World Map` button toggles `worldMapPanel`.
- `runScroll(...)` routes scroll wheel to `worldMapPanel.adjustZoom(...)` when the map is visible.
- `sendWorldWalkRequest(int destX, int destY)` sends `Opcodes.Out.WORLD_WALK_REQUEST`.
- `setWorldWalkRoute(...)` receives route results, stores route arrays, and shows success/failure messages.

Android input model:

- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java`
- `GameActivity` creates the `RSCBitmapSurfaceView`, creates/reuses the shared `mudclient`, and installs `InputImpl`.
- `RSCBitmapSurfaceView` renders the shared framebuffer with aspect-ratio scaling/letterboxing; `InputImpl` applies the inverse transform so touch coordinates land in shared client coordinates.
- Android special-cases visible world map scroll/drag by setting mouse position and holding `currentMouseButtonDown = 1`.
- Android routes world-map search keys directly into `worldMapPanel.handleSearchKey(...)`.
- Android chat text updates both `inputTextCurrent` and `chatMessageInput`, and Enter commits both `inputTextFinal` and `chatMessageInputCommit`.
- Android Back first closes the keyboard if it is open, otherwise delegates to `mudclient.handleAndroidBackButton()`, which can close Input-X, world map, keyboard, login subpanels, and top menus before finishing the Activity.

TeaVM/iPhone bridge:

- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientMain.java`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `WebClientMain` asks the browser shell for the resolved `runtimeMode` before setting `osConfig.F_ANDROID_BUILD`; `phone` uses the current Android-parity mobile shell, while `desktop` and `tablet` do not.
- `index.html` owns runtime-mode detection and classes. `html.touch` now means "phone shell active", not merely "this device has touch hardware"; `html.touch-device`, `html.phone`, `html.tablet`, and `html.desktop` carry the broader detection result for diagnostics and future tablet work.
- `installInputHandlers()` queues pointer/key/text/scroll/back events from browser JS to Java.
- The event queue uses compact strings: `p` pointer, `k` key, `t` text, `s` keyboard state, `w` scroll, `m` top-menu touch release, `u` mobile UI panel shortcut, `c` mobile chat shortcut, and `b` mobile Back.
- `handleKeyInput(...)` mirrors Android chat/input behavior.
- `syncMobileInputHints()` publishes `worldMap`, a broad Android-like `scrollableUi` predicate, `messageScroll`, top-menu hints, and Android-derived long-press timing to browser JS.
- Current JS routes dedicated world-map touches only when a touch starts inside the published world-map window. Outside-window touches fall back to normal shared pointer input instead of being swallowed.
- `#keyboard-button` focuses a hidden `#keyboard-capture` textarea. Browser `beforeinput`, `paste`, and composition text normalize into `t,<charCode>` events, while Backspace/Enter become key events. During plain gameplay with no menu/modal/panel open, tapping `Aa` first queues `c,compose-main`; `mudclient.openVoidscapeMobilePublicChatInput()` then focuses the real shared public-chat entry so the next typed characters go to chat.
- The web `...` action arms a one-shot secondary tap; long press queues a secondary click using Android's `osConfig.C_LONG_PRESS_TIMER * 50ms` timing; open top-menu releases route through `m,x,y` and padded shared menu bounds.
- TeaVM phone resize keeps portrait Android-like with logical width `512` and viewport-derived full height up to the existing `1152` cap. Phone landscape uses classic full height `346` and computes logical width from viewport aspect, capped at `1152`, so Safari fills the available width without CSS stretching or side gutters. The target height is computed from the viewport, not the already-contained canvas rectangle, so aspect-preserving CSS cannot trap portrait in a landscape-shaped feedback loop. Desktop and tablet runtime modes stay separate from this phone-only sizing branch.
- On the iPhone web profile, the current mobile baseline is Android parity, not the previous OSRS-style bottom dock experiment. `mudclient` keeps shared canvas top tabs visible, disables the TeaVM-only mobile panel shell, and keeps inventory, stats, map, magic/prayer, social, and options in the real shared Java renderer/state. `WebClientPort` publishes `ui.panelAccessMode: "canvas"`, `ui.mobilePanelShell: false`, `ui.canvasTopTabsVisible: true`, `ui.canvasPanelRailVisible: false`, and `ui.canvasPanelDockVisible: false`; the DOM `UI` button/drawer stays hidden in normal play as fallback/debug wiring.
- The real canvas chat and canvas chat tabs own normal chat display/input on iPhone. `WebClientPort` publishes `ui.chatPanelHidden`, `ui.chatAccessMode`, `ui.mobileLooseChat`, and the legacy orientation diagnostic `ui.phoneLandscapeLooseChat`. Android-profile mobile chat now uses loose recent-message text above the shared six-band glass tab strip in both phone orientations, so `/play` no longer carries a framed chat-history container. The compact browser chat-icon helper/tray is hidden in normal Android-parity mode so it does not duplicate the shared chat HUD. The browser `Aa` keyboard helper and secondary/context-action helper remain Safari affordances that feed the shared input path; long-press still queues secondary clicks.
- Diagnostics live mostly in `index.html`, including endpoint/portal/profile/resource/lifecycle state, control hit-test snapshots, `uiHistory`, `scrollHistory`, and clipboard-or-select export fallback.
- Web-only resource modes are implemented in `WebClientPort.draw()` plus `index.html` frame-upload hooks. Player-facing controls expose only `Normal` and `GFX off`; legacy `resource=low` / `low-resource` aliases normalize to normal because the throttled mode felt bad in play. `resource=gfx-off` stops normal canvas uploads after one transition frame and shows a `GFX off / Resume` overlay. Desktop/tablet expose the controls through the page shell, and phone mode exposes a compact in-game version after gameplay starts while hiding it on login/non-game screens. These modes do not pause networking, input polling, diagnostics, lifecycle handling, server ticks, or the shared Java game loop.
- Named beta profiles (`profile`, `clientProfile`, `accountProfile`, or `slot`) namespace saved endpoint and portal settings in browser storage for multi-account testing in separate tabs/windows. They do not save game credentials; the real account login stays in the shared game client.

Existing iPhone checks:

- `scripts/smoke-web-teavm-iphone.sh` covers login, mobile keyboard, basic chat send, tap-to-move, context action, camera diagnostics/gestures, canvas chat tabs, Android-parity canvas top-tab panel access, shared-panel scroll, orientation, lifecycle, diagnostics, and focused authenticated world-map proof.
- `WEB_TEA_SMOKE_AUTH_DB=server/inc/sqlite/preservation.db scripts/smoke-web-teavm-iphone.sh --no-build --only-bank --user wbtest --pass voidtest123 --out tmp/mobile-qc-web-bank-current` covers the real shared Void Glass bank on phone `/play`: `::quickbank`, renderer diagnostics, search/clear, touch-scroll routing, withdraw, deposit, deposit-all, loadout save/load, Close, and fixture restore.
- `scripts/smoke-web-teavm-iphone-controls.sh` covers no-login input bridge behavior and viewport/layout matrix.
- Synthetic canvas pointerdowns in both smoke harnesses assert `document.elementFromPoint()` hits the game canvas before dispatch, so a passing smoke no longer proves only that JavaScript can bypass a DOM overlay.
- `scripts/run-web-teavm-iphone-simulator.sh` gives fast Mobile Safari iteration with screenshots, orientation matrix, and optional video.
- `scripts/check-web-teavm-iphone-release.sh` aggregates local release preflight, including controls, broad real-login, focused world-map/walker, focused chat, HTTPS/WSS, package, and package verification gates.
- `scripts/check-web-teavm-iphone-final-release.py` ties local preflight, exact package files, hosted verifier, simulator proof, and physical iPhone QA.

Current coverage notes:

- The older physical-iPhone Magic/Prayer, keyboard-close, and landscape-loose-chat follow-up passed at `tmp/web-teavm-controls-loose-landscape-chat-v1`, `tmp/web-teavm-iphone-loose-landscape-chat-v1`, and `tmp/web-teavm-simulator-loose-landscape-chat-v1`. That package is now historical: it included a side `Best` shortcut and hid Mag/Pray while shared panels were open. Current live `/play/` is client version `10120`, not the old `10116` package.
- The UI-polish follow-up is now deployed, not local-only. Current live `/play/` removes the side `...`, adds side `Inv`, keeps side `Inv`/`Mag`/`Pray` visible while their side-opened panel is active, toggles a side-opened panel closed when its active side button is tapped again, reserves more room between side panels and the icon rail, hides quick panel buttons in phone landscape, shows phone resource controls in gameplay only, moves option menus above chat, adds mobile spell tap-target messaging plus empty target-click cancel, draws mobile Hits/Prayer under FPS, and leaves only `Normal` / `GFX off` in player-facing resource controls.
- After the physical-iPhone username-loss report and landscape/chat follow-up, `scripts/build-web-teavm-spike.sh` passed, controls smoke passed at `tmp/web-teavm-controls-landscape-chat-v1`, and authenticated local smoke passed at `tmp/web-teavm-iphone-landscape-chat-v2`. The local authenticated proof preserves username text across the username -> blur/Done -> password tap path, opens public chat by tapping `Aa` in plain gameplay (`c,compose-main`), observes local chat echo, and records phone landscape canvas `749x346` mapped to `844x390` with no side gutters. The package was deployed to live `/play/`; hosted static/deep verification passed at `tmp/web-teavm-deployment-verify-landscape-chat-static-v1` with manifest SHA-256 `e3dc847f166540f425c04d93ed2cbc8f126d95e5dae3e6557f421cd20e5703ab`, and hosted focused chat/default-WSS smoke passed at `tmp/web-teavm-hosted-only-chat-landscape-chat-v1`. Physical iPhone Safari/Home Screen QA is still required.
- The focused authenticated iPhone smoke now opens the real full world-map dialog from touch UI, pans it, zooms it, searches it, routes focused-search Escape into `WorldMapPanel.handleSearchKey(...)` as raw `k,1,27`/`k,0,27` instead of mobile Back, taps a nearby reachable map destination, proves a successful route plus player movement, closes it, and records `worldMapHistory`.
- The world-walker proof computes map-screen tap points from the shared map transform after resetting the map to the local player, then retries nearby world offsets until it gets `lastRoute.ok === true`, a nonzero route count, and movement in `__voidscapeClientState`.
- The focused authenticated chat smoke now proves in-game echo/send through typed text, browser `beforeinput`, paste, composition commit, and Backspace-edited input; it also proves Enter send, browser Back close, external Escape close, keyboard viewport freeze, and zero page scroll. Real iPhone Safari/Home Screen QA is still the release-grade proof for native autocorrect/composition feel.
- The focused authenticated bank smoke now proves `/play` opens the real shared Void Glass bank, publishes live `ui.bank` geometry, routes touch-drag scroll through the shared bank renderer, and completes search/clear, withdraw/deposit/deposit-all, loadout save/load, Close, and SQLite fixture restore at `tmp/mobile-qc-web-bank-current`.
- The broad authenticated iPhone smoke passed for the Android-parity baseline at `tmp/web-teavm-iphone-android-parity-v1`: portal Create Account/Recover account handoff, real mobile-keyboard login, canvas chat tabs, canvas top-tab panel access, shared-panel scroll, orientation, lifecycle/resume, diagnostics, and a real in-game chat echo in one run.
- The broad authenticated iPhone smoke now expects `ui.panelAccessMode: "canvas"`, `ui.mobilePanelShell: false`, `ui.canvasTopTabsVisible: true`, `ui.canvasPanelRailVisible: false`, `ui.canvasPanelDockVisible: false`, hidden DOM `panelButton`/drawer, hidden DOM chat helper/tray in normal play, and real shared `showUiTab` transitions from canvas top-tab taps.
- The no-login controls smoke passed at `tmp/web-teavm-controls-android-resize-v1`, the broad authenticated smoke passed at `tmp/web-teavm-iphone-android-resize-v2`, the focused synthetic world-map input proof passed at `tmp/web-teavm-controls-android-resize-world-map-v1`, the focused authenticated world-map/walker proof passed at `tmp/web-teavm-iphone-android-resize-world-map-v1`, and the focused map-search Escape/walker proof passed at `tmp/web-teavm-iphone-android-resize-world-map-escape-v2`. After the Escape patch, focused chat and controls also passed at `tmp/web-teavm-iphone-android-resize-chat-escape-v2` and `tmp/web-teavm-controls-android-resize-escape-v2`. After smoke hit-test hardening, controls and focused world-map/walker proof passed again at `tmp/web-teavm-controls-hit-test-v1` and `tmp/web-teavm-iphone-world-map-hit-test-v1`; the latter still records raw map-search Escape, an 8-tile successful route, movement, zero failed requests, and zero unexpected console output.
- After the Android long-press timing fix, `tmp/web-teavm-controls-android-longpress-v2` records `longPressTiming.configuredMillis: 250`, a secondary click by the 300ms check, and secondary release on pointerup. The authenticated smoke passed at `tmp/web-teavm-iphone-android-longpress-v1` with diagnostics `input.longPressMillis: 250`, portrait/landscape screenshots, canvas panel/chat parity, context action, post-resume movement proof, zero failed requests, and zero unexpected console output.
- Historical Android input/layout parity follow-up: `scripts/build-web-teavm-spike.sh` passed, `tmp/web-teavm-controls-android-input-parity-v2` passed the no-login controls matrix with broad scrollable-UI hints, outside-world-map-window touch fallback, long-press timing, keyboard paths, Back/Escape, and aspect-preserving layout, `tmp/web-teavm-iphone-android-input-parity-v8` passed the authenticated broad smoke with portrait `512x872` framebuffer mapped to `390x664` CSS, then-current landscape letterboxing, canvas chat/top-tab parity, movement, camera gestures, lifecycle, and zero failed/unexpected requests, and `tmp/web-teavm-iphone-worldmap-android-input-parity-v1` passed focused world-map pan/zoom/search plus an 8-tile successful world-walker route/movement proof.
- After the world-map resource-loader fix, `WorldMapPanel` loads map PNG/TSV/icon assets through `ClientPort.openCacheResource(...)` when direct platform file I/O is unavailable, fixing TeaVM's black `Plane 0 image missing` panel without adding new map assets. `scripts/build-web-teavm-spike.sh` passed, `tmp/web-teavm-iphone-worldmap-resource-fix-v1` passed focused rendered world-map open/pan/zoom/search plus an 8-tile route/movement proof with zero failed/unexpected requests, and `tmp/web-teavm-iphone-android-parity-clean-resource-fix-v1` passed broad Android-parity login/chat/HUD/movement/lifecycle/orientation proof with clean portrait/landscape screenshots.
- After the Safari helper collision polish, the browser-only `...` context helper and `Aa` keyboard helper are compact 44px controls during normal Android-parity play and are hidden while the shared world-map modal is open. `WebClientPort.publishWorldMapState(...)` toggles a `world-map-open` body class from the shared `WorldMapPanel` state, so the real Java map/search/walker UI owns that screen without helper overlap. Validation passed with controls smoke at `tmp/web-teavm-controls-helper-collision-v1`, focused rendered world-map/search/walker proof at `tmp/web-teavm-iphone-worldmap-helper-collision-v1`, broad authenticated portrait/landscape proof at `tmp/web-teavm-iphone-helper-collision-clean-v1`, and Mobile Safari Simulator orientation proof at `tmp/web-teavm-iphone-simulator-helper-collision-v1`. The physical QA template at `tmp/iphone-web-qa-current/iphone-safari-qa-report.md` now asks testers to verify compact helper reachability and world-map no-overlap behavior. Portrait still shows the compact helpers over gameplay; this is intentional Safari input scaffolding for the current Android-parity beta, not final UI.
- After the helper idle-visibility polish, `index.html` keeps the same 44px right-side Safari `...` and `Aa` helpers but renders them at `opacity: 0.72` during normal gameplay, restoring full opacity only when context is armed, the browser keyboard is open, or a blocking dialog needs the helpers. This is a visual declutter pass only: no input routing, renderer, protocol, or shared Java HUD ownership changed. Focused controls proof passed at `tmp/web-teavm-controls-helper-opacity-v1`, and fresh broad authenticated portrait/landscape screenshots passed at `tmp/web-teavm-iphone-helper-opacity-v1`.
- The helper idle-visibility candidate also passed the full fresh-build simulator-inclusive aggregate at `tmp/web-teavm-iphone-release-preflight-helper-opacity-v1`, then that run was copied into the canonical `tmp/web-teavm-iphone-release-preflight` path. It passed prerequisites, controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package, package verification, and Mobile Safari Simulator. That package SHA-256 was `35665cebe5b60bb3de9477ed14ed8384cf56557b495de734539a5c41d73df035`; it is now superseded by the resource/profile beta package below.
- The low-resource/profile beta slice added web-only `resource=low`, `resource=gfx-off`, and named profile storage isolation. Validation passed with embedded-JS/bash syntax checks, TeaVM build, controls smoke at `tmp/web-teavm-controls-resource-profile-v2` including the `iphone-controls-resource-gfx-off.png` screenshot, authenticated broad smoke at `tmp/web-teavm-iphone-resource-profile-v1`, package static verification at `tmp/web-teavm-package-verify-resource-profile-v4`, and simulator-inclusive aggregate local preflight at `tmp/web-teavm-iphone-release-preflight-resource-profile-simulator-v1` covering controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package, package verification, and Mobile Safari Simulator orientation/assets. That slice's local `dist/web-teavm/voidscape-web-build.json` SHA-256 was `44536d34cc5d70f608038f2451af37c4f1753d1c14c47acdb4d07e0ed5d21ba1` with 420 files verified locally; the current live package is tracked at the top of this document. The final release audit at `tmp/web-teavm-final-audit-resource-profile-simulator-v1` passes local preflight/package/simulator/world-map/overlay checks and fails only for missing hosted deployment manifest evidence plus the unfilled physical iPhone QA report.
- Version guard history: a `10120` package previously produced the in-game "Voidscape has been updated" rejection while live still expected `10116`. That is resolved for the current live package/server pair: `/play/` is deployed as client version `10120`, and the server accepts it. Keep using `scripts/check-web-teavm-iphone-release.sh --client-version N` only when intentionally testing a local server that expects a different version.
- After the HUD context-disarm follow-up, shared canvas top tabs and bottom chat tabs no longer inherit the Safari `...` one-shot secondary action or long-press timer. `WebClientPort.installInputHandlers()` treats those Android-parity canvas HUD control bands as normal left-tap regions, clears any armed secondary state only for HUD controls, and leaves gameplay long-press/armed-secondary behavior intact. The smoke clean-screenshot helper now refreshes diagnostics after restoring temporarily hidden chrome, so `controlsHistory` records the real visible layout instead of a hidden-camera artifact. The simulator-inclusive aggregate preflight passed at `tmp/web-teavm-iphone-release-preflight-context-hud-disarm-v3` with controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package, package verification, and Mobile Safari Simulator orientation all green. Fresh normal-play screenshots are `login-smoke/iphone-portrait-clean.png` and `login-smoke/iphone-landscape-clean.png`; fresh world-map/walker screenshots are under `world-map-smoke/`.
- After the keyboard-first Back parity follow-up, browser Back/Escape now matches Android's IME-first rule in the web shell. Normal public chat keeps the keyboard available after Enter, so the broad smoke now proves first browser Back closes the keyboard with `s,0` and no `b,1`, then a second browser Back sends shared mobile Back. Focused world-map search Back sends `k,1,27`/`k,0,27` into the shared map search, leaves the map open, clears search focus/query, and does not send `b,1`. Validation passed at `tmp/web-teavm-iphone-chat-keyboard-back-v1`, `tmp/web-teavm-iphone-worldmap-keyboard-back-v3`, broad authenticated smoke `tmp/web-teavm-iphone-keyboard-back-broad-v2`, no-build aggregate `tmp/web-teavm-iphone-release-preflight-keyboard-back-v1`, fresh-build simulator-inclusive local release aggregate `tmp/web-teavm-iphone-release-preflight-keyboard-back-simulator-v1`, and the current canonical no-build simulator-inclusive local release aggregate `tmp/web-teavm-iphone-release-preflight` with prerequisites, controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package to `dist/web-teavm`, package verification, and Mobile Safari Simulator orientation/assets green. Latest normal-play screenshots are `tmp/web-teavm-iphone-release-preflight/login-smoke/iphone-portrait-clean.png` and `tmp/web-teavm-iphone-release-preflight/login-smoke/iphone-landscape-clean.png`; latest world-map/walker screenshots are under `tmp/web-teavm-iphone-release-preflight/world-map-smoke/`, and latest Simulator screenshots are under `tmp/web-teavm-iphone-release-preflight/simulator/`.
- After the physical-QA gate follow-up, `scripts/run-web-teavm-iphone-qa.sh` and `scripts/validate-web-teavm-iphone-qa-report.py` require real-device checklist proof for the same keyboard-first Back behavior, focused world-map-search Back behavior, and world-map pinch/no-walk behavior that local smokes prove. The regenerated blank local handoff at `tmp/iphone-web-qa-current/iphone-safari-qa-report.md` now asks testers to send public chat, press Back once to close only the keyboard, press Back again to reach shared mobile Back, focus world-map search, press Back, confirm search closes while the map stays open, and pinch the world map without starting world-walk. `docs/RELEASE-CHECKLIST.md` no longer describes the superseded bottom-dock/compact-Chat experiment as the current iPhone gate.
- After the staged-package verification follow-up, `scripts/package-web-teavm.sh --skip-build --output-dir dist/web-teavm` refreshed the local release-candidate static directory from the current TeaVM output. `scripts/verify-web-teavm-deployment.sh --allow-http --base-url http://127.0.0.1:55231/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --out tmp/web-teavm-dist-local-verify-v1` passed required/forbidden static checks plus all 420 manifest file hashes, and the same packaged directory passed a deployed-style login/game smoke with `--ws ws://127.0.0.1:43496/ --portal /iphone-account/ --smoke --user test --pass test --out tmp/web-teavm-dist-local-verify-smoke-v1`. This proves the staged static package can serve the current iPhone client locally; it does not replace the still-required hosted HTTPS/WSS verification.
- After the final-audit focused-smoke gate follow-up, `scripts/check-web-teavm-iphone-final-release.py` requires passed `world-map-smoke` and `chat-smoke` local-preflight steps. A negative audit against the stale unsuffixed summary failed those two step checks, then `scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --simulator-wait 5 --package-dir dist/web-teavm --out tmp/web-teavm-iphone-release-preflight --portal /iphone-account/` regenerated the canonical final-audit preflight path with all required local steps green and `package.artifact` pointing at `dist/web-teavm`.
- Historical Android-mirror proof: after the full-surface letterbox touch parity follow-up, TeaVM listened for pointer/wheel/context events on the canvas parent game surface while still mapping coordinates through the real canvas rect. Landscape side-letterbox touches dispatched to the shared input bridge and clamped to `x=0` or `x=511` instead of being ignored by browser hit-testing outside the canvas. Validation passed with TeaVM build, controls smoke at `tmp/web-teavm-controls-letterbox-parity-v2` including `letterboxSurface` left/right `<main>` hit proof, standalone focused world-map/walker smoke at `tmp/web-teavm-iphone-worldmap-letterbox-parity-v1`, authenticated broad smoke at `tmp/web-teavm-iphone-letterbox-parity-v1`, and the canonical simulator-inclusive local release preflight at `tmp/web-teavm-iphone-release-preflight`. Current phone landscape supersedes this with fill-width sizing at `tmp/web-teavm-controls-landscape-chat-v1`.
- After the authenticated world-map pinch follow-up, the focused world-map smoke now performs a real two-finger map pinch after pan and before wheel zoom. The first passing proof in `tmp/web-teavm-iphone-worldmap-pinch-v1` exposed a bug: pinch zoom changed the map but also produced an unintended world-walk request. `WebClientPort.handleWorldMapTouchEvent(...)` now treats phase `3` cancel as a short held moved touch before release, making `WorldMapPanel.pollMouse(...)` classify it as a drag/cancel instead of a non-drag tap. The smoke now asserts pinch emits `g,0`, `g,3`, and `w,*`, changes zoom, records `scrollHistory.worldMap`, leaks no `p,*` events, and does not advance `walker.lastRequest.at`. Validation passed with TeaVM build, focused proof at `tmp/web-teavm-iphone-worldmap-pinch-v2`, controls smoke at `tmp/web-teavm-controls-worldmap-pinch-v1`, and the current canonical simulator-inclusive local release preflight at `tmp/web-teavm-iphone-release-preflight`; canonical `world-map-smoke/summary.json` records pinch zoom `0 -> 2`, events `g,0,232,440`, `g,3,304,440`, `w,1`, `w,1`, and walker `lastRequest.at` staying `0`.
- After the canvas top-tab auto-close proof follow-up, `scripts/smoke-web-teavm-iphone.sh` no longer closes the final opened shared panel with a synthetic `u,hud` event. It moves the real pointer to a safe world point and requires a zero-button pointer event to auto-close `showUiTab` back to `0`, matching Android's pointer-position/hover-style shared panel ownership. Validation passed at `tmp/web-teavm-iphone-canvas-panel-autoclose-v1`, which opened shared panels `3,1,4,2,5,6`, closed with `p,0,169,506,0`, recorded `closedAfter.showUiTab: 0`, had zero failed requests/unexpected console output, and saved fresh portrait/landscape screenshots.
- The current canonical simulator-inclusive preflight was refreshed after starting the local server on WS `43496`: `scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --simulator-wait 5 --package-dir dist/web-teavm --out tmp/web-teavm-iphone-release-preflight --portal /iphone-account/` passed prerequisites, controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package, package verification, and Mobile Safari Simulator. The refreshed `login-smoke/summary.json` now carries the real canvas top-tab auto-close proof with panel ids `3,1,4,2,5,6`, close event `p,0,169,506,0`, and `closedAfter.showUiTab: 0`.
- The Android-resize aggregate local preflight passed at `tmp/web-teavm-iphone-release-preflight-android-resize-final-v1` with prerequisites, controls, broad login/game, focused world-map/walker, focused chat, HTTPS/WSS, package, and package-verify green. It skipped Simulator because fresh Simulator proof already lives at `tmp/web-teavm-iphone-simulator-android-resize-v1`.
- Historical visual review artifacts live at `tmp/web-teavm-iphone-android-resize-clean-v3`; those screenshots were captured with diagnostics enabled only for the smoke login hook, then diagnostic/camera chrome hidden before capture. They show the older Android-like 512-wide landscape model, which is superseded by the current phone landscape fill screenshot at `tmp/web-teavm-iphone-landscape-chat-v2/iphone-landscape-clean.png`.
- Fresh Mobile Safari Simulator orientation proof lives at `tmp/web-teavm-iphone-simulator-android-resize-v1`. It passed on iPhone 17 Pro Simulator / iOS 26.1 with portrait, raw landscape, upright landscape, and required HTTP asset checks green. This is Safari-load/orientation evidence only; it is not Home Screen/PWA release proof and does not replace physical iPhone QA.
- The physical iPhone QA report template and validator now require real-device proof for Android-parity canvas panel access, canvas chat tabs, hidden redundant DOM drawer/chat controls, browser Back closure, `ui.chatAccessMode`, `ui.panelAccessMode`, dialog-safe mobile shell controls, and copied diagnostics that show the old TeaVM-only bottom dock is off.
- The local release preflight passed at `tmp/web-teavm-iphone-release-preflight-slice6` with controls, login, world-map, chat, HTTPS/WSS, package, package-verify, and Simulator all green. The Simulator step opened Mobile Safari on iPhone 17 Pro Simulator, captured portrait plus landscape screenshots, and verified required web/cache/login assets loaded with HTTP 200 responses.
- After the mobile panel drawer slice, the local release preflight passed at `tmp/web-teavm-iphone-release-preflight-mobile-panel-drawer-slice7b` with controls, login, world-map, chat, HTTPS/WSS, package, and package-verify green. That run skipped Simulator and is now superseded by the slice8-video local preflight for current Simulator evidence.
- After the mobile chat tray slice, the local release preflight passed at `tmp/web-teavm-iphone-release-preflight-mobile-chat-tray-slice8-video` with controls, login, world-map, chat, HTTPS/WSS, `dist/web-teavm` packaging, package-verify, and Simulator green. The Simulator step used iPhone 17 Pro Simulator on iOS 26.1, diagnostics mode, orientation matrix screenshots, and a 90-second recording; `simulator-video-checks.json`, `simulator-screenshot-checks.json`, and `simulator-http-checks.json` all passed.
- Earlier standalone Simulator orientation proof also passed at `tmp/web-teavm-iphone-simulator-slice6`. The slice8-video run is the current local Mobile Safari evidence, but it is explicitly not Home Screen/PWA release proof and does not replace physical iPhone QA.

## Likely Technical Diagnosis

World map/world walker on iPhone failed because the web bridge lacked an Android-like dedicated world-map gesture stream.

The shared map wants:

- Pointer down over map content with button held.
- Pointer moves while still held for pan.
- Pointer up after no drag for tap-to-walk.
- Scroll wheel/pinch/zoom events routed to map zoom while the map is visible.
- Search focus to open the mobile keyboard and feed text to `WorldMapPanel.handleSearchKey(...)`.

The old iPhone bridge had generic touch handling optimized for terrain taps, long press/context menu, top-panel scroll, camera drag, and pinch camera zoom. When world map was visible it blocked scroll/camera gestures, but did not provide a first-class map-specific drag/pinch/search/tap workflow with diagnostics.

Android already demonstrated the shape to copy: when the world map is visible, treat touch movement as map interaction first, not camera/scroll. The web bridge now similarly bypasses the terrain/camera gesture system while map is active and feeds the shared `WorldMapPanel` the mouse-style stream it expects.

## Current Working Plan Snapshot

This section is intentionally concrete so the plan survives context compaction. Slices 1-7 have been implemented locally as of 2026-06-24; remaining work is real-device QA, hosted release verification, and the larger mobile HUD/product polish phase.

Primary implementation files:

- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `scripts/smoke-web-teavm-iphone.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh`
- `docs/subsystems/iphone-web-client.md`
- `docs/DIVERGENCE.md`

Reference files to keep open while editing:

- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java`
- `Client_Base/src/orsc/mudclient.java`
- `Client_Base/src/orsc/net/Opcodes.java`
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java`
- `scripts/android-smoke.sh`

Bridge shape:

- Add a dedicated TeaVM world-map touch event path instead of forcing map gestures through terrain tap, context action, camera drag, or top-panel scroll logic. Implemented as `g,<phase>,<x>,<y>`.
- Mirror Android's behavior: while the world map is visible and a touch begins in the map, hold `currentMouseButtonDown = 1` across frames, update mouse coordinates during drag, and delay release long enough for `WorldMapPanel.pollMouse(...)` to observe press then release.
- Let non-drag release flow into the existing `MAP_TILE` result and `mudclient.sendWorldWalkRequest(...)`; do not add a new map-walk packet.
- Route wheel and pinch gestures to `WorldMapPanel.adjustZoom(...)` while map is visible. Pinch out should zoom in; pinch in should zoom out.
- Route search text, Backspace, Enter, and Escape into `WorldMapPanel.handleSearchKey(...)` whenever map search is focused.
- Publish diagnostics for map visibility, search focus/query, map geometry, pan/zoom, recent map gestures, recent world-walk requests, and recent world-walk route results so smoke failures are explainable.

Smoke proof shape:

- Open the real full world map from the mobile touch UI, not merely the custom HUD map tab.
- Prove pan by dragging map content and asserting diagnostics changed.
- Prove zoom by wheel or pinch and asserting zoom diagnostics changed.
- Prove search by focusing the map search field, typing a known query, pressing Enter, and asserting search diagnostics/focus behavior.
- Prove world walker by tapping a nearby reachable map destination and asserting route/movement/message diagnostics from the real server path.
- Keep chat proof separate: keyboard open/close, text, paste/composition-safe input, Enter send, Back/Escape behavior, and no broken viewport resize while keyboard is open.
- Keep mobile panel proof separate: the Android-parity canvas top tabs should hit-test as the single phone-facing launcher for now, keep the hidden DOM drawer out of normal play, and verify the shared `showUiTab` state changes through real shared panels.

Explicit non-goals for this slice:

- No separate JavaScript renderer.
- No new gameplay protocol/opcode unless discovery proves the shared path cannot work.
- No desktop HUD rewrite before chat, world map, and world walker are reliable.
- No App Store/native iOS commitment before the web client proves the shared renderer and mobile input model.

## Fix Slices

Slices 1-7 are implemented and locally verified. The local Simulator gate has passed for the current bundle, but do not call the iPhone client mobile-complete until physical iPhone QA and hosted deployment gates in Success Criteria pass.

### Slice 1: Dedicated iPhone World-Map Touch Stream

Files:

- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `scripts/smoke-web-teavm-iphone-controls.sh`

Plan:

- In `installInputHandlers()`, add a world-map branch before long-press, terrain tap, top-menu scroll, and camera gesture handling when `window.__voidscapeWorldMapTouchActive` is true.
- Add a compact world-map touch event, for example `g,<phase>,<x>,<y>`, parsed by `handleInputEvent(...)`.
- Add Java-side state in `WebClientPort` for active map touch, start x/y, moved flag, and delayed release.
- Add `handleWorldMapTouchEvent(...)` mirroring `InputImpl.handleWorldMapTouch(...)`: hold `currentMouseButtonDown = 1` while active, never set `lastMouseButtonDown`, and preserve the non-drag release long enough for `WorldMapPanel.pollMouse(...)` to emit `MAP_TILE`.
- Clear the delayed release in `pollInput()` after roughly the Android 90 ms hold window. Keeping this Java-side makes it safer against Safari event batching.
- Cancel the active map touch cleanly on pointer cancel, map close, multi-touch pinch start, or loss of map visibility.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh --no-build --only-world-map-input --out tmp/web-teavm-controls-world-map`

Status on 2026-06-23: implemented and locally verified.

- `WebClientPort` now parses `g,<phase>,<x>,<y>` world-map touch events.
- Browser touch input routes through `g` events while `window.__voidscapeWorldMapTouchActive` is true, bypassing terrain taps, long press, top-menu scroll, and camera drag behavior.
- Java-side map touch state holds `currentMouseButtonDown = 1` through the release window so `WorldMapPanel.pollMouse(...)` can observe press/hold/release on iPhone Safari-style batched events.
- The focused controls smoke proved map drag/tap, long-press suppression, and pinch-start cancellation without leaking `p` terrain events or camera key events.
- Verification passed: `scripts/build-web-teavm-spike.sh`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --only-world-map-input --out tmp/web-teavm-controls-world-map`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-full-after-world-map`, and `scripts/build.sh`.

### Slice 2: World-Map Pinch, Zoom, Search, And Keyboard Parity

Files:

- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `scripts/smoke-web-teavm-iphone.sh`

Plan:

- Route pinch while the world map is active to `queueScroll(...)`, not virtual camera arrows.
- Preserve wheel events as `w,<amount>` so `mudclient.runScroll(...)` reaches `worldMapPanel.adjustZoom(...)`.
- In `handleKeyInput(...)`, if `client.worldMapPanel.isSearchFocused()` is true, send printable text, Backspace, Enter, and Escape to `worldMapPanel.handleSearchKey(...)` and return before mutating `inputTextCurrent` or `chatMessageInput`.
- Let the existing `drawUi(...)` Android/mobile branch open the keyboard when search is focused and close it when search focus drops.
- Add diagnostics for map visible, search focused, search query, zoom, pan, window/control centers, and keyboard state during map search.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone.sh --no-build --only-world-map-search --user <user> --pass <pass> --host 127.0.0.1 --ws-port <ws-port> --out tmp/web-teavm-world-map-search`

Status on 2026-06-23: implemented and locally verified.

- World-map pinch now queues scroll ticks while `window.__voidscapeWorldMapTouchActive` is true, so `mudclient.runScroll(...)` reaches `worldMapPanel.adjustZoom(...)` instead of camera key pulses.
- Focused world-map search keys now call `WorldMapPanel.handleSearchKey(...)` before chat/login input buffers mutate, so map search text cannot leak into chat.
- `WebClientPort` now publishes `window.__voidscapeWorldMapState` with visible, search, zoom, pan, floor, window, and control-center fields, and copied diagnostics include this as `worldMap`.
- Verification passed: `scripts/build-web-teavm-spike.sh`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --only-world-map-input --out tmp/web-teavm-controls-world-map-slice2`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-full-slice2`, and `scripts/build.sh`.
- Authenticated real-client world-map search coverage is now wired in Slice 3.

### Slice 3: Diagnostics And World-Walker Proof

Files:

- `Client_Base/src/orsc/mudclient.java`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `scripts/smoke-web-teavm-iphone.sh`

Plan:

- In `sendWorldWalkRequest(...)`, record last requested destination x/y/time for diagnostics only.
- In `setWorldWalkRoute(...)`, record route update time, ok, reason, and count for diagnostics only.
- Publish `worldMap` diagnostics to `window.__voidscapeWorldMapState`: visible, zoom, pan, floor, search focus/query, window/control centers, last map touch, last walk request, and last route result.
- Add bounded `worldMapHistory` diagnostics in `index.html` so failures show what changed frame by frame.
- Extend the authenticated iPhone smoke to open the real full world map from touch UI, pan it, zoom it, search it, close/reopen if needed, tap a nearby destination, and assert route update or player movement.
- For the walker tap, try a small set of nearby map offsets so same-tile/no-path does not make the test flaky.

Verification:

- `scripts/smoke-web-teavm-iphone.sh --no-build --only-world-map-search --user <user> --pass <pass> --host 127.0.0.1 --ws-port <ws-port> --out tmp/web-teavm-world-map-search`

Status on 2026-06-23: implemented and locally verified.

- `mudclient` records web-only world-map opener geometry, last world-walk request x/y/time, and last route time/ok/reason/count. These are diagnostics only.
- `WebClientPort` publishes the extra opener, request, and route fields under `window.__voidscapeWorldMapState`, and `index.html` copies a bounded `worldMapHistory` into diagnostics.
- The generic web pointer release path now sets `client.mouseButtonClick` before `addMouseClick(...)`, which lets legacy shared-client click buttons such as the minimap `World Map` opener activate correctly in TeaVM.
- A diagnostics-gated focused smoke login hook, `window.__voidscapeSmokeLoginRequest`, exists only so `--only-world-map-search` can jump past flaky browser-emulated login typing and test the map path quickly. The normal authenticated iPhone smoke still exercises the real mobile keyboard login.
- The focused authenticated smoke opens the real world map from the mobile HUD, pans it through `g` events, zooms through `w` scroll events, searches `varrock`, resets the map to the local player, computes nearby reachable map taps from the shared map transform, observes successful `WORLD_WALK_REQUEST`/route diagnostics, waits for player movement, closes the map, and writes screenshot/summary artifacts.
- Verification passed: `scripts/build-web-teavm-spike.sh`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --only-world-map-input --out tmp/web-teavm-controls-world-map-slice3b`, `scripts/smoke-web-teavm-iphone.sh --no-build --only-world-map-search --out tmp/web-teavm-iphone-world-map-slice3h`, `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-full-slice3`, `scripts/smoke-web-teavm-iphone.sh --no-build --only-world-map-search --out tmp/web-teavm-iphone-world-map-walker-success-2`, and `scripts/build.sh`.

### Slice 4: Robust Authenticated Chat Coverage

Files:

- `scripts/smoke-web-teavm-iphone.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh`
- `Web_Client_TeaVM/src/main/webapp/index.html` only if keyboard-history diagnostics are needed.

Plan:

- Add `--only-chat` to the authenticated iPhone smoke.
- Cover keyboard open/close, typed send, paste send, composition-style committed text, Backspace edit before send, Enter send, browser Back/Escape close behavior, and frozen viewport while the keyboard is open.
- Keep this as smoke coverage first; defer a visual mobile chat tray until the underlying shared chat path is solid.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-keyboard`
- `scripts/smoke-web-teavm-iphone.sh --no-build --only-chat --user <user> --pass <pass> --host 127.0.0.1 --ws-port <ws-port> --out tmp/web-teavm-chat`

Status on 2026-06-23: implemented and locally verified.

- `scripts/smoke-web-teavm-iphone.sh` now accepts `--only-chat`. This focused path uses the diagnostics-gated smoke login shortcut so runtime is spent on in-game chat; the normal full smoke still covers real mobile-keyboard login.
- The focused chat smoke sends five real in-game chat messages: typed text, `beforeinput` text, paste text with newline normalization, composition-committed text, and a Backspace-edited message. Local console echo must show all five messages.
- The same smoke asserts the mobile keyboard opens, freezes the CSS viewport height across a simulated iPhone keyboard shrink, sends on Enter, closes from browser Back, closes from external Escape without raw Escape key leakage, leaves the keyboard closed, leaves the page unscrolled, and reports no diagnostics errors.
- Verification passed: `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-keyboard-slice4` and `scripts/smoke-web-teavm-iphone.sh --no-build --only-chat --out tmp/web-teavm-iphone-chat-slice4-2`; the authenticated run echoed `typed 9jnqy`, `input 9jnqy`, `paste 9jnqy`, `comp 9jnqy`, and `edit 9jnqy`.

### Slice 5: Release Preflight Integration And Docs

Files:

- `scripts/check-web-teavm-iphone-release.sh`
- `docs/subsystems/iphone-web-client.md`
- `docs/DIVERGENCE.md`

Plan:

- Include enhanced controls, authenticated map, walker, and chat checks in local iPhone release preflight.
- Update this subsystem doc with whatever implementation details changed during the work.
- Add a short divergence entry for the iPhone web-client bridge behavior.

Verification:

- `scripts/build.sh`
- `scripts/build-web-teavm-spike.sh`
- `scripts/check-web-teavm-iphone-release.sh --no-build --user <user> --pass <pass> --ws-host 127.0.0.1 --ws-port <ws-port> --out tmp/web-teavm-iphone-release-preflight`
- `scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --orientation-matrix --exit-after-open --wait 10 --out tmp/web-teavm-iphone-simulator-map-chat`

Status on 2026-06-23: implemented and locally verified.

- `scripts/check-web-teavm-iphone-release.sh` now runs focused `world-map-smoke` and `chat-smoke` steps by default, with `--skip-world-map` and `--skip-chat` escape hatches for narrow debugging.
- The preflight treats those focused smokes as first-class summary steps, so map/walker/chat regressions cannot hide behind the broad login smoke.
- The broad iPhone smoke was hardened to prove Create Account and Recover account portal handoff through the same web pointer bridge used by real mobile canvas input, with retry diagnostics that include portal state, queued input events, and client state.
- TeaVM client diagnostics now expose login screen number, view mode, and mouse/click state in `__voidscapeClientState`, making mobile tap failures debuggable from copied diagnostics.
- The shared Voidscape login home and custom HUD chat-tab paths now accept the web-mobile queued-click fallback when Safari-style input sets `mouseButtonClick` but the old Java panel did not retain `lastMouseButtonDown` for that frame. This is scoped to `Config.isWeb() && isAndroid()`.
- Verification passed: `scripts/build-web-teavm-spike.sh`, `scripts/smoke-web-teavm-iphone.sh --no-build --out tmp/web-teavm-iphone-login-hud-fixed-slice5 --portal /iphone-account/`, `scripts/check-web-teavm-iphone-release.sh --no-build --out tmp/web-teavm-iphone-release-preflight-slice5b`, `scripts/run-web-teavm-iphone-simulator.sh --no-build --diag --orientation-matrix --exit-after-open --wait 10 --out tmp/web-teavm-iphone-simulator-slice6`, `scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --simulator-wait 10 --out tmp/web-teavm-iphone-release-preflight-slice6`, and `scripts/build.sh`.

### Slice 6: Mobile Panel Drawer / OSRS-Style First Pass

Files:

- `Web_Client_TeaVM/src/main/webapp/index.html`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Client_Base/src/orsc/mudclient.java`
- `scripts/smoke-web-teavm-iphone.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh`

Plan:

- Add a compact mobile `UI` button that is visible in-game but hidden before login and during blocking dialogs.
- Keep the safe-area-aware DOM panel drawer as fallback/debug metadata only; the current Android-parity production launcher is the shared canvas top-tab strip.
- Queue `u,<panel>` events from browser JS and route them through `WebClientPort` into shared client panel state.
- Use `mudclient.openVoidscapeMobileUiPanel(...)` as a narrow web-mobile wrapper over the existing shared `workbenchOpenVoidscapeUiPanel(...)` helper.
- Keep this as a shell-control layer only: no new renderer, no packet changes, and no duplicate inventory/map/chat implementation in JavaScript.
- Close the drawer on shortcut selection, canvas pointerdown, keyboard/action controls, leaving in-game, and browser Back before shared Back is queued.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-mobile-panel-drawer-slice7c`
- `scripts/smoke-web-teavm-iphone.sh --no-build --out tmp/web-teavm-iphone-mobile-panel-drawer-slice7d --portal /iphone-account/`
- `scripts/check-web-teavm-iphone-release.sh --no-build --out tmp/web-teavm-iphone-release-preflight-mobile-panel-drawer-slice7b`
- `scripts/build.sh`

Status on 2026-06-24: implemented and locally verified, then superseded by the Android-parity pivot. Keep this as history for debugging the hidden fallback tray path, not as the current player-facing target.

- The drawer uses 54x44 shortcut buttons and is positioned away from the existing right-side action/keyboard/diagnostics controls.
- Controls smoke proves the drawer is hidden by default, opens from the `UI` button, exposes all eight shortcuts with touch-sized targets, queues `u,<panel>`, closes after each shortcut, and lets browser Back close the drawer without queuing shared Back.
- The authenticated smoke proves each shortcut opens the real shared panel state: inventory `showUiTab=1`, map `2`, magic/prayer `4`, skills/quests `3`, friends `5`, and options `6`.
- The same authenticated run continued through custom HUD chat tabs/top panels, scroll, chat echo, camera controls, terrain movement, orientation, lifecycle/resume, and post-resume movement with no unexpected console failures.
- The aggregate preflight passed at `tmp/web-teavm-iphone-release-preflight-mobile-panel-drawer-slice7b` with controls, login, world-map, chat, HTTPS/WSS, package, and package-verify green. Simulator was skipped in that run; this was later superseded by the slice8-video Simulator preflight.
- Verification also passed `bash -n` over the touched shell scripts and `git diff --check` over the touched iPhone-web/client/docs files.

### Slice 7: Mobile Chat Tray / Shared Chat Tab Shortcuts

Files:

- `Web_Client_TeaVM/src/main/webapp/index.html`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `Client_Base/src/orsc/mudclient.java`
- `scripts/smoke-web-teavm-iphone.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh`

Plan:

- Add a compact mobile `Chat` button that is visible in-game but hidden before login and during blocking dialogs.
- Add a safe-area-aware tray beside the left rail with touch-sized shortcuts for All, Chat, Quest, Private, and Say.
- Queue `c,<chat>` events from browser JS and route them through `WebClientPort` into shared `messageTabSelected` state.
- Use `mudclient.openVoidscapeMobileChatTab(...)` as a narrow web-mobile wrapper over real shared chat tab fields, not a browser-rendered chat log.
- Make `Say` focus the existing hidden-textarea mobile keyboard path after selecting the real Chat tab.
- Close the tray on shortcut selection, canvas pointerdown, keyboard/action/panel controls, leaving in-game, and browser Back before shared Back is queued.
- Keep phone UI sane by having the Chat trigger close an already-open shared side panel through `u,closed`.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-mobile-chat-tray-slice8b`
- `scripts/smoke-web-teavm-iphone.sh --no-build --out tmp/web-teavm-iphone-mobile-chat-tray-slice8b --portal /iphone-account/`
- `scripts/check-web-teavm-iphone-release.sh --no-build --out tmp/web-teavm-iphone-release-preflight-mobile-chat-tray-slice8`
- `scripts/build.sh`

Status on 2026-06-24: implemented and locally verified.

- The tray uses 54x44 shortcut buttons and is positioned away from camera, dock, and bottom HUD controls in portrait and landscape.
- Controls smoke proves the fallback/debug tray is hidden by default, opens from the `Chat` button in that historical slice, exposes six shortcuts including Global with touch-sized targets, queues `c,<chat>`, opens keyboard for `compose`, closes after each shortcut, and lets browser Back close the tray without queuing shared Back.
- The authenticated smoke proves each shortcut selects real shared message state: `CHAT`, `QUEST`, `PRIVATE`, `ALL`, and `compose -> CHAT` with keyboard open.
- The smoke close-away point was moved away from mobile launcher controls after the new Chat button revealed an overlay collision in the old test coordinates.
- The aggregate preflight passed at `tmp/web-teavm-iphone-release-preflight-mobile-chat-tray-slice8-video` with controls, login, world-map, chat, HTTPS/WSS, `dist/web-teavm` package, package-verify, and Simulator video green. The Simulator artifact includes orientation screenshots, HTTP asset checks, and `simulator-session.mov` with `manualSeconds=90`.
- Verification also passed `bash -n` over the touched shell scripts, `git diff --check` over the touched tracked paths, `scripts/build-web-teavm-spike.sh`, and `scripts/build.sh`.

### Deferred Phase: Mobile HUD Redesign

After Android-parity chat, world map, world walker, panel access, keyboard, orientation, and physical iPhone QA are reliable:

- Consider an OSRS-inspired Voidscape mobile shell as a repositioning pass, not a second HUD. Keep the real Java HUD/panel/chat state authoritative, then move or de-emphasize cramped desktop-shaped access points on phone only where physical play proves the need.
- Keep the current Android-parity canvas top tabs as the baseline until a redesign slice proves a better single owner for panel access.
- Avoid adding a bottom row of HTML buttons that mirrors the existing canvas chat/panel tabs unless that same slice also suppresses, hides, or clearly demotes the redundant canvas access path in mobile mode.
- Prefer Java-side HUD geometry changes when the thing being moved is already drawn by `mudclient` (`getUITabsY()`, `voidscapeTopTabsStartX()`, `voidscapeChatFrame*`, `voidscapeRightPanel*`, `drawVoidscapeChatMessageTabs(...)`, `drawVoidscapeTopTabs(...)`). Prefer DOM overlay changes only for browser-only controls or launchers.
- Keep the shared renderer/protocol/game state.
- Test in Simulator first, then physical iPhone Safari/Home Screen.
- Current local UI-polish visual inventory is `tmp/web-teavm-visual-polish-pass-v5-final-normal`: `contact-mobile-portrait.png`, `contact-mobile-landscape.png`, `contact-tablet.png`, and `contact-desktop.png` cover every shared HUD panel/chat tab plus side Inv/Mag/Pray states at phone portrait, phone landscape, tablet, and desktop sizes. The pass also reviewed the bottom-right portrait `Normal` / `GFX off` placement beside the account button and the phone-landscape placement left of the account button. The local-only forced-selector visual inventory is `tmp/web-teavm-combat-selector-forced-visual-v4`; it proved combat-selector placement and loose landscape chat avoidance, then the debug forcing was reverted before the deployed package. Earlier hosted visual proof in `tmp/web-teavm-hosted-menu-visual-shell-resource-v1`, hosted broad smoke screenshots in `tmp/web-teavm-hosted-iphone-broad-shell-resource-v2/`, and Mobile Safari Simulator screenshots under `tmp/web-teavm-simulator-shell-resource-live-v1/` remain useful history, but they predate the final visual-polish deployment. Latest focused world-map/walker screenshots are under `tmp/web-teavm-iphone-release-preflight/world-map-smoke/`, including `iphone-world-map-pinched.png`. The UI is not final; the next polish should keep Android parity as the source of truth and adjust phone ergonomics from real play.

### Current Working Plan Snapshot

Files:

- `Client_Base/src/orsc/mudclient.java`
- `Web_Client_TeaVM/src/main/webapp/index.html`
- `Web_Client_TeaVM/src/main/java/com/voidscape/webclient/WebClientPort.java`
- `scripts/smoke-web-teavm-iphone.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh`

Plan:

- Keep the Android app as the source of truth for the current iPhone baseline.
- Keep inventory, map, magic/prayer, skills/quests, friends, and options on the shared canvas top-tab path. They must resolve through `showUiTab` and the existing panel draw/reposition methods.
- Keep chat tab selection on the shared canvas chat-tab path. It must resolve through `messageTabSelected`, `voidscapeChatHidden`, and the existing `panelMessageTabs` lists.
- Keep the DOM `UI` drawer and DOM chat tray hidden in normal play. They remain fallback/debug metadata only.
- Keep `Aa`, `...`, diagnostics, and diagnostics-only camera controls as DOM/browser-shell controls because they bridge browser-specific input into the shared client.
- Use physical iPhone play to decide whether the shared canvas HUD needs later rearrangement; do not revive the bottom dock or DOM tray without making it the single owner and proving the canvas duplicate is demoted.
- Treat remaining visual rough edges as physical-play ergonomics, not renderer/protocol blockers; phone landscape now fills Mobile Safari width by widening the shared Java framebuffer while preserving the classic `346` full height.

Verification:

- `scripts/build-web-teavm-spike.sh`
- `scripts/smoke-web-teavm-iphone-controls.sh --no-build --out tmp/web-teavm-controls-android-parity-v1`
- `scripts/smoke-web-teavm-iphone.sh --no-build --out tmp/web-teavm-iphone-android-parity-v1 --portal /iphone-account/`
- `scripts/smoke-web-teavm-iphone.sh --no-build --only-world-map-search --out tmp/web-teavm-iphone-android-parity-world-map-v1 --portal /iphone-account/`
- Clean portrait/landscape screenshots at `tmp/web-teavm-iphone-android-parity-clean-v3`
- Current live no-Best/visual-polish/deployment artifacts: `tmp/web-teavm-deployment-verify-10120-desktop-overlay-safe-v1`, hosted focused key-buffer proof `tmp/web-teavm-hosted-nav-keys-focused-v1` proving native non-stretched desktop `512x346`, default `/play/` WSS, and no PageUp/PageDown/arrow Java text-buffer mutation, normal local visual pass `tmp/web-teavm-visual-polish-pass-v5-final-normal`, and local-only forced-selector pass `tmp/web-teavm-combat-selector-forced-visual-v4`. Hosted desktop proof `tmp/web-teavm-hosted-desktop-arrow-legacy-percent-v1` remains the latest full logged-in desktop mouse terrain proof from the prior package because the current broad smoke is blocked by the live `test` account already being logged in. Earlier hosted chat/broad/menu/Simulator artifacts remain useful shared-path history at `tmp/web-teavm-hosted-iphone-chat-shell-resource-v1`, `tmp/web-teavm-hosted-iphone-broad-shell-resource-v2`, `tmp/web-teavm-hosted-menu-visual-shell-resource-v1`, and `tmp/web-teavm-simulator-shell-resource-live-v1`; they predate the final visual-polish deployment. Superseded landscape-loose-chat artifacts remain useful history at `tmp/web-teavm-controls-loose-landscape-chat-v1`, `tmp/web-teavm-iphone-loose-landscape-chat-v1`, `tmp/web-teavm-simulator-loose-landscape-chat-v1`, `tmp/web-teavm-deployment-verify-loose-landscape-chat-static-v2`, and `tmp/web-teavm-hosted-only-chat-loose-landscape-v1`; they predate removing the side `Best` button. Historical resize artifacts include `tmp/web-teavm-controls-android-resize-v1`, `tmp/web-teavm-iphone-android-resize-v2`, `tmp/web-teavm-controls-android-resize-world-map-v1`, `tmp/web-teavm-iphone-android-resize-world-map-v1`, and clean screenshots at `tmp/web-teavm-iphone-android-resize-clean-v3`.
- Current Mobile Safari Simulator artifact: `tmp/web-teavm-iphone-simulator-android-resize-v1`.
- Physical iPhone Safari/Home Screen QA before calling the mobile HUD pass complete.

Status on 2026-06-24: Android-parity HUD pivot locally verified; physical iPhone Safari/Home Screen QA still pending.

- Fresh overlay-suppression proof passed at `tmp/web-teavm-controls-overlay-suppression-v1` after tightening the controls smoke harness. The no-login smoke now records `overlaySuppression.normal` proving `panelAccessMode: "canvas"`, `chatAccessMode: "canvas"`, hidden DOM panel/chat controls, and hittable `Aa`/`...` helpers in normal play, plus `overlaySuppression.worldMap` proving `world-map-open` hides `Aa`, `...`, DOM panel, and DOM chat controls so they cannot cover shared map controls. `resetState()` also clears stale `world-map-open` before later layout checks.
- The final release audit now requires that overlay proof inside the local preflight controls artifact. Stale preflight evidence without `controls.overlaySuppression` is rejected; `tmp/web-teavm-iphone-release-preflight-overlay-v1` passed all local aggregate steps with the new controls artifact and was copied into the default `tmp/web-teavm-iphone-release-preflight` evidence path. `tmp/web-teavm-final-audit-overlay-default-v1` confirms the overlay checks pass and only external release evidence remains missing.
- The shared `WorldMapPanel` now uses a larger near-full-canvas modal only for `Config.isWeb() && Config.isAndroid()`, improving iPhone map/search/walker touch area without replacing the Java renderer or changing desktop web / Android APK layout. Targeted proof passed at `tmp/web-teavm-iphone-worldmap-mobile-modal-v2`: map window `488x727` at `(12,72)` on a `512x872` iPhone canvas, pan via dedicated `g` events, pinch zoom `0 -> 2` without accidental walker request, focused-search Back as raw Escape, `varrock` search, successful walker route `ok: true` / `count: 8`, and no failed requests or unexpected console output. Follow-up controls proof passed at `tmp/web-teavm-controls-worldmap-mobile-modal-v2`.
- The canonical local preflight was refreshed after that modal change at `tmp/web-teavm-iphone-release-preflight-mobile-modal-v1` and copied into `tmp/web-teavm-iphone-release-preflight`. It passed prerequisites, fresh TeaVM build, controls, login, world-map/walker, chat, HTTPS/WSS, package, package verification, and Mobile Safari Simulator; the default final audit at `tmp/web-teavm-final-audit-mobile-modal-default-v1` now passes `localPreflight` and still fails only for missing physical iPhone QA plus hosted deployment manifest evidence.
- `scripts/check-web-teavm-iphone-final-release.py` now makes the modal sizing a final-audit gate, not just a screenshot note. The local `world-map-smoke` artifact must prove a 512-wide iPhone canvas, near-full-canvas map window, search/close controls inside the window, pinch zoom without walker request, and successful route/movement. The current default audit proof is `tmp/web-teavm-final-audit-mobile-map-geometry-v1`; `tmp/web-teavm-final-audit-mobile-map-geometry-negative-v1` proves older overlay-era evidence is rejected for the old centered 75% map dialog.
- Physical iPhone QA now mirrors that gate: `scripts/run-web-teavm-iphone-qa.sh` generates a required `## World Map Diagnostics` section, and `scripts/validate-web-teavm-iphone-qa-report.py` rejects reports unless that JSON was copied while the map was open after pan/zoom/search/world-walk. The report must prove the 512-wide phone canvas, near-full map window, in-window search/close controls, hidden Safari helpers over the map, and successful world-walk route diagnostics. The current regenerated blank handoff is `tmp/iphone-web-qa-current/iphone-safari-qa-report.md`.

- `mudclient.isVoidscapeChatPanelHidden()` exposes the shared collapsed-chat state to the TeaVM bridge.
- `WebClientPort.publishUiState(...)` now publishes `ui.chatPanelHidden` plus `ui.chatAccessMode` (`canvas`, `collapsed-helper`, or legacy `dom-helper`).
- `index.html` hides the browser chat icon helper/tray in normal Android-parity mode. The real canvas chat frame and tabs own chat; the browser keyboard/context helpers remain only to bridge Safari-specific input affordances into shared state.
- Controls smoke and authenticated smoke are being updated to assert normal canvas-chat mode keeps redundant DOM chat controls hidden.
- Physical QA/report validation now expects normal final diagnostics to show `ui.chatAccessMode: "canvas"`, `ui.chatPanelHidden: false`, and `controls.chatButton.display: "none"`.

Status on 2026-06-24: panel access consolidation has pivoted from the OSRS-style bottom dock experiment back to Android-parity shared canvas top tabs.

- `mudclient` now disables the TeaVM-only mobile panel shell and leaves the shared canvas top-tab strip as the active mobile panel launcher, matching the Android app's "same game UI on another platform" direction.
- `WebClientPort.publishUiState(...)` publishes `ui.mobilePanelShell`, `ui.canvasTopTabsVisible`, `ui.canvasPanelRailVisible`, `ui.canvasPanelDockVisible`, and `ui.panelAccessMode`.
- `index.html` records those panel-shell fields in copied diagnostics and orientation control history, hides the DOM `UI` button/drawer while canvas tabs own panels, and keeps the four-button camera pad diagnostics-only because drag/pinch gestures are the normal player camera path.
- Smokes and physical QA/report validation now expect normal final diagnostics to show `ui.panelAccessMode: "canvas"`, `ui.mobilePanelShell: false`, `ui.canvasTopTabsVisible: true`, `ui.canvasPanelRailVisible: false`, `ui.canvasPanelDockVisible: false`, `controls.panelButton.display: "none"`, and `controls.chatButton.display: "none"`; the broad smoke proves the canvas top tabs open all real shared `showUiTab` panels.

## Packet And Protocol Impact

No packet or protocol changes are planned for the iPhone map/chat/walker fixes.

The launch cracker HUD activation coordinates this shared TeaVM client with the enforced server preset at client version `10132`. It reuses senderless `SEND_SERVER_MESSAGE` / `MessageType.QUEST` metadata and consumes recognized `@vscrackercampaign@v1|remaining` envelopes before chat; it adds no opcode, enum ordinal, packet length, or packet shape. Use `scripts/smoke-web-teavm-cracker-campaign.sh` for the real-WebSocket receipt/rendering gate. Local proof does not replace hosted HTTPS/WSS deployment verification or physical iPhone Safari/Home Screen QA.

World walker should use:

- Existing outbound `Opcodes.Out.WORLD_WALK_REQUEST` with wire opcode `35`.
- Existing inbound `SEND_WORLD_WALK_ROUTE` handling in `PacketHandler.handleWorldWalkRoute(...)`.
- Existing server handler `server/src/com/openrsc/server/net/rsc/handlers/WorldWalkRequest.java`.

If a later discovery proves an opcode change is unavoidable, stop and follow the cross-client contract process in `docs/subsystems/networking-protocol.md`.

## Known Risks

- Delayed map release timing is frame-rate sensitive. Keep the hold/release state in Java `pollInput()` rather than browser timers.
- Multi-touch pinch must cancel any active map drag so `currentMouseButtonDown` cannot get stuck at `1`.
- Search focus must not leak map-search text into chat or login buffers.
- The walker smoke must avoid same-tile and unreachable destinations by retrying nearby offsets.
- Chrome mobile emulation and iPhone Simulator are not final release proof. Physical iPhone Safari/Home Screen QA remains required.

## Success Criteria

The iPhone web client should not be called mobile-complete until:

- Chat send works from real iPhone Safari/Home Screen using touch keyboard, paste/composition, Enter, and Back/Escape interactions.
- The shared canvas chat strip exposes `ALL`, `CHAT`, `QUEST`, `GLOBAL`, and `PRIVATE` on `/play`, with the Report action still opening the report flow and copied diagnostics reporting `ui.mobileLooseChat: true` for the no-container path in normal phone play.
- Void Glass bank opens on phone `/play`, copied diagnostics while the bank is open show `ui.bankRenderer: "voidGlass"` plus live `ui.bank` geometry, mobile Close/search/action/loadout targets remain easy to hit, and real touch search/clear/scroll/deposit/withdraw/loadout behavior is covered by the focused local bank smoke and physical QA.
- World map opens from touch UI, pans, zooms, searches, closes, pinch-zooms without accidental world-walk, and records diagnostics.
- World walker/autowalk works from map tap and proves route or movement.
- Android-parity canvas top tabs open real shared inventory/map/magic/prayer/skills/friends/options panels, and hidden DOM drawer/chat fallback controls do not appear in normal play.
- Mobile controls do not cover blocking dialogs, top HUD, bottom HUD, or map controls.
- Simulator proof and physical iPhone report both cover chat, map, walker, and HUD interactions. For UI/control releases, require Simulator video when final audit is run with `--require-simulator-video`; the current slice8-video proof includes screenshots/orientation/assets plus a 90-second Simulator recording, but final release still needs physical iPhone Home Screen proof.

## Do Not Lose

- Current strategy is shared TeaVM-rendered game plus mobile shell.
- Android-parity shared canvas HUD is the current target. Larger OSRS-style or bottom-dock mobile UI ideas are deferred until physical iPhone playtesting proves the shared Android-style HUD needs a different owner/layout.
- Avoid a separate JavaScript game renderer.
- Avoid packet/opcode changes unless discovery proves there is no shared-client path; opcode changes require cross-client sync per `docs/subsystems/networking-protocol.md`.
