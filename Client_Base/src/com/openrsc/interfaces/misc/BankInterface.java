package com.openrsc.interfaces.misc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import com.openrsc.client.entityhandling.instances.Item;
import orsc.Config;
import orsc.enumerations.InputXAction;
import orsc.graphics.gui.InputXPrompt;
import orsc.graphics.gui.Panel;
import orsc.graphics.two.Fonts;
import orsc.mudclient;
import orsc.util.BankUtil;

import java.util.ArrayList;
import java.util.Locale;

import static orsc.Config.*;

public class BankInterface {
	public static mudclient mc;

	public int selectedBankSlot = -1;
	private boolean swapNoteMode;
	private boolean swapCertMode;

	public int width, height;
	public boolean membersWorld;
	public Panel bank;
	ArrayList<BankItem> bankItems;

	BankInterface(mudclient m) {
		mc = m;
		width = 408; // WIDTH MODIFIER
		height = 334; // HEIGHT MODIFIER
		membersWorld = wantMembers();
		bank = new Panel(mc.getSurface(), 3);
		bankItems = new ArrayList<>();
	}

	private int selectedBankSlotItemID = -2;
	private int mouseOverBankPageText;
	private ArrayList<Item> currentItems = new ArrayList<>();
	private ArrayList<Integer> currentItemIDs = new ArrayList<>();

	public boolean onRender() {
		int currMouseX = mc.getMouseX();
		int currMouseY = mc.getMouseY();

		if (!mc.getInitLoginCleared()) {
			swapNoteMode = false;
			swapCertMode = false;
			mc.setInitLoginCleared(true);
		}

		// Create current bank state
		currentItems.clear();
		currentItemIDs.clear();
		for (BankItem item : bankItems) {
			// Add bank items
			currentItems.add(item.getItem());
		}
		// Add inventory items
		for (Item item : mc.getInventory()) {
			Integer itemID = item.getCatalogID();
			if (itemID == -1) continue;
			if (getBankItemByID(itemID) != null) continue;
			if (currentItemIDs.contains(itemID)) continue;
			currentItems.add(item);
			currentItemIDs.add(itemID);
		}

		// Voidscape modern "Void Glass" bank (skin on). Preservation / skin-off falls through
		// to the authentic classic bank below.
		if (voidGlassBank()) {
			return renderVoidGlassBank(currMouseX, currMouseY);
		}

		// Set Bank Page
		if (mouseOverBankPageText > 0 && currentItems.size() <= 48)
			mouseOverBankPageText = 0;
		if (mouseOverBankPageText > 1 && currentItems.size() <= 96)
			mouseOverBankPageText = 1;
		if (mouseOverBankPageText > 2 && currentItems.size() <= 144)
			mouseOverBankPageText = 2;
		if (mc.getMouseClick() == 1 || ((mc.getMouseButtonDown() == 1 && mc.getMouseButtonDownTime() > 99999 && Config.isAndroid()) ||
			(mc.getMouseButtonDown() == 1 && mc.getMouseButtonDownTime() > 20 && !Config.isAndroid()))) {
			int selectedX = currMouseX - bankOriginX();
			int selectedY = currMouseY - bankOriginY();
			if (selectedX >= 0 && selectedY >= 16 && selectedX < 408 && selectedY < 280) {
				if (mc.inputX_Action == InputXAction.ACT_0) {
					selectSlot(selectedX, selectedY); // Set the slot we clicked on

					selectedX = bankOriginX();
					selectedY = bankOriginY();
				}

				// Check for a transaction
				if (this.selectedBankSlot > -1) {
					checkTransaction(currMouseX, currMouseY, selectedX, selectedY);
				}

				// Select bank page
			} else if (currentItems.size() > 48 && selectedX >= 50 && selectedX <= 115 &&
				selectedY <= 16 && currMouseY > bankOriginY() + 1 && membersWorld) {
				mouseOverBankPageText = 0; // Select page 1
			} else if (currentItems.size() > 48 && selectedX >= 115 && selectedX <= 180 &&
				selectedY <= 16 && currMouseY > bankOriginY() + 1 && membersWorld) {
				mouseOverBankPageText = 1; // Select page 2
			} else if (currentItems.size() > 96 && selectedX >= 180 && selectedX <= 245 &&
				selectedY <= 16 && currMouseY > bankOriginY() + 1 && membersWorld) {
				mouseOverBankPageText = 2; // Select page 3
			} else if (currentItems.size() > 144 && selectedX >= 245 && selectedX <= 310 &&
				selectedY <= 16 && currMouseY > bankOriginY() + 1 && membersWorld) {
				mouseOverBankPageText = 3; // Select page 4

			} else { // Close Bank
				bankClose();
				return false;
			}
		}

		// Draw the top header
		drawBankComponents(currMouseX, currMouseY);
		return true;
	}

	// ===================== Void Glass bank (voidscape modern skin) =====================
	// Translucent purple GLASS colorway (see-through). Slice 1: centered HUD-safe glass slab
	// + native-icon grid + amounts + left-click withdraw/deposit. Chrome/tabs/search/loadouts
	// come in later slices. Classic bank (skin off) is untouched for preservation.
	private static final int VG_BODY = 0x160B2C, VG_BODY_A = 112;      // glass panel body (more see-through)
	private static final int VG_GRID_LINE = 0x8A6BD8, VG_GRID_LINE_A = 36; // thin subtle grid lines
	private static final int VG_STRIP = 0x2A1A4D, VG_STRIP_A = 55;     // title chrome strip (over body)
	private static final int VG_HAIRLINE = 0x7C63C0;                   // region dividers
	private static final int VG_FRAME_INNER = 0x8A6BD8;               // lavender inner rim
	private static final int VG_SLOT = 0x3A2A5E, VG_SLOT_A = 60;       // slot glass
	private static final int VG_SLOT_BORDER = 0x6E5CA8;               // slot hairline border
	private static final int VG_HOVER = 0x8F2BFF, VG_HOVER_A = 70;     // hover bloom
	private static final int VG_HOVER_BORDER = 0xC35CFF;
	private static final int VG_SEL_RING = 0xF6DA7D;                  // gold selected ring
	private static final int VG_TITLE_TEXT = 0xF2ECFF, VG_LABEL = 0xB6A6D6;
	private static final int VG_GREEN = 0x54FF6A, VG_CYAN = 0x66E0FF, VG_GOLD = 0xF6DA7D;
	private static final int VG_INNER_PAD = 10, VG_SCROLL_GUTTER = 8, VG_SIDE_MARGIN = 8, VG_TITLE_H = 24;
	private static final int VG_MENU_CARD = 0x160B28, VG_MENU_ROW_H = 15;
	private static final int VG_X = -2, VG_ALL = Integer.MAX_VALUE;
	private boolean vgMenu;
	private int vgMenuKind = 0; // 0 = item quantity menu, 1 = loadout actions
	private int vgMenuPresetSlot = -1;
	private int vgMenuX, vgMenuY, vgMenuSlot, vgMenuItemID;
	private final ArrayList<String> vgMenuLabels = new ArrayList<>();
	private final ArrayList<Boolean> vgMenuDep = new ArrayList<>();
	private final ArrayList<Integer> vgMenuAmt = new ArrayList<>();
	private int vgScrollRow = 0;
	private boolean vgScrollDrag = false;
	private int vgInvScrollRow = 0;
	private boolean vgInvScrollDrag = false;
	private String vgSearch = "";
	private String vgLastQuery = "";
	private boolean vgSearchFocus = false;
	private int vgCaretTick = 0;
	private int vgPage = 0;
	private static final int VG_PAGE_SIZE = 48, VG_PAGE_MAX = 6;

	private boolean voidGlassBank() {
		return Config.C_CUSTOM_UI && !Config.isAndroid();
	}

	private int vgTier() { int gw = mc.getGameWidth(); return gw < 700 ? 0 : (gw < 940 ? 1 : 2); }
	private int vgCols() { int t = vgTier(); return t == 0 ? 8 : (t == 1 ? 10 : 12); }
	private int vgCellW() { int t = vgTier(); return t == 0 ? 49 : (t == 1 ? 54 : 60); }
	private int vgCellH() { int t = vgTier(); return t == 0 ? 34 : (t == 1 ? 36 : 40); }
	private int vgTopSafe() { return Math.max(56, mc.getVoidscapeDesktopOverlayTopSafeY()); }
	private int vgBotSafe() { int gh = mc.getGameHeight(); int e = mc.getVoidscapeDesktopOverlayBottomSafeY(); return e < gh ? e : gh - 22; }
	private int vgPanelW() { return Math.min(vgCols() * vgCellW() + 2 * VG_INNER_PAD + VG_SCROLL_GUTTER, mc.getGameWidth() - 2 * VG_SIDE_MARGIN); }
	private int vgInvRows() { int c = vgCols(); return (30 + c - 1) / c; }

	private boolean renderVoidGlassBank(int mx, int my) {
		int gw = mc.getGameWidth();
		int cols = vgCols(), cellW = vgCellW(), cellH = vgCellH();
		int topSafe = vgTopSafe(), botSafe = vgBotSafe();
		int availH = botSafe - topSafe;
		int titleH = VG_TITLE_H, invLabelH = 16, actionH = 22, pad = 4;
		// page tabs (client-side slices of bankItems, 48 per tab like classic pages)
		String vq = vgSearch.trim().toLowerCase(Locale.ROOT);
		int bankPages = bankItems.isEmpty() ? 0 : Math.min(VG_PAGE_MAX, Math.max(1, (bankItems.size() + VG_PAGE_SIZE - 1) / VG_PAGE_SIZE));
		if (!vq.isEmpty() && vgPage != 0) vgPage = 0; // search always spans the whole bank
		if (vgPage > bankPages) vgPage = 0;
		boolean tabsVisible = vq.isEmpty() && bankPages > 1;
		int tabRowH = tabsVisible ? 20 : 0;

		// Adaptive bank/inventory split: the bank grid keeps >=3 rows; the inventory
		// tray takes the remainder (min 1) and scrolls when rows are hidden (Classic 512x334).
		int invRowsFull = vgInvRows();
		int chrome = titleH + tabRowH + pad + invLabelH + actionH + pad + pad;
		int totalRows = Math.max(2, (availH - chrome) / cellH);
		int invRows = Math.max(1, Math.min(invRowsFull, totalRows - 3));
		int bankRows = Math.max(1, Math.min(14, totalRows - invRows));
		int fixed = chrome + invRows * cellH;
		int pw = vgPanelW();
		int ph = fixed + bankRows * cellH;
		int px = (gw - pw) / 2;
		int py = topSafe + Math.max(0, (availH - ph) / 2);
		int gx = px + VG_INNER_PAD;
		int bankGY = py + titleH + tabRowH + pad;
		int gridH = bankRows * cellH;
		int invLabelY = bankGY + gridH;
		int invGY = invLabelY + invLabelH;
		int actionY = invGY + invRows * cellH + pad;

		// visible list = page slice, or live search filter (name contains, case-insensitive)
		ArrayList<BankItem> vis;
		if (!vq.isEmpty()) {
			vis = new ArrayList<>();
			for (BankItem bi : bankItems) {
				if (bi == null || bi.getItem() == null) continue;
				ItemDef d = bi.getItem().getItemDef();
				if (d != null && d.getName() != null && d.getName().toLowerCase(Locale.ROOT).contains(vq)) vis.add(bi);
			}
		} else if (vgPage > 0) {
			int from = (vgPage - 1) * VG_PAGE_SIZE;
			int to = vgPage == VG_PAGE_MAX ? bankItems.size() : Math.min(bankItems.size(), vgPage * VG_PAGE_SIZE);
			vis = from < bankItems.size() ? new ArrayList<>(bankItems.subList(from, to)) : new ArrayList<>();
		} else {
			vis = bankItems;
		}
		if (!vq.equals(vgLastQuery)) { vgLastQuery = vq; vgScrollRow = 0; }

		int bankTotalRows = (vis.size() + cols - 1) / cols;
		int maxScroll = Math.max(0, bankTotalRows - bankRows);
		vgScrollRow = Math.max(0, Math.min(maxScroll, vgScrollRow));

		boolean click = mc.getMouseClick() == 1 || mc.mouseButtonClick == 1;
		boolean rclick = mc.getMouseClick() == 2 || mc.mouseButtonClick == 2;
		boolean down = mc.getMouseButtonDown() == 1;

		// scrollbar geometry (right gutter)
		int sbW = 6, sbX = px + pw - VG_INNER_PAD + 1, sbY = bankGY, sbH = gridH;
		int thumbH = maxScroll > 0 ? Math.max(18, sbH * bankRows / Math.max(1, bankTotalRows)) : sbH;
		int thumbY = maxScroll > 0 ? sbY + (sbH - thumbH) * vgScrollRow / maxScroll : sbY;

		// inventory tray scrollbar (same gutter, tray Y-range) for rows hidden by the split
		int maxInvScroll = Math.max(0, invRowsFull - invRows);
		vgInvScrollRow = Math.max(0, Math.min(maxInvScroll, vgInvScrollRow));
		int invSbY = invGY, invSbH = invRows * cellH;
		int invThumbH = maxInvScroll > 0 ? Math.max(14, invSbH * invRows / Math.max(1, invRowsFull)) : invSbH;
		int invThumbY = maxInvScroll > 0 ? invSbY + (invSbH - invThumbH) * vgInvScrollRow / maxInvScroll : invSbY;

		// --- input: right-click quantity menu takes priority ---
		if (vgMenu && (click || rclick)) {
			int mw = vgMenuWidth(), mh = vgMenuLabels.size() * VG_MENU_ROW_H + 4;
			if (mx >= vgMenuX && mx < vgMenuX + mw && my >= vgMenuY && my < vgMenuY + mh) {
				int row = (my - vgMenuY - 2) / VG_MENU_ROW_H;
				if (row >= 0 && row < vgMenuLabels.size()) vgDoMenu(row);
			}
			vgMenu = false; mc.setMouseClick(0); mc.mouseButtonClick = 0; click = false; rclick = false;
		}

		// --- input: scrollbar drag / track click ---
		if (!down) vgScrollDrag = false;
		if (down && !vgInvScrollDrag && maxScroll > 0 && mx >= sbX - 3 && mx <= sbX + sbW + 3 && my >= sbY && my <= sbY + sbH) {
			if (my >= thumbY && my <= thumbY + thumbH) vgScrollDrag = true;
			else if (mc.getMouseButtonDownTime() < 3) vgScrollRow += (my < thumbY ? -bankRows : bankRows);
		}
		if (vgScrollDrag && maxScroll > 0) {
			int rel = my - sbY - thumbH / 2;
			vgScrollRow = rel * maxScroll / Math.max(1, sbH - thumbH);
		}
		vgScrollRow = Math.max(0, Math.min(maxScroll, vgScrollRow));
		thumbY = maxScroll > 0 ? sbY + (sbH - thumbH) * vgScrollRow / maxScroll : sbY;

		// --- input: inventory scrollbar drag / track click ---
		if (!down) vgInvScrollDrag = false;
		if (down && !vgScrollDrag && maxInvScroll > 0 && mx >= sbX - 3 && mx <= sbX + sbW + 3 && my >= invSbY && my <= invSbY + invSbH) {
			if (my >= invThumbY && my <= invThumbY + invThumbH) vgInvScrollDrag = true;
			else if (mc.getMouseButtonDownTime() < 3) vgInvScrollRow += (my < invThumbY ? -invRows : invRows);
		}
		if (vgInvScrollDrag && maxInvScroll > 0) {
			int rel = my - invSbY - invThumbH / 2;
			vgInvScrollRow = rel * maxInvScroll / Math.max(1, invSbH - invThumbH);
		}
		vgInvScrollRow = Math.max(0, Math.min(maxInvScroll, vgInvScrollRow));
		invThumbY = maxInvScroll > 0 ? invSbY + (invSbH - invThumbH) * vgInvScrollRow / maxInvScroll : invSbY;

		// --- input: close button ---
		int closeX = px + pw - 18, closeY = py + 5;
		boolean overClose = mx >= closeX && mx <= closeX + 14 && my >= closeY && my <= closeY + 14;
		if (click && overClose) { mc.setMouseClick(0); bankClose(); return false; }

		// --- input: search box focus ---
		int sx = px + 58, sy = py + 4, sh2 = 16;
		int sw = Math.min(170, pw - 58 - 100);
		boolean overSearch = mx >= sx && mx < sx + sw && my >= sy && my < sy + sh2;
		if (click) {
			if (overSearch) { vgSearchFocus = true; mc.setMouseClick(0); mc.mouseButtonClick = 0; click = false; }
			else vgSearchFocus = false;
		}

		// --- input: page tabs ---
		int tabY = py + titleH, tabH = Math.max(0, tabRowH - 3), tabGap = 4;
		int tabCount = tabsVisible ? bankPages + 1 : 0;
		int tabW = tabsVisible ? Math.max(30, Math.min(60, (pw - 2 * VG_INNER_PAD - (tabCount - 1) * tabGap) / tabCount)) : 0;
		if (tabsVisible && click) {
			for (int t = 0; t < tabCount; t++) {
				int tx = gx + t * (tabW + tabGap);
				if (mx >= tx && mx < tx + tabW && my >= tabY && my < tabY + tabH) {
					vgPage = t; vgScrollRow = 0;
					mc.setMouseClick(0); mc.mouseButtonClick = 0; click = false;
					break;
				}
			}
		}

		// --- input: deposit-all button ---
		int daW = Math.min(170, pw - 2 * VG_INNER_PAD), daX = px + pw - VG_INNER_PAD - daW, daY = actionY;
		boolean overDA = mx >= daX && mx < daX + daW && my >= daY && my < daY + actionH;
		if (click && overDA) { mc.setMouseClick(0); mc.mouseButtonClick = 0; sendVgDepositAll(); click = false; }

		// --- input: loadout chips (left of the action bar); any click opens the action menu ---
		int loW = 26, loGap = 4, loY = actionY;
		int loHover = -1;
		if (S_WANT_BANK_PRESETS) {
			for (int p = 0; p < 3; p++) {
				int lx = gx + p * (loW + loGap);
				if (mx >= lx && mx < lx + loW && my >= loY && my < loY + actionH) {
					loHover = p;
					if (click || rclick) {
						openVgPresetMenu(p, mx, my);
						mc.setMouseClick(0); mc.mouseButtonClick = 0; click = false; rclick = false;
					}
				}
			}
		}

		// --- input: withdraw-as-note toggle (sendWithdraw appends swapNoteMode) ---
		int noteW = 62, noteX = gx + (S_WANT_BANK_PRESETS ? 3 * (loW + loGap) + 6 : 0);
		boolean overNote = S_WANT_BANK_NOTES && mx >= noteX && mx < noteX + noteW && my >= loY && my < loY + actionH;
		if (overNote && click) {
			swapNoteMode = !swapNoteMode;
			mc.setMouseClick(0); mc.mouseButtonClick = 0; click = false;
		}

		// --- input: bank grid (withdraw) ---
		int bankHover = -1;
		for (int r = 0; r < bankRows; r++) {
			for (int c = 0; c < cols; c++) {
				int idx = (vgScrollRow + r) * cols + c;
				int cx = gx + c * cellW, cy = bankGY + r * cellH;
				if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH && idx < vis.size()) {
					bankHover = r * cols + c;
					int id = vis.get(idx).getItem().getCatalogID();
					if (id != -1) {
						if (rclick) { openVgMenu(id, mx, my); mc.setMouseClick(0); mc.mouseButtonClick = 0; rclick = false; }
						else if (click) { selectedBankSlotItemID = id; mc.setMouseClick(0); mc.mouseButtonClick = 0; sendWithdraw(1); }
					}
				}
			}
		}

		// --- input: inventory tray (deposit) ---
		int invHover = -1;
		int invCount = mc.getInventoryItemCount();
		for (int r = 0; r < invRows; r++) {
			for (int c = 0; c < cols; c++) {
				int slot = (vgInvScrollRow + r) * cols + c;
				if (slot >= 30) break;
				int cx = gx + c * cellW, cy = invGY + r * cellH;
				if (mx >= cx && mx < cx + cellW && my >= cy && my < cy + cellH && slot < invCount && mc.getInventoryItemID(slot) != -1) {
					invHover = r * cols + c;
					int id = mc.getInventoryItemID(slot);
					if (rclick) { openVgMenu(id, mx, my); mc.setMouseClick(0); mc.mouseButtonClick = 0; rclick = false; }
					else if (click) {
						int ci = vgFindCurrentIndex(id);
						if (ci >= 0) { this.selectedBankSlot = ci; selectedBankSlotItemID = id; mc.setMouseClick(0); mc.mouseButtonClick = 0; sendDeposit(1); }
					}
				}
			}
		}

		// ---- render: panel + title ----
		mc.getSurface().drawBoxAlpha(px, py, pw, ph, VG_BODY, VG_BODY_A);
		mc.getSurface().drawBoxAlpha(px + 1, py + titleH, pw - 2, (ph - titleH) * 2 / 5, 0xBFA8FF, 12); // glass sheen
		mc.getSurface().drawBoxAlpha(px, py, pw, titleH, VG_STRIP, VG_STRIP_A);
		mc.getSurface().drawLineAlpha(px, py + titleH, px + pw, py + titleH, VG_HAIRLINE, 120);
		mc.getSurface().drawBoxBorder(px, pw, py, ph, 0);
		mc.getSurface().drawBoxBorder(px + 1, pw - 2, py + 1, ph - 2, VG_FRAME_INNER);
		drawString("Bank", px + 10, py + 16, 5, VG_TITLE_TEXT);
		String counter = vq.isEmpty() ? bankItems.size() + " / 1608" : vis.size() + " found";
		drawString(counter, px + pw - 40 - mc.getSurface().stringWidth(1, counter), py + 15, 1, VG_LABEL);
		drawString("X", closeX + 4, py + 16, 4, overClose ? 0xFF7A7A : VG_TITLE_TEXT);

		// search box (title strip)
		mc.getSurface().drawBoxAlpha(sx, sy, sw, sh2, 0x0C0620, 150);
		mc.getSurface().drawBoxBorder(sx, sw, sy, sh2, vgSearchFocus ? VG_FRAME_INNER : VG_SLOT_BORDER);
		vgCaretTick++;
		String disp = vgSearch;
		if (vgSearchFocus && (vgCaretTick / 25) % 2 == 0) disp = disp + "|";
		if (disp.isEmpty()) drawString("Search", sx + 5, sy + 12, 1, 0x8474A8);
		else drawString(disp, sx + 5, sy + 12, 1, VG_TITLE_TEXT);

		// ---- render: page tabs ----
		if (tabsVisible) {
			for (int t = 0; t < tabCount; t++) {
				int tx = gx + t * (tabW + tabGap);
				boolean active = t == vgPage;
				boolean hovT = mx >= tx && mx < tx + tabW && my >= tabY && my < tabY + tabH;
				mc.getSurface().drawBoxAlpha(tx, tabY, tabW, tabH, active ? VG_STRIP : 0x0C0620, active ? 170 : (hovT ? 150 : 105));
				mc.getSurface().drawBoxBorder(tx, tabW, tabY, tabH, active ? VG_FRAME_INNER : VG_SLOT_BORDER);
				if (active) mc.getSurface().drawBoxAlpha(tx + 1, tabY + tabH - 2, tabW - 2, 2, VG_SEL_RING, 200);
				String lb = t == 0 ? "All" : (tabW < 44 ? "T" + t : "Tab " + t);
				drawString(lb, tx + (tabW - mc.getSurface().stringWidth(1, lb)) / 2, tabY + 12, 1, active ? VG_TITLE_TEXT : VG_LABEL);
			}
		}

		// ---- render: bank grid ----
		for (int r = 0; r < bankRows; r++) {
			for (int c = 0; c < cols; c++) {
				int idx = (vgScrollRow + r) * cols + c;
				int cx = gx + c * cellW, cy = bankGY + r * cellH;
				mc.getSurface().drawBoxAlpha(cx, cy, cellW, cellH, VG_SLOT, VG_SLOT_A);
				if (r * cols + c == bankHover) mc.getSurface().drawBoxAlpha(cx, cy, cellW, cellH, VG_HOVER, VG_HOVER_A);
				if (idx < vis.size()) drawVoidGlassCell(vis.get(idx).getItem(), vis.get(idx).getItem().getAmount(), true, cx, cy, cellW, cellH);
			}
		}
		drawVgGridLines(gx, bankGY, cols, bankRows, cellW, cellH);
		if (bankHover >= 0) mc.getSurface().drawBoxBorder(gx + (bankHover % cols) * cellW, cellW, bankGY + (bankHover / cols) * cellH, cellH, VG_HOVER_BORDER);
		if (maxScroll > 0) {
			boolean hovSb = mx >= sbX - 3 && mx <= sbX + sbW + 3 && my >= thumbY && my <= thumbY + thumbH;
			mc.getSurface().drawBoxAlpha(sbX, sbY, sbW, sbH, 0x0C0620, 120);
			mc.getSurface().drawBoxAlpha(sbX, thumbY, sbW, thumbH, VG_FRAME_INNER, hovSb || vgScrollDrag ? 220 : 160);
		}

		// ---- render: inventory divider + tray ----
		mc.getSurface().drawLineAlpha(px + 6, invLabelY + invLabelH - 2, px + pw - 6, invLabelY + invLabelH - 2, VG_HAIRLINE, 120);
		drawString("Inventory", px + 10, invLabelY + 12, 1, VG_LABEL);
		for (int r = 0; r < invRows; r++) {
			for (int c = 0; c < cols; c++) {
				int slot = (vgInvScrollRow + r) * cols + c;
				if (slot >= 30) break;
				int cx = gx + c * cellW, cy = invGY + r * cellH;
				mc.getSurface().drawBoxAlpha(cx, cy, cellW, cellH, VG_SLOT, VG_SLOT_A);
				if (r * cols + c == invHover) mc.getSurface().drawBoxAlpha(cx, cy, cellW, cellH, VG_HOVER, VG_HOVER_A);
				if (slot < invCount && mc.getInventoryItemID(slot) != -1)
					drawVoidGlassCell(mc.getInventoryItem(slot), mc.getInventoryItemAmount(slot), false, cx, cy, cellW, cellH);
			}
		}
		drawVgGridLines(gx, invGY, cols, invRows, cellW, cellH);
		if (invHover >= 0) mc.getSurface().drawBoxBorder(gx + (invHover % cols) * cellW, cellW, invGY + (invHover / cols) * cellH, cellH, VG_HOVER_BORDER);
		if (maxInvScroll > 0) {
			boolean hovIsb = mx >= sbX - 3 && mx <= sbX + sbW + 3 && my >= invThumbY && my <= invThumbY + invThumbH;
			mc.getSurface().drawBoxAlpha(sbX, invSbY, sbW, invSbH, 0x0C0620, 120);
			mc.getSurface().drawBoxAlpha(sbX, invThumbY, sbW, invThumbH, VG_FRAME_INNER, hovIsb || vgInvScrollDrag ? 220 : 160);
		}

		// ---- render: action bar (loadouts + deposit all) ----
		if (S_WANT_BANK_PRESETS) {
			for (int p = 0; p < 3; p++) {
				int lx = gx + p * (loW + loGap);
				boolean filled = !vgPresetEmpty(p);
				boolean hovL = p == loHover;
				mc.getSurface().drawBoxAlpha(lx, loY, loW, actionH, hovL ? VG_HOVER : 0x341F5E, hovL ? 95 : (filled ? 165 : 110));
				mc.getSurface().drawBoxBorder(lx, loW, loY, actionH, filled ? VG_FRAME_INNER : VG_SLOT_BORDER);
				String ll = "L" + (p + 1);
				drawString(ll, lx + (loW - mc.getSurface().stringWidth(1, ll)) / 2, loY + 15, 1, filled ? VG_GOLD : VG_LABEL);
			}
		}
		if (S_WANT_BANK_NOTES) {
			mc.getSurface().drawBoxAlpha(noteX, loY, noteW, actionH, overNote ? VG_HOVER : 0x341F5E, overNote ? 95 : (swapNoteMode ? 165 : 110));
			mc.getSurface().drawBoxBorder(noteX, noteW, loY, actionH, swapNoteMode ? VG_FRAME_INNER : VG_SLOT_BORDER);
			String nl = swapNoteMode ? "Note: On" : "Note: Off";
			drawString(nl, noteX + (noteW - mc.getSurface().stringWidth(1, nl)) / 2, loY + 15, 1, swapNoteMode ? VG_GREEN : VG_LABEL);
		}
		mc.getSurface().drawBoxAlpha(daX, daY, daW, actionH, overDA ? VG_HOVER : 0x341F5E, overDA ? 95 : 165);
		mc.getSurface().drawBoxBorder(daX, daW, daY, actionH, VG_FRAME_INNER);
		String da = "Deposit all inventory";
		drawString(da, daX + (daW - mc.getSurface().stringWidth(1, da)) / 2, daY + 15, 1, VG_GOLD);

		if (vgMenu) drawVoidGlassMenu(mx, my);
		return true;
	}

	// Keyboard entry for the Void Glass search box; returns true when the key was consumed.
	// Called from mudclient's key dispatcher while the bank dialog is open.
	public boolean vgHandleKey(int key) {
		if (!voidGlassBank() || !mc.isShowDialogBank()) return false;
		if (key == 27) {
			if (vgSearchFocus || !vgSearch.isEmpty()) { vgSearch = ""; vgSearchFocus = false; }
			else bankClose();
			return true;
		}
		if (!vgSearchFocus) return false;
		if (key == '\b') {
			if (!vgSearch.isEmpty()) vgSearch = vgSearch.substring(0, vgSearch.length() - 1);
			return true;
		}
		if (key == 10 || key == 13) { vgSearchFocus = false; return true; }
		if (vgSearch.length() < 18 && Fonts.inputFilterChars.indexOf((char) key) >= 0) vgSearch += (char) key;
		return true;
	}

	public void vgResetSearch() {
		vgSearch = ""; vgLastQuery = ""; vgSearchFocus = false;
		vgScrollRow = 0; vgInvScrollRow = 0; vgMenu = false; vgPage = 0;
		swapNoteMode = false;
	}

	private void vgDoMenu(int row) {
		if (vgMenuKind == 1) {
			sendVgPreset(vgMenuAmt.get(row), vgMenuPresetSlot);
			return;
		}
		boolean dep = vgMenuDep.get(row);
		int amt = vgMenuAmt.get(row);
		selectedBankSlotItemID = vgMenuItemID;
		if (dep) {
			int ci = vgFindCurrentIndex(vgMenuItemID);
			if (ci < 0) return;
			this.selectedBankSlot = ci;
		}
		if (amt == VG_X) {
			mc.showItemModX(dep ? InputXPrompt.bankDepositX : InputXPrompt.bankWithdrawX,
				dep ? InputXAction.BANK_DEPOSIT : InputXAction.BANK_WITHDRAW, true);
		} else if (dep) sendDeposit(amt); else sendWithdraw(amt);
	}

	private int vgFindCurrentIndex(int itemID) {
		for (int i = 0; i < currentItems.size(); i++)
			if (currentItems.get(i).getCatalogID() == itemID) return i;
		return -1;
	}

	private void sendVgDepositAll() {
		mc.packetHandler.getClientStream().newPacket(24);
		mc.packetHandler.getClientStream().finishPacket();
		mc.setMouseClick(0); mc.setMouseButtonDown(0);
	}

	// Loadout actions ride the existing custom-bank preset packets:
	// load = 28, save = 27 (server snapshots current inventory/equipment), clear = 199 sub-op 14.
	private void sendVgPreset(int action, int slot) {
		if (action == 0 || action == 1) {
			mc.packetHandler.getClientStream().newPacket(action == 0 ? 28 : 27);
			mc.packetHandler.getClientStream().bufferBits.putShort(slot);
		} else {
			mc.packetHandler.getClientStream().newPacket(199);
			mc.packetHandler.getClientStream().bufferBits.putByte(14);
			mc.packetHandler.getClientStream().bufferBits.putByte(slot);
		}
		mc.packetHandler.getClientStream().finishPacket();
	}

	private boolean vgPresetEmpty(int slot) {
		CustomBankInterface cbi = mc.getBank();
		if (cbi == null || slot < 0 || slot >= cbi.presets.length || cbi.presets[slot] == null) return true;
		for (Item it : cbi.presets[slot].inventory)
			if (it != null && it.getItemDef() != null) return false;
		for (Item it : cbi.presets[slot].equipment)
			if (it != null && it.getItemDef() != null) return false;
		return true;
	}

	private void openVgPresetMenu(int slot, int mx, int my) {
		vgMenuLabels.clear(); vgMenuDep.clear(); vgMenuAmt.clear();
		boolean empty = vgPresetEmpty(slot);
		if (!empty) addVgMenu("Load loadout " + (slot + 1), false, 0);
		addVgMenu("Save current as loadout " + (slot + 1), true, 1);
		if (!empty) addVgMenu("Clear loadout " + (slot + 1), true, 2);
		vgMenuKind = 1; vgMenuPresetSlot = slot; vgMenu = true;
		int mw = vgMenuWidth(), mh = vgMenuLabels.size() * VG_MENU_ROW_H + 4;
		vgMenuX = Math.min(mx, mc.getGameWidth() - mw - 2);
		vgMenuY = Math.min(my, mc.getGameHeight() - mh - 2);
	}

	private void drawVgGridLines(int gx, int gy, int cols, int rows, int cw, int ch) {
		for (int c = 0; c <= cols; c++)
			mc.getSurface().drawLineAlpha(gx + c * cw, gy, gx + c * cw, gy + rows * ch, VG_GRID_LINE, VG_GRID_LINE_A);
		for (int r = 0; r <= rows; r++)
			mc.getSurface().drawLineAlpha(gx, gy + r * ch, gx + cols * cw, gy + r * ch, VG_GRID_LINE, VG_GRID_LINE_A);
	}

	private void drawVoidGlassCell(Item it, int amount, boolean bankSide, int cx, int cy, int cellW, int cellH) {
		if (it == null || it.getCatalogID() == -1) return;
		ItemDef def = it.getItemDef();
		if (def == null) return;
		int ix = cx + (cellW - 48) / 2, iy = cy + 1 + (cellH - 34) / 2;
		if (it.getNoted()) {
			// classic two-layer note: paper backing at full size, item inset at 29x19
			ItemDef bg = S_WANT_CERT_AS_NOTES ? EntityHandler.noteDef : EntityHandler.certificateDef;
			mc.getSurface().drawSpriteClipping(mc.spriteSelect(bg), ix, iy, 48, 32,
				bg.getPictureMask(), 0, bg.getBlueMask(), false, 0, 1);
			if (S_WANT_CERT_AS_NOTES)
				mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), ix + 7, iy + 8, 29, 19,
					def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
		} else {
			mc.getSurface().drawSpriteClipping(mc.spriteSelect(def), ix, iy, 48, 32,
				def.getPictureMask(), 0, def.getBlueMask(), false, 0, 1);
		}
		if (amount > 1 || def.isStackable() || it.getNoted()) {
			String s = String.valueOf(amount);
			int col = bankSide ? (amount >= 10000000 ? VG_CYAN : (amount >= 100000 ? VG_GOLD : VG_GREEN)) : VG_CYAN;
			drawString(s, cx + 3, cy + 11, 1, 0x000000);
			drawString(s, cx + 2, cy + 10, 1, col);
		}
	}

	private void openVgMenu(int id, int mx, int my) {
		vgMenuLabels.clear(); vgMenuDep.clear(); vgMenuAmt.clear();
		Item banked = getBankItemByID(id);
		int bAmt = banked != null ? banked.getAmount() : 0;
		if (bAmt > 0) {
			addVgMenu("Withdraw 1", false, 1);
			if (bAmt >= 5) addVgMenu("Withdraw 5", false, 5);
			if (bAmt >= 10) addVgMenu("Withdraw 10", false, 10);
			addVgMenu("Withdraw X", false, VG_X);
			addVgMenu("Withdraw All", false, VG_ALL);
		}
		int held = mc.getInventoryCount(id);
		if (held > 0) {
			addVgMenu("Deposit 1", true, 1);
			if (held >= 5) addVgMenu("Deposit 5", true, 5);
			if (held >= 10) addVgMenu("Deposit 10", true, 10);
			addVgMenu("Deposit X", true, VG_X);
			addVgMenu("Deposit All", true, VG_ALL);
		}
		if (vgMenuLabels.isEmpty()) return;
		int mw = vgMenuWidth(), mh = vgMenuLabels.size() * VG_MENU_ROW_H + 4;
		vgMenu = true; vgMenuKind = 0; vgMenuItemID = id;
		vgMenuX = Math.min(mx, mc.getGameWidth() - mw - 2);
		vgMenuY = Math.min(my, mc.getGameHeight() - mh - 2);
	}

	private void addVgMenu(String label, boolean deposit, int amt) {
		vgMenuLabels.add(label); vgMenuDep.add(deposit); vgMenuAmt.add(amt);
	}

	private int vgMenuWidth() {
		int w = 80;
		for (String s : vgMenuLabels) w = Math.max(w, mc.getSurface().stringWidth(1, s) + 18);
		return w;
	}

	private void drawVoidGlassMenu(int mx, int my) {
		int mw = vgMenuWidth(), mh = vgMenuLabels.size() * VG_MENU_ROW_H + 4;
		mc.getSurface().drawBoxAlpha(vgMenuX, vgMenuY, mw, mh, VG_MENU_CARD, 210);
		mc.getSurface().drawBoxBorder(vgMenuX, mw, vgMenuY, mh, VG_FRAME_INNER);
		for (int i = 0; i < vgMenuLabels.size(); i++) {
			int ry = vgMenuY + 2 + i * VG_MENU_ROW_H;
			boolean hov = mx >= vgMenuX && mx < vgMenuX + mw && my >= ry && my < ry + VG_MENU_ROW_H;
			if (hov) mc.getSurface().drawBoxAlpha(vgMenuX + 1, ry, mw - 2, VG_MENU_ROW_H, VG_HOVER, 90);
			drawString(vgMenuLabels.get(i), vgMenuX + 6, ry + 12, 1, vgMenuDep.get(i) ? VG_CYAN : VG_GREEN);
		}
	}

	private void selectSlot(int selectedX, int selectedY) {
		int selectedItemSlot = mouseOverBankPageText * 48;
		for (int verticalSlots = 0; verticalSlots < 6; verticalSlots++) {
			for (int horizontalSlots = 0; horizontalSlots < 8; horizontalSlots++) {
				int slotX = 7 + horizontalSlots * 49;
				int slotY = 28 + verticalSlots * 34;

				// If the selection is in area
				if (selectedX > slotX && selectedX < slotX + 49 &&
					selectedY > slotY && selectedY < slotY + 34) {

					// Check if the click was on a bank item.
					if (selectedItemSlot < currentItems.size()) {
						Item i = currentItems.get(selectedItemSlot);
						if (i.getCatalogID() != -1){
							if (i.getAmount() > 0 || mc.getInventoryCount(i.getCatalogID()) > 0) {
								selectedBankSlotItemID = i.getCatalogID();
								this.selectedBankSlot = selectedItemSlot;
							}
							return;
						}
					}
				}
				selectedItemSlot++;
			}
		}
	}

	private void checkTransaction(int currMouseX, int currMouseY, int selectedX, int selectedY) {
		int itemID = selectedBankSlotItemID;
		int amount = currentItems.get(this.selectedBankSlot).getAmount();

		final boolean L_WANT_CERT_DEPOSIT = Config.S_WANT_CERT_DEPOSIT && BankUtil.isCert(itemID);

		// Incremental Withdraw or Deposit
		if (currMouseX >= selectedX + 220 && currMouseY >= selectedY + 240
			&& currMouseX < selectedX + 250 && currMouseY <= selectedY + 251) {
			if (mc.mouseButtonItemCountIncrement == 0)
				mc.mouseButtonItemCountIncrement = 1;
			if (Config.S_WANT_BANK_NOTES) {
				this.swapNoteMode = !this.swapNoteMode;
			} else
				sendWithdraw(mc.mouseButtonItemCountIncrement); // Withdraw 1
		} else if (mc.getInventoryCount(itemID) >= 1 && currMouseX >= selectedX + 220 && currMouseY >= selectedY + 265
			&& currMouseX < selectedX + 250 && currMouseY <= selectedY + 276) {
			if (mc.mouseButtonItemCountIncrement == 0)
				mc.mouseButtonItemCountIncrement = 1;
			if (L_WANT_CERT_DEPOSIT) {
				this.swapCertMode = !this.swapCertMode;
				sendCertMode();
			} else
				sendDeposit(mc.mouseButtonItemCountIncrement); // Deposit 1
		}

		// Non incremental Withdraw or deposit
		else if ((mc.getMouseButtonDownTime() < 99999 && Config.isAndroid()) || (mc.getMouseButtonDownTime() < 50 && !Config.isAndroid())) {
			if ((amount >= 5 || Config.S_WANT_BANK_NOTES) && currMouseX >= selectedX + 250 && currMouseY >= selectedY + 240
				&& currMouseX < selectedX + 280 && currMouseY <= selectedY + 251) {
				if (Config.S_WANT_BANK_NOTES) {
					this.swapNoteMode = !this.swapNoteMode;
				} else
					sendWithdraw(5); // Withdraw 5
			} else if ((amount >= 10 || Config.S_WANT_BANK_NOTES) && currMouseX >= selectedX + 280 && currMouseY >= selectedY + 240
				&& currMouseX < selectedX + 305 && currMouseY <= selectedY + 251) {
				if (!Config.S_WANT_BANK_NOTES)
					sendWithdraw(10); // Withdraw 10
			} else if ((amount >= 50 || Config.S_WANT_BANK_NOTES) && currMouseX >= selectedX + 305 && currMouseY >= selectedY + 240
				&& currMouseX < selectedX + 335 && currMouseY <= selectedY + 251) {
				if (Config.S_WANT_BANK_NOTES)
					sendWithdraw(1);
				else
					sendWithdraw(50); // Withdraw 50
			} else if (currMouseX >= selectedX + 340 && currMouseY >= selectedY + 240
				&& currMouseX < selectedX + 368 && currMouseY <= selectedY + 251) {
				// Withdraw X
				mc.showItemModX(InputXPrompt.bankWithdrawX, InputXAction.BANK_WITHDRAW, true);
				mc.setMouseClick(0);
			} else if (currMouseX >= selectedX + 370 && currMouseY >= selectedY + 240
				&& currMouseX < selectedX + 400 && currMouseY <= selectedY + 251) {
				sendWithdraw(Integer.MAX_VALUE); // Withdraw All
			}

			// Depositing
			else if ((mc.getInventoryCount(itemID) >= 5 || L_WANT_CERT_DEPOSIT) && currMouseX >= selectedX + 250 && currMouseY >= selectedY + 265
				&& currMouseX < selectedX + 280 && currMouseY <= selectedY + 276) {
				if (L_WANT_CERT_DEPOSIT) {
					this.swapCertMode = !this.swapCertMode;
					sendCertMode();
				} else
					sendDeposit(5); // Deposit 5
			} else if ((mc.getInventoryCount(itemID) >= 10 || L_WANT_CERT_DEPOSIT) && currMouseX >= selectedX + 280 && currMouseY >= selectedY + 265
				&& currMouseX < selectedX + 305 && currMouseY <= selectedY + 276) {
				if (!L_WANT_CERT_DEPOSIT)
					sendDeposit(10); // Deposit 10
			} else if ((mc.getInventoryCount(itemID) >= 50 || L_WANT_CERT_DEPOSIT) && currMouseX >= selectedX + 305 && currMouseY >= selectedY + 265
				&& currMouseX < selectedX + 335 && currMouseY <= selectedY + 276) {
				if (L_WANT_CERT_DEPOSIT)
					sendDeposit(1);
				else
					sendDeposit(50); // Deposit 50
			} else if (currMouseX >= selectedX + 340 && currMouseY >= selectedY + 265
				&& currMouseX < selectedX + 368 && currMouseY <= selectedY + 276) {
				// Deposit X
				mc.showItemModX(InputXPrompt.bankDepositX, InputXAction.BANK_DEPOSIT, true);
				mc.setMouseClick(0);
			} else if (currMouseX >= selectedX + 370 && currMouseY >= selectedY + 265
				&& currMouseX < selectedX + 400 && currMouseY <= selectedY + 276) {
				sendDeposit(Integer.MAX_VALUE); // Deposit All
			}
		}
	}

	private int bankOriginX() {
		return (mc.getGameWidth() - width) / 2;
	}

	private int bankOriginY() {
		// Absolute default bank position (centered). No voidscape offset override.
		return mc.getGameHeight() / 2 - height / 2 + 20;
	}

	private void drawBankComponents(int currMouseX, int currMouseY) {
		int relativeX = bankOriginX(); // WAS 256
		int relativeY = bankOriginY(); // WAS 170
		mc.getSurface().drawBox(relativeX, relativeY, 408, 12, 192);
		int backgroundColour = 0x989898;
		mc.getSurface().drawBoxAlpha(relativeX, relativeY + 12, 408, 17, backgroundColour, 160);
		mc.getSurface().drawBoxAlpha(relativeX, relativeY + 29, 8, 204, backgroundColour, 160);
		mc.getSurface().drawBoxAlpha(relativeX + 399, relativeY + 29, 9, 204, backgroundColour, 160);
		mc.getSurface().drawBoxAlpha(relativeX, relativeY + 233, 408, 47, backgroundColour, 160);
		drawString("Bank", relativeX + 1, relativeY + 10, 1, 0xffffff);

		// Draw Bank Page Buttons
		if (membersWorld) {
			drawPageButtons(currMouseX, currMouseY, relativeX, relativeY);
		}

		// Draw Top Descriptions & Close Button
		int closeButtonColour = 0xffffff;
		if (currMouseX > relativeX + 320 && currMouseY >= relativeY + 3 && currMouseX < relativeX + 408 && currMouseY < relativeY + 15)
			closeButtonColour = 0xff0000;
		drawString("Close window", relativeX + 326, relativeY + 10, 1, closeButtonColour);
		drawString("Number in bank in green", relativeX + 7, relativeY + 24, 1, 0x00ff00);
		drawString("Number held in blue", relativeX + 289, relativeY + 24, 1, 0x00ffff);

		// Draw the items in the bank.
		drawBankItems(relativeX, relativeY);

		// Line between Withdraw & Deposit
		mc.getSurface().drawLineHoriz(relativeX + 5, relativeY + 256, width - 8, 0);

		// Draw the Quantity Buttons
		if (this.selectedBankSlot != -1) {
			drawQuantityButtons(currMouseX, currMouseY, relativeX, relativeY);
		} else {
			mc.getSurface().drawColoredStringCentered(relativeX + 204, "Select an object to withdraw or deposit", 0xFFFF00, 0, 3,
				relativeY + 248);
		}
	}

	private void drawPageButtons(int currMouseX, int currMouseY, int relativeX, int relativeY) {
		int pageButtonMargin = 50;
		int pageButtonColour = 0xffffff;

		if (currentItems.size() > 48) {
			// Page 1
			if (mouseOverBankPageText == 0)
				pageButtonColour = 0xff0000;
			else if (currMouseX > relativeX + pageButtonMargin && currMouseY >= relativeY + 4
				&& currMouseX < relativeX + pageButtonMargin + 65 && currMouseY < relativeY + 16)
				pageButtonColour = 0xffff00;
			drawString("<page 1>", relativeX + pageButtonMargin, relativeY + 10, 1, pageButtonColour);
			pageButtonMargin += 65;

			// Page 2
			pageButtonColour = 0xffffff;
			if (mouseOverBankPageText == 1)
				pageButtonColour = 0xff0000;
			else if (currMouseX > relativeX + pageButtonMargin && currMouseY >= relativeY + 4
				&& currMouseX < relativeX + pageButtonMargin + 65 && currMouseY < relativeY + 16)
				pageButtonColour = 0xffff00;
			drawString("<page 2>", relativeX + pageButtonMargin, relativeY + 10, 1, pageButtonColour);
			pageButtonMargin += 65;
		}
		if (currentItems.size() > 96) {
			pageButtonColour = 0xffffff;
			if (mouseOverBankPageText == 2)
				pageButtonColour = 0xff0000;
			else if (currMouseX > relativeX + pageButtonMargin && currMouseY >= relativeY + 4
				&& currMouseX < relativeX + pageButtonMargin + 65 && currMouseY < relativeY + 16)
				pageButtonColour = 0xffff00;
			drawString("<page 3>", relativeX + pageButtonMargin, relativeY + 10, 1, pageButtonColour);
			pageButtonMargin += 65;
		}
		if (currentItems.size() > 144) {
			pageButtonColour = 0xffffff;
			if (mouseOverBankPageText == 3)
				pageButtonColour = 0xff0000;
			else if (currMouseX > relativeX + pageButtonMargin && currMouseY >= relativeY + 4
				&& currMouseX < relativeX + pageButtonMargin + 65 && currMouseY < relativeY + 16)
				pageButtonColour = 0xffff00;
			drawString("<page 4>", relativeX + pageButtonMargin, relativeY + 10, 1, pageButtonColour);
		}
	}

	private void drawBankItems(int relativeX, int relativeY) {
		int inventorySlot = mouseOverBankPageText * 48;
		int inventoryCount;
		for (int verticalSlots = 0; verticalSlots < 6; verticalSlots++) {
			for (int horizontalSlots = 0; horizontalSlots < 8; horizontalSlots++) {
				int slotX = relativeX + 7 + horizontalSlots * 49;
				int slotY = relativeY + 28 + verticalSlots * 34;

				// Background Colour of Bank Tile
				if (this.selectedBankSlot == inventorySlot) { // Selected
					mc.getSurface().drawBoxAlpha(slotX, slotY, 49, 34, 0xff0000, 160);
				} else { // Not Selected
					mc.getSurface().drawBoxAlpha(slotX, slotY, 49, 34, 0xd0d0d0, 160);
				}

				mc.getSurface().drawBoxBorder(slotX, 50, slotY, 35, 0);

				// Draw Item Sprite From Bank
				if (inventorySlot < currentItems.size()) { // We don't exceed the bank size
					Item i = currentItems.get(inventorySlot);
					if (i.getCatalogID() != -1
						&& (i.getAmount() > 0 || mc.getInventoryCount(i.getCatalogID()) > 0)) {
						ItemDef def = i.getItemDef();
						if (def == null) {
							inventorySlot++;
							continue;
						}
						if (i.getNoted() && Config.S_WANT_CUSTOM_BANKS) {
							if (S_WANT_CERT_AS_NOTES) {
								// Draw the note background
								mc.getSurface().drawSpriteClipping(
									mc.spriteSelect(EntityHandler.noteDef),
									slotX, slotY, 48, 32,
									EntityHandler.noteDef.getPictureMask(),
									0, EntityHandler.noteDef.getBlueMask(),
									false, 0, 1);
								// Draw the item on top of the note background
								mc.getSurface().drawSpriteClipping(
									mc.spriteSelect(def),
									slotX + 7, slotY + 5, 29, 19,
									def.getPictureMask(), 0, def.getBlueMask(), false,
									0, 1);
							} else {
								// Draw the certificate sprite
								mc.getSurface().drawSpriteClipping(
									mc.spriteSelect(EntityHandler.certificateDef),
									slotX, slotY, 48, 32,
									EntityHandler.certificateDef.getPictureMask(),
									0, EntityHandler.certificateDef.getBlueMask(),
									false, 0, 1);
							}
						} else {
							// Draw the item in the correct slot.
							mc.getSurface().drawSpriteClipping(
								mc.spriteSelect(def),
								slotX, slotY, 48, 32,
								def.getPictureMask(),
								0, def.getBlueMask(),
								false, 0, 1);
						}

						// Amount in bank (green)
						Item banked = getBankItemByID(i.getCatalogID());
						if (banked != null)
							drawString("" + banked.getAmount(), slotX + 1, slotY + 10, 1, 0x00ff00);
						else
							drawString("0", slotX + 1, slotY + 10, 1, 0x00ff00);

						// Amount in inventory (blue)
						inventoryCount = mc.getInventoryCount(i.getCatalogID());
						drawString(String.valueOf(inventoryCount),
							(slotX + 47) - mc.getSurface().stringWidth(1, String.valueOf(inventoryCount)),
							slotY + 29, 1, 0x00ffff); // Amount in inventory (blue)
					}
				}
				inventorySlot++;
			}
		}
	}

	private void drawQuantityButtons(int currMouseX, int currMouseY, int relativeX, int relativeY) {
		int itemID = selectedBankSlotItemID;
		if (this.selectedBankSlot > currentItems.size()) return;
		int amount = currentItems.get(this.selectedBankSlot).getAmount();

		int quantityColour = 0xffffff;
		if (getBankItemByID(itemID) != null && getBankItemByID(itemID).getAmount() > 0) {
			drawString(
				"Withdraw " + EntityHandler.getItemDef(itemID).getName(),
				relativeX + 2, relativeY + 248, 1, 0xffffff);

			boolean b = currMouseX >= relativeX + 305 && currMouseY >= relativeY + 240 &&
				currMouseX < relativeX + 335 && currMouseY <= relativeY + 251;
			if (Config.S_WANT_BANK_NOTES) {
				if (currMouseX >= relativeX + 220 && currMouseY >= relativeY + 240 &&
					currMouseX < relativeX + 250 && currMouseY <= relativeY + 251)
					quantityColour = 0xff0000;
				if (S_WANT_CERT_AS_NOTES) {
					drawString("Note: ", relativeX + 222, relativeY + 248, 1, quantityColour);
				} else {
					drawString("Certificate: ", relativeX + 187, relativeY + 248, 1, quantityColour);
				}
				drawString(swapNoteMode ? "On" : "Off",
					relativeX + 257, relativeY + 248, 1, swapNoteMode ? 0x00FF00 : 0xFF0000);

				quantityColour = 0xffffff;
				if (b)
					quantityColour = 0xff0000;
				drawString("One", relativeX + 307, relativeY + 248, 1, quantityColour);

			} else { // Authentic
				if (currMouseX >= relativeX + 220 && currMouseY >= relativeY + 240 &&
					currMouseX < relativeX + 250 && currMouseY <= relativeY + 251)
					quantityColour = 0xff0000;
				drawString("One", relativeX + 222, relativeY + 248, 1, quantityColour);

				if (amount >= 5) {
					quantityColour = 0xffffff;
					if (currMouseX >= relativeX + 250 && currMouseY >= relativeY + 240 &&
						currMouseX < relativeX + 280 && currMouseY <= relativeY + 251)
						quantityColour = 0xff0000;
					drawString("Five", relativeX + 252, relativeY + 248, 1, quantityColour);
				}

				if (amount >= 10) {
					quantityColour = 0xffffff;
					if (currMouseX >= relativeX + 280 && currMouseY >= relativeY + 240 &&
						currMouseX < relativeX + 305 && currMouseY <= relativeY + 251)
						quantityColour = 0xff0000;
					drawString("10", relativeX + 282, relativeY + 248, 1, quantityColour);
				}

				if (amount >= 50) {
					quantityColour = 0xffffff;
					if (b)
						quantityColour = 0xff0000;
					drawString("50", relativeX + 307, relativeY + 248, 1, quantityColour);
				}
			}

			quantityColour = 0xffffff;
			if (currMouseX >= relativeX + 340 && currMouseY >= relativeY + 240 &&
				currMouseX < relativeX + 368 && currMouseY <= relativeY + 251)
				quantityColour = 0xff0000;
			drawString("X", relativeX + 346, relativeY + 248, 1, quantityColour);

			quantityColour = 0xffffff;
			if (currMouseX >= relativeX + 370 && currMouseY >= relativeY + 240 &&
				currMouseX < relativeX + 400 && currMouseY <= relativeY + 251)
				quantityColour = 0xff0000;
			drawString("All", relativeX + 370, relativeY + 248, 1, quantityColour);
		}

		if (mc.getInventoryCount(itemID) > 0) {
			drawString("Deposit " + EntityHandler.getItemDef(itemID).getName(),
				relativeX + 2, relativeY + 273, 1, 0xffffff);

			quantityColour = 0xffffff;

			if (Config.S_WANT_CERT_DEPOSIT && BankUtil.isCert(itemID)) {
				if (currMouseX >= relativeX + 220 && currMouseY >= relativeY + 265 &&
					currMouseX < relativeX + 250 && currMouseY <= relativeY + 276)
					quantityColour = 0xff0000;
				drawString("Uncert: ", relativeX + 212, relativeY + 273, 1, quantityColour);
				drawString(swapCertMode ? "On" : "Off",
					relativeX + 257, relativeY + 273, 1, swapCertMode ? 0x00FF00 : 0xFF0000);

				quantityColour = 0xffffff;

				if (currMouseX >= relativeX + 305 && currMouseY >= relativeY + 265 &&
					currMouseX < relativeX + 335 && currMouseY <= relativeY + 276)
					quantityColour = 0xff0000;
				drawString("One", relativeX + 307, relativeY + 273, 1, quantityColour);
			} else {
				if (currMouseX >= relativeX + 220 && currMouseY >= relativeY + 265 &&
					currMouseX < relativeX + 250 && currMouseY <= relativeY + 276)
					quantityColour = 0xff0000;
				drawString("One", relativeX + 222, relativeY + 273, 1, quantityColour);

				if (mc.getInventoryCount(itemID) >= 5) {
					quantityColour = 0xffffff;
					if (currMouseX >= relativeX + 250 && currMouseY >= relativeY + 265 &&
						currMouseX < relativeX + 280 && currMouseY <= relativeY + 276)
						quantityColour = 0xff0000;
					drawString("Five", relativeX + 252, relativeY + 273, 1, quantityColour);
				}

				if (mc.getInventoryCount(itemID) >= 10) {
					quantityColour = 0xffffff;
					if (currMouseX >= relativeX + 280 && currMouseY >= relativeY + 265 &&
						currMouseX < relativeX + 305 && currMouseY <= relativeY + 276)
						quantityColour = 0xff0000;
					drawString("10", relativeX + 282, relativeY + 273, 1, quantityColour);
				}

				if (mc.getInventoryCount(itemID) >= 50) {
					quantityColour = 0xffffff;
					if (currMouseX >= relativeX + 305 && currMouseY >= relativeY + 265 &&
						currMouseX < relativeX + 335 && currMouseY <= relativeY + 276)
						quantityColour = 0xff0000;
					drawString("50", relativeX + 307, relativeY + 273, 1, quantityColour);
				}
			}

			quantityColour = 0xffffff;
			if (currMouseX >= relativeX + 340 && currMouseY >= relativeY + 265 &&
				currMouseX < relativeX + 368 && currMouseY <= relativeY + 276)
				quantityColour = 0xff0000;
			drawString("X", relativeX + 346, relativeY + 273, 1, quantityColour);

			quantityColour = 0xffffff;
			if (currMouseX >= relativeX + 370 && currMouseY >= relativeY + 265 &&
				currMouseX < relativeX + 400 && currMouseY <= relativeY + 276)
				quantityColour = 0xff0000;
			drawString("All", relativeX + 370, relativeY + 273, 1, quantityColour);
		}
	}

	void bankClose() {
		mc.setShowDialogBank(false);
		this.selectedBankSlot = -1;
		mc.packetHandler.getClientStream().newPacket(212);
		mc.packetHandler.getClientStream().finishPacket();
	}

	public void sendDeposit(int i) {
		int itemID = currentItems.get(this.selectedBankSlot).getCatalogID();
		mc.packetHandler.getClientStream().newPacket(23);
		mc.packetHandler.getClientStream().bufferBits.putShort(itemID);
		if (i > mc.getInventoryCount(itemID)) {
			i = mc.getInventoryCount(itemID);
		}
		mc.packetHandler.getClientStream().bufferBits.putInt(i);
		mc.packetHandler.getClientStream().finishPacket();
		if (mc.getMouseButtonDownTime() == 0) {
			mc.setMouseClick(0);
			mc.setMouseButtonDown(0);
		}
		if (mc.getInventoryCount(itemID) - i < 1) this.selectedBankSlot = -1;
		// checks if player has an uncerted item in bank when depositing to item a cert
		// if not clear the bank slot to force user update selected slot
		if (swapCertMode && BankUtil.isCert(itemID)) {
			ArrayList<Integer> bankIds = getBankItemIds();
			if (!bankIds.contains(BankUtil.uncertedID(itemID))) this.selectedBankSlot = -1;
		}
	}


	public void sendWithdraw(int i) {
		Item item = getBankItemByID(this.selectedBankSlotItemID);
		if (item == null) return;
		int itemID = item.getCatalogID();
		int amt = item.getAmount();
		mc.packetHandler.getClientStream().newPacket(22);
		mc.packetHandler.getClientStream().bufferBits.putShort(itemID);
		if (i > amt) {
			i = amt;
		}
		mc.packetHandler.getClientStream().bufferBits.putInt(i);

		if (Config.S_WANT_BANK_NOTES)
			mc.packetHandler.getClientStream().bufferBits.putByte(swapNoteMode ? 1 : 0);

		mc.packetHandler.getClientStream().finishPacket();
		if (mc.getMouseButtonDownTime() == 0) {
			mc.setMouseClick(0);
			mc.setMouseButtonDown(0);
		}
		if (amt - i < 1) this.selectedBankSlot = -1;
	}


	public void drawString(String str, int x, int y, int font, int color) {
		mc.getSurface().drawString(str, x, y, color, font);
	}

	public void resetBank() {
		bankItems.clear();
	}

	public void addBank(int bankID, int itemID, int amount) {
		bankItems.add(new BankItem(bankID, itemID, amount));
	}

	public void updateBank(int slot, int itemID, int amount) {
		if (amount == 0) {
			bankItems.remove(slot);
			for (slot = 0; slot < bankItems.size(); slot++) {
				bankItems.get(slot).bankID = slot;
			}
			return;
		}
		if (bankItems.size() <= slot) {
			bankItems.add(new BankItem(slot, itemID, amount));
		}
		if (bankItems.get(slot) != null) {
			bankItems.get(slot).bankID = slot;
			bankItems.get(slot).getItem().setItemDef(itemID);
			bankItems.get(slot).getItem().setAmount(amount);
		}
	}

	private void sendCertMode() {
		mc.packetHandler.getClientStream().newPacket(199);
		mc.packetHandler.getClientStream().bufferBits.putByte(0);
		mc.packetHandler.getClientStream().bufferBits.putByte(swapCertMode ? 1 : 0);
		mc.packetHandler.getClientStream().finishPacket();
	}

	private ArrayList<Integer> getBankItemIds() {
		ArrayList<Integer> idList = new ArrayList<Integer>();
		for (Item b : currentItems) {
			idList.add(b.getCatalogID());
		}
		return idList;
	}

	private Item getBankItemByID(int ID) {
		for (BankItem i : bankItems) {
			if (i.getItem().getCatalogID() == ID) {
				return i.getItem();
			}
		}
		return null;
	}

	public int maximumBankItemsSupported() {
		if (bankItems instanceof ArrayList) {
			// Depends on ArrayList implementation, but this is a generally safe upper limit for its capacity
			return Integer.MAX_VALUE;
		} else {
			// this just gets arbitrarily resized by the server & isn't a real limitation
			return mc.bankItemsMax;
		}
	}

	class BankItem {

		int bankID;
		Item item;

		BankItem(int bankID, int itemID, int amount) {
			this.item = new Item();
			this.item.setItemDef(itemID);
			this.item.setAmount(amount);
			this.item.setDurability(100);
			this.item.setCharges(0);
			this.item.setEquipped(false);
			this.item.setNoted(false);
			this.bankID = bankID;
		}

		public Item getItem() { return this.item; }
	}
}
