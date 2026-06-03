package com.openrsc.server.content.announcements;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public final class WorldAnnouncementService {
	private static final int[] SKILL_LEVEL_MILESTONES = {90, 95, 99};
	private static final int[] TOTAL_LEVEL_MILESTONES = {500, 750, 1000, 1250, 1500, 1750, 2000};

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
		if (!world.getServer().getConfig().WANT_WORLD_SKULLED_PK_ANNOUNCEMENTS) return;
		if (killer == null || killed == null) return;
		if (!killed.getLocation().inWilderness()) return;
		if (!killed.isSkulled()) return;

		announce(skulledWildernessKillMessage(killer, killed), false);
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
