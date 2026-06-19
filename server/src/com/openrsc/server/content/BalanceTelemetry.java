package com.openrsc.server.content;

import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.external.NPCDef;
import com.openrsc.server.external.SkillDef;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class BalanceTelemetry {
	private static final int DEFAULT_LIMIT = 8;
	private static final AtomicLong reportStartedAtMillis = new AtomicLong(System.currentTimeMillis());
	private static final AtomicLong totalExperience = new AtomicLong(0);
	private static final AtomicLong totalNpcKills = new AtomicLong(0);
	private static final AtomicLong totalItemDrops = new AtomicLong(0);
	private static final AtomicLong totalRareDropEvents = new AtomicLong(0);
	private static final ConcurrentHashMap<Integer, AtomicLong> experienceBySkill = new ConcurrentHashMap<Integer, AtomicLong>();
	private static final ConcurrentHashMap<String, AtomicLong> experienceByPlayer = new ConcurrentHashMap<String, AtomicLong>();
	private static final ConcurrentHashMap<Integer, AtomicLong> killsByNpc = new ConcurrentHashMap<Integer, AtomicLong>();
	private static final ConcurrentHashMap<Integer, AtomicLong> dropsByItem = new ConcurrentHashMap<Integer, AtomicLong>();

	private BalanceTelemetry() {
	}

	public static void recordExperience(Player player, int skill, int amount) {
		if (player == null || amount <= 0) {
			return;
		}

		totalExperience.addAndGet(amount);
		increment(experienceBySkill, skill, amount);
		increment(experienceByPlayer, player.getUsername(), amount);
	}

	public static void recordNpcKill(Player player, Npc npc) {
		if (player == null || npc == null) {
			return;
		}

		totalNpcKills.incrementAndGet();
		increment(killsByNpc, npc.getID(), 1);
	}

	public static void recordNpcDrop(Player player, Npc npc, int itemId, int amount, boolean rare) {
		if (player == null || npc == null || amount <= 0) {
			return;
		}

		totalItemDrops.addAndGet(amount);
		if (rare) {
			totalRareDropEvents.incrementAndGet();
		}
		increment(dropsByItem, itemId, amount);
	}

	public static List<String> report(Player viewer, String mode) {
		String normalizedMode = mode == null || mode.isEmpty() ? "summary" : mode.toLowerCase(Locale.ENGLISH);
		if (normalizedMode.equals("xp") || normalizedMode.equals("skills")) {
			return skillExperienceReport(viewer);
		}
		if (normalizedMode.equals("players") || normalizedMode.equals("playerxp")) {
			return playerExperienceReport();
		}
		if (normalizedMode.equals("npcs") || normalizedMode.equals("kills")) {
			return npcKillReport(viewer);
		}
		if (normalizedMode.equals("drops") || normalizedMode.equals("items")) {
			return itemDropReport(viewer);
		}
		return summaryReport(viewer);
	}

	public static void reset() {
		reportStartedAtMillis.set(System.currentTimeMillis());
		totalExperience.set(0);
		totalNpcKills.set(0);
		totalItemDrops.set(0);
		totalRareDropEvents.set(0);
		experienceBySkill.clear();
		experienceByPlayer.clear();
		killsByNpc.clear();
		dropsByItem.clear();
	}

	private static List<String> summaryReport(Player viewer) {
		List<String> lines = new ArrayList<String>();
		lines.add("@yel@Balance telemetry window (" + durationSinceStart() + ")");
		lines.add("@whi@XP: @gre@" + format(totalExperience.get())
			+ "@whi@ | NPC kills: @gre@" + format(totalNpcKills.get())
			+ "@whi@ | item qty: @gre@" + format(totalItemDrops.get())
			+ "@whi@ | rare drop events: @mag@" + format(totalRareDropEvents.get()));
		lines.add("@whi@Use @cya@::balancereport xp@whi@, @cya@players@whi@, @cya@npcs@whi@, or @cya@drops@whi@.");
		List<Map.Entry<Integer, AtomicLong>> topSkills = top(experienceBySkill, 1);
		if (!topSkills.isEmpty()) {
			lines.add("@whi@Top skill: " + formatSkillEntry(viewer, topSkills.get(0)));
		}
		List<Map.Entry<Integer, AtomicLong>> topNpcs = top(killsByNpc, 1);
		if (!topNpcs.isEmpty()) {
			lines.add("@whi@Top NPC: " + formatNpcEntry(viewer, topNpcs.get(0)));
		}
		List<Map.Entry<Integer, AtomicLong>> topDrops = top(dropsByItem, 1);
		if (!topDrops.isEmpty()) {
			lines.add("@whi@Top drop: " + formatItemEntry(viewer, topDrops.get(0)));
		}
		return lines;
	}

	private static List<String> skillExperienceReport(Player viewer) {
		List<String> lines = new ArrayList<String>();
		lines.add("@yel@XP by skill in telemetry window");
		for (Map.Entry<Integer, AtomicLong> entry : top(experienceBySkill, DEFAULT_LIMIT)) {
			lines.add("@whi@" + formatSkillEntry(viewer, entry));
		}
		if (lines.size() == 1) {
			lines.add("@whi@No XP recorded yet.");
		}
		return lines;
	}

	private static List<String> playerExperienceReport() {
		List<String> lines = new ArrayList<String>();
		lines.add("@yel@XP by player in telemetry window");
		for (Map.Entry<String, AtomicLong> entry : top(experienceByPlayer, DEFAULT_LIMIT)) {
			lines.add("@whi@" + entry.getKey() + ": @gre@" + format(entry.getValue().get()) + " xp");
		}
		if (lines.size() == 1) {
			lines.add("@whi@No XP recorded yet.");
		}
		return lines;
	}

	private static List<String> npcKillReport(Player viewer) {
		List<String> lines = new ArrayList<String>();
		lines.add("@yel@NPC kills in telemetry window");
		for (Map.Entry<Integer, AtomicLong> entry : top(killsByNpc, DEFAULT_LIMIT)) {
			lines.add("@whi@" + formatNpcEntry(viewer, entry));
		}
		if (lines.size() == 1) {
			lines.add("@whi@No NPC kills recorded yet.");
		}
		return lines;
	}

	private static List<String> itemDropReport(Player viewer) {
		List<String> lines = new ArrayList<String>();
		lines.add("@yel@NPC item quantity in telemetry window");
		for (Map.Entry<Integer, AtomicLong> entry : top(dropsByItem, DEFAULT_LIMIT)) {
			lines.add("@whi@" + formatItemEntry(viewer, entry));
		}
		if (lines.size() == 1) {
			lines.add("@whi@No NPC drops recorded yet.");
		}
		return lines;
	}

	private static <K> void increment(ConcurrentHashMap<K, AtomicLong> map, K key, long amount) {
		AtomicLong counter = map.get(key);
		if (counter == null) {
			AtomicLong newCounter = new AtomicLong(0);
			counter = map.putIfAbsent(key, newCounter);
			if (counter == null) {
				counter = newCounter;
			}
		}
		counter.addAndGet(amount);
	}

	private static <K> List<Map.Entry<K, AtomicLong>> top(ConcurrentHashMap<K, AtomicLong> map, int limit) {
		List<Map.Entry<K, AtomicLong>> entries = new ArrayList<Map.Entry<K, AtomicLong>>(map.entrySet());
		Collections.sort(entries, new Comparator<Map.Entry<K, AtomicLong>>() {
			@Override
			public int compare(Map.Entry<K, AtomicLong> left, Map.Entry<K, AtomicLong> right) {
				return Long.compare(right.getValue().get(), left.getValue().get());
			}
		});
		if (entries.size() <= limit) {
			return entries;
		}
		return new ArrayList<Map.Entry<K, AtomicLong>>(entries.subList(0, limit));
	}

	private static String formatSkillEntry(Player viewer, Map.Entry<Integer, AtomicLong> entry) {
		return skillName(viewer, entry.getKey()) + ": @gre@" + format(entry.getValue().get()) + " xp";
	}

	private static String formatNpcEntry(Player viewer, Map.Entry<Integer, AtomicLong> entry) {
		return npcName(viewer, entry.getKey()) + " (" + entry.getKey() + "): @gre@" + format(entry.getValue().get()) + " kills";
	}

	private static String formatItemEntry(Player viewer, Map.Entry<Integer, AtomicLong> entry) {
		return itemName(viewer, entry.getKey()) + " (" + entry.getKey() + "): @gre@" + format(entry.getValue().get());
	}

	private static String skillName(Player viewer, int skill) {
		try {
			SkillDef def = viewer.getWorld().getServer().getConstants().getSkills().getSkill(skill);
			return def == null || def.getLongName() == null ? "Skill " + skill : def.getLongName();
		} catch (Exception ex) {
			return "Skill " + skill;
		}
	}

	private static String npcName(Player viewer, int npcId) {
		NPCDef def = viewer.getWorld().getServer().getEntityHandler().getNpcDef(npcId);
		return def == null ? "NPC" : def.getName();
	}

	private static String itemName(Player viewer, int itemId) {
		ItemDefinition def = viewer.getWorld().getServer().getEntityHandler().getItemDef(itemId);
		return def == null ? "Item" : def.getName();
	}

	private static String durationSinceStart() {
		long seconds = Math.max(0, (System.currentTimeMillis() - reportStartedAtMillis.get()) / 1000);
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long remainingSeconds = seconds % 60;
		if (hours > 0) {
			return hours + "h " + minutes + "m";
		}
		if (minutes > 0) {
			return minutes + "m " + remainingSeconds + "s";
		}
		return remainingSeconds + "s";
	}

	private static String format(long value) {
		return String.format("%,d", value);
	}
}
