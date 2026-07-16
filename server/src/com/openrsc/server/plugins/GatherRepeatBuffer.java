package com.openrsc.server.plugins;

/**
 * One-slot input buffer for repeating the active gathering interaction.
 */
public final class GatherRepeatBuffer {

	private boolean bound;
	private boolean pending;
	private boolean awaitingObjectCommand;
	private int objectId;
	private int objectX;
	private int objectY;
	private int option;

	public synchronized void bind(int objectId, int objectX, int objectY, int option) {
		this.objectId = objectId;
		this.objectX = objectX;
		this.objectY = objectY;
		this.option = option;
		this.bound = true;
		this.pending = false;
		this.awaitingObjectCommand = false;
	}

	public synchronized boolean expectObjectCommand() {
		if (!bound) {
			return false;
		}
		awaitingObjectCommand = true;
		return true;
	}

	public synchronized boolean queueIfMatches(
		int objectId, int objectX, int objectY, int option
	) {
		if (!bound
			|| this.objectId != objectId
			|| this.objectX != objectX
			|| this.objectY != objectY
			|| this.option != option) {
			return false;
		}
		pending = true;
		awaitingObjectCommand = false;
		return true;
	}

	/**
	 * Resolves the buffered click at an attempt boundary. An earned repeat satisfies
	 * the click. At the final earned boundary, the click converts the current batch
	 * into exactly one manual tail attempt instead of starting a fresh earned batch.
	 */
	public synchronized AttemptBoundary resolveAttemptBoundary(
		int currentProgress, int totalBatch
	) {
		final boolean startManualTail = pending && currentProgress >= totalBatch;
		pending = false;
		if (startManualTail) {
			return new AttemptBoundary(0, 1, true);
		}
		return new AttemptBoundary(currentProgress, totalBatch, false);
	}

	public synchronized boolean isBound() {
		return bound;
	}

	public synchronized boolean hasPending() {
		return pending;
	}

	public synchronized boolean isAwaitingObjectCommand() {
		return awaitingObjectCommand;
	}

	public synchronized void clearPending() {
		pending = false;
		awaitingObjectCommand = false;
	}

	public synchronized void reset() {
		bound = false;
		pending = false;
		awaitingObjectCommand = false;
	}

	public static final class AttemptBoundary {
		private final int currentProgress;
		private final int totalBatch;
		private final boolean manualTail;

		private AttemptBoundary(
			int currentProgress, int totalBatch, boolean manualTail
		) {
			this.currentProgress = currentProgress;
			this.totalBatch = totalBatch;
			this.manualTail = manualTail;
		}

		public int getCurrentProgress() {
			return currentProgress;
		}

		public int getTotalBatch() {
			return totalBatch;
		}

		public boolean startsManualTail() {
			return manualTail;
		}
	}
}
