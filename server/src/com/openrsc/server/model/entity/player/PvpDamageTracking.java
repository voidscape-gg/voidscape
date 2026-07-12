package com.openrsc.server.model.entity.player;

/** Pure arithmetic shared by the direct PvP damage application paths. */
public final class PvpDamageTracking {
	private PvpDamageTracking() {
	}

	/** Returns the hitpoints actually removable by one nonnegative direct hit. */
	public static int actualDirectDamage(int rolledDamage, int currentHits) {
		return Math.min(Math.max(0, rolledDamage), Math.max(0, currentHits));
	}

	/** Adds one nonnegative contribution without allowing the persisted int contract to wrap. */
	public static int saturatedNonnegativeAdd(int currentTotal, int contribution) {
		final long safeCurrent = Math.max(0, currentTotal);
		final long safeContribution = Math.max(0, contribution);
		return (int)Math.min(Integer.MAX_VALUE, safeCurrent + safeContribution);
	}
}
