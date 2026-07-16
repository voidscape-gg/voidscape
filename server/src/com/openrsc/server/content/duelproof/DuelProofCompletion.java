package com.openrsc.server.content.duelproof;

import com.openrsc.server.database.struct.DuelProofWitnessRecord;
import com.openrsc.server.database.struct.DuelReceipt;

/** Immutable terminal package detached from mutable combat state before settlement. */
final class DuelProofCompletion {
	final DuelReceipt receipt;
	final DuelProofWitnessRecord witness;
	final int winnerPlayerId;
	final int loserPlayerId;

	DuelProofCompletion(final DuelReceipt receipt, final DuelProofWitnessRecord witness,
						final int winnerPlayerId, final int loserPlayerId) {
		if (receipt == null || witness == null || winnerPlayerId <= 0 || loserPlayerId <= 0
			|| winnerPlayerId == loserPlayerId) {
			throw new IllegalArgumentException("invalid terminal duel proof completion");
		}
		this.receipt = receipt;
		this.witness = witness;
		this.winnerPlayerId = winnerPlayerId;
		this.loserPlayerId = loserPlayerId;
	}
}
