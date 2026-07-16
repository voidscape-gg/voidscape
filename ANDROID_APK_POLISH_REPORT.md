# Voidscape Android APK Polish Report

Date: 2026-07-10

## Status

The Android client is now a substantially stronger automated/emulator candidate. It keeps the classic RSC world, gameplay, and packet model while improving app-switch continuity, touch routing, login recovery, socket/audio ownership, first-launch cache setup, saved-login security, direct-APK promotion safety, and the native in-game interaction shell.

The highest-priority result is verified: switching from the game to another Android app and returning no longer immediately disconnects the player. Android sessions now have a ten-minute activity/reconnect grace, and an emulator round-trip into Android Settings for 130 seconds returned directly to the live game. A separate saved-login smoke also switched apps for 35 seconds and reconnected successfully. Android process death still ends the in-memory client and must return through normal login.

The current implementation meets the reduced practical-usability bar agreed for this pass. The split-rail hub, attached drawers, native chat sheet, Void Glass bank, and native World Map have current focused emulator evidence. The responsive shop also passed its functional and narrow-layout checks; only the harness cleanup/logout tail was incomplete. A practical app-switch run proved the retained-session reconnect and first safe HUD action on the current code. `scripts/build.sh` passed, and a forced `scripts/build-android.sh --debug --rerun-tasks` completed all 34 tasks. Focused same-revision Magic/Prayer, equipment, settings-persistence, and login/dialog reruns were intentionally deferred, along with exhaustive OEM Activity recreation, network-loss, and physical-device coverage. Production promotion still requires a clean upload-signed release APK, the trusted signer fingerprint, and physical-device QA. The working packaging changes report Android version `7` / `1.0.6`, `minSdk 23`, and `targetSdk 35`; those version/minimum-SDK edits predated this polish commit series and remain separate workspace changes.

## Current mobile UX reset — usable emulator candidate

### Summary

The Android-only Voidscape HUD has been redesigned around thumb reach instead of reusing desktop tab placement. Stats, Map, Social, and Settings live on a left rail; Inventory, Magic, and Prayer live on a right rail. Selecting a destination opens one bounded drawer immediately inside the same rail. The drawer is vertically centered on its opener where space permits, clamped between the location/chat safe bands, and joined to the active icon by a short highlighted connector that is part of the consumed hit region. This keeps the control and content visibly attached without turning portrait panels into full-height columns. The permanent row of chat tabs is replaced by a compact Chat launcher and an expandable sheet with All, Public, Quest, Global, and PM filters plus the shared composer. Logout is removed from the HUD and routed through `Settings -> Account -> Log out -> confirmation`.

All primary mobile controls derive from a physical 48dp minimum. The Android viewport now computes cutout, navigation-bar, and mandatory-gesture insets once and shares the resulting transform between rendering and inverse input mapping. The Activity retains `SOFT_INPUT_ADJUST_NOTHING`, so opening the real soft keyboard does not resize the framebuffer; its IME inset is mapped into logical `keyboardTop`, and the expanded chat sheet/composer rises above that line. Native touch menus use the center lane between the rails, keep 48dp rows, and paginate rather than shrinking. Existing Voidscape icon assets are reused; no new generated bitmap art is required for this pass.

Each drawer is a viewport sized for its content rather than a desktop page. Inventory uses a 5x6 portrait grid and 6x5 landscape grid. Magic and Prayer show four touch rows in portrait and three in landscape, with scrolling for the remaining entries and a persistent guidance/status footer. Stats uses 48dp sub-tabs and skill rows with an independently scrolling skill viewport. Social keeps 48dp Friends/Ignore tabs and a pinned Add action; Settings uses 48dp scrolling option rows with a pinned Account action; Map stays compact with a full-size World Map action. The shared data and actions behind every panel remain unchanged.

The native shop now reflows around the available safe framebuffer instead of scaling the desktop dialog. Portrait stacks a fixed 8x5 item grid over its action block; landscape keeps all five grid rows and places the action block alongside it. Wide landscape uses one six-cell row for each Buy/Sell group, while narrow landscape wraps those groups to 3x2 instead of collapsing the grid. Item cells, Close, Buy/Sell labels, and `1`/`5`/`10`/`50`/`X` amount controls share 48dp geometry and continue through the existing shop packet and price/stock paths. Void Glass bank geometry now also adapts to native Android: touch-sized title/search/clear/close/tabs/cells/actions, fewer columns when width is constrained, stacked portrait actions with full-width Deposit All, one-row landscape actions, drag scrolling, and paginated 48dp quantity menus.

The supporting mobile surfaces follow the same contract. The full World Map is a centered, bounded safe-frame whose title/Close, Set and five waypoints, Search, zoom pair, Reset, and Tiles actions derive from 48dp; controls wrap on constrained widths, portrait height is capped, and a completed short tap is latched across map render frames. Home, existing-user, and character-creation forms derive fields and actions from 48dp geometry and reposition above the IME. The report flow uses an IME-aware name sheet followed by paginated single-column rules (up to five visible in portrait and three in landscape), and requires an explicit selected-rule Send report action before reusing packet `206`. Password, recovery-question, recovery-answer, welcome, and server-message dialogs are bounded to the usable viewport and expose touch-size fields/actions instead of relying on their old narrow text bands. The native bootstrap/update wrapper scrolls when needed, fills narrow widths with 16dp margins, and caps its centered card at 340dp on wider screens.

This reset is gated to the native Android client with custom UI enabled. PC, TeaVM/web, authentic/classic UI, world rendering, gameplay calculations, packet opcodes, chat sends, and logout packets remain outside the visual/layout divergence.

### Scoped implementation files

- `Client_Base/src/orsc/mudclient.java` — split rails, attached drawers, panel touch reflow, chat sheet, responsive native shop, touch-menu layout, Account/logout confirmation, background polling pause and timer reset, Back order, and smoke telemetry.
- `Client_Base/src/orsc/multiclient/ClientPort.java` — platform conversion from dp to logical client pixels.
- `Client_Base/src/com/openrsc/interfaces/misc/BankInterface.java` — responsive Void Glass bank grids, actions, drag scrolling, and paginated mobile menus.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/AndroidClientViewport.java` — safe content bounds, immutable viewport metrics, transform, and dp conversion.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/RSCBitmapSurfaceView.java` — safe-inset capture, one render/input viewport snapshot, current-Activity-only target sizing, real text-editor bridge, and actual IME visibility tracking.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/android/render/InputImpl.java` — inverse touch mapping through the shared viewport transform, direct native chat ACTION_UP routing, drawer capture, and completed World Map tap latching.
- `Client_Base/src/orsc/graphics/gui/WorldMapPanel.java` — bounded native safe-frame, wrapped touch controls, and shared renderer/hit-test geometry.
- `Android_Client/Open RSC Android Client/src/main/AndroidManifest.xml` — `GameActivity` single-task ownership contract.
- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` — edge-to-edge/cutout setup, viewport refresh, background-duration handoff, and lock-protected volatile retained-client/current-owner ownership so stale Activities cannot terminate the replacement owner.
- `Client_Base/src/orsc/graphics/gui/Menu.java` — Android touch-row sizing and pagination support where used by the native menu path.
- `Android_Client/Open RSC Android Client/src/main/res/layout/{updater,applicationupdater}.xml` and responsive styles — scrolling, centered, width-bounded launch/update cards.
- `scripts/android-smoke.sh` — telemetry-driven hub, chat, responsive shop, settings/logout, viewport, and lifecycle gates, including a post-resume `chat_logs` roundtrip and single-`GameActivity` assertion.
- `docs/subsystems/android-client.md`, `docs/UI-STYLE-GUIDE.md`, `docs/DIVERGENCE.md`, and this report — contract, commands, divergence, and release checklist.

### Reset acceptance status

| Gate | Expected evidence | Final evidence/path | Status |
|---|---|---|---|
| Shared build | `scripts/build.sh` | Final 2026-07-10 shared-client integration build completed successfully | **Pass** |
| Android debug APK | `scripts/build-android.sh --debug --rerun-tasks` | Final forced debug build completed all 34 tasks successfully | **Pass** |
| Split-rail hub | `--only-auth-chat-tabs`; seven controls in portrait/landscape, 48dp, correct-side drawer, no fallthrough | `tmp/android-mobile-hub-final6`; all seven rails, Stats-family tabs/scroll, connectors, both orientations, restore, and Account/logout modal | **Pass (emulator)** |
| Chat sheet | `--only-auth-chat-send`; five filters, composer, exactly one send, stable framebuffer, `composerBottom <= keyboardTop`, IME-then-sheet Back order | `tmp/android-mobile-chat-final3`; all filters, real IME, one send, Back order, logout cleanup | **Pass (emulator)** |
| Magic/Prayer | `--only-auth-magic-prayer`; direct list selection and 48dp rows | Bounded 4-row portrait/3-row landscape implementation is present; no same-revision focused rerun was required for the reduced usability gate | **Deferred focused rerun** |
| Void Glass bank | `--only-auth-bank`; responsive grid/actions, drag scroll, quantity-menu pagination, transactions and close | `tmp/android-mobile-bank-final3`; 241 items, seven wrapped tabs, real IME, drag, withdraw/deposit, loadout save/load, persistence, close | **Pass (emulator)** |
| Responsive shop | `--only-auth-shop`; `layout=native`, `targets48=true`, portrait and narrow-landscape item select/buy/sell, fixed 8x5 grid, close | `tmp/android-mobile-shop-narrow-final7`; geometry, selection, buy/sell, no-scroll, and narrow 3x2 side rail passed; the display-override cleanup/logout tail did not complete | **Usable partial pass** |
| Native World Map | `--only-auth-world-map`; bounded 48dp controls, useful map canvas, pan/zoom/search/Back-close, completed-tap latch | `tmp/android-mobile-world-map-final`; portrait/landscape geometry, zoom, pan, Varrock search, close, Account, and cancel/reopen/confirmed logout passed with no control overlap | **Pass (emulator)** |
| Equipment | `--only-auth-equipment`; active inventory wield/remove path and fixture restore | Active inventory wield/remove contract is unchanged; no same-revision focused rerun was required for the reduced usability gate | **Deferred focused rerun** |
| Settings/Account | `--only-auth-settings`; Account sheet, cancel-safe confirmation, persistence and relog | Account/logout cancellation and confirmation covered by `tmp/android-mobile-hub-final6` and `tmp/android-mobile-world-map-final`; option persistence/relogin remains a focused follow-up | **Usable partial pass** |
| Login/wrapper/dialogs | bootstrap, `--only-auth-login`, and manual report/dialog captures; narrow/wide portrait and landscape, IME, create/recovery/report/dialog actions | Existing implementation and earlier polish evidence retained; no same-revision focused dialog sweep was required for the reduced usability gate | **Deferred focused rerun** |
| App switch/viewport | Practical >=10s background return; proactive retained-session reconnect; one safe HUD intent queued and replayed exactly; game shell restored without a welcome interruption | `tmp/android-mobile-lifecycle-final31` and `tmp/android-mobile-lifecycle-final32`; reconnect response `86`, identical queued/replayed coordinates, Chat reopened, and no welcome modal. The exhaustive marker/single-Activity/network-loss tail was not used as the reduced gate | **Pass (practical emulator gate)** |

The debug APK is a usable emulator candidate, not a production-signed release. Physical-device checks, OEM lifecycle/network behavior, release signing, and promotion verification remain separate release gates.

## Improvements

### Mobile UI and input ownership

- Replaces the mixed top-tab/bottom-chat layout with stable left and right thumb rails in portrait and landscape.
- Opens panels beside the control that owns them and keeps one destination active at a time.
- Bounds and centers each drawer around the active icon, draws a filled connector between them, and consumes both regions from world input.
- Uses compact panel-specific viewports: 5x6/6x5 Inventory, 4/3-row Magic and Prayer lists, scrollable Stats/options, and pinned Social/Account actions.
- Gives Inventory, Magic, Prayer, Stats, Social, Map, Settings, chat, menus, and destructive/account actions a consistent 48dp touch baseline.
- Collapses chat into one launcher and a focused five-filter sheet with an explicit composer.
- Routes chat filter/composer ACTION_UP directly into the native sheet so reconnect or a cleared legacy click pulse cannot swallow the first tap.
- Keeps the canvas framebuffer stable while the IME opens and lifts the composer above the real keyboard inset.
- Removes accidental logout from the HUD and introduces an Account sheet plus confirmation.
- Reflows the fixed 8x5 shop between stacked portrait and side-by-side landscape layouts with 48dp item, amount, and close actions; narrow landscape wraps each side action group to 3x2 without stealing item rows.
- Reflows Void Glass bank controls and cells, stacks portrait actions, and uses drag scrolling plus paginated quantity menus.
- Reflows the full World Map into a bounded, aspect-capped card with wrapped 48dp controls and a completed-tap latch.
- Reflows login, report, password/recovery, welcome/server-message, and native wrapper surfaces around width, orientation, and the real IME.
- Consumes rail/drawer/chat/menu touches so they cannot also walk, rotate the camera, or select a world entity.
- Shares one safe-inset viewport transform between drawing and touch mapping across rotate/resume/system-bar changes, and lets only the currently bound Activity resize the retained client.
- Retains the existing Voidscape visual language and icon assets rather than introducing a separate mobile skin.

The agreed emulator usability gate is complete as recorded above. Deferred focused and physical-device acceptance remains explicitly listed rather than being inferred from the emulator result.

### App switching and reconnect

- Android players receive a ten-minute server activity timeout and closed-channel reconnect grace; desktop and web timeout behavior is unchanged.
- While Android marks the Activity backgrounded, the retained client pauses socket polling and refreshes its last-write/read-timeout bookkeeping instead of flushing, draining incoming packets, or sending keepalives off-screen; audio is suspended too.
- The server's ten-minute activity timeout preserves an open-but-idle Android session, and its ten-minute closed-channel grace lets guarded resume reclaim the player if the old socket closes.
- A switch shorter than ten seconds resumes the retained stream. After at least ten seconds in the background, a logged-in return proactively closes the potentially stale stream on the game thread and enters retained-session reconnect instead of waiting for a foreground dead-socket timeout.
- Successful reconnects replace and close stale sockets without showing a lingering connection-lost overlay.
- If a reconnect is still completing when the player taps a rail destination or Chat, that safe launcher intent is retained for at most 120 seconds, revalidated, and applied directly after reconnect. World, drawer-content, and other gameplay taps are never replayed.
- Chat filter/composer input consumes native ACTION_UP directly, with a 15-second post-reconnect grace for the transient state between retained-session login and the fully visible shell.
- `GameActivity` is `singleTask`; its volatile retained-client/current-owner pair is lock-protected, and only the current owner may terminate the shared thread/connection.
- Duplicate launcher delivery no longer creates a second Activity or game session, and a stale Activity teardown cannot kill the rebound client or overwrite its settled viewport target.
- Explicit logout still closes the session immediately and returns to a usable login form.

Current practical evidence: `tmp/android-mobile-lifecycle-final31` and `tmp/android-mobile-lifecycle-final32` show an app return after the reconnect threshold, retained-session response `86`, one safe HUD intent queued and replayed at identical coordinates, Chat open after replay, and no welcome modal. The fuller Activity-recreation, persisted-chat-marker, network-loss, and OEM battery-behavior matrix remains deferred. Earlier longer-duration evidence is retained at `tmp/android-apk-polish/lifecycle-130s-settings`, `tmp/android-apk-polish/network-audio-lifecycle-final-2`, and `tmp/android-apk-polish/credentials-encrypted-final-5`.

### Login, keyboard, Back, and touch UX

- Login fields reliably reopen the soft keyboard after validation or authentication errors.
- Canvas Cancel paths dismiss the keyboard, and connection failures leave the form ready for immediate retry.
- Android Back closes a context menu before clearing an active item or spell selection; last-spell memory is preserved.
- Hardware/synthetic special keys dispatch once per press, and key-release pulses use the Android view queue instead of allocating timer threads.
- A swipe captured by an open panel or scrollable message area no longer rotates or pitches the world behind it.
- One-finger gestures cannot accidentally trigger pinch zoom; real two-finger pinch remains a physical-device check.
- World art, gameplay calculations, and underlying menu actions are unchanged; the Android presentation now follows the mobile-shell contract above.

Evidence: `tmp/android-apk-polish/login-touch-final`, `back-selection-final`, `world-map-input`, `camera-routing-final-3`, and `zoom-one-finger-final-2`.

### Network, lifecycle, and audio stability

- Publishing a replacement connection closes the superseded socket.
- Native background state intentionally short-circuits socket flush, incoming-packet drain, and keepalive work while refreshing local connection timers; a foreground return after at least ten seconds proactively replaces the socket through guarded server session resume, while a shorter switch resumes the retained path.
- Logout drains its confirmation packet before closing the connection.
- Writer threads are named, race-safe, and terminate on replacement, logout, interruption, setup failure, or write failure.
- Android connectivity is based on validated networks rather than merely present network handles.
- Sound preparation is asynchronous, uses game-audio attributes, and is limited to eight active players.
- Audio players are released on completion, error, backgrounding, logout, and Activity destruction.
- Activity references used for external links are weakly held.

Current practical retained-session evidence is recorded above. Earlier network/audio evidence remains at `tmp/android-apk-polish/network-audio-magic-review-final` and `network-audio-lifecycle-final-2`; exhaustive network-loss and OEM lifecycle behavior is still a physical/follow-up gate.

### First launch and bundled cache

- Bundled cache files are copied through same-directory temporary files, flushed, and atomically renamed.
- A completion marker is tied to the installed APK version/install time.
- Normal launches skip unchanged cache copies.
- Missing or truncated startup-critical archives trigger repair.
- Interrupted/failed installs clear the marker and expose Retry/Close recovery instead of accepting a partial cache.
- Host and port are committed as one canonical endpoint transaction; compatibility mirrors are repaired together and incomplete debug overrides are ignored.
- The required `video/library.orsc` archive is included in bootstrap validation.

Evidence: `tmp/android-apk-polish/bootstrap-atomic-final` proves injected mid-copy failure, recovery, exact archive hashes, marker skip, truncated-file repair, canonical endpoint repair, and rejection of an incomplete override.

### Encrypted saved logins

- “Remember” is an explicit opt-in and commits only after successful authentication.
- Wrong-password attempts persist no credential envelope.
- Android stores one versioned AES-256-GCM envelope under `noBackupFilesDir` using a non-exportable Android Keystore key and a fresh IV/AAD.
- Writes use `AtomicFile`, reopen/decrypt/semantic verification, and rollback on failure.
- Legacy `credentials.txt` and `accounts.txt` migrate only when no encrypted state or tombstone exists; plaintext is deleted only after verified commit.
- Corrupt ciphertext or a missing key fails closed without plaintext fallback.
- Forget/reset writes an authoritative tombstone, preventing deleted legacy credentials from reappearing.
- Backup and device-transfer rules exclude app data.
- A currently logged-in saved account can be forgotten without ending the active session.

Evidence: store-level emulator tests covered migration, Unicode/comma secrets, ciphertext freshness, corruption, tombstone, forget, and clear. `tmp/android-apk-polish/credentials-encrypted-final-5` proves wrong-password non-persistence, encrypted save, real app-switch reconnect, cold-restart prefill, explicit opt-out, tombstone creation, and empty fields after the next cold restart.

### Performance and resource ownership

- Marker-hit launches avoid recopying the full bundled cache.
- Audio preparation no longer blocks its caller and active players are bounded.
- Per-key timer-thread allocation was removed.
- Socket, writer, Activity, and audio ownership now have explicit termination paths.
- The mobile shell reuses existing cached Voidscape icons instead of adding another asset pack.
- Viewport geometry is refreshed from surface/inset changes and reused by rendering and input; IME movement changes the chat clamp without reallocating the logical framebuffer.
- Mobile QA telemetry remains behind app-private smoke flags instead of logging every production interaction.

No quantified startup, frame-time, CPU, memory, thermal, or battery benchmark was run, so this report makes no numerical performance claim.

### Mobile UX build and QA automation

- Hub automation consumes renderer-reported `ANDROID_SMOKE_HUB_LAYOUT` and `ANDROID_SMOKE_HUB_ACTION` rectangles rather than fixed taps.
- Chat automation consumes `ANDROID_SMOKE_CHAT_LAYOUT`, including all filters, composer, IME state, `keyboardTop`, and draft state, and requires exactly one normal chat-send event.
- Shop automation consumes the renderer's native dialog/grid/action rectangles and requires `layout=native` plus `targets48=true` before item selection and buy/sell actions.
- Shop automation temporarily applies a narrow phone landscape pressure profile and requires the fixed 8x5 grid plus 3x2 wrapped side actions before restoring the display override.
- World Map automation reads native safe-frame/control geometry, rejects undersized/overlapping/map-starved/overlong layouts, and drives zoom, pan, Search, and Close from renderer-reported targets.
- Viewport telemetry records density, scale, safe insets, logical size, 44/48dp conversions, IME inset, and logical keyboard top.
- Settings/logout automation follows the player-visible Account and confirmation sequence; bypassing it is treated as a regression.
- Lifecycle automation treats `players.online` only as a coarse background guard: after a 35-second interval it requires proactive retained-session reconnect, opens Chat through the preserved safe launcher intent, sends a unique public-chat marker through the direct native composer route, requires the server to persist it in `chat_logs`, then requires exactly one distinct `GameActivity` after duplicate launcher delivery.
- The final reset run must use `scripts/build.sh`, `scripts/build-android.sh --debug`, and the focused `scripts/android-smoke.sh` flags listed in the acceptance table. No raw Gradle invocation is an acceptance substitute.

### Build and direct-APK promotion

- The canonical build embeds schema-v3 provenance inside the signed APK and mirrors it in the JSON sidecar.
- Provenance contains the full commit, actual Android/shared/cache/build input digest, independent commit-tree digest, dirty state, and dirty-release override state.
- Release builds reject dirty relevant inputs by default and fail if the commit, input bytes, dirty state, or commit-tree digest changes while Gradle is running.
- Hidden modifications marked `assume-unchanged` or `skip-worktree` are still detected by comparing actual bytes/modes with Git commit blobs/modes.
- Promotion requires a valid non-debuggable APK, exactly one explicitly trusted signer, matching package/version/SDK/client-server data, matching hash/size, clean source, and sidecar equality with the provenance inside the signed APK.
- Staging endpoint rewrites opt into a visibly dirty, non-promotable artifact.
- The portal has no implicit local APK fallback; `PORTAL_ANDROID_APK` must point to the exact artifact that passed promotion.

The regression suite passes a mocked release-shaped positive fixture and rejects a real debug APK, malformed metadata, forged digest, forged commit, wrong signer, and a hidden dirty checkout. Real `apksigner` verification is exercised against the debug APK. No current clean upload-signed production APK was created because signing secrets and a clean release checkout are intentionally outside this pass.

## Bugs fixed

Current HUD-reset problems addressed in code and assessed to the practical emulator bar:

- Panel destinations were visually detached from where their content opened, especially after rotation; bounded drawers now center on and visibly connect to their owning icon.
- Permanent chat buttons competed with panel navigation for the bottom edge and were inconsistently aligned.
- Magic and Prayer shared a destination without making the requested internal list direct or obvious.
- Stats, Magic, and Prayer could become extremely tall portrait lists instead of bounded, scrollable touch viewports.
- Desktop-sized rows and icon targets were too small for reliable phone taps.
- UI taps could leak through into walking, camera, or world selection.
- Context menus could overlap rail/chat controls or shrink into dense rows on short screens.
- The desktop shop dialog exposed tiny item and amount hitboxes and did not reflow between phone orientations.
- Narrow landscape could choose a tall stacked shop action block and crush the fixed five-row item grid; it now keeps side actions and wraps each group to 3x2.
- Void Glass bank search, tabs, cells, action rows, scrolling, and long quantity menus retained desktop-sized interactions.
- The full World Map inherited desktop-sized overlay links and could become an overlong or overlapping portrait control strip; it now reserves a bounded map canvas below wrapped 48dp controls.
- Login, account-report, password/recovery, welcome/server-message, and wrapper cards retained fixed or narrow desktop-era geometry under short screens and the IME.
- Logout was exposed as a small immediate action instead of a deliberate account flow.
- The IME could cover a canvas composer, while resizing the surface would destabilize the classic framebuffer and touch transform.
- Render and input geometry could disagree around cutouts, system navigation, rotation, or resume.
- A stale pre-rotation Activity could deliver a late `SurfaceView` target and overwrite the replacement Activity's logical orientation.
- Background polling needed an explicit pause/timer-reset contract so an expected Android socket transition would not surface an off-screen reconnect overlay before guarded server resume.
- A background socket could look alive until its first foreground write, leaving the resumed UI frozen behind the read timeout; returns after ten seconds now enter retained-session reconnect proactively.
- Retained-client ownership was not volatile/owner-aware, so a stale Activity teardown could race a replacement Activity; non-single-task launch delivery also permitted duplicate Activity risk.
- Lifecycle QA treated `players.online` as connectivity proof even though that flag remains true during server reconnect grace; it did not prove a resumed request reached the server.
- A first safe HUD tap made while guarded reconnect was still completing could be lost; only rail/Chat launcher intent is now retained, revalidated, and applied directly.
- The first chat filter/composer tap after reconnect could be swallowed when the legacy one-frame click pulse cleared between render/input frames; native ACTION_UP now consumes those controls directly.

Previously completed stability/security fixes:

- VS-061: Android players disconnected after minimizing or switching apps.
- VS-073: lifecycle QA waited for the wrong Settings tab and never proved the background/resume path.
- Login keyboard did not reliably reopen after errors.
- A login IME could intermittently survive world entry and intercept a bottom-edge in-game tap.
- Cancel paths could leave the keyboard visible.
- Panel/message scrolling could also move the camera.
- Special keys could dispatch repeatedly and allocate timer threads.
- Android Back did not correctly prioritize menus and active selections.
- Reconnect could leak superseded sockets/writer threads.
- Logout could close before its confirmation packet drained.
- Sound players could block preparation or survive background/logout.
- Connectivity could treat an unvalidated network as usable.
- Every wrapper launch recopied the bundled cache.
- Interrupted bootstrap could leave partial files or a misleading completion state.
- Endpoint host/port mirrors could diverge.
- Android saved credentials were plaintext and could be persisted before authentication.
- APK promotion could trust a forged external sidecar or hidden dirty source bytes.
- The portal could expose a stale/debug local APK merely because the file existed.

## Files and systems changed

Native Android:

- `AndroidManifest.xml`
- `ApplicationUpdater.java`
- `CacheUpdater.java`
- `GameActivity.java`
- `InputImpl.java`
- `RSCBitmapSurfaceView.java`
- `AndroidCredentialStore.java`
- `soundPlayer.java`
- `Utils.java`
- `res/xml/backup_rules.xml`
- `res/xml/data_extraction_rules.xml`
- Android Gradle asset-source wiring for embedded provenance

Shared client and protocol implementation:

- `mudclient.java`
- `PacketHandler.java`
- `ClientPort.java`
- `CredentialStore.java`
- `CredentialSnapshot.java`
- `SavedCredentialAccount.java`
- `Network_Base.java`
- `Network_Socket.java`
- `Network_WebSocket.java`

Server session support:

- `GameStateUpdater.java`
- `RSCConnectionHandler.java`

QA, release, and documentation:

- `scripts/android-smoke.sh`
- `scripts/android-provenance.py`
- `scripts/build-android.sh`
- `scripts/check-android-apk-release.sh`
- `scripts/test-check-android-apk-release.sh`
- `scripts/check-prelaunch-readiness.sh`
- `scripts/package-launch-staging.sh`
- `web/portal/dev-server.mjs` (only the Android artifact availability behavior)
- `docs/BUGS.md`
- `docs/DIVERGENCE.md`
- `docs/OPERATIONS.md`
- this report

## Verification completed before the mobile HUD reset

These results remain useful stability/security regression evidence, but they do not fill the current reset acceptance table above.

- `scripts/build.sh` — passed after final shared-client integration.
- `scripts/build-android.sh --debug` — passed; current APK contains embedded schema-v3 provenance.
- `scripts/build-android.sh clean assembleDebug` — passed, proving provenance survives a Gradle clean.
- `scripts/build-web-teavm-spike.sh` — passed after the final shared credential/network changes.
- `scripts/test-check-android-apk-release.sh` — all positive/negative regression cases passed.
- Dirty `scripts/build-android.sh --release` — refused before Gradle/signing as designed.
- Explicit dirty/unsigned release probe — built only with both overrides and embedded `relevantInputDirty=true` plus `dirtyReleaseOverride=true`; signature verification failed, so it is non-promotable.
- Focused prelaunch release guard — one pass, zero failures; correctly recognized the dirty-input block.
- Canonical shell syntax, Python compilation, Node syntax, and `git diff --check` — passed.
- Android Settings round-trip for 130 seconds — returned in game and later logged out normally.
- Final encrypted-credential smoke — passed the complete save/reconnect/restart/forget/restart sequence.
- Cache interruption/repair/skip/endpoint smoke — passed.
- Network writer/audio/logout/magic-prayer smoke — passed.

## Known remaining issues and limits

- Hub, chat, bank, and World Map have focused emulator signoff; the final shared and forced Android debug builds pass. The shop meets the practical functional/layout bar despite an incomplete harness cleanup tail, and the retained-session app-switch path has practical current-code evidence. Same-revision focused Magic/Prayer, equipment, settings-persistence, and login/dialog sweeps remain deferred.
- The 48dp math and safe-inset telemetry still require physical confirmation on a cutout phone, a gesture-navigation phone, and a three-button-navigation device in both orientations.
- Real IME behavior must be checked with at least Gboard and one OEM keyboard: the logical framebuffer must stay fixed, the composer must remain above the keyboard, and Back must close IME then sheet without sending or walking.
- Rail thumb reach, icon recognition, panel readability, long-press menu paging, and accidental world-tap rate need human play sessions; emulator action logs cannot assess feel.
- Ordinary app switching is protected; Android process death cannot preserve an in-memory socket/session.
- Socket polling and keepalives are intentionally paused whenever the Activity is backgrounded, so continuity depends on the bounded server activity/reconnect grace and successful foreground resume; a near-ten-minute physical test is still required.
- Ten-minute grace is implemented, but this pass directly held the app away for 130 seconds rather than the full ten minutes.
- App-switch proof is emulator-based; OEM battery restrictions and physical Wi-Fi/cellular transitions remain manual.
- Real two-finger pinch requires a physical device because ADB cannot synthesize it reliably.
- Remote cache updating remains disabled. Do not enable it until manifest paths are contained and every downloaded file is hash-verified before publication.
- No clean, upload-signed current APK has passed promotion yet. The trusted release certificate SHA-256 must be supplied outside Git.
- The separate untracked Play-bundle checker is not covered by the direct-APK provenance gate; it should gain the same embedded-provenance checks and require a versionCode strictly greater than the live Play artifact.
- The public endpoint still relies on a fixed address/cleartext-compatible policy. Stable DNS and a narrower transport policy remain desirable when deployment permits.
- No complete physical no-network, flaky-network, notch, navigation-mode, low-memory, thermal, or battery matrix was run.
- Trade was not included in the focused emulator suites and remains on the manual checklist.

## Recommended next tasks

1. If raising the gate beyond the agreed usable candidate, run the deferred same-revision Magic/Prayer, equipment, settings-persistence, login/report/dialog, complete shop teardown, and exhaustive lifecycle/network-loss suites.
2. On a clean release checkout, configure upload signing and `VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256`, build the current release APK, run the promotion checker, and point `PORTAL_ANDROID_APK` at exactly that verified file.
3. Run the manual checklist on a low-end API-23-class device/emulator, a current midrange phone, and a large-screen device.
4. Exercise Wi-Fi/cellular changes, airplane mode, OEM background restrictions, 130-second and near-ten-minute app switches.
5. Harden or permanently remove the dormant remote cache updater.
6. Bring the Play-bundle checker to the same provenance and strict versionCode standard.
7. Profile cold start, cache setup, steady-state memory/CPU, frame pacing, thermal behavior, and battery before making numerical performance claims.
8. Move the public game endpoint to stable DNS and narrow cleartext policy where compatible.

## Manual QA checklist

Mobile HUD and touch shell:

- [ ] In portrait, all four left-rail and all three right-rail icons are visible, at least 48dp, clear of cutouts/system navigation, and reachable without hand gymnastics.
- [ ] In landscape, all seven rail icons remain visible, at least 48dp, and clear of both short-edge cutouts/gesture zones.
- [ ] Stats, Map, Social, and Settings each open immediately inside the left rail; Inventory, Magic, and Prayer each open immediately inside the right rail.
- [ ] Every open drawer is centered near its owning icon where safe space allows, visibly joined by the highlighted connector, and clamped between the location plaque and Chat instead of filling portrait height.
- [ ] Only one drawer is open at a time; tapping another destination switches cleanly, and tapping the active icon closes it.
- [ ] Opening/closing every drawer causes no walk, camera movement, target selection, or stale click after the drawer disappears.
- [ ] Inventory shows 5x6 cells in portrait and 6x5 in landscape; Magic/Prayer show four/three full rows and scroll; Stats skill scrolling, Social Add, Settings Account, and Map World Map remain reachable without stretching the drawer.
- [ ] Magic opens the spell list directly and Prayer opens the prayer list directly; alternating them never leaves the wrong internal tab active.
- [ ] Inventory slots, Magic/Prayer rows and tabs, Stats sub-tabs, Social rows, Map actions, Settings rows, scrollbars, and destructive actions are comfortable and do not rely on tiny text hitboxes.
- [ ] In portrait, the native shop shows its 8x5 item grid above a readable action block; every item, Close, Buy/Sell amount, and `X` control is comfortably tappable and no control overlaps system/UI chrome.
- [ ] In landscape, the shop moves the action block beside the grid without shrinking item, Close, `1`, `5`, `10`, `50`, or `X` targets below 48dp; a narrow phone uses 3x2 Buy/Sell groups while keeping all five item rows, and a wide phone uses one-row groups.
- [ ] The full World Map opens as a bounded card with 48dp Close, waypoint, Search, zoom, Reset, and Tiles controls; portrait is not a skyscraper, controls never overlap the map canvas, and quick taps, pan, search, Back, and rotation remain responsive.
- [ ] Void Glass bank fits both orientations: search/clear/close, tabs, cells and actions remain touch-sized; portrait stacks actions and exposes full-width Deposit All; landscape keeps one action row; bank/inventory drags and paginated amount menus do not select or transact accidentally.
- [ ] The collapsed Chat launcher is readable, shows the intended active channel/recent state, and its compose end-cap is distinguishable from opening history.
- [ ] The expanded sheet shows exactly All, Public, Quest, Global, and PM; every filter selects the correct shared history and remains readable in both orientations.
- [ ] Opening the IME leaves world/HUD framebuffer geometry unchanged; no scale jump, crop, stretch, or touch-offset drift occurs.
- [ ] With the IME visible, the chat composer and compose action sit completely above the keyboard on Gboard and an OEM keyboard in portrait and landscape.
- [ ] Sending from the composer sends exactly once, clears/retains draft as intended, and never clicks the world behind the sheet.
- [ ] Back with chat open dismisses the IME first, closes the sheet second, closes a drawer next, and only then reaches shared item/spell/modal behavior.
- [ ] Settings ends with Account; Account shows Report a player and Log out; no separate HUD logout action is present.
- [ ] Report a player keeps its name field above the IME, pages rules at readable 48dp rows, and sends only after a rule is selected and the separate Send report action is tapped.
- [ ] Tapping Log out opens a second confirmation. Outside tap, Cancel, and Back return safely without disconnecting; only final Log out sends the request.
- [ ] Long-press menus open in the center lane between rails, use comfortable 48dp rows, stay above Chat, and paginate without clipping on short portrait/landscape screens.
- [ ] Context-menu selection works at all four screen corners and on dense NPC/player/object stacks without choosing an adjacent row.
- [ ] Rotate with each drawer, chat sheet, IME, shop, Account sheet, and logout confirmation open; geometry reflows once, hit targets still match pixels, and no duplicate Activity/session appears.
- [ ] Test a display cutout plus gesture navigation and three-button navigation; all framebuffer content and tap targets remain inside the reported safe area.
- [ ] Switch apps while a drawer is open, while chat/IME is open, and during ordinary play; return to one live session with correct scale, focus, keyboard state, and input mapping, send a fresh message that reaches the server, and confirm only one `GameActivity` owns the retained client.
- [ ] Tap a rail icon or Chat immediately after returning during guarded reconnect; that launcher intent opens once after reconnect, while an immediate world/gameplay tap is never replayed later.

Launch, gameplay, stability, and release:

- [ ] Fresh signed-release install reaches “Ready to play,” uses the intended public endpoint, requests no unexpected permission, and enters login.
- [ ] Interrupted first launch shows Retry/Close and recovers without uninstalling.
- [ ] Valid login succeeds and enters the world.
- [ ] Login home, Existing User, and Create Account keep 48dp fields/actions above the IME in both orientations; the visible fields and actual hit targets remain aligned after keyboard show/hide.
- [ ] Bootstrap/update cards scroll at large font scale, keep 16dp narrow-screen margins, and remain centered and width-bounded on phones/tablets.
- [ ] Password change, recovery setup/recovery answer, welcome, and server-message dialogs remain bounded and expose full-size Back/Next/Cancel/Close actions on short portrait and landscape screens.
- [ ] Wrong password shows clear copy, saves nothing, and leaves fields/keyboard usable.
- [ ] Remember opt-in persists only after success; restart prefills; opt-out removes it; the next restart is empty.
- [ ] No internet: bundled bootstrap remains usable, login fails clearly, and retry works after restoring connectivity.
- [ ] Server offline: no crash or endless loading; error is clear and retry is immediate.
- [ ] Update available: mark N/A while remote updating is intentionally disabled.
- [ ] Update failure: mark N/A for remote updates; separately verify interrupted bundled-cache recovery.
- [ ] First world entry, appearance/welcome flow, HUD, and terrain render correctly.
- [ ] Movement taps work at short range, long range, and screen edges without accidental menus.
- [ ] NPC short tap and long-press/context actions select the intended NPC.
- [ ] Object short tap and item-on-object actions select the intended scenery.
- [ ] Combat target selection, retreat, prayer/spell selection, eating, and potting remain responsive.
- [ ] Chat keyboard opens, sends, dismisses with Back, and reopens after app switching.
- [ ] Inventory select/use/equip/unequip/drop and item-on-item/NPC/object flows work.
- [ ] Bank open/search/scroll/withdraw/deposit/deposit-all/loadout/close flows work.
- [ ] Shop open/select/buy `1`/`5`/`10`/`50`/`X`, sell `1`/`5`/`10`/`50`/`X`, disabled-state, outside-close, and X-close flows use the normal packet results and never double-submit.
- [ ] Trade request, offer, confirm, decline, and cancel work if enabled.
- [ ] Switch to Android Settings for 30 seconds and 130 seconds; return directly in game without stale reconnect UI, then prove a new chat message reaches server `chat_logs` rather than relying only on the grace-preserved online flag.
- [ ] Repeat app switching with a Wi-Fi/cellular transition and near the ten-minute grace boundary.
- [ ] Confirm process-kill behavior is understood: a killed process returns through normal login.
- [ ] Back closes context menu first, clears item/spell selection next, closes map/settings appropriately, and dismisses the login keyboard.
- [ ] Rotate wrapper, login, and game between portrait/landscape without duplicate Activities, clipping, or lost input.
- [ ] Test small phone, baseline phone, large phone/tablet, display cutout, gesture navigation, and three-button navigation.
- [ ] On physical hardware, verify one-finger camera control, two-finger pinch, panel-scroll isolation, and touch latency.
- [ ] Background audio and socket polling stop; a short switch resumes the retained stream, a return after at least ten seconds proactively completes retained-session reconnect without a dead-socket freeze, and explicit logout leaves no sound or network writer running.
- [ ] Run the final signed APK promotion checker and archive its signer/hash/version output.

## Earlier polish commit series

- `dc8fbefe` — Fix Android lifecycle smoke gate (VS-073)
- `aa513cc0` — Keep Android sessions through app switches (VS-061)
- `9d21c98c` — Polish Android touch and login recovery
- `56b0939c` — Bound Android network and audio lifecycles
- `230454e9` — Make Android bootstrap atomic and resumable
- `c0047f96` — Encrypt Android saved logins
- `80b3ffda` — Bind Android releases to signed provenance
- `6a120935` — Dismiss Android keyboard after login
