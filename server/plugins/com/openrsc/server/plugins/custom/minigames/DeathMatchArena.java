package com.openrsc.server.plugins.custom.minigames;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.combat.scripts.all.VoidKnightBoss;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;
import com.openrsc.server.plugins.triggers.PlayerLogoutTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.openrsc.server.plugins.Functions.multi;

public final class DeathMatchArena implements TalkNpcTrigger, OpNpcTrigger, AttackNpcTrigger, OpLocTrigger,
	KillNpcTrigger, PlayerLoginTrigger, PlayerLogoutTrigger, PlayerRangeNpcTrigger, SpellNpcTrigger {

	private static final int VOID_KNIGHT_ID = NpcId.VOID_KNIGHT.id();
	private static final int FIGHT_VOID_KNIGHT_ID = NpcId.VOID_KNIGHT_ARENA.id();
	private static final boolean VOID_KNIGHT_RELEASE_ENABLED = false;
	private static final String DYNAMIC_ATTRIBUTE = "deathmatch_void_knight_dynamic";
	private static final String OWNER_ATTRIBUTE = "deathmatch_void_knight_owner";

	private static final int LADDER_UP_ID = 5;
	private static final int LADDER_DOWN_ID = 6;
	private static final int ENCLAVE_LADDER_X = 122;
	private static final int ENCLAVE_LADDER_Y = 313;
	private static final int ENCLAVE_UPSTAIRS_X = 122;
	private static final int ENCLAVE_UPSTAIRS_Y = 314;
	private static final int BASEMENT_LADDER_X = 984;
	private static final int BASEMENT_LADDER_Y = 668;
	private static final int BASEMENT_RETURN_X = 984;
	private static final int BASEMENT_RETURN_Y = 667;
	private static final int FIGHT_X = 984;
	private static final int FIGHT_Y = 643;
	private static final int PLAYER_START_X = 984;
	private static final int PLAYER_START_Y = 651;
	private static final int ARENA_MIN_X = 976;
	private static final int ARENA_MAX_X = 992;
	private static final int ARENA_MIN_Y = 635;
	private static final int ARENA_MAX_Y = 655;
	private static final int FIGHT_MIN_X = 976;
	private static final int FIGHT_MAX_X = 992;
	private static final int FIGHT_MIN_Y = 635;
	private static final int FIGHT_MAX_Y = 655;
	private static final int LEGACY_ARENA_MIN_X = 304;
	private static final int LEGACY_ARENA_MAX_X = 320;
	private static final int LEGACY_ARENA_MIN_Y = 59;
	private static final int LEGACY_ARENA_MAX_Y = 94;
	private static final int LEGACY_UNDERGROUND_MIN_X = 304;
	private static final int LEGACY_UNDERGROUND_MAX_X = 320;
	private static final int LEGACY_UNDERGROUND_MIN_Y = 971;
	private static final int LEGACY_UNDERGROUND_MAX_Y = 1006;
	private static final int REENGAGE_DELAY_TICKS = 2;
	private static final int KITE_DURATION_TICKS = 4;
	private static final int KITE_STEP_TICKS = 2;
	private static final int KITE_MIN_DISTANCE = 4;
	private static final int KITE_MAX_DISTANCE = 8;
	private static final int TACTIC_BASE_DELAY_TICKS = 13;
	private static final int TACTIC_RANDOM_DELAY_TICKS = 8;
	private static final int LOW_HITS_KITE_THRESHOLD = 50;
	private static final int RANDOM_KITE_CHANCE = 25;
	private static final int DRAGON_PIECE_CHANCE = 32;
	private static final int VOID_GEAR_CHANCE = 1024;
	private static final int RUPTURE_WARNING_TICKS = 2;
	private static final int RUPTURE_ACTIVE_TICKS = 1;
	private static final int RUPTURE_RADIUS = 1;
	private static final int RUPTURE_PHASE_TWO_MIN_DELAY = 12;
	private static final int RUPTURE_PHASE_TWO_MAX_DELAY = 17;
	private static final int RUPTURE_PHASE_THREE_MIN_DELAY = 8;
	private static final int RUPTURE_PHASE_THREE_MAX_DELAY = 12;
	private static final int DISTANCE_PRESSURE_DISTANCE = 8;
	private static final int DISTANCE_PRESSURE_WARNING_TICKS = 4;
	private static final int DISTANCE_PRESSURE_DAMAGE_TICKS = 8;
	private static final int DISTANCE_PRESSURE_MESSAGE_DELAY = 10;

	private static final WeightedItem[] VOID_KNIGHT_RUNE_REWARDS = {
		new WeightedItem(ItemId.RUNE_DAGGER.id(), 5),
		new WeightedItem(ItemId.RUNE_MACE.id(), 5),
		new WeightedItem(ItemId.RUNE_SHORT_SWORD.id(), 5),
		new WeightedItem(ItemId.RUNE_SCIMITAR.id(), 4),
		new WeightedItem(ItemId.RUNE_LONG_SWORD.id(), 4),
		new WeightedItem(ItemId.RUNE_BATTLE_AXE.id(), 3),
		new WeightedItem(ItemId.RUNE_2_HANDED_SWORD.id(), 3),
		new WeightedItem(ItemId.RUNE_AXE.id(), 4),
		new WeightedItem(ItemId.RUNE_PICKAXE.id(), 3),
		new WeightedItem(ItemId.MEDIUM_RUNE_HELMET.id(), 5),
		new WeightedItem(ItemId.LARGE_RUNE_HELMET.id(), 4),
		new WeightedItem(ItemId.RUNE_CHAIN_MAIL_BODY.id(), 4),
		new WeightedItem(ItemId.RUNE_PLATE_MAIL_BODY.id(), 2),
		new WeightedItem(ItemId.RUNE_PLATE_MAIL_TOP.id(), 2),
		new WeightedItem(ItemId.RUNE_PLATE_MAIL_LEGS.id(), 3),
		new WeightedItem(ItemId.RUNE_SKIRT.id(), 3),
		new WeightedItem(ItemId.RUNE_SQUARE_SHIELD.id(), 4),
		new WeightedItem(ItemId.RUNE_KITE_SHIELD.id(), 3)
	};

	private static final WeightedItem[] VOID_KNIGHT_DRAGON_REWARDS = {
		new WeightedItem(ItemId.DRAGON_BAR.id(), 4),
		new WeightedItem(ItemId.CHIPPED_DRAGON_SCALE.id(), 4),
		new WeightedItem(ItemId.DRAGON_METAL_CHAIN.id(), 4)
	};

	private static final WeightedItem[] VOID_KNIGHT_VOID_REWARDS = {
		new WeightedItem(ItemId.VOID_SCIMITAR.id(), 1),
		new WeightedItem(ItemId.VOID_BOW.id(), 1),
		new WeightedItem(ItemId.VOID_AMULET.id(), 1),
		new WeightedItem(ItemId.VOID_MACE.id(), 1)
	};

	private static final Reward[] VOID_KNIGHT_SUPPLY_REWARDS = {
		new Reward(ItemId.RAW_SWORDFISH.id(), 12, 24, 8, true),
		new Reward(ItemId.RAW_LOBSTER.id(), 24, 42, 7, true),
		new Reward(ItemId.UNIDENTIFIED_TARROMIN.id(), 3, 8, 2, true),
		new Reward(ItemId.LIMPWURT_ROOT.id(), 3, 8, 2, true),
		new Reward(ItemId.DEATH_RUNE.id(), 20, 45, 6),
		new Reward(ItemId.CHAOS_RUNE.id(), 35, 75, 6),
		new Reward(ItemId.LAW_RUNE.id(), 15, 30, 4),
		new Reward(ItemId.NATURE_RUNE.id(), 18, 36, 4),
		new Reward(ItemId.COAL.id(), 50, 100, 4, true),
		new Reward(ItemId.RUNITE_ORE.id(), 1, 3, 2, true)
	};

	private final Map<Long, DeathMatchSession> sessions = new ConcurrentHashMap<Long, DeathMatchSession>();

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isDynamicKnight(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			player.message("The Void Knight is focused on the death match.");
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return false;
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		// The static Void Knight is intentionally attack-only; use the ladder to leave.
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				return true;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			return session != null && session.isReengageCoolingDown();
		}
		return isGatekeeper(npc);
	}

	@Override
	public void onAttackNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				player.message("That duel belongs to another challenger.");
				return;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			if (session != null && session.isReengageCoolingDown()) {
				player.message("You need a moment before re-engaging.");
			}
			return;
		}
		if (!isGatekeeper(npc)) {
			return;
		}
		startChallenge(player);
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		DeathMatchSession session = sessions.get(player.getUsernameHash());
		return session != null && session.knight == npc;
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		DeathMatchSession session = sessions.get(player.getUsernameHash());
		if (session != null && session.knight == npc) {
			session.win();
		}
	}

	@Override
	public boolean blockPlayerRangeNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				return true;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			return session != null && session.isReengageCoolingDown();
		}
		return isGatekeeper(npc);
	}

	@Override
	public void onPlayerRangeNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				player.message("That duel belongs to another challenger.");
				return;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			if (session != null && session.isReengageCoolingDown()) {
				player.message("You need a moment before re-engaging.");
			}
		} else if (isGatekeeper(npc)) {
			startChallenge(player);
		}
	}

	@Override
	public boolean blockSpellNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				return true;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			return session != null && session.isReengageCoolingDown();
		}
		return isGatekeeper(npc);
	}

	@Override
	public void onSpellNpc(Player player, Npc npc) {
		if (isDynamicKnight(npc)) {
			if (!isOwnedDynamicKnight(player, npc)) {
				player.message("That duel belongs to another challenger.");
				return;
			}
			DeathMatchSession session = sessions.get(player.getUsernameHash());
			if (session != null && session.isReengageCoolingDown()) {
				player.message("You need a moment before re-engaging.");
			}
		} else if (isGatekeeper(npc)) {
			startChallenge(player);
		}
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isEnclaveLadderDown(obj) || isBasementLadderUp(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isEnclaveLadderDown(obj)) {
			if (!VOID_KNIGHT_RELEASE_ENABLED) {
				player.message("The Void Knight's chamber is sealed for now.");
				return;
			}
			warnAndEnterBasement(player);
		} else if (isBasementLadderUp(obj)) {
			returnToEnclave(player);
		}
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return isInsideArena(player) || isInsideLegacyDeathMatchArea(player);
	}

	@Override
	public void onPlayerLogin(Player player) {
		DeathMatchSession session = sessions.remove(player.getUsernameHash());
		if (session != null) {
			session.cleanup(false);
		}
		if (isInsideArena(player) || isInsideLegacyDeathMatchArea(player)) {
			clearCombatState(player, null);
			player.teleport(BASEMENT_RETURN_X, BASEMENT_RETURN_Y, true);
			player.message("You are returned from the Death Match Arena.");
		}
	}

	@Override
	public boolean blockPlayerLogout(Player player) {
		return sessions.containsKey(player.getUsernameHash());
	}

	@Override
	public void onPlayerLogout(Player player) {
		DeathMatchSession session = sessions.remove(player.getUsernameHash());
		if (session != null) {
			session.cleanup(false);
		}
	}

	private void enterBasement(Player player) {
		if (sessions.containsKey(player.getUsernameHash())) {
			player.message("You are already in a death match.");
			return;
		}
		if (player.inCombat()) {
			player.message("You need to be free from combat first.");
			return;
		}
		player.resetAll();
		player.teleport(BASEMENT_RETURN_X, BASEMENT_RETURN_Y, true);
		player.message("@mag@You descend into the Void Knight's challenge chamber.");
	}

	private void warnAndEnterBasement(Player player) {
		if (sessions.containsKey(player.getUsernameHash()) || player.inCombat()) {
			enterBasement(player);
			return;
		}

		player.message("@red@Warning: the chamber below leads to the Void Knight boss fight.");
		player.message("@red@Bring food and prayer before attacking him.");
		int option = multi(player, "Climb down.", "Stay upstairs.");
		if (option == 0) {
			enterBasement(player);
		}
	}

	private void returnToEnclave(Player player) {
		DeathMatchSession session = sessions.get(player.getUsernameHash());
		if (session != null) {
			session.finishLoss("You forfeit the death match.", true);
			return;
		}
		clearCombatState(player, null);
		player.teleport(ENCLAVE_UPSTAIRS_X, ENCLAVE_UPSTAIRS_Y, true);
	}

	private synchronized void startChallenge(Player player) {
		if (!VOID_KNIGHT_RELEASE_ENABLED) {
			player.message("The Void Knight is not available yet.");
			return;
		}
		if (sessions.containsKey(player.getUsernameHash())) {
			player.message("You are already fighting the Void Knight.");
			return;
		}
		if (!sessions.isEmpty()) {
			player.message("The Death Match Arena is occupied. Try again in a moment.");
			return;
		}
		if (player.getSkills().getLevel(Skill.HITS.id()) <= 1) {
			player.message("The Void Knight refuses to fight someone already collapsing.");
			return;
		}

		World world = player.getWorld();
		Npc knight = new Npc(world, FIGHT_VOID_KNIGHT_ID, FIGHT_X, FIGHT_Y,
			FIGHT_MIN_X, FIGHT_MAX_X, FIGHT_MIN_Y, FIGHT_MAX_Y);
		knight.setShouldRespawn(false);
		knight.setAttribute(DYNAMIC_ATTRIBUTE, true);
		knight.setAttribute(OWNER_ATTRIBUTE, player.getUsernameHash());
		world.registerNpc(knight);

		player.resetAll();
		player.teleport(PLAYER_START_X, PLAYER_START_Y, true);

		DeathMatchSession session = new DeathMatchSession(player, knight);
		sessions.put(player.getUsernameHash(), session);
		session.start();
	}

	private boolean isGatekeeper(Npc npc) {
		return npc != null && npc.getID() == VOID_KNIGHT_ID;
	}

	private boolean isDynamicKnight(Npc npc) {
		return npc != null && npc.getID() == FIGHT_VOID_KNIGHT_ID && npc.getAttribute(DYNAMIC_ATTRIBUTE, false);
	}

	private boolean isOwnedDynamicKnight(Player player, Npc npc) {
		if (!isDynamicKnight(npc)) {
			return false;
		}
		Long ownerHash = npc.getAttribute(OWNER_ATTRIBUTE, null);
		return ownerHash != null && ownerHash.longValue() == player.getUsernameHash();
	}

	private boolean isEnclaveLadderDown(GameObject obj) {
		return obj != null
			&& obj.getID() == LADDER_DOWN_ID
			&& obj.getX() == ENCLAVE_LADDER_X
			&& obj.getY() == ENCLAVE_LADDER_Y;
	}

	private boolean isBasementLadderUp(GameObject obj) {
		return obj != null
			&& obj.getID() == LADDER_UP_ID
			&& obj.getX() == BASEMENT_LADDER_X
			&& obj.getY() == BASEMENT_LADDER_Y;
	}

	private boolean isInsideArena(Player player) {
		return player != null && player.getLocation().inBounds(ARENA_MIN_X, ARENA_MIN_Y, ARENA_MAX_X, ARENA_MAX_Y);
	}

	private boolean isInsideFightBounds(Player player) {
		return player != null && player.getLocation().inBounds(FIGHT_MIN_X, FIGHT_MIN_Y, FIGHT_MAX_X, FIGHT_MAX_Y);
	}

	private boolean isInsideLegacyDeathMatchArea(Player player) {
		return player != null
			&& (player.getLocation().inBounds(
				LEGACY_ARENA_MIN_X, LEGACY_ARENA_MIN_Y, LEGACY_ARENA_MAX_X, LEGACY_ARENA_MAX_Y)
			|| player.getLocation().inBounds(
				LEGACY_UNDERGROUND_MIN_X, LEGACY_UNDERGROUND_MIN_Y,
				LEGACY_UNDERGROUND_MAX_X, LEGACY_UNDERGROUND_MAX_Y));
	}

	private void broadcastWin(Player winner) {
		String message = "@mag@" + winner.getUsername() + " has defeated the Void Knight beneath the Void Enclave!";
		for (Player player : winner.getWorld().getPlayers()) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private void broadcastRareReward(Player winner, Item item) {
		String itemName = item.getDef(winner.getWorld()).getName();
		String message = "@mag@" + winner.getUsername() + " received " + itemName + " from the Void Knight!";
		for (Player player : winner.getWorld().getPlayers()) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private void awardVoidKnightRewards(Player player) {
		giveReward(player, new Item(ItemId.COINS.id(), DataConversions.random(12000, 28000)));
		Reward supply = rollReward(VOID_KNIGHT_SUPPLY_REWARDS);
		giveReward(player, supply.createItem());
		giveReward(player, new Item(rollWeightedItem(VOID_KNIGHT_RUNE_REWARDS), 1));

		if (DataConversions.random(1, DRAGON_PIECE_CHANCE) == 1) {
			Item dragonPiece = new Item(rollWeightedItem(VOID_KNIGHT_DRAGON_REWARDS), 1);
			giveReward(player, dragonPiece);
			player.message("@red@The Void Knight yields a rare dragon component.");
			broadcastRareReward(player, dragonPiece);
		}

		if (DataConversions.random(1, VOID_GEAR_CHANCE) == 1) {
			Item voidGear = new Item(rollWeightedItem(VOID_KNIGHT_VOID_REWARDS), 1);
			giveReward(player, voidGear);
			player.message("@mag@The Void Knight's core collapses into void gear.");
			broadcastRareReward(player, voidGear);
		}
	}

	private void recordVoidKnightKill(Player player, Npc knight) {
		player.setLastNpcKilledId(knight.getID());
		player.incNpcKills();
		player.addNpcKill(knight, true);
		ActionSender.sendNpcKills(player);
	}

	private void giveReward(Player player, Item item) {
		player.getCarriedItems().getInventory().add(item);
		player.message("The Void Knight yields " + describeReward(player, item) + ".");
	}

	private String describeReward(Player player, Item item) {
		String name = item.getDef(player.getWorld()).getName();
		String notePrefix = item.getNoted() ? "noted " : "";
		if (item.getAmount() == 1) {
			return "a " + notePrefix + name;
		}
		return item.getAmount() + " " + notePrefix + name;
	}

	private Reward rollReward(Reward[] rewards) {
		int totalWeight = 0;
		for (Reward reward : rewards) {
			totalWeight += reward.weight;
		}

		int roll = DataConversions.random(1, totalWeight);
		for (Reward reward : rewards) {
			roll -= reward.weight;
			if (roll <= 0) {
				return reward;
			}
		}
		return rewards[0];
	}

	private int rollWeightedItem(WeightedItem[] rewards) {
		int totalWeight = 0;
		for (WeightedItem reward : rewards) {
			totalWeight += reward.weight;
		}

		int roll = DataConversions.random(1, totalWeight);
		for (WeightedItem reward : rewards) {
			roll -= reward.weight;
			if (roll <= 0) {
				return reward.itemId;
			}
		}
		return rewards[0].itemId;
	}

	private static void clearCombatState(Player player, Npc knight) {
		if (player != null) {
			if (player.getCombatEvent() != null) {
				player.getCombatEvent().stop();
			}
			player.setCombatEvent(null);
			player.setOpponent(null);
			player.setLastOpponent(null);
			player.setHitsMade(0);
			if (player.getSprite() > 7) {
				player.setSprite(4);
			}
			player.setBusy(false);
			player.cancelAutoWalk();
			player.resetPath();
			player.resetFollowing();
			player.resetRange();
			player.setWalkToAction(null);
			player.resetRanAwayTimer();
		}
		if (knight != null) {
			if (knight.getCombatEvent() != null) {
				knight.getCombatEvent().stop();
			}
			knight.setCombatEvent(null);
			knight.setOpponent(null);
			knight.setLastOpponent(null);
			knight.setHitsMade(0);
			if (knight.getSprite() > 7) {
				knight.setSprite(4);
			}
			knight.setBusy(false);
			knight.resetPath();
			knight.resetFollowing();
			knight.resetRange();
			knight.resetRanAwayTimer();
		}
	}

	private final class DeathMatchSession {
		private final Player player;
		private final Npc knight;
		private final DeathMatchEvent event;
		private long nextReengageTick;
		private long nextTacticTick;
		private long kiteUntilTick;
		private long nextKiteStepTick;
		private long nextBossLineTick;
		private long nextRuptureTick;
		private long nextDistancePressureMessageTick;
		private long nextForcedReengageTick;
		private int distancePressureTicks;
		private VoidRupture rupture;
		private boolean hasEnteredCombat;
		private boolean finished;

		private DeathMatchSession(Player player, Npc knight) {
			this.player = player;
			this.knight = knight;
			this.event = new DeathMatchEvent(player.getWorld(), this);
		}

		private void start() {
			long tick = player.getWorld().getServer().getCurrentTick();
			VoidKnightBoss.begin(knight, player);
			player.message("@mag@The Void Knight locks the chamber.");
			player.message("The altar in the arena can recharge your prayer.");
			player.getWorld().getServer().getGameEventHandler().add(event);
			nextTacticTick = tick + 5;
			nextRuptureTick = tick + 10;
			knight.setChasing(player);
		}

		private void tick() {
			if (finished) {
				return;
			}
			if (!player.loggedIn() || player.isRemoved()) {
				finishLoss("", false);
				return;
			}
			if (knight.isRemoved()) {
				finishLoss("The Void Knight slips out of the arena.", true);
				return;
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				finishLoss("", false);
				return;
			}
			if (!isInsideArena(player)) {
				finishLoss("", false);
				return;
			}
			if (!isInsideFightBounds(player)) {
				finishLoss("You leave the fighting floor and forfeit the death match.", true);
				return;
			}

			VoidKnightBoss.pulse(knight, player, true);
			if (finished) {
				return;
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0 || !isInsideArena(player)) {
				finishLoss("", false);
				return;
			}
			long tick = player.getWorld().getServer().getCurrentTick();
			tickVoidRupture(tick);
			applyDistancePressure(tick);
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0 || finished) {
				return;
			}
			runBossMovement(tick);
			enforceReengageDelay();
		}

		private void tickVoidRupture(long tick) {
			int phase = VoidKnightBoss.currentPhase(knight);
			if (rupture != null) {
				rupture.tick();
				return;
			}
			if (phase < 2 || tick < nextRuptureTick) {
				return;
			}

			rupture = new VoidRupture(player.getLocation(), phase);
			nextRuptureTick = tick + ruptureDelay(phase);
			knight.face(player);
			knight.getUpdateFlags().setChatMessage(new com.openrsc.server.model.entity.update.ChatMessage(
				knight, "The floor remembers your feet.", player));
			player.message("@mag@Void energy gathers beneath you. Move!");
		}

		private int ruptureDelay(int phase) {
			if (phase >= 3) {
				return DataConversions.random(RUPTURE_PHASE_THREE_MIN_DELAY, RUPTURE_PHASE_THREE_MAX_DELAY);
			}
			return DataConversions.random(RUPTURE_PHASE_TWO_MIN_DELAY, RUPTURE_PHASE_TWO_MAX_DELAY);
		}

		private void applyDistancePressure(long tick) {
			int distance = distance(knight.getLocation(), player.getLocation());
			if (distance <= DISTANCE_PRESSURE_DISTANCE) {
				distancePressureTicks = 0;
				return;
			}

			distancePressureTicks++;
			if (distancePressureTicks == DISTANCE_PRESSURE_WARNING_TICKS && tick >= nextDistancePressureMessageTick) {
				player.message("@mag@Void pressure builds at the edge of the arena.");
				nextDistancePressureMessageTick = tick + DISTANCE_PRESSURE_MESSAGE_DELAY;
			}
			if (distancePressureTicks < DISTANCE_PRESSURE_DAMAGE_TICKS) {
				return;
			}

			int phase = VoidKnightBoss.currentPhase(knight);
			int damage = phase >= 3 ? DataConversions.random(5, 11) : DataConversions.random(3, 8);
			knight.face(player);
			knight.getUpdateFlags().setChatMessage(new com.openrsc.server.model.entity.update.ChatMessage(
				knight, "No hiding.", player));
			player.message("@mag@The void punishes your distance.");
			VoidKnightBoss.applyVoidDamage(knight, player, damage);
			VoidKnightBoss.queueCastSoon(knight, 0);
			distancePressureTicks = 0;
		}

		private final class VoidRupture {
			private final Point center;
			private final int phase;
			private int warningTicks = RUPTURE_WARNING_TICKS;
			private int activeTicks = RUPTURE_ACTIVE_TICKS;
			private boolean hit;

			private VoidRupture(Point center, int phase) {
				this.center = Point.location(center.getX(), center.getY());
				this.phase = phase;
			}

			private void tick() {
				if (warningTicks > 0) {
					warningTicks--;
					if (warningTicks == 0) {
						player.message("@mag@The void rupture opens.");
					}
					return;
				}

				if (activeTicks <= 0) {
					rupture = null;
					return;
				}

				if (!hit && distance(center, player.getLocation()) <= RUPTURE_RADIUS) {
					hit = true;
					int damage = phase >= 3 ? DataConversions.random(8, 15) : DataConversions.random(5, 11);
					VoidKnightBoss.applyVoidDamage(knight, player, damage);
					drainPrayer(phase >= 3 ? 4 : 2);
					player.message("@mag@The rupture tears through you.");
				}

				activeTicks--;
				if (activeTicks <= 0) {
					rupture = null;
				}
			}

			private void drainPrayer(int amount) {
				int prayer = player.getSkills().getLevel(Skill.PRAYER.id());
				if (prayer <= 0) {
					return;
				}
				player.getSkills().setLevel(Skill.PRAYER.id(), Math.max(0, prayer - amount), true);
			}
		}

		private void runBossMovement(long tick) {
			if (player.inCombat() && knight.inCombat()) {
				if (shouldBreakForKite(tick)) {
					Point kiteTile = chooseKiteTile();
					if (kiteTile != null) {
						startKite(tick, kiteTile);
					}
				}
				return;
			}

			if (tick < kiteUntilTick && tick >= nextKiteStepTick && knight.finishedPath()) {
				Point kiteTile = chooseKiteTile();
				if (kiteTile != null && !kiteTile.equals(knight.getLocation())) {
					knight.walk(kiteTile.getX(), kiteTile.getY());
					knight.face(player);
					nextKiteStepTick = tick + KITE_STEP_TICKS;
					VoidKnightBoss.queueCastSoon(knight, 1);
				}
			}
		}

		private boolean shouldBreakForKite(long tick) {
			if (tick < nextTacticTick || !knight.finishedPath() || !playerLikelyMelee()) {
				return false;
			}
			if (distance(knight.getLocation(), player.getLocation()) > 1) {
				return false;
			}
			if (player.getHitsMade() + knight.getHitsMade() < 4) {
				return false;
			}

			int knightHits = knight.getSkills().getLevel(Skill.HITS.id());
			boolean underPressure = knightHits <= LOW_HITS_KITE_THRESHOLD
				|| player.getHitsMade() > knight.getHitsMade() + 1;
			return underPressure || DataConversions.random(0, 99) < RANDOM_KITE_CHANCE;
		}

		private void startKite(long tick, Point kiteTile) {
			hasEnteredCombat = true;
			clearCombatState(player, knight);
			knight.getBehavior().setRoaming();
			knight.walk(kiteTile.getX(), kiteTile.getY());
			knight.face(player);
			nextReengageTick = tick + REENGAGE_DELAY_TICKS;
			kiteUntilTick = tick + KITE_DURATION_TICKS;
			nextKiteStepTick = tick + KITE_STEP_TICKS;
			nextTacticTick = tick + TACTIC_BASE_DELAY_TICKS + DataConversions.random(0, TACTIC_RANDOM_DELAY_TICKS);
			VoidKnightBoss.queueCastSoon(knight, 1);
			if (tick >= nextBossLineTick) {
				knight.getUpdateFlags().setChatMessage(new com.openrsc.server.model.entity.update.ChatMessage(
					knight, "Keep up.", player));
				nextBossLineTick = tick + 18;
			}
		}

		private Point chooseKiteTile() {
			Point best = null;
			int bestScore = Integer.MIN_VALUE;
			Point current = knight.getLocation();
			Point playerLocation = player.getLocation();
			Point center = Point.location((FIGHT_MIN_X + FIGHT_MAX_X) / 2, (FIGHT_MIN_Y + FIGHT_MAX_Y) / 2);

			for (int x = FIGHT_MIN_X; x <= FIGHT_MAX_X; x++) {
				for (int y = FIGHT_MIN_Y; y <= FIGHT_MAX_Y; y++) {
					Point candidate = Point.location(x, y);
					if (candidate.equals(current) || candidate.equals(playerLocation)) {
						continue;
					}
					int distanceFromPlayer = distance(candidate, playerLocation);
					if (distanceFromPlayer < KITE_MIN_DISTANCE || distanceFromPlayer > KITE_MAX_DISTANCE) {
						continue;
					}
					if (!PathValidation.checkPath(player.getWorld(), current, candidate)) {
						continue;
					}
					if (!PathValidation.checkPath(player.getWorld(), candidate, playerLocation)) {
						continue;
					}

					int edgeDistance = Math.min(
						Math.min(x - FIGHT_MIN_X, FIGHT_MAX_X - x),
						Math.min(y - FIGHT_MIN_Y, FIGHT_MAX_Y - y));
					int score = distanceFromPlayer * 12
						+ edgeDistance * 5
						- distance(candidate, center) * 2
						+ DataConversions.random(0, 4);
					score += 20;
					if (distanceFromPlayer <= distance(current, playerLocation)) {
						score -= 18;
					}
					if (score > bestScore) {
						bestScore = score;
						best = candidate;
					}
				}
			}
			return best;
		}

		private boolean playerLikelyMelee() {
			long sinceCastMs = System.currentTimeMillis() - player.lastCast;
			if (sinceCastMs >= 0 && sinceCastMs < player.getConfig().GAME_TICK * 6L) {
				return false;
			}
			return player.getRangeEvent() == null
				&& player.getThrowingEvent() == null
				&& player.getRangeEquip() < 0
				&& player.getThrowingEquip() < 0;
		}

		private int distance(Point first, Point second) {
			return Math.max(Math.abs(first.getX() - second.getX()), Math.abs(first.getY() - second.getY()));
		}

		private boolean isReengageCoolingDown() {
			if (player.inCombat() && knight.inCombat()) {
				hasEnteredCombat = true;
				nextReengageTick = 0L;
				return false;
			}
			if (player.inCombat() || knight.inCombat()) {
				hasEnteredCombat = true;
				return true;
			}
			if (!hasEnteredCombat) {
				return false;
			}

			long tick = player.getWorld().getServer().getCurrentTick();
			if (nextReengageTick == 0L) {
				startReengageDelay(tick);
			}
			return tick < nextReengageTick;
		}

		private void enforceReengageDelay() {
			if (isReengageCoolingDown()) {
				return;
			}
			if (player.inCombat() || knight.inCombat()) {
				return;
			}

			long tick = player.getWorld().getServer().getCurrentTick();
			if (tick < kiteUntilTick) {
				return;
			}
			if (tick < nextReengageTick) {
				return;
			}
			if (distance(knight.getLocation(), player.getLocation()) > 1 && tick >= nextForcedReengageTick) {
				knight.resetPath();
				knight.resetFollowing();
				knight.setChasing(player);
				nextForcedReengageTick = tick + REENGAGE_DELAY_TICKS;
			} else if (!knight.isChasing()) {
				knight.setChasing(player);
			}
		}

		private void startReengageDelay(long tick) {
			nextReengageTick = tick + REENGAGE_DELAY_TICKS;
			knight.getBehavior().setRoaming();
			knight.resetPath();
			knight.resetFollowing();
		}

		private void win() {
			if (finished) {
				return;
			}
			finished = true;
			sessions.remove(player.getUsernameHash());
			event.stop();
			if (player.loggedIn()) {
				recordVoidKnightKill(player, knight);
				broadcastWin(player);
				player.message("You defeat the Void Knight.");
				ActionSender.sendSound(player, "victory");
				if (isInsideArena(player)) {
					player.teleport(BASEMENT_RETURN_X, BASEMENT_RETURN_Y, true);
				}
				awardVoidKnightRewards(player);
			}
		}

		private void finishLoss(String message, boolean teleport) {
			if (finished) {
				return;
			}
			finished = true;
			sessions.remove(player.getUsernameHash());
			event.stop();
			if (message != null && !message.isEmpty() && player.loggedIn()) {
				player.message(message);
			}
			cleanup(teleport);
		}

		private void cleanup(boolean teleport) {
			event.stop();
			clearCombatState(player, knight);
			if (player.getWorld().hasNpc(knight)) {
				player.getWorld().unregisterNpc(knight);
			}
			if (teleport && player.loggedIn() && isInsideArena(player)) {
				player.teleport(BASEMENT_RETURN_X, BASEMENT_RETURN_Y, true);
			}
		}
	}

	private static final class DeathMatchEvent extends GameTickEvent {
		private final DeathMatchSession session;

		private DeathMatchEvent(World world, DeathMatchSession session) {
			super(world, session.player, 1, "Death Match Arena", DuplicationStrategy.ALLOW_MULTIPLE);
			this.session = session;
		}

		@Override
		public void run() {
			session.tick();
			setDelayTicks(1);
		}
	}

	private static final class Reward {
		private final int itemId;
		private final int minAmount;
		private final int maxAmount;
		private final int weight;
		private final boolean noted;

		private Reward(int itemId, int minAmount, int maxAmount, int weight) {
			this(itemId, minAmount, maxAmount, weight, false);
		}

		private Reward(int itemId, int minAmount, int maxAmount, int weight, boolean noted) {
			this.itemId = itemId;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.weight = weight;
			this.noted = noted;
		}

		private Item createItem() {
			return new Item(itemId, DataConversions.random(minAmount, maxAmount), noted);
		}
	}

	private static final class WeightedItem {
		private final int itemId;
		private final int weight;

		private WeightedItem(int itemId, int weight) {
			this.itemId = itemId;
			this.weight = weight;
		}
	}
}
