package com.openrsc.server.model.action;

import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.RegionManager;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.lang.reflect.Field;

/** Exercises the production PvP reach predicate against real terrain masks. */
public final class WalkToMobActionCatchReachTest {
	public static void main(String[] args) throws Exception {
		World world = blankWorld();
		Point target = Point.location(100, 100);

		assertReach(world, Point.location(99, 100), target, true,
			"clear cardinal adjacency");
		assertReach(world, Point.location(99, 99), target, true,
			"clear diagonal adjacency");
		assertReach(world, Point.location(98, 100), target, false,
			"a checked point two tiles away is outside radius one");

		world.getTile(target).traversalMask = (byte)CollisionFlag.FULL_BLOCK_C;
		assertReach(world, Point.location(99, 100), target, false,
			"full-blocked target boundary");
		world.getTile(target).traversalMask = 0;

		Point west = Point.location(99, 100);
		world.getTile(west).traversalMask = (byte)CollisionFlag.WALL_WEST;
		assertReach(world, west, target, false, "cardinal wall boundary");
		world.getTile(west).traversalMask = 0;

		world.getTile(target).traversalMask = (byte)CollisionFlag.FULL_BLOCK_A;
		assertReach(world, Point.location(99, 99), target, false,
			"blocked diagonal target");

		System.out.println("WalkToMobAction authentic catch-reach tests passed");
	}

	private static void assertReach(
			World world, Point checked, Point target, boolean expected, String message) {
		boolean production = WalkToMobAction.isWithinInteractionReach(
			world, checked, target, 1, true);
		boolean authentic = checked.withinRange(target, 1)
			&& PathValidation.checkAdjacentDistance(world, checked, target, true, false);
		if (production != authentic) {
			throw new AssertionError(message + ": production predicate drifted from PvP pathing");
		}
		if (production != expected) {
			throw new AssertionError(message + ": expected " + expected + ", got " + production);
		}
	}

	private static World blankWorld() throws Exception {
		World world = (World)unsafe().getClass().getMethod("allocateInstance", Class.class)
			.invoke(unsafe(), World.class);
		RegionManager regions = new RegionManager(world);
		putObjectField(world, World.class.getDeclaredField("regionManager"), regions);
		for (int x = 94; x <= 106; x++) {
			for (int y = 94; y <= 106; y++) {
				world.getTile(x, y).traversalMask = 0;
			}
		}
		return world;
	}

	private static void putObjectField(Object owner, Field field, Object value) throws Exception {
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
}
