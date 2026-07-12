package orsc;

import android.media.AudioAttributes;
import android.media.MediaPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

public class soundPlayer {
	private static final int MAX_ACTIVE_PLAYERS = 8;
	private static final String ANDROID_SMOKE_AUDIO_FLAG = "android-smoke-audio.flag";
	private static final Object LOCK = new Object();
	private static final Set<MediaPlayer> ACTIVE_PLAYERS = new LinkedHashSet<>();
	private static boolean backgroundSuspended;
	private static boolean afkSuspended;

    public static void playSoundFile(String key) {
		MediaPlayer player = null;
        try {
			if (orsc.mudclient.optionSoundDisabled) {
				return;
			}
			File sound = orsc.mudclient.soundCache.get(key + ".wav");
			if (sound == null || !sound.isFile()) {
				return;
			}

			player = new MediaPlayer();
			MediaPlayer ownedPlayer = player;
			player.setOnCompletionListener(completed -> releasePlayer(completed, "completed"));
			player.setOnErrorListener((failed, what, extra) -> {
				releasePlayer(failed, "error");
				return true;
			});
			player.setOnPreparedListener(prepared -> startPreparedPlayer(prepared));
			AudioAttributes audioAttrib = new AudioAttributes.Builder()
				.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
				.setUsage(AudioAttributes.USAGE_GAME)
				.build();
			player.setAudioAttributes(audioAttrib);
			player.setDataSource(sound.getPath());
			player.setLooping(false);

			MediaPlayer evicted = null;
			synchronized (LOCK) {
				if (isSuspendedLocked()) {
					releaseDirect(ownedPlayer);
					return;
				}
				if (ACTIVE_PLAYERS.size() >= MAX_ACTIVE_PLAYERS) {
					evicted = ACTIVE_PLAYERS.iterator().next();
					ACTIVE_PLAYERS.remove(evicted);
				}
				ACTIVE_PLAYERS.add(ownedPlayer);
				logSmokeLocked("queued");
				player.prepareAsync();
			}
			if (evicted != null) {
				releaseDirect(evicted);
			}
		} catch (Exception error) {
			if (player != null) {
				releasePlayer(player, "setup-error");
			}
			error.printStackTrace();
        }
    }

	private static void startPreparedPlayer(MediaPlayer player) {
		boolean release = false;
		synchronized (LOCK) {
			if (isSuspendedLocked() || !ACTIVE_PLAYERS.contains(player)) {
				release = true;
			} else {
				try {
					player.start();
					logSmokeLocked("started");
					return;
				} catch (RuntimeException error) {
					error.printStackTrace();
					release = true;
				}
			}
		}
		if (release) {
			releasePlayer(player, isSuspended() ? "suspended" : "start-error");
		}
	}

	private static void releasePlayer(MediaPlayer player, String event) {
		synchronized (LOCK) {
			ACTIVE_PLAYERS.remove(player);
		}
		releaseDirect(player);
		synchronized (LOCK) {
			logSmokeLocked(event);
		}
	}

	private static void releaseDirect(MediaPlayer player) {
		if (player == null) {
			return;
		}
		try {
			player.reset();
		} catch (RuntimeException ignored) {
		}
		try {
			player.release();
		} catch (RuntimeException ignored) {
		}
	}

	public static void stopAll() {
		ArrayList<MediaPlayer> players;
		synchronized (LOCK) {
			players = new ArrayList<>(ACTIVE_PLAYERS);
			ACTIVE_PLAYERS.clear();
		}
		for (MediaPlayer player : players) {
			releaseDirect(player);
		}
		synchronized (LOCK) {
			logSmokeLocked("stop-all");
		}
	}

	public static void suspendForBackground() {
		synchronized (LOCK) {
			backgroundSuspended = true;
		}
		stopAll();
	}

	public static void resumeForeground() {
		synchronized (LOCK) {
			backgroundSuspended = false;
			logSmokeLocked(isSuspendedLocked() ? "resume-afk-suspended" : "resume");
		}
	}

	public static void setAfkSuspended(boolean suspended) {
		boolean stopPlayers = false;
		synchronized (LOCK) {
			if (afkSuspended == suspended) {
				return;
			}
			afkSuspended = suspended;
			stopPlayers = suspended;
			if (!stopPlayers) {
				logSmokeLocked(isSuspendedLocked() ? "afk-resume-background-suspended" : "afk-resume");
			}
		}
		if (stopPlayers) {
			stopAll();
		}
	}

	private static boolean isSuspended() {
		synchronized (LOCK) {
			return isSuspendedLocked();
		}
	}

	private static boolean isSuspendedLocked() {
		return backgroundSuspended || afkSuspended;
	}

	private static void logSmokeLocked(String event) {
		if (Config.F_ANDROID_SMOKE_DIR == null || Config.F_ANDROID_SMOKE_DIR.isEmpty()) {
			return;
		}
		if (!new File(Config.F_ANDROID_SMOKE_DIR, ANDROID_SMOKE_AUDIO_FLAG).isFile()) {
			return;
		}
		System.out.println("ANDROID_SMOKE_AUDIO event=" + event + " active=" + ACTIVE_PLAYERS.size());
	}
}
