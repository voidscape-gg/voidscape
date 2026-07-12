#!/usr/bin/env bash
# Compiles exact preview copy checks and guards the read-only admin preview seam.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
ANNOUNCEMENT_SOURCE="$REPO/server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java"
PK_RESULT_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldPkSettlementResult.java"
PK_EVENT_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldPkEvent.java"
ADMIN_SOURCE="$REPO/server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
COMMANDS_DOC="$REPO/Commands.md"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/content/announcements/WorldAnnouncementPreviewTest.java"

if [ ! -f "$REPO/server/core.jar" ] || [ ! -f "$REPO/server/plugins.jar" ]; then
	echo "server/core.jar and server/plugins.jar are required for the focused preview compile" >&2
	exit 1
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/world-announcement-preview.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$PK_EVENT_SOURCE" "$PK_RESULT_SOURCE" "$ANNOUNCEMENT_SOURCE" \
	"$ADMIN_SOURCE" "$TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.content.announcements.WorldAnnouncementPreviewTest

python3 - "$ANNOUNCEMENT_SOURCE" "$ADMIN_SOURCE" "$COMMANDS_DOC" <<'PY'
import sys
from pathlib import Path

announcements, admin, commands = (Path(path).read_text() for path in sys.argv[1:])

preview_start = announcements.index("public void previewSkillMilestone")
preview_end = announcements.index("public void announceUniqueTitleClaim", preview_start)
previews = announcements[preview_start:preview_end]

for existing in (
    "previewSkillMilestone(player)",
    "previewTotalLevelMilestone(player)",
    "previewSkulledWildernessKill(player)",
    "previewNewPlayerJoined(player)",
):
    assert existing in admin, f"existing preview command drifted: {existing}"

for required in (
    "public void previewFirstSkillLevel(Player player)",
    "Skill.MINING.id(), PREVIEW_FIRST_SKILL_LEVEL",
    "public void previewQualifiedWildernessKill(Player player)",
    "PREVIEW_PK_VICTIM_NAME",
    "PREVIEW_PK_LOOT_VALUE",
    "PREVIEW_PK_WILDERNESS_LEVEL",
    "public void previewPkStreakMilestone(Player player, int streak)",
    "streak != 3 && streak != 5 && streak != 10",
    "public void previewFirstCampaignCracker(Player player)",
):
    assert required in previews, f"deterministic preview contract missing: {required}"

assert 'PREVIEW_FIRST_SKILL_LEVEL = 80;' in announcements
assert 'PREVIEW_PK_VICTIM_NAME = "Test Rival";' in announcements
assert 'PREVIEW_PK_LOOT_VALUE = 5_000L;' in announcements
assert 'PREVIEW_PK_WILDERNESS_LEVEL = 20;' in announcements
assert previews.count(", true);" ) == 8, \
    "all four old and six new preview modes must force presentation through eight methods"

for forbidden in (
    "getDatabase(", "getCache().store", "claimFirst", "settle(", "award(",
    "setRemaining(", "addExperience(", "incExp(", "setLevel(", "queryInsert",
):
    assert forbidden not in previews, f"preview path may not mutate durable/player state: {forbidden}"

qualified_runtime_start = announcements.index("private String qualifiedWildernessKillMessage(WorldPkSettlementResult")
qualified_runtime_end = announcements.index("static String qualifiedWildernessKillMessage", qualified_runtime_start)
qualified_runtime = announcements[qualified_runtime_start:qualified_runtime_end]
assert "qualifiedWildernessKillMessage(result.getKillerName(), result.getVictimName()," in qualified_runtime
assert "qualifiedWildernessKillMessage(player.getUsername(), PREVIEW_PK_VICTIM_NAME," in previews

streak_runtime_start = announcements.index("private String pkStreakMilestoneMessage(WorldPkSettlementResult")
streak_runtime_end = announcements.index("static String qualifiedWildernessKillMessage", streak_runtime_start)
streak_runtime = announcements[streak_runtime_start:streak_runtime_end]
assert "pkStreakMilestoneMessage(result.getKillerName(), result.getStreakAfter())" in streak_runtime
assert "pkStreakMilestoneMessage(player.getUsername(), streak)" in previews

assert previews.count("firstSkillLevelMessage(player,") == 1
assert announcements.count("firstSkillLevelMessage(player,") == 2, \
    "production and preview first-skill paths must share one formatter"
assert previews.count("firstCampaignCrackerMessage(player)") == 1
assert announcements.count("firstCampaignCrackerMessage(player)") == 2, \
    "production and preview first-cracker paths must share one formatter"

command_start = admin.index("private void worldAnnouncementPreview(")
command_end = admin.index("private void npcRangedPlayer", command_start)
command = admin[command_start:command_end]
for mode, invocation in (
    ("firstskill", "previewFirstSkillLevel(player);"),
    ("qualifiedpk", "previewQualifiedWildernessKill(player);"),
    ("pk3", "previewPkStreakMilestone(player, 3);"),
    ("pk5", "previewPkStreakMilestone(player, 5);"),
    ("pk10", "previewPkStreakMilestone(player, 10);"),
    ("firstcracker", "previewFirstCampaignCracker(player);"),
):
    assert f'args[0].equalsIgnoreCase("{mode}")' in command, f"missing command mode {mode}"
    assert command.count(invocation) == 1, f"preview invocation must be exact for {mode}"

usage = "[skill|total|pk|newplayer|firstskill|qualifiedpk|pk3|pk5|pk10|firstcracker]"
assert usage in command, "admin syntax help is stale"
assert f"`::announcepreview {usage}`" in commands, "Commands.md syntax help is stale"
assert "do not write achievement, PK, cracker, or player-progression state" in commands
assert "Production command lockdown restricts both aliases to owners" in commands

print("World announcement preview source contracts passed.")
PY

echo "World announcement preview focused tests passed."
