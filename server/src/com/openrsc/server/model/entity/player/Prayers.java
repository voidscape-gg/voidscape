package com.openrsc.server.model.entity.player;

import com.openrsc.server.net.rsc.ActionSender;

public class Prayers {
	public static final int THICK_SKIN = 0, BURST_OF_STRENGTH = 1, CLARITY_OF_THOUGHT = 2, ROCK_SKIN = 3,
		SUPERHUMAN_STRENGTH = 4, IMPROVED_REFLEXES = 5, RAPID_RESTORE = 6, RAPID_HEAL = 7, PROTECT_ITEMS = 8,
		STEEL_SKIN = 9, ULTIMATE_STRENGTH = 10, INCREDIBLE_REFLEXES = 11, PARALYZE_MONSTER = 12,
		PROTECT_FROM_MISSILES = 13, PROTECT_FROM_MAGIC = 14;

	private final boolean[] activatedPrayers = new boolean[15];
	private final Player player;

	public Prayers(final Player player) {
		this.player = player;
	}

	public boolean isPrayerActivated(final int prayerID) {
		return activatedPrayers[prayerID];
	}

	public boolean[] getActivePrayers() {
		return activatedPrayers;
	}

	public void setPrayer(final int prayerID, final boolean activated) {
		setPrayer(prayerID, activated, true);
	}

	public void setPrayer(final int prayerID, final boolean activated, final boolean updatePlayer) {
		final boolean changed = activatedPrayers[prayerID] != activated;
		activatedPrayers[prayerID] = activated;
		if (changed && isOverheadProtectionPrayer(prayerID)) {
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		if (updatePlayer) ActionSender.sendPrayers(player, activatedPrayers);
	}

	public void resetPrayers() {
		boolean overheadChanged = false;
		for (int i = 0; i < activatedPrayers.length; i++) {
			if (activatedPrayers[i]) {
				if (isOverheadProtectionPrayer(i)) {
					overheadChanged = true;
				}
				activatedPrayers[i] = false;
			}
		}
		if (overheadChanged) {
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		ActionSender.sendPrayers(player, activatedPrayers);
	}

	private static boolean isOverheadProtectionPrayer(final int prayerID) {
		return prayerID == PROTECT_FROM_MISSILES || prayerID == PROTECT_FROM_MAGIC;
	}
}
