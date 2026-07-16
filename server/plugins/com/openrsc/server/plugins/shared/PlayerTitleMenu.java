package com.openrsc.server.plugins.shared;

import com.openrsc.server.content.PlayerTitle;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.struct.PlayerCacheOwner;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.openrsc.server.plugins.Functions.multi;

/** Shared read-only title catalog and Void Herald title registrar. */
public final class PlayerTitleMenu {
	private static final int CATALOG_PAGE_SIZE = 8;
	private static final int MENU_OPTION_LIMIT = 20;
	private static final PlayerTitle[] RECOGNIZED_HONORIFICS = {
		PlayerTitle.SAINT,
		PlayerTitle.KNIGHT
	};

	private PlayerTitleMenu() {
	}

	public static void handleBrowseCommand(Player player, String[] args) {
		PlayerTitle.refreshAutomaticUnlocks(player);
		if (args.length == 0) {
			openBrowse(player);
			return;
		}

		String action = args[0].toLowerCase();
		if (action.equals("count")) {
			player.message("You have unlocked @gre@" + PlayerTitle.unlockedCount(player)
				+ "@whi@/" + PlayerTitle.values().length + " titles.");
			return;
		}
		if (action.equals("list") || action.equals("all") || action.equals("catalog")) {
			showCatalogFromArgs(player, CatalogView.ALL, args, 1);
			return;
		}
		CatalogView view = parseCatalogView(action);
		if (view != null) {
			showCatalogFromArgs(player, view, args, 1);
			return;
		}
		if (action.equals("clear") || action.equals("off") || action.equals("none")
			|| PlayerTitle.byId(joinArgs(args, 0)) != null) {
			player.message("The Void Herald registers title changes. Use ::titles to browse your catalog.");
			return;
		}
		player.message("Unknown title option. Use ::titles to browse the title catalog.");
	}

	public static void openBrowse(Player player) {
		openHub(player, MenuMode.BROWSE);
	}

	public static void openManage(Player player) {
		PlayerTitle.refreshAutomaticUnlocks(player);
		openHub(player, MenuMode.MANAGE);
	}

	private static void openHub(Player player, MenuMode mode) {
		while (true) {
			PlayerTitle activeTitle = PlayerTitle.active(player);
			PlayerTitle activeHonorific = PlayerTitle.activeHonorific(player);
			List<String> options = new ArrayList<>();
			options.add("Active: " + (activeHonorific == null ? "no honorific" : activeHonorific.prefixForm())
				+ " | " + (activeTitle == null ? "no title" : activeTitle.displayName()));

			if (mode == MenuMode.MANAGE) {
				int suffixOption = options.size();
				options.add("Wear or change suffix title");
				int honorificOption = options.size();
				options.add("Wear or change honorific");
				int browseOption = options.size();
				options.add("Browse the full title catalog");
				int courtOption = options.size();
				options.add("The Court");
				int clearTitleOption = -1;
				int clearHonorificOption = -1;
				int clearBothOption = -1;
				if (activeTitle != null) {
					clearTitleOption = options.size();
					options.add("Clear suffix title (free)");
				}
				if (activeHonorific != null) {
					clearHonorificOption = options.size();
					options.add("Clear honorific (free)");
				}
				if (activeTitle != null && activeHonorific != null) {
					clearBothOption = options.size();
					options.add("Clear both (free)");
				}
				int closeOption = options.size();
				options.add("Close");

				int option = titleMulti(player, options);
				if (option < 0 || option == closeOption) return;
				if (option == 0) continue;
				if (option == suffixOption) {
					if (showCatalog(player, CatalogView.UNLOCKED, 0, mode, SlotFilter.SUFFIX) == MenuExit.CLOSE) return;
				} else if (option == honorificOption) {
					if (showCatalog(player, CatalogView.UNLOCKED, 0, mode, SlotFilter.PREFIX) == MenuExit.CLOSE) return;
				} else if (option == browseOption) {
					if (showCatalog(player, CatalogView.ALL, 0, mode, SlotFilter.ALL) == MenuExit.CLOSE) return;
				} else if (option == courtOption) {
					if (showCourt(player, 0, mode) == MenuExit.CLOSE) return;
				} else if (option == clearTitleOption) {
					confirmClear(player, ClearTarget.TITLE);
				} else if (option == clearHonorificOption) {
					confirmClear(player, ClearTarget.HONORIFIC);
				} else if (option == clearBothOption) {
					confirmClear(player, ClearTarget.BOTH);
				}
				continue;
			}

			int unlockedOption = options.size();
			options.add("Unlocked titles (" + PlayerTitle.unlockedCount(player) + "/" + PlayerTitle.values().length + ")");
			int renownOption = options.size(); options.add("Renown titles");
			int featOption = options.size(); options.add("Feat titles");
			int supremeOption = options.size(); options.add("Supreme titles");
			int uniqueOption = options.size(); options.add("Unique titles");
			int courtOption = options.size(); options.add("The Court");
			int allOption = options.size(); options.add("All titles");
			int closeOption = options.size(); options.add("Close");

			int option = titleMulti(player, options);
			if (option < 0 || option == closeOption) return;
			if (option == 0) continue;
			MenuExit exit = MenuExit.BACK;
			if (option == unlockedOption) exit = showCatalog(player, CatalogView.UNLOCKED, 0, mode, SlotFilter.ALL);
			else if (option == allOption) exit = showCatalog(player, CatalogView.ALL, 0, mode, SlotFilter.ALL);
			else if (option == renownOption) exit = showCatalog(player, CatalogView.RENOWN, 0, mode, SlotFilter.ALL);
			else if (option == featOption) exit = showCatalog(player, CatalogView.FEAT, 0, mode, SlotFilter.ALL);
			else if (option == supremeOption) exit = showCatalog(player, CatalogView.SUPREME, 0, mode, SlotFilter.ALL);
			else if (option == uniqueOption) exit = showCatalog(player, CatalogView.UNIQUE, 0, mode, SlotFilter.ALL);
			else if (option == courtOption) exit = showCourt(player, 0, mode);
			if (exit == MenuExit.CLOSE) return;
		}
	}

	private static MenuExit showCatalog(Player player, CatalogView view, int page, MenuMode mode, SlotFilter filter) {
		if (view == CatalogView.COURT) {
			return showCourt(player, page, mode);
		}
		List<PlayerTitle> titles = catalogTitles(player, view, filter);
		int totalPages = Math.max(1, (titles.size() + CATALOG_PAGE_SIZE - 1) / CATALOG_PAGE_SIZE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		while (true) {
			List<String> options = new ArrayList<>();
			int start = page * CATALOG_PAGE_SIZE;
			int end = Math.min(titles.size(), start + CATALOG_PAGE_SIZE);
			String showing = titles.isEmpty() ? "0-0 of 0" : (start + 1) + "-" + end + " of " + titles.size();
			options.add(view.label + " - page " + (page + 1) + "/" + totalPages + " - showing " + showing
				+ " - unlocked " + PlayerTitle.unlockedCount(player) + "/" + PlayerTitle.values().length);

			int allViewOption = -1, unlockedViewOption = -1, renownViewOption = -1;
			int featViewOption = -1, supremeViewOption = -1, uniqueViewOption = -1, courtViewOption = -1;
			if (player.isUsingCustomClient()) {
				allViewOption = options.size(); options.add("View all titles");
				unlockedViewOption = options.size(); options.add("View unlocked titles");
				renownViewOption = options.size(); options.add("View renown titles");
				featViewOption = options.size(); options.add("View feat titles");
				supremeViewOption = options.size(); options.add("View supreme titles");
				uniqueViewOption = options.size(); options.add("View unique titles");
				courtViewOption = options.size(); options.add("View The Court");
			}

			int firstTitleOption = options.size();
			for (int i = start; i < end; i++) options.add(catalogOption(player, titles.get(i)));
			if (titles.isEmpty()) options.add("No titles in this category yet");
			int previousOption = -1, nextOption = -1;
			if (page > 0) { previousOption = options.size(); options.add("< Previous page"); }
			if (page + 1 < totalPages) { nextOption = options.size(); options.add("Next page >"); }
			int categoriesOption = options.size(); options.add("Change category");
			int closeOption = options.size(); options.add("Close");

			int option = titleMulti(player, options);
			if (option < 0 || option == closeOption) return MenuExit.CLOSE;
			if (option == 0) continue;
			CatalogView selectedView = null;
			if (option == allViewOption) selectedView = CatalogView.ALL;
			else if (option == unlockedViewOption) selectedView = CatalogView.UNLOCKED;
			else if (option == renownViewOption) selectedView = CatalogView.RENOWN;
			else if (option == featViewOption) selectedView = CatalogView.FEAT;
			else if (option == supremeViewOption) selectedView = CatalogView.SUPREME;
			else if (option == uniqueViewOption) selectedView = CatalogView.UNIQUE;
			if (selectedView != null) {
				view = selectedView;
				titles = catalogTitles(player, view, filter);
				totalPages = Math.max(1, (titles.size() + CATALOG_PAGE_SIZE - 1) / CATALOG_PAGE_SIZE);
				page = 0;
				continue;
			}
			if (option == courtViewOption) return showCourt(player, 0, mode);
			if (option == previousOption) { page = Math.max(0, page - 1); continue; }
			if (option == nextOption) { page = Math.min(totalPages - 1, page + 1); continue; }
			if (option == categoriesOption) return MenuExit.BACK;

			int titleIndex = start + option - firstTitleOption;
			if (titleIndex >= start && titleIndex < end
				&& !showDetail(player, titles.get(titleIndex), view, page, mode)) return MenuExit.CLOSE;
		}
	}

	private static boolean showDetail(Player player, PlayerTitle title, CatalogView returnView,
			int returnPage, MenuMode mode) {
		while (true) {
			List<String> options = new ArrayList<>();
			options.add("Title details: " + (title.honorific() ? title.prefixForm() : title.displayName()));
			options.add("Requirement - " + title.unlockHint());
			options.add(title.requirementProgress(player));
			options.add("Tier: " + catalogTier(title));
			options.add("Position: " + (title.honorific() ? "prefix honorific" : "suffix title"));
			options.add(detailScope(title));
			if (title.unique()) options.add(detailUnique(player, title));
			options.add("Status: " + detailState(player, title));
			if (title.honorific() && title.isUnlocked(player)) {
				String date = PlayerTitle.honorificDate(player, title);
				if (!date.isEmpty()) options.add("Recognized: " + date + ".");
			}

			int wearOption = -1;
			if (mode == MenuMode.MANAGE && title.isUnlocked(player) && !PlayerTitle.isActive(player, title)) {
				wearOption = options.size();
				options.add("Wear for " + formatCoins(PlayerTitle.WEAR_COST_COINS) + " coins");
			}
			int backOption = options.size();
			options.add("Back to " + returnView.label + " page " + (returnPage + 1));
			int closeOption = options.size(); options.add("Close");
			int option = titleMulti(player, options);
			if (option < 0 || option == closeOption) return false;
			if (option == backOption) return true;
			if (option == wearOption) {
				confirmWear(player, title);
				return true;
			}
		}
	}

	private static void confirmWear(Player player, PlayerTitle title) {
		int confirm = titleMulti(player, listOf(
			"Pay " + formatCoins(PlayerTitle.WEAR_COST_COINS) + " coins",
			"Cancel"));
		if (confirm != 0) return;
		PlayerTitle.WearResult result = PlayerTitle.tryWear(player, title);
		switch (result) {
			case WORN:
				player.message("The Void Herald registers " + title.tierColorToken() + title.displayName()
					+ "@whi@ as your active " + (title.honorific() ? "honorific" : "title") + ".");
				break;
			case ALREADY_ACTIVE:
				player.message("You are already wearing that " + (title.honorific() ? "honorific" : "title") + ". No coins were taken.");
				break;
			case NOT_UNLOCKED:
				player.message("You no longer hold that title. No coins were taken.");
				break;
			case INSUFFICIENT_COINS:
				player.message("You need " + formatCoins(PlayerTitle.WEAR_COST_COINS) + " coins in your pack. No coins were taken.");
				break;
			case PAYMENT_FAILED:
			default:
				player.message("The registration could not be completed. No coins were taken.");
				break;
		}
	}

	private static void confirmClear(Player player, ClearTarget target) {
		player.message("Clearing is free, but wearing it again costs "
			+ formatCoins(PlayerTitle.WEAR_COST_COINS) + " coins.");
		int confirm = titleMulti(player, listOf("Clear it for free", "Keep it"));
		if (confirm != 0) return;
		switch (target) {
			case TITLE:
				PlayerTitle.clearActiveTitle(player);
				player.message("Your suffix title has been cleared.");
				break;
			case HONORIFIC:
				PlayerTitle.clearActiveHonorific(player);
				player.message("Your honorific has been cleared.");
				break;
			case BOTH:
				PlayerTitle.clearActive(player);
				player.message("Your suffix title and honorific have been cleared.");
				break;
		}
	}

	private static MenuExit showCourt(Player player, int page, MenuMode mode) {
		List<CourtEntry> entries = courtEntries(player);
		int totalPages = Math.max(1, (entries.size() + CATALOG_PAGE_SIZE - 1) / CATALOG_PAGE_SIZE);
		page = Math.max(0, Math.min(page, totalPages - 1));
		while (true) {
			int start = page * CATALOG_PAGE_SIZE;
			int end = Math.min(entries.size(), start + CATALOG_PAGE_SIZE);
			String showing = entries.isEmpty() ? "0-0 of 0" : (start + 1) + "-" + end + " of " + entries.size();
			List<String> options = new ArrayList<>();
			options.add("The Court - page " + (page + 1) + "/" + totalPages + " - showing " + showing);

			int allOption = -1, unlockedOption = -1, renownOption = -1, featOption = -1;
			int supremeOption = -1, uniqueOption = -1, courtOption = -1;
			if (player.isUsingCustomClient()) {
				allOption = options.size(); options.add("View all titles");
				unlockedOption = options.size(); options.add("View unlocked titles");
				renownOption = options.size(); options.add("View renown titles");
				featOption = options.size(); options.add("View feat titles");
				supremeOption = options.size(); options.add("View supreme titles");
				uniqueOption = options.size(); options.add("View unique titles");
				courtOption = options.size(); options.add("View The Court");
			}
			int firstRowOption = options.size();
			for (int i = start; i < end; i++) options.add(courtOption(player, entries.get(i)));
			if (entries.isEmpty()) options.add("No honorific holders yet");
			int previousOption = -1, nextOption = -1;
			if (page > 0) { previousOption = options.size(); options.add("< Previous page"); }
			if (page + 1 < totalPages) { nextOption = options.size(); options.add("Next page >"); }
			int categoriesOption = options.size(); options.add("Change category");
			int closeOption = options.size(); options.add("Close");

			int option = titleMulti(player, options);
			if (option < 0 || option == closeOption) return MenuExit.CLOSE;
			if (option == 0 || option == courtOption) continue;
			if (option == allOption) return showCatalog(player, CatalogView.ALL, 0, mode, SlotFilter.ALL);
			if (option == unlockedOption) return showCatalog(player, CatalogView.UNLOCKED, 0, mode, SlotFilter.ALL);
			if (option == renownOption) return showCatalog(player, CatalogView.RENOWN, 0, mode, SlotFilter.ALL);
			if (option == featOption) return showCatalog(player, CatalogView.FEAT, 0, mode, SlotFilter.ALL);
			if (option == supremeOption) return showCatalog(player, CatalogView.SUPREME, 0, mode, SlotFilter.ALL);
			if (option == uniqueOption) return showCatalog(player, CatalogView.UNIQUE, 0, mode, SlotFilter.ALL);
			if (option == previousOption) { page = Math.max(0, page - 1); continue; }
			if (option == nextOption) { page = Math.min(totalPages - 1, page + 1); continue; }
			if (option == categoriesOption) return MenuExit.BACK;
			if (option >= firstRowOption && option < firstRowOption + Math.max(1, end - start)) continue;
		}
	}

	private static List<CourtEntry> courtEntries(Player player) {
		List<CourtEntry> entries = new ArrayList<>();
		String warlord = PlayerTitle.ownerName(player, PlayerTitle.WARLORD_WASTES);
		if (warlord != null) {
			int score = PlayerTitle.currentContestedScore(player, PlayerTitle.WARLORD_WASTES);
			entries.add(new CourtEntry("Warlord " + warlord, "Unique", PlayerTitle.Tier.UNIQUE.code(),
				warlord, "this month", score > 0 ? "reigning - " + score + " kills" : "reigning",
				PlayerTitle.activeHonorific(player) == PlayerTitle.WARLORD_WASTES));
		}
		boolean failed = false;
		for (PlayerTitle honorific : RECOGNIZED_HONORIFICS) {
			try {
				PlayerCacheOwner[] holders = player.getWorld().getServer().getDatabase()
					.queryPlayerCacheOwners(PlayerTitle.honorificDateCacheKey(honorific), 1000);
				for (PlayerCacheOwner holder : holders) {
					String date = PlayerTitle.formatEpochDate(parseLong(holder.cacheValue));
					entries.add(new CourtEntry(honorific.prefixForm() + " " + holder.username,
						honorific.tableTierLabel(), honorific.tier().code(), holder.username,
						date.isEmpty() ? "date unknown" : date, "recognized",
						player.getDatabaseID() == holder.playerId && PlayerTitle.activeHonorific(player) == honorific));
				}
			} catch (GameDatabaseException ignored) {
				failed = true;
			}
		}
		if (failed) player.message("The Court roster could not be loaded right now.");
		return entries;
	}

	private static String catalogOption(Player player, PlayerTitle title) {
		boolean active = PlayerTitle.isActive(player, title);
		String fallback = (active ? "* " : "") + title.displayName() + (title.honorific() ? " [prefix]" : "")
			+ " - " + catalogTier(title) + " - " + catalogState(player, title);
		if (!player.isUsingCustomClient()) return fallback;
		return fallback + " ~vstitle~" + safeCell(title.tableTitle()) + "|" + safeCell(title.tableTierLabel())
			+ "|" + title.tier().code() + "|" + (title.recordTitle() ? "1" : "0") + "|"
			+ safeCell(title.tableHolder(player)) + "|" + safeCell(title.tableAge(player)) + "|"
			+ safeCell(catalogStatePlain(player, title)) + "|" + (active ? "1" : "0") + "|"
			+ (title.honorific() ? "prefix" : "suffix");
	}

	private static String courtOption(Player player, CourtEntry entry) {
		String fallback = (entry.active ? "* " : "") + entry.name + " - " + entry.tier
			+ " - " + entry.state + " - " + entry.age;
		if (!player.isUsingCustomClient()) return fallback;
		return fallback + " ~vstitle~" + safeCell(entry.name) + "|" + safeCell(entry.tier) + "|"
			+ entry.tierCode + "|0|" + safeCell(entry.holder) + "|" + safeCell(entry.age) + "|"
			+ safeCell(entry.state) + "|" + (entry.active ? "1" : "0") + "|prefix";
	}

	private static String catalogTier(PlayerTitle title) {
		return title.tierColorToken() + title.tierLabel() + "@whi@";
	}

	private static String catalogState(Player player, PlayerTitle title) {
		if (PlayerTitle.isActive(player, title)) return "@gre@active@whi@";
		if (title.isUnlocked(player)) return "@gre@unlocked@whi@";
		if (title.unique()) {
			String owner = PlayerTitle.ownerName(player, title);
			return owner == null ? "@yel@open@whi@" : "@yel@held by " + owner + "@whi@";
		}
		return "locked";
	}

	private static String catalogStatePlain(Player player, PlayerTitle title) {
		if (PlayerTitle.isActive(player, title)) return "active";
		if (title.isUnlocked(player)) return "unlocked";
		if (title.unique()) return PlayerTitle.ownerName(player, title) == null ? "open" : "held";
		return "locked";
	}

	private static String detailScope(PlayerTitle title) {
		if (!title.unique()) return "Lifecycle: reusable - anyone can earn it.";
		if (title.firstUnique()) return "Lifecycle: first - permanent server history.";
		if (title.contested()) return "Lifecycle: contested - held by the current leader.";
		return "Lifecycle: item-bound - follows the relic holder.";
	}

	private static String detailUnique(Player player, PlayerTitle title) {
		String owner = PlayerTitle.ownerName(player, title);
		if (title.firstUnique()) {
			if (owner == null) return "Claim: unclaimed - first to this deed takes it forever.";
			String date = PlayerTitle.firstClaimDate(player, title);
			return "Claim: " + owner + (date.isEmpty() ? "." : " on " + date + ".");
		}
		if (title.contested()) {
			int score = PlayerTitle.currentContestedScore(player, title);
			if (owner == null) return "Holder: open.";
			return score > 0 ? "Holder: " + owner + " with " + score + "." : "Holder: " + owner + ".";
		}
		return owner == null ? "Holder: open." : "Holder: " + owner + ".";
	}

	private static String detailState(Player player, PlayerTitle title) {
		if (PlayerTitle.isActive(player, title)) return "active";
		if (title.isUnlocked(player)) return "unlocked";
		if (title.unique()) {
			String owner = PlayerTitle.ownerName(player, title);
			if (owner != null) return "locked - held by " + owner + ".";
			if (title.firstUnique()) return "open - first qualifying player claims it.";
			if (title.contested()) return "open - current leader claims it.";
		}
		return "locked";
	}

	private static List<PlayerTitle> catalogTitles(Player player, CatalogView view, SlotFilter filter) {
		List<PlayerTitle> titles = new ArrayList<>();
		for (PlayerTitle title : PlayerTitle.values()) {
			if (filter == SlotFilter.PREFIX && !title.honorific()) continue;
			if (filter == SlotFilter.SUFFIX && title.honorific()) continue;
			if (view == CatalogView.UNLOCKED && !title.isUnlocked(player)) continue;
			if (view == CatalogView.UNIQUE && !title.unique()) continue;
			if (view == CatalogView.RENOWN && title.tier() != PlayerTitle.Tier.RENOWN) continue;
			if (view == CatalogView.FEAT && title.tier() != PlayerTitle.Tier.FEAT) continue;
			if (view == CatalogView.SUPREME && title.tier() != PlayerTitle.Tier.SUPREME) continue;
			titles.add(title);
		}
		titles.sort(Comparator.comparingInt(PlayerTitle::prestigeRank));
		return titles;
	}

	private static void showCatalogFromArgs(Player player, CatalogView defaultView, String[] args, int startArg) {
		CatalogView view = defaultView;
		int pageArg = startArg;
		if (args.length > startArg) {
			CatalogView parsed = parseCatalogView(args[startArg].toLowerCase());
			if (parsed != null) { view = parsed; pageArg = startArg + 1; }
		}
		if (showCatalog(player, view, parsePage(args, pageArg), MenuMode.BROWSE, SlotFilter.ALL) == MenuExit.BACK) {
			openBrowse(player);
		}
	}

	private static CatalogView parseCatalogView(String action) {
		if (action.equals("all") || action.equals("catalog") || action.equals("list")) return CatalogView.ALL;
		if (action.equals("unlocked") || action.equals("owned") || action.equals("mine")) return CatalogView.UNLOCKED;
		if (action.equals("unique") || action.equals("uniques")) return CatalogView.UNIQUE;
		if (action.equals("supreme") || action.equals("supremes") || action.equals("red")) return CatalogView.SUPREME;
		if (action.equals("court") || action.equals("honorific") || action.equals("honorifics")) return CatalogView.COURT;
		if (action.equals("renown") || action.equals("silver") || action.equals("reusable") || action.equals("reusables")) return CatalogView.RENOWN;
		if (action.equals("feat") || action.equals("feats") || action.equals("purple")) return CatalogView.FEAT;
		return null;
	}

	private static int parsePage(String[] args, int pageArg) {
		if (args.length <= pageArg) return 0;
		try { return Math.max(0, Integer.parseInt(args[pageArg]) - 1); }
		catch (NumberFormatException ignored) { return 0; }
	}

	private static int titleMulti(Player player, List<String> options) {
		if (options.size() > MENU_OPTION_LIMIT) {
			player.message("The title menu is temporarily too large to display.");
			return -1;
		}
		// Deliberately NPC-less: catalog pages own their menu lifecycle even when opened by the Herald.
		return multi(player, null, false, options.toArray(new String[0]));
	}

	private static List<String> listOf(String first, String second) {
		List<String> values = new ArrayList<>(2);
		values.add(first);
		values.add(second);
		return values;
	}

	private static String safeCell(String value) {
		return value == null ? "" : value.replace("|", "/").replace("~vstitle~", "");
	}

	private static long parseLong(String value) {
		try { return Long.parseLong(value == null ? "" : value); }
		catch (NumberFormatException ignored) { return 0L; }
	}

	private static String formatCoins(int amount) {
		return String.format("%,d", amount);
	}

	private static String joinArgs(String[] args, int startIndex) {
		StringBuilder value = new StringBuilder();
		for (int i = startIndex; i < args.length; i++) {
			if (value.length() > 0) value.append(' ');
			value.append(args[i]);
		}
		return value.toString();
	}

	private enum MenuMode { BROWSE, MANAGE }
	private enum MenuExit { BACK, CLOSE }
	private enum SlotFilter { ALL, SUFFIX, PREFIX }
	private enum ClearTarget { TITLE, HONORIFIC, BOTH }

	private enum CatalogView {
		ALL("All titles"), UNLOCKED("Unlocked titles"), RENOWN("Renown titles"),
		FEAT("Feat titles"), SUPREME("Supreme titles"), UNIQUE("Unique titles"), COURT("The Court");
		private final String label;
		CatalogView(String label) { this.label = label; }
	}

	private static final class CourtEntry {
		private final String name;
		private final String tier;
		private final int tierCode;
		private final String holder;
		private final String age;
		private final String state;
		private final boolean active;
		private CourtEntry(String name, String tier, int tierCode, String holder,
				String age, String state, boolean active) {
			this.name = name;
			this.tier = tier;
			this.tierCode = tierCode;
			this.holder = holder;
			this.age = age;
			this.state = state;
			this.active = active;
		}
	}
}
