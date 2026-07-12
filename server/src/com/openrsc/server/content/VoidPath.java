package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.HashSet;
import java.util.Set;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidPath {
	public static final String CACHE_KEY = "void_path";
	public static final String STARTER_KIT_CACHE_KEY = "void_path_starter_kit";

	// Client contract: mudclient.isVoidscapePathMenu matches these option prefixes.
	public static final String OPTION_WARRIOR = "Warrior's Path - 2x XP: Attack, Defense, Strength to 50 + melee kit";
	public static final String OPTION_FORAGER = "Forager's Path - 2x XP: Fishing, Cooking, Mining to 50 + gathering kit";
	public static final String OPTION_ARCANIST = "Arcanist's Path - 2x XP: Ranged, Magic to 50 + arcane kit";
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

	/**
	 * Runs the full path choice: preamble, menu, kit, teleport to Lumbridge.
	 * Must be called from plugin context (blocking dialogue). Pass npc = null
	 * for the NPC-less variant (skip track, login resume).
	 *
	 * @return true if a path was chosen.
	 */
	public static boolean openPathChoice(Player player, Npc npc) {
		if (player == null || hasChosen(player)) {
			return false;
		}
		if (VoidVeteranTour.needsRequiredTour(player)) {
			if (npc != null) {
				npcsay(player, npc, "The Archivist waits south of here. Hear what changed, then return to me.");
			} else {
				player.message("@mag@Speak to the Void Archivist before choosing a path.");
			}
			return false;
		}

		if (npc != null) {
			npcsay(player, npc,
				"choose your path",
				"each path gives 2x experience in its listed skills until level " + BOOST_LEVEL_CAP,
				"I will also give you a starter kit suited to that path");
		} else {
			player.message("Choose your path: each gives 2x experience in its listed skills until level " + BOOST_LEVEL_CAP + ".");
			player.message("A starter kit suited to your path will be placed in your backpack.");
		}

		int choice = multi(player, npc, false, OPTION_WARRIOR, OPTION_FORAGER, OPTION_ARCANIST);
		if (choice < 0) {
			return false;
		}

		int path = choice == 0 ? WARRIOR : choice == 1 ? FORAGER : ARCANIST;
		boolean kitAlreadyGranted = player.getCache().hasKey(STARTER_KIT_CACHE_KEY);
		if (!kitAlreadyGranted && !canGrantStarterKit(player, path)) {
			return false;
		}

		choose(player, path);
		boolean kitGranted = !kitAlreadyGranted && grantStarterKit(player, path);
		if (!kitAlreadyGranted && !kitGranted) {
			player.getCache().remove(CACHE_KEY);
			ActionSender.sendGameSettings(player);
			player.save(false, true);
			player.message("@red@Your starter kit could not be placed in your backpack. Try again.");
			return false;
		}

		ActionSender.sendGameSettings(player);
		player.save(false, true);
		player.message(name(path) + " chosen. " + boostedSkillSummary(path) + " now earn " + boostLimitSummary() + ".");
		if (kitGranted) {
			player.message("Your starter kit has been placed in your backpack.");
		}
		player.teleport(player.getConfig().RESPAWN_LOCATION_X, player.getConfig().RESPAWN_LOCATION_Y, true);
		if (!BetaOnboardingGuide.showFirstTime(player)) {
			ActionSender.sendBox(player, "@yel@" + name(path) + " chosen.% %"
				+ "@whi@" + boostLimitSummary() + ": @gre@" + boostedSkillSummary(path) + "@whi@.%"
				+ "@whi@Starter kit: @cya@" + starterKitSummary(path) + "@whi@.% %"
				+ "@whi@You have arrived in Lumbridge. Open your backpack, equip anything useful, and start exploring.", true);
		}
		return true;
	}

	public static boolean grantStarterKit(Player player, int path) {
		if (player == null || path < WARRIOR || path > ARCANIST || player.getCache().hasKey(STARTER_KIT_CACHE_KEY)) {
			return false;
		}
		if (!canGrantStarterKit(player, path)) {
			return false;
		}

		for (StarterItem item : starterKit(path)) {
			if (!addStarterItem(player, item)) {
				return false;
			}
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
			&& (VoidOnboarding.hasRetiredGuidedState(player)
				|| inLegacyVoidIsland(player)
				|| player.getLocation().inVoidIsland()
				|| player.getLocation().inVoidTutorialIsle()
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
				return "classic tools, basic runes, bronze sword and shield, iron short sword, and extra food";
			case FORAGER:
				return "classic tools, basic runes, bronze sword and shield, fishing rod, bait, hammer, and coins";
			case ARCANIST:
				return "classic tools, basic runes, bronze sword and shield, extra runes, shortbow, arrows, and wizard hat";
			default:
				return "no starter kit";
		}
	}

	private static StarterItem[] starterKit(int path) {
		switch (path) {
			case WARRIOR:
				return starterKitWithBonus(
					new StarterItem(ItemId.IRON_SHORT_SWORD.id()),
					new StarterItem(ItemId.COOKEDMEAT.id(), 5));
			case FORAGER:
				return starterKitWithBonus(
					new StarterItem(ItemId.FISHING_ROD.id()),
					new StarterItem(ItemId.FISHING_BAIT.id(), 25),
					new StarterItem(ItemId.HAMMER.id()),
					new StarterItem(ItemId.COINS.id(), 50));
			case ARCANIST:
				return starterKitWithBonus(
					new StarterItem(ItemId.AIR_RUNE.id(), 50),
					new StarterItem(ItemId.MIND_RUNE.id(), 30),
					new StarterItem(ItemId.FIRE_RUNE.id(), 15),
					new StarterItem(ItemId.SHORTBOW.id()),
					new StarterItem(ItemId.BRONZE_ARROWS.id(), 50),
					new StarterItem(ItemId.BLUE_WIZARDSHAT.id()));
			default:
				return new StarterItem[0];
		}
	}

	private static StarterItem[] starterKitWithBonus(StarterItem... bonusItems) {
		StarterItem[] kit = new StarterItem[CLASSIC_STARTER_ITEMS.length + bonusItems.length];
		System.arraycopy(CLASSIC_STARTER_ITEMS, 0, kit, 0, CLASSIC_STARTER_ITEMS.length);
		System.arraycopy(bonusItems, 0, kit, CLASSIC_STARTER_ITEMS.length, bonusItems.length);
		return kit;
	}

	private static boolean canGrantStarterKit(Player player, int path) {
		if (player == null || !player.getWorld().isCurrentPlayer(player)) {
			return false;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		int requiredSlots = 0;
		Set<Integer> plannedStackableItems = new HashSet<>();
		for (StarterItem item : starterKit(path)) {
			if (!shouldGrantStarterItem(player, item)) {
				continue;
			}
			ItemDefinition itemDef = player.getWorld().getServer().getEntityHandler().getItemDef(item.itemId);
			if (itemDef == null) {
				player.message("@red@Starter kit item " + item.itemId + " is not available. Contact staff.");
				return false;
			}
			if (player.getConfig().RESTRICT_ITEM_ID >= 0 && player.getConfig().RESTRICT_ITEM_ID < item.itemId) {
				player.message("@red@This world cannot grant one of the starter kit items.");
				return false;
			}
			if (player.getClientLimitations().maxItemId < item.itemId) {
				player.message("@red@Your client cannot receive one of the starter kit items.");
				return false;
			}
			if (itemDef.isStackable()) {
				if (!inventory.hasCatalogID(item.itemId, false) && plannedStackableItems.add(item.itemId)) {
					requiredSlots++;
				}
			} else {
				requiredSlots += inventory.getRequiredSlots(item.itemId, item.amount, false);
			}
		}
		if (inventory.size() + requiredSlots > Inventory.MAX_SIZE) {
			player.message("@red@Clear " + (inventory.size() + requiredSlots - Inventory.MAX_SIZE)
				+ " backpack slot(s) before choosing a path.");
			return false;
		}
		return true;
	}

	private static boolean addStarterItem(Player player, StarterItem item) {
		if (!shouldGrantStarterItem(player, item)) {
			return true;
		}
		int itemId = item.itemId;
		int amount = item.amount;
		ItemDefinition itemDef = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		if (itemDef != null && !itemDef.isStackable() && amount > 1) {
			for (int i = 0; i < amount; i++) {
				if (!player.getCarriedItems().getInventory().add(new Item(itemId), false)) {
					return false;
				}
			}
			return true;
		}
		return player.getCarriedItems().getInventory().add(new Item(itemId, amount), false);
	}

	private static boolean shouldGrantStarterItem(Player player, StarterItem item) {
		return !item.skipIfOwned
			|| (!player.getCarriedItems().hasCatalogID(item.itemId) && !player.getBank().hasItemId(item.itemId));
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
		private final boolean skipIfOwned;

		private StarterItem(int itemId) {
			this(itemId, 1);
		}

		private StarterItem(int itemId, int amount) {
			this(itemId, amount, false);
		}

		private StarterItem(int itemId, boolean skipIfOwned) {
			this(itemId, 1, skipIfOwned);
		}

		private StarterItem(int itemId, int amount, boolean skipIfOwned) {
			this.itemId = itemId;
			this.amount = amount;
			this.skipIfOwned = skipIfOwned;
		}
	}

	private static final StarterItem[] CLASSIC_STARTER_ITEMS = new StarterItem[] {
		new StarterItem(ItemId.BRONZE_AXE.id(), true),
		new StarterItem(ItemId.TINDERBOX.id(), true),
		new StarterItem(ItemId.COOKEDMEAT.id()),
		new StarterItem(ItemId.NET.id(), true),
		new StarterItem(ItemId.BRONZE_PICKAXE.id(), true),
		new StarterItem(ItemId.BRONZE_LONG_SWORD.id(), true),
		new StarterItem(ItemId.WOODEN_SHIELD.id(), true),
		new StarterItem(ItemId.AIR_RUNE.id(), 12, true),
		new StarterItem(ItemId.MIND_RUNE.id(), 8, true),
		new StarterItem(ItemId.WATER_RUNE.id(), 3, true),
		new StarterItem(ItemId.EARTH_RUNE.id(), 2, true),
		new StarterItem(ItemId.BODY_RUNE.id(), 1, true)
	};
}
