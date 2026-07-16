package com.openrsc.server.plugins;

public final class GatherRepeatBufferTest {

	private GatherRepeatBufferTest() {
	}

	public static void main(String[] args) {
		testSpamCoalescesForTheBoundTarget();
		testDifferentTargetAndOptionAreRejected();
		testRejectedClickDoesNotReplacePendingTarget();
		testExistingEarnedRepeatConsumesThePendingClick();
		testFinalAttemptAddsExactlyOneManualRepeat();
		testClearDropsThePendingClick();
		testBindAndResetLifecycle();
		testEntityPreludeLifecycle();
		testBusyWalkInterruptionTruthTable();
		System.out.println("Gather repeat buffer tests passed");
	}

	private static void testSpamCoalescesForTheBoundTarget() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);
		for (int i = 0; i < 20; i++) {
			assertTrue(buffer.queueIfMatches(1, 138, 639, 0),
				"same-target clicks must be accepted");
		}
		assertTrue(buffer.hasPending(), "spam must leave one pending click");
		GatherRepeatBuffer.AttemptBoundary tail =
			buffer.resolveAttemptBoundary(1, 1);
		assertManualTail(tail,
			"one pending click at the final boundary must add one attempt");
		GatherRepeatBuffer.AttemptBoundary complete =
			buffer.resolveAttemptBoundary(1, 1);
		assertFalse(complete.startsManualTail(),
			"coalesced spam must not add a second attempt");
		assertEquals(1, complete.getCurrentProgress(),
			"the unbuffered tail must complete normally");
		assertEquals(1, complete.getTotalBatch(),
			"the manual tail must remain exactly one attempt");
	}

	private static void testDifferentTargetAndOptionAreRejected() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);
		assertFalse(buffer.queueIfMatches(1, 139, 639, 0),
			"a different coordinate must not replace the target");
		assertFalse(buffer.queueIfMatches(1, 138, 639, 1),
			"a different option must not replace the target");
		assertFalse(buffer.queueIfMatches(2, 138, 639, 0),
			"a different object id must not replace the target");
		assertFalse(buffer.hasPending(), "rejected clicks must not queue work");
	}

	private static void testRejectedClickDoesNotReplacePendingTarget() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);
		assertTrue(buffer.queueIfMatches(1, 138, 639, 0),
			"the bound target must queue");
		assertFalse(buffer.queueIfMatches(1, 139, 639, 0),
			"a different target must be rejected");
		assertTrue(buffer.hasPending(),
			"a rejected click must not erase the already-buffered target");
		assertManualTail(buffer.resolveAttemptBoundary(1, 1),
			"the original buffered target must still add its tail");
	}

	private static void testExistingEarnedRepeatConsumesThePendingClick() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);
		buffer.queueIfMatches(1, 138, 639, 0);
		GatherRepeatBuffer.AttemptBoundary earned =
			buffer.resolveAttemptBoundary(1, 2);
		assertFalse(earned.startsManualTail(),
			"an already-scheduled earned repeat must satisfy the click");
		assertEquals(1, earned.getCurrentProgress(),
			"earned progress must remain unchanged");
		assertEquals(2, earned.getTotalBatch(),
			"the earned batch size must remain unchanged");
		assertFalse(buffer.hasPending(), "the satisfied click must be consumed");
	}

	private static void testFinalAttemptAddsExactlyOneManualRepeat() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);

		// Level-10 style batch: attempt one already has an earned attempt two.
		buffer.queueIfMatches(1, 138, 639, 0);
		assertFalse(buffer.resolveAttemptBoundary(1, 2).startsManualTail(),
			"clicking attempt one must not turn a two-attempt batch into three");

		// A click during the final earned attempt adds exactly attempt three.
		buffer.queueIfMatches(1, 138, 639, 0);
		GatherRepeatBuffer.AttemptBoundary tail =
			buffer.resolveAttemptBoundary(2, 2);
		assertManualTail(tail,
			"clicking the final attempt must create a one-attempt continuation");
		GatherRepeatBuffer.AttemptBoundary complete =
			buffer.resolveAttemptBoundary(1, 1);
		assertFalse(complete.startsManualTail(),
			"that continuation must not become a fresh earned batch");
		assertEquals(1, complete.getCurrentProgress(),
			"the one-attempt continuation must finish at one");
		assertEquals(1, complete.getTotalBatch(),
			"the one-attempt continuation must not restore the earned size");
	}

	private static void testClearDropsThePendingClick() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		buffer.bind(1, 138, 639, 0);
		buffer.queueIfMatches(1, 138, 639, 0);
		buffer.clearPending();
		assertFalse(buffer.hasPending(), "explicit cancellation must clear the click");
		assertFalse(buffer.resolveAttemptBoundary(1, 1).startsManualTail(),
			"a cleared click must not continue the batch");
	}

	private static void testBindAndResetLifecycle() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		assertFalse(buffer.queueIfMatches(1, 138, 639, 0),
			"an unbound buffer must reject clicks");
		buffer.bind(1, 138, 639, 0);
		buffer.queueIfMatches(1, 138, 639, 0);
		buffer.bind(2, 200, 500, 1);
		assertFalse(buffer.hasPending(), "rebinding must clear stale input");
		assertFalse(buffer.queueIfMatches(1, 138, 639, 0),
			"rebinding must reject the old target");
		assertTrue(buffer.queueIfMatches(2, 200, 500, 1),
			"rebinding must accept the new target");
		buffer.reset();
		assertFalse(buffer.isBound(), "reset must remove the target binding");
		assertFalse(buffer.hasPending(), "reset must clear pending input");
		assertFalse(buffer.queueIfMatches(2, 200, 500, 1),
			"a reset buffer must reject clicks");
	}

	private static void testEntityPreludeLifecycle() {
		GatherRepeatBuffer buffer = new GatherRepeatBuffer();
		assertFalse(buffer.expectObjectCommand(),
			"an unbound buffer must not suppress entity-walk cancellation");
		buffer.bind(1, 138, 639, 0);
		assertTrue(buffer.expectObjectCommand(),
			"a bound gather may await its paired object command");
		assertTrue(buffer.isAwaitingObjectCommand(),
			"the entity prelude must remain pending until its semantic command");
		assertTrue(buffer.queueIfMatches(1, 138, 639, 0),
			"the paired same-target command must queue");
		assertFalse(buffer.isAwaitingObjectCommand(),
			"a matching semantic command must consume the prelude");
		buffer.expectObjectCommand();
		buffer.clearPending();
		assertFalse(buffer.isAwaitingObjectCommand(),
			"explicit cancellation must clear an unresolved prelude");
	}

	private static void testBusyWalkInterruptionTruthTable() {
		assertFalse(GatherInputPolicy.shouldInterruptBusyWalk(true, true, false),
			"an entity prewalk must not interrupt its active gathering action");
		assertTrue(GatherInputPolicy.shouldInterruptBusyWalk(true, true, true),
			"a point walk must cancel active gathering");
		assertTrue(GatherInputPolicy.shouldInterruptBusyWalk(true, false, false),
			"a non-gather entity prewalk must preserve prior interruption behavior");
		assertTrue(GatherInputPolicy.shouldInterruptBusyWalk(true, false, true),
			"a non-gather point walk must preserve prior interruption behavior");
		assertFalse(GatherInputPolicy.shouldInterruptBusyWalk(false, true, true),
			"batching-disabled worlds must preserve their prior busy-walk behavior");
	}

	private static void assertManualTail(
		GatherRepeatBuffer.AttemptBoundary boundary, String message
	) {
		assertTrue(boundary.startsManualTail(), message);
		assertEquals(0, boundary.getCurrentProgress(),
			"a manual tail must start before its only attempt");
		assertEquals(1, boundary.getTotalBatch(),
			"a manual tail must contain exactly one attempt");
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean condition, String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(
				message + " (expected " + expected + ", got " + actual + ")");
		}
	}
}
