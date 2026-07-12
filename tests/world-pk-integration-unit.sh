#!/usr/bin/env bash
# Guards the exact post-drop world-PK runtime wiring without changing client contracts.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"

python3 - "$REPO" <<'PY'
import sys
from pathlib import Path

root = Path(sys.argv[1])
world = (root / "server/src/com/openrsc/server/model/world/World.java").read_text()
plugin = (root / "server/plugins/com/openrsc/server/plugins/custom/misc/WorldAnnouncements.java").read_text()
announcements = (root / "server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java").read_text()
config = (root / "server/src/com/openrsc/server/ServerConfiguration.java").read_text()

for contract in (
    "private final WorldPkSettlementService worldPkSettlementService;",
    "this.worldPkSettlementService = new WorldPkSettlementService(server.getDatabase(),",
    "server.getConfig().WANT_WORLD_ACHIEVEMENTS,",
    "server.getConfig().WORLD_ACHIEVEMENT_SEASON_ID,",
    "server.getConfig().WORLD_PK_LOOT_MINIMUM);",
    "public WorldPkSettlementService getWorldPkSettlementService()",
):
    assert contract in world, f"World PK service wiring missing: {contract}"

assert "implements PlayerKilledPlayerTrigger," in plugin \
    and "PlayerDeathDropTrigger" in plugin, "announcement plugin must own both lifecycle seams"
legacy = plugin[plugin.index("public boolean blockPlayerKilledPlayer"):
                plugin.index("public void onPlayerDeathDrop")]
assert "if (!killer.getConfig().WANT_WORLD_ACHIEVEMENTS)" in legacy \
    and "announceSkulledWildernessKill" in legacy, \
    "legacy every-skull message must survive only while durable achievements are disabled"

postdrop = plugin[plugin.index("public boolean blockPlayerDeathDrop"):]
for contract in (
    "if (!player.getConfig().WANT_WORLD_ACHIEVEMENTS || context == null)",
    "context.getPvpKillEvidence()",
    "WorldPkEvaluation.preliminaryRejectReason(evidence)",
    "WorldPkEvaluation.eligibleLootValue(",
    "evidence, context.getGroundItems()",
    "evidence.getCanonicalDeathId()",
    "evidence.getOccurredAtMs()",
    ".getWorldPkSettlementService().settle(request)",
    "result.isPublishable() && result.isQualified()",
    "announceQualifiedWildernessKill(result)",
):
    assert contract in postdrop, f"post-drop settlement wiring missing: {contract}"
assert postdrop.rstrip().endswith("}\n}" ) or postdrop.rstrip().endswith("}\n}"), \
    "plugin source unexpectedly truncated"
assert "getCurrentIP" not in plugin and "ipAddress" not in plugin, \
    "post-drop plugin must consume only reduced IP evidence"

announcement_method = announcements[
    announcements.index("public void announceQualifiedWildernessKill"):
    announcements.index("public void announceNewPlayerJoined")
]
for contract in (
    "WANT_WORLD_ANNOUNCEMENTS",
    "WANT_WORLD_SKULLED_PK_ANNOUNCEMENTS",
    "!result.isPublishable() || !result.isQualified()",
    "result.getStreakAfter() == 3",
    "result.getStreakAfter() == 5",
    "result.getStreakAfter() == 10",
):
    assert contract in announcement_method, f"qualified-PK announcement guard missing: {contract}"

assert 'WORLD_PK_LOOT_MINIMUM = tryReadInt("world_pk_loot_minimum").orElse(5000);' in config
assert "ActionSender" not in plugin and "Opcode" not in plugin, \
    "Slice 5C must not add a packet/client contract"

print("World PK runtime integration source contracts passed.")
PY

echo "World PK runtime integration tests passed."
