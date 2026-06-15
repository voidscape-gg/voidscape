package orsc;

import net.arikia.dev.drpc.DiscordEventHandlers;
import net.arikia.dev.drpc.DiscordRPC;
import net.arikia.dev.drpc.DiscordRichPresence;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The class used to initialise and set user status on Discord.
 */
public class Discord {

	public static DiscordEventHandlers discord;

	public static final String APPLICATION_ID = setting("voidscape.discordApplicationId",
		"VOIDSCAPE_DISCORD_APPLICATION_ID", "");
	private static final String LARGE_IMAGE_KEY = setting("voidscape.discordLargeImageKey",
		"VOIDSCAPE_DISCORD_LARGE_IMAGE_KEY", "voidscape_logo");
	private static final String LARGE_IMAGE_TEXT = setting("voidscape.discordLargeImageText",
		"VOIDSCAPE_DISCORD_LARGE_IMAGE_TEXT", "Voidscape");

	public static boolean startedDiscord = false;

	private static final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
	private static final Runnable presenceTask = new PresenceCheck();
	private static final Runnable discordTask = new DiscordUpdate();
	private static ScheduledFuture scheduled;
	private static String lastUpdate = "Voidscape";

	private static String setting(String property, String env, String fallback) {
		String value = System.getProperty(property);
		if (value != null && value.trim().length() > 0) {
			return value.trim();
		}
		value = System.getenv(env);
		if (value != null && value.trim().length() > 0) {
			return value.trim();
		}
		return fallback;
	}

	/**
	 * Write whether or not Discord is currently in use. This prevents race conditions when multiple clients are
	 * initialised at the same time.
	 * @param inuse Status to set.
	 */
	public static void setInUse(final boolean inuse) {
		try {
			Files.write(Paths.get(Config.F_CACHE_DIR + File.separator + "discord_inuse.txt"), (inuse ? "1" : "0").getBytes());
		} catch (Exception e) {
		}
	}

	/**
	 * Get lockfile status.
	 * @return True if Discord is already initialised.
	 */
	public static boolean getInUse() {
		try {
			final String read = Files.readAllLines(Paths.get(Config.F_CACHE_DIR + File.separator + "discord_inuse.txt")).get(0);
			return read.equals("1");
		} catch (Exception e) {
			setInUse(true);
		}
		return false;
	}

	/**
	 * Task to check for Discord presence.
	 */
	static class PresenceCheck implements Runnable {
		public void run() {
			if (APPLICATION_ID.length() == 0) {
				startedDiscord = true;
				setInUse(false);
				System.out.println("Discord rich presence disabled: missing Voidscape Discord application id.");
				if (scheduled != null && !scheduled.isCancelled()) {
					scheduled.cancel(false);
				}
				return;
			}
			// discord natives not in use and have not started discord
			if (!startedDiscord && !getInUse()) {
				Runtime.getRuntime().addShutdownHook(new Thread(() -> {
					System.out.println("Closing Discord hook.");
					DiscordRPC.discordShutdown();
					setInUse(false);
				}));
				System.out.println("Starting discord rich presence.");
				discord = new DiscordEventHandlers.Builder().setReadyEventHandler((user) -> {
					System.out.println("Established discord rich presence.");
					// DiscordRPC.discordRunCallbacks();
				}).build();
				setInUse(true);
				DiscordRPC.discordInitialize(APPLICATION_ID, discord, false);
				DiscordRPC.discordRegister(APPLICATION_ID, "");
				scheduledExecutorService.scheduleAtFixedRate(discordTask, 0L, 5L, TimeUnit.SECONDS);
				startedDiscord = true;
				if (scheduled != null && !scheduled.isCancelled()) {
					System.out.println("Discord detection finished.");
					scheduled.cancel(false);
				}
			}
		}
	}

	/**
	 * Initialize an instance of Discord.
	 */
	public static void InitalizeDiscord() {
		// users may (likely) have multiple instances of the game open at once
		// so we have to run a timer task to only have one initialized at a time
		// if that instance later gets closed, one of the other instances
		// will then check to initialize
		// this is done per 15 secs
		scheduled = scheduledExecutorService.scheduleAtFixedRate(presenceTask, 0L, 15L, TimeUnit.SECONDS);
	}

	/**
	 * Task to update Discord status.
	 */
	static class DiscordUpdate implements Runnable {
		public void run() {
			DiscordRPC.discordRunCallbacks();
			DiscordRichPresence.Builder presence = new DiscordRichPresence.Builder(lastUpdate);
			presence.setBigImage(LARGE_IMAGE_KEY, LARGE_IMAGE_TEXT);
			DiscordRPC.discordUpdatePresence(presence.build());
			// This will be the default message if the player hasn't done anything
			// since the last update.
			setLastUpdate("Adventuring");
		}
	}

	/**
	 * Set status message on Discord.
	 * @param update Message content.
	 */
	public static void setLastUpdate(String update) {
		lastUpdate = update;
	}
}
