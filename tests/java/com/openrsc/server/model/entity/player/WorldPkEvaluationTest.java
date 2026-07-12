package com.openrsc.server.model.entity.player;

import com.openrsc.server.content.WorldPkEvaluation;
import com.openrsc.server.model.Point;

import java.util.Collections;
import java.util.UUID;

/** Deterministic unit coverage for the pure qualified-PK evaluation boundary. */
public final class WorldPkEvaluationTest {
	private WorldPkEvaluationTest() {
	}

	public static void main(String[] args) {
		rejectReasonsHaveStablePrecedence();
		invalidIdentityFailsClosed();
		saturatingArithmeticIsNonnegativeAndExact();
		emptyLootInputsFailClosed();
		System.out.println("World PK evaluation tests passed.");
	}

	private static void rejectReasonsHaveStablePrecedence() {
		Fixture fixture = Fixture.withEveryPolicyFailure();

		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture.killerPlayerId = 101;
		assertReason(WorldPkEvaluation.NOT_WILDERNESS, fixture);
		fixture.inWilderness = true;
		assertReason(WorldPkEvaluation.NONZERO_INSTANCE, fixture);
		fixture.instanceId = 0;
		assertReason(WorldPkEvaluation.SAFE_ZONE_OR_DEATH, fixture);
		fixture.safeZone = false;
		assertReason(WorldPkEvaluation.SAFE_ZONE_OR_DEATH, fixture);
		fixture.safeDeath = false;
		assertReason(WorldPkEvaluation.DUEL, fixture);
		fixture.duel = false;
		assertReason(WorldPkEvaluation.VICTIM_NOT_SKULLED, fixture);
		fixture.victimSkulled = true;
		assertReason(WorldPkEvaluation.NON_DEFAULT_USER, fixture);
		fixture.killerDefaultUser = true;
		assertReason(WorldPkEvaluation.NON_DEFAULT_USER, fixture);
		fixture.victimDefaultUser = true;
		assertReason(WorldPkEvaluation.LOW_COMBAT_LEVEL, fixture);
		fixture.killerCombatLevel = 10;
		assertReason(WorldPkEvaluation.LOW_COMBAT_LEVEL, fixture);
		fixture.victimCombatLevel = 10;
		assertReason(WorldPkEvaluation.SAME_ACCOUNT, fixture);
		fixture.victimAccountId = 88L;
		assertReason(WorldPkEvaluation.SAME_IP, fixture);
		fixture.sameNonLoopbackIp = false;
		assertReason(WorldPkEvaluation.FRIENDS, fixture);
		fixture.killerFriendsWithVictim = false;
		assertReason(WorldPkEvaluation.FRIENDS, fixture);
		fixture.victimFriendsWithKiller = false;
		assertReason(WorldPkEvaluation.NO_VICTIM_DAMAGE, fixture);
		fixture.victimDamage = 1;
		assertReason(WorldPkEvaluation.PASS, fixture);
	}

	private static void invalidIdentityFailsClosed() {
		assertEquals(WorldPkEvaluation.INVALID_IDENTITY,
			WorldPkEvaluation.preliminaryRejectReason(null), "null evidence");

		Fixture fixture = Fixture.passing();
		fixture.victimPlayerId = fixture.killerPlayerId;
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture = Fixture.passing();
		fixture.killerUsernameHash = 0L;
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture = Fixture.passing();
		fixture.killerName = "   ";
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture = Fixture.passing();
		fixture.killerName = " Killer";
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture = Fixture.passing();
		fixture.victimName = "Victim ";
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
		fixture = Fixture.passing();
		fixture.victimName = "NameIsTooLong";
		assertReason(WorldPkEvaluation.INVALID_IDENTITY, fixture);
	}

	private static void saturatingArithmeticIsNonnegativeAndExact() {
		assertEquals(0L, WorldPkEvaluation.saturatingMultiplyPositive(0L, 4L),
			"zero product");
		assertEquals(0L, WorldPkEvaluation.saturatingMultiplyPositive(-1L, 4L),
			"negative product");
		assertEquals(42L, WorldPkEvaluation.saturatingMultiplyPositive(6L, 7L),
			"exact product");
		assertEquals(Long.MAX_VALUE,
			WorldPkEvaluation.saturatingMultiplyPositive(Long.MAX_VALUE, 2L),
			"overflowing product saturates");

		assertEquals(0L, WorldPkEvaluation.saturatingAddPositive(-5L, -1L),
			"negative add inputs clamp to zero");
		assertEquals(11L, WorldPkEvaluation.saturatingAddPositive(5L, 6L),
			"exact sum");
		assertEquals(Long.MAX_VALUE,
			WorldPkEvaluation.saturatingAddPositive(Long.MAX_VALUE - 2L, 3L),
			"overflowing sum saturates");
		assertEquals(Long.MAX_VALUE,
			WorldPkEvaluation.saturatingAddPositive(Long.MAX_VALUE, 1L),
			"already saturated sum remains saturated");
	}

	private static void emptyLootInputsFailClosed() {
		Fixture fixture = Fixture.passing();
		assertEquals(0L, WorldPkEvaluation.eligibleLootValue(null, Collections.emptyList()),
			"missing evidence has no loot");
		assertEquals(0L, WorldPkEvaluation.eligibleLootValue(fixture.evidence(), null),
			"missing post-drop list has no loot");
		assertEquals(0L, WorldPkEvaluation.eligibleLootValue(
			fixture.evidence(), Collections.emptyList()), "empty post-drop list has no loot");
		fixture.killerUsernameHash = 0L;
		assertEquals(0L, WorldPkEvaluation.eligibleLootValue(
			fixture.evidence(), Collections.emptyList()), "invalid immutable killer hash has no loot");
	}

	private static void assertReason(String expected, Fixture fixture) {
		assertEquals(expected, WorldPkEvaluation.preliminaryRejectReason(fixture.evidence()),
			"preliminary rejection");
	}

	private static void assertEquals(Object expected, Object actual, String message) {
		if (expected == null ? actual != null : !expected.equals(actual)) {
			throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
		}
	}

	private static final class Fixture {
		private int killerPlayerId;
		private String killerName = "Killer";
		private long killerUsernameHash = 123456L;
		private Long killerAccountId;
		private boolean killerDefaultUser;
		private int killerCombatLevel;
		private int victimPlayerId = 202;
		private String victimName = "Victim";
		private Long victimAccountId;
		private boolean victimDefaultUser;
		private int victimCombatLevel;
		private boolean inWilderness;
		private int instanceId;
		private boolean safeZone;
		private boolean safeDeath;
		private boolean duel;
		private boolean victimSkulled;
		private int victimDamage;
		private boolean sameNonLoopbackIp;
		private boolean killerFriendsWithVictim;
		private boolean victimFriendsWithKiller;

		private static Fixture withEveryPolicyFailure() {
			Fixture fixture = new Fixture();
			fixture.killerPlayerId = 0;
			fixture.inWilderness = false;
			fixture.instanceId = 44;
			fixture.safeZone = true;
			fixture.safeDeath = true;
			fixture.duel = true;
			fixture.victimSkulled = false;
			fixture.killerDefaultUser = false;
			fixture.victimDefaultUser = false;
			fixture.killerCombatLevel = 9;
			fixture.victimCombatLevel = 9;
			fixture.killerAccountId = 77L;
			fixture.victimAccountId = 77L;
			fixture.sameNonLoopbackIp = true;
			fixture.killerFriendsWithVictim = true;
			fixture.victimFriendsWithKiller = true;
			fixture.victimDamage = 0;
			return fixture;
		}

		private static Fixture passing() {
			Fixture fixture = new Fixture();
			fixture.killerPlayerId = 101;
			fixture.inWilderness = true;
			fixture.instanceId = 0;
			fixture.safeZone = false;
			fixture.safeDeath = false;
			fixture.duel = false;
			fixture.victimSkulled = true;
			fixture.killerDefaultUser = true;
			fixture.victimDefaultUser = true;
			fixture.killerCombatLevel = 10;
			fixture.victimCombatLevel = 10;
			fixture.killerAccountId = 77L;
			fixture.victimAccountId = 88L;
			fixture.sameNonLoopbackIp = false;
			fixture.killerFriendsWithVictim = false;
			fixture.victimFriendsWithKiller = false;
			fixture.victimDamage = 1;
			return fixture;
		}

		private PvpKillEvidence evidence() {
			return new PvpKillEvidence(
				UUID.fromString("123e4567-e89b-12d3-a456-426614174099"),
				1_789_000_000_000L,
				Point.location(216, 3527), instanceId,
				inWilderness, safeZone, inWilderness ? 42 : 0,
				safeDeath, duel,
				killerPlayerId, killerName, killerUsernameHash, killerAccountId,
				killerDefaultUser, killerCombatLevel,
				victimPlayerId, victimName, victimAccountId,
				victimDefaultUser, victimCombatLevel, victimSkulled,
				victimDamage, sameNonLoopbackIp,
				killerFriendsWithVictim, victimFriendsWithKiller);
		}
	}
}
