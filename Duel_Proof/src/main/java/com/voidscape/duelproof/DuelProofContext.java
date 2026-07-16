package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable canonical context recovered from independently verified terminal evidence. */
public final class DuelProofContext {

	private final int contextVersion;
	private final int protocolVersion;
	private final int rngVersion;
	private final int formulaVersion;
	private final byte[] proofId;
	private final int ruleMask;
	private final int recoilLimit;
	private final List<DuelProofContextParticipant> participants;

	DuelProofContext(final int contextVersion, final int protocolVersion,
					 final int rngVersion, final int formulaVersion, final byte[] proofId,
					 final int ruleMask, final int recoilLimit,
					 final List<DuelProofContextParticipant> participants) {
		this.contextVersion = contextVersion;
		this.protocolVersion = protocolVersion;
		this.rngVersion = rngVersion;
		this.formulaVersion = formulaVersion;
		this.proofId = Arrays.copyOf(proofId, proofId.length);
		this.ruleMask = ruleMask;
		this.recoilLimit = recoilLimit;
		this.participants = Collections.unmodifiableList(
			new ArrayList<DuelProofContextParticipant>(participants));
	}

	public int getContextVersion() { return contextVersion; }
	public int getProtocolVersion() { return protocolVersion; }
	public int getRngVersion() { return rngVersion; }
	public int getFormulaVersion() { return formulaVersion; }
	public byte[] getProofId() { return Arrays.copyOf(proofId, proofId.length); }
	public int getRuleMask() { return ruleMask; }
	public int getRecoilLimit() { return recoilLimit; }
	public List<DuelProofContextParticipant> getParticipants() { return participants; }

	public DuelProofContextParticipant getParticipant(final int ordinal) {
		if (ordinal != 0 && ordinal != 1) {
			throw new IllegalArgumentException("ordinal must be 0 or 1");
		}
		return participants.get(ordinal);
	}
}
