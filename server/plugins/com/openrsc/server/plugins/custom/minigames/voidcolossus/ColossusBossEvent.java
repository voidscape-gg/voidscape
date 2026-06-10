package com.openrsc.server.plugins.custom.minigames.voidcolossus;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.DataConversions;

/**
 * Void Colossus special moves.
 *
 * Now that the boss is a solo phased instance, the ENGINE owns the base fight: the player attacks, the
 * engine starts combat (the 1v1 lock is fine — only one player is ever in the arena), and the boss
 * walks/chases ({@link com.openrsc.server.model.entity.npc.NpcBehavior}) and plays its walk + slam
 * combat frames natively. This per-boss tick event only LAYERS the two timed specials on top while the
 * boss is engaged with its player: a telegraphed roar and a prayer-draining void torrent. It never
 * touches the combat sprite or facing — the engine owns those during the fight.
 */
public final class ColossusBossEvent extends GameTickEvent {

	private static final String AI_FLAG = "colossus_ai";
	private static final String NEXT_ROAR = "colossus_next_roar";
	private static final String NEXT_TORRENT = "colossus_next_torrent";

	private static final int ROAR_MIN_DELAY = 15;
	private static final int ROAR_MAX_DELAY = 25;
	private static final int TORRENT_MIN_DELAY = 10;
	private static final int TORRENT_MAX_DELAY = 18;

	// Dev inspection toggle (::colossuspeace): suppress the specials. The base engine melee is avoided
	// during inspection simply by not attacking the boss, which is non-aggressive and only paces.
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
		boss.setAttribute(NEXT_ROAR, tick + ROAR_MAX_DELAY);
		boss.setAttribute(NEXT_TORRENT, tick + TORRENT_MIN_DELAY);
	}

	@Override
	public void run() {
		// Permanent per-boss event (started at spawn). Only stops if the NPC is truly removed.
		if (boss == null || boss.isRemoved()) {
			shutDown();
			return;
		}
		if (boss.isRespawning() || boss.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			setDelayTicks(1);
			return;
		}

		// Specials only fire while the boss is actually engaged with its solo player (provoked). When
		// idle the boss just paces via the engine roam — no unprovoked attacks.
		Player target = engagedPlayer();
		if (target == null || PEACEFUL) {
			setDelayTicks(1);
			return;
		}

		long tick = boss.getWorld().getServer().getCurrentTick();
		if (tick >= boss.getAttribute(NEXT_ROAR, 0L)) {
			roar(target);
			boss.setAttribute(NEXT_ROAR, tick + DataConversions.random(ROAR_MIN_DELAY, ROAR_MAX_DELAY));
		}
		if (tick >= boss.getAttribute(NEXT_TORRENT, 0L)) {
			voidTorrent(target);
			boss.setAttribute(NEXT_TORRENT, tick + DataConversions.random(TORRENT_MIN_DELAY, TORRENT_MAX_DELAY));
		}
		setDelayTicks(1);
	}

	/** The solo player the boss is fighting, if any (its opponent, in-arena and alive). */
	private Player engagedPlayer() {
		Mob opponent = boss.getOpponent();
		if (opponent != null && opponent.isPlayer()) {
			Player p = (Player) opponent;
			if (p.loggedIn() && p.getSkills().getLevel(Skill.HITS.id()) > 0
					&& VoidColossusArena.inArena(p.getX(), p.getY())) {
				return p;
			}
		}
		return null;
	}

	/** Telegraphed heavy roar on the engaged player. */
	private void roar(Player target) {
		boss.getUpdateFlags().setChatMessage(new ChatMessage(boss, "The Void Colossus unleashes a roar!", target));
		target.message("@mag@The Void Colossus unleashes a roar!");
		hit(target, DataConversions.random(0, 8));
	}

	/** Prayer-draining torrent: projectile + prayer drain + light damage. No face() — the engine owns
	 *  the boss's orientation/combat sprite during the fight, and the projectile fires boss -> target. */
	private void voidTorrent(Player target) {
		boss.getUpdateFlags().setProjectile(new Projectile(boss, target, 1));
		int prayer = target.getSkills().getLevel(Skill.PRAYER.id());
		int drain = Math.min(prayer, DataConversions.random(3, 8));
		if (drain > 0) {
			target.getSkills().setLevel(Skill.PRAYER.id(), prayer - drain, true);
			target.message("@mag@The Void Colossus summons a void torrent! Your prayers are drained.");
		} else {
			target.message("@mag@The Void Colossus summons a void torrent!");
		}
		hit(target, DataConversions.random(0, 5));
	}

	private void hit(Player player, int damage) {
		int current = player.getSkills().getLevel(Skill.HITS.id());
		int actual = Math.min(Math.max(damage, 0), current);
		player.getSkills().subtractLevel(Skill.HITS.id(), actual, true);
		player.getUpdateFlags().setDamage(new Damage(player, actual));
		if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			player.killedBy(boss);
		}
	}

	private void shutDown() {
		if (boss != null) {
			boss.removeAttribute(AI_FLAG);
		}
		stop();
	}
}
