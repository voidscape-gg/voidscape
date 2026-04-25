package com.openrsc.server.model;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.Formulae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Long-distance same-floor A* for the world-map auto-walker.
 *
 * Cross-floor traversal lives in slice 3 (TelePoint graph). For now the
 * search refuses inputs whose start/end land on different floors, where
 * floor = {@code y / 944} per {@link Formulae#getHeight}.
 *
 * Why a new class instead of extending {@link AStarPathfinder}: that one
 * pre-allocates a {@code Node[2*depth+1][2*depth+1]} cost board, which is
 * fine for short interaction-range walks but blows up for the cross-region
 * distances a world-map click implies. We use a sparse hash-keyed open/closed
 * set bounded by an explored-node cap.
 *
 * The adjacency check is geometric only (walls + diagonal corners). It does
 * not consider mob/player blocking the way {@link PathValidation#checkAdjacent}
 * does — the route is planned against static geometry and the runtime walking
 * queue handles transient blockers via its own {@code reset()} path.
 */
public class WorldPathfinder {

	public static final int DEFAULT_NODE_CAP = 50_000;

	private static final int BASIC_COST = 10;
	private static final int DIAG_COST = 14;

	public enum Reason { OK, NO_PATH, CAP_EXHAUSTED, INVALID_INPUT, SAME_TILE, CROSS_FLOOR }

	private final World world;
	private final Player owner;
	private final boolean filterP2P;
	private final TelePointGraph telePointGraph;

	private Reason lastReason = Reason.OK;
	private int nodesExplored;

	public WorldPathfinder(final World world, final Player owner) {
		this(world, owner, world == null ? null : world.getTelePointGraph());
	}

	public WorldPathfinder(final World world, final Player owner, final TelePointGraph telePointGraph) {
		this.world = world;
		this.owner = owner;
		this.filterP2P = !world.getServer().getConfig().MEMBER_WORLD;
		this.telePointGraph = telePointGraph;
	}

	public Reason getLastReason() { return lastReason; }
	public int getNodesExplored() { return nodesExplored; }

	public List<Point> findPath(final Point start, final Point end) {
		return findPath(start, end, DEFAULT_NODE_CAP);
	}

	public List<Point> findPath(final Point start, final Point end, final int maxNodes) {
		nodesExplored = 0;
		if (start == null || end == null) {
			lastReason = Reason.INVALID_INPUT;
			return null;
		}
		if (start.getX() == end.getX() && start.getY() == end.getY()) {
			lastReason = Reason.SAME_TILE;
			return Collections.emptyList();
		}
		if (telePointGraph == null && Formulae.getHeight(start) != Formulae.getHeight(end)) {
			// Without a graph there is no way to cross floors; reject early so
			// we don't waste 50k-node frontier exploring a same-floor dead end.
			lastReason = Reason.CROSS_FLOOR;
			return null;
		}
		final TileValue endTile = world.getTile(end.getX(), end.getY());
		if (endTile == null || (endTile.traversalMask & CollisionFlag.FULL_BLOCK) != 0) {
			lastReason = Reason.NO_PATH;
			return null;
		}
		if (filterP2P && !Formulae.isF2PLocation(end)) {
			lastReason = Reason.NO_PATH;
			return null;
		}

		final HashMap<Long, Node> visited = new HashMap<>();
		final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.fCost, b.fCost));

		final Node startNode = new Node(start.getX(), start.getY(), 0,
			heuristic(start.getX(), start.getY(), end.getX(), end.getY()), null);
		visited.put(key(startNode.x, startNode.y), startNode);
		open.add(startNode);

		Node found = null;
		while (!open.isEmpty()) {
			final Node current = open.poll();
			if (current.closed) continue;
			final Node bestKnown = visited.get(key(current.x, current.y));
			if (bestKnown != current && current.gCost > bestKnown.gCost) continue;
			current.closed = true;

			if (current.x == end.getX() && current.y == end.getY()) {
				found = current;
				break;
			}

			nodesExplored++;
			if (nodesExplored >= maxNodes) {
				lastReason = Reason.CAP_EXHAUSTED;
				return null;
			}

			expand(current, end.getX(), end.getY(), open, visited);
		}

		if (found == null) {
			lastReason = Reason.NO_PATH;
			return null;
		}
		lastReason = Reason.OK;
		return reconstruct(found);
	}

	private void expand(final Node cur, final int ex, final int ey,
	                    final PriorityQueue<Node> open, final HashMap<Long, Node> visited) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue;
				final int nx = cur.x + dx;
				final int ny = cur.y + dy;
				if (!canStep(cur.x, cur.y, nx, ny, dx, dy)) continue;
				if (filterP2P && !Formulae.isF2PLocation(Point.location(nx, ny))) continue;

				final int stepCost = (dx == 0 || dy == 0) ? BASIC_COST : DIAG_COST;
				addNeighbor(cur, nx, ny, stepCost, ex, ey, open, visited);
			}
		}

		// Telepoint hops: zero-or-low-cost long-range edges sourced from
		// ObjectTelePoints.xml. They span floors (Y % 944 changes), which is
		// the whole point of slice 3.
		if (telePointGraph != null) {
			for (TelePointGraph.Edge edge : telePointGraph.edgesAt(cur.x, cur.y)) {
				if (filterP2P && !Formulae.isF2PLocation(Point.location(edge.destX, edge.destY))) continue;
				final TileValue dst = world.getTile(edge.destX, edge.destY);
				if (dst == null) continue;
				addNeighbor(cur, edge.destX, edge.destY, BASIC_COST, ex, ey, open, visited);
			}
		}
	}

	private void addNeighbor(final Node cur, final int nx, final int ny, final int stepCost,
	                          final int ex, final int ey,
	                          final PriorityQueue<Node> open, final HashMap<Long, Node> visited) {
		final int g = cur.gCost + stepCost;
		final long k = key(nx, ny);
		final Node existing = visited.get(k);
		if (existing != null && existing.gCost <= g) return;
		final Node n = new Node(nx, ny, g, g + heuristic(nx, ny, ex, ey), cur);
		visited.put(k, n);
		open.add(n);
	}

	/**
	 * Geometric-only adjacency check: walls on either tile's facing edges,
	 * full-block tiles, diagonal corner clipping. Does not consult mobs.
	 */
	private boolean canStep(final int sx, final int sy, final int dx, final int dy,
	                        final int xdir, final int ydir) {
		final TileValue st = world.getTile(sx, sy);
		final TileValue dt = world.getTile(dx, dy);
		if (st == null || dt == null) return false;
		if ((dt.traversalMask & CollisionFlag.FULL_BLOCK) != 0) return false;

		final boolean diagonal = xdir != 0 && ydir != 0;

		// Wall on the X-facing edge of source or destination.
		boolean xBlocked = false;
		if (xdir > 0) {
			xBlocked = isBlocked(st, CollisionFlag.WALL_EAST, true)
				|| isBlocked(dt, CollisionFlag.WALL_WEST, false);
		} else if (xdir < 0) {
			xBlocked = isBlocked(st, CollisionFlag.WALL_WEST, true)
				|| isBlocked(dt, CollisionFlag.WALL_EAST, false);
		}

		boolean yBlocked = false;
		if (ydir > 0) {
			yBlocked = isBlocked(st, CollisionFlag.WALL_SOUTH, true)
				|| isBlocked(dt, CollisionFlag.WALL_NORTH, false);
		} else if (ydir < 0) {
			yBlocked = isBlocked(st, CollisionFlag.WALL_NORTH, true)
				|| isBlocked(dt, CollisionFlag.WALL_SOUTH, false);
		}

		if (!diagonal) {
			return !(xBlocked || yBlocked);
		}

		// For diagonal moves, require both cardinal half-steps to be clear AND
		// the diagonal corner-pass to be unobstructed.
		if (xBlocked || yBlocked) return false;
		final TileValue xMid = world.getTile(sx + xdir, sy);
		final TileValue yMid = world.getTile(sx, sy + ydir);
		if (xMid == null || yMid == null) return false;
		if ((xMid.traversalMask & CollisionFlag.FULL_BLOCK) != 0) return false;
		if ((yMid.traversalMask & CollisionFlag.FULL_BLOCK) != 0) return false;
		return !PathValidation.checkDiagonalPassThroughCollisions(world,
			Point.location(sx, sy), Point.location(dx, dy));
	}

	private static boolean isBlocked(final TileValue tile, final int flag, final boolean isSource) {
		if (tile == null) return true;
		return PathValidation.isBlocking(tile.traversalMask, (byte) flag, isSource);
	}

	private List<Point> reconstruct(final Node end) {
		final ArrayList<Point> rev = new ArrayList<>();
		Node n = end;
		while (n.parent != null) {
			rev.add(Point.location(n.x, n.y));
			n = n.parent;
		}
		Collections.reverse(rev);
		return rev;
	}

	private static int heuristic(final int ax, final int ay, final int bx, final int by) {
		final int xd = Math.abs(ax - bx);
		final int yd = Math.abs(ay - by);
		final int s = Math.min(xd, yd);
		final int l = Math.max(xd, yd);
		return s * DIAG_COST + (l - s) * BASIC_COST;
	}

	private static long key(final int x, final int y) {
		return ((long) x << 16) | (y & 0xFFFFL);
	}

	private static final class Node {
		final int x, y;
		final int gCost;
		final int fCost;
		final Node parent;
		boolean closed;

		Node(final int x, final int y, final int gCost, final int fCost, final Node parent) {
			this.x = x;
			this.y = y;
			this.gCost = gCost;
			this.fCost = fCost;
			this.parent = parent;
		}
	}
}
