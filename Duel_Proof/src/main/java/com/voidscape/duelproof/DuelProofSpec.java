package com.voidscape.duelproof;

/**
 * Versioned constants and canonical ordering rules for stake-duel proofs.
 *
 * <p>This module intentionally has no server, client, JCA, or platform dependencies so the
 * exact same byte-level rules compile under Java 8, Android, and TeaVM.</p>
 */
public final class DuelProofSpec {

	public static final int PROTOCOL_VERSION = 1;
	public static final int RNG_VERSION = 1;
	public static final int CLASSIC_MELEE_FORMULA_VERSION = 1;
	public static final int CONTEXT_VERSION = 3;
	public static final int WITNESS_VERSION = 2;

	public static final int HASH_BYTES = 32;
	public static final int SEED_BYTES = 32;
	public static final int PROOF_ID_BYTES = 16;
	public static final int MAX_WITNESS_SWINGS = 4096;
	public static final int MAX_WITNESS_CONTEXT_BYTES = 65535;
	public static final int MAX_WITNESS_BYTES = 256 * 1024;

	public static final int DUEL_RULE_NO_MAGIC = 1 << 1;
	public static final int DUEL_RULE_NO_PRAYER = 1 << 2;

	public static final int TERMINAL_CAUSE_DIRECT_MELEE = 1;
	public static final int TERMINAL_CAUSE_RECOIL = 2;

	public static final int DRAW_STARTER = 1;
	public static final int DRAW_ACCURACY = 2;
	public static final int DRAW_ATTACK_CAPE_ACTIVATION = 3;
	public static final int DRAW_ATTACK_CAPE_ACCURACY = 4;
	public static final int DRAW_DAMAGE = 5;
	public static final int DRAW_MOMENTUM_DAMAGE = 6;
	public static final int DRAW_DEFENCE_CAPE_ACTIVATION = 7;
	public static final int DRAW_STRENGTH_CAPE_ACTIVATION = 8;

	public static final int COMBAT_STYLE_CONTROLLED = 0;
	public static final int COMBAT_STYLE_AGGRESSIVE = 1;
	public static final int COMBAT_STYLE_ACCURATE = 2;
	public static final int COMBAT_STYLE_DEFENSIVE = 3;

	public static final int PRAYER_TIER_NONE = 0;
	public static final int PRAYER_TIER_LOW = 1;
	public static final int PRAYER_TIER_MIDDLE = 2;
	public static final int PRAYER_TIER_HIGH = 3;

	private DuelProofSpec() {
	}

	/**
	 * Returns the lower database id, which is v1's canonical first participant.
	 */
	public static int canonicalFirstPlayerId(int firstPlayerId, int secondPlayerId) {
		validatePlayerIds(firstPlayerId, secondPlayerId);
		return Math.min(firstPlayerId, secondPlayerId);
	}

	/**
	 * Selects the lower-level player, using the committed starter bit only for an exact level tie.
	 * On a tie, bit 0 selects the lower database id and bit 1 selects the higher database id.
	 */
	public static int starterPlayerId(int firstPlayerId, int firstCombatLevel,
								  int secondPlayerId, int secondCombatLevel,
								  int starterBit) {
		validatePlayerIds(firstPlayerId, secondPlayerId);
		validateCombatLevels(firstCombatLevel, secondCombatLevel);
		if (starterBit != 0 && starterBit != 1) {
			throw new IllegalArgumentException("starterBit must be 0 or 1");
		}
		if (firstCombatLevel < secondCombatLevel) {
			return firstPlayerId;
		}
		if (secondCombatLevel < firstCombatLevel) {
			return secondPlayerId;
		}
		int lower = Math.min(firstPlayerId, secondPlayerId);
		int higher = Math.max(firstPlayerId, secondPlayerId);
		return starterBit == 0 ? lower : higher;
	}

	private static void validateCombatLevels(int firstCombatLevel, int secondCombatLevel) {
		if (firstCombatLevel <= 0 || secondCombatLevel <= 0) {
			throw new IllegalArgumentException("combat levels must be positive");
		}
	}

	private static void validatePlayerIds(int firstPlayerId, int secondPlayerId) {
		if (firstPlayerId <= 0 || secondPlayerId <= 0) {
			throw new IllegalArgumentException("player ids must be positive");
		}
		if (firstPlayerId == secondPlayerId) {
			throw new IllegalArgumentException("duel participants must be distinct");
		}
	}
}
