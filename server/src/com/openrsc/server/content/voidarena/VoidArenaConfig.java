package com.openrsc.server.content.voidarena;

import com.openrsc.server.model.Point;

public final class VoidArenaConfig {
	public static final String CURRENT_SEASON = "global";
	public static final int STARTING_RATING = 1200;
	public static final int ELO_K_FACTOR = 32;
	public static final int ELO_DIVISOR = 400;
	public static final int CHALLENGE_TIMEOUT_MS = 60_000;
	public static final int RATING_VISIBLE_MATCHES = 5;
	public static final int RATING_DISPLAY_CLIENT_VERSION = 10106;

	public static final int LOBBY_X = 600;
	public static final int LOBBY_Y = 82;
	public static final int LOBBY_MIN_X = 582;
	public static final int LOBBY_MIN_Y = 78;
	public static final int LOBBY_MAX_X = 616;
	public static final int LOBBY_MAX_Y = 84;
	public static final int EXIT_X = 113;
	public static final int EXIT_Y = 318;

	private static final ArenaSlot[] ARENA_SLOTS = {
		new ArenaSlot(0, 584, 65, 590, 77, 585, 72, 589, 72),
		new ArenaSlot(1, 592, 65, 598, 77, 593, 72, 597, 72),
		new ArenaSlot(2, 600, 65, 606, 77, 601, 72, 605, 72),
		new ArenaSlot(3, 608, 65, 614, 77, 609, 72, 613, 72),
	};

	public static Point lobbyTile() {
		return Point.location(LOBBY_X, LOBBY_Y);
	}

	public static Point exitTile() {
		return Point.location(EXIT_X, EXIT_Y);
	}

	public static int arenaSlotCount() {
		return ARENA_SLOTS.length;
	}

	public static ArenaSlot arenaSlot(int index) {
		if (index < 0 || index >= ARENA_SLOTS.length) {
			throw new IllegalArgumentException("Unknown Void Arena cage " + index);
		}
		return ARENA_SLOTS[index];
	}

	public static boolean isInsideLobby(Point point) {
		return point != null && point.inBounds(LOBBY_MIN_X, LOBBY_MIN_Y, LOBBY_MAX_X, LOBBY_MAX_Y);
	}

	public static boolean isInsideArena(Point point) {
		return slotFor(point) >= 0;
	}

	public static boolean isInsideVoidArena(Point point) {
		return isInsideLobby(point) || isInsideArena(point);
	}

	public static int slotFor(Point point) {
		if (point == null) {
			return -1;
		}
		for (ArenaSlot slot : ARENA_SLOTS) {
			if (slot.contains(point)) {
				return slot.index;
			}
		}
		return -1;
	}

	public static final class ArenaSlot {
		public final int index;
		public final int minX;
		public final int minY;
		public final int maxX;
		public final int maxY;
		public final int startAX;
		public final int startAY;
		public final int startBX;
		public final int startBY;

		private ArenaSlot(int index, int minX, int minY, int maxX, int maxY,
						  int startAX, int startAY, int startBX, int startBY) {
			this.index = index;
			this.minX = minX;
			this.minY = minY;
			this.maxX = maxX;
			this.maxY = maxY;
			this.startAX = startAX;
			this.startAY = startAY;
			this.startBX = startBX;
			this.startBY = startBY;
		}

		public boolean contains(Point point) {
			return point != null && point.inBounds(minX, minY, maxX, maxY);
		}

		public Point startA() {
			return Point.location(startAX, startAY);
		}

		public Point startB() {
			return Point.location(startBX, startBY);
		}
	}

	private VoidArenaConfig() {
	}
}
