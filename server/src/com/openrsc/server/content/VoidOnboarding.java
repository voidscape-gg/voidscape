package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;

/**
 * Track state for the Void Island welcome choice. The menu itself is driven from
 * plugins (VoidWelcome) so it can dispatch into plugin-tree track modules.
 */
public final class VoidOnboarding {
	public static final String TRACK_CACHE_KEY = "void_onboard_track";
	private static final String RETIRED_GUIDED_STAGE_CACHE_KEY = "void_guided_stage";
	private static final String RETIRED_GUIDED_KIT_CACHE_KEY = "void_guided_kit";

	public static final int TRACK_NONE = 0;
	/** Retired. Kept only so old player-cache values can be migrated safely. */
	public static final int TRACK_GUIDED = 1;
	public static final int TRACK_VETERAN = 2;
	public static final int TRACK_SKIP = 3;

	// Client contract: mudclient.isVoidscapeWelcomeMenu matches these option prefixes.
	public static final String OPTION_VETERAN = "I've played Classic - what's new in Voidscape?";
	public static final String OPTION_SKIP = "Skip the intro - pick a path and start in Lumbridge";

	private VoidOnboarding() {
	}

	public static int getTrack(Player player) {
		int track = rawTrack(player);
		return track == TRACK_VETERAN || track == TRACK_SKIP ? track : TRACK_NONE;
	}

	private static int rawTrack(Player player) {
		if (player == null || !player.getCache().hasKey(TRACK_CACHE_KEY)) {
			return TRACK_NONE;
		}
		return player.getCache().getInt(TRACK_CACHE_KEY);
	}

	public static void setTrack(Player player, int track) {
		if (player == null || (track != TRACK_VETERAN && track != TRACK_SKIP)) {
			return;
		}
		player.getCache().set(TRACK_CACHE_KEY, track);
	}

	public static boolean hasRetiredGuidedState(Player player) {
		return player != null
			&& (rawTrack(player) == TRACK_GUIDED
				|| player.getCache().hasKey(RETIRED_GUIDED_STAGE_CACHE_KEY)
				|| player.getCache().hasKey(RETIRED_GUIDED_KIT_CACHE_KEY));
	}

	public static boolean retireGuidedState(Player player) {
		if (!hasRetiredGuidedState(player)) {
			return false;
		}
		player.getCache().set(TRACK_CACHE_KEY, TRACK_SKIP);
		player.getCache().store(VoidStarterIntro.SEEN_CACHE_KEY, true);
		player.getCache().remove(RETIRED_GUIDED_STAGE_CACHE_KEY, RETIRED_GUIDED_KIT_CACHE_KEY);
		return true;
	}

	/**
	 * True only for players who never answered the welcome menu and never saw the
	 * council lore — legacy accounts mid-old-flow are deliberately excluded.
	 */
	public static boolean needsWelcome(Player player) {
		return player != null
			&& getTrack(player) == TRACK_NONE
			&& !hasRetiredGuidedState(player)
			&& !VoidPath.hasChosen(player)
			&& !VoidStarterIntro.hasSeen(player);
	}
}
