package com.openrsc.interfaces.misc;

import orsc.Config;
import orsc.ORSCharacter;
import orsc.graphics.gui.UiAnchor;
import orsc.graphics.gui.UiSkin;
import orsc.graphics.three.Scene;
import orsc.graphics.three.World;
import orsc.graphics.two.GraphicsController;
import orsc.mudclient;

/**
 * Void Glass presentation and client-side state for the PK Catching Simulator.
 *
 * The server remains authoritative for every score, timer, and Trainer hint.
 * This class consumes versioned Quest-message controls, rejects stale session
 * state, and owns the geometry shared by rendering, hit-testing, and Workbench.
 */
public final class PkCatchingInterface {

	public static final int NO_CHOOSER_ACTION = Integer.MIN_VALUE;
	private static final String PREFIX = "@vspkcatch@v1|";
	private static final String EASY_OPTION = "Easy / Trainer (unranked)";
	private static final String MEDIUM_OPTION = "Medium (classic ranked)";
	private static final String HARD_OPTION = "Hard (ranked)";
	private static final String LEGACY_RESULT_PREFIX = "PK Catching Simulator% %";
	private static final String LEGACY_HIGHSCORES_PREFIX = "PK Catching Simulator Highscores% %";
	private static final int MAX_LEADERBOARD_ROWS = 10;
	private static final int SESSION_SECONDS = 5 * 60;
	private static final int RSC_FLOOR_HEIGHT = 944;

	public enum Mode {
		TRAINER("trainer", "EASY / TRAINER", "Guided", false),
		CLASSIC("classic", "MEDIUM", "Classic ranked", true),
		HARD("hard", "HARD", "Expert ranked", true);

		private final String wireName;
		private final String displayName;
		private final String badge;
		private final boolean ranked;

		Mode(String wireName, String displayName, String badge, boolean ranked) {
			this.wireName = wireName;
			this.displayName = displayName;
			this.badge = badge;
			this.ranked = ranked;
		}

		private static Mode fromWire(String wireName) {
			for (Mode mode : values()) {
				if (mode.wireName.equals(wireName)) return mode;
			}
			return null;
		}
	}

	private enum GuideState {
		HIDDEN("hidden"),
		DESTINATION("destination"),
		ATTACK("attack");

		private final String wireName;

		GuideState(String wireName) {
			this.wireName = wireName;
		}

		private static GuideState fromWire(String wireName) {
			for (GuideState state : values()) {
				if (state.wireName.equals(wireName)) return state;
			}
			return null;
		}
	}

	public static final class LeaderboardEntry {
		private final int rank;
		private final long usernameHash;
		private final String username;
		private final int catches;
		private final boolean self;

		private LeaderboardEntry(int rank, long usernameHash, String username, int catches, boolean self) {
			this.rank = rank;
			this.usernameHash = usernameHash;
			this.username = username;
			this.catches = catches;
			this.self = self;
		}

		public int getRank() { return rank; }
		public long getUsernameHash() { return usernameHash; }
		public String getUsername() { return username; }
		public int getCatches() { return catches; }
		public boolean isSelf() { return self; }
	}

	private enum ModalKind {
		NONE,
		RESULTS,
		LEADERBOARD
	}

	private static final class Result {
		private final long sessionId;
		private final Mode mode;
		private final boolean completed;
		private final int catches;
		private final int currentStreak;
		private final int bestStreak;
		private final int exactTrailTicks;
		private final int trailSamples;
		private final int totalReactionTicks;
		private final int reactionSamples;
		private final int rank;
		private final int personalBest;
		private final boolean newBest;

		private Result(long sessionId, Mode mode, boolean completed, int catches,
				int currentStreak, int bestStreak, int exactTrailTicks, int trailSamples,
				int totalReactionTicks, int reactionSamples, int rank, int personalBest,
				boolean newBest) {
			this.sessionId = sessionId;
			this.mode = mode;
			this.completed = completed;
			this.catches = catches;
			this.currentStreak = currentStreak;
			this.bestStreak = bestStreak;
			this.exactTrailTicks = exactTrailTicks;
			this.trailSamples = trailSamples;
			this.totalReactionTicks = totalReactionTicks;
			this.reactionSamples = reactionSamples;
			this.rank = rank;
			this.personalBest = personalBest;
			this.newBest = newBest;
		}
	}

	private static final class LeaderboardBoard {
		private final LeaderboardEntry[] rows;
		private final int personalRank;
		private final int personalBest;

		private LeaderboardBoard(LeaderboardEntry[] rows, int personalRank, int personalBest) {
			this.rows = rows;
			this.personalRank = personalRank;
			this.personalBest = personalBest;
		}
	}

	private static final class Leaderboards {
		private final long generationId;
		private final LeaderboardBoard medium;
		private final LeaderboardBoard hard;

		private Leaderboards(long generationId, LeaderboardBoard medium, LeaderboardBoard hard) {
			this.generationId = generationId;
			this.medium = medium;
			this.hard = hard;
		}
	}

	private static final class LeaderboardStage {
		private final long transferId;
		private final long generationId;
		private final LeaderboardEntry[] medium;
		private final LeaderboardEntry[] hard;
		private final int mediumRank;
		private final int mediumBest;
		private final int hardRank;
		private final int hardBest;

		private LeaderboardStage(long transferId, long generationId, int mediumCount,
				int hardCount, int mediumRank, int mediumBest, int hardRank, int hardBest) {
			this.transferId = transferId;
			this.generationId = generationId;
			this.medium = new LeaderboardEntry[mediumCount];
			this.hard = new LeaderboardEntry[hardCount];
			this.mediumRank = mediumRank;
			this.mediumBest = mediumBest;
			this.hardRank = hardRank;
			this.hardBest = hardBest;
		}
	}

	private final mudclient mc;
	private long latestGenerationId;
	private long chooserGenerationId;
	private long activeSessionId;
	private long activeGenerationId;
	private long lastClearedSessionId;
	private long lastClearedGenerationId;
	private long consumedResultSessionId;
	private Mode activeMode;
	private Mode lastClearedMode;
	private int durationTicks;
	private int remainingTicks;
	private int catches;
	private int currentStreak;
	private int bestStreak;
	private int exactTrailTicks;
	private int trailSamples;
	private int totalReactionTicks;
	private int reactionSamples;
	private boolean hintActive;
	private int hintWorldX = -1;
	private int hintWorldY = -1;
	private boolean guideSeen;
	private GuideState guideState = GuideState.HIDDEN;
	private int guideWorldX = -1;
	private int guideWorldY = -1;
	private Result result;
	private Leaderboards leaderboards;
	private LeaderboardStage leaderboardStage;
	private Mode selectedBoard = Mode.CLASSIC;
	private ModalKind modalKind = ModalKind.NONE;
	private boolean leaderboardReturnsToResult;
	private boolean suppressNextResultBox;
	private boolean suppressNextLeaderboardBox;

	private final int[] chooserPanel = new int[4];
	private final int[] chooserClose = new int[4];
	private final int[][] chooserCards = new int[][] {new int[4], new int[4], new int[4]};
	private final int[] hudRect = new int[4];
	private final int[] modalRect = new int[4];
	private final int[] modalClose = new int[4];
	private final int[] modalPrimary = new int[4];
	private final int[] modalSecondary = new int[4];
	private final int[] mediumTab = new int[4];
	private final int[] hardTab = new int[4];
	private final int[][] markerProjection = new int[][] {
		new int[3], new int[3], new int[3], new int[3]
	};
	private final int[] markerCenterProjection = new int[3];
	private boolean markerProjected;

	public PkCatchingInterface(mudclient mc) {
		this.mc = mc;
	}

	/** Returns false only for ordinary chat messages outside this reserved namespace. */
	public boolean handleServerMessage(String message) {
		if (message == null || !message.startsWith(PREFIX)) return false;
		String[] parts = message.substring(PREFIX.length()).split("\\|", -1);
		if (parts.length == 0) return true;
		try {
			String kind = parts[0];
			if ("chooser".equals(kind)) handleChooser(parts);
			else if ("start".equals(kind)) handleStart(parts);
			else if ("hud".equals(kind)) handleHud(parts);
			else if ("guide".equals(kind)) handleGuide(parts);
			else if ("clear".equals(kind)) handleClear(parts);
			else if ("result".equals(kind)) handleResult(parts);
			else if ("leaderboard-begin".equals(kind)) handleLeaderboardBegin(parts);
			else if ("leaderboard-row".equals(kind)) handleLeaderboardRow(parts);
			else if ("leaderboard-end".equals(kind)) handleLeaderboardEnd(parts);
		} catch (RuntimeException ignored) {
			if (parts[0].startsWith("leaderboard-")) leaderboardStage = null;
		}
		return true;
	}

	private void handleChooser(String[] parts) {
		if (parts.length != 2) return;
		long generation = positiveLong(parts[1]);
		if (generation <= latestGenerationId) return;
		latestGenerationId = generation;
		chooserGenerationId = generation;
		clearActiveSession(false);
		lastClearedSessionId = 0L;
		lastClearedGenerationId = 0L;
		lastClearedMode = null;
		consumedResultSessionId = 0L;
		modalKind = ModalKind.NONE;
		suppressNextResultBox = false;
		suppressNextLeaderboardBox = false;
	}

	private void handleStart(String[] parts) {
		if (parts.length != 5) return;
		long sessionId = positiveLong(parts[1]);
		long generation = positiveLong(parts[2]);
		Mode mode = Mode.fromWire(parts[3]);
		int ticks = boundedNonNegativeInt(parts[4], 1, 10000);
		if (sessionId <= 0L || generation != chooserGenerationId
				|| generation != latestGenerationId || mode == null || ticks < 1) return;
		activeSessionId = sessionId;
		activeGenerationId = generation;
		activeMode = mode;
		durationTicks = ticks;
		remainingTicks = ticks;
		chooserGenerationId = 0L;
		lastClearedSessionId = 0L;
		lastClearedGenerationId = 0L;
		lastClearedMode = null;
		consumedResultSessionId = 0L;
		clearMetrics();
		clearHint();
		clearGuide();
		result = null;
		leaderboards = null;
		leaderboardStage = null;
		modalKind = ModalKind.NONE;
		suppressNextResultBox = false;
		suppressNextLeaderboardBox = false;
	}

	private void handleHud(String[] parts) {
		if (parts.length != 14) return;
		long sessionId = positiveLong(parts[1]);
		Mode mode = Mode.fromWire(parts[2]);
		if (sessionId != activeSessionId || mode == null || mode != activeMode) return;
		int nextRemaining = boundedNonNegativeInt(parts[3], 0, Math.max(10000, durationTicks));
		int nextCatches = nonNegativeInt(parts[4]);
		int nextCurrent = nonNegativeInt(parts[5]);
		int nextBest = nonNegativeInt(parts[6]);
		int nextExact = nonNegativeInt(parts[7]);
		int nextTrailSamples = nonNegativeInt(parts[8]);
		int nextReactionTotal = nonNegativeInt(parts[9]);
		int nextReactionSamples = nonNegativeInt(parts[10]);
		int hintFlag = flag(parts[11]);
		int nextHintX = signedCoordinate(parts[12]);
		int nextHintY = signedCoordinate(parts[13]);
		if (nextRemaining < 0 || nextCatches < 0 || nextCurrent < 0 || nextBest < 0
				|| nextExact < 0 || nextTrailSamples < 0 || nextReactionTotal < 0
				|| nextReactionSamples < 0 || hintFlag < 0 || nextExact > nextTrailSamples
				|| nextCurrent > nextBest || nextBest > nextCatches
				|| nextReactionSamples > nextCatches) return;
		if (hintFlag == 1) {
			if (mode != Mode.TRAINER || nextHintX < 0 || nextHintY < 0) return;
		} else if (nextHintX != -1 || nextHintY != -1) {
			return;
		}
		remainingTicks = Math.min(durationTicks, nextRemaining);
		catches = nextCatches;
		currentStreak = nextCurrent;
		bestStreak = nextBest;
		exactTrailTicks = nextExact;
		trailSamples = nextTrailSamples;
		totalReactionTicks = nextReactionTotal;
		reactionSamples = nextReactionSamples;
		hintActive = hintFlag == 1;
		hintWorldX = nextHintX;
		hintWorldY = nextHintY;
	}

	private void handleGuide(String[] parts) {
		if (parts.length != 5) return;
		long sessionId = positiveLong(parts[1]);
		GuideState nextState = GuideState.fromWire(parts[2]);
		if (sessionId != activeSessionId || activeMode != Mode.TRAINER || nextState == null) return;

		int nextWorldX = -1;
		int nextWorldY = -1;
		if (nextState == GuideState.DESTINATION) {
			nextWorldX = signedCoordinate(parts[3]);
			nextWorldY = signedCoordinate(parts[4]);
			if (nextWorldX < 0 || nextWorldY < 0) return;
		} else if (!"-1".equals(parts[3]) || !"-1".equals(parts[4])) {
			// signedCoordinate deliberately maps malformed input and the unset
			// sentinel to -1, so non-destination records validate the literal.
			return;
		}

		guideSeen = true;
		guideState = nextState;
		guideWorldX = nextWorldX;
		guideWorldY = nextWorldY;
		markerProjected = false;
	}

	private void handleClear(String[] parts) {
		if (parts.length != 3) return;
		long sessionId = nonNegativeLong(parts[1]);
		long generation = positiveLong(parts[2]);
		if (sessionId < 0L || generation <= 0L) return;
		if (sessionId == activeSessionId && generation == activeGenerationId) {
			lastClearedSessionId = activeSessionId;
			lastClearedGenerationId = activeGenerationId;
			lastClearedMode = activeMode;
			clearActiveSession(false);
			chooserGenerationId = 0L;
			return;
		}
		if (sessionId == lastClearedSessionId && generation == lastClearedGenerationId) return;
		if (sessionId == 0L && generation == chooserGenerationId) {
			chooserGenerationId = 0L;
			return;
		}
		if (generation > latestGenerationId) {
			latestGenerationId = generation;
			chooserGenerationId = 0L;
			clearActiveSession(false);
			lastClearedSessionId = sessionId;
			lastClearedGenerationId = generation;
			lastClearedMode = null;
			consumedResultSessionId = 0L;
			modalKind = ModalKind.NONE;
		}
	}

	private void handleResult(String[] parts) {
		if (parts.length != 14) return;
		long sessionId = positiveLong(parts[1]);
		Mode mode = Mode.fromWire(parts[2]);
		boolean expected = sessionId == lastClearedSessionId && mode == lastClearedMode;
		if (!expected && sessionId == activeSessionId && mode == activeMode) expected = true;
		if (!expected || sessionId == consumedResultSessionId || mode == null) return;
		int completed = flag(parts[3]);
		int nextCatches = nonNegativeInt(parts[4]);
		int nextCurrent = nonNegativeInt(parts[5]);
		int nextBest = nonNegativeInt(parts[6]);
		int nextExact = nonNegativeInt(parts[7]);
		int nextTrailSamples = nonNegativeInt(parts[8]);
		int nextReactionTotal = nonNegativeInt(parts[9]);
		int nextReactionSamples = nonNegativeInt(parts[10]);
		int rank = rankOrUnranked(parts[11]);
		int personalBest = scoreOrUnset(parts[12]);
		int newBest = flag(parts[13]);
		if (completed < 0 || nextCatches < 0 || nextCurrent < 0 || nextBest < 0
				|| nextExact < 0 || nextTrailSamples < 0 || nextReactionTotal < 0
				|| nextReactionSamples < 0 || rank < -1 || personalBest < -1 || newBest < 0
				|| nextExact > nextTrailSamples || nextCurrent > nextBest || nextBest > nextCatches
				|| nextReactionSamples > nextCatches || ((rank == -1) != (personalBest == -1))) return;
		if (!mode.ranked && (rank != -1 || personalBest != -1 || newBest != 0)) return;
		if (newBest == 1 && (completed != 1 || personalBest != nextCatches)) return;
		result = new Result(sessionId, mode, completed == 1, nextCatches, nextCurrent,
			nextBest, nextExact, nextTrailSamples, nextReactionTotal, nextReactionSamples,
			rank, personalBest, newBest == 1);
		consumedResultSessionId = sessionId;
		clearActiveSession(false);
		modalKind = Config.C_CUSTOM_UI ? ModalKind.RESULTS : ModalKind.NONE;
		leaderboardReturnsToResult = false;
		suppressNextResultBox = Config.C_CUSTOM_UI;
	}

	private void handleLeaderboardBegin(String[] parts) {
		leaderboardStage = null;
		if (parts.length != 9) return;
		long transferId = positiveLong(parts[1]);
		long generation = positiveLong(parts[2]);
		int mediumCount = boundedNonNegativeInt(parts[3], 0, MAX_LEADERBOARD_ROWS);
		int hardCount = boundedNonNegativeInt(parts[4], 0, MAX_LEADERBOARD_ROWS);
		int mediumRank = rankOrUnranked(parts[5]);
		int mediumBest = scoreOrUnset(parts[6]);
		int hardRank = rankOrUnranked(parts[7]);
		int hardBest = scoreOrUnset(parts[8]);
		if (transferId <= 0L || generation <= latestGenerationId
				|| mediumCount < 0 || hardCount < 0 || mediumRank < -1 || mediumBest < -1
				|| hardRank < -1 || hardBest < -1
				|| ((mediumRank == -1) != (mediumBest == -1))
				|| ((hardRank == -1) != (hardBest == -1))) return;
		latestGenerationId = generation;
		leaderboardStage = new LeaderboardStage(transferId, generation, mediumCount,
			hardCount, mediumRank, mediumBest, hardRank, hardBest);
	}

	private void handleLeaderboardRow(String[] parts) {
		if (parts.length != 8 || leaderboardStage == null) {
			leaderboardStage = null;
			return;
		}
		long transferId = positiveLong(parts[1]);
		Mode mode = Mode.fromWire(parts[2]);
		int rank = boundedNonNegativeInt(parts[3], 1, MAX_LEADERBOARD_ROWS);
		long usernameHash = positiveLong(parts[4]);
		String username = parts[5];
		int nextCatches = nonNegativeInt(parts[6]);
		int selfFlag = flag(parts[7]);
		if (transferId != leaderboardStage.transferId || (mode != Mode.CLASSIC && mode != Mode.HARD)
				|| rank < 1 || usernameHash <= 0L || !validProtocolName(username)
				|| nextCatches < 0 || selfFlag < 0) {
			leaderboardStage = null;
			return;
		}
		LeaderboardEntry[] rows = mode == Mode.CLASSIC ? leaderboardStage.medium : leaderboardStage.hard;
		int personalRank = mode == Mode.CLASSIC ? leaderboardStage.mediumRank : leaderboardStage.hardRank;
		int personalBest = mode == Mode.CLASSIC ? leaderboardStage.mediumBest : leaderboardStage.hardBest;
		// Row ranks are transfer ordinals; the personal competition rank can differ on ties.
		if (rank > rows.length || rows[rank - 1] != null || containsHash(rows, usernameHash)
				|| (selfFlag == 1 && (personalRank < 1 || personalBest != nextCatches))) {
			leaderboardStage = null;
			return;
		}
		rows[rank - 1] = new LeaderboardEntry(rank, usernameHash, username, nextCatches, selfFlag == 1);
	}

	private void handleLeaderboardEnd(String[] parts) {
		if (parts.length != 2 || leaderboardStage == null) {
			leaderboardStage = null;
			return;
		}
		long transferId = positiveLong(parts[1]);
		if (transferId != leaderboardStage.transferId || !complete(leaderboardStage.medium)
				|| !complete(leaderboardStage.hard)) {
			leaderboardStage = null;
			return;
		}
		LeaderboardStage completed = leaderboardStage;
		leaderboardStage = null;
		leaderboards = new Leaderboards(completed.generationId,
			new LeaderboardBoard(copy(completed.medium), completed.mediumRank, completed.mediumBest),
			new LeaderboardBoard(copy(completed.hard), completed.hardRank, completed.hardBest));
		modalKind = Config.C_CUSTOM_UI ? ModalKind.LEADERBOARD : ModalKind.NONE;
		leaderboardReturnsToResult = false;
		suppressNextLeaderboardBox = Config.C_CUSTOM_UI;
		if (selectedBoard != Mode.CLASSIC && selectedBoard != Mode.HARD) selectedBoard = Mode.CLASSIC;
	}

	public boolean matchesChooser(int optionCount, String[] options) {
		return Config.C_CUSTOM_UI && chooserGenerationId > 0L && optionCount == 3
			&& options != null && options.length >= 3
			&& EASY_OPTION.equals(options[0]) && MEDIUM_OPTION.equals(options[1])
			&& HARD_OPTION.equals(options[2]);
	}

	/** Draws the card chooser and returns an ordinary packet-116 option index. */
	public int renderChooser() {
		layoutChooser();
		GraphicsController g = mc.getSurface();
		int mouseX = mc.getMouseX();
		int mouseY = mc.getMouseY();
		g.drawBoxAlpha(0, 0, mc.getGameWidth(), mc.getGameHeight(), UiSkin.VOID_SCRIM, 138);
		InterfaceChrome.window(g, chooserPanel[0], chooserPanel[1], chooserPanel[2], chooserPanel[3],
			"PK CATCHING SIMULATOR", controlHit(chooserClose, mouseX, mouseY));
		g.drawColoredStringCentered(chooserPanel[0] + chooserPanel[2] / 2,
			"CHOOSE YOUR FIVE MINUTE DRILL", UiSkin.GOLD_HEADER, 0, UiSkin.FONT_SMALL,
			chooserPanel[1] + 40);
		for (int i = 0; i < chooserCards.length; i++) drawChooserCard(g, i, mouseX, mouseY);
		if (mc.getMouseClick() != 1) return NO_CHOOSER_ACTION;
		if (controlHit(chooserClose, mouseX, mouseY)) return -1;
		for (int i = 0; i < chooserCards.length; i++) {
			if (controlHit(chooserCards[i], mouseX, mouseY)) return i;
		}
		return NO_CHOOSER_ACTION;
	}

	private void drawChooserCard(GraphicsController g, int index, int mouseX, int mouseY) {
		Mode mode = index == 0 ? Mode.TRAINER : index == 1 ? Mode.CLASSIC : Mode.HARD;
		int[] card = chooserCards[index];
		boolean hover = controlHit(card, mouseX, mouseY);
		UiSkin.glassPanel(g, card[0], card[1], card[2], card[3],
			hover ? UiSkin.A_GLASS_TEXT : UiSkin.A_GLASS);
		int accent = mode == Mode.TRAINER ? UiSkin.GOOD : mode == Mode.HARD ? UiSkin.BAD : UiSkin.GOLD_HOT;
		g.drawBoxAlpha(card[0] + 2, card[1] + 2, 3, card[3] - 4, accent, UiSkin.A_BUTTON);
		g.drawString(mode.displayName, card[0] + 12, card[1] + 20,
			hover ? UiSkin.GOLD_HOT : UiSkin.GOLD_TITLE, UiSkin.FONT_TITLE);
		String number = Integer.toString(index + 1);
		g.drawString(number, card[0] + card[2] - g.stringWidth(UiSkin.FONT_BODY, number) - 9,
			card[1] + 19, UiSkin.TEXT_DIM, UiSkin.FONT_BODY);
		int badgeWidth = Math.min(card[2] - 24, g.stringWidth(UiSkin.FONT_SMALL, mode.badge) + 14);
		g.drawBoxAlpha(card[0] + 12, card[1] + 29, badgeWidth, 17,
			mode.ranked ? UiSkin.PURPLE_SELECT : UiSkin.VOID_BOX, UiSkin.A_BUTTON);
		g.drawBorder(card[0] + 12, card[1] + 29, badgeWidth, 17, accent);
		g.drawString(mode.badge, card[0] + 19, card[1] + 41, accent, UiSkin.FONT_SMALL);
		String[] lines;
		if (mode == Mode.TRAINER) {
			lines = new String[] {"Stable intercept guidance", "Paced two-tick routes", "Practice without ranking"};
		} else if (mode == Mode.CLASSIC) {
			lines = new String[] {"The original mixed drill", "Reactive jukes and routes", "Medium leaderboard"};
		} else {
			lines = new String[] {"Long diagonals and cutbacks", "Obstacle and crazy runs", "Hard leaderboard"};
		}
		if (card[3] < 120) {
			String compact = mode == Mode.TRAINER ? "Perfect-click guidance - unranked"
				: mode == Mode.CLASSIC ? "Classic drill - ranked" : "Crazy routes - ranked";
			g.drawString(fit(g, compact, card[2] - badgeWidth - 42, UiSkin.FONT_SMALL),
				card[0] + badgeWidth + 22, card[1] + 41, UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
			return;
		}
		int textY = card[1] + 63;
		for (String line : lines) {
			g.drawString(fit(g, line, card[2] - 24, UiSkin.FONT_SMALL), card[0] + 12,
				textY, UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
			textY += 16;
		}
		if (card[3] >= 145) {
			g.drawLineHoriz(card[0] + 10, card[1] + card[3] - 30, card[2] - 20, UiSkin.VOID_LINE);
			g.drawColoredStringCentered(card[0] + card[2] / 2,
				mode == Mode.TRAINER ? "LEARN THE PERFECT TRAIL" : mode == Mode.CLASSIC ? "PRESERVE THE CLASSIC" : "EXPECT NO MERCY",
				accent, 0, UiSkin.FONT_SMALL, card[1] + card[3] - 12);
		}
	}

	public void renderHud() {
		if (!Config.C_CUSTOM_UI || activeSessionId <= 0L || activeMode == null) return;
		layoutHud();
		GraphicsController g = mc.getSurface();
		boolean attackNow = isAttackNow();
		UiSkin.glassPanel(g, hudRect[0], hudRect[1], hudRect[2], hudRect[3], UiSkin.A_GLASS_TEXT);
		int accent = activeMode == Mode.TRAINER ? UiSkin.GOOD : activeMode == Mode.HARD ? UiSkin.BAD : UiSkin.GOLD_HOT;
		g.drawBoxAlpha(hudRect[0] + 2, hudRect[1] + 2, 3, hudRect[3] - 4, accent, UiSkin.A_BUTTON);
		g.drawString(activeMode.displayName, hudRect[0] + 11, hudRect[1] + 16, accent, UiSkin.FONT_BODY);
		String time = formatTime(remainingSeconds());
		g.drawString(time, hudRect[0] + hudRect[2] - g.stringWidth(UiSkin.FONT_BODY, time) - 9,
			hudRect[1] + 16, remainingSeconds() <= 30 ? UiSkin.BAD : UiSkin.GOLD_TITLE, UiSkin.FONT_BODY);
		g.drawLineHoriz(hudRect[0] + 9, hudRect[1] + 22, hudRect[2] - 18, UiSkin.VOID_LINE);
		if (attackNow) {
			int promptX = hudRect[0] + 8;
			int promptY = hudRect[1] + 25;
			int promptW = hudRect[2] - 16;
			g.drawBoxAlpha(promptX, promptY, promptW, 17, UiSkin.PURPLE_SELECT, UiSkin.A_GLASS);
			g.drawBorder(promptX, promptY, promptW, 17, UiSkin.PURPLE_FOCUS);
			g.drawColoredStringCentered(hudRect[0] + hudRect[2] / 2, "ATTACK NOW",
				UiSkin.GOOD, 0, UiSkin.FONT_TITLE, hudRect[1] + 39);
			return;
		}
		g.drawString("Catches " + catches + "   Streak " + currentStreak + " / " + bestStreak,
			hudRect[0] + 11, hudRect[1] + 38, UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
		String quality = percentage(exactTrailTicks, trailSamples) + " trail   "
			+ average(totalReactionTicks, reactionSamples) + "t react";
		g.drawString(quality, hudRect[0] + hudRect[2] - g.stringWidth(UiSkin.FONT_SMALL, quality) - 9,
			hudRect[1] + 38, UiSkin.TEXT_LABEL, UiSkin.FONT_SMALL);
	}

	/** Draw immediately after Scene.endScene so the tile follows the 3D floor. */
	public void renderSceneMarker() {
		markerProjected = false;
		if (!isGuideDestinationActive()) return;
		int markerWorldX = getGuideWorldX();
		int markerWorldY = getGuideWorldY();
		if (markerWorldX < 0 || markerWorldY < 0) return;
		World world = mc.getWorld();
		Scene scene = mc.getScene();
		ORSCharacter player = mc.getLocalPlayer();
		if (world == null || scene == null || player == null) return;
		int currentWorldY = mc.getLocalPlayerZ() + mc.getMidRegionBaseZ();
		if (markerWorldY / RSC_FLOOR_HEIGHT != currentWorldY / RSC_FLOOR_HEIGHT) return;
		int localX = markerWorldX - mc.getMidRegionBaseX();
		int localY = markerWorldY - mc.getMidRegionBaseZ();
		if (localX < 0 || localY < 0 || localX >= 95 || localY >= 95) return;
		int tile = mc.getTileSize();
		int x0 = localX * tile;
		int y0 = localY * tile;
		int x1 = x0 + tile;
		int y1 = y0 + tile;
		if (!projectMarker(scene, world, 0, x0, y0) || !projectMarker(scene, world, 1, x1, y0)
				|| !projectMarker(scene, world, 2, x1, y1) || !projectMarker(scene, world, 3, x0, y1)) return;
		GraphicsController g = mc.getSurface();
		int pulse = (mc.getFrameCounter() / 2) & 15;
		int edgeAlpha = 176 + pulse;
		int[] p0 = markerProjection[0];
		int[] p1 = markerProjection[1];
		int[] p2 = markerProjection[2];
		int[] p3 = markerProjection[3];
		g.drawQuadrilateralAlpha(p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1],
			UiSkin.PURPLE_BRIGHT, 52);
		g.drawLineAlpha(p0[0], p0[1], p1[0], p1[1], UiSkin.PURPLE_FOCUS, edgeAlpha);
		g.drawLineAlpha(p1[0], p1[1], p2[0], p2[1], UiSkin.PURPLE_FOCUS, edgeAlpha);
		g.drawLineAlpha(p2[0], p2[1], p3[0], p3[1], UiSkin.PURPLE_FOCUS, edgeAlpha);
		g.drawLineAlpha(p3[0], p3[1], p0[0], p0[1], UiSkin.PURPLE_FOCUS, edgeAlpha);
		int centerX = x0 + tile / 2;
		int centerY = y0 + tile / 2;
		int elevation = -world.getElevation(centerX, centerY) - 14;
		if (scene.projectToScreen(centerX, elevation, centerY, markerCenterProjection)) {
			int cx = markerCenterProjection[0];
			int cy = markerCenterProjection[1];
			int radius = 7 + (pulse / 6);
			g.drawLineAlpha(cx - radius - 3, cy, cx + radius + 3, cy, UiSkin.PURPLE_FOCUS, edgeAlpha);
			g.drawLineAlpha(cx, cy - radius - 3, cx, cy + radius + 3, UiSkin.PURPLE_FOCUS, edgeAlpha);
			g.drawCircle(cx, cy, 3, UiSkin.GOLD_HOT, edgeAlpha, 0);
		}
		markerProjected = true;
	}

	private boolean projectMarker(Scene scene, World world, int index, int sceneX, int sceneY) {
		return scene.projectToScreen(sceneX, -world.getElevation(sceneX, sceneY) - 3,
			sceneY, markerProjection[index]);
	}

	public boolean isModalVisible() {
		if (!Config.C_CUSTOM_UI) {
			modalKind = ModalKind.NONE;
			leaderboardReturnsToResult = false;
			return false;
		}
		return modalKind != ModalKind.NONE;
	}

	public String getModalName() {
		if (!isModalVisible()) return "";
		return modalKind == ModalKind.RESULTS ? "pkCatchingResults" : "pkCatchingLeaderboard";
	}

	public void renderModal() {
		if (!isModalVisible()) return;
		layoutModal();
		GraphicsController g = mc.getSurface();
		int mouseX = mc.getMouseX();
		int mouseY = mc.getMouseY();
		g.drawBoxAlpha(0, 0, mc.getGameWidth(), mc.getGameHeight(), UiSkin.VOID_SCRIM, 138);
		InterfaceChrome.window(g, modalRect[0], modalRect[1], modalRect[2], modalRect[3],
			modalKind == ModalKind.RESULTS ? "CATCHING DRILL RESULTS" : "PK CATCHING HIGHSCORES",
			controlHit(modalClose, mouseX, mouseY));
		if (modalKind == ModalKind.RESULTS) drawResults(g, mouseX, mouseY);
		else drawLeaderboard(g, mouseX, mouseY);
	}

	public boolean handleClick(int x, int y, int button) {
		if (!isModalVisible()) return false;
		layoutModal();
		if (button == 1) {
			if (controlHit(modalClose, x, y)) {
				closeModal();
			} else if (modalKind == ModalKind.RESULTS) {
				if (leaderboards != null && controlHit(modalPrimary, x, y)) {
					modalKind = ModalKind.LEADERBOARD;
					leaderboardReturnsToResult = true;
				} else if (controlHit(modalSecondary, x, y)) {
					closeModal();
				}
			} else {
				if (controlHit(mediumTab, x, y)) selectedBoard = Mode.CLASSIC;
				else if (controlHit(hardTab, x, y)) selectedBoard = Mode.HARD;
				else if (controlHit(modalPrimary, x, y)) {
					if (leaderboardReturnsToResult && result != null) modalKind = ModalKind.RESULTS;
					else closeModal();
				}
			}
		}
		mc.setMouseClick(0);
		mc.setMouseButtonDown(0);
		return true;
	}

	public void closeModal() {
		modalKind = ModalKind.NONE;
		leaderboardReturnsToResult = false;
		mc.setMouseClick(0);
		mc.setMouseButtonDown(0);
	}

	private void drawResults(GraphicsController g, int mouseX, int mouseY) {
		if (result == null) return;
		int x = modalRect[0];
		int y = modalRect[1];
		int w = modalRect[2];
		boolean compact = modalRect[3] < 280;
		int accent = result.mode == Mode.TRAINER ? UiSkin.GOOD : result.mode == Mode.HARD ? UiSkin.BAD : UiSkin.GOLD_HOT;
		g.drawColoredStringCentered(x + w / 2, result.mode.displayName, accent, 0,
			UiSkin.FONT_DISPLAY, y + (compact ? 42 : 51));
		String status = result.mode.ranked
			? (result.completed ? "FULL RANKED RUN" : "RUN ENDED EARLY - SCORE NOT RECORDED")
			: "GUIDED TRAINING - UNRANKED";
		g.drawColoredStringCentered(x + w / 2, status,
			result.completed || !result.mode.ranked ? UiSkin.TEXT_LABEL : UiSkin.BAD,
			0, UiSkin.FONT_SMALL, y + (compact ? 56 : 69));

		int gap = compact ? 3 : 6;
		int cardW = (w - 24 - gap) / 2;
		int cardH = compact ? 31 : 43;
		int left = x + 9;
		int top = y + (compact ? 61 : 80);
		drawMetricCard(g, left, top, cardW, cardH, "CATCHES", Integer.toString(result.catches), accent);
		drawMetricCard(g, left + cardW + gap, top, cardW, cardH, "BEST STREAK",
			Integer.toString(result.bestStreak), UiSkin.GOLD_TITLE);
		drawMetricCard(g, left, top + cardH + gap, cardW, cardH, "TRAIL ACCURACY",
			percentage(result.exactTrailTicks, result.trailSamples), UiSkin.GOOD);
		drawMetricCard(g, left + cardW + gap, top + cardH + gap, cardW, cardH, "AVG REACTION",
			average(result.totalReactionTicks, result.reactionSamples) + " ticks", UiSkin.PURPLE_FOCUS);

		int summaryY = top + (cardH + gap) * 2 + (compact ? 4 : 10);
		String summary;
		if (!result.mode.ranked) summary = "Trainer runs never change the ranked boards.";
		else if (result.rank < 0) summary = "Personal best: none yet";
		else summary = "Personal best " + result.personalBest + " catches   /   Rank #" + result.rank;
		int summaryFont = compact ? UiSkin.FONT_SMALL : UiSkin.FONT_BODY;
		g.drawColoredStringCentered(x + w / 2, fit(g, summary, w - 24, summaryFont),
			result.newBest ? UiSkin.GOLD_HOT : UiSkin.TEXT_BODY, 0, summaryFont, summaryY);
		if (result.newBest) {
			g.drawColoredStringCentered(x + w / 2, "NEW PERSONAL BEST", UiSkin.GOLD_HOT,
				0, UiSkin.FONT_SMALL, summaryY + (compact ? 14 : 18));
		}

		if (leaderboards != null) {
			InterfaceChrome.button(g, modalPrimary[0], modalPrimary[1], modalPrimary[2], modalPrimary[3],
				"View Highscores", UiSkin.FONT_BODY, false, false, mouseX, mouseY);
		}
		InterfaceChrome.button(g, modalSecondary[0], modalSecondary[1], modalSecondary[2], modalSecondary[3],
			"Close", UiSkin.FONT_BODY, false, false, mouseX, mouseY);
	}

	private void drawMetricCard(GraphicsController g, int x, int y, int w, int h,
			String label, String value, int accent) {
		UiSkin.glassPanel(g, x, y, w, h, UiSkin.A_GLASS);
		boolean compact = h < 40;
		g.drawString(label, x + 7, y + (compact ? 10 : 13), UiSkin.TEXT_DIM, UiSkin.FONT_SMALL);
		g.drawString(value, x + 7, y + (compact ? h - 5 : 33), accent, UiSkin.FONT_TITLE);
	}

	private void drawLeaderboard(GraphicsController g, int mouseX, int mouseY) {
		if (leaderboards == null) return;
		InterfaceChrome.button(g, mediumTab[0], mediumTab[1], mediumTab[2], mediumTab[3],
			"MEDIUM", UiSkin.FONT_BODY, selectedBoard == Mode.CLASSIC, false, mouseX, mouseY);
		InterfaceChrome.button(g, hardTab[0], hardTab[1], hardTab[2], hardTab[3],
			"HARD", UiSkin.FONT_BODY, selectedBoard == Mode.HARD, false, mouseX, mouseY);
		LeaderboardBoard board = selectedBoard == Mode.HARD ? leaderboards.hard : leaderboards.medium;
		int bodyX = modalRect[0] + 9;
		int bodyY = mediumTab[1] + mediumTab[3] + 6;
		int bodyW = modalRect[2] - 18;
		int footerTop = modalPrimary[1]
			- (nativeAndroid() ? (modalRect[3] < 280 ? 18 : 24) : 35);
		int bodyH = Math.max(1, footerTop - bodyY);
		UiSkin.glassPanel(g, bodyX, bodyY, bodyW, bodyH, UiSkin.A_GLASS);
		if (board.rows.length == 0) {
			g.drawColoredStringCentered(bodyX + bodyW / 2, "No completed five minute runs yet.",
				UiSkin.TEXT_DIM, 0, UiSkin.FONT_BODY, bodyY + 32);
		} else {
			int rowH = Math.max(12, (bodyH - 4) / MAX_LEADERBOARD_ROWS);
			for (int i = 0; i < board.rows.length; i++) {
				LeaderboardEntry entry = board.rows[i];
				int rowY = bodyY + 2 + i * rowH;
				if (entry.self) UiSkin.listRowFill(g, bodyX + 2, rowY, bodyW - 4, rowH, false, true);
				g.drawString("#" + entry.rank, bodyX + 8, rowY + rowH - 2,
					entry.rank <= 3 ? UiSkin.GOLD_HOT : UiSkin.TEXT_LABEL, UiSkin.FONT_SMALL);
				g.drawString(fit(g, entry.username, bodyW - 112, UiSkin.FONT_SMALL), bodyX + 40,
					rowY + rowH - 2, entry.self ? UiSkin.GOLD_TITLE : UiSkin.TEXT_BODY, UiSkin.FONT_SMALL);
				String score = entry.catches + (entry.catches == 1 ? " catch" : " catches");
				g.drawString(score, bodyX + bodyW - g.stringWidth(UiSkin.FONT_SMALL, score) - 8,
					rowY + rowH - 2, entry.self ? UiSkin.GOOD : UiSkin.TEXT_LABEL, UiSkin.FONT_SMALL);
				if (i + 1 < board.rows.length) g.drawLineHoriz(bodyX + 6, rowY + rowH - 1, bodyW - 12, UiSkin.VOID_LINE);
			}
		}
		String personal = board.personalRank < 0 ? "YOU ARE UNRANKED"
			: "YOUR BEST: " + board.personalBest + " CATCHES   /   RANK #" + board.personalRank;
		g.drawColoredStringCentered(modalRect[0] + modalRect[2] / 2, personal,
			board.personalRank < 0 ? UiSkin.TEXT_DIM : UiSkin.GOLD_HEADER, 0, UiSkin.FONT_SMALL,
			modalPrimary[1] - 12);
		InterfaceChrome.button(g, modalPrimary[0], modalPrimary[1], modalPrimary[2], modalPrimary[3],
			leaderboardReturnsToResult && result != null ? "Back to Results" : "Close",
			UiSkin.FONT_BODY, false, false, mouseX, mouseY);
	}

	public boolean shouldSuppressLegacyBox(String message) {
		if (message == null) return false;
		if (message.startsWith(LEGACY_HIGHSCORES_PREFIX)) {
			boolean suppress = suppressNextLeaderboardBox && leaderboards != null && Config.C_CUSTOM_UI;
			suppressNextLeaderboardBox = false;
			return suppress;
		}
		if (message.startsWith(LEGACY_RESULT_PREFIX)) {
			boolean suppress = suppressNextResultBox && result != null && Config.C_CUSTOM_UI;
			suppressNextResultBox = false;
			return suppress;
		}
		return false;
	}

	public void reset() {
		latestGenerationId = 0L;
		chooserGenerationId = 0L;
		clearActiveSession(true);
		lastClearedSessionId = 0L;
		lastClearedGenerationId = 0L;
		lastClearedMode = null;
		consumedResultSessionId = 0L;
		result = null;
		leaderboards = null;
		leaderboardStage = null;
		selectedBoard = Mode.CLASSIC;
		modalKind = ModalKind.NONE;
		leaderboardReturnsToResult = false;
		suppressNextResultBox = false;
		suppressNextLeaderboardBox = false;
	}

	private void clearActiveSession(boolean clearMetrics) {
		activeSessionId = 0L;
		activeGenerationId = 0L;
		activeMode = null;
		durationTicks = 0;
		remainingTicks = 0;
		clearHint();
		clearGuide();
		if (clearMetrics) clearMetrics();
	}

	private void clearHint() {
		hintActive = false;
		hintWorldX = -1;
		hintWorldY = -1;
		markerProjected = false;
	}

	private void clearGuide() {
		guideSeen = false;
		guideState = GuideState.HIDDEN;
		guideWorldX = -1;
		guideWorldY = -1;
		markerProjected = false;
	}

	private boolean isGuideDestinationActive() {
		if (activeSessionId <= 0L || activeMode != Mode.TRAINER) return false;
		return guideSeen ? guideState == GuideState.DESTINATION : hintActive;
	}

	private void clearMetrics() {
		catches = 0;
		currentStreak = 0;
		bestStreak = 0;
		exactTrailTicks = 0;
		trailSamples = 0;
		totalReactionTicks = 0;
		reactionSamples = 0;
	}

	private void layoutChooser() {
		int width = Math.min(640, Math.max(1, mc.getGameWidth() - 16));
		int height = Math.min(300, Math.max(1, mc.getGameHeight() - 16));
		chooserPanel[0] = UiAnchor.centeredDialogX(mc.getGameWidth(), width);
		chooserPanel[1] = UiAnchor.centeredCardY(mc.getGameHeight(), height, 8);
		chooserPanel[2] = width;
		chooserPanel[3] = height;
		setRect(chooserClose, InterfaceChrome.closeX(chooserPanel[0], width),
			InterfaceChrome.closeY(chooserPanel[1]), InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE);
		int contentX = chooserPanel[0] + 10;
		int contentY = chooserPanel[1] + 50;
		int contentW = width - 20;
		int contentH = height - 60;
		int gap = 7;
		if (width >= 560) {
			int cardW = (contentW - gap * 2) / 3;
			for (int i = 0; i < 3; i++) setRect(chooserCards[i], contentX + i * (cardW + gap),
				contentY, cardW, contentH);
		} else {
			int cardH = (contentH - gap * 2) / 3;
			for (int i = 0; i < 3; i++) setRect(chooserCards[i], contentX,
				contentY + i * (cardH + gap), contentW, cardH);
		}
	}

	private void layoutHud() {
		int width = Math.min(430, Math.max(1, mc.getGameWidth() - 16));
		setRect(hudRect, UiAnchor.centerX(mc.getGameWidth(), width),
			mc.getPkCatchingHudTopSafeY(), width, 46);
	}

	private void layoutModal() {
		int width = UiSkin.modalWidth(mc.getGameWidth(), UiSkin.MODAL_W_GRID);
		int height = Math.min(modalKind == ModalKind.RESULTS ? 292 : 318, mc.getGameHeight() - 16);
		int x = UiAnchor.centeredDialogX(mc.getGameWidth(), width);
		int y = UiAnchor.centeredCardY(mc.getGameHeight(), height, 8);
		setRect(modalRect, x, y, width, height);
		setRect(modalClose, InterfaceChrome.closeX(x, width), InterfaceChrome.closeY(y),
			InterfaceChrome.CLOSE_SIZE, InterfaceChrome.CLOSE_SIZE);
		int buttonH = nativeAndroid()
			? (height < 280 ? 40 : height < 300 ? 44 : 48)
			: 28;
		int buttonY = y + height - buttonH - 8;
		if (modalKind == ModalKind.RESULTS) {
			int gap = 8;
			int buttonW = leaderboards == null ? Math.min(180, width - 24) : (width - 30 - gap) / 2;
			if (leaderboards == null) {
				setRect(modalPrimary, 0, 0, 0, 0);
				setRect(modalSecondary, x + (width - buttonW) / 2, buttonY, buttonW, buttonH);
			} else {
				setRect(modalPrimary, x + 11, buttonY, buttonW, buttonH);
				setRect(modalSecondary, x + 11 + buttonW + gap, buttonY, buttonW, buttonH);
			}
		} else {
			int tabH = nativeAndroid()
				? (height < 280 ? 32 : height < 300 ? 44 : 48)
				: 27;
			int tabW = (width - 24) / 2;
			setRect(mediumTab, x + 9, y + InterfaceChrome.TITLE_H + 6, tabW, tabH);
			setRect(hardTab, x + 9 + tabW, y + InterfaceChrome.TITLE_H + 6,
				width - 18 - tabW, tabH);
			int buttonW = Math.min(190, width - 24);
			setRect(modalPrimary, x + (width - buttonW) / 2, buttonY, buttonW, buttonH);
			setRect(modalSecondary, 0, 0, 0, 0);
		}
	}

	private boolean controlHit(int[] rect, int x, int y) {
		if (rect[2] <= 0 || rect[3] <= 0) return false;
		if (!nativeAndroid() || rect[2] >= 48 && rect[3] >= 48) {
			return UiSkin.hit(rect[0], rect[1], rect[2], rect[3], x, y);
		}
		int width = Math.max(48, rect[2]);
		int height = Math.max(48, rect[3]);
		int hitX = rect[0] + rect[2] / 2 - width / 2;
		int hitY = rect[1] + rect[3] / 2 - height / 2;
		return UiSkin.hit(hitX, hitY, width, height, x, y);
	}

	private static boolean nativeAndroid() {
		return Config.isAndroid() && !Config.isWeb();
	}

	private int remainingSeconds() {
		if (durationTicks <= 0 || remainingTicks <= 0) return 0;
		return (int)(((long)remainingTicks * SESSION_SECONDS + durationTicks - 1L) / durationTicks);
	}

	private static String formatTime(int seconds) {
		int minutes = Math.max(0, seconds) / 60;
		int remainder = Math.max(0, seconds) % 60;
		return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
	}

	private static String percentage(int numerator, int denominator) {
		return denominator <= 0 ? "0%" : ((long)numerator * 100L / denominator) + "%";
	}

	private static String average(int total, int samples) {
		if (samples <= 0) return "0.0";
		long tenths = ((long)total * 10L + samples / 2L) / samples;
		return (tenths / 10L) + "." + (tenths % 10L);
	}

	private static String fit(GraphicsController g, String text, int maxWidth, int font) {
		if (text == null) return "";
		if (g.stringWidth(font, text) <= maxWidth) return text;
		String suffix = "...";
		int length = text.length();
		while (length > 0 && g.stringWidth(font, text.substring(0, length) + suffix) > maxWidth) length--;
		return text.substring(0, length) + suffix;
	}

	private static void setRect(int[] rect, int x, int y, int width, int height) {
		rect[0] = x;
		rect[1] = y;
		rect[2] = Math.max(0, width);
		rect[3] = Math.max(0, height);
	}

	private static boolean containsHash(LeaderboardEntry[] rows, long hash) {
		for (LeaderboardEntry row : rows) {
			if (row != null && row.usernameHash == hash) return true;
		}
		return false;
	}

	private static boolean complete(LeaderboardEntry[] rows) {
		for (LeaderboardEntry row : rows) if (row == null) return false;
		return true;
	}

	private static LeaderboardEntry[] copy(LeaderboardEntry[] rows) {
		LeaderboardEntry[] copy = new LeaderboardEntry[rows.length];
		System.arraycopy(rows, 0, copy, 0, rows.length);
		return copy;
	}

	private static boolean validProtocolName(String value) {
		if (value == null || value.length() == 0 || value.length() > 24) return false;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '|' || c == '%' || c == '@' || c == '\n' || c == '\r' || Character.isISOControl(c)) return false;
		}
		return true;
	}

	private static int flag(String value) {
		return "0".equals(value) ? 0 : "1".equals(value) ? 1 : -1;
	}

	private static int rankOrUnranked(String value) {
		if ("-1".equals(value)) return -1;
		return boundedNonNegativeInt(value, 1, Integer.MAX_VALUE);
	}

	private static int scoreOrUnset(String value) {
		if ("-1".equals(value)) return -1;
		return nonNegativeInt(value);
	}

	private static int signedCoordinate(String value) {
		if ("-1".equals(value)) return -1;
		return boundedNonNegativeInt(value, 0, 65535);
	}

	private static int nonNegativeInt(String value) {
		return boundedNonNegativeInt(value, 0, Integer.MAX_VALUE);
	}

	private static int boundedNonNegativeInt(String value, int minimum, int maximum) {
		long parsed = unsignedLong(value, maximum);
		return parsed < minimum ? -1 : (int)parsed;
	}

	private static long positiveLong(String value) {
		long parsed = unsignedLong(value, Long.MAX_VALUE);
		return parsed > 0L ? parsed : -1L;
	}

	private static long nonNegativeLong(String value) {
		return unsignedLong(value, Long.MAX_VALUE);
	}

	private static long unsignedLong(String value, long maximum) {
		if (value == null || value.length() == 0 || value.length() > 1 && value.charAt(0) == '0') return -1L;
		long parsed = 0L;
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c < '0' || c > '9') return -1L;
			int digit = c - '0';
			if (parsed > (maximum - digit) / 10L) return -1L;
			parsed = parsed * 10L + digit;
		}
		return parsed;
	}

	// Production geometry/state accessors used by the later Workbench slice.
	public boolean isChooserArmed() { return chooserGenerationId > 0L; }
	public long getActiveSessionId() { return activeSessionId; }
	public String getModeName() { return activeMode == null ? "" : activeMode.wireName; }
	public int getRemainingTicks() { return remainingTicks; }
	public int getCatches() { return catches; }
	public int getCurrentStreak() { return currentStreak; }
	public int getBestStreak() { return bestStreak; }
	public String getTrailAccuracy() { return percentage(exactTrailTicks, trailSamples); }
	public String getReactionAverage() { return average(totalReactionTicks, reactionSamples); }
	public boolean isHintActive() { return hintActive; }
	public int getHintWorldX() { return hintWorldX; }
	public int getHintWorldY() { return hintWorldY; }
	public String getGuideStateName() {
		if (activeSessionId <= 0L || activeMode != Mode.TRAINER) return GuideState.HIDDEN.wireName;
		return guideSeen ? guideState.wireName
			: hintActive ? GuideState.DESTINATION.wireName : GuideState.HIDDEN.wireName;
	}
	public boolean isAttackNow() {
		return activeSessionId > 0L && activeMode == Mode.TRAINER
			&& guideSeen && guideState == GuideState.ATTACK;
	}
	public int getGuideWorldX() {
		return !isGuideDestinationActive() ? -1 : guideSeen ? guideWorldX : hintWorldX;
	}
	public int getGuideWorldY() {
		return !isGuideDestinationActive() ? -1 : guideSeen ? guideWorldY : hintWorldY;
	}
	public boolean isMarkerProjected() { return markerProjected; }
	public int getMarkerCenterX() { return markerCenterProjection[0]; }
	public int getMarkerCenterY() { return markerCenterProjection[1]; }
	public int[] getChooserPanelRect() { layoutChooser(); return copyRect(chooserPanel); }
	public int[] getChooserCloseRect() { layoutChooser(); return copyRect(chooserClose); }
	public int[] getChooserCardRect(int index) {
		layoutChooser();
		return index < 0 || index >= chooserCards.length ? new int[] {0, 0, 0, 0}
			: copyRect(chooserCards[index]);
	}
	public int[] getHudRect() { layoutHud(); return copyRect(hudRect); }
	public int[] getModalRect() { layoutModal(); return copyRect(modalRect); }
	public int[] getModalCloseRect() { layoutModal(); return copyRect(modalClose); }
	public int[] getModalPrimaryRect() { layoutModal(); return copyRect(modalPrimary); }
	public int[] getModalSecondaryRect() { layoutModal(); return copyRect(modalSecondary); }
	public int[] getMediumTabRect() { layoutModal(); return copyRect(mediumTab); }
	public int[] getHardTabRect() { layoutModal(); return copyRect(hardTab); }
	public boolean isResultCompleted() { return result != null && result.completed; }
	public String getResultModeName() { return result == null ? "" : result.mode.wireName; }
	public int getResultCatches() { return result == null ? 0 : result.catches; }
	public int getResultBestStreak() { return result == null ? 0 : result.bestStreak; }
	public String getResultTrailAccuracy() {
		return result == null ? "0%" : percentage(result.exactTrailTicks, result.trailSamples);
	}
	public String getResultReactionAverage() {
		return result == null ? "0.0" : average(result.totalReactionTicks, result.reactionSamples);
	}
	public int getResultRank() { return result == null ? -1 : result.rank; }
	public int getResultPersonalBest() { return result == null ? -1 : result.personalBest; }
	public boolean isResultNewBest() { return result != null && result.newBest; }
	public String getSelectedBoardName() { return selectedBoard.wireName; }
	public int getLeaderboardRowCount(String mode) {
		LeaderboardBoard board = board(mode);
		return board == null ? 0 : board.rows.length;
	}
	public LeaderboardEntry getLeaderboardRow(String mode, int index) {
		LeaderboardBoard board = board(mode);
		return board == null || index < 0 || index >= board.rows.length ? null : board.rows[index];
	}
	public int getPersonalRank(String mode) { LeaderboardBoard board = board(mode); return board == null ? -1 : board.personalRank; }
	public int getPersonalBest(String mode) { LeaderboardBoard board = board(mode); return board == null ? -1 : board.personalBest; }

	private LeaderboardBoard board(String mode) {
		if (leaderboards == null) return null;
		return "hard".equals(mode) ? leaderboards.hard : "classic".equals(mode) ? leaderboards.medium : null;
	}

	private static int[] copyRect(int[] rect) {
		return new int[] {rect[0], rect[1], rect[2], rect[3]};
	}
}
