package com.openrsc.server.content.announcements;

/** Exact copy checks for deterministic announcement-preview fixtures. */
public final class WorldAnnouncementPreviewTest {
	private WorldAnnouncementPreviewTest() {
	}

	public static void main(String[] args) {
		assertEquals(
			"@mag@[Wilderness] @red@Preview Owner @whi@defeated @red@Test Rival"
				+ " @whi@and secured @yel@5000 gp @whi@of qualified loot in level-20 Wilderness.",
			WorldAnnouncementService.qualifiedWildernessKillMessage(
				"Preview Owner", "Test Rival", 5_000L, 20),
			"qualified PK baseline");
		assertEquals(
			"@mag@[PK Streak] @red@Preview Owner"
				+ " @whi@is heating up: @yel@3 qualified Wilderness kills@whi@!",
			WorldAnnouncementService.pkStreakMilestoneMessage("Preview Owner", 3),
			"three-kill streak");
		assertEquals(
			"@mag@[PK Streak] @red@Preview Owner"
				+ " @whi@is dominating the Wilderness: @yel@5 kills without dying@whi@!",
			WorldAnnouncementService.pkStreakMilestoneMessage("Preview Owner", 5),
			"five-kill streak");
		assertEquals(
			"@mag@[PK Streak] @red@Preview Owner"
				+ " @whi@is @red@LEGENDARY@whi@: @yel@10 qualified kills without dying@whi@!",
			WorldAnnouncementService.pkStreakMilestoneMessage("Preview Owner", 10),
			"ten-kill streak");

		System.out.println("World announcement preview message tests passed.");
	}

	private static void assertEquals(String expected, String actual, String label) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + " copy drifted\nexpected: " + expected
				+ "\nactual:   " + actual);
		}
	}
}
