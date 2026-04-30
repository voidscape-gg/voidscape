package com.openrsc.server.model;

import com.openrsc.server.external.DoorDef;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
import com.openrsc.server.plugins.Functions;

/**
 * Shared auto-walk pass-through rules for simple closed doors and gates.
 *
 * The long-distance pathfinder uses this to plan through known openable
 * obstacles; the WalkingQueue uses the same rules to open the obstacle at
 * runtime and retry the blocked step.
 */
public final class AutoOpenRouteObstacle {
	private static final int CLOSED_BANK_DOORS_ID = 64;
	private static final int OPEN_BANK_DOORS_ID = 63;

	private AutoOpenRouteObstacle() {
	}

	public static boolean canOpenBetween(final World world, final Point cur, final Point next) {
		return find(world, cur, next) != null;
	}

	public static boolean tryOpenBetween(final Player player, final Point cur, final Point next) {
		if (player == null) return false;
		final Candidate candidate = find(player.getWorld(), cur, next);
		if (candidate == null) return false;
		return candidate.open(player);
	}

	private static Candidate find(final World world, final Point cur, final Point next) {
		if (world == null || cur == null || next == null) return null;
		Candidate candidate = findBoundaryDoor(world, cur, next);
		if (candidate != null) return candidate;
		candidate = findSceneryNear(world, cur.getX(), cur.getY());
		if (candidate != null) return candidate;
		return findSceneryNear(world, next.getX(), next.getY());
	}

	private static Candidate findBoundaryDoor(final World world, final Point cur, final Point next) {
		final int dx = next.getX() - cur.getX();
		final int dy = next.getY() - cur.getY();
		GameObject door = null;
		if (dx == 0 && dy != 0) {
			final int wallY = Math.max(cur.getY(), next.getY());
			final Point wallLoc = Point.location(cur.getX(), wallY);
			final Region region = world.getRegionManager().getRegion(wallLoc);
			if (region != null) door = region.getWallGameObject(wallLoc, 0);
		} else if (dy == 0 && dx != 0) {
			final int wallX = Math.max(cur.getX(), next.getX());
			final Point wallLoc = Point.location(wallX, cur.getY());
			final Region region = world.getRegionManager().getRegion(wallLoc);
			if (region != null) door = region.getWallGameObject(wallLoc, 1);
		}
		if (isAutoOpenableBoundaryDoor(door)) {
			// Open doorframe id 11 still has doorType=1, so replacing a boundary
			// door with it leaves collision behind. Remove briefly and let the
			// walking queue retry the step through the cleared edge.
			return new Candidate(door, -1, true);
		}

		// Some RSC doors are diagonal boundary objects (dirs 2/3). They block
		// by setting FULL_BLOCK_A/B on their own tile, not by setting a wall
		// flag on the edge between two tiles, so look on the destination tile.
		door = findBoundaryDoorAt(world, next, 2);
		if (door == null) door = findBoundaryDoorAt(world, next, 3);
		if (isAutoOpenableBoundaryDoor(door)) {
			// Diagonal boundary doors set FULL_BLOCK_A/B on their own tile; do
			// not force-place the player onto that tile while it is still blocked.
			return new Candidate(door, -1, true);
		}
		return null;
	}

	private static GameObject findBoundaryDoorAt(final World world, final Point loc, final int direction) {
		if (world == null || loc == null) return null;
		final Region region = world.getRegionManager().getRegion(loc);
		return region == null ? null : region.getWallGameObject(loc, direction);
	}

	private static boolean isAutoOpenableBoundaryDoor(final GameObject object) {
		if (object == null || object.getGameObjectType() != GameObjectType.BOUNDARY) return false;
		final DoorDef def = object.getDoorDef();
		if (def == null || def.getDoorType() != 1) return false;
		return isRouteDoorCommand(def.getCommand1()) && isDoorOrGateName(def.getName());
	}

	private static Candidate findSceneryNear(final World world, final int cx, final int cy) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				final Point loc = Point.location(cx + dx, cy + dy);
				final Region region = world.getRegionManager().getRegion(loc);
				if (region == null) continue;
				final Candidate candidate = sceneryCandidate(region.getGameObject(loc));
				if (candidate != null) return candidate;
			}
		}
		return null;
	}

	private static Candidate sceneryCandidate(final GameObject object) {
		if (object == null || object.getGameObjectType() != GameObjectType.SCENERY) return null;
		final GameObjectDef def = object.getGameObjectDef();
		if (def == null || !isOpenCommand(def.getCommand1())) return null;
		if (def.getType() != 1 && def.getType() != 2) return null;

		if (object.getID() == CLOSED_BANK_DOORS_ID && !isLockedBankDoor(object)) {
			return new Candidate(object, OPEN_BANK_DOORS_ID, false);
		}

		if (!isDoorOrGateName(def.getName())) return null;
		final int replacementId = passableSceneryReplacement(object.getID());
		return new Candidate(object, replacementId, replacementId < 0);
	}

	private static int passableSceneryReplacement(final int closedId) {
		switch (closedId) {
			case 57: return 58;   // metal generic closed -> open, non-blocking
			case 60: return 59;   // wooden generic closed -> open, non-blocking
			case 371: return 372; // metal quest-style closed -> open, non-blocking
			default: return -1;   // unknown open variant: remove briefly, then respawn original
		}
	}

	private static boolean isLockedBankDoor(final GameObject object) {
		return (object.getX() == 467 && object.getY() == 518)
			|| (object.getX() == 558 && object.getY() == 587);
	}

	private static boolean isOpenCommand(final String command) {
		return "open".equalsIgnoreCase(clean(command));
	}

	private static boolean isRouteDoorCommand(final String command) {
		final String value = clean(command).toLowerCase();
		return "open".equals(value) || "walk through".equals(value) || "go through".equals(value);
	}

	private static boolean isDoorOrGateName(final String name) {
		final String value = clean(name).toLowerCase();
		return value.contains("door") || value.contains("gate");
	}

	private static String clean(final String value) {
		return value == null ? "" : value.trim();
	}

	private static final class Candidate {
		private final GameObject object;
		private final int replacementId;
		private final boolean removeOnly;

		private Candidate(final GameObject object, final int replacementId, final boolean removeOnly) {
			this.object = object;
			this.replacementId = replacementId;
			this.removeOnly = removeOnly;
		}

		private boolean open(final Player player) {
			if (object == null || object.isRemoved()) return false;
			if (!removeOnly && object.getID() == replacementId) return false;
			player.playSound("opendoor");
			if (removeOnly) {
				Functions.delloc(object);
			} else {
				final GameObject opened = new GameObject(object.getWorld(), object.getLocation(),
					replacementId, object.getDirection(), object.getType());
				Functions.changeloc(object, opened);
			}
			Functions.addloc(object.getWorld(), object.getLoc(), 3000);
			return true;
		}
	}
}
