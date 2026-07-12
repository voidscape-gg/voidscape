package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.minigames.undeadsiege.UndeadSiegeMinigame;
import com.openrsc.server.plugins.triggers.CommandTrigger;

public final class UndeadSiegeCommands implements CommandTrigger {
	@Override
	public boolean blockCommand(Player player, String command, String[] args) {
		return !player.isDev()
			&& (command.equalsIgnoreCase("shop")
			|| command.equalsIgnoreCase("siegeshop")
			|| command.equalsIgnoreCase("zombiesshop"));
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		UndeadSiegeMinigame.openSupplyShop(player);
	}
}
