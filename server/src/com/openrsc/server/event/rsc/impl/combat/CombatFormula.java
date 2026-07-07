package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.SkillCapes;
import com.openrsc.server.content.VoidContent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

import static com.openrsc.server.constants.ItemId.*;

public class CombatFormula {
	/**
	 * Logger instance
	 */
	private static final Logger LOGGER = LogManager.getLogger();
	private static final double VOIDSCAPE_ARMOUR_ACCURACY_SCALE = 0.60D;
	private static final double VOIDSCAPE_PHYSICAL_MITIGATION_DIVISOR = 1200.0D;
	private static final double VOIDSCAPE_PHYSICAL_MITIGATION_CAP = 0.24D;
	private static final double VOIDSCAPE_MAGIC_PLAYER_DAMAGE_SCALE = 0.92D;
	private static final int NPC_VS_PLAYER_MELEE_FLOOR = 15;
	private static final int NPC_VS_PLAYER_OFFENCE_BONUS_START_LEVEL = 40;
	private static final int NPC_VS_PLAYER_OFFENCE_BONUS_LEVELS_PER_POINT = 8;
	private static final int NPC_VS_PLAYER_OFFENCE_BONUS_CAP = 12;
	private static final String PVP_MELEE_MOMENTUM_TARGET_ATTRIBUTE = "pvpMeleeMomentumTarget";
	private static final String PVP_MELEE_MOMENTUM_STACKS_ATTRIBUTE = "pvpMeleeMomentumStacks";
	private static final double PVP_MELEE_MOMENTUM_BIG_HIT_RATIO = 0.68D;
	private static final int PVP_MELEE_MOMENTUM_MAX_STACKS = 1;

	/**
	 * Gets a dice roll for melee damage for a single attack
	 * The result is an int sourced from randomness effectively from 0.5 - maxHit.
	 * Max hit is fractional, so it is more rare than other values in most circumstances.
	 * 0 rolls are biased against in a similar way.
	 * @param source      The mob doing the damage
	 * @return The randomized value.
	 */
	private static int calculateMeleeDamage(final Mob source, final Mob victim) {
		int maxRoll = getMeleeDamage(source, victim);
		int chosenHit = maxRoll <= 0 ? 0 : (DataConversions.getRandom().nextInt(maxRoll) + 320) / 640;
		return chosenHit;
	}

	/**
	 * Gets a dice roll for ranged damage for a single attack
	 * The result is an int sourced from randomness effectively from 0.5 - maxHit.
	 * Max hit is fractional, so it is more rare than other values in most circumstances.
	 * 0 rolls are biased against in a similar way.
	 * @param source      The mob doing the damage
	 * @param arrowId	  The type of ranged ammunition
	 * @return The randomized value.
	 */
	private static int calculateRangedDamage(final Mob source, final int bowId, final int arrowId, final Mob victim) {
		int maxRoll = getRangedDamage(source, bowId, arrowId, victim);
		int chosenHit = (DataConversions.getRandom().nextInt(maxRoll) + 320) / 640;
		return chosenHit;
	}

	/**
	 * Gets a dice roll for magic damage for a single attack.
	 * @param spellPower      The max hit of the spell
	 * @return The randomized value.
	 */
	public static int calculateMagicDamage(final double spellPower) {
		return calculateMagicDamage(spellPower, null, null);
	}

	public static int calculateMagicDamage(final double spellPower, final Mob victim) {
		return calculateMagicDamage(spellPower, null, victim);
	}

	public static int calculateMagicDamage(final double spellPower, final Mob source, final Mob victim) {
		//Given that melee max hit is fractional, it was likely that spell power values ending in "5" were supposed to hit their max hit more often.
		//TODO: More research to see if that was the case. For now, we can just make it uniform after flooring.
		final int damage = DataConversions.getRandom().nextInt((int)Math.floor(spellPower) + 1);
		return applyMagicDamageReduction(damage, source, victim);
	}

	/**
	 * Gets a dice roll for magic damage (god spells) for a single attack
	 *
	 * @param source      The player casting this spell
	 * @return The randomized value.
	 */
	public static int calculateGodSpellDamage(final Player source) {
		return calculateGodSpellDamage(source, null);
	}

	public static int calculateGodSpellDamage(final Player source, final Mob victim) {
		int[] godCapes = new int[] {
			ZAMORAK_CAPE.id(),
			SARADOMIN_CAPE.id(),
			GUTHIX_CAPE.id()
		};

		//Authentically, players only receive Charge benefit if they have a god cape equipped.
		boolean hasCapeEquipped = false;
		for (int capeId : godCapes) {
			if (source.getCarriedItems().getEquipment().hasEquipped(capeId)) {
				hasCapeEquipped = true;
				break;
			}
		}
		boolean hasChargeBenefit = source.isCharged() && hasCapeEquipped;
		int godSpellMax = hasChargeBenefit ? 25 : 18;

		return calculateMagicDamage(godSpellMax, source, victim);
	}

	/**
	 * Gets a dice roll for magic damage (iban blast) for a single attack
	 *
	 * @return The randomized value.
	 */
	public static int calculateIbanSpellDamage() {
		return calculateIbanSpellDamage(null);
	}

	public static int calculateIbanSpellDamage(final Mob victim) {
		return calculateIbanSpellDamage(null, victim);
	}

	public static int calculateIbanSpellDamage(final Player source, final Mob victim) {
		// TODO: Remove this code and roll it into calculateMagicDamage
		// Source for max damage: http://web.archive.org/web/20041226185618/http://www.rsinn.com/forum/showthread.php?t=2469
		return calculateMagicDamage(15, source, victim);
	}

	/**
	 * Calculates an accuracy check (base method)
	 *
	 * @param accuracy            The accuracy term
	 * @param defence             The defence term
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateAccuracy(final double accuracy, final double defence) {
		double hitChance;
		if (accuracy > defence) {
			hitChance = 1 - ((defence + 2) / (2 * (accuracy + 1)));
		} else {
			hitChance = (accuracy) / (2 * (defence + 1));
		}

		double rand = Math.random();
		boolean didHit = rand <= hitChance;

		return didHit;
	}


	/**
	 * Calculates an accuracy check (melee)
	 *
	 * @param source             The attacking mob.
	 * @param victim             The mob being attacked.
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateMeleeAccuracy(final Mob source, final Mob victim) {
		return calculateAccuracy(getMeleeAccuracy(source, victim), getMeleeDefence(victim));
	}

	/**
	 * Calculates an accuracy check (ranged)
	 *
	 * @param source             The attacking mob.
	 * @param bowId				 The type of ranged weapon being wielded
	 * @param victim             The mob being attacked.
	 * @return True if the attack is a hit, false if the attack is a miss
	 */
	private static boolean calculateRangedAccuracy(final Mob source, final int bowId, final Mob victim) {
		return calculateAccuracy(getRangedAccuracy(source, bowId, victim), getMeleeDefence(victim));
	}

	/**
	 * Gets the damage dealt for a specific attack. Includes accuracy checks.
	 *
	 * @param source             The attacking mob.
	 * @param victim             The mob being attacked.
	 * @return The amount to hit.
	 */
	public static int doMeleeDamage(final Mob source, final Mob victim) {
		final boolean isPvpMelee = source instanceof Player && victim instanceof Player;
		boolean isHit = calculateMeleeAccuracy(source, victim);
		boolean wasHit = isHit;
		if (source instanceof Player) {
			while(SkillCapes.shouldActivate((Player)source, ATTACK_CAPE, isHit)){
				isHit = calculateMeleeAccuracy(source, victim);
			}
			if (!wasHit && isHit)
				((Player) source).message("@red@Your Attack cape has prevented a zero hit");
		}

		int damage = 0;
		if (isHit) {
			damage = rollMeleeDamage(source, victim, isPvpMelee && consumePvpMeleeMomentum(source, victim));
		} else if (isPvpMelee) {
			clearPvpMeleeMomentum(source);
		}

		if (victim instanceof Player) {
			// Track the damage dealt to the player
			Player playerVictim = (Player)victim;
			if (isHit) {
				int damageToPlayer = damage;
				int blockedDamage = 0;

				// Defense skillcape
				if (SkillCapes.shouldActivate((Player) victim, DEFENSE_CAPE) && damageToPlayer > 0) {
					damage /= 2;
					blockedDamage = damage;
				}

				playerVictim.updateDamageAndBlockedDamageTracking(source, damageToPlayer, blockedDamage);
			}
		}
		if (source instanceof Player) {

			final double maximum = (double) (getMeleeDamage(source, victim) + 320) / 640;
			if (damage >= (maximum * 0.5) && SkillCapes.shouldActivate((Player) source, STRENGTH_CAPE, isHit)) {
				damage += (maximum*0.2);
				((Player) source).message("@ora@Your Strength cape has granted you a critical hit");
			}
		}
		if (isPvpMelee && isHit) {
			updatePvpMeleeMomentum(source, victim, damage);
		}

		//LOGGER.info(source + " " + (isHit ? "hit" : "missed") + " " + victim + ", Damage: " + damage);

		return applyPlayerAttackDamageFloor(source, victim, isHit ? damage : 0);
	}

	private static int rollMeleeDamage(final Mob source, final Mob victim, final boolean hasMomentum) {
		int damage = calculateMeleeDamage(source, victim);
		if (hasMomentum) {
			damage = Math.max(damage, calculateMeleeDamage(source, victim));
		}
		return applyPhysicalDamageReduction(damage, victim);
	}

	/**
	 * Gets the damage dealt for a specific attack. Includes accuracy checks.
	 *
	 * @param source             The attacking mob.
	 * @param bowId				 The ranged ammo being wielded
	 * @param arrowId			 The ranged weapon being wielded
	 * @param victim             The mob being attacked.
	 * @return The amount to hit.
	 */
	public static int doRangedDamage(final Mob source, final int bowId, final int arrowId, final Mob victim, final boolean skillCape) {
		boolean isHit = calculateRangedAccuracy(source, bowId, victim);

		if (!isHit) return applyPlayerAttackDamageFloor(source, victim, 0);

		final int damage;
		if (skillCape) {
			int maxHit = (getRangedDamage(source, bowId, arrowId, victim) + 320) / 640;
			damage = DataConversions.getRandom().nextInt(maxHit * 2);
		} else {
			damage = calculateRangedDamage(source, bowId, arrowId, victim);
		}

		//LOGGER.info(source + " " + (isHit ? "hit" : "missed") + " " + victim + ", Damage: " + damage);

		return applyPlayerAttackDamageFloor(source, victim, applyPhysicalDamageReduction(damage, victim));
	}

	/**
	 * Gets the melee max roll of the attacking mob.
	 * @param source             The attacking mob.
	 * @return The max hit
	 */
	private static int getMeleeDamage(final Mob source) {
		final int styleBonus = styleBonus(source, 2);
		final double prayerBonus = addPrayers(source, Prayers.BURST_OF_STRENGTH,
			Prayers.SUPERHUMAN_STRENGTH,
			Prayers.ULTIMATE_STRENGTH);

		final int bonusConstant = source.isPlayer() ? 8 : 0;
		final double maxRoll = (Math.floor(source.getSkills().getLevel(Skill.STRENGTH.id()) * prayerBonus) + bonusConstant + styleBonus) * (source.getWeaponPowerPoints() + 64);
		return (int)maxRoll;
	}

	private static int getMeleeDamage(final Mob source, final Mob victim) {
		int maxRoll = getMeleeDamage(source);
		if (source instanceof Npc && victim instanceof Player) {
			final int styleBonus = styleBonus(source, Skill.STRENGTH.id());
			final double prayerBonus = addPrayers(source, Prayers.BURST_OF_STRENGTH,
				Prayers.SUPERHUMAN_STRENGTH,
				Prayers.ULTIMATE_STRENGTH);
			final int effectiveStrength = npcVsPlayerMeleeLevel(source, Skill.STRENGTH.id());
			maxRoll = (int)((Math.floor(effectiveStrength * prayerBonus) + styleBonus) * (source.getWeaponPowerPoints() + 64));
		}
		return (int) (maxRoll * voidMeleeMultiplier(source, victim));
	}

	/**
	 * Gets the ranged max roll of the attacking mob.
	 *
	 * @param source             The attacking mob.
	 * @return The max hit
	 */
	private static int getRangedDamage(final Mob source, final int bowId, final int arrowId) {
		final int bonusConstant = source.isPlayer() ? 8 : 0; //NPCs can't range authentically - this should be considered if custom content implements this.
		final int power = source.getConfig().RETRO_RANGED_DAMAGE ? rangedPowerRetro(bowId) : rangedPower(arrowId);
		final double maxRoll = (source.getSkills().getLevel(Skill.RANGED.id()) + bonusConstant) * (power + 1 + 64);
		return (int)maxRoll;
	}

	private static int getRangedDamage(final Mob source, final int bowId, final int arrowId, final Mob victim) {
		return (int) (getRangedDamage(source, bowId, arrowId) * voidRangedMultiplier(source, bowId, victim));
	}

	/**
	 * Gets the melee defence of the defending mob
	 *
	 * @param defender             The defending mob.
	 * @return The melee defence
	 */
	private static double getMeleeDefence(final Mob defender) {
		final int styleBonus = styleBonus(defender, 1);
		final double prayerBonus = addPrayers(defender, Prayers.THICK_SKIN,
			Prayers.ROCK_SKIN,
			Prayers.STEEL_SKIN);
		final int bonusConstant = defender.isPlayer() ? 8 : 0;
		final double defense = (Math.floor(defender.getSkills().getLevel(Skill.DEFENSE.id()) * prayerBonus) + bonusConstant + styleBonus) * getArmourAccuracyPoints(defender);
		return defense;
	}

	private static double getArmourAccuracyPoints(final Mob defender) {
		return 64 + (defender.getArmourPoints() * VOIDSCAPE_ARMOUR_ACCURACY_SCALE);
	}

	private static double getPhysicalDamageReduction(final Mob victim) {
		if (victim == null || victim.getArmourPoints() <= 1) {
			return 0.0D;
		}
		return Math.min(VOIDSCAPE_PHYSICAL_MITIGATION_CAP, victim.getArmourPoints() / VOIDSCAPE_PHYSICAL_MITIGATION_DIVISOR);
	}

	private static int applyPhysicalDamageReduction(final int damage, final Mob victim) {
		if (damage <= 0) {
			return 0;
		}
		final double reduction = getPhysicalDamageReduction(victim);
		if (reduction <= 0) {
			return damage;
		}
		return Math.max(1, (int)Math.floor(damage * (1.0D - reduction)));
	}

	private static int applyMagicDamageReduction(final int damage, final Mob source, final Mob victim) {
		if (damage <= 0 || !(victim instanceof Player)) {
			return damage;
		}
		final Player playerVictim = (Player) victim;
		if (isProtectFromMagicBlockableSource(source)
			&& playerVictim.getPrayers().isPrayerActivated(Prayers.PROTECT_FROM_MAGIC)) {
			return 0;
		}
		return Math.max(1, (int)Math.floor(damage * VOIDSCAPE_MAGIC_PLAYER_DAMAGE_SCALE));
	}

	private static boolean isProtectFromMagicBlockableSource(final Mob source) {
		if (!(source instanceof Npc)) {
			return false;
		}
		final int npcId = ((Npc) source).getID();
		return npcId != NpcId.DM_KING.id() && npcId != NpcId.DM_KING_ARENA.id();
	}

	private static int applyPlayerAttackDamageFloor(final Mob source, final Mob victim, final int damage) {
		if (!(source instanceof Player) || !(victim instanceof Npc)) {
			return damage;
		}

		final int floor = ((Npc) victim).getPlayerAttackDamageFloor();
		if (floor <= 0) {
			return damage;
		}

		final int remainingHits = victim.getSkills().getLevel(Skill.HITS.id());
		if (remainingHits <= 0) {
			return damage;
		}
		return Math.min(remainingHits, Math.max(damage, floor));
	}

	private static boolean consumePvpMeleeMomentum(final Mob source, final Mob victim) {
		final UUID momentumTarget = source.getAttribute(PVP_MELEE_MOMENTUM_TARGET_ATTRIBUTE, null);
		final int stacks = source.getAttribute(PVP_MELEE_MOMENTUM_STACKS_ATTRIBUTE, 0);
		if (stacks <= 0 || momentumTarget == null) {
			clearPvpMeleeMomentum(source);
			return false;
		}
		if (!momentumTarget.equals(victim.getUUID())) {
			clearPvpMeleeMomentum(source);
			return false;
		}
		clearPvpMeleeMomentum(source);
		return true;
	}

	private static void updatePvpMeleeMomentum(final Mob source, final Mob victim, final int damage) {
		if (damage <= 0) {
			clearPvpMeleeMomentum(source);
			return;
		}
		final int maxHit = getTargetAdjustedMeleeMaxHit(source, victim);
		final int bigHitThreshold = Math.max(1, (int)Math.ceil(maxHit * PVP_MELEE_MOMENTUM_BIG_HIT_RATIO));
		if (maxHit > 0 && damage >= bigHitThreshold) {
			source.setAttribute(PVP_MELEE_MOMENTUM_TARGET_ATTRIBUTE, victim.getUUID());
			source.setAttribute(PVP_MELEE_MOMENTUM_STACKS_ATTRIBUTE, PVP_MELEE_MOMENTUM_MAX_STACKS);
		} else {
			clearPvpMeleeMomentum(source);
		}
	}

	private static int getTargetAdjustedMeleeMaxHit(final Mob source, final Mob victim) {
		final int maxRoll = getMeleeDamage(source, victim);
		if (maxRoll <= 0) {
			return 0;
		}
		return applyPhysicalDamageReduction((maxRoll - 1 + 320) / 640, victim);
	}

	public static int calculateMeleeMaxHit(final Mob source, final Mob victim) {
		return applyPlayerAttackDamageFloor(source, victim, getTargetAdjustedMeleeMaxHit(source, victim));
	}

	public static int calculateRangedMaxHit(final Mob source, final int bowId, final int arrowId, final Mob victim, final boolean skillCape) {
		final int maxRoll = getRangedDamage(source, bowId, arrowId, victim);
		if (maxRoll <= 0) {
			return 0;
		}
		final int maxHit = skillCape
			? Math.max(0, (((maxRoll + 320) / 640) * 2) - 1)
			: (maxRoll - 1 + 320) / 640;
		return applyPlayerAttackDamageFloor(source, victim, applyPhysicalDamageReduction(maxHit, victim));
	}

	public static int calculateMagicMaxHit(final double spellPower, final Mob victim) {
		return applyMagicDamageReduction((int)Math.floor(spellPower), null, victim);
	}

	private static void clearPvpMeleeMomentum(final Mob source) {
		source.removeAttribute(PVP_MELEE_MOMENTUM_TARGET_ATTRIBUTE);
		source.removeAttribute(PVP_MELEE_MOMENTUM_STACKS_ATTRIBUTE);
	}


	/**
	 * Gets the ranged accuracy of the attacking mob
	 *
	 * @param attacker             The attacking mob.
	 * @param bowId				   The ranged weapon being wielded
	 * @return The ranged accuracy
	 */
	private static double getRangedAccuracy(final Mob attacker, final int bowId) {
		final int bonusConstant = attacker.isPlayer() ? 8 : 0; //NPCs can't range authentically - this should be considered if custom content implements this.
		return (attacker.getSkills().getLevel(Skill.RANGED.id()) + bonusConstant) * (rangedAim(bowId) + 1 + 64);
	}

	private static double getRangedAccuracy(final Mob attacker, final int bowId, final Mob defender) {
		return getRangedAccuracy(attacker, bowId) * voidRangedMultiplier(attacker, bowId, defender);
	}

	/**
	 * Gets the melee accuracy of the attacking mob
	 *
	 * @param attacker             The attacking mob.
	 * @return The melee accuracy
	 */
	private static double getMeleeAccuracy(final Mob attacker) {
		final int styleBonus = styleBonus(attacker, 0);
		final double prayerBonus = addPrayers(attacker, Prayers.CLARITY_OF_THOUGHT,
			Prayers.IMPROVED_REFLEXES,
			Prayers.INCREDIBLE_REFLEXES);

		final int bonusConstant = attacker.isPlayer() ? 8 : 0;
		final double accuracy = (Math.floor(attacker.getSkills().getLevel(Skill.ATTACK.id()) * prayerBonus) + bonusConstant + styleBonus) * (attacker.getWeaponAimPoints() + 64);

		return accuracy;
	}

	private static double getMeleeAccuracy(final Mob attacker, final Mob defender) {
		double accuracy = getMeleeAccuracy(attacker);
		if (attacker instanceof Npc && defender instanceof Player) {
			final int styleBonus = styleBonus(attacker, Skill.ATTACK.id());
			final double prayerBonus = addPrayers(attacker, Prayers.CLARITY_OF_THOUGHT,
				Prayers.IMPROVED_REFLEXES,
				Prayers.INCREDIBLE_REFLEXES);
			final int effectiveAttack = npcVsPlayerMeleeLevel(attacker, Skill.ATTACK.id());
			accuracy = (Math.floor(effectiveAttack * prayerBonus) + styleBonus) * (attacker.getWeaponAimPoints() + 64);
		}
		return accuracy * voidMeleeMultiplier(attacker, defender);
	}

	private static int npcVsPlayerMeleeLevel(final Mob npc, final int skillId) {
		final int skillLevel = npc.getSkills().getLevel(skillId);
		int effectiveLevel = Math.max(NPC_VS_PLAYER_MELEE_FLOOR, skillLevel);
		final int offenseLevel = Math.max(npc.getSkills().getLevel(Skill.ATTACK.id()), npc.getSkills().getLevel(Skill.STRENGTH.id()));
		if (offenseLevel < NPC_VS_PLAYER_OFFENCE_BONUS_START_LEVEL) {
			return effectiveLevel;
		}
		final int bonus = Math.min(NPC_VS_PLAYER_OFFENCE_BONUS_CAP,
			((offenseLevel - NPC_VS_PLAYER_OFFENCE_BONUS_START_LEVEL) / NPC_VS_PLAYER_OFFENCE_BONUS_LEVELS_PER_POINT) + 1);
		return effectiveLevel + bonus;
	}

	private static double voidMeleeMultiplier(final Mob attacker, final Mob defender) {
		if (!(attacker instanceof Player) || !VoidContent.isVoidNpc(defender)) {
			return 1.0D;
		}
		final Player player = (Player) attacker;
		if (player.getCarriedItems().getEquipment().hasEquipped(VOID_MACE.id())) {
			return VoidContent.VOID_MACE_MELEE_MULTIPLIER;
		}
		if (player.getCarriedItems().getEquipment().hasEquipped(VOID_SCIMITAR.id())) {
			return VoidContent.VOID_SCIMITAR_MELEE_MULTIPLIER;
		}
		return 1.0D;
	}

	private static double voidRangedMultiplier(final Mob attacker, final int bowId, final Mob defender) {
		if (!(attacker instanceof Player) || bowId != VOID_BOW.id() || !VoidContent.isVoidNpc(defender)) {
			return 1.0D;
		}
		return VoidContent.VOID_BOW_RANGED_MULTIPLIER;
	}

	/**
	 * Gets the amount of skill points to be added for a specific skill based on style bonus
	 *
	 * @param attacker             The attacking mob.
	 * @return The amount of skill points to add for combat style
	 */
	protected static int styleBonus(final Mob attacker, final int skill) {
		if (attacker.isNpc())
			return 0;

		final int style = attacker.getCombatStyle();
		if (style == Skills.CONTROLLED_MODE)
			return 1;

		return (skill == Skill.ATTACK.id() && style == Skills.ACCURATE_MODE) || (skill == Skill.DEFENSE.id() && style == Skills.DEFENSIVE_MODE)
			|| (skill == Skill.STRENGTH.id() && style == Skills.AGGRESSIVE_MODE) ? 3 : 0;
	}

	/**
	 * Get the prayer multiplier for the context mob's skill
	 *
	 * @param source             The context mob.
	 * @return A multiplier to modify the context mob's relevant stat to the prayers.
	 */
	protected static double addPrayers(final Mob source, final int prayer1, final int prayer2, final int prayer3) {
		if (source.isNpc()) {
			return source.getWorld().getVoidArena().dmKingPrayerMultiplier(source, prayer1, prayer2, prayer3);
		}
		if (source.isPlayer()) {
			final Player sourcePlayer = (Player) source;
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer3)) {
				return 1.15D;
			}
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer2)) {
				return 1.1D;
			}
			if (sourcePlayer.getPrayers().isPrayerActivated(prayer1)) {
				return 1.05D;
			}
		}
		return 1.0D;
	}

	/**
	 * Returns a power to associate with each bow (pre-Fletching version)
	 *
	 * Uses values from the old projectile.txt file included with configXX.jag.
	 */
	private static int rangedPowerRetro(final int bowId) {
		switch (ItemId.getById(bowId)) {
			case SHORTBOW:
				return 14;
			case LONGBOW:
				return 20;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 22;
			default:
				return 0;
		}
	}

	/**
	 * Returns a power to associate with each arrow (post-Fletching version)
	 */
	private static int rangedPower(final int arrowId) {
		/**
		 * We don't have good data for throwing knives,
		 * so everything besides rune knives is a guess based on
		 * arrows increasing by 5 per tier.
		 * Rune spear should be accurate since the stats were leaked.
		 * Note circa 14th May 2023: even this might be wrong now. The arrow data should now be pretty accurate, but thrown items may need re-review.
		 * All the values were scaled back by 5 based on extensive ranged data fitting into the new formula.
		 * Iron, steel, and adamantite darts are also guesses.
		 */
		switch (ItemId.getById(arrowId)) {
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case BRONZE_ARROWS:
			case POISON_BRONZE_ARROWS:
				return 15;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
				return 17;
			case IRON_ARROWS:
			case POISON_IRON_ARROWS:
			case CROSSBOW_BOLTS:
			case POISON_CROSSBOW_BOLTS:
				return 20;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
				return 22;
			case STEEL_ARROWS:
			case POISON_STEEL_ARROWS:
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
				return 25;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
				return 27;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case MITHRIL_ARROWS:
			case POISON_MITHRIL_ARROWS:
			case OYSTER_PEARL_BOLTS:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
				return 30;
			case ADAMANTITE_ARROWS:
			case POISON_ADAMANTITE_ARROWS:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
				return 35;
			case RUNE_ARROWS:
			case POISON_RUNE_ARROWS:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
			case VOID_BOW:
				return 40;
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
			case DRAGON_ARROWS:
			case POISON_DRAGON_ARROWS:
			case DRAGON_BOLTS:
			case POISON_DRAGON_BOLTS:
				return 50;
			case BRONZE_SPEAR:
			case POISONED_BRONZE_SPEAR:
				return 29;
			case IRON_SPEAR:
			case POISONED_IRON_SPEAR:
				return 37;
			case STEEL_SPEAR:
			case POISONED_STEEL_SPEAR:
				return 46;
			case MITHRIL_SPEAR:
			case POISONED_MITHRIL_SPEAR:
				return 53;
			case ADAMANTITE_SPEAR:
			case POISONED_ADAMANTITE_SPEAR:
				return 61;
			case RUNE_SPEAR:
			case POISONED_RUNE_SPEAR:
				return 69;
			default:
				return 0;
		}
	}

	/**
	 * Returns an aim to associate with each ranged item
	 */
	private static int rangedAim(final int bowId) {
		/**
		 * We have limited pre-Fletching "aim" information for
		 * the shortbow, longbow, and crossbow in configXX.jag
		 * from 2001.
		 *
		 * We are using known information about how ranged weapon
		 * power scales by 5 per tier.
		 *
		 * We probably have a good guess for the base accuracy of
		 * darts.
		 *
		 * For spears and knives, we can only make wild guesses
		 * (people didn't throw enough of them). Rune spear was
		 * leaked.
		 */
		switch (ItemId.getById(bowId)) {
			case SHORTBOW:
				return 10;
			case CROSSBOW:
			case PHOENIX_CROSSBOW:
				return 12;
			case LONGBOW:
			case OAK_SHORTBOW:
				return 15;
			case WILLOW_SHORTBOW:
			case OAK_LONGBOW:
				return 20;
			case BRONZE_THROWING_DART:
			case POISONED_BRONZE_THROWING_DART:
			case MAPLE_SHORTBOW:
			case WILLOW_LONGBOW:
				return 25;
			case IRON_THROWING_DART:
			case POISONED_IRON_THROWING_DART:
			case BRONZE_THROWING_KNIFE:
			case POISONED_BRONZE_THROWING_KNIFE:
			case YEW_SHORTBOW:
			case MAPLE_LONGBOW:
				return 30;
			case STEEL_THROWING_DART:
			case POISONED_STEEL_THROWING_DART:
			case IRON_THROWING_KNIFE:
			case POISONED_IRON_THROWING_KNIFE:
			case MAGIC_SHORTBOW:
			case YEW_LONGBOW:
				return 35;
			case MITHRIL_THROWING_DART:
			case POISONED_MITHRIL_THROWING_DART:
			case BLACK_THROWING_KNIFE:
			case POISONED_BLACK_THROWING_KNIFE:
			case STEEL_THROWING_KNIFE:
			case POISONED_STEEL_THROWING_KNIFE:
			case MAGIC_LONGBOW:
			case DRAGON_CROSSBOW:
				return 40;
			case ADAMANTITE_THROWING_DART:
			case POISONED_ADAMANTITE_THROWING_DART:
			case MITHRIL_THROWING_KNIFE:
			case POISONED_MITHRIL_THROWING_KNIFE:
				return 45;
			case RUNE_THROWING_DART:
			case POISONED_RUNE_THROWING_DART:
			case ADAMANTITE_THROWING_KNIFE:
			case POISONED_ADAMANTITE_THROWING_KNIFE:
				return 50;
			case RUNE_THROWING_KNIFE:
			case POISONED_RUNE_THROWING_KNIFE:
				return 55;
			case BRONZE_SPEAR:
			case POISONED_BRONZE_SPEAR:
				return 25;
			case IRON_SPEAR:
			case POISONED_IRON_SPEAR:
				return 33;
			case STEEL_SPEAR:
			case POISONED_STEEL_SPEAR:
				return 41;
			case MITHRIL_SPEAR:
			case POISONED_MITHRIL_SPEAR:
				return 49;
			case ADAMANTITE_SPEAR:
			case POISONED_ADAMANTITE_SPEAR:
				return 57;
			case RUNE_SPEAR:
			case POISONED_RUNE_SPEAR:
				return 65;
			default:
				return 0;
		}
	}
}
