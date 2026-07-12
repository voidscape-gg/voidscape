package com.openrsc.server.database.struct;

/** Immutable server-wide achievement claim persisted for later Legends queries. */
public final class WorldAchievementRecord {
	public String seasonId;
	public String recordKey;
	public String recordType;
	public int playerId;
	public String playerName;
	public int subjectId;
	public long value;
	public String source;
	public String sourceEventKey;
	public long claimedAtMs;
	public String detail;
}
