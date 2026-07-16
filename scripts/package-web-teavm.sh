#!/usr/bin/env bash
# Build and stage the TeaVM iPhone/web client as a static production web root.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT/dist/web-teavm"
SKIP_BUILD=0
INCLUDE_DEBUG=0
CLIENT_VERSION_OVERRIDE=""

usage() {
	cat <<'EOF'
Usage: scripts/package-web-teavm.sh [options]

Options:
  --output-dir DIR     Static web root to create. Default: dist/web-teavm.
  --skip-build         Reuse existing Web_Client_TeaVM/target/teavm output.
  --include-debug      Keep TeaVM source maps/debug files in the staged output.
  --client-version N   Build the web package with a temporary client protocol version.
  -h, --help           Show this help.

The packaged client defaults to:
  http://...  -> ws://<host>:43496/
  https://... -> wss://<host>:443/ unless ?port=... or ?ws=... is supplied.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--output-dir)
			OUTPUT_DIR="${2:-}"
			shift 2
			;;
		--skip-build)
			SKIP_BUILD=1
			shift
			;;
		--include-debug)
			INCLUDE_DEBUG=1
			shift
			;;
		--client-version)
			CLIENT_VERSION_OVERRIDE="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 1
			;;
	esac
done

if [[ -z "$OUTPUT_DIR" ]]; then
	echo "ERROR: --output-dir cannot be empty." >&2
	exit 1
fi
if [[ -n "$CLIENT_VERSION_OVERRIDE" && ! "$CLIENT_VERSION_OVERRIDE" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --client-version must be numeric." >&2
	exit 1
fi
if [[ "$SKIP_BUILD" -ne 0 && -n "$CLIENT_VERSION_OVERRIDE" ]]; then
	echo "ERROR: --client-version cannot be used with --skip-build." >&2
	exit 1
fi

forbidden_runtime_files=(
	"Cache/accounts.txt"
	"Cache/credentials.txt"
	"Cache/uid.dat"
	"Cache/ip.txt"
	"Cache/port.txt"
	"Cache/hideIp.txt"
	"Cache/config.txt"
	"Cache/client.properties"
	"Cache/discord_inuse.txt"
	"Cache/launcherSettings.conf"
	"Cache/voidscapeLauncher.properties"
)

assert_no_forbidden_files() {
	local root="$1"
	local found=0
	for relative in "${forbidden_runtime_files[@]}"; do
		if [[ -e "$root/$relative" ]]; then
			echo "ERROR: production web root contains runtime file: $relative" >&2
			found=1
		fi
	done
	if [[ "$found" -ne 0 ]]; then
		exit 1
	fi
}

cd "$ROOT"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
	if [[ -n "$CLIENT_VERSION_OVERRIDE" ]]; then
		scripts/build-web-teavm-spike.sh --client-version "$CLIENT_VERSION_OVERRIDE"
	else
		scripts/build-web-teavm-spike.sh
	fi
fi

TARGET="$ROOT/Web_Client_TeaVM/target/teavm"
if [[ ! -f "$TARGET/index.html" ]]; then
	echo "ERROR: missing TeaVM output at $TARGET/index.html." >&2
	exit 1
fi

assert_no_forbidden_files "$TARGET"

OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
if [[ "$OUTPUT_DIR" == "/" || "$OUTPUT_DIR" == "$ROOT" ]]; then
	echo "ERROR: refusing to replace unsafe output directory: $OUTPUT_DIR" >&2
	exit 1
fi
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

if command -v rsync >/dev/null 2>&1; then
	rsync_args=(-a)
	if [[ "$INCLUDE_DEBUG" -eq 0 ]]; then
		rsync_args+=(--exclude='*.map' --exclude='*.teavmdbg')
	fi
	rsync "${rsync_args[@]}" "$TARGET/" "$OUTPUT_DIR/"
else
	cp -R "$TARGET/." "$OUTPUT_DIR/"
	if [[ "$INCLUDE_DEBUG" -eq 0 ]]; then
		find "$OUTPUT_DIR" -type f \( -name '*.map' -o -name '*.teavmdbg' \) -delete
	fi
fi

assert_no_forbidden_files "$OUTPUT_DIR"

cat > "$OUTPUT_DIR/DEPLOYMENT.txt" <<'EOF'
Voidscape TeaVM web client

Serve this directory as a static web root over HTTPS.

Recommended production shape:
  https://<host>/                       -> this static directory
  wss://<host>/                         -> reverse proxy to the server ws_server_port

The client defaults to wss://<host>:443/ on HTTPS pages. Override when needed:
  index.html?mobile=1&host=<ws-host>&port=<ws-port>
  index.html?mobile=1&ws=wss://<host>/<path>

Explicit endpoint overrides are saved in browser storage so Home Screen launches
from index.html?mobile=1 keep using the intended game server. To clear the saved
endpoint for a tester, open:
  index.html?mobile=1&endpoint=reset

Named beta profiles isolate saved endpoint and portal settings for players who
run more than one account in separate tabs/windows:
  index.html?mobile=1&profile=main
  index.html?mobile=1&profile=alt1
Profiles do not store game credentials. endpoint=reset and resetPortal=1 clear
only the active profile's saved web settings.

The mobile login account/recovery buttons default to the same web root:
  Create Account  -> /portal?auth=login
  Recover account -> /portal?auth=recovery
Override when needed:
  index.html?mobile=1&portal=https://<portal-host>/
  index.html?mobile=1&portalAccountUrl=https://<portal-host>/portal%3Fauth%3Dlogin&portalRecoveryUrl=https://<portal-host>/portal%3Fauth%3Drecovery
Portal overrides are saved for Home Screen launches. To clear stale tester
portal URLs, open:
  index.html?mobile=1&resetPortal=1

For real iPhone troubleshooting, append diag=1 or debug=1 once:
  index.html?mobile=1&diag=1
That saves diagnostics mode for the manifest/Home Screen launch. Final QA
should then launch from index.html?mobile=1 without diag=1/debug=1 and paste
two diagnostics blobs into the generated QA report:
  1. blocking-dialog diagnostics while the welcome/wilderness modal is open
  2. final diagnostics after tapping All/Chat/Quest/Private, opening every top
     HUD panel, swipe-scrolling one opened top panel, closing a panel,
     background/resume, post-resume tap-to-move or chat, and portrait/landscape rotation
The diagnostics copy button copies JSON from Safari. If Safari blocks clipboard
access, it shows a selectable JSON field instead. The final report validator
requires standalone, diagnostics.source=stored, lifecycle resume counters,
post-resume gameplay proof, blocking-dialog proof, custom HUD uiHistory proof, scrollHistory proof, and
current mobile overlay-control geometry. Paste verify-web-teavm-deployment.sh --smoke
summary.json with smokeRan=true, smokePassed=true, cachePolicyChecked=true,
cachePolicyFailureCount=0, allowHttp=false, insecureTls=false,
allowDebug=false, and allowInsecureWs=false into the report's Deployment Verification section
for release.

Resource modes are web-only canvas-upload modes. They do not pause networking,
input polling, diagnostics, or the shared Java game loop:
  index.html?mobile=1&resource=gfx-off
resource=gfx-off shows a GFX off / Resume overlay and stops normal canvas
uploads after one transition frame. Use resource=normal to return to normal
rendering.

Before uploading, run the aggregate local iPhone web preflight:
  scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir dist/web-teavm
If this package was built with --client-version N, the local server's
client_version must also be N and the preflight must build with the same
override instead of --no-build:
  scripts/check-web-teavm-iphone-release.sh --client-version N --with-simulator --package-dir dist/web-teavm
That simulator-inclusive preflight must include current simulator-run.json,
simulator-screenshot-checks.json, and simulator-http-checks.json artifacts
before the final audit can count it for release.

Or validate only the same-host HTTPS/WSS shape locally:
  scripts/smoke-web-teavm-iphone-https-wss.sh --no-build

After uploading, verify the hosted static root before sharing it:
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --portal https://<portal-host>/ --smoke
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest dist/web-teavm/voidscape-web-build.json --deep-manifest --ws wss://<host>/<path> --portal https://<portal-host>/ --smoke
The verifier rejects stale uploads missing copied diagnostics, profile storage,
resource-mode controls, controlsHistory, custom HUD uiHistory, scrollHistory,
postResumeProof, mobile endpoint state, iPhone/PWA metadata, icons, or
runtime/debug-file exclusions. With --expected-build-manifest it also proves
the hosted voidscape-web-build.json matches this package, and with
--deep-manifest it proves every packaged static asset is served with the
expected size and SHA-256.

Do not serve local runtime cache files such as accounts.txt, credentials.txt,
uid.dat, ip.txt, port.txt, or config.txt from this directory.
EOF

cat <<EOF
TeaVM web client package created:

  $OUTPUT_DIR

Verify after upload:
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest $OUTPUT_DIR/voidscape-web-build.json --deep-manifest
  The verifier rejects stale uploads missing copied diagnostics, profile storage,
  resource-mode controls, controlsHistory, custom HUD uiHistory, scrollHistory,
  postResumeProof, mobile endpoint state, iPhone/PWA metadata, icons, or
  runtime/debug-file exclusions, confirms the hosted build manifest matches
  the local package manifest when --expected-build-manifest is supplied, and
  verifies every manifest-listed asset when --deep-manifest is supplied.

If HTTPS and WSS share the same hostname, no query parameters are needed.
For a separate websocket endpoint, use:
  https://<host>/index.html?mobile=1&host=<ws-host>&port=<ws-port>
or:
  https://<host>/index.html?mobile=1&ws=wss://<ws-host>/<path>

Those endpoint overrides are saved for later Home Screen launches. Clear with:
  https://<host>/index.html?mobile=1&endpoint=reset

Use named beta profiles for multiple-account testing in separate tabs/windows:
  https://<host>/index.html?mobile=1&profile=main
  https://<host>/index.html?mobile=1&profile=alt1
Profiles isolate saved endpoint/portal settings only; they do not store game credentials.

Account/recovery portal handoff:
  https://<host>/index.html?mobile=1&portal=https://<portal-host>/
or:
  https://<host>/index.html?mobile=1&portalAccountUrl=https://<portal-host>/portal%3Fauth%3Dlogin&portalRecoveryUrl=https://<portal-host>/portal%3Fauth%3Drecovery

Those portal overrides are saved for later Home Screen launches. Clear with:
  https://<host>/index.html?mobile=1&resetPortal=1

Resource modes for AFK browser tabs:
  https://<host>/index.html?mobile=1&resource=gfx-off
This stops canvas uploads only; networking, input polling, diagnostics, and the
shared Java game loop keep running. Use the GFX off overlay's Resume button or
set resource=normal to return to normal rendering.

Real-device diagnostics:
  https://<host>/index.html?mobile=1&diag=1
Open that diagnostics URL once to save diagnostics mode, add/launch from Home
Screen using the manifest start URL, copy blocking-dialog diagnostics before
closing the welcome/wilderness modal, tap All/Chat/Quest/Private, open every top
HUD panel, swipe-scroll one opened top panel, close a panel, background and return
to the Home Screen app, tap-to-move or chat once after resume, rotate through portrait/landscape,
then copy final diagnostics so the report proves standalone,
diagnostics.source=stored, lifecycle resume, post-resume gameplay proof, custom HUD uiHistory, scrollHistory, and
dialog-safe overlay state. Paste verify-web-teavm-deployment.sh --smoke
summary.json with smokeRan=true, smokePassed=true, cachePolicyChecked=true,
cachePolicyFailureCount=0, allowHttp=false, insecureTls=false,
allowDebug=false, and allowInsecureWs=false into the report's Deployment Verification section
before final validation.

Chrome iPhone-emulation smoke after upload:
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest $OUTPUT_DIR/voidscape-web-build.json --deep-manifest --portal https://<portal-host>/ --smoke --user <qa-user> --pass <qa-pass>
  scripts/verify-web-teavm-deployment.sh https://<host>/ --expected-build-manifest $OUTPUT_DIR/voidscape-web-build.json --deep-manifest --ws wss://<host>/<path> --portal https://<portal-host>/ --smoke --user <qa-user> --pass <qa-pass>

Final release audit after the prerequisite-checked simulator-inclusive local preflight, hosted verifier, and physical iPhone QA report are filled:
  scripts/check-web-teavm-iphone-final-release.py --qa-report tmp/iphone-web-qa/iphone-safari-qa-report.md --local-preflight tmp/web-teavm-iphone-release-preflight/summary.json --package-dir $OUTPUT_DIR
That audit also checks that the local preflight prerequisites passed, the simulator artifact includes current simulator-run.json/screenshot/HTTP checks, and the package artifact matches this package directory.
For iPhone UI/control changes, run the local preflight with --simulator-video 90 and add --require-simulator-video to the final audit. That video preflight needs about 1.6 GB free by default so the recording does not run out of disk mid-capture. Use scripts/clean-web-teavm-iphone-artifacts.sh to dry-run generated-artifact cleanup.

Aggregate local iPhone web preflight before upload:
  scripts/check-web-teavm-iphone-release.sh --no-build --with-simulator --package-dir $OUTPUT_DIR
If this package was built with --client-version N, the local server's
client_version must also be N and the preflight must build with the same
override instead of --no-build:
  scripts/check-web-teavm-iphone-release.sh --client-version N --with-simulator --package-dir $OUTPUT_DIR
This must produce current simulator-run.json, simulator-screenshot-checks.json,
and simulator-http-checks.json in the simulator artifact directory for final audit.

Local same-host HTTPS/WSS smoke before upload:
  scripts/smoke-web-teavm-iphone-https-wss.sh --no-build
EOF

# Static web hosts must be able to read every packaged cache file. Preserve
# content hashes, but normalize modes so local cache permissions such as 0600
# do not become production 403s after upload.
find "$OUTPUT_DIR" -type d -exec chmod 755 {} +
find "$OUTPUT_DIR" -type f -exec chmod 644 {} +

python3 - "$OUTPUT_DIR" "$CLIENT_VERSION_OVERRIDE" "$ROOT/Client_Base/src/orsc/Config.java" <<'PY'
from __future__ import annotations

import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

root = Path(sys.argv[1])
client_version_override = sys.argv[2].strip()
config_file = Path(sys.argv[3])
manifest_name = "voidscape-web-build.json"

cache_digest = hashlib.sha256()
cache_file_count = 0
cache_root = root / "Cache"
if cache_root.exists():
    for path in sorted(cache_root.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        cache_digest.update(relative.encode("utf-8"))
        cache_digest.update(b"\0")
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                cache_digest.update(chunk)
        cache_digest.update(b"\0")
        cache_file_count += 1
cache_token = f"cache-{cache_digest.hexdigest()[:20]}" if cache_file_count else "cache-none"
index_file = root / "index.html"
if index_file.exists():
    text = index_file.read_text(encoding="utf-8")
    text = text.replace("__VOIDSCAPE_ASSET_TOKEN__", cache_token)
    index_file.write_text(text, encoding="utf-8")

files = []
source_mtimes = []
for path in sorted(root.rglob("*")):
    if not path.is_file():
        continue
    relative = path.relative_to(root).as_posix()
    if relative == manifest_name:
        continue
    stat = path.stat()
    if relative != "DEPLOYMENT.txt":
        source_mtimes.append(stat.st_mtime)
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    files.append(
        {
            "path": relative,
            "size": stat.st_size,
            "sha256": digest.hexdigest(),
        }
    )

generated_at = datetime.now(timezone.utc)
if source_mtimes:
    generated_at = datetime.fromtimestamp(max(source_mtimes), timezone.utc)

client_version = None
client_version_source = "unknown"
if client_version_override:
    client_version = int(client_version_override)
    client_version_source = "override"
else:
    match = re.search(r"public\s+static\s+final\s+int\s+CLIENT_VERSION\s*=\s*(\d+)\s*;", config_file.read_text(encoding="utf-8"))
    if match:
        client_version = int(match.group(1))
        client_version_source = "source"

payload = {
    "schemaVersion": 1,
    "name": "voidscape-teavm-web-client",
    "generatedAt": generated_at.strftime("%Y-%m-%dT%H:%M:%SZ"),
    "clientVersion": client_version,
    "clientVersionSource": client_version_source,
    "assetToken": cache_token,
    "fileCount": len(files),
    "files": files,
}
(root / manifest_name).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
PY
