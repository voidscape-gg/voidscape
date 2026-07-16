package orsc.multiclient;

/** Minimal test-only port surface for the isolated duel-proof handshake test. */
public interface ClientPort {
	boolean fillSecureRandom(byte[] destination);
}
