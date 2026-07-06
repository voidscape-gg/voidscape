#!/usr/bin/env bash
# Package a production-like launch staging bundle.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT/dist/launch-staging"
GAME_HOST=""
GAME_PORT="43596"
WS_PORT="43496"
PORTAL_URL=""
WEB_URL=""
WS_URL=""
LAUNCH_AT="2026-07-11T18:00:00Z"
SERVER_PRESET="$ROOT/server/preservation.conf"
SKIP_BUILD=0
SKIP_WEB_BUILD=0
SKIP_ANDROID=0
ANDROID_RELEASE=0
RSYNC_TARGET=""

LAUNCHER_RESOURCE="$ROOT/PC_Launcher/src/main/resources/voidscape-launcher.properties"
LAUNCHER_BACKUP=""
LAUNCHER_EXISTED=0
LAUNCHER_TOUCHED=0
ANDROID_CONFIG="$ROOT/Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java"
ANDROID_BACKUP=""

usage() {
	cat <<'EOF'
Usage: scripts/package-launch-staging.sh --host HOST --portal-url URL --web-url URL [options]

Builds a staging bundle for the launch flow:
  - server/core.jar and server/plugins.jar
  - a production-like local.launch-staging.conf with command lockdown on
  - web/portal plus a launch-signup portal.env template
  - dist/web-teavm staged as play/
  - a staging-configured desktop launcher jar and sidecar properties
  - an Android APK optionally rebuilt with the staging host and portal URLs
  - verification/runbook files for scripts/verify-launch-staging.mjs

Required:
  --host HOST              Game server host for launcher/Android.
  --portal-url URL         Portal base URL, e.g. https://staging.voidscape.gg/.
  --web-url URL            Web client URL, e.g. https://staging.voidscape.gg/play/.

Options:
  --port PORT              Game TCP port. Default: 43596.
  --ws-port PORT           Game WebSocket port. Default: 43496.
  --ws-url URL             Public WSS URL for /play verification.
                           Default: <portal-url>/play/ws/ converted to wss://.
  --launch-at ISO          Launch timestamp. Default: 2026-07-11T18:00:00Z.
  --server-preset FILE     Source config for generated local.launch-staging.conf.
                           Default: server/preservation.conf.
  --output-dir DIR         Bundle output directory. Default: dist/launch-staging.
  --skip-build             Reuse existing server/client/launcher jars.
  --skip-web-build         Reuse existing Web_Client_TeaVM/target/teavm.
  --skip-android           Do not rebuild/copy the Android APK.
  --android-release        Build/copy a signed release APK instead of debug.
                           Requires Android signing config.
  --rsync-target TARGET    Optional rsync destination, e.g. user@host:/opt/voidscape-staging.
  -h, --help               Show this help.

After syncing the bundle, deploy its files to the host-specific paths in
DEPLOYMENT.md, restart services, then run the VERIFY-STAGING.sh command.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--host)
			GAME_HOST="${2:-}"
			shift 2
			;;
		--port)
			GAME_PORT="${2:-}"
			shift 2
			;;
		--ws-port)
			WS_PORT="${2:-}"
			shift 2
			;;
		--portal-url)
			PORTAL_URL="${2:-}"
			shift 2
			;;
		--web-url)
			WEB_URL="${2:-}"
			shift 2
			;;
		--ws-url)
			WS_URL="${2:-}"
			shift 2
			;;
		--launch-at)
			LAUNCH_AT="${2:-}"
			shift 2
			;;
		--server-preset)
			SERVER_PRESET="${2:-}"
			shift 2
			;;
		--output-dir)
			OUTPUT_DIR="${2:-}"
			shift 2
			;;
		--skip-build)
			SKIP_BUILD=1
			shift
			;;
		--skip-web-build)
			SKIP_WEB_BUILD=1
			shift
			;;
		--skip-android)
			SKIP_ANDROID=1
			shift
			;;
		--android-release)
			ANDROID_RELEASE=1
			shift
			;;
		--rsync-target)
			RSYNC_TARGET="${2:-}"
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

if [[ -z "$GAME_HOST" ]]; then
	echo "ERROR: --host is required." >&2
	exit 1
fi
if [[ -z "$PORTAL_URL" ]]; then
	echo "ERROR: --portal-url is required." >&2
	exit 1
fi
if [[ -z "$WEB_URL" ]]; then
	echo "ERROR: --web-url is required." >&2
	exit 1
fi
if ! [[ "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "ERROR: --port must be a number from 1 to 65535." >&2
	exit 1
fi
if ! [[ "$WS_PORT" =~ ^[0-9]+$ ]] || (( WS_PORT < 1 || WS_PORT > 65535 )); then
	echo "ERROR: --ws-port must be a number from 1 to 65535." >&2
	exit 1
fi
if [[ ! -f "$SERVER_PRESET" ]]; then
	echo "ERROR: server preset not found: $SERVER_PRESET" >&2
	exit 1
fi

trim_trailing_slash() {
	local value="$1"
	while [[ "$value" == */ ]]; do
		value="${value%/}"
	done
	printf '%s' "$value"
}

PORTAL_URL="$(trim_trailing_slash "$PORTAL_URL")"
WEB_URL="$(trim_trailing_slash "$WEB_URL")/"
if [[ -z "$WS_URL" ]]; then
	WS_URL="${PORTAL_URL}/play/ws/"
	WS_URL="${WS_URL/http:\/\//ws://}"
	WS_URL="${WS_URL/https:\/\//wss://}"
fi

sha256_file() {
	shasum -a 256 "$1" | awk '{print $1}'
}

client_version() {
	awk '/CLIENT_VERSION[[:space:]]*=/ {
		gsub(/[^0-9]/, "", $0);
		print $0;
		exit
	}' "$ROOT/Client_Base/src/orsc/Config.java"
}

restore_sources() {
	if [[ "$LAUNCHER_TOUCHED" -eq 1 ]]; then
		local launcher_backup="$LAUNCHER_BACKUP"
		LAUNCHER_TOUCHED=0
		LAUNCHER_BACKUP=""
		if [[ "$LAUNCHER_EXISTED" -eq 1 && -f "$launcher_backup" ]]; then
			cp "$launcher_backup" "$LAUNCHER_RESOURCE"
		else
			rm -f "$LAUNCHER_RESOURCE"
		fi
		if [[ -n "$launcher_backup" ]]; then
			rm -f "$launcher_backup"
		fi
	fi
	if [[ -n "$ANDROID_BACKUP" ]]; then
		local android_backup="$ANDROID_BACKUP"
		ANDROID_BACKUP=""
		if [[ -f "$android_backup" ]]; then
			cp "$android_backup" "$ANDROID_CONFIG"
		fi
		rm -f "$android_backup"
	fi
}
trap restore_sources EXIT

write_property() {
	printf '%s=%s\n' "$1" "$2"
}

bundle_copy_dir() {
	local source="$1"
	local target="$2"
	rm -rf "$target"
	mkdir -p "$(dirname "$target")"
	if command -v rsync >/dev/null 2>&1; then
		rsync -a --delete "$source"/ "$target"/
	else
		mkdir -p "$target"
		cp -R "$source"/. "$target"/
	fi
}

cd "$ROOT"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
	scripts/build.sh
fi

OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
if [[ "$OUTPUT_DIR" == "/" || "$OUTPUT_DIR" == "$ROOT" ]]; then
	echo "ERROR: refusing to replace unsafe output directory: $OUTPUT_DIR" >&2
	exit 1
fi
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"/{server,portal,launcher,android,logs}

VERSION="$(client_version)"
COMMIT="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
FULL_COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "==> Packaging server artifacts"
cp "$ROOT/server/core.jar" "$OUTPUT_DIR/server/core.jar"
cp "$ROOT/server/plugins.jar" "$OUTPUT_DIR/server/plugins.jar"
bundle_copy_dir "$ROOT/server/database" "$OUTPUT_DIR/server/database"

python3 - "$SERVER_PRESET" "$OUTPUT_DIR/server/local.launch-staging.conf" "$VERSION" "$GAME_PORT" "$WS_PORT" <<'PY'
import re
import sys
from pathlib import Path

source, target, client_version, server_port, ws_port = sys.argv[1:]
text = Path(source).read_text()
updates = {
    "server_name": "Voidscape",
    "server_name_welcome": "Voidscape",
    "welcome_text": "Welcome to Voidscape.",
    "client_version": client_version,
    "enforce_custom_client_version": "true",
    "server_port": server_port,
    "ws_server_port": ws_port,
    "want_feature_websockets": "true",
    "member_world": "true",
    "want_pcap_logging": "false",
    "production_command_lockdown": "true",
    "want_packet_register": "false",
}

for key, value in updates.items():
    pattern = re.compile(rf"(^\s*{re.escape(key)}\s*:\s*)[^#\n]*(.*)$", re.MULTILINE)
    if pattern.search(text):
        def repl(match, v=value):
            suffix = match.group(2)
            if suffix and not suffix.startswith(" "):
                suffix = " " + suffix
            return f"{match.group(1)}{v}{suffix}"
        text = pattern.sub(repl, text, count=1)
    else:
        text += f"\n\t{key}: {value}\n"

Path(target).write_text(text)
PY

echo "==> Packaging portal"
bundle_copy_dir "$ROOT/web/portal" "$OUTPUT_DIR/portal/web-portal"
cat > "$OUTPUT_DIR/portal/portal.env.staging" <<EOF
PORT=8788
PORTAL_BIND_HOST=127.0.0.1
PORTAL_PUBLIC_MODE=1
PORTAL_LAUNCH_SIGNUP_MODE=1
PORTAL_LAUNCH_AT=$LAUNCH_AT
PORTAL_WEB_CLIENT_URL=$WEB_URL
PORTAL_DATA_DIR=/var/lib/voidscape-portal
PORTAL_OPENRSC_DB=/opt/voidscape/server/inc/sqlite/voidscape.db
PORTAL_INTEGRITY_SNAPSHOT=/var/lib/voidscape-portal/integrity-summary.json
PORTAL_INTEGRITY_FINDINGS=/var/lib/voidscape-portal/integrity-findings.json
PORTAL_PUBLIC_ORIGIN=$PORTAL_URL
PORTAL_SIGNUP_IP_DAILY_LIMIT=10
PORTAL_STARTER_IP_DAILY_LIMIT=10
PORTAL_ABUSE_HASH_SALT=CHANGE_ME_STABLE_PRIVATE_SECRET
# PORTAL_ADMIN_TOKEN=CHANGE_ME_LONG_RANDOM_SECRET
# PORTAL_ANDROID_APK overrides the APK served by /downloads/android-apk.
# Use the packaged release APK path for production if direct Android download is live.
# PORTAL_ANDROID_APK=/opt/voidscape/downloads/voidscape-staging-release.apk
# PORTAL_GOOGLE_CLIENT_ID=
# PORTAL_GOOGLE_JWKS_URL=https://www.googleapis.com/oauth2/v3/certs
# Resend email delivery is optional for staging; enable dry-run for smoke tests
# or set a real API key once DNS is verified.
# PORTAL_EMAIL_PROVIDER=resend
# PORTAL_EMAIL_DRY_RUN=1
# PORTAL_RESEND_API_KEY=
# PORTAL_EMAIL_FROM="Voidscape <launch@voidscape.gg>"
# PORTAL_EMAIL_REPLY_TO=support@voidscape.gg
# PORTAL_REQUIRE_EMAIL=1
# PORTAL_TURNSTILE_SITE_KEY=
# PORTAL_TURNSTILE_SECRET=
# PORTAL_PAYMENT_PROVIDER=
# PORTAL_PAYMENT_WEBHOOK_SECRET=
EOF

echo "==> Packaging TeaVM /play"
if [[ "$SKIP_WEB_BUILD" -eq 1 ]]; then
	scripts/package-web-teavm.sh --skip-build --output-dir "$OUTPUT_DIR/play"
else
	scripts/package-web-teavm.sh --output-dir "$OUTPUT_DIR/play"
fi

echo "==> Packaging desktop launcher for staging"
if [[ -f "$LAUNCHER_RESOURCE" ]]; then
	LAUNCHER_EXISTED=1
	LAUNCHER_BACKUP="$(mktemp)"
	cp "$LAUNCHER_RESOURCE" "$LAUNCHER_BACKUP"
fi
LAUNCHER_TOUCHED=1
mkdir -p "$(dirname "$LAUNCHER_RESOURCE")"
{
	write_property "voidscape.serverHost" "$GAME_HOST"
	write_property "voidscape.serverPort" "$GAME_PORT"
	write_property "voidscape.portalUrl" "$PORTAL_URL"
	write_property "voidscape.websiteUrl" "$PORTAL_URL"
	write_property "voidscape.manifestUrl" "$PORTAL_URL/api/launcher/manifest.properties"
} > "$LAUNCHER_RESOURCE"
(cd "$ROOT/PC_Launcher" && ant compile)
cp "$ROOT/PC_Launcher/OpenRSC.jar" "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar"
cp "$LAUNCHER_RESOURCE" "$OUTPUT_DIR/launcher/voidscape-launcher.properties"
python3 - "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar" "$OUTPUT_DIR/launcher/voidscape-launcher.properties" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL" <<'PY'
import sys
import zipfile
from pathlib import Path

jar_path, sidecar_path, host, port, portal = sys.argv[1:]
expected = {
    "voidscape.serverHost": host,
    "voidscape.serverPort": port,
    "voidscape.portalUrl": portal,
    "voidscape.websiteUrl": portal,
    "voidscape.manifestUrl": f"{portal}/api/launcher/manifest.properties",
}

def parse_properties(text):
    values = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in stripped:
            continue
        key, value = stripped.split("=", 1)
        values[key.strip()] = value.strip()
    return values

with zipfile.ZipFile(jar_path) as jar:
    try:
        packaged_text = jar.read("data/voidscape-launcher.properties").decode("utf-8")
    except KeyError:
        raise SystemExit("launcher jar is missing data/voidscape-launcher.properties")

sidecar_text = Path(sidecar_path).read_text(encoding="utf-8")
for label, text in (("packaged", packaged_text), ("sidecar", sidecar_text)):
    values = parse_properties(text)
    for key, value in expected.items():
        if values.get(key) != value:
            raise SystemExit(f"{label} launcher property {key}={values.get(key)!r}, expected {value!r}")

Path(sidecar_path).with_name("voidscape-launcher.packaged.properties").write_text(packaged_text, encoding="utf-8")
PY
restore_sources
trap restore_sources EXIT

if [[ "$SKIP_ANDROID" -eq 0 ]]; then
	echo "==> Packaging Android APK for staging host"
	ANDROID_BACKUP="$(mktemp)"
	cp "$ANDROID_CONFIG" "$ANDROID_BACKUP"
	python3 - "$ANDROID_CONFIG" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL/#account" "$PORTAL_URL/#security" <<'PY'
import re
import sys
from pathlib import Path

path, host, port, account_url, recovery_url = sys.argv[1:]
text = Path(path).read_text()
replacements = {
    "VOIDSCAPE_PUBLIC_HOST": host,
    "VOIDSCAPE_DEFAULT_PORT": port,
    "VOIDSCAPE_PORTAL_ACCOUNT_URL": account_url,
    "VOIDSCAPE_PORTAL_RECOVERY_URL": recovery_url,
}
for key, value in replacements.items():
    text = re.sub(
        rf'(public static final String {re.escape(key)} = ")[^"]*(";)'
        , rf'\g<1>{value}\2'
        , text
        , count=1
    )
Path(path).write_text(text)
PY
	if [[ "$ANDROID_RELEASE" -eq 1 ]]; then
		scripts/build-android.sh --release
		ANDROID_APK="$ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/release/voidscape.apk"
		ANDROID_LABEL="release"
	else
		scripts/build-android.sh --debug
		ANDROID_APK="$ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
		ANDROID_LABEL="debug"
	fi
	cp "$ANDROID_APK" "$OUTPUT_DIR/android/voidscape-staging-$ANDROID_LABEL.apk"
	cp "$ANDROID_APK" "$OUTPUT_DIR/android/voidscape-staging.apk"
	if [[ -f "$ANDROID_APK.json" ]]; then
		cp "$ANDROID_APK.json" "$OUTPUT_DIR/android/voidscape-staging-$ANDROID_LABEL.apk.json"
		cp "$ANDROID_APK.json" "$OUTPUT_DIR/android/voidscape-staging.apk.json"
	fi
	restore_sources
	trap restore_sources EXIT
else
	echo "==> Skipping Android APK packaging"
	cat > "$OUTPUT_DIR/android/README.md" <<EOF
Android packaging was skipped.

For release/staging proof, rerun:
  scripts/package-launch-staging.sh --host $GAME_HOST --portal-url $PORTAL_URL --web-url $WEB_URL

The package script temporarily rebuilds Android with VOIDSCAPE_PUBLIC_HOST=$GAME_HOST,
then restores the source tree after the APK is copied.
EOF
fi

cat > "$OUTPUT_DIR/VERIFY-STAGING.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
BUNDLE_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="\${VOIDSCAPE_REPO_ROOT:-\$(pwd)}"
EXTRA_ARGS=()
"\$REPO_ROOT/scripts/verify-launch-staging.mjs" \\
  --portal-url "$PORTAL_URL/" \\
  --web-url "$WEB_URL" \\
  --ws "$WS_URL" \\
  --expected-build-manifest "\$BUNDLE_DIR/play/voidscape-web-build.json" \\
  --server-config "\$BUNDLE_DIR/server/local.launch-staging.conf" \\
  --expected-client-version "$VERSION" \\
  --out "\$BUNDLE_DIR/logs/verify-staging-\$(date -u +%Y%m%dT%H%M%SZ)" \\
  "\${EXTRA_ARGS[@]}" \\
  --run-signup
EOF
chmod +x "$OUTPUT_DIR/VERIFY-STAGING.sh"

cat > "$OUTPUT_DIR/DEPLOYMENT.md" <<EOF
# Voidscape Launch Staging Bundle

Created: $CREATED_AT
Commit: $FULL_COMMIT
Client version: $VERSION

## Target

- Portal: $PORTAL_URL/
- Web client: $WEB_URL
- Game TCP: $GAME_HOST:$GAME_PORT
- Game WSS: $WS_URL

## Files

- \`server/core.jar\` and \`server/plugins.jar\`
- \`server/database/\`
- \`server/local.launch-staging.conf\`
- \`portal/web-portal/\`
- \`portal/portal.env.staging\`
- \`play/\`
- \`launcher/VoidscapeLauncher-staging.jar\`
- \`android/voidscape-staging.apk\` when Android packaging was enabled

## Host Placement

Suggested single-host layout:

\`\`\`text
/opt/voidscape/server/core.jar
/opt/voidscape/server/plugins.jar
/opt/voidscape/server/database/
/opt/voidscape/server/local.conf
/opt/voidscape/web/portal/
/opt/voidscape/web/play/
/var/lib/voidscape-portal/
\`\`\`

Copy \`server/local.launch-staging.conf\` to the deployed server's \`local.conf\`
or merge its release-critical values into the real private config:

- \`client_version: $VERSION\`
- \`enforce_custom_client_version: true\`
- \`production_command_lockdown: true\`
- \`want_packet_register: false\`
- \`want_pcap_logging: false\`
- \`member_world: true\`
- \`want_feature_websockets: true\`

Copy \`portal/portal.env.staging\` to the host, replace the two
\`CHANGE_ME\` values, and keep \`PORTAL_GOOGLE_CLIENT_ID\` unset unless Google
is intentionally being tested. Enable the Resend email variables only after the
sender domain is verified, or use \`PORTAL_EMAIL_DRY_RUN=1\` for staging smoke.

## Verify

After services restart and HTTPS/WSS are reachable:

\`\`\`bash
cd $OUTPUT_DIR
VOIDSCAPE_REPO_ROOT=/path/to/voidscape-source ./VERIFY-STAGING.sh
\`\`\`

For the final launch candidate, also run Android emulator/physical smoke and a
hands-on desktop/web playthrough against this same host.
EOF

cat > "$OUTPUT_DIR/RELEASE-HANDOFF.md" <<EOF
# Voidscape Prelaunch Release Handoff

Created: $CREATED_AT
Commit: $FULL_COMMIT
Client version: $VERSION

This file is for the final operator pass before putting player-facing links in
front of people. Fill the blanks with the actual deployed paths from the host.

## Target

- Portal: $PORTAL_URL/
- Web client: $WEB_URL
- Game TCP: $GAME_HOST:$GAME_PORT
- Game WSS: $WS_URL

## Before Deploy

- Confirm this bundle came from the intended commit: \`$FULL_COMMIT\`.
- Confirm \`portal/portal.env.staging\` has real private values for:
  - \`PORTAL_ABUSE_HASH_SALT\`
  - \`PORTAL_ADMIN_TOKEN\`, if staff admin endpoints are enabled
- Keep \`PORTAL_GOOGLE_CLIENT_ID\` unset unless Google sign-in is intentionally enabled and tested.
- If email is enabled, confirm \`PORTAL_EMAIL_PROVIDER=resend\`, \`PORTAL_RESEND_API_KEY\`, and \`PORTAL_PUBLIC_ORIGIN\` are set, then check \`/api/health.config.email.configured\`.
- If Android is public, prefer a release APK bundle, confirm \`MANIFEST.txt\` says \`android_apk_type=release\`, and set \`PORTAL_ANDROID_APK\` to that APK if it lives outside the default build path.

## Backup Paths

Record these before overwriting live files:

\`\`\`text
Portal backup:
Play/web-client backup:
Server jar/config backup:
Database backup:
Launcher/APK public-download backup:
\`\`\`

Suggested single-host backup commands:

\`\`\`bash
stamp="\$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p /opt/voidscape/backups
tar -czf "/opt/voidscape/backups/portal-prelaunch-\$stamp.tgz" -C /opt/voidscape/web portal
tar -czf "/opt/voidscape/backups/play-prelaunch-\$stamp.tgz" -C /opt/voidscape/web play
tar -czf "/opt/voidscape/backups/server-prelaunch-\$stamp.tgz" -C /opt/voidscape server/core.jar server/plugins.jar server/local.conf
sqlite3 /opt/voidscape/server/inc/sqlite/voidscape.db ".backup '/opt/voidscape/backups/voidscape-\$stamp.sqlite'"
\`\`\`

If production is MariaDB, use the matching dump/restore commands from
\`docs/OPERATIONS.md\` instead of the SQLite backup line.

## Deploy

- Copy \`server/core.jar\`, \`server/plugins.jar\`, and the release-critical config values into the live server path.
- Copy \`portal/web-portal/\` to the live portal static/app path.
- Copy \`play/\` to the live \`/play/\` static root.
- Copy \`launcher/VoidscapeLauncher-staging.jar\` and \`launcher/voidscape-launcher.properties\` to the public download path when ready.
- Copy \`android/voidscape-staging.apk\` to the public download path when Android direct download should be live; set \`PORTAL_ANDROID_APK\` to that path if it is not the default build output.
- Restart the server, portal, and reverse proxy units.

## Verify

Run this exact bundle verifier after syncing and restarting:

\`\`\`bash
cd <synced-bundle-dir>
VOIDSCAPE_REPO_ROOT=/path/to/voidscape-source ./VERIFY-STAGING.sh
\`\`\`

Then archive this handoff file, \`MANIFEST.txt\`, and the verifier output with
the backup paths above.

## Rollback

Use rollback only after stopping or draining player traffic.

1. Stop or gate the portal and game server.
2. Restore the previous portal directory from the portal backup.
3. Restore the previous \`/play/\` directory from the web-client backup.
4. Restore previous server jars and config from the server backup.
5. Restore the database backup only if the failed release changed persistent state or corrupted accounts.
6. Restart services and rerun the non-mutating health checks:
   - \`curl -fsS $PORTAL_URL/api/health\`
   - \`curl -fsS $PORTAL_URL/api/public\`
   - \`scripts/verify-web-teavm-deployment.sh $WEB_URL --expected-build-manifest <previous-manifest> --deep-manifest\`

## AGPL Source Disclosure

Before public distribution, confirm the public source link shown by the portal
points to the intended Voidscape source mirror for commit \`$FULL_COMMIT\` or a
documented public-source commit that contains the corresponding AGPL-covered
server/client changes.

Checklist:

- Portal footer/source tab link is current.
- Public source mirror excludes runtime DBs, logs, backups, private deploy files, and secrets.
- Public source mirror includes license text and build/run instructions.
- APK/launcher/web distribution pages link to the same source location or to the portal source page.
- If the deployed private commit differs from the public-source commit, record the public-source commit:

\`\`\`text
Public source URL:
Public source commit:
Source disclosure verified by:
Verified at:
\`\`\`
EOF

cat > "$OUTPUT_DIR/MANIFEST.txt" <<EOF
created_at=$CREATED_AT
commit=$FULL_COMMIT
short_commit=$COMMIT
client_version=$VERSION
game_host=$GAME_HOST
game_port=$GAME_PORT
ws_port=$WS_PORT
portal_url=$PORTAL_URL/
web_url=$WEB_URL
ws_url=$WS_URL
server_core_sha256=$(sha256_file "$OUTPUT_DIR/server/core.jar")
server_plugins_sha256=$(sha256_file "$OUTPUT_DIR/server/plugins.jar")
launcher_sha256=$(sha256_file "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar")
web_build_manifest_sha256=$(sha256_file "$OUTPUT_DIR/play/voidscape-web-build.json")
EOF

if [[ -f "$OUTPUT_DIR/android/voidscape-staging.apk" ]]; then
	printf 'android_apk_type=%s\n' "$([[ "$ANDROID_RELEASE" -eq 1 ]] && echo release || echo debug)" >> "$OUTPUT_DIR/MANIFEST.txt"
	printf 'android_apk_sha256=%s\n' "$(sha256_file "$OUTPUT_DIR/android/voidscape-staging.apk")" >> "$OUTPUT_DIR/MANIFEST.txt"
fi

if [[ -n "$RSYNC_TARGET" ]]; then
	if ! command -v rsync >/dev/null 2>&1; then
		echo "ERROR: --rsync-target requires rsync." >&2
		exit 1
	fi
	echo "==> Syncing bundle to $RSYNC_TARGET"
	rsync -az --delete "$OUTPUT_DIR"/ "$RSYNC_TARGET"/
fi

cat <<EOF
Launch staging bundle created:

  $OUTPUT_DIR

Deploy/read:
  $OUTPUT_DIR/DEPLOYMENT.md
  $OUTPUT_DIR/RELEASE-HANDOFF.md

Verify after deploy:
  $OUTPUT_DIR/VERIFY-STAGING.sh

Manifest:
  $OUTPUT_DIR/MANIFEST.txt
EOF
