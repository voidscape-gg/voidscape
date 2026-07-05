package com.openrsc.server.content;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;

import java.util.List;

/**
 * The Tutorial Isle: a dedicated, linearly gated area for the guided
 * onboarding track, carved into the ocean west of Void Island. Three chambers
 * south-to-north — the Landing (camp), the Ring (spar), the Scar (ambush) —
 * joined at the guide lane (x=40). Fence rows are baked into the landscape;
 * the lane gaps are script-gated here by void_guided_stage, mirroring the
 * council-gate pattern (VoidStarterIntro.blocksUnseenIntroPath).
 *
 * Lives in core because the walk handlers can't reference plugin classes.
 */
public final class VoidTutorialIsle {
	public static final String STAGE_CACHE_KEY = "void_guided_stage";

	public static final int STAGE_NONE = 0;
	public static final int STAGE_CAMP = 1;
	public static final int STAGE_SPAR = 2;
	public static final int STAGE_SCOUT = 3;
	public static final int STAGE_DONE = 4;

	public static final int LANDING_X = 40;
	public static final int LANDING_Y = 37;
	public static final int FIGHT_RESPAWN_X = 40;
	public static final int FIGHT_RESPAWN_Y = 29;
	public static final int RING_X = 40;
	public static final int RING_Y = 27;

	// Gate rows (fence boundaries between chambers); a gate at row Y blocks
	// stepping to any tile with y < Y until the stage opens it.
	private static final int GATE1_Y = 31; // Landing -> Ring, opens at STAGE_SPAR
	private static final int GATE2_Y = 23; // Ring -> Scar, opens at STAGE_SCOUT
	private static final int SCAR_MIN_Y = 18;

	private VoidTutorialIsle() {
	}

	public static int getStage(Player player) {
		if (player == null || !player.getCache().hasKey(STAGE_CACHE_KEY)) {
			return STAGE_NONE;
		}
		return player.getCache().getInt(STAGE_CACHE_KEY);
	}

	/**
	 * Where a mid-tour guided player belongs on login/teleport-routing, or
	 * null when the isle isn't their place (not guided, done, or pre-lore).
	 */
	public static Point resumePoint(Player player) {
		if (player == null
			|| VoidPath.hasChosen(player)
			|| VoidOnboarding.getTrack(player) != VoidOnboarding.TRACK_GUIDED) {
			return null;
		}
		switch (getStage(player)) {
			case STAGE_CAMP:
				return Point.location(LANDING_X, LANDING_Y);
			case STAGE_SPAR:
				return Point.location(FIGHT_RESPAWN_X, FIGHT_RESPAWN_Y);
			case STAGE_SCOUT:
				return Point.location(RING_X, RING_Y);
			default:
				// NONE: lore unfinished, stay in the council flow. DONE: island.
				return null;
		}
	}

	public static boolean blocksGatedTutorialPath(Player player, Point firstStep, List<Point> relativeSteps) {
		if (!gatesApply(player) || firstStep == null) {
			return false;
		}
		if (isPastGate(player, firstStep)) {
			return true;
		}
		for (Point step : relativeSteps) {
			if (step != null && isPastGate(player,
				Point.location(firstStep.getX() + step.getX(), firstStep.getY() + step.getY()))) {
				return true;
			}
		}
		return false;
	}

	public static boolean blocksGatedTutorialPath(Player player, List<Point> path) {
		if (!gatesApply(player) || path == null) {
			return false;
		}
		for (Point step : path) {
			if (isPastGate(player, step)) {
				return true;
			}
		}
		return false;
	}

	private static boolean gatesApply(Player player) {
		return player != null
			&& !VoidPath.hasChosen(player)
			&& VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_GUIDED
			&& player.getLocation().inVoidTutorialIsle();
	}

	private static boolean isPastGate(Player player, Point point) {
		if (point == null || !Point.inVoidTutorialIsle(point.getX(), point.getY())) {
			return false;
		}
		int stage = getStage(player);
		int minAllowedY = stage >= STAGE_SCOUT ? SCAR_MIN_Y : stage >= STAGE_SPAR ? GATE2_Y : GATE1_Y;
		return point.getY() < minAllowedY;
	}
}
