package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.PlayerKilledPlayerTrigger;

public final class WorldAnnouncements implements PlayerKilledPlayerTrigger {
	@Override
	public void onPlayerKilledPlayer(Player killer, Player killed) {
		// Passive trigger: blockPlayerKilledPlayer records the event without owning death handling.
	}

	@Override
	public boolean blockPlayerKilledPlayer(Player killer, Player killed) {
		killer.getWorld().getWorldAnnouncementService().announceSkulledWildernessKill(killer, killed);
		return false;
	}
}
