package com.openrsc.server.content.voidarena;

/** Deterministic finite-resource and action-order rules for Sir Charles. */
final class VoidArenaSirPolicy {
	static final int FISH_CAPACITY = 21;
	static final int FIRE_BLAST_CAST_CAPACITY = 100;
	static final int STRENGTH_POTION_DOSE_CAPACITY = 4;
	static final int PRAYER_LEVEL = 99;
	static final int PRAYER_STATE_POINTS_PER_LEVEL = 120;
	static final int INITIAL_PRAYER_STATE_POINTS = PRAYER_LEVEL * PRAYER_STATE_POINTS_PER_LEVEL;
	static final int RETREAT_HIT_GATE = 3;

	enum MainAction {
		RETREAT_TO_EAT,
		EAT,
		REPOT_STRENGTH,
		CAST_FIRE_BLAST,
		KITE_FOR_MAGIC,
		MELEE_PRESSURE,
		NONE
	}

	static boolean canEatFish(int fishRemaining) {
		return fishRemaining > 0 && fishRemaining <= FISH_CAPACITY;
	}

	static int fishRemainingAfterEat(int fishRemaining) {
		return canEatFish(fishRemaining) ? fishRemaining - 1 : Math.max(0, fishRemaining);
	}

	static boolean canCastFireBlast(int castsUsed) {
		return castsUsed >= 0 && castsUsed < FIRE_BLAST_CAST_CAPACITY;
	}

	static int castsUsedAfterFireBlast(int castsUsed) {
		return canCastFireBlast(castsUsed) ? castsUsed + 1 : Math.max(0, castsUsed);
	}

	static boolean canDrinkStrengthPotion(int dosesRemaining) {
		return dosesRemaining > 0 && dosesRemaining <= STRENGTH_POTION_DOSE_CAPACITY;
	}

	static int potionDosesAfterDrink(int dosesRemaining) {
		return canDrinkStrengthPotion(dosesRemaining) ? dosesRemaining - 1
			: Math.max(0, dosesRemaining);
	}

	static int prayerPointsAfterDrain(int prayerStatePoints, int drainPerTick) {
		if (drainPerTick < 0) {
			throw new IllegalArgumentException("Prayer drain must not be negative");
		}
		long remaining = (long) Math.max(0, prayerStatePoints) - drainPerTick;
		return (int) Math.max(0L, remaining);
	}

	static boolean prayerActive(int prayerStatePoints) {
		return prayerStatePoints > 0;
	}

	static double prayerMultiplier(int prayerStatePoints, double activeMultiplier) {
		return prayerActive(prayerStatePoints) ? activeMultiplier : 1.0D;
	}

	static long castDelayTicks(long castDelayMs, long gameTickMs) {
		long delay = Math.max(0L, castDelayMs);
		long tick = Math.max(1L, gameTickMs);
		long ticks = delay / tick;
		if (delay % tick != 0) {
			ticks++;
		}
		return Math.max(1L, ticks);
	}

	static boolean retreatHitGatePassed(int opponentHitsMade) {
		return opponentHitsMade >= RETREAT_HIT_GATE;
	}

	static boolean sharedReattackReady(long currentTick, long sirRetreatTick,
									 long playerRetreatTick, long reattackDelayTicks) {
		long delay = Math.max(0L, reattackDelayTicks);
		return participantReattackReady(currentTick, sirRetreatTick, delay)
			&& participantReattackReady(currentTick, playerRetreatTick, delay);
	}

	static MainAction selectMainAction(int castsUsed, boolean retreatToEatReady,
									 boolean eatReady, boolean repotReady, boolean castReady,
									 boolean kiteReady, boolean meleeReady) {
		if (retreatToEatReady) {
			return MainAction.RETREAT_TO_EAT;
		}
		if (eatReady) {
			return MainAction.EAT;
		}
		if (repotReady) {
			return MainAction.REPOT_STRENGTH;
		}
		boolean magicRemaining = canCastFireBlast(castsUsed);
		if (magicRemaining && castReady) {
			return MainAction.CAST_FIRE_BLAST;
		}
		if (magicRemaining && kiteReady) {
			return MainAction.KITE_FOR_MAGIC;
		}
		if (meleeReady) {
			return MainAction.MELEE_PRESSURE;
		}
		return MainAction.NONE;
	}

	private static boolean participantReattackReady(long currentTick, long retreatTick,
														 long reattackDelayTicks) {
		return retreatTick <= 0L || (currentTick >= retreatTick
			&& currentTick - retreatTick >= reattackDelayTicks);
	}

	private VoidArenaSirPolicy() {
	}
}
