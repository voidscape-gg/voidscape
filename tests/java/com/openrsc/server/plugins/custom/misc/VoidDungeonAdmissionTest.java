package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Executable fault tests for the production Void Dungeon admission transaction.
 * This deliberately uses no test framework so the repository's Java 8 toolchain can run it.
 */
public final class VoidDungeonAdmissionTest {
	private VoidDungeonAdmissionTest() {
	}

	public static void main(String[] args) {
		testSuccessfulAdmission();
		testSaveReservationFailure();
		testSaveEnqueueFailure();
		testNullSaveResultRepairsRollback();
		testThrownSaveRequestRepairsRollback();
		testDatabaseSaveFailure();
		testLogoutDuringSave();
		testLateSaveCompletionSchedulesRepair();
		testDeathDuringSaveConsumesFee();
		testFailedRefundDoesNotClaimInventoryReturn();
		testDungeonDeathClearsAndPersists();
		testOrdinaryDeathDoesNotClearAdmission();
		System.out.println("Void Dungeon admission fault tests passed.");
	}

	private static void testSuccessfulAdmission() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = CompletableFuture.completedFuture(true);

		check(VoidDungeonAdmission.purchaseAndPersist(admission), "successful save admits player");
		check(admission.admitted, "successful save retains admission");
		check(admission.coins == 0, "successful save charges exactly one entry fee");
		check(admission.releaseCount == 1, "successful save releases reservation");
		check(admission.repairCount == 0, "successful save needs no repair");
	}

	private static void testSaveReservationFailure() {
		FakeAdmission admission = new FakeAdmission();
		admission.reserveResult = false;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "reservation failure denies admission");
		check(admission.coins == VoidDungeonAdmission.ENTRY_FEE_COINS,
			"reservation failure cannot charge coins");
		check(!admission.admitted, "reservation failure cannot set admission");
		check(admission.releaseCount == 0, "unowned reservation is not released");
	}

	private static void testSaveEnqueueFailure() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = CompletableFuture.completedFuture(false);

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "enqueue rejection denies admission");
		assertRolledBack(admission, "enqueue rejection");
		check(admission.repairCount == 1, "enqueue rejection schedules persisted rollback");
	}

	private static void testNullSaveResultRepairsRollback() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = null;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "null save result denies admission");
		assertRolledBack(admission, "null save result");
		check(admission.repairCount == 1, "null save result immediately persists compensation");
	}

	private static void testThrownSaveRequestRepairsRollback() {
		FakeAdmission admission = new FakeAdmission();
		admission.throwOnSave = true;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "thrown save request denies admission");
		assertRolledBack(admission, "thrown save request");
		check(admission.repairCount == 1, "thrown save request immediately persists compensation");
	}

	private static void testDatabaseSaveFailure() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = new CompletableFuture<>();
		admission.saveResult.completeExceptionally(new IllegalStateException("injected DB failure"));

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "database failure denies admission");
		assertRolledBack(admission, "database failure");
		check(admission.repairCount == 1, "database failure schedules persisted rollback");
	}

	private static void testLogoutDuringSave() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = new CompletableFuture<>();
		admission.canComplete = false;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "logout interruption denies admission");
		assertRolledBack(admission, "logout interruption");
		check(admission.repairCount == 0, "in-flight save waits before repair");
	}

	private static void testLateSaveCompletionSchedulesRepair() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = new CompletableFuture<>();
		admission.interruptAfterDelay = true;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "lifecycle interruption rolls back");
		assertRolledBack(admission, "late completion");
		check(admission.repairCount == 0, "repair is not scheduled before the save completes");
		admission.saveResult.complete(true);
		check(admission.repairCount == 1, "late completion schedules a second persisted rollback");
	}

	private static void testDeathDuringSaveConsumesFee() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = new CompletableFuture<>();
		admission.interruptAfterDelay = true;
		admission.refundAllowed = false;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "death interruption denies admission");
		check(!admission.admitted, "death interruption clears admission");
		check(admission.coins == 0, "death interruption cannot protect the fee from Wilderness loss");
		check(admission.messages.stream().anyMatch(message -> message.contains("fee is consumed")),
			"death interruption explains that the fee was consumed");
		check(admission.repairCount == 0, "compensation save waits for the original save completion");
		admission.saveResult.complete(true);
		check(admission.repairCount == 1, "late original commit is followed by compensation persistence");
	}

	private static void testFailedRefundDoesNotClaimInventoryReturn() {
		FakeAdmission admission = new FakeAdmission();
		admission.saveResult = CompletableFuture.completedFuture(false);
		admission.refundResult = false;

		check(!VoidDungeonAdmission.purchaseAndPersist(admission), "failed refund denies admission");
		check(admission.coins == 0, "failed inventory re-add is not counted as an inventory refund");
		check(admission.messages.stream().anyMatch(message -> message.contains("could not be returned and is consumed")),
			"failed inventory re-add never claims the coins were returned to inventory");
	}

	private static void testDungeonDeathClearsAndPersists() {
		int[] calls = new int[2];
		boolean handled = VoidDungeon.handleDeath(Point.location(72, 3252),
			() -> calls[0]++, () -> calls[1]++);

		check(handled, "dungeon death is handled");
		check(calls[0] == 1, "dungeon death clears admission once");
		check(calls[1] == 1, "dungeon death requests persistence once");
	}

	private static void testOrdinaryDeathDoesNotClearAdmission() {
		int[] calls = new int[2];
		boolean handled = VoidDungeon.handleDeath(Point.location(120, 648),
			() -> calls[0]++, () -> calls[1]++);

		check(!handled, "ordinary death bypasses dungeon reset");
		check(calls[0] == 0, "ordinary death retains admission");
		check(calls[1] == 0, "ordinary death does not request dungeon persistence");
		check(!VoidDungeon.shouldClearAdmissionOnDeath(null), "missing death point is fail-safe");
	}

	private static void assertRolledBack(FakeAdmission admission, String label) {
		check(!admission.admitted, label + " clears admission");
		check(admission.coins == VoidDungeonAdmission.ENTRY_FEE_COINS,
			label + " returns exactly one entry fee");
		check(admission.releaseCount == 1, label + " releases reservation");
		check(admission.messages.stream().anyMatch(message -> message.contains("coins are returned")),
			label + " tells the player coins were returned");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static final class FakeAdmission implements VoidDungeonAdmission.AdmissionPort {
		private boolean admitted;
		private boolean reserveResult = true;
		private boolean canComplete = true;
		private boolean refundAllowed = true;
		private boolean refundResult = true;
		private boolean interruptAfterDelay;
		private boolean throwOnSave;
		private int coins = VoidDungeonAdmission.ENTRY_FEE_COINS;
		private int releaseCount;
		private int repairCount;
		private CompletableFuture<Boolean> saveResult = CompletableFuture.completedFuture(true);
		private final List<String> messages = new ArrayList<>();

		@Override
		public boolean isAdmitted() {
			return admitted;
		}

		@Override
		public boolean reserveSave() {
			return reserveResult;
		}

		@Override
		public boolean hasEntryFee() {
			return coins >= VoidDungeonAdmission.ENTRY_FEE_COINS;
		}

		@Override
		public boolean removeEntryFee() {
			if (!hasEntryFee()) {
				return false;
			}
			coins -= VoidDungeonAdmission.ENTRY_FEE_COINS;
			return true;
		}

		@Override
		public boolean canRefundEntryFee() {
			return refundAllowed;
		}

		@Override
		public boolean returnEntryFee() {
			if (refundResult) {
				coins += VoidDungeonAdmission.ENTRY_FEE_COINS;
			}
			return refundResult;
		}

		@Override
		public void markAdmitted() {
			admitted = true;
		}

		@Override
		public void clearAdmission() {
			admitted = false;
		}

		@Override
		public CompletableFuture<Boolean> saveReservedAsync() {
			if (throwOnSave) {
				throw new IllegalStateException("injected save request failure");
			}
			return saveResult;
		}

		@Override
		public boolean canCompleteAdmission() {
			return canComplete;
		}

		@Override
		public void delayTick() {
			if (interruptAfterDelay) {
				canComplete = false;
			}
		}

		@Override
		public void message(String message) {
			messages.add(message);
		}

		@Override
		public void syncInventory() {
		}

		@Override
		public void repairRolledBackSave() {
			repairCount++;
		}

		@Override
		public void releaseSaveReservation() {
			releaseCount++;
		}
	}
}
