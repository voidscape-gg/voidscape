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
		if (cur == null || next == null) return false;
		return canOpenBetween(world, cur.getX(), cur.getY(), next.getX(), next.getY());
	}

	public static boolean canOpenBetween(final World world, final int curX, final int curY, final int nextX, final int nextY) {
		return find(world, curX, curY, nextX, nextY) != null;
	}

	public static boolean tryOpenBetween(final Player player, final Point cur, final Point next) {
		if (player == null || cur == null || next == null) return false;
		final Candidate candidate = find(player.getWorld(), cur.getX(), cur.getY(), next.getX(), next.getY());
		if (candidate == null) return false;
		return candidate.open(player);
	}

	private static Candidate find(final World world, final int curX, final int curY, final int nextX, final int nextY) {
		if (world == null) return null;
		Candidate candidate = findBoundaryDoor(world, curX, curY, nextX, nextY);
		if (candidate != null) return candidate;
		return findSceneryBlockingStep(world, curX, curY, nextX, nextY);
	}

	private static Candidate findBoundaryDoor(final World world, final int curX, final int curY, final int nextX, final int nextY) {
		final int dx = nextX - curX;
		final int dy = nextY - curY;
		GameObject door = null;
		if (dx == 0 && dy != 0) {
			final int wallY = Math.max(curY, nextY);
			final Point wallLoc = Point.location(curX, wallY);
			final Region region = world.getRegionManager().getRegion(wallLoc);
			if (region != null) door = region.getWallGameObject(wallLoc, 0);
		} else if (dy == 0 && dx != 0) {
			final int wallX = Math.max(curX, nextX);
			final Point wallLoc = Point.location(wallX, curY);
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
		final Point next = Point.location(nextX, nextY);
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

	private static Candidate findSceneryBlockingStep(final World world, final int curX, final int curY,
	                                                 final int nextX, final int nextY) {
		if (Math.abs(nextX - curX) > 1 || Math.abs(nextY - curY) > 1) return null;
		final int minX = Math.min(curX, nextX) - 2;
		final int maxX = Math.max(curX, nextX) + 2;
		final int minY = Math.min(curY, nextY) - 2;
		final int maxY = Math.max(curY, nextY) + 2;
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				if (x < 0 || y < 0) continue;
				final Point loc = Point.location(x, y);
				final Region region = world.getRegionManager().getRegion(loc);
				if (region == null) continue;
				final GameObject object = region.getGameObject(loc);
				if (!sceneryBlocksStep(object, curX, curY, nextX, nextY)) continue;
				final Candidate candidate = sceneryCandidate(object);
				if (candidate != null) return candidate;
			}
		}
		return null;
	}

	private static boolean sceneryBlocksStep(final GameObject object, final int curX, final int curY,
	                                         final int nextX, final int nextY) {
		if (object == null || object.getGameObjectType() != GameObjectType.SCENERY) return false;
		final GameObjectDef def = object.getGameObjectDef();
		if (def == null) return false;
		final int dir = object.getDirection();
		final int width;
		final int height;
		if (dir == 0 || dir == 4) {
			width = def.getWidth();
			height = def.getHeight();
		} else {
			height = def.getWidth();
			width = def.getHeight();
		}

		if (def.getType() == 1) {
			return pointInFootprint(object.getX(), object.getY(), width, height, curX, curY)
				|| pointInFootprint(object.getX(), object.getY(), width, height, nextX, nextY);
		}

		if (def.getType() != 2) return false;
		for (int x = object.getX(); x < object.getX() + width; x++) {
			for (int y = object.getY(); y < object.getY() + height; y++) {
				if (dir == 0 && sameStep(curX, curY, nextX, nextY, x, y, x - 1, y)) return true;
				if (dir == 2 && sameStep(curX, curY, nextX, nextY, x, y, x, y + 1)) return true;
				if (dir == 4 && sameStep(curX, curY, nextX, nextY, x, y, x + 1, y)) return true;
				if (dir == 6 && sameStep(curX, curY, nextX, nextY, x, y, x, y - 1)) return true;
			}
		}
		return false;
	}

	private static boolean pointInFootprint(final int objectX, final int objectY, final int width, final int height,
	                                        final int x, final int y) {
		return x >= objectX && x < objectX + width && y >= objectY && y < objectY + height;
	}

	private static boolean sameStep(final int curX, final int curY, final int nextX, final int nextY,
	                                final int ax, final int ay, final int bx, final int by) {
		return (curX == ax && curY == ay && nextX == bx && nextY == by)
			|| (curX == bx && curY == by && nextX == ax && nextY == ay);
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
