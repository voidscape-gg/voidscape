package com.openrsc.server.database.struct;

/** Result of the transaction-owned Void Arena ACTIVE-to-SETTLED transition. */
public enum VoidArenaSettlementStatus {
	SETTLED,
	NOT_ACTIVE,
	DATABASE_ERROR
}
