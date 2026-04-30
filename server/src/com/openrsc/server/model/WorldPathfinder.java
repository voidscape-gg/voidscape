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

	// Generous enough for any F2P cross-region walk (Edgeville → Lumbridge,
	// Falador → Al Kharid, etc.). Earlier 50k cap exhausted on dense urban
	// routes — Varrock alone has thousands of wall edges to detour around.
	public static final int DEFAULT_NODE_CAP = 500_000;

	private static final int BASIC_COST = 10;
	private static final int DIAG_COST = 14;
	private static final int DESTINATION_SNAP_RADIUS = 12;
	private static final int WAYPOINT_MIN_DISTANCE = 64;
	private static final int WAYPOINT_LOOKAHEAD_DISTANCE = 64;
	private static final int WAYPOINT_SEGMENT_NODE_BUDGET = 8_192;

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
		// Snap unwalkable destinations (walls, building roofs, coastlines, and
		// — on F2P worlds — P2P-restricted regions) to the nearest walkable
		// tile within minimap radius. Without this, world-map clicks anywhere
		// off the path mesh fail with reason=NO_PATH.
		final TileValue endTile = world.getTile(end.getX(), end.getY());
		final boolean endBlocked = endTile == null
			|| (endTile.traversalMask & CollisionFlag.FULL_BLOCK) != 0
			|| !isAllowedLocation(end.getX(), end.getY());
		final Point target;
		if (endBlocked) {
			final Point snapped = nearestWalkable(end, DESTINATION_SNAP_RADIUS);
			if (snapped == null) {
				lastReason = Reason.NO_PATH;
				return null;
			}
			if (start.getX() == snapped.getX() && start.getY() == snapped.getY()) {
				lastReason = Reason.SAME_TILE;
				return Collections.emptyList();
			}
			target = snapped;
		} else {
			target = end;
		}

		final int estimatedDistance = Math.max(
			Math.abs(start.getX() - target.getX()),
			Math.abs(start.getY() - target.getY()));
		if (estimatedDistance >= WAYPOINT_MIN_DISTANCE) {
			final List<Point> waypointPath = findWaypointAssistedPath(start, target, maxNodes);
			if (waypointPath != null) {
				lastReason = Reason.OK;
				return waypointPath;
			}
			nodesExplored = 0;
		}
		return findPathAStar(start, target, maxNodes);
	}

	private List<Point> findPathAStar(final Point start, final Point target, final int maxNodes) {
		final int estimatedDistance = Math.max(
			Math.abs(start.getX() - target.getX()),
			Math.abs(start.getY() - target.getY()));
		final int initialCapacity = Math.min(maxNodes, Math.max(1024, estimatedDistance * 16));
		final HashMap<Long, Node> visited = new HashMap<>(initialCapacity);
		final PriorityQueue<Node> open = new PriorityQueue<>(
			Math.max(16, Math.min(initialCapacity, 4096)),
			(a, b) -> Integer.compare(a.fCost, b.fCost));

		final Node startNode = new Node(start.getX(), start.getY(), 0,
			heuristic(start.getX(), start.getY(), target.getX(), target.getY()), null);
		visited.put(key(startNode.x, startNode.y), startNode);
		open.add(startNode);

		Node found = null;
		while (!open.isEmpty()) {
			final Node current = open.poll();
			if (current.closed) continue;
			final Node bestKnown = visited.get(key(current.x, current.y));
			if (bestKnown != current && current.gCost > bestKnown.gCost) continue;
			current.closed = true;

			if (current.x == target.getX() && current.y == target.getY()) {
				found = current;
				break;
			}

			nodesExplored++;
			if (nodesExplored >= maxNodes) {
				lastReason = Reason.CAP_EXHAUSTED;
				return null;
			}

			expand(current, target.getX(), target.getY(), open, visited);
		}

		if (found == null) {
			lastReason = Reason.NO_PATH;
			return null;
		}
		lastReason = Reason.OK;
		return reconstruct(found);
	}

	private List<Point> findWaypointAssistedPath(final Point start, final Point target, final int maxNodes) {
		final WaypointGraph graph = WaypointGraph.getDefault();
		if (graph == null) return null;

		final List<WaypointGraph.Node> waypointRoute = graph.route(
			start.getX(), start.getY(), target.getX(), target.getY(), this::isAllowedLocation);
		if (waypointRoute == null || waypointRoute.size() < 2) return null;

		final ArrayList<Point> stitched = new ArrayList<>();
		Point anchor = start;
		for (int i = 0; i < waypointRoute.size(); i++) {
			i = farthestDirectWaypoint(anchor, waypointRoute, i);
			final WaypointGraph.Node node = waypointRoute.get(i);
			final Point next = Point.location(node.x, node.y);
			if (!appendSegment(stitched, anchor, next, maxNodes)) return null;
			anchor = next;
		}
		if (!appendSegment(stitched, anchor, target, maxNodes)) return null;
		return stitched.isEmpty() ? null : stitched;
	}

	private int farthestDirectWaypoint(final Point anchor, final List<WaypointGraph.Node> route, final int startIndex) {
		int best = startIndex;
		for (int i = startIndex + 1; i < route.size(); i++) {
			final WaypointGraph.Node node = route.get(i);
			final int distance = Math.max(Math.abs(anchor.getX() - node.x), Math.abs(anchor.getY() - node.y));
			if (distance > WAYPOINT_LOOKAHEAD_DISTANCE) break;
			if (!isDirectSegmentClear(anchor.getX(), anchor.getY(), node.x, node.y)) break;
			best = i;
		}
		return best;
	}

	private boolean appendSegment(final ArrayList<Point> stitched, final Point from, final Point to, final int maxNodes) {
		if (from.getX() == to.getX() && from.getY() == to.getY()) return true;
		if (appendDirectSegment(stitched, from.getX(), from.getY(), to.getX(), to.getY())) return true;

		final int segmentCap = Math.min(maxNodes, nodesExplored + WAYPOINT_SEGMENT_NODE_BUDGET);
		final List<Point> segment = findPathAStar(from, to, segmentCap);
		if (segment == null) return false;
		stitched.addAll(segment);
		return nodesExplored < maxNodes;
	}

	private boolean appendDirectSegment(final ArrayList<Point> stitched,
	                                    final int fromX, final int fromY,
	                                    final int toX, final int toY) {
		final int originalSize = stitched.size();
		int x = fromX;
		int y = fromY;
		while (x != toX || y != toY) {
			final int nextX = x + Integer.compare(toX, x);
			final int nextY = y + Integer.compare(toY, y);
			if (!canStep(x, y, nextX, nextY)) {
				while (stitched.size() > originalSize) {
					stitched.remove(stitched.size() - 1);
				}
				return false;
			}
			if (!isAllowedLocation(nextX, nextY)) {
				while (stitched.size() > originalSize) {
					stitched.remove(stitched.size() - 1);
				}
				return false;
			}
			stitched.add(Point.location(nextX, nextY));
			x = nextX;
			y = nextY;
		}
		return true;
	}

	private boolean isDirectSegmentClear(final int fromX, final int fromY, final int toX, final int toY) {
		int x = fromX;
		int y = fromY;
		while (x != toX || y != toY) {
			final int nextX = x + Integer.compare(toX, x);
			final int nextY = y + Integer.compare(toY, y);
			if (!canStep(x, y, nextX, nextY)) return false;
			if (!isAllowedLocation(nextX, nextY)) return false;
			x = nextX;
			y = nextY;
		}
		return true;
	}

	/**
	 * Snap an unwalkable destination to the nearest walkable tile within
	 * {@code radius} (Chebyshev). Used so a world-map click on a wall, a
	 * building roof, or a coastline routes to the boundary tile instead of
	 * silently failing.
	 *
	 * Maintains the same floor as {@code end} (y / 944) and respects the F2P
	 * region filter on F2P worlds, so we never snap a free player across the
	 * F2P/P2P boundary.
	 */
	private Point nearestWalkable(final Point end, final int radius) {
		final int ex = end.getX();
		final int ey = end.getY();
		final int floor = Formulae.getHeight(end);
		for (int r = 1; r <= radius; r++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dx = -r; dx <= r; dx++) {
					if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
					final int nx = ex + dx;
					final int ny = ey + dy;
					if (nx < 0 || ny < 0) continue;
					if (ny / 944 != floor) continue;
					final TileValue t = world.getTile(nx, ny);
					if (t == null) continue;
					if ((t.traversalMask & CollisionFlag.FULL_BLOCK) != 0) continue;
					if (!isAllowedLocation(nx, ny)) continue;
					return Point.location(nx, ny);
				}
			}
		}
		return null;
	}

	private void expand(final Node cur, final int ex, final int ey,
	                    final PriorityQueue<Node> open, final HashMap<Long, Node> visited) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				if (dx == 0 && dy == 0) continue;
				final int nx = cur.x + dx;
				final int ny = cur.y + dy;
				if (!canStep(cur.x, cur.y, nx, ny)) continue;
				if (!isAllowedLocation(nx, ny)) continue;

				final int stepCost = (dx == 0 || dy == 0) ? BASIC_COST : DIAG_COST;
				addNeighbor(cur, nx, ny, stepCost, ex, ey, open, visited);
			}
		}

		// Telepoint hops: zero-or-low-cost long-range edges sourced from
		// ObjectTelePoints.xml. They span floors (Y % 944 changes), which is
		// the whole point of slice 3.
		if (telePointGraph != null) {
			for (TelePointGraph.Edge edge : telePointGraph.edgesAt(cur.x, cur.y)) {
				if (!isAllowedLocation(edge.destX, edge.destY)) continue;
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
	 * Static adjacency check. Uses the same terrain collision rules as the
	 * WalkingQueue, but ignores mob/player occupancy so temporary blockers can
	 * still be handled by runtime recovery.
	 *
	 * Voidscape: when static collision rejects a step but the blocked edge is
	 * an auto-openable closed door/gate, allow it; the {@link WalkingQueue}
	 * opens that exact obstacle on contact and retries the step.
	 */
	private boolean canStep(final int sx, final int sy, final int dx, final int dy) {
		final TileValue st = world.getTile(sx, sy);
		final TileValue dt = world.getTile(dx, dy);
		if (st == null || dt == null) return false;

		if (PathValidation.checkAdjacentStatic(world, sx, sy, dx, dy)) return true;

		// Voidscape bypass: if there's an auto-openable closed route obstacle,
		// allow — walker auto-opens on contact.
		if (!isAllowedLocation(dx, dy)) return false;
		return AutoOpenRouteObstacle.canOpenBetween(world, sx, sy, dx, dy);
	}

	private boolean isAllowedLocation(final int x, final int y) {
		return !filterP2P || Formulae.isF2PLocation(x, y);
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
