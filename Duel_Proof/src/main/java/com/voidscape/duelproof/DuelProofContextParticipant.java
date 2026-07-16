package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable participant state locked into a verified v3 duel context. */
public final class DuelProofContextParticipant {

	private final int ordinal;
	private final int playerId;
	private final String username;
	private final int combatLevel;
	private final int currentAttack;
	private final int maximumAttack;
	private final int currentDefence;
	private final int maximumDefence;
	private final int currentStrength;
	private final int maximumStrength;
	private final int currentHits;
	private final int maximumHits;
	private final int currentPrayer;
	private final int maximumPrayer;
	private final int initialCombatStyle;
	private final int weaponAimPoints;
	private final int weaponPowerPoints;
	private final int armourPoints;
	private final boolean attackCapeEligible;
	private final boolean strengthCapeEligible;
	private final boolean defenceCapeEligible;
	private final int prayerMask;
	private final List<DuelProofContextItem> equipment;
	private final boolean recoilEquipped;
	private final int recoilRemaining;
	private final List<DuelProofContextItem> stakes;

	DuelProofContextParticipant(final int ordinal, final int playerId, final String username,
								final int combatLevel,
								final int currentAttack, final int maximumAttack,
								final int currentDefence, final int maximumDefence,
								final int currentStrength, final int maximumStrength,
								final int currentHits, final int maximumHits,
								final int currentPrayer, final int maximumPrayer,
								final int initialCombatStyle, final int weaponAimPoints,
								final int weaponPowerPoints, final int armourPoints,
								final boolean attackCapeEligible,
								final boolean strengthCapeEligible,
								final boolean defenceCapeEligible,
								final int prayerMask,
								final List<DuelProofContextItem> equipment,
								final boolean recoilEquipped, final int recoilRemaining,
								final List<DuelProofContextItem> stakes) {
		this.ordinal = ordinal;
		this.playerId = playerId;
		this.username = username;
		this.combatLevel = combatLevel;
		this.currentAttack = currentAttack;
		this.maximumAttack = maximumAttack;
		this.currentDefence = currentDefence;
		this.maximumDefence = maximumDefence;
		this.currentStrength = currentStrength;
		this.maximumStrength = maximumStrength;
		this.currentHits = currentHits;
		this.maximumHits = maximumHits;
		this.currentPrayer = currentPrayer;
		this.maximumPrayer = maximumPrayer;
		this.initialCombatStyle = initialCombatStyle;
		this.weaponAimPoints = weaponAimPoints;
		this.weaponPowerPoints = weaponPowerPoints;
		this.armourPoints = armourPoints;
		this.attackCapeEligible = attackCapeEligible;
		this.strengthCapeEligible = strengthCapeEligible;
		this.defenceCapeEligible = defenceCapeEligible;
		this.prayerMask = prayerMask;
		this.equipment = immutableCopy(equipment);
		this.recoilEquipped = recoilEquipped;
		this.recoilRemaining = recoilRemaining;
		this.stakes = immutableCopy(stakes);
	}

	public int getOrdinal() { return ordinal; }
	public int getPlayerId() { return playerId; }
	public String getUsername() { return username; }
	public int getCombatLevel() { return combatLevel; }
	public int getCurrentAttack() { return currentAttack; }
	public int getMaximumAttack() { return maximumAttack; }
	public int getCurrentDefence() { return currentDefence; }
	public int getMaximumDefence() { return maximumDefence; }
	public int getCurrentStrength() { return currentStrength; }
	public int getMaximumStrength() { return maximumStrength; }
	public int getCurrentHits() { return currentHits; }
	public int getMaximumHits() { return maximumHits; }
	public int getCurrentPrayer() { return currentPrayer; }
	public int getMaximumPrayer() { return maximumPrayer; }
	public int getInitialCombatStyle() { return initialCombatStyle; }
	public int getWeaponAimPoints() { return weaponAimPoints; }
	public int getWeaponPowerPoints() { return weaponPowerPoints; }
	public int getArmourPoints() { return armourPoints; }
	public boolean isAttackCapeEligible() { return attackCapeEligible; }
	public boolean isStrengthCapeEligible() { return strengthCapeEligible; }
	public boolean isDefenceCapeEligible() { return defenceCapeEligible; }
	public int getPrayerMask() { return prayerMask; }
	public List<DuelProofContextItem> getEquipment() { return equipment; }
	public boolean isRecoilEquipped() { return recoilEquipped; }
	public int getRecoilRemaining() { return recoilRemaining; }
	public List<DuelProofContextItem> getStakes() { return stakes; }

	private static List<DuelProofContextItem> immutableCopy(
		final List<DuelProofContextItem> items) {
		return Collections.unmodifiableList(new ArrayList<DuelProofContextItem>(items));
	}
}
