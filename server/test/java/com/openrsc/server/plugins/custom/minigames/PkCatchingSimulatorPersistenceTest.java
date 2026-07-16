package com.openrsc.server.plugins.custom.minigames;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Reflection tests for the private simulator persistence and Classic movement
 * policy. The test operates only on a caller-provided temporary directory.
 */
public final class PkCatchingSimulatorPersistenceTest {
	private static final String SIMULATOR_CLASS =
		"com.openrsc.server.plugins.custom.minigames.PkCatchingSimulator";
	private static final String TABLE_CLASS = SIMULATOR_CLASS + "$HighscoreTable";
	private static final String SESSION_CLASS = SIMULATOR_CLASS + "$CatchSession";

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new IllegalArgumentException("Expected one disposable test-directory argument");
		}
		File root = new File(args[0]).getCanonicalFile();
		if (!root.isDirectory() && !root.mkdirs()) {
			throw new IOException("Unable to create test directory " + root);
		}

		legacyRowsMigrateToMediumAndVersionedRowsWin(root);
		malformedOwnedRowsAreDiscardedWhileUnknownPropertiesSurvive(root);
		failedReplacementRestoresOriginalBytes(root);
		classicDifficultyRollStaysExactlyFortyFiveThirtyFiveTwenty();
		System.out.println("PK Catching Simulator persistence and policy tests passed");
	}

	private static void legacyRowsMigrateToMediumAndVersionedRowsWin(File root) throws Exception {
		File file = new File(root, "legacy-migration.properties");
		Properties input = new Properties();
		input.setProperty("schema_version", "1");
		input.setProperty("101", "12|Legacy Alice");
		input.setProperty("606", "6|Legacy Loses");
		input.setProperty("medium.606", "16|Versioned Wins");
		input.setProperty("hard.202", "31|Hard Hero");
		input.setProperty("deployment.note", "preserve this exactly");
		writeProperties(file, input);

		Object data = load(newTable(file));
		Map<?, ?> medium = mapField(data, "medium");
		Map<?, ?> hard = mapField(data, "hard");
		assertEquals(2, medium.size(), "migrated Medium board size");
		assertEquals(1, hard.size(), "existing Hard board size");
		assertEntry(medium, 101L, "Legacy Alice", 12);
		assertEntry(medium, 606L, "Versioned Wins", 16);
		assertEntry(hard, 202L, "Hard Hero", 31);

		Properties migrated = readProperties(file);
		assertEquals("2", migrated.getProperty("schema_version"), "schema version");
		assertEquals("12|Legacy Alice", migrated.getProperty("medium.101"),
			"legacy row migrated into Medium namespace");
		assertEquals("16|Versioned Wins", migrated.getProperty("medium.606"),
			"versioned row wins over duplicate legacy row");
		assertEquals("31|Hard Hero", migrated.getProperty("hard.202"),
			"Hard row remains isolated");
		assertEquals("preserve this exactly", migrated.getProperty("deployment.note"),
			"unowned property survives migration");
		assertFalse(migrated.containsKey("101"), "legacy numeric key removed");
		assertFalse(migrated.containsKey("606"), "duplicate legacy numeric key removed");

		byte[] migratedBytes = Files.readAllBytes(file.toPath());
		Object reloaded = load(newTable(file));
		assertEntry(mapField(reloaded, "medium"), 101L, "Legacy Alice", 12);
		assertEntry(mapField(reloaded, "hard"), 202L, "Hard Hero", 31);
		assertTrue(Arrays.equals(migratedBytes, Files.readAllBytes(file.toPath())),
			"schema-v2 reload must not rewrite a clean file");
	}

	private static void malformedOwnedRowsAreDiscardedWhileUnknownPropertiesSurvive(File root)
			throws Exception {
		File file = new File(root, "malformed-owned.properties");
		Properties input = new Properties();
		input.setProperty("schema_version", "2");
		input.setProperty("medium.11", "20|Medium Runner");
		input.setProperty("hard.22", "30|Hard Runner");
		input.setProperty("medium.33", "not-a-score|Broken");
		input.setProperty("hard.44", "-7|Negative");
		input.setProperty("55", "also-broken");
		input.setProperty("medium.extension", "future-medium-data");
		input.setProperty("hard.extension", "future-hard-data");
		input.setProperty("other.owner", "leave-alone");
		writeProperties(file, input);

		Object data = load(newTable(file));
		Map<?, ?> medium = mapField(data, "medium");
		Map<?, ?> hard = mapField(data, "hard");
		assertEquals(1, medium.size(), "malformed Medium row discarded");
		assertEquals(1, hard.size(), "negative Hard row discarded");
		assertEntry(medium, 11L, "Medium Runner", 20);
		assertEntry(hard, 22L, "Hard Runner", 30);
		assertFalse(medium.containsKey(Long.valueOf(33L)), "bad Medium score is not loaded");
		assertFalse(hard.containsKey(Long.valueOf(44L)), "negative Hard score is not loaded");

		Properties cleaned = readProperties(file);
		assertEquals("2", cleaned.getProperty("schema_version"), "cleaned schema version");
		assertFalse(cleaned.containsKey("medium.33"), "malformed owned Medium row removed");
		assertFalse(cleaned.containsKey("hard.44"), "malformed owned Hard row removed");
		assertFalse(cleaned.containsKey("55"), "malformed legacy owned row removed");
		assertEquals("future-medium-data", cleaned.getProperty("medium.extension"),
			"non-numeric Medium extension property preserved");
		assertEquals("future-hard-data", cleaned.getProperty("hard.extension"),
			"non-numeric Hard extension property preserved");
		assertEquals("leave-alone", cleaned.getProperty("other.owner"),
			"unrelated owner property preserved");
	}

	private static void failedReplacementRestoresOriginalBytes(File root) throws Exception {
		File file = new File(root, "rollback.properties");
		byte[] original = "schema_version=2\nmedium.77=19|Original Runner\n"
			.getBytes(StandardCharsets.ISO_8859_1);
		Files.write(file.toPath(), original);
		File missingReplacement = new File(root, "replacement-does-not-exist.tmp");
		Object table = newTable(file);
		Method replace = table.getClass().getDeclaredMethod(
			"replaceNonAtomicallyPreservingOriginal", File.class, File.class);
		replace.setAccessible(true);
		try {
			replace.invoke(table, missingReplacement, root);
			throw new AssertionError("missing replacement should fail");
		} catch (InvocationTargetException expected) {
			assertTrue(expected.getCause() instanceof IOException,
				"replacement failure must remain an IOException");
		}
		assertTrue(Arrays.equals(original, Files.readAllBytes(file.toPath())),
			"failed replacement restores the original score file byte-for-byte");
		File[] backups = root.listFiles((dir, name) -> name.startsWith(file.getName() + ".backup."));
		assertTrue(backups != null && backups.length == 0,
			"rollback must remove its temporary backup");
	}

	private static void classicDifficultyRollStaysExactlyFortyFiveThirtyFiveTwenty()
			throws Exception {
		Class<?> sessionClass = Class.forName(SESSION_CLASS);
		Object session = allocateWithoutConstructor(sessionClass);
		SequenceRandom random = new SequenceRandom();
		putObjectFieldWithoutConstructor(session, sessionClass.getDeclaredField("random"), random);
		Method roll = sessionClass.getDeclaredMethod("rollDifficulty");
		roll.setAccessible(true);

		int easy = 0;
		int medium = 0;
		int hard = 0;
		for (int i = 0; i < 100; i++) {
			String result = String.valueOf(roll.invoke(session));
			if ("EASY".equals(result)) easy++;
			else if ("MEDIUM".equals(result)) medium++;
			else if ("HARD".equals(result)) hard++;
			else throw new AssertionError("Unexpected difficulty " + result);
		}
		assertEquals(45, easy, "Classic Easy threshold");
		assertEquals(35, medium, "Classic Medium threshold");
		assertEquals(20, hard, "Classic Hard threshold");
	}

	private static Object newTable(File file) throws Exception {
		Class<?> type = Class.forName(TABLE_CLASS);
		Constructor<?> constructor = type.getDeclaredConstructor(File.class);
		constructor.setAccessible(true);
		return constructor.newInstance(file);
	}

	private static Object load(Object table) throws Exception {
		Method load = table.getClass().getDeclaredMethod("load");
		load.setAccessible(true);
		return load.invoke(table);
	}

	@SuppressWarnings("unchecked")
	private static Map<?, ?> mapField(Object owner, String fieldName) throws Exception {
		Field field = owner.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Map<?, ?>)field.get(owner);
	}

	private static void assertEntry(Map<?, ?> board, long hash, String username, int catches)
			throws Exception {
		Object entry = board.get(Long.valueOf(hash));
		assertTrue(entry != null, "missing score entry " + hash);
		assertEquals(hash, longField(entry, "usernameHash"), "username hash " + hash);
		assertEquals(username, stringField(entry, "username"), "username " + hash);
		assertEquals(catches, intField(entry, "catches"), "catch count " + hash);
	}

	private static long longField(Object owner, String fieldName) throws Exception {
		Field field = owner.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getLong(owner);
	}

	private static int intField(Object owner, String fieldName) throws Exception {
		Field field = owner.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.getInt(owner);
	}

	private static String stringField(Object owner, String fieldName) throws Exception {
		Field field = owner.getClass().getDeclaredField(fieldName);
		field.setAccessible(true);
		return (String)field.get(owner);
	}

	private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
		Object unsafe = unsafe();
		return unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type);
	}

	private static void putObjectFieldWithoutConstructor(Object owner, Field field, Object value)
			throws Exception {
		Object unsafe = unsafe();
		long offset = ((Long)unsafe.getClass().getMethod("objectFieldOffset", Field.class)
			.invoke(unsafe, field)).longValue();
		unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class)
			.invoke(unsafe, owner, offset, value);
	}

	private static Object unsafe() throws Exception {
		Class<?> type = Class.forName("sun.misc.Unsafe");
		Field field = type.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		return field.get(null);
	}

	private static void writeProperties(File file, Properties properties) throws IOException {
		try (FileOutputStream output = new FileOutputStream(file)) {
			properties.store(output, "PK simulator test fixture");
		}
	}

	private static Properties readProperties(File file) throws IOException {
		Properties properties = new Properties();
		try (FileInputStream input = new FileInputStream(file)) {
			properties.load(input);
		}
		return properties;
	}

	private static final class SequenceRandom extends Random {
		private int next;

		@Override
		public int nextInt(int bound) {
			if (bound != 100 || next >= 100) {
				throw new AssertionError("rollDifficulty must request exactly one nextInt(100)");
			}
			return next++;
		}
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}
}
