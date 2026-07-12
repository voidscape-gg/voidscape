package com.openrsc.server.database.struct;

/** Immutable audit row for one evaluated Wilderness player death. */
public final class WorldPkEvent {
	public String deathId;
	public String seasonId;
	public int killerPlayerId;
	public Long killerAccountId;
	public String killerName;
	public int victimPlayerId;
	public Long victimAccountId;
	public String victimName;
	public int pairLowPlayerId;
	public int pairHighPlayerId;
	public boolean qualified;
	public String rejectReason;
	public boolean victimWasSkulled;
	public int victimDamage;
	public long lootValue;
	public int streakAfter;
	public int endedStreak;
	public int wildernessLevel;
	public long occurredAtMs;
}
