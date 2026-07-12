package com.openrsc.server.model.entity.player;

/**
 * Owns the save reservation and the single merged save intent deferred behind it.
 * Access is serialized by {@link Player}'s monitor.
 */
final class SaveReservationTracker {
	static final class Intent {
		final boolean logout;
		final boolean force;

		private Intent(boolean logout, boolean force) {
			this.logout = logout;
			this.force = force;
		}
	}

	private boolean reserved;
	private boolean deferred;
	private boolean deferredLogout;
	private boolean deferredForce;

	boolean reserve() {
		if (reserved) {
			return false;
		}
		reserved = true;
		return true;
	}

	boolean isReserved() {
		return reserved;
	}

	boolean deferIfReserved(boolean reservationOwner, boolean logout, boolean force) {
		if (!reserved || reservationOwner) {
			return false;
		}
		deferred = true;
		deferredLogout |= logout;
		deferredForce |= force;
		return true;
	}

	void release() {
		reserved = false;
	}

	Intent deferredIntent() {
		return deferred ? new Intent(deferredLogout, deferredForce) : null;
	}

	void deferredEnqueued() {
		deferred = false;
		deferredLogout = false;
		deferredForce = false;
	}

	void discardDeferred() {
		deferredEnqueued();
	}
}
