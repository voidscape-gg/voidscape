package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketItem;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.DiscordService;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CancelMarketItemTask extends MarketTask {

	private static final Logger LOGGER = LogManager.getLogger();

	private Player owner;
	private int auctionID;

	public CancelMarketItemTask(Player owner, final int auctionID) {
		this.owner = owner;
		this.auctionID = auctionID;
	}

	@Override
	public void doTask() {
		try {
			boolean updateDiscord = false;
			MarketItem item = owner.getWorld().getServer().getDatabase().getAuctionItem(auctionID);
			if (item != null) {
				int itemIndex = item.getCatalogID();
				int amount = item.getAmountLeft();
				int seller = item.getSeller();
				if (owner.getWorld().getPlayer(DataConversions.usernameToHash(owner.getUsername())) == null) {
					return;
				}
				if (owner.getDatabaseID() != seller) {
					LOGGER.info("Auction Player Database ID Mismatch, possible auction cancel packet manipulation by " + owner.getUsername());
					DiscordService ds = owner.getWorld().getServer().getDiscordService();
					if (ds != null) {
						ds.playerLog(owner, "Auction Player Database ID Mismatch, possible auction cancel packet manipulation by " + owner.getUsername());
					}
					return;
				}
				ItemDefinition def = owner.getWorld().getServer().getEntityHandler().getItemDef(itemIndex);
				if (def == null || amount <= 0) {
					ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to cancel auction.", false);
					return;
				}
				Item deliveryItem = createInventoryDeliveryItem(def, itemIndex, amount);
				boolean toInventory = owner.getCarriedItems().getInventory().canHold(deliveryItem);
				boolean toBank = !toInventory && owner.getBank().canHold(new Item(itemIndex, amount));
				if (toInventory) {
					if (!addInventoryItem(deliveryItem)) {
						ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to return item to your inventory.", false);
						return;
					}
					boolean dbOk = owner.getWorld().getServer().getDatabase().atomically(() -> {
						owner.getWorld().getServer().getDatabase().cancelAuction(item.getAuctionID());
						owner.getWorld().getServer().getDatabase().savePlayerInventory(owner);
					});
					if (!dbOk) {
						rollbackDelivery(deliveryItem, true, amount);
						ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to cancel auction.", false);
						return;
					}
					ActionSender.sendBox(owner, "@gre@[Auction House - Success] % @whi@ The item has been canceled and returned to your inventory.", false);
					updateDiscord = true;
				} else if (toBank) {
					if (!owner.getBank().add(new Item(itemIndex, amount), false)) {
						ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to return item to your bank.", false);
						return;
					}
					boolean dbOk = owner.getWorld().getServer().getDatabase().atomically(() -> {
						owner.getWorld().getServer().getDatabase().cancelAuction(item.getAuctionID());
						owner.getWorld().getServer().getDatabase().savePlayerBank(owner);
					});
					if (!dbOk) {
						rollbackDelivery(deliveryItem, false, amount);
						ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to cancel auction.", false);
						return;
					}
					ActionSender.sendBox(owner, "@gre@[Auction House - Success] % @whi@ The item has been canceled and returned to your bank. % Talk with a Banker to collect your item(s).", false);
					updateDiscord = true;
				} else
					ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unable to cancel auction! % % @red@Reason: @whi@No space left in your bank or inventory.", false);

			}
			owner.getWorld().getMarket().addRequestOpenAuctionHouseTask(owner);
			if (updateDiscord) {
				DiscordService ds = owner.getWorld().getServer().getDiscordService();
				if (ds != null) {
					ds.auctionCancel(item);
				}
			}
		} catch (GameDatabaseException e) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ There was a problem accessing the database % Please try again.", false);
			LOGGER.catching(e);
			return;
		}
	}

	private Item createInventoryDeliveryItem(ItemDefinition def, int catalogID, int amount) {
		if (def.isStackable() || amount == 1) {
			return new Item(catalogID, amount);
		}
		if (def.isNoteable()) {
			return new Item(catalogID, amount, true);
		}
		return new Item(catalogID, amount);
	}

	private boolean addInventoryItem(Item item) {
		ItemDefinition def = item.getDef(owner.getWorld());
		if (def == null) return false;
		if (!def.isStackable() && !item.getNoted() && item.getAmount() > 1) {
			int added = 0;
			for (int i = 0; i < item.getAmount(); i++) {
				if (!owner.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), 1))) {
					for (int rollback = 0; rollback < added; rollback++) {
						owner.getCarriedItems().remove(new Item(item.getCatalogId(), 1, false));
					}
					return false;
				}
				added++;
			}
			return true;
		}
		return owner.getCarriedItems().getInventory().add(item);
	}

	private void rollbackDelivery(Item deliveryItem, boolean toInventory, int amount) {
		if (toInventory) {
			ItemDefinition def = deliveryItem.getDef(owner.getWorld());
			if (def != null && !def.isStackable() && !deliveryItem.getNoted() && amount > 1) {
				for (int i = 0; i < amount; i++) {
					owner.getCarriedItems().remove(new Item(deliveryItem.getCatalogId(), 1, false));
				}
				return;
			}
			owner.getCarriedItems().remove(deliveryItem);
			return;
		}
		owner.getBank().remove(deliveryItem.getCatalogId(), amount, false);
	}

}
