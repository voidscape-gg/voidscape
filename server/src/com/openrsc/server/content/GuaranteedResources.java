package com.openrsc.server.content;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.util.rsc.MessageType;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class GuaranteedResources {
	public static final String MINING = "mining";
	public static final String WOODCUTTING = "woodcutting";
	public static final String FISHING = "fishing";

	private static final String ATTRIBUTE_PREFIX = "guaranteed_resource_";
	private static final int FAILURES_BEFORE_GUARANTEE = 4;
	private static final int MINIMUM_NODE_YIELD = 3;
	private static final ConcurrentHashMap<String, AtomicInteger> nodeYields = new ConcurrentHashMap<String, AtomicInteger>();

	private GuaranteedResources() {
	}

	public static boolean shouldGuarantee(Player player, String action, Object resourceKey) {
		return getFailureCount(player, action, resourceKey) >= FAILURES_BEFORE_GUARANTEE;
	}

	public static int failuresBeforeGuarantee() {
		return FAILURES_BEFORE_GUARANTEE;
	}

	public static int minimumNodeYield() {
		return MINIMUM_NODE_YIELD;
	}

	public static int getFailureCount(Player player, String action, Object resourceKey) {
		Object value = player.getAttribute(attribute(action, resourceKey));
		if (value instanceof Number) {
			return ((Number) value).intValue();
		}
		return 0;
	}

	public static void setFailureCount(Player player, String action, Object resourceKey, int failures) {
		String attribute = attribute(action, resourceKey);
		if (failures <= 0) {
			player.removeAttribute(attribute);
			return;
		}
		player.setAttribute(attribute, Math.min(failures, FAILURES_BEFORE_GUARANTEE));
	}

	public static void recordAttempt(Player player, String action, Object resourceKey, boolean success) {
		String attribute = attribute(action, resourceKey);
		if (success) {
			player.removeAttribute(attribute);
			return;
		}

		int failures = Math.min(FAILURES_BEFORE_GUARANTEE, getFailureCount(player, action, resourceKey) + 1);
		player.setAttribute(attribute, failures);
	}

	public static void notifyIfGuaranteed(Player player, boolean guaranteed) {
		if (guaranteed) {
			player.playerServerMessage(MessageType.QUEST, "@yel@Your persistence pays off.");
		}
	}

	public static int recordNodeYield(GameObject object, String action) {
		if (object == null) {
			return 0;
		}
		AtomicInteger counter = nodeYields.get(nodeKey(object, action));
		if (counter == null) {
			AtomicInteger created = new AtomicInteger(0);
			counter = nodeYields.putIfAbsent(nodeKey(object, action), created);
			if (counter == null) {
				counter = created;
			}
		}
		return counter.incrementAndGet();
	}

	public static boolean shouldProtectNode(GameObject object, String action) {
		return getNodeYieldCount(object, action) < MINIMUM_NODE_YIELD;
	}

	public static int getNodeYieldCount(GameObject object, String action) {
		if (object == null) {
			return 0;
		}
		AtomicInteger counter = nodeYields.get(nodeKey(object, action));
		return counter == null ? 0 : counter.get();
	}

	public static void clearNode(GameObject object, String action) {
		if (object != null) {
			nodeYields.remove(nodeKey(object, action));
		}
	}

	private static String attribute(String action, Object resourceKey) {
		return ATTRIBUTE_PREFIX + action + "_" + resourceKey;
	}

	private static String nodeKey(GameObject object, String action) {
		return action + "_" + object.getID() + "_" + object.getX() + "_" + object.getY() + "_" + object.getType();
	}
}
