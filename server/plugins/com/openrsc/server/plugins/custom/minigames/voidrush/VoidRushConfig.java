package com.openrsc.server.plugins.custom.minigames.voidrush;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.Point;

public final class VoidRushConfig {
	public static final int MIN_PLAYERS = 2;
	public static final int MAX_PLAYERS = 20;
	public static final int COUNTDOWN_TICKS = 3;
	public static final int WARNING_TICKS = 1;
	public static final int QUEUE_START_DELAY_TICKS = 3;
	public static final boolean ENFORCE_ONE_ACCOUNT_PER_IP = true;

	/*
	 * Placeholder arena coordinates. These are intentionally isolated from the
	 * live Void Enclave and should be moved when a dedicated Void Rush landscape
	 * patch is baked.
	 */
	public static final int ARENA_MIN_X = 488;
	public static final int ARENA_MIN_Y = 56;
	public static final int ARENA_MAX_X = 522;
	public static final int ARENA_MAX_Y = 86;

	public static final int LOBBY_X = 505;
	public static final int LOBBY_Y = 88;
	public static final int EXIT_X = 113;
	public static final int EXIT_Y = 318;
	public static final int SPECTATOR_X = 505;
	public static final int SPECTATOR_Y = 88;

	/*
	 * Christmas cracker already exists as item 575 in this codebase. The wave
	 * object uses the classic "shock" cosmic-energy object as a placeholder
	 * until a dedicated Void Rift/Void Surge object is added.
	 */
	public static final int CHRISTMAS_CRACKER_ID = ItemId.CHRISTMAS_CRACKER.id();
	public static final int VOID_WAVE_OBJECT_ID = 1022;
	public static final int ARENA_WALL_OBJECT_ID = 1010;
	public static final boolean VOID_WAVE_USE_TEMP_OBJECTS = false;
	public static final boolean VOID_WAVE_USE_CLIENT_PROJECTILE = true;

	public static final int STARTING_GAP_SIZE = 4;
	public static final int MIN_GAP_SIZE = 1;
	public static final int STARTING_WAVE_DELAY = 1;
	public static final int MIN_WAVE_DELAY = 1;
	public static final int STARTING_WAVE_STRIDE = 2;
	public static final int MAX_WAVE_STRIDE = 3;

	public static Point lobbyTile() {
		return Point.location(LOBBY_X, LOBBY_Y);
	}

	public static Point exitTile() {
		return Point.location(EXIT_X, EXIT_Y);
	}

	public static Point spectatorTile() {
		return Point.location(SPECTATOR_X, SPECTATOR_Y);
	}

	public static boolean isInsideArena(Point point) {
		return point != null && point.inBounds(ARENA_MIN_X, ARENA_MIN_Y, ARENA_MAX_X, ARENA_MAX_Y);
	}

	public static boolean isInsideArenaInterior(Point point) {
		return point != null && point.inBounds(ARENA_MIN_X + 1, ARENA_MIN_Y + 1, ARENA_MAX_X - 1, ARENA_MAX_Y - 1);
	}

	public static boolean isInsideSpectator(Point point) {
		return point != null && point.inBounds(SPECTATOR_X - 2, SPECTATOR_Y - 2, SPECTATOR_X + 2, SPECTATOR_Y + 2);
	}

	private VoidRushConfig() {
	}
}
