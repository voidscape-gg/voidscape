package com.openrsc.server.content.market.task;

import com.openrsc.server.constants.ItemId;
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

public class BuyMarketItemTask extends MarketTask {

	private static final Logger LOGGER = LogManager.getLogger();

	private Player playerBuyer;
	private int auctionID;
	private int amount;

	public BuyMarketItemTask(Player buyer, final int auctionID, int amount) {
		this.playerBuyer = buyer;
		this.auctionID = auctionID;
		this.amount = amount;
	}

	@Override
	public void doTask() {
		try {
			MarketItem item = playerBuyer.getWorld().getServer().getDatabase().getAuctionItem(auctionID);
			boolean updateDiscord = false;

			if (item == null) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ This item is sold out! % Click 'Refresh' to update the Auction.", false);
				return;
			}
			if (amount <= 0) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ Invalid amount", false);
				return;
			}
			if (item.getSeller() == playerBuyer.getDatabaseID()) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ You can't buy your own object, please select another item. % Or cancel this item from the 'My Auction' tab.", false);
				return;
			}

			if (amount > item.getAmountLeft()) {
				amount = item.getAmountLeft();
			}
			if (item.getAmountLeft() <= 0 || item.getPrice() <= 0) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ This listing is no longer valid. % Click 'Refresh' to update the Auction.", false);
				return;
			}

			int priceForEach = item.getPrice() / item.getAmountLeft();
			if (priceForEach <= 0) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ This listing has an invalid price. % Ask staff to remove it.", false);
				return;
			}
			int auctionPrice = amount * priceForEach;

			if (playerBuyer.getCarriedItems().getInventory().countId(ItemId.COINS.id()) < auctionPrice) {
				ActionSender.sendBox(playerBuyer, "@ora@[Auction House - Warning] % @whi@ You don't have enough coins!", false);
				return;
			}

			if (playerBuyer.getWorld().getPlayer(DataConversions.usernameToHash(playerBuyer.getUsername())) == null) {
				return;
			}

			ItemDefinition def = playerBuyer.getWorld().getServer().getEntityHandler().getItemDef(item.getCatalogID());
			if (def == null) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ This listing has an invalid item.", false);
				return;
			}
			Item deliveryItem = createInventoryDeliveryItem(def, item.getCatalogID(), amount);
			boolean toInventory = playerBuyer.getCarriedItems().getInventory().canHold(deliveryItem);
			boolean toBank = !toInventory && playerBuyer.getBank().canHold(new Item(item.getCatalogID(), amount));
			if (!toInventory && !toBank) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ Unable to buy auction, no space left in your inventory or bank.", false);
				return;
			}

			int sellerUsernameID = item.getSeller();

			item.setBuyers(!item.getBuyers().isEmpty() ? item.getBuyers() + ", \n" + "[" + (System.currentTimeMillis() / 1000) + ": "
				+ playerBuyer.getUsername() + ": x" + amount + "]" : "[" + (System.currentTimeMillis() / 1000) + ": "
				+ playerBuyer.getUsername() + ": x" + amount + "]");

			item.setAmountLeft(item.getAmountLeft() - amount);
			item.setPrice(item.getAmountLeft() * priceForEach);

			// voidscape: 5% on-sale tax — destroyed at point of sale (gp sink)
			final int sellerProceeds = auctionPrice - (auctionPrice / 20);
			final MarketItem finalItem = item;
			final int finalAmount = amount;
			final int finalPriceForEach = priceForEach;
			final int finalAuctionPrice = auctionPrice;
			final int finalTax = auctionPrice - sellerProceeds;
			final int finalSellerProceeds = sellerProceeds;
			final int finalSeller = sellerUsernameID;
			final String soldExplanation = "Sold " + def.getName() + "(" + item.getCatalogID() + ") x" + amount + " for " + sellerProceeds + "gp (after 5% tax of " + (auctionPrice - sellerProceeds) + "gp)";
			boolean itemAdded = toInventory ? addInventoryItem(deliveryItem) : playerBuyer.getBank().add(new Item(item.getCatalogID(), amount), false);
			if (!itemAdded) {
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ Unable to add the purchased item.", false);
				return;
			}
			long coinsRemoved = playerBuyer.getCarriedItems().remove(new Item(ItemId.COINS.id(), auctionPrice));
			if (coinsRemoved == -1) {
				rollbackDelivery(deliveryItem, toInventory, amount);
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ Unable to remove coins from your inventory.", false);
				return;
			}

			final boolean finalToBank = toBank;
			boolean dbOk = playerBuyer.getWorld().getServer().getDatabase().atomically(() -> {
				playerBuyer.getWorld().getServer().getDatabase().addExpiredAuction(soldExplanation, ItemId.COINS.id(), finalSellerProceeds, finalSeller);
				playerBuyer.getWorld().getServer().getDatabase().addAuctionSale(finalItem, finalAmount, finalPriceForEach, finalAuctionPrice, finalTax, playerBuyer);
				if (finalItem.getAmountLeft() == 0) playerBuyer.getWorld().getServer().getDatabase().setSoldOut(finalItem);
				else playerBuyer.getWorld().getServer().getDatabase().updateAuction(finalItem);
				playerBuyer.getWorld().getServer().getDatabase().savePlayerInventory(playerBuyer);
				if (finalToBank) {
					playerBuyer.getWorld().getServer().getDatabase().savePlayerBank(playerBuyer);
				}
			});
			if (!dbOk) {
				rollbackDelivery(deliveryItem, toInventory, amount);
				playerBuyer.getCarriedItems().getInventory().add(new Item(ItemId.COINS.id(), auctionPrice));
				ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ The purchase could not be completed. Please try again.", false);
				return;
			}

			if (toInventory) {
				ActionSender.sendBox(playerBuyer, "@gre@[Auction House - Success] % @whi@ The item has been added to your inventory.", false);
			} else {
				ActionSender.sendBox(playerBuyer, "@gre@[Auction House - Success] % @whi@ The item has been added to your bank.", false);
			}
			updateDiscord = true;

			Player sellerPlayer = playerBuyer.getWorld().getPlayerID(sellerUsernameID);
			if (sellerPlayer != null) {
				sellerPlayer.message("@gre@[Auction House]@lre@ " + amount + "x " + def.getName() + "@whi@ has been sold!");
				sellerPlayer.message("@gre@[Auction House]@whi@ You can collect your earnings from a bank.");
			}

			for (MarketItem marketItem : playerBuyer.getWorld().getMarket().getAuctionItems()) {
				if (marketItem.getAuctionID() == item.getAuctionID()) {
					marketItem.setAmountLeft(item.getAmountLeft());
					marketItem.setPrice(item.getPrice());
				}
			}

			playerBuyer.getWorld().getMarket().addRequestOpenAuctionHouseTask(playerBuyer);

			if (updateDiscord) {
				DiscordService ds = playerBuyer.getWorld().getServer().getDiscordService();
				if (ds != null) {
					ds.auctionBuy(item);
				}
			}
		} catch (GameDatabaseException e) {
			ActionSender.sendBox(playerBuyer, "@red@[Auction House - Error] % @whi@ There was a problem accessing the database % Please try again.", false);
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
		ItemDefinition def = item.getDef(playerBuyer.getWorld());
		if (def == null) return false;
		if (!def.isStackable() && !item.getNoted() && item.getAmount() > 1) {
			int added = 0;
			for (int i = 0; i < item.getAmount(); i++) {
				if (!playerBuyer.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), 1))) {
					for (int rollback = 0; rollback < added; rollback++) {
						playerBuyer.getCarriedItems().remove(new Item(item.getCatalogId(), 1, false));
					}
					return false;
				}
				added++;
			}
			return true;
		}
		return playerBuyer.getCarriedItems().getInventory().add(item);
	}

	private void rollbackDelivery(Item deliveryItem, boolean toInventory, int amount) {
		if (toInventory) {
			ItemDefinition def = deliveryItem.getDef(playerBuyer.getWorld());
			if (def != null && !def.isStackable() && !deliveryItem.getNoted() && amount > 1) {
				for (int i = 0; i < amount; i++) {
					playerBuyer.getCarriedItems().remove(new Item(deliveryItem.getCatalogId(), 1, false));
				}
				return;
			}
			playerBuyer.getCarriedItems().remove(deliveryItem);
			return;
		}
		playerBuyer.getBank().remove(deliveryItem.getCatalogId(), amount, false);
	}

}
