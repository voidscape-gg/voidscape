package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.content.VoidContent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Random;
import java.util.UUID;

/**
 * Optional opposed-margin combat ruleset described by
 * {@code docs/design/overmatch-combat-formula.md}.
 *
 * This class owns all Overmatch-only state and arithmetic so the default,
 * OpenRSC, and OSRS formula paths remain unchanged while the flag is off.
 */
public final class OvermatchCombatFormula {
	static final int DENOMINATOR = 1024;

	private static final int GLANCE_THRESHOLD = 205;
	private static final int SELF_GLANCE_FLOOR = 256;
	private static final int MELEE_CRIT_THRESHOLD = 614;
	private static final int RANGED_CRIT_THRESHOLD = 717;
	private static final int MAGIC_CRIT_THRESHOLD = 717;
	private static final int SELF_CRIT_FLOOR = 614;
	private static final int PVP_CRIT_CAP = 870;

	private static final int GLANCE_LOW = 51;
	private static final int GLANCE_HIGH = 307;
	private static final int SOLID_LOW = 410;
	private static final int SOLID_HIGH = 768;
	private static final int CRIT_LOW = 870;
	private static final int CRIT_HIGH = 1178;
	private static final int MAGIC_GRAZE_LOW = 0;
	private static final int MAGIC_GRAZE_HIGH = 358;

	private static final int EDGE_BONUS = 307;
	private static final int RIPOSTE_THRESHOLD = 922;
	private static final String EDGE_TARGET_ATTRIBUTE = "overmatchEdgeTarget";
	private static final String EDGE_STACKS_ATTRIBUTE = "overmatchEdgeStacks";

	private static final int NPC_ACCURACY_FLOOR = 15;
	private static final int NPC_POWER_FLOOR = 15;
	private static final int NPC_BONUS_START_LEVEL = 40;
	private static final int NPC_BONUS_LEVELS_PER_POINT = 8;
	private static final int NPC_BONUS_CAP = 12;

	private OvermatchCombatFormula() {
	}

	enum Tier {
		MISS,
		GLANCE,
		SOLID,
		CRITICAL
	}

	public static MeleeHitResult doMeleeHit(final Mob source, final Mob victim) {
		return doMeleeHit(source, victim, DataConversions.getRandom());
	}

	static MeleeHitResult doMeleeHit(final Mob source, final Mob victim, final Random random) {
		CombatFormula.clearPvpMeleeMomentum(source);

		final int attackScore = meleeAttackScore(source, victim);
		final int guardScore = guardScore(source, victim);
		final int maxHit = meleeMaxHit(source, victim);
		final int[] thresholds = thresholds(source, victim, attackScore, guardScore,
			MELEE_CRIT_THRESHOLD);
		final int edgeBonus = consumeEdge(source, victim)
			? EDGE_BONUS * guardScore / DENOMINATOR : 0;

		int attackDie = random.nextInt(attackScore) + edgeBonus;
		final int guardDie = random.nextInt(guardScore);
		Tier tier = tierForMargin(attackDie - guardDie, thresholds[0], thresholds[1]);

		if (tier == Tier.MISS && source instanceof Player
			&& SkillCapes.shouldActivate((Player) source, ItemId.ATTACK_CAPE, false)) {
			attackDie = random.nextInt(attackScore) + edgeBonus;
			tier = tierForMargin(attackDie - guardDie, thresholds[0], thresholds[1]);
			if (tier != Tier.MISS) {
				((Player) source).message("@red@Your Attack cape has prevented a zero hit");
			}
		}

		int damage = tier == Tier.MISS ? 0 : rollBandDamage(tier, maxHit, random);
		if (source instanceof Player && (tier == Tier.SOLID || tier == Tier.CRITICAL)
			&& SkillCapes.shouldActivate((Player) source, ItemId.STRENGTH_CAPE, true)) {
			damage += damage * 205 / DENOMINATOR;
			((Player) source).message("@ora@Your Strength cape has granted you a critical hit");
		}

		if (victim instanceof Player && damage > 0) {
			final int damageBeforeCape = damage;
			int blockedDamage = 0;
			if (SkillCapes.shouldActivate((Player) victim, ItemId.DEFENSE_CAPE)) {
				damage /= 2;
				blockedDamage = damage;
			}
			((Player) victim).updateDamageAndBlockedDamageTracking(source,
				damageBeforeCape, blockedDamage);
		}

		if (tier == Tier.CRITICAL && victim instanceof Player) {
			grantEdge(source, victim);
		}
		final int riposte = RIPOSTE_THRESHOLD * attackScore / DENOMINATOR;
		if (source instanceof Player && guardDie - attackDie >= riposte) {
			grantEdge(victim, source);
		}

		return new MeleeHitResult(tier != Tier.MISS,
			CombatFormula.applyPlayerAttackDamageFloor(source, victim, damage));
	}

	public static int calculateMeleeMaxHit(final Mob source, final Mob victim) {
		final int maximum = quantize(CRIT_HIGH * meleeMaxHit(source, victim));
		return CombatFormula.applyPlayerAttackDamageFloor(source, victim, maximum);
	}

	public static int doRangedDamage(final Mob source, final int bowId, final int arrowId,
									 final Mob victim, final boolean rangedCape) {
		final Random random = DataConversions.getRandom();
		int damage = rollRangedShot(source, bowId, arrowId, victim, random);
		// The existing cape promises two arrows. Resolve the extra arrow through the
		// same contest rather than falling back to the classic uniform damage roll.
		if (rangedCape) {
			damage += rollRangedShot(source, bowId, arrowId, victim, random);
		}
		return CombatFormula.applyPlayerAttackDamageFloor(source, victim, damage);
	}

	private static int rollRangedShot(final Mob source, final int bowId, final int arrowId,
									 final Mob victim, final Random random) {
		final int attackScore = rangedAttackScore(source, bowId, victim);
		final int guardScore = guardScore(source, victim);
		final int[] thresholds = thresholds(source, victim, attackScore, guardScore,
			RANGED_CRIT_THRESHOLD);
		final Tier tier = tierForMargin(random.nextInt(attackScore) - random.nextInt(guardScore),
			thresholds[0], thresholds[1]);
		return tier == Tier.MISS ? 0 : rollBandDamage(tier,
			rangedMaxHit(source, bowId, arrowId, victim), random);
	}

	public static int calculateRangedMaxHit(final Mob source, final int bowId, final int arrowId,
										 final Mob victim, final boolean rangedCape) {
		int maximum = quantize(CRIT_HIGH * rangedMaxHit(source, bowId, arrowId, victim));
		if (rangedCape) {
			maximum *= 2;
		}
		return CombatFormula.applyPlayerAttackDamageFloor(source, victim, maximum);
	}

	public static int calculateMagicDamage(final double spellPower, final Mob source,
										final Mob victim) {
		final int casterScore = magicAttackScore(source);
		final int magicGuard = magicGuardScore(victim);
		final int glanceThreshold = GLANCE_THRESHOLD * magicGuard / DENOMINATOR;
		final int critThreshold = MAGIC_CRIT_THRESHOLD * magicGuard / DENOMINATOR;
		final int solidAt = Math.max(glanceThreshold, 1);
		final int criticalAt = Math.max(critThreshold, solidAt);
		final Random random = DataConversions.getRandom();
		final int margin = random.nextInt(casterScore) - random.nextInt(magicGuard);

		final Tier tier;
		if (margin >= criticalAt) {
			tier = Tier.CRITICAL;
		} else if (margin >= solidAt) {
			tier = Tier.SOLID;
		} else {
			tier = Tier.GLANCE;
		}

		final int effectiveSpellPower = Math.max(0, (int) Math.floor(spellPower
			* VoidContent.voidSceptreMagicMultiplier(source, victim)));
		final int damage = rollMagicBandDamage(tier, effectiveSpellPower, random);
		return CombatFormula.applyMagicDamageReduction(damage, source, victim);
	}

	static Tier tierForMargin(final int margin, final int glanceAt, final int criticalAt) {
		if (margin <= 0) {
			return Tier.MISS;
		}
		if (margin < glanceAt) {
			return Tier.GLANCE;
		}
		if (margin < criticalAt) {
			return Tier.SOLID;
		}
		return Tier.CRITICAL;
	}

	static int quantize(final int fixedPointDamage) {
		return (fixedPointDamage + DENOMINATOR / 2) >> 10;
	}

	private static int rollBandDamage(final Tier tier, final int maxHit, final Random random) {
		if (tier == Tier.GLANCE) {
			return quantize(randomInclusive(random, GLANCE_LOW * maxHit, GLANCE_HIGH * maxHit));
		}
		if (tier == Tier.SOLID) {
			return quantize(randomInclusive(random, SOLID_LOW * maxHit, SOLID_HIGH * maxHit));
		}
		return quantize(randomInclusive(random, CRIT_LOW * maxHit, CRIT_HIGH * maxHit));
	}

	private static int rollMagicBandDamage(final Tier tier, final int spellPower,
										 final Random random) {
		if (tier == Tier.GLANCE) {
			return quantize(randomInclusive(random, MAGIC_GRAZE_LOW,
				MAGIC_GRAZE_HIGH * spellPower));
		}
		return rollBandDamage(tier, spellPower, random);
	}

	private static int randomInclusive(final Random random, final int low, final int high) {
		return low + random.nextInt(high - low + 1);
	}

	private static int[] thresholds(final Mob source, final Mob victim, final int attackScore,
									final int guardScore, final int critNumerator) {
		final int glanceAt = Math.max(GLANCE_THRESHOLD * guardScore / DENOMINATOR,
			SELF_GLANCE_FLOOR * attackScore / DENOMINATOR);
		int criticalAt = critNumerator * guardScore / DENOMINATOR;
		if (source instanceof Player && victim instanceof Player) {
			criticalAt = Math.min(criticalAt, PVP_CRIT_CAP * attackScore / DENOMINATOR);
		}
		criticalAt = Math.max(criticalAt, SELF_CRIT_FLOOR * attackScore / DENOMINATOR);
		criticalAt = Math.max(criticalAt, glanceAt);
		return new int[] {glanceAt, criticalAt};
	}

	private static int meleeAttackScore(final Mob source, final Mob victim) {
		final int level = npcLevel(source, victim,
			source.getSkills().getLevel(Skill.ATTACK.id()), NPC_ACCURACY_FLOOR);
		final int effective = (int) Math.floor(level * CombatFormula.addPrayers(source,
			Prayers.CLARITY_OF_THOUGHT, Prayers.IMPROVED_REFLEXES,
			Prayers.INCREDIBLE_REFLEXES))
			+ playerBonus(source) + CombatFormula.styleBonus(source, Skill.ATTACK.id());
		long score = (long) effective * (source.getWeaponAimPoints() + 64);
		if (source instanceof Npc && victim instanceof Player) {
			score = score * 110 / 100;
		}
		if (CombatFormula.voidMeleeMultiplier(source, victim) > 1.0D) {
			score = score * 115 / 100;
		}
		return boundedScore(score);
	}

	private static int guardScore(final Mob source, final Mob victim) {
		final int effective = (int) Math.floor(victim.getSkills().getLevel(Skill.DEFENSE.id())
			* CombatFormula.addPrayers(victim, Prayers.THICK_SKIN, Prayers.ROCK_SKIN,
			Prayers.STEEL_SKIN))
			+ playerBonus(victim) + CombatFormula.styleBonus(victim, Skill.DEFENSE.id());
		long score = (long) effective * (64 + victim.getArmourPoints() * 3 / 5);
		if (source instanceof Player && victim instanceof Npc) {
			score = score * 110 / 100;
		}
		return boundedScore(score);
	}

	private static int meleeMaxHit(final Mob source, final Mob victim) {
		final int level = npcLevel(source, victim,
			source.getSkills().getLevel(Skill.STRENGTH.id()), NPC_POWER_FLOOR);
		final int effective = (int) Math.floor(level * CombatFormula.addPrayers(source,
			Prayers.BURST_OF_STRENGTH, Prayers.SUPERHUMAN_STRENGTH,
			Prayers.ULTIMATE_STRENGTH))
			+ playerBonus(source) + CombatFormula.styleBonus(source, Skill.STRENGTH.id());
		long roll = (long) effective * (source.getWeaponPowerPoints() + 64);
		if (CombatFormula.voidMeleeMultiplier(source, victim) > 1.0D) {
			roll = roll * 115 / 100;
		}
		return Math.max(1, (int) (roll / 640));
	}

	private static int rangedAttackScore(final Mob source, final int bowId, final Mob victim) {
		long score = (long) (source.getSkills().getLevel(Skill.RANGED.id()) + playerBonus(source))
			* (CombatFormula.rangedAim(bowId) + 1 + 64);
		if (source instanceof Npc && victim instanceof Player) {
			score = score * 110 / 100;
		}
		if (CombatFormula.voidRangedMultiplier(source, bowId, victim) > 1.0D) {
			score = score * 115 / 100;
		}
		return boundedScore(score);
	}

	private static int rangedMaxHit(final Mob source, final int bowId, final int arrowId,
									final Mob victim) {
		final int power = source.getConfig().RETRO_RANGED_DAMAGE
			? CombatFormula.rangedPowerRetro(bowId) : CombatFormula.rangedPower(arrowId);
		long roll = (long) (source.getSkills().getLevel(Skill.RANGED.id()) + playerBonus(source))
			* (power + 1 + 64);
		if (CombatFormula.voidRangedMultiplier(source, bowId, victim) > 1.0D) {
			roll = roll * 115 / 100;
		}
		return Math.max(1, (int) (roll / 640));
	}

	private static int magicAttackScore(final Mob source) {
		final int magicBonus = source instanceof Player ? ((Player) source).getMagicPoints() : 1;
		final long score = (long) (source.getSkills().getLevel(Skill.MAGIC.id())
			+ playerBonus(source)) * (64 + magicBonus * 8);
		return boundedScore(score);
	}

	private static int magicGuardScore(final Mob victim) {
		final int effective = (int) Math.floor(victim.getSkills().getLevel(Skill.DEFENSE.id())
			* CombatFormula.addPrayers(victim, Prayers.THICK_SKIN, Prayers.ROCK_SKIN,
			Prayers.STEEL_SKIN))
			+ playerBonus(victim) + CombatFormula.styleBonus(victim, Skill.DEFENSE.id());
		final long score = (long) effective
			* (64 + victim.getArmourPoints() * 123 / DENOMINATOR);
		return boundedScore(score);
	}

	private static int playerBonus(final Mob mob) {
		return mob instanceof Player ? 8 : 0;
	}

	private static int npcLevel(final Mob source, final Mob victim, final int level,
								final int floor) {
		if (!(source instanceof Npc) || !(victim instanceof Player)) {
			return level;
		}
		int effective = Math.max(level, floor);
		final int offence = Math.max(source.getSkills().getLevel(Skill.ATTACK.id()),
			source.getSkills().getLevel(Skill.STRENGTH.id()));
		if (offence >= NPC_BONUS_START_LEVEL) {
			final int bonus = Math.min(NPC_BONUS_CAP,
				((offence - NPC_BONUS_START_LEVEL) / NPC_BONUS_LEVELS_PER_POINT) + 1);
			effective += bonus;
		}
		return effective;
	}

	private static int boundedScore(final long score) {
		return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, score));
	}

	private static boolean consumeEdge(final Mob source, final Mob victim) {
		final UUID target = source.getAttribute(EDGE_TARGET_ATTRIBUTE, null);
		final int stacks = source.getAttribute(EDGE_STACKS_ATTRIBUTE, 0);
		clearEdge(source);
		return stacks > 0 && target != null && target.equals(victim.getUUID());
	}

	private static void grantEdge(final Mob source, final Mob victim) {
		source.setAttribute(EDGE_TARGET_ATTRIBUTE, victim.getUUID());
		source.setAttribute(EDGE_STACKS_ATTRIBUTE, 1);
	}

	public static void clearEdge(final Mob source) {
		if (source == null) {
			return;
		}
		source.removeAttribute(EDGE_TARGET_ATTRIBUTE);
		source.removeAttribute(EDGE_STACKS_ATTRIBUTE);
	}
}
