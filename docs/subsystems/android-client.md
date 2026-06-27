# Android Client

Voidscape's Android client should stay a native Android shell around the shared `Client_Base` game core. That keeps combat, packets, menus, cache loading, and custom client behavior identical to PC while letting Android specialize only the phone-specific pieces: first launch, cache install/update, touch input, fullscreen rendering, keyboard, battery/network overlays, and distribution.

## Current Architecture

- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` implements `orsc.multiclient.ClientPort` and owns the shared `mudclient` instance.
- `com.openrsc.android.render.RSCBitmapSurfaceView` renders the classic `512x346` framebuffer into a fullscreen `SurfaceView` with aspect-ratio letterboxing.
- `com.openrsc.android.render.InputImpl` maps touch, swipes, long-press, soft keyboard, and volume keys into the old mouse/key model.
- `com.openrsc.android.updater.CacheUpdater` copies bundled `Client_Base/Cache` assets into app-private storage and optionally checks `orsc.osConfig.CACHE_URL`.
- The Android build uses Gradle 8.13, Android Gradle Plugin 8.13, JDK 17, `compileSdk 36`, `targetSdk 35`, and `minSdk 26`. Shared client sources still compile with Java 8 source compatibility.
- `scripts/build-android.sh` is the canonical APK build entry point. It selects JDK 17 and runs the Android Gradle build.
- `scripts/android-smoke.sh` is the canonical emulator screenshot loop. It can start `voidscape_api35`, reinstall the latest APK, launch the wrapper, and capture bootstrap/ready/picker/manual/login keyboard/back-button/saved-credentials/account-handoff screenshots. Use `--only-auth-login` as the first auth diagnostic: it defaults to the local `android/android` fixture, verifies the SQLite password hash, writes the requested endpoint before launch unless `ANDROID_SMOKE_AUTH_USE_BUNDLED_ENDPOINT=1` is set, proves the launcher selected `10.0.2.2:43596`, submits credentials with the calibrated portrait login-form taps, watches the login response, checks post-login crash logs, then force-stops cleanly. Use `--only-auth-lifecycle` for login/resume/relaunch/logout hardening: it logs in, closes the welcome panel, backgrounds and resumes through the launcher, starts the launcher twice to catch duplicate activity launches, logs out, verifies the login form still accepts input, and fails on immediate Android runtime crashes. Prelaunch returning-player visual proof passed at `tmp/prelaunch-client-qa/android-auth-lifecycle-v15`; the Game settings tab's logout row is logged from its actual drawn geometry so the smoke taps the visible `Click here to logout` row, not the Social-tab row, and the first Game settings row is readable in portrait. When `ANDROID_SMOKE_AUTH_USER` and `ANDROID_SMOKE_AUTH_PASS` are set, the full smoke also logs into a local server, captures the in-game HUD/settings panel, verifies terrain, NPC, scenery/object, inventory-item, item-on-item, item-on-scenery, item-on-NPC, long-press context-menu, edge-clamped context-menu, camera-rotate, zoom gesture, chat-tab taps, chat message send, bank open/search/scroll/withdraw/deposit/deposit-all/loadout save/load, shop open/select/buy/sell/non-scroll behavior, worn-item equip/remove behavior, magic/prayer panel selection behavior, settings open/change/persistence behavior, ground-item label/rare-drop beam readability, and wilderness player-target menu selection, logs out, and verifies the login form is usable again.

## Player Launch

Focused `--only-auth-wilderness-target` coverage verifies wilderness player-target selection by temporarily moving the auth fixture into a quiet wilderness tile, promoting it only long enough to spawn a cinematic player through a smoke-only command key with a non-combat anchor, long-pressing the projected player target, selecting the logged `PLAYER_ATTACK_*` row, stopping the scene, and restoring the fixture.

The APK should prioritize one-tap entry for beta players:

- After bundled cache install, the visible `Play` button starts `GameActivity` using the saved Android app-private `ip.txt` / `port.txt` endpoint when present. Release builds always default to `5.161.114.251:43596`, including emulator-like Play review devices; debuggable builds on Android emulators default to `10.0.2.2:43596` so local smoke tests hit the host server without the advanced picker.
- The shared login screen's `Create Account` and recovery actions open the portal (`https://voidscape.gg/#account` and `#security`) in Android's browser. Android does not use in-client packet registration for release account creation.
- Long-press `Play` opens the advanced server picker for developers and testers only in debuggable builds.
- Debug advanced choices are public Voidscape, Android emulator `10.0.2.2:43596`, LAN placeholder `192.168.1.100:43596`, and manual host/port. Manual host/port opens prefilled with the current saved endpoint to avoid accidentally switching a QA device back to the default server.
- Replace the hardcoded public IP with the final DNS name before broad release so old APKs survive VPS moves.

## Emulator QA Commands

Use the smoke helper for visual changes:

```bash
scripts/android-smoke.sh --out /tmp/voidscape-android-smoke
```

On Windows, run the same script through Git Bash and pass Android SDK paths in the shell environment. Example focused bank rerun against a local server on port `43598`:

```powershell
$env:ANDROID_HOME='/c/Users/ryanr/AppData/Local/Android/Sdk'
$env:ANDROID_SDK_ROOT=$env:ANDROID_HOME
$env:JAVA_HOME='/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot'
$env:ANDROID_SMOKE_AUTH_USER='android'
$env:ANDROID_SMOKE_AUTH_PASS='android'
$env:ANDROID_SMOKE_AUTH_HOST='10.0.2.2'
$env:ANDROID_SMOKE_AUTH_PORT='43598'
$env:ANDROID_SMOKE_AUTH_DB='/c/Users/ryanr/Desktop/voidscape-latest-run/server/inc/sqlite/voidscape.db'
$env:ANDROID_SCREENSHOT_DIR='tmp/android-bank-smoke'
& 'C:\Program Files\Git\bin\bash.exe' 'scripts/android-smoke.sh' --no-build --only-auth-bank
```

The script discovers the Android SDK from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `Android_Client/local.properties`, common Android Studio/Homebrew SDK roots, or `adb` on `PATH`; set `ANDROID_SMOKE_ADB` / `ANDROID_SMOKE_EMULATOR` for unusual layouts. If no Android device is connected, it starts `voidscape_api35` headless with `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`, waits for boot completion, installs the debug APK, and writes screenshots into the output directory. Use `--no-build` or `--no-install` when iterating on automation rather than APK contents; use `--only-auth-login` before deeper auth debugging, `--only-auth-lifecycle` after lifecycle/launcher/render changes, then `--only-auth-zoom`, `--only-auth-chat-tabs`, `--only-auth-chat-send`, `--only-auth-bank`, `--only-auth-shop`, `--only-auth-equipment`, `--only-auth-magic-prayer`, `--only-auth-world-map`, `--only-auth-settings`, or `--only-auth-ground-loot` for focused authenticated reruns after the APK has already been built.

```bash
scripts/android-smoke.sh --no-build --only-auth-login --out /tmp/voidscape-android-login
scripts/android-smoke.sh --no-build --only-auth-lifecycle --out /tmp/voidscape-android-lifecycle
scripts/android-smoke.sh --no-build --only-auth-wilderness-target --out /tmp/voidscape-android-wilderness-target
```

Profile coverage AVDs:

- `voidscape_api35` - baseline medium phone, `1080x2400 @ 420dpi`, about 2 GB RAM.
- `voidscape_small_api35` - older/smaller phone, `768x1280 @ 320dpi`, about 2 GB RAM.
- `voidscape_tablet_api35` - tablet-ish wide profile, `2560x1600 @ 320dpi`, about 2 GB RAM.

Run focused lifecycle coverage on a specific profile with:

```bash
AVD_NAME=voidscape_small_api35 scripts/android-smoke.sh --no-build --only-auth-lifecycle --out /tmp/voidscape-android-small-lifecycle
AVD_NAME=voidscape_tablet_api35 scripts/android-smoke.sh --no-build --only-auth-lifecycle --out /tmp/voidscape-android-tablet-lifecycle
```

If those profile AVDs are not installed on a Windows dev machine, use `adb shell wm size` / `adb shell wm density` overrides on the connected emulator for layout pressure, then reset both values afterward. The bank smoke passed at `720x1280 @ 320dpi` (`tmp/android-ui-small-bank-slice-2`) and `1200x1920 @ 240dpi` (`tmp/android-ui-tablet-bank-slice-2`) against the local `android/android` fixture on port `43598`; these runs covered bank open, search, scroll, withdraw, loadout save/load, deposit, and deposit-all while visually checking stack counts and bottom chat-tab overlap.

For authenticated in-game coverage, run a local server and set `ANDROID_SMOKE_AUTH_USER`, `ANDROID_SMOKE_AUTH_PASS`, `ANDROID_SMOKE_AUTH_HOST`, and `ANDROID_SMOKE_AUTH_PORT`. The default auth user/password are `android` / `android`; the default auth host/port are `10.0.2.2:43596`, which points the emulator at the host machine's local server. If another local server is also running, set `ANDROID_SMOKE_AUTH_PORT` explicitly and use the smoke script's Manual Server flow so Android does not fall back to the wrapper default. Set `ANDROID_SMOKE_AUTH_DB` to the local SQLite DB path to assert that a terrain tap changed the saved player `x,y` after logout, temporarily move the auth player near object/NPC fixtures, and seed/restore inventory fixtures. The smoke script can create a Python-backed `sqlite3` shim in its output directory when the native CLI is missing, which keeps Windows bank/inventory/equipment fixture snapshots runnable. The NPC tap proof defaults to Void Herald (`ANDROID_SMOKE_NPC_ID=839`) and expects `ANDROID_SMOKE_NPC_ACTION=NPC_TALK_TO`; use those variables to point the same smoke hook at an attackable fixture when one is available. If the NPC projection log flakes while the fixed Herald fixture is visibly on screen, the smoke can fall back to `ANDROID_SMOKE_NPC_FALLBACK_CLIENT_X/Y` and still requires the shared NPC action assertion to pass. The scenery/object proof defaults to Void waystone (`ANDROID_SMOKE_OBJECT_ID=1303`) and expects `ANDROID_SMOKE_OBJECT_ACTION=OBJECT_COMMAND1`. The inventory tap proof temporarily seeds coins into slot `ANDROID_SMOKE_INVENTORY_SLOT=0` and expects the shared `ITEM_USE` path; the item-on-item proof reuses that selected source item, temporarily seeds a tinderbox into slot `1`, taps the rendered target slot, and expects `ITEM_USE_ITEM`. The item-on-scenery/NPC proof temporarily seeds a source item, selects it from inventory, taps the projected object/NPC target, and expects `OBJECT_USE_ITEM` and `NPC_USE_ITEM`. The context-menu proof long-presses the projected NPC target, waits for shared menu geometry, screenshots the open `Choose option` menu, taps row `0`, and expects `NPC_TALK_TO` by default. The edge context-menu proof long-presses client coordinate `ANDROID_SMOKE_EDGE_MENU_CLIENT_X/Y`, asserts the menu rectangle is clamped inside the classic framebuffer, taps row `0`, and expects `LANDSCAPE_WALK_HERE`. The camera and zoom proofs use `ANDROID_SMOKE_CAMERA_SWIPE_*` and `ANDROID_SMOKE_ZOOM_SWIPE_*` client-coordinate swipes and assert shared client state logs changed. The chat-tab proof taps the configured `ANDROID_SMOKE_CHAT_TAB_SEQUENCE` centers at `ANDROID_SMOKE_CHAT_TAB_Y` and asserts the shared selected tab changed. The chat-send proof uses `ANDROID_SMOKE_CHAT_KEYBOARD_X/Y`, `ANDROID_SMOKE_CHAT_ENTRY_X/Y`, and `ANDROID_SMOKE_CHAT_MESSAGE` to open the in-game Android keyboard, type into the shared chat entry, press Enter, and assert the normal chat packet path logged `ANDROID_SMOKE_CHAT_SEND`. The bank proof temporarily moves the auth player to the bank chest fixture, closes the welcome dialog from logged client coordinates when present, seeds high-ID inventory/bank rows, opens object `ANDROID_SMOKE_BANK_OBJECT_ID`, searches, clears search, scrolls with an Android grid swipe, withdraws, opens loadouts, saves a preset, deposits one item, deposit-alls the inventory, loads the saved preset, verifies preset persistence after logout, and restores the original DB rows and item ID sequence. The shop proof temporarily moves the auth player to the Edgeville general store fixture, seeds high-ID coins, long-presses shopkeeper `ANDROID_SMOKE_SHOP_NPC_ID`, selects the configured `NPC_COMMAND1` trade row, selects a shop slot, buys one item, sells one item, performs a swipe over the classic shop grid, asserts `scrollable=0`, and restores the original DB rows. The equipment proof temporarily seeds a low-level wearable (`ANDROID_SMOKE_EQUIPMENT_ITEM_ID`, default Wooden Shield id `4`), clears/restores worn state through either the optional `equipped` table or current SQLite `itemstatuses.wielded` inventory rows, equips it through the real inventory tap path, and for current Voidscape config verifies the inventory slot toggles `equipped=true` and removes it through `ITEM_UNEQUIP_FROM_INVENTORY`. The magic/prayer proof snapshots/restores stats, raises magic only for the fixture, opens the magic/prayer panel, selects Home teleport, verifies the self-cast context-menu action, switches to Prayers, toggles Thick Skin on/off, and restores the account. It intentionally treats the saved-position Home teleport result as a warning because the server-side self-cast teleport effect needs separate protocol investigation. The world-map proof clears the first-login `tutorial_appearance` cache key, opens the shared map, proves zoom/pan/search/Back-close through `ANDROID_SMOKE_WORLD_MAP`, and verifies Android can decode packaged world-map PNGs. The settings proof snapshots settings, forces a known baseline, toggles camera auto and one-button mouse through the normal setting packet, verifies DB persistence after logout, relogs, verifies the changed values reload into the visible panel, then restores the original settings. The ground-loot proof snapshots/restores inventory and loot-display cache keys, temporarily seeds a rare item (`ANDROID_SMOKE_GROUND_LOOT_ITEM_ID`, default Rune battle axe id `93`), drops it through a smoke-gated key path, asserts the rare-drop beam and label geometry fit inside the classic framebuffer, captures screenshot `102-auth-ground-loot-readable`, and restores the account. The wilderness target proof snapshots/restores position and `group_id`, moves the auth player to the quiet wilderness fixture `ANDROID_SMOKE_WILDERNESS_PLAYER_X/Y` (default `23,25`), temporarily promotes it, uses a smoke-only key to send the existing cinematic command packet with non-combat anchor `ANDROID_SMOKE_WILDERNESS_BOSS_ID` (default Bob, id `1`), waits for `ANDROID_SMOKE_WILDERNESS_TARGET_NAME`, long-presses the projected player sprite, selects the logged attack row, asserts a shared `PLAYER_ATTACK_*` action, stops the cinematic scene, and restores the account.

Bank smoke waits for a stable rendered bank-object coordinate before tapping the chest. This avoids racing camera settle or viewport-size changes when the fixture logs multiple object positions during Android smoke setup.

Endpoint note: when iterating against a non-default local port, write `ip.txt` / `port.txt` through the smoke launcher or manual server dialog once; the normal debug `Play` path preserves that saved endpoint on later launches. Release builds ignore saved developer endpoints such as `10.0.2.2`, `127.0.0.1`, and the LAN placeholder so review installs cannot strand themselves on local-only hosts.

Current emulator quirk: `adb shell wm size` and `dumpsys window displays` can report stale orientation after switching between the portrait wrapper and landscape `GameActivity`. The smoke script sizes tap coordinates from a temporary `screencap` PNG first, then falls back to `dumpsys`/`wm size` only if image probing fails. In tall portrait gameplay, client coordinates map from the top of the Android surface with width-based scale and `oy=0`; treating the old `512x346` frame as vertically centered misses visible UI rows. The native wrapper's `Play` button can land higher on tablet-density layouts than on phone profiles, so smoke taps the UIAutomator text when possible and falls back to the actual button band instead of a bottom-biased percentage. Named screenshots also go through a timeout-backed `screencap` helper; if adb stalls, the smoke logs a warning and keeps action assertions moving instead of hanging indefinitely. The ATD automation image is reliable for log-driven assertions but can return black `screencap` frames in this environment; use it for repeatable input proof, then use `voidscape_api35` or a real device for visual QA screenshots. The Google APIs image can still ANR during credential entry under headless load, so real-device visual screenshots remain the release-grade check. Fresh Google APIs profiles can show Android's OS-owned fullscreen education card (`Viewing full screen` / `Got it`) on first launch; focused authenticated UI smokes close the game welcome panel from logged client coordinates instead of blind center taps.

Android account creation and recovery are portal handoffs, not legacy in-client packet flows. The shared login screen labels the welcome action `Create Account` on Android and opens `orsc.osConfig.VOIDSCAPE_PORTAL_ACCOUNT_URL`; the existing-user `Recover account` action uses `VOIDSCAPE_PORTAL_RECOVERY_URL` the same way. Release builds point those URLs at `https://voidscape.gg/#account` and `#security`. Because the APK targets modern Android, `AndroidManifest.xml` declares HTTP/HTTPS `ACTION_VIEW` queries so `GameActivity.openUrl()` can discover Chrome/browser handlers before calling `startActivity`.

Android shows the existing `Save` credential button even when the server-side desktop remember-login flag is disabled. Saved credentials are stored in Android app-private `credentials.txt`, reloaded into the shared login fields on the next app launch, and remain excluded from bundled APK cache assets.

Android hides last-login host/IP information by default and does not expose the desktop `Hide IP` toggle. That keeps the login panel uncluttered and preserves the privacy-oriented behavior on a shared phone screen.

The legacy Android settings panel keeps its own three-tab Social/Game/Android layout. The Game and Android tabs already label the active panel, so the extra classic Android-only headings are skipped to keep the first option row readable in portrait. Desktop and web settings layout still render their headings normally.

Android login status messages dismiss the soft keyboard before drawing on the existing-user/recovery panels. Local validation now reports missing username/password immediately instead of silently returning with the keyboard still open, and the existing-user field labels hide while a status is visible so the compact mobile layout stays readable. If an Android player chooses an unreachable manual server, startup now stops on a clear selected-server error instead of hanging or crashing before login. Backgrounding from the login screen and relaunching from the app icon resumes the existing game task, refreshes fullscreen/network/focus state, and keeps input usable. The Android touch layer maps a single terrain tap into the shared client's normal `LANDSCAPE_WALK_HERE` path; the auth smoke can verify this by comparing the saved DB position before and after a tap/logout. The same tap path reaches shared NPC, scenery, and inventory menu actions: the smoke helper can temporarily enable app-private target logging, tap projected NPC/object/item coordinates, and assert resulting shared `NPC_TALK_TO`/`NPC_ATTACK*`, `OBJECT_COMMAND1`, `OBJECT_USE_ITEM`, `NPC_USE_ITEM`, `ITEM_USE`, and `ITEM_USE_ITEM` actions in logcat. Long-press now explicitly synthesizes the shared right-click path, opens the normal `Choose option` menu, suppresses the release tap so the player can review options, and lets the next tap choose a row. Android also has early touch-close hooks for welcome, server-message, and wilderness-warning dialogs so scaled phone taps do not depend on the classic frame's narrow text hitbox. Logging out from the in-game settings panel returns Android to the branded login home, closes stale game overlays, and leaves the existing-user form/keyboard usable without an app restart.

Android launcher branding uses the modern platform path while keeping old-device fallbacks. Android 8+ reads the adaptive launcher icon from `res/drawable-anydpi-v26/ic_launcher.xml`, with a dark background and the Voidscape cracked-`V` foreground. Android 12+ reads `VoidscapeLaunchTheme` from `res/values-v31/styles.xml`, using `windowSplashScreenAnimatedIcon` for a centered app-owned launch mark and `voidscape_launch_background` for the dark scene preview between the platform splash and the updater layout. Verification screenshots live at `/tmp/voidscape-android-launch-branding-v3/manual` for the cold-start/icon frames and `/tmp/voidscape-android-launch-branding-v3-smoke` for the wrapper/login smoke set. Android portrait is supported player-facing UI, so launcher and in-game QA should verify both portrait and landscape frames instead of forcing a landscape-only handoff.

Android native wrapper screens tolerate system dark mode and a larger Android font scale on the current emulator matrix. Baseline `voidscape_api35` and small `voidscape_small_api35` were both checked with `font_scale=1.3` and `cmd uimode night yes`; ready/play, server picker, manual server, login handoff, and bad-server screenshots remained readable without clipped text. Proof screenshots are `/tmp/voidscape-android-dark-font-v1`, `/tmp/voidscape-android-dark-font-small-v1`, and the small manual-dialog follow-up `/tmp/voidscape-android-dark-font-small-v1/manual-proof`.

## Shared Client Constraints

Everything in `Client_Base/src` must compile on Android. Avoid direct imports of desktop-only APIs such as AWT, Swing, `java.awt.image.BufferedImage`, or `javax.imageio.ImageIO` in shared code. Use `orsc.multiclient.ClientPort` for platform-specific image decoding or rendering behavior.

Country flag chat badges are the current pattern:

- `GraphicsController` asks `mudclient.clientPort.getSpriteFromByteArray(...)` to decode PNG flag assets, so PC and Android each use their native decoder.
- If a PNG asset is unavailable, shared code draws a tiny two-letter badge using raw pixels.
- This keeps Android compiling while preserving actual PNG flags where assets exist.

## Quality Bar

Before sharing an APK with players:

- For internal QA/emulator smoke, `scripts/build-android.sh --debug` must pass and emit `Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk`.
- Current public-channel stance is to show the Android APK in the post-launch download chooser when the APK artifact exists. For production promotion, prefer `scripts/build-android.sh --release` with upload signing configured, point `PORTAL_ANDROID_APK` at that release artifact if it is outside the default build path, and keep a passing physical Android QA report with the release evidence.
- Physical Android QA should be captured with `scripts/run-android-device-qa.sh --apk <release-apk-or-url>` and must pass `scripts/validate-android-device-qa-report.py` before a direct public APK channel is opened.
- Fresh install on an emulator reaches `Ready to play`, pressing `Play` writes the public endpoint, and the login screen renders.
- Emulator test matrix: one low-end-ish profile around 2 GB RAM, one modern phone profile, portrait and landscape fullscreen behavior, portrait HUD fit/touch targets, and at least one cold install with app data cleared.
- Input smoke: tap walk, tap NPC/object, long-press/right-click menu, chat keyboard, login text entry, inventory tap, bank scroll, camera rotate, zoom gesture, and logout.
- Performance smoke: idle in Lumbridge/Edgeville for several minutes, enter a populated/combat area, confirm no sustained skipped frames, ANRs, or runaway battery/CPU warnings in Android Studio Profiler.
- Network smoke: launch on Wi-Fi, background/resume the app, reconnect after a brief network drop, and verify bad host/port failure is understandable.

## Roadmap Checklist

This is the working Android punch list. The standard loop for each visual/input change is: build APK, reinstall on `voidscape_api35`, clear app data when first-run behavior changed, capture screenshots, compare against PC/launcher intent, iterate, then record what changed.

### Emulator and Visual QA Loop

- [x] Install Android Emulator and an ARM64 system image on the local Mac.
- [x] Create a dedicated AVD: `voidscape_api35` using Android 35 Google APIs ARM64, medium phone, ~2 GB RAM baseline.
- [x] Create a dedicated automation AVD: `voidscape_atd35` using Android 35 AOSP ATD ARM64. Use this for headless smoke runs; the Google APIs image can trigger System UI/Google-service ANRs during long scripted runs.
- [x] Confirm fresh APK install reaches the Android cache/bootstrap screen.
- [x] Confirm the one-tap `Play` path reaches the Voidscape login screen over `5.161.114.251:43596`.
- [x] Confirm the long-press server picker appears and is legible.
- [x] Confirm the existing-user login form responds and opens the soft keyboard.
- [x] Keep a screenshot set for every wrapper/login change: bootstrap, ready/play, server picker, server-picker Back, manual server, login home, existing-user keyboard, missing-password error, typed/masked password, keyboard Back, credentials saved/reloaded, recover-account handoff/status, login Back, create-account handoff/status, background/resume login, resumed keyboard input, bad manual server, bad-server startup error.
- [x] Extend the screenshot set after Android login: successful login, in-game HUD, terrain tap before/after, NPC tap before/after, scenery/object tap before/after, inventory tap before/after, item-on-item before/after, item-on-scenery before/after, item-on-NPC before/after, long-press context-menu before/open/after-selection, edge context-menu before/open/after-selection, camera rotate before/after, zoom before/after, chat-tab before/after, chat send before/keyboard/after, settings panel, logout return, and post-logout keyboard.
- [x] Extend the screenshot set after Android login: bank. ATD covers repeatable input assertions; `voidscape_api35` produces readable bank visual screenshots.
- [x] Add a small script for repeatable screenshots and APK reinstall so Android QA is one command instead of manual ADB commands.
- [x] Add a second AVD profile for a smaller/older-style phone after the main baseline is stable. `voidscape_small_api35` covers `768x1280 @ 320dpi`.
- [x] Add a large/tablet-ish AVD profile to catch scaling and touch-target issues. `voidscape_tablet_api35` covers `2560x1600 @ 320dpi`.
- [x] Test with `-no-window` for automation and visible emulator mode for human visual review.
- [x] Record any emulator quirks in this doc when they affect screenshots or input testing.

### Visual Identity and Branding

- [x] Make Android login use the custom Voidscape renderer instead of the old blue/gray OpenRSC login panels.
- [x] Make `voidscape-login-background.png` load on Android through `ClientPort`/`BitmapFactory`; desktop still tries the normal PNG loader first.
- [x] Screenshot the PC custom login and Android custom login side by side after parity is enabled.
- [x] Make Android first bootstrap screen match the Voidscape launcher identity instead of plain black/TextView/progressbar.
- [x] Make Android ready/play screen match the launcher: Voidscape logo/wordmark, restrained dark background, custom button styling, and no stock gray Android button.
- [x] Restyle the advanced server picker so it does not use the default white AlertDialog on a black game screen.
- [x] Restyle manual host/port entry with dark theme, legible fields, and clear cancel/back behavior.
- [x] Replace legacy Android launcher icon PNG densities with Voidscape artwork.
- [x] Add a true Android adaptive icon foreground/background pair.
- [x] Add a branded Android splash screen for cold app start.
- [x] Make fullscreen education overlay acceptable on first boot, or document that it is OS-owned and appears only once.
- [x] Verify portrait and landscape launch flows feel intentional and not like the app is rotating by accident.
- [x] Make native Android text sizes and spacing look polished at 420 dpi and smaller densities.
- [x] Ensure all Android native screens look good with system dark mode and font scaling.
- [x] Decide whether Android should reuse the desktop launcher layered assets or have a phone-specific composition.
- [ ] Keep the actual game surface classic RSC once logged in; polish wrapper and login without turning the game HUD into a different product.

### Account and Login Flow

- [x] Decide Android's public account path: mobile-friendly portal handoff for account creation and recovery; existing users log in inside the client.
- [x] If client packet registration remains disabled, route `Create Account` to the portal so players are not sent into a dead path.
- [x] Add a clear `Create Account` path that opens the configured portal/account site, with an in-client fallback status if the URL is missing in a dev build.
- [x] Add a clear `Forgot password` path that opens the configured portal recovery flow, with an in-client fallback status if the URL is missing in a dev build.
- [x] Verify existing-user username/password entry with the Android soft keyboard.
- [x] Verify password field masking and input focus behavior.
- [x] Verify back button behavior from login, keyboard, recovery, and server picker.
- [x] Verify saved credentials behavior on Android app-private storage.
- [x] Verify `Hide IP`/privacy controls make sense on mobile, or hide them if the feature is not player-facing.
- [x] Verify login errors are readable and not covered by the keyboard.
- [x] Verify bad server endpoint/error states are understandable.
- [x] Test login-screen resume after app background/resume.
- [x] Test logout returns to a usable login screen without needing app restart.

### Touch Controls and In-Game UX

- [x] Tap to walk on terrain.
- [x] Tap NPC to attack/talk. Void Herald talk is verified in smoke; NPC attack uses the same shared target/action path and can be asserted by setting `ANDROID_SMOKE_NPC_ACTION=NPC_ATTACK1` or `NPC_ATTACK2` against an attackable fixture.
- [x] Tap scenery/object actions. Void waystone `Return` is verified in smoke through the shared `OBJECT_COMMAND1` path; item-on-object now has separate fixture coverage, while spell-on-object still shares the same action logger and can be asserted later with a spell fixture.
- [x] Tap inventory item. Smoke seeds coins in slot `0`, taps the rendered slot center, and asserts the shared `ITEM_USE` path.
- [x] Use item on item. Smoke reopens inventory with the seeded source selected, taps a temporary target item in slot `1`, and asserts the shared `ITEM_USE_ITEM` path before restoring the original inventory rows.
- [x] Use item on scenery/NPC. Smoke seeds a harmless source item, selects it from inventory, taps the projected Void waystone and Void Herald targets, and asserts the shared `OBJECT_USE_ITEM` and `NPC_USE_ITEM` paths before restoring position/inventory rows.
- [x] Long-press/right-click context menu. Smoke long-presses the projected Void Herald/player target, waits for `ANDROID_SMOKE_CONTEXT_MENU`, screenshots the open `Choose option` menu, taps the logged row, and asserts the shared action. Android keeps the native mobile HUD path but uses the same translucent Voidscape context-menu shell when the server advertises custom UI.
- [x] Menu selection accuracy near screen edges. Smoke long-presses a bottom-right terrain coordinate, asserts the `ANDROID_SMOKE_CONTEXT_MENU` rectangle is clamped inside the classic framebuffer, taps the first row using logged menu geometry, and asserts `LANDSCAPE_WALK_HERE`.
- [x] Camera rotate gesture. Smoke waits for the in-game NPC projection, performs a horizontal client-coordinate swipe in the game viewport with the app-private camera flag enabled, then asserts `ANDROID_SMOKE_CAMERA_ROTATE` changed the shared camera angle or rotation before taking before/after screenshots.
- [x] Zoom gesture. Smoke waits for the in-game NPC projection, captures baseline `ANDROID_SMOKE_ZOOM` state, performs a vertical client-coordinate swipe away from the chat area with the app-private zoom flag enabled, then asserts `C_LAST_ZOOM` or rendered camera zoom changed before taking before/after screenshots.
- [x] Chat tab selection. Smoke taps the shared tab strip centers for `CHAT`, `QUEST`, `PRIVATE`, and `ALL` with the app-private chat-tab flag enabled, then asserts `ANDROID_SMOKE_CHAT_TAB` reports each selected tab.
- [x] Chat message entry and send. Smoke opens the Android keyboard from the in-game `Key-board` toggle, taps the keyboard-open message entry, types `ANDROID_SMOKE_CHAT_MESSAGE`, presses Enter, and asserts `ANDROID_SMOKE_CHAT_SEND` from the shared packet send path.
- [x] Bank open, scroll, deposit, withdraw, search, presets/loadouts. Smoke opens the bank chest fixture, searches/clears, scrolls, withdraws, saves a loadout, deposits, deposit-alls, loads the saved loadout, verifies persistence after logout, and restores the auth player's DB rows.
- [x] Shop open, buy, sell, scroll. Smoke opens the Edgeville general store through the real NPC trade row, selects slot `0`, buys one item, sells it back, verifies the classic shop grid reports `scrollable=0`, and restores the auth player's DB rows.
- [x] Equipment tab and worn item interactions. Voidscape currently runs with `want_equipment_tab=false`, so smoke validates the active inventory wield/remove path (`ITEM_EQUIP_FROM_INVENTORY`, `equipped=true`, `ITEM_UNEQUIP_FROM_INVENTORY`, `equipped=false`) and keeps dormant equipment-tab geometry/action hooks for any future server config flip.
- [x] Prayer/spell tab selection and casting. Android now records short taps before the shared component stack can consume them, so the magic/prayer side panel can reliably select rows. Smoke selects Home teleport, verifies the shared self-cast action from the long-press menu, switches to Prayers, and toggles Thick Skin on/off; the Home teleport saved-position side effect is logged as a warning for later server/protocol follow-up.
- [x] World map open, pan, zoom, search, close. Smoke opens the shared map, verifies zoom/pan/search/Back-close state, uses the Android PNG decode path for packaged world-map assets, and captures readable `voidscape_api35` frames including a Varrock search result; ATD remains log-proof only because its screenshots can be black.
- [x] Settings/wrench screen open, change options, persist options. Smoke opens the wrench/Game tab, toggles camera/mouse settings through the shared packet path, asserts `cameraauto/onemouse` persistence after logout, relogs, and captures readable `voidscape_api35` reloaded screenshots.
- [x] Ground-item labels and rare-drop beams readability. Smoke seeds a Rune battle axe, drops it through a smoke-gated path, asserts the rare beam and label are inside the classic framebuffer, captures `102-auth-ground-loot-readable`, and restores the auth player's inventory/cache rows.
- [x] Wilderness combat target selection. Smoke moves the auth player to wilderness, spawns a temporary cinematic player, long-presses the projected player target, selects the logged `PLAYER_ATTACK_*` row, and restores the DB fixture.
- [ ] Eating, potting, spell casting, and running during PvP-style stress.
- [x] Confirm logout is reachable and not too tiny.
- [x] Confirm Android back/home/app switcher do not corrupt input state. The focused lifecycle smoke backgrounds the game, resumes via launcher intent, logs out, and verifies the post-logout keyboard.

### Layout, Scaling, and Readability

- [x] Verify the classic `512x346` framebuffer is aspect-ratio correct on the medium phone AVD.
- [x] Verify letterboxing is acceptable in landscape and does not waste too much usable area.
- [ ] Check portrait gameplay on current public APK builds for clipped HUD, excess unused vertical space, context-menu touch targets, settings-panel spacing, and chat-tab reachability.
- [x] Check login button hitboxes against what the player sees after scaling.
- [x] Check small RSC fonts on a 1080x2400 phone screenshot.
- [x] Check UI at reduced emulator resolution/density for lower-end phones. Bank smoke passed at `720x1280 @ 320dpi`.
- [x] Check bank UI at tablet-ish emulator resolution/density. Bank smoke passed at `1200x1920 @ 240dpi`.
- [ ] Check display cutout/notch behavior.
- [ ] Check gesture navigation bar overlap.
- [ ] Check 3-button navigation mode if available.
- [ ] Check Android font scaling does not wreck native bootstrap screens.
- [x] Check keyboard overlay on username/password fields.
- [x] Check chat input with keyboard open in-game.
- [ ] Check battery/network overlay placement.
- [ ] Decide whether mobile needs optional larger context menus or touch-friendly menu scaling.

### Performance and Low-End Behavior

- [ ] Record cold start time to bootstrap screen.
- [ ] Record cache install time from fresh app data.
- [ ] Record time from `Play` tap to login screen.
- [ ] Idle on login for several minutes and watch for crashes/log spam.
- [ ] Idle in game after login and watch CPU/frame pacing.
- [ ] Walk through Lumbridge/Edgeville and watch responsiveness.
- [ ] Stress bank/inventory/shop scrolling.
- [ ] Stress combat and spell effects.
- [ ] Background the app for 30 seconds, resume, and verify rendering/input recover.
- [ ] Background for several minutes, resume, and verify reconnect behavior.
- [ ] Verify screen stays awake only while appropriate.
- [ ] Check APK size and installed app storage use.
- [ ] Profile memory footprint on the 2 GB AVD.
- [ ] Profile CPU/battery in Android Studio once available.
- [ ] Remove or defer expensive wrapper effects unless they clearly improve the mobile experience.

### Build, Release, and Updates

- [x] `scripts/build-android.sh` builds a debug APK.
- [x] Debug APK installs on an emulator.
- [x] Add a documented command for launching `voidscape_api35`.
- [x] Add a documented command for reinstalling the latest APK and taking screenshots.
- [ ] Add release signing config outside Git.
- [ ] Produce a release APK/AAB path when distribution is chosen.
- [x] Keep public portal APK downloads wired to the configured APK artifact and verify `/api/public` publishes a hash when the file exists.
- [ ] Replace hardcoded public IP with a stable DNS name before wider release.
- [ ] Decide Android update strategy: website APK download for beta, Play internal testing, or another channel.
- [ ] Add HTTPS remote cache/update endpoint only when versioned checksums are ready.
- [ ] Ensure Android packaging excludes mutable local files and secrets.
- [ ] Ensure AGPL source disclosure plan covers APK distribution.
- [ ] Document exact install steps for testers who sideload.
- [ ] Decide minimum supported Android version after real device feedback.
- [x] Update target/compile SDK after wrapper deprecation cleanup.

### Reliability and Error Handling

- [x] Replace deprecated `AsyncTask` updater flow with a modern executor/thread path.
- [x] Replace deprecated fullscreen/system UI flags with modern APIs.
- [x] Replace deprecated resource loading calls.
- [ ] Fix any Activity lifecycle issues found during rotate/background/resume testing.
- [ ] Add clear cache-install failure messaging.
- [ ] Add clear no-network/server-down messaging.
- [ ] Make retry behavior obvious when cache or server checks fail.
- [x] Prevent double-taps on Play from starting duplicate activities.
- [ ] Ensure app-private cache writes are atomic enough for interrupted first launch.
- [ ] Ensure corrupted cache can be repaired without uninstalling.
- [x] Confirm no fatal crashes in logcat through the login and lifecycle smokes.
- [ ] Add crash/ANR telemetry only after privacy/disclosure is ready.

### Real-Device Testing

- [ ] Test on at least one low-end Android phone owned by a player/friend.
- [ ] Test on one midrange current Android phone.
- [ ] Test on one tablet or large-screen device if any players use them.
- [ ] Test on physical cellular data and Wi-Fi.
- [ ] Test with flaky network/airplane-mode transitions.
- [ ] Test touch latency on real hardware; emulator input is not enough for PvP feel.
- [ ] Test thermal/battery behavior over a 30-minute play session.
- [ ] Gather screenshots/videos from testers who report visual bugs.
- [ ] Generate a real-device report with `scripts/run-android-device-qa.sh`, fill every mandatory check, and validate it with `scripts/validate-android-device-qa-report.py`.
- [ ] Keep a device matrix with Android version, screen size, RAM, result, and notes.

### Product Decisions

- [ ] Decide whether Android should ship for beta at all or stay internal until the portal/account path is clear.
- [ ] Decide whether Android players should create accounts in-app, in browser, or only via portal before installing.
- [ ] Decide whether Android gets a separate download page with screenshots and sideload instructions.
- [ ] Decide whether Android gets mobile-specific help text or keeps the PC-authentic minimal UI.
- [ ] Decide what “good enough for beta” means: login only, basic skilling, bank/shop, or PvP-ready.
- [ ] Decide if mobile PvP needs explicit support or if Android is mainly for casual/skilling players at first.

### Release Packaging

- Debug APKs remain the emulator/internal QA default: `scripts/build-android.sh --debug`.
- Public APK bundles should use `scripts/build-android.sh --release` or `scripts/package-launch-staging.sh --android-release`.
- Release signing is configured through `VOIDSCAPE_ANDROID_UPLOAD_KEYSTORE`, `VOIDSCAPE_ANDROID_UPLOAD_STORE_PASSWORD`, and `VOIDSCAPE_ANDROID_UPLOAD_KEY_PASSWORD`, or matching `voidscape.android.uploadKeystore.*` Gradle properties.
- `assembleRelease` / `bundleRelease` fail unless signing is configured. `VOIDSCAPE_ANDROID_ALLOW_UNSIGNED_RELEASE=1` exists only for local unsigned release experiments.

## Deferred Hardening

- Replace deprecated wrapper APIs (`AsyncTask`, old fullscreen flags, `getDrawable(int)`, bare `Handler()`) after the build and first-launch flow are stable.
- Decide the public distribution channel: direct APK download, Play internal/closed testing, or both.
- Add a remote Android cache endpoint only when it can be served over HTTPS with versioned cache checksums.
- Add crash/ANR telemetry before public beta if privacy policy and player disclosure are ready.
