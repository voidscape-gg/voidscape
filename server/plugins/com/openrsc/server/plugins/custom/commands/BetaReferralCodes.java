package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.content.BetaReferralReward;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.CommandTrigger;

public final class BetaReferralCodes implements CommandTrigger {
	@Override
	public boolean blockCommand(Player player, String command, String[] args) {
		return player.getConfig().WANT_BETA_ONBOARDING_GUIDE
			&& (command.equalsIgnoreCase("codes") || command.equalsIgnoreCase("refcodes"));
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		BetaReferralReward.showCodes(player);
	}
}
