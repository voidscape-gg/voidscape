package com.openrsc.server.content.voidarena;

import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemStatus;
import com.openrsc.server.model.entity.player.Player;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class VoidArenaKitSnapshot {
	private static final int LEGACY_VERSION = 1;
	private static final int VERSION = 2;
	private static final int WORN_ITEM_COUNT = 12;
	private static final int MAX_SKILL_COUNT = 64;
	private static final int MAX_PRAYER_COUNT = 64;
	private static final int MAX_CURRENT_LEVEL = 255;
	private static final int MAX_PRAYER_STATE_POINTS = 1_000_000;
	private static final int MAX_CATALOG_ID = 1_000_000;
	private static final int MAX_WORN_SPRITE_ID = Character.MAX_VALUE;
	private static final int MAX_KILL_LOG_CHARS = 8_192;
	private static final int MAX_DECODED_BYTES = 512 * 1_024;
	private static final int MAX_ENCODED_CHARS = 4 * ((MAX_DECODED_BYTES + 2) / 3);

	static String capture(Player player) {
		try {
			int[] worn = player.getWornItems().clone();
			List<SnapshotItem> inventory = new ArrayList<>();
			synchronized (player.getCarriedItems().getInventory().getItems()) {
				for (Item item : player.getCarriedItems().getInventory().getItems()) {
					inventory.add(SnapshotItem.from(item));
				}
			}

			List<SnapshotItem> equipment = new ArrayList<>();
			synchronized (player.getCarriedItems().getEquipment().getList()) {
				for (Item item : player.getCarriedItems().getEquipment().getList()) {
					equipment.add(item == null ? null : SnapshotItem.from(item));
				}
			}
			int skillCount = player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
			int[] currentLevels = new int[skillCount];
			for (int skill = 0; skill < skillCount; skill++) {
				currentLevels[skill] = player.getSkills().getLevel(skill);
			}
			return encode(new DecodedSnapshot(worn, inventory, equipment,
				currentLevels, player.getPrayerStatePoints(),
				player.getPrayers().getActivePrayers().clone()));
		} catch (RuntimeException e) {
			throw new IllegalStateException("Unable to capture Void Arena kit snapshot", e);
		}
	}

	static void restore(Player player, String encoded) {
		try {
			// Decode and validate every byte before touching either live container.
			DecodedSnapshot decoded = decode(encoded);

			List<Item> restoredInventory = new ArrayList<>(decoded.inventoryItems.size());
			for (SnapshotItem item : decoded.inventoryItems) {
				restoredInventory.add(item.toItem());
			}
			Item[] restoredEquipment = new Item[decoded.equipmentItems.size()];
			for (int i = 0; i < restoredEquipment.length; i++) {
				SnapshotItem item = decoded.equipmentItems.get(i);
				restoredEquipment[i] = item == null ? null : item.toItem();
			}
			int[] restoredWorn = decoded.wornItems.clone();
			int[] restoredLevels = decoded.restoresCombatState
				? decoded.currentLevels.clone() : null;
			boolean[] restoredPrayers = decoded.restoresCombatState
				? decoded.activePrayers.clone() : null;

			List<Item> liveInventory = player.getCarriedItems().getInventory().getItems();
			Item[] liveEquipment = player.getCarriedItems().getEquipment().getList();
			if (liveEquipment.length != restoredEquipment.length) {
				throw new IllegalArgumentException("equipment slot count changed");
			}
			if (decoded.restoresCombatState) {
				int liveSkillCount = player.getWorld().getServer().getConstants().getSkills().getSkillsCount();
				if (liveSkillCount != restoredLevels.length) {
					throw new IllegalArgumentException("skill count changed");
				}
				if (player.getPrayers().getActivePrayers().length != restoredPrayers.length) {
					throw new IllegalArgumentException("prayer count changed");
				}
			}

			synchronized (liveInventory) {
				liveInventory.clear();
				liveInventory.addAll(restoredInventory);
			}
			synchronized (liveEquipment) {
				System.arraycopy(restoredEquipment, 0, liveEquipment, 0, liveEquipment.length);
			}
			player.setWornItems(restoredWorn);
			if (decoded.restoresCombatState) {
				for (int skill = 0; skill < restoredLevels.length; skill++) {
					player.getSkills().setLevel(skill, restoredLevels[skill], false);
				}
				player.setPrayerStatePoints(decoded.prayerStatePoints);
				for (int prayer = 0; prayer < restoredPrayers.length; prayer++) {
					player.getPrayers().setPrayer(prayer, restoredPrayers[prayer], false);
				}
			}
		} catch (RuntimeException e) {
			throw new IllegalStateException("Unable to restore Void Arena kit snapshot", e);
		}
	}

	static void clearContainers(Player player) {
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			player.getCarriedItems().getInventory().getItems().clear();
		}
		synchronized (player.getCarriedItems().getEquipment().getList()) {
			Arrays.fill(player.getCarriedItems().getEquipment().getList(), null);
		}
		player.setWornItems(player.getSettings().getAppearance().getSprites());
	}

	static String encode(DecodedSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		if (!snapshot.restoresCombatState) {
			throw new IllegalArgumentException("Legacy Void Arena kit snapshots cannot be re-encoded");
		}
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream out = new DataOutputStream(bytes);
			out.writeInt(VERSION);
			out.writeInt(snapshot.wornItems.length);
			for (int wornItem : snapshot.wornItems) {
				out.writeInt(wornItem);
			}

			out.writeInt(snapshot.inventoryItems.size());
			for (SnapshotItem item : snapshot.inventoryItems) {
				writeItem(out, item);
			}

			out.writeInt(snapshot.equipmentItems.size());
			for (SnapshotItem item : snapshot.equipmentItems) {
				out.writeBoolean(item != null);
				if (item != null) {
					writeItem(out, item);
				}
			}

			out.writeInt(snapshot.currentLevels.length);
			for (int currentLevel : snapshot.currentLevels) {
				out.writeInt(currentLevel);
			}
			out.writeInt(snapshot.prayerStatePoints);
			out.writeInt(snapshot.activePrayers.length);
			for (boolean activePrayer : snapshot.activePrayers) {
				out.writeBoolean(activePrayer);
			}
			out.flush();
			byte[] raw = bytes.toByteArray();
			if (raw.length > MAX_DECODED_BYTES) {
				throw new IllegalArgumentException("Void Arena kit snapshot exceeds the size limit");
			}
			return Base64.getEncoder().encodeToString(raw);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to encode Void Arena kit snapshot", e);
		}
	}

	static DecodedSnapshot decode(String encoded) {
		if (encoded == null) {
			throw new IllegalArgumentException("Void Arena kit snapshot is missing");
		}
		if (encoded.length() > MAX_ENCODED_CHARS) {
			throw new IllegalArgumentException("Void Arena kit snapshot exceeds the size limit");
		}

		final byte[] raw;
		try {
			raw = Base64.getDecoder().decode(encoded);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Void Arena kit snapshot is not valid Base64", e);
		}
		if (raw.length > MAX_DECODED_BYTES) {
			throw new IllegalArgumentException("Void Arena kit snapshot exceeds the size limit");
		}

		try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw))) {
			int version = in.readInt();
			if (version != LEGACY_VERSION && version != VERSION) {
				throw new IllegalArgumentException("Unsupported Void Arena kit snapshot version " + version);
			}

			int wornLength = in.readInt();
			requireExactCount("worn item", wornLength, WORN_ITEM_COUNT);
			int[] worn = new int[wornLength];
			for (int i = 0; i < wornLength; i++) {
				worn[i] = in.readInt();
				validateWornSprite(worn[i]);
			}

			int inventoryCount = in.readInt();
			requireCountInRange("inventory", inventoryCount, 0, Inventory.MAX_SIZE);
			List<SnapshotItem> inventory = new ArrayList<>(inventoryCount);
			for (int i = 0; i < inventoryCount; i++) {
				inventory.add(readItem(in));
			}

			int equipmentCount = in.readInt();
			requireExactCount("equipment", equipmentCount, Equipment.SLOT_COUNT);
			List<SnapshotItem> equipment = new ArrayList<>(equipmentCount);
			for (int i = 0; i < equipmentCount; i++) {
				equipment.add(readStrictBoolean(in, "equipment presence") ? readItem(in) : null);
			}
			if (version == LEGACY_VERSION) {
				if (in.read() != -1) {
					throw new IllegalArgumentException("Void Arena kit snapshot contains trailing data");
				}
				return DecodedSnapshot.legacyItemsOnly(worn, inventory, equipment);
			}

			int skillCount = in.readInt();
			requireCountInRange("skill", skillCount, 1, MAX_SKILL_COUNT);
			int[] currentLevels = new int[skillCount];
			for (int skill = 0; skill < skillCount; skill++) {
				currentLevels[skill] = in.readInt();
				validateCurrentLevel(currentLevels[skill]);
			}
			int prayerStatePoints = in.readInt();
			validatePrayerStatePoints(prayerStatePoints);
			int prayerCount = in.readInt();
			requireCountInRange("prayer", prayerCount, 1, MAX_PRAYER_COUNT);
			boolean[] activePrayers = new boolean[prayerCount];
			for (int prayer = 0; prayer < prayerCount; prayer++) {
				activePrayers[prayer] = readStrictBoolean(in, "active prayer");
			}

			if (in.read() != -1) {
				throw new IllegalArgumentException("Void Arena kit snapshot contains trailing data");
			}
			return new DecodedSnapshot(worn, inventory, equipment, currentLevels,
				prayerStatePoints, activePrayers);
		} catch (EOFException e) {
			throw new IllegalArgumentException("Void Arena kit snapshot is truncated", e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Void Arena kit snapshot is malformed", e);
		}
	}

	private static void writeItem(DataOutputStream out, SnapshotItem item) throws IOException {
		out.writeLong(item.itemId);
		out.writeInt(item.catalogId);
		out.writeInt(item.amount);
		out.writeBoolean(item.noted);
		out.writeBoolean(item.wielded);
		out.writeInt(item.durability);
		out.writeBoolean(item.killLog != null);
		if (item.killLog != null) {
			out.writeUTF(item.killLog);
		}
	}

	private static SnapshotItem readItem(DataInputStream in) throws IOException {
		long itemId = in.readLong();
		int catalogId = in.readInt();
		int amount = in.readInt();
		boolean noted = readStrictBoolean(in, "noted flag");
		boolean wielded = readStrictBoolean(in, "wielded flag");
		int durability = in.readInt();
		String killLog = readStrictBoolean(in, "kill-log presence") ? in.readUTF() : null;
		return new SnapshotItem(itemId, catalogId, amount, noted, wielded, durability, killLog);
	}

	private static boolean readStrictBoolean(DataInputStream in, String field) throws IOException {
		int value = in.readUnsignedByte();
		if (value == 0) {
			return false;
		}
		if (value == 1) {
			return true;
		}
		throw new IllegalArgumentException("Invalid Void Arena kit snapshot " + field);
	}

	private static void requireExactCount(String field, int actual, int expected) {
		if (actual != expected) {
			throw new IllegalArgumentException("Invalid Void Arena kit snapshot " + field
				+ " count " + actual + " (expected " + expected + ")");
		}
	}

	private static void requireCountInRange(String field, int actual, int minimum, int maximum) {
		if (actual < minimum || actual > maximum) {
			throw new IllegalArgumentException("Invalid Void Arena kit snapshot " + field
				+ " count " + actual);
		}
	}

	private static void validateWornSprite(int spriteId) {
		if (spriteId < 0 || spriteId > MAX_WORN_SPRITE_ID) {
			throw new IllegalArgumentException("Invalid Void Arena kit snapshot worn sprite " + spriteId);
		}
	}

	private static void validateCurrentLevel(int level) {
		if (level < 0 || level > MAX_CURRENT_LEVEL) {
			throw new IllegalArgumentException("Invalid Void Arena kit snapshot current level " + level);
		}
	}

	private static void validatePrayerStatePoints(int points) {
		if (points < 0 || points > MAX_PRAYER_STATE_POINTS) {
			throw new IllegalArgumentException("Invalid Void Arena kit snapshot prayer state " + points);
		}
	}

	static final class DecodedSnapshot {
		private final int[] wornItems;
		private final List<SnapshotItem> inventoryItems;
		private final List<SnapshotItem> equipmentItems;
		private final int[] currentLevels;
		private final int prayerStatePoints;
		private final boolean[] activePrayers;
		private final boolean restoresCombatState;

		DecodedSnapshot(int[] wornItems, List<SnapshotItem> inventoryItems,
			List<SnapshotItem> equipmentItems, int[] currentLevels, int prayerStatePoints,
			boolean[] activePrayers) {
			this(wornItems, inventoryItems, equipmentItems, currentLevels, prayerStatePoints,
				activePrayers, true);
		}

		private DecodedSnapshot(int[] wornItems, List<SnapshotItem> inventoryItems,
			List<SnapshotItem> equipmentItems, int[] currentLevels, int prayerStatePoints,
			boolean[] activePrayers, boolean restoresCombatState) {
			Objects.requireNonNull(wornItems, "wornItems");
			Objects.requireNonNull(inventoryItems, "inventoryItems");
			Objects.requireNonNull(equipmentItems, "equipmentItems");
			Objects.requireNonNull(currentLevels, "currentLevels");
			Objects.requireNonNull(activePrayers, "activePrayers");
			requireExactCount("worn item", wornItems.length, WORN_ITEM_COUNT);
			requireCountInRange("inventory", inventoryItems.size(), 0, Inventory.MAX_SIZE);
			requireExactCount("equipment", equipmentItems.size(), Equipment.SLOT_COUNT);
			if (restoresCombatState) {
				requireCountInRange("skill", currentLevels.length, 1, MAX_SKILL_COUNT);
				requireCountInRange("prayer", activePrayers.length, 1, MAX_PRAYER_COUNT);
				validatePrayerStatePoints(prayerStatePoints);
			} else if (currentLevels.length != 0 || prayerStatePoints != 0
				|| activePrayers.length != 0) {
				throw new IllegalArgumentException("Legacy Void Arena kit snapshots are item-only");
			}

			this.wornItems = wornItems.clone();
			for (int wornItem : this.wornItems) {
				validateWornSprite(wornItem);
			}

			List<SnapshotItem> inventoryCopy = new ArrayList<>(inventoryItems.size());
			for (SnapshotItem item : inventoryItems) {
				inventoryCopy.add(Objects.requireNonNull(item, "inventory item"));
			}
			this.inventoryItems = Collections.unmodifiableList(inventoryCopy);
			this.equipmentItems = Collections.unmodifiableList(new ArrayList<>(equipmentItems));
			this.currentLevels = currentLevels.clone();
			for (int currentLevel : this.currentLevels) {
				validateCurrentLevel(currentLevel);
			}
			this.prayerStatePoints = prayerStatePoints;
			this.activePrayers = activePrayers.clone();
			this.restoresCombatState = restoresCombatState;
		}

		private static DecodedSnapshot legacyItemsOnly(int[] wornItems,
			List<SnapshotItem> inventoryItems, List<SnapshotItem> equipmentItems) {
			return new DecodedSnapshot(wornItems, inventoryItems, equipmentItems,
				new int[0], 0, new boolean[0], false);
		}

		int[] getWornItems() {
			return wornItems.clone();
		}

		List<SnapshotItem> getInventoryItems() {
			return inventoryItems;
		}

		List<SnapshotItem> getEquipmentItems() {
			return equipmentItems;
		}

		int[] getCurrentLevels() {
			return currentLevels.clone();
		}

		int getPrayerStatePoints() {
			return prayerStatePoints;
		}

		boolean[] getActivePrayers() {
			return activePrayers.clone();
		}

		boolean isLegacyItemOnly() {
			return !restoresCombatState;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof DecodedSnapshot)) {
				return false;
			}
			DecodedSnapshot that = (DecodedSnapshot) other;
			return Arrays.equals(wornItems, that.wornItems)
				&& inventoryItems.equals(that.inventoryItems)
				&& equipmentItems.equals(that.equipmentItems)
				&& Arrays.equals(currentLevels, that.currentLevels)
				&& prayerStatePoints == that.prayerStatePoints
				&& Arrays.equals(activePrayers, that.activePrayers)
				&& restoresCombatState == that.restoresCombatState;
		}

		@Override
		public int hashCode() {
			int result = Arrays.hashCode(wornItems);
			result = 31 * result + inventoryItems.hashCode();
			result = 31 * result + equipmentItems.hashCode();
			result = 31 * result + Arrays.hashCode(currentLevels);
			result = 31 * result + prayerStatePoints;
			result = 31 * result + Arrays.hashCode(activePrayers);
			return 31 * result + Boolean.hashCode(restoresCombatState);
		}
	}

	static final class SnapshotItem {
		private final long itemId;
		private final int catalogId;
		private final int amount;
		private final boolean noted;
		private final boolean wielded;
		private final int durability;
		private final String killLog;

		SnapshotItem(long itemId, int catalogId, int amount, boolean noted, boolean wielded,
			int durability, String killLog) {
			if (itemId < Item.ITEM_ID_UNASSIGNED) {
				throw new IllegalArgumentException("Invalid Void Arena kit snapshot item id " + itemId);
			}
			if (catalogId < 0 || catalogId > MAX_CATALOG_ID) {
				throw new IllegalArgumentException("Invalid Void Arena kit snapshot catalog id " + catalogId);
			}
			if (amount <= 0) {
				throw new IllegalArgumentException("Invalid Void Arena kit snapshot item amount " + amount);
			}
			if (durability < 0 || durability > 100) {
				throw new IllegalArgumentException("Invalid Void Arena kit snapshot durability " + durability);
			}
			if (killLog != null && killLog.length() > MAX_KILL_LOG_CHARS) {
				throw new IllegalArgumentException("Void Arena kit snapshot kill log exceeds the size limit");
			}
			this.itemId = itemId;
			this.catalogId = catalogId;
			this.amount = amount;
			this.noted = noted;
			this.wielded = wielded;
			this.durability = durability;
			this.killLog = killLog;
		}

		static SnapshotItem from(Item item) {
			Objects.requireNonNull(item, "item");
			ItemStatus status = Objects.requireNonNull(item.getItemStatus(), "item status");
			return new SnapshotItem(item.getItemId(), status.getCatalogId(), status.getAmount(),
				status.getNoted(), status.isWielded(), status.getDurability(), status.getKillLog());
		}

		Item toItem() {
			ItemStatus status = new ItemStatus();
			status.setCatalogId(catalogId);
			status.setAmount(amount);
			status.setNoted(noted);
			status.setWielded(wielded);
			status.setDurability(durability);
			status.setKillLog(killLog);
			return new Item(itemId, status);
		}

		long getItemId() {
			return itemId;
		}

		int getCatalogId() {
			return catalogId;
		}

		int getAmount() {
			return amount;
		}

		boolean isNoted() {
			return noted;
		}

		boolean isWielded() {
			return wielded;
		}

		int getDurability() {
			return durability;
		}

		String getKillLog() {
			return killLog;
		}

		@Override
		public boolean equals(Object other) {
			if (!(other instanceof SnapshotItem)) {
				return false;
			}
			SnapshotItem that = (SnapshotItem) other;
			return itemId == that.itemId
				&& catalogId == that.catalogId
				&& amount == that.amount
				&& noted == that.noted
				&& wielded == that.wielded
				&& durability == that.durability
				&& Objects.equals(killLog, that.killLog);
		}

		@Override
		public int hashCode() {
			return Objects.hash(itemId, catalogId, amount, noted, wielded, durability, killLog);
		}
	}

	private VoidArenaKitSnapshot() {
	}
}
