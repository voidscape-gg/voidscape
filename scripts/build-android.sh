#!/usr/bin/env bash
# build-android.sh — build the Voidscape Android APK

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/Android_Client"

if [[ -z "${JAVA_HOME:-}" ]]; then
    if [[ -d /opt/homebrew/Cellar/openjdk@17 ]]; then
        JAVA17_HOME="$(find /opt/homebrew/Cellar/openjdk@17 -path '*/libexec/openjdk.jdk/Contents/Home' -type d | sort | tail -1)"
        export JAVA_HOME="$JAVA17_HOME"
    elif /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    fi
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    echo "ERROR: Android build requires JDK 17. Set JAVA_HOME to a JDK 17 install." >&2
    exit 1
fi

JAVA_MAJOR="$("$JAVA_HOME/bin/java" -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"
if [[ "$JAVA_MAJOR" != "17" ]]; then
    echo "ERROR: Android build requires JDK 17, but JAVA_HOME points to Java $JAVA_MAJOR: $JAVA_HOME" >&2
    exit 1
fi

if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f "$ANDROID_DIR/local.properties" ]]; then
    DEFAULT_SDK="$HOME/Library/Android/sdk"
    HOMEBREW_SDK="/opt/homebrew/share/android-commandlinetools"
    if [[ -d "$DEFAULT_SDK" ]]; then
        export ANDROID_HOME="$DEFAULT_SDK"
    elif [[ -d "$HOMEBREW_SDK/platforms" ]]; then
        export ANDROID_HOME="$HOMEBREW_SDK"
    else
        echo "ERROR: Android SDK not found. Set ANDROID_HOME or create Android_Client/local.properties with sdk.dir=/path/to/sdk." >&2
        exit 1
    fi
fi

cd "$ANDROID_DIR"
if [[ "$#" -eq 0 ]]; then
    set -- assembleDebug
fi
sh ./gradlew "$@"
