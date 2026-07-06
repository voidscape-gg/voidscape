#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH=""
SERVER_CONFIG=""
SERVER_CLIENT_VERSION=""
SOURCE_CONFIG="$ROOT/Client_Base/src/orsc/Config.java"
META_PATH=""

usage() {
	cat <<'USAGE'
Usage: scripts/check-android-apk-release.sh --apk PATH (--server-config PATH | --server-client-version N) [--source-config PATH] [--meta PATH]

Verifies an Android APK is safe to promote by checking:
  - APK sidecar metadata exists and points at the APK being uploaded.
  - sidecar clientVersion matches Client_Base/src/orsc/Config.java.
  - sidecar clientVersion matches the target server client_version.
  - sidecar sha256 and sizeBytes match the APK bytes.

The default sidecar path is "${APK_PATH}.json", for example voidscape.apk.json.
USAGE
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--apk)
			APK_PATH="${2:-}"
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

if [[ -z "$APK_PATH" ]]; then
	echo "ERROR: --apk is required." >&2
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

if [[ ! -f "$APK_PATH" ]]; then
	echo "ERROR: APK not found: $APK_PATH" >&2
	exit 1
fi
if [[ ! -f "$SOURCE_CONFIG" ]]; then
	echo "ERROR: source config not found: $SOURCE_CONFIG" >&2
	exit 1
fi
if [[ -z "$META_PATH" ]]; then
	META_PATH="${APK_PATH}.json"
fi
if [[ ! -f "$META_PATH" ]]; then
	echo "ERROR: APK metadata sidecar not found: $META_PATH" >&2
	echo "Expected JSON fields: clientVersion, sha256, sizeBytes, builtAt, gitCommit, buildType." >&2
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

actual_sha="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
actual_size="$(wc -c < "$APK_PATH" | tr -d ' ')"

python3 - "$META_PATH" "$source_client_version" "$SERVER_CLIENT_VERSION" "$actual_sha" "$actual_size" <<'PY'
import json
import re
import sys
from pathlib import Path

meta_path, source_version, server_version, actual_sha, actual_size = sys.argv[1:]
try:
    meta = json.loads(Path(meta_path).read_text(encoding="utf-8"))
except Exception as exc:
    raise SystemExit(f"ERROR: could not parse APK metadata {meta_path}: {exc}")

errors = []
client_version = str(meta.get("clientVersion", ""))
if client_version != source_version:
    errors.append(f"metadata clientVersion {client_version!r} != source CLIENT_VERSION {source_version}")
if client_version != server_version:
    errors.append(f"metadata clientVersion {client_version!r} != server client_version {server_version}")

sha = str(meta.get("sha256", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    errors.append(f"metadata sha256 is invalid: {sha!r}")
elif sha != actual_sha:
    errors.append(f"metadata sha256 {sha} != APK sha256 {actual_sha}")

size = str(meta.get("sizeBytes", ""))
if size != actual_size:
    errors.append(f"metadata sizeBytes {size!r} != APK size {actual_size}")

for field in ("builtAt", "gitCommit", "buildType"):
    if not str(meta.get(field, "")).strip():
        errors.append(f"metadata field {field} is required")

if errors:
    raise SystemExit("ERROR: Android APK release preflight failed:\n- " + "\n- ".join(errors))

print(f"Android APK release preflight passed: clientVersion={client_version} sha256={actual_sha}")
PY
