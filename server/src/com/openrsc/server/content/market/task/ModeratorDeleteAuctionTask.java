package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketItem;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModeratorDeleteAuctionTask extends MarketTask {

	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger();

	private Player player;
	private int auctionID;

	public ModeratorDeleteAuctionTask(Player mod, int auctionID) {
		this.player = mod;
		this.auctionID = auctionID;
	}

	@Override
	public void doTask() {
		boolean updateDiscord = false;
		if (!player.isMod()) {
			player.setSuspiciousPlayer(true, "tried mod delete auction when not mod");
			ActionSender.sendBox(player, "@red@[Auction House - Error] % @whi@ Unable to remove auction", false);
		} else {
			try {
				MarketItem item = player.getWorld().getServer().getDatabase().getAuctionItem(auctionID);
				if (item != null) {
					int itemIndex = item.getCatalogID();
					int amount = item.getAmountLeft();
					boolean dbOk = player.getWorld().getServer().getDatabase().atomically(() -> {
						player.getWorld().getServer().getDatabase().setSoldOut(item);
						player.getWorld().getServer().getDatabase().addExpiredAuction("Removed by " + player.getStaffName(), itemIndex, amount, item.getSeller());
					});
					if (dbOk) {
						ActionSender.sendBox(player, "@gre@[Auction House - Success] % @whi@ Item has been removed from Auctions. % % Returned to collections for:  " + item.getSellerName(), false);
						recordModeratorDeleteReceipt(item, amount);
						updateDiscord = true;
					} else {
						ActionSender.sendBox(player, "@red@[Auction House - Error] % @whi@ Unable to remove auction", false);
					}
				}
				player.getWorld().getMarket().addRequestOpenAuctionHouseTask(player);
				if (updateDiscord) {
					if (player.getWorld().getServer().getDiscordService() != null) {
						player.getWorld().getServer().getDiscordService().auctionModDelete(item);
					}
				}
			} catch (GameDatabaseException e) {
				ActionSender.sendBox(player, "@red@[Auction House - Error] % @whi@ There was a problem accessing the database % Please try again.", false);
				LOGGER.catching(e);
				return;
			}
		}
	}

	private void recordModeratorDeleteReceipt(final MarketItem item, final int amount) {
		final int x = player.getX();
		final int y = player.getY();
		final String extra = "auction_id=" + item.getAuctionID()
			+ " seller=" + item.getSeller()
			+ " removed_by=" + player.getStaffName();
		player.getWorld().getServer().submitSqlLogging(() -> {
			try {
				player.getWorld().getServer().getDatabase().addItemProvenanceEvent(player, null, "item_transfer",
					"auction_listing", "auction_collectible", "auction_mod_delete", item.getCatalogID(), amount,
					false, 0, x, y, extra);
			} catch (final GameDatabaseException ex) {
				LOGGER.catching(ex);
			}
		});
	}
}
