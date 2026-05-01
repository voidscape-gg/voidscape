package com.openrsc.server.plugins.custom.minigames.voidrush;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;
import com.openrsc.server.plugins.triggers.PlayerLogoutTrigger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VoidRushMinigame implements PlayerLoginTrigger, PlayerLogoutTrigger {
	private static final Object LOCK = new Object();
	private static final LinkedHashMap<Long, Player> QUEUE = new LinkedHashMap<Long, Player>();
	private static VoidRushInstance activeInstance;
	private static boolean startScheduled;

	public static boolean joinQueue(Player player) {
		synchronized (LOCK) {
			String failure = validateJoin(player);
			if (failure != null) {
				player.message(failure);
				return false;
			}

			QUEUE.put(player.getUsernameHash(), player);
			player.message("You join the Void Rush queue.");
			player.message("Void Rush queue: " + QUEUE.size() + "/" + VoidRushConfig.MAX_PLAYERS + ".");
			player.cancelAutoWalk();
			player.resetPath();
			player.teleport(VoidRushConfig.LOBBY_X, VoidRushConfig.LOBBY_Y, true);

			if (QUEUE.size() >= VoidRushConfig.MIN_PLAYERS && !startScheduled) {
				startScheduled = true;
				player.getWorld().getServer().getGameEventHandler().add(
					new VoidRushQueueStartEvent(player.getWorld()));
				messageQueue("Void Rush is starting soon.");
			}
			return true;
		}
	}

	public static boolean isQueued(Player player) {
		synchronized (LOCK) {
			return player != null && QUEUE.containsKey(player.getUsernameHash());
		}
	}

	public static boolean isActive(Player player) {
		synchronized (LOCK) {
			return player != null && activeInstance != null && activeInstance.contains(player);
		}
	}

	static void instanceEnded(VoidRushInstance instance) {
		synchronized (LOCK) {
			if (activeInstance == instance) {
				activeInstance = null;
			}
			if (!QUEUE.isEmpty() && QUEUE.size() >= VoidRushConfig.MIN_PLAYERS && !startScheduled) {
				startScheduled = true;
				Player first = QUEUE.values().iterator().next();
				first.getWorld().getServer().getGameEventHandler().add(
					new VoidRushQueueStartEvent(first.getWorld()));
			}
		}
	}

	@Override
	public boolean blockPlayerLogout(Player player) {
		synchronized (LOCK) {
			return QUEUE.containsKey(player.getUsernameHash())
				|| (activeInstance != null && activeInstance.contains(player));
		}
	}

	@Override
	public void onPlayerLogout(Player player) {
		synchronized (LOCK) {
			QUEUE.remove(player.getUsernameHash());
			if (activeInstance != null) {
				activeInstance.handleLogout(player);
			}
		}
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		synchronized (LOCK) {
			return VoidRushConfig.isInsideArena(player.getLocation())
				|| VoidRushConfig.isInsideSpectator(player.getLocation())
				|| (activeInstance != null && activeInstance.containsUsernameHash(player.getUsernameHash()));
		}
	}

	@Override
	public void onPlayerLogin(Player player) {
		synchronized (LOCK) {
			QUEUE.remove(player.getUsernameHash());
			if (activeInstance != null) {
				activeInstance.handleLogin(player);
			}
		}

		if (VoidRushConfig.isInsideArena(player.getLocation()) || VoidRushConfig.isInsideSpectator(player.getLocation())) {
			player.resetAll();
			player.setBusy(false);
			player.teleport(VoidRushConfig.EXIT_X, VoidRushConfig.EXIT_Y, true);
			player.message("You are returned from Void Rush.");
		}
	}

	private static String validateJoin(Player player) {
		if (player == null || !player.loggedIn() || player.isRemoved()) {
			return "You cannot enter Void Rush right now.";
		}
		if (activeInstance != null) {
			return "Void Rush is already underway.";
		}
		if (QUEUE.containsKey(player.getUsernameHash())) {
			return "You are already in the Void Rush queue.";
		}
		if (QUEUE.size() >= VoidRushConfig.MAX_PLAYERS) {
			return "The Void Rush queue is full.";
		}
		if (player.inCombat() || System.currentTimeMillis() - player.getCombatTimer() < 10000) {
			return "You need to be free from combat first.";
		}
		if (player.getTrade().isTradeActive()) {
			return "Finish trading before entering Void Rush.";
		}
		if (player.getDuel().isDuelActive() || player.getDuel().isDueling()) {
			return "Finish dueling before entering Void Rush.";
		}
		if (player.accessingBank() || player.isInBank()) {
			return "Close the bank before entering Void Rush.";
		}
		if (player.getOwnedPlugins().size() > 1) {
			return "Finish what you are doing before entering Void Rush.";
		}
		if (player.getSkills().getLevel(Skill.HITS.id()) <= 0) {
			return "You are in no state to enter Void Rush.";
		}
		if (VoidRushConfig.ENFORCE_ONE_ACCOUNT_PER_IP
			&& !isDummyPlayer(player)
			&& hasSameIpQueuedOrActive(player)) {
			return "Only one account from your IP may enter this Void Rush.";
		}
		return null;
	}

	private static boolean isDummyPlayer(Player player) {
		return player != null && player.getAttribute("dummyplayer", false);
	}

	private static boolean hasSameIpQueuedOrActive(Player player) {
		String ip = player.getCurrentIP();
		if (ip == null || ip.length() == 0) {
			return false;
		}
		for (Player queued : QUEUE.values()) {
			if (queued != null && ip.equals(queued.getCurrentIP())) {
				return true;
			}
		}
		return activeInstance != null && activeInstance.hasIp(player);
	}

	private static List<Player> drainQueue() {
		List<Player> players = new ArrayList<Player>();
		Iterator<Map.Entry<Long, Player>> iterator = QUEUE.entrySet().iterator();
		while (iterator.hasNext() && players.size() < VoidRushConfig.MAX_PLAYERS) {
			Player player = iterator.next().getValue();
			iterator.remove();
			if (isReadyForStart(player)) {
				players.add(player);
			} else if (player != null && player.loggedIn()) {
				player.message("You are removed from the Void Rush queue.");
			}
		}
		return players;
	}

	private static boolean isReadyForStart(Player player) {
		return player != null
			&& player.loggedIn()
			&& !player.isRemoved()
			&& !player.inCombat()
			&& System.currentTimeMillis() - player.getCombatTimer() >= 10000
			&& !player.getTrade().isTradeActive()
			&& !player.getDuel().isDuelActive()
			&& !player.getDuel().isDueling()
			&& !player.accessingBank()
			&& !player.isInBank()
			&& player.getSkills().getLevel(Skill.HITS.id()) > 0;
	}

	private static void messageQueue(String message) {
		for (Player player : QUEUE.values()) {
			if (player != null && player.loggedIn()) {
				player.message(message);
			}
		}
	}

	private static final class VoidRushQueueStartEvent extends GameTickEvent {
		private VoidRushQueueStartEvent(World world) {
			super(world, null, VoidRushConfig.QUEUE_START_DELAY_TICKS, "Void Rush Queue Start", DuplicationStrategy.ONE_PER_SERVER);
		}

		@Override
		public void run() {
			synchronized (LOCK) {
				startScheduled = false;
				if (activeInstance != null) {
					stop();
					return;
				}

				List<Player> players = drainQueue();
				if (players.size() < VoidRushConfig.MIN_PLAYERS) {
					for (Player player : players) {
						QUEUE.put(player.getUsernameHash(), player);
						player.message("Void Rush needs at least " + VoidRushConfig.MIN_PLAYERS + " players.");
					}
					stop();
					return;
				}

				activeInstance = new VoidRushInstance(getWorld(), players);
				activeInstance.start();
			}
			stop();
		}
	}
}
