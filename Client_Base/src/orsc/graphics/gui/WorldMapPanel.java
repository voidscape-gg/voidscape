package orsc.graphics.gui;

import com.openrsc.client.model.Sprite;
import orsc.graphics.two.GraphicsController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Voidscape — windowed world-map dialog (slice 5 of the auto-walker).
 *
 * <p>Visual model mirrors RSCRevolution2's world map: a centered modal window
 * with a title bar (and "Close window" link), floor tabs, zoom +/−/reset
 * buttons on the right, and the map content scaled + clipped to the inner
 * rect. The 3D scene stays visible around the window.
 *
 * <p>Backed by 4 pre-rendered PNGs at {@code Client_Base/Cache/worldmap/floor{0..3}.png}
 * (slice 4 / rsc-mapgen). Zoomed in by default, centered on the player.
 *
 * <p><b>Pixel ↔ tile calibration:</b> the rsc-mapgen PNG is 2304×2736; the
 * world is 1008×944 tiles per floor. The renderer crops empty water regions,
 * so the affine mapping is approximate — see {@link #worldToPngX/Y} —
 * calibrate after the panel is in front of a real landmark.
 */
public final class WorldMapPanel {

	public enum ClickResult { OUTSIDE, FLOOR_SWITCH, CLOSE, ZOOM, MAP_TILE, UI_BUTTON }

	private static final int FLOOR_COUNT = 4;
	private static final int MAP_PNG_W = 2304;
	private static final int MAP_PNG_H = 2736;

	// Layout constants. Keep sizes small enough that the dialog fits even
	// on the smaller-than-OSRS RSC client viewport.
	private static final int TITLE_H = 22;
	private static final int TAB_H = 18;
	private static final int TAB_W = 64;
	private static final int TAB_PAD = 4;
	private static final int BTN_W = 64;
	private static final int BTN_H = 22;
	private static final int RIGHT_COL_W = BTN_W + 8;
	private static final int CLOSE_LINK_W = 80;

	private static final int COLOR_WIN_BG = 0x141420;
	private static final int COLOR_WIN_BORDER = 0xC0C0C0;
	private static final int COLOR_TITLE_BG = 0x2828A0;
	private static final int COLOR_TAB_BG = 0x404040;
	private static final int COLOR_TAB_BG_ACTIVE = 0x808040;
	private static final int COLOR_BTN_BG = 0x404060;
	private static final int COLOR_BTN_BG_HOVER = 0x606080;
	private static final int COLOR_PLAYER_MARKER = 0xFFFF40;
	private static final int COLOR_ROUTE_DOT = 0x40FF40;
	private static final int COLOR_HELP_TEXT = 0xC0C0C0;

	private final Sprite[] floorSprites = new Sprite[FLOOR_COUNT];
	private final boolean[] floorLoadAttempted = new boolean[FLOOR_COUNT];

	private boolean visible;
	private int currentFloor;
	private float zoom = 10.0f;
	private static final float MIN_ZOOM = 1.0f;
	private static final float MAX_ZOOM = 24.0f;
	private static final float DEFAULT_ZOOM = 10.0f;

	/** Hand-baked F2P landmarks. Drawn on top of the map sprite at their world
	 *  positions (using the same placeholder pixel↔tile transform as the
	 *  click handler — labels move with calibration when we tune that). */
	private static final Object[][] LANDMARKS = {
		{"Lumbridge",   120, 648,  0},
		{"Varrock",     122, 509,  0},
		{"Falador",     304, 542,  0},
		{"Edgeville",   217, 449,  0},
		{"Draynor",     214, 632,  0},
		{"Karamja",     370, 685,  0},
		{"Al Kharid",    89, 693,  0},
		{"Port Sarim",  269, 643,  0},
		{"Barbarian",   233, 513,  0},
		{"Wilderness",  280, 200,  0},
		{"Mining Camp", 270, 352,  0},
		{"Champions",   151, 556,  0},
		{"Lumb. Castle", 217, 1647, 1},
	};

	// Computed each render() — used by handleClick().
	private int winX, winY, winW, winH;
	private int contentX, contentY, contentW, contentH;
	private int closeLinkX, closeLinkY;
	private int btnZoomInY, btnZoomOutY, btnResetY, btnX;
	private int tabsY;
	private float spriteRenderX, spriteRenderY, spriteRenderScale;

	public boolean isVisible() { return visible; }
	public void setVisible(boolean v) { this.visible = v; }
	public void toggleVisible() { setVisible(!this.visible); }
	public int getCurrentFloor() { return currentFloor; }

	public void adjustZoom(int wheelDelta) {
		float step = wheelDelta > 0 ? 1.2f : (1f / 1.2f);
		zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * step));
	}

	public void zoomIn() { zoom = Math.min(MAX_ZOOM, zoom * 1.4f); }
	public void zoomOut() { zoom = Math.max(MIN_ZOOM, zoom / 1.4f); }
	public void zoomReset() { zoom = DEFAULT_ZOOM; }

	public void render(GraphicsController surface, int gameWidth, int gameHeight,
	                   int playerWorldX, int playerWorldY,
	                   int[] routeXs, int[] routeYs) {

		// Window sizing: leave space for side panels (right) and chat bar (bottom).
		winW = Math.min(720, gameWidth - 220);
		winH = Math.min(520, gameHeight - 100);
		winX = (gameWidth - winW) / 2;
		winY = Math.max(8, (gameHeight - winH) / 2 - 30);

		// Window backdrop + border.
		surface.drawBoxAlpha(winX, winY, winW, winH, COLOR_WIN_BG, 220);
		surface.drawBoxBorder(winX, winW, winY, winH, COLOR_WIN_BORDER);

		// Title bar.
		surface.drawBox(winX, winY, winW, TITLE_H, COLOR_TITLE_BG);
		surface.drawString("Voidscape World Map", winX + 8, winY + TITLE_H - 7, 0xFFFFFF, 1);
		closeLinkX = winX + winW - CLOSE_LINK_W - 4;
		closeLinkY = winY + 2;
		surface.drawColoredStringCentered(closeLinkX + CLOSE_LINK_W / 2,
			"Close window", 0xFFFFFF, 0, 1, winY + TITLE_H - 7);

		// Floor tabs row.
		tabsY = winY + TITLE_H + 4;
		for (int f = 0; f < FLOOR_COUNT; f++) {
			int tx = winX + 8 + f * (TAB_W + TAB_PAD);
			surface.drawBox(tx, tabsY, TAB_W, TAB_H, f == currentFloor ? COLOR_TAB_BG_ACTIVE : COLOR_TAB_BG);
			surface.drawBoxBorder(tx, TAB_W, tabsY, TAB_H, COLOR_WIN_BORDER);
			surface.drawColoredStringCentered(tx + TAB_W / 2,
				floorLabel(f), 0xFFFFFF, 0, 1, tabsY + TAB_H - 4);
		}

		// Zoom + Reset buttons (right column).
		btnX = winX + winW - RIGHT_COL_W;
		int btnTop = tabsY + TAB_H + 12;
		btnZoomInY = btnTop;
		btnZoomOutY = btnTop + BTN_H + 6;
		btnResetY = btnTop + 2 * (BTN_H + 6);
		drawButton(surface, btnX, btnZoomInY, BTN_W, BTN_H, "Zoom +");
		drawButton(surface, btnX, btnZoomOutY, BTN_W, BTN_H, "Zoom −");
		drawButton(surface, btnX, btnResetY, BTN_W, BTN_H, "Reset");

		// Map content rect (everything left of the right column, below the tabs).
		contentX = winX + 6;
		contentY = tabsY + TAB_H + 4;
		contentW = winW - RIGHT_COL_W - 12;
		contentH = winH - (contentY - winY) - 6;

		// Compute view centre. Always centre on the player (when they're on
		// the displayed floor) — RSCR2-style fixed view, no panning yet.
		float centerPngX = MAP_PNG_W / 2f;
		float centerPngY = MAP_PNG_H / 2f;
		if (playerWorldY / 944 == currentFloor) {
			centerPngX = worldToPngX(playerWorldX);
			centerPngY = worldToPngY(playerWorldY);
		}

		// Scale: zoom=1 fits the entire map within the content rect.
		float baseScale = Math.min((float) contentW / MAP_PNG_W, (float) contentH / MAP_PNG_H);
		spriteRenderScale = baseScale * zoom;
		int contentCx = contentX + contentW / 2;
		int contentCy = contentY + contentH / 2;
		spriteRenderX = contentCx - centerPngX * spriteRenderScale;
		spriteRenderY = contentCy - centerPngY * spriteRenderScale;

		// Clamp so we don't drift the map off the content rect when zoomed out.
		float spriteW = MAP_PNG_W * spriteRenderScale;
		float spriteH = MAP_PNG_H * spriteRenderScale;
		if (spriteW > contentW) {
			spriteRenderX = Math.min(spriteRenderX, contentX);
			spriteRenderX = Math.max(spriteRenderX, contentX + contentW - spriteW);
		} else {
			spriteRenderX = contentX + (contentW - spriteW) / 2f;
		}
		if (spriteH > contentH) {
			spriteRenderY = Math.min(spriteRenderY, contentY);
			spriteRenderY = Math.max(spriteRenderY, contentY + contentH - spriteH);
		} else {
			spriteRenderY = contentY + (contentH - spriteH) / 2f;
		}

		// Map content (clipped).
		Sprite map = getOrLoadFloor(currentFloor);
		surface.setClip(contentX, contentX + contentW, contentY + contentH, contentY);
		surface.drawBox(contentX, contentY, contentW, contentH, 0x000000);
		if (map != null) {
			surface.drawSprite(map,
				Math.round(spriteRenderX), Math.round(spriteRenderY),
				Math.max(1, Math.round(spriteW)), Math.max(1, Math.round(spriteH)),
				5924);
		} else {
			surface.drawColoredStringCentered(contentX + contentW / 2,
				"Floor " + currentFloor + " image missing — run scripts/render-worldmap.sh",
				COLOR_HELP_TEXT, 0, 1, contentY + contentH / 2);
		}

		// Route polyline.
		if (routeXs != null && routeYs != null && routeXs.length > 0) {
			for (int i = 0; i < routeXs.length; i++) {
				int wy = routeYs[i];
				if (wy / 944 != currentFloor) continue;
				int px = worldToScreenX(routeXs[i]);
				int py = worldToScreenY(wy);
				surface.drawBox(px - 1, py - 1, 3, 3, COLOR_ROUTE_DOT);
			}
		}

		// Hand-baked landmark labels for the active floor.
		for (Object[] lm : LANDMARKS) {
			int lmFloor = (int) lm[3];
			if (lmFloor != currentFloor) continue;
			int lmX = (int) lm[1];
			int lmY = (int) lm[2] + lmFloor * 944;
			int sx = worldToScreenX(lmX);
			int sy = worldToScreenY(lmY);
			if (sx < contentX - 40 || sx > contentX + contentW + 40
				|| sy < contentY - 8 || sy > contentY + contentH + 8) continue;
			surface.drawColoredStringCentered(sx, (String) lm[0], 0xFFFF80, 0, 1, sy);
		}

		// Player marker + "You are here".
		if (playerWorldY / 944 == currentFloor) {
			int px = worldToScreenX(playerWorldX);
			int py = worldToScreenY(playerWorldY);
			// Filled square with black border, OSRS-ish.
			surface.drawBox(px - 4, py - 4, 9, 9, COLOR_PLAYER_MARKER);
			surface.drawBoxBorder(px - 4, 9, py - 4, 9, 0x000000);
			surface.drawColoredStringCentered(px, "You are here", 0xFFFFFF, 0, 1, py - 8);
		}

		// Restore default clip for following UI passes (chat etc.).
		surface.setClip(0, gameWidth, gameHeight + 12, 0);
	}

	public ClickResult handleClick(int mx, int my, int gameWidth, int[] outWorld) {
		// Outside window: don't intercept.
		if (mx < winX || mx >= winX + winW || my < winY || my >= winY + winH) {
			return ClickResult.OUTSIDE;
		}
		// Close link in title bar.
		if (mx >= closeLinkX && mx < closeLinkX + CLOSE_LINK_W
			&& my >= closeLinkY && my < closeLinkY + TITLE_H - 4) {
			visible = false;
			return ClickResult.CLOSE;
		}
		// Floor tabs.
		for (int f = 0; f < FLOOR_COUNT; f++) {
			int tx = winX + 8 + f * (TAB_W + TAB_PAD);
			if (mx >= tx && mx < tx + TAB_W && my >= tabsY && my < tabsY + TAB_H) {
				currentFloor = f;
				return ClickResult.FLOOR_SWITCH;
			}
		}
		// Zoom buttons.
		if (mx >= btnX && mx < btnX + BTN_W) {
			if (my >= btnZoomInY && my < btnZoomInY + BTN_H) { zoomIn();  return ClickResult.ZOOM; }
			if (my >= btnZoomOutY && my < btnZoomOutY + BTN_H) { zoomOut(); return ClickResult.ZOOM; }
			if (my >= btnResetY && my < btnResetY + BTN_H) { zoomReset(); return ClickResult.ZOOM; }
		}
		// Map content.
		if (mx >= contentX && mx < contentX + contentW
			&& my >= contentY && my < contentY + contentH) {
			float pngX = (mx - spriteRenderX) / spriteRenderScale;
			float pngY = (my - spriteRenderY) / spriteRenderScale;
			outWorld[0] = pixelToWorldX(pngX);
			outWorld[1] = pixelToWorldY(pngY) + currentFloor * 944;
			return ClickResult.MAP_TILE;
		}
		// Click landed on the window frame but not on an active widget.
		return ClickResult.UI_BUTTON;
	}

	// ─── Render helpers ─────────────────────────────────────────────────────

	private static void drawButton(GraphicsController surface, int x, int y, int w, int h, String label) {
		surface.drawBox(x, y, w, h, COLOR_BTN_BG);
		surface.drawBoxBorder(x, w, y, h, COLOR_WIN_BORDER);
		surface.drawColoredStringCentered(x + w / 2, label, 0xFFFFFF, 0, 1, y + h - 6);
	}

	private static String floorLabel(int floor) {
		switch (floor) {
			case 0: return "Ground";
			case 1: return "Up 1";
			case 2: return "Up 2";
			case 3: return "Dungeon";
			default: return "F" + floor;
		}
	}

	// ─── Pixel ↔ tile transform ─────────────────────────────────────────────
	//
	// rsc-mapgen renders 16 sectors X (48..63) × 19 sectors Y (37..55), each
	// 48 tiles at 3 px/tile, then flips the whole image on the X axis. So:
	//   unflipped PNG x = worldX * 3       (covers worldX in [0, 768])
	//   PNG x = 2303 - worldX * 3          (after the X-axis flip)
	//   PNG y = (worldY % 944) * 3         (covers worldY % 944 in [0, 912])
	// World X 768..1008 and world Y 912..944 are NOT rendered — those tiles
	// fall outside the PNG. F2P content all lives within the rendered range.

	private static final int RSCMAPGEN_TILE_PX = 3;

	private static float worldToPngX(int worldX) {
		return (MAP_PNG_W - 1) - worldX * RSCMAPGEN_TILE_PX;
	}

	private static float worldToPngY(int worldY) {
		return (worldY % 944) * RSCMAPGEN_TILE_PX;
	}

	private static int pixelToWorldX(float pngX) {
		return Math.round(((MAP_PNG_W - 1) - pngX) / (float) RSCMAPGEN_TILE_PX);
	}

	private static int pixelToWorldY(float pngY) {
		return Math.round(pngY / (float) RSCMAPGEN_TILE_PX);
	}

	private int worldToScreenX(int worldX) {
		return Math.round(spriteRenderX + worldToPngX(worldX) * spriteRenderScale);
	}

	private int worldToScreenY(int worldY) {
		return Math.round(spriteRenderY + worldToPngY(worldY) * spriteRenderScale);
	}

	// ─── Sprite loader ──────────────────────────────────────────────────────

	private Sprite getOrLoadFloor(int floor) {
		if (floor < 0 || floor >= FLOOR_COUNT) return null;
		if (floorSprites[floor] != null) return floorSprites[floor];
		if (floorLoadAttempted[floor]) return null;
		floorLoadAttempted[floor] = true;

		File f = new File("Cache/worldmap/floor" + floor + ".png");
		if (!f.exists()) return null;
		try {
			BufferedImage img = ImageIO.read(f);
			if (img == null) return null;
			int w = img.getWidth();
			int h = img.getHeight();
			int[] pixels = new int[w * h];
			img.getRGB(0, 0, w, h, pixels, 0, w);
			floorSprites[floor] = new Sprite(pixels, w, h);
			return floorSprites[floor];
		} catch (IOException ex) {
			return null;
		}
	}
}
