package com.openrsc.server.content.voiddungeon;

import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

/**
 * Short, movement-intent-gated protection from ordinary Void Dungeon NPC auto-aggro.
 * State is session-only and deliberately separate from PvP retreat immunity.
 */
public final class VoidDungeonTraversalGrace {
	public static final int GRACE_TICKS = 5;

	private static final String STATE_ATTRIBUTE = "void_dungeon_traversal_grace";

	private VoidDungeonTraversalGrace() {
	}

	public static void armAfterKill(final Player player, final Npc npc) {
		if (player == null || npc == null
			|| !inDungeon(player.getLocation()) || !inDungeon(npc.getLocation())) {
			return;
		}

		clear(player);
		player.setAttribute(STATE_ATTRIBUTE, new State(currentTick(player)));
	}

	public static boolean activateForAcceptedWalk(final Player player) {
		if (player == null || !inDungeon(player.getLocation())) {
			clear(player);
			return false;
		}

		final State state = player.getAttribute(STATE_ATTRIBUTE, null);
		if (state == null || !state.activate(currentTick(player))) {
			clear(player);
			return false;
		}
		return true;
	}

	public static boolean isActive(final Player player) {
		if (player == null || !inDungeon(player.getLocation())) {
			clear(player);
			return false;
		}

		final State state = player.getAttribute(STATE_ATTRIBUTE, null);
		if (state == null || !state.isActive(currentTick(player))) {
			return false;
		}
		return true;
	}

	public static boolean blocksAutoAggro(final Player player) {
		if (player == null || !inDungeon(player.getLocation())) {
			clear(player);
			return false;
		}

		final State state = player.getAttribute(STATE_ATTRIBUTE, null);
		if (state == null) {
			return false;
		}
		final long now = currentTick(player);
		final boolean blocked = state.blocksAutoAggro(now);
		if (!blocked && state.isExpired(now)) {
			clear(player);
		}
		return blocked;
	}

	public static void clear(final Player player) {
		if (player == null) {
			return;
		}
		player.removeAttribute(STATE_ATTRIBUTE);
	}

	public static void onLocationChanged(final Player player, final Point previous,
			final Point current) {
		if (inDungeon(previous) && !inDungeon(current)) {
			clear(player);
		}
	}

	private static boolean inDungeon(final Point point) {
		return point != null && point.inVoidDungeonUnderground();
	}

	private static long currentTick(final Player player) {
		return player.getWorld().getServer().getCurrentTick();
	}

	static final class State {
		private long armedUntilTick;
		private long decisionUntilTick;
		private long activeUntilTick;

		State(final long killTick) {
			armedUntilTick = killTick + GRACE_TICKS;
			// NPC AI runs before player packet queues. Hold one extra NPC scan so a
			// ground-click sent after the kill can activate before another mob engages.
			decisionUntilTick = killTick + 2;
		}

		boolean activate(final long currentTick) {
			if (currentTick >= armedUntilTick) {
				return false;
			}
			armedUntilTick = 0;
			decisionUntilTick = 0;
			activeUntilTick = currentTick + GRACE_TICKS;
			return true;
		}

		boolean blocksAutoAggro(final long currentTick) {
			return currentTick < decisionUntilTick || isActive(currentTick);
		}

		boolean isActive(final long currentTick) {
			return currentTick < activeUntilTick;
		}

		boolean isExpired(final long currentTick) {
			return currentTick >= armedUntilTick
				&& currentTick >= decisionUntilTick
				&& currentTick >= activeUntilTick;
		}
	}
}
