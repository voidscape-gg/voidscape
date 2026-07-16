package com.voidscape.duelproof;

import java.util.Arrays;

/**
 * Deterministic HMAC-SHA-256 counter stream for covered duel randomness.
 *
 * <p>The class does not obtain entropy. Its 32-byte key must be derived by the proof handshake.</p>
 */
public final class DuelProofRng {

	private static final long UNSIGNED_INT_MODULUS = 0x100000000L;
	private static final double UNIT_53_DIVISOR = 9007199254740992.0;
	private static final byte[] DOMAIN_RNG =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/rng-block");

	private final byte[] masterSeed;
	private byte[] block = new byte[0];
	private int blockOffset;
	private long blockCounter;
	private long candidateDrawCount;
	private boolean destroyed;

	public DuelProofRng(byte[] masterSeed) {
		if (masterSeed == null || masterSeed.length != DuelProofSpec.SEED_BYTES) {
			throw new IllegalArgumentException("masterSeed must contain 32 bytes");
		}
		this.masterSeed = Arrays.copyOf(masterSeed, masterSeed.length);
	}

	/** Returns one unsigned 32-bit candidate in a Java long. */
	public long nextUnsignedInt32() {
		requireAvailable();
		if (blockOffset + 4 > block.length) {
			refillBlock();
		}
		long value = ((long) (block[blockOffset] & 0xff) << 24)
			| ((long) (block[blockOffset + 1] & 0xff) << 16)
			| ((long) (block[blockOffset + 2] & 0xff) << 8)
			| (long) (block[blockOffset + 3] & 0xff);
		blockOffset += 4;
		candidateDrawCount++;
		return value;
	}

	/** Returns an unbiased value in {@code [0, bound)} using rejection sampling. */
	public int nextInt(int bound) {
		requireAvailable();
		if (bound <= 0) {
			throw new IllegalArgumentException("bound must be positive");
		}
		long limit = UNSIGNED_INT_MODULUS - (UNSIGNED_INT_MODULUS % bound);
		long candidate;
		do {
			candidate = nextUnsignedInt32();
		} while (candidate >= limit);
		return (int) (candidate % bound);
	}

	public int nextCoin() {
		return nextInt(2);
	}

	/** Returns a uniform integer in {@code [0, 2^53)} using exactly two candidates. */
	public long nextUnit53() {
		requireAvailable();
		long first = nextUnsignedInt32();
		long second = nextUnsignedInt32();
		return (first << 21) | (second >>> 11);
	}

	public double nextUnitDouble() {
		return nextUnit53() / UNIT_53_DIVISOR;
	}

	public long getCandidateDrawCount() {
		requireAvailable();
		return candidateDrawCount;
	}

	public long getGeneratedBlockCount() {
		requireAvailable();
		return blockCounter;
	}

	/** Wipes the copied stream key and rejects every subsequent operation. */
	public void destroy() {
		if (destroyed) {
			return;
		}
		Arrays.fill(masterSeed, (byte) 0);
		Arrays.fill(block, (byte) 0);
		block = new byte[0];
		blockOffset = 0;
		blockCounter = 0;
		candidateDrawCount = 0;
		destroyed = true;
	}

	private void refillBlock() {
		if (blockCounter > 0xffffffffL) {
			throw new IllegalStateException("duel RNG exhausted its v1 counter space");
		}
		if (block.length > 0) {
			Arrays.fill(block, (byte) 0);
		}
		block = DuelProofCrypto.hmacSha256(masterSeed, DuelProofCodec.concat(
			DOMAIN_RNG,
			DuelProofCodec.unsignedInt32(blockCounter)
		));
		blockCounter++;
		blockOffset = 0;
	}

	private void requireAvailable() {
		if (destroyed) {
			throw new IllegalStateException("duel RNG has been destroyed");
		}
	}
}
