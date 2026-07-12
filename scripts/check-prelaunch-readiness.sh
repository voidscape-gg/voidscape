#!/usr/bin/env bash
# One-command prelaunch readiness report for non-device gates.

set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="$ROOT/tmp/prelaunch-readiness-$STAMP"
HOST="voidscape.gg"
GAME_PORT="43596"
PORTAL_URL="https://voidscape.gg/"
WEB_URL="https://voidscape.gg/play/"
WS_URL="wss://voidscape.gg/play/ws/"
LAUNCH_AT="2026-07-18T18:00:00Z"
RUN_BUILD=1
RUN_PORTAL_API=1
RUN_PORTAL_SCHEMA=1
RUN_PACKAGE=1
RUN_LAUNCHER=1
RUN_LIVE_PORTAL_VISUAL=0
RUN_VISUAL_BOARD=0
RUN_ANDROID_RELEASE_GUARD=0
ANDROID_MODE="skip"
ANDROID_PUBLIC_CHANNEL_DECISION="apk-visible-when-artifact-exists"
BUILD_WEB=0
PC_USER=""
PC_PASS_FILE=""
PC_TIMEOUT="120"
MIN_FREE_MB="512"
ANDROID_DEVICE_REPORT=""
IPHONE_QA_REPORT=""
IPHONE_LOCAL_PREFLIGHT="$ROOT/tmp/web-teavm-iphone-release-preflight/summary.json"
IPHONE_PACKAGE_DIR="$ROOT/dist/web-teavm"
IPHONE_REQUIRE_SIMULATOR_VIDEO=0
ANDROID_DEVICE_PROVEN=0
IPHONE_DEVICE_PROVEN=0

usage() {
	cat <<'EOF'
Usage: scripts/check-prelaunch-readiness.sh [options]

Runs the local, non-physical-device prelaunch gates and writes a compact
Markdown report plus per-check logs.

Defaults:
  - portal schema smoke
  - portal API/entitlement smoke
  - canonical scripts/build.sh
  - launch-staging package rehearsal using existing web build, without Android
  - packaged desktop launcher visual smoke

Options:
  --out DIR                 Output report directory.
  --host HOST               Game host for package/launcher. Default: voidscape.gg.
  --port PORT               Game TCP port. Default: 43596.
  --portal-url URL          Portal URL. Default: https://voidscape.gg/.
  --web-url URL             Web client URL. Default: https://voidscape.gg/play/.
  --ws-url URL              WebSocket URL. Default: wss://voidscape.gg/play/ws/.
  --launch-at ISO           Expected launch timestamp. Default: 2026-07-18T18:00:00Z.
  --skip-build              Do not run scripts/build.sh.
  --skip-portal-api         Do not run scripts/test-portal-api.sh.
  --skip-portal-schema      Do not run scripts/test-portal-schema.sh.
  --skip-package            Do not build a launch-staging rehearsal bundle.
  --skip-launcher           Do not run packaged desktop launcher visual smoke.
  --build-web               Rebuild TeaVM web client during package rehearsal.
  --android-debug-package   Include a debug Android APK in the package rehearsal.
  --android-release-package Include a signed release Android APK in the package rehearsal.
  --android-release-guard   Check that release APK signing is configured, or guarded.
  --live-portal-visual      Run non-mutating live portal visual smoke.
  --visual-board            Generate the screenshot-backed game/client visual board.
  --pc-user USER            Run authenticated desktop Java smoke for USER.
  --pc-pass-file FILE       Password file for --pc-user.
  --pc-timeout SECONDS      Desktop Java smoke timeout. Default: 120.
  --android-device-report FILE
                            Validate a filled physical Android QA report.
  --iphone-qa-report FILE   Run final iPhone web release audit with a filled
                            physical iPhone Safari/Home Screen QA report.
  --iphone-local-preflight FILE
                            Local iPhone automated preflight summary for the
                            final iPhone audit. Default:
                            tmp/web-teavm-iphone-release-preflight/summary.json.
  --iphone-package-dir DIR  Exact TeaVM package dir for final iPhone audit.
                            Default: dist/web-teavm.
  --iphone-require-simulator-video
                            Require simulator video evidence in final iPhone audit.
  --min-free-mb MB          Refuse to start if output filesystem has less free space. Default: 512.
  --skip-space-check        Do not check free space before starting.
  -h, --help                Show this help.

Physical Android/iPhone QA, Android direct-APK channel changes, hosted sync/restart,
and final rollback/source-disclosure notes remain manual gates.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--out)
			OUT_DIR="${2:-}"
			shift 2
			;;
		--host)
			HOST="${2:-}"
			shift 2
			;;
		--port)
			GAME_PORT="${2:-}"
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
		--skip-build)
			RUN_BUILD=0
			shift
			;;
		--skip-portal-api)
			RUN_PORTAL_API=0
			shift
			;;
		--skip-portal-schema)
			RUN_PORTAL_SCHEMA=0
			shift
			;;
		--skip-package)
			RUN_PACKAGE=0
			shift
			;;
		--skip-launcher)
			RUN_LAUNCHER=0
			shift
			;;
		--build-web)
			BUILD_WEB=1
			shift
			;;
		--android-debug-package)
			ANDROID_MODE="debug"
			shift
			;;
		--android-release-package)
			ANDROID_MODE="release"
			shift
			;;
		--android-release-guard)
			RUN_ANDROID_RELEASE_GUARD=1
			shift
			;;
		--live-portal-visual)
			RUN_LIVE_PORTAL_VISUAL=1
			shift
			;;
		--visual-board)
			RUN_VISUAL_BOARD=1
			shift
			;;
		--pc-user)
			PC_USER="${2:-}"
			shift 2
			;;
		--pc-pass-file)
			PC_PASS_FILE="${2:-}"
			shift 2
			;;
		--pc-timeout)
			PC_TIMEOUT="${2:-}"
			shift 2
			;;
		--android-device-report)
			ANDROID_DEVICE_REPORT="${2:-}"
			shift 2
			;;
		--iphone-qa-report)
			IPHONE_QA_REPORT="${2:-}"
			shift 2
			;;
		--iphone-local-preflight)
			IPHONE_LOCAL_PREFLIGHT="${2:-}"
			shift 2
			;;
		--iphone-package-dir)
			IPHONE_PACKAGE_DIR="${2:-}"
			shift 2
			;;
		--iphone-require-simulator-video)
			IPHONE_REQUIRE_SIMULATOR_VIDEO=1
			shift
			;;
		--min-free-mb)
			MIN_FREE_MB="${2:-}"
			shift 2
			;;
		--skip-space-check)
			MIN_FREE_MB="0"
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "Unknown option: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

if [[ -z "$OUT_DIR" || -z "$HOST" || -z "$PORTAL_URL" || -z "$WEB_URL" || -z "$WS_URL" ]]; then
	echo "ERROR: output, host, portal URL, web URL, and WSS URL must be non-empty." >&2
	exit 2
fi
if ! [[ "$GAME_PORT" =~ ^[0-9]+$ ]] || (( GAME_PORT < 1 || GAME_PORT > 65535 )); then
	echo "ERROR: --port must be a number from 1 to 65535." >&2
	exit 2
fi
if ! [[ "$PC_TIMEOUT" =~ ^[0-9]+$ ]] || (( PC_TIMEOUT < 15 )); then
	echo "ERROR: --pc-timeout must be at least 15 seconds." >&2
	exit 2
fi
if ! [[ "$MIN_FREE_MB" =~ ^[0-9]+$ ]]; then
	echo "ERROR: --min-free-mb must be a non-negative integer." >&2
	exit 2
fi
if [[ -n "$PC_USER" && -z "$PC_PASS_FILE" ]]; then
	echo "ERROR: --pc-user requires --pc-pass-file." >&2
	exit 2
fi
if [[ -n "$PC_PASS_FILE" && ! -f "$PC_PASS_FILE" ]]; then
	echo "ERROR: PC password file not found: $PC_PASS_FILE" >&2
	exit 2
fi
if [[ -n "$ANDROID_DEVICE_REPORT" && ! -f "$ANDROID_DEVICE_REPORT" ]]; then
	echo "ERROR: Android device QA report not found: $ANDROID_DEVICE_REPORT" >&2
	exit 2
fi
if [[ -n "$IPHONE_QA_REPORT" && ! -f "$IPHONE_QA_REPORT" ]]; then
	echo "ERROR: iPhone QA report not found: $IPHONE_QA_REPORT" >&2
	exit 2
fi
if [[ -n "$IPHONE_QA_REPORT" && -z "$IPHONE_LOCAL_PREFLIGHT" ]]; then
	echo "ERROR: --iphone-qa-report requires --iphone-local-preflight or the default path." >&2
	exit 2
fi
if [[ -n "$IPHONE_QA_REPORT" && -z "$IPHONE_PACKAGE_DIR" ]]; then
	echo "ERROR: --iphone-qa-report requires --iphone-package-dir or the default path." >&2
	exit 2
fi
if [[ "$RUN_LAUNCHER" -eq 1 && "$RUN_PACKAGE" -eq 0 ]]; then
	echo "ERROR: packaged launcher smoke requires package rehearsal; remove --skip-package or add --skip-launcher." >&2
	exit 2
fi

OUT_DIR="$(mkdir -p "$OUT_DIR" && cd "$OUT_DIR" && pwd)"
if (( MIN_FREE_MB > 0 )); then
	FREE_MB="$(df -Pm "$OUT_DIR" | awk 'NR==2 {print $4}')"
	if ! [[ "$FREE_MB" =~ ^[0-9]+$ ]]; then
		echo "ERROR: unable to determine free space for $OUT_DIR." >&2
		exit 2
	fi
	if (( FREE_MB < MIN_FREE_MB )); then
		cat >&2 <<EOF
ERROR: only ${FREE_MB}MiB free for $OUT_DIR; need at least ${MIN_FREE_MB}MiB.

The readiness report writes launch bundles, logs, and screenshots. Free space first, for example:
  du -sh "$ROOT/tmp"/* 2>/dev/null | sort -h | tail -n 25
  rm -rf "$ROOT/tmp/<superseded-report-dir>"

Keep the latest evidence directories referenced by docs/PRELAUNCH-QA-HANDOFF.md.
EOF
		exit 2
	fi
fi
LOG_DIR="$OUT_DIR/logs"
REPORT="$OUT_DIR/summary.md"
mkdir -p "$LOG_DIR"

PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0
CHECK_ROWS=()
PACKAGE_DIR="$OUT_DIR/launch-staging-package"

slug() {
	printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | tr -cs 'a-z0-9._-' '-' | sed 's/^-//; s/-$//'
}

relpath() {
	local path="$1"
	case "$path" in
		"$ROOT"/*) printf '%s\n' "${path#$ROOT/}" ;;
		*) printf '%s\n' "$path" ;;
	esac
}

record_check() {
	local status="$1"
	local name="$2"
	local detail="$3"
	local log_path="${4:-}"
	local artifact="${5:-}"
	local label
	case "$status" in
		pass)
			label="PASS"
			PASS_COUNT=$((PASS_COUNT + 1))
			;;
		fail)
			label="FAIL"
			FAIL_COUNT=$((FAIL_COUNT + 1))
			;;
		warn)
			label="WARN"
			WARN_COUNT=$((WARN_COUNT + 1))
			;;
		*)
			label="$status"
			;;
	esac
	local row="- [$label] $name"
	if [[ -n "$detail" ]]; then
		row="$row - $detail"
	fi
	if [[ -n "$log_path" ]]; then
		row="$row (log: \`$(relpath "$log_path")\`)"
	fi
	if [[ -n "$artifact" ]]; then
		row="$row (artifact: \`$(relpath "$artifact")\`)"
	fi
	CHECK_ROWS+=("$row")
}

write_report() {
	{
		echo "# Prelaunch Readiness Report"
		echo
		echo "- Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
		echo "- Target host: $HOST:$GAME_PORT"
		echo "- Portal URL: $PORTAL_URL"
		echo "- Web URL: $WEB_URL"
		echo "- WSS URL: $WS_URL"
	echo "- Launch timestamp: $LAUNCH_AT"
	echo "- Android package mode: $ANDROID_MODE"
	echo "- Android public channel: $ANDROID_PUBLIC_CHANNEL_DECISION (post-launch chooser serves the configured APK artifact; release signing and physical QA are recommended before broad promotion)"
		echo
		echo "## Result"
		echo
		echo "- Pass: $PASS_COUNT"
		echo "- Warn: $WARN_COUNT"
		echo "- Fail: $FAIL_COUNT"
		echo
		echo "## Checks"
		echo
		local row
		for row in "${CHECK_ROWS[@]}"; do
			echo "$row"
		done
		echo
		echo "## Manual Gates Not Proven Here"
		echo
		echo "- Android public channel decision is post-launch APK visibility when the APK artifact exists; prefer release signing and physical Android QA before broad promotion."
		echo "- Configure Android release signing before publishing APK links, only if direct APK distribution is later chosen."
		if [[ "$ANDROID_DEVICE_PROVEN" -eq 0 ]]; then
			echo "- Run physical Android QA on a real phone, then pass --android-device-report."
		fi
		if [[ "$IPHONE_DEVICE_PROVEN" -eq 0 ]]; then
			echo "- Run physical iPhone Safari and Home Screen QA on a real device, then pass --iphone-qa-report with matching preflight/package paths."
		fi
		echo "- If portal/footer changes are synced after this report, rerun live portal visual smoke with --live-portal-visual or scripts/smoke-portal-prelaunch-visual.sh."
		echo "- Sync/restart the final hosted bundle, then run that bundle's VERIFY-STAGING.sh."
		echo "- Fill and archive the generated RELEASE-HANDOFF.md with real backup paths and AGPL/source-disclosure confirmation."
	} > "$REPORT"
}

run_check() {
	local name="$1"
	shift
	local log="$LOG_DIR/$(slug "$name").log"
	echo "==> $name"
	(
		cd "$ROOT"
		"$@"
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		record_check pass "$name" "ok" "$log"
	else
		record_check fail "$name" "exit $rc" "$log"
	fi
	return 0
}

run_portal_api_check() {
	local name="Portal API and entitlement smoke"
	local log="$LOG_DIR/$(slug "$name").log"
	echo "==> $name"
	(
		cd "$ROOT"
		scripts/test-portal-api.sh
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		record_check pass "$name" "account signup/login, character creation, starter-card invariants, launch-mode recovery reset, and session revocation ok" "$log"
	else
		record_check fail "$name" "exit $rc" "$log"
	fi
	return 0
}

run_android_release_guard() {
	local name="Android release signing guard"
	local log="$LOG_DIR/$(slug "$name").log"
	echo "==> $name"
	(
		cd "$ROOT"
		scripts/build-android.sh --release
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		record_check pass "$name" "release build succeeded; signing is configured or unsigned override is explicit" "$log"
	elif grep -q "Voidscape release artifact signing is not configured" "$log"; then
		record_check pass "$name" "unsigned release is blocked until signing is configured" "$log"
	elif grep -q "Android release inputs are dirty" "$log"; then
		record_check pass "$name" "dirty Android inputs are blocked from release promotion" "$log"
	else
		record_check fail "$name" "unexpected release build failure" "$log"
	fi
	return 0
}

run_package_check() {
	local args=(
		scripts/package-launch-staging.sh
		--host "$HOST"
		--port "$GAME_PORT"
		--portal-url "$PORTAL_URL"
		--web-url "$WEB_URL"
		--ws-url "$WS_URL"
		--launch-at "$LAUNCH_AT"
		--skip-build
		--allow-dirty-staging
		--output-dir "$PACKAGE_DIR"
	)
	if [[ "$BUILD_WEB" -eq 0 ]]; then
		args+=(--skip-web-build)
	fi
	case "$ANDROID_MODE" in
		skip)
			args+=(--skip-android)
			;;
		debug)
			;;
		release)
			args+=(--android-release)
			;;
	esac
	run_check "Launch-staging package rehearsal" "${args[@]}"
	if [[ -f "$PACKAGE_DIR/MANIFEST.txt" ]]; then
		record_check pass "Launch-staging manifest present" "bundle manifest written" "" "$PACKAGE_DIR/MANIFEST.txt"
		local package_promotable
		local package_blockers
		package_promotable="$(awk -F= '$1 == "promotable" { print $2; exit }' "$PACKAGE_DIR/MANIFEST.txt")"
		package_blockers="$(awk -F= '$1 == "promotion_blockers" { print $2; exit }' "$PACKAGE_DIR/MANIFEST.txt")"
		if [[ "$package_promotable" == "false" && "$package_blockers" == *server_client_build_reused* ]]; then
			record_check pass "Launch-staging rehearsal provenance" "explicitly non-promotable: $package_blockers" "" "$PACKAGE_DIR/MANIFEST.txt"
		else
			record_check fail "Launch-staging rehearsal provenance" "expected promotable=false with reused-build blocker, got promotable=$package_promotable blockers=$package_blockers" "" "$PACKAGE_DIR/MANIFEST.txt"
		fi
	else
		record_check fail "Launch-staging manifest present" "MANIFEST.txt missing" ""
	fi
	if [[ -f "$PACKAGE_DIR/RELEASE-HANDOFF.md" ]]; then
		record_check pass "Release handoff present" "rollback/source-disclosure template written" "" "$PACKAGE_DIR/RELEASE-HANDOFF.md"
	else
		record_check fail "Release handoff present" "RELEASE-HANDOFF.md missing" ""
	fi
}

run_packaged_launcher_smoke() {
	local jar="$PACKAGE_DIR/launcher/VoidscapeLauncher-staging.jar"
	if [[ ! -f "$jar" ]]; then
		record_check fail "Packaged desktop launcher visual smoke" "packaged launcher jar missing" ""
		return 0
	fi
	run_check "Packaged desktop launcher visual smoke" \
		scripts/smoke-launcher-prelaunch.sh \
		--jar "$jar" \
		--use-packaged-config \
		--host "$HOST" \
		--port "$GAME_PORT" \
		--portal-url "${PORTAL_URL%/}" \
		--out "$OUT_DIR/launcher-smoke"
}

run_live_portal_visual() {
	run_check "Live portal visual smoke" \
		scripts/smoke-portal-prelaunch-visual.sh \
		--portal-url "$PORTAL_URL" \
		--skip-signup \
		--out "$OUT_DIR/live-portal-visual"
}

run_visual_board() {
	local name="Visual game/client evidence board"
	local board_dir="$OUT_DIR/visual-board"
	local log="$LOG_DIR/$(slug "$name").log"
	echo "==> $name"
	(
		cd "$ROOT"
		scripts/build-prelaunch-visual-board.mjs --out "$board_dir"
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -ne 0 ]]; then
		record_check fail "$name" "exit $rc" "$log"
		return 0
	fi
	local detail
	if detail="$(node -e '
const s = require(process.argv[1]);
const overviewScreenshots = Array.isArray(s.overviewScreenshots) ? s.overviewScreenshots : [];
const overviewWarning = s.overviewWarning || "";
const overviewDetail = `${overviewScreenshots.length} overview PNGs`;
console.log(`${s.totals.sections} sections, ${s.totals.tiles} screenshots, ${s.totals.missing} missing, ${overviewDetail}`);
if (s.totals.missing || overviewWarning || overviewScreenshots.length < 2) {
	if (overviewWarning) console.error(overviewWarning);
	process.exit(1);
}
' "$board_dir/summary.json" 2>>"$log")"; then
		record_check pass "$name" "$detail" "$log" "$board_dir/index.html"
	else
		record_check fail "$name" "one or more curated screenshots or overview PNGs are missing" "$log" "$board_dir/summary.json"
	fi
	return 0
}

run_pc_smoke() {
	local pass
	pass="$(<"$PC_PASS_FILE")"
	pass="${pass//$'\r'/}"
	pass="${pass//$'\n'/}"
	local log="$LOG_DIR/$(slug "Desktop Java authenticated visual smoke").log"
	echo "==> Desktop Java authenticated visual smoke"
	(
		cd "$ROOT"
		VOIDSCAPE_PC_SMOKE_PASS="$pass" \
			scripts/smoke-pc-client-prelaunch.sh \
				--host "$HOST" \
				--port "$GAME_PORT" \
				--user "$PC_USER" \
				--out "$OUT_DIR/pc-client-smoke" \
				--timeout "$PC_TIMEOUT"
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		record_check pass "Desktop Java authenticated visual smoke" "ok" "$log" "$OUT_DIR/pc-client-smoke/manifest.json"
	else
		record_check fail "Desktop Java authenticated visual smoke" "exit $rc" "$log"
	fi
}

run_android_device_report() {
	local name="Physical Android QA report"
	local log="$LOG_DIR/$(slug "$name").log"
	echo "==> $name"
	(
		cd "$ROOT"
		scripts/validate-android-device-qa-report.py "$ANDROID_DEVICE_REPORT"
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		ANDROID_DEVICE_PROVEN=1
		record_check pass "$name" "real-device report passed" "$log" "$ANDROID_DEVICE_REPORT"
	else
		record_check fail "$name" "exit $rc" "$log" "$ANDROID_DEVICE_REPORT"
	fi
}

run_iphone_final_audit() {
	local name="Physical iPhone final release audit"
	local log="$LOG_DIR/$(slug "$name").log"
	local out="$OUT_DIR/iphone-final-release-audit"
	local args=(
		scripts/check-web-teavm-iphone-final-release.py
		--qa-report "$IPHONE_QA_REPORT"
		--local-preflight "$IPHONE_LOCAL_PREFLIGHT"
		--package-dir "$IPHONE_PACKAGE_DIR"
		--out "$out"
	)
	if [[ "$IPHONE_REQUIRE_SIMULATOR_VIDEO" -eq 1 ]]; then
		args+=(--require-simulator-video)
	fi
	echo "==> $name"
	(
		cd "$ROOT"
		"${args[@]}"
	) > "$log" 2>&1
	local rc=$?
	if [[ "$rc" -eq 0 ]]; then
		IPHONE_DEVICE_PROVEN=1
		record_check pass "$name" "physical iPhone QA, hosted deployment, local preflight, and package audit passed" "$log" "$out/final-release-audit-summary.json"
	else
		record_check fail "$name" "exit $rc" "$log" "$out/final-release-audit-summary.json"
	fi
}

echo "==> Prelaunch readiness output: $OUT_DIR"

run_check "Launch config policy" tests/launch-config.sh
run_check "Cracker campaign durable control plane" tests/cracker-campaign-unit.sh
run_check "Deferred-content reachability gate" tests/launch-content-reachability.sh
run_check "Void Dungeon deterministic static gate" tests/void-dungeon-static.sh
run_check "Canonical client/cache manifest" tests/client-cache-manifest.sh

if [[ "$RUN_PORTAL_SCHEMA" -eq 1 ]]; then
	run_check "Portal schema smoke" scripts/test-portal-schema.sh
else
	record_check warn "Portal schema smoke" "skipped by flag" ""
fi

if [[ "$RUN_PORTAL_API" -eq 1 ]]; then
	run_portal_api_check
else
	record_check warn "Portal API and entitlement smoke" "skipped by flag" ""
fi

if [[ "$RUN_BUILD" -eq 1 ]]; then
	run_check "Canonical build" scripts/build.sh
else
	record_check warn "Canonical build" "skipped by flag" ""
fi

if [[ "$RUN_PACKAGE" -eq 1 ]]; then
	run_package_check
else
	record_check warn "Launch-staging package rehearsal" "skipped by flag" ""
fi

if [[ "$RUN_LAUNCHER" -eq 1 ]]; then
	run_packaged_launcher_smoke
else
	record_check warn "Packaged desktop launcher visual smoke" "skipped by flag" ""
fi

if [[ "$RUN_ANDROID_RELEASE_GUARD" -eq 1 ]]; then
	run_android_release_guard
else
	record_check warn "Android release signing guard" "not run; pass --android-release-guard to check" ""
fi

if [[ "$RUN_LIVE_PORTAL_VISUAL" -eq 1 ]]; then
	run_live_portal_visual
else
	record_check warn "Live portal visual smoke" "not run; pass --live-portal-visual for browser proof" ""
fi

if [[ "$RUN_VISUAL_BOARD" -eq 1 ]]; then
	run_visual_board
else
	record_check warn "Visual game/client evidence board" "not run; pass --visual-board to generate" ""
fi

if [[ -n "$PC_USER" ]]; then
	run_pc_smoke
else
	record_check warn "Desktop Java authenticated visual smoke" "not run; pass --pc-user and --pc-pass-file when a QA account/server are available" ""
fi

if [[ -n "$ANDROID_DEVICE_REPORT" ]]; then
	run_android_device_report
else
	record_check warn "Physical Android QA report" "not run; pass --android-device-report after real-device QA" ""
fi

if [[ -n "$IPHONE_QA_REPORT" ]]; then
	run_iphone_final_audit
else
	record_check warn "Physical iPhone final release audit" "not run; pass --iphone-qa-report after real-device Safari/Home Screen QA" ""
fi

write_report

echo "==> Report: $REPORT"
if [[ "$FAIL_COUNT" -gt 0 ]]; then
	echo "Prelaunch readiness checks finished with $FAIL_COUNT failure(s)." >&2
	exit 1
fi

echo "Prelaunch readiness checks finished with $PASS_COUNT pass(es) and $WARN_COUNT warning(s)."
