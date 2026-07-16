package com.openrsc.server.content.voidarena;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.PlayerInventory;
import com.openrsc.server.database.struct.VoidArenaMatchRecord;
import com.openrsc.server.database.struct.VoidArenaMatchSessionRecord;
import com.openrsc.server.database.struct.VoidArenaPairAudit;
import com.openrsc.server.database.struct.VoidArenaSettlementStatus;
import com.openrsc.server.database.struct.VoidArenaStats;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.UnregisterForcefulness;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.BubbleNpc;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class VoidArena {
	private static final Logger LOGGER = LogManager.getLogger(VoidArena.class);
	private static final int RULE_RANKED = 1;
	private static final int RULE_F2P_ONLY = 1 << 1;
	private static final int RULE_ALLOW_PRAYER = 1 << 2;
	private static final int RULE_ALLOW_RANGED = 1 << 3;
	private static final int RULE_ALLOW_MAGIC = 1 << 4;
	private static final int ACTION_CHALLENGE = 0;
	private static final int ACTION_DECLINE = 1;
	private static final int ACTION_UPDATE_RULES = 2;
	private static final int ACTION_ACCEPT = 3;
	private static final int ACTION_CONFIRM = 4;
	private static final int UNRANKED_MATCH_TIMEOUT_MS = 5 * 60 * 1000;
	private static final int RANKED_MATCH_TIMEOUT_MS = 10 * 60 * 1000;
	private static final int MATCH_COUNTDOWN_MS = 5 * 1000;
	private static final int MONTHLY_CHAMPION_FINALIZATION_GRACE_MS = 15 * 60 * 1000;
	private static final int DEATH_MATCH_SETUP_CLIENT_VERSION = 10109;
	private static final int DM_KING_CLIENT_VERSION = 10112;
	private static final String DM_KING_DISPLAY_NAME = "Sir Charles";
	private static final int DM_KING_MATCH_TIMEOUT_MS = RANKED_MATCH_TIMEOUT_MS;
	private static final int DM_KING_FISH_COUNT = VoidArenaSirPolicy.FISH_CAPACITY;
	private static final int DM_KING_FIRE_BLAST_CASTS = VoidArenaSirPolicy.FIRE_BLAST_CAST_CAPACITY;
	private static final int DM_KING_AIR_RUNES = DM_KING_FIRE_BLAST_CASTS * 4;
	private static final int DM_KING_DEATH_RUNES = DM_KING_FIRE_BLAST_CASTS;
	private static final int DM_KING_FIRE_RUNES = DM_KING_FIRE_BLAST_CASTS * 5;
	private static final int DM_KING_STRENGTH_POTION_DOSES = VoidArenaSirPolicy.STRENGTH_POTION_DOSE_CAPACITY;
	private static final int DM_KING_PRAYER_LEVEL = VoidArenaSirPolicy.PRAYER_LEVEL;
	private static final int DM_KING_PRAYER_STATE_POINTS = VoidArenaSirPolicy.INITIAL_PRAYER_STATE_POINTS;
	private static final int DM_KING_EAT_DELAY_TICKS = 1;
	private static final int DM_KING_STRENGTH_REPOT_DELAY_TICKS = 1;
	private static final int DM_KING_IDLE_ROAM_DELAY_TICKS = 2;
	private static final int DM_KING_REENGAGE_DELAY_TICKS = 2;
	private static final int DM_KING_KITE_DELAY_TICKS = 1;
	private static final int DM_KING_KITE_MIN_DISTANCE = 3;
	private static final int DM_KING_MELEE_COMMIT_HITS = 18;
	private static final int DM_KING_QUIP_COOLDOWN_TICKS = 12;
	private static final int DM_KING_RANDOM_QUIP_CHANCE = 18;
	private static final int DM_KING_FIRE_BLAST_PROJECTILE = 9;
	private static final double DM_KING_FIRE_BLAST_POWER = 12.0D;
	private static final int[] DM_KING_ACTIVE_PRAYERS = {
		Prayers.STEEL_SKIN,
		Prayers.ULTIMATE_STRENGTH,
		Prayers.INCREDIBLE_REFLEXES
	};
	private static final String DM_KING_KIT_CACHE = "void_arena_dmking_kit_snapshot";
	private static final String DM_KING_BROADCAST_CACHE = "void_arena_dmking_broadcast";
	private static final String DM_KING_WINS_CACHE = "void_arena_dmking_wins";
	private static final String DM_KING_LOSSES_CACHE = "void_arena_dmking_losses";
	private static final int DM_KING_INITIAL_WINS = 300;
	private static final int DM_KING_INITIAL_LOSSES = 0;
	private static final String[] DM_KING_PREFIGHT_QUIPS = {
		"Shall we? I've a dinner engagement and you're the appetiser.",
		"Do say a prayer first. Several, ideally. They won't help, but it's polite.",
		"Last chance to flee with your dignity. Going once?",
		"I'll go easy on you. By which I mean I'll finish quickly, out of pity.",
		"Try to bleed somewhere tasteful. The carpets are imported.",
		"Ready your little weapon. This will sting your pride far more than your body.",
		"I do hope you've written a will. And chosen a flattering portrait.",
		"Let's get this farce underway, shall we? My patience is aristocratically thin.",
		"Raise your guard. Not that it matters, but I admire the gesture.",
		"Come along then. The sooner we begin, the sooner you can lie down.",
		"I shall count to three. You won't make it to two.",
		"Brace yourself, peasant. This is the closest you'll come to greatness.",
		"Oh, you brought a spear. How adorable. I brought four hundred years of breeding.",
		"Defend the realm, defend your honour, defend yourself, do pick one, you've not the time for all three.",
		"I'll let you swing first. I find the false hope so very moving.",
		"Steady now. Wouldn't want you fainting before I've even started.",
		"This is the part where you discover the difference between us. The lethal part.",
		"En garde, or whatever it is you common folk shout.",
		"Mind the throne on your way down. It's a family heirloom.",
		"Shall I dim the torches? I'd hate for the crowd to see your face when it happens."
	};
	private static final String[] DM_KING_RANDOM_FIGHT_QUIPS = {
		"Oh dear. Was that meant to hurt?",
		"You're trembling. How delightfully predictable.",
		"Is that all? I've been bitten by more aggressive teacups.",
		"Keep swinging, it's wonderful exercise for you.",
		"You're not winning, you understand. You're simply losing slowly.",
		"Ha! A valiant effort. Valiant, and entirely pointless.",
		"I felt that one. Almost. Not really. Do try harder.",
		"You fight like a man whose mother is watching. Badly, and apologetically.",
		"Bleeding already? We've only just begun the humiliation.",
		"I could do this all day. You, evidently, cannot.",
		"Mind the footwork, dear. Oh, too late. Down you pop.",
		"Magnificent flailing. Truly. The crowd weeps with laughter.",
		"You're making this far too easy. I do prefer a challenge. Pity.",
		"Tired already? Royalty doesn't tire. We have people for that.",
		"Almost had me there. No, I lie. You didn't. Not even slightly."
	};
	private static final String[] DM_KING_WIN_QUIPS = {
		"And there it is. The crown remains where crowns belong. On me.",
		"Predictable. Disappointing. Over. Do gather your teeth on the way out.",
		"Another for the wall of skulls. You'll be in good, dead company.",
		"Well fought! By my standards, you were almost present.",
		"Run along now. Tell the others the king sends his regards. And his pity.",
		"That, dear challenger, is what four centuries of superiority looks like.",
		"I'd say it was an honour, but we both know it was a slaughter.",
		"Defeat suits you, actually. Brings out the colour in your bruises.",
		"Do come again. I collect humiliations, and yours was a fine vintage.",
		"The throne thanks you for your contribution. Which was losing, beautifully.",
		"You may weep now. I shan't watch. Actually, I shall. It's the best part.",
		"And so the natural order is restored. I'm rather good at restoring it.",
		"Off you pop, then. Mind the puddle. It's you."
	};
	private static final String[] DM_KING_RARE_LOSS_QUIPS = {
		"I... what? No. No, the light was in my eyes. The sun. Indoors.",
		"This changes nothing. NOTHING. I demand a rematch. And a lawyer.",
		"You cheated. I don't know how, but breeding tells me you cheated.",
		"Impossible. Statistically, cosmically, aristocratically impossible.",
		"Enjoy it. It won't happen again. I refuse to let it happen again.",
		"...Well played. There. I said it. Now never speak of this. EVER."
	};
	private static final String[] DM_KING_LOW_FOOD_QUIPS = {
		"Counting fish now, are we?",
		"Pantry looking thin.",
		"Swordfish discipline, old chap.",
		"That inventory is getting draughty."
	};
	private static final String[] DM_KING_NO_FOOD_QUIPS = {
		"No fish. Bold policy.",
		"Dry pockets already?",
		"That is the end of supper.",
		"Empty bag, full confidence."
	};
	private static final String[] DM_KING_LOW_HITS_QUIPS = {
		"Careful. Hitpoints are useful.",
		"Steady now, nearly curtains.",
		"That health is frightfully small.",
		"Do breathe between clicks."
	};
	private static final String[] DM_KING_MISSED_CAST_QUIPS = {
		"That spell went to Varrock.",
		"A scenic Fire Blast.",
		"Lovely splash. No notes.",
		"Nearly warmed my boots."
	};
	private static final String[] DM_KING_NAME_QUIPS = {
		"%s, is it? Marvellous optimism.",
		"Was %s your duelling name or a warning?",
		"%s. I shall try to remember it.",
		"Chin up, %s."
	};
	private static final String[] DM_KING_OWN_LOW_FOOD_QUIPS = {
		"My fish are low. How sporting.",
		"Nearly out of supper myself.",
		"A proper finish, then.",
		"Now it gets tidy."
	};
	public static final String DM_KING_DYNAMIC_ATTRIBUTE = "void_arena_dm_king_dynamic";
	public static final String DM_KING_OWNER_ATTRIBUTE = "void_arena_dm_king_owner";
	private static final long SEASON_RESET_CONFIRM_MS = 2 * 60 * 1000;
	private static final String LAST_FINALIZED_CHAMPION_SEASON_CACHE = "void_arena_last_champion_season";
	private static final int SEASON_MAINTENANCE_INTERVAL_MS = 60 * 60 * 1000;
	private static final int RANKED_SETTLEMENT_RETRY_INITIAL_MS = 2_000;
	private static final int RANKED_SETTLEMENT_RETRY_MAX_MS = 30_000;

	private final World world;
	private final Map<Long, Match> activeMatches = new ConcurrentHashMap<>();
	private final Map<Long, DmKingChallenge> activeDmKingChallenges = new ConcurrentHashMap<>();
	private final Map<Long, Challenge> incomingChallenges = new ConcurrentHashMap<>();
	private final Map<Long, DeathMatchSetup> activeSetups = new ConcurrentHashMap<>();
	private final Map<String, VoidArenaStats> statsCache = new ConcurrentHashMap<>();
	private final Map<Long, SeasonResetConfirmation> pendingSeasonResets = new ConcurrentHashMap<>();

	public VoidArena(World world) {
		this.world = world;
	}

	public void startSeasonMaintenance() {
		scheduleSeasonMaintenance(1_000);
	}

	private void scheduleSeasonMaintenance(int delayMs) {
		world.getServer().getGameEventHandler().add(new SingleEvent(world, null, delayMs,
			"Void Arena Monthly Season Maintenance", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void action() {
				try {
					ensurePreviousSeasonChampion();
				} catch (GameDatabaseException e) {
					LOGGER.error("Unable to run Void Arena monthly season maintenance", e);
				}
				if (!world.getServer().isShuttingDown()) {
					scheduleSeasonMaintenance(SEASON_MAINTENANCE_INTERVAL_MS);
				}
			}
		});
	}

	public AttackCheck checkAttack(Player attacker, Player victim, boolean missile) {
		Match match = activeMatches.get(attacker.getUsernameHash());
		if (match != null) {
			if (match.isFinished() || match.hasPendingDisconnect()) {
				return AttackCheck.deny("That Death Match result is still being finalized.");
			}
			if (match.contains(victim) && match.isInsideAssignedCage(attacker) && match.isInsideAssignedCage(victim)) {
				if (!match.hasStarted()) {
					return AttackCheck.deny("The Death Match has not started yet.");
				}
				if (!canUsePvpOffense(attacker, victim)) {
					return AttackCheck.deny("You need to wait before re-attacking after a retreat.");
				}
				if (missile && !match.rules.allowRanged) {
					return AttackCheck.deny("Ranged is disabled for this Death Match.");
				}
				return AttackCheck.allow();
			}
			if (match.contains(victim)) {
				return AttackCheck.deny("Return to your assigned Void Arena cage.");
			}
			return AttackCheck.deny("You can only attack your Void Arena opponent.");
		}

		if (activeMatches.containsKey(victim.getUsernameHash())) {
			return AttackCheck.deny("That player is already in a Void Arena fight.");
		}

		if (VoidArenaConfig.isInsideVoidArena(attacker.getLocation())
			|| VoidArenaConfig.isInsideVoidArena(victim.getLocation())) {
			return AttackCheck.deny("Death Matches must be started through the Void Arena.");
		}

		return AttackCheck.pass();
	}

	public AttackCheck checkMagic(Player attacker, Player victim) {
		Match match = activeMatches.get(attacker.getUsernameHash());
		if (match != null) {
			if (match.isFinished() || match.hasPendingDisconnect()) {
				return AttackCheck.deny("That Death Match result is still being finalized.");
			}
			if (match.contains(victim) && match.isInsideAssignedCage(attacker) && match.isInsideAssignedCage(victim)) {
				if (!match.hasStarted()) {
					return AttackCheck.deny("The Death Match has not started yet.");
				}
				if (!canUsePvpOffense(attacker, victim)) {
					return AttackCheck.deny("You need to wait before re-attacking after a retreat.");
				}
				return match.rules.allowMagic
					? AttackCheck.allow()
					: AttackCheck.deny("Magic is disabled for this Death Match.");
			}
			if (match.contains(victim)) {
				return AttackCheck.deny("Return to your assigned Void Arena cage.");
			}
			return AttackCheck.deny("You can only cast on your Void Arena opponent.");
		}

		if (activeMatches.containsKey(victim.getUsernameHash())) {
			return AttackCheck.deny("That player is already in a Void Arena fight.");
		}

		if (VoidArenaConfig.isInsideVoidArena(attacker.getLocation())
			|| VoidArenaConfig.isInsideVoidArena(victim.getLocation())) {
			return AttackCheck.deny("Death Matches must be started through the Void Arena.");
		}

		return AttackCheck.pass();
	}

	private boolean canUsePvpOffense(Player attacker, Player victim) {
		return attacker != null && victim != null
			&& attacker.canBeReattacked() && victim.canBeReattacked();
	}

	public boolean canActivatePrayer(Player player) {
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null && !match.rules.allowPrayer) {
			player.message("Prayer is disabled for this Death Match.");
			player.getPrayers().resetPrayers();
			return false;
		}
		return true;
	}

	public Point handlePlayerDeath(Player loser, Mob killer) {
		DmKingChallenge dmKingChallenge = activeDmKingChallenges.get(loser.getUsernameHash());
		if (dmKingChallenge != null) {
			resolveDmKingLoss(dmKingChallenge, loser, DM_KING_DISPLAY_NAME + " defeated you.",
				false, true, true);
			loser.setInstanceId(0);
			return VoidArenaConfig.lobbyTile();
		}

		Match match = activeMatches.get(loser.getUsernameHash());
		if (match == null) {
			return null;
		}

		Player disconnected = match.disconnectedPlayer();
		if (disconnected != null) {
			resolveMatch(match, match.opponent(disconnected), disconnected,
				VoidArenaRankedPolicy.ResultType.FORFEIT);
		} else {
			Player winner = match.opponent(loser);
			resolveMatch(match, winner, loser, VoidArenaRankedPolicy.ResultType.DEATH);
		}
		loser.setInstanceId(0);
		return VoidArenaConfig.lobbyTile();
	}

	public void handleLogout(Player player) {
		cancelSetup(player, true);
		removeChallenges(player);
		DmKingChallenge dmKingChallenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (dmKingChallenge != null) {
			resolveDmKingLoss(dmKingChallenge, player, "", true, true);
			sendRatingClear(player);
			player.setInstanceId(0);
			player.setLocation(VoidArenaConfig.exitTile(), true);
			return;
		}
		Match match = activeMatches.get(player.getUsernameHash());
		if (match == null) {
			sendRatingClear(player);
			if (VoidArenaConfig.isInsideVoidArena(player.getLocation())) {
				player.setInstanceId(0);
				player.setLocation(VoidArenaConfig.exitTile(), true);
			}
			return;
		}

		match.noteDisconnect(player, System.currentTimeMillis());
		Player forfeiter = match.disconnectedPlayer();
		if (forfeiter == null) {
			forfeiter = player;
		}
		Player opponent = match.opponent(forfeiter);
		resolveMatch(match, opponent, forfeiter, VoidArenaRankedPolicy.ResultType.FORFEIT);
		sendRatingClear(player);
		player.setInstanceId(0);
		player.setLocation(VoidArenaConfig.exitTile(), true);
	}

	public void handleLogin(Player player) {
		if (isOrphanedArenaCageLogin(player)) {
			player.resetAll();
			player.setInstanceId(0);
			player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
			restoreHits(player);
			player.message("An interrupted Void Arena session returned you to the lobby.");
		}
		if (player == null || !VoidArenaConfig.isInsideVoidArena(player.getLocation())) {
			return;
		}
		sendLobbyRatings(player);
		if (VoidArenaConfig.isInsideLobby(player.getLocation())) {
			broadcastLobbyRating(player);
		}
	}

	/** Finalizes live sessions without competitive results before a graceful server stop. */
	public synchronized boolean prepareForShutdown() {
		Set<Match> matches = new HashSet<>(activeMatches.values());
		for (Match match : matches) {
			if (match.isFinished() && match.rules.ranked
				&& attemptRankedSettlement(match) == VoidArenaSettlementStatus.DATABASE_ERROR) {
				LOGGER.fatal("Refusing graceful shutdown while ranked result {} is not durable",
					match.sessionId);
				return false;
			}
		}
		for (Match match : matches) {
			if (match.isFinished()) {
				continue;
			}
			resolveNeutralMatch(match, VoidArenaRankedPolicy.ResultType.SERVER_SHUTDOWN_NO_CONTEST,
				"server shutdown - no contest.");
			if (match.rules.ranked && activeMatches.get(match.playerAHash) == match) {
				LOGGER.fatal("Refusing graceful shutdown while ranked no-contest {} is not durable",
					match.sessionId);
				return false;
			}
		}
		Set<DmKingChallenge> sirChallenges = new HashSet<>(activeDmKingChallenges.values());
		for (DmKingChallenge challenge : sirChallenges) {
			resolveDmKingNoContest(challenge, world.getPlayer(challenge.playerHash));
		}
		return true;
	}

	public void handleInterfaceOption(Player player, int action, int targetServerIndex, int ruleMask) {
		switch (action) {
			case ACTION_CHALLENGE: {
				Player target = world.getPlayer(targetServerIndex);
				if (target == null || target.equals(player)) {
					player.message("That player is not available for a Death Match.");
					return;
				}
				requestChallenge(player, target);
				return;
			}
			case ACTION_DECLINE: {
				Player target = world.getPlayer(targetServerIndex);
				if (!cancelSetup(player, true) && target != null) {
					cancelChallenge(player, target);
				}
				return;
			}
			case ACTION_UPDATE_RULES:
				updateSetupRules(player, MatchRules.fromMask(ruleMask));
				return;
			case ACTION_ACCEPT:
				acceptSetup(player);
				return;
			case ACTION_CONFIRM:
				confirmSetup(player);
				return;
			default:
				player.message("That Death Match option is not available.");
		}
	}

	public void handleCommand(Player player, String[] args) {
		if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
			showHelp(player);
			return;
		}

		String action = args[0].toLowerCase();
		if (action.equals("enter")) {
			player.message("Use the Void Arena rift in the Void Enclave to enter.");
			return;
		}
		if (action.equals("leave") || action.equals("exit")) {
			leave(player);
			return;
		}
		if (action.equals("stats") || action.equals("rating")) {
			sendStats(player, player);
			return;
		}
		if (action.equals("top") || action.equals("leaderboard")) {
			sendLeaderboard(player);
			return;
		}
		if (action.equals("season")) {
			handleSeasonCommand(player, args);
			return;
		}
		if (action.equals("audit")) {
			sendAudit(player, args);
			return;
		}
		if (action.equals("challenge") || action.equals("fight")) {
			if (args.length < 2) {
				player.message("Syntax: ::arena challenge <player>");
				return;
			}
			Player target = world.getPlayer(com.openrsc.server.util.rsc.DataConversions.usernameToHash(joinArgs(args, 1)));
			if (target == null) {
				player.message("That player is not online.");
				return;
			}
			requestChallenge(player, target);
			return;
		}

		showHelp(player);
	}

	public void enterFromVoidEnclaveRift(Player player) {
		enterLobby(player);
	}

	public boolean leaveThroughVoidArenaRift(Player player) {
		return leave(player);
	}

	public synchronized void challengeDmKing(Player player) {
		if (!canStartDmKingChallenge(player, true)) {
			return;
		}
		int slotIndex = firstAvailableSlot();
		if (slotIndex < 0) {
			player.message("All Void Arena cages are occupied right now.");
			return;
		}
		purgeCageGroundItems(slotIndex);
		if (!player.tryReserveSave()) {
			player.message("Your account is still saving. Try the " + DM_KING_DISPLAY_NAME
				+ " challenge again in a moment.");
			return;
		}

		final String snapshot;
		try {
			snapshot = VoidArenaKitSnapshot.capture(player);
		} catch (IllegalStateException e) {
			LOGGER.error("Unable to capture {} kit for {}", DM_KING_DISPLAY_NAME,
				player.getUsername(), e);
			player.message("Your " + DM_KING_DISPLAY_NAME
				+ " challenge could not start because your real kit could not be snapshotted safely.");
			releaseDmKingSaveReservation(player, "snapshot capture failure");
			return;
		}
		boolean markerSaved = false;
		try {
			player.getCache().store(DM_KING_KIT_CACHE, snapshot);
			markerSaved = saveDmKingKitMarker(player, snapshot);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to save {} kit marker for {}",
				DM_KING_DISPLAY_NAME, player.getUsername(), e);
		}
		if (!markerSaved) {
			player.getCache().remove(DM_KING_KIT_CACHE);
			player.message("Your " + DM_KING_DISPLAY_NAME
				+ " challenge could not start because your real kit was not safely saved.");
			releaseDmKingSaveReservation(player, "snapshot marker failure");
			return;
		}

		VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
		Npc king = null;
		DmKingChallenge challenge = null;
		try {
			king = new Npc(world, NpcId.DM_KING_ARENA.id(), slot.startBX, slot.startBY,
				slot.minX, slot.maxX, slot.minY, slot.maxY);
			king.setShouldRespawn(false);
			king.setAttribute(Npc.SUPPRESS_DEFAULT_DEATH_ATTRIBUTE, true);
			king.setAttribute(DM_KING_DYNAMIC_ATTRIBUTE, true);
			king.setAttribute(DM_KING_OWNER_ATTRIBUTE, player.getUsernameHash());
			world.registerNpc(king);

			player.resetAll();
			int playerFishCapacity = applyDmKingKit(player);
			if (playerFishCapacity != DM_KING_FISH_COUNT) {
				throw new IllegalStateException("temporary challenge kit did not fit exactly");
			}
			restoreHits(player);
			player.setInstanceId(0);
			player.teleportFromVoidArena(slot.startAX, slot.startAY, true);
			removeChallenges(player);

			challenge = new DmKingChallenge(slotIndex, player, king,
				System.currentTimeMillis() + MATCH_COUNTDOWN_MS, playerFishCapacity);
			if (activeDmKingChallenges.putIfAbsent(player.getUsernameHash(), challenge) != null) {
				throw new IllegalStateException("player already has an active Sir Charles challenge");
			}
			world.getServer().getGameEventHandler().add(challenge.event);

			sendArenaControl(player, "countdown|5");
			player.message("@red@" + DM_KING_DISPLAY_NAME + " challenge begins in 5 seconds.");
			player.message("Rules: Ranked F2P kit, melee and Fire Blast, finite supplies, no Elo.");
			player.message("Defeat " + DM_KING_DISPLAY_NAME + " before the 10 minute limit.");
		} catch (RuntimeException e) {
			LOGGER.error("Unable to start {} challenge for {} after saving the recovery marker",
				DM_KING_DISPLAY_NAME,
				player.getUsername(), e);
			rollbackDmKingChallengeStart(player, slotIndex, king, challenge);
		}
	}

	private void rollbackDmKingChallengeStart(Player player, int slotIndex, Npc king,
		DmKingChallenge challenge) {
		if (challenge != null) {
			activeDmKingChallenges.remove(player.getUsernameHash(), challenge);
			try {
				challenge.event.stop();
			} catch (RuntimeException e) {
				LOGGER.error("Unable to stop failed {} startup event for {}",
					DM_KING_DISPLAY_NAME, player.getUsername(), e);
			}
		}
		try {
			if (king != null) {
				player.resetTrackedDamageAndBlockedDamage(king);
			}
		} catch (RuntimeException e) {
			LOGGER.error("Unable to reset failed {} startup damage tracking for {}",
				DM_KING_DISPLAY_NAME, player.getUsername(), e);
		}
		try {
			cleanupDmKingNpc(king);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to remove failed {} startup NPC for {}",
				DM_KING_DISPLAY_NAME, player.getUsername(), e);
		}
		try {
			purgeCageGroundItems(slotIndex);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to purge failed {} startup cage {}",
				DM_KING_DISPLAY_NAME, slotIndex, e);
		}
		try {
			player.resetAll();
			player.setInstanceId(0);
			player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to relocate {} after failed {} startup",
				player.getUsername(), DM_KING_DISPLAY_NAME, e);
			try {
				player.setInstanceId(0);
				player.setLocation(VoidArenaConfig.lobbyTile(), true);
			} catch (RuntimeException fallbackError) {
				LOGGER.error("Unable to apply fallback arena relocation for {}",
					player.getUsername(), fallbackError);
			}
		}

		boolean recoveryCommitted = false;
		try {
			recoveryCommitted = restoreDmKingKit(player);
		} catch (IllegalStateException e) {
			LOGGER.error("Unable to recover {} kit startup for {}", DM_KING_DISPLAY_NAME,
				player.getUsername(), e);
		} finally {
			releaseDmKingSaveReservation(player, "startup rollback");
		}
		if (!recoveryCommitted) {
			player.unregister(UnregisterForcefulness.FORCED,
				"Sir Charles startup recovery persistence failed");
			return;
		}
		if (player.loggedIn()) {
			player.message("The " + DM_KING_DISPLAY_NAME
				+ " challenge could not start; your exact real state was restored.");
		}
	}

	public AttackCheck checkDmKingNpcAction(Player player, Npc npc, boolean missile) {
		if (npc == null || player == null) {
			return AttackCheck.pass();
		}
		if (npc.getID() == NpcId.DM_KING.id()) {
			return AttackCheck.deny("Challenge " + DM_KING_DISPLAY_NAME + " from the Void Arena lobby.");
		}
		if (!isDmKingChallengeNpc(npc)) {
			return AttackCheck.pass();
		}

		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge == null || challenge.king != npc) {
			return AttackCheck.deny("That " + DM_KING_DISPLAY_NAME + " challenge belongs to another fighter.");
		}
		if (!challenge.isInsideAssignedCage(player) || !challenge.isInsideAssignedCage(npc)) {
			return AttackCheck.deny("Return to your assigned " + DM_KING_DISPLAY_NAME + " cage.");
		}
		if (!challenge.hasStarted()) {
			return AttackCheck.deny("The " + DM_KING_DISPLAY_NAME + " challenge has not started yet.");
		}
		if (missile) {
			return AttackCheck.deny("Ranged is not part of the " + DM_KING_DISPLAY_NAME + " challenge kit.");
		}
		if (!challenge.canUsePvpOffense(world.getServer().getCurrentTick(), player)) {
			return AttackCheck.deny("You need to wait before re-attacking after a retreat.");
		}
		return AttackCheck.allow();
	}

	public void handleDmKingNpcKilled(Player player, Npc npc) {
		DmKingChallenge challenge = player == null ? null : activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge == null || challenge.king != npc) {
			return;
		}
		resolveDmKingVictory(challenge, player);
	}

	public void recoverDmKingKit(Player player) {
		recoverDmKingKit(player, false);
	}

	public void recoverDmKingKitBeforeLogin(Player player) {
		recoverDmKingKit(player, true);
		if (player == null || hasPendingDmKingKitRecovery(player)
			|| player.hasUnregisterRequest() || !isOrphanedArenaCageLogin(player)) {
			return;
		}
		player.resetAll();
		player.setInstanceId(0);
		player.setLocation(VoidArenaConfig.lobbyTile(), true);
		restoreHits(player);
	}

	private void recoverDmKingKit(Player player, boolean beforeLoginPackets) {
		if (player == null || !player.getCache().hasKey(DM_KING_KIT_CACHE)) {
			return;
		}
		player.resetAll();
		player.setInstanceId(0);
		if (beforeLoginPackets) {
			player.setLocation(VoidArenaConfig.lobbyTile(), true);
		} else {
			player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		}
		final boolean recoveryCommitted;
		try {
			recoveryCommitted = restoreDmKingKit(player, !beforeLoginPackets);
		} catch (IllegalStateException e) {
			LOGGER.error("Corrupt {} kit snapshot for {}", DM_KING_DISPLAY_NAME,
				player.getUsername(), e);
			if (!beforeLoginPackets) {
				player.message("Your " + DM_KING_DISPLAY_NAME
					+ " kit recovery is locked because its saved snapshot is invalid. Contact staff.");
			}
			player.unregister(UnregisterForcefulness.FORCED,
				"Invalid Sir Charles kit recovery snapshot");
			return;
		}
		if (!recoveryCommitted) {
			if (!beforeLoginPackets) {
				player.message("Your " + DM_KING_DISPLAY_NAME
					+ " kit was restored, but recovery could not be finalized safely. Please log in again.");
			}
			player.unregister(UnregisterForcefulness.FORCED,
				"Sir Charles kit recovery persistence failed");
			return;
		}
		if (!beforeLoginPackets) {
			player.message("@mag@Your " + DM_KING_DISPLAY_NAME + " challenge kit was restored.");
		}
	}

	public boolean hasPendingDmKingKitRecovery(Player player) {
		return player != null && player.getCache().hasKey(DM_KING_KIT_CACHE);
	}

	public boolean needsImmediateArenaLoginRecovery(Player player) {
		return hasPendingDmKingKitRecovery(player) || isOrphanedArenaCageLogin(player);
	}

	private boolean isOrphanedArenaCageLogin(Player player) {
		if (player == null || !VoidArenaConfig.isInsideArena(player.getLocation())) {
			return false;
		}
		Match match = activeMatches.get(player.getUsernameHash());
		return (match == null || match.isFinished())
			&& !activeDmKingChallenges.containsKey(player.getUsernameHash());
	}

	public boolean isDmKingChallengeNpc(Npc npc) {
		return npc != null
			&& npc.getID() == NpcId.DM_KING_ARENA.id()
			&& npc.getAttribute(DM_KING_DYNAMIC_ATTRIBUTE, false);
	}

	public boolean shouldSuppressDmKingNpcXp(Npc npc) {
		return isDmKingChallengeNpc(npc);
	}

	public double dmKingPrayerMultiplier(Mob source, int prayer1, int prayer2, int prayer3) {
		if (!(source instanceof Npc) || !isDmKingChallengeNpc((Npc) source)) {
			return 1.0D;
		}
		DmKingChallenge challenge = dmKingChallengeFor((Npc) source);
		if (challenge == null) {
			return 1.0D;
		}
		double activeMultiplier = challenge.hasPrayer(prayer3) ? 1.15D
			: challenge.hasPrayer(prayer2) ? 1.1D
			: challenge.hasPrayer(prayer1) ? 1.05D : 1.0D;
		return VoidArenaSirPolicy.prayerMultiplier(challenge.prayerStatePoints, activeMultiplier);
	}

	public VoidArenaStats getStats(Player player) {
		return getStats(player.getDatabaseID(), player.getUsername());
	}

	public void sendStats(Player viewer, Player target) {
		VoidArenaStats stats = getStats(target);
		if (isRatingVisible(stats)) {
			viewer.playerServerMessage(MessageType.QUEST,
				"@mag@Void Arena: @whi@" + target.getUsername() + " @yel@" + stats.rating
					+ "@whi@ rating, @gre@" + stats.wins + "@whi@ wins, @red@" + stats.losses + "@whi@ losses.");
			return;
		}
		viewer.playerServerMessage(MessageType.QUEST,
			"@mag@Void Arena: @whi@" + target.getUsername() + " is @yel@unranked@whi@ ("
				+ matchCount(stats) + "/" + VoidArenaConfig.RATING_VISIBLE_MATCHES + " placement matches).");
	}

	public boolean isInsideLobby(Player player) {
		return VoidArenaConfig.isInsideLobby(player.getLocation());
	}

	public boolean isInActiveMatch(Player player) {
		return player != null
			&& (activeMatches.containsKey(player.getUsernameHash())
				|| activeDmKingChallenges.containsKey(player.getUsernameHash()));
	}

	/** Captures connection loss before queued combat/death work can choose a terminal result. */
	public void noteDisconnect(Player player) {
		if (player == null) {
			return;
		}
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null) {
			match.noteDisconnect(player, System.currentTimeMillis());
		}
	}

	public boolean blocksGroundItemAction(Player player, Point targetLocation) {
		return player != null && (isInActiveMatch(player)
			|| VoidArenaConfig.isInsideArena(player.getLocation())
			|| VoidArenaConfig.isInsideArena(targetLocation));
	}

	public boolean blocksOrdinaryDuel(Player player) {
		return player != null && (VoidArenaConfig.isInsideVoidArena(player.getLocation())
			|| activeMatches.containsKey(player.getUsernameHash())
			|| activeDmKingChallenges.containsKey(player.getUsernameHash())
			|| activeSetups.containsKey(player.getUsernameHash())
			|| hasPendingChallenge(player));
	}

	public UUID projectileSessionId(Mob caster, Mob opponent) {
		UUID casterSession = projectileSessionIdFor(caster);
		UUID opponentSession = projectileSessionIdFor(opponent);
		return casterSession != null && casterSession.equals(opponentSession) ? casterSession : null;
	}

	public boolean canProjectileImpact(Mob caster, Mob opponent, UUID expectedSessionId) {
		UUID casterSession = projectileSessionIdFor(caster);
		UUID opponentSession = projectileSessionIdFor(opponent);
		if (expectedSessionId == null) {
			return casterSession == null && opponentSession == null;
		}
		return expectedSessionId.equals(casterSession) && expectedSessionId.equals(opponentSession);
	}

	private UUID projectileSessionIdFor(Mob mob) {
		if (mob instanceof Player) {
			Player player = (Player) mob;
			Match match = activeMatches.get(player.getUsernameHash());
			if (match != null && !match.isFinished() && !match.hasPendingDisconnect()) {
				return match.sessionId;
			}
			DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
			return challenge == null ? null : challenge.sessionId;
		}
		if (mob instanceof Npc && isDmKingChallengeNpc((Npc) mob)) {
			DmKingChallenge challenge = dmKingChallengeFor((Npc) mob);
			return challenge == null ? null : challenge.sessionId;
		}
		return null;
	}

	public boolean blocksTeleport(Player player) {
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null) {
			player.message("You can't teleport during a Death Match.");
			return true;
		}
		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge != null) {
			player.message("You can't teleport during the " + DM_KING_DISPLAY_NAME + " challenge.");
			return true;
		}
		return false;
	}

	public void enforceMatchBounds(Player player) {
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null && !match.isFinished() && !match.isInsideAssignedCage(player)) {
			player.resetPath();
			player.message("You cannot leave the Death Match cage.");
			Point start = match.startFor(player);
			player.teleportFromVoidArena(start.getX(), start.getY(), true);
			return;
		}
		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge != null && !challenge.isInsideAssignedCage(player)) {
			player.resetPath();
			player.message("You cannot leave the " + DM_KING_DISPLAY_NAME + " cage.");
			Point start = VoidArenaConfig.arenaSlot(challenge.slotIndex).startA();
			player.teleportFromVoidArena(start.getX(), start.getY(), true);
		}
	}

	private int matchCount(VoidArenaStats stats) {
		return stats == null ? 0 : stats.wins + stats.losses;
	}

	private boolean isRatingVisible(VoidArenaStats stats) {
		return matchCount(stats) >= VoidArenaConfig.RATING_VISIBLE_MATCHES;
	}

	private boolean supportsRatingDisplay(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= VoidArenaConfig.RATING_DISPLAY_CLIENT_VERSION;
	}

	private void sendRatingClear(Player player) {
		if (supportsRatingDisplay(player)) {
			ActionSender.sendMessage(player, "@vsarena@clear");
		}
	}

	private void sendRatingPayload(Player viewer, Player subject) {
		if (!supportsRatingDisplay(viewer) || subject == null) {
			return;
		}
		VoidArenaStats stats = getStats(subject);
		boolean visible = isRatingVisible(stats);
		ActionSender.sendMessage(viewer,
			"@vsarena@rating|" + subject.getUsername() + "|"
				+ (visible ? Integer.toString(stats.rating) : "0") + "|"
				+ matchCount(stats) + "|" + (visible ? "1" : "0") + "|"
				+ (isRankedEligible(subject) ? "1" : "0"));
	}

	private boolean supportsDmKingRecordDisplay(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= DM_KING_CLIENT_VERSION;
	}

	private void sendDmKingRecordPayload(Player viewer) {
		sendDmKingRecordPayload(viewer, loadDmKingRecord());
	}

	private void sendDmKingRecordPayload(Player viewer, DmKingRecord record) {
		if (!supportsDmKingRecordDisplay(viewer) || record == null) {
			return;
		}
		ActionSender.sendMessage(viewer,
			"@vsarena@dmking|" + record.wins + "|" + record.losses);
	}

	private void broadcastDmKingRecord(DmKingRecord record) {
		if (record == null) {
			return;
		}
		for (Player viewer : world.getPlayers()) {
			if (VoidArenaConfig.isInsideVoidArena(viewer.getLocation())) {
				sendDmKingRecordPayload(viewer, record);
			}
		}
	}

	private void sendLobbyRatings(Player viewer) {
		if (!supportsRatingDisplay(viewer)) {
			return;
		}
		for (Player subject : world.getPlayers()) {
			if (VoidArenaConfig.isInsideLobby(subject.getLocation())) {
				sendRatingPayload(viewer, subject);
			}
		}
		sendDmKingRecordPayload(viewer);
	}

	private void broadcastLobbyRating(Player subject) {
		if (subject == null) {
			return;
		}
		for (Player viewer : world.getPlayers()) {
			if (VoidArenaConfig.isInsideVoidArena(viewer.getLocation())) {
				sendRatingPayload(viewer, subject);
			}
		}
	}

	private void enterLobby(Player player) {
		if (player.inCombat()) {
			player.message("You can't enter the Void Arena whilst fighting.");
			return;
		}
		if (player.getDuel().getDuelRecipient() != null || player.getDuel().isDuelActive()) {
			player.message("Finish or cancel your regular duel before entering the Void Arena.");
			return;
		}
		if (activeMatches.containsKey(player.getUsernameHash())
			|| activeDmKingChallenges.containsKey(player.getUsernameHash())) {
			player.message("You are already in a Void Arena fight.");
			return;
		}
		player.setInstanceId(0);
		player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		restoreHits(player);
		sendStats(player, player);
		sendLobbyRatings(player);
		broadcastLobbyRating(player);
		player.message("Right-click another player here and choose Death Match.");
	}

	private boolean leave(Player player) {
		if (!VoidArenaConfig.isInsideVoidArena(player.getLocation())) {
			player.message("You are not in the Void Arena.");
			return false;
		}
		if (activeMatches.containsKey(player.getUsernameHash())
			|| activeDmKingChallenges.containsKey(player.getUsernameHash())) {
			player.message("You cannot leave during an active Death Match.");
			return false;
		}
		cancelSetup(player, true);
		removeChallenges(player);
		sendRatingClear(player);
		restoreHits(player);
		player.setInstanceId(0);
		player.teleportFromVoidArena(VoidArenaConfig.EXIT_X, VoidArenaConfig.EXIT_Y, true);
		player.message("The void folds around you and returns you to the Void Enclave.");
		return true;
	}

	private void requestChallenge(Player challenger, Player target) {
		Challenge reciprocal = incomingChallenges.get(challenger.getUsernameHash());
		if (reciprocal != null && reciprocal.challengerHash == target.getUsernameHash() && !reciprocal.expired()) {
			if (!canOpenSetup(challenger, target, true)) {
				return;
			}
			incomingChallenges.remove(challenger.getUsernameHash());
			openSetup(target, challenger);
			return;
		}

		if (!canOpenSetup(challenger, target, true)) {
			return;
		}

		incomingChallenges.put(target.getUsernameHash(),
			new Challenge(challenger.getUsernameHash(), target.getUsernameHash()));
		challenger.message("Death Match request sent to " + target.getUsername() + ".");
		target.playerServerMessage(MessageType.QUEST,
			"@mag@Death Match request from @whi@" + challenger.getUsername() + "@mag@.");
		target.message(challenger.getUsername() + " challenged you to a Death Match.");
		target.message("Right-click " + challenger.getUsername() + " and choose Death Match to accept.");
	}

	private void cancelChallenge(Player player, Player target) {
		Challenge challenge = incomingChallenges.get(target.getUsernameHash());
		if (challenge != null && challenge.challengerHash == player.getUsernameHash()) {
			incomingChallenges.remove(target.getUsernameHash());
			player.message("Your Void Arena challenge has been cancelled.");
		}
	}

	private synchronized void openSetup(Player playerA, Player playerB) {
		if (!canOpenSetup(playerA, playerB, true)) {
			return;
		}
		MatchRules rules = isRankedPairEligible(playerA, playerB)
			? MatchRules.ranked()
			: MatchRules.unrankedDefault();
		DeathMatchSetup setup = new DeathMatchSetup(playerA.getUsernameHash(), playerB.getUsernameHash(), rules);
		activeSetups.put(playerA.getUsernameHash(), setup);
		activeSetups.put(playerB.getUsernameHash(), setup);
		removeChallenges(playerA);
		removeChallenges(playerB);
		scheduleSetupTimeout(setup);
		syncSetup(setup);
		playerA.message("Death Match setup opened with " + playerB.getUsername() + ".");
		playerB.message("Death Match setup opened with " + playerA.getUsername() + ".");
	}

	private void updateSetupRules(Player player, MatchRules requestedRules) {
		DeathMatchSetup setup = activeSetups.get(player.getUsernameHash());
		if (setup == null) {
			player.message("You are not setting up a Death Match.");
			return;
		}
		synchronized (this) {
			if (activeSetups.get(player.getUsernameHash()) != setup) {
				return;
			}
			if (setup.expired()) {
				expireSetup(setup);
				return;
			}
			Player playerA = world.getPlayer(setup.playerAHash);
			Player playerB = world.getPlayer(setup.playerBHash);
			RankedAdmission admission = requestedRules.ranked
				? evaluateRankedAdmission(playerA, playerB, System.currentTimeMillis()) : null;
			if (requestedRules.ranked && (admission == null || !admission.eligible())) {
				player.message(rankedDenialMessage(admission == null
					? VoidArenaRankedPolicy.PairEligibility.DATABASE_UNAVAILABLE : admission.eligibility));
				requestedRules = MatchRules.unrankedDefault();
			}
			setup.rules = requestedRules;
			setup.playerAAccepted = false;
			setup.playerBAccepted = false;
			setup.playerAConfirmed = false;
			setup.playerBConfirmed = false;
			setup.confirmPhase = false;
			syncSetup(setup);
		}
	}

	private void acceptSetup(Player player) {
		DeathMatchSetup setup = activeSetups.get(player.getUsernameHash());
		if (setup == null) {
			player.message("You are not setting up a Death Match.");
			return;
		}
		synchronized (this) {
			if (activeSetups.get(player.getUsernameHash()) != setup) {
				return;
			}
			if (setup.expired()) {
				expireSetup(setup);
				return;
			}
			Player playerA = world.getPlayer(setup.playerAHash);
			Player playerB = world.getPlayer(setup.playerBHash);
			if (!canStartSetup(setup, playerA, playerB, player, true, false)) {
				syncSetup(setup);
				return;
			}
			if (player.getUsernameHash() == setup.playerAHash) {
				setup.playerAAccepted = true;
			} else {
				setup.playerBAccepted = true;
			}
			if (setup.playerAAccepted && setup.playerBAccepted) {
				setup.confirmPhase = true;
				setup.playerAConfirmed = false;
				setup.playerBConfirmed = false;
			}
			syncSetup(setup);
		}
	}

	private void confirmSetup(Player player) {
		DeathMatchSetup setup = activeSetups.get(player.getUsernameHash());
		if (setup == null) {
			player.message("You are not setting up a Death Match.");
			return;
		}
		synchronized (this) {
			if (activeSetups.get(player.getUsernameHash()) != setup) {
				return;
			}
			if (setup.expired()) {
				expireSetup(setup);
				return;
			}
			if (!setup.confirmPhase || !setup.playerAAccepted || !setup.playerBAccepted) {
				player.message("Both players must accept the Death Match first.");
				syncSetup(setup);
				return;
			}
			Player playerA = world.getPlayer(setup.playerAHash);
			Player playerB = world.getPlayer(setup.playerBHash);
			if (!canStartSetup(setup, playerA, playerB, player, true, true)) {
				syncSetup(setup);
				return;
			}
			if (player.getUsernameHash() == setup.playerAHash) {
				setup.playerAConfirmed = true;
			} else {
				setup.playerBConfirmed = true;
			}
			if (!setup.playerAConfirmed || !setup.playerBConfirmed) {
				syncSetup(setup);
				return;
			}
			// Clear setup before startMatch emits countdown; reversing these controls makes
			// stateful clients interpret the newly started match as already ended.
			sendArenaControl(playerA, "close");
			sendArenaControl(playerB, "close");
			if (!startMatch(playerA, playerB, setup.rules)) {
				setup.playerAConfirmed = false;
				setup.playerBConfirmed = false;
				syncSetup(setup);
				return;
			}
			removeSetup(setup);
		}
	}

	private boolean cancelSetup(Player player, boolean notify) {
		DeathMatchSetup setup = activeSetups.get(player.getUsernameHash());
		if (setup == null) {
			return false;
		}
		synchronized (this) {
			if (activeSetups.get(player.getUsernameHash()) != setup) {
				return false;
			}
			removeSetup(setup);
		}
		Player playerA = world.getPlayer(setup.playerAHash);
		Player playerB = world.getPlayer(setup.playerBHash);
		sendArenaControl(playerA, "close");
		sendArenaControl(playerB, "close");
		if (notify) {
			Player opponent = setup.opponent(player);
			if (opponent != null) {
				opponent.message(player.getUsername() + " declined the Death Match.");
			}
			if (player.loggedIn()) {
				player.message("Death Match cancelled.");
			}
		}
		return true;
	}

	private void scheduleSetupTimeout(final DeathMatchSetup setup) {
		world.getServer().getGameEventHandler().add(new SingleEvent(world, null,
			VoidArenaConfig.SETUP_TIMEOUT_MS, "Void Arena Setup Timeout", DuplicationStrategy.ALLOW_MULTIPLE) {
			@Override
			public void action() {
				expireSetup(setup);
			}
		});
	}

	private synchronized void expireSetup(DeathMatchSetup setup) {
		if (activeSetups.get(setup.playerAHash) != setup
			|| activeSetups.get(setup.playerBHash) != setup) {
			return;
		}
		removeSetup(setup);
		Player playerA = world.getPlayer(setup.playerAHash);
		Player playerB = world.getPlayer(setup.playerBHash);
		sendArenaControl(playerA, "close");
		sendArenaControl(playerB, "close");
		if (playerA != null) {
			playerA.message("Death Match setup expired.");
		}
		if (playerB != null) {
			playerB.message("Death Match setup expired.");
		}
	}

	private void syncSetup(DeathMatchSetup setup) {
		Player playerA = world.getPlayer(setup.playerAHash);
		Player playerB = world.getPlayer(setup.playerBHash);
		if (playerA == null || playerB == null) {
			removeSetup(setup);
			sendArenaControl(playerA, "close");
			sendArenaControl(playerB, "close");
			return;
		}
		sendSetupPayload(playerA, setup, playerB, setup.playerAAccepted, setup.playerBAccepted,
			setup.playerAConfirmed, setup.playerBConfirmed);
		sendSetupPayload(playerB, setup, playerA, setup.playerBAccepted, setup.playerAAccepted,
			setup.playerBConfirmed, setup.playerAConfirmed);
	}

	private void sendSetupPayload(Player viewer, DeathMatchSetup setup, Player opponent,
								  boolean viewerAccepted, boolean opponentAccepted,
								  boolean viewerConfirmed, boolean opponentConfirmed) {
		sendArenaControl(viewer, "setup|" + opponent.getIndex() + "|" + opponent.getUsername()
			+ "|" + setup.rules.mask()
			+ "|" + boolFlag(viewerAccepted)
			+ "|" + boolFlag(opponentAccepted)
			+ "|" + boolFlag(setup.confirmPhase)
			+ "|" + boolFlag(viewerConfirmed)
			+ "|" + boolFlag(opponentConfirmed)
			+ "|" + boolFlag(setup.rankedAvailable()));
	}

	private void sendArenaControl(Player player, String payload) {
		if (player != null && player.loggedIn()) {
			ActionSender.sendMessage(player, "@vsarena@" + payload);
		}
	}

	private String boolFlag(boolean value) {
		return value ? "1" : "0";
	}

	private void removeSetup(DeathMatchSetup setup) {
		activeSetups.remove(setup.playerAHash, setup);
		activeSetups.remove(setup.playerBHash, setup);
	}

	private boolean canOpenSetup(Player challenger, Player target, boolean message) {
		if (challenger.equals(target)) {
			if (message) challenger.message("You cannot challenge yourself.");
			return false;
		}
		if (!isInsideLobby(challenger) || !isInsideLobby(target)) {
			if (message) challenger.message("Both players must be in the Void Arena lobby.");
			return false;
		}
		if (challenger.inCombat() || target.inCombat()) {
			if (message) challenger.message("Both players must be out of combat.");
			return false;
		}
		if (challenger.getCurrentPoisonPower() > 0 || target.getCurrentPoisonPower() > 0) {
			if (message) challenger.message("Both players must cure poison before starting a Death Match.");
			return false;
		}
		if (challenger.isSkulled() || target.isSkulled()) {
			if (message) challenger.message("Both players must wait for any skull to expire before starting a Death Match.");
			return false;
		}
		if (activeMatches.containsKey(challenger.getUsernameHash())
			|| activeMatches.containsKey(target.getUsernameHash())
			|| activeDmKingChallenges.containsKey(challenger.getUsernameHash())
			|| activeDmKingChallenges.containsKey(target.getUsernameHash())) {
			if (message) challenger.message("One of you is already in a Death Match.");
			return false;
		}
		if (activeSetups.containsKey(challenger.getUsernameHash())
			|| activeSetups.containsKey(target.getUsernameHash())) {
			if (message) challenger.message("One of you is already setting up a Death Match.");
			return false;
		}
		if (challenger.getParty() != null && target.getParty() != null && challenger.getParty() == target.getParty()) {
			if (message) challenger.message("Leave your party before starting a Death Match.");
			return false;
		}
		if (!hasAvailableSlot()) {
			if (message) challenger.message("All Void Arena cages are occupied right now.");
			return false;
		}
		if (!supportsDeathMatchSetup(challenger) || !supportsDeathMatchSetup(target)) {
			if (message) challenger.message("Both players need the current Voidscape client for Death Match setup.");
			return false;
		}
		return true;
	}

	private boolean canStartSetup(DeathMatchSetup setup, Player playerA, Player playerB,
								  Player messenger, boolean message) {
		return canStartSetup(setup, playerA, playerB, messenger, message, true);
	}

	private boolean canStartSetup(DeathMatchSetup setup, Player playerA, Player playerB,
								  Player messenger, boolean message, boolean checkLoadouts) {
		if (setup == null || setup.expired()) {
			if (message && messenger != null) messenger.message("That Death Match setup has expired.");
			return false;
		}
		if (playerA == null || playerB == null) {
			if (message && messenger != null) messenger.message("That player is no longer online.");
			return false;
		}
		if (!isInsideLobby(playerA) || !isInsideLobby(playerB)) {
			if (message && messenger != null) messenger.message("Both players must stay in the Void Arena lobby.");
			return false;
		}
		if (playerA.inCombat() || playerB.inCombat()) {
			if (message && messenger != null) messenger.message("Both players must be out of combat.");
			return false;
		}
		if (playerA.getCurrentPoisonPower() > 0 || playerB.getCurrentPoisonPower() > 0) {
			if (message && messenger != null) messenger.message("Both players must cure poison before starting a Death Match.");
			return false;
		}
		if (playerA.isSkulled() || playerB.isSkulled()) {
			if (message && messenger != null) messenger.message("Both players must wait for any skull to expire before starting a Death Match.");
			return false;
		}
		if (activeMatches.containsKey(playerA.getUsernameHash())
			|| activeMatches.containsKey(playerB.getUsernameHash())
			|| activeDmKingChallenges.containsKey(playerA.getUsernameHash())
			|| activeDmKingChallenges.containsKey(playerB.getUsernameHash())) {
			if (message && messenger != null) messenger.message("One of you is already in a Death Match.");
			return false;
		}
		if (playerA.getParty() != null && playerB.getParty() != null && playerA.getParty() == playerB.getParty()) {
			if (message && messenger != null) messenger.message("Leave your party before starting a Death Match.");
			return false;
		}
		if (!hasAvailableSlot()) {
			if (message && messenger != null) messenger.message("All Void Arena cages are occupied right now.");
			return false;
		}
		if (setup.rules.ranked) {
			RankedAdmission admission = evaluateRankedAdmission(playerA, playerB, System.currentTimeMillis());
			if (!admission.eligible()) {
				if (message && messenger != null) {
					messenger.message(rankedDenialMessage(admission.eligibility));
				}
				return false;
			}
		}
		if (!checkLoadouts || !setup.rules.f2pOnly) {
			return true;
		}
		boolean playerALoadoutValid = hasF2PLoadout(playerA, playerB, setup.rules.ranked);
		boolean playerBLoadoutValid = hasF2PLoadout(playerB, playerA, setup.rules.ranked);
		return playerALoadoutValid && playerBLoadoutValid;
	}

	private boolean canStartDmKingChallenge(Player player, boolean message) {
		if (player == null) {
			return false;
		}
		if (player.getCache().hasKey(DM_KING_KIT_CACHE)) {
			recoverDmKingKit(player);
			if (message && !player.getCache().hasKey(DM_KING_KIT_CACHE)) {
				player.message("Your previous " + DM_KING_DISPLAY_NAME
					+ " challenge kit was restored. Challenge him again when ready.");
			}
			return false;
		}
		if (!isInsideLobby(player)) {
			if (message) player.message("You must be in the Void Arena lobby to challenge " + DM_KING_DISPLAY_NAME + ".");
			return false;
		}
		if (player.inCombat()) {
			if (message) player.message("You must be out of combat to challenge " + DM_KING_DISPLAY_NAME + ".");
			return false;
		}
		if (player.getCurrentPoisonPower() > 0) {
			if (message) player.message("Cure your poison before challenging " + DM_KING_DISPLAY_NAME + ".");
			return false;
		}
		if (player.isSkulled()) {
			if (message) player.message("Wait for your skull to expire before challenging " + DM_KING_DISPLAY_NAME + ".");
			return false;
		}
		if (activeMatches.containsKey(player.getUsernameHash())
			|| activeDmKingChallenges.containsKey(player.getUsernameHash())) {
			if (message) player.message("You are already in a Void Arena fight.");
			return false;
		}
		if (activeSetups.containsKey(player.getUsernameHash())) {
			if (message) player.message("Finish or cancel your Death Match setup first.");
			return false;
		}
		if (hasPendingChallenge(player)) {
			if (message) player.message("Answer or cancel your pending Death Match challenge first.");
			return false;
		}
		if (!hasAvailableSlot()) {
			if (message) player.message("All Void Arena cages are occupied right now.");
			return false;
		}
		if (!supportsDmKingChallenge(player)) {
			if (message) player.message("You need the current Voidscape client to challenge " + DM_KING_DISPLAY_NAME + ".");
			return false;
		}
		if (!hasRankedStats(player, message)) {
			return false;
		}
		return true;
	}

	private boolean supportsDmKingChallenge(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= DM_KING_CLIENT_VERSION;
	}

	private boolean hasPendingChallenge(Player player) {
		long hash = player.getUsernameHash();
		Challenge incoming = incomingChallenges.get(hash);
		if (incoming != null) {
			if (!incoming.expired()) {
				return true;
			}
			incomingChallenges.remove(hash, incoming);
		}
		Map<Long, Challenge> copy = new HashMap<>(incomingChallenges);
		for (Map.Entry<Long, Challenge> entry : copy.entrySet()) {
			Challenge challenge = entry.getValue();
			if (challenge.expired()) {
				incomingChallenges.remove(entry.getKey(), challenge);
			} else if (challenge.challengerHash == hash) {
				return true;
			}
		}
		return false;
	}

	private int applyDmKingKit(Player player) {
		VoidArenaKitSnapshot.clearContainers(player);
		player.exitMorph();
		player.getSkills().setLevel(Skill.ATTACK.id(),
			player.getSkills().getMaxStat(Skill.ATTACK.id()), false);
		player.getSkills().setLevel(Skill.STRENGTH.id(),
			player.getSkills().getMaxStat(Skill.STRENGTH.id()), false);
		player.getSkills().setLevel(Skill.DEFENSE.id(),
			player.getSkills().getMaxStat(Skill.DEFENSE.id()), false);
		if (!equipChallengeItem(player, ItemId.LARGE_RUNE_HELMET.id())
			|| !equipChallengeItem(player, ItemId.RUNE_PLATE_MAIL_BODY.id())
			|| !equipChallengeItem(player, ItemId.RUNE_PLATE_MAIL_LEGS.id())
			|| !equipChallengeItem(player, ItemId.DIAMOND_AMULET_OF_POWER.id())
			|| !equipChallengeItem(player, ItemId.RUNE_2_HANDED_SWORD.id())) {
			return -1;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		if (!inventory.add(new Item(ItemId.FULL_STRENGTH_POTION.id()), false)
			|| !inventory.add(new Item(ItemId.AIR_RUNE.id(), DM_KING_AIR_RUNES), false)
			|| !inventory.add(new Item(ItemId.DEATH_RUNE.id(), DM_KING_DEATH_RUNES), false)
			|| !inventory.add(new Item(ItemId.FIRE_RUNE.id(), DM_KING_FIRE_RUNES), false)) {
			return -1;
		}
		int playerFishCapacity = Math.min(DM_KING_FISH_COUNT,
			inventory.getFreeSlots());
		for (int i = 0; i < playerFishCapacity; i++) {
			if (!inventory.add(new Item(ItemId.SWORDFISH.id()), false)) {
				return -1;
			}
		}
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
		player.getSkills().sendUpdateAll();
		player.getUpdateFlags().setAppearanceChanged(true);
		return playerFishCapacity;
	}

	private boolean equipChallengeItem(Player player, int itemId) {
		if (!player.getCarriedItems().getInventory().add(new Item(itemId), false)) {
			return false;
		}
		Item equipped = player.getCarriedItems().getInventory()
			.get(player.getCarriedItems().getInventory().size() - 1);
		if (equipped == null) {
			return false;
		}
		equipped.setWielded(true);
		ItemDefinition def = equipped.getDef(player.getWorld());
		if (def == null || def.getWieldPosition() < 0 || def.getWieldPosition() >= 12) {
			return false;
		}
		player.updateWornItems(def.getWieldPosition(), def.getAppearanceId(), def.getWearableId(), true);
		return true;
	}

	private void resolveDmKingVictory(DmKingChallenge challenge, Player player) {
		if (challenge == null || !challenge.tryFinish()) {
			return;
		}
		player = player == null ? challenge.player : player;
		DmKingSummary summary = captureDmKingSummaryBestEffort(challenge, player, "victory");
		cleanupDmKingChallengeTerminal(challenge, "victory");
		finishDmKingSession(player, true);
		DmKingRecord record = recordDmKingResultBestEffort(false, "victory");
		if (player != null && player.loggedIn()) {
			player.playerServerMessage(MessageType.QUEST,
				"@mag@" + DM_KING_DISPLAY_NAME + " defeated: @whi@You beat the perfect death matcher.");
			player.playerServerMessage(MessageType.QUEST,
				"@yel@" + DM_KING_DISPLAY_NAME + ": " + randomLine(DM_KING_RARE_LOSS_QUIPS));
			sendDmKingSummary(player, summary);
			sendDmKingRecordPayload(player, record);
		}
		broadcastDmKingRecord(record);
		if (player != null && player.loggedIn()
			&& !player.getCache().hasKey(DM_KING_BROADCAST_CACHE)) {
			player.getCache().store(DM_KING_BROADCAST_CACHE, true);
			savePlayerCache(player);
			world.sendWorldMessage("@mag@" + player.getUsername() + " has defeated " + DM_KING_DISPLAY_NAME + " in the Void Arena!");
		}
	}

	private void resolveDmKingLoss(DmKingChallenge challenge, Player player, String message, boolean disconnectLoss,
								   boolean recordDmKingWin) {
		resolveDmKingLoss(challenge, player, message, disconnectLoss, recordDmKingWin, false);
	}

	private void resolveDmKingLoss(DmKingChallenge challenge, Player player, String message, boolean disconnectLoss,
								   boolean recordDmKingWin, boolean deferDeathRestore) {
		if (challenge == null || !challenge.tryFinish()) {
			return;
		}
		player = player == null ? challenge.player : player;
		DmKingSummary summary = captureDmKingSummaryBestEffort(challenge, player, "loss");
		cleanupDmKingChallengeTerminal(challenge, "loss");
		if (deferDeathRestore) {
			sendArenaControlBestEffort(player, "close", "death recovery");
		} else {
			finishDmKingSession(player, !disconnectLoss);
		}
		DmKingRecord record = recordDmKingWin
			? recordDmKingResultBestEffort(true, "loss") : null;
		if (message != null && !message.isEmpty() && player != null && player.loggedIn()) {
			player.playerServerMessage(MessageType.QUEST, "@mag@" + DM_KING_DISPLAY_NAME + " challenge ended: @whi@" + message);
			if (recordDmKingWin) {
				player.playerServerMessage(MessageType.QUEST,
					"@yel@" + DM_KING_DISPLAY_NAME + ": " + randomLine(DM_KING_WIN_QUIPS));
			}
			sendDmKingSummary(player, summary);
			sendDmKingRecordPayload(player, record);
		}
		if (record != null) {
			broadcastDmKingRecord(record);
		}
	}

	/** Completes Sir kit restoration after generic death normalization has finished. */
	public void finishDeferredDmKingDeathRecovery(Player player) {
		if (player == null) {
			return;
		}
		try {
			if (!hasPendingDmKingKitRecovery(player)) {
				return;
			}
			try {
				player.setInstanceId(0);
			} catch (RuntimeException e) {
				LOGGER.error("Unable to clear {} instance for {} after death",
					DM_KING_DISPLAY_NAME, player.getUsername(), e);
			}
			final boolean recoveryCommitted;
			try {
				recoveryCommitted = restoreDmKingKit(player);
			} catch (IllegalStateException e) {
				LOGGER.error("Unable to restore {} kit for {} after death", DM_KING_DISPLAY_NAME,
					player.getUsername(), e);
				player.message("Your saved real kit could not be validated. You will be logged out; contact staff.");
				player.unregister(UnregisterForcefulness.FORCED,
					"Invalid Sir Charles kit snapshot after death");
				return;
			}
			if (!recoveryCommitted) {
				player.message("Your real kit recovery could not be finalized. You will be logged out to keep it safe.");
				player.unregister(UnregisterForcefulness.FORCED,
					"Sir Charles post-death kit recovery persistence failed");
			}
		} finally {
			releaseDmKingSaveReservation(player, "post-death recovery");
		}
	}

	private void resolveDmKingNoContest(DmKingChallenge challenge, Player player) {
		if (challenge == null || !challenge.tryFinish()) {
			return;
		}
		player = player == null ? challenge.player : player;
		cleanupDmKingChallengeTerminal(challenge, "server no-contest");
		finishDmKingSession(player, true);
		if (player != null && player.loggedIn()) {
			player.playerServerMessage(MessageType.QUEST,
				"@mag@" + DM_KING_DISPLAY_NAME + " challenge ended: @whi@server shutdown - no contest.");
		}
	}

	private void expireDmKingChallenge(DmKingChallenge challenge) {
		if (activeDmKingChallenges.get(challenge.playerHash) != challenge) {
			return;
		}
		resolveDmKingLoss(challenge, challenge.player, "time limit reached.", false, true);
	}

	private void finishDmKingSession(Player player, boolean moveToLobby) {
		if (player == null) {
			return;
		}
		try {
			sendArenaControlBestEffort(player, "close", "session finish");
			try {
				player.resetAll();
				player.setInstanceId(0);
				if (moveToLobby) {
					player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
				} else {
					player.setLocation(VoidArenaConfig.exitTile(), true);
				}
			} catch (RuntimeException e) {
				LOGGER.error("Unable to relocate {} while finishing {} session",
					player.getUsername(), DM_KING_DISPLAY_NAME, e);
				try {
					player.setInstanceId(0);
					player.setLocation(moveToLobby
						? VoidArenaConfig.lobbyTile() : VoidArenaConfig.exitTile(), true);
				} catch (RuntimeException fallbackError) {
					LOGGER.error("Unable to apply fallback {} terminal relocation for {}",
						DM_KING_DISPLAY_NAME, player.getUsername(), fallbackError);
				}
			}
			final boolean recoveryCommitted;
			try {
				recoveryCommitted = restoreDmKingKit(player);
			} catch (IllegalStateException e) {
				LOGGER.error("Unable to restore {} kit for {} at session end", DM_KING_DISPLAY_NAME,
					player.getUsername(), e);
				player.message("Your saved real kit could not be validated. You will be logged out; contact staff.");
				player.unregister(UnregisterForcefulness.FORCED,
					"Invalid Sir Charles kit snapshot at session end");
				return;
			}
			if (!recoveryCommitted) {
				player.message("Your real kit was restored, but the recovery marker could not be cleared. "
					+ "You will be logged out to keep it safe.");
				player.unregister(UnregisterForcefulness.FORCED,
					"Sir Charles kit recovery persistence failed");
			}
			try {
				ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
				ActionSender.sendInventory(player);
				ActionSender.sendEquipmentStats(player);
			} catch (RuntimeException e) {
				LOGGER.error("Unable to send final {} restoration packets to {}",
					DM_KING_DISPLAY_NAME, player.getUsername(), e);
			}
		} finally {
			releaseDmKingSaveReservation(player, "session finish");
		}
	}

	private void releaseDmKingSaveReservation(Player player, String context) {
		if (player == null) {
			return;
		}
		try {
			player.releaseSaveReservation();
		} catch (RuntimeException e) {
			LOGGER.error("Unable to release {} save reservation for {} during {}",
				DM_KING_DISPLAY_NAME, player.getUsername(), context, e);
		}
	}

	private void sendArenaControlBestEffort(Player player, String payload, String context) {
		if (player == null) {
			return;
		}
		try {
			sendArenaControl(player, payload);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to send Void Arena control '{}' to {} during {}",
				payload, player.getUsername(), context, e);
		}
	}

	private DmKingSummary captureDmKingSummaryBestEffort(DmKingChallenge challenge,
		Player player, String context) {
		try {
			return captureDmKingSummary(challenge, player);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to capture {} {} summary for {}",
				DM_KING_DISPLAY_NAME, context,
				player == null ? challenge.playerHash : player.getUsername(), e);
			return null;
		}
	}

	private void cleanupDmKingChallengeTerminal(DmKingChallenge challenge, String context) {
		activeDmKingChallenges.remove(challenge.playerHash, challenge);
		try {
			challenge.event.stop();
		} catch (RuntimeException e) {
			LOGGER.error("Unable to stop {} event during {}", DM_KING_DISPLAY_NAME, context, e);
		}
		try {
			challenge.player.resetTrackedDamageAndBlockedDamage(challenge.king);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to reset {} damage tracking during {}",
				DM_KING_DISPLAY_NAME, context, e);
		}
		try {
			cleanupDmKingNpc(challenge.king);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to remove {} NPC during {}", DM_KING_DISPLAY_NAME, context, e);
		}
		purgeCageGroundItemsBestEffort(challenge.slotIndex,
			DM_KING_DISPLAY_NAME + " " + context);
	}

	private DmKingRecord recordDmKingResultBestEffort(boolean dmKingWon, String context) {
		try {
			return recordDmKingResult(dmKingWon);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to record {} result during {}",
				DM_KING_DISPLAY_NAME, context, e);
			return null;
		}
	}

	private DmKingSummary captureDmKingSummary(DmKingChallenge challenge, Player player) {
		int playerFish = player == null ? 0
			: player.getCarriedItems().getInventory().countId(ItemId.SWORDFISH.id());
		int playerFishCapacity = challenge == null ? playerFish
			: Math.max(playerFish, challenge.playerFishCapacity);
		int kingFish = challenge == null ? 0 : Math.max(0, challenge.fishRemaining);
		int kingFishCapacity = challenge == null ? kingFish : challenge.fishCapacity;
		int castsUsed = challenge == null ? 0 : Math.max(0, challenge.castsUsed);
		int potionDosesRemaining = challenge == null ? 0
			: Math.max(0, challenge.strengthPotionDosesRemaining);
		int prayerStatePoints = challenge == null ? 0 : Math.max(0, challenge.prayerStatePoints);
		long castTicks = challenge == null ? dmKingCastDelayTicks() : challenge.dmKingCastDelayTicks();
		long castMs = castTicks * Math.max(1, world.getServer().getConfig().GAME_TICK);
		return new DmKingSummary(playerFish, playerFishCapacity, kingFish, kingFishCapacity,
			castTicks, castMs, castsUsed, potionDosesRemaining, prayerStatePoints);
	}

	private void sendDmKingSummary(Player player, DmKingSummary summary) {
		if (player == null || !player.loggedIn() || summary == null) {
			return;
		}
		String tickLabel = summary.castDelayTicks == 1 ? "tick" : "ticks";
		player.playerServerMessage(MessageType.QUEST,
			"@mag@" + DM_KING_DISPLAY_NAME + " supplies: @whi@Food left - you " + summary.playerFishRemaining
				+ "/" + summary.playerFishCapacity + ", " + DM_KING_DISPLAY_NAME + " " + summary.kingFishRemaining
				+ "/" + summary.kingFishCapacity + ".");
		player.playerServerMessage(MessageType.QUEST,
			"@mag@" + DM_KING_DISPLAY_NAME + " cast speed: @whi@Fire Blast every " + summary.castDelayMs
				+ "ms (" + summary.castDelayTicks + " " + tickLabel + "); casts used "
				+ summary.castsUsed + "/" + DM_KING_FIRE_BLAST_CASTS + ".");
		player.playerServerMessage(MessageType.QUEST,
			"@mag@" + DM_KING_DISPLAY_NAME + " remaining: @whi@strength potion doses "
				+ summary.potionDosesRemaining + "/" + DM_KING_STRENGTH_POTION_DOSES
				+ ", prayer " + ((summary.prayerStatePoints + 119) / 120)
				+ "/" + DM_KING_PRAYER_LEVEL + ".");
	}

	private long dmKingCastDelayTicks() {
		int gameTick = Math.max(1, world.getServer().getConfig().GAME_TICK);
		int castDelay = world.getServer().getConfig().RAPID_CAST_SPELLS
			? 0
			: Math.max(0, world.getServer().getConfig().MILLISECONDS_BETWEEN_CASTS);
		return VoidArenaSirPolicy.castDelayTicks(castDelay, gameTick);
	}

	private int dmKingPrayerDrainPerTick() {
		int totalDrainRate = 0;
		for (int prayerId : DM_KING_ACTIVE_PRAYERS) {
			if (world.getServer().getEntityHandler().getPrayerDef(prayerId) != null) {
				totalDrainRate += world.getServer().getEntityHandler()
					.getPrayerDef(prayerId).getDrainRate();
			}
		}
		return (int) Math.ceil(totalDrainRate * 120.0D / 300.0D);
	}

	private long pvpReattackDelayTicks() {
		return Math.max(0, world.getServer().getConfig().PVP_REATTACK_TIMER);
	}

	private boolean restoreDmKingKit(Player player) {
		return restoreDmKingKit(player, true);
	}

	private boolean restoreDmKingKit(Player player, boolean sendUpdates) {
		if (player == null || !player.getCache().hasKey(DM_KING_KIT_CACHE)) {
			return true;
		}
		Object storedSnapshot = player.getCache().getCacheMap().get(DM_KING_KIT_CACHE);
		if (!(storedSnapshot instanceof String)) {
			throw new IllegalStateException("Sir Charles kit snapshot has an invalid cache type");
		}
		String snapshot = (String) storedSnapshot;
		VoidArenaKitSnapshot.restore(player, snapshot);
		player.getCache().remove(DM_KING_KIT_CACHE);
		PlayerInventory[] inventory = exactInventoryForPersistence(player);
		boolean committed = world.getServer().getDatabase().atomically(() -> {
			world.getServer().getDatabase().savePlayerInventory(player.getDatabaseID(), inventory);
			world.getServer().getDatabase().querySavePlayerEquipped(player);
			world.getServer().getPlayerService().savePlayerCache(player);
			world.getServer().getDatabase().querySavePlayerData(player);
			world.getServer().getDatabase().querySavePlayerSkills(player);
		});
		if (!committed) {
			player.getCache().store(DM_KING_KIT_CACHE, snapshot);
			return false;
		}
		if (sendUpdates) {
			ActionSender.sendInventory(player);
			ActionSender.sendEquipmentStats(player);
			player.getSkills().sendUpdateAll();
			ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
		}
		return true;
	}

	private boolean saveDmKingKitMarker(Player player, String snapshot) {
		boolean committed = world.getServer().getDatabase().atomically(() ->
			world.getServer().getDatabase().querySavePlayerCacheValue(
				player.getDatabaseID(), 1, DM_KING_KIT_CACHE, snapshot));
		if (!committed) {
			LOGGER.error("Unable to save {} kit recovery marker for {}",
				DM_KING_DISPLAY_NAME, player.getUsername());
		}
		return committed;
	}

	private PlayerInventory[] exactInventoryForPersistence(Player player) {
		List<Item> items = player.getCarriedItems().getInventory().getItems();
		synchronized (items) {
			PlayerInventory[] inventory = new PlayerInventory[items.size()];
			for (int slot = 0; slot < items.size(); slot++) {
				Item item = items.get(slot);
				PlayerInventory saved = new PlayerInventory();
				saved.itemId = item.getItemId();
				saved.item = item;
				saved.wielded = item.isWielded();
				saved.slot = slot;
				saved.amount = item.getAmount();
				saved.noted = item.getNoted();
				saved.catalogID = item.getCatalogId();
				saved.durability = item.getItemStatus().getDurability();
				saved.killLog = item.getItemStatus().getKillLog();
				inventory[slot] = saved;
			}
			return inventory;
		}
	}

	private boolean savePlayerCache(Player player) {
		try {
			world.getServer().getPlayerService().savePlayerCache(player);
			return true;
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to save Void Arena player cache for {}", player.getUsername(), e);
			return false;
		}
	}

	private void cleanupDmKingNpc(Npc king) {
		if (king != null && !king.isRemoved()) {
			world.unregisterNpc(king);
		}
	}

	private DmKingChallenge dmKingChallengeFor(Npc king) {
		if (king == null) {
			return null;
		}
		Long ownerHash = king.getAttribute(DM_KING_OWNER_ATTRIBUTE, null);
		return ownerHash == null ? null : activeDmKingChallenges.get(ownerHash);
	}

	private synchronized DmKingRecord recordDmKingResult(boolean dmKingWon) {
		final DmKingRecord[] committedRecord = {null};
		boolean committed = world.getServer().getDatabase().atomically(() -> {
			Integer storedWins = world.getServer().getDatabase()
				.queryLoadGlobalCacheInt(DM_KING_WINS_CACHE);
			Integer storedLosses = world.getServer().getDatabase()
				.queryLoadGlobalCacheInt(DM_KING_LOSSES_CACHE);
			int wins = storedWins == null ? DM_KING_INITIAL_WINS : Math.max(0, storedWins);
			int losses = storedLosses == null ? DM_KING_INITIAL_LOSSES : Math.max(0, storedLosses);
			DmKingRecord next = dmKingWon
				? new DmKingRecord(Math.addExact(wins, 1), losses)
				: new DmKingRecord(wins, Math.addExact(losses, 1));
			world.getServer().getDatabase().querySaveGlobalCacheInt(DM_KING_WINS_CACHE, next.wins);
			world.getServer().getDatabase().querySaveGlobalCacheInt(DM_KING_LOSSES_CACHE, next.losses);
			committedRecord[0] = next;
		});
		if (!committed) {
			LOGGER.error("Unable to commit {} result", DM_KING_DISPLAY_NAME);
			return null;
		}
		return committedRecord[0];
	}

	private DmKingRecord loadDmKingRecord() {
		return new DmKingRecord(
			loadGlobalCacheInt(DM_KING_WINS_CACHE, DM_KING_INITIAL_WINS),
			loadGlobalCacheInt(DM_KING_LOSSES_CACHE, DM_KING_INITIAL_LOSSES));
	}

	private int loadGlobalCacheInt(String cacheKey, int defaultValue) {
		try {
			Integer value = world.getServer().getDatabase().queryLoadGlobalCacheInt(cacheKey);
			return value == null ? defaultValue : Math.max(0, value);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena global cache key {}", cacheKey, e);
			return defaultValue;
		}
	}

	private static String randomLine(String[] lines) {
		return lines[ThreadLocalRandom.current().nextInt(lines.length)];
	}

	private boolean supportsDeathMatchSetup(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= DEATH_MATCH_SETUP_CLIENT_VERSION;
	}

	private boolean isRankedEligible(Player player) {
		return hasRankedStats(player, false);
	}

	private boolean isRankedPairEligible(Player playerA, Player playerB) {
		return evaluateRankedAdmission(playerA, playerB, System.currentTimeMillis()).eligible();
	}

	private boolean sharesPublicNetworkAddress(Player playerA, Player playerB) {
		return playerA != null && playerB != null
			&& VoidArenaRankedPolicy.samePublicNetwork(playerA.getCurrentIP(), playerB.getCurrentIP());
	}

	private boolean hasAmbiguousRankedOrigin(Player player) {
		return player != null && VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(
			player.isWebSocketConnection(), player.getCurrentIP(),
			world.getServer().getConfig().VOID_ARENA_ALLOW_AMBIGUOUS_PROXY_RANKED);
	}

	private RankedAdmission evaluateRankedAdmission(Player playerA, Player playerB, long now) {
		if (playerA == null || playerB == null || playerA.getDatabaseID() <= 0 || playerB.getDatabaseID() <= 0
			|| playerA.getDatabaseID() == playerB.getDatabaseID()) {
			return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.INVALID_PLAYER);
		}
		if (!isRankedEligible(playerA) || !isRankedEligible(playerB)) {
			return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.STAT_REQUIREMENT);
		}
		if (hasAmbiguousRankedOrigin(playerA) || hasAmbiguousRankedOrigin(playerB)) {
			return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.AMBIGUOUS_PROXY_IP);
		}
		if (sharesPublicNetworkAddress(playerA, playerB)) {
			return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.SAME_PUBLIC_NETWORK);
		}

		Player canonicalA = playerA.getDatabaseID() < playerB.getDatabaseID() ? playerA : playerB;
		Player canonicalB = canonicalA == playerA ? playerB : playerA;
		String seasonId = VoidArenaConfig.currentSeasonId();
		try {
			VoidArenaStats statsA = loadRankedStats(canonicalA, seasonId);
			VoidArenaStats statsB = loadRankedStats(canonicalB, seasonId);
			long rollingCutoff = now - VoidArenaConfig.RATED_PAIR_COOLDOWN_MS;
			long dayStart = VoidArenaRankedPolicy.utcDayStart(now);
			VoidArenaPairAudit audit = world.getServer().getDatabase().queryVoidArenaPairAudit(
				canonicalA.getDatabaseID(), canonicalB.getDatabaseID(), rollingCutoff, dayStart);
			if (VoidArenaRankedPolicy.cooldownActive(audit.lastRatedResultAtMs, now)) {
				return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.RATED_RESULT_COOLDOWN);
			}
			if (VoidArenaRankedPolicy.dailyCapReached(audit.decisiveResultsUtcDay)) {
				return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.DAILY_DECISIVE_CAP);
			}
			return RankedAdmission.allowed(seasonId, canonicalA, canonicalB, statsA, statsB, audit);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to authorize ranked Void Arena pair {} and {}",
				playerA.getUsername(), playerB.getUsername(), e);
			return RankedAdmission.denied(VoidArenaRankedPolicy.PairEligibility.DATABASE_UNAVAILABLE);
		}
	}

	private VoidArenaStats loadRankedStats(Player player, String seasonId) throws GameDatabaseException {
		VoidArenaStats stats = world.getServer().getDatabase()
			.queryLoadVoidArenaStats(player.getDatabaseID(), seasonId);
		if (stats == null) {
			stats = defaultStats(player.getDatabaseID(), player.getUsername(), seasonId);
		}
		if (stats.username == null || stats.username.isEmpty()) {
			stats.username = player.getUsername();
		}
		return stats;
	}

	private String rankedDenialMessage(VoidArenaRankedPolicy.PairEligibility eligibility) {
		switch (eligibility) {
			case INVALID_PLAYER:
				return "Both players need valid saved accounts for a ranked Death Match.";
			case STAT_REQUIREMENT:
				return "Ranked Death Match requires both players to have 99 Attack, Strength, and Defense.";
			case AMBIGUOUS_PROXY_IP:
				return "Ranked Death Match is unavailable on a WebSocket route that hides your public IP. Use a direct game connection; unranked remains available.";
			case SAME_PUBLIC_NETWORK:
				return "Ranked Death Matches are unavailable between players on the same public network.";
			case RATED_RESULT_COOLDOWN:
				return "This pair must wait 30 minutes after its last rated result. Unranked remains available.";
			case DAILY_DECISIVE_CAP:
				return "This pair has reached today's limit of three rated results. Unranked remains available.";
			case DATABASE_UNAVAILABLE:
				return "Ranked Death Matches are temporarily unavailable because rating history could not be verified.";
			default:
				return "Ranked Death Match is unavailable for this pair.";
		}
	}

	private boolean hasRankedStats(Player player, boolean message) {
		if (player.getSkills().getMaxStat(Skill.ATTACK.id()) < 99
			|| player.getSkills().getMaxStat(Skill.STRENGTH.id()) < 99
			|| player.getSkills().getMaxStat(Skill.DEFENSE.id()) < 99) {
			if (message) {
				player.message("Ranked Death Match requires 99 Attack, Strength, and Defense.");
			}
			return false;
		}
		return true;
	}

	private boolean hasF2PLoadout(Player player, Player opponent, boolean ranked) {
		String prefix = ranked ? "Ranked Death Match" : "F2P Death Match";
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (isMembersItem(player, item)) {
					String itemName = item.getDef(player.getWorld()).getName();
					player.message(prefix + " allows F2P items only. Bank " + itemName + ".");
					if (opponent != null) {
						opponent.message(player.getUsername() + " needs to bank " + itemName + " for this Death Match.");
					}
					return false;
				}
			}
		}
		synchronized (player.getCarriedItems().getEquipment().getList()) {
			for (Item item : player.getCarriedItems().getEquipment().getList()) {
				if (isMembersItem(player, item)) {
					String itemName = item.getDef(player.getWorld()).getName();
					player.message(prefix + " allows F2P gear only. Remove " + itemName + ".");
					if (opponent != null) {
						opponent.message(player.getUsername() + " needs to remove " + itemName + " for this Death Match.");
					}
					return false;
				}
			}
		}
		return true;
	}

	private boolean isMembersItem(Player player, Item item) {
		if (item == null) {
			return false;
		}
		ItemDefinition def = item.getDef(player.getWorld());
		return def != null && def.isMembersOnly();
	}

	private synchronized boolean startMatch(Player playerA, Player playerB, MatchRules rules) {
		RankedAdmission admission = null;
		if (rules.ranked) {
			admission = evaluateRankedAdmission(playerA, playerB, System.currentTimeMillis());
			if (!admission.eligible()) {
				String denial = rankedDenialMessage(admission.eligibility);
				playerA.message(denial);
				playerB.message(denial);
				return false;
			}
		}
		int slotIndex = firstAvailableSlot();
		if (slotIndex < 0) {
			playerA.message("All Void Arena cages are occupied right now.");
			playerB.message("All Void Arena cages are occupied right now.");
			return false;
		}

		VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
		Match match = new Match(slotIndex, playerA, playerB, rules,
			System.currentTimeMillis() + MATCH_COUNTDOWN_MS, admission);
		if (rules.ranked && !world.getServer().getDatabase()
			.createActiveVoidArenaMatch(match.activeSessionRecord())) {
			playerA.message("Ranked Death Match could not be started because its audit record was not saved.");
			playerB.message("Ranked Death Match could not be started because its audit record was not saved.");
			return false;
		}
		activeMatches.put(playerA.getUsernameHash(), match);
		activeMatches.put(playerB.getUsernameHash(), match);
		try {
			purgeCageGroundItems(slotIndex);
			removeChallenges(playerA);
			removeChallenges(playerB);

			playerA.resetAll();
			playerB.resetAll();
			if (!rules.allowPrayer) {
				playerA.getPrayers().resetPrayers();
				playerB.getPrayers().resetPrayers();
			}
			restoreHits(playerA);
			restoreHits(playerB);
			playerA.setInstanceId(0);
			playerB.setInstanceId(0);
			playerA.teleportFromVoidArena(slot.startAX, slot.startAY, true);
			playerB.teleportFromVoidArena(slot.startBX, slot.startBY, true);
			sendArenaControl(playerA, "countdown|5");
			sendArenaControl(playerB, "countdown|5");
			playerA.message("@red@" + rules.title() + " begins in 5 seconds. Defeat " + playerB.getUsername() + ".");
			playerB.message("@red@" + rules.title() + " begins in 5 seconds. Defeat " + playerA.getUsername() + ".");
			playerA.message("Rules: " + rules.summary() + ".");
			playerB.message("Rules: " + rules.summary() + ".");
			playerA.message(rules.title() + " ends after " + rules.timeoutMinutes() + " minutes if nobody wins.");
			playerB.message(rules.title() + " ends after " + rules.timeoutMinutes() + " minutes if nobody wins.");
			scheduleMatchTimeout(match);
			return true;
		} catch (RuntimeException e) {
			LOGGER.error("Void Arena match {} failed after its durable admission", match.sessionId, e);
			resolveNeutralMatch(match, VoidArenaRankedPolicy.ResultType.SERVER_RESTART_NO_CONTEST,
				"server setup failed - no contest.");
			return false;
		}
	}

	private void scheduleMatchTimeout(final Match match) {
		int delay = (int) Math.max(1L,
			match.startsAt + match.rules.timeoutMs() - System.currentTimeMillis());
		world.getServer().getGameEventHandler().add(new SingleEvent(world, null, delay,
			"Void Arena Match Timeout", DuplicationStrategy.ALLOW_MULTIPLE) {
			public void action() {
				expireTimedMatch(match);
			}
		});
	}

	private synchronized void expireTimedMatch(Match match) {
		if (activeMatches.get(match.playerAHash) != match
			|| activeMatches.get(match.playerBHash) != match) {
			return;
		}
		Player disconnected = match.disconnectedPlayer();
		if (disconnected != null) {
			resolveMatch(match, match.opponent(disconnected), disconnected,
				VoidArenaRankedPolicy.ResultType.FORFEIT);
			return;
		}
		resolveNeutralMatch(match, VoidArenaRankedPolicy.ResultType.TIMEOUT_DRAW,
			"time limit reached; the match is a draw.");
	}

	private boolean hasAvailableSlot() {
		return firstAvailableSlot() >= 0;
	}

	private int firstAvailableSlot() {
		boolean[] occupied = new boolean[VoidArenaConfig.arenaSlotCount()];
		for (Match match : activeMatches.values()) {
			if (match.slotIndex >= 0 && match.slotIndex < occupied.length) {
				occupied[match.slotIndex] = true;
			}
		}
		for (DmKingChallenge challenge : activeDmKingChallenges.values()) {
			if (challenge.slotIndex >= 0 && challenge.slotIndex < occupied.length) {
				occupied[challenge.slotIndex] = true;
			}
		}
		for (int i = 0; i < occupied.length; i++) {
			if (!occupied[i]) {
				return i;
			}
		}
		return -1;
	}

	private void purgeCageGroundItems(int slotIndex) {
		VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
		for (GroundItem item : world.getRegionManager().getGroundItemsInBounds(
			slot.minX, slot.minY, slot.maxX, slot.maxY, 0)) {
			if (item.getLoc() == null && !item.isRemoved()) {
				world.unregisterItem(item);
			}
		}
	}

	private void purgeCageGroundItemsBestEffort(int slotIndex, String context) {
		try {
			purgeCageGroundItems(slotIndex);
		} catch (RuntimeException e) {
			LOGGER.error("Unable to purge Void Arena cage {} during {}",
				slotIndex, context, e);
		}
	}

	private synchronized void resolveMatch(Match match, Player winner, Player loser,
						  VoidArenaRankedPolicy.ResultType resultType) {
		if (match == null) {
			return;
		}
		MatchTerminalClaim claim = match.claimTerminal(winner, loser, resultType);
		if (claim == null) {
			return;
		}
		resolveClaimedMatch(match, claim.winner, claim.loser, claim.resultType);
	}

	private void resolveClaimedMatch(Match match, Player winner, Player loser,
		VoidArenaRankedPolicy.ResultType resultType) {
		if (winner == null || loser == null) {
			VoidArenaSettlementStatus settlement = VoidArenaSettlementStatus.SETTLED;
			if (match.rules.ranked) {
				settlement = beginNeutralRankedSettlement(match,
					VoidArenaRankedPolicy.ResultType.SERVER_RESTART_NO_CONTEST);
			} else {
				releaseMatchLock(match);
			}
			purgeCageGroundItemsBestEffort(match.slotIndex, "missing-participant settlement");
			returnNeutralPlayer(winner, match.rules, "participant unavailable - no contest.", settlement);
			returnNeutralPlayer(loser, match.rules, "participant unavailable - no contest.", settlement);
			return;
		}

		if (!match.rules.ranked) {
			purgeCageGroundItemsBestEffort(match.slotIndex, "unranked terminal settlement");
			releaseMatchLock(match);
			returnToLobby(winner);
			finishArenaSession(loser, false);
			runArenaTerminalActionBestEffort(winner, "send unranked victory result", () ->
				winner.playerServerMessage(MessageType.QUEST,
					"@mag@Unranked Death Match win: @whi@You defeated " + loser.getUsername() + "."));
			runArenaTerminalActionBestEffort(loser, "send unranked loss result", () ->
				loser.playerServerMessage(MessageType.QUEST,
					"@mag@Unranked Death Match loss: @whi@" + winner.getUsername() + " defeated you."));
			if (resultType == VoidArenaRankedPolicy.ResultType.FORFEIT) {
				runArenaTerminalActionBestEffort(winner, "send unranked forfeit result", () ->
					winner.message(loser.getUsername() + " forfeited by leaving the Death Match."));
			}
			return;
		}

		try {
			VoidArenaStats statsA = copyStats(match.rankedAdmission.playerAStats);
			VoidArenaStats statsB = copyStats(match.rankedAdmission.playerBStats);
			boolean winnerIsA = winner.getDatabaseID() == match.rankedAdmission.playerA.getDatabaseID();
			VoidArenaStats winnerStats = winnerIsA ? statsA : statsB;
			VoidArenaStats loserStats = winnerIsA ? statsB : statsA;
			int oldWinnerRating = winnerStats.rating;
			int oldLoserRating = loserStats.rating;
			int delta = VoidArenaRankedPolicy.ratingTransfer(oldWinnerRating, oldLoserRating);

			winnerStats.rating = oldWinnerRating + delta;
			winnerStats.wins++;
			loserStats.rating = oldLoserRating - delta;
			loserStats.losses++;
			if (resultType == VoidArenaRankedPolicy.ResultType.FORFEIT) {
				loserStats.disconnectLosses++;
			}
			long candidateEndedAt = resultType == VoidArenaRankedPolicy.ResultType.FORFEIT
				&& match.disconnectedAtMs() > 0L
				? match.disconnectedAtMs() : System.currentTimeMillis();
			long endedAt = Math.max(match.createdAt, candidateEndedAt);
			loserStats.updatedAt = endedAt;
			winnerStats.updatedAt = endedAt;
			VoidArenaMatchSessionRecord settledRecord = match.settledSessionRecord(resultType,
				winner.getDatabaseID(), loser.getDatabaseID(), statsA.rating, statsB.rating,
				statsA.rating - match.activeSessionRecord().playerARatingBefore, true, endedAt);
			match.setPendingSettlement(PendingRankedSettlement.decisive(settledRecord,
				statsA, statsB, resultType, winner.getUsernameHash(), loser.getUsernameHash(),
				winnerIsA, delta, oldWinnerRating, oldLoserRating));
		} catch (RuntimeException e) {
			LOGGER.fatal("Unable to prepare terminal ranked settlement {}; recording no contest",
				match.sessionId, e);
			VoidArenaSettlementStatus neutralSettlement = beginNeutralRankedSettlement(match,
				VoidArenaRankedPolicy.ResultType.SERVER_RESTART_NO_CONTEST);
			purgeCageGroundItemsBestEffort(match.slotIndex,
				"ranked terminal-preparation failure");
			returnNeutralPlayer(winner, match.rules,
				"result preparation failed - no contest.", neutralSettlement);
			returnNeutralPlayer(loser, match.rules,
				"result preparation failed - no contest.", neutralSettlement);
			return;
		}
		VoidArenaSettlementStatus settlement = attemptRankedSettlement(match);
		purgeCageGroundItemsBestEffort(match.slotIndex, "ranked terminal settlement");

		returnToLobby(winner);
		finishArenaSession(loser, false);
		if (settlement == VoidArenaSettlementStatus.DATABASE_ERROR) {
			String pending = "The ranked result is still being finalized; neither fighter can start another arena match until it is durable.";
			runArenaTerminalActionBestEffort(winner, "send pending ranked-result notice", () ->
				winner.message(pending));
			runArenaTerminalActionBestEffort(loser, "send pending ranked-result notice", () ->
				loser.message(pending));
			return;
		}
	}

	private synchronized void resolveNeutralMatch(Match match,
		VoidArenaRankedPolicy.ResultType resultType, String message) {
		if (match == null) {
			return;
		}
		MatchTerminalClaim claim = match.claimTerminal(null, null, resultType);
		if (claim == null) {
			return;
		}
		if (claim.resultType == VoidArenaRankedPolicy.ResultType.FORFEIT) {
			resolveClaimedMatch(match, claim.winner, claim.loser, claim.resultType);
			return;
		}
		VoidArenaSettlementStatus settlement;
		if (match.rules.ranked) {
			settlement = beginNeutralRankedSettlement(match, claim.resultType);
		} else {
			releaseMatchLock(match);
			settlement = VoidArenaSettlementStatus.SETTLED;
		}
		purgeCageGroundItemsBestEffort(match.slotIndex, "neutral terminal settlement");
		Player playerA = world.getPlayer(match.playerAHash);
		Player playerB = world.getPlayer(match.playerBHash);
		returnNeutralPlayer(playerA, match.rules, message, settlement);
		returnNeutralPlayer(playerB, match.rules, message, settlement);
	}

	private VoidArenaSettlementStatus beginNeutralRankedSettlement(Match match,
												  VoidArenaRankedPolicy.ResultType resultType) {
		long endedAt = Math.max(match.createdAt, System.currentTimeMillis());
		VoidArenaMatchSessionRecord active = match.activeSessionRecord();
		VoidArenaMatchSessionRecord record = match.settledSessionRecord(resultType, null, null,
			active.playerARatingBefore, active.playerBRatingBefore, 0, false, endedAt);
		match.setPendingSettlement(PendingRankedSettlement.neutral(record, resultType));
		return attemptRankedSettlement(match);
	}

	private synchronized VoidArenaSettlementStatus attemptRankedSettlement(Match match) {
		PendingRankedSettlement pending = match == null ? null : match.pendingSettlement();
		if (pending == null) {
			return VoidArenaSettlementStatus.DATABASE_ERROR;
		}
		if (pending.isComplete()) {
			return VoidArenaSettlementStatus.NOT_ACTIVE;
		}

		final VoidArenaSettlementStatus settlement;
		try {
			settlement = world.getServer().getDatabase()
				.settleVoidArenaMatch(pending.record, pending.statsA, pending.statsB);
		} catch (RuntimeException e) {
			LOGGER.error("Unexpected failure settling ranked match {}",
				pending.record.matchId, e);
			pending.markDatabaseError();
			scheduleRankedSettlementRetry(match, pending);
			return VoidArenaSettlementStatus.DATABASE_ERROR;
		}
		VoidArenaStats durableStatsA = pending.statsA;
		VoidArenaStats durableStatsB = pending.statsB;
		if (settlement == VoidArenaSettlementStatus.NOT_ACTIVE) {
			try {
				VoidArenaMatchSessionRecord durable = world.getServer().getDatabase()
					.queryVoidArenaMatchSession(pending.record.matchId);
				if (!sameTerminalSettlement(pending.record, durable)) {
					LOGGER.fatal("Ranked settlement {} conflicts with its durable terminal row",
						pending.record.matchId);
					pending.markDatabaseError();
					scheduleRankedSettlementRetry(match, pending);
					return VoidArenaSettlementStatus.DATABASE_ERROR;
				}
				if (pending.record.isDecisive()) {
					durableStatsA = world.getServer().getDatabase().queryLoadVoidArenaStats(
						pending.record.playerAId, pending.record.seasonId);
					durableStatsB = world.getServer().getDatabase().queryLoadVoidArenaStats(
						pending.record.playerBId, pending.record.seasonId);
					if (durableStatsA == null || durableStatsB == null) {
						throw new GameDatabaseException(VoidArena.class,
							"Durable ranked settlement is missing participant stats");
					}
				}
			} catch (RuntimeException e) {
				LOGGER.error("Unable to confirm already-terminal ranked settlement {}",
					pending.record.matchId, e);
				pending.markDatabaseError();
				scheduleRankedSettlementRetry(match, pending);
				return VoidArenaSettlementStatus.DATABASE_ERROR;
			}
		}

		if (settlement == VoidArenaSettlementStatus.DATABASE_ERROR) {
			pending.markDatabaseError();
			scheduleRankedSettlementRetry(match, pending);
			return settlement;
		}
		if (!pending.markComplete()) {
			return settlement;
		}

		releaseMatchLock(match);
		if (pending.record.isDecisive()) {
			final VoidArenaStats settledStatsA = durableStatsA;
			final VoidArenaStats settledStatsB = durableStatsB;
			runArenaTerminalActionBestEffort(match.playerA, "cache ranked result", () ->
				cacheStats(settledStatsA));
			runArenaTerminalActionBestEffort(match.playerB, "cache ranked result", () ->
				cacheStats(settledStatsB));
			notifyDecisiveRankedSettlement(pending, durableStatsA, durableStatsB);
		} else if (pending.hadDatabaseError()) {
			notifyNeutralRankedSettlementFinalized(match);
		}
		return settlement;
	}

	private void scheduleRankedSettlementRetry(final Match match,
											  final PendingRankedSettlement pending) {
		int delay = pending.reserveRetryDelay();
		if (delay < 0) {
			return;
		}
		try {
			world.getServer().getGameEventHandler().add(new SingleEvent(world, null, delay,
				"Void Arena Ranked Settlement Retry", DuplicationStrategy.ALLOW_MULTIPLE) {
				@Override
				public void action() {
					pending.retryStarted();
					attemptRankedSettlement(match);
				}
			});
		} catch (RuntimeException e) {
			pending.retryStarted();
			LOGGER.fatal("Unable to schedule ranked settlement retry {}",
				pending.record.matchId, e);
		}
	}

	private boolean sameTerminalSettlement(VoidArenaMatchSessionRecord expected,
											 VoidArenaMatchSessionRecord durable) {
		return durable != null
			&& VoidArenaMatchSessionRecord.STATUS_SETTLED.equals(durable.status)
			&& Objects.equals(expected.matchId, durable.matchId)
			&& Objects.equals(expected.seasonId, durable.seasonId)
			&& Objects.equals(expected.resultReason, durable.resultReason)
			&& expected.playerAId == durable.playerAId
			&& expected.playerBId == durable.playerBId
			&& Objects.equals(expected.winnerId, durable.winnerId)
			&& Objects.equals(expected.loserId, durable.loserId)
			&& expected.playerARatingBefore == durable.playerARatingBefore
			&& Objects.equals(expected.playerARatingAfter, durable.playerARatingAfter)
			&& expected.playerBRatingBefore == durable.playerBRatingBefore
			&& Objects.equals(expected.playerBRatingAfter, durable.playerBRatingAfter)
			&& expected.ratingDelta == durable.ratingDelta
			&& expected.ratingApplied == durable.ratingApplied
			&& expected.sameIp == durable.sameIp
			&& expected.sameIpLocalExempt == durable.sameIpLocalExempt
			&& expected.priorRatedResults30m == durable.priorRatedResults30m
			&& expected.priorDecisiveResultsDay == durable.priorDecisiveResultsDay
			&& expected.slotIndex == durable.slotIndex
			&& expected.startedAtMs == durable.startedAtMs
			&& Objects.equals(expected.endedAtMs, durable.endedAtMs);
	}

	private void notifyDecisiveRankedSettlement(PendingRankedSettlement pending,
											   VoidArenaStats statsA, VoidArenaStats statsB) {
		Player winner = world.getPlayer(pending.winnerHash);
		Player loser = world.getPlayer(pending.loserHash);
		VoidArenaStats winnerStats = pending.winnerIsA ? statsA : statsB;
		VoidArenaStats loserStats = pending.winnerIsA ? statsB : statsA;
		if (winner != null && winner.loggedIn()) {
			runArenaTerminalActionBestEffort(winner, "send ranked victory result", () ->
				sendResultMessage(winner, true, pending.delta, pending.oldWinnerRating, winnerStats));
			if (pending.resultType == VoidArenaRankedPolicy.ResultType.FORFEIT) {
				runArenaTerminalActionBestEffort(winner, "send ranked forfeit result", () -> {
					String loserName = loser == null ? "Your opponent" : loser.getUsername();
					winner.message(loserName + " forfeited by leaving the ranked Death Match.");
				});
			}
			runArenaTerminalActionBestEffort(winner, "broadcast ranked victory rating", () ->
				broadcastLobbyRating(winner));
		}
		if (loser != null && loser.loggedIn()) {
			runArenaTerminalActionBestEffort(loser, "send ranked loss result", () ->
				sendResultMessage(loser, false, pending.delta, pending.oldLoserRating, loserStats));
			runArenaTerminalActionBestEffort(loser, "broadcast ranked loss rating", () ->
				broadcastLobbyRating(loser));
		}
	}

	private void notifyNeutralRankedSettlementFinalized(Match match) {
		Player playerA = world.getPlayer(match.playerAHash);
		Player playerB = world.getPlayer(match.playerBHash);
		if (playerA != null && playerA.loggedIn()) {
			runArenaTerminalActionBestEffort(playerA, "send finalized neutral-result notice", () ->
				playerA.message("Your ranked neutral result is now durable; arena admission is unlocked."));
		}
		if (playerB != null && playerB.loggedIn()) {
			runArenaTerminalActionBestEffort(playerB, "send finalized neutral-result notice", () ->
				playerB.message("Your ranked neutral result is now durable; arena admission is unlocked."));
		}
	}

	private void releaseMatchLock(Match match) {
		activeMatches.remove(match.playerAHash, match);
		activeMatches.remove(match.playerBHash, match);
	}

	private void returnNeutralPlayer(Player player, MatchRules rules, String message,
									 VoidArenaSettlementStatus settlement) {
		if (player == null) {
			return;
		}
		returnToLobby(player);
		runArenaTerminalActionBestEffort(player, "send neutral arena result", () ->
			player.playerServerMessage(MessageType.QUEST,
				"@mag@" + rules.title() + " ended: @whi@" + message));
		if (rules.ranked && settlement == VoidArenaSettlementStatus.DATABASE_ERROR) {
			runArenaTerminalActionBestEffort(player, "send pending neutral-result notice", () ->
				player.message("The neutral result is still being finalized; arena admission is locked until it is durable."));
		}
	}

	private void sendResultMessage(Player player, boolean winner, int delta, int oldRating, VoidArenaStats stats) {
		if (isRatingVisible(stats)) {
			player.playerServerMessage(MessageType.QUEST,
				"@mag@Void Arena " + (winner ? "win" : "loss") + ": "
					+ (winner ? "@gre@+" : "@red@-") + delta + "@whi@ rating ("
					+ oldRating + " -> " + stats.rating + ").");
			return;
		}
		player.playerServerMessage(MessageType.QUEST,
			"@mag@Void Arena " + (winner ? "win" : "loss") + " recorded. @whi@Placement: @yel@"
				+ matchCount(stats) + "/" + VoidArenaConfig.RATING_VISIBLE_MATCHES
				+ "@whi@ ranked matches.");
	}

	private void returnToLobby(Player player) {
		finishArenaSession(player, true);
	}

	private void finishArenaSession(Player player, boolean moveToLobby) {
		if (player == null) {
			return;
		}
		sendArenaControlBestEffort(player, "close", "match session finish");
		runArenaTerminalActionBestEffort(player, "reset arena combat state", player::resetAll);
		runArenaTerminalActionBestEffort(player, "clear arena instance", () ->
			player.setInstanceId(0));
		if (moveToLobby) {
			try {
				player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
			} catch (RuntimeException e) {
				LOGGER.error("Unable to return {} to the Void Arena lobby at match end",
					player.getUsername(), e);
				try {
					player.setInstanceId(0);
					player.setLocation(VoidArenaConfig.lobbyTile(), true);
				} catch (RuntimeException fallbackError) {
					LOGGER.error("Unable to apply fallback Void Arena lobby relocation for {}",
						player.getUsername(), fallbackError);
				}
			}
			runArenaTerminalActionBestEffort(player, "confirm cleared arena instance", () ->
				player.setInstanceId(0));
		}
		runArenaTerminalActionBestEffort(player, "restore arena hitpoints", () ->
			restoreHits(player));
		runArenaTerminalActionBestEffort(player, "send restored arena prayers", () ->
			ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers()));
		runArenaTerminalActionBestEffort(player, "send restored arena inventory", () ->
			ActionSender.sendInventory(player));
		runArenaTerminalActionBestEffort(player, "send restored arena equipment", () ->
			ActionSender.sendEquipmentStats(player));
	}

	private void runArenaTerminalActionBestEffort(Player player, String action, Runnable terminalAction) {
		if (player == null || terminalAction == null) {
			return;
		}
		try {
			terminalAction.run();
		} catch (RuntimeException e) {
			LOGGER.error("Unable to {} for {} while finishing a Void Arena match",
				action, player.getUsername(), e);
		}
	}

	private void restoreHits(Player player) {
		if (player == null) {
			return;
		}
		player.getSkills().setLevel(Skill.HITS.id(), player.getSkills().getMaxStat(Skill.HITS.id()));
	}

	private VoidArenaStats getStats(int playerId, String username) {
		return getStats(playerId, username, VoidArenaConfig.currentSeasonId());
	}

	private VoidArenaStats getStats(int playerId, String username, String seasonId) {
		String cacheKey = statsCacheKey(seasonId, playerId);
		VoidArenaStats cached = statsCache.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		try {
			VoidArenaStats stats = world.getServer().getDatabase()
				.queryLoadVoidArenaStats(playerId, seasonId);
			if (stats == null) {
				stats = defaultStats(playerId, username, seasonId);
			}
			if (stats.username == null || stats.username.isEmpty()) {
				stats.username = username;
			}
			statsCache.put(cacheKey, stats);
			return stats;
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena stats for player {}", playerId, e);
			VoidArenaStats fallback = defaultStats(playerId, username, seasonId);
			statsCache.put(cacheKey, fallback);
			return fallback;
		}
	}

	private String statsCacheKey(String seasonId, int playerId) {
		return seasonId + ':' + playerId;
	}

	private VoidArenaStats defaultStats(int playerId, String username, String seasonId) {
		VoidArenaStats stats = new VoidArenaStats();
		stats.seasonId = seasonId;
		stats.playerId = playerId;
		stats.username = username;
		stats.rating = VoidArenaConfig.STARTING_RATING;
		return stats;
	}

	private VoidArenaStats copyStats(VoidArenaStats source) {
		VoidArenaStats copy = new VoidArenaStats();
		copy.seasonId = source.seasonId;
		copy.playerId = source.playerId;
		copy.username = source.username;
		copy.rating = source.rating;
		copy.wins = source.wins;
		copy.losses = source.losses;
		copy.disconnectLosses = source.disconnectLosses;
		copy.resetCount = source.resetCount;
		copy.updatedAt = source.updatedAt;
		return copy;
	}

	private void cacheStats(VoidArenaStats stats) {
		statsCache.put(statsCacheKey(stats.seasonId, stats.playerId), stats);
	}

	public void sendLeaderboard(Player player) {
		try {
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.currentSeasonId(),
					VoidArenaConfig.RATING_VISIBLE_MATCHES, 50);
			StringBuilder box = new StringBuilder("@yel@Void Arena Leaderboard:%");
			appendCurrentChampion(box);
			box.append("@yel@Top 5:%");
			int displayed = 0;
			for (VoidArenaStats stats : top) {
				if (!isRatingVisible(stats)) {
					continue;
				}
				if (displayed >= 5) {
					break;
				}
				displayed++;
				box.append("@whi@").append(displayed).append(". @mag@")
					.append(stats.username == null ? ("player " + stats.playerId) : stats.username)
					.append("@whi@ - @yel@").append(stats.rating)
					.append("@whi@ (").append(stats.wins).append("-").append(stats.losses).append(")%");
			}
			if (displayed == 0) {
				box.append("@whi@No visible ranked fighters yet. Complete ")
					.append(VoidArenaConfig.RATING_VISIBLE_MATCHES).append(" ranked matches to appear.%");
			}
			ActionSender.sendBox(player, box.toString(), true);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena leaderboard", e);
			player.message("Unable to load the Void Arena leaderboard right now.");
		}
	}

	private void handleSeasonCommand(Player player, String[] args) {
		if (args.length >= 2 && (args[1].equalsIgnoreCase("reset")
			|| args[1].equalsIgnoreCase("confirm") || args[1].equalsIgnoreCase("finalize"))) {
			if (requireArenaAdmin(player)) {
				player.message("Void Arena seasons roll automatically at 00:00 UTC on the first of each month.");
			}
			return;
		}
		sendSeasonPreview(player);
	}

	private void sendSeasonPreview(Player player) {
		if (!requireArenaAdmin(player)) {
			return;
		}
		try {
			int rankedProfiles = world.getServer().getDatabase()
				.queryCountVoidArenaStats(VoidArenaConfig.currentSeasonId());
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchSessions(VoidArenaConfig.currentSeasonId());
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.currentSeasonId(),
					VoidArenaConfig.RATING_VISIBLE_MATCHES, 10);
			VoidArenaMatchSessionRecord[] recent = world.getServer().getDatabase()
				.queryRecentVoidArenaMatchSessions(VoidArenaConfig.currentSeasonId(), 5);
			StringBuilder box = new StringBuilder("@yel@Void Arena Season Preview:%");
			box.append("@whi@Season: @cya@").append(VoidArenaConfig.currentSeasonId())
				.append("@whi@, ranked profiles: @yel@").append(rankedProfiles)
				.append("@whi@, ledger rows: @yel@").append(rankedMatches).append("%");
			appendCurrentChampion(box);
			box.append("@yel@Top ratings:%");
			appendTopRatings(box, top, 5);
			box.append("@yel@Recent ranked matches:%");
			appendSessionRows(box, recent);
			box.append("@whi@The next season begins automatically at 00:00 UTC on the first of the month.%");
			ActionSender.sendBox(player, box.toString(), true);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena season preview", e);
			player.message("Unable to load the Void Arena season preview right now.");
		}
	}

	private void sendSeasonResetPreview(Player player) {
		if (!requireArenaAdmin(player)) {
			return;
		}
		try {
			int rankedProfiles = world.getServer().getDatabase()
				.queryCountVoidArenaStats(VoidArenaConfig.currentSeasonId());
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchRecords(VoidArenaConfig.currentSeasonId());
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.currentSeasonId(),
					VoidArenaConfig.RATING_VISIBLE_MATCHES, 10);
			VoidArenaStats champion = findSeasonChampionCandidate();
			String token = Integer.toString(ThreadLocalRandom.current().nextInt(100000, 1000000));
			pendingSeasonResets.put(player.getUsernameHash(),
				new SeasonResetConfirmation(token, System.currentTimeMillis(), rankedProfiles, rankedMatches,
					champion == null ? 0 : champion.playerId,
					champion == null ? 0 : champion.rating,
					matchCount(champion)));

			StringBuilder box = new StringBuilder("@red@Void Arena Ranked Reset Preview:%");
			box.append("@whi@This will reset @yel@").append(rankedProfiles)
				.append("@whi@ ranked profiles to @yel@").append(VoidArenaConfig.STARTING_RATING)
				.append("@whi@ rating, 0 wins, 0 losses, and 0 disconnect losses.%");
			box.append("@whi@The @yel@").append(rankedMatches)
				.append("@whi@ ranked match ledger rows are retained for audit.%");
			if (hasActiveSetupOrMatch()) {
				box.append("@red@Blocked right now: @whi@")
					.append(activeMatchCount()).append(" active match(es), ")
					.append(activeSetupCount()).append(" setup(s).%");
			}
			appendCandidateChampion(box, champion);
			box.append("@yel@Current top ratings before reset:%");
			appendTopRatings(box, top, 5);
			box.append("@whi@Confirm within 2 minutes with:%@cya@::arena season confirm ")
				.append(token).append("%");
			ActionSender.sendBox(player, box.toString(), true);
			player.message("Void Arena reset token: " + token);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena reset preview", e);
			player.message("Unable to load the Void Arena reset preview right now.");
		}
	}

	private void confirmSeasonReset(Player player, String[] args) {
		if (!requireArenaAdmin(player)) {
			return;
		}
		if (args.length < 3) {
			player.message("Syntax: ::arena season confirm <token>");
			return;
		}
		SeasonResetConfirmation pending = pendingSeasonResets.get(player.getUsernameHash());
		if (pending == null) {
			player.message("Run ::arena season reset first to preview and generate a confirmation token.");
			return;
		}
		if (pending.isExpired()) {
			pendingSeasonResets.remove(player.getUsernameHash());
			player.message("That Void Arena reset token expired. Run ::arena season reset again.");
			return;
		}
		if (!pending.token.equals(args[2])) {
			player.message("Incorrect Void Arena reset token.");
			return;
		}
		if (hasActiveSetupOrMatch()) {
			player.message("Cannot reset Void Arena ranked stats while a setup or match is active.");
			return;
		}
		try {
			int rankedProfiles = world.getServer().getDatabase()
				.queryCountVoidArenaStats(VoidArenaConfig.currentSeasonId());
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchRecords(VoidArenaConfig.currentSeasonId());
			VoidArenaStats champion = findSeasonChampionCandidate();
			if (rankedProfiles != pending.rankedProfiles || rankedMatches != pending.rankedMatches) {
				pendingSeasonResets.remove(player.getUsernameHash());
				player.message("Void Arena ranked data changed since preview. Run ::arena season reset again.");
				return;
			}
			if (!matchesPendingChampion(pending, champion)) {
				pendingSeasonResets.remove(player.getUsernameHash());
				player.message("Void Arena champion candidate changed since preview. Run ::arena season reset again.");
				return;
			}
			int affected = world.getServer().getDatabase().queryResetVoidArenaStats(
				VoidArenaConfig.currentSeasonId(), VoidArenaConfig.STARTING_RATING, System.currentTimeMillis());
			boolean championRecorded = recordSeasonChampion(champion);
			pendingSeasonResets.remove(player.getUsernameHash());
			statsCache.clear();
			refreshLobbyRatingDisplays();
			LOGGER.warn("{} reset Void Arena ranked stats for season {} ({} profiles, {} ledger rows retained, champion: {})",
				player.getUsername(), VoidArenaConfig.currentSeasonId(), affected, rankedMatches,
				championRecorded ? playerName(champion.username, champion.playerId) : "none");
			player.playerServerMessage(MessageType.QUEST,
				"@mag@Void Arena ranked reset complete: @whi@" + affected
					+ " profiles reset. The ranked match ledger was retained.");
			if (championRecorded) {
				player.playerServerMessage(MessageType.QUEST,
					"@mag@Season champion: @whi@" + playerName(champion.username, champion.playerId)
						+ " was recorded for the completed season.");
			}
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to reset Void Arena ranked stats", e);
			player.message("Unable to reset Void Arena ranked stats right now.");
		}
	}

	private VoidArenaStats findSeasonChampionCandidate() throws GameDatabaseException {
		return findSeasonChampionCandidate(VoidArenaConfig.currentSeasonId());
	}

	private VoidArenaStats findSeasonChampionCandidate(String seasonId) throws GameDatabaseException {
		VoidArenaStats[] top = world.getServer().getDatabase()
			.queryTopVoidArenaStats(seasonId, VoidArenaConfig.RATING_VISIBLE_MATCHES, 100);
		for (VoidArenaStats stats : top) {
			if (isRatingVisible(stats)) {
				return stats;
			}
		}
		return null;
	}

	private boolean matchesPendingChampion(SeasonResetConfirmation pending, VoidArenaStats champion) {
		if (pending.championPlayerId == 0) {
			return champion == null;
		}
		return champion != null
			&& pending.championPlayerId == champion.playerId
			&& pending.championRating == champion.rating
			&& pending.championMatches == matchCount(champion);
	}

	private boolean recordSeasonChampion(VoidArenaStats champion) throws GameDatabaseException {
		if (champion == null) {
			return false;
		}
		String seasonId = VoidArenaConfig.currentSeasonId();
		world.getServer().getDatabase().querySaveGlobalCacheInt(
			championPlayerKey(seasonId), champion.playerId);
		world.getServer().getDatabase().querySaveGlobalCacheInt(
			championRatingKey(seasonId), champion.rating);
		world.getServer().getDatabase().querySaveGlobalCacheLong(
			championAwardedAtKey(seasonId), System.currentTimeMillis());
		return true;
	}

	private boolean hasActiveSetupOrMatch() {
		return !activeMatches.isEmpty() || !activeSetups.isEmpty() || !activeDmKingChallenges.isEmpty();
	}

	private int activeMatchCount() {
		return (activeMatches.size() / 2) + activeDmKingChallenges.size();
	}

	private int activeSetupCount() {
		return activeSetups.size() / 2;
	}

	private void refreshLobbyRatingDisplays() {
		for (Player viewer : world.getPlayers()) {
			if (VoidArenaConfig.isInsideVoidArena(viewer.getLocation())) {
				sendRatingClear(viewer);
				sendLobbyRatings(viewer);
			}
		}
	}

	private void sendAudit(Player player, String[] args) {
		if (!requireArenaAdmin(player)) {
			return;
		}
		int limit = auditLimit(args, 10);
		try {
			if (args.length < 2 || args[1].equalsIgnoreCase("recent") || (args.length == 2 && lastArgIsInteger(args))) {
				VoidArenaMatchSessionRecord[] recent = world.getServer().getDatabase()
					.queryRecentVoidArenaMatchSessions(VoidArenaConfig.currentSeasonId(), limit);
				StringBuilder box = new StringBuilder("@yel@Void Arena Ranked Audit:%");
				box.append("@whi@Season @cya@").append(VoidArenaConfig.currentSeasonId())
					.append("@whi@ recent ranked sessions:%");
				appendSessionRows(box, recent);
				ActionSender.sendBox(player, box.toString(), true);
				return;
			}

			if (args[1].equalsIgnoreCase("legacy")) {
				VoidArenaMatchRecord[] recent = world.getServer().getDatabase()
					.queryRecentVoidArenaMatchRecords(VoidArenaConfig.LEGACY_SEASON, limit);
				StringBuilder box = new StringBuilder("@yel@Void Arena Legacy Audit:%");
				box.append("@whi@Pre-monthly ranked matches (read-only):%");
				appendMatchRows(box, recent);
				ActionSender.sendBox(player, box.toString(), true);
				return;
			}

			int usernameEnd = lastArgIsInteger(args) ? args.length - 1 : args.length;
			String username = joinArgs(args, 1, usernameEnd);
			int playerId = world.getServer().getDatabase().playerIdFromUsername(username);
			if (playerId < 0) {
				player.message("No player named '" + username + "' was found.");
				return;
			}
			VoidArenaStats stats = getStats(playerId, username);
			VoidArenaMatchSessionRecord[] recent = world.getServer().getDatabase()
				.queryRecentVoidArenaMatchSessionsForPlayer(
					VoidArenaConfig.currentSeasonId(), playerId, limit);
			StringBuilder box = new StringBuilder("@yel@Void Arena Player Audit:%");
			box.append("@whi@Player: @mag@").append(username)
				.append("@whi@ rating @yel@").append(stats.rating)
				.append("@whi@ (").append(stats.wins).append("-").append(stats.losses)
				.append(", DC ").append(stats.disconnectLosses).append(")%");
			box.append("@whi@Recent ranked matches:%");
			appendSessionRows(box, recent);
			ActionSender.sendBox(player, box.toString(), true);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena ranked audit", e);
			player.message("Unable to load the Void Arena ranked audit right now.");
		}
	}

	private boolean requireArenaAdmin(Player player) {
		if (player.isAdmin()) {
			return true;
		}
		player.message("Only administrators can audit Void Arena ranked matches.");
		return false;
	}

	private int auditLimit(String[] args, int defaultLimit) {
		if (args.length > 1 && lastArgIsInteger(args)) {
			try {
				return Math.max(1, Math.min(25, Integer.parseInt(args[args.length - 1])));
			} catch (NumberFormatException ignored) {
			}
		}
		return defaultLimit;
	}

	private boolean lastArgIsInteger(String[] args) {
		if (args.length == 0) {
			return false;
		}
		try {
			Integer.parseInt(args[args.length - 1]);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private void appendTopRatings(StringBuilder box, VoidArenaStats[] top, int limit) {
		int displayed = 0;
		for (VoidArenaStats stats : top) {
			if (displayed >= limit) {
				break;
			}
			displayed++;
			box.append("@whi@").append(displayed).append(". @mag@")
				.append(stats.username == null ? ("player " + stats.playerId) : stats.username)
				.append("@whi@ - @yel@").append(stats.rating)
				.append("@whi@ (").append(stats.wins).append("-").append(stats.losses)
				.append(", DC ").append(stats.disconnectLosses).append(")%");
		}
		if (displayed == 0) {
			box.append("@whi@No ranked stats yet.%");
		}
	}

	private void appendCurrentChampion(StringBuilder box) throws GameDatabaseException {
		if (!ensurePreviousSeasonChampion()) {
			box.append("@whi@Previous season champion: @yel@finalizing.%");
			return;
		}
		SeasonChampion champion = loadCurrentChampion();
		if (champion == null) {
			box.append("@whi@Previous season champion: @gre@none.%");
			return;
		}
		box.append("@whi@Previous season champion: @mag@").append(champion.username)
			.append("@whi@ at @yel@").append(champion.rating).append("@whi@ rating.%");
	}

	private synchronized boolean ensurePreviousSeasonChampion() throws GameDatabaseException {
		if (System.currentTimeMillis() < VoidArenaConfig.currentSeasonStartMs()
			+ MONTHLY_CHAMPION_FINALIZATION_GRACE_MS) {
			return false;
		}

		YearMonth previous = YearMonth.parse(VoidArenaConfig.previousSeasonId());
		Integer lastFinalized = world.getServer().getDatabase()
			.queryLoadGlobalCacheInt(LAST_FINALIZED_CHAMPION_SEASON_CACHE);
		YearMonth next = previous;
		YearMonth parsedLast = parseSeasonNumber(lastFinalized);
		if (parsedLast != null && parsedLast.isBefore(previous)) {
			next = parsedLast.plusMonths(1);
		}
		while (!next.isAfter(previous)) {
			if (!finalizeSeasonChampion(next.toString())) {
				return false;
			}
			next = next.plusMonths(1);
		}
		return true;
	}

	private boolean finalizeSeasonChampion(final String seasonId)
		throws GameDatabaseException {
		final boolean[] finalized = {false};
		boolean committed = world.getServer().getDatabase().atomically(() -> {
			Integer existingPlayer = world.getServer().getDatabase()
				.queryLoadGlobalCacheInt(championPlayerKey(seasonId));
			Integer existingRating = world.getServer().getDatabase()
				.queryLoadGlobalCacheInt(championRatingKey(seasonId));
			if (existingPlayer == null) {
				if (world.getServer().getDatabase()
					.queryCountActiveVoidArenaMatchSessions(seasonId) > 0) {
					return;
				}
				VoidArenaStats champion = findSeasonChampionCandidate(seasonId);
				existingPlayer = champion == null ? 0 : champion.playerId;
				existingRating = champion == null ? 0 : champion.rating;
				world.getServer().getDatabase().querySaveGlobalCacheInt(
					championPlayerKey(seasonId), existingPlayer);
				world.getServer().getDatabase().querySaveGlobalCacheInt(
					championRatingKey(seasonId), existingRating);
				world.getServer().getDatabase().querySaveGlobalCacheLong(
					championAwardedAtKey(seasonId), System.currentTimeMillis());
			}
			if (existingRating == null) {
				throw new GameDatabaseException(VoidArena.class,
					"Season champion cache is missing its rating");
			}
			world.getServer().getDatabase().querySaveGlobalCacheInt(
				LAST_FINALIZED_CHAMPION_SEASON_CACHE, seasonNumber(YearMonth.parse(seasonId)));
			finalized[0] = true;
		});
		if (!committed) {
			throw new GameDatabaseException(VoidArena.class,
				"Unable to finalize monthly champion for " + seasonId);
		}
		return finalized[0];
	}

	private YearMonth parseSeasonNumber(Integer seasonNumber) {
		if (seasonNumber == null) {
			return null;
		}
		int year = seasonNumber / 100;
		int month = seasonNumber % 100;
		try {
			return YearMonth.of(year, month);
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private int seasonNumber(YearMonth season) {
		return season.getYear() * 100 + season.getMonthValue();
	}

	private String championPlayerKey(String seasonId) {
		return "void_arena_champ_p_" + seasonId.replace("-", "");
	}

	private String championRatingKey(String seasonId) {
		return "void_arena_champ_r_" + seasonId.replace("-", "");
	}

	private String championAwardedAtKey(String seasonId) {
		return "void_arena_champ_t_" + seasonId.replace("-", "");
	}

	private void appendCandidateChampion(StringBuilder box, VoidArenaStats champion) {
		if (champion == null) {
			box.append("@whi@Season champion if reset now: @gre@none, no visible ranked fighters.%");
			return;
		}
		box.append("@whi@Season champion if reset now: @mag@")
			.append(playerName(champion.username, champion.playerId))
			.append("@whi@ at @yel@").append(champion.rating)
			.append("@whi@ (").append(champion.wins).append("-").append(champion.losses).append(").%");
	}

	private SeasonChampion loadCurrentChampion() throws GameDatabaseException {
		String previousSeasonId = VoidArenaConfig.previousSeasonId();
		Integer playerId = world.getServer().getDatabase()
			.queryLoadGlobalCacheInt(championPlayerKey(previousSeasonId));
		if (playerId == null || playerId <= 0) {
			return null;
		}
		Integer rating = world.getServer().getDatabase()
			.queryLoadGlobalCacheInt(championRatingKey(previousSeasonId));
		String username = world.getServer().getDatabase().usernameFromId(playerId);
		return new SeasonChampion(playerId, username == null ? ("player " + playerId) : username,
			rating == null ? 0 : rating);
	}

	private void appendMatchRows(StringBuilder box, VoidArenaMatchRecord[] records) {
		if (records.length == 0) {
			box.append("@whi@No ranked matches recorded yet.%");
			return;
		}
		for (VoidArenaMatchRecord record : records) {
			box.append("@whi@#").append(record.id).append(" @mag@")
				.append(playerName(record.winnerUsername, record.winnerId))
				.append("@whi@ defeated @mag@").append(playerName(record.loserUsername, record.loserId))
				.append("@whi@ @gre@+").append(record.ratingDelta)
				.append("@whi@ (").append(record.winnerRatingBefore).append("->")
				.append(record.winnerRatingAfter).append(" / ")
				.append(record.loserRatingBefore).append("->").append(record.loserRatingAfter).append(")");
			if (record.disconnectLoss) {
				box.append(" @red@DC");
			}
			box.append("%");
		}
	}

	private void appendSessionRows(StringBuilder box, VoidArenaMatchSessionRecord[] records)
		throws GameDatabaseException {
		if (records.length == 0) {
			box.append("@whi@No ranked sessions recorded yet.%");
			return;
		}
		Map<Integer, String> names = new HashMap<>();
		for (VoidArenaMatchSessionRecord record : records) {
			String matchLabel = record.matchId == null ? "?"
				: record.matchId.substring(0, Math.min(8, record.matchId.length()));
			String playerA = sessionPlayerName(record.playerAId, names);
			String playerB = sessionPlayerName(record.playerBId, names);
			box.append("@whi@#").append(matchLabel).append(" ");
			if (VoidArenaMatchSessionRecord.STATUS_ACTIVE.equals(record.status)) {
				box.append("@yel@ACTIVE @mag@").append(playerA).append("@whi@ vs @mag@")
					.append(playerB).append("%");
				continue;
			}
			if (record.isDecisive() && record.winnerId != null && record.loserId != null) {
				String winner = sessionPlayerName(record.winnerId, names);
				String loser = sessionPlayerName(record.loserId, names);
				box.append("@mag@").append(winner).append("@whi@ defeated @mag@").append(loser)
					.append("@whi@ @gre@+").append(Math.abs(record.ratingDelta))
					.append("@whi@ (A ").append(record.playerARatingBefore).append("->")
					.append(record.playerARatingAfter).append(" / B ")
					.append(record.playerBRatingBefore).append("->")
					.append(record.playerBRatingAfter).append(")");
				if (VoidArenaMatchSessionRecord.REASON_FORFEIT.equals(record.resultReason)) {
					box.append(" @red@FORFEIT");
				}
				box.append("%");
				continue;
			}
			box.append("@mag@").append(playerA).append("@whi@ vs @mag@").append(playerB)
				.append("@whi@ - @yel@").append(sessionResultLabel(record.resultReason)).append("%");
		}
	}

	private String sessionPlayerName(int playerId, Map<Integer, String> names)
		throws GameDatabaseException {
		String cached = names.get(playerId);
		if (cached != null) {
			return cached;
		}
		String username = world.getServer().getDatabase().usernameFromId(playerId);
		String resolved = playerName(username, playerId);
		names.put(playerId, resolved);
		return resolved;
	}

	private String sessionResultLabel(String reason) {
		if (VoidArenaMatchSessionRecord.REASON_TIMEOUT_DRAW.equals(reason)) {
			return "timeout draw";
		}
		if (VoidArenaMatchSessionRecord.REASON_SERVER_SHUTDOWN_NO_CONTEST.equals(reason)) {
			return "server shutdown - no contest";
		}
		if (VoidArenaMatchSessionRecord.REASON_SERVER_RESTART_NO_CONTEST.equals(reason)) {
			return "server restart recovery - no contest";
		}
		return reason == null ? "unknown result" : reason.toLowerCase().replace('_', ' ');
	}

	private String playerName(String username, int playerId) {
		if (username != null && !username.isEmpty()) {
			return username;
		}
		return "player " + playerId;
	}

	private void showHelp(Player player) {
		player.playerServerMessage(MessageType.QUEST, "@mag@Void Arena commands:");
		player.playerServerMessage(MessageType.QUEST, "@whi@Void Enclave rift@mag@ - enter the ranked lobby");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena stats@mag@ - show your rating");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena top@mag@ - show the top ranked fighters");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena leave@mag@ - leave the lobby");
			if (player.isAdmin()) {
				player.playerServerMessage(MessageType.QUEST, "@whi@::arena season@mag@ - preview current ranked season");
				player.playerServerMessage(MessageType.QUEST, "@whi@::arena audit [recent|player|legacy]@mag@ - inspect ranked match ledger");
		}
		player.playerServerMessage(MessageType.QUEST,
			"@whi@Ratings are hidden until " + VoidArenaConfig.RATING_VISIBLE_MATCHES + " ranked matches are complete.");
	}

	private void removeChallenges(Player player) {
		incomingChallenges.remove(player.getUsernameHash());
		Map<Long, Challenge> copy = new HashMap<>(incomingChallenges);
		for (Map.Entry<Long, Challenge> entry : copy.entrySet()) {
			if (entry.getValue().challengerHash == player.getUsernameHash()) {
				incomingChallenges.remove(entry.getKey());
			}
		}
	}

	private String joinArgs(String[] args, int startIndex) {
		return joinArgs(args, startIndex, args.length);
	}

	private String joinArgs(String[] args, int startIndex, int endIndex) {
		StringBuilder builder = new StringBuilder();
		for (int i = startIndex; i < endIndex; i++) {
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(args[i]);
		}
		return builder.toString();
	}

	private static final class SeasonResetConfirmation {
		private final String token;
		private final long createdAt;
		private final int rankedProfiles;
		private final int rankedMatches;
		private final int championPlayerId;
		private final int championRating;
		private final int championMatches;

		private SeasonResetConfirmation(String token, long createdAt, int rankedProfiles, int rankedMatches,
										int championPlayerId, int championRating, int championMatches) {
			this.token = token;
			this.createdAt = createdAt;
			this.rankedProfiles = rankedProfiles;
			this.rankedMatches = rankedMatches;
			this.championPlayerId = championPlayerId;
			this.championRating = championRating;
			this.championMatches = championMatches;
		}

		private boolean isExpired() {
			return System.currentTimeMillis() - createdAt > SEASON_RESET_CONFIRM_MS;
		}
	}

	private static final class RankedAdmission {
		private final VoidArenaRankedPolicy.PairEligibility eligibility;
		private final String seasonId;
		private final Player playerA;
		private final Player playerB;
		private final VoidArenaStats playerAStats;
		private final VoidArenaStats playerBStats;
		private final VoidArenaPairAudit audit;

		private RankedAdmission(VoidArenaRankedPolicy.PairEligibility eligibility, String seasonId,
								Player playerA, Player playerB, VoidArenaStats playerAStats,
								VoidArenaStats playerBStats, VoidArenaPairAudit audit) {
			this.eligibility = eligibility;
			this.seasonId = seasonId;
			this.playerA = playerA;
			this.playerB = playerB;
			this.playerAStats = playerAStats;
			this.playerBStats = playerBStats;
			this.audit = audit;
		}

		private static RankedAdmission denied(VoidArenaRankedPolicy.PairEligibility eligibility) {
			return new RankedAdmission(eligibility, null, null, null, null, null, null);
		}

		private static RankedAdmission allowed(String seasonId, Player playerA, Player playerB,
										 VoidArenaStats playerAStats, VoidArenaStats playerBStats,
										 VoidArenaPairAudit audit) {
			return new RankedAdmission(VoidArenaRankedPolicy.PairEligibility.ELIGIBLE, seasonId,
				playerA, playerB, playerAStats, playerBStats, audit);
		}

		private boolean eligible() {
			return eligibility == VoidArenaRankedPolicy.PairEligibility.ELIGIBLE;
		}
	}

	private static final class SeasonChampion {
		private final int playerId;
		private final String username;
		private final int rating;

		private SeasonChampion(int playerId, String username, int rating) {
			this.playerId = playerId;
			this.username = username;
			this.rating = rating;
		}
	}

	public static final class AttackCheck {
		public final boolean applies;
		public final boolean allowed;
		public final String message;

		private AttackCheck(boolean applies, boolean allowed, String message) {
			this.applies = applies;
			this.allowed = allowed;
			this.message = message;
		}

		public static AttackCheck pass() {
			return new AttackCheck(false, false, null);
		}

		public static AttackCheck allow() {
			return new AttackCheck(true, true, null);
		}

		public static AttackCheck deny(String message) {
			return new AttackCheck(true, false, message);
		}
	}

	private static final class Challenge {
		private final long challengerHash;
		private final long targetHash;
		private final long createdAt = System.currentTimeMillis();

		private Challenge(long challengerHash, long targetHash) {
			this.challengerHash = challengerHash;
			this.targetHash = targetHash;
		}

		private boolean expired() {
			return System.currentTimeMillis() - createdAt > VoidArenaConfig.CHALLENGE_TIMEOUT_MS;
		}
	}

	private final class DeathMatchSetup {
		private final long playerAHash;
		private final long playerBHash;
		private final long createdAt = System.currentTimeMillis();
		private MatchRules rules;
		private boolean playerAAccepted;
		private boolean playerBAccepted;
		private boolean confirmPhase;
		private boolean playerAConfirmed;
		private boolean playerBConfirmed;

		private DeathMatchSetup(long playerAHash, long playerBHash, MatchRules rules) {
			this.playerAHash = playerAHash;
			this.playerBHash = playerBHash;
			this.rules = rules;
		}

		private Player opponent(Player player) {
			if (player == null) {
				return null;
			}
			long opponentHash = player.getUsernameHash() == playerAHash ? playerBHash : playerAHash;
			return world.getPlayer(opponentHash);
		}

		private boolean rankedAvailable() {
			Player playerA = world.getPlayer(playerAHash);
			Player playerB = world.getPlayer(playerBHash);
			return playerA != null && playerB != null && isRankedPairEligible(playerA, playerB);
		}

		private boolean expired() {
			return System.currentTimeMillis() - createdAt >= VoidArenaConfig.SETUP_TIMEOUT_MS;
		}
	}

	private final class DmKingChallenge {
		private final UUID sessionId = UUID.randomUUID();
		private final int slotIndex;
		private final Player player;
		private final long playerHash;
		private final Npc king;
		private final DmKingEvent event;
		private final long startsAt;
		private final long expiresAt;
		private final int playerFishCapacity;
		private final int fishCapacity;
		private int fishRemaining;
		private int castsUsed;
		private int strengthPotionDosesRemaining = DM_KING_STRENGTH_POTION_DOSES;
		private int prayerStatePoints = DM_KING_PRAYER_STATE_POINTS;
		private int strengthPotionBoostedLevel;
		private long nextEatTick;
		private long nextCastTick;
		private long nextReengageTick;
		private long nextKiteTick;
		private long nextStrengthPotionTick;
		private long nextIdleRoamTick;
		private long nextQuipTick;
		private long pendingPlayerCastCheckTick = -1;
		private int pendingPlayerCastKingHits = -1;
		private int lastPlayerDeathRunes = -1;
		private boolean eatingToFull;
		private boolean prepared;
		private boolean started;
		private volatile boolean finished;
		private boolean quippedPlayerLowFood;
		private boolean quippedPlayerNoFood;
		private boolean quippedPlayerLowHits;
		private boolean quippedPlayerName;
		private boolean quippedKingLowFood;
		private boolean announcedPrayerDepleted;

		private DmKingChallenge(int slotIndex, Player player, Npc king, long startsAt,
			int playerFishCapacity) {
			this.slotIndex = slotIndex;
			this.player = Objects.requireNonNull(player, "player");
			this.playerHash = player.getUsernameHash();
			this.king = king;
			this.startsAt = startsAt;
			this.expiresAt = startsAt + DM_KING_MATCH_TIMEOUT_MS;
			this.playerFishCapacity = playerFishCapacity;
			this.fishCapacity = DM_KING_FISH_COUNT;
			this.fishRemaining = DM_KING_FISH_COUNT;
			this.event = new DmKingEvent(world, this);
		}

		private boolean hasStarted() {
			return System.currentTimeMillis() >= startsAt;
		}

		private synchronized boolean tryFinish() {
			if (finished) {
				return false;
			}
			finished = true;
			return true;
		}

		private boolean hasPrayer(int prayerId) {
			if (prayerStatePoints <= 0) {
				return false;
			}
			for (int activePrayerId : DM_KING_ACTIVE_PRAYERS) {
				if (activePrayerId == prayerId) {
					return true;
				}
			}
			return false;
		}

		private boolean isInsideAssignedCage(Mob mob) {
			return mob != null && VoidArenaConfig.arenaSlot(slotIndex).contains(mob.getLocation());
		}

		private void tick() {
			if (finished) {
				event.stop();
				return;
			}
			Player player = world.getPlayer(playerHash);
			if (player == null || !player.loggedIn() || player.isRemoved()) {
				resolveDmKingLoss(this, player, "", true, true);
				event.stop();
				return;
			}
			if (king == null || king.isRemoved()) {
				resolveDmKingLoss(this, player, DM_KING_DISPLAY_NAME + " vanished from the arena.", false, false);
				event.stop();
				return;
			}
			if (king.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				resolveDmKingVictory(this, player);
				event.stop();
				return;
			}
			if (System.currentTimeMillis() >= expiresAt) {
				expireDmKingChallenge(this);
				event.stop();
				return;
			}
			if (!hasStarted()) {
				prepareIfReady(player);
				maybeIdleRoam(world.getServer().getCurrentTick(), player);
				return;
			}
			if (!prepared) {
				prepare(player);
				return;
			}
			if (!started) {
				started = true;
				player.message("@red@" + DM_KING_DISPLAY_NAME + " attacks with perfect supplies.");
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return;
			}
			if (!isInsideAssignedCage(king)) {
				Point start = VoidArenaConfig.arenaSlot(slotIndex).startB();
				king.teleport(start.getX(), start.getY());
			}

			long tick = world.getServer().getCurrentTick();
			drainPrayer(player);
			VoidArenaSirPolicy.MainAction action = VoidArenaSirPolicy.selectMainAction(castsUsed,
				canRetreatToEat(tick, player), canEat(tick, player), canRepotStrength(tick),
				canCast(tick, player), canKiteForMagic(tick, player), canReengage(tick, player));
			switch (action) {
				case RETREAT_TO_EAT:
					maybeRetreatToEat(tick, player);
					break;
				case EAT:
					maybeEat(tick, player);
					break;
				case REPOT_STRENGTH:
					maybeRepotStrength(tick, player);
					break;
				case CAST_FIRE_BLAST:
					maybeCast(tick, player);
					break;
				case KITE_FOR_MAGIC:
					maybeKiteForMagic(tick, player);
					break;
				case MELEE_PRESSURE:
					maybeReengage(tick, player);
					break;
				case NONE:
				default:
					break;
			}
			maybeFightQuip(tick, player);
		}

		private void prepareIfReady(Player player) {
			if (!prepared && System.currentTimeMillis() + world.getServer().getConfig().GAME_TICK >= startsAt) {
				prepare(player);
			}
		}

		private void prepare(Player player) {
			if (prepared) {
				return;
			}
			prepared = true;
			drinkStrengthPotion();
			king.getUpdateFlags().setChatMessage(new ChatMessage(king,
				"Steel skin. Ultimate strength. Incredible reflexes.", player));
			player.message("@red@" + DM_KING_DISPLAY_NAME + " drinks a strength potion and activates Steel Skin, Ultimate Strength, and Incredible Reflexes.");
			player.playerServerMessage(MessageType.QUEST,
				"@yel@" + DM_KING_DISPLAY_NAME + ": " + randomLine(DM_KING_PREFIGHT_QUIPS));
		}

		private boolean drinkStrengthPotion() {
			if (!VoidArenaSirPolicy.canDrinkStrengthPotion(strengthPotionDosesRemaining)) {
				return false;
			}
			int maxStrength = king.getSkills().getMaxStat(Skill.STRENGTH.id());
			int boosted = maxStrength + 3 + ((maxStrength * 10) / 100);
			king.getSkills().setLevel(Skill.STRENGTH.id(), boosted);
			strengthPotionBoostedLevel = boosted;
			strengthPotionDosesRemaining = VoidArenaSirPolicy.potionDosesAfterDrink(
				strengthPotionDosesRemaining);
			king.getUpdateFlags().setActionBubbleNpc(new BubbleNpc(king, ItemId.FULL_STRENGTH_POTION.id()));
			return true;
		}

		private boolean maybeRepotStrength(long tick, Player player) {
			if (!canRepotStrength(tick)) {
				return false;
			}
			if (!drinkStrengthPotion()) {
				return false;
			}
			nextStrengthPotionTick = tick + DM_KING_STRENGTH_REPOT_DELAY_TICKS;
			if (player != null && player.loggedIn()) {
				player.message("@mag@" + DM_KING_DISPLAY_NAME + " drinks a strength potion. "
					+ strengthPotionDosesRemaining + " doses left.");
			}
			return true;
		}

		private boolean canRepotStrength(long tick) {
			return tick >= nextStrengthPotionTick
				&& strengthPotionBoostedLevel > 0
				&& VoidArenaSirPolicy.canDrinkStrengthPotion(strengthPotionDosesRemaining)
				&& king.getSkills().getLevel(Skill.STRENGTH.id()) < strengthPotionBoostedLevel;
		}

		private void drainPrayer(Player player) {
			if (prayerStatePoints <= 0) {
				return;
			}
			prayerStatePoints = VoidArenaSirPolicy.prayerPointsAfterDrain(prayerStatePoints,
				VoidArena.this.dmKingPrayerDrainPerTick());
			if (prayerStatePoints == 0 && !announcedPrayerDepleted) {
				announcedPrayerDepleted = true;
				if (player != null && player.loggedIn()) {
					player.message("@mag@" + DM_KING_DISPLAY_NAME
						+ " has run out of prayer points.");
				}
			}
		}

		private boolean maybeRetreatToEat(long tick, Player player) {
			if (!canRetreatToEat(tick, player)) {
				return false;
			}
			eatingToFull = true;
			king.getBehavior().retreat(1);
			markPvpStyleRetreat(player, tick);
			nextEatTick = tick + 1;
			nextReengageTick = Math.max(nextReengageTick, tick + DM_KING_REENGAGE_DELAY_TICKS);
			return true;
		}

		private boolean canRetreatToEat(long tick, Player player) {
			return king.inCombat() && tick >= nextEatTick && shouldStartEating(player)
				&& king.getOpponent() == player
				&& VoidArenaSirPolicy.retreatHitGatePassed(player.getHitsMade());
		}

		private boolean maybeEat(long tick, Player player) {
			if (!canEat(tick, player)) {
				return false;
			}
			int hits = king.getSkills().getLevel(Skill.HITS.id());
			int healed = Math.min(dmKingMaxHits(), hits + dmKingSwordfishHeal());
			if (healed <= hits) {
				return false;
			}
			king.getSkills().setLevel(Skill.HITS.id(), healed);
			fishRemaining = VoidArenaSirPolicy.fishRemainingAfterEat(fishRemaining);
			eatingToFull = fishRemaining > 0 && healed < dmKingMaxHits();
			nextEatTick = tick + DM_KING_EAT_DELAY_TICKS;
			nextReengageTick = Math.max(nextReengageTick,
				tick + (eatingToFull ? DM_KING_EAT_DELAY_TICKS : DM_KING_REENGAGE_DELAY_TICKS));
			king.getUpdateFlags().setActionBubbleNpc(new BubbleNpc(king, ItemId.SWORDFISH.id()));
			player.message("@mag@" + DM_KING_DISPLAY_NAME + " eats a swordfish. " + fishRemaining + " left.");
			return true;
		}

		private boolean canEat(long tick, Player player) {
			if (king.inCombat() || tick < nextEatTick || !shouldContinueEating(player)) {
				return false;
			}
			int hits = king.getSkills().getLevel(Skill.HITS.id());
			return Math.min(dmKingMaxHits(), hits + dmKingSwordfishHeal()) > hits;
		}

		private boolean shouldContinueEating(Player player) {
			return canUseSwordfish() && (eatingToFull || shouldStartEating(player));
		}

		private boolean shouldStartEating(Player player) {
			if (!canUseSwordfish()) {
				return false;
			}
			int hits = king.getSkills().getLevel(Skill.HITS.id());
			int maxHits = dmKingMaxHits();
			int missing = maxHits - hits;
			int heal = dmKingSwordfishHeal();
			return missing >= heal || hits <= playerMeleeMaxHit(player) + heal;
		}

		private boolean canUseSwordfish() {
			int hits = king.getSkills().getLevel(Skill.HITS.id());
			return VoidArenaSirPolicy.canEatFish(fishRemaining)
				&& hits > 0 && hits < dmKingMaxHits() && dmKingSwordfishHeal() > 0;
		}

		private int dmKingMaxHits() {
			return king.getSkills().getMaxStat(Skill.HITS.id());
		}

		private int dmKingSwordfishHeal() {
			return world.getServer().getEntityHandler().getItemEdibleHeals(ItemId.SWORDFISH.id());
		}

		private int playerMeleeMaxHit(Player player) {
			return CombatFormula.calculateMeleeMaxHit(player, king);
		}

		private boolean maybeCast(long tick, Player player) {
			if (!canCast(tick, player)) {
				return false;
			}
			int damage = CombatFormula.calculateMagicDamage(DM_KING_FIRE_BLAST_POWER, king, player);
			world.getServer().getGameEventHandler().add(new ProjectileEvent(world, king, player,
				damage, DM_KING_FIRE_BLAST_PROJECTILE, false));
			castsUsed = VoidArenaSirPolicy.castsUsedAfterFireBlast(castsUsed);
			nextCastTick = tick + dmKingCastDelayTicks();
			return true;
		}

		private boolean canCast(long tick, Player player) {
			return VoidArenaSirPolicy.canCastFireBlast(castsUsed)
				&& tick >= nextCastTick
				&& (!world.getServer().getConfig().BLOCK_USE_MAGIC_IN_COMBAT || !king.inCombat())
				&& canUsePvpOffense(tick, player)
				&& canCastAt(player);
		}

		private long dmKingCastDelayTicks() {
			return VoidArena.this.dmKingCastDelayTicks();
		}

		private boolean canCastAt(Player player) {
			return king.withinRange(player, player.getConfig().SPELL_RANGE_DISTANCE)
				&& PathValidation.checkPath(world, king.getLocation(), player.getLocation());
		}

		private boolean maybeKiteForMagic(long tick, Player player) {
			if (!canKiteForMagic(tick, player)) {
				return false;
			}
			if (king.inCombat()) {
				king.getBehavior().retreat(1);
				markPvpStyleRetreat(player, tick);
				nextKiteTick = tick + DM_KING_KITE_DELAY_TICKS;
				nextReengageTick = Math.max(nextReengageTick, tick + DM_KING_REENGAGE_DELAY_TICKS);
				return true;
			}
			if (isGoodMagicKitePosition(player)) {
				nextKiteTick = tick + DM_KING_KITE_DELAY_TICKS;
				nextReengageTick = Math.max(nextReengageTick, tick + 1);
				return true;
			}
			Point next = bestMagicKiteStep(player);
			if (next == null) {
				return false;
			}
			king.walk(next.getX(), next.getY());
			nextKiteTick = tick + DM_KING_KITE_DELAY_TICKS;
			nextReengageTick = Math.max(nextReengageTick, tick + DM_KING_REENGAGE_DELAY_TICKS);
			return true;
		}

		private boolean canKiteForMagic(long tick, Player player) {
			if (tick < nextKiteTick || !shouldPreferMagicKite(tick, player)) {
				return false;
			}
			if (king.inCombat()) {
				return king.getOpponent() == player
					&& VoidArenaSirPolicy.retreatHitGatePassed(player.getHitsMade());
			}
			return isGoodMagicKitePosition(player) || bestMagicKiteStep(player) != null;
		}

		private boolean shouldPreferMagicKite(long tick, Player player) {
			if (!VoidArenaSirPolicy.canCastFireBlast(castsUsed)) {
				return false;
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return false;
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= DM_KING_MELEE_COMMIT_HITS
				&& tick < nextCastTick) {
				return false;
			}
			if (tick + DM_KING_KITE_DELAY_TICKS >= nextCastTick) {
				return true;
			}
			return shouldStartEating(player) || shouldAvoidMeleeWithoutFood(player);
		}

		private boolean shouldAvoidMeleeWithoutFood(Player player) {
			return !canUseSwordfish()
				&& king.getSkills().getLevel(Skill.HITS.id()) <= Math.max(1, playerMeleeMaxHit(player) * 2);
		}

		private void maybeIdleRoam(long tick, Player player) {
			if (tick < nextIdleRoamTick || !king.finishedPath() || king.inCombat()) {
				return;
			}
			VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
			ThreadLocalRandom random = ThreadLocalRandom.current();
			Point playerPoint = player.getLocation();
			for (int attempt = 0; attempt < 12; attempt++) {
				Point candidate = Point.location(
					random.nextInt(slot.minX, slot.maxX + 1),
					random.nextInt(slot.minY, slot.maxY + 1));
				if (candidate.equals(playerPoint) || candidate.equals(king.getLocation())
					|| !isWalkablePressureTile(candidate)
					|| !PathValidation.checkPath(world, king.getLocation(), candidate)) {
					continue;
				}
				king.walk(candidate.getX(), candidate.getY());
				break;
			}
			nextIdleRoamTick = tick + DM_KING_IDLE_ROAM_DELAY_TICKS;
		}

		private boolean isGoodMagicKitePosition(Player player) {
			int distance = king.getLocation().getDistancePythagoras(player.getLocation());
			return distance >= DM_KING_KITE_MIN_DISTANCE
				&& distance <= player.getConfig().SPELL_RANGE_DISTANCE
				&& PathValidation.checkPath(world, king.getLocation(), player.getLocation());
		}

		private Point bestMagicKiteStep(Player player) {
			Point current = king.getLocation();
			Point playerPoint = player.getLocation();
			int spellRange = player.getConfig().SPELL_RANGE_DISTANCE;
			VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
			Point best = null;
			int bestScore = Integer.MIN_VALUE;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dy == 0) {
						continue;
					}
					Point candidate = Point.location(current.getX() + dx, current.getY() + dy);
					if (!slot.contains(candidate) || candidate.equals(playerPoint)
						|| !PathValidation.checkAdjacent(king, current, candidate)
						|| !PathValidation.checkPath(world, candidate, playerPoint)) {
						continue;
					}
					int distance = candidate.getDistancePythagoras(playerPoint);
					if (distance > spellRange) {
						continue;
					}
					int score = distance * 100;
					if (distance >= DM_KING_KITE_MIN_DISTANCE) {
						score += 50;
					}
					if (Math.abs(candidate.getX() - playerPoint.getX())
						+ Math.abs(candidate.getY() - playerPoint.getY())
						> Math.abs(current.getX() - playerPoint.getX())
							+ Math.abs(current.getY() - playerPoint.getY())) {
						score += 15;
					}
					if (score > bestScore) {
						bestScore = score;
						best = candidate;
					}
				}
			}
			return best;
		}

		private void maybeReengage(long tick, Player player) {
			if (!canReengage(tick, player)) {
				return;
			}
			nextReengageTick = tick + DM_KING_REENGAGE_DELAY_TICKS;
			if (canStartMelee(player)) {
				king.getBehavior().setChasing(player);
				king.startCombat(player);
				return;
			}
			Point pressureTile = bestMeleePressureTile(player);
			if (pressureTile != null) {
				king.walkToEntityAStar(pressureTile.getX(), pressureTile.getY(), 20);
			} else {
				king.walkToEntityAStar(player.getX(), player.getY(), 20);
			}
		}

		private boolean canReengage(long tick, Player player) {
			return player != null && tick >= nextReengageTick && !king.inCombat()
				&& canUsePvpOffense(tick, player);
		}

		private void maybeFightQuip(long tick, Player player) {
			observePlayerCasts(tick, player);
			if (tick < nextQuipTick || king.getUpdateFlags().hasChatMessage()) {
				return;
			}
			int playerFish = player.getCarriedItems().getInventory().countId(ItemId.SWORDFISH.id());
			int playerHits = player.getSkills().getLevel(Skill.HITS.id());
			int playerMaxHits = Math.max(1, player.getSkills().getMaxStat(Skill.HITS.id()));
			if (!quippedPlayerNoFood && playerFish <= 0) {
				quippedPlayerNoFood = sayFightQuip(player, tick, randomLine(DM_KING_NO_FOOD_QUIPS));
				return;
			}
			if (!quippedPlayerLowFood && playerFish > 0
				&& playerFish <= Math.max(2, playerFishCapacity / 4)) {
				quippedPlayerLowFood = sayFightQuip(player, tick, randomLine(DM_KING_LOW_FOOD_QUIPS));
				return;
			}
			if (!quippedPlayerLowHits && playerHits <= Math.max(12, playerMaxHits / 4)) {
				quippedPlayerLowHits = sayFightQuip(player, tick, randomLine(DM_KING_LOW_HITS_QUIPS));
				return;
			}
			if (!quippedKingLowFood && fishRemaining > 0 && fishRemaining <= Math.max(2, fishCapacity / 4)) {
				quippedKingLowFood = sayFightQuip(player, tick, randomLine(DM_KING_OWN_LOW_FOOD_QUIPS));
				return;
			}
			if (!quippedPlayerName && ThreadLocalRandom.current().nextInt(28) == 0) {
				quippedPlayerName = sayFightQuip(player, tick, playerNameLine(player));
				return;
			}
			if (ThreadLocalRandom.current().nextInt(DM_KING_RANDOM_QUIP_CHANCE) == 0) {
				sayFightQuip(player, tick, randomLine(DM_KING_RANDOM_FIGHT_QUIPS));
			}
		}

		private void observePlayerCasts(long tick, Player player) {
			int deathRunes = player.getCarriedItems().getInventory().countId(ItemId.DEATH_RUNE.id());
			if (lastPlayerDeathRunes < 0) {
				lastPlayerDeathRunes = deathRunes;
				return;
			}
			if (deathRunes < lastPlayerDeathRunes) {
				pendingPlayerCastCheckTick = tick + 2;
				pendingPlayerCastKingHits = king.getSkills().getLevel(Skill.HITS.id());
			}
			lastPlayerDeathRunes = deathRunes;
			if (pendingPlayerCastCheckTick >= 0 && tick >= pendingPlayerCastCheckTick) {
				if (pendingPlayerCastKingHits > 0
					&& king.getSkills().getLevel(Skill.HITS.id()) >= pendingPlayerCastKingHits) {
					sayFightQuip(player, tick, randomLine(DM_KING_MISSED_CAST_QUIPS));
				}
				pendingPlayerCastCheckTick = -1;
				pendingPlayerCastKingHits = -1;
			}
		}

		private boolean sayFightQuip(Player player, long tick, String message) {
			if (player == null || !player.loggedIn() || message == null || tick < nextQuipTick
				|| king.getUpdateFlags().hasChatMessage()) {
				return false;
			}
			king.getUpdateFlags().setChatMessage(new ChatMessage(king, message, player));
			nextQuipTick = tick + DM_KING_QUIP_COOLDOWN_TICKS + ThreadLocalRandom.current().nextInt(4);
			return true;
		}

		private String randomLine(String[] lines) {
			return lines[ThreadLocalRandom.current().nextInt(lines.length)];
		}

		private String playerNameLine(Player player) {
			return randomLine(DM_KING_NAME_QUIPS).replace("%s", player.getUsername());
		}

		private boolean canStartMelee(Player player) {
			return player != null && !player.inCombat()
				&& king.withinRange(player, 1)
				&& PathValidation.checkAdjacentDistance(world, king.getLocation(), player.getLocation(), true, false);
		}

		private Point bestMeleePressureTile(Player player) {
			Point playerPoint = player.getLocation();
			Point current = king.getLocation();
			VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
			Point best = null;
			int bestScore = Integer.MIN_VALUE;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					if (dx == 0 && dy == 0) {
						continue;
					}
					Point candidate = Point.location(playerPoint.getX() + dx, playerPoint.getY() + dy);
					if (!slot.contains(candidate) || !isWalkablePressureTile(candidate)
						|| !PathValidation.checkAdjacentDistance(world, candidate, playerPoint, true, false)) {
						continue;
					}
					int distance = candidate.getDistancePythagoras(current);
					int score = -distance * 100;
					if (PathValidation.checkPath(world, current, candidate)) {
						score += 25;
					}
					if (score > bestScore) {
						bestScore = score;
						best = candidate;
					}
				}
			}
			return best;
		}

		private boolean isWalkablePressureTile(Point point) {
			if (point == null) {
				return false;
			}
			if (point.equals(king.getLocation())) {
				return true;
			}
			if (world.getTile(point.getX(), point.getY()) == null) {
				return false;
			}
			if ((world.getTile(point.getX(), point.getY()).traversalMask & CollisionFlag.FULL_BLOCK) != 0) {
				return false;
			}
			return !PathValidation.isMobBlocking(king, point.getX(), point.getY());
		}

		private boolean canUsePvpOffense(long tick, Player player) {
			return player != null && VoidArenaSirPolicy.sharedReattackReady(tick,
				king.getRanAwayTimer(), player.getRanAwayTimer(),
				VoidArena.this.pvpReattackDelayTicks());
		}

		private void markPvpStyleRetreat(Player player, long tick) {
			king.setRanAwayTimer();
			if (player != null) {
				player.setRanAwayTimer();
			}
			long readyTick = tick + VoidArena.this.pvpReattackDelayTicks();
			nextCastTick = Math.max(nextCastTick, readyTick);
			nextReengageTick = Math.max(nextReengageTick, readyTick);
		}
	}

	private final class DmKingEvent extends GameTickEvent {
		private final DmKingChallenge challenge;

		private DmKingEvent(World world, DmKingChallenge challenge) {
			super(world, null, 1, "Void Arena DM King Challenge", DuplicationStrategy.ALLOW_MULTIPLE);
			this.challenge = challenge;
		}

		@Override
		public void run() {
			challenge.tick();
		}
	}

	private static final class DmKingSummary {
		private final int playerFishRemaining;
		private final int playerFishCapacity;
		private final int kingFishRemaining;
		private final int kingFishCapacity;
		private final long castDelayTicks;
		private final long castDelayMs;
		private final int castsUsed;
		private final int potionDosesRemaining;
		private final int prayerStatePoints;

		private DmKingSummary(int playerFishRemaining, int playerFishCapacity, int kingFishRemaining,
			int kingFishCapacity, long castDelayTicks, long castDelayMs, int castsUsed,
			int potionDosesRemaining, int prayerStatePoints) {
			this.playerFishRemaining = playerFishRemaining;
			this.playerFishCapacity = playerFishCapacity;
			this.kingFishRemaining = kingFishRemaining;
			this.kingFishCapacity = kingFishCapacity;
			this.castDelayTicks = castDelayTicks;
			this.castDelayMs = castDelayMs;
			this.castsUsed = castsUsed;
			this.potionDosesRemaining = potionDosesRemaining;
			this.prayerStatePoints = prayerStatePoints;
		}
	}

	private static final class DmKingRecord {
		private final int wins;
		private final int losses;

		private DmKingRecord(int wins, int losses) {
			this.wins = wins;
			this.losses = losses;
		}
	}

	private static final class PendingRankedSettlement {
		private final VoidArenaMatchSessionRecord record;
		private final VoidArenaStats statsA;
		private final VoidArenaStats statsB;
		private final VoidArenaRankedPolicy.ResultType resultType;
		private final long winnerHash;
		private final long loserHash;
		private final boolean winnerIsA;
		private final int delta;
		private final int oldWinnerRating;
		private final int oldLoserRating;
		private int retryAttempts;
		private boolean retryScheduled;
		private boolean databaseError;
		private boolean complete;

		private PendingRankedSettlement(VoidArenaMatchSessionRecord record,
			VoidArenaStats statsA, VoidArenaStats statsB,
			VoidArenaRankedPolicy.ResultType resultType, long winnerHash, long loserHash,
			boolean winnerIsA, int delta, int oldWinnerRating, int oldLoserRating) {
			this.record = record;
			this.statsA = statsA;
			this.statsB = statsB;
			this.resultType = resultType;
			this.winnerHash = winnerHash;
			this.loserHash = loserHash;
			this.winnerIsA = winnerIsA;
			this.delta = delta;
			this.oldWinnerRating = oldWinnerRating;
			this.oldLoserRating = oldLoserRating;
		}

		private static PendingRankedSettlement decisive(VoidArenaMatchSessionRecord record,
			VoidArenaStats statsA, VoidArenaStats statsB,
			VoidArenaRankedPolicy.ResultType resultType, long winnerHash, long loserHash,
			boolean winnerIsA, int delta, int oldWinnerRating, int oldLoserRating) {
			return new PendingRankedSettlement(record, statsA, statsB, resultType,
				winnerHash, loserHash, winnerIsA, delta, oldWinnerRating, oldLoserRating);
		}

		private static PendingRankedSettlement neutral(VoidArenaMatchSessionRecord record,
			VoidArenaRankedPolicy.ResultType resultType) {
			return new PendingRankedSettlement(record, null, null, resultType,
				0L, 0L, false, 0, 0, 0);
		}

		private synchronized boolean isComplete() {
			return complete;
		}

		private synchronized boolean markComplete() {
			if (complete) {
				return false;
			}
			complete = true;
			retryScheduled = false;
			return true;
		}

		private synchronized void markDatabaseError() {
			databaseError = true;
		}

		private synchronized boolean hadDatabaseError() {
			return databaseError;
		}

		private synchronized int reserveRetryDelay() {
			if (complete || retryScheduled) {
				return -1;
			}
			long multiplier = 1L << Math.min(retryAttempts, 4);
			long delay = Math.min(RANKED_SETTLEMENT_RETRY_MAX_MS,
				RANKED_SETTLEMENT_RETRY_INITIAL_MS * multiplier);
			retryAttempts++;
			retryScheduled = true;
			return (int) delay;
		}

		private synchronized void retryStarted() {
			retryScheduled = false;
		}
	}

	private static final class MatchTerminalClaim {
		private final Player winner;
		private final Player loser;
		private final VoidArenaRankedPolicy.ResultType resultType;

		private MatchTerminalClaim(Player winner, Player loser,
			VoidArenaRankedPolicy.ResultType resultType) {
			this.winner = winner;
			this.loser = loser;
			this.resultType = resultType;
		}
	}

	private final class Match {
		private final UUID sessionId = UUID.randomUUID();
		private final int slotIndex;
		private final Player playerA;
		private final Player playerB;
		private final long playerAHash;
		private final long playerBHash;
		private final MatchRules rules;
		private final long createdAt;
		private final long startsAt;
		private final RankedAdmission rankedAdmission;
		private final boolean sameIp;
		private final boolean sameIpLocalExempt;
		private final VoidArenaMatchSessionRecord rankedSessionRecord;
		private volatile PendingRankedSettlement pendingSettlement;
		private volatile Player disconnectedPlayer;
		private volatile long disconnectedPlayerHash;
		private volatile long disconnectedAtMs;
		private volatile boolean finished;

		private Match(int slotIndex, Player playerA, Player playerB, MatchRules rules, long startsAt,
						  RankedAdmission rankedAdmission) {
			this.slotIndex = slotIndex;
			this.playerA = Objects.requireNonNull(playerA, "playerA");
			this.playerB = Objects.requireNonNull(playerB, "playerB");
			this.playerAHash = playerA.getUsernameHash();
			this.playerBHash = playerB.getUsernameHash();
			this.rules = rules;
			this.createdAt = System.currentTimeMillis();
			this.startsAt = startsAt;
			this.rankedAdmission = rankedAdmission;
			this.sameIp = rankedAdmission != null && VoidArenaRankedPolicy.sameAddress(
				rankedAdmission.playerA.getCurrentIP(), rankedAdmission.playerB.getCurrentIP());
			this.sameIpLocalExempt = sameIp;
			this.rankedSessionRecord = rankedAdmission == null ? null
				: VoidArenaMatchSessionRecord.active(sessionId.toString(), rankedAdmission.seasonId,
					rankedAdmission.playerA.getDatabaseID(), rankedAdmission.playerB.getDatabaseID(),
					rankedAdmission.playerAStats.rating, rankedAdmission.playerBStats.rating,
					sameIp, sameIpLocalExempt, 0, rankedAdmission.audit.decisiveResultsUtcDay,
					slotIndex, createdAt);
		}

		private VoidArenaMatchSessionRecord activeSessionRecord() {
			return rankedSessionRecord;
		}

		private VoidArenaMatchSessionRecord settledSessionRecord(
			VoidArenaRankedPolicy.ResultType resultType, Integer winnerId, Integer loserId,
			int playerARatingAfter, int playerBRatingAfter, int ratingDelta,
			boolean ratingApplied, long endedAt) {
			return rankedSessionRecord.settled(resultType.name(), winnerId, loserId,
				playerARatingAfter, playerBRatingAfter, ratingDelta, ratingApplied, endedAt);
		}

		private boolean contains(Player player) {
			return player != null
				&& (player.getUsernameHash() == playerAHash || player.getUsernameHash() == playerBHash);
		}

		private boolean isInsideAssignedCage(Player player) {
			return VoidArenaConfig.arenaSlot(slotIndex).contains(player.getLocation());
		}

		private Point startFor(Player player) {
			if (player != null && player.getUsernameHash() == playerAHash) {
				return VoidArenaConfig.arenaSlot(slotIndex).startA();
			}
			return VoidArenaConfig.arenaSlot(slotIndex).startB();
		}

		private boolean hasStarted() {
			return System.currentTimeMillis() >= startsAt;
		}

		private synchronized void noteDisconnect(Player player, long disconnectedAtMs) {
			long playerHash = player == null ? 0L : player.getUsernameHash();
			if (finished || disconnectedPlayerHash != 0L
				|| (playerHash != playerAHash && playerHash != playerBHash)) {
				return;
			}
			disconnectedPlayer = player;
			disconnectedPlayerHash = playerHash;
			this.disconnectedAtMs = disconnectedAtMs;
		}

		private boolean hasPendingDisconnect() {
			return disconnectedPlayerHash != 0L;
		}

		private long disconnectedAtMs() {
			return disconnectedAtMs;
		}

		private Player disconnectedPlayer() {
			return disconnectedPlayer;
		}

		private synchronized MatchTerminalClaim claimTerminal(Player requestedWinner,
			Player requestedLoser, VoidArenaRankedPolicy.ResultType requestedResult) {
			if (finished) {
				return null;
			}
			Player winner = requestedWinner;
			Player loser = requestedLoser;
			VoidArenaRankedPolicy.ResultType result = requestedResult;
			if (disconnectedPlayerHash != 0L) {
				loser = disconnectedPlayerHash == playerAHash ? playerA : playerB;
				winner = loser == playerA ? playerB : playerA;
				result = VoidArenaRankedPolicy.ResultType.FORFEIT;
			}
			finished = true;
			return new MatchTerminalClaim(winner, loser, result);
		}

		private boolean isFinished() {
			return finished;
		}

		private synchronized void setPendingSettlement(PendingRankedSettlement pending) {
			if (pendingSettlement != null) {
				throw new IllegalStateException("Ranked settlement is already captured");
			}
			pendingSettlement = pending;
		}

		private PendingRankedSettlement pendingSettlement() {
			return pendingSettlement;
		}

		private Player opponent(Player player) {
			return player != null && player.getUsernameHash() == playerAHash ? playerB : playerA;
		}
	}

	private static final class MatchRules {
		private final boolean ranked;
		private final boolean f2pOnly;
		private final boolean allowPrayer;
		private final boolean allowRanged;
		private final boolean allowMagic;

		private MatchRules(boolean ranked, boolean f2pOnly,
						   boolean allowPrayer, boolean allowRanged, boolean allowMagic) {
			this.ranked = ranked;
			this.f2pOnly = f2pOnly;
			this.allowPrayer = allowPrayer;
			this.allowRanged = allowRanged;
			this.allowMagic = allowMagic;
		}

		private static MatchRules ranked() {
			return new MatchRules(true, true, true, true, true);
		}

		private static MatchRules unrankedDefault() {
			return new MatchRules(false, true, true, true, true);
		}

		private static MatchRules fromMask(int mask) {
			if ((mask & RULE_RANKED) != 0) {
				return ranked();
			}
			return new MatchRules(false,
				(mask & RULE_F2P_ONLY) != 0,
				(mask & RULE_ALLOW_PRAYER) != 0,
				(mask & RULE_ALLOW_RANGED) != 0,
				(mask & RULE_ALLOW_MAGIC) != 0);
		}

		private String modeName() {
			return ranked ? "ranked" : "unranked";
		}

		private String title() {
			return ranked ? "Ranked Death Match" : "Unranked Death Match";
		}

		private int timeoutMs() {
			return ranked ? RANKED_MATCH_TIMEOUT_MS : UNRANKED_MATCH_TIMEOUT_MS;
		}

		private int timeoutMinutes() {
			return timeoutMs() / 60000;
		}

		private int mask() {
			if (ranked) {
				return RULE_RANKED;
			}
			int mask = 0;
			if (f2pOnly) {
				mask |= RULE_F2P_ONLY;
			}
			if (allowPrayer) {
				mask |= RULE_ALLOW_PRAYER;
			}
			if (allowRanged) {
				mask |= RULE_ALLOW_RANGED;
			}
			if (allowMagic) {
				mask |= RULE_ALLOW_MAGIC;
			}
			return mask;
		}

		private String summary() {
			if (ranked) {
				return "Ranked F2P, prayer on, ranged on, magic on";
			}
			return (f2pOnly ? "F2P" : "P2P")
				+ ", prayer " + onOff(allowPrayer)
				+ ", ranged " + onOff(allowRanged)
				+ ", magic " + onOff(allowMagic);
		}

		private static String onOff(boolean value) {
			return value ? "on" : "off";
		}
	}
}
