package com.openrsc.server.content.duelproof;

import com.openrsc.server.Server;
import com.openrsc.server.content.dueljournal.DuelJournalSession;
import com.openrsc.server.database.struct.DuelProofAttemptParticipant;
import com.openrsc.server.database.struct.DuelProofAttemptRecord;
import com.openrsc.server.database.struct.DuelProofWitnessRecord;
import com.openrsc.server.database.struct.DuelReceipt;
import com.openrsc.server.database.struct.DuelReceiptParticipant;
import com.openrsc.server.database.struct.DuelReceiptStake;
import com.openrsc.server.database.struct.DuelReceiptSwing;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;
import com.voidscape.duelproof.DuelProofCodec;
import com.voidscape.duelproof.DuelProofContext;
import com.voidscape.duelproof.DuelProofContextItem;
import com.voidscape.duelproof.DuelProofContextParticipant;
import com.voidscape.duelproof.DuelProofCrypto;
import com.voidscape.duelproof.DuelProofMeleeSwing;
import com.voidscape.duelproof.DuelProofSpec;
import com.voidscape.duelproof.DuelProofTerminalParticipant;
import com.voidscape.duelproof.DuelProofTerminalSwing;
import com.voidscape.duelproof.DuelProofTerminalVerifier;
import com.voidscape.duelproof.DuelProofTerminalWitness;
import com.voidscape.duelproof.DuelProofTerminalWitnessCodec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Durable, fail-closed commit/reveal/lock coordinator for classic No Magic stake duels. */
public final class DuelProofService {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int CLIENT_PROOF_VERSION = 10136;
	private static final int HANDSHAKE_TIMEOUT_MS = 20000;
	private static final int COMBAT_START_TIMEOUT_MS = 15000;
	private static final int MAX_CLIENT_PAYLOAD = 192;
	private static final int CONTEXT_CHUNK_BYTES = 160;
	private static final String CLIENT_PREFIX = "@vsduelproof@";

	private DuelProofService() {
	}

	public static boolean requiresProof(final Player first, final Player second) {
		return first != null && second != null
			&& first.getDuel().getDuelSetting(1) && second.getDuel().getDuelSetting(1)
			&& !first.getWorld().getServer().getConfig().OSRS_COMBAT_MELEE;
	}

	public static boolean supportsProofClient(final Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= CLIENT_PROOF_VERSION;
	}

	/** Creates and durably stores the server commitment before either client is challenged. */
	public static DuelProofSession begin(final Player firstPlayer, final Player secondPlayer,
										 final Runnable continuation) {
		if (!requiresProof(firstPlayer, secondPlayer) || continuation == null
			|| !supportsProofClient(firstPlayer) || !supportsProofClient(secondPlayer)
			|| firstPlayer.getDuel().getProofSession() != null
			|| secondPlayer.getDuel().getProofSession() != null) {
			cancelWithoutSession(firstPlayer, secondPlayer,
				"The duel was cancelled because melee verification could not start.");
			return null;
		}

		final Player canonicalFirst = firstPlayer.getDatabaseID() < secondPlayer.getDatabaseID()
			? firstPlayer : secondPlayer;
		final Player canonicalSecond = canonicalFirst == firstPlayer ? secondPlayer : firstPlayer;
		try {
			final byte[] proofIdBytes = new byte[DuelProofSpec.PROOF_ID_BYTES];
			final byte[] serverSeed = new byte[DuelProofSpec.SEED_BYTES];
			SECURE_RANDOM.nextBytes(proofIdBytes);
			SECURE_RANDOM.nextBytes(serverSeed);
			final String proofId = DuelProofCodec.hexLower(proofIdBytes);
			final byte[] contextBytes = DuelProofContextEncoder.encode(
				canonicalFirst, canonicalSecond, proofIdBytes);
			final DuelProofContext committedContext =
				DuelProofTerminalVerifier.verifyContext(contextBytes);
			final byte[] contextHash = DuelProofCrypto.contextHash(contextBytes);
			final byte[] serverCommitment = DuelProofCrypto.serverCommitment(contextHash, serverSeed);
			final long createdAt = System.currentTimeMillis();
			final DuelProofAttemptRecord attempt = new DuelProofAttemptRecord(
				proofId,
				DuelProofSpec.PROTOCOL_VERSION,
				DuelProofSpec.RNG_VERSION,
				DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION,
				DuelProofSpec.CONTEXT_VERSION,
				createdAt,
				contextBytes,
				contextHash,
				serverCommitment,
				serverSeed,
				Arrays.asList(
					new DuelProofAttemptParticipant(0, canonicalFirst.getDatabaseID(),
						canonicalFirst.getUsername()),
					new DuelProofAttemptParticipant(1, canonicalSecond.getDatabaseID(),
						canonicalSecond.getUsername())
				)
			);
			final DuelProofSession session = new DuelProofSession(canonicalFirst, canonicalSecond,
				attempt, proofIdBytes, contextBytes, contextHash, serverCommitment, serverSeed,
				committedContext.getParticipant(0).getCombatLevel(),
				committedContext.getParticipant(1).getCombatLevel(), continuation);
			canonicalFirst.getDuel().setProofSession(session);
			canonicalSecond.getDuel().setProofSession(session);
			scheduleTimeout(session);
			persistInitialCommitment(session);
			return session;
		} catch (final RuntimeException error) {
			LOGGER.error("Unable to initialize melee duel proof for players {} and {}",
				firstPlayer.getDatabaseID(), secondPlayer.getDatabaseID(), error);
			cancelWithoutSession(firstPlayer, secondPlayer,
				"The duel was cancelled because melee verification could not start.");
			return null;
		}
	}

	/** Handles one strict option-19 response. Invalid current-session data aborts pre-combat. */
	public static void handleClientMessage(final Player player, final String payload) {
		final DuelProofSession session = player == null ? null : player.getDuel().getProofSession();
		if (session == null) {
			return;
		}
		if (payload == null || payload.length() < 1 || payload.length() > MAX_CLIENT_PAYLOAD) {
			abort(session, "malformed");
			return;
		}
		final String[] parts = payload.split("\\|", -1);
		if (parts.length != 4 || !"v1".equals(parts[0])) {
			if (parts.length >= 3 && session.getProofId().equals(parts[2])) {
				abort(session, "malformed");
			}
			return;
		}
		if (!session.getProofId().equals(parts[2])) {
			return;
		}
		if (!isCanonicalProofId(parts[2])) {
			abort(session, "malformed");
			return;
		}

		try {
			if ("commit".equals(parts[1])) {
				handleCommitment(session, player,
					DuelProofCodec.parseHexLowerExact(parts[3], DuelProofSpec.HASH_BYTES));
			} else if ("reveal".equals(parts[1])) {
				handleReveal(session, player,
					DuelProofCodec.parseHexLowerExact(parts[3], DuelProofSpec.SEED_BYTES));
			} else if ("ack".equals(parts[1])) {
				handleLockAck(session, player,
					DuelProofCodec.parseHexLowerExact(parts[3], DuelProofSpec.HASH_BYTES));
			} else if ("fail".equals(parts[1]) && isClientFailureReason(parts[3])) {
				abort(session, parts[3]);
			} else {
				abort(session, "malformed");
			}
		} catch (final IllegalArgumentException malformed) {
			abort(session, "malformed");
		}
	}

	/** Persists LOCKED -> COMBAT before running the game-thread combat entry callback. */
	public static boolean persistCombatStart(final DuelProofSession session,
										 final Runnable continuation) {
		if (session == null || continuation == null) {
			return false;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.LOCKED
				|| combatStartInvalidReason(session) != null) {
				return false;
			}
			session.phase = DuelProofSession.Phase.PERSISTING_COMBAT;
		}
		final Server server = session.first.getWorld().getServer();
		final String proofId = session.getProofId();
		final long startedAt = System.currentTimeMillis();
		server.submitSql(() -> {
			final boolean[] updated = {false};
			final boolean committed = server.getDatabase().atomically(() ->
				updated[0] = server.getDatabase().queryMarkDuelProofAttemptCombat(proofId, startedAt));
			final boolean persisted = committed && updated[0];
			server.getGameEventHandler().submit(
				() -> onCombatStartPersisted(session, persisted, continuation),
				"Duel proof combat start persisted");
		});
		return true;
	}

	private static void onCombatStartPersisted(final DuelProofSession session,
											 final boolean persisted,
											 final Runnable continuation) {
		if (!persisted) {
			LOGGER.error("Durable duel proof {} did not transition from LOCKED to COMBAT",
				session.getProofId());
			abort(session, "database");
			return;
		}

		String invalidReason = null;
		RuntimeException failure = null;
		boolean entered = false;
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_COMBAT) {
				return;
			}
			invalidReason = combatStartInvalidReason(session);
			if (invalidReason == null) {
				try {
					continuation.run();
				} catch (final RuntimeException error) {
					failure = error;
				}
				if (failure == null
					&& session.phase == DuelProofSession.Phase.PERSISTING_COMBAT
					&& session.first.getDuel().hasDuelCombatStarted()
					&& session.second.getDuel().hasDuelCombatStarted()
					&& session.first.getCombatEvent() != null
					&& session.first.getCombatEvent() == session.second.getCombatEvent()) {
					session.phase = DuelProofSession.Phase.COMBAT;
					entered = true;
				}
			}
		}
		if (invalidReason != null) {
			abort(session, invalidReason);
			return;
		}
		if (failure != null) {
			LOGGER.error("Unable to enter durably persisted duel proof combat {}",
				session.getProofId(), failure);
			abort(session, "state");
			return;
		}
		if (!entered) {
			abort(session, "state");
		}
	}

	/** Caller holds the session monitor. */
	private static String combatStartInvalidReason(final DuelProofSession session) {
		if (!isLiveAndAttached(session)) {
			return "disconnected";
		}
		if (!session.first.isBusy() || !session.second.isBusy()
			|| !contextStillMatches(session)) {
			return "state";
		}
		if (!session.first.getDuel().checkDuelItems()
			|| !session.second.getDuel().checkDuelItems()) {
			return "items";
		}
		if (!session.first.canReach(session.second) || !session.second.canReach(session.first)) {
			return "unreachable";
		}
		return null;
	}

	public static void abort(final DuelProofSession session, final String reason) {
		abortInternal(session, canonicalAbortReason(reason), true);
	}

	/** Stops a post-start duel without transferring stakes when proof execution breaks. */
	public static boolean failCombat(final DuelProofSession session, final RuntimeException failure) {
		if (session == null) {
			return false;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.COMBAT
				&& session.phase != DuelProofSession.Phase.TERMINAL_CAPTURED) {
				return false;
			}
			session.phase = DuelProofSession.Phase.ABORTED;
			session.continuation = null;
			session.terminalCompletion = null;
			session.wipePreCombatSecrets();
		}
		LOGGER.error("Melee proof execution failed during duel {} for players {} and {}",
			session.getProofId(), session.first.getDatabaseID(), session.second.getDatabaseID(), failure);
		runCombatFailureCleanup(session, "clear first proof reference", () ->
			session.first.getDuel().clearProofSession(session));
		runCombatFailureCleanup(session, "clear second proof reference", () ->
			session.second.getDuel().clearProofSession(session));
		runCombatFailureCleanup(session, "clear first melee momentum", () ->
			CombatFormula.clearPvpMeleeMomentum(session.first));
		runCombatFailureCleanup(session, "clear second melee momentum", () ->
			CombatFormula.clearPvpMeleeMomentum(session.second));
		runCombatFailureCleanup(session, "notify first client", () ->
			sendControl(session.first, "v1|abort|" + session.getProofId() + "|state"));
		runCombatFailureCleanup(session, "notify second client", () ->
			sendControl(session.second, "v1|abort|" + session.getProofId() + "|state"));
		runCombatFailureCleanup(session, "message first player", () ->
			session.first.message("Duel stopped without transferring stakes because melee verification failed."));
		runCombatFailureCleanup(session, "message second player", () ->
			session.second.message("Duel stopped without transferring stakes because melee verification failed."));
		runCombatFailureCleanup(session, "persist abort", () -> persistAbort(session, "state"));
		return true;
	}

	/** Ends an allowed proof duel retreat without treating it as a verification failure. */
	public static boolean cancelForRetreat(final DuelProofSession session,
										 final Player retreatingPlayer) {
		if (session == null || retreatingPlayer == null) {
			return false;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.COMBAT
				|| session.ordinal(retreatingPlayer) < 0) {
				return false;
			}
			session.phase = DuelProofSession.Phase.ABORTED;
			session.continuation = null;
			session.terminalCompletion = null;
			session.wipePreCombatSecrets();
		}

		session.first.getDuel().clearProofSession(session);
		session.second.getDuel().clearProofSession(session);
		CombatFormula.clearPvpMeleeMomentum(session.first);
		CombatFormula.clearPvpMeleeMomentum(session.second);
		sendControl(session.first, "v1|abort|" + session.getProofId() + "|retreat");
		sendControl(session.second, "v1|abort|" + session.getProofId() + "|retreat");
		final Player opponent = retreatingPlayer == session.first ? session.second : session.first;
		retreatingPlayer.message("You retreated. The duel ended and no stakes were transferred.");
		opponent.message("Your opponent retreated. The duel ended and no stakes were transferred.");
		persistAbort(session, "retreat");
		return true;
	}

	/**
	 * Freezes and independently verifies the exact terminal tape before lethal damage or
	 * any stake mutation is allowed to run.
	 */
	public static boolean captureTerminal(final DuelProofSession session, final Player winner,
									  final Player loser, final int terminalCause,
									  final DuelJournalSession journal) {
		if (session == null) {
			return false;
		}
		RuntimeException failure = null;
		try {
			synchronized (session) {
				if (session.phase != DuelProofSession.Phase.COMBAT
					|| winner == null || loser == null || winner == loser
					|| session.ordinal(winner) < 0 || session.ordinal(loser) < 0
					|| session.ordinal(winner) == session.ordinal(loser)
					|| journal == null
					|| session.first.getDuel().getJournalSession() != journal
					|| session.second.getDuel().getJournalSession() != journal
					|| !session.first.getDuel().checkDuelItems()
					|| !session.second.getDuel().checkDuelItems()
					|| !session.firstAck || !session.secondAck
					|| session.meleeReplay == null || session.finalLockHash == null
					|| session.firstClientCommitment == null
					|| session.secondClientCommitment == null
					|| session.firstClientSeed == null || session.secondClientSeed == null) {
					throw new IllegalStateException("duel proof terminal capture state is incomplete");
				}

				final List<DuelProofMeleeSwing> liveSwings = session.meleeReplay.getSwings();
				if (liveSwings.isEmpty()
					|| liveSwings.size() > DuelProofSpec.MAX_WITNESS_SWINGS) {
					throw new IllegalStateException("duel proof terminal transcript length is invalid");
				}
				final ArrayList<DuelProofTerminalSwing> tape =
					new ArrayList<DuelProofTerminalSwing>(liveSwings.size());
				for (final DuelProofMeleeSwing swing : liveSwings) {
					tape.add(new DuelProofTerminalSwing(swing.getSwingNumber(),
						swing.getActorOrdinal(), swing.getInput()));
				}
				final List<DuelProofTerminalParticipant> participants = Arrays.asList(
					new DuelProofTerminalParticipant(0, session.first.getDatabaseID(),
						session.firstClientCommitment, session.firstClientSeed,
						session.finalLockHash),
					new DuelProofTerminalParticipant(1, session.second.getDatabaseID(),
						session.secondClientCommitment, session.secondClientSeed,
						session.finalLockHash)
				);
				final long finishedAt = Math.max(System.currentTimeMillis(), session.createdAt);
				final DuelProofTerminalWitness witness = DuelProofTerminalWitness.createV2(
					session.proofIdBytes, session.contextBytes, session.contextHash,
					session.serverCommitment, session.serverSeed, session.finalLockHash,
					participants, session.meleeReplay.getStarterOrdinal(), session.ordinal(winner),
					terminalCause, finishedAt, session.meleeReplay.getCandidateDrawCount(), tape);
				final byte[] witnessBytes = DuelProofTerminalWitnessCodec.encode(witness);
				final DuelProofTerminalVerifier.Verification verified =
					DuelProofTerminalVerifier.verify(witnessBytes);
				if (!liveSwings.equals(verified.getComputedSwings())) {
					throw new IllegalStateException("independent terminal replay differs from live combat");
				}

				final DuelReceipt receipt = journal.complete(winner, loser, finishedAt);
				if (receipt == null) {
					throw new IllegalStateException("duel journal could not freeze terminal receipt");
				}
				verifyReceiptMatchesReplay(receipt, session, verified.getContext(),
					verified.getComputedSwings(),
					winner, finishedAt);
				final String causeName = terminalCause == DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE
					? "DIRECT_MELEE" : "RECOIL";
				final DuelProofWitnessRecord record = new DuelProofWitnessRecord(
					session.getProofId(), witness.getWitnessVersion(), witnessBytes,
					verified.getWitnessHash(), witness.getStarterOrdinal(), liveSwings.size(),
					winner.getDatabaseID(), causeName, finishedAt);
				session.terminalCompletion = new DuelProofCompletion(receipt, record,
					winner.getDatabaseID(), loser.getDatabaseID());
				session.phase = DuelProofSession.Phase.TERMINAL_CAPTURED;
				session.wipePreCombatSecrets();
				return true;
			}
		} catch (final RuntimeException invalidTerminal) {
			failure = invalidTerminal;
		}
		failCombat(session, failure == null
			? new IllegalStateException("duel proof terminal capture failed") : failure);
		return false;
	}

	/** True only for the winner/loser pair bound into the frozen terminal witness. */
	public static boolean canSettle(final DuelProofSession session, final Player winner,
									final Player loser) {
		if (session == null || winner == null || loser == null) {
			return false;
		}
		synchronized (session) {
			return session.phase == DuelProofSession.Phase.TERMINAL_CAPTURED
				&& session.terminalCompletion != null
				&& session.terminalCompletion.winnerPlayerId == winner.getDatabaseID()
				&& session.terminalCompletion.loserPlayerId == loser.getDatabaseID()
				&& session.first.getDuel().checkDuelItems()
				&& session.second.getDuel().checkDuelItems();
		}
	}

	/** Queues one atomic receipt insert plus COMBAT -> VERIFIED witness link after payout. */
	public static boolean completeSettlement(final DuelProofSession session, final Player winner,
										 final Player loser) {
		if (session == null || winner == null || loser == null) {
			return false;
		}
		final DuelProofCompletion completion;
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.TERMINAL_CAPTURED
				|| session.terminalCompletion == null
				|| session.terminalCompletion.winnerPlayerId != winner.getDatabaseID()
				|| session.terminalCompletion.loserPlayerId != loser.getDatabaseID()) {
				return false;
			}
			// Inventory ownership was checked immediately before payout by canSettle().
			// Rechecking it here would always reject after the losing stake was removed.
			completion = session.terminalCompletion;
			session.phase = DuelProofSession.Phase.PERSISTING_COMPLETION;
		}
		session.first.getDuel().clearJournalSession(
			session.first.getDuel().getJournalSession());
		session.second.getDuel().clearJournalSession(
			session.second.getDuel().getJournalSession());

		final Server server = winner.getWorld().getServer();
		server.submitSql(() -> {
			final long[] duelId = {0L};
			final boolean persisted = server.getDatabase().atomically(() -> {
				duelId[0] = server.getDatabase().queryInsertDuelReceipt(completion.receipt);
				if (!server.getDatabase().queryVerifyDuelProofAttempt(session.getProofId(),
					duelId[0], completion.witness)) {
					throw new IllegalStateException("terminal proof did not transition to VERIFIED");
				}
			});
			synchronized (session) {
				if (session.phase == DuelProofSession.Phase.PERSISTING_COMPLETION) {
					session.phase = persisted
						? DuelProofSession.Phase.VERIFIED : DuelProofSession.Phase.ABORTED;
					session.terminalCompletion = null;
				}
			}
			if (!persisted) {
				LOGGER.error("Unable to atomically persist verified duel proof {} for players {} and {}",
					session.getProofId(), winner.getDatabaseID(), loser.getDatabaseID());
				final boolean[] updated = {false};
				final boolean committed = server.getDatabase().atomically(() ->
					updated[0] = server.getDatabase().queryAbortDuelProofAttempt(session.getProofId(),
						System.currentTimeMillis(), "DATABASE"));
				final boolean aborted = committed && updated[0];
				if (!aborted) {
					LOGGER.error("Unable to abort terminal proof {} after persistence failure",
						session.getProofId());
				}
			}
		});
		return true;
	}

	private static void verifyReceiptMatchesReplay(final DuelReceipt receipt,
											 final DuelProofSession session,
											 final DuelProofContext context,
											 final List<DuelProofMeleeSwing> computed,
											 final Player winner, final long finishedAt) {
		if (context == null || receipt.completedAt != finishedAt
			|| receipt.participants.size() != 2
			|| receipt.swings.size() != computed.size()) {
			throw new IllegalStateException("terminal receipt header does not match proof replay");
		}
		final Map<Integer, DuelReceiptParticipant> participants = new HashMap<>();
		for (final DuelReceiptParticipant participant : receipt.participants) {
			if (participant == null || participants.put(participant.playerId, participant) != null) {
				throw new IllegalStateException("terminal receipt participants are invalid");
			}
		}
		if (!participants.containsKey(session.first.getDatabaseID())
			|| !participants.containsKey(session.second.getDatabaseID())
			|| !participants.get(winner.getDatabaseID()).won
			|| participants.get(winner == session.first
				? session.second.getDatabaseID() : session.first.getDatabaseID()).won) {
			throw new IllegalStateException("terminal receipt winner does not match proof replay");
		}
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			final DuelProofContextParticipant committed = context.getParticipant(ordinal);
			final DuelReceiptParticipant recorded = participants.get(committed.getPlayerId());
			if (recorded == null || !committed.getUsername().equals(recorded.username)) {
				throw new IllegalStateException("terminal receipt identity does not match proof context");
			}
		}

		final Map<String, DuelReceiptStake> receiptStakes = new HashMap<>();
		for (final DuelReceiptStake stake : receipt.stakes) {
			if (stake == null || receiptStakes.put(
				stake.ownerPlayerId + ":" + stake.slot, stake) != null) {
				throw new IllegalStateException("terminal receipt stakes are invalid");
			}
		}
		int committedStakeCount = 0;
		for (int ordinal = 0; ordinal < 2; ordinal++) {
			final DuelProofContextParticipant participant = context.getParticipant(ordinal);
			for (final DuelProofContextItem committed : participant.getStakes()) {
				committedStakeCount++;
				final DuelReceiptStake recorded = receiptStakes.get(
					participant.getPlayerId() + ":" + committed.getSlot());
				if (recorded == null || recorded.catalogId != committed.getItemId()
					|| recorded.amount != committed.getAmount()
					|| recorded.noted != committed.isNoted()) {
					throw new IllegalStateException("terminal receipt stakes do not match proof context");
				}
			}
		}
		if (receiptStakes.size() != committedStakeCount) {
			throw new IllegalStateException("terminal receipt contains an uncommitted stake");
		}

		final Map<String, DuelReceiptSwing> receiptSwings = new HashMap<>();
		for (final DuelReceiptSwing swing : receipt.swings) {
			if (swing == null || receiptSwings.put(
				swing.actorPlayerId + ":" + swing.swingNumber, swing) != null) {
				throw new IllegalStateException("terminal receipt swing rows are invalid");
			}
		}
		final int[] perActorNumber = {0, 0};
		for (final DuelProofMeleeSwing swing : computed) {
			final int ordinal = swing.getActorOrdinal();
			final int playerId = ordinal == 0
				? session.first.getDatabaseID() : session.second.getDatabaseID();
			final DuelReceiptSwing receiptSwing = receiptSwings.get(
				playerId + ":" + (++perActorNumber[ordinal]));
			if (receiptSwing == null
				|| receiptSwing.combatStyle != swing.getInput().getAttackerCombatStyle()
				|| receiptSwing.didHit != swing.getResult().isHit()
				|| receiptSwing.damage != swing.getResult().getDamage()) {
				throw new IllegalStateException("terminal receipt swings do not match proof replay");
			}
		}
	}

	private static void runCombatFailureCleanup(final DuelProofSession session,
										 final String operation, final Runnable task) {
		try {
			task.run();
		} catch (final RuntimeException cleanupFailure) {
			LOGGER.error("Unable to {} for failed melee proof {}", operation,
				session.getProofId(), cleanupFailure);
		}
	}

	/** Called by Duel.resetAll after it has removed its own expected-identity reference. */
	public static void onDuelReset(final DuelProofSession session) {
		if (session == null) {
			return;
		}
		final DuelProofSession.Phase phase;
		synchronized (session) {
			phase = session.phase;
			if (phase == DuelProofSession.Phase.PERSISTING_COMPLETION
				|| phase == DuelProofSession.Phase.VERIFIED
				|| phase == DuelProofSession.Phase.ABORTED) {
				session.first.getDuel().clearProofSession(session);
				session.second.getDuel().clearProofSession(session);
				session.wipePreCombatSecrets();
				return;
			}
		}
		if (phase == DuelProofSession.Phase.COMBAT
			|| phase == DuelProofSession.Phase.TERMINAL_CAPTURED) {
			failCombat(session,
				new IllegalStateException("duel reset before terminal settlement completed"));
			return;
		}
		abortInternal(session, "cancelled", false);
	}

	private static void handleCommitment(final DuelProofSession session, final Player player,
										 final byte[] commitment) {
		boolean persist = false;
		boolean invalid = false;
		synchronized (session) {
			final int ordinal = session.ordinal(player);
			final byte[] existing = ordinal == 0
				? session.firstClientCommitment : session.secondClientCommitment;
			if (ordinal < 0) {
				invalid = true;
			} else if (existing != null) {
				invalid = !session.same(existing, commitment);
			} else if (session.phase != DuelProofSession.Phase.AWAITING_CLIENT_COMMITMENTS) {
				invalid = true;
			} else {
				if (ordinal == 0) {
					session.firstClientCommitment = Arrays.copyOf(commitment, commitment.length);
				} else {
					session.secondClientCommitment = Arrays.copyOf(commitment, commitment.length);
				}
				if (session.firstClientCommitment != null && session.secondClientCommitment != null) {
					session.phase = DuelProofSession.Phase.PERSISTING_CLIENT_COMMITMENTS;
					persist = true;
				}
			}
		}
		if (invalid) {
			abort(session, "state");
		} else if (persist) {
			persistClientCommitments(session);
		}
	}

	private static void handleReveal(final DuelProofSession session, final Player player,
									 final byte[] seed) {
		boolean persist = false;
		boolean invalid = false;
		synchronized (session) {
			final int ordinal = session.ordinal(player);
			final byte[] existing = ordinal == 0 ? session.firstClientSeed : session.secondClientSeed;
			final byte[] committed = ordinal == 0
				? session.firstClientCommitment : session.secondClientCommitment;
			if (ordinal < 0 || committed == null) {
				invalid = true;
			} else if (existing != null) {
				invalid = !session.same(existing, seed);
			} else if (session.phase != DuelProofSession.Phase.AWAITING_CLIENT_REVEALS) {
				invalid = true;
			} else {
				final byte[] expected = DuelProofCrypto.clientCommitment(session.contextHash,
					session.serverCommitment, ordinal, seed);
				if (!session.same(expected, committed)) {
					invalid = true;
				} else {
					if (ordinal == 0) {
						session.firstClientSeed = Arrays.copyOf(seed, seed.length);
					} else {
						session.secondClientSeed = Arrays.copyOf(seed, seed.length);
					}
					if (session.firstClientSeed != null && session.secondClientSeed != null) {
						session.finalLockHash = DuelProofCrypto.finalLockHash(
							session.proofIdBytes, session.contextHash, session.serverCommitment,
							session.first.getDatabaseID(), session.firstClientCommitment,
							session.second.getDatabaseID(), session.secondClientCommitment);
						session.masterSeed = DuelProofCrypto.masterSeed(session.contextHash,
							session.serverSeed, session.firstClientSeed, session.secondClientSeed);
						session.phase = DuelProofSession.Phase.PERSISTING_CLIENT_REVEALS;
						persist = true;
					}
				}
			}
		}
		if (invalid) {
			abort(session, "malformed");
		} else if (persist) {
			persistClientReveals(session);
		}
	}

	private static void handleLockAck(final DuelProofSession session, final Player player,
									  final byte[] lockHash) {
		boolean persist = false;
		boolean invalid = false;
		synchronized (session) {
			final int ordinal = session.ordinal(player);
			if (ordinal < 0 || session.finalLockHash == null
				|| !session.same(session.finalLockHash, lockHash)) {
				invalid = true;
			} else if ((ordinal == 0 && session.firstAck) || (ordinal == 1 && session.secondAck)) {
				return;
			} else if (session.phase != DuelProofSession.Phase.AWAITING_LOCK_ACKS) {
				invalid = true;
			} else {
				if (ordinal == 0) {
					session.firstAck = true;
				} else {
					session.secondAck = true;
				}
				if (session.firstAck && session.secondAck) {
					session.phase = DuelProofSession.Phase.PERSISTING_LOCK;
					persist = true;
				}
			}
		}
		if (invalid) {
			abort(session, "state");
		} else if (persist) {
			persistLock(session);
		}
	}

	private static void persistInitialCommitment(final DuelProofSession session) {
		final Server server = session.first.getWorld().getServer();
		server.submitSql(() -> {
			final boolean persisted = server.getDatabase().atomically(() ->
				server.getDatabase().queryInsertDuelProofAttempt(session.attempt));
			server.getGameEventHandler().submit(() -> onInitialCommitmentPersisted(session, persisted),
				"Duel proof server commitment persisted");
		});
	}

	private static void onInitialCommitmentPersisted(final DuelProofSession session,
											 final boolean persisted) {
		if (!persisted) {
			abort(session, "database");
			return;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_SERVER_COMMITMENT) {
				return;
			}
			if (!isLiveAndAttached(session)) {
				// Abort outside the monitor so reset callbacks cannot nest player state mutation here.
			} else {
				session.phase = DuelProofSession.Phase.AWAITING_CLIENT_COMMITMENTS;
				sendCommitChallenge(session, session.first, 0);
				sendCommitChallenge(session, session.second, 1);
				return;
			}
		}
		abort(session, "disconnected");
	}

	private static void persistClientCommitments(final DuelProofSession session) {
		final Server server = session.first.getWorld().getServer();
		final byte[] firstCommit = Arrays.copyOf(session.firstClientCommitment,
			session.firstClientCommitment.length);
		final byte[] secondCommit = Arrays.copyOf(session.secondClientCommitment,
			session.secondClientCommitment.length);
		final long updatedAt = System.currentTimeMillis();
		server.submitSql(() -> {
			final boolean persisted = server.getDatabase().atomically(() ->
				server.getDatabase().queryStoreDuelProofCommitments(session.getProofId(),
					session.first.getDatabaseID(), firstCommit,
					session.second.getDatabaseID(), secondCommit, updatedAt));
			server.getGameEventHandler().submit(() -> onClientCommitmentsPersisted(session, persisted),
				"Duel proof client commitments persisted");
		});
	}

	private static void onClientCommitmentsPersisted(final DuelProofSession session,
											  final boolean persisted) {
		if (!persisted) {
			abort(session, "database");
			return;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_CLIENT_COMMITMENTS) {
				return;
			}
			if (isLiveAndAttached(session)) {
				session.phase = DuelProofSession.Phase.AWAITING_CLIENT_REVEALS;
				sendRevealRequest(session, session.first);
				sendRevealRequest(session, session.second);
				return;
			}
		}
		abort(session, "disconnected");
	}

	private static void persistClientReveals(final DuelProofSession session) {
		final Server server = session.first.getWorld().getServer();
		final byte[] firstSeed = Arrays.copyOf(session.firstClientSeed, session.firstClientSeed.length);
		final byte[] secondSeed = Arrays.copyOf(session.secondClientSeed, session.secondClientSeed.length);
		final byte[] lockHash = Arrays.copyOf(session.finalLockHash, session.finalLockHash.length);
		final long updatedAt = System.currentTimeMillis();
		server.submitSql(() -> {
			final boolean persisted = server.getDatabase().atomically(() ->
				server.getDatabase().queryStoreDuelProofReveals(session.getProofId(),
					session.first.getDatabaseID(), firstSeed,
					session.second.getDatabaseID(), secondSeed, lockHash, updatedAt));
			server.getGameEventHandler().submit(() -> onClientRevealsPersisted(session, persisted),
				"Duel proof client reveals persisted");
		});
	}

	private static void onClientRevealsPersisted(final DuelProofSession session,
											final boolean persisted) {
		if (!persisted) {
			abort(session, "database");
			return;
		}
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_CLIENT_REVEALS) {
				return;
			}
			if (isLiveAndAttached(session)) {
				session.phase = DuelProofSession.Phase.AWAITING_LOCK_ACKS;
				sendFinalLock(session, session.first);
				sendFinalLock(session, session.second);
				return;
			}
		}
		abort(session, "disconnected");
	}

	private static void persistLock(final DuelProofSession session) {
		final Server server = session.first.getWorld().getServer();
		final byte[] lockHash = Arrays.copyOf(session.finalLockHash, session.finalLockHash.length);
		final long lockedAt = System.currentTimeMillis();
		final boolean combineCombatStart;
		synchronized (session) {
			combineCombatStart = session.phase == DuelProofSession.Phase.PERSISTING_LOCK
				&& canStartCombatImmediately(session);
			if (combineCombatStart) {
				session.phase = DuelProofSession.Phase.PERSISTING_COMBAT;
			}
		}
		if (combineCombatStart) {
			scheduleCombatStartTimeout(session);
		}
		server.submitSql(() -> {
			final boolean[] combatStarted = {!combineCombatStart};
			final boolean committed = server.getDatabase().atomically(() -> {
				server.getDatabase().queryLockDuelProofAttempt(session.getProofId(),
					session.first.getDatabaseID(), lockHash,
					session.second.getDatabaseID(), lockHash, lockedAt);
				if (combineCombatStart) {
					combatStarted[0] = server.getDatabase().queryMarkDuelProofAttemptCombat(
						session.getProofId(), lockedAt);
				}
			});
			final boolean persisted = committed && combatStarted[0];
			server.getGameEventHandler().submit(() -> {
				if (combineCombatStart) {
					onCombinedLockAndCombatPersisted(session, persisted);
				} else {
					onLockPersisted(session, persisted);
				}
			}, combineCombatStart
				? "Duel proof lock and combat start persisted"
				: "Duel proof final lock persisted");
		});
	}

	/** Caller holds the session monitor on the game thread. */
	private static boolean canStartCombatImmediately(final DuelProofSession session) {
		try {
			return combatStartInvalidReason(session) == null
				&& session.first.withinRange(session.second, 1)
				&& PathValidation.checkAdjacentDistance(session.first.getWorld(),
					session.first.getLocation(), session.second.getLocation(), true, false);
		} catch (final RuntimeException invalidPath) {
			return false;
		}
	}

	private static void onCombinedLockAndCombatPersisted(final DuelProofSession session,
												 final boolean persisted) {
		final Runnable continuation;
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_COMBAT) {
				return;
			}
			continuation = session.continuation;
			session.continuation = null;
		}
		if (continuation == null) {
			abort(session, "state");
			return;
		}
		onCombatStartPersisted(session, persisted, continuation);
	}

	private static void onLockPersisted(final DuelProofSession session, final boolean persisted) {
		if (!persisted) {
			abort(session, "database");
			return;
		}
		final Runnable continuation;
		final String abortReason;
		synchronized (session) {
			if (session.phase != DuelProofSession.Phase.PERSISTING_LOCK) {
				return;
			}
			if (!isLiveAndAttached(session)) {
				continuation = null;
				abortReason = "disconnected";
			} else if (!contextStillMatches(session)) {
				continuation = null;
				abortReason = "state";
			} else {
				session.phase = DuelProofSession.Phase.LOCKED;
				continuation = session.continuation;
				session.continuation = null;
				abortReason = continuation == null ? "state" : null;
			}
		}
		if (continuation == null) {
			abort(session, abortReason);
			return;
		}
		scheduleCombatStartTimeout(session);
		try {
			continuation.run();
		} catch (final RuntimeException failure) {
			LOGGER.error("Unable to commence durably locked duel proof {}", session.getProofId(), failure);
			abort(session, "state");
		}
	}

	private static void scheduleTimeout(final DuelProofSession session) {
		session.first.getWorld().getServer().getGameEventHandler().add(new SingleEvent(
			session.first.getWorld(), null, HANDSHAKE_TIMEOUT_MS, "Duel proof handshake timeout",
			DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void action() {
					synchronized (session) {
						if (session.phase == DuelProofSession.Phase.LOCKED
							|| session.phase == DuelProofSession.Phase.PERSISTING_COMBAT
							|| session.phase == DuelProofSession.Phase.COMBAT
						|| session.phase == DuelProofSession.Phase.ABORTED) {
						return;
					}
				}
				abort(session, "timeout");
			}
		});
	}

	private static void scheduleCombatStartTimeout(final DuelProofSession session) {
		session.first.getWorld().getServer().getGameEventHandler().add(new SingleEvent(
			session.first.getWorld(), null, COMBAT_START_TIMEOUT_MS,
			"Duel proof combat-start timeout", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void action() {
				synchronized (session) {
					if (session.phase == DuelProofSession.Phase.COMBAT
						|| session.phase == DuelProofSession.Phase.ABORTED) {
						return;
					}
					if (session.phase != DuelProofSession.Phase.LOCKED
						&& session.phase != DuelProofSession.Phase.PERSISTING_COMBAT) {
						return;
					}
				}
				abort(session, "timeout");
			}
		});
	}

	private static boolean contextStillMatches(final DuelProofSession session) {
		byte[] currentContext = null;
		byte[] currentHash = null;
		try {
			currentContext = DuelProofContextEncoder.encode(
				session.first, session.second, session.proofIdBytes);
			currentHash = DuelProofCrypto.contextHash(currentContext);
			return session.same(session.contextHash, currentHash);
		} catch (final RuntimeException invalidState) {
			LOGGER.warn("Unable to revalidate locked duel-proof context {}",
				session.getProofId(), invalidState);
			return false;
		} finally {
			if (currentContext != null) {
				Arrays.fill(currentContext, (byte) 0);
			}
			if (currentHash != null) {
				Arrays.fill(currentHash, (byte) 0);
			}
		}
	}

	private static void sendCommitChallenge(final DuelProofSession session, final Player player,
										final int ordinal) {
		sendContext(session, player);
		sendControl(player, "v1|commit|" + session.getProofId() + "|"
			+ DuelProofCodec.hexLower(session.contextHash) + "|"
			+ DuelProofCodec.hexLower(session.serverCommitment) + "|" + ordinal);
	}

	/** Sends the canonical committed context before asking the client to contribute entropy. */
	private static void sendContext(final DuelProofSession session, final Player player) {
		final int chunks = (session.contextBytes.length + CONTEXT_CHUNK_BYTES - 1)
			/ CONTEXT_CHUNK_BYTES;
		for (int chunk = 0; chunk < chunks; chunk++) {
			final int start = chunk * CONTEXT_CHUNK_BYTES;
			final int end = Math.min(session.contextBytes.length, start + CONTEXT_CHUNK_BYTES);
			final byte[] bytes = Arrays.copyOfRange(session.contextBytes, start, end);
			try {
				sendControl(player, "v1|context|" + session.getProofId() + "|" + chunk
					+ "|" + chunks + "|" + DuelProofCodec.hexLower(bytes));
			} finally {
				Arrays.fill(bytes, (byte) 0);
			}
		}
	}

	private static void sendRevealRequest(final DuelProofSession session, final Player player) {
		sendControl(player, "v1|reveal|" + session.getProofId() + "|"
			+ session.first.getDatabaseID() + "|"
			+ DuelProofCodec.hexLower(session.firstClientCommitment) + "|"
			+ session.second.getDatabaseID() + "|"
			+ DuelProofCodec.hexLower(session.secondClientCommitment));
	}

	private static void sendFinalLock(final DuelProofSession session, final Player player) {
		sendControl(player, "v1|lock|" + session.getProofId() + "|"
			+ DuelProofCodec.hexLower(session.contextHash) + "|"
			+ DuelProofCodec.hexLower(session.serverCommitment) + "|"
			+ session.first.getDatabaseID() + "|"
			+ DuelProofCodec.hexLower(session.firstClientCommitment) + "|"
			+ session.second.getDatabaseID() + "|"
			+ DuelProofCodec.hexLower(session.secondClientCommitment) + "|"
			+ DuelProofCodec.hexLower(session.finalLockHash));
	}

	private static void sendControl(final Player player, final String payload) {
		if (player != null && player.loggedIn() && !player.isRemoved()) {
			ActionSender.sendMessage(player, null, MessageType.QUEST, CLIENT_PREFIX + payload, 0, null);
		}
	}

	private static void abortInternal(final DuelProofSession session, final String reason,
									  final boolean resetDuels) {
		if (session == null) {
			return;
		}
		synchronized (session) {
			if (session.phase == DuelProofSession.Phase.ABORTED
				|| session.phase == DuelProofSession.Phase.COMBAT
				|| session.phase == DuelProofSession.Phase.TERMINAL_CAPTURED
				|| session.phase == DuelProofSession.Phase.PERSISTING_COMPLETION
				|| session.phase == DuelProofSession.Phase.VERIFIED) {
				return;
			}
			session.phase = DuelProofSession.Phase.ABORTED;
			session.continuation = null;
			session.wipePreCombatSecrets();
		}

		session.first.getDuel().clearProofSession(session);
		session.second.getDuel().clearProofSession(session);
		clearPendingPlayer(session.first);
		clearPendingPlayer(session.second);
		sendControl(session.first, "v1|abort|" + session.getProofId() + "|" + reason);
		sendControl(session.second, "v1|abort|" + session.getProofId() + "|" + reason);
		final String message = abortMessage(reason);
		session.first.message(message);
		session.second.message(message);
		persistAbort(session, reason);

		if (resetDuels) {
			session.first.getDuel().resetAll();
			session.second.getDuel().resetAll();
		}
	}

	private static void persistAbort(final DuelProofSession session, final String reason) {
		final Server server = session.first.getWorld().getServer();
		final String proofId = session.getProofId();
		final long finishedAt = System.currentTimeMillis();
		final String databaseReason = reason.toUpperCase().replace('-', '_');
		server.submitSql(() -> {
			final boolean[] updated = {false};
			final boolean committed = server.getDatabase().atomically(() ->
				updated[0] = server.getDatabase().queryAbortDuelProofAttempt(
					proofId, finishedAt, databaseReason));
			if (!committed || !updated[0]) {
				LOGGER.error("Unable to mark duel proof {} aborted", proofId);
			}
		});
	}

	private static void clearPendingPlayer(final Player player) {
		if (player == null) {
			return;
		}
		player.setBusy(false);
		player.resetPath();
		player.setWalkToAction(null);
	}

	private static boolean isLiveAndAttached(final DuelProofSession session) {
		return session.first.getDuel().getProofSession() == session
			&& session.second.getDuel().getProofSession() == session
			&& session.first.getDuel().getDuelRecipient() == session.second
			&& session.second.getDuel().getDuelRecipient() == session.first
			&& session.first.getDuel().isDuelActive() && session.second.getDuel().isDuelActive()
			&& session.first.getDuel().isDuelAccepted() && session.second.getDuel().isDuelAccepted()
			&& session.first.getDuel().isDuelConfirmAccepted()
			&& session.second.getDuel().isDuelConfirmAccepted()
			&& requiresProof(session.first, session.second)
			&& supportsProofClient(session.first) && supportsProofClient(session.second)
			&& channelHealthy(session.first) && channelHealthy(session.second);
	}

	private static boolean channelHealthy(final Player player) {
		return player != null && player.loggedIn() && !player.isRemoved()
			&& player.getChannel() != null && player.getChannel().isOpen()
			&& player.getChannel().isActive() && player.getChannel().isWritable();
	}

	private static boolean isCanonicalProofId(final String proofId) {
		try {
			DuelProofCodec.parseHexLowerExact(proofId, DuelProofSpec.PROOF_ID_BYTES);
			return true;
		} catch (final IllegalArgumentException invalid) {
			return false;
		}
	}

	private static boolean isClientFailureReason(final String reason) {
		return "entropy".equals(reason) || "malformed".equals(reason) || "state".equals(reason);
	}

	private static String canonicalAbortReason(final String reason) {
		if ("timeout".equals(reason) || "database".equals(reason) || "entropy".equals(reason)
			|| "malformed".equals(reason) || "state".equals(reason)
			|| "disconnected".equals(reason) || "cancelled".equals(reason)
			|| "unsupported".equals(reason) || "items".equals(reason)
			|| "unreachable".equals(reason) || "server-restart".equals(reason)
			|| "retreat".equals(reason)) {
			return reason;
		}
		return "state";
	}

	private static String abortMessage(final String reason) {
		if ("timeout".equals(reason)) {
			return "Duel cancelled before combat because melee verification timed out.";
		}
		if ("disconnected".equals(reason)) {
			return "Duel cancelled before combat because a player disconnected.";
		}
		if ("items".equals(reason)) {
			return "Duel cancelled before combat because a staked item changed.";
		}
		if ("unreachable".equals(reason)) {
			return "Duel cancelled before combat because the players could not meet.";
		}
		return "Duel cancelled before combat because melee verification could not be locked.";
	}

	private static void cancelWithoutSession(final Player first, final Player second,
										 final String message) {
		if (first != null) {
			clearPendingPlayer(first);
			first.message(message);
			first.getDuel().resetAll();
		}
		if (second != null) {
			clearPendingPlayer(second);
			second.message(message);
			second.getDuel().resetAll();
		}
	}
}
