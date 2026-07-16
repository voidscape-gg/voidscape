package com.openrsc.server.database.struct;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DuelReceiptDetail {
	public final DuelReceiptHistoryEntry header;
	public final List<DuelReceiptStake> stakes;
	public final List<DuelReceiptSwing> requesterSwings;
	public final DuelProofWitnessRecord proof;

	public DuelReceiptDetail(final DuelReceiptHistoryEntry header, final List<DuelReceiptStake> stakes,
							 final List<DuelReceiptSwing> requesterSwings) {
		this(header, stakes, requesterSwings, null);
	}

	public DuelReceiptDetail(final DuelReceiptHistoryEntry header, final List<DuelReceiptStake> stakes,
							 final List<DuelReceiptSwing> requesterSwings,
							 final DuelProofWitnessRecord proof) {
		this.header = header;
		this.stakes = immutableCopy(stakes);
		this.requesterSwings = immutableCopy(requesterSwings);
		this.proof = proof;
	}

	private static <T> List<T> immutableCopy(final List<T> values) {
		if (values == null || values.isEmpty()) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(new ArrayList<>(values));
	}
}
