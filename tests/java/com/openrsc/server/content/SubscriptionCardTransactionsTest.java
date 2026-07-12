package com.openrsc.server.content;

import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.content.SubscriptionCardTransactions.AddedCard;
import com.openrsc.server.content.SubscriptionCardTransactions.EntitlementMarker;
import com.openrsc.server.content.SubscriptionCardTransactions.GrantPort;
import com.openrsc.server.content.SubscriptionCardTransactions.GrantResult;
import com.openrsc.server.content.SubscriptionCardTransactions.GrantStatus;
import com.openrsc.server.content.SubscriptionCardTransactions.MarkerScope;
import com.openrsc.server.content.SubscriptionCardTransactions.PaidGrantPort;
import com.openrsc.server.content.SubscriptionCardTransactions.RedeemPort;
import com.openrsc.server.content.SubscriptionCardTransactions.RedeemResult;
import com.openrsc.server.content.SubscriptionCardTransactions.RedeemStatus;
import com.openrsc.server.content.SubscriptionCardTransactions.TransactionBody;
import com.openrsc.server.content.SubscriptionCardTransactions.TransactionVerifier;
import com.openrsc.server.database.struct.PortalCommerceEntitlement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executable fault-injection tests for the production subscription-card transaction
 * coordinator. No test framework is used so the repository's Java 8 toolchain can run it.
 */
public final class SubscriptionCardTransactionsTest {
	private static final EntitlementMarker GLOBAL_MARKER = EntitlementMarker.global("starter_card:7", 1, 2);
	private static final EntitlementMarker PLAYER_MARKER = EntitlementMarker.player("campaign_local_test", 1, 2);
	private static final long NOW = 1_700_000_000_000L;

	private SubscriptionCardTransactionsTest() {
	}

	public static void main(String[] args) throws Exception {
		testGrantCommitsMarkerInventoryAndOriginTogether();
		testPlayerMarkerIsReusableAndSynchronizedAfterCommit();
		testRepeatedGrantCannotMintTwice();
		testConcurrentGrantMintsExactlyOneCard();
		testFullInventoryNeverDropsOrConsumesEntitlement();
		testUnsupportedClientNeverDropsOrConsumesEntitlement();
		testPostAddValidationFailureCleansTentativeItem();
		testGrantReservationFailureMutatesNothing();
		testGrantLifecycleInterruptionRollsBackExactItem();
		testGrantFaultsRollbackEveryDurableComponent();
		testGrantLostCommitAcknowledgementIsReconciled();
		testUnresolvedGrantOutcomeQuarantinesWithoutCompensation();
		testPaidGrantClaimsOldestOneAtATime();
		testPaidGrantFullAndUnsupportedSafeguards();
		testPaidGrantFaultsRollbackEntitlementInventoryAndOrigin();
		testPaidGrantConditionalClaimRaceRollsBackExactCard();
		testPaidGrantLostCommitAcknowledgementAndUnknownOutcome();
		testConcurrentPaidPickupClaimsExactlyOneCard();
		testPaidTransactionReferenceIsStableAndRedacted();
		testInactiveRedemptionCommitsExactConsumptionExpiryAndProvenance();
		testActiveRedemptionExtendsFromStoredExpiry();
		testRedemptionRestoresSameItemAndSlotOnEveryFault();
		testRedemptionReservationAndLifecycleFailuresAreSafe();
		testRedemptionLostCommitAcknowledgementIsReconciled();
		testUnresolvedRedemptionOutcomeQuarantinesWithoutCompensation();
		testMissingClickedCardCannotExtendTime();
		testConcurrentAccountRedemptionsSerializeAndBothExtend();
		testCommittedCachePublicationCannotMoveBackward();
		testExpirySaturatesInsteadOfOverflowing();
		testMarkerValidation();
		System.out.println("Subscription-card transaction fault tests passed.");
	}

	private static void testGrantCommitsMarkerInventoryAndOriginTogether() {
		FakeGrantPort port = new FakeGrantPort();
		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "grant=starter_card");

		check(result.getStatus() == GrantStatus.GRANTED, "starter grant succeeds");
		check(result.getItemId() == 5001L, "starter grant reports the exact minted item id");
		check(port.memoryItems.equals(Arrays.asList(5001L)), "starter grant adds exactly one memory item");
		check(port.dbItems.equals(port.memoryItems), "starter grant persists exact inventory");
		check(port.dbMarker == 2, "starter grant claims marker in the same transaction");
		check(port.provenanceCount == 1 && port.provenanceItemId == 5001L,
			"starter grant persists exact item-origin provenance");
		check(port.releaseCount == 1, "successful grant releases its save reservation");
		check(port.rollbackCount == 0, "successful grant does not compensate memory");
	}

	private static void testPlayerMarkerIsReusableAndSynchronizedAfterCommit() {
		FakeGrantPort port = new FakeGrantPort();
		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, PLAYER_MARKER, "campaign_test", "scope=player");

		check(result.isGranted(), "character-local campaign marker can use the shared grant path");
		check(port.savedMarkerScope == MarkerScope.PLAYER, "player marker is persisted in player scope");
		check(port.memoryMarker == 2, "player marker cache changes only after commit");
		check(port.markerCommitCount == 1, "player marker receives one post-commit cache update");
	}

	private static void testRepeatedGrantCannotMintTwice() {
		FakeGrantPort port = new FakeGrantPort();
		check(SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "first").isGranted(), "first grant succeeds");
		GrantResult replay = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "replay");

		check(replay.getStatus() == GrantStatus.NOT_AVAILABLE, "claimed marker rejects replay");
		check(port.memoryItems.equals(Arrays.asList(5001L)), "replay cannot mint a second card");
		check(port.provenanceCount == 1, "replay cannot add a second origin row");
		check(port.releaseCount == 2, "each owned reservation is released");
	}

	private static void testConcurrentGrantMintsExactlyOneCard() throws Exception {
		SharedGrantLedger ledger = new SharedGrantLedger();
		ConcurrentGrantPort first = new ConcurrentGrantPort(ledger, 6101L);
		ConcurrentGrantPort second = new ConcurrentGrantPort(ledger, 6202L);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<GrantResult> firstResult = new AtomicReference<>();
		AtomicReference<GrantResult> secondResult = new AtomicReference<>();
		Thread one = new Thread(() -> runGrant(start, first, firstResult));
		Thread two = new Thread(() -> runGrant(start, second, secondResult));
		one.start();
		two.start();
		start.countDown();
		one.join();
		two.join();

		int granted = (firstResult.get().isGranted() ? 1 : 0) + (secondResult.get().isGranted() ? 1 : 0);
		int unavailable = (firstResult.get().getStatus() == GrantStatus.NOT_AVAILABLE ? 1 : 0)
			+ (secondResult.get().getStatus() == GrantStatus.NOT_AVAILABLE ? 1 : 0);
		check(granted == 1 && unavailable == 1, "concurrent entitlement grants have one winner");
		check(ledger.marker == 2 && ledger.provenanceCount == 1,
			"concurrent grant commits one claimed marker and one provenance row");
		check(first.memoryItems.size() + second.memoryItems.size() == 1,
			"concurrent grant mints exactly one physical card");
		check(first.releaseCount == 1 && second.releaseCount == 1,
			"both concurrent grant reservations are released");
	}

	private static void runGrant(CountDownLatch start, ConcurrentGrantPort port,
		AtomicReference<GrantResult> result) {
		try {
			start.await();
			result.set(SubscriptionCardTransactions.grantReservedCard(
				port, GLOBAL_MARKER, "subscription_vendor", "concurrent"));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void testFullInventoryNeverDropsOrConsumesEntitlement() {
		FakeGrantPort port = new FakeGrantPort();
		port.addStatus = GrantStatus.INVENTORY_FULL;

		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "full");

		check(result.getStatus() == GrantStatus.INVENTORY_FULL, "full inventory is reported");
		assertGrantUntouched(port, "full inventory");
		check(port.groundDrops == 0, "full inventory never creates a ground card");
	}

	private static void testUnsupportedClientNeverDropsOrConsumesEntitlement() {
		FakeGrantPort port = new FakeGrantPort();
		port.addStatus = GrantStatus.CLIENT_UNSUPPORTED;

		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "old_client");

		check(result.getStatus() == GrantStatus.CLIENT_UNSUPPORTED, "unsupported client is reported");
		assertGrantUntouched(port, "unsupported client");
		check(port.groundDrops == 0, "unsupported client never creates a ground card");
	}

	private static void testPostAddValidationFailureCleansTentativeItem() {
		FakeGrantPort port = new FakeGrantPort();
		port.failPostAddValidation = true;

		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "bad_item_identity");

		check(result.getStatus() == GrantStatus.FAILED, "invalid post-add identity fails the grant");
		assertGrantUntouched(port, "invalid post-add identity");
		check(port.internalAddCompensations == 1,
			"port compensates its tentative item before reporting add failure");
		check(port.rollbackCount == 0,
			"coordinator does not attempt a second rollback for an unreported mutation");
	}

	private static void testGrantReservationFailureMutatesNothing() {
		FakeGrantPort port = new FakeGrantPort();
		port.reserve = false;

		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "busy");

		check(result.getStatus() == GrantStatus.BUSY, "busy save lifecycle rejects grant");
		assertGrantUntouched(port, "reservation failure");
		check(port.releaseCount == 0, "unowned save reservation is never released");
	}

	private static void testGrantLifecycleInterruptionRollsBackExactItem() {
		FakeGrantPort port = new FakeGrantPort();
		port.interruptAfterAdd = true;

		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "interrupt");

		check(result.getStatus() == GrantStatus.INTERRUPTED, "lifecycle interruption is distinguished");
		assertGrantUntouched(port, "grant interruption");
		check(port.rollbackItemId == 5001L, "grant interruption removes the exact minted item id");
		check(port.releaseCount == 1, "interrupted grant releases reservation after compensation");
	}

	private static void testGrantFaultsRollbackEveryDurableComponent() {
		for (String failure : Arrays.asList("load_marker", "save_inventory", "save_marker", "provenance")) {
			FakeGrantPort port = new FakeGrantPort();
			port.failAt = failure;

			GrantResult result = SubscriptionCardTransactions.grantReservedCard(
				port, GLOBAL_MARKER, "subscription_vendor", "fault=" + failure);

			check(result.getStatus() == GrantStatus.FAILED, failure + " reports failed grant");
			assertGrantUntouched(port, failure);
			check(port.releaseCount == 1, failure + " releases reservation after rollback");
			if (!"load_marker".equals(failure)) {
				check(port.rollbackItemId == 5001L, failure + " compensates the exact minted item");
			}
		}
	}

	private static void testGrantLostCommitAcknowledgementIsReconciled() {
		FakeGrantPort committed = new FakeGrantPort();
		committed.commitAcknowledgementLost = true;
		GrantResult committedResult = SubscriptionCardTransactions.grantReservedCard(
			committed, GLOBAL_MARKER, "subscription_vendor", "lost_ack=committed");
		check(committedResult.isGranted(), "lost grant COMMIT acknowledgement reconciles committed state");
		check(committed.memoryItems.equals(Arrays.asList(5001L)) && committed.dbMarker == 2,
			"reconciled committed grant keeps exact in-memory and durable card");

		FakeGrantPort rolledBack = new FakeGrantPort();
		rolledBack.commitAcknowledgementLost = true;
		rolledBack.commitWasDurable = false;
		GrantResult rolledBackResult = SubscriptionCardTransactions.grantReservedCard(
			rolledBack, GLOBAL_MARKER, "subscription_vendor", "lost_ack=rolled_back");
		check(rolledBackResult.getStatus() == GrantStatus.FAILED,
			"lost grant COMMIT acknowledgement reconciles rolled-back state");
		assertGrantUntouched(rolledBack, "reconciled rolled-back grant");
		check(rolledBack.rollbackItemId == 5001L,
			"reconciled rollback compensates exact tentative card");
	}

	private static void testUnresolvedGrantOutcomeQuarantinesWithoutCompensation() {
		FakeGrantPort port = new FakeGrantPort();
		port.forceUnknownOutcome = true;
		GrantResult result = SubscriptionCardTransactions.grantReservedCard(
			port, GLOBAL_MARKER, "subscription_vendor", "outcome=unknown");

		check(result.getStatus() == GrantStatus.UNCERTAIN, "unresolved grant reports uncertain outcome");
		check(port.quarantineCount == 1, "unresolved grant quarantines the player session");
		check(port.releaseCount == 0, "unresolved grant retains the save reservation");
		check(port.rollbackCount == 0, "unresolved grant does not guess rollback after possible commit");
	}

	private static void testPaidGrantClaimsOldestOneAtATime() {
		FakePaidLedger ledger = new FakePaidLedger(
			paidEntitlement(9L, 42, 200L),
			paidEntitlement(8L, 42, 100L),
			paidEntitlement(7L, 42, 100L));
		FakePaidGrantPort port = new FakePaidGrantPort(ledger, 42, 701, 8001L);

		GrantResult first = SubscriptionCardTransactions.claimPurchasedCard(port);

		check(first.isGranted() && first.getItemId() == 8001L,
			"paid pickup grants the exact physical card instance");
		check("claimed".equals(ledger.row(7L).state),
			"paid pickup chooses oldest created row and id as FIFO tie-breaker");
		check("pending".equals(ledger.row(8L).state) && "pending".equals(ledger.row(9L).state),
			"one vendor interaction claims exactly one paid entitlement");
		check(ledger.row(7L).claimedPlayerId == 701
			&& ledger.row(7L).claimedItemId == VoidSubscription.CARD_ITEM_ID
			&& ledger.row(7L).claimedAtMs == NOW,
			"paid claim stores player, catalog item 1602, and claim time");
		check(port.dbItems.equals(Arrays.asList(8001L))
			&& ledger.provenanceItemIds.equals(Arrays.asList(8001L)),
			"paid entitlement, exact inventory item, and origin commit together");

		GrantResult second = SubscriptionCardTransactions.claimPurchasedCard(port);
		check(second.isGranted() && second.getItemId() == 8002L,
			"a second interaction collects the next purchased card");
		check("claimed".equals(ledger.row(8L).state) && "pending".equals(ledger.row(9L).state),
			"second interaction advances FIFO by one row only");
	}

	private static void testPaidGrantFullAndUnsupportedSafeguards() {
		for (GrantStatus status : Arrays.asList(
			GrantStatus.INVENTORY_FULL, GrantStatus.CLIENT_UNSUPPORTED)) {
			FakePaidLedger ledger = new FakePaidLedger(paidEntitlement(21L, 42, 100L));
			FakePaidGrantPort port = new FakePaidGrantPort(ledger, 42, 702, 8101L);
			port.addStatus = status;

			GrantResult result = SubscriptionCardTransactions.claimPurchasedCard(port);

			check(result.getStatus() == status, status + " is reported for paid pickup");
			assertPaidUntouched(port, ledger, status.toString());
			check(port.groundDrops == 0, status + " can never drop a paid card on the ground");
		}
	}

	private static void testPaidGrantFaultsRollbackEntitlementInventoryAndOrigin() {
		for (String failure : Arrays.asList(
			"load_entitlement", "claim_entitlement", "save_inventory", "provenance")) {
			FakePaidLedger ledger = new FakePaidLedger(paidEntitlement(31L, 42, 100L));
			FakePaidGrantPort port = new FakePaidGrantPort(ledger, 42, 703, 8201L);
			port.failAt = failure;

			GrantResult result = SubscriptionCardTransactions.claimPurchasedCard(port);

			check(result.getStatus() == GrantStatus.FAILED,
				failure + " reports a failed paid pickup");
			assertPaidUntouched(port, ledger, failure);
			if (!"load_entitlement".equals(failure)) {
				check(port.rollbackItemId == 8201L,
					failure + " compensates the exact tentative card instance");
			}
		}
	}

	private static void testPaidGrantConditionalClaimRaceRollsBackExactCard() {
		FakePaidLedger ledger = new FakePaidLedger(paidEntitlement(41L, 42, 100L));
		FakePaidGrantPort port = new FakePaidGrantPort(ledger, 42, 704, 8301L);
		port.loseConditionalClaim = true;

		GrantResult result = SubscriptionCardTransactions.claimPurchasedCard(port);

		check(result.getStatus() == GrantStatus.FAILED,
			"losing the conditional pending-row update cannot grant a card");
		assertPaidUntouched(port, ledger, "conditional claim race");
		check(port.rollbackItemId == 8301L,
			"conditional claim race removes the exact tentative card");
	}

	private static void testPaidGrantLostCommitAcknowledgementAndUnknownOutcome() {
		FakePaidLedger durableLedger = new FakePaidLedger(paidEntitlement(51L, 42, 100L));
		FakePaidGrantPort durable = new FakePaidGrantPort(durableLedger, 42, 705, 8401L);
		durable.commitAcknowledgementLost = true;
		check(SubscriptionCardTransactions.claimPurchasedCard(durable).isGranted(),
			"lost paid-pickup COMMIT acknowledgement reconciles durable settlement");
		check("claimed".equals(durableLedger.row(51L).state)
			&& durable.dbItems.equals(Arrays.asList(8401L)),
			"reconciled paid settlement keeps entitlement and exact inventory item");

		FakePaidLedger revertedLedger = new FakePaidLedger(paidEntitlement(52L, 42, 100L));
		FakePaidGrantPort reverted = new FakePaidGrantPort(revertedLedger, 42, 706, 8402L);
		reverted.commitAcknowledgementLost = true;
		reverted.commitWasDurable = false;
		check(SubscriptionCardTransactions.claimPurchasedCard(reverted).getStatus() == GrantStatus.FAILED,
			"lost paid-pickup acknowledgement reconciles a rolled-back settlement");
		assertPaidUntouched(reverted, revertedLedger, "reconciled paid rollback");
		check(reverted.rollbackItemId == 8402L,
			"reconciled paid rollback compensates exact card instance");

		FakePaidLedger unknownLedger = new FakePaidLedger(paidEntitlement(53L, 42, 100L));
		FakePaidGrantPort unknown = new FakePaidGrantPort(unknownLedger, 42, 707, 8403L);
		unknown.forceUnknownOutcome = true;
		GrantResult unknownResult = SubscriptionCardTransactions.claimPurchasedCard(unknown);
		check(unknownResult.getStatus() == GrantStatus.UNCERTAIN,
			"unresolved paid pickup reports uncertain outcome");
		check(unknown.quarantineCount == 1 && unknown.releaseCount == 0,
			"unresolved paid pickup fences the player and retains save reservation");
		check(unknown.rollbackItemId == -1L && unknown.memoryItems.equals(Arrays.asList(8403L)),
			"unknown paid outcome never guesses a compensating rollback");
	}

	private static void testConcurrentPaidPickupClaimsExactlyOneCard() throws Exception {
		FakePaidLedger ledger = new FakePaidLedger(paidEntitlement(61L, 42, 100L));
		FakePaidGrantPort first = new FakePaidGrantPort(ledger, 42, 708, 8501L);
		FakePaidGrantPort second = new FakePaidGrantPort(ledger, 42, 709, 8502L);
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<GrantResult> firstResult = new AtomicReference<>();
		AtomicReference<GrantResult> secondResult = new AtomicReference<>();
		Thread one = new Thread(() -> runPaidGrant(start, first, firstResult));
		Thread two = new Thread(() -> runPaidGrant(start, second, secondResult));
		one.start();
		two.start();
		start.countDown();
		one.join();
		two.join();

		int granted = (firstResult.get().isGranted() ? 1 : 0)
			+ (secondResult.get().isGranted() ? 1 : 0);
		int unavailable = (firstResult.get().getStatus() == GrantStatus.NOT_AVAILABLE ? 1 : 0)
			+ (secondResult.get().getStatus() == GrantStatus.NOT_AVAILABLE ? 1 : 0);
		check(granted == 1 && unavailable == 1,
			"two concurrent players have exactly one paid-entitlement winner");
		check("claimed".equals(ledger.row(61L).state)
			&& ledger.provenanceItemIds.size() == 1,
			"concurrent pickup commits one claim and one item-origin record");
		check(first.memoryItems.size() + second.memoryItems.size() == 1,
			"concurrent pickup materializes exactly one physical card");
	}

	private static void runPaidGrant(CountDownLatch start, FakePaidGrantPort port,
		AtomicReference<GrantResult> result) {
		try {
			start.await();
			result.set(SubscriptionCardTransactions.claimPurchasedCard(port));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void testPaidTransactionReferenceIsStableAndRedacted() {
		String transactionId = "tbx-sensitive-provider-transaction";
		String first = SubscriptionCardTransactions.paidTransactionReference(transactionId);
		String second = SubscriptionCardTransactions.paidTransactionReference(transactionId);
		check(first.equals(second) && first.length() == 16,
			"paid provenance uses a stable short transaction reference");
		check(!first.contains(transactionId),
			"paid provenance never exposes the raw provider transaction id");
	}

	private static void testInactiveRedemptionCommitsExactConsumptionExpiryAndProvenance() {
		FakeRedeemPort port = new FakeRedeemPort(new SharedLedger(0L), 101L, 202L, 303L);
		port.clickedItemId = 202L;

		RedeemResult result = SubscriptionCardTransactions.redeem(port);

		check(result.getStatus() == RedeemStatus.REDEEMED, "inactive redemption succeeds");
		check(result.isAccountWide(), "linked redemption reports account scope");
		check(!result.wasActive(), "zero stored expiry activates rather than extends");
		check(result.getExpiresAt() == NOW + VoidSubscription.DURATION_MILLIS,
			"inactive redemption adds seven days from transaction time");
		check(port.memoryItems.equals(Arrays.asList(101L, 303L)), "clicked card only is consumed in memory");
		check(port.dbItems.equals(port.memoryItems), "clicked card consumption is durable");
		check(port.ledger.expiry == result.getExpiresAt(), "expiry commits with inventory removal");
		check(port.ledger.provenanceCount == 1 && port.provenanceItemId == 202L,
			"redemption provenance records the exact consumed item id");
		check(port.cachedExpiry == result.getExpiresAt(), "online subscription cache updates after commit");
		check(port.siblingCacheUpdates == 1, "linked online sibling cache is updated after commit");
		check(port.releaseCount == 1, "successful redemption releases reservation");
	}

	private static void testActiveRedemptionExtendsFromStoredExpiry() {
		long existing = NOW + 2L * VoidSubscription.DURATION_MILLIS;
		FakeRedeemPort port = new FakeRedeemPort(new SharedLedger(existing), 401L);
		port.clickedItemId = 401L;

		RedeemResult result = SubscriptionCardTransactions.redeem(port);

		check(result.isRedeemed() && result.wasActive(), "active subscription is extended");
		check(result.getExpiresAt() == existing + VoidSubscription.DURATION_MILLIS,
			"active extension uses fresh stored expiry, not current time or cache");
	}

	private static void testRedemptionRestoresSameItemAndSlotOnEveryFault() {
		for (String failure : Arrays.asList("load_expiry", "save_inventory", "save_expiry", "provenance")) {
			SharedLedger ledger = new SharedLedger(NOW + 1234L);
			FakeRedeemPort port = new FakeRedeemPort(ledger, 11L, 22L, 33L);
			port.clickedItemId = 22L;
			port.failAt = failure;

			RedeemResult result = SubscriptionCardTransactions.redeem(port);

			check(result.getStatus() == RedeemStatus.FAILED, failure + " reports failed redemption");
			check(port.memoryItems.equals(Arrays.asList(11L, 22L, 33L)),
				failure + " restores the same card id at the original slot");
			check(port.dbItems.equals(Arrays.asList(11L, 22L, 33L)),
				failure + " rolls durable inventory back");
			check(ledger.expiry == NOW + 1234L, failure + " rolls subscription expiry back");
			check(ledger.provenanceCount == 0, failure + " rolls provenance back");
			check(port.cachedExpiry == 0L, failure + " does not publish an uncommitted expiry");
			check(port.rollbackItemId == 22L && port.rollbackIndex == 1,
				failure + " compensates exact card identity and slot");
			check(port.releaseCount == 1, failure + " releases reservation after compensation");
		}
	}

	private static void testRedemptionReservationAndLifecycleFailuresAreSafe() {
		FakeRedeemPort busy = new FakeRedeemPort(new SharedLedger(0L), 71L);
		busy.clickedItemId = 71L;
		busy.reserve = false;
		check(SubscriptionCardTransactions.redeem(busy).getStatus() == RedeemStatus.BUSY,
			"busy save lifecycle rejects redemption");
		check(busy.memoryItems.equals(Arrays.asList(71L)) && busy.ledger.expiry == 0L,
			"busy redemption changes nothing");
		check(busy.releaseCount == 0, "unowned redemption reservation is not released");

		FakeRedeemPort interrupted = new FakeRedeemPort(new SharedLedger(0L), 81L, 82L);
		interrupted.clickedItemId = 81L;
		interrupted.interruptAfterRemove = true;
		check(SubscriptionCardTransactions.redeem(interrupted).getStatus() == RedeemStatus.INTERRUPTED,
			"lifecycle interruption aborts redemption");
		check(interrupted.memoryItems.equals(Arrays.asList(81L, 82L)),
			"interruption restores exact clicked card and ordering");
		check(interrupted.ledger.expiry == 0L, "interruption cannot grant subscription time");
	}

	private static void testRedemptionLostCommitAcknowledgementIsReconciled() {
		FakeRedeemPort committed = new FakeRedeemPort(new SharedLedger(0L), 301L);
		committed.clickedItemId = 301L;
		committed.commitAcknowledgementLost = true;
		RedeemResult committedResult = SubscriptionCardTransactions.redeem(committed);
		check(committedResult.isRedeemed(), "lost redeem COMMIT acknowledgement reconciles committed state");
		check(committed.memoryItems.isEmpty()
			&& committed.ledger.expiry == NOW + VoidSubscription.DURATION_MILLIS,
			"reconciled committed redemption keeps card consumed and expiry extended");

		FakeRedeemPort rolledBack = new FakeRedeemPort(new SharedLedger(0L), 302L);
		rolledBack.clickedItemId = 302L;
		rolledBack.commitAcknowledgementLost = true;
		rolledBack.commitWasDurable = false;
		RedeemResult rolledBackResult = SubscriptionCardTransactions.redeem(rolledBack);
		check(rolledBackResult.getStatus() == RedeemStatus.FAILED,
			"lost redeem COMMIT acknowledgement reconciles rolled-back state");
		check(rolledBack.memoryItems.equals(Arrays.asList(302L)) && rolledBack.ledger.expiry == 0L,
			"reconciled redemption rollback restores exact card and expiry");
	}

	private static void testUnresolvedRedemptionOutcomeQuarantinesWithoutCompensation() {
		FakeRedeemPort port = new FakeRedeemPort(new SharedLedger(0L), 401L, 402L);
		port.clickedItemId = 401L;
		port.forceUnknownOutcome = true;
		RedeemResult result = SubscriptionCardTransactions.redeem(port);

		check(result.getStatus() == RedeemStatus.UNCERTAIN,
			"unresolved redemption reports uncertain outcome");
		check(port.quarantineCount == 1, "unresolved redemption quarantines the player session");
		check(port.releaseCount == 0, "unresolved redemption retains save reservation");
		check(port.rollbackItemId == -1L,
			"unresolved redemption does not restore a possibly durably consumed card");
	}

	private static void testMissingClickedCardCannotExtendTime() {
		SharedLedger ledger = new SharedLedger(NOW + 999L);
		FakeRedeemPort port = new FakeRedeemPort(ledger, 91L);
		port.clickedItemId = 9999L;

		RedeemResult result = SubscriptionCardTransactions.redeem(port);

		check(result.getStatus() == RedeemStatus.CARD_NOT_FOUND, "stale item click is rejected");
		check(port.memoryItems.equals(Arrays.asList(91L)), "stale click consumes no other card");
		check(ledger.expiry == NOW + 999L, "stale click cannot extend subscription");
		check(ledger.provenanceCount == 0, "stale click creates no provenance");
	}

	private static void testConcurrentAccountRedemptionsSerializeAndBothExtend() throws Exception {
		SharedLedger ledger = new SharedLedger(0L);
		FakeRedeemPort first = new FakeRedeemPort(ledger, 1001L);
		FakeRedeemPort second = new FakeRedeemPort(ledger, 2002L);
		first.clickedItemId = 1001L;
		second.clickedItemId = 2002L;
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<RedeemResult> firstResult = new AtomicReference<>();
		AtomicReference<RedeemResult> secondResult = new AtomicReference<>();
		Thread one = new Thread(() -> runRedemption(start, first, firstResult));
		Thread two = new Thread(() -> runRedemption(start, second, secondResult));
		one.start();
		two.start();
		start.countDown();
		one.join();
		two.join();

		check(firstResult.get() != null && firstResult.get().isRedeemed(), "first concurrent card redeems");
		check(secondResult.get() != null && secondResult.get().isRedeemed(), "second concurrent card redeems");
		check(ledger.expiry == NOW + 2L * VoidSubscription.DURATION_MILLIS,
			"serialized account read-modify-write preserves both seven-day extensions");
		check(ledger.provenanceCount == 2, "both concurrent consumptions have provenance");
		check(first.memoryItems.isEmpty() && second.memoryItems.isEmpty(),
			"both committed cards are consumed exactly once");
	}

	private static void runRedemption(CountDownLatch start, FakeRedeemPort port,
		AtomicReference<RedeemResult> result) {
		try {
			start.await();
			result.set(SubscriptionCardTransactions.redeem(port));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private static void testExpirySaturatesInsteadOfOverflowing() {
		check(SubscriptionCardTransactions.saturatingAdd(
			Long.MAX_VALUE - 1L, VoidSubscription.DURATION_MILLIS) == Long.MAX_VALUE,
			"expiry addition saturates at Long.MAX_VALUE");
	}

	private static void testCommittedCachePublicationCannotMoveBackward() {
		long firstCommit = NOW + VoidSubscription.DURATION_MILLIS;
		long secondCommit = firstCommit + VoidSubscription.DURATION_MILLIS;
		check(VoidSubscription.monotonicSubscriptionExpiry(secondCommit, firstCommit) == secondCommit,
			"late publication from an earlier account redemption cannot overwrite a newer expiry");
		check(VoidSubscription.monotonicSubscriptionExpiry(firstCommit, secondCommit) == secondCommit,
			"newer account expiry still advances the cache");
		check(VoidSubscription.monotonicSubscriptionExpiry(null, firstCommit) == firstCommit,
			"empty subscription cache accepts its first observed expiry");
	}

	private static void testMarkerValidation() {
		check(PLAYER_MARKER.getScope() == MarkerScope.PLAYER, "player marker exposes scope");
		check("campaign_local_test".equals(PLAYER_MARKER.getKey()), "player marker exposes key");
		check("launch_subcard_2026:77".equals(VoidSubscription.launchCardCacheKey(77)),
			"launch marker is deterministically keyed by immutable player id");
		check(VoidSubscription.launchCardCacheKey(0).isEmpty(),
			"invalid player ids cannot address a launch entitlement");
		check(VoidSubscription.launchCardCacheKey(Integer.MAX_VALUE).length() <= 32,
			"largest player id still fits the player_cache key contract");
		expectIllegalArgument(() -> EntitlementMarker.global("", 1, 2), "empty marker key is rejected");
		expectIllegalArgument(() -> EntitlementMarker.global(
			"123456789012345678901234567890123", 1, 2), "overlong marker key is rejected");
		expectIllegalArgument(() -> EntitlementMarker.player("same", 1, 1),
			"identical marker states are rejected");
	}

	private static void assertGrantUntouched(FakeGrantPort port, String label) {
		check(port.memoryItems.isEmpty(), label + " leaves memory inventory untouched");
		check(port.dbItems.isEmpty(), label + " leaves durable inventory untouched");
		check(port.dbMarker == 1, label + " leaves entitlement available");
		check(port.provenanceCount == 0, label + " creates no item-origin row");
		check(port.memoryMarker == 1, label + " does not publish a claimed player marker");
	}

	private static void assertPaidUntouched(FakePaidGrantPort port, FakePaidLedger ledger,
		String label) {
		check(port.memoryItems.isEmpty(), label + " leaves paid memory inventory untouched");
		check(port.dbItems.isEmpty(), label + " leaves paid durable inventory untouched");
		for (PortalCommerceEntitlement entitlement : ledger.entitlements) {
			check("pending".equals(entitlement.state)
				&& entitlement.claimedPlayerId == 0
				&& entitlement.claimedItemId == 0L
				&& entitlement.claimedAtMs == 0L,
				label + " leaves paid entitlement pending and unclaimed");
		}
		check(ledger.provenanceItemIds.isEmpty(), label + " creates no paid item-origin row");
	}

	private static PortalCommerceEntitlement paidEntitlement(long id, int accountId,
		long createdAtMs) {
		PortalCommerceEntitlement entitlement = new PortalCommerceEntitlement();
		entitlement.id = id;
		entitlement.paymentId = 1000L + id;
		entitlement.accountId = accountId;
		entitlement.provider = "tebex";
		entitlement.transactionId = "tbx-transaction-" + id;
		entitlement.lineKey = "line-" + id;
		entitlement.packageId = "subscription-card";
		entitlement.unitIndex = 1;
		entitlement.state = "pending";
		entitlement.createdAtMs = createdAtMs;
		entitlement.updatedAtMs = createdAtMs;
		return entitlement;
	}

	private static PortalCommerceEntitlement copyEntitlement(PortalCommerceEntitlement source) {
		PortalCommerceEntitlement copy = new PortalCommerceEntitlement();
		copy.id = source.id;
		copy.paymentId = source.paymentId;
		copy.accountId = source.accountId;
		copy.provider = source.provider;
		copy.transactionId = source.transactionId;
		copy.lineKey = source.lineKey;
		copy.packageId = source.packageId;
		copy.unitIndex = source.unitIndex;
		copy.state = source.state;
		copy.claimedPlayerId = source.claimedPlayerId;
		copy.claimedItemId = source.claimedItemId;
		copy.claimedAtMs = source.claimedAtMs;
		copy.createdAtMs = source.createdAtMs;
		copy.updatedAtMs = source.updatedAtMs;
		return copy;
	}

	private static void expectIllegalArgument(Runnable runnable, String message) {
		try {
			runnable.run();
			throw new AssertionError(message);
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeGrantPort implements GrantPort {
		private boolean reserve = true;
		private boolean canComplete = true;
		private boolean interruptAfterAdd;
		private boolean failPostAddValidation;
		private boolean commitAcknowledgementLost;
		private boolean commitWasDurable = true;
		private boolean forceUnknownOutcome;
		private String failAt = "";
		private GrantStatus addStatus = GrantStatus.GRANTED;
		private int dbMarker = 1;
		private int memoryMarker = 1;
		private long nextItemId = 5001L;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private int provenanceCount;
		private long provenanceItemId = -1L;
		private int groundDrops;
		private int releaseCount;
		private int rollbackCount;
		private long rollbackItemId = -1L;
		private int markerCommitCount;
		private int internalAddCompensations;
		private int quarantineCount;
		private MarkerScope savedMarkerScope;
		private long addedItemId = -1L;

		@Override
		public boolean reserveSave() {
			return reserve;
		}

		@Override
		public boolean canComplete() {
			return canComplete;
		}

		@Override
		public AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier) {
			int markerSnapshot = dbMarker;
			List<Long> itemsSnapshot = new ArrayList<>(dbItems);
			int provenanceSnapshot = provenanceCount;
			long provenanceIdSnapshot = provenanceItemId;
			try {
				body.run();
				if (forceUnknownOutcome) {
					return AtomicTransactionOutcome.UNKNOWN;
				}
				if (commitAcknowledgementLost) {
					if (!commitWasDurable) {
						dbMarker = markerSnapshot;
						dbItems.clear();
						dbItems.addAll(itemsSnapshot);
						provenanceCount = provenanceSnapshot;
						provenanceItemId = provenanceIdSnapshot;
					}
					return verifier.verify();
				}
				return AtomicTransactionOutcome.COMMITTED;
			} catch (Exception ex) {
				dbMarker = markerSnapshot;
				dbItems.clear();
				dbItems.addAll(itemsSnapshot);
				provenanceCount = provenanceSnapshot;
				provenanceItemId = provenanceIdSnapshot;
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
		}

		@Override
		public Integer loadMarker(EntitlementMarker marker) throws Exception {
			fail("load_marker");
			return dbMarker;
		}

		@Override
		public AddedCard addCard() {
			if (addStatus != GrantStatus.GRANTED) {
				return AddedCard.failed(addStatus);
			}
			addedItemId = nextItemId++;
			memoryItems.add(addedItemId);
			if (failPostAddValidation) {
				memoryItems.remove(Long.valueOf(addedItemId));
				internalAddCompensations++;
				return AddedCard.failed(GrantStatus.FAILED);
			}
			if (interruptAfterAdd) {
				canComplete = false;
			}
			return AddedCard.granted(addedItemId);
		}

		@Override
		public void saveInventory() throws Exception {
			dbItems.clear();
			dbItems.addAll(memoryItems);
			fail("save_inventory");
		}

		@Override
		public void saveMarker(EntitlementMarker marker) throws Exception {
			savedMarkerScope = marker.getScope();
			dbMarker = 2;
			fail("save_marker");
		}

		@Override
		public void recordGrantProvenance(String source, String extra, long itemId) throws Exception {
			provenanceCount++;
			provenanceItemId = itemId;
			fail("provenance");
		}

		@Override
		public AtomicTransactionOutcome verifyGrantSettlement(EntitlementMarker marker, long itemId) {
			boolean persisted = dbItems.contains(itemId);
			if (dbMarker == 2 && persisted) return AtomicTransactionOutcome.COMMITTED;
			if (dbMarker == 1 && !persisted) return AtomicTransactionOutcome.ROLLED_BACK;
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			quarantineCount++;
		}

		@Override
		public void markerCommitted(EntitlementMarker marker) {
			markerCommitCount++;
			if (marker.getScope() == MarkerScope.PLAYER) {
				memoryMarker = 2;
			}
		}

		@Override
		public void rollbackAddedCard() {
			rollbackCount++;
			rollbackItemId = addedItemId;
			memoryItems.remove(Long.valueOf(addedItemId));
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

	private static final class SharedGrantLedger {
		private int marker = 1;
		private int provenanceCount;
	}

	private static final class ConcurrentGrantPort implements GrantPort {
		private final SharedGrantLedger ledger;
		private final long itemId;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private int releaseCount;

		private ConcurrentGrantPort(SharedGrantLedger ledger, long itemId) {
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
		public AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier) {
			synchronized (ledger) {
				int markerSnapshot = ledger.marker;
				int provenanceSnapshot = ledger.provenanceCount;
				List<Long> dbSnapshot = new ArrayList<>(dbItems);
				try {
					body.run();
					return AtomicTransactionOutcome.COMMITTED;
				} catch (Exception ex) {
					ledger.marker = markerSnapshot;
					ledger.provenanceCount = provenanceSnapshot;
					dbItems.clear();
					dbItems.addAll(dbSnapshot);
					return AtomicTransactionOutcome.ROLLED_BACK;
				}
			}
		}

		@Override
		public Integer loadMarker(EntitlementMarker marker) {
			return ledger.marker;
		}

		@Override
		public AddedCard addCard() {
			memoryItems.add(itemId);
			return AddedCard.granted(itemId);
		}

		@Override
		public void saveInventory() {
			dbItems.clear();
			dbItems.addAll(memoryItems);
		}

		@Override
		public void saveMarker(EntitlementMarker marker) {
			ledger.marker = 2;
		}

		@Override
		public void recordGrantProvenance(String source, String extra, long itemId) {
			ledger.provenanceCount++;
		}

		@Override
		public AtomicTransactionOutcome verifyGrantSettlement(EntitlementMarker marker, long itemId) {
			if (ledger.marker == 2 && dbItems.contains(itemId)) return AtomicTransactionOutcome.COMMITTED;
			if (ledger.marker == 1 && !dbItems.contains(itemId)) return AtomicTransactionOutcome.ROLLED_BACK;
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
		}

		@Override
		public void markerCommitted(EntitlementMarker marker) {
		}

		@Override
		public void rollbackAddedCard() {
			memoryItems.remove(Long.valueOf(itemId));
		}

		@Override
		public void releaseSaveReservation() {
			releaseCount++;
		}
	}

	private static final class FakePaidLedger {
		private final List<PortalCommerceEntitlement> entitlements = new ArrayList<>();
		private final List<Long> provenanceItemIds = new ArrayList<>();

		private FakePaidLedger(PortalCommerceEntitlement... rows) {
			for (PortalCommerceEntitlement row : rows) {
				entitlements.add(copyEntitlement(row));
			}
		}

		private PortalCommerceEntitlement row(long id) {
			for (PortalCommerceEntitlement entitlement : entitlements) {
				if (entitlement.id == id) return entitlement;
			}
			throw new AssertionError("Missing fake paid entitlement " + id);
		}

		private List<PortalCommerceEntitlement> snapshot() {
			List<PortalCommerceEntitlement> snapshot = new ArrayList<>();
			for (PortalCommerceEntitlement entitlement : entitlements) {
				snapshot.add(copyEntitlement(entitlement));
			}
			return snapshot;
		}

		private void restore(List<PortalCommerceEntitlement> snapshot,
			List<Long> provenanceSnapshot) {
			entitlements.clear();
			for (PortalCommerceEntitlement entitlement : snapshot) {
				entitlements.add(copyEntitlement(entitlement));
			}
			provenanceItemIds.clear();
			provenanceItemIds.addAll(provenanceSnapshot);
		}
	}

	private static final class FakePaidGrantPort implements PaidGrantPort {
		private final FakePaidLedger ledger;
		private final int accountId;
		private final int playerId;
		private long nextItemId;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private boolean reserve = true;
		private boolean canComplete = true;
		private boolean commitAcknowledgementLost;
		private boolean commitWasDurable = true;
		private boolean forceUnknownOutcome;
		private boolean loseConditionalClaim;
		private String failAt = "";
		private GrantStatus addStatus = GrantStatus.GRANTED;
		private long addedItemId = -1L;
		private long rollbackItemId = -1L;
		private int releaseCount;
		private int quarantineCount;
		private int groundDrops;

		private FakePaidGrantPort(FakePaidLedger ledger, int accountId, int playerId,
			long nextItemId) {
			this.ledger = ledger;
			this.accountId = accountId;
			this.playerId = playerId;
			this.nextItemId = nextItemId;
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
		public AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			synchronized (ledger) {
				List<PortalCommerceEntitlement> entitlementSnapshot = ledger.snapshot();
				List<Long> provenanceSnapshot = new ArrayList<>(ledger.provenanceItemIds);
				List<Long> inventorySnapshot = new ArrayList<>(dbItems);
				try {
					body.run();
					if (forceUnknownOutcome) {
						return AtomicTransactionOutcome.UNKNOWN;
					}
					if (commitAcknowledgementLost) {
						if (!commitWasDurable) {
							ledger.restore(entitlementSnapshot, provenanceSnapshot);
							dbItems.clear();
							dbItems.addAll(inventorySnapshot);
						}
						return verifier.verify();
					}
					return AtomicTransactionOutcome.COMMITTED;
				} catch (Exception ex) {
					ledger.restore(entitlementSnapshot, provenanceSnapshot);
					dbItems.clear();
					dbItems.addAll(inventorySnapshot);
					return AtomicTransactionOutcome.ROLLED_BACK;
				}
			}
		}

		@Override
		public PortalCommerceEntitlement loadOldestPendingEntitlement() throws Exception {
			fail("load_entitlement");
			PortalCommerceEntitlement oldest = null;
			for (PortalCommerceEntitlement candidate : ledger.entitlements) {
				if (candidate.accountId != accountId || !"pending".equals(candidate.state)) continue;
				if (oldest == null
					|| candidate.createdAtMs < oldest.createdAtMs
					|| candidate.createdAtMs == oldest.createdAtMs && candidate.id < oldest.id) {
					oldest = candidate;
				}
			}
			return oldest == null ? null : copyEntitlement(oldest);
		}

		@Override
		public AddedCard addCard() {
			if (addStatus != GrantStatus.GRANTED) {
				return AddedCard.failed(addStatus);
			}
			addedItemId = nextItemId++;
			memoryItems.add(addedItemId);
			return AddedCard.granted(addedItemId);
		}

		@Override
		public long currentTimeMillis() {
			return NOW;
		}

		@Override
		public boolean claimEntitlement(PortalCommerceEntitlement entitlement, long claimedAtMs)
			throws Exception {
			if (loseConditionalClaim) return false;
			PortalCommerceEntitlement durable = ledger.row(entitlement.id);
			if (durable.accountId != accountId
				|| !"pending".equals(durable.state)
				|| durable.claimedPlayerId != 0
				|| durable.claimedItemId != 0L
				|| durable.claimedAtMs != 0L) {
				return false;
			}
			durable.state = "claimed";
			durable.claimedPlayerId = playerId;
			durable.claimedItemId = VoidSubscription.CARD_ITEM_ID;
			durable.claimedAtMs = claimedAtMs;
			durable.updatedAtMs = claimedAtMs;
			fail("claim_entitlement");
			return true;
		}

		@Override
		public void saveInventory() throws Exception {
			dbItems.clear();
			dbItems.addAll(memoryItems);
			fail("save_inventory");
		}

		@Override
		public void recordPaidGrantProvenance(PortalCommerceEntitlement entitlement,
			long itemId) throws Exception {
			ledger.provenanceItemIds.add(itemId);
			fail("provenance");
		}

		@Override
		public AtomicTransactionOutcome verifyPaidGrantSettlement(
			PortalCommerceEntitlement entitlement, long itemId) {
			PortalCommerceEntitlement durable = ledger.row(entitlement.id);
			boolean cardPersisted = dbItems.contains(itemId);
			if (durable.accountId == accountId
				&& "claimed".equals(durable.state)
				&& durable.claimedPlayerId == playerId
				&& durable.claimedItemId == VoidSubscription.CARD_ITEM_ID
				&& cardPersisted) {
				return AtomicTransactionOutcome.COMMITTED;
			}
			if (durable.accountId == accountId
				&& "pending".equals(durable.state)
				&& durable.claimedPlayerId == 0
				&& durable.claimedItemId == 0L
				&& durable.claimedAtMs == 0L
				&& !cardPersisted) {
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			quarantineCount++;
		}

		@Override
		public void rollbackAddedCard() {
			rollbackItemId = addedItemId;
			memoryItems.remove(Long.valueOf(addedItemId));
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

	private static final class SharedLedger {
		private long expiry;
		private int provenanceCount;

		private SharedLedger(long expiry) {
			this.expiry = expiry;
		}
	}

	private static final class FakeRedeemPort implements RedeemPort {
		private final SharedLedger ledger;
		private final List<Long> memoryItems = new ArrayList<>();
		private final List<Long> dbItems = new ArrayList<>();
		private boolean reserve = true;
		private boolean canComplete = true;
		private boolean accountWide = true;
		private boolean interruptAfterRemove;
		private boolean commitAcknowledgementLost;
		private boolean commitWasDurable = true;
		private boolean forceUnknownOutcome;
		private String failAt = "";
		private long clickedItemId = -1L;
		private long removedItemId = -1L;
		private int removedIndex = -1;
		private long rollbackItemId = -1L;
		private int rollbackIndex = -1;
		private long provenanceItemId = -1L;
		private long cachedExpiry;
		private int siblingCacheUpdates;
		private int releaseCount;
		private int quarantineCount;

		private FakeRedeemPort(SharedLedger ledger, Long... itemIds) {
			this.ledger = ledger;
			memoryItems.addAll(Arrays.asList(itemIds));
			dbItems.addAll(memoryItems);
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
		public AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier) {
			synchronized (ledger) {
				long expirySnapshot = ledger.expiry;
				int provenanceSnapshot = ledger.provenanceCount;
				List<Long> itemsSnapshot = new ArrayList<>(dbItems);
				try {
					body.run();
					if (forceUnknownOutcome) {
						return AtomicTransactionOutcome.UNKNOWN;
					}
					if (commitAcknowledgementLost) {
						if (!commitWasDurable) {
							ledger.expiry = expirySnapshot;
							ledger.provenanceCount = provenanceSnapshot;
							dbItems.clear();
							dbItems.addAll(itemsSnapshot);
						}
						return verifier.verify();
					}
					return AtomicTransactionOutcome.COMMITTED;
				} catch (Exception ex) {
					ledger.expiry = expirySnapshot;
					ledger.provenanceCount = provenanceSnapshot;
					dbItems.clear();
					dbItems.addAll(itemsSnapshot);
					return AtomicTransactionOutcome.ROLLED_BACK;
				}
			}
		}

		@Override
		public boolean isAccountWide() {
			return accountWide;
		}

		@Override
		public boolean removeExactCard() {
			removedIndex = memoryItems.indexOf(clickedItemId);
			if (removedIndex < 0) {
				return false;
			}
			removedItemId = memoryItems.remove(removedIndex);
			if (interruptAfterRemove) {
				canComplete = false;
			}
			return true;
		}

		@Override
		public long currentTimeMillis() {
			return NOW;
		}

		@Override
		public Long loadSubscriptionExpiry() throws Exception {
			fail("load_expiry");
			return ledger.expiry;
		}

		@Override
		public void saveInventory() throws Exception {
			dbItems.clear();
			dbItems.addAll(memoryItems);
			fail("save_inventory");
		}

		@Override
		public void saveSubscriptionExpiry(long expiresAt) throws Exception {
			ledger.expiry = expiresAt;
			fail("save_expiry");
		}

		@Override
		public void recordRedeemProvenance(boolean accountWide, boolean wasActive, long expiresAt)
			throws Exception {
			ledger.provenanceCount++;
			provenanceItemId = removedItemId;
			fail("provenance");
		}

		@Override
		public AtomicTransactionOutcome verifyRedeemSettlement(long previousExpiry, long intendedExpiry) {
			boolean persisted = dbItems.contains(removedItemId);
			if (!persisted && ledger.expiry == intendedExpiry) return AtomicTransactionOutcome.COMMITTED;
			if (persisted && ledger.expiry == previousExpiry) return AtomicTransactionOutcome.ROLLED_BACK;
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public long removedCardItemId() {
			return removedItemId;
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			quarantineCount++;
		}

		@Override
		public void subscriptionCommitted(long expiresAt) {
			cachedExpiry = expiresAt;
			if (accountWide) {
				siblingCacheUpdates++;
			}
		}

		@Override
		public void rollbackRemovedCard() {
			rollbackItemId = removedItemId;
			rollbackIndex = removedIndex;
			memoryItems.add(removedIndex, removedItemId);
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
}
