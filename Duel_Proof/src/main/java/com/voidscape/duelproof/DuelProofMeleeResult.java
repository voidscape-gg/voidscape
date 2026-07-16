package com.voidscape.duelproof;

/** Pure classic-melee result plus cape side effects needed by the live server. */
public final class DuelProofMeleeResult {

	private final boolean initialHit;
	private final boolean hit;
	private final int damage;
	private final int damageBeforeDefenceCape;
	private final int blockedDamage;
	private final int attackCapeRolls;
	private final int attackCapeRerolls;
	private final boolean attackCapePreventedZero;
	private final boolean defenceCapeRolled;
	private final boolean defenceCapeActivated;
	private final boolean defenceCapeApplied;
	private final boolean strengthCapeRolled;
	private final boolean strengthCapeActivated;

	DuelProofMeleeResult(final boolean initialHit, final boolean hit, final int damage,
						 final int damageBeforeDefenceCape, final int blockedDamage,
						 final int attackCapeRolls, final int attackCapeRerolls,
						 final boolean attackCapePreventedZero, final boolean defenceCapeRolled,
						 final boolean defenceCapeActivated, final boolean defenceCapeApplied,
						 final boolean strengthCapeRolled, final boolean strengthCapeActivated) {
		this.initialHit = initialHit;
		this.hit = hit;
		this.damage = damage;
		this.damageBeforeDefenceCape = damageBeforeDefenceCape;
		this.blockedDamage = blockedDamage;
		this.attackCapeRolls = attackCapeRolls;
		this.attackCapeRerolls = attackCapeRerolls;
		this.attackCapePreventedZero = attackCapePreventedZero;
		this.defenceCapeRolled = defenceCapeRolled;
		this.defenceCapeActivated = defenceCapeActivated;
		this.defenceCapeApplied = defenceCapeApplied;
		this.strengthCapeRolled = strengthCapeRolled;
		this.strengthCapeActivated = strengthCapeActivated;
	}

	public boolean isInitialHit() { return initialHit; }
	public boolean isHit() { return hit; }
	public int getDamage() { return damage; }
	public int getDamageBeforeDefenceCape() { return damageBeforeDefenceCape; }
	public int getBlockedDamage() { return blockedDamage; }
	public int getAttackCapeRolls() { return attackCapeRolls; }
	public int getAttackCapeRerolls() { return attackCapeRerolls; }
	public boolean isAttackCapePreventedZero() { return attackCapePreventedZero; }
	public boolean isDefenceCapeRolled() { return defenceCapeRolled; }
	public boolean isDefenceCapeActivated() { return defenceCapeActivated; }
	public boolean isDefenceCapeApplied() { return defenceCapeApplied; }
	public boolean isStrengthCapeRolled() { return strengthCapeRolled; }
	public boolean isStrengthCapeActivated() { return strengthCapeActivated; }

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DuelProofMeleeResult)) {
			return false;
		}
		final DuelProofMeleeResult result = (DuelProofMeleeResult) other;
		return initialHit == result.initialHit && hit == result.hit && damage == result.damage
			&& damageBeforeDefenceCape == result.damageBeforeDefenceCape
			&& blockedDamage == result.blockedDamage && attackCapeRolls == result.attackCapeRolls
			&& attackCapeRerolls == result.attackCapeRerolls
			&& attackCapePreventedZero == result.attackCapePreventedZero
			&& defenceCapeRolled == result.defenceCapeRolled
			&& defenceCapeActivated == result.defenceCapeActivated
			&& defenceCapeApplied == result.defenceCapeApplied
			&& strengthCapeRolled == result.strengthCapeRolled
			&& strengthCapeActivated == result.strengthCapeActivated;
	}

	@Override
	public int hashCode() {
		int result = initialHit ? 1 : 0;
		result = 31 * result + (hit ? 1 : 0);
		result = 31 * result + damage;
		result = 31 * result + damageBeforeDefenceCape;
		result = 31 * result + blockedDamage;
		result = 31 * result + attackCapeRolls;
		result = 31 * result + attackCapeRerolls;
		result = 31 * result + (attackCapePreventedZero ? 1 : 0);
		result = 31 * result + (defenceCapeRolled ? 1 : 0);
		result = 31 * result + (defenceCapeActivated ? 1 : 0);
		result = 31 * result + (defenceCapeApplied ? 1 : 0);
		result = 31 * result + (strengthCapeRolled ? 1 : 0);
		result = 31 * result + (strengthCapeActivated ? 1 : 0);
		return result;
	}
}
