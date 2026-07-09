#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH=""
SERVER_CONFIG=""
SERVER_CLIENT_VERSION=""
SOURCE_CONFIG="$ROOT/Client_Base/src/orsc/Config.java"
ANDROID_BUILD_CONFIG="$ROOT/Android_Client/Open RSC Android Client/build.gradle"
PROVENANCE_ROOT="$ROOT"
PROVENANCE_TOOL="$ROOT/scripts/android-provenance.py"
META_PATH=""
EXPECTED_SIGNER_SHA256="${VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256:-}"
AAPT_BIN="${VOIDSCAPE_ANDROID_AAPT:-}"
APKSIGNER_BIN="${VOIDSCAPE_ANDROID_APKSIGNER:-}"

usage() {
	cat <<'USAGE'
Usage: scripts/check-android-apk-release.sh --apk PATH (--server-config PATH | --server-client-version N) --expected-signer-sha256 HEX [options]

Options:
  --source-config PATH         CLIENT_VERSION source (default: Client_Base/src/orsc/Config.java)
  --android-build-config PATH  Android version/SDK source (default: Android client build.gradle)
  --provenance-root PATH       Git checkout whose Android inputs built the APK (default: repo root)
  --meta PATH                  Metadata sidecar (default: ${APK_PATH}.json)
  --expected-signer-sha256 HEX Expected release certificate SHA-256. May instead be
                               set with VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256.
  --aapt PATH                  Android SDK aapt override.
  --apksigner PATH             Android SDK apksigner override.

Verifies an Android APK is safe to promote by checking:
  - Android SDK tools accept the APK manifest and signature.
  - the packaged app is com.voidscape.gg and is not debuggable.
  - the packaged signer certificate matches the explicitly trusted SHA-256.
  - packaged versionCode/versionName/minSdk/targetSdk match build.gradle and metadata.
  - sidecar clientVersion matches source and the target server client_version.
  - sidecar artifactType/buildType are apk/release and gitCommit is a full commit id.
  - schema-v3 provenance is embedded inside the signed APK and matches a clean source checkout.
  - sidecar sha256 and sizeBytes match the APK bytes.

The signer SHA-256 is a public certificate fingerprint, not a keystore password or key.
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
		--android-build-config)
			ANDROID_BUILD_CONFIG="${2:-}"
			shift 2
			;;
		--provenance-root)
			PROVENANCE_ROOT="${2:-}"
			shift 2
			;;
		--meta)
			META_PATH="${2:-}"
			shift 2
			;;
		--expected-signer-sha256)
			EXPECTED_SIGNER_SHA256="${2:-}"
			shift 2
			;;
		--aapt)
			AAPT_BIN="${2:-}"
			shift 2
			;;
		--apksigner)
			APKSIGNER_BIN="${2:-}"
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
if [[ -z "$EXPECTED_SIGNER_SHA256" ]]; then
	echo "ERROR: --expected-signer-sha256 or VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256 is required." >&2
	echo "Pass the trusted release certificate's public SHA-256 fingerprint; never pass keystore secrets." >&2
	exit 2
fi

EXPECTED_SIGNER_SHA256="$(printf '%s' "$EXPECTED_SIGNER_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]:')"
if [[ ! "$EXPECTED_SIGNER_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
	echo "ERROR: expected signer SHA-256 must contain exactly 64 hexadecimal digits." >&2
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
if [[ ! -f "$ANDROID_BUILD_CONFIG" ]]; then
	echo "ERROR: Android build config not found: $ANDROID_BUILD_CONFIG" >&2
	exit 1
fi
if [[ ! -d "$PROVENANCE_ROOT" || ! -f "$PROVENANCE_TOOL" ]]; then
	echo "ERROR: Android provenance checkout/helper is unavailable." >&2
	exit 1
fi
if [[ -z "$META_PATH" ]]; then
	META_PATH="${APK_PATH}.json"
fi
if [[ ! -f "$META_PATH" ]]; then
	echo "ERROR: APK metadata sidecar not found: $META_PATH" >&2
	echo "Expected release metadata including artifact/version, provenance, hash, and size fields." >&2
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

resolve_android_tool() {
	local tool_name="$1"
	local requested_path="$2"
	local candidate=""
	local found=""
	local local_sdk=""
	local root=""

	if [[ -n "$requested_path" ]]; then
		if [[ -x "$requested_path" ]]; then
			printf '%s\n' "$requested_path"
			return 0
		fi
		echo "ERROR: configured Android SDK tool is not executable: $requested_path" >&2
		return 1
	fi

	if candidate="$(command -v "$tool_name" 2>/dev/null)" && [[ -x "$candidate" ]]; then
		printf '%s\n' "$candidate"
		return 0
	fi

	if [[ -f "$ROOT/Android_Client/local.properties" ]]; then
		local_sdk="$(awk -F= '/^[[:space:]]*sdk\.dir[[:space:]]*=/ {
			sub(/^[^=]*=/, "");
			print;
			exit;
		}' "$ROOT/Android_Client/local.properties")"
	fi

	for root in \
		"${ANDROID_SDK_ROOT:-}" \
		"${ANDROID_HOME:-}" \
		"$local_sdk" \
		"$HOME/Library/Android/sdk" \
		"/opt/homebrew/share/android-commandlinetools"; do
		[[ -n "$root" && -d "$root/build-tools" ]] || continue
		for candidate in "$root"/build-tools/*/"$tool_name"; do
			[[ -x "$candidate" ]] && found="$candidate"
		done
	done

	if [[ -n "$found" ]]; then
		printf '%s\n' "$found"
		return 0
	fi

	echo "ERROR: required Android SDK tool '$tool_name' was not found; release promotion fails closed." >&2
	echo "Install Android SDK Build Tools or pass --$tool_name PATH." >&2
	return 1
}

if ! AAPT_BIN="$(resolve_android_tool aapt "$AAPT_BIN")"; then
	exit 1
fi
if ! APKSIGNER_BIN="$(resolve_android_tool apksigner "$APKSIGNER_BIN")"; then
	exit 1
fi

if ! apk_badging="$("$AAPT_BIN" dump badging "$APK_PATH" 2>&1)"; then
	echo "ERROR: aapt could not inspect APK manifest: $APK_PATH" >&2
	printf '%s\n' "$apk_badging" >&2
	exit 1
fi
if [[ -z "$apk_badging" ]]; then
	echo "ERROR: aapt returned no APK manifest data; release promotion fails closed." >&2
	exit 1
fi

if ! signature_output="$("$APKSIGNER_BIN" verify --verbose --print-certs "$APK_PATH" 2>&1)"; then
	echo "ERROR: APK signature verification failed: $APK_PATH" >&2
	printf '%s\n' "$signature_output" >&2
	exit 1
fi

actual_signer_count="$(printf '%s\n' "$signature_output" | awk '/Signer #[0-9]+ certificate SHA-256 digest:/ { count++ } END { print count + 0 }')"
actual_signer_sha256="$(printf '%s\n' "$signature_output" | awk -F': ' '/Signer #[0-9]+ certificate SHA-256 digest:/ {
	print tolower($2);
	exit;
}')"
actual_signer_sha256="$(printf '%s' "$actual_signer_sha256" | tr -d '[:space:]:')"
if [[ "$actual_signer_count" != "1" || ! "$actual_signer_sha256" =~ ^[0-9a-f]{64}$ ]]; then
	echo "ERROR: expected exactly one parseable APK signer certificate, found $actual_signer_count." >&2
	exit 1
fi
if [[ "$actual_signer_sha256" != "$EXPECTED_SIGNER_SHA256" ]]; then
	echo "ERROR: APK signer certificate SHA-256 does not match the trusted release signer." >&2
	echo "Expected: $EXPECTED_SIGNER_SHA256" >&2
	echo "Actual:   $actual_signer_sha256" >&2
	exit 1
fi

actual_sha="$(shasum -a 256 "$APK_PATH" | awk '{print $1}')"
actual_size="$(wc -c < "$APK_PATH" | tr -d ' ')"
read -r current_commit current_relevant_digest current_relevant_dirty current_commit_digest < <(
	python3 "$PROVENANCE_TOOL" state --repo-root "$PROVENANCE_ROOT" --format tsv
)

python3 - "$META_PATH" "$ANDROID_BUILD_CONFIG" "$source_client_version" "$SERVER_CLIENT_VERSION" "$actual_sha" "$actual_size" "$apk_badging" "$APK_PATH" "$current_commit" "$current_relevant_digest" "$current_relevant_dirty" "$current_commit_digest" <<'PY'
import json
import re
import sys
import zipfile
from pathlib import Path

(
    meta_path,
    android_build_config,
    source_client_version,
    server_client_version,
    actual_sha,
    actual_size,
    apk_badging,
    apk_path,
    current_commit,
    current_relevant_digest,
    current_relevant_dirty,
    current_commit_digest,
) = sys.argv[1:]

try:
    meta = json.loads(Path(meta_path).read_text(encoding="utf-8"))
except Exception as exc:
    raise SystemExit(f"ERROR: could not parse APK metadata {meta_path}: {exc}")
if not isinstance(meta, dict):
    raise SystemExit(f"ERROR: APK metadata {meta_path} must contain a JSON object")

try:
    gradle_text = Path(android_build_config).read_text(encoding="utf-8")
except Exception as exc:
    raise SystemExit(f"ERROR: could not read Android build config {android_build_config}: {exc}")

errors = []
expected_application_id = "com.voidscape.gg"

try:
    with zipfile.ZipFile(apk_path) as archive:
        matches = [
            name for name in archive.namelist()
            if name == "assets/voidscape-provenance.json"
        ]
        if len(matches) != 1:
            raise ValueError(f"expected one provenance asset, found {len(matches)}")
        embedded = json.loads(archive.read(matches[0]).decode("utf-8"))
except Exception as exc:
    embedded = {}
    errors.append(f"signed APK provenance asset is missing or invalid: {exc}")
if not isinstance(embedded, dict):
    errors.append("signed APK provenance asset must contain a JSON object")
    embedded = {}


def gradle_value(field, pattern, *, numeric=False):
    matches = re.findall(pattern, gradle_text, flags=re.MULTILINE)
    if len(matches) != 1:
        errors.append(
            f"could not uniquely parse source {field} from {android_build_config} "
            f"(found {len(matches)})"
        )
        return None
    value = matches[0]
    return int(value) if numeric else value


source_application_id = gradle_value(
    "applicationId", r'^\s*applicationId\s+["\']([^"\']+)["\']\s*(?://.*)?$'
)
source_version_code = gradle_value(
    "versionCode", r"^\s*versionCode\s+([0-9]+)\s*(?://.*)?$", numeric=True
)
source_version_name = gradle_value(
    "versionName", r'^\s*versionName\s+["\']([^"\']+)["\']\s*(?://.*)?$'
)
source_min_sdk = gradle_value(
    "minSdk", r"^\s*minSdk\s+([0-9]+)\s*(?://.*)?$", numeric=True
)
source_target_sdk = gradle_value(
    "targetSdk", r"^\s*targetSdk\s+([0-9]+)\s*(?://.*)?$", numeric=True
)

package_match = re.search(
    r"(?m)^package:\s+name='([^']+)'\s+versionCode='([^']+)'\s+versionName='([^']+)'",
    apk_badging,
)
min_sdk_match = re.search(r"(?m)^sdkVersion:'([^']+)'\s*$", apk_badging)
target_sdk_match = re.search(r"(?m)^targetSdkVersion:'([^']+)'\s*$", apk_badging)
if not package_match:
    errors.append("aapt output did not contain package/version manifest data")
    actual_application_id = actual_version_code = actual_version_name = None
else:
    actual_application_id, actual_version_code_raw, actual_version_name = package_match.groups()
    try:
        actual_version_code = int(actual_version_code_raw)
    except ValueError:
        actual_version_code = None
        errors.append(f"APK manifest versionCode is not numeric: {actual_version_code_raw!r}")


def parse_manifest_sdk(match, field):
    if not match:
        errors.append(f"aapt output did not contain APK manifest {field}")
        return None
    try:
        return int(match.group(1))
    except ValueError:
        errors.append(f"APK manifest {field} is not numeric: {match.group(1)!r}")
        return None


actual_min_sdk = parse_manifest_sdk(min_sdk_match, "minSdk")
actual_target_sdk = parse_manifest_sdk(target_sdk_match, "targetSdk")

if re.search(r"(?m)^application-debuggable\s*$", apk_badging):
    errors.append("APK manifest is debuggable; only a non-debuggable release APK may be promoted")

if source_application_id != expected_application_id:
    errors.append(
        f"source applicationId {source_application_id!r} != expected {expected_application_id!r}"
    )
if actual_application_id != expected_application_id:
    errors.append(
        f"APK manifest package {actual_application_id!r} != expected {expected_application_id!r}"
    )

client_version = str(meta.get("clientVersion", ""))
if client_version != source_client_version:
    errors.append(
        f"metadata clientVersion {client_version!r} != source CLIENT_VERSION {source_client_version}"
    )
if client_version != server_client_version:
    errors.append(
        f"metadata clientVersion {client_version!r} != server client_version {server_client_version}"
    )

if meta.get("artifactType") != "apk":
    errors.append(f"metadata artifactType {meta.get('artifactType')!r} != 'apk'")
if meta.get("buildType") != "release":
    errors.append(f"metadata buildType {meta.get('buildType')!r} != 'release'")
if "applicationId" in meta and meta.get("applicationId") != expected_application_id:
    errors.append(
        f"metadata applicationId {meta.get('applicationId')!r} != {expected_application_id!r}"
    )


def optional_positive_int(field):
    if field not in meta:
        return None
    value = meta.get(field)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        errors.append(f"metadata {field} must be a positive integer, got {value!r}")
        return None
    return value


metadata_version_code = optional_positive_int("versionCode")
metadata_min_sdk = optional_positive_int("minSdk")
metadata_target_sdk = optional_positive_int("targetSdk")
metadata_version_name = meta.get("versionName")
if "versionName" in meta and (
    not isinstance(metadata_version_name, str) or not metadata_version_name
):
    errors.append(f"metadata versionName must be a non-empty string, got {metadata_version_name!r}")

for field, actual, source, metadata in (
    ("versionCode", actual_version_code, source_version_code, metadata_version_code),
    ("versionName", actual_version_name, source_version_name, metadata_version_name),
    ("minSdk", actual_min_sdk, source_min_sdk, metadata_min_sdk),
    ("targetSdk", actual_target_sdk, source_target_sdk, metadata_target_sdk),
):
    if actual is not None and source is not None and actual != source:
        errors.append(f"APK manifest {field} {actual!r} != source {field} {source!r}")
    if actual is not None and metadata is not None and actual != metadata:
        errors.append(f"APK manifest {field} {actual!r} != metadata {field} {metadata!r}")
    if source is not None and metadata is not None and source != metadata:
        errors.append(f"metadata {field} {metadata!r} != source {field} {source!r}")

sha = str(meta.get("sha256", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    errors.append(f"metadata sha256 is invalid: {sha!r}")
elif sha != actual_sha:
    errors.append(f"metadata sha256 {sha} != APK sha256 {actual_sha}")

size = meta.get("sizeBytes")
if isinstance(size, bool) or not isinstance(size, int) or size < 0:
    errors.append(f"metadata sizeBytes must be a non-negative integer, got {size!r}")
elif str(size) != actual_size:
    errors.append(f"metadata sizeBytes {size!r} != APK size {actual_size}")

if not str(meta.get("builtAt", "")).strip():
    errors.append("metadata field builtAt is required")
git_commit = str(meta.get("gitCommit", ""))
if not re.fullmatch(r"[0-9a-fA-F]{40}", git_commit):
    errors.append(f"metadata gitCommit must be a full 40-hex commit id, got {git_commit!r}")

schema_version = meta.get("metadataSchemaVersion")
if (
    isinstance(schema_version, bool)
    or not isinstance(schema_version, int)
    or schema_version < 3
):
    errors.append(
        f"metadata metadataSchemaVersion must be >= 3 for release promotion, got {schema_version!r}"
    )
else:
    relevant_digest = str(meta.get("relevantInputDigest", "")).lower()
    if not re.fullmatch(r"[0-9a-f]{64}", relevant_digest):
        errors.append(
            f"metadata relevantInputDigest must be a 64-hex SHA-256, got {relevant_digest!r}"
        )
    relevant_dirty = meta.get("relevantInputDirty")
    if relevant_dirty is not False:
        errors.append(
            f"metadata relevantInputDirty must be false for release promotion, got {relevant_dirty!r}"
        )
    dirty_override = meta.get("dirtyReleaseOverride")
    if dirty_override is not False:
        errors.append(
            f"metadata dirtyReleaseOverride must be false for release promotion, got {dirty_override!r}"
        )

provenance_fields = (
    "metadataSchemaVersion",
    "gitCommit",
    "relevantInputDigest",
    "commitInputDigest",
    "relevantInputDirty",
    "dirtyReleaseOverride",
)
for field in provenance_fields:
    if meta.get(field) != embedded.get(field):
        errors.append(
            f"metadata {field} {meta.get(field)!r} != signed APK provenance {embedded.get(field)!r}"
        )

if embedded.get("metadataSchemaVersion") != schema_version:
    errors.append(
        "signed APK provenance metadataSchemaVersion does not match the sidecar"
    )
if git_commit.lower() != current_commit.lower():
    errors.append(
        f"metadata gitCommit {git_commit!r} != checked-out commit {current_commit!r}"
    )
if str(meta.get("relevantInputDigest", "")).lower() != current_relevant_digest.lower():
    errors.append(
        "metadata relevantInputDigest does not match the checked-out Android/shared inputs"
    )
if str(meta.get("commitInputDigest", "")).lower() != current_commit_digest.lower():
    errors.append("metadata commitInputDigest does not match the claimed commit tree")
if str(meta.get("relevantInputDigest", "")).lower() != str(meta.get("commitInputDigest", "")).lower():
    errors.append("embedded Android/shared input bytes do not match the claimed commit tree")
if current_relevant_dirty != "false":
    errors.append("checked-out Android/shared inputs are dirty; promotion requires a clean checkout")

if errors:
    raise SystemExit("ERROR: Android APK release preflight failed:\n- " + "\n- ".join(errors))
PY

echo "Android APK release preflight passed: clientVersion=$source_client_version sha256=$actual_sha signerSha256=$actual_signer_sha256"
