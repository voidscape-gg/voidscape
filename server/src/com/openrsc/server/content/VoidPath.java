package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.entity.player.Player;

public final class VoidPath {
	public static final String CACHE_KEY = "void_path";
	public static final int VOID_ISLAND_X = 24;
	public static final int VOID_ISLAND_Y = 26;
	private static final int LEGACY_VOID_ISLAND_MIN_X = 734;
	private static final int LEGACY_VOID_ISLAND_MAX_X = 750;
	private static final int LEGACY_VOID_ISLAND_MIN_Y = 880;
	private static final int LEGACY_VOID_ISLAND_MAX_Y = 894;

	public static final int NONE = 0;
	public static final int WARRIOR = 1;
	public static final int FORAGER = 2;
	public static final int ARCANIST = 3;

	private VoidPath() {
	}

	public static boolean hasChosen(Player player) {
		return get(player) != NONE;
	}

	public static int get(Player player) {
		if (player == null || !player.getCache().hasKey(CACHE_KEY)) {
			return NONE;
		}

		int path = player.getCache().getInt(CACHE_KEY);
		return path >= WARRIOR && path <= ARCANIST ? path : NONE;
	}

	public static void choose(Player player, int path) {
		if (path < WARRIOR || path > ARCANIST) {
			return;
		}
		player.getCache().set(CACHE_KEY, path);
	}

	public static boolean inLegacyVoidIsland(Player player) {
		return player != null
			&& player.getX() >= LEGACY_VOID_ISLAND_MIN_X
			&& player.getX() <= LEGACY_VOID_ISLAND_MAX_X
			&& player.getY() >= LEGACY_VOID_ISLAND_MIN_Y
			&& player.getY() <= LEGACY_VOID_ISLAND_MAX_Y;
	}

	public static boolean shouldRouteToVoidIsland(Player player) {
		return player != null
			&& !hasChosen(player)
			&& (inLegacyVoidIsland(player)
				|| player.getLocation().inTutorialLanding()
				|| player.getLocation().onTutorialIsland());
	}

	public static boolean boostsSkill(Player player, int skill) {
		switch (get(player)) {
			case WARRIOR:
				return matches(skill, Skill.ATTACK, Skill.DEFENSE, Skill.STRENGTH);
			case FORAGER:
				return matches(skill, Skill.FISHING, Skill.COOKING, Skill.MINING);
			case ARCANIST:
				return matches(skill, Skill.RANGED, Skill.MAGIC, Skill.GOODMAGIC, Skill.EVILMAGIC);
			default:
				return false;
		}
	}

	public static String name(int path) {
		switch (path) {
			case WARRIOR:
				return "Warrior's Path";
			case FORAGER:
				return "Forager's Path";
			case ARCANIST:
				return "Arcanist's Path";
			default:
				return "No Path";
		}
	}

	private static boolean matches(int skill, Skill... boostedSkills) {
		for (Skill boostedSkill : boostedSkills) {
			if (boostedSkill.id() == skill) {
				return true;
			}
		}
		return false;
	}
}
