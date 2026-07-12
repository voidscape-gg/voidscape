package com.openrsc.server.util;

import com.openrsc.server.Server;
import com.openrsc.server.constants.AppearanceId;
import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.PlayerAppearance;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.update.ChatMessage;
import com.openrsc.server.model.entity.update.Damage;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
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
	private static final int MAX_CINEMATIC_BOTS = 48;
	private static final int DEFAULT_RADIUS = 18;
	private static final int DEFAULT_INTERVAL_TICKS = 2;
	private static final int LOADTEST_DB_ID_BASE = -1_000_000;
	private static final int CINEMATIC_DB_ID_BASE = -1_200_000;
	private static final int CINEMATIC_RUNE_KITE_APPEARANCE = 242;
	private static final int WILDERNESS_CASTLE_BODY_X = 258;
	private static final int WILDERNESS_CASTLE_BODY_Y = 333;
	private static final int WILDERNESS_CASTLE_FIGHT_X = 258;
	private static final int WILDERNESS_CASTLE_FIGHT_Y = 371;
	private static final int VOID_SPARROW_CINEMATIC_DISTANCE = 96;
	private static final long VOID_SPARROW_CINEMATIC_DURATION_MILLIS = 45_000L;
	private static final String CINEMATIC_SCENE_BOSS = "bossfight";
	private static final String CINEMATIC_SCENE_TEAM = "teamfight";
	private static final String CINEMATIC_SCENE_SPARROW_CASTLE = "sparrowcastle";
	private static final String[] CINEMATIC_BOSS_LINES = {
		"Face me!",
		"Burn!",
		"You cannot win",
		"I am the end"
	};
	private static final String[] CINEMATIC_TEAM_LINES = {
		"Push!",
		"Fall back!",
		"Hold the gate!",
		"Pile north!",
		"Rally on me!"
	};

	private final Server server;
	private final ArrayList<Player> bots = new ArrayList<>();
	private final ArrayList<Npc> cinematicNpcs = new ArrayList<>();
	private final Random random = new Random(0x510ADE);

	private GameTickEvent driver;
	private GameTickEvent cinematicDriver;
	private int radius = DEFAULT_RADIUS;
	private int intervalTicks = DEFAULT_INTERVAL_TICKS;
	private long startedAt;
	private int cinematicTick;
	private String cinematicSceneType = "";
	private Point cinematicAnchor;

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

	public synchronized String startCinematicBossFight(final Player director, final int requestedCount, final int requestedBossId, final int requestedRadius) {
		if (server.getEntityHandler().getNpcDef(requestedBossId) == null) {
			return "invalid cinematic boss npc id " + requestedBossId;
		}
		stop();

		final int count = Math.max(1, Math.min(MAX_CINEMATIC_BOTS, requestedCount));
		radius = Math.max(2, Math.min(12, requestedRadius));
		intervalTicks = 2;
		cinematicTick = 0;
		cinematicSceneType = CINEMATIC_SCENE_BOSS;

		final World world = server.getWorld();
		final Point origin = findCinematicOrigin(world, director);
		cinematicAnchor = origin;
		final Point bossLocation = findOpenNear(world, Point.location(origin.getX() + 2, origin.getY()), origin, radius);
		final Npc boss = new Npc(world, requestedBossId, bossLocation.getX(), bossLocation.getY(),
			bossLocation.getX() - radius, bossLocation.getX() + radius,
			bossLocation.getY() - radius, bossLocation.getY() + radius);
		boss.setAttribute("cinematic_scene", true);
		boss.setShouldRespawn(false);
		world.registerNpc(boss);
		cinematicNpcs.add(boss);

		for (int i = 0; i < count; i++) {
			final Point location = findCinematicSpawn(world, bossLocation, i, radius);
			final Player bot = createCinematicBot(world, i, location);
			world.getPlayers().add(bot);
			bot.updateRegion();
			bots.add(bot);
		}

		startedAt = System.currentTimeMillis();
		cinematicDriver = new GameTickEvent(world, null, intervalTicks, "Cinematic Boss Fight Driver", DuplicationStrategy.ONE_PER_SERVER) {
			@Override
			public void run() {
				tickCinematicScene();
			}
		};
		server.getGameEventHandler().add(cinematicDriver);
		return status();
	}

	public synchronized String startCinematicTeamFight(final Player director, final int requestedCount, final int requestedRadius) {
		stop();

		final World world = server.getWorld();
		final Point origin = findCinematicOrigin(world, director);
		return startCinematicTeamFightAt(world, origin, requestedCount, requestedRadius, CINEMATIC_SCENE_TEAM);
	}

	public synchronized String startVoidSparrowCastleFlyover(final Player director, final int requestedCount, final int requestedRadius) {
		if (director == null) {
			return "cinematic flyover needs a logged-in director";
		}
		stop();

		final World world = server.getWorld();
		final Point bodyLocation = findOpenNear(world,
			Point.location(WILDERNESS_CASTLE_BODY_X, WILDERNESS_CASTLE_BODY_Y),
			Point.location(WILDERNESS_CASTLE_BODY_X, WILDERNESS_CASTLE_BODY_Y), 6);
		final Point fightLocation = findOpenNear(world,
			Point.location(WILDERNESS_CASTLE_FIGHT_X, WILDERNESS_CASTLE_FIGHT_Y), bodyLocation, 10);

		moveDirectorForCinematic(director, bodyLocation);
		final String sceneStatus = startCinematicTeamFightAt(world, fightLocation, requestedCount, requestedRadius,
			CINEMATIC_SCENE_SPARROW_CASTLE);
		director.startVoidScout(VOID_SPARROW_CINEMATIC_DURATION_MILLIS, VOID_SPARROW_CINEMATIC_DISTANCE);
		director.queueVoidScoutPath(buildVoidSparrowCastlePath(bodyLocation, fightLocation));
		director.message("@mag@Cinematic flyover queued over the Dark Warriors Fortress.");
		return sceneStatus + "; void sparrow flyover queued from " + bodyLocation + " to " + fightLocation;
	}

	private void moveDirectorForCinematic(final Player director, final Point location) {
		director.stopVoidScout(null);
		director.cancelAutoWalk();
		director.resetPath();
		director.setLocation(location, true);
		ActionSender.sendWorldInfo(director);
	}

	private String startCinematicTeamFightAt(final World world, final Point origin, final int requestedCount,
											final int requestedRadius, final String sceneType) {
		final int count = Math.max(2, Math.min(MAX_CINEMATIC_BOTS, requestedCount));
		radius = Math.max(3, Math.min(16, requestedRadius));
		intervalTicks = 2;
		cinematicTick = 0;
		cinematicSceneType = sceneType;
		cinematicAnchor = origin;

		for (int i = 0; i < count; i++) {
			final int team = i % 2;
			final Point location = findTeamFightSpawn(world, origin, i / 2, team, radius);
			final Player bot = createCinematicBot(world, i, location);
			bot.setAttribute("cinematic_team", team);
			world.getPlayers().add(bot);
			bot.updateRegion();
			bots.add(bot);
		}

		startedAt = System.currentTimeMillis();
		cinematicDriver = new GameTickEvent(world, null, intervalTicks, "Cinematic Team Fight Driver", DuplicationStrategy.ONE_PER_SERVER) {
			@Override
			public void run() {
				tickCinematicScene();
			}
		};
		server.getGameEventHandler().add(cinematicDriver);
		return status();
	}

	public synchronized String stop() {
		if (driver != null) {
			driver.stop();
			driver = null;
		}
		if (cinematicDriver != null) {
			cinematicDriver.stop();
			cinematicDriver = null;
		}
		final int removed = bots.size();
		for (Player bot : new ArrayList<>(bots)) {
			removeBot(bot);
		}
		bots.clear();
		final int removedNpcs = cinematicNpcs.size();
		for (Npc npc : new ArrayList<>(cinematicNpcs)) {
			removeCinematicNpc(npc);
		}
		cinematicNpcs.clear();
		startedAt = 0;
		cinematicTick = 0;
		cinematicSceneType = "";
		cinematicAnchor = null;
		return "stopped " + removed + " synthetic load bots"
			+ (removedNpcs > 0 ? " and " + removedNpcs + " cinematic NPCs" : "");
	}

	public synchronized String status() {
		if (cinematicDriver != null) {
			final long elapsed = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
			final String scene = cinematicSceneType == null || cinematicSceneType.isEmpty()
				? "cinematic"
				: cinematicSceneType;
			final String anchor = cinematicAnchor == null ? "none" : cinematicAnchor.toString();
			if (CINEMATIC_SCENE_BOSS.equals(scene)) {
				final String bossName = cinematicNpcs.isEmpty() || cinematicNpcs.get(0).getDef() == null
					? "none"
					: cinematicNpcs.get(0).getDef().getName();
				return "cinematic scene active: type=" + scene
					+ ", actors=" + bots.size()
					+ ", boss=" + bossName
					+ ", radius=" + radius
					+ ", anchor=" + anchor
					+ ", elapsed=" + elapsed + "s";
			}
			return "cinematic scene active: actors=" + bots.size()
				+ ", type=" + scene
				+ ", radius=" + radius
				+ ", anchor=" + anchor
				+ ", elapsed=" + elapsed + "s";
		}
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

	private Player createCinematicBot(final World world, final int ordinal, final Point location) {
		final String username = String.format("cinebot%04d", ordinal + 1);
		final long usernameHash = DataConversions.usernameToHash(username);
		final Player bot = new Player(world, usernameHash);
		bot.setAttribute("dummyplayer", true);
		bot.setAttribute("cinematicbot", true);
		bot.setAttribute("loadtest_home_x", location.getX());
		bot.setAttribute("loadtest_home_y", location.getY());
		bot.setDatabaseID(CINEMATIC_DB_ID_BASE - ordinal);
		bot.setClientVersion(server.getConfig().CLIENT_VERSION);
		bot.setClientLimitations(new ClientLimitations(server.getConfig().CLIENT_VERSION));
		bot.setInitialLocation(location);
		bot.setNextRegionLoad();
		bot.setMale(true);
		bot.getSettings().setAppearance(new PlayerAppearance(ordinal % 10, (ordinal * 2) % 15, (ordinal + 6) % 15, ordinal % 5, 1, 2));

		final int[] worn = bot.getSettings().getAppearance().getSprites();
		worn[AppearanceId.SLOT_SHIELD] = CINEMATIC_RUNE_KITE_APPEARANCE;
		worn[AppearanceId.SLOT_WEAPON] = AppearanceId.DRAGON_SWORD.id();
		worn[AppearanceId.SLOT_HAT] = AppearanceId.LARGE_RUNE_HELMET.id();
		worn[AppearanceId.SLOT_BODY] = AppearanceId.RUNE_PLATE_MAIL_BODY.id();
		worn[AppearanceId.SLOT_LEGS] = AppearanceId.RUNE_PLATE_MAIL_LEGS.id();
		worn[AppearanceId.SLOT_GLOVES] = AppearanceId.LEATHER_GLOVES.id();
		worn[AppearanceId.SLOT_BOOTS] = AppearanceId.LEATHER_BOOTS.id();
		worn[AppearanceId.SLOT_CAPE] = ordinal % 2 == 0 ? AppearanceId.RED_CAPE.id() : AppearanceId.BLUE_CAPE.id();
		bot.setWornItems(worn);

		setCinematicSkill(bot, Skill.ATTACK.id(), 99);
		setCinematicSkill(bot, Skill.DEFENSE.id(), 99);
		setCinematicSkill(bot, Skill.STRENGTH.id(), 99);
		setCinematicSkill(bot, Skill.HITS.id(), 99);
		bot.setLoggedIn(true);
		bot.setBusy(false);
		return bot;
	}

	private void setCinematicSkill(final Player bot, final int skill, final int level) {
		bot.getSkills().setExperienceAndLevel(skill, bot.getSkills().experienceForLevel(level), level, false);
	}

	private void removeBot(final Player bot) {
		try {
			server.getGameEventHandler().getPlayerEvents(bot).forEach(GameTickEvent::stop);
			if (bot.getRegion() != null) {
				bot.remove();
			}
			server.getWorld().removePlayer(bot.getUsernameHash());
			bot.setLoggedIn(false);
		} catch (Exception e) {
			// Load test cleanup should never destabilize the server.
		}
	}

	private void removeCinematicNpc(final Npc npc) {
		try {
			if (npc.getCombatEvent() != null) {
				npc.getCombatEvent().stop();
			}
			server.getWorld().unregisterNpc(npc);
		} catch (Exception e) {
			// Cinematic cleanup should never destabilize the server.
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

	private void tickCinematicScene() {
		if (CINEMATIC_SCENE_TEAM.equals(cinematicSceneType)
			|| CINEMATIC_SCENE_SPARROW_CASTLE.equals(cinematicSceneType)) {
			tickCinematicTeamFightScene();
			return;
		}
		tickCinematicBossFightScene();
	}

	private void tickCinematicBossFightScene() {
		final List<Player> botSnapshot;
		final Npc boss;
		synchronized (this) {
			if (bots.isEmpty() || cinematicNpcs.isEmpty()) return;
			botSnapshot = new ArrayList<>(bots);
			boss = cinematicNpcs.get(0);
			cinematicTick++;
		}
		if (boss == null || boss.isRemoved()) return;

		boss.getSkills().setLevel(Skill.HITS.id(), Math.max(boss.getSkills().getMaxStat(Skill.HITS.id()), 1), false);
		if (!botSnapshot.isEmpty()) {
			boss.face(botSnapshot.get(cinematicTick % botSnapshot.size()));
		}
		if (cinematicTick % 3 == 0) {
			boss.setSprite(8);
		}
		if (cinematicTick % 4 == 0) {
			boss.getUpdateFlags().setDamage(new Damage(boss, random.nextInt(19)));
		}
		if (cinematicTick % 34 == 0) {
			boss.getUpdateFlags().setChatMessage(new ChatMessage(boss, CINEMATIC_BOSS_LINES[random.nextInt(CINEMATIC_BOSS_LINES.length)], null));
		}

		for (int i = 0; i < botSnapshot.size(); i++) {
			final Player bot = botSnapshot.get(i);
			if (bot == null || bot.isRemoved()) continue;
			bot.getSkills().setLevel(Skill.HITS.id(), 99, false);
			bot.face(boss);

			if ((cinematicTick + i) % 2 == 0) {
				bot.setSprite(9);
			}
			if ((cinematicTick + i) % 12 == 0) {
				bot.getUpdateFlags().setDamage(new Damage(bot, random.nextInt(7)));
			}
			if ((cinematicTick + i) % 16 == 0 && bot.getWalkingQueue() != null && bot.getWalkingQueue().finished()) {
				final Point next = findCinematicSpawn(bot.getWorld(), boss.getLocation(), i + (cinematicTick / 16), radius);
				if (!next.equals(bot.getLocation())) {
					bot.walk(next.getX(), next.getY());
				}
			}
		}
	}

	private void tickCinematicTeamFightScene() {
		final List<Player> botSnapshot;
		final Point anchor;
		synchronized (this) {
			if (bots.isEmpty()) return;
			botSnapshot = new ArrayList<>(bots);
			anchor = cinematicAnchor;
			cinematicTick++;
		}

		for (int i = 0; i < botSnapshot.size(); i++) {
			final Player bot = botSnapshot.get(i);
			if (bot == null || bot.isRemoved()) continue;

			bot.getSkills().setLevel(Skill.HITS.id(), 99, false);
			final Player target = findOpposingTeamTarget(botSnapshot, bot, i);
			if (target != null) {
				bot.face(target);
			}

			if ((cinematicTick + i) % 2 == 0) {
				bot.setSprite(9);
			}
			if ((cinematicTick + i) % 7 == 0) {
				bot.getUpdateFlags().setDamage(new Damage(bot, random.nextInt(13)));
			}
			if ((cinematicTick + i) % 41 == 0) {
				bot.getUpdateFlags().setChatMessage(new ChatMessage(bot,
					CINEMATIC_TEAM_LINES[random.nextInt(CINEMATIC_TEAM_LINES.length)], null));
			}
			if ((cinematicTick + i) % 12 == 0 && bot.getWalkingQueue() != null && bot.getWalkingQueue().finished()) {
				final Point next = findTeamFightStep(bot, target, anchor, i, radius);
				if (!next.equals(bot.getLocation())) {
					bot.walk(next.getX(), next.getY());
				}
			}
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

	private Point findCinematicOrigin(final World world, final Player director) {
		if (director != null && director.getLocation() != null && world.getTile(director.getLocation()) != null) {
			return director.getLocation();
		}
		return findOrigin(world);
	}

	private Point findCinematicSpawn(final World world, final Point origin, final int ordinal, final int sceneRadius) {
		final int[][] offsets = {
			{-2, -1}, {-1, -2}, {1, -2}, {2, -1},
			{2, 1}, {1, 2}, {-1, 2}, {-2, 1},
			{-3, 0}, {0, -3}, {3, 0}, {0, 3}
		};
		final int[] offset = offsets[Math.floorMod(ordinal, offsets.length)];
		final int ring = Math.max(0, Math.min(2, ordinal / offsets.length));
		final Point desired = Point.location(Math.max(0, origin.getX() + offset[0] + Integer.signum(offset[0]) * ring),
			Math.max(0, origin.getY() + offset[1] + Integer.signum(offset[1]) * ring));
		return findOpenNear(world, desired, origin, sceneRadius + 2);
	}

	private Point findTeamFightSpawn(final World world, final Point origin, final int ordinal, final int team,
									 final int sceneRadius) {
		final int rowSpan = Math.max(3, (sceneRadius * 2) + 1);
		final int row = Math.floorMod(ordinal, rowSpan) - sceneRadius;
		final int rank = ordinal / rowSpan;
		final int side = team == 0 ? -1 : 1;
		final int front = Math.max(3, sceneRadius / 2) + rank;
		final Point desired = Point.location(origin.getX() + (side * front), Math.max(0, origin.getY() + row));
		return findOpenNear(world, desired, origin, sceneRadius + 6);
	}

	private Player findOpposingTeamTarget(final List<Player> snapshot, final Player bot, final int ordinal) {
		final int team = bot.getAttribute("cinematic_team", ordinal % 2);
		Player best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Player candidate : snapshot) {
			if (candidate == null || candidate == bot || candidate.isRemoved()) continue;
			if (candidate.getAttribute("cinematic_team", -1) == team) continue;
			final int distance = Math.max(Math.abs(bot.getX() - candidate.getX()), Math.abs(bot.getY() - candidate.getY()));
			if (distance < bestDistance) {
				best = candidate;
				bestDistance = distance;
			}
		}
		return best;
	}

	private Point findTeamFightStep(final Player bot, final Player target, final Point anchor, final int ordinal,
									final int sceneRadius) {
		final World world = bot.getWorld();
		final Point toward = target == null ? anchor : target.getLocation();
		for (int attempts = 0; attempts < 10; attempts++) {
			int dx = toward == null ? 0 : Integer.compare(toward.getX(), bot.getX());
			int dy = toward == null ? 0 : Integer.compare(toward.getY(), bot.getY());
			if (attempts > 0 || random.nextInt(4) == 0) {
				dx = random.nextInt(3) - 1;
				dy = random.nextInt(3) - 1;
			}
			if (dx == 0 && dy == 0) continue;
			final int nextX = bot.getX() + dx;
			final int nextY = bot.getY() + dy;
			if (anchor != null
				&& (Math.abs(nextX - anchor.getX()) > sceneRadius + 6
				|| Math.abs(nextY - anchor.getY()) > sceneRadius + 6)) {
				continue;
			}
			if (world.getTile(nextX, nextY) == null) continue;
			if (!PathValidation.checkAdjacentStatic(world, bot.getX(), bot.getY(), nextX, nextY)) continue;
			return Point.location(nextX, nextY);
		}
		return bot.getLocation();
	}

	private List<Point> buildVoidSparrowCastlePath(final Point bodyLocation, final Point fightLocation) {
		final ArrayList<Point> path = new ArrayList<>();
		Point cursor = bodyLocation;
		cursor = appendScoutLine(path, cursor, Point.location(bodyLocation.getX(), 344));
		cursor = appendScoutLine(path, cursor, Point.location(bodyLocation.getX() + 3, 350));
		cursor = appendScoutLine(path, cursor, Point.location(fightLocation.getX() + 2, fightLocation.getY() - 7));
		cursor = appendScoutLine(path, cursor, fightLocation);
		cursor = appendScoutLine(path, cursor, Point.location(fightLocation.getX() - 2, fightLocation.getY() + 1));
		cursor = appendScoutLine(path, cursor, Point.location(fightLocation.getX() + 2, fightLocation.getY() + 2));
		appendScoutLine(path, cursor, fightLocation);
		return path;
	}

	private Point appendScoutLine(final List<Point> path, final Point from, final Point to) {
		int x = from.getX();
		int y = from.getY();
		while (x != to.getX() || y != to.getY()) {
			x += Integer.compare(to.getX(), x);
			y += Integer.compare(to.getY(), y);
			path.add(Point.location(x, y));
		}
		return Point.location(x, y);
	}

	private Point findOpenNear(final World world, final Point desired, final Point fallback, final int searchRadius) {
		if (desired != null && world.getTile(desired) != null) {
			return desired;
		}
		for (int distance = 1; distance <= Math.max(1, searchRadius); distance++) {
			for (int dx = -distance; dx <= distance; dx++) {
				for (int dy = -distance; dy <= distance; dy++) {
					if (Math.abs(dx) != distance && Math.abs(dy) != distance) continue;
					final Point candidate = Point.location(fallback.getX() + dx, fallback.getY() + dy);
					if (world.getTile(candidate) != null) {
						return candidate;
					}
				}
			}
		}
		return fallback;
	}
}
