#!/usr/bin/env bash
# Verify a hosted TeaVM iPhone web-client deployment before sharing it.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${WEB_TEA_VERIFY_BASE_URL:-}"
WS_URL="${WEB_TEA_VERIFY_WS_URL:-}"
OUT_DIR="${WEB_TEA_VERIFY_OUT:-$ROOT/tmp/web-teavm-deployment-verify}"
AUTH_USER="${WEB_TEA_SMOKE_USER:-test}"
AUTH_PASS="${WEB_TEA_SMOKE_PASS:-test}"
PORTAL_URL="${WEB_TEA_VERIFY_PORTAL_URL:-}"
PORTAL_ACCOUNT_URL="${WEB_TEA_VERIFY_PORTAL_ACCOUNT_URL:-}"
PORTAL_RECOVERY_URL="${WEB_TEA_VERIFY_PORTAL_RECOVERY_URL:-}"
TIMEOUT="${WEB_TEA_VERIFY_TIMEOUT:-20}"
RUN_SMOKE=0
ALLOW_HTTP=0
INSECURE=0
ALLOW_DEBUG=0
ALLOW_INSECURE_WS=0
DEEP_MANIFEST=0
CHROME_PATH="${CHROME_PATH:-${PLAYWRIGHT_CHROMIUM_EXECUTABLE:-}}"
PLAYWRIGHT_CORE_DIR="${PLAYWRIGHT_CORE_DIR:-}"
EXPECTED_BUILD_MANIFEST="${WEB_TEA_VERIFY_EXPECTED_BUILD_MANIFEST:-}"

usage() {
	cat <<'EOF'
Usage: scripts/verify-web-teavm-deployment.sh --base-url URL [options]
       scripts/verify-web-teavm-deployment.sh URL [options]

Options:
  --base-url URL          HTTPS directory URL that serves the web client.
  --ws URL                Explicit WSS endpoint for deployed smoke, e.g. wss://host/ws/.
  --smoke                 Also run the Chrome iPhone-emulation login smoke.
  --user USER             Login username for --smoke. Default: test.
  --pass PASS             Login password for --smoke. Default: test.
  --portal URL            Portal base URL passed into deployed smoke.
  --portal-account-url URL
                          Explicit Create Account portal URL passed into deployed smoke.
  --portal-recovery-url URL
                          Explicit Recover account portal URL passed into deployed smoke.
  --out DIR               Output directory for headers, summary, and smoke artifacts.
  --timeout SECONDS       curl timeout for each deployment check. Default: 20.
  --allow-http            Permit http:// base URLs for local fixture testing.
  --insecure              Ignore TLS certificate errors. Use only for local/staging.
  --allow-debug           Do not fail if TeaVM .map/.teavmdbg files are served.
  --allow-insecure-ws     Permit ws:// with an HTTPS page. Not for production.
  --expected-build-manifest FILE
                          Compare deployed voidscape-web-build.json to this local package manifest.
  --deep-manifest         Fetch and verify every file listed in voidscape-web-build.json.
  --chrome PATH           Chrome/Chromium executable for --smoke.
  --playwright-core DIR   playwright-core package directory for --smoke.
  -h, --help              Show this help.

The verifier checks:
  - required static files load from the deployed root
  - production HTTPS responses use the documented Cache-Control policy
  - deployed voidscape-web-build.json is present and matches required file hashes
  - optional deep verification proves every build-manifest file is hosted
	  - index/manifest/client JS contain the expected iPhone web-client hooks,
	    including copied diagnostics, custom-HUD uiHistory, scrollHistory,
	    post-resume gameplay proof, resource-mode controls, and mobile endpoint state
  - icons are valid PNGs
  - local runtime cache files and TeaVM debug files are not publicly exposed
  - optional real-login deployed smoke using scripts/smoke-web-teavm-iphone.sh

Examples:
  scripts/verify-web-teavm-deployment.sh https://play.example.com/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest
  scripts/verify-web-teavm-deployment.sh https://play.example.com/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://play.example.com/ws/ --smoke
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--base-url)
			BASE_URL="${2:-}"
			shift 2
			;;
		--ws)
			WS_URL="${2:-}"
			shift 2
			;;
		--smoke)
			RUN_SMOKE=1
			shift
			;;
		--user)
			AUTH_USER="${2:-}"
			shift 2
			;;
		--pass)
			AUTH_PASS="${2:-}"
			shift 2
			;;
		--portal)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--portal-account-url)
			PORTAL_ACCOUNT_URL="${2:-}"
			shift 2
			;;
		--portal-recovery-url)
			PORTAL_RECOVERY_URL="${2:-}"
			shift 2
			;;
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--timeout)
			TIMEOUT="${2:-}"
			shift 2
			;;
		--allow-http)
			ALLOW_HTTP=1
			shift
			;;
		--insecure)
			INSECURE=1
			shift
			;;
		--allow-debug)
			ALLOW_DEBUG=1
			shift
			;;
		--allow-insecure-ws)
			ALLOW_INSECURE_WS=1
			shift
			;;
		--expected-build-manifest)
			EXPECTED_BUILD_MANIFEST="${2:-}"
			shift 2
			;;
		--deep-manifest)
			DEEP_MANIFEST=1
			shift
			;;
		--chrome)
			CHROME_PATH="${2:-}"
			shift 2
			;;
		--playwright-core)
			PLAYWRIGHT_CORE_DIR="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		--*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 2
			;;
		*)
			if [[ -n "$BASE_URL" ]]; then
				echo "Unexpected positional argument: $1" >&2
				usage >&2
				exit 2
			fi
			BASE_URL="$1"
			shift
			;;
	esac
done

if [[ -z "$BASE_URL" ]]; then
	echo "ERROR: --base-url is required." >&2
	usage >&2
	exit 2
fi
if [[ -z "$AUTH_USER" || -z "$AUTH_PASS" ]]; then
	echo "ERROR: --user and --pass must be non-empty when --smoke is used." >&2
	exit 2
fi

BASE_URL="$(python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse, urlunparse
import sys

url = sys.argv[1].strip()
parsed = urlparse(url)
if parsed.scheme not in {"http", "https"} or not parsed.netloc:
    print(f"ERROR: base URL must be an absolute http(s) URL, got {url!r}", file=sys.stderr)
    sys.exit(2)
if parsed.query or parsed.fragment:
    print("ERROR: pass the deployed directory/root URL without query or fragment.", file=sys.stderr)
    sys.exit(2)
if parsed.path and not parsed.path.endswith("/"):
    parsed = parsed._replace(path=parsed.path + "/")
if not parsed.path:
    parsed = parsed._replace(path="/")
print(urlunparse(parsed))
PY
)"

BASE_SCHEME="$(python3 - "$BASE_URL" <<'PY'
from urllib.parse import urlparse
import sys
print(urlparse(sys.argv[1]).scheme)
PY
)"

if [[ "$BASE_SCHEME" != "https" && "$ALLOW_HTTP" -eq 0 ]]; then
	echo "ERROR: production verification requires https://. Pass --allow-http only for local fixtures." >&2
	exit 2
fi

if [[ -n "$WS_URL" ]]; then
	WS_SCHEME="$(python3 - "$WS_URL" <<'PY'
from urllib.parse import urlparse
import sys
print(urlparse(sys.argv[1]).scheme)
PY
)"
	if [[ "$WS_SCHEME" != "ws" && "$WS_SCHEME" != "wss" ]]; then
		echo "ERROR: --ws must be an absolute ws:// or wss:// URL." >&2
		exit 2
	fi
	if [[ "$BASE_SCHEME" == "https" && "$WS_SCHEME" != "wss" && "$ALLOW_INSECURE_WS" -eq 0 ]]; then
		echo "ERROR: an HTTPS page must use wss://. Pass --allow-insecure-ws only for local fixtures." >&2
		exit 2
	fi
fi

if [[ -n "$EXPECTED_BUILD_MANIFEST" && ! -f "$EXPECTED_BUILD_MANIFEST" ]]; then
	echo "ERROR: --expected-build-manifest does not exist: $EXPECTED_BUILD_MANIFEST" >&2
	exit 2
fi

mkdir -p "$OUT_DIR/responses"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
RESULTS_TSV="$OUT_DIR/results.tsv"
FAILURES_FILE="$OUT_DIR/failures.txt"
WARNINGS_FILE="$OUT_DIR/warnings.txt"
SUMMARY_JSON="$OUT_DIR/summary.json"
BUILD_MANIFEST_SHA=""
EXPECTED_BUILD_MANIFEST_SHA=""
BUILD_MANIFEST_MATCHES_EXPECTED=""
DEEP_MANIFEST_RESULTS_JSON="$OUT_DIR/deep-manifest-results.json"
DEEP_MANIFEST_CHECKED="false"
DEEP_MANIFEST_FILE_COUNT=0
DEEP_MANIFEST_VERIFIED_COUNT=0
DEEP_MANIFEST_FAILURE_COUNT=0
CACHE_POLICY_RESULTS_JSON="$OUT_DIR/cache-policy-results.json"
CACHE_POLICY_CHECKED="false"
CACHE_POLICY_FAILURE_COUNT=0
SMOKE_RAN="false"
SMOKE_PASSED="false"
SMOKE_ARTIFACT="$OUT_DIR/smoke"
SMOKE_SUMMARY="$OUT_DIR/smoke/summary.json"
: > "$RESULTS_TSV"
: > "$FAILURES_FILE"
: > "$WARNINGS_FILE"

CURL_ARGS=(-sS -L --connect-timeout "$TIMEOUT" --max-time "$TIMEOUT")
if [[ "$INSECURE" -eq 1 ]]; then
	CURL_ARGS+=(-k)
fi

FAILURES=()
WARNINGS=()
FETCH_URL=
FETCH_STATUS=
FETCH_CURL_CODE=
FETCH_BODY=
FETCH_HEADERS=
FETCH_SIZE=
FETCH_CONTENT_TYPE=
INDEX_SHA=
INDEX_BODY=

record_failure() {
	FAILURES+=("$*")
	echo "FAIL: $*" >&2
}

record_warning() {
	WARNINGS+=("$*")
	echo "WARN: $*" >&2
}

url_for() {
	python3 - "$BASE_URL" "$1" <<'PY'
from urllib.parse import urljoin
import sys

base = sys.argv[1]
path = sys.argv[2]
if path == "/":
    print(base)
else:
    print(urljoin(base, path))
PY
}

safe_name() {
	python3 - "$1" <<'PY'
import re
import sys

value = sys.argv[1].strip("/") or "root"
value = re.sub(r"[^A-Za-z0-9._-]+", "_", value)
print(value[:180] or "root")
PY
}

header_value() {
	python3 - "$1" "$2" <<'PY'
import sys

path, name = sys.argv[1], sys.argv[2].lower()
value = ""
try:
    with open(path, "rb") as handle:
        for raw in handle:
            line = raw.decode("iso-8859-1", "replace").rstrip("\r\n")
            if ":" not in line:
                continue
            key, current = line.split(":", 1)
            if key.strip().lower() == name:
                value = current.strip()
except FileNotFoundError:
    pass
print(value)
PY
}

sha256_file() {
	python3 - "$1" <<'PY'
import hashlib
import sys

h = hashlib.sha256()
with open(sys.argv[1], "rb") as handle:
    for chunk in iter(lambda: handle.read(1024 * 1024), b""):
        h.update(chunk)
print(h.hexdigest())
PY
}

fetch_path() {
	local relative="$1"
	local kind="$2"
	local note="${3:-}"
	local label
	local err_file

	FETCH_URL="$(url_for "$relative")"
	label="$(safe_name "$relative")"
	FETCH_HEADERS="$OUT_DIR/responses/$label.headers"
	FETCH_BODY="$OUT_DIR/responses/$label.body"
	err_file="$OUT_DIR/responses/$label.stderr"
	rm -f "$FETCH_HEADERS" "$FETCH_BODY" "$err_file"

	set +e
	FETCH_STATUS="$(curl "${CURL_ARGS[@]}" -o "$FETCH_BODY" -D "$FETCH_HEADERS" -w "%{http_code}" "$FETCH_URL" 2>"$err_file")"
	FETCH_CURL_CODE=$?
	set -e
	if [[ ! -f "$FETCH_BODY" ]]; then
		: > "$FETCH_BODY"
	fi
	if [[ ! -f "$FETCH_HEADERS" ]]; then
		: > "$FETCH_HEADERS"
	fi
	FETCH_SIZE="$(wc -c < "$FETCH_BODY" | tr -d ' ')"
	FETCH_CONTENT_TYPE="$(header_value "$FETCH_HEADERS" "content-type")"
	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
		"$kind" "$relative" "$FETCH_URL" "$FETCH_STATUS" "$FETCH_CURL_CODE" "$FETCH_SIZE" "$FETCH_CONTENT_TYPE" "$note" >> "$RESULTS_TSV"
}

require_200() {
	local relative="$1"
	local label="$2"
	fetch_path "$relative" "required" "$label"
	if [[ "$FETCH_CURL_CODE" -ne 0 ]]; then
		record_failure "$label could not be fetched from $FETCH_URL (curl exit $FETCH_CURL_CODE)."
		return
	fi
	if [[ "$FETCH_STATUS" != "200" ]]; then
		record_failure "$label returned HTTP $FETCH_STATUS from $FETCH_URL."
		return
	fi
	if [[ "$FETCH_SIZE" -le 0 ]]; then
		record_failure "$label returned an empty body from $FETCH_URL."
	fi
}

warn_unless_content_type_contains() {
	local label="$1"
	local expected="$2"
	local actual="$3"
	if [[ -z "$actual" ]]; then
		record_warning "$label did not return a Content-Type header."
	elif [[ "$actual" != *"$expected"* ]]; then
		record_warning "$label Content-Type was '$actual' rather than containing '$expected'."
	fi
}

require_body_contains() {
	local file="$1"
	local needle="$2"
	local label="$3"
	if ! grep -q "$needle" "$file"; then
		record_failure "$label does not contain expected marker: $needle"
	fi
}

require_png_signature() {
	local file="$1"
	local label="$2"
	if ! python3 - "$file" <<'PY'
import sys

with open(sys.argv[1], "rb") as handle:
    data = handle.read(8)
sys.exit(0 if data == b"\x89PNG\r\n\x1a\n" else 1)
PY
	then
		record_failure "$label is not a valid PNG file."
	fi
}

require_200 "/" "root page"
ROOT_BODY="$FETCH_BODY"
ROOT_CONTENT_TYPE="$FETCH_CONTENT_TYPE"
require_200 "index.html" "index.html"
INDEX_BODY="$FETCH_BODY"
INDEX_SHA="$(sha256_file "$INDEX_BODY")"
warn_unless_content_type_contains "index.html" "html" "$FETCH_CONTENT_TYPE"
require_body_contains "$INDEX_BODY" "voidscape-web-client.js" "index.html"
require_body_contains "$INDEX_BODY" "manifest.webmanifest" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeCollectDiagnostics" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeOpenClientUrl" "index.html"
require_body_contains "$INDEX_BODY" "controlsHistory" "index.html"
require_body_contains "$INDEX_BODY" "uiHistory" "index.html"
require_body_contains "$INDEX_BODY" "scrollHistory" "index.html"
require_body_contains "$INDEX_BODY" "postResumeProof" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeProfile" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeProfileStorageKey" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeSetResourceMode" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapePrepareFrameUpload" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeResourceState" "index.html"
require_body_contains "$INDEX_BODY" "resource-gfx-off" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeRecordInputActivity" "index.html"
require_body_contains "$INDEX_BODY" "__voidscapeRecordClientState" "index.html"
require_body_contains "$INDEX_BODY" "portalAccountUrl" "index.html"
require_body_contains "$INDEX_BODY" "portalRecoveryUrl" "index.html"
require_body_contains "$INDEX_BODY" "viewport-fit=cover" "index.html"
require_body_contains "$INDEX_BODY" "apple-mobile-web-app-capable" "index.html"
require_body_contains "$INDEX_BODY" "mobile-web-app-capable" "index.html"
require_body_contains "$INDEX_BODY" "apple-mobile-web-app-title" "index.html"
require_body_contains "$INDEX_BODY" "apple-mobile-web-app-status-bar-style" "index.html"
require_body_contains "$INDEX_BODY" "apple-touch-icon" "index.html"
require_body_contains "$INDEX_BODY" "format-detection" "index.html"

if [[ -n "$ROOT_BODY" && -f "$ROOT_BODY" ]]; then
	require_body_contains "$ROOT_BODY" "voidscape-web-client.js" "root page"
	warn_unless_content_type_contains "root page" "html" "$ROOT_CONTENT_TYPE"
fi

require_200 "manifest.webmanifest" "manifest.webmanifest"
MANIFEST_BODY="$FETCH_BODY"
warn_unless_content_type_contains "manifest.webmanifest" "json" "$FETCH_CONTENT_TYPE"
if ! python3 - "$MANIFEST_BODY" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    manifest = json.load(handle)

errors = []
if manifest.get("name") != "Voidscape":
    errors.append("name must be Voidscape")
if manifest.get("short_name") != "Voidscape":
    errors.append("short_name must be Voidscape")
if manifest.get("display") != "fullscreen":
    errors.append("display must be fullscreen")
if manifest.get("scope") != "./":
    errors.append("scope must be ./")
if manifest.get("orientation") != "any":
    errors.append("orientation must be any")
if manifest.get("background_color") != "#000000":
    errors.append("background_color must be #000000")
if manifest.get("theme_color") != "#000000":
    errors.append("theme_color must be #000000")
if "mobile=1" not in manifest.get("start_url", ""):
    errors.append("start_url must include mobile=1")
icons = manifest.get("icons") or []
sizes = {icon.get("sizes") for icon in icons}
if "192x192" not in sizes or "512x512" not in sizes:
    errors.append("icons must include 192x192 and 512x512")
if errors:
    print("; ".join(errors), file=sys.stderr)
    sys.exit(1)
PY
then
	record_failure "manifest.webmanifest is not the expected Voidscape iPhone manifest."
fi

require_200 "voidscape-web-client.js" "voidscape-web-client.js"
JS_BODY="$FETCH_BODY"
warn_unless_content_type_contains "voidscape-web-client.js" "javascript" "$FETCH_CONTENT_TYPE"
require_body_contains "$JS_BODY" "__voidscapeEndpoint" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeEffectiveWebSocketUrl" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeInputQueue" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeUiState" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeUiHistory" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeScrollHistory" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeRecordInputActivity" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "__voidscapeRecordClientState" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "messageTab" "voidscape-web-client.js"
require_body_contains "$JS_BODY" "showUiTab" "voidscape-web-client.js"

for icon in "icon-192.png" "icon-512.png" "apple-touch-icon.png" "favicon.png"; do
	require_200 "$icon" "$icon"
	warn_unless_content_type_contains "$icon" "image/png" "$FETCH_CONTENT_TYPE"
	require_png_signature "$FETCH_BODY" "$icon"
done

require_200 "Cache/MD5.SUM" "Cache/MD5.SUM"
if [[ "$FETCH_SIZE" -lt 16 ]]; then
	record_failure "Cache/MD5.SUM looks too small to be a real cache checksum file."
fi

require_200 "voidscape-web-build.json" "voidscape-web-build.json"
BUILD_MANIFEST_BODY="$FETCH_BODY"
BUILD_MANIFEST_SHA="$(sha256_file "$BUILD_MANIFEST_BODY")"
if [[ -n "$EXPECTED_BUILD_MANIFEST" ]]; then
	EXPECTED_BUILD_MANIFEST_SHA="$(sha256_file "$EXPECTED_BUILD_MANIFEST")"
	if [[ "$BUILD_MANIFEST_SHA" == "$EXPECTED_BUILD_MANIFEST_SHA" ]]; then
		BUILD_MANIFEST_MATCHES_EXPECTED="true"
	else
		BUILD_MANIFEST_MATCHES_EXPECTED="false"
		record_failure "deployed voidscape-web-build.json does not match expected local package manifest $EXPECTED_BUILD_MANIFEST."
	fi
fi
if ! python3 - "$BUILD_MANIFEST_BODY" "$OUT_DIR/responses" <<'PY'
from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

manifest_path = Path(sys.argv[1])
responses_dir = Path(sys.argv[2])
required_paths = [
    "index.html",
    "manifest.webmanifest",
    "voidscape-web-client.js",
    "icon-192.png",
    "icon-512.png",
    "apple-touch-icon.png",
    "favicon.png",
    "Cache/MD5.SUM",
]
forbidden_fragments = (
    "accounts.txt",
    "credentials.txt",
    "uid.dat",
    "ip.txt",
    "port.txt",
    "config.txt",
    ".map",
    ".teavmdbg",
)

def safe_name(value: str) -> str:
    value = value.strip("/") or "root"
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", value)
    return value[:180] or "root"

try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
except Exception as exc:
    print(f"invalid JSON: {exc}", file=sys.stderr)
    raise SystemExit(1)

errors: list[str] = []
if manifest.get("schemaVersion") != 1:
    errors.append("schemaVersion must be 1")
if manifest.get("name") != "voidscape-teavm-web-client":
    errors.append("name must be voidscape-teavm-web-client")
files = manifest.get("files")
if not isinstance(files, list) or not files:
    errors.append("files must be a non-empty list")
    files = []
if manifest.get("fileCount") != len(files):
    errors.append("fileCount must match files length")

by_path = {}
for entry in files:
    if not isinstance(entry, dict):
        errors.append("files entries must be objects")
        continue
    path = entry.get("path")
    size = entry.get("size")
    digest = entry.get("sha256")
    if not isinstance(path, str) or not path or path.startswith("/") or ".." in path.split("/"):
        errors.append(f"invalid manifest path: {path!r}")
        continue
    if any(fragment in path for fragment in forbidden_fragments):
        errors.append(f"manifest includes forbidden/debug/runtime path: {path}")
    if not isinstance(size, int) or size <= 0:
        errors.append(f"manifest path {path} has invalid size {size!r}")
    if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-f]{64}", digest):
        errors.append(f"manifest path {path} has invalid sha256 {digest!r}")
    if path in by_path:
        errors.append(f"manifest path is duplicated: {path}")
    by_path[path] = entry

for path in required_paths:
    entry = by_path.get(path)
    if not entry:
        errors.append(f"manifest is missing required path: {path}")
        continue
    body = responses_dir / f"{safe_name(path)}.body"
    if not body.exists():
        errors.append(f"missing fetched response body for required path: {path}")
        continue
    data = body.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != entry.get("sha256"):
        errors.append(f"manifest sha256 mismatch for {path}: {entry.get('sha256')} != {digest}")
    if len(data) != entry.get("size"):
        errors.append(f"manifest size mismatch for {path}: {entry.get('size')} != {len(data)}")

if errors:
    for error in errors:
        print(error, file=sys.stderr)
    raise SystemExit(1)
PY
then
	record_failure "voidscape-web-build.json is invalid or does not match required deployed files."
fi

if [[ "$DEEP_MANIFEST" -eq 1 ]]; then
	set +e
	python3 - "$BUILD_MANIFEST_BODY" "$BASE_URL" "$TIMEOUT" "$INSECURE" "$DEEP_MANIFEST_RESULTS_JSON" <<'PY'
from __future__ import annotations

import hashlib
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

manifest_path = Path(sys.argv[1])
base_url = sys.argv[2]
timeout = float(sys.argv[3])
insecure = sys.argv[4] == "1"
results_path = Path(sys.argv[5])

forbidden_fragments = (
    "accounts.txt",
    "credentials.txt",
    "uid.dat",
    "ip.txt",
    "port.txt",
    "config.txt",
    ".map",
    ".teavmdbg",
)


def write_artifact(artifact: dict[str, Any]) -> None:
    results_path.write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")


def file_url(path: str) -> str:
    quoted = urllib.parse.quote(path, safe="/._-~!$&'()*+,;=:@%")
    return urllib.parse.urljoin(base_url, quoted)


started_at = time.time()
artifact: dict[str, Any] = {
    "baseUrl": base_url,
    "checked": False,
    "fileCount": 0,
    "verifiedCount": 0,
    "failureCount": 0,
    "failures": [],
    "results": [],
}

errors: list[str] = []
try:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
except Exception as exc:
    artifact["failures"] = [f"invalid build manifest JSON: {exc}"]
    artifact["failureCount"] = 1
    artifact["durationSeconds"] = round(time.time() - started_at, 3)
    write_artifact(artifact)
    print(artifact["failures"][0], file=sys.stderr)
    raise SystemExit(1)

files = manifest.get("files")
if not isinstance(files, list) or not files:
    errors.append("build manifest files must be a non-empty list")
    files = []
artifact["fileCount"] = len(files)

seen: set[str] = set()
for index, entry in enumerate(files):
    if not isinstance(entry, dict):
        errors.append(f"files[{index}] must be an object")
        continue
    path = entry.get("path")
    expected_size = entry.get("size")
    expected_sha = entry.get("sha256")
    if not isinstance(path, str) or not path or path.startswith("/") or ".." in path.split("/"):
        errors.append(f"files[{index}] has invalid path {path!r}")
        continue
    if path in seen:
        errors.append(f"manifest path is duplicated: {path}")
        continue
    seen.add(path)
    if any(fragment in path for fragment in forbidden_fragments):
        errors.append(f"manifest includes forbidden/debug/runtime path: {path}")
        continue
    if not isinstance(expected_size, int) or expected_size <= 0:
        errors.append(f"manifest path {path} has invalid size {expected_size!r}")
        continue
    if not isinstance(expected_sha, str) or not re.fullmatch(r"[0-9a-f]{64}", expected_sha):
        errors.append(f"manifest path {path} has invalid sha256 {expected_sha!r}")
        continue

    url = file_url(path)
    result: dict[str, Any] = {
        "path": path,
        "url": url,
        "status": "",
        "curlCode": 1,
        "size": 0,
        "sha256": "",
        "expectedSize": expected_size,
        "expectedSha256": expected_sha,
        "cacheControl": "",
        "ok": False,
        "passed": False,
    }
    context = ssl._create_unverified_context() if insecure else None
    try:
        with urllib.request.urlopen(url, timeout=timeout, context=context) as response:
            data = response.read()
            result["status"] = str(response.status)
            result["curlCode"] = 0
            result["size"] = len(data)
            digest = hashlib.sha256(data).hexdigest()
            result["sha256"] = digest
            result["cacheControl"] = response.headers.get("Cache-Control", "")
    except urllib.error.HTTPError as exc:
        result["status"] = str(exc.code)
        result["curlCode"] = 22
        result["error"] = str(exc)
        try:
            data = exc.read()
            result["size"] = len(data)
            if data:
                result["sha256"] = hashlib.sha256(data).hexdigest()
        except Exception:
            pass
    except Exception as exc:
        result["error"] = str(exc)

    if result["status"] != "200":
        errors.append(f"{path} returned HTTP/status {result['status'] or 'fetch-error'} from {url}")
    elif result["size"] != expected_size:
        errors.append(f"{path} size mismatch: expected {expected_size}, got {result['size']}")
    elif result["sha256"] != expected_sha:
        errors.append(f"{path} sha256 mismatch: expected {expected_sha}, got {result['sha256']}")
    else:
        result["ok"] = True
        result["passed"] = True
        artifact["verifiedCount"] += 1

    artifact["results"].append(result)

artifact["checked"] = len(files) > 0
artifact["failureCount"] = len(errors)
artifact["failures"] = errors[:50]
artifact["durationSeconds"] = round(time.time() - started_at, 3)
write_artifact(artifact)

if errors:
    for error in errors[:50]:
        print(error, file=sys.stderr)
    if len(errors) > 50:
        print(f"... {len(errors) - 50} more deep-manifest failures omitted", file=sys.stderr)
    raise SystemExit(1)
PY
	deep_manifest_rc=$?
	set -e
	if [[ -f "$DEEP_MANIFEST_RESULTS_JSON" ]]; then
		read -r DEEP_MANIFEST_CHECKED DEEP_MANIFEST_FILE_COUNT DEEP_MANIFEST_VERIFIED_COUNT DEEP_MANIFEST_FAILURE_COUNT < <(
			python3 - "$DEEP_MANIFEST_RESULTS_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)
print(
    "true" if data.get("checked") is True else "false",
    int(data.get("fileCount") or 0),
    int(data.get("verifiedCount") or 0),
    int(data.get("failureCount") or 0),
)
PY
		)
	fi
	if [[ "$deep_manifest_rc" -ne 0 ]]; then
		record_failure "deep build-manifest asset verification failed; see $DEEP_MANIFEST_RESULTS_JSON."
	fi
fi

set +e
python3 - "$ALLOW_HTTP" "$RESULTS_TSV" "$OUT_DIR/responses" "$DEEP_MANIFEST_RESULTS_JSON" "$CACHE_POLICY_RESULTS_JSON" <<'PY'
from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

allow_http = sys.argv[1] == "1"
results_path = Path(sys.argv[2])
responses_dir = Path(sys.argv[3])
deep_results_path = Path(sys.argv[4])
cache_policy_path = Path(sys.argv[5])


def safe_name(value: str) -> str:
    value = value.strip("/") or "root"
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", value)
    return value[:180] or "root"


def header_value(path: Path, name: str) -> str:
    wanted = name.lower()
    try:
        with path.open("rb") as handle:
            value = ""
            for raw in handle:
                line = raw.decode("iso-8859-1", "replace").rstrip("\r\n")
                if ":" not in line:
                    continue
                key, current = line.split(":", 1)
                if key.strip().lower() == wanted:
                    value = current.strip()
            return value
    except FileNotFoundError:
        return ""


def max_age(cache_control: str) -> int | None:
    match = re.search(r"(?:^|,)\s*max-age\s*=\s*(\d+)\s*(?:,|$)", cache_control, re.IGNORECASE)
    if not match:
        return None
    return int(match.group(1))


def expected_for(path: str) -> str | None:
    if path in {"index.html", "manifest.webmanifest", "voidscape-web-client.js"}:
        return "no-cache"
    if path.startswith("Cache/"):
        return "public, max-age=31536000, immutable"
    return None


def policy_ok(path: str, cache_control: str) -> tuple[bool, str]:
    expected = expected_for(path)
    if expected is None:
        return True, ""
    actual = cache_control.lower()
    if expected == "no-cache":
        return ("no-cache" in actual, expected)
    age = max_age(cache_control)
    ok = "public" in actual and "immutable" in actual and age is not None and age >= 31536000
    return ok, expected


artifact: dict[str, Any] = {
    "checked": False,
    "skipped": allow_http,
    "skipReason": "--allow-http local fixture" if allow_http else "",
    "failureCount": 0,
    "failures": [],
    "results": [],
}

if allow_http:
    cache_policy_path.write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
    raise SystemExit(0)

records: dict[str, dict[str, Any]] = {}

if results_path.is_file():
    for line in results_path.read_text(encoding="utf-8").splitlines():
        if not line:
            continue
        kind, path, url, status, curl_code, size, content_type, note = (line.split("\t") + [""] * 8)[:8]
        expected = expected_for(path)
        if expected is None:
            continue
        headers_path = responses_dir / f"{safe_name(path)}.headers"
        records[path] = {
            "path": path,
            "url": url,
            "source": "required",
            "status": status,
            "expected": expected,
            "cacheControl": header_value(headers_path, "Cache-Control"),
        }

if deep_results_path.is_file():
    try:
        deep = json.loads(deep_results_path.read_text(encoding="utf-8"))
    except Exception as exc:
        artifact["failures"].append(f"could not read deep-manifest results for cache policy: {exc}")
    else:
        results = deep.get("results")
        if isinstance(results, list):
            for entry in results:
                if not isinstance(entry, dict):
                    continue
                path = str(entry.get("path") or "")
                expected = expected_for(path)
                if expected is None:
                    continue
                records[path] = {
                    "path": path,
                    "url": str(entry.get("url") or ""),
                    "source": "deep-manifest",
                    "status": str(entry.get("status") or ""),
                    "expected": expected,
                    "cacheControl": str(entry.get("cacheControl") or ""),
                }

required = ("index.html", "manifest.webmanifest", "voidscape-web-client.js", "Cache/MD5.SUM")
for path in required:
    if path not in records:
        records[path] = {
            "path": path,
            "url": "",
            "source": "missing",
            "status": "",
            "expected": expected_for(path),
            "cacheControl": "",
        }

for path in sorted(records):
    row = records[path]
    ok, expected = policy_ok(path, str(row.get("cacheControl") or ""))
    row["passed"] = ok
    row["ok"] = ok
    row["expected"] = expected
    if not ok:
        artifact["failures"].append(
            f"{path} Cache-Control should include {expected!r}, got {row.get('cacheControl')!r}"
        )
    artifact["results"].append(row)

artifact["checked"] = True
artifact["failureCount"] = len(artifact["failures"])
cache_policy_path.write_text(json.dumps(artifact, indent=2) + "\n", encoding="utf-8")
if artifact["failures"]:
    for failure in artifact["failures"][:50]:
        print(failure, file=sys.stderr)
    if len(artifact["failures"]) > 50:
        print(f"... {len(artifact['failures']) - 50} more cache-policy failures omitted", file=sys.stderr)
    raise SystemExit(1)
PY
cache_policy_rc=$?
set -e
if [[ -f "$CACHE_POLICY_RESULTS_JSON" ]]; then
	read -r CACHE_POLICY_CHECKED CACHE_POLICY_FAILURE_COUNT < <(
		python3 - "$CACHE_POLICY_RESULTS_JSON" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    data = json.load(handle)
print("true" if data.get("checked") is True else "false", int(data.get("failureCount") or 0))
PY
	)
fi
if [[ "$cache_policy_rc" -ne 0 ]]; then
	record_failure "production Cache-Control policy verification failed; see $CACHE_POLICY_RESULTS_JSON."
fi

dangerous_names=(
	"accounts.txt"
	"credentials.txt"
	"uid.dat"
	"ip.txt"
	"port.txt"
	"hideIp.txt"
	"config.txt"
	"client.properties"
	"discord_inuse.txt"
	"launcherSettings.conf"
	"voidscapeLauncher.properties"
)

for name in "${dangerous_names[@]}"; do
	for relative in "$name" "Cache/$name"; do
		fetch_path "$relative" "forbidden" "runtime cache file"
		if [[ "$FETCH_CURL_CODE" -ne 0 ]]; then
			record_failure "could not verify forbidden path $relative at $FETCH_URL (curl exit $FETCH_CURL_CODE)."
			continue
		fi
		if [[ "$FETCH_STATUS" =~ ^4 ]]; then
			continue
		fi
		if [[ "$FETCH_STATUS" == "200" ]]; then
			if [[ -n "$INDEX_SHA" && "$(sha256_file "$FETCH_BODY")" == "$INDEX_SHA" ]]; then
				continue
			fi
			record_failure "forbidden runtime path is publicly served: $relative ($FETCH_URL)."
			continue
		fi
		record_failure "forbidden runtime path $relative returned unexpected HTTP $FETCH_STATUS at $FETCH_URL."
	done
done

debug_paths=("voidscape-web-client.js.map" "voidscape-web-client.js.teavmdbg")
for relative in "${debug_paths[@]}"; do
	fetch_path "$relative" "forbidden" "TeaVM debug artifact"
	if [[ "$FETCH_CURL_CODE" -ne 0 ]]; then
		record_failure "could not verify debug path $relative at $FETCH_URL (curl exit $FETCH_CURL_CODE)."
		continue
	fi
	if [[ "$FETCH_STATUS" =~ ^4 ]]; then
		continue
	fi
	if [[ "$FETCH_STATUS" == "200" ]]; then
		if [[ -n "$INDEX_SHA" && "$(sha256_file "$FETCH_BODY")" == "$INDEX_SHA" ]]; then
			continue
		fi
		if [[ "$ALLOW_DEBUG" -eq 1 ]]; then
			record_warning "TeaVM debug artifact is publicly served because --allow-debug was passed: $relative"
		else
			record_failure "TeaVM debug artifact is publicly served: $relative ($FETCH_URL)."
		fi
		continue
	fi
	record_failure "debug path $relative returned unexpected HTTP $FETCH_STATUS at $FETCH_URL."
done

if [[ "${#FAILURES[@]}" -eq 0 && "$RUN_SMOKE" -eq 1 ]]; then
	smoke_args=(--base-url "$BASE_URL" --user "$AUTH_USER" --pass "$AUTH_PASS" --out "$OUT_DIR/smoke")
	if [[ -n "$WS_URL" ]]; then
		smoke_args+=(--ws "$WS_URL")
	fi
	if [[ -n "$PORTAL_URL" ]]; then
		smoke_args+=(--portal "$PORTAL_URL")
	fi
	if [[ -n "$PORTAL_ACCOUNT_URL" ]]; then
		smoke_args+=(--portal-account-url "$PORTAL_ACCOUNT_URL")
	fi
	if [[ -n "$PORTAL_RECOVERY_URL" ]]; then
		smoke_args+=(--portal-recovery-url "$PORTAL_RECOVERY_URL")
	fi
	if [[ "$INSECURE" -eq 1 ]]; then
		smoke_args+=(--ignore-https-errors)
	fi
	if [[ -n "$CHROME_PATH" ]]; then
		smoke_args+=(--chrome "$CHROME_PATH")
	fi
	if [[ -n "$PLAYWRIGHT_CORE_DIR" ]]; then
		smoke_args+=(--playwright-core "$PLAYWRIGHT_CORE_DIR")
	fi
	SMOKE_RAN="true"
	if "$ROOT/scripts/smoke-web-teavm-iphone.sh" "${smoke_args[@]}"; then
		SMOKE_PASSED="true"
		if [[ ! -f "$SMOKE_SUMMARY" ]]; then
			SMOKE_PASSED="false"
			record_failure "deployed Chrome iPhone-emulation smoke did not write summary artifact: $SMOKE_SUMMARY"
		fi
	else
		record_failure "deployed Chrome iPhone-emulation smoke failed."
	fi
elif [[ "$RUN_SMOKE" -eq 1 ]]; then
	record_warning "skipping deployed smoke because static deployment checks already failed."
fi

if [[ "${#FAILURES[@]}" -gt 0 ]]; then
	for failure in "${FAILURES[@]}"; do
		printf '%s\n' "$failure" >> "$FAILURES_FILE"
	done
fi
if [[ "${#WARNINGS[@]}" -gt 0 ]]; then
	for warning in "${WARNINGS[@]}"; do
		printf '%s\n' "$warning" >> "$WARNINGS_FILE"
	done
fi

python3 - "$BASE_URL" "$WS_URL" "$ALLOW_HTTP" "$INSECURE" "$ALLOW_DEBUG" "$ALLOW_INSECURE_WS" "$RUN_SMOKE" "$SMOKE_RAN" "$SMOKE_PASSED" "$SMOKE_ARTIFACT" "$SMOKE_SUMMARY" "$PORTAL_URL" "$PORTAL_ACCOUNT_URL" "$PORTAL_RECOVERY_URL" "$BUILD_MANIFEST_SHA" "$EXPECTED_BUILD_MANIFEST" "$EXPECTED_BUILD_MANIFEST_SHA" "$BUILD_MANIFEST_MATCHES_EXPECTED" "$DEEP_MANIFEST" "$DEEP_MANIFEST_CHECKED" "$DEEP_MANIFEST_FILE_COUNT" "$DEEP_MANIFEST_VERIFIED_COUNT" "$DEEP_MANIFEST_FAILURE_COUNT" "$DEEP_MANIFEST_RESULTS_JSON" "$CACHE_POLICY_CHECKED" "$CACHE_POLICY_FAILURE_COUNT" "$CACHE_POLICY_RESULTS_JSON" "$RESULTS_TSV" "$FAILURES_FILE" "$WARNINGS_FILE" "$SUMMARY_JSON" <<'PY'
import json
import sys

(
    base_url,
    ws_url,
    allow_http,
    insecure,
    allow_debug,
    allow_insecure_ws,
    run_smoke,
    smoke_ran,
    smoke_passed,
    smoke_artifact,
    smoke_summary,
    portal_url,
    portal_account_url,
    portal_recovery_url,
    build_manifest_sha,
    expected_build_manifest,
    expected_build_manifest_sha,
    build_manifest_matches_expected,
    deep_manifest_requested,
    deep_manifest_checked,
    deep_manifest_file_count,
    deep_manifest_verified_count,
    deep_manifest_failure_count,
    deep_manifest_results_json,
    cache_policy_checked,
    cache_policy_failure_count,
    cache_policy_results_json,
    results_path,
    failures_path,
    warnings_path,
    summary_path,
) = sys.argv[1:]
results = []
with open(results_path, "r", encoding="utf-8") as handle:
    for line in handle:
        line = line.rstrip("\n")
        if not line:
            continue
        kind, path, url, status, curl_code, size, content_type, note = (line.split("\t") + [""] * 8)[:8]
        results.append({
            "kind": kind,
            "path": path,
            "url": url,
            "status": status,
            "curlCode": int(curl_code),
            "size": int(size),
            "contentType": content_type,
            "note": note
        })

def lines(path):
    with open(path, "r", encoding="utf-8") as handle:
        return [line.rstrip("\n") for line in handle if line.rstrip("\n")]

summary = {
    "baseUrl": base_url,
    "wsUrl": ws_url,
    "allowHttp": allow_http == "1",
    "insecureTls": insecure == "1",
    "allowDebug": allow_debug == "1",
    "allowInsecureWs": allow_insecure_ws == "1",
    "portalUrl": portal_url,
    "portalAccountUrl": portal_account_url,
    "portalRecoveryUrl": portal_recovery_url,
    "buildManifestSha256": build_manifest_sha,
    "expectedBuildManifest": expected_build_manifest,
    "expectedBuildManifestSha256": expected_build_manifest_sha,
    "buildManifestMatchesExpected": (
        None if not build_manifest_matches_expected else build_manifest_matches_expected == "true"
    ),
    "deepManifestRequested": deep_manifest_requested == "1",
    "deepManifestChecked": deep_manifest_checked == "true",
    "deepManifestFileCount": int(deep_manifest_file_count),
    "deepManifestVerifiedCount": int(deep_manifest_verified_count),
    "deepManifestFailureCount": int(deep_manifest_failure_count),
    "deepManifestResults": deep_manifest_results_json if deep_manifest_requested == "1" else "",
    "cachePolicyChecked": cache_policy_checked == "true",
    "cachePolicyFailureCount": int(cache_policy_failure_count),
    "cachePolicyResults": cache_policy_results_json,
    "smokeRequested": run_smoke == "1",
    "smokeRan": smoke_ran == "true",
    "smokePassed": smoke_passed == "true",
    "smokeArtifact": smoke_artifact if run_smoke == "1" else "",
    "smokeSummary": smoke_summary if run_smoke == "1" else "",
    "results": results,
    "failures": lines(failures_path),
    "warnings": lines(warnings_path)
}
with open(summary_path, "w", encoding="utf-8") as handle:
    json.dump(summary, handle, indent=2)
    handle.write("\n")
PY

echo
if [[ "${#FAILURES[@]}" -ne 0 ]]; then
	echo "TeaVM web deployment verification failed."
	echo "Artifacts: $OUT_DIR"
	exit 1
fi

echo "TeaVM web deployment verification passed."
if [[ "$RUN_SMOKE" -eq 0 ]]; then
	echo "Static checks passed. Re-run with --smoke once the game server and test credentials are ready."
fi
echo "Artifacts: $OUT_DIR"
