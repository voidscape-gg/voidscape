package com.openrsc.server.database.struct;

public final class DuelReceiptParticipant {
	public final int playerId;
	public final String username;
	public final boolean won;

	public DuelReceiptParticipant(final int playerId, final String username, final boolean won) {
		this.playerId = playerId;
		this.username = username;
		this.won = won;
	}
}
