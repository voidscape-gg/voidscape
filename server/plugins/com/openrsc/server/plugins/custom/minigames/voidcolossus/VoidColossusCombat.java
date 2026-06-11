package com.openrsc.server.plugins.custom.minigames.voidcolossus;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;

/**
 * Death handler for the Void Colossus.
 *
 * The Colossus is a solo-instance boss, so the engine's normal combat path drives the fight: the
 * player attacks, the engine starts combat (the 1v1 lock is fine — only one player is ever phased
 * into the arena), the boss walks/chases via {@link com.openrsc.server.model.entity.npc.NpcBehavior}
 * and animates its walk + slam combat frames natively. Special moves (roar / void torrent) are layered
 * on top by {@link ColossusBossEvent}, armed at spawn. No attack carve-out is needed any more.
 *
 * On death, onKillNpc scatters 10 phase-private Christmas crackers across random walkable arena tiles.
 * The boss is not in Npc.removeHandledInPlugin, so the engine's default drop (bones) + respawn still
 * run — onKillNpc only adds the cracker hoard and the kill announcement on top.
 */
public final class VoidColossusCombat implements KillNpcTrigger {

	private static final int CRACKER_COUNT = 10;
	private static final int CRACKER_DESPAWN_TICKS = 600; // ~6.4 min at 640ms ticks
	private static final int SCATTER_RADIUS = 11;         // keep crackers on the plaza floor

	private static boolean isColossus(Npc npc) {
		return npc != null && npc.getID() == NpcId.VOID_COLOSSUS.id();
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		// Return true so onKillNpc fires. The boss isn't in removeHandledInPlugin, so the engine's
		// default drops + remove()/respawn still run regardless of this return.
		return isColossus(npc);
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		if (!isColossus(npc)) {
			return;
		}
		World world = npc.getWorld();
		int instanceId = npc.getInstanceId();
		int dropped = 0;
		for (int i = 0; i < CRACKER_COUNT; i++) {
			Point tile = randomArenaTile(world);
			if (tile == null) {
				continue;
			}
			// (Player) null owner -> ownerless ground item. Tagged to the boss's instance so it is
			// only visible to that solo player (the visibility filter makes the loot phase-private).
			GroundItem cracker = new GroundItem(world, ItemId.CHRISTMAS_CRACKER.id(),
				tile.getX(), tile.getY(), 1, (Player) null);
			cracker.setInstanceId(instanceId);
			VoidColossusArena.trackDrop(cracker);
			world.registerItem(cracker, world.getServer().getConfig().GAME_TICK * CRACKER_DESPAWN_TICKS);
			dropped++;
		}
		announceKill(world, player, dropped);
	}

	/** Random walkable tile inside the plaza (not the boss centre), or null after enough misses. */
	private Point randomArenaTile(World world) {
		for (int attempt = 0; attempt < 40; attempt++) {
			int x = VoidColossusArena.ARENA_CENTER_X + DataConversions.random(-SCATTER_RADIUS, SCATTER_RADIUS);
			int y = VoidColossusArena.ARENA_CENTER_Y + DataConversions.random(-SCATTER_RADIUS, SCATTER_RADIUS);
			if (x == VoidColossusArena.ARENA_CENTER_X && y == VoidColossusArena.ARENA_CENTER_Y) {
				continue; // skip the boss tile
			}
			if (!VoidColossusArena.inArena(x, y)) {
				continue;
			}
			if ((world.getTile(x, y).traversalMask & CollisionFlag.FULL_BLOCK) != 0) {
				continue; // skip blocked/ocean tiles
			}
			return Point.location(x, y);
		}
		return null;
	}

	private void announceKill(World world, Player killer, int crackers) {
		// Solo instances: message only the player who killed it (a server-wide broadcast every kill
		// would be spam). The crackers are phase-private to them anyway.
		if (killer != null && killer.loggedIn()) {
			killer.message("@mag@You have slain the Void Colossus! " + crackers
				+ " Christmas crackers scatter across the plaza.");
		}
	}

	/** Starts the boss offense event if it isn't already running. */
	static void ensureBossAi(Npc boss) {
		if (!ColossusBossEvent.isRunning(boss)) {
			ColossusBossEvent.arm(boss);
			boss.getWorld().getServer().getGameEventHandler().add(
				new ColossusBossEvent(boss.getWorld(), boss));
		}
	}
}
