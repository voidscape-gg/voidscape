package com.openrsc.server.content.voiddungeon;

public final class VoidDungeonTraversalGraceTest {
	public static void main(String[] args) {
		stationaryPlayerGetsOneDecisionScan();
		acceptedWalkGetsExactlyFiveProtectedTicks();
		expiredOpportunityCannotActivate();
		System.out.println("Void Dungeon traversal grace tests passed");
	}

	private static void stationaryPlayerGetsOneDecisionScan() {
		VoidDungeonTraversalGrace.State state = new VoidDungeonTraversalGrace.State(100);
		assertTrue(state.blocksAutoAggro(100), "kill tick must be blocked");
		assertTrue(state.blocksAutoAggro(101), "packet decision scan must be blocked");
		assertFalse(state.blocksAutoAggro(102), "stationary AFK player must be eligible next scan");
	}

	private static void acceptedWalkGetsExactlyFiveProtectedTicks() {
		VoidDungeonTraversalGrace.State state = new VoidDungeonTraversalGrace.State(200);
		assertTrue(state.activate(201), "walk inside the opportunity must activate");
		for (long tick = 201; tick <= 205; tick++) {
			assertTrue(state.blocksAutoAggro(tick), "active grace lost tick " + tick);
		}
		assertFalse(state.blocksAutoAggro(206), "grace must expire after five ticks");
	}

	private static void expiredOpportunityCannotActivate() {
		VoidDungeonTraversalGrace.State state = new VoidDungeonTraversalGrace.State(300);
		assertFalse(state.activate(305), "expired opportunity must not activate");
		assertTrue(state.isExpired(305), "expired opportunity must be cleanable");
	}

	private static void assertTrue(boolean value, String message) {
		if (!value) throw new AssertionError(message);
	}

	private static void assertFalse(boolean value, String message) {
		assertTrue(!value, message);
	}
}
