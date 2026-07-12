package com.openrsc.server.content;

/**
 * Immutable evidence captured for one real player-versus-player death.
 *
 * Runtime policy supplies the first rejection reason, if any. The settlement service
 * then applies the configured loot floor and the durable unordered-pair cooldown.
 */
public final class WorldPkSettlementRequest {
	private final String deathId;
	private final int killerPlayerId;
	private final Long killerAccountId;
	private final String killerName;
	private final int victimPlayerId;
	private final Long victimAccountId;
	private final String victimName;
	private final boolean victimWasSkulled;
	private final int victimDamage;
	private final long lootValue;
	private final int wildernessLevel;
	private final long occurredAtMs;
	private final String preliminaryRejectReason;

	/** An empty preliminary rejection reason means the runtime checks passed. */
	public WorldPkSettlementRequest(String deathId, int killerPlayerId,
		Long killerAccountId, String killerName, int victimPlayerId,
		Long victimAccountId, String victimName, boolean victimWasSkulled,
		int victimDamage, long lootValue, int wildernessLevel, long occurredAtMs,
		String preliminaryRejectReason) {
		this.deathId = deathId;
		this.killerPlayerId = killerPlayerId;
		this.killerAccountId = killerAccountId;
		this.killerName = killerName;
		this.victimPlayerId = victimPlayerId;
		this.victimAccountId = victimAccountId;
		this.victimName = victimName;
		this.victimWasSkulled = victimWasSkulled;
		this.victimDamage = victimDamage;
		this.lootValue = lootValue;
		this.wildernessLevel = wildernessLevel;
		this.occurredAtMs = occurredAtMs;
		this.preliminaryRejectReason = preliminaryRejectReason;
	}

	public String getDeathId() {
		return deathId;
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

	public boolean wasVictimSkulled() {
		return victimWasSkulled;
	}

	public int getVictimDamage() {
		return victimDamage;
	}

	public long getLootValue() {
		return lootValue;
	}

	public int getWildernessLevel() {
		return wildernessLevel;
	}

	public long getOccurredAtMs() {
		return occurredAtMs;
	}

	public String getPreliminaryRejectReason() {
		return preliminaryRejectReason;
	}
}
