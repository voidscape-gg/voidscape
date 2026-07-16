package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

/**
 * Voidscape's earned batching policy for skill actions.
 *
 * Limits are derived from permanent base levels, so temporary boosts and drains
 * never change the available batch size.
 */
public final class SkillBatching {

	private SkillBatching() {
	}

	public static int limitForLevel(int baseLevel) {
		if (baseLevel < 10) return 1;
		if (baseLevel < 20) return 2;
		if (baseLevel < 30) return 3;
		if (baseLevel < 40) return 4;
		if (baseLevel < 50) return 5;
		if (baseLevel < 60) return 7;
		if (baseLevel < 70) return 10;
		if (baseLevel < 80) return 14;
		if (baseLevel < 90) return 18;
		if (baseLevel < 99) return 24;
		return 30;
	}

	public static int limitForSkill(Player player, int skill) {
		return limitForLevel(player.getSkills().getMaxStat(skill));
	}

	public static int clampRequested(int requested, int baseLevel) {
		return Math.max(1, Math.min(requested, limitForLevel(baseLevel)));
	}

	public static int clampRequested(Player player, int requested, int skill) {
		return Math.max(1, Math.min(requested, limitForSkill(player, skill)));
	}

	public static void notifyLimitIncrease(Player player, int skill, int oldBaseLevel,
										int newBaseLevel, String skillName) {
		if (!player.getConfig().BATCH_PROGRESSION || !isGovernedSkill(skill)) {
			return;
		}
		int oldLimit = limitForLevel(oldBaseLevel);
		int newLimit = limitForLevel(newBaseLevel);
		if (newLimit > oldLimit) {
			player.message("@gre@Your " + skillName + " batch limit is now " + newLimit + ".");
		}
	}

	private static boolean isGovernedSkill(int skill) {
		return skill == Skill.PRAYER.id()
			|| skill == Skill.PRAYGOOD.id()
			|| skill == Skill.PRAYEVIL.id()
			|| skill == Skill.COOKING.id()
			|| skill == Skill.WOODCUTTING.id()
			|| skill == Skill.FLETCHING.id()
			|| skill == Skill.FISHING.id()
			|| skill == Skill.FIREMAKING.id()
			|| skill == Skill.CRAFTING.id()
			|| skill == Skill.SMITHING.id()
			|| skill == Skill.MINING.id()
			|| skill == Skill.HERBLAW.id()
			|| skill == Skill.THIEVING.id()
			|| skill == Skill.RUNECRAFT.id()
			|| skill == Skill.HARVESTING.id();
	}
}
