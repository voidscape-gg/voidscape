# Android Client Audit — Principal Architect Review

**Date:** 2026-07-04 · **Scope:** `Android_Client/` + shared `Client_Base` login/session code as it affects Android. TeaVM client and game content out of scope.
**Assumptions confirmed with owner:** distribution is sideloaded APK from voidscape.gg (Play items advisory with runway noted); quality bar is "classic RSC wrapped, best possible" within the current SurfaceView architecture; audio must ship at launch; background session grace is wanted.

---

## 1. Current-state assessment

### Rendering pipeline
`GameActivity` owns the shared `mudclient` and delegates drawing to `RSCBitmapSurfaceView`. The game thread renders the classic framebuffer (512 wide; height 346 in landscape, stretched up to 1152 in portrait) into `MudClientGraphics.pixelData` (int[]), then `draw()` copies it into an `RGB_565` Bitmap via `setPixels()` under a lock and calls `postInvalidate()`. Despite being a `SurfaceView`, the view uses `setWillNotDraw(false)` + `onDraw()` — i.e., it draws through the **View/UI-thread path, not the surface**, scaling the bitmap with `FILTER_BITMAP_FLAG` and letterboxing.

Consequences: every frame does an int[]→565 conversion plus a UI-thread bitmap blit; frame pacing is whatever `postInvalidate` coalesces to, not Choreographer-driven; input handled on the UI thread contends with drawing. On a Pixel 6a/A54 this holds ~50 fps for RSC's tiny framebuffer, but latency is ~2 frames worse than a `lockHardwareCanvas` loop and bilinear upscale of a 512-wide 565 bitmap to a 1080p+ panel is visibly soft. `surfaceDestroyed()` is empty; `resized()` is a no-op; resize requests flow through `mudclient.resizeWidth/Height` and are picked up by the game loop.

### Touch input
`InputImpl` synthesizes the desktop mouse model: `GestureDetector` tap → left click, long-press → right click (two modes via `C_HOLD_AND_CHOOSE`), swipe → camera pitch/rotate, zoom, or panel scroll chosen by heuristics over which UI is visible; world-map touches are special-cased; physical mouse (secondary button) and volume-key rotate/zoom are supported. Screen→client coordinate mapping duplicates the letterbox math from the view (two copies of scale/offset code that must stay in sync).

Weak points: no pinch-to-zoom (vertical-swipe zoom is a learned behavior); `checkSpecialKeys` allocates a **new `java.util.Timer` (thread) per keypress** for debounce; the deprecated `ACTION_MULTIPLE` key path still exists; taps route through `recordAndroidTap` plus mouse-button flags read asynchronously by the game thread — a tap can be dropped or double-applied under load since there is no event queue, just shared fields.

### Networking & reconnect
Plain blocking TCP to `5.161.114.251:43596` (hardcoded public IP; DNS migration is a known open item). Login packet 0 carries client version, username, and the password RSA-encrypted client-side (`encodeWithRSA`); the rest of the protocol is plaintext. On read failure `lostConnection()` sets `autoLoginTimeout = 10` and calls `login(..., reconnecting=true)`, which reuses the in-memory password and shows "Connection lost! Please wait…". There is **no backoff, no jitter, no ConnectivityManager-driven retry** — the `NetworkCallback` in `GameActivity` only feeds the HUD wifi/cell icon. If retries exhaust or the process dies, the session is simply gone; server-side there is a reconnect flag byte but no meaningful grace window for the mobile case.

### Lifecycle
Rotation is handled via manifest `configChanges`, so the Activity survives it. Other recreations are covered by a `static mudclient retainedClient` that the new Activity re-adopts if the client thread is alive — pragmatic, but a classic static-leak pattern: `mudclient.clientPort` is repointed to the new Activity, and any missed repoint (e.g., sprites, `Utils.context` set to the Activity in the view constructor) leaks the old one. `onDestroy` shuts the client thread down only when finishing and not changing configurations, with a 750 ms join. **Backgrounding is unhandled**: no `onStop` hook, the socket stays open until Android freezes/kills the process (cached-app freezer will stall the connection within minutes), and after process death the user restarts at the launcher with no session restore. Back navigation is correctly dual-pathed (`OnBackInvokedCallback` on 33+, `onBackPressed` below), but predictive back animation is not enabled. **Audio is a stub** — `playSound()` computes a buffer size and returns; Android ships silent today.

### Memory profile (Pixel 6a / A54 class)
Comfortable. No native libs; the frame bitmap is ≤512×1152×2 B ≈ 1.2 MB; the shared client's arrays and sprite cache are tens of MB; bundled cache seeds ~15 MB into `filesDir`. `largeHeap="true"` is legacy cargo and almost certainly unnecessary — it worsens GC pause behavior and should be removed after a profile pass. Real risks are churn, not footprint: per-frame `setPixels`, per-keypress Timer threads, and `getSpriteFromByteArray`'s pixel-by-pixel `getPixel()` loop (use `Bitmap.getPixels()`; only matters at load time). The 2 GB-RAM emulator matrix already passes.

### APK/AAB size levers
Debug APK ≈ 16 MB. Levers, largest first: (1) the bundled cache copies **all** of `Client_Base/Cache`, including `video/` (8 MB) — exclude it if the Android client never plays those assets, plus `audio/` (0.5 MB) until audio ships; (2) `minifyEnabled=false` — enabling R8 + resource shrinking on release is free size and obfuscation (needs a proguard pass over reflection-free shared code, low risk); (3) convert PNG drawables (2.2 MB res/) to WebP; (4) `dependenciesInfo` blocks are Play-only metadata and can be dropped for sideload. No native libs → no ABI-split savings. For sideload, APK (not AAB) remains the right artifact.

---

## 2. Compliance checklist

Channel is sideload-only, so Play items are **advisory today** but scored as if Play onboarding happens later (owner wants the runway visible).

| Item | Status | Notes | Fix size |
|---|---|---|---|
| Target SDK vs Play requirement | **FAIL (advisory)** | `targetSdk 35`, `compileSdk 36`. Play requires **API 36 by Aug 31, 2026** for new apps and updates (verified July 2026 — [Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [Android Developers](https://developer.android.com/google/play/requirements/target-sdk)). Sideload unaffected, but the bump is cheap now and expensive later; API 36 also hard-enforces predictive back and edge-to-edge. | **S** |
| 16 KB page size | **PASS (N/A)** | No `.so` files ship; pure-Java client. Becomes relevant only if a native renderer/audio lib is ever added — gate any future NDK dependency on 16 KB-aligned builds. | — |
| Edge-to-edge at API 35+ | **PASS w/ caveat** | Already targeting 35; the game hides system bars via `WindowInsetsController` (immersive), which remains legitimate for games. Caveats: no `windowLayoutInDisplayCutoutMode` declared (cutout behavior is device-default; the roadmap's notch/gesture-nav checks are still open), and at target 36 the remaining opt-outs disappear. Handle display-cutout + gesture-nav insets explicitly when bumping. | **S** |
| Foreground service types / Play policy | **FAIL (by design gap)** | No services declared. Owner wants background session grace. Recommendation: **do not add an FGS** — a connected-socket FGS maps poorly to allowed types (`connectedDevice`/`dataSync` don't fit; `specialUse` invites Play review friction) and Android 14+ requires type declaration at the OS level even for sideload. Instead: server-side grace window (keep the player entity 60–120 s on socket loss with the existing reconnect flag) + instant client auto-reconnect on `onStart`. Same UX, zero policy surface. | **M** (mostly server) |
| Data safety / credential storage | **FAIL** | "Save login" writes username+password **plaintext** to app-private `credentials.txt` (`ClientPort.saveCredentials`); login RSA protects transit only. App-private storage is sandboxed, but backups/rooted devices/`adb` expose it, and a future Play Data safety form would have to declare collected credentials with encryption-at-rest answered "no". Wrap with Android Keystore (`EncryptedFile` or Keystore-wrapped AES). `allowBackup="false"` is already correctly set. | **S** |

---

## 3. Top 10 ranked changes (classic-wrapped, best-possible bar)

Executor key: **Opus** = design-heavy/cross-cutting, needs judgment across client+server; **Codex** = well-scoped mechanical implementation against a clear spec.

1. **Session resilience: server grace + auto-reconnect.** Server holds the logged-in player 60–120 s after socket loss; client reconnects automatically on `onStart`/network-regain (ConnectivityManager callback → retry with capped backoff), reusing the existing `reconnecting=true` login path. *Why:* backgrounding an app for 30 seconds is the single most common mobile action; today it can cost the session — the #1 perceived-quality gap vs OSRS Mobile. **L** — **Opus** (protocol + server + lifecycle coordination).
2. **Ship Android audio.** Implement `playSound()` with `AudioTrack` (data is 8-bit µ-law-style PCM already decoded by shared code) or Oboe-free `AudioTrack` streaming; wire `stopSoundPlayer()`. *Why:* the client is silent; sound is table stakes at launch. **M** — **Codex** (self-contained, testable via smoke).
3. **Encrypt saved credentials with Android Keystore.** Keystore-wrapped AES around the existing `credentials.txt` payload, with transparent migration of the plaintext file. *Why:* launch-blocking trust/security issue; cheap. **S** — **Codex**.
4. **Replace hardcoded `5.161.114.251` with DNS.** Already flagged in the subsystem doc; every shipped APK hardcodes a VPS IP and strands players on any migration. *Why:* irreversible once APKs are in the wild. **S** — **Codex**.
5. **Move rendering onto the surface.** Draw via `SurfaceHolder.lockHardwareCanvas()` from a dedicated render pass (or the game thread) paced by frame availability instead of `onDraw`+`postInvalidate`; keep the letterbox math in one shared helper used by both renderer and `InputImpl`. *Why:* removes UI-thread contention, cuts ~1–2 frames of latency, fixes the duplicated coordinate mapping. **M** — **Opus** (threading/lifecycle edges: `surfaceDestroyed`, pause, resize).
6. **Input latency and robustness pass.** Queue input events into the game loop instead of racing shared fields; fire taps on `ACTION_UP` immediately; add pinch-to-zoom mapped to `C_LAST_ZOOM`; replace per-keypress `Timer` threads with `postDelayed`; delete the `ACTION_MULTIPLE` path. *Why:* tap-drop under load and swipe/zoom awkwardness are directly felt in combat. **M** — **Opus** (behavioral heuristics need judgment), mechanical subparts to **Codex**.
7. **Target SDK 36 + predictive back + cutout/gesture insets.** Bump target, enable `android:enableOnBackInvokedCallback`, declare `windowLayoutInDisplayCutoutMode="shortEdges"`, verify immersive + inset behavior on notched/gesture-nav devices (open roadmap items). *Why:* one dated runway item covering three OS-behavior cliffs before they're forced. **S/M** — **Codex**.
8. **Sideload update channel.** `ApplicationUpdater` is currently a splash. Add a version check against the portal's `/api/public` (hash already published), prompt-download-install flow with `REQUEST_INSTALL_PACKAGES` handling. *Why:* sideload has no store updates; without this, old APKs (with hardcoded IPs, old protocol versions) live forever — and `CLIENT_VERSION` bumps require it. **M** — **Codex** against an Opus-written spec.
9. **Crash/ANR telemetry (privacy-gated).** Minimal self-hosted crash upload (e.g., `UncaughtExceptionHandler` → portal endpoint) with player disclosure, per the deferred-hardening note. *Why:* sideload means no Play vitals; launch week is blind without it. **M** — **Codex**.
10. **Release-build diet + hygiene.** Exclude `video/` (and pre-audio `audio/`) from `syncVoidscapeCache`, enable R8 + resource shrink, WebP-convert drawables, drop `largeHeap` after a profiler pass, fix the `getPixel()` loop. *Why:* ~half the APK and faster cold start/cache install for a few hours' work. **S** — **Codex**.

**Sequencing note:** 3, 4, 7, 10 are pre-launch mandatories on any timeline; 1 and 2 define launch quality; 5 and 6 are the "feels good" tier; 8 and 9 are operational insurance that pay off the week after launch.
