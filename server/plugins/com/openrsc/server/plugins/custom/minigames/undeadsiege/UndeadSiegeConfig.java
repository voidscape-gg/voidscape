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
	public static final int POINT_SHOP_PRICE_MODIFIER = 255;
	public static final int NPC_STUCK_TICKS_BEFORE_REPOSITION = 4;
	public static final int NPC_REPOSITION_MIN_RADIUS = 2;
	public static final int NPC_REPOSITION_MAX_RADIUS = 6;

	public static final int ARENA_MIN_X = 579;
	public static final int ARENA_MIN_Y = 99;
	public static final int ARENA_MAX_X = 621;
	public static final int ARENA_MAX_Y = 141;
	public static final int CHASE_MIN_X = 579;
	public static final int CHASE_MIN_Y = 99;
	public static final int CHASE_MAX_X = 621;
	public static final int CHASE_MAX_Y = 141;

	public static final int EXIT_X = 113;
	public static final int EXIT_Y = 318;

	public static final int START_X = 600;
	public static final int START_Y = 122;

	public static final int START_BOLT_COUNT = 180;
	public static final int START_AIR_RUNES = 10;
	public static final int START_EARTH_RUNES = 10;
	public static final int START_CHAOS_RUNES = 5;
	public static final int START_SWORDFISH = 8;
	public static final int TEMPORARY_COMBAT_LEVEL = 40;
	public static final int TEMPORARY_MAGIC_LEVEL = 70;

	public static final int SUPPLY_TUNA_COST = 45;
	public static final int SUPPLY_CAKE_COST = 60;
	public static final int SUPPLY_SWORDFISH_COST = 100;
	public static final int SUPPLY_BOLTS_COST = 75;
	public static final int SUPPLY_CRUMBLE_RUNES_COST = 160;
	public static final int SUPPLY_STAFF_COST = 120;

	public static final int REWARD_COINS_PER_WAVE = 250;
	public static final int REWARD_COINS_CAP = 10000;

	static final int[] START_ITEM_IDS = {
		ItemId.CROSSBOW.id(),
		ItemId.CROSSBOW_BOLTS.id(),
		ItemId.STAFF_OF_AIR.id(),
		ItemId.AIR_RUNE.id(),
		ItemId.EARTH_RUNE.id(),
		ItemId.CHAOS_RUNE.id(),
		ItemId.TUNA.id(),
		ItemId.CAKE.id(),
		ItemId.SWORDFISH.id()
	};

	static final Point[] START_TILES = {
		Point.location(600, 121),
		Point.location(599, 121),
		Point.location(601, 121),
		Point.location(600, 123)
	};

	static final Point[] SPAWN_TILES = {
		Point.location(584, 106),
		Point.location(590, 103),
		Point.location(600, 102),
		Point.location(610, 103),
		Point.location(616, 107),
		Point.location(618, 118),
		Point.location(616, 132),
		Point.location(609, 137),
		Point.location(600, 138),
		Point.location(591, 137),
		Point.location(583, 132),
		Point.location(582, 120),
		Point.location(590, 110),
		Point.location(610, 129)
	};

	static final Point[] APPROACH_TILES = {
		Point.location(590, 108),
		Point.location(596, 108),
		Point.location(600, 108),
		Point.location(604, 108),
		Point.location(610, 108),
		Point.location(590, 114),
		Point.location(596, 114),
		Point.location(600, 114),
		Point.location(604, 114),
		Point.location(610, 114),
		Point.location(588, 120),
		Point.location(594, 120),
		Point.location(600, 120),
		Point.location(606, 120),
		Point.location(612, 120),
		Point.location(590, 126),
		Point.location(596, 126),
		Point.location(600, 126),
		Point.location(604, 126),
		Point.location(610, 126),
		Point.location(590, 132),
		Point.location(596, 132),
		Point.location(600, 132),
		Point.location(604, 132),
		Point.location(610, 132)
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
