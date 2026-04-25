package com.openrsc.server.model;

import com.openrsc.server.external.EntityHandler;
import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.TileValue;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.Formulae;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Graph of "teleport-style" connections between distant tiles, used by
 * {@link WorldPathfinder} to span floors and the few specially-wired
 * crossings (Underground Pass, Watchtower mining camps, etc.).
 *
 * <p>Two edge sources, both built once at first access:
 * <ol>
 *   <li>{@code ObjectTelePoints.xml} — ~18 explicit (point + command) → point
 *       entries. Used as exact-match edges.</li>
 *   <li>Climbable scenery (1×1 ladders) — every scenery object whose
 *       {@code command1} or {@code command2} is one of {@code climb-up,
 *       climb up, go up, climb-down, climb down, go down} contributes an
 *       edge from each walkable adjacent tile to {@code (sameX,
 *       Formulae.getNewY(sameY, dir))}, mirroring
 *       {@link com.openrsc.server.plugins.authentic.defaults.Ladders}'s
 *       {@code coordModifier} for height-1 objects.</li>
 * </ol>
 *
 * <p><b>Caveats (slice 3 v1.5):</b>
 * <ul>
 *   <li>Multi-tile staircases ({@code def.getHeight() > 1}) are skipped —
 *       their destination depends on the object's direction in a way the
 *       static graph can't predict cleanly. They'll need slice 3.x.</li>
 *   <li>Quest-gated ladders are NOT excluded; the auto-walker can teleport
 *       through them even when the player would normally fail the prereq.
 *       This is mostly a single-player dev posture concession; for
 *       multiplayer we'd need an exclusion list mirroring the special-case
 *       branches in {@code Ladders.java}.</li>
 *   <li>F2P region filtering is NOT done in the graph itself — the
 *       pathfinder rejects edges whose destination is in P2P at expansion
 *       time, so members ladders inside F2P pockets get filtered correctly
 *       without us hardcoding ID lists.</li>
 * </ul>
 */
public final class TelePointGraph {

	public static final class Edge {
		public final int destX;
		public final int destY;
		public final String command;

		public Edge(final int destX, final int destY, final String command) {
			this.destX = destX;
			this.destY = destY;
			this.command = command;
		}
	}

	private static final Logger LOGGER = LogManager.getLogger();

	private final Map<Long, List<Edge>> edges = new HashMap<>();
	private int xmlEdgeCount;
	private int scenerySourceCount;
	private int sceneryEdgeCount;

	public TelePointGraph(final World world) {
		if (world == null) return;
		final long started = System.currentTimeMillis();
		final EntityHandler eh = world.getServer().getEntityHandler();
		buildXmlEdges(eh.getObjectTelePoints());
		buildSceneryEdges(world, eh);
		LOGGER.info("TelePointGraph built in {} ms — XML edges: {}, scenery sources: {}, scenery edges: {}, source tiles: {}",
			System.currentTimeMillis() - started, xmlEdgeCount, scenerySourceCount, sceneryEdgeCount, edges.size());
	}

	private void buildXmlEdges(final Map<Point, TelePoint> objectTelePoints) {
		if (objectTelePoints == null) return;
		for (Map.Entry<Point, TelePoint> entry : objectTelePoints.entrySet()) {
			final Point src = entry.getKey();
			final TelePoint dst = entry.getValue();
			if (src == null || dst == null) continue;
			addEdge(src.getX(), src.getY(), dst.getX(), dst.getY(), dst.getCommand());
			xmlEdgeCount++;
		}
	}

	private void buildSceneryEdges(final World world, final EntityHandler eh) {
		final Map<Point, Integer> scenery = world.getSceneryLocs();
		if (scenery == null) return;
		for (Map.Entry<Point, Integer> entry : scenery.entrySet()) {
			final Point objPos = entry.getKey();
			final int objId = entry.getValue();
			final GameObjectDef def = eh.getGameObjectDef(objId);
			if (def == null) continue;
			if (def.getHeight() > 1) continue; // multi-tile staircases — not handled in v1.5
			final boolean canUp = isClimb(def.command1, true) || isClimb(def.command2, true);
			final boolean canDown = isClimb(def.command1, false) || isClimb(def.command2, false);
			if (!canUp && !canDown) continue;
			scenerySourceCount++;

			// Each walkable tile adjacent to the scenery becomes a graph
			// source. The destination is on the same X with Y rotated to
			// the next floor via Formulae.getNewY — matching the runtime
			// behaviour of Ladders.coordModifier for height-1 objects.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dy == 0) continue;
					final int adjX = objPos.getX() + dx;
					final int adjY = objPos.getY() + dy;
					final TileValue tile = world.getTile(adjX, adjY);
					if (tile == null) continue;
					if ((tile.traversalMask & CollisionFlag.FULL_BLOCK) != 0) continue;
					if (canUp) {
						final int destY = Formulae.getNewY(adjY, true);
						if (destY != adjY) {
							addEdge(adjX, adjY, adjX, destY, "auto-climb-up");
							sceneryEdgeCount++;
						}
					}
					if (canDown) {
						final int destY = Formulae.getNewY(adjY, false);
						if (destY != adjY) {
							addEdge(adjX, adjY, adjX, destY, "auto-climb-down");
							sceneryEdgeCount++;
						}
					}
				}
			}
		}
	}

	private static boolean isClimb(final String command, final boolean up) {
		if (command == null) return false;
		final String c = command.toLowerCase();
		if (up) {
			return c.equals("climb-up") || c.equals("climb up") || c.equals("go up");
		}
		return c.equals("climb-down") || c.equals("climb down") || c.equals("go down");
	}

	private void addEdge(final int sx, final int sy, final int dx, final int dy, final String command) {
		edges.computeIfAbsent(key(sx, sy), k -> new ArrayList<>()).add(new Edge(dx, dy, command));
	}

	/** Edges leaving the given tile. Empty list if none. */
	public List<Edge> edgesAt(final int x, final int y) {
		final List<Edge> list = edges.get(key(x, y));
		return list == null ? Collections.emptyList() : list;
	}

	public boolean hasEdge(final int sx, final int sy, final int dx, final int dy) {
		for (Edge e : edgesAt(sx, sy)) {
			if (e.destX == dx && e.destY == dy) return true;
		}
		return false;
	}

	public int sourceTileCount() { return edges.size(); }
	public int xmlEdgeCount() { return xmlEdgeCount; }
	public int scenerySourceCount() { return scenerySourceCount; }
	public int sceneryEdgeCount() { return sceneryEdgeCount; }

	private static long key(final int x, final int y) {
		return ((long) x << 16) | (y & 0xFFFFL);
	}
}
