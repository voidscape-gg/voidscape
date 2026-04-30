package com.openrsc.server.model;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loader for RSCRevolution's waypoint graph ({@code waypoints.rev}).
 *
 * Voidscape treats this graph as a high-level route guide only. Every leg is
 * still stitched by {@link WorldPathfinder}'s collision-checked A*, so stale
 * waypoint edges or ignored gate metadata cannot authorize walking through
 * walls.
 */
public final class WaypointGraph {
	private static final Logger LOGGER = LogManager.getLogger(WaypointGraph.class);
	private static final int MAX_SNAP_DISTANCE = 96; // Manhattan distance from tile to nearest graph node.

	private static volatile WaypointGraph defaultGraph;
	private static volatile boolean defaultLoadAttempted;

	private final Node[] nodes;
	private final List<Edge>[] adjacency;

	private WaypointGraph(final Node[] nodes, final List<Edge>[] adjacency) {
		this.nodes = nodes;
		this.adjacency = adjacency;
	}

	public static WaypointGraph getDefault() {
		if (defaultLoadAttempted) return defaultGraph;
		synchronized (WaypointGraph.class) {
			if (defaultLoadAttempted) return defaultGraph;
			defaultLoadAttempted = true;
			for (File candidate : defaultCandidates()) {
				if (!candidate.isFile()) continue;
				try {
					defaultGraph = load(candidate);
					LOGGER.info("Loaded waypoint graph from {}: {} nodes, {} directed edges",
						candidate.getPath(), defaultGraph.nodes.length, defaultGraph.directedEdgeCount());
					return defaultGraph;
				} catch (Exception e) {
					LOGGER.warn("Failed to load waypoint graph from {}", candidate.getPath(), e);
				}
			}
			LOGGER.info("No waypoint graph found; world-map autowalk will use grid A* only");
			return null;
		}
	}

	private static List<File> defaultCandidates() {
		final String home = System.getProperty("user.home", "");
		final ArrayList<File> files = new ArrayList<>();
		files.add(new File("conf/server/data/waypoints.rev"));
		files.add(new File(home, "RSCRevolution2/waypoints.rev"));
		return files;
	}

	@SuppressWarnings("unchecked")
	public static WaypointGraph load(final File file) throws IOException {
		try (ZipFile zip = new ZipFile(file)) {
			final ZipEntry nodesEntry = zip.getEntry("nodes.dat");
			final ZipEntry edgesEntry = zip.getEntry("edges.dat");
			if (nodesEntry == null || edgesEntry == null) {
				throw new IOException("waypoints.rev missing nodes.dat or edges.dat");
			}

			final Node[] nodes;
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(nodesEntry)))) {
				final int count = in.readInt();
				nodes = new Node[count];
				for (int id = 0; id < count; id++) {
					final int x = in.readUnsignedShort();
					final int y = in.readUnsignedShort();
					final int attr = in.readUnsignedByte();
					final boolean hasName = in.readUnsignedByte() != 0;
					final String name = hasName ? in.readUTF() : null;
					nodes[id] = new Node(id, x, y, attr, name);
				}
			}

			final List<Edge>[] adjacency = new List[nodes.length];
			for (int i = 0; i < adjacency.length; i++) {
				adjacency[i] = new ArrayList<>();
			}
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(zip.getInputStream(edgesEntry)))) {
				final int count = in.readInt();
				for (int i = 0; i < count; i++) {
					final int from = in.readUnsignedShort();
					final int to = in.readUnsignedShort();
					final int cost = in.readUnsignedShort();
					if (from >= nodes.length || to >= nodes.length) continue;
					adjacency[from].add(new Edge(to, cost));
					adjacency[to].add(new Edge(from, cost));
				}
			}
			for (int i = 0; i < adjacency.length; i++) {
				adjacency[i] = Collections.unmodifiableList(adjacency[i]);
			}
			return new WaypointGraph(nodes, adjacency);
		}
	}

	public List<Node> route(final int sx, final int sy, final int tx, final int ty, final NodeFilter filter) {
		final int floor = sy / 944;
		if (ty / 944 != floor) return null;
		final Node start = nearestNode(sx, sy, floor, filter);
		final Node goal = nearestNode(tx, ty, floor, filter);
		if (start == null || goal == null) return null;
		return shortestPath(start.id, goal.id, floor, filter);
	}

	private Node nearestNode(final int x, final int y, final int floor, final NodeFilter filter) {
		Node best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Node node : nodes) {
			if (node.y / 944 != floor) continue;
			if (filter != null && !filter.allow(node.x, node.y)) continue;
			final int distance = Math.abs(node.x - x) + Math.abs(node.y - y);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = node;
			}
		}
		return bestDistance <= MAX_SNAP_DISTANCE ? best : null;
	}

	private List<Node> shortestPath(final int startId, final int goalId, final int floor, final NodeFilter filter) {
		if (startId == goalId) return Collections.singletonList(nodes[startId]);

		final double[] dist = new double[nodes.length];
		final int[] prev = new int[nodes.length];
		Arrays.fill(dist, Double.POSITIVE_INFINITY);
		Arrays.fill(prev, -1);
		dist[startId] = 0.0;

		final PriorityQueue<QueueNode> open = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));
		open.add(new QueueNode(startId, 0.0));
		while (!open.isEmpty()) {
			final QueueNode current = open.poll();
			if (current.id == goalId) break;
			if (current.distance > dist[current.id]) continue;

			for (Edge edge : adjacency[current.id]) {
				final Node target = nodes[edge.to];
				if (target.y / 944 != floor) continue;
				if (filter != null && !filter.allow(target.x, target.y)) continue;
				final double nextDistance = current.distance + edge.cost;
				if (nextDistance >= dist[edge.to]) continue;
				dist[edge.to] = nextDistance;
				prev[edge.to] = current.id;
				open.add(new QueueNode(edge.to, nextDistance));
			}
		}

		if (Double.isInfinite(dist[goalId])) return null;
		final ArrayList<Node> route = new ArrayList<>();
		for (int current = goalId; current != -1; current = prev[current]) {
			route.add(nodes[current]);
		}
		Collections.reverse(route);
		return route;
	}

	private int directedEdgeCount() {
		int total = 0;
		for (List<Edge> edges : adjacency) {
			total += edges.size();
		}
		return total;
	}

	public interface NodeFilter {
		boolean allow(int x, int y);
	}

	public static final class Node {
		public final int id;
		public final int x;
		public final int y;
		public final int attr;
		public final String name;

		private Node(final int id, final int x, final int y, final int attr, final String name) {
			this.id = id;
			this.x = x;
			this.y = y;
			this.attr = attr;
			this.name = name;
		}
	}

	private static final class Edge {
		private final int to;
		private final int cost;

		private Edge(final int to, final int cost) {
			this.to = to;
			this.cost = cost;
		}
	}

	private static final class QueueNode {
		private final int id;
		private final double distance;

		private QueueNode(final int id, final double distance) {
			this.id = id;
			this.distance = distance;
		}
	}
}
