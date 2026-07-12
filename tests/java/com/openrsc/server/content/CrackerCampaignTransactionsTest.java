package com.openrsc.server.content;

import com.openrsc.server.content.CrackerCampaignService.SetResult;
import com.openrsc.server.content.CrackerCampaignService.SetStatus;
import com.openrsc.server.content.CrackerCampaignService.State;
import com.openrsc.server.content.CrackerCampaignTransactions.AddedCracker;
import com.openrsc.server.content.CrackerCampaignTransactions.AwardPort;
import com.openrsc.server.content.CrackerCampaignTransactions.AwardResult;
import com.openrsc.server.content.CrackerCampaignTransactions.AwardStatus;
import com.openrsc.server.content.CrackerCampaignTransactions.WorldFirstClaimant;
import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.struct.WorldAchievementRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable fault-injection tests for the durable cracker-campaign pool.
 * No test framework is used so the repository's Java 8 toolchain can run it.
 */
public final class CrackerCampaignTransactionsTest {
	private CrackerCampaignTransactionsTest() {
	}

	public static void main(String[] args) throws Exception {
		testMissingPoolLoadsAsInactiveZero();
		testCommittedSetReportsExactTransition();
		testZeroDisablesPoolDurably();
		testIdempotentSetDoesNotRewritePool();
		testNegativeSetIsRejectedBeforeTransaction();
		testLoadAndSaveFaultsRollbackWithoutGuessing();
		testLostCommitAcknowledgementIsReconciled();
		testUnresolvedOutcomeIsNotPublished();
		testServiceLoadFailureRetriesAndNegativeStateFailsClosed();
		testEnabledAndDisabledStateSemantics();
		testRestartReloadsDurablePool();
		testConcurrentSettersSerializeAgainstDurableState();
		testAwardCommitsExactCrackerPoolAndProvenanceTogether();
		testFirstCampaignCrackerRecordIsExactAndAtomic();
		testLaterAwardAndExistingClaimAreNotNewWorldFirsts();
		testInsertZeroRequiresCanonicalExistingRecord();
		testWorldFirstFeatureAndSeasonGates();
		testEmptyAndCapacityRejectionsMutateNothing();
		testAwardFaultsRollbackEveryDurableComponent();
		testAwardLifecycleFenceAndExactCompensation();
		testAwardLostCommitAcknowledgementIsReconciled();
		testWorldFirstLostCommitRequiresExactAfterOrBeforeState();
		testUnresolvedAwardOutcomeQuarantinesWithoutCompensation();
		testConcurrentAwardsCannotOverspendPool();
		testChanceRollersReceiveExactConfiguredDenominators();
		testCampaignStatePublishesOnlySettledAuthority();
		testCampaignStateSetAndRangeSemantics();
		testAwardInputValidation();
		System.out.println("Cracker-campaign pool and gameplay-award transaction tests passed.");
	}

	private static void testMissingPoolLoadsAsInactiveZero() {
		FakePoolPort port = new FakePoolPort(null);
		CrackerCampaignService service = new CrackerCampaignService(port, true);

		State state = service.getState();

		check(state.isEnabled(), "campaign feature flag remains visible");
		check(state.isLoaded(), "missing pool row is a valid loaded state");
		check(state.getRemaining() == 0, "missing pool row means zero remaining");
		check(!state.isActive(), "missing pool row cannot activate awards");
		check(port.loadCount == 1, "first state read lazily loads durable pool once");
		service.getState();
		check(port.loadCount == 1, "loaded state is cached within one world process");
	}

	private static void testCommittedSetReportsExactTransition() {
		FakePoolPort port = new FakePoolPort(17);
		CrackerCampaignService service = new CrackerCampaignService(port, true);

		SetResult result = service.setRemaining(1000);

		check(result.getStatus() == SetStatus.UPDATED, "changed pool reports updated");
		check(result.isSuccessful(), "committed pool update reports success");
		check(result.getPreviousRemaining() == 17, "update reports exact previous count");
		check(result.getRemaining() == 1000, "update reports exact intended count");
		check(port.durableRemaining == 1000, "updated count is durable");
		check(port.saveCount == 1 && port.atomicCount == 1,
			"changed pool is written exactly once inside one transaction");
		check(service.getState().getRemaining() == 1000,
			"service publishes committed count after the transaction");
	}

	private static void testZeroDisablesPoolDurably() {
		FakePoolPort port = new FakePoolPort(5);
		CrackerCampaignService service = new CrackerCampaignService(port, true);

		SetResult result = service.setRemaining(0);

		check(result.getStatus() == SetStatus.UPDATED, "zero is a valid pool update");
		check(result.getPreviousRemaining() == 5 && result.getRemaining() == 0,
			"zero update reports exact old and new values");
		check(port.durableRemaining == 0, "zero is persisted rather than deleting state");
		check(!service.getState().isActive(), "zero turns campaign awards off");
	}

	private static void testIdempotentSetDoesNotRewritePool() {
		FakePoolPort port = new FakePoolPort(1000);
		CrackerCampaignService service = new CrackerCampaignService(port, true);

		SetResult result = service.setRemaining(1000);

		check(result.getStatus() == SetStatus.UNCHANGED, "same count reports unchanged");
		check(result.isSuccessful(), "idempotent set remains successful");
		check(result.getPreviousRemaining() == 1000 && result.getRemaining() == 1000,
			"idempotent set preserves exact audit values");
		check(port.saveCount == 0, "idempotent set does not churn global cache rows");
		check(port.atomicCount == 1, "idempotent comparison still owns a transaction");
	}

	private static void testNegativeSetIsRejectedBeforeTransaction() {
		FakePoolPort port = new FakePoolPort(9);

		expectIllegalArgument(() -> CrackerCampaignTransactions.setRemaining(port, -1),
			"coordinator rejects a negative pool");
		expectIllegalArgument(() -> new CrackerCampaignService(port, true).setRemaining(-1),
			"service rejects a negative pool");
		expectIllegalArgument(() -> CrackerCampaignTransactions.setRemaining(
			(CrackerCampaignTransactions.PoolPort) null, 1), "coordinator rejects a missing port");

		check(port.atomicCount == 0 && port.saveCount == 0 && port.durableRemaining == 9,
			"invalid requests mutate no transaction or durable state");
	}

	private static void testLoadAndSaveFaultsRollbackWithoutGuessing() {
		FakePoolPort loadFailure = new FakePoolPort(12);
		loadFailure.failLoadsRemaining = 1;
		CrackerCampaignTransactions.MutationResult loadResult =
			CrackerCampaignTransactions.setRemaining(loadFailure, 40);
		check(loadResult.getOutcome() == AtomicTransactionOutcome.ROLLED_BACK,
			"load failure is a confirmed rollback");
		check(!loadResult.isPreviousKnown(), "failed load does not invent a previous value");
		check(loadResult.getPreviousRemaining() == CrackerCampaignService.UNKNOWN_REMAINING
			&& loadResult.getRemaining() == CrackerCampaignService.UNKNOWN_REMAINING,
			"failed load reports unknown audit values");
		check(loadFailure.durableRemaining == 12 && loadFailure.saveCount == 0,
			"load failure leaves durable pool untouched");

		FakePoolPort saveFailure = new FakePoolPort(12);
		saveFailure.failSavesRemaining = 1;
		CrackerCampaignService service = new CrackerCampaignService(saveFailure, true);
		SetResult saveResult = service.setRemaining(40);
		check(saveResult.getStatus() == SetStatus.FAILED,
			"save failure is distinguished from an uncertain commit");
		check(saveResult.getPreviousRemaining() == 12 && saveResult.getRemaining() == 12,
			"rolled-back save reports the known durable previous value");
		check(saveFailure.durableRemaining == 12,
			"save failure restores the exact durable previous count");
		check(service.getState().getRemaining() == 12,
			"service retains confirmed previous count after rollback");
	}

	private static void testLostCommitAcknowledgementIsReconciled() {
		FakePoolPort committed = new FakePoolPort(21);
		committed.commitAcknowledgementLost = true;
		CrackerCampaignService committedService = new CrackerCampaignService(committed, true);

		SetResult committedResult = committedService.setRemaining(22);

		check(committedResult.getStatus() == SetStatus.UPDATED,
			"lost COMMIT acknowledgement reconciles committed pool state");
		check(committed.durableRemaining == 22,
			"reconciled commit retains intended durable count");
		check(committed.verifierCount == 1,
			"lost acknowledgement runs one operation-specific verifier");

		FakePoolPort rolledBack = new FakePoolPort(31);
		rolledBack.commitAcknowledgementLost = true;
		rolledBack.commitWasDurable = false;
		CrackerCampaignService rolledBackService = new CrackerCampaignService(rolledBack, true);

		SetResult rolledBackResult = rolledBackService.setRemaining(32);

		check(rolledBackResult.getStatus() == SetStatus.FAILED,
			"lost COMMIT acknowledgement reconciles rolled-back pool state");
		check(rolledBackResult.getPreviousRemaining() == 31
			&& rolledBackResult.getRemaining() == 31,
			"reconciled rollback reports exact retained count");
		check(rolledBack.durableRemaining == 31 && rolledBack.verifierCount == 1,
			"reconciled rollback keeps previous durable count");
	}

	private static void testUnresolvedOutcomeIsNotPublished() {
		FakePoolPort port = new FakePoolPort(40);
		port.forceUnknownOutcome = true;
		CrackerCampaignService service = new CrackerCampaignService(port, true);

		SetResult result = service.setRemaining(41);

		check(result.getStatus() == SetStatus.UNCERTAIN,
			"unresolved transaction is reported as uncertain");
		check(result.getPreviousRemaining() == 40,
			"uncertain result retains the known pre-transaction value for audit");
		check(result.getRemaining() == CrackerCampaignService.UNKNOWN_REMAINING,
			"uncertain result never claims the intended value was committed");
		check(port.durableRemaining == 41,
			"fault fixture models a commit whose acknowledgement cannot be resolved");

		State reloaded = service.getState();
		check(reloaded.isLoaded() && reloaded.getRemaining() == 41,
			"next status read resolves uncertainty from authoritative durable state");
	}

	private static void testServiceLoadFailureRetriesAndNegativeStateFailsClosed() {
		FakePoolPort transientFailure = new FakePoolPort(7);
		transientFailure.failLoadsRemaining = 1;
		CrackerCampaignService retrying = new CrackerCampaignService(transientFailure, true);

		State failed = retrying.getState();
		check(!failed.isLoaded() && failed.getRemaining() == CrackerCampaignService.UNKNOWN_REMAINING,
			"database load failure remains visibly unknown");
		State recovered = retrying.getState();
		check(recovered.isLoaded() && recovered.getRemaining() == 7 && recovered.isActive(),
			"next status call retries and recovers durable state");

		FakePoolPort corrupt = new FakePoolPort(-5);
		CrackerCampaignService failClosed = new CrackerCampaignService(corrupt, true);
		State corruptState = failClosed.getState();
		check(!corruptState.isLoaded() && !corruptState.isActive(),
			"negative durable pool fails closed instead of activating awards");
		corrupt.durableRemaining = 3;
		State repaired = failClosed.getState();
		check(repaired.isLoaded() && repaired.getRemaining() == 3 && repaired.isActive(),
			"operator-repaired durable pool can be loaded on retry");
	}

	private static void testEnabledAndDisabledStateSemantics() {
		FakePoolPort enabledPort = new FakePoolPort(4);
		State enabled = new CrackerCampaignService(enabledPort, true).getState();
		check(enabled.isEnabled() && enabled.isLoaded() && enabled.isActive(),
			"positive loaded pool is active only when feature is enabled");

		FakePoolPort disabledPort = new FakePoolPort(4);
		State disabled = new CrackerCampaignService(disabledPort, false).getState();
		check(!disabled.isEnabled() && disabled.isLoaded() && disabled.getRemaining() == 4,
			"disabled feature still reports persisted operator state");
		check(!disabled.isActive(), "disabled feature cannot activate a positive pool");
	}

	private static void testRestartReloadsDurablePool() {
		FakePoolPort durableLedger = new FakePoolPort(8);
		CrackerCampaignService firstWorld = new CrackerCampaignService(durableLedger, true);
		check(firstWorld.getState().getRemaining() == 8, "first world loads initial pool");
		check(firstWorld.setRemaining(6).isSuccessful(), "first world commits updated pool");

		CrackerCampaignService restartedWorld = new CrackerCampaignService(durableLedger, true);
		State restarted = restartedWorld.getState();
		check(restarted.isLoaded() && restarted.getRemaining() == 6 && restarted.isActive(),
			"new world process restores exact durable remaining count");

		durableLedger.durableRemaining = 5;
		check(firstWorld.getState().getRemaining() == 6,
			"old process retains its last committed snapshot");
		CrackerCampaignService secondRestart = new CrackerCampaignService(durableLedger, true);
		check(secondRestart.getState().getRemaining() == 5,
			"subsequent restart always reloads current durable authority");
	}

	private static void testConcurrentSettersSerializeAgainstDurableState() throws Exception {
		FakePoolPort port = new FakePoolPort(0);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<CrackerCampaignTransactions.MutationResult> toHundred =
			new AtomicReference<>();
		AtomicReference<CrackerCampaignTransactions.MutationResult> toTwoHundred =
			new AtomicReference<>();
		Thread first = new Thread(() -> runSet(start, port, 100, toHundred),
			"cracker-pool-set-100");
		Thread second = new Thread(() -> runSet(start, port, 200, toTwoHundred),
			"cracker-pool-set-200");
		first.start();
		second.start();
		start.countDown();
		first.join();
		second.join();

		CrackerCampaignTransactions.MutationResult hundred = toHundred.get();
		CrackerCampaignTransactions.MutationResult twoHundred = toTwoHundred.get();
		check(hundred != null && twoHundred != null,
			"both concurrent setters return a result");
		check(hundred.getOutcome() == AtomicTransactionOutcome.COMMITTED
			&& twoHundred.getOutcome() == AtomicTransactionOutcome.COMMITTED,
			"both serialized owner updates commit cleanly");
		check(port.atomicCount == 2 && port.saveCount == 2,
			"concurrent changed values each own one serialized transaction");

		if (port.durableRemaining == 100) {
			check(twoHundred.getPreviousRemaining() == 0
				&& hundred.getPreviousRemaining() == 200,
				"final 100 reflects serialized 0 -> 200 -> 100 transitions");
		} else {
			check(port.durableRemaining == 200, "final count must be one intended value");
			check(hundred.getPreviousRemaining() == 0
				&& twoHundred.getPreviousRemaining() == 100,
				"final 200 reflects serialized 0 -> 100 -> 200 transitions");
		}
	}

	private static void testAwardCommitsExactCrackerPoolAndProvenanceTogether() {
		FakeAwardPort port = new FakeAwardPort(2);

		AwardResult result = CrackerCampaignTransactions.award(
			port, CrackerCampaignTransactions.TRIGGER_NPC_KILL);

		check(result.getStatus() == AwardStatus.AWARDED, "winning event awards a cracker");
		check(result.getItemId() == 9001L, "award reports the exact minted item id");
		check(result.getRemaining() == 1 && port.durableRemaining == 1,
			"award decrements the durable pool exactly once");
		check(port.memoryItems.equals(Arrays.asList(9001L))
			&& port.dbItems.equals(port.memoryItems),
			"award persists exactly the tentative inventory item");
		check(port.provenanceCount == 1 && port.provenanceItemId == 9001L,
			"award records provenance for the exact item instance");
		check(CrackerCampaignTransactions.TRIGGER_NPC_KILL.equals(port.provenanceTrigger)
			&& port.provenanceBefore == 2 && port.provenanceAfter == 1,
			"award provenance records trigger and exact pool transition");
		check(port.releaseCount == 1 && port.rollbackCount == 0,
			"committed award releases its save reservation without compensation");
		check(port.groundDrops == 0, "committed award never creates a ground item");
	}

	private static void testFirstCampaignCrackerRecordIsExactAndAtomic() {
		FakeAwardPort port = new FakeAwardPort(2);
		port.achievementSeasonId = " Launch-2026 ";
		port.achievementPlayerId = 77;
		port.achievementPlayerName = " Winner ";
		port.awardedAtMs = 1_725_000_123_456L;
		port.nextItemId = 5_000_000_001L;

		AwardResult result = CrackerCampaignTransactions.award(
			port, CrackerCampaignTransactions.TRIGGER_NPC_KILL);

		check(result.isAwarded() && result.isNewlyWonWorldFirst(),
			"the first exact campaign award reports a newly won world first");
		check(port.worldFirstInsertAttempts == 1 && port.worldAchievementRecords.size() == 1,
			"item, pool, provenance, and first-item record commit in one transaction");
		WorldAchievementRecord record = port.worldAchievementRecords.get(0);
		check("launch-2026".equals(record.seasonId),
			"record uses the normalized configured achievement season");
		check("first:item:575".equals(record.recordKey)
			&& "first_item".equals(record.recordType),
			"record owns the stable first-cracker identity and type");
		check(record.playerId == 77 && "Winner".equals(record.playerName),
			"record identifies the exact winning character without account data");
		check(record.subjectId == 575 && record.value == 1L,
			"record identifies one exact Christmas cracker");
		check("launch_cracker_campaign".equals(record.source),
			"record source is the durable campaign award path");
		check("5000000001".equals(record.sourceEventKey),
			"64-bit item-instance id is stored as canonical decimal source identity");
		check(record.claimedAtMs == port.awardedAtMs,
			"record timestamp is the exact award time captured for this transaction");
		check("item=575 trigger=npc_kill".equals(record.detail)
			&& record.detail.length() <= 255
			&& !record.detail.contains("Winner"),
			"record detail is bounded operational metadata without private identity");
	}

	private static void testLaterAwardAndExistingClaimAreNotNewWorldFirsts() {
		FakeAwardPort port = new FakeAwardPort(3);
		AwardResult first = CrackerCampaignTransactions.award(
			port, CrackerCampaignTransactions.TRIGGER_SKILLING);
		AwardResult later = CrackerCampaignTransactions.award(
			port, CrackerCampaignTransactions.TRIGGER_NPC_KILL);

		check(first.isNewlyWonWorldFirst(), "first award wins the one durable record");
		check(later.isAwarded() && !later.isNewlyWonWorldFirst(),
			"a later physical campaign award is not reported as another world first");
		check(port.worldFirstInsertAttempts == 2 && port.worldAchievementRecords.size() == 1,
			"later award observes the existing stable record without replacing it");
		check("9001".equals(port.worldAchievementRecords.get(0).sourceEventKey),
			"later award preserves the first winner's exact source event");

		FakeAwardPort replay = new FakeAwardPort(1);
		replay.worldAchievementRecords.add(
			copyWorldAchievementRecord(port.worldAchievementRecords.get(0)));
		AwardResult replayResult = CrackerCampaignTransactions.award(
			replay, CrackerCampaignTransactions.TRIGGER_SKILLING);
		check(replayResult.isAwarded() && !replayResult.isNewlyWonWorldFirst(),
			"an existing exact claim replay is never surfaced as newly won");
		check(replay.worldAchievementRecords.size() == 1,
			"existing exact claim replay does not duplicate the ledger row");
	}

	private static void testWorldFirstFeatureAndSeasonGates() {
		FakeAwardPort disabled = new FakeAwardPort(1);
		disabled.worldAchievementsEnabled = false;
		AwardResult disabledResult = CrackerCampaignTransactions.award(
			disabled, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(disabledResult.isAwarded() && !disabledResult.isNewlyWonWorldFirst()
			&& disabled.worldFirstInsertAttempts == 0,
			"disabled achievements leave the exact cracker award intact without a claim");

		FakeAwardPort elevated = new FakeAwardPort(1);
		elevated.defaultUser = false;
		AwardResult elevatedResult = CrackerCampaignTransactions.award(
			elevated, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(elevatedResult.isAwarded() && !elevatedResult.isNewlyWonWorldFirst()
			&& elevated.worldFirstInsertAttempts == 0,
			"non-default character receives the campaign item but cannot claim its world first");

		for (String invalidSeason : Arrays.asList("", "bad season", "abcdefghijklmnopqrstuvwxyz1234567")) {
			FakeAwardPort invalid = new FakeAwardPort(1);
			invalid.achievementSeasonId = invalidSeason;
			AwardResult result = CrackerCampaignTransactions.award(
				invalid, CrackerCampaignTransactions.TRIGGER_SKILLING);
			check(result.isAwarded() && !result.isNewlyWonWorldFirst()
				&& invalid.worldFirstInsertAttempts == 0,
				"invalid season gates only the optional record: " + invalidSeason);
		}

		for (long invalidItemId : Arrays.asList(0L, -2L)) {
			FakeAwardPort invalid = new FakeAwardPort(1);
			invalid.nextItemId = invalidItemId;
			AwardResult result = CrackerCampaignTransactions.award(
				invalid, CrackerCampaignTransactions.TRIGGER_SKILLING);
			check(result.isAwarded() && !result.isNewlyWonWorldFirst()
				&& invalid.worldFirstInsertAttempts == 0,
				"non-positive item-instance identity cannot become a canonical source key");
		}

		FakeAwardPort secondSeason = new FakeAwardPort(1);
		secondSeason.achievementSeasonId = "season-two";
		AwardResult secondSeasonResult = CrackerCampaignTransactions.award(
			secondSeason, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(secondSeasonResult.isNewlyWonWorldFirst()
			&& "season-two".equals(secondSeason.worldAchievementRecords.get(0).seasonId),
			"configured valid season namespaces the first-item record");
	}

	private static void testInsertZeroRequiresCanonicalExistingRecord() {
		FakeAwardPort absent = new FakeAwardPort(2);
		absent.forceWorldFirstInsertZero = true;
		AwardResult absentResult = CrackerCampaignTransactions.award(
			absent, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(absentResult.getStatus() == AwardStatus.FAILED,
			"insert zero with no stable first-item record fails the whole award");
		assertAwardUntouched(absent, 2, "unexplained insert zero");
		check(absent.worldFirstLoadCount == 1,
			"insert zero is reconciled against the canonical season/key inside the transaction");

		FakeAwardPort malformed = new FakeAwardPort(2);
		WorldAchievementRecord badRecord = canonicalExistingRecord();
		badRecord.recordType = "wrong_type";
		malformed.worldAchievementRecords.add(badRecord);
		AwardResult malformedResult = CrackerCampaignTransactions.award(
			malformed, CrackerCampaignTransactions.TRIGGER_SKILLING);
		check(malformedResult.getStatus() == AwardStatus.FAILED,
			"insert zero backed by a malformed stable key cannot masquerade as an existing first");
		check(malformed.durableRemaining == 2 && malformed.dbItems.isEmpty(),
			"malformed existing claim rolls back the physical award and pool decrement");
		check(malformed.worldAchievementRecords.size() == 1
			&& "wrong_type".equals(malformed.worldAchievementRecords.get(0).recordType),
			"rollback preserves the pre-existing malformed row for operator repair");
	}

	private static void testEmptyAndCapacityRejectionsMutateNothing() {
		FakeAwardPort empty = new FakeAwardPort(0);
		AwardResult emptyResult = CrackerCampaignTransactions.award(
			empty, CrackerCampaignTransactions.TRIGGER_SKILLING);
		check(emptyResult.getStatus() == AwardStatus.POOL_EMPTY
			&& emptyResult.getRemaining() == 0,
			"empty durable pool rejects a stale winning candidate");
		assertAwardUntouched(empty, 0, "empty pool");

		for (AwardStatus rejection : Arrays.asList(
			AwardStatus.INVENTORY_FULL, AwardStatus.CLIENT_UNSUPPORTED)) {
			FakeAwardPort port = new FakeAwardPort(3);
			port.addStatus = rejection;

			AwardResult result = CrackerCampaignTransactions.award(
				port, CrackerCampaignTransactions.TRIGGER_SKILLING);

			check(result.getStatus() == rejection && result.getRemaining() == 3,
				rejection + " reports the unchanged authoritative pool");
			assertAwardUntouched(port, 3, rejection.toString());
			check(port.groundDrops == 0, rejection + " never spills a cracker to the ground");
		}
	}

	private static void testAwardFaultsRollbackEveryDurableComponent() {
		for (String failure : Arrays.asList(
			"load_pool", "save_inventory", "save_pool", "provenance", "world_first")) {
			FakeAwardPort port = new FakeAwardPort(4);
			port.failAt = failure;

			AwardResult result = CrackerCampaignTransactions.award(
				port, CrackerCampaignTransactions.TRIGGER_NPC_KILL);

			check(result.getStatus() == AwardStatus.FAILED,
				failure + " reports a confirmed failed award");
			assertAwardUntouched(port, 4, failure);
			check(port.releaseCount == 1, failure + " releases the save reservation");
			if (!"load_pool".equals(failure)) {
				check(port.rollbackCount == 1 && port.rollbackItemId == 9001L,
					failure + " compensates the exact tentative item id");
			}
		}

		FakeAwardPort addFailure = new FakeAwardPort(4);
		addFailure.addStatus = AwardStatus.FAILED;
		AwardResult addResult = CrackerCampaignTransactions.award(
			addFailure, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(addResult.getStatus() == AwardStatus.FAILED,
			"failed tentative insertion is reported without claiming an item");
		assertAwardUntouched(addFailure, 4, "failed tentative insertion");
		check(addFailure.rollbackCount == 0,
			"coordinator does not compensate an insertion that reported no mutation");
	}

	private static void testAwardLifecycleFenceAndExactCompensation() {
		FakeAwardPort busy = new FakeAwardPort(2);
		busy.reserve = false;
		AwardResult busyResult = CrackerCampaignTransactions.award(
			busy, CrackerCampaignTransactions.TRIGGER_SKILLING);
		check(busyResult.getStatus() == AwardStatus.BUSY,
			"busy save lifecycle rejects the winning candidate");
		assertAwardUntouched(busy, 2, "busy save lifecycle");
		check(busy.atomicCount == 0 && busy.releaseCount == 0,
			"unowned reservation opens no transaction and is never released");

		FakeAwardPort interruptedBefore = new FakeAwardPort(2);
		interruptedBefore.canComplete = false;
		AwardResult beforeResult = CrackerCampaignTransactions.award(
			interruptedBefore, CrackerCampaignTransactions.TRIGGER_SKILLING);
		check(beforeResult.getStatus() == AwardStatus.INTERRUPTED,
			"lifecycle fence rejects an award before its transaction");
		assertAwardUntouched(interruptedBefore, 2, "pre-transaction interruption");
		check(interruptedBefore.atomicCount == 0 && interruptedBefore.releaseCount == 1,
			"pre-transaction interruption releases its owned reservation");

		for (boolean afterWrites : Arrays.asList(false, true)) {
			FakeAwardPort interrupted = new FakeAwardPort(2);
			if (afterWrites) {
				interrupted.interruptAfterWrites = true;
			} else {
				interrupted.interruptAfterAdd = true;
			}
			AwardResult result = CrackerCampaignTransactions.award(
				interrupted, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
			String label = afterWrites ? "post-write interruption" : "post-add interruption";
			check(result.getStatus() == AwardStatus.INTERRUPTED, label + " is distinguished");
			assertAwardUntouched(interrupted, 2, label);
			check(interrupted.rollbackCount == 1 && interrupted.rollbackItemId == 9001L,
				label + " removes the exact tentative item");
			check(interrupted.releaseCount == 1,
				label + " releases reservation after confirmed rollback");
		}
	}

	private static void testAwardLostCommitAcknowledgementIsReconciled() {
		FakeAwardPort committed = new FakeAwardPort(5);
		committed.commitAcknowledgementLost = true;
		AwardResult committedResult = CrackerCampaignTransactions.award(
			committed, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(committedResult.isAwarded() && committedResult.getRemaining() == 4
			&& committedResult.isNewlyWonWorldFirst(),
			"lost COMMIT acknowledgement reconciles a durable award");
		check(committed.durableRemaining == 4
			&& committed.dbItems.equals(Arrays.asList(9001L))
			&& committed.provenanceCount == 1
			&& committed.worldAchievementRecords.size() == 1,
			"reconciled commit keeps pool, exact inventory, provenance, and first record");
		check(committed.verifierCount == 1 && committed.rollbackCount == 0
			&& committed.releaseCount == 1,
			"reconciled commit neither compensates nor retains the reservation");

		FakeAwardPort rolledBack = new FakeAwardPort(5);
		rolledBack.commitAcknowledgementLost = true;
		rolledBack.commitWasDurable = false;
		AwardResult rolledBackResult = CrackerCampaignTransactions.award(
			rolledBack, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
		check(rolledBackResult.getStatus() == AwardStatus.FAILED
			&& rolledBackResult.getRemaining() == 5
			&& !rolledBackResult.isNewlyWonWorldFirst(),
			"lost acknowledgement reconciles a durable rollback");
		assertAwardUntouched(rolledBack, 5, "reconciled rolled-back award");
		check(rolledBack.rollbackCount == 1 && rolledBack.rollbackItemId == 9001L,
			"reconciled rollback compensates the exact tentative item");
	}

	private static void testWorldFirstLostCommitRequiresExactAfterOrBeforeState() {
		for (String corruption : Arrays.asList(
			"missing_after", "mismatch_after", "present_before")) {
			FakeAwardPort port = new FakeAwardPort(2);
			port.commitAcknowledgementLost = true;
			port.worldFirstSettlementCorruption = corruption;
			if ("present_before".equals(corruption)) {
				port.commitWasDurable = false;
			}

			AwardResult result = CrackerCampaignTransactions.award(
				port, CrackerCampaignTransactions.TRIGGER_NPC_KILL);

			check(result.getStatus() == AwardStatus.UNCERTAIN
				&& !result.isNewlyWonWorldFirst(),
				corruption + " cannot be guessed as a committed first or rollback");
			check(port.verifierCount == 1 && port.worldFirstLoadCount == 1,
				corruption + " verifies the exact tentative first-item record");
			check(port.quarantineCount == 1 && port.releaseCount == 0
				&& port.rollbackCount == 0,
				corruption + " preserves unknown-state quarantine semantics");
		}
	}

	private static void testUnresolvedAwardOutcomeQuarantinesWithoutCompensation() {
		for (boolean mixedSettlement : Arrays.asList(false, true)) {
			FakeAwardPort port = new FakeAwardPort(6);
			if (mixedSettlement) {
				port.commitAcknowledgementLost = true;
				port.forceMixedSettlement = true;
			} else {
				port.forceUnknownOutcome = true;
			}

			AwardResult result = CrackerCampaignTransactions.award(
				port, CrackerCampaignTransactions.TRIGGER_SKILLING);

			String label = mixedSettlement ? "mixed durable settlement" : "unresolved transaction";
			check(result.getStatus() == AwardStatus.UNCERTAIN
				&& result.getRemaining() == CrackerCampaignService.UNKNOWN_REMAINING,
				label + " reports unknown authority");
			check(port.quarantineCount == 1, label + " fences the player session");
			check(port.releaseCount == 0, label + " retains the save reservation");
			check(port.rollbackCount == 0, label + " never guesses compensation");
			check(port.memoryItems.equals(Arrays.asList(9001L)),
				label + " leaves tentative memory to authoritative restart reconciliation");
		}
	}

	private static void testConcurrentAwardsCannotOverspendPool() throws Exception {
		SharedAwardLedger ledger = new SharedAwardLedger(1);
		ConcurrentAwardPort first = new ConcurrentAwardPort(ledger, 9101L);
		ConcurrentAwardPort second = new ConcurrentAwardPort(ledger, 9202L);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<AwardResult> firstResult = new AtomicReference<>();
		AtomicReference<AwardResult> secondResult = new AtomicReference<>();
		Thread one = new Thread(() -> runAward(start, first, firstResult),
			"cracker-award-one");
		Thread two = new Thread(() -> runAward(start, second, secondResult),
			"cracker-award-two");
		one.start();
		two.start();
		start.countDown();
		one.join();
		two.join();

		int awarded = (firstResult.get().isAwarded() ? 1 : 0)
			+ (secondResult.get().isAwarded() ? 1 : 0);
		int empty = (firstResult.get().getStatus() == AwardStatus.POOL_EMPTY ? 1 : 0)
			+ (secondResult.get().getStatus() == AwardStatus.POOL_EMPTY ? 1 : 0);
		check(awarded == 1 && empty == 1,
			"two simultaneous winners against one remaining cracker have one award");
		check(ledger.remaining == 0 && ledger.provenanceCount == 1,
			"serialized awards decrement once and record one origin");
		check(first.memoryItems.size() + second.memoryItems.size() == 1
			&& first.dbItems.size() + second.dbItems.size() == 1,
			"concurrent winners materialize exactly one physical item");
		check(first.releaseCount == 1 && second.releaseCount == 1,
			"both settled award attempts release their reservations");
	}

	private static void runAward(CountDownLatch start, ConcurrentAwardPort port,
		AtomicReference<AwardResult> result) {
		try {
			start.await();
			result.set(CrackerCampaignTransactions.award(
				port, CrackerCampaignTransactions.TRIGGER_NPC_KILL));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void testChanceRollersReceiveExactConfiguredDenominators() {
		RecordingChanceRoller roller = new RecordingChanceRoller();
		CrackerCampaignService service = new CrackerCampaignService(
			new FakePoolPort(1), true, 500, 1000, roller);

		check(service.isNpcKillWinningRoll(), "deterministic NPC roller result is returned");
		check(service.isSkillingWinningRoll(), "deterministic skilling roller result is returned");
		check(roller.denominators.equals(Arrays.asList(500, 1000)),
			"roll seam receives exact 1/500 and 1/1000 denominators without off-by-one math");

		expectIllegalArgument(() -> new CrackerCampaignService(
			new FakePoolPort(1), true, 0, 1000, roller),
			"zero NPC denominator is rejected");
		expectIllegalArgument(() -> new CrackerCampaignService(
			new FakePoolPort(1), true, 500, -1, roller),
			"negative skilling denominator is rejected");
		expectIllegalArgument(() -> new CrackerCampaignService(
			new FakePoolPort(1), true, 500, 1000, null),
			"missing chance roller is rejected");
	}

	private static void testCampaignStatePublishesOnlySettledAuthority() {
		RecordingStatePublisher publisher = new RecordingStatePublisher();
		CrackerCampaignService service = new CrackerCampaignService(
			new FakePoolPort(9), true, 500, 1000, denominator -> false, publisher);

		FakeAwardPort awarded = new FakeAwardPort(2);
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			awarded, CrackerCampaignTransactions.TRIGGER_NPC_KILL));

		FakeAwardPort empty = new FakeAwardPort(0);
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			empty, CrackerCampaignTransactions.TRIGGER_SKILLING));

		for (AwardStatus noChange : Arrays.asList(AwardStatus.INVENTORY_FULL,
			AwardStatus.CLIENT_UNSUPPORTED)) {
			FakeAwardPort rejected = new FakeAwardPort(8);
			rejected.addStatus = noChange;
			service.publishAwardSettlement(CrackerCampaignTransactions.award(
				rejected, CrackerCampaignTransactions.TRIGGER_SKILLING));
		}
		FakeAwardPort busy = new FakeAwardPort(8);
		busy.reserve = false;
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			busy, CrackerCampaignTransactions.TRIGGER_SKILLING));
		FakeAwardPort interrupted = new FakeAwardPort(8);
		interrupted.canComplete = false;
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			interrupted, CrackerCampaignTransactions.TRIGGER_SKILLING));

		FakeAwardPort failed = new FakeAwardPort(8);
		failed.failAt = "save_pool";
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			failed, CrackerCampaignTransactions.TRIGGER_NPC_KILL));
		FakeAwardPort uncertain = new FakeAwardPort(8);
		uncertain.forceUnknownOutcome = true;
		service.publishAwardSettlement(CrackerCampaignTransactions.award(
			uncertain, CrackerCampaignTransactions.TRIGGER_NPC_KILL));

		check(publisher.remaining.equals(Arrays.asList(1, 0, 0, 0)),
			"HUD publishes committed award, observed empty, and fail-closed states only");
	}

	private static void testCampaignStateSetAndRangeSemantics() {
		RecordingStatePublisher committedPublisher = new RecordingStatePublisher();
		FakePoolPort committedPort = new FakePoolPort(2);
		CrackerCampaignService committed = new CrackerCampaignService(committedPort,
			true, 500, 1000, denominator -> false, committedPublisher);
		check(committed.setRemaining(5).isSuccessful(), "committed owner set succeeds");
		check(committed.setRemaining(5).getStatus() == SetStatus.UNCHANGED,
			"idempotent owner set remains a committed settlement");
		check(committedPublisher.remaining.equals(Arrays.asList(5, 5)),
			"every committed owner set publishes its exact authoritative count");

		RecordingStatePublisher failedPublisher = new RecordingStatePublisher();
		FakePoolPort failedPort = new FakePoolPort(2);
		failedPort.failSavesRemaining = 1;
		CrackerCampaignService failed = new CrackerCampaignService(failedPort,
			true, 500, 1000, denominator -> false, failedPublisher);
		check(failed.setRemaining(5).getStatus() == SetStatus.FAILED,
			"rolled-back owner set is reported failed");
		check(failedPublisher.remaining.equals(Arrays.asList(2)),
			"known owner-set rollback republishes its proven previous count");

		RecordingStatePublisher uncertainPublisher = new RecordingStatePublisher();
		FakePoolPort uncertainPort = new FakePoolPort(2);
		uncertainPort.forceUnknownOutcome = true;
		CrackerCampaignService uncertain = new CrackerCampaignService(uncertainPort,
			true, 500, 1000, denominator -> false, uncertainPublisher);
		check(uncertain.setRemaining(5).getStatus() == SetStatus.UNCERTAIN,
			"unknown owner set is reported uncertain");
		check(uncertainPublisher.remaining.equals(Arrays.asList(0)),
			"uncertain owner set hides the HUD");
		check(uncertain.getState().getRemaining() == 5,
			"authoritative reload resolves the uncertain committed fixture");
		check(uncertainPublisher.remaining.equals(Arrays.asList(0, 5)),
			"successful authoritative reload republishes the exact count to connected clients");

		expectIllegalArgument(() -> committed.setRemaining(
			CrackerCampaignService.MAX_REMAINING + 1),
			"owner set cannot exceed the one-million display contract");
		check(committedPublisher.remaining.equals(Arrays.asList(5, 5)),
			"out-of-range set publishes nothing");

		State corrupt = new CrackerCampaignService(
			new FakePoolPort(CrackerCampaignService.MAX_REMAINING + 1), true).getState();
		check(!corrupt.isLoaded() && !corrupt.isActive(),
			"out-of-range durable state fails closed");

		CrackerCampaignService throwingPublisher = new CrackerCampaignService(
			new FakePoolPort(1), true, 500, 1000, denominator -> false,
			remaining -> { throw new RuntimeException("injected publisher failure"); });
		check(throwingPublisher.setRemaining(2).isSuccessful(),
			"HUD publication failure cannot undo a committed owner mutation");
	}

	private static void testAwardInputValidation() {
		FakeAwardPort port = new FakeAwardPort(2);
		for (String trigger : Arrays.asList("", "bad trigger", "123456789012345678901234567890123")) {
			AwardResult result = CrackerCampaignTransactions.award(port, trigger);
			check(result.getStatus() == AwardStatus.FAILED,
				"invalid trigger is rejected: " + trigger);
		}
		check(port.atomicCount == 0 && port.releaseCount == 0,
			"invalid triggers mutate no lifecycle or durable state");
		expectIllegalArgument(() -> CrackerCampaignTransactions.award(
			(AwardPort) null, CrackerCampaignTransactions.TRIGGER_SKILLING),
			"missing award port is rejected");
		expectIllegalArgument(() -> AddedCracker.failed(AwardStatus.AWARDED),
			"failed mutation cannot claim awarded status");
	}

	private static void assertAwardUntouched(FakeAwardPort port, int remaining,
		String label) {
		check(port.durableRemaining == remaining, label + " leaves pool unchanged");
		check(port.memoryItems.isEmpty(), label + " leaves memory inventory unchanged");
		check(port.dbItems.isEmpty(), label + " leaves durable inventory unchanged");
		check(port.provenanceCount == 0, label + " creates no provenance");
		check(port.worldAchievementRecords.isEmpty(),
			label + " creates no world-achievement record");
	}

	private static WorldAchievementRecord canonicalExistingRecord() {
		WorldAchievementRecord record = new WorldAchievementRecord();
		record.seasonId = "launch-2026";
		record.recordKey = "first:item:575";
		record.recordType = "first_item";
		record.playerId = 11;
		record.playerName = "Earlier";
		record.subjectId = 575;
		record.value = 1L;
		record.source = "launch_cracker_campaign";
		record.sourceEventKey = "8000000001";
		record.claimedAtMs = 1_724_000_000_000L;
		record.detail = "item=575 trigger=skilling";
		return record;
	}

	private static List<WorldAchievementRecord> copyWorldAchievementRecords(
		List<WorldAchievementRecord> records) {
		List<WorldAchievementRecord> copies = new ArrayList<>(records.size());
		for (WorldAchievementRecord record : records) {
			copies.add(copyWorldAchievementRecord(record));
		}
		return copies;
	}

	private static WorldAchievementRecord copyWorldAchievementRecord(
		WorldAchievementRecord source) {
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

	private static boolean same(Object first, Object second) {
		return first == null ? second == null : first.equals(second);
	}

	private static void runSet(CountDownLatch start, FakePoolPort port, int remaining,
		AtomicReference<CrackerCampaignTransactions.MutationResult> result) {
		try {
			start.await();
			result.set(CrackerCampaignTransactions.setRemaining(port, remaining));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void expectIllegalArgument(CheckedAction action, String message) {
		try {
			action.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		} catch (Exception ex) {
			throw new AssertionError(message + ": unexpected " + ex, ex);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	@FunctionalInterface
	private interface CheckedAction {
		void run() throws Exception;
	}

	private static final class RecordingChanceRoller
		implements CrackerCampaignService.ChanceRoller {
		private final List<Integer> denominators = new ArrayList<>();

		@Override
		public boolean wins(int denominator) {
			denominators.add(denominator);
			return true;
		}
	}

	private static final class RecordingStatePublisher
		implements CrackerCampaignService.CampaignStatePublisher {
		private final List<Integer> remaining = new ArrayList<>();

		@Override
		public void publish(int remaining) {
			this.remaining.add(remaining);
		}
	}

	private static final class FakeAwardPort implements AwardPort {
		private Integer durableRemaining;
		private boolean reserve = true;
		private boolean canComplete = true;
		private boolean worldAchievementsEnabled = true;
		private boolean defaultUser = true;
		private String achievementSeasonId = "launch-2026";
		private int achievementPlayerId = 42;
		private String achievementPlayerName = "Winner";
		private long awardedAtMs = 1_725_000_000_000L;
		private boolean interruptAfterAdd;
		private boolean interruptAfterWrites;
		private boolean commitAcknowledgementLost;
		private boolean commitWasDurable = true;
		private boolean forceUnknownOutcome;
		private boolean forceMixedSettlement;
		private boolean forceWorldFirstInsertZero;
		private String worldFirstSettlementCorruption = "";
		private String failAt = "";
		private AwardStatus addStatus = AwardStatus.AWARDED;
		private long nextItemId = 9001L;
		private long addedItemId = -1L;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private int provenanceCount;
		private long provenanceItemId = -1L;
		private String provenanceTrigger = "";
		private int provenanceBefore = -1;
		private int provenanceAfter = -1;
		private final List<WorldAchievementRecord> worldAchievementRecords = new ArrayList<>();
		private WorldAchievementRecord lastWorldFirstAttempt;
		private int worldFirstInsertAttempts;
		private int worldFirstLoadCount;
		private int atomicCount;
		private int verifierCount;
		private int releaseCount;
		private int rollbackCount;
		private long rollbackItemId = -1L;
		private int quarantineCount;
		private int groundDrops;

		private FakeAwardPort(Integer durableRemaining) {
			this.durableRemaining = durableRemaining;
		}

		@Override
		public boolean reserveSave() {
			return reserve;
		}

		@Override
		public boolean canComplete() {
			return canComplete;
		}

		@Override
		public synchronized AtomicTransactionOutcome atomically(
			CrackerCampaignTransactions.TransactionBody body,
			CrackerCampaignTransactions.TransactionVerifier verifier) {
			atomicCount++;
			Integer remainingSnapshot = durableRemaining;
			List<Long> itemsSnapshot = new ArrayList<>(dbItems);
			int provenanceSnapshot = provenanceCount;
			long provenanceIdSnapshot = provenanceItemId;
			String provenanceTriggerSnapshot = provenanceTrigger;
			int provenanceBeforeSnapshot = provenanceBefore;
			int provenanceAfterSnapshot = provenanceAfter;
			List<WorldAchievementRecord> worldAchievementSnapshot =
				copyWorldAchievementRecords(worldAchievementRecords);
			try {
				body.run();
				if (forceUnknownOutcome) {
					return AtomicTransactionOutcome.UNKNOWN;
				}
				if (commitAcknowledgementLost) {
					if (!commitWasDurable) {
						restoreDurableSnapshot(remainingSnapshot, itemsSnapshot,
							provenanceSnapshot, provenanceIdSnapshot,
							provenanceTriggerSnapshot, provenanceBeforeSnapshot,
							provenanceAfterSnapshot, worldAchievementSnapshot);
					} else if (forceMixedSettlement) {
						dbItems.clear();
					}
					corruptWorldFirstSettlement();
					verifierCount++;
					return verifier.verify();
				}
				return AtomicTransactionOutcome.COMMITTED;
			} catch (Exception ex) {
				restoreDurableSnapshot(remainingSnapshot, itemsSnapshot,
					provenanceSnapshot, provenanceIdSnapshot,
					provenanceTriggerSnapshot, provenanceBeforeSnapshot,
					provenanceAfterSnapshot, worldAchievementSnapshot);
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
		}

		private void restoreDurableSnapshot(Integer remainingSnapshot,
			List<Long> itemsSnapshot, int provenanceSnapshot, long provenanceIdSnapshot,
			String provenanceTriggerSnapshot, int provenanceBeforeSnapshot,
			int provenanceAfterSnapshot,
			List<WorldAchievementRecord> worldAchievementSnapshot) {
			durableRemaining = remainingSnapshot;
			dbItems.clear();
			dbItems.addAll(itemsSnapshot);
			provenanceCount = provenanceSnapshot;
			provenanceItemId = provenanceIdSnapshot;
			provenanceTrigger = provenanceTriggerSnapshot;
			provenanceBefore = provenanceBeforeSnapshot;
			provenanceAfter = provenanceAfterSnapshot;
			worldAchievementRecords.clear();
			worldAchievementRecords.addAll(copyWorldAchievementRecords(worldAchievementSnapshot));
		}

		private void corruptWorldFirstSettlement() {
			if ("missing_after".equals(worldFirstSettlementCorruption)) {
				worldAchievementRecords.clear();
			} else if ("mismatch_after".equals(worldFirstSettlementCorruption)) {
				if (!worldAchievementRecords.isEmpty()) {
					worldAchievementRecords.get(0).detail = "mismatched";
				}
			} else if ("present_before".equals(worldFirstSettlementCorruption)
				&& lastWorldFirstAttempt != null) {
				worldAchievementRecords.clear();
				worldAchievementRecords.add(copyWorldAchievementRecord(lastWorldFirstAttempt));
			}
		}

		@Override
		public Integer loadRemaining() throws Exception {
			fail("load_pool");
			return durableRemaining;
		}

		@Override
		public void saveRemaining(int remaining) throws Exception {
			durableRemaining = remaining;
			fail("save_pool");
		}

		@Override
		public AddedCracker addCracker() {
			if (addStatus != AwardStatus.AWARDED) {
				return AddedCracker.failed(addStatus);
			}
			addedItemId = nextItemId++;
			memoryItems.add(addedItemId);
			if (interruptAfterAdd) {
				canComplete = false;
			}
			return AddedCracker.awarded(addedItemId);
		}

		@Override
		public void saveInventory() throws Exception {
			dbItems.clear();
			dbItems.addAll(memoryItems);
			fail("save_inventory");
		}

		@Override
		public void recordProvenance(String trigger, int before, int after, long itemId)
			throws Exception {
			provenanceCount++;
			provenanceItemId = itemId;
			provenanceTrigger = trigger;
			provenanceBefore = before;
			provenanceAfter = after;
			fail("provenance");
			if (interruptAfterWrites) {
				canComplete = false;
			}
		}

		@Override
		public WorldFirstClaimant worldFirstClaimant() {
			return new WorldFirstClaimant(worldAchievementsEnabled, defaultUser,
				achievementSeasonId, achievementPlayerId, achievementPlayerName, awardedAtMs);
		}

		@Override
		public int insertWorldAchievementRecord(WorldAchievementRecord record)
			throws Exception {
			worldFirstInsertAttempts++;
			lastWorldFirstAttempt = copyWorldAchievementRecord(record);
			if (forceWorldFirstInsertZero) {
				return 0;
			}
			for (WorldAchievementRecord existing : worldAchievementRecords) {
				if (same(existing.seasonId, record.seasonId)
					&& (same(existing.recordKey, record.recordKey)
						|| (same(existing.source, record.source)
							&& same(existing.sourceEventKey, record.sourceEventKey)
							&& same(existing.recordType, record.recordType)))) {
					return 0;
				}
			}
			worldAchievementRecords.add(copyWorldAchievementRecord(record));
			fail("world_first");
			return 1;
		}

		@Override
		public WorldAchievementRecord loadWorldAchievementRecord(String seasonId,
			String recordKey) {
			worldFirstLoadCount++;
			for (WorldAchievementRecord record : worldAchievementRecords) {
				if (same(record.seasonId, seasonId) && same(record.recordKey, recordKey)) {
					return copyWorldAchievementRecord(record);
				}
			}
			return null;
		}

		@Override
		public boolean persistedInventoryContains(long itemId) {
			return dbItems.contains(itemId);
		}

		@Override
		public void rollbackAddedCracker() {
			rollbackCount++;
			rollbackItemId = addedItemId;
			memoryItems.remove(Long.valueOf(addedItemId));
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			quarantineCount++;
		}

		@Override
		public void releaseSaveReservation() {
			releaseCount++;
		}

		private void fail(String point) throws Exception {
			if (point.equals(failAt)) {
				throw new Exception("injected " + point + " failure");
			}
		}
	}

	private static final class SharedAwardLedger {
		private int remaining;
		private int provenanceCount;

		private SharedAwardLedger(int remaining) {
			this.remaining = remaining;
		}
	}

	private static final class ConcurrentAwardPort implements AwardPort {
		private final SharedAwardLedger ledger;
		private final long itemId;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private int releaseCount;

		private ConcurrentAwardPort(SharedAwardLedger ledger, long itemId) {
			this.ledger = ledger;
			this.itemId = itemId;
		}

		@Override
		public boolean reserveSave() {
			return true;
		}

		@Override
		public boolean canComplete() {
			return true;
		}

		@Override
		public AtomicTransactionOutcome atomically(
			CrackerCampaignTransactions.TransactionBody body,
			CrackerCampaignTransactions.TransactionVerifier verifier) {
			synchronized (ledger) {
				int remainingSnapshot = ledger.remaining;
				int provenanceSnapshot = ledger.provenanceCount;
				List<Long> dbSnapshot = new ArrayList<>(dbItems);
				try {
					body.run();
					return AtomicTransactionOutcome.COMMITTED;
				} catch (Exception ex) {
					ledger.remaining = remainingSnapshot;
					ledger.provenanceCount = provenanceSnapshot;
					dbItems.clear();
					dbItems.addAll(dbSnapshot);
					return AtomicTransactionOutcome.ROLLED_BACK;
				}
			}
		}

		@Override
		public Integer loadRemaining() {
			return ledger.remaining;
		}

		@Override
		public void saveRemaining(int remaining) {
			ledger.remaining = remaining;
		}

		@Override
		public AddedCracker addCracker() {
			memoryItems.add(itemId);
			return AddedCracker.awarded(itemId);
		}

		@Override
		public void saveInventory() {
			dbItems.clear();
			dbItems.addAll(memoryItems);
		}

		@Override
		public void recordProvenance(String trigger, int before, int after, long itemId) {
			ledger.provenanceCount++;
		}

		@Override
		public boolean persistedInventoryContains(long itemId) {
			return dbItems.contains(itemId);
		}

		@Override
		public void rollbackAddedCracker() {
			memoryItems.remove(Long.valueOf(itemId));
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
		}

		@Override
		public void releaseSaveReservation() {
			releaseCount++;
		}
	}

	private static final class FakePoolPort implements CrackerCampaignTransactions.PoolPort {
		private Integer durableRemaining;
		private int failLoadsRemaining;
		private int failSavesRemaining;
		private boolean commitAcknowledgementLost;
		private boolean commitWasDurable = true;
		private boolean forceUnknownOutcome;
		private int atomicCount;
		private int loadCount;
		private int saveCount;
		private int verifierCount;

		private FakePoolPort(Integer durableRemaining) {
			this.durableRemaining = durableRemaining;
		}

		@Override
		public synchronized AtomicTransactionOutcome atomically(
			CrackerCampaignTransactions.TransactionBody body,
			CrackerCampaignTransactions.TransactionVerifier verifier) {
			atomicCount++;
			Integer snapshot = durableRemaining;
			try {
				body.run();
				if (forceUnknownOutcome) {
					return AtomicTransactionOutcome.UNKNOWN;
				}
				if (commitAcknowledgementLost) {
					if (!commitWasDurable) {
						durableRemaining = snapshot;
					}
					verifierCount++;
					return verifier.verify();
				}
				return AtomicTransactionOutcome.COMMITTED;
			} catch (Exception ex) {
				durableRemaining = snapshot;
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
		}

		@Override
		public synchronized Integer loadRemaining() throws Exception {
			loadCount++;
			if (failLoadsRemaining > 0) {
				failLoadsRemaining--;
				throw new Exception("injected cracker-pool load failure");
			}
			return durableRemaining;
		}

		@Override
		public synchronized void saveRemaining(int remaining) throws Exception {
			saveCount++;
			durableRemaining = remaining;
			if (failSavesRemaining > 0) {
				failSavesRemaining--;
				throw new Exception("injected cracker-pool save failure");
			}
		}
	}
}
