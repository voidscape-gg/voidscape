package com.openrsc.server.plugins.triggers;

import com.openrsc.server.model.entity.player.Player;

public interface VoidWelcomeTrigger {

	/**
	 * Called when a new character lands on Void Island and must choose an onboarding track
	 */
	void onVoidWelcome(Player player);

	boolean blockVoidWelcome(Player player);
}
