#!/usr/bin/env bash
# Compiles and runs the Slice 5A world-achievement persistence contract.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
LEDGER_SOURCE="$REPO/server/src/com/openrsc/server/database/WorldAchievementLedger.java"
RECORD_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldAchievementRecord.java"
EVENT_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldPkEvent.java"
STREAK_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldPkStreak.java"
OUTCOME_SOURCE="$REPO/server/src/com/openrsc/server/database/AtomicTransactionOutcome.java"
DATABASE_SOURCE="$REPO/server/src/com/openrsc/server/database/GameDatabase.java"
MYSQL_DATABASE_SOURCE="$REPO/server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java"
TEST_SOURCE="$REPO/tests/java/com/openrsc/server/database/WorldAchievementLedgerSqliteTest.java"
SERVICE_SOURCE="$REPO/server/src/com/openrsc/server/content/WorldAchievementService.java"
SERVICE_TEST_SOURCE="$REPO/tests/java/com/openrsc/server/content/WorldAchievementServiceTest.java"
WORLD_SOURCE="$REPO/server/src/com/openrsc/server/model/world/World.java"
SKILLS_SOURCE="$REPO/server/src/com/openrsc/server/model/Skills.java"
ANNOUNCEMENT_SOURCE="$REPO/server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java"
SQLITE_PATCH="$REPO/server/database/sqlite/patches/2026_07_11_add_world_achievements.sql"
MYSQL_PATCH="$REPO/server/database/mysql/patches/2026_07_11_add_world_achievements.sql"

if [ ! -f "$REPO/server/core.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/world-achievements-unit.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$RECORD_SOURCE" "$EVENT_SOURCE" "$STREAK_SOURCE" "$OUTCOME_SOURCE" \
	"$LEDGER_SOURCE" "$DATABASE_SOURCE" "$MYSQL_DATABASE_SOURCE" \
	"$SERVICE_SOURCE" "$TEST_SOURCE" "$SERVICE_TEST_SOURCE"

java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.database.WorldAchievementLedgerSqliteTest "$SQLITE_PATCH"
java -cp "$TMP:$REPO/server/core.jar:$REPO/server/lib/*" \
	com.openrsc.server.content.WorldAchievementServiceTest

python3 - "$MYSQL_PATCH" "$SQLITE_PATCH" "$LEDGER_SOURCE" \
	"$DATABASE_SOURCE" "$MYSQL_DATABASE_SOURCE" <<'PY'
import re
import sys
from pathlib import Path

mysql_path, sqlite_path, ledger_path, database_path, mysql_database_path = map(Path, sys.argv[1:])
mysql = mysql_path.read_text()
sqlite = sqlite_path.read_text()
ledger = ledger_path.read_text()
database = database_path.read_text()
mysql_database = mysql_database_path.read_text()

expected = {
    "world_achievement_records": [
        "season_id", "record_key", "record_type", "player_id", "player_name",
        "subject_id", "value", "source", "source_event_key", "claimed_at_ms", "detail",
    ],
    "world_pk_events": [
        "death_id", "season_id", "killer_player_id", "killer_account_id", "killer_name",
        "victim_player_id", "victim_account_id", "victim_name", "pair_low_player_id",
        "pair_high_player_id", "qualified", "reject_reason", "victim_was_skulled",
        "victim_damage", "loot_value", "streak_after", "ended_streak",
        "wilderness_level", "occurred_at_ms",
    ],
    "world_pk_streaks": [
        "season_id", "player_id", "player_name", "current_streak", "best_streak",
        "qualified_kills", "last_qualified_at_ms", "updated_at_ms",
    ],
}

def table_block(source, table):
    match = re.search(
        r"CREATE TABLE IF NOT EXISTS\s+[`\"]_PREFIX_" + re.escape(table)
        + r"[`\"]\s*\((.*?)\n\)",
        source,
        flags=re.S | re.I,
    )
    assert match, f"missing prefixed table {table}"
    return match.group(1)

def columns(block):
    found = []
    for line in block.splitlines():
        match = re.match(r"\s*[`\"]([a-z0-9_]+)[`\"]\s+", line, flags=re.I)
        if match:
            found.append(match.group(1).lower())
    return found

for table, required_columns in expected.items():
    mysql_block = table_block(mysql, table)
    sqlite_block = table_block(sqlite, table)
    assert columns(mysql_block) == required_columns, f"MySQL columns drifted for {table}"
    assert columns(sqlite_block) == required_columns, f"SQLite columns drifted for {table}"
    assert columns(mysql_block) == columns(sqlite_block), f"engine schemas diverged for {table}"

normalized_mysql = re.sub(r"\s+", " ", mysql.lower())
for contract in (
    "primary key (`season_id`, `record_key`)",
    "unique key `world_achievement_source_event` (`season_id`, `source`, `source_event_key`, `record_type`)",
    "key `world_achievement_type_time` (`season_id`, `record_type`, `claimed_at_ms`)",
    "key `world_achievement_player_time` (`season_id`, `player_id`, `claimed_at_ms`)",
    "primary key (`death_id`)",
    "key `world_pk_pair_qualified_time` (`season_id`, `pair_low_player_id`, `pair_high_player_id`, `qualified`, `occurred_at_ms`)",
    "key `world_pk_qualified_time` (`season_id`, `qualified`, `occurred_at_ms`)",
    "key `world_pk_killer_time` (`season_id`, `killer_player_id`, `occurred_at_ms`)",
    "key `world_pk_victim_time` (`season_id`, `victim_player_id`, `occurred_at_ms`)",
    "primary key (`season_id`, `player_id`)",
    "key `world_pk_streak_leaders` (`season_id`, `best_streak`, `qualified_kills`, `updated_at_ms`, `player_id`)",
):
    assert contract in normalized_mysql, f"MySQL key/index contract missing: {contract}"

for identifier in (
    "season_id", "record_key", "record_type", "source", "source_event_key",
    "death_id", "reject_reason",
):
    matching_lines = [line.lower() for line in mysql.splitlines()
                      if re.match(r"\s*`" + re.escape(identifier) + r"`", line, flags=re.I)]
    assert matching_lines and all("character set ascii" in line and "collate ascii_bin" in line
                                  for line in matching_lines), \
        f"MySQL machine identifier is not ASCII/binary: {identifier}"

assert re.search(r"`death_id`\s+char\(36\)", mysql, flags=re.I), \
    "MySQL death id must preserve canonical UUID text"
assert "`account_id`" not in table_block(mysql, "world_achievement_records").lower(), \
    "world records must not copy private account ids"
for account_column in ("killer_account_id", "victim_account_id"):
    line = next(line.lower() for line in mysql.splitlines() if f"`{account_column}`" in line)
    assert "bigint" in line and "unsigned" in line and "default null" in line, \
        f"{account_column} must be nullable unsigned BIGINT"

for index in (
    "world_achievement_type_time", "world_achievement_player_time",
    "world_pk_pair_qualified_time", "world_pk_qualified_time", "world_pk_killer_time",
    "world_pk_victim_time", "world_pk_streak_leaders",
):
    assert f'"_PREFIX_{index}"' in sqlite, f"SQLite index is not prefix-safe: {index}"

all_columns = {column for values in expected.values() for column in values}
assert not any(column == "ip" or column == "ip_address" or column.endswith("_ip")
               or "ip_address" in column for column in all_columns), \
    "world achievement schema must never retain raw IP fields"
for forbidden in (" ON CONFLICT ", " ON DUPLICATE ", "INSERT OR ", "INSERT IGNORE"):
    assert forbidden.lower() not in ledger.lower(), f"ledger uses engine-specific UPSERT: {forbidden}"

for method in (
    "queryLoadWorldAchievementRecord", "queryInsertWorldAchievementRecord",
    "queryLoadWorldPkEvent", "queryInsertWorldPkEvent",
    "queryLoadLastQualifiedWorldPkPairTime", "queryLoadWorldPkStreak",
    "queryInsertWorldPkStreak", "queryUpdateWorldPkStreak",
):
    assert method in database, f"GameDatabase contract missing {method}"
    assert method in mysql_database, f"MySqlGameDatabase delegate missing {method}"
assert mysql_database.count("getServer().getConfig().DB_TABLE_PREFIX") >= 8, \
    "world ledger delegates must forward the configured table prefix"
assert 'character != \'_\'' in ledger and 'MAX_PREFIX_LENGTH = 32' in ledger, \
    "ledger table prefix must be explicitly bounded and character-validated"

print("World achievement migration/source contracts passed.")
PY

rg -Fq 'private static final int[] FIRST_SKILL_LEVELS = {80, 90, 99};' "$SERVICE_SOURCE"
rg -Fq 'record.recordKey = "first:skill:" + skill + ":" + level;' "$SERVICE_SOURCE"
rg -Fq 'record.recordType = FIRST_SKILL_TYPE;' "$SERVICE_SOURCE"
rg -Fq 'record.source = SKILL_LEVEL_SOURCE;' "$SERVICE_SOURCE"
rg -Fq 'record.sourceEventKey = null;' "$SERVICE_SOURCE"
rg -Fq 'return database.atomicallySettled(body::run, verifier::verify);' "$SERVICE_SOURCE"
if rg -q 'WorldAnnouncementService|announce[A-Za-z]*\(' "$SERVICE_SOURCE"; then
	echo "World achievement service must not own presentation callbacks." >&2
	exit 1
fi

python3 - "$WORLD_SOURCE" "$SKILLS_SOURCE" "$ANNOUNCEMENT_SOURCE" "$SERVICE_SOURCE" <<'PY'
import sys
from pathlib import Path

world, skills, announcements, service = (Path(path).read_text() for path in sys.argv[1:])

for required in (
    "private final WorldAchievementService worldAchievementService;",
    "this.worldAchievementService = new WorldAchievementService(server.getDatabase(),",
    "server.getConfig().WANT_WORLD_ACHIEVEMENTS,",
    "server.getConfig().WORLD_ACHIEVEMENT_SEASON_ID);",
    "public WorldAchievementService getWorldAchievementService()",
):
    assert required in world, f"World achievement ownership contract missing: {required}"

add_start = skills.index("public void addExperience(int skill, int exp)")
add_end = skills.index("public void reduceExperience", add_start)
add = skills[add_start:add_end]

save_call = add.index("savePlayerMaxSkill(player.getDatabaseID(), skill, maxStats[skill])")
save_success = add.index("maxSkillSaved = true", save_call)
social_gate = add.index("if (!player.getConfig().WANT_OPENPK_POINTS)", save_success)
save_gate = add.index("if (maxSkillSaved)", social_gate)
claim = add.index("claimFirstSkillLevels", save_gate)
highest = add.index("getHighestClaimedLevel()", claim)
first_announcement = add.index("announceFirstSkillLevel", highest)
normal_announcement = add.index("announceSkillMilestone", first_announcement)
total_announcement = add.index("announceTotalLevelMilestone", normal_announcement)
progression = add.index("ProgressionMilestones.handleLevelUp", total_announcement)
title = add.index("PlayerTitle.refreshAutomaticUnlocks", progression)
assert save_call < save_success < social_gate < save_gate < claim < highest \
    < first_announcement < normal_announcement < total_announcement < progression < title, \
    "skill save/claim/announcement/progression ordering drifted"
assert "boolean maxSkillSaved = false;" in add, "skill persistence gate must fail closed"
assert add.count("claimFirstSkillLevels") == 1, "level-up path must attempt one claim batch"
assert skills.count("claimFirstSkillLevels") == 1, \
    "only normal addExperience level-ups may claim first-skill records"
assert add.count("announceFirstSkillLevel") == 1, "level-up path must announce only the highest claim"
assert "!player.isDefaultUser(), skill, oldLevel, newLevel" in add, \
    "skill claim must exclude every non-player rank and pass the exact crossing"
assert "player, skill, oldLevel, newLevel, highestWorldFirstLevel" in add, \
    "ordinary milestone announcement must receive the claimed-level suppression"
assert "ActionSender.sendSound((Player) getMob(), \"advance\");" in add, \
    "level-up sound hook must remain intact"
assert "announceTotalLevelMilestone" in add and "ProgressionMilestones.handleLevelUp" in add \
    and "PlayerTitle.refreshAutomaticUnlocks" in add, \
    "existing total/progression/title hooks must remain intact"

for required in (
    "announceSkillMilestone(player, skill, oldLevel, newLevel, 0);",
    "if (milestone <= 0 || milestone == suppressedMilestone) return;",
    "public void announceFirstSkillLevel(Player player, int skill, int level)",
    'return "@mag@[World First] @yel@" + player.getUsername()',
    '+ " @whi@is the first player to reach @gre@level " + level + " "',
    '+ skillName(skill) + "@whi@!";',
):
    assert required in announcements, f"world-first announcement contract missing: {required}"

first_method_start = announcements.index("public void announceFirstSkillLevel")
first_method_end = announcements.index("public void announceTotalLevelMilestone", first_method_start)
first_method = announcements[first_method_start:first_method_end]
assert "WANT_WORLD_ANNOUNCEMENTS" in first_method \
    and "WANT_WORLD_MILESTONE_ANNOUNCEMENTS" in first_method, \
    "production world-first chat must obey both existing announcement flags"
assert "WorldAnnouncementService" not in service and "announceFirstSkillLevel" not in service, \
    "durable service must remain presentation-independent"

print("World achievement skill wiring source contracts passed.")
PY

echo "World achievement persistence tests passed."
