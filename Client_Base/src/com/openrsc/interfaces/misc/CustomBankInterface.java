package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import orsc.Config;
import orsc.enumerations.InputXAction;
import orsc.graphics.gui.InputXPrompt;
import orsc.mudclient;
import orsc.util.BankUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

import static orsc.Config.*;
import static orsc.osConfig.C_MENU_SIZE;

public final class CustomBankInterface extends BankInterface {
	private static final String ANDROID_SMOKE_BANK_FLAG = "android-smoke-bank.flag";
	private static final long ANDROID_SMOKE_BANK_STATE_LOG_INTERVAL_MS = 1000L;
	private static int fontSize = Config.isAndroid() ? C_MENU_SIZE : 1;
	private static int fontSizeHeight;
	private static final int BANK_COLUMNS = 10;
	private static final int BANK_ROWS = 4;
	private static final int BANK_PAGE_SIZE = BANK_COLUMNS * BANK_ROWS;
	private static final int BANK_SLOT_WIDTH_COMPACT = 56;
	private static final int BANK_SLOT_HEIGHT_COMPACT = 34;
	private static final int BANK_SLOT_WIDTH_ANDROID = 48;
	private static final int BANK_SLOT_HEIGHT_ANDROID = 32;
	private static final int BANK_SLOT_WIDTH_SPACIOUS = 62;
	private static final int BANK_SLOT_HEIGHT_SPACIOUS = 40;
	private static final int BANK_PANEL_WIDTH = 620;
	private static final int BANK_PANEL_WIDTH_ANDROID = 504;
	private static final int BANK_PANEL_WIDTH_SPACIOUS = 720;
	private static final int BANK_TAB_LIMIT = 6;
	private static final int UI_BG = 0x08050C;
	private static final int UI_HEADER = 0x0B0710;
	private static final int UI_SLOT = 0x3C3125;
	private static final int UI_SLOT_HOVER = 0x58452F;
	private static final int UI_GRID_LINE = 0x6E5737;
	private static final int UI_PANEL_BODY_ALPHA = 46;
	private static final int UI_HOVER_GLOW = 0xF6DA7D;
	private static final int UI_TOOLBAR_BG = 0x08050C;
	private static final int UI_TOOLBAR_ALPHA = 142;
	private static final int UI_TOOLBAR_DIVIDER_ALPHA = 104;
	private static final int UI_MENU_ALPHA = 132;
	private static final int UI_MENU_HEADER_ALPHA = 96;
	private static final int UI_MENU_LINE_ALPHA = 116;
	private static final int UI_MENU_HOVER_ALPHA = 58;
	private static final int UI_PURPLE = 0x6A4FA0;
	private static final int UI_PURPLE_DARK = 0x241A36;
	private static final int UI_PURPLE_ACTIVE = 0x5B24A3;
	private static final int UI_GOLD = 0xF6DA7D;
	private static final int UI_MUTED = 0xA99BBF;
	private static final int UI_GREEN = 0x40FF48;
	private static final int UI_CYAN = 0x00FFFF;
	private static final int UI_DISABLED = 0x777777;
	private static final int UI_SLOT_ALPHA = 58;
	private static final int UI_SLOT_HOVER_ALPHA = 78;
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
	private String lastBankSearchText = "";
	private long lastAndroidSmokeBankStateLogMillis = 0L;
	private int lastAndroidSmokeBankScroll = Integer.MIN_VALUE;
	private String lastAndroidSmokeBankSearch = null;
	private int androidBankSwipeLastY = Integer.MIN_VALUE;
	private int androidBankSwipeRemainder = 0;
	private boolean androidBankSwipeScrolling = false;

	public CustomBankInterface(mudclient mc) {
		super(mc);
		if (Config.S_WANT_CUSTOM_BANKS) {
			updateLayout();
			bankScroll = bank.addScrollingList(bankGridX(), bankGridY(), bankGridWidth(), bankGridHeight(), 500, BANK_ROWS, true);
			bankSearch = bank.addLeftTextEntry(searchFieldX(), searchFieldY(), searchFieldWidth(), searchFieldHeight(), 0, 15, false, true);
		}
	}

	private void updateLayout() {
		if (androidLayout()) {
			width = Math.min(BANK_PANEL_WIDTH_ANDROID, Math.max(bankGridWidth() + 20, mc.getGameWidth() - 8));
			width = Math.min(width, mc.getGameWidth() - 8);
			height = compactPanelHeight();
		} else if (spaciousLayout()) {
			int spaciousWidth = Math.min(BANK_PANEL_WIDTH_SPACIOUS, mc.getGameWidth() - 32);
			width = Math.max(bankGridWidth() + 56, spaciousWidth);
			height = spaciousPanelHeight();
		} else {
			width = Math.min(BANK_PANEL_WIDTH, Math.max(bankGridWidth() + 20, mc.getGameWidth() - 30));
			height = compactPanelHeight();
		}
		x = (mc.getGameWidth() - width) / 2;
		if (androidLayout()) {
			x = Math.max(4, x);
			y = Math.max(4, (mc.getGameHeight() - height) / 2);
		} else {
			y = Math.max(55, Math.min(82, (mc.getGameHeight() - height) / 2 + 2));
		}
	}

	private boolean androidLayout() {
		return Config.isAndroid();
	}

	private boolean spaciousLayout() {
		return !androidLayout() && mc.getGameWidth() >= 700 && mc.getGameHeight() >= 520;
	}

	private int contentX() {
		return x + 20;
	}

	private int contentRight() {
		return x + width - 20;
	}

	private int controlRowY() {
		return y + controlRowYOffset();
	}

	private int controlRowYOffset() {
		if (androidLayout()) {
			return 42;
		}
		return spaciousLayout() ? 58 : 54;
	}

	private int bankGridX() {
		return x + (width - bankGridWidth()) / 2;
	}

	private int bankGridY() {
		return y + bankGridYOffset();
	}

	private int bankGridYOffset() {
		if (androidLayout()) {
			return 64;
		}
		return spaciousLayout() ? 96 : 82;
	}

	private int bankGridWidth() {
		return BANK_COLUMNS * bankSlotWidth();
	}

	private int bankGridHeight() {
		return BANK_ROWS * bankSlotHeight();
	}

	private int actionRowY() {
		return y + actionRowYOffset();
	}

	private int actionRowYOffset() {
		if (androidLayout()) {
			return bankGridYOffset() + bankGridHeight() + 4;
		}
		return bankGridYOffset() + bankGridHeight() + (spaciousLayout() ? 17 : 8);
	}

	private int inventoryGridY() {
		return y + inventoryGridYOffset();
	}

	private int inventoryGridYOffset() {
		if (androidLayout()) {
			return actionRowYOffset() + actionButtonHeight() + 4;
		}
		return actionRowYOffset() + actionButtonHeight() + (spaciousLayout() ? 13 : 8);
	}

	private int inventoryGridHeight() {
		return 3 * bankSlotHeight();
	}

	private int spaciousPanelHeight() {
		return inventoryGridYOffset() + inventoryGridHeight() + 18;
	}

	private int compactPanelHeight() {
		if (androidLayout()) {
			return inventoryGridYOffset() + inventoryGridHeight() + 6;
		}
		return inventoryGridYOffset() + inventoryGridHeight() + 18;
	}

	private int bankSlotWidth() {
		if (androidLayout()) {
			return BANK_SLOT_WIDTH_ANDROID;
		}
		return spaciousLayout() ? BANK_SLOT_WIDTH_SPACIOUS : BANK_SLOT_WIDTH_COMPACT;
	}

	private int bankSlotHeight() {
		if (androidLayout()) {
			return BANK_SLOT_HEIGHT_ANDROID;
		}
		return spaciousLayout() ? BANK_SLOT_HEIGHT_SPACIOUS : BANK_SLOT_HEIGHT_COMPACT;
	}

	private int actionButtonHeight() {
		if (androidLayout()) {
			return 20;
		}
		return spaciousLayout() ? 26 : 22;
	}

	private boolean isAndroidSmokeBankLoggingEnabled() {
		return Config.isAndroid()
			&& (isFileInDirectory(Config.F_CACHE_DIR, ANDROID_SMOKE_BANK_FLAG)
			|| isFileInDirectory(Config.F_ANDROID_SMOKE_DIR, ANDROID_SMOKE_BANK_FLAG));
	}

	private boolean isFileInDirectory(final String directory, final String fileName) {
		return directory != null
			&& !directory.isEmpty()
			&& new File(directory, fileName).isFile();
	}

	private String androidSmokeLogToken(final String value) {
		if (value == null) return "null";

		final StringBuilder token = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			final char ch = value.charAt(i);
			if ((ch >= 'A' && ch <= 'Z')
				|| (ch >= 'a' && ch <= 'z')
				|| (ch >= '0' && ch <= '9')
				|| ch == '_'
				|| ch == '-'
				|| ch == '.') {
				token.append(ch);
			} else {
				token.append('_');
			}
		}
		return token.toString();
	}

	private int countInventoryItems() {
		int count = 0;
		for (int slot = 0; slot < mc.getInventoryItemCount(); slot++) {
			if (mc.getInventoryItemID(slot) != -1) {
				count++;
			}
		}
		return count;
	}

	private void logAndroidSmokeBankState(final String rawSearchItem, final int matches, final int visibleBankSlotStart) {
		if (!isAndroidSmokeBankLoggingEnabled()) return;

		final int scroll = bank.getScrollPosition(bankScroll);
		if (scroll != lastAndroidSmokeBankScroll) {
			System.out.println("ANDROID_SMOKE_BANK_SCROLL"
				+ " before=" + lastAndroidSmokeBankScroll
				+ " after=" + scroll
				+ " bankPage=" + mc.bankPage
				+ " visibleBankSlotStart=" + visibleBankSlotStart);
			lastAndroidSmokeBankScroll = scroll;
		}

		final String search = rawSearchItem == null ? "" : rawSearchItem;
		if (lastAndroidSmokeBankSearch == null || !lastAndroidSmokeBankSearch.equals(search)) {
			System.out.println("ANDROID_SMOKE_BANK_SEARCH"
				+ " query=" + androidSmokeLogToken(search)
				+ " length=" + search.length()
				+ " matches=" + matches
				+ " bankPage=" + mc.bankPage);
			lastAndroidSmokeBankSearch = search;
		}

		final long now = System.currentTimeMillis();
		if (now - this.lastAndroidSmokeBankStateLogMillis < ANDROID_SMOKE_BANK_STATE_LOG_INTERVAL_MS) return;
		this.lastAndroidSmokeBankStateLogMillis = now;

		System.out.println("ANDROID_SMOKE_BANK_OPEN"
			+ " bankItems=" + bankItems.size()
			+ " max=" + mc.bankItemsMax
			+ " inventoryItems=" + countInventoryItems()
			+ " bankPage=" + mc.bankPage
			+ " scroll=" + scroll
			+ " search=" + androidSmokeLogToken(search)
			+ " searchFocused=" + bank.focusOn(bankSearch)
				+ " matches=" + matches
				+ " visibleBankSlotStart=" + visibleBankSlotStart
				+ " bankSlotX=" + (bankGridX() + bankSlotWidth() / 2)
				+ " bankSlotY=" + (bankGridY() + bankSlotHeight() / 2)
				+ " inventorySlotX=" + (bankGridX() + bankSlotWidth() / 2)
				+ " inventorySlotY=" + (inventoryGridY() + bankSlotHeight() / 2)
				+ " searchX=" + (searchFieldX() + searchFieldWidth() / 2)
				+ " searchY=" + (searchFieldY() + searchFieldHeight() / 2)
				+ " searchClearX=" + (searchClearHitX() + searchClearHitWidth() / 2)
				+ " searchClearY=" + (searchClearHitY() + searchClearHitHeight() / 2)
				+ " depositAllX=" + (depositButtonX() + depositButtonWidth() / 2)
				+ " depositAllY=" + (actionRowY() + 12)
			+ " loadoutsX=" + (loadoutButtonX() + loadoutButtonWidth() / 2)
			+ " loadoutsY=" + (loadoutButtonY() + loadoutButtonHeight() / 2)
			+ " loadoutSave0X=" + (x + 337)
			+ " loadoutSave0Y=" + (y + 99)
			+ " loadoutLoad0X=" + (x + 289)
			+ " loadoutLoad0Y=" + (y + 99)
			+ " confirmSaveX=203"
			+ " confirmSaveY=271"
			+ " mouseX=" + mc.getMouseX()
			+ " mouseY=" + mc.getMouseY());
	}

	private void logAndroidSmokeBankAction(final String action, final int catalogID, final int amount, final int slot) {
		if (!isAndroidSmokeBankLoggingEnabled()) return;

		System.out.println("ANDROID_SMOKE_BANK_ACTION"
			+ " action=" + action
			+ " catalogID=" + catalogID
			+ " amount=" + amount
			+ " slot=" + slot
			+ " bankItems=" + bankItems.size()
			+ " inventoryItems=" + countInventoryItems()
			+ " scroll=" + bank.getScrollPosition(bankScroll)
			+ " bankPage=" + mc.bankPage
			+ " mouseX=" + mc.getMouseX()
			+ " mouseY=" + mc.getMouseY());
	}

	private void logAndroidSmokeBankLoadoutsPanel(final int panelX, final int panelY) {
		if (!isAndroidSmokeBankLoggingEnabled()) return;

		System.out.println("ANDROID_SMOKE_BANK_LOADOUT_PANEL"
			+ " panelX=" + panelX
			+ " panelY=" + panelY
			+ " save0X=" + (x + 337)
			+ " save0Y=" + (y + 99)
			+ " load0X=" + (x + 289)
			+ " load0Y=" + (y + 99));
	}

	private void logAndroidSmokeBankSaveConfirm(final int slot, final int saveX, final int saveY) {
		if (!isAndroidSmokeBankLoggingEnabled()) return;

		System.out.println("ANDROID_SMOKE_BANK_MODAL"
			+ " type=SAVE_CONFIRM"
			+ " slot=" + slot
			+ " saveX=" + saveX
			+ " saveY=" + saveY);
	}

	@Override
	public boolean onRender() {
		if (!Config.S_WANT_CUSTOM_BANKS) return super.onRender();

		updateLayout();
		bank.reposition(bankSearch, searchFieldX(), searchFieldY(), searchFieldWidth(), searchFieldHeight());
		bank.reposition(bankScroll, bankGridX(), bankGridY(), bankGridWidth(), bankGridHeight());
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

		drawFrame(x, y, width, height);
		drawSkinSprite("bank-chest-24.png", x + 20, y + 16, 24, 24);
		drawString("BANK", x + 50, y + 32, 4, UI_GOLD);

		int closeButtonX = x + width - 36;
		int closeButtonY = y + 14;
		if (buttonClicked(closeButtonX, closeButtonY, 24, 24)) {
			resetVar();
			bankClose();
			consumeMouse();
		}
		drawCloseButton(closeButtonX, closeButtonY, isInside(closeButtonX, closeButtonY, 24, 24));

		int bankPages = bankItems.isEmpty() ? 0 :
			Math.min(BANK_TAB_LIMIT, Math.max(1, (bankItems.size() + BANK_PAGE_SIZE - 1) / BANK_PAGE_SIZE));
		if (mc.bankPage > bankPages) {
			mc.bankPage = bankPages;
		}

		int tabX = contentX();
		int tabY = controlRowY();
		int tabCount = bankPages + 1;
		int tabGap = 4;
		int maxTabsWidth = loadoutButtonX() - tabX - 10;
		int tabWidth = Math.max(32, Math.min(54, (maxTabsWidth - (tabCount - 1) * tabGap) / tabCount));
		int tabHeight = actionButtonHeight();
		for (int tabs = 0; tabs < tabCount; tabs++) {
			int drawX = tabX + tabs * (tabWidth + tabGap);
			String label = tabs == 0 ? "All" : (tabWidth < 46 ? "T" + tabs : "Tab " + tabs);
			boolean active = tabs == mc.bankPage;
			if (buttonClicked(drawX, tabY, tabWidth, tabHeight)) {
				if (!rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
					bank.setText(this.bankSearch, "");
					lastBankSearchText = "";
					mc.bankPage = tabs;
					bank.resetListToIndex(bankScroll, 0);
					consumeMouse();
				}
			}
			drawBankTab(drawX, tabY, tabWidth, tabHeight, label, active);
		}

		if (S_WANT_BANK_PRESETS) {
			if (buttonClicked(loadoutButtonX(), loadoutButtonY(), loadoutButtonWidth(), loadoutButtonHeight())) {
				loadoutActionSlot = loadoutActionSlot == -1 ? 0 : -1;
				arrangeMenuOpen = false;
				consumeMouse();
			}
			drawButton(loadoutButtonX(), loadoutButtonY(), loadoutButtonWidth(), loadoutButtonHeight(),
				"Loadouts", loadoutActionSlot != -1, true, "bank-loadouts-shirt-16.png");
		}

		drawSearchFrame();

		String rawSearchItem = bank.getControlText(bankSearch);
		if (rawSearchItem == null) {
			rawSearchItem = "";
		}
		if (handleSearchClick(rawSearchItem)) {
			rawSearchItem = bank.getControlText(bankSearch);
			if (rawSearchItem == null) {
				rawSearchItem = "";
			}
		} else if ((leftMouseClick() || mc.getMouseButtonDown() == 1) && bank.focusOn(bankSearch)
			&& !isInside(searchFrameX(), searchFrameY(), searchFrameWidth(), searchFrameHeight())) {
			bank.setFocus(-1);
			if (Config.isAndroid() && orsc.osConfig.F_SHOWING_KEYBOARD && mudclient.clientPort != null) {
				mudclient.clientPort.closeKeyboard();
			}
		}
		if (!rawSearchItem.equals(lastBankSearchText)) {
			lastBankSearchText = rawSearchItem;
			bank.resetListToIndex(bankScroll, 0);
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
			bankSlotStart = clampedBankScrollRow() * BANK_COLUMNS;
			handleAndroidBankSwipeScroll();
			bankSlotStart = clampedBankScrollRow() * BANK_COLUMNS;
		} else {
			bank.hide(bankScroll);
			resetAndroidBankSwipeScroll();
		}

		String bankHeaderText = "Slots " + bankItems.size() + "/" + mc.bankItemsMax;
		if (!searchItem.isEmpty()) {
			bankHeaderText = searchList.size() + " found - slots " + bankItems.size() + "/" + mc.bankItemsMax;
		}
		int visibleBankSlotStart = bankSlotStart;
		logAndroidSmokeBankState(rawSearchItem, searchList.size(), visibleBankSlotStart);

		drawGridPanel(bankGridX(), bankGridY(), bankGridWidth(), bankGridHeight());
		int gridY = bankGridY();
		for (int verticalSlots = 0; verticalSlots < BANK_ROWS; verticalSlots++) {
			for (int horizonalSlots = 0; horizonalSlots < BANK_COLUMNS; horizonalSlots++) {
				BankItem bankItem = null;
				ItemDef bankDef = null;
				if (bankSlotStart >= 0 && bankSlotStart < searchList.size()) {
					bankItem = searchList.get(bankSlotStart);
				}
				if (bankItem != null)
					bankDef = bankItem.getItem().getItemDef();

				int drawX = bankGridX() + horizonalSlots * bankSlotWidth();
				int drawY = gridY + verticalSlots * bankSlotHeight();
				boolean slotHovered = isInside(drawX, drawY, bankSlotWidth(), bankSlotHeight());
				boolean bankItemHovered = bankItem != null && slotHovered && !rightClickMenu
					&& loadoutActionSlot == -1 && !arrangeMenuOpen;
				drawSlot(drawX, drawY, bankSlotWidth(), bankSlotHeight(), UI_SLOT,
					bankItem != null,
					bankItemHovered);
				if (bankItem != null) {
					if (draggingBankSlot >= 0 && draggingBankSlot < bankItems.size()
						&& bank.getControlText(bankSearch).isEmpty()) {
						drawItemSprite(bankItems.get(draggingBankSlot).getItem(), mc.getMouseX(), mc.getMouseY(), true);
						drawStackAmount(bankItems.get(draggingBankSlot).getItem().getAmount(), mc.getMouseX(), mc.getMouseY(), UI_GREEN);
					}

					if (bankItem.getItem().getCatalogID() != -1 && bankDef != null) {
						if (draggingBankSlot != bankItem.bankID) {
							drawItemInSlot(bankItem.getItem(), drawX, drawY, false);
							drawStackAmount(bankItem.getItem().getAmount(), drawX, drawY, UI_GREEN);
						}
					}
					if (bankItemHovered && bankItem.getItem().getCatalogID() != -1) {
						drawBankItemHoverGlow(drawX, drawY, bankSlotWidth(), bankSlotHeight());
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
						} else if (leftMouseClick()) {
							selectedBankSlot = bankItem.bankID;
							sendWithdraw(1);
						}
					}

					if (slotHovered && bankItem.getItem().getCatalogID() != -1 && loadoutActionSlot == -1 && !arrangeMenuOpen && mc.inputX_Action == InputXAction.ACT_0) {
						if (rightMouseClick()) {
							selectedBankSlot = bankItem.bankID;
							selectedInventorySlot = -1;
							rightClickMenuX = mc.getMouseX();
							rightClickMenuY = mc.getMouseY();
							rightClickMenu = true;
							consumeMouse();
						}
					}

					if (slotHovered && bankItem.getItem().getCatalogID() != -1 && bankDef != null && bankDef.getName() != null) {
						bankHeaderText = bankDef.getName();
					}

					bankSlotStart++;
				}
			}
		}
		drawGridLines(bankGridX(), bankGridY(), BANK_COLUMNS, BANK_ROWS);
		if (!searchItem.isEmpty() && searchList.isEmpty()) {
			drawCenteredString("No matching items", x + width / 2, bankGridY() + 83, 1, UI_GOLD);
		}

		int settingsY = actionRowY();
		int buttonH = actionButtonHeight();
		drawActionStrip(settingsY, buttonH);
		int inventoryLabelIconSize = 18;
		int inventoryLabelIconX = bankGridX() + 10;
		int inventoryLabelIconY = settingsY + (buttonH - inventoryLabelIconSize) / 2;
		drawSkinSprite("top-bag-24.png", inventoryLabelIconX, inventoryLabelIconY, inventoryLabelIconSize, inventoryLabelIconSize);
		drawString("INVENTORY", inventoryLabelIconX + inventoryLabelIconSize + 8, settingsY + 15, 1, UI_GOLD);

		int depositX = depositButtonX();
		if (buttonClicked(depositX, settingsY, depositButtonWidth(), buttonH) && loadoutActionSlot == -1 && !arrangeMenuOpen && !rightClickMenu) {
			sendDepositAllInventory();
			consumeMouse();
		}
		drawButton(depositX, settingsY, depositButtonWidth(), buttonH, "Deposit", false, true, "bank-deposit-arrow-16.png");

		int arrangeX = arrangeButtonX();
		if (buttonClicked(arrangeX, settingsY, arrangeButtonWidth(), buttonH) && loadoutActionSlot == -1 && !rightClickMenu) {
			arrangeMenuOpen = !arrangeMenuOpen;
			consumeMouse();
		}
		drawButton(arrangeX, settingsY, arrangeButtonWidth(), buttonH, "Arrange " + organizeModeName(), arrangeMenuOpen || organizeMode > 0, true);

		if (arrangeMenuOpen) {
			renderArrangeMenu(arrangeX, settingsY - 72);
		}

		int noteWidth = noteButtonWidth();
		int itemWidth = noteButtonWidth();
		int noteX = noteModeButtonX();
		int itemX = itemModeButtonX();
		if (buttonClicked(itemX, settingsY, itemWidth, buttonH) && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
			swapNoteMode = false;
			consumeMouse();
		}
		drawButton(itemX, settingsY, itemWidth, buttonH, "Item", !swapNoteMode, true);
		if (buttonClicked(noteX, settingsY, noteWidth, buttonH) && !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen) {
			swapNoteMode = true;
			consumeMouse();
		}
		drawButton(noteX, settingsY, noteWidth, buttonH, S_WANT_CERT_AS_NOTES ? "Note" : "Cert", swapNoteMode, true);

		boolean inventoryClickHandled = handleInventorySlotClick();
		drawGridPanel(bankGridX(), inventoryGridY(), bankGridWidth(), inventoryGridHeight());
		int inventorySlot = 0;
		int inventoryDrawY = inventoryGridY();
		for (int verticalSlots = 0; verticalSlots < 3; verticalSlots++) {
			for (int horizonalSlots = 0; horizonalSlots < 10; horizonalSlots++) {

				int drawX = bankGridX() + horizonalSlots * bankSlotWidth();
				int drawY = inventoryDrawY + verticalSlots * bankSlotHeight();

				boolean inventoryHasItem = inventorySlot < mc.getInventoryItemCount()
					&& mc.getInventoryItemID(inventorySlot) != -1;
				drawSlot(drawX, drawY, bankSlotWidth(), bankSlotHeight(), UI_SLOT,
					inventoryHasItem,
					isInside(drawX, drawY, bankSlotWidth(), bankSlotHeight())
						&& !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen);

				if (draggingInventoryID != -1
					&& (mc.getInventoryItemAmount(draggingInventoryID) != -1)) {
					drawItemSprite(mc.getInventoryItem(draggingInventoryID), mc.getMouseX(), mc.getMouseY(), true);
				}

				if (inventorySlot < mc.getInventoryItemCount() && mc.getInventoryItemID(inventorySlot) != -1) {
					ItemDef def = mc.getInventoryItem(inventorySlot).getItemDef();
					drawItemInSlot(mc.getInventoryItem(inventorySlot), drawX, drawY, false);
					if (def.isStackable() || mc.getInventoryItem(inventorySlot).getNoted()) {
						drawStackAmount(mc.getInventoryItemAmount(inventorySlot), drawX, drawY, UI_CYAN);
					}
				}

				if (isInside(drawX, drawY, bankSlotWidth(), bankSlotHeight())
					&& !rightClickMenu && loadoutActionSlot == -1 && !arrangeMenuOpen && mc.inputX_Action == InputXAction.ACT_0) {
					if (!inventoryClickHandled && rightMouseClick()) {
						if (inventorySlot < mc.getInventoryItemCount()
							&& mc.getInventoryItemID(inventorySlot) != -1) {
							selectedInventorySlot = inventorySlot;
							selectedBankSlot = -1;
							rightClickMenuX = mc.getMouseX();
							rightClickMenuY = mc.getMouseY();
							rightClickMenu = true;
							consumeMouse();
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
					}
				}

				if (isInside(drawX, drawY, bankSlotWidth(), bankSlotHeight())) {
					if (mc.getInventoryItemID(inventorySlot) != -1) {
						bankHeaderText = EntityHandler.getItemDef(mc.getInventoryItemID(inventorySlot), mc.getInventory()[inventorySlot].getNoted()).getName();
					}
				}
				inventorySlot++;
			}
		}
		drawGridLines(bankGridX(), inventoryGridY(), BANK_COLUMNS, 3);

		bank.hide(bankSearch);
		bank.drawPanel();
		bank.show(bankSearch);
		drawSearchText(rawSearchItem);
		drawSearchClearButton(rawSearchItem);
		drawFittedString(bankHeaderText, x + 132, y + 31, closeButtonX - x - 140, 1, 0xFFFFFF);

		if (loadoutActionSlot != -1) {
			renderLoadoutsPanel();
		}
		if (rightClickMenu && mc.inputX_Action == InputXAction.ACT_0) {
			renderContextMenu();
		}
		return true;
	}

	private boolean handleInventorySlotClick() {
		if (rightClickMenu || loadoutActionSlot != -1 || arrangeMenuOpen
			|| organizeMode > 0 || mc.inputX_Action != InputXAction.ACT_0) {
			return false;
		}
		boolean rightClick = rightMouseClick();
		if (!leftMouseClick() && !rightClick) {
			return false;
		}
		int slot = inventorySlotAtMouse();
		if (slot < 0 || slot >= mc.getInventoryItemCount() || mc.getInventoryItemID(slot) == -1) {
			return false;
		}
		if (rightClick) {
			selectedInventorySlot = slot;
			selectedBankSlot = -1;
			rightClickMenuX = mc.getMouseX();
			rightClickMenuY = mc.getMouseY();
			rightClickMenu = true;
			consumeMouse();
			return true;
		}
		selectedInventorySlot = slot;
		sendDeposit(1);
		return true;
	}

	private int inventorySlotAtMouse() {
		int mouseX = mc.getMouseX();
		int mouseY = mc.getMouseY();
		if (!isInside(bankGridX(), inventoryGridY(), bankGridWidth(), inventoryGridHeight())) {
			return -1;
		}
		int column = (mouseX - bankGridX()) / bankSlotWidth();
		int row = (mouseY - inventoryGridY()) / bankSlotHeight();
		if (column < 0 || column >= BANK_COLUMNS || row < 0 || row >= 3) {
			return -1;
		}
		return row * BANK_COLUMNS + column;
	}

	private void drawButton(int bx, int by, int bw, int bh, String label, boolean active, boolean enabled) {
		drawButton(bx, by, bw, bh, label, active, enabled, null);
	}

	private void drawButton(int bx, int by, int bw, int bh, String label, boolean active, boolean enabled, String iconAsset) {
		boolean hover = enabled && isInside(bx, by, bw, bh);
		String frame = active || hover ? "bank-button-active.png" : "bank-button-normal.png";
		drawSkinSprite(frame, bx, by, bw, bh);
		if (!enabled) {
			mc.getSurface().drawBoxAlpha(bx + 3, by + 3, bw - 6, bh - 6, 0x000000, 96);
		}

		int iconSize = iconAsset == null ? 0 : Math.min(18, bh - 7);
		int buttonFont = mc.getSurface().stringWidth(1, label) <= bw - 10 - iconSize ? 1 : 0;
		String fitted = fitText(label, bw - 12 - (iconSize == 0 ? 0 : iconSize + 5), buttonFont);
		int labelWidth = mc.getSurface().stringWidth(buttonFont, fitted);
		int groupWidth = labelWidth + (iconSize == 0 ? 0 : iconSize + 5);
		int startX = bx + Math.max(5, (bw - groupWidth) / 2);
		if (iconSize > 0) {
			drawSkinSprite(iconAsset, startX, by + (bh - iconSize) / 2, iconSize, iconSize);
			startX += iconSize + 5;
		}
		int textY = by + Math.max(mc.getSurface().fontHeight(buttonFont) + 1,
			(bh + mc.getSurface().fontHeight(buttonFont)) / 2 - 1);
		drawString(fitted, startX, textY, buttonFont, enabled ? (active ? UI_GOLD : 0xFFFFFF) : UI_DISABLED);
	}

	private void drawBankTab(int bx, int by, int bw, int bh, String label, boolean active) {
		boolean hover = isInside(bx, by, bw, bh);
		drawSkinSprite(active || hover ? "bank-tab-active.png" : "bank-tab-normal.png", bx, by, bw, bh);
		int font = mc.getSurface().stringWidth(1, label) <= bw - 8 ? 1 : 0;
		String fitted = fitText(label, bw - 8, font);
		int textX = bx + Math.max(3, (bw - mc.getSurface().stringWidth(font, fitted)) / 2);
		int textY = by + Math.max(mc.getSurface().fontHeight(font) + 1,
			(bh + mc.getSurface().fontHeight(font)) / 2 - 1);
		drawString(fitted, textX, textY, font, active ? UI_GOLD : 0xFFFFFF);
	}

	private void drawFrame(int fx, int fy, int fw, int fh) {
		boolean mainBankFrame = fx == x && fy == y;
		mc.getSurface().drawBoxAlpha(fx + 10, fy + 10, fw - 20, fh - 20,
			UI_SLOT,
			mainBankFrame ? UI_PANEL_BODY_ALPHA : UI_MENU_ALPHA);
		if (fx == x && fy == y) {
			int topSectionH = Math.max(30, bankGridYOffset() - 50);
			mc.getSurface().drawBoxAlpha(fx + 18, fy + 46, fw - 36, topSectionH, UI_SLOT, UI_PANEL_BODY_ALPHA);
			int inventorySectionY = actionRowY() - 12;
			int inventorySectionH = Math.max(84, fy + fh - inventorySectionY - 28);
			mc.getSurface().drawBoxAlpha(fx + 18, inventorySectionY, fw - 36, inventorySectionH, UI_SLOT, UI_PANEL_BODY_ALPHA);
		}
		drawSkinSprite("bank-panel-frame.png", fx, fy, fw, fh);
	}

	private void drawGridPanel(int gx, int gy, int gw, int gh) {
		mc.getSurface().drawBoxAlpha(gx, gy, gw, gh, UI_SLOT, UI_PANEL_BODY_ALPHA);
	}

	private void drawActionStrip(int ay, int ah) {
		int gx = bankGridX();
		int gw = bankGridWidth();
		int barY = ay - 5;
		int barH = ah + 10;
		mc.getSurface().drawBoxAlpha(gx, barY, gw, barH, UI_TOOLBAR_BG, UI_TOOLBAR_ALPHA);
		mc.getSurface().drawBoxAlpha(gx + 1, barY + 1, gw - 2, barH - 2, UI_SLOT, UI_PANEL_BODY_ALPHA);
		mc.getSurface().drawLineAlpha(gx, barY, gx + gw - 1, barY, UI_GRID_LINE, UI_TOOLBAR_DIVIDER_ALPHA);
		mc.getSurface().drawLineAlpha(gx, barY + barH - 1, gx + gw - 1, barY + barH - 1, UI_GRID_LINE, UI_TOOLBAR_DIVIDER_ALPHA);
		drawToolbarDivider(depositButtonX() - 10, barY + 3, barH - 6);
		drawToolbarDivider(itemModeButtonX() - 8, barY + 3, barH - 6);
	}

	private void drawToolbarDivider(int dx, int dy, int dh) {
		mc.getSurface().drawLineAlpha(dx, dy, dx, dy + dh - 1, UI_GRID_LINE, UI_TOOLBAR_DIVIDER_ALPHA);
	}

	private void drawSlot(int sx, int sy, int sw, int sh, int fill, boolean hover) {
		drawSlot(sx, sy, sw, sh, fill, false, hover);
	}

	private void drawSlot(int sx, int sy, int sw, int sh, int fill, boolean occupied, boolean hover) {
		mc.getSurface().drawBoxAlpha(sx, sy, sw, sh, UI_SLOT, hover ? UI_SLOT_HOVER_ALPHA : UI_SLOT_ALPHA);
	}

	private void drawBankItemHoverGlow(int sx, int sy, int sw, int sh) {
		int pulseStep = (int)((System.currentTimeMillis() / 85L) % 16L);
		int pulse = pulseStep < 8 ? pulseStep : 15 - pulseStep;
		int fillAlpha = 18 + pulse * 2;
		int lineAlpha = 92 + pulse * 8;
		mc.getSurface().drawBoxAlpha(sx + 1, sy + 1, sw - 2, sh - 2, UI_HOVER_GLOW, fillAlpha);
		mc.getSurface().drawLineAlpha(sx, sy, sx + sw - 1, sy, UI_HOVER_GLOW, lineAlpha);
		mc.getSurface().drawLineAlpha(sx, sy + sh - 1, sx + sw - 1, sy + sh - 1, UI_HOVER_GLOW, lineAlpha);
		mc.getSurface().drawLineAlpha(sx, sy, sx, sy + sh - 1, UI_HOVER_GLOW, lineAlpha);
		mc.getSurface().drawLineAlpha(sx + sw - 1, sy, sx + sw - 1, sy + sh - 1, UI_HOVER_GLOW, lineAlpha);
	}

	private void drawGridLines(int gx, int gy, int columns, int rows) {
		int cellW = bankSlotWidth();
		int cellH = bankSlotHeight();
		for (int column = 1; column < columns; column++) {
			mc.getSurface().drawLineVert(gx + column * cellW, gy, UI_GRID_LINE, rows * cellH);
		}
		for (int row = 1; row < rows; row++) {
			mc.getSurface().drawLineHoriz(gx, gy + row * cellH, columns * cellW, UI_GRID_LINE);
		}
	}

	private void drawSkinSprite(String asset, int sx, int sy, int sw, int sh) {
		mc.drawVoidscapeUiSkinSprite(asset, sx, sy, sw, sh);
	}

	private String sizedSkinAsset(String asset, int size) {
		return mc.voidscapeSizedUiSkinAsset(asset, size);
	}

	private void drawCloseButton(int closeButtonX, int closeButtonY, boolean hover) {
		drawSkinSprite("bank-close.png", closeButtonX, closeButtonY, 24, 24);
		if (hover) {
			mc.getSurface().drawBoxBorder(closeButtonX + 3, 18, closeButtonY + 3, 18, UI_GOLD);
		}
	}

	private void drawSearchFrame() {
		drawSkinSprite("bank-search-frame.png", searchFrameX(), searchFrameY(), searchFrameWidth(), searchFrameHeight());
		if (bank.focusOn(bankSearch)) {
			mc.getSurface().drawBoxBorder(searchFrameX() + 2, searchFrameWidth() - 4, searchFrameY() + 2, searchFrameHeight() - 4, UI_GOLD);
		}
		drawSkinSprite("bank-search-18.png", searchFrameX() + searchFrameWidth() - 24, searchFrameY() + (searchFrameHeight() - 18) / 2, 18, 18);
	}

	private boolean handleSearchClick(String rawSearchItem) {
		if (!buttonClicked(searchFrameX(), searchFrameY(), searchFrameWidth(), searchFrameHeight())
			|| rightClickMenu || loadoutActionSlot != -1 || arrangeMenuOpen) {
			return false;
		}

		mc.bankPage = 0;
		bank.setFocus(bankSearch);
		if (Config.isAndroid() && !orsc.osConfig.F_SHOWING_KEYBOARD && mudclient.clientPort != null) {
			mudclient.clientPort.drawKeyboard();
		}
		if (rawSearchItem != null && !rawSearchItem.isEmpty()
			&& isInside(searchClearHitX(), searchClearHitY(), searchClearHitWidth(), searchClearHitHeight())) {
			bank.setText(bankSearch, "");
			lastBankSearchText = "";
			bank.resetListToIndex(bankScroll, 0);
			if (Config.isAndroid()) {
				bank.setFocus(-1);
				if (orsc.osConfig.F_SHOWING_KEYBOARD && mudclient.clientPort != null) {
					mudclient.clientPort.closeKeyboard();
				}
			}
		}
		consumeMouse();
		return true;
	}

	private void drawStackAmount(int amount, int sx, int sy, int color) {
		if (amount <= 1) {
			return;
		}
		String text = mudclient.formatStackAmount(amount);
		int chipX = sx + 2;
		int baseline = sy + bankSlotHeight() - 5;
		drawString(text, chipX + 2, baseline, 0, color);
	}

	private void drawItemInSlot(Item item, int drawX, int drawY, boolean dragging) {
		drawItemSprite(item, drawX + (bankSlotWidth() - 48) / 2, drawY + (bankSlotHeight() - 32) / 2, dragging);
	}

	private void drawCenteredString(String label, int cx, int cy, int font, int color) {
		drawString(label, cx - mc.getSurface().stringWidth(font, label) / 2, cy, font, color);
	}

	private void drawFittedString(String label, int tx, int ty, int maxWidth, int font, int color) {
		drawString(fitText(label, maxWidth, font), tx, ty, font, color);
	}

	private String fitText(String label, int maxWidth, int font) {
		if (label == null) {
			return "";
		}
		if (maxWidth <= 0 || mc.getSurface().stringWidth(font, label) <= maxWidth) {
			return label;
		}
		String suffix = "...";
		int suffixWidth = mc.getSurface().stringWidth(font, suffix);
		if (suffixWidth >= maxWidth) {
			return "";
		}
		String trimmed = label;
		while (trimmed.length() > 0 && mc.getSurface().stringWidth(font, trimmed) + suffixWidth > maxWidth) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		return trimmed + suffix;
	}

	private int loadoutButtonX() {
		return searchFrameX() - loadoutButtonWidth() - 12;
	}

	private int loadoutButtonY() {
		return controlRowY();
	}

	private int loadoutButtonWidth() {
		return spaciousLayout() ? 122 : 108;
	}

	private int loadoutButtonHeight() {
		return actionButtonHeight();
	}

	private int searchFrameX() {
		return contentRight() - searchFrameWidth();
	}

	private int searchFrameY() {
		return controlRowY();
	}

	private int searchFrameWidth() {
		return spaciousLayout() ? 184 : 150;
	}

	private int searchFrameHeight() {
		return actionButtonHeight();
	}

	private int searchFieldX() {
		return searchFrameX() + 10;
	}

	private int searchFieldY() {
		return searchFrameY() + 3;
	}

	private int searchFieldWidth() {
		return searchFrameWidth() - 38;
	}

	private int searchFieldHeight() {
		return searchFrameHeight() - 6;
	}

	private int searchClearX() {
		return searchFrameX() + searchFrameWidth() - 20;
	}

	private int searchClearHitX() {
		return searchClearX() - 5;
	}

	private int searchClearHitY() {
		return searchFrameY();
	}

	private int searchClearHitWidth() {
		return 22;
	}

	private int searchClearHitHeight() {
		return searchFrameHeight();
	}

	private int depositButtonX() {
		return bankGridX() + (spaciousLayout() ? 176 : 150);
	}

	private int depositButtonWidth() {
		return spaciousLayout() ? 134 : 112;
	}

	private int arrangeButtonX() {
		return depositButtonX() + depositButtonWidth() + (spaciousLayout() ? 14 : 8);
	}

	private int arrangeButtonWidth() {
		return spaciousLayout() ? 126 : 110;
	}

	private int noteButtonWidth() {
		return spaciousLayout() ? 54 : 46;
	}

	private int noteModeButtonX() {
		return bankGridX() + bankGridWidth() - noteButtonWidth();
	}

	private int itemModeButtonX() {
		return noteModeButtonX() - noteButtonWidth() - (spaciousLayout() ? 4 : 3);
	}

	private void resetAndroidBankSwipeScroll() {
		androidBankSwipeLastY = Integer.MIN_VALUE;
		androidBankSwipeRemainder = 0;
		androidBankSwipeScrolling = false;
	}

	private int clampedBankScrollRow() {
		int maxScrollRow = Math.max(0, bank.controlListCurrentSize[bankScroll] - BANK_ROWS);
		int scrollRow = Math.max(0, Math.min(maxScrollRow, bank.getScrollPosition(bankScroll)));
		if (scrollRow != bank.getScrollPosition(bankScroll)) {
			bank.resetListToIndex(bankScroll, scrollRow);
		}
		return scrollRow;
	}

	private void handleAndroidBankSwipeScroll() {
		if (!androidLayout() || mc.bankPage != 0 || rightClickMenu || loadoutActionSlot != -1 || arrangeMenuOpen) {
			resetAndroidBankSwipeScroll();
			return;
		}

		int maxScroll = Math.max(0, bank.controlListCurrentSize[bankScroll] - BANK_ROWS);
		if (maxScroll <= 0) {
			resetAndroidBankSwipeScroll();
			return;
		}

		if (mc.getMouseButtonDown() == 1) {
			if (androidBankSwipeLastY == Integer.MIN_VALUE) {
				if (isInside(bankGridX(), bankGridY(), bankGridWidth(), bankGridHeight())) {
					androidBankSwipeLastY = mc.getMouseY();
					androidBankSwipeRemainder = 0;
					androidBankSwipeScrolling = false;
				}
				return;
			}

			int dy = mc.getMouseY() - androidBankSwipeLastY;
			androidBankSwipeLastY = mc.getMouseY();
			if (dy == 0) {
				return;
			}

			androidBankSwipeRemainder -= dy;
			int threshold = Math.max(8, bankSlotHeight() / 2);
			int rows = 0;
			while (androidBankSwipeRemainder >= threshold) {
				rows++;
				androidBankSwipeRemainder -= threshold;
			}
			while (androidBankSwipeRemainder <= -threshold) {
				rows--;
				androidBankSwipeRemainder += threshold;
			}
			if (rows == 0) {
				return;
			}

			int before = bank.getScrollPosition(bankScroll);
			int after = Math.max(0, Math.min(maxScroll, before + rows));
			if (after != before) {
				bank.resetListToIndex(bankScroll, after);
				androidBankSwipeScrolling = true;
			}
			return;
		}

		if (androidBankSwipeScrolling && mc.getLastMouseDown() == 1) {
			consumeMouse();
		}
		resetAndroidBankSwipeScroll();
	}

	private boolean buttonClicked(int bx, int by, int bw, int bh) {
		return mc.inputX_Action == InputXAction.ACT_0 && leftMouseClick() && isInside(bx, by, bw, bh);
	}

	private boolean leftMouseClick() {
		return mc.getMouseClick() == 1 || mc.mouseButtonClick == 1;
	}

	private boolean rightMouseClick() {
		return mc.getMouseClick() == 2 || mc.mouseButtonClick == 2;
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
		mc.setMouseButtonDown(0);
		mc.lastMouseButtonDown = 0;
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
		int menuW = arrangeButtonWidth();
		int rowH = actionButtonHeight();
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
			&& !isInside(menuX, actionRowY(), menuW, actionButtonHeight())) {
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
		logAndroidSmokeBankLoadoutsPanel(panelX, panelY);

		drawFrame(panelX, panelY, panelW, panelH);
		drawString("LOADOUTS", panelX + 11, panelY + 15, 1, UI_GOLD);
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
			mc.getSurface().drawBoxAlpha(cardX, cardY, cardW, 36, empty ? 0x0F0D12 : 0x181028, 224);
			mc.getSurface().drawBoxBorder(cardX, cardW, cardY, 36, empty ? UI_PURPLE_DARK : UI_PURPLE);
			drawString("Loadout " + (slot + 1), cardX + 8, cardY + 13, 1, 0xFFFFFF);
			drawString(empty ? "Empty" : "Saved", cardX + 8, cardY + 28, 1, empty ? UI_MUTED : UI_CYAN);

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
		} else {
			rightClickMenu = false;
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
		ArrayList<String> rows = new ArrayList<String>();
		if (bankItem.getItem().getItemDef().isWieldable()) {
			rows.add("Wield");
		}
		rows.add("Withdraw-1");
		rows.add("Withdraw-5");
		rows.add("Withdraw-10");
		rows.add("Withdraw-50");
		if (lastXAmount > 1 && lastXAmount != 5 && lastXAmount != 10 && lastXAmount != 50) {
			rows.add("Withdraw-" + lastXAmount);
		}
		rows.add("Withdraw-X");
		rows.add("Withdraw-All");
		rows.add("Withdraw-All-But-1");
		int menuWidth = contextMenuWidth(name, rows, 132);
		int rowH = contextMenuRowHeight();
		int menuHeight = 23 + rows.size() * rowH + 4;
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
			closeContextMenuPreservingSelection();
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
		ArrayList<String> rows = new ArrayList<String>();
		rows.add("Deposit-1");
		rows.add("Deposit-5");
		rows.add("Deposit-10");
		rows.add("Deposit-50");
		rows.add("Deposit-X");
		rows.add("Deposit-All");
		if (wantCertDeposit) {
			rows.add("Uncert+Deposit-X");
			rows.add("Uncert+Deposit-All");
		}
		int menuWidth = contextMenuWidth(name, rows, wantCertDeposit ? 154 : 124);
		int rowH = contextMenuRowHeight();
		int menuHeight = 23 + rows.size() * rowH + 4;
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
			closeContextMenuPreservingSelection();
		}
		if (drawMenuRow("Deposit-All", row++, menuWidth, rowH)) sendDeposit(Integer.MAX_VALUE);
		if (wantCertDeposit) {
			if (drawMenuRow("Uncert+Deposit-X", row++, menuWidth, rowH)) {
				tryChangeCertMode(true);
				mc.showItemModX(InputXPrompt.bankDepositX, InputXAction.BANK_DEPOSIT, true);
				closeContextMenuPreservingSelection();
			}
			if (drawMenuRow("Uncert+Deposit-All", row, menuWidth, rowH)) sendDeposit(Integer.MAX_VALUE, true);
		}
	}

	private void positionContextMenu(int menuWidth, int menuHeight) {
		if (rightClickMenuX + menuWidth >= mc.getGameWidth()) {
			rightClickMenuX = mc.getGameWidth() - menuWidth - 5;
		}
		int bottomLimit = contextMenuBottomLimit();
		if (rightClickMenuY + menuHeight >= bottomLimit) {
			rightClickMenuY = Math.max(5, bottomLimit - menuHeight - 5);
		}
	}

	private int contextMenuBottomLimit() {
		if (C_CUSTOM_UI && !Config.isAndroid()) {
			return mc.getGameHeight() - 42;
		}
		return mc.getGameHeight();
	}

	private void drawMenuShell(int menuWidth, int menuHeight, String title) {
		mc.getSurface().drawBoxAlpha(rightClickMenuX, rightClickMenuY, menuWidth, menuHeight, UI_SLOT, UI_MENU_ALPHA);
		mc.getSurface().drawBoxAlpha(rightClickMenuX + 1, rightClickMenuY + 1, menuWidth - 2, 18,
			UI_SLOT, UI_MENU_HEADER_ALPHA);
		drawMenuBorder(rightClickMenuX, rightClickMenuY, menuWidth, menuHeight, UI_GRID_LINE, UI_MENU_LINE_ALPHA);
		mc.getSurface().drawLineAlpha(rightClickMenuX + 3, rightClickMenuY + 20,
			rightClickMenuX + menuWidth - 4, rightClickMenuY + 20, UI_PURPLE, 96);
		drawFittedString(title, rightClickMenuX + 5, rightClickMenuY + 14, menuWidth - 10, fontSize, UI_GOLD);
	}

	private boolean drawMenuRow(String label, int row, int menuWidth, int rowH) {
		int rowTop = rightClickMenuY + 23 + row * rowH;
		boolean hover = isInside(rightClickMenuX, rowTop, menuWidth, rowH);
		if (hover) {
			mc.getSurface().drawBoxAlpha(rightClickMenuX + 2, rowTop, menuWidth - 4, rowH,
				UI_HOVER_GLOW, UI_MENU_HOVER_ALPHA);
			mc.getSurface().drawLineAlpha(rightClickMenuX + 4, rowTop + rowH - 1,
				rightClickMenuX + menuWidth - 5, rowTop + rowH - 1, UI_HOVER_GLOW, UI_MENU_LINE_ALPHA);
		}
		drawString(label, rightClickMenuX + 5, rowTop + Math.max(fontSizeHeight, (rowH + fontSizeHeight) / 2 - 1),
			fontSize, hover ? UI_GOLD : 0xFFFFFF);
		if (hover && mc.getMouseClick() == 1) {
			consumeMouse();
			return true;
		}
		return false;
	}

	private void drawMenuBorder(int bx, int by, int bw, int bh, int color, int alpha) {
		mc.getSurface().drawLineAlpha(bx, by, bx + bw - 1, by, color, alpha);
		mc.getSurface().drawLineAlpha(bx, by + bh - 1, bx + bw - 1, by + bh - 1, color, alpha);
		mc.getSurface().drawLineAlpha(bx, by, bx, by + bh - 1, color, alpha);
		mc.getSurface().drawLineAlpha(bx + bw - 1, by, bx + bw - 1, by + bh - 1, color, alpha);
	}

	private int contextMenuWidth(String title, ArrayList<String> rows, int minWidth) {
		int menuWidth = Math.max(minWidth, mc.getSurface().stringWidth(fontSize, title) + 14);
		for (String row : rows) {
			menuWidth = Math.max(menuWidth, mc.getSurface().stringWidth(fontSize, row) + 14);
		}
		return menuWidth;
	}

	private int contextMenuRowHeight() {
		return Math.max(fontSizeHeight + 3, 14);
	}

	private void closeContextMenu() {
		rightClickMenu = false;
		selectedBankSlot = -1;
		selectedInventorySlot = -1;
		consumeMouse();
	}

	private void closeContextMenuPreservingSelection() {
		rightClickMenu = false;
		consumeMouse();
	}

	private void drawSearchClearButton(String rawSearchItem) {
		if (rawSearchItem == null || rawSearchItem.isEmpty()) {
			if (!bank.focusOn(bankSearch)) drawString("Search bank...", searchFieldX(), searchFieldY() + 13, 1, UI_DISABLED);
			return;
		}
		boolean hover = isInside(searchClearHitX(), searchClearHitY(), searchClearHitWidth(), searchClearHitHeight());
		drawString("x", searchClearX() + 3, searchFieldY() + 13, 1, hover ? 0xFDFF21 : 0xFFFFFF);
	}

	private void drawSearchText(String rawSearchItem) {
		String searchText = rawSearchItem == null ? "" : rawSearchItem;
		boolean focused = bank.focusOn(bankSearch);
		if (searchText.isEmpty() && !focused) {
			return;
		}

		String displayText = searchText + (focused ? "*" : "");
		drawFittedString(displayText, searchFieldX(), searchFieldY() + 13, searchFieldWidth() - 2, 1,
			searchText.isEmpty() ? UI_MUTED : 0xFFFFFF);
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
			int originalSlot = selectedInventorySlot;
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
			logAndroidSmokeBankAction("DEPOSIT", itemID, i, originalSlot);
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
		logAndroidSmokeBankAction("DEPOSIT_ALL", -1, Integer.MAX_VALUE, -1);
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
			int originalSlot = selectedBankSlot;
			BankItem selectedItem = bankItems.get(selectedBankSlot);
			if (selectedItem == null || selectedItem.getItem() == null || selectedItem.getItem().getCatalogID() < 0) {
				return;
			}
			int catalogID = selectedItem.getItem().getCatalogID();
			if (i > selectedItem.getItem().getAmount()) {
				i = selectedItem.getItem().getAmount();
			}
			if (i <= 0) {
				return;
			}
			mc.packetHandler.getClientStream().newPacket(22);
			mc.packetHandler.getClientStream().bufferBits.putShort(catalogID);
			mc.packetHandler.getClientStream().bufferBits.putInt(i);

			if (Config.S_WANT_BANK_NOTES)
				mc.packetHandler.getClientStream().bufferBits.putByte(swapNoteMode ? 1 : 0);

			mc.packetHandler.getClientStream().finishPacket();
			logAndroidSmokeBankAction("WITHDRAW", catalogID, i, originalSlot);
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
				resetVar();
				bankClose();
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
		lastBankSearchText = "";
		swapNoteMode = false;
		rightClickMenu = false;
		selectedBankSlot = -1;
		selectedInventorySlot = -1;
		arrangeMenuOpen = false;
		loadoutActionSlot = -1;
		draggingBankSlot = -1;
		draggingInventoryID = -1;
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

		mc.getSurface().drawBoxAlpha(0, 0, mc.getGameWidth(), mc.getGameHeight(), 0, 100);
		drawFrame(modalX, modalY, modalW, modalH);
		drawCenteredString("SAVE LOADOUT " + (slot + 1) + "?", modalX + modalW / 2, modalY + 15, 1, UI_GOLD);

		drawString("Inventory",
			modalX + 22, modalY + 38, 1, UI_GOLD);

		int gridX = modalX + (modalW - 6 * 49) / 2;
		int gridY = modalY + 48;
		int invSlot = 0;
		for (int r = 0; r < 5; r++) {
			for (int c = 0; c < 6; c++) {
				int dx = gridX + c * 49;
				int dy = gridY + r * 34;
				drawSlot(dx, dy, 49, 34, UI_SLOT, false);
				if (invSlot < mc.getInventoryItemCount() && mc.getInventoryItemID(invSlot) != -1) {
					ItemDef def = mc.getInventoryItem(invSlot).getItemDef();
					drawItemSprite(mc.getInventoryItem(invSlot), dx, dy, false);
					if (def.isStackable() || mc.getInventoryItem(invSlot).getNoted()) {
						drawStackAmount(mc.getInventoryItemAmount(invSlot), dx, dy, UI_CYAN);
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
		logAndroidSmokeBankSaveConfirm(slot, saveX + btnW / 2, btnY + btnH / 2);

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

		mc.getSurface().drawBoxAlpha(0, 0, mc.getGameWidth(), mc.getGameHeight(), 0, 100);
		drawFrame(modalX, modalY, modalW, modalH);
		drawCenteredString("CLEAR LOADOUT " + (slot + 1) + "?", modalX + modalW / 2, modalY + 15, 1, UI_GOLD);
		drawCenteredString("Remove the saved inventory setup.", modalX + modalW / 2, modalY + 45, 1, UI_MUTED);

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
		logAndroidSmokeBankAction("SAVE_PRESET", -1, 0, slot);
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
		logAndroidSmokeBankAction("LOAD_PRESET", -1, 0, slot);
	}

	public int workbenchBankItemCount() {
		return bankItems.size();
	}

	public int workbenchBankItemCatalogId(int index) {
		Item item = workbenchBankItem(index);
		return item == null ? -1 : item.getCatalogID();
	}

	public int workbenchBankItemAmount(int index) {
		Item item = workbenchBankItem(index);
		return item == null ? 0 : item.getAmount();
	}

	public boolean workbenchBankItemNoted(int index) {
		Item item = workbenchBankItem(index);
		return item != null && item.getNoted();
	}

	public String workbenchBankItemName(int index) {
		Item item = workbenchBankItem(index);
		ItemDef def = item == null ? null : item.getItemDef();
		return def == null ? null : def.getName();
	}

	public String workbenchSearchText() {
		String text = bank.getControlText(bankSearch);
		return text == null ? "" : text;
	}

	public boolean workbenchSearchFocused() {
		return bank.focusOn(bankSearch);
	}

	public int workbenchSearchMatchCount() {
		String search = workbenchSearchText().trim().toLowerCase(Locale.ROOT);
		if (search.isEmpty()) {
			return bankItems.size();
		}
		int matches = 0;
		for (BankItem item : bankItems) {
			if (item == null || item.getItem() == null) continue;
			ItemDef def = item.getItem().getItemDef();
			if (def != null && def.getName() != null
				&& def.getName().toLowerCase(Locale.ROOT).contains(search)) {
				matches++;
			}
		}
		return matches;
	}

	public int workbenchScrollRow() {
		return bank.getScrollPosition(bankScroll);
	}

	public boolean workbenchNoteMode() {
		return swapNoteMode;
	}

	public boolean workbenchCertMode() {
		return swapCertMode;
	}

	public int workbenchOrganizeMode() {
		return organizeMode;
	}

	public boolean workbenchArrangeMenuOpen() {
		return arrangeMenuOpen;
	}

	public boolean workbenchLoadoutsOpen() {
		return loadoutActionSlot != -1;
	}

	public boolean workbenchContextMenuOpen() {
		return rightClickMenu;
	}

	public int workbenchSelectedBankSlot() {
		return selectedBankSlot;
	}

	public int workbenchSelectedInventorySlot() {
		return selectedInventorySlot;
	}

	public int workbenchContextMenuX() {
		return rightClickMenuX;
	}

	public int workbenchContextMenuY() {
		return rightClickMenuY;
	}

	public int workbenchContextMenuRowTopOffset() {
		return 23;
	}

	public int workbenchContextMenuRowHeight() {
		fontSizeHeight = mc.getSurface().fontHeight(fontSize);
		return contextMenuRowHeight();
	}

	public int workbenchPendingSavePresetSlot() {
		return pendingSavePresetSlot;
	}

	public int workbenchPendingClearPresetSlot() {
		return pendingClearPresetSlot;
	}

	public int workbenchPresetCount() {
		return presetCount;
	}

	public boolean workbenchPresetEmpty(int slot) {
		return isPresetEmpty(slot);
	}

	public int workbenchPanelX() {
		updateLayout();
		return x;
	}

	public int workbenchPanelY() {
		updateLayout();
		return y;
	}

	public int workbenchPanelWidth() {
		updateLayout();
		return width;
	}

	public int workbenchPanelHeight() {
		updateLayout();
		return height;
	}

	public int workbenchBankGridX() {
		updateLayout();
		return bankGridX();
	}

	public int workbenchBankGridY() {
		updateLayout();
		return bankGridY();
	}

	public int workbenchBankSlotWidth() {
		updateLayout();
		return bankSlotWidth();
	}

	public int workbenchBankSlotHeight() {
		updateLayout();
		return bankSlotHeight();
	}

	public int workbenchInventoryGridY() {
		updateLayout();
		return inventoryGridY();
	}

	public int workbenchBankSlotCenterX(int slot) {
		updateLayout();
		return bankGridX() + (slot % BANK_COLUMNS) * bankSlotWidth() + bankSlotWidth() / 2;
	}

	public int workbenchBankSlotCenterY(int slot) {
		updateLayout();
		return bankGridY() + (slot / BANK_COLUMNS) * bankSlotHeight() + bankSlotHeight() / 2;
	}

	public int workbenchInventorySlotCenterX(int slot) {
		updateLayout();
		return bankGridX() + (slot % BANK_COLUMNS) * bankSlotWidth() + bankSlotWidth() / 2;
	}

	public int workbenchInventorySlotCenterY(int slot) {
		updateLayout();
		return inventoryGridY() + (slot / BANK_COLUMNS) * bankSlotHeight() + bankSlotHeight() / 2;
	}

	public int workbenchSearchCenterX() {
		updateLayout();
		return searchFieldX() + searchFieldWidth() / 2;
	}

	public int workbenchSearchCenterY() {
		updateLayout();
		return searchFieldY() + searchFieldHeight() / 2;
	}

	public int workbenchSearchClearCenterX() {
		updateLayout();
		return searchClearHitX() + searchClearHitWidth() / 2;
	}

	public int workbenchSearchClearCenterY() {
		updateLayout();
		return searchClearHitY() + searchClearHitHeight() / 2;
	}

	public int workbenchDepositInventoryCenterX() {
		updateLayout();
		return depositButtonX() + depositButtonWidth() / 2;
	}

	public int workbenchDepositInventoryCenterY() {
		updateLayout();
		return actionRowY() + actionButtonHeight() / 2;
	}

	public int workbenchArrangeCenterX() {
		updateLayout();
		return arrangeButtonX() + arrangeButtonWidth() / 2;
	}

	public int workbenchArrangeCenterY() {
		updateLayout();
		return actionRowY() + actionButtonHeight() / 2;
	}

	public int workbenchLoadoutsCenterX() {
		updateLayout();
		return loadoutButtonX() + loadoutButtonWidth() / 2;
	}

	public int workbenchLoadoutsCenterY() {
		updateLayout();
		return loadoutButtonY() + loadoutButtonHeight() / 2;
	}

	public int workbenchItemModeCenterX() {
		updateLayout();
		return itemModeButtonX() + noteButtonWidth() / 2;
	}

	public int workbenchItemModeCenterY() {
		updateLayout();
		return actionRowY() + actionButtonHeight() / 2;
	}

	public int workbenchNoteModeCenterX() {
		updateLayout();
		return noteModeButtonX() + noteButtonWidth() / 2;
	}

	public int workbenchNoteModeCenterY() {
		updateLayout();
		return actionRowY() + actionButtonHeight() / 2;
	}

	public int workbenchCloseCenterX() {
		updateLayout();
		return x + width - 24;
	}

	public int workbenchCloseCenterY() {
		updateLayout();
		return y + 26;
	}

	private Item workbenchBankItem(int index) {
		if (index < 0 || index >= bankItems.size()) {
			return null;
		}
		BankItem bankItem = bankItems.get(index);
		return bankItem == null ? null : bankItem.getItem();
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
