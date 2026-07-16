package com.openrsc.server.content.announcements;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.PlayerTitle;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public final class WorldAnnouncementService {
	private static final int[] SKILL_LEVEL_MILESTONES = {90, 95, 99};
	private static final int[] TOTAL_LEVEL_MILESTONES = {500, 750, 1000, 1250, 1500, 1750, 2000};
	private static final String NEW_PLAYER_ACCOUNT_CACHE_PREFIX = "new_join_acct:";
	private static final String NEW_PLAYER_CHARACTER_CACHE_PREFIX = "new_join_char:";
	private static final String NEW_PLAYER_LOCAL_CACHE_KEY = "void_announce_new_player";
	private static final int NEW_PLAYER_ANNOUNCED = 1;

	private final World world;

	public WorldAnnouncementService(World world) {
		this.world = world;
	}

	public void announceSkillMilestone(Player player, int skill, int oldLevel, int newLevel) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (!world.getServer().getConfig().WANT_WORLD_MILESTONE_ANNOUNCEMENTS) return;

		int milestone = highestCrossed(SKILL_LEVEL_MILESTONES, oldLevel, newLevel);
		int maxLevel = world.getServer().getConfig().PLAYER_LEVEL_LIMIT;
		if (oldLevel < maxLevel && newLevel >= maxLevel) {
			milestone = maxLevel;
		}
		if (milestone <= 0) return;

		String cacheKey = "void_announce_skill_" + skill + "_" + milestone;
		if (player.getCache().hasKey(cacheKey)) return;

		player.getCache().store(cacheKey, true);
		announce(skillMilestoneMessage(player, skill, milestone), false);
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
		if (killer == null || killed == null) return;
		if (!killed.getLocation().inWilderness()) return;
		if (PlayerTitle.activeHonorific(killed) != null) {
			announce(honorificWildernessKillMessage(killer, killed), false);
			return;
		}
		if (!world.getServer().getConfig().WANT_WORLD_SKULLED_PK_ANNOUNCEMENTS) return;
		if (!killed.isSkulled()) return;

		announce(skulledWildernessKillMessage(killer, killed), false);
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

	public void announceSupremeTitleClaim(Player player, String titleName) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (player == null || titleName == null || titleName.isEmpty()) return;
		announce("@mag@[Void Herald] @whi@Let it be known: " + player.getUsername()
			+ ", @red@" + titleName + "@whi@.", false);
	}

	public void announceSainthood(Player player) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS || player == null) return;
		announce("@mag@[Void Herald] @whi@Let it be known: @red@Saint " + player.getUsername()
			+ "@whi@ walks among us.", false);
	}

	public void announceContestedOfficeClaim(String claimantName, String titleName, String prefixForm) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (claimantName == null || claimantName.isEmpty() || titleName == null || titleName.isEmpty()) return;
		String claimant = prefixForm == null || prefixForm.isEmpty()
			? claimantName : prefixForm + " " + claimantName;
		announce("@mag@[Void Herald] @whi@" + claimant + " has claimed @yel@" + titleName + "@whi@.", false);
	}

	public void announceClosedContestedPeriod(String titleName, String prefixForm, String owner,
				int score, int period) {
		if (!world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		if (owner == null || owner.isEmpty()) return;
		String styledOwner = prefixForm == null || prefixForm.isEmpty() ? owner : prefixForm + " " + owner;
		String scoreText = score > 0 ? " with " + String.format("%,d", score) : "";
		announce("@mag@[Void Herald] @whi@All hail @yel@" + styledOwner + "@whi@, holder of "
			+ titleName + " for " + formatPeriod(period) + scoreText + ".", false);
	}

	private void announce(String message, boolean force) {
		if (!force && !world.getServer().getConfig().WANT_WORLD_ANNOUNCEMENTS) return;
		world.sendWorldMessage(message);
	}

	private String skillMilestoneMessage(Player player, int skill, int milestone) {
		return "@mag@[Void Herald] @whi@" + player.getUsername()
			+ " has reached @gre@" + milestone + " " + skillName(skill) + "@whi@.";
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

	private String honorificWildernessKillMessage(Player killer, Player killed) {
		return "@mag@[Wilderness] @whi@" + killer.getUsername() + " has slain @red@"
			+ PlayerTitle.styledName(killed) + "@whi@ in the Wilderness!";
	}

	private String newPlayerJoinedMessage(Player player) {
		return "@mag@[Void Herald] @gre@Welcome @whi@" + player.getUsername()
			+ "@gre@ to Voidscape. @whi@This is their first time in the world.";
	}

	private String uniqueTitleClaimMessage(Player player, String titleName) {
		return "@mag@[Void Herald] @whi@Let it be known: " + player.getUsername()
			+ ", @yel@" + titleName + "@whi@.";
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

	private String formatPeriod(int period) {
		String value = String.valueOf(period);
		return value.length() == 6 ? value.substring(0, 4) + "-" + value.substring(4) : value;
	}
}
