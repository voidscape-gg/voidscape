package com.openrsc.server.content;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public enum PlayerTitle {
	FIRST_STEP("first_step", "First-Step", "Reach total level 50.", RequirementType.TOTAL_LEVEL, 50),
	TRAILSPARK("trailspark", "Trail-Spark", "Reach total level 100.", RequirementType.TOTAL_LEVEL, 100),
	WAYFARER("wayfarer", "Wayfarer of Old Roads", "Reach total level 250.", RequirementType.TOTAL_LEVEL, 250),
	SEASONED("seasoned", "Weathered Wayfarer", "Reach total level 500.", RequirementType.TOTAL_LEVEL, 500),
	TESTED("tested", "Iron-Tried", "Reach total level 750.", RequirementType.TOTAL_LEVEL, 750),
	PROVEN("proven", "Oath-Proven", "Reach total level 1,000.", RequirementType.TOTAL_LEVEL, 1000),
	RESOLUTE("resolute", "Grey Resolve", "Reach total level 1,250.", RequirementType.TOTAL_LEVEL, 1250),
	ASCENDANT("ascendant", "Skyward Ascendant", "Reach total level 1,500.", RequirementType.TOTAL_LEVEL, 1500),
	NEAR_MAXED("near_maxed", "Almost Maxed", "Reach total level 1,750.", RequirementType.TOTAL_LEVEL, 1750, TitleScope.UNIQUE),
	MAXED("maxed", "Max-Caped", "Reach total level 1,782.", RequirementType.TOTAL_LEVEL, 1782, TitleScope.UNIQUE),

	SPARRING_BLADE("sparring_blade", "Sparring Blade", "Reach combat level 20.", RequirementType.COMBAT_LEVEL, 20),
	DUELIST("duelist", "Red-Steel Duelist", "Reach combat level 40.", RequirementType.COMBAT_LEVEL, 40),
	IRONBLOOD("ironblood", "Ironblooded", "Reach combat level 60.", RequirementType.COMBAT_LEVEL, 60),
	WARBRINGER("warbringer", "War-Bringer", "Reach combat level 80.", RequirementType.COMBAT_LEVEL, 80),
	CONQUEROR("conqueror", "Crownless Conqueror", "Reach combat level 100.", RequirementType.COMBAT_LEVEL, 100, TitleScope.UNIQUE),
	UNBROKEN("unbroken", "Unbroken Oath", "Reach combat level 110.", RequirementType.COMBAT_LEVEL, 110, TitleScope.UNIQUE),
	WARLORD("warlord", "Warlord of the Edge", "Reach combat level 120.", RequirementType.COMBAT_LEVEL, 120, TitleScope.UNIQUE),
	BLACK_HELM("black_helm", "Black-Helmed", "Reach combat level 123.", RequirementType.COMBAT_LEVEL, 123, TitleScope.UNIQUE),

	ERRAND_KNIGHT("errand_knight", "Errand Knight", "Earn 5 quest points.", RequirementType.QUEST_POINTS, 5),
	TALE_SEEKER("tale_seeker", "Tale-Seeker", "Earn 15 quest points.", RequirementType.QUEST_POINTS, 15),
	OATHBOUND("oathbound", "Oathbound", "Earn 30 quest points.", RequirementType.QUEST_POINTS, 30),
	LOREMASTER("loremaster", "Lore-Sworn", "Earn 45 quest points.", RequirementType.QUEST_POINTS, 45),
	CHRONICLER("chronicler", "Chronicler of Misthalin", "Earn 60 quest points.", RequirementType.QUEST_POINTS, 60),
	LEGEND("legend", "Legend of Old Roads", "Earn 75 quest points.", RequirementType.QUEST_POINTS, 75, TitleScope.UNIQUE),
	QUEST_CROWNED("quest_crowned", "Quest-Crowned", "Earn 90 quest points.", RequirementType.QUEST_POINTS, 90, TitleScope.UNIQUE),

	FIRST_BLOOD("first_blood", "First Blood", "Defeat one player in the wilderness.", RequirementType.PLAYER_KILLS, 1),
	RED_HANDED("red_handed", "Red-Handed", "Defeat 5 players in the wilderness.", RequirementType.PLAYER_KILLS, 5),
	REAVER("reaver", "Edge Reaver", "Defeat 10 players in the wilderness.", RequirementType.PLAYER_KILLS, 10),
	HUNTER("hunter", "Rogue Hunter", "Defeat 25 players in the wilderness.", RequirementType.PLAYER_KILLS, 25),
	REAPER("reaper", "Grave-Reaper", "Defeat 50 players in the wilderness.", RequirementType.PLAYER_KILLS, 50),
	DREADED("dreaded", "Dreadmarked", "Defeat 100 players in the wilderness.", RequirementType.PLAYER_KILLS, 100, TitleScope.UNIQUE),
	WASTELAND_KING("wasteland_king", "Wasteland Crown", "Defeat 250 players in the wilderness.", RequirementType.PLAYER_KILLS, 250, TitleScope.UNIQUE),
	RIVAL_ENDER("rival_ender", "Rival-Ender", "Defeat 500 players in the wilderness.", RequirementType.PLAYER_KILLS, 500, TitleScope.UNIQUE),
	SKULLBREAKER("skullbreaker", "Skullbreaker", "Defeat 1,000 players in the wilderness.", RequirementType.PLAYER_KILLS, 1000, TitleScope.UNIQUE),

	STRIDER("strider", "Trail Strider", "Defeat 10 NPCs.", RequirementType.NPC_KILLS_TOTAL, 10),
	CULLER("culler", "Cullhand", "Defeat 25 NPCs.", RequirementType.NPC_KILLS_TOTAL, 25),
	EXTERMINATOR("exterminator", "Pest-Cleanser", "Defeat 50 NPCs.", RequirementType.NPC_KILLS_TOTAL, 50),
	CLEAVER("cleaver", "Bone-Cleaver", "Defeat 100 NPCs.", RequirementType.NPC_KILLS_TOTAL, 100),
	GRINDER("grinder", "Grindstone", "Defeat 250 NPCs.", RequirementType.NPC_KILLS_TOTAL, 250),
	RELENTLESS("relentless", "Relentless", "Defeat 500 NPCs.", RequirementType.NPC_KILLS_TOTAL, 500),
	SLAYER("slayer", "Slayer of Many", "Defeat 1,000 NPCs.", RequirementType.NPC_KILLS_TOTAL, 1000),
	BLOODLETTER("bloodletter", "Bloodletter", "Defeat 2,500 NPCs.", RequirementType.NPC_KILLS_TOTAL, 2500),
	ENDLESS("endless", "Endless Hunt", "Defeat 5,000 NPCs.", RequirementType.NPC_KILLS_TOTAL, 5000, TitleScope.UNIQUE),
	TEN_THOUSAND_BLADES("ten_thousand_blades", "Ten-Thousand Blades", "Defeat 10,000 NPCs.", RequirementType.NPC_KILLS_TOTAL, 10000, TitleScope.UNIQUE),

	SPECIALIST("specialist", "Marked Specialist", "Defeat the same NPC type 50 times.", RequirementType.NPC_KILLS_ANY, 50),
	STALKER("stalker", "Stalker of One Prey", "Defeat the same NPC type 100 times.", RequirementType.NPC_KILLS_ANY, 100),
	BANE("bane", "Named Bane", "Defeat the same NPC type 250 times.", RequirementType.NPC_KILLS_ANY, 250),
	OBLITERATOR("obliterator", "Obliterator", "Defeat the same NPC type 500 times.", RequirementType.NPC_KILLS_ANY, 500),
	OBSESSION("obsession", "Single-Minded", "Defeat the same NPC type 1,000 times.", RequirementType.NPC_KILLS_ANY, 1000, TitleScope.UNIQUE),
	TRUE_BANE("true_bane", "True Bane", "Defeat the same NPC type 2,000 times.", RequirementType.NPC_KILLS_ANY, 2000, TitleScope.UNIQUE),

	BLADE("blade", "Steelhand", "Reach level 50 Attack.", RequirementType.SKILL_LEVEL, 50, Skill.ATTACK),
	SHIELD("shield", "Shieldborne", "Reach level 50 Defence.", RequirementType.SKILL_LEVEL, 50, Skill.DEFENSE),
	STRONG("strong", "Oak-Strong", "Reach level 50 Strength.", RequirementType.SKILL_LEVEL, 50, Skill.STRENGTH),
	SURVIVOR("survivor", "Scar-Warden", "Reach level 50 Hits.", RequirementType.SKILL_LEVEL, 50, Skill.HITS),
	MARKSMAN("marksman", "Longshot", "Reach level 50 Ranged.", RequirementType.SKILL_LEVEL, 50, Skill.RANGED),
	DEVOUT("devout", "Candlekeeper", "Reach level 50 Prayer.", RequirementType.SKILL_LEVEL, 50, Skill.PRAYER),
	ARCANE("arcane", "Spellscarred", "Reach level 50 Magic.", RequirementType.SKILL_LEVEL, 50, Skill.MAGIC),
	FEASTKEEPER("feastkeeper", "Hearthkeeper", "Reach level 50 Cooking.", RequirementType.SKILL_LEVEL, 50, Skill.COOKING),
	FORESTER("forester", "Axe-Woken", "Reach level 50 Woodcutting.", RequirementType.SKILL_LEVEL, 50, Skill.WOODCUTTING),
	FLETCHER("fletcher", "Bowstring", "Reach level 50 Fletching.", RequirementType.SKILL_LEVEL, 50, Skill.FLETCHING),
	ANGLER("angler", "Deep-Line", "Reach level 50 Fishing.", RequirementType.SKILL_LEVEL, 50, Skill.FISHING),
	FIRESTARTER("firestarter", "Emberhand", "Reach level 50 Firemaking.", RequirementType.SKILL_LEVEL, 50, Skill.FIREMAKING),
	ARTISAN("artisan", "Glass-Shaper", "Reach level 50 Crafting.", RequirementType.SKILL_LEVEL, 50, Skill.CRAFTING),
	IRONHAND("ironhand", "Anvilhand", "Reach level 50 Smithing.", RequirementType.SKILL_LEVEL, 50, Skill.SMITHING),
	PROSPECTOR("prospector", "Stone-Seeker", "Reach level 50 Mining.", RequirementType.SKILL_LEVEL, 50, Skill.MINING),
	HERBALIST("herbalist", "Greenblood", "Reach level 50 Herblaw.", RequirementType.SKILL_LEVEL, 50, Skill.HERBLAW),
	SWIFT("swift", "Quickstep", "Reach level 50 Agility.", RequirementType.SKILL_LEVEL, 50, Skill.AGILITY),
	SHADOWHAND("shadowhand", "Shadowfinger", "Reach level 50 Thieving.", RequirementType.SKILL_LEVEL, 50, Skill.THIEVING),

	SWORD_SAINT("sword_saint", "Sword Saint", "Reach level 90 Attack.", RequirementType.SKILL_LEVEL, 90, Skill.ATTACK, TitleScope.UNIQUE),
	BULWARK("bulwark", "Wall of Iron", "Reach level 90 Defence.", RequirementType.SKILL_LEVEL, 90, Skill.DEFENSE, TitleScope.UNIQUE),
	MOUNTAIN("mountain", "Storm-Shouldered", "Reach level 90 Strength.", RequirementType.SKILL_LEVEL, 90, Skill.STRENGTH, TitleScope.UNIQUE),
	DEATHLESS("deathless", "Deathless", "Reach level 90 Hits.", RequirementType.SKILL_LEVEL, 90, Skill.HITS, TitleScope.UNIQUE),
	SHARPSHOOTER("sharpshooter", "Eagle-Eyed", "Reach level 90 Ranged.", RequirementType.SKILL_LEVEL, 90, Skill.RANGED, TitleScope.UNIQUE),
	SANCTIFIED("sanctified", "Sanctified Flame", "Reach level 90 Prayer.", RequirementType.SKILL_LEVEL, 90, Skill.PRAYER, TitleScope.UNIQUE),
	ARCHMAGE("archmage", "Archmage of Ash", "Reach level 90 Magic.", RequirementType.SKILL_LEVEL, 90, Skill.MAGIC, TitleScope.UNIQUE),
	BANQUET_LORD("banquet_lord", "Banquet Lord", "Reach level 90 Cooking.", RequirementType.SKILL_LEVEL, 90, Skill.COOKING, TitleScope.UNIQUE),
	TIMBERMASTER("timbermaster", "Timber-King", "Reach level 90 Woodcutting.", RequirementType.SKILL_LEVEL, 90, Skill.WOODCUTTING, TitleScope.UNIQUE),
	BOWYER("bowyer", "Master Bowyer", "Reach level 90 Fletching.", RequirementType.SKILL_LEVEL, 90, Skill.FLETCHING, TitleScope.UNIQUE),
	DEEPCALLER("deepcaller", "Deepcaller", "Reach level 90 Fishing.", RequirementType.SKILL_LEVEL, 90, Skill.FISHING, TitleScope.UNIQUE),
	FLAMEKEEPER("flamekeeper", "Flamekeeper", "Reach level 90 Firemaking.", RequirementType.SKILL_LEVEL, 90, Skill.FIREMAKING, TitleScope.UNIQUE),
	MASTERWORK("masterwork", "Masterwork", "Reach level 90 Crafting.", RequirementType.SKILL_LEVEL, 90, Skill.CRAFTING, TitleScope.UNIQUE),
	ANVIL_KING("anvil_king", "Anvil King", "Reach level 90 Smithing.", RequirementType.SKILL_LEVEL, 90, Skill.SMITHING, TitleScope.UNIQUE),
	STONEBREAKER("stonebreaker", "Stonebreaker", "Reach level 90 Mining.", RequirementType.SKILL_LEVEL, 90, Skill.MINING, TitleScope.UNIQUE),
	ALCHEMIST("alchemist", "White Alchemist", "Reach level 90 Herblaw.", RequirementType.SKILL_LEVEL, 90, Skill.HERBLAW, TitleScope.UNIQUE),
	QUICKSTEP("quickstep", "Fleetfoot", "Reach level 90 Agility.", RequirementType.SKILL_LEVEL, 90, Skill.AGILITY, TitleScope.UNIQUE),
	NIGHTFINGER("nightfinger", "Nightfinger", "Reach level 90 Thieving.", RequirementType.SKILL_LEVEL, 90, Skill.THIEVING, TitleScope.UNIQUE),

	VOIDBANE("voidbane", "Voidbane", "Defeat the Void Knight in the Death Match Arena.", RequirementType.MANUAL),
	VOIDWALKER("voidwalker", "Voidwalker", "Complete a major Voidscape encounter.", RequirementType.MANUAL),
	VOID_TOUCHED("void_touched", "Void-Touched", "Earn a rare void-themed reward.", RequirementType.MANUAL),
	AUCTIONEER("auctioneer", "Auctioneer", "Earn through auction house activity.", RequirementType.MANUAL),
	MERCHANT("merchant", "Market-Marked", "Build a trade reputation through the auction house.", RequirementType.MANUAL),
	RARESTRUCK("rarestruck", "Rarestruck", "Find a rare drop marked by a loot beam.", RequirementType.MANUAL),
	BEAMCALLER("beamcaller", "Beamcaller", "Customize and trigger a loot beam.", RequirementType.MANUAL),
	FOUNDER("founder", "Founder", "Reserved for early Voidscape supporters.", RequirementType.MANUAL),
	OLD_GUARD("old_guard", "Old Guard", "Reserved for veteran accounts.", RequirementType.MANUAL),
	VANQUISHER("vanquisher", "Vanquisher", "Win a high-stakes Voidscape challenge.", RequirementType.MANUAL, TitleScope.UNIQUE),
	PATHFINDER("pathfinder", "Pathfinder", "Complete an exploration milestone.", RequirementType.MANUAL, TitleScope.UNIQUE),
	RIFTBOUND("riftbound", "Riftbound", "Win a major rift event.", RequirementType.MANUAL, TitleScope.UNIQUE),
	MARKET_MAKER("market_maker", "Market Maker", "Lead a major auction-house milestone.", RequirementType.MANUAL, TitleScope.UNIQUE),
	RELIC_KEEPER("relic_keeper", "Relic-Keeper", "Hold a one-of-one Voidscape relic.", RequirementType.MANUAL, TitleScope.UNIQUE);

	public static final String ACTIVE_TITLE_CACHE = "player_title_active";
	public static final int OVERHEAD_TITLE_CLIENT_VERSION = 10052;
	private static final String UNLOCK_CACHE_PREFIX = "pt_u_";
	private static final String LEGACY_UNLOCK_CACHE_PREFIX = "player_title_unlocked_";
	private static final String BLOCKED_UNIQUE_CACHE_PREFIX = "pt_b_";
	private static final String UNIQUE_CLAIM_CACHE = "pt_unique_claim";

	private final String id;
	private final String displayName;
	private final String unlockHint;
	private final RequirementType requirementType;
	private final int threshold;
	private final Skill skill;
	private final TitleScope scope;

	PlayerTitle(String id, String displayName, String unlockHint) {
		this(id, displayName, unlockHint, RequirementType.MANUAL, 0, null, TitleScope.REUSABLE);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType) {
		this(id, displayName, unlockHint, requirementType, 0, null, TitleScope.REUSABLE);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType, TitleScope scope) {
		this(id, displayName, unlockHint, requirementType, 0, null, scope);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType, int threshold) {
		this(id, displayName, unlockHint, requirementType, threshold, null, TitleScope.REUSABLE);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType, int threshold, TitleScope scope) {
		this(id, displayName, unlockHint, requirementType, threshold, null, scope);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType, int threshold, Skill skill) {
		this(id, displayName, unlockHint, requirementType, threshold, skill, TitleScope.REUSABLE);
	}

	PlayerTitle(String id, String displayName, String unlockHint, RequirementType requirementType, int threshold, Skill skill, TitleScope scope) {
		this.id = id;
		this.displayName = displayName;
		this.unlockHint = unlockHint;
		this.requirementType = requirementType;
		this.threshold = threshold;
		this.skill = skill;
		this.scope = scope;
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

	public String catalogHint() {
		switch (requirementType) {
			case TOTAL_LEVEL:
				return "Total " + threshold + ".";
			case COMBAT_LEVEL:
				return "Combat " + threshold + ".";
			case QUEST_POINTS:
				return threshold + " quest points.";
			case PLAYER_KILLS:
				return threshold == 1 ? "1 Wilderness PK." : threshold + " Wilderness PKs.";
			case NPC_KILLS_TOTAL:
				return threshold + " NPC kills.";
			case NPC_KILLS_ANY:
				return threshold + " kills on one NPC type.";
			case SKILL_LEVEL:
				return "Level " + threshold + " " + skillName(skill) + ".";
			case MANUAL:
			default:
				return manualCatalogHint();
		}
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
			case QUEST_POINTS:
				return "Progress: " + player.getQuestPoints() + "/" + threshold + " quest points.";
			case PLAYER_KILLS:
				return "Progress: " + formatNumber(player.getKills()) + "/" + formatNumber(threshold) + " Wilderness PKs.";
			case NPC_KILLS_TOTAL:
				return "Progress: " + formatNumber(totalNpcKills(player)) + "/" + formatNumber(threshold) + " NPC kills.";
			case NPC_KILLS_ANY:
				return "Progress: " + formatNumber(highestSingleNpcKillCount(player)) + "/" + formatNumber(threshold) + " on one NPC type.";
			case SKILL_LEVEL:
				return "Progress: " + currentSkillLevel(player, skill) + "/" + threshold + " " + skillName(skill) + ".";
			case MANUAL:
			default:
				return "Progress: manual unlock.";
		}
	}

	public boolean unique() {
		return scope == TitleScope.UNIQUE;
	}

	public String scopeLabel() {
		return unique() ? "unique" : "reusable";
	}

	public String rarityLabel() {
		if (unique()) {
			return "unique";
		}
		int score = rarityScore();
		if (score >= 10000) {
			return "rare";
		}
		if (score >= 2500) {
			return "uncommon";
		}
		return "common";
	}

	public int rarityScore() {
		int score = unique() ? 100000 : 0;
		switch (requirementType) {
			case TOTAL_LEVEL:
				return score + threshold;
			case COMBAT_LEVEL:
				return score + threshold * 20;
			case QUEST_POINTS:
				return score + threshold * 30;
			case PLAYER_KILLS:
				return score + threshold * 15;
			case NPC_KILLS_TOTAL:
				return score + threshold;
			case NPC_KILLS_ANY:
				return score + threshold * 5;
			case SKILL_LEVEL:
				return score + threshold * 30;
			case MANUAL:
			default:
				return score + 5000;
		}
	}

	public boolean automatic() {
		return requirementType != RequirementType.MANUAL;
	}

	public String cacheKey() {
		return UNLOCK_CACHE_PREFIX + id;
	}

	public String legacyCacheKey() {
		return LEGACY_UNLOCK_CACHE_PREFIX + id;
	}

	private String blockedCacheKey() {
		return BLOCKED_UNIQUE_CACHE_PREFIX + id;
	}

	public boolean isUnlocked(Player player) {
		if (!hasUnlockCache(player)) {
			return false;
		}
		if (!unique()) {
			return true;
		}
		return uniqueClaim(player) == this && !isClaimedByOther(player, this);
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
			case QUEST_POINTS:
				return player.getQuestPoints() >= threshold;
			case PLAYER_KILLS:
				return player.getKills() >= threshold;
			case NPC_KILLS_TOTAL:
				return totalNpcKills(player) >= threshold;
			case NPC_KILLS_ANY:
				return highestSingleNpcKillCount(player) >= threshold;
			case SKILL_LEVEL:
				return hasSkillLevel(player, skill, threshold);
			case MANUAL:
			default:
				return false;
		}
	}

	public static synchronized boolean unlock(Player player, PlayerTitle title) {
		if (player == null || title == null) {
			return false;
		}
		if (title.hasUnlockCache(player)) {
			if (title.unique() && uniqueClaim(player) == null) {
				claimUnique(player, title);
			}
			return false;
		}
		if (title.unique()) {
			PlayerTitle currentUnique = uniqueClaim(player);
			if (currentUnique != null && currentUnique != title) {
				player.getCache().store(title.blockedCacheKey(), true);
				return false;
			}
			String owner = ownerName(player, title);
			if (owner != null && !owner.equalsIgnoreCase(player.getUsername())) {
				player.getCache().store(title.blockedCacheKey(), true);
				return false;
			}
		}

		player.getCache().store(title.cacheKey(), true);
		if (title.unique()) {
			claimUnique(player, title);
		}
		player.message("@mag@Title unlocked: @red@" + title.displayName() + "@whi@.");
		if (active(player) == null) {
			setActive(player, title);
		}
		return true;
	}

	public static int refreshAutomaticUnlocks(Player player) {
		if (player == null) {
			return 0;
		}
		uniqueClaim(player);
		int unlocked = 0;
		for (PlayerTitle title : values()) {
			if (title.automatic() && !title.hasUnlockCache(player)
				&& !player.getCache().hasKey(title.blockedCacheKey()) && title.qualifies(player)) {
				if (unlock(player, title)) {
					unlocked++;
				}
			}
		}
		return unlocked;
	}

	public static List<PlayerTitle> unlocked(Player player) {
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

	public static String activePrefix(Player player) {
		PlayerTitle title = active(player);
		return title == null ? "" : "@red@[" + title.displayName() + "]@whi@ ";
	}

	public static String activeOverhead(Player player) {
		PlayerTitle title = active(player);
		return title == null ? "" : title.displayName();
	}

	public static void setActive(Player player, PlayerTitle title) {
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

	public static PlayerTitle uniqueClaim(Player player) {
		if (player == null) {
			return null;
		}

		if (player.getCache().hasKey(UNIQUE_CLAIM_CACHE)) {
			PlayerTitle claimed = byId(player.getCache().getString(UNIQUE_CLAIM_CACHE));
			if (claimed != null && claimed.unique() && claimed.hasUnlockCache(player)) {
				return claimed;
			}
			player.getCache().remove(UNIQUE_CLAIM_CACHE);
		}

		PlayerTitle active = activeRaw(player);
		if (active != null && active.unique() && active.hasUnlockCache(player)) {
			claimUnique(player, active);
			return active;
		}

		for (PlayerTitle title : values()) {
			if (title.unique() && title.hasUnlockCache(player)) {
				claimUnique(player, title);
				return title;
			}
		}
		return null;
	}

	private static PlayerTitle activeRaw(Player player) {
		if (player == null || !player.getCache().hasKey(ACTIVE_TITLE_CACHE)) {
			return null;
		}
		return byId(player.getCache().getString(ACTIVE_TITLE_CACHE));
	}

	private static void claimUnique(Player player, PlayerTitle title) {
		if (player != null && title != null && title.unique()) {
			player.getCache().store(UNIQUE_CLAIM_CACHE, title.id());
		}
	}

	public static String ownerName(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.unique()) {
			return null;
		}

		String onlineOwner = onlineUniqueOwnerName(player, title);
		return onlineOwner != null ? onlineOwner : databaseUniqueOwnerName(player, title);
	}

	public static boolean isClaimedByOther(Player player, PlayerTitle title) {
		if (player == null || title == null || !title.unique()) {
			return false;
		}
		String owner = ownerName(player, title);
		return owner != null && !owner.equalsIgnoreCase(player.getUsername());
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

	private String manualCatalogHint() {
		switch (this) {
			case VOIDBANE:
				return "Kill Void Knight.";
			case VOIDWALKER:
				return "Major Voidscape encounter.";
			case VOID_TOUCHED:
				return "Rare void-themed reward.";
			case AUCTIONEER:
				return "Auction House activity.";
			case MERCHANT:
				return "Auction House reputation.";
			case RARESTRUCK:
				return "Find a loot-beam drop.";
			case BEAMCALLER:
				return "Customize a loot beam.";
			case FOUNDER:
				return "Early supporter reward.";
			case VANQUISHER:
				return "High-stakes challenge win.";
			case PATHFINDER:
				return "Exploration milestone.";
			case RIFTBOUND:
				return "Major rift event.";
			case MARKET_MAKER:
				return "Auction-house milestone.";
			case RELIC_KEEPER:
				return "One-of-one relic.";
			case OLD_GUARD:
				return "Veteran account.";
			default:
				return unlockHint;
		}
	}

	private static String skillName(Skill skill) {
		if (skill == null || skill.name() == null) {
			return "skill";
		}
		String value = skill.name().toLowerCase();
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private static String onlineUniqueOwnerName(Player player, PlayerTitle title) {
		for (Player candidate : player.getWorld().getPlayers()) {
			if (uniqueClaim(candidate) == title) {
				return candidate.getUsername();
			}
		}
		return null;
	}

	private static String databaseUniqueOwnerName(Player player, PlayerTitle title) {
		try {
			return player.getWorld().getServer().getDatabase().queryPlayerCacheOwner(UNIQUE_CLAIM_CACHE, title.id());
		} catch (GameDatabaseException ex) {
			return null;
		}
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

	private enum RequirementType {
		MANUAL,
		TOTAL_LEVEL,
		COMBAT_LEVEL,
		QUEST_POINTS,
		PLAYER_KILLS,
		NPC_KILLS_TOTAL,
		NPC_KILLS_ANY,
		SKILL_LEVEL
	}

	private enum TitleScope {
		REUSABLE,
		UNIQUE
	}
}
