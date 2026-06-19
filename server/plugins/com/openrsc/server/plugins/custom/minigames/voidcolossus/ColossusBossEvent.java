package com.openrsc.server.plugins.custom.minigames.voidcolossus;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.impl.ObjectRemover;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.List;

/**
 * Ranged/magic controller for the hybrid Void Colossus.
 *
 * The boss does not melee, walk, or chase. It holds the centre of the arena as a sprite NPC standing
 * on a 3D rift base, and this tick event pressures the phased player with ranged shards, void claw
 * magic, and capped voidborn summons.
 */
public final class ColossusBossEvent extends GameTickEvent {

	private static final String AI_FLAG = "colossus_ai";
	private static final String NEXT_SHARD = "colossus_next_shard";
	private static final String NEXT_CLAWS = "colossus_next_claws";
	private static final String NEXT_SUMMON = "colossus_next_summon";

	private static final int SHARD_MIN_DELAY = 4;
	private static final int SHARD_MAX_DELAY = 6;
	private static final int CLAWS_MIN_DELAY = 9;
	private static final int CLAWS_MAX_DELAY = 14;
	private static final int SUMMON_MIN_DELAY = 15;
	private static final int SUMMON_MAX_DELAY = 24;
	private static final int MAX_SUMMONS = 3;
	private static final int SUMMON_SCATTER_RADIUS = 10;
	private static final int VOID_SHARD_PROJECTILE = 7;
	private static final int VOID_CLAW_PROJECTILE = 8;
	private static final int VOID_SHARD_CHARGE_OBJECT = 1308;
	private static final int VOID_CLAW_CHARGE_OBJECT = 1309;

	// Dev inspection toggle (::colossuspeace): suppress boss offense while keeping the model spawned.
	public static volatile boolean PEACEFUL = false;

	private final Npc boss;

	public ColossusBossEvent(World world, Npc boss) {
		super(world, null, 1, "Colossus Boss AI", DuplicationStrategy.ALLOW_MULTIPLE);
		this.boss = boss;
	}

	public static boolean isRunning(Npc boss) {
		return boss != null && boss.getAttribute(AI_FLAG, false);
	}

	public static void arm(Npc boss) {
		boss.setAttribute(AI_FLAG, true);
		long tick = boss.getWorld().getServer().getCurrentTick();
		boss.setAttribute(NEXT_SHARD, tick + SHARD_MIN_DELAY);
		boss.setAttribute(NEXT_CLAWS, tick + CLAWS_MIN_DELAY);
		boss.setAttribute(NEXT_SUMMON, tick + SUMMON_MIN_DELAY);
	}

	@Override
	public void run() {
		if (boss == null || boss.isRemoved()) {
			shutDown();
			return;
		}
		if (boss.isRespawning() || boss.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			setDelayTicks(1);
			return;
		}

		List<Player> targets = arenaTargets();
		if (targets.isEmpty() || PEACEFUL) {
			setDelayTicks(1);
			return;
		}

		long tick = boss.getWorld().getServer().getCurrentTick();
		if (tick >= boss.getAttribute(NEXT_SHARD, 0L)) {
			voidShard(randomTarget(targets));
			boss.setAttribute(NEXT_SHARD, tick + DataConversions.random(SHARD_MIN_DELAY, SHARD_MAX_DELAY));
		}
		if (tick >= boss.getAttribute(NEXT_CLAWS, 0L)) {
			voidClaws(randomTarget(targets));
			boss.setAttribute(NEXT_CLAWS, tick + DataConversions.random(CLAWS_MIN_DELAY, CLAWS_MAX_DELAY));
		}
		if (tick >= boss.getAttribute(NEXT_SUMMON, 0L)) {
			summonVoidborn(randomTarget(targets));
			boss.setAttribute(NEXT_SUMMON, tick + DataConversions.random(SUMMON_MIN_DELAY, SUMMON_MAX_DELAY));
		}
		setDelayTicks(1);
	}

	private List<Player> arenaTargets() {
		List<Player> targets = new ArrayList<>();
		int instanceId = boss.getInstanceId();
		for (Player player : getWorld().getPlayers()) {
			if (player == null || !player.loggedIn() || player.isRemoved()) {
				continue;
			}
			if (player.getInstanceId() != instanceId) {
				continue;
			}
			if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
				continue;
			}
			if (!VoidColossusArena.inArena(player.getX(), player.getY())) {
				continue;
			}
			targets.add(player);
		}
		return targets;
	}

	private Player randomTarget(List<Player> targets) {
		return targets.get(DataConversions.getRandom().nextInt(targets.size()));
	}

	private void voidShard(Player target) {
		if (target == null) {
			return;
		}
		flashBossEffect(VOID_SHARD_CHARGE_OBJECT, Point.location(VoidColossusArena.ARENA_CENTER_X + 2,
			VoidColossusArena.ARENA_CENTER_Y - 1));
		boss.getUpdateFlags().setChatMessage(new ChatMessage(boss, "The Void Colossus fires a void shard!", target));
		getWorld().getServer().getGameEventHandler().add(
			new ProjectileEvent(getWorld(), boss, target, DataConversions.random(0, 7), VOID_SHARD_PROJECTILE, false));
	}

	private void voidClaws(Player target) {
		if (target == null) {
			return;
		}
		flashBossEffect(VOID_CLAW_CHARGE_OBJECT, Point.location(VoidColossusArena.ARENA_CENTER_X - 2,
			VoidColossusArena.ARENA_CENTER_Y - 1));
		GameObject claws = new GameObject(getWorld(), target.getLocation(), 1142, 0, 0);
		claws.setInstanceId(boss.getInstanceId());
		getWorld().registerGameObject(claws);
		getWorld().getServer().getGameEventHandler().add(new ObjectRemover(getWorld(), claws, 2));

		int prayer = target.getSkills().getLevel(Skill.PRAYER.id());
		int drain = Math.min(prayer, DataConversions.random(2, 5));
		if (drain > 0) {
			target.getSkills().setLevel(Skill.PRAYER.id(), prayer - drain, true);
			target.message("@mag@Void claws tear at the ground beneath you. Your prayers are drained.");
		} else {
			target.message("@mag@Void claws tear at the ground beneath you.");
		}
		getWorld().getServer().getGameEventHandler().add(
			new ProjectileEvent(getWorld(), boss, target, DataConversions.random(0, 6), VOID_CLAW_PROJECTILE, false));
	}

	private void flashBossEffect(int objectId, Point location) {
		GameObject effect = new GameObject(getWorld(), location, objectId, 0, 0);
		effect.setInstanceId(boss.getInstanceId());
		getWorld().registerGameObject(effect);
		getWorld().getServer().getGameEventHandler().add(new ObjectRemover(getWorld(), effect, 2));
	}

	private void summonVoidborn(Player target) {
		if (target == null || VoidColossusArena.liveSummonCount(boss.getInstanceId()) >= MAX_SUMMONS) {
			return;
		}
		int count = DataConversions.random(1, 2);
		for (int i = 0; i < count && VoidColossusArena.liveSummonCount(boss.getInstanceId()) < MAX_SUMMONS; i++) {
			Point tile = randomSummonTile();
			if (tile == null) {
				continue;
			}
			NPCLoc loc = new NPCLoc(NpcId.VOID_KNIGHT_VOIDBORN.id(),
				tile.getX(), tile.getY(),
				tile.getX() - 3, tile.getX() + 3,
				tile.getY() - 3, tile.getY() + 3);
			Npc summon = new Npc(getWorld(), loc);
			summon.setShouldRespawn(false);
			summon.setInstanceId(boss.getInstanceId());
			getWorld().registerNpc(summon);
			VoidColossusArena.trackSummon(summon);
			summon.setChasing(target);
		}
		target.message("@mag@The Void Colossus tears open the rift and calls voidborn to the arena.");
	}

	private Point randomSummonTile() {
		for (int attempt = 0; attempt < 40; attempt++) {
			int x = VoidColossusArena.ARENA_CENTER_X + DataConversions.random(-SUMMON_SCATTER_RADIUS, SUMMON_SCATTER_RADIUS);
			int y = VoidColossusArena.ARENA_CENTER_Y + DataConversions.random(-SUMMON_SCATTER_RADIUS, SUMMON_SCATTER_RADIUS);
			if (!VoidColossusArena.inArena(x, y)) {
				continue;
			}
			if (Math.abs(x - VoidColossusArena.ARENA_CENTER_X) <= 2
				&& Math.abs(y - VoidColossusArena.ARENA_CENTER_Y) <= 2) {
				continue;
			}
			if (getWorld().getTile(x, y) == null
				|| (getWorld().getTile(x, y).traversalMask & CollisionFlag.FULL_BLOCK) != 0) {
				continue;
			}
			return Point.location(x, y);
		}
		return null;
	}

	private void shutDown() {
		if (boss != null) {
			boss.removeAttribute(AI_FLAG);
		}
		stop();
	}
}
