package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One globally ordered covered melee swing in a duel replay. */
public final class DuelProofMeleeSwing {

	private final int swingNumber;
	private final int actorOrdinal;
	private final DuelProofMeleeInput input;
	private final DuelProofMeleeResult result;
	private final boolean momentumBefore;
	private final boolean momentumAfter;
	private final long candidateStart;
	private final long candidateEnd;
	private final List<DuelProofDraw> draws;

	DuelProofMeleeSwing(final int swingNumber, final int actorOrdinal,
						final DuelProofMeleeInput input, final DuelProofMeleeResult result,
						final boolean momentumBefore, final boolean momentumAfter,
						final long candidateStart, final long candidateEnd,
						final List<DuelProofDraw> draws) {
		if (swingNumber <= 0 || (actorOrdinal != 0 && actorOrdinal != 1)
			|| input == null || result == null || candidateStart < 0 || candidateEnd <= candidateStart
			|| draws == null || draws.isEmpty()) {
			throw new IllegalArgumentException("invalid duel proof melee swing");
		}
		this.swingNumber = swingNumber;
		this.actorOrdinal = actorOrdinal;
		this.input = input;
		this.result = result;
		this.momentumBefore = momentumBefore;
		this.momentumAfter = momentumAfter;
		this.candidateStart = candidateStart;
		this.candidateEnd = candidateEnd;
		this.draws = Collections.unmodifiableList(new ArrayList<DuelProofDraw>(draws));
	}

	public int getSwingNumber() { return swingNumber; }
	public int getActorOrdinal() { return actorOrdinal; }
	public DuelProofMeleeInput getInput() { return input; }
	public DuelProofMeleeResult getResult() { return result; }
	public boolean hadMomentumBefore() { return momentumBefore; }
	public boolean hasMomentumAfter() { return momentumAfter; }
	public long getCandidateStart() { return candidateStart; }
	public long getCandidateEnd() { return candidateEnd; }
	public List<DuelProofDraw> getDraws() { return draws; }

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DuelProofMeleeSwing)) {
			return false;
		}
		final DuelProofMeleeSwing swing = (DuelProofMeleeSwing) other;
		return swingNumber == swing.swingNumber && actorOrdinal == swing.actorOrdinal
			&& momentumBefore == swing.momentumBefore && momentumAfter == swing.momentumAfter
			&& candidateStart == swing.candidateStart && candidateEnd == swing.candidateEnd
			&& input.equals(swing.input) && result.equals(swing.result) && draws.equals(swing.draws);
	}

	@Override
	public int hashCode() {
		int resultHash = swingNumber;
		resultHash = 31 * resultHash + actorOrdinal;
		resultHash = 31 * resultHash + input.hashCode();
		resultHash = 31 * resultHash + result.hashCode();
		resultHash = 31 * resultHash + (momentumBefore ? 1 : 0);
		resultHash = 31 * resultHash + (momentumAfter ? 1 : 0);
		resultHash = 31 * resultHash + (int) (candidateStart ^ (candidateStart >>> 32));
		resultHash = 31 * resultHash + (int) (candidateEnd ^ (candidateEnd >>> 32));
		resultHash = 31 * resultHash + draws.hashCode();
		return resultHash;
	}
}
