#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-launch-staging-guards.XXXXXX")"
PIDS=()

cleanup() {
	if [[ ${#PIDS[@]} -gt 0 ]]; then
		for pid in "${PIDS[@]}"; do
			kill "$pid" >/dev/null 2>&1 || true
			wait "$pid" >/dev/null 2>&1 || true
		done
	fi
	rm -rf "$TMP_DIR"
}
trap cleanup EXIT

client_version="$(awk '/CLIENT_VERSION[[:space:]]*=/ { gsub(/[^0-9]/, "", $0); print; exit }' "$ROOT/Client_Base/src/orsc/Config.java")"

bash -n "$ROOT/scripts/package-launch-staging.sh"
grep -q 'write_sha256_tree "$OUTPUT_DIR/server/database" "$OUTPUT_DIR/server/database.SHA256"' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'server_database_manifest_sha256=' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'server_database_file_count=' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'promotable=$PROMOTABLE_TEXT' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'source_publication_status=publication_pending' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'portal_build_meta_sha256=' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'android_apk_promotable=' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'scripts/check-android-apk-release.sh' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'verify_exact_sha256_tree "\\$DEPLOYED_SERVER_DIR/database"' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'location \^~ /api/admin/' "$ROOT/scripts/package-launch-staging.sh"
grep -q 'https://voidscape.5.161.114.251.sslip.io/' "$ROOT/scripts/package-launch-staging.sh"
grep -q '/var/www/html play voidscape' "$ROOT/scripts/package-launch-staging.sh"
grep -q '/var/lib voidscape-portal' "$ROOT/scripts/package-launch-staging.sh"
grep -q '/etc/voidscape/portal.env' "$ROOT/scripts/package-launch-staging.sh"
if grep -q '/opt/voidscape/web/play' "$ROOT/scripts/package-launch-staging.sh"; then
	echo "stale /opt/voidscape/web/play backup path remains" >&2
	exit 1
fi
if grep -Eq 'legacy block (if|unless)|remove or disable the legacy' "$ROOT/scripts/package-launch-staging.sh"; then
	echo "legacy sslip launch guard was made optional" >&2
	exit 1
fi
node -e '
const metadata = require(process.argv[1]);
if (metadata.status !== "publication_pending") throw new Error("tracked source publication must remain pending");
for (const key of ["repositoryUrl", "commit", "shortCommit", "branch"]) {
	if (metadata[key] !== "") throw new Error(`pending source metadata leaked ${key}`);
}
' "$ROOT/web/portal/build-meta.json"
if rg -n 'github\.com/voidscape-gg/voidscape|game code is public|public code version' \
	"$ROOT/web/portal/transparency.html" "$ROOT/web/portal/transparency.js"; then
	echo "transparency page claims an unpublished source mirror" >&2
	exit 1
fi

start_fixture() {
	local admin_status="$1"
	local name="$2"
	local port_file="$TMP_DIR/$name.port"
	node "$ROOT/tests/fixtures/launch-staging-guard-server.mjs" "$port_file" "$admin_status" "$client_version" \
		>"$TMP_DIR/$name.stdout" 2>"$TMP_DIR/$name.stderr" &
	local pid=$!
	PIDS+=("$pid")
	for _ in $(seq 1 100); do
		if [[ -s "$port_file" ]]; then
			FIXTURE_BASE="http://127.0.0.1:$(cat "$port_file")/"
			return
		fi
		if ! kill -0 "$pid" >/dev/null 2>&1; then
			cat "$TMP_DIR/$name.stderr" >&2
			exit 1
		fi
		sleep 0.05
	done
	echo "fixture $name did not start" >&2
	exit 1
}

expect_pass() {
	local label="$1"
	shift
	if ! "$@" >"$TMP_DIR/$label.log" 2>&1; then
		cat "$TMP_DIR/$label.log" >&2
		echo "expected pass: $label" >&2
		exit 1
	fi
}

expect_fail() {
	local label="$1"
	shift
	if "$@" >"$TMP_DIR/$label.log" 2>&1; then
		cat "$TMP_DIR/$label.log" >&2
		echo "expected failure: $label" >&2
		exit 1
	fi
}

# Exercise package promotability decisions from a genuinely clean Git worktree.
promo_root="$TMP_DIR/promotability-repo"
mkdir -p "$promo_root/scripts" "$promo_root/server" \
	"$promo_root/Android_Client/Open RSC Android Client/src/main/java/orsc"
cp "$ROOT/scripts/package-launch-staging.sh" "$promo_root/scripts/package-launch-staging.sh"
cat > "$promo_root/server/test.conf" <<'EOF'
	client_version: 1
EOF
cat > "$promo_root/Android_Client/Open RSC Android Client/src/main/java/orsc/osConfig.java" <<'EOF'
package orsc;
public final class osConfig {
	public static final String VOIDSCAPE_PUBLIC_HOST = "old.example";
	public static final String VOIDSCAPE_DEFAULT_PORT = "43596";
	public static final String VOIDSCAPE_PORTAL_ACCOUNT_URL = "https://old.example/portal?auth=login";
	public static final String VOIDSCAPE_PORTAL_RECOVERY_URL = "https://old.example/portal?auth=recovery";
}
EOF
git -C "$promo_root" init -q
git -C "$promo_root" config user.email fixture@voidscape.invalid
git -C "$promo_root" config user.name "Voidscape Fixture"
git -C "$promo_root" add .
git -C "$promo_root" commit -qm fixture

package_common=(
	"$promo_root/scripts/package-launch-staging.sh"
	--host voidscape.gg
	--portal-url https://voidscape.gg/
	--web-url https://voidscape.gg/play/
	--server-preset "$promo_root/server/test.conf"
)
expect_fail clean-reused-builds-rejected "${package_common[@]}" \
	--skip-build --skip-web-build --skip-android
grep -q 'server_client_build_reused,web_build_reused' "$TMP_DIR/clean-reused-builds-rejected.log"

expect_fail clean-debug-android-rejected "${package_common[@]}"
grep -q 'android_debug_apk' "$TMP_DIR/clean-debug-android-rejected.log"

expect_fail clean-rewritten-android-rejected "${package_common[@]}" --android-release
if ! grep -q 'target Android endpoint differs' "$TMP_DIR/clean-rewritten-android-rejected.log"; then
	cat "$TMP_DIR/clean-rewritten-android-rejected.log" >&2
	exit 1
fi

expect_fail conflicting-android-options "${package_common[@]}" --skip-android --android-release
grep -q 'mutually exclusive' "$TMP_DIR/conflicting-android-options.log"

start_fixture 404 public404
base_404="$FIXTURE_BASE"
start_fixture 403 backend403
base_403="$FIXTURE_BASE"

common_404=(
	"$ROOT/scripts/verify-launch-staging.mjs"
	--portal-url "$base_404"
	--skip-web-verify
	--skip-server-config
	--allow-http
)
common_403=(
	"$ROOT/scripts/verify-launch-staging.mjs"
	--portal-url "$base_403"
	--skip-web-verify
	--skip-server-config
	--allow-http
)

expect_pass public-404 "${common_404[@]}" --skip-signup --out "$TMP_DIR/public-404"
expect_fail public-403-rejected "${common_403[@]}" --skip-signup --out "$TMP_DIR/public-403-rejected"
grep -q "public admin route externally blocked" "$TMP_DIR/public-403-rejected.log"

expect_pass local-legacy-403 "${common_403[@]}" \
	--allow-local-admin-token-guard \
	--skip-signup \
	--out "$TMP_DIR/local-legacy-403"

expect_fail final-needs-exact-credentials "${common_404[@]}" \
	--run-signup \
	--out "$TMP_DIR/final-needs-exact-credentials"
grep -q "requires explicit --signup-username" "$TMP_DIR/final-needs-exact-credentials.log"

expect_pass pending-rehearsal "${common_404[@]}" \
	--pending-email-rehearsal \
	--signup-username PendingQa \
	--signup-email pending-rehearsal@example.com \
	--signup-password Launchpass1 \
	--out "$TMP_DIR/pending-rehearsal"
node -e '
const summary = require(process.argv[1]);
if (summary.signupMode !== "pending-email-rehearsal") throw new Error("wrong rehearsal mode");
if (!summary.warnings.some((row) => row.name === "non-final signup rehearsal pending")) throw new Error("missing rehearsal warning");
' "$TMP_DIR/pending-rehearsal/summary.json"

expect_pass exact-signup-completes "${common_404[@]}" \
	--run-signup \
	--signup-username ExactQa \
	--signup-email exact@example.com \
	--signup-password Launchpass1 \
	--verification-timeout-seconds 1 \
	--verification-poll-seconds 1 \
	--out "$TMP_DIR/exact-signup-completes"
grep -q "Open the delivered email and complete verification within 1 seconds" "$TMP_DIR/exact-signup-completes.log"
node -e '
const summary = require(process.argv[1]);
if (summary.signupMode !== "final") throw new Error("wrong final mode");
if (!summary.signup || summary.signup.exactSignupVerified !== true) throw new Error("exact signup was not proven");
' "$TMP_DIR/exact-signup-completes/summary.json"

expect_fail unverified-account-rejected "${common_404[@]}" \
	--run-signup \
	--signup-username unverified \
	--signup-email unverified@example.com \
	--signup-password Launchpass1 \
	--verification-timeout-seconds 1 \
	--verification-poll-seconds 1 \
	--out "$TMP_DIR/unverified-account-rejected"
grep -q "email verified" "$TMP_DIR/unverified-account-rejected.log"

expect_fail pending-final-times-out "${common_404[@]}" \
	--run-signup \
	--signup-username PendingFinal \
	--signup-email pending-final@example.com \
	--signup-password Launchpass1 \
	--verification-timeout-seconds 1 \
	--verification-poll-seconds 1 \
	--out "$TMP_DIR/pending-final-times-out"
grep -q "did not become a verified, active, linked account" "$TMP_DIR/pending-final-times-out.log"

echo "launch staging guard unit checks passed"
