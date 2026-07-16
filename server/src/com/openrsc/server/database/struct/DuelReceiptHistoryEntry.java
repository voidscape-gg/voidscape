package com.openrsc.server.database.struct;

public final class DuelReceiptHistoryEntry {
	public final long duelId;
	public final long startedAt;
	public final long completedAt;
	public final DuelReceiptParticipant requester;
	public final DuelReceiptParticipant opponent;

	public DuelReceiptHistoryEntry(final long duelId, final long startedAt, final long completedAt,
								   final DuelReceiptParticipant requester,
								   final DuelReceiptParticipant opponent) {
		this.duelId = duelId;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.requester = requester;
		this.opponent = opponent;
	}
}
