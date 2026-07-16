package orsc.duelproof;

import com.voidscape.duelproof.DuelProofCodec;
import com.voidscape.duelproof.DuelProofContext;
import com.voidscape.duelproof.DuelProofCrypto;
import com.voidscape.duelproof.DuelProofSpec;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import orsc.multiclient.ClientPort;

/** Dependency-free focused checks for client context attestation and commit/reveal/lock. */
public final class DuelProofHandshakeClientTest {

	private static final int CONTEXT_CHUNK_BYTES = 160;
	private static final byte[] PROOF_A = filled(16, 0x01);
	private static final byte[] PROOF_B = filled(16, 0x02);
	private static final byte[] CONTEXT_BYTES_A = canonicalContext(PROOF_A, "Alpha", "Beta");
	private static final byte[] CONTEXT_BYTES_B = canonicalContext(PROOF_B, "Alpha", "Beta");
	private static final byte[] CONTEXT_HASH_A = DuelProofCrypto.contextHash(CONTEXT_BYTES_A);
	private static final byte[] CONTEXT_HASH_B = DuelProofCrypto.contextHash(CONTEXT_BYTES_B);
	private static final byte[] SERVER_COMMIT_A = sequence(0x50);
	private static final byte[] SERVER_COMMIT_B = sequence(0x70);
	private static final byte[] CLIENT_SEED_A = sequence(0x90);
	private static final byte[] CLIENT_SEED_B = sequence(0xb0);
	private static final byte[] OTHER_COMMIT = filled(32, 0x5a);

	private static final DuelProofHandshakeClient.ContextValidator ACCEPT_CONTEXT =
		new DuelProofHandshakeClient.ContextValidator() {
			@Override
			public boolean validate(DuelProofContext context, int ownOrdinal) {
				return context != null && (ownOrdinal == 0 || ownOrdinal == 1);
			}
		};

	private static int checkedOutboundCount;

	private DuelProofHandshakeClientTest() {
	}

	public static void main(String[] args) throws Exception {
		testValidFlowAndExactDuplicates();
		testLockedAnchorsSurviveMutableTrafficAndRemainBounded();
		testContextMustBeCompleteAuthenticAndLocallyAccepted();
		testContextChunkOrderingAndDuplicateGuards();
		testSecretAndContextWipesOnAbortResetAndNewProof();
		testEntropyFailureIsStickyAndWipesPartialBytes();
		testMalformedAndStateRejections();
		assertTrue("outbound length checks ran", checkedOutboundCount >= 18);
		System.out.println("Duel proof client handshake tests passed");
	}

	private static void testValidFlowAndExactDuplicates() throws Exception {
		DuelProofHandshakeClient client = new DuelProofHandshakeClient();
		DeterministicPort port = new DeterministicPort(CLIENT_SEED_A);
		Recorder recorder = new Recorder();
		RecordingValidator validator = new RecordingValidator(PROOF_A, 0, true);
		String proofId = DuelProofCodec.hexLower(PROOF_A);

		assertConsumed("first context chunk", client,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), port, validator, recorder);
		byte[] retainedChunk = pendingContextChunk(client, 0);
		int beforeDuplicate = recorder.size();
		assertConsumed("exact context duplicate", client,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), port, validator, recorder);
		assertInt("exact context duplicate sends no response", beforeDuplicate, recorder.size());
		sendRemainingContext(client, PROOF_A, CONTEXT_BYTES_A, 1, port, validator, recorder);

		String commitControl = commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0);
		byte[] clientCommit = DuelProofCrypto.clientCommitment(
			CONTEXT_HASH_A, SERVER_COMMIT_A, 0, CLIENT_SEED_A);
		String expectedCommit = "v1|commit|" + proofId + "|"
			+ DuelProofCodec.hexLower(clientCommit);

		assertConsumed("initial commit", client, commitControl, port, validator, recorder);
		assertString("commit response", expectedCommit, recorder.last());
		assertInt("context validator calls", 1, validator.calls);
		assertAllZero("context chunk wiped after commit", retainedChunk);
		assertPendingContextCleared(client);
		assertConsumed("duplicate commit", client, commitControl, port, validator, recorder);
		assertString("duplicate commit response", expectedCommit, recorder.last());
		assertInt("duplicate commit entropy calls", 1, port.calls);
		assertInt("duplicate commit validation calls", 1, validator.calls);

		String revealControl = revealControl(PROOF_A, 7, clientCommit, 42, OTHER_COMMIT);
		String expectedReveal = "v1|reveal|" + proofId + "|"
			+ DuelProofCodec.hexLower(CLIENT_SEED_A);
		assertConsumed("initial reveal", client, revealControl, port, validator, recorder);
		assertString("reveal response", expectedReveal, recorder.last());
		assertConsumed("duplicate reveal", client, revealControl, port, validator, recorder);
		assertString("duplicate reveal response", expectedReveal, recorder.last());

		byte[] lockHash = DuelProofCrypto.finalLockHash(PROOF_A, CONTEXT_HASH_A,
			SERVER_COMMIT_A, 7, clientCommit, 42, OTHER_COMMIT);
		String lockControl = lockControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A,
			7, clientCommit, 42, OTHER_COMMIT, lockHash);
		String expectedAck = "v1|ack|" + proofId + "|" + DuelProofCodec.hexLower(lockHash);
		assertConsumed("initial lock", client, lockControl, port, validator, recorder);
		assertString("lock acknowledgement", expectedAck, recorder.last());
		assertArray("seed retained after lock", CLIENT_SEED_A, activeSeed(client));

		assertConsumed("duplicate lock", client, lockControl, port, validator, recorder);
		assertString("duplicate lock acknowledgement", expectedAck, recorder.last());
		assertConsumed("identical reveal after lock", client, revealControl, port,
			validator, recorder);
		assertString("retained-seed reveal response", expectedReveal, recorder.last());
		assertTrue("bare latest request requires retained lock",
			client.requiresLatestLockedWitness(true));
		assertTrue("retained lock matches latest terminal witness",
			client.matchesLockedWitnessIfApplicable(true, PROOF_A, CONTEXT_HASH_A,
				SERVER_COMMIT_A, 7, clientCommit, 42, OTHER_COMMIT, lockHash));
		assertTrue("different proof cannot replace retained latest lock",
			!client.matchesLockedWitnessIfApplicable(true, PROOF_B, CONTEXT_HASH_B,
				SERVER_COMMIT_B, 7, clientCommit, 42, OTHER_COMMIT, lockHash));
		assertTrue("unretained historical proof remains self-contained",
			client.matchesLockedWitnessIfApplicable(false, PROOF_B, CONTEXT_HASH_B,
				SERVER_COMMIT_B,
				7, clientCommit, 42, OTHER_COMMIT, lockHash));
		assertTrue("retained historical proof with changed context is rejected",
			!client.matchesLockedWitnessIfApplicable(false, PROOF_A, CONTEXT_HASH_B,
				SERVER_COMMIT_A,
				7, clientCommit, 42, OTHER_COMMIT, lockHash));
		recorder.assertWithinCap();
	}

	private static void testLockedAnchorsSurviveMutableTrafficAndRemainBounded()
		throws Exception {
		DuelProofHandshakeClient client = new DuelProofHandshakeClient();
		Recorder recorder = new Recorder();
		byte[][] firstLock = completeLock(client, PROOF_A, CONTEXT_BYTES_A, CONTEXT_HASH_A,
			SERVER_COMMIT_A, CLIENT_SEED_A, 0, OTHER_COMMIT, recorder);
		byte[] retainedContextHash = retainedAnchorBytes(client, PROOF_A, "contextHash");

		DeterministicPort secondPort = new DeterministicPort(CLIENT_SEED_B);
		assertConsumed("different context starts while lock is retained", client,
			contextControl(PROOF_B, CONTEXT_BYTES_B, 0), secondPort, ACCEPT_CONTEXT, recorder);
		assertArray("different context does not erase retained anchor", CONTEXT_HASH_A,
			retainedContextHash);
		assertTrue("latest anchor survives different context traffic",
			client.matchesLockedWitnessIfApplicable(true, PROOF_A, CONTEXT_HASH_A,
				SERVER_COMMIT_A, 7, firstLock[0], 42, OTHER_COMMIT, firstLock[1]));
		assertTrue("different proof id cannot sidestep surviving latest anchor",
			!client.matchesLockedWitnessIfApplicable(true, PROOF_B, CONTEXT_HASH_B,
				SERVER_COMMIT_B, 7, firstLock[0], 42, OTHER_COMMIT, firstLock[1]));

		sendRemainingContext(client, PROOF_B, CONTEXT_BYTES_B, 1, secondPort,
			ACCEPT_CONTEXT, recorder);
		assertConsumed("second commit", client,
			commitControl(PROOF_B, CONTEXT_HASH_B, SERVER_COMMIT_B, 1), secondPort,
			ACCEPT_CONTEXT, recorder);
		byte[] secondCommit = DuelProofCrypto.clientCommitment(
			CONTEXT_HASH_B, SERVER_COMMIT_B, 1, CLIENT_SEED_B);
		assertConsumed("second reveal", client,
			revealControl(PROOF_B, 7, OTHER_COMMIT, 42, secondCommit), secondPort,
			ACCEPT_CONTEXT, recorder);
		byte[] secondLockHash = DuelProofCrypto.finalLockHash(PROOF_B, CONTEXT_HASH_B,
			SERVER_COMMIT_B, 7, OTHER_COMMIT, 42, secondCommit);
		assertConsumed("second lock", client,
			lockControl(PROOF_B, CONTEXT_HASH_B, SERVER_COMMIT_B, 7, OTHER_COMMIT,
				42, secondCommit, secondLockHash), secondPort, ACCEPT_CONTEXT, recorder);
		assertTrue("newest retained lock is required for latest receipt",
			client.matchesLockedWitnessIfApplicable(true, PROOF_B, CONTEXT_HASH_B,
				SERVER_COMMIT_B, 7, OTHER_COMMIT, 42, secondCommit, secondLockHash));
		assertTrue("older retained lock still protects explicit history",
			client.matchesLockedWitnessIfApplicable(false, PROOF_A, CONTEXT_HASH_A,
				SERVER_COMMIT_A, 7, firstLock[0], 42, OTHER_COMMIT, firstLock[1]));
		byte[] retiredProofId = retainedAnchorBytes(client, PROOF_B, "proofId");

		assertConsumed("abort clears only mutable state", client,
			DuelProofHandshakeClient.CONTROL_PREFIX + "v1|abort|"
				+ DuelProofCodec.hexLower(PROOF_B) + "|timeout", secondPort,
			ACCEPT_CONTEXT, recorder);
		assertTrue("abort does not erase newest locked proof",
			client.matchesLockedWitnessIfApplicable(true, PROOF_B, CONTEXT_HASH_B,
				SERVER_COMMIT_B, 7, OTHER_COMMIT, 42, secondCommit, secondLockHash));
		assertConsumed("retreat retires its terminal-less lock", client,
			DuelProofHandshakeClient.CONTROL_PREFIX + "v1|abort|"
				+ DuelProofCodec.hexLower(PROOF_B) + "|retreat", secondPort,
			ACCEPT_CONTEXT, recorder);
		assertAllZero("retreat wipes retired anchor bytes", retiredProofId);
		assertInt("retreat removes only its matching lock", 1, retainedAnchorCount(client));
		assertTrue("retreat falls latest lock back to prior completed duel",
			client.matchesLockedWitnessIfApplicable(true, PROOF_A, CONTEXT_HASH_A,
				SERVER_COMMIT_A, 7, firstLock[0], 42, OTHER_COMMIT, firstLock[1]));
		client.reset();
		assertAllZero("reset wipes retained anchor bytes", retainedContextHash);
		assertTrue("reset removes latest anchor requirement",
			!client.requiresLatestLockedWitness(true));

		DuelProofHandshakeClient bounded = new DuelProofHandshakeClient();
		Recorder boundedRecorder = new Recorder();
		byte[] eldestAnchorProofId = null;
		for (int index = 0; index < 18; index++) {
			byte[] proofId = filled(DuelProofSpec.PROOF_ID_BYTES, 0x20 + index);
			byte[] contextBytes = canonicalContext(proofId, "Alpha", "Beta");
			byte[] contextHash = DuelProofCrypto.contextHash(contextBytes);
			byte[] serverCommitment = sequence(0x30 + index);
			byte[] clientSeed = sequence(0x80 + index);
			byte[] otherCommitment = filled(DuelProofSpec.HASH_BYTES, 0x60 + index);
			completeLock(bounded, proofId, contextBytes, contextHash, serverCommitment,
				clientSeed, 0, otherCommitment, boundedRecorder);
			if (index == 0) {
				eldestAnchorProofId = retainedAnchorBytes(bounded, proofId, "proofId");
			}
		}
		assertInt("retained anchor map is bounded", 16, retainedAnchorCount(bounded));
		assertAllZero("evicted retained anchor is wiped", eldestAnchorProofId);
		bounded.reset();
		assertInt("reset clears retained anchor map", 0, retainedAnchorCount(bounded));
		recorder.assertWithinCap();
		boundedRecorder.assertWithinCap();
	}

	private static void testContextMustBeCompleteAuthenticAndLocallyAccepted() throws Exception {
		String proofId = DuelProofCodec.hexLower(PROOF_A);

		DuelProofHandshakeClient missing = new DuelProofHandshakeClient();
		DeterministicPort missingPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder missingRecorder = new Recorder();
		assertConsumed("incomplete context first chunk", missing,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), missingPort, ACCEPT_CONTEXT,
			missingRecorder);
		byte[] missingChunk = pendingContextChunk(missing, 0);
		assertConsumed("commit rejects missing context chunks", missing,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), missingPort,
			ACCEPT_CONTEXT, missingRecorder);
		assertString("missing chunks failure", "v1|fail|" + proofId + "|state",
			missingRecorder.last());
		assertInt("missing chunks request no entropy", 0, missingPort.calls);
		assertAllZero("missing chunks are wiped", missingChunk);

		DuelProofHandshakeClient tampered = new DuelProofHandshakeClient();
		DeterministicPort tamperedPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder tamperedRecorder = new Recorder();
		byte[] changedContext = Arrays.copyOf(CONTEXT_BYTES_A, CONTEXT_BYTES_A.length);
		changedContext[changedContext.length - 1] ^= 1;
		sendAllContext(tampered, PROOF_A, changedContext, tamperedPort, ACCEPT_CONTEXT,
			tamperedRecorder);
		assertConsumed("commit rejects changed context", tampered,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), tamperedPort,
			ACCEPT_CONTEXT, tamperedRecorder);
		assertString("changed context failure", "v1|fail|" + proofId + "|state",
			tamperedRecorder.last());
		assertInt("changed context requests no entropy", 0, tamperedPort.calls);

		DuelProofHandshakeClient rejected = new DuelProofHandshakeClient();
		DeterministicPort rejectedPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder rejectedRecorder = new Recorder();
		RecordingValidator rejectingValidator = new RecordingValidator(PROOF_A, 0, false);
		sendAllContext(rejected, PROOF_A, CONTEXT_BYTES_A, rejectedPort,
			rejectingValidator, rejectedRecorder);
		byte[] rejectedChunk = pendingContextChunk(rejected, 0);
		assertConsumed("local context rejection", rejected,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), rejectedPort,
			rejectingValidator, rejectedRecorder);
		assertString("local context failure", "v1|fail|" + proofId + "|state",
			rejectedRecorder.last());
		assertInt("local validator called", 1, rejectingValidator.calls);
		assertInt("local rejection requests no entropy", 0, rejectedPort.calls);
		assertAllZero("locally rejected context is wiped", rejectedChunk);

		DuelProofHandshakeClient embeddedId = new DuelProofHandshakeClient();
		DeterministicPort embeddedPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder embeddedRecorder = new Recorder();
		sendAllContext(embeddedId, PROOF_B, CONTEXT_BYTES_A, embeddedPort, ACCEPT_CONTEXT,
			embeddedRecorder);
		byte[] hash = DuelProofCrypto.contextHash(CONTEXT_BYTES_A);
		assertConsumed("commit rejects embedded proof id mismatch", embeddedId,
			commitControl(PROOF_B, hash, SERVER_COMMIT_B, 1), embeddedPort,
			ACCEPT_CONTEXT, embeddedRecorder);
		assertString("embedded id failure", "v1|fail|" + DuelProofCodec.hexLower(PROOF_B)
			+ "|state", embeddedRecorder.last());
		assertInt("embedded id requests no entropy", 0, embeddedPort.calls);
		Arrays.fill(hash, (byte) 0);
	}

	private static void testContextChunkOrderingAndDuplicateGuards() throws Exception {
		String proofId = DuelProofCodec.hexLower(PROOF_A);

		DuelProofHandshakeClient conflict = new DuelProofHandshakeClient();
		Recorder conflictRecorder = new Recorder();
		DeterministicPort conflictPort = new DeterministicPort(CLIENT_SEED_A);
		assertConsumed("conflict setup", conflict,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), conflictPort, ACCEPT_CONTEXT,
			conflictRecorder);
		byte[] retained = pendingContextChunk(conflict, 0);
		byte[] conflictingBytes = Arrays.copyOf(CONTEXT_BYTES_A, CONTEXT_BYTES_A.length);
		conflictingBytes[0] ^= 1;
		assertConsumed("conflicting duplicate", conflict,
			contextControl(PROOF_A, conflictingBytes, 0), conflictPort, ACCEPT_CONTEXT,
			conflictRecorder);
		assertString("conflicting duplicate failure", "v1|fail|" + proofId + "|state",
			conflictRecorder.last());
		assertAllZero("conflicting duplicate wipes retained chunk", retained);

		DuelProofHandshakeClient outOfOrder = new DuelProofHandshakeClient();
		Recorder orderRecorder = new Recorder();
		DeterministicPort orderPort = new DeterministicPort(CLIENT_SEED_A);
		assertConsumed("out-of-order context", outOfOrder,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 1), orderPort, ACCEPT_CONTEXT,
			orderRecorder);
		assertString("out-of-order failure", "v1|fail|" + proofId + "|state",
			orderRecorder.last());

		DuelProofHandshakeClient totalChange = new DuelProofHandshakeClient();
		Recorder totalRecorder = new Recorder();
		DeterministicPort totalPort = new DeterministicPort(CLIENT_SEED_A);
		assertConsumed("total change setup", totalChange,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), totalPort, ACCEPT_CONTEXT,
			totalRecorder);
		String changedTotal = DuelProofHandshakeClient.CONTROL_PREFIX + "v1|context|"
			+ proofId + "|1|" + (contextChunkCount(CONTEXT_BYTES_A) + 1) + "|00";
		assertConsumed("context total change", totalChange, changedTotal, totalPort,
			ACCEPT_CONTEXT, totalRecorder);
		assertString("total change failure", "v1|fail|" + proofId + "|state",
			totalRecorder.last());
	}

	private static void testSecretAndContextWipesOnAbortResetAndNewProof() throws Exception {
		Recorder recorder = new Recorder();

		DuelProofHandshakeClient aborted = new DuelProofHandshakeClient();
		DeterministicPort abortPort = new DeterministicPort(CLIENT_SEED_A);
		sendAllContext(aborted, PROOF_A, CONTEXT_BYTES_A, abortPort, ACCEPT_CONTEXT, recorder);
		assertConsumed("abort setup", aborted,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), abortPort,
			ACCEPT_CONTEXT, recorder);
		byte[] abortSeed = activeSeed(aborted);
		assertConsumed("foreign abort is ignored", aborted, DuelProofHandshakeClient.CONTROL_PREFIX
			+ "v1|abort|" + DuelProofCodec.hexLower(PROOF_B) + "|timeout", abortPort,
			ACCEPT_CONTEXT, recorder);
		assertArray("foreign abort retains active seed", CLIENT_SEED_A, activeSeed(aborted));
		int beforeAbort = recorder.size();
		assertConsumed("valid abort", aborted, DuelProofHandshakeClient.CONTROL_PREFIX
			+ "v1|abort|" + DuelProofCodec.hexLower(PROOF_A) + "|timeout", abortPort,
			ACCEPT_CONTEXT, recorder);
		assertInt("abort sends no response", beforeAbort, recorder.size());
		assertAllZero("abort seed wipe", abortSeed);
		assertNull("abort clears active seed", activeSeed(aborted));

		DuelProofHandshakeClient reset = new DuelProofHandshakeClient();
		DeterministicPort resetPort = new DeterministicPort(CLIENT_SEED_A);
		assertConsumed("reset context setup", reset,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), resetPort, ACCEPT_CONTEXT, recorder);
		byte[] resetContext = pendingContextChunk(reset, 0);
		reset.reset();
		assertAllZero("reset context wipe", resetContext);
		assertPendingContextCleared(reset);

		DuelProofHandshakeClient replaced = new DuelProofHandshakeClient();
		DeterministicPort replacePort = new DeterministicPort(CLIENT_SEED_A, CLIENT_SEED_B);
		sendAllContext(replaced, PROOF_A, CONTEXT_BYTES_A, replacePort, ACCEPT_CONTEXT, recorder);
		assertConsumed("first proof setup", replaced,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), replacePort,
			ACCEPT_CONTEXT, recorder);
		byte[] replacedSeed = activeSeed(replaced);
		assertConsumed("new proof context replaces active proof", replaced,
			contextControl(PROOF_B, CONTEXT_BYTES_B, 0), replacePort, ACCEPT_CONTEXT, recorder);
		assertAllZero("new proof context wipes prior seed", replacedSeed);
		sendRemainingContext(replaced, PROOF_B, CONTEXT_BYTES_B, 1, replacePort,
			ACCEPT_CONTEXT, recorder);
		assertConsumed("new proof commit", replaced,
			commitControl(PROOF_B, CONTEXT_HASH_B, SERVER_COMMIT_B, 1), replacePort,
			ACCEPT_CONTEXT, recorder);
		assertArray("new proof owns new seed", CLIENT_SEED_B, activeSeed(replaced));
		assertInt("new proof entropy calls", 2, replacePort.calls);
		recorder.assertWithinCap();
	}

	private static void testEntropyFailureIsStickyAndWipesPartialBytes() throws Exception {
		DuelProofHandshakeClient client = new DuelProofHandshakeClient();
		FailingEntropyPort failingPort = new FailingEntropyPort();
		Recorder recorder = new Recorder();
		String proofId = DuelProofCodec.hexLower(PROOF_A);
		String control = commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0);
		sendAllContext(client, PROOF_A, CONTEXT_BYTES_A, failingPort, ACCEPT_CONTEXT, recorder);

		assertConsumed("entropy failure", client, control, failingPort, ACCEPT_CONTEXT, recorder);
		assertString("entropy failure response", "v1|fail|" + proofId + "|entropy",
			recorder.last());
		assertAllZero("partially filled entropy buffer", failingPort.destination);
		assertNull("entropy failure leaves no active seed", activeSeed(client));

		DeterministicPort laterWorkingPort = new DeterministicPort(CLIENT_SEED_A);
		assertConsumed("same proof remains rejected", client, control, laterWorkingPort,
			ACCEPT_CONTEXT, recorder);
		assertString("sticky entropy failure", "v1|fail|" + proofId + "|entropy",
			recorder.last());
		assertInt("rejected proof does not request new entropy", 0, laterWorkingPort.calls);
		recorder.assertWithinCap();
	}

	private static void testMalformedAndStateRejections() throws Exception {
		String proofId = DuelProofCodec.hexLower(PROOF_A);

		DuelProofHandshakeClient malformed = new DuelProofHandshakeClient();
		DeterministicPort malformedPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder malformedRecorder = new Recorder();
		String malformedChunk = DuelProofHandshakeClient.CONTROL_PREFIX + "v1|context|"
			+ proofId + "|0|1|AA";
		assertConsumed("malformed lowercase context hex", malformed, malformedChunk,
			malformedPort, ACCEPT_CONTEXT, malformedRecorder);
		assertString("malformed response", "v1|fail|" + proofId + "|malformed",
			malformedRecorder.last());
		assertConsumed("malformed proof remains poisoned", malformed,
			contextControl(PROOF_A, CONTEXT_BYTES_A, 0), malformedPort, ACCEPT_CONTEXT,
			malformedRecorder);
		assertString("sticky malformed response", "v1|fail|" + proofId + "|malformed",
			malformedRecorder.last());
		assertInt("malformed proof requests no entropy", 0, malformedPort.calls);

		DuelProofHandshakeClient malformedContext = new DuelProofHandshakeClient();
		DeterministicPort malformedContextPort = new DeterministicPort(CLIENT_SEED_A);
		Recorder malformedContextRecorder = new Recorder();
		byte[] invalidCanonical = Arrays.copyOf(CONTEXT_BYTES_A, CONTEXT_BYTES_A.length);
		invalidCanonical[0] ^= 1;
		byte[] invalidHash = DuelProofCrypto.contextHash(invalidCanonical);
		sendAllContext(malformedContext, PROOF_A, invalidCanonical, malformedContextPort,
			ACCEPT_CONTEXT, malformedContextRecorder);
		assertConsumed("malformed canonical context", malformedContext,
			commitControl(PROOF_A, invalidHash, SERVER_COMMIT_A, 0), malformedContextPort,
			ACCEPT_CONTEXT, malformedContextRecorder);
		assertString("malformed context response", "v1|fail|" + proofId + "|malformed",
			malformedContextRecorder.last());
		Arrays.fill(invalidHash, (byte) 0);

		DuelProofHandshakeClient state = new DuelProofHandshakeClient();
		DeterministicPort statePort = new DeterministicPort(CLIENT_SEED_A);
		Recorder stateRecorder = new Recorder();
		sendAllContext(state, PROOF_A, CONTEXT_BYTES_A, statePort, ACCEPT_CONTEXT, stateRecorder);
		assertConsumed("state setup", state,
			commitControl(PROOF_A, CONTEXT_HASH_A, SERVER_COMMIT_A, 0), statePort,
			ACCEPT_CONTEXT, stateRecorder);
		byte[] stateSeed = activeSeed(state);
		byte[] actualCommit = DuelProofCrypto.clientCommitment(
			CONTEXT_HASH_A, SERVER_COMMIT_A, 0, CLIENT_SEED_A);
		assertConsumed("mismatched own commitment", state,
			revealControl(PROOF_A, 7, OTHER_COMMIT, 42, actualCommit), statePort,
			ACCEPT_CONTEXT, stateRecorder);
		assertString("state rejection response", "v1|fail|" + proofId + "|state",
			stateRecorder.last());
		assertAllZero("state rejection seed wipe", stateSeed);
		assertNull("state rejection clears active seed", activeSeed(state));
		malformedRecorder.assertWithinCap();
		stateRecorder.assertWithinCap();
	}

	private static byte[][] completeLock(DuelProofHandshakeClient client, byte[] proofId,
			byte[] contextBytes, byte[] contextHash, byte[] serverCommitment,
			byte[] clientSeed, int ordinal, byte[] otherCommitment, Recorder recorder) {
		DeterministicPort port = new DeterministicPort(clientSeed);
		sendAllContext(client, proofId, contextBytes, port, ACCEPT_CONTEXT, recorder);
		assertConsumed("complete lock commit", client,
			commitControl(proofId, contextHash, serverCommitment, ordinal), port,
			ACCEPT_CONTEXT, recorder);
		byte[] clientCommitment = DuelProofCrypto.clientCommitment(contextHash,
			serverCommitment, ordinal, clientSeed);
		byte[] firstCommitment = ordinal == 0 ? clientCommitment : otherCommitment;
		byte[] secondCommitment = ordinal == 1 ? clientCommitment : otherCommitment;
		assertConsumed("complete lock reveal", client,
			revealControl(proofId, 7, firstCommitment, 42, secondCommitment), port,
			ACCEPT_CONTEXT, recorder);
		byte[] finalLockHash = DuelProofCrypto.finalLockHash(proofId, contextHash,
			serverCommitment, 7, firstCommitment, 42, secondCommitment);
		assertConsumed("complete lock", client,
			lockControl(proofId, contextHash, serverCommitment, 7, firstCommitment,
				42, secondCommitment, finalLockHash), port, ACCEPT_CONTEXT, recorder);
		return new byte[][] {clientCommitment, finalLockHash};
	}

	private static void sendAllContext(DuelProofHandshakeClient client, byte[] proofId,
									 byte[] contextBytes, ClientPort port,
									 DuelProofHandshakeClient.ContextValidator validator,
									 Recorder recorder) {
		sendRemainingContext(client, proofId, contextBytes, 0, port, validator, recorder);
	}

	private static void sendRemainingContext(DuelProofHandshakeClient client, byte[] proofId,
									   byte[] contextBytes, int firstChunk, ClientPort port,
									   DuelProofHandshakeClient.ContextValidator validator,
									   Recorder recorder) {
		for (int chunk = firstChunk; chunk < contextChunkCount(contextBytes); chunk++) {
			assertConsumed("context chunk " + chunk, client,
				contextControl(proofId, contextBytes, chunk), port, validator, recorder);
		}
	}

	private static String contextControl(byte[] proofId, byte[] contextBytes, int chunk) {
		int total = contextChunkCount(contextBytes);
		if (chunk < 0 || chunk >= total) throw new IllegalArgumentException("bad chunk");
		int start = chunk * CONTEXT_CHUNK_BYTES;
		int end = Math.min(contextBytes.length, start + CONTEXT_CHUNK_BYTES);
		byte[] bytes = Arrays.copyOfRange(contextBytes, start, end);
		try {
			return DuelProofHandshakeClient.CONTROL_PREFIX + "v1|context|"
				+ DuelProofCodec.hexLower(proofId) + "|" + chunk + "|" + total + "|"
				+ DuelProofCodec.hexLower(bytes);
		} finally {
			Arrays.fill(bytes, (byte) 0);
		}
	}

	private static int contextChunkCount(byte[] contextBytes) {
		return (contextBytes.length + CONTEXT_CHUNK_BYTES - 1) / CONTEXT_CHUNK_BYTES;
	}

	private static String commitControl(byte[] proofId, byte[] contextHash,
										byte[] serverCommitment, int ordinal) {
		return DuelProofHandshakeClient.CONTROL_PREFIX + "v1|commit|"
			+ DuelProofCodec.hexLower(proofId) + "|" + DuelProofCodec.hexLower(contextHash)
			+ "|" + DuelProofCodec.hexLower(serverCommitment) + "|" + ordinal;
	}

	private static String revealControl(byte[] proofId, int firstPlayerId,
										byte[] firstCommitment, int secondPlayerId,
										byte[] secondCommitment) {
		return DuelProofHandshakeClient.CONTROL_PREFIX + "v1|reveal|"
			+ DuelProofCodec.hexLower(proofId) + "|" + firstPlayerId + "|"
			+ DuelProofCodec.hexLower(firstCommitment) + "|" + secondPlayerId + "|"
			+ DuelProofCodec.hexLower(secondCommitment);
	}

	private static String lockControl(byte[] proofId, byte[] contextHash,
								  byte[] serverCommitment, int firstPlayerId,
								  byte[] firstCommitment, int secondPlayerId,
								  byte[] secondCommitment, byte[] lockHash) {
		return DuelProofHandshakeClient.CONTROL_PREFIX + "v1|lock|"
			+ DuelProofCodec.hexLower(proofId) + "|" + DuelProofCodec.hexLower(contextHash)
			+ "|" + DuelProofCodec.hexLower(serverCommitment) + "|" + firstPlayerId + "|"
			+ DuelProofCodec.hexLower(firstCommitment) + "|" + secondPlayerId + "|"
			+ DuelProofCodec.hexLower(secondCommitment) + "|" + DuelProofCodec.hexLower(lockHash);
	}

	private static byte[] canonicalContext(byte[] proofId, String firstName, String secondName) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes);
			output.write("VSDPCTX3".getBytes(StandardCharsets.US_ASCII));
			output.writeInt(DuelProofSpec.CONTEXT_VERSION);
			output.writeInt(DuelProofSpec.PROTOCOL_VERSION);
			output.writeInt(DuelProofSpec.RNG_VERSION);
			output.writeInt(DuelProofSpec.CLASSIC_MELEE_FORMULA_VERSION);
			output.write(proofId);
			output.writeByte(DuelProofSpec.DUEL_RULE_NO_MAGIC);
			output.writeInt(40);
			writeParticipant(output, 0, 7, firstName, 57, 50, 60, 40, 50,
				55, 60, 45, 50, 30, 40, 0, 12, 13, 14, 10, 100, false);
			writeParticipant(output, 1, 42, secondName, 58, 45, 55, 48, 55,
				52, 58, 44, 50, 25, 35, 3, 11, 12, 15, 20, 2, true);
			output.flush();
			return bytes.toByteArray();
		} catch (IOException impossible) {
			throw new AssertionError(impossible);
		}
	}

	private static void writeParticipant(DataOutputStream output, int ordinal, int playerId,
									 String username, int combatLevel,
									 int currentAttack, int maximumAttack,
									 int currentDefence, int maximumDefence,
									 int currentStrength, int maximumStrength,
									 int currentHits, int maximumHits,
									 int currentPrayer, int maximumPrayer,
									 int style, int aim, int power, int armour,
									 int stakeItemId, int stakeAmount, boolean noted)
		throws IOException {
		output.writeByte(ordinal);
		output.writeInt(playerId);
		byte[] name = username.getBytes(StandardCharsets.US_ASCII);
		output.writeByte(name.length);
		output.write(name);
		output.writeInt(combatLevel);
		output.writeInt(currentAttack);
		output.writeInt(maximumAttack);
		output.writeInt(currentDefence);
		output.writeInt(maximumDefence);
		output.writeInt(currentStrength);
		output.writeInt(maximumStrength);
		output.writeInt(currentHits);
		output.writeInt(maximumHits);
		output.writeInt(currentPrayer);
		output.writeInt(maximumPrayer);
		output.writeByte(style);
		output.writeInt(aim);
		output.writeInt(power);
		output.writeInt(armour);
		output.writeBoolean(false);
		output.writeBoolean(false);
		output.writeBoolean(false);
		output.writeInt(0);
		output.writeByte(14);
		for (int slot = 0; slot < 14; slot++) {
			output.writeInt(-1);
			output.writeInt(0);
			output.writeBoolean(false);
		}
		output.writeBoolean(false);
		output.writeInt(0);
		output.writeByte(1);
		output.writeByte(0);
		output.writeInt(stakeItemId);
		output.writeInt(stakeAmount);
		output.writeBoolean(noted);
	}

	private static void assertConsumed(String label, DuelProofHandshakeClient client,
									 String control, ClientPort port,
									 DuelProofHandshakeClient.ContextValidator validator,
									 Recorder recorder) {
		assertTrue(label, client.handleServerMessage(control, port, validator, recorder));
	}

	private static byte[] activeSeed(DuelProofHandshakeClient client) throws Exception {
		Field field = DuelProofHandshakeClient.class.getDeclaredField("clientSeed");
		field.setAccessible(true);
		return (byte[]) field.get(client);
	}

	private static byte[] pendingContextChunk(DuelProofHandshakeClient client, int chunk)
		throws Exception {
		Field field = DuelProofHandshakeClient.class.getDeclaredField("pendingContextChunks");
		field.setAccessible(true);
		byte[][] chunks = (byte[][]) field.get(client);
		if (chunks == null || chunk < 0 || chunk >= chunks.length || chunks[chunk] == null) {
			throw new AssertionError("pending context chunk was absent");
		}
		return chunks[chunk];
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> retainedAnchors(DuelProofHandshakeClient client)
		throws Exception {
		Field field = DuelProofHandshakeClient.class.getDeclaredField("lockedProofAnchors");
		field.setAccessible(true);
		return (Map<String, Object>) field.get(client);
	}

	private static int retainedAnchorCount(DuelProofHandshakeClient client) throws Exception {
		return retainedAnchors(client).size();
	}

	private static byte[] retainedAnchorBytes(DuelProofHandshakeClient client, byte[] proofId,
			String fieldName) throws Exception {
		Object anchor = retainedAnchors(client).get(DuelProofCodec.hexLower(proofId));
		if (anchor == null) throw new AssertionError("retained anchor was absent");
		Field field = anchor.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return (byte[]) field.get(anchor);
	}

	private static void assertPendingContextCleared(DuelProofHandshakeClient client)
		throws Exception {
		Field chunks = DuelProofHandshakeClient.class.getDeclaredField("pendingContextChunks");
		chunks.setAccessible(true);
		assertNull("pending context chunks cleared", chunks.get(client));
		Field byteCount = DuelProofHandshakeClient.class.getDeclaredField(
			"pendingContextBytesReceived");
		byteCount.setAccessible(true);
		assertInt("pending context byte count cleared", 0, byteCount.getInt(client));
	}

	private static byte[] sequence(int start) {
		byte[] value = new byte[32];
		for (int i = 0; i < value.length; i++) value[i] = (byte) (start + i);
		return value;
	}

	private static byte[] filled(int length, int value) {
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, (byte) value);
		return bytes;
	}

	private static void assertString(String label, String expected, String actual) {
		if (!expected.equals(actual)) {
			throw new AssertionError(label + ": expected " + expected + " but got " + actual);
		}
	}

	private static void assertInt(String label, int expected, int actual) {
		if (expected != actual) {
			throw new AssertionError(label + ": expected " + expected + " but got " + actual);
		}
	}

	private static void assertArray(String label, byte[] expected, byte[] actual) {
		if (!Arrays.equals(expected, actual)) {
			throw new AssertionError(label + ": byte arrays differ");
		}
	}

	private static void assertAllZero(String label, byte[] actual) {
		if (actual == null) throw new AssertionError(label + ": value was null");
		assertArray(label, new byte[actual.length], actual);
	}

	private static void assertNull(String label, Object actual) {
		if (actual != null) throw new AssertionError(label + ": expected null");
	}

	private static void assertTrue(String label, boolean condition) {
		if (!condition) throw new AssertionError(label);
	}

	private static final class RecordingValidator
		implements DuelProofHandshakeClient.ContextValidator {
		private final byte[] expectedProofId;
		private final int expectedOrdinal;
		private final boolean result;
		int calls;

		RecordingValidator(byte[] expectedProofId, int expectedOrdinal, boolean result) {
			this.expectedProofId = expectedProofId;
			this.expectedOrdinal = expectedOrdinal;
			this.result = result;
		}

		@Override
		public boolean validate(DuelProofContext context, int ownOrdinal) {
			calls++;
			assertArray("validator proof id", expectedProofId, context.getProofId());
			assertInt("validator own ordinal", expectedOrdinal, ownOrdinal);
			assertInt("validator participant ordinal", expectedOrdinal,
				context.getParticipant(ownOrdinal).getOrdinal());
			return result;
		}
	}

	private static final class Recorder implements DuelProofHandshakeClient.Outbound {
		private final List<String> payloads = new ArrayList<String>();

		@Override
		public boolean send(String payload) {
			assertTrue("outbound payload present", payload != null && payload.length() > 0);
			assertTrue("outbound payload cap", payload.length()
				<= DuelProofHandshakeClient.MAX_OUTBOUND_CHARS);
			for (int i = 0; i < payload.length(); i++) {
				char character = payload.charAt(i);
				assertTrue("outbound payload ASCII", character >= 0x20 && character <= 0x7e);
			}
			checkedOutboundCount++;
			payloads.add(payload);
			return true;
		}

		String last() {
			if (payloads.isEmpty()) throw new AssertionError("no outbound payload recorded");
			return payloads.get(payloads.size() - 1);
		}

		int size() {
			return payloads.size();
		}

		void assertWithinCap() {
			for (String payload : payloads) {
				assertTrue("recorded outbound payload cap",
					payload.length() <= DuelProofHandshakeClient.MAX_OUTBOUND_CHARS);
			}
		}
	}

	private static final class DeterministicPort implements ClientPort {
		private final byte[][] seeds;
		int calls;

		DeterministicPort(byte[]... seeds) {
			this.seeds = seeds;
		}

		@Override
		public boolean fillSecureRandom(byte[] destination) {
			if (calls >= seeds.length || destination == null
				|| destination.length != seeds[calls].length) return false;
			System.arraycopy(seeds[calls], 0, destination, 0, destination.length);
			calls++;
			return true;
		}
	}

	private static final class FailingEntropyPort implements ClientPort {
		byte[] destination;

		@Override
		public boolean fillSecureRandom(byte[] destination) {
			this.destination = destination;
			Arrays.fill(destination, (byte) 0x7f);
			return false;
		}
	}
}
