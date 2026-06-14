package com.openrsc.server.database.struct;

public class VoidArenaMatchRecord {
	public String seasonId;
	public int winnerId;
	public int loserId;
	public int winnerRatingBefore;
	public int winnerRatingAfter;
	public int loserRatingBefore;
	public int loserRatingAfter;
	public int ratingDelta;
	public boolean disconnectLoss;
	public int slotIndex;
	public long startedAt;
	public long endedAt;
}
