package com.openrsc.server.content.duelproof;

import com.openrsc.server.database.struct.DuelProofAttemptRecord;
import com.openrsc.server.model.entity.player.Player;
import com.voidscape.duelproof.DuelProofCrypto;
import com.voidscape.duelproof.DuelProofMeleeInput;
import com.voidscape.duelproof.DuelProofMeleeReplay;
import com.voidscape.duelproof.DuelProofMeleeSwing;
import com.voidscape.duelproof.DuelProofSpec;

import java.util.Arrays;
import java.util.List;

/** One fail-closed proof handshake shared by both final-confirmed duelists. */
public final class DuelProofSession {
	public enum Phase {
		PERSISTING_SERVER_COMMITMENT,
		AWAITING_CLIENT_COMMITMENTS,
		PERSISTING_CLIENT_COMMITMENTS,
		AWAITING_CLIENT_REVEALS,
		PERSISTING_CLIENT_REVEALS,
		AWAITING_LOCK_ACKS,
		PERSISTING_LOCK,
		LOCKED,
		PERSISTING_COMBAT,
		COMBAT,
		TERMINAL_CAPTURED,
		PERSISTING_COMPLETION,
		VERIFIED,
		ABORTED
	}

	final Player first;
	final Player second;
	final DuelProofAttemptRecord attempt;
	final String proofId;
	final byte[] proofIdBytes;
	final byte[] contextBytes;
	final byte[] contextHash;
	final byte[] serverCommitment;
	final byte[] serverSeed;
	final int firstCombatLevel;
	final int secondCombatLevel;
	final long createdAt;
	Runnable continuation;
	Phase phase = Phase.PERSISTING_SERVER_COMMITMENT;
	byte[] firstClientCommitment;
	byte[] secondClientCommitment;
	byte[] firstClientSeed;
	byte[] secondClientSeed;
	byte[] finalLockHash;
	byte[] masterSeed;
	DuelProofMeleeReplay meleeReplay;
	DuelProofCompletion terminalCompletion;
	boolean firstAck;
	boolean secondAck;

	DuelProofSession(final Player first, final Player second,
					 final DuelProofAttemptRecord attempt, final byte[] proofIdBytes,
					 final byte[] contextBytes, final byte[] contextHash, final byte[] serverCommitment,
					 final byte[] serverSeed, final int firstCombatLevel,
					 final int secondCombatLevel, final Runnable continuation) {
		this.first = first;
		this.second = second;
		this.attempt = attempt;
		this.proofId = attempt.proofId;
		this.proofIdBytes = Arrays.copyOf(proofIdBytes, proofIdBytes.length);
		this.contextBytes = Arrays.copyOf(contextBytes, contextBytes.length);
		this.contextHash = Arrays.copyOf(contextHash, contextHash.length);
		this.serverCommitment = Arrays.copyOf(serverCommitment, serverCommitment.length);
		this.serverSeed = Arrays.copyOf(serverSeed, serverSeed.length);
		if (firstCombatLevel <= 0 || secondCombatLevel <= 0) {
			throw new IllegalArgumentException("duel proof combat levels must be positive");
		}
		this.firstCombatLevel = firstCombatLevel;
		this.secondCombatLevel = secondCombatLevel;
		this.createdAt = attempt.createdAt;
		this.continuation = continuation;
	}

	public String getProofId() {
		return proofId;
	}

	public synchronized Phase getPhase() {
		return phase;
	}

	public synchronized boolean isLocked() {
		return phase == Phase.LOCKED || phase == Phase.PERSISTING_COMBAT
			|| phase == Phase.COMBAT || phase == Phase.TERMINAL_CAPTURED
			|| phase == Phase.PERSISTING_COMPLETION || phase == Phase.VERIFIED;
	}

	public synchronized boolean isCombatStarted() {
		return phase == Phase.COMBAT;
	}

	public synchronized boolean isPreCombat() {
		switch (phase) {
			case PERSISTING_SERVER_COMMITMENT:
			case AWAITING_CLIENT_COMMITMENTS:
			case PERSISTING_CLIENT_COMMITMENTS:
			case AWAITING_CLIENT_REVEALS:
			case PERSISTING_CLIENT_REVEALS:
			case AWAITING_LOCK_ACKS:
			case PERSISTING_LOCK:
			case LOCKED:
			case PERSISTING_COMBAT:
				return true;
			default:
				return false;
		}
	}

	/** Melee-only proof duels pause poison and other uncovered lethal damage. */
	public synchronized boolean blocksExternalDamage() {
		return phase != Phase.ABORTED && phase != Phase.VERIFIED;
	}

	public synchronized byte[] getMasterSeed() {
		return masterSeed == null ? null : Arrays.copyOf(masterSeed, masterSeed.length);
	}

	/** Uses committed levels plus draw one to select the canonical starting participant. */
	public synchronized int chooseStarterPlayerId() {
		if (phase != Phase.PERSISTING_COMBAT || masterSeed == null || meleeReplay != null) {
			throw new IllegalStateException("duel proof starter is outside combat entry");
		}
		DuelProofMeleeReplay created = null;
		try {
			created = new DuelProofMeleeReplay(masterSeed);
			final int starterOrdinal = created.chooseStarterOrdinal(
				firstCombatLevel, secondCombatLevel);
			meleeReplay = created;
			return starterOrdinal == 0 ? first.getDatabaseID() : second.getDatabaseID();
		} catch (final RuntimeException failure) {
			if (created != null) {
				created.destroy();
			}
			throw failure;
		}
	}

	/** Resolves and records one full, globally ordered covered melee swing. */
	public synchronized DuelProofMeleeSwing resolveMeleeSwing(final Player actor,
													 final Player defender,
													 final DuelProofMeleeInput input) {
		if (phase != Phase.COMBAT || meleeReplay == null || actor == null || defender == null) {
			throw new IllegalStateException("duel proof melee swing is outside active combat");
		}
		final int actorOrdinal = ordinal(actor);
		final int defenderOrdinal = ordinal(defender);
		if (actorOrdinal < 0 || defenderOrdinal < 0 || actorOrdinal == defenderOrdinal) {
			throw new IllegalArgumentException("duel proof swing participants do not match");
		}
		if (meleeReplay.getSwings().size() >= DuelProofSpec.MAX_WITNESS_SWINGS) {
			throw new IllegalStateException("duel proof melee transcript exceeded its v1 limit");
		}
		return meleeReplay.resolveSwing(actorOrdinal, input);
	}

	public synchronized int getStarterOrdinal() {
		requireMeleeReplay();
		return meleeReplay.getStarterOrdinal();
	}

	public synchronized List<DuelProofMeleeSwing> getMeleeSwings() {
		requireMeleeReplay();
		return meleeReplay.getSwings();
	}

	private void requireMeleeReplay() {
		if (meleeReplay == null) {
			throw new IllegalStateException("duel proof melee replay is unavailable");
		}
	}

	int ordinal(final Player player) {
		if (player == first || (player != null && player.getDatabaseID() == first.getDatabaseID())) {
			return 0;
		}
		if (player == second || (player != null && player.getDatabaseID() == second.getDatabaseID())) {
			return 1;
		}
		return -1;
	}

	void wipePreCombatSecrets() {
		if (meleeReplay != null) {
			meleeReplay.destroy();
			meleeReplay = null;
		}
		wipe(firstClientSeed);
		wipe(secondClientSeed);
		wipe(masterSeed);
		wipe(serverSeed);
		firstClientSeed = null;
		secondClientSeed = null;
		masterSeed = null;
	}

	private static void wipe(final byte[] value) {
		if (value != null) {
			Arrays.fill(value, (byte) 0);
		}
	}

	boolean same(final byte[] firstValue, final byte[] secondValue) {
		return DuelProofCrypto.constantTimeEquals(firstValue, secondValue);
	}
}
