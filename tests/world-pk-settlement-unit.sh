#!/usr/bin/env bash
# Compiles and runs the Slice 5C pure durable PK settlement contract.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
OUTCOME_SOURCE="$REPO/server/src/com/openrsc/server/database/AtomicTransactionOutcome.java"
EVENT_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldPkEvent.java"
STREAK_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldPkStreak.java"
REQUEST_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldPkSettlementRequest.java"
RESULT_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldPkSettlementResult.java"
SERVICE_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldPkSettlementService.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/content/WorldPkSettlementServiceTest.java"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/world-pk-settlement-unit.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$OUTCOME_SOURCE" "$EVENT_SOURCE" "$STREAK_SOURCE" \
	"$REQUEST_SOURCE" "$RESULT_SOURCE" "$SERVICE_SOURCE" "$TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.content.WorldPkSettlementServiceTest

rg -Fq 'public static final long PAIR_COOLDOWN_MS = 30L * 60L * 1000L;' "$SERVICE_SOURCE"
rg -Fq 'request.getLootValue() < lootMinimum' "$SERVICE_SOURCE"
rg -Fq 'return database.atomicallySettled(body::run, verifier::verify);' "$SERVICE_SOURCE"
rg -Fq 'sameStreak(attempt.victimAfter, durableVictim)' "$SERVICE_SOURCE"
rg -Fq 'sameStreak(attempt.victimBefore, durableVictim)' "$SERVICE_SOURCE"
if rg -q 'WorldAnnouncementService|announce[A-Za-z]*\(' "$SERVICE_SOURCE"; then
	echo "World PK settlement service must not own presentation callbacks." >&2
	exit 1
fi

echo "World PK settlement source contracts passed."
