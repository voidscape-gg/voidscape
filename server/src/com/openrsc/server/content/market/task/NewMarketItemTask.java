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

import java.util.ArrayList;
import java.util.Optional;

public class NewMarketItemTask extends MarketTask {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private static final int MAX_LISTINGS_PER_PLAYER = 6;

	private MarketItem newItem;
	private Player owner;
	private ArrayList<Item> itemsToAuction;

	public NewMarketItemTask(Player player, MarketItem item) {
		this.owner = player;
		this.newItem = item;
	}

	@Override
	public void doTask() {
		ItemDefinition def = owner.getWorld().getServer().getEntityHandler().getItemDef(newItem.getCatalogID());

		boolean updateDiscord = false;

		if (def == null) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Unknown item.", false);
			return;
		}
		if (newItem.getCatalogID() == ItemId.COINS.id() || def.isUntradable()) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ You cannot sell that item on auction house!", false);
			return;
		}
		if (newItem.getPrice() < 1) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Price must be greater than zero", false);
			return;
		}
		if (newItem.getAmount() < 1) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Amount must be greater than zero", false);
			return;
		}
		if (newItem.getPrice() < newItem.getAmount()) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Total price must be at least 1gp per item.", false);
			return;
		}
		if (newItem.getPrice() % newItem.getAmount() != 0) {
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Total price must divide evenly by quantity.", false);
			return;
		}

		// Ensure we have enough to satisfy the amount
		if (owner.getCarriedItems().getInventory().countId(newItem.getCatalogID(), Optional.empty()) < newItem.getAmount()) {
			return;
		}

		// voidscape: cap concurrent listings per player
		try {
			int currentListings = owner.getWorld().getServer().getDatabase().queryPlayerAuctionCount(owner.getDatabaseID());
			if (currentListings >= MAX_LISTINGS_PER_PLAYER) {
				ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ You already have " + MAX_LISTINGS_PER_PLAYER + " active listings. % Cancel one before posting another.", false);
				return;
			}
		} catch (GameDatabaseException e) {
			LOGGER.catching(e);
			return;
		}

		// TODO: Auction Fees
		/*int feeCost = (int) (newItem.getPrice() * 0.025);
		if(feeCost < 5)
			feeCost = 5;

		if(feeCost >= 5) {
			if((!owner.getCarriedItems().getInventory().contains(new Item(10)) && (!owner.getBank().contains(new Item(10))))) {
				ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ You have no coins in your inventory or bank to cover the auction fee.", false);
				return;
			} else {
				if(owner.getCarriedItems().getInventory().countId(10) >= feeCost) {
					owner.getCarriedItems().getInventory().remove(10, feeCost);
				} else if(owner.getBank().countId(10) >= feeCost){
					owner.getBank().remove(10, feeCost);
				} else {
					ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ You don't have enough coins in your inventory or bank to cover the auction fee. " + (feeCost != 0 ? "% Your auction fee: @gre@" + feeCost + "gp" : "No fee was required."), false);
					return;
				}
			}
		}*/

		if (owner.getWorld().getPlayer(DataConversions.usernameToHash(owner.getUsername())) == null) {
			return;
		}

		// Loop through inventory until quantity has been added.
		this.itemsToAuction = new ArrayList<>();
		int totalAuctionCount = 0;
		for (Item x : owner.getCarriedItems().getInventory().getItems()) {
			if (x.getCatalogId() != newItem.getCatalogID()) continue;
			if (x.getAmount() + totalAuctionCount == newItem.getAmount()) {
				totalAuctionCount += x.getAmount();
				this.itemsToAuction.add(x);
				break;
			}
			else if (x.getAmount() + totalAuctionCount > newItem.getAmount()) {
				int partialAmount = newItem.getAmount() - totalAuctionCount;
				totalAuctionCount += partialAmount;
				this.itemsToAuction.add(new Item(x.getCatalogId(), partialAmount, x.getNoted(), x.getItemId()));
				break;
			}
			else if (x.getAmount() + totalAuctionCount < newItem.getAmount()) {
				totalAuctionCount += x.getAmount();
				this.itemsToAuction.add(x);
			}
		}

		// No items found!
		if (this.itemsToAuction.size() == 0) return;

		// Remove applicable items.
		ArrayList<Item> removedItems = new ArrayList<>();
		for (Item x : this.itemsToAuction) {
			if (owner.getCarriedItems().remove(x) == -1) {
				rollbackRemovedItems(removedItems);
				ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Failed to remove item from your inventory.", false);
				return;
			}
			removedItems.add(x);
		}

		boolean dbOk = owner.getWorld().getServer().getDatabase().atomically(() -> {
			owner.getWorld().getServer().getDatabase().newAuction(newItem);
			owner.getWorld().getServer().getDatabase().savePlayerInventory(owner);
		});
		if (dbOk) {
			//ActionSender.sendBox(owner, "@gre@[Auction House - Success] % @whi@ Auction has been listed % " + newItem.getAmount() + "x @yel@" + def.getName() + " @whi@for @yel@" + newItem.getPrice() + "gp % @whi@Completed auction fee: @gre@" + feeCost + "gp", false);
			ActionSender.sendBox(owner, "@gre@[Auction House - Success] % @whi@ Auction has been listed % " + newItem.getAmount() + "x @yel@" + def.getName() + " @whi@for @yel@" + newItem.getPrice() + "gp", false);
			updateDiscord = true;
		} else {
			rollbackRemovedItems(removedItems);
			ActionSender.sendBox(owner, "@red@[Auction House - Error] % @whi@ Failed to add item to Auction. % Item(s) have been returned to your inventory.", false);
		}
		owner.getWorld().getMarket().addRequestOpenAuctionHouseTask(owner);
		if (updateDiscord) {
			DiscordService ds = owner.getWorld().getServer().getDiscordService();
			if (ds != null) {
				ds.auctionAdd(newItem);
			}
		}
	}

	private void rollbackRemovedItems(ArrayList<Item> removedItems) {
		if (removedItems == null) return;
		for (Item item : removedItems) {
			owner.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), item.getAmount(), item.getNoted()));
		}
	}
}
