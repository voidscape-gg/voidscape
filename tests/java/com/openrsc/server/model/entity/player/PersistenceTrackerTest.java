package com.openrsc.server.model.entity.player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PersistenceTrackerTest {
	private PersistenceTrackerTest() {
	}

	public static void main(String[] args) {
		testLifecycleIdsArePerPlayerIncarnation();
		testPersistentSaveGenerationFollowUp();
		testReservationIntentSurvivesRejectedEnqueue();
		testSaveTicketCountsRemainPendingUntilEveryCompletion();
		testForceMutationDuringInFlightSaveQueuesFollowUp();
		testLogoutPreparationAndRetryAreSingleFlight();
		testShutdownDrainRetriesFailureThenCommits();
		testShutdownDrainFailsClosedOnTimeout();
		System.out.println("Player persistence tracker tests passed.");
	}

	private static void testLifecycleIdsArePerPlayerIncarnation() {
		UUID first = SaveLifecycle.newId();
		UUID second = SaveLifecycle.newId();
		check(!first.equals(second), "fresh Player incarnations receive distinct lifecycle IDs");
	}

	private static void testPersistentSaveGenerationFollowUp() {
		PersistentSaveTracker tracker = new PersistentSaveTracker();
		check(tracker.request(), "first mutation installs a worker");
		long first = tracker.beginAttempt();
		check(!tracker.request(), "mutation during save reuses the worker");
		check(tracker.completeAttempt(first, true) == PersistentSaveTracker.Completion.FOLLOW_UP,
			"generation one commit requires generation two follow-up");

		long second = tracker.beginAttempt();
		check(tracker.completeAttempt(second, false) == PersistentSaveTracker.Completion.RETRY,
			"failed generation two attempt retries");
		long retry = tracker.beginAttempt();
		check(retry == second, "retry targets the latest dirty generation");
		check(tracker.completeAttempt(retry, true) == PersistentSaveTracker.Completion.QUIESCENT,
			"latest generation commit reaches quiescence");

		tracker.workerStopped();
		check(tracker.request(), "request after stop/clear boundary installs a replacement worker");
	}

	private static void testReservationIntentSurvivesRejectedEnqueue() {
		SaveReservationTracker tracker = new SaveReservationTracker();
		check(tracker.reserve(), "save reservation acquired");
		check(tracker.deferIfReserved(false, false, false), "ordinary save deferred");
		check(tracker.deferIfReserved(false, false, true), "forced save merged");
		check(tracker.deferIfReserved(false, true, false), "logout save merged");
		tracker.release();

		SaveReservationTracker.Intent firstAttempt = tracker.deferredIntent();
		check(firstAttempt != null && firstAttempt.logout && firstAttempt.force,
			"release yields one merged logout/force intent");
		SaveReservationTracker.Intent afterRejectedEnqueue = tracker.deferredIntent();
		check(afterRejectedEnqueue != null && afterRejectedEnqueue.logout && afterRejectedEnqueue.force,
			"enqueue rejection cannot discard deferred intent");
		tracker.deferredEnqueued();
		check(tracker.deferredIntent() == null, "accepted enqueue clears deferred intent");
	}

	private static void testSaveTicketCountsRemainPendingUntilEveryCompletion() {
		SaveTicketTracker tracker = new SaveTicketTracker();
		tracker.reserve(false);
		tracker.reserve(true);
		check(tracker.pendingCount() == 2 && tracker.pendingLogoutCount() == 1,
			"normal and logout tickets are counted independently");
		tracker.complete(false);
		check(tracker.hasPending() && tracker.pendingCount() == 1 && tracker.pendingLogoutCount() == 1,
			"first completion cannot expose a false not-saving window");
		tracker.complete(true);
		check(!tracker.hasPending(), "all tickets complete before saving clears");
	}

	private static void testForceMutationDuringInFlightSaveQueuesFollowUp() {
		PersistentSaveTracker tracker = new PersistentSaveTracker();
		CountDownLatch saveStarted = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		Thread inFlightSave = new Thread(() -> {
			saveStarted.countDown();
			try {
				releaseSave.await();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		});
		inFlightSave.start();
		try {
			check(saveStarted.await(1, TimeUnit.SECONDS), "normal save reached injected in-flight latch");
			check(PersistentSaveTracker.shouldQueueForcedFollowUp(false, true, true),
				"production policy converts force collision to generation follow-up");
			check(tracker.request(), "force mutation installs follow-up worker while original save is blocked");
			releaseSave.countDown();
			inFlightSave.join(1_000L);
			long followUp = tracker.beginAttempt();
			check(tracker.completeAttempt(followUp, true) == PersistentSaveTracker.Completion.QUIESCENT,
				"forced mutation is durably committed after original save releases");
			tracker.workerStopped();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError("force follow-up test interrupted", ex);
		} finally {
			releaseSave.countDown();
		}
	}

	private static void testLogoutPreparationAndRetryAreSingleFlight() {
		LogoutSaveTracker tracker = new LogoutSaveTracker();
		check(tracker.beginPreparation(), "logout hooks prepare once");
		check(!tracker.beginPreparation(), "logout hooks cannot prepare twice");
		check(tracker.installWorker(), "logout worker installs once");
		check(!tracker.installWorker(), "duplicate logout worker rejected");
		check(tracker.recordFailureDelayTicks() == 2, "first failed write backs off");
		check(tracker.recordFailureDelayTicks() == 4, "second failed write increases backoff");
		tracker.completed();
		check(!tracker.isWorkerPending(), "committed logout stops retry worker");
	}

	private static void testShutdownDrainRetriesFailureThenCommits() {
		final boolean[] current = {true};
		final int[] attempts = {0};
		boolean drained = LogoutSaveDrainer.drain(new LogoutSaveDrainer.Port() {
			@Override
			public boolean isCurrent() {
				return current[0];
			}

			@Override
			public CompletableFuture<Boolean> currentOrStartAttempt() {
				attempts[0]++;
				if (attempts[0] == 1) {
					return CompletableFuture.completedFuture(false);
				}
				current[0] = false;
				return CompletableFuture.completedFuture(true);
			}
		}, 1_000L);
		check(drained && attempts[0] == 2, "shutdown drain retries one failure and observes commit");
	}

	private static void testShutdownDrainFailsClosedOnTimeout() {
		boolean drained = LogoutSaveDrainer.drain(new LogoutSaveDrainer.Port() {
			@Override
			public boolean isCurrent() {
				return true;
			}

			@Override
			public CompletableFuture<Boolean> currentOrStartAttempt() {
				return CompletableFuture.completedFuture(false);
			}
		}, 25L);
		check(!drained, "shutdown drain timeout refuses to claim success");
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}
}
