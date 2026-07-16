#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLIENT_JAR="$REPO_ROOT/Client_Base/Open_RSC_Client.jar"
SERVER_JAR="$REPO_ROOT/server/core.jar"
SOURCE="$REPO_ROOT/tools/appearance-studio/java-tests/PaperdollV2PackAdversarialTest.java"
LEGACY_SOURCE="$REPO_ROOT/tools/appearance-studio/java-tests/PaperdollV2LegacyCompatibilityAdversarialTest.java"
SERVER_SOURCE="$REPO_ROOT/tools/appearance-studio/java-tests/PaperdollV2ServerPolicyTest.java"
if [[ $# -eq 0 ]]; then
	WORKSPACE="$REPO_ROOT/tmp/appearance-studio/v2-java-default-fixture"
	echo "==> Materializing deterministic Paperdoll V2 Java fixture"
	"$REPO_ROOT/scripts/appearance-v2.sh" collection "$WORKSPACE" --force >/dev/null
	"$REPO_ROOT/scripts/appearance-v2.sh" build "$WORKSPACE" >/dev/null
	VALID_PACK="$WORKSPACE/build/Paperdoll_V2.orsc"
else
	VALID_PACK="$1"
	WORKSPACE="${2:-$(dirname "$(dirname "$VALID_PACK")")}"
fi
TEST_ROOT="$REPO_ROOT/tmp/appearance-studio/v2-java-adversarial"
CLASSES="$TEST_ROOT/classes"
MUTANTS="$TEST_ROOT/mutants"

if [[ ! -f "$CLIENT_JAR" ]]; then
	echo "ERROR: missing client jar; run scripts/build.sh first" >&2
	exit 1
fi
if [[ ! -f "$SERVER_JAR" ]]; then
	echo "ERROR: missing server jar; run scripts/build.sh first" >&2
	exit 1
fi
if [[ ! -f "$VALID_PACK" ]]; then
	echo "ERROR: missing valid V2 pack: $VALID_PACK" >&2
	exit 1
fi
if [[ ! -f "$WORKSPACE/build/legacy-compatibility/runtime.properties" ]]; then
	echo "ERROR: missing V2 legacy compatibility runtime.properties in workspace: $WORKSPACE" >&2
	exit 1
fi

rm -rf "$CLASSES" "$MUTANTS"
mkdir -p "$CLASSES" "$MUTANTS"
javac -source 8 -target 8 -cp "$CLIENT_JAR:$SERVER_JAR" -d "$CLASSES" \
	"$SOURCE" "$LEGACY_SOURCE" "$SERVER_SOURCE"
java -cp "$CLIENT_JAR:$SERVER_JAR:$CLASSES" tools.PaperdollV2PackAdversarialTest "$VALID_PACK" "$MUTANTS"
java -cp "$CLIENT_JAR:$SERVER_JAR:$CLASSES" tools.PaperdollV2LegacyCompatibilityAdversarialTest \
	"$WORKSPACE" "$REPO_ROOT" "$MUTANTS/legacy-compatibility"
java -cp "$CLIENT_JAR:$SERVER_JAR:$CLASSES" tools.PaperdollV2ServerPolicyTest
