package com.openrsc.server.content.announcements;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.content.WorldPkSettlementResult;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public final class WorldAnnouncementService {
	private static final int[] SKILL_LEVEL_MILESTONES = {90, 95, 99};
	private static final int[] TOTAL_LEVEL_MILESTONES = {500, 750, 1000, 1250, 1500, 1750, 2000};
	private static final String NEW_PLAYER_ACCOUNT_CACHE_PREFIX = "new_join_acct:";
	private static final String NEW_PLAYER_CHARACTER_CACHE_PREFIX = "new_join_char:";
	private static final String NEW_PLAYER_LOCAL_CACHE_KEY = "void_announce_new_player";
	private static final int NEW_PLAYER_ANNOUNCED = 1;
	private static final int PREVIEW_FIRST_SKILL_LEVEL = 80;
	private static final String PREVIEW_PK_VICTIM_NAME = "Test Rival";
	private static final long PREVIEW_PK_LOOT_VALUE = 5_000L;
	private static final int PREVIEW_PK_WILDERNESS_LEVEL = 20;

	private final World world;

	public WorldAnnouncementService(World world) {
		this.world = world;
	}

	public void announceSkillMilestone(Player player, int skill, int oldLevel, int newLevel) {
		announceSkillMilestone(player, skill, oldLevel, newLevel, 0);
	}

	public void announceSkillMilestone(Player player, int skill, int oldLevel, int newLevel,
		int suppressedMilestone) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_MILESTONE_ANNOUNCEMENTS) return;

		int milestone = highestCrossed(SKILL_LEVEL_MILESTONES, oldLevel, newLevel);
		int maxLevel = world.getServer().getConfig().PLAYER_LEVEL_LIMIT;
		if (oldLevel < maxLevel && newLevel >= maxLevel) {
			milestone = maxLevel;
		}
		if (milestone <= 0 || milestone == suppressedMilestone) return;

		String cacheKey = "void_announce_skill_" + skill + "_" + milestone;
		if (player.getCache().hasKey(cacheKey)) return;

		player.getCache().store(cacheKey, true);
		announce(skillMilestoneMessage(player, skill, milestone), false);
	}

	public void announceFirstSkillLevel(Player player, int skill, int level) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_MILESTONE_ANNOUNCEMENTS) return;
		if (player == null || level <= 0) return;
		announce(firstSkillLevelMessage(player, skill, level), false);
	}

	public void announceTotalLevelMilestone(Player player, int oldTotalLevel, int newTotalLevel) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_MILESTONE_ANNOUNCEMENTS) return;

		int milestone = highestCrossed(TOTAL_LEVEL_MILESTONES, oldTotalLevel, newTotalLevel);
		if (milestone <= 0) return;

		String cacheKey = "void_announce_total_" + milestone;
		if (player.getCache().hasKey(cacheKey)) return;

		player.getCache().store(cacheKey, true);
		announce(totalLevelMilestoneMessage(player, milestone), false);
	}

	public void announceSkulledWildernessKill(Player killer, Player killed) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_SKULLED_PK_ANNOUNCEMENTS) return;
		if (killer == null || killed == null) return;
		if (!killed.getLocation().inWilderness()) return;
		if (!killed.isSkulled()) return;

		announce(skulledWildernessKillMessage(killer, killed), false);
	}

	public void announceQualifiedWildernessKill(WorldPkSettlementResult result) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_SKULLED_PK_ANNOUNCEMENTS) return;
		if (result == null || !result.isPublishable() || !result.isQualified()) return;

		announce(qualifiedWildernessKillMessage(result), false);
		if (result.getStreakAfter() == 3 || result.getStreakAfter() == 5
			|| result.getStreakAfter() == 10) {
			announce(pkStreakMilestoneMessage(result), false);
		}
	}

	public void announceNewPlayerJoined(Player player) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_NEW_PLAYER_ANNOUNCEMENTS) return;
		if (player == null || player.getLastLogin() != 0L) return;
		if (player.getCache().hasKey(NEW_PLAYER_LOCAL_CACHE_KEY)) return;

		String cacheKey = newPlayerAnnouncementCacheKey(player);
		if (cacheKey.isEmpty()) return;

		try {
			Integer announced = world.getServer().getDatabase().queryLoadGlobalCacheInt(cacheKey);
			if (announced != null && announced == NEW_PLAYER_ANNOUNCED) return;
			world.getServer().getDatabase().querySaveGlobalCacheInt(cacheKey, NEW_PLAYER_ANNOUNCED);
		} catch (RuntimeException ex) {
			// Fall back to character cache for dev DB hiccups; normal saves persist it.
		}

		player.getCache().store(NEW_PLAYER_LOCAL_CACHE_KEY, true);
		announce(newPlayerJoinedMessage(player), false);
	}

	public void previewSkillMilestone(Player player) {
		announce(skillMilestoneMessage(player, Skill.MINING.id(), 99), true);
	}

	public void previewTotalLevelMilestone(Player player) {
		announce(totalLevelMilestoneMessage(player, 1000), true);
	}

	public void previewSkulledWildernessKill(Player player) {
		announce(skulledWildernessKillMessage(player, player), true);
	}

	public void previewNewPlayerJoined(Player player) {
		announce(newPlayerJoinedMessage(player), true);
	}

	public void previewFirstSkillLevel(Player player) {
		announce(firstSkillLevelMessage(player, Skill.MINING.id(), PREVIEW_FIRST_SKILL_LEVEL), true);
	}

	public void previewQualifiedWildernessKill(Player player) {
		announce(qualifiedWildernessKillMessage(player.getUsername(), PREVIEW_PK_VICTIM_NAME,
			PREVIEW_PK_LOOT_VALUE, PREVIEW_PK_WILDERNESS_LEVEL), true);
	}

	public void previewPkStreakMilestone(Player player, int streak) {
		if (streak != 3 && streak != 5 && streak != 10) return;
		announce(pkStreakMilestoneMessage(player.getUsername(), streak), true);
	}

	public void previewFirstCampaignCracker(Player player) {
		announce(firstCampaignCrackerMessage(player), true);
	}

	public void announceUniqueTitleClaim(Player player, String titleName) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (player == null || titleName == null || titleName.isEmpty()) return;
		announce(uniqueTitleClaimMessage(player, titleName), false);
	}

	public void announceUniqueTitleTransfer(Player player, String titleName, String previousOwner) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (player == null || titleName == null || titleName.isEmpty()) return;
		announce(uniqueTitleTransferMessage(player, titleName, previousOwner), false);
	}

	public void announceCrackerDrop(Player player) {
		if (player == null) return;
		announce("@mag@[Cracker Hunt] @whi@" + player.getUsername()
			+ " has got a @yel@cracker drop@whi@!", false);
	}

	public void announceFirstCampaignCracker(Player player) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_MILESTONE_ANNOUNCEMENTS) return;
		if (player == null) return;
		announce(firstCampaignCrackerMessage(player), false);
	}

	private void announce(String message, boolean force) {
		if (!force && !world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		world.sendWorldMessage(message);
	}

	private String skillMilestoneMessage(Player player, int skill, int milestone) {
		return "@mag@[Void Herald] @whi@" + player.getUsername()
			+ " has reached @gre@" + milestone + " " + skillName(skill) + "@whi@.";
	}

	private String firstSkillLevelMessage(Player player, int skill, int level) {
		return "@mag@[World First] @yel@" + player.getUsername()
			+ " @whi@is the first player to reach @gre@level " + level + " "
			+ skillName(skill) + "@whi@!";
	}

	private String firstCampaignCrackerMessage(Player player) {
		return "@mag@[World First] @yel@" + player.getUsername()
			+ " @whi@is the first player to find a @gre@Christmas cracker@whi@"
			+ " in the launch Cracker Hunt!";
	}

	private String totalLevelMilestoneMessage(Player player, int milestone) {
		return "@mag@[Void Herald] @whi@" + player.getUsername()
			+ " has crossed @gre@" + milestone + " total level@whi@.";
	}

	private String skulledWildernessKillMessage(Player killer, Player killed) {
		int wildernessLevel = Math.max(1, killed.getLocation().wildernessLevel());
		return "@mag@[Wilderness] @red@A skull has fallen: @whi@" + killer.getUsername()
			+ " defeated " + killed.getUsername()
			+ " in level-" + wildernessLevel + " Wilderness.";
	}

	private String qualifiedWildernessKillMessage(WorldPkSettlementResult result) {
		return qualifiedWildernessKillMessage(result.getKillerName(), result.getVictimName(),
			result.getLootValue(), result.getWildernessLevel());
	}

	private String pkStreakMilestoneMessage(WorldPkSettlementResult result) {
		return pkStreakMilestoneMessage(result.getKillerName(), result.getStreakAfter());
	}

	static String qualifiedWildernessKillMessage(String killerName, String victimName,
		long lootValue, int wildernessLevel) {
		return "@mag@[Wilderness] @red@" + killerName
			+ " @whi@defeated @red@" + victimName
			+ " @whi@and secured @yel@" + lootValue
			+ " gp @whi@of qualified loot in level-" + wildernessLevel
			+ " Wilderness.";
	}

	static String pkStreakMilestoneMessage(String killerName, int streak) {
		if (streak == 3) {
			return "@mag@[PK Streak] @red@" + killerName
				+ " @whi@is heating up: @yel@3 qualified Wilderness kills@whi@!";
		}
		if (streak == 5) {
			return "@mag@[PK Streak] @red@" + killerName
				+ " @whi@is dominating the Wilderness: @yel@5 kills without dying@whi@!";
		}
		return "@mag@[PK Streak] @red@" + killerName
			+ " @whi@is @red@LEGENDARY@whi@: @yel@10 qualified kills without dying@whi@!";
	}

	private String newPlayerJoinedMessage(Player player) {
		return "@mag@[Void Herald] @gre@Welcome @whi@" + player.getUsername()
			+ "@gre@ to Voidscape. @whi@This is their first time in the world.";
	}

	private String uniqueTitleClaimMessage(Player player, String titleName) {
		return "@mag@[Void Herald] @whi@" + player.getUsername()
			+ " has claimed a @yel@unique title@whi@: @yel@" + titleName + "@whi@!";
	}

	private String uniqueTitleTransferMessage(Player player, String titleName, String previousOwner) {
		String previous = previousOwner == null || previousOwner.isEmpty() ? "" : " from " + previousOwner;
		return "@mag@[Void Herald] @whi@" + player.getUsername()
			+ " now holds @yel@" + titleName + "@whi@" + previous + ".";
	}

	private String newPlayerAnnouncementCacheKey(Player player) {
		int accountId = VoidSubscription.getAccountId(player);
		if (accountId > 0) {
			return NEW_PLAYER_ACCOUNT_CACHE_PREFIX + accountId;
		}
		int playerId = player.getDatabaseID();
		return playerId > 0 ? NEW_PLAYER_CHARACTER_CACHE_PREFIX + playerId : "";
	}

	private int highestCrossed(int[] milestones, int oldValue, int newValue) {
		int crossed = -1;
		for (int milestone : milestones) {
			if (oldValue < milestone && newValue >= milestone) {
				crossed = milestone;
			}
		}
		return crossed;
	}

	private String skillName(int skill) {
		if (skill == Skill.DEFENSE.id()) return "Defence";
		if (skill == Skill.HITS.id()) return "Hitpoints";

		String name = world.getServer().getConstants().getSkills().getSkill(skill).getLongName();
		if (name == null || name.isEmpty()) return "Unknown";
		return titleCase(name);
	}

	private String titleCase(String name) {
		String[] words = name.toLowerCase().split(" ");
		StringBuilder formatted = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) continue;
			if (formatted.length() > 0) formatted.append(' ');
			formatted.append(Character.toUpperCase(word.charAt(0)));
			if (word.length() > 1) formatted.append(word.substring(1));
		}
		return formatted.toString();
	}
}
