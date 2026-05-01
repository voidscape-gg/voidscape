package com.openrsc.server.plugins.custom.minigames.voidrush;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.Region;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.CollisionFlag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class VoidRushInstance {
	private enum Phase {
		COUNTDOWN,
		RUNNING,
		ENDING
	}

	private final World world;
	private final List<Player> allPlayers;
	private final LinkedHashMap<Long, Player> activePlayers = new LinkedHashMap<Long, Player>();
	private final Set<Long> eliminatedPlayers = new HashSet<Long>();
	private final boolean soloTestRun;
	private final List<GameObject> arenaObjects = new ArrayList<GameObject>();
	private final List<GameObject> waveObjects = new ArrayList<GameObject>();
	private final Map<Integer, Byte> savedTileMasks = new HashMap<Integer, Byte>();
	private final Random random;

	private GameTickEvent event;
	private Phase phase = Phase.COUNTDOWN;
	private int countdownTicks = VoidRushConfig.COUNTDOWN_TICKS;
	private int round;
	private int wavesThisRound;
	private int waveIndex;
	private int warningTicks;
	private int ticksUntilWaveStep;
	private VoidRushWave currentWave;
	private boolean rewardAwarded;

	public VoidRushInstance(World world, List<Player> players) {
		this.world = world;
		this.allPlayers = new ArrayList<Player>(players);
		for (Player player : players) {
			activePlayers.put(player.getUsernameHash(), player);
		}
		this.soloTestRun = players.size() == 1;
		this.random = new Random(world.getServer().getCurrentTick() ^ players.size());
	}

	public void start() {
		prepareArena();
		teleportPlayersIntoArena();
		broadcastActive("Void Rush is starting!");
		if (soloTestRun) {
			broadcastActive("Solo test run. Survive as long as you can.");
		}
		event = new VoidRushEvent(world, this);
		world.getServer().getGameEventHandler().add(event);
	}

	public boolean contains(Player player) {
		return player != null && activePlayers.containsKey(player.getUsernameHash());
	}

	public boolean containsUsernameHash(long usernameHash) {
		return activePlayers.containsKey(usernameHash);
	}

	public boolean hasIp(Player player) {
		String ip = player.getCurrentIP();
		if (ip == null || ip.length() == 0) {
			return false;
		}
		for (Player active : activePlayers.values()) {
			if (active != null && active != player && ip.equals(active.getCurrentIP())) {
				return true;
			}
		}
		return false;
	}

	public void handleLogout(Player player) {
		if (player == null || !activePlayers.containsKey(player.getUsernameHash())) {
			return;
		}
		eliminate(player, null, false);
	}

	public void handleLogin(Player player) {
		if (player == null) {
			return;
		}
		Player oldPlayer = activePlayers.get(player.getUsernameHash());
		if (oldPlayer != null) {
			eliminate(oldPlayer, null, false);
		}
	}

	private void tick() {
		if (phase == Phase.ENDING) {
			stopEvent();
			return;
		}

		removeInvalidPlayers();
		if (checkEndCondition()) {
			return;
		}

		if (phase == Phase.COUNTDOWN) {
			tickCountdown();
			return;
		}

		tickWave();
	}

	private void tickCountdown() {
		if (countdownTicks > 0) {
			broadcastActive("The Void surges in " + countdownTicks + "...");
			countdownTicks--;
			return;
		}

		for (Player player : activePlayers.values()) {
			if (player.loggedIn()) {
				player.setBusy(false);
				player.resetPath();
			}
		}
		phase = Phase.RUNNING;
		broadcastActive("The Void surges!");
		startNextRound();
	}

	private void tickWave() {
		if (currentWave == null) {
			startNextRound();
			return;
		}

		if (warningTicks > 0) {
			warningTicks--;
			if (warningTicks == 0) {
				broadcastActive("A void wave tears across the arena!");
				makeCurrentLineLethal();
			}
			return;
		}

		eliminatePlayersOnCurrentWave();
		if (checkEndCondition()) {
			return;
		}

		ticksUntilWaveStep--;
		if (ticksUntilWaveStep <= 0) {
			currentWave.advance();
			if (!currentWave.hasActiveLines()) {
				finishCurrentWave();
			} else {
				makeCurrentLineLethal();
			}
		}
	}

	private void startNextRound() {
		round++;
		waveIndex = 0;
		wavesThisRound = round >= 8 && random.nextInt(3) == 0 ? 2 : 1;
		startNextWave();
	}

	private void startNextWave() {
		waveIndex++;
		currentWave = rollWave();
		warningTicks = VoidRushConfig.WARNING_TICKS;
		ticksUntilWaveStep = 0;
		cleanupWaveObjects();
		showWaveEffects(currentWave, false);
		broadcastActive("Void energy gathers...");
	}

	private void finishCurrentWave() {
		cleanupWaveObjects();
		currentWave = null;
		if (checkEndCondition()) {
			return;
		}
		if (waveIndex < wavesThisRound) {
			startNextWave();
		} else {
			startNextRound();
		}
	}

	private VoidRushWave rollWave() {
		for (int attempt = 0; attempt < 32; attempt++) {
			VoidRushWave.Direction direction = VoidRushWave.Direction.values()[random.nextInt(VoidRushWave.Direction.values().length)];
			int gapSize = rollGapSize();
			int gapStart = rollGapStart(direction, gapSize);
			VoidRushWave wave = new VoidRushWave(direction, gapStart, gapSize, rollWaveDelay(), rollWaveStride());
			if (hasReachableGap(wave)) {
				return wave;
			}
		}

		VoidRushWave.Direction fallbackDirection = VoidRushWave.Direction.NORTH_TO_SOUTH;
		int gapSize = Math.max(VoidRushConfig.MIN_GAP_SIZE, Math.min(VoidRushConfig.STARTING_GAP_SIZE, rollGapSize()));
		int min = VoidRushConfig.ARENA_MIN_X + 1;
		int max = VoidRushConfig.ARENA_MAX_X - 1;
		int gapStart = clamp((min + max - gapSize) / 2, min, max - gapSize + 1);
		return new VoidRushWave(fallbackDirection, gapStart, gapSize, rollWaveDelay(), rollWaveStride());
	}

	private int rollGapSize() {
		if (round <= 3) {
			return random.nextInt(2) == 0 ? VoidRushConfig.STARTING_GAP_SIZE : VoidRushConfig.STARTING_GAP_SIZE - 1;
		}
		if (round <= 7) {
			return random.nextInt(2) == 0 ? 3 : 2;
		}
		return random.nextInt(4) == 0 ? VoidRushConfig.MIN_GAP_SIZE : 2;
	}

	private int rollWaveDelay() {
		return Math.max(VoidRushConfig.MIN_WAVE_DELAY, VoidRushConfig.STARTING_WAVE_DELAY);
	}

	private int rollWaveStride() {
		if (round <= 2) {
			return VoidRushConfig.STARTING_WAVE_STRIDE;
		}
		return VoidRushConfig.MAX_WAVE_STRIDE;
	}

	private int rollGapStart(VoidRushWave.Direction direction, int gapSize) {
		VoidRushWave probe = new VoidRushWave(direction, 0, gapSize, rollWaveDelay(), rollWaveStride());
		int min = probe.getPerpendicularMin();
		int max = probe.getPerpendicularMax();
		int maxStart = max - gapSize + 1;
		int anchor = min + random.nextInt(Math.max(1, max - min + 1));

		if (!activePlayers.isEmpty()) {
			List<Player> players = new ArrayList<Player>(activePlayers.values());
			Player player = players.get(random.nextInt(players.size()));
			if (player != null) {
				Point location = player.getLocation();
				anchor = probe.isHorizontal() ? location.getX() : location.getY();
				anchor += random.nextInt(5) - 2;
			}
		}

		return clamp(anchor - (gapSize / 2), min, maxStart);
	}

	private boolean hasReachableGap(VoidRushWave wave) {
		for (Player player : activePlayers.values()) {
			if (player == null || !player.loggedIn() || player.isRemoved()) {
				continue;
			}
			Point location = player.getLocation();
			if (!VoidRushConfig.isInsideArenaInterior(location)) {
				continue;
			}
			int lineDistance = Math.abs(wave.getLine() - wave.getLineCoordinate(location));
			int ticksBeforeHit = VoidRushConfig.WARNING_TICKS
				+ (((lineDistance + wave.getStrideTiles() - 1) / wave.getStrideTiles()) * wave.getDelayTicks());
			int distanceToGap = wave.distanceToGap(wave.getPerpendicularCoordinate(location));
			if (distanceToGap <= ticksBeforeHit) {
				return true;
			}
		}
		return activePlayers.isEmpty();
	}

	private void makeCurrentLineLethal() {
		cleanupWaveObjects();
		if (currentWave == null || !currentWave.hasActiveLines()) {
			finishCurrentWave();
			return;
		}
		showWaveEffects(currentWave, true);
		eliminatePlayersOnCurrentWave();
		ticksUntilWaveStep = currentWave.getDelayTicks();
		checkEndCondition();
	}

	private void eliminatePlayersOnCurrentWave() {
		if (currentWave == null || currentWave.isPastArena()) {
			return;
		}

		List<Player> eliminated = new ArrayList<Player>();
		for (Player player : activePlayers.values()) {
			if (player != null && currentWave.isDangerous(player.getLocation())) {
				eliminated.add(player);
			}
		}

		for (Player player : eliminated) {
			eliminate(player, "You were swallowed by the Void.", true);
		}
	}

	private void eliminate(Player player, String message, boolean teleport) {
		if (player == null || activePlayers.remove(player.getUsernameHash()) == null) {
			return;
		}

		eliminatedPlayers.add(player.getUsernameHash());
		if (player.loggedIn()) {
			player.resetAll();
			player.setBusy(false);
			player.cancelAutoWalk();
			player.resetPath();
			if (message != null && message.length() > 0) {
				player.message(message);
			}
			if (teleport) {
				player.teleport(VoidRushConfig.SPECTATOR_X, VoidRushConfig.SPECTATOR_Y, true);
			}
		}
	}

	private boolean checkEndCondition() {
		if (phase == Phase.ENDING) {
			return true;
		}
		if (!soloTestRun && activePlayers.size() == 1) {
			Player winner = activePlayers.values().iterator().next();
			end(winner);
			return true;
		}
		if (activePlayers.isEmpty()) {
			end(null);
			return true;
		}
		return false;
	}

	private void end(Player winner) {
		if (phase == Phase.ENDING) {
			return;
		}
		phase = Phase.ENDING;
		cleanupWaveObjects();

		if (winner == null) {
			broadcastAll("Nobody survived the Void Rush.");
		}

		for (Player player : allPlayers) {
			if (player != null && player.loggedIn()) {
				player.resetAll();
				player.setBusy(false);
				player.cancelAutoWalk();
				player.resetPath();
				player.teleport(VoidRushConfig.EXIT_X, VoidRushConfig.EXIT_Y, true);
			}
		}

		if (winner != null && winner.loggedIn() && !rewardAwarded) {
			rewardAwarded = true;
			giveReward(winner);
			winner.message("You survived Void Rush and won a Christmas cracker!");
			world.sendWorldMessage(winner.getUsername() + " has survived Void Rush and won a Christmas cracker!");
		}

		cleanupArena();
		stopEvent();
		VoidRushMinigame.instanceEnded(this);
	}

	private void giveReward(Player winner) {
		Item reward = new Item(VoidRushConfig.CHRISTMAS_CRACKER_ID, 1);
		if (winner.getCarriedItems().getInventory().canHold(reward)) {
			winner.getCarriedItems().getInventory().add(reward);
			return;
		}

		winner.getWorld().registerItem(
			new GroundItem(winner.getWorld(), VoidRushConfig.CHRISTMAS_CRACKER_ID, winner.getX(), winner.getY(), 1, winner),
			winner.getConfig().GAME_TICK * 300);
		winner.message("Your inventory is full, so the prize falls at your feet.");
	}

	private void removeInvalidPlayers() {
		List<Player> removed = new ArrayList<Player>();
		for (Player player : activePlayers.values()) {
			if (player == null || !player.loggedIn() || player.isRemoved() || player.isUnregistering()) {
				removed.add(player);
			} else if (!VoidRushConfig.isInsideArenaInterior(player.getLocation())) {
				removed.add(player);
			}
		}

		for (Player player : removed) {
			eliminate(player, player != null && player.loggedIn() ? "You were swallowed by the Void." : null, player != null && player.loggedIn());
		}
	}

	private void teleportPlayersIntoArena() {
		List<Point> starts = buildStartTiles();
		int index = 0;
		for (Player player : allPlayers) {
			if (player == null || !player.loggedIn()) {
				continue;
			}
			Point start = starts.get(index % starts.size());
			index++;
			player.resetAll();
			player.setBusy(true);
			player.cancelAutoWalk();
			player.resetPath();
			player.teleport(start.getX(), start.getY(), true);
		}
	}

	private List<Point> buildStartTiles() {
		List<Point> starts = new ArrayList<Point>();
		int centerX = (VoidRushConfig.ARENA_MIN_X + VoidRushConfig.ARENA_MAX_X) / 2;
		int centerY = (VoidRushConfig.ARENA_MIN_Y + VoidRushConfig.ARENA_MAX_Y) / 2;
		for (int radius = 0; starts.size() < VoidRushConfig.MAX_PLAYERS && radius < 8; radius++) {
			for (int x = centerX - radius; x <= centerX + radius; x++) {
				for (int y = centerY - radius; y <= centerY + radius; y++) {
					Point point = Point.location(x, y);
					if (VoidRushConfig.isInsideArenaInterior(point) && Math.max(Math.abs(x - centerX), Math.abs(y - centerY)) == radius) {
						starts.add(point);
					}
					if (starts.size() >= VoidRushConfig.MAX_PLAYERS) {
						return starts;
					}
				}
			}
		}
		if (starts.isEmpty()) {
			starts.add(Point.location(centerX, centerY));
		}
		return starts;
	}

	private void prepareArena() {
		for (int x = VoidRushConfig.ARENA_MIN_X; x <= VoidRushConfig.ARENA_MAX_X; x++) {
			for (int y = VoidRushConfig.ARENA_MIN_Y; y <= VoidRushConfig.ARENA_MAX_Y; y++) {
				saveTileMask(x, y);
				world.getTile(x, y).traversalMask = 0;
			}
		}

		for (int x = VoidRushConfig.ARENA_MIN_X; x <= VoidRushConfig.ARENA_MAX_X; x++) {
			placeArenaWall(x, VoidRushConfig.ARENA_MIN_Y);
			placeArenaWall(x, VoidRushConfig.ARENA_MAX_Y);
			blockArenaTile(x, VoidRushConfig.ARENA_MIN_Y);
			blockArenaTile(x, VoidRushConfig.ARENA_MAX_Y);
		}
		for (int y = VoidRushConfig.ARENA_MIN_Y + 1; y < VoidRushConfig.ARENA_MAX_Y; y++) {
			placeArenaWall(VoidRushConfig.ARENA_MIN_X, y);
			placeArenaWall(VoidRushConfig.ARENA_MAX_X, y);
			blockArenaTile(VoidRushConfig.ARENA_MIN_X, y);
			blockArenaTile(VoidRushConfig.ARENA_MAX_X, y);
		}
	}

	private void cleanupArena() {
		for (GameObject object : arenaObjects) {
			if (!object.isRemoved()) {
				world.unregisterGameObject(object);
			}
		}
		arenaObjects.clear();
		for (Map.Entry<Integer, Byte> entry : savedTileMasks.entrySet()) {
			int key = entry.getKey();
			int x = key >> 16;
			int y = key & 0xffff;
			world.getTile(x, y).traversalMask = entry.getValue();
		}
		savedTileMasks.clear();
	}

	private void placeArenaWall(int x, int y) {
		placeObject(VoidRushConfig.ARENA_WALL_OBJECT_ID, Point.location(x, y), arenaObjects);
	}

	private void blockArenaTile(int x, int y) {
		saveTileMask(x, y);
		world.getTile(x, y).traversalMask |= CollisionFlag.FULL_BLOCK_C;
	}

	private void showWaveEffects(VoidRushWave wave, boolean lethal) {
		placeWaveObjects(wave);
		sendWaveProjectile(wave, lethal);
	}

	private void placeWaveObjects(VoidRushWave wave) {
		if (!VoidRushConfig.VOID_WAVE_USE_TEMP_OBJECTS
			|| VoidRushConfig.VOID_WAVE_OBJECT_ID <= 0
			|| wave == null
			|| !wave.hasActiveLines()) {
			return;
		}

		int minLine = Math.max(wave.getSweptLineMin(), wave.getLineMin());
		int maxLine = Math.min(wave.getSweptLineMax(), wave.getLineMax());
		for (int line = minLine; line <= maxLine; line++) {
			for (int coordinate = wave.getPerpendicularMin(); coordinate <= wave.getPerpendicularMax(); coordinate++) {
				if (wave.isInGap(coordinate)) {
					continue;
				}
				placeObject(VoidRushConfig.VOID_WAVE_OBJECT_ID, wave.pointAt(line, coordinate), waveObjects);
			}
		}
	}

	private void sendWaveProjectile(VoidRushWave wave, boolean lethal) {
		if (!VoidRushConfig.VOID_WAVE_USE_CLIENT_PROJECTILE || wave == null || !wave.hasActiveLines()) {
			return;
		}
		for (Player player : activePlayers.values()) {
			if (player != null && player.loggedIn() && player.isUsingCustomClient()) {
				ActionSender.sendVoidRushWave(player, wave.getDirection().ordinal(), wave.getPreviousLine(),
					wave.getLine(), wave.getGapStart(), wave.getGapEnd(), lethal);
			}
		}
	}

	private void placeObject(int objectId, Point point, List<GameObject> targetList) {
		Region region = world.getRegionManager().getRegion(point);
		if (region == null || region.getGameObject(point) != null) {
			return;
		}
		GameObject object = new GameObject(world, point, objectId, 0, 0);
		world.registerGameObject(object);
		targetList.add(object);
	}

	private void cleanupWaveObjects() {
		for (GameObject object : waveObjects) {
			if (!object.isRemoved()) {
				world.unregisterGameObject(object);
			}
		}
		waveObjects.clear();
	}

	private void saveTileMask(int x, int y) {
		int key = tileKey(x, y);
		if (!savedTileMasks.containsKey(key)) {
			savedTileMasks.put(key, world.getTile(x, y).traversalMask);
		}
	}

	private int tileKey(int x, int y) {
		return (x << 16) | (y & 0xffff);
	}

	private void broadcastActive(String message) {
		for (Player player : activePlayers.values()) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private void broadcastAll(String message) {
		for (Player player : allPlayers) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private int clamp(int value, int min, int max) {
		if (value < min) {
			return min;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	private void stopEvent() {
		if (event != null) {
			event.stop();
		}
	}

	private static final class VoidRushEvent extends GameTickEvent {
		private final VoidRushInstance instance;

		private VoidRushEvent(World world, VoidRushInstance instance) {
			super(world, null, 0, "Void Rush", DuplicationStrategy.ALLOW_MULTIPLE);
			this.instance = instance;
		}

		@Override
		public void run() {
			if (getDelayTicks() == 0) {
				setDelayTicks(1);
			}
			instance.tick();
		}
	}
}
