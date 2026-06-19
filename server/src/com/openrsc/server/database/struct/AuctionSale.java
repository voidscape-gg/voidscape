package com.openrsc.server.database.struct;

public class AuctionSale {
	public int saleID;
	public int auctionID;
	public int itemID;
	public int amount;
	public int unitPrice;
	public int totalPrice;
	public int tax;
	public int seller;
	public String sellerUsername;
	public int buyer;
	public String buyerUsername;
	public long soldAt;
}
