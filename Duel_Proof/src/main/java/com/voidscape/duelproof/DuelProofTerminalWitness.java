package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable, self-contained terminal evidence for one completed covered duel. */
public final class DuelProofTerminalWitness {

	private final int witnessVersion;
	private final int protocolVersion;
	private final int rngVersion;
	private final int formulaVersion;
	private final int contextVersion;
	private final byte[] proofId;
	private final byte[] contextBytes;
	private final byte[] contextHash;
	private final byte[] serverCommitment;
	private final byte[] serverSeed;
	private final byte[] finalLockHash;
	private final List<DuelProofTerminalParticipant> participants;
	private final int starterOrdinal;
	private final int winnerOrdinal;
	private final int terminalCause;
	private final long finishedAtMs;
	private final long finalCandidateDrawCount;
	private final List<DuelProofTerminalSwing> swings;

	public DuelProofTerminalWitness(final int witnessVersion, final int protocolVersion,
									final int rngVersion, final int formulaVersion,
									final int contextVersion, final byte[] proofId,
									final byte[] contextBytes, final byte[] contextHash,
									final byte[] serverCommitment, final byte[] serverSeed,
									final byte[] finalLockHash,
									final List<DuelProofTerminalParticipant> participants,
									final int starterOrdinal, final int winnerOrdinal,
									final int terminalCause, final long finishedAtMs,
									final long finalCandidateDrawCount,
									final List<DuelProofTerminalSwing> swings) {
		requireVersion(witnessVersion, DuelProofSpec.WITNESS_VERSION, "witnessVersion");
		requireVersion(protocolVersion, DuelProofSpec.PROTOCOL_VERSION, "protocolVersion");
		requireVersion(rngVersion, DuelProofSpec.RNG_VERSION, "rngVersion");
		requireVersion(formulaVersion, DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION,
			"formulaVersion");
		requireVersion(contextVersion, DuelProofSpec.CONTEXT_VERSION, "contextVersion");
		requireLength(proofId, DuelProofSpec.PROOF_ID_BYTES, "proofId");
		if (contextBytes == null || contextBytes.length == 0
			|| contextBytes.length > DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES) {
			throw new IllegalArgumentException("contextBytes must contain 1..65535 bytes");
		}
		requireLength(contextHash, DuelProofSpec.HASH_BYTES, "contextHash");
		requireLength(serverCommitment, DuelProofSpec.HASH_BYTES, "serverCommitment");
		requireLength(serverSeed, DuelProofSpec.SEED_BYTES, "serverSeed");
		requireLength(finalLockHash, DuelProofSpec.HASH_BYTES, "finalLockHash");
		if (participants == null || participants.size() != 2
			|| participants.get(0) == null || participants.get(1) == null) {
			throw new IllegalArgumentException("witness must contain exactly two participants");
		}
		final DuelProofTerminalParticipant first = participants.get(0);
		final DuelProofTerminalParticipant second = participants.get(1);
		if (first.getOrdinal() != 0 || second.getOrdinal() != 1
			|| first.getPlayerId() >= second.getPlayerId()) {
			throw new IllegalArgumentException("participants must use canonical ordinal and player-id order");
		}
		requireOrdinal(starterOrdinal, "starterOrdinal");
		requireOrdinal(winnerOrdinal, "winnerOrdinal");
		if (terminalCause != DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE
			&& terminalCause != DuelProofSpec.TERMINAL_CAUSE_RECOIL) {
			throw new IllegalArgumentException("terminalCause is unsupported");
		}
		if (finishedAtMs <= 0) {
			throw new IllegalArgumentException("finishedAtMs must be positive");
		}
		if (finalCandidateDrawCount <= 0) {
			throw new IllegalArgumentException("finalCandidateDrawCount must be positive");
		}
		if (swings == null || swings.isEmpty()
			|| swings.size() > DuelProofSpec.MAX_WITNESS_SWINGS) {
			throw new IllegalArgumentException("witness must contain 1..4096 swings");
		}
		final ArrayList<DuelProofTerminalSwing> copiedSwings =
			new ArrayList<DuelProofTerminalSwing>(swings.size());
		for (int index = 0; index < swings.size(); index++) {
			final DuelProofTerminalSwing swing = swings.get(index);
			if (swing == null || swing.getSwingNumber() != index + 1) {
				throw new IllegalArgumentException("witness swing numbers must be sequential");
			}
			copiedSwings.add(swing);
		}

		this.witnessVersion = witnessVersion;
		this.protocolVersion = protocolVersion;
		this.rngVersion = rngVersion;
		this.formulaVersion = formulaVersion;
		this.contextVersion = contextVersion;
		this.proofId = Arrays.copyOf(proofId, proofId.length);
		this.contextBytes = Arrays.copyOf(contextBytes, contextBytes.length);
		this.contextHash = Arrays.copyOf(contextHash, contextHash.length);
		this.serverCommitment = Arrays.copyOf(serverCommitment, serverCommitment.length);
		this.serverSeed = Arrays.copyOf(serverSeed, serverSeed.length);
		this.finalLockHash = Arrays.copyOf(finalLockHash, finalLockHash.length);
		this.participants = Collections.unmodifiableList(
			new ArrayList<DuelProofTerminalParticipant>(participants));
		this.starterOrdinal = starterOrdinal;
		this.winnerOrdinal = winnerOrdinal;
		this.terminalCause = terminalCause;
		this.finishedAtMs = finishedAtMs;
		this.finalCandidateDrawCount = finalCandidateDrawCount;
		this.swings = Collections.unmodifiableList(copiedSwings);
	}

	/** Constructs a witness using every currently supported proof format version. */
	public static DuelProofTerminalWitness createV2(final byte[] proofId,
											final byte[] contextBytes, final byte[] contextHash,
													final byte[] serverCommitment, final byte[] serverSeed,
													final byte[] finalLockHash,
													final List<DuelProofTerminalParticipant> participants,
													final int starterOrdinal, final int winnerOrdinal,
													final int terminalCause, final long finishedAtMs,
													final long finalCandidateDrawCount,
													final List<DuelProofTerminalSwing> swings) {
		return new DuelProofTerminalWitness(DuelProofSpec.WITNESS_VERSION,
			DuelProofSpec.PROTOCOL_VERSION, DuelProofSpec.RNG_VERSION,
			DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION, DuelProofSpec.CONTEXT_VERSION,
			proofId, contextBytes, contextHash, serverCommitment, serverSeed, finalLockHash,
			participants, starterOrdinal, winnerOrdinal, terminalCause, finishedAtMs,
			finalCandidateDrawCount, swings);
	}

	public int getWitnessVersion() { return witnessVersion; }
	public int getProtocolVersion() { return protocolVersion; }
	public int getRngVersion() { return rngVersion; }
	public int getFormulaVersion() { return formulaVersion; }
	public int getContextVersion() { return contextVersion; }
	public byte[] getProofId() { return Arrays.copyOf(proofId, proofId.length); }
	public byte[] getContextBytes() { return Arrays.copyOf(contextBytes, contextBytes.length); }
	public byte[] getContextHash() { return Arrays.copyOf(contextHash, contextHash.length); }
	public byte[] getServerCommitment() {
		return Arrays.copyOf(serverCommitment, serverCommitment.length);
	}
	public byte[] getServerSeed() { return Arrays.copyOf(serverSeed, serverSeed.length); }
	public byte[] getFinalLockHash() { return Arrays.copyOf(finalLockHash, finalLockHash.length); }
	public List<DuelProofTerminalParticipant> getParticipants() { return participants; }
	public DuelProofTerminalParticipant getParticipant(final int ordinal) {
		requireOrdinal(ordinal, "ordinal");
		return participants.get(ordinal);
	}
	public int getStarterOrdinal() { return starterOrdinal; }
	public int getWinnerOrdinal() { return winnerOrdinal; }
	public int getTerminalCause() { return terminalCause; }
	public long getFinishedAtMs() { return finishedAtMs; }
	public long getFinalCandidateDrawCount() { return finalCandidateDrawCount; }
	public List<DuelProofTerminalSwing> getSwings() { return swings; }

	private static void requireVersion(final int actual, final int expected, final String label) {
		if (actual != expected) {
			throw new IllegalArgumentException(label + " is unsupported");
		}
	}

	private static void requireOrdinal(final int ordinal, final String label) {
		if (ordinal != 0 && ordinal != 1) {
			throw new IllegalArgumentException(label + " must be 0 or 1");
		}
	}

	private static void requireLength(final byte[] value, final int expected, final String label) {
		if (value == null || value.length != expected) {
			throw new IllegalArgumentException(label + " must contain " + expected + " bytes");
		}
	}
}
