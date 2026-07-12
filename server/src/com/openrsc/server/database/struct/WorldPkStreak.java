package com.openrsc.server.database.struct;

/** Mutable per-season PK streak projection derived from immutable PK events. */
public final class WorldPkStreak {
	public String seasonId;
	public int playerId;
	public String playerName;
	public int currentStreak;
	public int bestStreak;
	public long qualifiedKills;
	public long lastQualifiedAtMs;
	public long updatedAtMs;
}
