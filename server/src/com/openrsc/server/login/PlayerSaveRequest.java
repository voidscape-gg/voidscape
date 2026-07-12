package com.openrsc.server.login;

import com.openrsc.server.Server;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.util.PlayerEventStopper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Used to verify save players on the Login thread
 */
public class PlayerSaveRequest extends LoginExecutorProcess {
	/**
	 * The asynchronous logger.
	 */
	private static final Logger LOGGER = LogManager.getLogger(PlayerSaveRequest.class);

	private final Server server;
	private final Player player;
	private final boolean logout;
	private final UUID lifecycleId;
	private final long persistentGeneration;
	private final CompletableFuture<Boolean> completion = new CompletableFuture<>();

	public PlayerSaveRequest(final Server server, final Player player, boolean logout) {
		this.server = server;
		this.player = player;
		this.logout = logout;
		this.lifecycleId = player.getSaveLifecycleId();
		this.persistentGeneration = player.getPersistentSaveGeneration();
	}

	public final Player getPlayer() {
		return player;
	}

	public final Server getServer() {
		return server;
	}

	public CompletableFuture<Boolean> getCompletionFuture() {
		return completion;
	}

	public void rejectEnqueue() {
		completion.complete(false);
	}

	public void rejectBeforeProcessing() {
		try {
			player.completeSaveTicket(logout, false);
		} catch (final RuntimeException ex) {
			LOGGER.error("Unable to balance rejected save ticket for " + player.getUsername(), ex);
		} finally {
			completion.complete(false);
		}
	}

	protected void processInternal() {
//		LOGGER.info("Saved player " + player.getUsername() + "");
		boolean transactionSucceeded = false;
		try {
			if (player.getAttribute("dummyplayer", false)) {
				transactionSucceeded = !this.logout || logoutSaveSuccess(persistentGeneration);
				return;
			}
			if (!isCapturedLifecycleCurrent()) {
				return;
			}
			final boolean databaseCommitted = getServer().getPlayerService().savePlayer(player);
			final boolean stillCurrent = isCapturedLifecycleCurrent();
			final boolean generationCurrent = !this.logout
				|| player.isLogoutSaveGenerationCurrent(lifecycleId, persistentGeneration);
			transactionSucceeded = databaseCommitted && stillCurrent && generationCurrent;
			if (mayFinalizeLogout(databaseCommitted, stillCurrent, generationCurrent) && this.logout) {
				transactionSucceeded = logoutSaveSuccess(persistentGeneration);
			}
		} catch (final Exception ex) {
			LOGGER.error("Error saving the player, phantom player may have extra login count on their IP address now...! Have a look at this Exception:", ex);
		} finally {
			if (getPlayer() != null) {
				try {
					getPlayer().completeSaveTicket(this.logout, transactionSucceeded);
				} catch (final RuntimeException ex) {
					transactionSucceeded = false;
					LOGGER.error("Unable to balance completed save ticket for " + getPlayer().getUsername(), ex);
				}
			}
			completion.complete(transactionSucceeded);
		}
	}

	private boolean isCapturedLifecycleCurrent() {
		return mayWrite(
			lifecycleId.equals(player.getSaveLifecycleId()),
			player.loggedIn(),
			player.isRemoved(),
			player.getWorld().isCurrentPlayer(player));
	}

	static boolean mayWrite(boolean sameLifecycle, boolean loggedIn, boolean removed,
		boolean currentWorldInstance) {
		return sameLifecycle && loggedIn && !removed && currentWorldInstance;
	}

	static boolean mayFinalizeLogout(boolean databaseCommitted, boolean stillCurrent,
		boolean generationCurrent) {
		return databaseCommitted && stillCurrent && generationCurrent;
	}

	public boolean logoutSaveSuccess(long expectedPersistentGeneration) {
		final java.util.Collection<GameTickEvent> eventsToStop;
		synchronized (getPlayer()) {
			if (!isCapturedLifecycleCurrent()
				|| !getPlayer().isLogoutSaveGenerationCurrent(lifecycleId, expectedPersistentGeneration)) {
				return false;
			}
			try {
				getServer().getPacketFilter().removeLoggedInPlayer(
					getPlayer().getCurrentIP(), getPlayer().getUsernameHash());
			} catch (final RuntimeException ex) {
				LOGGER.error("Unable to clear packet-filter logout state for " + getPlayer().getUsername(), ex);
			}

			try {
				if (!getPlayer().isRemoved()) {
					getPlayer().remove(); // remove player from region
				}
			} catch (final RuntimeException ex) {
				LOGGER.error("Unable to remove player from its final region: " + getPlayer().getUsername(), ex);
				return false;
			}
			if (!getServer().getWorld().removePlayerIfCurrent(getPlayer())) {
				return false;
			}
			eventsToStop = getServer().getGameEventHandler().getPlayerEvents(getPlayer());
			getPlayer().setLoggedIn(false);
			getPlayer().completeLogoutSaveWorker();
		}
		final int stopFailures = PlayerEventStopper.stopOutsidePlayerLock(
			getPlayer(), eventsToStop, GameTickEvent::stop);
		if (stopFailures > 0) {
			LOGGER.error("Unable to stop {} finalized player event(s) for {}",
				stopFailures, getPlayer().getUsername());
		}

		LOGGER.info("Removed player " + getPlayer().getUsername());

		try {
			updateFriendsLists();
		} catch (final RuntimeException ex) {
			LOGGER.error("Unable to finish social logout cleanup for " + getPlayer().getUsername(), ex);
		}
		return true;
	}

	private void updateFriendsLists() {
		final World world = getPlayer().getWorld();
		for (Player other : world.getPlayers()) {
			other.getSocial().alertOfLogout(getPlayer());
		}

		world.getClanManager().checkAndUnattachFromClan(getPlayer());
		world.getPartyManager().checkAndUnattachFromParty(getPlayer());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PlayerSaveRequest request = (PlayerSaveRequest) o;
		return logout == request.logout && lifecycleId.equals(request.lifecycleId);
	}

	@Override
	public int hashCode() {
		int result = lifecycleId.hashCode();
		return 31 * result + (logout ? 1 : 0);
	}
}
