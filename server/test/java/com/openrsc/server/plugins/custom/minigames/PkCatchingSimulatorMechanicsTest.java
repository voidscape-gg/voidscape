package com.openrsc.server.plugins.custom.minigames;

/** Behavioral contract for the simulator's tick-exact attempt clock. */
public final class PkCatchingSimulatorMechanicsTest {
	public static void main(String[] args) {
		for (int reattackTicks : new int[] {0, 1, 5, 7}) {
			exclusiveAttemptWindow(reattackTicks);
		}
		missIsIdempotentAndRecoveryIsFresh();
		System.out.println("PK Catching Simulator attempt-clock tests passed");
	}

	private static void exclusiveAttemptWindow(int reattackTicks) {
		long releaseTick = 100;
		long openTick = releaseTick + reattackTicks;
		long missTick = openTick + 12;

		PkCatchingSimulator.AttemptClock before = released(releaseTick, reattackTicks);
		if (reattackTicks > 0) {
			assertEquals(PkCatchingSimulator.AttemptPhase.REATTACK_LOCK,
				before.getPhase(), "phase before immunity expires");
			assertFalse(before.canScoreCatch(openTick - 1), "immunity tick cannot score");
		}

		for (long tick = openTick; tick < missTick; tick++) {
			PkCatchingSimulator.AttemptClock clock = released(releaseTick, reattackTicks);
			clock.advance(tick);
			assertTrue(clock.canScoreCatch(tick), "legal catch tick " + tick);
			clock.enterCombat();
			assertEquals(PkCatchingSimulator.AttemptPhase.COMBAT,
				clock.getPhase(), "a catch closes its attempt");
		}

		PkCatchingSimulator.AttemptClock deadline = released(releaseTick, reattackTicks);
		assertEquals(PkCatchingSimulator.AttemptTransition.MISSED,
			deadline.advance(missTick), "deadline attack expires before scoring");
		assertFalse(deadline.canScoreCatch(missTick), "deadline is exclusive");
	}

	private static void missIsIdempotentAndRecoveryIsFresh() {
		PkCatchingSimulator.AttemptClock clock = released(200, 5);
		long missTick = clock.getDeadlineTick();

		assertEquals(PkCatchingSimulator.AttemptTransition.MISSED,
			clock.advance(missTick), "first deadline observation records a miss");
		assertEquals(PkCatchingSimulator.AttemptTransition.NONE,
			clock.advance(missTick), "same-tick callback cannot record a second miss");
		assertFalse(clock.targetMayMove(), "target is stopped on miss tick");
		assertEquals(PkCatchingSimulator.AttemptTransition.NONE,
			clock.advance(missTick + 1), "second recovery tick remains closed");
		assertFalse(clock.targetMayMove(), "target is stopped on second recovery tick");

		assertEquals(PkCatchingSimulator.AttemptTransition.RESUMED,
			clock.advance(missTick + 2), "fresh packet may resume before player event");
		assertTrue(clock.canScoreCatch(missTick + 2), "resume tick is immediately actionable");
		assertTrue(clock.targetMayMove(), "target may move again on resume tick");
		assertEquals(missTick + 14, clock.getDeadlineTick(), "fresh window has twelve ticks");
	}

	private static PkCatchingSimulator.AttemptClock released(long tick, int reattackTicks) {
		PkCatchingSimulator.AttemptClock clock = new PkCatchingSimulator.AttemptClock();
		clock.enterCombat();
		clock.release(tick, reattackTicks);
		return clock;
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		if (value) throw new AssertionError(message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
