#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_JAR="$REPO_ROOT/server/core.jar"
SNAPSHOT_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/content/voidarena/VoidArenaKitSnapshot.java"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/content/voidarena/VoidArenaKitSnapshotTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-arena-kit-snapshot.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

if [[ ! -f "$SERVER_JAR" ]]; then
	echo "ERROR: missing server/core.jar; run scripts/build.sh first" >&2
	exit 1
fi

mkdir -p "$CLASSES"
javac --release 8 -cp "$REPO_ROOT/server/lib/*:$SERVER_JAR" \
	-d "$CLASSES" "$SNAPSHOT_SOURCE" "$TEST_SOURCE"
java -cp "$CLASSES:$REPO_ROOT/server/lib/*:$SERVER_JAR" \
	com.openrsc.server.content.voidarena.VoidArenaKitSnapshotTest
