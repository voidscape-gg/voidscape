package com.openrsc.server.database.struct;

public final class PlayerCacheOwner {
	public final int playerId;
	public final String username;
	public final String cacheValue;

	public PlayerCacheOwner(final int playerId, final String username, final String cacheValue) {
		this.playerId = playerId;
		this.username = username;
		this.cacheValue = cacheValue;
	}
}
