package com.openrsc.server.plugins.shared;

import com.openrsc.server.content.BetaReferralReward;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.onboarding.VoidWelcome;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;

public class PlayerLogin implements PlayerLoginTrigger {
	@Override
	public void onPlayerLogin(Player player) {
		VoidSubscription.refreshAccountSubscription(player);
		BetaReferralReward.showPendingReferralNotices(player);
		player.getWorld().getVoidArena().handleLogin(player);
		// Last: can block on the welcome menu for minutes.
		VoidWelcome.handleLogin(player);
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return true;
	}
}
