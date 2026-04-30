package com.openrsc.server.util;

import com.openrsc.server.Server;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ClientLimitations;
import com.openrsc.server.util.rsc.DataConversions;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * In-process synthetic player swarm for local server load testing.
 *
 * These are real Player instances in the world list/regions, so they exercise
 * player tick processing, walking, AOI scans, NPC/player update generation, and
 * event overhead. They are explicitly marked as dummy players to avoid DB save,
 * auth, and socket lifecycle paths.
 */
public final class SyntheticLoadService {
	private static final int MAX_BOTS = 500;
	private static final int DEFAULT_RADIUS = 18;
	private static final int DEFAULT_INTERVAL_TICKS = 2;
	private static final int LOADTEST_DB_ID_BASE = -1_000_000;

	private final Server server;
	private final ArrayList<Player> bots = new ArrayList<>();
	private final Random random = new Random(0x510ADE);

	private GameTickEvent driver;
	private int radius = DEFAULT_RADIUS;
	private int intervalTicks = DEFAULT_INTERVAL_TICKS;
	private long startedAt;

	public SyntheticLoadService(final Server server) {
		this.server = server;
	}

	public synchronized String start(final int requestedCount, final int requestedRadius, final int requestedIntervalTicks) {
		stop();

		final int count = Math.max(1, Math.min(MAX_BOTS, requestedCount));
		radius = Math.max(1, Math.min(96, requestedRadius));
		intervalTicks = Math.max(1, Math.min(50, requestedIntervalTicks));

		final World world = server.getWorld();
		final Point origin = findOrigin(world);
		for (int i = 0; i < count; i++) {
			final Point location = findSpawn(world, origin, i);
			final Player bot = createBot(world, i, location);
			world.getPlayers().add(bot);
			bot.updateRegion();
			bots.add(bot);
		}

		startedAt = System.currentTimeMillis();
		driver = new GameTickEvent(world, null, intervalTicks, "Synthetic Load Driver", DuplicationStrategy.ONE_PER_SERVER) {
			@Override
			public void run() {
				tickBots();
			}
		};
		server.getGameEventHandler().add(driver);
		return status();
	}

	public synchronized String stop() {
		if (driver != null) {
			driver.stop();
			driver = null;
		}
		final int removed = bots.size();
		for (Player bot : new ArrayList<>(bots)) {
			removeBot(bot);
		}
		bots.clear();
		startedAt = 0;
		return "stopped " + removed + " synthetic load bots";
	}

	public synchronized String status() {
		if (bots.isEmpty()) {
			return "synthetic load bots inactive";
		}
		final long elapsed = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
		return "synthetic load bots active: count=" + bots.size()
			+ ", radius=" + radius
			+ ", intervalTicks=" + intervalTicks
			+ ", elapsed=" + elapsed + "s";
	}

	public synchronized int count() {
		return bots.size();
	}

	private Player createBot(final World world, final int ordinal, final Point location) {
		final String username = String.format("loadbot%04d", ordinal + 1);
		final long usernameHash = DataConversions.usernameToHash(username);
		final Player bot = new Player(world, usernameHash);
		bot.setAttribute("dummyplayer", true);
		bot.setAttribute("loadtest", true);
		bot.setAttribute("loadtest_home_x", location.getX());
		bot.setAttribute("loadtest_home_y", location.getY());
		bot.setDatabaseID(LOADTEST_DB_ID_BASE - ordinal);
		bot.setClientVersion(server.getConfig().CLIENT_VERSION);
		bot.setClientLimitations(new ClientLimitations(server.getConfig().CLIENT_VERSION));
		bot.setInitialLocation(location);
		bot.setNextRegionLoad();
		bot.setMale(true);
		bot.getSettings().setAppearance(new PlayerAppearance(ordinal % 10, ordinal % 15, (ordinal + 5) % 15, ordinal % 5, 1, 2));
		bot.setWornItems(bot.getSettings().getAppearance().getSprites());
		bot.getSkills().setLevel(Skill.HITS.id(), 10, false);
		bot.setLoggedIn(true);
		bot.setBusy(false);
		return bot;
	}

	private void removeBot(final Player bot) {
		try {
			server.getGameEventHandler().getPlayerEvents(bot).forEach(GameTickEvent::stop);
			if (bot.getRegion() != null) {
				bot.remove();
			}
			server.getWorld().getPlayers().remove(bot);
			server.getWorld().removePlayer(bot.getUsernameHash());
			bot.setLoggedIn(false);
		} catch (Exception e) {
			// Load test cleanup should never destabilize the server.
		}
	}

	private void tickBots() {
		final List<Player> snapshot;
		synchronized (this) {
			if (bots.isEmpty()) return;
			snapshot = new ArrayList<>(bots);
		}
		for (Player bot : snapshot) {
			if (bot.isRemoved() || bot.getWalkingQueue() == null || !bot.getWalkingQueue().finished()) continue;
			queueRandomStep(bot);
		}
	}

	private void queueRandomStep(final Player bot) {
		final World world = bot.getWorld();
		final int homeX = bot.getAttribute("loadtest_home_x", bot.getX());
		final int homeY = bot.getAttribute("loadtest_home_y", bot.getY());
		for (int attempts = 0; attempts < 12; attempts++) {
			final int dx = random.nextInt(3) - 1;
			final int dy = random.nextInt(3) - 1;
			if (dx == 0 && dy == 0) continue;
			final int nextX = bot.getX() + dx;
			final int nextY = bot.getY() + dy;
			if (Math.abs(nextX - homeX) > radius || Math.abs(nextY - homeY) > radius) continue;
			if (world.getTile(nextX, nextY) == null) continue;
			if (!PathValidation.checkAdjacentStatic(world, bot.getX(), bot.getY(), nextX, nextY)) continue;
			bot.walk(nextX, nextY);
			return;
		}
	}

	private Point findOrigin(final World world) {
		final int x = world.getServer().getConfig().RESPAWN_LOCATION_X;
		final int y = world.getServer().getConfig().RESPAWN_LOCATION_Y;
		if (world.getTile(x, y) != null) {
			return Point.location(x, y);
		}
		return Point.location(120, 648);
	}

	private Point findSpawn(final World world, final Point origin, final int ordinal) {
		if (world.getTile(origin.getX(), origin.getY()) != null) {
			final int side = Math.max(1, (int)Math.ceil(Math.sqrt(ordinal + 1)));
			final int offsetX = (ordinal % (side * 2 + 1)) - side;
			final int offsetY = ((ordinal / (side * 2 + 1)) % (side * 2 + 1)) - side;
			final Point candidate = Point.location(origin.getX() + offsetX, origin.getY() + offsetY);
			if (world.getTile(candidate) != null) {
				return candidate;
			}
		}
		return origin;
	}
}
