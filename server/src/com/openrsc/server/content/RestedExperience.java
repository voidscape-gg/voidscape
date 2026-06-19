package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

public final class RestedExperience {
	public static final String POOL_CACHE_KEY = "rested_xp_pool";
	public static final String LAST_SEEN_CACHE_KEY = "rested_xp_last_seen";
	public static final long MAX_RESTED_SECONDS = 45L * 60L;
	public static final int BONUS_PERCENT = 50;
	private static final long MIN_OFFLINE_SECONDS = 30L * 60L;
	private static final String LAST_DRAIN_ATTRIBUTE = "rested_xp_last_drain";
	private static final String NOTICE_ATTRIBUTE = "rested_xp_notice";
	private static final String EMPTY_NOTICE_ATTRIBUTE = "rested_xp_empty_notice";

	private RestedExperience() {
	}

	public static void applyOfflineGain(Player player) {
		if (player == null) {
			return;
		}

		long now = nowSeconds();
		long lastSeen = getCacheLong(player, LAST_SEEN_CACHE_KEY, now);
		long offlineSeconds = Math.max(0L, now - lastSeen);
		long oldPool = getPool(player);
		long newPool = oldPool;

		if (offlineSeconds >= MIN_OFFLINE_SECONDS) {
			long gained = offlineSeconds;
			newPool = clampPool(oldPool + gained);
			setPool(player, newPool);
		}

		player.getCache().store(LAST_SEEN_CACHE_KEY, now);
		player.setAttribute(LAST_DRAIN_ATTRIBUTE, now);
		player.removeAttribute(NOTICE_ATTRIBUTE);
		player.removeAttribute(EMPTY_NOTICE_ATTRIBUTE);

		if (newPool > oldPool) {
			player.message("@gre@You feel rested. @whi@Rested XP: @gre@" + formatDuration(newPool)
				+ "@whi@ of 1.5x XP available.");
		} else if (newPool > 0L) {
			player.message("@whi@Rested XP: @gre@" + formatDuration(newPool) + "@whi@ of 1.5x XP available.");
		}
	}

	public static void recordLogout(Player player) {
		if (player == null) {
			return;
		}
		drainElapsedSessionTime(player);
		player.getCache().store(LAST_SEEN_CACHE_KEY, nowSeconds());
	}

	public static int applyBonus(Player player, int skill, int awardedXp, boolean fromQuest) {
		if (player == null || awardedXp <= 0 || fromQuest || player.isOneXp()) {
			return awardedXp;
		}

		drainElapsedSessionTime(player);
		long pool = getPool(player);
		if (pool <= 0L) {
			return awardedXp;
		}

		long bonus = Math.max(1L, Math.round((double) awardedXp * BONUS_PERCENT / 100.0D));
		long total = (long) awardedXp + bonus;
		if (total > Integer.MAX_VALUE) {
			bonus = Integer.MAX_VALUE - (long) awardedXp;
			total = Integer.MAX_VALUE;
		}
		if (bonus <= 0L) {
			return awardedXp;
		}

		if (!player.getAttribute(NOTICE_ATTRIBUTE, false)) {
			player.message("@gre@Rested XP grants +" + format(bonus) + " bonus xp.");
			player.setAttribute(NOTICE_ATTRIBUTE, true);
		}
		return (int) total;
	}

	public static long getPool(Player player) {
		return clampPool(getCacheLong(player, POOL_CACHE_KEY, 0L));
	}

	public static void setPool(Player player, long pool) {
		if (player == null) {
			return;
		}
		player.getCache().store(POOL_CACHE_KEY, clampPool(pool));
	}

	public static String status(Player player) {
		drainElapsedSessionTime(player);
		long pool = getPool(player);
		return "@whi@Rested XP: @gre@" + formatDuration(pool) + "@whi@ / @gre@"
			+ formatDuration(MAX_RESTED_SECONDS) + "@whi@ at 1.5x XP. Earns one rested minute per offline minute.";
	}

	private static void drainElapsedSessionTime(Player player) {
		if (player == null) {
			return;
		}

		long pool = getPool(player);
		long now = nowSeconds();
		long lastDrain = player.getAttribute(LAST_DRAIN_ATTRIBUTE, now);
		player.setAttribute(LAST_DRAIN_ATTRIBUTE, now);
		if (pool <= 0L) {
			return;
		}

		long elapsed = Math.max(0L, now - lastDrain);
		if (elapsed <= 0L) {
			return;
		}

		long remaining = clampPool(pool - elapsed);
		setPool(player, remaining);
		if (remaining == 0L && !player.getAttribute(EMPTY_NOTICE_ATTRIBUTE, false)) {
			player.message("@yel@Your rested XP is used up.");
			player.setAttribute(EMPTY_NOTICE_ATTRIBUTE, true);
		}
	}

	private static long clampPool(long pool) {
		if (pool < 0L) {
			return 0L;
		}
		return Math.min(pool, MAX_RESTED_SECONDS);
	}

	private static long getCacheLong(Player player, String key, long fallback) {
		if (player == null || !player.getCache().hasKey(key)) {
			return fallback;
		}
		Object value = player.getCache().getCacheMap().get(key);
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		if (value instanceof String) {
			try {
				return Long.parseLong((String) value);
			} catch (NumberFormatException ex) {
				player.getCache().remove(key);
				return fallback;
			}
		}
		player.getCache().remove(key);
		return fallback;
	}

	private static long nowSeconds() {
		return System.currentTimeMillis() / 1000L;
	}

	private static String format(long value) {
		return String.format("%,d", value);
	}

	private static String formatDuration(long seconds) {
		long minutes = Math.max(0L, seconds) / 60L;
		long remainingSeconds = Math.max(0L, seconds) % 60L;
		if (minutes <= 0L) {
			return remainingSeconds + "s";
		}
		if (remainingSeconds == 0L) {
			return minutes + "m";
		}
		return minutes + "m " + remainingSeconds + "s";
	}
}
