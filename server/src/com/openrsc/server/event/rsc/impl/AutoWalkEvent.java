package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.Path.PathType;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.TelePointGraph;
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

		final Point head = remaining.peekFirst();
		final boolean adjacent = Math.abs(head.getX() - player.getX()) <= 1
			&& Math.abs(head.getY() - player.getY()) <= 1;

		if (!adjacent) {
			// Two reasons head can be non-adjacent: a planned telepoint hop
			// (slice 3 — ladder/stair edge from the TelePointGraph) or the
			// player got displaced (knock-back, teleport spell, etc.).
			final TelePointGraph graph = player.getWorld().getTelePointGraph();
			if (graph != null && graph.hasEdge(player.getX(), player.getY(), head.getX(), head.getY())) {
				player.teleport(head.getX(), head.getY(), false);
				remaining.pollFirst();
				return; // let the teleport settle; resume walking next tick
			}
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
		Point last = player.getLocation();
		while (taken < CHUNK_SIZE && !remaining.isEmpty()) {
			final Point next = remaining.peekFirst();
			// Stop at telepoint boundaries: a non-adjacent step shouldn't
			// be fed to Path.addStep (which would interpolate through walls
			// trying to reach it). The next tick will detect the boundary,
			// teleport via the TelePointGraph, and resume.
			if (Math.abs(next.getX() - last.getX()) > 1 || Math.abs(next.getY() - last.getY()) > 1) {
				break;
			}
			path.addStep(next.getX(), next.getY());
			remaining.pollFirst();
			last = next;
			taken++;
		}
		path.finish();
		player.getWalkingQueue().setPath(path);
	}
}
