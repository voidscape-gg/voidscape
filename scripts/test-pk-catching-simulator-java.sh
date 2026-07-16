#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SERVER_JAR="$REPO_ROOT/server/core.jar"
PLUGINS_JAR="$REPO_ROOT/server/plugins.jar"
SIMULATOR_SOURCE="$REPO_ROOT/server/plugins/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulator.java"
ATTACK_HANDLER_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/net/rsc/handlers/AttackHandler.java"
WALK_TO_MOB_SOURCE="$REPO_ROOT/server/src/com/openrsc/server/model/action/WalkToMobAction.java"
PERSISTENCE_TEST="$REPO_ROOT/server/test/java/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulatorPersistenceTest.java"
MECHANICS_TEST="$REPO_ROOT/server/test/java/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulatorMechanicsTest.java"
REACH_TEST="$REPO_ROOT/server/test/java/com/openrsc/server/model/action/WalkToMobActionCatchReachTest.java"
CONTRACT_TEST="$REPO_ROOT/Client_Base/test/java/com/openrsc/interfaces/misc/PkCatchingProtocolContractTest.java"
TEST_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/voidscape-pk-catching-tests.XXXXXX")"
CLASSES="$TEST_ROOT/classes"
DATA="$TEST_ROOT/data"

cleanup() {
	rm -rf "$TEST_ROOT"
}
trap cleanup EXIT

if [[ ! -f "$SERVER_JAR" || ! -f "$PLUGINS_JAR" ]]; then
	echo "ERROR: missing server/core.jar or server/plugins.jar; run scripts/build.sh first" >&2
	exit 1
fi
if [[ "$SIMULATOR_SOURCE" -nt "$PLUGINS_JAR" ]]; then
	echo "ERROR: server/plugins.jar is older than PkCatchingSimulator.java; run scripts/build.sh first" >&2
	exit 1
fi
if [[ "$ATTACK_HANDLER_SOURCE" -nt "$SERVER_JAR" || "$WALK_TO_MOB_SOURCE" -nt "$SERVER_JAR" ]]; then
	echo "ERROR: server/core.jar is older than the PK catching core mechanics; run scripts/build.sh first" >&2
	exit 1
fi

mkdir -p "$CLASSES" "$DATA"
javac --release 8 \
	-cp "$REPO_ROOT/server/lib/*:$SERVER_JAR:$PLUGINS_JAR" \
	-d "$CLASSES" "$PERSISTENCE_TEST" "$MECHANICS_TEST" "$REACH_TEST" "$CONTRACT_TEST"

java -cp "$CLASSES:$REPO_ROOT/server/lib/*:$SERVER_JAR:$PLUGINS_JAR" \
	com.openrsc.server.plugins.custom.minigames.PkCatchingSimulatorPersistenceTest "$DATA"
java -cp "$CLASSES:$REPO_ROOT/server/lib/*:$SERVER_JAR:$PLUGINS_JAR" \
	com.openrsc.server.plugins.custom.minigames.PkCatchingSimulatorMechanicsTest
java -cp "$CLASSES:$REPO_ROOT/server/lib/*:$SERVER_JAR:$PLUGINS_JAR" \
	com.openrsc.server.model.action.WalkToMobActionCatchReachTest
java -cp "$CLASSES" \
	com.openrsc.interfaces.misc.PkCatchingProtocolContractTest "$REPO_ROOT"
python3 "$REPO_ROOT/tools/voidbot/test_pk_catching_commands.py"

echo "All PK Catching Simulator focused tests passed"
