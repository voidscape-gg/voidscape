package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.content.VoidScout;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class VoidSparrow implements OpInvTrigger {
	private static final String RELEASE_COMMAND = "Release";

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return item != null
			&& item.getCatalogId() == VoidScout.ITEM_ID
			&& command != null
			&& command.equalsIgnoreCase(RELEASE_COMMAND);
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!blockOpInv(player, invIndex, item, command)) return;
		VoidScout.release(player);
	}
}
