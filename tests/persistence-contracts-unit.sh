#!/usr/bin/env bash
# Compiles and runs deterministic regressions for durable player-save contracts.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/persistence-contracts.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

SOURCES=(
	"$REPO/tests/java/com/openrsc/server/model/entity/player/PersistenceTrackerTest.java"
	"$REPO/tests/java/com/openrsc/server/service/PlayerServiceSaveOutcomeTest.java"
	"$REPO/tests/java/com/openrsc/server/login/PlayerSaveRequestPolicyTest.java"
	"$REPO/tests/java/com/openrsc/server/model/world/LogoutPreparationGuardTest.java"
	"$REPO/tests/java/com/openrsc/server/util/PlayerEventStopperTest.java"
)

javac -source 8 -target 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" "${SOURCES[@]}"

CLASSPATH="$TMP:$REPO/server/core.jar:$REPO/server/lib/*"
java -cp "$CLASSPATH" com.openrsc.server.model.entity.player.PersistenceTrackerTest
java -cp "$CLASSPATH" com.openrsc.server.service.PlayerServiceSaveOutcomeTest
java -cp "$CLASSPATH" com.openrsc.server.login.PlayerSaveRequestPolicyTest
java -cp "$CLASSPATH" com.openrsc.server.model.world.LogoutPreparationGuardTest
java -cp "$CLASSPATH" com.openrsc.server.util.PlayerEventStopperTest

# Production integration assertions: no shadow tracker is allowed to satisfy these tests.
rg -Fq 'new PersistentSaveTracker()' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'new SaveReservationTracker()' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'new SaveTicketTracker()' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'PersistentSaveTracker.shouldQueueForcedFollowUp(logout, force, isSaving())' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'new LogoutPreparationGuard(() -> finishPreparedLogout(player))' "$REPO/server/src/com/openrsc/server/model/world/World.java"
rg -Fq 'currentOrStartLogoutSaveAttempt();' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'if (isLoggingOut())' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'PlayerEventStopper.stopOutsidePlayerLock(' "$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
rg -Fq 'PlayerEventStopper.stopOutsidePlayerLock(' "$REPO/server/src/com/openrsc/server/login/PlayerSaveRequest.java"
rg -Fq 'Server stop aborted while player data remains uncommitted' "$REPO/server/src/com/openrsc/server/Server.java"
STOP_ABORT_BLOCK="$(sed -n '/Server stop aborted while player data remains uncommitted/,/return;/p' "$REPO/server/src/com/openrsc/server/Server.java")"
for required_reset in 'shutdownEvent = null;' 'shuttingDown = false;' 'restarting = false;'; do
	if ! printf '%s\n' "$STOP_ABORT_BLOCK" | rg -Fq "$required_reset"; then
		echo "Aborted stop must reset: $required_reset" >&2
		exit 1
	fi
done
if sed -n '/isCurrentSaveLifecycle(UUID lifecycleId)/,/^\t}/p' \
	"$REPO/server/src/com/openrsc/server/model/entity/player/Player.java" | rg -q 'sessionId'; then
	echo "Lifecycle fence must be independent of Android/network sessionId" >&2
	exit 1
fi

echo "Persistence production integration checks passed."
