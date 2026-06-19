package com.openrsc.server.plugins.custom.minigames.voidcolossus;

import com.google.inject.Inject;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.PlayerDeathTrigger;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;
import com.openrsc.server.plugins.triggers.PlayerLogoutTrigger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.displayTeleportBubble;
import static com.openrsc.server.plugins.Functions.multi;

/**
 * Void Colossus solo-instance manager.
 *
 * Every player fights the Colossus alone in a PRIVATE phase. Thanks to the engine instancing layer
 * (Entity.instanceId + the RegionManager visibility filter), all instances live at the SAME arena
 * coordinates but cannot see each other — so there is exactly one physical arena, no landscape copies.
 *
 * On entry the player is given a fresh instanceId, teleported in, and their own boss is spawned with
 * the same id (so only they can see/fight it, and only they see its loot). Killing the boss schedules
 * an in-instance respawn so the player keeps farming. Leaving (exit rift), dying, or logging out tears
 * the instance down and returns the player to the normal phase (instanceId 0).
 */
public class VoidColossusArena implements OpLocTrigger, KillNpcTrigger, PlayerLoginTrigger, PlayerLogoutTrigger, PlayerDeathTrigger {

	public static final int ARENA_CENTER_X = 552;
	public static final int ARENA_CENTER_Y = 71;
	public static final int ARENA_LANDING_X = 552;
	public static final int ARENA_LANDING_Y = 82;
	public static final int ARENA_RADIUS = 14;

	private static final int RESPAWN_DELAY_MS = 12_000; // in-instance boss respawn (solo farming)

	private static final int VOID_RIFT_ID = 1306;
	static final int VOID_COLOSSUS_OBJECT_ID = 1307;
	private static final int HUB_RIFT_X = 113;
	private static final int HUB_RIFT_Y = 321;
	private static final int HUB_ARRIVAL_X = 113;
	private static final int HUB_ARRIVAL_Y = 322;
	private static final int ARENA_RIFT_X = 553;
	private static final int ARENA_RIFT_Y = 83;
	private static final int BOSS_OBJECT_X = ARENA_CENTER_X - 2;
	private static final int BOSS_OBJECT_Y = ARENA_CENTER_Y - 2;

	// Instance bookkeeping. instanceId starts at 1 (0 is reserved for the normal overworld).
	private static final AtomicInteger nextInstanceId = new AtomicInteger(1);
	private final Map<Long, Integer> playerInstance = new ConcurrentHashMap<>();  // playerHash -> instanceId
	private final Map<Integer, Long> instanceOwner = new ConcurrentHashMap<>();   // instanceId -> playerHash
	private final Map<Integer, Npc> instanceBoss = new ConcurrentHashMap<>();     // instanceId -> live boss
	private final Map<Integer, GameObject> instanceBossObject = new ConcurrentHashMap<>();
	private final Map<Integer, Set<GroundItem>> instanceDrops = new ConcurrentHashMap<>();
	private final Map<Integer, Set<Npc>> instanceSummons = new ConcurrentHashMap<>();

	// Singleton handle so dev tooling (::colossus) can drive the real entry flow without the rift.
	private static volatile VoidColossusArena instance;

	@Inject
	private World world;

	public VoidColossusArena() {
		instance = this;
	}

	/** Dev hook: drop the player straight into a fresh Colossus instance (real spawn/teleport path). */
	public static boolean devEnter(Player player) {
		VoidColossusArena self = instance;
		if (self == null) {
			return false;
		}
		self.enterInstance(player);
		return true;
	}

	static void trackDrop(GroundItem item) {
		VoidColossusArena self = instance;
		if (self == null || item == null || item.getInstanceId() <= 0) {
			return;
		}
		self.instanceDrops.computeIfAbsent(item.getInstanceId(), ignored -> ConcurrentHashMap.newKeySet())
			.add(item);
	}

	static void trackSummon(Npc npc) {
		VoidColossusArena self = instance;
		if (self == null || npc == null || npc.getInstanceId() <= 0) {
			return;
		}
		self.instanceSummons.computeIfAbsent(npc.getInstanceId(), ignored -> ConcurrentHashMap.newKeySet())
			.add(npc);
	}

	static boolean isColossusObject(GameObject obj) {
		return obj != null && obj.getID() == VOID_COLOSSUS_OBJECT_ID;
	}

	static Npc bossFor(Player player) {
		VoidColossusArena self = instance;
		if (self == null || player == null) {
			return null;
		}
		Integer instanceId = self.playerInstance.get(player.getUsernameHash());
		if (instanceId == null || instanceId != player.getInstanceId()) {
			return null;
		}
		Npc boss = self.instanceBoss.get(instanceId);
		if (boss == null || boss.isRemoved()) {
			return null;
		}
		return boss;
	}

	static int liveSummonCount(int instanceId) {
		VoidColossusArena self = instance;
		if (self == null) {
			return 0;
		}
		Set<Npc> summons = self.instanceSummons.get(instanceId);
		if (summons == null) {
			return 0;
		}
		int live = 0;
		for (Npc summon : summons) {
			if (summon != null && !summon.isRemoved()
				&& summon.getSkills().getLevel(com.openrsc.server.constants.Skill.HITS.id()) > 0) {
				live++;
			}
		}
		return live;
	}

	/** True if (x,y) is inside the circular plaza (a couple tiles of slack for the rim). */
	public static boolean inArena(int x, int y) {
		int dx = x - ARENA_CENTER_X;
		int dy = y - ARENA_CENTER_Y;
		return dx * dx + dy * dy <= (ARENA_RADIUS + 2) * (ARENA_RADIUS + 2);
	}

	// --- Portals -----------------------------------------------------------------------------------

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isHubRift(obj) || isArenaRift(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isHubRift(obj)) {
			if (!confirmTravel(player, "The rift hums with colossal fury beyond.",
				"Face the Void Colossus alone?")) {
				return;
			}
			enterInstance(player);
		} else if (isArenaRift(obj)) {
			if (!confirmTravel(player, "The rift leads back to the safety of the Void Enclave.",
				"Leave the arena?")) {
				return;
			}
			exitInstance(player, true);
		}
	}

	private boolean confirmTravel(Player player, String intro, String confirm) {
		if (player.inCombat()) {
			player.message("You cannot enter the rift while fighting.");
			return false;
		}
		player.message(intro);
		player.message(confirm);
		int option = multi(player, "Enter the rift", "Stay here");
		if (option != 0) {
			player.message("You step away from the rift.");
			return false;
		}
		if (player.inCombat()) {
			player.message("You cannot enter the rift while fighting.");
			return false;
		}
		return true;
	}

	// --- Instance lifecycle ------------------------------------------------------------------------

	private void enterInstance(Player player) {
		long hash = player.getUsernameHash();
		// Defensive: tear down any stale instance the player still owns.
		Integer previousInstance = playerInstance.remove(hash);
		if (previousInstance != null) {
			cleanupInstance(previousInstance);
		}
		if (player.getInstanceId() > 0 && inArena(player.getX(), player.getY())) {
			cleanupInstance(player.getInstanceId());
			clearPlayerState(player);
		}
		int instanceId = nextInstanceId.getAndIncrement();
		playerInstance.put(hash, instanceId);
		instanceOwner.put(instanceId, hash);
		player.setInstanceId(instanceId);

		player.message("You step into the Void Rift.");
		displayTeleportBubble(player, player.getX(), player.getY(), false);
		delay(2);
		player.teleport(ARENA_LANDING_X, ARENA_LANDING_Y, true);
		spawnBoss(instanceId);
		player.message("The void folds around you and opens onto a shattered plaza, yours alone.");
	}

	private void exitInstance(Player player, boolean teleport) {
		long hash = player.getUsernameHash();
		Integer instanceId = playerInstance.remove(hash);
		if (instanceId != null) {
			cleanupInstance(instanceId);
		}
		clearPlayerState(player);
		if (teleport) {
			player.message("You step into the Void Rift.");
			displayTeleportBubble(player, player.getX(), player.getY(), false);
			delay(2);
			player.teleport(HUB_ARRIVAL_X, HUB_ARRIVAL_Y, true);
			player.message("The void folds around you and opens into the Void Enclave.");
		}
	}

	private void cleanupInstance(int instanceId) {
		instanceOwner.remove(instanceId);
		cleanupInstanceDrops(instanceId);
		cleanupInstanceSummons(instanceId);
		GameObject bossObject = instanceBossObject.remove(instanceId);
		if (bossObject != null && !bossObject.isRemoved()) {
			world.unregisterGameObject(bossObject);
		}
		Npc boss = instanceBoss.remove(instanceId);
		if (boss != null && !boss.isRemoved()) {
			boss.setShouldRespawn(false);
			world.unregisterNpc(boss);
		}
	}

	private void clearPlayerState(Player player) {
		player.resetCombatEvent();
		player.setLastOpponent(null);
		player.setBusy(false);
		player.setInstanceId(0);
	}

	private void cleanupInstanceDrops(int instanceId) {
		Set<GroundItem> drops = instanceDrops.remove(instanceId);
		if (drops == null) {
			return;
		}
		for (GroundItem drop : drops) {
			if (drop != null && !drop.isRemoved()) {
				world.unregisterItem(drop);
			}
		}
	}

	private void cleanupInstanceSummons(int instanceId) {
		Set<Npc> summons = instanceSummons.remove(instanceId);
		if (summons == null) {
			return;
		}
		for (Npc summon : summons) {
			if (summon != null && !summon.isRemoved()) {
				summon.setShouldRespawn(false);
				world.unregisterNpc(summon);
			}
		}
	}

	/** Spawn (or respawn) the boss for an instance at the shared arena center, tagged to that phase. */
	private void spawnBoss(int instanceId) {
		GameObject staleObject = instanceBossObject.remove(instanceId);
		if (staleObject != null && !staleObject.isRemoved()) {
			world.unregisterGameObject(staleObject);
		}

		GameObject bossObject = new GameObject(world, com.openrsc.server.model.Point.location(BOSS_OBJECT_X, BOSS_OBJECT_Y),
			VOID_COLOSSUS_OBJECT_ID, 0, 0);
		bossObject.setInstanceId(instanceId);
		world.registerGameObject(bossObject);
		instanceBossObject.put(instanceId, bossObject);

		// The NPC sprite is the visible boss body; the object above is the rift/base under it. Keeping
		// the real combat state on the NPC preserves ranged/magic target packets, projectiles, HP,
		// hit splats, and drops without making NPCs true 3D entities.
		NPCLoc loc = new NPCLoc(NpcId.VOID_COLOSSUS.id(),
			ARENA_CENTER_X, ARENA_CENTER_Y,
			ARENA_CENTER_X, ARENA_CENTER_X,
			ARENA_CENTER_Y, ARENA_CENTER_Y);
		Npc boss = new Npc(world, loc);
		boss.setShouldRespawn(false); // respawn is handled per-instance below, not by the engine
		boss.setAttribute("raid_boss", true);
		boss.setInstanceId(instanceId);
		world.registerNpc(boss);
		boss.face(ARENA_LANDING_X, ARENA_LANDING_Y);
		instanceBoss.put(instanceId, boss);
		VoidColossusCombat.ensureBossAi(boss);
	}

	// --- Kill -> in-instance respawn ---------------------------------------------------------------

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		// The plugin chain only invokes onKillNpc when block returns true. VoidColossusCombat returns
		// true too — both fire (crackers there, respawn here); the OR'd default-block is harmless.
		return npc.getID() == NpcId.VOID_COLOSSUS.id();
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		if (npc.getID() != NpcId.VOID_COLOSSUS.id()) {
			return;
		}
		final int instanceId = npc.getInstanceId();
		instanceBoss.remove(instanceId);
		cleanupInstanceSummons(instanceId);
		GameObject bossObject = instanceBossObject.remove(instanceId);
		if (bossObject != null && !bossObject.isRemoved()) {
			world.unregisterGameObject(bossObject);
		}
		// Respawn only if the instance is still owned by an online player.
		world.getServer().getGameEventHandler().add(new DelayedEvent(world, null, RESPAWN_DELAY_MS,
				"Colossus Respawn") {
			@Override
			public void run() {
				if (instanceOwner.containsKey(instanceId) && !instanceBoss.containsKey(instanceId)) {
					spawnBoss(instanceId);
				}
				stop();
			}
		});
	}

	// --- Teardown on logout / death ----------------------------------------------------------------

	@Override
	public boolean blockPlayerLogout(Player player) {
		return playerInstance.containsKey(player.getUsernameHash());
	}

	@Override
	public void onPlayerLogout(Player player) {
		if (playerInstance.containsKey(player.getUsernameHash())) {
			exitInstance(player, false);
		}
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return playerInstance.containsKey(player.getUsernameHash())
			|| inArena(player.getX(), player.getY());
	}

	@Override
	public void onPlayerLogin(Player player) {
		long hash = player.getUsernameHash();
		Integer instanceId = playerInstance.remove(hash);
		if (instanceId != null) {
			cleanupInstance(instanceId);
		}
		if (player.getInstanceId() > 0 && inArena(player.getX(), player.getY())) {
			cleanupInstance(player.getInstanceId());
		}
		if (inArena(player.getX(), player.getY())) {
			clearPlayerState(player);
			player.teleport(HUB_ARRIVAL_X, HUB_ARRIVAL_Y, true);
			player.message("You are returned from the Void Colossus arena.");
		}
	}

	@Override
	public boolean blockPlayerDeath(Player player) {
		return playerInstance.containsKey(player.getUsernameHash());
	}

	@Override
	public void onPlayerDeath(Player player) {
		// Dying in the arena drops the player to the normal death-respawn point, so clear their phase
		// and tear down the instance (the engine handles where they respawn).
		if (playerInstance.containsKey(player.getUsernameHash())) {
			exitInstance(player, false);
		}
	}

	private boolean isHubRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && obj.getX() == HUB_RIFT_X && obj.getY() == HUB_RIFT_Y;
	}

	private boolean isArenaRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && obj.getX() == ARENA_RIFT_X && obj.getY() == ARENA_RIFT_Y;
	}
}
