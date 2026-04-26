package com.openrsc.server.model;

import com.openrsc.server.external.DoorDef;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
import com.openrsc.server.plugins.Functions;

/**
 * A WalkingQueue stores steps the client needs to walk and allows
 * this queue of steps to be modified.
 * The class will also process these steps when processNextMovement()
 * is called. This should be called once per server cycle.
 */
public class WalkingQueue {
	private boolean DEBUG = false;
	private Mob mob;

	public Path path;
	public boolean playerWasWalking;

	public WalkingQueue(Mob entity) {
		this.mob = entity;
	}

   /**
    * Handles logic to run when the player finishes walking. A player has finished walking if they 
	* were walking and then their path becomes null or empty.
    */ 
	private void handlePlayerFinishedWalking() {
		if (playerWasWalking) {
			Player currentPlayer = mob.isPlayer() ? (Player)mob : null;

			// Only track finished walking status of players.
			if (currentPlayer != null && !currentPlayer.isBusy()) {
				Point targetTile = currentPlayer.getLastTileClicked();

				if (targetTile != null) {
					Region region = currentPlayer.getWorld().getRegionManager().getRegion(targetTile);

					// Target would be the other player currentPlayer clicked on.
					Player target = region.getPlayer(targetTile.getX(), targetTile.getY(), currentPlayer, false);

					if (target != null && target != currentPlayer) {
						// Is current player within 1 tile of target?
						boolean targetWithinOneTile = currentPlayer.withinRange(targetTile, 1);

						// Face the other player. This will have no effect if player_blocking config is disabled.
						if (targetWithinOneTile) {
							currentPlayer.face(target);
						}
					}
					
					// Reset lastTileClicked so the player doesn't re-face the last player they clicked on.
					currentPlayer.setLastTileClicked(null);
				}
			}
		}

		playerWasWalking = false;
	}

	/**
	 * Processes the next player's movement.
	 */
	public void processNextMovement() {
		if (path == null) {
			handlePlayerFinishedWalking();
			return;
		} else if (path.isEmpty()) {
			handlePlayerFinishedWalking();
			reset();
			return;
		}

		// Player is walking if path is not null or empty.
		playerWasWalking = true;

		Point walkPoint = path.poll();

		if (mob.getAttribute("blink", false)) {
			if (path.size() >= 1) {
				walkPoint = path.getLastPoint();
				((Player) mob).teleport(walkPoint.getX(), walkPoint.getY(), false);
			}
			return;
		}

		int destX = walkPoint.getX();
		int destY = walkPoint.getY();
		int startX = mob.getX();
		int startY = mob.getY();
		if (!PathValidation.checkAdjacent(mob, new Point(startX, startY), new Point(destX, destY))) {
			// voidscape: RSC1 ships every basic interior door closed-by-default
			// and requires a manual click to open. That's authentic but
			// annoying for navigation — auto-open the trivial "Door" type
			// when a player walks into one. Gates, locked doors, magic doors,
			// jail doors, and quest-restricted doors stay manual.
			if (mob.isPlayer() && tryAutoOpenBasicDoor(new Point(startX, startY), new Point(destX, destY))) {
				path.addDirect(destX, destY);  // re-queue the step we polled
				return;
			}
			reset();
			if (DEBUG && mob.isPlayer()) System.out.println("Failed adjacent check, not pathing.");
			return;
		}

		if (mob.isNpc()) {
			NPCLoc loc = ((Npc) mob).getLoc();
			if (Point.location(destX, destY).inBounds(loc.minX() - 12, loc.minY() - 12,
				loc.maxX() + 12, loc.maxY() + 12) || (destX == 0 && destY == 0)) {
				mob.face(Point.location(destX, destY));
				mob.setLocation(Point.location(destX, destY));
			}
		}
		else {
			Player player = (Player) mob;
			player.face(Point.location(destX, destY));
			player.setLocation(Point.location(destX, destY));
			player.stepIncrementActivity();
		}

	}

	public Point getNextMovement() {
		if (path == null || path.isEmpty()) {
			return mob.getLocation();
		}
		Point destPoint = path.getNextPoint();
		Point curPoint = mob.getLocation();
		if (!PathValidation.checkAdjacent(mob, curPoint, destPoint)) {
			return curPoint;
		} else {
			return destPoint;
		}
	}

	public void reset() {
		path = null;
		if (this.mob.isPlayer()) {
			if (this.mob.getDropItemEvent() != null) {
				this.mob.runDropEvent(true);
			}
		}
		if (this.mob.getTalkToNpcEvent() != null) {
			this.mob.runTalkToNpcEvent();
		}
	}

	public boolean finished() {
		return path == null || path.isEmpty();
	}

	public void setPath(Path path) {
		this.path = path;
	}

	/**
	 * Voidscape — auto-open basic closed doors blocking a step. Returns true
	 * iff a door was opened; the caller should NOT reset the path so the
	 * player retries the step on the next tick (door is now traversable).
	 *
	 * Locates the wall object on the edge between {@code cur} and {@code
	 * next} via {@link Region#getWallGameObject(Point, int)}, then opens it
	 * only when its {@link DoorDef} is the trivial "Door" type with command1
	 * == "Open" (i.e., closed and not key/quest-locked). Replacement id 11
	 * matches what {@link Functions#doDoor} uses, with a 3 s auto-close —
	 * so behaviour is identical to a normal click except no teleport.
	 *
	 * Cardinal moves only; diagonals are rare for blocked doors and would
	 * need a both-walls check.
	 */
	private boolean tryAutoOpenBasicDoor(final Point cur, final Point next) {
		final int dx = next.getX() - cur.getX();
		final int dy = next.getY() - cur.getY();
		final World world = mob.getWorld();
		GameObject door = null;
		if (dx == 0 && dy != 0) {
			final int wallY = Math.max(cur.getY(), next.getY());
			final Point wallLoc = new Point(cur.getX(), wallY);
			final Region region = world.getRegionManager().getRegion(wallLoc);
			if (region != null) door = region.getWallGameObject(wallLoc, 0);
		} else if (dy == 0 && dx != 0) {
			final int wallX = Math.max(cur.getX(), next.getX());
			final Point wallLoc = new Point(wallX, cur.getY());
			final Region region = world.getRegionManager().getRegion(wallLoc);
			if (region != null) door = region.getWallGameObject(wallLoc, 1);
		}
		if (door != null) {
			final DoorDef def = door.getDoorDef();
			if (def != null
				&& "Door".equalsIgnoreCase(def.getName())
				&& "Open".equalsIgnoreCase(def.getCommand1())) {
				final int OPEN_DOOR_ID = 11;
				if (door.getID() == OPEN_DOOR_ID) return false;
				final GameObject opened = new GameObject(world, door.getLocation(),
					OPEN_DOOR_ID, door.getDirection(), door.getType());
				Functions.changeloc(door, opened);
				Functions.addloc(world, door.getLoc(), 3000);
				return true;
			}
		}

		// Also handle closed scenery gates near the step (Lumbridge farm, sheep
		// pen, etc.). Scenery gates are 1×2 (or 2×1) so the GameObject is
		// registered at one tile but its wall flags affect the next tile too —
		// scan a 3×3 around source AND dest to find the actual gate object.
		final GameObject gate = findGateNear(world, cur.getX(), cur.getY());
		final GameObject gate2 = (gate != null) ? gate : findGateNear(world, next.getX(), next.getY());
		final GameObject toOpen = (gate != null) ? gate : gate2;
		if (toOpen == null) return false;
		final int openVariant;
		switch (toOpen.getID()) {
			case 57: openVariant = 58; break;  // METAL_GENERIC closed → open
			case 60: openVariant = 59; break;  // WOODEN_GENERIC closed → open
			default: return false;
		}
		final GameObject openedGate = new GameObject(world, toOpen.getLocation(),
			openVariant, toOpen.getDirection(), toOpen.getType());
		Functions.changeloc(toOpen, openedGate);
		Functions.addloc(world, toOpen.getLoc(), 3000);
		return true;
	}

	/** 3×3 scan for an auto-openable gate scenery — handles 1×2/2×1 gate
	 *  footprints where the GameObject's registered tile is offset from the
	 *  tile where the wall flag actually blocks the step. */
	private static GameObject findGateNear(final World world, final int cx, final int cy) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				final Point p = new Point(cx + dx, cy + dy);
				final Region r = world.getRegionManager().getRegion(p);
				if (r == null) continue;
				final GameObject g = r.getGameObject(p);
				if (g == null || g.getGameObjectType() != GameObjectType.SCENERY) continue;
				if (g.getID() == 57 || g.getID() == 60) return g;
			}
		}
		return null;
	}
}
