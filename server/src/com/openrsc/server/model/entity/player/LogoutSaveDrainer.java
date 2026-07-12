package com.openrsc.server.model.entity.player;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Bounded shutdown retry loop used while the login executor and database are still open. */
final class LogoutSaveDrainer {
	interface Port {
		boolean isCurrent();
		CompletableFuture<Boolean> currentOrStartAttempt();
	}

	private LogoutSaveDrainer() {
	}

	static boolean drain(Port port, long timeoutMillis) {
		final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
		while (port.isCurrent()) {
			final long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				return false;
			}
			final CompletableFuture<Boolean> attempt = port.currentOrStartAttempt();
			if (attempt == null) {
				Thread.yield();
				continue;
			}
			try {
				if (!Boolean.TRUE.equals(attempt.get(
					Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)), TimeUnit.MILLISECONDS))) {
					Thread.sleep(10L);
				}
			} catch (final Exception ignored) {
				// A failed/exceptional attempt is retried while time and lifecycle remain.
			}
		}
		return true;
	}
}
