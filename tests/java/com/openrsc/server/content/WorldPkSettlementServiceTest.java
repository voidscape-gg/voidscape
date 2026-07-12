package com.openrsc.server.content;

import com.openrsc.server.content.WorldPkSettlementResult.Status;
import com.openrsc.server.content.WorldPkSettlementService.SettlementPort;
import com.openrsc.server.content.WorldPkSettlementService.TransactionBody;
import com.openrsc.server.content.WorldPkSettlementService.TransactionVerifier;
import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.struct.WorldPkEvent;
import com.openrsc.server.database.struct.WorldPkStreak;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Executable fault-injection tests for exact-once durable PK settlement. */
public final class WorldPkSettlementServiceTest {
	private static final String SEASON = "launch-2026";
	private static final long LOOT_MINIMUM = 5_000L;
	private static final long TIME = 1_789_000_000_000L;
	private static final String DEATH_ONE = "00000000-0000-0000-0000-000000000101";
	private static final String DEATH_TWO = "00000000-0000-0000-0000-000000000102";
	private static final String DEATH_THREE = "00000000-0000-0000-0000-000000000103";

	private WorldPkSettlementServiceTest() {
	}

	public static void main(String[] args) throws Exception {
		testInvalidInputAndDisabledServiceAvoidTransactions();
		testQualifiedDeathAtomicallyResetsAndIncrements();
		testPreliminaryAndLootRejectionsLeaveKillerUntouched();
		testPairCooldownIsUnorderedAndBoundaryQualifies();
		testReplayReturnsStoredOutcomeWithoutWrites();
		testReplayIdentityCollisionFailsClosed();
		testRejectedDeathCreatesMissingVictimProjectionOnly();
		testNoOpVictimUpdateIsAcceptedOnlyWhenExact();
		testBodyFailureRollsBackEventAndBothProjections();
		testLostCommitRequiresExactAfterOrBeforeState();
		testReplayLostCommitRemainsNonPublishable();
		testCounterOverflowFailsClosed();
		testConcurrentReplayMutatesStreaksOnce();
		testConcurrentPairDeathsProduceOneQualifiedKill();
		System.out.println("World PK settlement service tests passed.");
	}

	private static void testInvalidInputAndDisabledServiceAvoidTransactions() {
		FakePort port = new FakePort();
		checkNone(new WorldPkSettlementService(port, false, SEASON, LOOT_MINIMUM)
			.settle(request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			"disabled service fails closed");
		checkNone(new WorldPkSettlementService(port, true, "launch 2026!", LOOT_MINIMUM)
			.settle(request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			"invalid season fails closed");
		WorldPkSettlementService service = service(port);
		checkNone(service.settle(request("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA",
			10, 20, 6_000L, TIME, "")), "noncanonical UUID fails closed");
		checkNone(service.settle(request(DEATH_ONE, 10, 10, 6_000L, TIME, "")),
			"self-kill identity fails closed");
		checkNone(service.settle(request(DEATH_ONE, 10, 20, -1L, TIME, "")),
			"negative loot fails closed");
		checkNone(service.settle(request(DEATH_ONE, 10, 20, 6_000L, TIME,
			"bad reason")), "unstable rejection token fails closed");
		checkNone(service.settle(new WorldPkSettlementRequest(DEATH_ONE, 10, null,
			"NameTooLongxx", 20, null, "Victim", true, 1, 6_000L, 5, TIME, "")),
			"overlong player name fails closed");
		check(port.atomicCount == 0, "invalid requests never open a transaction");

		boolean rejectedMinimum = false;
		try {
			new WorldPkSettlementService(port, true, SEASON, -1L);
		} catch (IllegalArgumentException expected) {
			rejectedMinimum = true;
		}
		check(rejectedMinimum, "negative configured loot minimum is rejected");
	}

	private static void testQualifiedDeathAtomicallyResetsAndIncrements() {
		FakePort port = new FakePort();
		port.seedStreak(streak(10, "Killer", 2, 4, 9L, TIME - 10_000L, TIME - 1L));
		port.seedStreak(streak(20, "Victim", 5, 7, 8L, TIME - 20_000L, TIME - 2L));
		long wideLoot = 5_000_000_000L;

		WorldPkSettlementResult result = service(port).settle(
			request(DEATH_ONE, 10, 20, wideLoot, TIME, ""));

		checkApplied(result, true, "qualified death is newly durable");
		check(result.getLootValue() == wideLoot && result.getStreakAfter() == 3
			&& result.getEndedStreak() == 5 && result.getRejectReason().isEmpty(),
			"event preserves 64-bit loot and exact before/after streaks");
		WorldPkStreak killer = port.getStreak(10);
		WorldPkStreak victim = port.getStreak(20);
		check(killer.currentStreak == 3 && killer.bestStreak == 4
			&& killer.qualifiedKills == 10L && killer.lastQualifiedAtMs == TIME
			&& killer.updatedAtMs == TIME, "qualified kill increments killer projection once");
		check(victim.currentStreak == 0 && victim.bestStreak == 7
			&& victim.qualifiedKills == 8L && victim.lastQualifiedAtMs == TIME - 20_000L
			&& victim.updatedAtMs == TIME, "death resets victim while preserving lifetime fields");
		check(port.pairQueryCount == 1 && port.pairQueriesInsideTransaction == 1,
			"unordered-pair cooldown is queried inside the transaction");
		check(port.eventInsertCalls == 1 && port.streakUpdateCalls == 2,
			"event and both existing projections share one transaction");
	}

	private static void testPreliminaryAndLootRejectionsLeaveKillerUntouched() {
		FakePort preliminary = new FakePort();
		WorldPkStreak killerBefore = streak(10, "Killer", 2, 4, 9L,
			TIME - 10_000L, TIME - 1L);
		preliminary.seedStreak(killerBefore);
		preliminary.seedStreak(streak(20, "Victim", 3, 3, 3L,
			TIME - 20_000L, TIME - 2L));
		WorldPkSettlementResult rejected = service(preliminary).settle(
			request(DEATH_ONE, 10, 20, 99_000L, TIME, "same_linked_account"));
		checkApplied(rejected, false, "preliminary policy rejection is durably audited");
		check("same_linked_account".equals(rejected.getRejectReason())
			&& rejected.getStreakAfter() == 2 && rejected.getEndedStreak() == 3,
			"preliminary reason wins and rejected event records unchanged killer streak");
		checkStreak(killerBefore, preliminary.getStreak(10),
			"preliminary rejection leaves killer projection byte-for-byte unchanged");
		check(preliminary.getStreak(20).currentStreak == 0,
			"preliminary rejection still resets victim");
		check(preliminary.pairQueryCount == 0,
			"preliminary rejection avoids unnecessary cooldown query");

		FakePort lowLoot = new FakePort();
		lowLoot.seedStreak(killerBefore);
		WorldPkSettlementResult belowFloor = service(lowLoot).settle(
			request(DEATH_TWO, 10, 20, LOOT_MINIMUM - 1L, TIME + 1L, ""));
		checkApplied(belowFloor, false, "below-floor death is durably audited");
		check(WorldPkSettlementService.LOOT_REJECT_REASON.equals(
			belowFloor.getRejectReason()), "configured loot floor supplies stable rejection reason");
		checkStreak(killerBefore, lowLoot.getStreak(10),
			"loot rejection leaves killer projection unchanged");
		check(lowLoot.pairQueryCount == 0, "loot rejection occurs before cooldown query");
	}

	private static void testPairCooldownIsUnorderedAndBoundaryQualifies() {
		FakePort port = new FakePort();
		port.seedEvent(previousQualified(DEATH_ONE, 10, 20, TIME));

		WorldPkSettlementResult inside = service(port).settle(request(DEATH_TWO,
			10, 20, LOOT_MINIMUM, TIME + WorldPkSettlementService.PAIR_COOLDOWN_MS - 1L, ""));
		checkApplied(inside, false, "pair inside cooldown is recorded as rejected");
		check(WorldPkSettlementService.PAIR_COOLDOWN_REJECT_REASON.equals(
			inside.getRejectReason()), "inside-window event has pair cooldown reason");

		WorldPkSettlementResult boundary = service(port).settle(request(DEATH_THREE,
			20, 10, LOOT_MINIMUM, TIME + WorldPkSettlementService.PAIR_COOLDOWN_MS, ""));
		checkApplied(boundary, true, "exact 30-minute boundary qualifies");
		check(boundary.getStreakAfter() == 1,
			"reversed unordered pair increments the new killer at the boundary");
		check(port.pairQueryCount == 2 && port.pairQueriesInsideTransaction == 2,
			"both directions query the same pair durably");
	}

	private static void testReplayReturnsStoredOutcomeWithoutWrites() {
		FakePort port = new FakePort();
		WorldPkSettlementService service = service(port);
		WorldPkSettlementRequest request = request(DEATH_ONE, 10, 20, 6_000L, TIME, "");
		WorldPkSettlementResult first = service.settle(request);
		checkApplied(first, true, "initial event applies");
		int eventWrites = port.eventInsertCalls;
		int streakWrites = port.streakInsertCalls + port.streakUpdateCalls;

		WorldPkSettlementResult replay = service.settle(request);
		check(replay.getStatus() == Status.REPLAY && replay.isSettled()
			&& !replay.isNewEvent() && !replay.isPublishable() && replay.isQualified(),
			"replay returns stored durable outcome but cannot republish");
		check(replay.getStreakAfter() == first.getStreakAfter()
			&& replay.getEndedStreak() == first.getEndedStreak(),
			"replay exposes the stored result rather than recomputing it");
		check(port.eventInsertCalls == eventWrites
			&& port.streakInsertCalls + port.streakUpdateCalls == streakWrites,
			"replay performs no event or streak writes");
	}

	private static void testReplayIdentityCollisionFailsClosed() {
		FakePort port = new FakePort();
		WorldPkSettlementService service = service(port);
		checkApplied(service.settle(request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			true, "fixture event applies");
		WorldPkStreak killerBefore = port.getStreak(10);
		WorldPkStreak victimBefore = port.getStreak(20);
		int eventWrites = port.eventInsertCalls;

		checkNone(service.settle(request(DEATH_ONE, 10, 20, 7_000L, TIME, "")),
			"same death id with different evidence fails closed");
		check(port.eventInsertCalls == eventWrites, "collision does not insert another event");
		checkStreak(killerBefore, port.getStreak(10), "collision does not mutate killer");
		checkStreak(victimBefore, port.getStreak(20), "collision does not mutate victim");
	}

	private static void testRejectedDeathCreatesMissingVictimProjectionOnly() {
		FakePort port = new FakePort();
		WorldPkSettlementResult result = service(port).settle(
			request(DEATH_ONE, 10, 20, 6_000L, TIME, "friend_pair"));
		checkApplied(result, false, "rejected missing-projection death applies");
		check(port.getStreak(10) == null, "rejected kill does not create killer projection");
		WorldPkStreak victim = port.getStreak(20);
		check(victim != null && victim.currentStreak == 0 && victim.bestStreak == 0
			&& victim.qualifiedKills == 0L && victim.lastQualifiedAtMs == 0L,
			"every real PvP death durably creates/resets the victim projection");
	}

	private static void testNoOpVictimUpdateIsAcceptedOnlyWhenExact() {
		FakePort exact = new FakePort();
		exact.returnZeroForNoOpUpdate = true;
		exact.seedStreak(streak(20, "Victim", 0, 0, 0L, 0L, TIME));
		WorldPkSettlementResult result = service(exact).settle(
			request(DEATH_ONE, 10, 20, 6_000L, TIME, "friend_pair"));
		checkApplied(result, false, "exact no-op victim UPDATE is accepted");
		check(exact.streakUpdateCalls == 1 && exact.streakReloadAfterZeroCount == 1,
			"zero-row UPDATE is followed by an exact in-transaction reload");

		FakePort drift = new FakePort();
		drift.returnZeroForEveryUpdate = true;
		drift.seedStreak(streak(20, "Victim", 2, 2, 2L, TIME - 2L, TIME - 1L));
		checkNone(service(drift).settle(
			request(DEATH_TWO, 10, 20, 6_000L, TIME, "friend_pair")),
			"zero-row UPDATE with non-exact durable projection rolls back");
		check(drift.getEvent(DEATH_TWO) == null && drift.getStreak(20).currentStreak == 2,
			"failed no-op proof restores event and victim before-state");
	}

	private static void testBodyFailureRollsBackEventAndBothProjections() {
		FakePort port = new FakePort();
		WorldPkStreak killer = streak(10, "Killer", 1, 2, 3L, TIME - 3L, TIME - 2L);
		WorldPkStreak victim = streak(20, "Victim", 4, 5, 6L, TIME - 4L, TIME - 3L);
		port.seedStreak(killer);
		port.seedStreak(victim);
		port.failOnTransactionStreakWrite = 2;

		checkNone(service(port).settle(request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			"projection write failure publishes no result");
		check(port.getEvent(DEATH_ONE) == null, "body failure rolls back tentative event");
		checkStreak(killer, port.getStreak(10), "body failure restores killer before-state");
		checkStreak(victim, port.getStreak(20), "body failure restores victim before-state");
	}

	private static void testLostCommitRequiresExactAfterOrBeforeState() {
		FakePort committed = new FakePort();
		committed.mode = SettlementMode.LOST_COMMIT_KEEP_AFTER;
		WorldPkSettlementResult committedResult = service(committed).settle(
			request(DEATH_ONE, 10, 20, 6_000L, TIME, ""));
		checkApplied(committedResult, true, "exact durable after-state resolves lost COMMIT");
		check(committed.lastVerifierOutcome == AtomicTransactionOutcome.COMMITTED
			&& committed.verifierStreakLoads == 2,
			"verifier requires exact victim and qualified-killer after-state");

		FakePort rolledBack = new FakePort();
		rolledBack.mode = SettlementMode.LOST_COMMIT_RESTORE_BEFORE;
		checkNone(service(rolledBack).settle(
			request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			"exact durable before-state resolves rollback without publishing");
		check(rolledBack.lastVerifierOutcome == AtomicTransactionOutcome.ROLLED_BACK
			&& rolledBack.getEvent(DEATH_ONE) == null
			&& rolledBack.getStreak(10) == null && rolledBack.getStreak(20) == null,
			"rollback verifier requires absent event and exact absent projections");

		assertUnknownMode(SettlementMode.LOST_COMMIT_EVENT_ONLY,
			"event without exact affected projections is unknown");
		assertUnknownMode(SettlementMode.LOST_COMMIT_DRIFT_AFTER,
			"event plus malformed after-projection is unknown");
		assertUnknownMode(SettlementMode.LOST_COMMIT_DRIFT_BEFORE,
			"absent event plus malformed before-projection is unknown");

		FakePort rejected = new FakePort();
		rejected.mode = SettlementMode.LOST_COMMIT_KEEP_AFTER;
		WorldPkSettlementResult rejectedResult = service(rejected).settle(
			request(DEATH_TWO, 10, 20, 6_000L, TIME, "friend_pair"));
		checkApplied(rejectedResult, false, "rejected event can resolve exact lost COMMIT");
		check(rejected.verifierStreakLoads == 1,
			"rejected settlement verifies only its affected victim projection");
	}

	private static void assertUnknownMode(SettlementMode mode, String message) {
		FakePort port = new FakePort();
		port.mode = mode;
		WorldPkSettlementResult result = service(port).settle(
			request(DEATH_ONE, 10, 20, 6_000L, TIME, ""));
		checkNone(result, message);
		check(port.lastVerifierOutcome == AtomicTransactionOutcome.UNKNOWN,
			"fixture must reach unresolved verifier outcome");
	}

	private static void testReplayLostCommitRemainsNonPublishable() {
		FakePort port = new FakePort();
		WorldPkSettlementService service = service(port);
		WorldPkSettlementRequest request = request(DEATH_ONE, 10, 20, 6_000L, TIME, "");
		checkApplied(service.settle(request), true, "fixture applies");
		int writes = port.totalWriteCalls();
		port.mode = SettlementMode.LOST_COMMIT_KEEP_AFTER;

		WorldPkSettlementResult replay = service.settle(request);
		check(replay.getStatus() == Status.REPLAY && replay.isSettled()
			&& !replay.isPublishable(), "verified read-only replay remains nonpublishable");
		check(port.totalWriteCalls() == writes && port.verifierStreakLoads == 0,
			"replay verifier checks exact event and performs no projection work");
	}

	private static void testCounterOverflowFailsClosed() {
		FakePort port = new FakePort();
		WorldPkStreak killer = streak(10, "Killer", Integer.MAX_VALUE,
			Integer.MAX_VALUE, Long.MAX_VALUE, TIME - 1L, TIME - 1L);
		port.seedStreak(killer);
		checkNone(service(port).settle(request(DEATH_ONE, 10, 20, 6_000L, TIME, "")),
			"counter overflow fails closed");
		check(port.getEvent(DEATH_ONE) == null && port.getStreak(20) == null,
			"overflow creates neither event nor victim projection");
		checkStreak(killer, port.getStreak(10), "overflow preserves killer projection");
	}

	private static void testConcurrentReplayMutatesStreaksOnce() throws Exception {
		FakePort port = new FakePort();
		WorldPkSettlementService service = service(port);
		WorldPkSettlementRequest request = request(DEATH_ONE, 10, 20, 6_000L, TIME, "");
		AtomicReference<WorldPkSettlementResult> first = new AtomicReference<>();
		AtomicReference<WorldPkSettlementResult> second = new AtomicReference<>();
		runConcurrent(() -> first.set(service.settle(request)),
			() -> second.set(service.settle(request)));

		int applied = (first.get().getStatus() == Status.APPLIED ? 1 : 0)
			+ (second.get().getStatus() == Status.APPLIED ? 1 : 0);
		int replays = (first.get().getStatus() == Status.REPLAY ? 1 : 0)
			+ (second.get().getStatus() == Status.REPLAY ? 1 : 0);
		check(applied == 1 && replays == 1 && port.events.size() == 1,
			"concurrent same-death calls produce one apply and one replay");
		check(port.getStreak(10).currentStreak == 1
			&& port.getStreak(10).qualifiedKills == 1L
			&& port.getStreak(20).currentStreak == 0,
			"concurrent replay mutates killer/victim projections exactly once");
	}

	private static void testConcurrentPairDeathsProduceOneQualifiedKill() throws Exception {
		FakePort port = new FakePort();
		WorldPkSettlementService service = service(port);
		AtomicReference<WorldPkSettlementResult> first = new AtomicReference<>();
		AtomicReference<WorldPkSettlementResult> second = new AtomicReference<>();
		runConcurrent(
			() -> first.set(service.settle(request(DEATH_ONE, 10, 20,
				6_000L, TIME, ""))),
			() -> second.set(service.settle(request(DEATH_TWO, 10, 20,
				6_000L, TIME + 1L, ""))));

		check(first.get().isNewEvent() && second.get().isNewEvent(),
			"different concurrent deaths are both durably audited");
		int qualified = (first.get().isQualified() ? 1 : 0)
			+ (second.get().isQualified() ? 1 : 0);
		check(qualified == 1 && port.getStreak(10).currentStreak == 1
			&& port.getStreak(10).qualifiedKills == 1L,
			"transactional pair cooldown permits only one concurrent qualified kill");
		check(port.events.size() == 2 && port.getStreak(20).currentStreak == 0,
			"both concurrent deaths reset the victim and remain auditable");
	}

	private static void runConcurrent(Runnable first, Runnable second) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread firstThread = concurrentThread(first, ready, start, failure);
		Thread secondThread = concurrentThread(second, ready, start, failure);
		firstThread.start();
		secondThread.start();
		ready.await();
		start.countDown();
		firstThread.join();
		secondThread.join();
		if (failure.get() != null) throw new AssertionError(failure.get());
	}

	private static Thread concurrentThread(Runnable action, CountDownLatch ready,
		CountDownLatch start, AtomicReference<Throwable> failure) {
		return new Thread(() -> {
			try {
				ready.countDown();
				start.await();
				action.run();
			} catch (Throwable throwable) {
				failure.compareAndSet(null, throwable);
			}
		});
	}

	private static WorldPkSettlementService service(FakePort port) {
		return new WorldPkSettlementService(port, true, "  LAUNCH-2026  ", LOOT_MINIMUM);
	}

	private static WorldPkSettlementRequest request(String deathId, int killerId,
		int victimId, long lootValue, long occurredAtMs, String rejectReason) {
		return new WorldPkSettlementRequest(deathId, killerId, 4_000_000_000L + killerId,
			killerId == 10 ? "Killer" : "Victim", victimId,
			4_000_000_000L + victimId, victimId == 20 ? "Victim" : "Killer",
			true, 7, lootValue, 12, occurredAtMs, rejectReason);
	}

	private static WorldPkStreak streak(int playerId, String playerName, int current,
		int best, long qualifiedKills, long lastQualifiedAtMs, long updatedAtMs) {
		WorldPkStreak streak = new WorldPkStreak();
		streak.seasonId = SEASON;
		streak.playerId = playerId;
		streak.playerName = playerName;
		streak.currentStreak = current;
		streak.bestStreak = best;
		streak.qualifiedKills = qualifiedKills;
		streak.lastQualifiedAtMs = lastQualifiedAtMs;
		streak.updatedAtMs = updatedAtMs;
		return streak;
	}

	private static WorldPkEvent previousQualified(String deathId, int killerId,
		int victimId, long occurredAtMs) {
		WorldPkSettlementRequest request = request(
			deathId, killerId, victimId, 6_000L, occurredAtMs, "");
		WorldPkEvent event = new WorldPkEvent();
		event.deathId = deathId;
		event.seasonId = SEASON;
		event.killerPlayerId = killerId;
		event.killerAccountId = request.getKillerAccountId();
		event.killerName = request.getKillerName();
		event.victimPlayerId = victimId;
		event.victimAccountId = request.getVictimAccountId();
		event.victimName = request.getVictimName();
		event.pairLowPlayerId = Math.min(killerId, victimId);
		event.pairHighPlayerId = Math.max(killerId, victimId);
		event.qualified = true;
		event.rejectReason = "";
		event.victimWasSkulled = true;
		event.victimDamage = 7;
		event.lootValue = 6_000L;
		event.streakAfter = 1;
		event.endedStreak = 0;
		event.wildernessLevel = 12;
		event.occurredAtMs = occurredAtMs;
		return event;
	}

	private static void checkApplied(WorldPkSettlementResult result, boolean qualified,
		String message) {
		check(result.getStatus() == Status.APPLIED && result.isSettled()
			&& result.isNewEvent() && result.isPublishable()
			&& result.isQualified() == qualified, message);
	}

	private static void checkNone(WorldPkSettlementResult result, String message) {
		check(result.getStatus() == Status.NOT_SETTLED && !result.isSettled()
			&& !result.isNewEvent() && !result.isPublishable(), message);
	}

	private static void checkStreak(WorldPkStreak expected, WorldPkStreak actual,
		String message) {
		check(sameStreak(expected, actual), message);
	}

	private static boolean sameStreak(WorldPkStreak first, WorldPkStreak second) {
		if (first == null || second == null) return first == second;
		return equal(first.seasonId, second.seasonId)
			&& first.playerId == second.playerId
			&& equal(first.playerName, second.playerName)
			&& first.currentStreak == second.currentStreak
			&& first.bestStreak == second.bestStreak
			&& first.qualifiedKills == second.qualifiedKills
			&& first.lastQualifiedAtMs == second.lastQualifiedAtMs
			&& first.updatedAtMs == second.updatedAtMs;
	}

	private static boolean equal(Object first, Object second) {
		return first == null ? second == null : first.equals(second);
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private enum SettlementMode {
		NORMAL,
		LOST_COMMIT_KEEP_AFTER,
		LOST_COMMIT_RESTORE_BEFORE,
		LOST_COMMIT_EVENT_ONLY,
		LOST_COMMIT_DRIFT_AFTER,
		LOST_COMMIT_DRIFT_BEFORE
	}

	private static final class FakePort implements SettlementPort {
		private final Map<String, WorldPkEvent> events = new LinkedHashMap<>();
		private final Map<String, WorldPkStreak> streaks = new LinkedHashMap<>();
		private SettlementMode mode = SettlementMode.NORMAL;
		private boolean transactionBodyActive;
		private boolean verifierActive;
		private boolean returnZeroForNoOpUpdate;
		private boolean returnZeroForEveryUpdate;
		private boolean awaitingExactReload;
		private int failOnTransactionStreakWrite = -1;
		private int transactionStreakWrites;
		private int atomicCount;
		private int pairQueryCount;
		private int pairQueriesInsideTransaction;
		private int eventInsertCalls;
		private int streakInsertCalls;
		private int streakUpdateCalls;
		private int streakReloadAfterZeroCount;
		private int verifierStreakLoads;
		private AtomicTransactionOutcome lastVerifierOutcome;

		@Override
		public synchronized AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			atomicCount++;
			transactionStreakWrites = 0;
			Map<String, WorldPkEvent> eventsBefore = copyEvents(events);
			Map<String, WorldPkStreak> streaksBefore = copyStreaks(streaks);
			try {
				transactionBodyActive = true;
				body.run();
			} catch (Exception ex) {
				restore(eventsBefore, streaksBefore);
				return AtomicTransactionOutcome.ROLLED_BACK;
			} finally {
				transactionBodyActive = false;
			}

			if (mode == SettlementMode.NORMAL) {
				return AtomicTransactionOutcome.COMMITTED;
			}

			WorldPkEvent insertedEvent = findInsertedEvent(eventsBefore);
			if (mode == SettlementMode.LOST_COMMIT_RESTORE_BEFORE) {
				restore(eventsBefore, streaksBefore);
			} else if (mode == SettlementMode.LOST_COMMIT_EVENT_ONLY) {
				Map<String, WorldPkEvent> eventsAfter = copyEvents(events);
				restore(eventsBefore, streaksBefore);
				events.clear();
				events.putAll(eventsAfter);
			} else if (mode == SettlementMode.LOST_COMMIT_DRIFT_AFTER) {
				driftVictim(insertedEvent);
			} else if (mode == SettlementMode.LOST_COMMIT_DRIFT_BEFORE) {
				restore(eventsBefore, streaksBefore);
				driftVictim(insertedEvent);
			}

			try {
				verifierActive = true;
				lastVerifierOutcome = verifier.verify();
				return lastVerifierOutcome;
			} catch (Exception ex) {
				lastVerifierOutcome = AtomicTransactionOutcome.UNKNOWN;
				return lastVerifierOutcome;
			} finally {
				verifierActive = false;
			}
		}

		@Override
		public synchronized WorldPkEvent loadEvent(String deathId) {
			return copy(events.get(deathId));
		}

		@Override
		public synchronized Long loadLastQualifiedPairTime(String seasonId,
			int pairLowPlayerId, int pairHighPlayerId) {
			pairQueryCount++;
			if (transactionBodyActive) pairQueriesInsideTransaction++;
			Long latest = null;
			for (WorldPkEvent event : events.values()) {
				if (event.qualified && equal(seasonId, event.seasonId)
					&& event.pairLowPlayerId == pairLowPlayerId
					&& event.pairHighPlayerId == pairHighPlayerId
					&& (latest == null || event.occurredAtMs > latest)) {
					latest = event.occurredAtMs;
				}
			}
			return latest;
		}

		@Override
		public synchronized WorldPkStreak loadStreak(String seasonId, int playerId) {
			if (verifierActive) verifierStreakLoads++;
			if (transactionBodyActive && awaitingExactReload) {
				streakReloadAfterZeroCount++;
				awaitingExactReload = false;
			}
			return copy(streaks.get(streakKey(seasonId, playerId)));
		}

		@Override
		public synchronized int insertEvent(WorldPkEvent event) {
			eventInsertCalls++;
			if (events.containsKey(event.deathId)) return 0;
			events.put(event.deathId, copy(event));
			return 1;
		}

		@Override
		public synchronized int insertStreak(WorldPkStreak streak) {
			streakInsertCalls++;
			beforeStreakWrite();
			String key = streakKey(streak.seasonId, streak.playerId);
			if (streaks.containsKey(key)) return 0;
			streaks.put(key, copy(streak));
			return 1;
		}

		@Override
		public synchronized int updateStreak(WorldPkStreak streak) {
			streakUpdateCalls++;
			beforeStreakWrite();
			String key = streakKey(streak.seasonId, streak.playerId);
			WorldPkStreak existing = streaks.get(key);
			if (existing == null) return 0;
			if (returnZeroForEveryUpdate) {
				awaitingExactReload = true;
				return 0;
			}
			if (returnZeroForNoOpUpdate && sameStreak(existing, streak)) {
				awaitingExactReload = true;
				return 0;
			}
			streaks.put(key, copy(streak));
			return 1;
		}

		private void beforeStreakWrite() {
			transactionStreakWrites++;
			if (failOnTransactionStreakWrite == transactionStreakWrites) {
				throw new IllegalStateException("injected streak write failure");
			}
		}

		private void driftVictim(WorldPkEvent insertedEvent) {
			if (insertedEvent == null) return;
			String key = streakKey(insertedEvent.seasonId, insertedEvent.victimPlayerId);
			WorldPkStreak victim = streaks.get(key);
			if (victim == null) {
				victim = streak(insertedEvent.victimPlayerId, insertedEvent.victimName,
					1, 1, 1L, insertedEvent.occurredAtMs,
					insertedEvent.occurredAtMs);
				streaks.put(key, victim);
			} else {
				victim.bestStreak++;
			}
		}

		private WorldPkEvent findInsertedEvent(Map<String, WorldPkEvent> before) {
			for (Map.Entry<String, WorldPkEvent> entry : events.entrySet()) {
				if (!before.containsKey(entry.getKey())) return copy(entry.getValue());
			}
			return null;
		}

		private void restore(Map<String, WorldPkEvent> eventsBefore,
			Map<String, WorldPkStreak> streaksBefore) {
			events.clear();
			events.putAll(copyEvents(eventsBefore));
			streaks.clear();
			streaks.putAll(copyStreaks(streaksBefore));
		}

		private void seedEvent(WorldPkEvent event) {
			events.put(event.deathId, copy(event));
		}

		private void seedStreak(WorldPkStreak streak) {
			streaks.put(streakKey(streak.seasonId, streak.playerId), copy(streak));
		}

		private WorldPkEvent getEvent(String deathId) {
			return copy(events.get(deathId));
		}

		private WorldPkStreak getStreak(int playerId) {
			return copy(streaks.get(streakKey(SEASON, playerId)));
		}

		private int totalWriteCalls() {
			return eventInsertCalls + streakInsertCalls + streakUpdateCalls;
		}

		private static String streakKey(String seasonId, int playerId) {
			return seasonId + "\n" + playerId;
		}

		private static Map<String, WorldPkEvent> copyEvents(
			Map<String, WorldPkEvent> source) {
			Map<String, WorldPkEvent> copy = new LinkedHashMap<>();
			for (Map.Entry<String, WorldPkEvent> entry : source.entrySet()) {
				copy.put(entry.getKey(), copy(entry.getValue()));
			}
			return copy;
		}

		private static Map<String, WorldPkStreak> copyStreaks(
			Map<String, WorldPkStreak> source) {
			Map<String, WorldPkStreak> copy = new LinkedHashMap<>();
			for (Map.Entry<String, WorldPkStreak> entry : source.entrySet()) {
				copy.put(entry.getKey(), copy(entry.getValue()));
			}
			return copy;
		}

		private static WorldPkEvent copy(WorldPkEvent source) {
			if (source == null) return null;
			WorldPkEvent copy = new WorldPkEvent();
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
			WorldPkStreak copy = new WorldPkStreak();
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
	}
}
