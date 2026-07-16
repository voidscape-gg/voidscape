package com.voidscape.duelproof;

/** Immutable formula-relevant state captured immediately before one covered melee swing. */
public final class DuelProofMeleeInput {

	private final int attackerCombatStyle;
	private final int defenderCombatStyle;
	private final int attackerAttackLevel;
	private final int attackerStrengthLevel;
	private final int defenderDefenceLevel;
	private final int attackerAttackPrayerTier;
	private final int attackerStrengthPrayerTier;
	private final int defenderDefencePrayerTier;
	private final int attackerWeaponAimPoints;
	private final int attackerWeaponPowerPoints;
	private final int defenderArmourPoints;
	private final boolean attackCapeEligible;
	private final boolean strengthCapeEligible;
	private final boolean defenceCapeEligible;

	public DuelProofMeleeInput(final int attackerCombatStyle, final int defenderCombatStyle,
							   final int attackerAttackLevel, final int attackerStrengthLevel,
							   final int defenderDefenceLevel, final int attackerAttackPrayerTier,
							   final int attackerStrengthPrayerTier, final int defenderDefencePrayerTier,
							   final int attackerWeaponAimPoints, final int attackerWeaponPowerPoints,
							   final int defenderArmourPoints, final boolean attackCapeEligible,
							   final boolean strengthCapeEligible, final boolean defenceCapeEligible) {
		requireStyle(attackerCombatStyle, "attackerCombatStyle");
		requireStyle(defenderCombatStyle, "defenderCombatStyle");
		requirePrayerTier(attackerAttackPrayerTier, "attackerAttackPrayerTier");
		requirePrayerTier(attackerStrengthPrayerTier, "attackerStrengthPrayerTier");
		requirePrayerTier(defenderDefencePrayerTier, "defenderDefencePrayerTier");
		this.attackerCombatStyle = attackerCombatStyle;
		this.defenderCombatStyle = defenderCombatStyle;
		this.attackerAttackLevel = attackerAttackLevel;
		this.attackerStrengthLevel = attackerStrengthLevel;
		this.defenderDefenceLevel = defenderDefenceLevel;
		this.attackerAttackPrayerTier = attackerAttackPrayerTier;
		this.attackerStrengthPrayerTier = attackerStrengthPrayerTier;
		this.defenderDefencePrayerTier = defenderDefencePrayerTier;
		this.attackerWeaponAimPoints = attackerWeaponAimPoints;
		this.attackerWeaponPowerPoints = attackerWeaponPowerPoints;
		this.defenderArmourPoints = defenderArmourPoints;
		this.attackCapeEligible = attackCapeEligible;
		this.strengthCapeEligible = strengthCapeEligible;
		this.defenceCapeEligible = defenceCapeEligible;
	}

	public int getAttackerCombatStyle() { return attackerCombatStyle; }
	public int getDefenderCombatStyle() { return defenderCombatStyle; }
	public int getAttackerAttackLevel() { return attackerAttackLevel; }
	public int getAttackerStrengthLevel() { return attackerStrengthLevel; }
	public int getDefenderDefenceLevel() { return defenderDefenceLevel; }
	public int getAttackerAttackPrayerTier() { return attackerAttackPrayerTier; }
	public int getAttackerStrengthPrayerTier() { return attackerStrengthPrayerTier; }
	public int getDefenderDefencePrayerTier() { return defenderDefencePrayerTier; }
	public int getAttackerWeaponAimPoints() { return attackerWeaponAimPoints; }
	public int getAttackerWeaponPowerPoints() { return attackerWeaponPowerPoints; }
	public int getDefenderArmourPoints() { return defenderArmourPoints; }
	public boolean isAttackCapeEligible() { return attackCapeEligible; }
	public boolean isStrengthCapeEligible() { return strengthCapeEligible; }
	public boolean isDefenceCapeEligible() { return defenceCapeEligible; }

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DuelProofMeleeInput)) {
			return false;
		}
		final DuelProofMeleeInput input = (DuelProofMeleeInput) other;
		return attackerCombatStyle == input.attackerCombatStyle
			&& defenderCombatStyle == input.defenderCombatStyle
			&& attackerAttackLevel == input.attackerAttackLevel
			&& attackerStrengthLevel == input.attackerStrengthLevel
			&& defenderDefenceLevel == input.defenderDefenceLevel
			&& attackerAttackPrayerTier == input.attackerAttackPrayerTier
			&& attackerStrengthPrayerTier == input.attackerStrengthPrayerTier
			&& defenderDefencePrayerTier == input.defenderDefencePrayerTier
			&& attackerWeaponAimPoints == input.attackerWeaponAimPoints
			&& attackerWeaponPowerPoints == input.attackerWeaponPowerPoints
			&& defenderArmourPoints == input.defenderArmourPoints
			&& attackCapeEligible == input.attackCapeEligible
			&& strengthCapeEligible == input.strengthCapeEligible
			&& defenceCapeEligible == input.defenceCapeEligible;
	}

	@Override
	public int hashCode() {
		int result = attackerCombatStyle;
		result = 31 * result + defenderCombatStyle;
		result = 31 * result + attackerAttackLevel;
		result = 31 * result + attackerStrengthLevel;
		result = 31 * result + defenderDefenceLevel;
		result = 31 * result + attackerAttackPrayerTier;
		result = 31 * result + attackerStrengthPrayerTier;
		result = 31 * result + defenderDefencePrayerTier;
		result = 31 * result + attackerWeaponAimPoints;
		result = 31 * result + attackerWeaponPowerPoints;
		result = 31 * result + defenderArmourPoints;
		result = 31 * result + (attackCapeEligible ? 1 : 0);
		result = 31 * result + (strengthCapeEligible ? 1 : 0);
		result = 31 * result + (defenceCapeEligible ? 1 : 0);
		return result;
	}

	private static void requireStyle(final int value, final String label) {
		if (value < DuelProofSpec.COMBAT_STYLE_CONTROLLED
			|| value > DuelProofSpec.COMBAT_STYLE_DEFENSIVE) {
			throw new IllegalArgumentException(label + " is outside the supported range");
		}
	}

	private static void requirePrayerTier(final int value, final String label) {
		if (value < DuelProofSpec.PRAYER_TIER_NONE || value > DuelProofSpec.PRAYER_TIER_HIGH) {
			throw new IllegalArgumentException(label + " is outside the supported range");
		}
	}
}
