package com.openrsc.server.util;

import java.util.Collection;
import java.util.function.Consumer;

/** Enforces the Player -> PluginTask lock-order boundary during logout. */
public final class PlayerEventStopper {
	private PlayerEventStopper() {
	}

	public static <T> int stopOutsidePlayerLock(Object playerLock, Collection<T> events,
		Consumer<T> stopper) {
		if (Thread.holdsLock(playerLock)) {
			throw new IllegalStateException("Player events must be stopped after releasing the Player monitor");
		}
		int failures = 0;
		for (T event : events) {
			try {
				stopper.accept(event);
			} catch (final RuntimeException ignored) {
				failures++;
			}
		}
		return failures;
	}
}
