package com.openrsc.server.plugins.custom.minigames.undeadsiege;

import com.openrsc.server.content.arena.ArenaKitSnapshot;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.party.Party;
import com.openrsc.server.content.party.PartyPlayer;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.Skills;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerDeathTrigger;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;
import com.openrsc.server.plugins.triggers.PlayerLogoutTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class UndeadSiegeMinigame implements KillNpcTrigger, PlayerDeathTrigger,
	PlayerLoginTrigger, PlayerLogoutTrigger, AttackNpcTrigger, PlayerRangeNpcTrigger, SpellNpcTrigger {

	public static final String KIT_CACHE = "undead_siege_kit_snapshot";
	public static final String STAT_CACHE = "undead_siege_stat_snapshot";
	public static final String SESSION_ATTRIBUTE = "undead_siege_session";
	static final String NPC_ATTRIBUTE = "undead_siege_npc";

	private static final Logger LOGGER = LogManager.getLogger(UndeadSiegeMinigame.class);
	private static volatile UndeadSiegeMinigame instance;

	private final Map<Long, UndeadSiegeInstance> playerSessions = new ConcurrentHashMap<Long, UndeadSiegeInstance>();
	private final Map<Integer, UndeadSiegeInstance> instanceSessions = new ConcurrentHashMap<Integer, UndeadSiegeInstance>();
	private final Map<UUID, UndeadSiegeInstance> npcSessions = new ConcurrentHashMap<UUID, UndeadSiegeInstance>();

	public UndeadSiegeMinigame() {
		instance = this;
	}

	public static void startSolo(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		self.start(player, Collections.singletonList(player));
	}

	public static void startParty(Player leader) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			leader.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		self.start(leader, self.collectPartyParticipants(leader));
	}

	public static void debugClearWave(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		UndeadSiegeInstance session = self.playerSessions.get(player.getUsernameHash());
		if (session == null || !session.isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		session.debugClearWave(player);
	}

	public static void debugFinish(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		UndeadSiegeInstance session = self.playerSessions.get(player.getUsernameHash());
		if (session == null || !session.isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		session.debugFinish(player);
	}

	public static void debugFinishFull(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		UndeadSiegeInstance session = self.playerSessions.get(player.getUsernameHash());
		if (session == null || !session.isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		session.debugFinishFull(player);
	}

	public static void debugKillPlayer(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		UndeadSiegeInstance session = self.playerSessions.get(player.getUsernameHash());
		if (session == null || !session.isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		session.debugKillPlayer(player);
	}

	public static void debugStartNearbyParty(Player leader) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			leader.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		self.startNearbyParty(leader);
	}

	public static void openSupplyShop(Player player) {
		UndeadSiegeMinigame self = instance;
		if (self == null) {
			player.message(UndeadSiegeConfig.NAME + " is not ready yet.");
			return;
		}
		self.openSupplyShopFor(player);
	}

	private synchronized void start(Player leader, List<Player> requestedParticipants) {
		if (leader == null || requestedParticipants == null || requestedParticipants.isEmpty()) {
			return;
		}
		if (!canJoin(leader, leader, true)) {
			return;
		}

		List<Player> participants = new ArrayList<Player>();
		for (Player candidate : requestedParticipants) {
			if (participants.size() >= UndeadSiegeConfig.MAX_PLAYERS) {
				break;
			}
			if (candidate == null) {
				continue;
			}
			if (canJoin(candidate, leader, candidate == leader)) {
				participants.add(candidate);
			}
		}

		if (participants.isEmpty()) {
			leader.message("Nobody is ready for " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		if (!participants.contains(leader)) {
			leader.message("You must be part of the run you start.");
			return;
		}

		int instanceId = leader.getWorld().allocateInstanceId();
		UndeadSiegeInstance session = new UndeadSiegeInstance(this, leader.getWorld(), instanceId, participants);
		instanceSessions.put(instanceId, session);
		for (Player participant : participants) {
			playerSessions.put(participant.getUsernameHash(), session);
			participant.setAttribute(SESSION_ATTRIBUTE, instanceId);
		}
		session.start();
	}

	private void startNearbyParty(Player leader) {
		if (leader.getParty() != null) {
			startParty(leader);
			return;
		}

		Party party = new Party(leader.getWorld());
		party.setPartyName(UndeadSiegeConfig.NAME + " QA");
		party.setPartyTag("SIEGE");
		party.addPlayer(leader);
		leader.getWorld().getPartyManager().createParty(party);

		int added = 1;
		for (Player candidate : leader.getWorld().getPlayers()) {
			if (added >= UndeadSiegeConfig.MAX_PLAYERS) {
				break;
			}
			if (candidate == null || candidate == leader || candidate.getParty() != null) {
				continue;
			}
			if (canJoin(candidate, leader, false)) {
				party.addPlayer(candidate);
				added++;
			}
		}

		leader.message("@gre@Created " + UndeadSiegeConfig.NAME + " QA party with " + added + " player(s).");
		startParty(leader);
	}

	private List<Player> collectPartyParticipants(Player leader) {
		if (leader.getParty() == null) {
			leader.message("You are not in a party. Starting solo.");
			return Collections.singletonList(leader);
		}

		Party party = leader.getParty();
		PartyPlayer partyLeader = party.getLeader();
		if (partyLeader == null || !partyLeader.getUsername().equalsIgnoreCase(leader.getUsername())) {
			leader.message("Only the party leader can start a party " + UndeadSiegeConfig.NAME + " run.");
			return Collections.emptyList();
		}

		List<Player> participants = new ArrayList<Player>();
		participants.add(leader);
		for (PartyPlayer partyMember : party.getPlayers()) {
			if (participants.size() >= UndeadSiegeConfig.MAX_PLAYERS) {
				break;
			}
			Player candidate = partyMember.getPlayerReference();
			if (candidate == null || candidate == leader) {
				continue;
			}
			if (canJoin(candidate, leader, false)) {
				participants.add(candidate);
			}
		}
		if (participants.size() == 1) {
			leader.message("No eligible nearby party members found. Starting solo.");
		} else {
			leader.message("Starting " + UndeadSiegeConfig.NAME + " with " + participants.size() + " players.");
		}
		return participants;
	}

	private boolean canJoin(Player player, Player leader, boolean message) {
		if (player == null || !player.loggedIn() || player.isRemoved() || player.isUnregistering()) {
			return false;
		}
		if (playerSessions.containsKey(player.getUsernameHash())) {
			if (message) player.message("You are already in " + UndeadSiegeConfig.NAME + ".");
			return false;
		}
		if (player.getCache().hasKey(KIT_CACHE) || player.getCache().hasKey(STAT_CACHE)) {
			recoverKit(player, true);
			if (message) player.message("Your previous " + UndeadSiegeConfig.NAME + " kit was restored. Try again when ready.");
			return false;
		}
		if (player.getInstanceId() != 0) {
			if (message) player.message("You must leave your current private area first.");
			return false;
		}
		if (player.inCombat()) {
			if (message) player.message("You must be out of combat to enter " + UndeadSiegeConfig.NAME + ".");
			return false;
		}
		if (player.accessingBank() || player.accessingShop() || player.getDuel().isDuelActive()
			|| (player != leader && player.isBusy())) {
			if (message) player.message("Finish what you are doing before entering " + UndeadSiegeConfig.NAME + ".");
			return false;
		}
		if (leader != null && player != leader && !near(player, leader, UndeadSiegeConfig.PARTY_JOIN_RADIUS)) {
			return false;
		}
		for (int itemId : UndeadSiegeConfig.START_ITEM_IDS) {
			if (player.getClientLimitations().maxItemId < itemId) {
				if (message) player.message("Your client is too old for the " + UndeadSiegeConfig.NAME + " kit.");
				return false;
			}
		}
		return true;
	}

	private boolean near(Player player, Player leader, int radius) {
		return player.getInstanceId() == leader.getInstanceId()
			&& Math.abs(player.getX() - leader.getX()) <= radius
			&& Math.abs(player.getY() - leader.getY()) <= radius;
	}

	void trackNpc(Npc npc, UndeadSiegeInstance session) {
		if (npc != null) {
			npcSessions.put(npc.getUUID(), session);
		}
	}

	void untrackNpc(Npc npc) {
		if (npc != null) {
			npcSessions.remove(npc.getUUID());
		}
	}

	void sessionEnded(UndeadSiegeInstance session) {
		if (session == null) {
			return;
		}
		instanceSessions.remove(session.getInstanceId(), session);
		for (Long playerHash : session.playerHashes()) {
			playerSessions.remove(playerHash, session);
		}
		for (UUID npcUuid : session.npcUuids()) {
			npcSessions.remove(npcUuid, session);
		}
	}

	void untrackPlayer(Player player, UndeadSiegeInstance session) {
		if (player == null || session == null) {
			return;
		}
		playerSessions.remove(player.getUsernameHash(), session);
		player.removeAttribute(SESSION_ATTRIBUTE);
	}

	boolean isActiveParticipant(Player player, UndeadSiegeInstance session) {
		return player != null && session != null && playerSessions.get(player.getUsernameHash()) == session
			&& session.isActiveParticipant(player);
	}

	void savePlayerCache(Player player) {
		try {
			player.getWorld().getServer().getPlayerService().savePlayerCache(player);
		} catch (GameDatabaseException e) {
			LOGGER.error("Unable to save " + UndeadSiegeConfig.NAME + " cache for {}", player.getUsername(), e);
		}
	}

	private static int[] temporarySkills() {
		return new int[] {
			Skill.ATTACK.id(),
			Skill.DEFENSE.id(),
			Skill.STRENGTH.id(),
			Skill.HITS.id(),
			Skill.RANGED.id(),
			Skill.PRAYER.id(),
			Skill.MAGIC.id()
		};
	}

	String captureCombatStats(Player player) {
		StringBuilder snapshot = new StringBuilder("v1");
		Skills skills = player.getSkills();
		for (int skill : temporarySkills()) {
			snapshot.append(';')
				.append(skill).append(',')
				.append(skills.getLevel(skill)).append(',')
				.append(skills.getMaxStat(skill)).append(',')
				.append(skills.getExperience(skill));
		}
		return snapshot.toString();
	}

	void applyTemporaryCombatStats(Player player) {
		Skills skills = player.getSkills();
		for (int skill : temporarySkills()) {
			int level = skill == Skill.MAGIC.id()
				? UndeadSiegeConfig.TEMPORARY_MAGIC_LEVEL
				: UndeadSiegeConfig.TEMPORARY_COMBAT_LEVEL;
			int experience = skills.experienceForLevel(level);
			skills.setExperienceAndLevel(skill, experience, level, false);
		}
		player.getPrayers().resetPrayers();
		player.setPrayerStatePoints(UndeadSiegeConfig.TEMPORARY_COMBAT_LEVEL * 120);
		ActionSender.sendStats(player);
		ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
	}

	void restoreCombatStats(Player player) {
		if (player == null || !player.getCache().hasKey(STAT_CACHE)) {
			return;
		}
		String snapshot = player.getCache().getString(STAT_CACHE);
		if (snapshot != null && snapshot.startsWith("v1")) {
			Skills skills = player.getSkills();
			String[] entries = snapshot.split(";");
			for (int i = 1; i < entries.length; i++) {
				String[] parts = entries[i].split(",");
				if (parts.length != 4) {
					continue;
				}
				try {
					int skill = Integer.parseInt(parts[0]);
					int current = Integer.parseInt(parts[1]);
					int max = Integer.parseInt(parts[2]);
					int experience = Integer.parseInt(parts[3]);
					skills.setExperienceAndLevel(skill, experience, max, false);
					skills.setLevel(skill, current, false);
				} catch (NumberFormatException ignored) {
					// Ignore malformed cache entries so recovery still clears the safe-run state.
				}
			}
		}
		player.getCache().remove(STAT_CACHE);
		ActionSender.sendStats(player);
	}

	private void openSupplyShopFor(Player player) {
		UndeadSiegeInstance session = player == null ? null : playerSessions.get(player.getUsernameHash());
		if (session == null || !session.isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		session.openSupplyShop(player, true);
	}

	void recoverKit(Player player, boolean message) {
		if (player == null || (!player.getCache().hasKey(KIT_CACHE) && !player.getCache().hasKey(STAT_CACHE))) {
			return;
		}
		if (player.getCache().hasKey(KIT_CACHE)) {
			String snapshot = player.getCache().getString(KIT_CACHE);
			ArenaKitSnapshot.restore(player, snapshot);
			player.getCache().remove(KIT_CACHE);
		}
		restoreCombatStats(player);
		player.removeAttribute(Player.SAFE_DEATH_RESPAWN_ATTRIBUTE);
		player.removeAttribute(Player.SAFE_DEATH_CLEANUP_PENDING_ATTRIBUTE);
		player.removeAttribute(SESSION_ATTRIBUTE);
		player.setInstanceId(0);
		player.setLocation(UndeadSiegeConfig.exitTile(), true);
		player.resetPath();
		savePlayerCache(player);
		if (player.loggedIn() && player.getChannel() != null && player.getChannel().isActive()) {
			ActionSender.sendWorldInfo(player);
		}
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
		ActionSender.sendStats(player);
		if (message) {
			player.message("@mag@Your " + UndeadSiegeConfig.NAME + " kit was restored.");
		}
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		return npc != null && npcSessions.containsKey(npc.getUUID());
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		UndeadSiegeInstance session = npc == null ? null : npcSessions.get(npc.getUUID());
		if (session != null) {
			session.handleNpcKilled(player, npc);
		}
	}

	@Override
	public boolean blockPlayerDeath(Player player) {
		return player != null && playerSessions.containsKey(player.getUsernameHash());
	}

	@Override
	public void onPlayerDeath(Player player) {
		UndeadSiegeInstance session = player == null ? null : playerSessions.get(player.getUsernameHash());
		if (session != null) {
			session.handleDeath(player);
		}
	}

	@Override
	public boolean blockPlayerLogout(Player player) {
		return player != null && playerSessions.containsKey(player.getUsernameHash());
	}

	@Override
	public void onPlayerLogout(Player player) {
		UndeadSiegeInstance session = player == null ? null : playerSessions.get(player.getUsernameHash());
		if (session != null) {
			session.handleLogout(player);
		}
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return player != null
			&& (playerSessions.containsKey(player.getUsernameHash())
				|| player.getCache().hasKey(KIT_CACHE)
				|| player.getCache().hasKey(STAT_CACHE));
	}

	@Override
	public void onPlayerLogin(Player player) {
		UndeadSiegeInstance session = player == null ? null : playerSessions.get(player.getUsernameHash());
		if (session != null) {
			session.handleLogin(player);
			return;
		}
		recoverKit(player, true);
		if (player != null) {
			player.teleport(UndeadSiegeConfig.EXIT_X, UndeadSiegeConfig.EXIT_Y, true);
		}
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc npc) {
		return shouldBlockNpcAction(player, npc);
	}

	@Override
	public void onAttackNpc(Player player, Npc npc) {
		sendNpcActionDenied(player);
	}

	@Override
	public boolean blockPlayerRangeNpc(Player player, Npc npc) {
		return shouldBlockNpcAction(player, npc);
	}

	@Override
	public void onPlayerRangeNpc(Player player, Npc npc) {
		sendNpcActionDenied(player);
	}

	@Override
	public boolean blockSpellNpc(Player player, Npc npc) {
		return shouldBlockNpcAction(player, npc);
	}

	@Override
	public void onSpellNpc(Player player, Npc npc) {
		sendNpcActionDenied(player);
	}

	private boolean shouldBlockNpcAction(Player player, Npc npc) {
		if (npc == null || !npc.getAttribute(NPC_ATTRIBUTE, false)) {
			return false;
		}
		UndeadSiegeInstance session = npcSessions.get(npc.getUUID());
		return !isActiveParticipant(player, session);
	}

	private void sendNpcActionDenied(Player player) {
		if (player != null) {
			player.message("That undead belongs to another " + UndeadSiegeConfig.NAME + " run.");
		}
	}
}
