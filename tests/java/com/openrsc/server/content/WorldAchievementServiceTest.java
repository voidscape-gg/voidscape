package com.openrsc.server.content;

import com.openrsc.server.content.WorldAchievementService.RecordPort;
import com.openrsc.server.content.WorldAchievementService.SkillClaimResult;
import com.openrsc.server.content.WorldAchievementService.TransactionBody;
import com.openrsc.server.content.WorldAchievementService.TransactionVerifier;
import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.struct.WorldAchievementRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Executable fault-injection tests for first-skill world records. */
public final class WorldAchievementServiceTest {
	private static final long CLAIMED_AT = 1_789_000_000_000L;

	private WorldAchievementServiceTest() {
	}

	public static void main(String[] args) throws Exception {
		testDisabledElevatedInvalidAndNoCrossing();
		testExactThresholdBoundaries();
		testMultiThresholdJumpAndImmutableResult();
		testCompetitorReplayAndConcurrencyHaveOneWinner();
		testSeasonNormalizationAndIsolation();
		testBodyFailureRollsBackEveryTentativeClaim();
		testLostCommitVerifiesCommittedAndRolledBack();
		testLostCommitMixedOrMalformedIsUnknown();
		testMalformedExistingRecordIsNeverOwned();
		System.out.println("World first-skill achievement service tests passed.");
	}

	private static void testDisabledElevatedInvalidAndNoCrossing() {
		FakePort port = new FakePort();
		WorldAchievementService disabled = service(port, false, "launch-2026");
		checkNone(disabled.claimFirstSkillLevels(1, "Player", false, 0, 79, 80),
			"disabled service rejects a crossing");

		WorldAchievementService enabled = service(port, true, "launch-2026");
		checkNone(enabled.claimFirstSkillLevels(1, "Player", true, 0, 79, 80),
			"elevated player cannot win");
		checkNone(enabled.claimFirstSkillLevels(0, "Player", false, 0, 79, 80),
			"non-positive player id cannot win");
		checkNone(enabled.claimFirstSkillLevels(1, " ", false, 0, 79, 80),
			"blank player name cannot win");
		checkNone(enabled.claimFirstSkillLevels(1, "NameIsTooLong", false, 0, 79, 80),
			"overlong player name cannot win");
		checkNone(enabled.claimFirstSkillLevels(1, "Player", false, -1, 79, 80),
			"negative skill cannot win");
		checkNone(enabled.claimFirstSkillLevels(1, "Player", false, 0, 80, 80),
			"equal levels are not a crossing");
		checkNone(enabled.claimFirstSkillLevels(1, "Player", false, 0, 81, 80),
			"decreasing levels are not a crossing");
		checkNone(enabled.claimFirstSkillLevels(1, "Player", false, 0, 80, 89),
			"progress between thresholds claims nothing");

		WorldAchievementService invalidSeason = service(port, true, "launch 2026!");
		checkNone(invalidSeason.claimFirstSkillLevels(1, "Player", false, 0, 79, 80),
			"invalid season fails closed");
		check(port.atomicCount == 0, "all rejected inputs avoid opening a transaction");
	}

	private static void testExactThresholdBoundaries() {
		checkLevels(service(new FakePort(), true, "launch-2026")
			.claimFirstSkillLevels(1, "Eighty", false, 0, 79, 80), 80);
		checkLevels(service(new FakePort(), true, "launch-2026")
			.claimFirstSkillLevels(2, "Ninety", false, 1, 89, 90), 90);
		checkLevels(service(new FakePort(), true, "launch-2026")
			.claimFirstSkillLevels(3, "Maxed", false, 2, 98, 99), 99);
		checkNone(service(new FakePort(), true, "launch-2026")
			.claimFirstSkillLevels(4, "Already", false, 3, 80, 81),
			"starting at a threshold does not reclaim it");
	}

	private static void testMultiThresholdJumpAndImmutableResult() {
		FakePort port = new FakePort();
		SkillClaimResult result = service(port, true, "launch-2026")
			.claimFirstSkillLevels(7, "Skiller", false, 4, 79, 99);

		checkLevels(result, 80, 90, 99);
		check(result.getHighestClaimedLevel() == 99, "multi-crossing exposes highest claim");
		check(port.atomicCount == 1 && port.recordCount() == 3,
			"all crossed thresholds share one transaction");
		for (int level : Arrays.asList(80, 90, 99)) {
			WorldAchievementRecord record = port.get("launch-2026", "first:skill:4:" + level);
			check(record != null, "crossed threshold has durable record " + level);
			check("first_skill".equals(record.recordType) && record.subjectId == 4
				&& record.value == level && "skill_level".equals(record.source)
				&& record.sourceEventKey == null && record.claimedAtMs == CLAIMED_AT,
				"record fields match the stable first-skill contract");
			check(record.detail.length() <= 255
				&& !record.detail.toLowerCase().contains("account")
				&& !record.detail.toLowerCase().contains("ip="),
				"record detail is bounded and nonprivate");
		}
		try {
			result.getClaimedLevels().add(100);
			throw new AssertionError("claimed-level result must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
	}

	private static void testCompetitorReplayAndConcurrencyHaveOneWinner()
		throws Exception {
		FakePort sequential = new FakePort();
		WorldAchievementService service = service(sequential, true, "launch-2026");
		checkLevels(service.claimFirstSkillLevels(10, "Winner", false, 5, 79, 80), 80);
		checkNone(service.claimFirstSkillLevels(11, "Rival", false, 5, 79, 80),
			"competitor cannot replace the first claimant");
		checkNone(service.claimFirstSkillLevels(10, "Winner", false, 5, 79, 80),
			"winner replay does not republish a claim");
		check(sequential.get("launch-2026", "first:skill:5:80").playerId == 10,
			"durable winner remains unchanged");

		FakePort concurrent = new FakePort();
		WorldAchievementService concurrentService =
			service(concurrent, true, "launch-2026");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<SkillClaimResult> first = new AtomicReference<>();
		AtomicReference<SkillClaimResult> second = new AtomicReference<>();
		Thread firstThread = contender(concurrentService, ready, start, first, 20, "First");
		Thread secondThread = contender(concurrentService, ready, start, second, 21, "Second");
		firstThread.start();
		secondThread.start();
		ready.await();
		start.countDown();
		firstThread.join();
		secondThread.join();

		int winners = (first.get().hasClaims() ? 1 : 0) + (second.get().hasClaims() ? 1 : 0);
		check(winners == 1 && concurrent.recordCount() == 1,
			"serialized concurrent contenders produce exactly one winner");
	}

	private static Thread contender(WorldAchievementService service, CountDownLatch ready,
		CountDownLatch start, AtomicReference<SkillClaimResult> result, int playerId,
		String playerName) {
		return new Thread(() -> {
			try {
				ready.countDown();
				start.await();
				result.set(service.claimFirstSkillLevels(
					playerId, playerName, false, 6, 79, 80));
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new AssertionError(ex);
			}
		});
	}

	private static void testSeasonNormalizationAndIsolation() {
		FakePort port = new FakePort();
		WorldAchievementService launch = service(port, true, "  LAUNCH-2026  ");
		WorldAchievementService next = service(port, true, "launch-2027");

		checkLevels(launch.claimFirstSkillLevels(30, "Launch", false, 7, 79, 80), 80);
		checkLevels(next.claimFirstSkillLevels(31, "Next", false, 7, 79, 80), 80);
		check(port.get("launch-2026", "first:skill:7:80").playerId == 30
			&& port.get("launch-2027", "first:skill:7:80").playerId == 31,
			"normalized seasons isolate otherwise identical record keys");
	}

	private static void testBodyFailureRollsBackEveryTentativeClaim() {
		FakePort port = new FakePort();
		port.failAfterSuccessfulInserts = 1;
		SkillClaimResult result = service(port, true, "launch-2026")
			.claimFirstSkillLevels(40, "Rollback", false, 8, 79, 99);

		checkNone(result, "body failure publishes no tentative claims");
		check(port.recordCount() == 0, "body failure rolls back every record in the batch");
		check(port.atomicCount == 1, "body failure occurs inside one transaction");
	}

	private static void testLostCommitVerifiesCommittedAndRolledBack() {
		FakePort committed = new FakePort();
		committed.mode = SettlementMode.LOST_COMMIT_COMMITTED;
		SkillClaimResult committedResult = service(committed, true, "launch-2026")
			.claimFirstSkillLevels(50, "Committed", false, 9, 79, 99);
		checkLevels(committedResult, 80, 90, 99);
		check(committed.verifierCount == 1 && committed.loadCount == 3,
			"lost acknowledgement verifies every inserted record exactly once");

		FakePort rolledBack = new FakePort();
		rolledBack.mode = SettlementMode.LOST_COMMIT_ROLLED_BACK;
		SkillClaimResult rolledBackResult = service(rolledBack, true, "launch-2026")
			.claimFirstSkillLevels(51, "Rolledback", false, 10, 79, 99);
		checkNone(rolledBackResult, "verified rollback publishes no claim");
		check(rolledBack.verifierCount == 1 && rolledBack.loadCount == 3
			&& rolledBack.recordCount() == 0,
			"all-absent verifier result is a confirmed rollback");
	}

	private static void testLostCommitMixedOrMalformedIsUnknown() {
		FakePort mixed = new FakePort();
		mixed.mode = SettlementMode.LOST_COMMIT_MIXED;
		SkillClaimResult mixedResult = service(mixed, true, "launch-2026")
			.claimFirstSkillLevels(60, "Mixed", false, 11, 79, 99);
		checkNone(mixedResult, "mixed durable rows never publish claims");
		check(mixed.verifierCount == 1 && mixed.recordCount() == 2,
			"mixed verifier fixture leaves an unresolved partial state");

		FakePort malformed = new FakePort();
		malformed.mode = SettlementMode.LOST_COMMIT_MALFORMED;
		SkillClaimResult malformedResult = service(malformed, true, "launch-2026")
			.claimFirstSkillLevels(61, "Malformed", false, 12, 79, 80);
		checkNone(malformedResult, "mismatched durable claimant is never accepted");
		check(malformed.verifierCount == 1,
			"malformed durable row is evaluated by the uncertain-commit verifier");
	}

	private static void testMalformedExistingRecordIsNeverOwned() {
		FakePort port = new FakePort();
		WorldAchievementRecord malformed = new WorldAchievementRecord();
		malformed.seasonId = "launch-2026";
		malformed.recordKey = "first:skill:13:80";
		malformed.recordType = "first_skill";
		malformed.playerId = 71;
		malformed.playerName = "Other";
		malformed.subjectId = 999;
		malformed.value = 80;
		malformed.source = "skill_level";
		malformed.claimedAtMs = CLAIMED_AT;
		malformed.detail = "wrong subject";
		port.seed(malformed);

		SkillClaimResult result = service(port, true, "launch-2026")
			.claimFirstSkillLevels(70, "Expected", false, 13, 79, 80);
		checkNone(result, "insert-zero existing row is never treated as this call's claim");
		check(port.get("launch-2026", "first:skill:13:80").playerId == 71,
			"existing malformed row remains owned by its durable claimant");
	}

	private static WorldAchievementService service(FakePort port, boolean enabled,
		String seasonId) {
		return new WorldAchievementService(port, enabled, seasonId, () -> CLAIMED_AT);
	}

	private static void checkLevels(SkillClaimResult result, Integer... expected) {
		List<Integer> levels = Arrays.asList(expected);
		check(result.getClaimedLevels().equals(levels),
			"expected claimed levels " + levels + " but got " + result.getClaimedLevels());
		check(result.hasClaims() == !levels.isEmpty(), "claim presence matches level list");
		int highest = levels.isEmpty() ? 0 : levels.get(levels.size() - 1);
		check(result.getHighestClaimedLevel() == highest, "highest claim matches ordered list");
	}

	private static void checkNone(SkillClaimResult result, String message) {
		check(!result.hasClaims() && result.getClaimedLevels().isEmpty()
			&& result.getHighestClaimedLevel() == 0, message);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private enum SettlementMode {
		NORMAL,
		LOST_COMMIT_COMMITTED,
		LOST_COMMIT_ROLLED_BACK,
		LOST_COMMIT_MIXED,
		LOST_COMMIT_MALFORMED
	}

	private static final class FakePort implements RecordPort {
		private final Map<String, WorldAchievementRecord> records = new LinkedHashMap<>();
		private SettlementMode mode = SettlementMode.NORMAL;
		private int failAfterSuccessfulInserts = -1;
		private int transactionInsertCount;
		private int atomicCount;
		private int verifierCount;
		private int loadCount;

		@Override
		public synchronized AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			atomicCount++;
			transactionInsertCount = 0;
			Map<String, WorldAchievementRecord> before = copyRecords(records);
			try {
				body.run();
			} catch (Exception ex) {
				restore(before);
				return AtomicTransactionOutcome.ROLLED_BACK;
			}

			if (mode == SettlementMode.NORMAL) {
				return AtomicTransactionOutcome.COMMITTED;
			}
			if (mode == SettlementMode.LOST_COMMIT_ROLLED_BACK) {
				restore(before);
			} else if (mode == SettlementMode.LOST_COMMIT_MIXED) {
				for (String key : new ArrayList<>(records.keySet())) {
					if (!before.containsKey(key)) {
						records.remove(key);
						break;
					}
				}
			} else if (mode == SettlementMode.LOST_COMMIT_MALFORMED) {
				for (Map.Entry<String, WorldAchievementRecord> entry : records.entrySet()) {
					if (!before.containsKey(entry.getKey())) {
						entry.getValue().playerId++;
						break;
					}
				}
			}

			verifierCount++;
			try {
				return verifier.verify();
			} catch (Exception ex) {
				return AtomicTransactionOutcome.UNKNOWN;
			}
		}

		@Override
		public synchronized int insertRecord(WorldAchievementRecord record) {
			if (failAfterSuccessfulInserts >= 0
				&& transactionInsertCount >= failAfterSuccessfulInserts) {
				throw new IllegalStateException("injected record insert failure");
			}
			String key = key(record.seasonId, record.recordKey);
			if (records.containsKey(key)) {
				return 0;
			}
			records.put(key, copy(record));
			transactionInsertCount++;
			return 1;
		}

		@Override
		public synchronized WorldAchievementRecord loadRecord(String seasonId,
			String recordKey) {
			loadCount++;
			WorldAchievementRecord record = records.get(key(seasonId, recordKey));
			return record == null ? null : copy(record);
		}

		private synchronized void seed(WorldAchievementRecord record) {
			records.put(key(record.seasonId, record.recordKey), copy(record));
		}

		private synchronized WorldAchievementRecord get(String seasonId, String recordKey) {
			WorldAchievementRecord record = records.get(key(seasonId, recordKey));
			return record == null ? null : copy(record);
		}

		private synchronized int recordCount() {
			return records.size();
		}

		private void restore(Map<String, WorldAchievementRecord> snapshot) {
			records.clear();
			records.putAll(copyRecords(snapshot));
		}

		private static Map<String, WorldAchievementRecord> copyRecords(
			Map<String, WorldAchievementRecord> source) {
			Map<String, WorldAchievementRecord> copy = new LinkedHashMap<>();
			for (Map.Entry<String, WorldAchievementRecord> entry : source.entrySet()) {
				copy.put(entry.getKey(), copy(entry.getValue()));
			}
			return copy;
		}

		private static WorldAchievementRecord copy(WorldAchievementRecord source) {
			WorldAchievementRecord copy = new WorldAchievementRecord();
			copy.seasonId = source.seasonId;
			copy.recordKey = source.recordKey;
			copy.recordType = source.recordType;
			copy.playerId = source.playerId;
			copy.playerName = source.playerName;
			copy.subjectId = source.subjectId;
			copy.value = source.value;
			copy.source = source.source;
			copy.sourceEventKey = source.sourceEventKey;
			copy.claimedAtMs = source.claimedAtMs;
			copy.detail = source.detail;
			return copy;
		}

		private static String key(String seasonId, String recordKey) {
			return seasonId + "\n" + recordKey;
		}
	}
}
