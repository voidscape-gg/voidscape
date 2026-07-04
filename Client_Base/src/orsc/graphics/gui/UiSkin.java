package orsc.graphics.gui;

import orsc.graphics.two.GraphicsController;

/**
 * Voidscape UI design tokens + shared chrome vocabulary.
 *
 * See docs/UI-STYLE-GUIDE.md. No UI hex literal may live outside this class:
 * consume a token or add one here with a source citation in the style guide.
 *
 * Client_Base-pure: no AWT, no PC_Client/Android imports (compiled into the
 * Android APK and TeaVM builds). Colors are 0xRRGGBB ints — the framebuffer
 * has no alpha channel; alpha parameters are 0-256 blend weights.
 *
 * Palette fields are non-final so the voidscape.ui.* system properties can
 * retune them live (same convention as mudclient's voidscapeDebugColorProperty).
 */
public final class UiSkin {

	private UiSkin() {
	}

	// --- palette -----------------------------------------------------------
	public static int VOID_SCRIM = colorProperty("voidscape.ui.VOID_SCRIM", 0x050308);
	public static int VOID_HEADER = colorProperty("voidscape.ui.VOID_HEADER", 0x0D0914);
	public static int VOID_BODY = colorProperty("voidscape.ui.VOID_BODY", 0x0B0810);
	public static int VOID_BOX = colorProperty("voidscape.ui.VOID_BOX", 0x130E1A);
	public static int VOID_LINE = colorProperty("voidscape.ui.VOID_LINE", 0x2E2140);
	public static int BORDER_INNER = colorProperty("voidscape.ui.BORDER_INNER", 0x271F2D);
	public static int SHADOW_A = colorProperty("voidscape.ui.SHADOW_A", 0x2F2434);
	public static int SHADOW_B = colorProperty("voidscape.ui.SHADOW_B", 0x18111E);
	public static int GOLD_BEVEL = colorProperty("voidscape.ui.GOLD_BEVEL", 0x8C6F3D);
	public static int GOLD_LINE = colorProperty("voidscape.ui.GOLD_LINE", 0x6E5737);
	public static int PURPLE_SELECT = colorProperty("voidscape.ui.PURPLE_SELECT", 0x4B2472);
	public static int PURPLE_EDGE = colorProperty("voidscape.ui.PURPLE_EDGE", 0x6A4FA0);
	public static int PURPLE_BRIGHT = colorProperty("voidscape.ui.PURPLE_BRIGHT", 0x8F2BFF);
	public static int PURPLE_BLOOM = colorProperty("voidscape.ui.PURPLE_BLOOM", 0xC35CFF);
	public static int PURPLE_FOCUS = colorProperty("voidscape.ui.PURPLE_FOCUS", 0xB68AFF);
	public static int GOLD_TITLE = colorProperty("voidscape.ui.GOLD_TITLE", 0xF0DFA3);
	public static int GOLD_HEADER = colorProperty("voidscape.ui.GOLD_HEADER", 0xE4D08D);
	public static int GOLD_HOT = colorProperty("voidscape.ui.GOLD_HOT", 0xFFD968);
	public static int GOLD_RING = colorProperty("voidscape.ui.GOLD_RING", 0xF6DA7D);
	public static int TEXT_BODY = colorProperty("voidscape.ui.TEXT_BODY", 0xE7DEBC);
	public static int TEXT_LABEL = colorProperty("voidscape.ui.TEXT_LABEL", 0xB7ABC8);
	public static int TEXT_DIM = colorProperty("voidscape.ui.TEXT_DIM", 0x8E7EA7);
	public static int GOOD = colorProperty("voidscape.ui.GOOD", 0x6FE38A);
	public static int BAD = colorProperty("voidscape.ui.BAD", 0xFF6B6B);
	public static int FLASH = colorProperty("voidscape.ui.FLASH", 0xFF4D4D);
	public static int CLOSE_FILL = colorProperty("voidscape.ui.CLOSE_FILL", 0x221A24);
	public static int CLOSE_BORDER = colorProperty("voidscape.ui.CLOSE_BORDER", 0x7D6F8B);
	public static int DANGER_HOVER = colorProperty("voidscape.ui.DANGER_HOVER", 0x6E1E1E);
	public static int DANGER_GLYPH = colorProperty("voidscape.ui.DANGER_GLYPH", 0xFF7070);
	public static int FIELD_BG = colorProperty("voidscape.ui.FIELD_BG", 0x07090C);
	public static int FIELD_BORDER_IDLE = colorProperty("voidscape.ui.FIELD_BORDER_IDLE", 0x56606A);
	/** Parchment fallback tint matching right-panel-bg.png (panel chrome + Menu tint). */
	public static int TINT_PARCHMENT = colorProperty("voidscape.ui.TINT_PARCHMENT", 0x3C3125);

	// --- glass family (owner direction 2026-07-04: the translucent light-purple
	// "Void Glass" look is THE standard panel treatment — panels stay see-through;
	// opaque backdrops are reserved for nothing). Canonical source: the VG bank
	// (BankInterface VG_BODY/VG_FRAME_INNER/glass sheen).
	public static int GLASS_BODY = colorProperty("voidscape.ui.GLASS_BODY", 0x160B2C);
	public static int GLASS_RIM = colorProperty("voidscape.ui.GLASS_RIM", 0x8A6BD8);
	public static int GLASS_SHEEN = colorProperty("voidscape.ui.GLASS_SHEEN", 0xBFA8FF);

	// --- alpha ladder (0-256 blend weights, see style guide §1.2) -----------
	public static final int A_SCRIM = 50;
	public static final int A_HEADER = 218;
	public static final int A_MODAL = 218;
	public static final int A_CHAT = 170;
	public static final int A_BUTTON = 200;
	public static final int A_TAB_ACTIVE = 235;
	public static final int A_HOVER_ROW = 116;
	public static final int A_FIELD = 218;
	public static final int A_TOOLTIP = 232;
	public static final int A_CLOSE = 220;
	/** Glass body over the 3D world (hero/grid surfaces — the VG bank's weight). */
	public static final int A_GLASS = 112;
	/** Glass body under dense text (modals, cards): still see-through, but legible. */
	public static final int A_GLASS_TEXT = 168;
	public static final int A_SHEEN = 12;

	// --- typography (style guide §1.4) --------------------------------------
	public static final int FONT_SMALL = 0;   // h11p — captions/values
	public static final int FONT_BODY = 1;    // h12b — rows/body/tooltips
	public static final int FONT_TITLE = 4;   // h14b — panel titles/strip labels
	public static final int FONT_DISPLAY = 5; // h16b — pre-game hero titles only
	public static final int FONT_HUGE = 7;    // h24b — sleep screen only

	// --- geometry constants --------------------------------------------------
	public static final int TITLE_BAR_H = 24;
	/** Standard modal widths (style guide §1.5); clamp via {@link #modalWidth}. */
	public static final int MODAL_W_MESSAGE = 400;
	public static final int MODAL_W_GRID = 468;

	// --- world-color exemption (style guide §1.1): a UI palette sweep must
	// never touch these — they are scene colors, not chrome.
	public static final int WORLD_ROUTE_GREEN = 0x40FF40;
	public static final int WORLD_MARKER_MINT = 0x2AD28C;
	public static final int WORLD_MARKER_MINT_EDGE = 0xA8FFD6;
	public static final int WORLD_HP_GREEN = 0x00FF00;
	public static final int WORLD_HP_RED = 0xFF0000;

	// --- sprite indirection ---------------------------------------------------
	// UiSkin never touches cache loaders: the host client wires its skin-asset
	// path in at startup. A null hook or a false return means the code-drawn
	// fallback recipe runs — missing assets can never produce a pink placeholder.
	public interface SpriteHook {
		boolean nineSlice(String asset, int x, int y, int width, int height, int corner, int topCenterW, int bottomCenterW);

		boolean threeSliceH(String asset, int x, int y, int width, int height, int cap, int centerW);
	}

	private static SpriteHook spriteHook;

	public static void setSpriteHook(SpriteHook hook) {
		spriteHook = hook;
	}

	private static boolean nineSlice(String asset, int x, int y, int width, int height, int corner, int topCenterW, int bottomCenterW) {
		return spriteHook != null && spriteHook.nineSlice(asset, x, y, width, height, corner, topCenterW, bottomCenterW);
	}

	// --- chrome helpers (geometry in logical game coordinates) ----------------

	/**
	 * Standard void panel: scrim wash + header strip + body underlay, framed by
	 * the nine-slice asset when available, else the glass-kit bevel recipe.
	 */
	public static void panel(GraphicsController g, int x, int y, int width, int height, String title, int bodyAlpha) {
		g.drawBoxAlpha(x, y, width, height, VOID_SCRIM, A_SCRIM);
		g.drawBoxAlpha(x + 1, y + 1, width - 2, TITLE_BAR_H - 1, VOID_HEADER, A_HEADER);
		g.drawBoxAlpha(x + 1, y + TITLE_BAR_H, width - 2, height - TITLE_BAR_H - 1, VOID_BODY, clampAlpha(bodyAlpha));
		if (!nineSlice("right-panel-frame-thin.png", x, y, width, height, 32, 16, 16)) {
			g.drawLineHoriz(x, y, width, GOLD_BEVEL);
			g.drawLineHoriz(x, y + height - 1, width, SHADOW_A);
			g.drawLineVert(x, y, GOLD_BEVEL, height);
			g.drawLineVert(x + width - 1, y, SHADOW_A, height);
			g.drawLineHoriz(x + 2, y + TITLE_BAR_H - 1, width - 4, GOLD_LINE);
			g.drawLineHoriz(x + 2, y + TITLE_BAR_H, width - 4, SHADOW_B);
			g.drawBorder(x + 2, y + 2, width - 4, height - 4, BORDER_INNER);
		}
		if (title != null) {
			g.drawColoredStringCentered(x + width / 2, title, GOLD_TITLE, 0, FONT_TITLE, y + 17);
		}
	}

	/**
	 * The standard glass card (VG-bank recipe): translucent body + upper sheen,
	 * black outer border, lavender inner rim. Callers draw their own title/content.
	 */
	public static void glassPanel(GraphicsController g, int x, int y, int width, int height, int bodyAlpha) {
		g.drawBoxAlpha(x, y, width, height, GLASS_BODY, clampAlpha(bodyAlpha));
		g.drawBoxAlpha(x + 1, y + 1, width - 2, (height - 2) * 2 / 5, GLASS_SHEEN, A_SHEEN);
		g.drawBorder(x, y, width, height, 0);
		g.drawBorder(x + 1, y + 1, width - 2, height - 2, GLASS_RIM);
	}

	/** Preferred modal width clamped to the frame (style guide §1.5: gameWidth - 16). */
	public static int modalWidth(int gameWidth, int preferredWidth) {
		return Math.min(preferredWidth, gameWidth - 16);
	}

	/**
	 * Standard modal dialog: glass card (text-tier alpha) plus, when a title is
	 * given, the InterfaceChrome.window header strip (VOID_HEADER @190 + GOLD_LINE
	 * seam + centered GOLD_TITLE font-4 title) — but no close button: the legacy
	 * black-box dialogs this replaces carry their own button/answer flows.
	 * Draw-only; callers keep hit-tests derived from the same x/y/width/height.
	 */
	public static void modal(GraphicsController g, int x, int y, int width, int height, String title) {
		glassPanel(g, x, y, width, height, A_GLASS_TEXT);
		if (title != null) {
			g.drawBoxAlpha(x + 1, y + 1, width - 2, TITLE_BAR_H - 1, VOID_HEADER, 190);
			g.drawLineHoriz(x + 2, y + TITLE_BAR_H - 1, width - 4, GOLD_LINE);
			g.drawColoredStringCentered(x + width / 2, title, GOLD_TITLE, 0, FONT_TITLE, y + 17);
		}
	}

	/** Header strip + gold seam + centered title, for panels that manage their own body. */
	public static void titleBar(GraphicsController g, int x, int y, int width, int height, String title) {
		g.drawBoxAlpha(x, y, width, height, VOID_HEADER, A_HEADER);
		g.drawLineHoriz(x + 2, y + height - 1, width - 4, GOLD_LINE);
		g.drawLineHoriz(x + 2, y + height, width - 4, SHADOW_B);
		if (title != null) {
			g.drawColoredStringCentered(x + width / 2, title, GOLD_TITLE, 0, FONT_TITLE, y + height - 7);
		}
	}

	/** Standard button (style guide §1.3). Draw-only; callers keep their own hit/click logic. */
	public static void button(GraphicsController g, int x, int y, int width, int height, String label,
							  boolean hover, boolean selected, boolean disabled, int font) {
		g.drawBoxAlpha(x, y, width, height, selected ? PURPLE_SELECT : VOID_BOX, A_BUTTON);
		int border = disabled ? VOID_LINE : (hover && !selected ? GOLD_HOT : GOLD_LINE);
		g.drawBorder(x, y, width, height, border);
		if (selected) {
			g.drawLineHoriz(x + 2, y + height - 2, width - 4, GOLD_TITLE);
		}
		if (label != null) {
			int textColor = disabled ? TEXT_DIM : (selected ? GOLD_TITLE : (hover ? GOLD_HOT : TEXT_BODY));
			g.drawColoredStringCentered(x + width / 2, label, textColor, 0, font, y + height / 2 + 4);
		}
	}

	/** 20x20 "X" close button (farm-sim recipe — the canonical close affordance). */
	public static void closeButton(GraphicsController g, int x, int y, int size, boolean hover) {
		g.drawBoxAlpha(x, y, size, size, hover ? DANGER_HOVER : CLOSE_FILL, A_CLOSE);
		g.drawBorder(x, y, size, size, hover ? DANGER_GLYPH : CLOSE_BORDER);
		g.drawColoredStringCentered(x + size / 2, "X", hover ? DANGER_GLYPH : TEXT_BODY, 0, FONT_BODY, y + size / 2 + 4);
	}

	/** Rect hit-test against unscaled game-frame mouse coordinates. */
	public static boolean hit(int x, int y, int width, int height, int mouseX, int mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Text field chrome (login-kit recipe). Caller draws its own text + caret. */
	public static void textField(GraphicsController g, int x, int y, int width, int height, boolean focused) {
		g.drawBoxAlpha(x, y, width, height, FIELD_BG, A_FIELD);
		g.drawBorder(x, y, width, height, focused ? PURPLE_FOCUS : FIELD_BORDER_IDLE);
		g.drawBorder(x + 1, y + 1, width - 2, height - 2, SHADOW_B);
	}

	/** List-row state fill: selected wins over hovered; idle rows draw nothing. */
	public static void listRowFill(GraphicsController g, int x, int y, int width, int height,
								   boolean hovered, boolean selected) {
		if (selected) {
			g.drawBoxAlpha(x, y, width, height, PURPLE_SELECT, A_BUTTON);
		} else if (hovered) {
			g.drawBoxAlpha(x, y, width, height, PURPLE_SELECT, A_HOVER_ROW);
		}
	}

	/** Tooltip backing — glass card, text-tier alpha. Caller draws its own content rows. */
	public static void tooltip(GraphicsController g, int x, int y, int width, int height) {
		g.drawBoxAlpha(x, y, width, height, GLASS_BODY, A_GLASS_TEXT);
		g.drawBorder(x, y, width, height, GLASS_RIM);
	}

	/** HUD plaque backing (scout-timer recipe): dark backing + left accent strip. */
	public static void plaque(GraphicsController g, int x, int y, int width, int height) {
		g.drawBoxAlpha(x, y, width, height, VOID_BODY, A_MODAL);
		g.drawBorder(x, y, width, height, VOID_LINE);
		g.drawLineVert(x, y, PURPLE_EDGE, height);
		g.drawLineVert(x + 1, y, PURPLE_EDGE, height);
	}

	// --- clip save/restore (single-level: GraphicsController has one global
	// clip rect and no stack; nesting pushClip without popClip is a bug) ------
	private static final int[] savedClip = new int[4];
	private static boolean clipSaved = false;

	public static void pushClip(GraphicsController g, int x, int y, int width, int height) {
		savedClip[0] = g.getClipLeft();
		savedClip[1] = g.getClipRight();
		savedClip[2] = g.getClipBottom();
		savedClip[3] = g.getClipTop();
		clipSaved = true;
		g.setClip(x, x + width, y + height, y);
	}

	public static void popClip(GraphicsController g) {
		if (clipSaved) {
			g.setClip(savedClip[0], savedClip[1], savedClip[2], savedClip[3]);
			clipSaved = false;
		} else {
			g.clearClip();
		}
	}

	// --- debug-property parsing (mirrors mudclient.voidscapeDebugColorProperty) --
	private static int colorProperty(String propertyName, int defaultValue) {
		String raw = System.getProperty(propertyName);
		if (raw == null || raw.trim().isEmpty()) {
			return defaultValue;
		}
		String normalized = raw.trim();
		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		} else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
			normalized = normalized.substring(2);
		}
		try {
			return (int) (Long.parseLong(normalized, 16) & 0xFFFFFFL);
		} catch (NumberFormatException ignored) {
			return defaultValue;
		}
	}

	private static int clampAlpha(int alpha) {
		return Math.max(0, Math.min(256, alpha));
	}
}
