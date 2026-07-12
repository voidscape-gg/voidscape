package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.SubscriptionCardTransactions;
import com.openrsc.server.content.SubscriptionCardTransactions.EntitlementMarker;
import com.openrsc.server.content.SubscriptionCardTransactions.GrantResult;
import com.openrsc.server.content.SubscriptionCardTransactions.GrantStatus;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.inputBox;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidSubscriptionVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final int NPC_ID = NpcId.VOID_SUBSCRIPTION_VENDOR.id();
	private static final String SUBSCRIBE_COMMAND = "Subscribe";

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVendor(npc)) return;
		openVendorMenu(player, npc);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isVendor(npc)
			&& command != null
			&& command.equalsIgnoreCase(SUBSCRIBE_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) return;
		openVendorMenu(player, npc);
	}

	private void openVendorMenu(Player player, Npc npc) {
		npcsay(player, npc,
			"Subscription cards bought through SHOP wait in my ledger.",
			"I also hold launch, starter, and website signup cards.");
		final int option = multi(player, npc,
			"Collect one SHOP purchase.",
			"Claim my reserved free card.",
			"Enter a website signup code.",
			"How do subscription cards work?",
			"Never mind.");
		switch (option) {
			case 0:
				claimPurchasedCard(player);
				return;
			case 1:
				if (!claimReservedFreeCard(player)) {
					player.message("@mag@The vendor checks the void ledger.");
					player.message("@whi@No reserved free card is waiting for this character.");
				}
				return;
			case 2:
				promptSignupCode(player, npc);
				return;
			case 3:
				npcsay(player, npc,
					"Buy cards through the in-game SHOP, then collect them from me.",
					"Each collection gives one physical, tradeable card.",
					"Redeeming adds seven days. Linked characters share the time.",
					"Otherwise, the time applies only to this character.",
					"If you bought several, collect them one at a time.");
				return;
			default:
				npcsay(player, npc, "Your purchases will remain safely in the ledger.");
		}
	}

	private void claimPurchasedCard(Player player) {
		if (!VoidSubscription.hasLinkedAccount(player)) {
			player.message("@whi@Link this character to the same website account used in SHOP first.");
			return;
		}
		final GrantResult result = SubscriptionCardTransactions.claimPurchasedCard(player);
		if (!result.isGranted()) {
			if (result.getStatus() == GrantStatus.NOT_AVAILABLE) {
				player.message("@mag@The vendor checks the SHOP ledger.");
				player.message("@whi@No purchased subscription card is waiting for this account.");
				return;
			}
			handleGrantFailure(player, result, false, true);
			return;
		}

		ActionSender.sendInventory(player);
		player.message("@mag@The vendor hands you one purchased subscription card.");
		player.message("@whi@If you bought more, choose SHOP collection again for the next card.");
		log(player, " collected purchased subscription card item " + result.getItemId() + ".");
	}

	/**
	 * The code popup can sit open for up to 60 seconds while the plugin blocks in
	 * inputBox. Release the vendor before prompting so other players can talk to
	 * him meanwhile — redemption correctness comes from the database transaction
	 * and single-use ledger state, not from NPC exclusivity.
	 */
	private void releaseVendor(Player player, Npc npc) {
		if (npc == null) return;
		npc.setBusy(false);
		if (npc.getInteractingPlayer() == player) {
			npc.setNpcInteraction(null);
			npc.setInteractingPlayer(null);
		}
	}

	private boolean claimStarterCard(Player player) {
		if (!VoidSubscription.hasLinkedAccount(player)) {
			return false;
		}

		String cacheKey = VoidSubscription.starterCardCacheKey(player);
		if (cacheKey.isEmpty()) {
			return false;
		}

		final GrantResult result = SubscriptionCardTransactions.grantReservedCard(player,
			EntitlementMarker.global(cacheKey, VoidSubscription.STARTER_CARD_AVAILABLE,
				VoidSubscription.STARTER_CARD_CLAIMED),
			"subscription_vendor", "grant=starter_card");
		if (!result.isGranted()) {
			return handleGrantFailure(player, result, false, false);
		}

		ActionSender.sendInventory(player);
		player.message("@mag@The vendor hands you your starter subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername()
				+ " claimed a starter subscription card from the Lumbridge vendor."));
		return true;
	}

	private boolean claimReservedFreeCard(Player player) {
		if (claimLaunchCard(player)) {
			return true;
		}
		return claimStarterCard(player);
	}

	private boolean claimLaunchCard(Player player) {
		String cacheKey = VoidSubscription.launchCardCacheKey(player);
		if (cacheKey.isEmpty()) {
			return false;
		}

		final GrantResult result = SubscriptionCardTransactions.grantReservedCard(player,
			EntitlementMarker.global(cacheKey, VoidSubscription.LAUNCH_CARD_AVAILABLE,
				VoidSubscription.LAUNCH_CARD_CLAIMED),
			"subscription_launch_2026", "campaign=launch_subcard_2026");
		if (!result.isGranted()) {
			return handleGrantFailure(player, result, false, false);
		}

		ActionSender.sendInventory(player);
		player.message("@mag@The vendor hands you this character's launch subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername()
				+ " claimed launch subscription card item " + result.getItemId() + "."));
		return true;
	}

	private void promptSignupCode(Player player, Npc npc) {
		if (!ActionSender.supportsInputBox(player)) {
			player.message("@whi@Signed up on the website? Update your client to enter your code here.");
			return;
		}
		releaseVendor(player, npc);
		String entered = inputBox(player, "Got a signup code from the website?%Enter it and press enter");
		if (entered == null || entered.trim().isEmpty()) {
			return;
		}
		String normalized = VoidSubscription.normalizeSignupCode(entered);
		String cacheKey = VoidSubscription.signupCodeCacheKey(normalized);
		if (cacheKey.isEmpty()) {
			player.message("@or1@That doesn't look like a signup code. Check it and try again.");
			return;
		}
		redeemSignupCode(player, normalized, cacheKey);
	}

	private void redeemSignupCode(Player player, String code, String cacheKey) {
		Integer state;
		try {
			state = player.getWorld().getServer().getDatabase().queryLoadGlobalCacheInt(cacheKey);
		} catch (RuntimeException ex) {
			player.message("@or1@The vendor can't check the void ledger right now. Try again shortly.");
			return;
		}
		if (state == null) {
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That code isn't valid. Check it matches your signup code exactly.");
			log(player, " tried to redeem unknown signup code " + code);
			return;
		}
		if (state == VoidSubscription.SIGNUP_CODE_REDEEMED) {
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That signup code has already been redeemed.");
			log(player, " tried to re-redeem signup code " + code);
			return;
		}
		if (state != VoidSubscription.SIGNUP_CODE_AVAILABLE) {
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That code isn't valid. Check it matches your signup code exactly.");
			log(player, " tried to redeem signup code " + code + " in unexpected state " + state);
			return;
		}
		final GrantResult result = SubscriptionCardTransactions.grantReservedCard(player,
			EntitlementMarker.global(cacheKey, VoidSubscription.SIGNUP_CODE_AVAILABLE,
				VoidSubscription.SIGNUP_CODE_REDEEMED),
			"subscription_signup_code", "grant=signup_code code_suffix=" + codeSuffix(code));
		if (!result.isGranted()) {
			if (result.getStatus() == GrantStatus.NOT_AVAILABLE) {
				player.message("@mag@The vendor checks your code against the void ledger.");
				player.message("@whi@That signup code has already been redeemed.");
				log(player, " lost a signup-code claim race for " + code);
				return;
			}
			handleGrantFailure(player, result, true, false);
			return;
		}
		ActionSender.sendInventory(player);
		player.message("@mag@The vendor accepts your signup code and hands you a subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		log(player, " redeemed signup code " + code + " for a subscription card.");
	}

	private boolean handleGrantFailure(Player player, GrantResult result,
		boolean codeWasKnownAvailable, boolean paidPurchase) {
		switch (result.getStatus()) {
			case NOT_AVAILABLE:
				return false;
			case INVENTORY_FULL:
				player.message("@mag@Your subscription card is ready, but you need a free inventory slot.");
				return true;
			case CLIENT_UNSUPPORTED:
				player.message("@or1@Your client cannot safely hold the subscription card.");
				player.message(paidPurchase
					? "@whi@Update your client, then return. Your SHOP purchase is still waiting."
					: "@whi@Update your client, then return to the vendor. Your card is still reserved.");
				return true;
			case BUSY:
				player.message("@or1@Your character is still being saved. Try the vendor again in a moment.");
				return true;
			case INTERRUPTED:
				player.message(paidPurchase
					? "@or1@The claim was interrupted. Your SHOP purchase is still waiting; try again."
					: "@or1@The claim was interrupted. Your card remains reserved; try again.");
				return true;
			case UNCERTAIN:
				// The transaction coordinator has already fenced and closed this session
				// without another save. Do not guess whether the entitlement was consumed.
				return true;
			case FAILED:
			default:
				player.message(paidPurchase
					? "@or1@The vendor could not collect the purchase. It remains waiting; try again shortly."
					: codeWasKnownAvailable
					? "@or1@The vendor could not redeem the code. It remains available; try again shortly."
					: "@or1@The vendor could not hand over the card. It remains reserved; try again shortly.");
				return true;
		}
	}

	private void log(Player player, String message) {
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername() + message));
	}

	private String codeSuffix(final String code) {
		if (code == null || code.length() <= 4) {
			return "";
		}
		return code.substring(code.length() - 4);
	}

	private boolean isVendor(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}
}
