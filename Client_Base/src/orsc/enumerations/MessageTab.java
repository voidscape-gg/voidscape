package orsc.enumerations;

public enum MessageTab {
	// Client-side UI state only: never serialized and never sent on the wire
	// (lookup() below has no callers — verified again when GLOBAL was added).
	// GLOBAL is the voidscape-skin global-chat history tab; the classic 5-tab
	// strip never selects it.
	ALL(0), CHAT(1), QUEST(2), PRIVATE(3), GLOBAL(4);
	private static final MessageTab[] map;

	static {
		int cap = 0;
		for (MessageTab t : values())
			cap = Math.max(1 + t.rsID, cap);

		map = new MessageTab[cap];
		for (MessageTab t : values())
			if (t.rsID >= 0)
				map[t.rsID] = t;
	}

	private final int rsID;

	private MessageTab(int rsID) {
		this.rsID = rsID;
	}

	public static MessageTab lookup(int rsID) {
		if (rsID >= 0 && rsID < map.length)
			return map[rsID];
		return null;
	}
}
