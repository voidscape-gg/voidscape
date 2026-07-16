package com.openrsc.server.content.dueljournal;

import com.openrsc.server.Server;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.DuelReceipt;
import com.openrsc.server.database.struct.DuelReceiptDetail;
import com.openrsc.server.database.struct.DuelReceiptHistoryEntry;
import com.openrsc.server.database.struct.DuelReceiptStake;
import com.openrsc.server.database.struct.DuelReceiptSwing;
import com.openrsc.server.event.rsc.ImmediateEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.voidscape.duelproof.DuelProofCodec;

/** Coordinates duel-journal lifecycle and off-thread persistence. */
public final class DuelJournalService {
	private static final Logger LOGGER = LogManager.getLogger();
	public static final int CLIENT_JOURNAL_VERSION = 10132;
	public static final int CLIENT_VERIFIED_JOURNAL_VERSION = 10134;
	public static final String CLIENT_PREFIX = "@vsduel@";
	private static final String REQUEST_ATTRIBUTE = "duel_journal_request";
	private static final String ACTIVE_DUEL_MESSAGE =
		"Finish or cancel your current duel, then use ::duel to view the result.";
	private static final int SWINGS_PER_CHUNK = 40;
	private static final int PROOF_BYTES_PER_CHUNK = 192;
	private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong(System.currentTimeMillis());

	private DuelJournalService() {
	}

	public static DuelJournalSession begin(final Player first, final Player second) {
		final DuelJournalSession session = DuelJournalSession.begin(first, second);
		first.getDuel().setJournalSession(session);
		second.getDuel().setJournalSession(session);
		return session;
	}

	public static void complete(final DuelJournalSession session, final Player winner, final Player loser) {
		if (session == null || winner == null || loser == null) {
			return;
		}
		final DuelReceipt receipt = session.complete(winner, loser);
		if (receipt == null) {
			return;
		}
		winner.getDuel().clearJournalSession(session);
		loser.getDuel().clearJournalSession(session);
		final Server server = winner.getWorld().getServer();
		final int winnerId = winner.getDatabaseID();
		final int loserId = loser.getDatabaseID();
		server.submitSql(() -> {
			final boolean inserted = server.getDatabase().atomically(() ->
				server.getDatabase().queryInsertDuelReceipt(receipt));
			if (!inserted) {
				LOGGER.error("Unable to persist completed duel journal for players {} and {}",
					winnerId, loserId);
			}
		});
	}

	/** Loads one private duel-journal response without blocking the game thread. */
	public static void request(final Player player, final Long selectedDuelId) {
		if (player == null) {
			return;
		}
		final long requestId = NEXT_REQUEST_ID.incrementAndGet();
		player.setAttribute(REQUEST_ATTRIBUTE, requestId);
		if (rejectWhileDuelActive(player, requestId)) {
			return;
		}
		final Server server = player.getWorld().getServer();
		final World world = player.getWorld();
		final int requesterPlayerId = player.getDatabaseID();
		server.submitSql(() -> {
			DuelReceiptHistoryEntry[] history = new DuelReceiptHistoryEntry[0];
			DuelReceiptDetail detail = null;
			boolean missingSelection = false;
			String error = null;
			try {
				history = server.getDatabase().queryRecentDuelReceiptsForPlayer(requesterPlayerId);
				final long detailId;
				if (selectedDuelId != null) {
					detailId = selectedDuelId;
				} else {
					detailId = history.length == 0 ? 0L : history[0].duelId;
				}
				if (detailId > 0) {
					detail = server.getDatabase().queryDuelReceiptDetail(detailId, requesterPlayerId);
					missingSelection = selectedDuelId != null && detail == null;
				}
			} catch (final GameDatabaseException ex) {
				LOGGER.error("Unable to load duel journal for player {}", requesterPlayerId, ex);
				error = "Your duel journal could not be loaded right now.";
			}

			final DuelReceiptHistoryEntry[] loadedHistory = history;
			final DuelReceiptDetail loadedDetail = detail;
			final boolean selectionMissing = missingSelection;
			final String loadError = error;
			server.getGameEventHandler().add(new ImmediateEvent(world, "Send private duel journal") {
				@Override
				public void action() {
					if (!player.loggedIn() || player.isRemoved()
						|| player.getDatabaseID() != requesterPlayerId
						|| player.getAttribute(REQUEST_ATTRIBUTE, 0L) != requestId) {
						return;
					}
					if (rejectWhileDuelActive(player, requestId)) {
						return;
					}
					if (loadError != null) {
						player.message(player.getConfig().MESSAGE_PREFIX + loadError);
						return;
					}
					if (selectionMissing) {
						player.message(player.getConfig().MESSAGE_PREFIX + "That duel receipt was not found.");
						return;
					}
					if (supportsJournalClient(player)) {
						sendClientJournal(player, requestId, requesterPlayerId, loadedHistory, loadedDetail,
							player.getClientVersion() >= CLIENT_VERIFIED_JOURNAL_VERSION ? "v2" : "v1");
					} else {
						sendLegacySummary(player, loadedHistory, loadedDetail);
					}
				}
			});
		});
	}

	private static boolean rejectWhileDuelActive(final Player player, final long requestId) {
		if (!player.getDuel().isDuelActive()) {
			return false;
		}
		if (supportsJournalClient(player)
			&& player.getClientVersion() >= CLIENT_VERIFIED_JOURNAL_VERSION) {
			sendControl(player, "v2", requestId, "blocked|duel_active");
		} else {
			player.message(player.getConfig().MESSAGE_PREFIX + ACTIVE_DUEL_MESSAGE);
		}
		return true;
	}

	private static boolean supportsJournalClient(final Player player) {
		return player.isUsingCustomClient() && player.getClientVersion() >= CLIENT_JOURNAL_VERSION;
	}

	private static void sendClientJournal(final Player player, final long requestId,
										final int requesterPlayerId,
										final DuelReceiptHistoryEntry[] history,
										final DuelReceiptDetail detail,
										final String protocolVersion) {
		final long selectedId = detail == null ? 0L : detail.header.duelId;
		sendControl(player, protocolVersion, requestId,
			"begin|" + history.length + "|" + selectedId);

		final StringBuilder historyRows = new StringBuilder();
		for (final DuelReceiptHistoryEntry row : history) {
			appendRowSeparator(historyRows);
			historyRows.append(row.duelId).append(',').append(row.startedAt).append(',')
				.append(row.completedAt).append(',').append(row.requester.won ? 1 : 0).append(',')
				.append(row.opponent.playerId).append(',').append(clean(row.opponent.username));
		}
		sendControl(player, protocolVersion, requestId, "history|" + historyRows);

		if (detail != null) {
			sendControl(player, protocolVersion, requestId,
				"detail|" + detail.header.duelId + "|" + requesterPlayerId
				+ "|" + detail.header.opponent.playerId + "|" + (detail.header.requester.won ? 1 : 0)
				+ "|" + detail.header.startedAt + "|" + detail.header.completedAt + "|"
				+ clean(detail.header.opponent.username));

			final StringBuilder stakeRows = new StringBuilder();
			for (final DuelReceiptStake stake : detail.stakes) {
				appendRowSeparator(stakeRows);
				stakeRows.append(stake.ownerPlayerId == requesterPlayerId ? 1 : 0).append(',')
					.append(stake.slot).append(',').append(stake.catalogId).append(',')
					.append(stake.amount).append(',').append(stake.noted ? 1 : 0);
			}
			sendControl(player, protocolVersion, requestId, "stakes|" + stakeRows);
			sendSwingChunks(player, protocolVersion, requestId, detail.requesterSwings);
			if ("v2".equals(protocolVersion)) {
				sendProofChunks(player, requestId, detail);
			}
		}
		sendControl(player, protocolVersion, requestId, "end");
	}

	private static void sendSwingChunks(final Player player, final String protocolVersion,
										final long requestId,
										final List<DuelReceiptSwing> swings) {
		final int chunkCount = Math.max(1, (swings.size() + SWINGS_PER_CHUNK - 1) / SWINGS_PER_CHUNK);
		for (int chunk = 0; chunk < chunkCount; chunk++) {
			final StringBuilder rows = new StringBuilder();
			final int end = Math.min(swings.size(), (chunk + 1) * SWINGS_PER_CHUNK);
			for (int i = chunk * SWINGS_PER_CHUNK; i < end; i++) {
				final DuelReceiptSwing swing = swings.get(i);
				appendRowSeparator(rows);
				rows.append(swing.swingNumber).append(',').append(swing.combatStyle).append(',')
					.append(swing.didHit ? 1 : 0).append(',').append(swing.damage);
			}
			sendControl(player, protocolVersion, requestId,
				"swings|" + chunk + "|" + chunkCount + "|" + rows);
		}
	}

	private static void sendProofChunks(final Player player, final long requestId,
									final DuelReceiptDetail detail) {
		if (detail.proof == null) {
			sendControl(player, "v2", requestId, "proofmeta|0");
			return;
		}
		final byte[] witnessBytes = detail.proof.getWitnessBytes();
		final byte[] witnessHash = detail.proof.getWitnessHash32();
		final int chunkCount = Math.max(1,
			(witnessBytes.length + PROOF_BYTES_PER_CHUNK - 1) / PROOF_BYTES_PER_CHUNK);
		sendControl(player, "v2", requestId, "proofmeta|1|" + detail.proof.getProofId()
			+ "|" + detail.proof.getWitnessVersion() + "|" + witnessBytes.length + "|"
			+ DuelProofCodec.hexLower(witnessHash) + "|" + chunkCount);
		for (int chunk = 0; chunk < chunkCount; chunk++) {
			final int start = chunk * PROOF_BYTES_PER_CHUNK;
			final int end = Math.min(witnessBytes.length, start + PROOF_BYTES_PER_CHUNK);
			final byte[] encodedChunk = new byte[end - start];
			System.arraycopy(witnessBytes, start, encodedChunk, 0, encodedChunk.length);
			sendControl(player, "v2", requestId, "proofdata|" + chunk + "|" + chunkCount
				+ "|" + DuelProofCodec.hexLower(encodedChunk));
		}
		java.util.Arrays.fill(witnessBytes, (byte) 0);
	}

	private static void sendControl(final Player player, final String protocolVersion,
									final long requestId, final String payload) {
		ActionSender.sendMessage(player, null, MessageType.QUEST,
			CLIENT_PREFIX + protocolVersion + "|" + requestId + "|" + payload, 0, null);
	}

	private static void sendLegacySummary(final Player player, final DuelReceiptHistoryEntry[] history,
										  final DuelReceiptDetail detail) {
		if (history.length == 0 || detail == null) {
			player.message(player.getConfig().MESSAGE_PREFIX + "You have no completed duels in your journal yet.");
			return;
		}
		player.message(player.getConfig().MESSAGE_PREFIX + "Your last duel was a "
			+ (detail.header.requester.won ? "win" : "loss") + " against "
			+ detail.header.opponent.username + ", with " + detail.requesterSwings.size()
			+ " recorded melee swings. Update your Voidscape client to open the full journal.");
	}

	private static void appendRowSeparator(final StringBuilder builder) {
		if (builder.length() > 0) {
			builder.append(';');
		}
	}

	private static String clean(final String value) {
		return value == null ? "" : value.replace('|', ' ').replace(',', ' ')
			.replace(';', ' ').replace('\n', ' ').replace('\r', ' ').trim();
	}
}
