package com.openrsc.server.database.struct;

import java.util.Arrays;

/**
 * Immutable, defensive-copy database representation of a terminal duel-proof witness.
 */
public final class DuelProofWitnessRecord {
	private final String proofId;
	private final int witnessVersion;
	private final byte[] witnessBytes;
	private final byte[] witnessHash32;
	private final int starterOrdinal;
	private final int swingCount;
	private final int winnerPlayerId;
	private final String terminalCause;
	private final long finishedAtMs;

	public DuelProofWitnessRecord(final String proofId, final int witnessVersion,
								  final byte[] witnessBytes, final byte[] witnessHash32,
								  final int starterOrdinal, final int swingCount,
								  final int winnerPlayerId, final String terminalCause,
								  final long finishedAtMs) {
		this.proofId = proofId;
		this.witnessVersion = witnessVersion;
		this.witnessBytes = copy(witnessBytes);
		this.witnessHash32 = copy(witnessHash32);
		this.starterOrdinal = starterOrdinal;
		this.swingCount = swingCount;
		this.winnerPlayerId = winnerPlayerId;
		this.terminalCause = terminalCause;
		this.finishedAtMs = finishedAtMs;
	}

	public String getProofId() { return proofId; }
	public int getWitnessVersion() { return witnessVersion; }
	public byte[] getWitnessBytes() { return copy(witnessBytes); }
	public byte[] getWitnessHash32() { return copy(witnessHash32); }
	public int getStarterOrdinal() { return starterOrdinal; }
	public int getSwingCount() { return swingCount; }
	public int getWinnerPlayerId() { return winnerPlayerId; }
	public String getTerminalCause() { return terminalCause; }
	public long getFinishedAtMs() { return finishedAtMs; }

	private static byte[] copy(final byte[] value) {
		return value == null ? null : Arrays.copyOf(value, value.length);
	}
}
