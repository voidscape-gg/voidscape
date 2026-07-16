package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.event.DelayedEvent;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.concurrent.CompletableFuture;

import static com.openrsc.server.plugins.Functions.delay;

public final class VoidDungeonAdmission {
	public static final int ENTRY_FEE_COINS = 100000;

	private static final String ADMISSION_CACHE_KEY = "void_dungeon_admission";
	private static final String DEPTH_CACHE_KEY = "void_dungeon_depth";
	private static final int INITIAL_DEPTH = 1;
	private static final int SAVE_RESERVATION_WAIT_TICKS = 10;
	private static final int SAVE_COMPLETION_WAIT_TICKS = 20;

	private VoidDungeonAdmission() {
	}

	public static boolean isAdmitted(Player player) {
		return player.getCache().hasKey(ADMISSION_CACHE_KEY);
	}

	public static int getDiscoveredDepth(Player player) {
		return player.getCache().hasKey(DEPTH_CACHE_KEY)
			? player.getCache().getInt(DEPTH_CACHE_KEY)
			: 0;
	}

	public static boolean discoverDepth(Player player, int depth) {
		if (depth < INITIAL_DEPTH) {
			throw new IllegalArgumentException("Void Dungeon depth must be positive");
		}
		if (!isAdmitted(player) || depth <= getDiscoveredDepth(player)) {
			return false;
		}
		player.getCache().set(DEPTH_CACHE_KEY, depth);
		return true;
	}

	public static boolean purchaseAndPersist(Player player) {
		if (isAdmitted(player)) {
			return true;
		}
		if (!reserveSave(player)) {
			return false;
		}

		final Inventory inventory = player.getCarriedItems().getInventory();
		CompletableFuture<Boolean> saveResult = null;
		boolean coinsRemoved = false;
		boolean committed = false;
		try {
			if (isAdmitted(player)) {
				committed = true;
				return true;
			}
			if (inventory.countId(ItemId.COINS.id()) < ENTRY_FEE_COINS) {
				player.message("You need 100,000 coins to enter the Void Dungeon.");
				return false;
			}

			final long removedItemId = player.getCarriedItems().remove(
				new Item(ItemId.COINS.id(), ENTRY_FEE_COINS), false);
			if (removedItemId == Item.ITEM_ID_UNASSIGNED) {
				player.message("The rift cannot take your coins. You remain outside.");
				return false;
			}
			coinsRemoved = true;

			player.getCache().set(ADMISSION_CACHE_KEY, 1);
			discoverDepth(player, INITIAL_DEPTH);

			saveResult = player.saveReservedAsync();
			if (!awaitSave(player, saveResult)) {
				player.message("The rift cannot secure your admission. Your 100,000 coins are returned.");
				return false;
			}

			committed = true;
			ActionSender.sendInventory(player);
			return true;
		} finally {
			try {
				if (!committed) {
					clear(player);
					if (coinsRemoved) {
						inventory.add(new Item(ItemId.COINS.id(), ENTRY_FEE_COINS), false);
					}
					ActionSender.sendInventory(player);
					repairRolledBackSave(player, saveResult);
				}
			} finally {
				player.releaseSaveReservation();
			}
		}
	}

	public static boolean clear(Player player) {
		final boolean changed = player.getCache().hasKey(ADMISSION_CACHE_KEY)
			|| player.getCache().hasKey(DEPTH_CACHE_KEY);
		player.getCache().remove(ADMISSION_CACHE_KEY, DEPTH_CACHE_KEY);
		return changed;
	}

	private static boolean awaitSave(Player player, CompletableFuture<Boolean> saveResult) {
		for (int waited = 0; waited < SAVE_COMPLETION_WAIT_TICKS && !saveResult.isDone(); waited++) {
			if (!canCompleteAdmission(player)) {
				return false;
			}
			delay(1);
		}
		if (!saveResult.isDone() || !canCompleteAdmission(player)) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(saveResult.getNow(false))
				&& canCompleteAdmission(player);
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private static boolean canCompleteAdmission(Player player) {
		return player.loggedIn() && !player.isRemoved() && !player.isLoggingOut()
			&& !player.isUnregistering() && !player.killed;
	}

	private static void repairRolledBackSave(Player player, CompletableFuture<Boolean> saveResult) {
		if (saveResult == null) {
			return;
		}
		saveResult.whenComplete((ignored, error) ->
			player.getWorld().getServer().getGameEventHandler().add(
				new DelayedEvent(player.getWorld(), null, 0, "Void Dungeon admission rollback save") {
					@Override
					public void run() {
						player.requestPersistentSave();
						stop();
					}
				}));
	}

	private static boolean reserveSave(Player player) {
		for (int waited = 0; waited < SAVE_RESERVATION_WAIT_TICKS; waited++) {
			if (!player.loggedIn() || player.isRemoved() || player.isLoggingOut()
				|| player.isUnregistering() || player.killed) {
				return false;
			}
			if (player.tryReserveSave()) {
				return true;
			}
			delay(1);
		}
		player.message("The rift is still securing your account. Please try again.");
		return false;
	}
}
