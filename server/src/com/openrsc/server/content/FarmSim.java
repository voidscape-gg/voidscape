package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static com.openrsc.server.plugins.Functions.multi;

public final class FarmSim {
	private static final String SESSION_ATTRIBUTE = "voidscape_farmsim_session";
	private static final String CLIENT_PREFIX = "@vsfarmsim@";
	private static final long DEFAULT_PROJECTION_MILLIS = 60L * 60L * 1000L;
	private static final int MAX_DROPS_IN_POPUP = 80;

	private static final FarmKit[] KITS = {
		new FarmKit("40", "Flat 40 melee", 40, new int[] {
			ItemId.LARGE_ADAMANTITE_HELMET.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_BODY.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_LEGS.id(),
			ItemId.ADAMANTITE_KITE_SHIELD.id(),
			ItemId.ADAMANTITE_BATTLE_AXE.id(),
			ItemId.RUBY_AMULET_OF_STRENGTH.id()
		}),
		new FarmKit("60", "Flat 60 melee", 60, new int[] {
			ItemId.LARGE_ADAMANTITE_HELMET.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_BODY.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_LEGS.id(),
			ItemId.ADAMANTITE_2_HANDED_SWORD.id(),
			ItemId.RUBY_AMULET_OF_STRENGTH.id()
		}),
		new FarmKit("80", "Flat 80 melee", 80, new int[] {
			ItemId.LARGE_ADAMANTITE_HELMET.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_BODY.id(),
			ItemId.ADAMANTITE_PLATE_MAIL_LEGS.id(),
			ItemId.ADAMANTITE_KITE_SHIELD.id(),
			ItemId.RUNE_BATTLE_AXE.id(),
			ItemId.RUBY_AMULET_OF_STRENGTH.id()
		}),
		new FarmKit("99", "Flat 99 melee", 99, new int[] {
			ItemId.LARGE_RUNE_HELMET.id(),
			ItemId.RUNE_PLATE_MAIL_BODY.id(),
			ItemId.RUNE_PLATE_MAIL_LEGS.id(),
			ItemId.RUNE_2_HANDED_SWORD.id(),
			ItemId.RUBY_AMULET_OF_STRENGTH.id()
		})
	};

	private FarmSim() {
	}

	public static boolean enabled(Player player) {
		return player != null && player.getConfig().WANT_BETA_ONBOARDING_GUIDE;
	}

	public static void recordNpcKill(Player player, Npc npc) {
		if (!enabled(player) || player == null || npc == null || npc.getDef() == null) {
			return;
		}
		session(player).record(npc);
	}

	public static void handleFarmKitCommand(Player player, String[] args) {
		if (!enabled(player)) {
			player.message("FarmKit is only available on beta worlds.");
			return;
		}
		if (args.length < 1 || args[0].equalsIgnoreCase("help")) {
			sendFarmKitHelp(player);
			return;
		}
		FarmKit kit = findKit(args[0]);
		if (kit == null) {
			sendFarmKitHelp(player);
			return;
		}
		applyKit(player, kit);
	}

	public static void handleFarmSimCommand(Player player, String[] args) {
		if (!enabled(player)) {
			player.message("FarmSim is only available on beta worlds.");
			return;
		}
		if (args.length > 0) {
			String action = args[0].toLowerCase(Locale.ENGLISH);
			if (action.equals("start") || action.equals("reset") || action.equals("clear")) {
				resetSession(player);
				player.message("@gre@FarmSim sample reset. Kill a few NPCs, then use @whi@::farmsim@gre@.");
				return;
			}
			if (action.equals("status")) {
				sendStatus(player);
				return;
			}
			if (action.equals("help")) {
				sendFarmSimHelp(player);
				return;
			}
		}
		long projectionMillis = args.length > 0 ? parseDurationMillis(args[0], DEFAULT_PROJECTION_MILLIS) : DEFAULT_PROJECTION_MILLIS;
		sendProjection(player, projectionMillis);
	}

	public static void showMenu(Player player) {
		if (!enabled(player)) {
			player.message("FarmSim is only available on beta worlds.");
			return;
		}

		while (true) {
			int choice = multi(player, null, false,
				"Apply Flat 40 melee FarmKit",
				"Apply Flat 60 melee FarmKit",
				"Apply Flat 80 melee FarmKit",
				"Apply Flat 99 melee FarmKit",
				"Reset FarmSim sample",
				"Show 1-hour FarmSim projection",
				"FarmSim help",
				"Back to beta toolkit");
			if (choice >= 0 && choice <= 3) {
				applyKit(player, KITS[choice]);
			} else if (choice == 4) {
				resetSession(player);
				player.message("@gre@FarmSim sample reset.");
			} else if (choice == 5) {
				sendProjection(player, DEFAULT_PROJECTION_MILLIS);
			} else if (choice == 6) {
				sendFarmSimHelp(player);
			} else {
				return;
			}
		}
	}

	private static void applyKit(Player player, FarmKit kit) {
		if (player.inCombat()) {
			player.message("@or1@Leave combat before applying a FarmKit.");
			return;
		}
		if (!unequipCurrentGear(player)) {
			return;
		}

		setMeleeStats(player, kit.level);
		for (int itemId : kit.itemIds) {
			equipSpawnedItem(player, itemId);
		}
		player.getSkills().normalize();
		player.checkEquipment();
		player.getUpdateFlags().setAppearanceChanged(true);
		player.getSkills().sendUpdateAll();
		ActionSender.sendEquipmentStats(player);
		ActionSender.sendInventory(player);
		resetSession(player);
		player.message("@gre@Applied @whi@" + kit.label + "@gre@ FarmKit. Sample reset; kill a few NPCs, then use @whi@::farmsim@gre@.");
	}

	private static boolean unequipCurrentGear(Player player) {
		Equipment equipment = player.getCarriedItems().getEquipment();
		List<Item> equipped = new ArrayList<Item>();
		for (int slot = 0; slot < Equipment.SLOT_COUNT; slot++) {
			Item item = equipment.get(slot);
			if (item != null) {
				equipped.add(item);
			}
		}
		if (equipped.isEmpty()) {
			return true;
		}
		Inventory inventory = player.getCarriedItems().getInventory();
		if (inventory.getFreeSlots() < equipped.size()) {
			player.message("@or1@You need " + equipped.size() + " free inventory slot"
				+ (equipped.size() == 1 ? "" : "s") + " to store your current worn gear.");
			return false;
		}
		for (Item item : equipped) {
			equipment.unequipItem(new UnequipRequest(player, item, UnequipRequest.RequestType.FROM_EQUIPMENT, false), false);
		}
		return true;
	}

	private static void equipSpawnedItem(Player player, int itemId) {
		ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		if (def == null || !def.isWieldable()) {
			return;
		}
		int slot = player.getCarriedItems().getEquipment().add(new Item(itemId, 1));
		if (slot < 0) {
			return;
		}
		player.updateWornItems(def.getWieldPosition(), def.getAppearanceId(), def.getWearableId(), true);
	}

	private static void setMeleeStats(Player player, int level) {
		int capped = Math.max(1, Math.min(level, player.getConfig().PLAYER_LEVEL_LIMIT));
		setSkill(player, Skill.ATTACK.id(), capped);
		setSkill(player, Skill.DEFENSE.id(), capped);
		setSkill(player, Skill.STRENGTH.id(), capped);
		setSkill(player, Skill.HITS.id(), capped);
		setSkill(player, Skill.RANGED.id(), 1);
		setSkill(player, Skill.PRAYER.id(), 1);
		setSkill(player, Skill.MAGIC.id(), 1);
		player.getPrayers().resetPrayers();
	}

	private static void setSkill(Player player, int skill, int level) {
		if (skill < 0 || skill >= player.getWorld().getServer().getConstants().getSkills().getSkillsCount()) {
			return;
		}
		player.getSkills().setLevelTo(skill, level);
		player.getSkills().setLevel(skill, level);
	}

	private static void sendProjection(Player player, long projectionMillis) {
		Session sample = session(player);
		if (sample.totalKills() <= 0) {
			player.message("@or1@FarmSim has no sample yet. Kill a few NPCs, or use @whi@::farmsim start@or1@ first.");
			return;
		}

		long now = System.currentTimeMillis();
		long elapsed = Math.max(1000L, now - sample.startedAtMillis);
		double scale = (double) projectionMillis / (double) elapsed;
		Projection projection = buildProjection(player, sample, scale);
		if (projection.items.isEmpty()) {
			player.message("@or1@FarmSim projected no visible drops for that sample.");
			return;
		}

		String title = "FarmSim " + durationLabel(projectionMillis) + " projection";
		String subtitle = sampleComposition(player, sample) + " | " + kitLabel(player);
		String details = sample.totalKills() + " kills in " + shortDuration(elapsed)
			+ " | " + formatOneDecimal(sample.totalKills() * 3600000.0D / elapsed) + " kills/hr"
			+ " | approx " + formatLong(Math.round(projection.expectedValue)) + " gp/hr";
		if (elapsed < 120000L) {
			details += " | short sample";
		}

		sendFarmSimPopup(player, title, subtitle, details, projection.items);
		player.playerServerMessage(MessageType.QUEST, "@gre@FarmSim projected @whi@"
			+ formatLong(Math.round(sample.totalKills() * scale)) + "@gre@ kills over @whi@"
			+ durationLabel(projectionMillis) + "@gre@ from your sample.");
	}

	private static Projection buildProjection(Player player, Session sample, double scale) {
		Map<Integer, Double> expected = new TreeMap<Integer, Double>();
		double expectedValue = 0.0D;
		for (Map.Entry<Integer, KillSample> entry : sample.killsByNpc.entrySet()) {
			int npcId = entry.getKey();
			KillSample killSample = entry.getValue();
			double projectedKills = killSample.count * scale;
			if (projectedKills <= 0.0D) {
				continue;
			}

			int boneDrop = boneDrop(player, npcId);
			if (boneDrop != ItemId.NOTHING.id() && worldAllowsItem(player, boneDrop)) {
				addExpected(expected, boneDrop, projectedKills);
			}

			if (killSample.voidKeyEligibleCount > 0 && worldAllowsItem(player, ItemId.VOID_KEY.id())) {
				NPCDef def = player.getWorld().getServer().getEntityHandler().getNpcDef(npcId);
				int combat = def == null ? 0 : def.combatLevel;
				int denominator = Math.max(128, 1200 - combat * 6);
				addExpected(expected, ItemId.VOID_KEY.id(), killSample.voidKeyEligibleCount * scale / denominator);
			}

			DropTable dropTable = player.getWorld().getNpcDrops().getDropTable(npcId);
			if (dropTable == null) {
				continue;
			}
			for (DropTable.DropChance chance : dropTable.getDropChances()) {
				if (!worldAllowsItem(player, chance.itemId)) {
					continue;
				}
				double dropAmount = chance.amount;
				if (isStackable(player, chance.itemId)) {
					dropAmount = applyStackableBoosts(player, npcId, chance.itemId, dropAmount);
				}
				double amount = projectedKills * dropAmount * ((double) chance.numerator / (double) chance.denominator);
				addExpected(expected, chance.itemId, amount);
			}
		}

		List<ProjectedItem> items = new ArrayList<ProjectedItem>();
		for (Map.Entry<Integer, Double> entry : expected.entrySet()) {
			long amount = Math.round(entry.getValue());
			if (amount <= 0L) {
				continue;
			}
			items.add(new ProjectedItem(entry.getKey(), amount, itemValue(player, entry.getKey(), amount)));
			expectedValue += itemValue(player, entry.getKey(), entry.getValue());
		}
		Collections.sort(items, Comparator
			.comparingLong((ProjectedItem item) -> item.amount).reversed()
			.thenComparingInt(item -> item.itemId));
		if (items.size() > MAX_DROPS_IN_POPUP) {
			items = new ArrayList<ProjectedItem>(items.subList(0, MAX_DROPS_IN_POPUP));
		}
		return new Projection(items, expectedValue);
	}

	private static void sendFarmSimPopup(Player player, String title, String subtitle, String details, List<ProjectedItem> items) {
		StringBuilder payload = new StringBuilder(CLIENT_PREFIX);
		payload.append("v1|").append(clean(title));
		payload.append("|").append(clean(subtitle));
		payload.append("|").append(clean(details));
		payload.append("|");
		for (int i = 0; i < items.size(); i++) {
			ProjectedItem item = items.get(i);
			if (i > 0) {
				payload.append(';');
			}
			payload.append(item.itemId).append(',').append(item.amount);
		}
		ActionSender.sendMessage(player, null, MessageType.QUEST, payload.toString(), 0, null);
	}

	private static void resetSession(Player player) {
		Session session = new Session();
		player.setAttribute(SESSION_ATTRIBUTE, session);
	}

	private static Session session(Player player) {
		Session session = player.getAttribute(SESSION_ATTRIBUTE, null);
		if (session == null) {
			session = new Session();
			player.setAttribute(SESSION_ATTRIBUTE, session);
		}
		return session;
	}

	private static String sampleComposition(Player player, Session sample) {
		List<Map.Entry<Integer, KillSample>> entries = new ArrayList<Map.Entry<Integer, KillSample>>(sample.killsByNpc.entrySet());
		Collections.sort(entries, Comparator
			.comparingInt((Map.Entry<Integer, KillSample> entry) -> entry.getValue().count).reversed()
			.thenComparingInt(Map.Entry::getKey));
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < entries.size() && i < 3; i++) {
			Map.Entry<Integer, KillSample> entry = entries.get(i);
			if (i > 0) {
				out.append(", ");
			}
			out.append(npcName(player, entry.getKey())).append(" x").append(entry.getValue().count);
		}
		if (entries.size() > 3) {
			out.append(", +").append(entries.size() - 3).append(" more");
		}
		return out.toString();
	}

	private static void sendStatus(Player player) {
		Session sample = session(player);
		if (sample.totalKills() <= 0) {
			player.message("@or1@FarmSim sample is empty.");
			return;
		}
		player.playerServerMessage(MessageType.QUEST, "@yel@FarmSim sample: @whi@" + sampleComposition(player, sample)
			+ " @yel@over @whi@" + shortDuration(Math.max(1000L, System.currentTimeMillis() - sample.startedAtMillis)) + "@yel@.");
	}

	private static void sendFarmKitHelp(Player player) {
		player.playerServerMessage(MessageType.QUEST, "@yel@FarmKit: @whi@::farmkit 40@yel@, @whi@60@yel@, @whi@80@yel@, or @whi@99@yel@.");
		player.playerServerMessage(MessageType.QUEST, "@whi@Each kit applies melee stats, sets range/prayer/magic to 1, equips beta gear, and resets FarmSim.");
	}

	private static void sendFarmSimHelp(Player player) {
		player.playerServerMessage(MessageType.QUEST, "@yel@FarmSim flow: @whi@::farmkit 60@yel@, kill a few NPCs, then @whi@::farmsim@yel@.");
		player.playerServerMessage(MessageType.QUEST, "@whi@Use @gre@::farmsim start@whi@ to reset the sample or @gre@::farmsim 30m@whi@ for another duration.");
	}

	private static FarmKit findKit(String key) {
		for (FarmKit kit : KITS) {
			if (kit.key.equalsIgnoreCase(key) || kit.label.toLowerCase(Locale.ENGLISH).contains(key.toLowerCase(Locale.ENGLISH))) {
				return kit;
			}
		}
		return null;
	}

	private static long parseDurationMillis(String value, long fallback) {
		if (value == null || value.trim().isEmpty()) {
			return fallback;
		}
		String normalized = value.trim().toLowerCase(Locale.ENGLISH);
		try {
			if (normalized.endsWith("m")) {
				return clampMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1))) * 60000L;
			}
			if (normalized.endsWith("h")) {
				return clampMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60L) * 60000L;
			}
			return clampMinutes(Long.parseLong(normalized)) * 60000L;
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}

	private static long clampMinutes(long minutes) {
		return Math.max(5L, Math.min(240L, minutes));
	}

	private static int boneDrop(Player player, int npcId) {
		if (player.getWorld().getNpcDrops().isBigBoned(npcId)) {
			return player.getConfig().ONLY_REGULAR_BONES ? ItemId.BONES.id() : ItemId.BIG_BONES.id();
		}
		if (player.getWorld().getNpcDrops().isBatBoned(npcId)) {
			return player.getConfig().ONLY_REGULAR_BONES ? ItemId.BONES.id() : ItemId.BAT_BONES.id();
		}
		if (player.getWorld().getNpcDrops().isDragon(npcId)) {
			return player.getConfig().ONLY_REGULAR_BONES ? ItemId.BONES.id() : ItemId.DRAGON_BONES.id();
		}
		if (player.getWorld().getNpcDrops().isDemon(npcId)) {
			return ItemId.ASHES.id();
		}
		if (!player.getWorld().getNpcDrops().isBoneless(npcId)) {
			return ItemId.BONES.id();
		}
		return ItemId.NOTHING.id();
	}

	private static boolean worldAllowsItem(Player player, int itemId) {
		if (itemId == ItemId.NOTHING.id()) {
			return false;
		}
		ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		if (def == null) {
			return false;
		}
		if (player.getConfig().RESTRICT_ITEM_ID >= 0 && itemId > player.getConfig().RESTRICT_ITEM_ID) {
			return false;
		}
		if (player.getConfig().ONLY_BASIC_RUNES && def.getName().endsWith("-Rune") && itemId >= ItemId.LIFE_RUNE.id()) {
			return false;
		}
		return player.getConfig().MEMBER_WORLD || !def.isMembersOnly();
	}

	private static boolean isStackable(Player player, int itemId) {
		ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		return def != null && def.isStackable();
	}

	private static double applyStackableBoosts(Player player, int npcId, int itemId, double amount) {
		if (itemId == ItemId.COINS.id() && player.getCarriedItems().getEquipment().hasEquipped(ItemId.RING_OF_SPLENDOR.id())) {
			amount += Formulae.getSplendorBoost((int) Math.round(amount));
		}
		if (VoidContent.isVoidNpc(npcId) && player.getCarriedItems().getEquipment().hasEquipped(ItemId.VOID_AMULET.id())) {
			amount *= VoidContent.VOID_AMULET_STACKABLE_DROP_MULTIPLIER;
		}
		return amount;
	}

	private static void addExpected(Map<Integer, Double> expected, int itemId, double amount) {
		if (amount <= 0.0D) {
			return;
		}
		Double current = expected.get(itemId);
		expected.put(itemId, (current == null ? 0.0D : current) + amount);
	}

	private static double itemValue(Player player, int itemId, double amount) {
		ItemDefinition def = player.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		return def == null ? 0.0D : amount * def.getDefaultPrice();
	}

	private static String npcName(Player player, int npcId) {
		NPCDef def = player.getWorld().getServer().getEntityHandler().getNpcDef(npcId);
		if (def == null || def.getName() == null || def.getName().trim().isEmpty()) {
			return "NPC " + npcId;
		}
		return def.getName();
	}

	private static String kitLabel(Player player) {
		int attack = player.getSkills().getMaxStat(Skill.ATTACK.id());
		int strength = player.getSkills().getMaxStat(Skill.STRENGTH.id());
		int defense = player.getSkills().getMaxStat(Skill.DEFENSE.id());
		int hits = player.getSkills().getMaxStat(Skill.HITS.id());
		return "stats " + attack + "/" + strength + "/" + defense + "/" + hits;
	}

	private static String durationLabel(long millis) {
		long minutes = Math.max(1L, millis / 60000L);
		if (minutes % 60L == 0L) {
			long hours = minutes / 60L;
			return hours + "h";
		}
		return minutes + "m";
	}

	private static String shortDuration(long millis) {
		long seconds = Math.max(1L, millis / 1000L);
		long minutes = seconds / 60L;
		long remainder = seconds % 60L;
		if (minutes <= 0L) {
			return seconds + "s";
		}
		return minutes + "m " + remainder + "s";
	}

	private static String formatLong(long value) {
		return String.format(Locale.ENGLISH, "%,d", value);
	}

	private static String formatOneDecimal(double value) {
		return String.format(Locale.ENGLISH, "%,.1f", value);
	}

	private static String clean(String value) {
		if (value == null) {
			return "";
		}
		return value.replace('|', '/').replace(';', ',').replace('\n', ' ').replace('\r', ' ');
	}

	private static final class Session {
		private final long startedAtMillis = System.currentTimeMillis();
		private final LinkedHashMap<Integer, KillSample> killsByNpc = new LinkedHashMap<Integer, KillSample>();

		private void record(Npc npc) {
			KillSample sample = killsByNpc.get(npc.getID());
			if (sample == null) {
				sample = new KillSample();
				killsByNpc.put(npc.getID(), sample);
			}
			sample.count++;
			if (npc.getDef().combatLevel > 0 && npc.getLocation().inWilderness() && !npc.getLocation().isInSafeZone()) {
				sample.voidKeyEligibleCount++;
			}
		}

		private int totalKills() {
			int total = 0;
			for (KillSample sample : killsByNpc.values()) {
				total += sample.count;
			}
			return total;
		}
	}

	private static final class KillSample {
		private int count;
		private int voidKeyEligibleCount;
	}

	private static final class FarmKit {
		private final String key;
		private final String label;
		private final int level;
		private final int[] itemIds;

		private FarmKit(String key, String label, int level, int[] itemIds) {
			this.key = key;
			this.label = label;
			this.level = level;
			this.itemIds = itemIds;
		}
	}

	private static final class Projection {
		private final List<ProjectedItem> items;
		private final double expectedValue;

		private Projection(List<ProjectedItem> items, double expectedValue) {
			this.items = items;
			this.expectedValue = expectedValue;
		}
	}

	private static final class ProjectedItem {
		private final int itemId;
		private final long amount;
		private final double value;

		private ProjectedItem(int itemId, long amount, double value) {
			this.itemId = itemId;
			this.amount = amount;
			this.value = value;
		}
	}
}
