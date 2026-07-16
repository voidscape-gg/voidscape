#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CRYPTO_SOURCE_ROOT="$REPO_ROOT/Duel_Proof/src/main/java/com/voidscape/duelproof"
HANDSHAKE_SOURCE="$REPO_ROOT/Client_Base/src/orsc/duelproof/DuelProofHandshakeClient.java"
PORT_STUB="$REPO_ROOT/Client_Base/test/java/orsc/multiclient/ClientPort.java"
TEST_SOURCE="$REPO_ROOT/Client_Base/test/java/orsc/duelproof/DuelProofHandshakeClientTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-duel-proof-client.XXXXXX")"
CLASSES="$TEST_ROOT/classes"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

mkdir -p "$CLASSES"
javac --release 8 -d "$CLASSES" \
	"$CRYPTO_SOURCE_ROOT"/*.java \
	"$PORT_STUB" \
	"$HANDSHAKE_SOURCE" \
	"$TEST_SOURCE"
java -cp "$CLASSES" orsc.duelproof.DuelProofHandshakeClientTest
