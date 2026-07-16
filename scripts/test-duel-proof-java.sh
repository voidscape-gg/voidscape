#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SOURCE_ROOT="$REPO_ROOT/Duel_Proof/src/main/java/com/voidscape/duelproof"
TEST_SOURCE="$REPO_ROOT/Duel_Proof/src/test/java/com/voidscape/duelproof/DuelProofVectorsTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-duel-proof.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -d "$CLASSES" "$SOURCE_ROOT"/*.java "$TEST_SOURCE"
java -cp "$CLASSES" com.voidscape.duelproof.DuelProofVectorsTest
