package com.openrsc.server.content.voidarena;

import com.openrsc.server.model.Point;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class VoidArenaConfig {
	public static final String LEGACY_SEASON = "LEGACY";
	public static final int STARTING_RATING = 1200;
	public static final int ELO_K_FACTOR = 32;
	public static final int ELO_DIVISOR = 400;
	public static final int CHALLENGE_TIMEOUT_MS = 60_000;
	public static final int SETUP_TIMEOUT_MS = 120_000;
	public static final int RATING_VISIBLE_MATCHES = 5;
	public static final int RATING_DISPLAY_CLIENT_VERSION = 10106;
	public static final long RATED_PAIR_COOLDOWN_MS = 30L * 60L * 1000L;
	public static final int MAX_RATED_PAIR_RESULTS_PER_UTC_DAY = 3;
	private static final DateTimeFormatter SEASON_FORMATTER =
		DateTimeFormatter.ofPattern("uuuu-MM").withZone(ZoneOffset.UTC);

	public static final int LOBBY_X = 600;
	public static final int LOBBY_Y = 2914;
	public static final int LOBBY_MIN_X = 582;
	public static final int LOBBY_MIN_Y = 2910;
	public static final int LOBBY_MAX_X = 616;
	public static final int LOBBY_MAX_Y = 2916;
	public static final int EXIT_X = 113;
	public static final int EXIT_Y = 318;

	private static final ArenaSlot[] ARENA_SLOTS = {
		new ArenaSlot(0, 584, 2897, 590, 2909, 585, 2904, 589, 2904),
		new ArenaSlot(1, 592, 2897, 598, 2909, 593, 2904, 597, 2904),
		new ArenaSlot(2, 600, 2897, 606, 2909, 601, 2904, 605, 2904),
		new ArenaSlot(3, 608, 2897, 614, 2909, 609, 2904, 613, 2904),
	};

	public static Point lobbyTile() {
		return Point.location(LOBBY_X, LOBBY_Y);
	}

	public static String currentSeasonId() {
		return seasonIdAt(System.currentTimeMillis());
	}

	public static String seasonIdAt(long epochMillis) {
		return SEASON_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
	}

	public static String previousSeasonId() {
		return YearMonth.now(ZoneOffset.UTC).minusMonths(1).toString();
	}

	public static long currentSeasonStartMs() {
		return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC)
			.toInstant().toEpochMilli();
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
