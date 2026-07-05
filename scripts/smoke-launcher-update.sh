#!/usr/bin/env bash
# End-to-end smoke for the launcher auto-update pipeline.
#
# Builds a hermetic local update channel (manifest v2 + payload files) served over
# http://127.0.0.1, then drives the launcher's --sync-only mode through the full
# lifecycle: fresh install, idempotent re-run, corruption repair, prune of removed
# files, launcher self-update staging (and its --relaunched loop guard), offline
# fallback, and the plain-http refusal for non-loopback hosts.
#
# No network access beyond loopback; nothing outside --out is touched.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${VOIDSCAPE_LAUNCHER_UPDATE_SMOKE_OUT:-${TMPDIR:-/tmp}/voidscape-launcher-update-smoke-$(date +%Y%m%d-%H%M%S)}"
PORT="${VOIDSCAPE_LAUNCHER_UPDATE_SMOKE_PORT:-18987}"
JAR_PATH="$ROOT/PC_Launcher/OpenRSC.jar"
BUILD=1

usage() {
	cat <<'EOF'
Usage: scripts/smoke-launcher-update.sh [options]

Options:
  --no-build   Reuse PC_Launcher/OpenRSC.jar instead of running ant compile.
  --out DIR    Output directory for channel/caches/logs.
  --port PORT  Local HTTP port for the update channel. Default: 18987.
  -h, --help   Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--no-build)
			BUILD=0
			shift
			;;
		--out)
			[[ $# -ge 2 ]] || { echo "error: --out needs a value" >&2; exit 2; }
			OUT_DIR="$2"
			shift 2
			;;
		--port)
			[[ $# -ge 2 ]] || { echo "error: --port needs a value" >&2; exit 2; }
			PORT="$2"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "error: unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
CHANNEL_DIR="$OUT_DIR/channel"
CACHE_DIR="$OUT_DIR/cache"
LOG_DIR="$OUT_DIR/logs"
mkdir -p "$CHANNEL_DIR" "$LOG_DIR"

SERVER_PID=""
cleanup() {
	if [[ -n "$SERVER_PID" ]] && kill -0 "$SERVER_PID" 2>/dev/null; then
		kill "$SERVER_PID" 2>/dev/null || true
	fi
}
trap cleanup EXIT

sha256_file() {
	shasum -a 256 "$1" | awk '{print $1}'
}

file_size() {
	stat -f%z "$1" 2>/dev/null || stat -c%s "$1"
}

FAILURES=0
check() {
	local label="$1"
	shift
	if "$@"; then
		echo "PASS: $label"
	else
		echo "FAIL: $label"
		FAILURES=$((FAILURES + 1))
	fi
}

# run_sync <log-name> <extra jvm/launcher args...>; captures exit code in SYNC_EXIT and log in SYNC_LOG.
# Runs from / so the launcher's CWD-relative Client_Base dev seeding stays inert even
# when --out points inside the repository.
run_sync() {
	local log_name="$1"
	shift
	SYNC_LOG="$LOG_DIR/$log_name.log"
	set +e
		(cd / && java "-Dvoidscape.manifestUrl=$MANIFEST_URL" \
		"-Dvoidscape.serverHost=127.0.0.1" "-Dvoidscape.serverPort=43596" \
		-jar "$JAR_PATH" --sync-only --dir "$CACHE_DIR" "$@") >"$SYNC_LOG" 2>&1
	SYNC_EXIT=$?
	set -e
}

log_has() {
	grep -q "$1" "$SYNC_LOG"
}

# ---------------------------------------------------------------------------
# Build launcher + channel payloads
# ---------------------------------------------------------------------------

if [[ "$BUILD" -eq 1 ]]; then
	(cd "$ROOT/PC_Launcher" && ant compile) >"$LOG_DIR/ant.log" 2>&1
fi
[[ -f "$JAR_PATH" ]] || { echo "error: missing $JAR_PATH" >&2; exit 1; }

# Hermetic payloads: sync never launches the client, so these only need stable bytes.
head -c 262144 /dev/urandom > "$CHANNEL_DIR/Open_RSC_Client.jar"
mkdir -p "$CHANNEL_DIR/video" "$CHANNEL_DIR/data"
head -c 4096 /dev/urandom > "$CHANNEL_DIR/video/fonts.bin"
head -c 16384 /dev/urandom > "$CHANNEL_DIR/data/sample.bin"

# Self-update payload: the built launcher jar plus a copy with different bytes ("new build").
mkdir -p "$CHANNEL_DIR/launcher"
cp "$JAR_PATH" "$CHANNEL_DIR/launcher/VoidscapeLauncher.jar"
cat "$JAR_PATH" > "$CHANNEL_DIR/launcher/VoidscapeLauncher-next.jar"
printf 'voidscape-smoke-extra-byte' >> "$CHANNEL_DIR/launcher/VoidscapeLauncher-next.jar"

BASE_URL="http://127.0.0.1:$PORT"
MANIFEST_URL="$BASE_URL/manifest.properties"

# write_manifest <include-sample> <launcher-jar-or-empty>
write_manifest() {
	local include_sample="$1"
	local launcher_jar="$2"
	{
		printf 'version=10123-%s\n' "$(date -u +%Y%m%d%H%M%S)"
		printf 'clientVersion=10123\n'
		printf 'baseUrl=%s/\n' "$BASE_URL"
		local index=1
		local files=("Open_RSC_Client.jar" "video/fonts.bin")
		if [[ "$include_sample" -eq 1 ]]; then
			files+=("data/sample.bin")
		fi
		local relative
		for relative in "${files[@]}"; do
			printf 'file.%d.path=%s\n' "$index" "$relative"
			printf 'file.%d.sha256=%s\n' "$index" "$(sha256_file "$CHANNEL_DIR/$relative")"
			printf 'file.%d.size=%s\n' "$index" "$(file_size "$CHANNEL_DIR/$relative")"
			index=$((index + 1))
		done
		if [[ -n "$launcher_jar" ]]; then
			printf 'launcher.sha256=%s\n' "$(sha256_file "$launcher_jar")"
			printf 'launcher.url=%s/launcher/%s\n' "$BASE_URL" "$(basename "$launcher_jar")"
			printf 'launcher.size=%s\n' "$(file_size "$launcher_jar")"
			printf 'launcher.version=10123-smoke\n'
		fi
	} > "$CHANNEL_DIR/manifest.properties"
}

write_manifest 1 ""

(cd "$CHANNEL_DIR" && exec python3 -m http.server "$PORT" --bind 127.0.0.1 >"$LOG_DIR/http-server.log" 2>&1) &
SERVER_PID=$!
for _ in $(seq 1 50); do
	if curl -fs -o /dev/null "$MANIFEST_URL"; then
		break
	fi
	sleep 0.1
done
curl -fs -o /dev/null "$MANIFEST_URL" || { echo "error: channel server did not come up on $MANIFEST_URL" >&2; exit 1; }

# ---------------------------------------------------------------------------
# 1. Fresh install
# ---------------------------------------------------------------------------

run_sync "1-fresh-install"
check "fresh install exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "fresh install reports updated" log_has "SYNC_OUTCOME updated"
check "fresh install downloads 3 files" log_has "SYNC_DOWNLOADED 3"
check "fresh install reports client version" log_has "SYNC_CLIENT_VERSION 10123"
check "client jar matches channel hash" [ "$(sha256_file "$CACHE_DIR/Open_RSC_Client.jar")" = "$(sha256_file "$CHANNEL_DIR/Open_RSC_Client.jar")" ]
check "nested cache file downloaded" [ -f "$CACHE_DIR/video/fonts.bin" ]
check "sync state written" [ -f "$CACHE_DIR/.voidscape-sync-state.properties" ]

# ---------------------------------------------------------------------------
# 2. Idempotent re-run
# ---------------------------------------------------------------------------

run_sync "2-idempotent"
check "re-run exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "re-run reports up-to-date" log_has "SYNC_OUTCOME up-to-date"
check "re-run downloads nothing" log_has "SYNC_DOWNLOADED 0"

# ---------------------------------------------------------------------------
# 3. Corruption repair
# ---------------------------------------------------------------------------

printf 'corruption' >> "$CACHE_DIR/video/fonts.bin"
run_sync "3-repair"
check "repair exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "repair re-downloads exactly 1 file" log_has "SYNC_DOWNLOADED 1"
check "corrupted file restored" [ "$(sha256_file "$CACHE_DIR/video/fonts.bin")" = "$(sha256_file "$CHANNEL_DIR/video/fonts.bin")" ]

# ---------------------------------------------------------------------------
# 4. Prune removed files
# ---------------------------------------------------------------------------

write_manifest 0 ""
run_sync "4-prune"
check "prune run exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "removed file pruned from cache" [ ! -f "$CACHE_DIR/data/sample.bin" ]

# ---------------------------------------------------------------------------
# 5. Launcher self-update staging + loop guard
# ---------------------------------------------------------------------------

write_manifest 0 "$CHANNEL_DIR/launcher/VoidscapeLauncher-next.jar"
run_sync "5-self-update"
check "self-update run exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "launcher update staged" log_has "SYNC_LAUNCHER_STAGED"
NEXT_SHA="$(sha256_file "$CHANNEL_DIR/launcher/VoidscapeLauncher-next.jar")"
STAGED_JAR="$CACHE_DIR/launcher/VoidscapeLauncher-${NEXT_SHA:0:8}.jar"
check "staged jar exists at hash-derived path" [ -f "$STAGED_JAR" ]
check "staged jar hash matches manifest" [ "$(sha256_file "$STAGED_JAR")" = "$NEXT_SHA" ]

run_sync "5b-relaunched-guard" --relaunched
check "--relaunched exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "--relaunched never stages a launcher update" bash -c "! grep -q SYNC_LAUNCHER_STAGED '$SYNC_LOG'"

# ---------------------------------------------------------------------------
# 6. Offline fallback
# ---------------------------------------------------------------------------

kill "$SERVER_PID" 2>/dev/null || true
wait "$SERVER_PID" 2>/dev/null || true
SERVER_PID=""

run_sync "6-offline"
check "offline with local files exits 0" [ "$SYNC_EXIT" -eq 0 ]
check "offline reports offline outcome" log_has "SYNC_OUTCOME offline"

EMPTY_CACHE="$OUT_DIR/cache-empty"
SYNC_LOG="$LOG_DIR/6b-offline-fresh.log"
set +e
(cd / && java "-Dvoidscape.manifestUrl=$MANIFEST_URL" -jar "$JAR_PATH" \
	--sync-only --dir "$EMPTY_CACHE") >"$SYNC_LOG" 2>&1
SYNC_EXIT=$?
set -e
check "offline with no client fails" [ "$SYNC_EXIT" -ne 0 ]
check "offline failure is reported" log_has "SYNC_OUTCOME failed"

# ---------------------------------------------------------------------------
# 7. Plain-http refusal for non-loopback hosts
# ---------------------------------------------------------------------------

SYNC_LOG="$LOG_DIR/7-insecure-refused.log"
set +e
(cd / && java "-Dvoidscape.manifestUrl=http://198.51.100.1/manifest.properties" -jar "$JAR_PATH" \
	--sync-only --dir "$OUT_DIR/cache-insecure") >"$SYNC_LOG" 2>&1
SYNC_EXIT=$?
set -e
check "non-loopback http manifest is refused" [ "$SYNC_EXIT" -ne 0 ]
check "refusal names the insecure URL policy" log_has "Refusing plain-http"

# ---------------------------------------------------------------------------

echo
if [[ "$FAILURES" -eq 0 ]]; then
	echo "smoke-launcher-update: ALL CHECKS PASSED (logs: $LOG_DIR)"
	exit 0
fi
echo "smoke-launcher-update: $FAILURES CHECK(S) FAILED (logs: $LOG_DIR)"
exit 1
