package orsc.graphics.gui;

import com.openrsc.client.model.Sprite;
import orsc.Config;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;
import orsc.util.PngSpriteLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Voidscape — windowed world-map dialog (slice 5 of the auto-walker).
 *
 * <p>Java port of {@code 2003scape/rsc-world-map@70f488a} (AGPLv3). Each plane
 * PNG is 2448×2736 in PNG-pixel space; surface labels, POIs and icon sprites
 * ship in the same coord system as upstream. Only the player marker uses a
 * world-tile → PNG-pixel transform.
 *
 * <p>Surface and underground planes share one coordinate transform. Runtime
 * labels and POIs remain surface-only; dungeon stage names and landmarks are
 * baked directly into {@code plane-1.png} through {@code plane-3.png}.
 *
 * <p>The surface keeps upstream's four zoom levels (0.5×, 1×, 2×, 4×).
 * Underground floors use a tighter 0.5×, 1×, 1.5×, 2× sequence so the
 * compact dungeon footprint remains useful at every step. Mouse-drag-to-pan
 * uses bounds clamp, and wheel zooms about the cursor. On show and on Reset,
 * the surface centres on the player while a dungeon floor frames its full map.
 *
 * <p>Click-to-walk fires on mouse-up only when the click was NOT a drag (under
 * 3 px movement) — so dragging the map doesn't accidentally walk to where you
 * grabbed it. {@link #pollMouse} is called every frame from {@code mudclient}
 * and handles drag transitions internally; it returns {@link ClickResult#MAP_TILE}
 * with the world coords on a non-drag click.
 *
 * <p><b>World-tile → PNG-pixel</b> (lifted from upstream's entity-canvas.js):
 * {@code pngX = 2446 - 3*worldX, pngY = 3*(worldY%944) - 1}.
 */
public final class WorldMapPanel {

	public enum ClickResult { OUTSIDE, CLOSE, ZOOM, MAP_TILE, WAYPOINT_WALK, WAYPOINT_SET, WAYPOINT_ARMED, UI_BUTTON }

	private static final int FLOOR_COUNT = 4;
	private static final int FLOOR_HEIGHT = 944;
	private static final int MAP_PNG_W = 2448;
	private static final int MAP_PNG_H = 2736;
	private static final String[] FLOOR_LABELS = { "Surface", "F1", "F2", "F3" };
	private static final String[] FLOOR_NAMES = {
		"Surface", "Null Sanctum", "Broken Menagerie", "Riftworks"
	};
	public static final int WAYPOINT_COUNT = 5;

	// Discrete zoom levels (matching upstream rsc-world-map ZOOM_SCALES).
	private static final float[] ZOOM_SCALES = { 0.5f, 1f, 2f, 4f };  // index = zoomLevel + 1
	private static final float[] DUNGEON_ZOOM_SCALES = { 0.5f, 1f, 1.5f, 2f };
	private static final int ZOOM_MIN_LEVEL = -1;
	private static final int ZOOM_MAX_LEVEL = 2;
	private static final int DEFAULT_ZOOM_LEVEL = 0;
	private static final int DUNGEON_DEFAULT_ZOOM_LEVEL = 1;
	private static final int DRAG_DEADZONE_PX = 3;
	private static final int VOID_DUNGEON_CENTER_X = 72;
	private static final int VOID_DUNGEON_CENTER_LOCAL_Y = 392;
	private static final int VOID_DUNGEON_MIN_X = 54;
	private static final int VOID_DUNGEON_MAX_X = 90;
	private static final int VOID_DUNGEON_MIN_LOCAL_Y = 361;
	private static final int VOID_DUNGEON_MAX_LOCAL_Y = 424;
	private static final int VOID_DUNGEON_SURFACE_X = 112;
	private static final int VOID_DUNGEON_SURFACE_Y = 296;

	// Layout constants.
	private static final int TITLE_H = 22;
	private static final int TAB_H = 18;
	private static final int TAB_W = 64;
	private static final int TAB_PAD = 4;
	private static final int BTN_W = 36;
	private static final int BTN_H = 28;
	private static final int RIGHT_COL_W = BTN_W + 16;
	private static final int WAYPOINT_SET_W = 32;
	private static final int WAYPOINT_BTN_MIN_W = 26;
	private static final int WAYPOINT_BTN_MAX_W = 62;
	private static final int WAYPOINT_BTN_H = 22;
	private static final int WAYPOINT_GAP = 4;
	private static final int CLOSE_LINK_W = 80;
	private static final int MOBILE_WEB_MIN_TOP_MARGIN = 34;
	private static final int MOBILE_WEB_PORTRAIT_MIN_TOP_MARGIN = 72;
	private static final int MOBILE_WEB_MIN_BOTTOM_MARGIN = 44;
	private static final int MOBILE_WEB_MIN_X_MARGIN = 8;

	private static final int COLOR_WIN_BG = UiSkin.VOID_BODY;
	private static final int COLOR_WIN_BORDER = 0xC0C0C0;
	private static final int COLOR_TITLE_BG = UiSkin.VOID_HEADER;
	private static final int COLOR_TAB_BG = 0x404040;
	private static final int COLOR_TAB_BG_ACTIVE = 0x808040;
	private static final int COLOR_BTN_OUTLINE = 0x000000;
	private static final int COLOR_BTN_BORDER = 0x373737;
	private static final int COLOR_PLAYER_MARKER = 0xFFFF40;
	private static final int COLOR_ROUTE_DOT = 0x40FF40;
	private static final int COLOR_HELP_TEXT = 0xC0C0C0;
	private static final int COLOR_LABEL_DEFAULT = 0xFFFFFF;

	private final Sprite[] floorSprites = new Sprite[FLOOR_COUNT];
	private final boolean[] floorLoadAttempted = new boolean[FLOOR_COUNT];

	private boolean visible;
	private int currentFloor;
	private boolean sceneRouteVisible = false;

	// Discrete zoom + pan state. panX,panY is the screen-space offset of the
	// plane PNG's (0,0) — i.e. where on screen the top-left of the unscaled
	// PNG would be drawn. Computed/clamped each render.
	private int zoomLevel = DEFAULT_ZOOM_LEVEL;
	private float panX, panY;
	private boolean panInitialized;

	// Drag state.
	private boolean dragActive;
	private boolean dragMoved;
	private int dragStartMouseX, dragStartMouseY;
	private float dragStartPanX, dragStartPanY;
	private boolean prevButtonDown;

	// Last cursor position from pollMouse — used by render() for hover state on
	// the zoom buttons (the surface API doesn't plumb mouse coords into render).
	private int lastMouseX, lastMouseY;

	// Search bar state. Searches `labels` (case-insensitive substring) then
	// `points` (POI type). On match, pans + recentres on the result.
	private boolean searchFocused;
	private String searchQuery = "";
	private int searchBoxX, searchBoxY, searchBoxW, searchBoxH;
	private int searchBlinkFrame;

	// Vendored data — loaded on first render.
	private static List<Label> labels = Collections.emptyList();
	private static List<Poi> points = Collections.emptyList();
	private static Map<String, Sprite> iconSprites = new HashMap<String, Sprite>();
	private static Sprite stoneBg;
	private static Sprite poiCircleSprite;  // synthesised; backdrop disc behind POI icons
	private static boolean assetsLoadAttempted;

	// Computed each render() — used by pollMouse().
	private int winX, winY, winW, winH;
	private int contentX, contentY, contentW, contentH;
	private int titleH = TITLE_H;
	private int closeLinkX, closeLinkY, closeLinkW = CLOSE_LINK_W, closeLinkH = TITLE_H - 4;
	private final int[] floorTabX = new int[FLOOR_COUNT];
	private final int[] floorTabW = new int[FLOOR_COUNT];
	private int floorTabY, floorTabH = TAB_H;
	private int btnZoomInX, btnZoomInY;
	private int btnZoomOutX, btnZoomOutY;
	private int btnResetX, btnResetY;
	private int btnTilesX, btnTilesY;
	private int zoomButtonW = BTN_W, zoomButtonH = BTN_H;
	private int waypointSetButtonX, waypointSetButtonY;
	private int waypointSetButtonW = WAYPOINT_SET_W;
	private int waypointButtonW = WAYPOINT_BTN_MIN_W;
	private int waypointButtonH = WAYPOINT_BTN_H;
	private final int[] waypointButtonX = new int[WAYPOINT_COUNT];
	private final int[] waypointButtonY = new int[WAYPOINT_COUNT];
	private final Waypoint[] waypoints = new Waypoint[WAYPOINT_COUNT];
	private boolean nativeAndroidLayout;
	private int nativeAndroidTouchTarget;
	private boolean waypointEditMode;
	private int waypointSetSlot = -1;
	private int lastWaypointSlot = -1;

	public boolean isVisible() { return visible; }
	public boolean isSceneRouteVisible() { return sceneRouteVisible; }
	public void setVisible(boolean v) {
		this.visible = v;
		if (v) panInitialized = false;  // re-center on next render
		if (!v) {
			searchFocused = false;
			searchQuery = "";
			waypointEditMode = false;
			waypointSetSlot = -1;
		}
	}
	public boolean isSearchFocused() { return visible && searchFocused; }
	public void toggleVisible() { setVisible(!this.visible); }
	public int getCurrentFloor() { return currentFloor; }
	public int getZoomLevel() { return zoomLevel; }
	public int getPanXRounded() { return Math.round(panX); }
	public int getPanYRounded() { return Math.round(panY); }
	public String getSearchQuery() { return searchQuery; }
	public int getWindowX() { return winX; }
	public int getWindowY() { return winY; }
	public int getWindowW() { return winW; }
	public int getWindowH() { return winH; }
	public int getContentCenterX() { return contentX + contentW / 2; }
	public int getContentCenterY() { return contentY + contentH / 2; }
	public int getContentW() { return contentW; }
	public int getContentH() { return contentH; }
	public int getZoomInCenterX() { return btnZoomInX + zoomButtonW / 2; }
	public int getZoomInCenterY() { return btnZoomInY + zoomButtonH / 2; }
	public int getZoomOutCenterX() { return btnZoomOutX + zoomButtonW / 2; }
	public int getZoomOutCenterY() { return btnZoomOutY + zoomButtonH / 2; }
	public int getResetCenterX() { return btnResetX + zoomButtonW / 2; }
	public int getResetCenterY() { return btnResetY + zoomButtonH / 2; }
	public int getTilesCenterX() { return btnTilesX + zoomButtonW / 2; }
	public int getTilesCenterY() { return btnTilesY + zoomButtonH / 2; }
	public int getCloseCenterX() { return closeLinkX + closeLinkW / 2; }
	public int getCloseCenterY() { return closeLinkY + closeLinkH / 2; }
	public int getSearchCenterX() { return searchBoxX + searchBoxW / 2; }
	public int getSearchCenterY() { return searchBoxY + searchBoxH / 2; }
	public int getFloorTabCenterX(final int floor) {
		return floor >= 0 && floor < FLOOR_COUNT
			? floorTabX[floor] + floorTabW[floor] / 2 : -1;
	}
	public int getFloorTabCenterY(final int floor) {
		return floor >= 0 && floor < FLOOR_COUNT ? floorTabY + floorTabH / 2 : -1;
	}
	public int getWaypointSetCenterX() { return waypointSetButtonX + waypointSetButtonW / 2; }
	public int getWaypointSetCenterY() { return waypointSetButtonY + waypointButtonH / 2; }
	public int getWaypointCenterX(final int slot) {
		return slot >= 0 && slot < WAYPOINT_COUNT
			? waypointButtonX[slot] + waypointButtonW / 2 : -1;
	}
	public int getWaypointCenterY(final int slot) {
		return slot >= 0 && slot < WAYPOINT_COUNT
			? waypointButtonY[slot] + waypointButtonH / 2 : -1;
	}
	public String getLayoutMode() {
		if (nativeAndroidLayout) return "native";
		return Config.isWeb() && Config.isAndroid() ? "mobileWeb" : "desktop";
	}
	public int getTouchTarget() { return nativeAndroidTouchTarget; }
	public int getMinimumInteractiveWidth() {
		return Math.min(Math.min(closeLinkW, searchBoxW),
			Math.min(zoomButtonW, Math.min(waypointSetButtonW,
				Math.min(waypointButtonW, minimumFloorTabWidth()))));
	}
	public int getMinimumInteractiveHeight() {
		return Math.min(Math.min(closeLinkH, searchBoxH),
			Math.min(zoomButtonH, Math.min(waypointButtonH, floorTabH)));
	}
	public boolean hasTouchSizedControls() {
		return !nativeAndroidLayout || nativeAndroidTouchTarget > 0
			&& getMinimumInteractiveWidth() >= nativeAndroidTouchTarget
			&& getMinimumInteractiveHeight() >= nativeAndroidTouchTarget;
	}
	public boolean areControlsInsideWindow() {
		int[][] rects = interactiveRects();
		for (int[] rect : rects) {
			if (rect[0] < winX || rect[1] < winY
				|| rect[0] + rect[2] > winX + winW
				|| rect[1] + rect[3] > winY + winH) return false;
		}
		return contentX >= winX && contentY >= winY
			&& contentX + contentW <= winX + winW
			&& contentY + contentH <= winY + winH;
	}
	public boolean doControlsOverlap() {
		int[][] rects = interactiveRects();
		for (int i = 0; i < rects.length; i++) {
			for (int j = i + 1; j < rects.length; j++) {
				if (rectsOverlap(rects[i], rects[j])) return true;
			}
		}
		return false;
	}
	public int getLastWaypointSlot() { return lastWaypointSlot; }
	public boolean isWaypointSetArmed() { return waypointSetSlot >= 0; }
	public boolean containsWindow(final int x, final int y) {
		return visible && x >= winX && x < winX + winW && y >= winY && y < winY + winH;
	}

	private int[][] interactiveRects() {
		int[][] rects = new int[WAYPOINT_COUNT + 7 + FLOOR_COUNT][];
		int index = 0;
		rects[index++] = new int[]{closeLinkX, closeLinkY, closeLinkW, closeLinkH};
		rects[index++] = new int[]{searchBoxX, searchBoxY, searchBoxW, searchBoxH};
		rects[index++] = new int[]{btnZoomInX, btnZoomInY, zoomButtonW, zoomButtonH};
		rects[index++] = new int[]{btnZoomOutX, btnZoomOutY, zoomButtonW, zoomButtonH};
		rects[index++] = new int[]{btnResetX, btnResetY, zoomButtonW, zoomButtonH};
		rects[index++] = new int[]{btnTilesX, btnTilesY, zoomButtonW, zoomButtonH};
		rects[index++] = new int[]{waypointSetButtonX, waypointSetButtonY,
			waypointSetButtonW, waypointButtonH};
		for (int slot = 0; slot < WAYPOINT_COUNT; slot++) {
			rects[index++] = new int[]{waypointButtonX[slot], waypointButtonY[slot],
				waypointButtonW, waypointButtonH};
		}
		for (int floor = 0; floor < FLOOR_COUNT; floor++) {
			rects[index++] = new int[]{floorTabX[floor], floorTabY,
				floorTabW[floor], floorTabH};
		}
		return rects;
	}

	private int minimumFloorTabWidth() {
		int minimum = Integer.MAX_VALUE;
		for (int width : floorTabW) minimum = Math.min(minimum, width);
		return minimum == Integer.MAX_VALUE ? 0 : minimum;
	}

	private static boolean rectsOverlap(final int[] first, final int[] second) {
		return first[0] < second[0] + second[2]
			&& first[0] + first[2] > second[0]
			&& first[1] < second[1] + second[3]
			&& first[1] + first[3] > second[1];
	}

	public void setWaypoints(final Waypoint[] saved) {
		for (int i = 0; i < WAYPOINT_COUNT; i++) {
			setWaypoint(i, saved != null && i < saved.length ? saved[i] : null);
		}
	}

	public void setWaypoint(final int slot, final Waypoint waypoint) {
		if (slot < 0 || slot >= WAYPOINT_COUNT) return;
		waypoints[slot] = waypoint == null || !waypoint.isSet()
			? null
			: new Waypoint(waypoint.worldX, waypoint.worldY, waypoint.name);
	}

	public void zoomIn()    { setZoomLevel(zoomLevel + 1, contentX + contentW / 2, contentY + contentH / 2); }
	public void zoomOut()   { setZoomLevel(zoomLevel - 1, contentX + contentW / 2, contentY + contentH / 2); }
	/** Reset jumps back to the default zoom AND re-centres on the player. */
	public void zoomReset() {
		zoomLevel = DEFAULT_ZOOM_LEVEL;
		panInitialized = false;
	}
	public void panBy(final int dx, final int dy) {
		panX += dx;
		panY += dy;
		panInitialized = true;
		clampPan();
	}
	public void focusSearch() {
		searchFocused = true;
		searchBlinkFrame = 0;
	}

	public void prepareLayout(final int gameWidth, final int gameHeight,
	                          final int playerWorldX, final int playerWorldY) {
		prepareLayout(gameWidth, gameHeight, playerWorldX, playerWorldY, false, 0);
	}

	public void prepareLayout(final int gameWidth, final int gameHeight,
	                          final int playerWorldX, final int playerWorldY,
	                          final boolean nativeAndroid, final int touchTarget) {
		resetClassicControlMetrics();
		nativeAndroidLayout = nativeAndroid;
		nativeAndroidTouchTarget = nativeAndroid ? Math.max(1, touchTarget) : 0;
		if (nativeAndroid) {
			prepareNativeAndroidLayout(gameWidth, gameHeight, nativeAndroidTouchTarget);
		} else {
			prepareClassicLayout(gameWidth, gameHeight);
		}

		// Opening and Reset follow the player back to their physical plane before
		// centering at a scale suited to that plane.
		if (!panInitialized) {
			currentFloor = floorForWorldTile(playerWorldX, playerWorldY);
			zoomLevel = defaultZoomForFloor(currentFloor);
			if (currentFloor == 0) {
				centerOnPlayer(playerWorldX, playerWorldY);
			} else {
				centerOnDungeonFloor(currentFloor);
			}
			panInitialized = true;
		}
		clampPan();
	}

	private void prepareClassicLayout(final int gameWidth, final int gameHeight) {
		// Desktop keeps the original centered 75% dialog. The iPhone TeaVM
		// profile uses the same shared map renderer/input, but gives the modal
		// most of the tall phone canvas so search, zoom, and walker taps are not
		// squeezed into a desktop-shaped window.
		if (Config.isWeb() && Config.isAndroid()) {
			int marginX = Math.max(MOBILE_WEB_MIN_X_MARGIN, gameWidth / 40);
			int topMargin = gameHeight >= 600
				? Math.max(MOBILE_WEB_PORTRAIT_MIN_TOP_MARGIN, gameHeight / 14)
				: Math.max(MOBILE_WEB_MIN_TOP_MARGIN, gameHeight / 16);
			int bottomMargin = Math.max(MOBILE_WEB_MIN_BOTTOM_MARGIN, gameHeight / 14);
			winX = marginX;
			winY = topMargin;
			winW = Math.max(260, gameWidth - marginX * 2);
			winH = Math.max(220, gameHeight - topMargin - bottomMargin);
		} else {
			// Windowed dialog at 75% of the game area, centered. Side-panel icons
			// and chat tabs remain visible around the edges; clicks outside the
			// panel pass through on desktop.
			winW = (gameWidth  * 3) / 4;
			winH = (gameHeight * 3) / 4;
			winX = (gameWidth  - winW) / 2;
			winY = (gameHeight - winH) / 2;
		}
		closeLinkX = winX + winW - closeLinkW - 4;
		closeLinkY = winY + 2;

		int floorTabsW = Math.max(FLOOR_COUNT,
			Math.min(winW - TAB_PAD * 2, TAB_W * FLOOR_COUNT));
		setFloorTabRects(winX + (winW - floorTabsW) / 2,
			winY + titleH + 2, floorTabsW, TAB_H);

		// Map content rect — fills the panel below the dedicated floor row.
		contentX = winX + 2;
		contentY = floorTabY + floorTabH + 3;
		contentW = winW - 4;
		contentH = winY + winH - contentY - 2;

		// Zoom buttons at top-right of content (overlay).
		btnZoomInX = contentX + contentW - zoomButtonW - 6;
		btnZoomOutX = btnZoomInX;
		btnResetX = btnZoomInX;
		btnTilesX = btnZoomInX;
		btnZoomInY = contentY + 4;
		btnZoomOutY = btnZoomInY + zoomButtonH + 4;
		btnResetY = btnZoomOutY + zoomButtonH + 4;
		btnTilesY = btnResetY + zoomButtonH + 4;

		// Five saved auto-walk locations. The compact row keeps the controls
		// usable on the mobile-web map without covering the search box.
		waypointSetButtonX = contentX + 6;
		waypointSetButtonY = contentY + 4;
		int slotAreaRight = btnZoomInX - WAYPOINT_GAP;
		int slotAreaLeft = waypointSetButtonX + waypointSetButtonW + WAYPOINT_GAP;
		int rawWaypointW = (slotAreaRight - slotAreaLeft - (WAYPOINT_COUNT - 1) * WAYPOINT_GAP) / WAYPOINT_COUNT;
		waypointButtonW = Math.max(WAYPOINT_BTN_MIN_W, Math.min(WAYPOINT_BTN_MAX_W, rawWaypointW));
		int waypointX = waypointSetButtonX + waypointSetButtonW + WAYPOINT_GAP;
		for (int i = 0; i < WAYPOINT_COUNT; i++) {
			waypointButtonX[i] = waypointX + i * (waypointButtonW + WAYPOINT_GAP);
			waypointButtonY[i] = waypointSetButtonY;
		}

		// Search box at bottom-left of content (overlay).
		searchBoxW = 200;
		searchBoxH = 22;
		searchBoxX = contentX + 6;
		searchBoxY = contentY + contentH - searchBoxH - 6;

	}

	private void resetClassicControlMetrics() {
		titleH = TITLE_H;
		closeLinkW = CLOSE_LINK_W;
		closeLinkH = TITLE_H - 4;
		zoomButtonW = BTN_W;
		zoomButtonH = BTN_H;
		waypointSetButtonW = WAYPOINT_SET_W;
		waypointButtonW = WAYPOINT_BTN_MIN_W;
		waypointButtonH = WAYPOINT_BTN_H;
		floorTabH = TAB_H;
	}

	private void prepareNativeAndroidLayout(final int gameWidth, final int gameHeight,
	                                        final int target) {
		final int gap = Math.max(2, target / 12);
		final int margin = Math.max(4, target / 6);
		final int availableW = Math.max(1, gameWidth - margin * 2);
		final int availableH = Math.max(1, gameHeight - margin * 2);
		final boolean portrait = gameHeight > gameWidth;

		titleH = target;
		closeLinkW = target;
		closeLinkH = target;
		zoomButtonH = target;
		waypointButtonH = target;

		if (portrait) {
			winW = availableW;
		} else {
			int minimumW = target * 6 + gap * 7;
			winW = Math.min(availableW, Math.max(minimumW, availableH * 2));
		}
		winX = (gameWidth - winW) / 2;

		final int innerW = Math.max(1, winW - gap * 2);
		final int waypointColumns = fittedColumns(innerW, target, gap, WAYPOINT_COUNT + 1);
		final int waypointRows = rowsForItems(WAYPOINT_COUNT + 1, waypointColumns);
		final boolean combinedSearchZoom = innerW >= target * 5 + gap * 4;
		final int zoomColumns = combinedSearchZoom ? 4 : fittedColumns(innerW, target, gap, 4);
		final int zoomRows = combinedSearchZoom ? 0 : rowsForItems(4, zoomColumns);
		final int controlRows = 1 + waypointRows + 1 + zoomRows;
		final int minimumMapH = target * 2;
		final int requiredH = titleH + gap * (controlRows + 2)
			+ controlRows * target + minimumMapH;
		if (portrait) {
			int comfortableH = Math.min(availableH, winW * 5 / 4);
			winH = Math.min(availableH, Math.max(requiredH, comfortableH));
		} else {
			winH = availableH;
		}
		winY = (gameHeight - winH) / 2;

		closeLinkX = winX + winW - closeLinkW;
		closeLinkY = winY;

		final int innerX = winX + gap;
		int rowY = winY + titleH + gap;
		setFloorTabRects(innerX, rowY, innerW, target);
		rowY += target + gap;

		final int waypointCellW = Math.max(target,
			(innerW - gap * (waypointColumns - 1)) / waypointColumns);
		waypointSetButtonW = waypointCellW;
		waypointButtonW = waypointCellW;
		for (int item = 0; item < WAYPOINT_COUNT + 1; item++) {
			int row = item / waypointColumns;
			int column = item % waypointColumns;
			int rowItems = Math.min(waypointColumns, WAYPOINT_COUNT + 1 - row * waypointColumns);
			int rowW = rowItems * waypointCellW + Math.max(0, rowItems - 1) * gap;
			int rowX = innerX + Math.max(0, (innerW - rowW) / 2);
			int cellX = rowX + column * (waypointCellW + gap);
			int cellY = rowY + row * (target + gap);
			if (item == 0) {
				waypointSetButtonX = cellX;
				waypointSetButtonY = cellY;
			} else {
				waypointButtonX[item - 1] = cellX;
				waypointButtonY[item - 1] = cellY;
			}
		}
		rowY += waypointRows * target + waypointRows * gap;

		if (combinedSearchZoom) {
			zoomButtonW = target;
			searchBoxX = innerX;
			searchBoxY = rowY;
			searchBoxW = innerW - target * 4 - gap * 4;
			searchBoxH = target;
			int buttonX = searchBoxX + searchBoxW + gap;
			setHorizontalZoomButtonRects(buttonX, rowY, target, target, gap);
			rowY += target + gap;
		} else {
			searchBoxX = innerX;
			searchBoxY = rowY;
			searchBoxW = innerW;
			searchBoxH = target;
			rowY += target + gap;

			zoomButtonW = Math.max(target,
				(innerW - gap * (zoomColumns - 1)) / zoomColumns);
			setZoomButtonGrid(innerX, rowY, innerW, zoomButtonW, target, gap, zoomColumns);
			rowY += zoomRows * target + zoomRows * gap;
		}

		contentX = innerX;
		contentY = rowY;
		contentW = innerW;
		contentH = Math.max(1, winY + winH - gap - contentY);
	}

	private void setFloorTabRects(final int x, final int y,
	                              final int width, final int height) {
		int cursor = x;
		int baseWidth = width / FLOOR_COUNT;
		int remainder = width % FLOOR_COUNT;
		floorTabY = y;
		floorTabH = height;
		for (int floor = 0; floor < FLOOR_COUNT; floor++) {
			int segmentWidth = baseWidth + (floor < remainder ? 1 : 0);
			floorTabX[floor] = cursor;
			floorTabW[floor] = segmentWidth;
			cursor += segmentWidth;
		}
	}

	private static int fittedColumns(final int width, final int target,
	                                 final int gap, final int itemCount) {
		return Math.max(1, Math.min(itemCount,
			Math.max(1, (width + gap) / Math.max(1, target + gap))));
	}

	private static int rowsForItems(final int itemCount, final int columns) {
		return (itemCount + columns - 1) / columns;
	}

	private void setHorizontalZoomButtonRects(final int x, final int y, final int width,
	                                          final int height, final int gap) {
		btnZoomInX = x;
		btnZoomInY = y;
		btnZoomOutX = x + width + gap;
		btnZoomOutY = y;
		btnResetX = x + (width + gap) * 2;
		btnResetY = y;
		btnTilesX = x + (width + gap) * 3;
		btnTilesY = y;
	}

	private void setZoomButtonGrid(final int x, final int y, final int totalWidth,
	                               final int width, final int height, final int gap,
	                               final int columns) {
		int[] xs = {0, 0, 0, 0};
		int[] ys = {0, 0, 0, 0};
		for (int item = 0; item < 4; item++) {
			int row = item / columns;
			int column = item % columns;
			int rowItems = Math.min(columns, 4 - row * columns);
			int rowW = rowItems * width + Math.max(0, rowItems - 1) * gap;
			int rowX = x + Math.max(0, (totalWidth - rowW) / 2);
			xs[item] = rowX + column * (width + gap);
			ys[item] = y + row * (height + gap);
		}
		btnZoomInX = xs[0]; btnZoomInY = ys[0];
		btnZoomOutX = xs[1]; btnZoomOutY = ys[1];
		btnResetX = xs[2]; btnResetY = ys[2];
		btnTilesX = xs[3]; btnTilesY = ys[3];
	}

	/** Mouse-wheel from mudclient. Positive = zoom in, negative = zoom out. */
	public void adjustZoom(int wheelDelta, int focusScreenX, int focusScreenY) {
		setZoomLevel(zoomLevel + (wheelDelta > 0 ? 1 : -1), focusScreenX, focusScreenY);
	}

	private void setZoomLevel(int newLevel, int focusScreenX, int focusScreenY) {
		newLevel = Math.max(ZOOM_MIN_LEVEL, Math.min(ZOOM_MAX_LEVEL, newLevel));
		if (newLevel == zoomLevel) return;
		float oldScale = scale(zoomLevel);
		float newScale = scale(newLevel);
		// Keep the PNG point under the focus position fixed across the zoom.
		float pngX = (focusScreenX - panX) / oldScale;
		float pngY = (focusScreenY - panY) / oldScale;
		zoomLevel = newLevel;
		panX = focusScreenX - pngX * newScale;
		panY = focusScreenY - pngY * newScale;
		// clamp deferred to render() so contentX/W are current
	}

	private float scale(int level) {
		return (currentFloor == 0 ? ZOOM_SCALES : DUNGEON_ZOOM_SCALES)[level + 1];
	}
	private float scale() { return scale(zoomLevel); }

	public void render(GraphicsController surface, int gameWidth, int gameHeight,
	                   int playerWorldX, int playerWorldY,
	                   int[] routeXs, int[] routeYs) {
		render(surface, gameWidth, gameHeight, playerWorldX, playerWorldY,
			routeXs, routeYs, false, 0);
	}

	public void render(GraphicsController surface, int gameWidth, int gameHeight,
	                   int playerWorldX, int playerWorldY,
	                   int[] routeXs, int[] routeYs,
	                   boolean nativeAndroid, int touchTarget) {

		ensureAssetsLoaded();
		prepareLayout(gameWidth, gameHeight, playerWorldX, playerWorldY,
			nativeAndroid, touchTarget);

		// Native Android uses the same translucent card and header treatment as
		// the surrounding mobile shell. Desktop/mobile-web retain their original
		// flat map window unchanged.
		if (nativeAndroidLayout) {
			UiSkin.glassPanel(surface, winX, winY, winW, winH, UiSkin.A_GLASS_TEXT);
			UiSkin.titleBar(surface, winX, winY, winW, titleH, null);
		} else {
			surface.drawBox(winX, winY, winW, winH, COLOR_WIN_BG);
			surface.drawBoxBorder(winX, winW, winY, winH, COLOR_WIN_BORDER);
			surface.drawBox(winX, winY, winW, titleH, COLOR_TITLE_BG);
		}

		// Title bar and an explicitly bounded native Close action.
		int titleBaseline = nativeAndroidLayout ? winY + titleH / 2 + 4 : winY + titleH - 7;
		String mapTitle = currentFloor == 0
			? (nativeAndroidLayout ? "World Map" : "Voidscape World Map")
			: (nativeAndroidLayout ? FLOOR_NAMES[currentFloor]
				: "World Map: " + FLOOR_NAMES[currentFloor]);
		surface.drawString(mapTitle,
			winX + 8, titleBaseline, nativeAndroidLayout ? UiSkin.GOLD_TITLE : COLOR_LABEL_DEFAULT, 1);
		if (nativeAndroidLayout) {
			boolean closeHover = contains(lastMouseX, lastMouseY,
				closeLinkX, closeLinkY, closeLinkW, closeLinkH);
			UiSkin.button(surface, closeLinkX, closeLinkY, closeLinkW, closeLinkH,
				"Close", closeHover, false, false, UiSkin.FONT_SMALL);
		} else {
			surface.drawColoredStringCentered(closeLinkX + closeLinkW / 2,
				"Close window", COLOR_LABEL_DEFAULT, 0, 1, titleBaseline);
		}

		// Map content (clipped).
		float scl = scale();
		Sprite map = getOrLoadFloor(currentFloor);
		surface.setClip(contentX, contentX + contentW, contentY + contentH, contentY);
		surface.drawBox(contentX, contentY, contentW, contentH, 0x000000);
		if (map != null) {
			// Software-clip via sub-rect extraction. The engine's scaled
			// drawSprite has a clipping bug when the source extends past the
			// clip on both the left/top AND right/bottom (the dest-coord reset
			// to 0 corrupts the right/bottom clip math), which floods the log
			// with "error in plot_scale" and tears the map. Pre-cropping the
			// PNG to just the visible portion keeps destX/destY ≥ clipLeft/Top
			// so only the bug-free right/bottom clip path runs.
			drawScaledClippedMap(surface, map, scl);
		} else {
			surface.drawColoredStringCentered(contentX + contentW / 2,
				"Plane " + currentFloor + " image missing - see worldmap/UPSTREAM.md",
				COLOR_HELP_TEXT, 0, 1, contentY + contentH / 2);
		}

		// Route polyline.
		if (routeXs != null && routeYs != null && routeXs.length > 0) {
			for (int i = 0; i < routeXs.length; i++) {
				int wy = routeYs[i];
				if (wy / FLOOR_HEIGHT != currentFloor) continue;
				int px = worldToScreenX(routeXs[i]);
				int py = worldToScreenY(wy);
				surface.drawBox(px - 1, py - 1, 3, 3, COLOR_ROUTE_DOT);
			}
		}

		// POI sprite icons. Labels/points are in plane-0 coords; only render on F0.
		if (currentFloor == 0 && zoomLevel >= 0) {
			for (Poi p : points) {
				int sx = pngToScreenX(p.x);
				int sy = pngToScreenY(p.y);
				if (sx < contentX - 12 || sx > contentX + contentW + 12
					|| sy < contentY - 12 || sy > contentY + contentH + 12) continue;
				Sprite ic = iconSprites.get(p.type);
				if (ic == null) continue;
				// 15×15 light-grey disc backdrop (matches upstream's CSS wrapper).
				if (poiCircleSprite != null) {
					surface.drawSprite(poiCircleSprite, sx - 7, sy - 7);
				}
				surface.drawSprite(ic, sx - ic.getWidth() / 2, sy - ic.getHeight() / 2);
			}
		}

		// Player screen pos so labels can dodge it. Compute dodge in PNG
		// space (zoom-independent) — at high zoom, screen-space distances
		// scale with zoom and the threshold breaks.
		int playerScreenX = -9999;
		int playerScreenY = -9999;
		float playerPngX = -9999f;
		float playerPngY = -9999f;
		if (playerWorldY / FLOOR_HEIGHT == currentFloor) {
			playerScreenX = worldToScreenX(playerWorldX);
			playerScreenY = worldToScreenY(playerWorldY);
			playerPngX = worldToPngX(playerWorldX);
			playerPngY = worldToPngY(playerWorldY);
		}

		// Place labels.
		if (currentFloor == 0) {
			for (Label lm : labels) {
				if (lm.size <= 9 && zoomLevel < 1) continue;
				if (lm.size <= 10 && zoomLevel < 0) continue;
				int sx = pngToScreenX(lm.x);
				int sy = pngToScreenY(lm.y);
				if (sx < contentX - 60 || sx > contentX + contentW + 60
					|| sy < contentY - 12 || sy > contentY + contentH + 12) continue;
				// Dodge the player marker in PNG-px (~40 PNG = ~13 tiles wide).
				if (Math.abs(lm.x - playerPngX) < 40 && Math.abs(lm.y - playerPngY) < 14) continue;
				drawLabel(surface, lm, sx, sy);
			}
		}

		// Player marker + "You are here".
		if (playerScreenX != -9999) {
			surface.drawBox(playerScreenX - 4, playerScreenY - 4, 9, 9, COLOR_PLAYER_MARKER);
			surface.drawBoxBorder(playerScreenX - 4, 9, playerScreenY - 4, 9, 0x000000);
			surface.drawColoredStringCentered(playerScreenX, "You are here", 0xFFFFFF, 0, 1, playerScreenY - 8);
		}

		// Restore default clip BEFORE drawing UI chrome.
		surface.setClip(0, gameWidth, gameHeight + 12, 0);
		drawFloorTabs(surface);

		// Flat-styled zoom buttons matching the "World Map" button under the
		// minimap (dark grey fill, light grey border, olive on hover).
		drawFlatButton(surface, btnZoomInX, btnZoomInY, zoomButtonW, zoomButtonH,
			"+", zoomLevel < ZOOM_MAX_LEVEL);
		drawFlatButton(surface, btnZoomOutX, btnZoomOutY, zoomButtonW, zoomButtonH,
			"-", zoomLevel > ZOOM_MIN_LEVEL);
		drawFlatButton(surface, btnResetX, btnResetY, zoomButtonW, zoomButtonH, "Reset", true);
		drawFlatButton(surface, btnTilesX, btnTilesY, zoomButtonW, zoomButtonH,
			"Tiles", true, sceneRouteVisible);
		drawWaypointButtons(surface);

		// Search box overlay (bottom-left).
		drawSearchBox(surface);
		searchBlinkFrame++;
	}

	/**
	 * Per-frame mouse handler. Called from mudclient regardless of click
	 * state — manages drag transitions internally. Returns MAP_TILE only on
	 * a non-drag click-release inside the map content.
	 */
	public ClickResult pollMouse(int mx, int my, boolean buttonDown, int gameWidth, int[] outWorld) {
		boolean wasDown = prevButtonDown;
		prevButtonDown = buttonDown;
		lastMouseX = mx;
		lastMouseY = my;

		// Outside window: don't intercept; cancel any in-flight drag silently.
		if (mx < winX || mx >= winX + winW || my < winY || my >= winY + winH) {
			if (!buttonDown) dragActive = false;
			return ClickResult.OUTSIDE;
		}

		// PRESS — chrome handlers fire immediately; map content starts a drag.
		if (buttonDown && !wasDown) {
			boolean clickedSearch = mx >= searchBoxX && mx < searchBoxX + searchBoxW
				&& my >= searchBoxY && my < searchBoxY + searchBoxH;
			if (!clickedSearch) searchFocused = false;  // any click outside the box drops focus

			if (contains(mx, my, closeLinkX, closeLinkY, closeLinkW, closeLinkH)) {
				setVisible(false);
				return ClickResult.CLOSE;
			}
			for (int floor = 0; floor < FLOOR_COUNT; floor++) {
				if (contains(mx, my, floorTabX[floor], floorTabY,
					floorTabW[floor], floorTabH)) {
					if (floor != currentFloor) {
						currentFloor = floor;
						zoomLevel = defaultZoomForFloor(floor);
						if (floor == 0) {
							centerOnPlayer(VOID_DUNGEON_SURFACE_X, VOID_DUNGEON_SURFACE_Y);
						} else {
							centerOnDungeonFloor(floor);
						}
					}
					panInitialized = true;
					dragActive = false;
					return ClickResult.UI_BUTTON;
				}
			}
			if (contains(mx, my, btnZoomInX, btnZoomInY, zoomButtonW, zoomButtonH)) {
				setZoomLevel(zoomLevel + 1, mx, my);
				return ClickResult.ZOOM;
			}
			if (contains(mx, my, btnZoomOutX, btnZoomOutY, zoomButtonW, zoomButtonH)) {
				setZoomLevel(zoomLevel - 1, mx, my);
				return ClickResult.ZOOM;
			}
			if (contains(mx, my, btnResetX, btnResetY, zoomButtonW, zoomButtonH)) {
				zoomReset();
				return ClickResult.ZOOM;
			}
			if (contains(mx, my, btnTilesX, btnTilesY, zoomButtonW, zoomButtonH)) {
				sceneRouteVisible = !sceneRouteVisible;
				return ClickResult.UI_BUTTON;
			}
			if (contains(mx, my, waypointSetButtonX, waypointSetButtonY,
				waypointSetButtonW, waypointButtonH)) {
				waypointEditMode = !waypointEditMode;
				if (!waypointEditMode) waypointSetSlot = -1;
				lastWaypointSlot = -1;
				return ClickResult.UI_BUTTON;
			}
			for (int i = 0; i < WAYPOINT_COUNT; i++) {
				if (contains(mx, my, waypointButtonX[i], waypointButtonY[i],
					waypointButtonW, waypointButtonH)) {
					lastWaypointSlot = i;
					if (waypointEditMode || waypoints[i] == null || !waypoints[i].isSet()) {
						waypointEditMode = true;
						waypointSetSlot = i;
						return ClickResult.WAYPOINT_ARMED;
					}
					outWorld[0] = waypoints[i].worldX;
					outWorld[1] = waypoints[i].worldY;
					return ClickResult.WAYPOINT_WALK;
				}
			}
			if (clickedSearch) {
				searchFocused = true;
				searchBlinkFrame = 0;
				return ClickResult.UI_BUTTON;
			}
			if (mx >= contentX && mx < contentX + contentW
				&& my >= contentY && my < contentY + contentH) {
				dragActive = true;
				dragMoved = false;
				dragStartMouseX = mx; dragStartMouseY = my;
				dragStartPanX = panX; dragStartPanY = panY;
			}
			return ClickResult.UI_BUTTON;
		}

		// HOLD — update pan if we've crossed the dead zone.
		if (buttonDown && wasDown && dragActive) {
			int dx = mx - dragStartMouseX;
			int dy = my - dragStartMouseY;
			if (Math.abs(dx) > DRAG_DEADZONE_PX || Math.abs(dy) > DRAG_DEADZONE_PX) dragMoved = true;
			if (dragMoved) {
				panX = dragStartPanX + dx;
				panY = dragStartPanY + dy;
				clampPan();
			}
			return ClickResult.UI_BUTTON;
		}

		// RELEASE — non-drag click inside the map content = walk target.
		if (!buttonDown && wasDown && dragActive) {
			boolean wasDrag = dragMoved;
			dragActive = false;
			dragMoved = false;
			if (!wasDrag
				&& mx >= contentX && mx < contentX + contentW
				&& my >= contentY && my < contentY + contentH) {
				float pngX = (mx - panX) / scale();
				float pngY = (my - panY) / scale();
				outWorld[0] = pixelToWorldX(pngX);
				outWorld[1] = pixelToWorldY(pngY) + currentFloor * FLOOR_HEIGHT;
				if (waypointSetSlot >= 0) {
					lastWaypointSlot = waypointSetSlot;
					waypointSetSlot = -1;
					waypointEditMode = false;
					return ClickResult.WAYPOINT_SET;
				}
				return ClickResult.MAP_TILE;
			}
		}

		return ClickResult.UI_BUTTON;
	}

	private static boolean contains(final int x, final int y,
	                                final int rectX, final int rectY,
	                                final int rectW, final int rectH) {
		return x >= rectX && x < rectX + rectW && y >= rectY && y < rectY + rectH;
	}

	private void centerOnPlayer(int playerWorldX, int playerWorldY) {
		float scl = scale();
		float centerPngX = MAP_PNG_W / 2f;
		float centerPngY = MAP_PNG_H / 2f;
		if (playerWorldY / FLOOR_HEIGHT == currentFloor) {
			centerPngX = worldToPngX(playerWorldX);
			centerPngY = worldToPngY(playerWorldY);
		}
		panX = contentX + contentW / 2f - centerPngX * scl;
		panY = contentY + contentH / 2f - centerPngY * scl;
	}

	private void centerOnDungeonFloor(final int floor) {
		centerOnPlayer(VOID_DUNGEON_CENTER_X,
			floor * FLOOR_HEIGHT + VOID_DUNGEON_CENTER_LOCAL_Y);
	}

	private static int floorForWorldTile(final int worldX, final int worldY) {
		int floor = worldY / FLOOR_HEIGHT;
		int localY = worldY % FLOOR_HEIGHT;
		return floor >= 1 && floor < FLOOR_COUNT
			&& worldX >= VOID_DUNGEON_MIN_X && worldX <= VOID_DUNGEON_MAX_X
			&& localY >= VOID_DUNGEON_MIN_LOCAL_Y && localY <= VOID_DUNGEON_MAX_LOCAL_Y
			? floor : 0;
	}

	private static int defaultZoomForFloor(final int floor) {
		return floor == 0 ? DEFAULT_ZOOM_LEVEL : DUNGEON_DEFAULT_ZOOM_LEVEL;
	}

	private void clampPan() {
		float scl = scale();
		float spriteW = MAP_PNG_W * scl;
		float spriteH = MAP_PNG_H * scl;
		if (spriteW > contentW) {
			panX = Math.min(panX, contentX);
			panX = Math.max(panX, contentX + contentW - spriteW);
		} else {
			panX = contentX + (contentW - spriteW) / 2f;
		}
		if (spriteH > contentH) {
			panY = Math.min(panY, contentY);
			panY = Math.max(panY, contentY + contentH - spriteH);
		} else {
			panY = contentY + (contentH - spriteH) / 2f;
		}
	}

	// ─── Search ─────────────────────────────────────────────────────────────

	/**
	 * Called from mudclient/ORSCApplet when the panel is visible and the
	 * search box has focus. Consumes printable chars, Backspace, Enter (run
	 * search), and Escape (clear focus + query).
	 */
	public void handleSearchKey(char c, int keyCode) {
		if (keyCode == 27) {  // Escape
			searchFocused = false;
			searchQuery = "";
			return;
		}
		if (c == '\n' || c == '\r' || keyCode == 10 || keyCode == 13) {  // Enter
			if (!searchQuery.isEmpty()) searchAndPan(searchQuery);
			searchFocused = false;
			return;
		}
		if (c == '\b') {  // Backspace
			if (searchQuery.length() > 0) {
				searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
			}
			searchBlinkFrame = 0;
			return;
		}
		if (c >= 32 && c < 127 && searchQuery.length() < 30) {
			searchQuery = searchQuery + c;
			searchBlinkFrame = 0;
		}
	}

	private void searchAndPan(String query) {
		String needle = query.toLowerCase().trim();
		if (needle.isEmpty()) return;
		// 1) Match against label text (most user-friendly hits — city / region names).
		for (Label lm : labels) {
			String text = lm.text.replace("\\n", " ").toLowerCase();
			if (text.contains(needle)) {
				panTo(lm.x, lm.y);
				return;
			}
		}
		// 2) Fallback to POI types (bank, anvil, altar, …).
		for (Poi p : points) {
			if (p.type.toLowerCase().contains(needle)) {
				panTo(p.x, p.y);
				return;
			}
		}
	}

	private void panTo(int pngX, int pngY) {
		currentFloor = 0;  // labels + POIs are floor-0 only
		float scl = scale();
		panX = contentX + contentW / 2f - pngX * scl;
		panY = contentY + contentH / 2f - pngY * scl;
		panInitialized = true;  // suppress auto-recenter on next render
		clampPan();
	}

	public String describeWorldTile(final int worldX, final int worldY) {
		ensureAssetsLoaded();
		if (worldY / FLOOR_HEIGHT == 0 && !labels.isEmpty()) {
			float pngX = worldToPngX(worldX);
			float pngY = worldToPngY(worldY);
			Label best = null;
			float bestDist = 999999f;
			for (Label lm : labels) {
				float dx = lm.x - pngX;
				float dy = lm.y - pngY;
				float dist = dx * dx + dy * dy;
				if (dist < bestDist) {
					best = lm;
					bestDist = dist;
				}
			}
			if (best != null && bestDist <= 160f * 160f) {
				String text = best.text.replace("\\n", " ").trim();
				if (!text.isEmpty()) {
					return text.length() > 36 ? text.substring(0, 36) : text;
				}
			}
		}
		return worldX + "," + worldY;
	}

	private void drawFloorTabs(GraphicsController surface) {
		for (int floor = 0; floor < FLOOR_COUNT; floor++) {
			int x = floorTabX[floor];
			int width = floorTabW[floor];
			boolean active = floor == currentFloor;
			boolean hover = contains(lastMouseX, lastMouseY, x, floorTabY, width, floorTabH);
			if (nativeAndroidLayout) {
				UiSkin.button(surface, x, floorTabY, width, floorTabH,
					FLOOR_LABELS[floor], hover, active, false, UiSkin.FONT_SMALL);
			} else {
				int background = active ? COLOR_TAB_BG_ACTIVE : hover ? 0x585858 : COLOR_TAB_BG;
				surface.drawBox(x, floorTabY, width, floorTabH, background);
				surface.drawBoxBorder(x, width, floorTabY, floorTabH,
					active ? COLOR_PLAYER_MARKER : COLOR_WIN_BORDER);
				surface.drawColoredStringCentered(x + width / 2, FLOOR_LABELS[floor],
					COLOR_LABEL_DEFAULT, 0, 1, floorTabY + floorTabH / 2 + 4);
			}
		}
	}

	private void drawWaypointButtons(GraphicsController surface) {
		boolean editing = waypointEditMode || waypointSetSlot >= 0;
		drawFlatButton(surface, waypointSetButtonX, waypointSetButtonY,
			waypointSetButtonW, waypointButtonH, "Set", true, editing);
		for (int i = 0; i < WAYPOINT_COUNT; i++) {
			boolean set = waypoints[i] != null && waypoints[i].isSet();
			boolean armed = i == waypointSetSlot;
			drawFlatButton(surface, waypointButtonX[i], waypointButtonY[i],
				waypointButtonW, waypointButtonH, waypointButtonLabel(i), true, set || armed, 0);
			if (armed) {
				surface.drawBoxBorder(waypointButtonX[i], waypointButtonW,
					waypointButtonY[i], waypointButtonH,
					nativeAndroidLayout ? UiSkin.GOLD_TITLE : COLOR_PLAYER_MARKER);
			}
		}
	}

	private String waypointButtonLabel(final int slot) {
		Waypoint waypoint = waypoints[slot];
		if (waypoint == null || !waypoint.isSet()) {
			return Integer.toString(slot + 1);
		}
		String label = compactWaypointName(waypoint.name);
		return label.isEmpty() ? Integer.toString(slot + 1) : label;
	}

	private static String compactWaypointName(String name) {
		if (name == null) return "";
		String clean = name.replace("\\n", " ").trim();
		String lower = clean.toLowerCase();
		if ("void enclave".equals(lower)) return "Enclave";
		if ("east ardougne".equals(lower) || "west ardougne".equals(lower)) return "Ardougne";
		if ("barbarian village".equals(lower)) return "Barb.";
		if ("draynor village".equals(lower)) return "Draynor";
		if ("wizard's tower".equals(lower)) return "Wizard";
		if ("champions guild".equals(lower) || "champions' guild".equals(lower)) return "Champions";
		return clean;
	}

	private void drawSearchBox(GraphicsController surface) {
		if (nativeAndroidLayout) {
			UiSkin.textField(surface, searchBoxX, searchBoxY, searchBoxW, searchBoxH, searchFocused);
		} else {
			int border = searchFocused ? 0xFFFF40 : 0xC0C0C0;
			surface.drawBox(searchBoxX, searchBoxY, searchBoxW, searchBoxH, 0x202020);
			surface.drawBoxBorder(searchBoxX, searchBoxW, searchBoxY, searchBoxH, border);
		}
		String text = searchQuery;
		int color = nativeAndroidLayout ? UiSkin.TEXT_BODY : COLOR_LABEL_DEFAULT;
		if (text.isEmpty() && !searchFocused) {
			text = "Search…";
			color = nativeAndroidLayout ? UiSkin.TEXT_DIM : 0x808080;
		}
		int textY = nativeAndroidLayout
			? searchBoxY + searchBoxH / 2 + 4
			: searchBoxY + searchBoxH - 7;
		surface.drawString(text, searchBoxX + 6, textY, color, 1);
		if (searchFocused && (searchBlinkFrame / 25) % 2 == 0) {
			int textW = surface.stringWidth(1, searchQuery);
			int cursorX = searchBoxX + 6 + textW + 1;
			int cursorInset = nativeAndroidLayout ? Math.max(4, searchBoxH / 4) : 4;
			surface.drawBox(cursorX, searchBoxY + cursorInset, 1,
				Math.max(1, searchBoxH - cursorInset * 2),
				nativeAndroidLayout ? UiSkin.PURPLE_FOCUS : COLOR_LABEL_DEFAULT);
		}
	}

	// ─── Render helpers ─────────────────────────────────────────────────────

	private void drawFlatButton(GraphicsController surface, int x, int y, int w, int h, String label, boolean enabled) {
		drawFlatButton(surface, x, y, w, h, label, enabled, false);
	}

	private void drawFlatButton(GraphicsController surface, int x, int y, int w, int h, String label, boolean enabled, boolean active) {
		drawFlatButton(surface, x, y, w, h, label, enabled, active, 1);
	}

	private void drawFlatButton(GraphicsController surface, int x, int y, int w, int h, String label, boolean enabled, boolean active, int font) {
		boolean hover = enabled
			&& lastMouseX >= x && lastMouseX < x + w
			&& lastMouseY >= y && lastMouseY < y + h;
		if (nativeAndroidLayout) {
			UiSkin.button(surface, x, y, w, h,
				fitButtonLabel(surface, label, w, font), hover, active, !enabled, font);
			return;
		}
		int bg = active ? 0x2E7D32 : (hover ? 0x808040 : 0x404040);
		if (active && hover) bg = 0x3F9B43;
		surface.drawBox(x, y, w, h, bg);
		surface.drawBoxBorder(x, w, y, h, 0xC0C0C0);
		int textColor = enabled ? 0xFFFFFF : 0x808080;
		surface.drawColoredStringCentered(x + w / 2, fitButtonLabel(surface, label, w, font),
			textColor, 0, font, y + h / 2 + (font == 0 ? 3 : 4));
	}

	private static String fitButtonLabel(GraphicsController surface, String label, int width, int font) {
		String text = label == null ? "" : label.trim();
		int maxWidth = Math.max(1, width - 6);
		if (surface.stringWidth(font, text) <= maxWidth) return text;
		for (int len = text.length() - 1; len > 0; len--) {
			String candidate = text.substring(0, len) + ".";
			if (surface.stringWidth(font, candidate) <= maxWidth) return candidate;
		}
		return text.isEmpty() ? "" : text.substring(0, 1);
	}

	/**
	 * Draw the visible portion of {@code map} into the content rect at the
	 * current pan/zoom. Avoids the engine's broken scaled-clip path by
	 * pre-cropping the source PNG to only the pixels that land inside the
	 * clip — guarantees {@code destX ≥ clipLeft} and {@code destY ≥ clipTop},
	 * which sidesteps the buggy {@code x = 0 / y = 0} reset in plot_scale.
	 */
	private void drawScaledClippedMap(GraphicsController surface, Sprite map, float scl) {
		int srcW = map.getWidth();
		int srcH = map.getHeight();

		int srcLeft   = Math.max(0,    (int) Math.floor((contentX - panX) / scl));
		int srcTop    = Math.max(0,    (int) Math.floor((contentY - panY) / scl));
		int srcRight  = Math.min(srcW, (int) Math.ceil ((contentX + contentW - panX) / scl));
		int srcBottom = Math.min(srcH, (int) Math.ceil ((contentY + contentH - panY) / scl));

		if (srcLeft >= srcRight || srcTop >= srcBottom) return;

		int subW = srcRight - srcLeft;
		int subH = srcBottom - srcTop;
		int[] subPixels = new int[subW * subH];
		int[] mapPixels = map.getPixels();
		for (int yy = 0; yy < subH; yy++) {
			System.arraycopy(mapPixels, (srcTop + yy) * srcW + srcLeft,
				subPixels, yy * subW, subW);
		}
		Sprite sub = new Sprite(subPixels, subW, subH);

		int destX = Math.round(panX + srcLeft * scl);
		int destY = Math.round(panY + srcTop  * scl);
		int destW = Math.max(1, Math.round(subW * scl));
		int destH = Math.max(1, Math.round(subH * scl));

		surface.drawSprite(sub, destX, destY, destW, destH, 5924);
	}

	private static void drawLabel(GraphicsController surface, Label lm, int sx, int sy) {
		int font = pickFont(lm.size, lm.bold);
		int color = lm.color;
		String[] lines = splitLiteralNewlines(lm.text);
		int lineH = surface.fontHeight(font) + 1;
		for (int i = 0; i < lines.length; i++) {
			int ly = sy + i * lineH;
			if (lm.align == 'l') {
				surface.drawString(lines[i], sx, ly, color, font);
			} else {
				surface.drawColoredStringCentered(sx, lines[i], color, 0, font, ly);
			}
		}
	}

	private static int pickFont(int size, boolean bold) {
		if (size <= 8)  return bold ? 1 : 0;
		if (size <= 10) return bold ? 2 : 1;
		if (size <= 12) return bold ? 3 : 2;
		return 5;
	}

	private static String[] splitLiteralNewlines(String s) {
		if (s.indexOf('\\') < 0) return new String[] { s };
		List<String> out = new ArrayList<String>();
		int from = 0;
		for (int i = 0; i + 1 < s.length(); i++) {
			if (s.charAt(i) == '\\' && s.charAt(i + 1) == 'n') {
				out.add(s.substring(from, i));
				from = i + 2;
				i++;
			}
		}
		out.add(s.substring(from));
		return out.toArray(new String[0]);
	}

	// ─── Pixel ↔ tile transform ─────────────────────────────────────────────
	// Lifted from 2003scape/rsc-world-map src/entity-canvas.js:
	//   x = imageWidth - worldX*3 - 2;       imageWidth = 2448
	//   y = worldY*3 - 1 - plane * 2832
	// For floor-relative Y (worldY % FLOOR_HEIGHT), the plane term cancels so
	// pngY = 3*(worldY%FLOOR_HEIGHT) - 1 on every plane.

	private static final int TILE_PX = 3;
	private static final int X_ORIGIN = 2446;
	private static final int Y_OFFSET = -1;

	private static float worldToPngX(int worldX) { return X_ORIGIN - worldX * TILE_PX; }
	private static float worldToPngY(int worldY) { return (worldY % FLOOR_HEIGHT) * TILE_PX + Y_OFFSET; }
	private static int pixelToWorldX(float pngX)  { return Math.round((X_ORIGIN - pngX) / (float) TILE_PX); }
	private static int pixelToWorldY(float pngY)  { return Math.round((pngY - Y_OFFSET) / (float) TILE_PX); }

	private int worldToScreenX(int worldX) { return Math.round(panX + worldToPngX(worldX) * scale()); }
	private int worldToScreenY(int worldY) { return Math.round(panY + worldToPngY(worldY) * scale()); }
	private int pngToScreenX(int pngX)     { return Math.round(panX + pngX * scale()); }
	private int pngToScreenY(int pngY)     { return Math.round(panY + pngY * scale()); }

	// ─── Sprite + asset loaders ─────────────────────────────────────────────

	private Sprite getOrLoadFloor(int floor) {
		if (floor < 0 || floor >= FLOOR_COUNT) return null;
		if (floorSprites[floor] != null) return floorSprites[floor];
		if (floorLoadAttempted[floor]) return null;
		floorLoadAttempted[floor] = true;

		Sprite s = readPngAsSprite("plane-" + floor + ".png");
		floorSprites[floor] = s;
		return s;
	}

	/**
	 * Synthesise a 15×15 light-grey disc with a 1-px black border. Matches
	 * upstream's POI wrapper CSS ({@code borderRadius: 8px} on a 15×15 div
	 * with {@code background-color: #c0c0c0} and {@code border: 1px solid
	 * #000}). The renderer reads pixel value 0 as transparent, so cells
	 * outside the radius stay 0 and clip naturally.
	 */
	private static Sprite makePoiCircle() {
		final int size = 15;
		final int r = size / 2;
		final int outer2 = r * r;
		final int inner2 = (r - 1) * (r - 1);
		int[] pixels = new int[size * size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				int dx = x - r;
				int dy = y - r;
				int d2 = dx * dx + dy * dy;
				if (d2 > outer2)      pixels[y * size + x] = 0;            // transparent outside
				else if (d2 > inner2) pixels[y * size + x] = 0xFF000001;   // black border (non-zero so renderer doesn't drop it)
				else                  pixels[y * size + x] = 0xFFC0C0C0;   // light-grey fill
			}
		}
		return new Sprite(pixels, size, size);
	}

	private static Sprite readPngAsSprite(String relativePath) {
		File f = worldMapAsset(relativePath);
		Sprite sprite = PngSpriteLoader.read(f);
		if (sprite != null) return sprite;
		if (mudclient.clientPort == null) return null;
		try {
			return mudclient.clientPort.getSpriteFromByteArray(new ByteArrayInputStream(readResourceBytes(relativePath)));
		} catch (Throwable ex) {
			return null;
		}
	}

	private static byte[] readResourceBytes(String relativePath) throws IOException {
		try (InputStream in = openWorldMapResource(relativePath);
			 ByteArrayOutputStream out = new ByteArrayOutputStream(1024 * 1024)) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			return out.toByteArray();
		}
	}

	private static InputStream openWorldMapResource(String relativePath) throws IOException {
		if (mudclient.clientPort != null) {
			return mudclient.clientPort.openCacheResource("worldmap/" + relativePath);
		}
		return new FileInputStream(worldMapAsset(relativePath));
	}

	private static BufferedReader openWorldMapText(String relativePath) throws IOException {
		if (mudclient.clientPort != null) {
			return new BufferedReader(new InputStreamReader(openWorldMapResource(relativePath), "UTF-8"));
		}
		return new BufferedReader(new FileReader(worldMapAsset(relativePath)));
	}

	private static File worldMapAsset(String path) {
		return new File(new File(Config.F_CACHE_DIR, "worldmap"), path);
	}

	private static synchronized void ensureAssetsLoaded() {
		if (assetsLoadAttempted) return;
		assetsLoadAttempted = true;
		labels = loadLabels("labels.tsv");
		points = loadPoints("points.tsv");
		stoneBg = readPngAsSprite("stone-background.png");
		poiCircleSprite = makePoiCircle();
		for (Poi p : points) {
			if (iconSprites.containsKey(p.type)) continue;
			Sprite s = readPngAsSprite("icons/" + p.type + ".png");
			if (s != null) iconSprites.put(p.type, s);
		}
		File iconsDir = worldMapAsset("icons");
		if (iconsDir.isDirectory()) {
			File[] files = iconsDir.listFiles();
			if (files != null) {
				for (File f : files) {
					String n = f.getName();
					if (!n.endsWith(".png")) continue;
					if (iconSprites.containsKey(n.substring(0, n.length() - 4))) continue;
					Sprite s = readPngAsSprite("icons/" + n);
					if (s != null) iconSprites.put(n.substring(0, n.length() - 4), s);
				}
			}
		}
	}

	private static List<Label> loadLabels(String relativePath) {
		List<Label> out = new ArrayList<Label>();
		BufferedReader r = null;
		try {
			r = openWorldMapText(relativePath);
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				String[] parts = line.split("\t", -1);
				if (parts.length < 7) continue;
				String text = parts[0];
				int x = parseIntSafe(parts[1], 0);
				int y = parseIntSafe(parts[2], 0);
				int size = parseIntSafe(parts[3], 10);
				char align = parts[4].length() > 0 && parts[4].charAt(0) == 'l' ? 'l' : 'c';
				boolean bold = "1".equals(parts[5]);
				int color = parseColor(parts[6], COLOR_LABEL_DEFAULT);
				out.add(new Label(text, x, y, size, align, bold, color));
			}
		} catch (IOException ex) {
			// fall through
		} finally {
			closeQuiet(r);
		}
		return out;
	}

	private static List<Poi> loadPoints(String relativePath) {
		List<Poi> out = new ArrayList<Poi>();
		BufferedReader r = null;
		try {
			r = openWorldMapText(relativePath);
			String line;
			while ((line = r.readLine()) != null) {
				if (line.isEmpty() || line.charAt(0) == '#') continue;
				String[] parts = line.split("\t", -1);
				if (parts.length < 3) continue;
				out.add(new Poi(parts[0], parseIntSafe(parts[1], 0), parseIntSafe(parts[2], 0)));
			}
		} catch (IOException ex) {
			// fall through
		} finally {
			closeQuiet(r);
		}
		return out;
	}

	private static int parseIntSafe(String s, int dflt) {
		try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ex) { return dflt; }
	}

	private static int parseColor(String s, int dflt) {
		if (s == null || s.isEmpty()) return dflt;
		s = s.trim();
		if (s.startsWith("rgb(")) {
			int close = s.indexOf(')');
			if (close < 0) return dflt;
			String[] rgb = s.substring(4, close).split(",");
			if (rgb.length != 3) return dflt;
			int r = parseIntSafe(rgb[0], 255) & 0xFF;
			int g = parseIntSafe(rgb[1], 255) & 0xFF;
			int b = parseIntSafe(rgb[2], 255) & 0xFF;
			return (r << 16) | (g << 8) | b;
		}
		return dflt;
	}

	private static void closeQuiet(java.io.Closeable c) {
		if (c == null) return;
		try { c.close(); } catch (IOException ignore) { /* ignore */ }
	}

	private static final class Label {
		final String text;
		final int x, y, size, color;
		final char align;       // 'l' = left, 'c' = center
		final boolean bold;

		Label(String text, int x, int y, int size, char align, boolean bold, int color) {
			this.text = text; this.x = x; this.y = y; this.size = size;
			this.align = align; this.bold = bold; this.color = color;
		}
	}

	private static final class Poi {
		final String type;
		final int x, y;

		Poi(String type, int x, int y) { this.type = type; this.x = x; this.y = y; }
	}

	public static final class Waypoint {
		public final int worldX;
		public final int worldY;
		public final String name;

		public Waypoint(int worldX, int worldY, String name) {
			this.worldX = worldX;
			this.worldY = worldY;
			this.name = name == null ? "" : name;
		}

		public boolean isSet() {
			return worldX >= 0 && worldY >= 0;
		}
	}
}
