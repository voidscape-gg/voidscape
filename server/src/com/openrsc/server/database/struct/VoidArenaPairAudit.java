package com.openrsc.server.database.struct;

/** Durable cross-season anti-farm history for one canonical player-id pair. */
public final class VoidArenaPairAudit {
	public final long lastRatedResultAtMs;
	public final int decisiveResultsUtcDay;

	public VoidArenaPairAudit(final long lastRatedResultAtMs,
							  final int decisiveResultsUtcDay) {
		this.lastRatedResultAtMs = lastRatedResultAtMs;
		this.decisiveResultsUtcDay = decisiveResultsUtcDay;
	}
}
