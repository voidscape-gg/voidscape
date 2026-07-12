package com.openrsc.server.content;

import com.openrsc.server.database.struct.WorldPkEvent;

/** Immutable durable outcome returned by {@link WorldPkSettlementService}. */
public final class WorldPkSettlementResult {
	public enum Status {
		NOT_SETTLED,
		APPLIED,
		REPLAY
	}

	private static final WorldPkSettlementResult NOT_SETTLED =
		new WorldPkSettlementResult(Status.NOT_SETTLED, null);

	private final Status status;
	private final String deathId;
	private final String seasonId;
	private final int killerPlayerId;
	private final Long killerAccountId;
	private final String killerName;
	private final int victimPlayerId;
	private final Long victimAccountId;
	private final String victimName;
	private final boolean qualified;
	private final String rejectReason;
	private final boolean victimWasSkulled;
	private final int victimDamage;
	private final long lootValue;
	private final int streakAfter;
	private final int endedStreak;
	private final int wildernessLevel;
	private final long occurredAtMs;

	private WorldPkSettlementResult(Status status, WorldPkEvent event) {
		this.status = status;
		this.deathId = event == null ? "" : event.deathId;
		this.seasonId = event == null ? "" : event.seasonId;
		this.killerPlayerId = event == null ? 0 : event.killerPlayerId;
		this.killerAccountId = event == null ? null : event.killerAccountId;
		this.killerName = event == null ? "" : event.killerName;
		this.victimPlayerId = event == null ? 0 : event.victimPlayerId;
		this.victimAccountId = event == null ? null : event.victimAccountId;
		this.victimName = event == null ? "" : event.victimName;
		this.qualified = event != null && event.qualified;
		this.rejectReason = event == null ? "" : event.rejectReason;
		this.victimWasSkulled = event != null && event.victimWasSkulled;
		this.victimDamage = event == null ? 0 : event.victimDamage;
		this.lootValue = event == null ? 0L : event.lootValue;
		this.streakAfter = event == null ? 0 : event.streakAfter;
		this.endedStreak = event == null ? 0 : event.endedStreak;
		this.wildernessLevel = event == null ? 0 : event.wildernessLevel;
		this.occurredAtMs = event == null ? 0L : event.occurredAtMs;
	}

	static WorldPkSettlementResult notSettled() {
		return NOT_SETTLED;
	}

	static WorldPkSettlementResult applied(WorldPkEvent event) {
		return new WorldPkSettlementResult(Status.APPLIED, event);
	}

	static WorldPkSettlementResult replay(WorldPkEvent event) {
		return new WorldPkSettlementResult(Status.REPLAY, event);
	}

	public Status getStatus() {
		return status;
	}

	public boolean isSettled() {
		return status != Status.NOT_SETTLED;
	}

	public boolean isNewEvent() {
		return status == Status.APPLIED;
	}

	/** Only a newly applied durable event may drive chat or another presentation effect. */
	public boolean isPublishable() {
		return status == Status.APPLIED;
	}

	public String getDeathId() {
		return deathId;
	}

	public String getSeasonId() {
		return seasonId;
	}

	public int getKillerPlayerId() {
		return killerPlayerId;
	}

	public Long getKillerAccountId() {
		return killerAccountId;
	}

	public String getKillerName() {
		return killerName;
	}

	public int getVictimPlayerId() {
		return victimPlayerId;
	}

	public Long getVictimAccountId() {
		return victimAccountId;
	}

	public String getVictimName() {
		return victimName;
	}

	public boolean isQualified() {
		return qualified;
	}

	public String getRejectReason() {
		return rejectReason;
	}

	public boolean wasVictimSkulled() {
		return victimWasSkulled;
	}

	public int getVictimDamage() {
		return victimDamage;
	}

	public long getLootValue() {
		return lootValue;
	}

	public int getStreakAfter() {
		return streakAfter;
	}

	public int getEndedStreak() {
		return endedStreak;
	}

	public int getWildernessLevel() {
		return wildernessLevel;
	}

	public long getOccurredAtMs() {
		return occurredAtMs;
	}
}
