#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAB_PATH=""
SERVER_CONFIG=""
SERVER_CLIENT_VERSION=""
SOURCE_CONFIG="$ROOT/Client_Base/src/orsc/Config.java"
META_PATH=""
MIN_VERSION_CODE=""

usage() {
	cat <<'USAGE'
Usage: scripts/check-android-play-release.sh --aab PATH (--server-config PATH | --server-client-version N) [--source-config PATH] [--meta PATH] [--min-version-code N]

Verifies a Google Play Android App Bundle is safe to upload by checking:
  - AAB sidecar metadata exists and points at the bundle being uploaded.
  - sidecar clientVersion matches Client_Base/src/orsc/Config.java.
  - sidecar clientVersion matches the target server client_version.
  - sidecar sha256 and sizeBytes match the AAB bytes.
  - sidecar artifactType is android-app-bundle and versionCode is valid.
  - sidecar applicationId/buildType/targetSdk are Play-ready.
  - jarsigner accepts the AAB signature when jarsigner is available.

The default sidecar path is "${AAB_PATH}.json", for example voidscape.aab.json.
USAGE
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--aab)
			AAB_PATH="${2:-}"
			shift 2
			;;
		--server-config)
			SERVER_CONFIG="${2:-}"
			shift 2
			;;
		--server-client-version)
			SERVER_CLIENT_VERSION="${2:-}"
			shift 2
			;;
		--source-config)
			SOURCE_CONFIG="${2:-}"
			shift 2
			;;
		--meta)
			META_PATH="${2:-}"
			shift 2
			;;
		--min-version-code)
			MIN_VERSION_CODE="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "ERROR: unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$AAB_PATH" ]]; then
	echo "ERROR: --aab is required." >&2
	usage >&2
	exit 2
fi
if [[ -z "$SERVER_CONFIG" && -z "$SERVER_CLIENT_VERSION" ]]; then
	echo "ERROR: pass --server-config or --server-client-version." >&2
	usage >&2
	exit 2
fi
if [[ -n "$SERVER_CONFIG" && -n "$SERVER_CLIENT_VERSION" ]]; then
	echo "ERROR: pass only one of --server-config or --server-client-version." >&2
	exit 2
fi
if [[ -n "$MIN_VERSION_CODE" && ! "$MIN_VERSION_CODE" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --min-version-code must be numeric." >&2
	exit 2
fi

if [[ ! -f "$AAB_PATH" ]]; then
	echo "ERROR: AAB not found: $AAB_PATH" >&2
	exit 1
fi
if [[ ! -f "$SOURCE_CONFIG" ]]; then
	echo "ERROR: source config not found: $SOURCE_CONFIG" >&2
	exit 1
fi
if [[ -z "$META_PATH" ]]; then
	META_PATH="${AAB_PATH}.json"
fi
if [[ ! -f "$META_PATH" ]]; then
	echo "ERROR: AAB metadata sidecar not found: $META_PATH" >&2
	echo "Expected JSON fields: clientVersion, sha256, sizeBytes, builtAt, gitCommit, buildType, artifactType, applicationId, versionCode, versionName, minSdk, targetSdk." >&2
	exit 1
fi

source_client_version="$(awk '/CLIENT_VERSION[[:space:]]*=/ {
	for (i = 1; i <= NF; i++) {
		if ($i ~ /^[0-9]+;?$/) {
			gsub(/;/, "", $i);
			print $i;
			exit;
		}
	}
}' "$SOURCE_CONFIG")"
if [[ ! "$source_client_version" =~ ^[0-9]+$ ]]; then
	echo "ERROR: could not parse CLIENT_VERSION from $SOURCE_CONFIG" >&2
	exit 1
fi

if [[ -n "$SERVER_CONFIG" ]]; then
	if [[ ! -f "$SERVER_CONFIG" ]]; then
		echo "ERROR: server config not found: $SERVER_CONFIG" >&2
		exit 1
	fi
	SERVER_CLIENT_VERSION="$(awk -F: '/^[[:space:]]*client_version[[:space:]]*:/ {
		gsub(/[[:space:]]/, "", $2);
		print $2;
		exit;
	}' "$SERVER_CONFIG")"
fi
if [[ ! "$SERVER_CLIENT_VERSION" =~ ^[0-9]+$ ]]; then
	echo "ERROR: target server client_version is missing or not numeric." >&2
	exit 1
fi

actual_sha="$(shasum -a 256 "$AAB_PATH" | awk '{print $1}')"
actual_size="$(wc -c < "$AAB_PATH" | tr -d ' ')"

python3 - "$META_PATH" "$source_client_version" "$SERVER_CLIENT_VERSION" "$actual_sha" "$actual_size" "$MIN_VERSION_CODE" <<'PY'
import json
import re
import sys
from pathlib import Path

meta_path, source_version, server_version, actual_sha, actual_size, min_version_code = sys.argv[1:]
try:
    meta = json.loads(Path(meta_path).read_text(encoding="utf-8"))
except Exception as exc:
    raise SystemExit(f"ERROR: could not parse AAB metadata {meta_path}: {exc}")

errors = []
client_version = str(meta.get("clientVersion", ""))
if client_version != source_version:
    errors.append(f"metadata clientVersion {client_version!r} != source CLIENT_VERSION {source_version}")
if client_version != server_version:
    errors.append(f"metadata clientVersion {client_version!r} != server client_version {server_version}")

if meta.get("artifactType") != "android-app-bundle":
    errors.append(f"metadata artifactType {meta.get('artifactType')!r} != 'android-app-bundle'")

sha = str(meta.get("sha256", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    errors.append(f"metadata sha256 is invalid: {sha!r}")
elif sha != actual_sha:
    errors.append(f"metadata sha256 {sha} != AAB sha256 {actual_sha}")

size = str(meta.get("sizeBytes", ""))
if size != actual_size:
    errors.append(f"metadata sizeBytes {size!r} != AAB size {actual_size}")

version_code = meta.get("versionCode")
if not isinstance(version_code, int) or version_code <= 0:
    errors.append(f"metadata versionCode must be a positive integer, got {version_code!r}")
elif min_version_code and version_code < int(min_version_code):
    errors.append(f"metadata versionCode {version_code} < required minimum {min_version_code}")

for field in ("builtAt", "gitCommit", "buildType", "applicationId", "versionName"):
    if not str(meta.get(field, "")).strip():
        errors.append(f"metadata field {field} is required")

if meta.get("applicationId") != "com.voidscape.gg":
    errors.append(f"metadata applicationId {meta.get('applicationId')!r} != 'com.voidscape.gg'")
if meta.get("buildType") != "release":
    errors.append(f"metadata buildType {meta.get('buildType')!r} != 'release'")
target_sdk = meta.get("targetSdk")
if not isinstance(target_sdk, int) or target_sdk < 35:
    errors.append(f"metadata targetSdk must be >= 35 for current Play updates, got {target_sdk!r}")

if errors:
    raise SystemExit("ERROR: Android Play release preflight failed:\n- " + "\n- ".join(errors))

print(f"Android Play release preflight passed: clientVersion={client_version} versionCode={version_code} sha256={actual_sha}")
PY

if command -v jarsigner >/dev/null 2>&1; then
	# Android upload keys are self-signed; Play validates them against the app's
	# registered upload certificate, while jarsigner -strict treats that as local
	# PKIX failure. Plain verification still checks the bundle signature integrity.
	jarsigner -verify -certs "$AAB_PATH" >/dev/null
	echo "AAB signature verification passed."
else
	echo "WARNING: jarsigner not found; skipped AAB signature verification." >&2
fi
