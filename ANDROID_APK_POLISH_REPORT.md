# Voidscape Android APK Polish Report

Date: 2026-07-09

## Status

The Android client is now a substantially stronger automated/emulator candidate. It keeps the classic RSC framebuffer and interaction model while improving app-switch continuity, touch routing, login recovery, socket/audio ownership, first-launch cache setup, saved-login security, and direct-APK promotion safety.

The highest-priority result is verified: switching from the game to another Android app and returning no longer immediately disconnects the player. Android sessions now have a ten-minute activity/reconnect grace, and an emulator round-trip into Android Settings for 130 seconds returned directly to the live game. A separate saved-login smoke also switched apps for 35 seconds and reconnected successfully. Android process death still ends the in-memory client and must return through normal login.

Automated gates are green for the current debug candidate. Production promotion still requires a clean, upload-signed release APK, the trusted signer fingerprint, and physical-device QA. The working packaging changes report Android version `7` / `1.0.6`, `minSdk 23`, and `targetSdk 35`; those version/minimum-SDK edits predated this polish commit series and remain separate workspace changes.

## Improvements

### App switching and reconnect

- Android players receive a ten-minute server activity timeout and closed-channel reconnect grace; desktop and web timeout behavior is unchanged.
- Backgrounded Android clients stop normal polling and can reclaim the existing player session after returning.
- Successful reconnects replace and close stale sockets without showing a lingering connection-lost overlay.
- Duplicate launcher delivery no longer creates a second game session.
- Explicit logout still closes the session immediately and returns to a usable login form.

Evidence: `tmp/android-apk-polish/lifecycle-130s-settings`, `tmp/android-apk-polish/network-audio-lifecycle-final-2`, and `tmp/android-apk-polish/credentials-encrypted-final-5`.

### Login, keyboard, Back, and touch UX

- Login fields reliably reopen the soft keyboard after validation or authentication errors.
- Canvas Cancel paths dismiss the keyboard, and connection failures leave the form ready for immediate retry.
- Android Back closes a context menu before clearing an active item or spell selection; last-spell memory is preserved.
- Hardware/synthetic special keys dispatch once per press, and key-release pulses use the Android view queue instead of allocating timer threads.
- A swipe captured by an open panel or scrollable message area no longer rotates or pitches the world behind it.
- One-finger gestures cannot accidentally trigger pinch zoom; real two-finger pinch remains a physical-device check.
- The existing RSC look, menu model, framebuffer, and gameplay calculations are unchanged.

Evidence: `tmp/android-apk-polish/login-touch-final`, `back-selection-final`, `world-map-input`, `camera-routing-final-3`, and `zoom-one-finger-final-2`.

### Network, lifecycle, and audio stability

- Publishing a replacement connection closes the superseded socket.
- Logout drains its confirmation packet before closing the connection.
- Writer threads are named, race-safe, and terminate on replacement, logout, interruption, setup failure, or write failure.
- Android connectivity is based on validated networks rather than merely present network handles.
- Sound preparation is asynchronous, uses game-audio attributes, and is limited to eight active players.
- Audio players are released on completion, error, backgrounding, logout, and Activity destruction.
- Activity references used for external links are weakly held.

Evidence: `tmp/android-apk-polish/network-audio-magic-review-final` and `network-audio-lifecycle-final-2`.

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

No quantified startup, frame-time, CPU, memory, thermal, or battery benchmark was run, so this report makes no numerical performance claim.

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

## Verification completed

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

- Ordinary app switching is protected; Android process death cannot preserve an in-memory socket/session.
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

1. On a clean release checkout, configure upload signing and `VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256`, build the current release APK, run the promotion checker, and point `PORTAL_ANDROID_APK` at exactly that verified file.
2. Run the manual checklist on a low-end API-23-class device/emulator, a current midrange phone, and a large-screen device.
3. Exercise Wi-Fi/cellular changes, airplane mode, OEM background restrictions, 130-second and near-ten-minute app switches.
4. Harden or permanently remove the dormant remote cache updater.
5. Bring the Play-bundle checker to the same provenance and strict versionCode standard.
6. Profile cold start, cache setup, steady-state memory/CPU, frame pacing, thermal behavior, and battery before making numerical performance claims.
7. Move the public game endpoint to stable DNS and narrow cleartext policy where compatible.

## Manual QA checklist

- [ ] Fresh signed-release install reaches “Ready to play,” uses the intended public endpoint, requests no unexpected permission, and enters login.
- [ ] Interrupted first launch shows Retry/Close and recovers without uninstalling.
- [ ] Valid login succeeds and enters the world.
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
- [ ] Shop open/select/buy/sell/close flows work.
- [ ] Trade request, offer, confirm, decline, and cancel work if enabled.
- [ ] Switch to Android Settings for 30 seconds and 130 seconds; return directly in game without stale reconnect UI.
- [ ] Repeat app switching with a Wi-Fi/cellular transition and near the ten-minute grace boundary.
- [ ] Confirm process-kill behavior is understood: a killed process returns through normal login.
- [ ] Back closes context menu first, clears item/spell selection next, closes map/settings appropriately, and dismisses the login keyboard.
- [ ] Rotate wrapper, login, and game between portrait/landscape without duplicate Activities, clipping, or lost input.
- [ ] Test small phone, baseline phone, large phone/tablet, display cutout, gesture navigation, and three-button navigation.
- [ ] On physical hardware, verify one-finger camera control, two-finger pinch, panel-scroll isolation, and touch latency.
- [ ] Background audio stops; foreground audio resumes; logout leaves no sound or network writer running.
- [ ] Run the final signed APK promotion checker and archive its signer/hash/version output.

## Commit series

- `dc8fbefe` — Fix Android lifecycle smoke gate (VS-073)
- `aa513cc0` — Keep Android sessions through app switches (VS-061)
- `9d21c98c` — Polish Android touch and login recovery
- `56b0939c` — Bound Android network and audio lifecycles
- `230454e9` — Make Android bootstrap atomic and resumable
- `c0047f96` — Encrypt Android saved logins
- `80b3ffda` — Bind Android releases to signed provenance
- `6a120935` — Dismiss Android keyboard after login
