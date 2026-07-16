package com.voidscape.duelproof;

import java.util.Arrays;

/**
 * Dependency-free SHA-256 and HMAC-SHA-256 used identically by every Voidscape runtime.
 */
public final class DuelProofCrypto {

	private static final int SHA256_BLOCK_BYTES = 64;

	private static final byte[] DOMAIN_CONTEXT =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/context");
	private static final byte[] DOMAIN_SERVER_COMMITMENT =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/commitment");
	private static final byte[] DOMAIN_MASTER_SEED =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/master-seed");
	private static final byte[] DOMAIN_CLIENT_COMMITMENT =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/client-commitment");
	private static final byte[] DOMAIN_FINAL_LOCK =
		DuelProofCodec.ascii("Voidscape/DuelProof/v1/final-lock");

	private static final int[] ROUND_CONSTANTS = {
		0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
		0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
		0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
		0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
		0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
		0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
		0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
		0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
		0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
		0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
		0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
		0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
		0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
		0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
		0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
		0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
	};

	private DuelProofCrypto() {
	}

	public static byte[] contextHash(byte[] canonicalContext) {
		if (canonicalContext == null) {
			throw new IllegalArgumentException("canonicalContext must not be null");
		}
		return sha256(DuelProofCodec.concat(
			DOMAIN_CONTEXT,
			DuelProofCodec.unsignedInt32(canonicalContext.length),
			canonicalContext
		));
	}

	public static byte[] serverCommitment(byte[] contextHash, byte[] serverSeed) {
		requireLength(contextHash, DuelProofSpec.HASH_BYTES, "contextHash");
		requireLength(serverSeed, DuelProofSpec.SEED_BYTES, "serverSeed");
		return sha256(DuelProofCodec.concat(DOMAIN_SERVER_COMMITMENT, contextHash, serverSeed));
	}

	/**
	 * Commits one participant to a private seed without revealing it. The ordinal is the
	 * participant's canonical player-id order (zero for the lower id, one for the higher id).
	 */
	public static byte[] clientCommitment(byte[] contextHash, byte[] serverCommitment,
									 int canonicalOrdinal, byte[] clientSeed) {
		requireLength(contextHash, DuelProofSpec.HASH_BYTES, "contextHash");
		requireLength(serverCommitment, DuelProofSpec.HASH_BYTES, "serverCommitment");
		requireLength(clientSeed, DuelProofSpec.SEED_BYTES, "clientSeed");
		if (canonicalOrdinal != 0 && canonicalOrdinal != 1) {
			throw new IllegalArgumentException("canonicalOrdinal must be 0 or 1");
		}
		return sha256(DuelProofCodec.concat(
			DOMAIN_CLIENT_COMMITMENT,
			contextHash,
			serverCommitment,
			DuelProofCodec.unsignedByte(canonicalOrdinal),
			clientSeed
		));
	}

	/**
	 * Hashes the complete pre-combat witness record acknowledged by both clients. No seed is
	 * included, so this record is safe to show before combat begins.
	 */
	public static byte[] finalLockHash(byte[] proofId, byte[] contextHash,
								   byte[] serverCommitment, int canonicalFirstPlayerId,
								   byte[] canonicalFirstCommitment, int canonicalSecondPlayerId,
								   byte[] canonicalSecondCommitment) {
		requireLength(proofId, DuelProofSpec.PROOF_ID_BYTES, "proofId");
		requireLength(contextHash, DuelProofSpec.HASH_BYTES, "contextHash");
		requireLength(serverCommitment, DuelProofSpec.HASH_BYTES, "serverCommitment");
		requireLength(canonicalFirstCommitment, DuelProofSpec.HASH_BYTES,
			"canonicalFirstCommitment");
		requireLength(canonicalSecondCommitment, DuelProofSpec.HASH_BYTES,
			"canonicalSecondCommitment");
		if (canonicalFirstPlayerId <= 0 || canonicalSecondPlayerId <= canonicalFirstPlayerId) {
			throw new IllegalArgumentException("player ids must be positive and canonically ordered");
		}
		return sha256(DuelProofCodec.concat(
			DOMAIN_FINAL_LOCK,
			DuelProofCodec.unsignedInt32(DuelProofSpec.PROTOCOL_VERSION),
			DuelProofCodec.unsignedInt32(DuelProofSpec.RNG_VERSION),
			DuelProofCodec.unsignedInt32(DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION),
			proofId,
			contextHash,
			serverCommitment,
			DuelProofCodec.unsignedInt32(canonicalFirstPlayerId),
			canonicalFirstCommitment,
			DuelProofCodec.unsignedInt32(canonicalSecondPlayerId),
			canonicalSecondCommitment
		));
	}

	/**
	 * Derives the duel stream key. Client seeds must be supplied in canonical player-id order.
	 */
	public static byte[] masterSeed(byte[] contextHash, byte[] serverSeed,
									byte[] canonicalClientSeedA, byte[] canonicalClientSeedB) {
		requireLength(contextHash, DuelProofSpec.HASH_BYTES, "contextHash");
		requireLength(serverSeed, DuelProofSpec.SEED_BYTES, "serverSeed");
		requireLength(canonicalClientSeedA, DuelProofSpec.SEED_BYTES, "canonicalClientSeedA");
		requireLength(canonicalClientSeedB, DuelProofSpec.SEED_BYTES, "canonicalClientSeedB");
		return hmacSha256(serverSeed, DuelProofCodec.concat(
			DOMAIN_MASTER_SEED,
			contextHash,
			canonicalClientSeedA,
			canonicalClientSeedB
		));
	}

	public static boolean constantTimeEquals(byte[] first, byte[] second) {
		if (first == null || second == null) {
			return false;
		}
		int difference = first.length ^ second.length;
		int sharedLength = Math.min(first.length, second.length);
		for (int i = 0; i < sharedLength; i++) {
			difference |= first[i] ^ second[i];
		}
		return difference == 0;
	}

	public static byte[] hmacSha256(byte[] key, byte[] message) {
		if (key == null || message == null) {
			throw new IllegalArgumentException("key and message must not be null");
		}
		byte[] normalizedKey = key.length > SHA256_BLOCK_BYTES
			? sha256(key)
			: Arrays.copyOf(key, key.length);
		byte[] innerPad = new byte[SHA256_BLOCK_BYTES];
		byte[] outerPad = new byte[SHA256_BLOCK_BYTES];
		for (int i = 0; i < SHA256_BLOCK_BYTES; i++) {
			byte keyByte = i < normalizedKey.length ? normalizedKey[i] : 0;
			innerPad[i] = (byte) (keyByte ^ 0x36);
			outerPad[i] = (byte) (keyByte ^ 0x5c);
		}
		byte[] innerHash = sha256(DuelProofCodec.concat(innerPad, message));
		byte[] result = sha256(DuelProofCodec.concat(outerPad, innerHash));
		Arrays.fill(normalizedKey, (byte) 0);
		Arrays.fill(innerPad, (byte) 0);
		Arrays.fill(outerPad, (byte) 0);
		Arrays.fill(innerHash, (byte) 0);
		return result;
	}

	public static byte[] sha256(byte[] message) {
		if (message == null) {
			throw new IllegalArgumentException("message must not be null");
		}
		long paddedLength = ((message.length + 9L + SHA256_BLOCK_BYTES - 1L)
			/ SHA256_BLOCK_BYTES) * SHA256_BLOCK_BYTES;
		if (paddedLength > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("message is too large");
		}
		byte[] padded = new byte[(int) paddedLength];
		System.arraycopy(message, 0, padded, 0, message.length);
		padded[message.length] = (byte) 0x80;
		long bitLength = ((long) message.length) * 8L;
		for (int i = 0; i < 8; i++) {
			padded[padded.length - 1 - i] = (byte) (bitLength >>> (i * 8));
		}

		int[] hash = {
			0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
			0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
		};
		int[] schedule = new int[64];
		for (int chunkOffset = 0; chunkOffset < padded.length; chunkOffset += SHA256_BLOCK_BYTES) {
			for (int i = 0; i < 16; i++) {
				int offset = chunkOffset + i * 4;
				schedule[i] = ((padded[offset] & 0xff) << 24)
					| ((padded[offset + 1] & 0xff) << 16)
					| ((padded[offset + 2] & 0xff) << 8)
					| (padded[offset + 3] & 0xff);
			}
			for (int i = 16; i < 64; i++) {
				int sigma0 = rotateRight(schedule[i - 15], 7)
					^ rotateRight(schedule[i - 15], 18)
					^ (schedule[i - 15] >>> 3);
				int sigma1 = rotateRight(schedule[i - 2], 17)
					^ rotateRight(schedule[i - 2], 19)
					^ (schedule[i - 2] >>> 10);
				schedule[i] = schedule[i - 16] + sigma0 + schedule[i - 7] + sigma1;
			}

			int a = hash[0];
			int b = hash[1];
			int c = hash[2];
			int d = hash[3];
			int e = hash[4];
			int f = hash[5];
			int g = hash[6];
			int h = hash[7];

			for (int i = 0; i < 64; i++) {
				int sum1 = rotateRight(e, 6) ^ rotateRight(e, 11) ^ rotateRight(e, 25);
				int choose = (e & f) ^ (~e & g);
				int temporary1 = h + sum1 + choose + ROUND_CONSTANTS[i] + schedule[i];
				int sum0 = rotateRight(a, 2) ^ rotateRight(a, 13) ^ rotateRight(a, 22);
				int majority = (a & b) ^ (a & c) ^ (b & c);
				int temporary2 = sum0 + majority;

				h = g;
				g = f;
				f = e;
				e = d + temporary1;
				d = c;
				c = b;
				b = a;
				a = temporary1 + temporary2;
			}

			hash[0] += a;
			hash[1] += b;
			hash[2] += c;
			hash[3] += d;
			hash[4] += e;
			hash[5] += f;
			hash[6] += g;
			hash[7] += h;
		}

		byte[] result = new byte[DuelProofSpec.HASH_BYTES];
		for (int i = 0; i < hash.length; i++) {
			result[i * 4] = (byte) (hash[i] >>> 24);
			result[i * 4 + 1] = (byte) (hash[i] >>> 16);
			result[i * 4 + 2] = (byte) (hash[i] >>> 8);
			result[i * 4 + 3] = (byte) hash[i];
		}
		Arrays.fill(padded, (byte) 0);
		Arrays.fill(schedule, 0);
		Arrays.fill(hash, 0);
		return result;
	}

	private static int rotateRight(int value, int distance) {
		return (value >>> distance) | (value << (32 - distance));
	}

	private static void requireLength(byte[] value, int expectedLength, String label) {
		if (value == null || value.length != expectedLength) {
			throw new IllegalArgumentException(label + " must contain " + expectedLength + " bytes");
		}
	}
}
