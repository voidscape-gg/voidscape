package com.openrsc.server.plugins.shared;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.ImmediatePlayerLogin;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;

/** Restores interrupted Sir Charles kits before any initial world packets are sent. */
public final class VoidArenaRecoveryLogin implements PlayerLoginTrigger, ImmediatePlayerLogin {
	@Override
	public void onPlayerLogin(Player player) {
		player.getWorld().getVoidArena().recoverDmKingKitBeforeLogin(player);
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return player != null && player.getWorld().getVoidArena().needsImmediateArenaLoginRecovery(player);
	}
}
