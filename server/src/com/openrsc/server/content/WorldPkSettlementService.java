package com.openrsc.server.content;

import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.struct.WorldPkEvent;
import com.openrsc.server.database.struct.WorldPkStreak;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Locale;
import java.util.UUID;

/**
 * Exact-once durable settlement for real player-versus-player deaths.
 *
 * This coordinator owns no player mutation or presentation. Every accepted death creates
 * an immutable event and resets the victim projection in one transaction. Only a fully
 * qualified kill updates the killer projection. Replays return the stored result without
 * touching either projection.
 */
public final class WorldPkSettlementService {
	private static final Logger LOGGER = LogManager.getLogger(WorldPkSettlementService.class);
	public static final long PAIR_COOLDOWN_MS = 30L * 60L * 1000L;
	public static final String LOOT_REJECT_REASON = "loot_below_minimum";
	public static final String PAIR_COOLDOWN_REJECT_REASON = "pair_cooldown";

	private static final int MAX_SEASON_ID_LENGTH = 32;
	private static final int MAX_PLAYER_NAME_LENGTH = 12;
	private static final int MAX_REJECT_REASON_LENGTH = 64;

	private final SettlementPort port;
	private final boolean enabled;
	private final String seasonId;
	private final long lootMinimum;

	public WorldPkSettlementService(GameDatabase database, boolean enabled, String seasonId,
		long lootMinimum) {
		this(new DatabaseSettlementPort(database), enabled, seasonId, lootMinimum);
	}

	WorldPkSettlementService(SettlementPort port, boolean enabled, String seasonId,
		long lootMinimum) {
		if (port == null) {
			throw new IllegalArgumentException("World PK settlement port is required");
		}
		if (lootMinimum < 0L) {
			throw new IllegalArgumentException("World PK loot minimum must not be negative");
		}
		this.port = port;
		this.seasonId = normalizeSeasonId(seasonId);
		this.enabled = enabled && !this.seasonId.isEmpty();
		this.lootMinimum = lootMinimum;
		if (enabled && this.seasonId.isEmpty()) {
			LOGGER.error("World PK settlement disabled because the season id is invalid");
		}
	}

	/** Settles one real PvP death, returning no publishable result unless durable state is exact. */
	public WorldPkSettlementResult settle(WorldPkSettlementRequest request) {
		if (!enabled || !validRequest(request)) {
			return WorldPkSettlementResult.notSettled();
		}

		final SettlementAttempt attempt = new SettlementAttempt();
		final AtomicTransactionOutcome outcome;
		try {
			outcome = port.atomically(
				() -> settleInsideTransaction(request, attempt),
				() -> verifySettlement(attempt));
		} catch (RuntimeException ex) {
			LOGGER.error("Unable to settle world PK death " + request.getDeathId(), ex);
			return WorldPkSettlementResult.notSettled();
		}

		if (outcome != AtomicTransactionOutcome.COMMITTED || attempt.event == null) {
			if (outcome == AtomicTransactionOutcome.ROLLED_BACK) {
				LOGGER.warn("World PK settlement rolled back for death {}", request.getDeathId());
			} else {
				LOGGER.error("World PK settlement has unresolved outcome for death {}",
					request.getDeathId());
			}
			return WorldPkSettlementResult.notSettled();
		}

		return attempt.inserted
			? WorldPkSettlementResult.applied(attempt.event)
			: WorldPkSettlementResult.replay(attempt.event);
	}

	private void settleInsideTransaction(WorldPkSettlementRequest request,
		SettlementAttempt attempt) throws Exception {
		final WorldPkEvent existing = port.loadEvent(request.getDeathId());
		if (existing != null) {
			requireMatchingReplay(request, existing);
			attempt.event = copy(existing);
			return;
		}

		final int pairLow = Math.min(request.getKillerPlayerId(), request.getVictimPlayerId());
		final int pairHigh = Math.max(request.getKillerPlayerId(), request.getVictimPlayerId());
		final WorldPkStreak killerBefore = loadProjection(request.getKillerPlayerId());
		final WorldPkStreak victimBefore = loadProjection(request.getVictimPlayerId());

		String rejectReason = request.getPreliminaryRejectReason();
		if (rejectReason.isEmpty() && request.getLootValue() < lootMinimum) {
			rejectReason = LOOT_REJECT_REASON;
		}
		if (rejectReason.isEmpty()) {
			final Long lastPairKillAt = port.loadLastQualifiedPairTime(
				seasonId, pairLow, pairHigh);
			if (lastPairKillAt != null) {
				if (lastPairKillAt <= 0L) {
					throw new IllegalStateException("World PK pair cooldown row has invalid time");
				}
				if (insidePairCooldown(lastPairKillAt, request.getOccurredAtMs())) {
					rejectReason = PAIR_COOLDOWN_REJECT_REASON;
				}
			}
		}
		final boolean qualified = rejectReason.isEmpty();

		final int killerCurrent = killerBefore == null ? 0 : killerBefore.currentStreak;
		final int victimCurrent = victimBefore == null ? 0 : victimBefore.currentStreak;
		final WorldPkStreak victimAfter = resetVictimProjection(request, victimBefore);
		final WorldPkStreak killerAfter = qualified
			? incrementKillerProjection(request, killerBefore) : null;

		final WorldPkEvent event = event(request, pairLow, pairHigh, qualified,
			rejectReason, qualified ? killerAfter.currentStreak : killerCurrent, victimCurrent);
		final int inserted = port.insertEvent(event);
		if (inserted == 0) {
			final WorldPkEvent racedReplay = port.loadEvent(request.getDeathId());
			if (racedReplay == null) {
				throw new IllegalStateException("World PK replay insert had no durable event");
			}
			requireMatchingReplay(request, racedReplay);
			attempt.event = copy(racedReplay);
			return;
		}
		if (inserted != 1) {
			throw new IllegalStateException("World PK event insert returned " + inserted);
		}

		persistProjection(victimBefore, victimAfter);
		if (qualified) {
			persistProjection(killerBefore, killerAfter);
		}

		attempt.inserted = true;
		attempt.event = copy(event);
		attempt.victimBefore = copy(victimBefore);
		attempt.victimAfter = copy(victimAfter);
		attempt.killerBefore = copy(killerBefore);
		attempt.killerAfter = copy(killerAfter);
	}

	private AtomicTransactionOutcome verifySettlement(SettlementAttempt attempt)
		throws Exception {
		if (attempt.event == null) {
			return AtomicTransactionOutcome.UNKNOWN;
		}

		final WorldPkEvent durableEvent = port.loadEvent(attempt.event.deathId);
		if (!attempt.inserted) {
			return sameEvent(attempt.event, durableEvent)
				? AtomicTransactionOutcome.COMMITTED : AtomicTransactionOutcome.UNKNOWN;
		}

		final WorldPkStreak durableVictim = port.loadStreak(
			seasonId, attempt.event.victimPlayerId);
		final WorldPkStreak durableKiller = attempt.event.qualified
			? port.loadStreak(seasonId, attempt.event.killerPlayerId) : null;
		final boolean exactAfter = sameEvent(attempt.event, durableEvent)
			&& sameStreak(attempt.victimAfter, durableVictim)
			&& (!attempt.event.qualified
				|| sameStreak(attempt.killerAfter, durableKiller));
		if (exactAfter) {
			return AtomicTransactionOutcome.COMMITTED;
		}

		final boolean exactBefore = durableEvent == null
			&& sameStreak(attempt.victimBefore, durableVictim)
			&& (!attempt.event.qualified
				|| sameStreak(attempt.killerBefore, durableKiller));
		return exactBefore
			? AtomicTransactionOutcome.ROLLED_BACK : AtomicTransactionOutcome.UNKNOWN;
	}

	private WorldPkStreak loadProjection(int playerId) throws Exception {
		final WorldPkStreak streak = port.loadStreak(seasonId, playerId);
		if (streak != null && !validStoredStreak(streak, playerId)) {
			throw new IllegalStateException("Malformed world PK streak projection for " + playerId);
		}
		return streak == null ? null : copy(streak);
	}

	private void persistProjection(WorldPkStreak before, WorldPkStreak after)
		throws Exception {
		final int changed = before == null
			? port.insertStreak(after) : port.updateStreak(after);
		if (changed == 1) {
			return;
		}
		if (changed == 0 && sameStreak(after,
			port.loadStreak(after.seasonId, after.playerId))) {
			return;
		}
		throw new IllegalStateException("World PK streak persistence returned " + changed);
	}

	private WorldPkStreak resetVictimProjection(WorldPkSettlementRequest request,
		WorldPkStreak before) {
		final WorldPkStreak after = baselineProjection(
			request.getVictimPlayerId(), request.getVictimName(), request.getOccurredAtMs());
		if (before != null) {
			after.bestStreak = before.bestStreak;
			after.qualifiedKills = before.qualifiedKills;
			after.lastQualifiedAtMs = before.lastQualifiedAtMs;
		}
		return after;
	}

	private WorldPkStreak incrementKillerProjection(WorldPkSettlementRequest request,
		WorldPkStreak before) {
		final WorldPkStreak after = baselineProjection(
			request.getKillerPlayerId(), request.getKillerName(), request.getOccurredAtMs());
		final int current = before == null ? 0 : before.currentStreak;
		final long qualifiedKills = before == null ? 0L : before.qualifiedKills;
		if (current == Integer.MAX_VALUE || qualifiedKills == Long.MAX_VALUE) {
			throw new IllegalStateException("World PK streak counter overflow");
		}
		after.currentStreak = current + 1;
		after.bestStreak = Math.max(before == null ? 0 : before.bestStreak,
			after.currentStreak);
		after.qualifiedKills = qualifiedKills + 1L;
		after.lastQualifiedAtMs = request.getOccurredAtMs();
		return after;
	}

	private WorldPkStreak baselineProjection(int playerId, String playerName,
		long updatedAtMs) {
		final WorldPkStreak streak = new WorldPkStreak();
		streak.seasonId = seasonId;
		streak.playerId = playerId;
		streak.playerName = playerName;
		streak.currentStreak = 0;
		streak.bestStreak = 0;
		streak.qualifiedKills = 0L;
		streak.lastQualifiedAtMs = 0L;
		streak.updatedAtMs = updatedAtMs;
		return streak;
	}

	private WorldPkEvent event(WorldPkSettlementRequest request, int pairLow,
		int pairHigh, boolean qualified, String rejectReason, int streakAfter,
		int endedStreak) {
		final WorldPkEvent event = new WorldPkEvent();
		event.deathId = request.getDeathId();
		event.seasonId = seasonId;
		event.killerPlayerId = request.getKillerPlayerId();
		event.killerAccountId = request.getKillerAccountId();
		event.killerName = request.getKillerName();
		event.victimPlayerId = request.getVictimPlayerId();
		event.victimAccountId = request.getVictimAccountId();
		event.victimName = request.getVictimName();
		event.pairLowPlayerId = pairLow;
		event.pairHighPlayerId = pairHigh;
		event.qualified = qualified;
		event.rejectReason = rejectReason;
		event.victimWasSkulled = request.wasVictimSkulled();
		event.victimDamage = request.getVictimDamage();
		event.lootValue = request.getLootValue();
		event.streakAfter = streakAfter;
		event.endedStreak = endedStreak;
		event.wildernessLevel = request.getWildernessLevel();
		event.occurredAtMs = request.getOccurredAtMs();
		return event;
	}

	private void requireMatchingReplay(WorldPkSettlementRequest request,
		WorldPkEvent event) {
		final int pairLow = Math.min(request.getKillerPlayerId(), request.getVictimPlayerId());
		final int pairHigh = Math.max(request.getKillerPlayerId(), request.getVictimPlayerId());
		if (!validStoredEvent(event)
			|| !same(request.getDeathId(), event.deathId)
			|| !same(seasonId, event.seasonId)
			|| request.getKillerPlayerId() != event.killerPlayerId
			|| !same(request.getKillerAccountId(), event.killerAccountId)
			|| !same(request.getKillerName(), event.killerName)
			|| request.getVictimPlayerId() != event.victimPlayerId
			|| !same(request.getVictimAccountId(), event.victimAccountId)
			|| !same(request.getVictimName(), event.victimName)
			|| pairLow != event.pairLowPlayerId || pairHigh != event.pairHighPlayerId
			|| request.wasVictimSkulled() != event.victimWasSkulled
			|| request.getVictimDamage() != event.victimDamage
			|| request.getLootValue() != event.lootValue
			|| request.getWildernessLevel() != event.wildernessLevel
			|| request.getOccurredAtMs() != event.occurredAtMs) {
			throw new IllegalStateException("World PK death id collided with different evidence");
		}
	}

	private boolean validRequest(WorldPkSettlementRequest request) {
		return request != null
			&& canonicalDeathId(request.getDeathId())
			&& request.getKillerPlayerId() > 0 && request.getVictimPlayerId() > 0
			&& request.getKillerPlayerId() != request.getVictimPlayerId()
			&& validAccountId(request.getKillerAccountId())
			&& validAccountId(request.getVictimAccountId())
			&& validName(request.getKillerName()) && validName(request.getVictimName())
			&& request.getVictimDamage() >= 0 && request.getLootValue() >= 0L
			&& request.getWildernessLevel() >= 0 && request.getOccurredAtMs() > 0L
			&& validRejectReason(request.getPreliminaryRejectReason(), true);
	}

	private boolean validStoredEvent(WorldPkEvent event) {
		return event != null && canonicalDeathId(event.deathId)
			&& same(seasonId, event.seasonId)
			&& event.killerPlayerId > 0 && event.victimPlayerId > 0
			&& event.killerPlayerId != event.victimPlayerId
			&& validAccountId(event.killerAccountId) && validAccountId(event.victimAccountId)
			&& validName(event.killerName) && validName(event.victimName)
			&& event.pairLowPlayerId == Math.min(event.killerPlayerId, event.victimPlayerId)
			&& event.pairHighPlayerId == Math.max(event.killerPlayerId, event.victimPlayerId)
			&& validRejectReason(event.rejectReason, event.qualified)
			&& (event.qualified ? event.rejectReason.isEmpty() : !event.rejectReason.isEmpty())
			&& event.victimDamage >= 0 && event.lootValue >= 0L
			&& event.streakAfter >= 0 && event.endedStreak >= 0
			&& (!event.qualified || event.streakAfter > 0)
			&& event.wildernessLevel >= 0 && event.occurredAtMs > 0L;
	}

	private boolean validStoredStreak(WorldPkStreak streak, int playerId) {
		return streak != null && same(seasonId, streak.seasonId)
			&& streak.playerId == playerId && validName(streak.playerName)
			&& streak.currentStreak >= 0 && streak.bestStreak >= streak.currentStreak
			&& streak.qualifiedKills >= streak.currentStreak
			&& streak.lastQualifiedAtMs >= 0L && streak.updatedAtMs > 0L;
	}

	private static boolean insidePairCooldown(long lastQualifiedAtMs, long occurredAtMs) {
		return lastQualifiedAtMs > occurredAtMs
			|| occurredAtMs - lastQualifiedAtMs < PAIR_COOLDOWN_MS;
	}

	private static boolean validAccountId(Long accountId) {
		return accountId == null || accountId > 0L;
	}

	private static boolean validName(String playerName) {
		return playerName != null && !playerName.isEmpty()
			&& playerName.length() <= MAX_PLAYER_NAME_LENGTH
			&& playerName.equals(playerName.trim());
	}

	private static boolean validRejectReason(String reason, boolean mayBeEmpty) {
		if (reason == null || reason.length() > MAX_REJECT_REASON_LENGTH
			|| (!mayBeEmpty && reason.isEmpty())) {
			return false;
		}
		for (int index = 0; index < reason.length(); index++) {
			final char character = reason.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < '0' || character > '9')
				&& character != '_' && character != '-') {
				return false;
			}
		}
		return true;
	}

	private static boolean canonicalDeathId(String deathId) {
		if (deathId == null || deathId.length() != 36) return false;
		try {
			return UUID.fromString(deathId).toString().equals(deathId);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private static String normalizeSeasonId(String rawSeasonId) {
		if (rawSeasonId == null) return "";
		final String normalized = rawSeasonId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.length() > MAX_SEASON_ID_LENGTH) return "";
		for (int index = 0; index < normalized.length(); index++) {
			final char character = normalized.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < '0' || character > '9')
				&& character != '-' && character != '_') {
				return "";
			}
		}
		return normalized;
	}

	private static boolean same(Object first, Object second) {
		return first == null ? second == null : first.equals(second);
	}

	private static boolean sameEvent(WorldPkEvent expected, WorldPkEvent actual) {
		return expected != null && actual != null
			&& same(expected.deathId, actual.deathId)
			&& same(expected.seasonId, actual.seasonId)
			&& expected.killerPlayerId == actual.killerPlayerId
			&& same(expected.killerAccountId, actual.killerAccountId)
			&& same(expected.killerName, actual.killerName)
			&& expected.victimPlayerId == actual.victimPlayerId
			&& same(expected.victimAccountId, actual.victimAccountId)
			&& same(expected.victimName, actual.victimName)
			&& expected.pairLowPlayerId == actual.pairLowPlayerId
			&& expected.pairHighPlayerId == actual.pairHighPlayerId
			&& expected.qualified == actual.qualified
			&& same(expected.rejectReason, actual.rejectReason)
			&& expected.victimWasSkulled == actual.victimWasSkulled
			&& expected.victimDamage == actual.victimDamage
			&& expected.lootValue == actual.lootValue
			&& expected.streakAfter == actual.streakAfter
			&& expected.endedStreak == actual.endedStreak
			&& expected.wildernessLevel == actual.wildernessLevel
			&& expected.occurredAtMs == actual.occurredAtMs;
	}

	private static boolean sameStreak(WorldPkStreak expected, WorldPkStreak actual) {
		if (expected == null || actual == null) return expected == actual;
		return same(expected.seasonId, actual.seasonId)
			&& expected.playerId == actual.playerId
			&& same(expected.playerName, actual.playerName)
			&& expected.currentStreak == actual.currentStreak
			&& expected.bestStreak == actual.bestStreak
			&& expected.qualifiedKills == actual.qualifiedKills
			&& expected.lastQualifiedAtMs == actual.lastQualifiedAtMs
			&& expected.updatedAtMs == actual.updatedAtMs;
	}

	private static WorldPkEvent copy(WorldPkEvent source) {
		if (source == null) return null;
		final WorldPkEvent copy = new WorldPkEvent();
		copy.deathId = source.deathId;
		copy.seasonId = source.seasonId;
		copy.killerPlayerId = source.killerPlayerId;
		copy.killerAccountId = source.killerAccountId;
		copy.killerName = source.killerName;
		copy.victimPlayerId = source.victimPlayerId;
		copy.victimAccountId = source.victimAccountId;
		copy.victimName = source.victimName;
		copy.pairLowPlayerId = source.pairLowPlayerId;
		copy.pairHighPlayerId = source.pairHighPlayerId;
		copy.qualified = source.qualified;
		copy.rejectReason = source.rejectReason;
		copy.victimWasSkulled = source.victimWasSkulled;
		copy.victimDamage = source.victimDamage;
		copy.lootValue = source.lootValue;
		copy.streakAfter = source.streakAfter;
		copy.endedStreak = source.endedStreak;
		copy.wildernessLevel = source.wildernessLevel;
		copy.occurredAtMs = source.occurredAtMs;
		return copy;
	}

	private static WorldPkStreak copy(WorldPkStreak source) {
		if (source == null) return null;
		final WorldPkStreak copy = new WorldPkStreak();
		copy.seasonId = source.seasonId;
		copy.playerId = source.playerId;
		copy.playerName = source.playerName;
		copy.currentStreak = source.currentStreak;
		copy.bestStreak = source.bestStreak;
		copy.qualifiedKills = source.qualifiedKills;
		copy.lastQualifiedAtMs = source.lastQualifiedAtMs;
		copy.updatedAtMs = source.updatedAtMs;
		return copy;
	}

	@FunctionalInterface
	interface TransactionBody {
		void run() throws Exception;
	}

	@FunctionalInterface
	interface TransactionVerifier {
		AtomicTransactionOutcome verify() throws Exception;
	}

	interface SettlementPort {
		AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier);
		WorldPkEvent loadEvent(String deathId) throws Exception;
		Long loadLastQualifiedPairTime(String seasonId, int pairLowPlayerId,
			int pairHighPlayerId) throws Exception;
		WorldPkStreak loadStreak(String seasonId, int playerId) throws Exception;
		int insertEvent(WorldPkEvent event) throws Exception;
		int insertStreak(WorldPkStreak streak) throws Exception;
		int updateStreak(WorldPkStreak streak) throws Exception;
	}

	private static final class SettlementAttempt {
		private boolean inserted;
		private WorldPkEvent event;
		private WorldPkStreak victimBefore;
		private WorldPkStreak victimAfter;
		private WorldPkStreak killerBefore;
		private WorldPkStreak killerAfter;
	}

	private static final class DatabaseSettlementPort implements SettlementPort {
		private final GameDatabase database;

		private DatabaseSettlementPort(GameDatabase database) {
			if (database == null) {
				throw new IllegalArgumentException("World PK settlement database is required");
			}
			this.database = database;
		}

		@Override
		public AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			return database.atomicallySettled(body::run, verifier::verify);
		}

		@Override
		public WorldPkEvent loadEvent(String deathId) throws Exception {
			return database.queryLoadWorldPkEvent(deathId);
		}

		@Override
		public Long loadLastQualifiedPairTime(String seasonId, int pairLowPlayerId,
			int pairHighPlayerId) throws Exception {
			return database.queryLoadLastQualifiedWorldPkPairTime(
				seasonId, pairLowPlayerId, pairHighPlayerId);
		}

		@Override
		public WorldPkStreak loadStreak(String seasonId, int playerId) throws Exception {
			return database.queryLoadWorldPkStreak(seasonId, playerId);
		}

		@Override
		public int insertEvent(WorldPkEvent event) throws Exception {
			return database.queryInsertWorldPkEvent(event);
		}

		@Override
		public int insertStreak(WorldPkStreak streak) throws Exception {
			return database.queryInsertWorldPkStreak(streak);
		}

		@Override
		public int updateStreak(WorldPkStreak streak) throws Exception {
			return database.queryUpdateWorldPkStreak(streak);
		}
	}
}
