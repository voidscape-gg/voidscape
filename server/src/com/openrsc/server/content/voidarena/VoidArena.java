package com.openrsc.server.content.voidarena;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.VoidArenaStats;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidArena {
	private static final Logger LOGGER = LogManager.getLogger(VoidArena.class);
	private static final int RULE_RANKED = 1;
	private static final int RULE_F2P_ONLY = 1 << 1;
	private static final int RULE_ALLOW_PRAYER = 1 << 2;
	private static final int RULE_ALLOW_RANGED = 1 << 3;
	private static final int RULE_ALLOW_MAGIC = 1 << 4;

	private final World world;
	private final Map<Long, Match> activeMatches = new ConcurrentHashMap<>();
	private final Map<Long, Challenge> incomingChallenges = new ConcurrentHashMap<>();
	private final Map<Integer, VoidArenaStats> statsCache = new ConcurrentHashMap<>();

	public VoidArena(World world) {
		this.world = world;
	}

	public AttackCheck checkAttack(Player attacker, Player victim, boolean missile) {
		Match match = activeMatches.get(attacker.getUsernameHash());
		if (match != null) {
			if (match.contains(victim) && match.isInsideAssignedCage(attacker) && match.isInsideAssignedCage(victim)) {
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

	public Point handlePlayerDeath(Player loser, Player winner) {
		Match match = activeMatches.get(loser.getUsernameHash());
		if (match == null || winner == null || !match.contains(winner)) {
			return null;
		}

		resolveMatch(match, winner, loser, false);
		loser.setInstanceId(0);
		return VoidArenaConfig.lobbyTile();
	}

	public void handleLogout(Player player) {
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

	public void handleInterfaceOption(Player player, int action, int targetServerIndex, int ruleMask) {
		Player target = world.getPlayer(targetServerIndex);
		if (target == null || target.equals(player)) {
			player.message("That player is not available for a Death Match.");
			return;
		}
		if (action == 1) {
			cancelChallenge(player, target);
			return;
		}
		challenge(player, target, MatchRules.fromMask(ruleMask));
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
			challenge(player, target, MatchRules.ranked());
			return;
		}

		showHelp(player);
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

	private void sendLobbyRatings(Player viewer) {
		if (!supportsRatingDisplay(viewer)) {
			return;
		}
		for (Player subject : world.getPlayers()) {
			if (VoidArenaConfig.isInsideLobby(subject.getLocation())) {
				sendRatingPayload(viewer, subject);
			}
		}
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
		if (activeMatches.containsKey(player.getUsernameHash())) {
			player.message("You are already in a Void Arena fight.");
			return;
		}
		player.setInstanceId(0);
		player.teleport(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		restoreHits(player);
		sendStats(player, player);
		sendLobbyRatings(player);
		broadcastLobbyRating(player);
		player.message("Right-click another player here and choose Death Match.");
	}

	private void leave(Player player) {
		if (activeMatches.containsKey(player.getUsernameHash())) {
			player.message("You cannot leave during an active Death Match.");
			return;
		}
		sendRatingClear(player);
		restoreHits(player);
		player.setInstanceId(0);
		player.teleport(VoidArenaConfig.EXIT_X, VoidArenaConfig.EXIT_Y, true);
	}

	private void challenge(Player challenger, Player target, MatchRules rules) {
		Challenge reciprocal = incomingChallenges.get(challenger.getUsernameHash());
		if (reciprocal != null && reciprocal.challengerHash == target.getUsernameHash() && !reciprocal.expired()) {
			if (!canChallenge(challenger, target, reciprocal.rules)) {
				return;
			}
			incomingChallenges.remove(challenger.getUsernameHash());
			startMatch(target, challenger, reciprocal.rules);
			return;
		}

		if (!canChallenge(challenger, target, rules)) {
			return;
		}

		incomingChallenges.put(target.getUsernameHash(),
			new Challenge(challenger.getUsernameHash(), target.getUsernameHash(), rules));
		challenger.message("You challenge " + target.getUsername() + " to a " + rules.modeName() + " Death Match.");
		target.playerServerMessage(MessageType.QUEST,
			"@mag@" + challenger.getUsername() + "@whi@ has challenged you to a "
				+ rules.modeName() + " Death Match.");
		target.message("Rules: " + rules.summary() + ".");
		target.message("Right-click them and choose Death Match to accept.");
	}

	private void cancelChallenge(Player player, Player target) {
		Challenge challenge = incomingChallenges.get(target.getUsernameHash());
		if (challenge != null && challenge.challengerHash == player.getUsernameHash()) {
			incomingChallenges.remove(target.getUsernameHash());
			player.message("Your Void Arena challenge has been cancelled.");
		}
	}

	private boolean canChallenge(Player challenger, Player target, MatchRules rules) {
		if (challenger.equals(target)) {
			challenger.message("You cannot challenge yourself.");
			return false;
		}
		if (!isInsideLobby(challenger) || !isInsideLobby(target)) {
			challenger.message("Both players must be in the Void Arena lobby.");
			return false;
		}
		if (challenger.inCombat() || target.inCombat()) {
			challenger.message("Both players must be out of combat.");
			return false;
		}
		if (activeMatches.containsKey(challenger.getUsernameHash())
			|| activeMatches.containsKey(target.getUsernameHash())) {
			challenger.message("One of you is already in a Death Match.");
			return false;
		}
		if (challenger.getParty() != null && target.getParty() != null && challenger.getParty() == target.getParty()) {
			challenger.message("Leave your party before starting a Death Match.");
			return false;
		}
		if (!hasAvailableSlot()) {
			challenger.message("All Void Arena cages are occupied right now.");
			return false;
		}
		if (rules.ranked && (!hasRankedStats(challenger, true) || !hasRankedStats(target, true))) {
			return false;
		}
		return !rules.f2pOnly || (hasF2PLoadout(challenger, rules.ranked) && hasF2PLoadout(target, rules.ranked));
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

	private boolean hasF2PLoadout(Player player, boolean ranked) {
		String prefix = ranked ? "Ranked Death Match" : "F2P Death Match";
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item item : player.getCarriedItems().getInventory().getItems()) {
				if (isMembersItem(player, item)) {
					player.message(prefix + " allows F2P items only. Bank " + item.getDef(player.getWorld()).getName() + ".");
					return false;
				}
			}
		}
		synchronized (player.getCarriedItems().getEquipment().getList()) {
			for (Item item : player.getCarriedItems().getEquipment().getList()) {
				if (isMembersItem(player, item)) {
					player.message(prefix + " allows F2P gear only. Remove " + item.getDef(player.getWorld()).getName() + ".");
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
		Match match = new Match(slotIndex, playerA.getUsernameHash(), playerB.getUsernameHash(), rules);
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
		playerA.teleport(slot.startAX, slot.startAY, true);
		playerB.teleport(slot.startBX, slot.startBY, true);
		playerA.message("@red@" + rules.title() + " started. Defeat " + playerB.getUsername() + ".");
		playerB.message("@red@" + rules.title() + " started. Defeat " + playerA.getUsername() + ".");
		playerA.message("Rules: " + rules.summary() + ".");
		playerB.message("Rules: " + rules.summary() + ".");
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

		returnToLobby(winner);
		sendResultMessage(winner, true, delta, oldWinnerRating, winnerStats);
		sendResultMessage(loser, false, delta, oldLoserRating, loserStats);
		if (disconnectLoss) {
			winner.message(loser.getUsername() + " forfeited by leaving the ranked Death Match.");
		}
		broadcastLobbyRating(winner);
		broadcastLobbyRating(loser);
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
		player.setInstanceId(0);
		player.teleport(VoidArenaConfig.LOBBY_X, VoidArenaConfig.LOBBY_Y, true);
		restoreHits(player);
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
			StringBuilder box = new StringBuilder("@yel@Void Arena Top 5:%");
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

	private void showHelp(Player player) {
		player.playerServerMessage(MessageType.QUEST, "@mag@Void Arena commands:");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena enter@mag@ - teleport to the ranked lobby");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena stats@mag@ - show your rating");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena top@mag@ - show the top ranked fighters");
		player.playerServerMessage(MessageType.QUEST, "@whi@::arena leave@mag@ - leave the lobby");
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
		StringBuilder builder = new StringBuilder();
		for (int i = startIndex; i < args.length; i++) {
			if (builder.length() > 0) {
				builder.append(' ');
			}
			builder.append(args[i]);
		}
		return builder.toString();
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
		private final MatchRules rules;
		private final long createdAt = System.currentTimeMillis();

		private Challenge(long challengerHash, long targetHash, MatchRules rules) {
			this.challengerHash = challengerHash;
			this.targetHash = targetHash;
			this.rules = rules;
		}

		private boolean expired() {
			return System.currentTimeMillis() - createdAt > VoidArenaConfig.CHALLENGE_TIMEOUT_MS;
		}
	}

	private final class Match {
		private final int slotIndex;
		private final long playerAHash;
		private final long playerBHash;
		private final MatchRules rules;

		private Match(int slotIndex, long playerAHash, long playerBHash, MatchRules rules) {
			this.slotIndex = slotIndex;
			this.playerAHash = playerAHash;
			this.playerBHash = playerBHash;
			this.rules = rules;
		}

		private boolean contains(Player player) {
			return player != null
				&& (player.getUsernameHash() == playerAHash || player.getUsernameHash() == playerBHash);
		}

		private boolean isInsideAssignedCage(Player player) {
			return VoidArenaConfig.arenaSlot(slotIndex).contains(player.getLocation());
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
