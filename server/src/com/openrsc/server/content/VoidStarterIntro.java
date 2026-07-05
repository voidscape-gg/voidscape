package com.openrsc.server.content;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.List;

import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidStarterIntro {
	public static final String SEEN_CACHE_KEY = "void_intro_seen";
	private static final String RUNNING_ATTRIBUTE = "void_intro_running";
	public static final int INTRO_X = 24;
	public static final int INTRO_Y = 37;
	private static final int INTRO_MIN_X = 17;
	private static final int INTRO_MAX_X = 31;
	private static final int INTRO_MIN_Y = 31;
	private static final int INTRO_MAX_Y = 42;
	private static final int GATE_NORTH_Y = 32;

	private static final CouncilLine[] LINES = {
		new CouncilLine(NpcId.VOID_COUNCIL_ONE.id(), 24, 35, "The void came without warning."),
		new CouncilLine(NpcId.VOID_COUNCIL_ONE.id(), 24, 35, "It consumed the world and everyone in it."),
		new CouncilLine(NpcId.VOID_COUNCIL_TWO.id(), 22, 37, "Almost everyone."),
		new CouncilLine(NpcId.VOID_COUNCIL_THREE.id(), 26, 37, "Some of us kept our sanity. But you..."),
		new CouncilLine(NpcId.VOID_COUNCIL_ONE.id(), 24, 35, "You survived. As did others."),
		new CouncilLine(NpcId.VOID_COUNCIL_TWO.id(), 22, 37, "Find them. Get strong. Take our world back.")
	};

	private VoidStarterIntro() {
	}

	public static boolean hasSeen(Player player) {
		return player != null && player.getCache().hasKey(SEEN_CACHE_KEY);
	}

	public static boolean needsIntro(Player player) {
		return player != null && !hasSeen(player) && !VoidPath.hasChosen(player);
	}

	public static boolean inIntroArea(Player player) {
		return player != null && inIntroArea(player.getLocation());
	}

	public static boolean inIntroArea(Point point) {
		return point != null && point.inBounds(INTRO_MIN_X, INTRO_MIN_Y, INTRO_MAX_X, INTRO_MAX_Y);
	}

	public static Point entryPoint(Player player) {
		if (needsIntro(player)) {
			return Point.location(INTRO_X, INTRO_Y);
		}
		return Point.location(VoidPath.VOID_ISLAND_X, VoidPath.VOID_ISLAND_Y);
	}

	public static boolean isCouncil(Npc npc) {
		if (npc == null) {
			return false;
		}
		int id = npc.getID();
		return id == NpcId.VOID_COUNCIL_ONE.id()
			|| id == NpcId.VOID_COUNCIL_TWO.id()
			|| id == NpcId.VOID_COUNCIL_THREE.id();
	}

	public static void prompt(Player player) {
		if (needsIntro(player) && inIntroArea(player)) {
			player.message("@mag@The Void Council blocks the path north. Speak to one of them.");
		}
	}

	public static void startDialogue(Player player, Npc clickedNpc) {
		if (!isCouncil(clickedNpc)) {
			return;
		}

		if (!needsIntro(player)) {
			npcsay(player, clickedNpc, "The path north is open.");
			return;
		}

		playLore(player);
	}

	/**
	 * Plays the six council lore lines and marks the intro seen. Safe to call
	 * without a clicked NPC (welcome-menu tracks); missing council NPCs fall
	 * back to plain messages.
	 */
	public static void playLore(Player player) {
		if (!needsIntro(player) || player.getAttribute(RUNNING_ATTRIBUTE, false)) {
			return;
		}

		player.setAttribute(RUNNING_ATTRIBUTE, true);
		player.resetPath();
		try {
			for (CouncilLine line : LINES) {
				Npc npc = findCouncil(player, line);
				if (npc != null) {
					npcsay(player, npc, line.text);
				} else {
					player.message("@yel@" + line.text);
				}
			}
				player.message("@mag@The council lowers their scythes. The path north lies open.");
				player.getCache().store(SEEN_CACHE_KEY, true);
				player.save(false, true);
			} finally {
				player.setAttribute(RUNNING_ATTRIBUTE, false);
			}
	}

	public static boolean blocksUnseenIntroPath(Player player, Point firstStep, List<Point> relativeSteps) {
		if (!needsIntro(player) || !inIntroArea(player) || firstStep == null) {
			return false;
		}

		if (isPastCouncilGate(firstStep)) {
			return true;
		}

		for (Point step : relativeSteps) {
			if (step != null && isPastCouncilGate(Point.location(firstStep.getX() + step.getX(), firstStep.getY() + step.getY()))) {
				return true;
			}
		}
		return false;
	}

	public static boolean blocksUnseenIntroPath(Player player, List<Point> path) {
		if (!needsIntro(player) || !inIntroArea(player) || path == null) {
			return false;
		}

		for (Point step : path) {
			if (isPastCouncilGate(step)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isPastCouncilGate(Point point) {
		return point != null
			&& point.getX() >= INTRO_MIN_X
			&& point.getX() <= INTRO_MAX_X
			&& point.getY() <= GATE_NORTH_Y;
	}

	private static Npc findCouncil(Player player, CouncilLine line) {
		return player.getWorld().getNpc(line.npcId, line.x, line.x, line.y, line.y);
	}

	private static final class CouncilLine {
		private final int npcId;
		private final int x;
		private final int y;
		private final String text;

		private CouncilLine(int npcId, int x, int y, String text) {
			this.npcId = npcId;
			this.x = x;
			this.y = y;
			this.text = text;
		}
	}
}
