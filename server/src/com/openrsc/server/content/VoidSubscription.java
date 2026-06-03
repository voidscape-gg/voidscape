package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

public final class VoidSubscription {
	public static final String LEGACY_ACTIVE_CACHE_KEY = "void_subscription";
	public static final String EXPIRES_CACHE_KEY = "void_sub_expires";
	public static final String VENDOR_STOCK_CACHE_KEY = "sub_vendor_stock";
	public static final String VENDOR_TIER_CACHE_KEY = "sub_vendor_tier";
	public static final int CARD_ITEM_ID = ItemId.SUBSCRIPTION_CARD.id();
	public static final int VENDOR_BASE_PRICE = 10000;
	public static final int VENDOR_STOCK_PER_TIER = 20;
	public static final int PROFILE_CLIENT_VERSION = 10055;
	public static final double COMBAT_EXP_RATE = 10.0;
	public static final double SKILLING_EXP_RATE = 6.0;
	public static final long DURATION_MILLIS = 7L * 24L * 60L * 60L * 1000L;
	private static final long HOUR_MILLIS = 60L * 60L * 1000L;

	private VoidSubscription() {
	}

	public static boolean isActive(Player player) {
		if (player == null) {
			return false;
		}
		long expiresAt = getExpiresAt(player);
		if (expiresAt > System.currentTimeMillis()) {
			return true;
		}
		clearExpiredOrLegacy(player);
		return false;
	}

	public static long activate(Player player) {
		if (player == null) {
			return 0L;
		}
		clearLegacy(player);
		long now = System.currentTimeMillis();
		long base = Math.max(now, getExpiresAt(player));
		long expiresAt = Long.MAX_VALUE - base < DURATION_MILLIS ? Long.MAX_VALUE : base + DURATION_MILLIS;
		player.getCache().store(EXPIRES_CACHE_KEY, expiresAt);
		return expiresAt;
	}

	public static long getExpiresAt(Player player) {
		if (player == null || !player.getCache().hasKey(EXPIRES_CACHE_KEY)) {
			return 0L;
		}
		try {
			return player.getCache().getLong(EXPIRES_CACHE_KEY);
		} catch (RuntimeException ex) {
			player.getCache().remove(EXPIRES_CACHE_KEY);
			return 0L;
		}
	}

	public static String formatRemaining(long remainingMillis) {
		if (remainingMillis <= 0L) {
			return "expired";
		}
		long hours = Math.max(1L, (remainingMillis + HOUR_MILLIS - 1L) / HOUR_MILLIS);
		long days = hours / 24L;
		long extraHours = hours % 24L;
		if (days > 0L && extraHours > 0L) {
			return days + " day" + (days == 1L ? "" : "s") + " " + extraHours + " hour" + (extraHours == 1L ? "" : "s");
		}
		if (days > 0L) {
			return days + " day" + (days == 1L ? "" : "s");
		}
		return hours + " hour" + (hours == 1L ? "" : "s");
	}

	public static boolean isCombatSkill(int skill) {
		return skill == Skill.ATTACK.id()
			|| skill == Skill.DEFENSE.id()
			|| skill == Skill.STRENGTH.id()
			|| skill == Skill.HITS.id()
			|| skill == Skill.RANGED.id()
			|| skill == Skill.PRAYGOOD.id()
			|| skill == Skill.PRAYEVIL.id()
			|| skill == Skill.PRAYER.id()
			|| skill == Skill.GOODMAGIC.id()
			|| skill == Skill.EVILMAGIC.id()
			|| skill == Skill.MAGIC.id();
	}

	public static double applyRate(Player player, int skill, double currentRate) {
		if (!isActive(player)) {
			return currentRate;
		}
		double subscriptionRate = isCombatSkill(skill) ? COMBAT_EXP_RATE : SKILLING_EXP_RATE;
		return Math.max(currentRate, subscriptionRate);
	}

	public static int vendorPriceForTier(int tier) {
		long price = VENDOR_BASE_PRICE;
		for (int i = 0; i < tier; i++) {
			price *= 2L;
			if (price > Integer.MAX_VALUE) {
				return Integer.MAX_VALUE;
			}
		}
		return (int) price;
	}

	private static void clearExpiredOrLegacy(Player player) {
		clearLegacy(player);
		long expiresAt = getExpiresAt(player);
		if (expiresAt > 0L && expiresAt <= System.currentTimeMillis()) {
			player.getCache().remove(EXPIRES_CACHE_KEY);
		}
	}

	private static void clearLegacy(Player player) {
		if (player.getCache().hasKey(LEGACY_ACTIVE_CACHE_KEY)) {
			player.getCache().remove(LEGACY_ACTIVE_CACHE_KEY);
		}
	}
}
