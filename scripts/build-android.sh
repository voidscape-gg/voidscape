#!/usr/bin/env bash
# build-android.sh — build the Voidscape Android APK or Play bundle

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/Android_Client"
PROVENANCE_TOOL="$SCRIPT_DIR/android-provenance.py"
PROVENANCE_ASSET="$ANDROID_DIR/.gradle/voidscape-provenance-assets/voidscape-provenance.json"
GRADLE_ARGS=()

usage() {
    cat <<'EOF'
Usage: scripts/build-android.sh [--debug|--release|--play-release] [gradle args...]

Options:
  --debug         Build the debug APK. Default when no task is supplied.
  --release       Build the release APK. Requires upload signing config unless
                  VOIDSCAPE_ANDROID_ALLOW_UNSIGNED_RELEASE=1 is set for local experiments.
  --play-release  Build the signed release Android App Bundle for Google Play.
                  Writes a stable voidscape.aab plus voidscape.aab.json metadata.
  -h, --help      Show this help.

Release signing can be supplied through environment:
  VOIDSCAPE_ANDROID_UPLOAD_KEYSTORE=/path/to/voidscape-upload.jks
  VOIDSCAPE_ANDROID_UPLOAD_STORE_PASSWORD=...
  VOIDSCAPE_ANDROID_UPLOAD_KEY_PASSWORD=...

Release builds also require clean Android/shared-client inputs. For a local or
staging artifact that promotion checks must reject, set:
  VOIDSCAPE_ANDROID_ALLOW_DIRTY_RELEASE=1

or matching Gradle properties:
  voidscape.android.uploadKeystore.file
  voidscape.android.uploadKeystore.storePassword
  voidscape.android.uploadKeystore.keyPassword

On macOS, this script also checks Keychain services:
  voidscape.android.uploadKeystore.storePassword
  voidscape.android.uploadKeystore.keyPassword
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --debug)
            GRADLE_ARGS=(assembleDebug)
            shift
            ;;
        --release)
            GRADLE_ARGS=(assembleRelease)
            shift
            ;;
        --play-release)
            GRADLE_ARGS=(bundleRelease)
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        --)
            shift
            while [[ $# -gt 0 ]]; do
                GRADLE_ARGS+=("$1")
                shift
            done
            ;;
        *)
            GRADLE_ARGS+=("$1")
            shift
            ;;
    esac
done

# Resolve the documented default before any helper expands the task array.
# macOS still ships Bash 3.2, where expanding an empty array under `set -u`
# raises an unbound-variable error even when the array was declared.
if [[ "${#GRADLE_ARGS[@]}" -eq 0 ]]; then
    GRADLE_ARGS=(assembleDebug)
fi

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

release_signing_requested() {
    local arg
    for arg in "${GRADLE_ARGS[@]}"; do
        case "$arg" in
            assembleRelease|bundleRelease|*Release*)
                return 0
                ;;
        esac
    done
    return 1
}

read_relevant_state() {
    python3 "$PROVENANCE_TOOL" state --repo-root "$REPO_ROOT" --format tsv
}

read_macos_keychain_password() {
    local service_name="$1"
    [[ "$(uname -s)" == "Darwin" ]] || return 1
    command -v security >/dev/null 2>&1 || return 1
    security find-generic-password -s "$service_name" -w 2>/dev/null
}

configure_macos_keychain_signing() {
    local default_keystore store_password key_password loaded_from_keychain=0
    default_keystore="$HOME/.voidscape/android-signing/voidscape-upload.jks"

    if [[ -z "${VOIDSCAPE_ANDROID_UPLOAD_KEYSTORE:-}" && -f "$default_keystore" ]]; then
        export VOIDSCAPE_ANDROID_UPLOAD_KEYSTORE="$default_keystore"
    fi

    if [[ -z "${VOIDSCAPE_ANDROID_UPLOAD_STORE_PASSWORD:-}" ]]; then
        if store_password="$(read_macos_keychain_password "voidscape.android.uploadKeystore.storePassword")" && [[ -n "$store_password" ]]; then
            export VOIDSCAPE_ANDROID_UPLOAD_STORE_PASSWORD="$store_password"
            loaded_from_keychain=1
        fi
    fi

    if [[ -z "${VOIDSCAPE_ANDROID_UPLOAD_KEY_PASSWORD:-}" ]]; then
        if key_password="$(read_macos_keychain_password "voidscape.android.uploadKeystore.keyPassword")" && [[ -n "$key_password" ]]; then
            export VOIDSCAPE_ANDROID_UPLOAD_KEY_PASSWORD="$key_password"
            loaded_from_keychain=1
        fi
    fi

    if [[ "$loaded_from_keychain" == "1" ]]; then
        echo "Loaded Android upload signing config from macOS Keychain."
    fi
}

if [[ ! -f "$PROVENANCE_TOOL" ]]; then
    echo "ERROR: Android provenance helper not found: $PROVENANCE_TOOL" >&2
    exit 1
fi
read -r SOURCE_COMMIT_BEFORE SOURCE_DIGEST_BEFORE SOURCE_DIRTY_BEFORE SOURCE_COMMIT_DIGEST_BEFORE < <(read_relevant_state)
DIRTY_RELEASE_OVERRIDE=false

if release_signing_requested && [[ "$SOURCE_DIRTY_BEFORE" == "true" ]]; then
    if [[ "${VOIDSCAPE_ANDROID_ALLOW_DIRTY_RELEASE:-}" == "1" ]]; then
        DIRTY_RELEASE_OVERRIDE=true
        echo "WARNING: building a dirty release artifact for local/staging use only; do not promote it." >&2
    else
        echo "ERROR: Android release inputs are dirty. Commit/stash the relevant Android and shared-client changes first." >&2
        echo "For a non-promotable local/staging experiment only, set VOIDSCAPE_ANDROID_ALLOW_DIRTY_RELEASE=1." >&2
        exit 1
    fi
fi

cleanup_provenance_asset() {
    rm -f "$PROVENANCE_ASSET"
}
trap cleanup_provenance_asset EXIT
python3 "$PROVENANCE_TOOL" write \
    --repo-root "$REPO_ROOT" \
    --output "$PROVENANCE_ASSET" \
    --dirty-release-override "$DIRTY_RELEASE_OVERRIDE" >/dev/null

cd "$ANDROID_DIR"
if release_signing_requested && [[ "${VOIDSCAPE_ANDROID_ALLOW_UNSIGNED_RELEASE:-}" != "1" ]]; then
    configure_macos_keychain_signing
fi
sh ./gradlew "${GRADLE_ARGS[@]}"

read -r SOURCE_COMMIT_AFTER SOURCE_DIGEST_AFTER SOURCE_DIRTY_AFTER SOURCE_COMMIT_DIGEST_AFTER < <(read_relevant_state)
if [[ "$SOURCE_COMMIT_AFTER" != "$SOURCE_COMMIT_BEFORE" \
    || "$SOURCE_DIGEST_AFTER" != "$SOURCE_DIGEST_BEFORE" \
    || "$SOURCE_DIRTY_AFTER" != "$SOURCE_DIRTY_BEFORE" \
    || "$SOURCE_COMMIT_DIGEST_AFTER" != "$SOURCE_COMMIT_DIGEST_BEFORE" ]]; then
    echo "ERROR: Android artifact inputs changed while Gradle was running; refusing to write provenance metadata." >&2
    exit 1
fi

client_version() {
    awk '/CLIENT_VERSION[[:space:]]*=/ {
        for (i = 1; i <= NF; i++) {
            if ($i ~ /^[0-9]+;?$/) {
                gsub(/;/, "", $i);
                print $i;
                exit;
            }
        }
    }' "$REPO_ROOT/Client_Base/src/orsc/Config.java"
}

gradle_value() {
    local key="$1"
    awk -v key="$key" '
        $1 == key {
            value = $2
            gsub(/"/, "", value)
            print value
            exit
        }
    ' "$ANDROID_DIR/Open RSC Android Client/build.gradle"
}

write_artifact_metadata() {
    local artifact_path="$1"
    local build_type="$2"
    local artifact_type="$3"
    local version sha size commit built_at application_id version_code version_name min_sdk target_sdk
    [[ -f "$artifact_path" ]] || return 0
    version="$(client_version)"
    if [[ ! "$version" =~ ^[0-9]+$ ]]; then
        echo "ERROR: could not parse CLIENT_VERSION from Client_Base/src/orsc/Config.java." >&2
        exit 1
    fi
    application_id="$(gradle_value applicationId)"
    version_code="$(gradle_value versionCode)"
    version_name="$(gradle_value versionName)"
    min_sdk="$(gradle_value minSdk)"
    target_sdk="$(gradle_value targetSdk)"
    if [[ ! "$version_code" =~ ^[0-9]+$ ]]; then
        echo "ERROR: could not parse Android versionCode from build.gradle." >&2
        exit 1
    fi
    if [[ ! "$min_sdk" =~ ^[0-9]+$ || ! "$target_sdk" =~ ^[0-9]+$ ]]; then
        echo "ERROR: could not parse Android minSdk/targetSdk from build.gradle." >&2
        exit 1
    fi
    sha="$(shasum -a 256 "$artifact_path" | awk '{print $1}')"
    size="$(wc -c < "$artifact_path" | tr -d ' ')"
    commit="$SOURCE_COMMIT_BEFORE"
    built_at="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    python3 - "$artifact_path.json" "$version" "$sha" "$size" "$built_at" "$commit" "$build_type" "$artifact_type" "$application_id" "$version_code" "$version_name" "$min_sdk" "$target_sdk" "$PROVENANCE_ASSET" <<'PY'
import json
import sys
from pathlib import Path

meta_path, version, sha, size, built_at, commit, build_type, artifact_type, application_id, version_code, version_name, min_sdk, target_sdk, provenance_path = sys.argv[1:]
provenance = json.loads(Path(provenance_path).read_text(encoding="utf-8"))
metadata = {
    "clientVersion": int(version),
    "sha256": sha,
    "sizeBytes": int(size),
    "builtAt": built_at,
    "gitCommit": commit,
    "buildType": build_type,
    "artifactType": artifact_type,
    "applicationId": application_id,
    "versionCode": int(version_code),
    "versionName": version_name,
    "minSdk": int(min_sdk),
    "targetSdk": int(target_sdk),
}
metadata.update(provenance)
Path(meta_path).write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
PY
    echo "Wrote Android artifact metadata: $artifact_path.json"
}

copy_play_bundle() {
    local bundle_dir="$ANDROID_DIR/Open RSC Android Client/build/outputs/bundle/release"
    local source_bundle stable_bundle
    source_bundle="$(find "$bundle_dir" -maxdepth 1 -type f -name '*.aab' ! -name 'voidscape.aab' | sort | tail -1)"
    if [[ -z "$source_bundle" || ! -f "$source_bundle" ]]; then
        echo "ERROR: release app bundle not found in $bundle_dir" >&2
        exit 1
    fi
    stable_bundle="$bundle_dir/voidscape.aab"
    cp "$source_bundle" "$stable_bundle"
    write_artifact_metadata "$stable_bundle" release android-app-bundle
}

for arg in "${GRADLE_ARGS[@]}"; do
    case "$arg" in
        *bundleRelease*)
            copy_play_bundle
            ;;
        *Release*)
            write_artifact_metadata "$ANDROID_DIR/Open RSC Android Client/build/outputs/apk/release/voidscape.apk" release apk
            ;;
        *Debug*)
            write_artifact_metadata "$ANDROID_DIR/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk" debug apk
            ;;
    esac
done
