package com.openrsc.server.plugins.custom.minigames.undeadsiege;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.arena.ArenaKitSnapshot;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UndeadSiegeInstance {
	private enum Phase {
		COUNTDOWN,
		RUNNING,
		INTERMISSION,
		ENDING
	}

	private static final String BOSS_ATTRIBUTE = "undead_siege_boss";
	private static final String CLIENT_POINTS_PREFIX = "@vsundeadsiege@points|";

	private final UndeadSiegeMinigame manager;
	private final World world;
	private final int instanceId;
	private final Shop supplyShop;
	private final LinkedHashMap<Long, Player> allPlayers = new LinkedHashMap<Long, Player>();
	private final LinkedHashMap<Long, Player> activePlayers = new LinkedHashMap<Long, Player>();
	private final Map<Long, Integer> points = new LinkedHashMap<Long, Integer>();
	private final Set<Long> rewardedPlayers = new HashSet<Long>();
	private final Set<Long> finishedPlayers = new HashSet<Long>();
	private final Set<Long> supplyMenus = new HashSet<Long>();
	private final Set<Npc> activeNpcs = new HashSet<Npc>();
	private final Set<UUID> npcUuids = new HashSet<UUID>();
	private final Map<UUID, Point> npcLastPositions = new HashMap<UUID, Point>();
	private final Map<UUID, Integer> npcStuckTicks = new HashMap<UUID, Integer>();

	private GameTickEvent event;
	private Phase phase = Phase.COUNTDOWN;
	private int countdownTicks = UndeadSiegeConfig.COUNTDOWN_TICKS;
	private int intermissionTicks;
	private int wave;
	private int completedWave;
	private int totalToSpawn;
	private int spawnedThisWave;
	private boolean finished;

	UndeadSiegeInstance(UndeadSiegeMinigame manager, World world, int instanceId, List<Player> players) {
		this.manager = manager;
		this.world = world;
		this.instanceId = instanceId;
		this.supplyShop = createSupplyShop();
		for (Player player : players) {
			allPlayers.put(player.getUsernameHash(), player);
			activePlayers.put(player.getUsernameHash(), player);
			points.put(player.getUsernameHash(), 0);
		}
	}

	void start() {
		int index = 0;
		for (Player player : allPlayers.values()) {
			preparePlayer(player, index++);
		}
		broadcastActive("@mag@" + UndeadSiegeConfig.NAME + " begins soon.");
		event = new UndeadSiegeEvent(world, this);
		world.getServer().getGameEventHandler().add(event);
	}

	int getInstanceId() {
		return instanceId;
	}

	Set<Long> playerHashes() {
		return new HashSet<Long>(allPlayers.keySet());
	}

	Set<UUID> npcUuids() {
		return new HashSet<UUID>(npcUuids);
	}

	boolean isActiveParticipant(Player player) {
		return player != null && activePlayers.containsKey(player.getUsernameHash())
			&& player.getInstanceId() == instanceId;
	}

	void debugClearWave(Player killer) {
		int cleared = 0;
		for (Npc npc : new ArrayList<Npc>(activeNpcs)) {
			if (npc == null || npc.isRemoved()) {
				continue;
			}
			npc.getSkills().setLevel(3, 0, false);
			npc.killedBy(killer);
			cleared++;
		}
		killer.message("@gre@Cleared " + cleared + " active " + UndeadSiegeConfig.NAME + " npc(s).");
	}

	void debugFinish(Player player) {
		finishPlayer(player, true, "You leave " + UndeadSiegeConfig.NAME + ".", true);
		if (activePlayers.isEmpty()) {
			end("");
		}
	}

	void debugFinishFull(Player player) {
		finishPlayer(player, true, "You leave " + UndeadSiegeConfig.NAME + ".", true, true);
		if (activePlayers.isEmpty()) {
			end("");
		}
	}

	void debugKillPlayer(Player player) {
		player.message("@or1@Forcing " + UndeadSiegeConfig.NAME + " safe-death cleanup.");
		player.getSkills().setLevel(Skill.HITS.id(), 0, true);
		player.killedBy(player);
	}

	void handleNpcKilled(Player killer, Npc npc) {
		if (npc == null || !activeNpcs.remove(npc)) {
			return;
		}
		manager.untrackNpc(npc);
		npcUuids.remove(npc.getUUID());
		npcLastPositions.remove(npc.getUUID());
		npcStuckTicks.remove(npc.getUUID());
		if (killer != null && activePlayers.containsKey(killer.getUsernameHash())) {
			int award = pointsFor(npc);
			addPoints(killer, award);
			killer.message("@mag@" + UndeadSiegeConfig.NAME + ": @whi@+" + award + " points. Total: "
				+ points.get(killer.getUsernameHash()) + ".");
		}
	}

	void handleDeath(Player player) {
		finishPlayer(player, true, "You were overwhelmed by the undead.", true);
		if (activePlayers.isEmpty()) {
			end("The undead overran the last survivor.");
		}
	}

	void handleLogout(Player player) {
		finishPlayer(player, false, "", true);
		if (activePlayers.isEmpty()) {
			end("");
		}
	}

	void handleLogin(Player player) {
		finishPlayer(player, false, "Your interrupted " + UndeadSiegeConfig.NAME + " run was cleaned up.", true);
	}

	private void tick() {
		if (phase == Phase.ENDING) {
			stopEvent();
			return;
		}

		removeInvalidPlayers();
		if (activePlayers.isEmpty()) {
			end("");
			return;
		}

		if (phase == Phase.COUNTDOWN) {
			tickCountdown();
		} else if (phase == Phase.RUNNING) {
			tickRunning();
		} else if (phase == Phase.INTERMISSION) {
			tickIntermission();
		}
	}

	private void tickCountdown() {
		if (countdownTicks > 0) {
			broadcastActive("Wave 1 begins in " + countdownTicks + "...");
			countdownTicks--;
			return;
		}
		startNextWave();
	}

	private void tickRunning() {
		cleanupRemovedNpcs();
		spawnWaveBatch();
		reassignIdleNpcs();
		recoverStuckNpcs();

		if (spawnedThisWave >= totalToSpawn && activeNpcs.isEmpty()) {
			completedWave = wave;
			broadcastActive("@gre@Wave " + wave + " cleared.");
			startIntermission();
		}
	}

	private void tickIntermission() {
		openSupplyMenus();
		intermissionTicks--;
		if (intermissionTicks <= 0) {
			clearSupplyMenus();
			startNextWave();
		}
	}

	private void startNextWave() {
		clearSupplyMenus();
		phase = Phase.RUNNING;
		wave++;
		totalToSpawn = activePlayers.size() * (3 + wave);
		spawnedThisWave = 0;
		broadcastActive("@red@Wave " + wave + " begins. " + totalToSpawn + " undead approach.");
	}

	private void startIntermission() {
		phase = Phase.INTERMISSION;
		intermissionTicks = UndeadSiegeConfig.INTERMISSION_TICKS;
		supplyMenus.clear();
		broadcastActive("@mag@::shop available for " + UndeadSiegeConfig.INTERMISSION_SECONDS
			+ " seconds. Buy supplies or save your points.");
	}

	private void spawnWaveBatch() {
		int spawnsThisTick = Math.max(1, activePlayers.size());
		while (spawnsThisTick-- > 0
			&& activeNpcs.size() < UndeadSiegeConfig.MAX_LIVE_NPCS
			&& spawnedThisWave < totalToSpawn) {
			Npc npc = spawnNpc(selectNpcId());
			if (npc != null) {
				spawnedThisWave++;
			} else {
				break;
			}
		}
	}

	private Npc spawnNpc(int npcId) {
		Point tile = randomSpawnTile();
		if (tile == null) {
			return null;
		}

		NPCLoc loc = new NPCLoc(npcId, tile.getX(), tile.getY(),
			UndeadSiegeConfig.CHASE_MIN_X,
			UndeadSiegeConfig.CHASE_MAX_X,
			UndeadSiegeConfig.CHASE_MIN_Y,
			UndeadSiegeConfig.CHASE_MAX_Y);
		Npc npc = new Npc(world, loc);
		npc.setShouldRespawn(false);
		npc.setInstanceId(instanceId);
		npc.setAttribute(UndeadSiegeMinigame.NPC_ATTRIBUTE, true);
		npc.setAttribute(Npc.SUPPRESS_DEFAULT_DEATH_ATTRIBUTE, true);
		npc.setAttribute(Npc.SUPPRESS_RANGED_AMMO_DROP_ATTRIBUTE, true);
		npc.setAttribute(Npc.FORCE_CHASE_ATTRIBUTE, true);
		npc.setAttribute(Npc.CONTACT_ATTACK_ATTRIBUTE, true);
		npc.setAttribute(Npc.CONTACT_ATTACK_MIN_DAMAGE_ATTRIBUTE, contactDamageMinForWave());
		npc.setAttribute(Npc.CONTACT_ATTACK_MAX_DAMAGE_ATTRIBUTE, contactDamageCapForWave());
		npc.setAttribute(Npc.PLAYER_ATTACK_DAMAGE_FLOOR_ATTRIBUTE, playerDamageFloorForWave(false));
		if (wave % 5 == 0 && spawnedThisWave == 0) {
			npc.setAttribute(BOSS_ATTRIBUTE, true);
			npc.setAttribute(Npc.CONTACT_ATTACK_MIN_DAMAGE_ATTRIBUTE, Math.max(1, contactDamageMinForWave()));
			npc.setAttribute(Npc.CONTACT_ATTACK_MAX_DAMAGE_ATTRIBUTE, Math.max(4, contactDamageCapForWave() + 2));
			npc.setAttribute(Npc.PLAYER_ATTACK_DAMAGE_FLOOR_ATTRIBUTE, playerDamageFloorForWave(true));
		}
		tuneNpcForWave(npc);
		world.registerNpc(npc);
		activeNpcs.add(npc);
		npcUuids.add(npc.getUUID());
		npcLastPositions.put(npc.getUUID(), Point.location(npc.getX(), npc.getY()));
		npcStuckTicks.put(npc.getUUID(), 0);
		manager.trackNpc(npc, this);

		Player target = randomActivePlayer();
		if (target != null) {
			npc.setChasing(target);
		}
		return npc;
	}

	private Shop createSupplyShop() {
		return new Shop(false, Integer.MAX_VALUE, 0, 0, UndeadSiegeConfig.POINT_SHOP_PRICE_MODIFIER,
			new Item(ItemId.CROSSBOW_BOLTS.id(), 1),
			new Item(ItemId.CHAOS_RUNE.id(), 1),
			new Item(ItemId.STAFF_OF_AIR.id(), 1),
			new Item(ItemId.TUNA.id(), 1),
			new Item(ItemId.CAKE.id(), 1),
			new Item(ItemId.SWORDFISH.id(), 1))
			.withoutPlayerSales()
			.withDisplayBuyPrice(ItemId.CROSSBOW_BOLTS.id(), UndeadSiegeConfig.SUPPLY_BOLTS_COST)
			.withDisplayBuyPrice(ItemId.CHAOS_RUNE.id(), UndeadSiegeConfig.SUPPLY_CRUMBLE_RUNES_COST)
			.withDisplayBuyPrice(ItemId.STAFF_OF_AIR.id(), UndeadSiegeConfig.SUPPLY_STAFF_COST)
			.withDisplayBuyPrice(ItemId.TUNA.id(), UndeadSiegeConfig.SUPPLY_TUNA_COST)
			.withDisplayBuyPrice(ItemId.CAKE.id(), UndeadSiegeConfig.SUPPLY_CAKE_COST)
			.withDisplayBuyPrice(ItemId.SWORDFISH.id(), UndeadSiegeConfig.SUPPLY_SWORDFISH_COST)
			.withBuyHandler(new Shop.BuyHandler() {
				@Override
				public void buy(Player player, Shop shop, int catalogID, int amount) {
					buySupplyFromShop(player, catalogID);
				}
			});
	}

	private void tuneNpcForWave(Npc npc) {
		if (npc.getAttribute(BOSS_ATTRIBUTE, false)) {
			return;
		}

		int hits;
		int attack;
		int strength;
		int defense;
		if (wave <= 1) {
			hits = 6;
			attack = 3;
			strength = 3;
			defense = 1;
		} else if (wave == 2) {
			hits = 7;
			attack = 4;
			strength = 4;
			defense = 2;
		} else if (wave == 3) {
			hits = 8;
			attack = 5;
			strength = 5;
			defense = 3;
		} else if (wave <= 6) {
			hits = 10 + wave;
			attack = 5 + wave;
			strength = 5 + wave;
			defense = 3 + wave / 2;
		} else if (wave <= 9) {
			hits = 14 + wave * 2;
			attack = 8 + wave;
			strength = 8 + wave;
			defense = 6 + wave;
		} else {
			hits = 24 + wave * 2;
			attack = 16 + wave;
			strength = 16 + wave;
			defense = 12 + wave;
		}

		if (isSpecialUndead(npc)) {
			hits += Math.max(3, wave / 2);
			attack += 2;
			strength += 2;
			defense += 2;
		}

		npc.getSkills().setLevelTo(Skill.ATTACK.id(), attack);
		npc.getSkills().setLevelTo(Skill.STRENGTH.id(), strength);
		npc.getSkills().setLevelTo(Skill.DEFENSE.id(), defense);
		npc.getSkills().setLevelTo(Skill.RANGED.id(), Math.max(1, attack / 2));
		npc.getSkills().setLevelTo(Skill.HITS.id(), hits);
	}

	private boolean isSpecialUndead(Npc npc) {
		return npc.getID() == NpcId.SKELETON_MAGE.id() || npc.getID() == NpcId.UNDEADONE.id();
	}

	private int selectNpcId() {
		if (wave % 5 == 0 && spawnedThisWave == 0) {
			int[] bosses = {
				NpcId.NAZASTAROOL_ZOMBIE.id(),
				NpcId.NAZASTAROOL_SKELETON.id(),
				NpcId.NAZASTAROOL_GHOST.id()
			};
			return supportedNpcId(bosses[DataConversions.random(0, bosses.length - 1)], NpcId.SKELETON_LVL54.id());
		}
		if (wave <= 3) {
			int[] ids = {NpcId.ZOMBIE_LVL19.id(), NpcId.SKELETON_RESTLESS.id()};
			return ids[DataConversions.random(0, ids.length - 1)];
		}
		if (wave <= 6) {
			int[] ids = {NpcId.ZOMBIE_LVL24_GEN.id(), NpcId.SKELETON_LVL21.id(), NpcId.GHOST.id()};
			return ids[DataConversions.random(0, ids.length - 1)];
		}
		if (wave <= 9) {
			int[] ids = {NpcId.ZOMBIE_LVL32.id(), NpcId.SKELETON_LVL31.id(), NpcId.SKELETON_MAGE.id()};
			return supportedNpcId(ids[DataConversions.random(0, ids.length - 1)], NpcId.SKELETON_LVL31.id());
		}
		int[] ids = {NpcId.SKELETON_LVL54.id(), NpcId.UNDEADONE.id(), NpcId.SKELETON_MAGE.id()};
		return supportedNpcId(ids[DataConversions.random(0, ids.length - 1)], NpcId.SKELETON_LVL54.id());
	}

	private int supportedNpcId(int preferred, int fallback) {
		for (Player player : activePlayers.values()) {
			if (player != null && player.getClientLimitations().maxNpcId < preferred) {
				return fallback;
			}
		}
		return preferred;
	}

	private int pointsFor(Npc npc) {
		if (npc.getAttribute(BOSS_ATTRIBUTE, false)) {
			return 100;
		}
		if (npc.getID() == NpcId.SKELETON_MAGE.id() || npc.getID() == NpcId.UNDEADONE.id()) {
			return 25 + wave * 3;
		}
		return 10 + wave * 2;
	}

	private int contactDamageCapForWave() {
		if (wave <= 3) {
			return 2;
		}
		if (wave <= 6) {
			return 3;
		}
		if (wave <= 9) {
			return 4;
		}
		return Math.min(8, 4 + wave / 3);
	}

	private int contactDamageMinForWave() {
		return wave <= 6 ? 1 : 2;
	}

	private int playerDamageFloorForWave(boolean boss) {
		if (boss) {
			return 1;
		}
		return wave <= 6 ? 2 : 1;
	}

	private Point randomSpawnTile() {
		for (int attempt = 0; attempt < 40; attempt++) {
			Point tile = UndeadSiegeConfig.SPAWN_TILES[
				DataConversions.random(0, UndeadSiegeConfig.SPAWN_TILES.length - 1)];
			if (isWalkable(tile)) {
				return tile;
			}
		}
		return UndeadSiegeConfig.SPAWN_TILES[0];
	}

	private boolean isWalkable(Point tile) {
		return tile != null && world.getTile(tile.getX(), tile.getY()) != null
			&& (world.getTile(tile.getX(), tile.getY()).traversalMask & CollisionFlag.FULL_BLOCK) == 0;
	}

	private Player randomActivePlayer() {
		List<Player> players = new ArrayList<Player>(activePlayers.values());
		if (players.isEmpty()) {
			return null;
		}
		return players.get(DataConversions.random(0, players.size() - 1));
	}

	private void reassignIdleNpcs() {
		for (Npc npc : activeNpcs) {
			if (npc == null || npc.isRemoved() || npc.inCombat()) {
				continue;
			}
			Player target = chaseTargetFor(npc);
			if (target != null) {
				npc.setChasing(target);
			}
		}
	}

	private Player chaseTargetFor(Npc npc) {
		Mob target = npc.getNpcBehavior().getChaseTarget();
		if (target instanceof Player) {
			Player player = (Player) target;
			if (isActiveParticipant(player)) {
				return player;
			}
		}
		return randomActivePlayer();
	}

	private void recoverStuckNpcs() {
		for (Npc npc : new ArrayList<Npc>(activeNpcs)) {
			if (npc == null || npc.isRemoved() || npc.inCombat()) {
				continue;
			}

			Player target = chaseTargetFor(npc);
			if (target == null || !target.loggedIn()) {
				continue;
			}

			Point current = Point.location(npc.getX(), npc.getY());
			if (current.withinRange(target.getLocation(), 1)) {
				npcLastPositions.put(npc.getUUID(), current);
				npcStuckTicks.put(npc.getUUID(), 0);
				continue;
			}

			Point last = npcLastPositions.get(npc.getUUID());
			if (!current.equals(last)) {
				npcLastPositions.put(npc.getUUID(), current);
				npcStuckTicks.put(npc.getUUID(), 0);
				continue;
			}

			int stuckTicks = npcStuckTicks.containsKey(npc.getUUID()) ? npcStuckTicks.get(npc.getUUID()) + 1 : 1;
			npcStuckTicks.put(npc.getUUID(), stuckTicks);
			if (stuckTicks < UndeadSiegeConfig.NPC_STUCK_TICKS_BEFORE_REPOSITION) {
				continue;
			}

			Point rescueTile = findRescueTile(npc, target);
			if (rescueTile != null && !rescueTile.equals(current)) {
				npc.teleport(rescueTile.getX(), rescueTile.getY());
			}
			npc.resetPath();
			npc.setChasing(target);
			npc.walkToEntityAStar(target.getX(), target.getY(), 48);
			npcLastPositions.put(npc.getUUID(), Point.location(npc.getX(), npc.getY()));
			npcStuckTicks.put(npc.getUUID(), 0);
		}
	}

	private Point findRescueTile(Npc npc, Player target) {
		Point targetPoint = target.getLocation();
		for (int radius = UndeadSiegeConfig.NPC_REPOSITION_MIN_RADIUS;
			 radius <= UndeadSiegeConfig.NPC_REPOSITION_MAX_RADIUS;
			 radius++) {
			Point best = null;
			int bestDistance = Integer.MAX_VALUE;
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -radius; dy <= radius; dy++) {
					if (Math.abs(dx) != radius && Math.abs(dy) != radius) {
						continue;
					}
					Point candidate = Point.location(target.getX() + dx, target.getY() + dy);
					if (!isValidRescueTile(candidate, npc, targetPoint, true)) {
						continue;
					}
					int distance = candidate.getDistancePythagoras(targetPoint);
					if (distance < bestDistance) {
						best = candidate;
						bestDistance = distance;
					}
				}
			}
			if (best != null) {
				return best;
			}
		}

		Point best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Point candidate : UndeadSiegeConfig.APPROACH_TILES) {
			if (!isValidRescueTile(candidate, npc, targetPoint, false)) {
				continue;
			}
			int distance = candidate.getDistancePythagoras(targetPoint);
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private boolean isValidRescueTile(Point candidate, Npc npc, Point targetPoint, boolean requireClearLine) {
		if (!UndeadSiegeConfig.isInsideArena(candidate) || !isWalkable(candidate)) {
			return false;
		}
		if (isOccupied(candidate, npc)) {
			return false;
		}
		return !requireClearLine || PathValidation.checkPath(world, candidate, targetPoint);
	}

	private boolean isOccupied(Point candidate, Npc exemptNpc) {
		for (Player player : activePlayers.values()) {
			if (player != null && player.loggedIn() && player.getLocation().equals(candidate)) {
				return true;
			}
		}
		for (Npc npc : activeNpcs) {
			if (npc != null && npc != exemptNpc && !npc.isRemoved()
				&& npc.getX() == candidate.getX() && npc.getY() == candidate.getY()) {
				return true;
			}
		}
		return false;
	}

	private void openSupplyMenus() {
		for (Player player : activePlayers.values()) {
			if (player == null || !player.loggedIn() || supplyMenus.contains(player.getUsernameHash())) {
				continue;
			}
			openSupplyShop(player, false);
		}
	}

	void openSupplyShop(Player player, boolean manual) {
		if (!isActiveParticipant(player)) {
			player.message("You are not in " + UndeadSiegeConfig.NAME + ".");
			return;
		}
		if (phase != Phase.INTERMISSION) {
			player.message("Supplies are only available between waves.");
			return;
		}
		player.resetMenuHandler();
		player.resetShop();
		player.setAccessingShop(supplyShop);
		int currentPoints = points.containsKey(player.getUsernameHash()) ? points.get(player.getUsernameHash()) : 0;
		syncPointBalance(player, currentPoints);
		ActionSender.showShop(player, supplyShop);
		if (manual) {
			player.message("@mag@" + UndeadSiegeConfig.NAME + ": @whi@shop reopened. Points: " + currentPoints + ".");
		} else {
			player.message("@mag@" + UndeadSiegeConfig.NAME + ": @whi@buy supplies or close the window to save points. Type ::shop to reopen.");
		}
		supplyMenus.add(player.getUsernameHash());
	}

	private void buySupplyFromShop(Player player, int catalogID) {
		if (!isActiveParticipant(player) || phase != Phase.INTERMISSION) {
			player.message("Supplies are only available between waves.");
			return;
		}
		if (catalogID == ItemId.CROSSBOW_BOLTS.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_BOLTS_COST,
				new Item(ItemId.CROSSBOW_BOLTS.id(), 100));
		} else if (catalogID == ItemId.CHAOS_RUNE.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_CRUMBLE_RUNES_COST,
				new Item(ItemId.AIR_RUNE.id(), 20),
				new Item(ItemId.EARTH_RUNE.id(), 20),
				new Item(ItemId.CHAOS_RUNE.id(), 10));
		} else if (catalogID == ItemId.STAFF_OF_AIR.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_STAFF_COST,
				new Item(ItemId.STAFF_OF_AIR.id(), 1));
		} else if (catalogID == ItemId.TUNA.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_TUNA_COST,
				new Item(ItemId.TUNA.id(), 5));
		} else if (catalogID == ItemId.CAKE.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_CAKE_COST,
				new Item(ItemId.CAKE.id(), 3));
		} else if (catalogID == ItemId.SWORDFISH.id()) {
			buy(player, UndeadSiegeConfig.SUPPLY_SWORDFISH_COST,
				new Item(ItemId.SWORDFISH.id(), 5));
		} else {
			player.message("That supply is not available.");
		}
	}

	private void buy(Player player, int cost, Item... items) {
		long hash = player.getUsernameHash();
		int current = points.containsKey(hash) ? points.get(hash) : 0;
		if (current < cost) {
			syncPointBalance(player, current);
			player.message("You need " + cost + " points for that.");
			return;
		}
		int remaining = current - cost;
		points.put(hash, remaining);
		syncPointBalance(player, remaining);
		for (Item item : items) {
			giveOrDrop(player, item);
		}
		player.message("Supplies purchased. Points left: " + remaining + ".");
	}

	private void syncPointBalance(Player player, int currentPoints) {
		if (player != null && player.loggedIn()) {
			player.playerServerMessage(MessageType.QUEST, CLIENT_POINTS_PREFIX + Math.max(0, currentPoints));
		}
	}

	private void clearSupplyMenus() {
		for (Player player : activePlayers.values()) {
			if (player != null && player.loggedIn() && supplyMenus.contains(player.getUsernameHash())) {
				player.resetMenuHandler();
				player.resetShop();
			}
		}
		supplyMenus.clear();
	}

	private void addPoints(Player player, int amount) {
		long hash = player.getUsernameHash();
		points.put(hash, (points.containsKey(hash) ? points.get(hash) : 0) + amount);
	}

	private void preparePlayer(Player player, int index) {
		String snapshot = ArenaKitSnapshot.capture(player);
		player.getCache().store(UndeadSiegeMinigame.KIT_CACHE, snapshot);
		player.getCache().store(UndeadSiegeMinigame.STAT_CACHE, manager.captureCombatStats(player));
		manager.savePlayerCache(player);

		player.resetAll();
		player.setBusy(false);
		player.cancelAutoWalk();
		player.resetPath();
		player.exitMorph();
		ArenaKitSnapshot.clearContainers(player);
		manager.applyTemporaryCombatStats(player);
		applyStartingKit(player);
		restoreVitals(player);
		player.setAttribute(Player.SAFE_DEATH_RESPAWN_ATTRIBUTE, UndeadSiegeConfig.exitTile());
		player.setInstanceId(instanceId);
		Point start = UndeadSiegeConfig.START_TILES[index % UndeadSiegeConfig.START_TILES.length];
		player.teleport(start.getX(), start.getY(), true);
		player.message("@mag@" + UndeadSiegeConfig.NAME + ": @whi@temporary kit equipped. Your real gear is safe.");
	}

	private void applyStartingKit(Player player) {
		equipTemporaryItem(player, ItemId.CROSSBOW.id());
		player.getCarriedItems().getInventory().add(new Item(ItemId.CROSSBOW_BOLTS.id(), UndeadSiegeConfig.START_BOLT_COUNT), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.STAFF_OF_AIR.id()), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.AIR_RUNE.id(), UndeadSiegeConfig.START_AIR_RUNES), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.EARTH_RUNE.id(), UndeadSiegeConfig.START_EARTH_RUNES), false);
		player.getCarriedItems().getInventory().add(new Item(ItemId.CHAOS_RUNE.id(), UndeadSiegeConfig.START_CHAOS_RUNES), false);
		for (int i = 0; i < UndeadSiegeConfig.START_SWORDFISH; i++) {
			player.getCarriedItems().getInventory().add(new Item(ItemId.SWORDFISH.id()), false);
		}
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
		player.getUpdateFlags().setAppearanceChanged(true);
	}

	private void equipTemporaryItem(Player player, int itemId) {
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

	private void restoreVitals(Player player) {
		player.getSkills().setLevel(Skill.HITS.id(), player.getSkills().getMaxStat(Skill.HITS.id()));
		player.setPrayerStatePoints(player.getSkills().getLevel(Skill.PRAYER.id()) * 120);
		ActionSender.sendStats(player);
		ActionSender.sendPrayers(player, player.getPrayers().getActivePrayers());
	}

	private void removeInvalidPlayers() {
		List<Player> invalid = new ArrayList<Player>();
		for (Player player : activePlayers.values()) {
			if (player == null || !player.loggedIn() || player.isRemoved() || player.isUnregistering()) {
				invalid.add(player);
			} else if (player.getInstanceId() != instanceId) {
				invalid.add(player);
			} else if (!UndeadSiegeConfig.isInsideArena(player.getLocation())) {
				returnPlayerToArena(player);
			}
		}
		for (Player player : invalid) {
			if (player != null && player.loggedIn()) {
				if (player.getAttribute(Player.SAFE_DEATH_CLEANUP_PENDING_ATTRIBUTE, false)) {
					finishPlayer(player, true, "You were overwhelmed by the undead.", true);
				} else {
					finishPlayer(player, true, "You left the " + UndeadSiegeConfig.NAME + " arena.", true);
				}
			} else {
				finishPlayer(player, false, "", true);
			}
		}
	}

	private void returnPlayerToArena(Player player) {
		Point fallback = closestWalkableStartTile(player.getLocation());
		player.teleport(fallback.getX(), fallback.getY(), true);
		player.cancelAutoWalk();
		player.resetPath();
		player.message("@mag@" + UndeadSiegeConfig.NAME + ": @whi@the barricades keep you inside the siege.");
	}

	private Point closestWalkableStartTile(Point from) {
		Point best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Point tile : UndeadSiegeConfig.START_TILES) {
			if (!isWalkable(tile)) {
				continue;
			}
			int distance = from == null ? 0 : tile.getDistancePythagoras(from);
			if (distance < bestDistance) {
				best = tile;
				bestDistance = distance;
			}
		}
		return best != null ? best : Point.location(UndeadSiegeConfig.START_X, UndeadSiegeConfig.START_Y);
	}

	private void finishPlayer(Player player, boolean reward, String message, boolean teleport) {
		finishPlayer(player, reward, message, teleport, false);
	}

	private void finishPlayer(Player player, boolean reward, String message, boolean teleport, boolean fillBeforeReward) {
		if (player == null || finishedPlayers.contains(player.getUsernameHash())) {
			return;
		}
		long hash = player.getUsernameHash();
		finishedPlayers.add(hash);
		activePlayers.remove(hash);
		manager.untrackPlayer(player, this);
		player.resetAll();
		player.setBusy(false);
		player.cancelAutoWalk();
		player.resetPath();
		player.resetMenuHandler();
		player.resetShop();
		player.removeAttribute(Player.SAFE_DEATH_CLEANUP_PENDING_ATTRIBUTE);
		player.setInstanceId(0);
		manager.recoverKit(player, false);
		restoreVitals(player);
		if (teleport) {
			moveToExit(player);
		}
		if (message != null && message.length() > 0 && player.loggedIn()) {
			player.message(message);
		}
		if (fillBeforeReward && reward && player.loggedIn()) {
			fillInventoryForPayoutQa(player);
		}
		if (reward && player.loggedIn()) {
			awardPayout(player);
		}
		ActionSender.sendInventory(player);
		ActionSender.sendEquipmentStats(player);
	}

	private void fillInventoryForPayoutQa(Player player) {
		while (!player.getCarriedItems().getInventory().full()) {
			player.getCarriedItems().getInventory().add(new Item(ItemId.BRONZE_AXE.id()), false);
		}
		ActionSender.sendInventory(player);
		player.message("@gre@" + UndeadSiegeConfig.NAME + " QA filled inventory before payout.");
	}

	private void moveToExit(Player player) {
		player.setInstanceId(0);
		player.setLocation(UndeadSiegeConfig.exitTile(), true);
		player.resetPath();
		if (player.loggedIn() && player.getChannel() != null && player.getChannel().isActive()) {
			ActionSender.sendWorldInfo(player);
		}
	}

	private void awardPayout(Player player) {
		if (rewardedPlayers.contains(player.getUsernameHash())) {
			return;
		}
		rewardedPlayers.add(player.getUsernameHash());
		if (completedWave <= 0) {
			player.message("No " + UndeadSiegeConfig.NAME + " payout was earned.");
			return;
		}

		int coins = Math.min(UndeadSiegeConfig.REWARD_COINS_CAP,
			completedWave * UndeadSiegeConfig.REWARD_COINS_PER_WAVE);
		giveOrDrop(player, new Item(ItemId.COINS.id(), coins));
		if (completedWave >= 5) {
			giveOrDrop(player, new Item(ItemId.SWORDFISH.id(), 5));
		}
		if (completedWave >= 10) {
			giveOrDrop(player, new Item(ItemId.AIR_RUNE.id(), 120));
			giveOrDrop(player, new Item(ItemId.FIRE_RUNE.id(), 100));
			giveOrDrop(player, new Item(ItemId.DEATH_RUNE.id(), 25));
		}
		player.playerServerMessage(MessageType.QUEST,
			"@mag@" + UndeadSiegeConfig.NAME + ": @whi@payout for wave " + completedWave + " complete.");
	}

	private void giveOrDrop(Player player, Item item) {
		if (player.getCarriedItems().getInventory().canHold(item)) {
			player.getCarriedItems().getInventory().add(item);
			return;
		}
		GroundItem drop = new GroundItem(player.getWorld(), item.getCatalogId(), player.getX(), player.getY(),
			item.getAmount(), player, item.getNoted());
		drop.setInstanceId(player.getInstanceId());
		player.getWorld().registerItem(drop, player.getConfig().GAME_TICK * 300);
		player.message("Your inventory is full, so " + item.getDef(player.getWorld()).getName()
			+ " falls at your feet.");
	}

	private void cleanupRemovedNpcs() {
		List<Npc> removed = new ArrayList<Npc>();
		for (Npc npc : activeNpcs) {
			if (npc == null || npc.isRemoved()) {
				removed.add(npc);
			}
		}
		for (Npc npc : removed) {
			activeNpcs.remove(npc);
			manager.untrackNpc(npc);
			if (npc != null) {
				npcUuids.remove(npc.getUUID());
				npcLastPositions.remove(npc.getUUID());
				npcStuckTicks.remove(npc.getUUID());
			}
		}
	}

	private void cleanupNpcs() {
		for (Npc npc : new ArrayList<Npc>(activeNpcs)) {
			activeNpcs.remove(npc);
			manager.untrackNpc(npc);
			if (npc != null) {
				npcUuids.remove(npc.getUUID());
				npcLastPositions.remove(npc.getUUID());
				npcStuckTicks.remove(npc.getUUID());
				if (!npc.isRemoved()) {
					npc.setShouldRespawn(false);
					world.unregisterNpc(npc);
				}
			}
		}
	}

	private void end(String message) {
		if (finished) {
			return;
		}
		finished = true;
		phase = Phase.ENDING;
		clearSupplyMenus();
		cleanupNpcs();
		for (Player player : new ArrayList<Player>(activePlayers.values())) {
			finishPlayer(player, true, message, true);
		}
		manager.sessionEnded(this);
		stopEvent();
	}

	private void broadcastActive(String message) {
		for (Player player : activePlayers.values()) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private void stopEvent() {
		if (event != null) {
			event.stop();
			event = null;
		}
	}

	private static final class UndeadSiegeEvent extends GameTickEvent {
		private final UndeadSiegeInstance session;

		private UndeadSiegeEvent(World world, UndeadSiegeInstance session) {
			super(world, null, 1, UndeadSiegeConfig.NAME, DuplicationStrategy.ALLOW_MULTIPLE);
			this.session = session;
		}

		@Override
		public void run() {
			session.tick();
			setDelayTicks(1);
		}
	}
}
