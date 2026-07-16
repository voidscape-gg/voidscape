package com.voidscape.duelproof;

/** One semantic draw from the committed duel stream. */
public final class DuelProofDraw {

	private final int type;
	private final int bound;
	private final long value;
	private final long candidateStart;
	private final long candidateEnd;

	DuelProofDraw(final int type, final int bound, final long value,
				  final long candidateStart, final long candidateEnd) {
		if (type < DuelProofSpec.DRAW_STARTER || type > DuelProofSpec.DRAW_STRENGTH_CAPE_ACTIVATION) {
			throw new IllegalArgumentException("unknown duel proof draw type");
		}
		if (bound < 0 || value < 0 || candidateStart < 0 || candidateEnd <= candidateStart) {
			throw new IllegalArgumentException("invalid duel proof draw");
		}
		if (bound > 0 && value >= bound) {
			throw new IllegalArgumentException("bounded draw value is outside its bound");
		}
		this.type = type;
		this.bound = bound;
		this.value = value;
		this.candidateStart = candidateStart;
		this.candidateEnd = candidateEnd;
	}

	public int getType() {
		return type;
	}

	/** Zero for a 53-bit unit draw, otherwise the exclusive integer bound. */
	public int getBound() {
		return bound;
	}

	/** The raw 53-bit value for unit draws, or the bounded integer result. */
	public long getValue() {
		return value;
	}

	public long getCandidateStart() {
		return candidateStart;
	}

	public long getCandidateEnd() {
		return candidateEnd;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof DuelProofDraw)) {
			return false;
		}
		final DuelProofDraw draw = (DuelProofDraw) other;
		return type == draw.type && bound == draw.bound && value == draw.value
			&& candidateStart == draw.candidateStart && candidateEnd == draw.candidateEnd;
	}

	@Override
	public int hashCode() {
		int result = type;
		result = 31 * result + bound;
		result = 31 * result + (int) (value ^ (value >>> 32));
		result = 31 * result + (int) (candidateStart ^ (candidateStart >>> 32));
		result = 31 * result + (int) (candidateEnd ^ (candidateEnd >>> 32));
		return result;
	}
}
