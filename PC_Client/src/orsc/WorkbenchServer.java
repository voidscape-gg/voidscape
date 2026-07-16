package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.GeneratedAppearanceRegistry;
import com.openrsc.client.entityhandling.defs.extras.AnimationDef;
import com.openrsc.client.entityhandling.instances.Item;
import com.openrsc.client.model.Sprite;
import com.openrsc.interfaces.misc.AuctionHouse;
import com.openrsc.interfaces.misc.CustomBankInterface;
import com.openrsc.interfaces.misc.DuelJournalInterface;
import com.openrsc.interfaces.misc.PkCatchingInterface;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import orsc.appearance.v2.PaperdollV2BaseProfile;
import orsc.appearance.v2.PaperdollV2LegacyCompatibility;
import orsc.appearance.v2.PaperdollV2Pack;
import orsc.appearance.v2.PaperdollV2Palette;
import orsc.appearance.v2.PaperdollV2Pose;
import orsc.appearance.v2.PaperdollV2Runtime;
import orsc.appearance.v2.PaperdollV2SelectorRegistry;
import orsc.enumerations.MessageTab;
import orsc.enumerations.MessageType;
import orsc.enumerations.ORSCharacterDirection;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
	private static final int COWBOY_HAT_APPEARANCE_ID = 245;
	private static final int PLAYER_HAT_LAYER = 5;
	private static final int PLAYER_WALK_FRAME_TICKS = 6;
	private static final int APPEARANCE_RASTER_WIDTH = 88;
	private static final int APPEARANCE_RASTER_HEIGHT = 112;
	private static final int APPEARANCE_DRAW_X = 12;
	private static final int APPEARANCE_DRAW_Y = 2;
	private static final int APPEARANCE_DRAW_WIDTH = 64;
	private static final int APPEARANCE_DRAW_HEIGHT = 102;
	private static final int APPEARANCE_RASTER_BACKGROUND = 0x121820;
	private static final int APPEARANCE_RASTER_PROBE_BACKGROUND = 0xd7c9b8;
	private static final int PAPERDOLL_V2_BACKGROUND = 0x121820;
	private static final int PAPERDOLL_V2_PRIMARY = 0x244866;
	private static final int PAPERDOLL_V2_SECONDARY = 0xd1ad53;
	private static final int PAPERDOLL_V2_SHIELD_APPEARANCE_ID = 98;
	private static final int PAPERDOLL_V2_WEAPON_APPEARANCE_ID = 48;
	private static final int PAPERDOLL_V2_HAIR_COLOUR_INDEX = 17;
	private static final int PAPERDOLL_V2_BENCHMARK_WARMUP_RENDERS = 60;
	private static final int PAPERDOLL_V2_BENCHMARK_MINIMUM_RENDERS = 600;
	private static final ORSCharacterDirection[] APPEARANCE_WALK_DIRECTIONS = {
		ORSCharacterDirection.NORTH,
		ORSCharacterDirection.NORTH_WEST,
		ORSCharacterDirection.WEST,
		ORSCharacterDirection.SOUTH_WEST,
		ORSCharacterDirection.SOUTH,
		ORSCharacterDirection.SOUTH_EAST,
		ORSCharacterDirection.EAST,
		ORSCharacterDirection.NORTH_EAST
	};
	private static final ORSCharacterDirection[] APPEARANCE_COMBAT_DIRECTIONS = {
		ORSCharacterDirection.COMBAT_A,
		ORSCharacterDirection.COMBAT_B
	};

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
			httpServer.createContext("/dev/paperdoll-v2-stack", WorkbenchServer::handlePaperdollV2Stack);
			httpServer.createContext("/fixture/auction-house", WorkbenchServer::handleAuctionHouseFixture);
			httpServer.createContext("/fixture/christmas-cracker", WorkbenchServer::handleChristmasCrackerFixture);
			httpServer.createContext("/fixture/duel-journal", WorkbenchServer::handleDuelJournalFixture);
			httpServer.createContext("/fixture/pk-catching", WorkbenchServer::handlePkCatchingFixture);
			httpServer.createContext("/scenario/auction-house-open", WorkbenchServer::handleAuctionHouseScenario);
			httpServer.createContext("/scenario/christmas-cracker-roll", WorkbenchServer::handleChristmasCrackerScenario);
			httpServer.createContext("/scenario/pk-catching-ui", WorkbenchServer::handlePkCatchingScenario);
			httpServer.createContext("/scenario/ui-panels", WorkbenchServer::handleUiPanelsScenario);
			httpServer.createContext("/scenario/subscription-vendor-claim", WorkbenchServer::handleSubscriptionVendorScenario);
			httpServer.createContext("/scenario/subscription-card-redeem", WorkbenchServer::handleSubscriptionCardRedeemScenario);
			httpServer.createContext("/scenario/cowboy-hat-frames", WorkbenchServer::handleCowboyHatFramesScenario);
			httpServer.createContext("/scenario/appearance-frames", WorkbenchServer::handleAppearanceFramesScenario);
			httpServer.createContext("/scenario/paperdoll-v2-frames",
				WorkbenchServer::handlePaperdollV2FramesScenario);
			httpServer.createContext("/scenario/paperdoll-v2-live-scene",
				WorkbenchServer::handlePaperdollV2LiveSceneScenario);
			httpServer.createContext("/scenario/paperdoll-v2-benchmark",
				WorkbenchServer::handlePaperdollV2BenchmarkScenario);
			httpServer.createContext("/scenario/paperdoll-v2-selector-resolution",
				WorkbenchServer::handlePaperdollV2SelectorResolutionScenario);
			httpServer.createContext("/scenario/paperdoll-v2-runtime-matrix",
				WorkbenchServer::handlePaperdollV2RuntimeMatrixScenario);
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

	private static void handleChristmasCrackerFixture(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, christmasCrackerFixtureJson(fields));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleDuelJournalFixture(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, duelJournalFixtureJson(fields));
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePkCatchingFixture(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, pkCatchingFixtureJson(fields));
		} catch (IOException | IllegalArgumentException e) {
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

	private static void handleChristmasCrackerScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, christmasCrackerScenarioJson(fields));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePkCatchingScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, pkCatchingScenarioJson());
		} catch (IOException | IllegalArgumentException e) {
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

	private static void handleCowboyHatFramesScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, appearanceFramesScenarioJson(COWBOY_HAT_APPEARANCE_ID));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handleAppearanceFramesScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, appearanceFramesScenarioJson(requiredInt(fields, "appearanceId")));
		} catch (IOException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2FramesScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		if (!Boolean.getBoolean(ENABLED_PROPERTY)) {
			sendJson(exchange, 403,
				"{\"ok\":false,\"error\":\"Paperdoll V2 QA requires voidscape.workbench\"}");
			return;
		}
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, paperdollV2FramesScenarioJson(fields));
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2LiveSceneScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, paperdollV2LiveSceneScenarioJson(fields));
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2BenchmarkScenario(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, paperdollV2BenchmarkScenarioJson(fields));
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\"" + jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2SelectorResolutionScenario(HttpExchange exchange)
		throws IOException {
		if (!requirePost(exchange)) return;
		try {
			sendJson(exchange, 200, paperdollV2SelectorResolutionScenarioJson());
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\""
				+ jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2RuntimeMatrixScenario(HttpExchange exchange)
		throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			sendJson(exchange, 200, paperdollV2RuntimeMatrixScenarioJson(fields));
		} catch (IOException | IllegalArgumentException e) {
			sendJson(exchange, 503, "{\"ok\":false,\"error\":\""
				+ jsonEscape(e.getMessage()) + "\"}");
		}
	}

	private static void handlePaperdollV2Stack(HttpExchange exchange) throws IOException {
		if (!requirePost(exchange)) return;
		Map<String, String> fields = requestFields(exchange);
		try {
			final mudclient client = requireClient();
			final String stackId = requiredString(fields, "stackId").trim();
			runOnEdt(() -> client.workbenchSelectPaperdollV2Stack(stackId));
			StringBuilder json = new StringBuilder("{\"ok\":true,");
			appendString(json, "action", "paperdoll-v2-stack").append(",");
			appendString(json, "selectedStackId", stackId).append(",");
			appendString(json, "generatedAt", isoTimestamp()).append(",");
			json.append("\"state\":").append(stateJson(null, -1, -1)).append("}");
			sendJson(exchange, 200, json.toString());
		} catch (IOException | IllegalArgumentException e) {
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
			appendPaperdollV2Runtime(json, client);
			json.append(",");
			appendMouse(json, client);
		json.append(",");
		appendPlayer(json, client);
		json.append(",");
		appendVisiblePlayers(json, client);
		json.append(",");
		appendHud(json, client);
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
			json.append("\"loginScreenNumber\":").append(client.getLoginScreenNumber()).append(",");
			json.append("\"width\":").append(client.getGameWidth()).append(",");
			json.append("\"height\":").append(client.getGameHeight()).append(",");
			json.append("\"cameraRotation\":").append(client.cameraRotation).append(",");
			json.append("\"cameraAngle\":").append(client.workbenchCameraAngle()).append(",");
			json.append("\"cameraZoom\":").append(client.cameraZoom).append(",");
			json.append("\"overheadPlayerLabelMode\":")
				.append(client.workbenchOverheadPlayerLabelMode()).append(",");
			appendString(json, "inputXAction", client.inputX_Action == null ? "" : client.inputX_Action.name()).append(",");
			appendString(json, "inputTextCurrent", client.inputTextCurrent).append(",");
			appendString(json, "loginExistingUser", client.getWebLoginUserText()).append(",");
			json.append("\"loginExistingPasswordLength\":").append(client.getWebLoginPasswordLength()).append(",");
			appendString(json, "loginStatus1", client.getWebLoginStatus1Text()).append(",");
			appendString(json, "loginStatus2", client.getWebLoginStatus2Text()).append(",");
			json.append("\"worldReskinMode\":").append(client.workbenchWorldReskinMode()).append(",");
			appendString(json, "worldReskinModeName", client.workbenchWorldReskinModeName()).append(",");
			json.append("\"worldReskinActive\":").append(client.workbenchVoidWorldReskinActive()).append(",");
			appendVoidRiftTarget(json, client);
			json.append(",");
			appendLoginTargets(json, client);
		}
		json.append("}");
	}

	private static void appendPaperdollV2Runtime(StringBuilder json, mudclient client) {
		json.append("\"paperdollV2\":");
		if (client == null) {
			json.append("null");
			return;
		}
		PaperdollV2Runtime runtime = client.workbenchPaperdollV2Runtime();
		if (runtime == null) {
			json.append("null");
			return;
		}
		PaperdollV2Pack pack = runtime.getPack();
		long v2Count = runtime.getV2RenderCount();
		long legacyCount = runtime.getLegacyFallbackRenderCount();
		json.append("{");
		json.append("\"requested\":").append(runtime.isRequestedByUser()).append(",");
		json.append("\"packValid\":").append(runtime.isPackValid()).append(",");
		json.append("\"active\":").append(runtime.isActive()).append(",");
		json.append("\"selectorModeRequested\":")
			.append(runtime.isSelectorModeRequested()).append(",");
		json.append("\"selectorModeValid\":").append(runtime.isSelectorModeValid()).append(",");
		json.append("\"selectorModeActive\":").append(runtime.isSelectorModeActive()).append(",");
		json.append("\"forceLegacy\":").append(runtime.isForceLegacy()).append(",");
		json.append("\"designerEvaluationEnabled\":")
			.append(runtime.isDesignerEvaluationEnabled()).append(",");
		appendString(json, "fallbackReason", runtime.getFallbackReason()).append(",");
		appendString(json, "lastRenderFallbackReason", runtime.getLastRenderFallbackReason()).append(",");
		appendString(json, "packPath", runtime.getPackPath()).append(",");
		appendString(json, "selectorRegistryPath", runtime.getSelectorRegistryPath()).append(",");
		appendString(json, "stackId", runtime.getSelectedStackId()).append(",");
		appendString(json, "configuredStackId", runtime.getConfiguredStackId()).append(",");
		appendString(json, "selectedStackId", runtime.getSelectedStackId()).append(",");
		json.append("\"mutableStackSelectionEnabled\":")
			.append(runtime.isDevStackSelectionEnabled()).append(",");
		json.append("\"stackSelectionRevision\":").append(runtime.getStackSelectionRevision()).append(",");
		PaperdollV2BaseProfile baseProfile = runtime.getBaseProfile();
		appendString(json, "baseProfile", baseProfile == null ? "" : baseProfile.getId()).append(",");
		json.append("\"compatibleBaseAppearances\":");
		if (baseProfile == null) {
			json.append("null,");
		} else {
			json.append("{\"head\":").append(baseProfile.getHeadAppearanceId())
				.append(",\"body\":").append(baseProfile.getBodyAppearanceId())
				.append(",\"legs\":").append(baseProfile.getLegsAppearanceId()).append("},");
		}
		appendIntArray(json, "nativePaperdollSlots", runtime.getStack() == null
			? new int[0] : runtime.getStack().getNativePaperdollSlots()).append(",");
		appendStringList(json, "availableLiveStackIds", runtime.getAvailableLiveStackIds()).append(",");
		appendPaperdollV2AvailableLiveStacks(json, runtime).append(",");
		appendPaperdollV2SelectorRegistry(json, runtime.getSelectorRegistry()).append(",");
		appendPaperdollV2LegacyCompatibility(json, runtime.getLegacyCompatibility()).append(",");
		appendString(json, "hatPolicy",
			"any-nonzero-hat-whole-player-legacy; native-slot-5-disabled-until-bound").append(",");
		json.append("\"targetViewportWidth\":").append(PaperdollV2Runtime.VIEWPORT_WIDTH).append(",");
		json.append("\"targetViewportHeight\":").append(PaperdollV2Runtime.VIEWPORT_HEIGHT).append(",");
		json.append("\"targetGameHeight\":").append(PaperdollV2Runtime.GAME_HEIGHT).append(",");
		json.append("\"projectionShift\":").append(client.workbenchPaperdollV2ProjectionShift()).append(",");
		json.append("\"exactTargetViewport\":")
			.append(client.getGameWidth() == PaperdollV2Runtime.VIEWPORT_WIDTH
				&& client.getGameHeight() == PaperdollV2Runtime.GAME_HEIGHT).append(",");
		json.append("\"pack\":");
		if (pack == null) {
			json.append("null");
		} else {
			json.append("{");
			appendString(json, "archiveSha256", pack.getArchiveSha256()).append(",");
			appendString(json, "registrySha256", pack.getRegistrySha256()).append(",");
			appendString(json, "templateSha256", pack.getTemplateSha256()).append(",");
			appendString(json, "derivedMasksSha256", pack.getDerivedMasksSha256()).append(",");
			appendString(json, "sourceV1Sha256", pack.getSourceV1Sha256()).append(",");
			json.append("\"archiveBytes\":").append(pack.getArchiveByteCount()).append(",");
			json.append("\"decodedArgbBytes\":").append(pack.getDecodedArgbByteCount()).append(",");
			json.append("\"spriteCount\":").append(pack.getSpriteCount());
			json.append("}");
		}
		json.append(",\"memory\":{");
		json.append("\"heapUsedBeforeLoad\":").append(runtime.getHeapUsedBeforeLoad()).append(",");
		json.append("\"heapUsedAfterLoad\":").append(runtime.getHeapUsedAfterLoad()).append(",");
		json.append("\"heapDeltaAtLoad\":").append(runtime.getHeapDeltaAtLoad()).append(",");
		json.append("\"surfacePixelBytes\":").append(client.workbenchSurfacePixelBytes()).append(",");
		Runtime jvm = Runtime.getRuntime();
		json.append("\"heapUsedNow\":").append(jvm.totalMemory() - jvm.freeMemory());
		json.append("},\"telemetry\":{");
		json.append("\"loadNanos\":").append(runtime.getLoadNanos()).append(",");
		json.append("\"v2RenderCount\":").append(v2Count).append(",");
		json.append("\"legacyFallbackRenderCount\":").append(legacyCount).append(",");
		json.append("\"v2RenderNanos\":").append(runtime.getV2RenderNanos()).append(",");
		json.append("\"legacyFallbackRenderNanos\":").append(runtime.getLegacyFallbackRenderNanos()).append(",");
		json.append("\"v2AverageRenderNanos\":")
			.append(v2Count == 0 ? 0 : runtime.getV2RenderNanos() / v2Count).append(",");
		json.append("\"legacyAverageRenderNanos\":")
			.append(legacyCount == 0 ? 0 : runtime.getLegacyFallbackRenderNanos() / legacyCount)
			.append(",\"localActor\":{");
		long localV2Count = runtime.getLocalV2RenderCount();
		long localLegacyCount = runtime.getLocalLegacyFallbackRenderCount();
		json.append("\"v2RenderCount\":").append(localV2Count).append(",")
			.append("\"legacyFallbackRenderCount\":").append(localLegacyCount).append(",")
			.append("\"v2RenderNanos\":").append(runtime.getLocalV2RenderNanos()).append(",")
			.append("\"legacyFallbackRenderNanos\":")
			.append(runtime.getLocalLegacyFallbackRenderNanos()).append("},\"remoteActors\":{");
		json.append("\"v2RenderCount\":").append(runtime.getRemoteV2RenderCount()).append(",")
			.append("\"legacyFallbackRenderCount\":")
			.append(runtime.getRemoteLegacyFallbackRenderCount()).append(",")
			.append("\"v2RenderNanos\":").append(runtime.getRemoteV2RenderNanos()).append(",")
			.append("\"legacyFallbackRenderNanos\":")
			.append(runtime.getRemoteLegacyFallbackRenderNanos()).append("}");
		json.append("}}");
	}

	private static StringBuilder appendPaperdollV2LegacyCompatibility(StringBuilder json,
		PaperdollV2LegacyCompatibility compatibility) {
		json.append("\"legacyCompatibility\":");
		if (compatibility == null) {
			json.append("null");
			return json;
		}
		json.append("{");
		json.append("\"rootValid\":").append(compatibility.isRootValid()).append(",");
		json.append("\"usable\":")
			.append(compatibility.isRootValid() && compatibility.getLoadedStyleCount() > 0)
			.append(",");
		appendString(json, "rootReason", compatibility.getRootReason()).append(",");
		appendString(json, "propertiesPath", compatibility.getPropertiesFile() == null
			? "" : compatibility.getPropertiesFile().getAbsolutePath()).append(",");
		appendString(json, "propertiesSha256", compatibility.getPropertiesSha256()).append(",");
		appendString(json, "manifestSha256", compatibility.getManifestSha256()).append(",");
		appendString(json, "expectedPackSha256", compatibility.getExpectedPackSha256()).append(",");
		json.append("\"compatibleHeadAppearanceId\":")
			.append(PaperdollV2LegacyCompatibility.COMPATIBLE_HEAD_APPEARANCE_ID).append(",");
		json.append("\"hatAllowedAppearanceIds\":[0],");
		appendString(json, "styleFailurePolicy", "reject-whole-style").append(",");
		json.append("\"loadedStyleCount\":").append(compatibility.getLoadedStyleCount())
			.append(",\"maximumSelectorId\":").append(compatibility.getMaximumSelectorId())
			.append(",\"styles\":[");
		for (int selectorId = 1; selectorId <= compatibility.getMaximumSelectorId(); selectorId++) {
			if (selectorId > 1) json.append(",");
			json.append("{\"selectorId\":").append(selectorId).append(",");
			appendString(json, "style", compatibility.getStyle(selectorId)).append(",");
			json.append("\"loaded\":").append(compatibility.hasStyle(selectorId)).append(",");
			appendString(json, "rejectionReason",
				compatibility.getStyleRejectionReason(selectorId));
			json.append("}");
		}
		json.append("]}");
		return json;
	}

	private static void appendVoidRiftTarget(StringBuilder json, mudclient client) {
		json.append("\"voidRiftTarget\":");
		int[] target = client.workbenchNearestVoidRiftTarget();
		if (target == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"screenX\":").append(target[0]).append(",");
		json.append("\"screenY\":").append(target[1]).append(",");
		json.append("\"worldX\":").append(target[2]).append(",");
		json.append("\"worldZ\":").append(target[3]);
		json.append("}");
	}

	private static void appendLoginTargets(StringBuilder json, mudclient client) {
		json.append("\"loginTargets\":{");
		appendPoint(json, "homeNew", client.workbenchLoginHomeNewUserX(), client.workbenchLoginHomeNewUserY()).append(",");
		appendPoint(json, "homeExisting", client.workbenchLoginHomeExistingUserX(), client.workbenchLoginHomeExistingUserY()).append(",");
		appendPoint(json, "existingOk", client.workbenchExistingUserOkX(), client.workbenchExistingUserOkY()).append(",");
		appendPoint(json, "newUser", client.workbenchNewUserX(), client.workbenchNewUserY()).append(",");
		appendPoint(json, "newPassword", client.workbenchNewPasswordX(), client.workbenchNewPasswordY()).append(",");
		appendPoint(json, "newConfirm", client.workbenchNewConfirmX(), client.workbenchNewConfirmY()).append(",");
		appendPoint(json, "newSubmit", client.workbenchNewSubmitX(), client.workbenchNewSubmitY());
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
		json.append("\"rendererFrameCounter\":").append(client.getFrameCounter()).append(",");
		appendString(json, "direction", player.direction == null ? null : player.direction.name()).append(",");
		json.append("\"animationNext\":").append(player.animationNext).append(",");
		json.append("\"stepFrame\":").append(player.stepFrame).append(",");
		json.append("\"hairStyle\":").append(player.hairStyle).append(",");
		appendString(json, "title", player.title).append(",");
		json.append("\"titleTier\":").append(player.titleTier).append(",");
		appendString(json, "honorific", player.honorific).append(",");
		json.append("\"honorificTier\":").append(player.honorificTier).append(",");
		appendIntArray(json, "layerAnimation", player.layerAnimation).append(",");
		json.append("\"hatAppearanceId\":")
			.append(player.layerAnimation.length > PLAYER_HAT_LAYER ? player.layerAnimation[PLAYER_HAT_LAYER] : -1)
			.append(",");
		json.append("\"level\":").append(player.level).append(",");
		json.append("\"healthCurrent\":").append(player.healthCurrent).append(",");
		json.append("\"healthMax\":").append(player.healthMax).append(",");
		json.append("\"groupId\":").append(player.groupID).append(",");
		json.append("\"admin\":").append(player.isAdmin()).append(",");
		json.append("\"dev\":").append(Group.isServerDeveloper(player.groupID)).append(",");
		json.append("\"mod\":").append(player.isMod());
		json.append("}");
	}

	private static void appendVisiblePlayers(StringBuilder json, mudclient client) {
		json.append("\"visiblePlayers\":[");
		if (client != null) {
			ORSCharacter local = client.getLocalPlayer();
			for (int index = 0; index < client.getPlayerCount(); index++) {
				ORSCharacter player = client.getPlayer(index);
				if (player == null) continue;
				if (json.charAt(json.length() - 1) != '[') json.append(',');
				json.append("{\"index\":").append(index).append(",");
				json.append("\"serverIndex\":").append(player.serverIndex).append(",");
				appendString(json, "displayName", player.displayName).append(",");
				appendString(json, "accountName", player.accountName).append(",");
				json.append("\"local\":").append(player == local).append(",");
				json.append("\"hairStyle\":").append(player.hairStyle).append(",");
				appendString(json, "title", player.title).append(",");
				json.append("\"titleTier\":").append(player.titleTier).append(",");
				appendString(json, "honorific", player.honorific).append(",");
				json.append("\"honorificTier\":").append(player.honorificTier).append(",");
				appendIntArray(json, "layerAnimation", player.layerAnimation);
				json.append('}');
			}
		}
		json.append(']');
	}

	private static void appendHud(StringBuilder json, mudclient client) {
		json.append("\"hud\":");
		if (client == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"locationPlaqueEnabled\":")
			.append(client.workbenchLocationPlaqueEnabled()).append(",");
		json.append("\"locationPlaqueRendered\":")
			.append(client.workbenchLocationPlaqueRendered()).append(",");
		json.append("\"inWilderness\":").append(client.workbenchInWilderness()).append(",");
		json.append("\"wildernessLevel\":").append(client.workbenchWildernessLevel()).append(",");
		json.append("\"wildernessFallbackRendered\":")
			.append(client.workbenchWildernessFallbackRendered());
		json.append("}");
	}

	private static void appendInterfaces(StringBuilder json, mudclient client) {
		json.append("\"interfaces\":{");
		appendAdvancedSettings(json, client);
		json.append(",");
		appendAuctionHouse(json, client);
		json.append(",");
		appendWorldMap(json, client);
		json.append(",");
		appendStats(json, client);
		json.append(",");
		appendShop(json, client);
		json.append(",");
		appendBank(json, client);
		json.append(",");
		appendBestiary(json, client);
		json.append(",");
		appendChristmasCracker(json, client);
		json.append(",");
		appendDuelJournal(json, client);
		json.append(",");
		appendPkCatching(json, client);
		json.append(",");
		appendTitleAward(json, client);
		json.append(",");
		appendRiftTeleport(json, client);
		json.append(",");
		appendPaperdollV2AppearanceDesigner(json, client);
		json.append("}");
	}

	private static void appendAdvancedSettings(StringBuilder json, mudclient client) {
		json.append("\"advancedSettings\":");
		if (client == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"visible\":").append(client.workbenchAdvancedSettingsVisible()).append(",");
		json.append("\"category\":").append(client.workbenchAdvancedSettingsCategory()).append(",");
		json.append("\"targets\":{");
		appendPoint(json, "interfaceCategory",
			client.workbenchAdvancedSettingsInterfaceCategoryCenterX(),
			client.workbenchAdvancedSettingsInterfaceCategoryCenterY()).append(",");
		appendPoint(json, "locationPlaqueToggle",
			client.workbenchLocationPlaqueToggleCenterX(),
			client.workbenchLocationPlaqueToggleCenterY());
		json.append("}}");
	}

	private static void appendStats(StringBuilder json, mudclient client) {
		json.append("\"stats\":");
		if (client == null) {
			json.append("null");
			return;
		}
		json.append("{");
		boolean statsVisible = client.workbenchStatsVisible();
		json.append("\"visible\":").append(statsVisible).append(",");
		json.append("\"hoveredSkill\":").append(client.workbenchStatsHoveredSkill()).append(",");
		json.append("\"scrollRows\":").append(client.workbenchStatsScrollRows()).append(",");
		json.append("\"questPoints\":").append(client.workbenchStatsQuestPoints()).append(",");
		if (statsVisible) {
			appendRect(json, "footer", client.workbenchStatsFooterX(), client.workbenchStatsFooterY(),
				client.workbenchStatsFooterWidth(), client.workbenchStatsFooterHeight()).append(",");
		} else {
			json.append("\"footer\":null,");
		}
		json.append("\"equipment\":[");
		String[] equipmentNames = {"Armour", "Weapon Aim", "Weapon Power", "Magic", "Prayer"};
		for (int stat = 0; stat < equipmentNames.length; stat++) {
			if (stat > 0) json.append(",");
			json.append("{");
			appendString(json, "name", equipmentNames[stat]).append(",");
			json.append("\"value\":").append(client.workbenchStatsEquipmentValue(stat));
			json.append("}");
		}
		json.append("],\"skills\":[");
		for (int skill = 0; skill < client.workbenchStatsSkillCount(); skill++) {
			if (skill > 0) json.append(",");
			json.append("{");
			json.append("\"index\":").append(skill).append(",");
			appendString(json, "name", client.workbenchStatsSkillName(skill)).append(",");
			json.append("\"current\":").append(client.workbenchStatsSkillCurrent(skill)).append(",");
			json.append("\"base\":").append(client.workbenchStatsSkillBase(skill)).append(",");
			appendString(json, "levelText", client.workbenchStatsSkillLevelText(skill)).append(",");
			boolean lockable = client.workbenchStatsSkillLockable(skill);
			boolean rowVisible = statsVisible && client.workbenchStatsSkillVisible(skill);
			json.append("\"lockable\":").append(lockable).append(",");
			json.append("\"locked\":").append(client.workbenchStatsSkillLocked(skill)).append(",");
			json.append("\"rowVisible\":").append(rowVisible).append(",");
			if (rowVisible) {
				appendRect(json, "row", client.workbenchStatsSkillRowX(skill),
					client.workbenchStatsSkillRowY(skill), client.workbenchStatsSkillRowWidth(skill),
					client.workbenchStatsSkillRowHeight(skill)).append(",");
			} else {
				json.append("\"row\":null,");
			}
			if (!lockable || !rowVisible) {
				json.append("\"lockTarget\":null");
			} else {
				appendRect(json, "lockTarget", client.workbenchStatsSkillLockX(skill),
					client.workbenchStatsSkillLockY(skill), client.workbenchStatsSkillLockWidth(skill),
					client.workbenchStatsSkillLockHeight(skill));
			}
			json.append("}");
		}
		json.append("]}");
	}

	private static void appendPaperdollV2AppearanceDesigner(StringBuilder json,
		mudclient client) {
		json.append("\"appearance\":");
		if (client == null) {
			json.append("null");
			return;
		}
		json.append("{");
		json.append("\"panelVisible\":")
			.append(client.workbenchAppearancePanelVisible()).append(",");
		json.append("\"visible\":").append(client.workbenchPaperdollV2DesignerVisible())
			.append(",");
		json.append("\"evaluationAvailable\":")
			.append(client.workbenchPaperdollV2DesignerAvailable()).append(",");
		json.append("\"evaluationSessionEligible\":")
			.append(client.workbenchPaperdollV2DesignerSessionEligible()).append(",");
		json.append("\"selectedHairStyle\":")
			.append(client.workbenchPaperdollV2DesignerHairStyle()).append(",");
		json.append("\"paletteIndices\":{");
		json.append("\"hair\":")
			.append(client.workbenchPaperdollV2DesignerHairColour()).append(",");
		json.append("\"top\":")
			.append(client.workbenchPaperdollV2DesignerTopColour()).append(",");
		json.append("\"bottom\":")
			.append(client.workbenchPaperdollV2DesignerBottomColour()).append(",");
		json.append("\"skin\":")
			.append(client.workbenchPaperdollV2DesignerSkinColour()).append("},");
		json.append("\"referralControlPresent\":")
			.append(client.workbenchAppearanceReferralControlPresent()).append(",");
		json.append("\"referralTextLength\":")
			.append(client.workbenchAppearanceReferralTextLength()).append(",");
		json.append("\"maximumHairStyle\":")
			.append(client.workbenchPaperdollV2DesignerMaximumHairStyle()).append(",");
		appendString(json, "styleIdentity",
			client.workbenchPaperdollV2DesignerStyleIdentity()).append(",");
		appendString(json, "styleLabel",
			client.workbenchPaperdollV2DesignerStyleLabel()).append(",");
		json.append("\"male\":").append(client.workbenchPaperdollV2DesignerMale())
			.append(",");
		json.append("\"baseAppearances\":{");
		json.append("\"head\":").append(client.workbenchPaperdollV2DesignerHeadAppearanceId())
			.append(",\"body\":")
			.append(client.workbenchPaperdollV2DesignerBodyAppearanceId())
			.append(",\"legs\":")
			.append(client.workbenchPaperdollV2DesignerLegsAppearanceId()).append("},");
		appendString(json, "previewRenderPath",
			client.workbenchPaperdollV2DesignerPreviewPath()).append(",");
		appendString(json, "previewFallbackReason",
			client.workbenchPaperdollV2DesignerPreviewFallbackReason()).append(",");
		appendString(json, "hydrationBlockedReason",
			client.workbenchPaperdollV2DesignerHydrationBlockedReason()).append(",");
		json.append("\"targets\":{");
		appendPoint(json, "previousStyle",
			client.workbenchPaperdollV2DesignerPreviousStyleX(),
			client.workbenchPaperdollV2DesignerStyleY()).append(",");
		appendPoint(json, "nextStyle", client.workbenchPaperdollV2DesignerNextStyleX(),
			client.workbenchPaperdollV2DesignerStyleY()).append(",");
		appendPoint(json, "previousGender",
			client.workbenchPaperdollV2DesignerGenderPreviousX(),
			client.workbenchPaperdollV2DesignerGenderY()).append(",");
		appendPoint(json, "nextGender", client.workbenchPaperdollV2DesignerGenderNextX(),
			client.workbenchPaperdollV2DesignerGenderY()).append(",");
		appendPoint(json, "accept", client.workbenchPaperdollV2DesignerAcceptX(),
			client.workbenchPaperdollV2DesignerAcceptY());
		json.append("}}");
	}

	private static void appendChristmasCracker(StringBuilder json, mudclient client) {
		json.append("\"christmasCracker\":");
		if (client == null) {
			json.append("null");
			return;
		}
		json.append("{");
		json.append("\"visible\":").append(client.workbenchChristmasCrackerVisible()).append(",");
		appendString(json, "outcome", client.workbenchChristmasCrackerOutcome()).append(",");
		json.append("\"winningItemId\":").append(client.workbenchChristmasCrackerWinningItemId()).append(",");
		appendString(json, "phase", client.workbenchChristmasCrackerPhase()).append(",");
		json.append("\"elapsedMillis\":").append(client.workbenchChristmasCrackerElapsedMillis()).append(",");
		json.append("\"seed\":").append(client.workbenchChristmasCrackerSeed()).append(",");
		json.append("\"winnerIndex\":").append(client.workbenchChristmasCrackerWinnerIndex()).append(",");
		json.append("\"centeredIndex\":").append(client.workbenchChristmasCrackerCenteredIndex()).append(",");
		json.append("\"centeredItemId\":").append(client.workbenchChristmasCrackerCenteredItemId()).append(",");
		json.append("\"settled\":").append(client.workbenchChristmasCrackerSettled()).append(",");
		json.append("\"closeCenter\":{");
		json.append("\"x\":").append(client.workbenchChristmasCrackerCloseCenterX()).append(",");
		json.append("\"y\":").append(client.workbenchChristmasCrackerCloseCenterY()).append("}");
		json.append("}");
	}

	private static void appendDuelJournal(StringBuilder json, mudclient client) {
		json.append("\"duelJournal\":");
		if (client == null) {
			json.append("null");
			return;
		}
		json.append("{");
		json.append("\"visible\":").append(client.workbenchDuelJournalVisible()).append(",");
		json.append("\"loading\":").append(client.workbenchDuelJournalLoading()).append(",");
		json.append("\"selectedDuelId\":").append(client.workbenchDuelJournalSelectedId()).append(",");
		json.append("\"historyCount\":").append(client.workbenchDuelJournalHistoryCount()).append(",");
		json.append("\"swingCount\":").append(client.workbenchDuelJournalSwingCount()).append(",");
		appendString(json, "proofState", client.workbenchDuelJournalProofState()).append(",");
		appendString(json, "proofId", client.workbenchDuelJournalProofId()).append(",");
		json.append("\"layout\":{");
		appendRect(json, "window", client.workbenchDuelJournalWindowX(),
			client.workbenchDuelJournalWindowY(), client.workbenchDuelJournalWindowWidth(),
			client.workbenchDuelJournalWindowHeight()).append(",");
		appendRect(json, "history", client.workbenchDuelJournalHistoryX(),
			client.workbenchDuelJournalHistoryY(), client.workbenchDuelJournalHistoryWidth(),
			client.workbenchDuelJournalHistoryHeight()).append(",");
		appendRect(json, "swings", client.workbenchDuelJournalSwingX(),
			client.workbenchDuelJournalSwingY(), client.workbenchDuelJournalSwingWidth(),
			client.workbenchDuelJournalSwingHeight()).append(",");
		appendPoint(json, "close", client.workbenchDuelJournalCloseCenterX(),
			client.workbenchDuelJournalCloseCenterY());
		json.append("}}");
	}

	private static void appendPkCatching(StringBuilder json, mudclient client) {
		json.append("\"pkCatching\":");
		PkCatchingInterface pk = client == null ? null : client.getPkCatchingInterface();
		if (pk == null) {
			json.append("null");
			return;
		}

		json.append("{");
		json.append("\"voidGlass\":").append(Config.C_CUSTOM_UI).append(",");
		json.append("\"chooser\":{");
		json.append("\"armed\":").append(pk.isChooserArmed()).append(",");
		json.append("\"visible\":").append(pk.isChooserArmed()
			&& "optionsMenu".equals(client.getWebOverlayDialogName())).append(",");
		appendRectArray(json, "panel", pk.getChooserPanelRect()).append(",");
		appendRectArray(json, "close", pk.getChooserCloseRect()).append(",");
		json.append("\"cards\":[");
		String[] modes = {"trainer", "medium", "hard"};
		for (int i = 0; i < modes.length; i++) {
			if (i > 0) json.append(",");
			json.append("{\"index\":").append(i).append(",");
			appendString(json, "mode", modes[i]).append(",");
			appendRectArray(json, "rect", pk.getChooserCardRect(i));
			json.append("}");
		}
		json.append("]},");

		json.append("\"session\":{");
		json.append("\"active\":").append(pk.getActiveSessionId() > 0L).append(",");
		json.append("\"id\":").append(pk.getActiveSessionId()).append(",");
		String sessionWireMode = pk.getModeName();
		appendString(json, "mode", pkCatchingDisplayMode(sessionWireMode)).append(",");
		appendString(json, "wireMode", sessionWireMode).append(",");
		json.append("\"remainingTicks\":").append(pk.getRemainingTicks()).append(",");
		json.append("\"catches\":").append(pk.getCatches()).append(",");
		json.append("\"currentStreak\":").append(pk.getCurrentStreak()).append(",");
		json.append("\"bestStreak\":").append(pk.getBestStreak()).append(",");
		appendString(json, "trailAccuracy", pk.getTrailAccuracy()).append(",");
		appendString(json, "reactionAverageTicks", pk.getReactionAverage()).append("},");

		String resultWireMode = pk.getResultModeName();
		json.append("\"result\":");
		if (resultWireMode.isEmpty()) {
			json.append("null,");
		} else {
			json.append("{");
			json.append("\"completed\":").append(pk.isResultCompleted()).append(",");
			appendString(json, "mode", pkCatchingDisplayMode(resultWireMode)).append(",");
			appendString(json, "wireMode", resultWireMode).append(",");
			json.append("\"catches\":").append(pk.getResultCatches()).append(",");
			json.append("\"bestStreak\":").append(pk.getResultBestStreak()).append(",");
			appendString(json, "trailAccuracy", pk.getResultTrailAccuracy()).append(",");
			appendString(json, "reactionAverageTicks", pk.getResultReactionAverage()).append(",");
			json.append("\"rank\":").append(pk.getResultRank()).append(",");
			json.append("\"personalBest\":").append(pk.getResultPersonalBest()).append(",");
			json.append("\"newBest\":").append(pk.isResultNewBest()).append("},");
		}

		json.append("\"hud\":{");
		json.append("\"visible\":").append(Config.C_CUSTOM_UI
			&& pk.getActiveSessionId() > 0L).append(",");
		appendRectArray(json, "rect", pk.getHudRect()).append("},");

		json.append("\"hint\":{");
		json.append("\"active\":").append(pk.isHintActive()).append(",");
		json.append("\"worldTile\":");
		if (pk.isHintActive()) {
			json.append("{\"x\":").append(pk.getHintWorldX())
				.append(",\"y\":").append(pk.getHintWorldY()).append("}");
		} else {
			json.append("null");
		}
		json.append(",\"projected\":").append(pk.isMarkerProjected()).append(",");
		json.append("\"projectedCenter\":");
		if (pk.isMarkerProjected()) {
			json.append("{\"x\":").append(pk.getMarkerCenterX())
				.append(",\"y\":").append(pk.getMarkerCenterY()).append("}");
		} else {
			json.append("null");
		}
		json.append("},");

		json.append("\"guide\":{");
		appendString(json, "state", pk.getGuideStateName()).append(",");
		json.append("\"attackNow\":").append(pk.isAttackNow()).append(",");
		json.append("\"worldTile\":");
		if (pk.getGuideWorldX() >= 0 && pk.getGuideWorldY() >= 0) {
			json.append("{\"x\":").append(pk.getGuideWorldX())
				.append(",\"y\":").append(pk.getGuideWorldY()).append("}");
		} else {
			json.append("null");
		}
		json.append(",\"projected\":").append(pk.isMarkerProjected()).append(",");
		json.append("\"projectedCenter\":");
		if (pk.isMarkerProjected()) {
			json.append("{\"x\":").append(pk.getMarkerCenterX())
				.append(",\"y\":").append(pk.getMarkerCenterY()).append("}");
		} else {
			json.append("null");
		}
		json.append("},");

		boolean modalVisible = pk.isModalVisible();
		String modalName = pk.getModalName();
		json.append("\"modal\":{");
		json.append("\"visible\":").append(modalVisible).append(",");
		appendString(json, "name", modalName).append(",");
		json.append("\"rect\":");
		if (modalVisible) appendRectValue(json, pk.getModalRect());
		else json.append("null");
		json.append(",\"close\":");
		if (modalVisible) appendRectValue(json, pk.getModalCloseRect());
		else json.append("null");
		json.append(",\"controls\":");
		if (modalVisible) {
			json.append("{");
			appendRectArray(json, "primary", pk.getModalPrimaryRect()).append(",");
			appendRectArray(json, "secondary", pk.getModalSecondaryRect()).append("}");
		} else {
			json.append("null");
		}
		json.append(",\"tabs\":");
		if ("pkCatchingLeaderboard".equals(modalName)) {
			json.append("{");
			appendRectArray(json, "medium", pk.getMediumTabRect()).append(",");
			appendRectArray(json, "hard", pk.getHardTabRect()).append("}");
		} else {
			json.append("null");
		}
		json.append("},");

		json.append("\"leaderboard\":{");
		String selectedBoardWire = pk.getSelectedBoardName();
		appendString(json, "selectedBoard", pkCatchingDisplayMode(selectedBoardWire)).append(",");
		appendString(json, "selectedBoardWire", selectedBoardWire).append(",");
		appendPkCatchingBoard(json, "medium", pk, "classic").append(",");
		appendPkCatchingBoard(json, "hard", pk, "hard");
		json.append("}}");
	}

	private static String pkCatchingDisplayMode(String wireMode) {
		return "classic".equals(wireMode) ? "medium" : wireMode;
	}

	private static StringBuilder appendPkCatchingBoard(StringBuilder json, String key,
		PkCatchingInterface pk, String mode) {
		json.append("\"").append(key).append("\":{");
		json.append("\"personalRank\":").append(pk.getPersonalRank(mode)).append(",");
		json.append("\"personalBest\":").append(pk.getPersonalBest(mode)).append(",");
		json.append("\"rows\":[");
		int count = pk.getLeaderboardRowCount(mode);
		for (int i = 0; i < count; i++) {
			PkCatchingInterface.LeaderboardEntry row = pk.getLeaderboardRow(mode, i);
			if (row == null) continue;
			if (json.charAt(json.length() - 1) != '[') json.append(",");
			json.append("{\"rank\":").append(row.getRank()).append(",");
			json.append("\"usernameHash\":").append(row.getUsernameHash()).append(",");
			appendString(json, "username", row.getUsername()).append(",");
			json.append("\"catches\":").append(row.getCatches()).append(",");
			json.append("\"self\":").append(row.isSelf()).append("}");
		}
		return json.append("]}");
	}

	private static void appendTitleAward(StringBuilder json, mudclient client) {
		json.append("\"titleAward\":");
		if (client == null) {
			json.append("null");
			return;
		}
		json.append("{");
		json.append("\"visible\":").append(client.workbenchTitleAwardVisible()).append(",");
		appendString(json, "id", client.workbenchTitleAwardId()).append(",");
		appendString(json, "display", client.workbenchTitleAwardDisplay()).append(",");
		json.append("\"tier\":").append(client.workbenchTitleAwardTier()).append(",");
		appendString(json, "position", client.workbenchTitleAwardPosition()).append(",");
		appendString(json, "form", client.workbenchTitleAwardForm()).append(",");
		json.append("\"options\":{");
		json.append("\"wear\":").append(client.workbenchTitleAwardWearOption()).append(",");
		json.append("\"notNow\":").append(client.workbenchTitleAwardNotNowOption()).append("},");
		json.append("\"layout\":{");
		appendRect(json, "panel", client.workbenchTitleAwardPanelX(),
			client.workbenchTitleAwardPanelY(), client.workbenchTitleAwardPanelWidth(),
			client.workbenchTitleAwardPanelHeight()).append(",");
		appendRect(json, "close", client.workbenchTitleAwardCloseX(),
			client.workbenchTitleAwardCloseY(), client.workbenchTitleAwardCloseWidth(),
			client.workbenchTitleAwardCloseHeight()).append(",");
		appendRect(json, "wear", client.workbenchTitleAwardWearX(),
			client.workbenchTitleAwardWearY(), client.workbenchTitleAwardWearWidth(),
			client.workbenchTitleAwardWearHeight()).append(",");
		appendRect(json, "notNow", client.workbenchTitleAwardNotNowX(),
			client.workbenchTitleAwardNotNowY(), client.workbenchTitleAwardNotNowWidth(),
			client.workbenchTitleAwardNotNowHeight());
		json.append("}}");
	}

	private static void appendRiftTeleport(StringBuilder json, mudclient client) {
		json.append("\"riftTeleport\":");
		if (client == null) {
			json.append("null");
			return;
		}
		boolean visible = client.workbenchRiftTeleportVisible();
		json.append("{\"visible\":").append(visible);
		if (!visible) {
			json.append(",\"layout\":null,\"cards\":[]}");
			return;
		}

		json.append(",\"layout\":{");
		appendRect(json, "panel", client.workbenchRiftTeleportPanelX(),
			client.workbenchRiftTeleportPanelY(), client.workbenchRiftTeleportPanelWidth(),
			client.workbenchRiftTeleportPanelHeight()).append(",");
		appendRect(json, "close", client.workbenchRiftTeleportCloseX(),
			client.workbenchRiftTeleportCloseY(), client.workbenchRiftTeleportCloseWidth(),
			client.workbenchRiftTeleportCloseHeight()).append(",");
		json.append("\"stay\":");
		int stayOption = client.workbenchRiftTeleportStayOption();
		if (stayOption < 0) {
			json.append("null");
		} else {
			json.append("{");
			json.append("\"option\":").append(stayOption).append(",");
			appendRect(json, "rect", client.workbenchRiftTeleportStayX(),
				client.workbenchRiftTeleportStayY(), client.workbenchRiftTeleportStayWidth(),
				client.workbenchRiftTeleportStayHeight());
			json.append("}");
		}
		json.append("},\"cards\":[");
		boolean first = true;
		for (int option = 0; option < client.workbenchRiftTeleportOptionCount(); option++) {
			if (option == stayOption) continue;
			if (!first) json.append(",");
			first = false;
			json.append("{\"option\":").append(option).append(",");
			json.append("\"shortcut\":").append(option + 1).append(",");
			appendString(json, "label", client.workbenchRiftTeleportOptionLabel(option)).append(",");
			appendString(json, "category", client.workbenchRiftTeleportOptionCategory(option)).append(",");
			json.append("\"hovered\":")
				.append(client.workbenchRiftTeleportOptionHovered(option)).append(",");
			appendRect(json, "rect", client.workbenchRiftTeleportOptionX(option),
				client.workbenchRiftTeleportOptionY(option),
				client.workbenchRiftTeleportOptionWidth(option),
				client.workbenchRiftTeleportOptionHeight(option));
			json.append("}");
		}
		json.append("]}");
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

	private static StringBuilder appendRect(StringBuilder json, String key,
		int x, int y, int width, int height) {
		json.append("\"").append(key).append("\":{\"x\":").append(x)
			.append(",\"y\":").append(y).append(",\"width\":").append(width)
			.append(",\"height\":").append(height).append("}");
		return json;
	}

	private static StringBuilder appendRectArray(StringBuilder json, String key, int[] rect) {
		json.append("\"").append(key).append("\":");
		return appendRectValue(json, rect);
	}

	private static StringBuilder appendRectValue(StringBuilder json, int[] rect) {
		if (rect == null || rect.length < 4) return json.append("null");
		return json.append("{\"x\":").append(rect[0])
			.append(",\"y\":").append(rect[1]).append(",\"width\":").append(rect[2])
			.append(",\"height\":").append(rect[3]).append("}");
	}

	private static String auctionHouseScenarioJson() throws IOException {
		ArrayList<CaptureResult> captures = new ArrayList<>();
		clearWorkbenchBlockingUi();
		seedAuctionHouseFixture();
		sendCommand("quickauction");
		waitForAuctionHouseVisible();
		sleep(500);
		clearWorkbenchOverlayDialogs();
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

	private static String christmasCrackerFixtureJson(Map<String, String> fields) throws IOException {
		requireLoggedIn();
		ChristmasCrackerFixture fixture = parseChristmasCrackerFixture(fields);
		clearWorkbenchBlockingUi();
		mudclient client = requireClient();
		runOnEdt(() -> {
			client.workbenchOpenChristmasCrackerFixture(fixture.outcome, fixture.itemId, fixture.seed);
			client.workbenchPinChristmasCrackerPhase(fixture.phase);
		});
		sleep(220);

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"fixture\":\"christmas-cracker\",");
		appendString(json, "outcome", fixture.outcome).append(",");
		json.append("\"itemId\":").append(fixture.itemId).append(",");
		json.append("\"seed\":").append(fixture.seed).append(",");
		appendString(json, "phase", fixture.phase).append(",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		if (fixture.capture) {
			CaptureResult capture = captureOnce("fixture-christmas-cracker-" + fixture.outcome.replace('_', '-')
				+ "-" + fixture.phase.replace('_', '-'));
			appendSingleCapture(json, capture);
			json.append(",");
		}
		json.append("\"state\":").append(stateJson(null, -1, -1));
		json.append("}");
		return json.toString();
	}

	private static String duelJournalFixtureJson(Map<String, String> fields) throws IOException {
		requireLoggedIn();
		clearWorkbenchBlockingUi();
		final mudclient client = requireClient();
		final long now = System.currentTimeMillis();
		final long selectedId = 730010L;
		final long completedAt = now - 5L * 60L * 1000L;
		final long startedAt = completedAt - 48L * 1000L;
		final String requestedProof = fields.containsKey("proof")
			? fields.get("proof").trim().toLowerCase(Locale.ROOT) : "verified";
		final DuelJournalInterface.ProofState proofState;
		if ("verified".equals(requestedProof)) {
			proofState = DuelJournalInterface.ProofState.VERIFIED;
		} else if ("failed".equals(requestedProof)) {
			proofState = DuelJournalInterface.ProofState.FAILED;
		} else if ("unavailable".equals(requestedProof)) {
			proofState = DuelJournalInterface.ProofState.UNAVAILABLE;
		} else {
			throw new IllegalArgumentException("proof must be verified, failed, or unavailable");
		}
		final String proofId = proofState == DuelJournalInterface.ProofState.UNAVAILABLE
			? "" : "9a37c0de41b2658ff034b9137d80ac52";

		final ArrayList<DuelJournalInterface.DuelSummary> history = new ArrayList<>();
		history.add(new DuelJournalInterface.DuelSummary(selectedId, startedAt, completedAt,
			true, 2002, "Ashen Knight"));
		String[] opponents = {
			"Mara", "Kestrel", "Grim Vale", "Nyx", "Old Bones",
			"Vesper", "Iron Finch", "Sable", "Warden"
		};
		for (int i = 0; i < opponents.length; i++) {
			long rowCompletedAt = completedAt - (i + 1L) * 47L * 60L * 1000L;
			history.add(new DuelJournalInterface.DuelSummary(selectedId - i - 1L,
				rowCompletedAt - (34L + i) * 1000L, rowCompletedAt, (i & 1) != 0,
				2100 + i, opponents[i]));
		}

		final ArrayList<DuelJournalInterface.StakeItem> stakes = new ArrayList<>();
		stakes.add(new DuelJournalInterface.StakeItem(true, 0, 576, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(true, 1, 10, 50000000, false));
		stakes.add(new DuelJournalInterface.StakeItem(true, 2, 81, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(true, 3, 597, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(true, 4, 373, 1000, true));
		stakes.add(new DuelJournalInterface.StakeItem(true, 5, 401, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(false, 0, 578, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(false, 1, 10, 35000000, false));
		stakes.add(new DuelJournalInterface.StakeItem(false, 2, 1278, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(false, 3, 971, 1, false));
		stakes.add(new DuelJournalInterface.StakeItem(false, 4, 546, 750, true));
		stakes.add(new DuelJournalInterface.StakeItem(false, 5, 316, 1, false));

		final ArrayList<DuelJournalInterface.Swing> swings = new ArrayList<>();
		for (int number = 1; number <= 28; number++) {
			int style = (number - 1) % 4;
			boolean accurate = number % 5 != 0;
			int damage = accurate ? (number % 7 == 0 ? 0 : 1 + (number * 7) % 14) : 0;
			swings.add(new DuelJournalInterface.Swing(number, style, accurate, damage));
		}

		final DuelJournalInterface.DuelDetail detail = new DuelJournalInterface.DuelDetail(
			selectedId, 1001, 2002, true, startedAt, completedAt, "Ashen Knight",
			proofState, proofId, stakes, swings);
		runOnEdt(() -> client.workbenchOpenDuelJournalFixture(history, detail));
		sleep(250);

		boolean capture = "true".equalsIgnoreCase(fields.get("capture"));
		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "fixture", "duel-journal").append(",");
		json.append("\"selectedDuelId\":").append(selectedId).append(",");
		json.append("\"historyCount\":").append(history.size()).append(",");
		json.append("\"swingCount\":").append(swings.size()).append(",");
		appendString(json, "proofState", requestedProof).append(",");
		appendString(json, "proofId", proofId).append(",");
		appendString(json, "generatedAt", isoTimestamp()).append(",");
		if (capture) {
			CaptureResult result = captureOnce("fixture-duel-journal");
			appendSingleCapture(json, result);
			json.append(",");
		}
		json.append("\"state\":").append(stateJson(null, -1, -1)).append("}");
		return json.toString();
	}

	private static String pkCatchingFixtureJson(Map<String, String> fields) throws IOException {
		String view = fields.containsKey("view") ? fields.get("view") : "chooser";
		view = normalizePkCatchingView(view);
		configurePkCatchingFixture(view);

		boolean capture = "true".equalsIgnoreCase(fields.get("capture"));
		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "fixture", "pk-catching").append(",");
		appendString(json, "view", view).append(",");
		appendString(json, "generatedAt", isoTimestamp()).append(",");
		if (capture) {
			CaptureResult result = captureOnce("fixture-pk-catching-" + view);
			appendSingleCapture(json, result);
			json.append(",");
		}
		json.append("\"state\":").append(stateJson(null, -1, -1)).append("}");
		return json.toString();
	}

	private static String pkCatchingScenarioJson() throws IOException {
		requirePkCatchingFixtureClient();
		ArrayList<CaptureResult> captures = new ArrayList<>();

		configurePkCatchingFixture("chooser");
		mudclient client = requireClient();
		PkCatchingInterface pk = client.getPkCatchingInterface();
		runOnEdt(() -> client.messageTabSelected = MessageTab.ALL);
		clickGame(Math.max(1, client.getGameWidth() / 4), client.getGameHeight() + 5, "left");
		sleep(160);
		if (client.messageTabSelected != MessageTab.ALL || !pk.isChooserArmed()
			|| !"optionsMenu".equals(client.getWebOverlayDialogName())) {
			throw new IOException("PK catching chooser leaked its scrim press to lower chat/UI state");
		}
		captures.add(captureOnce("scenario-pk-catching-chooser"));
		int[] hardCard = pk.getChooserCardRect(2);
		clickGame(hardCard[0] + hardCard[2] / 2, hardCard[1] + hardCard[3] / 2, "left");
		sleep(160);
		if ("optionsMenu".equals(client.getWebOverlayDialogName())) {
			throw new IOException("PK catching chooser did not dispatch its ordinary option reply");
		}

		configurePkCatchingFixture("trainer");
		waitUntil(pk::isMarkerProjected, 2500,
			"PK catching Trainer destination did not project through the production scene path");
		captures.add(captureOnce("scenario-pk-catching-trainer"));

		configurePkCatchingFixture("trainer-attack");
		waitUntil(() -> pk.isAttackNow() && !pk.isMarkerProjected(), 2500,
			"PK catching Trainer attack cue did not suppress the destination marker");
		captures.add(captureOnce("scenario-pk-catching-trainer-attack"));

		configurePkCatchingFixture("trainer-hidden");
		waitUntil(() -> "hidden".equals(pk.getGuideStateName()) && !pk.isMarkerProjected(), 2500,
			"PK catching Trainer hidden cue retained visible guidance");
		captures.add(captureOnce("scenario-pk-catching-trainer-hidden"));

		configurePkCatchingFixture("results");
		captures.add(captureOnce("scenario-pk-catching-results"));

		configurePkCatchingFixture("leaderboard-medium");
		captures.add(captureOnce("scenario-pk-catching-leaderboard-medium"));

		selectPkCatchingBoard("hard");
		captures.add(captureOnce("scenario-pk-catching-leaderboard-hard"));

		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "scenario", "pk-catching-ui").append(",");
		json.append("\"chooserScrimConsumed\":true,");
		json.append("\"chooserCardDispatched\":true,");
		json.append("\"trainerHintAccepted\":true,");
		json.append("\"trainerMarkerProjected\":true,");
		json.append("\"trainerDestinationAccepted\":true,");
		json.append("\"trainerAttackAccepted\":true,");
		json.append("\"trainerAttackMarkerSuppressed\":true,");
		json.append("\"trainerHiddenAccepted\":true,");
		json.append("\"trainerHiddenMarkerSuppressed\":true,");
		appendString(json, "generatedAt", isoTimestamp()).append(",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static String normalizePkCatchingView(String value) throws IOException {
		String view = value == null ? "chooser"
			: value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
		if ("trainer-hud".equals(view)) view = "trainer";
		if ("medium".equals(view) || "highscores".equals(view)
			|| "leaderboard".equals(view)) view = "leaderboard-medium";
		if ("hard".equals(view)) view = "leaderboard-hard";
		if (!"chooser".equals(view) && !"trainer".equals(view)
			&& !"trainer-attack".equals(view) && !"trainer-hidden".equals(view)
			&& !"results".equals(view)
			&& !"leaderboard-medium".equals(view) && !"leaderboard-hard".equals(view)) {
			throw new IOException("PK catching view must be chooser, trainer, trainer-attack, "
				+ "trainer-hidden, results, leaderboard-medium, or leaderboard-hard");
		}
		return view;
	}

	private static void configurePkCatchingFixture(String view) throws IOException {
		final mudclient client = requirePkCatchingFixtureClient();
		clearWorkbenchBlockingUi();
		final PkCatchingInterface pk = client.getPkCatchingInterface();
		final int localX = client.getLocalPlayerX();
		final int localY = client.getLocalPlayerZ();
		final int hintLocalX = localX < 94 ? localX + 1 : Math.max(0, localX - 1);
		final int hintWorldX = client.getMidRegionBaseX() + hintLocalX;
		final int hintWorldY = client.getMidRegionBaseZ() + Math.max(0, Math.min(94, localY));

		runOnEdt(() -> {
			if ("chooser".equals(view)) {
				acceptPkCatchingMessage(client, "chooser|7301");
				client.setOptionsMenuCount(3);
				client.setOptionsMenuText(0, "Easy / Trainer (unranked)");
				client.setOptionsMenuText(1, "Medium (classic ranked)");
				client.setOptionsMenuText(2, "Hard (ranked)");
				client.setOptionsMenuShow(true);
			} else if ("trainer".equals(view) || "trainer-attack".equals(view)
					|| "trainer-hidden".equals(view)) {
				boolean destination = "trainer".equals(view);
				String guideState = destination ? "destination"
					: "trainer-attack".equals(view) ? "attack" : "hidden";
				acceptPkCatchingMessage(client, "chooser|7401");
				acceptPkCatchingMessage(client, "start|7402|7401|trainer|500");
				acceptPkCatchingMessage(client, "hud|7402|trainer|286|7|3|4|31|40|21|7|"
					+ (destination ? "1|" + hintWorldX + "|" + hintWorldY : "0|-1|-1"));
				acceptPkCatchingMessage(client, "guide|7402|" + guideState + "|"
					+ (destination ? hintWorldX + "|" + hintWorldY : "-1|-1"));
			} else if ("results".equals(view)) {
				seedPkCatchingLeaderboards(client, 7501L, 7502L);
				acceptPkCatchingMessage(client, "chooser|7503");
				acceptPkCatchingMessage(client, "start|7504|7503|classic|500");
				acceptPkCatchingMessage(client, "hud|7504|classic|0|37|6|11|410|500|58|37|0|-1|-1");
				acceptPkCatchingMessage(client, "clear|7504|7503");
				acceptPkCatchingMessage(client, "result|7504|classic|1|37|6|11|410|500|58|37|4|37|1");
			} else {
				seedPkCatchingLeaderboards(client, 7601L, 7602L);
			}
		});

		sleep(260);
		assertPkCatchingFixtureState(pk, view, hintWorldX, hintWorldY);
		if ("leaderboard-hard".equals(view)) selectPkCatchingBoard("hard");
	}

	private static mudclient requirePkCatchingFixtureClient() throws IOException {
		requireLoggedIn();
		mudclient client = requireClient();
		if (client.getPkCatchingInterface() == null) {
			throw new IOException("PK catching interface is not initialized");
		}
		if (!Config.C_CUSTOM_UI) {
			throw new IOException("PK catching fixtures require Void Glass/custom UI at login; "
				+ "relaunch against a custom-UI-enabled server preset");
		}
		return client;
	}

	private static void acceptPkCatchingMessage(mudclient client, String payload) throws IOException {
		if (!client.handleVoidscapePkCatchingMessage("@vspkcatch@v1|" + payload)) {
			throw new IOException("PK catching production parser rejected its reserved message namespace");
		}
	}

	private static void seedPkCatchingLeaderboards(mudclient client, long transferId,
		long generationId) throws IOException {
		String[] mediumNames = {
			"Arc Runner", "Diagonal Dan", "Mint Trail", "Workbench Hero", "Sable Step",
			"Quick Reed", "North Cut", "Glass Fox", "Rune Dash", "Last Tile"
		};
		int[] mediumScores = {52, 47, 42, 37, 34, 31, 29, 26, 24, 21};
		String[] hardNames = {
			"No Mercy", "Workbench Hero", "Cutback King", "Wild Vector", "Obsidian Run",
			"Crazy Legs", "Edge Route", "Hard Read", "Fast Ash", "Final Juke"
		};
		int[] hardScores = {49, 44, 38, 33, 29, 26, 23, 20, 18, 16};

		acceptPkCatchingMessage(client, "leaderboard-begin|" + transferId + "|" + generationId
			+ "|10|10|4|37|2|44");
		for (int i = 0; i < mediumNames.length; i++) {
			acceptPkCatchingMessage(client, "leaderboard-row|" + transferId + "|classic|" + (i + 1)
				+ "|" + (910001L + i) + "|" + mediumNames[i] + "|" + mediumScores[i]
				+ "|" + (i == 3 ? 1 : 0));
		}
		for (int i = 0; i < hardNames.length; i++) {
			acceptPkCatchingMessage(client, "leaderboard-row|" + transferId + "|hard|" + (i + 1)
				+ "|" + (920001L + i) + "|" + hardNames[i] + "|" + hardScores[i]
				+ "|" + (i == 1 ? 1 : 0));
		}
		acceptPkCatchingMessage(client, "leaderboard-end|" + transferId);
	}

	private static void assertPkCatchingFixtureState(PkCatchingInterface pk, String view,
		int hintWorldX, int hintWorldY) throws IOException {
		if ("chooser".equals(view)) {
			if (!pk.isChooserArmed()) throw new IOException("PK catching chooser fixture did not arm");
			return;
		}
		if ("trainer".equals(view) || "trainer-attack".equals(view)
				|| "trainer-hidden".equals(view)) {
			if (pk.getActiveSessionId() != 7402L || !"trainer".equals(pk.getModeName())
				|| pk.getRemainingTicks() != 286
				|| pk.getCatches() != 7 || pk.getCurrentStreak() != 3 || pk.getBestStreak() != 4
				|| !"77%".equals(pk.getTrailAccuracy()) || !"3.0".equals(pk.getReactionAverage())) {
				throw new IOException("PK catching Trainer fixture did not accept authoritative session state");
			}
			if ("trainer".equals(view)) {
				if (!pk.isHintActive() || pk.getHintWorldX() != hintWorldX
					|| pk.getHintWorldY() != hintWorldY
					|| !"destination".equals(pk.getGuideStateName()) || pk.isAttackNow()
					|| pk.getGuideWorldX() != hintWorldX || pk.getGuideWorldY() != hintWorldY) {
					throw new IOException("PK catching Trainer destination fixture did not accept its stable cue");
				}
			} else if ("trainer-attack".equals(view)) {
				if (pk.isHintActive() || !"attack".equals(pk.getGuideStateName())
					|| !pk.isAttackNow() || pk.getGuideWorldX() != -1 || pk.getGuideWorldY() != -1
					|| pk.isMarkerProjected()) {
					throw new IOException("PK catching Trainer attack fixture retained destination guidance");
				}
			} else if (pk.isHintActive() || !"hidden".equals(pk.getGuideStateName())
					|| pk.isAttackNow() || pk.getGuideWorldX() != -1 || pk.getGuideWorldY() != -1
					|| pk.isMarkerProjected()) {
				throw new IOException("PK catching Trainer hidden fixture retained visible guidance");
			}
			return;
		}
		if ("results".equals(view)) {
			if (!pk.isModalVisible() || !"pkCatchingResults".equals(pk.getModalName())
				|| !pk.isResultCompleted() || !"classic".equals(pk.getResultModeName())
				|| pk.getResultCatches() != 37 || pk.getResultBestStreak() != 11
				|| !"82%".equals(pk.getResultTrailAccuracy())
				|| !"1.6".equals(pk.getResultReactionAverage())
				|| pk.getResultRank() != 4 || pk.getResultPersonalBest() != 37
				|| !pk.isResultNewBest()) {
				throw new IOException("PK catching results fixture did not open the production modal");
			}
			return;
		}
		if (!pk.isModalVisible() || !"pkCatchingLeaderboard".equals(pk.getModalName())
			|| pk.getLeaderboardRowCount("classic") != 10 || pk.getLeaderboardRowCount("hard") != 10
			|| pk.getPersonalRank("classic") != 4 || pk.getPersonalBest("classic") != 37
			|| pk.getPersonalRank("hard") != 2 || pk.getPersonalBest("hard") != 44) {
			throw new IOException("PK catching leaderboard fixture did not commit both complete boards");
		}
	}

	private static void selectPkCatchingBoard(String board) throws IOException {
		mudclient client = requireClient();
		PkCatchingInterface pk = client.getPkCatchingInterface();
		if (pk == null || !pk.isModalVisible()
			|| !"pkCatchingLeaderboard".equals(pk.getModalName())) {
			throw new IOException("PK catching leaderboard is not visible");
		}
		int[] tab = "hard".equals(board) ? pk.getHardTabRect() : pk.getMediumTabRect();
		clickGame(tab[0] + tab[2] / 2, tab[1] + tab[3] / 2, "left");
		waitUntil(() -> board.equals(pk.getSelectedBoardName()), 2000,
			"PK catching " + board + " tab did not activate through production hit-testing");
		sleep(180);
	}

	private static String christmasCrackerScenarioJson(Map<String, String> fields) throws IOException {
		requireLoggedIn();
		ChristmasCrackerFixture fixture = parseChristmasCrackerFixture(fields);
		clearWorkbenchBlockingUi();
		mudclient client = requireClient();
		runOnEdt(() -> client.workbenchOpenChristmasCrackerFixture(fixture.outcome, fixture.itemId, fixture.seed));

		ArrayList<CaptureResult> captures = new ArrayList<>();
		String[] phases = {"opening", "fast", "near-stop", "reveal"};
		for (String phase : phases) {
			runOnEdt(() -> client.workbenchPinChristmasCrackerPhase(phase));
			sleep(220);
			captures.add(captureOnce("scenario-christmas-cracker-" + fixture.outcome.replace('_', '-')
				+ "-" + phase));
			if ("near-stop".equals(phase)
				&& client.workbenchChristmasCrackerCenteredIndex() == client.workbenchChristmasCrackerWinnerIndex()) {
				throw new IOException("Christmas cracker near-stop exposed the winner before the final beat");
			}
		}
		if (!client.workbenchChristmasCrackerSettled()
			|| client.workbenchChristmasCrackerCenteredIndex() != client.workbenchChristmasCrackerWinnerIndex()
			|| client.workbenchChristmasCrackerCenteredItemId() != client.workbenchChristmasCrackerWinningItemId()) {
			throw new IOException("Christmas cracker reel did not settle on its authoritative result");
		}

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"christmas-cracker-roll\",");
		appendString(json, "outcome", fixture.outcome).append(",");
		json.append("\"itemId\":").append(fixture.itemId).append(",");
		json.append("\"seed\":").append(fixture.seed).append(",");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static ChristmasCrackerFixture parseChristmasCrackerFixture(Map<String, String> fields) throws IOException {
		String outcome = fields.containsKey("outcome") ? fields.get("outcome") : "nothing";
		outcome = outcome == null ? "nothing"
			: outcome.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
		int defaultItemId;
		if ("nothing".equals(outcome)) {
			defaultItemId = -1;
		} else if ("party_hat".equals(outcome)) {
			defaultItemId = 580;
		} else if ("holiday_rare".equals(outcome)) {
			defaultItemId = 971;
		} else {
			throw new IOException("Unknown Christmas cracker outcome: " + outcome);
		}
		int itemId = fields.containsKey("itemId") ? requiredInt(fields, "itemId") : defaultItemId;
		if (!validChristmasCrackerFixtureItem(outcome, itemId)) {
			throw new IOException("Item " + itemId + " is invalid for Christmas cracker outcome " + outcome);
		}
		long seed = fields.containsKey("seed") ? requiredLong(fields, "seed") : 424242L;
		String phase = fields.containsKey("phase") ? fields.get("phase") : "opening";
		phase = phase == null ? "opening"
			: phase.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
		if (!("opening".equals(phase) || "fast".equals(phase)
			|| "near-stop".equals(phase) || "reveal".equals(phase))) {
			throw new IOException("Unknown Christmas cracker phase: " + phase);
		}
		boolean capture = "true".equalsIgnoreCase(fields.get("capture"));
		return new ChristmasCrackerFixture(outcome, itemId, seed, phase, capture);
	}

	private static boolean validChristmasCrackerFixtureItem(String outcome, int itemId) {
		if ("nothing".equals(outcome)) return itemId == -1;
		if ("party_hat".equals(outcome)) return itemId >= 576 && itemId <= 581;
		return itemId == 422 || itemId == 677 || itemId == 828
			|| itemId == 831 || itemId == 832 || itemId == 971;
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

	private static String appearanceFramesScenarioJson(int appearanceId) throws IOException {
		requireLoggedIn();
		final mudclient client = requireClient();
		final ORSCharacter player = client.getLocalPlayer();
		final GeneratedAppearanceRegistry.Entry managed = GeneratedAppearanceRegistry.findAuthentic(appearanceId);
		if (managed == null) {
			throw new IOException("Appearance " + appearanceId + " is not compiler-managed");
		}
		if (Config.S_WANT_CUSTOM_SPRITES) {
			throw new IOException("Managed appearance QA requires the authentic sprite archive");
		}
		if (player.layerAnimation == null || player.layerAnimation.length <= managed.paperdollSlot) {
			throw new IOException("Local player appearance is not initialized");
		}
		requireAppearanceFramesLoaded(client, managed);

		final CowboyPreviewState original = new CowboyPreviewState(client, player, managed.paperdollSlot);
		if (original.clientCombatTimeout > 0 || player.combatTimeout > 0) {
			throw new IOException("Player must be out of combat before cowboy hat frame QA");
		}
		if (player.waypointIndexNext != (player.waypointIndexCurrent + 1) % player.waypointsX.length) {
			throw new IOException("Player must be stationary before cowboy hat frame QA");
		}

		ArrayList<CowboyFrameCapture> captures = new ArrayList<>();
		boolean restored;
		try {
			for (ORSCharacterDirection direction : APPEARANCE_WALK_DIRECTIONS) {
				for (int frame = 0; frame < 3; frame++) {
					int appliedAt = applyCowboyPreviewFrame(client, player, original.renderPlayerIndex, direction,
						frame * PLAYER_WALK_FRAME_TICKS, managed.paperdollSlot, appearanceId);
					int rendererFrame = waitForRendererAdvance(client, appliedAt, 2, 1500);
					requireCowboyPreviewState(client, player, original.renderPlayerIndex,
						direction, frame * PLAYER_WALK_FRAME_TICKS, managed.paperdollSlot, appearanceId);
					int wantedAnimDir = direction.rsDir;
					boolean mirrorX = wantedAnimDir >= 5;
					int actualAnimDir = mirrorX ? 8 - wantedAnimDir : wantedAnimDir;
					int spriteOffset = actualAnimDir * 3 + frame;
					String directionName = direction.name().toLowerCase(Locale.ROOT).replace('_', '-');
					CaptureResult capture = captureOnce("scenario-appearance-" + managed.key + "-walk-"
						+ directionName + "-" + frame);
					AppearanceRasterCapture raster = captureAppearanceRaster(client, player, wantedAnimDir,
						actualAnimDir, mirrorX, spriteOffset, frame * PLAYER_WALK_FRAME_TICKS,
						"scenario-appearance-raster-" + managed.key + "-walk-" + directionName + "-" + frame);
					captures.add(new CowboyFrameCapture(spriteOffset, "walk", directionName, frame,
						wantedAnimDir, actualAnimDir, mirrorX, rendererFrame, capture, raster));
				}
			}

			for (ORSCharacterDirection direction : APPEARANCE_COMBAT_DIRECTIONS) {
				for (int frame = 0; frame < 3; frame++) {
					int appliedAt = applyCowboyPreviewFrame(client, player, original.renderPlayerIndex,
						direction, 0, managed.paperdollSlot, appearanceId);
					int rendererFrame = waitForCombatPreviewPhase(client, player, original.renderPlayerIndex,
						appliedAt, direction, frame, managed.paperdollSlot, appearanceId);
					requireCowboyPreviewState(client, player, original.renderPlayerIndex,
						direction, 0, managed.paperdollSlot, appearanceId);
					String directionName = direction.name().toLowerCase(Locale.ROOT).replace('_', '-');
					CaptureResult capture = captureOnce("scenario-appearance-" + managed.key + "-"
						+ directionName + "-" + frame);
					AppearanceRasterCapture raster = captureAppearanceRaster(client, player, 2, 5,
						direction == ORSCharacterDirection.COMBAT_B, 15 + frame, 0,
						"scenario-appearance-raster-" + managed.key + "-" + directionName + "-" + frame);
					captures.add(new CowboyFrameCapture(15 + frame, "combat", directionName, frame,
						2, 5, direction == ORSCharacterDirection.COMBAT_B, rendererFrame, capture, raster));
				}
			}
		} finally {
			original.restore(client, player);
			restored = original.matches(client, player);
		}
		if (!restored) throw new IOException("Appearance preview state did not restore cleanly");

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		json.append("\"scenario\":\"appearance-frames\",");
		appendString(json, "appearanceKey", managed.key).append(",");
		json.append("\"appearanceId\":").append(appearanceId).append(",");
		json.append("\"paperdollSlot\":").append(managed.paperdollSlot).append(",");
		json.append("\"spriteBase\":").append(managed.definition.getNumber()).append(",");
		File spriteArchive = new File(Config.F_CACHE_DIR,
			"video" + File.separator + "Authentic_Sprites.orsc").getAbsoluteFile();
		json.append("\"spriteArchive\":{");
		appendString(json, "path", spriteArchive.getPath()).append(",");
		appendString(json, "sha256", sha256(Files.readAllBytes(spriteArchive.toPath()))).append("},");
		json.append("\"frameCount\":").append(captures.size()).append(",");
		json.append("\"restored\":true,");
		json.append("\"generatedAt\":\"").append(jsonEscape(isoTimestamp())).append("\",");
		json.append("\"state\":").append(stateJson(null, -1, -1)).append(",");
		appendCowboyFrameCaptures(json, captures);
		json.append("}");
		return json.toString();
	}

	private static String paperdollV2FramesScenarioJson(Map<String, String> fields) throws IOException {
		requireLoggedIn();
		final mudclient client = requireClient();
		final ORSCharacter livePlayer = client.getLocalPlayer();
		if (livePlayer == null || livePlayer.layerAnimation == null) {
			throw new IOException("Local player appearance is not initialized");
		}
		PaperdollV2Input input = resolvePaperdollV2Input(fields);
		final PaperdollV2Pack pack;
		try (FileInputStream stream = new FileInputStream(input.archiveFile)) {
			pack = PaperdollV2Pack.read(stream);
		}
		final int primaryRgb = optionalRgb(fields, "primaryRgb", PAPERDOLL_V2_PRIMARY);
		final int secondaryRgb = optionalRgb(fields, "secondaryRgb", PAPERDOLL_V2_SECONDARY);
		final int backgroundRgb = optionalRgb(fields, "backgroundRgb", PAPERDOLL_V2_BACKGROUND);
		if (backgroundRgb != PAPERDOLL_V2_BACKGROUND) {
			throw new IOException("Paperdoll V2 parity backgroundRgb is locked to 121820");
		}
		final PaperdollV2BaseProfile ambiguousBaseProfile = optionalBaseProfile(fields, "baseProfile");
		final int[][] paletteLengths = new int[1][];
		runOnEdt(() -> paletteLengths[0] = client.workbenchAppearancePaletteLengths());
		final int hairColour = optionalPaletteIndex(fields, "hairColour",
			PAPERDOLL_V2_HAIR_COLOUR_INDEX, paletteLengths[0][0]);
		final int topColour = optionalPaletteIndex(fields, "topColour",
			livePlayer.colourTop, paletteLengths[0][1]);
		final int bottomColour = optionalPaletteIndex(fields, "bottomColour",
			livePlayer.colourBottom, paletteLengths[0][2]);
		final int skinColour = optionalPaletteIndex(fields, "skinColour",
			livePlayer.colourSkin, paletteLengths[0][3]);
		final int shieldAppearanceId = optionalAppearanceId(fields, "shieldAppearanceId",
			PAPERDOLL_V2_SHIELD_APPEARANCE_ID);
		final int weaponAppearanceId = optionalAppearanceId(fields, "weaponAppearanceId",
			PAPERDOLL_V2_WEAPON_APPEARANCE_ID);
		final int hatAppearanceId = optionalAppearanceId(fields, "hatAppearanceId", 0);
		final ORSCharacter palettePlayer = paperdollV2PreviewPlayer(
			PaperdollV2BaseProfile.MALE, hairColour, topColour, bottomColour, skinColour,
			shieldAppearanceId, weaponAppearanceId, hatAppearanceId);
		final int[][] resolvedColours = new int[1][];
		runOnEdt(() -> resolvedColours[0] = client.workbenchResolvedAppearanceColours(palettePlayer));
		final int[] tintRgb = new int[] {
			resolvedColours[0][3], resolvedColours[0][0], resolvedColours[0][0],
			resolvedColours[0][1], resolvedColours[0][2], primaryRgb, secondaryRgb, 0x00ffffff
		};

		File outputDirectory = createPaperdollV2OutputDirectory(input.workspaceDirectory);
		ArrayList<PaperdollV2Capture> captures = new ArrayList<>();
		for (String stackId : pack.getRenderStackIds()) {
			PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
			PaperdollV2BaseProfile baseProfile = PaperdollV2BaseProfile.derive(stack, ambiguousBaseProfile);
			final ORSCharacter previewPlayer = paperdollV2PreviewPlayer(baseProfile,
				hairColour, topColour, bottomColour, skinColour, shieldAppearanceId,
				weaponAppearanceId, hatAppearanceId);
			File stackDirectory = new File(outputDirectory, stackId);
			Files.createDirectories(stackDirectory.toPath());
			File v2OnlyStackDirectory = null;
			if (stack.usesLiveControls()) {
				v2OnlyStackDirectory = new File(new File(outputDirectory, "v2-only"), stackId);
				Files.createDirectories(v2OnlyStackDirectory.toPath());
			}
			for (PaperdollV2Pose pose : PaperdollV2Pose.canonical()) {
				final int[][] raster = new int[1][];
				runOnEdt(() -> raster[0] = client.workbenchRenderPaperdollV2Raster(pack, stackId,
					previewPlayer, pose, backgroundRgb, primaryRgb, secondaryRgb));
				int[] layerOrder = client.workbenchAppearanceLayerOrder(pose.getWantedAnimDir());
				String fileName = String.format(Locale.ROOT, "%02d-%s.png", pose.getOrdinal(), pose.getKey());
				PaperdollV2RasterArtifact v2Only = null;
				if (stack.usesLiveControls()) {
					final int[][] v2OnlyRaster = new int[1][];
					runOnEdt(() -> v2OnlyRaster[0] = client.workbenchRenderPaperdollV2OnlyRaster(pack,
						stackId, previewPlayer, pose, backgroundRgb, primaryRgb, secondaryRgb));
					v2Only = writePaperdollV2Raster(new File(v2OnlyStackDirectory, fileName),
						v2OnlyRaster[0], backgroundRgb, stack.getId() + "/v2-only/" + pose.getKey(), true);
				}
				captures.add(writePaperdollV2Capture(new File(stackDirectory, fileName), raster[0],
					backgroundRgb, stack, baseProfile, previewPlayer.layerAnimation, pose,
					layerOrder, tintRgb, v2Only));
			}
		}
		int expectedCaptureCount = pack.getRenderStackIds().size() * PaperdollV2Pose.canonical().size();
		if (captures.size() != expectedCaptureCount) {
			throw new IOException("Paperdoll V2 capture set is partial: expected " + expectedCaptureCount
				+ " but produced " + captures.size());
		}

		File report = new File(outputDirectory, "report.json").getAbsoluteFile();
		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		appendString(json, "scenario", "paperdoll-v2-frames").append(",");
		json.append("\"workbenchOnly\":true,");
		json.append("\"liveRendererModified\":false,");
		json.append("\"liveStateMutated\":false,");
		json.append("\"restored\":true,");
		appendString(json, "stateRestoration", "not-required-no-live-mutation").append(",");
		appendString(json, "archivePath", input.archiveFile.getPath()).append(",");
		appendString(json, "archiveSha256", sha256(Files.readAllBytes(input.archiveFile.toPath()))).append(",");
		appendString(json, "registrySha256", pack.getRegistrySha256()).append(",");
		appendString(json, "workspacePath", input.workspaceDirectory.getPath()).append(",");
		appendString(json, "outputDirectory", outputDirectory.getPath()).append(",");
		appendString(json, "reportPath", report.getPath()).append(",");
		json.append("\"preview\":{");
		json.append("\"width\":").append(PaperdollV2Pack.PREVIEW_WIDTH).append(",");
		json.append("\"height\":").append(PaperdollV2Pack.PREVIEW_HEIGHT).append(",");
		json.append("\"walkWidth\":").append(PaperdollV2Pack.WALK_WIDTH).append(",");
		json.append("\"combatWidth\":").append(PaperdollV2Pack.COMBAT_WIDTH).append(",");
		json.append("\"playerHeight\":").append(PaperdollV2Pack.PLAYER_HEIGHT).append(",");
		json.append("\"drawX\":").append(PaperdollV2Pack.PREVIEW_DRAW_X).append(",");
		json.append("\"drawY\":").append(PaperdollV2Pack.PREVIEW_DRAW_Y).append(",");
		json.append("\"backgroundRgb\":").append(backgroundRgb).append("},");
		json.append("\"palette\":{");
		appendString(json, "hairPolicy", "pinned-voidscape-coal-index-17").append(",");
		json.append("\"indices\":{");
		json.append("\"hair\":").append(palettePlayer.colourHair).append(",");
		json.append("\"top\":").append(palettePlayer.colourTop).append(",");
		json.append("\"bottom\":").append(palettePlayer.colourBottom).append(",");
		json.append("\"skin\":").append(palettePlayer.colourSkin).append("},");
		json.append("\"resolvedRgb\":{");
		json.append("\"hair\":").append(resolvedColours[0][0]).append(",");
		json.append("\"facialHair\":").append(resolvedColours[0][0]).append(",");
		json.append("\"top\":").append(resolvedColours[0][1]).append(",");
		json.append("\"bottom\":").append(resolvedColours[0][2]).append(",");
		json.append("\"skin\":").append(resolvedColours[0][3]).append(",");
		json.append("\"primary\":").append(primaryRgb).append(",");
		json.append("\"secondary\":").append(secondaryRgb).append("}},");
		json.append("\"legacyControls\":{");
		appendString(json, "policy",
			"live-controls-stacks-use-live-slot-math; pack-only-control-uses-archive-assets").append(",");
		json.append("\"pinnedAppearances\":{");
		json.append("\"head\":").append(PaperdollV2Runtime.COMPATIBLE_HEAD_APPEARANCE_ID).append(",");
		json.append("\"maleBody\":").append(PaperdollV2Runtime.COMPATIBLE_MALE_BODY_APPEARANCE_ID).append(",");
		json.append("\"femaleBody\":").append(PaperdollV2Runtime.COMPATIBLE_FEMALE_BODY_APPEARANCE_ID).append(",");
		json.append("\"legs\":").append(PaperdollV2Runtime.COMPATIBLE_LEGS_APPEARANCE_ID).append(",");
		json.append("\"shield\":").append(PAPERDOLL_V2_SHIELD_APPEARANCE_ID).append(",");
		json.append("\"weapon\":").append(PAPERDOLL_V2_WEAPON_APPEARANCE_ID).append("},");
		json.append("\"qaInput\":{");
		appendString(json, "ambiguousBaseProfile", ambiguousBaseProfile == null
			? "derived" : ambiguousBaseProfile.getId()).append(",");
		json.append("\"equipment\":{\"shield\":").append(shieldAppearanceId)
			.append(",\"weapon\":").append(weaponAppearanceId)
			.append(",\"hat\":").append(hatAppearanceId).append("}},");
		appendIntArray(json, "paletteLayerAnimation", palettePlayer.layerAnimation).append("},");
		json.append("\"canonicalStateCount\":").append(PaperdollV2Pose.canonical().size()).append(",");
		json.append("\"qaCaseCount\":").append(pack.getRenderStackIds().size()).append(",");
		json.append("\"renderStackCount\":").append(pack.getRenderStackIds().size()).append(",");
		json.append("\"captureCount\":").append(captures.size()).append(",");
		appendPaperdollV2Pack(json, pack).append(",");
		appendPaperdollV2Captures(json, captures).append(",");
		appendString(json, "generatedAt", isoTimestamp());
		json.append("}");
		Files.write(report.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
		return json.toString();
	}

	private static String paperdollV2RuntimeMatrixScenarioJson(Map<String, String> fields)
		throws IOException {
		requireLoggedIn();
		final mudclient client = requireClient();
		final ORSCharacter livePlayer = client.getLocalPlayer();
		if (livePlayer == null || livePlayer.layerAnimation == null) {
			throw new IOException("Local player appearance is not initialized");
		}
		final PaperdollV2Runtime runtime = client.workbenchPaperdollV2Runtime();
		if (runtime == null || !runtime.isRequestedByUser()) {
			throw new IOException("Paperdoll V2 runtime matrix requires an explicit V2 launch");
		}
		PaperdollV2Input input = resolvePaperdollV2Input(fields);
		PaperdollV2SelectorRegistry registry = runtime.getSelectorRegistry();
		PaperdollV2LegacyCompatibility compatibility = runtime.getLegacyCompatibility();
		if (runtime.isActive() && registry == null) {
			throw new IOException("An active V2 runtime matrix requires selector mode");
		}
		int maximumSelectorId = registry == null
			? (compatibility == null ? 0 : compatibility.getMaximumSelectorId())
			: registry.getHighestDefinedSelectorId();
		if (maximumSelectorId <= 0) {
			throw new IOException("Runtime matrix has no hairstyle selectors");
		}
		String comparisonArchiveSha256 = sha256(Files.readAllBytes(input.archiveFile.toPath()));
		PaperdollV2Pack runtimePack = runtime.getPack();
		if (runtimePack != null) {
			File configuredRuntimePack = new File(runtime.getPackPath()).getCanonicalFile();
			if (!configuredRuntimePack.equals(input.archiveFile.getCanonicalFile())
				|| !runtimePack.getArchiveSha256().equals(comparisonArchiveSha256)) {
				throw new IOException("Runtime matrix workspace/archive differs from the loaded V2 pack");
			}
		}

		String caseId = fields.containsKey("caseId") ? fields.get("caseId").trim() : "runtime";
		if (!caseId.matches("[a-z0-9][a-z0-9_-]{0,63}")) {
			throw new IOException("Paperdoll V2 runtime matrix caseId is not canonical");
		}
		final int[][] paletteLengths = new int[1][];
		runOnEdt(() -> paletteLengths[0] = client.workbenchAppearancePaletteLengths());
		final int hairColour = optionalPaletteIndex(fields, "hairColour",
			PAPERDOLL_V2_HAIR_COLOUR_INDEX, paletteLengths[0][0]);
		final int topColour = optionalPaletteIndex(fields, "topColour",
			livePlayer.colourTop, paletteLengths[0][1]);
		final int bottomColour = optionalPaletteIndex(fields, "bottomColour",
			livePlayer.colourBottom, paletteLengths[0][2]);
		final int skinColour = optionalPaletteIndex(fields, "skinColour",
			livePlayer.colourSkin, paletteLengths[0][3]);
		final int shieldAppearanceId = optionalAppearanceId(fields, "shieldAppearanceId", 98);
		final int weaponAppearanceId = optionalAppearanceId(fields, "weaponAppearanceId", 48);
		final int hatAppearanceId = optionalAppearanceId(fields, "hatAppearanceId", 0);
		final int bodyArmorAppearanceId = optionalAppearanceId(fields, "bodyArmorAppearanceId", 0);
		final int legArmorAppearanceId = optionalAppearanceId(fields, "legArmorAppearanceId", 0);
		final int glovesAppearanceId = optionalAppearanceId(fields, "glovesAppearanceId", 0);
		final int bootsAppearanceId = optionalAppearanceId(fields, "bootsAppearanceId", 0);
		final int amuletAppearanceId = optionalAppearanceId(fields, "amuletAppearanceId", 0);
		final int capeAppearanceId = optionalAppearanceId(fields, "capeAppearanceId", 0);
		final int backgroundRgb = optionalRgb(fields, "backgroundRgb", PAPERDOLL_V2_BACKGROUND);
		if (backgroundRgb != PAPERDOLL_V2_BACKGROUND) {
			throw new IOException("Paperdoll V2 runtime matrix backgroundRgb is locked to 121820");
		}

		File outputDirectory = createPaperdollV2OutputDirectory(input.workspaceDirectory);
		StringBuilder captures = new StringBuilder("[");
		int captureCount = 0;
		int v2Count = 0;
		int legacyCount = 0;
		int styleVisibleCount = 0;
		int styleSuppressedCount = 0;
		int activeV2HairOracleCount = 0;
		int legacyOverlayHairOracleCount = 0;
		for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
			PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
			final ORSCharacter classic = paperdollV2PreviewPlayer(profile, hairColour,
				topColour, bottomColour, skinColour, shieldAppearanceId,
				weaponAppearanceId, hatAppearanceId);
			classic.hairStyle = 0;
			classic.layerAnimation[6] = bodyArmorAppearanceId;
			classic.layerAnimation[7] = legArmorAppearanceId;
			classic.layerAnimation[8] = glovesAppearanceId;
			classic.layerAnimation[9] = bootsAppearanceId;
			classic.layerAnimation[10] = amuletAppearanceId;
			classic.layerAnimation[11] = capeAppearanceId;
			File caseDirectory = new File(outputDirectory,
				"selector_00_classic/" + profile.getId());
			Files.createDirectories(caseDirectory.toPath());
			for (PaperdollV2Pose pose : PaperdollV2Pose.canonical()) {
				PaperdollV2Runtime.PreflightObservation observation = runtime.inspectPreflight(
					classic.layerAnimation, 0, pose.getSpriteOffset(),
					PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
				if (observation.getSelection() != null) {
					throw new IOException("Classic selector unexpectedly entered V2");
				}
				legacyCount++;
				final int[][] raster = new int[1][];
				runOnEdt(() -> raster[0] = client.workbenchRenderPaperdollV2RuntimePathRaster(
					classic, pose, backgroundRgb));
				String fileName = String.format(Locale.ROOT, "%02d-%s.png",
					pose.getOrdinal(), pose.getKey());
				PaperdollV2RasterArtifact artifact = writePaperdollV2Raster(
					new File(caseDirectory, fileName), raster[0], backgroundRgb,
					caseId + "/0/" + profile.getId() + "/" + pose.getKey(), true);
				if (captureCount++ > 0) captures.append(',');
				captures.append("{\"selectorId\":0,");
				appendString(captures, "style", "classic").append(",");
				appendString(captures, "baseProfile", profile.getId()).append(",");
				appendString(captures, "stateKey", pose.getKey()).append(",");
				captures.append("\"ordinal\":").append(pose.getOrdinal()).append(",");
				captures.append("\"spriteOffset\":").append(pose.getSpriteOffset()).append(",");
				captures.append("\"mirrorX\":").append(pose.isMirrorX()).append(",");
				appendString(captures, "renderPath", "legacy").append(",");
				appendString(captures, "fallbackReason", observation.getFallbackReason()).append(",");
				appendString(captures, "hairVisibilityOracle",
					"classic-whole-player-legacy-baseline").append(",");
				captures.append("\"isolatedHairPixelCount\":0,\"styleBaselinePixelDiff\":0,");
				appendIntArray(captures, "layerAnimation", classic.layerAnimation).append(",");
				appendPaperdollV2Raster(captures, "raster", artifact, null,
					"whole-player-legacy");
				captures.append('}');
			}
		}
		for (int selectorId = 1; selectorId <= maximumSelectorId; selectorId++) {
			String style = registry != null && registry.getEntry(selectorId) != null
				? registry.getEntry(selectorId).getStyle()
				: compatibility == null ? "" : compatibility.getStyle(selectorId);
			if (style.length() == 0) {
				throw new IOException("Runtime matrix selector " + selectorId + " has no style identity");
			}
			for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
				PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
				final ORSCharacter player = paperdollV2PreviewPlayer(profile, hairColour,
					topColour, bottomColour, skinColour, shieldAppearanceId,
					weaponAppearanceId, hatAppearanceId);
				player.hairStyle = selectorId;
				player.layerAnimation[6] = bodyArmorAppearanceId;
				player.layerAnimation[7] = legArmorAppearanceId;
				player.layerAnimation[8] = glovesAppearanceId;
				player.layerAnimation[9] = bootsAppearanceId;
				player.layerAnimation[10] = amuletAppearanceId;
				player.layerAnimation[11] = capeAppearanceId;
				final ORSCharacter baseline = paperdollV2PreviewPlayer(profile, hairColour,
					topColour, bottomColour, skinColour, shieldAppearanceId,
					weaponAppearanceId, hatAppearanceId);
				baseline.hairStyle = 0;
				System.arraycopy(player.layerAnimation, 3, baseline.layerAnimation, 3, 9);
				File caseDirectory = new File(outputDirectory,
					String.format(Locale.ROOT, "selector_%02d_%s/%s", selectorId, style,
						profile.getId()));
				Files.createDirectories(caseDirectory.toPath());
				for (PaperdollV2Pose pose : PaperdollV2Pose.canonical()) {
					PaperdollV2Runtime.PreflightObservation observation = runtime.inspectPreflight(
						player.layerAnimation, selectorId, pose.getSpriteOffset(),
						PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
					boolean usedV2 = observation.getSelection() != null;
					if (usedV2) v2Count++; else legacyCount++;
					final int[][] raster = new int[1][];
					final int[][] baselineRaster = new int[1][];
					runOnEdt(() -> raster[0] = client.workbenchRenderPaperdollV2RuntimePathRaster(
						player, pose, backgroundRgb));
					runOnEdt(() -> baselineRaster[0] =
						client.workbenchRenderPaperdollV2RuntimePathRaster(
							baseline, pose, backgroundRgb));
					int styleBaselinePixelDiff = pixelDifferenceCount(raster[0], baselineRaster[0]);
					int isolatedHairPixelCount = 0;
					String hairVisibilityOracle;
					if (hatAppearanceId == 0) {
						if (usedV2) {
							final int[][] hairRaster = new int[1][];
							runOnEdt(() -> hairRaster[0] =
								client.workbenchRenderPaperdollV2HairLayerRaster(
									player, pose, backgroundRgb));
							isolatedHairPixelCount = nonBackgroundPixelCount(
								hairRaster[0], backgroundRgb);
							if (isolatedHairPixelCount <= 0) {
								throw new IOException("Runtime matrix V2 hair layer is empty for selector "
									+ selectorId + " in " + profile.getId() + "/" + pose.getKey());
							}
							hairVisibilityOracle = "isolated-live-v2-hair-layer";
							activeV2HairOracleCount++;
						} else {
							if (styleBaselinePixelDiff <= 0) {
								throw new IOException("Runtime matrix lost legacy compatibility hairstyle "
									+ selectorId + " in " + profile.getId() + "/" + pose.getKey());
							}
							hairVisibilityOracle = "same-base-legacy-overlay-difference";
							legacyOverlayHairOracleCount++;
						}
						styleVisibleCount++;
					} else {
						if (usedV2 || styleBaselinePixelDiff != 0) {
							throw new IOException("Runtime matrix nonzero hat did not atomically suppress hair");
						}
						hairVisibilityOracle = "same-base-hat-suppression-equality";
						styleSuppressedCount++;
					}
					String fileName = String.format(Locale.ROOT, "%02d-%s.png",
						pose.getOrdinal(), pose.getKey());
					PaperdollV2RasterArtifact artifact = writePaperdollV2Raster(
						new File(caseDirectory, fileName), raster[0], backgroundRgb,
						caseId + "/" + selectorId + "/" + profile.getId() + "/" + pose.getKey(),
						true);
					if (captureCount++ > 0) captures.append(',');
					captures.append("{\"selectorId\":").append(selectorId).append(",");
					appendString(captures, "style", style).append(",");
					appendString(captures, "baseProfile", profile.getId()).append(",");
					appendString(captures, "stateKey", pose.getKey()).append(",");
					captures.append("\"ordinal\":").append(pose.getOrdinal()).append(",");
					captures.append("\"spriteOffset\":").append(pose.getSpriteOffset()).append(",");
					captures.append("\"mirrorX\":").append(pose.isMirrorX()).append(",");
					appendString(captures, "renderPath", usedV2 ? "v2" : "legacy").append(",");
					appendString(captures, "fallbackReason", observation.getFallbackReason()).append(",");
					appendString(captures, "hairVisibilityOracle", hairVisibilityOracle).append(",");
					captures.append("\"isolatedHairPixelCount\":")
						.append(isolatedHairPixelCount).append(",");
					captures.append("\"styleBaselinePixelDiff\":")
						.append(styleBaselinePixelDiff).append(",");
					appendIntArray(captures, "layerAnimation", player.layerAnimation).append(",");
					appendPaperdollV2Raster(captures, "raster", artifact, null,
						usedV2 ? "live-runtime-v2" : "whole-player-legacy");
					captures.append('}');
				}
			}
		}
		captures.append(']');
		int expectedCaptureCount = (maximumSelectorId + 1) * 2
			* PaperdollV2Pose.canonical().size();
		if (captureCount != expectedCaptureCount || v2Count + legacyCount != captureCount) {
			throw new IOException("Paperdoll V2 runtime matrix is partial");
		}

		File report = new File(outputDirectory, "runtime-matrix-report.json").getAbsoluteFile();
		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "scenario", "paperdoll-v2-runtime-matrix").append(",");
		appendString(json, "caseId", caseId).append(",");
		json.append("\"workbenchOnly\":true,\"liveActorStateMutated\":false,");
		appendString(json, runtimePack == null ? "comparisonOracleArchivePath" : "runtimeArchivePath",
			input.archiveFile.getAbsolutePath()).append(",");
		appendString(json, runtimePack == null ? "comparisonOracleArchiveSha256" : "runtimeArchiveSha256",
			comparisonArchiveSha256).append(",");
		appendString(json, "configuredRuntimePackPath", runtime.getPackPath()).append(",");
		appendString(json, "outputDirectory", outputDirectory.getAbsolutePath()).append(",");
		appendString(json, "reportPath", report.getAbsolutePath()).append(",");
		json.append("\"runtime\":{");
		json.append("\"active\":").append(runtime.isActive()).append(",");
		json.append("\"forceLegacy\":").append(runtime.isForceLegacy()).append(",");
		appendString(json, "fallbackReason", runtime.getFallbackReason()).append(",");
		json.append("\"compatibilityRootValid\":")
			.append(compatibility != null && compatibility.isRootValid()).append(",");
		json.append("\"compatibilityLoadedStyleCount\":")
			.append(compatibility == null ? 0 : compatibility.getLoadedStyleCount()).append("},");
		json.append("\"palette\":{"
			+ "\"hair\":").append(hairColour).append(",\"top\":").append(topColour)
			.append(",\"bottom\":").append(bottomColour).append(",\"skin\":")
			.append(skinColour).append("},");
		json.append("\"equipment\":{");
		json.append("\"shield\":").append(shieldAppearanceId)
			.append(",\"weapon\":").append(weaponAppearanceId)
			.append(",\"hat\":").append(hatAppearanceId)
			.append(",\"bodyArmor\":").append(bodyArmorAppearanceId)
			.append(",\"legArmor\":").append(legArmorAppearanceId)
			.append(",\"gloves\":").append(glovesAppearanceId)
			.append(",\"boots\":").append(bootsAppearanceId)
			.append(",\"amulet\":").append(amuletAppearanceId)
			.append(",\"cape\":").append(capeAppearanceId).append("},");
		json.append("\"captureCount\":").append(captureCount)
			.append(",\"v2CaptureCount\":").append(v2Count)
			.append(",\"legacyCaptureCount\":").append(legacyCount)
			.append(",\"styleVisibleCount\":").append(styleVisibleCount)
			.append(",\"styleSuppressedCount\":").append(styleSuppressedCount)
			.append(",\"activeV2HairOracleCount\":").append(activeV2HairOracleCount)
			.append(",\"legacyOverlayHairOracleCount\":")
			.append(legacyOverlayHairOracleCount)
			.append(",\"captures\":").append(captures).append(",");
		appendString(json, "generatedAt", isoTimestamp());
		json.append('}');
		Files.write(report.toPath(), json.toString().getBytes(StandardCharsets.UTF_8));
		return json.toString();
	}

	private static int pixelDifferenceCount(int[] left, int[] right) throws IOException {
		if (left == null || right == null || left.length != right.length) {
			throw new IOException("Paperdoll V2 runtime matrix raster geometry differs");
		}
		int differences = 0;
		for (int index = 0; index < left.length; index++) {
			if ((left[index] & 0x00ffffff) != (right[index] & 0x00ffffff)) differences++;
		}
		return differences;
	}

	private static int nonBackgroundPixelCount(int[] raster, int backgroundRgb)
		throws IOException {
		if (raster == null) throw new IOException("Paperdoll V2 raster is unavailable");
		int background = backgroundRgb & 0x00ffffff;
		int count = 0;
		for (int pixel : raster) if ((pixel & 0x00ffffff) != background) count++;
		return count;
	}

	private static PaperdollV2RuntimeSelection resolvePaperdollV2RuntimeSelection(
		PaperdollV2Runtime runtime, Map<String, String> fields) throws IOException {
		if (runtime.isSelectorModeRequested()) {
			PaperdollV2SelectorRegistry registry = runtime.getSelectorRegistry();
			if (registry == null) throw new IOException("Paperdoll V2 selector registry is unavailable");
			int selectorId = fields.containsKey("selectorId") ? requiredInt(fields, "selectorId") : 1;
			PaperdollV2SelectorRegistry.Entry entry = registry.getEntry(selectorId);
			if (entry == null || !entry.isV2Route()) {
				throw new IOException("Paperdoll V2 live fixture requires a positive V2 selector: "
					+ selectorId);
			}
			PaperdollV2BaseProfile baseProfile = optionalBaseProfile(fields, "baseProfile");
			if (baseProfile == null) baseProfile = PaperdollV2BaseProfile.MALE;
			PaperdollV2Runtime.RenderSelection selected = entry.getSelection(baseProfile);
			if (selected == null) {
				throw new IOException("Paperdoll V2 selector " + selectorId
					+ " does not support base profile " + baseProfile.getId());
			}
			return new PaperdollV2RuntimeSelection(true, selectorId, entry.getStyle(),
				baseProfile, selected.getStack(), selectorId);
		}

		if (fields.containsKey("selectorId")) {
			throw new IOException("selectorId requires Paperdoll V2 selector-registry mode");
		}
		PaperdollV2BaseProfile baseProfile = runtime.getBaseProfile();
		PaperdollV2Pack.RenderStack stack = runtime.getStack();
		if (baseProfile == null || stack == null) {
			throw new IOException("Paperdoll V2 global live stack has no base profile");
		}
		PaperdollV2BaseProfile requestedProfile = optionalBaseProfile(fields, "baseProfile");
		if (requestedProfile != null && requestedProfile != baseProfile) {
			throw new IOException("Global Paperdoll V2 stack " + stack.getId()
				+ " is pinned to base profile " + baseProfile.getId());
		}
		return new PaperdollV2RuntimeSelection(false, -1, "", baseProfile, stack, 0);
	}

	/**
	 * Temporarily supplies the selected stack's approved base anatomy to the actual local player,
	 * waits for the normal live Scene path to render it, captures, and restores all
	 * client state. No packet or server appearance mutation is involved.
	 */
	private static String paperdollV2LiveSceneScenarioJson(Map<String, String> fields)
		throws IOException {
		requireLoggedIn();
		clearWorkbenchBlockingUi();
		sleep(250);
		final mudclient client = requireClient();
		final ORSCharacter player = client.getLocalPlayer();
		if (player == null || player.layerAnimation == null || player.layerAnimation.length < 12) {
			throw new IOException("Local player appearance is not initialized");
		}
		final PaperdollV2Runtime runtime = client.workbenchPaperdollV2Runtime();
		if (runtime == null || !runtime.isActive()) {
			throw new IOException("Paperdoll V2 live runtime is not active: "
				+ (runtime == null ? "missing-runtime" : runtime.getFallbackReason()));
		}
		if (client.getGameWidth() != PaperdollV2Runtime.VIEWPORT_WIDTH
			|| client.getGameHeight() != PaperdollV2Runtime.GAME_HEIGHT
			|| client.workbenchPaperdollV2ProjectionShift() != PaperdollV2Runtime.PROJECTION_SHIFT) {
			throw new IOException("Paperdoll V2 live scene is not at the locked 1024x668/shift-10 target");
		}
		final PaperdollV2RuntimeSelection runtimeSelection =
			resolvePaperdollV2RuntimeSelection(runtime, fields);
		final PaperdollV2BaseProfile baseProfile = runtimeSelection.baseProfile;

		final CowboyPreviewState original = new CowboyPreviewState(client, player, 0);
		final PaperdollV2Runtime.TelemetrySnapshot telemetryBefore = runtime.snapshotTelemetry();
		PaperdollV2Runtime.TelemetrySnapshot telemetryAfter = telemetryBefore;
		CaptureResult capture = null;
		String activeState = null;
		boolean restored = false;
		try {
			final int[] appliedAt = new int[1];
			runOnEdt(() -> {
				client.setPlayer(original.renderPlayerIndex, player);
				baseProfile.applyTo(player.layerAnimation);
				player.layerAnimation[PLAYER_HAT_LAYER] = 0;
				player.colourHair = PAPERDOLL_V2_HAIR_COLOUR_INDEX;
				player.hairStyle = runtimeSelection.hairStyle;
				player.isInvisible = false;
				player.isInvulnerable = false;
				player.direction = ORSCharacterDirection.NORTH;
				player.animationNext = ORSCharacterDirection.NORTH.rsDir;
				player.stepFrame = 0;
				client.cameraRotation = 0;
				appliedAt[0] = client.getFrameCounter();
			});
			waitForRendererAdvance(client, appliedAt[0], 3, 2000);
			if (runtime.getLocalV2RenderCount() <= telemetryBefore.getLocalV2()) {
				throw new IOException("Live renderer did not enter the Paperdoll V2 branch");
			}
			capture = captureOnce("paperdoll-v2-live-scene-" + baseProfile.getId()
				+ "-" + sanitizeReason(runtimeSelection.stack.getId()));
			activeState = stateJson(capture.pngFile, capture.width, capture.height);
			telemetryAfter = runtime.snapshotTelemetry();
		} finally {
			original.restore(client, player);
			waitForRendererAdvance(client, client.getFrameCounter(), 1, 1000);
			restored = original.matches(client, player);
		}
		if (!restored) throw new IOException("Paperdoll V2 live scene fixture did not restore cleanly");

		StringBuilder json = new StringBuilder();
		json.append("{\"ok\":true,");
		appendString(json, "scenario", "paperdoll-v2-live-scene").append(",");
		json.append("\"selectorMode\":").append(runtimeSelection.selectorMode).append(",");
		json.append("\"selectorId\":").append(runtimeSelection.selectorId).append(",");
		appendString(json, "style", runtimeSelection.style).append(",");
		appendString(json, "baseProfile", baseProfile.getId()).append(",");
		appendString(json, "selectedStackId", runtimeSelection.stack.getId()).append(",");
		json.append("\"temporaryHairStyle\":").append(runtimeSelection.hairStyle).append(",");
		json.append("\"temporaryCompatibleHeadAppearanceId\":")
			.append(baseProfile.getHeadAppearanceId()).append(",");
		json.append("\"temporaryCompatibleAppearances\":{\"head\":")
			.append(baseProfile.getHeadAppearanceId()).append(",\"body\":")
			.append(baseProfile.getBodyAppearanceId()).append(",\"legs\":")
			.append(baseProfile.getLegsAppearanceId()).append("},");
		appendIntArray(json, "nativePaperdollSlots",
			runtimeSelection.stack.getNativePaperdollSlots()).append(",");
		json.append("\"serverStateMutated\":false,");
		json.append("\"restored\":true,");
		json.append("\"v2RenderCountBefore\":").append(telemetryBefore.getV2()).append(",");
		json.append("\"v2RenderCountAfter\":").append(telemetryAfter.getV2()).append(",");
		json.append("\"v2RenderCountDelta\":")
			.append(telemetryAfter.getV2() - telemetryBefore.getV2()).append(",");
		json.append("\"legacyRenderCountBefore\":").append(telemetryBefore.getLegacy()).append(",");
		json.append("\"legacyRenderCountAfter\":").append(telemetryAfter.getLegacy()).append(",");
		json.append("\"legacyRenderCountDelta\":")
			.append(telemetryAfter.getLegacy() - telemetryBefore.getLegacy()).append(",");
		json.append("\"localV2RenderCountBefore\":").append(telemetryBefore.getLocalV2()).append(",");
		json.append("\"localV2RenderCountAfter\":").append(telemetryAfter.getLocalV2()).append(",");
		json.append("\"localV2RenderCountDelta\":")
			.append(telemetryAfter.getLocalV2() - telemetryBefore.getLocalV2()).append(",");
		json.append("\"localLegacyRenderCountBefore\":")
			.append(telemetryBefore.getLocalLegacy()).append(",");
		json.append("\"localLegacyRenderCountAfter\":")
			.append(telemetryAfter.getLocalLegacy()).append(",");
		json.append("\"localLegacyRenderCountDelta\":")
			.append(telemetryAfter.getLocalLegacy() - telemetryBefore.getLocalLegacy()).append(",");
		json.append("\"remoteV2RenderCountBefore\":").append(telemetryBefore.getRemoteV2()).append(",");
		json.append("\"remoteV2RenderCountAfter\":").append(telemetryAfter.getRemoteV2()).append(",");
		json.append("\"remoteV2RenderCountDelta\":")
			.append(telemetryAfter.getRemoteV2() - telemetryBefore.getRemoteV2()).append(",");
		json.append("\"remoteLegacyRenderCountBefore\":")
			.append(telemetryBefore.getRemoteLegacy()).append(",");
		json.append("\"remoteLegacyRenderCountAfter\":")
			.append(telemetryAfter.getRemoteLegacy()).append(",");
		json.append("\"remoteLegacyRenderCountDelta\":")
			.append(telemetryAfter.getRemoteLegacy() - telemetryBefore.getRemoteLegacy()).append(",");
		appendString(json, "telemetryActor", "local-player-only").append(",");
		appendSingleCapture(json, capture);
		json.append(",\"activeState\":").append(activeState).append(",");
		appendString(json, "generatedAt", isoTimestamp());
		json.append("}");
		return json.toString();
	}

	/** Holds one compatible local-player fixture long enough for a meaningful render sample. */
	private static String paperdollV2BenchmarkScenarioJson(Map<String, String> fields)
		throws IOException {
		requireLoggedIn();
		clearWorkbenchBlockingUi();
		sleep(250);
		final mudclient client = requireClient();
		final ORSCharacter player = client.getLocalPlayer();
		if (player == null || player.layerAnimation == null || player.layerAnimation.length < 12) {
			throw new IOException("Local player appearance is not initialized");
		}
		final PaperdollV2Runtime runtime = client.workbenchPaperdollV2Runtime();
		if (runtime == null || !runtime.isRequestedByUser() || !runtime.isPackValid()
			|| (!runtime.isActive() && !runtime.isForceLegacy())) {
			throw new IOException("Paperdoll V2 benchmark requires an active or forced-legacy validated runtime");
		}
		if (client.getGameWidth() != PaperdollV2Runtime.VIEWPORT_WIDTH
			|| client.getGameHeight() != PaperdollV2Runtime.GAME_HEIGHT
			|| client.workbenchPaperdollV2ProjectionShift() != PaperdollV2Runtime.PROJECTION_SHIFT) {
			throw new IOException("Paperdoll V2 benchmark is not at the locked 1024x668/shift-10 target");
		}
		final PaperdollV2RuntimeSelection runtimeSelection =
			resolvePaperdollV2RuntimeSelection(runtime, fields);
		final PaperdollV2BaseProfile baseProfile = runtimeSelection.baseProfile;

		final boolean benchmarkV2 = runtime.isActive();
		final CowboyPreviewState original = new CowboyPreviewState(client, player, 0);
		long wallElapsed = 0L;
		PaperdollV2Runtime.BenchmarkSnapshot benchmark = null;
		CaptureResult capture = null;
		boolean restored = false;
		try {
			final int[] appliedAt = new int[1];
			runOnEdt(() -> {
				client.setPlayer(original.renderPlayerIndex, player);
				baseProfile.applyTo(player.layerAnimation);
				player.layerAnimation[PLAYER_HAT_LAYER] = 0;
				player.colourHair = PAPERDOLL_V2_HAIR_COLOUR_INDEX;
				player.hairStyle = runtimeSelection.hairStyle;
				player.isInvisible = false;
				player.isInvulnerable = false;
				player.direction = ORSCharacterDirection.NORTH;
				player.animationNext = ORSCharacterDirection.NORTH.rsDir;
				player.stepFrame = 0;
				client.cameraRotation = 0;
				appliedAt[0] = client.getFrameCounter();
			});
			waitForRendererAdvance(client, appliedAt[0], 3, 2000);
			long warmV2Start = runtime.getLocalV2RenderCount();
			long warmLegacyStart = runtime.getLocalLegacyFallbackRenderCount();
			long warmDeadline = System.nanoTime() + 10_000_000_000L;
			while ((benchmarkV2
				? runtime.getLocalV2RenderCount() - warmV2Start
				: runtime.getLocalLegacyFallbackRenderCount() - warmLegacyStart)
				< PAPERDOLL_V2_BENCHMARK_WARMUP_RENDERS) {
				if (System.nanoTime() >= warmDeadline) {
					throw new IOException("Paperdoll V2 benchmark timed out during unmeasured warm-up");
				}
				sleep(20);
			}
			if ((benchmarkV2 && runtime.getLocalLegacyFallbackRenderCount() != warmLegacyStart)
				|| (!benchmarkV2 && runtime.getLocalV2RenderCount() != warmV2Start)) {
				throw new IOException("Paperdoll V2 benchmark warm-up entered the unexpected render path");
			}

			runtime.beginDevBenchmark(benchmarkV2, PAPERDOLL_V2_BENCHMARK_MINIMUM_RENDERS);
			long wallStarted = System.nanoTime();
			long deadline = wallStarted + 30_000_000_000L;
			while (runtime.getDevBenchmarkSampleCount() < PAPERDOLL_V2_BENCHMARK_MINIMUM_RENDERS) {
				if (System.nanoTime() >= deadline) {
					throw new IOException("Paperdoll V2 benchmark timed out before 600 local-player renders");
				}
				sleep(20);
			}
			wallElapsed = System.nanoTime() - wallStarted;
			benchmark = runtime.endDevBenchmark();
			if (benchmark.getUnexpectedPathCount() != 0L) {
				throw new IOException("Paperdoll V2 benchmark entered the unexpected render path");
			}
			capture = captureOnce("paperdoll-v2-benchmark-"
				+ (benchmarkV2 ? "active" : "forced-legacy") + "-" + baseProfile.getId()
				+ "-" + sanitizeReason(runtimeSelection.stack.getId()));
		} finally {
			runtime.cancelDevBenchmark();
			original.restore(client, player);
			waitForRendererAdvance(client, client.getFrameCounter(), 1, 1000);
			restored = original.matches(client, player);
		}
		if (!restored) throw new IOException("Paperdoll V2 benchmark fixture did not restore cleanly");
		if (benchmark == null || capture == null) {
			throw new IOException("Paperdoll V2 benchmark produced no measured artifact");
		}

		long[] samples = benchmark.getDurationsNanos();
		if (samples.length != PAPERDOLL_V2_BENCHMARK_MINIMUM_RENDERS) {
			throw new IOException("Paperdoll V2 benchmark returned a partial measured sample");
		}
		long expectedLocalV2Delta = benchmarkV2 ? samples.length : 0L;
		long expectedLocalLegacyDelta = benchmarkV2 ? 0L : samples.length;
		if (benchmark.getLocalV2Delta() != expectedLocalV2Delta
			|| benchmark.getLocalLegacyDelta() != expectedLocalLegacyDelta) {
			throw new IOException("Paperdoll V2 benchmark telemetry was not exactly 600 "
				+ "local-player renders on the expected path");
		}
		Arrays.sort(samples);
		long sampleNanos = 0L;
		for (long sample : samples) sampleNanos += sample;
		long p50 = percentileNearestRank(samples, 50);
		long p95 = percentileNearestRank(samples, 95);
		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "scenario", "paperdoll-v2-benchmark").append(",");
		appendString(json, "mode", benchmarkV2 ? "active-v2" : "forced-legacy").append(",");
		json.append("\"selectorMode\":").append(runtimeSelection.selectorMode).append(",");
		json.append("\"selectorId\":").append(runtimeSelection.selectorId).append(",");
		appendString(json, "style", runtimeSelection.style).append(",");
		appendString(json, "selectedStackId", runtimeSelection.stack.getId()).append(",");
		appendString(json, "baseProfile", baseProfile.getId()).append(",");
		json.append("\"warmupRenderCount\":").append(PAPERDOLL_V2_BENCHMARK_WARMUP_RENDERS).append(",");
		json.append("\"minimumRenderCount\":").append(PAPERDOLL_V2_BENCHMARK_MINIMUM_RENDERS).append(",");
		json.append("\"sampleCount\":").append(samples.length).append(",");
		json.append("\"sampleNanos\":").append(sampleNanos).append(",");
		json.append("\"averageRenderNanos\":").append(sampleNanos / samples.length).append(",");
		json.append("\"meanRenderNanos\":").append(sampleNanos / samples.length).append(",");
		json.append("\"medianRenderNanos\":").append(p50).append(",");
		json.append("\"p50RenderNanos\":").append(p50).append(",");
		json.append("\"p95RenderNanos\":").append(p95).append(",");
		json.append("\"minRenderNanos\":").append(samples[0]).append(",");
		json.append("\"maxRenderNanos\":").append(samples[samples.length - 1]).append(",");
		json.append("\"unexpectedPathCount\":").append(benchmark.getUnexpectedPathCount()).append(",");
		json.append("\"wallElapsedNanos\":").append(wallElapsed).append(",");
		json.append("\"rendersPerSecond\":")
			.append(wallElapsed == 0 ? "0.0" : String.format(Locale.ROOT, "%.2f",
				samples.length * 1_000_000_000.0 / wallElapsed)).append(",");
		json.append("\"v2RenderCountBefore\":").append(benchmark.getV2Before()).append(",");
		json.append("\"v2RenderCountAfter\":").append(benchmark.getV2After()).append(",");
		json.append("\"v2RenderCountDelta\":").append(benchmark.getV2Delta()).append(",");
		json.append("\"legacyRenderCountBefore\":").append(benchmark.getLegacyBefore()).append(",");
		json.append("\"legacyRenderCountAfter\":").append(benchmark.getLegacyAfter()).append(",");
		json.append("\"legacyRenderCountDelta\":").append(benchmark.getLegacyDelta()).append(",");
		json.append("\"localV2RenderCountBefore\":").append(benchmark.getLocalV2Before()).append(",");
		json.append("\"localV2RenderCountAfter\":").append(benchmark.getLocalV2After()).append(",");
		json.append("\"localV2RenderCountDelta\":").append(benchmark.getLocalV2Delta()).append(",");
		json.append("\"localLegacyRenderCountBefore\":")
			.append(benchmark.getLocalLegacyBefore()).append(",");
		json.append("\"localLegacyRenderCountAfter\":")
			.append(benchmark.getLocalLegacyAfter()).append(",");
		json.append("\"localLegacyRenderCountDelta\":")
			.append(benchmark.getLocalLegacyDelta()).append(",");
		json.append("\"remoteV2RenderCountBefore\":").append(benchmark.getRemoteV2Before()).append(",");
		json.append("\"remoteV2RenderCountAfter\":").append(benchmark.getRemoteV2After()).append(",");
		json.append("\"remoteV2RenderCountDelta\":").append(benchmark.getRemoteV2Delta()).append(",");
		json.append("\"remoteLegacyRenderCountBefore\":")
			.append(benchmark.getRemoteLegacyBefore()).append(",");
		json.append("\"remoteLegacyRenderCountAfter\":")
			.append(benchmark.getRemoteLegacyAfter()).append(",");
		json.append("\"remoteLegacyRenderCountDelta\":")
			.append(benchmark.getRemoteLegacyDelta()).append(",");
		json.append("\"sampledLocalRenderCount\":").append(samples.length).append(",");
		json.append("\"sampledRemoteRenderCount\":0,");
		json.append("\"fixture\":{");
		appendString(json, "pose", "north-walk-frame-0-stationary").append(",");
		json.append("\"hairStyle\":").append(runtimeSelection.hairStyle).append(",");
		json.append("\"hairColour\":").append(PAPERDOLL_V2_HAIR_COLOUR_INDEX).append(",");
		json.append("\"topColour\":").append(original.colourTop).append(",");
		json.append("\"bottomColour\":").append(original.colourBottom).append(",");
		json.append("\"skinColour\":").append(original.colourSkin).append(",");
		json.append("\"hatAppearanceId\":0,\"invisible\":false,\"invulnerable\":false,\"baseAppearances\":{")
			.append("\"head\":").append(baseProfile.getHeadAppearanceId())
			.append(",\"body\":").append(baseProfile.getBodyAppearanceId())
			.append(",\"legs\":").append(baseProfile.getLegsAppearanceId()).append("}},");
		appendString(json, "timingScope",
			"local-player per-render durations only; remote actors, warm-up, HTTP, JSON, and screenshot I/O excluded").append(",");
		appendString(json, "telemetryActor", "local-player-only").append(",");
		appendString(json, "comparisonMode", "separate active-v2 and forced-legacy runs").append(",");
		appendString(json, "expectedVisualComparison",
			"different: native selected stack versus legacy whole-player baseline").append(",");
		appendString(json, "outputPngSha256", sha256(Files.readAllBytes(capture.pngFile.toPath()))).append(",");
		json.append("\"serverStateMutated\":false,\"restored\":true,");
		appendSingleCapture(json, capture);
		json.append(",");
		appendString(json, "generatedAt", isoTimestamp());
		json.append("}");
		return json.toString();
	}

	/**
	 * Pure Workbench selector fixture: all routes are resolved from synthetic players,
	 * and the two positive controls are rendered together without live/server mutation.
	 */
	private static String paperdollV2SelectorResolutionScenarioJson() throws IOException {
		requireLoggedIn();
		final mudclient client = requireClient();
		final PaperdollV2Runtime runtime = client.workbenchPaperdollV2Runtime();
		if (runtime == null || !runtime.isSelectorModeActive()) {
			throw new IOException("Paperdoll V2 selector fixture requires an active selector registry: "
				+ (runtime == null ? "missing-runtime" : runtime.getFallbackReason()));
		}
		final PaperdollV2SelectorRegistry registry = runtime.getSelectorRegistry();
		if (registry == null || registry.getEntries().size() != 7) {
			throw new IOException("Paperdoll V2 selector fixture requires selectors 0 through 6");
		}

		long revisionBefore = runtime.getStackSelectionRevision();
		StringBuilder resolutionCases = new StringBuilder("[");
		int resolutionCount = 0;
		for (int selectorId = 1; selectorId <= 6; selectorId++) {
			PaperdollV2SelectorRegistry.Entry entry = registry.getEntry(selectorId);
			for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
				PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
				int[] layers = new int[12];
				profile.applyTo(layers);
				layers[3] = PAPERDOLL_V2_SHIELD_APPEARANCE_ID;
				layers[4] = PAPERDOLL_V2_WEAPON_APPEARANCE_ID;
				PaperdollV2Runtime.PreflightObservation observation = runtime.inspectPreflight(
					layers, selectorId, 0, PaperdollV2Pack.WALK_WIDTH,
					PaperdollV2Pack.PLAYER_HEIGHT);
				PaperdollV2Runtime.RenderSelection selected = observation.getSelection();
				if (selected == null || selected.getSelectorId() != selectorId
					|| selected.getBaseProfile() != profile
					|| !entry.getStackId(profile).equals(selected.getStack().getId())) {
					throw new IOException("Selector " + selectorId + " failed " + profile.getId()
						+ " resolution: " + observation.getFallbackReason());
				}
				if (selected.getStack().substitutesSlot(3) || selected.getStack().substitutesSlot(4)
					|| selected.getStack().substitutesSlot(5)
					|| layers[3] != PAPERDOLL_V2_SHIELD_APPEARANCE_ID
					|| layers[4] != PAPERDOLL_V2_WEAPON_APPEARANCE_ID) {
					throw new IOException("Selector " + selectorId
						+ " replaced or mutated legacy weapon/shield slots");
				}
				for (int repeat = 0; repeat < 256; repeat++) {
					if (runtime.preflight(layers, selectorId, 0, PaperdollV2Pack.WALK_WIDTH,
						PaperdollV2Pack.PLAYER_HEIGHT) != selected) {
						throw new IOException("Selector " + selectorId
							+ " allocated/replaced its cached render selection");
					}
				}
				if (resolutionCount++ > 0) resolutionCases.append(",");
				resolutionCases.append("{");
				resolutionCases.append("\"selectorId\":").append(selectorId).append(",");
				appendString(resolutionCases, "style", selected.getStyle()).append(",");
				appendString(resolutionCases, "baseProfile", profile.getId()).append(",");
				appendString(resolutionCases, "stackId", selected.getStack().getId()).append(",");
				appendIntArray(resolutionCases, "nativePaperdollSlots",
					selected.getStack().getNativePaperdollSlots()).append(",");
				resolutionCases.append("\"cachedSelectionIdentityStable\":true,");
				resolutionCases.append("\"shieldAppearanceId\":")
					.append(layers[3]).append(",\"weaponAppearanceId\":").append(layers[4]);
				resolutionCases.append("}");
			}
		}
		resolutionCases.append("]");
		int[] allocationLayers = new int[12];
		PaperdollV2BaseProfile.MALE.applyTo(allocationLayers);
		allocationLayers[3] = PAPERDOLL_V2_SHIELD_APPEARANCE_ID;
		allocationLayers[4] = PAPERDOLL_V2_WEAPON_APPEARANCE_ID;
		long[] allocationProbe = paperdollV2SelectorAllocationProbe(runtime, allocationLayers, 1);

		int[] maleLayers = new int[12];
		PaperdollV2BaseProfile.MALE.applyTo(maleLayers);
		String selectorZeroReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 0,
			"classic-selector-legacy");
		String unknownSelectorReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 255,
			"unknown-selector-255");
		maleLayers[PLAYER_HAT_LAYER] = COWBOY_HAT_APPEARANCE_ID;
		String cowboyReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 1,
			"unsupported-hat-appearance-245");
		maleLayers[PLAYER_HAT_LAYER] = 151;
		String ordinaryHatReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 1,
			"unsupported-hat-appearance-151");
		maleLayers[PLAYER_HAT_LAYER] = -1;
		String unknownHatReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 1,
			"unsupported-hat-appearance--1");
		PaperdollV2BaseProfile.MALE.applyTo(maleLayers);
		maleLayers[PLAYER_HAT_LAYER] = 0;
		maleLayers[1] = 1;
		String incompatibleBaseReason = expectPaperdollV2SelectorFallback(runtime, maleLayers, 1,
			"incompatible-base-identity-");

		final int[][] paletteLengths = new int[1][];
		runOnEdt(() -> paletteLengths[0] = client.workbenchAppearancePaletteLengths());
		if (paletteLengths[0] == null || paletteLengths[0].length < 4 || paletteLengths[0][3] < 3) {
			throw new IOException("Paperdoll V2 selector fixture needs at least three skin palette entries");
		}
		int[] skinIndices = {0, paletteLengths[0][3] / 2, paletteLengths[0][3] - 1};
		StringBuilder skinSourceCases = new StringBuilder("[");
		int skinSourceIndex = 0;
		for (PaperdollV2BaseProfile profile : new PaperdollV2BaseProfile[] {
			PaperdollV2BaseProfile.MALE, PaperdollV2BaseProfile.FEMALE}) {
			PaperdollV2Pack.RenderStack stack = registry.getEntry(1).getSelection(profile).getStack();
			long[] headSource = paperdollV2ValidateSkinSource(stack, 0, "head");
			long[] bodySource = paperdollV2ValidateSkinSource(stack, 1, "body");
			if (skinSourceIndex++ > 0) skinSourceCases.append(",");
			skinSourceCases.append("{");
			appendString(skinSourceCases, "baseProfile", profile.getId()).append(",");
			appendPaperdollV2SkinSourceCounts(skinSourceCases, "head", headSource).append(",");
			appendPaperdollV2SkinSourceCounts(skinSourceCases, "body", bodySource);
			skinSourceCases.append("}");
		}
		skinSourceCases.append("]");
		StringBuilder skinPaletteCases = new StringBuilder("[");
		for (int index = 0; index < skinIndices.length; index++) {
			if (index > 0) skinPaletteCases.append(",");
			int skinIndex = skinIndices[index];
			ORSCharacter skinPlayer = paperdollV2PreviewPlayer(PaperdollV2BaseProfile.MALE,
				PAPERDOLL_V2_HAIR_COLOUR_INDEX, 0, 0, skinIndex, 0, 0, 0);
			final int[][] resolved = new int[1][];
			runOnEdt(() -> resolved[0] = client.workbenchResolvedAppearanceColours(skinPlayer));
			skinPaletteCases.append("{\"skinIndex\":").append(skinIndex)
				.append(",\"resolvedRgb\":").append(resolved[0][3]).append("}");
		}
		skinPaletteCases.append("]");

		final ORSCharacter left = paperdollV2PreviewPlayer(PaperdollV2BaseProfile.MALE,
			PAPERDOLL_V2_HAIR_COLOUR_INDEX, 1, 2, skinIndices[1],
			PAPERDOLL_V2_SHIELD_APPEARANCE_ID, PAPERDOLL_V2_WEAPON_APPEARANCE_ID, 0);
		left.hairStyle = 1;
		final ORSCharacter right = paperdollV2PreviewPlayer(PaperdollV2BaseProfile.FEMALE,
			PAPERDOLL_V2_HAIR_COLOUR_INDEX, 1, 2, skinIndices[1],
			PAPERDOLL_V2_SHIELD_APPEARANCE_ID, PAPERDOLL_V2_WEAPON_APPEARANCE_ID, 0);
		right.hairStyle = 2;
		final int[] leftLayersBefore = left.layerAnimation.clone();
		final int[] rightLayersBefore = right.layerAnimation.clone();
		final PaperdollV2Pose pairPose = PaperdollV2Pose.canonical().get(0);
		final int[][] pairPixels = new int[1][];
		runOnEdt(() -> pairPixels[0] = client.workbenchRenderPaperdollV2SelectorPairRaster(
			left, right, pairPose, PAPERDOLL_V2_BACKGROUND));
		if (!Arrays.equals(leftLayersBefore, left.layerAnimation)
			|| !Arrays.equals(rightLayersBefore, right.layerAnimation)) {
			throw new IOException("Paperdoll V2 selector pair mutated synthetic player appearances");
		}
		int pairWidth = PaperdollV2Pack.PREVIEW_WIDTH * 2;
		int pairHeight = PaperdollV2Pack.PREVIEW_HEIGHT;
		int[] nonBackground = paperdollV2PairNonBackground(pairPixels[0], pairWidth, pairHeight,
			PAPERDOLL_V2_BACKGROUND);
		if (nonBackground[0] == 0 || nonBackground[1] == 0) {
			throw new IOException("Paperdoll V2 selector pair scene omitted one actor");
		}
		final ORSCharacter fullRenderAllocationPlayer = paperdollV2PreviewPlayer(
			PaperdollV2BaseProfile.MALE, PAPERDOLL_V2_HAIR_COLOUR_INDEX, 1, 2,
			skinIndices[1], 0, 0, 0);
		fullRenderAllocationPlayer.hairStyle = 1;
		final long[][] fullRenderAllocation = new long[1][];
		runOnEdt(() -> fullRenderAllocation[0] = paperdollV2FullRenderAllocationProbe(
			client, fullRenderAllocationPlayer, pairPose, PAPERDOLL_V2_BACKGROUND,
			2_000, 5_000));
		File pairDirectory = new File(workbenchDir(), "paperdoll-v2-selector");
		Files.createDirectories(pairDirectory.toPath());
		File pairPng = new File(pairDirectory,
			fileTimestamp() + "-male-rare-spikes-female-faded-buzzcut.png").getAbsoluteFile();
		BufferedImage pairImage = new BufferedImage(pairWidth, pairHeight, BufferedImage.TYPE_INT_RGB);
		pairImage.setRGB(0, 0, pairWidth, pairHeight, pairPixels[0], 0, pairWidth);
		if (!ImageIO.write(pairImage, "png", pairPng)) {
			throw new IOException("No PNG writer for Paperdoll V2 selector pair");
		}
		if (runtime.getStackSelectionRevision() != revisionBefore || runtime.getStack() != null
			|| runtime.getBaseProfile() != null) {
			throw new IOException("Per-player selector fixture mutated the global stack selector");
		}

		StringBuilder json = new StringBuilder("{\"ok\":true,");
		appendString(json, "scenario", "paperdoll-v2-selector-resolution").append(",");
		json.append("\"workbenchOnly\":true,\"serverStateMutated\":false,")
			.append("\"packetSent\":false,\"saveStateMutated\":false,")
			.append("\"livePlayerArrayMutated\":false,");
		appendString(json, "selectorRegistryPath", runtime.getSelectorRegistryPath()).append(",");
		appendString(json, "selectorRegistrySha256", registry.getPropertiesSha256()).append(",");
		appendString(json, "packSha256", registry.getPackSha256()).append(",");
		json.append("\"positiveResolutionCaseCount\":").append(resolutionCount).append(",");
		json.append("\"positiveResolutionCases\":").append(resolutionCases).append(",");
		json.append("\"legacyFallbacks\":{");
		appendString(json, "selector0", selectorZeroReason).append(",");
		appendString(json, "unknownSelector255", unknownSelectorReason).append(",");
		appendString(json, "cowboyHat245", cowboyReason).append(",");
		appendString(json, "ordinaryHat151", ordinaryHatReason).append(",");
		appendString(json, "unknownHatNegative1", unknownHatReason).append(",");
		appendString(json, "incompatibleBase", incompatibleBaseReason).append("},");
		json.append("\"skinSourceValidation\":{")
			.append("\"frameCount\":").append(PaperdollV2Pack.FRAME_COUNT).append(",")
			.append("\"allowedVisibleGrayscale\":[118,176,255],")
			.append("\"profiles\":").append(skinSourceCases).append("},")
			.append("\"skinPaletteProbeCases\":").append(skinPaletteCases).append(",");
		json.append("\"allocationContract\":{")
			.append("\"preflight\":{")
			.append("\"selectionObjectsCachedAtRegistryLoad\":true,")
			.append("\"successfulIdentityStable\":true,")
			.append("\"repetitionsPerPositiveCase\":256,")
			.append("\"measurementSupported\":").append(allocationProbe[0] == 1L).append(",")
			.append("\"warmupIterations\":").append(allocationProbe[1]).append(",")
			.append("\"measuredIterations\":").append(allocationProbe[2]).append(",")
			.append("\"allocatedBytes\":").append(allocationProbe[3]).append("},")
			.append("\"fullThreeSlotRender\":{")
			.append("\"measurementSupported\":").append(fullRenderAllocation[0][0] == 1L).append(",")
			.append("\"warmupIterations\":").append(fullRenderAllocation[0][1]).append(",")
			.append("\"measuredIterations\":").append(fullRenderAllocation[0][2]).append(",")
			.append("\"allocatedBytes\":").append(fullRenderAllocation[0][3]).append("}},");
		json.append("\"legacyEquipmentContract\":{")
			.append("\"shieldAppearanceId\":").append(PAPERDOLL_V2_SHIELD_APPEARANCE_ID)
			.append(",\"weaponAppearanceId\":").append(PAPERDOLL_V2_WEAPON_APPEARANCE_ID)
			.append(",\"substitutedByV2\":false},");
		json.append("\"twoPlayerScene\":{");
		appendString(json, "pose", pairPose.getKey()).append(",");
		appendString(json, "leftStackId", registry.getEntry(1)
			.getStackId(PaperdollV2BaseProfile.MALE)).append(",");
		appendString(json, "rightStackId", registry.getEntry(2)
			.getStackId(PaperdollV2BaseProfile.FEMALE)).append(",");
		json.append("\"width\":").append(pairWidth).append(",\"height\":").append(pairHeight)
			.append(",\"leftNonBackgroundPixels\":").append(nonBackground[0])
			.append(",\"rightNonBackgroundPixels\":").append(nonBackground[1]).append(",");
		appendString(json, "pngPath", pairPng.getPath()).append(",");
		appendString(json, "pngSha256", sha256(Files.readAllBytes(pairPng.toPath()))).append("},");
		json.append("\"stackSelectionRevisionBefore\":").append(revisionBefore).append(",")
			.append("\"stackSelectionRevisionAfter\":")
			.append(runtime.getStackSelectionRevision()).append(",");
		appendString(json, "generatedAt", isoTimestamp());
		return json.append("}").toString();
	}

	private static String expectPaperdollV2SelectorFallback(PaperdollV2Runtime runtime,
		int[] layers, int selectorId, String expectedReasonPrefix) throws IOException {
		PaperdollV2Runtime.PreflightObservation observation = runtime.inspectPreflight(layers,
			selectorId, 0, PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
		if (observation.getSelection() != null) {
			throw new IOException("Selector " + selectorId + " unexpectedly entered V2");
		}
		String reason = observation.getFallbackReason();
		if (!reason.startsWith(expectedReasonPrefix)) {
			throw new IOException("Selector " + selectorId + " fallback reason differs: " + reason);
		}
		return reason;
	}

	private static long[] paperdollV2ValidateSkinSource(PaperdollV2Pack.RenderStack stack,
		int slot, String kind) throws IOException {
		PaperdollV2Pack.Channel skin = null;
		int assetCount = 0;
		for (int assetIndex = 0; assetIndex < stack.getAssetCount(); assetIndex++) {
			PaperdollV2Pack.Asset asset = stack.getAsset(assetIndex);
			if (asset.getPaperdollSlot() != slot || !kind.equals(asset.getKind())
				|| !"native".equals(asset.getSourceMode())) continue;
			assetCount++;
			for (int channelIndex = 0; channelIndex < asset.getChannelCount(); channelIndex++) {
				PaperdollV2Pack.Channel channel = asset.getChannel(channelIndex);
				if (!"skin".equals(channel.getTintRole())) continue;
				if (skin != null) throw new IOException("Paperdoll V2 " + kind
					+ " exposes duplicate skin source channels");
				skin = channel;
			}
		}
		if (assetCount != 1 || skin == null) {
			throw new IOException("Paperdoll V2 " + kind + " slot " + slot
				+ " must expose one native asset and one skin source channel");
		}
		long[] counts = new long[4];
		for (int frame = 0; frame < PaperdollV2Pack.FRAME_COUNT; frame++) {
			if (skin.isEmpty(frame)) throw new IOException("Paperdoll V2 " + kind
				+ " skin source is empty for frame " + frame);
			long frameVisible = 0;
			for (int argb : skin.getFrame(frame).getPixels()) {
				if ((argb >>> 24) == 0) continue;
				int red = argb >>> 16 & 0xff;
				int green = argb >>> 8 & 0xff;
				int blue = argb & 0xff;
				if (red != green || red != blue || (red != 0x76 && red != 0xb0 && red != 0xff)) {
					throw new IOException("Paperdoll V2 " + kind
						+ " visible skin source is outside neutral ramp at frame " + frame);
				}
				counts[0]++;
				frameVisible++;
				if (red == 0x76) counts[1]++;
				else if (red == 0xb0) counts[2]++;
				else counts[3]++;
			}
			if (frameVisible == 0) throw new IOException("Paperdoll V2 " + kind
				+ " skin source has no visible pixels at frame " + frame);
		}
		return counts;
	}

	private static StringBuilder appendPaperdollV2SkinSourceCounts(StringBuilder json,
		String key, long[] counts) {
		json.append("\"").append(jsonEscape(key)).append("\":{")
			.append("\"visiblePixelCount\":").append(counts[0]).append(",")
			.append("\"ramp76Count\":").append(counts[1]).append(",")
			.append("\"rampB0Count\":").append(counts[2]).append(",")
			.append("\"rampFFCount\":").append(counts[3]).append(",")
			.append("\"all18FramesVisibleAndNormalized\":true}");
		return json;
	}

	private static long[] paperdollV2SelectorAllocationProbe(PaperdollV2Runtime runtime,
		int[] layers, int selectorId) throws IOException {
		final int warmupIterations = 20_000;
		final int measuredIterations = 100_000;
		PaperdollV2Runtime.RenderSelection expected = runtime.preflight(layers, selectorId, 0,
			PaperdollV2Pack.WALK_WIDTH, PaperdollV2Pack.PLAYER_HEIGHT);
		if (expected == null) throw new IOException("Allocation probe selector did not resolve");
		for (int index = 0; index < warmupIterations; index++) {
			if (runtime.preflight(layers, selectorId, 0, PaperdollV2Pack.WALK_WIDTH,
				PaperdollV2Pack.PLAYER_HEIGHT) != expected) {
				throw new IOException("Allocation probe selector identity changed during warm-up");
			}
		}

		java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
		if (!(platformBean instanceof com.sun.management.ThreadMXBean)) {
			return new long[] {0L, warmupIterations, 0L, -1L};
		}
		com.sun.management.ThreadMXBean allocationBean =
			(com.sun.management.ThreadMXBean) platformBean;
		if (!allocationBean.isThreadAllocatedMemorySupported()) {
			return new long[] {0L, warmupIterations, 0L, -1L};
		}
		if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
			allocationBean.setThreadAllocatedMemoryEnabled(true);
		}
		long threadId = Thread.currentThread().getId();
		allocationBean.getThreadAllocatedBytes(threadId);
		long before = allocationBean.getThreadAllocatedBytes(threadId);
		for (int index = 0; index < measuredIterations; index++) {
			if (runtime.preflight(layers, selectorId, 0, PaperdollV2Pack.WALK_WIDTH,
				PaperdollV2Pack.PLAYER_HEIGHT) != expected) {
				throw new IOException("Allocation probe selector identity changed while measured");
			}
		}
		long allocatedBytes = allocationBean.getThreadAllocatedBytes(threadId) - before;
		if (allocatedBytes != 0L) {
			throw new IOException("Successful Paperdoll V2 selector preflight allocated "
				+ allocatedBytes + " bytes across " + measuredIterations + " calls");
		}
		return new long[] {1L, warmupIterations, measuredIterations, allocatedBytes};
	}

	private static long[] paperdollV2FullRenderAllocationProbe(mudclient client,
		ORSCharacter player, PaperdollV2Pose pose, int backgroundRgb,
		int warmupIterations, int measuredIterations) throws IOException {
		client.workbenchRunPaperdollV2ThreeSlotRenderLoop(player, pose, backgroundRgb,
			warmupIterations);

		java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
		if (!(platformBean instanceof com.sun.management.ThreadMXBean)) {
			return new long[] {0L, warmupIterations, 0L, -1L};
		}
		com.sun.management.ThreadMXBean allocationBean =
			(com.sun.management.ThreadMXBean) platformBean;
		if (!allocationBean.isThreadAllocatedMemorySupported()) {
			return new long[] {0L, warmupIterations, 0L, -1L};
		}
		if (!allocationBean.isThreadAllocatedMemoryEnabled()) {
			allocationBean.setThreadAllocatedMemoryEnabled(true);
		}
		long threadId = Thread.currentThread().getId();
		allocationBean.getThreadAllocatedBytes(threadId);
		long before = allocationBean.getThreadAllocatedBytes(threadId);
		client.workbenchRunPaperdollV2ThreeSlotRenderLoop(player, pose, backgroundRgb,
			measuredIterations);
		long allocatedBytes = allocationBean.getThreadAllocatedBytes(threadId) - before;
		return new long[] {1L, warmupIterations, measuredIterations, allocatedBytes};
	}

	private static int[] paperdollV2PairNonBackground(int[] pixels, int width, int height,
		int backgroundRgb) throws IOException {
		if (pixels == null || pixels.length != width * height) {
			throw new IOException("Paperdoll V2 selector pair raster has the wrong dimensions");
		}
		int[] counts = new int[2];
		int background = backgroundRgb & 0x00ffffff;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if ((pixels[y * width + x] & 0x00ffffff) != background) {
					counts[x < width / 2 ? 0 : 1]++;
				}
			}
		}
		return counts;
	}

	private static ORSCharacter paperdollV2PreviewPlayer(PaperdollV2BaseProfile baseProfile,
		int hairColour, int topColour, int bottomColour,
		int skinColour, int shieldAppearanceId, int weaponAppearanceId, int hatAppearanceId) {
		ORSCharacter preview = new ORSCharacter();
		preview.layerAnimation = new int[12];
		baseProfile.applyTo(preview.layerAnimation);
		preview.layerAnimation[3] = shieldAppearanceId;
		preview.layerAnimation[4] = weaponAppearanceId;
		preview.layerAnimation[PLAYER_HAT_LAYER] = hatAppearanceId;
		preview.colourHair = hairColour;
		preview.colourTop = topColour;
		preview.colourBottom = bottomColour;
		preview.colourSkin = skinColour;
		preview.hairStyle = 0;
		preview.isInvisible = false;
		preview.isInvulnerable = false;
		return preview;
	}

	private static PaperdollV2Capture writePaperdollV2Capture(File png, int[] pixels,
		int backgroundRgb, PaperdollV2Pack.RenderStack stack,
		PaperdollV2BaseProfile baseProfile, int[] layerAnimation, PaperdollV2Pose pose,
		int[] layerOrder, int[] tintRgb, PaperdollV2RasterArtifact v2Only) throws IOException {
		PaperdollV2RasterArtifact full = writePaperdollV2Raster(png, pixels, backgroundRgb,
			stack.getId() + "/" + pose.getKey(), true);
		PaperdollV2RasterArtifact oracle = v2Only == null ? full : v2Only;
		return new PaperdollV2Capture(stack.getId(), stack.getMode(), baseProfile.getId(), pose, full,
			oracle, paperdollV2OracleSlots(stack, layerOrder), layerAnimation.clone(),
			layerOrder.clone(), tintRgb.clone());
	}

	private static PaperdollV2RasterArtifact writePaperdollV2Raster(File png, int[] pixels,
		int backgroundRgb, String label, boolean requireNonEmpty) throws IOException {
		int expectedPixels = PaperdollV2Pack.PREVIEW_WIDTH * PaperdollV2Pack.PREVIEW_HEIGHT;
		if (pixels == null || pixels.length != expectedPixels) {
			throw new IOException("Paperdoll V2 raster has the wrong pixel count for " + label);
		}
		BufferedImage image = new BufferedImage(PaperdollV2Pack.PREVIEW_WIDTH,
			PaperdollV2Pack.PREVIEW_HEIGHT, BufferedImage.TYPE_INT_RGB);
		image.setRGB(0, 0, PaperdollV2Pack.PREVIEW_WIDTH, PaperdollV2Pack.PREVIEW_HEIGHT,
			pixels, 0, PaperdollV2Pack.PREVIEW_WIDTH);
		if (!ImageIO.write(image, "png", png)) {
			throw new IOException("No PNG writer is available for Paperdoll V2 capture");
		}

		int minX = PaperdollV2Pack.PREVIEW_WIDTH;
		int minY = PaperdollV2Pack.PREVIEW_HEIGHT;
		int maxX = -1;
		int maxY = -1;
		int background = backgroundRgb & 0x00ffffff;
		for (int y = 0; y < PaperdollV2Pack.PREVIEW_HEIGHT; y++) {
			for (int x = 0; x < PaperdollV2Pack.PREVIEW_WIDTH; x++) {
				if ((pixels[y * PaperdollV2Pack.PREVIEW_WIDTH + x] & 0x00ffffff) == background) continue;
				minX = Math.min(minX, x);
				minY = Math.min(minY, y);
				maxX = Math.max(maxX, x);
				maxY = Math.max(maxY, y);
			}
		}
		boolean empty = maxX < minX || maxY < minY;
		if (empty && requireNonEmpty) {
			throw new IOException("Paperdoll V2 raster is empty for " + label);
		}
		return new PaperdollV2RasterArtifact(png.getAbsoluteFile(),
			sha256(Files.readAllBytes(png.toPath())), rawRgbSha256(pixels), empty,
			empty ? -1 : minX, empty ? -1 : minY,
			empty ? 0 : maxX - minX + 1, empty ? 0 : maxY - minY + 1);
	}

	private static int[] paperdollV2OracleSlots(PaperdollV2Pack.RenderStack stack, int[] layerOrder) {
		boolean[] assetSlots = new boolean[12];
		for (PaperdollV2Pack.Asset asset : stack.getAssets()) {
			int slot = asset.getPaperdollSlot();
			if (!stack.usesLiveControls() || "native".equals(asset.getSourceMode())) {
				assetSlots[slot] = true;
			}
		}
		int count = 0;
		for (int slot : layerOrder) if (slot >= 0 && slot < assetSlots.length && assetSlots[slot]) count++;
		int[] result = new int[count];
		int index = 0;
		for (int slot : layerOrder) {
			if (slot >= 0 && slot < assetSlots.length && assetSlots[slot]) result[index++] = slot;
		}
		return result;
	}

	private static StringBuilder appendPaperdollV2AvailableLiveStacks(StringBuilder json,
		PaperdollV2Runtime runtime) {
		json.append("\"availableLiveStacks\":[");
		PaperdollV2Pack pack = runtime.getPack();
		List<String> available = runtime.getAvailableLiveStackIds();
		for (int index = 0; index < available.size(); index++) {
			if (index > 0) json.append(",");
			String stackId = available.get(index);
			try {
				PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
				PaperdollV2BaseProfile profile = PaperdollV2BaseProfile.derive(stack, null);
				json.append("{");
				appendString(json, "id", stackId).append(",");
				appendString(json, "baseProfile", profile.getId()).append(",");
				appendIntArray(json, "nativePaperdollSlots", stack.getNativePaperdollSlots());
				json.append("}");
			} catch (IOException impossible) {
				throw new IllegalStateException("Validated Paperdoll V2 live stack disappeared", impossible);
			}
		}
		return json.append("]");
	}

	private static StringBuilder appendPaperdollV2SelectorRegistry(StringBuilder json,
		PaperdollV2SelectorRegistry registry) {
		json.append("\"selectorRegistry\":");
		if (registry == null) return json.append("null");
		json.append("{");
		appendString(json, "schema", PaperdollV2SelectorRegistry.SCHEMA).append(",");
		appendString(json, "propertiesPath", registry.getPropertiesFile().getPath()).append(",");
		appendString(json, "propertiesSha256", registry.getPropertiesSha256()).append(",");
		appendString(json, "catalogPath", registry.getCatalogPath()).append(",");
		appendString(json, "catalogSha256", registry.getCatalogSha256()).append(",");
		appendString(json, "packSha256", registry.getPackSha256()).append(",");
		appendString(json, "namespaceField", registry.getNamespaceField()).append(",");
		json.append("\"classicSelectorId\":").append(registry.getClassicSelectorId()).append(",");
		json.append("\"minimumSelectorId\":").append(registry.getMinimumSelectorId()).append(",");
		json.append("\"maximumSelectorId\":").append(registry.getMaximumSelectorId()).append(",");
		json.append("\"defaultEnabled\":").append(registry.isDefaultEnabled()).append(",");
		json.append("\"entries\":[");
		List<PaperdollV2SelectorRegistry.Entry> entries = registry.getEntries();
		for (int index = 0; index < entries.size(); index++) {
			if (index > 0) json.append(",");
			PaperdollV2SelectorRegistry.Entry entry = entries.get(index);
			json.append("{");
			json.append("\"selectorId\":").append(entry.getSelectorId()).append(",");
			appendString(json, "style", entry.getStyle()).append(",");
			appendString(json, "route", entry.getRoute()).append(",");
			appendString(json, "eligibilityState", entry.getEligibilityState()).append(",");
			appendStringList(json, "eligibilityPlatforms",
				entry.getEligibilityPlatforms()).append(",");
			json.append("\"male\":{");
			appendString(json, "qaControlStackId",
				entry.getQaControlStackId(PaperdollV2BaseProfile.MALE)).append(",");
			appendString(json, "stackId", entry.getStackId(PaperdollV2BaseProfile.MALE));
			json.append("},\"female\":{");
			appendString(json, "qaControlStackId",
				entry.getQaControlStackId(PaperdollV2BaseProfile.FEMALE)).append(",");
			appendString(json, "stackId", entry.getStackId(PaperdollV2BaseProfile.FEMALE));
			json.append("}}");
		}
		return json.append("]}");
	}

	private static StringBuilder appendPaperdollV2Pack(StringBuilder json, PaperdollV2Pack pack) {
		json.append("\"pack\":{");
		appendString(json, "schema", PaperdollV2Pack.SCHEMA).append(",");
		appendString(json, "template", PaperdollV2Pack.TEMPLATE).append(",");
		appendString(json, "templateSha256", pack.getTemplateSha256()).append(",");
		appendString(json, "sourceV1Sha256", pack.getSourceV1Sha256()).append(",");
		appendString(json, "derivedMasksSha256", pack.getDerivedMasksSha256()).append(",");
		json.append("\"frameCount\":").append(PaperdollV2Pack.FRAME_COUNT).append(",");
		json.append("\"stacks\":[");
		for (int stackIndex = 0; stackIndex < pack.getRenderStackIds().size(); stackIndex++) {
			if (stackIndex > 0) json.append(",");
			String stackId = pack.getRenderStackIds().get(stackIndex);
			try {
				PaperdollV2Pack.RenderStack stack = pack.requireRenderStack(stackId);
				json.append("{");
				appendString(json, "id", stack.getId()).append(",");
				appendString(json, "javaMode", stack.getMode()).append(",");
				appendString(json, "javaBehavior", stack.usesLiveControls()
					? "directional-live-controls-with-all-declared-native-slot-substitutions"
					: "directional-pack-only-upscaled-legacy-control").append(",");
				appendIntArray(json, "nativePaperdollSlots", stack.getNativePaperdollSlots()).append(",");
				json.append("\"assets\":[");
				for (int assetIndex = 0; assetIndex < stack.getAssets().size(); assetIndex++) {
					if (assetIndex > 0) json.append(",");
					PaperdollV2Pack.Asset asset = stack.getAssets().get(assetIndex);
					json.append("{");
					appendString(json, "id", asset.getId()).append(",");
					appendString(json, "kind", asset.getKind()).append(",");
					appendString(json, "sourceMode", asset.getSourceMode()).append(",");
					appendString(json, "propagation", asset.getPropagation()).append(",");
					json.append("\"paperdollSlot\":").append(asset.getPaperdollSlot()).append(",");
					json.append("\"channels\":[");
					for (int channelIndex = 0; channelIndex < asset.getChannels().size(); channelIndex++) {
						if (channelIndex > 0) json.append(",");
						PaperdollV2Pack.Channel channel = asset.getChannels().get(channelIndex);
						json.append("{");
						appendString(json, "id", channel.getId()).append(",");
						appendString(json, "tintRole", channel.getTintRole());
						json.append("}");
					}
					json.append("]}");
				}
				json.append("]}");
			} catch (IOException impossible) {
				throw new IllegalStateException("Validated Paperdoll V2 stack disappeared", impossible);
			}
		}
		json.append("]}");
		return json;
	}

	private static StringBuilder appendPaperdollV2Captures(StringBuilder json,
		ArrayList<PaperdollV2Capture> captures) {
		json.append("\"captures\":[");
		for (int i = 0; i < captures.size(); i++) {
			if (i > 0) json.append(",");
			PaperdollV2Capture capture = captures.get(i);
			PaperdollV2Pose pose = capture.pose;
			PaperdollV2RasterArtifact full = capture.fullPanel;
			json.append("{");
			appendString(json, "stack", capture.stackId).append(",");
			appendString(json, "stackId", capture.stackId).append(",");
			appendString(json, "qaCaseId", capture.stackId).append(",");
			appendString(json, "stackMode", capture.stackMode).append(",");
			appendString(json, "baseProfile", capture.baseProfile).append(",");
			json.append("\"ordinal\":").append(pose.getOrdinal()).append(",");
			appendString(json, "stateKey", pose.getKey()).append(",");
			appendString(json, "kind", pose.getKind()).append(",");
			appendString(json, "direction", pose.getDirection()).append(",");
			json.append("\"frame\":").append(pose.getAnimationFrame()).append(",");
			json.append("\"animationFrame\":").append(pose.getAnimationFrame()).append(",");
			json.append("\"spriteOffset\":").append(pose.getSpriteOffset()).append(",");
			json.append("\"wantedAnimDir\":").append(pose.getWantedAnimDir()).append(",");
			json.append("\"actualAnimDir\":").append(pose.getActualAnimDir()).append(",");
			json.append("\"mirrorX\":").append(pose.isMirrorX()).append(",");
			appendIntArray(json, "layerOrder", capture.layerOrder).append(",");
			json.append("\"renderInputs\":{");
			json.append("\"wantedAnimDir\":").append(pose.getWantedAnimDir()).append(",");
			json.append("\"actualAnimDir\":").append(pose.getActualAnimDir()).append(",");
			json.append("\"mirrorX\":").append(pose.isMirrorX()).append(",");
			json.append("\"spriteOffset\":").append(pose.getSpriteOffset()).append(",");
			json.append("\"stepFrame\":").append(pose.getLegacyStepFrame()).append(",");
			json.append("\"canvasWidth\":").append(PaperdollV2Pack.PREVIEW_WIDTH).append(",");
			json.append("\"canvasHeight\":").append(PaperdollV2Pack.PREVIEW_HEIGHT).append(",");
			json.append("\"drawX\":").append(pose.getV2DrawX()).append(",");
			json.append("\"drawY\":").append(PaperdollV2Pack.PREVIEW_DRAW_Y).append(",");
			json.append("\"drawWidth\":").append(pose.getV2DrawWidth()).append(",");
			json.append("\"drawHeight\":").append(PaperdollV2Pack.PLAYER_HEIGHT).append(",");
			json.append("\"backgroundRgb\":").append(PAPERDOLL_V2_BACKGROUND).append(",");
			appendIntArray(json, "layerAnimation", capture.layerAnimation).append(",");
			json.append("\"tintRgb\":{");
			json.append("\"skin\":").append(capture.tintRgb[0]).append(",");
			json.append("\"hair\":").append(capture.tintRgb[1]).append(",");
			json.append("\"facial-hair\":").append(capture.tintRgb[2]).append(",");
			json.append("\"top\":").append(capture.tintRgb[3]).append(",");
			json.append("\"bottom\":").append(capture.tintRgb[4]).append(",");
			json.append("\"primary\":").append(capture.tintRgb[5]).append(",");
			json.append("\"secondary\":").append(capture.tintRgb[6]).append(",");
			json.append("\"fixed\":").append(capture.tintRgb[7]).append("}},");
			appendString(json, "pngPath", full.pngFile.getPath()).append(",");
			appendString(json, "pngSha256", full.pngSha256).append(",");
			appendString(json, "rawRgbSha256", full.rawRgbSha256).append(",");
			json.append("\"width\":").append(PaperdollV2Pack.PREVIEW_WIDTH).append(",");
			json.append("\"height\":").append(PaperdollV2Pack.PREVIEW_HEIGHT).append(",");
			appendPaperdollV2Crop(json, full).append(",");
			appendPaperdollV2Raster(json, "fullPanel", full, null, "human-review-full-panel").append(",");
			appendPaperdollV2Raster(json, "v2Only", capture.v2Only, capture.paperdollSlots,
				"live-controls".equals(capture.stackMode) ? "declared-native-slots" : "pack-only");
			json.append("}");
		}
		json.append("]");
		return json;
	}

	private static StringBuilder appendPaperdollV2Raster(StringBuilder json, String key,
		PaperdollV2RasterArtifact raster, int[] paperdollSlots, String mode) {
		json.append("\"").append(key).append("\":{");
		appendString(json, "mode", mode).append(",");
		appendString(json, "pngPath", raster.pngFile.getPath()).append(",");
		appendString(json, "pngSha256", raster.pngSha256).append(",");
		appendString(json, "rawRgbSha256", raster.rawRgbSha256).append(",");
		json.append("\"width\":").append(PaperdollV2Pack.PREVIEW_WIDTH).append(",");
		json.append("\"height\":").append(PaperdollV2Pack.PREVIEW_HEIGHT).append(",");
		json.append("\"empty\":").append(raster.empty).append(",");
		if (paperdollSlots == null) {
			json.append("\"paperdollSlots\":null,");
		} else {
			appendIntArray(json, "paperdollSlots", paperdollSlots).append(",");
		}
		appendPaperdollV2Crop(json, raster);
		json.append("}");
		return json;
	}

	private static StringBuilder appendPaperdollV2Crop(StringBuilder json,
		PaperdollV2RasterArtifact raster) {
		json.append("\"crop\":");
		if (raster.empty) {
			json.append("null");
			return json;
		}
		json.append("{");
		json.append("\"x\":").append(raster.cropX).append(",");
		json.append("\"y\":").append(raster.cropY).append(",");
		json.append("\"width\":").append(raster.cropWidth).append(",");
		json.append("\"height\":").append(raster.cropHeight).append("}");
		return json;
	}

	private static void requireAppearanceFramesLoaded(mudclient client,
		GeneratedAppearanceRegistry.Entry managed) throws IOException {
		AnimationDef definition = EntityHandler.getPlayerAppearanceDef(managed.appearanceId);
		if (definition == null || !managed.definition.getName().equals(definition.getName())
			|| definition.getNumber() != managed.definition.getNumber()) {
			throw new IOException("Managed appearance " + managed.key + " did not resolve to its pinned definition");
		}
		for (int offset = 0; offset < managed.frameCount; offset++) {
			Sprite sprite = client.spriteSelect(definition, offset);
			if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0
				|| sprite.getSomething1() <= 0 || sprite.getSomething2() <= 0) {
				throw new IOException("Managed appearance sprite offset " + offset + " is not loaded");
			}
		}
	}

	private static int applyCowboyPreviewFrame(final mudclient client, final ORSCharacter player,
		final int renderPlayerIndex, final ORSCharacterDirection direction, final int stepFrame,
		final int paperdollSlot, final int appearanceId) throws IOException {
		final int[] appliedAt = new int[1];
		runOnEdt(() -> {
			client.setPlayer(renderPlayerIndex, player);
			boolean combat = direction == ORSCharacterDirection.COMBAT_A
				|| direction == ORSCharacterDirection.COMBAT_B;
			client.cameraRotation = combat ? 0 : direction.rsDir * 32;
			player.layerAnimation[paperdollSlot] = appearanceId;
			player.hairStyle = 0;
			player.isInvisible = false;
			player.isInvulnerable = false;
			player.animationNext = combat ? direction.rsDir : ORSCharacterDirection.NORTH.rsDir;
			player.direction = combat ? direction : ORSCharacterDirection.NORTH;
			player.stepFrame = stepFrame;
			appliedAt[0] = client.getFrameCounter();
		});
		return appliedAt[0];
	}

	private static void requireCowboyPreviewState(mudclient client, ORSCharacter player,
		int renderPlayerIndex, ORSCharacterDirection direction, int stepFrame,
		int paperdollSlot, int appearanceId) throws IOException {
		boolean combat = direction == ORSCharacterDirection.COMBAT_A
			|| direction == ORSCharacterDirection.COMBAT_B;
		int expectedCamera = combat ? 0 : direction.rsDir * 32;
		ORSCharacterDirection expectedDirection = combat ? direction : ORSCharacterDirection.NORTH;
		int expectedAnimationNext = combat ? direction.rsDir : ORSCharacterDirection.NORTH.rsDir;
		if (client.getPlayer(renderPlayerIndex) != player || client.cameraRotation != expectedCamera
			|| player.layerAnimation[paperdollSlot] != appearanceId
			|| player.hairStyle != 0 || player.isInvisible || player.isInvulnerable
			|| player.direction != expectedDirection || player.animationNext != expectedAnimationNext
			|| player.stepFrame != stepFrame) {
			throw new IOException("Appearance preview state changed before capture: renderPlayerPinned="
				+ (client.getPlayer(renderPlayerIndex) == player) + " camera=" + client.cameraRotation
				+ " appearance=" + player.layerAnimation[paperdollSlot]
				+ " direction=" + player.direction + " animationNext=" + player.animationNext
				+ " stepFrame=" + player.stepFrame + " expectedCamera=" + expectedCamera
				+ " expectedDirection=" + expectedDirection
				+ " expectedAnimationNext=" + expectedAnimationNext
				+ " expectedStepFrame=" + stepFrame);
		}
	}

	private static int waitForRendererAdvance(mudclient client, int appliedAt, int frameCount,
		long timeoutMs) throws IOException {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			int current = client.getFrameCounter();
			if (current - appliedAt >= frameCount) return current;
			sleep(5);
		}
		throw new IOException("Client renderer did not advance for appearance capture");
	}

	private static int waitForCombatPreviewPhase(final mudclient client, final ORSCharacter player,
		final int renderPlayerIndex, int appliedAt, final ORSCharacterDirection direction,
		int frame, final int paperdollSlot, final int appearanceId) throws IOException {
		int phase = direction == ORSCharacterDirection.COMBAT_A
			? new int[] {0, 3, 2}[frame] : new int[] {4, 5, 6}[frame];
		int divisor = direction == ORSCharacterDirection.COMBAT_A ? 5 : 6;
		long deadline = System.currentTimeMillis() + 2500;
		while (System.currentTimeMillis() < deadline) {
			runOnEdt(() -> {
				client.setPlayer(renderPlayerIndex, player);
				client.cameraRotation = 0;
				player.layerAnimation[paperdollSlot] = appearanceId;
				player.animationNext = direction.rsDir;
				player.direction = direction;
				player.stepFrame = 0;
			});
			int current = client.getFrameCounter();
			if (current - appliedAt >= 2 && Math.floorMod(current / divisor, 8) == phase) {
				// copyGameImageForWorkbench reads the live back buffer. Require a brief
				// frame-counter-stable window so the capture cannot land mid-raster.
				sleep(12);
				if (client.getFrameCounter() == current) {
					runOnEdt(() -> {
						client.setPlayer(renderPlayerIndex, player);
						player.layerAnimation[paperdollSlot] = appearanceId;
						player.animationNext = direction.rsDir;
						player.direction = direction;
						player.stepFrame = 0;
					});
					return current;
				}
			}
			sleep(5);
		}
		throw new IOException("Client renderer did not reach appearance combat frame " + frame
			+ " for " + direction);
	}

	private static AppearanceRasterCapture captureAppearanceRaster(mudclient client, ORSCharacter player,
		int wantedAnimDir, int actualAnimDir, boolean mirrorX, int spriteOffset, int stepFrame,
		String reason) throws IOException {
		final int[][] rasters = new int[2][];
		final int[][] layerOrder = new int[1][];
		final int[][] resolvedColours = new int[1][];
		runOnEdt(() -> {
			rasters[0] = client.workbenchRenderPlayerCompositeRaster(player, wantedAnimDir, actualAnimDir,
				mirrorX, spriteOffset, stepFrame, APPEARANCE_RASTER_WIDTH, APPEARANCE_RASTER_HEIGHT,
				APPEARANCE_DRAW_X, APPEARANCE_DRAW_Y, APPEARANCE_DRAW_WIDTH, APPEARANCE_DRAW_HEIGHT,
				APPEARANCE_RASTER_BACKGROUND);
			rasters[1] = client.workbenchRenderPlayerCompositeRaster(player, wantedAnimDir, actualAnimDir,
				mirrorX, spriteOffset, stepFrame, APPEARANCE_RASTER_WIDTH, APPEARANCE_RASTER_HEIGHT,
				APPEARANCE_DRAW_X, APPEARANCE_DRAW_Y, APPEARANCE_DRAW_WIDTH, APPEARANCE_DRAW_HEIGHT,
				APPEARANCE_RASTER_PROBE_BACKGROUND);
			layerOrder[0] = client.workbenchAppearanceLayerOrder(wantedAnimDir);
			resolvedColours[0] = client.workbenchResolvedAppearanceColours(player);
		});

		int minX = APPEARANCE_RASTER_WIDTH;
		int minY = APPEARANCE_RASTER_HEIGHT;
		int maxX = -1;
		int maxY = -1;
		BufferedImage image = new BufferedImage(APPEARANCE_RASTER_WIDTH, APPEARANCE_RASTER_HEIGHT,
			BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < APPEARANCE_RASTER_HEIGHT; y++) {
			for (int x = 0; x < APPEARANCE_RASTER_WIDTH; x++) {
				int index = y * APPEARANCE_RASTER_WIDTH + x;
				image.setRGB(x, y, rasters[0][index] & 0x00ffffff);
				if (rasters[0][index] != APPEARANCE_RASTER_BACKGROUND
					|| rasters[1][index] != APPEARANCE_RASTER_PROBE_BACKGROUND) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < minX || maxY < minY) {
			throw new IOException("Isolated appearance raster is empty for " + reason);
		}

		File rasterDir = new File(workbenchDir(), "appearance-rasters");
		Files.createDirectories(rasterDir.toPath());
		File png = new File(rasterDir, fileTimestamp() + "-" + sanitizeReason(reason) + ".png").getAbsoluteFile();
		ImageIO.write(image, "png", png);
		return new AppearanceRasterCapture(png, sha256(Files.readAllBytes(png.toPath())), rawRgbSha256(rasters[0]), minX, minY,
			maxX - minX + 1, maxY - minY + 1, Arrays.copyOf(player.layerAnimation, player.layerAnimation.length),
			layerOrder[0], new int[]{player.colourHair, player.colourTop, player.colourBottom, player.colourSkin},
			resolvedColours[0]);
	}

	private static String rawRgbSha256(int[] pixels) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream(pixels.length * 3);
		for (int pixel : pixels) {
			bytes.write((byte) (pixel >> 16));
			bytes.write((byte) (pixel >> 8));
			bytes.write((byte) pixel);
		}
		return sha256(bytes.toByteArray());
	}

	private static String sha256(byte[] bytes) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(bytes);
			StringBuilder hex = new StringBuilder(64);
			for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IOException("SHA-256 is unavailable", e);
		}
	}

	private static void appendCowboyFrameCaptures(StringBuilder json, ArrayList<CowboyFrameCapture> captures) {
		json.append("\"captures\":[");
		for (int i = 0; i < captures.size(); i++) {
			if (i > 0) json.append(",");
			CowboyFrameCapture frame = captures.get(i);
			CaptureResult capture = frame.capture;
			json.append("{");
			json.append("\"spriteOffset\":").append(frame.spriteOffset).append(",");
			appendString(json, "kind", frame.kind).append(",");
			appendString(json, "direction", frame.direction).append(",");
			json.append("\"frame\":").append(frame.frame).append(",");
			json.append("\"wantedAnimDir\":").append(frame.wantedAnimDir).append(",");
			json.append("\"actualAnimDir\":").append(frame.actualAnimDir).append(",");
			json.append("\"mirrorX\":").append(frame.mirrorX).append(",");
			json.append("\"rendererFrameCounter\":").append(frame.rendererFrameCounter).append(",");
			appendString(json, "reason", capture.reason).append(",");
			appendString(json, "pngPath", capture.pngFile.getPath()).append(",");
			appendString(json, "statePath", capture.jsonFile.getPath()).append(",");
			json.append("\"width\":").append(capture.width).append(",");
			json.append("\"height\":").append(capture.height).append(",");
			appendAppearanceRaster(json, frame.raster).append(",");
			appendAppearanceRenderInputs(json, frame);
			json.append("}");
		}
		json.append("]");
	}

	private static StringBuilder appendAppearanceRaster(StringBuilder json, AppearanceRasterCapture raster) {
		json.append("\"isolatedRaster\":{");
		appendString(json, "pngPath", raster.pngFile.getPath()).append(",");
		appendString(json, "pngSha256", raster.pngSha256).append(",");
		appendString(json, "rawRgbSha256", raster.rawRgbSha256).append(",");
		json.append("\"width\":").append(APPEARANCE_RASTER_WIDTH).append(",");
		json.append("\"height\":").append(APPEARANCE_RASTER_HEIGHT).append(",");
		json.append("\"backgroundRgb\":").append(APPEARANCE_RASTER_BACKGROUND).append(",");
		json.append("\"crop\":{");
		json.append("\"x\":").append(raster.cropX).append(",");
		json.append("\"y\":").append(raster.cropY).append(",");
		json.append("\"width\":").append(raster.cropWidth).append(",");
		json.append("\"height\":").append(raster.cropHeight).append("}}");
		return json;
	}

	private static StringBuilder appendAppearanceRenderInputs(StringBuilder json, CowboyFrameCapture frame) {
		AppearanceRasterCapture raster = frame.raster;
		json.append("\"renderInputs\":{");
		json.append("\"wantedAnimDir\":").append(frame.wantedAnimDir).append(",");
		json.append("\"actualAnimDir\":").append(frame.actualAnimDir).append(",");
		json.append("\"mirrorX\":").append(frame.mirrorX).append(",");
		json.append("\"spriteOffset\":").append(frame.spriteOffset).append(",");
		json.append("\"stepFrame\":").append("walk".equals(frame.kind) ? frame.frame * PLAYER_WALK_FRAME_TICKS : 0).append(",");
		json.append("\"x\":").append(APPEARANCE_DRAW_X).append(",");
		json.append("\"y\":").append(APPEARANCE_DRAW_Y).append(",");
		json.append("\"width\":").append(APPEARANCE_DRAW_WIDTH).append(",");
		json.append("\"height\":").append(APPEARANCE_DRAW_HEIGHT).append(",");
		json.append("\"topPixelSkew\":0,\"overlayMovement\":0,");
		json.append("\"hairStyle\":0,\"invisible\":false,\"invulnerable\":false,");
		json.append("\"paletteIndices\":{");
		json.append("\"hair\":").append(raster.paletteIndices[0]).append(",");
		json.append("\"top\":").append(raster.paletteIndices[1]).append(",");
		json.append("\"bottom\":").append(raster.paletteIndices[2]).append(",");
		json.append("\"skin\":").append(raster.paletteIndices[3]).append("},");
		json.append("\"resolvedRgb\":{");
		json.append("\"hair\":").append(raster.resolvedColours[0]).append(",");
		json.append("\"top\":").append(raster.resolvedColours[1]).append(",");
		json.append("\"bottom\":").append(raster.resolvedColours[2]).append(",");
		json.append("\"skin\":").append(raster.resolvedColours[3]).append("},");
		appendIntArray(json, "layerAnimation", raster.layerAnimation).append(",");
		appendIntArray(json, "layerOrder", raster.layerOrder).append(",");
		json.append("\"layers\":[");
		for (int slot = 0; slot < raster.layerAnimation.length; slot++) {
			if (slot > 0) json.append(",");
			int appearanceId = raster.layerAnimation[slot];
			AnimationDef def = EntityHandler.getPlayerAppearanceDef(appearanceId);
			json.append("{\"slot\":").append(slot).append(",\"appearanceId\":").append(appearanceId)
				.append(",\"definition\":");
			if (def == null) {
				json.append("null");
			} else {
				json.append("{");
				appendString(json, "name", def.getName()).append(",");
				appendString(json, "category", def.category).append(",");
				json.append("\"spriteBase\":").append(def.getNumber()).append(",");
				json.append("\"charColour\":").append(def.getCharColour()).append(",");
				json.append("\"blueMask\":").append(def.getBlueMask()).append(",");
				json.append("\"hasA\":").append(def.hasA()).append(",");
				json.append("\"hasF\":").append(def.hasF()).append("}");
			}
			json.append("}");
		}
		json.append("]}");
		return json;
	}

	private static StringBuilder appendIntArray(StringBuilder json, String name, int[] values) {
		json.append("\"").append(name).append("\":[");
		for (int i = 0; i < values.length; i++) {
			if (i > 0) json.append(",");
			json.append(values[i]);
		}
		return json.append("]");
	}

	private static StringBuilder appendStringList(StringBuilder json, String name, List<String> values) {
		json.append("\"").append(name).append("\":[");
		for (int i = 0; i < values.size(); i++) {
			if (i > 0) json.append(",");
			json.append("\"").append(jsonEscape(values.get(i))).append("\"");
		}
		return json.append("]");
	}

	private static long percentileNearestRank(long[] sortedValues, int percentile) {
		if (sortedValues == null || sortedValues.length == 0) {
			throw new IllegalArgumentException("percentile sample is empty");
		}
		if (percentile < 1 || percentile > 100) {
			throw new IllegalArgumentException("percentile is outside 1..100");
		}
		int rank = (percentile * sortedValues.length + 99) / 100;
		return sortedValues[Math.max(0, Math.min(sortedValues.length - 1, rank - 1))];
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
		AuctionHouse auctionHouse = requireAuctionHouse();
		clickGame(auctionHouse.workbenchTabCenterX(tab), auctionHouse.workbenchTabCenterY(), "left");
	}

	private static void clearWorkbenchBlockingUi() throws IOException {
		final mudclient client = requireClient();
		runOnEdt(() -> {
			client.workbenchCloseChristmasCrackerFixture();
			client.workbenchCloseDuelJournalFixture();
			PkCatchingInterface pk = client.getPkCatchingInterface();
			if (pk != null) pk.reset();
			client.setOptionsMenuShow(false);
			client.setOptionsMenuCount(0);
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

	// Dismiss welcome / server-message / shop / bank dialogs that stack on top
	// of an interface after an admin command, WITHOUT closing the Auction House.
	// clearWorkbenchBlockingUi() closes the AH, so calling it after the AH is
	// open (as the auction scenario used to) blanks the very window under test.
	private static void clearWorkbenchOverlayDialogs() throws IOException {
		final mudclient client = requireClient();
		runOnEdt(() -> {
			client.setShowDialogServerMessage(false);
			client.setShowDialogMessage(false);
			client.setWelcomeScreenShown(false);
			client.setShowDialogBank(false);
			client.setShowDialogShop(false);
		});
	}

	private static boolean isLoggedIn() {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		ORSCharacter player = client == null ? null : client.getLocalPlayer();
		return player != null && player.accountName != null && !player.accountName.trim().isEmpty();
	}

	private static void clickSavedUserLogin() throws IOException {
		mudclient client = requireClient();
		clickGame(client.workbenchLoginHomeExistingUserX(), client.workbenchLoginHomeExistingUserY(), "left");
		sleep(350);
		clickGame(client.workbenchExistingUserOkX(), client.workbenchExistingUserOkY(), "left");
	}

	private static void clickAuctionCategory(int filter) throws IOException {
		AuctionHouse auctionHouse = requireAuctionHouse();
		clickGame(auctionHouse.workbenchCategoryCenterX(filter), auctionHouse.workbenchCategoryCenterY(filter), "left");
	}

	private static void clickFirstAuctionRow() throws IOException {
		AuctionHouse auctionHouse = requireAuctionHouse();
		clickGame(auctionHouse.workbenchFirstRowCenterX(), auctionHouse.workbenchFirstRowCenterY(), "left");
	}

	private static AuctionHouse getAuctionHouse() {
		mudclient client = ORSCApplet.getMudclientForWorkbench();
		return client == null ? null : client.getAuctionHouse();
	}

	private static AuctionHouse requireAuctionHouse() throws IOException {
		AuctionHouse auctionHouse = getAuctionHouse();
		if (auctionHouse == null) {
			throw new IOException("Auction House is not available");
		}
		return auctionHouse;
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
		final ORSCApplet applet = requireApplet();
		runOnEdt(() -> {
			int eventX = x + client.screenOffsetX;
			int eventY = y + client.screenOffsetY;
			long now = System.currentTimeMillis();
			Component source = applet;
			ORSCApplet.MouseHandler mouseHandler = applet.getMouseHandler();
			mouseHandler.mouseMoved(new MouseEvent(source, MouseEvent.MOUSE_MOVED, now,
				0, eventX, eventY, 0, false, MouseEvent.NOBUTTON));
			mouseHandler.mouseWheelMoved(new MouseWheelEvent(source, MouseEvent.MOUSE_WHEEL,
				now + 1, 0, eventX, eventY, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL,
				1, amount));
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
		});
		// Directional camera/zoom input is sampled by the game loop. Keep the
		// synthetic key down long enough for at least one frame to observe it.
		sleep(75);
		runOnEdt(() -> {
			long now = System.currentTimeMillis();
			Component source = applet;
			ORSCApplet.KeyHandler keyHandler = applet.getKeyHandler();
			keyHandler.keyReleased(new KeyEvent(source, KeyEvent.KEY_RELEASED, now, 0, keyCode, character));
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

	private static long requiredLong(Map<String, String> fields, String key) throws IOException {
		try {
			return Long.parseLong(requiredString(fields, key));
		} catch (NumberFormatException e) {
			throw new IOException("Expected long integer field: " + key);
		}
	}

	private static int optionalRgb(Map<String, String> fields, String key, int defaultValue)
		throws IOException {
		String text = fields.get(key);
		if (text == null || text.trim().isEmpty()) return defaultValue;
		String normalized = text.trim().toLowerCase(Locale.ROOT);
		if (normalized.startsWith("#")) normalized = normalized.substring(1);
		if (normalized.startsWith("0x")) normalized = normalized.substring(2);
		try {
			int value = Integer.parseInt(normalized, 16);
			if (value < 0 || value > 0x00ffffff) throw new IOException("RGB field is out of range: " + key);
			return value;
		} catch (NumberFormatException e) {
			throw new IOException("Expected a six-digit hexadecimal RGB field: " + key, e);
		}
	}

	private static PaperdollV2BaseProfile optionalBaseProfile(Map<String, String> fields, String key)
		throws IOException {
		String value = fields.get(key);
		if (value == null || value.trim().isEmpty() || "auto".equalsIgnoreCase(value.trim())
			|| "derived".equalsIgnoreCase(value.trim())) return null;
		return PaperdollV2BaseProfile.parse(value);
	}

	private static int optionalPaletteIndex(Map<String, String> fields, String key, int defaultValue,
		int paletteLength) throws IOException {
		int value = fields.containsKey(key) ? requiredInt(fields, key) : defaultValue;
		if (paletteLength <= 0 || value < 0 || value >= paletteLength) {
			throw new IOException("Paperdoll V2 palette index " + key + "=" + value
				+ " is outside 0.." + Math.max(-1, paletteLength - 1));
		}
		return value;
	}

	private static int optionalAppearanceId(Map<String, String> fields, String key, int defaultValue)
		throws IOException {
		int value = fields.containsKey(key) ? requiredInt(fields, key) : defaultValue;
		if (value < 0) throw new IOException("Paperdoll V2 appearance id must be non-negative: " + key);
		if (value > 0 && EntityHandler.getPlayerAppearanceDef(value) == null) {
			throw new IOException("Paperdoll V2 appearance id is not loaded: " + key + "=" + value);
		}
		return value;
	}

	private static PaperdollV2Input resolvePaperdollV2Input(Map<String, String> fields)
		throws IOException {
		String archiveValue = fields.get("archive");
		String workspaceValue = fields.get("workspace");
		boolean hasArchive = archiveValue != null && !archiveValue.trim().isEmpty();
		boolean hasWorkspace = workspaceValue != null && !workspaceValue.trim().isEmpty();
		if (hasArchive == hasWorkspace) {
			throw new IOException("Provide exactly one Paperdoll V2 archive or workspace field");
		}

		File repository = findRepositoryRoot();
		File tmpRoot = new File(repository, "tmp").getCanonicalFile();
		if (!tmpRoot.isDirectory()) throw new IOException("Repository tmp directory does not exist: " + tmpRoot);
		File archive;
		File workspace;
		if (hasWorkspace) {
			workspace = requireUnsymLinkedTmpPath(tmpRoot,
				resolveRepositoryPath(repository, workspaceValue), "Paperdoll V2 workspace");
			if (!isWithin(tmpRoot, workspace) || !workspace.isDirectory()) {
				throw new IOException("Paperdoll V2 workspace must be an existing directory under repository tmp");
			}
			archive = requireUnsymLinkedTmpPath(tmpRoot,
				new File(workspace, "Paperdoll_V2.orsc"), "Paperdoll V2 archive");
		} else {
			archive = requireUnsymLinkedTmpPath(tmpRoot,
				resolveRepositoryPath(repository, archiveValue), "Paperdoll V2 archive");
			workspace = requireUnsymLinkedTmpPath(tmpRoot, archive.getParentFile(),
				"Paperdoll V2 workspace");
		}
		if (!isWithin(tmpRoot, archive) || !isWithin(tmpRoot, workspace)) {
			throw new IOException("Paperdoll V2 archive and workspace must stay under repository tmp");
		}
		if (!archive.isFile() || !"Paperdoll_V2.orsc".equals(archive.getName())) {
			throw new IOException("Paperdoll V2 archive must be an existing Paperdoll_V2.orsc file");
		}
		return new PaperdollV2Input(archive.getAbsoluteFile(), workspace.getAbsoluteFile());
	}

	private static File requireUnsymLinkedTmpPath(File tmpRoot, File configured,
		String label) throws IOException {
		File root = tmpRoot.getCanonicalFile();
		File lexical = configured.getAbsoluteFile().toPath().normalize().toFile();
		if (!lexical.getPath().startsWith(root.getPath() + File.separator)) {
			throw new IOException(label + " must stay under repository tmp");
		}
		java.nio.file.Path cursor = root.toPath();
		for (java.nio.file.Path part : cursor.relativize(lexical.toPath())) {
			cursor = cursor.resolve(part);
			if (Files.isSymbolicLink(cursor)) {
				throw new IOException(label + " path contains a symlink");
			}
		}
		File canonical = lexical.getCanonicalFile();
		if (!lexical.equals(canonical)) {
			throw new IOException(label + " path is not canonical");
		}
		return canonical;
	}

	private static File createPaperdollV2OutputDirectory(File workspace) throws IOException {
		File repository = findRepositoryRoot();
		File tmpRoot = new File(repository, "tmp").getCanonicalFile();
		File oracleRoot = new File(workspace, "java-oracle");
		Files.createDirectories(oracleRoot.toPath());
		oracleRoot = requireUnsymLinkedTmpPath(tmpRoot, oracleRoot,
			"Paperdoll V2 oracle output");
		if (!isWithin(tmpRoot, oracleRoot)) {
			throw new IOException("Paperdoll V2 output directory escaped repository tmp");
		}
		String baseName = fileTimestamp();
		for (int suffix = 0; suffix < 1000; suffix++) {
			String name = suffix == 0 ? baseName : baseName + "-" + suffix;
			File candidate = new File(oracleRoot, name);
			if (candidate.mkdir()) return candidate.getCanonicalFile();
			if (!candidate.exists()) throw new IOException("Unable to create Paperdoll V2 output directory");
		}
		throw new IOException("Unable to allocate a unique Paperdoll V2 output directory");
	}

	private static File findRepositoryRoot() throws IOException {
		File cursor = new File(System.getProperty("user.dir", ".")).getCanonicalFile();
		for (int depth = 0; cursor != null && depth < 10; depth++, cursor = cursor.getParentFile()) {
			if (new File(cursor, "AGENTS.md").isFile() && new File(cursor, "Client_Base").isDirectory()
				&& new File(cursor, "PC_Client").isDirectory()) return cursor;
		}
		throw new IOException("Unable to locate the Voidscape repository root");
	}

	private static File resolveRepositoryPath(File repository, String value) {
		File file = new File(value.trim());
		return file.isAbsolute() ? file : new File(repository, value.trim());
	}

	private static boolean isWithin(File root, File candidate) throws IOException {
		String rootPath = root.getCanonicalPath();
		String candidatePath = candidate.getCanonicalPath();
		return candidatePath.equals(rootPath) || candidatePath.startsWith(rootPath + File.separator);
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

	private static final class PaperdollV2Input {
		final File archiveFile;
		final File workspaceDirectory;

		PaperdollV2Input(File archiveFile, File workspaceDirectory) {
			this.archiveFile = archiveFile;
			this.workspaceDirectory = workspaceDirectory;
		}
	}

	private static final class PaperdollV2RuntimeSelection {
		final boolean selectorMode;
		final int selectorId;
		final String style;
		final PaperdollV2BaseProfile baseProfile;
		final PaperdollV2Pack.RenderStack stack;
		final int hairStyle;

		PaperdollV2RuntimeSelection(boolean selectorMode, int selectorId, String style,
			PaperdollV2BaseProfile baseProfile, PaperdollV2Pack.RenderStack stack,
			int hairStyle) {
			this.selectorMode = selectorMode;
			this.selectorId = selectorId;
			this.style = style;
			this.baseProfile = baseProfile;
			this.stack = stack;
			this.hairStyle = hairStyle;
		}
	}

	private static final class PaperdollV2Capture {
		final String stackId;
		final String stackMode;
		final String baseProfile;
		final PaperdollV2Pose pose;
		final PaperdollV2RasterArtifact fullPanel;
		final PaperdollV2RasterArtifact v2Only;
		final int[] paperdollSlots;
		final int[] layerAnimation;
		final int[] layerOrder;
		final int[] tintRgb;

		PaperdollV2Capture(String stackId, String stackMode, String baseProfile,
			PaperdollV2Pose pose,
			PaperdollV2RasterArtifact fullPanel, PaperdollV2RasterArtifact v2Only,
			int[] paperdollSlots, int[] layerAnimation, int[] layerOrder, int[] tintRgb) {
			this.stackId = stackId;
			this.stackMode = stackMode;
			this.baseProfile = baseProfile;
			this.pose = pose;
			this.fullPanel = fullPanel;
			this.v2Only = v2Only;
			this.paperdollSlots = paperdollSlots;
			this.layerAnimation = layerAnimation;
			this.layerOrder = layerOrder;
			this.tintRgb = tintRgb;
		}
	}

	private static final class PaperdollV2RasterArtifact {
		final File pngFile;
		final String pngSha256;
		final String rawRgbSha256;
		final boolean empty;
		final int cropX;
		final int cropY;
		final int cropWidth;
		final int cropHeight;

		PaperdollV2RasterArtifact(File pngFile, String pngSha256, String rawRgbSha256,
			boolean empty, int cropX, int cropY, int cropWidth, int cropHeight) {
			this.pngFile = pngFile;
			this.pngSha256 = pngSha256;
			this.rawRgbSha256 = rawRgbSha256;
			this.empty = empty;
			this.cropX = cropX;
			this.cropY = cropY;
			this.cropWidth = cropWidth;
			this.cropHeight = cropHeight;
		}
	}

	private static final class ChristmasCrackerFixture {
		final String outcome;
		final int itemId;
		final long seed;
		final String phase;
		final boolean capture;

		ChristmasCrackerFixture(String outcome, int itemId, long seed, String phase, boolean capture) {
			this.outcome = outcome;
			this.itemId = itemId;
			this.seed = seed;
			this.phase = phase;
			this.capture = capture;
		}
	}

	private static final class CowboyPreviewState {
		final int renderPlayerIndex;
		final ORSCharacter renderPlayer;
		final int[] layerAnimation;
		final int paperdollSlot;
		final int colourHair;
		final int colourTop;
		final int colourBottom;
		final int colourSkin;
		final int hairStyle;
		final boolean invisible;
		final boolean invulnerable;
		final ORSCharacterDirection direction;
		final int animationNext;
		final int stepFrame;
		final int cameraRotation;
		final int clientCombatTimeout;
		final Field clientCombatTimeoutField;

		CowboyPreviewState(mudclient client, ORSCharacter player, int paperdollSlot) throws IOException {
			int foundPlayerIndex = -1;
			for (int i = 0; i < client.getPlayerCount(); i++) {
				if (client.getPlayer(i) == player) {
					foundPlayerIndex = i;
					break;
				}
			}
			if (foundPlayerIndex < 0) {
				throw new IOException("Local player is not present in the render player array");
			}
			renderPlayerIndex = foundPlayerIndex;
			renderPlayer = client.getPlayer(foundPlayerIndex);
			layerAnimation = player.layerAnimation.clone();
			this.paperdollSlot = paperdollSlot;
			colourHair = player.colourHair;
			colourTop = player.colourTop;
			colourBottom = player.colourBottom;
			colourSkin = player.colourSkin;
			hairStyle = player.hairStyle;
			invisible = player.isInvisible;
			invulnerable = player.isInvulnerable;
			direction = player.direction;
			animationNext = player.animationNext;
			stepFrame = player.stepFrame;
			cameraRotation = client.cameraRotation;
			try {
				clientCombatTimeoutField = mudclient.class.getDeclaredField("combatTimeout");
				clientCombatTimeoutField.setAccessible(true);
				clientCombatTimeout = clientCombatTimeoutField.getInt(client);
			} catch (NoSuchFieldException | IllegalAccessException | SecurityException e) {
				throw new IOException("Unable to snapshot client combat state", e);
			}
		}

		void restore(final mudclient client, final ORSCharacter player) throws IOException {
			runOnEdt(() -> {
				client.setPlayer(renderPlayerIndex, renderPlayer);
				System.arraycopy(layerAnimation, 0, player.layerAnimation, 0,
					Math.min(layerAnimation.length, player.layerAnimation.length));
				player.colourHair = colourHair;
				player.colourTop = colourTop;
				player.colourBottom = colourBottom;
				player.colourSkin = colourSkin;
				player.hairStyle = hairStyle;
				player.isInvisible = invisible;
				player.isInvulnerable = invulnerable;
				player.direction = direction;
				player.animationNext = animationNext;
				player.stepFrame = stepFrame;
				client.cameraRotation = cameraRotation;
				try {
					clientCombatTimeoutField.setInt(client, clientCombatTimeout);
				} catch (IllegalAccessException e) {
					throw new IOException("Unable to restore client combat state", e);
				}
			});
		}

		boolean matches(mudclient client, ORSCharacter player) throws IOException {
			if (client.getPlayer(renderPlayerIndex) != renderPlayer
				|| player.layerAnimation.length != layerAnimation.length
				|| player.colourHair != colourHair || player.colourTop != colourTop
				|| player.colourBottom != colourBottom || player.colourSkin != colourSkin
				|| player.hairStyle != hairStyle || player.isInvisible != invisible
				|| player.isInvulnerable != invulnerable || player.direction != direction
				|| player.animationNext != animationNext || player.stepFrame != stepFrame
				|| client.cameraRotation != cameraRotation) return false;
			for (int i = 0; i < layerAnimation.length; i++) {
				if (player.layerAnimation[i] != layerAnimation[i]) return false;
			}
			try {
				return clientCombatTimeoutField.getInt(client) == clientCombatTimeout;
			} catch (IllegalAccessException e) {
				throw new IOException("Unable to verify restored client combat state", e);
			}
		}
	}

	private static final class CowboyFrameCapture {
		final int spriteOffset;
		final String kind;
		final String direction;
		final int frame;
		final int wantedAnimDir;
		final int actualAnimDir;
		final boolean mirrorX;
		final int rendererFrameCounter;
		final CaptureResult capture;
		final AppearanceRasterCapture raster;

		CowboyFrameCapture(int spriteOffset, String kind, String direction, int frame,
			int wantedAnimDir, int actualAnimDir, boolean mirrorX,
			int rendererFrameCounter, CaptureResult capture, AppearanceRasterCapture raster) {
			this.spriteOffset = spriteOffset;
			this.kind = kind;
			this.direction = direction;
			this.frame = frame;
			this.wantedAnimDir = wantedAnimDir;
			this.actualAnimDir = actualAnimDir;
			this.mirrorX = mirrorX;
			this.rendererFrameCounter = rendererFrameCounter;
			this.capture = capture;
			this.raster = raster;
		}
	}

	private static final class AppearanceRasterCapture {
		final File pngFile;
		final String pngSha256;
		final String rawRgbSha256;
		final int cropX;
		final int cropY;
		final int cropWidth;
		final int cropHeight;
		final int[] layerAnimation;
		final int[] layerOrder;
		final int[] paletteIndices;
		final int[] resolvedColours;

		AppearanceRasterCapture(File pngFile, String pngSha256, String rawRgbSha256,
			int cropX, int cropY, int cropWidth, int cropHeight, int[] layerAnimation,
			int[] layerOrder, int[] paletteIndices, int[] resolvedColours) {
			this.pngFile = pngFile;
			this.pngSha256 = pngSha256;
			this.rawRgbSha256 = rawRgbSha256;
			this.cropX = cropX;
			this.cropY = cropY;
			this.cropWidth = cropWidth;
			this.cropHeight = cropHeight;
			this.layerAnimation = layerAnimation;
			this.layerOrder = layerOrder;
			this.paletteIndices = paletteIndices;
			this.resolvedColours = resolvedColours;
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
