#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
POLICY_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/content/voidarena/VoidArenaSirPolicy.java"
TEST_SOURCE="$REPO_ROOT/server/test/java/com/openrsc/server/content/voidarena/VoidArenaSirPolicyTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-arena-sir-policy.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -d "$CLASSES" "$POLICY_SOURCE" "$TEST_SOURCE"
java -cp "$CLASSES" com.openrsc.server.content.voidarena.VoidArenaSirPolicyTest
