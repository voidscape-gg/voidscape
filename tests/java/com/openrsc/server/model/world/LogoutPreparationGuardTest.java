package com.openrsc.server.model.world;

public final class LogoutPreparationGuardTest {
	private LogoutPreparationGuardTest() {
	}

	public static void main(String[] args) {
		final int[] saveWorkerInstalls = {0};
		try {
			try (LogoutPreparationGuard ignored = new LogoutPreparationGuard(() -> saveWorkerInstalls[0]++)) {
				throw new IllegalStateException("injected pre-save logout hook failure");
			}
		} catch (IllegalStateException expected) {
			check(saveWorkerInstalls[0] == 1, "pre-save hook failure still installs durable logout worker");
		}
		check(saveWorkerInstalls[0] == 1, "guard installs the save worker exactly once");
		System.out.println("Logout preparation guard tests passed.");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
