package com.openrsc.server.content.voidarena;

import com.openrsc.server.content.voidarena.VoidArenaKitSnapshot.DecodedSnapshot;
import com.openrsc.server.content.voidarena.VoidArenaKitSnapshot.SnapshotItem;
import com.openrsc.server.model.container.Item;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public final class VoidArenaKitSnapshotTest {
	private static final int VERSION_OFFSET = 0;
	private static final int WORN_COUNT_OFFSET = 4;
	private static final int INVENTORY_COUNT_OFFSET = 56;
	private static final int EQUIPMENT_COUNT_OFFSET = 60;
	private static final int FIRST_EQUIPMENT_PRESENCE_OFFSET = 64;

	public static void main(String[] args) throws Exception {
		roundTripPreservesEverySerializedItemField();
		legacyV1SnapshotsRemainRecoverableAsItemsOnly();
		decodedSnapshotIsDeeplyImmutable();
		allTruncatedPayloadsAreRejected();
		versionCountsTrailingDataAndBooleansAreStrict();
		itemFieldBoundsAreStrict();
		base64AndTotalSizeAreBounded();
		System.out.println("Void Arena kit snapshot tests passed");
	}

	private static void roundTripPreservesEverySerializedItemField() {
		DecodedSnapshot expected = sampleSnapshot();
		String encoded = VoidArenaKitSnapshot.encode(expected);
		DecodedSnapshot actual = VoidArenaKitSnapshot.decode(encoded);

		assertEquals(expected, actual, "snapshot round trip");
		assertTrue(encoded.length() > 150, "normal snapshots exceed the legacy cache column limit");
		SnapshotItem first = actual.getInventoryItems().get(0);
		assertEquals(Long.MAX_VALUE, first.getItemId(), "item instance id");
		assertEquals(999_999, first.getCatalogId(), "catalog id");
		assertEquals(Integer.MAX_VALUE, first.getAmount(), "amount");
		assertTrue(first.isNoted(), "noted flag");
		assertFalse(first.isWielded(), "wielded flag");
		assertEquals(0, first.getDurability(), "durability");
		assertEquals("npc:12;3\u0000\u03a9", first.getKillLog(), "kill-log metadata");
		Item materialized = first.toItem();
		assertEquals(first.getItemId(), materialized.getItemId(), "materialized item instance id");
		assertEquals(first.getCatalogId(), materialized.getItemStatus().getCatalogId(),
			"materialized catalog id");
		assertEquals(first.getAmount(), materialized.getItemStatus().getAmount(),
			"materialized amount");
		assertEquals(first.isNoted(), materialized.getItemStatus().getNoted(),
			"materialized noted flag");
		assertEquals(first.isWielded(), materialized.getItemStatus().isWielded(),
			"materialized wielded flag");
		assertEquals(first.getDurability(), materialized.getItemStatus().getDurability(),
			"materialized durability");
		assertEquals(first.getKillLog(), materialized.getItemStatus().getKillLog(),
			"materialized kill-log metadata");

		SnapshotItem equipped = actual.getEquipmentItems().get(7);
		assertEquals(-1L, equipped.getItemId(), "unassigned item instance id");
		assertFalse(equipped.isNoted(), "equipment noted flag");
		assertTrue(equipped.isWielded(), "equipment wielded flag");
		assertEquals(100, equipped.getDurability(), "equipment durability");
		assertEquals(null, equipped.getKillLog(), "null kill log");
	}

	private static void legacyV1SnapshotsRemainRecoverableAsItemsOnly() throws IOException {
		DecodedSnapshot legacy = VoidArenaKitSnapshot.decode(legacySnapshotBytes());
		assertTrue(legacy.isLegacyItemOnly(), "v1 snapshot is marked item-only");
		assertEquals(12, legacy.getWornItems().length, "legacy worn items");
		assertEquals(1, legacy.getInventoryItems().size(), "legacy inventory items");
		assertEquals(14, legacy.getEquipmentItems().size(), "legacy equipment slots");
		assertEquals(0, legacy.getCurrentLevels().length, "legacy snapshot has no skill state");
		assertEquals(0, legacy.getActivePrayers().length, "legacy snapshot has no prayer toggles");
		assertEquals(42, legacy.getInventoryItems().get(0).getCatalogId(), "legacy item catalog id");
		assertRejected(() -> VoidArenaKitSnapshot.encode(legacy),
			"legacy snapshots cannot silently become v2 snapshots");
	}

	private static void decodedSnapshotIsDeeplyImmutable() {
		int[] worn = validWorn();
		List<SnapshotItem> inventory = new ArrayList<>();
		inventory.add(new SnapshotItem(1, 2, 3, false, false, 50, "1:2;3"));
		List<SnapshotItem> equipment = emptyEquipment();
		int[] levels = validLevels();
		boolean[] prayers = validPrayers();
		DecodedSnapshot snapshot = new DecodedSnapshot(worn, inventory, equipment, levels, 7_200,
			prayers);

		worn[0] = 777;
		inventory.clear();
		equipment.set(0, new SnapshotItem(4, 5, 6, false, true, 100, null));
		levels[0] = 200;
		prayers[0] = !prayers[0];
		assertEquals(0, snapshot.getWornItems()[0], "constructor copies worn array");
		assertEquals(1, snapshot.getInventoryItems().size(), "constructor copies inventory list");
		assertEquals(null, snapshot.getEquipmentItems().get(0), "constructor copies equipment list");
		assertEquals(1, snapshot.getCurrentLevels()[0], "constructor copies current levels");
		assertEquals(7_200, snapshot.getPrayerStatePoints(), "prayer state round trip");
		assertTrue(snapshot.getActivePrayers()[0], "constructor copies active prayers");

		int[] returnedWorn = snapshot.getWornItems();
		returnedWorn[1] = 888;
		assertEquals(1, snapshot.getWornItems()[1], "worn getter returns a copy");
		assertUnsupported(() -> snapshot.getInventoryItems().clear(), "inventory list is immutable");
		assertUnsupported(() -> snapshot.getEquipmentItems().set(0, null),
			"equipment list is immutable");
	}

	private static void allTruncatedPayloadsAreRejected() {
		byte[] complete = decodeBase64(VoidArenaKitSnapshot.encode(sampleSnapshot()));
		for (int length = 0; length < complete.length; length++) {
			final byte[] truncated = Arrays.copyOf(complete, length);
			assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(truncated)),
				"truncated payload length " + length);
		}
	}

	private static void versionCountsTrailingDataAndBooleansAreStrict() {
		byte[] minimal = minimalSnapshotBytes();

		assertMutatedIntRejected(minimal, VERSION_OFFSET, 3, "unsupported version");
		assertMutatedIntRejected(minimal, WORN_COUNT_OFFSET, -1, "negative worn count");
		assertMutatedIntRejected(minimal, WORN_COUNT_OFFSET, 11, "short worn count");
		assertMutatedIntRejected(minimal, WORN_COUNT_OFFSET, 13, "long worn count");
		assertMutatedIntRejected(minimal, 8, -1, "negative worn sprite");
		assertMutatedIntRejected(minimal, 8, 65_536, "oversized worn sprite");
		assertMutatedIntRejected(minimal, INVENTORY_COUNT_OFFSET, -1,
			"negative inventory count");
		assertMutatedIntRejected(minimal, INVENTORY_COUNT_OFFSET, 31,
			"oversized inventory count");
		assertMutatedIntRejected(minimal, EQUIPMENT_COUNT_OFFSET, -1,
			"negative equipment count");
		assertMutatedIntRejected(minimal, EQUIPMENT_COUNT_OFFSET, 13,
			"short equipment count");
		assertMutatedIntRejected(minimal, EQUIPMENT_COUNT_OFFSET, 15,
			"long equipment count");

		byte[] invalidBoolean = minimal.clone();
		invalidBoolean[FIRST_EQUIPMENT_PRESENCE_OFFSET] = 2;
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(invalidBoolean)),
			"non-canonical boolean");

		byte[] trailing = Arrays.copyOf(minimal, minimal.length + 1);
		trailing[trailing.length - 1] = 42;
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(trailing)),
			"trailing byte");
	}

	private static void itemFieldBoundsAreStrict() throws IOException {
		assertItemRejected(-2L, 1, 1, 0, 0, 100, null, "item id below unassigned sentinel");
		assertItemRejected(1L, -1, 1, 0, 0, 100, null, "negative catalog id");
		assertItemRejected(1L, 1_000_001, 1, 0, 0, 100, null, "oversized catalog id");
		assertItemRejected(1L, 1, 0, 0, 0, 100, null, "zero amount");
		assertItemRejected(1L, 1, -1, 0, 0, 100, null, "negative amount");
		assertItemRejected(1L, 1, 1, 2, 0, 100, null, "non-canonical noted flag");
		assertItemRejected(1L, 1, 1, 0, 2, 100, null, "non-canonical wielded flag");
		assertRawItemRejected(1L, 1, 1, 0, 0, 100, 2, null,
			"non-canonical kill-log presence flag");
		assertItemRejected(1L, 1, 1, 0, 0, -1, null, "negative durability");
		assertItemRejected(1L, 1, 1, 0, 0, 101, null, "durability above 100");

		char[] oversizedKillLog = new char[8_193];
		Arrays.fill(oversizedKillLog, 'k');
		assertItemRejected(1L, 1, 1, 0, 0, 100, new String(oversizedKillLog),
			"oversized kill log");

		assertRejected(() -> new SnapshotItem(1L, 1, 0, false, false, 100, null),
			"pure item form validates amount");
		assertRejected(() -> new DecodedSnapshot(new int[11], new ArrayList<>(), emptyEquipment(),
			validLevels(), 0, validPrayers()),
			"pure snapshot form validates worn count");
	}

	private static void base64AndTotalSizeAreBounded() {
		assertRejected(() -> VoidArenaKitSnapshot.decode(null), "null snapshot");
		assertRejected(() -> VoidArenaKitSnapshot.decode("%%%"), "invalid Base64");

		byte[] oversized = new byte[512 * 1_024 + 1];
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(oversized)),
			"oversized decoded payload");
	}

	private static DecodedSnapshot sampleSnapshot() {
		List<SnapshotItem> inventory = new ArrayList<>();
		inventory.add(new SnapshotItem(Long.MAX_VALUE, 999_999, Integer.MAX_VALUE,
			true, false, 0, "npc:12;3\u0000\u03a9"));
		inventory.add(new SnapshotItem(22L, 7, 1, false, true, 73, ""));
		List<SnapshotItem> equipment = emptyEquipment();
		equipment.set(7, new SnapshotItem(-1L, 42, 250, false, true, 100, null));
		return new DecodedSnapshot(validWorn(), inventory, equipment, validLevels(), 11_880,
			validPrayers());
	}

	private static int[] validWorn() {
		int[] worn = new int[12];
		for (int i = 0; i < worn.length; i++) {
			worn[i] = i;
		}
		return worn;
	}

	private static int[] validLevels() {
		int[] levels = new int[18];
		for (int i = 0; i < levels.length; i++) {
			levels[i] = i + 1;
		}
		return levels;
	}

	private static boolean[] validPrayers() {
		boolean[] prayers = new boolean[15];
		prayers[0] = true;
		prayers[14] = true;
		return prayers;
	}

	private static List<SnapshotItem> emptyEquipment() {
		return new ArrayList<>(Arrays.asList(new SnapshotItem[14]));
	}

	private static byte[] minimalSnapshotBytes() {
		DecodedSnapshot minimal = new DecodedSnapshot(validWorn(), new ArrayList<>(), emptyEquipment(),
			validLevels(), 0, validPrayers());
		return decodeBase64(VoidArenaKitSnapshot.encode(minimal));
	}

	private static String legacySnapshotBytes() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(1);
		out.writeInt(12);
		for (int wornItem : validWorn()) {
			out.writeInt(wornItem);
		}
		out.writeInt(1);
		writeRawItem(out, 123L, 42, 7, false, false, 88, "legacy");
		out.writeInt(14);
		for (int slot = 0; slot < 14; slot++) {
			out.writeBoolean(slot == 3);
			if (slot == 3) {
				writeRawItem(out, 456L, 99, 1, false, true, 100, null);
			}
		}
		out.flush();
		return encodeBase64(bytes.toByteArray());
	}

	private static void writeRawItem(DataOutputStream out, long itemId, int catalogId,
		int amount, boolean noted, boolean wielded, int durability, String killLog)
		throws IOException {
		out.writeLong(itemId);
		out.writeInt(catalogId);
		out.writeInt(amount);
		out.writeBoolean(noted);
		out.writeBoolean(wielded);
		out.writeInt(durability);
		out.writeBoolean(killLog != null);
		if (killLog != null) {
			out.writeUTF(killLog);
		}
	}

	private static void assertMutatedIntRejected(byte[] source, int offset, int value, String message) {
		byte[] mutated = source.clone();
		ByteBuffer.wrap(mutated).putInt(offset, value);
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(mutated)), message);
	}

	private static void assertItemRejected(long itemId, int catalogId, int amount,
		int notedByte, int wieldedByte, int durability, String killLog, String message)
		throws IOException {
		byte[] raw = snapshotWithOneRawItem(itemId, catalogId, amount, notedByte,
			wieldedByte, durability, killLog == null ? 0 : 1, killLog);
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(raw)), message);
	}

	private static void assertRawItemRejected(long itemId, int catalogId, int amount,
		int notedByte, int wieldedByte, int durability, int killLogPresenceByte,
		String killLog, String message) throws IOException {
		byte[] raw = snapshotWithOneRawItem(itemId, catalogId, amount, notedByte,
			wieldedByte, durability, killLogPresenceByte, killLog);
		assertRejected(() -> VoidArenaKitSnapshot.decode(encodeBase64(raw)), message);
	}

	private static byte[] snapshotWithOneRawItem(long itemId, int catalogId, int amount,
		int notedByte, int wieldedByte, int durability, int killLogPresenceByte,
		String killLog) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(bytes);
		out.writeInt(2);
		out.writeInt(12);
		for (int wornItem : validWorn()) {
			out.writeInt(wornItem);
		}
		out.writeInt(1);
		out.writeLong(itemId);
		out.writeInt(catalogId);
		out.writeInt(amount);
		out.writeByte(notedByte);
		out.writeByte(wieldedByte);
		out.writeInt(durability);
		out.writeByte(killLogPresenceByte);
		if (killLog != null) {
			out.writeUTF(killLog);
		}
		out.writeInt(14);
		for (int i = 0; i < 14; i++) {
			out.writeBoolean(false);
		}
		out.writeInt(validLevels().length);
		for (int level : validLevels()) {
			out.writeInt(level);
		}
		out.writeInt(0);
		out.writeInt(validPrayers().length);
		for (boolean prayer : validPrayers()) {
			out.writeBoolean(prayer);
		}
		out.flush();
		return bytes.toByteArray();
	}

	private static String encodeBase64(byte[] raw) {
		return Base64.getEncoder().encodeToString(raw);
	}

	private static byte[] decodeBase64(String encoded) {
		return Base64.getDecoder().decode(encoded);
	}

	private static void assertRejected(ThrowingRunnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected rejection");
		} catch (IllegalArgumentException expected) {
			// Expected.
		} catch (Exception other) {
			throw new AssertionError(message + ": wrong exception " + other, other);
		}
	}

	private static void assertUnsupported(ThrowingRunnable action, String message) {
		try {
			action.run();
			throw new AssertionError(message + ": expected UnsupportedOperationException");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		} catch (Exception other) {
			throw new AssertionError(message + ": wrong exception " + other, other);
		}
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(boolean expected, boolean actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private VoidArenaKitSnapshotTest() {
	}
}
