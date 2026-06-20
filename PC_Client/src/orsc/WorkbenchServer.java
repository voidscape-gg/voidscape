package orsc;

import com.openrsc.client.entityhandling.instances.Item;
import com.openrsc.interfaces.misc.AuctionHouse;
import com.openrsc.interfaces.misc.CustomBankInterface;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import orsc.enumerations.MessageType;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WorkbenchServer {
	private static final String ENABLED_PROPERTY = "voidscape.workbench";
	private static final String PORT_PROPERTY = "voidscape.workbench.port";
	private static final String DIR_PROPERTY = "voidscape.workbench.dir";
	private static final int DEFAULT_PORT = 18787;
	private static final String DEFAULT_DIR = "../tmp/workbench";
	private static final int SUBSCRIPTION_CARD_ITEM_ID = 1602;
	private static final int VOID_SUBSCRIPTION_VENDOR_NPC_ID = 848;

	private static HttpServer server;
	private static ExecutorService executor;
	private static int port = DEFAULT_PORT;
	private static CaptureResult lastCapture;

	private WorkbenchServer() {
	}

	static boolean isEnabled() {
		return Boolean.getBoolean(ENABLED_PROPERTY);
	}

	static synchronized void start() {
		if (!isEnabled() || server != null) return;

		port = Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT);
		try {
			HttpServer httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
			httpServer.createContext("/health", WorkbenchServer::handleHealth);
			httpServer.createContext("/state", WorkbenchServer::handleState);
			httpServer.createContext("/screenshot", WorkbenchServer::handleScreenshot);
			httpServer.createContext("/captures/latest", WorkbenchServer::handleLatestCapture);
			httpServer.createContext("/input/click", WorkbenchServer::handleClick);
			httpServer.createContext("/input/drag", WorkbenchServer::handleDrag);
			httpServer.createContext("/input/scroll", WorkbenchServer::handleScroll);
			httpServer.createContext("/input/key", WorkbenchServer::handleKey);
			httpServer.createContext("/input/type", WorkbenchServer::handleType);
			httpServer.createContext("/input/command", WorkbenchServer::handleCommand);
			httpServer.createContext("/input/npc-action", WorkbenchServer::handleNpcAction);
			httpServer.createContext("/dev/ready", WorkbenchServer::handleDevReady);
			httpServer.createContext("/dev/ui-panel", WorkbenchServer::handleDevUiPanel);
			httpServer.createContext("/dev/viewport", WorkbenchServer::handleViewportPreset);
			httpServer.createContext("/dev/world-reskin", WorkbenchServer::handleWorldReskin);
			httpServer.createContext("/dev/reload-entity-sprites", WorkbenchServer::handleReloadEntitySprites);
			httpServer.createContext("/fixture/auction-house", WorkbenchServer::handleAuctionHouseFixture);
			httpServer.createContext("/scenario/auction-house-open", WorkbenchServer::handleAuctionHouseScenario);
			httpServer.createContext("/scenario/ui-panels", WorkbenchServer::handleUiPanelsScenario);
			httpServer.createContext("/scenario/subscription-vendor-claim", WorkbenchServer::handleSubscriptionVendorScenario);
			httpServer.createContext("/scenario/subscription-card-redeem", WorkbenchServer::handleSubscriptionCardRedeemScenario);
			executor = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "voidscape-workbench");
				thread.setDaemon(true);
				return thread;
			});
			httpServer.setExecutor(executor);
			httpServer.start();
			server = httpServer;
			System.out.println("Voidscape workbench listening on http://127.0.0.1:" + port);
		} catch (IOException e) {
			System.out.println("Unable to start Voidscape workbench: " + e.getMessage());
		}
	}

	static synchronized void stop() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
		if (executor != null) {
			executor.shutdownNow();
			executor = null;
		}
	}

	static CaptureResult captureOnce(String reason) throws IOException {
		BufferedImage image = ORSCApplet.copyGameImageForWorkbench();
		if (image == null) throw new IOException("No rendered game frame is available yet");

		File screenshotsDir = new File(workbenchDir(), "screenshots");
		Files.createDirectories(screenshotsDir.toPath());

		String timestamp = fileTimestamp();
		String safeReason = sanitizeReason(reason);
		String stem = timestamp + "-" + safeReason;
		File pngFile = new File(screenshotsDir, stem + ".png").getAbsoluteFile();
		File jsonFile = new File(screenshotsDir, stem + ".json").getAbsoluteFile();

		ImageIO.write(image, "png", pngFile);
		String stateJson = stateJson(pngFile, image.getWidth(), image.getHeight());
		Files.write(jsonFile.toPath(), stateJson.getBytes(StandardCharsets.UTF_8));

		CaptureResult result = new CaptureResult(reason, pngFile, jsonFile, image.getWidth(), image.getHeight());
		lastCapture = result;
		return result;
	}

	static void captureFromHotkey(mudclient client) {
		try {
			CaptureResult result = captureOnce("hotkey");
			if (client != null) {
				client.showMessage(false, null, "Screenshot saved: " + result.pngFile.getPath(),
					MessageType.GAME, 0, null);
			}
		} catch (IOException e) {
			if (client != null) {
				client.showMessage(false, null, "Screenshot failed: " + e.getMessage(),
					MessageType.GAME, 0, null);
			}
		}
	}

	private static void handleHealth(HttpExchange exchange) throws IOException {
		if (!requireGet(exchange)) return;
		String json = "{"
			+ "\"ok\":true,"
			+ "\"clientVersion\":" + Config.CLIENT_VERSION + ","
			+ "\"port\":" + port + ","
			+ "\"generatedAt\":\"" + jsonEscape(isoTimestamp()) + "\""
			+ "}";
		sendJson(exchange, 200, json);
	}

	private static void handleState(HttpExchange exchange) throws IOException {
		if (!requireGet(exchange)) return;
		sendJson(exchange, 200, stateJson(null, -1, -1));
	}

	private static void handleScreenshot(HttpExchange exchange) throws IOException {
		if (!requireGet(exchange)) return;
		try {
			CaptureResult result = captureOnce("http");
			sendJson(exchange, 200, captureJson(result));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleLatestCapture(HttpExchange exchange) throws IOException {
		if (!requireGet(exchange)) return;
		if (lastCapture == null) {
			sendJson(exchange, 404, "{\"ok\":false,\"error\":\"No capture has been saved yet\"}");
			return;
		}
		sendJson(exchange, 200, captureJson(lastCapture));
	}

	private static void handleClick(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		int x = requiredInt(fields, "x");
		int y = requiredInt(fields, "y");
		String button = fields.containsKey("button") ? fields.get("button") : "left";
		clickGame(x, y, button);
		sendJson(exchange, 200, controlJson("click", "\"x\":" + x + ",\"y\":" + y + ",\"button\":\"" + jsonEscape(button) + "\""));
	}

	private static void handleDrag(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		int fromX = requiredInt(fields, "fromX");
		int fromY = requiredInt(fields, "fromY");
		int toX = requiredInt(fields, "toX");
		int toY = requiredInt(fields, "toY");
		String button = fields.containsKey("button") ? fields.get("button") : "left";
		int steps = fields.containsKey("steps") ? requiredInt(fields, "steps") : 8;
		int durationMs = fields.containsKey("durationMs") ? requiredInt(fields, "durationMs") : 450;
		dragGame(fromX, fromY, toX, toY, button, steps, durationMs);
		sendJson(exchange, 200, controlJson("drag",
			"\"fromX\":" + fromX + ",\"fromY\":" + fromY
				+ ",\"toX\":" + toX + ",\"toY\":" + toY
				+ ",\"button\":\"" + jsonEscape(button) + "\""));
	}

	private static void handleScroll(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		int x = requiredInt(fields, "x");
		int y = requiredInt(fields, "y");
		int amount = requiredInt(fields, "amount");
		scrollGame(x, y, amount);
		sendJson(exchange, 200, controlJson("scroll", "\"x\":" + x + ",\"y\":" + y + ",\"amount\":" + amount));
	}

	private static void handleKey(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String key = fields.get("key");
		String character = fields.get("char");
		if ((key == null || key.isEmpty()) && character != null && !character.isEmpty()) {
			typeText(character);
		} else {
			pressKeyName(requiredString(fields, "key"));
		}
		sendJson(exchange, 200, controlJson("key", null));
	}

	private static void handleType(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String text = requiredString(fields, "text");
		typeText(text);
		sendJson(exchange, 200, controlJson("type", "\"length\":" + text.length()));
	}

	private static void handleCommand(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String command = normalizeCommand(requiredString(fields, "command"));
		sendCommand(command);
		sendJson(exchange, 200, controlJson("command", "\"command\":\"" + jsonEscape(command) + "\""));
	}

	private static void handleNpcAction(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String action = fields.containsKey("action") ? fields.get("action") : "command1";
		ORSCharacter npc = findWorkbenchNpc(fields);
		if (npc == null) {
			sendJson(exchange, 404, "{\"ok\":false,\"error\":\"NPC is not visible\"}");
			return;
		}

		int serverIndex = npc.serverIndex;
		if ("talk".equalsIgnoreCase(action) || "talk-to".equalsIgnoreCase(action)) {
			sendNpcTalk(serverIndex);
			action = "talk";
		} else if ("command1".equalsIgnoreCase(action) || "op1".equalsIgnoreCase(action)) {
			sendNpcCommand1(serverIndex);
			action = "command1";
		} else if ("command2".equalsIgnoreCase(action) || "op2".equalsIgnoreCase(action)) {
			sendNpcCommand2(serverIndex);
			action = "command2";
		} else {
			sendJson(exchange, 400, "{\"ok\":false,\"error\":\"Unknown NPC action\"}");
			return;
		}

		sendJson(exchange, 200, controlJson("npc-action",
			"\"npcAction\":\"" + jsonEscape(action) + "\",\"npcId\":" + npc.npcId + ",\"npcServerIndex\":" + serverIndex
				+ ",\"npcName\":\"" + jsonEscape(npc.displayName) + "\""));
	}

	private static void handleDevReady(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, devReadyJson());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleDevUiPanel(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String panel = requiredString(fields, "panel");
		boolean capture = !"false".equalsIgnoreCase(fields.containsKey("capture") ? fields.get("capture") : "true");
		try {
			openUiPanel(panel);
			StringBuilder json = new StringBuilder();
			json.append("{\"ok\":true,");
			json.append("\"action\":\"ui-panel\",");
			json.append("\"panel\":\"").append(jsonEscape(panel)).append("\",");
			json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
			if (capture) {
				CaptureResult result = captureOnce("ui-panel-" + panel);
				appendSingleCapture(json, result);
				json.append(",");
			}
			json.append("\"state\":").append(stateJson(null, -1, -1));
			json.append("}");
			sendJson(exchange, 200, json.toString());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleViewportPreset(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		int index = requiredInt(fields, "index");
		try {
			runOnEdt(() -> ScaledWindow.getInstance().applyViewportPreset(index));
			sleep(600);
			sendJson(exchange, 200, controlJson("viewport",
				"\"index\":" + ScaledWindow.getViewportPresetIndex()
					+ ",\"label\":\"" + jsonEscape(ScaledWindow.getViewportPresetLabel()) + "\""));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleWorldReskin(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String requestedMode = fields.containsKey("mode") ? fields.get("mode") : fields.get("profile");
		int mode = parseWorldReskinMode(requestedMode);
		try {
			mudclient client = requireClient();
			runOnEdt(() -> client.workbenchSetWorldReskinMode(mode));
			sleep(700);
			StringBuilder json = new StringBuilder();
			json.append("{\"ok\":true,");
			json.append("\"action\":\"world-reskin\",");
			json.append("\"mode\":").append(client.workbenchWorldReskinMode()).append(",");
			appendString(json, "modeName", client.workbenchWorldReskinModeName()).append(",");
			json.append("\"active\":").append(client.workbenchVoidWorldReskinActive()).append(",");
			json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\"");
			if ("true".equalsIgnoreCase(fields.get("capture"))) {
				CaptureResult result = captureOnce("world-reskin-" + client.workbenchWorldReskinModeName());
				json.append(",");
				appendSingleCapture(json, result);
			}
			json.append("}");
			sendJson(exchange, 200, json.toString());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleReloadEntitySprites(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			mudclient client = requireClient();
			runOnEdt(client::workbenchReloadEntitySprites);
			sleep(500);
			sendJson(exchange, 200, controlJson("reload-entity-sprites", null));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleAuctionHouseFixture(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, auctionHouseFixtureJson());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleAuctionHouseScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, auctionHouseScenarioJson());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleUiPanelsScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		String panels = fields.containsKey("panels") ? fields.get("panels") : "";
		try {
			sendJson(exchange, 200, uiPanelsScenarioJson(panels));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleSubscriptionVendorScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, subscriptionVendorScenarioJson());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleSubscriptionCardRedeemScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, subscriptionCardRedeemScenarioJson());
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static boolean requireGet(HttpExchange exchange) throws IOException {
		if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) return true;
		sendJson(exchange, 405, "{\"ok\":false,\"error\":\"GET required\"}");
		return false;
	}

	private static boolean requirePost(HttpExchange exchange) throws IOException {
		if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) return true;
		sendJson(exchange, 405, "{\"ok\":false,\"error\":\"POST required\"}");
		return false;
	}

	private static String captureJson(CaptureResult result) {
		return "{"
			+ "\"ok\":true,"
			+ "\"reason\":\"" + jsonEscape(result.reason) + "\","
			+ "\"pngPath\":\"" + jsonEscape(result.pngFile.getPath()) + "\","
			+ "\"statePath\":\"" + jsonEscape(result.jsonFile.getPath()) + "\","
			+ "\"width\":" + result.width + ","
			+ "\"height\":" + result.height + ","
			+ "\"generatedAt\":\"" + jsonEscape(isoTimestamp()) + "\""
			+ "}";
	}

	private static String controlJson(String action, String extraFields) {
		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"action\":\"").append(jsonEscape(action)).append("\",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\"");
		if (extraFields != null && !extraFields.isEmpty()) {
			json.append(",").append(extraFields);
		}
		json.append("}");
		return json.toString();
	}

	private static String stateJson(File screenshotFile, int screenshotWidth, int screenshotHeight) {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		StringBuilder json = new StringBuilder();
		json.append("{");
		appendString(json, "generatedAt", isoTimestamp()).append(",");
		json.append("\"clientVersion\":").append(Config.CLIENT_VERSION).append(",");
		appendString(json, "serverIp", Config.SERVER_IP).append(",");
		json.append("\"serverPort\":").append(Config.SERVER_PORT).append(",");
		appendScreenshot(json, screenshotFile, screenshotWidth, screenshotHeight);
		json.append(",");
		appendGame(json, client);
		json.append(",");
		appendMouse(json, client);
		json.append(",");
		appendPlayer(json, client);
		json.append(",");
		appendInterfaces(json, client);
		json.append("}");
		return json.toString();
	}

	private static void appendScreenshot(StringBuilder json, File screenshotFile, int width, int height) {
		json.append("\"screenshot\":");
		if (screenshotFile == null) {
			json.append("null");
			return;
		}
		json.append("{");
		appendString(json, "pngPath", screenshotFile.getPath()).append(",");
		json.append("\"width\":").append(width).append(",");
		json.append("\"height\":").append(height);
		json.append("}");
	}

	private static void appendGame(StringBuilder json, mudclient client) {
		json.append("\"game\":{");
		if (client == null) {
			json.append("\"available\":false");
		} else {
			json.append("\"available\":true,");
			json.append("\"state\":").append(client.getGameState()).append(",");
			json.append("\"width\":").append(client.getGameWidth()).append(",");
			json.append("\"height\":").append(client.getGameHeight()).append(",");
			appendString(json, "inputXAction", client.inputX_Action == null ? "" : client.inputX_Action.name()).append(",");
			appendString(json, "inputTextCurrent", client.inputTextCurrent).append(",");
			json.append("\"worldReskinMode\":").append(client.workbenchWorldReskinMode()).append(",");
			appendString(json, "worldReskinModeName", client.workbenchWorldReskinModeName()).append(",");
			json.append("\"worldReskinActive\":").append(client.workbenchVoidWorldReskinActive());
		}
		json.append("}");
	}

	private static void appendMouse(StringBuilder json, mudclient client) {
		json.append("\"mouse\":{");
		if (client == null) {
			json.append("\"available\":false");
		} else {
			json.append("\"available\":true,");
			json.append("\"x\":").append(client.getMouseX()).append(",");
			json.append("\"y\":").append(client.getMouseY()).append(",");
			json.append("\"click\":").append(client.getMouseClick()).append(",");
			json.append("\"down\":").append(client.getMouseButtonDown());
		}
		json.append("}");
	}

	private static void appendPlayer(StringBuilder json, mudclient client) {
		json.append("\"player\":");
		ORSCharacter player = client == null ? null : client.getLocalPlayer();
		if (player == null) {
			json.append("null");
			return;
		}

		json.append("{");
		appendString(json, "displayName", player.displayName).append(",");
		appendString(json, "accountName", player.accountName).append(",");
		json.append("\"x\":").append(player.currentX).append(",");
		json.append("\"z\":").append(player.currentZ).append(",");
		json.append("\"tileX\":").append(client.getLocalPlayerX()).append(",");
		json.append("\"tileZ\":").append(client.getLocalPlayerZ()).append(",");
		json.append("\"worldX\":").append(client.getMidRegionBaseX() + client.getLocalPlayerX()).append(",");
		json.append("\"worldZ\":").append(client.getMidRegionBaseZ() + client.getLocalPlayerZ()).append(",");
		json.append("\"level\":").append(player.level).append(",");
		json.append("\"healthCurrent\":").append(player.healthCurrent).append(",");
		json.append("\"healthMax\":").append(player.healthMax).append(",");
		json.append("\"groupId\":").append(player.groupID).append(",");
		json.append("\"admin\":").append(player.isAdmin()).append(",");
		json.append("\"dev\":").append(player.isDev()).append(",");
		json.append("\"mod\":").append(player.isMod());
		json.append("}");
	}

	private static void appendInterfaces(StringBuilder json, mudclient client) {
		json.append("\"interfaces\":{");
		appendAuctionHouse(json, client);
		json.append(",");
		appendWorldMap(json, client);
		json.append(",");
		appendShop(json, client);
		json.append(",");
		appendBank(json, client);
		json.append(",");
		appendBestiary(json, client);
		json.append("}");
	}

	private static void appendAuctionHouse(StringBuilder json, mudclient client) {
		json.append("\"auctionHouse\":");
		AuctionHouse auctionHouse = client == null ? null : client.getAuctionHouse();
		if (auctionHouse == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(auctionHouse.isVisible()).append(",");
		json.append("\"activeInterface\":").append(auctionHouse.activeInterface).append(",");
		appendString(json, "activeInterfaceName", auctionHouseTabName(auctionHouse.activeInterface));
		json.append("}");
	}

	private static void appendWorldMap(StringBuilder json, mudclient client) {
		json.append("\"worldMap\":");
		if (client == null || client.worldMapPanel == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(client.worldMapPanel.isVisible()).append(",");
		json.append("\"floor\":").append(client.worldMapPanel.getCurrentFloor());
		json.append("}");
	}

	private static void appendShop(StringBuilder json, mudclient client) {
		json.append("\"shop\":");
		if (client == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(client.getShowDialogShop()).append(",");
		json.append("\"containsSubscriptionCard\":").append(client.shopContains(SUBSCRIPTION_CARD_ITEM_ID)).append(",");
		json.append("\"selectedItemIndex\":").append(client.getShopSelectedItemIndex()).append(",");
		json.append("\"selectedItemType\":").append(client.getShopSelectedItemType());
		json.append("}");
	}

	private static void appendBank(StringBuilder json, mudclient client) {
		json.append("\"bank\":");
		CustomBankInterface bank = client == null ? null : client.getBank();
		if (client == null || bank == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(client.isShowDialogBank()).append(",");
		json.append("\"page\":").append(client.bankPage).append(",");
		json.append("\"maxItems\":").append(client.bankItemsMax).append(",");
		json.append("\"count\":").append(bank.workbenchBankItemCount()).append(",");
		json.append("\"matches\":").append(bank.workbenchSearchMatchCount()).append(",");
		json.append("\"scrollRow\":").append(bank.workbenchScrollRow()).append(",");
		appendString(json, "search", bank.workbenchSearchText()).append(",");
		json.append("\"searchFocused\":").append(bank.workbenchSearchFocused()).append(",");
		json.append("\"noteMode\":").append(bank.workbenchNoteMode()).append(",");
		json.append("\"certMode\":").append(bank.workbenchCertMode()).append(",");
		json.append("\"organizeMode\":").append(bank.workbenchOrganizeMode()).append(",");
		json.append("\"arrangeMenuOpen\":").append(bank.workbenchArrangeMenuOpen()).append(",");
		json.append("\"loadoutsOpen\":").append(bank.workbenchLoadoutsOpen()).append(",");
		json.append("\"contextMenuOpen\":").append(bank.workbenchContextMenuOpen()).append(",");
		json.append("\"selectedBankSlot\":").append(bank.workbenchSelectedBankSlot()).append(",");
		json.append("\"selectedInventorySlot\":").append(bank.workbenchSelectedInventorySlot()).append(",");
		json.append("\"contextMenuX\":").append(bank.workbenchContextMenuX()).append(",");
		json.append("\"contextMenuY\":").append(bank.workbenchContextMenuY()).append(",");
		json.append("\"contextRowTopOffset\":").append(bank.workbenchContextMenuRowTopOffset()).append(",");
		json.append("\"contextRowHeight\":").append(bank.workbenchContextMenuRowHeight()).append(",");
		json.append("\"pendingSavePresetSlot\":").append(bank.workbenchPendingSavePresetSlot()).append(",");
		json.append("\"pendingClearPresetSlot\":").append(bank.workbenchPendingClearPresetSlot()).append(",");
		appendBankLayout(json, bank);
		json.append(",");
		appendBankItems(json, bank);
		json.append(",");
		appendInventoryItems(json, client);
		json.append(",");
		appendBankPresets(json, bank);
		json.append("}");
	}

	private static void appendBestiary(StringBuilder json, mudclient client) {
		json.append("\"bestiary\":");
		if (client == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(client.workbenchBestiaryVisible()).append(",");
		json.append("\"mode\":").append(client.workbenchBestiaryMode()).append(",");
		json.append("\"loaded\":").append(client.workbenchBestiaryLoaded()).append(",");
		json.append("\"catalogLoaded\":").append(client.workbenchBestiaryCatalogLoaded()).append(",");
		json.append("\"catalogPending\":").append(client.workbenchBestiaryCatalogPending()).append(",");
		json.append("\"catalogCount\":").append(client.workbenchBestiaryCatalogCount()).append(",");
		json.append("\"requestedNpcId\":").append(client.workbenchBestiaryRequestedNpcId()).append(",");
		json.append("\"pendingNpcId\":").append(client.workbenchBestiaryPendingNpcId()).append(",");
		appendString(json, "search", client.workbenchBestiarySearchText()).append(",");
		json.append("\"searchFocused\":").append(client.workbenchBestiarySearchFocused()).append(",");
		json.append("\"resultCount\":").append(client.workbenchBestiarySearchResultCount()).append(",");
		json.append("\"resultScrollRows\":").append(client.workbenchBestiaryResultScrollRows()).append(",");
		json.append("\"dropScrollPixels\":").append(client.workbenchBestiaryDropScrollPixels()).append(",");
		json.append("\"layout\":{");
		appendPoint(json, "search", client.workbenchBestiarySearchCenterX(), client.workbenchBestiarySearchCenterY())
			.append(",");
		appendPoint(json, "firstResult", client.workbenchBestiaryFirstResultCenterX(),
			client.workbenchBestiaryFirstResultCenterY()).append(",");
		appendPoint(json, "back", client.workbenchBestiaryBackCenterX(), client.workbenchBestiaryBackCenterY())
			.append(",");
		appendPoint(json, "dropArea", client.workbenchBestiaryDropAreaCenterX(),
			client.workbenchBestiaryDropAreaCenterY());
		json.append("}}");
	}

	private static void appendBankLayout(StringBuilder json, CustomBankInterface bank) {
		json.append("\"layout\":{");
		json.append("\"panelX\":").append(bank.workbenchPanelX()).append(",");
		json.append("\"panelY\":").append(bank.workbenchPanelY()).append(",");
		json.append("\"panelWidth\":").append(bank.workbenchPanelWidth()).append(",");
		json.append("\"panelHeight\":").append(bank.workbenchPanelHeight()).append(",");
		json.append("\"bankGridX\":").append(bank.workbenchBankGridX()).append(",");
		json.append("\"bankGridY\":").append(bank.workbenchBankGridY()).append(",");
		json.append("\"inventoryGridY\":").append(bank.workbenchInventoryGridY()).append(",");
		json.append("\"slotWidth\":").append(bank.workbenchBankSlotWidth()).append(",");
		json.append("\"slotHeight\":").append(bank.workbenchBankSlotHeight()).append(",");
		appendPoint(json, "search", bank.workbenchSearchCenterX(), bank.workbenchSearchCenterY()).append(",");
		appendPoint(json, "searchClear", bank.workbenchSearchClearCenterX(), bank.workbenchSearchClearCenterY()).append(",");
		appendPoint(json, "depositInventory", bank.workbenchDepositInventoryCenterX(), bank.workbenchDepositInventoryCenterY()).append(",");
		appendPoint(json, "arrange", bank.workbenchArrangeCenterX(), bank.workbenchArrangeCenterY()).append(",");
		appendPoint(json, "loadouts", bank.workbenchLoadoutsCenterX(), bank.workbenchLoadoutsCenterY()).append(",");
		appendPoint(json, "itemMode", bank.workbenchItemModeCenterX(), bank.workbenchItemModeCenterY()).append(",");
		appendPoint(json, "noteMode", bank.workbenchNoteModeCenterX(), bank.workbenchNoteModeCenterY()).append(",");
		appendPoint(json, "close", bank.workbenchCloseCenterX(), bank.workbenchCloseCenterY()).append(",");
		appendPoint(json, "bankSlot0", bank.workbenchBankSlotCenterX(0), bank.workbenchBankSlotCenterY(0)).append(",");
		appendPoint(json, "inventorySlot0", bank.workbenchInventorySlotCenterX(0), bank.workbenchInventorySlotCenterY(0));
		json.append("}");
	}

	private static void appendBankItems(StringBuilder json, CustomBankInterface bank) {
		json.append("\"items\":[");
		for (int i = 0; i < bank.workbenchBankItemCount(); i++) {
			if (i > 0) json.append(",");
			json.append("{");
			json.append("\"slot\":").append(i).append(",");
			json.append("\"id\":").append(bank.workbenchBankItemCatalogId(i)).append(",");
			json.append("\"amount\":").append(bank.workbenchBankItemAmount(i)).append(",");
			json.append("\"noted\":").append(bank.workbenchBankItemNoted(i)).append(",");
			appendString(json, "name", bank.workbenchBankItemName(i));
			json.append("}");
		}
		json.append("]");
	}

	private static void appendInventoryItems(StringBuilder json, mudclient client) {
		json.append("\"inventory\":[");
		for (int slot = 0; slot < client.getInventoryItemCount(); slot++) {
			if (slot > 0) json.append(",");
			Item item = client.getInventoryItem(slot);
			json.append("{");
			json.append("\"slot\":").append(slot).append(",");
			json.append("\"id\":").append(client.getInventoryItemID(slot)).append(",");
			json.append("\"amount\":").append(client.getInventoryItemAmount(slot)).append(",");
			json.append("\"noted\":").append(item != null && item.getNoted()).append(",");
			appendString(json, "name", item == null || item.getItemDef() == null ? null : item.getItemDef().getName());
			json.append("}");
		}
		json.append("]");
	}

	private static void appendBankPresets(StringBuilder json, CustomBankInterface bank) {
		json.append("\"presets\":[");
		for (int slot = 0; slot < bank.workbenchPresetCount(); slot++) {
			if (slot > 0) json.append(",");
			json.append("{\"slot\":").append(slot)
				.append(",\"empty\":").append(bank.workbenchPresetEmpty(slot)).append("}");
		}
		json.append("]");
	}

	private static StringBuilder appendPoint(StringBuilder json, String key, int x, int y) {
		json.append("\"").append(key).append("\":{\"x\":").append(x).append(",\"y\":").append(y).append("}");
		return json;
	}

	private static String auctionHouseScenarioJson() throws IOException {
		ArrayList<CaptureResult> captures = new ArrayList<>();
		clearWorkbenchBlockingUi();
		seedAuctionHouseFixture();
		sendCommand("quickauction");
		waitForAuctionHouseVisible();
		sleep(500);
		clearWorkbenchBlockingUi();
		sleep(300);

		captures.add(captureOnce("scenario-auction-browse"));
		clickAuctionTab(0);
		sleep(200);
		clickFirstAuctionRow();
		sleep(350);
		captures.add(captureOnce("scenario-auction-selection"));

		clickAuctionTab(1);
		waitForAuctionTab(1);
		sleep(250);
		captures.add(captureOnce("scenario-auction-my-auctions"));

		clickAuctionTab(2);
		waitForAuctionTab(2);
		sleep(250);
		captures.add(captureOnce("scenario-auction-intel"));

		clickAuctionTab(0);
		waitForAuctionTab(0);
		sleep(200);
		clickAuctionCategory(3);
		sleep(250);
		captures.add(captureOnce("scenario-auction-category-food"));

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"auction-house-open\",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		json.append("\"captures\":[");
		for (int i = 0; i < captures.size(); i++) {
			if (i > 0) json.append(",");
			CaptureResult capture = captures.get(i);
			json.append("{");
			json.append("\"reason\":\"").append(jsonEscape(capture.reason)).append("\",");
			json.append("\"pngPath\":\"").append(jsonEscape(capture.pngFile.getPath())).append("\",");
			json.append("\"statePath\":\"").append(jsonEscape(capture.jsonFile.getPath())).append("\",");
			json.append("\"width\":").append(capture.width).append(",");
			json.append("\"height\":").append(capture.height);
			json.append("}");
		}
		json.append("]}");
		return json.toString();
	}

	private static String devReadyJson() throws IOException {
		boolean loginRequested = false;
		requireClient();
		if (!isLoggedIn()) {
			loginRequested = true;
			clickSavedUserLogin();
			waitUntil(WorkbenchServer::isLoggedIn, 10000, "Saved-user login did not complete");
			sleep(500);
		}
		clearWorkbenchBlockingUi();
		sleep(250);
		return "{"
			+ "\"ok\":true,"
			+ "\"action\":\"dev-ready\","
			+ "\"loginRequested\":" + loginRequested + ","
			+ "\"generatedAt\":\"" + jsonEscape(isoTimestamp()) + "\","
			+ "\"state\":" + stateJson(null, -1, -1)
			+ "}";
	}

	private static String auctionHouseFixtureJson() throws IOException {
		seedAuctionHouseFixture();
		return "{"
			+ "\"ok\":true,"
			+ "\"fixture\":\"auction-house\","
			+ "\"command\":\"workbenchauctionfixture\","
			+ "\"generatedAt\":\"" + jsonEscape(isoTimestamp()) + "\","
			+ "\"state\":" + stateJson(null, -1, -1)
			+ "}";
	}

	private static String subscriptionVendorScenarioJson() throws IOException {
		ArrayList<CaptureResult> captures = new ArrayList<>();
		requireLoggedIn();
		clearWorkbenchBlockingUi();
		sendCommand("tele 126 649");
		sleep(1100);
		clearWorkbenchBlockingUi();
		waitForVisibleNpc(VOID_SUBSCRIPTION_VENDOR_NPC_ID);
		ORSCharacter vendor = findVisibleNpcById(VOID_SUBSCRIPTION_VENDOR_NPC_ID);
		if (vendor == null) {
			throw new IOException("Void Subscription Vendor is not visible after teleport");
		}

		captures.add(captureOnce("scenario-subscription-vendor-before"));
		int serverIndex = vendor.serverIndex;
		sendNpcCommand1(serverIndex);
		waitForSubscriptionVendorCheck();
		sleep(450);
		captures.add(captureOnce("scenario-subscription-vendor-after"));

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"subscription-vendor-claim\",");
		json.append("\"npcId\":").append(VOID_SUBSCRIPTION_VENDOR_NPC_ID).append(",");
		json.append("\"npcServerIndex\":").append(serverIndex).append(",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static String uiPanelsScenarioJson(String requestedPanels) throws IOException {
		ArrayList<CaptureResult> captures = new ArrayList<>();
		devReadyJson();
		String[] panels = parseUiPanelList(requestedPanels);
		for (String panel : panels) {
			openUiPanel(panel);
			captures.add(captureOnce("ui-panel-" + panel));
		}

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"ui-panels\",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static String subscriptionCardRedeemScenarioJson() throws IOException {
		ArrayList<CaptureResult> captures = new ArrayList<>();
		requireLoggedIn();
		clearWorkbenchBlockingUi();
		int slot = findInventorySlot(SUBSCRIPTION_CARD_ITEM_ID);
		if (slot < 0) {
			throw new IOException("No Subscription card is present in inventory");
		}

		captures.add(captureOnce("scenario-subscription-card-before"));
		sendInventoryCommand(slot, 0);
		waitUntil(() -> findInventorySlot(SUBSCRIPTION_CARD_ITEM_ID) < 0, 5000,
			"Subscription card was not consumed");
		sleep(450);
		captures.add(captureOnce("scenario-subscription-card-after"));

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"subscription-card-redeem\",");
		json.append("\"itemId\":").append(SUBSCRIPTION_CARD_ITEM_ID).append(",");
		json.append("\"slot\":").append(slot).append(",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static void appendSingleCapture(StringBuilder json, CaptureResult capture) {
		json.append("\"capture\":{");
		json.append("\"reason\":\"").append(jsonEscape(capture.reason)).append("\",");
		json.append("\"pngPath\":\"").append(jsonEscape(capture.pngFile.getPath())).append("\",");
		json.append("\"statePath\":\"").append(jsonEscape(capture.jsonFile.getPath())).append("\",");
		json.append("\"width\":").append(capture.width).append(",");
		json.append("\"height\":").append(capture.height);
		json.append("}");
	}

	private static void appendCaptures(StringBuilder json, ArrayList<CaptureResult> captures) {
		json.append("\"captures\":[");
		for (int i = 0; i < captures.size(); i++) {
			if (i > 0) json.append(",");
			CaptureResult capture = captures.get(i);
			json.append("{");
			json.append("\"reason\":\"").append(jsonEscape(capture.reason)).append("\",");
			json.append("\"pngPath\":\"").append(jsonEscape(capture.pngFile.getPath())).append("\",");
			json.append("\"statePath\":\"").append(jsonEscape(capture.jsonFile.getPath())).append("\",");
			json.append("\"width\":").append(capture.width).append(",");
			json.append("\"height\":").append(capture.height);
			json.append("}");
		}
		json.append("]");
	}

	private static void seedAuctionHouseFixture() throws IOException {
		requireLoggedInAdmin();
		sendCommand("workbenchauctionfixture");
		sleep(900);
	}

	private static String[] parseUiPanelList(String requestedPanels) {
		if (requestedPanels == null || requestedPanels.trim().isEmpty()) {
			return new String[]{
				"hud",
				"options-profile",
				"options-settings",
				"friends",
				"ignore",
				"magic",
				"prayers",
				"skills",
				"quests",
				"loot",
				"bestiary",
				"minimap",
				"inventory",
				"account"
			};
		}
		String[] raw = requestedPanels.split(",");
		ArrayList<String> panels = new ArrayList<>();
		for (String value : raw) {
			String panel = value == null ? "" : value.trim();
			if (!panel.isEmpty()) {
				panels.add(panel);
			}
		}
		return panels.toArray(new String[0]);
	}

	private static void openUiPanel(final String panel) throws IOException {
		final mudclient client = requireClient();
		runOnEdt(() -> {
			if (!client.workbenchOpenVoidscapeUiPanel(panel)) {
				throw new IOException("Unknown or unavailable UI panel: " + panel);
			}
		});
		sleep(isLootUiPanel(panel) ? 900 : 300);
	}

	private static boolean isLootUiPanel(String panel) {
		if (panel == null) return false;
		String key = panel.trim().toLowerCase(Locale.ROOT).replace('_', '-');
		return "loot".equals(key) || "bestiary".equals(key) || "best".equals(key);
	}

	private static void waitForAuctionHouseVisible() throws IOException {
		waitUntil(() -> {
			AuctionHouse auctionHouse = getAuctionHouse();
			return auctionHouse != null && auctionHouse.isVisible();
		}, 5000, "Auction House did not open");
	}

	private static void waitForVisibleNpc(final int npcId) throws IOException {
		waitUntil(() -> findVisibleNpcById(npcId) != null, 5000, "NPC " + npcId + " is not visible");
	}

	private static void waitForSubscriptionVendorCheck() throws IOException {
		sleep(1200);
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		if (client != null && client.getShowDialogShop()) {
			throw new IOException("Subscription Vendor opened a shop unexpectedly");
		}
	}

	private static void waitForAuctionTab(final int tab) throws IOException {
		waitUntil(() -> {
			AuctionHouse auctionHouse = getAuctionHouse();
			return auctionHouse != null && auctionHouse.isVisible() && auctionHouse.activeInterface == tab;
		}, 2500, "Auction House tab " + tab + " did not activate");
	}

	private static void waitUntil(Condition condition, long timeoutMs, String error) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			if (condition.isTrue()) return;
			sleep(100);
		}
		throw new IOException(error);
	}

	private static void clickAuctionTab(int tab) throws IOException {
		int[] origin = auctionHouseOrigin();
		int x = origin[0] + 42 + (tab * 82);
		int y = origin[1] + 24;
		clickGame(x, y, "left");
	}

	private static void clearWorkbenchBlockingUi() throws IOException {
		final mudclient client = requireClient();
		runOnEdt(() -> {
			client.setShowDialogServerMessage(false);
			client.setShowDialogMessage(false);
			client.setWelcomeScreenShown(false);
			client.setShowDialogBank(false);
			client.setShowDialogShop(false);
			AuctionHouse auctionHouse = client.getAuctionHouse();
			if (auctionHouse != null) {
				auctionHouse.setVisible(false);
			}
			if (client.worldMapPanel != null) {
				client.worldMapPanel.setVisible(false);
			}
		});
	}

	private static boolean isLoggedIn() {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		ORSCharacter player = client == null ? null : client.getLocalPlayer();
		return player != null && player.accountName != null && !player.accountName.trim().isEmpty();
	}

	private static void clickSavedUserLogin() throws IOException {
		mudclient client = requireClient();
		int centerX = client.getGameWidth() / 2;
		clickGame(centerX, 224, "left");
		sleep(350);
		clickGame(centerX - 46, 273, "left");
	}

	private static void clickAuctionCategory(int filter) throws IOException {
		int[] origin = auctionHouseOrigin();
		int railX = origin[0] + 3;
		int railY = origin[1] + 62;
		int tileX = railX + 5 + (filter % 2) * 38;
		int tileY = railY + 23 + (filter / 2) * 39;
		clickGame(tileX + 17, tileY + 18, "left");
	}

	private static void clickFirstAuctionRow() throws IOException {
		int[] origin = auctionHouseOrigin();
		clickGame(origin[0] + 290, origin[1] + 120, "left");
	}

	private static int[] auctionHouseOrigin() throws IOException {
		mudclient client = requireClient();
		int width = 490;
		int height = 326 - 47;
		return new int[]{(client.getGameWidth() - width) / 2, (client.getGameHeight() - height) / 2};
	}

	private static AuctionHouse getAuctionHouse() {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		return client == null ? null : client.getAuctionHouse();
	}

	private static void requireLoggedInAdmin() throws IOException {
		requireLoggedIn();
		mudclient client = requireClient();
		ORSCharacter player = client.getLocalPlayer();
		if (!player.isAdmin()) throw new IOException("Admin account required for Auction House fixture seeding");
	}

	private static void requireLoggedIn() throws IOException {
		mudclient client = requireClient();
		ORSCharacter player = client.getLocalPlayer();
		if (player == null) throw new IOException("Login required");
	}

	private static void clickGame(final int x, final int y, final String buttonName) throws IOException {
		final mudclient client = requireClient();
		final ORSCApplet applet = requireApplet();
		final int button = "right".equalsIgnoreCase(buttonName) ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
		final int modifiers = button == MouseEvent.BUTTON3 ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;

		runOnEdt(() -> {
			int eventX = x + client.screenOffsetX;
			int eventY = y + client.screenOffsetY;
			long now = System.currentTimeMillis();
			Component source = applet;
			ORSCApplet.MouseHandler mouseHandler = applet.getMouseHandler();
			mouseHandler.mouseMoved(new MouseEvent(source, MouseEvent.MOUSE_MOVED, now, 0, eventX, eventY, 0, false, MouseEvent.NOBUTTON));
			mouseHandler.mousePressed(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, now, modifiers, eventX, eventY, 1, false, button));
		});
		sleep(55);
		runOnEdt(() -> {
			int eventX = x + client.screenOffsetX;
			int eventY = y + client.screenOffsetY;
			long now = System.currentTimeMillis();
			Component source = applet;
			ORSCApplet.MouseHandler mouseHandler = applet.getMouseHandler();
			mouseHandler.mouseReleased(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, now, 0, eventX, eventY, 1, false, button));
			mouseHandler.mouseClicked(new MouseEvent(source, MouseEvent.MOUSE_CLICKED, now + 1, 0, eventX, eventY, 1, false, button));
		});
	}

	private static void dragGame(final int fromX, final int fromY, final int toX, final int toY,
		final String buttonName, int steps, int durationMs) throws IOException {
		final mudclient client = requireClient();
		final ORSCApplet applet = requireApplet();
		final int button = "right".equalsIgnoreCase(buttonName) ? MouseEvent.BUTTON3 : MouseEvent.BUTTON1;
		final int modifiers = button == MouseEvent.BUTTON3 ? InputEvent.BUTTON3_DOWN_MASK : InputEvent.BUTTON1_DOWN_MASK;
		final int safeSteps = Math.max(1, steps);
		final int delay = Math.max(15, durationMs / safeSteps);
		final Component source = applet;
		final ORSCApplet.MouseHandler mouseHandler = applet.getMouseHandler();

		runOnEdt(() -> {
			long now = System.currentTimeMillis();
			int eventX = fromX + client.screenOffsetX;
			int eventY = fromY + client.screenOffsetY;
			mouseHandler.mouseMoved(new MouseEvent(source, MouseEvent.MOUSE_MOVED, now, 0, eventX, eventY, 0, false, MouseEvent.NOBUTTON));
			mouseHandler.mousePressed(new MouseEvent(source, MouseEvent.MOUSE_PRESSED, now + 1, modifiers, eventX, eventY, 1, false, button));
		});

		for (int step = 1; step <= safeSteps; step++) {
			sleep(delay);
			final int eventX = fromX + (toX - fromX) * step / safeSteps + client.screenOffsetX;
			final int eventY = fromY + (toY - fromY) * step / safeSteps + client.screenOffsetY;
			runOnEdt(() -> {
				long now = System.currentTimeMillis();
				mouseHandler.mouseDragged(new MouseEvent(source, MouseEvent.MOUSE_DRAGGED, now, modifiers, eventX, eventY, 0, false, button));
			});
		}

		runOnEdt(() -> {
			long now = System.currentTimeMillis();
			int eventX = toX + client.screenOffsetX;
			int eventY = toY + client.screenOffsetY;
			mouseHandler.mouseReleased(new MouseEvent(source, MouseEvent.MOUSE_RELEASED, now, 0, eventX, eventY, 1, false, button));
		});
	}

	private static void scrollGame(final int x, final int y, final int amount) throws IOException {
		final mudclient client = requireClient();
		runOnEdt(() -> {
			client.mouseX = x;
			client.mouseY = y;
			client.runScroll(amount);
		});
		sleep(100);
	}

	private static void typeText(String text) throws IOException {
		for (int i = 0; i < text.length(); i++) {
			char character = text.charAt(i);
			if (character == '\n') {
				pressKey(KeyEvent.VK_ENTER, '\n');
			} else if (character == '\r') {
				pressKey(KeyEvent.VK_ENTER, '\r');
			} else {
				pressKey(KeyEvent.getExtendedKeyCodeForChar(character), character);
			}
			sleep(15);
		}
	}

	private static void pressKeyName(String keyName) throws IOException {
		String key = keyName.trim();
		if (key.length() == 1) {
			char character = key.charAt(0);
			pressKey(KeyEvent.getExtendedKeyCodeForChar(character), character);
			return;
		}

		String normalized = key.toUpperCase().replace('-', '_').replace(' ', '_');
		if ("ENTER".equals(normalized) || "RETURN".equals(normalized)) {
			pressKey(KeyEvent.VK_ENTER, '\n');
		} else if ("BACKSPACE".equals(normalized) || "BKSP".equals(normalized)) {
			pressKey(KeyEvent.VK_BACK_SPACE, '\b');
		} else if ("TAB".equals(normalized)) {
			pressKey(KeyEvent.VK_TAB, '\t');
		} else if ("ESC".equals(normalized) || "ESCAPE".equals(normalized)) {
			pressKey(KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED);
		} else if ("LEFT".equals(normalized)) {
			pressKey(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED);
		} else if ("RIGHT".equals(normalized)) {
			pressKey(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED);
		} else if ("UP".equals(normalized)) {
			pressKey(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED);
		} else if ("DOWN".equals(normalized)) {
			pressKey(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED);
		} else {
			pressKey(vkCode(normalized), KeyEvent.CHAR_UNDEFINED);
		}
	}

	private static int vkCode(String normalized) throws IOException {
		String fieldName = normalized.startsWith("VK_") ? normalized : "VK_" + normalized;
		try {
			Field field = KeyEvent.class.getField(fieldName);
			return field.getInt(null);
		} catch (NoSuchFieldException | IllegalAccessException e) {
			throw new IOException("Unknown key: " + normalized);
		}
	}

	private static void pressKey(final int keyCode, final char character) throws IOException {
		final ORSCApplet applet = requireApplet();
		runOnEdt(() -> {
			long now = System.currentTimeMillis();
			Component source = applet;
			ORSCApplet.KeyHandler keyHandler = applet.getKeyHandler();
			keyHandler.keyPressed(new KeyEvent(source, KeyEvent.KEY_PRESSED, now, 0, keyCode, character));
			keyHandler.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, now + 1, 0, keyCode, character));
		});
	}

	private static void sendCommand(String command) throws IOException {
		final mudclient client = requireClient();
		final String normalized = normalizeCommand(command);
		if (normalized.isEmpty()) throw new IOException("Command is empty");
		runOnEdt(() -> client.sendCommandString(normalized));
	}

	private static void sendNpcTalk(final int serverIndex) throws IOException {
		sendNpcPacket(serverIndex, 153);
	}

	private static void sendNpcCommand1(final int serverIndex) throws IOException {
		sendNpcPacket(serverIndex, 202);
	}

	private static void sendNpcCommand2(final int serverIndex) throws IOException {
		sendNpcPacket(serverIndex, 203);
	}

	private static void sendNpcPacket(final int serverIndex, final int opcode) throws IOException {
		final mudclient client = requireClient();
		if (client.packetHandler == null || client.packetHandler.getClientStream() == null) {
			throw new IOException("Client packet stream is not ready");
		}
		runOnEdt(() -> {
			client.packetHandler.getClientStream().newPacket(opcode);
			client.packetHandler.getClientStream().bufferBits.putShort(serverIndex);
			client.packetHandler.getClientStream().finishPacket();
		});
	}

	private static void sendInventoryCommand(final int slot, final int commandIndex) throws IOException {
		final mudclient client = requireClient();
		if (client.packetHandler == null || client.packetHandler.getClientStream() == null) {
			throw new IOException("Client packet stream is not ready");
		}
		runOnEdt(() -> {
			client.packetHandler.getClientStream().newPacket(90);
			client.packetHandler.getClientStream().bufferBits.putShort(slot);
			client.packetHandler.getClientStream().bufferBits.putInt(1);
			client.packetHandler.getClientStream().bufferBits.putByte(commandIndex);
			client.packetHandler.getClientStream().finishPacket();
		});
	}

	private static int findInventorySlot(final int itemId) {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		if (client == null) return -1;
		for (int slot = 0; slot < client.getInventoryItemCount(); slot++) {
			if (client.getInventoryItemID(slot) == itemId) {
				return slot;
			}
		}
		return -1;
	}

	private static ORSCharacter findVisibleNpcById(int npcId) {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		if (client == null) return null;
		for (int i = 0; i < client.getNpcCount(); i++) {
			ORSCharacter npc = client.getNpc(i);
			if (npc != null && npc.npcId == npcId) {
				return npc;
			}
		}
		return null;
	}

	private static ORSCharacter findVisibleNpcByServerIndex(int serverIndex) {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		if (client == null) return null;
		for (int i = 0; i < client.getNpcCount(); i++) {
			ORSCharacter npc = client.getNpc(i);
			if (npc != null && npc.serverIndex == serverIndex) {
				return npc;
			}
		}
		return null;
	}

	private static ORSCharacter findWorkbenchNpc(Map<String, String> fields) throws IOException {
		if (fields.containsKey("serverIndex")) {
			return findVisibleNpcByServerIndex(requiredInt(fields, "serverIndex"));
		}
		if (fields.containsKey("npcId")) {
			return findVisibleNpcById(requiredInt(fields, "npcId"));
		}
		if (fields.containsKey("id")) {
			return findVisibleNpcById(requiredInt(fields, "id"));
		}
		throw new IOException("Missing required field: npcId or serverIndex");
	}

	private static String normalizeCommand(String command) {
		if (command == null) return "";
		String normalized = command.trim();
		while (normalized.startsWith(":")) normalized = normalized.substring(1);
		return normalized.trim();
	}

	private static mudclient requireClient() throws IOException {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		if (client == null) throw new IOException("Client is not initialized");
		return client;
	}

	private static ORSCApplet requireApplet() throws IOException {
		ORSCApplet applet = ORSCApplet.getAppletForWorkbench();
		if (applet == null) throw new IOException("Applet is not initialized");
		if (applet.getMouseHandler() == null || applet.getKeyHandler() == null) {
			throw new IOException("Client input handlers are not initialized");
		}
		return applet;
	}

	private static void runOnEdt(EdtAction action) throws IOException {
		if (SwingUtilities.isEventDispatchThread()) {
			action.run();
			return;
		}

		final IOException[] ioError = new IOException[1];
		final RuntimeException[] runtimeError = new RuntimeException[1];
		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					action.run();
				} catch (IOException e) {
					ioError[0] = e;
				} catch (RuntimeException e) {
					runtimeError[0] = e;
				}
			});
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting for client input", e);
		} catch (InvocationTargetException e) {
			throw new IOException("Client input failed", e.getCause());
		}

		if (ioError[0] != null) throw ioError[0];
		if (runtimeError[0] != null) throw runtimeError[0];
	}

	private static Map<String, String> requestFields(HttpExchange exchange) throws IOException {
		LinkedHashMap<String, String> fields = new LinkedHashMap<>();
		parseQuery(exchange.getRequestURI().getRawQuery(), fields);
		String body = readBody(exchange);
		if (!body.trim().isEmpty()) {
			if (body.trim().startsWith("{")) {
				fields.putAll(parseFlatJsonObject(body));
			} else {
				parseQuery(body, fields);
			}
		}
		return fields;
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read;
		while ((read = exchange.getRequestBody().read(buffer)) != -1) {
			bytes.write(buffer, 0, read);
		}
		return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
	}

	private static void parseQuery(String query, Map<String, String> fields) throws IOException {
		if (query == null || query.isEmpty()) return;
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			if (pair.isEmpty()) continue;
			int equals = pair.indexOf('=');
			String key = equals >= 0 ? pair.substring(0, equals) : pair;
			String value = equals >= 0 ? pair.substring(equals + 1) : "";
			fields.put(urlDecode(key), urlDecode(value));
		}
	}

	private static Map<String, String> parseFlatJsonObject(String json) throws IOException {
		LinkedHashMap<String, String> fields = new LinkedHashMap<>();
		String trimmed = json.trim();
		if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
			throw new IOException("Expected a flat JSON object");
		}

		int index = 1;
		while (index < trimmed.length() - 1) {
			index = skipWhitespaceAndCommas(trimmed, index);
			if (index >= trimmed.length() - 1) break;
			ParseString key = parseJsonString(trimmed, index);
			index = skipWhitespace(trimmed, key.nextIndex);
			if (index >= trimmed.length() || trimmed.charAt(index) != ':') throw new IOException("Expected ':' after JSON key");
			index = skipWhitespace(trimmed, index + 1);
			ParseValue value = parseJsonValue(trimmed, index);
			fields.put(key.value, value.value);
			index = value.nextIndex;
		}
		return fields;
	}

	private static ParseValue parseJsonValue(String json, int index) throws IOException {
		if (index < json.length() && json.charAt(index) == '"') {
			ParseString string = parseJsonString(json, index);
			return new ParseValue(string.value, string.nextIndex);
		}

		int start = index;
		while (index < json.length()) {
			char c = json.charAt(index);
			if (c == ',' || c == '}') break;
			index++;
		}
		return new ParseValue(json.substring(start, index).trim(), index);
	}

	private static ParseString parseJsonString(String json, int index) throws IOException {
		if (index >= json.length() || json.charAt(index) != '"') throw new IOException("Expected JSON string");
		StringBuilder value = new StringBuilder();
		index++;
		while (index < json.length()) {
			char c = json.charAt(index++);
			if (c == '"') return new ParseString(value.toString(), index);
			if (c == '\\') {
				if (index >= json.length()) throw new IOException("Invalid JSON escape");
				char escaped = json.charAt(index++);
				switch (escaped) {
					case '"':
					case '\\':
					case '/':
						value.append(escaped);
						break;
					case 'b':
						value.append('\b');
						break;
					case 'f':
						value.append('\f');
						break;
					case 'n':
						value.append('\n');
						break;
					case 'r':
						value.append('\r');
						break;
					case 't':
						value.append('\t');
						break;
					case 'u':
						if (index + 4 > json.length()) throw new IOException("Invalid unicode escape");
						value.append((char) Integer.parseInt(json.substring(index, index + 4), 16));
						index += 4;
						break;
					default:
						throw new IOException("Unsupported JSON escape: " + escaped);
				}
			} else {
				value.append(c);
			}
		}
		throw new IOException("Unterminated JSON string");
	}

	private static int skipWhitespaceAndCommas(String value, int index) {
		while (index < value.length()) {
			char c = value.charAt(index);
			if (c != ',' && !Character.isWhitespace(c)) break;
			index++;
		}
		return index;
	}

	private static int skipWhitespace(String value, int index) {
		while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
		return index;
	}

	private static String urlDecode(String value) throws IOException {
		return URLDecoder.decode(value, "UTF-8");
	}

	private static String requiredString(Map<String, String> fields, String key) throws IOException {
		String value = fields.get(key);
		if (value == null) throw new IOException("Missing required field: " + key);
		return value;
	}

	private static int requiredInt(Map<String, String> fields, String key) throws IOException {
		try {
			return Integer.parseInt(requiredString(fields, key));
		} catch (NumberFormatException e) {
			throw new IOException("Expected integer field: " + key);
		}
	}

	private static int parseWorldReskinMode(String mode) throws IOException {
		if (mode == null || mode.trim().isEmpty() || "auto".equalsIgnoreCase(mode.trim())) {
			return Config.WORLD_RESKIN_AUTO;
		}
		String normalized = mode.trim().toLowerCase(Locale.ROOT);
		if ("authentic".equals(normalized) || "classic".equals(normalized)) {
			return Config.WORLD_RESKIN_AUTHENTIC;
		}
		if ("void".equals(normalized)) {
			return Config.WORLD_RESKIN_VOID;
		}
		try {
			int value = Integer.parseInt(normalized);
			if (value >= Config.WORLD_RESKIN_AUTO && value <= Config.WORLD_RESKIN_VOID) {
				return value;
			}
		} catch (NumberFormatException ignored) {
		}
		throw new IOException("Unknown world reskin mode: " + mode);
	}

	private static void sleep(long millis) throws IOException {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted", e);
		}
	}

	private static String auctionHouseTabName(int activeInterface) {
		switch (activeInterface) {
			case 0:
				return "browse";
			case 1:
				return "myAuctions";
			case 2:
				return "intel";
			default:
				return "unknown";
		}
	}

	private static String sanitizeReason(String reason) {
		if (reason == null || reason.trim().isEmpty()) return "capture";
		String sanitized = reason.replaceAll("[^A-Za-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
		return sanitized.isEmpty() ? "capture" : sanitized;
	}

	private static File workbenchDir() {
		return new File(System.getProperty(DIR_PROPERTY, DEFAULT_DIR));
	}

	private static String fileTimestamp() {
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS");
		format.setTimeZone(TimeZone.getDefault());
		return format.format(new Date());
	}

	private static String isoTimestamp() {
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
		format.setTimeZone(TimeZone.getDefault());
		return format.format(new Date());
	}

	private static StringBuilder appendString(StringBuilder json, String key, String value) {
		json.append("\"").append(key).append("\":");
		if (value == null) {
			json.append("null");
		} else {
			json.append("\"").append(jsonEscape(value)).append("\"");
		}
		return json;
	}

	private static String jsonEscape(String value) {
		if (value == null) return "";

		StringBuilder escaped = new StringBuilder(value.length() + 8);
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					escaped.append("\\\"");
					break;
				case '\\':
					escaped.append("\\\\");
					break;
				case '\b':
					escaped.append("\\b");
					break;
				case '\f':
					escaped.append("\\f");
					break;
				case '\n':
					escaped.append("\\n");
					break;
				case '\r':
					escaped.append("\\r");
					break;
				case '\t':
					escaped.append("\\t");
					break;
				default:
					if (c < 0x20) {
						escaped.append(String.format("\\u%04x", (int) c));
					} else {
						escaped.append(c);
					}
			}
		}
		return escaped.toString();
	}

	private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
		byte[] body = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream stream = exchange.getResponseBody()) {
			stream.write(body);
		}
	}

	static final class CaptureResult {
		final String reason;
		final File pngFile;
		final File jsonFile;
		final int width;
		final int height;

		private CaptureResult(String reason, File pngFile, File jsonFile, int width, int height) {
			this.reason = reason;
			this.pngFile = pngFile;
			this.jsonFile = jsonFile;
			this.width = width;
			this.height = height;
		}
	}

	private interface Condition {
		boolean isTrue();
	}

	private interface EdtAction {
		void run() throws IOException;
	}

	private static final class ParseString {
		final String value;
		final int nextIndex;

		ParseString(String value, int nextIndex) {
			this.value = value;
			this.nextIndex = nextIndex;
		}
	}

	private static final class ParseValue {
		final String value;
		final int nextIndex;

		ParseValue(String value, int nextIndex) {
			this.value = value;
			this.nextIndex = nextIndex;
		}
	}
}
