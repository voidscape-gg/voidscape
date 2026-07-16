package com.openrsc.server;

import com.openrsc.server.util.YMLReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public final class ServerConfigurationExplicitConfigTest {
	private static final String EXPLICIT_PROPERTY = "voidscape.config.explicitOnly";
	private static final Path LOCAL_CONFIG = Paths.get("local.conf");
	private static final Path NAMED_CONFIG = Paths.get("headless-demo.conf");
	private static final Path MISSING_CONFIG = Paths.get("missing-demo.conf");

	public static void main(String[] args) throws Exception {
		String originalProperty = System.getProperty(EXPLICIT_PROPERTY);
		try {
			legacyModeStillPrefersLocalConfig();
			legacyModeStillFallsBackToNamedConfigAndDefaults();
			explicitModeIgnoresLocalConfig();
			explicitModeFailsClosedWhenNamedConfigIsMissing();
			explicitModeRejectsAnEmptyConfigName();
			System.out.println("Server explicit-config tests passed");
		} finally {
			restoreProperty(originalProperty);
			Files.deleteIfExists(LOCAL_CONFIG);
			Files.deleteIfExists(NAMED_CONFIG);
			Files.deleteIfExists(MISSING_CONFIG);
		}
	}

	private static void legacyModeStillPrefersLocalConfig() throws Exception {
		System.clearProperty(EXPLICIT_PROPERTY);
		writeConfig(LOCAL_CONFIG, "selection: local");
		writeConfig(NAMED_CONFIG, "selection: named");

		YMLReader reader = new YMLReader();
		String loaded = ServerConfiguration.loadServerProps(reader, NAMED_CONFIG.toString());

		assertEquals("local.conf", loaded, "legacy loaded file");
		assertEquals("local", reader.getAttribute("selection"), "legacy local value");
	}

	private static void legacyModeStillFallsBackToNamedConfigAndDefaults() throws Exception {
		System.clearProperty(EXPLICIT_PROPERTY);
		Files.deleteIfExists(LOCAL_CONFIG);
		writeConfig(NAMED_CONFIG, "selection: named");

		YMLReader namedReader = new YMLReader();
		String namedLoaded = ServerConfiguration.loadServerProps(
			namedReader, NAMED_CONFIG.toString());
		assertEquals(NAMED_CONFIG.toString(), namedLoaded, "legacy named fallback file");
		assertEquals("named", namedReader.getAttribute("selection"),
			"legacy named fallback value");

		Files.deleteIfExists(NAMED_CONFIG);
		YMLReader defaultsReader = new YMLReader();
		String defaultsLoaded = ServerConfiguration.loadServerProps(
			defaultsReader, MISSING_CONFIG.toString());
		assertEquals("Default values", defaultsLoaded, "legacy default-values fallback");
	}

	private static void explicitModeIgnoresLocalConfig() throws Exception {
		System.setProperty(EXPLICIT_PROPERTY, "true");
		writeConfig(LOCAL_CONFIG, "selection: local");
		writeConfig(NAMED_CONFIG, "selection: named");

		YMLReader reader = new YMLReader();
		String loaded = ServerConfiguration.loadServerProps(reader, NAMED_CONFIG.toString());

		assertEquals(NAMED_CONFIG.toString(), loaded, "explicit loaded file");
		assertEquals("named", reader.getAttribute("selection"), "explicit named value");
	}

	private static void explicitModeFailsClosedWhenNamedConfigIsMissing() throws Exception {
		System.setProperty(EXPLICIT_PROPERTY, "true");
		writeConfig(LOCAL_CONFIG, "selection: local");
		Files.deleteIfExists(MISSING_CONFIG);

		YMLReader reader = new YMLReader();
		try {
			ServerConfiguration.loadServerProps(reader, MISSING_CONFIG.toString());
			throw new AssertionError("missing explicit config must throw IOException");
		} catch (IOException expected) {
			assertContains(expected.getMessage(), MISSING_CONFIG.toString(),
				"missing explicit config error");
		}
		assertFalse(reader.keyExists("selection"),
			"explicit failure must not load local.conf");
	}

	private static void explicitModeRejectsAnEmptyConfigName() throws Exception {
		System.setProperty(EXPLICIT_PROPERTY, "true");
		try {
			ServerConfiguration.loadServerProps(new YMLReader(), "");
			throw new AssertionError("empty explicit config must throw IOException");
		} catch (IOException expected) {
			assertContains(expected.getMessage(), "not provided", "empty explicit config error");
		}
	}

	private static void writeConfig(Path path, String line) throws IOException {
		Files.write(path, Collections.singletonList(line), StandardCharsets.UTF_8);
	}

	private static void restoreProperty(String value) {
		if (value == null) {
			System.clearProperty(EXPLICIT_PROPERTY);
		} else {
			System.setProperty(EXPLICIT_PROPERTY, value);
		}
	}

	private static void assertFalse(boolean condition, String message) {
		if (condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertContains(String actual, String expected, String message) {
		if (actual == null || !actual.contains(expected)) {
			throw new AssertionError(message + ": expected to contain " + expected
				+ ", got " + actual);
		}
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private ServerConfigurationExplicitConfigTest() {
	}
}
