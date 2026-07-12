package com.openrsc.server.plugins.custom.minigames;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToAction;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.AttackPlayerTrigger;
import com.openrsc.server.plugins.triggers.CommandTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangePlayerTrigger;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import com.openrsc.server.plugins.triggers.SpellPlayerTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class PkCatchingSimulator implements CommandTrigger, AttackNpcTrigger,
		AttackPlayerTrigger, OpNpcTrigger, PlayerLoginTrigger, PlayerRangeNpcTrigger, PlayerRangePlayerTrigger,
		SpellNpcTrigger, SpellPlayerTrigger, TalkNpcTrigger {
	private static final Logger LOGGER = LogManager.getLogger(PkCatchingSimulator.class);
	private static final int TRAINER_NPC_ID = 840;
	private static final int TRAINER_X = 214;
	private static final int TRAINER_Y = 437;
	private static final int TARGET_NPC_ID = 841;
	private static final String LEAVE_COMMAND = "leave";
	private static final String HIGHSCORE_COMMAND = "Highscore";
	private static final String OWNER_ATTRIBUTE = "pkcatchsim_owner";
	private static final String COMBAT_ACTIVE_ATTRIBUTE = "pkcatchsim_combat_active";
	private static final HighscoreTable HIGHSCORES = new HighscoreTable();

	private static final int BASE_ARENA_MIN_X = 344;
	private static final int BASE_ARENA_MAX_X = 378;
	private static final int BASE_ARENA_MIN_Y = 56;
	private static final int BASE_ARENA_MAX_Y = 86;
	private static final int BASE_PLAYER_START_X = 361;
	private static final int BASE_PLAYER_START_Y = 71;
	private static final int BASE_TARGET_START_X = 360;
	private static final int BASE_TARGET_START_Y = 71;
	private static final ArenaSlot[] ARENA_SLOTS = new ArenaSlot[] {
		new ArenaSlot(0, 0, 0),
		new ArenaSlot(1, 48, 0),
		new ArenaSlot(2, 96, 0)
	};
	private static final int CATCH_DISTANCE = 2;
	private static final int SESSION_SECONDS = 5 * 60;
	private static final int PVP_RETREAT_UNLOCK_SWINGS = 3;

	private static final int TREE_OBJECT_ID = 0;
	private static final int ROCK_OBJECT_ID = 98;
	private static final int ARENA_WALL_OBJECT_ID = 1010;

	private static final String[] GOOD_CATCH_LINES = new String[] {
		"Good catch!",
		"That one landed.",
		"Nice timing."
	};
	private static final String[] GOOD_TRAIL_LINES = new String[] {
		"Good trail!",
		"Right on my heels.",
		"That's the tile."
	};
	private static final String[] TOO_FAR_LINES = new String[] {
		"Too far.",
		"You clicked from too wide.",
		"Close the gap first."
	};
	private static final String[] BAD_PATH_LINES = new String[] {
		"Bad pathing.",
		"You took the long way.",
		"Cut the corner tighter."
	};

	private final Map<Long, CatchSession> sessions = new ConcurrentHashMap<Long, CatchSession>();
	private final Map<Integer, Long> occupiedArenaSlots = new ConcurrentHashMap<Integer, Long>();

	@Override
	public boolean blockCommand(Player player, String cmd, String[] args) {
		return cmd.equalsIgnoreCase(LEAVE_COMMAND);
	}

	@Override
	public void onCommand(Player player, String cmd, String[] args) {
		CatchSession session = sessions.get(player.getUsernameHash());
		if (session == null) {
			player.message("You are not in the PK Catching Simulator.");
			return;
		}
		finishSession(player, session, false, "You leave the PK Catching Simulator.", true);
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return isInsideAnyArena(player.getLocation());
	}

	@Override
	public void onPlayerLogin(Player player) {
		CatchSession staleSession = sessions.remove(player.getUsernameHash());
		if (staleSession != null) {
			if (staleSession.event != null) {
				staleSession.event.stop();
			}
			try {
				staleSession.cleanup();
			} finally {
				releaseArenaSlot(staleSession);
			}
		}

		player.resetCombatEvent();
		player.setOpponent(null);
		player.setLastOpponent(null);
		player.setBusy(false);
		player.cancelAutoWalk();
		player.resetPath();
		player.resetFollowing();
		player.removeAttribute(COMBAT_ACTIVE_ATTRIBUTE);
		ActionSender.sendSystemUpdateTimer(player, 0);
		player.teleport(TRAINER_X, TRAINER_Y, true);
		player.message("You are returned from the PK Catching Simulator.");
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isTrainer(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!blockTalkNpc(player, npc)) {
			return;
		}
		if (player.inCombat() || player.getOwnedPlugins().size() > 1) {
			player.message("You need to be free from combat and other actions first.");
			return;
		}

		CatchSession existing = sessions.get(player.getUsernameHash());
		if (existing != null) {
			finishSession(player, existing, false, "", false);
		}

		npcsay(player, npc, "Start a five minute catching drill?");
		int choice = multi(player, npc, "Start the drill", "Not yet");
		if (choice != 0) {
			npcsay(player, npc, "Come back when you're ready.");
			return;
		}
		startSession(player);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isTrainer(npc) && command != null && command.equalsIgnoreCase(HIGHSCORE_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) {
			return;
		}
		showHighscores(player);
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc npc) {
		return isSimulatorTarget(npc);
	}

	@Override
	public void onAttackNpc(Player player, Npc npc) {
		CatchSession session = sessions.get(player.getUsernameHash());
		if (session == null || session.target != npc) {
			if (isSimulatorTarget(npc)) {
				player.message("That catching target belongs to another drill.");
			}
			return;
		}

		// The simulator owns attacks on this temporary target. A valid catch
		// enters a simulator-only PvP-style combat event: no XP, HP loss, or drops.
		long tick = player.getWorld().getServer().getCurrentTick();
		if (session.isLocked(tick)) {
			if (tick - session.lastCatchTick > 1) {
				player.message("Wait for the combat lock to end.");
			}
			return;
		}

		boolean goodTrail = session.wasGoodTrailOnPendingAttempt() || session.isGoodTrail();

		session.recordCatch(tick, goodTrail);
		session.startPvpStyleCombatRound(tick);
	}

	@Override
	public boolean blockAttackPlayer(Player player, Player affectedMob) {
		return involvesSimulatorPlayer(player, affectedMob);
	}

	@Override
	public void onAttackPlayer(Player player, Player affectedMob) {
		if (involvesSimulatorPlayer(player, affectedMob)) {
			player.message("The PK Catching Simulator blocks real player combat.");
		}
	}

	@Override
	public boolean blockPlayerRangePlayer(Player player, Player affectedMob) {
		return involvesSimulatorPlayer(player, affectedMob);
	}

	@Override
	public void onPlayerRangePlayer(Player player, Player affectedMob) {
		if (involvesSimulatorPlayer(player, affectedMob)) {
			player.message("The PK Catching Simulator blocks real player combat.");
		}
	}

	@Override
	public boolean blockPlayerRangeNpc(Player player, Npc npc) {
		return isSimulatorTarget(npc);
	}

	@Override
	public void onPlayerRangeNpc(Player player, Npc npc) {
		sendTargetActionDenied(player, npc);
	}

	@Override
	public boolean blockSpellNpc(Player player, Npc npc) {
		return isSimulatorTarget(npc);
	}

	@Override
	public void onSpellNpc(Player player, Npc npc) {
		sendTargetActionDenied(player, npc);
	}

	@Override
	public boolean blockSpellPlayer(Player player, Player affectedPlayer, Spells spellEnum) {
		return involvesSimulatorPlayer(player, affectedPlayer);
	}

	@Override
	public void onSpellPlayer(Player player, Player affectedPlayer, Spells spellEnum) {
		if (involvesSimulatorPlayer(player, affectedPlayer)) {
			player.message("The PK Catching Simulator blocks real player combat.");
		}
	}

	private void sendTargetActionDenied(Player player, Npc npc) {
		if (player == null || !isSimulatorTarget(npc)) {
			return;
		}
		if (isSimulatorTargetFor(player, npc)) {
			player.message("Use melee attack clicks to tag the catching target.");
		} else {
			player.message("That catching target belongs to another drill.");
		}
	}

	private void startSession(Player player) {
		World world = player.getWorld();
		Point returnPoint = player.getLocation();
		ArenaSlot slot = reserveArenaSlot(player.getUsernameHash());
		if (slot == null) {
			player.message("All PK Catching Simulator arenas are busy. Try again in a moment.");
			return;
		}

		player.cancelAutoWalk();
		player.resetFollowing();
		player.resetPath();
		player.teleport(slot.playerStartX, slot.playerStartY, true);

		Npc target = new SyntheticPvpTarget(world, TARGET_NPC_ID, slot.targetStartX, slot.targetStartY,
			slot.minX, slot.maxX, slot.minY, slot.maxY);
		target.setShouldRespawn(false);
		target.setBusy(true);
		target.setAttribute(OWNER_ATTRIBUTE, player.getUsernameHash());
		world.registerNpc(target);

		long startTick = world.getServer().getCurrentTick();
		int durationTicks = (int)Math.ceil((SESSION_SECONDS * 1000.0) / player.getConfig().GAME_TICK);
		CatchSession session = new CatchSession(player, target, slot, returnPoint, startTick, startTick + durationTicks);
		session.prepareArenaTiles();
		session.addArenaWalls();
		session.addObstacles();
		sessions.put(player.getUsernameHash(), session);

		CatchSimulatorEvent event = new CatchSimulatorEvent(world, player, session);
		session.event = event;
		world.getServer().getGameEventHandler().add(event);

		player.message("PK Catching Simulator started.");
		player.message("Practice arena " + (slot.id + 1) + " assigned.");
		player.message("Stay one tile behind the target and re-attack after each lock.");
		player.message("Use ::leave to leave early.");
		ActionSender.sendSystemUpdateTimer(player, SESSION_SECONDS);
	}

	private boolean isSimulatorTargetFor(Player player, Npc npc) {
		Long ownerHash = getSimulatorTargetOwner(npc);
		return ownerHash != null && ownerHash.longValue() == player.getUsernameHash();
	}

	private boolean isSimulatorTarget(Npc npc) {
		return getSimulatorTargetOwner(npc) != null;
	}

	private Long getSimulatorTargetOwner(Npc npc) {
		if (npc == null || npc.getID() != TARGET_NPC_ID) {
			return null;
		}
		return npc.getAttribute(OWNER_ATTRIBUTE, null);
	}

	private boolean isTrainer(Npc npc) {
		return npc != null && npc.getID() == TRAINER_NPC_ID;
	}

	private boolean involvesSimulatorPlayer(Player first, Player second) {
		return sessions.containsKey(first.getUsernameHash()) || sessions.containsKey(second.getUsernameHash());
	}

	private boolean isInsideAnyArena(Point point) {
		for (ArenaSlot slot : ARENA_SLOTS) {
			if (slot.contains(point, 2)) {
				return true;
			}
		}
		return false;
	}

	private synchronized ArenaSlot reserveArenaSlot(long ownerHash) {
		for (ArenaSlot slot : ARENA_SLOTS) {
			Long currentOwner = occupiedArenaSlots.get(slot.id);
			if (currentOwner == null || currentOwner.longValue() == ownerHash) {
				occupiedArenaSlots.put(slot.id, ownerHash);
				return slot;
			}
		}
		return null;
	}

	private synchronized void releaseArenaSlot(CatchSession session) {
		if (session == null) {
			return;
		}
		Long currentOwner = occupiedArenaSlots.get(session.slot.id);
		if (currentOwner != null && currentOwner.longValue() == session.player.getUsernameHash()) {
			occupiedArenaSlots.remove(session.slot.id);
		}
	}

	private void finishSession(Player player, CatchSession session, boolean completed, String message, boolean teleportBack) {
		sessions.remove(player.getUsernameHash());
		if (session.event != null) {
			session.event.stop();
		}
		try {
			session.cleanup();
		} finally {
			releaseArenaSlot(session);
		}
		player.setBusy(false);
		player.resetPath();
		player.resetFollowing();

		if (teleportBack && session.returnPoint != null && player.loggedIn()) {
			player.teleport(session.returnPoint.getX(), session.returnPoint.getY(), true);
		}
		if (message != null && message.length() > 0 && player.loggedIn()) {
			player.message(message);
		}
		if (player.loggedIn()) {
			ActionSender.sendSystemUpdateTimer(player, 0);
			session.showScoreboard(completed);
		}
	}

	private static int distance(Point a, Point b) {
		int x = Math.abs(a.getX() - b.getX());
		int y = Math.abs(a.getY() - b.getY());
		return Math.max(x, y);
	}

	private enum Difficulty {
		EASY(1, 0, 13),
		MEDIUM(1, 25, 11),
		HARD(1, 55, 10);

		private final int moveEveryTicks;
		private final int jukeChance;
		private final int badPathDistance;

		Difficulty(int moveEveryTicks, int jukeChance, int badPathDistance) {
			this.moveEveryTicks = moveEveryTicks;
			this.jukeChance = jukeChance;
			this.badPathDistance = badPathDistance;
		}
	}

	private enum RunnerTactic {
		KITE,
		STRAIGHT,
		DIAGONAL,
		CUTBACK,
		CORNER,
		OBSTACLE,
		CENTER
	}

	private final class CatchSimulatorEvent extends GameTickEvent {
		private final Player player;
		private final CatchSession session;

		private CatchSimulatorEvent(World world, Player player, CatchSession session) {
			super(world, player, 0, "PK Catching Simulator", DuplicationStrategy.ONE_PER_MOB);
			this.player = player;
			this.session = session;
		}

		@Override
		public void run() {
			if (getDelayTicks() == 0) {
				setDelayTicks(1);
			}

			if (!player.loggedIn() || player.isRemoved()) {
				finishSession(player, session, false, "", false);
				return;
			}
			if (session.target == null || session.target.isRemoved()) {
				finishSession(player, session, false, "The catching drill ended unexpectedly.", true);
				return;
			}

			long tick = getWorld().getServer().getCurrentTick();
			session.suppressRealCombat(tick);
			if (tick >= session.endTick) {
				finishSession(player, session, true, "Training time is up.", true);
				return;
			}

			if (!session.slot.contains(player.getLocation(), 2)) {
				finishSession(player, session, false, "You left the arena.", true);
				return;
			}

			session.samplePosition(tick);
			session.enforceCombatLock(tick);
			// Attack clicks become WalkToMobAction instances before the player
			// reaches the NPC, so far/late clicks can be scored server-side.
			session.inspectAttackAction(tick);
			session.releaseCombatLock(tick);

			if (!session.isLocked(tick)) {
				session.moveTarget(tick);
			}
		}
	}

	private final class CatchSession {
		private final Player player;
		private final Npc target;
		private final ArenaSlot slot;
		private final Point returnPoint;
		private final Random random;
		private final long endTick;
		private final List<PlacedObstacle> obstacles = new ArrayList<PlacedObstacle>();
		private final Map<Integer, Byte> savedTileMasks = new HashMap<Integer, Byte>();

		private GameTickEvent event;
		private Difficulty difficulty;
		private Point previousTargetLocation;
		private int targetDirectionX = 1;
		private int targetDirectionY = 0;
		private long nextMoveTick;
		private RunnerTactic runnerTactic = RunnerTactic.KITE;
		private long tacticUntilTick = -1;
		private Point tacticGoal;
		private boolean targetRunningStarted;
		private int repeatedDirectionTicks;
		private long lockReleasedTick = -1;
		private PkCatchingCombatEvent combatEvent;
		private boolean combatRoundActive;
		private boolean hasReleasedLock;
		private WalkToAction observedAttackAction;
		private WalkToAction pendingAttackAction;
		private boolean pendingAttackHadGoodTrail;
		private long pendingAttackTick = -1;
		private long lastCatchTick = -50;
		private long lastBadPathMessageTick = -50;
		private long lastGoodTrailMessageTick = -50;
		private int consecutiveBadPathTicks;

		private int distanceSamples;
		private int totalDistance;
		private int oneTileTicks;
		private int exactTrailTicks;
		private int consecutiveTrailTicks;
		private int badPathTicks;
		private int reactionSamples;
		private int totalReactionTicks;
		private int catches;
		private int misses;
		private int currentStreak;
		private int bestStreak;

		private CatchSession(Player player, Npc target, ArenaSlot slot, Point returnPoint, long startTick, long endTick) {
			this.player = player;
			this.target = target;
			this.slot = slot;
			this.returnPoint = returnPoint;
			this.random = new Random(player.getUsernameHash() ^ startTick);
			this.endTick = endTick;
			this.difficulty = rollDifficulty();
			this.nextMoveTick = startTick + 1;
			this.previousTargetLocation = target.getLocation();
		}

		private boolean isLocked(long tick) {
			return combatRoundActive;
		}

		private void startPvpStyleCombatRound(long tick) {
			// Simulate RSC PvP combat against a server-side training actor.
			// The actor is rendered as an NPC, but only this simulator owns its
			// combat state; it never enters normal NPC damage, XP, loot, or AI.
			forceClearSimulatedCombat();
			player.setBusy(false);
			target.setBusy(false);
			player.resetPath();
			target.resetPath();
			player.resetFollowing();
			target.resetFollowing();
			player.setWalkToAction(null);
			target.setAttribute(COMBAT_ACTIVE_ATTRIBUTE, true);
			player.setAttribute(COMBAT_ACTIVE_ATTRIBUTE, true);
			player.resetRanAwayTimer();
			target.resetRanAwayTimer();
			player.setHitsMade(0);
			target.setHitsMade(0);
			player.setLastOpponent(null);
			target.setLastOpponent(null);
			player.setLocation(target.getLocation(), false);
			player.setSprite(9);
			target.setSprite(8);
			player.setOpponent(target);
			target.setOpponent(player);
			player.setCombatTimer();
			target.setCombatTimer();

			combatRoundActive = true;
			hasReleasedLock = false;
			combatEvent = new PkCatchingCombatEvent(player.getWorld());
			player.getWorld().getServer().getGameEventHandler().add(combatEvent);
		}

		private void clearSimulatedCombat() {
			clearSimulatedCombat(false);
		}

		private void forceClearSimulatedCombat() {
			clearSimulatedCombat(true);
		}

		private void clearSimulatedCombat(boolean force) {
			if (!force && combatEvent != null && combatEvent.isRunning()) {
				return;
			}
			if (force) {
				if (combatEvent != null) {
					combatEvent.stop();
				}
			}
			combatEvent = null;
			clearNormalCombatEvents();
			if (player.getOpponent() == target) {
				player.setOpponent(null);
			}
			if (target.getOpponent() == player) {
				target.setOpponent(null);
			}
			player.setLastOpponent(null);
			target.setLastOpponent(null);
			player.setHitsMade(0);
			target.setHitsMade(0);
			player.resetFollowing();
			target.resetFollowing();
			player.setWalkToAction(null);
			player.removeAttribute(COMBAT_ACTIVE_ATTRIBUTE);
			target.removeAttribute(COMBAT_ACTIVE_ATTRIBUTE);
			if (player.getSprite() > 7) {
				player.setSprite(4);
			}
			if (target.getSprite() > 7) {
				target.setSprite(4);
			}
			player.setBusy(false);
			target.setBusy(false);
		}

		private void clearNormalCombatEvents() {
			if (player.getCombatEvent() != null) {
				player.getCombatEvent().stop();
				player.setCombatEvent(null);
			}
			if (target.getCombatEvent() != null) {
				target.getCombatEvent().stop();
				target.setCombatEvent(null);
			}
		}

		private void enforceCombatLock(long tick) {
			if (!isLocked(tick)) {
				return;
			}
			player.resetPath();
			target.resetPath();
		}

		private void suppressRealCombat(long tick) {
			if (player.getCombatEvent() != null || target.getCombatEvent() != null) {
				clearNormalCombatEvents();
			}
			if (!isLocked(tick) && (player.getOpponent() == target || target.getOpponent() == player)) {
				clearSimulatedCombat();
			}
			if (!isLocked(tick)) {
				if (!targetRunningStarted) {
					// Keep the target as a stationary opener. Normal NPC roam/aggro
					// would otherwise start moving it before the first catch.
					target.resetPath();
					target.setBusy(true);
				} else {
					target.setBusy(false);
				}
				target.setLastOpponent(null);
			}
		}

		private void releaseCombatLock(long tick) {
			if (combatRoundActive) {
				if (combatEvent != null && combatEvent.isRunning()) {
					return;
				}
				finishCombatRound(tick);
			}
		}

		private void finishCombatRound(long tick) {
			if (!combatRoundActive) {
				return;
			}
			combatRoundActive = false;
			forceClearSimulatedCombat();
			player.setBusy(false);
			target.setBusy(false);
			difficulty = rollDifficulty();
			lockReleasedTick = tick;
			hasReleasedLock = true;
			targetRunningStarted = true;
			player.setRanAwayTimer();
			target.setRanAwayTimer();
			nextMoveTick = tick;
			chooseRunnerTactic(tick, target.getLocation(), true);
		}

		private final class PkCatchingCombatEvent extends GameTickEvent {
			private int roundNumber;

			private PkCatchingCombatEvent(World world) {
				super(world, null, 0, "PK Catching Simulator Combat", DuplicationStrategy.ONE_PER_MOB);
			}

			@Override
			public void run() {
				long tick = getWorld().getServer().getCurrentTick();
				if (!canContinueCombatRound()) {
					stop();
					finishCombatRound(tick);
					return;
				}

				Mob hitter;
				Mob defender;
				if (roundNumber++ % 2 == 0) {
					hitter = player;
					defender = target;
				} else {
					hitter = target;
					defender = player;
				}
				// Real PvP alternates 3-1 or 2-2 based on PID order. This
				// synthetic target has no PID, so use the stable 2-2 duel/PvP
				// cadence rather than inventing a separate lock timer.
				setDelayTicks(2);
				inflictTrainingSwing(hitter, defender);

				// In real PvP, a retreat is legal once the opponent has made
				// three hits. The target is the one retreating, so the catcher's
				// hitsMade counter is the authoritative release condition.
				if (player.getHitsMade() >= PVP_RETREAT_UNLOCK_SWINGS) {
					stop();
					finishCombatRound(tick);
				}
			}

			private boolean canContinueCombatRound() {
				return player.loggedIn()
					&& !player.isRemoved()
					&& !target.isRemoved()
					&& player.getOpponent() == target
					&& target.getOpponent() == player
					&& player.getLocation().equals(target.getLocation());
			}

			private void inflictTrainingSwing(Mob hitter, Mob defender) {
				hitter.incHitsMade();
				hitter.setCombatTimer();
				defender.setCombatTimer();
				defender.getUpdateFlags().setDamage(new Damage(defender, 0));
				ActionSender.sendSound(player, "combat2a");
			}
		}

		private Difficulty rollDifficulty() {
			int roll = random.nextInt(100);
			if (roll < 45) {
				return Difficulty.EASY;
			}
			if (roll < 80) {
				return Difficulty.MEDIUM;
			}
			return Difficulty.HARD;
		}

		private void inspectAttackAction(long tick) {
			WalkToAction action = player.getWalkToAction();
			if (action == null || !(action instanceof WalkToMobAction)) {
				return;
			}

			WalkToMobAction mobAction = (WalkToMobAction)action;
			if (mobAction.getActionType() != ActionType.ATTACK || mobAction.getMob() != target) {
				return;
			}

			if (action != observedAttackAction) {
				observedAttackAction = action;
				pendingAttackAction = action;
				pendingAttackHadGoodTrail = isGoodTrail();
				pendingAttackTick = tick;
			}

			if (isWithinPvpCatchReach()) {
				if (isLocked(tick)) {
					player.setWalkToAction(null);
					if (tick - lastCatchTick > 1) {
						player.message("Wait for the combat lock to end.");
					}
					return;
				}

				player.setWalkToAction(null);
				player.resetPath();
				player.resetFollowing();

				recordCatch(tick, pendingAttackHadGoodTrail || isGoodTrail());
				startPvpStyleCombatRound(tick);
			}
		}

		private boolean isWithinPvpCatchReach() {
			Point targetPoint = target.getLocation();
			return isWithinPvpCatchReach(player.getLocation(), targetPoint)
				|| isWithinPvpCatchReach(player.getWalkingQueue().getNextMovement(), targetPoint);
		}

		private boolean isWithinPvpCatchReach(Point playerPoint, Point targetPoint) {
			return playerPoint.withinRange(targetPoint, CATCH_DISTANCE);
		}

		private void showScoreboard(boolean completed) {
			ActionSender.sendBox(player, buildRoundScoreboard(player, catches, completed), true);
		}

		private boolean wasGoodTrailOnPendingAttempt() {
			WalkToAction lastExecuted = player.getLastExecutedWalkToAction();
			return pendingAttackAction != null && pendingAttackAction == lastExecuted && pendingAttackHadGoodTrail;
		}

		private boolean isGoodTrail() {
			return previousTargetLocation != null && player.getLocation().equals(previousTargetLocation);
		}

		private void recordCatch(long tick, boolean goodTrail) {
			lastCatchTick = tick;
			catches++;
			currentStreak++;
			bestStreak = Math.max(bestStreak, currentStreak);
			if (pendingAttackTick >= 0 && hasReleasedLock) {
				totalReactionTicks += Math.max(0, (int)(pendingAttackTick - lockReleasedTick));
				reactionSamples++;
			}
			player.message("Good catch");
			targetSay(goodTrail ? GOOD_TRAIL_LINES : GOOD_CATCH_LINES);
			if (goodTrail) {
				player.message("Good trail");
			}
		}

		private void recordMiss(String feedback) {
			misses++;
			currentStreak = 0;
			pendingAttackAction = null;
			pendingAttackHadGoodTrail = false;
			pendingAttackTick = -1;
			player.message(feedback);
			if ("Too far".equals(feedback)) {
				targetSay(TOO_FAR_LINES);
			}
		}

		private void samplePosition(long tick) {
			int currentDistance = distance(player.getLocation(), target.getLocation());
			distanceSamples++;
			totalDistance += currentDistance;
			if (currentDistance == 1) {
				oneTileTicks++;
			}

			if (!targetRunningStarted) {
				return;
			}

			if (isGoodTrail()) {
				exactTrailTicks++;
				consecutiveTrailTicks++;
				if (consecutiveTrailTicks >= 4 && tick - lastGoodTrailMessageTick > 10) {
					player.message("Good trail");
					targetSay(GOOD_TRAIL_LINES);
					lastGoodTrailMessageTick = tick;
				}
			} else {
				consecutiveTrailTicks = 0;
			}

			if (!isLocked(tick) && pendingAttackAction == null && currentDistance >= difficulty.badPathDistance) {
				consecutiveBadPathTicks++;
				badPathTicks++;
				if (consecutiveBadPathTicks >= 12 && tick - lastBadPathMessageTick > 40) {
					player.message("Bad pathing");
					targetSay(BAD_PATH_LINES);
					lastBadPathMessageTick = tick;
				}
			} else {
				consecutiveBadPathTicks = 0;
			}
		}

		private void moveTarget(long tick) {
			if (!targetRunningStarted || tick < nextMoveTick || !target.finishedPath()) {
				return;
			}
			nextMoveTick = tick + difficulty.moveEveryTicks;

			Point current = target.getLocation();
			chooseRunnerTactic(tick, current, false);
			Point next = chooseNextTargetTile(current);
			if (next == null || next.equals(current)) {
				return;
			}

			previousTargetLocation = current;
			int nextDirectionX = Integer.compare(next.getX(), current.getX());
			int nextDirectionY = Integer.compare(next.getY(), current.getY());
			if (nextDirectionX == targetDirectionX && nextDirectionY == targetDirectionY) {
				repeatedDirectionTicks++;
			} else {
				repeatedDirectionTicks = 0;
			}
			targetDirectionX = nextDirectionX;
			targetDirectionY = nextDirectionY;
			// Keep the movement direction for the movement packet without also
			// sending a stationary sprite update before the tile step.
			target.face(next);
			target.resetSpriteChanged();
			target.setLocation(next, false);
		}

		private Point chooseNextTargetTile(Point current) {
			int awayX = Integer.compare(current.getX(), player.getX());
			int awayY = Integer.compare(current.getY(), player.getY());
			if (awayX == 0 && awayY == 0) {
				awayX = targetDirectionX;
				awayY = targetDirectionY;
			}

			List<Point> candidates = new ArrayList<Point>();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					addCandidate(candidates, current, dx, dy);
				}
			}
			addCandidate(candidates, current, awayX, awayY);
			addCandidate(candidates, current, awayX, 0);
			addCandidate(candidates, current, 0, awayY);
			addCandidate(candidates, current, targetDirectionX, targetDirectionY);
			addCandidate(candidates, current, -awayY, awayX);
			addCandidate(candidates, current, awayY, -awayX);

			if (random.nextInt(100) < difficulty.jukeChance) {
				addCandidate(candidates, current, -awayX, awayY);
				addCandidate(candidates, current, awayX, -awayY);
			}

			List<ScoredTile> scored = new ArrayList<ScoredTile>();
			int bestScore = Integer.MIN_VALUE;
			for (Point candidate : candidates) {
				if (!canTargetStep(current, candidate)) {
					continue;
				}
				int score = scoreRunnerTile(current, candidate, awayX, awayY);
				scored.add(new ScoredTile(candidate, score));
				if (score > bestScore) {
					bestScore = score;
				}
			}
			if (scored.isEmpty()) {
				return null;
			}

			List<Point> best = new ArrayList<Point>();
			for (ScoredTile tile : scored) {
				if (tile.score >= bestScore - 8) {
					best.add(tile.point);
				}
			}
			return best.get(random.nextInt(best.size()));
		}

		private void chooseRunnerTactic(long tick, Point current, boolean force) {
			if (!force && tick < tacticUntilTick) {
				return;
			}

			int playerDistance = distance(current, player.getLocation());
			int edgeDistance = distanceToArenaEdge(current);
			int roll = random.nextInt(100);
			if (edgeDistance <= 2 && roll < 75) {
				runnerTactic = RunnerTactic.CENTER;
			} else if (playerDistance <= CATCH_DISTANCE) {
				if (roll < 35) {
					runnerTactic = RunnerTactic.CUTBACK;
				} else if (roll < 65) {
					runnerTactic = RunnerTactic.DIAGONAL;
				} else if (roll < 85) {
					runnerTactic = RunnerTactic.OBSTACLE;
				} else {
					runnerTactic = RunnerTactic.KITE;
				}
			} else if (playerDistance <= 5) {
				if (roll < 25) {
					runnerTactic = RunnerTactic.STRAIGHT;
				} else if (roll < 50) {
					runnerTactic = RunnerTactic.DIAGONAL;
				} else if (roll < 75) {
					runnerTactic = RunnerTactic.OBSTACLE;
				} else {
					runnerTactic = RunnerTactic.CORNER;
				}
			} else {
				if (roll < 30) {
					runnerTactic = RunnerTactic.CENTER;
				} else if (roll < 55) {
					runnerTactic = RunnerTactic.CORNER;
				} else if (roll < 75) {
					runnerTactic = RunnerTactic.STRAIGHT;
				} else {
					runnerTactic = RunnerTactic.KITE;
				}
			}

			tacticGoal = chooseTacticGoal(current, runnerTactic);
			tacticUntilTick = tick + 4 + random.nextInt(difficulty == Difficulty.HARD ? 7 : 5);
		}

		private Point chooseTacticGoal(Point current, RunnerTactic tactic) {
			if (tactic == RunnerTactic.CENTER) {
				return slot.center();
			}
			if (tactic == RunnerTactic.CORNER) {
				Point[] corners = new Point[] {
					Point.location(slot.minX + 4, slot.minY + 4),
					Point.location(slot.minX + 4, slot.maxY - 4),
					Point.location(slot.maxX - 4, slot.minY + 4),
					Point.location(slot.maxX - 4, slot.maxY - 4)
				};
				Point best = corners[0];
				int bestScore = Integer.MIN_VALUE;
				for (Point corner : corners) {
					int score = distance(corner, player.getLocation()) * 3 - distance(corner, current);
					if (score > bestScore || (score == bestScore && random.nextBoolean())) {
						bestScore = score;
						best = corner;
					}
				}
				return best;
			}
			if (tactic == RunnerTactic.OBSTACLE && !obstacles.isEmpty()) {
				PlacedObstacle obstacle = obstacles.get(random.nextInt(obstacles.size()));
				return obstacle.object.getLocation();
			}
			return null;
		}

		private int scoreRunnerTile(Point current, Point candidate, int awayX, int awayY) {
			int dx = Integer.compare(candidate.getX(), current.getX());
			int dy = Integer.compare(candidate.getY(), current.getY());
			int score = distance(candidate, player.getLocation()) * 8;
			int edgeDistance = distanceToArenaEdge(candidate);

			if (edgeDistance <= 0) {
				score -= 40;
			} else if (edgeDistance == 1) {
				score -= runnerTactic == RunnerTactic.CORNER ? 8 : 24;
			} else if (edgeDistance == 2) {
				score -= runnerTactic == RunnerTactic.CORNER ? 0 : 10;
			}

			if (candidate.equals(previousTargetLocation) && runnerTactic != RunnerTactic.CUTBACK) {
				score -= 18;
			}
			if (dx == targetDirectionX && dy == targetDirectionY) {
				score += runnerTactic == RunnerTactic.STRAIGHT ? 16 : 5;
				if (repeatedDirectionTicks > 5 && runnerTactic != RunnerTactic.STRAIGHT) {
					score -= 20;
				}
			}
			if (dx == awayX && dy == awayY) {
				score += runnerTactic == RunnerTactic.KITE ? 20 : 8;
			}

			switch (runnerTactic) {
				case STRAIGHT:
					score += (dx == targetDirectionX && dy == targetDirectionY) ? 18 : -6;
					break;
				case DIAGONAL:
					score += dx != 0 && dy != 0 ? 22 : -8;
					break;
				case CUTBACK:
					score += (dx == -targetDirectionX || dy == -targetDirectionY) ? 24 : -8;
					score += distance(candidate, player.getLocation()) <= CATCH_DISTANCE + 1 ? 8 : 0;
					break;
				case CORNER:
				case CENTER:
					score += goalProgressScore(current, candidate, tacticGoal) * 5;
					break;
				case OBSTACLE:
					score += obstacleUseScore(candidate);
					break;
				case KITE:
				default:
					break;
			}

			if (random.nextInt(100) < difficulty.jukeChance) {
				score += random.nextInt(16);
			} else {
				score += random.nextInt(5);
			}
			return score;
		}

		private int goalProgressScore(Point current, Point candidate, Point goal) {
			if (goal == null) {
				return 0;
			}
			return distance(current, goal) - distance(candidate, goal);
		}

		private int obstacleUseScore(Point candidate) {
			if (obstacles.isEmpty()) {
				return 0;
			}
			int nearest = Integer.MAX_VALUE;
			for (PlacedObstacle obstacle : obstacles) {
				nearest = Math.min(nearest, distance(candidate, obstacle.object.getLocation()));
			}
			if (nearest <= 1) {
				return -8;
			}
			if (nearest <= 4) {
				return 18 - nearest;
			}
			return 0;
		}

		private int distanceToArenaEdge(Point point) {
			int west = point.getX() - (slot.minX + 1);
			int east = (slot.maxX - 1) - point.getX();
			int south = point.getY() - (slot.minY + 1);
			int north = (slot.maxY - 1) - point.getY();
			return Math.min(Math.min(west, east), Math.min(south, north));
		}

		private void addCandidate(List<Point> candidates, Point current, int dx, int dy) {
			if (dx == 0 && dy == 0) {
				return;
			}
			Point candidate = Point.location(current.getX() + dx, current.getY() + dy);
			if (!isInsideArenaWalkTile(candidate)) {
				return;
			}
			if (!candidates.contains(candidate)) {
				candidates.add(candidate);
			}
		}

		private boolean canTargetStep(Point current, Point candidate) {
			if (candidate.equals(player.getLocation())) {
				return false;
			}
			if (!isInsideArenaWalkTile(candidate) || isBlockedTile(candidate)) {
				return false;
			}
			return PathValidation.checkAdjacent(target, current, candidate);
		}

		private boolean isInsideArenaWalkTile(Point point) {
			return point.inBounds(slot.minX + 1, slot.minY + 1, slot.maxX - 1, slot.maxY - 1);
		}

		private boolean isBlockedTile(Point point) {
			return (player.getWorld().getTile(point.getX(), point.getY()).traversalMask & CollisionFlag.FULL_BLOCK) != 0;
		}

		private void addObstacles() {
			// Extra obstacles teach corners and inefficient path recovery. They
			// are temporary scenery, placed only on currently empty tiles.
			placeObstacle(ROCK_OBJECT_ID, slot.translateX(353), slot.translateY(64));
			placeObstacle(TREE_OBJECT_ID, slot.translateX(370), slot.translateY(63));
			placeObstacle(ROCK_OBJECT_ID, slot.translateX(366), slot.translateY(80));
			placeObstacle(ROCK_OBJECT_ID, slot.translateX(349), slot.translateY(79));
			placeObstacle(TREE_OBJECT_ID, slot.translateX(355), slot.translateY(72));
			placeObstacle(ROCK_OBJECT_ID, slot.translateX(374), slot.translateY(71));
		}

		private void targetSay(String[] lines) {
			if (target == null || target.isRemoved() || lines.length == 0) {
				return;
			}
			String line = lines[random.nextInt(lines.length)];
			target.getUpdateFlags().setChatMessage(new ChatMessage(target, line, player));
		}

		private void addArenaWalls() {
			// Temporary walls bound the baked sea-island training floor without
			// making the landscape patch itself responsible for minigame state.
			for (int x = slot.minX; x <= slot.maxX; x++) {
				placeObstacle(ARENA_WALL_OBJECT_ID, x, slot.minY);
				placeObstacle(ARENA_WALL_OBJECT_ID, x, slot.maxY);
				blockArenaTile(x, slot.minY);
				blockArenaTile(x, slot.maxY);
			}
			for (int y = slot.minY + 1; y < slot.maxY; y++) {
				placeObstacle(ARENA_WALL_OBJECT_ID, slot.minX, y);
				placeObstacle(ARENA_WALL_OBJECT_ID, slot.maxX, y);
				blockArenaTile(slot.minX, y);
				blockArenaTile(slot.maxX, y);
			}
		}

		private void blockArenaTile(int x, int y) {
			saveTileMask(x, y);
			player.getWorld().getTile(x, y).traversalMask |= CollisionFlag.FULL_BLOCK_C;
		}

		private void prepareArenaTiles() {
			// The client sees this as real floor from Custom_Landscape.orsc.
			// Preserve runtime masks, clear the arena for movement practice, then
			// restore them when the round ends.
			for (int x = slot.minX; x <= slot.maxX; x++) {
				for (int y = slot.minY; y <= slot.maxY; y++) {
					saveTileMask(x, y);
					player.getWorld().getTile(x, y).traversalMask = 0;
				}
			}
		}

		private void saveTileMask(int x, int y) {
			int key = tileKey(x, y);
			if (!savedTileMasks.containsKey(key)) {
				savedTileMasks.put(key, player.getWorld().getTile(x, y).traversalMask);
			}
		}

		private int tileKey(int x, int y) {
			return (x << 16) | (y & 0xffff);
		}

		private void placeObstacle(int objectId, int x, int y) {
			Point point = Point.location(x, y);
			World world = player.getWorld();
			Region region = world.getRegionManager().getRegion(point);
			if (region == null || region.getGameObject(point) != null) {
				return;
			}
			if (point.equals(player.getLocation()) || point.equals(target.getLocation())) {
				return;
			}

			saveTileMask(x, y);
			GameObject object = new GameObject(world, point, objectId, 0, 0);
			world.registerGameObject(object);
			obstacles.add(new PlacedObstacle(object));
		}

		private void cleanup() {
			World world = player.getWorld();
			for (PlacedObstacle obstacle : obstacles) {
				if (!obstacle.object.isRemoved()) {
					world.unregisterGameObject(obstacle.object);
				}
			}
			obstacles.clear();
			for (Map.Entry<Integer, Byte> entry : savedTileMasks.entrySet()) {
				int key = entry.getKey();
				int x = key >> 16;
				int y = key & 0xffff;
				world.getTile(x, y).traversalMask = entry.getValue();
			}
			savedTileMasks.clear();
			combatRoundActive = false;
			forceClearSimulatedCombat();
			player.resetRanAwayTimer();
			target.resetRanAwayTimer();

			if (target != null && player.getWorld().hasNpc(target)) {
				player.getWorld().unregisterNpc(target);
			}
		}

	}

	private void showHighscores(Player player) {
		HighscoreSnapshot snapshot = HIGHSCORES.snapshot(player, 10);
		StringBuilder builder = new StringBuilder();
		builder.append("PK Catching Simulator Highscores% %");
		appendPersonalRank(builder, snapshot);
		appendLeaderboard(builder, snapshot.top, 10, "Top 10 best catchers:");
		ActionSender.sendBox(player, builder.toString(), true);
	}

	private String buildRoundScoreboard(Player player, int catches, boolean completed) {
		HighscoreSnapshot snapshot = completed
			? HIGHSCORES.record(player, catches, 0)
			: HIGHSCORES.snapshot(player, 0);

		StringBuilder builder = new StringBuilder();
		builder.append("PK Catching Simulator% %");
		builder.append("Catches this round: @gre@").append(formatCatches(catches)).append("@whi@%");
		appendPersonalBest(builder, snapshot);
		if (completed) {
			builder.append(snapshot.newPersonalBest ? "@yel@New personal best!@whi@%" : "Full 5 minute score recorded.%");
		} else {
			builder.append("Finish all 5 minutes to enter the leaderboard.%");
		}
		return builder.toString();
	}

	private void appendPersonalBest(StringBuilder builder, HighscoreSnapshot snapshot) {
		if (snapshot.playerBest == null) {
			builder.append("Your best 5 minute round: @red@None yet@whi@%");
			return;
		}
		builder.append("Your best 5 minute round: @gre@")
			.append(formatCatches(snapshot.playerBest.catches))
			.append("@whi@%");
	}

	private void appendPersonalRank(StringBuilder builder, HighscoreSnapshot snapshot) {
		if (snapshot.playerBest == null) {
			builder.append("Your rank: @red@Unranked@whi@%");
			builder.append("Finish a full 5 minute round to place.%");
			return;
		}
		builder.append("Your rank: @yel@#")
			.append(snapshot.playerRank)
			.append("@whi@ with @gre@")
			.append(formatCatches(snapshot.playerBest.catches))
			.append("@whi@%");
	}

	private void appendLeaderboard(StringBuilder builder, List<HighscoreEntry> entries, int limit, String title) {
		builder.append(title).append("%");
		if (entries.isEmpty()) {
			builder.append("No completed 5 minute runs yet.%");
			return;
		}
		int count = Math.min(limit, entries.size());
		for (int i = 0; i < count; i++) {
			HighscoreEntry entry = entries.get(i);
			builder.append(i + 1)
				.append(". ")
				.append(sanitizeBoxText(entry.username))
				.append(" - @gre@")
				.append(formatCatches(entry.catches))
				.append("@whi@%");
		}
	}

	private static String formatCatches(int catches) {
		return catches + (catches == 1 ? " catch" : " catches");
	}

	private static String sanitizeBoxText(String value) {
		if (value == null || value.length() == 0) {
			return "Unknown";
		}
		return value.replace('%', ' ').replace('@', ' ').replace('|', ' ');
	}

	private static final class HighscoreTable {
		private static final File FILE = new File("conf/server/data/pk_catching_sim_highscores.properties");

		private synchronized HighscoreSnapshot record(Player player, int catches, int topCount) {
			Map<Long, HighscoreEntry> entries = load();
			long usernameHash = player.getUsernameHash();
			HighscoreEntry previous = entries.get(usernameHash);
			String username = sanitizeBoxText(player.getUsername());
			boolean updated = previous == null || catches > previous.catches;

			if (updated) {
				entries.put(usernameHash, new HighscoreEntry(usernameHash, username, catches));
				save(entries);
			} else if (!previous.username.equals(username)) {
				entries.put(usernameHash, new HighscoreEntry(usernameHash, username, previous.catches));
				save(entries);
			}

			return snapshot(entries, usernameHash, topCount, updated);
		}

		private synchronized HighscoreSnapshot snapshot(Player player, int topCount) {
			return snapshot(load(), player.getUsernameHash(), topCount, false);
		}

		private HighscoreSnapshot snapshot(Map<Long, HighscoreEntry> entries, long usernameHash, int topCount, boolean updated) {
			List<HighscoreEntry> sorted = new ArrayList<HighscoreEntry>(entries.values());
			Collections.sort(sorted, new Comparator<HighscoreEntry>() {
				@Override
				public int compare(HighscoreEntry first, HighscoreEntry second) {
					if (first.catches != second.catches) {
						return second.catches - first.catches;
					}
					return first.username.compareToIgnoreCase(second.username);
				}
			});

			HighscoreEntry playerBest = entries.get(usernameHash);
			int rank = -1;
			if (playerBest != null) {
				rank = 1;
				for (HighscoreEntry entry : sorted) {
					if (entry.catches > playerBest.catches) {
						rank++;
					}
				}
			}

			List<HighscoreEntry> top = new ArrayList<HighscoreEntry>();
			int count = Math.min(topCount, sorted.size());
			for (int i = 0; i < count; i++) {
				top.add(sorted.get(i));
			}
			return new HighscoreSnapshot(playerBest, rank, top, updated);
		}

		private Map<Long, HighscoreEntry> load() {
			Map<Long, HighscoreEntry> entries = new HashMap<Long, HighscoreEntry>();
			if (!FILE.isFile()) {
				return entries;
			}

			Properties properties = new Properties();
			try (FileInputStream input = new FileInputStream(FILE)) {
				properties.load(input);
			} catch (IOException ex) {
				LOGGER.warn("Unable to load PK catching highscores", ex);
				return entries;
			}

			for (String key : properties.stringPropertyNames()) {
				try {
					long usernameHash = Long.parseLong(key);
					String value = properties.getProperty(key, "");
					int separator = value.indexOf('|');
					int catches = Integer.parseInt(separator >= 0 ? value.substring(0, separator) : value);
					String username = separator >= 0 ? value.substring(separator + 1) : Long.toString(usernameHash);
					if (catches >= 0) {
						entries.put(usernameHash, new HighscoreEntry(usernameHash, sanitizeBoxText(username), catches));
					}
				} catch (NumberFormatException ex) {
					LOGGER.warn("Ignoring invalid PK catching highscore entry: {}", key);
				}
			}
			return entries;
		}

		private void save(Map<Long, HighscoreEntry> entries) {
			File parent = FILE.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
				LOGGER.warn("Unable to create PK catching highscore directory: {}", parent.getAbsolutePath());
				return;
			}

			Properties properties = new Properties();
			for (HighscoreEntry entry : entries.values()) {
				properties.setProperty(Long.toString(entry.usernameHash), entry.catches + "|" + sanitizeBoxText(entry.username));
			}

			try (FileOutputStream output = new FileOutputStream(FILE)) {
				properties.store(output, "PK Catching Simulator highscores");
			} catch (IOException ex) {
				LOGGER.warn("Unable to save PK catching highscores", ex);
			}
		}
	}

	private static final class HighscoreSnapshot {
		private final HighscoreEntry playerBest;
		private final int playerRank;
		private final List<HighscoreEntry> top;
		private final boolean newPersonalBest;

		private HighscoreSnapshot(HighscoreEntry playerBest, int playerRank, List<HighscoreEntry> top, boolean newPersonalBest) {
			this.playerBest = playerBest;
			this.playerRank = playerRank;
			this.top = top;
			this.newPersonalBest = newPersonalBest;
		}
	}

	private static final class HighscoreEntry {
		private final long usernameHash;
		private final String username;
		private final int catches;

		private HighscoreEntry(long usernameHash, String username, int catches) {
			this.usernameHash = usernameHash;
			this.username = username;
			this.catches = catches;
		}
	}

	private static final class ArenaSlot {
		private final int id;
		private final int minX;
		private final int maxX;
		private final int minY;
		private final int maxY;
		private final int playerStartX;
		private final int playerStartY;
		private final int targetStartX;
		private final int targetStartY;

		private ArenaSlot(int id, int offsetX, int offsetY) {
			this.id = id;
			this.minX = BASE_ARENA_MIN_X + offsetX;
			this.maxX = BASE_ARENA_MAX_X + offsetX;
			this.minY = BASE_ARENA_MIN_Y + offsetY;
			this.maxY = BASE_ARENA_MAX_Y + offsetY;
			this.playerStartX = BASE_PLAYER_START_X + offsetX;
			this.playerStartY = BASE_PLAYER_START_Y + offsetY;
			this.targetStartX = BASE_TARGET_START_X + offsetX;
			this.targetStartY = BASE_TARGET_START_Y + offsetY;
		}

		private boolean contains(Point point, int buffer) {
			return point != null && point.inBounds(minX - buffer, minY - buffer, maxX + buffer, maxY + buffer);
		}

		private Point center() {
			return Point.location((minX + maxX) / 2, (minY + maxY) / 2);
		}

		private int translateX(int baseX) {
			return baseX + minX - BASE_ARENA_MIN_X;
		}

		private int translateY(int baseY) {
			return baseY + minY - BASE_ARENA_MIN_Y;
		}
	}

	private static final class PlacedObstacle {
		private final GameObject object;

		private PlacedObstacle(GameObject object) {
			this.object = object;
		}
	}

	private static final class ScoredTile {
		private final Point point;
		private final int score;

		private ScoredTile(Point point, int score) {
			this.point = point;
			this.score = score;
		}
	}

	private static final class SyntheticPvpTarget extends Npc {
		private SyntheticPvpTarget(World world, int id, int startX, int startY,
								   int minX, int maxX, int minY, int maxY) {
			super(world, id, startX, startY, minX, maxX, minY, maxY);
		}

		@Override
		public void updatePosition() {
			// The simulator moves this actor after player attack checks, matching
			// the position the client just saw. Normal NPC movement happens before
			// player packets each tick and makes 2-tile catches feel one step ahead.
			resetPath();
		}
	}
}
