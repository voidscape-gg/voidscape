# Android_Client/ — Voidscape Android client wrapper

Android shell around `Client_Base/`. The wrapper is now Voidscape-branded and suitable for dev APK iteration, but still shares the PC client's core game loop and UI constraints.

- Build: Gradle (`Open RSC Android Client/build.gradle`).
- Entry: `src/main/java/com/openrsc/client/android/GameActivity.java` (implements `ClientPort`).
- SDK: minSDK 23, target/compile SDK 31.
- Output: `voidscape.apk`.
- Script: `../scripts/build-android.sh` selects JDK 17 and runs Gradle.

Shared logic lives in `Client_Base/`. Bumping `Client_Base/Config.CLIENT_VERSION` requires APK rebuild — Android has no separate version pin.

The APK packages a generated asset copy of `Client_Base/Cache` at build time, excluding local mutable files (`config.txt`, `credentials.txt`, `hideIp.txt`, `ip.txt`, `port.txt`, `uid.dat`). `CacheUpdater` seeds that bundled cache into app-private storage first and only uses `orsc.osConfig.CACHE_URL` if a future Voidscape remote cache endpoint is configured.

Default server choices are dev-focused:
- Android emulator: `10.0.2.2:43596`
- LAN placeholder: `192.168.1.100:43596`
- Manual host/port entry

For runtime architecture, see `docs/subsystems/client-cache.md`.
