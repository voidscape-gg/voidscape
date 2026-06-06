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

public final class VoidSubscriptionVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final int NPC_ID = NpcId.VOID_SUBSCRIPTION_VENDOR.id();
	private static final String SUBSCRIBE_COMMAND = "Subscribe";
	private static final Object CLAIM_LOCK = new Object();

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVendor(npc)) return;
		checkReservedCard(player);
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
		checkReservedCard(player);
	}

	private void checkReservedCard(Player player) {
		synchronized (CLAIM_LOCK) {
			if (!claimFounderCard(player)) {
				player.message("@mag@The vendor checks the void ledger.");
				player.message("@whi@No subscription card is ready for this character.");
			}
		}
	}

	private boolean claimFounderCard(Player player) {
		String cacheKey = VoidSubscription.founderCardCacheKey(player.getUsername());
		if (cacheKey.isEmpty()) {
			return false;
		}

		Integer claimState = player.getWorld().getServer().getDatabase()
			.queryLoadGlobalCacheInt(cacheKey);
		if (claimState == null || claimState != VoidSubscription.FOUNDER_CARD_AVAILABLE) {
			return false;
		}

		if (player.getCarriedItems().getInventory().getFreeSlots() <= 0) {
			player.message("@mag@Your subscription card is ready, but you need a free inventory slot.");
			return true;
		}

		player.getCarriedItems().getInventory().add(new Item(VoidSubscription.CARD_ITEM_ID));
		player.getWorld().getServer().getDatabase()
			.querySaveGlobalCacheInt(cacheKey, VoidSubscription.FOUNDER_CARD_CLAIMED);
		ActionSender.sendInventory(player);
		player.message("@mag@The vendor hands you your reserved founder subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername()
				+ " claimed a founder subscription card from the Lumbridge vendor."));
		player.save(false, true);
		return true;
	}

	private boolean isVendor(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}
}
