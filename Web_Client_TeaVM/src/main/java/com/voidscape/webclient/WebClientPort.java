package com.voidscape.webclient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.teavm.jso.JSBody;
import org.teavm.jso.crypto.Crypto;
import org.teavm.jso.typedarrays.Uint8Array;

import com.openrsc.client.model.Sprite;

import orsc.Config;
import orsc.ORSCharacter;
import orsc.PacketHandler;
import orsc.osConfig;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.GraphicsController;
import orsc.multiclient.ClientPort;
import orsc.mudclient;
import orsc.net.Network_Base;
import orsc.net.Network_WebSocket;

public class WebClientPort implements ClientPort {
	private static final int DESKTOP_GAME_WIDTH = 512;
	private static final int DESKTOP_FULL_HEIGHT = 346;
	private static final int TOP_MENU_TOUCH_PADDING = 10;
	private static final int WORLD_MAP_TOUCH_DEADZONE_PX = 3;
	private static final long WORLD_MAP_TOUCH_RELEASE_HOLD_MILLIS = 90L;

	private mudclient client;
	private boolean inputListenersInitialized;
	private int lastRequestedWidth;
	private int lastRequestedFullHeight;
	private boolean worldMapTouchActive;
	private boolean worldMapTouchMoved;
	private int worldMapTouchStartX;
	private int worldMapTouchStartY;
	private long worldMapTouchReleaseUntilMillis;
	private boolean pkCatchingPointerActive;

	public void setClient(mudclient client) {
		this.client = client;
	}

	@Override
	public boolean fillSecureRandom(byte[] destination) {
		if (destination == null || destination.length != 32) {
			wipe(destination);
			return false;
		}
		try {
			if (!Crypto.isSupported()) {
				wipe(destination);
				return false;
			}
			Uint8Array random = Uint8Array.create(destination.length);
			Crypto.current().getRandomValues(random);
			for (int i = 0; i < destination.length; i++) {
				destination[i] = (byte) random.get(i);
			}
			return true;
		} catch (RuntimeException ignored) {
			wipe(destination);
			return false;
		}
	}

	private static void wipe(byte[] destination) {
		if (destination == null) return;
		for (int i = 0; i < destination.length; i++) destination[i] = 0;
	}

	@Override
	public boolean drawLoading(int i) {
		drawLoadingScreen(i, "Loading");
		return true;
	}

	@Override
	public void showLoadingProgress(int percentage, String status) {
		drawLoadingScreen(percentage, status);
	}

	@Override
	public void initListeners() {
		if (inputListenersInitialized) {
			return;
		}
		installInputHandlers();
		applyViewportResize();
		inputListenersInitialized = true;
	}

	@Override
	public void pollInput() {
		applyViewportResize();
		syncMobileInputHints();
		publishClientState();
		publishLoginState();
		publishUiState();
		publishWorldMapState();
		drainWebSmokeLoginRequest();
		drainInputEvents();
		updateWorldMapTouchRelease();
	}

	@Override
	public void crashed() {
		setStatus("Client crashed.");
	}

	@Override
	public void drawLoadingError() {
		setStatus("Loading error.");
	}

	@Override
	public void drawOutOfMemoryError() {
		setStatus("Out of memory.");
	}

	@Override
	public boolean isDisplayable() {
		return true;
	}

	@Override
	public void drawTextBox(String line2, byte var2, String line1) {
		setStatus(line1 + " " + line2);
	}

	@Override
	public void initGraphics() {
	}

	@Override
	public void draw() {
		if (client == null || client.getSurface() == null) {
			return;
		}
		GraphicsController surface = client.getSurface();
		if (surface.pixelData == null || surface.width2 <= 0 || surface.height2 <= 0) {
			return;
		}
		if (!prepareFrameUpload(surface.width2, surface.height2)) {
			return;
		}
		drawPixels(surface.pixelData, surface.width2, surface.height2);
	}

	@Override
	public void close() {
	}

	@Override
	public String getCacheLocation() {
		return "Cache/";
	}

	@Override
	public InputStream openCacheResource(String relativePath) throws IOException {
		try {
			return new ByteArrayInputStream(fetchResourceBytes(getCacheLocation() + relativePath));
		} catch (RuntimeException e) {
			throw new IOException("Unable to fetch web cache resource: " + relativePath, e);
		}
	}

	@Override
	public Sprite getBattery(int level) {
		return null;
	}

	@Override
	public int getBatteryPercent() {
		return 100;
	}

	@Override
	public boolean getBatteryCharging() {
		return true;
	}

	@Override
	public Sprite getConnectivity(int level) {
		return null;
	}

	@Override
	public String getConnectivityText() {
		return "Web";
	}

	@Override
	public void resized() {
		lastRequestedWidth = 0;
		lastRequestedFullHeight = 0;
	}

	@Override
	public Sprite getSpriteFromByteArray(ByteArrayInputStream byteArrayInputStream) {
		try {
			return PngSpriteDecoder.decode(byteArrayInputStream);
		} catch (IOException e) {
			return null;
		}
	}

	@Override
	public void playSound(byte[] soundData, int offset, int dataLength) {
	}

	@Override
	public void stopSoundPlayer() {
	}

	@Override
	public void drawKeyboard() {
		osConfig.F_SHOWING_KEYBOARD = true;
		requestKeyboardFocus();
	}

	@Override
	public void closeKeyboard() {
		osConfig.F_SHOWING_KEYBOARD = false;
		hideKeyboardFocus();
	}

	@Override
	public boolean openUrl(String url) {
		return openWindow(url);
	}

	@Override
	public void setTitle(String title) {
		setDocumentTitle(title);
	}

	@Override
	public void setIconImage(String serverName) {
	}

	@Override
	public Network_Base openNetworkConnection(PacketHandler packetHandler, String host, int port) throws IOException {
		return new Network_WebSocket(host, port);
	}

	private void drainInputEvents() {
		if (client == null) {
			return;
		}
		String event;
		while ((event = pollInputEvent()) != null) {
			handleInputEvent(event);
		}
	}

	private void drainWebSmokeLoginRequest() {
		if (client == null || !hasWebSmokeLoginRequest()) {
			return;
		}
		String user = getWebSmokeLoginUser();
		String pass = getWebSmokeLoginPass();
		clearWebSmokeLoginRequest();
		client.webSmokeLogin(user, pass);
	}

	private void applyViewportResize() {
		if (client == null) {
			return;
		}
		if (isKeyboardResizeFrozen() && lastRequestedWidth > 0 && lastRequestedFullHeight > 0) {
			return;
		}
		int width;
		int fullHeight;
		if (isMobileProfile()) {
			width = getTargetGameWidth();
			fullHeight = getTargetGameFullHeight();
		} else {
			width = DESKTOP_GAME_WIDTH;
			fullHeight = DESKTOP_FULL_HEIGHT;
		}
		if (width <= 0 || fullHeight <= 0) {
			return;
		}
		if (width == lastRequestedWidth && fullHeight == lastRequestedFullHeight) {
			return;
		}
		lastRequestedWidth = width;
		lastRequestedFullHeight = fullHeight;
		if (client.getGameWidth() != width || client.getGameHeight() + 12 != fullHeight) {
			client.resizeWidth = width;
			client.resizeHeight = fullHeight;
		}
	}

	private void publishClientState() {
		if (client == null || client.getLocalPlayer() == null) {
			publishClientState(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, "", 0, 0, 0, 0, 0, "", "");
			return;
		}
		ORSCharacter player = client.getLocalPlayer();
		int tileSize = Math.max(1, client.getTileSize());
		int localX = client.getLocalPlayerX();
		int localZ = client.getLocalPlayerZ();
		int midX = client.getMidRegionBaseX();
		int midZ = client.getMidRegionBaseZ();
		publishClientState(
			true,
			localX,
			localZ,
			midX,
			midZ,
			localX + midX,
			localZ + midZ,
			player.currentX,
			player.currentZ,
			(player.currentX - 64) / tileSize,
			(player.currentZ - 64) / tileSize,
			client.getGameWidth(),
			client.getGameHeight(),
			client.cameraRotation,
			client.getCameraAngle(),
			client.cameraZoom,
			osConfig.C_LAST_ZOOM,
			client.getLoginScreenNumber(),
			client.getCurrentViewModeName(),
			client.getMouseX(),
			client.getMouseY(),
			client.getMouseClick(),
			client.getMouseButtonDown(),
			client.getLastMouseDown(),
			client.chatMessageInput,
			client.inputTextCurrent);
	}

		private void publishUiState() {
			if (client == null) {
				publishUiState(false, false, false, false, 0, "", "", false, false, false, false, false, false, false, false, 0, 0, 0, 0,
					false, false, "", false, "", false, "", "{}", "");
				return;
			}
		int menuWidth = 0;
		int menuHeight = 0;
		if (client.menuCommon != null) {
			menuWidth = client.menuCommon.getWidth();
			menuHeight = client.menuCommon.getHeight();
		}
		publishUiState(
			Config.C_CUSTOM_UI,
			Config.S_WANT_CUSTOM_UI,
			Config.isAndroid(),
			Config.isWeb(),
			client.showUiTab,
			client.getVoidscapeMobileSidePanelKey(),
			String.valueOf(client.messageTabSelected),
			client.isVoidscapeChatPanelHidden(),
			client.isVoidscapePhoneLandscapeLooseChat(),
			client.isVoidscapeMobileLooseChatMessages(),
			client.isVoidscapeMobilePanelShell(),
			client.isVoidscapeCanvasTopTabsVisible(),
			client.isVoidscapeCanvasPanelRailVisible(),
			client.isVoidscapeCanvasPanelDockVisible(),
			client.topMouseMenuVisible,
			client.menuX,
			client.menuY,
			menuWidth,
			menuHeight,
			osConfig.F_SHOWING_KEYBOARD,
			client.isWebOverlayDialogVisible(),
			client.getWebOverlayDialogName(),
			client.hasVoidscapeMobileSpellShortcut(),
			client.getVoidscapeMobileSpellShortcutLabel(),
			client.isShowDialogBank(),
			client.getBank() == null ? "" : client.getBank().getBankRendererName(),
			client.getBank() == null ? "{\"open\":false}" : client.getBank().getBankDiagnosticsJson(),
			client.getVoidscapeMobileSpellShortcutName());
		}

	private void publishLoginState() {
		if (client == null) {
			publishLoginState(0, "", "", 0, "", "", false, false);
			return;
		}
		publishLoginState(
			client.getLoginScreenNumber(),
			client.getCurrentViewModeName(),
			client.getWebLoginUserText(),
			client.getWebLoginPasswordLength(),
			client.getWebLoginStatus1Text(),
			client.getWebLoginStatus2Text(),
			client.isWebLoginUserFocused(),
			client.isWebLoginPassFocused());
	}

	private void publishWorldMapState() {
		if (client == null || client.worldMapPanel == null) {
			publishWorldMapState(false, false, "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				false, 0, 0, 0, 0, -1, -1, "0", "0", false, 0, 0);
			return;
		}
		publishWorldMapState(
			client.worldMapPanel.isVisible(),
			client.worldMapPanel.isSearchFocused(),
			client.worldMapPanel.getSearchQuery(),
			client.worldMapPanel.getCurrentFloor(),
			client.worldMapPanel.getZoomLevel(),
			client.worldMapPanel.getPanXRounded(),
			client.worldMapPanel.getPanYRounded(),
			client.worldMapPanel.getWindowX(),
			client.worldMapPanel.getWindowY(),
			client.worldMapPanel.getWindowW(),
			client.worldMapPanel.getWindowH(),
			client.worldMapPanel.getContentCenterX(),
			client.worldMapPanel.getContentCenterY(),
			client.worldMapPanel.getZoomInCenterX(),
			client.worldMapPanel.getZoomInCenterY(),
			client.worldMapPanel.getZoomOutCenterX(),
			client.worldMapPanel.getZoomOutCenterY(),
			client.worldMapPanel.getResetCenterX(),
			client.worldMapPanel.getResetCenterY(),
			client.worldMapPanel.getSearchCenterX(),
			client.worldMapPanel.getSearchCenterY(),
			client.worldMapPanel.getCloseCenterX(),
			client.worldMapPanel.getCloseCenterY(),
			client.webWorldMapButtonVisible,
			client.webWorldMapButtonX,
			client.webWorldMapButtonY,
			client.webWorldMapButtonW,
			client.webWorldMapButtonH,
			client.webWorldWalkLastRequestX,
			client.webWorldWalkLastRequestY,
			String.valueOf(client.webWorldWalkLastRequestAtMillis),
			String.valueOf(client.webWorldWalkLastRouteAtMillis),
			client.webWorldWalkLastRouteOk,
			client.webWorldWalkLastRouteReason,
			client.webWorldWalkLastRouteCount);
	}

	private void handleInputEvent(String event) {
		if (event == null || event.length() < 3 || event.charAt(1) != ',') {
			return;
		}
		try {
			char type = event.charAt(0);
			if (type == 'p') {
				int firstComma = event.indexOf(',', 2);
				int secondComma = commaAfter(event, firstComma);
				int thirdComma = commaAfter(event, secondComma);
				if (firstComma < 0 || secondComma < 0 || thirdComma < 0) {
					return;
				}
				handlePointerEvent(
					Integer.parseInt(event.substring(2, firstComma)),
					Integer.parseInt(event.substring(firstComma + 1, secondComma)),
					Integer.parseInt(event.substring(secondComma + 1, thirdComma)),
					Integer.parseInt(event.substring(thirdComma + 1)));
			} else if (type == 'k') {
				int comma = event.indexOf(',', 2);
				if (comma < 0) {
					return;
				}
				handleKeyEvent(Integer.parseInt(event.substring(2, comma)) == 1, Integer.parseInt(event.substring(comma + 1)));
			} else if (type == 't') {
				handleKeyInput(Integer.parseInt(event.substring(2)));
			} else if (type == 's') {
				osConfig.F_SHOWING_KEYBOARD = Integer.parseInt(event.substring(2)) == 1;
			} else if (type == 'w') {
				handleScrollEvent(Integer.parseInt(event.substring(2)));
			} else if (type == 'g') {
				int firstComma = event.indexOf(',', 2);
				int secondComma = commaAfter(event, firstComma);
				if (firstComma < 0 || secondComma < 0) {
					return;
				}
				handleWorldMapTouchEvent(
					Integer.parseInt(event.substring(2, firstComma)),
					Integer.parseInt(event.substring(firstComma + 1, secondComma)),
					Integer.parseInt(event.substring(secondComma + 1)));
			} else if (type == 'm') {
				int comma = event.indexOf(',', 2);
				if (comma < 0) {
					return;
				}
				handleTopMenuTouchRelease(Integer.parseInt(event.substring(2, comma)), Integer.parseInt(event.substring(comma + 1)));
		} else if (type == 'u') {
			handleMobilePanelEvent(event.substring(2));
		} else if (type == 'c') {
			handleMobileChatEvent(event.substring(2));
		} else if (type == 'b') {
			handleBackAction();
		}
		} catch (RuntimeException ignored) {
		}
	}

	private int commaAfter(String event, int previousComma) {
		if (previousComma < 0) {
			return -1;
		}
		return event.indexOf(',', previousComma + 1);
	}

	private void handlePointerEvent(int action, int x, int y, int button) {
		client.mouseX = clamp(x, 0, getCanvasWidth() - 1);
		client.mouseY = clamp(y, 0, getCanvasHeight() - 1);
		client.lastMouseAction = 0;
		if (pkCatchingPointerActive) {
			if (action == 2 || action == 4) {
				pkCatchingPointerActive = false;
				client.currentMouseButtonDown = 0;
				client.lastMouseButtonDown = 0;
			}
			return;
		}

		if (action == 0) {
			client.currentMouseButtonDown = button;
			return;
		}

		if (action == 1) {
			if (client.consumeChristmasCrackerPointerAt(client.mouseX, client.mouseY, button != 2)) {
				return;
			}
			if (button != 2
				&& (client.closeWelcomeDialogAt(client.mouseX, client.mouseY)
					|| client.closeServerMessageDialogAt(client.mouseX, client.mouseY)
					|| client.closeFarmSimDialogAt(client.mouseX, client.mouseY)
				|| client.closeWildWarningDialogAt(client.mouseX, client.mouseY))) {
				return;
			}
			if (client.consumePkCatchingPointerAt(client.mouseX, client.mouseY, button != 2)) {
				pkCatchingPointerActive = true;
				return;
			}
			client.currentMouseButtonDown = button;
			client.lastMouseButtonDown = button;
			client.mouseButtonClick = button;
			client.addMouseClick(button, client.mouseX, client.mouseY);
			return;
		}

		if (action == 3) {
			client.currentMouseButtonDown = button;
			client.lastMouseButtonDown = button;
			client.mouseButtonClick = button;
			return;
		}

		if (action == 4) {
			if (client.topMouseMenuVisible && !isInsideTopMouseMenuTouchBounds(client.mouseX, client.mouseY)) {
				keepMouseInsideTopMenu();
			}
			client.currentMouseButtonDown = 0;
			client.lastMouseButtonDown = 0;
			return;
		}

		client.currentMouseButtonDown = 0;
	}

	private void handleWorldMapTouchEvent(int phase, int x, int y) {
		if (client == null) return;
		client.mouseX = clamp(x, 0, getCanvasWidth() - 1);
		client.mouseY = clamp(y, 0, getCanvasHeight() - 1);
		if (pkCatchingPointerActive) {
			if (phase == 2 || phase == 3) pkCatchingPointerActive = false;
			return;
		}
		if (phase == 0 && client.consumePkCatchingPointerAt(client.mouseX, client.mouseY, true)) {
			pkCatchingPointerActive = true;
			clearWorldMapTouchState(true);
			return;
		}
		if (client.worldMapPanel == null || !client.worldMapPanel.isVisible()) {
			clearWorldMapTouchState(true);
			return;
		}

		client.lastMouseAction = 0;
		client.lastMouseButtonDown = 0;

		if (phase == 0) {
			worldMapTouchActive = client.worldMapPanel.containsWindow(client.mouseX, client.mouseY);
			worldMapTouchMoved = false;
			worldMapTouchStartX = client.mouseX;
			worldMapTouchStartY = client.mouseY;
			worldMapTouchReleaseUntilMillis = 0L;
			if (worldMapTouchActive) {
				client.currentMouseButtonDown = 1;
			}
			return;
		}

		if (!worldMapTouchActive) {
			return;
		}

		if (phase == 3) {
			int dx = client.mouseX - worldMapTouchStartX;
			int dy = client.mouseY - worldMapTouchStartY;
			if (Math.abs(dx) <= WORLD_MAP_TOUCH_DEADZONE_PX && Math.abs(dy) <= WORLD_MAP_TOUCH_DEADZONE_PX) {
				int cancelDelta = WORLD_MAP_TOUCH_DEADZONE_PX + 2;
				int movedX = worldMapTouchStartX + cancelDelta;
				if (movedX >= getCanvasWidth()) {
					movedX = worldMapTouchStartX - cancelDelta;
				}
				client.mouseX = clamp(movedX, 0, getCanvasWidth() - 1);
			}
			client.currentMouseButtonDown = 1;
			client.lastMouseButtonDown = 0;
			worldMapTouchMoved = true;
			worldMapTouchReleaseUntilMillis = System.currentTimeMillis() + WORLD_MAP_TOUCH_RELEASE_HOLD_MILLIS;
			worldMapTouchActive = false;
			return;
		}

		if (Math.abs(client.mouseX - worldMapTouchStartX) > WORLD_MAP_TOUCH_DEADZONE_PX
			|| Math.abs(client.mouseY - worldMapTouchStartY) > WORLD_MAP_TOUCH_DEADZONE_PX) {
			worldMapTouchMoved = true;
		}

		if (phase == 1) {
			client.currentMouseButtonDown = 1;
			return;
		}

		if (phase == 2) {
			if (worldMapTouchMoved) {
				client.currentMouseButtonDown = 0;
				worldMapTouchReleaseUntilMillis = 0L;
			} else {
				client.currentMouseButtonDown = 1;
				worldMapTouchReleaseUntilMillis = System.currentTimeMillis() + WORLD_MAP_TOUCH_RELEASE_HOLD_MILLIS;
			}
			worldMapTouchActive = false;
		}
	}

	private void updateWorldMapTouchRelease() {
		if (client == null) {
			return;
		}
		if (client.worldMapPanel == null || !client.worldMapPanel.isVisible()) {
			clearWorldMapTouchState(true);
			return;
		}
		if (worldMapTouchReleaseUntilMillis > 0L && System.currentTimeMillis() >= worldMapTouchReleaseUntilMillis) {
			worldMapTouchReleaseUntilMillis = 0L;
			client.currentMouseButtonDown = 0;
			client.lastMouseButtonDown = 0;
		}
	}

	private void clearWorldMapTouchState(boolean clearMouseButton) {
		worldMapTouchActive = false;
		worldMapTouchMoved = false;
		worldMapTouchReleaseUntilMillis = 0L;
		if (clearMouseButton && client != null) {
			client.currentMouseButtonDown = 0;
			client.lastMouseButtonDown = 0;
		}
	}

	private void handleTopMenuTouchRelease(int x, int y) {
		if (client == null) {
			return;
		}
		client.mouseX = clamp(x, 0, getCanvasWidth() - 1);
		client.mouseY = clamp(y, 0, getCanvasHeight() - 1);
		client.lastMouseAction = 0;
		if (!client.topMouseMenuVisible) {
			client.currentMouseButtonDown = 0;
			client.lastMouseButtonDown = 0;
			return;
		}
		if (isInsideTopMouseMenuTouchBounds(client.mouseX, client.mouseY)) {
			client.currentMouseButtonDown = 1;
			client.lastMouseButtonDown = 1;
			client.mouseButtonClick = 1;
		} else {
			client.topMouseMenuVisible = false;
			client.currentMouseButtonDown = 0;
			client.lastMouseButtonDown = 0;
			client.mouseButtonClick = 0;
		}
	}

	private boolean isInsideTopMouseMenuTouchBounds(int x, int y) {
		if (client == null || client.menuCommon == null) {
			return false;
		}
		int width = client.menuCommon.getWidth();
		int height = client.menuCommon.getHeight();
		return client.menuX - TOP_MENU_TOUCH_PADDING <= x
			&& client.menuY - TOP_MENU_TOUCH_PADDING <= y
			&& client.menuX + width + TOP_MENU_TOUCH_PADDING >= x
			&& client.menuY + height + TOP_MENU_TOUCH_PADDING >= y;
	}

	private void keepMouseInsideTopMenu() {
		if (client == null || client.menuCommon == null) {
			return;
		}
		int width = Math.max(1, client.menuCommon.getWidth());
		int height = Math.max(1, client.menuCommon.getHeight());
		int offsetX = Math.max(0, Math.min(width - 1, width / 2));
		int offsetY = Math.max(0, Math.min(height - 1, 8));
		client.mouseX = clamp(client.menuX + offsetX, 0, getCanvasWidth() - 1);
		client.mouseY = clamp(client.menuY + offsetY, 0, getCanvasHeight() - 1);
	}

	private void handleBackAction() {
		if (client == null) {
			return;
		}
		client.lastMouseAction = 0;
		client.handleAndroidBackButton();
	}

	private void handleMobilePanelEvent(String panelKey) {
		if (client == null || panelKey == null || panelKey.length() == 0) {
			return;
		}
		client.lastMouseAction = 0;
		client.openVoidscapeMobileUiPanel(panelKey);
	}

	private void handleMobileChatEvent(String chatKey) {
		if (client == null || chatKey == null || chatKey.length() == 0) {
			return;
		}
		client.lastMouseAction = 0;
		if ("compose-main".equals(chatKey)) {
			client.openVoidscapeMobilePublicChatInput();
			return;
		}
		client.openVoidscapeMobileChatTab(chatKey);
	}

	private int getCanvasWidth() {
		if (client != null && client.getSurface() != null && client.getSurface().width2 > 0) {
			return client.getSurface().width2;
		}
		return Math.max(1, client != null ? client.getGameWidth() : 512);
	}

	private int getCanvasHeight() {
		if (client != null && client.getSurface() != null && client.getSurface().height2 > 0) {
			return client.getSurface().height2;
		}
		return Math.max(1, client != null ? client.getGameHeight() + 12 : 346);
	}

	private int clamp(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		return Math.min(value, max);
	}

	private void handleKeyEvent(boolean down, int key) {
		if (!down) {
			if (key == 39) client.keyRight = false;
			if (key == 37) client.keyLeft = false;
			if (key == 38) client.keyUp = false;
			if (key == 40) client.keyDown = false;
			if (key == 34) client.pageDown = false;
			if (key == 33) client.pageUp = false;
			return;
		}
		if (client.consumeDuelJournalKeyDown(key)) {
			return;
		}
		if (handleNavigationKeyDown(key)) {
			return;
		}
		handleKeyInput(key);
	}

	private boolean handleNavigationKeyDown(int key) {
		if (key == 37 || key == 38 || key == 39 || key == 40) {
			client.nudgeWebMobileCameraControl(key);
			if (key == 39) client.keyRight = true;
			if (key == 37) client.keyLeft = true;
			if (key == 38) client.keyUp = true;
			if (key == 40) client.keyDown = true;
			client.lastMouseAction = 0;
			return true;
		}
		if (key == 34) {
			client.pageDown = true;
			client.lastMouseAction = 0;
			return true;
		}
		if (key == 33) {
			client.pageUp = true;
			client.lastMouseAction = 0;
			return true;
		}
		return false;
	}

	private void handleKeyInput(int key) {
		if (key == 0) {
			return;
		}
		if (client.consumeDuelJournalKeyDown(key)) return;

		if (isWorldMapSearchFocused()) {
			client.worldMapPanel.handleSearchKey((char) key, key);
			client.lastMouseAction = 0;
			if (!client.worldMapPanel.isSearchFocused() && osConfig.F_SHOWING_KEYBOARD) {
				closeKeyboard();
			}
			return;
		}

		boolean hitInputFilter = false;
		for (int i = 0; i < Fonts.inputFilterChars.length(); i++) {
			if (Fonts.inputFilterChars.charAt(i) == key) {
				hitInputFilter = true;
				break;
			}
		}

		client.handleKeyPress((byte) 126, key);
		client.lastMouseAction = 0;

		if (key == 37 || key == 38 || key == 39 || key == 40) client.nudgeWebMobileCameraControl(key);
		if (key == 39) client.keyRight = true;
		if (key == 37) client.keyLeft = true;
		if (key == 38) client.keyUp = true;
		if (key == 40) client.keyDown = true;
		if (key == 34) client.pageDown = true;
		if (key == 33) client.pageUp = true;
		if (key == 10 || key == 13) client.enterPressed = true;

		if (hitInputFilter && client.inputTextCurrent.length() < 20) {
			client.inputTextCurrent = client.inputTextCurrent + (char) key;
		}
		if (hitInputFilter && client.chatMessageInput.length() < 80 && !client.getIsSleeping()) {
			client.chatMessageInput = client.chatMessageInput + (char) key;
		}
		if (key == '\b' && client.inputTextCurrent.length() > 0) {
			client.inputTextCurrent = client.inputTextCurrent.substring(0, client.inputTextCurrent.length() - 1);
		}
		if (key == '\b' && client.chatMessageInput.length() > 0) {
			client.chatMessageInput = client.chatMessageInput.substring(0, client.chatMessageInput.length() - 1);
		}
		if (key == 10 || key == 13) {
			client.inputTextFinal = client.inputTextCurrent;
			client.chatMessageInputCommit = client.chatMessageInput;
		}
	}

	private boolean isWorldMapSearchFocused() {
		return client != null && client.worldMapPanel != null && client.worldMapPanel.isSearchFocused();
	}

	private void handleScrollEvent(int amount) {
		if (client == null || amount == 0) {
			return;
		}
		client.lastMouseAction = 0;
		int delta = clamp(amount, -8, 8);
		if (!client.routeDuelJournalScroll(delta)) client.runScroll(delta);
	}

	private void syncMobileInputHints() {
		if (client == null) {
			updateMobileInputHints(false, false, false, false, getAndroidLongPressMillis());
			return;
		}
		updateMobileInputHints(hasScrollableTouchUi(), hasScrollableMessageTab(), isWorldMapTouchActive(), isTopMouseMenuVisible(),
			getAndroidLongPressMillis());
	}

	private int getAndroidLongPressMillis() {
		int units = osConfig.C_LONG_PRESS_TIMER;
		if (units < 1) {
			units = 1;
		}
		if (units > 12) {
			units = 12;
		}
		return units * 50;
	}

	private boolean hasScrollableTouchUi() {
		if (client.isDuelJournalVisible() || client.showUiTab != 0 || client.isShowDialogBank()) {
			return true;
		}
		return (Config.S_SPAWN_AUCTION_NPCS && client.auctionHouse != null && client.auctionHouse.isVisible())
			|| (client.onlineList != null && client.onlineList.isVisible())
			|| (Config.S_WANT_SKILL_MENUS && client.skillGuideInterface != null && client.skillGuideInterface.isVisible())
			|| (Config.S_WANT_QUEST_MENUS && client.questGuideInterface != null && client.questGuideInterface.isVisible())
			|| (client.experienceConfigInterface != null && client.experienceConfigInterface.isVisible())
			|| (client.ironmanInterface != null && client.ironmanInterface.isVisible())
			|| (client.achievementInterface != null && client.achievementInterface.isVisible())
			|| (Config.S_WANT_SKILL_MENUS && client.doSkillInterface != null && client.doSkillInterface.isVisible())
			|| (Config.S_ITEMS_ON_DEATH_MENU && client.lostOnDeathInterface != null && client.lostOnDeathInterface.isVisible())
			|| (client.territorySignupInterface != null && client.territorySignupInterface.isVisible());
	}

	private boolean hasScrollableMessageTab() {
		return client.hasScroll(client.messageTabSelected);
	}

	private boolean isWorldMapTouchActive() {
		return client.worldMapPanel != null && client.worldMapPanel.isVisible();
	}

	private boolean isTopMouseMenuVisible() {
		return client.topMouseMenuVisible && client.menuCommon != null;
	}

	@JSBody(params = { "percentage", "status" }, script =
		"const canvas = document.getElementById('game');" +
		"const ctx = canvas.getContext('2d');" +
		"ctx.fillStyle = '#050505'; ctx.fillRect(0, 0, canvas.width, canvas.height);" +
		"ctx.strokeStyle = '#6b5f32'; ctx.strokeRect(116, 218, 408, 18);" +
		"ctx.fillStyle = '#c7b36a'; ctx.fillRect(118, 220, Math.max(0, Math.min(404, percentage * 4.04)), 14);" +
		"ctx.fillStyle = '#d8d8d8'; ctx.font = '14px monospace';" +
		"ctx.fillText(status || 'Loading', 116, 204);" +
		"document.getElementById('status').textContent = (status || 'Loading') + ' ' + percentage + '%';")
	private static native void drawLoadingScreen(int percentage, String status);

	@JSBody(params = { "status" }, script = "document.getElementById('status').textContent = status;")
	private static native void setStatus(String status);

	@JSBody(params = { "url" }, script =
		"if (window.__voidscapeOpenClientUrl) return !!window.__voidscapeOpenClientUrl(url);" +
		"try {" +
		"  const value = String(url || '').trim();" +
		"  if (!value) return false;" +
		"  const parsed = new URL(value, window.location.href);" +
		"  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') return false;" +
		"  return !!window.open(parsed.href, '_blank', 'noopener');" +
		"} catch (ignored) {" +
		"  return false;" +
		"}")
	private static native boolean openWindow(String url);

	@JSBody(params = { "title" }, script =
		"document.title = title || 'Voidscape';" +
		"const inGame = !!title && title.indexOf(' -- ') >= 0;" +
		"window.__voidscapeInGame = inGame;" +
		"if (document.body) {" +
		"  document.body.classList.toggle('in-game', inGame);" +
		"  if (!inGame) document.body.classList.remove('panel-drawer-open', 'chat-tray-open', 'chat-helper-visible', 'world-map-open', 'shared-panel-open');" +
		"}" +
		"if (!inGame) {" +
		"  window.__voidscapePanelDrawerOpen = false;" +
		"  window.__voidscapeChatTrayOpen = false;" +
		"}")
	private static native void setDocumentTitle(String title);

	@JSBody(params = { "width", "height" }, script =
		"const canvas = document.getElementById('game');" +
		"if (!canvas) return true;" +
		"document.body.classList.add('game-drawn');" +
		"if (!canvas.__voidscapeFocusedOnce) {" +
		"  canvas.__voidscapeFocusedOnce = true;" +
		"  try { canvas.focus({ preventScroll: true }); } catch (ignored) { try { canvas.focus(); } catch (ignoredAgain) {} }" +
		"}" +
		"document.documentElement.style.setProperty('--game-aspect', width + ' / ' + height);" +
		"document.documentElement.style.setProperty('--game-aspect-ratio', String(width / Math.max(1, height)));" +
		"canvas.style.aspectRatio = width + ' / ' + height;" +
		"const sizeChanged = canvas.width !== width || canvas.height !== height;" +
		"if (sizeChanged) {" +
		"  canvas.width = width;" +
		"  canvas.height = height;" +
		"  canvas.__voidscapeImageData = null;" +
		"  canvas.__voidscapeImageData32 = null;" +
		"}" +
		"if (typeof window.__voidscapePrepareFrameUpload === 'function') {" +
		"  return !!window.__voidscapePrepareFrameUpload(width, height, sizeChanged);" +
		"}" +
		"return true;")
	private static native boolean prepareFrameUpload(int width, int height);

	@JSBody(params = { "pixels", "width", "height" }, script =
		"if (!pixels) return;" +
		"const data = pixels && pixels.data ? pixels.data : pixels;" +
		"if (!data || typeof data.length !== 'number') return;" +
		"const canvas = document.getElementById('game');" +
		"if (!canvas) return;" +
		"document.body.classList.add('game-drawn');" +
		"if (!canvas.__voidscapeFocusedOnce) {" +
		"  canvas.__voidscapeFocusedOnce = true;" +
		"  try { canvas.focus({ preventScroll: true }); } catch (ignored) { try { canvas.focus(); } catch (ignoredAgain) {} }" +
		"}" +
		"document.documentElement.style.setProperty('--game-aspect', width + ' / ' + height);" +
		"document.documentElement.style.setProperty('--game-aspect-ratio', String(width / Math.max(1, height)));" +
		"canvas.style.aspectRatio = width + ' / ' + height;" +
		"if (canvas.width !== width || canvas.height !== height) {" +
		"  canvas.width = width;" +
		"  canvas.height = height;" +
		"  canvas.__voidscapeImageData = null;" +
		"  canvas.__voidscapeImageData32 = null;" +
		"}" +
		"const ctx = canvas.getContext('2d');" +
		"if (!ctx) return;" +
		"ctx.imageSmoothingEnabled = false;" +
		"let image = canvas.__voidscapeImageData;" +
		"if (!image || image.width !== width || image.height !== height) {" +
		"  image = ctx.createImageData(width, height);" +
		"  canvas.__voidscapeImageData = image;" +
		"  canvas.__voidscapeImageData32 = null;" +
		"}" +
		"if (!image || !image.data) return;" +
		"let out32 = canvas.__voidscapeImageData32;" +
		"if (!out32 || out32.buffer !== image.data.buffer || out32.length !== (image.data.length >> 2)) {" +
		"  out32 = new Uint32Array(image.data.buffer);" +
		"  canvas.__voidscapeImageData32 = out32;" +
		"}" +
		"if (canvas.__voidscapeLittleEndian === undefined) {" +
		"  const buffer = new ArrayBuffer(4);" +
		"  new Uint32Array(buffer)[0] = 0x0a0b0c0d;" +
		"  canvas.__voidscapeLittleEndian = new Uint8Array(buffer)[0] === 0x0d;" +
		"}" +
		"const count = Math.min(data.length, out32.length);" +
		"if (canvas.__voidscapeLittleEndian) {" +
		"  for (let i = 0; i < count; i++) {" +
		"    const rgb32 = data[i] | 0;" +
		"    out32[i] = 0xff000000 | ((rgb32 & 255) << 16) | (rgb32 & 0xff00) | ((rgb32 >>> 16) & 255);" +
		"  }" +
		"} else {" +
		"  const out = image.data;" +
		"  for (let i = 0, j = 0; i < count; i++, j += 4) {" +
		"    const rgbBytes = data[i] | 0;" +
		"    out[j] = (rgbBytes >>> 16) & 255;" +
		"    out[j + 1] = (rgbBytes >>> 8) & 255;" +
		"    out[j + 2] = rgbBytes & 255;" +
		"    out[j + 3] = 255;" +
		"  }" +
		"}" +
			"ctx.putImageData(image, 0, 0);" +
			"if (typeof window.__voidscapeRecordFrameUpload === 'function') window.__voidscapeRecordFrameUpload(width, height);")
		private static native void drawPixels(int[] pixels, int width, int height);

	@JSBody(params = {}, script =
		"if (window.__voidscapeInputInstalled) return;" +
		"window.__voidscapeInputInstalled = true;" +
			"window.__voidscapeInputQueue = window.__voidscapeInputQueue || [];" +
				"window.__voidscapeKeyboardWanted = false;" +
				"window.__voidscapeKeyboardOpen = false;" +
				"window.__voidscapeKeyboardClosingUntil = window.__voidscapeKeyboardClosingUntil || 0;" +
				"window.__voidscapeNextTapSecondary = false;" +
				"window.__voidscapeTopMenuVisible = false;" +
				"window.__voidscapeLongPressMillis = window.__voidscapeLongPressMillis || 250;" +
				"window.__voidscapeScrollHistory = window.__voidscapeScrollHistory || [];" +
				"const queue = function(value) {" +
		"  const q = window.__voidscapeInputQueue;" +
		"  q.push(value);" +
		"  if (q.length > 256) q.splice(0, q.length - 256);" +
		"  if (typeof window.__voidscapeRecordInputActivity === 'function') {" +
		"    window.__voidscapeRecordInputActivity(value);" +
		"  }" +
		"};" +
		"const setKeyboardOpen = function(open) {" +
		"  open = !!open;" +
		"  if (window.__voidscapeKeyboardOpen === open && document.body.classList.contains('keyboard-open') === open) return;" +
		"  const wasOpen = !!window.__voidscapeKeyboardOpen || document.body.classList.contains('keyboard-open');" +
		"  if (open) window.__voidscapeKeyboardClosingUntil = 0;" +
		"  else if (wasOpen) window.__voidscapeKeyboardClosingUntil = Date.now() + 520;" +
		"  window.__voidscapeKeyboardOpen = open;" +
		"  document.body.classList.toggle('keyboard-open', open);" +
		"  const button = document.getElementById('keyboard-button');" +
		"  if (button) {" +
		"    button.setAttribute('aria-pressed', open ? 'true' : 'false');" +
		"    button.setAttribute('aria-label', open ? 'Hide keyboard' : 'Show keyboard');" +
			"  }" +
			"  if (window.__voidscapeUpdateViewportHeight) {" +
			"    window.__voidscapeUpdateViewportHeight();" +
			"    if (!open) {" +
			"      setTimeout(window.__voidscapeUpdateViewportHeight, 120);" +
			"      setTimeout(window.__voidscapeUpdateViewportHeight, 560);" +
			"    }" +
			"  }" +
			"  queue('s,' + (open ? 1 : 0));" +
			"};" +
			"const setActionArmed = function(armed) {" +
			"  armed = !!armed;" +
			"  window.__voidscapeNextTapSecondary = armed;" +
			"  document.body.classList.toggle('context-armed', armed);" +
			"};" +
				"const setPanelDrawerOpen = function(open) {" +
				"  open = !!open;" +
				"  window.__voidscapePanelDrawerOpen = open;" +
			"  if (document.body) document.body.classList.toggle('panel-drawer-open', open);" +
			"  const button = document.getElementById('panel-button');" +
			"  if (button) {" +
			"    button.setAttribute('aria-pressed', open ? 'true' : 'false');" +
			"    button.setAttribute('aria-label', open ? 'Close mobile panel shortcuts' : 'Open mobile panel shortcuts');" +
				"  }" +
				"};" +
				"window.__voidscapeSetPanelDrawerOpen = setPanelDrawerOpen;" +
				"const setChatTrayOpen = function(open) {" +
				"  open = !!open;" +
				"  window.__voidscapeChatTrayOpen = open;" +
				"  if (document.body) document.body.classList.toggle('chat-tray-open', open);" +
				"  const button = document.getElementById('chat-button');" +
				"  if (button) {" +
				"    button.setAttribute('aria-pressed', open ? 'true' : 'false');" +
				"    button.setAttribute('aria-label', open ? 'Close mobile chat shortcuts' : 'Open mobile chat shortcuts');" +
				"  }" +
				"};" +
				"window.__voidscapeSetChatTrayOpen = setChatTrayOpen;" +
				"let captureKeydownValue = 0;" +
		"let captureKeydownAt = 0;" +
		"const markCaptureKeydown = function(key) {" +
		"  captureKeydownValue = key;" +
		"  captureKeydownAt = Date.now();" +
		"};" +
		"const alreadyHandledCaptureKeydown = function(key) {" +
		"  return captureKeydownValue === key && Date.now() - captureKeydownAt < 80;" +
		"};" +
		"const INPUT_PUNCTUATION = \"!\\\"$%^&*()-_=+[{]};:'@#~,<.>/?\\\\| \";" +
		"let captureComposing = false;" +
		"let pendingCompositionText = '';" +
		"let pendingCompositionTimer = 0;" +
		"const isAllowedKeyboardChar = function(ch) {" +
		"  const code = ch.charCodeAt(0);" +
		"  return code === 163 || (code >= 48 && code <= 57) || (code >= 65 && code <= 90) || (code >= 97 && code <= 122) || INPUT_PUNCTUATION.indexOf(ch) >= 0;" +
		"};" +
		"const normalizeKeyboardText = function(text) {" +
		"  text = String(text || '').replace(/[\\r\\n\\t]+/g, ' ');" +
		"  let out = '';" +
		"  for (let i = 0; i < text.length; i++) {" +
		"    const ch = text.charAt(i);" +
		"    if (isAllowedKeyboardChar(ch)) out += ch;" +
		"  }" +
		"  return out;" +
		"};" +
		"const queueText = function(text) {" +
		"  text = normalizeKeyboardText(text);" +
		"  if (!text.length) return false;" +
		"  for (let i = 0; i < text.length; i++) queue('t,' + text.charCodeAt(i));" +
		"  return true;" +
		"};" +
		"const clearPendingComposition = function(text) {" +
		"  if (!pendingCompositionTimer) return;" +
		"  if (text && normalizeKeyboardText(text) !== normalizeKeyboardText(pendingCompositionText)) return;" +
		"  clearTimeout(pendingCompositionTimer);" +
		"  pendingCompositionTimer = 0;" +
		"  pendingCompositionText = '';" +
		"};" +
		"const scheduleCompositionText = function(text) {" +
		"  clearPendingComposition();" +
		"  pendingCompositionText = text || '';" +
		"  pendingCompositionTimer = setTimeout(function() {" +
		"    const textToQueue = pendingCompositionText;" +
		"    pendingCompositionText = '';" +
		"    pendingCompositionTimer = 0;" +
		"    queueText(textToQueue);" +
		"  }, 45);" +
		"};" +
		"const ensureCapture = function() {" +
		"  let capture = document.getElementById('keyboard-capture');" +
		"  if (capture) return capture;" +
		"  capture = document.createElement('textarea');" +
		"  capture.id = 'keyboard-capture';" +
		"  capture.setAttribute('aria-hidden', 'true');" +
		"  capture.setAttribute('autocomplete', 'off');" +
		"  capture.setAttribute('autocorrect', 'off');" +
		"  capture.setAttribute('autocapitalize', 'none');" +
		"  capture.setAttribute('spellcheck', 'false');" +
		"  capture.inputMode = 'text';" +
		"  capture.enterKeyHint = 'send';" +
		"  capture.style.position = 'fixed';" +
		"  capture.style.left = '0';" +
		"  capture.style.bottom = '0';" +
		"  capture.style.width = '1px';" +
		"  capture.style.height = '1px';" +
		"  capture.style.opacity = '0.01';" +
		"  capture.style.pointerEvents = 'none';" +
		"  capture.style.fontSize = '16px';" +
		"  document.body.appendChild(capture);" +
		"  capture.addEventListener('compositionstart', function() {" +
		"    clearPendingComposition();" +
		"    captureComposing = true;" +
		"  });" +
		"  capture.addEventListener('compositionend', function(e) {" +
		"    captureComposing = false;" +
		"    scheduleCompositionText((e && e.data) || capture.value || '');" +
		"    capture.value = '';" +
		"  });" +
		"  capture.addEventListener('beforeinput', function(e) {" +
		"    const type = e.inputType || '';" +
		"    if (e.isComposing || type.indexOf('insertComposition') === 0) return;" +
		"    if (type === 'deleteContentBackward') {" +
		"      if (alreadyHandledCaptureKeydown(8)) { e.preventDefault(); return; }" +
		"      queue('k,1,8');" +
		"      capture.value = '';" +
		"      e.preventDefault();" +
		"      return;" +
		"    }" +
		"    if (type === 'insertLineBreak') {" +
		"      if (alreadyHandledCaptureKeydown(10)) { e.preventDefault(); return; }" +
		"      queue('k,1,10');" +
		"      capture.value = '';" +
		"      e.preventDefault();" +
		"      return;" +
		"    }" +
		"    const text = e.data || '';" +
		"    if (text.length > 0) {" +
		"      clearPendingComposition(text);" +
		"      queueText(text);" +
		"      capture.value = '';" +
		"      e.preventDefault();" +
		"    }" +
		"  });" +
		"  capture.addEventListener('input', function() {" +
		"    if (captureComposing) return;" +
		"    const value = capture.value || '';" +
		"    clearPendingComposition(value);" +
		"    queueText(value);" +
		"    capture.value = '';" +
		"  });" +
		"  capture.addEventListener('paste', function(e) {" +
		"    const text = e.clipboardData ? e.clipboardData.getData('text') : '';" +
		"    clearPendingComposition(text);" +
		"    queueText(text);" +
		"    capture.value = '';" +
		"    e.preventDefault();" +
		"  });" +
		"  capture.addEventListener('focus', function() {" +
		"    capture.value = '';" +
		"    window.__voidscapeKeyboardWanted = true;" +
		"    setKeyboardOpen(true);" +
		"  });" +
		"  capture.addEventListener('blur', function() {" +
		"    captureComposing = false;" +
		"    clearPendingComposition();" +
		"    capture.value = '';" +
		"    window.__voidscapeKeyboardWanted = false;" +
		"    setKeyboardOpen(false);" +
		"  });" +
		"  return capture;" +
		"};" +
		"window.__voidscapeFocusKeyboard = function() {" +
		"  window.__voidscapeKeyboardWanted = true;" +
		"  const capture = ensureCapture();" +
		"  try { capture.focus({ preventScroll: true }); } catch (e) { capture.focus(); }" +
		"  if (document.activeElement === capture) setKeyboardOpen(true);" +
		"};" +
		"window.__voidscapeBlurKeyboard = function() {" +
		"  window.__voidscapeKeyboardWanted = false;" +
		"  const capture = ensureCapture();" +
		"  capture.blur();" +
		"  setKeyboardOpen(false);" +
		"};" +
		"const bindControlStart = function(control, fn) {" +
		"  let lastActivationAt = 0;" +
		"  let lastTouchLikeAt = 0;" +
		"  const start = function(e) {" +
		"    const now = Date.now();" +
		"    const touchLike = e.type === 'touchstart' || (e.pointerType && e.pointerType !== 'mouse');" +
		"    if ((e.type === 'mousedown' || (e.pointerType === 'mouse')) && e.button !== 0) return;" +
		"    if (e.type === 'mousedown' && now - lastTouchLikeAt < 700) {" +
		"      e.preventDefault();" +
		"      e.stopPropagation();" +
		"      return;" +
		"    }" +
		"    if (now - lastActivationAt < 220) {" +
		"      e.preventDefault();" +
		"      e.stopPropagation();" +
		"      return;" +
		"    }" +
		"    lastActivationAt = now;" +
		"    if (touchLike) lastTouchLikeAt = now;" +
		"    fn(e);" +
		"    e.preventDefault();" +
		"    e.stopPropagation();" +
		"  };" +
		"  control.addEventListener('pointerdown', start);" +
		"  control.addEventListener('touchstart', start, { passive: false });" +
		"  control.addEventListener('mousedown', start);" +
		"};" +
		"const bindControlEnd = function(control, fn) {" +
		"  const end = function(e) {" +
		"    fn(e);" +
		"    e.preventDefault();" +
		"    e.stopPropagation();" +
		"  };" +
		"  control.addEventListener('pointerup', end);" +
		"  control.addEventListener('pointercancel', end);" +
		"  control.addEventListener('touchend', end, { passive: false });" +
		"  control.addEventListener('touchcancel', end, { passive: false });" +
		"  control.addEventListener('mouseup', end);" +
		"  control.addEventListener('mouseleave', end);" +
		"};" +
			"const keyboardButton = document.getElementById('keyboard-button');" +
			"if (keyboardButton) {" +
			"  const shouldRequestPublicChatCompose = function() {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    const worldMap = window.__voidscapeWorldMapState || {};" +
			"    return !!window.__voidscapeInGame" +
			"      && !window.__voidscapePanelDrawerOpen" +
			"      && !window.__voidscapeChatTrayOpen" +
			"      && !worldMap.visible" +
			"      && !ui.blockingDialog" +
			"      && !ui.topMenuVisible" +
			"      && (ui.showUiTab | 0) === 0;" +
			"  };" +
			"  bindControlStart(keyboardButton, function() {" +
			"    setPanelDrawerOpen(false);" +
			"    setChatTrayOpen(false);" +
			"    const capture = ensureCapture();" +
		"    if (window.__voidscapeKeyboardOpen || document.activeElement === capture) {" +
		"      window.__voidscapeBlurKeyboard();" +
		"    } else {" +
		"      if (shouldRequestPublicChatCompose()) queue('c,compose-main');" +
		"      window.__voidscapeFocusKeyboard();" +
		"    }" +
			"  });" +
			"}" +
			"const panelButton = document.getElementById('panel-button');" +
			"if (panelButton) {" +
				"  bindControlStart(panelButton, function() {" +
				"    if (window.__voidscapeKeyboardOpen) window.__voidscapeBlurKeyboard();" +
				"    setChatTrayOpen(false);" +
				"    if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
				"    setPanelDrawerOpen(!window.__voidscapePanelDrawerOpen);" +
				"  });" +
				"}" +
				"const chatButton = document.getElementById('chat-button');" +
				"if (chatButton) {" +
				"  bindControlStart(chatButton, function() {" +
				"    if (window.__voidscapeKeyboardOpen) window.__voidscapeBlurKeyboard();" +
				"    setPanelDrawerOpen(false);" +
				"    if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
				"    const ui = window.__voidscapeUiState || {};" +
				"    if ((ui.showUiTab | 0) !== 0) queue('u,closed');" +
				"    setChatTrayOpen(!window.__voidscapeChatTrayOpen);" +
				"  });" +
				"}" +
				"document.querySelectorAll('.mobile-quick-button').forEach(function(control) {" +
			"  bindControlStart(control, function() {" +
			"    const panel = String(control.getAttribute('data-panel') || '').replace(/[^a-z0-9_-]/gi, '');" +
			"    if (!panel) return;" +
			"    if (window.__voidscapeKeyboardOpen) window.__voidscapeBlurKeyboard();" +
			"    setPanelDrawerOpen(false);" +
			"    setChatTrayOpen(false);" +
			"    if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
			"    queue('u,' + panel);" +
			"    if (navigator.vibrate) {" +
			"      try { navigator.vibrate(6); } catch (ignored) {}" +
			"    }" +
			"  });" +
			"});" +
				"document.querySelectorAll('.mobile-panel-button').forEach(function(control) {" +
			"  bindControlStart(control, function() {" +
			"    const panel = String(control.getAttribute('data-panel') || '').replace(/[^a-z0-9_-]/gi, '');" +
			"    if (!panel) return;" +
			"    queue('u,' + panel);" +
			"    setPanelDrawerOpen(false);" +
				"    if (navigator.vibrate) {" +
				"      try { navigator.vibrate(6); } catch (ignored) {}" +
				"    }" +
				"  });" +
				"});" +
				"document.querySelectorAll('.mobile-chat-button').forEach(function(control) {" +
				"  bindControlStart(control, function() {" +
				"    const chat = String(control.getAttribute('data-chat') || '').replace(/[^a-z0-9_-]/gi, '');" +
				"    if (!chat) return;" +
				"    queue('c,' + chat);" +
				"    setChatTrayOpen(false);" +
				"    if (chat === 'compose') window.__voidscapeFocusKeyboard();" +
				"    if (navigator.vibrate) {" +
				"      try { navigator.vibrate(6); } catch (ignored) {}" +
				"    }" +
				"  });" +
				"});" +
			"const virtualKeys = {};" +
			"const VIRTUAL_KEY_MIN_HOLD_MS = 280;" +
			"const VIRTUAL_KEY_REPEAT_MS = 100;" +
			"const startVirtualKey = function(control, key) {" +
			"  key = key | 0;" +
			"  if (!key || virtualKeys[key]) return;" +
			"  const state = { startedAt: Date.now(), control: control, interval: 0, stopping: false };" +
			"  virtualKeys[key] = state;" +
			"  if (control) control.classList.add('is-held');" +
			"  queue('k,1,' + key);" +
			"  state.interval = setInterval(function() { queue('k,1,' + key); }, VIRTUAL_KEY_REPEAT_MS);" +
			"};" +
			"const stopVirtualKey = function(key) {" +
			"  key = key | 0;" +
			"  const state = virtualKeys[key];" +
			"  if (!state || state.stopping) return;" +
			"  state.stopping = true;" +
			"  const finish = function() {" +
			"    clearInterval(state.interval);" +
			"    queue('k,0,' + key);" +
			"    if (state.control) state.control.classList.remove('is-held');" +
			"    if (virtualKeys[key] === state) delete virtualKeys[key];" +
			"  };" +
			"  const remaining = Math.max(0, VIRTUAL_KEY_MIN_HOLD_MS - (Date.now() - state.startedAt));" +
			"  if (remaining > 0) setTimeout(finish, remaining); else finish();" +
			"};" +
			"const stopAllVirtualKeys = function() {" +
			"  Object.keys(virtualKeys).forEach(function(key) { stopVirtualKey(Number(key)); });" +
			"};" +
			"const pulseVirtualKey = function(key) {" +
			"  key = key | 0;" +
			"  if (!key || virtualKeys[key]) return;" +
			"  startVirtualKey(null, key);" +
			"  setTimeout(function() { stopVirtualKey(key); }, 90);" +
			"};" +
			"document.querySelectorAll('.camera-control-button').forEach(function(control) {" +
			"  const key = Number(control.getAttribute('data-key') || '0');" +
			"  bindControlStart(control, function(e) {" +
			"    startVirtualKey(control, key);" +
			"    try { control.setPointerCapture(e.pointerId); } catch (ignored) {}" +
			"  });" +
			"  const end = function(e) {" +
			"    stopVirtualKey(key);" +
			"    try { control.releasePointerCapture(e.pointerId); } catch (ignored) {}" +
			"  };" +
			"  bindControlEnd(control, end);" +
			"  control.addEventListener('lostpointercapture', function() { stopVirtualKey(key); });" +
			"  control.addEventListener('contextmenu', function(e) { e.preventDefault(); });" +
			"});" +
			"window.addEventListener('blur', stopAllVirtualKeys);" +
			"document.addEventListener('visibilitychange', function() { if (document.hidden) stopAllVirtualKeys(); });" +
				"const queueBackAction = function() {" +
				"  if (window.__voidscapePanelDrawerOpen) {" +
				"    setPanelDrawerOpen(false);" +
				"    return;" +
				"  }" +
				"  if (window.__voidscapeChatTrayOpen) {" +
				"    setChatTrayOpen(false);" +
				"    return;" +
				"  }" +
				"  if (window.__voidscapeKeyboardOpen || window.__voidscapeKeyboardWanted || document.activeElement === document.getElementById('keyboard-capture')) {" +
				"    if (window.__voidscapeWorldMapState && window.__voidscapeWorldMapState.visible && window.__voidscapeWorldMapState.searchFocused) {" +
				"      queue('k,1,27');" +
				"      queue('k,0,27');" +
				"    }" +
				"    if (window.__voidscapeBlurKeyboard) window.__voidscapeBlurKeyboard();" +
				"    return;" +
				"  }" +
				"  queue('b,1');" +
			"};" +
			"let lastEscapeBackAt = 0;" +
			"const queueEscapeBackAction = function() {" +
			"  const now = Date.now();" +
			"  if (now - lastEscapeBackAt < 250) return;" +
			"  lastEscapeBackAt = now;" +
			"  queueBackAction();" +
			"};" +
			"const canvas = document.getElementById('game');" +
			"const inputSurface = canvas ? (canvas.parentElement || canvas) : null;" +
		"if (canvas && inputSurface) {" +
		"  canvas.tabIndex = 0;" +
		"  canvas.style.touchAction = 'none';" +
		"  inputSurface.style.touchAction = 'none';" +
		"  let triggerContextLongPress = function() { return false; };" +
		"  const swallowContextMenu = function(e) {" +
		"    triggerContextLongPress(e);" +
		"    e.preventDefault();" +
		"  };" +
		"  canvas.addEventListener('contextmenu', swallowContextMenu);" +
		"  inputSurface.addEventListener('contextmenu', swallowContextMenu);" +
		"  const getLongPressMs = function() {" +
		"    const value = Number(window.__voidscapeLongPressMillis || 250);" +
		"    if (!Number.isFinite(value)) return 250;" +
		"    return Math.max(50, Math.min(1500, Math.round(value)));" +
		"  };" +
		"  const TOUCH_SLOP = 9;" +
		"  const PINCH_ZOOM_STEP = 24;" +
		"  const DRAG_CAMERA_START = 24;" +
		"  const DRAG_CAMERA_STEP = 22;" +
		"  const TOUCH_SCROLL_STEP = 18;" +
		"  const LONG_PRESS_CANCEL_CSS = 38;" +
			"  const activeTouchPointers = {};" +
			"  let touchState = null;" +
			"  let worldMapTouchState = null;" +
			"  let pinchState = null;" +
			"  let suppressTouchUntil = 0;" +
		"  const toPoint = function(e) {" +
		"    const rect = canvas.getBoundingClientRect();" +
		"    const width = canvas.width || rect.width || 1;" +
		"    const height = canvas.height || rect.height || 1;" +
		"    const x = Math.max(0, Math.min(width - 1, Math.floor((e.clientX - rect.left) * width / rect.width)));" +
		"    const y = Math.max(0, Math.min(height - 1, Math.floor((e.clientY - rect.top) * height / rect.height)));" +
		"    return [x, y];" +
			"  };" +
			"  const button = function(e) { return e.button === 2 ? 2 : 1; };" +
			"  const isTouchPointer = function(e) { return e.pointerType === 'touch' || e.pointerType === 'pen'; };" +
			"  const voidscapePanelSizeClass = function(width) {" +
			"    if (width <= 680) return 0;" +
			"    if (width <= 760) return 1;" +
			"    if (width <= 860) return 2;" +
			"    if (width <= 960) return 3;" +
			"    return 4;" +
			"  };" +
			"  const isSharedCanvasHudControlPoint = function(p) {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    if (!window.__voidscapeInGame) return false;" +
			"    const width = canvas.width || 512;" +
			"    const height = canvas.height || 346;" +
			"    const sizeClass = voidscapePanelSizeClass(width);" +
			"    const compact = sizeClass === 0;" +
			"    if (ui.canvasTopTabsVisible !== false && ui.canvasPanelDockVisible !== true) {" +
			"      const sizes = [52, 54, 56, 58, 60];" +
			"      const tabSize = sizes[sizeClass] || 60;" +
			"      const gap = compact ? 1 : 2;" +
			"      const span = (tabSize * 6) + (gap * 5);" +
			"      const startX = width - span - 3;" +
			"      if (p[0] >= startX && p[0] < startX + span && p[1] >= 3 && p[1] < 3 + tabSize) return true;" +
			"    }" +
			"    if (ui.chatAccessMode !== 'dom-helper' && ui.chatPanelHidden !== true) {" +
			"      const chatTop = height - (compact ? 28 : 32);" +
			"      if (p[1] >= chatTop - 4 && p[1] < height) return true;" +
			"    }" +
			"    return false;" +
			"  };" +
			"  const voidscapeTopTabKeyForPoint = function(p) {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    if (!window.__voidscapeInGame || ui.canvasTopTabsVisible === false || ui.canvasPanelDockVisible === true) return '';" +
			"    const width = canvas.width || 512;" +
			"    const sizeClass = voidscapePanelSizeClass(width);" +
			"    const compact = sizeClass === 0;" +
			"    const sizes = [52, 54, 56, 58, 60];" +
			"    const tabSize = sizes[sizeClass] || 60;" +
			"    const gap = compact ? 1 : 2;" +
			"    const tabCount = 6;" +
			"    const span = (tabSize * tabCount) + (gap * (tabCount - 1));" +
			"    const startX = width - span - 3;" +
			"    if (p[1] < 3 || p[1] >= 3 + tabSize || p[0] < startX || p[0] >= startX + span) return '';" +
			"    const index = Math.floor((p[0] - startX) / (tabSize + gap));" +
			"    const offset = (p[0] - startX) - index * (tabSize + gap);" +
			"    if (index < 0 || index >= tabCount || offset >= tabSize) return '';" +
			"    return ['options', 'friends', 'magic', 'skills', 'map', 'inventory'][index] || '';" +
			"  };" +
			"  const voidscapeChatTabKeyForPoint = function(p) {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    if (!window.__voidscapeInGame || ui.chatAccessMode === 'dom-helper' || ui.chatPanelHidden === true) return '';" +
			"    const width = canvas.width || 512;" +
			"    const height = canvas.height || 346;" +
			"    const gameHeight = Math.max(1, height - 12);" +
			"    const sizeClass = voidscapePanelSizeClass(width);" +
			"    const compact = sizeClass === 0;" +
			"    const accountButtonWidth = compact ? 54 : 62;" +
			"    const bottomButtonGap = compact ? 5 : 7;" +
			"    const bottomReservedWidth = bottomButtonGap + accountButtonWidth + 8;" +
			"    const frameX = 8;" +
			"    const visualWidths = [196, 204, 216, 228, 240];" +
			"    const insets = [14, 15, 16, 18, 18];" +
			"    const readableInset = insets[sizeClass] + (compact ? 4 : 6);" +
			"    const rightPanelBaseWidth = visualWidths[sizeClass] + readableInset * 2;" +
			"    const stableHomeX = width - rightPanelBaseWidth - 18;" +
			"    const accountSafeRight = width - bottomReservedWidth;" +
			"    const maxFrameWidth = Math.min(stableHomeX - 20, accountSafeRight - frameX);" +
			"    const frameWidth = Math.max(300, Math.min(660, maxFrameWidth));" +
			"    const tabCount = 6;" +
			"    const margin = compact ? 4 : 6;" +
			"    const gap = compact ? 3 : 5;" +
			"    const tabHeight = compact ? 32 : 40;" +
			"    const tabTop = gameHeight - (compact ? 28 : 32);" +
			"    if (p[1] < tabTop || p[1] >= tabTop + tabHeight + 6) return '';" +
			"    const avail = frameWidth - (margin * 2) - (gap * (tabCount - 1));" +
			"    const minWidth = 42;" +
			"    const baseWidth = Math.max(minWidth, Math.floor(avail / tabCount));" +
			"    const keys = ['all', 'chat', 'quest', 'global', 'private', 'report'];" +
			"    for (let i = 0; i < tabCount; i++) {" +
			"      const tabWidth = i === tabCount - 1 ? Math.max(minWidth, avail - baseWidth * (tabCount - 1)) : baseWidth;" +
			"      const tabX = frameX + margin + i * (baseWidth + gap);" +
			"      if (p[0] >= tabX && p[0] < tabX + tabWidth) return keys[i];" +
			"    }" +
			"    return '';" +
			"  };" +
			"  const voidscapeSharedPanelKeepOpenPoint = function(p) {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    const tabId = ui.showUiTab | 0;" +
			"    if (!window.__voidscapeInGame || tabId === 0) return false;" +
			"    if (voidscapeTopTabKeyForPoint(p)) return true;" +
			"    const sideKey = String(ui.mobileSidePanelKey || '');" +
			"    const sideActive = !!window.__voidscapeMobileProfile && !!sideKey" +
			"      && ((tabId === 1 && sideKey === 'inventory') || (tabId === 4 && (sideKey === 'magic' || sideKey === 'prayer')));" +
			"    const width = canvas.width || 512;" +
			"    const height = canvas.height || 346;" +
			"    const gameHeight = Math.max(1, height - 12);" +
			"    const sizeClass = voidscapePanelSizeClass(width);" +
			"    const compact = sizeClass === 0;" +
			"    const sizes = [52, 54, 56, 58, 60];" +
			"    const tabSize = sizes[sizeClass] || 60;" +
			"    const gap = compact ? 1 : 2;" +
			"    const tabIds = [6, 5, 4, 3, 2, 1];" +
			"    const index = tabIds.indexOf(tabId);" +
			"    if (index < 0) return false;" +
			"    const span = (tabSize * tabIds.length) + (gap * (tabIds.length - 1));" +
			"    const startX = width - span - 3;" +
			"    const iconX = startX + index * (tabSize + gap);" +
			"    const defaultPanelY = 3 + tabSize + (compact ? 1 : 3);" +
			"    let panelY = defaultPanelY;" +
			"    let panelX;" +
			"    let panelW;" +
			"    let panelH;" +
			"    if (tabId === 2) {" +
			"      const minimapW = compact ? 156 : 203;" +
			"      const minimapH = compact ? 152 : 198;" +
			"      panelX = Math.max(0, width - minimapW - 13) - 10;" +
			"      panelW = minimapW + 20;" +
			"      panelH = minimapH + 52;" +
			"    } else {" +
			"      const visualWidths = [196, 204, 216, 228, 240];" +
			"      const glassPad = compact ? 7 : 8;" +
			"      const inventoryW = 49 * 5 + glassPad * 2;" +
			"      const touchMagicW = Math.max(visualWidths[sizeClass], Math.min(compact ? 260 : 292, width - 32));" +
			"      const sideMagicW = Math.max(visualWidths[sizeClass], Math.min(244, width - 32));" +
			"      const visualW = tabId === 1 ? Math.max(visualWidths[sizeClass], inventoryW) : (tabId === 4 ? (sideActive ? sideMagicW : touchMagicW) : visualWidths[sizeClass]);" +
			"      panelX = Math.max(0, Math.min(iconX, width - visualW - 3));" +
			"      if (sideActive) panelX = Math.max(0, panelX - 76);" +
			"      panelW = visualW;" +
			"      if (tabId === 4) panelH = sideActive ? ((compact ? 25 : 27) + 24 + 80 + 74 + 20) : ((compact ? 25 : 27) + 24 + (gameHeight < 420 ? 126 : 190) + (gameHeight < 420 ? 58 : 82) + 20);" +
			"      else if (tabId === 6) panelH = [328, 334, 344, 356, 356][sizeClass] || 356;" +
			"      else if (tabId === 1) panelH = sideActive ? ((compact ? 25 : 27) + (6 * 34) + 24 + 14) : 366;" +
			"      else if (tabId === 3) panelH = Math.max(180, gameHeight - panelY - 8);" +
			"      else panelH = [238, 246, 254, 264, 264][sizeClass] || 264;" +
			"      if (sideActive) {" +
			"        const sidePanelOffset = (sideKey === 'inventory' ? -92 : (sideKey === 'prayer' ? 52 : 4)) + 24;" +
			"        const preferredY = Math.round(gameHeight / 2 + sidePanelOffset - panelH / 2);" +
			"        const chatTop = gameHeight - (compact ? 28 : 32);" +
			"        const bottomMax = chatTop - panelH - 6;" +
			"        panelY = bottomMax < defaultPanelY ? defaultPanelY : Math.max(defaultPanelY, Math.min(preferredY, bottomMax));" +
			"      }" +
			"    }" +
			"    if (sideActive) {" +
			"      const sideButtonOffset = (sideKey === 'inventory' ? -92 : (sideKey === 'prayer' ? 52 : 4)) + 24;" +
			"      const centerY = Math.round(gameHeight / 2 + sideButtonOffset);" +
			"      const overPanel = p[0] >= panelX && p[0] < panelX + panelW && p[1] >= panelY && p[1] < panelY + panelH;" +
			"      const overConnector = p[0] >= panelX + panelW && p[0] < width && p[1] >= centerY - 30 && p[1] < centerY + 30;" +
			"      const overTopIcon = p[0] >= iconX && p[0] < iconX + tabSize && p[1] >= 3 && p[1] < 3 + tabSize;" +
			"      return overPanel || overConnector || overTopIcon;" +
			"    }" +
			"    const keepLeft = Math.min(iconX, panelX);" +
			"    const keepRight = Math.max(iconX + tabSize, panelX + panelW);" +
			"    const keepBottom = Math.min(gameHeight, panelY + panelH);" +
			"    return p[0] >= keepLeft && p[0] < keepRight && p[1] >= 3 && p[1] < keepBottom;" +
			"  };" +
			"  const shouldCloseSharedPanelForPoint = function(p) {" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    return window.__voidscapeMobileProfile && (ui.showUiTab | 0) !== 0 && !voidscapeSharedPanelKeepOpenPoint(p);" +
			"  };" +
			"  const queuePointer = function(action, point, button) {" +
			"    queue('p,' + action + ',' + point[0] + ',' + point[1] + ',' + button);" +
			"    if (action === 1 && button !== 2) {" +
			"      const panel = voidscapeTopTabKeyForPoint(point);" +
			"      if (panel) queue('u,' + panel);" +
			"      const chat = voidscapeChatTabKeyForPoint(point);" +
			"      if (!panel && !chat && shouldCloseSharedPanelForPoint(point)) queue('u,hud');" +
			"      if (chat) queue('c,' + chat);" +
			"    }" +
			"  };" +
			"  const queueWorldMapTouch = function(phase, point) {" +
			"    queue('g,' + phase + ',' + point[0] + ',' + point[1]);" +
			"  };" +
			"  const queueScroll = function(amount) {" +
			"    amount = amount | 0;" +
			"    if (!amount) return;" +
			"    queue('w,' + amount);" +
			"    const history = window.__voidscapeScrollHistory || [];" +
			"    const ui = window.__voidscapeUiState || {};" +
			"    history.push({" +
			"      amount: amount," +
			"      scrollableUi: !!window.__voidscapeScrollableUiActive," +
			"      messageScroll: !!window.__voidscapeMessageScrollActive," +
			"      worldMap: !!window.__voidscapeWorldMapTouchActive," +
			"      showUiTab: ui.showUiTab | 0," +
			"      messageTab: String(ui.messageTab || '')," +
			"      at: new Date().toISOString()" +
			"    });" +
			"    if (history.length > 32) history.splice(0, history.length - 32);" +
			"    window.__voidscapeScrollHistory = history;" +
			"  };" +
			"  const queueMenuTouch = function(point) {" +
			"    queue('m,' + point[0] + ',' + point[1]);" +
			"  };" +
			"  const isInsideWorldMapWindow = function(point) {" +
			"    const state = window.__voidscapeWorldMapState;" +
			"    const win = state && state.window;" +
			"    if (!state || !state.visible || !win) return true;" +
			"    const x = win.x | 0;" +
			"    const y = win.y | 0;" +
			"    const width = win.width | 0;" +
			"    const height = win.height | 0;" +
			"    if (width <= 0 || height <= 0) return true;" +
			"    return point[0] >= x && point[0] < x + width && point[1] >= y && point[1] < y + height;" +
			"  };" +
			"  const installBackTrap = function() {" +
		"    if (!window.__voidscapeMobileProfile || window.__voidscapeBackTrapInstalled || !window.history || !history.pushState) return;" +
		"    window.__voidscapeBackTrapInstalled = true;" +
		"    const state = { voidscapeBackTrap: true };" +
		"    try {" +
		"      const current = history.state && typeof history.state === 'object' ? history.state : {};" +
		"      history.replaceState(Object.assign({}, current, { voidscapeGame: true }), '', location.href);" +
		"      history.pushState(state, '', location.href);" +
		"    } catch (ignored) {" +
		"      return;" +
		"    }" +
		"    window.addEventListener('popstate', function() {" +
		"      if (!window.__voidscapeMobileProfile) return;" +
		"      queueBackAction();" +
		"      try { history.pushState(state, '', location.href); } catch (ignored) {}" +
		"    });" +
		"  };" +
		"  installBackTrap();" +
		"  const clearTouchTimer = function() {" +
		"    if (touchState && touchState.timer) {" +
		"      clearTimeout(touchState.timer);" +
		"      touchState.timer = 0;" +
		"    }" +
		"  };" +
		"  const activeTouchValues = function() {" +
		"    return Object.keys(activeTouchPointers).map(function(key) { return activeTouchPointers[key]; });" +
		"  };" +
		"  const touchDistance = function(a, b) {" +
		"    const dx = a.clientX - b.clientX;" +
		"    const dy = a.clientY - b.clientY;" +
		"    return Math.sqrt(dx * dx + dy * dy);" +
		"  };" +
		"  const trackTouchPointer = function(e) {" +
		"    if (!isTouchPointer(e)) return;" +
		"    activeTouchPointers[e.pointerId] = { clientX: e.clientX, clientY: e.clientY };" +
		"  };" +
		"  const forgetTouchPointer = function(e) {" +
		"    if (!isTouchPointer(e)) return;" +
		"    delete activeTouchPointers[e.pointerId];" +
		"    if (activeTouchValues().length < 2) pinchState = null;" +
		"  };" +
		"  const updatePinchZoom = function() {" +
		"    const points = activeTouchValues();" +
		"    if (points.length < 2) {" +
		"      pinchState = null;" +
		"      return false;" +
		"    }" +
		"    const distance = touchDistance(points[0], points[1]);" +
		"    if (!pinchState) {" +
		"      pinchState = { lastDistance: distance };" +
		"      return true;" +
			"    }" +
			"    const delta = distance - pinchState.lastDistance;" +
			"    if (Math.abs(delta) >= PINCH_ZOOM_STEP) {" +
			"      if (window.__voidscapeWorldMapTouchActive) queueScroll(delta > 0 ? 1 : -1);" +
			"      else pulseVirtualKey(delta > 0 ? 38 : 40);" +
			"      pinchState.lastDistance = distance;" +
			"    }" +
		"    return true;" +
		"  };" +
		"  const beginPinchIfNeeded = function(e, p) {" +
		"    if (!isTouchPointer(e) || window.__voidscapeTopMenuVisible) return false;" +
		"    trackTouchPointer(e);" +
			"    if (activeTouchValues().length < 2) return false;" +
			"    clearTouchTimer();" +
			"    if (worldMapTouchState) {" +
			"      queueWorldMapTouch(3, p);" +
			"      worldMapTouchState = null;" +
			"    }" +
			"    if (touchState) {" +
			"      queuePointer(2, [touchState.x, touchState.y], 0);" +
			"      touchState = null;" +
		"    }" +
		"    if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
		"    updatePinchZoom();" +
		"    try { inputSurface.setPointerCapture(e.pointerId); } catch (ignored) {}" +
		"    return true;" +
			"  };" +
		"  const triggerTouchLongPress = function(reason) {" +
		"    if (!touchState || touchState.longPressed || touchState.armedSecondary || touchState.menuTouch) return false;" +
		"    if (window.__voidscapeTopMenuVisible) return false;" +
		"    const holdDrift = Math.max(Math.abs(touchState.clientX - touchState.startClientX), Math.abs(touchState.clientY - touchState.startClientY));" +
		"    if (holdDrift > LONG_PRESS_CANCEL_CSS) return false;" +
		"    clearTouchTimer();" +
		"    touchState.longPressed = true;" +
		"    touchState.menuTouch = true;" +
		"    queuePointer(3, [touchState.x, touchState.y], 2);" +
		"    if (navigator.vibrate) {" +
		"      try { navigator.vibrate(10); } catch (ignored) {}" +
		"    }" +
		"    return true;" +
		"  };" +
		"  triggerContextLongPress = function(e) {" +
		"    if (!touchState || touchState.longPressed || touchState.armedSecondary || touchState.menuTouch) return false;" +
		"    if (Number.isFinite(e.clientX) && Number.isFinite(e.clientY)) {" +
		"      const p = toPoint(e);" +
		"      touchState.x = p[0];" +
		"      touchState.y = p[1];" +
		"      touchState.clientX = e.clientX;" +
		"      touchState.clientY = e.clientY;" +
		"    }" +
		"    return triggerTouchLongPress('contextmenu');" +
		"  };" +
		"  const startTouchLongPress = function(e, p) {" +
		"    clearTouchTimer();" +
		"    touchState = {" +
		"      pointerId: e.pointerId," +
		"      startedAt: Date.now()," +
		"      startX: p[0]," +
			"      startY: p[1]," +
			"      startClientX: e.clientX," +
			"      startClientY: e.clientY," +
			"      x: p[0]," +
			"      y: p[1]," +
			"      clientX: e.clientX," +
			"      clientY: e.clientY," +
			"      armedSecondary: false," +
			"      gestureAxis: ''," +
			"      lastGestureX: p[0]," +
			"      lastGestureY: p[1]," +
			"      lastScrollY: p[1]," +
			"      scrollRemainder: 0," +
			"      gestureConsumed: false," +
			"      menuTouch: false," +
			"      moved: false," +
			"      longPressed: false," +
		"      timer: 0" +
		"    };" +
		"    touchState.timer = setTimeout(function() {" +
		"      if (!touchState || touchState.pointerId !== e.pointerId || touchState.longPressed) return;" +
		"      triggerTouchLongPress('timer');" +
			"    }, getLongPressMs());" +
			"  };" +
			"  const startHudTouch = function(e, p) {" +
			"    startTouchLongPress(e, p);" +
			"    clearTouchTimer();" +
			"  };" +
			"  const startArmedSecondaryTap = function(e, p) {" +
			"    clearTouchTimer();" +
			"    touchState = {" +
			"      pointerId: e.pointerId," +
			"      startX: p[0]," +
			"      startY: p[1]," +
			"      startClientX: e.clientX," +
			"      startClientY: e.clientY," +
			"      x: p[0]," +
			"      y: p[1]," +
			"      clientX: e.clientX," +
			"      clientY: e.clientY," +
			"      armedSecondary: true," +
			"      gestureAxis: ''," +
			"      lastGestureX: p[0]," +
			"      lastGestureY: p[1]," +
			"      lastScrollY: p[1]," +
			"      scrollRemainder: 0," +
			"      gestureConsumed: false," +
			"      menuTouch: false," +
			"      moved: false," +
			"      longPressed: false," +
			"      timer: 0" +
			"    };" +
			"  };" +
			"  const startMenuTouch = function(e, p) {" +
			"    clearTouchTimer();" +
			"    touchState = {" +
			"      pointerId: e.pointerId," +
			"      startX: p[0]," +
			"      startY: p[1]," +
			"      startClientX: e.clientX," +
			"      startClientY: e.clientY," +
			"      x: p[0]," +
			"      y: p[1]," +
			"      clientX: e.clientX," +
			"      clientY: e.clientY," +
			"      armedSecondary: false," +
			"      gestureAxis: ''," +
			"      lastGestureX: p[0]," +
			"      lastGestureY: p[1]," +
			"      lastScrollY: p[1]," +
			"      scrollRemainder: 0," +
			"      gestureConsumed: false," +
			"      menuTouch: true," +
			"      moved: false," +
			"      longPressed: false," +
				"      timer: 0" +
				"    };" +
				"  };" +
				"  const startWorldMapTouch = function(e, p) {" +
				"    clearTouchTimer();" +
				"    touchState = null;" +
				"    if (worldMapTouchState) queueWorldMapTouch(3, [worldMapTouchState.x, worldMapTouchState.y]);" +
				"    worldMapTouchState = { pointerId: e.pointerId, x: p[0], y: p[1] };" +
				"    queueWorldMapTouch(0, p);" +
				"  };" +
				"  const updateWorldMapTouch = function(e, p) {" +
				"    if (!worldMapTouchState || worldMapTouchState.pointerId !== e.pointerId) return false;" +
				"    worldMapTouchState.x = p[0];" +
				"    worldMapTouchState.y = p[1];" +
				"    queueWorldMapTouch(1, p);" +
				"    return true;" +
				"  };" +
				"  const finishWorldMapTouch = function(e, p) {" +
				"    if (!worldMapTouchState || worldMapTouchState.pointerId !== e.pointerId) return false;" +
				"    worldMapTouchState.x = p[0];" +
				"    worldMapTouchState.y = p[1];" +
				"    queueWorldMapTouch(e.type === 'pointercancel' ? 3 : 2, p);" +
				"    worldMapTouchState = null;" +
				"    return true;" +
				"  };" +
				"  const updateTouchState = function(e, p) {" +
		"    if (!touchState || touchState.pointerId !== e.pointerId) return false;" +
		"    touchState.x = p[0];" +
		"    touchState.y = p[1];" +
		"    touchState.clientX = e.clientX;" +
		"    touchState.clientY = e.clientY;" +
		"    const drift = Math.max(Math.abs(p[0] - touchState.startX), Math.abs(p[1] - touchState.startY));" +
		"    const clientDrift = Math.max(Math.abs(touchState.clientX - touchState.startClientX), Math.abs(touchState.clientY - touchState.startClientY));" +
		"    if (drift > TOUCH_SLOP) {" +
		"      touchState.moved = true;" +
		"    }" +
		"    if (clientDrift > LONG_PRESS_CANCEL_CSS) {" +
		"      clearTouchTimer();" +
		"    }" +
		"    return true;" +
		"  };" +
		"  const shouldTouchScroll = function(p) {" +
		"    if (window.__voidscapeWorldMapTouchActive) return false;" +
		"    if (window.__voidscapeScrollableUiActive) return true;" +
		"    if (!window.__voidscapeMessageScrollActive || !touchState) return false;" +
		"    const canvasHeight = canvas.height || 1;" +
		"    return canvasHeight - Math.max(touchState.startY, p[1]) <= 130;" +
		"  };" +
		"  const handleTouchScrollGesture = function(e, p) {" +
		"    if (!touchState || touchState.pointerId !== e.pointerId || !touchState.moved || touchState.longPressed || touchState.armedSecondary) return false;" +
		"    if (!window.__voidscapeInGame || window.__voidscapeKeyboardWanted || window.__voidscapeTopMenuVisible || activeTouchValues().length > 1) return false;" +
		"    if (!shouldTouchScroll(p)) return false;" +
		"    const totalDx = p[0] - touchState.startX;" +
		"    const totalDy = p[1] - touchState.startY;" +
		"    const clientDrift = Math.max(Math.abs(touchState.clientX - touchState.startClientX), Math.abs(touchState.clientY - touchState.startClientY));" +
		"    if (touchState.timer && clientDrift <= LONG_PRESS_CANCEL_CSS) return false;" +
		"    if (!touchState.gestureAxis) {" +
		"      const absX = Math.abs(totalDx);" +
		"      const absY = Math.abs(totalDy);" +
		"      if (absX < DRAG_CAMERA_START && absY < DRAG_CAMERA_START) return false;" +
		"      if (absY < absX) return false;" +
		"      touchState.gestureAxis = 'scroll';" +
		"      touchState.lastScrollY = touchState.startY;" +
		"      touchState.scrollRemainder = 0;" +
		"      touchState.gestureConsumed = true;" +
		"    }" +
		"    if (touchState.gestureAxis !== 'scroll') return false;" +
		"    const dy = (touchState.lastScrollY - p[1]) + touchState.scrollRemainder;" +
		"    if (Math.abs(dy) >= TOUCH_SCROLL_STEP) {" +
		"      const ticks = dy > 0 ? Math.floor(dy / TOUCH_SCROLL_STEP) : Math.ceil(dy / TOUCH_SCROLL_STEP);" +
		"      queueScroll(ticks);" +
		"      touchState.scrollRemainder = dy - ticks * TOUCH_SCROLL_STEP;" +
		"      touchState.lastScrollY = p[1];" +
		"    }" +
		"    return true;" +
		"  };" +
		"  const handleTouchCameraGesture = function(e, p) {" +
		"    if (!touchState || touchState.pointerId !== e.pointerId || !touchState.moved || touchState.longPressed || touchState.armedSecondary) return false;" +
		"    if (!window.__voidscapeInGame || window.__voidscapeKeyboardWanted || window.__voidscapeTopMenuVisible || window.__voidscapeScrollableUiActive || window.__voidscapeWorldMapTouchActive || activeTouchValues().length > 1) return false;" +
		"    const totalDx = p[0] - touchState.startX;" +
		"    const totalDy = p[1] - touchState.startY;" +
		"    const clientDrift = Math.max(Math.abs(touchState.clientX - touchState.startClientX), Math.abs(touchState.clientY - touchState.startClientY));" +
		"    if (touchState.timer && clientDrift <= LONG_PRESS_CANCEL_CSS) return false;" +
		"    if (!touchState.gestureAxis) {" +
		"      const absX = Math.abs(totalDx);" +
		"      const absY = Math.abs(totalDy);" +
		"      if (absX < DRAG_CAMERA_START && absY < DRAG_CAMERA_START) return false;" +
		"      touchState.gestureAxis = absX >= absY ? 'x' : 'y';" +
		"      touchState.lastGestureX = touchState.startX;" +
		"      touchState.lastGestureY = touchState.startY;" +
		"      touchState.gestureConsumed = true;" +
		"    }" +
		"    if (touchState.gestureAxis === 'x') {" +
		"      const dx = p[0] - touchState.lastGestureX;" +
		"      if (Math.abs(dx) >= DRAG_CAMERA_STEP) {" +
		"        pulseVirtualKey(dx < 0 ? 37 : 39);" +
		"        touchState.lastGestureX = p[0];" +
		"      }" +
		"      return true;" +
		"    }" +
		"    const dy = p[1] - touchState.lastGestureY;" +
		"    if (Math.abs(dy) >= DRAG_CAMERA_STEP) {" +
		"      pulseVirtualKey(dy < 0 ? 38 : 40);" +
		"      touchState.lastGestureY = p[1];" +
		"    }" +
		"    return true;" +
		"  };" +
				"  const maybeFocusKeyboard = function() {" +
				"    if (window.__voidscapeKeyboardWanted) window.__voidscapeFocusKeyboard();" +
				"  };" +
				"  inputSurface.addEventListener('pointerdown', function(e) {" +
				"    try {" +
				"      const AudioContextClass = window.AudioContext || window.webkitAudioContext;" +
				"      if (AudioContextClass) {" +
				"        const audioContext = window.__voidscapeAudioContext || (window.__voidscapeAudioContext = new AudioContextClass());" +
				"        if (audioContext.state === 'suspended') audioContext.resume().catch(function() {});" +
				"        const source = audioContext.createBufferSource();" +
				"        source.buffer = audioContext.createBuffer(1, 1, 22050);" +
				"        source.connect(audioContext.destination); source.start(0);" +
				"      }" +
				"    } catch (ignored) {}" +
				"    setPanelDrawerOpen(false);" +
				"    setChatTrayOpen(false);" +
			"    if (!window.__voidscapeKeyboardWanted) canvas.focus({ preventScroll: true });" +
			"    const p = toPoint(e);" +
			"    if (isTouchPointer(e) && Date.now() < suppressTouchUntil) {" +
			"      e.preventDefault();" +
			"      return;" +
			"    }" +
				"    if (beginPinchIfNeeded(e, p)) {" +
				"      e.preventDefault();" +
				"      return;" +
				"    }" +
					"    if (isTouchPointer(e) && window.__voidscapeWorldMapTouchActive) {" +
					"      if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
					"      if (isInsideWorldMapWindow(p)) {" +
					"        startWorldMapTouch(e, p);" +
					"        try { inputSurface.setPointerCapture(e.pointerId); } catch (ignored) {}" +
					"        e.preventDefault();" +
					"        return;" +
					"      }" +
					"    }" +
				"    if (isTouchPointer(e) && window.__voidscapeTopMenuVisible) {" +
			"      if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
			"      startMenuTouch(e, p);" +
			"    } else if (isTouchPointer(e) && isSharedCanvasHudControlPoint(p)) {" +
			"      if (window.__voidscapeNextTapSecondary) setActionArmed(false);" +
			"      startHudTouch(e, p);" +
			"      queuePointer(0, p, 0);" +
			"    } else if (window.__voidscapeNextTapSecondary) {" +
			"      startArmedSecondaryTap(e, p);" +
			"      queuePointer(0, p, 0);" +
			"    } else if (isTouchPointer(e)) {" +
			"      startTouchLongPress(e, p);" +
		"      queuePointer(0, p, 0);" +
		"    } else {" +
		"      queuePointer(0, p, 0);" +
		"      queuePointer(1, p, button(e));" +
		"    }" +
		"    try { inputSurface.setPointerCapture(e.pointerId); } catch (ignored) {}" +
		"    e.preventDefault();" +
			"  });" +
				"  inputSurface.addEventListener('pointermove', function(e) {" +
				"    const p = toPoint(e);" +
				"    if (isTouchPointer(e) && Date.now() < suppressTouchUntil) {" +
				"      e.preventDefault();" +
				"      return;" +
				"    }" +
			"    if (isTouchPointer(e) && activeTouchPointers[e.pointerId]) {" +
			"      trackTouchPointer(e);" +
			"      if (updatePinchZoom()) {" +
			"        e.preventDefault();" +
				"        return;" +
				"      }" +
				"    }" +
				"    if (isTouchPointer(e) && updateWorldMapTouch(e, p)) {" +
				"      e.preventDefault();" +
				"      return;" +
				"    }" +
				"    if (touchState && touchState.pointerId === e.pointerId && updateTouchState(e, p)) {" +
				"      if (touchState.menuTouch) {" +
			"        e.preventDefault();" +
			"        return;" +
			"      }" +
			"      if (handleTouchScrollGesture(e, p)) {" +
			"        e.preventDefault();" +
			"        return;" +
			"      }" +
			"      if (handleTouchCameraGesture(e, p)) {" +
			"        e.preventDefault();" +
			"        return;" +
			"      }" +
			"      queuePointer(0, p, touchState.armedSecondary ? 0 : (touchState.moved ? 1 : 0));" +
			"    } else {" +
		"      const held = (e.buttons & 2) ? 2 : ((e.buttons & 1) ? 1 : 0);" +
		"      if (held === 0 && shouldCloseSharedPanelForPoint(p)) {" +
		"        queue('u,hud');" +
		"      }" +
		"      queuePointer(0, p, held);" +
		"    }" +
		"    e.preventDefault();" +
			"  });" +
			"  const pointerEnd = function(e) {" +
				"    const p = toPoint(e);" +
			"    if (isTouchPointer(e) && pinchState) {" +
			"      suppressTouchUntil = Date.now() + 350;" +
			"      forgetTouchPointer(e);" +
			"      e.preventDefault();" +
			"      return;" +
			"    }" +
			"    forgetTouchPointer(e);" +
				"    if (isTouchPointer(e) && Date.now() < suppressTouchUntil) {" +
				"      e.preventDefault();" +
				"      return;" +
				"    }" +
				"    if (isTouchPointer(e) && finishWorldMapTouch(e, p)) {" +
				"      try { inputSurface.releasePointerCapture(e.pointerId); } catch (ignored) {}" +
				"      e.preventDefault();" +
				"      return;" +
				"    }" +
			    "    if (touchState && touchState.pointerId === e.pointerId) {" +
				"      updateTouchState(e, p);" +
			"      if (isTouchPointer(e) && e.type === 'pointercancel' && !touchState.longPressed && !touchState.armedSecondary && !touchState.menuTouch) {" +
			"        const age = Date.now() - (touchState.startedAt || Date.now());" +
			"        if (age >= getLongPressMs() - 80) triggerTouchLongPress('pointercancel');" +
			"      }" +
			"      clearTouchTimer();" +
			"      if (touchState.menuTouch) {" +
			"        if (touchState.longPressed) {" +
			"          queuePointer(4, p, 0);" +
			"        } else if (e.type !== 'pointercancel') {" +
			"          queueMenuTouch(p);" +
			"        }" +
			"        touchState = null;" +
			"      } else if (touchState.armedSecondary) {" +
			"        const useSecondaryTap = !touchState.moved && e.type !== 'pointercancel';" +
			"        setActionArmed(false);" +
			"        if (useSecondaryTap) {" +
			"          queuePointer(1, p, 2);" +
			"          queuePointer(2, p, 2);" +
			"        } else {" +
			"          queuePointer(2, p, 0);" +
			"        }" +
			"        touchState = null;" +
			"      } else {" +
				"      const releasePoint = touchState.gestureAxis === 'scroll' ? [touchState.startX, touchState.startY] : p;" +
				"      const releaseDrift = Math.max(Math.abs(p[0] - touchState.startX), Math.abs(p[1] - touchState.startY));" +
				"      const movedScrollableUi = !!window.__voidscapeScrollableUiActive && touchState.gestureAxis === 'scroll';" +
				"      if (!touchState.longPressed && !movedScrollableUi && (!touchState.moved || (!touchState.gestureConsumed && releaseDrift < DRAG_CAMERA_START)) && e.type !== 'pointercancel') {" +
			"        queuePointer(1, releasePoint, 1);" +
			"      }" +
			"      queuePointer(2, releasePoint, touchState.longPressed ? 2 : 1);" +
			"      touchState = null;" +
			"      }" +
			"    } else {" +
		"      queuePointer(2, p, button(e));" +
		"    }" +
		"    try { inputSurface.releasePointerCapture(e.pointerId); } catch (ignored) {}" +
		"    if (e.type !== 'pointercancel') maybeFocusKeyboard();" +
		"    e.preventDefault();" +
		"  };" +
		"  inputSurface.addEventListener('pointerup', pointerEnd);" +
		"  inputSurface.addEventListener('pointercancel', pointerEnd);" +
		"  inputSurface.addEventListener('wheel', function(e) {" +
		"    const dy = e.deltaY || 0;" +
		"    if (!dy || !window.__voidscapeInGame) return;" +
		"    queueScroll(dy > 0 ? 1 : -1);" +
		"    e.preventDefault();" +
		"  }, { passive: false });" +
		"}" +
		"const keyValue = function(e) {" +
		"  if (e.key === 'Backspace') return 8;" +
		"  if (e.key === 'Enter') return 10;" +
		"  if (e.key === 'Tab') return 9;" +
		"  if (e.key === 'Escape') return 27;" +
		"  if (e.key === 'ArrowLeft') return 37;" +
		"  if (e.key === 'ArrowRight') return 39;" +
		"  if (e.key === 'ArrowUp') return 38;" +
		"  if (e.key === 'ArrowDown') return 40;" +
		"  if (e.key === 'PageUp') return 33;" +
		"  if (e.key === 'PageDown') return 34;" +
		"  return 0;" +
		"};" +
		"const shouldPrevent = function(key) {" +
		"  return key === 8 || key === 9 || key === 10 || key === 27 || key === 32 || key === 33 || key === 34 || key === 37 || key === 38 || key === 39 || key === 40;" +
		"};" +
		"const suppressedKeypressUntil = Object.create(null);" +
		"const rememberSuppressedKeypress = function(key) {" +
		"  if (shouldPrevent(key)) suppressedKeypressUntil[key] = Date.now() + 1000;" +
		"};" +
		"const shouldSuppressLegacyKeypress = function(e) {" +
		"  const key = e.charCode || e.which || e.keyCode || 0;" +
		"  if (!key || !shouldPrevent(key)) return false;" +
		"  const until = suppressedKeypressUntil[key] || 0;" +
		"  if (until && until > Date.now()) {" +
		"    delete suppressedKeypressUntil[key];" +
		"    return true;" +
		"  }" +
		"  return false;" +
		"};" +
			"const isEscapeKeyEvent = function(e) {" +
			"  return e && (e.key === 'Escape' || e.key === 'Esc' || e.code === 'Escape' || e.keyCode === 27 || e.which === 27);" +
			"};" +
			"const isWorldMapSearchFocused = function() {" +
			"  const state = window.__voidscapeWorldMapState;" +
			"  return !!(state && state.visible && state.searchFocused);" +
			"};" +
			"let escapeWorldMapSearchKeyUpPending = false;" +
			"const handleMobileEscapeBack = function(e) {" +
			"  if (!window.__voidscapeMobileProfile || !isEscapeKeyEvent(e)) return false;" +
			"  if (isWorldMapSearchFocused()) {" +
			"    if (e.type === 'keydown') escapeWorldMapSearchKeyUpPending = true;" +
			"    return false;" +
			"  }" +
			"  if (e.type === 'keyup' && escapeWorldMapSearchKeyUpPending) return false;" +
			"  queueEscapeBackAction();" +
			"  e.preventDefault();" +
			"  e.stopPropagation();" +
		"  return true;" +
		"};" +
		"document.addEventListener('keydown', function(e) { handleMobileEscapeBack(e); }, true);" +
		"document.addEventListener('keyup', function(e) { handleMobileEscapeBack(e); }, true);" +
		"document.addEventListener('keydown', function(e) {" +
		"  const key = keyValue(e);" +
		"  if (!key) return;" +
			"  if (key === 27 && window.__voidscapeMobileProfile && !isWorldMapSearchFocused()) {" +
			"    queueEscapeBackAction();" +
			"    e.preventDefault();" +
			"    return;" +
		"  }" +
		"  rememberSuppressedKeypress(key);" +
		"  const capture = document.getElementById('keyboard-capture');" +
		"  if (document.activeElement === capture && (key === 8 || key === 10)) {" +
		"    markCaptureKeydown(key);" +
		"    queue('k,1,' + key);" +
		"    if (shouldPrevent(key)) e.preventDefault();" +
		"    return;" +
		"  }" +
		"  queue('k,1,' + key);" +
		"  if (shouldPrevent(key)) e.preventDefault();" +
		"});" +
		"document.addEventListener('keypress', function(e) {" +
		"  if (e.ctrlKey || e.metaKey || e.altKey) return;" +
		"  const capture = document.getElementById('keyboard-capture');" +
		"  if (document.activeElement === capture) return;" +
		"  if (shouldSuppressLegacyKeypress(e)) {" +
		"    e.preventDefault();" +
		"    return;" +
		"  }" +
		"  const controlKey = keyValue(e);" +
		"  if (controlKey || (e.key && e.key.length !== 1)) {" +
		"    if (shouldPrevent(controlKey || e.which || e.keyCode || 0)) e.preventDefault();" +
		"    return;" +
		"  }" +
		"  const key = e.charCode || (e.key && e.key.length === 1 ? e.key.charCodeAt(0) : 0) || 0;" +
		"  if (!key) return;" +
		"  queue('t,' + key);" +
		"  if (key === 32) e.preventDefault();" +
		"});" +
		"document.addEventListener('keyup', function(e) {" +
		"  const key = keyValue(e);" +
		"  if (!key) return;" +
			"  if (key === 27 && window.__voidscapeMobileProfile && escapeWorldMapSearchKeyUpPending) {" +
			"    escapeWorldMapSearchKeyUpPending = false;" +
			"    queue('k,0,' + key);" +
			"    e.preventDefault();" +
			"    return;" +
			"  }" +
			"  if (key === 27 && window.__voidscapeMobileProfile) {" +
			"    queueEscapeBackAction();" +
			"    e.preventDefault();" +
			"    return;" +
		"  }" +
		"  queue('k,0,' + key);" +
		"  if (shouldPrevent(key)) e.preventDefault();" +
		"});")
	private static native void installInputHandlers();

	@JSBody(params = {}, script =
		"return !!window.__voidscapeMobileProfile;")
	private static native boolean isMobileProfile();

	@JSBody(params = {}, script =
		"return !!window.__voidscapeMobileProfile && " +
		"(!!window.__voidscapeKeyboardOpen || !!window.__voidscapeKeyboardWanted || " +
		"(document.body && document.body.classList.contains('keyboard-open')) || " +
		"Date.now() < Number(window.__voidscapeKeyboardClosingUntil || 0));")
	private static native boolean isKeyboardResizeFrozen();

	@JSBody(params = { "scrollableUi", "messageScroll", "worldMap", "topMenu", "longPressMillis" }, script =
		"window.__voidscapeScrollableUiActive = !!scrollableUi;" +
		"window.__voidscapeMessageScrollActive = !!messageScroll;" +
		"window.__voidscapeWorldMapTouchActive = !!worldMap;" +
		"window.__voidscapeTopMenuVisible = !!topMenu;" +
		"window.__voidscapeLongPressMillis = Math.max(50, Math.min(1500, longPressMillis | 0));")
	private static native void updateMobileInputHints(boolean scrollableUi, boolean messageScroll, boolean worldMap, boolean topMenu,
		int longPressMillis);

	@JSBody(params = {
		"hasLocalPlayer",
		"localX",
		"localZ",
		"midRegionBaseX",
		"midRegionBaseZ",
		"worldX",
		"worldY",
		"currentX",
		"currentZ",
		"tileX",
		"tileZ",
		"gameWidth",
		"gameHeight",
		"cameraRotation",
		"cameraAngle",
		"cameraZoom",
		"lastZoom",
		"loginScreenNumber",
		"viewMode",
		"mouseX",
		"mouseY",
		"mouseButtonClick",
		"mouseButtonDown",
		"lastMouseDown",
		"chatMessageInput",
		"inputTextCurrent"
	}, script =
		"const state = {" +
		"  hasLocalPlayer: !!hasLocalPlayer," +
		"  localX: localX | 0," +
		"  localZ: localZ | 0," +
		"  midRegionBaseX: midRegionBaseX | 0," +
		"  midRegionBaseZ: midRegionBaseZ | 0," +
		"  worldX: worldX | 0," +
		"  worldY: worldY | 0," +
		"  currentX: currentX | 0," +
		"  currentZ: currentZ | 0," +
		"  tileX: tileX | 0," +
		"  tileZ: tileZ | 0," +
		"  gameWidth: gameWidth | 0," +
		"  gameHeight: gameHeight | 0," +
		"  cameraRotation: cameraRotation | 0," +
		"  cameraAngle: cameraAngle | 0," +
		"  cameraZoom: cameraZoom | 0," +
		"  lastZoom: lastZoom | 0," +
		"  loginScreenNumber: loginScreenNumber | 0," +
		"  viewMode: String(viewMode || '')," +
		"  mouseX: mouseX | 0," +
		"  mouseY: mouseY | 0," +
		"  mouseButtonClick: mouseButtonClick | 0," +
		"  mouseButtonDown: mouseButtonDown | 0," +
		"  lastMouseDown: lastMouseDown | 0," +
		"  chatMessageInput: String(chatMessageInput || '')," +
		"  inputTextCurrent: String(inputTextCurrent || '')," +
		"  updatedAt: Date.now()" +
		"};" +
		"window.__voidscapeClientState = state;" +
		"if (typeof window.__voidscapeRecordClientState === 'function') {" +
		"  window.__voidscapeRecordClientState(state);" +
		"}")
	private static native void publishClientState(
		boolean hasLocalPlayer,
		int localX,
		int localZ,
		int midRegionBaseX,
		int midRegionBaseZ,
		int worldX,
		int worldY,
		int currentX,
		int currentZ,
		int tileX,
		int tileZ,
		int gameWidth,
		int gameHeight,
		int cameraRotation,
		int cameraAngle,
		int cameraZoom,
		int lastZoom,
		int loginScreenNumber,
		String viewMode,
		int mouseX,
		int mouseY,
		int mouseButtonClick,
		int mouseButtonDown,
		int lastMouseDown,
		String chatMessageInput,
		String inputTextCurrent);

	@JSBody(params = {
		"loginScreenNumber",
		"viewMode",
		"userText",
		"passwordLength",
		"status1",
		"status2",
		"userFocused",
		"passwordFocused"
	}, script =
		"window.__voidscapeLoginState = {" +
		"  loginScreenNumber: loginScreenNumber | 0," +
		"  viewMode: String(viewMode || '')," +
		"  userText: String(userText || '')," +
		"  passwordLength: Math.max(0, passwordLength | 0)," +
		"  status1: String(status1 || '')," +
		"  status2: String(status2 || '')," +
		"  userFocused: !!userFocused," +
		"  passwordFocused: !!passwordFocused," +
		"  updatedAt: Date.now()" +
		"};")
	private static native void publishLoginState(
		int loginScreenNumber,
		String viewMode,
		String userText,
		int passwordLength,
		String status1,
		String status2,
		boolean userFocused,
		boolean passwordFocused);

	@JSBody(params = {
		"customUi",
		"serverWantsCustomUi",
			"androidProfile",
			"webBuild",
			"showUiTab",
			"mobileSidePanelKey",
			"messageTab",
			"chatPanelHidden",
			"phoneLandscapeLooseChat",
			"mobileLooseChat",
			"mobilePanelShell",
		"canvasTopTabsVisible",
		"canvasPanelRailVisible",
		"canvasPanelDockVisible",
		"topMenuVisible",
		"menuX",
			"menuY",
			"menuWidth",
			"menuHeight",
			"keyboardShowing",
			"blockingDialog",
			"blockingDialogName",
			"spellShortcutActive",
			"spellShortcutLabel",
			"bankOpen",
			"bankRenderer",
			"bankDiagnosticsJson",
			"spellShortcutName"
		}, script =
			"var dialogOpen = !!blockingDialog;" +
			"var previous = window.__voidscapeUiState || null;" +
			"var bankState = null;" +
			"try { bankState = bankDiagnosticsJson ? JSON.parse(String(bankDiagnosticsJson)) : null; }" +
			"catch (error) { bankState = { open: !!bankOpen, renderer: String(bankRenderer || ''), parseError: String(error && error.message || error) }; }" +
			"if (document.body) document.body.classList.toggle('dialog-open', dialogOpen);" +
			"var chatAccessMode = (!!customUi && !!androidProfile && !!webBuild) ? (!!chatPanelHidden ? 'collapsed-helper' : 'canvas') : 'dom-helper';" +
			"var panelAccessMode = (!!customUi && !!androidProfile && !!webBuild && !!canvasPanelDockVisible) ? 'canvas-dock' : ((!!customUi && !!androidProfile && !!webBuild && !!canvasPanelRailVisible) ? 'canvas-rail' : ((!!customUi && !!androidProfile && !!webBuild && !!mobilePanelShell) ? 'drawer' : 'canvas'));" +
			"var state = {" +
			"  customUi: !!customUi," +
			"  serverWantsCustomUi: !!serverWantsCustomUi," +
			"  androidProfile: !!androidProfile," +
			"  webBuild: !!webBuild," +
			"  showUiTab: showUiTab | 0," +
			"  mobileSidePanelKey: String(mobileSidePanelKey || '')," +
			"  messageTab: String(messageTab || '')," +
			"  chatPanelHidden: !!chatPanelHidden," +
			"  phoneLandscapeLooseChat: !!phoneLandscapeLooseChat," +
			"  mobileLooseChat: !!mobileLooseChat," +
			"  chatAccessMode: chatAccessMode," +
		"  mobilePanelShell: !!mobilePanelShell," +
		"  canvasTopTabsVisible: !!canvasTopTabsVisible," +
		"  canvasPanelRailVisible: !!canvasPanelRailVisible," +
		"  canvasPanelDockVisible: !!canvasPanelDockVisible," +
		"  panelAccessMode: panelAccessMode," +
		"  topMenuVisible: !!topMenuVisible," +
		"  menuX: menuX | 0," +
		"  menuY: menuY | 0," +
			"  menuWidth: menuWidth | 0," +
			"  menuHeight: menuHeight | 0," +
			"  keyboardShowing: !!keyboardShowing," +
			"  blockingDialog: dialogOpen," +
			"  blockingDialogName: String(blockingDialogName || '')," +
			"  spellShortcutActive: !!spellShortcutActive," +
			"  spellShortcutLabel: String(spellShortcutLabel || '')," +
			"  bankOpen: !!bankOpen," +
			"  bankRenderer: String(bankRenderer || '')," +
			"  bank: bankState," +
			"  spellShortcutName: String(spellShortcutName || '')," +
			"  updatedAt: Date.now()" +
			"};" +
			"window.__voidscapeUiState = state;" +
			"var magicButton = document.getElementById('magic-button');" +
			"if (magicButton) {" +
			"  var armed = !!(state.customUi && state.androidProfile && state.webBuild && state.spellShortcutActive);" +
			"  magicButton.classList.toggle('spell-shortcut', armed);" +
			"  magicButton.textContent = armed ? (state.spellShortcutLabel || 'Cast') : 'Mag';" +
			"  magicButton.setAttribute('aria-label', armed ? ('Cast ' + (state.spellShortcutName || 'selected spell')) : 'Open magic panel');" +
			"  magicButton.setAttribute('data-spell-shortcut', armed ? 'true' : 'false');" +
			"}" +
			"var chatHelperVisible = !!(state.customUi && state.androidProfile && state.webBuild && !state.blockingDialog && (state.chatPanelHidden || state.canvasPanelDockVisible));" +
				"if (document.body) document.body.classList.toggle('chat-helper-visible', chatHelperVisible);" +
				"if (document.body) document.body.classList.toggle('canvas-panel-rail-visible', !!state.canvasPanelRailVisible);" +
				"if (document.body) document.body.classList.toggle('canvas-panel-dock-visible', !!state.canvasPanelDockVisible);" +
				"if (document.body) document.body.classList.toggle('shared-panel-open', (state.showUiTab | 0) !== 0);" +
				"if (document.body) document.body.classList.toggle('android-parity-canvas-panels', !!(state.customUi && state.androidProfile && state.webBuild && state.panelAccessMode === 'canvas'));" +
				"if (!chatHelperVisible && window.__voidscapeChatTrayOpen) {" +
			"  if (window.__voidscapeSetChatTrayOpen) window.__voidscapeSetChatTrayOpen(false);" +
			"  else {" +
			"    window.__voidscapeChatTrayOpen = false;" +
			"    if (document.body) document.body.classList.remove('chat-tray-open');" +
			"  }" +
			"}" +
			"if (state.customUi && state.androidProfile && state.webBuild) {" +
			"  var history = window.__voidscapeUiHistory;" +
			"  if (!history || !Array.isArray(history.events)) {" +
			"    history = { createdAt: new Date().toISOString(), updatedAt: '', events: [], messageTabs: [], showUiTabs: [] };" +
			"  }" +
			"  if (!Array.isArray(history.messageTabs)) history.messageTabs = [];" +
			"  if (!Array.isArray(history.showUiTabs)) history.showUiTabs = [];" +
			"  var addUnique = function(list, value) {" +
			"    if (list.indexOf(value) < 0) list.push(value);" +
			"  };" +
			"  var pushUiEvent = function(kind, value) {" +
			"    history.events.push({" +
			"      kind: kind," +
			"      value: value," +
			"      messageTab: state.messageTab," +
			"      showUiTab: state.showUiTab," +
			"      blockingDialog: state.blockingDialog," +
			"      at: new Date().toISOString()" +
			"    });" +
			"    if (history.events.length > 48) history.events = history.events.slice(history.events.length - 48);" +
			"  };" +
			"  if (state.messageTab && (!previous || previous.messageTab !== state.messageTab)) {" +
			"    addUnique(history.messageTabs, state.messageTab);" +
			"    pushUiEvent('messageTab', state.messageTab);" +
			"  }" +
			"  if (!previous || previous.showUiTab !== state.showUiTab) {" +
			"    addUnique(history.showUiTabs, state.showUiTab);" +
			"    pushUiEvent('showUiTab', state.showUiTab);" +
			"  }" +
			"  history.updatedAt = new Date().toISOString();" +
			"  window.__voidscapeUiHistory = history;" +
			"}")
	private static native void publishUiState(
		boolean customUi,
		boolean serverWantsCustomUi,
			boolean androidProfile,
			boolean webBuild,
			int showUiTab,
			String mobileSidePanelKey,
			String messageTab,
		boolean chatPanelHidden,
		boolean phoneLandscapeLooseChat,
		boolean mobileLooseChat,
		boolean mobilePanelShell,
		boolean canvasTopTabsVisible,
		boolean canvasPanelRailVisible,
		boolean canvasPanelDockVisible,
		boolean topMenuVisible,
		int menuX,
		int menuY,
		int menuWidth,
		int menuHeight,
		boolean keyboardShowing,
			boolean blockingDialog,
			String blockingDialogName,
			boolean spellShortcutActive,
			String spellShortcutLabel,
			boolean bankOpen,
			String bankRenderer,
			String bankDiagnosticsJson,
			String spellShortcutName);

	@JSBody(params = {
		"visible",
		"searchFocused",
		"searchQuery",
		"floor",
		"zoom",
		"panX",
		"panY",
		"windowX",
		"windowY",
		"windowW",
		"windowH",
		"contentCenterX",
		"contentCenterY",
		"zoomInCenterX",
		"zoomInCenterY",
		"zoomOutCenterX",
		"zoomOutCenterY",
		"resetCenterX",
		"resetCenterY",
		"searchCenterX",
		"searchCenterY",
		"closeCenterX",
		"closeCenterY",
		"openerVisible",
		"openerX",
		"openerY",
		"openerW",
		"openerH",
		"walkRequestX",
		"walkRequestY",
		"walkRequestAtMillis",
		"walkRouteAtMillis",
		"walkRouteOk",
		"walkRouteReason",
		"walkRouteCount"
	}, script =
		"const previous = window.__voidscapeWorldMapState || null;" +
		"const requestAt = Number(walkRequestAtMillis || 0);" +
		"const routeAt = Number(walkRouteAtMillis || 0);" +
		"const state = {" +
		"  visible: !!visible," +
		"  searchFocused: !!searchFocused," +
		"  searchQuery: String(searchQuery || '')," +
		"  floor: floor | 0," +
		"  zoom: zoom | 0," +
		"  panX: panX | 0," +
		"  panY: panY | 0," +
		"  window: { x: windowX | 0, y: windowY | 0, width: windowW | 0, height: windowH | 0 }," +
		"  contentCenter: { x: contentCenterX | 0, y: contentCenterY | 0 }," +
		"  zoomInCenter: { x: zoomInCenterX | 0, y: zoomInCenterY | 0 }," +
		"  zoomOutCenter: { x: zoomOutCenterX | 0, y: zoomOutCenterY | 0 }," +
		"  resetCenter: { x: resetCenterX | 0, y: resetCenterY | 0 }," +
		"  searchCenter: { x: searchCenterX | 0, y: searchCenterY | 0 }," +
		"  closeCenter: { x: closeCenterX | 0, y: closeCenterY | 0 }," +
		"  opener: {" +
		"    visible: !!openerVisible," +
		"    x: openerX | 0," +
		"    y: openerY | 0," +
		"    width: openerW | 0," +
		"    height: openerH | 0," +
		"    center: { x: (openerX | 0) + Math.floor((openerW | 0) / 2), y: (openerY | 0) + Math.floor((openerH | 0) / 2) }" +
		"  }," +
		"  walker: {" +
		"    lastRequest: { x: walkRequestX | 0, y: walkRequestY | 0, at: requestAt }," +
		"    lastRoute: { at: routeAt, ok: !!walkRouteOk, reason: walkRouteReason | 0, count: walkRouteCount | 0 }" +
		"  }," +
		"  updatedAt: Date.now()" +
		"};" +
		"window.__voidscapeWorldMapState = state;" +
		"if (document.body) document.body.classList.toggle('world-map-open', state.visible);" +
		"const history = window.__voidscapeWorldMapHistory || [];" +
		"const pushHistory = function(kind, detail) {" +
		"  history.push({ kind: kind, detail: detail, at: new Date().toISOString() });" +
		"  if (history.length > 48) history.splice(0, history.length - 48);" +
		"};" +
		"if (!previous || previous.visible !== state.visible) pushHistory('visible', { visible: state.visible });" +
		"if (!previous || previous.zoom !== state.zoom || previous.panX !== state.panX || previous.panY !== state.panY) {" +
		"  pushHistory('view', { zoom: state.zoom, panX: state.panX, panY: state.panY });" +
		"}" +
		"if (!previous || previous.searchFocused !== state.searchFocused || previous.searchQuery !== state.searchQuery) {" +
		"  pushHistory('search', { focused: state.searchFocused, query: state.searchQuery });" +
		"}" +
		"const previousWalker = previous && previous.walker ? previous.walker : null;" +
		"if (!previousWalker || !previousWalker.lastRequest || previousWalker.lastRequest.at !== state.walker.lastRequest.at) {" +
		"  pushHistory('walkRequest', state.walker.lastRequest);" +
		"}" +
		"if (!previousWalker || !previousWalker.lastRoute || previousWalker.lastRoute.at !== state.walker.lastRoute.at) {" +
		"  pushHistory('walkRoute', state.walker.lastRoute);" +
		"}" +
		"window.__voidscapeWorldMapHistory = history;")
	private static native void publishWorldMapState(
		boolean visible,
		boolean searchFocused,
		String searchQuery,
		int floor,
		int zoom,
		int panX,
		int panY,
		int windowX,
		int windowY,
		int windowW,
		int windowH,
		int contentCenterX,
		int contentCenterY,
		int zoomInCenterX,
		int zoomInCenterY,
		int zoomOutCenterX,
		int zoomOutCenterY,
		int resetCenterX,
		int resetCenterY,
		int searchCenterX,
		int searchCenterY,
		int closeCenterX,
		int closeCenterY,
		boolean openerVisible,
		int openerX,
		int openerY,
		int openerW,
		int openerH,
		int walkRequestX,
		int walkRequestY,
		String walkRequestAtMillis,
		String walkRouteAtMillis,
		boolean walkRouteOk,
		int walkRouteReason,
		int walkRouteCount);

	@JSBody(params = {}, script =
		"const viewport = window.visualViewport || null;" +
		"const cssWidth = Math.max(1, (viewport && viewport.width) || window.innerWidth || document.documentElement.clientWidth || 1);" +
		"const cssHeight = Math.max(1, (viewport && viewport.height) || window.innerHeight || document.documentElement.clientHeight || 1);" +
		"if (cssWidth <= cssHeight) return 512;" +
		"return Math.max(512, Math.min(1152, Math.round(346 * cssWidth / cssHeight)));")
	private static native int getTargetGameWidth();

	@JSBody(params = {}, script =
		"const viewport = window.visualViewport || null;" +
		"const cssWidth = Math.max(1, (viewport && viewport.width) || window.innerWidth || document.documentElement.clientWidth || 1);" +
		"const cssHeight = Math.max(1, (viewport && viewport.height) || window.innerHeight || document.documentElement.clientHeight || 1);" +
		"if (cssHeight > cssWidth) return Math.max(346, Math.min(1152, Math.round(512 * cssHeight / cssWidth)));" +
		"return 346;")
	private static native int getTargetGameFullHeight();

	@JSBody(params = {}, script =
		"const q = window.__voidscapeInputQueue;" +
		"if (!q || q.length === 0) return null;" +
		"return q.shift();")
	private static native String pollInputEvent();

	@JSBody(params = {}, script =
		"return !!window.__voidscapeDiagnosticsEnabled && !!window.__voidscapeSmokeLoginRequest;")
	private static native boolean hasWebSmokeLoginRequest();

	@JSBody(params = {}, script =
		"const request = window.__voidscapeSmokeLoginRequest || {};" +
		"return String(request.user || '');")
	private static native String getWebSmokeLoginUser();

	@JSBody(params = {}, script =
		"const request = window.__voidscapeSmokeLoginRequest || {};" +
		"return String(request.pass || '');")
	private static native String getWebSmokeLoginPass();

	@JSBody(params = {}, script =
		"delete window.__voidscapeSmokeLoginRequest;")
	private static native void clearWebSmokeLoginRequest();

	@JSBody(params = {}, script =
		"if (window.__voidscapeFocusKeyboard) window.__voidscapeFocusKeyboard();" +
		"else window.__voidscapeKeyboardWanted = true;")
	private static native void requestKeyboardFocus();

	@JSBody(params = {}, script =
		"if (window.__voidscapeBlurKeyboard) window.__voidscapeBlurKeyboard();" +
		"else window.__voidscapeKeyboardWanted = false;")
	private static native void hideKeyboardFocus();

	@JSBody(params = { "path" }, script =
		"let requestPath = String(path || '');" +
		"if (requestPath.indexOf('Cache/') === 0) {" +
		"  const token = String(window.__voidscapeAssetToken || '').trim();" +
		"  if (token) requestPath += (requestPath.indexOf('?') >= 0 ? '&' : '?') + 'v=' + encodeURIComponent(token);" +
		"}" +
		"const xhr = new XMLHttpRequest();" +
		"xhr.open('GET', requestPath, false);" +
		"xhr.overrideMimeType('text/plain; charset=x-user-defined');" +
		"xhr.send(null);" +
		"if (xhr.status < 200 || xhr.status >= 300) {" +
		"  throw new Error('HTTP ' + xhr.status + ' for ' + requestPath);" +
		"}" +
		"const text = xhr.responseText;" +
		"const bytes = new Int8Array(text.length);" +
		"for (let i = 0; i < text.length; i++) {" +
		"  bytes[i] = text.charCodeAt(i) & 0xff;" +
		"}" +
		"return bytes;")
	private static native byte[] fetchResourceBytes(String path);
}
