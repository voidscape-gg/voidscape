package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

/**
 * Track state for the Void Island welcome choice. The menu itself is driven from
 * plugins (VoidWelcome) so it can dispatch into plugin-tree track modules.
 */
public final class VoidOnboarding {
	public static final String TRACK_CACHE_KEY = "void_onboard_track";

	public static final int TRACK_NONE = 0;
	public static final int TRACK_GUIDED = 1;
	public static final int TRACK_VETERAN = 2;
	public static final int TRACK_SKIP = 3;

	// Client contract: mudclient.isVoidscapeWelcomeMenu matches these option prefixes.
	public static final String OPTION_NEW = "I'm new to Classic - teach me the basics";
	public static final String OPTION_VETERAN = "I've played Classic - what's new in Voidscape?";
	public static final String OPTION_SKIP = "Skip the intro - pick a path and start in Lumbridge";

	private VoidOnboarding() {
	}

	public static int getTrack(Player player) {
		if (player == null || !player.getCache().hasKey(TRACK_CACHE_KEY)) {
			return TRACK_NONE;
		}
		int track = player.getCache().getInt(TRACK_CACHE_KEY);
		return track >= TRACK_GUIDED && track <= TRACK_SKIP ? track : TRACK_NONE;
	}

	public static void setTrack(Player player, int track) {
		if (player == null || track < TRACK_GUIDED || track > TRACK_SKIP) {
			return;
		}
		player.getCache().set(TRACK_CACHE_KEY, track);
	}

	/**
	 * True only for players who never answered the welcome menu and never saw the
	 * council lore — legacy accounts mid-old-flow are deliberately excluded.
	 */
	public static boolean needsWelcome(Player player) {
		return player != null
			&& getTrack(player) == TRACK_NONE
			&& !VoidPath.hasChosen(player)
			&& !VoidStarterIntro.hasSeen(player);
	}
}
