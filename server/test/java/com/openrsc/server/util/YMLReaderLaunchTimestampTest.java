package com.openrsc.server.util;

import com.openrsc.server.ServerConfiguration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class YMLReaderLaunchTimestampTest {
	private static final String CUTOFF = "2026-07-19T18:00:00Z";
	private static final long CUTOFF_EPOCH_MILLIS = 1784484000000L;

	public static void main(String[] args) throws Exception {
		if (args.length != 1) {
			throw new AssertionError("expected the generated launch config path");
		}

		YMLReader reader = new YMLReader();
		reader.loadFromYML(args[0]);
		assertTrue(reader.keyExists("launch_subscription_card_until"),
			"generated launch cutoff must survive runtime config parsing");
		assertEquals(CUTOFF, reader.getAttribute("launch_subscription_card_until"),
			"generated launch cutoff value");
		assertFalse(reader.keyExists("discord"),
			"section headers must not become empty settings");

		ServerConfiguration configuration = new ServerConfiguration();
		configuration.LAUNCH_SUBSCRIPTION_CARD_UNTIL =
			reader.getAttribute("launch_subscription_card_until");
		assertTrue(configuration.isLaunchSubscriptionCardActive(CUTOFF_EPOCH_MILLIS - 1),
			"one millisecond before the cutoff must qualify");
		assertFalse(configuration.isLaunchSubscriptionCardActive(CUTOFF_EPOCH_MILLIS),
			"the exact cutoff must not qualify");
		assertFalse(configuration.isLaunchSubscriptionCardActive(CUTOFF_EPOCH_MILLIS + 1),
			"one millisecond after the cutoff must not qualify");
		assertParserCompatibility();

		System.out.println("Launch subscription config parser tests passed");
	}

	private static void assertParserCompatibility() throws Exception {
		Path fixture = Files.createTempFile("voidscape-yml-reader", ".conf");
		try {
			Files.write(fixture, Arrays.asList(
				"section:",
				"ordinary: first",
				"ordinary: second",
				"colon_duplicate: first",
				"colon_duplicate: host:1234",
				"url: https://example.invalid:8443/path",
				"trailing_colon: scheme:",
				"blank_value:   # intentionally blank",
				"inline_comment: kept # ignored"
			), StandardCharsets.UTF_8);

			YMLReader reader = new YMLReader();
			reader.loadFromYML(fixture.toString());
			assertFalse(reader.keyExists("section"), "section header must stay ignored");
			assertEquals("first", reader.getAttribute("ordinary"),
				"ordinary duplicate must remain first-wins");
			assertEquals("host:1234", reader.getAttribute("colon_duplicate"),
				"later colon-valued duplicate must retain legacy override behavior");
			assertEquals("https://example.invalid:8443/path", reader.getAttribute("url"),
				"URL value must retain every colon");
			assertEquals("scheme:", reader.getAttribute("trailing_colon"),
				"non-empty value ending in a colon must survive");
			assertTrue(reader.keyExists("blank_value"),
				"explicit blank value must retain its prior presence semantics");
			assertEquals("", reader.getAttribute("blank_value"),
				"explicit blank value");
			assertEquals("kept", reader.getAttribute("inline_comment"),
				"inline comment handling");
		} finally {
			Files.deleteIfExists(fixture);
		}
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean condition, String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private YMLReaderLaunchTimestampTest() {
	}
}
