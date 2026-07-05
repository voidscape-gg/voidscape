package com.openrsc.server.plugins.triggers;

import com.openrsc.server.model.entity.player.Player;

/**
 * Called after the login packet has registered the player with the world.
 */
public interface PostLoginReadyTrigger {
	void onPostLoginReady(Player player);

	boolean blockPostLoginReady(Player player);
}
