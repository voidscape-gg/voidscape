#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HELPER="$ROOT/scripts/prepare-production-sqlite-permissions.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-sqlite-permissions.XXXXXX")"
SQLITE_DIR="$TMP_DIR/server/inc/sqlite"
DB_PATH="$SQLITE_DIR/voidscape.db"

cleanup() {
	chmod -R u+rwx "$TMP_DIR" >/dev/null 2>&1 || true
	rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mode() {
	if stat -c '%a' "$1" >/dev/null 2>&1; then
		stat -c '%a' "$1"
	else
		stat -f '%Lp' "$1"
	fi
}

run_as_test_service() {
	if [[ "$(id -un)" == "$SERVICE_USER" ]]; then
		"$@"
	else
		runuser -u "$SERVICE_USER" -- "$@"
	fi
}

mkdir -p "$SQLITE_DIR"
sqlite3 "$DB_PATH" 'CREATE TABLE baseline (value INTEGER NOT NULL); INSERT INTO baseline VALUES (1);'
chmod 0600 "$DB_PATH"

DIRECTORY_OWNER="$(id -un)"
SERVICE_USER="$(id -un)"
SERVICE_GROUP="$(id -gn)"
FAILURE_MODE="0550"

if [[ "${EUID:-$(id -u)}" -eq 0 ]]; then
	SERVICE_USER="nobody"
	if ! id "$SERVICE_USER" >/dev/null 2>&1 || ! command -v runuser >/dev/null 2>&1; then
		echo "ERROR: root test execution requires the nobody user and runuser." >&2
		exit 1
	fi
	SERVICE_GROUP="$(id -gn "$SERVICE_USER")"
	DIRECTORY_OWNER="root"
	chown "$SERVICE_USER:$SERVICE_GROUP" "$DB_PATH"
	chown "$DIRECTORY_OWNER:$SERVICE_GROUP" "$SQLITE_DIR"
	FAILURE_MODE="0750"
else
	# An unprivileged hermetic test cannot assign a synthetic root owner. Mode
	# 0550 gives the current identity the same r-x effective directory access
	# that the service group has under the production root:service 0750 mode.
	if (( (8#0750 & 8#0020) != 0 )); then
		echo "ERROR: regression fixture incorrectly grants group write in mode 0750." >&2
		exit 1
	fi
fi

chmod "$FAILURE_MODE" "$SQLITE_DIR"
failure_output=""
if failure_output="$(run_as_test_service sqlite3 "$DB_PATH" 'BEGIN IMMEDIATE; CREATE TABLE should_not_exist (id INTEGER); ROLLBACK;' 2>&1)"; then
	echo "ERROR: SQLite write unexpectedly succeeded without directory write permission." >&2
	exit 1
fi
if [[ "$failure_output" != *"attempt to write a readonly database"* ]]; then
	echo "ERROR: unexpected SQLite permission failure: $failure_output" >&2
	exit 1
fi

"$HELPER" \
	--db "$DB_PATH" \
	--service-user "$SERVICE_USER" \
	--service-group "$SERVICE_GROUP" \
	--directory-owner "$DIRECTORY_OWNER"
"$HELPER" \
	--db "$DB_PATH" \
	--service-user "$SERVICE_USER" \
	--service-group "$SERVICE_GROUP" \
	--directory-owner "$DIRECTORY_OWNER"

[[ "$(mode "$SQLITE_DIR")" == "770" ]]
[[ "$(mode "$DB_PATH")" == "600" ]]
[[ "$(run_as_test_service sqlite3 -readonly "$DB_PATH" 'SELECT value FROM baseline;')" == "1" ]]
[[ "$(run_as_test_service sqlite3 -readonly "$DB_PATH" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name LIKE '__voidscape_sqlite_permission_probe_%';")" == "0" ]]
[[ "$(run_as_test_service sqlite3 -readonly "$DB_PATH" "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='should_not_exist';")" == "0" ]]
[[ ! -e "$DB_PATH-journal" ]]

echo "production SQLite permission checks passed"
