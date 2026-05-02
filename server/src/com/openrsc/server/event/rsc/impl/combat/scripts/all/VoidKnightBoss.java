package com.openrsc.server.event.rsc.impl.combat.scripts.all;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.impl.combat.scripts.CombatScript;
import com.openrsc.server.event.rsc.impl.combat.scripts.OnCombatStartScript;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.entity.update.Projectile;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;

public final class VoidKnightBoss implements OnCombatStartScript, CombatScript {
	private static final int KNIGHT_ID = NpcId.VOID_KNIGHT_ARENA.id();
	private static final int KNIGHT_HITS = 99;
	private static final int KNIGHT_STRENGTH_BOOSTED = 118;
	private static final int KNIGHT_SWORDFISH = 22;
	private static final int SWORDFISH_HEAL = 14;
	private static final int EAT_AT_HITS = 42;
	private static final int FIRE_BLAST_MAX = 12;
	private static final int FIRE_BLAST_DELAY = 4;
	private static final int PRAYER_SWITCH_DELAY = 3;
	private static final int MAGIC_DISTANCE = 8;
	private static final int KNIGHT_MAGIC = 99;
	private static final int KNIGHT_MAGIC_BONUS = 6;

	private static final int STYLE_NONE = -1;
	private static final int STYLE_MELEE = 0;
	private static final int STYLE_RANGED = 1;
	private static final int STYLE_MAGIC = 2;

	private static final String INITIALIZED_ATTRIBUTE = "void_knight_boss_initialized";
	private static final String SWORDFISH_ATTRIBUTE = "void_knight_boss_swordfish";
	private static final String OBSERVED_HITS_ATTRIBUTE = "void_knight_boss_observed_hits";
	private static final String PRAYER_ATTRIBUTE = "void_knight_boss_prayer";
	private static final String NEXT_PRAYER_TICK_ATTRIBUTE = "void_knight_boss_next_prayer_tick";
	private static final String NEXT_CAST_TICK_ATTRIBUTE = "void_knight_boss_next_cast_tick";

	@Override
	public void executeScript(Mob attacker, Mob victim) {
		Npc knight = getKnight(attacker, victim);
		Player player = getPlayer(attacker, victim);
		if (knight == null || player == null) {
			return;
		}
		pulse(knight, player, false);
	}

	@Override
	public boolean shouldExecute(Mob attacker, Mob victim) {
		return getKnight(attacker, victim) != null && getPlayer(attacker, victim) != null;
	}

	@Override
	public boolean shouldCombatStop() {
		return false;
	}

	public static void begin(Npc knight, Player player) {
		if (!isBossKnight(knight) || player == null || knight.getAttribute(INITIALIZED_ATTRIBUTE, false)) {
			return;
		}
		long tick = knight.getWorld().getServer().getCurrentTick();
		knight.setAttribute(INITIALIZED_ATTRIBUTE, true);
		knight.setAttribute(SWORDFISH_ATTRIBUTE, KNIGHT_SWORDFISH);
		knight.setAttribute(OBSERVED_HITS_ATTRIBUTE, knight.getSkills().getLevel(Skill.HITS.id()));
		knight.setAttribute(PRAYER_ATTRIBUTE, STYLE_NONE);
		knight.setAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, tick + PRAYER_SWITCH_DELAY);
		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, tick + FIRE_BLAST_DELAY);
		knight.getSkills().setLevel(Skill.STRENGTH.id(), KNIGHT_STRENGTH_BOOSTED, false);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight,
			"I drink the strength potion. Ultimate strength.", player));
	}

	public static void queueCastSoon(Npc knight, int ticks) {
		if (!isBossKnight(knight)) {
			return;
		}
		long currentTick = knight.getWorld().getServer().getCurrentTick();
		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, currentTick + Math.max(0, ticks));
	}

	public static void pulse(Npc knight, Player player, boolean allowCast) {
		if (!isBossKnight(knight) || player == null || knight.isRemoved() || player.isRemoved()) {
			return;
		}
		if (knight.getSkills().getLevel(Skill.HITS.id()) <= 0 || player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return;
		}

		begin(knight, player);
		handleIncomingDamage(knight, player);
		eatIfNeeded(knight, player);
		updatePrayer(knight, player);
		if (allowCast) {
			castIfReady(knight, player);
		}
		knight.setAttribute(OBSERVED_HITS_ATTRIBUTE, knight.getSkills().getLevel(Skill.HITS.id()));
	}

	private static void handleIncomingDamage(Npc knight, Player player) {
		int currentHits = knight.getSkills().getLevel(Skill.HITS.id());
		int observedHits = knight.getAttribute(OBSERVED_HITS_ATTRIBUTE, currentHits);
		int lost = observedHits - currentHits;
		if (lost <= 0 || currentHits <= 0) {
			return;
		}
		int style = inferPlayerStyle(player);
		if (style == STYLE_MELEE || knight.getAttribute(PRAYER_ATTRIBUTE, STYLE_NONE) != style) {
			return;
		}

		int restored = Math.max(1, (lost * 2) / 5);
		int newHits = Math.min(KNIGHT_HITS, currentHits + restored);
		knight.getSkills().setLevel(Skill.HITS.id(), newHits, false);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, "Protection prayer absorbs some of it.", player));
	}

	private static void eatIfNeeded(Npc knight, Player player) {
		int currentHits = knight.getSkills().getLevel(Skill.HITS.id());
		int swordfish = knight.getAttribute(SWORDFISH_ATTRIBUTE, KNIGHT_SWORDFISH);
		if (swordfish <= 0 || currentHits <= 0 || currentHits > EAT_AT_HITS || currentHits >= KNIGHT_HITS) {
			return;
		}
		knight.setAttribute(SWORDFISH_ATTRIBUTE, swordfish - 1);
		knight.getSkills().setLevel(Skill.HITS.id(), Math.min(KNIGHT_HITS, currentHits + SWORDFISH_HEAL), false);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, "I eat a swordfish.", player));
	}

	private static void updatePrayer(Npc knight, Player player) {
		long tick = knight.getWorld().getServer().getCurrentTick();
		long nextPrayerTick = knight.getAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, 0L);
		if (tick < nextPrayerTick) {
			return;
		}

		int style = inferPlayerStyle(player);
		if (style == STYLE_MELEE) {
			if (knight.getAttribute(PRAYER_ATTRIBUTE, STYLE_NONE) != STYLE_NONE) {
				knight.setAttribute(PRAYER_ATTRIBUTE, STYLE_NONE);
				knight.setAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, tick + PRAYER_SWITCH_DELAY);
			}
			return;
		}

		if (style == knight.getAttribute(PRAYER_ATTRIBUTE, STYLE_NONE)) {
			return;
		}

		knight.setAttribute(PRAYER_ATTRIBUTE, style);
		knight.setAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, tick + PRAYER_SWITCH_DELAY);
		String prayer;
		if (style == STYLE_RANGED) {
			prayer = "Protect from missiles.";
		} else if (style == STYLE_MAGIC) {
			prayer = "Protect from magic.";
		} else {
			prayer = "Paralyze monster.";
		}
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, prayer, player));
	}

	private static void castIfReady(Npc knight, Player player) {
		long tick = knight.getWorld().getServer().getCurrentTick();
		long nextCastTick = knight.getAttribute(NEXT_CAST_TICK_ATTRIBUTE, 0L);
		if (tick < nextCastTick || !canCastFromHere(knight, player)) {
			return;
		}

		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, tick + FIRE_BLAST_DELAY);
		knight.face(player);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, "Fire blast!", player));
		knight.getUpdateFlags().setProjectile(new Projectile(knight, player, 1));
		ActionSender.sendSound(player, "spellok");
		int damage = magicHits(player) ? DataConversions.random(0, FIRE_BLAST_MAX) : 0;
		applyPlayerDamage(knight, player, damage);
	}

	private static void applyPlayerDamage(Npc knight, Player player, int damage) {
		int currentHits = player.getSkills().getLevel(Skill.HITS.id());
		int actualDamage = Math.min(Math.max(damage, 0), currentHits);
		player.getSkills().subtractLevel(Skill.HITS.id(), actualDamage, true);
		player.getUpdateFlags().setDamage(new Damage(player, actualDamage));
		ActionSender.sendSound(player, actualDamage > 0 ? "combat2b" : "combat2a");
		if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			player.killedBy(knight);
		}
	}

	private static boolean canCastFromHere(Npc knight, Player player) {
		return distanceToPlayer(knight, player) <= MAGIC_DISTANCE
			&& PathValidation.checkPath(player.getWorld(), knight.getLocation(), player.getLocation());
	}

	private static int distanceToPlayer(Npc knight, Player player) {
		return Math.max(Math.abs(knight.getX() - player.getX()), Math.abs(knight.getY() - player.getY()));
	}

	private static boolean magicHits(Player player) {
		double accuracy = (KNIGHT_MAGIC + 8.0D) * (64.0D + KNIGHT_MAGIC_BONUS);
		int magicDefenceBonus = Math.max(-63, player.getMagicPoints());
		double defence = (player.getSkills().getLevel(Skill.MAGIC.id()) + 8.0D) * (64.0D + magicDefenceBonus);
		return rollAccuracy(accuracy, defence);
	}

	private static boolean rollAccuracy(double accuracy, double defence) {
		double hitChance;
		if (accuracy > defence) {
			hitChance = 1.0D - ((defence + 2.0D) / (2.0D * (accuracy + 1.0D)));
		} else {
			hitChance = accuracy / (2.0D * (defence + 1.0D));
		}
		return Math.random() <= hitChance;
	}

	private static int inferPlayerStyle(Player player) {
		long sinceCastMs = System.currentTimeMillis() - player.lastCast;
		if (sinceCastMs >= 0 && sinceCastMs < player.getConfig().GAME_TICK * 6L) {
			return STYLE_MAGIC;
		}
		if (player.getRangeEvent() != null || player.getThrowingEvent() != null
			|| player.getRangeEquip() >= 0 || player.getThrowingEquip() >= 0) {
			return STYLE_RANGED;
		}
		return STYLE_MELEE;
	}

	private static boolean isBossKnight(Npc knight) {
		return knight != null && knight.getID() == KNIGHT_ID;
	}

	private static Npc getKnight(Mob attacker, Mob victim) {
		if (attacker != null && attacker.isNpc() && isBossKnight((Npc) attacker)) {
			return (Npc) attacker;
		}
		if (victim != null && victim.isNpc() && isBossKnight((Npc) victim)) {
			return (Npc) victim;
		}
		return null;
	}

	private static Player getPlayer(Mob attacker, Mob victim) {
		if (attacker != null && attacker.isPlayer()) {
			return (Player) attacker;
		}
		if (victim != null && victim.isPlayer()) {
			return (Player) victim;
		}
		return null;
	}
}
