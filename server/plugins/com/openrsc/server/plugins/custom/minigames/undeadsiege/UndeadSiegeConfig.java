package com.openrsc.server.plugins.custom.minigames.undeadsiege;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.Point;

public final class UndeadSiegeConfig {
	public static final String NAME = "Undead Siege";

	public static final int MAX_PLAYERS = 4;
	public static final int PARTY_JOIN_RADIUS = 8;
	public static final int COUNTDOWN_TICKS = 3;
	public static final int INTERMISSION_SECONDS = 30;
	public static final int INTERMISSION_TICKS = 47;
	public static final int MAX_LIVE_NPCS = 18;
	public static final int INSTANCE_ID_START = 20000;
	public static final int POINT_SHOP_PRICE_MODIFIER = 255;
	public static final int NPC_STUCK_TICKS_BEFORE_REPOSITION = 4;
	public static final int NPC_REPOSITION_MIN_RADIUS = 2;
	public static final int NPC_REPOSITION_MAX_RADIUS = 6;

	public static final int ARENA_MIN_X = 158;
	public static final int ARENA_MIN_Y = 247;
	public static final int ARENA_MAX_X = 180;
	public static final int ARENA_MAX_Y = 264;
	public static final int CHASE_MIN_X = 156;
	public static final int CHASE_MIN_Y = 245;
	public static final int CHASE_MAX_X = 181;
	public static final int CHASE_MAX_Y = 268;

	public static final int EXIT_X = 113;
	public static final int EXIT_Y = 318;

	public static final int START_X = 169;
	public static final int START_Y = 257;

	public static final int START_ARROW_COUNT = 200;
	public static final int START_AIR_RUNES = 200;
	public static final int START_FIRE_RUNES = 250;
	public static final int START_DEATH_RUNES = 50;
	public static final int START_SWORDFISH = 8;
	public static final int TEMPORARY_COMBAT_LEVEL = 40;

	public static final int SUPPLY_SWORDFISH_COST = 75;
	public static final int SUPPLY_STRENGTH_COST = 150;
	public static final int SUPPLY_RANGED_COST = 200;
	public static final int SUPPLY_RUNES_COST = 250;

	public static final int REWARD_COINS_PER_WAVE = 250;
	public static final int REWARD_COINS_CAP = 10000;

	static final int[] START_ITEM_IDS = {
		ItemId.RUNE_2_HANDED_SWORD.id(),
		ItemId.SHORTBOW.id(),
		ItemId.IRON_ARROWS.id(),
		ItemId.FULL_STRENGTH_POTION.id(),
		ItemId.AIR_RUNE.id(),
		ItemId.FIRE_RUNE.id(),
		ItemId.DEATH_RUNE.id(),
		ItemId.SWORDFISH.id()
	};

	static final Point[] START_TILES = {
		Point.location(169, 257),
		Point.location(168, 257),
		Point.location(170, 257),
		Point.location(169, 258)
	};

	static final Point[] SPAWN_TILES = {
		Point.location(160, 248),
		Point.location(166, 247),
		Point.location(173, 247),
		Point.location(178, 248),
		Point.location(158, 252),
		Point.location(158, 257),
		Point.location(180, 256),
		Point.location(180, 260),
		Point.location(159, 263),
		Point.location(163, 264),
		Point.location(179, 262),
		Point.location(176, 248)
	};

	static final Point[] APPROACH_TILES = {
		Point.location(164, 252),
		Point.location(166, 252),
		Point.location(169, 253),
		Point.location(172, 253),
		Point.location(175, 252),
		Point.location(162, 256),
		Point.location(166, 256),
		Point.location(169, 257),
		Point.location(172, 257),
		Point.location(176, 257),
		Point.location(160, 260),
		Point.location(164, 261),
		Point.location(168, 260),
		Point.location(172, 260),
		Point.location(176, 260),
		Point.location(179, 260),
		Point.location(163, 263),
		Point.location(170, 263),
		Point.location(176, 263)
	};

	public static Point exitTile() {
		return Point.location(EXIT_X, EXIT_Y);
	}

	public static boolean isInsideArena(Point point) {
		return point != null && point.inBounds(ARENA_MIN_X, ARENA_MIN_Y, ARENA_MAX_X, ARENA_MAX_Y);
	}

	private UndeadSiegeConfig() {
	}
}
