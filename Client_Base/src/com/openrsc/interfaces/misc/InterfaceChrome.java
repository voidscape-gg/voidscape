package com.openrsc.interfaces.misc;

import orsc.graphics.gui.UiSkin;
import orsc.graphics.two.GraphicsController;

/**
 * Shared chrome for the custom interface fleet (docs/UI-STYLE-GUIDE.md §4.3).
 *
 * Draw-only: hit-tests, click handling, and packet writes stay with each
 * interface. The glass mandate (§1.3) applies — windows are translucent
 * glass cards; no opaque plates.
 */
public final class InterfaceChrome {

	private InterfaceChrome() {
	}

	public static final int TITLE_H = 24;
	public static final int CLOSE_SIZE = 20;

	/**
	 * Standard glass window: card + header strip + gold title + X close.
	 * Content starts at y + TITLE_H. Callers hit-test the close button against
	 * closeX/closeY/CLOSE_SIZE and pass hover state in.
	 */
	public static void window(GraphicsController g, int x, int y, int width, int height,
							  String title, boolean closeHover) {
		UiSkin.glassPanel(g, x, y, width, height, UiSkin.A_GLASS_TEXT);
		g.drawBoxAlpha(x + 1, y + 1, width - 2, TITLE_H - 1, UiSkin.VOID_HEADER, 190);
		g.drawLineHoriz(x + 2, y + TITLE_H - 1, width - 4, UiSkin.GOLD_LINE);
		if (title != null) {
			g.drawColoredStringCentered(x + width / 2, title, UiSkin.GOLD_TITLE, 0, UiSkin.FONT_TITLE, y + 17);
		}
		UiSkin.closeButton(g, closeX(x, width), closeY(y), CLOSE_SIZE, closeHover);
	}

	public static int closeX(int x, int width) {
		return x + width - CLOSE_SIZE - 4;
	}

	public static int closeY(int y) {
		return y + 2;
	}

	/** Section header strip (replaces the fleet's blue 0x6580B7 rows). */
	public static void sectionStrip(GraphicsController g, int x, int y, int width, int height) {
		g.drawBoxAlpha(x, y, width, height, UiSkin.PURPLE_SELECT, 170);
		g.drawLineHoriz(x, y + height - 1, width, UiSkin.GOLD_LINE);
	}

	/**
	 * Standard button (replaces the fleet's duplicated grey drawButton copies).
	 * Returns hover so callers keep their existing click dispatch.
	 */
	public static boolean button(GraphicsController g, int x, int y, int width, int height,
								 String label, int font, boolean selected, boolean disabled,
								 int mouseX, int mouseY) {
		boolean hover = !disabled && UiSkin.hit(x, y, width, height, mouseX, mouseY);
		UiSkin.button(g, x, y, width, height, label, hover, selected, disabled, font);
		return hover;
	}
}
