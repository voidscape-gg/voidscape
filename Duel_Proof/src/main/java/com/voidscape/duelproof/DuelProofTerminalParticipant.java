package com.voidscape.duelproof;

import java.util.Arrays;

/** One canonical participant and the complete commit/reveal acknowledgement witness. */
public final class DuelProofTerminalParticipant {

	private final int ordinal;
	private final int playerId;
	private final byte[] commitment;
	private final byte[] seed;
	private final byte[] lockAck;

	public DuelProofTerminalParticipant(final int ordinal, final int playerId,
										final byte[] commitment, final byte[] seed,
										final byte[] lockAck) {
		if (ordinal != 0 && ordinal != 1) {
			throw new IllegalArgumentException("participant ordinal must be 0 or 1");
		}
		if (playerId <= 0) {
			throw new IllegalArgumentException("participant playerId must be positive");
		}
		requireLength(commitment, DuelProofSpec.HASH_BYTES, "participant commitment");
		requireLength(seed, DuelProofSpec.SEED_BYTES, "participant seed");
		requireLength(lockAck, DuelProofSpec.HASH_BYTES, "participant lockAck");
		this.ordinal = ordinal;
		this.playerId = playerId;
		this.commitment = Arrays.copyOf(commitment, commitment.length);
		this.seed = Arrays.copyOf(seed, seed.length);
		this.lockAck = Arrays.copyOf(lockAck, lockAck.length);
	}

	public int getOrdinal() {
		return ordinal;
	}

	public int getPlayerId() {
		return playerId;
	}

	public byte[] getCommitment() {
		return Arrays.copyOf(commitment, commitment.length);
	}

	public byte[] getSeed() {
		return Arrays.copyOf(seed, seed.length);
	}

	public byte[] getLockAck() {
		return Arrays.copyOf(lockAck, lockAck.length);
	}

	private static void requireLength(final byte[] value, final int expected, final String label) {
		if (value == null || value.length != expected) {
			throw new IllegalArgumentException(label + " must contain " + expected + " bytes");
		}
	}
}
