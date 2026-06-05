package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

public final class ProgressionMilestones {
	private static final int[] SKILL_LEVEL_MILESTONES = {20, 30, 40, 50, 60, 70, 80, 90, 99};
	private static final int[] TOTAL_LEVEL_MILESTONES = {250, 500, 750, 1000, 1250, 1500, 1750, 2000, 2250, 2500};
	private static final String SKILL_REWARD_KEY_PREFIX = "pm_s_";
	private static final String TOTAL_REWARD_KEY_PREFIX = "pm_t_";

	private ProgressionMilestones() {
	}

	public static void handleLevelUp(Player player, int skill, int oldLevel, int newLevel, int oldTotalLevel, int newTotalLevel) {
		if (player == null) {
			return;
		}

		boolean inventoryChanged = false;
		for (int milestone : SKILL_LEVEL_MILESTONES) {
			if (oldLevel < milestone && newLevel >= milestone) {
				inventoryChanged |= awardSkillMilestone(player, skill, milestone);
			}
		}

		for (int milestone : TOTAL_LEVEL_MILESTONES) {
			if (oldTotalLevel < milestone && newTotalLevel >= milestone) {
				inventoryChanged |= awardTotalMilestone(player, milestone);
			}
		}

		if (inventoryChanged) {
			ActionSender.sendInventory(player);
		}
	}

	private static boolean awardSkillMilestone(Player player, int skill, int milestone) {
		String cacheKey = SKILL_REWARD_KEY_PREFIX + skill + "_" + milestone;
		if (player.getCache().hasKey(cacheKey)) {
			return false;
		}

		player.getCache().store(cacheKey, true);
		int coins = skillRewardCoins(milestone);
		player.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(), coins), false);
		player.message("@yel@Milestone reward: @whi@level " + milestone + " " + skillName(player, skill)
			+ " - @gre@" + coins + " coins@whi@.");
		return true;
	}

	private static boolean awardTotalMilestone(Player player, int milestone) {
		String cacheKey = TOTAL_REWARD_KEY_PREFIX + milestone;
		if (player.getCache().hasKey(cacheKey)) {
			return false;
		}

		player.getCache().store(cacheKey, true);
		int coins = totalRewardCoins(milestone);
		player.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(), coins), false);
		player.message("@yel@Total milestone reward: @whi@" + milestone + " total level - @gre@" + coins + " coins@whi@.");
		return true;
	}

	private static int skillRewardCoins(int milestone) {
		if (milestone >= 99) return 2500;
		if (milestone >= 80) return milestone * 20;
		if (milestone >= 50) return milestone * 15;
		return milestone * 10;
	}

	private static int totalRewardCoins(int milestone) {
		return milestone * 2;
	}

	private static String skillName(Player player, int skill) {
		if (skill == Skill.DEFENSE.id()) return "Defence";
		if (skill == Skill.HITS.id()) return "Hitpoints";

		String name = player.getWorld().getServer().getConstants().getSkills().getSkill(skill).getLongName();
		if (name == null || name.isEmpty()) return "Unknown";
		return titleCase(name);
	}

	private static String titleCase(String name) {
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
