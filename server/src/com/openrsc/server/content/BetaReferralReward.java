package com.openrsc.server.content;

import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.struct.PlayerFriend;
import com.openrsc.server.database.struct.PlayerIps;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class BetaReferralReward {
	public static final int CLIENT_VERSION = 10111;
	private static final String REWARD_KEY_PREFIX = "refcode:";
	private static final String NOTICE_KEY_PREFIX = "refnotice:";
	private static final String REFERRED_BY_KEY = "referred_by";
	private static final String REFERRED_BY_NAME_KEY = "referred_name";
	private static final int CACHE_TYPE_INT = 0;
	private static final int CACHE_TYPE_STRING = 1;
	private static final String SIGNUP_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Object CODE_LOCK = new Object();

	private BetaReferralReward() {
	}

	public static void creditFromAppearance(Player referred, String referrerInput) {
		if (referred == null || referrerInput == null || referrerInput.trim().isEmpty()) {
			return;
		}
		if (referred.getCache().hasKey(REFERRED_BY_KEY)) {
			return;
		}

		String referrerName = DataConversions.sanitizeUsername(referrerInput.trim());
		if (referrerName == null || referrerName.trim().isEmpty()) {
			referred.message("@or1@Referral name was not recognised, so no invite reward was credited.");
			return;
		}
		if (DataConversions.usernameToHash(referrerName) == referred.getUsernameHash()) {
			referred.message("@or1@You can't refer yourself, but your character was created normally.");
			return;
		}

		try {
			GameDatabase database = referred.getWorld().getServer().getDatabase();
			if (!database.playerExists(referrerName)) {
				referred.message("@or1@Referral name '" + referrerInput.trim() + "' was not found.");
				referred.message("@whi@You can still play normally, but no invite reward was credited.");
				return;
			}

			int referrerId = database.playerIdFromUsername(referrerName);
			if (referrerId <= 0 || referrerId == referred.getDatabaseID()) {
				referred.message("@or1@You can't refer yourself, but your character was created normally.");
				return;
			}
			if (isLikelySameTester(referred, database.playerIps(referrerName))) {
				referred.message("@or1@Referral name appears to be one of your own characters.");
				referred.message("@whi@Your character was created normally, but no invite reward was credited.");
				return;
			}

			String rewardKey = rewardKey(referred.getDatabaseID());
			if (database.queryPlayerCacheOwner(rewardKey) != null) {
				return;
			}

			String referrerDisplayName = displayName(database, referrerName);
			String code = mintSignupCode(database);
			String entry = code + "|" + referred.getUsername() + "|" + System.currentTimeMillis();
			database.querySavePlayerCacheValue(referrerId, CACHE_TYPE_STRING, rewardKey, entry);
			database.querySavePlayerCacheValue(referred.getDatabaseID(), CACHE_TYPE_INT, REFERRED_BY_KEY, Integer.toString(referrerId));
			database.querySavePlayerCacheValue(referred.getDatabaseID(), CACHE_TYPE_STRING, REFERRED_BY_NAME_KEY, referrerDisplayName);
			referred.getCache().set(REFERRED_BY_KEY, referrerId);
			referred.getCache().store(REFERRED_BY_NAME_KEY, referrerDisplayName);

			Player onlineReferrer = referred.getWorld().getPlayer(DataConversions.usernameToHash(referrerName));
			if (onlineReferrer != null) {
				onlineReferrer.getCache().store(rewardKey, entry);
				messageReward(onlineReferrer, referred.getUsername(), code);
				onlineReferrer.save(false, true);
			} else {
				database.querySavePlayerCacheValue(referrerId, CACHE_TYPE_STRING, noticeKey(referred.getDatabaseID()), entry);
			}

			referred.message("@mag@Referral credited. " + referrerDisplayName + " earned a 1-week subscription card code.");
			referred.getWorld().getServer().getGameLogger().addQuery(
				new GenericLog(referred.getWorld(), referred.getUsername()
					+ " credited beta referral reward " + code + " to " + referrerDisplayName + "."));
		} catch (RuntimeException ex) {
			referred.message("@or1@The referral ledger could not be updated right now.");
			referred.message("@whi@Your character was created normally. Ask staff if the invite should be credited.");
		}
	}

	public static void showPendingReferralNotices(Player player) {
		if (player == null) {
			return;
		}
		List<String> noticeKeys = new ArrayList<>();
		for (String key : player.getCache().getCacheMap().keySet()) {
			if (key != null && key.startsWith(NOTICE_KEY_PREFIX)) {
				noticeKeys.add(key);
			}
		}
		if (noticeKeys.isEmpty()) {
			return;
		}
		Collections.sort(noticeKeys);
		for (String key : noticeKeys) {
			RewardEntry entry = parseEntry(player.getCache().getString(key));
			if (entry != null) {
				messageReward(player, entry.referredName, entry.code);
			}
			player.getCache().remove(key);
		}
		player.save(false, true);
	}

	public static void showCodes(Player player) {
		if (player == null) {
			return;
		}
		List<String> rewardKeys = new ArrayList<>();
		for (String key : player.getCache().getCacheMap().keySet()) {
			if (key != null && key.startsWith(REWARD_KEY_PREFIX)) {
				rewardKeys.add(key);
			}
		}
		Collections.sort(rewardKeys);
		if (rewardKeys.isEmpty()) {
			ActionSender.sendBox(player, "@mag@Beta referral codes% %@whi@No referral reward codes yet.%"
				+ "@whi@Invite a friend. If they enter your in-game name while making their character, you earn one.", true);
			return;
		}

		StringBuilder builder = new StringBuilder("@mag@Beta referral codes% %");
		GameDatabase database = player.getWorld().getServer().getDatabase();
		for (String key : rewardKeys) {
			RewardEntry entry = parseEntry(player.getCache().getString(key));
			if (entry == null) {
				continue;
			}
			builder.append("@yel@").append(entry.code)
				.append("@whi@ - ")
				.append(codeStatus(database, entry.code));
			if (!entry.referredName.isEmpty()) {
				builder.append(" from ").append(entry.referredName);
			}
			builder.append("%");
		}
		builder.append("%@whi@Redeem codes at the Lumbridge Void Subscription Vendor.");
		ActionSender.sendBox(player, builder.toString(), true);
	}

	private static void messageReward(Player player, String referredName, String code) {
		player.message("@mag@" + referredName + " used your name as their invite.");
		player.message("@whi@You earned a 1-week subscription card code: @yel@" + code);
		player.message("@whi@Use ::codes any time if you forget it.");
	}

	private static String mintSignupCode(GameDatabase database) {
		synchronized (CODE_LOCK) {
			for (int attempt = 0; attempt < 100; attempt++) {
				String code = randomSignupCode();
				String normalized = VoidSubscription.normalizeSignupCode(code);
				String cacheKey = VoidSubscription.signupCodeCacheKey(normalized);
				if (cacheKey.isEmpty() || database.queryLoadGlobalCacheInt(cacheKey) != null) {
					continue;
				}
				database.querySaveGlobalCacheInt(cacheKey, VoidSubscription.SIGNUP_CODE_AVAILABLE);
				return code;
			}
		}
		throw new IllegalStateException("Unable to mint unique beta referral signup code");
	}

	private static boolean isLikelySameTester(Player referred, PlayerIps referrerIps) {
		if (referred == null || referrerIps == null) {
			return false;
		}
		String currentIp = normalizeReferralIp(referred.getCurrentIP());
		if (currentIp.isEmpty() || isLocalIp(currentIp)) {
			return false;
		}
		return currentIp.equals(normalizeReferralIp(referrerIps.loginIp))
			|| currentIp.equals(normalizeReferralIp(referrerIps.creationIp));
	}

	private static String normalizeReferralIp(String ip) {
		return ip == null ? "" : ip.trim();
	}

	private static boolean isLocalIp(String ip) {
		return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
	}

	private static String randomSignupCode() {
		return "VOID-" + randomGroup() + "-" + randomGroup();
	}

	private static String randomGroup() {
		StringBuilder builder = new StringBuilder(4);
		for (int i = 0; i < 4; i++) {
			builder.append(SIGNUP_CODE_ALPHABET.charAt(RANDOM.nextInt(SIGNUP_CODE_ALPHABET.length())));
		}
		return builder.toString();
	}

	private static String rewardKey(int referredPlayerId) {
		return REWARD_KEY_PREFIX + referredPlayerId;
	}

	private static String noticeKey(int referredPlayerId) {
		return NOTICE_KEY_PREFIX + referredPlayerId;
	}

	private static String displayName(GameDatabase database, String username) {
		try {
			PlayerFriend proper = database.getProperUsernameCapitalization(username);
			if (proper != null && proper.playerName != null && !proper.playerName.trim().isEmpty()) {
				return proper.playerName;
			}
		} catch (RuntimeException ignored) {
		}
		return username;
	}

	private static String codeStatus(GameDatabase database, String code) {
		String normalized = VoidSubscription.normalizeSignupCode(code);
		String cacheKey = VoidSubscription.signupCodeCacheKey(normalized);
		if (cacheKey.isEmpty()) {
			return "invalid";
		}
		try {
			Integer state = database.queryLoadGlobalCacheInt(cacheKey);
			if (state != null && state == VoidSubscription.SIGNUP_CODE_REDEEMED) {
				return "redeemed";
			}
			if (state != null && state == VoidSubscription.SIGNUP_CODE_AVAILABLE) {
				return "available";
			}
		} catch (RuntimeException ignored) {
			return "status unknown";
		}
		return "not found";
	}

	private static RewardEntry parseEntry(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return null;
		}
		String[] parts = raw.split("\\|", 3);
		if (parts.length < 1 || parts[0].trim().isEmpty()) {
			return null;
		}
		String referredName = parts.length >= 2 ? parts[1].trim() : "";
		return new RewardEntry(parts[0].trim().toUpperCase(Locale.ROOT), referredName);
	}

	private static final class RewardEntry {
		private final String code;
		private final String referredName;

		private RewardEntry(String code, String referredName) {
			this.code = code;
			this.referredName = referredName;
		}
	}
}
