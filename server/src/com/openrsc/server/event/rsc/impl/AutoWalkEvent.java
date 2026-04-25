package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.Path.PathType;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.WorldPathfinder;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Drains a precomputed long-distance path into the player's WalkingQueue
 * one chunk at a time, refilling whenever the queue empties. Path lengths
 * routinely exceed {@link Path}'s 50-step queue capacity, so we feed it in
 * pieces rather than all at once.
 *
 * Cancellation is delegated to {@link Player#cancelAutoWalk()} — combat,
 * regular walk packets, and the future Stop button all route through that.
 * This event also self-stops if the player drifts off the planned tiles
 * (e.g. NPC pushed them off, teleport, knock-back) and a re-pathfind from
 * the new position fails.
 */
public class AutoWalkEvent extends GameTickEvent {

	/** How many tiles to feed the WalkingQueue per refill. Stays comfortably below {@code Path.MAXIMUM_SIZE}. */
	private static final int CHUNK_SIZE = 40;

	/** Recovery cap when re-pathfinding mid-route (the player got displaced). Smaller than the initial cap. */
	private static final int RECOVERY_NODE_CAP = 5_000;

	private final Deque<Point> remaining;

	public AutoWalkEvent(final World world, final Player owner, final List<Point> path) {
		// ALLOW_MULTIPLE because cancelAutoWalk() stops the previous event
		// in-tick and registers a new one before the cleanup pass runs;
		// ONE_PER_MOB would reject the replacement.
		super(world, owner, 0, "Auto Walk Event", DuplicationStrategy.ALLOW_MULTIPLE);
		this.remaining = new ArrayDeque<>(path);
	}

	@Override
	public void run() {
		final Player player = getPlayerOwner();
		if (player == null) {
			stop();
			return;
		}
		// Someone else replaced our reference (a fresh ::pathto, a Stop button, etc.).
		if (player.getAutoWalkEvent() != this) {
			stop();
			return;
		}
		// Combat / generic-busy abort. cancelAutoWalk() will be called separately
		// from the relevant hooks; this is a defensive belt-and-suspenders check.
		if (player.inCombat() || player.isBusy()) {
			player.cancelAutoWalk();
			return;
		}

		// Wait until the queue is fully drained before refilling so we don't
		// race the WalkingQueue's per-tick advance.
		if (!player.getWalkingQueue().finished()) {
			return;
		}

		if (remaining.isEmpty()) {
			player.cancelAutoWalk();
			return;
		}

		// If the player isn't where the path expects, try to recover.
		final Point head = remaining.peekFirst();
		if (Math.abs(head.getX() - player.getX()) > 1 || Math.abs(head.getY() - player.getY()) > 1) {
			if (!repathFromCurrent(player)) {
				player.cancelAutoWalk();
				return;
			}
		}

		feedChunk(player);
	}

	private boolean repathFromCurrent(final Player player) {
		final Point goal = remaining.peekLast();
		if (goal == null) return false;
		final WorldPathfinder pf = new WorldPathfinder(player.getWorld(), player);
		final List<Point> fresh = pf.findPath(player.getLocation(), goal, RECOVERY_NODE_CAP);
		if (fresh == null) return false;
		remaining.clear();
		remaining.addAll(fresh);
		return true;
	}

	private void feedChunk(final Player player) {
		final Path path = new Path(player, PathType.WALK_TO_POINT);
		int taken = 0;
		while (taken < CHUNK_SIZE && !remaining.isEmpty()) {
			final Point step = remaining.pollFirst();
			path.addStep(step.getX(), step.getY());
			taken++;
		}
		path.finish();
		player.getWalkingQueue().setPath(path);
	}
}
