package com.openrsc.server.content.dueljournal;

import com.openrsc.server.database.struct.DuelReceipt;
import com.openrsc.server.database.struct.DuelReceiptParticipant;
import com.openrsc.server.database.struct.DuelReceiptStake;
import com.openrsc.server.database.struct.DuelReceiptSwing;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One in-memory duel journal shared by the two participants from combat start
 * until death settlement. Cancelled duels simply lose their reference to it.
 */
public final class DuelJournalSession {
	private final long startedAt;
	private final int firstPlayerId;
	private final int secondPlayerId;
	private final String firstUsername;
	private final String secondUsername;
	private final List<DuelReceiptStake> stakes;
	private final Map<Integer, List<DuelReceiptSwing>> swingsByPlayer = new LinkedHashMap<>();
	private boolean completed;

	private DuelJournalSession(final Player first, final Player second) {
		startedAt = System.currentTimeMillis();
		firstPlayerId = first.getDatabaseID();
		secondPlayerId = second.getDatabaseID();
		firstUsername = first.getUsername();
		secondUsername = second.getUsername();
		final List<DuelReceiptStake> acceptedStakes = new ArrayList<>();
		snapshotOffer(first, acceptedStakes);
		snapshotOffer(second, acceptedStakes);
		stakes = Collections.unmodifiableList(acceptedStakes);
		swingsByPlayer.put(firstPlayerId, new ArrayList<DuelReceiptSwing>());
		swingsByPlayer.put(secondPlayerId, new ArrayList<DuelReceiptSwing>());
	}

	public static DuelJournalSession begin(final Player first, final Player second) {
		if (first == null || second == null || first == second) {
			throw new IllegalArgumentException("A duel journal requires two distinct players");
		}
		return new DuelJournalSession(first, second);
	}

	private static void snapshotOffer(final Player owner, final List<DuelReceiptStake> target) {
		final List<Item> offeredItems = owner.getDuel().getDuelOffer().getItems();
		synchronized (offeredItems) {
			for (int slot = 0; slot < offeredItems.size(); slot++) {
				final Item item = offeredItems.get(slot);
				if (item != null && item.getAmount() > 0) {
					target.add(new DuelReceiptStake(owner.getDatabaseID(), slot, item.getCatalogId(),
						item.getAmount(), item.getNoted()));
				}
			}
		}
	}

	public synchronized void recordMeleeSwing(final Player actor, final int combatStyle,
										 final boolean didHit, final int damage) {
		if (completed || actor == null || damage < 0) {
			return;
		}
		final List<DuelReceiptSwing> actorSwings = swingsByPlayer.get(actor.getDatabaseID());
		if (actorSwings == null) {
			return;
		}
		actorSwings.add(new DuelReceiptSwing(actor.getDatabaseID(), actorSwings.size() + 1,
			combatStyle, didHit, damage));
	}

	/**
	 * Produces the immutable receipt exactly once. Invalid or repeated completion
	 * attempts return null and cannot create a second ledger row.
	 */
	public synchronized DuelReceipt complete(final Player winner, final Player loser) {
		return complete(winner, loser, System.currentTimeMillis());
	}

	/**
	 * Produces the immutable receipt at the proof's captured terminal timestamp.
	 * Keeping one timestamp in the witness and receipt prevents a later database
	 * writer from linking otherwise unrelated completion records.
	 */
	public synchronized DuelReceipt complete(final Player winner, final Player loser,
										 final long completedAt) {
		if (completed || winner == null || loser == null || winner == loser
			|| winner.getDatabaseID() == loser.getDatabaseID()
			|| !isParticipant(winner.getDatabaseID()) || !isParticipant(loser.getDatabaseID())
			|| completedAt < startedAt) {
			return null;
		}
		completed = true;
		final List<DuelReceiptParticipant> participants = Arrays.asList(
			new DuelReceiptParticipant(firstPlayerId, firstUsername, winner.getDatabaseID() == firstPlayerId),
			new DuelReceiptParticipant(secondPlayerId, secondUsername, winner.getDatabaseID() == secondPlayerId)
		);
		final List<DuelReceiptSwing> allSwings = new ArrayList<>();
		allSwings.addAll(swingsByPlayer.get(firstPlayerId));
		allSwings.addAll(swingsByPlayer.get(secondPlayerId));
		return new DuelReceipt(startedAt, completedAt, participants, stakes, allSwings);
	}

	private boolean isParticipant(final int playerId) {
		return playerId == firstPlayerId || playerId == secondPlayerId;
	}
}
