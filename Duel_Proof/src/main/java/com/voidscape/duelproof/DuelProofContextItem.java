package com.voidscape.duelproof;

/** Immutable item entry from a verified duel's committed context. */
public final class DuelProofContextItem {

	private final int slot;
	private final int itemId;
	private final int amount;
	private final boolean noted;

	DuelProofContextItem(final int slot, final int itemId, final int amount,
						 final boolean noted) {
		this.slot = slot;
		this.itemId = itemId;
		this.amount = amount;
		this.noted = noted;
	}

	public int getSlot() { return slot; }
	public int getItemId() { return itemId; }
	public int getAmount() { return amount; }
	public boolean isNoted() { return noted; }
	public boolean isEmpty() { return itemId == -1; }
}
