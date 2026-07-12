package com.openrsc.server.model.entity.player;

/**
 * Tracks durable-save generations for mutations that must survive an in-flight save.
 * Access is serialized by {@link Player}'s monitor.
 */
final class PersistentSaveTracker {
	static boolean shouldQueueForcedFollowUp(boolean logout, boolean force, boolean savePending) {
		return !logout && force && savePending;
	}

	enum Completion {
		QUIESCENT,
		FOLLOW_UP,
		RETRY
	}

	private long requestedGeneration;
	private long committedGeneration;
	private long inFlightGeneration;
	private boolean workerPending;

	/**
	 * Marks another mutation dirty and returns whether a worker must be installed.
	 */
	boolean request() {
		requestedGeneration++;
		if (workerPending) {
			return false;
		}
		workerPending = true;
		return true;
	}

	long beginAttempt() {
		if (!workerPending || inFlightGeneration != 0) {
			throw new IllegalStateException("Persistent save attempt already active");
		}
		inFlightGeneration = requestedGeneration;
		return inFlightGeneration;
	}

	Completion completeAttempt(long generation, boolean committed) {
		if (generation == 0 || generation != inFlightGeneration) {
			throw new IllegalStateException("Persistent save completion does not match active generation");
		}
		inFlightGeneration = 0;
		if (!committed) {
			return Completion.RETRY;
		}

		committedGeneration = Math.max(committedGeneration, generation);
		return committedGeneration >= requestedGeneration
			? Completion.QUIESCENT
			: Completion.FOLLOW_UP;
	}

	boolean isQuiescent() {
		return inFlightGeneration == 0 && committedGeneration >= requestedGeneration;
	}

	/**
	 * Called only after the worker event has been stopped while holding Player's monitor.
	 */
	void workerStopped() {
		if (!isQuiescent()) {
			throw new IllegalStateException("Cannot stop a dirty persistent save worker");
		}
		workerPending = false;
	}

	void abandonWorker() {
		inFlightGeneration = 0;
		workerPending = false;
	}

	boolean isWorkerPending() {
		return workerPending;
	}

	long getRequestedGeneration() {
		return requestedGeneration;
	}

	long getCommittedGeneration() {
		return committedGeneration;
	}
}
