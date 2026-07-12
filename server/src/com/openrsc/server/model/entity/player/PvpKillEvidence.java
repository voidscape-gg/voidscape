package com.openrsc.server.model.entity.player;

import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.model.Point;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable facts captured for a player-versus-player death before combat tracking is reset.
 *
 * Raw network addresses are deliberately reduced to one same-non-loopback boolean at capture
 * time. This object is safe to hand to post-drop policy code without exposing an address that
 * could accidentally be copied into the world-achievement ledger.
 */
public final class PvpKillEvidence {
	private final UUID deathId;
	private final long occurredAtMs;
	private final Point deathPoint;
	private final int deathInstanceId;
	private final boolean deathPointInWilderness;
	private final boolean deathPointInSafeZone;
	private final int wildernessLevel;
	private final boolean safeDeath;
	private final boolean duelActive;

	private final int killerPlayerId;
	private final String killerName;
	private final long killerUsernameHash;
	private final Long killerAccountId;
	private final boolean killerDefaultUser;
	private final int killerCombatLevel;

	private final int victimPlayerId;
	private final String victimName;
	private final Long victimAccountId;
	private final boolean victimDefaultUser;
	private final int victimCombatLevel;
	private final boolean victimWasSkulled;
	private final int victimDamageToKiller;

	private final boolean sameNonLoopbackCurrentIp;
	private final boolean killerFriendsWithVictim;
	private final boolean victimFriendsWithKiller;

	static PvpKillEvidence capture(UUID deathId, long occurredAtMs,
		Point deathPoint, int deathInstanceId,
		Player killer, Player victim, boolean safeDeath) {
		Objects.requireNonNull(killer, "killer");
		Objects.requireNonNull(victim, "victim");
		final int killerAccountId = VoidSubscription.getAccountId(killer);
		final int victimAccountId = VoidSubscription.getAccountId(victim);
		return new PvpKillEvidence(
			deathId,
			occurredAtMs,
			deathPoint,
			deathInstanceId,
			deathPoint.inWilderness(),
			deathPoint.isInSafeZone(),
			deathPoint.wildernessLevel(),
			safeDeath,
			victim.getDuel().isDuelActive() || killer.getDuel().isDuelActive(),
			killer.getDatabaseID(),
			killer.getUsername(),
			killer.getUsernameHash(),
			killerAccountId > 0 ? Long.valueOf(killerAccountId) : null,
			killer.isDefaultUser(),
			killer.getCombatLevel(),
			victim.getDatabaseID(),
			victim.getUsername(),
			victimAccountId > 0 ? Long.valueOf(victimAccountId) : null,
			victim.isDefaultUser(),
			victim.getCombatLevel(),
			victim.isSkulled(),
			Math.max(0, killer.getTrackedDamage(victim)),
			sameNonLoopbackAddress(killer.getCurrentIP(), victim.getCurrentIP()),
			killer.getSocial().isFriendsWith(victim.getUsernameHash()),
			victim.getSocial().isFriendsWith(killer.getUsernameHash()));
	}

	PvpKillEvidence(UUID deathId, long occurredAtMs, Point deathPoint, int deathInstanceId,
		boolean deathPointInWilderness, boolean deathPointInSafeZone, int wildernessLevel,
		boolean safeDeath, boolean duelActive,
		int killerPlayerId, String killerName, long killerUsernameHash, Long killerAccountId,
		boolean killerDefaultUser, int killerCombatLevel,
		int victimPlayerId, String victimName, Long victimAccountId,
		boolean victimDefaultUser, int victimCombatLevel, boolean victimWasSkulled,
		int victimDamageToKiller, boolean sameNonLoopbackCurrentIp,
		boolean killerFriendsWithVictim, boolean victimFriendsWithKiller) {
		this.deathId = Objects.requireNonNull(deathId, "deathId");
		if (occurredAtMs <= 0L) {
			throw new IllegalArgumentException("PvP death time must be positive");
		}
		this.occurredAtMs = occurredAtMs;
		final Point requiredDeathPoint = Objects.requireNonNull(deathPoint, "deathPoint");
		this.deathPoint = Point.location(requiredDeathPoint.getX(), requiredDeathPoint.getY());
		this.deathInstanceId = deathInstanceId;
		this.deathPointInWilderness = deathPointInWilderness;
		this.deathPointInSafeZone = deathPointInSafeZone;
		this.wildernessLevel = Math.max(0, wildernessLevel);
		this.safeDeath = safeDeath;
		this.duelActive = duelActive;
		this.killerPlayerId = killerPlayerId;
		this.killerName = safeName(killerName);
		this.killerUsernameHash = killerUsernameHash;
		this.killerAccountId = positiveAccountId(killerAccountId);
		this.killerDefaultUser = killerDefaultUser;
		this.killerCombatLevel = Math.max(0, killerCombatLevel);
		this.victimPlayerId = victimPlayerId;
		this.victimName = safeName(victimName);
		this.victimAccountId = positiveAccountId(victimAccountId);
		this.victimDefaultUser = victimDefaultUser;
		this.victimCombatLevel = Math.max(0, victimCombatLevel);
		this.victimWasSkulled = victimWasSkulled;
		this.victimDamageToKiller = Math.max(0, victimDamageToKiller);
		this.sameNonLoopbackCurrentIp = sameNonLoopbackCurrentIp;
		this.killerFriendsWithVictim = killerFriendsWithVictim;
		this.victimFriendsWithKiller = victimFriendsWithKiller;
	}

	private static String safeName(String name) {
		return name == null ? "" : name;
	}

	private static Long positiveAccountId(Long accountId) {
		return accountId != null && accountId > 0L ? accountId : null;
	}

	static boolean sameNonLoopbackAddress(String firstAddress, String secondAddress) {
		final String first = normalizeAddress(firstAddress);
		final String second = normalizeAddress(secondAddress);
		return !first.isEmpty() && first.equals(second) && !isLoopbackAddress(first);
	}

	private static String normalizeAddress(String address) {
		return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
	}

	private static boolean isLoopbackAddress(String address) {
		return "localhost".equals(address)
			|| address.startsWith("127.")
			|| "::1".equals(address)
			|| "0:0:0:0:0:0:0:1".equals(address)
			|| address.startsWith("::ffff:127.");
	}

	public UUID getDeathId() {
		return deathId;
	}

	public String getCanonicalDeathId() {
		return deathId.toString();
	}

	public long getOccurredAtMs() {
		return occurredAtMs;
	}

	public Point getDeathPoint() {
		return Point.location(deathPoint.getX(), deathPoint.getY());
	}

	public int getDeathInstanceId() {
		return deathInstanceId;
	}

	public boolean isDeathPointInWilderness() {
		return deathPointInWilderness;
	}

	public boolean isDeathPointInSafeZone() {
		return deathPointInSafeZone;
	}

	public int getWildernessLevel() {
		return wildernessLevel;
	}

	public boolean isSafeDeath() {
		return safeDeath;
	}

	public boolean isDuelActive() {
		return duelActive;
	}

	public int getKillerPlayerId() {
		return killerPlayerId;
	}

	public String getKillerName() {
		return killerName;
	}

	public long getKillerUsernameHash() {
		return killerUsernameHash;
	}

	public Long getKillerAccountId() {
		return killerAccountId;
	}

	public boolean isKillerDefaultUser() {
		return killerDefaultUser;
	}

	public int getKillerCombatLevel() {
		return killerCombatLevel;
	}

	public int getVictimPlayerId() {
		return victimPlayerId;
	}

	public String getVictimName() {
		return victimName;
	}

	public Long getVictimAccountId() {
		return victimAccountId;
	}

	public boolean isVictimDefaultUser() {
		return victimDefaultUser;
	}

	public int getVictimCombatLevel() {
		return victimCombatLevel;
	}

	public boolean wasVictimSkulled() {
		return victimWasSkulled;
	}

	public int getVictimDamageToKiller() {
		return victimDamageToKiller;
	}

	public boolean hasSameNonLoopbackCurrentIp() {
		return sameNonLoopbackCurrentIp;
	}

	public boolean isKillerFriendsWithVictim() {
		return killerFriendsWithVictim;
	}

	public boolean isVictimFriendsWithKiller() {
		return victimFriendsWithKiller;
	}

	public boolean isEitherDirectionFriend() {
		return killerFriendsWithVictim || victimFriendsWithKiller;
	}
}
