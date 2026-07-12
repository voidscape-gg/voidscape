#!/usr/bin/env bash
# Compiles deterministic 4A/4B/4C server campaign tests and guards production contracts.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
OUTCOME_SOURCE="$REPO/server/src/com/openrsc/server/database/AtomicTransactionOutcome.java"
WORLD_ACHIEVEMENT_RECORD_SOURCE="$REPO/server/src/com/openrsc/server/database/struct/WorldAchievementRecord.java"
TRANSACTION_SOURCE="$REPO/server/src/com/openrsc/server/content/CrackerCampaignTransactions.java"
SERVICE_SOURCE="$REPO/server/src/com/openrsc/server/content/CrackerCampaignService.java"
CAMPAIGN_TEST="$REPO/tests/java/com/openrsc/server/content/CrackerCampaignTransactionsTest.java"
ACTION_SENDER_SOURCE="$REPO/server/src/com/openrsc/server/net/rsc/ActionSender.java"
COMMAND_SOURCE="$REPO/server/src/com/openrsc/server/net/rsc/handlers/CommandHandler.java"
COMMAND_TEST="$REPO/tests/java/com/openrsc/server/net/rsc/handlers/CommandLockdownTest.java"
WORLD_SOURCE="$REPO/server/src/com/openrsc/server/model/world/World.java"
PLAYER_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/player/Player.java"
NPC_SOURCE="$REPO/server/src/com/openrsc/server/model/entity/npc/Npc.java"
ANNOUNCEMENT_SOURCE="$REPO/server/src/com/openrsc/server/content/announcements/WorldAnnouncementService.java"
ADMIN_SOURCE="$REPO/server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
CONFIG_SOURCE="$REPO/server/src/com/openrsc/server/ServerConfiguration.java"
DATABASE_SOURCE="$REPO/server/src/com/openrsc/server/database/impl/mysql/MySqlGameDatabase.java"
LAUNCH_CHECKER="$REPO/scripts/check-launch-config.mjs"
LAUNCH_CONFIG="$REPO/server/voidscape-launch.conf"
DEFAULT_CONFIG="$REPO/server/default.conf"

if [ ! -f "$REPO/server/core.jar" ] || [ ! -f "$REPO/server/plugins.jar" ]; then
	"$REPO/scripts/build.sh"
fi

TMP="$(mktemp -d "${TMPDIR:-/tmp}/cracker-campaign-unit.XXXXXX")"
trap 'rm -rf "$TMP"' EXIT

javac --release 8 \
	-cp "$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*" \
	-d "$TMP" \
	"$OUTCOME_SOURCE" "$WORLD_ACHIEVEMENT_RECORD_SOURCE" "$TRANSACTION_SOURCE" \
	"$SERVICE_SOURCE" "$ANNOUNCEMENT_SOURCE" "$CAMPAIGN_TEST" \
	"$COMMAND_SOURCE" "$COMMAND_TEST"

CLASSPATH="$TMP:$REPO/server/core.jar:$REPO/server/plugins.jar:$REPO/server/lib/*"
java -cp "$CLASSPATH" com.openrsc.server.content.CrackerCampaignTransactionsTest
java -cp "$CLASSPATH" com.openrsc.server.net.rsc.handlers.CommandLockdownTest

# Production integration assertions. These deliberately inspect the source compiled by
# scripts/build.sh so the pure fake-port tests cannot be satisfied by a disconnected copy.
rg -Fq 'return database.atomicallySettled(body::run, verifier::verify);' "$TRANSACTION_SOURCE"
rg -Fq 'public static final String POOL_CACHE_KEY = "void_cracker_pool_remaining";' "$SERVICE_SOURCE"
rg -Fq 'new CrackerCampaignService(server.getDatabase(),' "$WORLD_SOURCE"
rg -Fq 'public CrackerCampaignService getCrackerCampaignService()' "$WORLD_SOURCE"
rg -Fq 'server.getConfig().CRACKER_CAMPAIGN_NPC_KILL_DENOMINATOR' "$WORLD_SOURCE"
rg -Fq 'server.getConfig().CRACKER_CAMPAIGN_SKILLING_DENOMINATOR' "$WORLD_SOURCE"
rg -Fq 'CrackerCampaignTransactions.award(player, trigger)' "$SERVICE_SOURCE"
rg -Fq 'ActionSender.sendInventory(player);' "$SERVICE_SOURCE"
rg -Fq 'announceCrackerDrop(player);' "$SERVICE_SOURCE"
rg -Fq 'publishAwardSettlement(result);' "$SERVICE_SOURCE"
rg -Fq 'statePublisher.publish(visibleRemaining);' "$SERVICE_SOURCE"
rg -Fq 'public static final int MAX_REMAINING = 1_000_000;' "$SERVICE_SOURCE"
rg -Fq 'this::broadcastCrackerCampaignState' "$WORLD_SOURCE"
rg -Fq 'ActionSender.sendCrackerCampaignState(player, remaining);' "$WORLD_SOURCE"
rg -Fq 'public static final int CRACKER_CAMPAIGN_STATE_CLIENT_VERSION = 10132;' "$ACTION_SENDER_SOURCE"
rg -Fq '"@vscrackercampaign@v1|"' "$ACTION_SENDER_SOURCE"
rg -Fq 'sendMessage(player, null, MessageType.QUEST,' "$ACTION_SENDER_SOURCE"
rg -Fq 'getCrackerCampaignService().sendStateSnapshot(player);' "$ACTION_SENDER_SOURCE"
rg -Fq 'player.tryReserveSave()' "$TRANSACTION_SOURCE"
rg -Fq 'player.releaseSaveReservation()' "$TRANSACTION_SOURCE"
rg -Fq 'database.savePlayerInventory(player.getDatabaseID(), exactInventory);' "$TRANSACTION_SOURCE"
rg -Fq '"launch_cracker_campaign", "player_inventory", "campaign_drop"' "$TRANSACTION_SOURCE"
rg -Fq 'database.addItemProvenanceEvent(player, player, "item_origin"' "$TRANSACTION_SOURCE"
rg -Fq 'public boolean isNewlyWonWorldFirst()' "$TRANSACTION_SOURCE"
rg -Fq 'player.isDefaultUser()' "$TRANSACTION_SOURCE"
rg -Fq 'database.queryInsertWorldAchievementRecord(record)' "$TRANSACTION_SOURCE"
rg -Fq 'database.queryLoadWorldAchievementRecord(seasonId, recordKey)' "$TRANSACTION_SOURCE"
rg -Fq 'record.recordKey = FIRST_CRACKER_RECORD_KEY;' "$TRANSACTION_SOURCE"
rg -Fq 'record.sourceEventKey = Long.toString(itemId);' "$TRANSACTION_SOURCE"
rg -Fq 'ItemId.CHRISTMAS_CRACKER.id()' "$TRANSACTION_SOURCE"
rg -Fq 'onNpcKill(owner);' "$NPC_SOURCE"
rg -Fq 'onSkillingExperience(this, skill, awardedXp);' "$PLAYER_SOURCE"
rg -Fq 'has got a @yel@cracker drop' "$ANNOUNCEMENT_SOURCE"
rg -Fq 'crackerCampaign(player, command, args);' "$ADMIN_SOURCE"
rg -Fq 'if (!player.isOwner())' "$ADMIN_SOURCE"
rg -Fq 'result = campaign.setRemaining(intendedRemaining);' "$ADMIN_SOURCE"
rg -Fq 'auditCrackerCampaignSet(player, "updated", intendedRemaining,' "$ADMIN_SOURCE"
rg -Fq 'previous=' "$ADMIN_SOURCE"
rg -Fq 'remaining=' "$ADMIN_SOURCE"
rg -Fq 'static boolean ownerOnlyWorldControlBlocks(boolean owner, String cmd)' "$COMMAND_SOURCE"
rg -Fq 'cmd.equals("cracker")' "$COMMAND_SOURCE"
rg -Fq 'WANT_CRACKER_CAMPAIGN = tryReadBool("want_cracker_campaign").orElse(false);' "$CONFIG_SOURCE"
rg -Fq 'cracker_campaign_npc_kill_denominator' "$CONFIG_SOURCE"
rg -Fq 'cracker_campaign_skilling_denominator' "$CONFIG_SOURCE"
rg -Fq '["want_cracker_campaign", "true"]' "$LAUNCH_CHECKER"
rg -Fq '["cracker_campaign_npc_kill_denominator", "500"]' "$LAUNCH_CHECKER"
rg -Fq '["cracker_campaign_skilling_denominator", "1000"]' "$LAUNCH_CHECKER"

python3 - "$SERVICE_SOURCE" "$TRANSACTION_SOURCE" "$DATABASE_SOURCE" "$ADMIN_SOURCE" \
	"$WORLD_SOURCE" "$PLAYER_SOURCE" "$NPC_SOURCE" "$ANNOUNCEMENT_SOURCE" "$ACTION_SENDER_SOURCE" \
	"$LAUNCH_CONFIG" "$DEFAULT_CONFIG" <<'PY'
import re
import sys
from pathlib import Path

service = Path(sys.argv[1]).read_text()
transaction = Path(sys.argv[2]).read_text()
database = Path(sys.argv[3]).read_text()
admin = Path(sys.argv[4]).read_text()
world = Path(sys.argv[5]).read_text()
player = Path(sys.argv[6]).read_text()
npc = Path(sys.argv[7]).read_text()
announcements = Path(sys.argv[8]).read_text()
action_sender = Path(sys.argv[9]).read_text()
launch = Path(sys.argv[10]).read_text()
default = Path(sys.argv[11]).read_text()

key_match = re.search(r'POOL_CACHE_KEY\s*=\s*"([^"]+)"', service)
assert key_match and len(key_match.group(1)) <= 32, "campaign cache key exceeds player_cache contract"

load_start = database.index("public Integer queryLoadGlobalCacheInt")
load_end = database.index("\n\t@Override", load_start)
load_method = database[load_start:load_end]
assert "ORDER BY `dbid` DESC LIMIT 1" in load_method, \
    "duplicate global-cache rows must resolve deterministically to newest dbid"

save_start = database.index("public void querySaveGlobalCacheInt")
save_end = database.index("\n\t@Override", save_start)
save_method = database[save_start:save_end]
delete_pos = save_method.index('DELETE FROM `')
delete_exec_pos = save_method.index("deleteStatement.executeUpdate()")
insert_pos = save_method.index('INSERT INTO `')
insert_exec_pos = save_method.index("insertStatement.executeUpdate()")
assert delete_pos < insert_pos and delete_exec_pos < insert_exec_pos, \
    "pool save must delete every duplicate key row before inserting one canonical row"
assert "`playerID`=0 AND `key`=?" in save_method, \
    "pool save must be scoped to the global cache key"

command_start = admin.index("private void crackerCampaign(")
command_end = admin.index("private void reportCrackerCampaignStatus", command_start)
command = admin[command_start:command_end]
for required in (
    "args.length == 0",
    'args[0].equalsIgnoreCase("status")',
    "Integer.parseInt(amountArgument)",
    "intendedRemaining < 0",
    "intendedRemaining > CRACKER_CAMPAIGN_MAX_POOL",
):
    assert required in command, f"cracker command validation missing: {required}"
assert "new Item(" not in command and "sendWorldMessage(" not in command, \
	"owner control command must not mint items or broadcast gameplay awards"

award_start = transaction.index("static AwardResult award(AwardPort port, String trigger)")
award_end = transaction.index("private static AtomicTransactionOutcome verifyAwardSettlement", award_start)
award = transaction[award_start:award_end]
award_steps = [
    "loadValidatedRemaining(port)",
    "port.addCracker()",
    "port.saveInventory()",
    "port.saveRemaining(attempt.remaining)",
    "port.recordProvenance(normalizedTrigger",
    "claimFirstCampaignCracker(port, attempt, normalizedTrigger)",
]
positions = [award.index(step) for step in award_steps]
assert positions == sorted(positions), \
    "award must read pool, add exact item, save inventory, decrement, then record origin"
assert "port.atomically" in award and "port.quarantineUnknownOutcome" in award
assert "port.rollbackAddedCracker" in award and "port.releaseSaveReservation" in award
assert "WorldAchievementService" not in transaction, \
	"campaign first-item claim must join the award transaction without nesting the service"

port_start = transaction.index("private static final class PlayerAwardPort")
atomic_start = transaction.index("public AtomicTransactionOutcome atomically(", port_start)
atomic_end = transaction.index("\n\t\t@Override", atomic_start + 1)
atomic_method = transaction[atomic_start:atomic_end]
assert "synchronized (items)" not in atomic_method, \
    "campaign transaction must not acquire DB while holding the inventory monitor"

add_start = transaction.index("public AddedCracker addCracker()", port_start)
add_end = transaction.index("\n\t\t@Override", add_start + 1)
add_method = transaction[add_start:add_end]
for required in (
    "synchronized (items)",
    "maxItemId < CRACKER_ITEM_ID",
    "items.size() >= Inventory.MAX_SIZE",
    "inventory.add(new Item(CRACKER_ITEM_ID, 1, false), false)",
    "tentative.getCatalogId() != CRACKER_ITEM_ID",
    "tentative.getAmount() != 1",
    "tentative.getNoted()",
    "tentative.getItemId() == Item.ITEM_ID_UNASSIGNED",
    "exactInventory = snapshotInventory(items)",
):
    assert required in add_method, f"exact no-spill cracker insertion missing: {required}"
assert "GroundItem" not in transaction and "registerItem" not in transaction, \
    "campaign award may never spill a finite cracker to the ground"
assert "submitSqlLogging" not in transaction, \
    "campaign provenance must remain synchronous inside the award transaction"

login_start = action_sender.index("static void sendLogin(Player player)")
login_end = action_sender.index("public static void sendReleasedNameExplanation", login_start)
login = action_sender[login_start:login_end]
settings_pos = login.index("sendGameSettings(player);")
snapshot_pos = login.index("getCrackerCampaignService().sendStateSnapshot(player);")
world_pos = login.index("sendWorldInfo(player);")
assert settings_pos < snapshot_pos < world_pos, \
    "normal login and reconnect must receive campaign snapshot immediately after game settings"

sender_start = action_sender.index("public static void sendCrackerCampaignState")
sender_end = action_sender.index("\n\t}", sender_start) + 3
sender = action_sender[sender_start:sender_end]
for required in (
    "supportsCrackerCampaignState(player)",
    "sendMessage(player, null, MessageType.QUEST",
    "crackerCampaignStateEnvelope(remaining)",
):
    assert required in sender, f"campaign sender contract missing: {required}"

capability_start = action_sender.index("static boolean supportsCrackerCampaignStateVersion")
capability_end = action_sender.index("\n\t}", capability_start) + 3
capability = action_sender[capability_start:capability_end]
assert "clientVersion >= CRACKER_CAMPAIGN_STATE_CLIENT_VERSION" in capability
assert "clientVersion <" not in capability, \
    "custom-client classification owns the upper bound; capability gate must remain forward-compatible"

envelope_start = action_sender.index("static String crackerCampaignStateEnvelope")
envelope_end = action_sender.index("\n\t}", envelope_start) + 3
envelope = action_sender[envelope_start:envelope_end]
assert "remaining >= 0" in envelope and "remaining <= CrackerCampaignService.MAX_REMAINING" in envelope
assert "CRACKER_CAMPAIGN_STATE_PREFIX + safeRemaining" in envelope

npc_start = npc.index("public void killedBy(Mob mob)")
npc_end = npc.index("private void logNpcKill", npc_start)
npc_kill = npc[npc_start:npc_end]
assert npc_kill.count("onNpcKill(owner)") == 1, \
    "ordinary NPC death must produce exactly one campaign candidate"
npc_positions = [
    npc_kill.index("shouldSuppressDefaultDeathRewards()"),
    npc_kill.index("for (int npcId : removeHandledInPlugin)"),
    npc_kill.index("owner = getWorld().getPlayerByUUID(ownerInfo.getLeft())"),
    npc_kill.index("owner.incNpcKills()"),
    npc_kill.index("onNpcKill(owner)"),
    npc_kill.index("dropItems(owner)"),
]
assert npc_positions == sorted(npc_positions), \
    "NPC campaign hook must follow suppression and credited-owner resolution on normal reward path"

inc_start = player.index("public void incExp(final int skill, int origSkillXP, final boolean useFatigue, final boolean fromQuest)")
inc_end = player.index("public void incQuestPoints", inc_start)
inc_exp = player[inc_start:inc_end]
for required in (
    "final int experienceBefore = getSkills().getExperience(skill)",
    "getSkills().addExperience(skill",
    "getSkills().getExperience(skill) - experienceBefore",
    "if (useFatigue && !fromQuest && awardedXp > 0)",
    "onSkillingExperience(this, skill, awardedXp)",
):
    assert required in inc_exp, f"accepted-XP skilling hook missing: {required}"
xp_positions = [
    inc_exp.index("final int experienceBefore"),
    inc_exp.index("getSkills().addExperience(skill", inc_exp.index("final int experienceBefore")),
    inc_exp.index("final int awardedXp"),
    inc_exp.index("onSkillingExperience(this, skill, awardedXp)"),
]
assert xp_positions == sorted(xp_positions), \
    "skilling hook must run only after measuring actual accepted XP"
openpk_start = inc_exp.index("if (getConfig().WANT_OPENPK_POINTS)")
openpk_else = inc_exp.index("} else {", openpk_start)
assert openpk_else < inc_exp.index("onSkillingExperience", openpk_else), \
    "OpenPK point conversion must not create campaign skilling candidates"

combat_start = service.index("private static boolean isCombatSkill")
combat_end = service.index("private static boolean isTutorialLocation", combat_start)
combat = service[combat_start:combat_end]
for alias in ("ATTACK", "DEFENSE", "STRENGTH", "HITS", "RANGED", "MAGIC", "GOODMAGIC", "EVILMAGIC"):
    assert f"Skill.{alias}.id()" in combat, f"combat/magic exclusion missing alias {alias}"
for prayer in ("PRAYER", "PRAYGOOD", "PRAYEVIL"):
    assert f"Skill.{prayer}.id()" not in combat, f"Prayer must remain an eligible skilling path: {prayer}"

skill_start = service.index("public synchronized void onSkillingExperience")
skill_end = service.index("boolean isNpcKillWinningRoll", skill_start)
skill_hook = service[skill_start:skill_end]
tick_positions = [
    skill_hook.index("awardedXp <= 0"),
    skill_hook.index("isCombatSkill(skill)"),
    skill_hook.index("final long currentTick"),
    skill_hook.index("if (previousTick == currentTick)"),
    skill_hook.index("player.setAttribute(SKILLING_CANDIDATE_TICK_ATTRIBUTE, currentTick)"),
    skill_hook.index("isSkillingWinningRoll()"),
]
assert tick_positions == sorted(tick_positions), \
    "tick dedupe must occur after eligibility and before the one skilling roll"
assert "onTutorialIsland()" in service and "inVoidTutorialIsle()" in service, \
    "both tutorial regions must be ineligible"

assert "ActionSender.sendInventory(player);" in service
assert service.index("case AWARDED:") < service.index("ActionSender.sendInventory(player);") \
    < service.index("announceCrackerDrop(player);"), \
    "inventory refresh and announcement must be confined to post-commit AWARDED handling"
assert "case INVENTORY_FULL:" in service and "inventory is full" in service
assert "has got a @yel@cracker drop" in announcements, \
    "world announcement must retain the approved cracker-drop wording"
assert "@vscrackercampaign@" not in transaction, \
    "Slice 4B transaction path must not introduce the later HUD envelope"

def config_value(text, key):
    match = re.search(rf"(?m)^\s*{re.escape(key)}:\s*([^#\s]+)", text)
    assert match, f"missing config key {key}"
    return match.group(1)

assert config_value(launch, "want_cracker_campaign") == "true"
assert config_value(launch, "cracker_campaign_npc_kill_denominator") == "500"
assert config_value(launch, "cracker_campaign_skilling_denominator") == "1000"
assert config_value(default, "want_cracker_campaign") == "false"
print("Cracker-campaign control, award, hook, and server HUD contracts passed.")
PY

echo "Cracker-campaign Slices 4A/4B/4C server checks passed."
