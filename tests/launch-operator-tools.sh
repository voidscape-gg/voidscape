#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-launch-operator-tools.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

bash -n \
	"$ROOT/scripts/manage-portal-roster-freeze.sh" \
	"$ROOT/scripts/promote-owner-sqlite.sh" \
	"$ROOT/scripts/package-launch-staging.sh"

cat > "$TMP/systemctl" <<'SH'
#!/usr/bin/env bash
set -eu
case "$1:$2" in
	is-active:voidscape.service)
		if [ "${FAKE_GAME_ACTIVE:-0}" = 1 ]; then echo active; else echo inactive; exit 3; fi
		;;
	is-enabled:voidscape.service)
		if [ "${FAKE_GAME_ACTIVE:-0}" = 1 ]; then echo enabled; else echo disabled; exit 1; fi
		;;
	is-active:*) echo active ;;
	restart:*)
		if [ "${FAKE_SYSTEMCTL_INTERRUPT:-0}" = 1 ] \
			&& grep -qx 'PORTAL_ROSTER_WRITES_FROZEN=1' "${FAKE_PORTAL_ENV:?}" \
			&& [ ! -e "${FAKE_INTERRUPT_MARKER:?}" ]; then
			touch "$FAKE_INTERRUPT_MARKER"
			kill -TERM "$PPID"
			sleep 0.1
		fi
		printf '%s\n' "$2" >> "${FAKE_SYSTEMCTL_LOG:?}"
		;;
	*) exit 2 ;;
esac
SH
chmod +x "$TMP/systemctl"

cat > "$TMP/ss" <<'SH'
#!/usr/bin/env bash
set -eu
if [ -n "${FAKE_SS_LISTENER_PORT:-}" ]; then
	printf 'LISTEN 0 128 0.0.0.0:%s 0.0.0.0:*\n' "$FAKE_SS_LISTENER_PORT"
fi
SH
chmod +x "$TMP/ss"

cat > "$TMP/lsof" <<'SH'
#!/usr/bin/env bash
set -eu
[ -z "${FAKE_LSOF_PIDS:-}" ] || printf '%s\n' "$FAKE_LSOF_PIDS"
SH
chmod +x "$TMP/lsof"

cat > "$TMP/ps" <<'SH'
#!/usr/bin/env bash
set -eu
pid=""
while [ "$#" -gt 0 ]; do
	case "$1" in
		-p) pid="$2"; shift 2 ;;
		*) shift ;;
	esac
done
case "$pid" in
	888) printf 'node node /opt/voidscape/web/portal/dev-server.mjs\n' ;;
	999) printf 'java java com.openrsc.server.Server\n' ;;
esac
SH
chmod +x "$TMP/ps"

cat > "$TMP/curl" <<'SH'
#!/usr/bin/env bash
set -eu
output=""
method=GET
url=""
while [ "$#" -gt 0 ]; do
	case "$1" in
		-o) output="$2"; shift 2 ;;
		-X) method="$2"; shift 2 ;;
		-H|-d|--connect-timeout|--max-time|-w) shift 2 ;;
		-sS) shift ;;
		http://*|https://*) url="$1"; shift ;;
		*) shift ;;
	esac
done
frozen=false
grep -qx 'PORTAL_ROSTER_WRITES_FROZEN=1' "${FAKE_PORTAL_ENV:?}" && frozen=true
if [ "${FAKE_CURL_BAD_ON_FREEZE:-0}" = 1 ] && [ "$frozen" = true ] && [[ "$url" == */api/health ]]; then
	printf '{"config":{"rosterWritesFrozen":false}}' > "$output"
	printf 200
	exit 0
fi
if [ "$method" = POST ] && [ "$frozen" = true ]; then
	printf '{"error":"roster_writes_frozen"}' > "$output"
	printf 503
elif [[ "$url" == */api/health ]]; then
	printf '{"config":{"rosterWritesFrozen":%s}}' "$frozen" > "$output"
	printf 200
elif [[ "$url" == */api/public ]]; then
	printf '{"rosterWritesFrozen":%s}' "$frozen" > "$output"
	printf 200
else
	printf '{"error":"unexpected_fixture_request"}' > "$output"
	printf 500
fi
SH
chmod +x "$TMP/curl"

portal_env="$TMP/portal.env"
printf 'PORTAL_PUBLIC_MODE=1\nPORTAL_SECRET=do-not-print-me\n' > "$portal_env"
chmod 0640 "$portal_env"
export FAKE_PORTAL_ENV="$portal_env"
export FAKE_SYSTEMCTL_LOG="$TMP/systemctl.log"

freeze_common=(
	--env-file "$portal_env"
	--service voidscape-portal.service
	--game-service voidscape.service
	--local-url http://127.0.0.1:8788
	--systemctl "$TMP/systemctl"
	--curl "$TMP/curl"
	--ss "$TMP/ss"
)
"$ROOT/scripts/manage-portal-roster-freeze.sh" status "${freeze_common[@]}" > "$TMP/status.log"
"$ROOT/scripts/manage-portal-roster-freeze.sh" freeze "${freeze_common[@]}" > "$TMP/freeze.log"
grep -qx 'PORTAL_ROSTER_WRITES_FROZEN=1' "$portal_env"
! grep -q 'do-not-print-me' "$TMP/freeze.log"
# Frozen status must fail closed if a production game listener appears, while
# unfreeze remains available so the inconsistent state can be cleared.
if FAKE_SS_LISTENER_PORT=43596 "$ROOT/scripts/manage-portal-roster-freeze.sh" status "${freeze_common[@]}" > "$TMP/frozen-listener.log" 2>&1; then
	echo "frozen status should reject a production game listener" >&2
	exit 1
fi
grep -q 'production game port 43596 still has a TCP listener' "$TMP/frozen-listener.log"
FAKE_SS_LISTENER_PORT=43596 FAKE_GAME_ACTIVE=1 \
	"$ROOT/scripts/manage-portal-roster-freeze.sh" unfreeze "${freeze_common[@]}" > "$TMP/unfreeze.log"
grep -qx 'PORTAL_ROSTER_WRITES_FROZEN=0' "$portal_env"

if FAKE_SS_LISTENER_PORT=43496 "$ROOT/scripts/manage-portal-roster-freeze.sh" freeze "${freeze_common[@]}" > "$TMP/freeze-listener.log" 2>&1; then
	echo "freeze should reject a production game listener before changing the env" >&2
	exit 1
fi
grep -qx 'PORTAL_ROSTER_WRITES_FROZEN=0' "$portal_env"

# A failed post-restart verification restores the exact prior env state.
before_hash="$(shasum -a 256 "$portal_env" | awk '{print $1}')"
if FAKE_CURL_BAD_ON_FREEZE=1 "$ROOT/scripts/manage-portal-roster-freeze.sh" freeze "${freeze_common[@]}" > "$TMP/rollback.log" 2>&1; then
	echo "freeze helper should fail when the API reports the wrong state" >&2
	exit 1
fi
after_hash="$(shasum -a 256 "$portal_env" | awk '{print $1}')"
[[ "$before_hash" == "$after_hash" ]]

# A termination after the atomic env swap also restores the exact prior file.
rm -f "$TMP/interrupted"
if FAKE_SYSTEMCTL_INTERRUPT=1 FAKE_INTERRUPT_MARKER="$TMP/interrupted" \
	"$ROOT/scripts/manage-portal-roster-freeze.sh" freeze "${freeze_common[@]}" > "$TMP/interrupt.log" 2>&1; then
	echo "interrupted freeze helper should not report success" >&2
	exit 1
fi
[[ -f "$TMP/interrupted" ]]
after_interrupt_hash="$(shasum -a 256 "$portal_env" | awk '{print $1}')"
[[ "$before_hash" == "$after_interrupt_hash" ]]

owner_db="$TMP/owner.sqlite"
sqlite3 "$owner_db" <<'SQL'
CREATE TABLE players (id INTEGER PRIMARY KEY, username TEXT, group_id INTEGER, online INTEGER, banned TEXT);
CREATE TABLE player_cache (dbid INTEGER PRIMARY KEY, playerID INTEGER, key TEXT, value TEXT);
CREATE TABLE curstats (playerID INTEGER);
CREATE TABLE maxstats (playerID INTEGER);
CREATE TABLE experience (playerID INTEGER);
CREATE TABLE capped_experience (playerID INTEGER);
INSERT INTO players VALUES (542, 'name123', 10, 0, '0');
INSERT INTO player_cache VALUES (1, 542, 'web_account_id', '225');
INSERT INTO curstats VALUES (542);
INSERT INTO maxstats VALUES (542);
INSERT INTO experience VALUES (542);
INSERT INTO capped_experience VALUES (542);
SQL

owner_common=(
	--db "$owner_db"
	--game-service voidscape.service
	--systemctl "$TMP/systemctl"
	--ss "$TMP/ss"
	--lsof "$TMP/lsof"
	--ps "$TMP/ps"
	--player-id 542
	--account-id 225
	--username name123
)
"$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" > "$TMP/owner-dry-run.log"
[[ "$(sqlite3 "$owner_db" 'SELECT group_id FROM players WHERE id=542;')" == 10 ]]

# Portal Node access is allowed, but live game listeners and Java holders are not.
FAKE_LSOF_PIDS=888 "$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" > "$TMP/owner-node-open.log"
if FAKE_SS_LISTENER_PORT=43596 "$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" > "$TMP/owner-listener.log" 2>&1; then
	echo "OWNER promotion should reject a production game listener" >&2
	exit 1
fi
if FAKE_LSOF_PIDS=999 "$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" > "$TMP/owner-java-open.log" 2>&1; then
	echo "OWNER promotion should reject a Java process with the DB open" >&2
	exit 1
fi

# A stale matching link plus a newer conflicting owner must fail closed.
sqlite3 "$owner_db" "INSERT INTO player_cache VALUES (2, 542, 'web_account_id', '999');"
if "$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" > "$TMP/owner-duplicate-link.log" 2>&1; then
	echo "OWNER promotion should reject duplicate ownership links" >&2
	exit 1
fi
grep -q 'exactly one total portal ownership link' "$TMP/owner-duplicate-link.log"
sqlite3 "$owner_db" 'DELETE FROM player_cache WHERE dbid=2;'

"$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" \
	--backup-dir "$TMP/promote-proof" --apply > "$TMP/owner-apply.log"
[[ "$(sqlite3 "$owner_db" 'SELECT group_id FROM players WHERE id=542;')" == 0 ]]
grep -qx 'backup_integrity=ok' "$TMP/promote-proof/summary.txt"
grep -qx 'restore_foreign_keys=ok' "$TMP/promote-proof/summary.txt"
"$ROOT/scripts/promote-owner-sqlite.sh" "${owner_common[@]}" --rollback \
	--backup-dir "$TMP/rollback-proof" --apply > "$TMP/owner-rollback.log"
[[ "$(sqlite3 "$owner_db" 'SELECT group_id FROM players WHERE id=542;')" == 10 ]]

package_common=(
	"$ROOT/scripts/package-launch-staging.sh"
	--host voidscape.gg
	--portal-url https://voidscape.gg/
	--web-url https://voidscape.gg/play/
	--skip-build --skip-web-build --skip-android
)
if "${package_common[@]}" --android-play-url 'https://example.com/store/apps/details?id=com.voidscape.gg' > "$TMP/play-invalid.log" 2>&1; then
	echo "packager should reject a non-Google Play listing" >&2
	exit 1
fi
grep -q -- '--android-play-url must be the official' "$TMP/play-invalid.log"
if "${package_common[@]}" --android-play-url 'https://play.google.com/store/apps/details?hl=en&id=com.voidscape.gg' > "$TMP/play-valid.log" 2>&1; then
	echo "dirty/skipped fixture package unexpectedly completed" >&2
	exit 1
fi
! grep -q -- '--android-play-url must be the official' "$TMP/play-valid.log"
grep -q 'requested package would be non-promotable' "$TMP/play-valid.log"
grep -q 'ANDROID_PLAY_ENV_LINE="PORTAL_ANDROID_PLAY_URL=\$ANDROID_PLAY_URL"' "$ROOT/scripts/package-launch-staging.sh"
grep -q '^PORTAL_ROSTER_WRITES_FROZEN=0$' "$ROOT/scripts/package-launch-staging.sh"
grep -q '^android_play_url=' "$ROOT/scripts/package-launch-staging.sh"
grep -q '^portal_roster_freeze_helper_sha256=' "$ROOT/scripts/package-launch-staging.sh"
grep -q '^owner_promotion_helper_sha256=' "$ROOT/scripts/package-launch-staging.sh"

echo "launch operator tooling tests passed"
