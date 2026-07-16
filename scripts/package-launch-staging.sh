#!/usr/bin/env bash
# Package a production-like launch staging bundle.

set -euo pipefail
umask 022

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT/dist/launch-staging"
GAME_HOST=""
GAME_PORT="43596"
WS_PORT="43496"
PORTAL_URL=""
WEB_URL=""
WS_URL=""
ANDROID_PLAY_URL=""
LAUNCH_AT="2026-07-18T18:00:00Z"
SERVER_PRESET="$ROOT/server/preservation.conf"
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
SOURCE_DIRTY=0
INITIAL_SOURCE_STATUS=""
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
  - a production-like local.launch-staging.conf with command lockdown on
  - the headless-player runtime, systemd units, and operator runbooks for /opt/voidscape
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
  --android-play-url URL   Optional canonical Google Play details URL for
                           com.voidscape.gg. Leave unset until the listing is public.
  --launch-at ISO          Launch timestamp. Default: 2026-07-18T18:00:00Z.
  --server-preset FILE     Secret-free base config for generated local.launch-staging.conf.
                           The reviewed launch contract is applied afterward.
                           Default: server/preservation.conf.
  --output-dir DIR         Bundle output directory. Default: dist/launch-staging.
  --skip-build             Reuse existing server/client/launcher jars.
  --skip-web-build         Reuse existing Web_Client_TeaVM/target/teavm.
  --skip-android           Do not rebuild/copy the Android APK.
  --android-release        Build/copy a signed release APK instead of debug.
                           Requires Android signing config.
  --allow-dirty-staging    Permit an explicitly non-promotable rehearsal bundle
                           with dirty source, reused builds, debug Android, or
                           temporary endpoint rewriting.
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
		--android-play-url)
			ANDROID_PLAY_URL="${2:-}"
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

canonical_android_play_url() {
	local configured="$1"
	python3 - "$configured" <<'PY'
import sys
from urllib.parse import parse_qs, urlsplit

text = sys.argv[1].strip()
try:
    parsed = urlsplit(text)
    ids = parse_qs(parsed.query, keep_blank_values=True).get("id", [])
    valid = (
        parsed.scheme == "https"
        and parsed.hostname == "play.google.com"
        and parsed.port in (None, 443)
        and parsed.username is None
        and parsed.password is None
        and parsed.path.rstrip("/") == "/store/apps/details"
        and len(ids) == 1
        and ids[0] == "com.voidscape.gg"
    )
except (TypeError, ValueError):
    valid = False
if not valid:
    raise SystemExit(1)
print("https://play.google.com/store/apps/details?id=com.voidscape.gg", end="")
PY
}

if [[ -n "$ANDROID_PLAY_URL" ]]; then
	if ! ANDROID_PLAY_URL="$(canonical_android_play_url "$ANDROID_PLAY_URL")"; then
		echo "ERROR: --android-play-url must be the official com.voidscape.gg Google Play details URL." >&2
		exit 1
	fi
fi

mark_non_promotable() {
	local reason="$1"
	local existing=""
	for existing in "${PROMOTION_BLOCKERS[@]:-}"; do
		[[ "$existing" == "$reason" ]] && return
	done
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
	for reason in "${PROMOTION_BLOCKERS[@]}"; do
		printf '%s%s' "$separator" "$reason"
		separator=","
	done
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
INITIAL_SOURCE_STATUS="$(git -C "$ROOT" status --porcelain=v1 --untracked-files=normal)"
if [[ -n "$INITIAL_SOURCE_STATUS" ]]; then
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

if [[ "$SKIP_ANDROID" -eq 0 ]]; then
	if ! python3 - "$ANDROID_CONFIG" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL/portal?auth=register" "$PORTAL_URL/portal?auth=recovery" "$PORTAL_URL/data-deletion" <<'PY'
import re
import sys
from pathlib import Path

path, host, port, account_url, recovery_url, deletion_url = sys.argv[1:]
expected = {
    "VOIDSCAPE_PUBLIC_HOST": host,
    "VOIDSCAPE_DEFAULT_PORT": port,
    "VOIDSCAPE_PORTAL_ACCOUNT_URL": account_url,
    "VOIDSCAPE_PORTAL_RECOVERY_URL": recovery_url,
    "VOIDSCAPE_PORTAL_DELETION_URL": deletion_url,
}
try:
    text = Path(path).read_text(encoding="utf-8")
except OSError as exc:
    raise SystemExit(f"cannot read Android endpoint contract {path}: {exc}") from exc
for key, value in expected.items():
    matches = re.findall(
        rf'public static final String {re.escape(key)} = "([^"]*)";',
        text,
    )
    if matches != [value]:
        raise SystemExit(1)
PY
	then
		ANDROID_ENDPOINT_REWRITTEN=1
		mark_non_promotable "android_endpoint_rewrite"
		if [[ "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
			echo "ERROR: target Android endpoint differs from committed osConfig.java; temporary rewriting is rehearsal-only." >&2
			exit 1
		fi
	fi
fi

if [[ "$BUNDLE_PROMOTABLE" -ne 1 && "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
	echo "ERROR: requested package would be non-promotable ($(promotion_blockers_text))." >&2
	echo "Commit and rebuild the release inputs, or pass --allow-dirty-staging only for an isolated rehearsal." >&2
	exit 1
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

normalize_public_tree_modes() {
	local target="$1"
	find "$target" -type d -exec chmod 0755 {} +
	find "$target" -type f -exec chmod 0644 {} +
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

bundle_copy_tracked_tree() {
	local repo_relative="$1"
	local target="$2"
	local tracked=""
	local relative=""
	rm -rf "$target"
	mkdir -p "$target"
	while IFS= read -r -d '' tracked; do
		relative="${tracked#"$repo_relative"/}"
		if [[ "$relative" == "$tracked" || -z "$relative" ]]; then
			echo "ERROR: invalid tracked release path below $repo_relative: $tracked" >&2
			exit 1
		fi
		if [[ -L "$ROOT/$tracked" || ! -f "$ROOT/$tracked" ]]; then
			echo "ERROR: release tree only permits tracked regular files: $tracked" >&2
			exit 1
		fi
		mkdir -p "$target/$(dirname "$relative")"
		cp -p "$ROOT/$tracked" "$target/$relative"
	done < <(git -C "$ROOT" ls-files -z -- "$repo_relative")
}

bundle_copy_client_cache() {
	local source="$1"
	local target="$2"
	rm -rf "$target"
	mkdir -p "$target"
	python3 - "$source" "$target" <<'PY'
import shutil
import sys
from pathlib import Path, PurePosixPath

source = Path(sys.argv[1])
target = Path(sys.argv[2])
manifest = source / "MD5.SUM"
for line in manifest.read_text(encoding="utf-8").splitlines():
    try:
        _digest, relative_text = line.split(" *./", 1)
    except ValueError as exc:
        raise SystemExit(f"ERROR: malformed client cache manifest row: {line!r}") from exc
    if relative_text == "Open_RSC_Client.jar":
        continue
    relative = PurePosixPath(relative_text)
    if relative.is_absolute() or not relative.parts or ".." in relative.parts:
        raise SystemExit(f"ERROR: unsafe client cache manifest path: {relative_text!r}")
    source_file = source.joinpath(*relative.parts)
    target_file = target.joinpath(*relative.parts)
    if source_file.is_symlink() or not source_file.is_file():
        raise SystemExit(f"ERROR: client cache manifest file is missing: {source_file}")
    target_file.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_file, target_file)
shutil.copy2(manifest, target / "MD5.SUM")
PY
}

if [[ -L "$OUTPUT_DIR" ]]; then
	echo "ERROR: --output-dir must not be a symlink: $OUTPUT_DIR" >&2
	exit 1
fi
if ! OUTPUT_DIR="$(python3 - "$ROOT" "$OUTPUT_DIR" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1]).resolve()
raw = Path(sys.argv[2])
candidate = raw if raw.is_absolute() else root / raw
if candidate.is_symlink():
    raise SystemExit("ERROR: --output-dir must not be a symlink")
resolved = candidate.resolve(strict=False)
allowed = [(root / "dist").resolve(strict=False), (root / "tmp").resolve(strict=False)]
if not any(resolved != parent and parent in resolved.parents for parent in allowed):
    raise SystemExit("ERROR: --output-dir must be a child of this worktree's dist/ or tmp/ directory")
print(resolved, end="")
PY
)"; then
	exit 1
fi
mkdir -p "$OUTPUT_DIR"

cd "$ROOT"

if [[ "$SKIP_BUILD" -eq 0 ]]; then
	scripts/build.sh
fi
tests/client-cache-manifest.sh

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"/{server,client,portal,launcher,android,logs,ops/database,ops/nginx}

VERSION="$(client_version)"
CREATED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

echo "==> Packaging server artifacts"
cp "$ROOT/server/core.jar" "$OUTPUT_DIR/server/core.jar"
cp "$ROOT/server/plugins.jar" "$OUTPUT_DIR/server/plugins.jar"
chmod 0644 "$OUTPUT_DIR/server/core.jar" "$OUTPUT_DIR/server/plugins.jar"
helper_tmp="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-portal-helper.XXXXXX")"
(
	cd "$helper_tmp"
	jar xf "$ROOT/server/core.jar" \
		com/openrsc/server/util/BCrypt.class \
		com/openrsc/server/util/rsc/PortalPasswordHasher.class
	jar cf "$OUTPUT_DIR/server/portal-password-helper.jar" com
)
rm -rf "$helper_tmp"
chmod 0644 "$OUTPUT_DIR/server/portal-password-helper.jar"
bundle_copy_tracked_tree "server/database" "$OUTPUT_DIR/server/database"
normalize_public_tree_modes "$OUTPUT_DIR/server/database"
write_sha256_tree "$OUTPUT_DIR/server/database" "$OUTPUT_DIR/server/database.SHA256"
bundle_copy_tracked_tree "server/conf" "$OUTPUT_DIR/server/conf"
normalize_public_tree_modes "$OUTPUT_DIR/server/conf"
write_sha256_tree "$OUTPUT_DIR/server/conf" "$OUTPUT_DIR/server/conf.SHA256"

echo "==> Packaging launcher-served client runtime"
cp "$ROOT/Client_Base/Open_RSC_Client.jar" "$OUTPUT_DIR/client/Open_RSC_Client.jar"
chmod 0644 "$OUTPUT_DIR/client/Open_RSC_Client.jar"
bundle_copy_client_cache "$ROOT/Client_Base/Cache" "$OUTPUT_DIR/client/Cache"
normalize_public_tree_modes "$OUTPUT_DIR/client/Cache"
if find "$OUTPUT_DIR/client/Cache" -type f \( -name '*.bak' -o -name '*.tmp' -o -name '*~' \) -print -quit | grep -q .; then
	echo "ERROR: backup or temporary cache files entered the release bundle." >&2
	exit 1
fi
write_sha256_tree "$OUTPUT_DIR/client/Cache" "$OUTPUT_DIR/client/cache.SHA256"

mkdir -p "$OUTPUT_DIR/ops/verify"
cp "$ROOT/scripts/prepare-production-sqlite-permissions.sh" "$OUTPUT_DIR/ops/database/"
cp "$ROOT/scripts/verify-exact-sha256-tree.py" "$OUTPUT_DIR/ops/verify/"
chmod +x "$OUTPUT_DIR/ops/database/prepare-production-sqlite-permissions.sh"
chmod +x "$OUTPUT_DIR/ops/verify/verify-exact-sha256-tree.py"

python3 scripts/generate-launch-server-config.py \
	"$SERVER_PRESET" \
	"$OUTPUT_DIR/server/local.launch-staging.conf" \
	--client-version "$VERSION" \
	--server-port "$GAME_PORT" \
	--ws-port "$WS_PORT"
cp "$ROOT/scripts/launch-config-contract.json" \
	"$OUTPUT_DIR/server/launch-config-contract.json"

echo "==> Packaging headless-player runtime"
mkdir -p \
	"$OUTPUT_DIR/scripts" \
	"$OUTPUT_DIR/tools/voidbot" \
	"$OUTPUT_DIR/tools/headless_players" \
	"$OUTPUT_DIR/Deployment_Scripts/systemd" \
	"$OUTPUT_DIR/docs/subsystems"
for name in \
	headless-player-helper.py \
	headless-players.sh \
	launch-config-contract.json \
	provision-headless-players.sh \
	run-headless-controller.sh \
	run-headless-player.sh \
	smoke-web-teavm-iphone.sh \
	verify-launch-staging.mjs \
	verify-web-teavm-deployment.sh
do
	cp -p "$ROOT/scripts/$name" "$OUTPUT_DIR/scripts/$name"
done
chmod +x \
	"$OUTPUT_DIR/scripts/smoke-web-teavm-iphone.sh" \
	"$OUTPUT_DIR/scripts/verify-launch-staging.mjs" \
	"$OUTPUT_DIR/scripts/verify-web-teavm-deployment.sh"
write_sha256_tree "$OUTPUT_DIR/scripts" "$OUTPUT_DIR/scripts.SHA256"
for name in cli.py protocol.py voidbot voidbotd.py; do
	cp -p "$ROOT/tools/voidbot/$name" "$OUTPUT_DIR/tools/voidbot/$name"
done
for name in controller.py roster.json; do
	cp -p "$ROOT/tools/headless_players/$name" "$OUTPUT_DIR/tools/headless_players/$name"
done
for name in \
	voidscape-headless.target \
	voidscape-headless@.service \
	voidscape-headless-controller.service
do
	cp -p "$ROOT/Deployment_Scripts/systemd/$name" \
		"$OUTPUT_DIR/Deployment_Scripts/systemd/$name"
done
cat > "$OUTPUT_DIR/Deployment_Scripts/systemd/voidscape-headless.env" <<EOF
HEADLESS_SERVER_CONFIG=/opt/voidscape/server/local.conf
HEADLESS_GAME_PORT=$GAME_PORT
HEADLESS_DEFS_DIR=/opt/voidscape/server/conf/server/defs
VOIDBOT_CLIENT_VERSION=$VERSION
EOF
cp -p "$ROOT/docs/subsystems/headless-players.md" \
	"$OUTPUT_DIR/docs/subsystems/headless-players.md"
cp -p "$ROOT/docs/OPERATIONS.md" "$OUTPUT_DIR/docs/OPERATIONS.md"

cat > "$OUTPUT_DIR/ops/nginx/voidscape-admin-public-block.location.conf" <<'EOF'
# Include this fragment inside every internet-facing Voidscape server block.
# Portal maintenance routes stay reachable only through the loopback backend.
location ^~ /api/admin/ {
	return 404;
}
EOF
cat > "$OUTPUT_DIR/ops/nginx/README.md" <<'EOF'
# Public admin-route boundary

Install `voidscape-admin-public-block.location.conf` below
`/etc/nginx/snippets/` and include it inside every public TLS server block that
proxies to the portal. For the known production layout, that includes both the
`voidscape.gg www.voidscape.gg` block and the launch-period
`https://voidscape.5.161.114.251.sslip.io/` block. Keep the portal backend bound to
`127.0.0.1:8788`.

After `nginx -t`, reload nginx and require HTTP 404 from all three public
origins, both without a token and with a fake `x-portal-admin-token` header.
Do not send a real maintenance token to a public URL.
EOF
chmod 0644 "$OUTPUT_DIR/ops/nginx/"*

echo "==> Packaging portal"
bundle_copy_tracked_tree "web/portal" "$OUTPUT_DIR/portal/web-portal"
normalize_public_tree_modes "$OUTPUT_DIR/portal/web-portal"
rm -f "$OUTPUT_DIR/portal/web-portal/build-meta.json"
cat > "$OUTPUT_DIR/portal/portal.env.staging" <<EOF
PORT=8788
PORTAL_BIND_HOST=127.0.0.1
PORTAL_PUBLIC_MODE=1
PORTAL_LAUNCH_SIGNUP_MODE=1
PORTAL_LAUNCH_AT=$LAUNCH_AT
PORTAL_WEB_CLIENT_URL=$WEB_URL
PORTAL_ANDROID_PLAY_URL=$ANDROID_PLAY_URL
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
PORTAL_ABUSE_HASH_SALT=CHANGE_ME_STABLE_PRIVATE_SECRET
# PORTAL_ADMIN_TOKEN=CHANGE_ME_LONG_RANDOM_SECRET
# PORTAL_ANDROID_APK overrides the APK served by /downloads/android-apk.
# Use the packaged release APK path for production if direct Android download is live.
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
normalize_public_tree_modes "$OUTPUT_DIR/play"

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
chmod 0644 "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar" \
	"$OUTPUT_DIR/launcher/voidscape-launcher.properties"
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
	python3 - "$ANDROID_CONFIG" "$GAME_HOST" "$GAME_PORT" "$PORTAL_URL/portal?auth=register" "$PORTAL_URL/portal?auth=recovery" "$PORTAL_URL/data-deletion" <<'PY'
import re
import sys
from pathlib import Path

path, host, port, account_url, recovery_url, deletion_url = sys.argv[1:]
text = Path(path).read_text()
replacements = {
    "VOIDSCAPE_PUBLIC_HOST": host,
    "VOIDSCAPE_DEFAULT_PORT": port,
    "VOIDSCAPE_PORTAL_ACCOUNT_URL": account_url,
    "VOIDSCAPE_PORTAL_RECOVERY_URL": recovery_url,
    "VOIDSCAPE_PORTAL_DELETION_URL": deletion_url,
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
			echo "ERROR: target Android endpoints differ from committed osConfig.java; temporary rewriting is rehearsal-only." >&2
			exit 1
		fi
	fi
	if [[ "$ANDROID_RELEASE" -eq 1 ]]; then
		if [[ "$BUNDLE_PROMOTABLE" -eq 1 ]]; then
			scripts/build-android.sh --release
		else
			VOIDSCAPE_ANDROID_ALLOW_DIRTY_RELEASE=1 scripts/build-android.sh --release
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
		chmod 0644 "$ANDROID_BUNDLE_APK"
		if [[ ! -f "$ANDROID_APK.json" ]]; then
			echo "ERROR: release APK metadata sidecar is missing: $ANDROID_APK.json" >&2
			exit 1
		fi
		cp "$ANDROID_APK.json" "$ANDROID_BUNDLE_META"
		chmod 0644 "$ANDROID_BUNDLE_META"
		if [[ -z "${VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256:-}" ]]; then
			echo "ERROR: VOIDSCAPE_ANDROID_EXPECTED_SIGNER_SHA256 is required for a promotable APK." >&2
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
			echo "ERROR: Android release checker failed; no public APK alias was created." >&2
			exit 1
		fi
		ANDROID_RELEASE_CHECK="passed"
	else
		mkdir -p "$OUTPUT_DIR/android/rehearsal-only"
		ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/rehearsal-only/voidscape-staging-$ANDROID_LABEL.apk"
		ANDROID_BUNDLE_META="$ANDROID_BUNDLE_APK.json"
		cp "$ANDROID_APK" "$ANDROID_BUNDLE_APK"
		chmod 0644 "$ANDROID_BUNDLE_APK"
		if [[ -f "$ANDROID_APK.json" ]]; then
			cp "$ANDROID_APK.json" "$ANDROID_BUNDLE_META"
			chmod 0644 "$ANDROID_BUNDLE_META"
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

FINAL_SOURCE_STATUS="$(git -C "$ROOT" status --porcelain=v1 --untracked-files=normal)"
if [[ "$FINAL_SOURCE_STATUS" != "$INITIAL_SOURCE_STATUS" ]]; then
	SOURCE_DIRTY=1
	mark_non_promotable "source_state_changed_during_packaging"
	if [[ "$ALLOW_DIRTY_STAGING" -ne 1 ]]; then
		echo "ERROR: Git source state changed during packaging; review tracked and untracked inputs before promotion." >&2
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

python3 - "$OUTPUT_DIR/portal/web-portal/build-meta.json" "$FULL_COMMIT" "$SOURCE_DIRTY_TEXT" "$CREATED_AT" <<'PY'
import json
import re
import sys
from pathlib import Path

target, commit, dirty_text, generated_at = sys.argv[1:]
if not re.fullmatch(r"[0-9a-f]{40}", commit):
    raise SystemExit("ERROR: portal build metadata requires a full 40-hex commit")
Path(target).write_text(json.dumps({
	"status": "publication_pending",
    "sourcePublicationStatus": "publication_pending",
	"repositoryUrl": "",
    "sourceRepositoryUrl": "",
	"commit": "",
	"shortCommit": "",
    "sourceCommit": "",
	"branch": "",
	"dirty": dirty_text == "true",
    "sourceDirty": dirty_text == "true",
    "generatedAt": generated_at,
}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
chmod 0644 "$OUTPUT_DIR/portal/web-portal/build-meta.json"
write_sha256_tree "$OUTPUT_DIR/portal/web-portal" "$OUTPUT_DIR/portal/web-portal.SHA256"

if [[ "$BUNDLE_PROMOTABLE" -eq 1 && "$ANDROID_RELEASE" -eq 1 ]]; then
	mv "$ANDROID_BUNDLE_APK" "$OUTPUT_DIR/android/voidscape-staging.apk"
	mv "$ANDROID_BUNDLE_META" "$OUTPUT_DIR/android/voidscape-staging.apk.json"
	ANDROID_BUNDLE_APK="$OUTPUT_DIR/android/voidscape-staging.apk"
	ANDROID_BUNDLE_META="$OUTPUT_DIR/android/voidscape-staging.apk.json"
	rmdir "$OUTPUT_DIR/android/candidate"
	ANDROID_APK_PROMOTABLE=1
	cat > "$OUTPUT_DIR/android/README.md" <<'EOF'
# Android direct-download release APK

The root APK exists only because the bundle is promotable and the signed APK
release checker passed. Rerun that checker immediately before publication.
EOF
elif [[ -n "$ANDROID_BUNDLE_APK" ]]; then
	cat > "$OUTPUT_DIR/android/README.md" <<EOF
# REHEARSAL-ONLY Android APK

This bundle is not promotable: \`$PROMOTION_BLOCKERS_TEXT\`. The APK is isolated
under \`android/rehearsal-only/\`; do not publish or upload it.
EOF
else
	cat > "$OUTPUT_DIR/android/README.md" <<'EOF'
# Android APK not included

No direct-download APK is present. Google Play AAB promotion uses its separate
signed release gate.
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
DEPLOYED_SERVER_DIR="\${VOIDSCAPE_DEPLOYED_SERVER_DIR:-/opt/voidscape/server}"
DEPLOYED_CLIENT_DIR="\${VOIDSCAPE_DEPLOYED_CLIENT_DIR:-/opt/voidscape/client}"
DEPLOYED_LAUNCHER_JAR="\${VOIDSCAPE_DEPLOYED_LAUNCHER_JAR:-/opt/voidscape/launcher/VoidscapeLauncher-staging.jar}"
DEPLOYED_PORTAL_DIR="\${VOIDSCAPE_DEPLOYED_PORTAL_DIR:-/opt/voidscape/web/portal}"
verify_exact_sha256_tree() {
	local deployed_root="\$1"
	local expected_manifest="\$2"
	"\$BUNDLE_DIR/ops/verify/verify-exact-sha256-tree.py" \
		"\$deployed_root" "\$expected_manifest"
}
	verify_exact_sha256_tree "\$BUNDLE_DIR/scripts" "\$BUNDLE_DIR/scripts.SHA256"
	for pair in \
		"\$BUNDLE_DIR/server/core.jar:\$DEPLOYED_SERVER_DIR/core.jar" \
		"\$BUNDLE_DIR/server/plugins.jar:\$DEPLOYED_SERVER_DIR/plugins.jar" \
		"\$BUNDLE_DIR/server/portal-password-helper.jar:\$DEPLOYED_SERVER_DIR/portal-password-helper.jar" \
		"\$BUNDLE_DIR/client/Open_RSC_Client.jar:\$DEPLOYED_CLIENT_DIR/Open_RSC_Client.jar" \
		"\$BUNDLE_DIR/launcher/VoidscapeLauncher-staging.jar:\$DEPLOYED_LAUNCHER_JAR"
	do
		expected="\${pair%%:*}"
		actual="\${pair#*:}"
		if [[ ! -f "\$actual" ]] || ! cmp -s "\$expected" "\$actual"; then
			echo "ERROR: deployed artifact bytes differ: \$actual" >&2
			exit 1
		fi
	done
	verify_exact_sha256_tree "\$DEPLOYED_SERVER_DIR/conf" "\$BUNDLE_DIR/server/conf.SHA256"
	verify_exact_sha256_tree "\$DEPLOYED_SERVER_DIR/database" "\$BUNDLE_DIR/server/database.SHA256"
	verify_exact_sha256_tree "\$DEPLOYED_CLIENT_DIR/Cache" "\$BUNDLE_DIR/client/cache.SHA256"
	verify_exact_sha256_tree "\$DEPLOYED_PORTAL_DIR" "\$BUNDLE_DIR/portal/web-portal.SHA256"
if [[ -z "\${VOIDSCAPE_DEPLOYED_SERVER_CONFIG:-}" ]]; then
	echo "ERROR: set VOIDSCAPE_DEPLOYED_SERVER_CONFIG to a copy of the actual deployed local.conf" >&2
	exit 2
fi
if [[ ! -f "\$VOIDSCAPE_DEPLOYED_SERVER_CONFIG" ]]; then
	echo "ERROR: deployed server config copy not found: \$VOIDSCAPE_DEPLOYED_SERVER_CONFIG" >&2
	exit 2
fi
if [[ -z "\${VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG:-}" ]]; then
	echo "ERROR: set VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG to a copy of the actual deployed connections.conf" >&2
	exit 2
fi
if [[ ! -f "\$VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG" ]]; then
	echo "ERROR: deployed connections config copy not found: \$VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG" >&2
	exit 2
fi
if [[ \$# -ne 0 ]]; then
	echo "ERROR: VERIFY-STAGING.sh accepts no positional or option arguments; its fixed release checks cannot be bypassed." >&2
	exit 2
fi
SIGNUP_MODE=(--skip-signup)
if [[ "\${VOIDSCAPE_VERIFY_RUN_SIGNUP:-0}" == "1" ]]; then
	: "\${VOIDSCAPE_VERIFY_SIGNUP_USERNAME:?Set the exact new QA character name}"
	: "\${VOIDSCAPE_VERIFY_SIGNUP_EMAIL:?Set the email address you can verify now}"
	: "\${VOIDSCAPE_VERIFY_SIGNUP_PASSWORD:?Set the exact portal/game password}"
	SIGNUP_MODE=(
		--run-signup
		--signup-username "\$VOIDSCAPE_VERIFY_SIGNUP_USERNAME"
		--signup-email "\$VOIDSCAPE_VERIFY_SIGNUP_EMAIL"
		--signup-password "\$VOIDSCAPE_VERIFY_SIGNUP_PASSWORD"
		--qa-user "\$VOIDSCAPE_VERIFY_SIGNUP_USERNAME"
		--qa-pass "\$VOIDSCAPE_VERIFY_SIGNUP_PASSWORD"
		--verification-timeout-seconds "\${VOIDSCAPE_VERIFY_EMAIL_TIMEOUT_SECONDS:-240}"
		--verification-poll-seconds "\${VOIDSCAPE_VERIFY_EMAIL_POLL_SECONDS:-30}"
	)
fi
ANDROID_VERIFY_MODE=(--expect-no-android-apk)
if [[ -f "\$BUNDLE_DIR/android/voidscape-staging.apk" ]]; then
	ANDROID_VERIFY_MODE=(--expected-android-apk "\$BUNDLE_DIR/android/voidscape-staging.apk")
fi
ANDROID_PLAY_VERIFY_MODE=(--expect-no-android-play)
if [[ -n "$ANDROID_PLAY_URL" ]]; then
	ANDROID_PLAY_VERIFY_MODE=(--expected-android-play-url "$ANDROID_PLAY_URL")
fi
"\$BUNDLE_DIR/scripts/verify-launch-staging.mjs" \\
  --portal-url "$PORTAL_URL/" \\
  --web-url "$WEB_URL" \\
  --ws "$WS_URL" \\
  --launch-at "$LAUNCH_AT" \\
  --expected-build-manifest "\$BUNDLE_DIR/play/voidscape-web-build.json" \\
  --server-config "\$VOIDSCAPE_DEPLOYED_SERVER_CONFIG" \\
  --connections-config "\$VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG" \\
  --expected-client-version "$VERSION" \\
  --out "\$BUNDLE_DIR/logs/verify-staging-\$(date -u +%Y%m%dT%H%M%SZ)" \\
	  "\${ANDROID_VERIFY_MODE[@]}" \\
	  "\${ANDROID_PLAY_VERIFY_MODE[@]}" \\
  "\${SIGNUP_MODE[@]}"
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

- \`server/core.jar\`, \`server/plugins.jar\`, and the isolated \`server/portal-password-helper.jar\`
- \`server/database/\`
- \`server/conf/\`
- \`client/Open_RSC_Client.jar\` and the exact manifest-backed \`client/Cache/\`
- \`server/local.launch-staging.conf\`
- \`server/launch-config-contract.json\`
- \`scripts/\`, \`tools/voidbot/\`, and \`tools/headless_players/\` fleet runtime files
- \`scripts.SHA256\`, binding the hosted gate to the packaged verifier scripts
- \`Deployment_Scripts/systemd/voidscape-headless*\` fleet units and generated non-secret environment
- \`docs/subsystems/headless-players.md\` and \`docs/OPERATIONS.md\` operator runbooks
- \`portal/web-portal/\`
- \`portal/web-portal.SHA256\`
- \`portal/portal.env.staging\`
- \`play/\`
- \`launcher/VoidscapeLauncher-staging.jar\`
- \`android/voidscape-staging.apk\` when Android packaging was enabled
- \`ops/database/prepare-production-sqlite-permissions.sh\` and
  \`ops/verify/verify-exact-sha256-tree.py\` release guards

## Host Placement

Suggested single-host layout:

\`\`\`text
/opt/voidscape/server/core.jar
/opt/voidscape/server/plugins.jar
/opt/voidscape/server/portal-password-helper.jar
/opt/voidscape/server/database/
/opt/voidscape/server/conf/
/opt/voidscape/server/inc/sqlite/voidscape.db
/opt/voidscape/server/local.conf
/opt/voidscape/client/Open_RSC_Client.jar
/opt/voidscape/client/Cache/
/opt/voidscape/launcher/VoidscapeLauncher-staging.jar
/opt/voidscape/scripts/
/opt/voidscape/tools/voidbot/
/opt/voidscape/tools/headless_players/
/opt/voidscape/docs/subsystems/headless-players.md
/opt/voidscape/docs/OPERATIONS.md
/opt/voidscape/web/portal/
/etc/voidscape/portal.env
/var/www/html/play/
/var/www/html/voidscape/
/etc/systemd/system/voidscape-headless.target
/etc/systemd/system/voidscape-headless@.service
/etc/systemd/system/voidscape-headless-controller.service
/etc/voidscape/headless.env
/var/lib/voidscape-portal/
\`\`\`

Copy \`server/local.launch-staging.conf\` to the deployed server's \`local.conf\`,
or merge every value in \`server/launch-config-contract.json\` plus this bundle's
\`client_version: $VERSION\`, \`server_port: $GAME_PORT\`, and
\`ws_server_port: $WS_PORT\` into the real private config. The hosted verifier
rejects a missing, changed, or duplicate contract key.

Copy the bundle's \`scripts/\`, \`tools/\`, \`docs/\`, \`server/conf/\`,
\`server/database/\`, \`client/\`, and \`launcher/\` trees to the same paths
below \`/opt/voidscape\`.
Before installing or starting the fleet, create its ten static nologin service users
with primary group \`voidscape\` (the unit does not use \`DynamicUser=\`):

\`\`\`bash
set -euo pipefail
voidscape_gid="\$(getent group voidscape | cut -d: -f3)"
uid_min="\$(awk '\$1 == "UID_MIN" { print \$2; exit }' /etc/login.defs)"
test -n "\$voidscape_gid"
test -n "\$uid_min"
for profile in fireee ch0p ultraz vinny six-seven college pknskate p-h-i-s-h fulani az; do
  account="voidscape-headless-\$profile"
  if ! getent passwd "\$account" >/dev/null; then
    useradd --system --no-create-home --home-dir /nonexistent \\
      --shell /usr/sbin/nologin --gid voidscape "\$account"
  fi
  IFS=: read -r name _ uid gid _ home shell < <(getent passwd "\$account")
  test "\$name" = "\$account"
  test "\$uid" -gt 0
  test "\$uid" -lt "\$uid_min"
  test "\$gid" = "\$voidscape_gid"
  test "\$home" = /nonexistent
  test "\$shell" = /usr/sbin/nologin
  test "\$(id -nG "\$account")" = voidscape
done
install -d -o root -g voidscape -m 0710 /etc/voidscape
install -d -o root -g root -m 0700 /etc/voidscape/headless-players
\`\`\`

Install the three \`voidscape-headless*\` unit files from
\`Deployment_Scripts/systemd/\` into \`/etc/systemd/system/\`. Install the generated
\`Deployment_Scripts/systemd/voidscape-headless.env\` as root-owned mode 0644
\`/etc/voidscape/headless.env\`, run \`systemctl daemon-reload\`, and verify both
service definitions still set
\`LimitCORE=0\`. Provisioning is deliberately SQLite-only and must fail closed on
another active store; follow the packaged \`docs/subsystems/headless-players.md\`.
The fleet target never starts a game-server service. For a non-default isolated
world, create the documented non-secret \`/etc/voidscape/headless.env\` with its
active config path, game port, definitions path, and exact client version before
starting the target.
Do not start the target until the ordinary accounts and root-owned mode-0600
credential/receipt files exist and a database-paired encrypted off-host backup has
been created by the packaged \`docs/OPERATIONS.md\` procedure. Credentials are
intentionally never bundled, and no plaintext credential archive is permitted.

For this packaged world, provision only with every selected-world input explicit:

\`\`\`bash
cd /opt/voidscape
HEADLESS_CREDENTIAL_DIR=/etc/voidscape/headless-players \\
HEADLESS_SERVER_CONFIG=/opt/voidscape/server/local.conf \\
HEADLESS_CONNECTIONS_CONFIG=/opt/voidscape/server/connections.conf \\
HEADLESS_DATABASE=/opt/voidscape/server/inc/sqlite/voidscape.db \\
HEADLESS_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/portal-password-helper.jar \\
HEADLESS_GAME_HOST=127.0.0.1 \\
HEADLESS_GAME_PORT=$GAME_PORT \\
VOIDBOT_CLIENT_VERSION=$VERSION \\
  scripts/provision-headless-players.sh
\`\`\`

Copy \`portal/portal.env.staging\` to the host, replace the two
\`CHANGE_ME\` values, and keep \`PORTAL_GOOGLE_CLIENT_ID\` unset unless Google
is intentionally being tested. Verified signup and password recovery require
working Resend delivery; use \`PORTAL_EMAIL_DRY_RUN=1\` only for private staging.

## Verify

After services restart and HTTPS/WSS are reachable, point the gate at copies of
the configs the running server actually loaded. Verification is non-mutating by
default; set \`VOIDSCAPE_VERIFY_RUN_SIGNUP=1\` only for an isolated staging DB when
one real staged account and character should be created:

\`\`\`bash
cd $OUTPUT_DIR
VOIDSCAPE_DEPLOYED_SERVER_CONFIG=/path/to/deployed-local.conf \\
VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG=/path/to/deployed-connections.conf \\
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
- Confirm \`PORTAL_EMAIL_VERIFICATION_REQUIRED=1\`, \`PORTAL_REQUIRE_EMAIL=1\`, \`PORTAL_EMAIL_PROVIDER=resend\`, \`PORTAL_RESEND_API_KEY\`, and \`PORTAL_PUBLIC_ORIGIN\` are set, then check \`/api/health.config.email.configured\` and \`.verificationRequired\`.
- Confirm \`PORTAL_GAME_PASSWORD_HELPER_CLASSPATH=/opt/voidscape/server/portal-password-helper.jar\` and that the packaged helper jar passes its stdin check before enabling Character Manager password changes or older native-account claims.
- Simulated world activity is out of scope for this no-player candidate: leave all
  \`voidscape-headless-*\` identities and units uninstalled or disabled. The bundled
  fleet remains QA tooling only. If it is ever approved as a launch feature, treat
  its identity, unit, and encrypted credential checks in \`DEPLOYMENT.md\` as a
  separate gated change.
- If Android is public, prefer a release APK bundle, confirm \`MANIFEST.txt\` says \`android_apk_type=release\`, and set \`PORTAL_ANDROID_APK\` to that APK if it lives outside the default build path.

## Backup Paths

Record these before overwriting live files:

\`\`\`text
Portal backup:
Play/web-client backup:
Server jar/config backup:
Database backup:
Headless credential/receipt backup: N/A unless simulated activity is separately approved
Launcher/APK public-download backup:
\`\`\`

Suggested single-host backup commands:

\`\`\`bash
set -euo pipefail
stamp="\$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p /opt/voidscape/backups
tar -czf "/opt/voidscape/backups/portal-prelaunch-\$stamp.tgz" -C /opt/voidscape/web portal
install -m 600 /etc/voidscape/portal.env "/opt/voidscape/backups/portal.env-prelaunch-\$stamp"
tar -czf "/opt/voidscape/backups/portal-state-prelaunch-\$stamp.tgz" -C /var/lib voidscape-portal
tar -czf "/opt/voidscape/backups/public-web-prelaunch-\$stamp.tgz" -C /var/www/html play voidscape
tar -czf "/opt/voidscape/backups/server-prelaunch-\$stamp.tgz" -C /opt/voidscape server/core.jar server/plugins.jar server/portal-password-helper.jar server/conf server/database server/local.conf client launcher
sqlite3 /opt/voidscape/server/inc/sqlite/voidscape.db ".backup '/opt/voidscape/backups/voidscape-\$stamp.sqlite'"
\`\`\`

If production is MariaDB, use the matching dump/restore commands from
\`docs/OPERATIONS.md\` instead of the SQLite backup line.

## Deploy

- Copy \`server/core.jar\`, \`server/plugins.jar\`, \`server/portal-password-helper.jar\`, and the release-critical config values into the live server path.
- Create the dedicated \`voidscape-db\` group, add only the shared game/portal
  service identity \`voidscape\`, and keep every simulated-player identity out.

\`\`\`bash
getent group voidscape-db >/dev/null || groupadd --system voidscape-db
usermod -aG voidscape-db voidscape
id -nG voidscape | tr ' ' '\n' | grep -Fx voidscape-db
\`\`\`

Restart the \`voidscape\` service identity's processes after changing group
membership so both game and portal receive the dedicated supplemental group.
- With both game and portal services stopped, run
  \`ops/database/prepare-production-sqlite-permissions.sh\` to enforce the
  reviewed shared SQLite contract (directory \`root:voidscape-db 0770\`, database
  \`voidscape:voidscape-db 0600\`) before either service starts.
- Copy \`server/conf/\`, \`server/database/\`, \`client/\`, \`scripts/\`, \`tools/voidbot/\`, \`tools/headless_players/\`, and \`docs/\` into the matching paths below \`/opt/voidscape\`.
- Keep the simulated world-activity fleet disabled by default. Install or start
  those accounts and units only when simulated players are an explicit launch
  feature; the bundled fleet remains available as QA tooling.
- Copy \`portal/web-portal/\` to \`/opt/voidscape/web/portal/\`.
- Copy \`play/\` to \`/var/www/html/play/\`.
- Copy \`launcher/VoidscapeLauncher-staging.jar\` and \`launcher/voidscape-launcher.properties\` to \`/opt/voidscape/launcher/\`; publish the launcher only after the deployment gate.
- Copy \`android/voidscape-staging.apk\` to \`/var/www/html/voidscape/\` only when Android direct download should be live; set \`PORTAL_ANDROID_APK\` to that path if it is not the default build output.
- Restart the server, portal, and reverse proxy units.

## Verify

Run this exact bundle verifier after syncing and restarting:

\`\`\`bash
cd <synced-bundle-dir>
VOIDSCAPE_DEPLOYED_SERVER_CONFIG=/path/to/deployed-local.conf \\
VOIDSCAPE_DEPLOYED_CONNECTIONS_CONFIG=/path/to/deployed-connections.conf \\
  ./VERIFY-STAGING.sh
\`\`\`

Then archive this handoff file, \`MANIFEST.txt\`, and the verifier output with
the backup paths above.

## Rollback

Use rollback only after stopping or draining player traffic.

1. Stop or gate the portal and game server; stop the optional headless target too only if it was separately approved and installed.
2. Restore the previous portal directory from the portal backup.
3. Restore the previous \`/play/\` directory from the web-client backup.
4. Restore previous server jars and config from the server backup.
5. Restore the database backup only if the failed release changed persistent state or corrupted accounts.
6. Restart the game/portal services and rerun the non-mutating health checks:
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

if find "$OUTPUT_DIR" -type f \( -name '*.password' -o -name '*.provisioned' \) \
	-print -quit | grep -q .; then
	echo "ERROR: refusing to package headless credential or receipt files" >&2
	exit 1
fi

cat > "$OUTPUT_DIR/MANIFEST.txt" <<EOF
created_at=$CREATED_AT
commit=$FULL_COMMIT
short_commit=$COMMIT
source_dirty=$SOURCE_DIRTY_TEXT
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
android_play_url=$([[ -n "$ANDROID_PLAY_URL" ]] && printf '%s' "$ANDROID_PLAY_URL" || printf '%s' "none")
server_core_sha256=$(sha256_file "$OUTPUT_DIR/server/core.jar")
server_plugins_sha256=$(sha256_file "$OUTPUT_DIR/server/plugins.jar")
server_config_sha256=$(sha256_file "$OUTPUT_DIR/server/local.launch-staging.conf")
server_conf_manifest_sha256=$(sha256_file "$OUTPUT_DIR/server/conf.SHA256")
server_database_manifest_sha256=$(sha256_file "$OUTPUT_DIR/server/database.SHA256")
server_database_file_count=$(wc -l < "$OUTPUT_DIR/server/database.SHA256" | tr -d '[:space:]')
launch_config_contract_sha256=$(sha256_file "$ROOT/scripts/launch-config-contract.json")
packaged_scripts_manifest_sha256=$(sha256_file "$OUTPUT_DIR/scripts.SHA256")
portal_password_helper_sha256=$(sha256_file "$OUTPUT_DIR/server/portal-password-helper.jar")
headless_controller_sha256=$(sha256_file "$OUTPUT_DIR/tools/headless_players/controller.py")
headless_roster_sha256=$(sha256_file "$OUTPUT_DIR/tools/headless_players/roster.json")
voidbotd_sha256=$(sha256_file "$OUTPUT_DIR/tools/voidbot/voidbotd.py")
headless_target_sha256=$(sha256_file "$OUTPUT_DIR/Deployment_Scripts/systemd/voidscape-headless.target")
headless_env_sha256=$(sha256_file "$OUTPUT_DIR/Deployment_Scripts/systemd/voidscape-headless.env")
headless_runbook_sha256=$(sha256_file "$OUTPUT_DIR/docs/subsystems/headless-players.md")
operations_runbook_sha256=$(sha256_file "$OUTPUT_DIR/docs/OPERATIONS.md")
client_runtime_sha256=$(sha256_file "$OUTPUT_DIR/client/Open_RSC_Client.jar")
client_cache_manifest_sha256=$(sha256_file "$OUTPUT_DIR/client/cache.SHA256")
launcher_sha256=$(sha256_file "$OUTPUT_DIR/launcher/VoidscapeLauncher-staging.jar")
web_build_manifest_sha256=$(sha256_file "$OUTPUT_DIR/play/voidscape-web-build.json")
portal_build_meta_sha256=$(sha256_file "$OUTPUT_DIR/portal/web-portal/build-meta.json")
portal_tree_manifest_sha256=$(sha256_file "$OUTPUT_DIR/portal/web-portal.SHA256")
sqlite_permissions_helper_sha256=$(sha256_file "$OUTPUT_DIR/ops/database/prepare-production-sqlite-permissions.sh")
exact_tree_verifier_sha256=$(sha256_file "$OUTPUT_DIR/ops/verify/verify-exact-sha256-tree.py")
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

if [[ -n "$RSYNC_TARGET" ]]; then
	if [[ "$BUNDLE_PROMOTABLE" -ne 1 ]]; then
		echo "ERROR: refusing to rsync a non-promotable rehearsal bundle: $PROMOTION_BLOCKERS_TEXT" >&2
		exit 1
	fi
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
