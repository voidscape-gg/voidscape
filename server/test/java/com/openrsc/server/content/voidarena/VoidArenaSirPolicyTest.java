package com.openrsc.server.content.voidarena;

public final class VoidArenaSirPolicyTest {
	public static void main(String[] args) {
		fishAreFiniteAtTwentyOne();
		fireBlastAllowsCastOneHundredAndDeniesOneHundredOne();
		strengthPotionHasExactlyFourDoses();
		prayerDrainsFromNinetyNineLevelsToZero();
		prayerMultiplierStopsAtZero();
		castDelayRoundsUpAndNeverDropsBelowOneTick();
		retreatRequiresThreeOpponentHits();
		sharedReattackOpensAtTheExactLaterBoundary();
		mainActionSelectionUsesOneStablePriorityOrder();
		magicExhaustionFallsBackToMeleeInsteadOfKiting();
		System.out.println("Void Arena Sir Charles policy tests passed");
	}

	private static void fishAreFiniteAtTwentyOne() {
		int fishRemaining = VoidArenaSirPolicy.FISH_CAPACITY;
		assertEquals(21, fishRemaining, "initial fish count");
		for (int eaten = 0; eaten < 21; eaten++) {
			assertTrue(VoidArenaSirPolicy.canEatFish(fishRemaining),
				"fish " + (eaten + 1) + " must be available");
			fishRemaining = VoidArenaSirPolicy.fishRemainingAfterEat(fishRemaining);
		}
		assertEquals(0, fishRemaining, "all twenty-one fish consumed");
		assertFalse(VoidArenaSirPolicy.canEatFish(fishRemaining), "twenty-second fish denied");
		assertEquals(0, VoidArenaSirPolicy.fishRemainingAfterEat(fishRemaining),
			"empty fish state remains at zero");
	}

	private static void fireBlastAllowsCastOneHundredAndDeniesOneHundredOne() {
		int castsUsed = 0;
		for (int castNumber = 1; castNumber <= 100; castNumber++) {
			assertTrue(VoidArenaSirPolicy.canCastFireBlast(castsUsed),
				"cast " + castNumber + " must be allowed");
			castsUsed = VoidArenaSirPolicy.castsUsedAfterFireBlast(castsUsed);
		}
		assertEquals(100, castsUsed, "one hundred casts recorded");
		assertFalse(VoidArenaSirPolicy.canCastFireBlast(castsUsed),
			"cast one hundred one denied");
		assertEquals(100, VoidArenaSirPolicy.castsUsedAfterFireBlast(castsUsed),
			"exhausted cast state remains capped");
	}

	private static void strengthPotionHasExactlyFourDoses() {
		int dosesRemaining = VoidArenaSirPolicy.STRENGTH_POTION_DOSE_CAPACITY;
		assertEquals(4, dosesRemaining, "initial potion doses");
		for (int dose = 1; dose <= 4; dose++) {
			assertTrue(VoidArenaSirPolicy.canDrinkStrengthPotion(dosesRemaining),
				"potion dose " + dose + " must be available");
			dosesRemaining = VoidArenaSirPolicy.potionDosesAfterDrink(dosesRemaining);
		}
		assertEquals(0, dosesRemaining, "all four potion doses consumed");
		assertFalse(VoidArenaSirPolicy.canDrinkStrengthPotion(dosesRemaining),
			"fifth potion dose denied");
	}

	private static void prayerDrainsFromNinetyNineLevelsToZero() {
		int suppliedDrainPerTick = 137;
		int points = VoidArenaSirPolicy.INITIAL_PRAYER_STATE_POINTS;
		assertEquals(99 * 120, points, "initial prayer state points");
		int ticks = 0;
		while (VoidArenaSirPolicy.prayerActive(points)) {
			points = VoidArenaSirPolicy.prayerPointsAfterDrain(points, suppliedDrainPerTick);
			ticks++;
		}
		assertEquals(ceilDiv(99 * 120, suppliedDrainPerTick), ticks,
			"supplied drain rate controls depletion tick");
		assertEquals(0, points, "prayer clamps exactly at zero");
		assertEquals(0, VoidArenaSirPolicy.prayerPointsAfterDrain(points, suppliedDrainPerTick),
			"depleted prayer cannot become negative");
	}

	private static void prayerMultiplierStopsAtZero() {
		assertEquals(1.15D, VoidArenaSirPolicy.prayerMultiplier(1, 1.15D),
			"last prayer state point keeps active multiplier");
		assertEquals(1.0D, VoidArenaSirPolicy.prayerMultiplier(0, 1.15D),
			"zero prayer removes multiplier");
		assertEquals(1.0D, VoidArenaSirPolicy.prayerMultiplier(-1, 1.15D),
			"invalid negative prayer cannot grant multiplier");
	}

	private static void castDelayRoundsUpAndNeverDropsBelowOneTick() {
		assertEquals(1L, VoidArenaSirPolicy.castDelayTicks(0, 640),
			"rapid cast still consumes one tick");
		assertEquals(1L, VoidArenaSirPolicy.castDelayTicks(1, 640),
			"sub-tick delay rounds up to one");
		assertEquals(1L, VoidArenaSirPolicy.castDelayTicks(640, 640),
			"exact tick delay");
		assertEquals(2L, VoidArenaSirPolicy.castDelayTicks(641, 640),
			"one millisecond over rounds up");
		assertEquals(3L, VoidArenaSirPolicy.castDelayTicks(1_281, 640),
			"multi-tick delay rounds up");
		assertEquals(1L, VoidArenaSirPolicy.castDelayTicks(-1, 0),
			"invalid configuration is safely clamped");
	}

	private static void retreatRequiresThreeOpponentHits() {
		assertFalse(VoidArenaSirPolicy.retreatHitGatePassed(0), "zero-hit retreat denied");
		assertFalse(VoidArenaSirPolicy.retreatHitGatePassed(2), "two-hit retreat denied");
		assertTrue(VoidArenaSirPolicy.retreatHitGatePassed(3), "three-hit retreat allowed");
		assertTrue(VoidArenaSirPolicy.retreatHitGatePassed(4), "later retreat remains allowed");
	}

	private static void sharedReattackOpensAtTheExactLaterBoundary() {
		long sirRetreatTick = 100;
		long playerRetreatTick = 102;
		long delay = 3;
		assertFalse(VoidArenaSirPolicy.sharedReattackReady(104, sirRetreatTick,
			playerRetreatTick, delay), "shared reattack blocked one tick early");
		assertTrue(VoidArenaSirPolicy.sharedReattackReady(105, sirRetreatTick,
			playerRetreatTick, delay), "shared reattack opens at exact later boundary");
		assertTrue(VoidArenaSirPolicy.sharedReattackReady(106, sirRetreatTick,
			playerRetreatTick, delay), "shared reattack remains open after boundary");
		assertTrue(VoidArenaSirPolicy.sharedReattackReady(100, 0, 0, delay),
			"no recorded retreats impose no delay");
	}

	private static void mainActionSelectionUsesOneStablePriorityOrder() {
		assertEquals(VoidArenaSirPolicy.MainAction.RETREAT_TO_EAT,
			select(0, true, true, true, true, true, true), "retreat priority");
		assertEquals(VoidArenaSirPolicy.MainAction.EAT,
			select(0, false, true, true, true, true, true), "eat priority");
		assertEquals(VoidArenaSirPolicy.MainAction.REPOT_STRENGTH,
			select(0, false, false, true, true, true, true), "repot priority");
		assertEquals(VoidArenaSirPolicy.MainAction.CAST_FIRE_BLAST,
			select(0, false, false, false, true, true, true), "cast priority");
		assertEquals(VoidArenaSirPolicy.MainAction.KITE_FOR_MAGIC,
			select(0, false, false, false, false, true, true), "kite priority");
		assertEquals(VoidArenaSirPolicy.MainAction.MELEE_PRESSURE,
			select(0, false, false, false, false, false, true), "melee priority");
		assertEquals(VoidArenaSirPolicy.MainAction.NONE,
			select(0, false, false, false, false, false, false), "idle fallback");
	}

	private static void magicExhaustionFallsBackToMeleeInsteadOfKiting() {
		assertEquals(VoidArenaSirPolicy.MainAction.KITE_FOR_MAGIC,
			select(99, false, false, false, false, true, true),
			"one remaining cast permits tactical kite");
		assertEquals(VoidArenaSirPolicy.MainAction.MELEE_PRESSURE,
			select(100, false, false, false, true, true, true),
			"exhausted magic suppresses cast and kite");
	}

	private static VoidArenaSirPolicy.MainAction select(int castsUsed,
			boolean retreat, boolean eat, boolean repot, boolean cast, boolean kite,
			boolean melee) {
		return VoidArenaSirPolicy.selectMainAction(castsUsed, retreat, eat, repot,
			cast, kite, melee);
	}

	private static int ceilDiv(int numerator, int denominator) {
		return numerator / denominator + (numerator % denominator == 0 ? 0 : 1);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(double expected, double actual, String message) {
		if (Double.compare(expected, actual) != 0) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private VoidArenaSirPolicyTest() {
	}
}
