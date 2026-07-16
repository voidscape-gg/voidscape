#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE="$REPO_ROOT/server/src/com/openrsc/server/avatargenerator/AvatarFileStore.java"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/avatargenerator/AvatarFileStoreTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-avatar-store.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -d "$CLASSES" "$SOURCE" "$TEST_SOURCE"
java -cp "$CLASSES" com.openrsc.server.avatargenerator.AvatarFileStoreTest
