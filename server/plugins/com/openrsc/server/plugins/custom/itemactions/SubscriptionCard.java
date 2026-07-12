package com.openrsc.server.plugins.custom.itemactions;

import com.openrsc.server.content.SubscriptionCardTransactions;
import com.openrsc.server.content.SubscriptionCardTransactions.RedeemResult;
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

		final RedeemResult result = SubscriptionCardTransactions.redeem(player, item);
		if (!result.isRedeemed()) {
			handleFailure(player, result);
			return;
		}

		final boolean accountWide = result.isAccountWide();
		final long expiresAt = result.getExpiresAt();
		final long remaining = expiresAt - System.currentTimeMillis();
		ActionSender.sendInventory(player);
		ActionSender.sendGameSettings(player);
		ActionSender.sendBox(player, "@mag@Subscription " + (result.wasActive() ? "extended" : "activated") + "% %"
			+ "@whi@This card adds @gre@7 days@whi@ of "
			+ (accountWide ? "account" : "character") + " subscription time.%"
			+ "@whi@Subscribed " + (accountWide ? "accounts" : "characters")
			+ " gain @gre@+1x@whi@ combat and skilling XP.%"
			+ "@whi@At base rates, that is @gre@11x@whi@ combat and @gre@2.5x@whi@ skilling XP.% %"
			+ "@lre@Time remaining: " + VoidSubscription.formatRemaining(remaining) + ".", true);
	}

	private void handleFailure(Player player, RedeemResult result) {
		switch (result.getStatus()) {
			case CARD_NOT_FOUND:
				return;
			case BUSY:
				ActionSender.sendBox(player, "@mag@Subscription not redeemed% %"
					+ "@whi@Your character is still being saved. Try again in a moment.", true);
				return;
			case INTERRUPTED:
				ActionSender.sendBox(player, "@mag@Subscription not redeemed% %"
					+ "@whi@The redemption was interrupted. Your card was not consumed.", true);
				return;
			case UNCERTAIN:
				// The coordinator fenced and closed the session without another save;
				// only the authoritative database state may answer this outcome.
				return;
			case FAILED:
			default:
				ActionSender.sendBox(player, "@mag@Subscription not redeemed% %"
					+ "@whi@The account ledger could not be updated. Your card was not consumed.%"
					+ "@whi@Try again in a moment.", true);
		}
	}
}
