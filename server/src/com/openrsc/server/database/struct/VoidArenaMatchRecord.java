package com.openrsc.server.database.struct;

public class VoidArenaMatchRecord {
	public int id;
	public String seasonId;
	public int winnerId;
	public int loserId;
	public String winnerUsername;
	public String loserUsername;
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
