package com.openrsc.server.content;

import com.openrsc.server.constants.Quests;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
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
	GNOME_BALLER("gnome_baller", "the Gnome-Baller", "Score 100 gnomeball goals.", Tier.FEAT, RequirementType.COUNTER, 100, "title_gnomeball_goals"),
	GIANT_KILLER("giant_killer", "the Giant-Killer", "Kill an NPC of combat level at least twice yours, minimum NPC level 50.", Tier.FEAT, RequirementType.MANUAL),
	UNTOUCHED("untouched", "the Untouched", "Reach combat level 90 without ever dying.", Tier.FEAT, RequirementType.ZERO_DEATH_COMBAT, 90),
	EDGELORD("edgelord", "the Edgelord", "Defeat 50 players in wilderness levels 1-5.", Tier.FEAT, RequirementType.COUNTER, 50, "title_edgeville_pks"),
	BRONZE_REAPER("bronze_reaper", "the Bronze Reaper", "Defeat a player while wielding a bronze weapon.", Tier.FEAT, RequirementType.MANUAL),
	VOIDBANE("voidbane", "Voidbane", "Defeat the Void Knight in the Death Match Arena.", Tier.FEAT, RequirementType.MANUAL),
	GRAVEWALKER("gravewalker", "the Gravewalker", "Clear wave 10 in Undead Siege.", Tier.FEAT, RequirementType.MANUAL),
	FOUNDER("founder", "the Founder", "Reserved for early Voidscape supporters.", Tier.FEAT, RequirementType.MANUAL),

	TRAILBLAZER("trailblazer", "the Trailblazer", "First player on the server to reach level 99 in any skill.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.SKILL_LEVEL, 99),
	PARAGON("paragon", "the Paragon", "First player to reach max total level.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.TOTAL_LEVEL, 1782),
	LOREKEEPER("lorekeeper", "the Lorekeeper", "First player to complete every quest.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.MAX_QUEST_POINTS),
	COLOSSUSBANE("colossusbane", "Colossusbane", "First kill of the Void Colossus.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.MANUAL),
	RIFTBOUND("riftbound", "Riftbound", "First to win the Void Rift event.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.MANUAL),
	WARLORD_WASTES("warlord_wastes", "Warlord of the Wastes", "Most wilderness kills this month, minimum 10.", Tier.UNIQUE, UniqueKind.CONTESTED, RequirementType.MANUAL),
	VOID_ARENA_CHAMPION("void_arena_champion", "the Void Arena Champion", "#1 in the current Void Arena ranked season.", Tier.UNIQUE, UniqueKind.CONTESTED, RequirementType.MANUAL),
	MAGNATE("magnate", "the Magnate", "Top auction-house trade volume this season.", Tier.UNIQUE, UniqueKind.CONTESTED, RequirementType.MANUAL),
	FIRST_SWORDFISH("first_swfish", "the Swordfish Sovereign", "First 50,000 Swordfish Cooked.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_swfish"),
	FIRST_COAL("first_coal25k", "the Coal Pioneer", "First 25,000 Coal Mined.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 25000, "title_coal_mined"),
	FIRST_MAGIC_LOGS("first_mlogs", "the Elder Feller", "First 50,000 Magic Logs Cut.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_mlogs"),
	FIRST_HERBS("first_herbs", "the Herb Harvester", "First 25,000 Herbs Picked Up.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 25000, "title_herbs"),
	FIRST_GEMS("first_gems", "the Gemsetter", "First 10,000 Gems Cut.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 10000, "title_gems"),
	FIRST_TOTAL_1700("first_t1700", "the Seventeen-Hundred", "First Total Level 1700.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.TOTAL_LEVEL, 1700),
	FIRST_AGILITY_30("first_agil30", "the First-Footed", "First 30 Agility.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.SKILL_LEVEL, 30, Skill.AGILITY),
	FIRST_SPELLS("first_spells", "the Spellstorm", "First 50,000 Spells Cast.", Tier.UNIQUE, UniqueKind.FIRST, RequirementType.COUNTER, 50000, "title_spells"),
	RELIC_KEEPER("relic_keeper", "the Relic-Keeper", "Hold the one-of-one Voidscape relic. Staff-granted until the relic exists.", Tier.UNIQUE, UniqueKind.ITEM_BOUND, RequirementType.MANUAL);

	public static final String ACTIVE_TITLE_CACHE = "player_title_active";
	public static final int OVERHEAD_TITLE_CLIENT_VERSION = 10052;
	public static final int OVERHEAD_TITLE_TIER_CLIENT_VERSION = 10123;
	public static final String COUNTER_LOBSTERS = "title_lobsters";
	public static final String COUNTER_COOK_STREAK = "title_cook_streak";
	public static final String COUNTER_BONES_BURIED = "title_bones_buried";
	public static final String COUNTER_COAL_MINED = "title_coal_mined";
	public static final String COUNTER_HIGH_ALCHS = "title_high_alchs";
	public static final String COUNTER_GNOMEBALL_GOALS = "title_gnomeball_goals";
	public static final String COUNTER_EDGEVILLE_PKS = "title_edgeville_pks";
	public static final String COUNTER_SWORDFISH_COOKED = "title_swfish";
	public static final String COUNTER_MAGIC_LOGS = "title_mlogs";
	public static final String COUNTER_HERBS_PICKED_UP = "title_herbs";
	public static final String COUNTER_GEMS_CUT = "title_gems";
	public static final String COUNTER_SPELLS_CAST = "title_spells";

	private static final String UNLOCK_CACHE_PREFIX = "pt_u_";
	private static final String LEGACY_UNLOCK_CACHE_PREFIX = "player_title_unlocked_";
	private static final String LEGACY_BLOCKED_UNIQUE_CACHE_PREFIX = "pt_b_";
	private static final String LEGACY_UNIQUE_CLAIM_CACHE = "pt_unique_claim";
	private static final String FIRST_DATE_CACHE_PREFIX = "pt_first_date_";
	private static final String CONTESTED_TOKEN_CACHE_PREFIX = "title_contested_token_";
	private static final String CONTESTED_MONTH_CACHE_PREFIX = "title_contested_month_";
	private static final String CONTESTED_SCORE_CACHE_PREFIX = "title_contested_score_";
	private static final String MIGRATION_CACHE = "pt_migration_10123";
	private static final int WARLORD_MIN_MONTHLY_KILLS = 10;

	private final String id;
	private final String displayName;
	private final String unlockHint;
	private final Tier tier;
	private final UniqueKind uniqueKind;
	private final RequirementType requirementType;
	private final int threshold;
	private final Skill skill;
	private final int[] npcIds;
	private final String counterKey;

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, null, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold) {
		this(id, displayName, unlockHint, tier, null, requirementType, threshold, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, Skill skill) {
		this(id, displayName, unlockHint, tier, null, requirementType, threshold, skill, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, int[] npcIds) {
		this(id, displayName, unlockHint, tier, null, requirementType, threshold, null, npcIds, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, RequirementType requirementType, int threshold, String counterKey) {
		this(id, displayName, unlockHint, tier, null, requirementType, threshold, null, null, counterKey);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType) {
		this(id, displayName, unlockHint, tier, uniqueKind, requirementType, 0, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold) {
		this(id, displayName, unlockHint, tier, uniqueKind, requirementType, threshold, null, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold, Skill skill) {
		this(id, displayName, unlockHint, tier, uniqueKind, requirementType, threshold, skill, null, null);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType, int threshold, String counterKey) {
		this(id, displayName, unlockHint, tier, uniqueKind, requirementType, threshold, null, null, counterKey);
	}

	PlayerTitle(String id, String displayName, String unlockHint, Tier tier, UniqueKind uniqueKind, RequirementType requirementType,
				int threshold, Skill skill, int[] npcIds, String counterKey) {
		this.id = id;
		this.displayName = displayName;
		this.unlockHint = unlockHint;
		this.tier = tier;
		this.uniqueKind = uniqueKind;
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
				return "Combat " + threshold + ", zero deaths.";
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
				return "Progress: combat " + player.getCombatLevel() + "/" + threshold + ", deaths " + player.getDeaths() + ".";
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
				return player.getCombatLevel() >= threshold && player.getDeaths() == 0;
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
		player.message("@mag@Title unlocked: " + title.tierColorToken() + title.displayName() + "@whi@.");
		if (active(player) == null) {
			setActive(player, title);
		}
		if (title.unique()) {
			announceUniqueClaim(player, title);
		}
		return true;
	}

	public static synchronized boolean revoke(Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (player == null || title == null || !title.hasUnlockCache(player)) {
			return false;
		}
		player.getCache().remove(title.cacheKey(), title.legacyCacheKey());
		if (active(player) == title) {
			setActive(player, null);
		}
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
		if (active(player) == null) {
			setActive(player, title);
		} else {
			player.getUpdateFlags().setAppearanceChanged(true);
		}
		player.message("@mag@Contested title awarded: " + title.tierColorToken() + title.displayName() + "@whi@.");

		Player previous = onlinePlayerByName(world, previousOwner);
		if (previous != null && previous.getUsernameHash() != player.getUsernameHash()) {
			previous.getCache().remove(title.cacheKey());
			if (active(previous) == title) {
				setActive(previous, null);
			}
			previous.message("@mag@The contested title " + title.tierColorToken() + title.displayName() + "@whi@ has changed hands.");
		}
		world.getWorldAnnouncementService().announceUniqueTitleTransfer(player, title.displayName(), previousOwner);
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

	public static void recordAuctionHouseSale(Player context, int sellerId) {
		if (context == null || sellerId <= 0) {
			return;
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

	public static void checkGiantKiller(Player player, int npcCombatLevel) {
		if (player != null && npcCombatLevel >= 50 && npcCombatLevel >= player.getCombatLevel() * 2) {
			unlock(player, GIANT_KILLER);
		}
	}

	public static void checkRunePlateSmith(Player player) {
		unlock(player, RUNESMITH);
	}

	public static void checkDragonMediumDrop(Player player) {
		unlock(player, DRAGON_CROWNED);
	}

	public static void checkBronzeWeaponPlayerKill(Player player, boolean wieldingBronzeWeapon) {
		if (wieldingBronzeWeapon) {
			unlock(player, BRONZE_REAPER);
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
		if (title == null || !title.isUnlocked(player)) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
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

	public static void setActive(Player player, PlayerTitle title) {
		migratePlayerCache(player);
		if (player == null) {
			return;
		}
		if (title == null) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
			return;
		}
		if (!title.isUnlocked(player)) {
			player.getCache().remove(ACTIVE_TITLE_CACHE);
			player.getUpdateFlags().setAppearanceChanged(true);
			return;
		}
		player.getCache().store(ACTIVE_TITLE_CACHE, title.id());
		player.getUpdateFlags().setAppearanceChanged(true);
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
			if (loadGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX + title.id()) != month) {
				return 0;
			}
			return loadGlobalInt(player, CONTESTED_SCORE_CACHE_PREFIX + title.id());
		}
		if (title == MAGNATE) {
			int month = currentMonth();
			if (loadGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX + title.id()) != month) {
				return 0;
			}
			return loadGlobalInt(player, CONTESTED_SCORE_CACHE_PREFIX + title.id());
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
		if (monthlyKills < WARLORD_MIN_MONTHLY_KILLS) {
			return;
		}
		int recordedMonth = loadGlobalInt(killer, CONTESTED_MONTH_CACHE_PREFIX + WARLORD_WASTES.id());
		int leaderScore = recordedMonth == month ? loadGlobalInt(killer, CONTESTED_SCORE_CACHE_PREFIX + WARLORD_WASTES.id()) : 0;
		if (monthlyKills < leaderScore) {
			return;
		}
		String owner = ownerName(killer, WARLORD_WASTES);
		if (monthlyKills == leaderScore && owner != null && !owner.equalsIgnoreCase(killer.getUsername())) {
			return;
		}
		saveGlobalInt(killer, CONTESTED_MONTH_CACHE_PREFIX + WARLORD_WASTES.id(), month);
		saveGlobalInt(killer, CONTESTED_SCORE_CACHE_PREFIX + WARLORD_WASTES.id(), monthlyKills);
		if (owner == null || !owner.equalsIgnoreCase(killer.getUsername())) {
			grantContested(killer.getWorld(), killer, WARLORD_WASTES);
		}
	}

	private static void updateMagnate(Player context, int month, int sellerId, int sellerVolume) {
		if (sellerVolume <= 0) {
			return;
		}
		int recordedMonth = loadGlobalInt(context, CONTESTED_MONTH_CACHE_PREFIX + MAGNATE.id());
		int leaderScore = recordedMonth == month ? loadGlobalInt(context, CONTESTED_SCORE_CACHE_PREFIX + MAGNATE.id()) : 0;
		if (sellerVolume < leaderScore) {
			return;
		}
		String sellerName = playerName(context, sellerId);
		String owner = ownerName(context, MAGNATE);
		if (sellerVolume == leaderScore && owner != null && (sellerName == null || !owner.equalsIgnoreCase(sellerName))) {
			return;
		}
		saveGlobalInt(context, CONTESTED_MONTH_CACHE_PREFIX + MAGNATE.id(), month);
		saveGlobalInt(context, CONTESTED_SCORE_CACHE_PREFIX + MAGNATE.id(), sellerVolume);
		if (owner == null || sellerName == null || !owner.equalsIgnoreCase(sellerName)) {
			grantContested(context.getWorld(), sellerId, MAGNATE);
		}
	}

	private static void migratePlayerCache(Player player) {
		if (player == null || player.getCache().hasKey(MIGRATION_CACHE)) {
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

	private static int totalNpcKills(Player player) {
		int cachedTotal = 0;
		for (Integer count : player.getKillCache().values()) {
			if (count != null) {
				cachedTotal += count;
			}
		}
		return Math.max(player.getNpcKills(), cachedTotal);
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
		if (title != WARLORD_WASTES) {
			return false;
		}
		return loadGlobalInt(player, CONTESTED_MONTH_CACHE_PREFIX + title.id()) != currentMonth();
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

	private static int currentContestedToken(Player player, PlayerTitle title) {
		return loadGlobalInt(player, CONTESTED_TOKEN_CACHE_PREFIX + title.id());
	}

	private static int nextContestedToken(World world, PlayerTitle title) {
		int token = 1;
		try {
			Integer current = world.getServer().getDatabase().queryLoadGlobalCacheInt(CONTESTED_TOKEN_CACHE_PREFIX + title.id());
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
		RENOWN(0, "renown", "@whi@"),
		FEAT(1, "feat", "@mag@"),
		UNIQUE(2, "unique", "@yel@");

		private final int code;
		private final String label;
		private final String chatColor;

		Tier(int code, String label, String chatColor) {
			this.code = code;
			this.label = label;
			this.chatColor = chatColor;
		}

		public int code() {
			return code;
		}
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
		ZERO_DEATH_COMBAT
	}
}
