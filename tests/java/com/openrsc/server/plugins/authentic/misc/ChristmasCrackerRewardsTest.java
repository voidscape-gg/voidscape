package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.ItemId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Executable reward-table regression for Voidscape Christmas crackers. */
public final class ChristmasCrackerRewardsTest {
	private ChristmasCrackerRewardsTest() {
	}

	public static void main(String[] args) {
		testCategoryBoundaries();
		testStandardPartyHatWeightsAndRoster();
		testCustomPartyHatWeightsAndRoster();
		testHolidayRareWeightsAndRoster();
		testFixtureDomainAndReplacementKeys();
		System.out.println("Christmas cracker server reward-table tests passed.");
	}

	private static void testCategoryBoundaries() {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		for (int roll = 0; roll <= 99; roll++) {
			increment(counts, ChristmasCracker.categoryForRoll(roll));
		}
		check(counts.get(0) == 60, "nothing category must contain exactly rolls 0..59");
		check(counts.get(1) == 20, "party-hat category must contain exactly rolls 60..79");
		check(counts.get(2) == 20, "holiday category must contain exactly rolls 80..99");
		check(ChristmasCracker.categoryForRoll(59) == 0, "59 must remain nothing");
		check(ChristmasCracker.categoryForRoll(60) == 1, "60 must enter party hats");
		check(ChristmasCracker.categoryForRoll(79) == 1, "79 must remain party hats");
		check(ChristmasCracker.categoryForRoll(80) == 2, "80 must enter holiday rares");
		check(ChristmasCracker.categoryForRoll(99) == 2, "99 must remain holiday rares");
		expectIllegalCategoryRoll(-1);
		expectIllegalCategoryRoll(100);
	}

	private static void testStandardPartyHatWeightsAndRoster() {
		Map<Integer, Integer> actual = new LinkedHashMap<>();
		for (int roll = 0; roll < 128; roll++) {
			increment(actual, ChristmasCracker.partyHatForRoll(false, roll));
		}

		Map<Integer, Integer> expected = new LinkedHashMap<>();
		expected.put(ItemId.PINK_PARTY_HAT.id(), 10);
		expected.put(ItemId.BLUE_PARTY_HAT.id(), 15);
		expected.put(ItemId.GREEN_PARTY_HAT.id(), 20);
		expected.put(ItemId.WHITE_PARTY_HAT.id(), 23);
		expected.put(ItemId.RED_PARTY_HAT.id(), 32);
		expected.put(ItemId.YELLOW_PARTY_HAT.id(), 28);
		check(actual.equals(expected), "party-hat roster and original colour weights must remain exact: " + actual);
		check(!actual.containsKey(ItemId.BLACK_PARTY_HAT.id()),
			"black party hat 1582 must remain excluded when custom sprites are disabled");
		check(ChristmasCracker.partyHatForRoll(false, 127) == ItemId.YELLOW_PARTY_HAT.id(),
			"raw roll 127 must remain yellow on standard worlds");
	}

	private static void testCustomPartyHatWeightsAndRoster() {
		Map<Integer, Integer> actual = new LinkedHashMap<>();
		for (int roll = 0; roll < 138; roll++) {
			increment(actual, ChristmasCracker.partyHatForRoll(true, roll));
		}

		Map<Integer, Integer> expected = new LinkedHashMap<>();
		expected.put(ItemId.PINK_PARTY_HAT.id(), 10);
		expected.put(ItemId.BLUE_PARTY_HAT.id(), 15);
		expected.put(ItemId.GREEN_PARTY_HAT.id(), 20);
		expected.put(ItemId.WHITE_PARTY_HAT.id(), 23);
		expected.put(ItemId.RED_PARTY_HAT.id(), 32);
		expected.put(ItemId.YELLOW_PARTY_HAT.id(), 28);
		expected.put(ItemId.BLACK_PARTY_HAT.id(), 10);
		check(actual.equals(expected), "custom party-hat roster and weights must remain exact: " + actual);
		for (int roll = 128; roll <= 137; roll++) {
			check(ChristmasCracker.partyHatForRoll(true, roll) == ItemId.BLACK_PARTY_HAT.id(),
				"custom raw roll " + roll + " must select black party hat");
			check(ChristmasCracker.partyHatForRoll(false, roll) != ItemId.BLACK_PARTY_HAT.id(),
				"standard raw roll " + roll + " must not select black party hat");
		}
	}

	private static void testHolidayRareWeightsAndRoster() {
		Map<Integer, Integer> actual = new LinkedHashMap<>();
		for (int roll = 0; roll < 10; roll++) {
			increment(actual, ChristmasCracker.holidayRareForRoll(roll));
		}

		Map<Integer, Integer> expected = new LinkedHashMap<>();
		expected.put(ItemId.PUMPKIN.id(), 2);
		expected.put(ItemId.EASTER_EGG.id(), 2);
		expected.put(ItemId.GREEN_HALLOWEEN_MASK.id(), 1);
		expected.put(ItemId.RED_HALLOWEEN_MASK.id(), 1);
		expected.put(ItemId.BLUE_HALLOWEEN_MASK.id(), 1);
		expected.put(ItemId.SANTAS_HAT.id(), 1);
		expected.put(ItemId.BUNNY_EARS.id(), 1);
		expected.put(ItemId.SCYTHE.id(), 1);
		check(actual.equals(expected), "holiday reward roster and weights must remain exact: " + actual);
		check(!actual.containsKey(ItemId.BLACK_PARTY_HAT.id()),
			"black party hat 1582 must not move into the holiday pool");
	}

	private static void testFixtureDomainAndReplacementKeys() {
		check(ChristmasCracker.validFixtureRolls(false, 0, 0),
			"nothing fixture must accept canonical reward roll 0");
		check(!ChristmasCracker.validFixtureRolls(false, 59, 1),
			"nothing fixture must reject nonzero reward rolls");
		check(ChristmasCracker.validFixtureRolls(false, 60, 127),
			"standard party fixture must accept reward roll 127");
		check(!ChristmasCracker.validFixtureRolls(false, 79, 128),
			"standard party fixture must reject custom-only reward roll 128");
		check(ChristmasCracker.validFixtureRolls(true, 60, 128),
			"custom party fixture must accept first black-party-hat roll 128");
		check(ChristmasCracker.validFixtureRolls(true, 79, 137),
			"custom party fixture must accept last black-party-hat roll 137");
		check(!ChristmasCracker.validFixtureRolls(true, 79, 138),
			"custom party fixture must reject reward roll 138");
		check(ChristmasCracker.validFixtureRolls(false, 80, 9),
			"holiday fixture must accept reward roll 9");
		check(!ChristmasCracker.validFixtureRolls(true, 99, 10),
			"holiday fixture must reject reward roll 10 on every world");
		check(!ChristmasCracker.validFixtureRolls(false, -1, 0), "fixture must reject category -1");
		check(!ChristmasCracker.validFixtureRolls(false, 100, 0), "fixture must reject category 100");
		check(!ChristmasCracker.validFixtureRolls(false, 60, -1), "fixture must reject reward -1");
		check("bunny_ears".equals(ChristmasCracker.replacementCacheKey(ItemId.BUNNY_EARS.id())),
			"Bunny ears must unlock Thessalia replacement eligibility");
		check("scythe".equals(ChristmasCracker.replacementCacheKey(ItemId.SCYTHE.id())),
			"Scythe must unlock Thessalia replacement eligibility");
		check(ChristmasCracker.replacementCacheKey(ItemId.PUMPKIN.id()) == null,
			"ordinary holiday rewards must not write replacement cache keys");
	}

	private static void expectIllegalCategoryRoll(int roll) {
		try {
			ChristmasCracker.categoryForRoll(roll);
			throw new AssertionError("category roll " + roll + " should be rejected");
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static void increment(Map<Integer, Integer> counts, int id) {
		counts.put(id, counts.containsKey(id) ? counts.get(id) + 1 : 1);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
