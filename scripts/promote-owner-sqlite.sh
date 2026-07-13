#!/usr/bin/env bash
# Guarded offline-game promotion of one linked player from USER to OWNER.

set -euo pipefail
umask 077

DB_PATH="/opt/voidscape/server/inc/sqlite/voidscape.db"
GAME_SERVICE="voidscape.service"
SYSTEMCTL_BIN="systemctl"
SS_BIN="ss"
LSOF_BIN="lsof"
PS_BIN="ps"
GAME_PORTS=(43596 43496)
CUSTOM_GAME_PORTS=0
PLAYER_ID=""
ACCOUNT_ID=""
USERNAME=""
BACKUP_DIR=""
APPLY=0
ROLLBACK=0

usage() {
	cat <<'EOF'
Usage: scripts/promote-owner-sqlite.sh --player-id ID --account-id ID --username NAME [options]

Required identity arguments:
  --player-id ID          Exact OpenRSC player id.
  --account-id ID         Exact linked portal account id.
  --username NAME         Exact game username (case-insensitive assertion).

Options:
  --db PATH               SQLite game database.
                         Default: /opt/voidscape/server/inc/sqlite/voidscape.db.
  --game-service NAME     Game systemd service. Default: voidscape.service.
  --systemctl PATH        systemctl-compatible executable (useful for tests).
  --game-port PORT        Game listener port that must be closed. Repeat to
                         replace defaults 43596 and 43496.
  --ss PATH               ss-compatible executable for listener checks.
  --lsof PATH             lsof-compatible executable for DB-open checks.
  --ps PATH               ps-compatible executable for process classification.
  --backup-dir DIR        New evidence directory for an apply operation.
                         Default: /opt/voidscape/backups/owner-promotion-<UTC>.
  --apply                 Mutate after all assertions and backup/restore proof pass.
                         Without this flag the command is a read-only dry run.
  --rollback              Targeted OWNER(0) -> USER(10) rollback. Still requires
                         --apply to mutate and creates a new pre-rollback backup.
  -h, --help              Show this help.

Promotion requires the game service to be inactive and disabled/masked, exactly
one offline/unbanned linked USER row, and no existing OWNER. Rollback requires
that exact linked row to be the only OWNER. No password is read, handled, or
printed. Never restore an older whole DB after signups continue; use the guarded
targeted rollback mode with the same identity arguments.
EOF
}

die() {
	echo "ERROR: $*" >&2
	exit 1
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--db)
			DB_PATH="${2:-}"
			shift 2
			;;
		--game-service)
			GAME_SERVICE="${2:-}"
			shift 2
			;;
		--systemctl)
			SYSTEMCTL_BIN="${2:-}"
			shift 2
			;;
		--game-port)
			if [[ "$CUSTOM_GAME_PORTS" -eq 0 ]]; then
				GAME_PORTS=()
				CUSTOM_GAME_PORTS=1
			fi
			GAME_PORTS+=("${2:-}")
			shift 2
			;;
		--ss)
			SS_BIN="${2:-}"
			shift 2
			;;
		--lsof)
			LSOF_BIN="${2:-}"
			shift 2
			;;
		--ps)
			PS_BIN="${2:-}"
			shift 2
			;;
		--player-id)
			PLAYER_ID="${2:-}"
			shift 2
			;;
		--account-id)
			ACCOUNT_ID="${2:-}"
			shift 2
			;;
		--username)
			USERNAME="${2:-}"
			shift 2
			;;
		--backup-dir)
			BACKUP_DIR="${2:-}"
			shift 2
			;;
		--apply)
			APPLY=1
			shift
			;;
		--rollback)
			ROLLBACK=1
			shift
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			die "unknown option: $1"
			;;
	esac
done

[[ -n "$DB_PATH" && -f "$DB_PATH" && ! -L "$DB_PATH" ]] \
	|| die "--db must be an existing regular, non-symlink file"
[[ "$PLAYER_ID" =~ ^[1-9][0-9]*$ ]] || die "--player-id must be a positive integer"
[[ "$ACCOUNT_ID" =~ ^[1-9][0-9]*$ ]] || die "--account-id must be a positive integer"
[[ -n "$USERNAME" && ${#USERNAME} -le 20 && "$USERNAME" =~ ^[A-Za-z0-9_\ -]+$ ]] \
	|| die "--username must be 1-20 letters, numbers, spaces, underscores, or hyphens"
[[ -n "$GAME_SERVICE" && "$GAME_SERVICE" =~ ^[A-Za-z0-9_.@-]+$ ]] \
	|| die "--game-service contains invalid characters"
for game_port in "${GAME_PORTS[@]}"; do
	[[ "$game_port" =~ ^[0-9]+$ ]] && (( game_port >= 1 && game_port <= 65535 )) \
		|| die "--game-port must be a number from 1 to 65535"
done
[[ -x "$SYSTEMCTL_BIN" ]] || command -v "$SYSTEMCTL_BIN" >/dev/null 2>&1 \
	|| die "systemctl executable not found"
command -v sqlite3 >/dev/null 2>&1 || die "sqlite3 is required"

case "$DB_PATH" in
	*"'"*|*$'\n'*) die "database paths containing quotes or newlines are unsupported" ;;
esac

if [[ -z "$BACKUP_DIR" ]]; then
	stamp="$(date -u +%Y%m%dT%H%M%SZ)"
	BACKUP_DIR="/opt/voidscape/backups/owner-promotion-$stamp"
fi
case "$BACKUP_DIR" in
	*"'"*|*$'\n'*) die "backup paths containing quotes or newlines are unsupported" ;;
esac

EXPECTED_GROUP=10
NEXT_GROUP=0
EXPECTED_OWNER_COUNT=0
NEXT_OWNER_COUNT=1
ACTION_LABEL="promotion"
if [[ "$ROLLBACK" -eq 1 ]]; then
	EXPECTED_GROUP=0
	NEXT_GROUP=10
	EXPECTED_OWNER_COUNT=1
	NEXT_OWNER_COUNT=0
	ACTION_LABEL="rollback"
fi

sql_value() {
	sqlite3 -batch -noheader -cmd '.timeout 10000' "$1" "$2"
}

assert_game_stopped() {
	local active_state enabled_state listeners local_address port open_pids pid process_text process_lower
	active_state="$($SYSTEMCTL_BIN is-active "$GAME_SERVICE" 2>/dev/null || true)"
	enabled_state="$($SYSTEMCTL_BIN is-enabled "$GAME_SERVICE" 2>/dev/null || true)"
	[[ "$active_state" == "inactive" ]] \
		|| die "$GAME_SERVICE must be inactive (observed: ${active_state:-unknown})"
	case "$enabled_state" in
		disabled|masked) ;;
		*) die "$GAME_SERVICE must be disabled or masked (observed: ${enabled_state:-unknown})" ;;
	esac
	[[ -x "$SS_BIN" ]] || command -v "$SS_BIN" >/dev/null 2>&1 \
		|| die "ss is required to prove production game ports are closed"
	[[ -x "$LSOF_BIN" ]] || command -v "$LSOF_BIN" >/dev/null 2>&1 \
		|| die "lsof is required to prove no Java game process has the DB open"
	[[ -x "$PS_BIN" ]] || command -v "$PS_BIN" >/dev/null 2>&1 \
		|| die "ps is required to classify processes with the DB open"
	listeners="$($SS_BIN -H -ltn 2>/dev/null)" \
		|| die "could not inspect listening TCP ports with ss"
	while IFS= read -r local_address; do
		[[ -n "$local_address" ]] || continue
		port="${local_address##*:}"
		for game_port in "${GAME_PORTS[@]}"; do
			[[ "$port" != "$game_port" ]] \
				|| die "production game port $game_port still has a TCP listener"
		done
	done < <(awk '{ print $4 }' <<<"$listeners")
	open_pids="$($LSOF_BIN -nP -t -- "$DB_PATH" 2>/dev/null || true)"
	while IFS= read -r pid; do
		[[ "$pid" =~ ^[0-9]+$ ]] || continue
		process_text="$($PS_BIN -p "$pid" -o comm= -o args= 2>/dev/null || true)"
		process_lower="$(printf '%s' "$process_text" | tr '[:upper:]' '[:lower:]')"
		if [[ "$process_lower" == *java* ]]; then
			die "a Java game-like process still has the production database open"
		fi
	done <<<"$open_pids"
}

assert_db_health() {
	local db="$1"
	local integrity foreign_keys
	integrity="$(sql_value "$db" 'PRAGMA integrity_check;')"
	[[ "$integrity" == "ok" ]] || die "SQLite integrity_check failed"
	foreign_keys="$(sql_value "$db" 'PRAGMA foreign_key_check;')"
	[[ -z "$foreign_keys" ]] || die "SQLite foreign_key_check found violations"
}

identity_predicate() {
	cat <<EOF
id = $PLAYER_ID
AND lower(username) = lower('$USERNAME')
AND group_id = $EXPECTED_GROUP
AND online = 0
AND lower(COALESCE(CAST(banned AS TEXT), '0')) IN ('', '0', 'false', 'none', 'null')
EOF
}

assert_identity() {
	local db="$1"
	local player_count link_count link_total owner_count stat_count
	player_count="$(sql_value "$db" "SELECT COUNT(*) FROM players WHERE $(identity_predicate);")"
	[[ "$player_count" == "1" ]] \
		|| die "expected exactly one matching offline, unbanned player in group $EXPECTED_GROUP"
	link_count="$(sql_value "$db" "SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='web_account_id' AND CAST(value AS TEXT)='$ACCOUNT_ID';")"
	link_total="$(sql_value "$db" "SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='web_account_id';")"
	[[ "$link_total" == "1" && "$link_count" == "1" ]] \
		|| die "expected exactly one total portal ownership link with the requested account"
	owner_count="$(sql_value "$db" 'SELECT COUNT(*) FROM players WHERE group_id=0;')"
	[[ "$owner_count" == "$EXPECTED_OWNER_COUNT" ]] \
		|| die "unexpected existing OWNER count (expected $EXPECTED_OWNER_COUNT, observed $owner_count)"
	for table in curstats maxstats experience capped_experience; do
		stat_count="$(sql_value "$db" "SELECT COUNT(*) FROM $table WHERE playerID=$PLAYER_ID;")"
		[[ "$stat_count" == "1" ]] || die "expected exactly one $table row for the target player"
	done
}

assert_post_identity() {
	local db="$1"
	local target_count link_count link_total owner_count
	target_count="$(sql_value "$db" "SELECT COUNT(*) FROM players WHERE id=$PLAYER_ID AND lower(username)=lower('$USERNAME') AND group_id=$NEXT_GROUP AND online=0 AND lower(COALESCE(CAST(banned AS TEXT), '0')) IN ('', '0', 'false', 'none', 'null');")"
	[[ "$target_count" == "1" ]] || die "postcheck did not find the exact target in group $NEXT_GROUP"
	link_count="$(sql_value "$db" "SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='web_account_id' AND CAST(value AS TEXT)='$ACCOUNT_ID';")"
	link_total="$(sql_value "$db" "SELECT COUNT(*) FROM player_cache WHERE playerID=$PLAYER_ID AND key='web_account_id';")"
	[[ "$link_total" == "1" && "$link_count" == "1" ]] \
		|| die "postcheck ownership link changed unexpectedly"
	owner_count="$(sql_value "$db" 'SELECT COUNT(*) FROM players WHERE group_id=0;')"
	[[ "$owner_count" == "$NEXT_OWNER_COUNT" ]] \
		|| die "postcheck OWNER count mismatch (expected $NEXT_OWNER_COUNT, observed $owner_count)"
}

sha256_file() {
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$1" | awk '{print $1}'
	else
		shasum -a 256 "$1" | awk '{print $1}'
	fi
}

assert_game_stopped
assert_db_health "$DB_PATH"
assert_identity "$DB_PATH"

if [[ "$APPLY" -ne 1 ]]; then
	echo "OWNER $ACTION_LABEL dry run passed: playerId=$PLAYER_ID; accountId=$ACCOUNT_ID; game inactive/disabled; DB and linkage verified; no changes made."
	exit 0
fi

[[ ! -e "$BACKUP_DIR" ]] || die "--backup-dir already exists; choose a new evidence directory"
mkdir -m 0700 -p "$(dirname "$BACKUP_DIR")"
mkdir -m 0700 "$BACKUP_DIR"
BACKUP_DB="$BACKUP_DIR/before.sqlite"
RESTORED_DB="$BACKUP_DIR/restored.sqlite"
SUMMARY="$BACKUP_DIR/summary.txt"

sqlite3 -batch "$DB_PATH" ".timeout 10000" ".backup '$BACKUP_DB'"
chmod 0600 "$BACKUP_DB"
assert_db_health "$BACKUP_DB"
assert_identity "$BACKUP_DB"

sqlite3 -batch "$RESTORED_DB" ".timeout 10000" ".restore '$BACKUP_DB'"
chmod 0600 "$RESTORED_DB"
assert_db_health "$RESTORED_DB"
assert_identity "$RESTORED_DB"

backup_sha256="$(sha256_file "$BACKUP_DB")"
restore_sha256="$(sha256_file "$RESTORED_DB")"
{
	printf 'action=%s\n' "$ACTION_LABEL"
	printf 'player_id=%s\n' "$PLAYER_ID"
	printf 'account_id=%s\n' "$ACCOUNT_ID"
	printf 'expected_group=%s\n' "$EXPECTED_GROUP"
	printf 'next_group=%s\n' "$NEXT_GROUP"
	printf 'backup_sha256=%s\n' "$backup_sha256"
	printf 'restored_sha256=%s\n' "$restore_sha256"
	printf 'backup_integrity=ok\n'
	printf 'backup_foreign_keys=ok\n'
	printf 'restore_integrity=ok\n'
	printf 'restore_foreign_keys=ok\n'
} > "$SUMMARY"
chmod 0600 "$SUMMARY"

# Recheck immediately before taking the write lock. The portal may keep serving
# signups, but the game must remain stopped throughout this operation.
assert_game_stopped
assert_identity "$DB_PATH"

sqlite3 -batch -bail "$DB_PATH" <<SQL
.timeout 10000
.bail on
BEGIN IMMEDIATE;
CREATE TEMP TABLE owner_promotion_guard (value INTEGER NOT NULL CHECK (value = 1));
INSERT INTO owner_promotion_guard
SELECT CASE WHEN COUNT(*) = 1 THEN 1 ELSE 0 END
FROM players
WHERE $(identity_predicate);
INSERT INTO owner_promotion_guard
SELECT CASE WHEN COUNT(*) = 1
  AND SUM(CASE WHEN CAST(value AS TEXT)='$ACCOUNT_ID' THEN 1 ELSE 0 END) = 1
  THEN 1 ELSE 0 END
FROM player_cache
WHERE playerID=$PLAYER_ID
  AND key='web_account_id';
INSERT INTO owner_promotion_guard
SELECT CASE WHEN COUNT(*) = $EXPECTED_OWNER_COUNT THEN 1 ELSE 0 END
FROM players
WHERE group_id=0;
UPDATE players
SET group_id=$NEXT_GROUP
WHERE $(identity_predicate);
INSERT INTO owner_promotion_guard VALUES (changes());
COMMIT;
SQL

assert_db_health "$DB_PATH"
assert_post_identity "$DB_PATH"
printf 'post_integrity=ok\npost_foreign_keys=ok\npost_group=%s\npost_owner_count=%s\n' \
	"$NEXT_GROUP" "$NEXT_OWNER_COUNT" >> "$SUMMARY"

echo "OWNER $ACTION_LABEL applied safely: playerId=$PLAYER_ID; accountId=$ACCOUNT_ID; group=$NEXT_GROUP; backup/restore and postchecks passed."
echo "Evidence directory: $BACKUP_DIR"
if [[ "$ROLLBACK" -eq 0 ]]; then
	echo "Targeted rollback: rerun with the same identity arguments, a new --backup-dir, and --rollback --apply while the game remains inactive and disabled."
fi
