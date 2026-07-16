#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CONNECTION_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/database/JDBCDatabaseConnection.java"
DATABASE_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/database/JDBCDatabase.java"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/database/JDBCDatabaseConnectionLockTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-jdbc-lock.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -cp "$REPO_ROOT/server/lib/*:$REPO_ROOT/server/core.jar" \
	-d "$CLASSES" "$CONNECTION_SOURCE" "$DATABASE_SOURCE" "$TEST_SOURCE"
java -cp "$CLASSES:$REPO_ROOT/server/lib/*:$REPO_ROOT/server/core.jar" \
	com.openrsc.server.database.JDBCDatabaseConnectionLockTest
