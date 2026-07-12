package com.openrsc.server.model.entity.player;

/** Access is serialized by {@link Player}'s monitor. */
final class SaveTicketTracker {
	private int pending;
	private int pendingLogout;

	void reserve(boolean logout) {
		pending++;
		if (logout) {
			pendingLogout++;
		}
	}

	void complete(boolean logout) {
		if (pending <= 0 || (logout && pendingLogout <= 0)) {
			throw new IllegalStateException("Completed a save ticket that was not pending");
		}
		pending--;
		if (logout) {
			pendingLogout--;
		}
	}

	boolean hasPending() {
		return pending > 0;
	}

	boolean hasLogoutPending() {
		return pendingLogout > 0;
	}

	void clear() {
		pending = 0;
		pendingLogout = 0;
	}

	int pendingCount() {
		return pending;
	}

	int pendingLogoutCount() {
		return pendingLogout;
	}
}
