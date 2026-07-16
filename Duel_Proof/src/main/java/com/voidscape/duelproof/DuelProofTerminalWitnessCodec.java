package com.voidscape.duelproof;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Strict canonical binary codec for terminal duel witnesses. */
public final class DuelProofTerminalWitnessCodec {

	private static final byte[] MAGIC = DuelProofCodec.ascii("VSDPWIT1");
	private static final int PARTICIPANT_COUNT = 2;

	private DuelProofTerminalWitnessCodec() {
	}

	public static byte[] encode(final DuelProofTerminalWitness witness) {
		if (witness == null) {
			throw new IllegalArgumentException("witness must not be null");
		}
		try {
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024);
			final DataOutputStream output = new DataOutputStream(bytes);
			output.write(MAGIC);
			output.writeInt(witness.getWitnessVersion());
			output.writeInt(witness.getProtocolVersion());
			output.writeInt(witness.getRngVersion());
			output.writeInt(witness.getFormulaVersion());
			output.writeInt(witness.getContextVersion());
			output.write(witness.getProofId());
			final byte[] contextBytes = witness.getContextBytes();
			output.writeShort(contextBytes.length);
			output.write(contextBytes);
			output.write(witness.getContextHash());
			output.write(witness.getServerCommitment());
			output.write(witness.getServerSeed());
			output.write(witness.getFinalLockHash());
			output.writeByte(PARTICIPANT_COUNT);
			for (final DuelProofTerminalParticipant participant : witness.getParticipants()) {
				output.writeByte(participant.getOrdinal());
				output.writeInt(participant.getPlayerId());
				output.write(participant.getCommitment());
				output.write(participant.getSeed());
				output.write(participant.getLockAck());
			}
			output.writeByte(witness.getStarterOrdinal());
			output.writeByte(witness.getWinnerOrdinal());
			output.writeByte(witness.getTerminalCause());
			output.writeLong(witness.getFinishedAtMs());
			output.writeInt(witness.getSwings().size());
			output.writeLong(witness.getFinalCandidateDrawCount());
			for (final DuelProofTerminalSwing swing : witness.getSwings()) {
				writeSwing(output, swing);
			}
			output.flush();
			final byte[] encoded = bytes.toByteArray();
			if (encoded.length > DuelProofSpec.MAX_WITNESS_BYTES) {
				throw new IllegalArgumentException("canonical witness exceeds 256 KiB");
			}
			return encoded;
		} catch (final IOException impossible) {
			throw new IllegalStateException("unable to encode in-memory terminal witness", impossible);
		}
	}

	public static DuelProofTerminalWitness decode(final byte[] encoded) {
		if (encoded == null || encoded.length == 0
			|| encoded.length > DuelProofSpec.MAX_WITNESS_BYTES) {
			throw new IllegalArgumentException("encoded witness must contain 1..256 KiB");
		}
		final Reader input = new Reader(encoded);
		input.requireBytes(MAGIC, "terminal witness magic");
		final int witnessVersion = input.readInt("witnessVersion");
		final int protocolVersion = input.readInt("protocolVersion");
		final int rngVersion = input.readInt("rngVersion");
		final int formulaVersion = input.readInt("formulaVersion");
		final int contextVersion = input.readInt("contextVersion");
		final byte[] proofId = input.readBytes(DuelProofSpec.PROOF_ID_BYTES, "proofId");
		final int contextLength = input.readUnsignedShort("context length");
		if (contextLength == 0 || contextLength > DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES) {
			throw new IllegalArgumentException("context length is outside the canonical range");
		}
		final byte[] contextBytes = input.readBytes(contextLength, "contextBytes");
		final byte[] contextHash = input.readBytes(DuelProofSpec.HASH_BYTES, "contextHash");
		final byte[] serverCommitment = input.readBytes(DuelProofSpec.HASH_BYTES,
			"serverCommitment");
		final byte[] serverSeed = input.readBytes(DuelProofSpec.SEED_BYTES, "serverSeed");
		final byte[] finalLockHash = input.readBytes(DuelProofSpec.HASH_BYTES, "finalLockHash");
		if (input.readUnsignedByte("participant count") != PARTICIPANT_COUNT) {
			throw new IllegalArgumentException("terminal witness must contain exactly two participants");
		}
		final List<DuelProofTerminalParticipant> participants =
			new ArrayList<DuelProofTerminalParticipant>(PARTICIPANT_COUNT);
		for (int ordinal = 0; ordinal < PARTICIPANT_COUNT; ordinal++) {
			final int encodedOrdinal = input.readUnsignedByte("participant ordinal");
			if (encodedOrdinal != ordinal) {
				throw new IllegalArgumentException("participant ordinals are not canonical");
			}
			participants.add(new DuelProofTerminalParticipant(encodedOrdinal,
				input.readInt("participant playerId"),
				input.readBytes(DuelProofSpec.HASH_BYTES, "participant commitment"),
				input.readBytes(DuelProofSpec.SEED_BYTES, "participant seed"),
				input.readBytes(DuelProofSpec.HASH_BYTES, "participant lockAck")));
		}
		final int starterOrdinal = input.readUnsignedByte("starterOrdinal");
		final int winnerOrdinal = input.readUnsignedByte("winnerOrdinal");
		final int terminalCause = input.readUnsignedByte("terminalCause");
		final long finishedAtMs = input.readLong("finishedAtMs");
		final long swingCountLong = input.readUnsignedInt("swing count");
		if (swingCountLong == 0 || swingCountLong > DuelProofSpec.MAX_WITNESS_SWINGS) {
			throw new IllegalArgumentException("swing count is outside the canonical range");
		}
		final int swingCount = (int) swingCountLong;
		final long finalCandidateDrawCount = input.readLong("finalCandidateDrawCount");
		final List<DuelProofTerminalSwing> swings =
			new ArrayList<DuelProofTerminalSwing>(swingCount);
		for (int index = 0; index < swingCount; index++) {
			swings.add(readSwing(input, index + 1));
		}
		input.requireFinished();
		return new DuelProofTerminalWitness(witnessVersion, protocolVersion, rngVersion,
			formulaVersion, contextVersion, proofId, contextBytes, contextHash,
			serverCommitment, serverSeed, finalLockHash, participants, starterOrdinal,
			winnerOrdinal, terminalCause, finishedAtMs, finalCandidateDrawCount, swings);
	}

	private static void writeSwing(final DataOutputStream output,
								   final DuelProofTerminalSwing swing) throws IOException {
		output.writeInt(swing.getSwingNumber());
		output.writeByte(swing.getActorOrdinal());
		final DuelProofMeleeInput input = swing.getInput();
		output.writeByte(input.getAttackerCombatStyle());
		output.writeByte(input.getDefenderCombatStyle());
		output.writeInt(input.getAttackerAttackLevel());
		output.writeInt(input.getAttackerStrengthLevel());
		output.writeInt(input.getDefenderDefenceLevel());
		output.writeByte(input.getAttackerAttackPrayerTier());
		output.writeByte(input.getAttackerStrengthPrayerTier());
		output.writeByte(input.getDefenderDefencePrayerTier());
		output.writeInt(input.getAttackerWeaponAimPoints());
		output.writeInt(input.getAttackerWeaponPowerPoints());
		output.writeInt(input.getDefenderArmourPoints());
		writeBoolean(output, input.isAttackCapeEligible());
		writeBoolean(output, input.isStrengthCapeEligible());
		writeBoolean(output, input.isDefenceCapeEligible());
	}

	private static DuelProofTerminalSwing readSwing(final Reader input,
												 final int expectedNumber) {
		final long swingNumberLong = input.readUnsignedInt("swing number");
		if (swingNumberLong != expectedNumber) {
			throw new IllegalArgumentException("witness swing numbers are not sequential");
		}
		final int actorOrdinal = input.readUnsignedByte("swing actor ordinal");
		final DuelProofMeleeInput meleeInput = new DuelProofMeleeInput(
			input.readUnsignedByte("attacker combat style"),
			input.readUnsignedByte("defender combat style"),
			input.readInt("attacker attack level"),
			input.readInt("attacker strength level"),
			input.readInt("defender defence level"),
			input.readUnsignedByte("attacker attack prayer tier"),
			input.readUnsignedByte("attacker strength prayer tier"),
			input.readUnsignedByte("defender defence prayer tier"),
			input.readInt("attacker weapon aim points"),
			input.readInt("attacker weapon power points"),
			input.readInt("defender armour points"),
			input.readBoolean("attack cape eligible"),
			input.readBoolean("strength cape eligible"),
			input.readBoolean("defence cape eligible"));
		return new DuelProofTerminalSwing(expectedNumber, actorOrdinal, meleeInput);
	}

	private static void writeBoolean(final DataOutputStream output, final boolean value)
		throws IOException {
		output.writeByte(value ? 1 : 0);
	}

	/** Bounds-checking reader that rejects truncated, non-canonical, and tailed records. */
	private static final class Reader {
		private final byte[] bytes;
		private int offset;

		private Reader(final byte[] bytes) {
			this.bytes = bytes;
		}

		private int readUnsignedByte(final String label) {
			requireRemaining(1, label);
			return bytes[offset++] & 0xff;
		}

		private int readUnsignedShort(final String label) {
			requireRemaining(2, label);
			final int value = ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
			offset += 2;
			return value;
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

		private long readUnsignedInt(final String label) {
			return readInt(label) & 0xffffffffL;
		}

		private long readLong(final String label) {
			requireRemaining(8, label);
			long value = 0;
			for (int index = 0; index < 8; index++) {
				value = (value << 8) | (bytes[offset + index] & 0xffL);
			}
			offset += 8;
			return value;
		}

		private boolean readBoolean(final String label) {
			final int value = readUnsignedByte(label);
			if (value != 0 && value != 1) {
				throw new IllegalArgumentException(label + " is not a canonical boolean");
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
			final byte[] actual = readBytes(expected.length, label);
			if (!DuelProofCrypto.constantTimeEquals(expected, actual)) {
				throw new IllegalArgumentException(label + " does not match");
			}
		}

		private void requireFinished() {
			if (offset != bytes.length) {
				throw new IllegalArgumentException("terminal witness contains a non-canonical tail");
			}
		}

		private void requireRemaining(final int length, final String label) {
			if (length < 0 || offset > bytes.length - length) {
				throw new IllegalArgumentException("terminal witness is truncated at " + label);
			}
		}
	}
}
