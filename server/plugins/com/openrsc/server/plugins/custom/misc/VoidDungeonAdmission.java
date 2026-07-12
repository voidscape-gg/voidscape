package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

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
		return purchaseAndPersist(new PlayerAdmissionPort(player));
	}

	static boolean purchaseAndPersist(AdmissionPort admission) {
		if (admission.isAdmitted()) {
			return true;
		}
		if (!admission.reserveSave()) {
			return false;
		}

		CompletableFuture<Boolean> saveResult = null;
		boolean coinsRemoved = false;
		boolean committed = false;
		try {
			if (admission.isAdmitted()) {
				committed = true;
				return true;
			}
			if (!admission.hasEntryFee()) {
				admission.message("You need 100,000 coins to enter the Void Dungeon.");
				return false;
			}

			if (!admission.removeEntryFee()) {
				admission.message("The rift cannot take your coins. You remain outside.");
				return false;
			}
			coinsRemoved = true;

			admission.markAdmitted();

			try {
				saveResult = admission.saveReservedAsync();
			} catch (final RuntimeException ex) {
				return false;
			}
			if (!awaitSave(admission, saveResult)) {
				return false;
			}

			committed = true;
			admission.syncInventory();
			return true;
		} finally {
			try {
				if (!committed) {
					admission.clearAdmission();
					if (coinsRemoved) {
						if (!admission.canRefundEntryFee()) {
							admission.message("The rift could not secure your admission. The interrupted fee is consumed.");
						} else if (admission.returnEntryFee()) {
							admission.message("The rift cannot secure your admission. Your 100,000 coins are returned.");
						} else {
							admission.message("The rift cannot secure your admission. The fee could not be returned and is consumed.");
						}
					}
					admission.syncInventory();
					repairRolledBackSave(admission, saveResult);
				}
			} finally {
				admission.releaseSaveReservation();
			}
		}
	}

	public static boolean clear(Player player) {
		final boolean changed = player.getCache().hasKey(ADMISSION_CACHE_KEY)
			|| player.getCache().hasKey(DEPTH_CACHE_KEY);
		player.getCache().remove(ADMISSION_CACHE_KEY, DEPTH_CACHE_KEY);
		return changed;
	}

	private static boolean awaitSave(AdmissionPort admission, CompletableFuture<Boolean> saveResult) {
		if (saveResult == null) {
			return false;
		}
		for (int waited = 0; waited < SAVE_COMPLETION_WAIT_TICKS && !saveResult.isDone(); waited++) {
			if (!admission.canCompleteAdmission()) {
				return false;
			}
			admission.delayTick();
		}
		if (!saveResult.isDone() || !admission.canCompleteAdmission()) {
			return false;
		}
		try {
			return Boolean.TRUE.equals(saveResult.getNow(false))
				&& admission.canCompleteAdmission();
		} catch (final RuntimeException ex) {
			return false;
		}
	}

	private static void repairRolledBackSave(AdmissionPort admission,
		CompletableFuture<Boolean> saveResult) {
		if (saveResult == null) {
			admission.repairRolledBackSave();
			return;
		}
		saveResult.whenComplete((ignored, error) -> admission.repairRolledBackSave());
	}

	interface AdmissionPort {
		boolean isAdmitted();
		boolean reserveSave();
		boolean hasEntryFee();
		boolean removeEntryFee();
		boolean canRefundEntryFee();
		boolean returnEntryFee();
		void markAdmitted();
		void clearAdmission();
		CompletableFuture<Boolean> saveReservedAsync();
		boolean canCompleteAdmission();
		void delayTick();
		void message(String message);
		void syncInventory();
		void repairRolledBackSave();
		void releaseSaveReservation();
	}

	private static final class PlayerAdmissionPort implements AdmissionPort {
		private final Player player;
		private final UUID lifecycleId;

		private PlayerAdmissionPort(Player player) {
			this.player = player;
			this.lifecycleId = player.getSaveLifecycleId();
		}

		@Override
		public boolean isAdmitted() {
			return VoidDungeonAdmission.isAdmitted(player);
		}

		@Override
		public boolean reserveSave() {
			for (int waited = 0; waited < SAVE_RESERVATION_WAIT_TICKS; waited++) {
				if (!canCompleteAdmission()) {
					return false;
				}
				if (player.tryReserveSave()) {
					return true;
				}
				delayTick();
			}
			message("The rift is still securing your account. Please try again.");
			return false;
		}

		@Override
		public boolean hasEntryFee() {
			return player.getCarriedItems().getInventory().countId(ItemId.COINS.id())
				>= ENTRY_FEE_COINS;
		}

		@Override
		public boolean removeEntryFee() {
			return player.getCarriedItems().remove(
				new Item(ItemId.COINS.id(), ENTRY_FEE_COINS), false) != Item.ITEM_ID_UNASSIGNED;
		}

		@Override
		public boolean canRefundEntryFee() {
			return player.isCurrentSaveLifecycle(lifecycleId) && !player.killed;
		}

		@Override
		public boolean returnEntryFee() {
			final Inventory inventory = player.getCarriedItems().getInventory();
			return inventory.add(new Item(ItemId.COINS.id(), ENTRY_FEE_COINS), false);
		}

		@Override
		public void markAdmitted() {
			player.getCache().set(ADMISSION_CACHE_KEY, 1);
			discoverDepth(player, INITIAL_DEPTH);
		}

		@Override
		public void clearAdmission() {
			clear(player);
		}

		@Override
		public CompletableFuture<Boolean> saveReservedAsync() {
			return player.saveReservedAsync();
		}

		@Override
		public boolean canCompleteAdmission() {
			return player.isCurrentSaveLifecycle(lifecycleId) && !player.isLoggingOut()
				&& !player.isUnregistering() && !player.killed;
		}

		@Override
		public void delayTick() {
			delay(1);
		}

		@Override
		public void message(String message) {
			player.message(message);
		}

		@Override
		public void syncInventory() {
			ActionSender.sendInventory(player);
		}

		@Override
		public void repairRolledBackSave() {
			player.requestPersistentSave(lifecycleId);
		}

		@Override
		public void releaseSaveReservation() {
			player.releaseSaveReservation();
		}
	}
}
