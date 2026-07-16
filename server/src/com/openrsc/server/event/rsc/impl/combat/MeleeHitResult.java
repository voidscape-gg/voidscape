package com.openrsc.server.event.rsc.impl.combat;

/**
 * The resolved accuracy and damage for one melee swing.
 *
 * Keeping accuracy separate from damage preserves the difference between a miss
 * and a successful zero-damage roll without performing another RNG call.
 */
public final class MeleeHitResult {
	private final boolean hit;
	private final int damage;

	public MeleeHitResult(final boolean hit, final int damage) {
		this.hit = hit;
		this.damage = damage;
	}

	public boolean isHit() {
		return hit;
	}

	public int getDamage() {
		return damage;
	}
}
