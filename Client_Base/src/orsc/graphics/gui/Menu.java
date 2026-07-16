package orsc.graphics.gui;

import orsc.enumerations.MenuItemAction;
import orsc.graphics.two.MudClientGraphics;
import orsc.util.ArrayUtil;
import orsc.util.GenUtil;

import static orsc.Config.C_CUSTOM_UI;
import static orsc.Config.S_WANT_CUSTOM_UI;
import static orsc.osConfig.F_ANDROID_BUILD;

public final class Menu {
	public static final int PAGE_CONTROL_CLICK = -3;
	private static final int ANDROID_MIN_LINE_HEIGHT = 28;
	private static final int VOIDSCAPE_UI_TINT = 0x3C3125; // menu-specific tint — no UiSkin token, see docs/UI-STYLE-GUIDE.md
	private static final int VOIDSCAPE_UI_LINE = UiSkin.GOLD_LINE;
	private static final int VOIDSCAPE_UI_PURPLE = UiSkin.PURPLE_SELECT;
	private static final int VOIDSCAPE_UI_GOLD = UiSkin.GOLD_RING;
	private static final int VOIDSCAPE_MENU_ALPHA = 132;
	private static final int VOIDSCAPE_MENU_LINE_ALPHA = 116;
	private static final int VOIDSCAPE_MENU_HEADER_ALPHA = 96;
	private static final int VOIDSCAPE_MENU_HOVER_ALPHA = 58;
	public int font;
	private int itemCount;
	private int maximumHeight;
	private int menuHeight;
	private MenuItem[] menuItems;
	private String menuTitle;
	private int menuWidth;
	private int minimumLineHeight;
	private int minimumWidth;
	private int pageIndex;
	private MudClientGraphics surf;

	public Menu(MudClientGraphics var1, int var2) {
		this(var1, var2, (String) null);
	}

	public Menu(MudClientGraphics surf, int font, String title) {
		this.maximumHeight = 0;
		this.menuHeight = 0;
		this.itemCount = 0;
		this.menuWidth = 0;
		this.minimumLineHeight = 0;
		this.minimumWidth = 0;
		this.pageIndex = 0;

		try {
			this.font = font;
			this.menuItems = new MenuItem[10];
			this.surf = surf;
			this.menuTitle = title;

			for (int var4 = 0; var4 < 10; ++var4) {
				this.menuItems[var4] = new MenuItem();
			}

			this.calculateMenuWidth();
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "wb.<init>(" + (surf != null ? "{...}" : "null") + ',' + font + ','
				+ (title != null ? "{...}" : "null") + ')');
		}
	}

	public final void addCharacterItem(int index, MenuItemAction actionID, String label, String actor) {
		try {
			this.addItem(0, label, 0, 0, actor, index, (String) null, actionID, 0, (String) null, (String) null);

		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7, "wb.DA(" + index + ',' + actionID + ',' + false + ','
				+ (label != null ? "{...}" : "null") + ',' + (actor != null ? "{...}" : "null") + ')');
		}
	}

	public final void addCharacterItem_WithID(int targetPlayer, String actor, MenuItemAction actionID, String label,
											  int selectedIndex) {
		try {

			this.addItem(selectedIndex, label, 0, 0, actor, targetPlayer, (String) null, actionID, 0, (String) null,
				(String) null);
		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8, "wb.J(" + targetPlayer + ',' + (actor != null ? "{...}" : "null") + ','
				+ actionID + ',' + (label != null ? "{...}" : "null") + ',' + selectedIndex + ',' + "dummy" + ')');
		}
	}

	public void addItem(int id_or_z, String label, int dir, int var4, String actor, int index_or_x,
						 String dropped2, MenuItemAction actionID, int tile_id, String dropped, String strB) {
		try {
			if (this.menuItems.length == this.itemCount) {
				MenuItem[] src = this.menuItems;
				this.menuItems = new MenuItem[10 + this.itemCount];

				for (int i = 0; this.menuItems.length > i; ++i) {
					if (this.itemCount > i) {
						this.menuItems[i] = src[i];
					} else {
						this.menuItems[i] = new MenuItem();
					}
				}
			}


			this.menuItems[this.itemCount++].set(label, var4, index_or_x, id_or_z, tile_id, dropped2, 100, actionID,
				actor, dropped, dir, strB);
			this.calculateMenuWidth();
		} catch (RuntimeException var15) {
			throw GenUtil.makeThrowable(var15, "wb.N(" + id_or_z + ',' + (label != null ? "{...}" : "null") + ',' + dir
				+ ',' + var4 + ',' + (actor != null ? "{...}" : "null") + ',' + index_or_x + ','
				+ (dropped2 != null ? "{...}" : "null") + ',' + actionID + ',' + tile_id + ',' + "dummy" + ','
				+ (dropped != null ? "{...}" : "null") + ',' + (strB != null ? "{...}" : "null") + ')');
		}
	}

	public final void addItem(MenuItemAction action, String actor, String label) {
		try {

			this.addItem(0, label, 0, 0, actor, 0, (String) null, action, 0, (String) null, (String) null);
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "wb.V(" + action + ',' + (actor != null ? "{...}" : "null") + ','
				+ (label != null ? "{...}" : "null") + ',' + "dummy" + ')');
		}
	}

	public final void addItem_With2Strings(String label, String actor, String dropped, MenuItemAction actionID,
										   String strB) {
		try {
			this.addItem(0, label, 0, 0, actor, 0, (String) null, actionID, 0, dropped, strB);

		} catch (RuntimeException var8) {
			throw GenUtil.makeThrowable(var8,
				"wb.E(" + (label != null ? "{...}" : "null") + ',' + (actor != null ? "{...}" : "null") + ','
					+ (dropped != null ? "{...}" : "null") + ',' + actionID + ','
					+ (strB != null ? "{...}" : "null") + ',' + "dumb" + ')');
		}
	}

	public final void addTileItem(int x, byte var2, MenuItemAction actID, String label, String actor, int dir, int z) {
		try {

			this.addItem(z, label, dir, 0, actor, x, (String) null, actID, 0, (String) null, (String) null);
		} catch (RuntimeException var9) {
			throw GenUtil.makeThrowable(var9,
				"wb.W(" + x + ',' + 22 + ',' + actID + ',' + (label != null ? "{...}" : "null") + ','
					+ (actor != null ? "{...}" : "null") + ',' + dir + ',' + z + ')');
		}
	}

	public final void addTileItem_WithID(MenuItemAction actID, int z, int dir, int x, int id, String actor,
										 String name) {
		try {

			this.addItem(z, name, dir, 0, actor, x, (String) null, actID, id, (String) null, (String) null);
		} catch (RuntimeException var10) {
			throw GenUtil.makeThrowable(var10, "wb.B(" + actID + ',' + z + ',' + dir + ',' + x + ',' + "dummy" + ','
				+ id + ',' + (actor != null ? "{...}" : "null") + ',' + (name != null ? "{...}" : "null") + ')');
		}
	}

	public final void addUseOnObject(int var1, String label, int var3, int var4, int var5, MenuItemAction actID,
									 int var7, String actor, int var9) {
		try {

			this.addItem(var1, label, var7, var4, actor, var9, (String) null, actID, var5, (String) null,
				(String) null);
		} catch (RuntimeException var11) {
			throw GenUtil.makeThrowable(var11,
				"wb.I(" + var1 + ',' + (label != null ? "{...}" : "null") + ',' + var3 + ',' + var4 + ',' + var5
					+ ',' + actID + ',' + var7 + ',' + (actor != null ? "{...}" : "null") + ',' + var9 + ')');
		}
	}

	private void calculateMenuWidth() {
		try {

			int lineHeight = this.lineHeight();
			if (null == this.menuTitle) {
				this.menuWidth = 0;
			} else {
				this.menuWidth = 5 + this.surf.stringWidth(this.font, this.menuTitle);
			}

			for (int i = 0; this.itemCount > i; ++i) {
				int lineWidth = 5
					+ this.surf.stringWidth(this.font, this.menuItems[i].label + " " + this.menuItems[i].actor);
				if (lineWidth > this.menuWidth) {
					this.menuWidth = lineWidth;
				}
			}
			if (this.isPaginated()) {
				int pageControlWidth = 5 + this.surf.stringWidth(this.font, this.pageControlLabel());
				if (pageControlWidth > this.menuWidth) {
					this.menuWidth = pageControlWidth;
				}
			}
			this.menuWidth = Math.max(this.menuWidth, this.minimumWidth);
			this.menuHeight = lineHeight * (this.titleRowCount() + this.visibleItemCount()
				+ (this.isPaginated() ? 1 : 0));

		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "wb.EA(" + "dummy" + ')');
		}
	}

	public final int getHeight() {
		try {

			return this.menuHeight;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "wb.T(" + "dummy" + ')');
		}
	}

	public final int getLineHeight() {
		return this.lineHeight();
	}

	public final int getPageCount() {
		return this.pageCount();
	}

	public final int getPageNumber() {
		return this.normalizedPageIndex() + 1;
	}

	public final boolean isPaginatedMenu() {
		return this.isPaginated();
	}

	/**
	 * Configures touch geometry without coupling this shared menu to a platform
	 * viewport. Zero values restore the legacy sizing behavior.
	 */
	public final void configureTouchLayout(int lineHeight, int width, int height) {
		this.minimumLineHeight = Math.max(0, lineHeight);
		this.minimumWidth = Math.max(0, width);
		this.maximumHeight = Math.max(0, height);
		this.pageIndex = 0;
		this.calculateMenuWidth();
	}

	public final void advancePage() {
		int pages = this.pageCount();
		if (pages <= 1) {
			return;
		}
		this.pageIndex = (this.normalizedPageIndex() + 1) % pages;
		this.calculateMenuWidth();
	}

	public final MenuItemAction getItemAction(int item) {
		try {

			return this.menuItems[item].actionID;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.M(" + "dummy" + ',' + item + ')');
		}
	}

	public final String getItemActor(int item) {
		try {

			return this.menuItems[item].actor;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.O(" + "dummy" + ',' + item + ')');
		}
	}

	public final int getItemCount(int var1) {
		try {

			if (var1 != -27153) {
				this.calculateMenuWidth();
			}

			return this.itemCount;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "wb.F(" + var1 + ')');
		}
	}

	public final int getItemDirection(int item) {
		try {

			return this.menuItems[item].dir;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.H(" + item + ',' + "dummy" + ')');
		}
	}

	public final int getItemIdOrZ(int var2) {
		try {

			return this.menuItems[var2].id_or_z;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.K(" + 97 + ',' + var2 + ')');
		}
	}

	public final int getItemIndexOrX(int item) {
		try {

			return this.menuItems[item].index_or_x;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.C(" + true + ',' + item + ')');
		}
	}

	public final String getItemLabel(int var1) {
		try {

			return this.menuItems[var1].label;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.A(" + var1 + ',' + "dummy" + ')');
		}
	}

	public final int getItemParam_l(int item) {
		try {

			return this.menuItems[item].m_l;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.Q(" + "dummy" + ',' + item + ')');
		}
	}

	public final String getItemStringB(int item) {
		try {

			return this.menuItems[item].strB;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.CA(" + item + ',' + "dummy" + ')');
		}
	}

	public final int getItemTileID(int item) {
		try {

			return this.menuItems[item].tile_id;
		} catch (RuntimeException var4) {
			throw GenUtil.makeThrowable(var4, "wb.L(" + item + ',' + "dummy" + ')');
		}
	}

	public final int getWidth() {
		try {

			return this.menuWidth;
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "wb.BA(" + "dummy" + ')');
		}
	}

	public final int handleClick(int mouseX, int menuX, int menuY, int mouseY) {
		try {

			return this.process(mouseY, mouseX, menuY, menuX, -3, false);
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7,
				"wb.D(" + mouseX + ',' + menuX + ',' + menuY + ',' + -40 + ',' + mouseY + ')');
		}
	}

	private int process(int mouseY, int mouseX, int menuY, int menuX, int var5, boolean draw) {
		try {

			if (this.menuWidth != 0 && this.menuHeight != 0) {
				if (draw) {
					if (useVoidscapeMenuStyle()) {
						this.drawVoidscapeMenuShell(menuX, menuY);
					} else {
						this.surf.drawBoxAlpha(menuX, menuY, this.menuWidth, this.menuHeight, 13684944, 160);
					}
				}

				int lineHeight = this.lineHeight();
				int lineY = menuY;
				int clickedLine = -1;
				if (null != this.menuTitle) {
					if (menuX < mouseX && mouseY >= lineY && mouseY < lineY + lineHeight
						&& mouseX < menuX + this.menuWidth) {
						if (!draw) {
							return -2;
						}

						clickedLine = -2;
					}

					if (draw) {
						this.surf.drawString(this.menuTitle, 2 + menuX, textBaselineY(lineY, lineHeight), 0xFFFF, this.font);
					}

					lineY += lineHeight;
				}

				if (var5 >= -1) {
					this.menuTitle = (String) null;
				}

				int firstItem = this.firstVisibleItem();
				int lastItem = firstItem + this.visibleItemCount();
				for (int i = firstItem; i < lastItem; ++i) {
					int lineColor = 16777215;
					if (menuX < mouseX && mouseY >= lineY && mouseY < lineY + lineHeight
						&& menuX + this.menuWidth > mouseX) {
						lineColor = 16776960;
						if (!draw) {
							return i;
						}

						clickedLine = i;
					}

					if (draw) {
						if (useVoidscapeMenuStyle() && clickedLine == i) {
							this.surf.drawBoxAlpha(menuX, lineY, this.menuWidth, lineHeight,
								VOIDSCAPE_UI_GOLD, VOIDSCAPE_MENU_HOVER_ALPHA);
							this.surf.drawLineAlpha(menuX + 2, lineY + lineHeight - 1,
								menuX + this.menuWidth - 3, lineY + lineHeight - 1,
								VOIDSCAPE_UI_GOLD, VOIDSCAPE_MENU_LINE_ALPHA);
						}
						this.surf.drawString(this.menuItems[i].label + " " + this.menuItems[i].actor, menuX + 2,
							textBaselineY(lineY, lineHeight), lineColor, this.font);
					}

					lineY += lineHeight;
				}

				if (this.isPaginated()) {
					boolean hovered = menuX < mouseX && mouseY >= lineY && mouseY < lineY + lineHeight
						&& menuX + this.menuWidth > mouseX;
					if (hovered && !draw) {
						return PAGE_CONTROL_CLICK;
					}
					if (draw) {
						if (useVoidscapeMenuStyle() && hovered) {
							this.surf.drawBoxAlpha(menuX, lineY, this.menuWidth, lineHeight,
								VOIDSCAPE_UI_GOLD, VOIDSCAPE_MENU_HOVER_ALPHA);
						}
						this.surf.drawLineAlpha(menuX + 2, lineY,
							menuX + this.menuWidth - 3, lineY,
							VOIDSCAPE_UI_PURPLE, VOIDSCAPE_MENU_LINE_ALPHA);
						this.surf.drawString(this.pageControlLabel(), menuX + 2,
							textBaselineY(lineY, lineHeight), hovered ? 16776960 : VOIDSCAPE_UI_GOLD,
							this.font);
					}
				}

				return clickedLine;
			} else {
				return -1;
			}
		} catch (RuntimeException var12) {
			throw GenUtil.makeThrowable(var12,
				"wb.R(" + mouseY + ',' + mouseX + ',' + menuY + ',' + menuX + ',' + var5 + ',' + draw + ')');
		}
	}

	private int lineHeight() {
		int height = this.surf.fontHeight(this.font) + 1;
		if (F_ANDROID_BUILD) {
			height = Math.max(height, ANDROID_MIN_LINE_HEIGHT);
		}
		return Math.max(height, this.minimumLineHeight);
	}

	private int titleRowCount() {
		return this.menuTitle == null ? 0 : 1;
	}

	private int maximumRows() {
		if (this.maximumHeight <= 0) {
			return Integer.MAX_VALUE;
		}
		return Math.max(this.titleRowCount() + 2, this.maximumHeight / Math.max(1, this.lineHeight()));
	}

	private boolean isPaginated() {
		return this.maximumHeight > 0
			&& this.titleRowCount() + this.itemCount > this.maximumRows();
	}

	private int pageCapacity() {
		if (!this.isPaginated()) {
			return Math.max(1, this.itemCount);
		}
		return Math.max(1, this.maximumRows() - this.titleRowCount() - 1);
	}

	private int pageCount() {
		if (!this.isPaginated()) {
			return 1;
		}
		int capacity = this.pageCapacity();
		return Math.max(1, (this.itemCount + capacity - 1) / capacity);
	}

	private int normalizedPageIndex() {
		return Math.max(0, Math.min(this.pageIndex, this.pageCount() - 1));
	}

	private int firstVisibleItem() {
		return this.isPaginated() ? this.normalizedPageIndex() * this.pageCapacity() : 0;
	}

	private int visibleItemCount() {
		return Math.max(0, Math.min(this.pageCapacity(), this.itemCount - this.firstVisibleItem()));
	}

	private String pageControlLabel() {
		int pages = this.pageCount();
		int nextPage = pages <= 1 ? 1 : (this.normalizedPageIndex() + 1) % pages + 1;
		return nextPage == 1 ? "Back to first options" : "More options (" + nextPage + "/" + pages + ")";
	}

	private int textBaselineY(int lineY, int lineHeight) {
		return lineY + (lineHeight + this.surf.fontHeight(this.font)) / 2 - 2;
	}

	private boolean useVoidscapeMenuStyle() {
		return C_CUSTOM_UI || F_ANDROID_BUILD && S_WANT_CUSTOM_UI;
	}

	private void drawVoidscapeMenuShell(int menuX, int menuY) {
		this.surf.drawBoxAlpha(menuX - 2, menuY - 2, this.menuWidth + 4, this.menuHeight + 4,
			VOIDSCAPE_UI_TINT, VOIDSCAPE_MENU_ALPHA);
		this.surf.drawBoxAlpha(menuX, menuY, this.menuWidth, Math.min(this.menuHeight, this.lineHeight()),
			VOIDSCAPE_UI_TINT, VOIDSCAPE_MENU_HEADER_ALPHA);
		int left = menuX - 2;
		int top = menuY - 2;
		int right = menuX + this.menuWidth + 1;
		int bottom = menuY + this.menuHeight + 1;
		this.surf.drawLineAlpha(left, top, right, top, VOIDSCAPE_UI_LINE, VOIDSCAPE_MENU_LINE_ALPHA);
		this.surf.drawLineAlpha(left, bottom, right, bottom, VOIDSCAPE_UI_LINE, VOIDSCAPE_MENU_LINE_ALPHA);
		this.surf.drawLineAlpha(left, top, left, bottom, VOIDSCAPE_UI_LINE, VOIDSCAPE_MENU_LINE_ALPHA);
		this.surf.drawLineAlpha(right, top, right, bottom, VOIDSCAPE_UI_LINE, VOIDSCAPE_MENU_LINE_ALPHA);
		this.surf.drawLineAlpha(menuX, menuY + this.lineHeight() - 1,
			menuX + this.menuWidth - 1, menuY + this.lineHeight() - 1,
			VOIDSCAPE_UI_PURPLE, 96);
	}

	public final void recalculateSize(int var1) {
		try {

			this.itemCount = var1;
			this.pageIndex = 0;
			this.calculateMenuWidth();
		} catch (RuntimeException var3) {
			throw GenUtil.makeThrowable(var3, "wb.P(" + var1 + ')');
		}
	}

	public final void removeItem(int item) {
		try {

			if (item >= 0 && this.itemCount > item) {
				MenuItem removed = this.menuItems[item];

				for (int i = item; i < this.itemCount - 1; ++i) {
					this.menuItems[i] = this.menuItems[1 + i];
				}

				this.menuItems[--this.itemCount] = removed;
				this.calculateMenuWidth();
			}
		} catch (RuntimeException var5) {
			throw GenUtil.makeThrowable(var5, "wb.G(" + "dummy" + ',' + item + ')');
		}
	}

	public final int render(int menuY, int menuX, int mouseY, byte var4, int mouseX) {
		try {

			return this.process(mouseY, mouseX, menuY, menuX, -66, true);
		} catch (RuntimeException var7) {
			throw GenUtil.makeThrowable(var7,
				"wb.U(" + menuY + ',' + menuX + ',' + mouseY + ',' + -12 + ',' + mouseX + ')');
		}
	}

	public final void sort() {
		try {

			if (this.itemCount != 0) {
				int[] priority = new int[this.itemCount];
				MenuItem[] src = new MenuItem[this.itemCount];

				int i;
				for (i = 0; this.itemCount > i; ++i) {
					MenuItem tmp = this.menuItems[i];
					priority[i] = tmp.actionID.priority();
					src[i] = tmp;
				}

				ArrayUtil.quickSort(src, priority);
				i = 0;

				while (i < this.itemCount) {
					this.menuItems[i] = (MenuItem) src[i];
					++i;
				}

			}
		} catch (RuntimeException var6) {
			throw GenUtil.makeThrowable(var6, "wb.AA(" + "dummy" + ')');
		}
	}
}
