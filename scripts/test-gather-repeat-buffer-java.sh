#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE="$REPO_ROOT/server/src/com/openrsc/server/plugins/GatherRepeatBuffer.java"
POLICY_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/plugins/GatherInputPolicy.java"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/plugins/GatherRepeatBufferTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-gather-repeat.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -d "$CLASSES" "$SOURCE" "$POLICY_SOURCE" "$TEST_SOURCE"
java -cp "$CLASSES" com.openrsc.server.plugins.GatherRepeatBufferTest
