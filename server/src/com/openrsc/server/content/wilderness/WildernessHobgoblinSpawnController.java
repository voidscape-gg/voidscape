package com.openrsc.server.content.wilderness;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.external.NPCLoc;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.rsc.MessageType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class WildernessHobgoblinSpawnController {
	private static final String DYNAMIC_ATTRIBUTE = "voidscape_dynamic_wild_hobgoblin";
	private static final int HOBGOBLIN_ID = NpcId.HOBGOBLIN_LVL32.id();
	private static final int MIN_X = 202;
	private static final int MAX_X = 246;
	private static final int MIN_Y = 230;
	private static final int MAX_Y = 274;
	private static final int EVALUATE_INTERVAL_MS = 30000;
	private static final int FAST_RESPAWN_IPS = 3;
	private static final int EXTRA_SPAWN_IPS = 4;
	private static final int MAX_RESPAWN_IPS = 5;
	private static final int MAX_EXTRA_HOBGOBLINS = 5;
	private static final double FAST_RESPAWN_MULTIPLIER = 0.5D;
	private static final double MAX_RESPAWN_MULTIPLIER = 1.0D / 3.0D;
	private static final String EXTRA_SPAWN_MESSAGE = "@mag@Void energy crackles nearby - more hobgoblins are drawn into the area.";
	private static final int[][] EXTRA_SPAWNS = {
		{217, 255},
		{219, 252},
		{220, 257},
		{221, 259},
		{226, 246}
	};

	private final World world;
	private final List<Npc> extraHobgoblins = new ArrayList<>();
	private long nextEvaluationAt;
	private Integer debugUniqueIpCount;
	private int lastUniqueIpCount;
	private int lastTargetExtraCount;
	private double lastRespawnMultiplier = 1.0D;
	private long lastEvaluatedAt;

	public WildernessHobgoblinSpawnController(final World world) {
		this.world = world;
	}

	public synchronized void tick() {
		final long now = System.currentTimeMillis();
		if (now < nextEvaluationAt) {
			return;
		}
		nextEvaluationAt = now + EVALUATE_INTERVAL_MS;
		evaluate(now);
	}

	public synchronized void evaluateNow() {
		final long now = System.currentTimeMillis();
		nextEvaluationAt = now + EVALUATE_INTERVAL_MS;
		evaluate(now);
	}

	public synchronized void setDebugUniqueIpCount(final Integer uniqueIpCount) {
		debugUniqueIpCount = uniqueIpCount == null ? null : Math.max(0, uniqueIpCount);
		evaluateNow();
	}

	public synchronized void clearDebugUniqueIpCount() {
		debugUniqueIpCount = null;
		evaluateNow();
	}

	public synchronized String statusSummary() {
		return "Wildy hobgoblins: uniqueIPs=" + lastUniqueIpCount
			+ (debugUniqueIpCount == null ? "" : " (debug)")
			+ ", respawn=" + respawnLabel(lastRespawnMultiplier)
			+ ", extras=" + currentExtraCount() + "/" + lastTargetExtraCount
			+ ", cap=" + MAX_EXTRA_HOBGOBLINS
			+ ", zone=(" + MIN_X + "," + MIN_Y + ")-(" + MAX_X + "," + MAX_Y + ")";
	}

	public boolean isZone(final Point point) {
		return point != null && isZone(point.getX(), point.getY());
	}

	private void evaluate(final long now) {
		cleanupExtraList();
		lastUniqueIpCount = debugUniqueIpCount == null ? countUniquePlayerIps() : debugUniqueIpCount;
		lastRespawnMultiplier = respawnMultiplier(lastUniqueIpCount);
		lastTargetExtraCount = targetExtraCount(lastUniqueIpCount);
		lastEvaluatedAt = now;

		applyRespawnMultiplier(lastRespawnMultiplier);
		adjustExtraHobgoblins(lastTargetExtraCount);
	}

	private int countUniquePlayerIps() {
		final Set<String> uniqueIps = new HashSet<>();
		for (final Player player : world.getPlayers()) {
			if (player == null || player.isRemoved() || !player.isLoggedIn() || player.getLocation() == null) {
				continue;
			}
			if (!player.getLocation().inWilderness() || !isZone(player.getLocation())) {
				continue;
			}
			final String ip = player.getCurrentIP();
			if (ip != null && !ip.isEmpty()) {
				uniqueIps.add(ip);
			}
		}
		return uniqueIps.size();
	}

	private void applyRespawnMultiplier(final double multiplier) {
		for (final Npc npc : world.getNpcs()) {
			if (!isBaseZoneHobgoblin(npc)) {
				continue;
			}
			if (multiplier < 1.0D) {
				npc.setRespawnMultiplierOverride(multiplier);
			} else {
				npc.clearRespawnMultiplierOverride();
			}
		}
	}

	private void adjustExtraHobgoblins(final int targetExtraCount) {
		int spawnedCount = 0;
		while (currentExtraCount() < targetExtraCount) {
			if (!spawnExtraHobgoblin(currentExtraCount())) {
				break;
			}
			spawnedCount++;
		}
		if (spawnedCount > 0) {
			messagePlayersInZone();
		}
		if (currentExtraCount() > targetExtraCount && despawnOneIdleExtra()) {
			cleanupExtraList();
		}
	}

	private boolean spawnExtraHobgoblin(final int index) {
		final int[] spawn = EXTRA_SPAWNS[index % EXTRA_SPAWNS.length];
		final int startX = spawn[0];
		final int startY = spawn[1];
		if ((world.getTile(startX, startY).overlay & 64) != 0) {
			return false;
		}
		final NPCLoc loc = new NPCLoc(HOBGOBLIN_ID, startX, startY,
			Math.max(MIN_X, startX - 15), Math.min(MAX_X, startX + 15),
			Math.max(MIN_Y, startY - 15), Math.min(MAX_Y, startY + 15));
		final Npc npc = new Npc(world, loc);
		npc.setShouldRespawn(false);
		npc.setAttribute(DYNAMIC_ATTRIBUTE, true);
		world.registerNpc(npc);
		extraHobgoblins.add(npc);
		return true;
	}

	private void messagePlayersInZone() {
		for (final Player player : world.getPlayers()) {
			if (player == null || player.isRemoved() || !player.isLoggedIn() || player.getLocation() == null) {
				continue;
			}
			if (player.getLocation().inWilderness() && isZone(player.getLocation())) {
				player.playerServerMessage(MessageType.QUEST, EXTRA_SPAWN_MESSAGE);
			}
		}
	}

	private boolean despawnOneIdleExtra() {
		for (final Npc npc : extraHobgoblins) {
			if (npc == null || !world.hasNpc(npc) || npc.isRemoved() || npc.isUnregistering()) {
				continue;
			}
			if (npc.inCombat() || npc.isBusy() || npc.isRespawning()) {
				continue;
			}
			npc.remove();
			return true;
		}
		return false;
	}

	private void cleanupExtraList() {
		for (final Iterator<Npc> iterator = extraHobgoblins.iterator(); iterator.hasNext(); ) {
			final Npc npc = iterator.next();
			if (npc == null || !world.hasNpc(npc) || npc.isRemoved() || npc.isUnregistering()) {
				iterator.remove();
			}
		}
	}

	private boolean isBaseZoneHobgoblin(final Npc npc) {
		if (npc == null || npc.getID() != HOBGOBLIN_ID || isDynamicExtra(npc)) {
			return false;
		}
		return isZone(npc.getLoc().startX(), npc.getLoc().startY());
	}

	private boolean isDynamicExtra(final Npc npc) {
		return npc.getAttribute(DYNAMIC_ATTRIBUTE, false);
	}

	private int currentExtraCount() {
		return extraHobgoblins.size();
	}

	private int targetExtraCount(final int uniqueIpCount) {
		if (uniqueIpCount < EXTRA_SPAWN_IPS) {
			return 0;
		}
		return Math.min(MAX_EXTRA_HOBGOBLINS, uniqueIpCount - (EXTRA_SPAWN_IPS - 1));
	}

	private double respawnMultiplier(final int uniqueIpCount) {
		if (uniqueIpCount >= MAX_RESPAWN_IPS) {
			return MAX_RESPAWN_MULTIPLIER;
		}
		if (uniqueIpCount >= FAST_RESPAWN_IPS) {
			return FAST_RESPAWN_MULTIPLIER;
		}
		return 1.0D;
	}

	private String respawnLabel(final double multiplier) {
		if (multiplier >= 1.0D) {
			return "normal";
		}
		return multiplier == FAST_RESPAWN_MULTIPLIER ? "42s" : "28s";
	}

	private boolean isZone(final int x, final int y) {
		return x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y;
	}

	public long getLastEvaluatedAt() {
		return lastEvaluatedAt;
	}
}
