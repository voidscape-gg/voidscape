package com.openrsc.server.content.voidarena;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.PlayerTitle;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.VoidArenaMatchRecord;
import com.openrsc.server.database.struct.VoidArenaStats;
import com.openrsc.server.event.SingleEvent;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.BubbleNpc;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
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
	private static final int DEATH_MATCH_SETUP_CLIENT_VERSION = 10109;
	private static final int DM_KING_CLIENT_VERSION = 10112;
	private static final int DM_KING_MATCH_TIMEOUT_MS = RANKED_MATCH_TIMEOUT_MS;
	private static final int DM_KING_FISH = 26;
	private static final int DM_KING_FISH_HEAL = 14;
	private static final int DM_KING_FIRE_BLAST_CASTS = 100;
	private static final int DM_KING_AIR_RUNES = DM_KING_FIRE_BLAST_CASTS * 4;
	private static final int DM_KING_DEATH_RUNES = DM_KING_FIRE_BLAST_CASTS;
	private static final int DM_KING_FIRE_RUNES = DM_KING_FIRE_BLAST_CASTS * 5;
	private static final int DM_KING_MAX_HITS = 99;
	private static final int DM_KING_EAT_THRESHOLD = 45;
	private static final int DM_KING_EAT_DELAY_TICKS = 3;
	private static final int DM_KING_REENGAGE_DELAY_TICKS = 2;
	private static final int DM_KING_FIRE_BLAST_PROJECTILE = 1;
	private static final double DM_KING_FIRE_BLAST_POWER = 12.0D;
	private static final int DM_KING_PRAYER_STATE_POINTS = 99 * 120;
	private static final int DM_KING_PRAYER_POINTS = 1;
	private static final String DM_KING_KIT_CACHE = "void_arena_dmking_kit_snapshot";
	private static final String DM_KING_BROADCAST_CACHE = "void_arena_dmking_broadcast";
	private static final String DM_KING_WINS_CACHE = "void_arena_dmking_wins";
	private static final String DM_KING_LOSSES_CACHE = "void_arena_dmking_losses";
	private static final int DM_KING_INITIAL_WINS = 300;
	private static final int DM_KING_INITIAL_LOSSES = 0;
	public static final String DM_KING_DYNAMIC_ATTRIBUTE = "void_arena_dm_king_dynamic";
	public static final String DM_KING_OWNER_ATTRIBUTE = "void_arena_dm_king_owner";
	private static final long SEASON_RESET_CONFIRM_MS = 2 * 60 * 1000;
	private static final String CURRENT_CHAMPION_PLAYER_CACHE = "void_arena_current_champion_player";
	private static final String CURRENT_CHAMPION_RATING_CACHE = "void_arena_current_champion_rating";
	private static final String CURRENT_CHAMPION_AWARDED_AT_CACHE = "void_arena_current_champion_awarded_at";

	private final World world;
	private final Map<Long, Match> activeMatches = new ConcurrentHashMap<>();
	private final Map<Long, DmKingChallenge> activeDmKingChallenges = new ConcurrentHashMap<>();
	private final Map<Long, Challenge> incomingChallenges = new ConcurrentHashMap<>();
	private final Map<Long, DeathMatchSetup> activeSetups = new ConcurrentHashMap<>();
	private final Map<Integer, VoidArenaStats> statsCache = new ConcurrentHashMap<>();
	private final Map<Long, SeasonResetConfirmation> pendingSeasonResets = new ConcurrentHashMap<>();

	public VoidArena(World world) {
		this.world = world;
	}

	public AttackCheck checkAttack(Player attacker, Player victim, boolean missile) {
		Match match = activeMatches.get(attacker.getUsernameHash());
		if (match != null) {
			if (match.contains(victim) && match.isInsideAssignedCage(attacker) && match.isInsideAssignedCage(victim)) {
				if (!match.hasStarted()) {
					return AttackCheck.deny("The Death Match has not started yet.");
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
			if (match.contains(victim) && match.isInsideAssignedCage(attacker) && match.isInsideAssignedCage(victim)) {
				if (!match.hasStarted()) {
					return AttackCheck.deny("The Death Match has not started yet.");
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
		if (dmKingChallenge != null && dmKingChallenge.king == killer) {
			resolveDmKingLoss(dmKingChallenge, loser, "DM King defeated you.", false, true);
			loser.setInstanceId(0);
			return VoidArenaConfig.lobbyTile();
		}

		Player winner = killer instanceof Player ? (Player) killer : null;
		Match match = activeMatches.get(loser.getUsernameHash());
		if (match == null || winner == null || !match.contains(winner)) {
			return null;
		}

		resolveMatch(match, winner, loser, false);
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

		Player opponent = match.opponent(player);
		resolveMatch(match, opponent, player, true);
		sendRatingClear(player);
		player.setInstanceId(0);
		player.setLocation(VoidArenaConfig.exitTile(), true);
	}

	public void handleLogin(Player player) {
		recoverDmKingKit(player);
		if (player == null || !VoidArenaConfig.isInsideVoidArena(player.getLocation())) {
			return;
		}
		sendLobbyRatings(player);
		if (VoidArenaConfig.isInsideLobby(player.getLocation())) {
			broadcastLobbyRating(player);
		}
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
			enterLobby(player);
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

	public synchronized void challengeDmKing(Player player) {
		if (!canStartDmKingChallenge(player, true)) {
			return;
		}
		int slotIndex = firstAvailableSlot();
		if (slotIndex < 0) {
			player.message("All Void Arena cages are occupied right now.");
			return;
		}

		String snapshot = VoidArenaKitSnapshot.capture(player);
		player.getCache().store(DM_KING_KIT_CACHE, snapshot);
		savePlayerCache(player);

		VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
		Npc king = new Npc(world, NpcId.DM_KING_ARENA.id(), slot.startBX, slot.startBY,
			slot.minX, slot.maxX, slot.minY, slot.maxY);
		king.setShouldRespawn(false);
		king.setAttribute(DM_KING_DYNAMIC_ATTRIBUTE, true);
		king.setAttribute(DM_KING_OWNER_ATTRIBUTE, player.getUsernameHash());
		world.registerNpc(king);

		player.resetAll();
		int playerFishCapacity = applyDmKingKit(player);
		restoreHits(player);
		player.setInstanceId(0);
		player.teleportFromVoidArena(slot.startAX, slot.startAY, true);
		removeChallenges(player);

		DmKingChallenge challenge = new DmKingChallenge(slotIndex, player.getUsernameHash(), king,
			System.currentTimeMillis() + MATCH_COUNTDOWN_MS, playerFishCapacity);
		activeDmKingChallenges.put(player.getUsernameHash(), challenge);
		world.getServer().getGameEventHandler().add(challenge.event);

		sendArenaControl(player, "countdown|5");
		player.message("@red@DM King challenge begins in 5 seconds.");
		player.message("Rules: Ranked F2P kit, melee and Fire Blast, finite supplies, no Elo.");
		player.message("Defeat DM King before the 10 minute limit.");
	}

	public AttackCheck checkDmKingNpcAction(Player player, Npc npc, boolean missile) {
		if (npc == null || player == null) {
			return AttackCheck.pass();
		}
		if (npc.getID() == NpcId.DM_KING.id()) {
			return AttackCheck.deny("Challenge DM King from the Void Arena lobby.");
		}
		if (!isDmKingChallengeNpc(npc)) {
			return AttackCheck.pass();
		}

		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge == null || challenge.king != npc) {
			return AttackCheck.deny("That DM King challenge belongs to another fighter.");
		}
		if (!challenge.isInsideAssignedCage(player) || !challenge.isInsideAssignedCage(npc)) {
			return AttackCheck.deny("Return to your assigned DM King cage.");
		}
		if (!challenge.hasStarted()) {
			return AttackCheck.deny("The DM King challenge has not started yet.");
		}
		if (missile) {
			return AttackCheck.deny("Ranged is not part of the DM King challenge kit.");
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
		if (player == null || !player.getCache().hasKey(DM_KING_KIT_CACHE)) {
			return;
		}
		restoreDmKingKit(player);
		player.resetAll();
		player.setInstanceId(0);
		player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		restoreHits(player);
		player.message("@mag@Your DM King challenge kit was restored.");
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
		return challenge != null && challenge.hasPrayer() ? 1.15D : 1.0D;
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

	public boolean blocksTeleport(Player player) {
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null) {
			player.message("You can't teleport during a Death Match.");
			return true;
		}
		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge != null) {
			player.message("You can't teleport during the DM King challenge.");
			return true;
		}
		return false;
	}

	public void enforceMatchBounds(Player player) {
		Match match = activeMatches.get(player.getUsernameHash());
		if (match != null && !match.isInsideAssignedCage(player)) {
			player.resetPath();
			player.message("You cannot leave the Death Match cage.");
			Point start = match.startFor(player);
			player.teleportFromVoidArena(start.getX(), start.getY(), true);
			return;
		}
		DmKingChallenge challenge = activeDmKingChallenges.get(player.getUsernameHash());
		if (challenge != null && !challenge.isInsideAssignedCage(player)) {
			player.resetPath();
			player.message("You cannot leave the DM King cage.");
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

	private void leave(Player player) {
		if (activeMatches.containsKey(player.getUsernameHash())
			|| activeDmKingChallenges.containsKey(player.getUsernameHash())) {
			player.message("You cannot leave during an active Death Match.");
			return;
		}
		sendRatingClear(player);
		restoreHits(player);
		player.setInstanceId(0);
		player.teleportFromVoidArena(VoidArenaConfig.EXIT_X, VoidArenaConfig.EXIT_Y, true);
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
		MatchRules rules = (isRankedEligible(playerA) && isRankedEligible(playerB))
			? MatchRules.ranked()
			: MatchRules.unrankedDefault();
		DeathMatchSetup setup = new DeathMatchSetup(playerA.getUsernameHash(), playerB.getUsernameHash(), rules);
		activeSetups.put(playerA.getUsernameHash(), setup);
		activeSetups.put(playerB.getUsernameHash(), setup);
		removeChallenges(playerA);
		removeChallenges(playerB);
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
			if (requestedRules.ranked && !setup.rankedAvailable()) {
				player.message("Ranked Death Match requires both players to have 99 Attack, Strength, Defense, and Hits.");
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
			removeSetup(setup);
			sendArenaControl(playerA, "close");
			sendArenaControl(playerB, "close");
			startMatch(playerA, playerB, setup.rules);
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
		if (setup.rules.ranked && (!hasRankedStats(playerA, message) || !hasRankedStats(playerB, message))) {
			return false;
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
			if (message) player.message("Your previous DM King challenge kit was restored. Challenge him again when ready.");
			return false;
		}
		if (!isInsideLobby(player)) {
			if (message) player.message("You must be in the Void Arena lobby to challenge DM King.");
			return false;
		}
		if (player.inCombat()) {
			if (message) player.message("You must be out of combat to challenge DM King.");
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
			if (message) player.message("You need the current Voidscape client to challenge DM King.");
			return false;
		}
		if (shouldAutoSetDmKingStats(player)) {
			applyBetaDmKingStats(player);
		} else if (!hasRankedStats(player, message)) {
			return false;
		}
		return true;
	}

	private boolean supportsDmKingChallenge(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= DM_KING_CLIENT_VERSION;
	}

	private boolean shouldAutoSetDmKingStats(Player player) {
		return player != null && player.getConfig().WANT_BETA_ONBOARDING_GUIDE;
	}

	private void applyBetaDmKingStats(Player player) {
		int betaLevel = Math.min(99, player.getConfig().PLAYER_LEVEL_LIMIT);
		int skills = world.getServer().getConstants().getSkills().getSkillsCount();
		boolean changed = false;
		for (int skill = 0; skill < skills; skill++) {
			if (player.getSkills().getMaxStat(skill) == betaLevel
				&& player.getSkills().getLevel(skill) == betaLevel) {
				continue;
			}
			player.getSkills().setLevelTo(skill, betaLevel);
			player.getSkills().setLevel(skill, betaLevel, false);
			changed = true;
		}
		player.setPrayerStatePoints(betaLevel * 120);
		player.checkEquipment();
		player.getSkills().sendUpdateAll();
		ActionSender.sendEquipmentStats(player);
		if (changed) {
			player.message("@gre@Beta DM King challenge: all stats set to " + betaLevel + ".");
		}
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
		equipChallengeItem(player, ItemId.LARGE_RUNE_HELMET.id());
		equipChallengeItem(player, ItemId.RUNE_PLATE_MAIL_BODY.id());
		equipChallengeItem(player, ItemId.RUNE_PLATE_MAIL_LEGS.id());
		equipChallengeItem(player, ItemId.DIAMOND_AMULET_OF_POWER.id());
		equipChallengeItem(player, ItemId.RUNE_2_HANDED_SWORD.id());
		player.getCarriedItems().getInventory().add(new Item(ItemId.FULL_STRENGTH_POTION.id()), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.AIR_RUNE.id(), DM_KING_AIR_RUNES), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.DEATH_RUNE.id(), DM_KING_DEATH_RUNES), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.FIRE_RUNE.id(), DM_KING_FIRE_RUNES), false);
		int playerFishCapacity = player.getCarriedItems().getInventory().getFreeSlots();
		for (int i = 0; i < playerFishCapacity; i++) {
			player.getCarriedItems().getInventory().add(new Item(ItemId.SWORDFISH.id()), false);
		}
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
		player.getUpdateFlags().setAppearanceChanged(true);
		return playerFishCapacity;
	}

	private void equipChallengeItem(Player player, int itemId) {
		if (!player.getCarriedItems().getInventory().add(new Item(itemId), false)) {
			return;
		}
		Item equipped = player.getCarriedItems().getInventory()
			.get(player.getCarriedItems().getInventory().size() - 1);
		if (equipped == null) {
			return;
		}
		equipped.setWielded(true);
		ItemDefinition def = equipped.getDef(player.getWorld());
		if (def != null && def.getWieldPosition() < 12) {
			player.updateWornItems(def.getWieldPosition(), def.getAppearanceId(), def.getWearableId(), true);
		}
	}

	private void resolveDmKingVictory(DmKingChallenge challenge, Player player) {
		DmKingSummary summary = captureDmKingSummary(challenge, player);
		activeDmKingChallenges.remove(challenge.playerHash, challenge);
		challenge.finished = true;
		challenge.event.stop();
		cleanupDmKingNpc(challenge.king);
		DmKingRecord record = recordDmKingResult(false);
		finishDmKingSession(player, true);
		player.playerServerMessage(MessageType.QUEST,
			"@mag@DM King defeated: @whi@You beat the perfect death matcher.");
		sendDmKingSummary(player, summary);
		sendDmKingRecordPayload(player, record);
		broadcastDmKingRecord(record);
		PlayerTitle.unlock(player, PlayerTitle.DM_KINGSLAYER);
		if (!player.getCache().hasKey(DM_KING_BROADCAST_CACHE)) {
			player.getCache().store(DM_KING_BROADCAST_CACHE, true);
			savePlayerCache(player);
			world.sendWorldMessage("@mag@" + player.getUsername() + " has defeated DM King in the Void Arena!");
		}
	}

	private void resolveDmKingLoss(DmKingChallenge challenge, Player player, String message, boolean disconnectLoss,
								   boolean recordDmKingWin) {
		DmKingSummary summary = captureDmKingSummary(challenge, player);
		activeDmKingChallenges.remove(challenge.playerHash, challenge);
		challenge.finished = true;
		challenge.event.stop();
		cleanupDmKingNpc(challenge.king);
		DmKingRecord record = recordDmKingWin ? recordDmKingResult(true) : null;
		finishDmKingSession(player, !disconnectLoss);
		if (message != null && !message.isEmpty() && player != null && player.loggedIn()) {
			player.playerServerMessage(MessageType.QUEST, "@mag@DM King challenge ended: @whi@" + message);
			sendDmKingSummary(player, summary);
			sendDmKingRecordPayload(player, record);
		}
		if (record != null) {
			broadcastDmKingRecord(record);
		}
	}

	private void expireDmKingChallenge(DmKingChallenge challenge) {
		if (activeDmKingChallenges.get(challenge.playerHash) != challenge) {
			return;
		}
		Player player = world.getPlayer(challenge.playerHash);
		resolveDmKingLoss(challenge, player, "time limit reached.", false, true);
	}

	private void finishDmKingSession(Player player, boolean moveToLobby) {
		if (player == null) {
			return;
		}
		sendArenaControl(player, "close");
		player.resetAll();
		player.setInstanceId(0);
		restoreDmKingKit(player);
		if (moveToLobby) {
			player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		}
		restoreHits(player);
		ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
	}

	private DmKingSummary captureDmKingSummary(DmKingChallenge challenge, Player player) {
		int playerFish = player == null ? 0
			: player.getCarriedItems().getInventory().countId(ItemId.SWORDFISH.id());
		int playerFishCapacity = challenge == null ? playerFish
			: Math.max(playerFish, challenge.playerFishCapacity);
		int kingFish = challenge == null ? 0 : Math.max(0, challenge.fishRemaining);
		int castsUsed = challenge == null ? 0
			: Math.max(0, Math.min(DM_KING_FIRE_BLAST_CASTS, DM_KING_FIRE_BLAST_CASTS - challenge.castsRemaining));
		long castTicks = challenge == null ? dmKingCastDelayTicks() : challenge.dmKingCastDelayTicks();
		long castMs = castTicks * Math.max(1, world.getServer().getConfig().GAME_TICK);
		return new DmKingSummary(playerFish, playerFishCapacity, kingFish, castTicks, castMs, castsUsed);
	}

	private void sendDmKingSummary(Player player, DmKingSummary summary) {
		if (player == null || !player.loggedIn() || summary == null) {
			return;
		}
		String tickLabel = summary.castDelayTicks == 1 ? "tick" : "ticks";
		player.playerServerMessage(MessageType.QUEST,
			"@mag@DM King supplies: @whi@Food left - you " + summary.playerFishRemaining
				+ "/" + summary.playerFishCapacity + ", DM King " + summary.kingFishRemaining + "/" + DM_KING_FISH + ".");
		player.playerServerMessage(MessageType.QUEST,
			"@mag@DM King cast speed: @whi@Fire Blast every " + summary.castDelayMs
				+ "ms (" + summary.castDelayTicks + " " + tickLabel + "); casts used "
				+ summary.castsUsed + "/" + DM_KING_FIRE_BLAST_CASTS + ".");
	}

	private long dmKingCastDelayTicks() {
		int gameTick = Math.max(1, world.getServer().getConfig().GAME_TICK);
		int castDelay = world.getServer().getConfig().RAPID_CAST_SPELLS
			? 0
			: Math.max(0, world.getServer().getConfig().MILLISECONDS_BETWEEN_CASTS);
		return Math.max(1, (castDelay + gameTick - 1L) / gameTick);
	}

	private void restoreDmKingKit(Player player) {
		if (player == null || !player.getCache().hasKey(DM_KING_KIT_CACHE)) {
			return;
		}
		String snapshot = player.getCache().getString(DM_KING_KIT_CACHE);
		VoidArenaKitSnapshot.restore(player, snapshot);
		player.getCache().remove(DM_KING_KIT_CACHE);
		savePlayerCache(player);
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
	}

	private void savePlayerCache(Player player) {
		try {
			world.getServer().getPlayerService().savePlayerCache(player);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to save Void Arena player cache for {}", player.getUsername(), e);
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
		DmKingRecord current = loadDmKingRecord();
		DmKingRecord next = dmKingWon
			? new DmKingRecord(current.wins + 1, current.losses)
			: new DmKingRecord(current.wins, current.losses + 1);
		saveDmKingRecord(next);
		return next;
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

	private void saveDmKingRecord(DmKingRecord record) {
		try {
			world.getServer().getDatabase().querySaveGlobalCacheInt(DM_KING_WINS_CACHE, record.wins);
			world.getServer().getDatabase().querySaveGlobalCacheInt(DM_KING_LOSSES_CACHE, record.losses);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to save DM King record", e);
		}
	}

	private boolean supportsDeathMatchSetup(Player player) {
		return player != null && player.isUsingCustomClient()
			&& player.getClientVersion() >= DEATH_MATCH_SETUP_CLIENT_VERSION;
	}

	private boolean isRankedEligible(Player player) {
		return hasRankedStats(player, false);
	}

	private boolean hasRankedStats(Player player, boolean message) {
		if (player.getSkills().getMaxStat(Skill.ATTACK.id()) < 99
			|| player.getSkills().getMaxStat(Skill.STRENGTH.id()) < 99
			|| player.getSkills().getMaxStat(Skill.DEFENSE.id()) < 99
			|| player.getSkills().getMaxStat(Skill.HITS.id()) < 99) {
			if (message) {
				player.message("Ranked Death Match requires 99 Attack, Strength, Defense, and Hits.");
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

	private synchronized void startMatch(Player playerA, Player playerB, MatchRules rules) {
		int slotIndex = firstAvailableSlot();
		if (slotIndex < 0) {
			playerA.message("All Void Arena cages are occupied right now.");
			playerB.message("All Void Arena cages are occupied right now.");
			return;
		}

		VoidArenaConfig.ArenaSlot slot = VoidArenaConfig.arenaSlot(slotIndex);
		Match match = new Match(slotIndex, playerA.getUsernameHash(), playerB.getUsernameHash(), rules,
			System.currentTimeMillis() + MATCH_COUNTDOWN_MS);
		activeMatches.put(playerA.getUsernameHash(), match);
		activeMatches.put(playerB.getUsernameHash(), match);
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
	}

	private void scheduleMatchTimeout(final Match match) {
		world.getServer().getGameEventHandler().add(new SingleEvent(world, null, match.rules.timeoutMs(),
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

		activeMatches.remove(match.playerAHash);
		activeMatches.remove(match.playerBHash);
		Player playerA = world.getPlayer(match.playerAHash);
		Player playerB = world.getPlayer(match.playerBHash);
		returnExpiredPlayer(playerA, match.rules);
		returnExpiredPlayer(playerB, match.rules);
	}

	private void returnExpiredPlayer(Player player, MatchRules rules) {
		if (player == null) {
			return;
		}
		returnToLobby(player);
		player.playerServerMessage(MessageType.QUEST,
			"@mag@" + rules.title() + " ended: @whi@time limit reached.");
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

	private void resolveMatch(Match match, Player winner, Player loser, boolean disconnectLoss) {
		activeMatches.remove(match.playerAHash);
		activeMatches.remove(match.playerBHash);
		if (winner == null || loser == null) {
			return;
		}

		if (!match.rules.ranked) {
			returnToLobby(winner);
			finishArenaSession(loser, false);
			winner.playerServerMessage(MessageType.QUEST,
				"@mag@Unranked Death Match win: @whi@You defeated " + loser.getUsername() + ".");
			loser.playerServerMessage(MessageType.QUEST,
				"@mag@Unranked Death Match loss: @whi@" + winner.getUsername() + " defeated you.");
			if (disconnectLoss) {
				winner.message(loser.getUsername() + " forfeited by leaving the Death Match.");
			}
			return;
		}

		VoidArenaStats winnerStats = getStats(winner);
		VoidArenaStats loserStats = getStats(loser);
		int oldWinnerRating = winnerStats.rating;
		int oldLoserRating = loserStats.rating;
		int delta = winnerDelta(oldWinnerRating, oldLoserRating);

		winnerStats.rating = oldWinnerRating + delta;
		winnerStats.wins++;
		winnerStats.updatedAt = System.currentTimeMillis();
		loserStats.rating = Math.max(1, oldLoserRating - delta);
		loserStats.losses++;
		if (disconnectLoss) {
			loserStats.disconnectLosses++;
		}
		loserStats.updatedAt = System.currentTimeMillis();
		saveStats(winnerStats);
		saveStats(loserStats);
		recordRankedMatch(match, winner, loser, oldWinnerRating, winnerStats.rating,
			oldLoserRating, loserStats.rating, delta, disconnectLoss);

		returnToLobby(winner);
		finishArenaSession(loser, false);
		sendResultMessage(winner, true, delta, oldWinnerRating, winnerStats);
		sendResultMessage(loser, false, delta, oldLoserRating, loserStats);
		if (disconnectLoss) {
			winner.message(loser.getUsername() + " forfeited by leaving the ranked Death Match.");
		}
		broadcastLobbyRating(winner);
		broadcastLobbyRating(loser);
	}

	private void recordRankedMatch(Match match, Player winner, Player loser,
								   int oldWinnerRating, int newWinnerRating,
								   int oldLoserRating, int newLoserRating,
								   int delta, boolean disconnectLoss) {
		VoidArenaMatchRecord record = new VoidArenaMatchRecord();
		record.seasonId = VoidArenaConfig.CURRENT_SEASON;
		record.winnerId = winner.getDatabaseID();
		record.loserId = loser.getDatabaseID();
		record.winnerRatingBefore = oldWinnerRating;
		record.winnerRatingAfter = newWinnerRating;
		record.loserRatingBefore = oldLoserRating;
		record.loserRatingAfter = newLoserRating;
		record.ratingDelta = delta;
		record.disconnectLoss = disconnectLoss;
		record.slotIndex = match.slotIndex;
		record.startedAt = match.createdAt;
		record.endedAt = System.currentTimeMillis();
		try {
			world.getServer().getDatabase().queryAddVoidArenaMatchRecord(record);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to record Void Arena ranked match {} vs {}", winner.getUsername(), loser.getUsername(), e);
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

	private int winnerDelta(int winnerRating, int loserRating) {
		double expected = 1.0D / (1.0D + Math.pow(10.0D,
			(loserRating - winnerRating) / (double) VoidArenaConfig.ELO_DIVISOR));
		return Math.max(1, (int) Math.round(VoidArenaConfig.ELO_K_FACTOR * (1.0D - expected)));
	}

	private void returnToLobby(Player player) {
		finishArenaSession(player, true);
	}

	private void finishArenaSession(Player player, boolean moveToLobby) {
		if (player == null) {
			return;
		}
		sendArenaControl(player, "close");
		player.resetAll();
		player.setInstanceId(0);
		if (moveToLobby) {
			player.teleportFromVoidArena(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		}
		restoreHits(player);
		ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
	}

	private void restoreHits(Player player) {
		if (player == null) {
			return;
		}
		player.getSkills().setLevel(Skill.HITS.id(), player.getSkills().getMaxStat(Skill.HITS.id()));
	}

	private VoidArenaStats getStats(int playerId, String username) {
		VoidArenaStats cached = statsCache.get(playerId);
		if (cached != null) {
			return cached;
		}
		try {
			VoidArenaStats stats = world.getServer().getDatabase()
				.queryLoadVoidArenaStats(playerId, VoidArenaConfig.CURRENT_SEASON);
			if (stats == null) {
				stats = defaultStats(playerId, username);
			}
			if (stats.username == null || stats.username.isEmpty()) {
				stats.username = username;
			}
			statsCache.put(playerId, stats);
			return stats;
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to load Void Arena stats for player {}", playerId, e);
			VoidArenaStats fallback = defaultStats(playerId, username);
			statsCache.put(playerId, fallback);
			return fallback;
		}
	}

	private VoidArenaStats defaultStats(int playerId, String username) {
		VoidArenaStats stats = new VoidArenaStats();
		stats.seasonId = VoidArenaConfig.CURRENT_SEASON;
		stats.playerId = playerId;
		stats.username = username;
		stats.rating = VoidArenaConfig.STARTING_RATING;
		return stats;
	}

	private void saveStats(VoidArenaStats stats) {
		try {
			world.getServer().getDatabase().querySaveVoidArenaStats(stats);
			statsCache.put(stats.playerId, stats);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to save Void Arena stats for player {}", stats.playerId, e);
		}
	}

	public void sendLeaderboard(Player player) {
		try {
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.CURRENT_SEASON, 50);
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
		if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
			sendSeasonResetPreview(player);
			return;
		}
		if (args.length >= 2 && (args[1].equalsIgnoreCase("confirm") || args[1].equalsIgnoreCase("finalize"))) {
			confirmSeasonReset(player, args);
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
				.queryCountVoidArenaStats(VoidArenaConfig.CURRENT_SEASON);
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchRecords(VoidArenaConfig.CURRENT_SEASON);
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.CURRENT_SEASON, 10);
			VoidArenaMatchRecord[] recent = world.getServer().getDatabase()
				.queryRecentVoidArenaMatchRecords(VoidArenaConfig.CURRENT_SEASON, 5);
			StringBuilder box = new StringBuilder("@yel@Void Arena Season Preview:%");
			box.append("@whi@Season: @cya@").append(VoidArenaConfig.CURRENT_SEASON)
				.append("@whi@, ranked profiles: @yel@").append(rankedProfiles)
				.append("@whi@, ledger rows: @yel@").append(rankedMatches).append("%");
			appendCurrentChampion(box);
			box.append("@yel@Top ratings:%");
			appendTopRatings(box, top, 5);
			box.append("@yel@Recent ranked matches:%");
			appendMatchRows(box, recent);
			box.append("@whi@Run @cya@::arena season reset@whi@ for a destructive reset preview.%");
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
				.queryCountVoidArenaStats(VoidArenaConfig.CURRENT_SEASON);
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchRecords(VoidArenaConfig.CURRENT_SEASON);
			VoidArenaStats[] top = world.getServer().getDatabase()
				.queryTopVoidArenaStats(VoidArenaConfig.CURRENT_SEASON, 10);
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
				.queryCountVoidArenaStats(VoidArenaConfig.CURRENT_SEASON);
			int rankedMatches = world.getServer().getDatabase()
				.queryCountVoidArenaMatchRecords(VoidArenaConfig.CURRENT_SEASON);
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
				VoidArenaConfig.CURRENT_SEASON, VoidArenaConfig.STARTING_RATING, System.currentTimeMillis());
			boolean championAwarded = awardSeasonChampion(champion);
			pendingSeasonResets.remove(player.getUsernameHash());
			statsCache.clear();
			refreshLobbyRatingDisplays();
			LOGGER.warn("{} reset Void Arena ranked stats for season {} ({} profiles, {} ledger rows retained, champion: {})",
				player.getUsername(), VoidArenaConfig.CURRENT_SEASON, affected, rankedMatches,
				championAwarded ? playerName(champion.username, champion.playerId) : "none");
			player.playerServerMessage(MessageType.QUEST,
				"@mag@Void Arena ranked reset complete: @whi@" + affected
					+ " profiles reset. The ranked match ledger was retained.");
			if (championAwarded) {
				player.playerServerMessage(MessageType.QUEST,
					"@mag@Season champion: @whi@" + playerName(champion.username, champion.playerId)
						+ " earned the @yel@" + PlayerTitle.VOID_ARENA_CHAMPION.displayName() + "@whi@ title.");
			}
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to reset Void Arena ranked stats", e);
			player.message("Unable to reset Void Arena ranked stats right now.");
		}
	}

	private VoidArenaStats findSeasonChampionCandidate() throws GameDatabaseException {
		VoidArenaStats[] top = world.getServer().getDatabase()
			.queryTopVoidArenaStats(VoidArenaConfig.CURRENT_SEASON, 100);
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

	private boolean awardSeasonChampion(VoidArenaStats champion) throws GameDatabaseException {
		if (champion == null) {
			return false;
		}
		world.getServer().getDatabase().querySaveGlobalCacheInt(CURRENT_CHAMPION_PLAYER_CACHE, champion.playerId);
		world.getServer().getDatabase().querySaveGlobalCacheInt(CURRENT_CHAMPION_RATING_CACHE, champion.rating);
		world.getServer().getDatabase().querySaveGlobalCacheLong(CURRENT_CHAMPION_AWARDED_AT_CACHE, System.currentTimeMillis());
		world.getServer().getDatabase().querySavePlayerCacheValue(
			champion.playerId, 2, PlayerTitle.VOID_ARENA_CHAMPION.cacheKey(), Boolean.TRUE.toString());

		Player onlineChampion = world.getPlayerID(champion.playerId);
		if (onlineChampion != null) {
			PlayerTitle.unlock(onlineChampion, PlayerTitle.VOID_ARENA_CHAMPION);
		}
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
				VoidArenaMatchRecord[] recent = world.getServer().getDatabase()
					.queryRecentVoidArenaMatchRecords(VoidArenaConfig.CURRENT_SEASON, limit);
				StringBuilder box = new StringBuilder("@yel@Void Arena Ranked Audit:%");
				box.append("@whi@Recent ranked matches:%");
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
			VoidArenaMatchRecord[] recent = world.getServer().getDatabase()
				.queryRecentVoidArenaMatchRecordsForPlayer(VoidArenaConfig.CURRENT_SEASON, playerId, limit);
			StringBuilder box = new StringBuilder("@yel@Void Arena Player Audit:%");
			box.append("@whi@Player: @mag@").append(username)
				.append("@whi@ rating @yel@").append(stats.rating)
				.append("@whi@ (").append(stats.wins).append("-").append(stats.losses)
				.append(", DC ").append(stats.disconnectLosses).append(")%");
			box.append("@whi@Recent ranked matches:%");
			appendMatchRows(box, recent);
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
		SeasonChampion champion = loadCurrentChampion();
		if (champion == null) {
			box.append("@whi@Current champion: @gre@none yet.%");
			return;
		}
		box.append("@whi@Current champion: @mag@").append(champion.username)
			.append("@whi@ at @yel@").append(champion.rating).append("@whi@ rating.%");
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
		Integer playerId = world.getServer().getDatabase().queryLoadGlobalCacheInt(CURRENT_CHAMPION_PLAYER_CACHE);
		if (playerId == null || playerId <= 0) {
			return null;
		}
		Integer rating = world.getServer().getDatabase().queryLoadGlobalCacheInt(CURRENT_CHAMPION_RATING_CACHE);
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

	private String playerName(String username, int playerId) {
		if (username != null && !username.isEmpty()) {
			return username;
		}
		return "player " + playerId;
	}

	private void showHelp(Player player) {
		player.playerServerMessage(MessageType.QUEST, "@mag@Void Arena commands:");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena enter@mag@ - teleport to the ranked lobby");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena stats@mag@ - show your rating");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena top@mag@ - show the top ranked fighters");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena leave@mag@ - leave the lobby");
		if (player.isAdmin()) {
			player.playerServerMessage(MessageType.QUEST, "@whi@::arena season@mag@ - preview current ranked season");
			player.playerServerMessage(MessageType.QUEST, "@whi@::arena season reset@mag@ - preview/reset ranked profiles");
			player.playerServerMessage(MessageType.QUEST, "@whi@::arena audit [recent|player]@mag@ - inspect ranked match ledger");
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
			return playerA != null && playerB != null && isRankedEligible(playerA) && isRankedEligible(playerB);
		}
	}

	private final class DmKingChallenge {
		private final int slotIndex;
		private final long playerHash;
		private final Npc king;
		private final DmKingEvent event;
		private final long startsAt;
		private final long expiresAt;
		private final int playerFishCapacity;
		private int fishRemaining = DM_KING_FISH;
		private int castsRemaining = DM_KING_FIRE_BLAST_CASTS;
		private int prayerStatePoints = DM_KING_PRAYER_STATE_POINTS;
		private long nextEatTick;
		private long nextCastTick;
		private long nextReengageTick;
		private boolean prepared;
		private boolean started;
		private boolean finished;

		private DmKingChallenge(int slotIndex, long playerHash, Npc king, long startsAt, int playerFishCapacity) {
			this.slotIndex = slotIndex;
			this.playerHash = playerHash;
			this.king = king;
			this.startsAt = startsAt;
			this.expiresAt = startsAt + DM_KING_MATCH_TIMEOUT_MS;
			this.playerFishCapacity = playerFishCapacity;
			this.event = new DmKingEvent(world, this);
		}

		private boolean hasStarted() {
			return System.currentTimeMillis() >= startsAt;
		}

		private boolean hasPrayer() {
			return prayerStatePoints > 0;
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
				activeDmKingChallenges.remove(playerHash, this);
				finished = true;
				cleanupDmKingNpc(king);
				event.stop();
				return;
			}
			if (king == null || king.isRemoved()) {
				resolveDmKingLoss(this, player, "DM King vanished from the arena.", false, false);
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
				return;
			}
			if (!prepared) {
				prepare(player);
				return;
			}
			if (!started) {
				started = true;
				king.setChasing(player);
				player.message("@red@DM King attacks with perfect supplies.");
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				return;
			}
			if (!isInsideAssignedCage(king)) {
				Point start = VoidArenaConfig.arenaSlot(slotIndex).startB();
				king.teleport(start.getX(), start.getY());
			}

			long tick = world.getServer().getCurrentTick();
			drainPrayer();
			maybeEat(tick, player);
			maybeCast(tick, player);
			maybeReengage(tick, player);
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
				"Strength potion. Steel skin. Ultimate strength.", player));
			player.message("@red@DM King drinks a strength potion and activates combat prayers.");
		}

		private void drinkStrengthPotion() {
			int maxStrength = king.getSkills().getMaxStat(Skill.STRENGTH.id());
			int boosted = maxStrength + 3 + ((maxStrength * 10) / 100);
			king.getSkills().setLevel(Skill.STRENGTH.id(), boosted);
			king.getUpdateFlags().setActionBubbleNpc(new BubbleNpc(king, ItemId.FULL_STRENGTH_POTION.id()));
		}

		private void drainPrayer() {
			if (prayerStatePoints <= 0) {
				return;
			}
			int totalDrainRate = prayerDrainRate(Prayers.STEEL_SKIN)
				+ prayerDrainRate(Prayers.ULTIMATE_STRENGTH)
				+ prayerDrainRate(Prayers.INCREDIBLE_REFLEXES);
			int pointDrain = (int) Math.ceil(totalDrainRate * 120
				/ (300 * (1 + (DM_KING_PRAYER_POINTS - 1) / 32.0D)));
			prayerStatePoints = Math.max(0, prayerStatePoints - pointDrain);
			if (prayerStatePoints == 0) {
				Player player = world.getPlayer(playerHash);
				if (player != null) {
					player.message("@mag@DM King's prayer fades.");
				}
			}
		}

		private int prayerDrainRate(int prayerId) {
			return world.getServer().getEntityHandler().getPrayerDef(prayerId).getDrainRate();
		}

		private void maybeEat(long tick, Player player) {
			int hits = king.getSkills().getLevel(Skill.HITS.id());
			if (fishRemaining <= 0 || hits <= 0 || hits > DM_KING_EAT_THRESHOLD || tick < nextEatTick) {
				return;
			}
			int healed = Math.min(DM_KING_MAX_HITS, hits + DM_KING_FISH_HEAL);
			if (healed <= hits) {
				return;
			}
			king.getSkills().setLevel(Skill.HITS.id(), healed);
			fishRemaining--;
			nextEatTick = tick + DM_KING_EAT_DELAY_TICKS;
			player.message("@mag@DM King eats a swordfish.");
		}

		private void maybeCast(long tick, Player player) {
			if (castsRemaining <= 0 || tick < nextCastTick) {
				return;
			}
			if (!king.withinRange(player, player.getConfig().SPELL_RANGE_DISTANCE)
				|| !PathValidation.checkPath(world, king.getLocation(), player.getLocation())) {
				return;
			}
			int damage = com.openrsc.server.event.rsc.impl.combat.CombatFormula
				.calculateMagicDamage(DM_KING_FIRE_BLAST_POWER, player);
			world.getServer().getGameEventHandler().add(new ProjectileEvent(world, king, player,
				damage, DM_KING_FIRE_BLAST_PROJECTILE, false));
			castsRemaining--;
			nextCastTick = tick + dmKingCastDelayTicks();
		}

		private long dmKingCastDelayTicks() {
			return VoidArena.this.dmKingCastDelayTicks();
		}

		private void maybeReengage(long tick, Player player) {
			if (tick < nextReengageTick || king.inCombat()) {
				return;
			}
			nextReengageTick = tick + DM_KING_REENGAGE_DELAY_TICKS;
			king.setChasing(player);
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
		private final long castDelayTicks;
		private final long castDelayMs;
		private final int castsUsed;

		private DmKingSummary(int playerFishRemaining, int playerFishCapacity, int kingFishRemaining,
			long castDelayTicks, long castDelayMs, int castsUsed) {
			this.playerFishRemaining = playerFishRemaining;
			this.playerFishCapacity = playerFishCapacity;
			this.kingFishRemaining = kingFishRemaining;
			this.castDelayTicks = castDelayTicks;
			this.castDelayMs = castDelayMs;
			this.castsUsed = castsUsed;
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

	private final class Match {
		private final int slotIndex;
		private final long playerAHash;
		private final long playerBHash;
		private final MatchRules rules;
		private final long createdAt;
		private final long startsAt;

		private Match(int slotIndex, long playerAHash, long playerBHash, MatchRules rules, long startsAt) {
			this.slotIndex = slotIndex;
			this.playerAHash = playerAHash;
			this.playerBHash = playerBHash;
			this.rules = rules;
			this.createdAt = System.currentTimeMillis();
			this.startsAt = startsAt;
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

		private Player opponent(Player player) {
			long opponentHash = player.getUsernameHash() == playerAHash ? playerBHash : playerAHash;
			return world.getPlayer(opponentHash);
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
