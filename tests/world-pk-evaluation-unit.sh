#!/usr/bin/env bash
# Compiles and runs the pure qualified-PK policy and arithmetic checks.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
EVIDENCE_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/PvpKillEvidence.java"
EVALUATION_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldPkEvaluation.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/model/entity/player/WorldPkEvaluationTest.java"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/world-pk-evaluation.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$EVIDENCE_SOURCE" "$EVALUATION_SOURCE" "$TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.model.entity.player.WorldPkEvaluationTest

python3 - "$EVALUATION_SOURCE" <<'PY'
import sys
from pathlib import Path

source = Path(sys.argv[1]).read_text()

contracts = (
    'groundItem.getAttribute("playerKill", false)',
    'groundItem.getAttribute("killerHash", -1L) != killerUsernameHash',
    'groundItem.getOwnerUsernameHash() != killerUsernameHash',
    'final ItemDefinition definition = groundItem.getDef()',
    'definition == null || definition.isUntradable()',
    'amount <= 0 || defaultPrice <= 0',
    'saturatingMultiplyPositive(defaultPrice, amount)',
    'saturatingAddPositive(total,',
)
for contract in contracts:
    assert contract in source, f"post-drop loot guard missing: {contract}"

assert source.index('groundItem.getAttribute("playerKill", false)') \
    < source.index('final ItemDefinition definition = groundItem.getDef()') \
    < source.index('definition == null || definition.isUntradable()') \
    < source.index('amount <= 0 || defaultPrice <= 0'), \
    "loot guards must fail closed before value arithmetic"

for forbidden in (
    "com.openrsc.server.database",
    "WorldAnnouncementService",
    "sendWorldMessage",
):
    assert forbidden not in source, f"pure evaluation helper gained a side effect: {forbidden}"

print("World PK evaluation source contracts passed.")
PY

echo "World PK evaluation unit tests passed."
