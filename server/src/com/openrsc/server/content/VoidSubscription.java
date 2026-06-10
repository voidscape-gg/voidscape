package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

public final class VoidSubscription {
	public static final String ACCOUNT_ID_CACHE_KEY = "web_account_id";
	public static final String ACCOUNT_SUBSCRIPTION_CACHE_PREFIX = "acct_sub:";
	public static final String STARTER_CARD_CACHE_PREFIX = "starter_card:";
	public static final int STARTER_CARD_AVAILABLE = 1;
	public static final int STARTER_CARD_CLAIMED = 2;
	public static final String SIGNUP_CODE_CACHE_PREFIX = "signup_code:";
	public static final int SIGNUP_CODE_AVAILABLE = 1;
	public static final int SIGNUP_CODE_REDEEMED = 2;
	// player_cache.key is varchar(32); 32 - "signup_code:".length() = 20
	private static final int SIGNUP_CODE_MAX_LENGTH = 20;
	public static final int CARD_ITEM_ID = ItemId.SUBSCRIPTION_CARD.id();
	public static final int PROFILE_CLIENT_VERSION = 10055;
	public static final double COMBAT_EXP_RATE = 10.0;
	public static final double SKILLING_EXP_RATE = 6.0;
	public static final long DURATION_MILLIS = 7L * 24L * 60L * 60L * 1000L;
	private static final long HOUR_MILLIS = 60L * 60L * 1000L;
	private static final long ACCOUNT_SUBSCRIPTION_REFRESH_MILLIS = 30L * 1000L;
	private static final String ACCOUNT_SUBSCRIPTION_ATTRIBUTE = "voidscape_account_subscription_expires";
	private static final String ACCOUNT_SUBSCRIPTION_REFRESH_ATTRIBUTE = "voidscape_account_subscription_refreshed";

	private VoidSubscription() {
	}

	public static boolean isActive(Player player) {
		if (player == null) {
			return false;
		}
		long expiresAt = getExpiresAt(player);
		return expiresAt > System.currentTimeMillis();
	}

	public static long activate(Player player) {
		if (player == null) {
			return 0L;
		}
		int accountId = getAccountId(player);
		if (accountId <= 0) {
			return 0L;
		}
		long now = System.currentTimeMillis();
		long base = Math.max(now, getAccountExpiresAt(player, true));
		long expiresAt = Long.MAX_VALUE - base < DURATION_MILLIS ? Long.MAX_VALUE : base + DURATION_MILLIS;
		try {
			player.getWorld().getServer().getDatabase()
				.querySaveGlobalCacheLong(accountSubscriptionCacheKey(accountId), expiresAt);
			cacheAccountExpiresAt(player, expiresAt);
		} catch (Exception ex) {
			return 0L;
		}
		return expiresAt;
	}

	public static long getExpiresAt(Player player) {
		if (player == null) {
			return 0L;
		}
		return getAccountExpiresAt(player, false);
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

	public static boolean hasLinkedAccount(Player player) {
		return getAccountId(player) > 0;
	}

	public static int getAccountId(Player player) {
		if (player == null || !player.getCache().hasKey(ACCOUNT_ID_CACHE_KEY)) {
			return 0;
		}
		try {
			int accountId = player.getCache().getInt(ACCOUNT_ID_CACHE_KEY);
			return accountId > 0 ? accountId : 0;
		} catch (RuntimeException ex) {
			player.getCache().remove(ACCOUNT_ID_CACHE_KEY);
			return 0;
		}
	}

	public static String normalizeSignupCode(String raw) {
		if (raw == null) {
			return "";
		}
		String normalized = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
		if (normalized.isEmpty() || normalized.length() > SIGNUP_CODE_MAX_LENGTH) {
			return "";
		}
		return normalized;
	}

	public static String signupCodeCacheKey(String normalizedCode) {
		if (normalizedCode == null || normalizedCode.isEmpty()
			|| normalizedCode.length() > SIGNUP_CODE_MAX_LENGTH) {
			return "";
		}
		return SIGNUP_CODE_CACHE_PREFIX + normalizedCode;
	}

	public static String starterCardCacheKey(Player player) {
		return starterCardCacheKey(getAccountId(player));
	}

	public static String starterCardCacheKey(int accountId) {
		if (accountId <= 0) {
			return "";
		}
		return STARTER_CARD_CACHE_PREFIX + accountId;
	}

	public static void refreshAccountSubscription(Player player) {
		getAccountExpiresAt(player, true);
	}

	private static String accountSubscriptionCacheKey(int accountId) {
		if (accountId <= 0) {
			return "";
		}
		return ACCOUNT_SUBSCRIPTION_CACHE_PREFIX + accountId;
	}

	private static long getAccountExpiresAt(Player player, boolean force) {
		int accountId = getAccountId(player);
		if (accountId <= 0) {
			return 0L;
		}
		long now = System.currentTimeMillis();
		Long cached = player.getAttribute(ACCOUNT_SUBSCRIPTION_ATTRIBUTE, null);
		Long refreshedAt = player.getAttribute(ACCOUNT_SUBSCRIPTION_REFRESH_ATTRIBUTE, 0L);
		if (!force && cached != null && now - refreshedAt < ACCOUNT_SUBSCRIPTION_REFRESH_MILLIS) {
			return cached;
		}

		try {
			Long expiresAt = player.getWorld().getServer().getDatabase()
				.queryLoadGlobalCacheLong(accountSubscriptionCacheKey(accountId));
			long value = expiresAt == null ? 0L : expiresAt;
			cacheAccountExpiresAt(player, value);
			return value;
		} catch (Exception ex) {
			return cached == null ? 0L : cached;
		}
	}

	private static void cacheAccountExpiresAt(Player player, long expiresAt) {
		player.setAttribute(ACCOUNT_SUBSCRIPTION_ATTRIBUTE, expiresAt);
		player.setAttribute(ACCOUNT_SUBSCRIPTION_REFRESH_ATTRIBUTE, System.currentTimeMillis());
	}
}
