package com.openrsc.server.database.struct;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable, defensive-copy database representation of one duel-proof attempt. */
public final class DuelProofAttemptRecord {
	public static final String STATUS_SERVER_COMMITTED = "SERVER_COMMITTED";
	public static final String STATUS_CLIENT_COMMITTED = "CLIENT_COMMITTED";
	public static final String STATUS_CLIENT_REVEALED = "CLIENT_REVEALED";
	public static final String STATUS_LOCKED = "LOCKED";
	public static final String STATUS_COMBAT = "COMBAT";
	public static final String STATUS_VERIFIED = "VERIFIED";
	public static final String STATUS_ABORTED = "ABORTED";

	public final String proofId;
	public final long createdAt;
	private final Long duelId;
	private final int protocolVersion;
	private final int rngVersion;
	private final int formulaVersion;
	private final int contextVersion;
	private final String status;
	private final long updatedAtMs;
	private final Long lockedAtMs;
	private final Long startedAtMs;
	private final Long finishedAtMs;
	private final String abortReason;
	private final byte[] contextBytes;
	private final byte[] contextHash;
	private final byte[] serverCommitment;
	private final byte[] serverSeed;
	private final byte[] finalLockHash;
	private final List<DuelProofAttemptParticipant> participants;

	/** Convenience constructor for the only state accepted by the initial insert API. */
	public DuelProofAttemptRecord(final String proofId, final int protocolVersion, final int rngVersion,
								  final int formulaVersion, final int contextVersion, final long createdAtMs,
								  final byte[] contextBytes, final byte[] contextHash,
								  final byte[] serverCommitment, final byte[] serverSeed,
								  final List<DuelProofAttemptParticipant> participants) {
		this(proofId, null, protocolVersion, rngVersion, formulaVersion, contextVersion,
			STATUS_SERVER_COMMITTED, createdAtMs, createdAtMs, null, null, null, null,
			contextBytes, contextHash, serverCommitment, serverSeed, null, participants);
	}

	public DuelProofAttemptRecord(final String proofId, final Long duelId, final int protocolVersion,
								  final int rngVersion, final int formulaVersion, final int contextVersion,
								  final String status, final long createdAtMs, final long updatedAtMs,
								  final Long lockedAtMs, final Long startedAtMs, final Long finishedAtMs,
								  final String abortReason, final byte[] contextBytes, final byte[] contextHash,
								  final byte[] serverCommitment, final byte[] serverSeed,
								  final byte[] finalLockHash,
								  final List<DuelProofAttemptParticipant> participants) {
		this.proofId = proofId;
		this.duelId = duelId;
		this.protocolVersion = protocolVersion;
		this.rngVersion = rngVersion;
		this.formulaVersion = formulaVersion;
		this.contextVersion = contextVersion;
		this.status = status;
		this.createdAt = createdAtMs;
		this.updatedAtMs = updatedAtMs;
		this.lockedAtMs = lockedAtMs;
		this.startedAtMs = startedAtMs;
		this.finishedAtMs = finishedAtMs;
		this.abortReason = abortReason;
		this.contextBytes = copy(contextBytes);
		this.contextHash = copy(contextHash);
		this.serverCommitment = copy(serverCommitment);
		this.serverSeed = copy(serverSeed);
		this.finalLockHash = copy(finalLockHash);
		this.participants = participants == null
			? Collections.<DuelProofAttemptParticipant>emptyList()
			: Collections.unmodifiableList(new ArrayList<>(participants));
	}

	public String getProofId() { return proofId; }
	public Long getDuelId() { return duelId; }
	public int getProtocolVersion() { return protocolVersion; }
	public int getRngVersion() { return rngVersion; }
	public int getFormulaVersion() { return formulaVersion; }
	public int getContextVersion() { return contextVersion; }
	public String getStatus() { return status; }
	public long getCreatedAtMs() { return createdAt; }
	public long getUpdatedAtMs() { return updatedAtMs; }
	public Long getLockedAtMs() { return lockedAtMs; }
	public Long getStartedAtMs() { return startedAtMs; }
	public Long getFinishedAtMs() { return finishedAtMs; }
	public String getAbortReason() { return abortReason; }
	public byte[] getContextBytes() { return copy(contextBytes); }
	public byte[] getContextHash() { return copy(contextHash); }
	public byte[] getServerCommitment() { return copy(serverCommitment); }
	public byte[] getServerSeed() { return copy(serverSeed); }
	public byte[] getFinalLockHash() { return copy(finalLockHash); }
	public List<DuelProofAttemptParticipant> getParticipants() { return participants; }

	private static byte[] copy(final byte[] value) {
		return value == null ? null : Arrays.copyOf(value, value.length);
	}
}
