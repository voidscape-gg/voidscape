package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.HashSet;
import java.util.Set;

import static com.openrsc.server.plugins.Functions.multi;

public final class BetaOnboardingGuide {
	public static final String SEEN_CACHE_KEY = "void_beta_guide_seen";

	private static final int BETA_LEVEL = 99;

	private static final Destination[] HUB_DESTINATIONS = {
		new Destination("Lumbridge home - 120,648", 120, 648),
		new Destination("Subscription Vendor - 126,649", 126, 649),
		new Destination("Edgeville bank - 217,449", 217, 449),
		new Destination("Void Auctioneer - 217,460", 217, 460),
		new Destination("City Void Rift - 139,636", 139, 636),
		new Destination("Void Enclave - 113,314", 113, 314)
	};

	private static final Destination[] COMBAT_DESTINATIONS = {
		new Destination("Void Dungeon entrance - 112,296", 112, 296),
		new Destination("Void Dungeon inside - 72,3252", 72, 3252),
		new Destination("Void Knight chamber - 984,667", 984, 667),
		new Destination("Void Arena lobby - 600,2914", 600, 2914),
		new Destination("Void Colossus rift - 113,321", 113, 321)
	};

	private static final BetaItem[] CARD_KEY_KIT = {
		new BetaItem(ItemId.SUBSCRIPTION_CARD.id(), 1),
		new BetaItem(ItemId.VOID_KEY.id(), 3)
	};

	private static final BetaItem[] VOID_GEAR_KIT = {
		new BetaItem(ItemId.VOID_SCIMITAR.id(), 1),
		new BetaItem(ItemId.VOID_BOW.id(), 1),
		new BetaItem(ItemId.VOID_AMULET.id(), 1),
		new BetaItem(ItemId.VOID_MACE.id(), 1),
		new BetaItem(ItemId.RUNE_ARROWS.id(), 250)
	};

	private static final BetaItem[] MELEE_KIT = {
		new BetaItem(ItemId.RUNE_SCIMITAR.id(), 1),
		new BetaItem(ItemId.RUNE_2_HANDED_SWORD.id(), 1),
		new BetaItem(ItemId.LARGE_RUNE_HELMET.id(), 1),
		new BetaItem(ItemId.RUNE_PLATE_MAIL_BODY.id(), 1),
		new BetaItem(ItemId.RUNE_PLATE_MAIL_LEGS.id(), 1),
		new BetaItem(ItemId.RUNE_KITE_SHIELD.id(), 1),
		new BetaItem(ItemId.CHARGED_DRAGONSTONE_AMULET.id(), 1),
		new BetaItem(ItemId.LOBSTER.id(), 12),
		new BetaItem(ItemId.FULL_SUPER_ATTACK_POTION.id(), 2),
		new BetaItem(ItemId.FULL_SUPER_STRENGTH_POTION.id(), 2),
		new BetaItem(ItemId.FULL_SUPER_DEFENSE_POTION.id(), 2)
	};

	private static final BetaItem[] RANGED_KIT = {
		new BetaItem(ItemId.MAGIC_SHORTBOW.id(), 1),
		new BetaItem(ItemId.RUNE_ARROWS.id(), 500),
		new BetaItem(ItemId.SHARK.id(), 10),
		new BetaItem(ItemId.FULL_RANGING_POTION.id(), 3)
	};

	private static final BetaItem[] MAGIC_KIT = {
		new BetaItem(ItemId.AIR_RUNE.id(), 500),
		new BetaItem(ItemId.FIRE_RUNE.id(), 500),
		new BetaItem(ItemId.MIND_RUNE.id(), 500),
		new BetaItem(ItemId.CHAOS_RUNE.id(), 250),
		new BetaItem(ItemId.DEATH_RUNE.id(), 150),
		new BetaItem(ItemId.LAW_RUNE.id(), 100),
		new BetaItem(ItemId.SHARK.id(), 10),
		new BetaItem(ItemId.FULL_SUPER_MAGIC_POTION.id(), 3)
	};

	private static final BetaItem[] SKILLING_KIT = {
		new BetaItem(ItemId.RUNE_PICKAXE.id(), 1),
		new BetaItem(ItemId.RUNE_AXE.id(), 1),
		new BetaItem(ItemId.NET.id(), 1),
		new BetaItem(ItemId.FISHING_ROD.id(), 1),
		new BetaItem(ItemId.FISHING_BAIT.id(), 500),
		new BetaItem(ItemId.TINDERBOX.id(), 1),
		new BetaItem(ItemId.COINS.id(), 250000)
	};

	private BetaOnboardingGuide() {
	}

	public static boolean showFirstTime(Player player) {
		if (!enabled(player) || player.getCache().hasKey(SEEN_CACHE_KEY)) {
			return false;
		}
		player.getCache().store(SEEN_CACHE_KEY, true);
		player.save(false, true);
		player.message("@mag@Beta toolkit: choose a test action. Reopen it with ::beta.");
		show(player);
		return true;
	}

	public static void show(Player player) {
		if (!enabled(player)) {
			player.message("The beta guide is disabled on this world.");
			return;
		}

		while (true) {
			int choice = multi(player, null, false,
				"Teleports: hubs, bosses, and test areas",
				"Stats: set levels and restore",
				"Items: spawn beta kits",
				"Checklist: what to test",
				"Reference: commands, coords, item IDs",
				"Close beta toolkit");

			switch (choice) {
				case 0:
					showTeleports(player);
					break;
				case 1:
					showStats(player);
					break;
				case 2:
					showItems(player);
					break;
				case 3:
					ActionSender.sendBox(player, checklistText(), true);
					break;
				case 4:
					ActionSender.sendBox(player, referenceText(), true);
					break;
				default:
					player.message("@whi@Use @gre@::beta@whi@ any time you need the tester toolkit.");
					return;
			}
		}
	}

	private static void showTeleports(Player player) {
		while (true) {
			int choice = multi(player, null, false,
				"Hub teleports",
				"Combat and boss teleports",
				"Back to beta toolkit");
			switch (choice) {
				case 0:
					if (showDestinationList(player, "Hub teleports", HUB_DESTINATIONS)) {
						return;
					}
					break;
				case 1:
					if (showDestinationList(player, "Combat teleports", COMBAT_DESTINATIONS)) {
						return;
					}
					break;
				default:
					return;
			}
		}
	}

	private static boolean showDestinationList(Player player, String title, Destination[] destinations) {
		String[] options = new String[destinations.length + 1];
		for (int i = 0; i < destinations.length; i++) {
			options[i] = destinations[i].label;
		}
		options[destinations.length] = "Back";

		int choice = multi(player, null, false, options);
		if (choice < 0 || choice >= destinations.length) {
			return false;
		}
		Destination destination = destinations[choice];
		if (player.inCombat()) {
			player.message("You cannot beta-teleport while fighting.");
			return false;
		}
		player.resetAll();
		player.teleport(destination.x, destination.y, true);
		player.message("@mag@" + title + ": @whi@" + destination.label + ".");
		return true;
	}

	private static void showStats(Player player) {
		while (true) {
			int choice = multi(player, null, false,
				"Set all skills to 99",
				"Set combat skills to 99",
				"Set skilling skills to 99",
				"Restore hits and prayer",
				"Back to beta toolkit");
			switch (choice) {
				case 0:
					setAllSkills(player);
					break;
				case 1:
					setCombatSkills(player);
					break;
				case 2:
					setSkillingSkills(player);
					break;
				case 3:
					restore(player);
					break;
				default:
					return;
			}
		}
	}

	private static void showItems(Player player) {
		while (true) {
			int choice = multi(player, null, false,
				"Spawn subscription card + Void Keys",
				"Spawn Void gear set",
				"Spawn melee boss kit",
				"Spawn ranged boss kit",
				"Spawn magic boss kit",
				"Spawn skilling kit",
				"Spawn 250k coins",
				"Back to beta toolkit");
			switch (choice) {
				case 0:
					grantKit(player, "subscription card + Void Keys", CARD_KEY_KIT);
					break;
				case 1:
					grantKit(player, "Void gear set", VOID_GEAR_KIT);
					break;
				case 2:
					grantKit(player, "melee boss kit", MELEE_KIT);
					break;
				case 3:
					grantKit(player, "ranged boss kit", RANGED_KIT);
					break;
				case 4:
					grantKit(player, "magic boss kit", MAGIC_KIT);
					break;
				case 5:
					grantKit(player, "skilling kit", SKILLING_KIT);
					break;
				case 6:
					grantKit(player, "250k coins", new BetaItem[] {new BetaItem(ItemId.COINS.id(), 250000)});
					break;
				default:
					return;
			}
		}
	}

	private static void setAllSkills(Player player) {
		int count = player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
		for (int skill = 0; skill < count; skill++) {
			setSkillLevel(player, skill, cappedBetaLevel(player));
		}
		finishStats(player, "All skills set to " + cappedBetaLevel(player) + ".");
	}

	private static void setCombatSkills(Player player) {
		int[] skills = {
			Skill.ATTACK.id(),
			Skill.DEFENSE.id(),
			Skill.STRENGTH.id(),
			Skill.HITS.id(),
			Skill.RANGED.id(),
			Skill.PRAYER.id(),
			Skill.PRAYGOOD.id(),
			Skill.PRAYEVIL.id(),
			Skill.MAGIC.id(),
			Skill.GOODMAGIC.id(),
			Skill.EVILMAGIC.id()
		};
		for (int skill : skills) {
			setSkillLevel(player, skill, cappedBetaLevel(player));
		}
		finishStats(player, "Combat skills set to " + cappedBetaLevel(player) + ".");
	}

	private static void setSkillingSkills(Player player) {
		int[] combat = {
			Skill.ATTACK.id(),
			Skill.DEFENSE.id(),
			Skill.STRENGTH.id(),
			Skill.HITS.id(),
			Skill.RANGED.id(),
			Skill.PRAYER.id(),
			Skill.PRAYGOOD.id(),
			Skill.PRAYEVIL.id(),
			Skill.MAGIC.id(),
			Skill.GOODMAGIC.id(),
			Skill.EVILMAGIC.id()
		};
		int count = player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
		for (int skill = 0; skill < count; skill++) {
			if (!contains(combat, skill)) {
				setSkillLevel(player, skill, cappedBetaLevel(player));
			}
		}
		finishStats(player, "Skilling skills set to " + cappedBetaLevel(player) + ".");
	}

	private static void restore(Player player) {
		player.getSkills().normalize();
		player.message("@gre@Hits, prayer, and temporary stat drains restored.");
	}

	private static void setSkillLevel(Player player, int skill, int level) {
		if (!validSkill(player, skill)) {
			return;
		}
		player.getSkills().setLevelTo(skill, level);
		player.getSkills().setLevel(skill, level);
		if (skill == Skill.PRAYER.id()) {
			player.setPrayerStatePoints(level * 120);
		}
	}

	private static void finishStats(Player player, String message) {
		player.checkEquipment();
		player.getSkills().sendUpdateAll();
		ActionSender.sendEquipmentStats(player);
		player.message("@gre@" + message);
	}

	private static int cappedBetaLevel(Player player) {
		return Math.min(BETA_LEVEL, player.getWorld().getServer().getConfig().PLAYER_LEVEL_LIMIT);
	}

	private static boolean validSkill(Player player, int skill) {
		return skill >= 0 && skill < player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
	}

	private static boolean contains(int[] values, int target) {
		for (int value : values) {
			if (value == target) {
				return true;
			}
		}
		return false;
	}

	private static void grantKit(Player player, String kitName, BetaItem[] kit) {
		int requiredSlots = requiredSlots(player, kit);
		if (player.getCarriedItems().getInventory().getFreeSlots() < requiredSlots) {
			player.message("@or1@You need " + requiredSlots + " free inventory slot"
				+ (requiredSlots == 1 ? "" : "s") + " for that beta kit.");
			return;
		}

		int addedStacks = 0;
		for (BetaItem item : kit) {
			ItemDefinition def = itemDefinition(player, item.id);
			if (def == null || item.amount <= 0) {
				continue;
			}
			if (def.isStackable()) {
				if (player.getCarriedItems().getInventory().add(new Item(item.id, item.amount))) {
					addedStacks++;
				}
			} else {
				for (int i = 0; i < item.amount; i++) {
					if (player.getCarriedItems().getInventory().add(new Item(item.id, 1))) {
						addedStacks++;
					}
				}
			}
		}
		ActionSender.sendInventory(player);
		player.message("@gre@Spawned beta kit: @whi@" + kitName + " @gre@(" + addedStacks + " stack"
			+ (addedStacks == 1 ? "" : "s") + ").");
	}

	private static int requiredSlots(Player player, BetaItem[] kit) {
		int slots = 0;
		Set<Integer> newStackIds = new HashSet<Integer>();
		for (BetaItem item : kit) {
			ItemDefinition def = itemDefinition(player, item.id);
			if (def == null || item.amount <= 0) {
				continue;
			}
			if (def.isStackable()) {
				if (player.getCarriedItems().getInventory().countId(item.id) <= 0 && newStackIds.add(item.id)) {
					slots++;
				}
			} else {
				slots += item.amount;
			}
		}
		return slots;
	}

	private static ItemDefinition itemDefinition(Player player, int itemId) {
		try {
			return player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		} catch (Exception ignored) {
			return null;
		}
	}

	private static boolean enabled(Player player) {
		return player != null && player.getConfig().WANT_BETA_ONBOARDING_GUIDE;
	}

	private static String checklistText() {
		return "@yel@Beta checklist% %"
			+ "@whi@1. New account: intro, Void Herald, starter kit, path boost.%"
			+ "@whi@2. Account rewards: subscription card claim, code redeem, ::codes.%"
			+ "@whi@3. Core loops: bank, shops, Auction House, titles, loot beams.%"
			+ "@whi@4. Combat: Void Dungeon, Void Knight, Void Arena, Void Colossus.%"
			+ "@whi@5. Progression: drops, skilling, rested XP, teleport/rift flows.% %"
			+ "@lre@Bug report format: character, coords, action, result, expected result.";
	}

	private static String referenceText() {
		return "@yel@Beta reference% %"
			+ "@whi@Commands: @gre@::beta@whi@, @gre@::coords@whi@, @gre@::rested@whi@, @gre@::titles@whi@, @gre@::codes@whi@.%"
			+ "@whi@Coords: Lumbridge @gre@120 648@whi@, Vendor @gre@126 649@whi@, Edgeville @gre@217 449@whi@.%"
			+ "@whi@Coords: Enclave @gre@113 314@whi@, Dungeon @gre@112 296@whi@, Arena @gre@600 2914@whi@.%"
			+ "@whi@Coords: Void Knight @gre@984 667@whi@, Colossus rift @gre@113 321@whi@.% %"
			+ "@whi@Items: Subscription card @gre@1602@whi@, Void Key @gre@1601@whi@.%"
			+ "@whi@Items: Void Scimitar @gre@1593@whi@, Void Bow @gre@1594@whi@, Void Amulet @gre@1595@whi@, Void Mace @gre@1596@whi@.";
	}

	private static final class Destination {
		private final String label;
		private final int x;
		private final int y;

		private Destination(String label, int x, int y) {
			this.label = label;
			this.x = x;
			this.y = y;
		}
	}

	private static final class BetaItem {
		private final int id;
		private final int amount;

		private BetaItem(int id, int amount) {
			this.id = id;
			this.amount = amount;
		}
	}
}
