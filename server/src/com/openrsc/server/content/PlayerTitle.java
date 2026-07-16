package com.openrsc.server.content;

import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.MenuOptionListener;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.QuestInterface;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum PlayerTitle {
	MASTER_AT_ARMS("master_at_arms", "the Master-at-Arms", "Reach level 99 Attack.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.ATTACK),
	BULWARK("bulwark", "the Bulwark", "Reach level 99 Defence.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.DEFENSE),
	TITAN("titan", "the Titan", "Reach level 99 Strength.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.STRENGTH),
	DEATHLESS("deathless", "the Deathless", "Reach level 99 Hits.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.HITS),
	HAWKEYED("hawkeyed", "the Hawk-Eyed", "Reach level 99 Ranged.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.RANGED),
	HALLOWED("hallowed", "the Hallowed", "Reach level 99 Prayer.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.PRAYER),
	ARCHMAGE("archmage", "the Archmage", "Reach level 99 Magic.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.MAGIC),
	MASTER_CHEF("master_chef", "the Master Chef", "Reach level 99 Cooking.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.COOKING),
	TREEFELLER("treefeller", "the Treefeller", "Reach level 99 Woodcutting.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.WOODCUTTING),
	ARROWSMITH("arrowsmith", "the Arrowsmith", "Reach level 99 Fletching.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.FLETCHING),
	OLD_SALT("old_salt", "the Old Salt", "Reach level 99 Fishing.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.FISHING),
	FLAMEKEEPER("flamekeeper", "Keeper of the Flame", "Reach level 99 Firemaking.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.FIREMAKING),
	ARTIFICER("artificer", "the Artificer", "Reach level 99 Crafting.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.CRAFTING),
	FORGEMASTER("forgemaster", "the Forgemaster", "Reach level 99 Smithing.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.SMITHING),
	DEEPDELVER("deepdelver", "the Deepdelver", "Reach level 99 Mining.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.MINING),
	APOTHECARY("apothecary", "the Apothecary", "Reach level 99 Herblaw.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.HERBLAW),
	FLEET_FOOTED("fleet_footed", "the Fleet-Footed", "Reach level 99 Agility.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.AGILITY),
	LIGHT_FINGERED("light_fingered", "the Light-Fingered", "Reach level 99 Thieving.", Tier.RENOWN, RequirementType.SKILL_LEVEL, 99, Skill.THIEVING),
	JACK_OF_TRADES("jack_of_trades", "Jack of All Trades", "Reach total level 1,500.", Tier.RENOWN, RequirementType.TOTAL_LEVEL, 1500),
	MASTER_OF_ALL("master_of_all", "Master of All", "Reach total level 1,782.", Tier.RENOWN, RequirementType.TOTAL_LEVEL, 1782),
	APEX("apex", "the Apex", "Reach combat level 123.", Tier.RENOWN, RequirementType.COMBAT_LEVEL, 123),
	STORIED("storied", "the Storied", "Earn maximum quest points.", Tier.RENOWN, RequirementType.MAX_QUEST_POINTS),
	TEN_THOUSAND_BLADES("ten_thousand_blades", "Ten-Thousand Blades", "Defeat 10,000 NPCs.", Tier.RENOWN, RequirementType.NPC_KILLS_TOTAL, 10000),
	NEMESIS("nemesis", "the Nemesis", "Defeat the same NPC type 5,000 times.", Tier.RENOWN, RequirementType.NPC_KILLS_ANY, 5000),
	WIDOWMAKER("widowmaker", "the Widowmaker", "Defeat 100 players in the wilderness.", Tier.RENOWN, RequirementType.PLAYER_KILLS, 100),

	DRAGONSLAYER("dragonslayer", "the Dragonslayer", "Complete Dragon Slayer.", Tier.FEAT, RequirementType.QUEST_COMPLETE, Quests.DRAGON_SLAYER),
	HERO("hero", "the Hero", "Complete Hero's Quest.", Tier.FEAT, RequirementType.QUEST_COMPLETE, Quests.HEROS_QUEST),
	LEGEND("legend", "the Legend", "Complete Legends Quest.", Tier.FEAT, RequirementType.QUEST_COMPLETE, Quests.LEGENDS_QUEST),
	BLACK_KINGS_BANE("black_kings_bane", "the Black King's Bane", "Kill the King Black Dragon 100 times.", Tier.FEAT, RequirementType.NPC_KILLS_SET, 100, new int[] {477}),
	DEMONSBANE("demonsbane", "the Demonsbane", "Slay 666 demons.", Tier.FEAT, RequirementType.NPC_KILLS_SET, 666, new int[] {22, 35, 181, 184, 290, 315, 568, 857}),
	DRAGON_CROWNED("dragon_crowned", "Dragon-Crowned", "Receive a dragon medium helmet as your own drop.", Tier.FEAT, RequirementType.MANUAL),
	RUNESMITH("runesmith", "the Runesmith", "Smith a rune plate mail body.", Tier.FEAT, RequirementType.MANUAL),
	LOBSTER_BARON("lobster_baron", "the Lobster Baron", "Catch 5,000 lobsters.", Tier.FEAT, RequirementType.COUNTER, 5000, "title_lobsters"),
	UNBURNT("unburnt", "the Unburnt", "Cook 1,000 food in a row without burning.", Tier.FEAT, RequirementType.COUNTER, 1000, "title_cook_streak"),
	BONE_COLLECTOR("bone_collector", "the Bone Collector", "Bury 10,000 bones.", Tier.FEAT, RequirementType.COUNTER, 10000, "title_bones_buried"),
	COAL_HEARTED("coal_hearted", "the Coal-Hearted", "Mine 10,000 coal.", Tier.FEAT, RequirementType.COUNTER, 10000, "title_coal_mined"),
	GOLDSPINNER("goldspinner", "the Goldspinner", "Cast High Level Alchemy 10,000 times.", Tier.FEAT, RequirementType.COUNTER, 10000, "title_high_alchs"),
	FISHMONGER("fishmonger", "the Fishmonger", "Cook 25,000 swordfish.", Tier.FEAT, RequirementType.COUNTER, 25000, "title_swfish"),
	HEADHUNTER("headhunter", "the Headhunter", "Claim 250 Wilderness bounty marks.", Tier.FEAT, RequirementType.COUNTER, 250, "bounty_kills"),
	UNCAUGHT("uncaught", "the Uncaught", "Escape 100 Wilderness bounties placed on you.", Tier.FEAT, RequirementType.COUNTER, 100, "bounty_escapes"),
	KEYWARDEN("keywarden", "the Keywarden", "Open 500 Void Chests.", Tier.FEAT, RequirementType.COUNTER, 500, "title_void_chests"),
	UNTOUCHED("untouched", "the Untouched", "Reach combat level 90 without an unsafe death.", Tier.FEAT, RequirementType.ZERO_DEATH_COMBAT, 90),
	EDGELORD("edgelord", "the Edgelord", "Defeat 50 players in wilderness levels 1-5.", Tier.FEAT, RequirementType.COUNTER, 50, "title_edgeville_pks"),
	BRONZE_REAPER("bronze_reaper", "the Bronze Reaper", "Defeat a player of equal or higher combat level while wielding a bronze weapon.", Tier.FEAT, RequirementType.MANUAL),
	FOUNDER("founder", "the Founder", "Reserved for early Voidscape supporters.", Tier.FEAT, RequirementType.MANUAL),

	IMMORTAL("immortal", "the Immortal", "Reach total level 1,782 without an unsafe death.", Tier.SUPREME, RequirementType.ZERO_DEATH_TOTAL, 1782),
	VOIDTOUCHED("voidtouched", "the Voidtouched", "Receive all four Void chase items as your own NPC drops.", Tier.SUPREME, RequirementType.VOID_CHASE_DROPS),
	SCOURGE("scourge", "the Scourge", "Defeat 1,000 players in the Wilderness.", Tier.SUPREME, RequirementType.PLAYER_KILLS, 1000),
	MIDAS("midas", "the Midas", "Sell 25,000,000 gp through the Auction House.", Tier.SUPREME, RequirementType.COUNTER, 25_000_000, "title_ah_volume"),
	HUNTMASTER("huntmaster", "the Huntmaster", "Defeat every creature in the main-world hunting roster.", Tier.SUPREME, RequirementType.HUNTMASTER),
	SAINT("saint", "the Saint", "Reach level 99 Prayer and earn maximum quest points.", Tier.SUPREME,
		Position.PREFIX, "Saint", RequirementType.SAINT),
	KNIGHT("knight", "the Knight", "Reach 99 in every combat skill, combat level 123, and maximum quest points.", Tier.SUPREME,
		Position.PREFIX, "Knight", RequirementType.KNIGHT),

	TRAILBLAZER("trailblazer", "the Trailblazer", "First player on the server to reach level 99 in any skill.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.SKILL_LEVEL, 99),
	PARAGON("paragon", "the Paragon", "First player to reach max total level.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.TOTAL_LEVEL, 1782),
	LOREKEEPER("lorekeeper", "the Lorekeeper", "First player to complete every quest.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.MAX_QUEST_POINTS),
	WARLORD_WASTES("warlord_wastes", "Warlord of the Wastes", "Most wilderness kills this month, minimum 10.", Tier.UNIQUE,
		UniqueKind.CONTESTED, Position.PREFIX, "Warlord", RequirementType.MANUAL),
	MAGNATE("magnate", "the Magnate", "Highest auction-house sale volume this month, minimum 250,000 gp.", Tier.UNIQUE, UniqueKind.CONTESTED, RequirementType.MANUAL),
	FIRST_SWORDFISH("first_swfish", "the Swordfish Sovereign", "First 50,000 Swordfish Cooked.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_swfish"),
	FIRST_COAL("first_coal25k", "the Coal Pioneer", "First 25,000 Coal Mined.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 25000, "title_coal_mined"),
	FIRST_MAGIC_LOGS("first_mlogs", "the Elder Feller", "First 50,000 Magic Logs Cut.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_mlogs"),
	FIRST_HERBS("first_herbs", "the Herb Harvester", "First 25,000 Herbs Picked Up.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 25000, "title_herbs"),
	FIRST_GEMS("first_gems", "the Gemsetter", "First 10,000 Gems Cut.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 10000, "title_gems"),
	FIRST_TOTAL_1700("first_t1700", "the Seventeen-Hundred", "First Total Level 1700.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.TOTAL_LEVEL, 1700),
	FIRST_SPELLS("first_spells", "the Spellstorm", "First 50,000 Spells Cast.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_spells"),
	RELIC_KEEPER("relic_keeper", "the Relic-Keeper", "Hold the one-of-one Voidscape relic. Staff-granted until the relic exists.", Tier.UNIQUE, UniqueKind.ITEM_BOUND, RequirementType.MANUAL);

	public static final String ACTIVE_TITLE_CACHE = "player_title_active";
	public static final String ACTIVE_HONORIFIC_CACHE = "player_honorific_active";
	public static final int OVERHEAD_TITLE_CLIENT_VERSION = 10052;
	public static final int OVERHEAD_TITLE_TIER_CLIENT_VERSION = 10123;
	public static final int OVERHEAD_HONORIFIC_CLIENT_VERSION = 10137;
	public static final int WEAR_COST_COINS = 100_000;
	public static final String COUNTER_LOBSTERS = "title_lobsters";
	public static final String COUNTER_COOK_STREAK = "title_cook_streak";
	public static final String COUNTER_BONES_BURIED = "title_bones_buried";
	public static final String COUNTER_COAL_MINED = "title_coal_mined";
	public static final String COUNTER_HIGH_ALCHS = "title_high_alchs";
	public static final String COUNTER_EDGEVILLE_PKS = "title_edgeville_pks";
	public static final String COUNTER_SWORDFISH_COOKED = "title_swfish";
	public static final String COUNTER_MAGIC_LOGS = "title_mlogs";
	public static final String COUNTER_HERBS_PICKED_UP = "title_herbs";
	public static final String COUNTER_GEMS_CUT = "title_gems";
	public static final String COUNTER_SPELLS_CAST = "title_spells";
	public static final String COUNTER_AH_VOLUME = "title_ah_volume";
	public static final String COUNTER_VOID_CHESTS = "title_void_chests";
	public static final String COUNTER_UNSAFE_DEATHS = "title_unsafe_deaths";

	private static final String UNLOCK_CACHE_PREFIX = "pt_u_";
	private static final String LEGACY_UNLOCK_CACHE_PREFIX = "player_title_unlocked_";
	private static final String LEGACY_BLOCKED_UNIQUE_CACHE_PREFIX = "pt_b_";
	private static final String LEGACY_UNIQUE_CLAIM_CACHE = "pt_unique_claim";
	private static final String FIRST_DATE_CACHE_PREFIX = "pt_first_date_";
	private static final String CONTESTED_TOKEN_CACHE_PREFIX = "pt_ct_";
	private static final String CONTESTED_MONTH_CACHE_PREFIX = "pt_cm_";
	private static final String CONTESTED_SCORE_CACHE_PREFIX = "pt_cs_";
	private static final String CONTESTED_CROWNED_CACHE_PREFIX = "pt_cr_";
	private static final String LEGACY_CONTESTED_TOKEN_CACHE_PREFIX = "title_contested_token_";
	private static final String LEGACY_CONTESTED_MONTH_CACHE_PREFIX = "title_contested_month_";
	private static final String LEGACY_CONTESTED_SCORE_CACHE_PREFIX = "title_contested_score_";
	private static final String MIGRATION_CACHE = "pt_migration_10123";
	private static final String MIGRATION_V2_CACHE = "pt_migration_titles_core_v2";
	private static final String MIGRATION_FULL_V2_CACHE = "pt_migration_titles_full_v2";
	// Culled, rescoped, and minigame-backed ids whose pre-core-overhaul unlocks are revoked.
	private static final String[] MIGRATION_V2_REVOKED_IDS = {
		"first_agil30", "giant_killer", "bronze_reaper", "gnome_baller", "voidbane",
		"gravewalker", "colossusbane", "void_arena_champion", "riftbound"
	};
	private static final String PROMPT_QUEUE_CACHE_PREFIX = "pt_q_";
	private static final String LEGACY_PROMPT_QUEUE_CACHE_PREFIX = "title_promptq_";
	private static final String PROMPT_STATE_ATTRIBUTE = "title_prompt_state";
	private static final String PROMPT_BACKOFF_ATTRIBUTE = "title_prompt_backoff";
	private static final int PROMPT_TIMEOUT_TICKS = 50;
	// After an unanswered prompt times out, wait this long before re-offering so an
	// idle player is not nagged every timeout interval. Combat/menu resets skip this.
	private static final int PROMPT_BACKOFF_TICKS = 500;
	private static final int WARLORD_MIN_MONTHLY_KILLS = 10;
	private static final int MAGNATE_MIN_MONTHLY_VOLUME = 250_000;
	private static final int MAX_PLAYER_CACHE_KEY_LENGTH = 32;
	private static final int[] VOID_CHASE_ITEM_IDS = {
		ItemId.VOID_AMULET.id(), ItemId.VOID_MACE.id(), ItemId.VOID_SCIMITAR.id(), ItemId.VOID_BOW.id()
	};
	private static final Skill[] KNIGHT_COMBAT_SKILLS = {
		Skill.ATTACK, Skill.DEFENSE, Skill.STRENGTH, Skill.HITS,
		Skill.RANGED, Skill.PRAYER, Skill.MAGIC
	};
	// Stable Preservation-14 main-world roster. It deliberately excludes every custom
	// arena, instance, event, bot, and release-gated spawn file, plus quest-only kills
	// such as Delrith that intentionally bypass the persistent NPC kill cache.
	private static final int[] HUNTMASTER_NPC_IDS = {
		0, 3, 4, 5, 6, 8, 11, 19, 21, 22, 23, 25, 29, 34, 37, 40,
		41, 45, 46, 47, 52, 53, 57, 60, 61, 62, 63, 64, 65, 66, 67, 68,
		70, 72, 74, 76, 78, 79, 80, 81, 86, 89, 93, 94, 99, 100, 102,
		104, 108, 109, 114, 127, 135, 136, 137, 139, 140, 153, 154
	};

	private final String id;
	private final String displayName;
	private final String unlockHint;
	private final Tier tier;
	private final UniqueKind uniqueKind;
	private final Position position;
	private final String prefixForm;
	private final RequirementType requirementType;
	private final int threshold;
	private final Skill skill;
	private final int[] npcIds;
	private final String counterKey;

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, null, Position.SUFFIX, null, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold) {
		this(id, displayName, unlockHint, tier, null, Position.SUFFIX, null, requirementType, threshold, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, Skill skill) {
		this(id, displayName, unlockHint, tier, null, Position.SUFFIX, null, requirementType, threshold, skill, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, int[] npcIds) {
		this(id, displayName, unlockHint, tier, null, Position.SUFFIX, null, requirementType, threshold, null, npcIds, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, String counterKey) {
		this(id, displayName, unlockHint, tier, null, Position.SUFFIX, null, requirementType, threshold, null, null, counterKey);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, Position position,
				String prefixForm, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, null, position, prefixForm, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, uniqueKind, Position.SUFFIX, null, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold) {
		this(id, displayName, unlockHint, tier, uniqueKind, Position.SUFFIX, null, requirementType, threshold, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold, Skill skill) {
		this(id, displayName, unlockHint, tier, uniqueKind, Position.SUFFIX, null, requirementType, threshold, skill, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold, String counterKey) {
		this(id, displayName, unlockHint, tier, uniqueKind, Position.SUFFIX, null, requirementType, threshold, null, null, counterKey);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind,
				Position position, String prefixForm, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, uniqueKind, position, prefixForm, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType,
				int threshold, Skill skill, int[] npcIds, String counterKey) {
		this(id, displayName, unlockHint, tier, uniqueKind, Position.SUFFIX, null, requirementType,
			threshold, skill, npcIds, counterKey);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind,
				Position position, String prefixForm, RequirementType requirementType,
				int threshold, Skill skill, int[] npcIds, String counterKey) {
		this.id = id;
		this.displayName = displayName;
		this.unlockHint = unlockHint;
		this.tier = tier;
		this.uniqueKind = uniqueKind;
		this.position = position == null ? Position.SUFFIX : position;
		this.prefixForm = prefixForm == null ? "" : prefixForm;
		this.requirementType = requirementType;
		this.threshold = threshold;
		this.skill = skill;
		this.npcIds = npcIds;
		this.counterKey = counterKey;
	}

	public String id() {
		return id;
	}

	public String displayName() {
		return displayName;
	}

	public String unlockHint() {
		return unlockHint;
	}

	public Tier tier() {
		return tier;
	}

	public String tierLabel() {
		return tier.label;
	}

	public String tierColorToken() {
		return tier.chatColor;
	}

	public UniqueKind uniqueKind() {
		return uniqueKind;
	}

	public Position position() {
		return position;
	}

	public boolean honorific() {
		return position == Position.PREFIX;
	}

	public String prefixForm() {
		return prefixForm;
	}

	public int prestigeRank() {
		return tier.prestigeRank;
	}

	public boolean unique() {
		return tier == Tier.UNIQUE;
	}

	public boolean contested() {
		return uniqueKind == UniqueKind.CONTESTED;
	}

	public boolean firstUnique() {
		return uniqueKind == UniqueKind.FIRST;
	}

	public String catalogHint() {
		switch (requirementType) {
			case TOTAL_LEVEL:
				return "Total " + formatNumber(threshold) + ".";
			case COMBAT_LEVEL:
				return "Combat " + threshold + ".";
			case PLAYER_KILLS:
				return threshold + " Wilderness PKs.";
			case NPC_KILLS_TOTAL:
				return formatNumber(threshold) + " NPC kills.";
			case NPC_KILLS_ANY:
				return formatNumber(threshold) + " kills on one NPC type.";
			case NPC_KILLS_SET:
				return formatNumber(threshold) + " matching NPC kills.";
			case SKILL_LEVEL:
				return skill == null ? "Any skill " + threshold + "." : "Level " + threshold + " " + skillName(skill) + ".";
			case QUEST_COMPLETE:
				return "Complete quest.";
			case MAX_QUEST_POINTS:
				return "Maximum quest points.";
			case COUNTER:
				return formatNumber(threshold) + " tracked actions.";
			case ZERO_DEATH_COMBAT:
				return "Combat " + threshold + ", zero unsafe deaths.";
			case ZERO_DEATH_TOTAL:
				return "Total " + formatNumber(threshold) + ", zero unsafe deaths.";
			case VOID_CHASE_DROPS:
				return "All four Void chase drops.";
			case HUNTMASTER:
				return HUNTMASTER_NPC_IDS.length + " main-world creature types.";
			case SAINT:
				return "99 Prayer and maximum quest points.";
			case KNIGHT:
				return "All 7 combat skills 99, combat 123, and maximum quest points.";
			case MANUAL:
			default:
				return unlockHint;
		}
	}

	public boolean recordTitle() {
		return firstUnique() && id.startsWith("first_");
	}

	public String tableTitle() {
		if (recordTitle()) {
			return trimTrailingPeriod(unlockHint);
		}
		return displayName;
	}

	public String tableTierLabel() {
		return recordTitle() ? "Legend" : titleCase(tierLabel());
	}

	public String tableHolder(Player player) {
		if (!unique()) {
			return isUnlocked(player) ? player.getUsername() : "-";
		}
		String owner = ownerName(player, this);
		return owner == null ? "Open" : owner;
	}

	public String tableAge(Player player) {
		if (!firstUnique()) {
			return contested() ? "season" : "-";
		}
		long epochSeconds = firstClaimEpochSeconds(player, this);
		if (epochSeconds <= 0) {
			return "open";
		}
		long days = Math.max(0L, (System.currentTimeMillis() / 1000L - epochSeconds) / 86400L);
		if (days == 0) {
			return "today";
		}
		return days + "d ago";
	}

	public String requirementProgress(Player player) {
		if (player == null) {
			return "Progress: unknown.";
		}

		switch (requirementType) {
			case TOTAL_LEVEL:
				return "Progress: " + formatNumber(player.getTotalLevel()) + "/" + formatNumber(threshold) + " total level.";
			case COMBAT_LEVEL:
				return "Progress: " + player.getCombatLevel() + "/" + threshold + " combat level.";
			case PLAYER_KILLS:
				return "Progress: " + formatNumber(player.getKills()) + "/" + formatNumber(threshold) + " Wilderness PKs.";
			case NPC_KILLS_TOTAL:
				return "Progress: " + formatNumber(totalNpcKills(player)) + "/" + formatNumber(threshold) + " NPC kills.";
			case NPC_KILLS_ANY:
				return "Progress: " + formatNumber(highestSingleNpcKillCount(player)) + "/" + formatNumber(threshold) + " on one NPC type.";
			case NPC_KILLS_SET:
				return "Progress: " + formatNumber(npcKillsInSet(player)) + "/" + formatNumber(threshold) + " matching NPC kills.";
			case SKILL_LEVEL:
				if (skill == null) {
					return "Progress: " + highestSkillLevel(player) + "/" + threshold + " highest skill.";
				}
				return "Progress: " + currentSkillLevel(player, skill) + "/" + threshold + " " + skillName(skill) + ".";
			case QUEST_COMPLETE:
				return player.getQuestStage(threshold) == Quests.QUEST_STAGE_COMPLETED ? "Progress: complete." : "Progress: incomplete.";
			case MAX_QUEST_POINTS:
				return "Progress: " + player.getQuestPoints() + "/" + maxQuestPoints(player) + " quest points.";
			case COUNTER:
				return "Progress: " + formatNumber(counterValue(player, counterKey)) + "/" + formatNumber(threshold) + ".";
			case ZERO_DEATH_COMBAT:
				return "Progress: combat " + player.getCombatLevel() + "/" + threshold
					+ ", unsafe deaths " + unsafeDeathCount(player) + ".";
			case ZERO_DEATH_TOTAL:
				return "Progress: total " + formatNumber(player.getTotalLevel()) + "/" + formatNumber(threshold)
					+ ", unsafe deaths " + unsafeDeathCount(player) + ".";
			case VOID_CHASE_DROPS:
				return "Progress: " + voidChaseDropCount(player) + "/" + VOID_CHASE_ITEM_IDS.length + " own NPC drops.";
			case HUNTMASTER:
				return "Progress: " + huntmasterKillCount(player) + "/" + HUNTMASTER_NPC_IDS.length + " creature types.";
			case SAINT:
				return "Progress: Prayer " + currentSkillLevel(player, Skill.PRAYER) + "/99, quest points "
					+ player.getQuestPoints() + "/" + maxQuestPoints(player) + ".";
			case KNIGHT:
				return "Progress: combat skills " + knightCombatSkillsAt99(player) + "/"
					+ KNIGHT_COMBAT_SKILLS.length + " at 99" + knightMissingSkills(player)
					+ ", combat " + player.getCombatLevel() + "/123, quest points "
					+ player.getQuestPoints() + "/" + maxQuestPoints(player) + ".";
			case MANUAL:
			default:
				return "Progress: awarded by event or staff.";
		}
	}

	public boolean automatic() {
		return requirementType != RequirementType.MANUAL && uniqueKind != UniqueKind.CONTESTED && uniqueKind != UniqueKind.ITEM_BOUND;
	}

	public String cacheKey() {
		return UNLOCK_CACHE_PREFIX + id;
	}

	public String legacyCacheKey() {
		return LEGACY_UNLOCK_CACHE_PREFIX + id;
	}

	public boolean isUnlocked(Player player) {
		migratePlayerCache(player);
		if (player == null || !hasUnlockCache(player)) {
			return false;
		}
		if (uniqueKind == UniqueKind.CONTESTED) {
			return ownsCurrentContestedToken(player, this);
		}
		return true;
	}

	private boolean hasUnlockCache(Player player) {
		return player != null && (player.getCache().hasKey(cacheKey()) || player.getCache().hasKey(legacyCacheKey()));
	}

	private boolean qualifies(Player player) {
		if (player == null) {
			return false;
		}

		switch (requirementType) {
			case TOTAL_LEVEL:
				return player.getTotalLevel() >= threshold;
			case COMBAT_LEVEL:
				return player.getCombatLevel() >= threshold;
			case PLAYER_KILLS:
				return player.getKills() >= threshold;
			case NPC_KILLS_TOTAL:
				return totalNpcKills(player) >= threshold;
			case NPC_KILLS_ANY:
				return highestSingleNpcKillCount(player) >= threshold;
			case NPC_KILLS_SET:
				return npcKillsInSet(player) >= threshold;
			case SKILL_LEVEL:
				return skill == null ? highestSkillLevel(player) >= threshold : hasSkillLevel(player, skill, threshold);
			case QUEST_COMPLETE:
				return player.getQuestStage(threshold) == Quests.QUEST_STAGE_COMPLETED;
			case MAX_QUEST_POINTS:
				return maxQuestPoints(player) > 0 && player.getQuestPoints() >= maxQuestPoints(player);
			case COUNTER:
				return counterValue(player, counterKey) >= threshold;
			case ZERO_DEATH_COMBAT:
				return player.getCombatLevel() >= threshold && unsafeDeathCount(player) == 0;
			case ZERO_DEATH_TOTAL:
				return player.getTotalLevel() >= threshold && unsafeDeathCount(player) == 0;
			case VOID_CHASE_DROPS:
				return voidChaseDropCount(player) == VOID_CHASE_ITEM_IDS.length;
			case HUNTMASTER:
				return huntmasterKillCount(player) == HUNTMASTER_NPC_IDS.length;
			case SAINT:
				return currentSkillLevel(player, Skill.PRAYER) >= 99
					&& maxQuestPoints(player) > 0 && player.getQuestPoints() >= maxQuestPoints(player);
			case KNIGHT:
				return knightCombatSkillsAt99(player) == KNIGHT_COMBAT_SKILLS.length
					&& player.getCombatLevel() >= 123
					&& maxQuestPoints(player) > 0 && player.getQuestPoints() >= maxQuestPoints(player);
			case MANUAL:
			default:
				return false;
		}
	}

	public static synchronized boolean unlock(Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (player == null || title == null) {
			return false;
		}
		if (title.uniqueKind == UniqueKind.CONTESTED) {
			return grantContested(player.getWorld(), player, title);
		}
		if (title.isUnlocked(player)) {
			return false;
		}
		if (title.unique()) {
			String owner = ownerName(player, title);
			if (owner != null && !owner.equalsIgnoreCase(player.getUsername())) {
				return false;
			}
		}

		player.getCache().store(title.cacheKey(), true);
		if (title.firstUnique()) {
			stampFirstClaimDate(player, title);
		}
		if (title == SAINT || title == KNIGHT) {
			stampHonorificDate(player, title);
		}
		player.message("@mag@Title unlocked: " + title.tierColorToken() + title.displayName() + "@whi@.");
		enqueuePrompt(player, title);
		if (title == SAINT) {
			player.getWorld().getWorldAnnouncementService().announceSainthood(player);
		} else if (title.tier == Tier.SUPREME) {
			player.getWorld().getWorldAnnouncementService().announceSupremeTitleClaim(player, title.displayName());
		} else if (title.unique()) {
			announceUniqueClaim(player, title);
		}
		return true;
	}

	public static synchronized boolean revoke(Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (player == null || title == null || !title.hasUnlockCache(player)) {
			return false;
		}
		player.getCache().remove(title.cacheKey(), title.legacyCacheKey(), promptQueueKey(title));
		if (title == SAINT || title == KNIGHT) {
			player.getCache().remove(FIRST_DATE_CACHE_PREFIX + title.id());
		}
		clearTitleFromActiveSlots(player, title);
		player.message("@mag@Title revoked: " + title.tierColorToken() + title.displayName() + "@whi@.");
		return true;
	}

	public static synchronized boolean grantContested(World world, Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (world == null || player == null || title == null || title.uniqueKind != UniqueKind.CONTESTED) {
			return false;
		}

		String previousOwner = ownerName(player, title);
		if (previousOwner != null && previousOwner.equalsIgnoreCase(player.getUsername()) && title.isUnlocked(player)) {
			return false;
		}

		int token = nextContestedToken(world, title);
		player.getCache().set(title.cacheKey(), token);
		player.getUpdateFlags().setAppearanceChanged(true);
		player.message("@mag@Contested title awarded: " + title.tierColorToken() + title.displayName() + "@whi@.");
		enqueuePrompt(player, title);

		Player previous = onlinePlayerByName(world, previousOwner);
		if (previous != null && previous.getUsernameHash() != player.getUsernameHash()) {
			previous.getCache().remove(title.cacheKey());
			clearTitleFromActiveSlots(previous, title);
			previous.message("@mag@The contested title " + title.tierColorToken() + title.displayName() + "@whi@ has changed hands.");
		}
		return true;
	}

	public static synchronized boolean grantContested(World world, int playerId, PlayerTitle title) {
		if (world == null || playerId <= 0 || title == null || title.uniqueKind != UniqueKind.CONTESTED) {
			return false;
		}
		Player onlinePlayer = world.getPlayerID(playerId);
		if (onlinePlayer != null) {
			return grantContested(world, onlinePlayer, title);
		}
		int token = nextContestedToken(world, title);
		try {
			world.getServer().getDatabase().querySavePlayerCacheValue(playerId, 0, title.cacheKey(), String.valueOf(token));
			world.getServer().getDatabase().querySavePlayerCacheValue(playerId, 0, promptQueueKey(title), "1");
			return true;
		} catch (GameDatabaseException ex) {
			return false;
		}
	}

	public static int refreshAutomaticUnlocks(Player player) {
		migratePlayerCache(player);
		if (player == null) {
			return 0;
		}
		int unlocked = 0;
		for (PlayerTitle title : values()) {
			if (title.automatic() && !title.isUnlocked(player) && title.qualifies(player)) {
				if (unlock(player, title)) {
					unlocked++;
				}
			}
		}
		return unlocked;
	}

	public static int incrementCounter(Player player, String counterKey) {
		return incrementCounter(player, counterKey, 1);
	}

	public static int incrementCounter(Player player, String counterKey, int amount) {
		if (player == null || counterKey == null || counterKey.isEmpty() || amount <= 0) {
			return 0;
		}
		int value = counterValue(player, counterKey) + amount;
		player.getCache().set(counterKey, value);
		checkCounterTitles(player, counterKey);
		return value;
	}

	public static void recordSwordfishCooked(Player player, int itemId) {
		if (itemId == ItemId.SWORDFISH.id()) {
			incrementCounter(player, COUNTER_SWORDFISH_COOKED);
		}
	}

	public static void recordMagicLogsCut(Player player, int itemId) {
		if (itemId == ItemId.MAGIC_LOGS.id()) {
			incrementCounter(player, COUNTER_MAGIC_LOGS);
		}
	}

	public static void recordGemCut(Player player) {
		incrementCounter(player, COUNTER_GEMS_CUT);
	}

	public static void recordSpellCast(Player player) {
		incrementCounter(player, COUNTER_SPELLS_CAST);
	}

	public static void recordHerbPickup(Player player, int itemId, int amount) {
		if (amount > 0 && isHerb(itemId)) {
			incrementCounter(player, COUNTER_HERBS_PICKED_UP, amount);
		}
	}

	public static void resetCounter(Player player, String counterKey) {
		if (player != null && counterKey != null && !counterKey.isEmpty()) {
			player.getCache().set(counterKey, 0);
		}
	}

	public static void recordUnsafeDeath(Player player) {
		incrementCounter(player, COUNTER_UNSAFE_DEATHS);
	}

	public static void recordVoidNpcDrop(Player player, int itemId) {
		if (player == null || !isVoidChaseItem(itemId)) {
			return;
		}
		player.getCache().store(voidDropCacheKey(itemId), true);
		if (!VOIDTOUCHED.isUnlocked(player) && VOIDTOUCHED.qualifies(player)) {
			unlock(player, VOIDTOUCHED);
		}
	}

	public static void recordHuntmasterKill(Player player, Npc npc) {
		if (player == null || npc == null || !isHuntmasterNpc(npc.getID())) {
			return;
		}
		if (!HUNTMASTER.isUnlocked(player) && HUNTMASTER.qualifies(player)) {
			unlock(player, HUNTMASTER);
		}
	}

	public static int recordWildernessPlayerKill(Player killer, Player killed) {
		if (killer == null || killed == null || !killed.getLocation().inWilderness()) {
			return 0;
		}

		int month = currentMonth();
		String monthlyCounter = monthlyPkCounterKey(month);
		int monthlyKills = counterValue(killer, monthlyCounter) + 1;
		killer.getCache().set(monthlyCounter, monthlyKills);

		int wildernessLevel = Math.max(1, killed.getLocation().wildernessLevel());
		if (wildernessLevel <= 5) {
			incrementCounter(killer, COUNTER_EDGEVILLE_PKS);
		}

		updateWarlord(killer, month, monthlyKills);
		return monthlyKills;
	}

	public static void recordAuctionHouseSale(Player context, int sellerId, int grossAmount) {
		if (context == null || sellerId <= 0 || grossAmount <= 0) {
			return;
		}

		Player seller = context.getWorld().getPlayerID(sellerId);
		if (seller != null) {
			incrementCounter(seller, COUNTER_AH_VOLUME, grossAmount);
		} else {
			try {
				context.getWorld().getServer().getDatabase()
					.queryIncrementPlayerCacheInt(sellerId, 0, COUNTER_AH_VOLUME, grossAmount);
			} catch (GameDatabaseException ignored) {
				// The committed sale remains authoritative; login catch-up can only use
				// progress that was durably recorded here.
			}
		}

		int month = currentMonth();
		int sellerVolume;
		try {
			sellerVolume = context.getWorld().getServer().getDatabase()
				.getAuctionSellerVolumeSince(sellerId, monthStartEpochSeconds());
		} catch (GameDatabaseException ex) {
			return;
		}
		updateMagnate(context, month, sellerId, sellerVolume);
	}

	public static void checkRunePlateSmith(Player player) {
		unlock(player, RUNESMITH);
	}

	public static void checkDragonMediumDrop(Player player) {
		unlock(player, DRAGON_CROWNED);
	}

	public static void checkBronzeWeaponPlayerKill(Player killer, Player victim, boolean wieldingBronzeWeapon) {
		if (wieldingBronzeWeapon && killer != null && victim != null
			&& victim.getCombatLevel() >= killer.getCombatLevel()) {
			unlock(killer, BRONZE_REAPER);
		}
	}

	public static List<PlayerTitle> unlocked(Player player) {
		migratePlayerCache(player);
		List<PlayerTitle> unlocked = new ArrayList<>();
		if (player == null) {
			return unlocked;
		}
		for (PlayerTitle title : values()) {
			if (title.isUnlocked(player)) {
				unlocked.add(title);
			}
		}
		return unlocked;
	}

	public static int unlockedCount(Player player) {
		migratePlayerCache(player);
		int count = 0;
		if (player == null) {
			return count;
		}
		for (PlayerTitle title : values()) {
			if (title.isUnlocked(player)) {
				count++;
			}
		}
		return count;
	}

	public static PlayerTitle active(Player player) {
		migratePlayerCache(player);
		if (player == null || !player.getCache().hasKey(ACTIVE_TITLE_CACHE)) {
			return null;
		}

		PlayerTitle title = byId(player.getCache().getString(ACTIVE_TITLE_CACHE));
		if (title == null || title.honorific() || !title.isUnlocked(player)) {
			if (title != null && title.honorific() && title.isUnlocked(player)) {
				player.getCache().store(ACTIVE_HONORIFIC_CACHE, title.id());
			}
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
			return null;
		}
		return title;
	}

	public static PlayerTitle activeHonorific(Player player) {
		migratePlayerCache(player);
		if (player == null || !player.getCache().hasKey(ACTIVE_HONORIFIC_CACHE)) {
			return null;
		}

		PlayerTitle title = byId(player.getCache().getString(ACTIVE_HONORIFIC_CACHE));
		if (title == null || !title.honorific() || !title.isUnlocked(player)) {
			player.getCache().remove(ACTIVE_HONORIFIC_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
			return null;
		}
		return title;
	}

	public static String activeOverhead(Player player) {
		PlayerTitle title = active(player);
		return title == null ? "" : title.displayName();
	}

	public static int activeOverheadTier(Player player) {
		PlayerTitle title = active(player);
		return title == null ? 0 : title.tier.code;
	}

	public static String activeHonorificOverhead(Player player) {
		PlayerTitle title = activeHonorific(player);
		return title == null ? "" : title.prefixForm;
	}

	public static int activeHonorificOverheadTier(Player player) {
		PlayerTitle title = activeHonorific(player);
		return title == null ? 0 : title.tier.code;
	}

	public static boolean isActive(Player player, PlayerTitle title) {
		return title != null && (active(player) == title || activeHonorific(player) == title);
	}

	public static String styledName(Player player) {
		if (player == null) {
			return "";
		}
		String prefix = activeHonorificOverhead(player);
		String suffix = activeOverhead(player);
		String base = prefix.isEmpty() ? player.getUsername() : prefix + " " + player.getUsername();
		String suffixJoin = suffix.regionMatches(true, 0, "the ", 0, 4) ? " " : ", ";
		String styled = suffix.isEmpty() ? base : base + suffixJoin + suffix;
		return styled.length() > 40 && !suffix.isEmpty() ? base : styled;
	}

	public static synchronized WearResult tryWear(Player player, PlayerTitle title) {
		if (player == null || title == null) {
			return WearResult.NOT_UNLOCKED;
		}
		migratePlayerCache(player);
		if (!title.isUnlocked(player)) {
			return WearResult.NOT_UNLOCKED;
		}
		if (isActive(player, title)) {
			return WearResult.ALREADY_ACTIVE;
		}
		if (player.getCarriedItems() == null || player.getCarriedItems().getInventory() == null) {
			return WearResult.PAYMENT_FAILED;
		}
		if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) < WEAR_COST_COINS) {
			return WearResult.INSUFFICIENT_COINS;
		}
		if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), WEAR_COST_COINS)) == -1) {
			return WearResult.PAYMENT_FAILED;
		}

		setActive(player, title);
		player.requestPersistentSave();
		return WearResult.WORN;
	}

	private static void setActive(Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (player == null) {
			return;
		}
		if (title == null) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
			return;
		}
		String targetCache = title.honorific() ? ACTIVE_HONORIFIC_CACHE : ACTIVE_TITLE_CACHE;
		String otherCache = title.honorific() ? ACTIVE_TITLE_CACHE : ACTIVE_HONORIFIC_CACHE;
		if (!title.isUnlocked(player)) {
			player.getCache().remove(targetCache);
			player.getUpdateFlags().setAppearanceChanged(true);
			return;
		}
		if (player.getCache().hasKey(otherCache)
			&& title.id().equalsIgnoreCase(player.getCache().getString(otherCache))) {
			player.getCache().remove(otherCache);
		}
		player.getCache().store(targetCache, title.id());
		player.getUpdateFlags().setAppearanceChanged(true);
	}

	public static void clearActiveTitle(Player player) {
		migratePlayerCache(player);
		if (player == null) {
			return;
		}
		boolean changed = player.getCache().hasKey(ACTIVE_TITLE_CACHE);
		player.getCache().remove(ACTIVE_TITLE_CACHE);
		player.getUpdateFlags().setAppearanceChanged(true);
		if (changed) {
			player.requestPersistentSave();
		}
	}

	public static void clearActiveHonorific(Player player) {
		migratePlayerCache(player);
		if (player == null) {
			return;
		}
		boolean changed = player.getCache().hasKey(ACTIVE_HONORIFIC_CACHE);
		player.getCache().remove(ACTIVE_HONORIFIC_CACHE);
		player.getUpdateFlags().setAppearanceChanged(true);
		if (changed) {
			player.requestPersistentSave();
		}
	}

	public static void clearActive(Player player) {
		migratePlayerCache(player);
		if (player == null) {
			return;
		}
		boolean changed = player.getCache().hasKey(ACTIVE_TITLE_CACHE)
			|| player.getCache().hasKey(ACTIVE_HONORIFIC_CACHE);
		player.getCache().remove(ACTIVE_TITLE_CACHE, ACTIVE_HONORIFIC_CACHE);
		player.getUpdateFlags().setAppearanceChanged(true);
		if (changed) {
			player.requestPersistentSave();
		}
	}

	private static void clearTitleFromActiveSlots(Player player, PlayerTitle title) {
		if (player == null || title == null) {
			return;
		}
		boolean changed = false;
		if (player.getCache().hasKey(ACTIVE_TITLE_CACHE)
			&& title.id().equalsIgnoreCase(player.getCache().getString(ACTIVE_TITLE_CACHE))) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			changed = true;
		}
		if (player.getCache().hasKey(ACTIVE_HONORIFIC_CACHE)
			&& title.id().equalsIgnoreCase(player.getCache().getString(ACTIVE_HONORIFIC_CACHE))) {
			player.getCache().remove(ACTIVE_HONORIFIC_CACHE);
			changed = true;
		}
		if (changed) {
			player.getUpdateFlags().setAppearanceChanged(true);
		}
	}

	public static String ownerName(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.unique()) {
			return null;
		}

		String onlineOwner = onlineUniqueOwnerName(player.getWorld(), title);
		return onlineOwner != null ? onlineOwner : databaseUniqueOwnerName(player, title);
	}

	public static boolean isClaimedByOther(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.unique()) {
			return false;
		}
		String owner = ownerName(player, title);
		return owner != null && !owner.equalsIgnoreCase(player.getUsername());
	}

	public static String firstClaimDate(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.firstUnique()) {
			return "";
		}
		long epochSeconds = firstClaimEpochSeconds(player, title);
		return epochSeconds <= 0 ? "" : new SimpleDateFormat("yyyy-MM-dd").format(new Date(epochSeconds * 1000L));
	}

	public static String honorificDate(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.honorific()) {
			return "";
		}
		long epochSeconds = cacheLong(player, FIRST_DATE_CACHE_PREFIX + title.id());
		return formatEpochDate(epochSeconds);
	}

	public static String formatEpochDate(long epochSeconds) {
		return epochSeconds <= 0 ? "" : new SimpleDateFormat("yyyy-MM-dd").format(new Date(epochSeconds * 1000L));
	}

	public static String honorificDateCacheKey(PlayerTitle title) {
		return title == null ? "" : FIRST_DATE_CACHE_PREFIX + title.id();
	}

	private static long firstClaimEpochSeconds(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.firstUnique()) {
			return 0L;
		}
		long epochSeconds = loadGlobalLong(player, FIRST_DATE_CACHE_PREFIX + title.id());
		if (epochSeconds <= 0 && player.getCache().hasKey(FIRST_DATE_CACHE_PREFIX + title.id())) {
			epochSeconds = cacheLong(player, FIRST_DATE_CACHE_PREFIX + title.id());
		}
		return epochSeconds;
	}

	public static int currentContestedScore(Player player, PlayerTitle title) {
		if (player == null || title == null || title.uniqueKind != UniqueKind.CONTESTED) {
			return 0;
		}
		if (title == WARLORD_WASTES) {
			int month = currentMonth();
			if (loadContestedGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX,
				LEGACY_CONTESTED_MONTH_CACHE_PREFIX, title) != month) {
				return 0;
			}
			return loadContestedGlobalInt(player, CONTESTED_SCORE_CACHE_PREFIX,
				LEGACY_CONTESTED_SCORE_CACHE_PREFIX, title);
		}
		if (title == MAGNATE) {
			int month = currentMonth();
			if (loadContestedGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX,
				LEGACY_CONTESTED_MONTH_CACHE_PREFIX, title) != month) {
				return 0;
			}
			return loadContestedGlobalInt(player, CONTESTED_SCORE_CACHE_PREFIX,
				LEGACY_CONTESTED_SCORE_CACHE_PREFIX, title);
		}
		return 0;
	}

	public static PlayerTitle byId(String id) {
		if (id == null) {
			return null;
		}
		String normalized = normalize(id);
		for (PlayerTitle title : values()) {
			if (title.id.equalsIgnoreCase(id)
				|| normalize(title.id).equals(normalized)
				|| normalize(title.displayName).equals(normalized)
				|| normalize(trimLeadingThe(title.displayName)).equals(normalized)) {
				return title;
			}
		}
		return null;
	}

	private static void checkCounterTitles(Player player, String counterKey) {
		for (PlayerTitle title : values()) {
			if (title.requirementType == RequirementType.COUNTER
				&& counterKey.equals(title.counterKey)
				&& !title.isUnlocked(player)
				&& title.qualifies(player)) {
				unlock(player, title);
			}
		}
	}

	private static void updateWarlord(Player killer, int month, int monthlyKills) {
		int recordedMonth = loadContestedGlobalInt(killer, CONTESTED_MONTH_CACHE_PREFIX,
			LEGACY_CONTESTED_MONTH_CACHE_PREFIX, WARLORD_WASTES);
		announceClosedContestedPeriod(killer, WARLORD_WASTES, recordedMonth, month);
		if (monthlyKills < WARLORD_MIN_MONTHLY_KILLS) {
			return;
		}
		boolean firstClaimThisMonth = recordedMonth != month;
		int leaderScore = firstClaimThisMonth ? 0 : loadContestedGlobalInt(killer,
			CONTESTED_SCORE_CACHE_PREFIX, LEGACY_CONTESTED_SCORE_CACHE_PREFIX, WARLORD_WASTES);
		if (monthlyKills < leaderScore) {
			return;
		}
		String owner = firstClaimThisMonth ? null : ownerName(killer, WARLORD_WASTES);
		if (monthlyKills == leaderScore && owner != null && !owner.equalsIgnoreCase(killer.getUsername())) {
			return;
		}
		if (firstClaimThisMonth || owner == null || !owner.equalsIgnoreCase(killer.getUsername())) {
			if (!grantContested(killer.getWorld(), killer, WARLORD_WASTES)) {
				return;
			}
		}
		saveGlobalInt(killer, CONTESTED_MONTH_CACHE_PREFIX + WARLORD_WASTES.id(), month);
		saveGlobalInt(killer, CONTESTED_SCORE_CACHE_PREFIX + WARLORD_WASTES.id(), monthlyKills);
		if (firstClaimThisMonth) {
			killer.getWorld().getWorldAnnouncementService().announceContestedOfficeClaim(
				killer.getUsername(), WARLORD_WASTES.displayName(), WARLORD_WASTES.prefixForm());
		}
	}

	private static void updateMagnate(Player context, int month, int sellerId, int sellerVolume) {
		int recordedMonth = loadContestedGlobalInt(context, CONTESTED_MONTH_CACHE_PREFIX,
			LEGACY_CONTESTED_MONTH_CACHE_PREFIX, MAGNATE);
		announceClosedContestedPeriod(context, MAGNATE, recordedMonth, month);
		if (sellerVolume <= 0) {
			return;
		}
		if (sellerVolume < MAGNATE_MIN_MONTHLY_VOLUME) {
			return;
		}
		boolean firstClaimThisMonth = recordedMonth != month;
		int leaderScore = firstClaimThisMonth ? 0 : loadContestedGlobalInt(context,
			CONTESTED_SCORE_CACHE_PREFIX, LEGACY_CONTESTED_SCORE_CACHE_PREFIX, MAGNATE);
		if (sellerVolume < leaderScore) {
			return;
		}
		String sellerName = playerName(context, sellerId);
		String owner = firstClaimThisMonth ? null : ownerName(context, MAGNATE);
		if (sellerVolume == leaderScore && owner != null && (sellerName == null || !owner.equalsIgnoreCase(sellerName))) {
			return;
		}
		if (firstClaimThisMonth || owner == null || sellerName == null || !owner.equalsIgnoreCase(sellerName)) {
			if (!grantContested(context.getWorld(), sellerId, MAGNATE)) {
				return;
			}
		}
		saveGlobalInt(context, CONTESTED_MONTH_CACHE_PREFIX + MAGNATE.id(), month);
		saveGlobalInt(context, CONTESTED_SCORE_CACHE_PREFIX + MAGNATE.id(), sellerVolume);
		if (firstClaimThisMonth && sellerName != null) {
			context.getWorld().getWorldAnnouncementService().announceContestedOfficeClaim(
				sellerName, MAGNATE.displayName(), "");
		}
	}

	private static void announceClosedContestedPeriod(Player context, PlayerTitle title,
				int recordedMonth, int currentMonth) {
		if (context == null || title == null || recordedMonth <= 0 || recordedMonth == currentMonth) {
			return;
		}
		String crownedKey = CONTESTED_CROWNED_CACHE_PREFIX + title.id();
		if (loadGlobalInt(context, crownedKey) == recordedMonth) {
			return;
		}
		String owner = contestedTokenOwnerName(context, title);
		int score = loadContestedGlobalInt(context, CONTESTED_SCORE_CACHE_PREFIX,
			LEGACY_CONTESTED_SCORE_CACHE_PREFIX, title);
		if (owner != null) {
			context.getWorld().getWorldAnnouncementService().announceClosedContestedPeriod(
				title.displayName(), title.prefixForm(), owner, score, recordedMonth);
		}
		saveGlobalInt(context, crownedKey, recordedMonth);
	}

	private static void migratePlayerCache(Player player) {
		if (player == null) {
			return;
		}
		migrateV1(player);
		migrateFullV2(player);
		// Run the earlier core cull after the full migration so a legacy active
		// Warlord can move into the honorific slot before the old suffix is cleared.
		migrateV2(player);
	}

	private static void migrateV1(Player player) {
		if (player.getCache().hasKey(MIGRATION_CACHE)) {
			return;
		}

		Set<String> keys = new HashSet<>(player.getCache().getCacheMap().keySet());
		for (String key : keys) {
			if (key.startsWith(UNLOCK_CACHE_PREFIX)) {
				String id = key.substring(UNLOCK_CACHE_PREFIX.length());
				if (!FOUNDER.id.equals(id)) {
					player.getCache().remove(key);
				}
			} else if (key.startsWith(LEGACY_UNLOCK_CACHE_PREFIX)) {
				String id = key.substring(LEGACY_UNLOCK_CACHE_PREFIX.length());
				if (!FOUNDER.id.equals(id)) {
					player.getCache().remove(key);
				}
			} else if (key.startsWith(LEGACY_BLOCKED_UNIQUE_CACHE_PREFIX) || key.equals(LEGACY_UNIQUE_CLAIM_CACHE)) {
				player.getCache().remove(key);
			}
		}

		PlayerTitle active = null;
		if (player.getCache().hasKey(ACTIVE_TITLE_CACHE)) {
			active = byId(player.getCache().getString(ACTIVE_TITLE_CACHE));
		}
		if (active != FOUNDER) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
		}
		player.getCache().store(MIGRATION_CACHE, true);
	}

	// Core title migration: nothing is worn by default (founder excepted), and culled,
	// rescoped, or minigame-backed titles are revoked. Kept-title progress is untouched.
	private static void migrateV2(Player player) {
		if (player.getCache().hasKey(MIGRATION_V2_CACHE)) {
			return;
		}

		for (String id : MIGRATION_V2_REVOKED_IDS) {
			player.getCache().remove(UNLOCK_CACHE_PREFIX + id, LEGACY_UNLOCK_CACHE_PREFIX + id,
				PROMPT_QUEUE_CACHE_PREFIX + id, LEGACY_PROMPT_QUEUE_CACHE_PREFIX + id,
				FIRST_DATE_CACHE_PREFIX + id);
		}
		player.getCache().remove("title_gnomeball_goals");

		PlayerTitle active = null;
		if (player.getCache().hasKey(ACTIVE_TITLE_CACHE)) {
			active = byId(player.getCache().getString(ACTIVE_TITLE_CACHE));
		}
		if (active != FOUNDER) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		player.getCache().store(MIGRATION_V2_CACHE, true);
	}

	private static void migrateFullV2(Player player) {
		if (player.getCache().hasKey(MIGRATION_FULL_V2_CACHE)) {
			return;
		}

		int recordedUnsafeDeaths = counterValue(player, COUNTER_UNSAFE_DEATHS);
		if (player.getDeaths() > recordedUnsafeDeaths) {
			player.getCache().set(COUNTER_UNSAFE_DEATHS, player.getDeaths());
		}

		boolean appearanceChanged = false;
		if (player.getCache().hasKey(ACTIVE_TITLE_CACHE)) {
			PlayerTitle activeTitle = byId(player.getCache().getString(ACTIVE_TITLE_CACHE));
			if (activeTitle != null && activeTitle.honorific() && activeTitle.hasUnlockCache(player)) {
				player.getCache().store(ACTIVE_HONORIFIC_CACHE, activeTitle.id());
				player.getCache().remove(ACTIVE_TITLE_CACHE);
				appearanceChanged = true;
			}
		}
		if (player.getCache().hasKey(ACTIVE_HONORIFIC_CACHE)) {
			PlayerTitle activeHonorific = byId(player.getCache().getString(ACTIVE_HONORIFIC_CACHE));
			if (activeHonorific == null || !activeHonorific.honorific() || !activeHonorific.hasUnlockCache(player)) {
				player.getCache().remove(ACTIVE_HONORIFIC_CACHE);
				appearanceChanged = true;
			}
		}
		if (appearanceChanged) {
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		player.getCache().store(MIGRATION_FULL_V2_CACHE, true);
	}

	private static int totalNpcKills(Player player) {
		int cachedTotal = 0;
		for (Integer count : player.getKillCache().values()) {
			if (count != null) {
				cachedTotal += count;
			}
		}
		return Math.max(player.getNpcKills(), cachedTotal);
	}

	private static int unsafeDeathCount(Player player) {
		return counterValue(player, COUNTER_UNSAFE_DEATHS);
	}

	private static int voidChaseDropCount(Player player) {
		int count = 0;
		for (int itemId : VOID_CHASE_ITEM_IDS) {
			if (player.getCache().hasKey(voidDropCacheKey(itemId))) {
				count++;
			}
		}
		return count;
	}

	private static String voidDropCacheKey(int itemId) {
		return "title_vdrop_" + itemId;
	}

	private static boolean isVoidChaseItem(int itemId) {
		for (int chaseItemId : VOID_CHASE_ITEM_IDS) {
			if (itemId == chaseItemId) {
				return true;
			}
		}
		return false;
	}

	private static int huntmasterKillCount(Player player) {
		int count = 0;
		for (int npcId : HUNTMASTER_NPC_IDS) {
			if (player.getKillCache().getOrDefault(npcId, 0) > 0) {
				count++;
			}
		}
		return count;
	}

	private static boolean isHuntmasterNpc(int npcId) {
		for (int huntableId : HUNTMASTER_NPC_IDS) {
			if (npcId == huntableId) {
				return true;
			}
		}
		return false;
	}

	private static int highestSingleNpcKillCount(Player player) {
		int highest = 0;
		for (Map.Entry<Integer, Integer> entry : player.getKillCache().entrySet()) {
			if (entry.getValue() != null) {
				highest = Math.max(highest, entry.getValue());
			}
		}
		return highest;
	}

	private int npcKillsInSet(Player player) {
		int total = 0;
		if (npcIds == null) {
			return total;
		}
		for (int npcId : npcIds) {
			Integer kills = player.getKillCache().get(npcId);
			if (kills != null) {
				total += kills;
			}
		}
		return total;
	}

	private static boolean hasSkillLevel(Player player, Skill skill, int level) {
		return currentSkillLevel(player, skill) >= level;
	}

	private static int currentSkillLevel(Player player, Skill skill) {
		if (player == null || skill == null) {
			return 0;
		}
		int skillId = skill.id();
		if (skillId < 0 || skillId >= player.getWorld().getServer().getConstants().getSkills().getSkillsCount()) {
			return 0;
		}
		return player.getSkills().getMaxStat(skillId);
	}

	private static int knightCombatSkillsAt99(Player player) {
		int complete = 0;
		for (Skill skill : KNIGHT_COMBAT_SKILLS) {
			if (currentSkillLevel(player, skill) >= 99) {
				complete++;
			}
		}
		return complete;
	}

	private static String knightMissingSkills(Player player) {
		List<String> missing = new ArrayList<>();
		for (Skill skill : KNIGHT_COMBAT_SKILLS) {
			if (currentSkillLevel(player, skill) < 99) {
				missing.add(skillName(skill));
			}
		}
		return missing.isEmpty() ? "" : " (need " + String.join(", ", missing) + ")";
	}

	private static int highestSkillLevel(Player player) {
		int highest = 0;
		int count = player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
		for (int skillId = 0; skillId < count; skillId++) {
			highest = Math.max(highest, player.getSkills().getMaxStat(skillId));
		}
		return highest;
	}

	private static int maxQuestPoints(Player player) {
		int max = 0;
		for (QuestInterface quest : player.getWorld().getQuests()) {
			max += quest.getQuestPoints();
		}
		return max;
	}

	private static int counterValue(Player player, String counterKey) {
		if (player == null || counterKey == null || !player.getCache().hasKey(counterKey)) {
			return 0;
		}
		Object value = player.getCache().getCacheMap().get(counterKey);
		if (value instanceof Integer) {
			return (Integer) value;
		}
		if (value instanceof Long) {
			return (int) Math.min(Integer.MAX_VALUE, (Long) value);
		}
		if (value instanceof String) {
			try {
				return Integer.parseInt((String) value);
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}
		return 0;
	}

	private static long cacheLong(Player player, String key) {
		if (player == null || key == null || !player.getCache().hasKey(key)) {
			return 0L;
		}
		Object value = player.getCache().getCacheMap().get(key);
		if (value instanceof Long) {
			return (Long) value;
		}
		if (value instanceof Integer) {
			return ((Integer) value).longValue();
		}
		if (value instanceof String) {
			try {
				return Long.parseLong((String) value);
			} catch (NumberFormatException ignored) {
				return 0L;
			}
		}
		return 0L;
	}

	private static void stampFirstClaimDate(Player player, PlayerTitle title) {
		long epochSeconds = System.currentTimeMillis() / 1000L;
		player.getCache().store(FIRST_DATE_CACHE_PREFIX + title.id(), epochSeconds);
		saveGlobalLong(player, FIRST_DATE_CACHE_PREFIX + title.id(), epochSeconds);
	}

	private static void stampHonorificDate(Player player, PlayerTitle title) {
		String key = FIRST_DATE_CACHE_PREFIX + title.id();
		if (!player.getCache().hasKey(key)) {
			player.getCache().store(key, System.currentTimeMillis() / 1000L);
		}
	}

	private static void announceUniqueClaim(Player player, PlayerTitle title) {
		if (player != null && title != null) {
			player.getWorld().getWorldAnnouncementService().announceUniqueTitleClaim(player, title.displayName());
		}
	}

	private static String onlineUniqueOwnerName(World world, PlayerTitle title) {
		if (world == null || title == null) {
			return null;
		}
		for (Player candidate : world.getPlayers()) {
			migratePlayerCache(candidate);
			if (directlyOwnsUnique(candidate, title)) {
				return candidate.getUsername();
			}
		}
		return null;
	}

	private static boolean directlyOwnsUnique(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.hasUnlockCache(player)) {
			return false;
		}
		if (title.uniqueKind == UniqueKind.CONTESTED) {
			return ownsCurrentContestedToken(player, title);
		}
		return true;
	}

	private static boolean ownsCurrentContestedToken(Player player, PlayerTitle title) {
		int token = currentContestedToken(player, title);
		return token > 0 && counterValue(player, title.cacheKey()) == token && !contestedSeasonExpired(player, title);
	}

	private static boolean contestedSeasonExpired(Player player, PlayerTitle title) {
		if (title != WARLORD_WASTES && title != MAGNATE) {
			return false;
		}
		return loadContestedGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX,
			LEGACY_CONTESTED_MONTH_CACHE_PREFIX, title) != currentMonth();
	}

	private static String databaseUniqueOwnerName(Player player, PlayerTitle title) {
		try {
			if (title.uniqueKind == UniqueKind.CONTESTED) {
				int token = currentContestedToken(player, title);
				if (token <= 0 || contestedSeasonExpired(player, title)) {
					return null;
				}
				return player.getWorld().getServer().getDatabase().queryPlayerCacheOwner(title.cacheKey(), String.valueOf(token));
			}
			return player.getWorld().getServer().getDatabase().queryPlayerCacheOwner(title.cacheKey());
		} catch (GameDatabaseException ex) {
			return null;
		}
	}

	private static String contestedTokenOwnerName(Player context, PlayerTitle title) {
		if (context == null || title == null || !title.contested()) {
			return null;
		}
		int token = currentContestedToken(context, title);
		if (token <= 0) {
			return null;
		}
		for (Player candidate : context.getWorld().getPlayers()) {
			if (candidate.getCache().hasKey(title.cacheKey())
				&& counterValue(candidate, title.cacheKey()) == token) {
				return candidate.getUsername();
			}
		}
		try {
			return context.getWorld().getServer().getDatabase()
				.queryPlayerCacheOwner(title.cacheKey(), String.valueOf(token));
		} catch (GameDatabaseException ex) {
			return null;
		}
	}

	private static int currentContestedToken(Player player, PlayerTitle title) {
		return loadContestedGlobalInt(player, CONTESTED_TOKEN_CACHE_PREFIX,
			LEGACY_CONTESTED_TOKEN_CACHE_PREFIX, title);
	}

	private static int nextContestedToken(World world, PlayerTitle title) {
		int token = 1;
		try {
			Integer current = world.getServer().getDatabase().queryLoadGlobalCacheInt(CONTESTED_TOKEN_CACHE_PREFIX + title.id());
			if (current == null || current <= 0) {
				String legacyKey = LEGACY_CONTESTED_TOKEN_CACHE_PREFIX + title.id();
				current = world.getServer().getDatabase().queryLoadGlobalCacheInt(legacyKey);
				if ((current == null || current <= 0) && legacyKey.length() > MAX_PLAYER_CACHE_KEY_LENGTH) {
					current = world.getServer().getDatabase().queryLoadGlobalCacheInt(
						legacyKey.substring(0, MAX_PLAYER_CACHE_KEY_LENGTH));
				}
			}
			if (current != null && current > 0) {
				token = current + 1;
			}
			world.getServer().getDatabase().querySaveGlobalCacheInt(CONTESTED_TOKEN_CACHE_PREFIX + title.id(), token);
		} catch (GameDatabaseException ignored) {
			token = (int) (System.currentTimeMillis() / 1000L);
		}
		return token;
	}

	private static Player onlinePlayerByName(World world, String username) {
		if (world == null || username == null) {
			return null;
		}
		for (Player candidate : world.getPlayers()) {
			if (candidate.getUsername().equalsIgnoreCase(username)) {
				return candidate;
			}
		}
		return null;
	}

	private static String playerName(Player context, int playerId) {
		if (context == null || playerId <= 0) {
			return null;
		}
		Player onlinePlayer = context.getWorld().getPlayerID(playerId);
		if (onlinePlayer != null) {
			return onlinePlayer.getUsername();
		}
		try {
			return context.getWorld().getServer().getDatabase().usernameFromId(playerId);
		} catch (GameDatabaseException ex) {
			return null;
		}
	}

	private static int loadGlobalInt(Player player, String key) {
		try {
			Integer value = player.getWorld().getServer().getDatabase().queryLoadGlobalCacheInt(key);
			return value == null ? 0 : value;
		} catch (GameDatabaseException ex) {
			return 0;
		}
	}

	private static int loadContestedGlobalInt(Player player, String compactPrefix,
				String legacyPrefix, PlayerTitle title) {
		String compactKey = compactPrefix + title.id();
		int value = loadGlobalInt(player, compactKey);
		if (value > 0) {
			return value;
		}
		String legacyKey = legacyPrefix + title.id();
		value = loadGlobalInt(player, legacyKey);
		if (value <= 0 && legacyKey.length() > MAX_PLAYER_CACHE_KEY_LENGTH) {
			value = loadGlobalInt(player, legacyKey.substring(0, MAX_PLAYER_CACHE_KEY_LENGTH));
		}
		if (value > 0) {
			saveGlobalInt(player, compactKey, value);
		}
		return value;
	}

	private static long loadGlobalLong(Player player, String key) {
		try {
			Long value = player.getWorld().getServer().getDatabase().queryLoadGlobalCacheLong(key);
			return value == null ? 0L : value;
		} catch (GameDatabaseException ex) {
			return 0L;
		}
	}

	private static void saveGlobalInt(Player player, String key, int value) {
		try {
			player.getWorld().getServer().getDatabase().querySaveGlobalCacheInt(key, value);
		} catch (GameDatabaseException ignored) {
		}
	}

	private static void saveGlobalLong(Player player, String key, long value) {
		try {
			player.getWorld().getServer().getDatabase().querySaveGlobalCacheLong(key, value);
		} catch (GameDatabaseException ignored) {
		}
	}

	private static int currentMonth() {
		return Integer.parseInt(new SimpleDateFormat("yyyyMM").format(new Date()));
	}

	private static long monthStartEpochSeconds() {
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		return calendar.getTimeInMillis() / 1000L;
	}

	private static String monthlyPkCounterKey(int month) {
		return "title_pk_" + month;
	}

	private static String skillName(Skill skill) {
		if (skill == null || skill.name() == null) {
			return "skill";
		}
		if (skill == Skill.DEFENSE) {
			return "Defence";
		}
		if (skill == Skill.HITS) {
			return "Hits";
		}
		String value = skill.name().toLowerCase();
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]+", "");
	}

	private static String formatNumber(int value) {
		return String.format("%,d", value);
	}

	private static String trimLeadingThe(String value) {
		if (value == null) {
			return "";
		}
		if (value.toLowerCase().startsWith("the ")) {
			return value.substring(4);
		}
		return value;
	}

	private static String trimTrailingPeriod(String value) {
		if (value == null) {
			return "";
		}
		return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
	}

	private static String titleCase(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	// ---------------------------------------------------------------------------------
	// Core-loop unlock wear-prompt. Unlocking never equips; instead a short per-title cache
	// marker is queued and Player.processTick offers a two-option menu when the player is
	// eligible. The menu cannot
	// use Functions.multi() — unlock() fires on the game thread with no plugin context —
	// so replies are observed through the shared question-option field, one tick at a time.
	// ---------------------------------------------------------------------------------

	private static String promptQueueKey(PlayerTitle title) {
		return PROMPT_QUEUE_CACHE_PREFIX + title.id();
	}

	private static void enqueuePrompt(Player player, PlayerTitle title) {
		if (player == null || title == null || isActive(player, title)) {
			return;
		}
		player.getCache().set(promptQueueKey(title), 1);
	}

	/**
	 * Consume a title-menu reply immediately after inbound packets. Some legacy player
	 * events reset the shared question option later in the same tick, so offer timing and
	 * reply timing intentionally sit at opposite ends of Player.processTick.
	 */
	public static void processPromptReply(Player player) {
		if (player == null) {
			return;
		}
		PromptState state = player.getAttribute(PROMPT_STATE_ATTRIBUTE, null);
		if (state == null || player.getMenuHandler() != null) {
			return;
		}
		int option = player.getOption();
		if (option == 0 || option == 1) {
			handlePromptAnswer(player, state.title, option);
		} else {
			// X, ESC, combat resets, and ordinary menu cancellation all use -1. Keep
			// the durable marker, but do not reopen the ceremony in the same tick.
			long tick = player.getWorld().getServer().getCurrentTick();
			player.setAttribute(PROMPT_BACKOFF_ATTRIBUTE, tick + PROMPT_BACKOFF_TICKS);
		}
		player.setOption(-1);
		player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
	}

	public static void processPromptTick(Player player) {
		if (player == null || !player.loggedIn()) {
			return;
		}
		long tick = player.getWorld().getServer().getCurrentTick();
		PromptState state = player.getAttribute(PROMPT_STATE_ATTRIBUTE, null);
		if (state != null) {
			if (player.getMenuHandler() == null) {
				// Option -1 means the menu was dismissed or reset. The marker survives,
				// so the core loop offers it again once the player is eligible.
				player.setOption(-1);
				player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
				return;
			}
			if (player.getMenuHandler() != state.listener) {
				// Another core interaction owns the menu slot now. Leave it alone and
				// retain the title marker for a later tick.
				player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
				return;
			}
			if (!promptStillPending(player, state.title)) {
				player.resetMenuHandler();
				player.getCache().remove(promptQueueKey(state.title));
				player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
				return;
			}
			// Combat can begin without every combat path replacing the open menu. Other
			// core interactions take ownership of the menu handler and are handled above.
			if (player.inCombat()) {
				player.resetMenuHandler();
				player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
				return;
			}
			// Still open (a client-side dismissal sends no reply). Time out, hide,
			// and re-offer later without dropping the persistent marker.
			if (tick - state.sentTick >= PROMPT_TIMEOUT_TICKS) {
				player.resetMenuHandler();
				player.removeAttribute(PROMPT_STATE_ATTRIBUTE);
				player.setAttribute(PROMPT_BACKOFF_ATTRIBUTE, tick + PROMPT_BACKOFF_TICKS);
			}
			return;
		}

		Long backoffUntil = player.getAttribute(PROMPT_BACKOFF_ATTRIBUTE, null);
		if (backoffUntil != null && tick < backoffUntil) {
			return;
		}
		if (!promptEligible(player)) {
			return;
		}
		PlayerTitle next = nextQueuedPrompt(player);
		if (next != null) {
			offerPrompt(player, next, tick);
		}
	}

	private static boolean promptEligible(Player player) {
		return !promptContextBlocked(player)
			&& player.getMenuHandler() == null
			&& player.getMenu() == null;
	}

	private static boolean promptContextBlocked(Player player) {
		return player.isBusy()
			|| player.inCombat()
			|| player.getDuel().isDuelActive()
			|| player.getTrade().isTradeActive()
			|| player.accessingBank()
			|| player.accessingShop()
			|| player.getAttribute("input_box_pending", null) != null;
	}

	private static boolean promptStillPending(Player player, PlayerTitle title) {
		return player.getCache().hasKey(promptQueueKey(title))
			&& title.isUnlocked(player)
			&& !isActive(player, title);
	}

	private static PlayerTitle nextQueuedPrompt(Player player) {
		for (PlayerTitle title : values()) {
			if (!player.getCache().hasKey(promptQueueKey(title))) {
				continue;
			}
			if (!title.isUnlocked(player) || isActive(player, title)) {
				player.getCache().remove(promptQueueKey(title));
				continue;
			}
			return title;
		}
		return null;
	}

	private static void offerPrompt(Player player, PlayerTitle title, long tick) {
		String metadata = "";
		if (player.isUsingCustomClient() && player.getClientVersion() >= OVERHEAD_HONORIFIC_CLIENT_VERSION) {
			metadata = " ~vstitleaward~" + safeMetadata(title.id()) + "|"
				+ safeMetadata(title.displayName()) + "|" + title.tier.code + "|"
				+ title.position.name().toLowerCase() + "|" + safeMetadata(title.prefixForm);
		}
		String[] options = {
			"Wear it - " + formatNumber(WEAR_COST_COINS) + " gp" + metadata,
			"Not now" + metadata
		};
		MenuOptionListener listener = new MenuOptionListener(options);
		player.setMenuHandler(listener);
		ActionSender.sendMenu(player, options);
		player.message("@mag@You have earned " + title.tierColorToken() + title.displayName() + "@whi@.");
		player.setAttribute(PROMPT_STATE_ATTRIBUTE, new PromptState(title, listener, tick));
	}

	private static void handlePromptAnswer(Player player, PlayerTitle title, int option) {
		player.getCache().remove(promptQueueKey(title));
		if (option == 0) {
			WearResult result = tryWear(player, title);
			switch (result) {
				case WORN:
					player.message("You pay " + formatNumber(WEAR_COST_COINS) + " coins. Your active "
						+ (title.honorific() ? "honorific" : "title") + " is now "
						+ title.tierColorToken() + title.displayName() + "@whi@.");
					break;
				case ALREADY_ACTIVE:
					player.message("You are already wearing " + title.tierColorToken()
						+ title.displayName() + "@whi@. No coins were charged.");
					break;
				case NOT_UNLOCKED:
					player.message("You no longer hold " + title.tierColorToken()
						+ title.displayName() + "@whi@. No coins were charged.");
					break;
				case INSUFFICIENT_COINS:
					player.message("You need " + formatNumber(WEAR_COST_COINS) + " coins in your pack to wear it.");
					player.message("Bring them to the Void Herald when you are ready.");
					break;
				case PAYMENT_FAILED:
				default:
					player.message("The payment could not be taken, so your title was not changed.");
					player.message("Speak to the Void Herald and try again.");
					break;
			}
		} else {
			player.message("You can wear it later by speaking to the Void Herald.");
		}
	}

	private static String safeMetadata(String value) {
		return value == null ? "" : value.replace("|", "/").replace("~vstitleaward~", "");
	}

	private static final class PromptState {
		private final PlayerTitle title;
		private final MenuOptionListener listener;
		private final long sentTick;

		private PromptState(PlayerTitle title, MenuOptionListener listener, long sentTick) {
			this.title = title;
			this.listener = listener;
			this.sentTick = sentTick;
		}
	}

	private static boolean isHerb(int itemId) {
		return itemId == ItemId.UNIDENTIFIED_GUAM_LEAF.id()
			|| itemId == ItemId.UNIDENTIFIED_MARRENTILL.id()
			|| itemId == ItemId.UNIDENTIFIED_TARROMIN.id()
			|| itemId == ItemId.UNIDENTIFIED_HARRALANDER.id()
			|| itemId == ItemId.GUAM_LEAF.id()
			|| itemId == ItemId.MARRENTILL.id()
			|| itemId == ItemId.TARROMIN.id()
			|| itemId == ItemId.HARRALANDER.id();
	}

	public enum Tier {
		RENOWN(0, 0, "renown", "@whi@"),
		FEAT(1, 1, "feat", "@mag@"),
		SUPREME(3, 2, "supreme", "@red@"),
		UNIQUE(2, 3, "unique", "@yel@");

		private final int code;
		private final int prestigeRank;
		private final String label;
		private final String chatColor;

		Tier(int code, int prestigeRank, String label, String chatColor) {
			this.code = code;
			this.prestigeRank = prestigeRank;
			this.label = label;
			this.chatColor = chatColor;
		}

		public int code() {
			return code;
		}

		public int prestigeRank() {
			return prestigeRank;
		}
	}

	public enum Position {
		SUFFIX,
		PREFIX
	}

	public enum WearResult {
		WORN,
		ALREADY_ACTIVE,
		NOT_UNLOCKED,
		INSUFFICIENT_COINS,
		PAYMENT_FAILED
	}

	public enum UniqueKind {
		FIRST,
		CONTESTED,
		ITEM_BOUND
	}

	private enum RequirementType {
		MANUAL,
		TOTAL_LEVEL,
		COMBAT_LEVEL,
		PLAYER_KILLS,
		NPC_KILLS_TOTAL,
		NPC_KILLS_ANY,
		NPC_KILLS_SET,
		SKILL_LEVEL,
		QUEST_COMPLETE,
		MAX_QUEST_POINTS,
		COUNTER,
		ZERO_DEATH_COMBAT,
		ZERO_DEATH_TOTAL,
		VOID_CHASE_DROPS,
		HUNTMASTER,
		SAINT,
		KNIGHT
	}
}
