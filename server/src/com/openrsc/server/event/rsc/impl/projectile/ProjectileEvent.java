package com.openrsc.server.event.rsc.impl.projectile;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.SingleTickEvent;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.UUID;

public class ProjectileEvent extends SingleTickEvent {

	Mob caster, opponent;
	protected int damage;
	protected int attackerMaxHit;
	protected int type;
	boolean canceled;
	boolean shouldChase;
	private final UUID voidArenaSessionId;

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type) {
		this(world, caster, opponent, damage, type, true);
	}

	public ProjectileEvent(World world, Mob caster, Mob opponent, int damage, int type, boolean setChasing) {
		this(world, caster, opponent, damage, type, setChasing, DuplicationStrategy.ONE_PER_MOB);
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
							   final boolean setChasing, final DuplicationStrategy duplicationStrategy)
	{
		this(world, caster, opponent, damage, type, setChasing, duplicationStrategy, 0);
	}

	public ProjectileEvent(final World world, final Mob caster, final Mob opponent, final int damage, final int type,
							   final boolean setChasing, final DuplicationStrategy duplicationStrategy, final int attackerMaxHit)
	{
		super(world, caster, 1, "Projectile Event", duplicationStrategy);
		this.caster = caster;
		this.opponent = opponent;
		this.damage = damage;
		this.type = type;
		this.shouldChase = setChasing;
		this.attackerMaxHit = attackerMaxHit;
		this.voidArenaSessionId = world.getVoidArena().projectileSessionId(caster, opponent);

		sendProjectile(caster, opponent);
		if (caster.isPlayer() && opponent.isPlayer()) {
			Player oppPlayer = (Player) opponent;
			Player casterPlayer = (Player) caster;
			if (!casterPlayer.getDuel().isDuelActive())
				casterPlayer.setSkulledOn(oppPlayer);
			String casterName = casterPlayer.getUsername();

			oppPlayer.message("Warning! " + casterName + " is shooting at you!");
		}
	}

	private void sendProjectile(Mob caster, Mob opponent) {
		Projectile projectile = new Projectile(caster, opponent, type);
		caster.getUpdateFlags().setProjectile(projectile);
	}

	@Override
	public void action() {
		if (canImpact() && caster.withinRange(opponent, 15)) {// maybe this will
			// cancel the damage
			// out on death
			projectileDamage();
			if (opponent.isPlayer()) {
				if (((Player) opponent).getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_RECOIL.id())) {
					recoilDamage((Player) opponent, caster, damage);
				} else if (opponent.getSkills().getLevel(Skill.HITS.id()) > 0) {
					if (((Player) opponent).checkRingOfLife(caster))
						return;
				}
			}
		}
	}

	protected final boolean canImpact() {
		return !canceled && caster.sharesInstanceWith(opponent)
			&& getWorld().getVoidArena().canProjectileImpact(caster, opponent, voidArenaSessionId);
	}

	private void recoilDamage(Player opponent, Mob caster, int damage) {
		int reflectedDamage = damage / 10 + ((damage > 0) ? 1 : 0);
		if (reflectedDamage == 0)
			return;

		if (opponent.getCache().hasKey("ringofrecoil")) {
			int ringCheck = opponent.getCache().getInt("ringofrecoil");
			if (getWorld().getServer().getConfig().RING_OF_RECOIL_LIMIT - ringCheck <= reflectedDamage) {
				reflectedDamage = getWorld().getServer().getConfig().RING_OF_RECOIL_LIMIT - ringCheck;
				opponent.getCache().remove("ringofrecoil");
				opponent.getCarriedItems().shatter(new Item(ItemId.RING_OF_RECOIL.id()));
			} else {
				opponent.getCache().set("ringofrecoil", ringCheck + reflectedDamage);
			}
		} else {
			opponent.getCache().put("ringofrecoil", reflectedDamage);
			opponent.message("You start a new ring of recoil");
		}

		caster.getSkills().subtractLevel(Skill.HITS.id(), reflectedDamage, false);
		caster.getUpdateFlags().setDamage(new Damage(caster, reflectedDamage));

		if (caster.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			if (type == 2 || type == 5) {
				opponent.resetRange();
			}
			caster.killedBy(opponent);
		} else {
			if (caster.isPlayer()) {
				((Player) caster).checkRingOfLife(opponent);
			}
		}
	}

	private void projectileDamage() {
		if (caster.isPlayer()
				&& opponent.isRemoved()
				&& type == 2
		) {
				caster.resetRange();
		}

		int lastHits = opponent.getLevel(Skill.HITS.id());
		opponent.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		opponent.getUpdateFlags().setDamage(new Damage(opponent, damage).withHitFeedback(caster, attackerMaxHit));


		if (caster.isPlayer()) {
			Player casterPlayer = (Player) caster;
			if (opponent.isNpc()) {
				Npc npc = (Npc) opponent;
				if (type == 1 || type == 4) {
					damage = Math.min(damage, lastHits);
					npc.addMageDamage(casterPlayer, damage);
				}
				else if (type == 2 || type == 5) {
					damage = Math.min(damage, lastHits);
					npc.addRangeDamage(casterPlayer, damage);
				}
			}
		}

		// Update party menu with new HITS stat.
		if (opponent.isPlayer()) {
			Player affectedPlayer = (Player) opponent;
			ActionSender.sendStat(affectedPlayer, Skill.HITS.id());
			if (affectedPlayer.getConfig().WANT_PARTIES) {
				if (affectedPlayer.getParty() != null) {
					affectedPlayer.getParty().sendParty();
				}
			}
		}

		if (opponent.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			if (caster.isPlayer()) {
				Player player = (Player) caster;
				if (type == 2 || type == 5) {
					player.resetRange();
				}
			}
			if (opponent.isNpc() && caster.isPlayer()) {
				final Player playerCaster = (Player) caster;
				final Npc npcOpponent = (Npc) opponent;
				npcOpponent.killedBy(playerCaster);
			} else if(opponent.isPlayer() && caster.isPlayer()) {
				final Player playerCaster = (Player) caster;
				final Player playerOpponent = (Player) opponent;
				playerOpponent.killedBy(playerCaster);
			} else {
				opponent.killedBy(caster);
			}
		} else {
			if (opponent.isNpc() && caster.isPlayer()) {
				Npc npc = (Npc) opponent;
				Player player = (Player) caster;
				// voidscape: raid bosses (Void Colossus) never chase ranged/magic attackers — they
				// hold their arena position and retaliate via their own AI event instead.
				if (!npc.getAttribute("raid_boss", false)
					&& !npc.isChasing() && !npc.inCombat() && npc.getCombatState() != CombatState.RUNNING && this.shouldChase) {
					npc.setChasing(player);
				}
			}
		}
	}

	public void setCanceled(boolean b) {
		canceled = b;
	}

}
