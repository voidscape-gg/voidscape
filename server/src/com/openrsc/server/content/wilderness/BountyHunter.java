package com.openrsc.server.content.wilderness;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BountyHunter {
	private static final long NO_PLAYER = -1L;
	private static final long SELECTION_CHECK_DELAY_MS = 30000L;
	private static final long RESELECTION_DELAY_MS = 30000L;
	private static final long MIN_WILDERNESS_TIME_MS = 120000L;
	private static final long MIN_ESCAPE_TIME_MS = 60000L;
	private static final long PAIR_REWARD_COOLDOWN_MS = 1800000L;
	private static final int MIN_TARGET_COMBAT_LEVEL = 10;

	private static final String POINTS_CACHE = "bounty_points";
	private static final String KILLS_CACHE = "bounty_kills";
	private static final String COUNTER_KILLS_CACHE = "bounty_counter_kills";
	private static final String ESCAPES_CACHE = "bounty_escapes";

	private final World world;
	private final Map<Long, Long> wildernessEnteredAt = new ConcurrentHashMap<>();
	private final Map<String, Long> pairRewardCooldowns = new ConcurrentHashMap<>();

	private long targetHash = NO_PLAYER;
	private String targetName = "";
	private long markedAt;
	private long claimantHash = NO_PLAYER;
	private String claimantName = "";
	private long claimedAt;
	private long nextSelectionCheckAt;
	private long lastLifecycleCheckTick = -1L;

	public BountyHunter(final World world) {
		this.world = world;
	}

	public void onPlayerTick(final Player player) {
		updateWildernessDwell(player);

		final long currentTick = world.getServer().getCurrentTick();
		if (lastLifecycleCheckTick == currentTick) {
			return;
		}
		lastLifecycleCheckTick = currentTick;
		runLifecycle(System.currentTimeMillis());
	}

	public void onPlayerLogout(final Player player) {
		wildernessEnteredAt.remove(player.getUsernameHash());
		if (isTarget(player)) {
			clearActiveMark("The bounty target has left the world.", System.currentTimeMillis());
		} else if (isClaimant(player)) {
			clearClaimant("The bounty hunter has left the world.");
		}
	}

	public void onPvPAttack(final Player attacker, final Player target) {
		if (!hasActiveMark() || !isTarget(target) || isTarget(attacker) || hasClaimant()) {
			return;
		}

		if (!isBountyLocation(attacker) || !isBountyLocation(target)) {
			return;
		}

		if (!isRewardEligible(attacker, target, true)) {
			return;
		}

		claimantHash = attacker.getUsernameHash();
		claimantName = attacker.getUsername();
		claimedAt = System.currentTimeMillis();
		attacker.playerServerMessage(MessageType.QUEST,
			"@red@Bounty: @whi@You are now hunting @red@" + targetName + "@whi@.");
		target.playerServerMessage(MessageType.QUEST,
			"@red@Bounty: @whi@" + claimantName + " has claimed your mark.");
	}

	public void onPlayerDeath(final Player killed, final Player killer) {
		if (!hasActiveMark()) {
			return;
		}

		if (isTarget(killed)) {
			if (killer != null && (!hasClaimant() || isClaimant(killer))) {
				handleBountyKill(killer, killed);
			} else {
				clearActiveMark("The bounty target has been killed.", System.currentTimeMillis());
			}
			return;
		}

		if (isClaimant(killed)) {
			if (killer != null && isTarget(killer)) {
				handleCounterKill(killer, killed);
			} else {
				clearClaimant("The bounty hunter has fallen.");
			}
		}
	}

	public boolean isMarked(final Player player) {
		return isTarget(player);
	}

	public void sendStatus(final Player player) {
		if (hasActiveMark()) {
			final String claimant = hasClaimant() ? " claimed by @red@" + claimantName + "@whi@" : " unclaimed";
			player.playerServerMessage(MessageType.QUEST,
				"@red@Bounty target: @whi@" + targetName + claimant + ".");
		} else {
			player.playerServerMessage(MessageType.QUEST,
				"@red@Bounty: @whi@No active target. A mark appears when eligible players roam the Wilderness.");
		}

		player.playerServerMessage(MessageType.QUEST,
			"@red@Bounty stats: @whi@" + cacheInt(player, POINTS_CACHE) + " pts, "
				+ cacheInt(player, KILLS_CACHE) + " kills, "
				+ cacheInt(player, COUNTER_KILLS_CACHE) + " counters, "
				+ cacheInt(player, ESCAPES_CACHE) + " escapes.");
	}

	private void runLifecycle(final long now) {
		if (hasActiveMark()) {
			refreshActiveMark(now);
		}

		if (!hasActiveMark() && now >= nextSelectionCheckAt) {
			nextSelectionCheckAt = now + SELECTION_CHECK_DELAY_MS;
			cleanupRewardCooldowns(now);
			selectNextTarget(now);
		}
	}

	private void refreshActiveMark(final long now) {
		final Player target = world.getPlayer(targetHash);
		if (!isValidOnlinePlayer(target)) {
			clearActiveMark("The bounty target has left the world.", now);
			return;
		}

		if (!isBountyLocation(target)) {
			handleTargetEscape(target, now);
			return;
		}

		if (hasClaimant()) {
			final Player claimant = world.getPlayer(claimantHash);
			if (!isValidOnlinePlayer(claimant) || !isBountyLocation(claimant)) {
				clearClaimant("The bounty hunter has left the Wilderness.");
			}
		}
	}

	private void selectNextTarget(final long now) {
		final List<Player> eligible = new ArrayList<>();
		for (final Player player : world.getPlayers()) {
			if (isEligibleTarget(player, now)) {
				eligible.add(player);
			}
		}

		if (eligible.isEmpty()) {
			return;
		}

		final Player target = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
		targetHash = target.getUsernameHash();
		targetName = target.getUsername();
		markedAt = now;
		claimantHash = NO_PLAYER;
		claimantName = "";
		claimedAt = 0L;

		target.getUpdateFlags().setAppearanceChanged(true);
		world.sendWorldMessage("@red@Bounty: @whi@" + targetName + " has been marked in the Wilderness.");
		target.playerServerMessage(MessageType.QUEST,
			"@red@Bounty: @whi@You have been marked. Survive, counter-kill your hunter, or escape.");
	}

	private void handleBountyKill(final Player killer, final Player target) {
		final long now = System.currentTimeMillis();
		if (isValidOnlinePlayer(killer) && isBountyLocation(killer) && isRewardEligible(killer, target, true)) {
			incrementCache(killer, POINTS_CACHE, 5);
			incrementCache(killer, KILLS_CACHE, 1);
			startRewardCooldown(killer, target, now);
			awardVoidKey(killer);
			world.sendWorldMessage("@red@Bounty: @whi@" + killer.getUsername()
				+ " claimed the mark on " + target.getUsername() + ".");
		} else {
			world.sendWorldMessage("@red@Bounty: @whi@" + target.getUsername()
				+ "'s mark ended without a reward.");
		}
		clearActiveMark(null, now);
	}

	private void handleCounterKill(final Player target, final Player claimant) {
		final long now = System.currentTimeMillis();
		if (isValidOnlinePlayer(target) && isBountyLocation(target) && isRewardEligible(target, claimant, true)) {
			incrementCache(target, POINTS_CACHE, 2);
			incrementCache(target, COUNTER_KILLS_CACHE, 1);
			startRewardCooldown(target, claimant, now);
			world.sendWorldMessage("@red@Bounty: @whi@" + target.getUsername()
				+ " counter-killed " + claimant.getUsername() + ".");
		} else {
			world.sendWorldMessage("@red@Bounty: @whi@" + target.getUsername()
				+ " survived the hunter without a reward.");
		}
		clearActiveMark(null, now);
	}

	private void handleTargetEscape(final Player target, final long now) {
		if (hasClaimant() && claimedAt > 0L && now - claimedAt >= MIN_ESCAPE_TIME_MS) {
			incrementCache(target, POINTS_CACHE, 1);
			incrementCache(target, ESCAPES_CACHE, 1);
			world.sendWorldMessage("@red@Bounty: @whi@" + target.getUsername()
				+ " escaped the Wilderness with a mark.");
		} else {
			world.sendWorldMessage("@red@Bounty: @whi@" + target.getUsername()
				+ " left the Wilderness. The mark moves on.");
		}
		clearActiveMark(null, now);
	}

	private void awardVoidKey(final Player player) {
		final boolean added = player.getCarriedItems().getInventory().add(new Item(ItemId.VOID_KEY.id(), 1));
		if (added) {
			player.playerServerMessage(MessageType.QUEST, "@gre@You receive a Void key for the bounty.");
		} else {
			player.playerServerMessage(MessageType.QUEST, "@gre@Your Void key dropped to the ground.");
		}
	}

	private void clearActiveMark(final String message, final long now) {
		final Player oldTarget = world.getPlayer(targetHash);
		if (oldTarget != null) {
			oldTarget.getUpdateFlags().setAppearanceChanged(true);
		}
		wildernessEnteredAt.remove(targetHash);
		if (message != null) {
			world.sendWorldMessage("@red@Bounty: @whi@" + message);
		}

		targetHash = NO_PLAYER;
		targetName = "";
		markedAt = 0L;
		claimantHash = NO_PLAYER;
		claimantName = "";
		claimedAt = 0L;
		nextSelectionCheckAt = now + RESELECTION_DELAY_MS;
	}

	private void clearClaimant(final String message) {
		claimantHash = NO_PLAYER;
		claimantName = "";
		claimedAt = 0L;
		if (message != null) {
			world.sendWorldMessage("@red@Bounty: @whi@" + message);
		}
	}

	private void updateWildernessDwell(final Player player) {
		if (isValidOnlinePlayer(player) && isBountyLocation(player)) {
			wildernessEnteredAt.putIfAbsent(player.getUsernameHash(), System.currentTimeMillis());
		} else {
			wildernessEnteredAt.remove(player.getUsernameHash());
		}
	}

	private boolean isEligibleTarget(final Player player, final long now) {
		if (!isValidOnlinePlayer(player) || !isBountyLocation(player)) {
			return false;
		}
		if (player.hasElevatedPriveledges()
			|| player.getDuel().isDuelActive()
			|| player.getDuel().isDueling()
			|| player.getCombatLevel() < MIN_TARGET_COMBAT_LEVEL
			|| player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return false;
		}

		final Long enteredAt = wildernessEnteredAt.get(player.getUsernameHash());
		return enteredAt != null && now - enteredAt >= MIN_WILDERNESS_TIME_MS;
	}

	private boolean isBountyLocation(final Player player) {
		return player.getLocation().inWilderness() && !player.getLocation().isInSafeZone();
	}

	private boolean isValidOnlinePlayer(final Player player) {
		return player != null && player.loggedIn() && !player.isRemoved();
	}

	private boolean isRewardEligible(final Player player, final Player other, final boolean tellPlayer) {
		if (player.getCurrentIP() != null
			&& !player.getCurrentIP().isEmpty()
			&& !isLocalIp(player.getCurrentIP())
			&& player.getCurrentIP().equals(other.getCurrentIP())) {
			if (tellPlayer) {
				player.playerServerMessage(MessageType.QUEST,
					"@red@Bounty reward blocked: @whi@same current IP as the other player.");
			}
			return false;
		}

		final long now = System.currentTimeMillis();
		final Long cooldownUntil = pairRewardCooldowns.get(pairKey(player, other));
		if (cooldownUntil != null && cooldownUntil > now) {
			if (tellPlayer) {
				final long minutes = Math.max(1L, (cooldownUntil - now + 59999L) / 60000L);
				player.playerServerMessage(MessageType.QUEST,
					"@red@Bounty reward blocked: @whi@this pair is on cooldown for " + minutes + " more minutes.");
			}
			return false;
		}

		return true;
	}

	private void startRewardCooldown(final Player player, final Player other, final long now) {
		pairRewardCooldowns.put(pairKey(player, other), now + PAIR_REWARD_COOLDOWN_MS);
	}

	private void cleanupRewardCooldowns(final long now) {
		for (final Map.Entry<String, Long> entry : pairRewardCooldowns.entrySet()) {
			if (entry.getValue() <= now) {
				pairRewardCooldowns.remove(entry.getKey(), entry.getValue());
			}
		}
	}

	private String pairKey(final Player first, final Player second) {
		final long firstHash = first.getUsernameHash();
		final long secondHash = second.getUsernameHash();
		if (firstHash < secondHash) {
			return firstHash + ":" + secondHash;
		}
		return secondHash + ":" + firstHash;
	}

	private boolean isLocalIp(final String ip) {
		return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
	}

	private boolean hasActiveMark() {
		return targetHash != NO_PLAYER;
	}

	private boolean hasClaimant() {
		return claimantHash != NO_PLAYER;
	}

	private boolean isTarget(final Player player) {
		return player != null && player.getUsernameHash() == targetHash;
	}

	private boolean isClaimant(final Player player) {
		return player != null && player.getUsernameHash() == claimantHash;
	}

	private int cacheInt(final Player player, final String key) {
		return player.getCache().hasKey(key) ? player.getCache().getInt(key) : 0;
	}

	private void incrementCache(final Player player, final String key, final int amount) {
		player.getCache().set(key, cacheInt(player, key) + amount);
	}
}
