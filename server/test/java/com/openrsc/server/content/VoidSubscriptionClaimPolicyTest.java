package com.openrsc.server.content;

import com.openrsc.server.ServerConfiguration;

public final class VoidSubscriptionClaimPolicyTest {
	private VoidSubscriptionClaimPolicyTest() {
	}

	public static void main(String[] args) {
		testCacheKeysFitSchema();
		testBaseCodeBinding();
		testLinkedBaseCodeRoute();
		testReservedCardOverlapPolicy();
		testFounderCodeOverlapPolicy();
		testLaunchCardCutoffBoundary();
		System.out.println("VoidSubscription claim policy tests passed");
	}

	private static void testCacheKeysFitSchema() {
		assertEquals("starter:17:23", VoidSubscription.starterCardCacheKey(17, 23),
			"starter key must include account and character IDs");
		assertFalse(VoidSubscription.starterCardCacheKey(17, 23)
			.equals(VoidSubscription.starterCardCacheKey(17, 24)),
			"siblings under one account must have independent founder markers");
		assertEquals("starter_card:17", VoidSubscription.legacyStarterCardCacheKey(17),
			"legacy key must remain explicit migration input");
		assertEquals("", VoidSubscription.starterCardCacheKey(17, 0),
			"starter key must reject a missing character ID");

		String maximumStarterKey = VoidSubscription.starterCardCacheKey(Integer.MAX_VALUE, Integer.MAX_VALUE);
		assertEquals(29, maximumStarterKey.length(), "maximum starter key length");
		assertTrue(maximumStarterKey.length() <= 32, "starter key must fit player_cache.key");

		String maximumCode = "ABCDEFGHIJKLMNOPQRST";
		String maximumTagKey = VoidSubscription.baseCodeTagCacheKey(maximumCode);
		assertEquals("base_tag:" + maximumCode, maximumTagKey, "base-code classifier key");
		assertEquals(29, maximumTagKey.length(), "maximum base tag key length");
		assertTrue(maximumTagKey.length() <= 32, "base tag key must fit player_cache.key");
		assertEquals("", VoidSubscription.baseCodeTagCacheKey(maximumCode + "U"),
			"base tag key must reject an oversized code");
		String maximumAccountKey = VoidSubscription.baseCodeAccountCacheKey(maximumCode);
		assertEquals("base_acct:" + maximumCode, maximumAccountKey,
			"base-code account classifier key");
		assertTrue(maximumAccountKey.length() <= 32,
			"base account key must fit player_cache.key");
	}

	private static void testBaseCodeBinding() {
		assertFalse(VoidSubscription.baseCodeMayBeRedeemedBy(null, 41),
			"a missing tag classifies a referral, not a base code");
		assertTrue(VoidSubscription.baseCodeMayBeRedeemedBy(0, 41),
			"an unassigned base code may bind to its first claimant");
		assertTrue(VoidSubscription.baseCodeMayBeRedeemedBy(41, 41),
			"a bound base code may be reconciled by its exact character");
		assertFalse(VoidSubscription.baseCodeMayBeRedeemedBy(42, 41),
			"a bound base code must reject a sibling or other character");
		assertFalse(VoidSubscription.baseCodeMayBeRedeemedBy(-1, 41),
			"a negative tag is invalid");
		assertFalse(VoidSubscription.baseCodeMayBeRedeemedBy(0, 0),
			"a base code cannot bind to a non-persistent character ID");
	}

	private static void testLinkedBaseCodeRoute() {
		assertTrue(VoidSubscription.linkedBaseCodeRouteMatches(17, 41, 17, 41, true),
			"linked founder route requires its exact account, player, and composite");
		assertFalse(VoidSubscription.linkedBaseCodeRouteMatches(17, 41, 18, 41, true),
			"relinking the bound player must not authorize another founder card");
		assertFalse(VoidSubscription.linkedBaseCodeRouteMatches(17, 41, 17, 41, false),
			"a missing reset-created composite must fail closed");
		assertFalse(VoidSubscription.linkedBaseCodeRouteMatches(17, 42, 17, 41, true),
			"a sibling cannot consume a bound founder route");
	}

	private static void testReservedCardOverlapPolicy() {
		assertPlan(VoidSubscription.planReservedCardClaim(1, null), true, true, false,
			"founder-only claim");
		assertPlan(VoidSubscription.planReservedCardClaim(1, 1), true, true, true,
			"founder and native availability must mint one and close both routes");
		assertPlan(VoidSubscription.planReservedCardClaim(1, 2), false, true, false,
			"a claimed native card must retire the founder route without another item");
		assertPlan(VoidSubscription.planReservedCardClaim(2, 1), false, false, true,
			"a claimed founder card must retire the native route without another item");
		assertPlan(VoidSubscription.planReservedCardClaim(null, 1), true, false, true,
			"native-only claim");
		assertPlan(VoidSubscription.planReservedCardClaim(null, null), false, false, false,
			"no entitlement");
		assertPlan(VoidSubscription.planReservedCardClaim(7, 1), false, false, false,
			"invalid state must fail closed");
	}

	private static void testFounderCodeOverlapPolicy() {
		assertPlan(VoidSubscription.planFounderCodeClaim(false, null, null), true, false, false,
			"an unlinked code-only founder receives one card");
		assertPlan(VoidSubscription.planFounderCodeClaim(false, null, 1), true, false, true,
			"an unlinked founder code suppresses an available native route");
		assertPlan(VoidSubscription.planFounderCodeClaim(false, null, 2), false, false, false,
			"an already-claimed native route suppresses an unlinked founder code item");
		assertPlan(VoidSubscription.planFounderCodeClaim(true, null, null), false, false, false,
			"a linked founder code must not create a missing composite marker");
		assertPlan(VoidSubscription.planFounderCodeClaim(true, 1, 1), true, true, true,
			"a linked founder code closes both available routes with one item");
		assertPlan(VoidSubscription.planFounderCodeClaim(true, 2, 1), false, false, true,
			"an issued founder card retires native during code reconciliation");
		assertPlan(VoidSubscription.planFounderCodeClaim(true, 1, 2), false, true, false,
			"an issued native card consumes the matching founder composite without another item");
		assertPlan(VoidSubscription.planFounderCodeClaim(true, 9, null), false, false, false,
			"an invalid linked composite must fail closed");
	}

	private static void testLaunchCardCutoffBoundary() {
		ServerConfiguration configuration = new ServerConfiguration();
		configuration.LAUNCH_SUBSCRIPTION_CARD_UNTIL = "2026-07-19T18:00:00Z";
		long cutoff = 1784484000000L;
		assertTrue(configuration.isLaunchSubscriptionCardActive(cutoff - 1),
			"a character created one millisecond before the cutoff qualifies");
		assertFalse(configuration.isLaunchSubscriptionCardActive(cutoff),
			"the launch-card window is half-open at the exact cutoff");
		assertFalse(configuration.isLaunchSubscriptionCardActive(cutoff + 1),
			"a character created after the cutoff does not qualify");
	}

	private static void assertPlan(VoidSubscription.CardClaimPlan plan, boolean grant,
								 boolean starter, boolean launch, String message) {
		assertEquals(grant, plan.grantsCard(), message + " (grant)");
		assertEquals(starter, plan.claimsStarter(), message + " (starter)");
		assertEquals(launch, plan.claimsLaunch(), message + " (launch)");
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void assertFalse(boolean condition, String message) {
		assertTrue(!condition, message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
		}
	}
}
