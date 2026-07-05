package com.openrsc.server.plugins.custom.onboarding;

import com.openrsc.server.content.VoidOnboarding;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.content.VoidStarterIntro;
import com.openrsc.server.content.VoidVeteranTour;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.VoidWelcomeTrigger;

import static com.openrsc.server.plugins.Functions.multi;

/**
 * The 3-way welcome choice shown when a new character lands on Void Island,
 * plus login resume for every onboarding track.
 */
public final class VoidWelcome implements VoidWelcomeTrigger {
	private static final String RUNNING_ATTRIBUTE = "void_welcome_running";

	@Override
	public boolean blockVoidWelcome(Player player) {
		// Must return true: false would fall through to the Default handler.
		return true;
	}

	@Override
	public void onVoidWelcome(Player player) {
		showWelcomeMenu(player);
	}

	public static void showWelcomeMenu(Player player) {
		if (!VoidOnboarding.needsWelcome(player) || player.getAttribute(RUNNING_ATTRIBUTE, false)) {
			return;
		}

		player.setAttribute(RUNNING_ATTRIBUTE, true);
		player.resetPath();
		try {
			int choice = multi(player, null, false,
				VoidOnboarding.OPTION_NEW,
				VoidOnboarding.OPTION_VETERAN,
				VoidOnboarding.OPTION_SKIP);

			switch (choice) {
				case 0:
					VoidOnboarding.setTrack(player, VoidOnboarding.TRACK_GUIDED);
					player.save();
					VoidStarterIntro.playLore(player);
					continueAfterLore(player);
					break;
				case 1:
					VoidOnboarding.setTrack(player, VoidOnboarding.TRACK_VETERAN);
					player.save();
					VoidStarterIntro.playLore(player);
					continueAfterLore(player);
					break;
				case 2:
					VoidOnboarding.setTrack(player, VoidOnboarding.TRACK_SKIP);
					player.getCache().store(VoidStarterIntro.SEEN_CACHE_KEY, true);
					player.save();
					VoidPath.openPathChoice(player, null);
					break;
				default:
					// Dismissed: track stays unset; council talk or next login re-prompts.
					player.message("@mag@Speak to the Void Council when you are ready.");
					break;
			}
		} finally {
			player.setAttribute(RUNNING_ATTRIBUTE, false);
		}
	}

	/**
	 * Continues the chosen track once the council lore has been seen. Also used
	 * by VoidCouncil for players who logged out mid-lore and finished it there.
	 */
	public static void continueAfterLore(Player player) {
		if (VoidPath.hasChosen(player) || VoidStarterIntro.needsIntro(player)) {
			return;
		}

		switch (VoidOnboarding.getTrack(player)) {
			case VoidOnboarding.TRACK_GUIDED:
				VoidGuidedTour.start(player);
				break;
			case VoidOnboarding.TRACK_VETERAN:
				VoidVeteranTour.start(player);
				break;
			default:
				break;
		}
	}

	public static void handleLogin(Player player) {
		// Brand-new characters answer via PlayerAppearanceUpdater after the appearance screen.
		if (player.isChangingAppearance() || VoidPath.hasChosen(player)) {
			return;
		}
		if (!VoidPath.inStarterIsland(player.getX(), player.getY())) {
			return;
		}

		switch (VoidOnboarding.getTrack(player)) {
			case VoidOnboarding.TRACK_NONE:
				if (VoidOnboarding.needsWelcome(player)) {
					showWelcomeMenu(player);
				}
				// Legacy accounts (lore seen, no track): unchanged behavior — walk to the Herald.
				break;
			case VoidOnboarding.TRACK_SKIP:
				VoidPath.openPathChoice(player, null);
				break;
			case VoidOnboarding.TRACK_GUIDED:
				if (VoidStarterIntro.needsIntro(player)) {
					VoidStarterIntro.prompt(player);
				} else {
					VoidGuidedTour.resume(player);
				}
				break;
			case VoidOnboarding.TRACK_VETERAN:
				if (VoidStarterIntro.needsIntro(player)) {
					VoidStarterIntro.prompt(player);
				} else {
					VoidVeteranTour.start(player);
				}
				break;
			default:
				break;
		}
	}
}
