package com.openrsc.server.plugins.triggers;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerDeathContext;

public interface PlayerDeathDropTrigger {
	void onPlayerDeathDrop(Player player, PlayerDeathContext context);

	boolean blockPlayerDeathDrop(Player player, PlayerDeathContext context);
}
