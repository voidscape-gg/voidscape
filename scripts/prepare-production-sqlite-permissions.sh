#!/usr/bin/env bash
# Establish the shared game/portal SQLite ownership contract and prove that the
# service identity can create a main-database rollback journal.

set -euo pipefail

DB_PATH="/opt/voidscape/server/inc/sqlite/voidscape.db"
SERVICE_USER="voidscape"
SERVICE_GROUP="voidscape-db"
DIRECTORY_OWNER="root"

usage() {
	cat <<'EOF'
Usage: scripts/prepare-production-sqlite-permissions.sh [options]

Options:
  --db PATH                 SQLite database. Default: /opt/voidscape/server/inc/sqlite/voidscape.db.
  --service-user USER       Game/portal service user. Default: voidscape.
  --service-group GROUP     Dedicated game/portal DB group. Default: voidscape-db.
  --directory-owner USER    SQLite directory owner. Default: root.
                            Override only for an isolated test/staging root.
  -h, --help                Show this help.

The database must already exist and both the portal and game services must be
stopped. The helper sets the containing directory to OWNER:GROUP 0770 and the
database to USER:GROUP 0600, then creates a uniquely named table inside a
transaction and rolls it back. A successful probe leaves no schema row behind.
Keep simulated-player and unrelated service accounts out of this group; directory
write permission is sufficient to replace a SQLite database file.
EOF
}

while [[ $# -gt 0 ]]; do
	case "$1" in
		--db)
			DB_PATH="${2:-}"
			shift 2
			;;
		--service-user)
			SERVICE_USER="${2:-}"
			shift 2
			;;
		--service-group)
			SERVICE_GROUP="${2:-}"
			shift 2
			;;
		--directory-owner)
			DIRECTORY_OWNER="${2:-}"
			shift 2
			;;
		-h|--help)
			usage
			exit 0
			;;
		*)
			echo "ERROR: unknown argument: $1" >&2
			usage >&2
			exit 2
			;;
	esac
done

valid_identity() {
	[[ "$1" =~ ^[A-Za-z_][A-Za-z0-9_-]*$ ]]
}

if [[ -z "$DB_PATH" || ! -f "$DB_PATH" || -L "$DB_PATH" ]]; then
	echo "ERROR: database must be an existing regular, non-symlink file: $DB_PATH" >&2
	exit 1
fi
for identity in "$SERVICE_USER" "$SERVICE_GROUP" "$DIRECTORY_OWNER"; do
	if ! valid_identity "$identity"; then
		echo "ERROR: invalid user/group name: $identity" >&2
		exit 2
	fi
done
if ! id "$SERVICE_USER" >/dev/null 2>&1; then
	echo "ERROR: service user does not exist: $SERVICE_USER" >&2
	exit 1
fi
if ! id "$DIRECTORY_OWNER" >/dev/null 2>&1; then
	echo "ERROR: directory owner does not exist: $DIRECTORY_OWNER" >&2
	exit 1
fi
if ! id -Gn "$SERVICE_USER" | tr ' ' '\n' | grep -Fqx "$SERVICE_GROUP"; then
	echo "ERROR: $SERVICE_USER is not a member of service group $SERVICE_GROUP" >&2
	exit 1
fi
if ! command -v sqlite3 >/dev/null 2>&1; then
	echo "ERROR: sqlite3 is required." >&2
	exit 1
fi

SQLITE_DIR="$(cd "$(dirname "$DB_PATH")" && pwd)"
DB_PATH="$SQLITE_DIR/$(basename "$DB_PATH")"

owner_group() {
	if stat -c '%U:%G' "$1" >/dev/null 2>&1; then
		stat -c '%U:%G' "$1"
	else
		stat -f '%Su:%Sg' "$1"
	fi
}

ensure_owner_group() {
	local path="$1"
	local expected="$2"
	if [[ "$(owner_group "$path")" != "$expected" ]]; then
		chown "$expected" "$path"
	fi
}

run_as_service() {
	if [[ "$(id -un)" == "$SERVICE_USER" ]]; then
		"$@"
		return
	fi
	if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
		echo "ERROR: run as root to execute the SQLite probe as $SERVICE_USER." >&2
		return 1
	fi
	if command -v runuser >/dev/null 2>&1; then
		runuser -u "$SERVICE_USER" -- "$@"
		return
	fi
	echo "ERROR: runuser is required when the invoking user differs from $SERVICE_USER." >&2
	return 1
}

ensure_owner_group "$SQLITE_DIR" "$DIRECTORY_OWNER:$SERVICE_GROUP"
chmod 0770 "$SQLITE_DIR"
ensure_owner_group "$DB_PATH" "$SERVICE_USER:$SERVICE_GROUP"
chmod 0600 "$DB_PATH"

probe_table="__voidscape_sqlite_permission_probe_$$_${RANDOM}"
probe_output=""
if ! probe_output="$(run_as_service sqlite3 -batch "$DB_PATH" 2>&1 <<EOF
.bail on
.timeout 5000
PRAGMA foreign_keys=ON;
BEGIN IMMEDIATE;
CREATE TABLE "$probe_table" (id INTEGER NOT NULL);
ROLLBACK;
SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '$probe_table';
EOF
)"; then
	echo "ERROR: SQLite rollback-journal write probe failed as $SERVICE_USER." >&2
	printf '%s\n' "$probe_output" >&2
	exit 1
fi
probe_output="${probe_output//$'\r'/}"
if [[ "$probe_output" != "0" ]]; then
	echo "ERROR: SQLite rollback probe left unexpected schema state: $probe_output" >&2
	exit 1
fi

echo "SQLite runtime permissions ready: $DB_PATH ($SERVICE_USER:$SERVICE_GROUP 0600; directory $DIRECTORY_OWNER:$SERVICE_GROUP 0770)."
