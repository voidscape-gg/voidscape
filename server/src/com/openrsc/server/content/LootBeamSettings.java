package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LootBeamSettings {
	public static final String MODE_DEFAULT = "default";
	public static final String MODE_CUSTOM = "custom";

	private static final String MODE_CACHE_KEY = "lootbeam_mode";
	private static final String ADD_CACHE_PREFIX = "lb_add_";
	private static final String HIDE_CACHE_PREFIX = "lb_hide_";

	private static final Set<Integer> DEFAULT_BEAM_ITEMS = buildDefaultBeamItems();

	private LootBeamSettings() {
	}

	public static boolean isDefaultBeamItem(int itemId) {
		return DEFAULT_BEAM_ITEMS.contains(itemId);
	}

	public static Set<Integer> getDefaultBeamItems() {
		return DEFAULT_BEAM_ITEMS;
	}

	public static boolean shouldShowBeam(Player player, GroundItem groundItem) {
		if (player == null || groundItem == null) {
			return false;
		}
		return shouldShowBeam(player, groundItem.getID());
	}

	public static boolean shouldShowBeam(Player player, int itemId) {
		if (itemId <= ItemId.NOTHING.id()) {
			return false;
		}
		if (MODE_CUSTOM.equals(getMode(player))) {
			return hasAddedItem(player, itemId);
		}
		return (isDefaultBeamItem(itemId) && !hasHiddenItem(player, itemId))
			|| hasAddedItem(player, itemId);
	}

	public static String getMode(Player player) {
		if (player != null && player.getCache().hasKey(MODE_CACHE_KEY)) {
			String mode = player.getCache().getString(MODE_CACHE_KEY);
			if (MODE_CUSTOM.equals(mode)) {
				return MODE_CUSTOM;
			}
		}
		return MODE_DEFAULT;
	}

	public static void setMode(Player player, String mode) {
		if (player == null) {
			return;
		}
		if (MODE_CUSTOM.equals(mode)) {
			player.getCache().store(MODE_CACHE_KEY, MODE_CUSTOM);
		} else {
			player.getCache().store(MODE_CACHE_KEY, MODE_DEFAULT);
		}
	}

	public static void addItem(Player player, int itemId) {
		if (player == null || itemId <= ItemId.NOTHING.id()) {
			return;
		}
		player.getCache().remove(hideKey(itemId));
		player.getCache().store(addKey(itemId), true);
	}

	public static void removeItem(Player player, int itemId) {
		if (player == null || itemId <= ItemId.NOTHING.id()) {
			return;
		}
		player.getCache().remove(addKey(itemId));
		if (MODE_DEFAULT.equals(getMode(player)) && isDefaultBeamItem(itemId)) {
			player.getCache().store(hideKey(itemId), true);
		} else {
			player.getCache().remove(hideKey(itemId));
		}
	}

	public static void reset(Player player) {
		if (player == null) {
			return;
		}
		List<String> keysToRemove = new ArrayList<>();
		for (String key : player.getCache().getCacheMap().keySet()) {
			if (key.equals(MODE_CACHE_KEY) || key.startsWith(ADD_CACHE_PREFIX) || key.startsWith(HIDE_CACHE_PREFIX)) {
				keysToRemove.add(key);
			}
		}
		for (String key : keysToRemove) {
			player.getCache().remove(key);
		}
	}

	public static Set<Integer> getAddedItemIds(Player player) {
		return getStoredItemIds(player, ADD_CACHE_PREFIX);
	}

	public static Set<Integer> getHiddenItemIds(Player player) {
		return getStoredItemIds(player, HIDE_CACHE_PREFIX);
	}

	private static boolean hasAddedItem(Player player, int itemId) {
		return player != null && player.getCache().hasKey(addKey(itemId));
	}

	private static boolean hasHiddenItem(Player player, int itemId) {
		return player != null && player.getCache().hasKey(hideKey(itemId));
	}

	private static Set<Integer> getStoredItemIds(Player player, String prefix) {
		if (player == null) {
			return Collections.emptySet();
		}
		Set<Integer> itemIds = new HashSet<>();
		for (String key : player.getCache().getCacheMap().keySet()) {
			if (!key.startsWith(prefix)) {
				continue;
			}
			try {
				itemIds.add(Integer.parseInt(key.substring(prefix.length())));
			} catch (NumberFormatException ignored) {
				// Ignore malformed cache keys so commands cannot crash the setting UI.
			}
		}
		return itemIds;
	}

	private static String addKey(int itemId) {
		return ADD_CACHE_PREFIX + itemId;
	}

	private static String hideKey(int itemId) {
		return HIDE_CACHE_PREFIX + itemId;
	}

	private static Set<Integer> buildDefaultBeamItems() {
		Set<Integer> ids = new HashSet<>();

		add(ids,
			ItemId.CHRISTMAS_CRACKER,
			ItemId.RED_PARTY_HAT,
			ItemId.YELLOW_PARTY_HAT,
			ItemId.BLUE_PARTY_HAT,
			ItemId.GREEN_PARTY_HAT,
			ItemId.PINK_PARTY_HAT,
			ItemId.WHITE_PARTY_HAT,
			ItemId.TOOTH_KEY_HALF,
			ItemId.LOOP_KEY_HALF,
			ItemId.DRAGONSTONE,
			ItemId.UNCUT_DRAGONSTONE,
			ItemId.RUNE_SPEAR,
			ItemId.POISONED_RUNE_SPEAR,
			ItemId.RUNE_2_HANDED_SWORD,
			ItemId.RUNE_BATTLE_AXE,
			ItemId.RUNE_SQUARE_SHIELD,
			ItemId.RUNE_KITE_SHIELD,
			ItemId.DRAGON_SWORD,
			ItemId.DRAGON_AXE,
			ItemId.DRAGON_MEDIUM_HELMET,
			ItemId.RIGHT_HALF_DRAGON_SQUARE_SHIELD,
			ItemId.LEFT_HALF_DRAGON_SQUARE_SHIELD,
			ItemId.DRAGON_SQUARE_SHIELD,
			ItemId.DRAGON_2_HANDED_SWORD,
			ItemId.KING_BLACK_DRAGON_SCALE,
			ItemId.DRAGON_BAR,
			ItemId.CHIPPED_DRAGON_SCALE,
			ItemId.DRAGON_METAL_CHAIN,
			ItemId.DRAGON_SCALE_MAIL,
			ItemId.DRAGON_SCALE_MAIL_TOP,
			ItemId.VOID_BOW,
			ItemId.VOID_AMULET,
			ItemId.VOID_MACE,
			ItemId.DRAGON_SWORD_HILT,
			ItemId.DRAGON_SWORD_BLADE,
			ItemId.DRAGON_SWORD_TIP,
			ItemId.VOID_KEY
		);

		return Collections.unmodifiableSet(ids);
	}

	private static void add(Set<Integer> ids, ItemId... itemIds) {
		for (ItemId itemId : itemIds) {
			ids.add(itemId.id());
		}
	}
}
