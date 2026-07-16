package com.openrsc.server.database.struct;

import java.util.Arrays;

/** Immutable participant snapshot for one durable duel-proof attempt. */
public final class DuelProofAttemptParticipant {
	private final int canonicalOrdinal;
	private final int playerId;
	private final String username;
	private final byte[] clientCommitment;
	private final byte[] clientSeed;
	private final byte[] lockAck;

	public DuelProofAttemptParticipant(final int canonicalOrdinal, final int playerId,
										   final String username) {
		this(canonicalOrdinal, playerId, username, null, null, null);
	}

	public DuelProofAttemptParticipant(final int canonicalOrdinal, final int playerId,
										   final String username, final byte[] clientCommitment,
										   final byte[] clientSeed, final byte[] lockAck) {
		this.canonicalOrdinal = canonicalOrdinal;
		this.playerId = playerId;
		this.username = username;
		this.clientCommitment = copy(clientCommitment);
		this.clientSeed = copy(clientSeed);
		this.lockAck = copy(lockAck);
	}

	public int getCanonicalOrdinal() {
		return canonicalOrdinal;
	}

	public int getPlayerId() {
		return playerId;
	}

	public String getUsername() {
		return username;
	}

	public byte[] getClientCommitment() {
		return copy(clientCommitment);
	}

	public byte[] getClientSeed() {
		return copy(clientSeed);
	}

	public byte[] getLockAck() {
		return copy(lockAck);
	}

	private static byte[] copy(final byte[] value) {
		return value == null ? null : Arrays.copyOf(value, value.length);
	}
}
