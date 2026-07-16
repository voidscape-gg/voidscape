package com.openrsc.server.database.struct;

public final class DuelReceiptSwing {
	public final int actorPlayerId;
	public final int swingNumber;
	public final int combatStyle;
	public final boolean didHit;
	public final int damage;

	public DuelReceiptSwing(final int actorPlayerId, final int swingNumber, final int combatStyle,
							final boolean didHit, final int damage) {
		this.actorPlayerId = actorPlayerId;
		this.swingNumber = swingNumber;
		this.combatStyle = combatStyle;
		this.didHit = didHit;
		this.damage = damage;
	}
}
