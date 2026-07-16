package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.voiddungeon.VoidDungeonTraversalGrace;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.handler.GameEventHandler;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.WildernessRules;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.TargetMobStruct;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.AttackPlayerTrigger;

import static com.openrsc.server.plugins.Functions.inArray;

public class AttackHandler implements PayloadProcessor<TargetMobStruct, OpcodeIn> {
	private static final String PK_CATCHING_SIM_OWNER = "pkcatchsim_owner";
	private static final String PK_CATCHING_SIM_COMBAT_ACTIVE = "pkcatchsim_combat_active";
	private static final String PK_CATCHING_SIM_RUNNING = "pkcatchsim_running";
	private static final String PK_CATCHING_SIM_ATTACK_BLOCKED_UNTIL =
		"pkcatchsim_attack_blocked_until";

	public void process(TargetMobStruct payload, Player player) throws Exception {
		OpcodeIn pID = payload.getOpcode();

		if (player.getDuel().isDueling()) {
			return;
		}

		Mob affectedMob = null;
		if (pID == OpcodeIn.PLAYER_ATTACK) {
			affectedMob = player.getWorld().getPlayer(payload.serverIndex);
		} else if (pID == OpcodeIn.NPC_ATTACK) {
			affectedMob = player.getWorld().getNpc(payload.serverIndex);
		}
		if (affectedMob == null || affectedMob.equals(player)) {
			player.resetPath();
			return;
		}
		if (!passesVoidDungeonPvpGate(player, affectedMob)) {
			player.resetPath();
			return;
		}

		boolean pkCatchingSimulatorTarget = isPkCatchingSimulatorTarget(affectedMob);
		if (pkCatchingSimulatorTarget && isPkCatchingSimulatorReattackBlocked(player, affectedMob)) {
			player.resetPath();
			player.resetFollowing();
			player.setWalkToAction(null);
			return;
		}

		if (player.inCombat()) {
			if (!pkCatchingSimulatorTarget || !clearStalePkCatchingSimulatorCombat(player, affectedMob)) {
				player.message("You are already busy fighting!");
				player.resetPath();
				return;
			}
		}

		if (player.isBusy() && !pkCatchingSimulatorTarget) {
			player.resetPath();
			return;
		}

		player.cancelAutoWalk();
		player.resetAll();

		if (affectedMob.isPlayer()) {
			assert affectedMob instanceof Player;
			Player pl = (Player) affectedMob;
			//Immune players cannot be attacked until their immunity wears off.
			if (!pl.canBeReattacked()) {
				if (pl.getLocation().inWilderness() || player.getConfig().USES_PK_MODE) {
					player.resetPath();
				}
				return;
			}
		} else {
			assert affectedMob instanceof Npc;
			Npc n = (Npc) affectedMob;
			long curTick = player.getWorld().getServer().getCurrentTick();
			long runTick = n.getRanAwayTimer();
			if (n.isRespawning()) return;
			if (n.getX() == 0 && n.getY() == 0)
				return;
			if (n.getID() == NpcId.OGRE_TRAINING_CAMP.id()) {
				boolean melee = player.getRangeEquip() < 0 && player.getThrowingEquip() < 0;
				boolean inPen = player.getX() >= 663 && player.getX() <= 668
					&& player.getY() >= 531 && player.getY() <= 535;
				if (melee || inPen) {
					player.message("these ogres are for range combat training only");
					return;
				}
			} else if (inArray(n.getID(), NpcId.BATTLE_MAGE_GUTHIX.id(), NpcId.BATTLE_MAGE_ZAMORAK.id(), NpcId.BATTLE_MAGE_SARADOMIN.id())
				&& (!player.getCache().hasKey("mage_arena") || player.getCache().getInt("mage_arena") < 2)) {
				player.message("you are not yet ready to fight the battle mages");
				return;
			} else if (!pkCatchingSimulatorTarget
				&& (curTick <= runTick || (curTick <= runTick + 1 && !n.finishedPath()))) {
				//Moving retreating enemies are immune from attack requests for an extra tick.
				player.resetPath();
				return;
			}
		}
		if (affectedMob.isNpc()) {
			VoidDungeonTraversalGrace.clear(player);
		}

		if (player.getRangeEquip() < 0 && player.getThrowingEquip() < 0) {

			boolean movingPvpStyleTarget = affectedMob.isPlayer()
				? !affectedMob.finishedPath()
				: pkCatchingSimulatorTarget
					&& affectedMob.getAttribute(PK_CATCHING_SIM_RUNNING, false);
			if ((affectedMob.isPlayer() || pkCatchingSimulatorTarget)
				&& !player.finishedPath() && movingPvpStyleTarget) {
				int pidlessCatchingDistanceOffset = 0;
				if (affectedMob.isPlayer() && player.getConfig().PIDLESS_CATCHING && !player.willBeProcessedBefore((Player)affectedMob)) {
					// other player has already moved this tick, meaning the gap is 1 more than is rendered on either person's client
					pidlessCatchingDistanceOffset += 1;
				}

				// authentically, if you're more than a couple tiles away while already moving, the attack packet just resets your path.
				// https://www.youtube.com/watch?v=ia02boQlVts&t=1131s
				 if (player.getLocation().getDistancePythagoras(affectedMob.getLocation()) > player.getConfig().MAX_PVP_MELEE_ATTACK_DISTANCE + pidlessCatchingDistanceOffset) {
					 player.resetPath();
					 return;
				 }
			}

			player.setFollowing(affectedMob, 0, false, true);

			int radius = affectedMob.isPlayer() || pkCatchingSimulatorTarget
				? player.getConfig().PVP_CATCHING_DISTANCE
				: player.getConfig().PVM_CATCHING_DISTANCE;
			player.setWalkToAction(new WalkToMobAction(player, affectedMob, radius, true, ActionType.ATTACK) {
				public void executeInternal() {
					getPlayer().resetFollowing();

					if (!passesVoidDungeonPvpGate(getPlayer(), mob)) {
						return;
					}
					if (mob.inCombat() && !isPkCatchingSimulatorTarget(mob)
						&& getPlayer().getRangeEquip() < 0 && getPlayer().getThrowingEquip() < 0) {
						getPlayer().message("I can't get close enough");
						return;
					}
					if ((!pkCatchingSimulatorTarget && getPlayer().isBusy())
						|| (!pkCatchingSimulatorTarget && mob.isBusy())
						|| !getPlayer().checkAttack(mob, false)) {
						return;
					}
					if (mob.isNpc()) {
						NpcInteraction interaction = NpcInteraction.NPC_ATTACK;
						NpcInteraction.setInteractions(((Npc)mob), getPlayer(), interaction);
						getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob}, this);
					} else {
						getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(AttackPlayerTrigger.class, getPlayer(), new Object[]{getPlayer(), mob}, this);
					}
				}

				@Override
				public boolean isPvPAttack() {
					return pkCatchingSimulatorTarget || super.isPvPAttack();
				}
			});
		} else { // Attack with ranged instead of melee
			if (!player.checkAttack(affectedMob, true)) {
				return;
			}
			final Mob target = affectedMob;
			player.resetPath();
			int radius = player.getProjectileRadius();
			player.setFollowing(affectedMob, radius, false);
			player.setWalkToAction(new WalkToMobAction(player, affectedMob, radius, false, ActionType.ATTACK) {
				public void executeInternal() {
					if (getPlayer().isBusy() || getPlayer().inCombat()) return;
					if (!getPlayer().checkAttack(getMob(), true)) return;
					getPlayer().resetFollowing();
					if (getMob().isPlayer()) {
						Player affectedPlayer = (Player) getMob();
						getPlayer().setSkulledOn(affectedPlayer);
						affectedPlayer.getTrade().resetAll();
						if (affectedPlayer.getMenuHandler() != null) {
							affectedPlayer.resetMenuHandler();
						}
						if (affectedPlayer.accessingBank()) {
							affectedPlayer.resetBank();
						}
						if (affectedPlayer.accessingShop()) {
							affectedPlayer.resetShop();
						}
					}

					// Authentic player always faced NW
					getPlayer().face(getPlayer().getX() + 1, getPlayer().getY() - 1);

					if (getPlayer().getRangeEquip() > 0) {
						// TODO: replace with gameEventHandler.addOrUpdate()
						final GameEventHandler gameEventHandler = getPlayer().getWorld()
							.getServer()
							.getGameEventHandler();

						RangeEvent rangeEvent = null;

						for (final GameTickEvent gameTickEvent : gameEventHandler.getPlayerEvents(getPlayer())) {
							if (gameTickEvent instanceof RangeEvent) {
								rangeEvent = (RangeEvent) gameTickEvent;
								break;
							}
						}

						if (rangeEvent != null) {
							if (!rangeEvent.getTarget().equals(getMob())) {
								rangeEvent.reTarget(getMob());
							}

							rangeEvent.restart();
							getPlayer().setRangeEvent(rangeEvent);
							return;
						}

						rangeEvent = new RangeEvent(getPlayer().getWorld(), getPlayer(), 1, target);
						getPlayer().setRangeEvent(rangeEvent);
						gameEventHandler.add(rangeEvent);
					} else {
						// TODO: replace with gameEventHandler.addOrUpdate()
						final GameEventHandler gameEventHandler = getPlayer().getWorld()
							.getServer()
							.getGameEventHandler();

						ThrowingEvent throwingEvent = null;

						for (final GameTickEvent gameTickEvent : gameEventHandler.getPlayerEvents(getPlayer())) {
							if (gameTickEvent instanceof ThrowingEvent) {
								throwingEvent = (ThrowingEvent) gameTickEvent;
								break;
							}
						}

						if (throwingEvent != null) {
							if (!throwingEvent.getTarget().equals(getMob())) {
								throwingEvent.reTarget(getMob());
							}

							throwingEvent.restart();
							getPlayer().setThrowingEvent(throwingEvent);
							return;
						}

						throwingEvent = new ThrowingEvent(getPlayer().getWorld(), getPlayer(), 1, target);
						getPlayer().setThrowingEvent(throwingEvent);
						gameEventHandler.add(throwingEvent);
					}
				}
			});
		}
	}

	private boolean passesVoidDungeonPvpGate(Player player, Mob target) {
		if (!target.isPlayer()
			|| WildernessRules.canAttackVoidDungeonPvp(player, (Player) target)) {
			return true;
		}

		player.message(WildernessRules.voidDungeonPvpMessage());
		return false;
	}

	private boolean isPkCatchingSimulatorTarget(Mob mob) {
		return mob != null && mob.isNpc() && mob.getAttribute(PK_CATCHING_SIM_OWNER, null) != null;
	}

	private boolean isPkCatchingSimulatorReattackBlocked(Player player, Mob target) {
		long currentTick = player.getWorld().getServer().getCurrentTick();
		Long blockedUntil = target.getAttribute(PK_CATCHING_SIM_ATTACK_BLOCKED_UNTIL, null);
		if (blockedUntil != null && currentTick < blockedUntil.longValue()) {
			return true;
		}
		long ranAwayTick = target.getRanAwayTimer();
		return ranAwayTick > 0
			&& ranAwayTick + player.getConfig().PVP_REATTACK_TIMER > currentTick;
	}

	private boolean clearStalePkCatchingSimulatorCombat(Player player, Mob target) {
		if (player.getAttribute(PK_CATCHING_SIM_COMBAT_ACTIVE, false)
			|| target.getAttribute(PK_CATCHING_SIM_COMBAT_ACTIVE, false)) {
			return false;
		}

		if (player.getOpponent() != target && target.getOpponent() != player) {
			return !player.inCombat();
		}

		if (player.getCombatEvent() != null) {
			player.getCombatEvent().stop();
		}
		if (target.getCombatEvent() != null) {
			target.getCombatEvent().stop();
		}
		player.setCombatEvent(null);
		target.setCombatEvent(null);
		player.setOpponent(null);
		target.setOpponent(null);
		player.setLastOpponent(null);
		target.setLastOpponent(null);
		player.setHitsMade(0);
		target.setHitsMade(0);
		player.setBusy(false);
		target.setBusy(false);
		player.resetFollowing();
		target.resetFollowing();
		player.removeAttribute(PK_CATCHING_SIM_COMBAT_ACTIVE);
		target.removeAttribute(PK_CATCHING_SIM_COMBAT_ACTIVE);
		if (player.getSprite() > 7) {
			player.setSprite(4);
		}
		if (target.getSprite() > 7) {
			target.setSprite(4);
		}
		return true;
	}
}
