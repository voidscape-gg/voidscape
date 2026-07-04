package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import orsc.Config;
import orsc.enumerations.MessageType;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;

public final class AuctionHouse {
	private static final String[] CATEGORY_LABELS = {
		"All", "Weapon", "Armour", "Food", "Ammo", "Jewels", "Ore & Bar", "Herblaw", "Rares", "Misc"
	};
	private static final String[] CATEGORY_SHORT_LABELS = {
		"All", "Wep", "Arm", "Food", "Ammo", "Gem", "Ore", "Herb", "Rare", "Misc"
	};
	private static final int[] CATEGORY_ICON_IDS = {
		10, 75, 404, 373, 646, 305, 172, 464, 576, 14
	};
	private static final int COLOR_FRAME = 0x24313b;
	private static final int COLOR_PANEL = 0x111820;
	private static final int COLOR_PANEL_SOFT = 0x1d2731;
	private static final int COLOR_HOVER = 0x304458;
	private static final int COLOR_SELECTED = 0x6b8e23;
	private static final int COLOR_GOLD = 0xc1b575;

	// Layout geometry shared by the draw code and the workbench accessors so a
	// coordinate can never be hardcoded twice and drift. Every magic offset the
	// renderer uses for a hit-testable control lives here.
	private static final int TAB_BUTTON_X_OFFSET = 2;
	private static final int TAB_BUTTON_Y_OFFSET = 14;
	private static final int TAB_BUTTON_WIDTH = 80;
	private static final int TAB_BUTTON_HEIGHT = 21;
	private static final int TAB_BUTTON_PITCH = 82;
	private static final int CATEGORY_RAIL_X_OFFSET = 3;
	private static final int CATEGORY_RAIL_Y_OFFSET = 62;
	private static final int CATEGORY_TILE_INSET_X = 5;
	private static final int CATEGORY_TILE_INSET_Y = 23;
	private static final int CATEGORY_TILE_COL_PITCH = 38;
	private static final int CATEGORY_TILE_ROW_PITCH = 39;
	private static final int CATEGORY_TILE_WIDTH = 35;
	private static final int CATEGORY_TILE_HEIGHT = 36;
	private static final int LIST_X_OFFSET = 94;
	private static final int LIST_Y_OFFSET = 102;
	private static final int LIST_WIDTH = 392;
	private static final int LIST_ROW_HEIGHT = 36;

	public int auctionScrollHandle;
	public int auctionSearchHandle;
	public Panel auctionMenu;
	public Panel myAuctions;
	public Panel selectItemGUI;
	public int activeInterface;
	public int myAuctionScrollHandle;
	public int textField_amount;
	public int textField_price;
	public int textField_priceEach;
	public int textField_buyAmount;
	private int x, y;
	private int width, height;
	private ArrayList<AuctionItem> auctionItems;
	private ArrayList<MarketHotItem> hotMarketItems;
	private ArrayList<MarketRecentSale> recentMarketSales;
	private HashMap<Integer, AuctionItemIntel> marketIntelByItemID;
	private int newAuctionInventoryIndex = -1;
	private AuctionItem newAuctionItem = null;
	private int selectedAuction = -1;
	private int selectedCancelAuction = -1;
	private int confirmAuctionID = -1;
	private int confirmAmount = -1;
	private long confirmExpiresAt = 0L;

	private int selectItemAdd = 0;

	private boolean visible = false;
	private int selectedFilter;
	private int orderingBy = 0;
	private Comparator<AuctionItem> auctionComparator = (o1, o2) -> {
		if (orderingBy == 0) { /* price low */
			return Integer.compare(o1.getPrice(), o2.getPrice());
		} else if (orderingBy == 1) { /* price high */
			return Integer.compare(o2.getPrice(), o1.getPrice());
		} else if (orderingBy == 2) { /* name */
			ItemDef d1 = EntityHandler.getItemDef(o1.getItemID());
			ItemDef d2 = EntityHandler.getItemDef(o2.getItemID());

			return d1.getName().compareToIgnoreCase(d2.getName());
		} else if (orderingBy == 3) { /* price each low */
			int priceEach1 = o1.getAmount() > 0 ? o1.getPrice() / o1.getAmount() : Integer.MAX_VALUE;
			int priceEach2 = o2.getAmount() > 0 ? o2.getPrice() / o2.getAmount() : Integer.MAX_VALUE;
			return Integer.compare(priceEach1, priceEach2);
		} else if (orderingBy == 4) { /* price each high */
			int priceEach1 = o1.getAmount() > 0 ? o1.getPrice() / o1.getAmount() : 0;
			int priceEach2 = o2.getAmount() > 0 ? o2.getPrice() / o2.getAmount() : 0;
			return Integer.compare(priceEach2, priceEach1);
		}
		ItemDef d1 = EntityHandler.getItemDef(o1.getAuctionID());
		ItemDef d2 = EntityHandler.getItemDef(o2.getAuctionID());

		return d1.getName().compareToIgnoreCase(d2.getName());
	};
	private String sortBy = "Price Low";
	private double fee;
	private mudclient mc;

	public AuctionHouse(mudclient mc) {
		this.mc = mc;

		width = 490;
		height = 326 - 47;

		x = (mc.getGameWidth() / 2) - width;
		y = (mc.getGameHeight() / 2) - height;

		auctionItems = new ArrayList<>();
		hotMarketItems = new ArrayList<>();
		recentMarketSales = new ArrayList<>();
		marketIntelByItemID = new HashMap<>();

		auctionMenu = new Panel(mc.getSurface(), 5);
		myAuctions = new Panel(mc.getSurface(), 15);

		auctionScrollHandle = auctionMenu.addScrollingList2(x + 94, y + 100, 394, 184, 1000, 7, true);
		auctionSearchHandle = auctionMenu.addLeftTextEntry(x + 314, y + 48, 172, 18, 1, 36, false, true);
		textField_buyAmount = auctionMenu.addLeftTextEntry(x + 105, y + 240, 84, 18, 1, 10, false, true);

		textField_price = myAuctions.addLeftTextEntry(x + 60, y + 130, 70, 18, 1, 8, false, true);
		textField_amount = myAuctions.addLeftTextEntry(x + 60, y + 209, 70, 18, 1, 8, false, true);
		textField_priceEach = myAuctions.addLeftTextEntry(x + 60, y + 169, 70, 18, 1, 8, false, true);

		myAuctionScrollHandle = myAuctions.addScrollingList2(x + 216, y + 74, 270, 179, 1000, 7, true);
	}

	public void reposition() {
		x = originX();
		y = originY();

		auctionMenu.reposition(auctionScrollHandle, x + 94, y + 100, 394, 184);
		auctionMenu.reposition(auctionSearchHandle, x + 314, y + 48, 172, 18);
		auctionMenu.reposition(textField_buyAmount, x + 105, y + 240, 84, 18);

		myAuctions.reposition(myAuctionScrollHandle, x + 216, y + 74, 270, 179);
		myAuctions.reposition(textField_amount, x + 60, y + 209, 70, 18);
		myAuctions.reposition(textField_price, x + 60, y + 130, 70, 18);
		myAuctions.reposition(textField_priceEach, x + 60, y + 169, 70, 18);
	}

	// Origin of the whole window (top-left of the title bar), centered the same
	// way reposition() places it. The draw code reads x/y after reposition()
	// runs; the workbench accessors re-derive it here so both share one formula.
	private int originX() {
		return (mc.getGameWidth() - width) / 2;
	}

	private int originY() {
		return (mc.getGameHeight() - height) / 2;
	}

	private int tabButtonLeftX(int index) {
		return x + TAB_BUTTON_X_OFFSET + index * TAB_BUTTON_PITCH;
	}

	private int tabButtonTopY() {
		return y + TAB_BUTTON_Y_OFFSET;
	}

	private int categoryRailX() {
		return x + CATEGORY_RAIL_X_OFFSET;
	}

	private int categoryRailY() {
		return y + CATEGORY_RAIL_Y_OFFSET;
	}

	private int categoryTileLeftX(int filter) {
		return categoryRailX() + CATEGORY_TILE_INSET_X + (filter % 2) * CATEGORY_TILE_COL_PITCH;
	}

	private int categoryTileTopY(int filter) {
		return categoryRailY() + CATEGORY_TILE_INSET_Y + (filter / 2) * CATEGORY_TILE_ROW_PITCH;
	}

	private int listLeftX() {
		return x + LIST_X_OFFSET;
	}

	private int listTopY() {
		return y + LIST_Y_OFFSET;
	}

	// Re-center x/y for a workbench read without the panel-control side effects
	// of reposition(). Writes the same values reposition() would, so it is safe
	// to call off the render thread and never changes what is drawn.
	private void ensureWorkbenchLayout() {
		x = originX();
		y = originY();
	}

	public int workbenchPanelX() {
		ensureWorkbenchLayout();
		return x;
	}

	public int workbenchPanelY() {
		ensureWorkbenchLayout();
		return y;
	}

	public int workbenchTabCenterX(int index) {
		ensureWorkbenchLayout();
		return tabButtonLeftX(index) + TAB_BUTTON_WIDTH / 2;
	}

	public int workbenchTabCenterY() {
		ensureWorkbenchLayout();
		return tabButtonTopY() + TAB_BUTTON_HEIGHT / 2;
	}

	public int workbenchCategoryCenterX(int filter) {
		ensureWorkbenchLayout();
		return categoryTileLeftX(filter) + CATEGORY_TILE_WIDTH / 2;
	}

	public int workbenchCategoryCenterY(int filter) {
		ensureWorkbenchLayout();
		return categoryTileTopY(filter) + CATEGORY_TILE_HEIGHT / 2;
	}

	public int workbenchFirstRowCenterX() {
		ensureWorkbenchLayout();
		return listLeftX() + LIST_WIDTH / 2;
	}

	public int workbenchFirstRowCenterY() {
		ensureWorkbenchLayout();
		return listTopY() + LIST_ROW_HEIGHT / 2;
	}

	private boolean inBounds(int x, int y, int rectX, int rectY, int width, int height)
	{
		return x >= rectX && x < (rectX + width) && y >= rectY && y < (rectY + height);
	}

	public boolean onRender(GraphicsController graphics) {
		reposition();

		if (!Config.isAndroid() && mc.getMouseClick() == 1) {
			if(!inBounds(mc.getMouseX(), mc.getMouseY(), x, y, width, height + 12)) {
				auctionClose();
			}
		}

		graphics.drawBoxAlpha(x, y, width, 12, COLOR_FRAME, 230);
		graphics.drawBoxAlpha(x, y + 12, width, height, COLOR_PANEL, 214);
		graphics.drawBoxBorder(x, width, y, height + 12, 0x0b0d10);
		drawItemSprite(10, x + 4, y - 1, 18, 14);
		graphics.drawString("Auction House", x + 24, y + 10, 0xffffff, 1);

		drawButton(graphics, tabButtonLeftX(0), tabButtonTopY(), TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, "Browse", activeInterface == 0, new ButtonHandler() {
			@Override
			void handle() {
				activeInterface = 0;
				auctionMenu.setFocus(-1);
				myAuctions.setFocus(-1);
			}
		});
		drawButton(graphics, tabButtonLeftX(1), tabButtonTopY(), TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, "My Auctions", activeInterface == 1, new ButtonHandler() {
			@Override
			void handle() {
				activeInterface = 1;
				auctionMenu.setFocus(-1);
				myAuctions.setFocus(-1);
			}
		});
		drawButton(graphics, tabButtonLeftX(2), tabButtonTopY(), TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT, "Intel", activeInterface == 2, new ButtonHandler() {
			@Override
			void handle() {
				activeInterface = 2;
				selectedAuction = -1;
				resetPurchaseConfirm();
				auctionMenu.setFocus(-1);
				myAuctions.setFocus(-1);
			}
		});

		drawButton(graphics, x + 408, y + 14, 80, 21, "Refresh", false, new ButtonHandler() {
			@Override
			void handle() {
				sendRefreshList();
				auctionMenu.setFocus(-1);
				myAuctions.setFocus(-1);
			}
		});

		drawTextHit(graphics, x + 405, y - 1, 81, 12, "Close window", false, new ButtonHandler() {
			@Override
			void handle() {
				auctionClose();
			}
		});

		if (activeInterface == 0) {
			drawAuctionMenu(graphics);
		} else if (activeInterface == 1) {
			drawMyAuctions(graphics);
		} else if (activeInterface == 2) {
			drawMarketIntel(graphics);
		}
		return true;
	}

	private void auctionClose() {
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(10);
		mc.packetHandler.getClientStream().bufferBits.putByte(4);
		mc.packetHandler.getClientStream().finishPacket();
		resetAllVariables();
		setVisible(false);
	}

	private void resetPurchaseConfirm() {
		confirmAuctionID = -1;
		confirmAmount = -1;
		confirmExpiresAt = 0L;
	}

	private boolean hasPurchaseConfirm(AuctionItem item, int amount) {
		return item != null
			&& confirmAuctionID == item.getAuctionID()
			&& confirmAmount == amount
			&& System.currentTimeMillis() <= confirmExpiresAt;
	}

	private int parseAmount(String text, int max) {
		if (max <= 0) return 0;
		try {
			int value = Integer.parseInt(text);
			if (value < 1) return 1;
			return Math.min(value, max);
		} catch (NumberFormatException ex) {
			return 1;
		}
	}

	private void setBuyAmount(int amount, int max) {
		int safeAmount = parseAmount(String.valueOf(amount), max);
		auctionMenu.setText(textField_buyAmount, String.valueOf(safeAmount));
		resetPurchaseConfirm();
	}

	private int safePrice(int amount, int priceEach) {
		long value = (long) amount * (long) priceEach;
		if (value > Integer.MAX_VALUE) {
			return Integer.MAX_VALUE;
		}
		return (int) Math.max(1L, value);
	}

	private void sendRefreshList() {
		selectedAuction = -1;
		resetPurchaseConfirm();
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(10);
		mc.packetHandler.getClientStream().bufferBits.putByte(3);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private void drawMyAuctions(GraphicsController graphics) {
		myAuctions.clearList(myAuctionScrollHandle);

		graphics.drawBoxAlpha(x + 3, y + 37, 129, 67, 0, 60);
		graphics.drawBoxBorder(x + 2, 130, y + 37, 68, 0x343434);


		int inventorySlot = 0;
		int i7 = 0xd0d0d0;
		int inventoryDrawX = x + 182;
		int inventoryDrawY = y + 40;

		int boxWidth = (49);
		int boxHeight = (34);

		// START RIGHT SIDE
		graphics.drawBoxAlpha(x + 138, y + 37, 349, 251, 0, 60);
		graphics.drawBoxBorder(x + 137, 350, y + 37, 252, 0x343434);

		if (newAuctionItem == null) {
			drawButtonFancy(graphics, x + 16, y + 37 + 10, 100, 48, "+ Select item", selectItemAdd == 1, new ButtonHandler() {
				@Override
				void handle() {
					selectItemAdd = 1;
				}
			});
		} else if (newAuctionItem != null) {
			ItemDef def = EntityHandler.getItemDef(newAuctionItem.getItemID());
			if (newAuctionItem.getNoted()) {
				if (Config.S_WANT_CERT_AS_NOTES) {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.noteDef), x + 40, y + 55, 48, 32,
						EntityHandler.noteDef.getPictureMask(), 0, EntityHandler.noteDef.getBlueMask(), false, 0, 1);
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), x + 47,
						y + 59, 33, 23, def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
				} else {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.certificateDef), x + 40, y + 55, 48, 32,
						EntityHandler.certificateDef.getPictureMask(), 0, EntityHandler.certificateDef.getBlueMask(), false, 0, 1);
				}
			} else {
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), x + 40, y + 55, 48, 32,
					def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
			}
			//graphics.drawString("Fee: +" + (int) getFee() + "gp", x + 6, y + 101, 0xffffff, 0);
			graphics.drawString(def.getName(), x + 6, y + 101, 0xffffff, 0);
		}
		graphics.drawBoxAlpha(x + 3, y + 37 + 71, 129, 181, 0, 60);
		graphics.drawBoxBorder(x + 2, 130, y + 37 + 70, 182, 0x343434);

		graphics.drawBoxAlpha(x + 57, y + 121, 70, 18, 0x0C0C0C, 228);
		graphics.drawBoxBorder(x + 57, 70, y + 121, 18, 0x35231B);
		graphics.drawString("GP Total:", x + 6, y + 133, 0xffffff, 0);

		graphics.drawLineHoriz(x + 5, y + 133 + 15, 124, 0x222222);


		graphics.drawBoxAlpha(x + 57, y + 133 + 27, 70, 18, 0x0C0C0C, 228);
		graphics.drawBoxBorder(x + 57, 70, y + 133 + 27, 18, 0x35231B);
		graphics.drawString("GP Each:", x + 6, y + 133 + 39, 0xffffff, 0);

		graphics.drawLineHoriz(x + 5, y + 133 + 39 + 16, 124, 0x222222);

		graphics.drawBoxAlpha(x + 57, y + 200, 70, 18, 0x0C0C0C, 228);
		graphics.drawBoxBorder(x + 57, 70, y + 200, 18, 0x35231B);
		graphics.drawString("Quantity:", x + 6, y + 133 + 39 + 16 + 24, 0xffffff, 0);

		graphics.drawLineHoriz(x + 5, y + 133 + 39 + 16 + 24 + 16, 124, 0x222222);

		drawButtonFancy(graphics, x + 16, y + 238, 100, 27, "Create Auction", newAuctionItem == null, new ButtonHandler() {
			@Override
			void handle() {
				sendCreateAuction();
			}
		});

		//graphics.drawString("Fee: 2.5%", x + 5 + 38, y + 280, 0xffffff, 0);
		// END RIGHT SIDE

		if (selectItemAdd == 1) {
			//graphics.drawString("Auction House has a fee of 2.5% upon adding your sale", x + 176, y + 285, 0xffffff, 0);
			graphics.drawString("My Inventory", x + 189, y + 64, 0xFFFF00, 1);
			drawButton(graphics, x + 402, y + 32 + 10, 80, 21, "< My Listings", false, new ButtonHandler() {
				@Override
				void handle() {
					selectItemAdd = 0;
					newAuctionItem = null;
					newAuctionInventoryIndex = -1;
					selectedCancelAuction = -1;
					myAuctions.hide(textField_priceEach);
					myAuctions.hide(textField_price);
					myAuctions.hide(textField_amount);
				}
			});
			for (int verticalSlots = 0; verticalSlots < 6; verticalSlots++) {
				for (int horizonalSlots = 0; horizonalSlots < 5; horizonalSlots++) {
					int drawX = inventoryDrawX + 7 + horizonalSlots * boxWidth;
					int drawY = inventoryDrawY + 28 + verticalSlots * boxHeight;
					graphics.drawBoxAlpha(drawX, drawY, boxWidth, boxHeight, i7, 160);
					if (newAuctionInventoryIndex == inventorySlot) {
						graphics.drawBoxAlpha(drawX, drawY, boxWidth, boxHeight, 0xff, 160);
					}
					graphics.drawBoxBorder(drawX, boxWidth + 1, drawY, boxHeight + 1, 0);
					if (inventorySlot < mc.getInventoryItemCount() && mc.getInventoryItemID(inventorySlot) != -1) {
						Item item = mc.getInventoryItem(inventorySlot);
						ItemDef def = item.getItemDef();
						if (item.getNoted()) {
							if (Config.S_WANT_CERT_AS_NOTES) {
								mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.noteDef), drawX,
									drawY, 48, 32, EntityHandler.noteDef.getPictureMask(), 0, EntityHandler.noteDef.getBlueMask(), false, 0, 1);
								mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX + 7, drawY + 4, 33,
									23, def.getPictureMask(), 0, def.getBlueMask(),false, 0, 1);
							} else {
								mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.certificateDef), drawX,
									drawY, 48, 32, EntityHandler.certificateDef.getPictureMask(), 0, EntityHandler.certificateDef.getBlueMask(), false, 0, 1);
							}
						} else {
							mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX, drawY, 48,
								32, def.getPictureMask(), 0, def.getBlueMask(),false, 0, 1);
						}
						graphics.drawString(String.valueOf(mc.getInventoryItemAmount(inventorySlot)), drawX + 1,
							drawY + 10, 65280, 1);
					}
					if (mc.getMouseX() > drawX && mc.getMouseX() < drawX + boxWidth && mc.getMouseY() > drawY
						&& mc.getMouseY() < drawY + boxHeight) {
						graphics.drawBoxAlpha(drawX, drawY, boxWidth, boxHeight, i7, 160);
						if (mc.getMouseClick() == 1) {
							int itemID = mc.getInventoryItemID(inventorySlot);
							int amount = mc.getInventoryCount(itemID);
							if (itemID == 10 || EntityHandler.getItemDef(itemID).untradeable) {
								mc.showMessage(false, null, "This object cannot be added to auction", MessageType.GAME,
									0, null);
								return;
							}
							if (amount > 0) {
								int price = EntityHandler.getItemDef(itemID).getBasePrice();
								newAuctionInventoryIndex = inventorySlot;
								newAuctionItem = new AuctionItem(-2, itemID, amount, safePrice(amount, price), "", 0);

								myAuctions.show(textField_priceEach);
								myAuctions.show(textField_price);
								myAuctions.show(textField_amount);
								myAuctions.setText(textField_amount, amount + "");
								myAuctions.setText(textField_price, safePrice(amount, price) + "");
								myAuctions.setText(textField_priceEach, price + "");
								/*if(price * amount * 0.025 < 5) {
									setFee(5);
								} else {
									setFee((int) (price * amount) * 0.025);
								}*/
							}
						}
					}
					inventorySlot++;
				}
			}
		} else if (selectItemAdd == 0) {
			if (newAuctionItem == null) {
				drawButtonFancy(graphics, x + 16, y + 37 + 10, 100, 48, "+ Select item", selectItemAdd == 1, new ButtonHandler() {
					@Override
					void handle() {
						selectItemAdd = 1;
					}
				});
			}

			LinkedList<AuctionItem> filteredList = new LinkedList<>();
			for (AuctionItem item : auctionItems) {
				if (item.getSeller().equalsIgnoreCase(mc.getUsername())) {
					filteredList.add(item);
				}
			}

			int listX = x + 210;
			int listY = y + 85;

			graphics.drawBoxAlpha(listX - 72, listY - 47, 348, 20, 0x3E557C, 192);
			graphics.drawString("My Listings", listX - 68, listY - 34, 0xffffff, 1);

			graphics.drawBoxAlpha(listX - 72, listY - 26, 348, 15, 0x192638, 192);
			graphics.drawBoxBorder(listX - 73, 350, listY - 27, 17, 0x292D30);

			graphics.drawString("Item", listX - 68, listY - 14, 0xffffff, 1);
			graphics.drawString("Name / Sale Prices", listX - 18, listY - 14, 0xffffff, 1);
			graphics.drawString("Left", listX + 238, listY - 14, 0xffffff, 1);

			int listStartPoint = myAuctions.getScrollPosition(myAuctionScrollHandle);
			int listEndPoint = listStartPoint + 4;
			for (int i = -1; i < filteredList.size(); i++) {
				myAuctions.setListEntry(myAuctionScrollHandle, i + 1, "", 0, null, null);
				if (i < listStartPoint || i > listEndPoint)
					continue;
				AuctionItem ahItem = filteredList.get(i);
				if (mc.getMouseX() >= listX - 72 && mc.getMouseY() >= listY - 11 && mc.getMouseX() <= listX + 275 - 12
					&& mc.getMouseY() <= listY - 11 + boxHeight) {
					graphics.drawBoxAlpha(listX - 72, listY - 11, 348, boxHeight, 0x980000, 128);
					if (mc.getMouseClick() == 1) {
						selectedCancelAuction = i;
					}
				} else {
					if (selectedCancelAuction == i) {
						graphics.drawBoxAlpha(listX - 72, listY - 11, 348, boxHeight, 0xff0000, 128);
					} else {
						graphics.drawBoxAlpha(listX - 72, listY - 11, 348, boxHeight, 0x45454545, 128);
					}
				}
				graphics.drawBoxBorder(listX - 73, 350, listY - 11, boxHeight + 1, 0x343434);
				ItemDef def = EntityHandler.getItemDef(ahItem.getItemID());
				if (def == null) {
					continue;
				}
				int price = ahItem.getPrice();
				int priceEach = 0;
				if (price > 0 && ahItem.getAmount() > 0) {
					priceEach = price / ahItem.getAmount();
				}

				graphics.drawString(def.getName(), listX - 17, listY + boxHeight / 2 - 14, 0xffffff, 2);
				graphics.drawString("Buyout:", listX - 17, listY + boxHeight / 2 + 10 - 8, 0xc1b575, 0);
				graphics.drawString("Each:", listX + 90, listY + boxHeight / 2 + 10 - 8, 0xc1b575, 0);

				graphics.drawString(basicNumber(price) + " gp", listX + 21, listY + boxHeight / 2 + 10 - 8, 0xffffff, 0);

				graphics.drawString(basicNumber(priceEach) + " gp ea", listX + 118, listY + boxHeight / 2 + 10 - 8, 0xffffff, 0);
				graphics.drawString(getTime(ahItem) + "h", listX + 240, listY + boxHeight / 2 - 14, 0xffffff, 2);

				graphics.drawBoxAlpha(listX - 72, listY - 10, boxWidth + 1, boxHeight - 1, 0xfffffff, 128);

				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), listX - 72, listY - 10, 48,
					32, def.getPictureMask(), 0, def.getBlueMask(),false, 0, 1);

				graphics.drawString(String.valueOf(ahItem.getAmount()), listX - 72 + 1, listY - 10 + 11, 65280, 3);
				listY += boxHeight + 2;
			}

			if (selectedCancelAuction >= 0) {
				int cancelAuctionColor = 0x980000;

				if (mc.getMouseX() >= x + 285 - 29 && mc.getMouseY() >= y + 260 && mc.getMouseX() <= x + 385 - 17
					&& mc.getMouseY() <= y + 20 + 260) {
					cancelAuctionColor = 0x500000;
					if (mc.getMouseClick() == 1 && selectedCancelAuction < filteredList.size()) {
						sendCancelAuction(filteredList.get(selectedCancelAuction).getAuctionID());
					}
				}
				graphics.drawBoxAlpha(x + 255, y + 260, 114, 22, cancelAuctionColor, 192);
				graphics.drawBoxBorder(x + 255, 114, y + 260, 22, 0xC8C7BE);
				graphics.drawString("Cancel Auction", x + 270, y + 275, 0xffffff, 1);
			}
		}
		myAuctions.drawPanel();
	}

	private void sendCancelAuction(int auctionID) {
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(10);
		mc.packetHandler.getClientStream().bufferBits.putByte(2);
		mc.packetHandler.getClientStream().bufferBits.putInt(auctionID);
		mc.packetHandler.getClientStream().finishPacket();
		selectedCancelAuction = -1;
	}

	private void sendCreateAuction() {
		if (newAuctionItem != null) {
			if (newAuctionItem.getAmount() <= 0) {
				newAuctionItem.setAmount(1);
			}
			if (newAuctionItem.getPrice() <= 0) {
				newAuctionItem.setPrice(1);
			}
			mc.packetHandler.getClientStream().newPacket(199);
			mc.packetHandler.getClientStream().bufferBits.putByte(10);
			mc.packetHandler.getClientStream().bufferBits.putByte(1);
			mc.packetHandler.getClientStream().bufferBits.putInt(newAuctionItem.getItemID());
			mc.packetHandler.getClientStream().bufferBits.putInt(newAuctionItem.getAmount());
			mc.packetHandler.getClientStream().bufferBits.putInt(newAuctionItem.getPrice());
			mc.packetHandler.getClientStream().finishPacket();

			myAuctions.setText(textField_amount, "");
			myAuctions.setText(textField_price, "");
			myAuctions.setText(textField_priceEach, "");
			myAuctions.hide(textField_amount);
			myAuctions.hide(textField_price);
			myAuctions.hide(textField_priceEach);
			selectItemAdd = 0;
			newAuctionItem = null;
			newAuctionInventoryIndex = -1;
		}
	}

	private void drawButton(GraphicsController graphics, int x, int y, int width, int height, String text,
							boolean checked, ButtonHandler handler) {
		int allColor = COLOR_PANEL_SOFT;
		if (checked) {
			allColor = COLOR_SELECTED;
		}
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			if (!checked)
				allColor = COLOR_HOVER;
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		graphics.drawBoxAlpha(x, y, width, height, allColor, 192);
		graphics.drawBoxBorder(x, width, y, height, checked ? 0xd6c47f : 0x303840);
		graphics.drawString(text, x + (width / 2 - graphics.stringWidth(1, text) / 2), y + height / 2 + 5, 0xffffff, 1);
	}

	private void drawButtonFancy(GraphicsController graphics, int x, int y, int width, int height, String text,
								 boolean checked, ButtonHandler handler) {
		int allColor = 0x173047;
		if (checked) {
			allColor = COLOR_SELECTED;
		}
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			if (!checked)
				allColor = COLOR_HOVER;
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		graphics.drawBoxAlpha(x, y, width, height, allColor, 192);
		graphics.drawBoxBorder(x, width, y, height, checked ? 0xd6c47f : 0xBFA086);
		graphics.drawString(text, x + (width / 2 - graphics.stringWidth(1, text) / 2), y + height / 2 + 5, 0xffffff, 1);
	}

	private void drawTextHit(GraphicsController graphics, int x, int y, int width, int height, String text,
							 boolean checked, ButtonHandler handler) {
		int allColor = 0xffffff;
		if (checked) {
			allColor = 0x6b8e23;
		}
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			allColor = 16711680;
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		graphics.drawString(text, x + (width / 2 - graphics.stringWidth(1, text) / 2), y + height / 2 + 5, allColor, 1);
	}

	private void drawItemSprite(int itemID, int drawX, int drawY, int drawWidth, int drawHeight) {
		ItemDef def = EntityHandler.getItemDef(itemID);
		if (def == null) {
			return;
		}
		mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX, drawY, drawWidth, drawHeight,
			def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
	}

	private void setBrowseFilter(int filter) {
		selectedFilter = filter;
		selectedAuction = -1;
		resetPurchaseConfirm();
		auctionMenu.resetScrollIndex(auctionScrollHandle);
	}

	private void drawCategoryRail(GraphicsController graphics) {
		int railX = categoryRailX();
		int railY = categoryRailY();
		int railWidth = 86;
		int railHeight = 226;
		graphics.drawBoxAlpha(railX, railY, railWidth, railHeight, 0, 72);
		graphics.drawBoxBorder(railX, railWidth, railY, railHeight, 0x343434);
		graphics.drawBoxAlpha(railX + 1, railY + 1, railWidth - 2, 18, COLOR_SELECTED, 204);
		graphics.drawString("Browse", railX + 23, railY + 14, 0xffffff, 1);

		for (int i = 0; i < CATEGORY_LABELS.length; i++) {
			drawCategoryTile(graphics, i, categoryTileLeftX(i), categoryTileTopY(i));
		}
	}

	private void drawCategoryTile(GraphicsController graphics, final int filter, int tileX, int tileY) {
		boolean selected = selectedFilter == filter;
		boolean hover = inBounds(mc.getMouseX(), mc.getMouseY(), tileX, tileY, CATEGORY_TILE_WIDTH, CATEGORY_TILE_HEIGHT);
		int fill = selected ? COLOR_SELECTED : (hover ? COLOR_HOVER : COLOR_PANEL_SOFT);
		graphics.drawBoxAlpha(tileX, tileY, CATEGORY_TILE_WIDTH, CATEGORY_TILE_HEIGHT, fill, selected ? 218 : 176);
		graphics.drawBoxBorder(tileX, CATEGORY_TILE_WIDTH, tileY, CATEGORY_TILE_HEIGHT, selected ? 0xd6c47f : 0x303840);
		drawItemSprite(CATEGORY_ICON_IDS[filter], tileX + 3, tileY + 2, 29, 20);
		String label = CATEGORY_SHORT_LABELS[filter];
		graphics.drawString(label, tileX + (CATEGORY_TILE_WIDTH / 2 - graphics.stringWidth(0, label) / 2), tileY + 32,
			selected ? 0xffff99 : 0xffffff, 0);
		if (hover && mc.getMouseClick() == 1) {
			setBrowseFilter(filter);
			mc.setMouseClick(0);
		}
	}

	private void drawEmptyState(GraphicsController graphics, int boxX, int boxY, int boxWidth, int boxHeight,
								String title, String detail) {
		graphics.drawBoxAlpha(boxX, boxY, boxWidth, boxHeight, COLOR_PANEL_SOFT, 160);
		graphics.drawBoxBorder(boxX, boxWidth, boxY, boxHeight, 0x303840);
		drawItemSprite(10, boxX + boxWidth / 2 - 20, boxY + 26, 40, 28);
		graphics.drawString(title, boxX + (boxWidth / 2 - graphics.stringWidth(1, title) / 2), boxY + 78,
			0xffffff, 1);
		graphics.drawString(detail, boxX + (boxWidth / 2 - graphics.stringWidth(0, detail) / 2), boxY + 96,
			0xc1c8cf, 0);
	}

	private void drawStatChip(GraphicsController graphics, int chipX, int chipY, int chipWidth, String label,
							  String value) {
		graphics.drawBoxAlpha(chipX, chipY, chipWidth, 32, 0x0b1117, 188);
		graphics.drawBoxBorder(chipX, chipWidth, chipY, 32, 0x303840);
		graphics.drawString(label, chipX + 6, chipY + 12, COLOR_GOLD, 0);
		graphics.drawString(value, chipX + 6, chipY + 26, 0xffffff, 1);
	}

	private void drawMarketIntel(GraphicsController graphics) {
		auctionMenu.hide(auctionScrollHandle);
		auctionMenu.hide(textField_buyAmount);

		int paneY = y + 39;
		int paneHeight = 249;
		int hotX = x + 5;
		int recentX = x + 250;
		int paneWidth = 236;

		graphics.drawBoxAlpha(hotX, paneY, paneWidth, paneHeight, 0, 60);
		graphics.drawBoxBorder(hotX, paneWidth, paneY, paneHeight, 0x343434);
		graphics.drawBoxAlpha(hotX + 1, paneY + 1, paneWidth - 2, 18, 0x6b8e23, 192);
		graphics.drawString("Hot this week", hotX + 8, paneY + 14, 0xffffff, 1);

		graphics.drawBoxAlpha(recentX, paneY, paneWidth, paneHeight, 0, 60);
		graphics.drawBoxBorder(recentX, paneWidth, paneY, paneHeight, 0x343434);
		graphics.drawBoxAlpha(recentX + 1, paneY + 1, paneWidth - 2, 18, 0x6b8e23, 192);
		graphics.drawString("Recent sales", recentX + 8, paneY + 14, 0xffffff, 1);

		int hotLimit = Math.min(7, hotMarketItems.size());
		if (hotLimit == 0) {
			graphics.drawString("No sales yet.", hotX + 8, paneY + 45, 0xffffff, 1);
		}
		for (int i = 0; i < hotLimit; i++) {
			MarketHotItem hot = hotMarketItems.get(i);
			ItemDef def = EntityHandler.getItemDef(hot.getItemID());
			int rowY = paneY + 24 + (i * 31);
			boolean hover = inBounds(mc.getMouseX(), mc.getMouseY(), hotX + 4, rowY, paneWidth - 8, 29);
			graphics.drawBoxAlpha(hotX + 4, rowY, paneWidth - 8, 29, hover ? 0x263751 : 0x454545, 144);
			graphics.drawBoxBorder(hotX + 4, paneWidth - 8, rowY, 29, 0x343434);
			if (hover && mc.getMouseClick() == 1 && def != null) {
				activeInterface = 0;
				selectedAuction = -1;
				selectedFilter = 0;
				auctionMenu.setText(auctionSearchHandle, def.getName());
				auctionMenu.resetScrollIndex(auctionScrollHandle);
				resetPurchaseConfirm();
				mc.setMouseClick(0);
			}
			if (def != null) {
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), hotX + 6, rowY + 1, 32, 24,
					def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
				graphics.drawString(mc.ellipsize(def.getName(), 18), hotX + 42, rowY + 11, 0xffffff, 1);
			} else {
				graphics.drawString("Item " + hot.getItemID(), hotX + 42, rowY + 11, 0xffffff, 1);
			}
			graphics.drawString("Sold " + shortNumber(hot.getVolumeSold()) + " | avg " + priceText(hot.getAverageUnitPrice()),
				hotX + 42, rowY + 24, 0xffffff, 0);
			graphics.drawString(shortAge(hot.getLastSoldAt()), hotX + 178, rowY + 11, 0xffffff, 1);
		}

		int recentLimit = Math.min(7, recentMarketSales.size());
		if (recentLimit == 0) {
			graphics.drawString("No sales yet.", recentX + 8, paneY + 45, 0xffffff, 1);
		}
		for (int i = 0; i < recentLimit; i++) {
			MarketRecentSale sale = recentMarketSales.get(i);
			ItemDef def = EntityHandler.getItemDef(sale.getItemID());
			int rowY = paneY + 24 + (i * 31);
			graphics.drawBoxAlpha(recentX + 4, rowY, paneWidth - 8, 29, 0x454545, 144);
			graphics.drawBoxBorder(recentX + 4, paneWidth - 8, rowY, 29, 0x343434);
			if (def != null) {
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), recentX + 6, rowY + 1, 32, 24,
					def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
				graphics.drawString(mc.ellipsize(def.getName(), 18), recentX + 42, rowY + 11, 0xffffff, 1);
			} else {
				graphics.drawString("Item " + sale.getItemID(), recentX + 42, rowY + 11, 0xffffff, 1);
			}
			graphics.drawString("x" + shortNumber(sale.getAmount()) + " | " + priceText(sale.getUnitPrice()) + " ea",
				recentX + 42, rowY + 24, 0xffffff, 0);
			graphics.drawString(shortAge(sale.getSoldAt()), recentX + 178, rowY + 11, 0xffffff, 1);
		}
	}

	private void drawAuctionMenu(GraphicsController graphics) {
		Collections.sort(auctionItems, auctionComparator);
		auctionMenu.clearList(auctionScrollHandle);

		drawCategoryRail(graphics);

		graphics.drawBoxAlpha(x + 93, y + 37, width - 96, 58, COLOR_PANEL_SOFT, 174);
		graphics.drawBoxBorder(x + 93, width - 96, y + 37, 58, 0x303840);
		drawItemSprite(10, x + 101, y + 43, 28, 20);
		graphics.drawString(method74(mc.getInventoryCount(10)), x + 132, y + 56, 0xffffff, 1);

		String searchTerm = auctionMenu.getControlText(auctionSearchHandle);
		if (searchTerm.equalsIgnoreCase("Search")) {
			searchTerm = "";
		}
		drawButton(graphics, x + 188, y + 43, 106, 22, sortBy, false, new ButtonHandler() {
			@Override
			void handle() {
				orderingBy++;
				if (orderingBy >= 5)
					orderingBy = 0;

				if (orderingBy == 0) {
					sortBy = "Price Low";
				} else if (orderingBy == 1) {
					sortBy = "Price High";
				} else if (orderingBy == 2) {
					sortBy = "Name";
				} else if (orderingBy == 3) {
					sortBy = "Each Low";
				} else if (orderingBy == 4) {
					sortBy = "Each High";
				}
				selectedAuction = -1;
				resetPurchaseConfirm();
				Collections.sort(auctionItems, auctionComparator);
			}
		});
		graphics.drawBoxAlpha(x + 312, y + 39, 174, 18, 0x0b1117, 235);
		graphics.drawBoxBorder(x + 312, 174, y + 39, 18, 0x474843);
		if (searchTerm.length() == 0) {
			graphics.drawString("Search", x + 318, y + 52, 0x7f8a91, 0);
		}

		LinkedList<AuctionItem> filteredList = new LinkedList<>();
		for (AuctionItem item : auctionItems) {
			ItemDef def = EntityHandler.getItemDef(item.getItemID());

			String itemName = def.getName().toLowerCase();
			String exactItemName = def.getName().toLowerCase();
			String[] command = def.getCommand();

			String[] commandFilter = null;
			String[] nameFilter = null;
			String[] exactNameFilter = null;

			if (selectedFilter == 1 && ((24 & def.wearableID) == 0 || !def.isWieldable() || itemName.contains("shield"))) {
				continue;
			} else if (selectedFilter == 2 && ((24 & def.wearableID) != 0 || !def.isWieldable()) && !itemName.contains("shield")) {
				continue;
			} else if (selectedFilter == 3) { // Consumable
				commandFilter = new String[]{"drink", "eat"};
				nameFilter = new String[]{"raw"};
			} else if (selectedFilter == 4) { // Projectile
				nameFilter = new String[]{"-rune", "arrow", "bolt", "rune stone"};
			} else if (selectedFilter == 5) {  // Jewelry
				nameFilter = new String[]{"uncut", "sapphire", "emerald", "ruby", "diamond", "dragonstone", "crown"};
				exactNameFilter = new String[]{"opal", "jade", "amulet of accuracy", "gold amulet", "brass necklace",
					"gold necklace", "holy symbol of saradomin", "unblessed holy symbol", "ring of wealth",
					"ring of avarice", "ring of recoil", "ring of forging", "ring of splendor",
					"dwarven ring", "ring of life"};
			} else if (selectedFilter == 6) { // Ore and Bar
				nameFilter = new String[]{" ore", "coal", "bar", "clay"};
				exactNameFilter = new String[]{"gold", "silver", "silver certificate", "gold certificate"};
			} else if (selectedFilter == 7) { // Herblaw
				commandFilter = new String[]{"identify"};
				nameFilter = new String[]{"unfinished", "vial", "weed", "ground", "root", "wine of",
					"dragonfruit", "coconut"};
				exactNameFilter = new String[]{"guam Leaf", "marrentill", "tarromin", "harralander", "irit leaf",
					"avantoe", "kwuarm", "cadantine", "torstol", "pestle and mortar", "eye of newt", "jangerberries",
					"red spiders eggs", "white berries", "snape grass", "blue dragon scale", "fish oil",
					"unicorn horn", "grapes"};
			} else if (selectedFilter == 8) { // Rare
				nameFilter = new String[]{"halloween", "christmas", "bunny", "party", "easter", "scythe", "cracker",
					"santa's", "mask", "antlers"};
				exactNameFilter = new String[]{"disc of returning", "pumpkin", "present", "rubber chicken cap",
					"half full wine jug", "rune spear", "star cookie", "cane cookie", "glass of milk", "tree cookie"};
			}
			if (selectedFilter == 9) { // Misc
				nameFilter = new String[]{"log", "bones", "pickaxe", "dough", "bead", "key", "mould",
					"hatchet", "watering can"};
				exactNameFilter = new String[]{
					"fur", "leather", "wool", "bow string", "flax", "cow hide",
					"knife", "egg", "bucket", "milk", "flour", "skull", "grain",
					"needle", "thread", "holy", "water", "cadavaberries",
					"pot", "bowl", "pie dish", "jug", "grapes", "shears", "tinderbox",
					"chisel", "hammer", "ashes", "apron", "chef's hat", "skirt", "silk",
					"flier", "garlic", "redberries", "rope", "bad wine", "cape", "mixing bowl",
					"eye of newt", "lobster pot", "net", "fishing rod", "fly fishing rod", "harpoon",
					"fishing bait", "feather", "hand shovel", "herb clippers", "fruit picker",
					"redberry pie", "broken shield", "king black dragon scale", "chipped dragon scale",
					"dragon metal chain", "bronze axe", "iron axe", "steel axe", "black axe", "mithril axe",
					"adamantite axe", "rune axe" };
			}
			boolean skip = true;

			if (nameFilter != null) {
				for (String n : nameFilter) {
					if (itemName.contains(n)) {
						skip = false;
						break;
					}
				}
			}

			if (exactNameFilter != null) {
				for (String enf : exactNameFilter) {
					if (exactItemName.equalsIgnoreCase(enf)) {
						skip = false;
						break;
					}
				}
			}
			boolean breakit = false;
			if (commandFilter != null && skip) {
				for (String c : commandFilter) {
					if (command != null) {
						for (String comm : command) {
							if (comm.toLowerCase().contains(c)) {
								skip = false;
								breakit = true;
								break;
							}
							if (breakit)
								break;
						}
					}

				}
			}

			if (nameFilter != null || commandFilter != null || exactNameFilter != null) {
				if (skip) {
					continue;
				}
			}

			if (itemName.contains(searchTerm.toLowerCase())) {
				filteredList.add(item);
			}
		}
		if (selectedAuction == -1) {
			auctionMenu.clearList(auctionScrollHandle);
			auctionMenu.hide(textField_buyAmount);
			auctionMenu.show(auctionScrollHandle);
			int rowHeight = LIST_ROW_HEIGHT;
			int listX = listLeftX();
			int listY = listTopY();
			int listWidth = LIST_WIDTH;
			int listHeight = 186;
			graphics.drawBoxAlpha(listX, listY - 22, listWidth, 18, COLOR_SELECTED, 196);
			graphics.drawString(CATEGORY_LABELS[selectedFilter], listX + 8, listY - 8, 0xffffff, 1);
			graphics.drawString(filteredList.size() + " matches", listX + 88, listY - 8, COLOR_GOLD, 0);
			graphics.drawString("Each", listX + 252, listY - 8, 0xffffff, 0);
			graphics.drawString("Time", listX + 344, listY - 8, 0xffffff, 0);
			graphics.drawBoxAlpha(listX, listY - 4, listWidth, listHeight + 4, 0, 62);
			graphics.drawBoxBorder(listX, listWidth, listY - 4, listHeight + 4, 0x343434);

			if (filteredList.isEmpty()) {
				String detail = searchTerm.length() > 0 ? "Try a shorter search or another category." : "Check back soon or list one from My Auctions.";
				drawEmptyState(graphics, listX + 8, listY + 15, listWidth - 16, 132, "No listings found", detail);
			}
			int listStartPoint = auctionMenu.getScrollPosition(auctionScrollHandle);
			int listEndPoint = listStartPoint + 4;
			for (int i = -1; i < filteredList.size(); i++) {
				if (i >= 500) {
					break;
				}
				auctionMenu.setListEntry(auctionScrollHandle, i + 1, "", 0, null, null);

				if (i < listStartPoint || i > listEndPoint)
					continue;
				AuctionItem ahItem = filteredList.get(i);
				boolean hover = mc.getMouseX() >= listX + 3 && mc.getMouseY() >= listY
					&& mc.getMouseX() <= listX + listWidth - 8 && mc.getMouseY() <= listY + rowHeight - 2;
				if (hover) {
					graphics.drawBoxAlpha(listX + 3, listY, listWidth - 8, rowHeight - 2, COLOR_HOVER, 176);
					if (mc.getMouseClick() == 1) {
						selectedAuction = i;
						resetPurchaseConfirm();
						auctionMenu.setText(textField_buyAmount, "1");
						auctionMenu.setFocus(textField_buyAmount);
						mc.setMouseClick(0);
					}
				} else {
					if (selectedAuction == i) {
						graphics.drawBoxAlpha(listX + 3, listY, listWidth - 8, rowHeight - 2, COLOR_SELECTED, 176);
					} else {
						graphics.drawBoxAlpha(listX + 3, listY, listWidth - 8, rowHeight - 2, COLOR_PANEL_SOFT, 144);
					}
				}
				graphics.drawBoxBorder(listX + 3, listWidth - 8, listY, rowHeight - 2, 0x303840);
				ItemDef def = EntityHandler.getItemDef(ahItem.getItemID());
				int price = ahItem.getPrice();
				int priceEach = 0;
				if (price > 0 && ahItem.getAmount() > 0) {
					priceEach = price / ahItem.getAmount();
				}
				AuctionItemIntel intel = marketIntelByItemID.get(ahItem.getItemID());
				String secondary = "x" + shortNumber(ahItem.getAmount());
				if (intel != null && intel.getAverageUnitPrice() > 0) {
					secondary += " | avg " + priceText(intel.getAverageUnitPrice());
				}

					graphics.drawBoxAlpha(listX + 7, listY + 3, 38, 28, 0xffffff, 120);
				graphics.drawBoxBorder(listX + 7, 38, listY + 3, 28, 0);
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), listX + 8, listY + 3, 36,
					25, def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
				graphics.drawString(String.valueOf(ahItem.getAmount()), listX + 9, listY + 15, 65280, 1);

				graphics.drawString(mc.ellipsize(def.getName(), 22), listX + 52, listY + 14, 0xffffff, 1);
				graphics.drawString(secondary, listX + 52, listY + 28, 0xc1c8cf, 0);
				graphics.drawString(priceText(priceEach), listX + 252, listY + 15, 0xffffff, 1);
				graphics.drawString(basicNumber(price) + " all", listX + 252, listY + 29, COLOR_GOLD, 0);
				graphics.drawString(getTime(ahItem) + "h", listX + 344, listY + 21, 0xffffff, 1);
				listY += rowHeight + 1;
			}
		}

		if (selectedAuction != -1 && selectedAuction < filteredList.size()) {
			int selectX = x + 94;
			int selectY = y + 82;
			auctionMenu.hide(auctionScrollHandle);
			auctionMenu.show(textField_buyAmount);
			final AuctionItem ahItem = filteredList.get(selectedAuction);
			graphics.drawBoxAlpha(selectX, selectY - 20, 392, 18, COLOR_SELECTED, 196);
			graphics.drawBoxAlpha(selectX, selectY - 2, 392, 210, 0, 72);
			graphics.drawBoxBorder(selectX, 392, selectY - 20, 228, 0x343434);

			drawButton(graphics, selectX + 366, selectY - 21, 24, 18, "X", false, new ButtonHandler() {
				@Override
				void handle() {
					activeInterface = 0;
					selectedAuction = -1;
					resetPurchaseConfirm();
				}
			});

			ItemDef def = EntityHandler.getItemDef(ahItem.getItemID());
			int price = ahItem.getPrice();
			int priceEach = 0;
			if (price > 0 && ahItem.getAmount() > 0) {
				priceEach = price / ahItem.getAmount();
			}

			graphics.drawString(mc.ellipsize(def.getName(), 26), selectX + 8, selectY - 6, 0xffffff, 1);
				graphics.drawBoxAlpha(selectX + 8, selectY + 10, 74, 54, 0xffffff, 120);
			graphics.drawBoxBorder(selectX + 8, 74, selectY + 10, 54, 0);
			mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), selectX + 15, selectY + 17, 60, 40,
				def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
			graphics.drawString(String.valueOf(ahItem.getAmount()), selectX + 12, selectY + 35, 65280, 1);

			graphics.drawString(mc.ellipsize(def.getDescription(), 40), selectX + 94, selectY + 20, 0xc1c8cf, 0);
			graphics.drawString("Seller: " + ahItem.getSeller(), selectX + 94, selectY + 38, 0xffffff, 1);
			graphics.drawString(getTime(ahItem) + "h left", selectX + 94, selectY + 54, 0xffffff, 1);

			if (mc.getLocalPlayer().isMod()) {
				drawButton(graphics, selectX + 246, selectY + 42, 134, 22, "@red@[Staff] Delete", false, new ButtonHandler() {
					@Override
					void handle() {
						sendModCancelAuction(ahItem.getAuctionID());
					}
				});
			} else {
				drawButton(graphics, selectX + 246, selectY + 42, 134, 22, "@gre@Add Friend", false, new ButtonHandler() {
					@Override
					void handle() {
						mc.addFriend(ahItem.getSeller());
					}
				});
			}

			drawStatChip(graphics, selectX + 8, selectY + 76, 118, "Quantity", shortNumber(ahItem.getAmount()));
			drawStatChip(graphics, selectX + 136, selectY + 76, 118, "Each", priceText(priceEach));
			drawStatChip(graphics, selectX + 264, selectY + 76, 118, "Total", priceText(price));
			graphics.drawLineHoriz(selectX + 8, selectY + 119, 374, 0x303840);
			drawMarketSnapshot(graphics, ahItem, selectX + 8, selectY + 132);

			graphics.drawString("Amount", selectX + 8, selectY + 146, COLOR_GOLD, 0);
			graphics.drawBoxAlpha(selectX + 7, selectY + 149, 92, 18, 0x0b1117, 235);
			graphics.drawBoxBorder(selectX + 7, 92, selectY + 149, 18, 0x555555);

			String amountText = auctionMenu.getControlText(textField_buyAmount);

			if (amountText.length() > 0 && amountText.length() < 10) {
				int checkoutAmount = parseAmount(amountText, ahItem.getAmount());
				final int finalCheckoutAmount = checkoutAmount;
				final int checkoutTotal = priceEach * checkoutAmount;

				drawButton(graphics, selectX + 108, selectY + 149, 38, 18, "1", checkoutAmount == 1, new ButtonHandler() {
					@Override
					void handle() {
						setBuyAmount(1, ahItem.getAmount());
					}
				});
				drawButton(graphics, selectX + 150, selectY + 149, 38, 18, "+5", false, new ButtonHandler() {
					@Override
					void handle() {
						setBuyAmount(finalCheckoutAmount + 5, ahItem.getAmount());
					}
				});
				drawButton(graphics, selectX + 192, selectY + 149, 42, 18, "+10", false, new ButtonHandler() {
					@Override
					void handle() {
						setBuyAmount(finalCheckoutAmount + 10, ahItem.getAmount());
					}
				});
				drawButton(graphics, selectX + 238, selectY + 149, 48, 18, "All", checkoutAmount == ahItem.getAmount(), new ButtonHandler() {
					@Override
					void handle() {
						setBuyAmount(ahItem.getAmount(), ahItem.getAmount());
					}
				});

				if (checkoutAmount > 0 && checkoutAmount <= ahItem.getAmount()) {
					final boolean confirming = hasPurchaseConfirm(ahItem, checkoutAmount);
					graphics.drawString("Checkout: " + method74(checkoutTotal) + "gp", selectX + 8, selectY + 180, 0xffffff, 1);
					graphics.drawString(confirming ? "@yel@Click again to confirm." : "Review, then confirm.", selectX + 176, selectY + 180, 0xffffff, 0);
					drawButtonFancy(graphics, selectX + 8, selectY + 186, 374, 22, confirming ? "Confirm Purchase" : "Purchase Review", confirming, new ButtonHandler() {
						@Override
						void handle() {
							if (hasPurchaseConfirm(ahItem, finalCheckoutAmount)) {
								sendAuctionBuy(ahItem);
							} else {
								confirmAuctionID = ahItem.getAuctionID();
								confirmAmount = finalCheckoutAmount;
								confirmExpiresAt = System.currentTimeMillis() + 7000L;
							}
						}
					});
				}
			}
		} else {
			selectedAuction = -1;
		}
		auctionMenu.drawPanel();
	}

	private void drawMarketSnapshot(GraphicsController graphics, AuctionItem ahItem, int drawX, int drawY) {
		AuctionItemIntel intel = marketIntelByItemID.get(ahItem.getItemID());
		if (intel == null || !intel.hasData()) {
			graphics.drawString("Mkt: no 7d sales", drawX, drawY, 0xffffff, 1);
			return;
		}
		graphics.drawString("Mkt: avg " + priceText(intel.getAverageUnitPrice())
				+ " | last " + priceText(intel.getLastUnitPrice())
				+ " | sold " + shortNumber(intel.getVolumeSold())
				+ " | low " + priceText(intel.getActiveLowestEach()),
			drawX, drawY, 0xffffff, 1);
	}

	private String method74(int i) {
		String s = String.valueOf(i);
		for (int j = s.length() - 3; j > 0; j -= 3)
			s = s.substring(0, j) + "," + s.substring(j);

		if (s.length() > 8)
			s = "@gre@" + s.substring(0, s.length() - 8) + " million @whi@(" + s + ")";
		else if (s.length() > 4) {
			s = "@cya@" + s.substring(0, s.length() - 4) + "K @whi@(" + s + ")";
		}
		return s;
	}

	private String priceText(int price) {
		if (price <= 0) {
			return "--";
		}
		return basicNumber(price) + "gp";
	}

	private String shortNumber(int value) {
		if (value >= 1000000) {
			return String.format("%.1fM", value / 1000000D);
		}
		if (value >= 1000) {
			return String.format("%.1fK", value / 1000D);
		}
		return String.valueOf(value);
	}

	private String shortAge(int epochSeconds) {
		if (epochSeconds <= 0) {
			return "--";
		}
		long age = (System.currentTimeMillis() / 1000) - epochSeconds;
		if (age < 60) {
			return "now";
		}
		if (age < 3600) {
			return (age / 60) + "m";
		}
		if (age < 86400) {
			return (age / 3600) + "h";
		}
		return (age / 86400) + "d";
	}

	private String basicNumber(int priceEach) {
		if (priceEach >= 1000000) {
			double millions = priceEach / 1000000D;
			return "@gre@" + String.format("%.2f", millions) + "M";
		} else if (priceEach >= 1000) {
			double thousands = priceEach / 1000D;

			return String.format("%.2f", thousands) + "@whi@K";
		}
		return "" + priceEach;
	}

	private double getFee() {
		return fee;
	}

	private void setFee(double d) {
		this.fee = d;
	}

	private String getTime(AuctionItem ahItem) {
		int h = ahItem.getHoursLeft();
		String col = "";
		if (h <= 1)
			col = "@red@";
		else if (h <= 3)
			col = "@or3@";
		else if (h <= 6)
			col = "@or2@";
		else if (h <= 12)
			col = "@lre@";
		else if (h <= 16)
			col = "@or1@";
		else if (h <= 20)
			col = "@gr1@";
		else if (h <= 24)
			col = "@gre@";
		return col + "" + h;
	}

	private void sendModCancelAuction(int auctionID) {
		selectedAuction = -1;
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(10);
		mc.packetHandler.getClientStream().bufferBits.putByte(5);
		mc.packetHandler.getClientStream().bufferBits.putInt(auctionID);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private void sendAuctionBuy(AuctionItem ahItem) {
		int t = parseAmount(auctionMenu.getControlText(textField_buyAmount), ahItem.getAmount());
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(10);
		mc.packetHandler.getClientStream().bufferBits.putByte(0);
		mc.packetHandler.getClientStream().bufferBits.putInt(ahItem.getAuctionID());
		mc.packetHandler.getClientStream().bufferBits.putInt(t);
		mc.packetHandler.getClientStream().finishPacket();
		if (t >= ahItem.getAmount() || ahItem.getAmount() <= 1 || ahItem.getSeller().equals(mc.getLocalPlayer().displayName)) {
			selectedAuction = -1;
		}
		resetPurchaseConfirm();
	}

	public boolean keyDown(int key) {
		if (activeInterface == 0) {
			if (auctionMenu.focusOn(auctionSearchHandle) || auctionMenu.focusOn(textField_buyAmount)) {
				if (auctionMenu.focusOn(auctionSearchHandle)) {
					selectedAuction = -1;
				}
				if (auctionMenu.focusOn(textField_buyAmount)) {
					if (auctionMenu.getControlText(textField_buyAmount).length() == 0 && key == 48) {
						return true;
					}
					if (key >= 48 && key <= 57 || key == 8) {
						auctionMenu.keyPress(key);
						resetPurchaseConfirm();
					}
				} else
					auctionMenu.keyPress(key);
				return true;
			}
		} else if (activeInterface == 1) {
			if (myAuctions.focusOn(textField_amount) || myAuctions.focusOn(textField_price)
				|| myAuctions.focusOn(textField_priceEach)) {
				if (newAuctionItem != null) {
					if (key >= 48 && key <= 57 || key == 8) {
						myAuctions.keyPress(key);
					}
					String amountText = myAuctions.getControlText(textField_amount);
					String priceText = myAuctions.getControlText(textField_price);
					String priceEachText = myAuctions.getControlText(textField_priceEach);

					if (amountText.length() == 0) {
						return true;
					}
					if (priceText.length() == 0) {
						return true;
					}
					if (priceEachText.length() == 0) {
						return true;
					}

					int amount = Integer.parseInt(amountText);
					int price = Integer.parseInt(priceText);
					int priceEach = Integer.parseInt(priceEachText);

					if (amount > mc.getInventoryCount(newAuctionItem.getItemID())) {
						amount = mc.getInventoryCount(newAuctionItem.getItemID());
					}
					if (amount <= 0) {
						amount = 1;
					}
					if (price <= 0) {
						price = 1;
					}
					if (priceEach <= 0) {
						priceEach = 1;
					}

					if (myAuctions.focusOn(textField_amount)) {
						price = safePrice(amount, priceEach);
					} else if (myAuctions.focusOn(textField_price)) {
						priceEach = price / amount;
						if (priceEach <= 0) {
							priceEach = 1;
						}
						price = safePrice(amount, priceEach);
					} else if (myAuctions.focusOn(textField_priceEach)) {
						price = safePrice(amount, priceEach);
					}

					newAuctionItem.setAmount(amount);
					newAuctionItem.setPrice(price);
					/*if(price * 0.025 < 5) {
						setFee(5);
					} else {
						setFee(price * 0.025);
					}*/
					updateTextFields(amount, price, priceEach);
				}
				return true;
			}
		}
		return false;
	}

	private void updateTextFields(int amount, int price, int priceEach) {
		myAuctions.setText(textField_price, "" + price);
		myAuctions.setText(textField_priceEach, "" + priceEach);
		myAuctions.setText(textField_amount, "" + amount);
	}

	public void resetAuctionItems() {
		auctionItems.clear();
	}

	public void resetMarketIntel() {
		marketIntelByItemID.clear();
		hotMarketItems.clear();
		recentMarketSales.clear();
	}

	public void addAuction(int auctionID, int itemID, int amount, int price, String seller, int hoursLeft) {
		auctionItems.add(new AuctionItem(auctionID, itemID, amount, price, seller, hoursLeft));
	}

	public void addMarketSummary(int itemID, int activeLowestEach, int activeHighestEach, int activeAmount,
								 int lastUnitPrice, int averageUnitPrice, int volumeSold, int totalValue,
								 int lastSoldAt) {
		marketIntelByItemID.put(itemID, new AuctionItemIntel(itemID, activeLowestEach, activeHighestEach,
			activeAmount, lastUnitPrice, averageUnitPrice, volumeSold, totalValue, lastSoldAt));
	}

	public void addHotMarketItem(int itemID, int volumeSold, int totalValue, int averageUnitPrice, int lastSoldAt) {
		hotMarketItems.add(new MarketHotItem(itemID, volumeSold, totalValue, averageUnitPrice, lastSoldAt));
	}

	public void addRecentMarketSale(int itemID, int amount, int unitPrice, int totalPrice, int soldAt) {
		recentMarketSales.add(new MarketRecentSale(itemID, amount, unitPrice, totalPrice, soldAt));
	}

	private void resetAllVariables() {
		auctionMenu.clearList(auctionSearchHandle);
		auctionMenu.resetScrollIndex(auctionScrollHandle);
		myAuctions.resetScrollIndex(myAuctionScrollHandle);
		auctionMenu.setText(auctionSearchHandle, "");
		myAuctions.setText(textField_amount, "");
		myAuctions.setText(textField_priceEach, "");
		myAuctions.setText(textField_price, "");
		selectItemAdd = 0;
		newAuctionItem = null;
		newAuctionInventoryIndex = -1;
		selectedCancelAuction = -1;
		selectedAuction = -1;
		activeInterface = 0;
		selectedFilter = 0;
		orderingBy = 0;
		sortBy = "Price Low";
		resetMarketIntel();
		resetPurchaseConfirm();
		auctionMenu.setFocus(-1);
		myAuctions.setFocus(-1);
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

}

abstract class ButtonHandler {
	abstract void handle();
}

class AuctionItem {

	private int auctionID, price;
	private String seller;
	private int hoursLeft;
	private final Item item;

	AuctionItem(int auctionID, int itemID, int amount, int price, String seller2, int hoursLeft) {
		this.item = new Item();
		this.item.setItemDef(itemID);
		this.item.setAmount(amount);
		this.auctionID = auctionID;
		this.price = price;
		this.seller = seller2;
		this.hoursLeft = hoursLeft;
	}

	int getAuctionID() {
		return auctionID;
	}

	public void setAuctionID(int auctionID) {
		this.auctionID = auctionID;
	}

	public int getItemID() {
		return this.item.getItemDef().id;
	}

	public void setItemID(int itemID) {
		this.item.setItemDef(itemID);
	}

	public int getAmount() {
		return this.item.getAmount();
	}

	public void setAmount(int amount) {
		this.item.setAmount(amount);
	}

	int getPrice() {
		return price;
	}

	void setPrice(int price) {
		this.price = price;
	}

	public String getSeller() {
		return seller;
	}

	public void setSeller(String seller) {
		this.seller = seller;
	}

	int getHoursLeft() {
		return hoursLeft;
	}

	public void setHoursLeft(int hoursLeft) {
		this.hoursLeft = hoursLeft;
	}

	public boolean getNoted() {
		return this.item.getNoted();
	}

	public void setNoted(boolean noted) {
		this.item.setNoted(noted);
	}
}

class AuctionItemIntel {
	private final int itemID;
	private final int activeLowestEach;
	private final int activeHighestEach;
	private final int activeAmount;
	private final int lastUnitPrice;
	private final int averageUnitPrice;
	private final int volumeSold;
	private final int totalValue;
	private final int lastSoldAt;

	AuctionItemIntel(int itemID, int activeLowestEach, int activeHighestEach, int activeAmount, int lastUnitPrice,
					 int averageUnitPrice, int volumeSold, int totalValue, int lastSoldAt) {
		this.itemID = itemID;
		this.activeLowestEach = activeLowestEach;
		this.activeHighestEach = activeHighestEach;
		this.activeAmount = activeAmount;
		this.lastUnitPrice = lastUnitPrice;
		this.averageUnitPrice = averageUnitPrice;
		this.volumeSold = volumeSold;
		this.totalValue = totalValue;
		this.lastSoldAt = lastSoldAt;
	}

	boolean hasData() {
		return activeLowestEach > 0 || activeHighestEach > 0 || activeAmount > 0
			|| lastUnitPrice > 0 || averageUnitPrice > 0 || volumeSold > 0 || totalValue > 0 || lastSoldAt > 0;
	}

	int getItemID() {
		return itemID;
	}

	int getActiveLowestEach() {
		return activeLowestEach;
	}

	int getActiveHighestEach() {
		return activeHighestEach;
	}

	int getActiveAmount() {
		return activeAmount;
	}

	int getLastUnitPrice() {
		return lastUnitPrice;
	}

	int getAverageUnitPrice() {
		return averageUnitPrice;
	}

	int getVolumeSold() {
		return volumeSold;
	}

	int getTotalValue() {
		return totalValue;
	}

	int getLastSoldAt() {
		return lastSoldAt;
	}
}

class MarketHotItem {
	private final int itemID;
	private final int volumeSold;
	private final int totalValue;
	private final int averageUnitPrice;
	private final int lastSoldAt;

	MarketHotItem(int itemID, int volumeSold, int totalValue, int averageUnitPrice, int lastSoldAt) {
		this.itemID = itemID;
		this.volumeSold = volumeSold;
		this.totalValue = totalValue;
		this.averageUnitPrice = averageUnitPrice;
		this.lastSoldAt = lastSoldAt;
	}

	int getItemID() {
		return itemID;
	}

	int getVolumeSold() {
		return volumeSold;
	}

	int getTotalValue() {
		return totalValue;
	}

	int getAverageUnitPrice() {
		return averageUnitPrice;
	}

	int getLastSoldAt() {
		return lastSoldAt;
	}
}

class MarketRecentSale {
	private final int itemID;
	private final int amount;
	private final int unitPrice;
	private final int totalPrice;
	private final int soldAt;

	MarketRecentSale(int itemID, int amount, int unitPrice, int totalPrice, int soldAt) {
		this.itemID = itemID;
		this.amount = amount;
		this.unitPrice = unitPrice;
		this.totalPrice = totalPrice;
		this.soldAt = soldAt;
	}

	int getItemID() {
		return itemID;
	}

	int getAmount() {
		return amount;
	}

	int getUnitPrice() {
		return unitPrice;
	}

	int getTotalPrice() {
		return totalPrice;
	}

	int getSoldAt() {
		return soldAt;
	}
}
