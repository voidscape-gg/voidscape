#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AAB_PATH=""
SERVER_CONFIG=""
SERVER_CLIENT_VERSION=""
SOURCE_CONFIG="$ROOT/Client_Base/src/orsc/Config.java"
ANDROID_BUILD_CONFIG="$ROOT/Android_Client/Open RSC Android Client/build.gradle"
PROVENANCE_ROOT="$ROOT"
PROVENANCE_TOOL="$ROOT/scripts/android-provenance.py"
META_PATH=""
CURRENT_PLAY_VERSION_CODE="${VOIDSCAPE_ANDROID_CURRENT_PLAY_VERSION_CODE:-}"
EXPECTED_SIGNER_SHA256="${VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256:-}"
JARSIGNER_BIN="${VOIDSCAPE_ANDROID_JARSIGNER:-}"
KEYTOOL_BIN="${VOIDSCAPE_ANDROID_KEYTOOL:-}"
BUNDLETOOL_BIN="${VOIDSCAPE_ANDROID_BUNDLETOOL:-}"

usage() {
	cat <<'USAGE'
Usage: scripts/check-android-play-release.sh --aab PATH (--server-config PATH | --server-client-version N) --current-play-version-code N --expected-signer-sha256 HEX [options]

Options:
  --source-config PATH               CLIENT_VERSION source (default: Client_Base/src/orsc/Config.java)
  --android-build-config PATH        Android version/SDK source (default: Android client build.gradle)
  --provenance-root PATH             Git checkout whose Android inputs built the AAB (default: repo root)
  --meta PATH                        Metadata sidecar (default: ${AAB_PATH}.json)
  --current-play-version-code N      Highest versionCode already uploaded to Play. The AAB
                                     versionCode must be strictly greater. May instead be set
                                     with VOIDSCAPE_ANDROID_CURRENT_PLAY_VERSION_CODE.
  --expected-signer-sha256 HEX       Expected upload certificate SHA-256. May instead be set
                                     with VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256.
  --jarsigner PATH                   JDK jarsigner override.
  --keytool PATH                     JDK keytool override.
  --bundletool PATH                  Bundletool executable or JAR override.

Verifies a Google Play Android App Bundle is safe to upload by checking:
  - jarsigner verifies the AAB and the embedded provenance asset is signed.
  - the AAB signer matches the explicitly trusted upload-certificate SHA-256.
  - bundletool validates the AAB structure and reports the expected package, release manifest,
    and source version/SDK values.
  - the AAB versionCode is strictly greater than Play's supplied highest uploaded code.
  - sidecar clientVersion matches source and the target server client_version.
  - sidecar artifact/build fields, SHA-256, and size match the AAB.
  - schema-v3 provenance is embedded at base/assets/voidscape-provenance.json.
  - embedded and sidecar provenance match the exact clean Android/shared relevant inputs
    and their claimed commit tree.

The signer SHA-256 is a public certificate fingerprint, not a keystore password or key.
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
		--current-play-version-code)
			CURRENT_PLAY_VERSION_CODE="${2:-}"
			shift 2
			;;
		--expected-signer-sha256)
			EXPECTED_SIGNER_SHA256="${2:-}"
			shift 2
			;;
		--jarsigner)
			JARSIGNER_BIN="${2:-}"
			shift 2
			;;
		--keytool)
			KEYTOOL_BIN="${2:-}"
			shift 2
			;;
		--bundletool)
			BUNDLETOOL_BIN="${2:-}"
			shift 2
			;;
		--min-version-code)
			echo "ERROR: --min-version-code is no longer accepted because it can allow an already-uploaded Play versionCode." >&2
			echo "Pass --current-play-version-code with Play Console's highest uploaded code instead." >&2
			exit 2
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
if [[ ! "$CURRENT_PLAY_VERSION_CODE" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --current-play-version-code or VOIDSCAPE_ANDROID_CURRENT_PLAY_VERSION_CODE is required and must be numeric." >&2
	exit 2
fi
if [[ -z "$EXPECTED_SIGNER_SHA256" ]]; then
	echo "ERROR: --expected-signer-sha256 or VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256 is required." >&2
	echo "Pass the trusted upload certificate's public SHA-256 fingerprint; never pass keystore secrets." >&2
	exit 2
fi

EXPECTED_SIGNER_SHA256="$(printf '%s' "$EXPECTED_SIGNER_SHA256" | tr '[:upper:]' '[:lower:]' | tr -d '[:space:]:')"
if [[ ! "$EXPECTED_SIGNER_SHA256" =~ ^[0-9a-f]{64}$ ]]; then
	echo "ERROR: expected signer SHA-256 must contain exactly 64 hexadecimal digits." >&2
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
if [[ ! -f "$ANDROID_BUILD_CONFIG" ]]; then
	echo "ERROR: Android build config not found: $ANDROID_BUILD_CONFIG" >&2
	exit 1
fi
if [[ ! -d "$PROVENANCE_ROOT" || ! -f "$PROVENANCE_TOOL" ]]; then
	echo "ERROR: Android provenance checkout/helper is unavailable." >&2
	exit 1
fi
if [[ -z "$META_PATH" ]]; then
	META_PATH="${AAB_PATH}.json"
fi
if [[ ! -f "$META_PATH" ]]; then
	echo "ERROR: AAB metadata sidecar not found: $META_PATH" >&2
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

resolve_executable() {
	local tool_name="$1"
	local requested_path="$2"
	local candidate=""

	if [[ -n "$requested_path" ]]; then
		if [[ -x "$requested_path" ]]; then
			printf '%s\n' "$requested_path"
			return 0
		fi
		echo "ERROR: configured $tool_name is not executable: $requested_path" >&2
		return 1
	fi
	if candidate="$(command -v "$tool_name" 2>/dev/null)" && [[ -x "$candidate" ]]; then
		printf '%s\n' "$candidate"
		return 0
	fi
	echo "ERROR: required tool '$tool_name' was not found; Play promotion fails closed." >&2
	return 1
}

resolve_bundletool() {
	local requested_path="$1"
	local candidate=""
	local found=""

	if [[ -n "$requested_path" ]]; then
		if [[ -x "$requested_path" || ( -f "$requested_path" && "$requested_path" == *.jar ) ]]; then
			printf '%s\n' "$requested_path"
			return 0
		fi
		echo "ERROR: configured bundletool executable/JAR is unavailable: $requested_path" >&2
		return 1
	fi
	if candidate="$(command -v bundletool 2>/dev/null)" && [[ -x "$candidate" ]]; then
		printf '%s\n' "$candidate"
		return 0
	fi
	for candidate in "$HOME"/.gradle/caches/modules-2/files-2.1/com.android.tools.build/bundletool/*/*/bundletool-*.jar; do
		[[ -f "$candidate" ]] && found="$candidate"
	done
	if [[ -n "$found" ]]; then
		# Android Gradle Plugin caches bundletool as a library JAR without a
		# Main-Class. The full module cache supplies its runtime dependencies.
		printf '%s\n' "@gradle-cache"
		return 0
	fi
	echo "ERROR: bundletool was not found; pass --bundletool PATH or install/build Android dependencies." >&2
	return 1
}

if ! JARSIGNER_BIN="$(resolve_executable jarsigner "$JARSIGNER_BIN")"; then
	exit 1
fi
if ! KEYTOOL_BIN="$(resolve_executable keytool "$KEYTOOL_BIN")"; then
	exit 1
fi
if ! BUNDLETOOL_BIN="$(resolve_bundletool "$BUNDLETOOL_BIN")"; then
	exit 1
fi
if [[ "$BUNDLETOOL_BIN" == *.jar || "$BUNDLETOOL_BIN" == "@gradle-cache" ]] \
	&& ! command -v java >/dev/null 2>&1; then
	echo "ERROR: Java is required to run bundletool JAR: $BUNDLETOOL_BIN" >&2
	exit 1
fi

run_bundletool() {
	if [[ "$BUNDLETOOL_BIN" == "@gradle-cache" ]]; then
		local classpath=""
		classpath="$(find "$HOME/.gradle/caches/modules-2/files-2.1" -type f -name '*.jar' -print 2>/dev/null | paste -sd: -)"
		if [[ -z "$classpath" ]]; then
			echo "ERROR: Android Gradle module cache cannot supply bundletool dependencies." >&2
			return 1
		fi
		java -cp "$classpath" com.android.tools.build.bundletool.BundleToolMain "$@"
	elif [[ "$BUNDLETOOL_BIN" == *.jar ]]; then
		java -jar "$BUNDLETOOL_BIN" "$@"
	else
		"$BUNDLETOOL_BIN" "$@"
	fi
}

signature_report="$(mktemp "${TMPDIR:-/tmp}/voidscape-aab-signature.XXXXXX")"
manifest_dump=""
manifest_error=""
validation_output=""
cleanup() {
	rm -f "$signature_report" "${manifest_dump:-}" "${manifest_error:-}" "${validation_output:-}"
}
trap cleanup EXIT

if ! "$JARSIGNER_BIN" -verify -verbose -certs "$AAB_PATH" >"$signature_report" 2>&1; then
	echo "ERROR: AAB signature verification failed: $AAB_PATH" >&2
	cat "$signature_report" >&2
	exit 1
fi
signature_output="$(cat "$signature_report")"
if [[ "$signature_output" != *"jar verified."* ]]; then
	echo "ERROR: jarsigner did not report a verified AAB signature." >&2
	printf '%s\n' "$signature_output" >&2
	exit 1
fi

python3 - "$AAB_PATH" "$signature_report" <<'PY'
import re
import sys
import zipfile
from pathlib import Path

aab_path, report_path = map(Path, sys.argv[1:])
entry_line = re.compile(
    r"^\s*(?P<status>[smk?]+)\s+\d+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+(?P<name>.+?)\s*$"
)
signed_entries = set()
for line in report_path.read_text(encoding="utf-8", errors="replace").splitlines():
    match = entry_line.match(line)
    if match and "s" in match.group("status"):
        signed_entries.add(match.group("name"))


def is_signature_metadata(name):
    upper = name.upper()
    if not upper.startswith("META-INF/"):
        return False
    relative = upper[len("META-INF/"):]
    if not relative or "/" in relative:
        return False
    return relative.startswith("SIG-") or relative.endswith((".SF", ".RSA", ".DSA", ".EC"))


try:
    with zipfile.ZipFile(aab_path) as archive:
        required_entries = {
            info.filename
            for info in archive.infolist()
            if not info.is_dir() and not is_signature_metadata(info.filename)
        }
except Exception as exc:
    raise SystemExit(f"ERROR: could not enumerate AAB signature coverage: {exc}")

unsigned_entries = sorted(required_entries - signed_entries)
if unsigned_entries:
    preview = ", ".join(repr(name) for name in unsigned_entries[:10])
    if len(unsigned_entries) > 10:
        preview += f", ... ({len(unsigned_entries) - 10} more)"
    raise SystemExit(
        "ERROR: AAB contains non-signature payload entries not covered by the verified JAR signature: "
        + preview
    )
PY

signed_provenance_count="$(printf '%s\n' "$signature_output" | awk '
	$NF == "base/assets/voidscape-provenance.json" && $1 ~ /s/ { count++ }
	END { print count + 0 }
')"
if [[ "$signed_provenance_count" != "1" ]]; then
	echo "ERROR: base/assets/voidscape-provenance.json is not covered exactly once by the verified AAB signature." >&2
	exit 1
fi

if ! certificate_output="$("$KEYTOOL_BIN" -printcert -jarfile "$AAB_PATH" 2>&1)"; then
	echo "ERROR: keytool could not inspect the AAB signer certificate." >&2
	printf '%s\n' "$certificate_output" >&2
	exit 1
fi
actual_signer_count="$(printf '%s\n' "$certificate_output" | awk '/^Signer #[0-9]+:/ { count++ } END { print count + 0 }')"
actual_signer_sha256="$(printf '%s\n' "$certificate_output" | awk -F': ' '/^[[:space:]]*SHA256:/ {
	print tolower($2);
	exit;
}')"
actual_signer_sha256="$(printf '%s' "$actual_signer_sha256" | tr -d '[:space:]:')"
if [[ "$actual_signer_count" != "1" || ! "$actual_signer_sha256" =~ ^[0-9a-f]{64}$ ]]; then
	echo "ERROR: expected exactly one parseable AAB signer certificate, found $actual_signer_count." >&2
	exit 1
fi
if [[ "$actual_signer_sha256" != "$EXPECTED_SIGNER_SHA256" ]]; then
	echo "ERROR: AAB signer certificate SHA-256 does not match the trusted upload signer." >&2
	echo "Expected: $EXPECTED_SIGNER_SHA256" >&2
	echo "Actual:   $actual_signer_sha256" >&2
	exit 1
fi

validation_output="$(mktemp "${TMPDIR:-/tmp}/voidscape-aab-validation.XXXXXX")"
if ! run_bundletool validate --bundle="$AAB_PATH" >"$validation_output" 2>&1; then
	echo "ERROR: bundletool validation failed for the AAB." >&2
	cat "$validation_output" >&2
	exit 1
fi

manifest_dump="$(mktemp "${TMPDIR:-/tmp}/voidscape-aab-manifest.XXXXXX")"
manifest_error="$(mktemp "${TMPDIR:-/tmp}/voidscape-aab-manifest-error.XXXXXX")"
if ! run_bundletool dump manifest --bundle="$AAB_PATH" --module=base >"$manifest_dump" 2>"$manifest_error"; then
	echo "ERROR: bundletool could not inspect the AAB base manifest." >&2
	cat "$manifest_error" >&2
	exit 1
fi
if [[ ! -s "$manifest_dump" ]]; then
	echo "ERROR: bundletool returned no AAB manifest data; Play promotion fails closed." >&2
	exit 1
fi

actual_sha="$(shasum -a 256 "$AAB_PATH" | awk '{print $1}')"
actual_size="$(wc -c < "$AAB_PATH" | tr -d ' ')"
read -r current_commit current_relevant_digest current_relevant_dirty current_commit_digest < <(
	python3 "$PROVENANCE_TOOL" state --repo-root "$PROVENANCE_ROOT" --format tsv
)

python3 - "$META_PATH" "$ANDROID_BUILD_CONFIG" "$source_client_version" "$SERVER_CLIENT_VERSION" "$actual_sha" "$actual_size" "$manifest_dump" "$AAB_PATH" "$current_commit" "$current_relevant_digest" "$current_relevant_dirty" "$current_commit_digest" "$CURRENT_PLAY_VERSION_CODE" <<'PY'
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
    manifest_path,
    aab_path,
    current_commit,
    current_relevant_digest,
    current_relevant_dirty,
    current_commit_digest,
    current_play_version_code,
) = sys.argv[1:]

try:
    meta = json.loads(Path(meta_path).read_text(encoding="utf-8"))
except Exception as exc:
    raise SystemExit(f"ERROR: could not parse AAB metadata {meta_path}: {exc}")
if not isinstance(meta, dict):
    raise SystemExit(f"ERROR: AAB metadata {meta_path} must contain a JSON object")

try:
    gradle_text = Path(android_build_config).read_text(encoding="utf-8")
except Exception as exc:
    raise SystemExit(f"ERROR: could not read Android build config {android_build_config}: {exc}")

errors = []
expected_application_id = "com.voidscape.gg"

try:
    with zipfile.ZipFile(aab_path) as archive:
        matches = [
            name
            for name in archive.namelist()
            if name == "base/assets/voidscape-provenance.json"
        ]
        if len(matches) != 1:
            raise ValueError(f"expected one provenance asset, found {len(matches)}")
        embedded = json.loads(archive.read(matches[0]).decode("utf-8"))
except Exception as exc:
    embedded = {}
    errors.append(f"signed AAB provenance asset is missing or invalid: {exc}")
if not isinstance(embedded, dict):
    errors.append("signed AAB provenance asset must contain a JSON object")
    embedded = {}

try:
    manifest_text = Path(manifest_path).read_text(encoding="utf-8")
except Exception as exc:
    manifest_text = ""
    errors.append(f"bundletool AAB manifest is unreadable: {exc}")


def manifest_tag(name):
    match = re.search(rf"<{re.escape(name)}\b(?P<attributes>[^>]*)>", manifest_text)
    return match.group("attributes") if match else None


def manifest_attribute(attributes, name):
    if attributes is None:
        return None
    match = re.search(
        rf"(?:android:)?{re.escape(name)}\s*=\s*['\"]([^'\"]+)['\"]",
        attributes,
    )
    return match.group(1) if match else None


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


def manifest_int(value, field):
    if value is None:
        errors.append(f"AAB manifest {field} is missing")
        return None
    try:
        return int(value, 0)
    except ValueError:
        errors.append(f"AAB manifest {field} is not numeric: {value!r}")
        return None


manifest_attributes = manifest_tag("manifest")
if manifest_attributes is None:
    errors.append("bundletool AAB manifest root is missing")
    actual_application_id = None
    actual_version_code = None
    actual_version_name = None
    actual_min_sdk = None
    actual_target_sdk = None
else:
    actual_application_id = manifest_attribute(manifest_attributes, "package")
    actual_version_code = manifest_int(
        manifest_attribute(manifest_attributes, "versionCode"), "versionCode"
    )
    actual_version_name = manifest_attribute(manifest_attributes, "versionName")
    uses_sdk_attributes = manifest_tag("uses-sdk")
    if uses_sdk_attributes is None:
        errors.append("AAB manifest uses-sdk is missing")
        actual_min_sdk = None
        actual_target_sdk = None
    else:
        actual_min_sdk = manifest_int(
            manifest_attribute(uses_sdk_attributes, "minSdkVersion"), "minSdk"
        )
        actual_target_sdk = manifest_int(
            manifest_attribute(uses_sdk_attributes, "targetSdkVersion"), "targetSdk"
        )
    application_attributes = manifest_tag("application")
    if application_attributes is None:
        errors.append("AAB manifest application is missing")
    elif (manifest_attribute(application_attributes, "debuggable") or "false").lower() != "false":
        errors.append("AAB manifest is debuggable; only a release bundle may be promoted")

if source_application_id != expected_application_id:
    errors.append(
        f"source applicationId {source_application_id!r} != expected {expected_application_id!r}"
    )
if actual_application_id != expected_application_id:
    errors.append(
        f"AAB manifest package {actual_application_id!r} != expected {expected_application_id!r}"
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

if meta.get("artifactType") != "android-app-bundle":
    errors.append(
        f"metadata artifactType {meta.get('artifactType')!r} != 'android-app-bundle'"
    )
if meta.get("buildType") != "release":
    errors.append(f"metadata buildType {meta.get('buildType')!r} != 'release'")
if meta.get("applicationId") != expected_application_id:
    errors.append(
        f"metadata applicationId {meta.get('applicationId')!r} != {expected_application_id!r}"
    )


def required_positive_int(field):
    value = meta.get(field)
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        errors.append(f"metadata {field} must be a positive integer, got {value!r}")
        return None
    return value


metadata_version_code = required_positive_int("versionCode")
metadata_min_sdk = required_positive_int("minSdk")
metadata_target_sdk = required_positive_int("targetSdk")
metadata_version_name = meta.get("versionName")
if not isinstance(metadata_version_name, str) or not metadata_version_name:
    errors.append(
        f"metadata versionName must be a non-empty string, got {metadata_version_name!r}"
    )

for field, actual, source, metadata in (
    ("versionCode", actual_version_code, source_version_code, metadata_version_code),
    ("versionName", actual_version_name, source_version_name, metadata_version_name),
    ("minSdk", actual_min_sdk, source_min_sdk, metadata_min_sdk),
    ("targetSdk", actual_target_sdk, source_target_sdk, metadata_target_sdk),
):
    if actual is not None and source is not None and actual != source:
        errors.append(f"AAB manifest {field} {actual!r} != source {field} {source!r}")
    if actual is not None and metadata is not None and actual != metadata:
        errors.append(f"AAB manifest {field} {actual!r} != metadata {field} {metadata!r}")
    if source is not None and metadata is not None and source != metadata:
        errors.append(f"metadata {field} {metadata!r} != source {field} {source!r}")

play_highest = int(current_play_version_code)
if actual_version_code is not None and actual_version_code <= play_highest:
    errors.append(
        f"AAB versionCode {actual_version_code} must be greater than current Play highest {play_highest}"
    )
if actual_target_sdk is not None and actual_target_sdk < 35:
    errors.append(
        f"AAB targetSdk must be >= 35 for current Play updates, got {actual_target_sdk}"
    )

sha = str(meta.get("sha256", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", sha):
    errors.append(f"metadata sha256 is invalid: {sha!r}")
elif sha != actual_sha:
    errors.append(f"metadata sha256 {sha} != AAB sha256 {actual_sha}")

size = meta.get("sizeBytes")
if isinstance(size, bool) or not isinstance(size, int) or size < 0:
    errors.append(f"metadata sizeBytes must be a non-negative integer, got {size!r}")
elif str(size) != actual_size:
    errors.append(f"metadata sizeBytes {size!r} != AAB size {actual_size}")

if not str(meta.get("builtAt", "")).strip():
    errors.append("metadata field builtAt is required")
git_commit = str(meta.get("gitCommit", ""))
if not re.fullmatch(r"[0-9a-fA-F]{40}", git_commit):
    errors.append(f"metadata gitCommit must be a full 40-hex commit id, got {git_commit!r}")

schema_version = meta.get("metadataSchemaVersion")
if schema_version != 3 or isinstance(schema_version, bool):
    errors.append(
        f"metadata metadataSchemaVersion must be exactly 3 for Play promotion, got {schema_version!r}"
    )

relevant_digest = str(meta.get("relevantInputDigest", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", relevant_digest):
    errors.append(
        f"metadata relevantInputDigest must be a 64-hex SHA-256, got {relevant_digest!r}"
    )
commit_digest = str(meta.get("commitInputDigest", "")).lower()
if not re.fullmatch(r"[0-9a-f]{64}", commit_digest):
    errors.append(
        f"metadata commitInputDigest must be a 64-hex SHA-256, got {commit_digest!r}"
    )
relevant_dirty = meta.get("relevantInputDirty")
if relevant_dirty is not False:
    errors.append(
        f"metadata relevantInputDirty must be false for Play promotion, got {relevant_dirty!r}"
    )
dirty_override = meta.get("dirtyReleaseOverride")
if dirty_override is not False:
    errors.append(
        f"metadata dirtyReleaseOverride must be false for Play promotion, got {dirty_override!r}"
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
            f"metadata {field} {meta.get(field)!r} != signed AAB provenance {embedded.get(field)!r}"
        )

if embedded.get("metadataSchemaVersion") != 3:
    errors.append(
        f"signed AAB provenance metadataSchemaVersion must be exactly 3, got {embedded.get('metadataSchemaVersion')!r}"
    )
if git_commit.lower() != current_commit.lower():
    errors.append(
        f"metadata gitCommit {git_commit!r} != checked-out commit {current_commit!r}"
    )
if relevant_digest != current_relevant_digest.lower():
    errors.append(
        "metadata relevantInputDigest does not match the checked-out Android/shared inputs"
    )
if commit_digest != current_commit_digest.lower():
    errors.append("metadata commitInputDigest does not match the claimed commit tree")
if relevant_digest != commit_digest:
    errors.append("embedded Android/shared input bytes do not match the claimed commit tree")
if current_relevant_dirty != "false":
    errors.append(
        "checked-out Android/shared relevant inputs are dirty; Play promotion requires those inputs to match the claimed commit"
    )

if errors:
    raise SystemExit("ERROR: Android Play release preflight failed:\n- " + "\n- ".join(errors))
PY

echo "Android Play release preflight passed: clientVersion=$source_client_version versionCode>$(printf '%s' "$CURRENT_PLAY_VERSION_CODE") sha256=$actual_sha signerSha256=$actual_signer_sha256"
