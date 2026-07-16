package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Stateful v1 replay for the committed starter and every covered classic-melee swing.
 * Momentum belongs to this replay rather than mutable server combat attributes.
 */
public final class DuelProofMeleeReplay {

	private static final double UNIT_53_DIVISOR = 9007199254740992.0;
	private static final double ARMOUR_ACCURACY_SCALE = 0.60D;
	private static final double PHYSICAL_MITIGATION_DIVISOR = 1200.0D;
	private static final double PHYSICAL_MITIGATION_CAP = 0.24D;
	private static final double MOMENTUM_BIG_HIT_RATIO = 0.68D;
	private static final int CAPE_BUCKETS = 99;
	private static final int CAPE_ACTIVATION_BUCKETS = 35;

	private final DuelProofRng random;
	private final boolean[] momentum = new boolean[2];
	private final List<DuelProofMeleeSwing> swings = new ArrayList<DuelProofMeleeSwing>();
	private DuelProofDraw starterDraw;
	private int starterOrdinal = -1;
	private boolean destroyed;

	public DuelProofMeleeReplay(final byte[] masterSeed) {
		random = new DuelProofRng(masterSeed);
	}

	/**
	 * Consumes and records the first committed draw, then selects the lower combat level.
	 * The draw selects the starter only when both committed combat levels are equal.
	 * May be called exactly once.
	 */
	public int chooseStarterOrdinal(final int firstCombatLevel,
								final int secondCombatLevel) {
		requireAvailable();
		if (starterOrdinal != -1 || random.getCandidateDrawCount() != 0 || !swings.isEmpty()) {
			throw new IllegalStateException("duel starter has already been chosen");
		}
		if (firstCombatLevel <= 0 || secondCombatLevel <= 0) {
			throw new IllegalArgumentException("combat levels must be positive");
		}
		starterDraw = drawInt(DuelProofSpec.DRAW_STARTER, 2, null);
		if (firstCombatLevel < secondCombatLevel) {
			starterOrdinal = 0;
		} else if (secondCombatLevel < firstCombatLevel) {
			starterOrdinal = 1;
		} else {
			starterOrdinal = (int) starterDraw.getValue();
		}
		return starterOrdinal;
	}

	/** Resolves, records, and returns the next alternating covered swing. */
	public DuelProofMeleeSwing resolveSwing(final int actorOrdinal,
										 final DuelProofMeleeInput input) {
		requireAvailable();
		if (starterOrdinal == -1) {
			throw new IllegalStateException("duel starter must be chosen before resolving swings");
		}
		if (input == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		final int expectedActor = swings.size() % 2 == 0 ? starterOrdinal : 1 - starterOrdinal;
		if (actorOrdinal != expectedActor) {
			throw new IllegalArgumentException("melee swing actor is outside committed turn order");
		}

		final long candidateStart = random.getCandidateDrawCount();
		final List<DuelProofDraw> draws = new ArrayList<DuelProofDraw>();
		final boolean momentumBefore = momentum[actorOrdinal];
		final DuelProofMeleeResult result = resolve(input, momentumBefore, draws);
		final boolean momentumAfter = nextMomentum(input, result);
		momentum[actorOrdinal] = momentumAfter;
		final long candidateEnd = random.getCandidateDrawCount();
		final DuelProofMeleeSwing swing = new DuelProofMeleeSwing(swings.size() + 1,
			actorOrdinal, input, result, momentumBefore, momentumAfter,
			candidateStart, candidateEnd, draws);
		swings.add(swing);
		return swing;
	}

	public int getStarterOrdinal() {
		requireAvailable();
		requireStarterChosen();
		return starterOrdinal;
	}

	public DuelProofDraw getStarterDraw() {
		requireAvailable();
		requireStarterChosen();
		return starterDraw;
	}

	public long getCandidateDrawCount() {
		requireAvailable();
		return random.getCandidateDrawCount();
	}

	/** Returns an immutable defensive snapshot in global draw order. */
	public List<DuelProofMeleeSwing> getSwings() {
		requireAvailable();
		return Collections.unmodifiableList(new ArrayList<DuelProofMeleeSwing>(swings));
	}

	/**
	 * Replays a transcript prefix and rejects changed order, inputs, results, or draw boundaries.
	 * A valid prefix is not evidence that a completed duel has no omitted trailing swings; callers
	 * must separately bind the terminal swing count and settlement outcome.
	 */
	public static boolean verifiesPrefix(final byte[] masterSeed,
									 final int firstCombatLevel,
									 final int secondCombatLevel,
									 final int expectedStarterOrdinal,
									 final List<DuelProofMeleeSwing> expectedSwings) {
		if (masterSeed == null || expectedSwings == null) {
			return false;
		}
		DuelProofMeleeReplay replay = null;
		try {
			replay = new DuelProofMeleeReplay(masterSeed);
			if (replay.chooseStarterOrdinal(firstCombatLevel, secondCombatLevel)
				!= expectedStarterOrdinal) {
				return false;
			}
			for (final DuelProofMeleeSwing expected : expectedSwings) {
				if (expected == null || !expected.equals(
					replay.resolveSwing(expected.getActorOrdinal(), expected.getInput()))) {
					return false;
				}
			}
			return replay.getSwings().equals(expectedSwings);
		} catch (final RuntimeException invalid) {
			return false;
		} finally {
			if (replay != null) {
				replay.destroy();
			}
		}
	}

	/** Wipes the stream key and discards the in-memory transcript. */
	public void destroy() {
		if (destroyed) {
			return;
		}
		random.destroy();
		Arrays.fill(momentum, false);
		swings.clear();
		starterDraw = null;
		starterOrdinal = -1;
		destroyed = true;
	}

	private DuelProofMeleeResult resolve(final DuelProofMeleeInput input,
										 final boolean hasMomentum,
										 final List<DuelProofDraw> draws) {
		final double accuracy = meleeAccuracy(input);
		final double defence = meleeDefence(input);
		boolean hit = rollAccuracy(DuelProofSpec.DRAW_ACCURACY, accuracy, defence, draws);
		final boolean initialHit = hit;
		int attackCapeRolls = 0;
		int attackCapeRerolls = 0;
		while (!hit && input.isAttackCapeEligible()) {
			attackCapeRolls++;
			if (!rollCape(DuelProofSpec.DRAW_ATTACK_CAPE_ACTIVATION, draws)) {
				break;
			}
			attackCapeRerolls++;
			hit = rollAccuracy(DuelProofSpec.DRAW_ATTACK_CAPE_ACCURACY,
				accuracy, defence, draws);
		}

		final int maxRoll = meleeMaxRoll(input);
		int damage = 0;
		if (hit) {
			damage = rollDamage(DuelProofSpec.DRAW_DAMAGE, maxRoll, draws);
			if (hasMomentum) {
				damage = Math.max(damage,
					rollDamage(DuelProofSpec.DRAW_MOMENTUM_DAMAGE, maxRoll, draws));
			}
			damage = applyPhysicalDamageReduction(damage, input.getDefenderArmourPoints());
		}

		final int damageBeforeDefenceCape = damage;
		int blockedDamage = 0;
		boolean defenceCapeRolled = false;
		boolean defenceCapeActivated = false;
		boolean defenceCapeApplied = false;
		if (hit && input.isDefenceCapeEligible()) {
			defenceCapeRolled = true;
			defenceCapeActivated = rollCape(DuelProofSpec.DRAW_DEFENCE_CAPE_ACTIVATION, draws);
			if (defenceCapeActivated && damageBeforeDefenceCape > 0) {
				damage /= 2;
				blockedDamage = damage;
				defenceCapeApplied = true;
			}
		}

		boolean strengthCapeRolled = false;
		boolean strengthCapeActivated = false;
		final double maximum = (double) (maxRoll + 320) / 640;
		if (damage >= maximum * 0.5D && input.isStrengthCapeEligible()) {
			strengthCapeRolled = true;
			strengthCapeActivated = rollCape(DuelProofSpec.DRAW_STRENGTH_CAPE_ACTIVATION, draws)
				&& hit;
			if (strengthCapeActivated) {
				damage += maximum * 0.2D;
			}
		}

		return new DuelProofMeleeResult(initialHit, hit, damage, damageBeforeDefenceCape,
			blockedDamage, attackCapeRolls, attackCapeRerolls, !initialHit && hit,
			defenceCapeRolled, defenceCapeActivated, defenceCapeApplied,
			strengthCapeRolled, strengthCapeActivated);
	}

	private boolean rollAccuracy(final int type, final double accuracy, final double defence,
								 final List<DuelProofDraw> draws) {
		final double hitChance;
		if (accuracy > defence) {
			hitChance = 1 - ((defence + 2) / (2 * (accuracy + 1)));
		} else {
			hitChance = accuracy / (2 * (defence + 1));
		}
		return unitValue(drawUnit(type, draws)) <= hitChance;
	}

	private boolean rollCape(final int type, final List<DuelProofDraw> draws) {
		final int bucket = (int) (unitValue(drawUnit(type, draws)) * CAPE_BUCKETS) + 1;
		return bucket <= CAPE_ACTIVATION_BUCKETS;
	}

	private int rollDamage(final int type, final int maxRoll, final List<DuelProofDraw> draws) {
		if (maxRoll <= 0) {
			return 0;
		}
		return ((int) drawInt(type, maxRoll, draws).getValue() + 320) / 640;
	}

	private DuelProofDraw drawUnit(final int type, final List<DuelProofDraw> draws) {
		final long start = random.getCandidateDrawCount();
		final long value = random.nextUnit53();
		final DuelProofDraw draw = new DuelProofDraw(type, 0, value, start,
			random.getCandidateDrawCount());
		if (draws != null) {
			draws.add(draw);
		}
		return draw;
	}

	private DuelProofDraw drawInt(final int type, final int bound,
								 final List<DuelProofDraw> draws) {
		final long start = random.getCandidateDrawCount();
		final int value = random.nextInt(bound);
		final DuelProofDraw draw = new DuelProofDraw(type, bound, value, start,
			random.getCandidateDrawCount());
		if (draws != null) {
			draws.add(draw);
		}
		return draw;
	}

	private static double unitValue(final DuelProofDraw draw) {
		return draw.getValue() / UNIT_53_DIVISOR;
	}

	private static double meleeAccuracy(final DuelProofMeleeInput input) {
		return (Math.floor(input.getAttackerAttackLevel()
			* prayerMultiplier(input.getAttackerAttackPrayerTier())) + 8
			+ styleBonus(input.getAttackerCombatStyle(), DuelProofSpec.COMBAT_STYLE_ACCURATE))
			* (input.getAttackerWeaponAimPoints() + 64);
	}

	private static double meleeDefence(final DuelProofMeleeInput input) {
		return (Math.floor(input.getDefenderDefenceLevel()
			* prayerMultiplier(input.getDefenderDefencePrayerTier())) + 8
			+ styleBonus(input.getDefenderCombatStyle(), DuelProofSpec.COMBAT_STYLE_DEFENSIVE))
			* (64 + input.getDefenderArmourPoints() * ARMOUR_ACCURACY_SCALE);
	}

	private static int meleeMaxRoll(final DuelProofMeleeInput input) {
		final double maxRoll = (Math.floor(input.getAttackerStrengthLevel()
			* prayerMultiplier(input.getAttackerStrengthPrayerTier())) + 8
			+ styleBonus(input.getAttackerCombatStyle(), DuelProofSpec.COMBAT_STYLE_AGGRESSIVE))
			* (input.getAttackerWeaponPowerPoints() + 64);
		return (int) maxRoll;
	}

	private static int styleBonus(final int actualStyle, final int matchingStyle) {
		if (actualStyle == DuelProofSpec.COMBAT_STYLE_CONTROLLED) {
			return 1;
		}
		return actualStyle == matchingStyle ? 3 : 0;
	}

	private static double prayerMultiplier(final int tier) {
		switch (tier) {
			case DuelProofSpec.PRAYER_TIER_LOW:
				return 1.05D;
			case DuelProofSpec.PRAYER_TIER_MIDDLE:
				return 1.10D;
			case DuelProofSpec.PRAYER_TIER_HIGH:
				return 1.15D;
			default:
				return 1.0D;
		}
	}

	private static int applyPhysicalDamageReduction(final int damage, final int armourPoints) {
		if (damage <= 0 || armourPoints <= 1) {
			return Math.max(0, damage);
		}
		final double reduction = Math.min(PHYSICAL_MITIGATION_CAP,
			armourPoints / PHYSICAL_MITIGATION_DIVISOR);
		return Math.max(1, (int) Math.floor(damage * (1.0D - reduction)));
	}

	private static boolean nextMomentum(final DuelProofMeleeInput input,
										final DuelProofMeleeResult result) {
		if (!result.isHit() || result.getDamage() <= 0) {
			return false;
		}
		final int maxRoll = meleeMaxRoll(input);
		if (maxRoll <= 0) {
			return false;
		}
		final int maxHit = applyPhysicalDamageReduction((maxRoll - 1 + 320) / 640,
			input.getDefenderArmourPoints());
		final int threshold = Math.max(1, (int) Math.ceil(maxHit * MOMENTUM_BIG_HIT_RATIO));
		return maxHit > 0 && result.getDamage() >= threshold;
	}

	private void requireStarterChosen() {
		if (starterOrdinal == -1 || starterDraw == null) {
			throw new IllegalStateException("duel starter has not been chosen");
		}
	}

	private void requireAvailable() {
		if (destroyed) {
			throw new IllegalStateException("duel melee replay has been destroyed");
		}
	}
}
