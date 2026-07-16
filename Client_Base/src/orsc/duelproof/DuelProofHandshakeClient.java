package orsc.duelproof;

import com.voidscape.duelproof.DuelProofCodec;
import com.voidscape.duelproof.DuelProofContext;
import com.voidscape.duelproof.DuelProofCrypto;
import com.voidscape.duelproof.DuelProofSpec;
import com.voidscape.duelproof.DuelProofTerminalVerifier;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import orsc.multiclient.ClientPort;

/**
 * Client witness for the pre-combat duel-proof commit, reveal, and lock handshake.
 *
 * <p>The platform and network adapters are supplied for each control message and are never
 * retained. This keeps the secret seed with the retained client session while allowing Android
 * to replace its activity and rebind the current {@link ClientPort}.</p>
 */
public final class DuelProofHandshakeClient {

	public static final String CONTROL_PREFIX = "@vsduelproof@";
	public static final int MAX_OUTBOUND_CHARS = 192;

	private static final int MAX_CONTROL_CHARS = 512;
	private static final int MAX_CONTEXT_CHUNK_BYTES = 160;
	private static final int MAX_CONTEXT_CHUNKS = 512;
	private static final int MAX_RETAINED_LOCKS = 16;
	private static final String VERSION = "v1";

	/** Seed-free lock values retained independently from mutable handshake traffic. */
	private static final class LockedProofAnchor {
		final byte[] proofId;
		final byte[] contextHash;
		final byte[] serverCommitment;
		final int firstPlayerId;
		final byte[] firstCommitment;
		final int secondPlayerId;
		final byte[] secondCommitment;
		final byte[] lockHash;

		LockedProofAnchor(byte[] proofId, byte[] contextHash, byte[] serverCommitment,
				int firstPlayerId, byte[] firstCommitment, int secondPlayerId,
				byte[] secondCommitment, byte[] lockHash) {
			this.proofId = copy(proofId);
			this.contextHash = copy(contextHash);
			this.serverCommitment = copy(serverCommitment);
			this.firstPlayerId = firstPlayerId;
			this.firstCommitment = copy(firstCommitment);
			this.secondPlayerId = secondPlayerId;
			this.secondCommitment = copy(secondCommitment);
			this.lockHash = copy(lockHash);
		}

		boolean matches(byte[] candidateProofId, byte[] candidateContextHash,
				byte[] candidateServerCommitment, int candidateFirstPlayerId,
				byte[] candidateFirstCommitment, int candidateSecondPlayerId,
				byte[] candidateSecondCommitment, byte[] candidateLockHash) {
			return firstPlayerId == candidateFirstPlayerId
				&& secondPlayerId == candidateSecondPlayerId
				&& DuelProofCrypto.constantTimeEquals(proofId, candidateProofId)
				&& DuelProofCrypto.constantTimeEquals(contextHash, candidateContextHash)
				&& DuelProofCrypto.constantTimeEquals(serverCommitment,
					candidateServerCommitment)
				&& DuelProofCrypto.constantTimeEquals(firstCommitment,
					candidateFirstCommitment)
				&& DuelProofCrypto.constantTimeEquals(secondCommitment,
					candidateSecondCommitment)
				&& DuelProofCrypto.constantTimeEquals(lockHash, candidateLockHash);
		}

		void destroy() {
			wipe(proofId);
			wipe(contextHash);
			wipe(serverCommitment);
			wipe(firstCommitment);
			wipe(secondCommitment);
			wipe(lockHash);
		}
	}

	/** Sends one option-19 string without the terminating line feed. */
	public interface Outbound {
		boolean send(String payload);
	}

	/** Validates the locally visible portions of a canonical pre-combat context. */
	public interface ContextValidator {
		boolean validate(DuelProofContext context, int ownOrdinal);
	}

	private String pendingContextProofId;
	private byte[][] pendingContextChunks;
	private int pendingContextChunkCount = -1;
	private int pendingContextNextChunk;
	private int pendingContextBytesReceived;

	private String proofIdText;
	private byte[] proofId;
	private byte[] contextHash;
	private byte[] serverCommitment;
	private int ordinal = -1;
	private byte[] clientSeed;
	private byte[] clientCommitment;
	private String commitControl;

	private int firstPlayerId = -1;
	private byte[] firstCommitment;
	private int secondPlayerId = -1;
	private byte[] secondCommitment;
	private String revealControl;

	private byte[] lockHash;
	private String lockControl;

	private final LinkedHashMap<String, LockedProofAnchor> lockedProofAnchors =
		new LinkedHashMap<String, LockedProofAnchor>();
	private String latestLockedProofId;

	private String rejectedProofId;
	private String rejectedReason;

	/**
	 * Consumes a proof control envelope. Every message with the private prefix is consumed,
	 * including malformed and out-of-sequence traffic, so it can never leak into chat.
	 */
	public synchronized boolean handleServerMessage(String message, ClientPort clientPort,
											 ContextValidator contextValidator, Outbound outbound) {
		if (message == null || !message.startsWith(CONTROL_PREFIX)) {
			return false;
		}

		String payload = message.substring(CONTROL_PREFIX.length());
		if (payload.length() == 0 || payload.length() > MAX_CONTROL_CHARS
			|| !isPrintableAscii(payload)) {
			rejectMalformed(extractCanonicalProofId(payload), outbound);
			return true;
		}

		String[] parts = payload.split("\\|", -1);
		String candidateProofId = canonicalProofIdAt(parts, 2);
		if (parts.length < 3 || !VERSION.equals(parts[0])) {
			rejectMalformed(candidateProofId, outbound);
			return true;
		}

		try {
			if ("context".equals(parts[1])) {
				handleContext(parts, outbound);
			} else if ("commit".equals(parts[1])) {
				handleCommit(payload, parts, clientPort, contextValidator, outbound);
			} else if ("reveal".equals(parts[1])) {
				handleReveal(payload, parts, outbound);
			} else if ("lock".equals(parts[1])) {
				handleLock(payload, parts, outbound);
			} else if ("abort".equals(parts[1])) {
				handleAbort(parts, outbound);
			} else {
				rejectMalformed(candidateProofId, outbound);
			}
		} catch (IllegalArgumentException ignored) {
			rejectMalformed(candidateProofId, outbound);
		}
		return true;
	}

	private void handleContext(String[] parts, Outbound outbound) {
		if (parts.length != 6) {
			rejectMalformed(canonicalProofIdAt(parts, 2), outbound);
			return;
		}
		String incomingProofId = requireProofId(parts[2]);
		int incomingChunk = parseCanonicalDecimal(parts[3], 0, MAX_CONTEXT_CHUNKS - 1);
		int incomingTotal = parseCanonicalDecimal(parts[4], 1, MAX_CONTEXT_CHUNKS);
		String encoded = parts[5];
		if (incomingChunk < 0 || incomingTotal < 0 || incomingChunk >= incomingTotal
			|| encoded.length() == 0 || (encoded.length() & 1) != 0
			|| encoded.length() > MAX_CONTEXT_CHUNK_BYTES * 2) {
			rejectMalformed(incomingProofId, outbound);
			return;
		}

		byte[] decoded = DuelProofCodec.parseHexLowerExact(encoded, encoded.length() / 2);
		if (rejectedProofId != null && rejectedProofId.equals(incomingProofId)) {
			wipe(decoded);
			sendFailure(incomingProofId, rejectedReason, outbound);
			return;
		}
		if (pendingContextProofId == null || !pendingContextProofId.equals(incomingProofId)) {
			// A new proof id is the only valid replacement for an active handshake.
			clearActive();
			rejectedProofId = null;
			rejectedReason = null;
			pendingContextProofId = incomingProofId;
			pendingContextChunkCount = incomingTotal;
			pendingContextChunks = new byte[incomingTotal][];
		} else if (pendingContextChunkCount != incomingTotal) {
			wipe(decoded);
			rejectState(incomingProofId, outbound);
			return;
		}

		if (incomingChunk < pendingContextNextChunk) {
			boolean exactDuplicate = pendingContextChunks != null
				&& incomingChunk < pendingContextChunks.length
				&& Arrays.equals(pendingContextChunks[incomingChunk], decoded);
			wipe(decoded);
			if (!exactDuplicate) rejectState(incomingProofId, outbound);
			return;
		}
		if (incomingChunk != pendingContextNextChunk
			|| pendingContextBytesReceived > DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES
				- decoded.length) {
			wipe(decoded);
			rejectState(incomingProofId, outbound);
			return;
		}
		pendingContextChunks[incomingChunk] = decoded;
		pendingContextNextChunk++;
		pendingContextBytesReceived += decoded.length;
	}

	/** Wipes all session-owned proof state at login, logout, and reconnect boundaries. */
	public synchronized void reset() {
		clearActive();
		clearLockedProofAnchors();
		rejectedProofId = null;
		rejectedReason = null;
	}

	/**
	 * Returns whether a bare latest-receipt request must present this session's newest lock.
	 * A reconnect/reset deliberately removes this extra local assertion while preserving
	 * self-contained historical receipt verification.
	 */
	public synchronized boolean requiresLatestLockedWitness(boolean latestReceiptRequest) {
		return latestReceiptRequest && latestLockedAnchor() != null;
	}

	/**
	 * Strengthens a terminal replay with a separately retained pre-combat lock.
	 * A bare ::duel request is bound to the newest retained lock regardless of the
	 * proof id supplied by the server. Explicit history requests use their matching
	 * retained lock when available, and otherwise remain self-contained historical
	 * verification. Mutable context/commit traffic cannot erase these anchors.
	 */
	public synchronized boolean matchesLockedWitnessIfApplicable(boolean latestReceiptRequest,
			byte[] candidateProofId,
			byte[] candidateContextHash, byte[] candidateServerCommitment,
			int candidateFirstPlayerId, byte[] candidateFirstCommitment,
			int candidateSecondPlayerId, byte[] candidateSecondCommitment,
			byte[] candidateLockHash) {
		if (candidateProofId == null || candidateProofId.length != DuelProofSpec.PROOF_ID_BYTES) {
			return false;
		}
		LockedProofAnchor applicable = latestReceiptRequest
			? latestLockedAnchor()
			: lockedProofAnchors.get(DuelProofCodec.hexLower(candidateProofId));
		if (applicable == null) {
			return true;
		}
		return applicable.matches(candidateProofId, candidateContextHash,
			candidateServerCommitment, candidateFirstPlayerId, candidateFirstCommitment,
			candidateSecondPlayerId, candidateSecondCommitment, candidateLockHash);
	}

	private void handleCommit(String payload, String[] parts, ClientPort clientPort,
							  ContextValidator contextValidator, Outbound outbound) {
		if (parts.length != 6) {
			rejectMalformed(canonicalProofIdAt(parts, 2), outbound);
			return;
		}
		String incomingProofId = requireProofId(parts[2]);
		byte[] incomingProofIdBytes = DuelProofCodec.parseHexLowerExact(incomingProofId,
			DuelProofSpec.PROOF_ID_BYTES);
		byte[] incomingContextHash = DuelProofCodec.parseHexLowerExact(parts[3],
			DuelProofSpec.HASH_BYTES);
		byte[] incomingServerCommitment = DuelProofCodec.parseHexLowerExact(parts[4],
			DuelProofSpec.HASH_BYTES);
		int incomingOrdinal = parseOrdinal(parts[5]);

		if (proofIdText != null && proofIdText.equals(incomingProofId)) {
			if (payload.equals(commitControl)) {
				sendCommit(outbound);
			} else {
				rejectState(incomingProofId, outbound);
			}
			return;
		}
		if (rejectedProofId != null && rejectedProofId.equals(incomingProofId)) {
			sendFailure(incomingProofId, rejectedReason, outbound);
			return;
		}
		if (!incomingProofId.equals(pendingContextProofId)
			|| pendingContextChunks == null || pendingContextChunkCount <= 0
			|| pendingContextNextChunk != pendingContextChunkCount
			|| pendingContextBytesReceived <= 0) {
			rejectState(incomingProofId, outbound);
			return;
		}

		byte[] contextBytes = null;
		byte[] computedContextHash = null;
		DuelProofContext context;
		try {
			contextBytes = assemblePendingContext();
			computedContextHash = DuelProofCrypto.contextHash(contextBytes);
			if (!DuelProofCrypto.constantTimeEquals(computedContextHash, incomingContextHash)) {
				rejectState(incomingProofId, outbound);
				return;
			}
			try {
				context = DuelProofTerminalVerifier.verifyContext(contextBytes);
			} catch (IllegalArgumentException malformedContext) {
				rejectMalformed(incomingProofId, outbound);
				return;
			}
			if (!DuelProofCrypto.constantTimeEquals(context.getProofId(), incomingProofIdBytes)
				|| context.getParticipant(incomingOrdinal).getOrdinal() != incomingOrdinal
				|| contextValidator == null || !validateContext(contextValidator, context,
					incomingOrdinal)) {
				rejectState(incomingProofId, outbound);
				return;
			}
		} finally {
			wipe(contextBytes);
			wipe(computedContextHash);
		}

		// A new id is the only way to replace a still-live or rejected handshake.
		clearActive();
		rejectedProofId = null;
		rejectedReason = null;

		byte[] incomingClientSeed = new byte[DuelProofSpec.SEED_BYTES];
		boolean entropyAvailable = false;
		try {
			entropyAvailable = clientPort != null
				&& clientPort.fillSecureRandom(incomingClientSeed);
		} catch (RuntimeException ignored) {
			entropyAvailable = false;
		}
		if (!entropyAvailable) {
			wipe(incomingClientSeed);
			reject(incomingProofId, "entropy", outbound);
			return;
		}

		byte[] incomingClientCommitment = DuelProofCrypto.clientCommitment(
			incomingContextHash, incomingServerCommitment, incomingOrdinal, incomingClientSeed);
		proofIdText = incomingProofId;
		proofId = incomingProofIdBytes;
		contextHash = incomingContextHash;
		serverCommitment = incomingServerCommitment;
		ordinal = incomingOrdinal;
		clientSeed = incomingClientSeed;
		clientCommitment = incomingClientCommitment;
		commitControl = payload;
		sendCommit(outbound);
	}

	private byte[] assemblePendingContext() {
		if (pendingContextChunks == null || pendingContextBytesReceived <= 0
			|| pendingContextBytesReceived > DuelProofSpec.MAX_WITNESS_CONTEXT_BYTES) {
			throw new IllegalArgumentException("context chunks are incomplete");
		}
		byte[] assembled = new byte[pendingContextBytesReceived];
		int offset = 0;
		for (int chunk = 0; chunk < pendingContextChunkCount; chunk++) {
			byte[] bytes = pendingContextChunks[chunk];
			if (bytes == null || bytes.length == 0 || offset > assembled.length - bytes.length) {
				wipe(assembled);
				throw new IllegalArgumentException("context chunks are incomplete");
			}
			System.arraycopy(bytes, 0, assembled, offset, bytes.length);
			offset += bytes.length;
		}
		if (offset != assembled.length) {
			wipe(assembled);
			throw new IllegalArgumentException("context byte count does not match chunks");
		}
		return assembled;
	}

	private static boolean validateContext(ContextValidator validator, DuelProofContext context,
										 int ordinal) {
		try {
			return validator.validate(context, ordinal);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private void handleReveal(String payload, String[] parts, Outbound outbound) {
		if (parts.length != 7) {
			rejectMalformed(canonicalProofIdAt(parts, 2), outbound);
			return;
		}
		String incomingProofId = requireProofId(parts[2]);
		int incomingFirstPlayerId = parseCanonicalPlayerId(parts[3]);
		byte[] incomingFirstCommitment = DuelProofCodec.parseHexLowerExact(parts[4],
			DuelProofSpec.HASH_BYTES);
		int incomingSecondPlayerId = parseCanonicalPlayerId(parts[5]);
		byte[] incomingSecondCommitment = DuelProofCodec.parseHexLowerExact(parts[6],
			DuelProofSpec.HASH_BYTES);
		if (incomingFirstPlayerId >= incomingSecondPlayerId) {
			throw new IllegalArgumentException("player ids are not canonically ordered");
		}

		if (!requireActiveProof(incomingProofId, outbound)) {
			return;
		}
		if (revealControl != null) {
			if (payload.equals(revealControl)) {
				sendReveal(outbound);
			} else {
				rejectState(incomingProofId, outbound);
			}
			return;
		}

		byte[] witnessedOwnCommitment = ordinal == 0
			? incomingFirstCommitment : incomingSecondCommitment;
		if (!DuelProofCrypto.constantTimeEquals(clientCommitment, witnessedOwnCommitment)) {
			rejectState(incomingProofId, outbound);
			return;
		}

		firstPlayerId = incomingFirstPlayerId;
		firstCommitment = incomingFirstCommitment;
		secondPlayerId = incomingSecondPlayerId;
		secondCommitment = incomingSecondCommitment;
		revealControl = payload;
		sendReveal(outbound);
	}

	private void handleLock(String payload, String[] parts, Outbound outbound) {
		if (parts.length != 10) {
			rejectMalformed(canonicalProofIdAt(parts, 2), outbound);
			return;
		}
		String incomingProofId = requireProofId(parts[2]);
		byte[] incomingContextHash = DuelProofCodec.parseHexLowerExact(parts[3],
			DuelProofSpec.HASH_BYTES);
		byte[] incomingServerCommitment = DuelProofCodec.parseHexLowerExact(parts[4],
			DuelProofSpec.HASH_BYTES);
		int incomingFirstPlayerId = parseCanonicalPlayerId(parts[5]);
		byte[] incomingFirstCommitment = DuelProofCodec.parseHexLowerExact(parts[6],
			DuelProofSpec.HASH_BYTES);
		int incomingSecondPlayerId = parseCanonicalPlayerId(parts[7]);
		byte[] incomingSecondCommitment = DuelProofCodec.parseHexLowerExact(parts[8],
			DuelProofSpec.HASH_BYTES);
		byte[] incomingLockHash = DuelProofCodec.parseHexLowerExact(parts[9],
			DuelProofSpec.HASH_BYTES);
		if (incomingFirstPlayerId >= incomingSecondPlayerId) {
			throw new IllegalArgumentException("player ids are not canonically ordered");
		}

		if (!requireActiveProof(incomingProofId, outbound)) {
			return;
		}
		if (lockControl != null) {
			if (payload.equals(lockControl)) {
				sendAck(outbound);
			} else {
				rejectState(incomingProofId, outbound);
			}
			return;
		}
		if (revealControl == null
			|| incomingFirstPlayerId != firstPlayerId
			|| incomingSecondPlayerId != secondPlayerId
			|| !DuelProofCrypto.constantTimeEquals(contextHash, incomingContextHash)
			|| !DuelProofCrypto.constantTimeEquals(serverCommitment, incomingServerCommitment)
			|| !DuelProofCrypto.constantTimeEquals(firstCommitment, incomingFirstCommitment)
			|| !DuelProofCrypto.constantTimeEquals(secondCommitment, incomingSecondCommitment)) {
			rejectState(incomingProofId, outbound);
			return;
		}

		byte[] expectedLockHash = DuelProofCrypto.finalLockHash(proofId, contextHash,
			serverCommitment, firstPlayerId, firstCommitment, secondPlayerId, secondCommitment);
		boolean validLock = DuelProofCrypto.constantTimeEquals(expectedLockHash, incomingLockHash);
		wipe(expectedLockHash);
		if (!validLock) {
			rejectState(incomingProofId, outbound);
			return;
		}

		lockHash = incomingLockHash;
		lockControl = payload;
		retainLockedProofAnchor();
		sendAck(outbound);
	}

	private void handleAbort(String[] parts, Outbound outbound) {
		if (parts.length != 4) {
			rejectMalformed(canonicalProofIdAt(parts, 2), outbound);
			return;
		}
		String incomingProofId = requireProofId(parts[2]);
		if (!isAbortReason(parts[3])) {
			rejectMalformed(incomingProofId, outbound);
			return;
		}
		if ("retreat".equals(parts[3])) {
			retireLockedProofAnchor(incomingProofId);
		}
		if ((proofIdText != null && proofIdText.equals(incomingProofId))
			|| (pendingContextProofId != null && pendingContextProofId.equals(incomingProofId))
			|| (proofIdText == null && rejectedProofId != null
				&& rejectedProofId.equals(incomingProofId))) {
			clearActive();
			rejectedProofId = null;
			rejectedReason = null;
		}
	}

	private boolean requireActiveProof(String incomingProofId, Outbound outbound) {
		if (proofIdText == null || !proofIdText.equals(incomingProofId)) {
			rejectState(incomingProofId, outbound);
			return false;
		}
		return true;
	}

	private void sendCommit(Outbound outbound) {
		send(VERSION + "|commit|" + proofIdText + "|"
			+ DuelProofCodec.hexLower(clientCommitment), outbound);
	}

	private void sendReveal(Outbound outbound) {
		send(VERSION + "|reveal|" + proofIdText + "|"
			+ DuelProofCodec.hexLower(clientSeed), outbound);
	}

	private void sendAck(Outbound outbound) {
		send(VERSION + "|ack|" + proofIdText + "|" + DuelProofCodec.hexLower(lockHash), outbound);
	}

	private void rejectMalformed(String candidateProofId, Outbound outbound) {
		if (candidateProofId == null) {
			if (proofIdText != null) {
				candidateProofId = proofIdText;
			} else if (pendingContextProofId != null) {
				candidateProofId = pendingContextProofId;
			} else if (rejectedProofId != null) {
				return;
			} else {
				return;
			}
		}
		reject(candidateProofId, "malformed", outbound);
	}

	private void rejectState(String candidateProofId, Outbound outbound) {
		reject(candidateProofId, "state", outbound);
	}

	private void reject(String candidateProofId, String reason, Outbound outbound) {
		clearActive();
		rejectedProofId = candidateProofId;
		rejectedReason = reason;
		sendFailure(candidateProofId, reason, outbound);
	}

	private void sendFailure(String candidateProofId, String reason, Outbound outbound) {
		if (candidateProofId == null || !isClientFailureReason(reason)) {
			return;
		}
		send(VERSION + "|fail|" + candidateProofId + "|" + reason, outbound);
	}

	private static boolean send(String payload, Outbound outbound) {
		if (outbound == null || payload == null || payload.length() == 0
			|| payload.length() > MAX_OUTBOUND_CHARS || !isPrintableAscii(payload)) {
			return false;
		}
		try {
			return outbound.send(payload);
		} catch (RuntimeException ignored) {
			return false;
		}
	}

	private void clearActive() {
		clearPendingContext();
		wipe(proofId);
		wipe(contextHash);
		wipe(serverCommitment);
		wipe(clientSeed);
		wipe(clientCommitment);
		wipe(firstCommitment);
		wipe(secondCommitment);
		wipe(lockHash);
		proofIdText = null;
		proofId = null;
		contextHash = null;
		serverCommitment = null;
		ordinal = -1;
		clientSeed = null;
		clientCommitment = null;
		commitControl = null;
		firstPlayerId = -1;
		firstCommitment = null;
		secondPlayerId = -1;
		secondCommitment = null;
		revealControl = null;
		lockHash = null;
		lockControl = null;
	}

	private void retainLockedProofAnchor() {
		LockedProofAnchor replacement = new LockedProofAnchor(proofId, contextHash,
			serverCommitment, firstPlayerId, firstCommitment, secondPlayerId,
			secondCommitment, lockHash);
		LockedProofAnchor previous = lockedProofAnchors.remove(proofIdText);
		if (previous != null) previous.destroy();
		lockedProofAnchors.put(proofIdText, replacement);
		latestLockedProofId = proofIdText;

		while (lockedProofAnchors.size() > MAX_RETAINED_LOCKS) {
			Iterator<Map.Entry<String, LockedProofAnchor>> iterator =
				lockedProofAnchors.entrySet().iterator();
			if (!iterator.hasNext()) break;
			Map.Entry<String, LockedProofAnchor> eldest = iterator.next();
			iterator.remove();
			eldest.getValue().destroy();
		}
	}

	private LockedProofAnchor latestLockedAnchor() {
		return latestLockedProofId == null
			? null : lockedProofAnchors.get(latestLockedProofId);
	}

	/** A legal retreat has no terminal receipt, so its lock must not shadow older history. */
	private void retireLockedProofAnchor(String proofIdText) {
		LockedProofAnchor retired = lockedProofAnchors.remove(proofIdText);
		if (retired != null) retired.destroy();
		if (!proofIdText.equals(latestLockedProofId)) return;

		latestLockedProofId = null;
		for (String retainedProofId : lockedProofAnchors.keySet()) {
			latestLockedProofId = retainedProofId;
		}
	}

	private void clearLockedProofAnchors() {
		for (LockedProofAnchor anchor : lockedProofAnchors.values()) {
			if (anchor != null) anchor.destroy();
		}
		lockedProofAnchors.clear();
		latestLockedProofId = null;
	}

	private void clearPendingContext() {
		if (pendingContextChunks != null) {
			for (byte[] chunk : pendingContextChunks) wipe(chunk);
			Arrays.fill(pendingContextChunks, null);
		}
		pendingContextProofId = null;
		pendingContextChunks = null;
		pendingContextChunkCount = -1;
		pendingContextNextChunk = 0;
		pendingContextBytesReceived = 0;
	}

	private static String requireProofId(String value) {
		DuelProofCodec.parseHexLowerExact(value, DuelProofSpec.PROOF_ID_BYTES);
		return value;
	}

	private static int parseOrdinal(String value) {
		if ("0".equals(value)) return 0;
		if ("1".equals(value)) return 1;
		throw new IllegalArgumentException("ordinal must be 0 or 1");
	}

	private static int parseCanonicalPlayerId(String value) {
		if (value == null || value.length() == 0
			|| (value.length() > 1 && value.charAt(0) == '0')) {
			throw new IllegalArgumentException("player id is not canonical");
		}
		long parsed = 0L;
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character < '0' || character > '9') {
				throw new IllegalArgumentException("player id is not decimal");
			}
			parsed = parsed * 10L + character - '0';
			if (parsed > Integer.MAX_VALUE) {
				throw new IllegalArgumentException("player id is too large");
			}
		}
		if (parsed <= 0L) {
			throw new IllegalArgumentException("player id must be positive");
		}
		return (int) parsed;
	}

	private static int parseCanonicalDecimal(String value, int minimum, int maximum) {
		if (value == null || value.length() == 0 || minimum > maximum
			|| (value.length() > 1 && value.charAt(0) == '0')) {
			return -1;
		}
		long parsed = 0L;
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character < '0' || character > '9') return -1;
			parsed = parsed * 10L + character - '0';
			if (parsed > maximum) return -1;
		}
		return parsed < minimum ? -1 : (int) parsed;
	}

	private static String extractCanonicalProofId(String payload) {
		if (payload == null) return null;
		String[] parts = payload.split("\\|", -1);
		return canonicalProofIdAt(parts, 2);
	}

	private static String canonicalProofIdAt(String[] parts, int index) {
		if (parts == null || index < 0 || index >= parts.length) return null;
		String candidate = parts[index];
		if (candidate == null || candidate.length() != DuelProofSpec.PROOF_ID_BYTES * 2) return null;
		for (int i = 0; i < candidate.length(); i++) {
			char character = candidate.charAt(i);
			if (!((character >= '0' && character <= '9')
				|| (character >= 'a' && character <= 'f'))) return null;
		}
		return candidate;
	}

	private static boolean isAbortReason(String reason) {
		return "timeout".equals(reason)
			|| "database".equals(reason)
			|| "entropy".equals(reason)
			|| "malformed".equals(reason)
			|| "state".equals(reason)
			|| "disconnected".equals(reason)
			|| "cancelled".equals(reason)
			|| "unsupported".equals(reason)
			|| "items".equals(reason)
			|| "unreachable".equals(reason)
			|| "server-restart".equals(reason)
			|| "retreat".equals(reason);
	}

	private static boolean isClientFailureReason(String reason) {
		return "entropy".equals(reason) || "malformed".equals(reason) || "state".equals(reason);
	}

	private static boolean isPrintableAscii(String value) {
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character < 0x20 || character > 0x7e) return false;
		}
		return true;
	}

	private static void wipe(byte[] value) {
		if (value != null) Arrays.fill(value, (byte) 0);
	}

	private static byte[] copy(byte[] value) {
		return value == null ? null : Arrays.copyOf(value, value.length);
	}
}
