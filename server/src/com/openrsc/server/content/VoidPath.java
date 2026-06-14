package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

public final class VoidPath {
	public static final String CACHE_KEY = "void_path";
	public static final String STARTER_KIT_CACHE_KEY = "void_path_starter_kit";
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
	public static final int BOOST_LEVEL_CAP = 50;

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

	public static boolean grantStarterKit(Player player, int path) {
		if (player == null || path < WARRIOR || path > ARCANIST || player.getCache().hasKey(STARTER_KIT_CACHE_KEY)) {
			return false;
		}

		for (StarterItem item : starterKit(path)) {
			addStarterItem(player, item.itemId, item.amount);
		}
		player.getCache().set(STARTER_KIT_CACHE_KEY, path);
		ActionSender.sendInventory(player);
		return true;
	}

	public static boolean inLegacyVoidIsland(Player player) {
		return player != null && inLegacyVoidIsland(player.getX(), player.getY());
	}

	public static boolean inStarterIsland(int x, int y) {
		return Point.inVoidIsland(x, y) || inLegacyVoidIsland(x, y);
	}

	public static boolean blocksLeavingStarterIsland(Player player, int destinationX, int destinationY) {
		if (player == null
			|| hasChosen(player)
			|| !inStarterIsland(player.getX(), player.getY())
			|| inStarterIsland(destinationX, destinationY)) {
			return false;
		}

		player.resetPath();
		player.message("@mag@The Void Council prevents you from leaving until you choose a path.");
		return true;
	}

	private static boolean inLegacyVoidIsland(int x, int y) {
		return x >= LEGACY_VOID_ISLAND_MIN_X
			&& x <= LEGACY_VOID_ISLAND_MAX_X
			&& y >= LEGACY_VOID_ISLAND_MIN_Y
			&& y <= LEGACY_VOID_ISLAND_MAX_Y;
	}

	public static boolean shouldRouteToVoidIsland(Player player) {
		return player != null
			&& !hasChosen(player)
			&& (inLegacyVoidIsland(player)
				|| player.getLocation().inVoidIsland()
				|| VoidStarterIntro.inIntroArea(player)
				|| player.getLocation().inTutorialLanding()
				|| player.getLocation().onTutorialIsland());
	}

	public static boolean boostsSkill(Player player, int skill) {
		if (player == null || player.getSkills().getMaxStat(skill) >= BOOST_LEVEL_CAP) {
			return false;
		}

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

	public static String boostedSkillSummary(int path) {
		switch (path) {
			case WARRIOR:
				return "Attack, Defense, and Strength";
			case FORAGER:
				return "Fishing, Cooking, and Mining";
			case ARCANIST:
				return "Ranged and Magic";
			default:
				return "no skills";
		}
	}

	public static String boostLimitSummary() {
		return "2x XP until level " + BOOST_LEVEL_CAP;
	}

	public static String starterKitSummary(int path) {
		switch (path) {
			case WARRIOR:
				return "iron 2-handed sword, bronze plate body, bronze medium helmet, bronze legs, and food";
			case FORAGER:
				return "fishing gear, bait, a pickaxe, tinderbox, 100 coins, and food";
			case ARCANIST:
				return "shortbow, arrows, runes, wizard gear, and food";
			default:
				return "no starter kit";
		}
	}

	private static StarterItem[] starterKit(int path) {
		switch (path) {
			case WARRIOR:
				return new StarterItem[] {
					new StarterItem(ItemId.IRON_2_HANDED_SWORD.id()),
					new StarterItem(ItemId.COOKEDMEAT.id(), 10),
					new StarterItem(ItemId.BRONZE_PLATE_MAIL_BODY.id()),
					new StarterItem(ItemId.MEDIUM_BRONZE_HELMET.id()),
					new StarterItem(ItemId.BRONZE_PLATE_MAIL_LEGS.id())
				};
			case FORAGER:
				return new StarterItem[] {
					new StarterItem(ItemId.NET.id()),
					new StarterItem(ItemId.FISHING_ROD.id()),
					new StarterItem(ItemId.FISHING_BAIT.id(), 50),
					new StarterItem(ItemId.BRONZE_PICKAXE.id()),
					new StarterItem(ItemId.TINDERBOX.id()),
					new StarterItem(ItemId.COINS.id(), 100),
					new StarterItem(ItemId.COOKEDMEAT.id(), 2),
					new StarterItem(ItemId.BREAD.id(), 2)
				};
			case ARCANIST:
				return new StarterItem[] {
					new StarterItem(ItemId.SHORTBOW.id()),
					new StarterItem(ItemId.BRONZE_ARROWS.id(), 50),
					new StarterItem(ItemId.BLUE_WIZARDSHAT.id()),
					new StarterItem(ItemId.WIZARDS_ROBE.id()),
					new StarterItem(ItemId.AIR_RUNE.id(), 100),
					new StarterItem(ItemId.MIND_RUNE.id(), 50),
					new StarterItem(ItemId.FIRE_RUNE.id(), 25),
					new StarterItem(ItemId.BREAD.id(), 2)
				};
			default:
				return new StarterItem[0];
		}
	}

	private static void addStarterItem(Player player, int itemId, int amount) {
		ItemDefinition itemDef = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		if (itemDef != null && !itemDef.isStackable() && amount > 1) {
			for (int i = 0; i < amount; i++) {
				player.getCarriedItems().getInventory().add(new Item(itemId), false);
			}
			return;
		}
		player.getCarriedItems().getInventory().add(new Item(itemId, amount), false);
	}

	private static boolean matches(int skill, Skill... boostedSkills) {
		for (Skill boostedSkill : boostedSkills) {
			if (boostedSkill.id() == skill) {
				return true;
			}
		}
		return false;
	}

	private static final class StarterItem {
		private final int itemId;
		private final int amount;

		private StarterItem(int itemId) {
			this(itemId, 1);
		}

		private StarterItem(int itemId, int amount) {
			this.itemId = itemId;
			this.amount = amount;
		}
	}
}
