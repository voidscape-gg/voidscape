package com.openrsc.server.content.voidarena;

import java.time.Instant;
import java.util.TimeZone;

public final class VoidArenaRankedPolicyTest {
	public static void main(String[] args) {
		ratingGoldenValuesMatchEloPolicy();
		ratingTransferPreservesThePoolAndFloor();
		resultTypesHaveExactDecisiveSemantics();
		cooldownUsesAnExactExclusiveBoundary();
		dailyCapStartsAtTheConfiguredLimit();
		utcDayAndSeasonBoundariesIgnoreLocalTimezone();
		strictNumericPublicNetworksMatch();
		nonPublicAndMalformedAddressesAreExempt();
		proxyAmbiguousWebSocketsFailClosedUnlessExplicitlyAllowed();
		System.out.println("Void Arena ranked policy tests passed");
	}

	private static void proxyAmbiguousWebSocketsFailClosedUnlessExplicitlyAllowed() {
		assertTrue(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(true, "127.0.0.1", false),
			"loopback WebSocket behind a reverse proxy is ambiguous");
		assertTrue(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(true, "10.0.0.8", false),
			"private WebSocket peer is ambiguous");
		assertTrue(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(true, "not-an-ip", false),
			"malformed WebSocket origin is ambiguous");
		assertFalse(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(true, "8.8.8.8", false),
			"direct public WebSocket origin is usable");
		assertFalse(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(false, "127.0.0.1", false),
			"native loopback keeps the local-development exemption");
		assertFalse(VoidArenaRankedPolicy.hasAmbiguousWebSocketOrigin(true, "127.0.0.1", true),
			"explicit development override permits ambiguous WebSocket origin");
	}

	private static void ratingGoldenValuesMatchEloPolicy() {
		assertEquals(16, VoidArenaRankedPolicy.ratingTransfer(1200, 1200),
			"equal ratings");
		assertEquals(3, VoidArenaRankedPolicy.ratingTransfer(1600, 1200),
			"400-point favourite");
		assertEquals(29, VoidArenaRankedPolicy.ratingTransfer(1200, 1600),
			"400-point upset");
		assertEquals(8, VoidArenaRankedPolicy.ratingTransfer(1400, 1200),
			"200-point favourite");
	}

	private static void ratingTransferPreservesThePoolAndFloor() {
		assertEquals(0, VoidArenaRankedPolicy.ratingTransfer(1200, 1),
			"loser at strict floor");
		assertEquals(1, VoidArenaRankedPolicy.ratingTransfer(1, 2),
			"loser one point above strict floor");
		assertEquals(1, VoidArenaRankedPolicy.ratingTransfer(3000, 2),
			"floor clamp overrides favourite calculation");

		for (int winnerRating = 1; winnerRating <= 3000; winnerRating += 29) {
			for (int loserRating = 1; loserRating <= 3000; loserRating += 31) {
				assertRatingInvariants(winnerRating, loserRating);
			}
		}
		assertRatingInvariants(3000, 3000);
		assertRatingInvariants(1, 3000);
		assertRatingInvariants(3000, 1);
	}

	private static void assertRatingInvariants(int winnerRating, int loserRating) {
		int transfer = VoidArenaRankedPolicy.ratingTransfer(winnerRating, loserRating);
		assertTrue(transfer >= 0,
			"transfer must not be negative for " + winnerRating + "/" + loserRating);
		assertTrue(transfer <= loserRating - 1,
			"transfer crossed loser floor for " + winnerRating + "/" + loserRating);
		assertEquals(transfer, VoidArenaRankedPolicy.ratingTransfer(winnerRating, loserRating),
			"transfer must be deterministic for " + winnerRating + "/" + loserRating);

		int winnerAfter = winnerRating + transfer;
		int loserAfter = loserRating - transfer;
		assertTrue(loserAfter >= 1,
			"loser rating fell below one for " + winnerRating + "/" + loserRating);
		assertEquals((long) winnerRating + loserRating, (long) winnerAfter + loserAfter,
			"rating pool changed for " + winnerRating + "/" + loserRating);
	}

	private static void resultTypesHaveExactDecisiveSemantics() {
		assertTrue(VoidArenaRankedPolicy.isDecisive(VoidArenaRankedPolicy.ResultType.DEATH),
			"death must advance rating, W/L, placement, and pair limits");
		assertTrue(VoidArenaRankedPolicy.isDecisive(VoidArenaRankedPolicy.ResultType.FORFEIT),
			"forfeit must advance rating, W/L, placement, and pair limits");
		assertFalse(VoidArenaRankedPolicy.isDecisive(VoidArenaRankedPolicy.ResultType.TIMEOUT_DRAW),
			"timeout draw must be rating and placement neutral");
		assertFalse(VoidArenaRankedPolicy.isDecisive(
			VoidArenaRankedPolicy.ResultType.SERVER_SHUTDOWN_NO_CONTEST),
			"shutdown no-contest must be rating and placement neutral");
		assertFalse(VoidArenaRankedPolicy.isDecisive(
			VoidArenaRankedPolicy.ResultType.SERVER_RESTART_NO_CONTEST),
			"restart no-contest must be rating and placement neutral");

		assertTrue(VoidArenaRankedPolicy.isDecisive(VoidArenaRankedPolicy.ResultType.DEATH)
			&& VoidArenaRankedPolicy.ratingTransfer(1200, 1) == 0,
			"floor-clamped death remains decisive even when rating transfer is zero");
	}

	private static void cooldownUsesAnExactExclusiveBoundary() {
		long endedAt = Instant.parse("2026-07-15T12:00:00Z").toEpochMilli();
		long cooldown = VoidArenaConfig.RATED_PAIR_COOLDOWN_MS;
		assertFalse(VoidArenaRankedPolicy.cooldownActive(0, endedAt),
			"missing prior result must not start cooldown");
		assertTrue(VoidArenaRankedPolicy.cooldownActive(endedAt, endedAt + cooldown - 1),
			"cooldown must remain active one millisecond before expiry");
		assertFalse(VoidArenaRankedPolicy.cooldownActive(endedAt, endedAt + cooldown),
			"cooldown must expire at the exact boundary");
		assertFalse(VoidArenaRankedPolicy.cooldownActive(endedAt, endedAt + cooldown + 1),
			"cooldown must stay expired after the boundary");
	}

	private static void dailyCapStartsAtTheConfiguredLimit() {
		int cap = VoidArenaConfig.MAX_RATED_PAIR_RESULTS_PER_UTC_DAY;
		assertFalse(VoidArenaRankedPolicy.dailyCapReached(0),
			"empty UTC day must be eligible");
		assertFalse(VoidArenaRankedPolicy.dailyCapReached(cap - 1),
			"last permitted decisive result must remain eligible");
		assertTrue(VoidArenaRankedPolicy.dailyCapReached(cap),
			"configured decisive-result cap must block");
		assertTrue(VoidArenaRankedPolicy.dailyCapReached(cap + 1),
			"values over the cap must remain blocked");
	}

	private static void utcDayAndSeasonBoundariesIgnoreLocalTimezone() {
		long julyStart = Instant.parse("2026-07-31T00:00:00Z").toEpochMilli();
		long julyEnd = Instant.parse("2026-07-31T23:59:59.999Z").toEpochMilli();
		long augustStart = Instant.parse("2026-08-01T00:00:00Z").toEpochMilli();
		assertEquals(julyStart, VoidArenaRankedPolicy.utcDayStart(julyEnd),
			"last millisecond of UTC day");
		assertEquals(augustStart, VoidArenaRankedPolicy.utcDayStart(augustStart),
			"first millisecond of next UTC day");
		assertEquals("2026-07", VoidArenaConfig.seasonIdAt(julyEnd),
			"last millisecond of July season");
		assertEquals("2026-08", VoidArenaConfig.seasonIdAt(augustStart),
			"first millisecond of August season");
		assertEquals("2027-01", VoidArenaConfig.seasonIdAt(
			Instant.parse("2027-01-01T00:00:00Z").toEpochMilli()),
			"UTC year rollover");
		assertEquals("2028-02", VoidArenaConfig.seasonIdAt(
			Instant.parse("2028-02-29T23:59:59.999Z").toEpochMilli()),
			"leap-day season");

		TimeZone original = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
			assertEquals(julyStart, VoidArenaRankedPolicy.utcDayStart(julyEnd),
				"UTC day in UTC+14 default timezone");
			assertEquals("2026-07", VoidArenaConfig.seasonIdAt(julyEnd),
				"season in UTC+14 default timezone");

			TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
			assertEquals(augustStart, VoidArenaRankedPolicy.utcDayStart(augustStart),
				"UTC day in Pacific default timezone");
			assertEquals("2026-08", VoidArenaConfig.seasonIdAt(augustStart),
				"season in Pacific default timezone");
		} finally {
			TimeZone.setDefault(original);
		}
	}

	private static void strictNumericPublicNetworksMatch() {
		assertTrue(VoidArenaRankedPolicy.samePublicNetwork("8.8.8.8", "8.8.8.8"),
			"same public IPv4");
		assertTrue(VoidArenaRankedPolicy.samePublicNetwork(" 8.8.8.8 ", "8.8.8.8"),
			"numeric IPv4 whitespace normalization");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("8.8.8.8", "1.1.1.1"),
			"different public IPv4 addresses");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("8.8.8.8", "8.8.8.9"),
			"IPv4 remains exact-address rather than subnet based");

		assertTrue(VoidArenaRankedPolicy.samePublicNetwork(
			"2606:4700:4700::1111", "2606:4700:4700:0:0:0:0:1111"),
			"equivalent public IPv6 forms");
		assertTrue(VoidArenaRankedPolicy.samePublicNetwork(
			"2606:4700:4700::1111", "2606:4700:4700::1001"),
			"IPv6 privacy addresses in one public /64 share a network");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(
			"2606:4700:4700::1111", "2606:4700:4701::1001"),
			"different public IPv6 /64 networks");
		assertTrue(VoidArenaRankedPolicy.samePublicNetwork(
			"2606:4700:4700:abcd::1", "2606:4700:4700:abcd:ffff::2"),
			"public IPv6 privacy addresses match through the exact /64 boundary");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(
			"2606:4700:4700:abcd::1", "2606:4700:4700:abce::1"),
			"public IPv6 addresses beyond the /64 boundary remain distinct");
		assertFalse(VoidArenaRankedPolicy.sameAddress(
			"2606:4700:4700:abcd::1", "2606:4700:4700:abcd:ffff::2"),
			"exact-address audit must not collapse an IPv6 /64");

		assertTrue(VoidArenaRankedPolicy.samePublicNetwork("::ffff:8.8.8.8", "8.8.8.8"),
			"IPv4-mapped public IPv6 equals public IPv4");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(
			"::ffff:8.8.8.8", "::ffff:8.8.8.9"),
			"IPv4-mapped addresses retain exact IPv4 matching");
		assertTrue(VoidArenaRankedPolicy.samePublicNetwork(
			"0:0:0:0:0:ffff:808:808", "::ffff:8.8.8.8"),
			"equivalent IPv4-mapped IPv6 forms");
	}

	private static void nonPublicAndMalformedAddressesAreExempt() {
		assertAddressExempt("127.0.0.1", "IPv4 loopback");
		assertAddressExempt("::1", "IPv6 loopback");
		assertAddressExempt("10.0.0.1", "RFC1918 10/8");
		assertAddressExempt("172.16.0.1", "RFC1918 172.16/12");
		assertAddressExempt("192.168.1.1", "RFC1918 192.168/16");
		assertAddressExempt("100.64.0.1", "carrier-grade NAT");
		assertAddressExempt("169.254.1.1", "IPv4 link-local");
		assertAddressExempt("0.0.0.0", "unspecified IPv4");
		assertAddressExempt("224.0.0.1", "IPv4 multicast");
		assertAddressExempt("fc00::1", "IPv6 unique-local");
		assertAddressExempt("fe80::1", "IPv6 link-local");
		assertAddressExempt("ff02::1", "IPv6 multicast");
		assertAddressExempt("192.0.2.1", "TEST-NET-1 documentation range");
		assertAddressExempt("198.51.100.1", "TEST-NET-2 documentation range");
		assertAddressExempt("203.0.113.1", "TEST-NET-3 documentation range");
		assertAddressExempt("2001:db8::1", "IPv6 documentation range");
		assertAddressExempt("192.0.0.1", "IETF protocol-assignment range");
		assertAddressExempt("192.88.99.1", "deprecated 6to4 relay range");
		assertAddressExempt("198.18.0.1", "benchmarking range");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(
			"::ffff:192.168.1.1", "192.168.1.1"),
			"mapped private IPv4 must remain exempt");

		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(null, null), "null addresses");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("", ""), "empty addresses");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("example.com", "example.com"),
			"hostnames must not be resolved");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("008.008.008.008", "008.008.008.008"),
			"IPv4 with ambiguous leading zeroes");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("8.8.8.8:53", "8.8.8.8:53"),
			"IPv4 with port");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("[2606:4700:4700::1111]",
			"[2606:4700:4700::1111]"), "bracketed IPv6");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("fe80::1%lo0", "fe80::1%lo0"),
			"zone-scoped IPv6");
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork("not-an-ip", "not-an-ip"),
			"malformed addresses");
	}

	private static void assertAddressExempt(String address, String message) {
		assertFalse(VoidArenaRankedPolicy.samePublicNetwork(address, address), message);
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static void assertEquals(int expected, int actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private static void assertEquals(String expected, String actual, String message) {
		if (!expected.equals(actual)) {
			throw new AssertionError(message + ": expected " + expected + ", got " + actual);
		}
	}

	private VoidArenaRankedPolicyTest() {
	}
}
