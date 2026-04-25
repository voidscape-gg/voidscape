# Android_Client/ — Android client wrapper

Android shell around `Client_Base/`. Voidscape doesn't actively maintain Android; treat as best-effort.

- Build: Gradle (`Open RSC Android Client/build.gradle`).
- Entry: `src/main/java/com/openrsc/client/android/GameActivity.java` (implements `ClientPort`).
- SDK: minSDK 14, target/compile SDK 31.
- Output: `openrsc.apk`.

Shared logic lives in `Client_Base/`. Bumping `Client_Base/Config.CLIENT_VERSION` requires APK rebuild — Android has no separate version pin.

For runtime architecture, see `docs/subsystems/client-cache.md`.
