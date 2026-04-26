package com.openrsc.server.model;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GameObjectType;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
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
			|| (filterP2P && !Formulae.isF2PLocation(end));
		final Point target;
		if (endBlocked) {
			final Point snapped = nearestWalkable(end, 5);
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

		final HashMap<Long, Node> visited = new HashMap<>();
		final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Integer.compare(a.fCost, b.fCost));

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
					final Point candidate = Point.location(nx, ny);
					if (Formulae.getHeight(candidate) != floor) continue;
					final TileValue t = world.getTile(nx, ny);
					if (t == null) continue;
					if ((t.traversalMask & CollisionFlag.FULL_BLOCK) != 0) continue;
					if (filterP2P && !Formulae.isF2PLocation(candidate)) continue;
					return candidate;
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
	 *
	 * Voidscape: when the geometric check would reject a step but there's an
	 * auto-openable closed gate adjacent to the source or destination, allow
	 * it — the {@link WalkingQueue} auto-opens those on contact. Scenery
	 * gates set wall flags on a 1×2 (or 2×1) footprint that's offset from
	 * their registered tile, so we scan a small neighbourhood instead of
	 * trying to back-trace the exact flag source.
	 */
	private boolean canStep(final int sx, final int sy, final int dx, final int dy,
	                        final int xdir, final int ydir) {
		final TileValue st = world.getTile(sx, sy);
		final TileValue dt = world.getTile(dx, dy);
		if (st == null || dt == null) return false;

		final boolean fullBlocked = (dt.traversalMask & CollisionFlag.FULL_BLOCK) != 0;

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

		final boolean diagonal = xdir != 0 && ydir != 0;
		boolean blocked;
		if (!diagonal) {
			blocked = fullBlocked || xBlocked || yBlocked;
		} else if (fullBlocked || xBlocked || yBlocked) {
			blocked = true;
		} else {
			// For diagonal moves, require both cardinal half-steps to be clear
			// AND the diagonal corner-pass to be unobstructed.
			final TileValue xMid = world.getTile(sx + xdir, sy);
			final TileValue yMid = world.getTile(sx, sy + ydir);
			if (xMid == null || yMid == null) blocked = true;
			else if ((xMid.traversalMask & CollisionFlag.FULL_BLOCK) != 0) blocked = true;
			else if ((yMid.traversalMask & CollisionFlag.FULL_BLOCK) != 0) blocked = true;
			else blocked = PathValidation.checkDiagonalPassThroughCollisions(world,
				Point.location(sx, sy), Point.location(dx, dy));
		}

		if (!blocked) return true;

		// Voidscape bypass: if there's an auto-openable closed gate within the
		// step's neighbourhood, allow — walker auto-opens on contact.
		if (filterP2P && !Formulae.isF2PLocation(Point.location(dx, dy))) return false;
		return isAutoOpenableGateNear(sx, sy) || isAutoOpenableGateNear(dx, dy);
	}

	/**
	 * Voidscape: is there an auto-openable closed gate in the 3×3 neighbourhood
	 * of (x, y)? Scenery gates are typically 1×2 with the GameObject registered
	 * at one tile but wall flags set on both tiles of the footprint (and the
	 * adjacent tile across the wall edge), so the gate object itself isn't at
	 * (x, y) when its flags are blocking a step there. The 3×3 scan catches
	 * the gate regardless of which corner of its footprint we're at.
	 *
	 * Match: scenery with {@code GameObjectDef.name == "gate"} (case-insensitive
	 * exact — excludes "metal gate", "metalic dungeon gate", "ardounge wall
	 * gateway", "gnome stronghold gate", etc.) AND {@code command1 == "open"}
	 * (so we only target closed instances).
	 */
	private boolean isAutoOpenableGateNear(final int cx, final int cy) {
		for (int dy = -1; dy <= 1; dy++) {
			for (int dx = -1; dx <= 1; dx++) {
				if (isAutoOpenableGateAt(cx + dx, cy + dy)) return true;
			}
		}
		return false;
	}

	private boolean isAutoOpenableGateAt(final int x, final int y) {
		final Point loc = Point.location(x, y);
		final Region region = world.getRegionManager().getRegion(loc);
		if (region == null) return false;
		final GameObject scenery = region.getGameObject(loc);
		if (scenery == null || scenery.getGameObjectType() != GameObjectType.SCENERY) return false;
		final com.openrsc.server.external.GameObjectDef def = scenery.getGameObjectDef();
		if (def == null) return false;
		final String name = def.getName();
		if (name == null || !"gate".equalsIgnoreCase(name.trim())) return false;
		final String cmd = def.getCommand1();
		return cmd != null && "open".equalsIgnoreCase(cmd.trim());
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
