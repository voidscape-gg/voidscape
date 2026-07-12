package com.openrsc.server.model.entity.player;

import com.openrsc.server.model.Point;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.UUID;

public final class PvpKillEvidenceTest {
	public static void main(String[] args) {
		capturesImmutablePolicyInputs();
		discardsRawAddressMaterial();
		bindsOneCanonicalDeathIdToTheContext();
		System.out.println("PvP kill evidence tests passed.");
	}

	private static void capturesImmutablePolicyInputs() {
		final UUID deathId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		final Point point = Point.location(216, 3527);
		final long wideAccountId = 4_294_967_296L;
		final PvpKillEvidence evidence = new PvpKillEvidence(
			deathId, 1_752_000_123_456L, point, 73,
			true, false, 42,
			false, false,
			101, "Killer", 9_876_543_210L, wideAccountId,
			true, 88,
			202, "Victim", -1L,
			false, -5, true,
			-1, true,
			true, false);

		assertEquals(deathId, evidence.getDeathId(), "death UUID");
		assertEquals(deathId.toString(), evidence.getCanonicalDeathId(), "canonical UUID text");
		assertEquals(1_752_000_123_456L, evidence.getOccurredAtMs(), "frozen death time");
		assertTrue(evidence.getDeathPoint() != point, "death point must be defensively copied");
		assertEquals(216, evidence.getDeathPoint().getX(), "death x");
		assertEquals(3527, evidence.getDeathPoint().getY(), "death y");
		assertEquals(73, evidence.getDeathInstanceId(), "death instance");
		assertTrue(evidence.isDeathPointInWilderness(), "wilderness snapshot");
		assertFalse(evidence.isDeathPointInSafeZone(), "safe-zone snapshot");
		assertEquals(42, evidence.getWildernessLevel(), "wilderness level");
		assertFalse(evidence.isSafeDeath(), "safe-death snapshot");
		assertFalse(evidence.isDuelActive(), "duel snapshot");
		assertEquals(101, evidence.getKillerPlayerId(), "killer id");
		assertEquals("Killer", evidence.getKillerName(), "killer name");
		assertEquals(9_876_543_210L, evidence.getKillerUsernameHash(), "killer username hash");
		assertEquals(Long.valueOf(wideAccountId), evidence.getKillerAccountId(), "killer account");
		assertTrue(evidence.isKillerDefaultUser(), "killer ordinary-user snapshot");
		assertEquals(88, evidence.getKillerCombatLevel(), "killer combat");
		assertEquals(202, evidence.getVictimPlayerId(), "victim id");
		assertEquals("Victim", evidence.getVictimName(), "victim name");
		assertEquals(null, evidence.getVictimAccountId(), "invalid account becomes unlinked");
		assertFalse(evidence.isVictimDefaultUser(), "victim ordinary-user snapshot");
		assertEquals(0, evidence.getVictimCombatLevel(), "negative combat clamps to zero");
		assertTrue(evidence.wasVictimSkulled(), "victim skull snapshot");
		assertEquals(0, evidence.getVictimDamageToKiller(), "missing damage clamps to zero");
		assertTrue(evidence.hasSameNonLoopbackCurrentIp(), "same-IP snapshot");
		assertTrue(evidence.isKillerFriendsWithVictim(), "killer friend direction");
		assertFalse(evidence.isVictimFriendsWithKiller(), "victim friend direction");
		assertTrue(evidence.isEitherDirectionFriend(), "either-direction friendship");

		for (Field field : PvpKillEvidence.class.getDeclaredFields()) {
			assertTrue(Modifier.isFinal(field.getModifiers()),
				"evidence field must be final: " + field.getName());
			final String fieldName = field.getName().toLowerCase();
			assertFalse(field.getType() == String.class
				&& (fieldName.contains("ip") || fieldName.contains("address")),
				"raw address field is forbidden: " + field.getName());
		}
	}

	private static void discardsRawAddressMaterial() {
		assertTrue(PvpKillEvidence.sameNonLoopbackAddress("203.0.113.4", "203.0.113.4"),
			"matching public addresses should be captured as same");
		assertTrue(PvpKillEvidence.sameNonLoopbackAddress(" 2001:DB8::4 ", "2001:db8::4"),
			"address comparison should normalize case and whitespace");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress("203.0.113.4", "203.0.113.5"),
			"different addresses must not match");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress(null, null),
			"missing addresses must not match");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress("127.12.0.4", "127.12.0.4"),
			"IPv4 loopback must not reject local QA players");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress("::1", "::1"),
			"compressed IPv6 loopback must not reject local QA players");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress(
			"0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1"),
			"expanded IPv6 loopback must not reject local QA players");
		assertFalse(PvpKillEvidence.sameNonLoopbackAddress("::ffff:127.0.0.1", "::ffff:127.0.0.1"),
			"IPv4-mapped loopback must not reject local QA players");
	}

	private static void bindsOneCanonicalDeathIdToTheContext() {
		final UUID deathId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
		final Point point = Point.location(100, 200);
		final PvpKillEvidence evidence = new PvpKillEvidence(
			deathId, 1L, point, 0,
			false, false, 0,
			false, false,
			1, "One", 11L, null, true, 10,
			2, "Two", null, true, 10, false,
			1, false, false, false);
		final PlayerDeathContext context = new PlayerDeathContext(
			deathId, point, 0, null, Collections.emptyList(), evidence);

		assertEquals(deathId, context.getDeathId(), "context death UUID");
		assertEquals(deathId.toString(), context.getCanonicalDeathId(), "context UUID text");
		assertTrue(context.getPvpKillEvidence() == evidence, "context evidence identity");

		boolean mismatchedIdRejected = false;
		try {
			new PlayerDeathContext(UUID.randomUUID(), point, 0, null,
				Collections.emptyList(), evidence);
		} catch (IllegalArgumentException expected) {
			mismatchedIdRejected = true;
		}
		assertTrue(mismatchedIdRejected, "context must reject mismatched evidence identity");

		boolean nonpositiveTimeRejected = false;
		try {
			new PvpKillEvidence(
				deathId, 0L, point, 0,
				false, false, 0,
				false, false,
				1, "One", 11L, null, true, 10,
				2, "Two", null, true, 10, false,
				1, false, false, false);
		} catch (IllegalArgumentException expected) {
			nonpositiveTimeRejected = true;
		}
		assertTrue(nonpositiveTimeRejected, "death time must be positive");

		final PlayerDeathContext nonPvp = new PlayerDeathContext(
			point, 0, null, Collections.emptyList());
		assertEquals(null, nonPvp.getPvpKillEvidence(), "legacy/non-PvP context has no evidence");
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
		}
	}

	private static void assertEquals(long expected, long actual, String message) {
		if (expected != actual) {
			throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
		}
	}
}
