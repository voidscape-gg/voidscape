package com.openrsc.server.database.struct;

public final class DuelReceiptStake {
	public final int ownerPlayerId;
	public final int slot;
	public final int catalogId;
	public final int amount;
	public final boolean noted;

	public DuelReceiptStake(final int ownerPlayerId, final int slot, final int catalogId, final int amount,
							final boolean noted) {
		this.ownerPlayerId = ownerPlayerId;
		this.slot = slot;
		this.catalogId = catalogId;
		this.amount = amount;
		this.noted = noted;
	}
}
