package com.openrsc.server.event.rsc.impl.combat;

import com.openrsc.server.constants.Constants;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.Skills;
import com.openrsc.server.content.dueljournal.DuelJournalSession;
import com.openrsc.server.content.duelproof.DuelProofService;
import com.openrsc.server.content.duelproof.DuelProofSession;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.Prayers;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.states.CombatState;
import com.openrsc.server.model.world.WildernessRules;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.PidShuffler;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.voidscape.duelproof.DuelProofSpec;

public class CombatEvent extends GameTickEvent {

	private final Mob attackerMob, defenderMob;
	private final DuelProofSession proofSession;
	private int roundNumber = 0;
	boolean isPvPCombat = false;
	boolean forceTwoTickRounds = false;
	private int[] poisonedWeapons = {ItemId.POISONED_BRONZE_DAGGER.id(), ItemId.POISONED_IRON_DAGGER.id(), ItemId.POISONED_STEEL_DAGGER.id(), ItemId.POISONED_BLACK_DAGGER.id(), ItemId.POISONED_MITHRIL_DAGGER.id(), ItemId.POISONED_ADAMANTITE_DAGGER.id(), ItemId.POISONED_RUNE_DAGGER.id(), ItemId.POISONED_DRAGON_DAGGER.id()};

	public CombatEvent(World world, Mob attacker, Mob defender) {
		this(world, attacker, defender, null);
	}

	public CombatEvent(World world, Mob attacker, Mob defender,
					   final DuelProofSession proofSession) {
		super(world, null, 0, "Combat Event", DuplicationStrategy.ONE_PER_MOB);
		this.attackerMob = attacker;
		this.defenderMob = defender;
		this.proofSession = proofSession;
		//Reset retreat timers so it is possible to use spells if a retreating enemy attacks someone new before their timer expires.
		attackerMob.resetRanAwayTimer();
		defenderMob.resetRanAwayTimer();
		if (attackerMob.isPlayer() && defenderMob.isPlayer()) this.isPvPCombat = true;

		if (attackerMob.isNpc() && defenderMob.isPlayer()) forceTwoTickRounds = true;

		if (isPvPCombat) {
			//Dueling in RSC was always 2-2.
			if (((Player) attackerMob).getDuel().isDueling()) {
				forceTwoTickRounds = true;
			} else if (attackerMob.getConfig().SHUFFLE_PID_ORDER) {
				for (int curPid : PidShuffler.pidProcessingOrder) {
					Player p = getWorld().getPlayer(curPid);
					if (p == attackerMob) {
						//Attacker has lower PID, so we go to 2-2.
						forceTwoTickRounds = true;
						break;
					}
					if (p == defenderMob) {
						//Defender has lower PID, so this combat encounter is 3-1.
						break;
					}
				}
			} else {
				for (Player p : getWorld().getPlayers()) {
					if (p == attackerMob) {
						//Attacker has lower PID, so we go to 2-2.
						forceTwoTickRounds = true;
						break;
					}
					if (p == defenderMob) {
						//Defender has lower PID, so this combat encounter is 3-1.
						break;
					}
				}
			}
		}



		attacker.getWorld().getServer().getCombatScriptLoader().checkAndExecuteOnStartCombatScript(attacker, defender);
		if (attacker.isNpc()) {
			((Npc) attacker).setExecutedAggroScript(false);
		} else if (defender.isNpc()) {
			((Npc) defender).setExecutedAggroScript(false);
		}
	}

	private void onDeath(Mob killed, Mob killer) {

		/* Commented out useless codeblock. Can be put back if these plugins are implemented some day. 2021-03-05
		if (killer.isPlayer() && killed.isNpc()) {
			// this interface doesn't even exist anymore, so this code block is dead, never returns. 2021-03-05
			if (killed.getWorld().getServer().getPluginHandler().handlePlugin((Player)killer, "PlayerKilledNpc", new Object[]{((Player) killer), ((Npc) killed)})) {
				return;
			}
		} else if (killer.isPlayer() && killed.isPlayer()) {
			// no default action currently, so this code block is dead, never returns. 2021-03-05
			if (killed.getWorld().getServer().getPluginHandler().handlePlugin((Player)killer, "PlayerKilledPlayer", new Object[]{((Player) killer), ((Player) killed)})) {
				return;
			}
		}
		*/

		killed.setLastCombatState(CombatState.LOST);
		killer.setLastCombatState(CombatState.WON);

			if (killed.isPlayer() && killer.isPlayer()) {
			int[] skillsDist = new int[Skill.maxId(Skill.ATTACK.name(), Skill.DEFENSE.name(),
				Skill.STRENGTH.name(), Skill.HITS.name()) + 1];

			Player playerKiller = (Player) killer;
			Player playerKilled = (Player) killed;

			int exp = Formulae.combatExperience(playerKilled);
			switch (playerKiller.getCombatStyle()) {
				case Skills.CONTROLLED_MODE:
					for (int skillId : new int[]{Skill.ATTACK.id(), Skill.DEFENSE.id(), Skill.STRENGTH.id()}) {
						skillsDist[skillId] = 1;
					}
					break;
				case Skills.AGGRESSIVE_MODE:
					skillsDist[Skill.STRENGTH.id()] = 3;
					break;
				case Skills.ACCURATE_MODE:
					skillsDist[Skill.ATTACK.id()] = 3;
					break;
				case Skills.DEFENSIVE_MODE:
					skillsDist[Skill.DEFENSE.id()] = 3;
					break;
			}
			skillsDist[Skill.HITS.id()] = 1;
			playerKiller.incExp(skillsDist, exp, true);
		}

		// If `killed` is an NPC, xp distribution is handled by Npc.handleXpDistribution()

		killer.setKillType(KillType.COMBAT);
		killed.killedBy(killer);
		if (killer.isPlayer()) {
			updateParty((Player)killer);
		}
	}

	public final void run() {
		//In RSC combat against an NPC, the tick delay is dependent on if the defending mob is a player.
		//If it is, then the tick delay is always 2 ticks.
		//If it isn't, then there is a 3 tick delay after the attacker's round, and a 1 tick delay after the defender's round.
		//In PvP combat in the Wilderness, each combat encounter is assigned 3-1 or 2-2 tick cycles. This seems to be based on PID, based on footage before Jagex implemented PID shuffling in 2016.
		//In duels, combat was *always* 2-2.

		int delayTicks = 0;
		Mob hitter, target = null;

		if (roundNumber++ % 2 == 0) {
			hitter = attackerMob;
			target = defenderMob;
			delayTicks = 3;
		} else {
			hitter = defenderMob;
			target = attackerMob;
			delayTicks = 1;
		}

		if (forceTwoTickRounds) delayTicks = 2;
		setDelayTicks(delayTicks);

		if (!combatCanContinue()) {
			hitter.setLastCombatState(CombatState.ERROR);
			target.setLastCombatState(CombatState.ERROR);
			resetCombat();
		} else {
			if (hitter.isPlayer() && target.isNpc() && hitter.getConfig().WANT_POISON_NPCS
				&& checkPoisonousWeapons(hitter) && target.getCurrentPoisonPower() < 10
				&& DataConversions.random(1, 50) == 1) {
				target.setPoisonDamage(60);
				target.startPoisonEvent();
				((Player) hitter).message("@gr3@You @gr2@have @gr1@poisioned @gr2@the " + ((Npc) target).getDef().name + "!");
			}

			if (hitter.isNpc() && ((Npc)hitter).getBehavior().shouldRetreat(((Npc)hitter)) && target.getHitsMade() >= 3) {
				//Authentically, retreating enemies retreat on their turn but before they do damage.
				((Npc)hitter).getBehavior().retreat();
				return;
			}

			//if(hitter.isNpc() && target.isPlayer() || target.isNpc() && hitter.isPlayer()) {
			final int combatStyle = hitter.isPlayer() ? ((Player) hitter).getCombatStyle() : -1;
			final MeleeHitResult meleeHit;
			int damage;
			int attackerMaxHit;
			try {
				if (proofSession != null) {
					if (getWorld().getServer().getConfig().OPENRSC_CLASSIC_COMBAT_BASELINE
						|| getWorld().getServer().getConfig().OSRS_COMBAT_MELEE
						|| !hitter.isPlayer() || !target.isPlayer()) {
						throw new IllegalStateException("proof combat left classic player melee");
					}
					meleeHit = CombatFormula.doMeleeHit((Player) hitter, (Player) target,
						proofSession);
					damage = meleeHit.getDamage();
					attackerMaxHit = CombatFormula.calculateMeleeMaxHit(hitter, target);
				} else if (getWorld().getServer().getConfig().OSRS_COMBAT_MELEE) {
					meleeHit = OSRSCombatFormula.Melee.doMeleeHit(hitter, target);
					damage = meleeHit.getDamage();
					attackerMaxHit = OSRSCombatFormula.Melee.calculateMaxHit(hitter, target);
				} else {
					meleeHit = CombatFormula.doMeleeHit(hitter, target);
					damage = meleeHit.getDamage();
					attackerMaxHit = CombatFormula.calculateMeleeMaxHit(hitter, target);
				}

				if (isPvPCombat && hitter.isPlayer() && target.isPlayer()) {
					final Player playerHitter = (Player) hitter;
					final DuelJournalSession journal = playerHitter.getDuel().getJournalSession();
					if (journal != null && playerHitter.getDuel().isDueling()
						&& playerHitter.getDuel().getDuelRecipient() == target) {
						journal.recordMeleeSwing(playerHitter, combatStyle, meleeHit.isHit(), damage);
					}
				}

				if (isPvPCombat) {
					WildernessRules.markVoidDungeonPvp((Player) hitter, (Player) target);
				}
			} catch (final RuntimeException proofFailure) {
				if (proofSession == null) {
					throw proofFailure;
				}
				try {
					DuelProofService.failCombat(proofSession, proofFailure);
				} finally {
					resetCombat();
				}
				return;
			}

			final int reflectedDamage = target.isPlayer()
				? calculateRingOfRecoilDamage((Player) target, damage) : 0;
			if (proofSession != null) {
				final int targetHits = target.getLevel(Skill.HITS.id());
				final int hitterHits = hitter.getLevel(Skill.HITS.id());
				final boolean directLethal = targetHits > 0 && damage >= targetHits;
				final boolean recoilLethal = !directLethal && reflectedDamage > 0
					&& hitterHits > 0 && reflectedDamage >= hitterHits;
				if (directLethal || recoilLethal) {
					final Player winner = (Player) (directLethal ? hitter : target);
					final Player loser = (Player) (directLethal ? target : hitter);
					final int terminalCause = directLethal
						? DuelProofSpec.TERMINAL_CAUSE_DIRECT_MELEE
						: DuelProofSpec.TERMINAL_CAUSE_RECOIL;
					final DuelJournalSession journal = winner.getDuel().getJournalSession();
					if (!DuelProofService.captureTerminal(proofSession, winner, loser,
						terminalCause, journal)) {
						resetCombat();
						return;
					}
				}
			}

			inflictDamage(hitter, target, damage, attackerMaxHit);
			// A direct lethal hit has already completed and reset the duel. Never let
			// the old stack apply recoil to the winner after settlement.
			if (target.getLevel(Skill.HITS.id()) <= 0) {
				return;
			}
			if (target.isPlayer() && reflectedDamage > 0
				&& ((Player) target).getCarriedItems().getEquipment()
					.hasEquipped(ItemId.RING_OF_RECOIL.id())) {
				try {
					applyRingOfRecoil((Player) target, hitter, reflectedDamage);
				} catch (final RuntimeException recoilFailure) {
					if (proofSession == null) {
						throw recoilFailure;
					}
					try {
						DuelProofService.failCombat(proofSession, recoilFailure);
					} finally {
						resetCombat();
					}
				}
			}
		}
	}

	private int calculateRingOfRecoilDamage(final Player reflector, final int damage) {
		if (reflector == null || damage <= 0
			|| !reflector.getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_RECOIL.id())) {
			return 0;
		}
		final int used = reflector.getCache().hasKey("ringofrecoil")
			? reflector.getCache().getInt("ringofrecoil") : 0;
		final int remaining = Math.max(0,
			getWorld().getServer().getConfig().RING_OF_RECOIL_LIMIT - used);
		return Math.min(damage / 10 + 1, remaining);
	}

	private void applyRingOfRecoil(final Player reflector, final Mob hitter,
								   final int reflectedDamage) {
		if (reflectedDamage <= 0) {
			return;
		}
		final boolean freshRing = !reflector.getCache().hasKey("ringofrecoil");
		final int used = freshRing ? 0 : reflector.getCache().getInt("ringofrecoil");
		final int remaining = Math.max(0,
			getWorld().getServer().getConfig().RING_OF_RECOIL_LIMIT - used);
		final int appliedDamage = Math.min(reflectedDamage, remaining);
		if (appliedDamage <= 0) {
			return;
		}
		if (appliedDamage >= remaining) {
			shatterEquippedRecoil(reflector);
			reflector.getCache().remove("ringofrecoil");
		} else {
			reflector.getCache().set("ringofrecoil", used + appliedDamage);
			if (freshRing) {
				reflector.message("You start a new ring of recoil");
			}
		}
		inflictDamage(reflector, hitter, appliedDamage);
	}

	/** Removes the worn ring itself, even on classic worlds where worn items live in inventory. */
	private void shatterEquippedRecoil(final Player reflector) {
		Item wornRing = null;
		if (reflector.getConfig().WANT_EQUIPMENT_TAB) {
			wornRing = reflector.getCarriedItems().getEquipment().getRingItem();
			if (wornRing != null && wornRing.getCatalogId() == ItemId.RING_OF_RECOIL.id()
				&& reflector.getCarriedItems().getEquipment().remove(wornRing, 1) != -1) {
				reflector.message("Your Ring of recoil shatters");
				return;
			}
		} else {
			final java.util.List<Item> inventory = reflector.getCarriedItems()
				.getInventory().getItems();
			synchronized (inventory) {
				for (final Item item : inventory) {
					if (item != null && item.getCatalogId() == ItemId.RING_OF_RECOIL.id()
						&& item.isWielded()) {
						wornRing = item;
						break;
					}
				}
				if (wornRing != null
					&& reflector.getCarriedItems().getInventory().remove(wornRing, true) != -1) {
					reflector.message("Your Ring of recoil shatters");
					return;
				}
			}
		}
		throw new IllegalStateException("equipped ring of recoil could not be shattered");
	}

	private boolean checkPoisonousWeapons(Mob hitter) {
		for (int itemId : poisonedWeapons) {
			if (((Player) hitter).getCarriedItems().getEquipment().hasEquipped(itemId)) {
				return true;
			}
		}
		return false;
	}

	private void inflictDamage(final Mob hitter, final Mob target, int damage) {
		inflictDamage(hitter, target, damage, 0);
	}

	private void inflictDamage(final Mob hitter, final Mob target, int damage, int attackerMaxHit) {
		hitter.incHitsMade();

		if (target.isPlayer()) {
			Player targetPlayer = (Player) target;
			// side effects that may occur during combat (like poison) are regardless protect
			hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatSideEffectScript(hitter, target);

			if (hitter.isNpc()) {
				// If the hitter is an NPC, we want to check and execute their combat script
				// However if the player has the paralyze prayer on, we just want to return
				// so that the NPC is stopped from damaging the player.
				if (targetPlayer.getPrayers().isPrayerActivated(Prayers.PARALYZE_MONSTER)
					&& !(hitter instanceof Npc
						&& hitter.getWorld().getVoidArena().isDmKingChallengeNpc((Npc) hitter))) {
					return;
				} else {
					hitter.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
				}
			}
		}

		// Reduce targets hits by supplied damage amount.
		int lastHits = target.getLevel(Skill.HITS.id());
		target.getSkills().subtractLevel(Skill.HITS.id(), damage, false);
		target.getUpdateFlags().setDamage(new Damage(target, damage).withHitFeedback(hitter, attackerMaxHit));
		if (target.isNpc() && hitter.isPlayer()) {
			Npc n = (Npc) target;
			Player player = ((Player) hitter);
			if (!n.getWorld().getVoidArena().shouldSuppressDmKingNpcXp(n)
				&& !n.shouldSuppressDefaultDeathRewards()) {
				damage = Math.min(damage, lastHits);
				n.addCombatDamage(player, damage);
				n.awardMeleeHitExperience(player, damage);
			}
		}

		// Update players sound and party.
		if (target.isPlayer()) {
			sendSound((Player)target, hitter, damage > 0);
			updateParty((Player)target);
		}
		if (hitter.isPlayer()) {
			sendSound((Player)hitter, target, damage > 0);
			updateParty((Player)hitter);
		}

		if (target.getSkills().getLevel(Skill.HITS.id()) > 0) {

			// NPCs can run special combat scripts.
			// Custom: Ring of Life execution
			boolean ringOfLifeScript = false;
			if (target.isPlayer()) {
				Player player = (Player)target;
				ringOfLifeScript = !player.getDuel().isDuelActive() && player.checkRingOfLife(hitter);
			}
			if (target.isNpc() || ringOfLifeScript) {
				target.getWorld().getServer().getCombatScriptLoader().checkAndExecuteCombatScript(hitter, target);
			}
		}

		// Mob has <= 0 hits.
		else {
			onDeath(target, hitter);
		}
	}

	// Players in combat with an NPC will receive unique NPC
	// sounds dependent on npc type. Against Player is always combat2
	private void sendSound(Player player, Mob mob, boolean damaged) {
		String combatSound;
		boolean isNpc = mob.isNpc();
		boolean isPlayer = mob.isPlayer();
		if (isPlayer || DataConversions.inArray(Constants.ARMOR_NPCS, ((Npc)mob).getID())) {
			combatSound = damaged ? "combat2b" : "combat2a";
		} else if (isNpc && DataConversions.inArray(Constants.UNDEAD_NPCS, ((Npc)mob).getID())) {
			combatSound = damaged ? "combat3b" : "combat3a";
		} else {
			combatSound = damaged ? "combat1b" : "combat1a";
		}

		ActionSender.sendSound(player, combatSound);
	}

	private void updateParty(Player player) {
		if (getWorld().getServer().getConfig().WANT_PARTIES) {
			if(player.getParty() != null){
				player.getParty().sendParty();
			}
		}
	}

	public void resetCombat() {
		if (running) {
			if (defenderMob != null) {
				if (defenderMob.isPlayer()) {
					Player player = (Player) defenderMob;
					player.resetAll();
				}

				//defenderMob.setBusy(false);
				defenderMob.setOpponent(null);
				defenderMob.setCombatEvent(null);
				defenderMob.setHitsMade(0);
				defenderMob.setSprite(4);
				defenderMob.setCombatTimer();
				defenderMob.face(defenderMob.getX(), defenderMob.getY() - 1);
			}
			if (attackerMob != null) {
				if (attackerMob.isPlayer()) {
					Player player = (Player) attackerMob;
					player.resetAll();
				}

				//attackerMob.setBusy(false);
				attackerMob.setOpponent(null);
				attackerMob.setCombatEvent(null);
				attackerMob.setHitsMade(0);
				attackerMob.setSprite(4);
				attackerMob.setCombatTimer();
				attackerMob.face(attackerMob.getX(), attackerMob.getY() - 1);
			}
		} else {
			// combat event was reset while combat event wasn't running.
			// possible race condition; we will want to clean most things up if this happens.
			if (defenderMob != null) {
				defenderMob.setOpponent(null);
				defenderMob.setCombatEvent(null);
				defenderMob.setHitsMade(0);
				if (defenderMob.getSprite() > 7) {
					defenderMob.setSprite(4);
					defenderMob.face(defenderMob.getX(), defenderMob.getY() - 1);
				}
			}
			if (attackerMob != null) {
				attackerMob.setOpponent(null);
				attackerMob.setCombatEvent(null);
				attackerMob.setHitsMade(0);
				if (attackerMob.getSprite() > 7) {
					attackerMob.setSprite(4);
					attackerMob.face(attackerMob.getX(), attackerMob.getY() - 1);
				}
			}
		}
		stop();
	}

	private boolean combatCanContinue() {
		boolean removed = attackerMob.isRemoved() || defenderMob.isRemoved();
		boolean nextToVictim = attackerMob.getLocation().equals(defenderMob.getLocation());
		boolean sameInstance = attackerMob.sharesInstanceWith(defenderMob);
		if (defenderMob.isNpc() && attackerMob.isNpc()) {
			return !removed && sameInstance && nextToVictim && running;
		}
		boolean bothLoggedIn = (attackerMob.isPlayer() && ((Player) attackerMob).loggedIn())
			|| (defenderMob.isPlayer() && ((Player) defenderMob).loggedIn());
		boolean respawning = (attackerMob.isNpc() && ((Npc)attackerMob).isRespawning())
			|| (defenderMob.isNpc() && ((Npc)defenderMob).isRespawning());
		return bothLoggedIn && !removed && !respawning && sameInstance && nextToVictim && running;
	}

	public Mob getAttacker() {
		return attackerMob;
	}

	public Mob getVictim() {
		return defenderMob;
	}

}
