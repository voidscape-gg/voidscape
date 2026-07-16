package com.voidscape.duelproof;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Independent verifier for a complete, terminal classic-melee duel witness. */
public final class DuelProofTerminalVerifier {

	private static final byte[] CONTEXT_MAGIC = DuelProofCodec.ascii("VSDPCTX3");
	private static final int CONTEXT_RULE_MASK = 0x0f;
	private static final int CONTEXT_V3_EQUIPMENT_SLOTS = 14;
	private static final int CONTEXT_V3_MAX_STAKE_SLOTS = 12;
	private static final int CONTEXT_V3_RING_SLOT = 13;
	private static final int RING_OF_RECOIL_ITEM_ID = 1314;

	private static final int ATTACK_PRAYER_LOW_LEVEL = 7;
	private static final int ATTACK_PRAYER_MIDDLE_LEVEL = 16;
	private static final int ATTACK_PRAYER_HIGH_LEVEL = 34;
	private static final int STRENGTH_PRAYER_LOW_LEVEL = 4;
	private static final int STRENGTH_PRAYER_MIDDLE_LEVEL = 13;
	private static final int STRENGTH_PRAYER_HIGH_LEVEL = 31;
	private static final int DEFENCE_PRAYER_LOW_LEVEL = 1;
	private static final int DEFENCE_PRAYER_MIDDLE_LEVEL = 10;
	private static final int DEFENCE_PRAYER_HIGH_LEVEL = 28;

	private DuelProofTerminalVerifier() {
	}

	/** Strictly decodes a canonical v3 context for pre-combat client validation. */
	public static DuelProofContext verifyContext(final byte[] canonicalContextBytes) {
		final ContextReader input = new ContextReader(canonicalContextBytes);
		input.requireBytes(CONTEXT_MAGIC, "context magic");
		final int contextVersion = input.readInt("context version");
		final int protocolVersion = input.readInt("context protocol version");
		final int rngVersion = input.readInt("context RNG version");
		final int formulaVersion = input.readInt("context formula version");
		if (contextVersion != DuelProofSpec.CONTEXT_VERSION
			|| protocolVersion != DuelProofSpec.PROTOCOL_VERSION
			|| rngVersion != DuelProofSpec.RNG_VERSION
			|| formulaVersion != DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION) {
			throw invalid("context uses an unsupported version");
		}
		final byte[] proofId = input.readBytes(DuelProofSpec.PROOF_ID_BYTES, "context proof id");
		final int ruleMask = input.readUnsignedByte("context rule mask");
		if ((ruleMask & ~CONTEXT_RULE_MASK) != 0) {
			throw invalid("context rule mask contains reserved bits");
		}
		final int recoilLimit = input.readInt("context recoil limit");
		if (recoilLimit <= 0) {
			throw invalid("context recoil limit must be positive");
		}
		final List<DuelProofContextParticipant> participants =
			new ArrayList<DuelProofContextParticipant>(2);
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			participants.add(parseContextParticipant(input, ordinal, recoilLimit));
		}
		if (participants.get(0).getPlayerId() <= 0
			|| participants.get(1).getPlayerId() <= participants.get(0).getPlayerId()) {
			throw invalid("context participants are not in canonical player-id order");
		}
		if ((ruleMask & DuelProofSpec.DUEL_RULE_NO_PRAYER) != 0
			&& (participants.get(0).getPrayerMask() != 0
				|| participants.get(1).getPrayerMask() != 0)) {
			throw invalid("No Prayer context must begin with prayers disabled");
		}
		input.requireFinished();
		return new DuelProofContext(contextVersion, protocolVersion, rngVersion,
			formulaVersion, proofId, ruleMask, recoilLimit, participants);
	}

	/**
	 * Decodes and verifies every commitment, reveal, stream draw, static input, and terminal
	 * invariant. Invalid or unsupported evidence is rejected with IllegalArgumentException.
	 */
	public static Verification verify(final byte[] canonicalWitnessBytes) {
		final DuelProofTerminalWitness witness =
			DuelProofTerminalWitnessCodec.decode(canonicalWitnessBytes);
		final byte[] reencoded = DuelProofTerminalWitnessCodec.encode(witness);
		if (!DuelProofCrypto.constantTimeEquals(canonicalWitnessBytes, reencoded)) {
			throw invalid("terminal witness is not canonically encoded");
		}

		final DuelProofContext context = verifyContext(witness.getContextBytes());
		if (context.getContextVersion() != witness.getContextVersion()
			|| context.getProtocolVersion() != witness.getProtocolVersion()
			|| context.getRngVersion() != witness.getRngVersion()
			|| context.getFormulaVersion() != witness.getFormulaVersion()) {
			throw invalid("context and witness versions do not match");
		}
		if (!DuelProofCrypto.constantTimeEquals(context.getProofId(), witness.getProofId())) {
			throw invalid("context and witness proof ids do not match");
		}
		if ((context.getRuleMask() & DuelProofSpec.DUEL_RULE_NO_MAGIC) == 0) {
			throw invalid("terminal witness context did not lock the No Magic rule");
		}
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			if (context.getParticipant(ordinal).getPlayerId()
				!= witness.getParticipant(ordinal).getPlayerId()) {
				throw invalid("context and witness participant ids do not match");
			}
		}

		final byte[] contextHash = DuelProofCrypto.contextHash(witness.getContextBytes());
		if (!DuelProofCrypto.constantTimeEquals(contextHash, witness.getContextHash())) {
			throw invalid("context hash does not match the canonical context");
		}
		final byte[] serverCommitment = DuelProofCrypto.serverCommitment(contextHash,
			witness.getServerSeed());
		if (!DuelProofCrypto.constantTimeEquals(serverCommitment,
			witness.getServerCommitment())) {
			throw invalid("server seed does not open the server commitment");
		}

		final DuelProofTerminalParticipant first = witness.getParticipant(0);
		final DuelProofTerminalParticipant second = witness.getParticipant(1);
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			final DuelProofTerminalParticipant participant = witness.getParticipant(ordinal);
			final byte[] expectedCommitment = DuelProofCrypto.clientCommitment(contextHash,
				serverCommitment, ordinal, participant.getSeed());
			if (!DuelProofCrypto.constantTimeEquals(expectedCommitment,
				participant.getCommitment())) {
				throw invalid("participant seed does not open commitment " + ordinal);
			}
		}
		final byte[] finalLockHash = DuelProofCrypto.finalLockHash(witness.getProofId(),
			contextHash, serverCommitment, first.getPlayerId(), first.getCommitment(),
			second.getPlayerId(), second.getCommitment());
		if (!DuelProofCrypto.constantTimeEquals(finalLockHash, witness.getFinalLockHash())) {
			throw invalid("final lock hash does not match the committed handshake");
		}
		if (!DuelProofCrypto.constantTimeEquals(first.getLockAck(), finalLockHash)
			|| !DuelProofCrypto.constantTimeEquals(second.getLockAck(), finalLockHash)) {
			throw invalid("both participant lock acknowledgements must match the final lock");
		}

		final byte[] masterSeed = DuelProofCrypto.masterSeed(contextHash,
			witness.getServerSeed(), first.getSeed(), second.getSeed());
		DuelProofMeleeReplay replay = null;
		try {
			replay = new DuelProofMeleeReplay(masterSeed);
			if (replay.chooseStarterOrdinal(
				context.getParticipant(0).getCombatLevel(),
				context.getParticipant(1).getCombatLevel()) != witness.getStarterOrdinal()) {
				throw invalid("starter does not match the committed stream");
			}
			final int[] hits = {
				context.getParticipant(0).getCurrentHits(),
				context.getParticipant(1).getCurrentHits()
			};
			final int[] recoilRemaining = {
				context.getParticipant(0).getRecoilRemaining(),
				context.getParticipant(1).getRecoilRemaining()
			};
			final boolean[] recoilEquipped = {
				context.getParticipant(0).isRecoilEquipped(),
				context.getParticipant(1).isRecoilEquipped()
			};

			for (int index = 0; index < witness.getSwings().size(); index++) {
				final DuelProofTerminalSwing recorded = witness.getSwings().get(index);
				validateInput(context, recorded);
				final DuelProofMeleeSwing computed = replay.resolveSwing(
					recorded.getActorOrdinal(), recorded.getInput());
				applyAndValidateTerminalState(witness, computed, index,
					hits, recoilEquipped, recoilRemaining);
			}
			if (replay.getCandidateDrawCount() != witness.getFinalCandidateDrawCount()) {
				throw invalid("final candidate draw count does not match the exact replay");
			}
			final List<DuelProofMeleeSwing> computed = replay.getSwings();
			if (computed.size() != witness.getSwings().size()) {
				throw invalid("terminal replay swing count does not match the witness");
			}
			return new Verification(witness, context, computed,
				DuelProofCrypto.sha256(canonicalWitnessBytes));
		} catch (final IllegalArgumentException invalid) {
			throw invalid;
		} catch (final RuntimeException malformed) {
			throw invalid("terminal replay could not be evaluated", malformed);
		} finally {
			if (replay != null) {
				replay.destroy();
			}
			Arrays.fill(masterSeed, (byte) 0);
			Arrays.fill(contextHash, (byte) 0);
			Arrays.fill(serverCommitment, (byte) 0);
			Arrays.fill(finalLockHash, (byte) 0);
		}
	}

	private static void validateInput(final DuelProofContext context,
								  final DuelProofTerminalSwing swing) {
		final int actorOrdinal = swing.getActorOrdinal();
		final DuelProofContextParticipant attacker = context.getParticipant(actorOrdinal);
		final DuelProofContextParticipant defender = context.getParticipant(1 - actorOrdinal);
		final DuelProofMeleeInput input = swing.getInput();
		if (input.getAttackerAttackLevel() != attacker.getCurrentAttack()
			|| input.getAttackerStrengthLevel() != attacker.getCurrentStrength()
			|| input.getDefenderDefenceLevel() != defender.getCurrentDefence()
			|| input.getAttackerWeaponAimPoints() != attacker.getWeaponAimPoints()
			|| input.getAttackerWeaponPowerPoints() != attacker.getWeaponPowerPoints()
			|| input.getDefenderArmourPoints() != defender.getArmourPoints()
			|| input.isAttackCapeEligible() != attacker.isAttackCapeEligible()
			|| input.isStrengthCapeEligible() != attacker.isStrengthCapeEligible()
			|| input.isDefenceCapeEligible() != defender.isDefenceCapeEligible()) {
			throw invalid("swing formula input does not match the committed participant state");
		}

		if ((context.getRuleMask() & DuelProofSpec.DUEL_RULE_NO_PRAYER) != 0) {
			if (input.getAttackerAttackPrayerTier() != DuelProofSpec.PRAYER_TIER_NONE
				|| input.getAttackerStrengthPrayerTier() != DuelProofSpec.PRAYER_TIER_NONE
				|| input.getDefenderDefencePrayerTier() != DuelProofSpec.PRAYER_TIER_NONE) {
				throw invalid("No Prayer duel contains a prayer-affected melee input");
			}
			return;
		}

		validatePrayerTier(attacker, input.getAttackerAttackPrayerTier(),
			ATTACK_PRAYER_LOW_LEVEL, ATTACK_PRAYER_MIDDLE_LEVEL, ATTACK_PRAYER_HIGH_LEVEL,
			"attack");
		validatePrayerTier(attacker, input.getAttackerStrengthPrayerTier(),
			STRENGTH_PRAYER_LOW_LEVEL, STRENGTH_PRAYER_MIDDLE_LEVEL,
			STRENGTH_PRAYER_HIGH_LEVEL, "strength");
		validatePrayerTier(defender, input.getDefenderDefencePrayerTier(),
			DEFENCE_PRAYER_LOW_LEVEL, DEFENCE_PRAYER_MIDDLE_LEVEL,
			DEFENCE_PRAYER_HIGH_LEVEL, "defence");
	}

	private static void validatePrayerTier(final DuelProofContextParticipant participant,
									   final int tier, final int lowRequirement,
									   final int middleRequirement,
									   final int highRequirement, final String label) {
		if (tier == DuelProofSpec.PRAYER_TIER_NONE) {
			return;
		}
		final int requirement;
		switch (tier) {
			case DuelProofSpec.PRAYER_TIER_LOW:
				requirement = lowRequirement;
				break;
			case DuelProofSpec.PRAYER_TIER_MIDDLE:
				requirement = middleRequirement;
				break;
			case DuelProofSpec.PRAYER_TIER_HIGH:
				requirement = highRequirement;
				break;
			default:
				throw invalid("unsupported " + label + " prayer tier");
		}
		if (participant.getCurrentPrayer() <= 0
			|| participant.getMaximumPrayer() < requirement) {
			throw invalid(label + " prayer tier is impossible for the committed Prayer state");
		}
	}

	private static void applyAndValidateTerminalState(final DuelProofTerminalWitness witness,
											 final DuelProofMeleeSwing swing, final int index,
											 final int[] hits,
											 final boolean[] recoilEquipped,
											 final int[] recoilRemaining) {
		final int actor = swing.getActorOrdinal();
		final int defender = 1 - actor;
		final int damage = swing.getResult().getDamage();
		final boolean finalSwing = index == witness.getSwings().size() - 1;
		final boolean directLethal = damage > 0 && damage >= hits[defender];
		int reflectedDamage = 0;
		if (!directLethal && damage > 0 && recoilEquipped[defender]) {
			reflectedDamage = Math.min(damage / 10 + 1, recoilRemaining[defender]);
		}
		final boolean recoilLethal = !directLethal && reflectedDamage > 0
			&& reflectedDamage >= hits[actor];
		final boolean terminal = directLethal || recoilLethal;
		if (terminal && !finalSwing) {
			throw invalid("witness contains a swing after the duel was already terminal");
		}
		if (!terminal && finalSwing) {
			throw invalid("final witness swing is only a non-terminal replay prefix");
		}

		if (directLethal) {
			hits[defender] = 0;
		} else {
			hits[defender] -= damage;
			if (reflectedDamage > 0) {
				recoilRemaining[defender] -= reflectedDamage;
				if (recoilRemaining[defender] == 0) {
					recoilEquipped[defender] = false;
				}
				hits[actor] = Math.max(0, hits[actor] - reflectedDamage);
			}
		}

		if (!finalSwing) {
			if (hits[0] <= 0 || hits[1] <= 0) {
				throw invalid("pre-terminal swing left a participant dead");
			}
			return;
		}

		final int expectedCause = directLethal
			? DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE
			: DuelProofSpec.TERMINAL_CAUSE_RECOIL;
		final int expectedWinner = directLethal ? actor : defender;
		if (witness.getTerminalCause() != expectedCause
			|| witness.getWinnerOrdinal() != expectedWinner
			|| hits[expectedWinner] <= 0 || hits[1 - expectedWinner] != 0) {
			throw invalid("winner or terminal cause does not match the complete HP/recoil replay");
		}
	}

	private static DuelProofContextParticipant parseContextParticipant(
		final ContextReader input, final int ordinal, final int recoilLimit) {
		if (input.readUnsignedByte("context participant ordinal") != ordinal) {
			throw invalid("context participant ordinals are not canonical");
		}
		final int playerId = input.readInt("context participant player id");
		final int usernameLength = input.readUnsignedByte("context username length");
		final byte[] usernameBytes = input.readBytes(usernameLength, "context username");
		final StringBuilder username = new StringBuilder(usernameBytes.length);
		for (final byte value : usernameBytes) {
			if ((value & 0x80) != 0) {
				throw invalid("context username is not canonical ASCII");
			}
			username.append((char) (value & 0x7f));
		}
		final int combatLevel = readPositive(input, "context combat level");

		final int currentAttack = readNonNegative(input, "context current Attack");
		final int maximumAttack = readPositive(input, "context maximum Attack");
		final int currentDefence = readNonNegative(input, "context current Defence");
		final int maximumDefence = readPositive(input, "context maximum Defence");
		final int currentStrength = readNonNegative(input, "context current Strength");
		final int maximumStrength = readPositive(input, "context maximum Strength");
		final int currentHits = readPositive(input, "context current Hits");
		final int maximumHits = readPositive(input, "context maximum Hits");
		final int currentPrayer = readNonNegative(input, "context current Prayer");
		final int maximumPrayer = readNonNegative(input, "context maximum Prayer");
		final int combatStyle = input.readUnsignedByte("context combat style");
		if (combatStyle < DuelProofSpec.COMBAT_STYLE_CONTROLLED
			|| combatStyle > DuelProofSpec.COMBAT_STYLE_DEFENSIVE) {
			throw invalid("context combat style is unsupported");
		}
		final int aimPoints = input.readInt("context weapon aim points");
		final int powerPoints = input.readInt("context weapon power points");
		final int armourPoints = input.readInt("context armour points");
		final boolean attackCapeEligible = input.readBoolean("context Attack cape eligibility");
		final boolean strengthCapeEligible = input.readBoolean("context Strength cape eligibility");
		final boolean defenceCapeEligible = input.readBoolean("context Defence cape eligibility");
		final int prayerMask = input.readInt("context prayer mask");
		if (prayerMask < 0) {
			throw invalid("context prayer mask contains a reserved bit");
		}
		if (prayerMask != 0 && currentPrayer <= 0) {
			throw invalid("context cannot begin with active prayers and zero Prayer points");
		}

		if (input.readUnsignedByte("context equipment count")
			!= CONTEXT_V3_EQUIPMENT_SLOTS) {
			throw invalid("context equipment count is not canonical for v3");
		}
		final List<DuelProofContextItem> equipment =
			new ArrayList<DuelProofContextItem>(CONTEXT_V3_EQUIPMENT_SLOTS);
		for (int slot = 0; slot < CONTEXT_V3_EQUIPMENT_SLOTS; slot++) {
			final int itemId = input.readInt("context equipment item id");
			final int amount = input.readInt("context equipment amount");
			final boolean noted = input.readBoolean("context equipment noted flag");
			if (itemId == -1) {
				if (amount != 0 || noted) {
					throw invalid("empty context equipment slot is not canonical");
				}
			} else if (itemId < 0 || amount <= 0) {
				throw invalid("context equipment item is invalid");
			}
			equipment.add(new DuelProofContextItem(slot, itemId, amount, noted));
		}
		final boolean recoilEquipped = input.readBoolean("context recoil equipped flag");
		final int recoilRemaining = input.readInt("context recoil remaining");
		final DuelProofContextItem committedRingSlot =
			equipment.get(CONTEXT_V3_RING_SLOT);
		if (recoilEquipped) {
			if (committedRingSlot.getItemId() != RING_OF_RECOIL_ITEM_ID
				|| committedRingSlot.getAmount() <= 0 || committedRingSlot.isNoted()) {
				throw invalid("recoil flag does not match the committed ring slot");
			}
		} else if (committedRingSlot.getItemId() == RING_OF_RECOIL_ITEM_ID) {
			throw invalid("committed recoil ring is missing its recoil flag");
		}
		if (recoilEquipped) {
			if (recoilRemaining <= 0 || recoilRemaining > recoilLimit) {
				throw invalid("equipped recoil ring has invalid remaining capacity");
			}
		} else if (recoilRemaining != 0) {
			throw invalid("context has recoil capacity without an equipped recoil ring");
		}

		final int stakeCount = input.readUnsignedByte("context stake count");
		if (stakeCount > CONTEXT_V3_MAX_STAKE_SLOTS) {
			throw invalid("context stake count exceeds the v3 container");
		}
		final List<DuelProofContextItem> stakes =
			new ArrayList<DuelProofContextItem>(stakeCount);
		for (int slot = 0; slot < stakeCount; slot++) {
			if (input.readUnsignedByte("context stake slot") != slot) {
				throw invalid("context stake slots are not canonical");
			}
			final int itemId = input.readInt("context stake item id");
			final int amount = input.readInt("context stake amount");
			final boolean noted = input.readBoolean("context stake noted flag");
			if (itemId < 0 || amount <= 0) {
				throw invalid("context stake item is invalid");
			}
			stakes.add(new DuelProofContextItem(slot, itemId, amount, noted));
		}

		return new DuelProofContextParticipant(ordinal, playerId, username.toString(), combatLevel,
			currentAttack, maximumAttack, currentDefence, maximumDefence,
			currentStrength, maximumStrength, currentHits, maximumHits,
			currentPrayer, maximumPrayer, combatStyle, aimPoints, powerPoints, armourPoints,
			attackCapeEligible, strengthCapeEligible, defenceCapeEligible, prayerMask,
			equipment, recoilEquipped, recoilRemaining, stakes);
	}

	private static int readNonNegative(final ContextReader input, final String label) {
		final int value = input.readInt(label);
		if (value < 0) {
			throw invalid(label + " must not be negative");
		}
		return value;
	}

	private static int readPositive(final ContextReader input, final String label) {
		final int value = input.readInt(label);
		if (value <= 0) {
			throw invalid(label + " must be positive");
		}
		return value;
	}

	private static IllegalArgumentException invalid(final String message) {
		return new IllegalArgumentException(message);
	}

	private static IllegalArgumentException invalid(final String message,
											 final Throwable cause) {
		return new IllegalArgumentException(message, cause);
	}

	/** Verified immutable output, including context and the independently recomputed transcript. */
	public static final class Verification {
		private final DuelProofTerminalWitness witness;
		private final DuelProofContext context;
		private final List<DuelProofMeleeSwing> computedSwings;
		private final byte[] witnessHash;

		private Verification(final DuelProofTerminalWitness witness,
							 final DuelProofContext context,
							 final List<DuelProofMeleeSwing> computedSwings,
							 final byte[] witnessHash) {
			this.witness = witness;
			this.context = context;
			this.computedSwings = Collections.unmodifiableList(
				new ArrayList<DuelProofMeleeSwing>(computedSwings));
			this.witnessHash = Arrays.copyOf(witnessHash, witnessHash.length);
		}

		public DuelProofTerminalWitness getWitness() { return witness; }
		public DuelProofContext getContext() { return context; }
		public List<DuelProofMeleeSwing> getComputedSwings() { return computedSwings; }
		public byte[] getWitnessHash() {
			return Arrays.copyOf(witnessHash, witnessHash.length);
		}
	}

	/** Minimal strict reader for the separately versioned canonical context. */
	private static final class ContextReader {
		private final byte[] bytes;
		private int offset;

		private ContextReader(final byte[] bytes) {
			if (bytes == null || bytes.length == 0
				|| bytes.length > DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES) {
				throw invalid("context is outside the supported size range");
			}
			this.bytes = bytes;
		}

		private int readUnsignedByte(final String label) {
			requireRemaining(1, label);
			return bytes[offset++] & 0xff;
		}

		private int readInt(final String label) {
			requireRemaining(4, label);
			final int value = ((bytes[offset] & 0xff) << 24)
				| ((bytes[offset + 1] & 0xff) << 16)
				| ((bytes[offset + 2] & 0xff) << 8)
				| (bytes[offset + 3] & 0xff);
			offset += 4;
			return value;
		}

		private boolean readBoolean(final String label) {
			final int value = readUnsignedByte(label);
			if (value != 0 && value != 1) {
				throw invalid(label + " is not a canonical boolean");
			}
			return value == 1;
		}

		private byte[] readBytes(final int length, final String label) {
			requireRemaining(length, label);
			final byte[] result = new byte[length];
			System.arraycopy(bytes, offset, result, 0, length);
			offset += length;
			return result;
		}

		private void requireBytes(final byte[] expected, final String label) {
			if (!DuelProofCrypto.constantTimeEquals(expected,
				readBytes(expected.length, label))) {
				throw invalid(label + " does not match");
			}
		}

		private void requireFinished() {
			if (offset != bytes.length) {
				throw invalid("context contains a non-canonical tail");
			}
		}

		private void requireRemaining(final int length, final String label) {
			if (length < 0 || offset > bytes.length - length) {
				throw invalid("context is truncated at " + label);
			}
		}
	}
}
