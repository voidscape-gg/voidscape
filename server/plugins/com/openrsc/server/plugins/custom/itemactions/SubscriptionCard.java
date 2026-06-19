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

		boolean accountWide = VoidSubscription.hasLinkedAccount(player);
		boolean wasActive = VoidSubscription.isActive(player);

		if (player.getCarriedItems().remove(item) == -1) {
			return;
		}

		long expiresAt = VoidSubscription.activate(player);
		if (expiresAt <= 0L) {
			player.getCarriedItems().getInventory().add(item);
			ActionSender.sendInventory(player);
			ActionSender.sendBox(player, "@mag@Subscription not redeemed% %"
				+ "@whi@The account ledger could not be updated. Try again in a moment.", true);
			return;
		}
		long remaining = expiresAt - System.currentTimeMillis();
		ActionSender.sendInventory(player);
		ActionSender.sendBox(player, "@mag@Subscription " + (wasActive ? "extended" : "activated") + "% %"
			+ "@whi@This card adds @gre@7 days@whi@ of "
			+ (accountWide ? "account" : "character") + " subscription time.%"
			+ "@whi@Subscribed " + (accountWide ? "accounts" : "characters")
			+ " gain @gre@+1x@whi@ combat and skilling XP.%"
			+ "@whi@At base rates, that is @gre@11x@whi@ combat and @gre@3x@whi@ skilling XP.% %"
			+ "@lre@Time remaining: " + VoidSubscription.formatRemaining(remaining) + ".", true);
		player.save(false, true);
	}
}
