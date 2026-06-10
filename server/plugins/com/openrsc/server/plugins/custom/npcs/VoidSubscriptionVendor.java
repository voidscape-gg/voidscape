package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.inputBox;

public final class VoidSubscriptionVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final int NPC_ID = NpcId.VOID_SUBSCRIPTION_VENDOR.id();
	private static final String SUBSCRIBE_COMMAND = "Subscribe";
	private static final Object CLAIM_LOCK = new Object();
	private static final Object REDEEM_LOCK = new Object();

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVendor(npc)) return;
		checkReservedCard(player, npc);
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
		checkReservedCard(player, npc);
	}

	private void checkReservedCard(Player player, Npc npc) {
		synchronized (CLAIM_LOCK) {
			if (claimStarterCard(player)) {
				return;
			}
		}
		player.message("@mag@The vendor checks the void ledger.");
		player.message("@whi@No subscription card is ready for this character.");
		promptSignupCode(player, npc);
	}

	/**
	 * The code popup can sit open for up to 60 seconds while the plugin blocks in
	 * inputBox. Release the vendor before prompting so other players can talk to
	 * him meanwhile — redemption correctness comes from REDEEM_LOCK plus the
	 * single-use ledger state, not from NPC exclusivity.
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

		Integer claimState = player.getWorld().getServer().getDatabase()
			.queryLoadGlobalCacheInt(cacheKey);
		if (claimState == null || claimState != VoidSubscription.STARTER_CARD_AVAILABLE) {
			return false;
		}

		if (player.getCarriedItems().getInventory().getFreeSlots() <= 0) {
			player.message("@mag@Your subscription card is ready, but you need a free inventory slot.");
			return true;
		}

		player.getCarriedItems().getInventory().add(new Item(VoidSubscription.CARD_ITEM_ID));
		player.getWorld().getServer().getDatabase()
			.querySaveGlobalCacheInt(cacheKey, VoidSubscription.STARTER_CARD_CLAIMED);
		ActionSender.sendInventory(player);
		player.message("@mag@The vendor hands you your starter subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername()
				+ " claimed a starter subscription card from the Lumbridge vendor."));
		player.save(false, true);
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
		synchronized (REDEEM_LOCK) {
			redeemSignupCode(player, normalized, cacheKey);
		}
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
		if (player.getCarriedItems().getInventory().getFreeSlots() <= 0) {
			player.message("@mag@Your subscription card is ready, but you need a free inventory slot.");
			return;
		}
		// Mark redeemed before granting so a failed write can never mint two cards.
		try {
			player.getWorld().getServer().getDatabase()
				.querySaveGlobalCacheInt(cacheKey, VoidSubscription.SIGNUP_CODE_REDEEMED);
		} catch (RuntimeException ex) {
			player.message("@or1@The vendor can't update the void ledger right now. Try again shortly.");
			return;
		}
		player.getCarriedItems().getInventory().add(new Item(VoidSubscription.CARD_ITEM_ID));
		ActionSender.sendInventory(player);
		player.message("@mag@The vendor accepts your signup code and hands you a subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		log(player, " redeemed signup code " + code + " for a subscription card.");
		player.save(false, true);
	}

	private void log(Player player, String message) {
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername() + message));
	}

	private boolean isVendor(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}
}
