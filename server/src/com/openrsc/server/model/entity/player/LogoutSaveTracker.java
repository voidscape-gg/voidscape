package com.openrsc.server.model.entity.player;

/** Access is serialized by {@link Player}'s monitor. */
final class LogoutSaveTracker {
	private boolean prepared;
	private boolean workerPending;
	private int failures;

	boolean beginPreparation() {
		if (prepared) {
			return false;
		}
		prepared = true;
		return true;
	}

	boolean installWorker() {
		if (workerPending) {
			return false;
		}
		workerPending = true;
		return true;
	}

	long recordFailureDelayTicks() {
		failures++;
		return Math.min(50, 1L << Math.min(failures, 5));
	}

	void completed() {
		workerPending = false;
		failures = 0;
	}

	void abandoned() {
		workerPending = false;
	}

	void resetAfterInstallFailure() {
		prepared = false;
		workerPending = false;
		failures = 0;
	}

	boolean isPrepared() {
		return prepared;
	}

	boolean isWorkerPending() {
		return workerPending;
	}
}
