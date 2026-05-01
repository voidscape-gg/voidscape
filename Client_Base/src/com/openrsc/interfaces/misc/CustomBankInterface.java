package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import orsc.Config;
import orsc.enumerations.InputXAction;
import orsc.graphics.gui.InputXPrompt;
import orsc.mudclient;
import orsc.util.BankUtil;

import java.util.ArrayList;
import java.util.Locale;

import static orsc.Config.*;
import static orsc.osConfig.C_MENU_SIZE;

public final class CustomBankInterface extends BankInterface {
	private static int fontSize = Config.isAndroid() ? C_MENU_SIZE : 1;
	private static int fontSizeHeight;
	private static final int BANK_COLUMNS = 10;
	private static final int BANK_ROWS = 4;
	private static final int BANK_PAGE_SIZE = BANK_COLUMNS * BANK_ROWS;
	private static final int BANK_SLOT_WIDTH = 49;
	private static final int BANK_SLOT_HEIGHT = 34;
	private static final int BANK_TAB_LIMIT = 6;
	private static final int PANEL_ACTIVE = 0x7E1F1C;
	private static final int SLOT_HOVER = 0xE2D4A0;
	private static final int PANEL_DARK = 0x23211D;
	private static final int PANEL_SECTION = 0x26231F;
	private static final int BUTTON_IDLE = 0x4A4840;
	private static final int BUTTON_HOVER = 0x6D6251;
	private static final int BUTTON_ACTIVE = 0x7E1F1C;
	private static final int BUTTON_BORDER = 0x2D2C24;
	private static final int BUTTON_INNER = 0x706452;
	private static final int BUTTON_ACCENT = 0x00A9B8;
	private final int presetCount = 3;
	public Preset[] presets = new Preset[presetCount];
	public int selectedInventorySlot = -1;
	public int bankSearch;
	public int bankScroll;
	public int lastXAmount = 0;
	private int hotkey = -1;
	private boolean saveXAmount = false;
	private boolean rightClickMenu;
	private int organizeMode = 0;
	private boolean arrangeMenuOpen = false;
	private int rightClickMenuX;
	private int rightClickMenuY;
	private int draggingInventoryID = -1;
	private int draggingBankSlot = -1;
	private boolean swapNoteMode;
	private boolean swapCertMode;
	private int pendingSavePresetSlot = -1; // -1 = no pending save, otherwise slot index awaiting confirm
	private int pendingClearPresetSlot = -1;
	private int loadoutActionSlot = -1;
	private int x, y;
	private long totalWealth;

	public CustomBankInterface(mudclient mc) {
		super(mc);
		if (Config.S_WANT_CUSTOM_BANKS) {
			width = 509;
			height = 331;
			x = (mc.getGameWidth() - width) / 2;
			y = (mc.getGameHeight() - height) / 2;
			bankScroll = bank.addScrollingList(x + 4, y + 21, width - 5, 172, 500, 7, true);
			bankSearch = bank.addLeftTextEntry(x + 375 + 6, y + 44, 110, 18, 0, 15, false, true);
		}
	}

	@Override
	public boolean onRender() {
		if (!Config.S_WANT_CUSTOM_BANKS) return super.onRender();

		x = (mc.getGameWidth() - width) / 2;
		y = (mc.getGameHeight() - height) / 2 - 3;
		bank.reposition(bankSearch, searchFieldX(), searchFieldY(), searchFieldWidth(), searchFieldHeight());
		bank.reposition(bankScroll, x + 4, y + 57, width - 5, 137);
		fontSizeHeight = mc.getSurface().fontHeight(fontSize);

		if (pendingSavePresetSlot != -1) {
			renderSaveConfirm(pendingSavePresetSlot);
			return true;
		}
		if (pendingClearPresetSlot != -1) {
			renderClearConfirm(pendingClearPresetSlot);
			return true;
		}

		if (mc.controlPressed) {
			switch (hotkey) {
				case (int)'1':
					loadPreset(0);
					break;
				case (int)'2':
					loadPreset(1);
					break;
				case (int)'3':
					loadPreset(2);
					break;
				case 4:
					sendDepositAllInventory();
					break;
				case -1:
				default:
					break;
			}
		}

		if (hotkey == 27) {
			if (loadoutActionSlot != -1 || arrangeMenuOpen) {
				loadoutActionSlot = -1;
				arrangeMenuOpen = false;
			} else {
				bankClose();
			}
		}

		hotkey = -1;

		if (anyMouseClick() && !rightClickMenu && loadoutActionSlot == -1
			&& !isInside(x, y, width, height)) {
			resetVar();
			bankClose();
			return true;
		}

		mc.getSurface().drawBox(x, y, width, 21, PANEL_DARK);
		mc.getSurface().drawBoxAlpha(x, y + 21, width, 309, 0x8C8A80, 118);
		mc.getSurface().drawBoxBorder(x, width, y, 331, 0x000000);
		mc.getSurface().drawLineHoriz(x + 1, y + 21, width - 2, BUTTON_INNER);

		drawString("Bank", x + 7, y + 15, 1, 0xFFFFFF);

		int closeButtonX = x + width - 20;
		int closeButtonY = y + 3;
		if (buttonClicked(closeButtonX, closeButtonY, 16, 15)) {
			resetVar();
			bankClose();
			consumeMouse();
		}
		drawButton(closeButtonX, closeButtonY, 16, 15, "x", false, true);

		int bankPages = bankItems.isEmpty() ? 0 :
			Math.min(BANK_TAB_LIMIT, Math.max(1, (bankItems.size() + BANK_PAGE_SIZE - 1) / BANK_PAGE_SIZE));
		if (mc.bankPage > bankPages) {
			mc.bankPage = bankPages;
		}

		int tabX = x + 6;
		int tabY = y + 27;
		int tabCount = bankPages + 1;
		int tabGap = 2;
		int maxTabsWidth = loadoutButtonX() - tabX - 8;
		int tabWidth = Math.max(36, Math.min(52, (maxTabsWidth - (tabCount - 1) * tabGap) / tabCount));
		int tabHeight = 19;
		for (int tabs = 0; tabs < tabCount; tabs++) {
			int drawX = tabX + tabs * (tabWidth + tabGap);
			String label = tabs == 0 ? "All" : (tabWidth < 46 ? "T" + tabs : "Tab " + tabs);
			boolean active = tabs == mc.bankPage;
			if (buttonClicked(drawX, tabY, tabWidth, tabHeight)) {
				if (!rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
					bank.setText(this.bankSearch, "");
					mc.bankPage = tabs;
					bank.resetListToIndex(bankScroll, 0);
					consumeMouse();
				}
			}
			drawButton(drawX, tabY, tabWidth, tabHeight, label, active, true);
		}

		if (S_WANT_BANK_PRESETS) {
			if (buttonClicked(loadoutButtonX(), loadoutButtonY(), loadoutButtonWidth(), loadoutButtonHeight())) {
				loadoutActionSlot = loadoutActionSlot == -1 ? 0 : -1;
				arrangeMenuOpen = false;
				consumeMouse();
			}
			drawButton(loadoutButtonX(), loadoutButtonY(), loadoutButtonWidth(), loadoutButtonHeight(), "Loadouts", loadoutActionSlot != -1, true);
		}

		mc.getSurface().drawBoxAlpha(searchFieldX(), searchFieldY(), searchFieldWidth(), searchFieldHeight(), 0x181818, 245);
		mc.getSurface().drawBoxBorder(searchFieldX(), searchFieldWidth(), searchFieldY(), searchFieldHeight(), bank.focusOn(bankSearch) ? 0xF89922 : 0x474843);

		String rawSearchItem = bank.getControlText(bankSearch);
		if (rawSearchItem == null) {
			rawSearchItem = "";
		}
		if (!rawSearchItem.isEmpty() && mc.getMouseClick() != 0
			&& isInside(searchClearX(), searchFieldY() + 1, 12, searchFieldHeight() - 2)) {
			bank.setText(bankSearch, "");
			bank.setFocus(-1);
			rawSearchItem = "";
			mc.setMouseClick(0);
			mc.mouseButtonClick = 0;
		}

		String searchItem = rawSearchItem.trim().toLowerCase(Locale.ROOT);
		if (!searchItem.isEmpty() && mc.bankPage != 0) {
			mc.bankPage = 0;
		}
		ArrayList<BankItem> searchList = new ArrayList<BankItem>();
		for (BankItem item : bankItems) {
			if (item == null || item.getItem() == null) {
				continue;
			}
			ItemDef def = item.getItem().getItemDef();
			if (searchItem.length() > 0) {
				if (def != null && def.getName() != null
					&& def.getName().toLowerCase(Locale.ROOT).contains(searchItem)) {
					searchList.add(item);
				}
			} else {
				searchList.add(item);
			}
		}
		int bankCount = 0;
		int bankSlotStart = (mc.bankPage - 1) * BANK_PAGE_SIZE;

		if (mc.bankPage == 0) {
			bank.clearList(bankScroll);
			bank.show(bankScroll);
			bankCount = (int) ((searchList.size() - 1) / (double)BANK_COLUMNS);
			for (int i = 0; i < bankCount + 1; i++) {
				bank.setListEntry(bankScroll, i, "", 0, (String) null, (String) null);
			}
			bankSlotStart = bank.getScrollPosition(bankScroll) * BANK_COLUMNS;
			if (bank.controlListCurrentSize[bankScroll] > 0
				&& (int)(bankSlotStart / (double)BANK_COLUMNS) > (bank.controlListCurrentSize[bankScroll] - 4)) {
				bank.resetListToIndex(bankScroll, (int)(bankSlotStart / (double)BANK_COLUMNS) - 1);
			}
		} else {
			bank.hide(bankScroll);
		}

		String bankHeaderText = "Slots " + bankItems.size() + "/" + mc.bankItemsMax;
		if (!searchItem.isEmpty()) {
			bankHeaderText = searchList.size() + " found - slots " + bankItems.size() + "/" + mc.bankItemsMax;
		}

		int boxColour = 0xd0d0d0;
		int gridY = y + 57;
		for (int verticalSlots = 0; verticalSlots < BANK_ROWS; verticalSlots++) {
			for (int horizonalSlots = 0; horizonalSlots < BANK_COLUMNS; horizonalSlots++) {
				BankItem bankItem = null;
				ItemDef bankDef = null;
				if (bankSlotStart >= 0 && bankSlotStart < searchList.size()) {
					bankItem = searchList.get(bankSlotStart);
				}
				if (bankItem != null)
					bankDef = bankItem.getItem().getItemDef();

				int drawX = x + 6 + horizonalSlots * BANK_SLOT_WIDTH;
				int drawY = gridY + verticalSlots * BANK_SLOT_HEIGHT;
				boolean slotHovered = isInside(drawX, drawY, BANK_SLOT_WIDTH, BANK_SLOT_HEIGHT);
				int slotColour = boxColour;
				if (bankItem != null && selectedBankSlot == bankItem.bankID) {
					slotColour = PANEL_ACTIVE;
				} else if (bankItem != null && slotHovered && !rightClickMenu && loadoutActionSlot == -1) {
					slotColour = SLOT_HOVER;
				}

				mc.getSurface().drawBoxAlpha(drawX, drawY, BANK_SLOT_WIDTH, BANK_SLOT_HEIGHT, slotColour, 160);
				mc.getSurface().drawBoxBorder(drawX, BANK_SLOT_WIDTH + 1, drawY, BANK_SLOT_HEIGHT + 1, 0);
				if (bankItem != null) {
					if (draggingBankSlot >= 0 && draggingBankSlot < bankItems.size()
						&& bank.getControlText(bankSearch).isEmpty()) {
						drawItemSprite(bankItems.get(draggingBankSlot).getItem(), mc.getMouseX(), mc.getMouseY(), true);
						drawString(mudclient.formatStackAmount(bankItems.get(draggingBankSlot).getItem().getAmount()), mc.getMouseX(), mc.getMouseY(), 1, 65280);
					}

					if (bankItem.getItem().getCatalogID() != -1 && bankDef != null) {
						if (draggingBankSlot != bankItem.bankID) {
							drawItemSprite(bankItem.getItem(), drawX, drawY, false);
							drawString(slotHovered ? "" + bankItem.getItem().getAmount() : mudclient.formatStackAmount(bankItem.getItem().getAmount()),
								drawX + 1, drawY + 10, 1, 65280);
						}
					}

					if (slotHovered && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen && mc.inputX_Action == InputXAction.ACT_0) {
						if (organizeMode > 0 && !rightClickMenu && bank.getControlText(bankSearch).isEmpty()) {
							if (mc.getMouseButtonDownTime() > 0 && mc.getMouseButtonDown() == 1) {
								if (mc.getMouseButtonDownTime() < 2 && bankItem.getItem().getCatalogID() != -1) {
									draggingBankSlot = bankItem.bankID;
								}
							} else if (draggingBankSlot > -1 && bankItem.getItem().getCatalogID() != -1) {
								sendItemSwap(draggingBankSlot, bankItem.bankID);
								draggingBankSlot = -1;
							}
						} else if (mc.getMouseClick() == 1) {
							selectedBankSlot = bankItem.bankID;
							sendWithdraw(1);
						}
					}

					if (slotHovered && bankItem.getItem().getCatalogID() != -1 && loadoutActionSlot == -1 && !arrangeMenuOpen && mc.inputX_Action == InputXAction.ACT_0) {
						if (mc.getMouseClick() == 2 || mc.mouseButtonClick == 2) {
							selectedBankSlot = bankItem.bankID;
							selectedInventorySlot = -1;
							rightClickMenuX = mc.getMouseX();
							rightClickMenuY = mc.getMouseY();
							rightClickMenu = true;
							mc.setMouseClick(0);
						}
					}

					if (slotHovered && bankItem.getItem().getCatalogID() != -1 && bankDef != null && bankDef.getName() != null) {
						bankHeaderText = bankDef.getName();
					}

					bankSlotStart++;
				}
			}
		}
		if (!searchItem.isEmpty() && searchList.isEmpty()) {
			drawString("No matching items", x + 207, y + 127, 1, 0xFFFF00);
		}
		drawString(bankHeaderText, x + 61, y + 15, 1, 0xFFFFFF);

		int settingsY = y + 203;
		mc.getSurface().drawBoxAlpha(x + 4, y + 197, width - 8, 130, PANEL_SECTION, 95);
		mc.getSurface().drawLineHoriz(x + 6, y + 197, width - 12, BUTTON_BORDER);
		drawString("Inventory", x + 7, y + 215, 1, 0xF89922);

		if (buttonClicked(x + 72, settingsY, 105, 18) && loadoutActionSlot == -1 && !arrangeMenuOpen && !rightClickMenu) {
			sendDepositAllInventory();
			consumeMouse();
		}
		drawButton(x + 72, settingsY, 105, 18, "Deposit all", false, true);

		int arrangeX = x + 186;
		if (buttonClicked(arrangeX, settingsY, 94, 18) && loadoutActionSlot == -1 && !rightClickMenu) {
			arrangeMenuOpen = !arrangeMenuOpen;
			consumeMouse();
		}
		drawButton(arrangeX, settingsY, 94, 18, "Arrange: " + organizeModeName(), arrangeMenuOpen || organizeMode > 0, true);

		if (arrangeMenuOpen) {
			renderArrangeMenu(arrangeX, settingsY - 57);
		}

		drawString("Withdraw", x + 334, settingsY + 13, 1, 0xF89922);
		if (buttonClicked(x + 392, settingsY, 48, 18) && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
			swapNoteMode = false;
			consumeMouse();
		}
		drawButton(x + 392, settingsY, 48, 18, "Item", !swapNoteMode, true);
		if (buttonClicked(x + 443, settingsY, 58, 18) && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
			swapNoteMode = true;
			consumeMouse();
		}
		drawButton(x + 443, settingsY, 58, 18, S_WANT_CERT_AS_NOTES ? "Note" : "Cert", swapNoteMode, true);

		int inventorySlot = 0;
		int inventoryDrawY = y + 224;
		for (int verticalSlots = 0; verticalSlots < 3; verticalSlots++) {
				for (int horizonalSlots = 0; horizonalSlots < 10; horizonalSlots++) {

					int drawX = x + 6 + horizonalSlots * 49;
					int drawY = inventoryDrawY + verticalSlots * 34;

					mc.getSurface().drawBoxAlpha(drawX, drawY, 49, 34, boxColour, 160);
					mc.getSurface().drawBoxBorder(drawX, 50, drawY, 35, 0);

					if (draggingInventoryID != -1
						&& (mc.getInventoryItemAmount(draggingInventoryID) != -1)) {
						drawItemSprite(mc.getInventoryItem(draggingInventoryID), mc.getMouseX(), mc.getMouseY(), true);
					}

					if (inventorySlot < mc.getInventoryItemCount() && mc.getInventoryItemID(inventorySlot) != -1) {
						ItemDef def = mc.getInventoryItem(inventorySlot).getItemDef();
						drawItemSprite(mc.getInventoryItem(inventorySlot), drawX, drawY, false);
						if (def.isStackable() || mc.getInventoryItem(inventorySlot).getNoted()) {
							if (isInside(drawX, drawY, 48, 32)) {
								drawString("" + mc.getInventoryItemAmount(inventorySlot), drawX + 1, drawY + 10, 1, 0x00ff00);
							} else {
								drawString(mudclient.formatStackAmount(mc.getInventoryItemAmount(inventorySlot)),
									drawX + 1, drawY + 10, 1, '\uffff');
							}
						}
					}

					if (isInside(drawX, drawY, 49, 34) && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen && mc.inputX_Action == InputXAction.ACT_0) {
						if (mc.getMouseClick() == 2 || mc.mouseButtonClick == 2) {
							if (inventorySlot < mc.getInventoryItemCount()
								&& mc.getInventoryItemID(inventorySlot) != -1) {
								selectedInventorySlot = inventorySlot;
								selectedBankSlot = -1;
								rightClickMenuX = mc.getMouseX();
								rightClickMenuY = mc.getMouseY();
								rightClickMenu = true;
								mc.setMouseClick(0);
							}
						} else if (organizeMode > 0) {
							if (mc.getMouseButtonDownTime() > 0 && mc.getMouseButtonDown() == 1) {
								if (mc.getMouseButtonDownTime() < 2
									&& inventorySlot < mc.getInventoryItemCount()
									&& mc.getInventoryItemID(inventorySlot) != -1) {
									draggingInventoryID = inventorySlot;
								}
							} else {
								if (draggingInventoryID > -1 && mc.getInventoryItemID(inventorySlot) != -1) {
									sendInventoryOrganize(draggingInventoryID, inventorySlot);
								}
								draggingInventoryID = -1;
							}

						} else if (mc.getMouseClick() == 1) {
							selectedInventorySlot = inventorySlot;
							sendDeposit(1);
						}
					}

					if (isInside(drawX, drawY, 49, 34)) {
						if (mc.getInventoryItemID(inventorySlot) != -1) {
							drawString(EntityHandler.getItemDef(mc.getInventoryItemID(inventorySlot), mc.getInventory()[inventorySlot].getNoted()).getName(), x + 7, y + 15, 0, 0xFFFFFF);
						}
					}
					inventorySlot++;
				}
		}

		bank.drawPanel();
		drawSearchClearButton(rawSearchItem);

		if (loadoutActionSlot != -1) {
			renderLoadoutsPanel();
		}
		if (rightClickMenu && mc.inputX_Action == InputXAction.ACT_0) {
			renderContextMenu();
		}
		return true;
	}

	private void drawButton(int bx, int by, int bw, int bh, String label, boolean active, boolean enabled) {
		boolean hover = enabled && isInside(bx, by, bw, bh);
		int color = active ? BUTTON_ACTIVE : (hover ? BUTTON_HOVER : BUTTON_IDLE);
		if (!enabled) {
			color = 0x33312D;
		}
		mc.getSurface().drawBoxAlpha(bx, by, bw, bh, color, 215);
		mc.getSurface().drawBoxBorder(bx, bw, by, bh, BUTTON_BORDER);
		mc.getSurface().drawBoxBorder(bx + 1, bw - 2, by + 1, bh - 2, active ? BUTTON_ACCENT : BUTTON_INNER);
		int textX = bx + Math.max(3, (bw - mc.getSurface().stringWidth(1, label)) / 2);
		int textY = by + Math.max(fontSizeHeight + 1, (bh + fontSizeHeight) / 2 - 1);
		drawString(label, textX, textY, 1, enabled ? 0xFFFFFF : 0x888888);
	}

	private int loadoutButtonX() {
		return x + 286;
	}

	private int loadoutButtonY() {
		return y + 27;
	}

	private int loadoutButtonWidth() {
		return 90;
	}

	private int loadoutButtonHeight() {
		return 19;
	}

	private int searchFieldX() {
		return x + 384;
	}

	private int searchFieldY() {
		return y + 27;
	}

	private int searchFieldWidth() {
		return 110;
	}

	private int searchFieldHeight() {
		return 18;
	}

	private int searchClearX() {
		return searchFieldX() + searchFieldWidth() - 13;
	}

	private boolean buttonClicked(int bx, int by, int bw, int bh) {
		return mc.inputX_Action == InputXAction.ACT_0 && leftMouseClick() && isInside(bx, by, bw, bh);
	}

	private boolean leftMouseClick() {
		return mc.getMouseClick() == 1 || mc.mouseButtonClick == 1;
	}

	private boolean anyMouseClick() {
		return mc.getMouseClick() != 0 || mc.mouseButtonClick != 0;
	}

	private boolean isInside(int bx, int by, int bw, int bh) {
		return mc.getMouseX() >= bx && mc.getMouseX() < bx + bw
			&& mc.getMouseY() >= by && mc.getMouseY() < by + bh;
	}

	private void consumeMouse() {
		mc.mouseButtonClick = 0;
		mc.setMouseClick(0);
	}

	private String organizeModeName() {
		switch (organizeMode) {
			case 1:
				return "Swap";
			case 2:
				return "Insert";
			default:
				return "Off";
		}
	}

	private void renderArrangeMenu(int menuX, int menuY) {
		int menuW = 94;
		int rowH = 19;
		String[] labels = {"Off", "Swap", "Insert"};
		for (int i = 0; i < labels.length; i++) {
			int rowY = menuY + i * rowH;
			if (buttonClicked(menuX, rowY, menuW, rowH)) {
				organizeMode = i == 0 ? 0 : i;
				arrangeMenuOpen = false;
				draggingBankSlot = -1;
				draggingInventoryID = -1;
				consumeMouse();
			}
			drawButton(menuX, rowY, menuW, rowH, labels[i], organizeMode == (i == 0 ? 0 : i), true);
		}
		if (anyMouseClick() && !isInside(menuX, menuY, menuW, rowH * labels.length)
			&& !isInside(menuX, y + 203, menuW, 18)) {
			arrangeMenuOpen = false;
		}
	}

	private void drawItemSprite(Item item, int drawX, int drawY, boolean dragging) {
		if (item == null || item.getCatalogID() == -1 || item.getItemDef() == null) {
			return;
		}
		ItemDef def = item.getItemDef();
		if (item.getNoted()) {
			if (S_WANT_CERT_AS_NOTES) {
				if (dragging) {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.noteDef), drawX, drawY, 48, 32,
						EntityHandler.noteDef.getPictureMask(), 0, EntityHandler.noteDef.getBlueMask(), false, 0, 1);
				} else {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.noteDef), drawX, drawY, 48, 32,
						EntityHandler.noteDef.getPictureMask(), 0, EntityHandler.noteDef.getBlueMask(), false, 0, 1, 0xFFFFFFFF);
				}
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX + 7, drawY + 8, 29, 19,
					def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
			} else {
				if (dragging) {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.certificateDef), drawX, drawY, 48, 32,
						EntityHandler.certificateDef.getPictureMask(), 0, EntityHandler.certificateDef.getBlueMask(), false, 0, 1);
				} else {
					mc.getSurface().drawSpriteClipping(mc.spriteSelect(EntityHandler.certificateDef), drawX, drawY, 48, 32,
						EntityHandler.certificateDef.getPictureMask(), 0, EntityHandler.certificateDef.getBlueMask(), false, 0, 1, 0xFFFFFFFF);
				}
			}
			return;
		}
		if (dragging) {
			mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX, drawY, 48, 32,
				def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
		} else {
			mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), drawX, drawY, 48, 32,
				def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1, 0xFFFFFFFF);
		}
	}

	private void renderLoadoutsPanel() {
		int panelW = 338;
		int panelH = 166;
		int panelX = x + (width - panelW) / 2;
		int panelY = y + 53;

		mc.getSurface().drawBoxAlpha(panelX, panelY, panelW, panelH, 0x202020, 235);
		mc.getSurface().drawBoxBorder(panelX, panelW, panelY, panelH, 0x000000);
		mc.getSurface().drawBox(panelX, panelY, panelW, 22, PANEL_DARK);
		drawString("Loadouts", panelX + 11, panelY + 15, 1, 0xFFFFFF);
		if (buttonClicked(panelX + panelW - 21, panelY + 4, 16, 14)) {
			loadoutActionSlot = -1;
			consumeMouse();
		}
		drawButton(panelX + panelW - 21, panelY + 4, 16, 14, "x", false, true);

		for (int slot = 0; slot < presetCount; slot++) {
			int cardX = panelX + 10;
			int cardY = panelY + 29 + slot * 43;
			int cardW = panelW - 20;
			boolean empty = isPresetEmpty(slot);
			mc.getSurface().drawBoxAlpha(cardX, cardY, cardW, 36, empty ? 0x3C3934 : 0x263F42, 205);
			mc.getSurface().drawBoxBorder(cardX, cardW, cardY, 36, BUTTON_BORDER);
			drawString("Loadout " + (slot + 1), cardX + 8, cardY + 13, 1, 0xFFFFFF);
			drawString(empty ? "Empty" : "Saved", cardX + 8, cardY + 28, 1, empty ? 0xAAAAAA : 0x00FFFF);

			int btnY = cardY + 8;
			int loadX = cardX + cardW - 145;
			if (!empty && buttonClicked(loadX, btnY, 42, 18)) {
				loadPreset(slot);
				loadoutActionSlot = -1;
				consumeMouse();
			}
			drawButton(loadX, btnY, 42, 18, "Load", false, !empty);

			int saveX = cardX + cardW - 97;
			if (buttonClicked(saveX, btnY, 42, 18)) {
				pendingSavePresetSlot = slot;
				loadoutActionSlot = -1;
				consumeMouse();
			}
			drawButton(saveX, btnY, 42, 18, empty ? "Save" : "Save", false, true);

			int clearX = cardX + cardW - 49;
			if (!empty && buttonClicked(clearX, btnY, 42, 18)) {
				pendingClearPresetSlot = slot;
				loadoutActionSlot = -1;
				consumeMouse();
			}
			drawButton(clearX, btnY, 42, 18, "Clear", false, !empty);
		}

		if (anyMouseClick() && !isInside(panelX, panelY, panelW, panelH)
			&& !isInside(loadoutButtonX(), loadoutButtonY(), loadoutButtonWidth(), loadoutButtonHeight())) {
			loadoutActionSlot = -1;
		}
	}

	private void renderContextMenu() {
		if (selectedBankSlot > -1) {
			renderBankContextMenu();
		} else if (selectedInventorySlot > -1) {
			renderInventoryContextMenu();
		}
	}

	private void renderBankContextMenu() {
		if (selectedBankSlot < 0 || selectedBankSlot >= bankItems.size()
			|| bankItems.get(selectedBankSlot).getItem() == null
			|| bankItems.get(selectedBankSlot).getItem().getItemDef() == null) {
			rightClickMenu = false;
			selectedBankSlot = -1;
			return;
		}
		BankItem bankItem = bankItems.get(selectedBankSlot);
		String name = bankItem.getItem().getItemDef().getName();
		int rows = bankItem.getItem().getItemDef().isWieldable() ? 8 : 7;
		if (lastXAmount > 1 && lastXAmount != 5 && lastXAmount != 10 && lastXAmount != 50) {
			rows++;
		}
		int menuWidth = Math.max(128, mc.getSurface().stringWidth(fontSize, name) + 8);
		int rowH = fontSizeHeight;
		int menuHeight = 20 + rows * rowH + 4;
		positionContextMenu(menuWidth, menuHeight);

		if (!isInside(rightClickMenuX - 6, rightClickMenuY - 6, menuWidth + 12, menuHeight + 12)) {
			if (anyMouseClick()) {
				rightClickMenu = false;
				selectedBankSlot = -1;
			}
			return;
		}

		drawMenuShell(menuWidth, menuHeight, name);
		int row = 0;
		if (bankItem.getItem().getItemDef().isWieldable()) {
			if (drawMenuRow("Wield", row++, menuWidth, rowH)) {
				mc.packetHandler.getClientStream().newPacket(172);
				mc.packetHandler.getClientStream().bufferBits.putShort(selectedBankSlot);
				mc.packetHandler.getClientStream().finishPacket();
				closeContextMenu();
			}
		}
		if (drawMenuRow("Withdraw-1", row++, menuWidth, rowH)) sendWithdraw(1);
		if (drawMenuRow("Withdraw-5", row++, menuWidth, rowH)) sendWithdraw(5);
		if (drawMenuRow("Withdraw-10", row++, menuWidth, rowH)) sendWithdraw(10);
		if (drawMenuRow("Withdraw-50", row++, menuWidth, rowH)) sendWithdraw(50);
		if (lastXAmount > 1 && lastXAmount != 5 && lastXAmount != 10 && lastXAmount != 50) {
			if (drawMenuRow("Withdraw-" + lastXAmount, row++, menuWidth, rowH)) sendWithdraw(lastXAmount);
		}
		if (drawMenuRow("Withdraw-X", row++, menuWidth, rowH)) {
			saveXAmount = true;
			mc.showItemModX(InputXPrompt.bankWithdrawX, InputXAction.BANK_WITHDRAW, true);
			closeContextMenu();
		}
		if (drawMenuRow("Withdraw-All", row++, menuWidth, rowH)) sendWithdraw(Integer.MAX_VALUE);
		if (drawMenuRow("Withdraw-All-But-1", row, menuWidth, rowH)) {
			sendWithdraw(bankItem.getItem().getAmount() - 1);
		}
	}

	private void renderInventoryContextMenu() {
		if (selectedInventorySlot < 0 || selectedInventorySlot >= mc.getInventoryItemCount()
			|| mc.getInventoryItemID(selectedInventorySlot) == -1) {
			rightClickMenu = false;
			selectedInventorySlot = -1;
			return;
		}
		final boolean wantCertDeposit = Config.S_WANT_CERT_DEPOSIT && BankUtil.isCert(mc.getInventoryItemID(selectedInventorySlot));
		String name = EntityHandler.getItemDef(mc.getInventoryItemID(selectedInventorySlot)).getName();
		int rows = wantCertDeposit ? 8 : 6;
		int menuWidth = Math.max(wantCertDeposit ? 142 : 112, mc.getSurface().stringWidth(fontSize, name) + 8);
		int rowH = fontSizeHeight;
		int menuHeight = 20 + rows * rowH + 4;
		positionContextMenu(menuWidth, menuHeight);

		if (!isInside(rightClickMenuX - 6, rightClickMenuY - 6, menuWidth + 12, menuHeight + 12)) {
			if (anyMouseClick()) {
				rightClickMenu = false;
				selectedInventorySlot = -1;
			}
			return;
		}

		drawMenuShell(menuWidth, menuHeight, name);
		int row = 0;
		if (drawMenuRow("Deposit-1", row++, menuWidth, rowH)) sendDeposit(1);
		if (drawMenuRow("Deposit-5", row++, menuWidth, rowH)) sendDeposit(5);
		if (drawMenuRow("Deposit-10", row++, menuWidth, rowH)) sendDeposit(10);
		if (drawMenuRow("Deposit-50", row++, menuWidth, rowH)) sendDeposit(50);
		if (drawMenuRow("Deposit-X", row++, menuWidth, rowH)) {
			tryChangeCertMode(false);
			mc.showItemModX(InputXPrompt.bankDepositX, InputXAction.BANK_DEPOSIT, true);
			closeContextMenu();
		}
		if (drawMenuRow("Deposit-All", row++, menuWidth, rowH)) sendDeposit(Integer.MAX_VALUE);
		if (wantCertDeposit) {
			if (drawMenuRow("Uncert+Deposit-X", row++, menuWidth, rowH)) {
				tryChangeCertMode(true);
				mc.showItemModX(InputXPrompt.bankDepositX, InputXAction.BANK_DEPOSIT, true);
				closeContextMenu();
			}
			if (drawMenuRow("Uncert+Deposit-All", row, menuWidth, rowH)) sendDeposit(Integer.MAX_VALUE, true);
		}
	}

	private void positionContextMenu(int menuWidth, int menuHeight) {
		if (rightClickMenuX + menuWidth >= mc.getGameWidth()) {
			rightClickMenuX = mc.getGameWidth() - menuWidth - 5;
		}
		if (rightClickMenuY + menuHeight >= mc.getGameHeight()) {
			rightClickMenuY = mc.getGameHeight() - menuHeight - 5;
		}
	}

	private void drawMenuShell(int menuWidth, int menuHeight, String title) {
		mc.getSurface().drawBoxAlpha(rightClickMenuX, rightClickMenuY, menuWidth + 2, menuHeight, 0x5C5548, 255);
		mc.getSurface().drawBoxAlpha(rightClickMenuX + 1, rightClickMenuY + 1, menuWidth, 17, 0x000000, 255);
		mc.getSurface().drawBoxBorder(rightClickMenuX + 1, menuWidth, rightClickMenuY + 18, menuHeight - 19, 0x000000);
		drawString(title, rightClickMenuX + 4, rightClickMenuY + 13, fontSize, 0xFFFFFF);
	}

	private boolean drawMenuRow(String label, int row, int menuWidth, int rowH) {
		int rowTop = rightClickMenuY + 20 + row * rowH;
		boolean hover = isInside(rightClickMenuX, rowTop, menuWidth, rowH);
		drawString(label, rightClickMenuX + 4, rowTop + rowH - 2, fontSize, hover ? 0xFDFF21 : 0xFFFFFF);
		if (hover && mc.getMouseClick() == 1) {
			consumeMouse();
			return true;
		}
		return false;
	}

	private void closeContextMenu() {
		rightClickMenu = false;
		selectedBankSlot = -1;
		selectedInventorySlot = -1;
		consumeMouse();
	}

	private void drawSearchClearButton(String rawSearchItem) {
		if (rawSearchItem == null || rawSearchItem.isEmpty()) {
			if (!bank.focusOn(bankSearch)) {
				drawString("item name", searchFieldX() + 7, searchFieldY() + 13, 1, 0x777777);
			}
			return;
		}
		boolean hover = isInside(searchClearX(), searchFieldY() + 1, 12, searchFieldHeight() - 2);
		drawString("x", searchClearX() + 4, searchFieldY() + 13, 1, hover ? 0xFDFF21 : 0xFFFFFF);
	}

	private void tryChangeCertMode(boolean mode) {
		if (swapCertMode != mode) {
			sendCertMode(mode);
			swapCertMode = mode;
		}
	}

	public boolean getUncertMode() {
		return swapCertMode;
	}

	public void resetUncertMode() {
		swapCertMode = false;
	}

	private void sendCertMode(boolean mode) {
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(0);
		mc.packetHandler.getClientStream().bufferBits.putByte(mode ? 1 : 0);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private void sendInventoryOrganize(int draggingInventoryID2, int inventorySlot) {
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(organizeMode == 1 ? 4 : 5);
		mc.packetHandler.getClientStream().bufferBits.putInt(draggingInventoryID2);
		mc.packetHandler.getClientStream().bufferBits.putInt(inventorySlot);
		mc.packetHandler.getClientStream().finishPacket();
		mc.setMouseClick(0);
	}

	private void sendItemSwap(int draggingBankSlot2, int currentSlot) {
		if (!bank.getControlText(bankSearch).isEmpty()) {
			return;
		}
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte((organizeMode == 1 ? 2 : 3));
		mc.packetHandler.getClientStream().bufferBits.putInt(draggingBankSlot2);
		mc.packetHandler.getClientStream().bufferBits.putInt(currentSlot);
		mc.packetHandler.getClientStream().finishPacket();
		mc.setMouseClick(0);
	}

	public void sendDeposit(int i) {
		this.sendDeposit(i, false);
	}

	public void sendDeposit(int i, boolean uncertMode) {
		if (Config.S_WANT_CUSTOM_BANKS) {
			if (selectedInventorySlot < 0 || selectedInventorySlot >= mc.getInventoryItemCount()) {
				return;
			}
			int itemID = mc.getInventoryItemID(selectedInventorySlot);
			if (itemID < 0) {
				return;
			}
			int inventoryCount = mc.getInventoryCount(itemID);
			if (i > inventoryCount) {
				i = inventoryCount;
			}
			if (i <= 0) {
				return;
			}
			tryChangeCertMode(uncertMode);
			mc.packetHandler.getClientStream().newPacket(23);
			mc.packetHandler.getClientStream().bufferBits.putShort(itemID);
			mc.packetHandler.getClientStream().bufferBits.putInt(i);
			mc.packetHandler.getClientStream().finishPacket();
			rightClickMenu = false;
			mc.setMouseClick(0);
			mc.setMouseButtonDown(0);
			if (inventoryCount - i < 1) {
				selectedInventorySlot = -1;
			}
			if (swapCertMode && BankUtil.isCert(itemID) && !hasBankItem(BankUtil.uncertedID(itemID))) {
				this.selectedBankSlot = -1;
			}
		} else {
			// Authentic Bank Deposit
			tryChangeCertMode(uncertMode);
			super.sendDeposit(i);
		}
	}


	private void sendDepositAllInventory() {
		mc.packetHandler.getClientStream().newPacket(24);
		mc.packetHandler.getClientStream().finishPacket();
		rightClickMenu = false;
		mc.setMouseClick(0);
		mc.setMouseButtonDown(0);
		selectedInventorySlot = -1;
	}

	public void sendWithdraw(int i) {
		if (Config.S_WANT_CUSTOM_BANKS) {
			if (selectedBankSlot < 0 || selectedBankSlot >= bankItems.size()) {
				return;
			}
			BankItem selectedItem = bankItems.get(selectedBankSlot);
			if (selectedItem == null || selectedItem.getItem() == null || selectedItem.getItem().getCatalogID() < 0) {
				return;
			}
			if (i > selectedItem.getItem().getAmount()) {
				i = selectedItem.getItem().getAmount();
			}
			if (i <= 0) {
				return;
			}
			mc.packetHandler.getClientStream().newPacket(22);
			mc.packetHandler.getClientStream().bufferBits.putShort(selectedItem.getItem().getCatalogID());
			mc.packetHandler.getClientStream().bufferBits.putInt(i);

			if (Config.S_WANT_BANK_NOTES)
				mc.packetHandler.getClientStream().bufferBits.putByte(swapNoteMode ? 1 : 0);

			mc.packetHandler.getClientStream().finishPacket();
			rightClickMenu = false;
			selectedBankSlot = -1;
			mc.setMouseClick(0);
			mc.setMouseButtonDown(0);
		} else {
			super.sendWithdraw(i);
		}
	}

	private boolean hasBankItem(int itemID) {
		for (BankItem item : bankItems) {
			if (item != null && item.getItem() != null && item.getItem().getCatalogID() == itemID) {
				return true;
			}
		}
		return false;
	}


	public void keyDown(int key) {
		if (mc.inputX_Action == InputXAction.ACT_0) {
			if (key == 27) {
				if (pendingSavePresetSlot != -1) {
					pendingSavePresetSlot = -1;
					return;
				}
				if (pendingClearPresetSlot != -1) {
					pendingClearPresetSlot = -1;
					return;
				}
				if (loadoutActionSlot != -1 || arrangeMenuOpen) {
					loadoutActionSlot = -1;
					arrangeMenuOpen = false;
					return;
				}
				if (!bank.focusOn(-1)) {
					resetVar();
					return;
				}
			}
			if (key == 27 && !bank.focusOn(-1)) {
				resetVar();
				return;
			}
			if (bank.focusOn(bankSearch)) {
				if (mc.bankPage != 0)
					mc.bankPage = 0;
				bank.keyPress(key);
			} else {
				this.hotkey = key;
			}
		}

		return;
	}

	private void resetVar() {
		bank.clearList(this.bankSearch);
		bank.setText(this.bankSearch, "");
		bank.setFocus(-1);
		swapNoteMode = false;
	}

	public void initPresets() {
		for (int p = 0; p < presetCount; p++)
			presets[p] = new Preset();

	}

	private void renderSaveConfirm(int slot) {
		int modalW = 330;
		int modalH = 268;
		int modalX = (mc.getGameWidth() - modalW) / 2;
		int modalY = (mc.getGameHeight() - modalH) / 2 - 3;

		mc.getSurface().drawBoxAlpha(modalX, modalY, modalW, modalH, 0x202020, 220);
		mc.getSurface().drawBoxBorder(modalX, modalW, modalY, modalH, 0x000000);
		mc.getSurface().drawBox(modalX, modalY, modalW, 22, PANEL_DARK);
		drawString("Save Loadout " + (slot + 1) + "?", modalX + modalW / 2 - 50, modalY + 15, 1, 0xFFFFFF);

		drawString("Inventory",
			modalX + 22, modalY + 38, 1, 0xF89922);

		int gridX = modalX + (modalW - 6 * 49) / 2;
		int gridY = modalY + 48;
		int invSlot = 0;
		for (int r = 0; r < 5; r++) {
			for (int c = 0; c < 6; c++) {
				int dx = gridX + c * 49;
				int dy = gridY + r * 34;
				mc.getSurface().drawBoxAlpha(dx, dy, 49, 34, 0xd0d0d0, 160);
				mc.getSurface().drawBoxBorder(dx, 50, dy, 35, 0);
				if (invSlot < mc.getInventoryItemCount() && mc.getInventoryItemID(invSlot) != -1) {
					ItemDef def = mc.getInventoryItem(invSlot).getItemDef();
					drawItemSprite(mc.getInventoryItem(invSlot), dx, dy, false);
					if (def.isStackable() || mc.getInventoryItem(invSlot).getNoted()) {
						drawString(mudclient.formatStackAmount(mc.getInventoryItemAmount(invSlot)),
							dx + 1, dy + 10, 1, 0xFFFF00);
					}
				}
				invSlot++;
			}
		}

		int btnW = 90;
		int btnH = 22;
		int btnY = gridY + 5 * 34 + 12;
		int saveX = modalX + modalW / 2 - btnW - 8;
		int cancelX = modalX + modalW / 2 + 8;

		if (buttonClicked(saveX, btnY, btnW, btnH)) {
			saveSetup(slot);
			pendingSavePresetSlot = -1;
			consumeMouse();
		}
		drawButton(saveX, btnY, btnW, btnH, "Save", false, true);
		if (buttonClicked(cancelX, btnY, btnW, btnH)) {
			pendingSavePresetSlot = -1;
			consumeMouse();
		}
		drawButton(cancelX, btnY, btnW, btnH, "Cancel", false, true);

		if (anyMouseClick() && !isInside(modalX, modalY, modalW, modalH)) {
			pendingSavePresetSlot = -1;
			consumeMouse();
		}
	}

	private void renderClearConfirm(int slot) {
		int modalW = 260;
		int modalH = 104;
		int modalX = (mc.getGameWidth() - modalW) / 2;
		int modalY = (mc.getGameHeight() - modalH) / 2 - 3;

		mc.getSurface().drawBoxAlpha(modalX, modalY, modalW, modalH, 0x202020, 230);
		mc.getSurface().drawBoxBorder(modalX, modalW, modalY, modalH, 0x000000);
		mc.getSurface().drawBox(modalX, modalY, modalW, 22, PANEL_DARK);
		drawString("Clear Loadout " + (slot + 1) + "?", modalX + 68, modalY + 15, 1, 0xFFFFFF);
		drawString("This removes the saved inventory setup.", modalX + 20, modalY + 45, 1, 0xF89922);

		int clearX = modalX + 35;
		int cancelX = modalX + 135;
		int btnY = modalY + 68;
		if (buttonClicked(clearX, btnY, 86, 22)) {
			clearPreset(slot);
			pendingClearPresetSlot = -1;
			consumeMouse();
		}
		drawButton(clearX, btnY, 86, 22, "Clear", false, true);
		if (buttonClicked(cancelX, btnY, 86, 22)) {
			pendingClearPresetSlot = -1;
			consumeMouse();
		}
		drawButton(cancelX, btnY, 86, 22, "Cancel", false, true);

		if (anyMouseClick() && !isInside(modalX, modalY, modalW, modalH)) {
			pendingClearPresetSlot = -1;
			consumeMouse();
		}
	}

	private boolean isPresetEmpty(int slot) {
		if (slot < 0 || slot >= presetCount || presets[slot] == null) return true;
		for (Item item : presets[slot].inventory) {
			if (item != null && item.getItemDef() != null) return false;
		}
		return true;
	}

	public void updatePreset(int id, Item[] inventoryItems, Item[] equipmentItems) {
		for (int i = 0; i < Config.S_PLAYER_INVENTORY_SLOTS; i++)
		{
			if (inventoryItems[i] != null) {
				presets[id].inventory[i].setItemDef(inventoryItems[i].getItemDef());
				presets[id].inventory[i].setAmount(inventoryItems[i].getAmount());
				presets[id].inventory[i].setNoted(inventoryItems[i].getNoted());
			} else {
				presets[id].inventory[i].setItemDef(null);
				presets[id].inventory[i].setAmount(0);
			}
		}

		for (int i = 0; i < S_PLAYER_SLOT_COUNT; i++)
		{
			presets[id].equipment[i].setItemDef(null);
			presets[id].equipment[i].setAmount(0);
		}
	}

	private void saveSetup(int slot) {
		Item[] inventoryItems = new Item[S_PLAYER_INVENTORY_SLOTS];
		Item[] equipmentItems = new Item[S_PLAYER_SLOT_COUNT];
		for (int i = 0; i < S_PLAYER_INVENTORY_SLOTS; i++) {
			if (i < mc.getInventoryItemCount())
				inventoryItems[i] = mc.getInventoryItem(i);
			else
				inventoryItems[i] = new Item();
		}
		updatePreset(slot, inventoryItems, equipmentItems);
		mc.packetHandler.getClientStream().newPacket(27);
		mc.packetHandler.getClientStream().bufferBits.putShort(slot);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private void clearPreset(int slot) {
		clearPresetLocal(slot);
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(14);
		mc.packetHandler.getClientStream().bufferBits.putByte(slot);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private void clearPresetLocal(int slot) {
		if (slot < 0 || slot >= presetCount || presets[slot] == null) {
			return;
		}
		for (int i = 0; i < Config.S_PLAYER_INVENTORY_SLOTS; i++) {
			presets[slot].inventory[i].setItemDef(null);
			presets[slot].inventory[i].setAmount(0);
			presets[slot].inventory[i].setNoted(false);
		}
		for (int i = 0; i < S_PLAYER_SLOT_COUNT; i++) {
			presets[slot].equipment[i].setItemDef(null);
			presets[slot].equipment[i].setAmount(0);
			presets[slot].equipment[i].setNoted(false);
		}
	}

	private void loadPreset(int slot) {
		if (! S_WANT_BANK_PRESETS)
			return;
		mc.packetHandler.getClientStream().newPacket(28);
		mc.packetHandler.getClientStream().bufferBits.putShort(slot);
		mc.packetHandler.getClientStream().finishPacket();
	}

	/**
	 * Calculate the value of the player's tradeable items
	 */
	public void calculateWealth() {
		long totalWealth = 0;
		for (BankItem item : bankItems) {
			// Get the item's definition
			ItemDef itemDef = item.getItem().getItemDef();
			int amount = item.getItem().getAmount();

			if (itemDef == null) continue;

			if (!itemDef.untradeable) {
				totalWealth += (itemDef.getBasePrice() * amount);
			}
		}

		this.totalWealth = totalWealth;
	}

	public static class Preset {
		public Item[] inventory;
		public Item[] equipment;

		public Preset() {
			inventory = new Item[Config.S_PLAYER_INVENTORY_SLOTS];
			equipment = new Item[Config.S_PLAYER_SLOT_COUNT];

			for (int i = 0; i < inventory.length; i++)
				inventory[i] = new Item();
			for (int i = 0; i < equipment.length; i++)
				equipment[i] = new Item();
		}
	}
}
