package com.openrsc.server.service;

public final class PlayerServiceSaveOutcomeTest {
	private PlayerServiceSaveOutcomeTest() {
	}

	public static void main(String[] args) {
		final int[] failures = {0};
		final int[] resets = {0};
		for (int i = 0; i < 6; i++) {
			check(!PlayerService.applySaveOutcome(false, () -> resets[0]++, () -> failures[0]++),
				"failed database write " + (i + 1) + " is never a commit");
		}
		check(failures[0] == 6 && resets[0] == 0, "all failed writes remain retry bookkeeping only");
		check(PlayerService.applySaveOutcome(true, () -> resets[0]++, () -> failures[0]++),
			"actual database commit reports success");
		check(resets[0] == 1 && failures[0] == 6, "commit resets attempts without inventing a failure");
		System.out.println("Player service save outcome tests passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
