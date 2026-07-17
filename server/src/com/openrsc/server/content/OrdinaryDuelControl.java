package com.openrsc.server.content;

import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/** Persistent operational switch for ordinary player-versus-player duels. */
public final class OrdinaryDuelControl {
	private static final Logger LOGGER = LogManager.getLogger(OrdinaryDuelControl.class);
	private static final String CACHE_KEY = "void_dueling_enabled";

	private final World world;
	private volatile boolean enabled = true;

	public OrdinaryDuelControl(final World world) {
		this.world = world;
	}

	public void load() {
		try {
			final Integer stored = world.getServer().getDatabase().queryLoadGlobalCacheInt(CACHE_KEY);
			if (stored == null) {
				enabled = true;
			} else if (stored == 0 || stored == 1) {
				enabled = stored == 1;
			} else {
				enabled = false;
				LOGGER.warn("Invalid ordinary-dueling state {}; failing closed", stored);
			}
		} catch (final GameDatabaseException failure) {
			enabled = false;
			LOGGER.error("Could not load ordinary-dueling state; failing closed", failure);
		}
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Persist first; the runtime state changes only after the transaction commits. */
	public synchronized boolean setEnabled(final boolean nextEnabled) {
		if (enabled == nextEnabled) {
			return true;
		}
		final boolean committed = world.getServer().getDatabase().atomically(() ->
			world.getServer().getDatabase().querySaveGlobalCacheInt(CACHE_KEY, nextEnabled ? 1 : 0));
		if (!committed) {
			return false;
		}
		enabled = nextEnabled;
		return true;
	}

	/** Cancel only request/setup states. Combat already in progress is deliberately untouched. */
	public int cancelPendingDuels() {
		int cancelled = 0;
		for (final Player player : new ArrayList<Player>(world.getPlayers())) {
			final Player opponent = player.getDuel().getDuelRecipient();
			if (opponent == null || player.getDuel().hasDuelCombatStarted()
				|| opponent.getDuel().hasDuelCombatStarted()) {
				continue;
			}
			player.message("Dueling has been disabled; your pending duel was cancelled.");
			if (opponent != player) {
				opponent.message("Dueling has been disabled; your pending duel was cancelled.");
			}
			player.getDuel().resetAll();
			cancelled++;
		}
		return cancelled;
	}
}
