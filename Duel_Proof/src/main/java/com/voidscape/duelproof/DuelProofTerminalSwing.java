package com.voidscape.duelproof;

/** One globally ordered formula input in a terminal witness. */
public final class DuelProofTerminalSwing {

	private final int swingNumber;
	private final int actorOrdinal;
	private final DuelProofMeleeInput input;

	public DuelProofTerminalSwing(final int swingNumber, final int actorOrdinal,
								  final DuelProofMeleeInput input) {
		if (swingNumber <= 0) {
			throw new IllegalArgumentException("swingNumber must be positive");
		}
		if (actorOrdinal != 0 && actorOrdinal != 1) {
			throw new IllegalArgumentException("actorOrdinal must be 0 or 1");
		}
		if (input == null) {
			throw new IllegalArgumentException("input must not be null");
		}
		this.swingNumber = swingNumber;
		this.actorOrdinal = actorOrdinal;
		this.input = input;
	}

	public int getSwingNumber() {
		return swingNumber;
	}

	public int getActorOrdinal() {
		return actorOrdinal;
	}

	public DuelProofMeleeInput getInput() {
		return input;
	}
}
