package com.openrsc.server.content;

public final class SkillBatchingTest {
	public static void main(String[] args) {
		assertLimit(0, 1);
		assertRange(1, 9, 1);
		assertRange(10, 19, 2);
		assertRange(20, 29, 3);
		assertRange(30, 39, 4);
		assertRange(40, 49, 5);
		assertRange(50, 59, 7);
		assertRange(60, 69, 10);
		assertRange(70, 79, 14);
		assertRange(80, 89, 18);
		assertRange(90, 98, 24);
		assertLimit(99, 30);
		assertLimit(120, 30);

		assertEquals(1, SkillBatching.clampRequested(50, 9), "level 9 clamp");
		assertEquals(2, SkillBatching.clampRequested(50, 10), "level 10 clamp");
		assertEquals(3, SkillBatching.clampRequested(3, 60), "request below cap");
		assertEquals(10, SkillBatching.clampRequested(10, 60), "request equal to cap");
		assertEquals(10, SkillBatching.clampRequested(30, 60), "request above cap");
		assertEquals(1, SkillBatching.clampRequested(0, 99), "zero request stays safe");

		System.out.println("Skill batching limit tests passed");
	}

	private static void assertRange(int first, int last, int expected) {
		assertLimit(first, expected);
		assertLimit(last, expected);
	}

	private static void assertLimit(int level, int expected) {
		assertEquals(expected, SkillBatching.limitForLevel(level), "level " + level);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
