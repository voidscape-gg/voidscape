# Android Client

Voidscape's Android client should stay a native Android shell around the shared `Client_Base` game core. That keeps combat, packets, menus, cache loading, and custom client behavior identical to PC while letting Android specialize only the phone-specific pieces: first launch, cache install/update, touch input, fullscreen rendering, keyboard, battery/network overlays, and distribution.

## Current Architecture

- `Android_Client/Open RSC Android Client/src/main/java/com/openrsc/client/android/GameActivity.java` implements `orsc.multiclient.ClientPort` and owns the shared `mudclient` instance.
- `com.openrsc.android.render.RSCBitmapSurfaceView` renders the classic `512x346` framebuffer into a fullscreen `SurfaceView` with aspect-ratio letterboxing.
- `com.openrsc.android.render.InputImpl` maps touch, swipes, long-press, soft keyboard, and volume keys into the old mouse/key model.
- `com.openrsc.android.updater.CacheUpdater` copies bundled `Client_Base/Cache` assets into app-private storage and optionally checks `orsc.osConfig.CACHE_URL`.
- `scripts/build-android.sh` is the canonical APK build entry point. It selects JDK 17 and runs the Android Gradle build.

## Player Launch

The APK should prioritize one-tap entry for beta players:

- After bundled cache install, the visible `Play` button writes `5.161.114.251:43596` to Android app-private `ip.txt` / `port.txt` and starts `GameActivity`.
- Long-press `Play` opens the advanced server picker for developers and testers.
- Advanced choices are public Voidscape, Android emulator `10.0.2.2:43596`, LAN placeholder `192.168.1.100:43596`, and manual host/port.
- Replace the hardcoded public IP with the final DNS name before broad release so old APKs survive VPS moves.

## Shared Client Constraints

Everything in `Client_Base/src` must compile on Android. Avoid direct imports of desktop-only APIs such as AWT, Swing, `java.awt.image.BufferedImage`, or `javax.imageio.ImageIO` in shared code. Use `orsc.multiclient.ClientPort` for platform-specific image decoding or rendering behavior.

Country flag chat badges are the current pattern:

- `GraphicsController` asks `mudclient.clientPort.getSpriteFromByteArray(...)` to decode PNG flag assets, so PC and Android each use their native decoder.
- If a PNG asset is unavailable, shared code draws a tiny two-letter badge using raw pixels.
- This keeps Android compiling while preserving actual PNG flags where assets exist.

## Quality Bar

Before sharing an APK with players:

- `scripts/build-android.sh` must pass and emit `Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk`.
- Fresh install on an emulator reaches `Ready to play`, pressing `Play` writes the public endpoint, and the login screen renders.
- Emulator test matrix: one low-end-ish profile around 2 GB RAM, one modern phone profile, portrait launch prevention/fullscreen behavior, and at least one cold install with app data cleared.
- Input smoke: tap walk, tap NPC/object, long-press/right-click menu, chat keyboard, login text entry, inventory tap, bank scroll, camera rotate, zoom gesture, and logout.
- Performance smoke: idle in Lumbridge/Edgeville for several minutes, enter a populated/combat area, confirm no sustained skipped frames, ANRs, or runaway battery/CPU warnings in Android Studio Profiler.
- Network smoke: launch on Wi-Fi, background/resume the app, reconnect after a brief network drop, and verify bad host/port failure is understandable.

## Deferred Hardening

- Replace deprecated wrapper APIs (`AsyncTask`, old fullscreen flags, `getDrawable(int)`, bare `Handler()`) after the build and first-launch flow are stable.
- Add a release-signed AAB/APK path once distribution is chosen.
- Add a remote Android cache endpoint only when it can be served over HTTPS with versioned cache checksums.
- Add crash/ANR telemetry before public beta if privacy policy and player disclosure are ready.
