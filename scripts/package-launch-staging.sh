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
LAUNCH_AT="2026-07-18T18:00:00Z"
SERVER_PRESET="$ROOT/server/voidscape-launch.conf"
SKIP_BUILD=0
SKIP_WEB_BUILD=0
SKIP_ANDROID=0
ANDROID_RELEASE=0
RSYNC_TARGET=""
ALLOW_DIRTY_STAGING=0
BUNDLE_PROMOTABLE=1
PROMOTION_BLOCKERS=()
SERVER_CLIENT_BUILD_MODE="fresh"
WEB_BUILD_MODE="fresh"
ANDROID_APK_TYPE="not_included"
ANDROID_APK_PROMOTABLE=0
ANDROID_RELEASE_CHECK="not_run"
ANDROID_ENDPOINT_REWRITTEN=0
ANDROID_BUNDLE_APK=""
ANDROID_BUNDLE_META=""

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
  - server/conf runtime definitions, locations, and landscape archives
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
  --launch-at ISO          Launch timestamp. Default: 2026-07-18T18:00:00Z.
  --server-preset FILE     Source config for generated local.launch-staging.conf.
                           Default: server/voidscape-launch.conf.
  --output-dir DIR         Bundle output directory. Default: dist/launch-staging.
  --skip-build             Reuse server/client jars for a rehearsal-only bundle.
  --skip-web-build         Reuse TeaVM output for a rehearsal-only bundle.
  --skip-android           Do not rebuild/copy the Android APK.
  --android-release        Build/copy a signed release APK instead of debug.
                           Requires Android signing config.
  --allow-dirty-staging    Permit an explicitly non-promotable rehearsal bundle
                           with dirty source, skipped builds, debug Android, or
                           rewritten Android endpoints. Final packages fail closed.
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
		--allow-dirty-staging)
			ALLOW_DIRTY_STAGING=1
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
if [[ "$SKIP_ANDROID" -eq 1 && "$ANDROID_RELEASE" -eq 1 ]]; then
	echo "ERROR: --skip-android and --android-release are mutually exclusive." >&2
	exit 1
fi

mark_non_promotable() {
	local reason="$1"
	local existing=""
	if [[ ${#PROMOTION_BLOCKERS[@]} -gt 0 ]]; then
		for existing in "${PROMOTION_BLOCKERS[@]}"; do
			[[ "$existing" == "$reason" ]] && return
		done
	fi
	BUNDLE_PROMOTABLE=0
	PROMOTION_BLOCKERS+=("$reason")
}

promotion_blockers_text() {
	local separator=""
	local reason=""
	if [[ ${#PROMOTION_BLOCKERS[@]} -eq 0 ]]; then
		printf '%s' "none"
		return
	fi
	if [[ ${#PROMOTION_BLOCKERS[@]} -gt 0 ]]; then
		for reason in "${PROMOTION_BLOCKERS[@]}"; do
			printf '%s%s' "$separator" "$reason"
			separator=","
		done
	fi
}

if ! git -C "$ROOT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
	echo "ERROR: launch packages must be built from a Git worktree." >&2
	exit 1
fi
FULL_COMMIT="$(git -C "$ROOT" rev-parse --verify 'HEAD^{commit}' 2>/dev/null || true)"
if ! [[ "$FULL_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then
	echo "ERROR: could not resolve the package source to one full Git commit." >&2
	exit 1
fi
COMMIT="${FULL_COMMIT:0:12}"
SOURCE_DIRTY=0
if [[ -n "$(git -C "$ROOT" status --porcelain --untracked-files=normal)" ]]; then
	SOURCE_DIRTY=1
	mark_non_promotable "source_dirty"
fi
if [[ "$SKIP_BUILD" -eq 1 ]]; then
	SERVER_CLIENT_BUILD_MODE="reused"
	mark_non_promotable "server_client_build_reused"
fi
if [[ "$SKIP_WEB_BUILD" -eq 1 ]]; then
	WEB_BUILD_MODE="reused"
	mark_non_promotable "web_build_reused"
fi
if [[ "$SKIP_ANDROID" -eq 0 && "$ANDROID_RELEASE" -eq 0 ]]; then
	ANDROID_APK_TYPE="debug"
	mark_non_promotable "android_debug_apk"
elif [[ "$SKIP_ANDROID" -eq 0 ]]; then
	ANDROID_APK_TYPE="release"
fi
if [[ "$BUNDLE_PROMOTABLE" -ne 1 && "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
	echo "ERROR: requested package would be non-promotable ($(promotion_blockers_text))." >&2
	echo "Commit/rebuild every release artifact, use --skip-android or --android-release as appropriate, or pass --allow-dirty-staging only for an explicit rehearsal." >&2
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

if [[ "$SKIP_ANDROID" -eq 0 && "$ANDROID_RELEASE" -eq 1 ]]; then
	if [[ ! -f "$ANDROID_CONFIG" ]]; then
		echo "ERROR: Android endpoint config not found: $ANDROID_CONFIG" >&2
		exit 1
	fi
	android_expected_config="$(mktemp)"
	python3 - "$ANDROID_CONFIG" "$android_expected_config" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL/portal?auth=login" "$PORTAL_URL/portal?auth=recovery" <<'PY'
import re
import sys
from pathlib import Path

source, target, host, port, account_url, recovery_url = sys.argv[1:]
text = Path(source).read_text()
for key, value in {
    "VOIDSCAPE_PUBLIC_HOST": host,
    "VOIDSCAPE_DEFAULT_PORT": port,
    "VOIDSCAPE_PORTAL_ACCOUNT_URL": account_url,
    "VOIDSCAPE_PORTAL_RECOVERY_URL": recovery_url,
}.items():
    text = re.sub(
        rf'(public static final String {re.escape(key)} = ")[^"]*(";)',
        rf'\g<1>{value}\2',
        text,
        count=1,
    )
Path(target).write_text(text)
PY
	if ! cmp -s "$ANDROID_CONFIG" "$android_expected_config"; then
		ANDROID_ENDPOINT_REWRITTEN=1
		mark_non_promotable "android_endpoint_rewrite"
	fi
	rm -f "$android_expected_config"
	if [[ "$BUNDLE_PROMOTABLE" -ne 1 && "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
		echo "ERROR: target Android endpoint differs from committed osConfig.java; temporary endpoint rewriting is non-promotable." >&2
		echo "Commit the target endpoint first, or use --allow-dirty-staging only for a rehearsal." >&2
		exit 1
	fi
fi

sha256_file() {
	shasum -a 256 "$1" | awk '{print $1}'
}

write_sha256_tree() {
	local source="$1"
	local target="$2"
	(
		cd "$source"
		find . -type f -print | LC_ALL=C sort | while IFS= read -r relative; do
			shasum -a 256 "$relative"
		done
	) > "$target"
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
tests/client-cache-manifest.sh

OUTPUT_DIR="$(mkdir -p "$OUTPUT_DIR" && cd "$OUTPUT_DIR" && pwd)"
if [[ "$OUTPUT_DIR" == "/" || "$OUTPUT_DIR" == "$ROOT" ]]; then
	echo "ERROR: refusing to replace unsafe output directory: $OUTPUT_DIR" >&2
	exit 1
fi
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"/{server,client,portal,launcher,android,logs,ops/cutover,ops/database,ops/nginx}

VERSION="$(client_version)"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "==> Packaging server artifacts"
cp "$ROOT/server/core.jar" "$OUTPUT_DIR/server/core.jar"
cp "$ROOT/server/plugins.jar" "$OUTPUT_DIR/server/plugins.jar"
helper_tmp="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-helper.XXXXXX")"
(
	cd "$helper_tmp"
	jar xf "$ROOT/server/core.jar" \
		com/openrsc/server/util/BCrypt.class \
		com/openrsc/server/util/rsc/PortalPasswordHasher.class
	jar cf "$OUTPUT_DIR/server/portal-password-helper.jar" com
)
rm -rf "$helper_tmp"
bundle_copy_dir "$ROOT/server/conf" "$OUTPUT_DIR/server/conf"
write_sha256_tree "$OUTPUT_DIR/server/conf" "$OUTPUT_DIR/server/conf.SHA256"
bundle_copy_dir "$ROOT/server/database" "$OUTPUT_DIR/server/database"
write_sha256_tree "$OUTPUT_DIR/server/database" "$OUTPUT_DIR/server/database.SHA256"

echo "==> Packaging launcher-served client runtime"
cp "$ROOT/Client_Base/Open_RSC_Client.jar" "$OUTPUT_DIR/client/Open_RSC_Client.jar"
bundle_copy_dir "$ROOT/Client_Base/Cache" "$OUTPUT_DIR/client/Cache"
rm -f "$OUTPUT_DIR/client/Cache"/{accounts.txt,credentials.txt,uid.dat,ip.txt,port.txt,hideIp.txt,hideip.txt,config.txt,client.properties,discord_inuse.txt,launchersettings.conf,voidscapelauncher.properties}
write_sha256_tree "$OUTPUT_DIR/client/Cache" "$OUTPUT_DIR/client/cache.SHA256"

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
    "combat_exp_rate": "10",
    "skilling_exp_rate": "1.5",
    "idle_timer": "600000",
    "idle_timer_subscriber": "900000",
    "avatar_generator": "false",
    "want_fatigue": "false",
    "aggro_range": "4",
    "wilderness_spawn_multiplier": "1.5",
    "is_localhost_restricted": "true",
    "character_creation_mode": "0",
    "production_command_lockdown": "true",
    "want_email": "false",
    "want_packet_register": "true",
    "want_custom_ui": "true",
    "spawn_auction_npcs": "true",
    "want_cracker_campaign": "true",
    "cracker_campaign_npc_kill_denominator": "500",
    "cracker_campaign_skilling_denominator": "1000",
    "want_beta_onboarding_guide": "false",
    "want_void_enclave": "true",
    "want_void_colossus": "false",
    "want_void_dungeon": "true",
    "custom_landscape": "true",
    "want_equipment_tab": "false",
    "want_custom_banks": "false",
    "want_bank_presets": "true",
    "want_bank_notes": "true",
    "want_cert_as_notes": "true",
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

scripts/check-launch-config.mjs \
	"$OUTPUT_DIR/server/local.launch-staging.conf" \
	--expected-client-version "$VERSION"

echo "==> Packaging reviewed one-shot cutover helpers (not activated)"
cp "$ROOT/scripts/native-portal-backfill-sweep.sh" "$OUTPUT_DIR/ops/cutover/"
cp "$ROOT/Deployment_Scripts/systemd/voidscape-native-portal-backfill.service" \
	"$OUTPUT_DIR/ops/cutover/"
cat > "$OUTPUT_DIR/ops/cutover/README.md" <<'EOF'
# One-shot native portal and launch-card cutover

These files are packaged for the reviewed launch cutover only. Packaging does
not install, enable, or start the systemd unit. There is deliberately no timer.

1. Stop native/portal character creation and take verified portal/game database backups.
2. Run `native-portal-backfill-sweep.sh --dry-run` with the private portal env.
3. Review every count and exception. Record each flagged player ID in exactly one
   of `PORTAL_NATIVE_BACKFILL_APPROVED_EXCEPTIONS` or
   `PORTAL_NATIVE_BACKFILL_EXCLUDED_EXCEPTIONS`, then rerun the dry-run.
4. Archive the ready report and copy its exact review token and frozen snapshot
   time into `PORTAL_NATIVE_BACKFILL_REVIEW_TOKEN` and
   `PORTAL_NATIVE_BACKFILL_AS_OF_MS`.
5. Run `--apply` exactly once after the cutover owner approves the report.
6. Verify one `launch_subcard_2026:<playerId>` row per included character and
   exactly one `launch_subcard_2026:done=1` seal, then rerun `--dry-run`; it must
   report no pending mutations.
7. Remove both one-use review values. Do not enable the service. Archive the
   reports with the release handoff.

After the seal, later ownership repair cannot grant launch or automatic starter
cards to new player IDs. Physical delivery remains game-server-only and must be
proved separately through the vendor transaction QA.
EOF

cp "$ROOT/scripts/prepare-production-sqlite-permissions.sh" "$OUTPUT_DIR/ops/database/"
chmod +x "$OUTPUT_DIR/ops/database/prepare-production-sqlite-permissions.sh"

cat > "$OUTPUT_DIR/ops/nginx/voidscape-admin-public-block.location.conf" <<'EOF'
# Include this location fragment inside every internet-facing Voidscape nginx
# server block that proxies to the portal. Production currently has one shared
# voidscape.gg/www.voidscape.gg block and a legacy sslip.io block.
# The portal backend remains bound to 127.0.0.1:8788, so operators can still use
# token-authenticated admin routes from the host without exposing them publicly.
location ^~ /api/admin/ {
	return 404;
}
EOF
cat > "$OUTPUT_DIR/ops/nginx/README.md" <<EOF
# Public admin-route boundary

Install \`voidscape-admin-public-block.location.conf\` under
\`/etc/nginx/snippets/\`, then include it inside **every** public TLS server
block that proxies to the portal. Production currently means the shared
\`server_name voidscape.gg www.voidscape.gg\` block and the legacy
\`server_name voidscape.5.161.114.251.sslip.io\` block. Both blocks are
mandatory through launch and must include this location guard. Apply the same
rule to any staging alias.

Example inside each public \`server { ... }\` block:

\`\`\`nginx
include /etc/nginx/snippets/voidscape-admin-public-block.location.conf;
\`\`\`

Validate and reload only after the effective config shows the include for both
public hosts:

\`\`\`bash
nginx -t
systemctl reload nginx
curl -sS -o /dev/null -w '%{http_code}\\n' $PORTAL_URL/api/admin/signups
curl -sS -o /dev/null -w '%{http_code}\\n' https://www.voidscape.gg/api/admin/signups
curl -sS -o /dev/null -w '%{http_code}\\n' https://voidscape.5.161.114.251.sslip.io/api/admin/signups
\`\`\`

All three public origins must print \`404\`. For bounded maintenance,
loopback administration may use a temporary token through
\`http://127.0.0.1:8788/api/admin/...\`; remove the token and restart the portal
immediately afterward.
EOF

echo "==> Packaging portal"
bundle_copy_dir "$ROOT/web/portal" "$OUTPUT_DIR/portal/web-portal"
# The tracked development metadata may describe an older public snapshot. Never
# leave that file in a partially-created release bundle; authoritative metadata
# is generated only after all builds and source-consistency checks complete.
rm -f "$OUTPUT_DIR/portal/web-portal/build-meta.json"
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
PORTAL_EMAIL_VERIFICATION_REQUIRED=1
PORTAL_EMAIL_VERIFICATION_TTL_HOURS=48
PORTAL_EMAIL_VERIFICATION_IP_LIMIT=3
PORTAL_EMAIL_VERIFICATION_EMAIL_LIMIT=3
PORTAL_EMAIL_VERIFICATION_WINDOW_MINUTES=60
PORTAL_PASSWORD_RESET_TTL_MINUTES=30
PORTAL_PASSWORD_RESET_IP_LIMIT=5
PORTAL_PASSWORD_RESET_ACCOUNT_LIMIT=3
PORTAL_PASSWORD_RESET_WINDOW_MINUTES=60
PORTAL_LEGACY_CLAIM_TTL_MINUTES=30
PORTAL_LEGACY_CLAIM_IP_LIMIT=10
PORTAL_LEGACY_CLAIM_CHARACTER_LIMIT=5
PORTAL_LEGACY_CLAIM_WINDOW_MINUTES=60
PORTAL_SENSITIVE_ACTION_WINDOW_MINUTES=10
PORTAL_GAME_PASSWORD_RESET_LIMIT=5
PORTAL_GAME_PASSWORD_RESET_WINDOW_MINUTES=60
PORTAL_GAME_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/portal-password-helper.jar
PORTAL_PC_CLIENT_JAR=/opt/voidscape/client/Open_RSC_Client.jar
PORTAL_CLIENT_CACHE_DIR=/opt/voidscape/client/Cache
PORTAL_CLIENT_VERSION=$VERSION
PORTAL_LAUNCHER_JAR=/opt/voidscape/launcher/VoidscapeLauncher-staging.jar
PORTAL_SIGNUP_IP_HOURLY_LIMIT=3
PORTAL_SIGNUP_IP_DAILY_LIMIT=5
PORTAL_SIGNUP_SUBNET_HOURLY_LIMIT=20
PORTAL_SIGNUP_SUBNET_DAILY_LIMIT=50
PORTAL_CHARACTER_ACCOUNT_HOURLY_LIMIT=5
PORTAL_CHARACTER_ACCOUNT_DAILY_LIMIT=10
PORTAL_CHARACTER_IP_HOURLY_LIMIT=10
PORTAL_CHARACTER_IP_DAILY_LIMIT=20
PORTAL_CHARACTER_SUBNET_HOURLY_LIMIT=50
PORTAL_CHARACTER_SUBNET_DAILY_LIMIT=100
# Comma/space-separated IPv4 CIDRs; use /32 entries for exact-IP blocks.
PORTAL_BLOCKED_IP_CIDRS=
PORTAL_PROXY_IP_CIDRS=
PORTAL_PROXY_SIGNUP_IP_DAILY_LIMIT=1
PORTAL_PROXY_CHARACTER_IP_DAILY_LIMIT=1
PORTAL_STARTER_IP_DAILY_LIMIT=0
# Cutover accounts receive cards through the one-shot release migration in Slice 3.
# Newly-created post-cutover accounts must never inherit a time-window starter grant.
PORTAL_LAUNCH_FREE_CARD_HOURS=0
PORTAL_ABUSE_HASH_SALT=CHANGE_ME_STABLE_PRIVATE_SECRET
# Keep PORTAL_ADMIN_TOKEN unset during normal public operation. For bounded
# maintenance only, set a temporary token and call the loopback backend, then
# remove the token and restart the portal.
# PORTAL_ADMIN_TOKEN=TEMPORARY_LOOPBACK_MAINTENANCE_ONLY
# PORTAL_ANDROID_APK overrides the APK served by /downloads/android-apk.
# Set it only after MANIFEST.txt records a promotable checker-passed release APK.
# PORTAL_ANDROID_APK=/var/www/html/voidscape/Voidscape-Android.apk
# PORTAL_GOOGLE_CLIENT_ID=
# PORTAL_GOOGLE_JWKS_URL=https://www.googleapis.com/oauth2/v3/certs
# Verified-email registration and password recovery require Resend delivery.
# Use dry-run only for non-public staging smoke tests.
# PORTAL_EMAIL_PROVIDER=resend
# PORTAL_EMAIL_DRY_RUN=1
# PORTAL_RESEND_API_KEY=
# PORTAL_EMAIL_FROM="Voidscape <launch@voidscape.gg>"
# PORTAL_EMAIL_REPLY_TO=support@voidscape.gg
PORTAL_REQUIRE_EMAIL=1
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
	python3 - "$ANDROID_CONFIG" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL/portal?auth=login" "$PORTAL_URL/portal?auth=recovery" <<'PY'
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
	if ! cmp -s "$ANDROID_BACKUP" "$ANDROID_CONFIG"; then
		ANDROID_ENDPOINT_REWRITTEN=1
		mark_non_promotable "android_endpoint_rewrite"
		if [[ "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
			echo "ERROR: target Android endpoint differs from committed osConfig.java; a temporary rewrite cannot produce a promotable APK." >&2
			echo "Commit the production endpoint first, or use --allow-dirty-staging only for a rehearsal artifact." >&2
			exit 1
		fi
	fi
	if [[ "$ANDROID_RELEASE" -eq 1 ]]; then
		if [[ "$BUNDLE_PROMOTABLE" -ne 1 ]]; then
			VOIDSCAPE_ANDROID_ALLOW_DIRTY_RELEASE=1 scripts/build-android.sh --release
		else
			scripts/build-android.sh --release
		fi
		ANDROID_APK="$ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/release/voidscape.apk"
		ANDROID_LABEL="release"
	else
		scripts/build-android.sh --debug
		ANDROID_APK="$ROOT/Android_Client/Open RSC Android Client/build/outputs/apk/debug/voidscape.apk"
		ANDROID_LABEL="debug"
	fi
	if [[ "$BUNDLE_PROMOTABLE" -eq 1 && "$ANDROID_RELEASE" -eq 1 ]]; then
		mkdir -p "$OUTPUT_DIR/android/candidate"
		ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/candidate/voidscape-release.apk"
		ANDROID_BUNDLE_META="$ANDROID_BUNDLE_APK.json"
		cp "$ANDROID_APK" "$ANDROID_BUNDLE_APK"
		if [[ ! -f "$ANDROID_APK.json" ]]; then
			echo "ERROR: release APK metadata sidecar is missing: $ANDROID_APK.json" >&2
			exit 1
		fi
		cp "$ANDROID_APK.json" "$ANDROID_BUNDLE_META"
		if [[ -z "${VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256:-}" ]]; then
			echo "ERROR: VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256 is required before a direct-download APK can be packaged." >&2
			exit 1
		fi
		ANDROID_CHECK_LOG="$OUTPUT_DIR/android/android-apk-release-check.txt"
		if ! scripts/check-android-apk-release.sh \
			--apk "$ANDROID_BUNDLE_APK" \
			--meta "$ANDROID_BUNDLE_META" \
			--server-config "$OUTPUT_DIR/server/local.launch-staging.conf" \
			--expected-signer-sha256 "$VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256" \
			>"$ANDROID_CHECK_LOG" 2>&1; then
			cat "$ANDROID_CHECK_LOG" >&2
			echo "ERROR: Android release checker failed; no direct-publication APK was created." >&2
			exit 1
		fi
		ANDROID_RELEASE_CHECK="passed"
	else
		mkdir -p "$OUTPUT_DIR/android/rehearsal-only"
		ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-$ANDROID_LABEL.apk"
		ANDROID_BUNDLE_META="$ANDROID_BUNDLE_APK.json"
		cp "$ANDROID_APK" "$ANDROID_BUNDLE_APK"
		if [[ -f "$ANDROID_APK.json" ]]; then
			cp "$ANDROID_APK.json" "$ANDROID_BUNDLE_META"
		fi
		if [[ "$ANDROID_RELEASE" -eq 1 ]]; then
			ANDROID_RELEASE_CHECK="not_run_non_promotable"
		else
			ANDROID_RELEASE_CHECK="not_applicable_debug"
		fi
	fi
	restore_sources
	trap restore_sources EXIT
else
	echo "==> Skipping Android APK packaging"
fi

# Build steps temporarily rewrite endpoint resources and then restore them. Recheck
# tracked changes after restoration so a build cannot be mislabeled as clean. The
# initial gate already rejects pre-existing untracked source; package output itself
# may intentionally live in an otherwise-untracked custom output directory.
if ! git diff --quiet || ! git diff --cached --quiet; then
	SOURCE_DIRTY=1
	mark_non_promotable "tracked_source_changed_during_packaging"
	if [[ "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
		echo "ERROR: packaging changed or discovered tracked source; review/commit it before promotion." >&2
		exit 1
	fi
fi

CURRENT_FULL_COMMIT="$(git -C "$ROOT" rev-parse --verify 'HEAD^{commit}' 2>/dev/null || true)"
if [[ "$CURRENT_FULL_COMMIT" != "$FULL_COMMIT" ]]; then
	echo "ERROR: Git HEAD changed during packaging; refusing ambiguous build metadata." >&2
	exit 1
fi

PROMOTABLE_TEXT="$([[ "$BUNDLE_PROMOTABLE" -eq 1 ]] && echo true || echo false)"
PROMOTION_BLOCKERS_TEXT="$(promotion_blockers_text)"
SOURCE_DIRTY_TEXT="$([[ "$SOURCE_DIRTY" -eq 1 ]] && echo true || echo false)"

# Generate public metadata only after validating the immutable private commit
# captured before every build. Publication is deliberately pending: public
# runtime state must expose neither that private commit nor the older mirror.
python3 - \
	"$OUTPUT_DIR/portal/web-portal/build-meta.json" \
	"$FULL_COMMIT" \
	"$COMMIT" \
	"$SOURCE_DIRTY_TEXT" \
	"$CREATED_AT" <<'PY'
import json
import re
import sys
from pathlib import Path

target, commit, short_commit, dirty_text, generated_at = sys.argv[1:]
if not re.fullmatch(r"[0-9a-f]{40}", commit):
    raise SystemExit("ERROR: portal build metadata requires a full 40-hex commit")
if short_commit != commit[:12]:
    raise SystemExit("ERROR: portal build metadata short commit is inconsistent")
if dirty_text not in {"true", "false"}:
    raise SystemExit("ERROR: portal build metadata dirty state is invalid")

metadata = {
    "branch": "",
    "commit": "",
    "dirty": False,
    "generatedAt": generated_at,
    "repositoryUrl": "",
    "shortCommit": "",
    "status": "publication_pending",
}
path = Path(target)
path.write_text(json.dumps(metadata, indent=2, sort_keys=True) + "\n", encoding="utf-8")

written = json.loads(path.read_text(encoding="utf-8"))
if written != metadata:
    raise SystemExit("ERROR: portal build metadata did not round-trip exactly")
if (written["status"] != "publication_pending"
        or any(written[key] for key in ("repositoryUrl", "commit", "shortCommit", "branch"))):
    raise SystemExit("ERROR: unpublished source metadata must remain unlinked")
PY

if [[ -n "$ANDROID_BUNDLE_APK" && "$ANDROID_BUNDLE_APK" == "$OUTPUT_DIR/android/candidate/"* ]]; then
	if [[ "$BUNDLE_PROMOTABLE" -eq 1 && "$ANDROID_RELEASE_CHECK" == "passed" ]]; then
		mv "$ANDROID_BUNDLE_APK" "$OUTPUT_DIR/android/voidscape-staging.apk"
		mv "$ANDROID_BUNDLE_META" "$OUTPUT_DIR/android/voidscape-staging.apk.json"
		ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/voidscape-staging.apk"
		ANDROID_BUNDLE_META="$OUTPUT_DIR/android/voidscape-staging.apk.json"
		ANDROID_APK_PROMOTABLE=1
		rmdir "$OUTPUT_DIR/android/candidate"
	else
		mkdir -p "$OUTPUT_DIR/android/rehearsal-only"
		mv "$ANDROID_BUNDLE_APK" "$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-release.apk"
		mv "$ANDROID_BUNDLE_META" "$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-release.apk.json"
		ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-release.apk"
		ANDROID_BUNDLE_META="$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-release.apk.json"
		rmdir "$OUTPUT_DIR/android/candidate"
	fi
fi

if [[ "$ANDROID_APK_PROMOTABLE" -eq 1 ]]; then
	cat > "$OUTPUT_DIR/android/README.md" <<EOF
# Android direct-download release APK

\`voidscape-staging.apk\` is present only because this bundle is promotable and
\`scripts/check-android-apk-release.sh\` passed against its sidecar, the target
server config, the clean source provenance, and the trusted signer fingerprint.

Immediately before public copy, rerun from the matching clean source checkout:

\`\`\`bash
scripts/check-android-apk-release.sh \\
  --apk "$OUTPUT_DIR/android/voidscape-staging.apk" \\
  --meta "$OUTPUT_DIR/android/voidscape-staging.apk.json" \\
  --server-config "$OUTPUT_DIR/server/local.launch-staging.conf" \\
  --expected-signer-sha256 "\$VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256"
\`\`\`
EOF
elif [[ -n "$ANDROID_BUNDLE_APK" ]]; then
	cat > "$OUTPUT_DIR/android/README.md" <<EOF
# REHEARSAL-ONLY Android APK

This bundle is not promotable: \`$PROMOTION_BLOCKERS_TEXT\`.
The APK is isolated under \`android/rehearsal-only/\`; there is deliberately no
\`android/voidscape-staging.apk\` publication alias. Do not publish it, set
\`PORTAL_ANDROID_APK\` to it, or upload it to a release channel. Rebuild from a
clean commit without skipped builds or temporary endpoint rewrites, then require
\`scripts/check-android-apk-release.sh\` to pass.
EOF
else
	cat > "$OUTPUT_DIR/android/README.md" <<'EOF'
# Android APK not included

No direct-download APK is present. Do not advertise or configure an APK path
from this bundle. Google Play artifacts are promoted through their separate AAB
release gate.
EOF
fi

cat > "$OUTPUT_DIR/VERIFY-STAGING.sh" <<EOF
#!/usr/bin/env bash
set -euo pipefail
BUNDLE_DIR="\$(cd "\$(dirname "\${BASH_SOURCE[0]}")" && pwd)"
if [[ "$PROMOTABLE_TEXT" != "true" && "\${VOIDSCAPE_ALLOW_REHEARSAL_VERIFY:-0}" != "1" ]]; then
	echo "ERROR: bundle is non-promotable ($PROMOTION_BLOCKERS_TEXT); set VOIDSCAPE_ALLOW_REHEARSAL_VERIFY=1 only for an isolated rehearsal." >&2
	exit 1
fi
REPO_ROOT="\${VOIDSCAPE_REPO_ROOT:-\$(pwd)}"
DEPLOYED_SERVER_DIR="\${VOIDSCAPE_DEPLOYED_SERVER_DIR:-/opt/voidscape/server}"
DEPLOYED_CLIENT_DIR="\${VOIDSCAPE_DEPLOYED_CLIENT_DIR:-/opt/voidscape/client}"
: "\${VOIDSCAPE_VERIFY_SIGNUP_USERNAME:?Set the exact new QA character name}"
: "\${VOIDSCAPE_VERIFY_SIGNUP_EMAIL:?Set the exact email address you will verify}"
: "\${VOIDSCAPE_VERIFY_SIGNUP_PASSWORD:?Set the exact portal/game password for that new QA account}"
EXTRA_ARGS=()
ADMIN_PUBLIC_URLS=("$PORTAL_URL/")
if [[ "$PORTAL_URL" == "https://voidscape.gg" ]]; then
	ADMIN_PUBLIC_URLS+=("https://www.voidscape.gg/")
	ADMIN_PUBLIC_URLS+=("https://voidscape.5.161.114.251.sslip.io/")
elif [[ "$PORTAL_URL" == "https://www.voidscape.gg" ]]; then
	ADMIN_PUBLIC_URLS+=("https://voidscape.gg/")
	ADMIN_PUBLIC_URLS+=("https://voidscape.5.161.114.251.sslip.io/")
elif [[ "$PORTAL_URL" == "https://voidscape.5.161.114.251.sslip.io" ]]; then
	ADMIN_PUBLIC_URLS+=("https://voidscape.gg/")
	ADMIN_PUBLIC_URLS+=("https://www.voidscape.gg/")
fi
for admin_public_url in "\${ADMIN_PUBLIC_URLS[@]}"; do
	EXTRA_ARGS+=(--admin-public-url "\$admin_public_url")
done
EXTRA_ARGS+=(
	--signup-username "\$VOIDSCAPE_VERIFY_SIGNUP_USERNAME"
	--signup-email "\$VOIDSCAPE_VERIFY_SIGNUP_EMAIL"
	--signup-password "\$VOIDSCAPE_VERIFY_SIGNUP_PASSWORD"
	--qa-user "\$VOIDSCAPE_VERIFY_SIGNUP_USERNAME"
	--qa-pass "\$VOIDSCAPE_VERIFY_SIGNUP_PASSWORD"
)
verify_exact_sha256_tree() {
	local tree_dir="\$1"
	local manifest="\$2"
	local expected_list
	local actual_list
	expected_list="\$(mktemp "\${TMPDIR:-/tmp}/voidscape-tree-expected.XXXXXX")"
	actual_list="\$(mktemp "\${TMPDIR:-/tmp}/voidscape-tree-actual.XXXXXX")"
	(cd "\$tree_dir" && shasum -a 256 -c "\$manifest")
	sed -E 's/^[0-9a-fA-F]{64}[[:space:]]+[*]?//' "\$manifest" | LC_ALL=C sort > "\$expected_list"
	(cd "\$tree_dir" && find . -type f -print | LC_ALL=C sort) > "\$actual_list"
	if ! diff -u "\$expected_list" "\$actual_list"; then
		rm -f "\$expected_list" "\$actual_list"
		echo "ERROR: unexpected or missing files in SHA-256 tree: \$tree_dir" >&2
		return 1
	fi
	rm -f "\$expected_list" "\$actual_list"
}
(cd "\$BUNDLE_DIR/server/conf" && shasum -a 256 -c ../conf.SHA256)
verify_exact_sha256_tree "\$BUNDLE_DIR/server/database" "\$BUNDLE_DIR/server/database.SHA256"
(cd "\$BUNDLE_DIR/client/Cache" && shasum -a 256 -c ../cache.SHA256)
test -f "\$DEPLOYED_SERVER_DIR/local.conf"
cmp -s "\$BUNDLE_DIR/server/core.jar" "\$DEPLOYED_SERVER_DIR/core.jar"
cmp -s "\$BUNDLE_DIR/server/plugins.jar" "\$DEPLOYED_SERVER_DIR/plugins.jar"
cmp -s "\$BUNDLE_DIR/server/portal-password-helper.jar" "\$DEPLOYED_SERVER_DIR/portal-password-helper.jar"
(cd "\$DEPLOYED_SERVER_DIR/conf" && shasum -a 256 -c "\$BUNDLE_DIR/server/conf.SHA256")
verify_exact_sha256_tree "\$DEPLOYED_SERVER_DIR/database" "\$BUNDLE_DIR/server/database.SHA256"
cmp -s "\$BUNDLE_DIR/client/Open_RSC_Client.jar" "\$DEPLOYED_CLIENT_DIR/Open_RSC_Client.jar"
(cd "\$DEPLOYED_CLIENT_DIR/Cache" && shasum -a 256 -c "\$BUNDLE_DIR/client/cache.SHA256")
helper_password_b64="\$(printf '%s' 'LaunchHelperProbe9' | base64 | tr '+/' '-_' | tr -d '=\n')"
helper_salt_b64="\$(printf '%s' 'launch-helper-salt' | base64 | tr '+/' '-_' | tr -d '=\n')"
helper_hash="\$(printf 'hash\n%s\n%s\n' "\$helper_password_b64" "\$helper_salt_b64" | java -cp "\$DEPLOYED_SERVER_DIR/portal-password-helper.jar" com.openrsc.server.util.rsc.PortalPasswordHasher)"
helper_hash_b64="\$(printf '%s' "\$helper_hash" | base64 | tr '+/' '-_' | tr -d '=\n')"
test "\$(printf 'check\n%s\n%s\n%s\n' "\$helper_password_b64" "\$helper_salt_b64" "\$helper_hash_b64" | java -cp "\$DEPLOYED_SERVER_DIR/portal-password-helper.jar" com.openrsc.server.util.rsc.PortalPasswordHasher)" = "true"
"\$REPO_ROOT/scripts/verify-launch-staging.mjs" \\
  --portal-url "$PORTAL_URL/" \\
  --web-url "$WEB_URL" \\
  --ws "$WS_URL" \\
  --expected-build-manifest "\$BUNDLE_DIR/play/voidscape-web-build.json" \\
  --server-config "\$DEPLOYED_SERVER_DIR/local.conf" \\
  --expected-client-version "$VERSION" \\
  --out "\$BUNDLE_DIR/logs/verify-staging-\$(date -u +%Y%m%dT%H%M%SZ)" \\
  "\${EXTRA_ARGS[@]}" \\
  --run-signup
EOF
chmod +x "$OUTPUT_DIR/VERIFY-STAGING.sh"

cat > "$OUTPUT_DIR/DEPLOYMENT.md" <<EOF
# Voidscape Launch Staging Bundle

Created: $CREATED_AT
Client version: $VERSION
Source dirty: $([[ "$SOURCE_DIRTY" -eq 1 ]] && echo true || echo false)
Source publication: pending (no current public-source URL)
Promotable: $PROMOTABLE_TEXT
Promotion blockers: $PROMOTION_BLOCKERS_TEXT
Server/client build: $SERVER_CLIENT_BUILD_MODE
Web build: $WEB_BUILD_MODE

If \`Promotable\` is false, this bundle is rehearsal-only regardless of the
\`Source dirty\` value. Never publish, deploy as a release, or reuse its artifacts.

## Target

- Portal: $PORTAL_URL/
- Web client: $WEB_URL
- Game TCP: $GAME_HOST:$GAME_PORT
- Game WSS: $WS_URL

## Files

- \`server/core.jar\`, \`server/plugins.jar\`, and the isolated \`server/portal-password-helper.jar\`
- \`server/conf/\` plus \`server/conf.SHA256\` (runtime definitions, locations, and landscape data)
- \`server/database/\` plus \`server/database.SHA256\` (the exact migration/query tree)
- \`server/local.launch-staging.conf\`
- \`client/Open_RSC_Client.jar\`, \`client/Cache/\`, and \`client/cache.SHA256\`
- \`portal/web-portal/\`
- \`portal/portal.env.staging\`
- \`play/\`
- \`launcher/VoidscapeLauncher-staging.jar\`
- \`android/README.md\`; a root \`android/voidscape-staging.apk\` exists only after the release checker passes in a promotable bundle
- \`ops/cutover/\` one-shot native ownership backfill helpers (packaged inactive; no timer)
- \`ops/database/prepare-production-sqlite-permissions.sh\` for the shared game/portal SQLite runtime contract
- \`ops/nginx/voidscape-admin-public-block.location.conf\` for every public portal-proxying nginx block

## Host Placement

Suggested single-host layout:

\`\`\`text
/opt/voidscape/server/core.jar
/opt/voidscape/server/plugins.jar
/opt/voidscape/server/portal-password-helper.jar
/opt/voidscape/server/conf/
/opt/voidscape/server/database/
/opt/voidscape/server/local.conf
/opt/voidscape/client/Open_RSC_Client.jar
/opt/voidscape/client/Cache/
/opt/voidscape/launcher/VoidscapeLauncher-staging.jar
/opt/voidscape/web/portal/
/etc/voidscape/portal.env
/var/lib/voidscape-portal/
/var/www/html/play/
/var/www/html/voidscape/
\`\`\`

Copy \`server/local.launch-staging.conf\` to the deployed server's \`local.conf\`
or merge its release-critical values into the real private config:

- \`client_version: $VERSION\`
- \`enforce_custom_client_version: true\`
- \`production_command_lockdown: true\`
- \`want_email: false\`
- \`want_packet_register: true\`
- \`want_pcap_logging: false\`
- \`member_world: true\`
- \`want_feature_websockets: true\`
- \`want_void_dungeon: true\`
- \`want_void_colossus: false\`
- \`want_beta_onboarding_guide: false\`
- \`custom_landscape: true\`

Copy \`portal/portal.env.staging\` to the host, replace its required private
\`CHANGE_ME\` values, keep \`PORTAL_ADMIN_TOKEN\` unset during normal operation,
and keep \`PORTAL_GOOGLE_CLIENT_ID\` unset unless Google is intentionally being
tested. Verified signup and password recovery require working Resend delivery;
use \`PORTAL_EMAIL_DRY_RUN=1\` only for private staging.

While both the portal and game services are stopped, establish and verify the
shared SQLite runtime contract before either service restarts:

\`\`\`bash
sudo ./ops/database/prepare-production-sqlite-permissions.sh \\
  --db /opt/voidscape/server/inc/sqlite/voidscape.db \\
  --service-user voidscape \\
  --service-group voidscape
\`\`\`

This preserves root ownership of the SQLite directory while granting its
\`voidscape\` group the write permission SQLite needs for rollback journals. It
keeps the database private to the service identity and proves a main-database
write inside a rolled-back transaction without leaving a table behind.

Install \`ops/nginx/voidscape-admin-public-block.location.conf\` under
\`/etc/nginx/snippets/\` and include it in every portal-proxying TLS server
block: the shared \`voidscape.gg www.voidscape.gg\` block and the mandatory
launch-period \`voidscape.5.161.114.251.sslip.io\` block.
Run \`nginx -t\`, reload nginx, and require external \`404\` responses for
\`/api/admin/*\` on every origin. Do not add this
location to the loopback portal listener. Bounded maintenance may set a
temporary token and use \`http://127.0.0.1:8788\`; remove it and restart the
portal immediately afterward.

## Verify

After services restart and HTTPS/WSS are reachable, choose a new QA character,
email, and password. Start the command, then open the delivered email and confirm
that exact signup within the bounded verifier window:

\`\`\`bash
cd $OUTPUT_DIR
VOIDSCAPE_REPO_ROOT=/path/to/voidscape-source \
VOIDSCAPE_DEPLOYED_SERVER_DIR=/opt/voidscape/server \
VOIDSCAPE_DEPLOYED_CLIENT_DIR=/opt/voidscape/client \
VOIDSCAPE_VERIFY_SIGNUP_USERNAME=<new-qa-character> \
VOIDSCAPE_VERIFY_SIGNUP_EMAIL=<email-you-can-open-now> \
VOIDSCAPE_VERIFY_SIGNUP_PASSWORD=<new-portal-and-game-password> \
./VERIFY-STAGING.sh
\`\`\`

For the final launch candidate, also run Android emulator/physical smoke and a
hands-on desktop/web playthrough against this same host.
EOF

cat > "$OUTPUT_DIR/RELEASE-HANDOFF.md" <<EOF
# Voidscape Prelaunch Release Handoff

Created: $CREATED_AT
Commit: $FULL_COMMIT
Client version: $VERSION
Source dirty: $([[ "$SOURCE_DIRTY" -eq 1 ]] && echo true || echo false)
Source publication: pending (no current public-source URL)
Promotable: $PROMOTABLE_TEXT
Promotion blockers: $PROMOTION_BLOCKERS_TEXT
Server/client build: $SERVER_CLIENT_BUILD_MODE
Web build: $WEB_BUILD_MODE

STOP: if \`Promotable: false\`, this handoff is rehearsal evidence only. A clean
source flag does not override reused builds, debug Android, or dirty Android
provenance. Rebuild until \`Promotable: true\` before any release action.

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
  - Keep \`PORTAL_ADMIN_TOKEN\` unset during normal public operation. If bounded
    maintenance is required, use a temporary token only against
    \`127.0.0.1:8788\`, then remove it and restart the portal.
- Keep \`PORTAL_GOOGLE_CLIENT_ID\` unset unless Google sign-in is intentionally enabled and tested.
- Confirm \`PORTAL_EMAIL_VERIFICATION_REQUIRED=1\`, \`PORTAL_REQUIRE_EMAIL=1\`, \`PORTAL_EMAIL_PROVIDER=resend\`, \`PORTAL_RESEND_API_KEY\`, and \`PORTAL_PUBLIC_ORIGIN\` are set, then check \`/api/health.config.email.configured\` and \`.verificationRequired\`.
- Confirm \`PORTAL_LAUNCH_FREE_CARD_HOURS=0\`; launch cards come only from the reviewed cutover cohort migration.
- Confirm \`PORTAL_GAME_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/portal-password-helper.jar\` and that the packaged helper jar passes its stdin check before enabling Character Manager password changes or older native-account claims.
- Direct Android publication is permitted only when \`MANIFEST.txt\` says
  \`promotable=true\`, \`android_apk_promotable=true\`, and
  \`android_release_check=passed\`. Rerun the exact checker command in
  \`android/README.md\` immediately before copying; otherwise publish no APK.
- Confirm the native portal backfill was dry-run against verified backups, reports zero conflicts, and is applied exactly once. Never enable a repeating timer; none is packaged.
- Confirm both services are stopped, then run
  \`sudo ./ops/database/prepare-production-sqlite-permissions.sh\`. Do not
  restart the portal unless its rollback-only service-identity probe passes.
- Install \`ops/nginx/voidscape-admin-public-block.location.conf\` in every
  portal-proxying public TLS block: the shared \`voidscape.gg www.voidscape.gg\`
  block and the mandatory launch-period
  \`voidscape.5.161.114.251.sslip.io\` block. The final hosted verifier probes
  all three origins and requires external \`404\` responses even when an admin-token
  header is sent; token administration remains loopback-only.

## Backup Paths

Record these before overwriting live files:

\`\`\`text
Portal backup:
Portal data backup:
Portal environment backup:
Play/web-client backup:
Server jar/config backup:
Database backup:
Launcher/APK public-download backup:
Nginx config backup:
\`\`\`

Suggested single-host backup commands:

Gate player/account traffic and stop the game plus portal services before these
commands so the portal JSON tree and game database describe one cutover point.

\`\`\`bash
stamp="\$(date -u +%Y%m%dT%H%M%SZ)"
install -d -m 700 /opt/voidscape/backups
tar -czf "/opt/voidscape/backups/portal-prelaunch-\$stamp.tgz" -C /opt/voidscape/web portal
tar -czf "/opt/voidscape/backups/portal-data-prelaunch-\$stamp.tgz" -C /var/lib voidscape-portal
install -m 600 /etc/voidscape/portal.env "/opt/voidscape/backups/portal.env-prelaunch-\$stamp"
tar -czf "/opt/voidscape/backups/public-web-prelaunch-\$stamp.tgz" -C /var/www/html play voidscape
tar -czf "/opt/voidscape/backups/nginx-prelaunch-\$stamp.tgz" -C /etc nginx
tar -czf "/opt/voidscape/backups/server-prelaunch-\$stamp.tgz" -C /opt/voidscape server/core.jar server/plugins.jar server/portal-password-helper.jar server/conf server/database server/local.conf client launcher
sqlite3 /opt/voidscape/server/inc/sqlite/voidscape.db ".backup '/opt/voidscape/backups/voidscape-\$stamp.sqlite'"
\`\`\`

If production is MariaDB, use the matching dump/restore commands from
\`docs/OPERATIONS.md\` instead of the SQLite backup line.

## Deploy

- Copy \`server/core.jar\`, \`server/plugins.jar\`, \`server/portal-password-helper.jar\`, \`server/conf/\`, \`server/database/\`, \`client/\`, \`launcher/\`, and the release-critical config values into the documented live paths.
- From the bundle's \`server/conf/\` directory, run \`shasum -a 256 -c ../conf.SHA256\` before restarting the server.
- From the bundle's \`server/database/\` directory, run \`shasum -a 256 -c ../database.SHA256\`, then run the same manifest against \`/opt/voidscape/server/database/\` before booting migrations.
- From the bundle's \`client/Cache/\` directory, run \`shasum -a 256 -c ../cache.SHA256\` before publishing the launcher channel.
- Copy \`portal/web-portal/\` to the live portal static/app path.
- Copy \`play/\` to \`/var/www/html/play/\`.
- Copy \`launcher/VoidscapeLauncher-staging.jar\` and \`launcher/voidscape-launcher.properties\` to the public download path when ready.
- Only after rerunning the passing checker from \`android/README.md\`, copy
  \`android/voidscape-staging.apk\` when it exists and the three manifest gates
  above are true. Never publish anything under \`android/rehearsal-only/\`.
- While both services remain stopped, run
  \`sudo ./ops/database/prepare-production-sqlite-permissions.sh\` to enforce
  \`root:voidscape 0770\` on the SQLite directory, \`voidscape:voidscape 0600\`
  on the DB, and pass the rollback-only write probe before any restart.
- Install the packaged nginx location fragment in every public portal-proxying block, then run \`nginx -t\` before reloading nginx.
- Restart the server and portal units, then reload the reverse proxy.

## Verify

Run this exact bundle verifier after syncing and restarting:

\`\`\`bash
cd <synced-bundle-dir>
VOIDSCAPE_REPO_ROOT=/path/to/voidscape-source \
VOIDSCAPE_DEPLOYED_SERVER_DIR=/opt/voidscape/server \
VOIDSCAPE_DEPLOYED_CLIENT_DIR=/opt/voidscape/client \
VOIDSCAPE_VERIFY_SIGNUP_USERNAME=<new-qa-character> \
VOIDSCAPE_VERIFY_SIGNUP_EMAIL=<email-you-can-open-now> \
VOIDSCAPE_VERIFY_SIGNUP_PASSWORD=<new-portal-and-game-password> \
./VERIFY-STAGING.sh
\`\`\`

Then archive this handoff file, \`MANIFEST.txt\`, and the verifier output with
the backup paths above.

## Rollback

Use rollback only after stopping or draining player traffic.

1. Stop or gate the portal and game server.
2. Restore the previous portal app, \`/var/lib/voidscape-portal\`, and \`/etc/voidscape/portal.env\` from their separate backups.
3. Restore both \`/var/www/html/play\` and \`/var/www/html/voidscape\` from the public-web backup.
4. Restore previous server jars, runtime \`conf/\`, database definitions, client/launcher artifacts, and config from the server backup.
5. Restore the database backup only if the failed release changed persistent state or corrupted accounts.
6. Restore the prior nginx config, run \`nginx -t\`, then restart services and rerun the non-mutating health checks:
   - \`curl -fsS $PORTAL_URL/api/health\`
   - \`curl -fsS $PORTAL_URL/api/public\`
   - \`scripts/verify-web-teavm-deployment.sh $WEB_URL --expected-build-manifest <previous-manifest> --deep-manifest\`

## Source Publication Status

Publication of the release source is pending by owner decision. This private
handoff and \`MANIFEST.txt\` record build commit \`$FULL_COMMIT\` for operator
provenance. The public portal metadata deliberately contains no repository URL,
commit, short commit, or branch. Do not present the older public mirror as the
corresponding source for this build.

Source publication or disclosure is a separate reviewed task. Before adding a
link, verify that it resolves to source corresponding to this release, excludes
runtime DBs, logs, backups, private deploy files, and secrets, and includes the
license plus build/run instructions. Record that later decision here:

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
source_dirty=$([[ "$SOURCE_DIRTY" -eq 1 ]] && echo true || echo false)
source_publication_status=publication_pending
source_repository_url=none
promotable=$PROMOTABLE_TEXT
promotion_blockers=$PROMOTION_BLOCKERS_TEXT
server_client_build_mode=$SERVER_CLIENT_BUILD_MODE
web_build_mode=$WEB_BUILD_MODE
client_version=$VERSION
game_host=$GAME_HOST
game_port=$GAME_PORT
ws_port=$WS_PORT
portal_url=$PORTAL_URL/
web_url=$WEB_URL
ws_url=$WS_URL
server_core_sha256=$(sha256_file "$OUTPUT_DIR/server/core.jar")
server_plugins_sha256=$(sha256_file "$OUTPUT_DIR/server/plugins.jar")
portal_password_helper_sha256=$(sha256_file "$OUTPUT_DIR/server/portal-password-helper.jar")
server_conf_manifest_sha256=$(sha256_file "$OUTPUT_DIR/server/conf.SHA256")
server_database_manifest_sha256=$(sha256_file "$OUTPUT_DIR/server/database.SHA256")
server_database_file_count=$(wc -l < "$OUTPUT_DIR/server/database.SHA256" | tr -d '[:space:]')
client_runtime_sha256=$(sha256_file "$OUTPUT_DIR/client/Open_RSC_Client.jar")
client_cache_manifest_sha256=$(sha256_file "$OUTPUT_DIR/client/cache.SHA256")
launcher_sha256=$(sha256_file "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar")
web_build_manifest_sha256=$(sha256_file "$OUTPUT_DIR/play/voidscape-web-build.json")
portal_build_meta_sha256=$(sha256_file "$OUTPUT_DIR/portal/web-portal/build-meta.json")
native_portal_backfill_sha256=$(sha256_file "$OUTPUT_DIR/ops/cutover/native-portal-backfill-sweep.sh")
native_portal_backfill_service_sha256=$(sha256_file "$OUTPUT_DIR/ops/cutover/voidscape-native-portal-backfill.service")
sqlite_permissions_helper_sha256=$(sha256_file "$OUTPUT_DIR/ops/database/prepare-production-sqlite-permissions.sh")
nginx_admin_public_block_sha256=$(sha256_file "$OUTPUT_DIR/ops/nginx/voidscape-admin-public-block.location.conf")
android_apk_type=$ANDROID_APK_TYPE
android_apk_promotable=$([[ "$ANDROID_APK_PROMOTABLE" -eq 1 ]] && echo true || echo false)
android_release_check=$ANDROID_RELEASE_CHECK
android_endpoint_rewritten=$([[ "$ANDROID_ENDPOINT_REWRITTEN" -eq 1 ]] && echo true || echo false)
EOF

if [[ -n "$ANDROID_BUNDLE_APK" && -f "$ANDROID_BUNDLE_APK" ]]; then
	printf 'android_apk_path=%s\n' "${ANDROID_BUNDLE_APK#$OUTPUT_DIR/}" >> "$OUTPUT_DIR/MANIFEST.txt"
	printf 'android_apk_sha256=%s\n' "$(sha256_file "$ANDROID_BUNDLE_APK")" >> "$OUTPUT_DIR/MANIFEST.txt"
	if [[ -n "$ANDROID_BUNDLE_META" && -f "$ANDROID_BUNDLE_META" ]]; then
		printf 'android_apk_metadata_sha256=%s\n' "$(sha256_file "$ANDROID_BUNDLE_META")" >> "$OUTPUT_DIR/MANIFEST.txt"
	fi
fi
if [[ -f "$OUTPUT_DIR/android/android-apk-release-check.txt" ]]; then
	printf 'android_release_check_log_sha256=%s\n' "$(sha256_file "$OUTPUT_DIR/android/android-apk-release-check.txt")" >> "$OUTPUT_DIR/MANIFEST.txt"
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

Promotable: $PROMOTABLE_TEXT
Promotion blockers: $PROMOTION_BLOCKERS_TEXT
EOF
