package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class SubscriptionCard implements OpInvTrigger {
	private static final String REDEEM_COMMAND = "Redeem";

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return item != null
			&& item.getCatalogId() == VoidSubscription.CARD_ITEM_ID
			&& command != null
			&& command.equalsIgnoreCase(REDEEM_COMMAND);
	}

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!blockOpInv(player, invIndex, item, command)) return;

		boolean wasActive = VoidSubscription.isActive(player);

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		long expiresAt = VoidSubscription.activate(player);
		long remaining = expiresAt - System.currentTimeMillis();
		ActionSender.sendInventory(player);
		ActionSender.sendBox(player, "@mag@Subscription " + (wasActive ? "extended" : "activated") + "% %"
			+ "@whi@This card adds @gre@7 days@whi@ of subscription time.%"
			+ "@whi@Combat XP uses @gre@10x@whi@ global rate while active.%"
			+ "@whi@Skill XP uses @gre@6x@whi@ global rate while active.% %"
			+ "@lre@Time remaining: " + VoidSubscription.formatRemaining(remaining) + ".", true);
		player.save(false, true);
	}
}
