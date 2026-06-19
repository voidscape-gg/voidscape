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
	private static final int KNIGHT_STRENGTH_PHASE_TWO = 124;
	private static final int KNIGHT_STRENGTH_PHASE_THREE = 132;
	private static final int KNIGHT_SWORDFISH = 22;
	private static final int SWORDFISH_HEAL = 14;
	private static final int EAT_AT_HITS = 42;
	private static final int FIRE_BLAST_MAX = 12;
	private static final int FIRE_BLAST_DELAY = 6;
	private static final int FIRE_BLAST_PHASE_TWO_MAX = 14;
	private static final int FIRE_BLAST_PHASE_THREE_MAX = 16;
	private static final int PRAYER_SWITCH_DELAY = 3;
	private static final int MAGIC_DISTANCE = 8;
	private static final int FINAL_PHASE_MAGIC_DISTANCE = 10;
	private static final int KNIGHT_MAGIC = 99;
	private static final int KNIGHT_MAGIC_BONUS = 6;
	private static final int PHASE_TWO_HITS = 66;
	private static final int PHASE_THREE_HITS = 33;
	private static final int VOID_SIPHON_PHASE_TWO_MIN_DELAY = 14;
	private static final int VOID_SIPHON_PHASE_TWO_MAX_DELAY = 18;
	private static final int VOID_SIPHON_PHASE_THREE_MIN_DELAY = 10;
	private static final int VOID_SIPHON_PHASE_THREE_MAX_DELAY = 14;

	private static final int STYLE_NONE = -1;
	private static final int STYLE_MELEE = 0;
	private static final int STYLE_RANGED = 1;
	private static final int STYLE_MAGIC = 2;

	private static final String INITIALIZED_ATTRIBUTE = "void_knight_boss_initialized";
	private static final String SWORDFISH_ATTRIBUTE = "void_knight_boss_swordfish";
	private static final String OBSERVED_HITS_ATTRIBUTE = "void_knight_boss_observed_hits";
	private static final String PRAYER_ATTRIBUTE = "void_knight_boss_prayer";
	private static final String PHASE_ATTRIBUTE = "void_knight_boss_phase";
	private static final String NEXT_PRAYER_TICK_ATTRIBUTE = "void_knight_boss_next_prayer_tick";
	private static final String NEXT_CAST_TICK_ATTRIBUTE = "void_knight_boss_next_cast_tick";
	private static final String NEXT_SIPHON_TICK_ATTRIBUTE = "void_knight_boss_next_siphon_tick";

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
		knight.setAttribute(PHASE_ATTRIBUTE, 1);
		knight.setAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, tick + PRAYER_SWITCH_DELAY);
		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, tick + FIRE_BLAST_DELAY);
		knight.setAttribute(NEXT_SIPHON_TICK_ATTRIBUTE, tick + VOID_SIPHON_PHASE_TWO_MAX_DELAY);
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
		int phase = updatePhase(knight, player);
		handleIncomingDamage(knight, player, phase);
		eatIfNeeded(knight, player);
		updatePrayer(knight, player, phase);
		handleVoidSiphonIfReady(knight, player, phase);
		if (allowCast) {
			castIfReady(knight, player, phase);
		}
		knight.setAttribute(OBSERVED_HITS_ATTRIBUTE, knight.getSkills().getLevel(Skill.HITS.id()));
	}

	public static int currentPhase(Npc knight) {
		if (!isBossKnight(knight)) {
			return 1;
		}
		int hits = knight.getSkills().getLevel(Skill.HITS.id());
		int phase;
		if (hits <= PHASE_THREE_HITS) {
			phase = 3;
		} else if (hits <= PHASE_TWO_HITS) {
			phase = 2;
		} else {
			phase = 1;
		}
		return Math.max(phase, knight.getAttribute(PHASE_ATTRIBUTE, phase));
	}

	private static int updatePhase(Npc knight, Player player) {
		int computedPhase = currentPhase(knight);
		int previousPhase = knight.getAttribute(PHASE_ATTRIBUTE, 1);
		int phase = Math.max(previousPhase, computedPhase);
		if (phase == previousPhase) {
			return phase;
		}

		long tick = knight.getWorld().getServer().getCurrentTick();
		knight.setAttribute(PHASE_ATTRIBUTE, phase);
		knight.setAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, tick + 1);
		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, tick + 1);
		knight.setAttribute(NEXT_SIPHON_TICK_ATTRIBUTE, tick + 3);
		if (phase == 2) {
			knight.getSkills().setLevel(Skill.STRENGTH.id(), KNIGHT_STRENGTH_PHASE_TWO, false);
			knight.getUpdateFlags().setChatMessage(new ChatMessage(knight,
				"The void starts answering me.", player));
		} else {
			knight.getSkills().setLevel(Skill.STRENGTH.id(), KNIGHT_STRENGTH_PHASE_THREE, false);
			knight.getUpdateFlags().setChatMessage(new ChatMessage(knight,
				"No more measured blows.", player));
		}
		return phase;
	}

	private static void handleIncomingDamage(Npc knight, Player player, int phase) {
		int currentHits = knight.getSkills().getLevel(Skill.HITS.id());
		int observedHits = knight.getAttribute(OBSERVED_HITS_ATTRIBUTE, currentHits);
		int lost = observedHits - currentHits;
		if (lost <= 0 || currentHits <= 0) {
			return;
		}
		int style = inferPlayerStyle(player);
		if (knight.getAttribute(PRAYER_ATTRIBUTE, STYLE_NONE) != style) {
			return;
		}

		int restored = style == STYLE_MELEE
			? Math.max(1, lost / 4)
			: Math.max(1, (lost * 2) / 5);
		if (phase >= 3 && style != STYLE_MELEE) {
			restored = Math.max(restored, lost / 2);
		}
		int newHits = Math.min(KNIGHT_HITS, currentHits + restored);
		knight.getSkills().setLevel(Skill.HITS.id(), newHits, false);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, "The void eats part of the strike.", player));
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

	private static void updatePrayer(Npc knight, Player player, int phase) {
		long tick = knight.getWorld().getServer().getCurrentTick();
		long nextPrayerTick = knight.getAttribute(NEXT_PRAYER_TICK_ATTRIBUTE, 0L);
		if (tick < nextPrayerTick) {
			return;
		}

		int style = inferPlayerStyle(player);
		if (style == STYLE_MELEE && phase < 3) {
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
			prayer = "Void guard.";
		}
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, prayer, player));
	}

	private static void handleVoidSiphonIfReady(Npc knight, Player player, int phase) {
		if (phase < 2) {
			return;
		}

		long tick = knight.getWorld().getServer().getCurrentTick();
		long nextSiphonTick = knight.getAttribute(NEXT_SIPHON_TICK_ATTRIBUTE, 0L);
		if (tick < nextSiphonTick || !canCastFromHere(knight, player, phase)) {
			return;
		}

		int delay = phase >= 3
			? DataConversions.random(VOID_SIPHON_PHASE_THREE_MIN_DELAY, VOID_SIPHON_PHASE_THREE_MAX_DELAY)
			: DataConversions.random(VOID_SIPHON_PHASE_TWO_MIN_DELAY, VOID_SIPHON_PHASE_TWO_MAX_DELAY);
		knight.setAttribute(NEXT_SIPHON_TICK_ATTRIBUTE, tick + delay);
		knight.face(player);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, "Void siphon.", player));
		knight.getUpdateFlags().setProjectile(new Projectile(knight, player, 1));
		ActionSender.sendSound(player, "spellok");

		int prayer = player.getSkills().getLevel(Skill.PRAYER.id());
		int drain = Math.min(prayer, DataConversions.random(phase >= 3 ? 4 : 2, phase >= 3 ? 7 : 5));
		if (drain > 0) {
			player.getSkills().setLevel(Skill.PRAYER.id(), prayer - drain, true);
			int currentHits = knight.getSkills().getLevel(Skill.HITS.id());
			int healed = Math.min(KNIGHT_HITS, currentHits + Math.max(2, drain / 2 + 1));
			knight.getSkills().setLevel(Skill.HITS.id(), healed, false);
			player.message("@mag@The Void Knight siphons your prayer.");
		} else {
			player.message("@mag@The Void Knight searches for prayer to siphon.");
		}

		int damage = DataConversions.random(0, phase >= 3 ? 6 : 4);
		if (damage > 0) {
			applyPlayerDamage(knight, player, damage);
		}
	}

	private static void castIfReady(Npc knight, Player player, int phase) {
		long tick = knight.getWorld().getServer().getCurrentTick();
		long nextCastTick = knight.getAttribute(NEXT_CAST_TICK_ATTRIBUTE, 0L);
		if (tick < nextCastTick || !canCastFromHere(knight, player, phase)) {
			return;
		}

		knight.setAttribute(NEXT_CAST_TICK_ATTRIBUTE, tick + fireBlastDelay(phase));
		knight.face(player);
		knight.getUpdateFlags().setChatMessage(new ChatMessage(knight, phase >= 3 ? "Void flame!" : "Fire blast!", player));
		knight.getUpdateFlags().setProjectile(new Projectile(knight, player, 1));
		ActionSender.sendSound(player, "spellok");
		int damage = magicHits(player) ? DataConversions.random(0, fireBlastMax(phase)) : 0;
		applyPlayerDamage(knight, player, damage);
	}

	public static void applyVoidDamage(Npc knight, Player player, int damage) {
		if (!isBossKnight(knight) || player == null) {
			return;
		}
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

	private static int fireBlastDelay(int phase) {
		if (phase >= 3) {
			return 4;
		}
		if (phase == 2) {
			return 5;
		}
		return FIRE_BLAST_DELAY;
	}

	private static int fireBlastMax(int phase) {
		if (phase >= 3) {
			return FIRE_BLAST_PHASE_THREE_MAX;
		}
		if (phase == 2) {
			return FIRE_BLAST_PHASE_TWO_MAX;
		}
		return FIRE_BLAST_MAX;
	}

	private static boolean canCastFromHere(Npc knight, Player player, int phase) {
		int maxDistance = phase >= 3 ? FINAL_PHASE_MAGIC_DISTANCE : MAGIC_DISTANCE;
		return distanceToPlayer(knight, player) <= maxDistance
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
