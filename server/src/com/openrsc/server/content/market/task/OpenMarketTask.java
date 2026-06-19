package com.openrsc.server.content.market.task;

import com.openrsc.server.content.market.MarketItem;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.AuctionItemSummary;
import com.openrsc.server.database.struct.AuctionSale;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.Packet;
import com.openrsc.server.net.PacketBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;

public class OpenMarketTask extends MarketTask {

	private static final Logger LOGGER = LogManager.getLogger();
	private static final int MARKET_WINDOW_SECONDS = 7 * 24 * 60 * 60;
	private static final int MARKET_INTEL_LIMIT = 10;

	private Player owner;

	public OpenMarketTask(Player player) {
		this.owner = player;
	}

	public void doTask() {
		PacketBuilder pb = new PacketBuilder(132);
		pb.writeByte(0);
		owner.write(pb.toPacket());

		@SuppressWarnings("unchecked")
		ArrayList<MarketItem> items = (ArrayList<MarketItem>) owner.getWorld().getMarket().getAuctionItems().clone();
		Iterator<MarketItem> iterator = items.iterator();

		int currentWritten = 0;
		AuctionPacketChunk chunk = new AuctionPacketChunk();
		while (iterator.hasNext()) {
			if (chunk.getChunkItemCount() >= 200) {
				currentWritten += chunk.getChunkItemCount();
				owner.write(chunk.toPacket());
				chunk.reset();
			}
			MarketItem item = iterator.next();
			chunk.addItem(item);
		}

		if (!chunk.isFinished())
			owner.write(chunk.toPacket());

		sendMarketIntel();
	}

	private void sendMarketIntel() {
		try {
			final long since = (System.currentTimeMillis() / 1000) - MARKET_WINDOW_SECONDS;
			final AuctionItemSummary[] summaries = owner.getWorld().getServer().getDatabase().getAuctionItemSummaries(since);
			final AuctionItemSummary[] hotItems = owner.getWorld().getServer().getDatabase().getHotAuctionItems(since, MARKET_INTEL_LIMIT);
			final AuctionSale[] recentSales = owner.getWorld().getServer().getDatabase().getRecentAuctionSales(MARKET_INTEL_LIMIT);

			PacketBuilder pb = new PacketBuilder(132);
			pb.writeByte(2);

			pb.writeShort(summaries.length);
			for (AuctionItemSummary summary : summaries) {
				writeSummary(pb, summary);
			}

			pb.writeShort(hotItems.length);
			for (AuctionItemSummary summary : hotItems) {
				pb.writeInt(summary.itemID);
				pb.writeInt(summary.volumeSold);
				pb.writeInt(summary.totalValue);
				pb.writeInt(summary.averageUnitPrice);
				pb.writeInt(safeInt(summary.lastSoldAt));
			}

			pb.writeShort(recentSales.length);
			for (AuctionSale sale : recentSales) {
				pb.writeInt(sale.itemID);
				pb.writeInt(sale.amount);
				pb.writeInt(sale.unitPrice);
				pb.writeInt(sale.totalPrice);
				pb.writeInt(safeInt(sale.soldAt));
			}

			owner.write(pb.toPacket());
		} catch (GameDatabaseException e) {
			LOGGER.catching(e);
		}
	}

	private void writeSummary(PacketBuilder pb, AuctionItemSummary summary) {
		pb.writeInt(summary.itemID);
		pb.writeInt(summary.activeLowestEach);
		pb.writeInt(summary.activeHighestEach);
		pb.writeInt(summary.activeAmount);
		pb.writeInt(summary.lastUnitPrice);
		pb.writeInt(summary.averageUnitPrice);
		pb.writeInt(summary.volumeSold);
		pb.writeInt(summary.totalValue);
		pb.writeInt(safeInt(summary.lastSoldAt));
	}

	private int safeInt(long value) {
		if (value > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		if (value < Integer.MIN_VALUE) {
			return Integer.MIN_VALUE;
		}
		return (int) value;
	}

	private class AuctionPacketChunk {
		private ArrayList<MarketItem> items = new ArrayList<>();
		private PacketBuilder builder = new PacketBuilder();

		private boolean finished = false;

		AuctionPacketChunk() {
			builder = new PacketBuilder();
			builder.setID(132);
			builder.writeByte(1);
		}

		public void reset() {
			setFinished(false);
			items.clear();
			builder = new PacketBuilder();
			builder.setID(132);
			builder.writeByte(1);
		}

		public void addItem(MarketItem item) {
			items.add(item);
		}

		int getChunkItemCount() {
			return items.size();
		}

		Packet toPacket() {
			builder.writeShort(items.size());
			for (MarketItem item : items) {
				builder.writeInt(item.getAuctionID());
				builder.writeInt(item.getCatalogID());
				builder.writeInt(item.getAmountLeft());
				builder.writeInt(item.getPrice());
				builder.writeByte(item.getSeller() == owner.getDatabaseID() ? 1 : 0);
				if (item.getSeller() != owner.getDatabaseID()) builder.writeString(item.getSellerName());
				builder.writeShort(item.getHoursLeft());
			}
			setFinished(true);
			return builder.toPacket();
		}

		public boolean isFinished() {
			return finished;
		}

		public void setFinished(boolean finished) {
			this.finished = finished;
		}

	}
}
