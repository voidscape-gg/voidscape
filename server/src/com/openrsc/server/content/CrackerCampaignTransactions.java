package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.struct.PlayerInventory;
import com.openrsc.server.database.struct.WorldAchievementRecord;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Durable transaction coordinator for the finite Christmas-cracker campaign pool.
 *
 * The pool row is always read and written inside the same database transaction. The
 * package-private port keeps lost-COMMIT handling fault-testable and leaves a single
 * coordinator in which Slice 4B can atomically combine an inventory grant, provenance,
 * and the pool decrement. A gameplay award must never decrement through setRemaining;
 * all of those durable mutations must share one transaction.
 */
public final class CrackerCampaignTransactions {
	private static final Logger LOGGER = LogManager.getLogger(CrackerCampaignTransactions.class);
	private static final int CRACKER_ITEM_ID = ItemId.CHRISTMAS_CRACKER.id();
	private static final int MAX_TRIGGER_LENGTH = 32;
	private static final int MAX_SEASON_ID_LENGTH = 32;
	private static final int MAX_PLAYER_NAME_LENGTH = 12;
	private static final String FIRST_CRACKER_RECORD_KEY = "first:item:" + CRACKER_ITEM_ID;
	private static final String FIRST_CRACKER_RECORD_TYPE = "first_item";
	private static final String FIRST_CRACKER_SOURCE = "launch_cracker_campaign";

	public static final String TRIGGER_NPC_KILL = "npc_kill";
	public static final String TRIGGER_SKILLING = "skilling";

	private CrackerCampaignTransactions() {
	}

	public enum AwardStatus {
		AWARDED,
		POOL_EMPTY,
		INVENTORY_FULL,
		CLIENT_UNSUPPORTED,
		BUSY,
		INTERRUPTED,
		FAILED,
		UNCERTAIN
	}

	public static final class AwardResult {
		private final AwardStatus status;
		private final long itemId;
		private final int remaining;
		private final boolean newlyWonWorldFirst;

		private AwardResult(AwardStatus status, long itemId, int remaining,
			boolean newlyWonWorldFirst) {
			this.status = status;
			this.itemId = itemId;
			this.remaining = remaining;
			this.newlyWonWorldFirst = status == AwardStatus.AWARDED && newlyWonWorldFirst;
		}

		public AwardStatus getStatus() {
			return status;
		}

		/** Exact item-instance id, or {@link Item#ITEM_ID_UNASSIGNED} when none exists. */
		public long getItemId() {
			return itemId;
		}

		/** Durable pool count after settlement, or UNKNOWN_REMAINING when unresolved. */
		public int getRemaining() {
			return remaining;
		}

		public boolean isAwarded() {
			return status == AwardStatus.AWARDED;
		}

		/** True only when this confirmed award inserted the durable launch first. */
		public boolean isNewlyWonWorldFirst() {
			return newlyWonWorldFirst;
		}
	}

	/**
	 * Atomically materializes one unnoted Christmas cracker in the player's inventory,
	 * decrements the finite campaign pool, and records its origin. No client packet or
	 * gameplay message is emitted here; callers publish those only after AWARDED.
	 */
	public static AwardResult award(Player player, String trigger) {
		final String normalizedTrigger = normalizeTrigger(trigger);
		if (player == null || normalizedTrigger == null) {
			return awardResult(AwardStatus.FAILED, CrackerCampaignService.UNKNOWN_REMAINING);
		}
		return award(new PlayerAwardPort(player), normalizedTrigger);
	}

	static AwardResult award(AwardPort port, String trigger) {
		if (port == null) {
			throw new IllegalArgumentException("Cracker campaign award port is required");
		}
		final String normalizedTrigger = normalizeTrigger(trigger);
		if (normalizedTrigger == null) {
			return awardResult(AwardStatus.FAILED, CrackerCampaignService.UNKNOWN_REMAINING);
		}
		if (!port.reserveSave()) {
			return awardResult(AwardStatus.BUSY, CrackerCampaignService.UNKNOWN_REMAINING);
		}

		final AwardAttempt attempt = new AwardAttempt();
		boolean committed = false;
		boolean releaseReservation = true;
		try {
			if (!port.canComplete()) {
				return awardResult(AwardStatus.INTERRUPTED,
					CrackerCampaignService.UNKNOWN_REMAINING);
			}

			final AtomicTransactionOutcome outcome = port.atomically(() -> {
				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new AwardInterruptedException();
				}

				attempt.previousRemaining = loadValidatedRemaining(port);
				attempt.previousKnown = true;
				if (attempt.previousRemaining == 0) {
					attempt.status = AwardStatus.POOL_EMPTY;
					attempt.remaining = 0;
					return;
				}

				final AddedCracker added = port.addCracker();
				attempt.status = added.status;
				if (added.status != AwardStatus.AWARDED) {
					attempt.remaining = attempt.previousRemaining;
					return;
				}
				attempt.crackerAdded = true;
				attempt.itemId = added.itemId;
				attempt.remaining = attempt.previousRemaining - 1;

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new AwardInterruptedException();
				}

				port.saveInventory();
				port.saveRemaining(attempt.remaining);
				port.recordProvenance(normalizedTrigger, attempt.previousRemaining,
					attempt.remaining, attempt.itemId);
				claimFirstCampaignCracker(port, attempt, normalizedTrigger);

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new AwardInterruptedException();
				}
				attempt.status = AwardStatus.AWARDED;
			}, () -> verifyAwardSettlement(port, attempt));

			if (outcome != AtomicTransactionOutcome.COMMITTED) {
				if (outcome != AtomicTransactionOutcome.ROLLED_BACK) {
					releaseReservation = false;
					port.quarantineUnknownOutcome("launch cracker campaign award", attempt.itemId);
					return new AwardResult(AwardStatus.UNCERTAIN, attempt.itemId,
						CrackerCampaignService.UNKNOWN_REMAINING, false);
				}
				if (!attempt.crackerAdded && !attempt.interrupted
					&& attempt.status != AwardStatus.FAILED) {
					return awardResult(attempt.status, attempt.remaining);
				}
				return awardResult(attempt.interrupted ? AwardStatus.INTERRUPTED : AwardStatus.FAILED,
					attempt.previousKnown ? attempt.previousRemaining
						: CrackerCampaignService.UNKNOWN_REMAINING);
			}

			if (attempt.status != AwardStatus.AWARDED) {
				return awardResult(attempt.status, attempt.remaining);
			}

			committed = true;
			return new AwardResult(AwardStatus.AWARDED, attempt.itemId, attempt.remaining,
				attempt.insertedWorldFirstRecord != null);
		} finally {
			try {
				if (attempt.crackerAdded && !committed && releaseReservation) {
					port.rollbackAddedCracker();
				}
			} finally {
				if (releaseReservation) {
					port.releaseSaveReservation();
				}
			}
		}
	}

	private static AtomicTransactionOutcome verifyAwardSettlement(AwardPort port,
		AwardAttempt attempt) throws Exception {
		if (!attempt.previousKnown) {
			return AtomicTransactionOutcome.UNKNOWN;
		}
		final int durableRemaining = loadValidatedRemaining(port);
		if (!attempt.crackerAdded) {
			// A settled rejection mutates nothing, so either COMMIT or ROLLBACK is safe to
			// report as committed when the authoritative pool retained the observed value.
			return durableRemaining == attempt.previousRemaining
				? AtomicTransactionOutcome.COMMITTED : AtomicTransactionOutcome.UNKNOWN;
		}

		final boolean itemPersisted = port.persistedInventoryContains(attempt.itemId);
		WorldAchievementRecord durableWorldFirst = null;
		boolean worldFirstExactAfter = true;
		boolean worldFirstExactBefore = true;
		if (attempt.insertedWorldFirstRecord != null) {
			durableWorldFirst = port.loadWorldAchievementRecord(
				attempt.insertedWorldFirstRecord.seasonId,
				attempt.insertedWorldFirstRecord.recordKey);
			worldFirstExactAfter = sameRecord(attempt.insertedWorldFirstRecord,
				durableWorldFirst);
			worldFirstExactBefore = durableWorldFirst == null;
		}
		if (durableRemaining == attempt.remaining && itemPersisted
			&& worldFirstExactAfter) {
			return AtomicTransactionOutcome.COMMITTED;
		}
		if (durableRemaining == attempt.previousRemaining && !itemPersisted
			&& worldFirstExactBefore) {
			return AtomicTransactionOutcome.ROLLED_BACK;
		}
		return AtomicTransactionOutcome.UNKNOWN;
	}

	private static void claimFirstCampaignCracker(AwardPort port, AwardAttempt attempt,
		String trigger) throws Exception {
		final WorldFirstClaimant claimant = port.worldFirstClaimant();
		final WorldAchievementRecord record = firstCampaignCrackerRecord(
			claimant, attempt.itemId, trigger);
		if (record == null) {
			return;
		}

		final int inserted = port.insertWorldAchievementRecord(record);
		if (inserted == 1) {
			attempt.insertedWorldFirstRecord = record;
		} else if (inserted == 0) {
			final WorldAchievementRecord existing = port.loadWorldAchievementRecord(
				record.seasonId, record.recordKey);
			if (!isCanonicalExistingFirstCrackerRecord(existing, record.seasonId)) {
				throw new IllegalStateException(
					"World achievement insert returned zero without a canonical first-item record");
			}
		} else {
			throw new IllegalStateException(
				"World achievement insert returned " + inserted);
		}
	}

	private static WorldAchievementRecord firstCampaignCrackerRecord(
		WorldFirstClaimant claimant, long itemId, String trigger) {
		if (claimant == null || !claimant.enabled || !claimant.defaultUser) {
			return null;
		}
		final String seasonId = normalizeSeasonId(claimant.seasonId);
		final String playerName = normalizePlayerName(claimant.playerName);
		if (seasonId == null || claimant.playerId <= 0 || playerName == null
			|| claimant.awardedAtMs <= 0L || itemId <= 0L) {
			return null;
		}

		final WorldAchievementRecord record = new WorldAchievementRecord();
		record.seasonId = seasonId;
		record.recordKey = FIRST_CRACKER_RECORD_KEY;
		record.recordType = FIRST_CRACKER_RECORD_TYPE;
		record.playerId = claimant.playerId;
		record.playerName = playerName;
		record.subjectId = CRACKER_ITEM_ID;
		record.value = 1L;
		record.source = FIRST_CRACKER_SOURCE;
		record.sourceEventKey = Long.toString(itemId);
		record.claimedAtMs = claimant.awardedAtMs;
		record.detail = "item=" + CRACKER_ITEM_ID + " trigger=" + trigger;
		return record;
	}

	private static boolean isCanonicalExistingFirstCrackerRecord(
		WorldAchievementRecord record, String seasonId) {
		if (record == null
			|| !same(seasonId, record.seasonId)
			|| !FIRST_CRACKER_RECORD_KEY.equals(record.recordKey)
			|| !FIRST_CRACKER_RECORD_TYPE.equals(record.recordType)
			|| record.playerId <= 0
			|| normalizePlayerName(record.playerName) == null
			|| record.subjectId != CRACKER_ITEM_ID
			|| record.value != 1L
			|| !FIRST_CRACKER_SOURCE.equals(record.source)
			|| record.claimedAtMs <= 0L
			|| record.detail == null || record.detail.length() > 255) {
			return false;
		}
		try {
			final long sourceItemId = Long.parseLong(record.sourceEventKey);
			return sourceItemId > 0L
				&& Long.toString(sourceItemId).equals(record.sourceEventKey);
		} catch (RuntimeException ex) {
			return false;
		}
	}

	private static boolean sameRecord(WorldAchievementRecord expected,
		WorldAchievementRecord actual) {
		return expected != null && actual != null
			&& same(expected.seasonId, actual.seasonId)
			&& same(expected.recordKey, actual.recordKey)
			&& same(expected.recordType, actual.recordType)
			&& expected.playerId == actual.playerId
			&& same(expected.playerName, actual.playerName)
			&& expected.subjectId == actual.subjectId
			&& expected.value == actual.value
			&& same(expected.source, actual.source)
			&& same(expected.sourceEventKey, actual.sourceEventKey)
			&& expected.claimedAtMs == actual.claimedAtMs
			&& same(expected.detail, actual.detail);
	}

	private static boolean same(Object first, Object second) {
		return first == null ? second == null : first.equals(second);
	}

	private static String normalizeTrigger(String trigger) {
		if (trigger == null) {
			return null;
		}
		final String normalized = trigger.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.length() > MAX_TRIGGER_LENGTH) {
			return null;
		}
		for (int index = 0; index < normalized.length(); index++) {
			final char character = normalized.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < '0' || character > '9')
				&& character != '_' && character != '-') {
				return null;
			}
		}
		return normalized;
	}

	private static String normalizeSeasonId(String seasonId) {
		if (seasonId == null) {
			return null;
		}
		final String normalized = seasonId.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.length() > MAX_SEASON_ID_LENGTH) {
			return null;
		}
		for (int index = 0; index < normalized.length(); index++) {
			final char character = normalized.charAt(index);
			if ((character < 'a' || character > 'z')
				&& (character < '0' || character > '9')
				&& character != '_' && character != '-') {
				return null;
			}
		}
		return normalized;
	}

	private static String normalizePlayerName(String playerName) {
		if (playerName == null) {
			return null;
		}
		final String normalized = playerName.trim();
		return normalized.isEmpty() || normalized.length() > MAX_PLAYER_NAME_LENGTH
			? null : normalized;
	}

	private static AwardResult awardResult(AwardStatus status, int remaining) {
		return new AwardResult(status, Item.ITEM_ID_UNASSIGNED, remaining, false);
	}

	static MutationResult setRemaining(GameDatabase database, int intendedRemaining) {
		return setRemaining(databasePort(database), intendedRemaining);
	}

	static PoolPort databasePort(GameDatabase database) {
		if (database == null) {
			throw new IllegalArgumentException("Cracker campaign database is required");
		}
		return new DatabasePoolPort(database);
	}

	static MutationResult setRemaining(PoolPort port, int intendedRemaining) {
		if (port == null) {
			throw new IllegalArgumentException("Cracker campaign transaction port is required");
		}
		if (intendedRemaining < 0) {
			throw new IllegalArgumentException("Cracker campaign remaining count cannot be negative");
		}

		final MutationAttempt attempt = new MutationAttempt();
		final AtomicTransactionOutcome outcome = port.atomically(() -> {
			attempt.previousRemaining = loadValidatedRemaining(port);
			attempt.previousKnown = true;
			if (attempt.previousRemaining != intendedRemaining) {
				port.saveRemaining(intendedRemaining);
			}
		}, () -> verifySettlement(port, attempt, intendedRemaining));

		if (outcome == AtomicTransactionOutcome.COMMITTED) {
			return new MutationResult(outcome, attempt.previousKnown,
				attempt.previousRemaining, intendedRemaining);
		}
		if (outcome == AtomicTransactionOutcome.ROLLED_BACK) {
			return new MutationResult(outcome, attempt.previousKnown,
				attempt.previousRemaining,
				attempt.previousKnown ? attempt.previousRemaining : CrackerCampaignService.UNKNOWN_REMAINING);
		}
		return new MutationResult(outcome, attempt.previousKnown,
			attempt.previousRemaining, CrackerCampaignService.UNKNOWN_REMAINING);
	}

	private static AtomicTransactionOutcome verifySettlement(PoolPort port,
		MutationAttempt attempt, int intendedRemaining) throws Exception {
		final int durableRemaining = loadValidatedRemaining(port);
		if (durableRemaining == intendedRemaining) {
			return AtomicTransactionOutcome.COMMITTED;
		}
		if (attempt.previousKnown && durableRemaining == attempt.previousRemaining) {
			return AtomicTransactionOutcome.ROLLED_BACK;
		}
		return AtomicTransactionOutcome.UNKNOWN;
	}

	static int loadValidatedRemaining(PoolPort port) throws Exception {
		final Integer stored = port.loadRemaining();
		if (stored == null) {
			return 0;
		}
		if (stored < 0) {
			throw new IllegalStateException("Cracker campaign pool contains a negative value");
		}
		return stored;
	}

	@FunctionalInterface
	interface TransactionBody {
		void run() throws Exception;
	}

	@FunctionalInterface
	interface TransactionVerifier {
		AtomicTransactionOutcome verify() throws Exception;
	}

	interface PoolPort {
		AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier);
		Integer loadRemaining() throws Exception;
		void saveRemaining(int remaining) throws Exception;
	}

	interface AwardPort extends PoolPort {
		boolean reserveSave();
		boolean canComplete();
		AddedCracker addCracker();
		void saveInventory() throws Exception;
		void recordProvenance(String trigger, int before, int after, long itemId) throws Exception;
		default WorldFirstClaimant worldFirstClaimant() {
			return WorldFirstClaimant.disabled();
		}
		default int insertWorldAchievementRecord(WorldAchievementRecord record) throws Exception {
			throw new UnsupportedOperationException("World achievements are not enabled");
		}
		default WorldAchievementRecord loadWorldAchievementRecord(String seasonId,
			String recordKey) throws Exception {
			throw new UnsupportedOperationException("World achievements are not enabled");
		}
		boolean persistedInventoryContains(long itemId) throws Exception;
		void rollbackAddedCracker();
		void quarantineUnknownOutcome(String operation, long itemId);
		void releaseSaveReservation();
	}

	static final class WorldFirstClaimant {
		final boolean enabled;
		final boolean defaultUser;
		final String seasonId;
		final int playerId;
		final String playerName;
		final long awardedAtMs;

		WorldFirstClaimant(boolean enabled, boolean defaultUser, String seasonId, int playerId,
			String playerName, long awardedAtMs) {
			this.enabled = enabled;
			this.defaultUser = defaultUser;
			this.seasonId = seasonId;
			this.playerId = playerId;
			this.playerName = playerName;
			this.awardedAtMs = awardedAtMs;
		}

		private static WorldFirstClaimant disabled() {
			return new WorldFirstClaimant(false, false, null, 0, null, 0L);
		}
	}

	static final class AddedCracker {
		final AwardStatus status;
		final long itemId;

		private AddedCracker(AwardStatus status, long itemId) {
			this.status = status;
			this.itemId = itemId;
		}

		static AddedCracker awarded(long itemId) {
			return new AddedCracker(AwardStatus.AWARDED, itemId);
		}

		static AddedCracker failed(AwardStatus status) {
			if (status == AwardStatus.AWARDED) {
				throw new IllegalArgumentException("A failed cracker mutation cannot be AWARDED");
			}
			return new AddedCracker(status, Item.ITEM_ID_UNASSIGNED);
		}
	}

	static final class MutationResult {
		private final AtomicTransactionOutcome outcome;
		private final boolean previousKnown;
		private final int previousRemaining;
		private final int remaining;

		private MutationResult(AtomicTransactionOutcome outcome, boolean previousKnown,
			int previousRemaining, int remaining) {
			this.outcome = outcome;
			this.previousKnown = previousKnown;
			this.previousRemaining = previousKnown
				? previousRemaining : CrackerCampaignService.UNKNOWN_REMAINING;
			this.remaining = remaining;
		}

		AtomicTransactionOutcome getOutcome() {
			return outcome;
		}

		boolean isPreviousKnown() {
			return previousKnown;
		}

		int getPreviousRemaining() {
			return previousRemaining;
		}

		int getRemaining() {
			return remaining;
		}
	}

	private static final class MutationAttempt {
		private boolean previousKnown;
		private int previousRemaining;
	}

	private static final class AwardAttempt {
		private AwardStatus status = AwardStatus.FAILED;
		private boolean previousKnown;
		private boolean crackerAdded;
		private boolean interrupted;
		private int previousRemaining;
		private int remaining = CrackerCampaignService.UNKNOWN_REMAINING;
		private long itemId = Item.ITEM_ID_UNASSIGNED;
		private WorldAchievementRecord insertedWorldFirstRecord;
	}

	private static final class AwardInterruptedException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static final class DatabasePoolPort implements PoolPort {
		private final GameDatabase database;

		private DatabasePoolPort(GameDatabase database) {
			this.database = database;
		}

		@Override
		public AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			return database.atomicallySettled(body::run, verifier::verify);
		}

		@Override
		public Integer loadRemaining() throws Exception {
			return database.queryLoadGlobalCacheInt(CrackerCampaignService.POOL_CACHE_KEY);
		}

		@Override
		public void saveRemaining(int remaining) throws Exception {
			database.querySaveGlobalCacheInt(CrackerCampaignService.POOL_CACHE_KEY, remaining);
		}
	}

	private static final class PlayerAwardPort implements AwardPort {
		private final Player player;
		private final GameDatabase database;
		private final Inventory inventory;
		private final List<Item> items;
		private final UUID lifecycleId;
		private Item addedCracker;
		private PlayerInventory[] exactInventory;

		private PlayerAwardPort(Player player) {
			this.player = player;
			this.database = player.getWorld().getServer().getDatabase();
			this.inventory = player.getCarriedItems().getInventory();
			this.items = inventory.getItems();
			this.lifecycleId = player.getSaveLifecycleId();
		}

		@Override
		public boolean reserveSave() {
			return player.tryReserveSave();
		}

		@Override
		public boolean canComplete() {
			return player.isCurrentSaveLifecycle(lifecycleId)
				&& !player.isLoggingOut()
				&& !player.isUnregistering()
				&& !player.killed;
		}

		@Override
		public AtomicTransactionOutcome atomically(TransactionBody body,
			TransactionVerifier verifier) {
			// Never hold the inventory monitor while acquiring the database lock. The
			// save reservation fences async saves, gameplay mutation stays on the tick
			// path, and addCracker captures the exact list under its brief local lock.
			return database.atomicallySettled(body::run, verifier::verify);
		}

		@Override
		public Integer loadRemaining() throws Exception {
			return database.queryLoadGlobalCacheInt(CrackerCampaignService.POOL_CACHE_KEY);
		}

		@Override
		public void saveRemaining(int remaining) throws Exception {
			database.querySaveGlobalCacheInt(CrackerCampaignService.POOL_CACHE_KEY, remaining);
		}

		@Override
		public AddedCracker addCracker() {
			synchronized (items) {
				// Inventory.add drops unsupported/full items. Prechecking both conditions
				// under its own monitor makes that spill path unreachable for this award.
				if (player.getClientLimitations().maxItemId < CRACKER_ITEM_ID) {
					return AddedCracker.failed(AwardStatus.CLIENT_UNSUPPORTED);
				}
				if (items.size() >= Inventory.MAX_SIZE) {
					return AddedCracker.failed(AwardStatus.INVENTORY_FULL);
				}

				final int insertedIndex = items.size();
				if (!inventory.add(new Item(CRACKER_ITEM_ID, 1, false), false)) {
					return AddedCracker.failed(AwardStatus.FAILED);
				}
				final Item tentative = inventory.get(insertedIndex);
				if (items.size() != insertedIndex + 1
					|| tentative == null
					|| tentative.getCatalogId() != CRACKER_ITEM_ID
					|| tentative.getAmount() != 1
					|| tentative.getNoted()
					|| tentative.getItemId() == Item.ITEM_ID_UNASSIGNED) {
					while (items.size() > insertedIndex) {
						items.remove(items.size() - 1);
					}
					return AddedCracker.failed(AwardStatus.FAILED);
				}
				addedCracker = tentative;
				exactInventory = snapshotInventory(items);
				return AddedCracker.awarded(tentative.getItemId());
			}
		}

		@Override
		public void saveInventory() throws Exception {
			if (exactInventory == null) {
				throw new IllegalStateException("Cracker award has no exact inventory snapshot");
			}
			database.savePlayerInventory(player.getDatabaseID(), exactInventory);
		}

		@Override
		public void recordProvenance(String trigger, int before, int after, long itemId)
			throws Exception {
			final String extra = "trigger=" + trigger + " before=" + before + " after=" + after;
			database.addItemProvenanceEvent(player, player, "item_origin",
				"launch_cracker_campaign", "player_inventory", "campaign_drop",
				CRACKER_ITEM_ID, 1, false, itemId, player.getX(), player.getY(), extra);
		}

		@Override
		public WorldFirstClaimant worldFirstClaimant() {
			return new WorldFirstClaimant(player.getConfig().WANT_WORLD_ACHIEVEMENTS,
				player.isDefaultUser(),
				player.getConfig().WORLD_ACHIEVEMENT_SEASON_ID,
				player.getDatabaseID(), player.getUsername(), System.currentTimeMillis());
		}

		@Override
		public int insertWorldAchievementRecord(WorldAchievementRecord record)
			throws Exception {
			return database.queryInsertWorldAchievementRecord(record);
		}

		@Override
		public WorldAchievementRecord loadWorldAchievementRecord(String seasonId,
			String recordKey) throws Exception {
			return database.queryLoadWorldAchievementRecord(seasonId, recordKey);
		}

		@Override
		public boolean persistedInventoryContains(long itemId) throws Exception {
			for (PlayerInventory persisted :
				database.queryLoadPlayerInvItems(player.getDatabaseID())) {
				if (persisted.itemId == itemId
					&& persisted.item != null
					&& persisted.item.getCatalogId() == CRACKER_ITEM_ID
					&& persisted.item.getAmount() == 1
					&& !persisted.item.getNoted()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public void rollbackAddedCracker() {
			if (addedCracker == null) {
				return;
			}
			synchronized (items) {
				for (int index = 0; index < items.size(); index++) {
					final Item candidate = items.get(index);
					if (candidate == addedCracker
						|| candidate.getItemId() == addedCracker.getItemId()) {
						items.remove(index);
						break;
					}
				}
			}
			addedCracker = null;
			exactInventory = null;
		}

		private static PlayerInventory[] snapshotInventory(List<Item> items) {
			final PlayerInventory[] snapshot = new PlayerInventory[items.size()];
			for (int index = 0; index < items.size(); index++) {
				final Item item = items.get(index);
				final PlayerInventory persisted = new PlayerInventory();
				persisted.itemId = item.getItemId();
				persisted.wielded = item.isWielded();
				persisted.slot = index;
				persisted.item = item;
				persisted.amount = item.getAmount();
				persisted.noted = item.getNoted();
				persisted.catalogID = item.getCatalogId();
				persisted.durability = item.getItemStatus().getDurability();
				persisted.killLog = item.getItemStatus().getKillLog();
				snapshot[index] = persisted;
			}
			return snapshot;
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			LOGGER.fatal("Unknown durable outcome for {} by {} (playerID={}, itemID={}); "
				+ "fencing the session without another save until operator restart/reconciliation",
				operation, player.getUsername(), player.getDatabaseID(), itemId);
			player.setAttribute("cracker_campaign_transaction_uncertain", true);
			if (!player.beginLogoutPreparation()) {
				player.setBusy(true);
				player.setLoggingOut(true);
				player.resetPath();
			}
			player.close();
		}

		@Override
		public void releaseSaveReservation() {
			player.releaseSaveReservation();
		}
	}
}
