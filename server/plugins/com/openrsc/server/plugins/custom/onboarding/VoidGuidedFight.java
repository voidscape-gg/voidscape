package com.openrsc.server.plugins.custom.onboarding;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.VoidTutorialIsle;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

import java.util.concurrent.atomic.AtomicInteger;

import static com.openrsc.server.plugins.Functions.npcYell;
import static com.openrsc.server.plugins.Functions.npcattack;

/**
 * A scripted 1v1 against an instanced NPC with a scoped safe death: no bones,
 * no item loss, respawn beside the Void Guide. The instance keeps the fight
 * private and is required by the safe-death contract (Player.killedBy needs
 * instanceId != 0 and an NPC killer).
 */
public final class VoidGuidedFight {
	public enum Kind {
		SPAR,
		AMBUSH
	}

	// South edge of the Ring — reachable again at both spar and ambush stages.
	static final Point RESPAWN_POINT = Point.location(
		VoidTutorialIsle.FIGHT_RESPAWN_X, VoidTutorialIsle.FIGHT_RESPAWN_Y);
	private static final String FIGHT_ATTRIBUTE = "void_guided_fight";
	// UndeadSiege owns instance ids 20000+; stay clear of it.
	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(60000);
	private static final int ATTACK_DELAY_TICKS = 3;
	private static final int TIMEOUT_TICKS = 200;
	private static final int LEASH_DISTANCE = 12;

	private VoidGuidedFight() {
	}

	static boolean isFighting(Player player) {
		return player.getAttribute(FIGHT_ATTRIBUTE, null) != null;
	}

	static void begin(Player player, Kind kind, int npcId, int npcX, int npcY, String taunt) {
		if (player == null || isFighting(player) || player.inCombat()) {
			return;
		}

		World world = player.getWorld();
		int instance = INSTANCE_COUNTER.incrementAndGet();

		Npc foe = new Npc(world, npcId, npcX, npcY, npcX - 6, npcX + 6, npcY - 6, npcY + 6);
		foe.setShouldRespawn(false);
		// No bones, no kill counters/achievements; onKillNpc then dispatches
		// synchronously on the tick thread — win handlers must stay non-blocking.
		foe.setAttribute(Npc.SUPPRESS_DEFAULT_DEATH_ATTRIBUTE, true);
		foe.setInstanceId(instance);
		world.registerNpc(foe);

		player.setInstanceId(instance);
		player.setAttribute(Player.SAFE_DEATH_RESPAWN_ATTRIBUTE, RESPAWN_POINT);

		FightState state = new FightState(kind, foe);
		player.setAttribute(FIGHT_ATTRIBUTE, state);

		if (taunt != null) {
			npcYell(player, foe, taunt);
		}

		state.watchdog = new Watchdog(world, player, state);
		world.getServer().getGameEventHandler().add(state.watchdog);
	}

	/**
	 * Called from KillNpcTrigger when the player kills a fight NPC.
	 *
	 * @return the fight kind, or null if this NPC wasn't the player's active foe.
	 */
	static Kind completeWin(Player player, Npc npc) {
		FightState state = player.getAttribute(FIGHT_ATTRIBUTE, null);
		if (state == null || state.foe != npc) {
			return null;
		}
		cleanup(player, state, true);
		return state.kind;
	}

	/** Silently tears down any active fight (logout, path chosen, etc). */
	static void abort(Player player) {
		FightState state = player.getAttribute(FIGHT_ATTRIBUTE, null);
		if (state != null) {
			cleanup(player, state, false);
		}
	}

	private static void cleanup(Player player, FightState state, boolean restoreHits) {
		if (state.watchdog != null) {
			state.watchdog.stop();
		}
		if (!state.foe.isRemoved()) {
			player.getWorld().unregisterNpc(state.foe);
		}
		player.removeAttribute(FIGHT_ATTRIBUTE);
		player.removeAttribute(Player.SAFE_DEATH_RESPAWN_ATTRIBUTE);
		player.removeAttribute(Player.SAFE_DEATH_CLEANUP_PENDING_ATTRIBUTE);
		if (player.getInstanceId() != 0) {
			player.setInstanceId(0);
		}
		if (restoreHits) {
			player.getSkills().setLevel(Skill.HITS.id(), player.getSkills().getMaxStat(Skill.HITS.id()));
		}
	}

	private static final class FightState {
		private final Kind kind;
		private final Npc foe;
		private Watchdog watchdog;

		private FightState(Kind kind, Npc foe) {
			this.kind = kind;
			this.foe = foe;
		}
	}

	/**
	 * Per-fight tick event: starts the attack after the taunt lands, then
	 * watches for loss (safe-death cleanup pending), escape, and timeout.
	 * Runs on the game-tick thread — no blocking dialogue here.
	 */
	private static final class Watchdog extends GameTickEvent {
		private final Player player;
		private final FightState state;
		private int ticks = 0;

		private Watchdog(World world, Player player, FightState state) {
			super(world, player, 1, "Void Guided Fight", DuplicationStrategy.ALLOW_MULTIPLE);
			this.player = player;
			this.state = state;
		}

		@Override
		public void run() {
			if (!player.loggedIn()) {
				cleanup(player, state, false);
				return;
			}

			// Player died: core already respawned them safely and reset the instance.
			if (player.getAttribute(Player.SAFE_DEATH_CLEANUP_PENDING_ATTRIBUTE, false)) {
				cleanup(player, state, false);
				VoidGuidedTour.onFightLost(player, state.kind);
				return;
			}

			// Foe died: KillNpcTrigger owns the win path; just stop ticking.
			if (state.foe.isRemoved()) {
				stop();
				return;
			}

			ticks++;
			if (ticks == ATTACK_DELAY_TICKS) {
				npcattack(state.foe, player);
			}

			boolean escaped = !player.getLocation().inVoidTutorialIsle()
				|| !player.getLocation().withinRange(state.foe.getLocation(), LEASH_DISTANCE);
			if (ticks > TIMEOUT_TICKS || (ticks > ATTACK_DELAY_TICKS && escaped)) {
				cleanup(player, state, true);
				VoidGuidedTour.onFightEscaped(player, state.kind);
			}
		}
	}
}
