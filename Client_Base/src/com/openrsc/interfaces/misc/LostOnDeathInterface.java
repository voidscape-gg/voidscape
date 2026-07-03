package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.Config;
import orsc.graphics.gui.Panel;
import orsc.mudclient;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;


public final class LostOnDeathInterface {
	public Panel lostOnDeathPanel;
	int itemSelected = -1, rightClickMenuX = 0, rightClickMenuY = 0;
	int width = 509;
	int height = 331;
	// Prayer index for Protect Items — must match server Prayers.PROTECT_ITEMS (8) and the
	// client prayer list; mc.checkPrayerOn(8) is server-synced via opcode 206 SET_PRAYERS.
	private static final int PROTECT_ITEMS_PRAYER = 8;
	private ArrayList<OnDeathItem> onDeathItems;
	private boolean visible;
	private mudclient mc;
	private int panelColour, textColour, bordColour;
	private int x, y;

	public LostOnDeathInterface(mudclient mc) {
		this.mc = mc;

		lostOnDeathPanel = new Panel(mc.getSurface(), 15);

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;

		onDeathItems = new ArrayList<OnDeathItem>();
	}

	public void reposition() {
		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2;
	}

	public void onRender() {
		reposition();

		panelColour = 0x989898;
		textColour = 0xFFFFFF;
		bordColour = 0x000000;

		lostOnDeathPanel.handleMouse(mc.getMouseX(), mc.getMouseY(), mc.getMouseButtonDown(), mc.getLastMouseDown());

		// Draws the background
		mc.getSurface().drawBoxAlpha(x, y, width, height, panelColour, 160);
		mc.getSurface().drawBoxBorder(x, width, y, height, bordColour);

		// Draws the title
		drawStringCentered("Items on Death", x, y + 28, 5, textColour);

		this.drawButton(x + width - 35, y + 5, 30, 30, "X", 5, false, new ButtonHandler() {
			@Override
			void handle() {
				setVisible(false);
			}
		});

		mc.getSurface().drawLineHoriz(x + 1, y + 40, width - 1, 0);
		mc.getSurface().drawLineVert(x + 145, y + 40, 2, height - 40);
		mc.getSurface().drawLineHoriz(x + 1, y + 88, width - 1, 0);

		drawString("Items kept on death", x + 5, y + 70, 3, textColour);
		drawString("Items lost on death", x + 5, y + 115, 3, textColour);

		drawItemsLost();
	}

	public void drawItemsLost() {
		reposition();

		int curX = x + 160, curY = y + 48;
		int movedAtFlag = -1;

		for (int i = 0; i < onDeathItems.size(); i++) {
			if (i >= 100) {
				break;
			}

			OnDeathItem curItem = onDeathItems.get(i);
			ItemDef def = EntityHandler.getItemDef(curItem.getItemID());

			if (!curItem.getLost() && movedAtFlag < 0) {
				curX = x + 160;
				curY += 48;
				movedAtFlag = i;
			}

			if (curItem.getNoted()) {
				if (Config.S_WANT_CERT_AS_NOTES) {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.noteDef),
						curX, curY, 48, 32, EntityHandler.noteDef.getPictureMask(), 0,
						EntityHandler.noteDef.getBlueMask(), false, 0, 1);
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(def),
						curX + 7, curY + 8, 29, 19, def.getPictureMask(), 0,
						def.getBlueMask(),false,
						0, 1);
				} else {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.certificateDef),
						curX, curY, 48, 32, EntityHandler.certificateDef.getPictureMask(), 0,
						EntityHandler.certificateDef.getBlueMask(), false, 0, 1);
				}
			} else {
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def),
					curX, curY, 48, 32, def.getPictureMask(), 0,
					def.getBlueMask(), false, 0, 1);
			}


			if (def.isStackable()) {
				drawString(mudclient.formatStackAmount((int) curItem.getStackCount()),
					curX + 1, curY + 10, 1, '\uffff');
			}

			drawString("Amount lost on death:", x + 5, y + 200, 3, textColour);
			drawString(this.getLossTotal(), x + 5, y + 220, 3, textColour);

			if (((i - movedAtFlag + 1) % 6) == 0) {
				curX = x + 160;
				curY += 48;
			} else {
				curX += 58;
			}
		}

		lostOnDeathPanel.drawPanel();
	}

	private void populateOnDeathItems() {
		int[] invyItems = new int[mc.getInventory().length];
		for (int i = 0; i < invyItems.length; ++ i) {
			invyItems[i] = mc.getInventoryItemID(i);
		}
		Object[] equipmentItems = mc.getEquipmentItems();
		if (Config.S_WANT_EQUIPMENT_TAB && equipmentItems != null) {
			int[] temp = invyItems;
			invyItems = new int[invyItems.length + ((int[])equipmentItems[0]).length];
			System.arraycopy(temp, 0, invyItems, 0, temp.length);
			System.arraycopy(((int[])equipmentItems[0]), 0, invyItems, temp.length, ((int[])equipmentItems[0]).length);
		}
		for (int i = 0; i < invyItems.length; i++) {
			if (invyItems[i] > 0) {
				ItemDef def = EntityHandler.getItemDef(invyItems[i]);
				int stackCount = 1;
				boolean noted = false;
				if (Config.S_WANT_EQUIPMENT_TAB && i >= mc.getInventory().length) {
					stackCount = ((int[]) equipmentItems[1])[i - mc.getInventory().length];
				}
				else {
					stackCount = mc.getInventoryItemAmount(i);
					noted = mc.getInventoryItem(i).getNoted();
				}
				onDeathItems.add(new OnDeathItem(invyItems[i], def.getBasePrice(), stackCount, false, noted));
			}
		}

		// Stackable and noted items are always lost in full on death (the server keys
		// them -1 in Inventory.dropOnDeath and never keeps one), so rank them below every
		// keepable item rather than by their per-item price.
		Collections.sort(onDeathItems, new Comparator<OnDeathItem>() {
			@Override
			public int compare(OnDeathItem obj1, OnDeathItem obj2) {
				long p1 = isAlwaysLost(obj1) ? -1L : obj1.getPrice();
				long p2 = isAlwaysLost(obj2) ? -1L : obj2.getPrice();
				return Long.compare(p2, p1);
			}
		});

		// Keep the 3 most valuable items (0 if skulled), plus 1 more while the Protect
		// Items prayer is active — matches server Inventory.dropOnDeath(). Note that in
		// this interface lost==true actually marks an item as KEPT (see getLossTotal /
		// drawItemsLost), so the keep loop sets lost on the top items.
		int keepXItems = 0;
		if ((mc.getLocalPlayer().skullVisible & 0x03) == 0) {
			keepXItems += 3;
		}
		if (mc.checkPrayerOn(PROTECT_ITEMS_PRAYER)) {
			keepXItems += 1;
		}

		for (int i = 0; i < keepXItems; i++) {
			if (i >= onDeathItems.size()) {
				break;
			}
			// Stackable/noted items sort last and are never kept; once we reach one, no
			// later item can be kept either, so stop.
			if (isAlwaysLost(onDeathItems.get(i))) {
				break;
			}
			onDeathItems.get(i).setLost(true);
		}
	}

	/** True if the item is always fully lost on death (stackable or noted), matching the
	 *  server's Inventory.dropOnDeath rule. */
	private boolean isAlwaysLost(OnDeathItem item) {
		return item.getNoted() || EntityHandler.getItemDef(item.getItemID()).isStackable();
	}

	private String getLossTotal() {
		long totalLost = 0;
		for (int i = 0; i < onDeathItems.size(); i++) {
			if (!onDeathItems.get(i).getLost()) {
				totalLost += onDeathItems.get(i).getPrice() * onDeathItems.get(i).getStackCount();
			}
		}
		totalLost = totalLost < 0 ? 0 : totalLost;
		return NumberFormat.getNumberInstance(Locale.US).format(totalLost);
	}

	public void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawString(str, x, y, color, font);
	}

	public void drawStringCentered(String str, int x, int y, int font, int color) {
		int stringWid = mc.getSurface().stringWidth(font, str);
		mc.getSurface().drawString(str, x + (width / 2) - (stringWid / 2) - 2, y, color, font);
	}

	private void drawButton(int x, int y, int width, int height, String text, int font, boolean checked, ButtonHandler handler) {
		int bgBtnColour = 0x333333; // grey
		if (checked) {
			bgBtnColour = 16711680; // red
		}
		if (mc.getMouseX() >= x && mc.getMouseY() >= y && mc.getMouseX() <= x + width && mc.getMouseY() <= y + height) {
			if (!checked)
				bgBtnColour = 16711680; // blue
			if (mc.getMouseClick() == 1) {
				handler.handle();
				mc.setMouseClick(0);
			}
		}
		mc.getSurface().drawBoxAlpha(x, y, width, height, bgBtnColour, 192);
		mc.getSurface().drawBoxBorder(x, width, y, height, 0x242424);
		mc.getSurface().drawString(text, x + (width / 2) - (mc.getSurface().stringWidth(font, text) / 2) - 1, y + height / 2 + 5, textColour, font);
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		if (visible) {
			onDeathItems.clear();
			populateOnDeathItems();
		}
	}
}

class OnDeathItem {

	private int itemID;
	private boolean lost;
	private long price, stackCount;
	private boolean noted;

	public OnDeathItem(int itemID, long price, long stackCount, boolean lost, boolean noted) {
		this.itemID = itemID;
		this.price = price;
		this.stackCount = stackCount;
		this.lost = lost;
		this.noted = noted;
	}

	public int getItemID() {
		return itemID;
	}

	public long getPrice() {
		return price;
	}

	public long getStackCount() {
		return stackCount;
	}

	public void setStackCount(long stackCount) {
		this.stackCount = stackCount;
	}

	public boolean getLost() {
		return lost;
	}

	public void setLost(boolean lost) {
		this.lost = lost;
	}

	public boolean getNoted() {
		return noted;
	}

	public void setNoted(boolean noted) {
		this.noted = noted;
	}
}
