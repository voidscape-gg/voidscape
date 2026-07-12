package com.openrsc.server.util;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PlayerEventStopperTest {
	private PlayerEventStopperTest() {
	}

	public static void main(String[] args) throws Exception {
		testInverseLockOrderCompletes();
		testGuardRejectsStopUnderPlayerMonitor();
		testThrowingStopperIsBestEffort();
		System.out.println("Player event-stop lock-order tests passed.");
	}

	private static void testInverseLockOrderCompletes() throws Exception {
		Object playerLock = new Object();
		Object pluginTaskLock = new Object();
		CountDownLatch pluginLockHeld = new CountDownLatch(1);
		CountDownLatch playerStateMarked = new CountDownLatch(1);

		Thread pluginFinally = new Thread(() -> {
			synchronized (pluginTaskLock) {
				pluginLockHeld.countDown();
				await(playerStateMarked);
				synchronized (playerLock) {
					// Mirrors admission finally acquiring Player after holding PluginTask.
				}
			}
		}, "plugin-finally-lock-order-test");

		Thread logout = new Thread(() -> {
			await(pluginLockHeld);
			synchronized (playerLock) {
				playerStateMarked.countDown();
			}
			PlayerEventStopper.stopOutsidePlayerLock(playerLock,
				Collections.singletonList(pluginTaskLock), lock -> {
					synchronized (lock) {
						// Mirrors PluginTask.stop acquiring the task monitor.
					}
				});
		}, "logout-lock-order-test");

		pluginFinally.start();
		logout.start();
		pluginFinally.join(1_000L);
		logout.join(1_000L);
		check(!pluginFinally.isAlive() && !logout.isAlive(),
			"inverse-lock scenario completes because event stop occurs outside Player monitor");
	}

	private static void testGuardRejectsStopUnderPlayerMonitor() {
		Object playerLock = new Object();
		boolean rejected = false;
		synchronized (playerLock) {
			try {
				PlayerEventStopper.stopOutsidePlayerLock(playerLock,
					Collections.singletonList(new Object()), ignored -> { });
			} catch (IllegalStateException expected) {
				rejected = true;
			}
		}
		check(rejected, "runtime guard rejects Player-to-PluginTask lock inversion");
	}

	private static void testThrowingStopperIsBestEffort() {
		Object playerLock = new Object();
		int[] attempted = {0};
		int failures = PlayerEventStopper.stopOutsidePlayerLock(playerLock,
			java.util.Arrays.asList("throws", "continues"), event -> {
				attempted[0]++;
				if ("throws".equals(event)) {
					throw new IllegalStateException("injected stop failure");
				}
			});
		check(failures == 1 && attempted[0] == 2,
			"throwing event stop is recorded and cannot prevent later events or logout save");
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(1, TimeUnit.SECONDS)) {
				throw new AssertionError("lock-order latch timed out");
			}
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError("lock-order test interrupted", ex);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
