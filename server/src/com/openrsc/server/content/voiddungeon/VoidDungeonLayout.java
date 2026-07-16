package com.openrsc.server.content.voiddungeon;

/**
 * Generated authoritative coordinate policy for the shared Void Dungeon.
 * Regenerate with scripts/gen-void-dungeon.py; do not edit by hand.
 */
public final class VoidDungeonLayout {
	public static final int STAGE_NONE = 0;
	public static final int STAGE_RIFTWORKS = 1;
	public static final int STAGE_BROKEN_MENAGERIE = 2;
	public static final int STAGE_NULL_SANCTUM = 3;

	public static final int VOID_RIFT_ID = 1306;
	public static final int LADDER_UP_ID = 5;
	public static final int LADDER_DOWN_ID = 6;

	public static final int SURFACE_RIFT_X = 112;
	public static final int SURFACE_RIFT_Y = 296;
	public static final int SURFACE_RETURN_X = 111;
	public static final int SURFACE_RETURN_Y = 297;

	public static final int RIFTWORKS_FLOOR = 3;
	public static final int RIFTWORKS_ARRIVAL_X = 72;
	public static final int RIFTWORKS_ARRIVAL_Y = 3252;
	public static final int RIFTWORKS_EXIT_RIFT_X = 68;
	public static final int RIFTWORKS_EXIT_RIFT_Y = 3252;
	public static final int RIFTWORKS_TRANSITION_X = 72;
	public static final int RIFTWORKS_TRANSITION_Y = 3197;

	public static final int BROKEN_MENAGERIE_FLOOR = 2;
	public static final int BROKEN_MENAGERIE_ARRIVAL_X = 72;
	public static final int BROKEN_MENAGERIE_ARRIVAL_Y = 2308;
	public static final int BROKEN_MENAGERIE_EXIT_RIFT_X = 76;
	public static final int BROKEN_MENAGERIE_EXIT_RIFT_Y = 2308;
	public static final int BROKEN_MENAGERIE_TRANSITION_X = 72;
	public static final int BROKEN_MENAGERIE_TRANSITION_Y = 2253;

	public static final int NULL_SANCTUM_FLOOR = 1;
	public static final int NULL_SANCTUM_ARRIVAL_X = 72;
	public static final int NULL_SANCTUM_ARRIVAL_Y = 1364;
	public static final int NULL_SANCTUM_EXIT_RIFT_X = 76;
	public static final int NULL_SANCTUM_EXIT_RIFT_Y = 1364;

	private static final int[][] RIFTWORKS_RECTS = {
		{66, 3246, 78, 3256}, // room: riftworks_landing
		{56, 3229, 88, 3242}, // room: riftworks_spider_works
		{56, 3211, 88, 3224}, // room: riftworks_wolf_foundry
		{64, 3193, 80, 3206}, // room: riftworks_transition
		{69, 3240, 75, 3248}, // corridor: riftworks_landing_spine
		{69, 3222, 75, 3231}, // corridor: riftworks_middle_spine
		{69, 3204, 75, 3213}, // corridor: riftworks_upper_spine
	};

	private static final int[][] BROKEN_MENAGERIE_RECTS = {
		{66, 2302, 78, 2312}, // room: menagerie_landing
		{54, 2287, 66, 2298}, // room: menagerie_unicorn_west
		{78, 2287, 90, 2298}, // room: menagerie_unicorn_east
		{54, 2271, 66, 2283}, // room: menagerie_ogre_west
		{78, 2271, 90, 2283}, // room: menagerie_ogre_east
		{54, 2256, 66, 2268}, // room: menagerie_giant_west
		{78, 2256, 90, 2268}, // room: menagerie_giant_east
		{66, 2249, 78, 2255}, // room: menagerie_transition
		{70, 2253, 74, 2304}, // corridor: menagerie_spine
		{64, 2291, 80, 2294}, // corridor: menagerie_unicorn_bridge
		{64, 2275, 80, 2278}, // corridor: menagerie_ogre_bridge
		{64, 2260, 80, 2263}, // corridor: menagerie_giant_bridge
	};

	private static final int[][] NULL_SANCTUM_RECTS = {
		{66, 1358, 78, 1368}, // room: sanctum_landing
		{66, 1330, 78, 1360}, // room: sanctum_processional_nave
		{54, 1336, 66, 1350}, // room: sanctum_knight_chapel
		{78, 1336, 90, 1350}, // room: sanctum_wizard_chapel
		{60, 1322, 84, 1334}, // room: sanctum_crossing
		{58, 1305, 86, 1319}, // room: sanctum_demon_seal
		{64, 1340, 70, 1344}, // corridor: sanctum_west_transept
		{74, 1340, 80, 1344}, // corridor: sanctum_east_transept
		{69, 1317, 75, 1332}, // corridor: sanctum_seal_passage
	};

	public static final Transition RIFTWORKS_TO_BROKEN_MENAGERIE = new Transition(5, STAGE_RIFTWORKS, 72, 3197, STAGE_BROKEN_MENAGERIE, 72, 2308);
	public static final Transition BROKEN_MENAGERIE_TO_RIFTWORKS = new Transition(6, STAGE_BROKEN_MENAGERIE, 72, 2308, STAGE_RIFTWORKS, 72, 3197);
	public static final Transition BROKEN_MENAGERIE_TO_NULL_SANCTUM = new Transition(5, STAGE_BROKEN_MENAGERIE, 72, 2253, STAGE_NULL_SANCTUM, 72, 1364);
	public static final Transition NULL_SANCTUM_TO_BROKEN_MENAGERIE = new Transition(6, STAGE_NULL_SANCTUM, 72, 1364, STAGE_BROKEN_MENAGERIE, 72, 2253);

	private static final Transition[] ALL_TRANSITIONS = {
		RIFTWORKS_TO_BROKEN_MENAGERIE,
		BROKEN_MENAGERIE_TO_RIFTWORKS,
		BROKEN_MENAGERIE_TO_NULL_SANCTUM,
		NULL_SANCTUM_TO_BROKEN_MENAGERIE,
	};

	private VoidDungeonLayout() {
	}

	public static boolean contains(int x, int y) {
		return stageAt(x, y) != STAGE_NONE;
	}

	public static int stageAt(int x, int y) {
		if (inRectangles(x, y, RIFTWORKS_RECTS)) {
			return STAGE_RIFTWORKS;
		}
		if (inRectangles(x, y, BROKEN_MENAGERIE_RECTS)) {
			return STAGE_BROKEN_MENAGERIE;
		}
		if (inRectangles(x, y, NULL_SANCTUM_RECTS)) {
			return STAGE_NULL_SANCTUM;
		}
		return STAGE_NONE;
	}

	public static String stageName(int stage) {
		switch (stage) {
			case STAGE_RIFTWORKS:
				return "Riftworks";
			case STAGE_BROKEN_MENAGERIE:
				return "Broken Menagerie";
			case STAGE_NULL_SANCTUM:
				return "Null Sanctum";
			default:
				return null;
		}
	}

	public static boolean isExitRift(int x, int y) {
		return (x == RIFTWORKS_EXIT_RIFT_X && y == RIFTWORKS_EXIT_RIFT_Y)
			|| (x == BROKEN_MENAGERIE_EXIT_RIFT_X && y == BROKEN_MENAGERIE_EXIT_RIFT_Y)
			|| (x == NULL_SANCTUM_EXIT_RIFT_X && y == NULL_SANCTUM_EXIT_RIFT_Y);
	}

	public static Transition transitionAt(int objectId, int x, int y) {
		for (Transition transition : ALL_TRANSITIONS) {
			if (transition.matches(objectId, x, y)) {
				return transition;
			}
		}
		return null;
	}

	public static Transition[] getTransitions() {
		return ALL_TRANSITIONS.clone();
	}

	private static boolean inRectangles(int x, int y, int[][] rectangles) {
		for (int[] rectangle : rectangles) {
			if (x >= rectangle[0] && x <= rectangle[2]
				&& y >= rectangle[1] && y <= rectangle[3]) {
				return true;
			}
		}
		return false;
	}

	public static final class Transition {
		private final int objectId;
		private final int sourceStage;
		private final int sourceX;
		private final int sourceY;
		private final int targetStage;
		private final int targetX;
		private final int targetY;

		private Transition(int objectId, int sourceStage, int sourceX, int sourceY,
				int targetStage, int targetX, int targetY) {
			this.objectId = objectId;
			this.sourceStage = sourceStage;
			this.sourceX = sourceX;
			this.sourceY = sourceY;
			this.targetStage = targetStage;
			this.targetX = targetX;
			this.targetY = targetY;
		}

		public int getObjectId() { return objectId; }
		public int getSourceStage() { return sourceStage; }
		public int getSourceX() { return sourceX; }
		public int getSourceY() { return sourceY; }
		public int getTargetStage() { return targetStage; }
		public int getTargetX() { return targetX; }
		public int getTargetY() { return targetY; }

		public boolean matches(int candidateObjectId, int x, int y) {
			return objectId == candidateObjectId && sourceX == x && sourceY == y;
		}
	}
}
