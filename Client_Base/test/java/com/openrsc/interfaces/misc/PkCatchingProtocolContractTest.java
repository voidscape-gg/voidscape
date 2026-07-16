package com.openrsc.interfaces.misc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Source-level wire contract test. This deliberately compares both ends of the
 * hidden Quest-message channel so a server-only payload edit cannot silently
 * desynchronise the shared client parser.
 */
public final class PkCatchingProtocolContractTest {
	private static final String SERVER_PATH =
		"server/plugins/com/openrsc/server/plugins/custom/minigames/PkCatchingSimulator.java";
	private static final String CLIENT_PATH =
		"Client_Base/src/com/openrsc/interfaces/misc/PkCatchingInterface.java";
	private static final String PACKET_HANDLER_PATH = "Client_Base/src/orsc/PacketHandler.java";
	private static final String ATTACK_HANDLER_PATH =
		"server/src/com/openrsc/server/net/rsc/handlers/AttackHandler.java";
	private static final String WALK_TO_MOB_ACTION_PATH =
		"server/src/com/openrsc/server/model/action/WalkToMobAction.java";

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected the repository root as the only argument");
		}
		Path root = Paths.get(args[0]).toAbsolutePath().normalize();
		String server = read(root.resolve(SERVER_PATH));
		String client = read(root.resolve(CLIENT_PATH));
		String packetHandler = read(root.resolve(PACKET_HANDLER_PATH));
		String attackHandler = read(root.resolve(ATTACK_HANDLER_PATH));
		String walkToMobAction = read(root.resolve(WALK_TO_MOB_ACTION_PATH));

		reservedNamespaceAndTransportMatch(server, client, packetHandler);
		payloadFieldCountsMatch(server, client);
		chooserAndLegacyFallbackSignaturesMatch(server, client);
		competitionPersonalRanksAcceptTiedRows(client);
		authenticCatchReachIsShared(server, attackHandler, walkToMobAction);
		onlyAttackNpcCallbackCanScore(server);
		missRecoveryDoesNotPolluteMetrics(server);
		trainerCoachAndClassicDirectorStayExact(server);
		hardDirectorWeightsAndOneTileAuthorityStayExact(server);
		System.out.println("PK Catching Simulator server/client contract tests passed");
	}

	private static void reservedNamespaceAndTransportMatch(
			String server, String client, String packetHandler) {
		assertContains(server, "private static final String CLIENT_PREFIX = \"@vspkcatch@v1\";",
			"server protocol namespace");
		assertContains(client, "private static final String PREFIX = \"@vspkcatch@v1|\";",
			"client protocol namespace");
		String sender = methodBody(server, "private static void sendClientMetadata(");
		assertContains(sender,
			"ActionSender.sendMessage(player, null, MessageType.QUEST, CLIENT_PREFIX + \"|\" + state, 0, null);",
			"senderless Quest-message transport");
		assertContains(server, "private static final int CLIENT_METADATA_MIN_VERSION = 10139;",
			"metadata compatibility gate");
		assertContains(packetHandler,
			"sender == null && type == MessageType.QUEST\n\t\t\t\t&& mc.handleVoidscapePkCatchingMessage(message)",
			"client consumes only senderless Quest metadata");
	}

	private static void payloadFieldCountsMatch(String server, String client) {
		assertPayload(server, client, "chooser", "handleChooser", 2);
		assertPayload(server, client, "start", "handleStart", 5);
		assertPayload(server, client, "hud", "handleHud", 14);
		assertPayload(server, client, "guide", "handleGuide", 5);
		assertPayload(server, client, "clear", "handleClear", 3);
		assertPayload(server, client, "result", "handleResult", 14);
		assertPayload(server, client, "leaderboard-begin", "handleLeaderboardBegin", 9);
		assertPayload(server, client, "leaderboard-row", "handleLeaderboardRow", 8);
		assertPayload(server, client, "leaderboard-end", "handleLeaderboardEnd", 2);
	}

	private static void chooserAndLegacyFallbackSignaturesMatch(String server, String client) {
		String[] options = new String[] {
			"Easy / Trainer (unranked)",
			"Medium (classic ranked)",
			"Hard (ranked)"
		};
		for (String option : options) {
			assertContains(server, "\"" + option + "\"", "server chooser option " + option);
			assertContains(client, "\"" + option + "\"", "client chooser option " + option);
		}
		String matcher = methodBody(client, "public boolean matchesChooser(");
		assertContains(matcher, "optionCount == 3", "chooser option count");
		assertContains(matcher,
			"EASY_OPTION.equals(options[0]) && MEDIUM_OPTION.equals(options[1])\n\t\t\t&& HARD_OPTION.equals(options[2])",
			"chooser option ordering");

		assertContains(server, "builder.append(\"PK Catching Simulator% %\");",
			"round-result legacy box prefix");
		assertContains(server, "builder.append(\"PK Catching Simulator Highscores% %\");",
			"leaderboard legacy box prefix");
		assertContains(client,
			"private static final String LEGACY_RESULT_PREFIX = \"PK Catching Simulator% %\";",
			"client result fallback prefix");
		assertContains(client,
			"private static final String LEGACY_HIGHSCORES_PREFIX = \"PK Catching Simulator Highscores% %\";",
			"client leaderboard fallback prefix");
		String suppression = methodBody(client, "public boolean shouldSuppressLegacyBox(");
		assertContains(suppression, "message.startsWith(LEGACY_HIGHSCORES_PREFIX)",
			"leaderboard fallback suppression prefix");
		assertContains(suppression, "message.startsWith(LEGACY_RESULT_PREFIX)",
			"result fallback suppression prefix");
	}

	private static void authenticCatchReachIsShared(
			String server, String attackHandler, String walkToMobAction) {
		assertNotContains(attackHandler, "PK_CATCHING_SIM_PVP_DISTANCE",
			"simulator must not inflate the configured PvP catching radius");
		assertNotContains(attackHandler,
			"Math.max(player.getConfig().PVP_CATCHING_DISTANCE",
			"simulator must not retain a radius-two fallback");
		String attackProcess = methodBody(attackHandler,
			"public void process(TargetMobStruct payload, Player player)");
		assertContains(attackProcess,
			"int radius = affectedMob.isPlayer() || pkCatchingSimulatorTarget\n"
				+ "\t\t\t\t? player.getConfig().PVP_CATCHING_DISTANCE",
			"players and simulator targets share the configured PvP radius");

		assertNotContains(walkToMobAction, "isPkCatchingSimulatorAttack",
			"simulator must not bypass normal walk-to-mob collision validation");
		String action = methodBody(walkToMobAction,
			"public boolean shouldExecuteInternal(");
		assertContains(action,
			"isWithinInteractionReach(\n\t\t\tgetPlayer(), mob, radius, ignoreProjectileAllowed)",
			"normal actions use the shared interaction reach predicate");
		String sharedReach = methodBody(walkToMobAction,
			"public static boolean isWithinInteractionReach(");
		assertContains(sharedReach, "player.getWalkingQueue().getNextMovement()",
			"melee reach inspects the next legal queued movement");
		assertContains(walkToMobAction, "PathValidation.checkAdjacentDistance(",
			"shared reach retains authentic terrain-boundary validation");

		String guide = methodBody(server, "private TrainerGuide trainerGuide(");
		assertContains(guide,
			"WalkToMobAction.isWithinInteractionReach(player, target,\n"
				+ "\t\t\t\t\t\tplayer.getConfig().PVP_CATCHING_DISTANCE, true)",
			"Trainer ATTACK NOW uses the same authoritative reach predicate");
	}

	private static void onlyAttackNpcCallbackCanScore(String server) {
		String observer = methodBody(server, "private void observeAttackAction(");
		assertNotContains(observer, "recordCatch(",
			"attack observer may collect metrics but cannot award a catch");
		assertNotContains(observer, "startPvpStyleCombatRound(",
			"attack observer may not start a scoring combat round");

		String callback = methodBody(server, "public void onAttackNpc(");
		assertOrdered(callback,
			"session.advanceAttemptPhase(tick);",
			"if (!session.canScoreCatch(tick))",
			"session.recordCatch(tick, goodTrail);",
			"session.startPvpStyleCombatRound(tick);");
		assertEquals(2, count(server, "recordCatch("),
			"recordCatch must have one declaration and one authoritative call site");
	}

	private static void missRecoveryDoesNotPolluteMetrics(String server) {
		String recovery = methodBody(server, "private void beginMissRecovery(");
		assertContains(recovery, "consecutiveTrailTicks = 0;",
			"a miss resets the trail-message streak");
		assertContains(recovery, "consecutiveBadPathTicks = 0;",
			"a miss resets the bad-path warning streak");

		String sampler = methodBody(server, "private void samplePosition(");
		assertContains(sampler,
			"|| attemptClock.getPhase() == AttemptPhase.MISS_RECOVERY",
			"stationary recovery ticks cannot affect trail or bad-path feedback");
	}

	private static void trainerCoachAndClassicDirectorStayExact(String server) {
		assertContains(server, "TRAINER(\"trainer\", false)", "Trainer remains unranked");
		assertContains(server, "CLASSIC(\"classic\", true)", "Medium/Classic remains ranked");
		assertContains(server, "HARD(\"hard\", true)", "Hard remains separately ranked");

		String constructor = methodBody(server, "private CatchSession(Player player,");
		assertContains(constructor,
			"mode == SessionMode.TRAINER ? Difficulty.EASY : rollDifficulty()",
			"Trainer is fixed Easy while Classic keeps the mixed director");
		String roundReset = methodBody(server, "private void finishCombatRound(");
		assertOrdered(roundReset,
			"if (mode == SessionMode.HARD)", "difficulty = Difficulty.HARD;",
			"} else if (mode == SessionMode.TRAINER)", "difficulty = Difficulty.EASY;",
			"} else {", "difficulty = rollDifficulty();");

		String roll = methodBody(server, "private Difficulty rollDifficulty(");
		assertOrdered(roll,
			"if (roll < 45)", "return Difficulty.EASY;",
			"if (roll < 80)", "return Difficulty.MEDIUM;",
			"return Difficulty.HARD;");

		assertContains(server, "private static final int TRAINER_MOVE_INTERVAL_TICKS = 2;",
			"Trainer moves every two server ticks");
		assertContains(server, "private static final int TRAINER_ROUTE_STEPS = 4;",
			"Trainer commits a four-step coached segment");
		String movement = methodBody(server, "private void moveTarget(");
		assertOrdered(movement, new String[] {
			"if (mode == SessionMode.TRAINER)",
			"nextMoveTick = tick + TRAINER_MOVE_INTERVAL_TICKS;",
			"next = chooseTrainerNextTargetTile(current);"
		});
		assertOrdered(movement, "previousTargetLocation = current;", "target.setLocation(next, false);",
			"trail tile remains distinct from the coached destination");

		String routeBuilder = methodBody(server, "private void buildTrainerSegment(");
		assertContains(routeBuilder,
			"trainerWaypoint = bestRoute.get(bestRoute.size() - 1);",
			"Trainer publishes the committed route endpoint");
		String destination = methodBody(server, "private Point trainerGuideDestination(");
		assertContains(destination, "? target.getLocation() : trainerWaypoint",
			"pursuit guide remains fixed on the committed waypoint");
		assertNotContains(destination, "previousTargetLocation",
			"Trainer guide must not chase the tile the target just vacated");

		String guide = methodBody(server, "private TrainerGuide trainerGuide(");
		assertContains(guide, "mode != SessionMode.TRAINER", "guidance is Trainer-only");
		assertContains(guide,
			"phase == AttemptPhase.COMBAT || phase == AttemptPhase.REATTACK_LOCK\n"
				+ "\t\t\t\t\t|| phase == AttemptPhase.MISS_RECOVERY",
			"guidance stays hidden during combat, immunity, and miss recovery");
		assertContains(server,
			"HIDDEN(\"hidden\"),\n\t\tDESTINATION(\"destination\"),\n\t\tATTACK(\"attack\")",
			"guide wire states remain exact");
	}

	private static void competitionPersonalRanksAcceptTiedRows(String client) {
		String rowHandler = methodBody(client, "private void handleLeaderboardRow(");
		assertContains(rowHandler,
			"selfFlag == 1 && (personalRank < 1 || personalBest != nextCatches)",
			"self row validates the authoritative personal score");
		assertNotContains(rowHandler, "personalRank != rank",
			"competition personal rank must not be compared with the row ordinal when scores tie");
	}

	private static void hardDirectorWeightsAndOneTileAuthorityStayExact(String server) {
		String plan = methodBody(server, "private boolean beginHardRunPlan(");
		assertContains(plan, "int roll = random.nextInt(100);", "Hard plan roll range");
		assertOrdered(plan,
			"if (roll < 40)", "hardRunType = HardRunType.DIAGONAL",
			"if (roll < 65)", "hardRunType = HardRunType.CUTBACK",
			"if (roll < 85)", "hardRunType = HardRunType.OBSTACLE",
			"hardRunType = HardRunType.CRAZY");

		String chooser = methodBody(server, "private Point chooseHardNextTargetTile(");
		assertContains(chooser, "!canTargetStep(current, candidate)",
			"every Hard emitted tile is revalidated");
		String validator = methodBody(server, "private boolean canTargetStep(");
		assertContains(validator, "PathValidation.checkAdjacent(target, current, candidate)",
			"Hard movement remains one adjacent tile per tick");
		assertContains(server,
			"{ 0, 1 }, { 1, 1 }, { 1, 0 }, { 1, -1 },\n\t\t{ 0, -1 }, { -1, -1 }, { -1, 0 }, { -1, 1 }",
			"Hard heading set contains only adjacent compass steps");
	}

	private static void assertPayload(
			String server, String client, String kind, String handler, int expectedFields) {
		String marker = "\"" + kind + "|";
		int start = server.indexOf(marker);
		if (start < 0) throw new AssertionError("Missing server payload " + kind);
		int end = server.indexOf(");", start);
		if (end < 0) throw new AssertionError("Unterminated server payload " + kind);
		String expression = server.substring(start, end);
		int separators = count(expression, '|');
		assertEquals(expectedFields - 1, separators,
			kind + " server separator count");

		String body = methodBody(client, "private void " + handler + "(");
		assertContains(body, "parts.length != " + expectedFields,
			kind + " client field-count guard");
	}

	private static String methodBody(String source, String signature) {
		int signatureAt = source.indexOf(signature);
		if (signatureAt < 0) throw new AssertionError("Missing method signature " + signature);
		int open = source.indexOf('{', signatureAt);
		if (open < 0) throw new AssertionError("Missing method body for " + signature);
		int depth = 0;
		for (int i = open; i < source.length(); i++) {
			char c = source.charAt(i);
			if (c == '{') depth++;
			else if (c == '}' && --depth == 0) return source.substring(open + 1, i);
		}
		throw new AssertionError("Unterminated method body for " + signature);
	}

	private static String read(Path path) throws IOException {
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
			.replace("\r\n", "\n");
	}

	private static int count(String text, char needle) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == needle) count++;
		}
		return count;
	}

	private static int count(String text, String needle) {
		int count = 0;
		int cursor = 0;
		while ((cursor = text.indexOf(needle, cursor)) >= 0) {
			count++;
			cursor += needle.length();
		}
		return count;
	}

	private static void assertOrdered(String text, String first, String second, String message) {
		int firstAt = text.indexOf(first);
		int secondAt = text.indexOf(second);
		if (firstAt < 0 || secondAt <= firstAt) {
			throw new AssertionError(message + ": expected \"" + first + "\" before \"" + second + "\"");
		}
	}

	private static void assertOrdered(String text, String... fragments) {
		int cursor = -1;
		for (String fragment : fragments) {
			int next = text.indexOf(fragment, cursor + 1);
			if (next < 0) {
				throw new AssertionError("Missing or out-of-order contract fragment: " + fragment);
			}
			cursor = next;
		}
	}

	private static void assertContains(String text, String expected, String message) {
		if (!text.contains(expected)) {
			throw new AssertionError(message + ": missing contract fragment \"" + expected + "\"");
		}
	}

	private static void assertNotContains(String text, String unexpected, String message) {
		if (text.contains(unexpected)) {
			throw new AssertionError(message + ": unexpected contract fragment \"" + unexpected + "\"");
		}
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
