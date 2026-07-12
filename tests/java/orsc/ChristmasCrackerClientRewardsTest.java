package orsc;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** Executable shared-client validation contract for cracker reel outcomes. */
public final class ChristmasCrackerClientRewardsTest {
	private ChristmasCrackerClientRewardsTest() {
	}

	public static void main(String[] args) {
		boolean originalCustomSprites = Config.S_WANT_CUSTOM_SPRITES;
		try {
			Config.S_WANT_CUSTOM_SPRITES = false;
			Set<Integer> standardPartyHats = acceptedIds(1);
			check(standardPartyHats.equals(setOf(576, 577, 578, 579, 580, 581)),
				"standard shared-client party-hat roster drifted: " + standardPartyHats);
			check(!mudclient.isValidChristmasCrackerResult(1, 1582),
				"standard shared client must reject black party hat 1582");

			Config.S_WANT_CUSTOM_SPRITES = true;
			Set<Integer> customPartyHats = acceptedIds(1);
			check(customPartyHats.equals(setOf(576, 577, 578, 579, 580, 581, 1582)),
				"custom shared-client party-hat roster drifted: " + customPartyHats);
			check("BLACK PARTY HAT".equals(mudclient.christmasCrackerDisplayName(1582)),
				"black party hat reel label must be explicit");

			Set<Integer> holidayRares = acceptedIds(2);
			check(holidayRares.equals(setOf(422, 677, 828, 831, 832, 971, 1156, 1289)),
				"shared-client holiday roster drifted: " + holidayRares);
			check(!mudclient.isValidChristmasCrackerResult(2, 1582),
				"black party hat 1582 must not validate as a holiday outcome");
			check("BUNNY EARS".equals(mudclient.christmasCrackerDisplayName(1156)),
				"Bunny ears reel label must be explicit");
			check("SCYTHE".equals(mudclient.christmasCrackerDisplayName(1289)),
				"Scythe reel label must be explicit");
			check(mudclient.isValidChristmasCrackerResult(0, -1), "nothing/-1 must remain valid");
			check(!mudclient.isValidChristmasCrackerResult(0, 1156),
				"a reward item must not validate as the nothing category");
		} finally {
			Config.S_WANT_CUSTOM_SPRITES = originalCustomSprites;
		}
		System.out.println("Christmas cracker shared-client reward tests passed.");
	}

	private static Set<Integer> acceptedIds(int category) {
		Set<Integer> result = new LinkedHashSet<>();
		for (int id = 0; id <= 2000; id++) {
			if (mudclient.isValidChristmasCrackerResult(category, id)) result.add(id);
		}
		return result;
	}

	private static Set<Integer> setOf(Integer... ids) {
		return new LinkedHashSet<>(Arrays.asList(ids));
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
