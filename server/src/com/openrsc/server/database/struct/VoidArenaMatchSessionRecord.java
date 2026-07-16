package com.openrsc.server.database.struct;

/** Immutable database representation of one hardened ranked Void Arena session. */
public final class VoidArenaMatchSessionRecord {
	public static final String STATUS_ACTIVE = "ACTIVE";
	public static final String STATUS_SETTLED = "SETTLED";

	public static final String REASON_DEATH = "DEATH";
	public static final String REASON_FORFEIT = "FORFEIT";
	public static final String REASON_TIMEOUT_DRAW = "TIMEOUT_DRAW";
	public static final String REASON_SERVER_SHUTDOWN_NO_CONTEST = "SERVER_SHUTDOWN_NO_CONTEST";
	public static final String REASON_SERVER_RESTART_NO_CONTEST = "SERVER_RESTART_NO_CONTEST";

	public final String matchId;
	public final String seasonId;
	public final String status;
	public final String resultReason;
	public final int playerAId;
	public final int playerBId;
	public final Integer winnerId;
	public final Integer loserId;
	public final int playerARatingBefore;
	public final Integer playerARatingAfter;
	public final int playerBRatingBefore;
	public final Integer playerBRatingAfter;
	public final int ratingDelta;
	public final boolean ratingApplied;
	public final boolean sameIp;
	public final boolean sameIpLocalExempt;
	public final int priorRatedResults30m;
	public final int priorDecisiveResultsDay;
	public final int slotIndex;
	public final long startedAtMs;
	public final Long endedAtMs;

	public VoidArenaMatchSessionRecord(final String matchId, final String seasonId,
									   final String status, final String resultReason,
									   final int playerAId, final int playerBId,
									   final Integer winnerId, final Integer loserId,
									   final int playerARatingBefore,
									   final Integer playerARatingAfter,
									   final int playerBRatingBefore,
									   final Integer playerBRatingAfter,
									   final int ratingDelta, final boolean ratingApplied,
									   final boolean sameIp, final boolean sameIpLocalExempt,
									   final int priorRatedResults30m,
									   final int priorDecisiveResultsDay,
									   final int slotIndex, final long startedAtMs,
									   final Long endedAtMs) {
		this.matchId = matchId;
		this.seasonId = seasonId;
		this.status = status;
		this.resultReason = resultReason;
		this.playerAId = playerAId;
		this.playerBId = playerBId;
		this.winnerId = winnerId;
		this.loserId = loserId;
		this.playerARatingBefore = playerARatingBefore;
		this.playerARatingAfter = playerARatingAfter;
		this.playerBRatingBefore = playerBRatingBefore;
		this.playerBRatingAfter = playerBRatingAfter;
		this.ratingDelta = ratingDelta;
		this.ratingApplied = ratingApplied;
		this.sameIp = sameIp;
		this.sameIpLocalExempt = sameIpLocalExempt;
		this.priorRatedResults30m = priorRatedResults30m;
		this.priorDecisiveResultsDay = priorDecisiveResultsDay;
		this.slotIndex = slotIndex;
		this.startedAtMs = startedAtMs;
		this.endedAtMs = endedAtMs;
	}

	public static VoidArenaMatchSessionRecord active(final String matchId, final String seasonId,
												 final int playerAId, final int playerBId,
												 final int playerARatingBefore,
												 final int playerBRatingBefore,
												 final boolean sameIp,
												 final boolean sameIpLocalExempt,
												 final int priorRatedResults30m,
												 final int priorDecisiveResultsDay,
												 final int slotIndex, final long startedAtMs) {
		return new VoidArenaMatchSessionRecord(matchId, seasonId, STATUS_ACTIVE, null,
			playerAId, playerBId, null, null,
			playerARatingBefore, null, playerBRatingBefore, null, 0, false,
			sameIp, sameIpLocalExempt, priorRatedResults30m, priorDecisiveResultsDay,
			slotIndex, startedAtMs, null);
	}

	public VoidArenaMatchSessionRecord settled(final String reason,
												final Integer winnerId, final Integer loserId,
												final int playerARatingAfter,
												final int playerBRatingAfter,
												final int ratingDelta,
												final boolean ratingApplied,
												final long endedAtMs) {
		return new VoidArenaMatchSessionRecord(matchId, seasonId, STATUS_SETTLED, reason,
			playerAId, playerBId, winnerId, loserId,
			playerARatingBefore, playerARatingAfter, playerBRatingBefore, playerBRatingAfter,
			ratingDelta, ratingApplied, sameIp, sameIpLocalExempt, priorRatedResults30m,
			priorDecisiveResultsDay, slotIndex, startedAtMs, endedAtMs);
	}

	public boolean isDecisive() {
		return REASON_DEATH.equals(resultReason) || REASON_FORFEIT.equals(resultReason);
	}

	public boolean isNeutral() {
		return REASON_TIMEOUT_DRAW.equals(resultReason)
			|| REASON_SERVER_SHUTDOWN_NO_CONTEST.equals(resultReason)
			|| REASON_SERVER_RESTART_NO_CONTEST.equals(resultReason);
	}
}
