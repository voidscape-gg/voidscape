#!/usr/bin/env bash
# Guards the post-commit campaign-drop and first-item presentation boundary.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
TRANSACTION_SOURCE="$REPO/server/src/com/openrsc/server/content/CrackerCampaignTransactions.java"
SERVICE_SOURCE="$REPO/server/src/com/openrsc/server/content/CrackerCampaignService.java"
ANNOUNCEMENT_SOURCE="$REPO/server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java"

if [ ! -f "$REPO/server/core.jar" ]; then
	echo "server/core.jar is required for the focused presentation compile" >&2
	exit 1
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/cracker-first-announcement.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$TRANSACTION_SOURCE" "$SERVICE_SOURCE" "$ANNOUNCEMENT_SOURCE"

python3 - "$SERVICE_SOURCE" "$ANNOUNCEMENT_SOURCE" <<'PY'
import sys
from pathlib import Path

service = Path(sys.argv[1]).read_text()
announcements = Path(sys.argv[2]).read_text()

award_start = service.index("private void attemptAward(")
award_end = service.index("private void publishAwardAnnouncements", award_start)
award = service[award_start:award_end]
awarded_start = award.index("case AWARDED:")
awarded_end = award.index("case POOL_EMPTY:", awarded_start)
awarded = award[awarded_start:awarded_end]

assert awarded.count("publishAwardAnnouncements(player, result);") == 1, \
    "AWARDED must publish one announcement bundle"
assert "publishAwardAnnouncements(player, result);" not in award[:awarded_start] \
    and "publishAwardAnnouncements(player, result);" not in award[awarded_end:], \
    "non-awarded outcomes must never publish campaign award announcements"

publish_start = service.index("private void publishAwardAnnouncements")
publish_end = service.index("/** Applies only durable settlement information", publish_start)
publish = service[publish_start:publish_end]
normal = "announceCrackerDrop(player);"
world_first_gate = "if (!result.isNewlyWonWorldFirst()) return;"
first = "announceFirstCampaignCracker(player);"
assert publish.count(normal) == 1, "normal cracker drop must be announced exactly once"
assert publish.count(first) == 1, "newly committed first cracker must be announced exactly once"
assert publish.index(normal) < publish.index(world_first_gate) < publish.index(first), \
    "normal Cracker Hunt announcement must precede the conditional World First"
assert publish.count("catch (RuntimeException ex)") == 2, \
    "both post-commit world announcements must be independently best effort"

first_method_start = announcements.index("public void announceFirstCampaignCracker")
first_method_end = announcements.index("\n\t}", first_method_start) + 3
first_method = announcements[first_method_start:first_method_end]
for required in (
    "WANT_WORLD_ANNOUNCEMENTS",
    "WANT_WORLD_MILESTONE_ANNOUNCEMENTS",
    "player == null",
    "firstCampaignCrackerMessage(player)",
):
    assert required in first_method, f"first-cracker presentation gate missing: {required}"

normal_copy = (
    '"@mag@[Cracker Hunt] @whi@" + player.getUsername()\n'
    '\t\t\t+ " has got a @yel@cracker drop@whi@!"'
)
assert normal_copy in announcements, "approved normal cracker-drop copy drifted"
first_copy = (
    '"@mag@[World First] @yel@" + player.getUsername()\n'
    '\t\t\t+ " @whi@is the first player to find a @gre@Christmas cracker@whi@"\n'
    '\t\t\t+ " in the launch Cracker Hunt!"'
)
assert first_copy in announcements, "approved first-campaign-cracker copy drifted"

assert "queryInsertWorldAchievementRecord" not in service \
    and "WorldAchievementRecord" not in service, \
    "presentation must consume the committed result rather than write a second record"

print("Cracker campaign first-announcement source contracts passed.")
PY

echo "Cracker campaign first-announcement unit tests passed."
