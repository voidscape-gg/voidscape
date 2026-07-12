package com.openrsc.server.content;

import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.PvpKillEvidence;

import java.util.List;

/**
 * Pure policy helpers for evaluating a potential qualified Wilderness player kill.
 *
 * This class does not write persistence or publish announcements. The durable coordinator
 * applies the loot floor and pair cooldown only after these preliminary checks pass.
 */
public final class WorldPkEvaluation {
	public static final String PASS = "";
	public static final String INVALID_IDENTITY = "invalid_identity";
	public static final String NOT_WILDERNESS = "not_wilderness";
	public static final String NONZERO_INSTANCE = "nonzero_instance";
	public static final String SAFE_ZONE_OR_DEATH = "safe_zone_or_death";
	public static final String DUEL = "duel";
	public static final String VICTIM_NOT_SKULLED = "victim_not_skulled";
	public static final String NON_DEFAULT_USER = "non_default_user";
	public static final String LOW_COMBAT_LEVEL = "low_combat_level";
	public static final String SAME_ACCOUNT = "same_account";
	public static final String SAME_IP = "same_ip";
	public static final String FRIENDS = "friends";
	public static final String NO_VICTIM_DAMAGE = "no_victim_damage";

	private static final int MIN_COMBAT_LEVEL = 10;
	private static final int MAX_PLAYER_NAME_LENGTH = 12;

	private WorldPkEvaluation() {
	}

	/**
	 * Returns the first stable rejection reason, or the empty string when post-drop loot and
	 * pair-cooldown evaluation may continue.
	 */
	public static String preliminaryRejectReason(PvpKillEvidence evidence) {
		if (!hasValidIdentity(evidence)) return INVALID_IDENTITY;
		if (!evidence.isDeathPointInWilderness()) return NOT_WILDERNESS;
		if (evidence.getDeathInstanceId() != 0) return NONZERO_INSTANCE;
		if (evidence.isDeathPointInSafeZone() || evidence.isSafeDeath()) {
			return SAFE_ZONE_OR_DEATH;
		}
		if (evidence.isDuelActive()) return DUEL;
		if (!evidence.wasVictimSkulled()) return VICTIM_NOT_SKULLED;
		if (!evidence.isKillerDefaultUser() || !evidence.isVictimDefaultUser()) {
			return NON_DEFAULT_USER;
		}
		if (evidence.getKillerCombatLevel() < MIN_COMBAT_LEVEL
			|| evidence.getVictimCombatLevel() < MIN_COMBAT_LEVEL) {
			return LOW_COMBAT_LEVEL;
		}
		if (samePositiveAccount(evidence.getKillerAccountId(), evidence.getVictimAccountId())) {
			return SAME_ACCOUNT;
		}
		if (evidence.hasSameNonLoopbackCurrentIp()) return SAME_IP;
		if (evidence.isEitherDirectionFriend()) return FRIENDS;
		if (evidence.getVictimDamageToKiller() <= 0) return NO_VICTIM_DAMAGE;
		return PASS;
	}

	/**
	 * Sums exact killer-owned, tradeable items from the immutable post-drop list.
	 * Malformed entries fail closed and the nonnegative total saturates at {@link Long#MAX_VALUE}.
	 */
	public static long eligibleLootValue(PvpKillEvidence evidence,
		List<GroundItem> groundItems) {
		if (evidence == null || evidence.getKillerUsernameHash() <= 0L
			|| groundItems == null || groundItems.isEmpty()) {
			return 0L;
		}

		final long killerUsernameHash = evidence.getKillerUsernameHash();
		long total = 0L;
		for (GroundItem groundItem : groundItems) {
			total = saturatingAddPositive(total,
				eligibleGroundItemValue(groundItem, killerUsernameHash));
			if (total == Long.MAX_VALUE) return total;
		}
		return total;
	}

	/** Returns zero for nonpositive operands and otherwise multiplies with saturation. */
	public static long saturatingMultiplyPositive(long first, long second) {
		if (first <= 0L || second <= 0L) return 0L;
		if (first > Long.MAX_VALUE / second) return Long.MAX_VALUE;
		return first * second;
	}

	/** Adds only a positive increment to a nonnegative total, saturating on overflow. */
	public static long saturatingAddPositive(long total, long increment) {
		final long nonnegativeTotal = Math.max(0L, total);
		if (increment <= 0L) return nonnegativeTotal;
		if (nonnegativeTotal > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
		return nonnegativeTotal + increment;
	}

	private static long eligibleGroundItemValue(GroundItem groundItem,
		long killerUsernameHash) {
		if (groundItem == null) return 0L;
		try {
			if (!groundItem.getAttribute("playerKill", false)) return 0L;
			if (groundItem.getAttribute("killerHash", -1L) != killerUsernameHash) return 0L;
			if (groundItem.getOwnerUsernameHash() != killerUsernameHash) return 0L;

			final ItemDefinition definition = groundItem.getDef();
			if (definition == null || definition.isUntradable()) return 0L;
			final int amount = groundItem.getAmount();
			final int defaultPrice = definition.getDefaultPrice();
			if (amount <= 0 || defaultPrice <= 0) return 0L;
			return saturatingMultiplyPositive(defaultPrice, amount);
		} catch (RuntimeException malformedGroundItem) {
			return 0L;
		}
	}

	private static boolean hasValidIdentity(PvpKillEvidence evidence) {
		return evidence != null
			&& evidence.getKillerPlayerId() > 0
			&& evidence.getVictimPlayerId() > 0
			&& evidence.getKillerPlayerId() != evidence.getVictimPlayerId()
			&& evidence.getKillerUsernameHash() > 0L
			&& validPlayerName(evidence.getKillerName())
			&& validPlayerName(evidence.getVictimName());
	}

	private static boolean validPlayerName(String name) {
		if (name == null || name.length() > MAX_PLAYER_NAME_LENGTH) return false;
		return name.equals(name.trim()) && !name.isEmpty();
	}

	private static boolean samePositiveAccount(Long first, Long second) {
		return first != null && second != null && first > 0L && first.equals(second);
	}
}
