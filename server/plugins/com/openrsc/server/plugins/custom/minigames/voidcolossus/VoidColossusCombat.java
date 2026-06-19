package com.openrsc.server.plugins.custom.minigames.voidcolossus;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.SpellDamages;
import com.openrsc.server.constants.Spells;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.handler.GameEventHandler;
import com.openrsc.server.event.rsc.impl.ObjectRemover;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.event.rsc.impl.projectile.ProjectileEvent;
import com.openrsc.server.event.rsc.impl.projectile.RangeEvent;
import com.openrsc.server.event.rsc.impl.projectile.ThrowingEvent;
import com.openrsc.server.external.SpellDef;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.EntityType;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.KillType;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.SpellLocTrigger;
import com.openrsc.server.util.rsc.CollisionFlag;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;

import java.util.Locale;

/**
 * Combat bridge for the hybrid Void Colossus.
 *
 * The boss body is a sprite NPC, while the scenery object provides the rift/base and a large click
 * target. This plugin blocks melee and translates object clicks/spells back into the normal
 * ranged/magic combat paths.
 */
public final class VoidColossusCombat implements AttackNpcTrigger, OpLocTrigger, SpellLocTrigger, KillNpcTrigger {

	private static final int CRACKER_COUNT = 10;
	private static final int CRACKER_DESPAWN_TICKS = 600; // ~6.4 min at 640ms ticks
	private static final int SCATTER_RADIUS = 11;         // keep crackers on the plaza floor

	private static final String MELEE_BLOCK_MESSAGE =
		"The Void Colossus is bound beyond melee reach. Use ranged or magic.";

	private static boolean isColossus(Npc npc) {
		return npc != null && npc.getID() == NpcId.VOID_COLOSSUS.id();
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc affectedmob) {
		return isColossus(affectedmob);
	}

	@Override
	public void onAttackNpc(Player player, Npc affectedmob) {
		if (!isColossus(affectedmob)) {
			return;
		}
		if (player.getRangeEquip() > 0 || player.getThrowingEquip() > 0) {
			startRangedAttack(player, affectedmob);
			return;
		}
		player.message(MELEE_BLOCK_MESSAGE);
		player.resetPath();
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return VoidColossusArena.isColossusObject(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!VoidColossusArena.isColossusObject(obj)) {
			return;
		}
		if (!"attack".equalsIgnoreCase(command)) {
			player.message("The Void Colossus towers over the shattered plaza.");
			return;
		}
		Npc boss = VoidColossusArena.bossFor(player);
		if (!isLiveBoss(boss)) {
			player.message("The void is still reforming.");
			return;
		}
		startRangedAttack(player, boss);
	}

	@Override
	public boolean blockSpellLoc(Player player, GameObject gameObject, SpellDef spell) {
		return VoidColossusArena.isColossusObject(gameObject);
	}

	@Override
	public void onSpellLoc(Player player, GameObject gameObject, SpellDef spell) {
		if (!VoidColossusArena.isColossusObject(gameObject)) {
			return;
		}
		Npc boss = VoidColossusArena.bossFor(player);
		if (!isLiveBoss(boss)) {
			player.message("The void is still reforming.");
			return;
		}
		if (spell == null || spell.getSpellType() != 2) {
			player.message("The Colossus only answers combat magic.");
			return;
		}

		Spells spellEnum = spellEnumFor(spell);
		if (spellEnum == null || !isDirectDamageSpell(spellEnum)) {
			player.message("That spell washes across the Colossus without effect.");
			return;
		}
		if (!player.withinRange(gameObject, player.getConfig().SPELL_RANGE_DISTANCE)
			|| clearObjectEdgePoint(player, gameObject) == null) {
			player.playerServerMessage(MessageType.QUEST, "I can't get a clear shot from here");
			player.resetPath();
			return;
		}
		if (!checkSpecialSpellRequirements(player, spell, spellEnum)) {
			return;
		}
		if (!SpellHandler.checkAndRemoveRunes(player, spell)) {
			return;
		}
		afterSpecialSpellRunes(player, spell, spellEnum);

		int damage = calculateSpellDamage(player, boss, spellEnum);
		if (damage < 0) {
			player.message("That spell washes across the Colossus without effect.");
			return;
		}
		if (isGodSpell(spellEnum)) {
			voidClawObject(player.getWorld(), boss);
		}
		player.resetAllExceptDueling();
		player.setKillType(KillType.MAGIC);
		player.getWorld().getServer().getGameEventHandler().add(
			new ProjectileEvent(player.getWorld(), player, boss, damage, spellEnum == Spells.IBAN_BLAST ? 4 : 1, false));
		SpellHandler.finalizeSpell(player, spell, "", true);
	}

	private Point clearObjectEdgePoint(Player player, GameObject object) {
		Point[] bounds = object.getObjectBoundary();
		Point low = bounds[0];
		Point high = bounds[1];
		int minX = Math.min(low.getX(), high.getX());
		int maxX = Math.max(low.getX(), high.getX());
		int minY = Math.min(low.getY(), high.getY());
		int maxY = Math.max(low.getY(), high.getY());
		Point from = player.getLocation();
		Point best = null;
		int bestDistance = Integer.MAX_VALUE;

		for (int x = minX - 1; x <= maxX + 1; x++) {
			best = nearestClearPoint(player, from, Point.location(x, minY - 1), best, bestDistance);
			if (best != null) bestDistance = from.getDistancePythagoras(best);
			best = nearestClearPoint(player, from, Point.location(x, maxY + 1), best, bestDistance);
			if (best != null) bestDistance = from.getDistancePythagoras(best);
		}
		for (int y = minY; y <= maxY; y++) {
			best = nearestClearPoint(player, from, Point.location(minX - 1, y), best, bestDistance);
			if (best != null) bestDistance = from.getDistancePythagoras(best);
			best = nearestClearPoint(player, from, Point.location(maxX + 1, y), best, bestDistance);
			if (best != null) bestDistance = from.getDistancePythagoras(best);
		}
		return best;
	}

	private Point nearestClearPoint(Player player, Point from, Point candidate, Point best, int bestDistance) {
		if (candidate.getX() < 0 || candidate.getY() < 0) {
			return best;
		}
		int distance = from.getDistancePythagoras(candidate);
		if (distance >= bestDistance) {
			return best;
		}
		if (!PathValidation.checkPath(player.getWorld(), from, candidate)) {
			return best;
		}
		return candidate;
	}

	private boolean isLiveBoss(Npc boss) {
		return isColossus(boss) && !boss.isRemoved() && boss.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private void startRangedAttack(Player player, Npc boss) {
		if (!isLiveBoss(boss)) {
			player.message("The void is still reforming.");
			return;
		}
		if (player.inCombat()) {
			player.message("You are already busy fighting!");
			return;
		}
		if (!player.checkAttack(boss, true)) {
			return;
		}

		if (player.getRangeEquip() > 0) {
			startOrRetargetRange(player, boss);
		} else if (player.getThrowingEquip() > 0) {
			startOrRetargetThrowing(player, boss);
		} else {
			player.message(MELEE_BLOCK_MESSAGE);
		}
	}

	private void startOrRetargetRange(Player player, Npc boss) {
		player.resetPath();
		GameEventHandler gameEventHandler = player.getWorld().getServer().getGameEventHandler();
		RangeEvent rangeEvent = null;
		for (GameTickEvent event : gameEventHandler.getPlayerEvents(player)) {
			if (event instanceof RangeEvent) {
				rangeEvent = (RangeEvent) event;
				break;
			}
		}
		if (rangeEvent != null) {
			if (!rangeEvent.getTarget().equals(boss)) {
				rangeEvent.reTarget(boss);
			}
			rangeEvent.restart();
			player.setRangeEvent(rangeEvent);
			return;
		}
		rangeEvent = new RangeEvent(player.getWorld(), player, 1, boss);
		player.setRangeEvent(rangeEvent);
		gameEventHandler.add(rangeEvent);
	}

	private void startOrRetargetThrowing(Player player, Npc boss) {
		player.resetPath();
		GameEventHandler gameEventHandler = player.getWorld().getServer().getGameEventHandler();
		ThrowingEvent throwingEvent = null;
		for (GameTickEvent event : gameEventHandler.getPlayerEvents(player)) {
			if (event instanceof ThrowingEvent) {
				throwingEvent = (ThrowingEvent) event;
				break;
			}
		}
		if (throwingEvent != null) {
			if (!throwingEvent.getTarget().equals(boss)) {
				throwingEvent.reTarget(boss);
			}
			throwingEvent.restart();
			player.setThrowingEvent(throwingEvent);
			return;
		}
		throwingEvent = new ThrowingEvent(player.getWorld(), player, 1, boss);
		player.setThrowingEvent(throwingEvent);
		gameEventHandler.add(throwingEvent);
	}

	private Spells spellEnumFor(SpellDef spell) {
		String normalized = spell.getName()
			.toUpperCase(Locale.ROOT)
			.replace(' ', '_')
			.replace('-', '_');
		for (Spells candidate : Spells.values()) {
			if (candidate.name().equals(normalized)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean isDirectDamageSpell(Spells spell) {
		switch (spell) {
			case CHILL_BOLT:
			case SHOCK_BOLT:
			case ELEMENTAL_BOLT:
			case WIND_BOLT_R:
			case WIND_STRIKE:
			case WATER_STRIKE:
			case EARTH_STRIKE:
			case FIRE_STRIKE:
			case WIND_BOLT:
			case WATER_BOLT:
			case EARTH_BOLT:
			case FIRE_BOLT:
			case WIND_BLAST:
			case WATER_BLAST:
			case EARTH_BLAST:
			case FIRE_BLAST:
			case WIND_WAVE:
			case WATER_WAVE:
			case EARTH_WAVE:
			case FIRE_WAVE:
			case IBAN_BLAST:
			case CLAWS_OF_GUTHIX:
			case SARADOMIN_STRIKE:
			case FLAMES_OF_ZAMORAK:
				return true;
			default:
				return false;
		}
	}

	private boolean checkSpecialSpellRequirements(Player player, SpellDef spell, Spells spellEnum) {
		if (spellEnum == Spells.IBAN_BLAST) {
			if (player.getQuestStage(Quests.UNDERGROUND_PASS) != -1) {
				player.message("you need to complete underground pass quest to cast this spell");
				return false;
			}
			if (!player.getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_IBAN.id())) {
				player.message("you need the staff of iban to cast this spell");
				return false;
			}
			if (player.getCache().hasKey(spell.getName() + "_casts")
				&& player.getCache().getInt(spell.getName() + "_casts") < 1) {
				player.message("you need to recharge the staff of iban");
				player.message("at iban's temple");
				return false;
			}
			return true;
		}

		if (!isGodSpell(spellEnum)) {
			return true;
		}
		if (!player.getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_GUTHIX.id())
			&& spellEnum == Spells.CLAWS_OF_GUTHIX) {
			player.message("you must weild the staff of guthix to cast this spell");
			return false;
		}
		if (!player.getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_SARADOMIN.id())
			&& spellEnum == Spells.SARADOMIN_STRIKE) {
			player.message("you must weild the staff of saradomin to cast this spell");
			return false;
		}
		if (!player.getCarriedItems().getEquipment().hasEquipped(ItemId.STAFF_OF_ZAMORAK.id())
			&& spellEnum == Spells.FLAMES_OF_ZAMORAK) {
			player.message("you must weild the staff of zamorak to cast this spell");
			return false;
		}
		if (player.getConfig().WANT_OPENPK_POINTS
			&& player.getLocation().inWilderness()
			&& !player.getLocation().inMageArena()
			&& (player.getLocation().wildernessLevel() < player.getWorld().godSpellsStart
				|| player.getLocation().wildernessLevel() > player.getWorld().godSpellsMax)) {
			player.message("God spells can only be used in wild levels: " + player.getWorld().godSpellsStart
				+ " - " + player.getWorld().godSpellsMax);
			return false;
		}
		if (!player.getLocation().inMageArena()
			&& (!player.getCache().hasKey(spell.getName() + "_casts")
				|| player.getCache().getInt(spell.getName() + "_casts") < 100)) {
			player.message("this spell can only be used in the mage arena");
			player.message("You must learn this spell first, you need "
				+ (player.getCache().hasKey(spell.getName() + "_casts")
					? (100 - player.getCache().getInt(spell.getName() + "_casts")) : "100")
				+ " more casts in the mage arena");
			return false;
		}
		return true;
	}

	private void afterSpecialSpellRunes(Player player, SpellDef spell, Spells spellEnum) {
		if (spellEnum == Spells.IBAN_BLAST && player.getCache().hasKey(spell.getName() + "_casts")) {
			int casts = player.getCache().getInt(spell.getName() + "_casts");
			player.getCache().set(spell.getName() + "_casts", casts - 1);
		}
	}

	private int calculateSpellDamage(Player player, Npc boss, Spells spellEnum) {
		if (spellEnum == Spells.IBAN_BLAST) {
			return CombatFormula.calculateIbanSpellDamage(boss);
		}
		if (isGodSpell(spellEnum)) {
			return CombatFormula.calculateGodSpellDamage(player, boss);
		}

		double max = -1.0D;
		if (player.getConfig().DIVIDED_GOOD_EVIL) {
			max = player.getWorld().getServer().getConstants().getSpellDamages()
				.getSpellDamage(spellEnum, EntityType.NPC, SpellDamages.MagicType.GOODEVILMAGIC);
		}
		if (max < 0.0D) {
			max = player.getWorld().getServer().getConstants().getSpellDamages()
				.getSpellDamage(spellEnum, EntityType.NPC, SpellDamages.MagicType.MODERNMAGIC);
		}
		if (max < 0.0D) {
			max = player.getWorld().getServer().getConstants().getSpellDamages()
				.getSpellDamage(spellEnum, EntityType.NPC, SpellDamages.MagicType.F2PONLYMAGIC);
		}
		return max < 0.0D ? -1 : CombatFormula.calculateMagicDamage(max, boss);
	}

	private boolean isGodSpell(Spells spellEnum) {
		return spellEnum == Spells.CLAWS_OF_GUTHIX
			|| spellEnum == Spells.SARADOMIN_STRIKE
			|| spellEnum == Spells.FLAMES_OF_ZAMORAK;
	}

	private void voidClawObject(World world, Npc boss) {
		GameObject claws = new GameObject(world, boss.getLocation(), 1142, 0, 0);
		claws.setInstanceId(boss.getInstanceId());
		world.registerGameObject(claws);
		world.getServer().getGameEventHandler().add(new ObjectRemover(world, claws, 2));
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		// Return true so onKillNpc fires. The boss isn't in removeHandledInPlugin, so the engine's
		// default remove()/respawn still runs; this plugin only adds the cracker hoard.
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
			// Ownerless, phase-private loot.
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
