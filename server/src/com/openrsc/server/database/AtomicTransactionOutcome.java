package com.openrsc.server.database;

/** Durable outcome of a database transaction attempt. */
public enum AtomicTransactionOutcome {
	COMMITTED,
	ROLLED_BACK,
	/** COMMIT threw, but a subsequent ROLLBACK completed; durable state must be verified. */
	COMMIT_UNCERTAIN,
	/** Neither rollback nor commit could be established. The caller must fail closed. */
	UNKNOWN
}
