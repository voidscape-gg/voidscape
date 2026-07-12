package com.openrsc.server.content;

import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * World-owned, restart-safe access to the finite Christmas-cracker campaign pool.
 *
 * The durable global-cache row is authoritative. It is loaded only on first use because
 * World is constructed before the database connection is opened. Missing and zero are
 * valid inactive states; database failures remain visibly unknown rather than being
 * silently converted to an empty pool.
 */
public final class CrackerCampaignService {
	private static final Logger LOGGER = LogManager.getLogger(CrackerCampaignService.class);

	public static final String POOL_CACHE_KEY = "void_cracker_pool_remaining";
	public static final int UNKNOWN_REMAINING = -1;
	public static final int MAX_REMAINING = 1_000_000;
	private static final String SKILLING_CANDIDATE_TICK_ATTRIBUTE =
		"void_cracker_campaign_skilling_tick";
	private static final int DEFAULT_NPC_KILL_DENOMINATOR = 500;
	private static final int DEFAULT_SKILLING_DENOMINATOR = 1000;
	private static final CampaignStatePublisher NOOP_STATE_PUBLISHER = remaining -> { };

	private final CrackerCampaignTransactions.PoolPort poolPort;
	private final boolean enabled;
	private final int npcKillDenominator;
	private final int skillingDenominator;
	private final ChanceRoller chanceRoller;
	private final CampaignStatePublisher statePublisher;
	private int cachedRemaining = UNKNOWN_REMAINING;
	private boolean publishOnNextAuthoritativeLoad;

	public CrackerCampaignService(GameDatabase database, boolean enabled) {
		this(database, enabled, DEFAULT_NPC_KILL_DENOMINATOR,
			DEFAULT_SKILLING_DENOMINATOR);
	}

	public CrackerCampaignService(GameDatabase database, boolean enabled,
		int npcKillDenominator, int skillingDenominator) {
		this(database, enabled, npcKillDenominator, skillingDenominator,
			NOOP_STATE_PUBLISHER);
	}

	public CrackerCampaignService(GameDatabase database, boolean enabled,
		int npcKillDenominator, int skillingDenominator,
		CampaignStatePublisher statePublisher) {
		this(CrackerCampaignTransactions.databasePort(database), enabled,
			npcKillDenominator, skillingDenominator, CrackerCampaignService::randomWin,
			statePublisher);
	}

	CrackerCampaignService(CrackerCampaignTransactions.PoolPort poolPort, boolean enabled) {
		this(poolPort, enabled, DEFAULT_NPC_KILL_DENOMINATOR,
			DEFAULT_SKILLING_DENOMINATOR, CrackerCampaignService::randomWin,
			NOOP_STATE_PUBLISHER);
	}

	CrackerCampaignService(CrackerCampaignTransactions.PoolPort poolPort, boolean enabled,
		int npcKillDenominator, int skillingDenominator, ChanceRoller chanceRoller) {
		this(poolPort, enabled, npcKillDenominator, skillingDenominator, chanceRoller,
			NOOP_STATE_PUBLISHER);
	}

	CrackerCampaignService(CrackerCampaignTransactions.PoolPort poolPort, boolean enabled,
		int npcKillDenominator, int skillingDenominator, ChanceRoller chanceRoller,
		CampaignStatePublisher statePublisher) {
		if (poolPort == null) {
			throw new IllegalArgumentException("Cracker campaign pool port is required");
		}
		if (npcKillDenominator <= 0 || skillingDenominator <= 0) {
			throw new IllegalArgumentException("Cracker campaign denominators must be positive");
		}
		if (chanceRoller == null) {
			throw new IllegalArgumentException("Cracker campaign chance roller is required");
		}
		if (statePublisher == null) {
			throw new IllegalArgumentException("Cracker campaign state publisher is required");
		}
		this.poolPort = poolPort;
		this.enabled = enabled;
		this.npcKillDenominator = npcKillDenominator;
		this.skillingDenominator = skillingDenominator;
		this.chanceRoller = chanceRoller;
		this.statePublisher = statePublisher;
	}

	/**
	 * Considers one normally rewarded NPC death for the campaign. The cheap cached
	 * state and chance checks happen before the save reservation and durable award.
	 */
	public synchronized void onNpcKill(Player player) {
		if (player == null || isTutorialLocation(player)
			|| !getState().isActive() || !isNpcKillWinningRoll()) {
			return;
		}
		attemptAward(player, CrackerCampaignTransactions.TRIGGER_NPC_KILL);
	}

	/**
	 * Considers accepted non-combat skilling XP. A single action can award several
	 * XP packets, so the transient tick marker permits at most one campaign roll per
	 * player per server tick.
	 */
	public synchronized void onSkillingExperience(Player player, int skill, int awardedXp) {
		if (player == null || awardedXp <= 0 || isCombatSkill(skill)
			|| isTutorialLocation(player) || !getState().isActive()) {
			return;
		}

		final long currentTick = player.getWorld().getServer().getCurrentTick();
		final long previousTick = player.getAttribute(SKILLING_CANDIDATE_TICK_ATTRIBUTE,
			Long.MIN_VALUE);
		if (previousTick == currentTick) {
			return;
		}
		player.setAttribute(SKILLING_CANDIDATE_TICK_ATTRIBUTE, currentTick);

		if (isSkillingWinningRoll()) {
			attemptAward(player, CrackerCampaignTransactions.TRIGGER_SKILLING);
		}
	}

	boolean isNpcKillWinningRoll() {
		return chanceRoller.wins(npcKillDenominator);
	}

	boolean isSkillingWinningRoll() {
		return chanceRoller.wins(skillingDenominator);
	}

	private void attemptAward(Player player, String trigger) {
		final CrackerCampaignTransactions.AwardResult result =
			CrackerCampaignTransactions.award(player, trigger);
		publishAwardSettlement(result);

		switch (result.getStatus()) {
			case AWARDED:
				try {
					ActionSender.sendInventory(player);
				} catch (RuntimeException ex) {
					LOGGER.error("Unable to refresh committed cracker award inventory for player {}",
						player.getUsername(), ex);
				}
				try {
					player.playerServerMessage(MessageType.QUEST,
						"@gre@You found a Christmas cracker in the Cracker Hunt!");
				} catch (RuntimeException ex) {
					LOGGER.error("Unable to send committed cracker award message to player {}",
						player.getUsername(), ex);
				}
				publishAwardAnnouncements(player, result);
				break;
			case POOL_EMPTY:
				break;
			case INVENTORY_FULL:
				player.playerServerMessage(MessageType.QUEST,
					"@yel@Your inventory is full. Clear a space before hunting for crackers.");
				break;
			case FAILED:
			case UNCERTAIN:
				LOGGER.error("Cracker campaign award {} for player {} (trigger {})",
					result.getStatus(), player.getUsername(), trigger);
				break;
			case CLIENT_UNSUPPORTED:
			case BUSY:
			case INTERRUPTED:
				LOGGER.warn("Cracker campaign award {} for player {} (trigger {})",
					result.getStatus(), player.getUsername(), trigger);
				break;
			default:
				LOGGER.error("Unhandled cracker campaign award status {} for player {}",
					result.getStatus(), player.getUsername());
		}
	}

	private void publishAwardAnnouncements(Player player,
		CrackerCampaignTransactions.AwardResult result) {
		try {
			player.getWorld().getWorldAnnouncementService().announceCrackerDrop(player);
		} catch (RuntimeException ex) {
			LOGGER.error("Unable to announce committed cracker award for player {}",
				player.getUsername(), ex);
		}

		if (!result.isNewlyWonWorldFirst()) return;
		try {
			player.getWorld().getWorldAnnouncementService().announceFirstCampaignCracker(player);
		} catch (RuntimeException ex) {
			LOGGER.error("Unable to announce committed first campaign cracker for player {}",
				player.getUsername(), ex);
		}
	}

	/** Applies only durable settlement information to the cached state and HUD feed. */
	synchronized void publishAwardSettlement(CrackerCampaignTransactions.AwardResult result) {
		if (result == null) {
			cachedRemaining = UNKNOWN_REMAINING;
			publishOnNextAuthoritativeLoad = true;
			publishState(0);
			return;
		}
		switch (result.getStatus()) {
			case AWARDED:
				cachedRemaining = result.getRemaining();
				publishOnNextAuthoritativeLoad = false;
				publishState(result.getRemaining());
				return;
			case POOL_EMPTY:
				cachedRemaining = 0;
				publishOnNextAuthoritativeLoad = false;
				publishState(0);
				return;
			case FAILED:
			case UNCERTAIN:
				cachedRemaining = UNKNOWN_REMAINING;
				publishOnNextAuthoritativeLoad = true;
				publishState(0);
				return;
			case INVENTORY_FULL:
			case CLIENT_UNSUPPORTED:
			case BUSY:
			case INTERRUPTED:
				return;
			default:
				cachedRemaining = UNKNOWN_REMAINING;
				publishOnNextAuthoritativeLoad = true;
				publishState(0);
		}
	}

	/** Sends a fresh fail-closed snapshot after login settings on normal and resumed sessions. */
	public void sendStateSnapshot(Player player) {
		if (player == null) {
			return;
		}
		final State state = getState();
		final int remaining = state.isLoaded() ? visibleRemaining(state.getRemaining()) : 0;
		ActionSender.sendCrackerCampaignState(player, remaining);
	}

	private void publishState(int remaining) {
		final int visibleRemaining = visibleRemaining(remaining);
		try {
			statePublisher.publish(visibleRemaining);
		} catch (RuntimeException ex) {
			LOGGER.error("Unable to publish cracker campaign client state {}", visibleRemaining, ex);
		}
	}

	private int visibleRemaining(int remaining) {
		return enabled && remaining >= 0 && remaining <= MAX_REMAINING ? remaining : 0;
	}

	private static boolean randomWin(int denominator) {
		return DataConversions.random(1, denominator) == 1;
	}

	private static boolean isCombatSkill(int skill) {
		return skill == com.openrsc.server.constants.Skill.ATTACK.id()
			|| skill == com.openrsc.server.constants.Skill.DEFENSE.id()
			|| skill == com.openrsc.server.constants.Skill.STRENGTH.id()
			|| skill == com.openrsc.server.constants.Skill.HITS.id()
			|| skill == com.openrsc.server.constants.Skill.RANGED.id()
			|| skill == com.openrsc.server.constants.Skill.GOODMAGIC.id()
			|| skill == com.openrsc.server.constants.Skill.EVILMAGIC.id()
			|| skill == com.openrsc.server.constants.Skill.MAGIC.id();
	}

	private static boolean isTutorialLocation(Player player) {
		return player.getLocation().onTutorialIsland()
			|| player.getLocation().inVoidTutorialIsle();
	}

	/**
	 * Returns the process-local snapshot, lazily loading the durable row on first use.
	 * A failed load is retried by the next call and is reported with isLoaded() == false.
	 */
	public synchronized State getState() {
		if (cachedRemaining != UNKNOWN_REMAINING) {
			return loadedState(cachedRemaining);
		}

		try {
			final int remaining = CrackerCampaignTransactions.loadValidatedRemaining(poolPort);
			if (remaining > MAX_REMAINING) {
				throw new IllegalStateException("Cracker campaign pool exceeds maximum "
					+ MAX_REMAINING);
			}
			cachedRemaining = remaining;
			if (publishOnNextAuthoritativeLoad) {
				publishOnNextAuthoritativeLoad = false;
				publishState(remaining);
			}
			return loadedState(remaining);
		} catch (Exception ex) {
			LOGGER.error("Unable to load durable cracker campaign pool", ex);
			return new State(enabled, false, UNKNOWN_REMAINING);
		}
	}

	/**
	 * Replaces the durable remaining count. This is an idempotent operator mutation:
	 * zero disables the pool, while a positive value activates it when the feature flag
	 * is enabled. Negative values are rejected before opening a transaction.
	 */
	public synchronized SetResult setRemaining(int remaining) {
		if (remaining < 0 || remaining > MAX_REMAINING) {
			throw new IllegalArgumentException("Cracker campaign remaining count must be from 0 through "
				+ MAX_REMAINING);
		}

		final CrackerCampaignTransactions.MutationResult mutation =
			CrackerCampaignTransactions.setRemaining(poolPort, remaining);
		final AtomicTransactionOutcome outcome = mutation.getOutcome();

		if (outcome == AtomicTransactionOutcome.COMMITTED) {
			cachedRemaining = remaining;
			publishOnNextAuthoritativeLoad = false;
			publishState(remaining);
			final SetStatus status = mutation.getPreviousRemaining() == remaining
				? SetStatus.UNCHANGED : SetStatus.UPDATED;
			return new SetResult(status, enabled, mutation.getPreviousRemaining(), remaining);
		}

		if (outcome == AtomicTransactionOutcome.ROLLED_BACK) {
			if (mutation.isPreviousKnown()
				&& mutation.getPreviousRemaining() >= 0
				&& mutation.getPreviousRemaining() <= MAX_REMAINING) {
				cachedRemaining = mutation.getPreviousRemaining();
				publishOnNextAuthoritativeLoad = false;
				publishState(mutation.getPreviousRemaining());
			} else {
				cachedRemaining = UNKNOWN_REMAINING;
				publishOnNextAuthoritativeLoad = true;
				publishState(0);
			}
			return new SetResult(SetStatus.FAILED, enabled,
				mutation.getPreviousRemaining(), mutation.getRemaining());
		}

		// Do not publish an assumed count after an unresolved COMMIT. The next getState()
		// retries the authoritative read, while this result preserves the uncertainty for
		// the owner command/audit trail.
		cachedRemaining = UNKNOWN_REMAINING;
		publishOnNextAuthoritativeLoad = true;
		publishState(0);
		return new SetResult(SetStatus.UNCERTAIN, enabled,
			mutation.getPreviousRemaining(), UNKNOWN_REMAINING);
	}

	private State loadedState(int remaining) {
		return new State(enabled, true, remaining);
	}

	public enum SetStatus {
		UPDATED,
		UNCHANGED,
		FAILED,
		UNCERTAIN
	}

	@FunctionalInterface
	interface ChanceRoller {
		boolean wins(int denominator);
	}

	@FunctionalInterface
	public interface CampaignStatePublisher {
		void publish(int remaining);
	}

	public static final class State {
		private final boolean enabled;
		private final boolean loaded;
		private final int remaining;

		private State(boolean enabled, boolean loaded, int remaining) {
			this.enabled = enabled;
			this.loaded = loaded;
			this.remaining = remaining;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public boolean isLoaded() {
			return loaded;
		}

		public int getRemaining() {
			return remaining;
		}

		public boolean isActive() {
			return enabled && loaded && remaining > 0;
		}
	}

	public static final class SetResult {
		private final SetStatus status;
		private final boolean enabled;
		private final int previousRemaining;
		private final int remaining;

		private SetResult(SetStatus status, boolean enabled, int previousRemaining,
			int remaining) {
			this.status = status;
			this.enabled = enabled;
			this.previousRemaining = previousRemaining;
			this.remaining = remaining;
		}

		public SetStatus getStatus() {
			return status;
		}

		public boolean isEnabled() {
			return enabled;
		}

		public int getPreviousRemaining() {
			return previousRemaining;
		}

		public int getRemaining() {
			return remaining;
		}

		public boolean isSuccessful() {
			return status == SetStatus.UPDATED || status == SetStatus.UNCHANGED;
		}
	}
}
