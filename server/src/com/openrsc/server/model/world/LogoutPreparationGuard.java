package com.openrsc.server.model.world;

/** Ensures the durable logout worker is installed even when a noncritical hook fails. */
final class LogoutPreparationGuard implements AutoCloseable {
	private final Runnable ensureSaveWorker;
	private boolean closed;

	LogoutPreparationGuard(Runnable ensureSaveWorker) {
		this.ensureSaveWorker = ensureSaveWorker;
	}

	@Override
	public void close() {
		if (!closed) {
			closed = true;
			ensureSaveWorker.run();
		}
	}
}
