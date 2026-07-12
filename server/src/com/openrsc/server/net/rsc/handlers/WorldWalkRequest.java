package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.content.VoidStarterIntro;
import com.openrsc.server.content.VoidScout;
import com.openrsc.server.event.rsc.impl.AutoWalkEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.WorldPathfinder;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.WorldWalkStruct;

import java.util.Collections;
import java.util.List;

/**
 * Inbound handler for {@link OpcodeIn#WORLD_WALK_REQUEST}. The client sends a
 * destination tile (absolute coords, including floor encoded as Y / 944); the
 * server pathfinds, drives walking via {@link AutoWalkEvent}, and echoes the
 * computed route back so the client can visualise it on the world map.
 *
 * <p>Failure modes (combat / busy / unreachable) come back via the same
 * outbound opcode with {@code ok = false} and a non-zero reason code; the
 * client decides how to surface that.
 */
public class WorldWalkRequest implements PayloadProcessor<WorldWalkStruct, OpcodeIn> {

	/** Reason codes carried on the outbound route packet. Slice 2 keeps this
	 * compact; slice 5's UI converts codes into messages. */
	private static final int REASON_OK = 0;
	private static final int REASON_NO_PATH = 1;
	private static final int REASON_CAP_EXHAUSTED = 2;
	private static final int REASON_INVALID_INPUT = 3;
	private static final int REASON_SAME_TILE = 4;
	private static final int REASON_CROSS_FLOOR = 5;
	private static final int REASON_BUSY = 6;
	private static final int REASON_COMBAT = 7;

	@Override
	public void process(final WorldWalkStruct payload, final Player player) throws Exception {
		if (player.isVoidScouting()) {
			if (player.inCombat()) {
				player.stopVoidScout("@mag@Your vision snaps back as danger finds your body.");
				ActionSender.sendWorldWalkRoute(player, false, REASON_COMBAT, Collections.emptyList());
				return;
			}
			final Point start = player.getViewLocation();
			final Point end = Point.location(payload.destX, payload.destY);
			if (Math.max(Math.abs(end.getX() - player.getLocation().getX()), Math.abs(end.getY() - player.getLocation().getY())) > VoidScout.MAX_DISTANCE) {
				ActionSender.sendWorldWalkRoute(player, false, REASON_NO_PATH, Collections.emptyList());
				player.message("The sparrow won't fly any farther from your body.");
				return;
			}

			final WorldPathfinder pf = new WorldPathfinder(player.getWorld(), player);
			final List<Point> path = pf.findPath(start, end, 8_192);

			if (path == null) {
				ActionSender.sendWorldWalkRoute(player, false, mapReason(pf.getLastReason()), Collections.emptyList());
				return;
			}
			if (path.isEmpty()) {
				ActionSender.sendWorldWalkRoute(player, false, REASON_SAME_TILE, Collections.emptyList());
				return;
			}

			player.queueVoidScoutPath(path);
			ActionSender.sendWorldWalkRoute(player, true, REASON_OK, path);
			return;
		}
		if (player.inCombat()) {
			ActionSender.sendWorldWalkRoute(player, false, REASON_COMBAT, Collections.emptyList());
			return;
		}
		if (player.isBusy() && player.getMenuHandler() == null) {
			ActionSender.sendWorldWalkRoute(player, false, REASON_BUSY, Collections.emptyList());
			return;
		}

		final Point start = player.getLocation();
		final Point end = Point.location(payload.destX, payload.destY);

		final WorldPathfinder pf = new WorldPathfinder(player.getWorld(), player);
		final List<Point> path = pf.findPath(start, end);

		if (path == null) {
			ActionSender.sendWorldWalkRoute(player, false, mapReason(pf.getLastReason()), Collections.emptyList());
			return;
		}
		if (path.isEmpty()) {
			// Already standing on the requested tile.
			ActionSender.sendWorldWalkRoute(player, false, REASON_SAME_TILE, Collections.emptyList());
			return;
		}
		if (VoidStarterIntro.blocksUnseenIntroPath(player, path)) {
			player.message("@mag@The Void Council blocks the path north. Speak to one of them.");
			ActionSender.sendWorldWalkRoute(player, false, REASON_BUSY, Collections.emptyList());
			return;
		}
		player.cancelAutoWalk();
		player.resetAll();
		player.resetPath();
		final AutoWalkEvent event = new AutoWalkEvent(player.getWorld(), player, path);
		player.setAutoWalkEvent(event);
		player.getWorld().getServer().getGameEventHandler().add(event);

		ActionSender.sendWorldWalkRoute(player, true, REASON_OK, path);
	}

	private static int mapReason(final WorldPathfinder.Reason reason) {
		switch (reason) {
			case OK: return REASON_OK;
			case NO_PATH: return REASON_NO_PATH;
			case CAP_EXHAUSTED: return REASON_CAP_EXHAUSTED;
			case INVALID_INPUT: return REASON_INVALID_INPUT;
			case SAME_TILE: return REASON_SAME_TILE;
			case CROSS_FLOOR: return REASON_CROSS_FLOOR;
			default: return REASON_NO_PATH;
		}
	}
}
