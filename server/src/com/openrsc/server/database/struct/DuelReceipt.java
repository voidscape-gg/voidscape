package com.openrsc.server.database.struct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A complete, immutable duel receipt ready for one transactional database insert.
 */
public final class DuelReceipt {
	public final long duelId;
	public final long startedAt;
	public final long completedAt;
	public final List<DuelReceiptParticipant> participants;
	public final List<DuelReceiptStake> stakes;
	public final List<DuelReceiptSwing> swings;

	public DuelReceipt(final long startedAt, final long completedAt,
					   final List<DuelReceiptParticipant> participants,
					   final List<DuelReceiptStake> stakes,
					   final List<DuelReceiptSwing> swings) {
		this(0, startedAt, completedAt, participants, stakes, swings);
	}

	public DuelReceipt(final long duelId, final long startedAt, final long completedAt,
					   final List<DuelReceiptParticipant> participants,
					   final List<DuelReceiptStake> stakes,
					   final List<DuelReceiptSwing> swings) {
		this.duelId = duelId;
		this.startedAt = startedAt;
		this.completedAt = completedAt;
		this.participants = immutableCopy(participants);
		this.stakes = immutableCopy(stakes);
		this.swings = immutableCopy(swings);
	}

	private static <T> List<T> immutableCopy(final List<T> values) {
		if (values == null || values.isEmpty()) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}
