package com.openrsc.server.plugins.authentic.commands;

import com.openrsc.server.event.rsc.impl.AutoWalkEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.WorldPathfinder;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.CommandTrigger;

import java.util.List;

import static com.openrsc.server.plugins.Functions.config;

/**
 * Admin/dev command for slice 1 of the world-map auto-walker. Submits an
 * arbitrary destination to {@link WorldPathfinder} and drives the player
 * along the result via {@link AutoWalkEvent}. The eventual user-facing
 * surface is a click on the world map (slice 5); this lets us validate
 * pathfinding and the chunked walking driver without UI.
 */
public final class Pathto implements CommandTrigger {

	private static String messagePrefix = null;
	private static String badSyntaxPrefix = null;

	@Override
	public boolean blockCommand(final Player player, final String command, final String[] args) {
		return player.isAdmin()
			&& (command.equalsIgnoreCase("pathto")
			|| command.equalsIgnoreCase("pathwalk"));
	}

	@Override
	public void onCommand(final Player player, final String command, final String[] args) {
		if (messagePrefix == null) messagePrefix = config().MESSAGE_PREFIX;
		if (badSyntaxPrefix == null) badSyntaxPrefix = config().BAD_SYNTAX_PREFIX;

		if (args.length < 2) {
			player.message(badSyntaxPrefix + command.toUpperCase() + " [x] [y] (floor)");
			return;
		}

		final int x;
		final int y;
		try {
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
		} catch (NumberFormatException nfe) {
			player.message(badSyntaxPrefix + "x and y must be integers");
			return;
		}

		int floor = 0;
		if (args.length >= 3) {
			try {
				floor = Integer.parseInt(args[2]);
			} catch (NumberFormatException nfe) {
				player.message(badSyntaxPrefix + "floor must be an integer 0-3");
				return;
			}
		}
		if (floor < 0 || floor > 3) {
			player.message(badSyntaxPrefix + "floor must be 0-3");
			return;
		}

		final Point dest = Point.location(x, y + floor * 944);

		final WorldPathfinder pf = new WorldPathfinder(player.getWorld(), player);
		final long started = System.nanoTime();
		final List<Point> path = pf.findPath(player.getLocation(), dest);
		final long elapsedMs = (System.nanoTime() - started) / 1_000_000L;

		if (path == null) {
			player.message(messagePrefix + "Pathto failed (" + pf.getLastReason()
				+ "), nodes=" + pf.getNodesExplored() + ", " + elapsedMs + "ms");
			return;
		}
		if (path.isEmpty()) {
			player.message(messagePrefix + "Already there.");
			return;
		}

		player.cancelAutoWalk();
		final AutoWalkEvent event = new AutoWalkEvent(player.getWorld(), player, path);
		player.setAutoWalkEvent(event);
		player.getWorld().getServer().getGameEventHandler().add(event);

		player.message(messagePrefix + "Pathto " + dest.getX() + "," + dest.getY()
			+ " — " + path.size() + " tiles, " + pf.getNodesExplored() + " nodes, " + elapsedMs + "ms");
	}
}
