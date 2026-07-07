package com.openrsc.server.event.rsc.impl;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Path;
import com.openrsc.server.model.Path.PathType;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.TelePointGraph;
import com.openrsc.server.model.WorldPathfinder;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.EscapeNpcTrigger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Drains a precomputed long-distance path into the player's WalkingQueue
 * one chunk at a time, refilling whenever the queue empties. Path lengths
 * routinely exceed {@link Path}'s 50-step queue capacity, so we feed it in
 * pieces rather than all at once.
 *
 * Cancellation is delegated to {@link Player#cancelAutoWalk()} — regular walk
 * packets, explicit player actions, and the future Stop button all route
 * through that.
 * This event also self-stops if the player drifts off the planned tiles
 * (e.g. NPC pushed them off, teleport, knock-back) and a re-pathfind from
 * the new position fails.
 */
public class AutoWalkEvent extends GameTickEvent {

	/** How many tiles to feed the WalkingQueue per refill. Stays comfortably below {@code Path.MAXIMUM_SIZE}. */
	private static final int CHUNK_SIZE = 40;
	private static final int REASON_NO_PATH = 1;
	private static final int REASON_BUSY = 6;

	/**
	 * Recovery cap when re-pathfinding mid-route.
	 *
	 * Keep this equal to the initial pathfinder budget. The old 5k cap was
	 * enough for tiny displacements, but a queue reset inside a long fed chunk
	 * can require a full cross-region route from the current tile.
	 */
	private static final int RECOVERY_NODE_CAP = WorldPathfinder.DEFAULT_NODE_CAP;

	private final Deque<Point> remaining;
	private final Point goal;

	public AutoWalkEvent(final World world, final Player owner, final List<Point> path) {
		// ALLOW_MULTIPLE because cancelAutoWalk() stops the previous event
		// in-tick and registers a new one before the cleanup pass runs;
		// ONE_PER_MOB would reject the replacement.
		super(world, owner, 0, "Auto Walk Event", DuplicationStrategy.ALLOW_MULTIPLE);
		this.remaining = new ArrayDeque<>(path);
		this.goal = path.isEmpty() ? owner.getLocation() : path.get(path.size() - 1);
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
		if (player.inCombat() && !retreatFromNpcCombat(player)) {
			return;
		}
		if (player.isBusy()) {
			fail(player, REASON_BUSY);
			return;
		}

		// Wait until the queue is fully drained before refilling so we don't
		// race the WalkingQueue's per-tick advance.
		if (!player.getWalkingQueue().finished()) {
			return;
		}

		if (remaining.isEmpty()) {
			if (isAtGoal(player)) {
				player.cancelAutoWalk();
				return;
			}
			if (!repathFromCurrent(player) || remaining.isEmpty()) {
				fail(player, REASON_NO_PATH);
				return;
			}
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
			if (!repathFromCurrent(player) || remaining.isEmpty()) {
				fail(player, REASON_NO_PATH);
				return;
			}
		}

		feedChunk(player);
	}

	private boolean repathFromCurrent(final Player player) {
		if (isAtGoal(player)) return true;
		final WorldPathfinder pf = new WorldPathfinder(player.getWorld(), player);
		final List<Point> fresh = pf.findPath(player.getLocation(), goal, RECOVERY_NODE_CAP);
		if (fresh == null) return false;
		remaining.clear();
		remaining.addAll(fresh);
		if (!fresh.isEmpty()) {
			ActionSender.sendWorldWalkRoute(player, true, 0, fresh);
		}
		return true;
	}

	private void feedChunk(final Player player) {
		if (remaining.isEmpty()) return;
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
		if (taken == 0) return;
		path.finish();
		player.getWalkingQueue().setPath(path);
	}

	private boolean retreatFromNpcCombat(final Player player) {
		final Mob opponent = player.getOpponent();
		if (opponent == null || !opponent.isNpc()) {
			player.cancelAutoWalk();
			return false;
		}
		if (opponent.getHitsMade() < 3) {
			return false;
		}

		opponent.setLastOpponent(opponent.getOpponent());
		player.setLastOpponent(opponent);
		player.setCombatTimer();
		player.setLastCombatState(CombatState.RUNNING);
		opponent.setLastCombatState(CombatState.WAITING);
		player.resetCombatEvent();
		player.setRanAwayTimer();
		ActionSender.sendSound(player, "retreat");

		if (player.getConfig().WANT_PARTIES && player.getParty() != null) {
			player.getParty().sendParty();
		}
		player.getWorld().getServer().getPluginHandler().handlePlugin(
			EscapeNpcTrigger.class, player, new Object[]{player, (Npc) opponent});
		return true;
	}

	private boolean isAtGoal(final Player player) {
		return player.getX() == goal.getX() && player.getY() == goal.getY();
	}

	private void fail(final Player player, final int reason) {
		ActionSender.sendWorldWalkRoute(player, false, reason, java.util.Collections.emptyList());
		player.cancelAutoWalk();
	}
}
