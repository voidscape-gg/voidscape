package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.player.Player;

public final class VoidScout {
	public static final int ITEM_ID = ItemId.VOID_SPARROW.id();
	public static final int MAX_DISTANCE = 96;
	public static final long DURATION_MILLIS = 30_000L;
	public static final long COOLDOWN_MILLIS = 0L;

	private static final String COOLDOWN_KEY = "void_scout_cooldown_until";

	private VoidScout() {
	}

	public static void release(final Player player) {
		final long now = System.currentTimeMillis();
		final long cooldownUntil = player.getCache().hasKey(COOLDOWN_KEY)
			? player.getCache().getLong(COOLDOWN_KEY)
			: 0L;

		if (cooldownUntil > now) {
			player.message("Your void sparrow needs " + formatSeconds((cooldownUntil - now + 999L) / 1000L) + " to return.");
			return;
		}
		if (!player.canUseVoidScout()) {
			return;
		}

		if (COOLDOWN_MILLIS > 0L) {
			player.getCache().store(COOLDOWN_KEY, now + COOLDOWN_MILLIS);
		}
		player.startVoidScout(DURATION_MILLIS, MAX_DISTANCE);
		if (COOLDOWN_MILLIS > 0L) {
			player.save(false, true);
		}
	}

	private static String formatSeconds(final long seconds) {
		if (seconds <= 1L) return "1 second";
		if (seconds < 60L) return seconds + " seconds";
		final long minutes = (seconds + 59L) / 60L;
		return minutes == 1L ? "1 minute" : minutes + " minutes";
	}
}
