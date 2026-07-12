package com.openrsc.server.database;

import com.openrsc.server.util.checked.CheckedRunnable;

/** Deterministic phase-fault tests for the tri-state transaction outcome contract. */
public final class AtomicTransactionOutcomeTest {
	private AtomicTransactionOutcomeTest() {
	}

	public static void main(String[] args) {
		check(run("", new int[4]) == AtomicTransactionOutcome.COMMITTED,
			"successful transaction is committed");

		int[] startFailure = new int[4];
		check(run("start", startFailure) == AtomicTransactionOutcome.ROLLED_BACK,
			"start failure with successful rollback is confirmed rolled back");
		check(startFailure[1] == 0 && startFailure[2] == 0 && startFailure[3] == 1,
			"start failure skips body/commit and attempts rollback");

		int[] bodyFailure = new int[4];
		check(run("body", bodyFailure) == AtomicTransactionOutcome.ROLLED_BACK,
			"body failure with successful rollback is confirmed rolled back");
		check(bodyFailure[2] == 0 && bodyFailure[3] == 1,
			"body failure skips commit and attempts rollback");

		check(run("commit", new int[4]) == AtomicTransactionOutcome.COMMIT_UNCERTAIN,
			"commit acknowledgement failure is not mislabeled as rollback");
		check(run("body+rollback", new int[4]) == AtomicTransactionOutcome.UNKNOWN,
			"failed rollback after body failure has unknown outcome");
		check(run("commit+rollback", new int[4]) == AtomicTransactionOutcome.UNKNOWN,
			"failed rollback after commit failure has unknown outcome");
		System.out.println("Atomic transaction outcome tests passed.");
	}

	private static AtomicTransactionOutcome run(String failure, int[] calls) {
		CheckedRunnable<Exception> start = () -> phase("start", failure, calls, 0);
		CheckedRunnable<Exception> body = () -> phase("body", failure, calls, 1);
		CheckedRunnable<Exception> commit = () -> phase("commit", failure, calls, 2);
		CheckedRunnable<Exception> rollback = () -> phase("rollback", failure, calls, 3);
		return GameDatabase.executeAtomicTransaction(start, body, commit, rollback);
	}

	private static void phase(String phase, String failure, int[] calls, int index) throws Exception {
		calls[index]++;
		if (failure.contains(phase)) {
			throw new Exception("injected " + phase + " failure");
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}
