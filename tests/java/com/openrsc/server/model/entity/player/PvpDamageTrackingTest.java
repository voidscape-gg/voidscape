package com.openrsc.server.model.entity.player;

public final class PvpDamageTrackingTest {
	public static void main(String[] args) {
		capsTrackingAtHitpointsActuallyRemoved();
		saturatesAccumulatedTotals();
		System.out.println("PvP direct-damage arithmetic tests passed.");
	}

	private static void capsTrackingAtHitpointsActuallyRemoved() {
		assertEquals(7, PvpDamageTracking.actualDirectDamage(7, 20), "ordinary hit");
		assertEquals(3, PvpDamageTracking.actualDirectDamage(20, 3), "overkill");
		assertEquals(0, PvpDamageTracking.actualDirectDamage(0, 20), "zero-damage miss");
		assertEquals(0, PvpDamageTracking.actualDirectDamage(-3, 20), "negative damage");
		assertEquals(0, PvpDamageTracking.actualDirectDamage(7, 0), "already-dead target");
		assertEquals(0, PvpDamageTracking.actualDirectDamage(7, -2), "invalid hitpoints");
	}

	private static void saturatesAccumulatedTotals() {
		assertEquals(15, PvpDamageTracking.saturatedNonnegativeAdd(8, 7), "ordinary sum");
		assertEquals(7, PvpDamageTracking.saturatedNonnegativeAdd(-8, 7), "negative prior total");
		assertEquals(8, PvpDamageTracking.saturatedNonnegativeAdd(8, -7), "negative contribution");
		assertEquals(Integer.MAX_VALUE,
			PvpDamageTracking.saturatedNonnegativeAdd(Integer.MAX_VALUE - 2, 7),
			"overflow saturation");
		assertEquals(Integer.MAX_VALUE,
			PvpDamageTracking.saturatedNonnegativeAdd(Integer.MAX_VALUE, Integer.MAX_VALUE),
			"maximum saturation");
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
		}
	}
}
