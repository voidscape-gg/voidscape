package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class VoidSubscriptionVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final int NPC_ID = NpcId.VOID_SUBSCRIPTION_VENDOR.id();
	private static final String SUBSCRIBE_COMMAND = "Subscribe";
	private static final Object STOCK_LOCK = new Object();

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVendor(npc)) return;
		openShop(player);
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
		openShop(player);
	}

	private void openShop(Player player) {
		synchronized (STOCK_LOCK) {
			VendorState state = loadState(player);
			showShop(player, state);
			player.message("Subscription cards: " + state.stock + " left at " + state.price + " coins each.");
		}
	}

	private void buyCards(Player player, int catalogID, int requestedAmount) {
		if (catalogID != VoidSubscription.CARD_ITEM_ID) {
			player.message("This object can't be bought in shops");
			return;
		}

		synchronized (STOCK_LOCK) {
			VendorState state = loadState(player);
			int amount = Math.min(requestedAmount, state.stock);
			amount = Math.min(amount, player.getCarriedItems().getInventory().getFreeSlots());

			int coins = player.getCarriedItems().getInventory().countId(ItemId.COINS.id());
			int affordable = state.price <= 0 ? 0 : coins / state.price;
			amount = Math.min(amount, affordable);
			while (amount > 0 && (long) amount * state.price > Integer.MAX_VALUE) {
				amount--;
			}

			if (amount <= 0) {
				if (state.stock <= 0) {
					player.message("The vendor is refreshing his stock.");
				} else if (player.getCarriedItems().getInventory().getFreeSlots() <= 0) {
					player.message("You can't hold the objects you are trying to buy!");
				} else {
					player.message("You don't have enough coins");
				}
				showShop(player, state);
				return;
			}

			int totalMoney = state.price * amount;
			if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), totalMoney)) == -1) {
				showShop(player, state);
				return;
			}

			for (int i = 0; i < amount; i++) {
				player.getCarriedItems().getInventory().add(new Item(VoidSubscription.CARD_ITEM_ID));
			}

			VendorState nextState = state.afterSales(amount);
			saveState(player, nextState);
			showShop(player, nextState);

			player.playSound("coins");
			player.message("You buy " + amount + " subscription card" + (amount == 1 ? "" : "s")
				+ " for " + totalMoney + " coins.");
			if (nextState.tier > state.tier) {
				player.message("@mag@The vendor restocks. Subscription cards are now "
					+ nextState.price + " coins each.");
			}
			player.getWorld().getServer().getGameLogger().addQuery(
				new GenericLog(player.getWorld(), player.getUsername() + " bought subscription card x"
					+ amount + " for " + totalMoney + "gp at " + player.getLocation().toString()));
		}
	}

	private void showShop(Player player, VendorState state) {
		Shop currentShop = player.getShop();
		if (currentShop != null) {
			currentShop.removePlayer(player);
		}

		Shop shop = new Shop(false, 60000, 100, 0, 0,
			new Item(VoidSubscription.CARD_ITEM_ID, state.stock))
			.withDisplayBuyPrice(state.price)
			.withoutPlayerSales()
			.withBuyHandler((shopPlayer, current, catalogID, amount) -> buyCards(shopPlayer, catalogID, amount));
		shop.area = "Void Subscriptions";

		player.setAccessingShop(shop);
		ActionSender.showShop(player, shop);
	}

	private VendorState loadState(Player player) {
		Integer stock = player.getWorld().getServer().getDatabase()
			.queryLoadGlobalCacheInt(VoidSubscription.VENDOR_STOCK_CACHE_KEY);
		Integer tier = player.getWorld().getServer().getDatabase()
			.queryLoadGlobalCacheInt(VoidSubscription.VENDOR_TIER_CACHE_KEY);
		int safeTier = tier == null || tier < 0 ? 0 : tier;
		int safeStock = stock == null ? VoidSubscription.VENDOR_STOCK_PER_TIER : stock;
		if (safeStock <= 0) {
			return new VendorState(VoidSubscription.VENDOR_STOCK_PER_TIER, safeTier + 1);
		}
		return new VendorState(safeStock, safeTier);
	}

	private void saveState(Player player, VendorState state) {
		player.getWorld().getServer().getDatabase()
			.querySaveGlobalCacheInt(VoidSubscription.VENDOR_STOCK_CACHE_KEY, state.stock);
		player.getWorld().getServer().getDatabase()
			.querySaveGlobalCacheInt(VoidSubscription.VENDOR_TIER_CACHE_KEY, state.tier);
	}

	private boolean isVendor(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}

	private static final class VendorState {
		private final int stock;
		private final int tier;
		private final int price;

		private VendorState(int stock, int tier) {
			this.stock = stock;
			this.tier = tier;
			this.price = VoidSubscription.vendorPriceForTier(tier);
		}

		private VendorState afterSales(int sold) {
			int remaining = stock - sold;
			if (remaining <= 0) {
				return advanceTier();
			}
			return new VendorState(remaining, tier);
		}

		private VendorState advanceTier() {
			return new VendorState(VoidSubscription.VENDOR_STOCK_PER_TIER, tier + 1);
		}
	}
}
