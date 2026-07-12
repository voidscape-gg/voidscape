package com.openrsc.server.database.struct;

/** Restricted game-side view of one paid subscription-card fulfillment. */
public final class PortalCommerceEntitlement {
	public long id;
	public long paymentId;
	public int accountId;
	public String provider;
	public String transactionId;
	public String lineKey;
	public String packageId;
	public int unitIndex;
	public String state;
	public int claimedPlayerId;
	public long claimedItemId;
	public long claimedAtMs;
	public long createdAtMs;
	public long updatedAtMs;
}
