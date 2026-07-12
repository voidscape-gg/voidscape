#!/usr/bin/env bash
# Compiles the immutable PvP death evidence model and guards its pre-reset capture point.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
EVIDENCE_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/PvpKillEvidence.java"
CONTEXT_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/PlayerDeathContext.java"
PLAYER_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/model/entity/player/PvpKillEvidenceTest.java"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/pvp-evidence-boundary.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$EVIDENCE_SOURCE" "$CONTEXT_SOURCE" "$TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.model.entity.player.PvpKillEvidenceTest

python3 - "$PLAYER_SOURCE" "$EVIDENCE_SOURCE" "$CONTEXT_SOURCE" <<'PY'
import sys
from pathlib import Path

player = Path(sys.argv[1]).read_text()
evidence = Path(sys.argv[2]).read_text()
context = Path(sys.argv[3]).read_text()

killed_start = player.index("public void killedBy(final Mob mob)")
killed_end = player.index("private int getEquippedWeaponID()", killed_start)
killed = player[killed_start:killed_end]

capture = killed.index("PvpKillEvidence.capture")
death_id = killed.index("final UUID deathId = UUID.randomUUID();")
occurred_at = killed.index("final long deathOccurredAtMs = System.currentTimeMillis();")
last_death = killed.index('getCache().store("last_death", deathOccurredAtMs);')
damage_reset = killed.index("resetTrackedDamageAndBlockedDamage")
inventory_drop = killed.index("dropOnDeath(mob)")
context_create = killed.index("new PlayerDeathContext(")
post_drop_trigger = killed.index("PlayerDeathDropTrigger.class")
respawn_reset = killed.index("resetCombatEvent();", post_drop_trigger)
assert capture < damage_reset < inventory_drop < context_create < post_drop_trigger < respawn_reset, \
    "PvP evidence must be captured before damage reset and delivered only after drops"
assert death_id < occurred_at < last_death < capture, \
    "one frozen death time must feed cache state before PvP evidence capture"
assert killed[occurred_at:capture].count("System.currentTimeMillis()") == 1, \
    "death identity may read the wall clock exactly once"

for contract in (
    "final UUID deathId = UUID.randomUUID();",
    "final long deathOccurredAtMs = System.currentTimeMillis();",
    'getCache().store("last_death", deathOccurredAtMs);',
    "PvpKillEvidence.capture(deathId, deathOccurredAtMs,",
    "deathId, deathPoint, deathInstanceId, mob, deathGroundItems, pvpKillEvidence",
):
    assert contract in killed, f"death evidence wiring missing: {contract}"

for contract in (
    "Math.max(0, killer.getTrackedDamage(victim))",
    "sameNonLoopbackAddress(killer.getCurrentIP(), victim.getCurrentIP())",
    "killer.getSocial().isFriendsWith(victim.getUsernameHash())",
    "victim.getSocial().isFriendsWith(killer.getUsernameHash())",
    "killer.getUsernameHash()",
    "killer.isDefaultUser()",
    "victim.isDefaultUser()",
    "deathPoint.inWilderness()",
    "deathPoint.isInSafeZone()",
    "deathPoint.wildernessLevel()",
    "if (occurredAtMs <= 0L)",
    "this.occurredAtMs = occurredAtMs",
    "public long getOccurredAtMs()",
):
    assert contract in evidence, f"immutable PvP evidence input missing: {contract}"

assert "private final String currentIp" not in evidence \
    and "private final String ip" not in evidence \
    and "private final String address" not in evidence, \
    "raw address material may not be retained by PvP evidence"
assert "com.openrsc.server.database" not in evidence, \
    "the evidence boundary must not write or depend on persistence"
assert "getPvpKillEvidence()" in context and "getCanonicalDeathId()" in context, \
    "post-drop context must expose evidence and canonical event identity"

print("PvP evidence source contracts passed.")
PY

echo "PvP evidence boundary tests passed."
