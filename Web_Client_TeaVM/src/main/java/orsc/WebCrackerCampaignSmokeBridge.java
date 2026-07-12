package orsc;

/**
 * Diagnostics-only access to the package-private launch cracker envelope parser.
 *
 * <p>The browser smoke uses this only after a real server-delivered snapshot has
 * proven the production packet path. Keeping the bridge in the TeaVM source tree
 * avoids exposing a test entry point from the shared production client.</p>
 */
public final class WebCrackerCampaignSmokeBridge {
	private WebCrackerCampaignSmokeBridge() {
	}

	public static boolean applyEnvelope(mudclient client, String envelope) {
		return client != null && Config.isWeb()
			&& client.handleVoidscapeCrackerCampaignMessage(envelope);
	}

	public static boolean sendCrackerCommand(mudclient client, int remaining) {
		if (client == null || !Config.isWeb() || remaining < 0 || remaining > 1_000_000) {
			return false;
		}
		client.sendCommandString("cracker " + remaining);
		return true;
	}
}
