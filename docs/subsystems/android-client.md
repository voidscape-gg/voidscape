# Android Client

Voidscape's Android client stays a native Android shell around the shared `Client_Base` game core. Combat, packets, cache loading, world rendering, and gameplay rules remain shared with PC and web; native Android may specialize the phone interaction shell described below without changing those contracts.

## Current Architecture

- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` implements `orsc.multiclient.ClientPort` and owns the shared `mudclient` instance. The manifest declares it `singleTask`; a lock-protected volatile retained-client/owner pair lets a recreated Activity rebind the live client while preventing a stale Activity from shutting down its replacement's thread or connection. The shared `mudclient.clientPort` owner reference is volatile for the same cross-thread handoff.
- `com.openrsc.android.render.RSCBitmapSurfaceView` renders the shared client framebuffer into a fullscreen `SurfaceView`. Portrait keeps a 512px logical width and grows the full height; landscape keeps the 346px full height and widens the logical width up to 1152px so phone landscape fills the surface without non-uniform bitmap stretching. During Activity replacement, only the `SurfaceView` owned by the Activity currently installed as `mudclient.clientPort` may apply a logical target size; a late callback from the old Activity may still maintain its own bitmap but cannot overwrite the retained client's settled orientation.
- `com.openrsc.android.render.InputImpl` maps touch, swipes, two-finger pinch zoom, long-press, soft keyboard, and volume keys into the old mouse/key model.
- `com.openrsc.android.updater.CacheUpdater` copies bundled `Client_Base/Cache` assets into app-private storage and optionally checks `orsc.osConfig.CACHE_URL`.
- The Android build uses Gradle 8.13, Android Gradle Plugin 8.13, JDK 17, `compileSdk 36`, `targetSdk 35`, and `minSdk 23`. Shared client sources still compile with Java 8 source compatibility.
- `scripts/build-android.sh` is the canonical APK build entry point. It selects JDK 17 and runs the Android Gradle build.
- `scripts/android-smoke.sh` is the canonical emulator loop. It can start `voidscape_api35`, install the current APK, drive authenticated flows from renderer telemetry, capture named screenshots, and reject touch fallthrough, undersized targets, stale orientation geometry, or immediate runtime crashes. Its normal authenticated campaign includes the lifecycle/app-switch gate; use the focused commands under **Mobile HUD QA** to isolate a surface, and use `--only-auth-login` before deeper authenticated debugging.

## Android-native in-game shell

The mobile shell is enabled only for the native Android client when Voidscape custom UI is active. PC, TeaVM/web, and authentic/classic UI do not use it. The shell changes navigation and touch geometry only; it does not add packets, change opcodes, or fork gameplay logic.

- The seven side destinations are fixed to thumb rails: the left rail owns `Stats`, `Map`, `Social`, and `Settings`, while the right rail owns `Inventory`, `Magic`, and `Prayer`. Each destination opens its own attached drawer; Magic and Prayer open the shared classic panel directly on the requested internal list. Tapping the active destination again closes it.
- Exactly one drawer can be open. A left-rail destination attaches immediately inside the left rail; a right-rail destination attaches immediately inside the right rail. The drawer is sized for that panel and centered vertically on the active rail target where possible, then bounded by the safe framebuffer edges. The location plaque is suppressed while a drawer is open and Chat is an independent launcher/sheet, so neither legacy band creates a detached gap above or below the drawer. A filled purple/gold connector bridges the icon and drawer, and the connector shares the drawer's consumed hit region. Unlike desktop hover panels, Android drawers remain open until a deliberate tap, Back action, or blocking modal closes them.
- All rail cells and primary in-panel rows derive from a physical `48dp` target. `ClientPort.getTouchTargetClientPixels()` converts that target through the active Android density and framebuffer transform, so logical pixel counts may differ between devices while the physical target remains stable. Inventory cells, Magic/Prayer rows and tabs, Stats sub-tabs, Social rows, Map actions, and Settings rows use the same touch contract where applicable. The square inventory cells remain unchanged for reliable tapping, but their classic item art is centered and aspect-fitted inside a never-upscaled 48x32 logical box; note and certificate composites follow the same rule, so no item sprite is stretched vertically.
- Panel height is content-specific rather than full-screen, and every scrolling viewport clips on complete rows. Inventory is 5 columns x 6 rows in portrait and 6 x 5 in landscape. Magic and Prayer expose four visible touch rows in portrait and three in landscape, then scroll; their footer keeps the action hint and current Magic/Prayer value visible. Stats has 48dp Stats/Quests/Loot/Beasts tabs and scrollable 48dp skill, quest, loot, and bestiary rows; the whole Stats family keeps one stable drawer width so changing sub-tabs never makes the panel jump. Social has 48dp Friends/Ignore tabs, a bounded people viewport, and a pinned Add action. Settings uses scrolling 48dp rows and a pinned Account action. Map keeps a compact map frame plus one 48dp World Map action.
- Chat stays directly usable on the HUD instead of requiring an intermediate screen. Its lightweight launcher has a small history control and a persistent inline field: tapping the field focuses the shared message entry and opens the real IME immediately, while tapping history separately opens the optional `All`, `Public`, `Quest`, `Global`, and `PM` transcript sheet. Showing the soft keyboard does not resize or crop the canvas: `SOFT_INPUT_ADJUST_NOTHING` keeps the logical framebuffer stable, while the real IME inset is mapped through the viewport into a logical `keyboardTop` and the inline field/sheet clamps above it. Android Back dismisses the IME before closing the history sheet; another Back then closes an open drawer before clearing a selected item/spell through the shared modal priority path.
- Logout is intentionally absent from the game HUD. `Settings` ends with `Account`; `Account` contains `Report a player` and `Log out`; `Log out` requires a separate `Cancel`/`Log out` confirmation. Only the final confirmation sends the existing logout request. The Account overlay yields whenever a higher-priority blocking surface opens, and Back closes the authoritative top modal before returning to Account.
- A second pinned Settings action launches the foreground-only `AFK Mode`. It closes ordinary HUD surfaces and swaps expensive world drawing for a black monitor refreshed about once per second, while the shared client continues its normal network and game-state servicing. The card reports live Hits and Prayer, XP gained since entry, elapsed monitor time, combat state, and an explicitly approximate time to the existing movement-idle logout; that estimate is client-observed because the server does not transmit its authoritative idle clock. AFK audio suspension is tracked separately from Activity-background suspension, and `Resume` or Android Back returns to the normal renderer. This is a battery/readability mode, not gameplay automation: it never eats, moves, loots, selects another target, prevents death, or changes the server's regular/subscriber idle rules.
- Touches in either rail, its active connector, an attached drawer, or the chat sheet are consumed and cannot fall through into a world click. A swipe that starts inside the actual drawer/connector (or expanded chat) scrolls that touch surface even when the world `Swipe to Scroll` preference is `Unset`; an explicit inverted preference remains honored. Touch scrolling lists use a passive slim position indicator instead of tiny arrow-button scrollbars. World camera gestures remain available when they begin outside the open touch surface. Native context menus are constrained to the center lane between the rails, use the same `48dp` row contract, and paginate when their contents cannot fit the available safe height.
- The native shop is a responsive touch surface rather than the 408x246 desktop dialog. Its fixed 8x5 item grid and Close, Buy, Sell, `1`, `5`, `10`, `50`, and `X` actions derive from 48dp geometry shared by drawing, hit-testing, and telemetry. Portrait stacks the action area below the grid. Landscape keeps the action area beside the grid: wide frames use one six-cell row per Buy/Sell group, while narrow phone landscape wraps each group into a 3x2 rail so the five item rows never collapse. The existing shop buy/sell/close packets and price/stock rules are unchanged.

### Supporting mobile surfaces

- Void Glass bank derives its Android title, search, clear, close, tabs, item cells, loadouts, transaction actions, and menu rows from the physical touch target. Its seven category tabs wrap into two rows at constrained widths rather than shrinking below 48dp. Search opens the real Android IME; Back hides that keyboard while preserving the bank and search state, Clear retains search focus/IME, and closing the bank dismisses the IME. The bank reduces columns when width is constrained, stacks portrait actions into two rows with full-width Deposit All, keeps landscape actions on one row, captures bank/inventory drag scrolling after a small threshold, and paginates quantity menus with touch-sized Previous/Next rows. Desktop bank geometry is unchanged.
- Login home, Existing User, and Create Account use 48dp fields/buttons and recalculate their shared `Panel` hit rectangles whenever the IME changes. Their cards center within the usable area above the keyboard rather than preserving stale desktop coordinates.
- `Settings -> Account -> Report a player` opens a compact IME-aware name step, then a paginated one-column rule picker with up to five rows in portrait or three in landscape. Selecting a rule changes the footer to an explicit `Send report` action; submission still uses the existing report packet and values.
- Password change, recovery-question setup, account recovery, welcome, and server-message dialogs use bounded safe-area cards and 48dp fields/actions on native Android. Multi-field recovery flows page instead of compressing rows when height or the IME leaves insufficient room.
- The server-authoritative Christmas cracker reel keeps the shared presentation and outcome contract; its native Android close and continue actions use exact 48dp targets, and Back follows the same top-modal priority as the visible controls.
- The finite launch cracker campaign uses the shared item-`575` HUD plaque. On native Android it stays left of the vitals, drops below the location plaque when width is constrained, offsets the kill feed, and disappears for zero or malformed state. It consumes the reserved metadata before chat and adds no Android-only packet path.
- The full World Map is a centered, bounded native card rather than the desktop window scaled down. Title/Close, Set and five waypoint cells, Search, zoom in/out, Reset, and Tiles all derive from 48dp; waypoint and zoom groups wrap on constrained widths, portrait height is aspect-capped, and the map canvas is reserved below the controls. Android latches a completed map tap across client frames so a short DOWN/UP pair cannot disappear between expensive map renders. Desktop and mobile-web World Map geometry are unchanged.
- The updater/application-updater layouts use fill-viewport scrolling, 16dp outer margins, responsive wordmarks, and one centered launch card that fills narrow screens but is capped at 340dp from the `w372dp` resource bucket upward.

### Insets, viewport, and resume contract

`AndroidClientViewport` owns one immutable snapshot of surface size, cutout/navigation/mandatory-gesture insets, logical target size, scale, and offsets. `RSCBitmapSurfaceView` uses that snapshot for drawing and `InputImpl` uses the same transform for inverse touch mapping. This prevents a button from being drawn in one safe rectangle while receiving input through another after rotation, resume, keyboard changes, or system-bar changes. The IME bottom inset is tracked separately so it can move the chat sheet without changing the framebuffer target. `ANDROID_MOBILE_VIEWPORT` telemetry records the surface, safe content rectangle, logical framebuffer, density, scale, derived 44/48dp targets, `imeBottom`, and logical `keyboardTop`.

Switching to another Android app must not log the player out during an ordinary interruption. While Android marks the Activity backgrounded, the retained client pauses socket polling, refreshes its last-write bookkeeping, and resets the client read timeout instead of flushing, draining incoming packets, or sending keepalives off-screen; game audio is suspended at the same time. The server's native-client ten-minute activity timeout preserves an open-but-idle session, and its ten-minute closed-channel reconnect grace lets the guarded resume path reclaim the same player if Android closes the old socket. A switch shorter than ten seconds resumes normal servicing on the retained stream. After at least ten seconds in the background, a logged-in client proactively closes the potentially stale stream on the game thread and enters retained-session reconnect instead of making the player wait for a dead-socket read timeout. Returning to the current owner Activity also refreshes fullscreen, network, viewport, focus, and rendering state; `singleTask` launcher delivery, owner-aware teardown, and current-owner-only viewport sizing prevent duplicate game Activities or a stale Activity disturbing the retained client. Explicit logout remains immediate. Android process death cannot preserve the in-memory client and returns through normal login.

If the first foreground tap arrives while guarded login/reconnect is still active, only a currently valid rail cell or the compact Chat launcher may be deferred. That safe intent is retained for at most 120 seconds, revalidated after reconnect, and applied directly on the game thread rather than reconstructed as a legacy world click. World taps, drawer contents, modal actions, and gameplay targets are never replayed, preventing stale input from acting on a changed world. Chat filter/composer ACTION_UP events also have a direct native route so the shared one-frame mouse pulse cannot swallow them; a 15-second post-reconnect grace permits that route while the shell is transiently between reconnect states.

## Mobile HUD QA

Run a local server first, then build through the canonical wrapper and export the authenticated fixture once:

```bash
scripts/build.sh
scripts/build-android.sh --debug

export ANDROID_SMOKE_AUTH_USER=wbtest
export ANDROID_SMOKE_AUTH_PASS=voidtest123
export ANDROID_SMOKE_AUTH_HOST=10.0.2.2
export ANDROID_SMOKE_AUTH_PORT=43596
export ANDROID_SMOKE_AUTH_DB=server/inc/sqlite/void_dungeon_qa.db
```

The normal authenticated smoke campaign includes the lifecycle/app-switch flow. The focused shell gates below isolate individual surfaces; `--only-auth-lifecycle` is the quick rerun for lifecycle changes, not optional coverage in the canonical full run:

```bash
scripts/android-smoke.sh --no-build --only-auth-chat-tabs --out tmp/android-mobile-hub
scripts/android-smoke.sh --no-build --only-auth-chat-send --out tmp/android-mobile-chat
scripts/android-smoke.sh --no-build --only-auth-magic-prayer --out tmp/android-mobile-magic-prayer
scripts/android-smoke.sh --no-build --only-auth-bank --out tmp/android-mobile-bank
scripts/android-smoke.sh --no-build --only-auth-shop --out tmp/android-mobile-shop
scripts/android-smoke.sh --no-build --only-auth-world-map --out tmp/android-mobile-world-map
scripts/android-smoke.sh --no-build --only-auth-settings --out tmp/android-mobile-settings
scripts/android-smoke.sh --no-build --only-auth-afk --out tmp/android-mobile-afk
scripts/android-smoke.sh --no-build --only-auth-lifecycle --out tmp/android-mobile-lifecycle
scripts/android-smoke.sh --no-build --only-auth-cracker-campaign --out tmp/android-cracker-campaign
```

Despite its historical flag name, `--only-auth-chat-tabs` is the split-rail hub gate: it reads `ANDROID_SMOKE_HUB_LAYOUT`/`ANDROID_SMOKE_HUB_ACTION`, exercises all seven controls in portrait and landscape, and checks 48dp sizing, bounded same-side drawer geometry, icon-to-drawer connector geometry, stable Stats-family geometry, panel-specific rows/tabs, Magic/Prayer distinction, and no world fallthrough. It also scrolls Stats and taps a connector to prove the attached region is interactive but cannot walk. `--only-auth-chat-send` reads `ANDROID_SMOKE_CHAT_LAYOUT`, opens the inline field directly while history is closed, sends exactly one message, verifies IME/Back behavior, then opens the separate history control and checks all five filters. `--only-auth-afk` enters through the pinned Settings target and checks its 48dp geometry, live vitals/timers, progressive low-rate render count, Resume path, and absence of an Android runtime crash. `--only-auth-bank` exercises responsive Void Glass geometry, real-IME search/clear/Back behavior, all seven wrapped tabs, grid drag, transaction actions, quantity/loadout menus, and close. `--only-auth-shop` must report `layout=native` and `targets48=true`, preserve the 8x5 grid, use side actions in landscape (including wrapped 3x2 groups under the narrow emulator pressure profile), and exercise buy/sell/close through renderer-reported coordinates. `--only-auth-world-map` rejects undersized, overlapping, map-starved, out-of-frame, or overlong portrait cards, then drives zoom, pan, search, and close through reported geometry. Settings and lifecycle coverage must traverse `Settings -> Account -> Log out -> confirmation`; a direct HUD logout is a regression. Lifecycle acceptance cannot rely on `players.online`, because that remains true during server grace: after a 35-second background interval the smoke must observe proactive retained-session reconnect plus retained/revalidated safe HUD intent, send a unique chat marker through the direct native composer route and require it in `chat_logs`, launch the wrapper twice, and require `dumpsys activity` to contain exactly one distinct `GameActivity` before logging out.

`--only-auth-cracker-campaign` is the focused protocol-`10132` HUD gate. Accepted emulator evidence at `tmp/android-cracker-campaign-8b-accepted5` proves the real login path releases the IME with no harness Back dismissal, then checks positive portrait/landscape item-`575` plaque geometry, hidden zero and malformed state, kill-feed/location/vitals/rail/chat non-overlap, and no campaign metadata in chat history. Its guarded cleanup requires explicit logout, restores the exact campaign/player-cache/staff-log tails and sequences plus the player's group, performs a delayed reverify, and requires SQLite integrity `ok`. This is emulator proof; it does not replace the physical-device report.

## Player Launch

Focused `--only-auth-wilderness-target` coverage verifies wilderness player-target selection by temporarily moving the auth fixture into a quiet wilderness tile, promoting it only long enough to spawn a cinematic player through a smoke-only command key with a non-combat anchor, long-pressing the projected player target, selecting the logged `PLAYER_ATTACK_*` row, stopping the scene, and restoring the fixture.

The APK should prioritize one-tap entry for beta players:

- After bundled cache install, the visible `Play` button starts `GameActivity` using the saved Android app-private `ip.txt` / `port.txt` endpoint when present. Release builds default to `voidscape.gg:43596`, including emulator-like Play review devices; debuggable builds on Android emulators default to `10.0.2.2:43596` so local smoke tests hit the host server without the advanced picker. On upgrade, release policy rewrites only the exact former public endpoint `5.161.114.251:43596` to DNS and preserves unrelated custom endpoints.
- The shared login screen's `Create Account` action opens the in-client username/password character-creation form and submits the existing register packet to the selected game server. Recovery still opens the portal security URL in Android's browser.
- Long-press `Play` opens the advanced server picker for developers and testers only in debuggable builds.
- Debug advanced choices are public Voidscape, Android emulator `10.0.2.2:43596`, LAN placeholder `192.168.1.100:43596`, and manual host/port. Manual host/port opens prefilled with the current saved endpoint to avoid accidentally switching a QA device back to the default server.
- The public endpoint is DNS-backed so later VPS moves do not require another APK solely for an IP change.

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

The script discovers the Android SDK from `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `Android_Client/local.properties`, common Android Studio/Homebrew SDK roots, or `adb` on `PATH`; set `ANDROID_SMOKE_ADB` / `ANDROID_SMOKE_EMULATOR` for unusual layouts. If no Android device is connected, it starts `voidscape_api35` headless with `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect`, waits for boot completion, installs the debug APK, and writes screenshots into the output directory. Use `--no-build` or `--no-install` when iterating on automation rather than APK contents; use `--only-auth-login` before deeper auth debugging, `--only-auth-lifecycle` after lifecycle/launcher/render changes, then `--only-auth-zoom`, `--only-auth-chat-tabs`, `--only-auth-chat-send`, `--only-auth-bank`, `--only-auth-shop`, `--only-auth-equipment`, `--only-auth-magic-prayer`, `--only-auth-world-map`, `--only-auth-settings`, `--only-auth-afk`, or `--only-auth-ground-loot` for focused authenticated reruns after the APK has already been built.

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

For authenticated in-game coverage, run a local server and set `ANDROID_SMOKE_AUTH_USER`, `ANDROID_SMOKE_AUTH_PASS`, `ANDROID_SMOKE_AUTH_HOST`, and `ANDROID_SMOKE_AUTH_PORT`; add `ANDROID_SMOKE_AUTH_DB` for fixture-backed bank, shop, equipment, Magic/Prayer, Settings, loot, and persistence checks. The native client pauses socket polling and resets its local connection timers while backgrounded; the server's ten-minute activity timeout and closed-channel reconnect grace preserve the player, while a foreground return after at least ten seconds proactively replaces the potentially stale socket through retained-session reconnect. The smoke helper's `ANDROID_SMOKE_AUTH_OFFLINE_TIMEOUT` still defaults to 135 seconds because normal focused flows explicitly log out; use at least 615 seconds only when deliberately testing natural ungraceful-session expiry. Renderer telemetry, rather than hard-coded screen coordinates, is authoritative for hub, chat, menus, panels, dialogs, and item/NPC/object targets. The remaining `--only-auth-*` flags exercise the shared packet/action paths and restore any temporary SQLite fixtures during cleanup.

Bank smoke waits for a stable rendered bank-object coordinate before tapping the chest. This avoids racing camera settle or viewport-size changes when the fixture logs multiple object positions during Android smoke setup.

Current launch bank parity expects `ANDROID_SMOKE_BANK_OPEN renderer=voidGlass` under `want_custom_ui:true`, `want_custom_banks:false`, and `want_bank_presets:true`. The smoke script still branches for the older legacy custom-bank renderer when deliberately testing `want_custom_banks:true`, but the shipped APK path should be Void Glass.

Historical baseline (superseded by the 2026-07-09 native Android shell): the 2026-07-05 mobile parity run proved the former shared six-band chat strip and Void Glass bank flow. Keep its artifacts only as pre-reset regression evidence; current hub/chat acceptance comes from `ANDROID_SMOKE_HUB_*` and `ANDROID_SMOKE_CHAT_LAYOUT` through the focused commands above.

Endpoint note: when iterating against a non-default local port, write `ip.txt` / `port.txt` through the smoke launcher or manual server dialog once; the normal debug `Play` path preserves that saved endpoint on later launches. Release builds ignore saved developer endpoints such as `10.0.2.2`, `127.0.0.1`, and the LAN placeholder so review installs cannot strand themselves on local-only hosts.

Current emulator quirk: `adb shell wm size` and `dumpsys window displays` can report stale orientation after switching between the portrait wrapper and landscape `GameActivity`. The smoke script sizes tap coordinates from a temporary `screencap` PNG first, then falls back to `dumpsys`/`wm size` only if image probing fails. In tall portrait gameplay, client coordinates map from the top of the Android surface with width-based scale; in phone landscape, coordinates map against the widened logical framebuffer rather than the old centered `512x346` frame. The native wrapper's `Play` button can land higher on tablet-density layouts than on phone profiles, so smoke taps the UIAutomator text when possible and falls back to the actual button band instead of a bottom-biased percentage. Named screenshots also go through a timeout-backed `screencap` helper; if adb stalls, the smoke logs a warning and keeps action assertions moving instead of hanging indefinitely. The ATD automation image is reliable for log-driven assertions but can return black `screencap` frames in this environment; use it for repeatable input proof, then use `voidscape_api35` or a real device for visual QA screenshots. The Google APIs image can still ANR during credential entry under headless load, so real-device visual screenshots remain the release-grade check. Fresh Google APIs profiles can show Android's OS-owned fullscreen education card (`Viewing full screen` / `Got it`) on first launch; focused authenticated UI smokes close the game welcome panel from logged client coordinates instead of blind center taps.

Android account creation uses the shared in-client packet registration form, not the web portal. `Create Account` collects only username, password, and confirmation when `want_email:false`; on success it returns to Existing User with the new credentials prefilled so the player can press `Ok` and enter the normal first-login appearance/onboarding flow. The existing-user `Recover account` action still uses `orsc.osConfig.VOIDSCAPE_PORTAL_RECOVERY_URL`; release builds point recovery at `https://voidscape.gg/#security`. Because the APK targets modern Android, `AndroidManifest.xml` declares HTTP/HTTPS `ACTION_VIEW` queries so `GameActivity.openUrl()` can discover Chrome/browser handlers before calling `startActivity`.

Android shows the existing `Save` credential button even when the server-side desktop remember-login flag is disabled. Saved credentials are stored in Android app-private `credentials.txt`, reloaded into the shared login fields on the next app launch, and remain excluded from bundled APK cache assets.

Android hides last-login host/IP information by default and does not expose the desktop `Hide IP` toggle. That keeps the login panel uncluttered and preserves the privacy-oriented behavior on a shared phone screen.

The legacy Android settings panel keeps its own three-tab Social/Game/Android layout. The Game and Android tabs already label the active panel, so the extra classic Android-only headings are skipped to keep the first option row readable in portrait. Desktop and web settings layout still render their headings normally.

Android login status messages dismiss the soft keyboard before drawing on the existing-user/recovery panels. Local validation now reports missing username/password immediately instead of silently returning with the keyboard still open, and the existing-user field labels hide while a status is visible so the compact mobile layout stays readable. If an Android player chooses an unreachable manual server, startup now stops on a clear selected-server error instead of hanging or crashing before login. Backgrounding from the login screen and relaunching from the app icon resumes the existing game task, refreshes fullscreen/network/focus state, and keeps input usable. The Android touch layer maps a single terrain tap into the shared client's normal `LANDSCAPE_WALK_HERE` path; the auth smoke can verify this by comparing the saved DB position before and after a tap/logout. The same tap path reaches shared NPC, scenery, and inventory menu actions: the smoke helper can temporarily enable app-private target logging, tap projected NPC/object/item coordinates, and assert resulting shared `NPC_TALK_TO`/`NPC_ATTACK*`, `OBJECT_COMMAND1`, `OBJECT_USE_ITEM`, `NPC_USE_ITEM`, `ITEM_USE`, and `ITEM_USE_ITEM` actions in logcat. Long-press now explicitly synthesizes the shared right-click path, opens the normal `Choose option` menu, suppresses the release tap so the player can review options, and lets the next tap choose a row. Android also has early touch-close hooks for welcome, server-message, and wilderness-warning dialogs so scaled phone taps do not depend on the classic frame's narrow text hitbox. Logging out from the in-game settings panel returns Android to the branded login home, closes stale game overlays, and leaves the existing-user form/keyboard usable without an app restart.

Android launcher branding uses the modern platform path while keeping old-device fallbacks. Android 8+ reads the adaptive launcher icon from `res/drawable-anydpi-v26/ic_launcher.xml`, with a dark background and the Voidscape cracked-`V` foreground. Android 12+ reads `VoidscapeLaunchTheme` from `res/values-v31/styles.xml`, using `windowSplashScreenAnimatedIcon` for a centered app-owned launch mark and `voidscape_launch_background` for the dark scene preview between the platform splash and the updater layout. Verification screenshots live at `/tmp/voidscape-android-launch-branding-v3/manual` for the cold-start/icon frames and `/tmp/voidscape-android-launch-branding-v3-smoke` for the wrapper/login smoke set. Android portrait is supported player-facing UI, so launcher and in-game QA should verify both portrait and landscape frames instead of forcing a landscape-only handoff.

Android native wrapper screens tolerate system dark mode and a larger Android font scale on the current emulator matrix. Baseline `voidscape_api35` and small `voidscape_small_api35` were both checked with `font_scale=1.3` and `cmd uimode night yes`; ready/play, server picker, manual server, login handoff, and bad-server screenshots remained readable without clipped text. Proof screenshots are `/tmp/voidscape-android-dark-font-v1`, `/tmp/voidscape-android-dark-font-small-v1`, and the small manual-dialog follow-up `/tmp/voidscape-android-dark-font-small-v1/manual-proof`.

## Promo recording

`scripts/record-android-promo.sh` records the emulator display directly instead of capturing the macOS window. Its guided default session temporarily uses a `1080x1920 @ 320dpi` natural display, records silent `1080x1920` portrait and `1920x1080` landscape WebM sources at 30 fps / 16 Mbps, transcodes them to H.264 MP4, validates dimensions and duration, and restores the previous display size, density, rotation, touch indicators, notification heads-up setting, and stay-awake state on success or interruption. The five prompts cover portrait HUD/drawers, portrait AFK Monitor, landscape HUD/drawers, portrait Christmas-cracker reels, and landscape reels; the recorder owns capture only, while the person recording performs natural taps. When the Android debug client is pointed at a local host, the script can use a separate local voidbot admin to deliver real item `575` crackers to the online promo character through the existing staff item command. It refuses cracker preparation for non-local endpoints. Individual MP4 clips, raw WebM files, `SHOT-LIST.md`, `manifest.json`, and an optional silent 16:9 rough cut are written below the selected output directory.

```bash
# Log the promo character into the local Android client first, then run:
scripts/record-android-promo.sh --player wbtest

# Inspect prompts or prove one capture non-interactively:
scripts/record-android-promo.sh --list-shots
scripts/record-android-promo.sh --clip portrait-hud --auto-stop 3 --skip-cracker-prep --no-montage --out /tmp/android-promo-proof
```

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
- Network smoke: launch on Wi-Fi, background/resume the app, require a post-resume chat marker in server `chat_logs`, require exactly one `GameActivity`, reconnect after a brief network drop, and verify bad host/port failure is understandable.

## Roadmap Checklist

This is the working Android punch list. The standard loop for each visual/input change is: build APK, reinstall on `voidscape_api35`, clear app data when first-run behavior changed, capture screenshots, compare against PC/launcher intent, iterate, then record what changed.

### Emulator and Visual QA Loop

- [x] Install Android Emulator and an ARM64 system image on the local Mac.
- [x] Create a dedicated AVD: `voidscape_api35` using Android 35 Google APIs ARM64, medium phone, ~2 GB RAM baseline.
- [x] Create a dedicated automation AVD: `voidscape_atd35` using Android 35 AOSP ATD ARM64. Use this for headless smoke runs; the Google APIs image can trigger System UI/Google-service ANRs during long scripted runs.
- [x] Confirm fresh APK install reaches the Android cache/bootstrap screen.
- [x] Confirm release endpoint policy defaults fresh installs to `voidscape.gg:43596`, migrates the exact former public IP pair on upgrade, and preserves custom endpoints (`tmp/android-slice8a-endpoint-bootstrap-rerun`).
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
- [x] Keep the world renderer, game rules, and shared packet paths classic while allowing the native Android custom-UI shell to use the documented thumb-first navigation contract.

### Account and Login Flow

- [x] Decide Android's public account path: in-client character creation for new username/password characters; recovery remains a portal handoff.
- [x] Enable client packet registration on launch configs so `Create Account` does not lead to a disabled path.
- [x] Add a clear `Create Account` path that opens the shared in-client registration form and prefills Existing User after success.
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
- [x] Zoom gesture. Native Android zoom is now a two-finger pinch through `ScaleGestureDetector`, and one-finger scroll no longer edits `C_LAST_ZOOM`. Emulator smoke waits for the in-game NPC projection, captures baseline `ANDROID_SMOKE_ZOOM` state, and proves a one-finger `ANDROID_SMOKE_ZOOM_DRAG_*` swipe does not change zoom. Strict pinch assertion is gated by `ANDROID_SMOKE_MANUAL_PINCH_SECONDS` plus `ANDROID_SMOKE_REQUIRE_PINCH=1` on a physical Android device because plain ADB input cannot synthesize real multi-touch pinch telemetry.
- [x] Split-rail hub and attached drawers. `tmp/android-mobile-hub-final6` exercised all seven rail controls in portrait and landscape, the stable Stats/Quests/Loot/Beasts family, bounded list scrolling, same-side connectors, 48dp targets, orientation restore, and connector/world-click consumption.
- [x] Native chat sheet filters. `tmp/android-mobile-chat-final3` exercised `All`, `Public`, `Quest`, `Global`, and `PM` through `ANDROID_SMOKE_CHAT_LAYOUT`, with 48dp filter geometry and no world fallthrough.
- [x] Native chat message entry and send. `tmp/android-mobile-chat-final3` opened the sheet/composer, showed the real IME, emitted exactly one normal chat send, kept the composer above `keyboardTop`, and proved Back dismisses the IME before the sheet.
- [x] Void Glass bank open, scroll, deposit, withdraw, search, presets/loadouts, close. `tmp/android-mobile-bank-final3` covered 241 seeded items, all seven wrapped tabs, real-IME search/clear/Back, grid drag, withdraw, loadout save/load, deposit, deposit-all, persistence, close, and fixture restore.
- [x] Responsive native shop meets the practical usability bar. `tmp/android-mobile-shop-narrow-final7` passed native portrait geometry, selection, buy/sell, fixed-grid no-scroll, and the narrow-landscape 3x2 side-action pressure case. Its display-override cleanup/logout tail did not complete, so a clean full rerun remains a higher release-gate follow-up.
- [ ] Equipment tab and worn item interactions. Voidscape currently runs with `want_equipment_tab=false`, so the active inventory wield/remove path remains the relevant contract; a same-revision `--only-auth-equipment` rerun is deferred.
- [ ] Prayer/spell tab selection and casting. The direct panel/list paths, bounded 4/3-row geometry, and earlier shared-action smoke are implemented; a same-revision focused `--only-auth-magic-prayer` run is deferred.
- [x] Native World Map open, pan, zoom, search, close. `tmp/android-mobile-world-map-final` passed bounded portrait/landscape geometry, control sizing/containment, zoom, pan, Varrock search, close, and the Account/logout flow on the completed-tap-latch implementation.
- [ ] Settings/wrench screen open, change options, persist options. Account and cancel-safe/confirmed logout are covered by `tmp/android-mobile-hub-final6` and `tmp/android-mobile-world-map-final`; the focused option-persistence/relogin sweep is deferred.
- [x] Ground-item labels and rare-drop beams readability. Smoke seeds a Rune battle axe, drops it through a smoke-gated path, asserts the rare beam and label are inside the classic framebuffer, captures `102-auth-ground-loot-readable`, and restores the auth player's inventory/cache rows.
- [x] Wilderness combat target selection. Smoke moves the auth player to wilderness, spawns a temporary cinematic player, long-presses the projected player target, selects the logged `PLAYER_ATTACK_*` row, and restores the DB fixture.
- [ ] Eating, potting, spell casting, and running during PvP-style stress.
- [x] Confirm logout is reachable only through `Settings -> Account`, Cancel does not log out, and only the second-step confirmation ends the session. Covered in `tmp/android-mobile-hub-final6` and `tmp/android-mobile-world-map-final`.
- [x] Confirm ordinary app return crosses the proactive reconnect threshold without losing the session or the first safe HUD action. `tmp/android-mobile-lifecycle-final31` and `tmp/android-mobile-lifecycle-final32` recorded reconnect response `86`, identical queued/replayed coordinates, Chat open after replay, and no welcome modal. The exhaustive persisted-chat-marker, duplicate-Activity, network-loss, and OEM lifecycle matrix is deferred to the full release gate.

### Layout, Scaling, and Readability

- [x] Verify mobile framebuffer sizing on the medium phone AVD: portrait keeps `512px` width and grows full height to device aspect; landscape keeps `346px` full height and widens logical width.
- [x] Verify phone landscape widens the logical framebuffer and fills the surface without meaningful side gutters unless the 1152px cap is reached.
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
- [ ] Verify native context menus use 48dp rows, remain between the split rails and above the compact chat launcher, paginate when needed, and select the intended action near every screen edge.

### Performance and Low-End Behavior

- [ ] Record cold start time to bootstrap screen.
- [ ] Record cache install time from fresh app data.
- [ ] Record time from `Play` tap to login screen.
- [ ] Idle on login for several minutes and watch for crashes/log spam.
- [ ] Idle in game after login and watch CPU/frame pacing.
- [ ] Walk through Lumbridge/Edgeville and watch responsiveness.
- [ ] Stress bank/inventory/shop scrolling.
- [ ] Stress combat and spell effects.
- [ ] Background the app for 30 seconds, resume, verify rendering/input recover, and prove a fresh server roundtrip rather than checking only `players.online`.
- [ ] Background for several minutes, resume, and verify bounded session preservation plus foreground reconnect behavior under real OEM scheduling.
- [ ] Verify screen stays awake only while appropriate.
- [ ] Check APK size and installed app storage use.
- [ ] Profile memory footprint on the 2 GB AVD.
- [ ] Profile CPU/battery in Android Studio once available.
- [ ] Remove or defer expensive wrapper effects unless they clearly improve the mobile experience.

### Build, Release, and Updates

- [x] `scripts/build-android.sh` builds a debug APK. The final forced `scripts/build-android.sh --debug --rerun-tasks` run completed 34/34 tasks on 2026-07-10.
- [x] Debug APK installs on an emulator.
- [x] Add a documented command for launching `voidscape_api35`.
- [x] Add a documented command for reinstalling the latest APK and taking screenshots.
- [x] Add release signing config outside Git (local upload keystore plus macOS Keychain passwords; never committed).
- [ ] Produce a release APK/AAB path when distribution is chosen.
- [x] Keep public portal APK downloads wired to the configured APK artifact and verify `/api/public` publishes a hash when the file exists.
- [x] Replace hardcoded public IP with stable `voidscape.gg` and migrate the exact previous public endpoint on upgrade.
- [x] Decide Android update strategy: Google Play production is primary; a release-signed direct APK is the tested fallback.
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

- [ ] Decide whether Android should ship for beta at all or stay internal until real-device QA is green.
- [x] Android players create new username/password characters in-app; recovery stays in the portal/browser flow.
- [ ] Decide whether Android gets a separate download page with screenshots and sideload instructions.
- [x] Android uses mobile-specific navigation and touch affordances while keeping world content, gameplay rules, and authentic/classic mode unchanged.
- [ ] Decide what “good enough for beta” means: login only, basic skilling, bank/shop, or PvP-ready.
- [ ] Decide if mobile PvP needs explicit support or if Android is mainly for casual/skilling players at first.

### Release Packaging

- Debug APKs remain the emulator/internal QA default: `scripts/build-android.sh --debug`.
- Public APK bundles should use `scripts/build-android.sh --release` or `scripts/package-launch-staging.sh --android-release`.
- Google Play releases should use `scripts/build-android.sh --play-release`, then upload `Android_Client/Open RSC Android Client/build/outputs/bundle/release/voidscape.aab` only after `scripts/check-android-play-release.sh --aab "Android_Client/Open RSC Android Client/build/outputs/bundle/release/voidscape.aab" --server-config <target-server.conf> --current-play-version-code <highest-uploaded-code> --expected-signer-sha256 <Play-upload-certificate-SHA256>` passes. As verified across Play Console's complete bundle history on 2026-07-12, exactly version codes `1` through `8` have been uploaded, `8 / 1.0.7` is active in production, and no testing track has a higher code. The launch candidate is therefore `9 / 1.0.8`; the registered upload SHA-256 is `3B:AE:B2:F4:6D:68:55:DD:9E:21:DA:27:80:8C:02:90:B1:32:9F:07:27:23:F4:39:DC:A2:07:A4:0A:14:2E:D7`. Google Play re-signs installed builds with app-signing SHA-256 `7F:1E:AC:B9:71:0D:CF:EE:AC:0C:EE:3D:AC:8D:2A:01:A6:FF:A3:45:D5:08:96:EB:F1:EA:82:A0:78:ED:16:D0`; use that app-signing fingerprint, not the upload fingerprint, for any Android OAuth/client-ID registration.
- Release source and the enforced launch preset speak protocol `10132`. On 2026-07-12 the exact clean, signed `9 / 1.0.8` AAB (SHA-256 `e956a14c1d8355127365f9ab50d7c52a31b78c2829c5c128768232f85d26534e`) became available on Play Internal Testing, then entered Google review for a 100% Production rollout across all targeted countries. Play quick checks passed and Managed Publishing is off, but review status is not public availability: production still serves `8 / 1.0.7` until Google approves it. No physical Android device was available; the owner explicitly waived that gate and accepted the existing emulator evidence without converting it into a physical-device pass. Do not activate the `10132` production server until ordinary players can receive code `9`.
- Release signing is configured through `VOIDSCAPE_ANDROID_UPLOAD_KEYSTORE`, `VOIDSCAPE_ANDROID_UPLOAD_STORE_PASSWORD`, and `VOIDSCAPE_ANDROID_UPLOAD_KEY_PASSWORD`, matching `voidscape.android.uploadKeystore.*` Gradle properties, or the local macOS Keychain services documented in `~/.voidscape/android-signing`.
- `assembleRelease` / `bundleRelease` fail unless signing is configured. `VOIDSCAPE_ANDROID_ALLOW_UNSIGNED_RELEASE=1` exists only for local unsigned release experiments.

## Deferred Hardening

- Replace deprecated wrapper APIs (`AsyncTask`, old fullscreen flags, `getDrawable(int)`, bare `Handler()`) after the build and first-launch flow are stable.
- Decide the public distribution channel: direct APK download, Play internal/closed testing, or both.
- Add a remote Android cache endpoint only when it can be served over HTTPS with versioned cache checksums.
- Add crash/ANR telemetry before public beta if privacy policy and player disclosure are ready.
