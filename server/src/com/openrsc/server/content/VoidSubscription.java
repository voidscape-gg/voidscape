package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

public final class VoidSubscription {
	public static final String ACCOUNT_ID_CACHE_KEY = "web_account_id";
	public static final String ACCOUNT_SUBSCRIPTION_CACHE_PREFIX = "acct_sub:";
	public static final String PLAYER_SUBSCRIPTION_CACHE_PREFIX = "char_sub:";
	/** Durable per-character founder issuance state. */
	public static final String STARTER_CARD_CACHE_PREFIX = "starter:";
	/** Migration input only. Runtime claim paths must not consult this account-wide key. */
	public static final String LEGACY_STARTER_CARD_CACHE_PREFIX = "starter_card:";
	public static final int STARTER_CARD_AVAILABLE = 1;
	public static final int STARTER_CARD_CLAIMED = 2;
	public static final String LAUNCH_CARD_CACHE_KEY = "launch_24h_card";
	public static final int LAUNCH_CARD_AVAILABLE = 1;
	public static final int LAUNCH_CARD_CLAIMED = 2;
	public static final String SIGNUP_CODE_CACHE_PREFIX = "signup_code:";
	public static final String BASE_CODE_TAG_CACHE_PREFIX = "base_tag:";
	public static final String BASE_CODE_ACCOUNT_CACHE_PREFIX = "base_acct:";
	public static final int BASE_CODE_UNASSIGNED = 0;
	public static final int SIGNUP_CODE_AVAILABLE = 1;
	public static final int SIGNUP_CODE_REDEEMED = 2;
	// player_cache.key is varchar(32); 32 - "signup_code:".length() = 20
	private static final int SIGNUP_CODE_MAX_LENGTH = 20;
	public static final int CARD_ITEM_ID = ItemId.SUBSCRIPTION_CARD.id();
	public static final int PROFILE_CLIENT_VERSION = 10055;
	public static final int CHAT_NAME_CLIENT_VERSION = 10124;
	public static final int CHAT_NAME_ICON_FLAG = 0x40000000;
	public static final double COMBAT_EXP_BONUS = 1.0D;
	public static final double SKILLING_EXP_BONUS = 1.0D;
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

	public static int withChatNameFlag(Player viewer, Player sender, int icon) {
		if (viewer == null || sender == null || !viewer.isUsingCustomClient()
			|| viewer.getClientVersion() < CHAT_NAME_CLIENT_VERSION) {
			return icon;
		}
		return isActive(sender) ? icon | CHAT_NAME_ICON_FLAG : icon;
	}

	public static long activate(Player player) {
		if (player == null) {
			return 0L;
		}
		String cacheKey = subscriptionCacheKey(player);
		if (cacheKey.isEmpty()) {
			return 0L;
		}
		long now = System.currentTimeMillis();
		long base = Math.max(now, getSubscriptionExpiresAt(player, true));
		long expiresAt = Long.MAX_VALUE - base < DURATION_MILLIS ? Long.MAX_VALUE : base + DURATION_MILLIS;
		try {
			player.getWorld().getServer().getDatabase()
				.querySaveGlobalCacheLong(cacheKey, expiresAt);
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
		return getSubscriptionExpiresAt(player, false);
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
			|| skill == Skill.GOODMAGIC.id()
			|| skill == Skill.EVILMAGIC.id()
			|| skill == Skill.MAGIC.id();
	}

	public static double applyRate(Player player, int skill, double currentRate) {
		if (!isActive(player)) {
			return currentRate;
		}
		return currentRate + (isCombatSkill(skill) ? COMBAT_EXP_BONUS : SKILLING_EXP_BONUS);
	}

	public static double effectiveCombatRate(Player player, double baseRate) {
		return applyRate(player, Skill.ATTACK.id(), baseRate);
	}

	public static double effectiveSkillingRate(Player player, double baseRate) {
		return applyRate(player, Skill.WOODCUTTING.id(), baseRate);
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

	public static String baseCodeTagCacheKey(String normalizedCode) {
		if (normalizedCode == null || normalizedCode.isEmpty()
			|| normalizedCode.length() > SIGNUP_CODE_MAX_LENGTH) {
			return "";
		}
		return BASE_CODE_TAG_CACHE_PREFIX + normalizedCode;
	}

	public static String baseCodeAccountCacheKey(String normalizedCode) {
		if (normalizedCode == null || normalizedCode.isEmpty()
			|| normalizedCode.length() > SIGNUP_CODE_MAX_LENGTH) {
			return "";
		}
		return BASE_CODE_ACCOUNT_CACHE_PREFIX + normalizedCode;
	}

	public static String starterCardCacheKey(Player player) {
		return starterCardCacheKey(getAccountId(player), player == null ? 0 : player.getDatabaseID());
	}

	public static String starterCardCacheKey(int accountId, int playerId) {
		if (accountId <= 0 || playerId <= 0) {
			return "";
		}
		return STARTER_CARD_CACHE_PREFIX + accountId + ":" + playerId;
	}

	public static String legacyStarterCardCacheKey(int accountId) {
		if (accountId <= 0) {
			return "";
		}
		return LEGACY_STARTER_CARD_CACHE_PREFIX + accountId;
	}

	/**
	 * A base founder code may be unassigned, or pre-bound to exactly one database player ID.
	 * A missing tag is a referral code and is deliberately not accepted by this classifier.
	 */
	public static boolean baseCodeMayBeRedeemedBy(Integer assignedPlayerId, int claimantPlayerId) {
		return assignedPlayerId != null
			&& claimantPlayerId > 0
			&& (assignedPlayerId == BASE_CODE_UNASSIGNED || assignedPlayerId == claimantPlayerId);
	}

	public static boolean linkedBaseCodeRouteMatches(Integer frozenAccountId,
													Integer assignedPlayerId, int currentAccountId,
													int claimantPlayerId, boolean hasCompositeLedger) {
		return frozenAccountId != null && frozenAccountId > 0
			&& assignedPlayerId != null && assignedPlayerId > 0
			&& frozenAccountId == currentAccountId
			&& assignedPlayerId == claimantPlayerId
			&& hasCompositeLedger;
	}

	public static boolean isCardClaimState(Integer state) {
		return state == null || state == STARTER_CARD_AVAILABLE || state == STARTER_CARD_CLAIMED;
	}

	/**
	 * Resolve the native-launch and per-character founder routes as one issuance. A claimed
	 * route suppresses an available sibling route; two available routes grant exactly one card.
	 */
	public static CardClaimPlan planReservedCardClaim(Integer starterState, Integer launchState) {
		if (!isCardClaimState(starterState) || !isCardClaimState(launchState)) {
			return CardClaimPlan.NONE;
		}

		if (starterState != null && starterState == STARTER_CARD_AVAILABLE) {
			if (launchState != null && launchState == LAUNCH_CARD_CLAIMED) {
				return new CardClaimPlan(false, true, false);
			}
			return new CardClaimPlan(true, true,
				launchState != null && launchState == LAUNCH_CARD_AVAILABLE);
		}
		if (starterState != null && starterState == STARTER_CARD_CLAIMED) {
			return new CardClaimPlan(false, false,
				launchState != null && launchState == LAUNCH_CARD_AVAILABLE);
		}
		if (launchState != null && launchState == LAUNCH_CARD_AVAILABLE) {
			return new CardClaimPlan(true, false, true);
		}
		return CardClaimPlan.NONE;
	}

	/**
	 * A base founder code itself is an entitlement only for code-only/unlinked founders.
	 * Linked founders must already have an exact reset-created composite row; this method
	 * never creates one. Existing founder or native issuance retires the code without a
	 * second card.
	 */
	public static CardClaimPlan planFounderCodeClaim(boolean hasCompositeLedger, Integer starterState,
													Integer launchState) {
		if ((hasCompositeLedger && (starterState == null || !isCardClaimState(starterState)))
			|| !isCardClaimState(launchState)) {
			return CardClaimPlan.NONE;
		}
		boolean alreadyIssued = (hasCompositeLedger
			&& starterState == STARTER_CARD_CLAIMED)
			|| (launchState != null && launchState == LAUNCH_CARD_CLAIMED);
		return new CardClaimPlan(!alreadyIssued,
			hasCompositeLedger && starterState == STARTER_CARD_AVAILABLE,
			launchState != null && launchState == LAUNCH_CARD_AVAILABLE);
	}

	public static final class CardClaimPlan {
		private static final CardClaimPlan NONE = new CardClaimPlan(false, false, false);
		private final boolean grantCard;
		private final boolean claimStarter;
		private final boolean claimLaunch;

		private CardClaimPlan(boolean grantCard, boolean claimStarter, boolean claimLaunch) {
			this.grantCard = grantCard;
			this.claimStarter = claimStarter;
			this.claimLaunch = claimLaunch;
		}

		public boolean grantsCard() {
			return grantCard;
		}

		public boolean claimsStarter() {
			return claimStarter;
		}

		public boolean claimsLaunch() {
			return claimLaunch;
		}

		public boolean hasWork() {
			return grantCard || claimStarter || claimLaunch;
		}
	}

	public static void refreshAccountSubscription(Player player) {
		getSubscriptionExpiresAt(player, true);
	}

	private static String accountSubscriptionCacheKey(int accountId) {
		if (accountId <= 0) {
			return "";
		}
		return ACCOUNT_SUBSCRIPTION_CACHE_PREFIX + accountId;
	}

	private static String playerSubscriptionCacheKey(int playerId) {
		if (playerId <= 0) {
			return "";
		}
		return PLAYER_SUBSCRIPTION_CACHE_PREFIX + playerId;
	}

	private static String subscriptionCacheKey(Player player) {
		int accountId = getAccountId(player);
		if (accountId > 0) {
			return accountSubscriptionCacheKey(accountId);
		}
		return player == null ? "" : playerSubscriptionCacheKey(player.getDatabaseID());
	}

	private static long getSubscriptionExpiresAt(Player player, boolean force) {
		String cacheKey = subscriptionCacheKey(player);
		if (cacheKey.isEmpty()) {
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
				.queryLoadGlobalCacheLong(cacheKey);
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
