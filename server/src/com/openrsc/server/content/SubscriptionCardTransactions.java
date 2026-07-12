package com.openrsc.server.content;

import com.openrsc.server.database.AtomicTransactionOutcome;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.struct.PlayerCache;
import com.openrsc.server.database.struct.PlayerInventory;
import com.openrsc.server.database.struct.PortalCommerceEntitlement;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/**
 * Durable, single-transaction mutations for subscription-card grants and redemption.
 *
 * Player inventory is mutated with client updates suppressed while a save reservation
 * fences asynchronous player saves. The inventory row set, entitlement/subscription
 * ledger, and provenance row are then committed together through GameDatabase.atomically.
 * A failed transaction restores the exact in-memory item and slot before releasing the
 * reservation.
 */
public final class SubscriptionCardTransactions {
	private static final Logger LOGGER = LogManager.getLogger(SubscriptionCardTransactions.class);
	private static final int PLAYER_CACHE_INT_TYPE = 0;
	private static final int MAX_CACHE_KEY_LENGTH = 32;

	private SubscriptionCardTransactions() {
	}

	public enum MarkerScope {
		GLOBAL,
		PLAYER
	}

	/**
	 * Describes a one-time card entitlement. PLAYER scope is intentionally supported
	 * now so launch campaigns can use an immutable character-local marker later without
	 * inventing another grant path.
	 */
	public static final class EntitlementMarker {
		private final MarkerScope scope;
		private final String key;
		private final int availableState;
		private final int claimedState;

		private EntitlementMarker(MarkerScope scope, String key, int availableState, int claimedState) {
			if (scope == null) {
				throw new IllegalArgumentException("Subscription-card marker scope is required");
			}
			if (key == null || key.isEmpty() || key.length() > MAX_CACHE_KEY_LENGTH) {
				throw new IllegalArgumentException("Subscription-card marker key must be 1-32 characters");
			}
			if (availableState == claimedState) {
				throw new IllegalArgumentException("Available and claimed marker states must differ");
			}
			this.scope = scope;
			this.key = key;
			this.availableState = availableState;
			this.claimedState = claimedState;
		}

		public static EntitlementMarker global(String key, int availableState, int claimedState) {
			return new EntitlementMarker(MarkerScope.GLOBAL, key, availableState, claimedState);
		}

		public static EntitlementMarker player(String key, int availableState, int claimedState) {
			return new EntitlementMarker(MarkerScope.PLAYER, key, availableState, claimedState);
		}

		public MarkerScope getScope() {
			return scope;
		}

		public String getKey() {
			return key;
		}
	}

	public enum GrantStatus {
		GRANTED,
		NOT_AVAILABLE,
		INVENTORY_FULL,
		CLIENT_UNSUPPORTED,
		BUSY,
		INTERRUPTED,
		UNCERTAIN,
		FAILED
	}

	public static final class GrantResult {
		private final GrantStatus status;
		private final long itemId;

		private GrantResult(GrantStatus status, long itemId) {
			this.status = status;
			this.itemId = itemId;
		}

		public GrantStatus getStatus() {
			return status;
		}

		public long getItemId() {
			return itemId;
		}

		public boolean isGranted() {
			return status == GrantStatus.GRANTED;
		}
	}

	public enum RedeemStatus {
		REDEEMED,
		CARD_NOT_FOUND,
		BUSY,
		INTERRUPTED,
		UNCERTAIN,
		FAILED
	}

	public static final class RedeemResult {
		private final RedeemStatus status;
		private final boolean accountWide;
		private final boolean wasActive;
		private final long expiresAt;

		private RedeemResult(RedeemStatus status, boolean accountWide, boolean wasActive, long expiresAt) {
			this.status = status;
			this.accountWide = accountWide;
			this.wasActive = wasActive;
			this.expiresAt = expiresAt;
		}

		public RedeemStatus getStatus() {
			return status;
		}

		public boolean isAccountWide() {
			return accountWide;
		}

		public boolean wasActive() {
			return wasActive;
		}

		public long getExpiresAt() {
			return expiresAt;
		}

		public boolean isRedeemed() {
			return status == RedeemStatus.REDEEMED;
		}
	}

	public static GrantResult grantReservedCard(Player player, EntitlementMarker marker,
		String source, String extra) {
		if (player == null || marker == null) {
			return grantResult(GrantStatus.FAILED);
		}
		return grantReservedCard(new PlayerGrantPort(player), marker, source, extra);
	}

	public static GrantResult claimPurchasedCard(Player player) {
		if (player == null || VoidSubscription.getAccountId(player) <= 0) {
			return grantResult(GrantStatus.NOT_AVAILABLE);
		}
		return claimPurchasedCard(new PlayerPaidGrantPort(player));
	}

	public static RedeemResult redeem(Player player, Item clickedCard) {
		if (player == null || clickedCard == null
			|| clickedCard.getCatalogId() != VoidSubscription.CARD_ITEM_ID) {
			return redeemResult(RedeemStatus.CARD_NOT_FOUND, false);
		}
		final String cacheKey = VoidSubscription.subscriptionCacheKey(player);
		if (cacheKey.isEmpty()) {
			return redeemResult(RedeemStatus.FAILED, false);
		}
		return redeem(new PlayerRedeemPort(player, clickedCard, cacheKey));
	}

	static GrantResult grantReservedCard(GrantPort port, EntitlementMarker marker,
		String source, String extra) {
		if (!port.reserveSave()) {
			return grantResult(GrantStatus.BUSY);
		}

		final GrantAttempt attempt = new GrantAttempt();
		boolean committed = false;
		boolean releaseReservation = true;
		try {
			if (!port.canComplete()) {
				return grantResult(GrantStatus.INTERRUPTED);
			}
			final AtomicTransactionOutcome outcome = port.atomically(() -> {
				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				final Integer markerState = port.loadMarker(marker);
				if (markerState == null || markerState != marker.availableState) {
					attempt.status = GrantStatus.NOT_AVAILABLE;
					return;
				}

				final AddedCard added = port.addCard();
				attempt.status = added.status;
				if (added.status != GrantStatus.GRANTED) {
					return;
				}
				attempt.cardAdded = true;
				attempt.itemId = added.itemId;

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				port.saveInventory();
				port.saveMarker(marker);
				port.recordGrantProvenance(source, extra, attempt.itemId);

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}
			}, () -> attempt.cardAdded
				? port.verifyGrantSettlement(marker, attempt.itemId)
				: AtomicTransactionOutcome.ROLLED_BACK);

			if (outcome != AtomicTransactionOutcome.COMMITTED) {
				if (outcome != AtomicTransactionOutcome.ROLLED_BACK && attempt.cardAdded) {
					releaseReservation = false;
					port.quarantineUnknownOutcome("subscription-card grant", attempt.itemId);
					return grantResult(GrantStatus.UNCERTAIN);
				}
				if (outcome == AtomicTransactionOutcome.ROLLED_BACK
					&& !attempt.cardAdded && !attempt.interrupted
					&& attempt.status != GrantStatus.FAILED) {
					return grantResult(attempt.status);
				}
				return grantResult(attempt.interrupted ? GrantStatus.INTERRUPTED : GrantStatus.FAILED);
			}
			if (attempt.status != GrantStatus.GRANTED) {
				return grantResult(attempt.status);
			}

			committed = true;
			port.markerCommitted(marker);
			return new GrantResult(GrantStatus.GRANTED, attempt.itemId);
		} finally {
			try {
				if (attempt.cardAdded && !committed && releaseReservation) {
					port.rollbackAddedCard();
				}
			} finally {
				if (releaseReservation) {
					port.releaseSaveReservation();
				}
			}
		}
	}

	static RedeemResult redeem(RedeemPort port) {
		if (!port.reserveSave()) {
			return redeemResult(RedeemStatus.BUSY, port.isAccountWide());
		}

		final RedeemAttempt attempt = new RedeemAttempt();
		boolean committed = false;
		boolean releaseReservation = true;
		try {
			if (!port.canComplete()) {
				return redeemResult(RedeemStatus.INTERRUPTED, port.isAccountWide());
			}
			final AtomicTransactionOutcome outcome = port.atomically(() -> {
				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				if (!port.removeExactCard()) {
					attempt.status = RedeemStatus.CARD_NOT_FOUND;
					return;
				}
				attempt.cardRemoved = true;

				final long now = port.currentTimeMillis();
				final Long storedExpiry = port.loadSubscriptionExpiry();
				final long currentExpiry = storedExpiry == null ? 0L : storedExpiry;
				attempt.previousExpiry = currentExpiry;
				attempt.wasActive = currentExpiry > now;
				final long base = Math.max(now, currentExpiry);
				attempt.expiresAt = saturatingAdd(base, VoidSubscription.DURATION_MILLIS);

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				port.saveInventory();
				port.saveSubscriptionExpiry(attempt.expiresAt);
				port.recordRedeemProvenance(port.isAccountWide(), attempt.wasActive, attempt.expiresAt);

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}
				attempt.status = RedeemStatus.REDEEMED;
			}, () -> attempt.cardRemoved
				? port.verifyRedeemSettlement(attempt.previousExpiry, attempt.expiresAt)
				: AtomicTransactionOutcome.ROLLED_BACK);

			if (outcome != AtomicTransactionOutcome.COMMITTED) {
				if (outcome != AtomicTransactionOutcome.ROLLED_BACK && attempt.cardRemoved) {
					releaseReservation = false;
					port.quarantineUnknownOutcome("subscription-card redemption", port.removedCardItemId());
					return redeemResult(RedeemStatus.UNCERTAIN, port.isAccountWide());
				}
				if (outcome == AtomicTransactionOutcome.ROLLED_BACK
					&& !attempt.cardRemoved && !attempt.interrupted
					&& attempt.status != RedeemStatus.FAILED) {
					return redeemResult(attempt.status, port.isAccountWide());
				}
				return redeemResult(attempt.interrupted ? RedeemStatus.INTERRUPTED : RedeemStatus.FAILED,
					port.isAccountWide());
			}
			if (attempt.status != RedeemStatus.REDEEMED) {
				return redeemResult(attempt.status, port.isAccountWide());
			}

			committed = true;
			port.subscriptionCommitted(attempt.expiresAt);
			return new RedeemResult(RedeemStatus.REDEEMED, port.isAccountWide(),
				attempt.wasActive, attempt.expiresAt);
		} finally {
			try {
				if (attempt.cardRemoved && !committed && releaseReservation) {
					port.rollbackRemovedCard();
				}
			} finally {
				if (releaseReservation) {
					port.releaseSaveReservation();
				}
			}
		}
	}

	static GrantResult claimPurchasedCard(PaidGrantPort port) {
		if (!port.reserveSave()) {
			return grantResult(GrantStatus.BUSY);
		}

		final PaidGrantAttempt attempt = new PaidGrantAttempt();
		boolean committed = false;
		boolean releaseReservation = true;
		try {
			if (!port.canComplete()) {
				return grantResult(GrantStatus.INTERRUPTED);
			}
			final AtomicTransactionOutcome outcome = port.atomically(() -> {
				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				attempt.entitlement = port.loadOldestPendingEntitlement();
				if (attempt.entitlement == null) {
					attempt.status = GrantStatus.NOT_AVAILABLE;
					return;
				}

				final AddedCard added = port.addCard();
				attempt.status = added.status;
				if (added.status != GrantStatus.GRANTED) {
					return;
				}
				attempt.cardAdded = true;
				attempt.itemId = added.itemId;
				attempt.claimedAtMs = port.currentTimeMillis();

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}

				if (!port.claimEntitlement(attempt.entitlement, attempt.claimedAtMs)) {
					throw new EntitlementClaimLostException();
				}
				port.saveInventory();
				port.recordPaidGrantProvenance(attempt.entitlement, attempt.itemId);

				if (!port.canComplete()) {
					attempt.interrupted = true;
					throw new TransactionInterruptedException();
				}
				attempt.status = GrantStatus.GRANTED;
			}, () -> attempt.cardAdded
				? port.verifyPaidGrantSettlement(attempt.entitlement, attempt.itemId)
				: AtomicTransactionOutcome.ROLLED_BACK);

			if (outcome != AtomicTransactionOutcome.COMMITTED) {
				if (outcome != AtomicTransactionOutcome.ROLLED_BACK && attempt.cardAdded) {
					releaseReservation = false;
					port.quarantineUnknownOutcome("paid subscription-card pickup", attempt.itemId);
					return grantResult(GrantStatus.UNCERTAIN);
				}
				if (outcome == AtomicTransactionOutcome.ROLLED_BACK
					&& !attempt.cardAdded && !attempt.interrupted
					&& attempt.status != GrantStatus.FAILED) {
					return grantResult(attempt.status);
				}
				return grantResult(attempt.interrupted ? GrantStatus.INTERRUPTED : GrantStatus.FAILED);
			}
			if (attempt.status != GrantStatus.GRANTED) {
				return grantResult(attempt.status);
			}

			committed = true;
			return new GrantResult(GrantStatus.GRANTED, attempt.itemId);
		} finally {
			try {
				if (attempt.cardAdded && !committed && releaseReservation) {
					port.rollbackAddedCard();
				}
			} finally {
				if (releaseReservation) {
					port.releaseSaveReservation();
				}
			}
		}
	}

	static long saturatingAdd(long base, long addition) {
		return Long.MAX_VALUE - base < addition ? Long.MAX_VALUE : base + addition;
	}

	static String paidTransactionReference(String transactionId) {
		if (transactionId == null || transactionId.isEmpty()) return "missing";
		try {
			final byte[] digest = MessageDigest.getInstance("SHA-256")
				.digest(transactionId.getBytes(StandardCharsets.UTF_8));
			final StringBuilder reference = new StringBuilder(16);
			for (int i = 0; i < 8; i++) {
				reference.append(String.format("%02x", digest[i] & 0xff));
			}
			return reference.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable", ex);
		}
	}

	private static GrantResult grantResult(GrantStatus status) {
		return new GrantResult(status, Item.ITEM_ID_UNASSIGNED);
	}

	private static RedeemResult redeemResult(RedeemStatus status, boolean accountWide) {
		return new RedeemResult(status, accountWide, false, 0L);
	}

	@FunctionalInterface
	interface TransactionBody {
		void run() throws Exception;
	}

	interface TransactionPort {
		boolean reserveSave();
		boolean canComplete();
		AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier);
		void saveInventory() throws Exception;
		void quarantineUnknownOutcome(String operation, long itemId);
		void releaseSaveReservation();
	}

	@FunctionalInterface
	interface TransactionVerifier {
		AtomicTransactionOutcome verify() throws Exception;
	}

	interface GrantPort extends TransactionPort {
		Integer loadMarker(EntitlementMarker marker) throws Exception;
		AddedCard addCard();
		void saveMarker(EntitlementMarker marker) throws Exception;
		void recordGrantProvenance(String source, String extra, long itemId) throws Exception;
		AtomicTransactionOutcome verifyGrantSettlement(EntitlementMarker marker, long itemId) throws Exception;
		void markerCommitted(EntitlementMarker marker);
		void rollbackAddedCard();
	}

	interface PaidGrantPort extends TransactionPort {
		PortalCommerceEntitlement loadOldestPendingEntitlement() throws Exception;
		AddedCard addCard();
		long currentTimeMillis();
		boolean claimEntitlement(PortalCommerceEntitlement entitlement, long claimedAtMs) throws Exception;
		void recordPaidGrantProvenance(PortalCommerceEntitlement entitlement, long itemId) throws Exception;
		AtomicTransactionOutcome verifyPaidGrantSettlement(PortalCommerceEntitlement entitlement,
			long itemId) throws Exception;
		void rollbackAddedCard();
	}

	interface RedeemPort extends TransactionPort {
		boolean isAccountWide();
		boolean removeExactCard();
		long currentTimeMillis();
		Long loadSubscriptionExpiry() throws Exception;
		void saveSubscriptionExpiry(long expiresAt) throws Exception;
		void recordRedeemProvenance(boolean accountWide, boolean wasActive, long expiresAt) throws Exception;
		AtomicTransactionOutcome verifyRedeemSettlement(long previousExpiry, long intendedExpiry) throws Exception;
		long removedCardItemId();
		void subscriptionCommitted(long expiresAt);
		void rollbackRemovedCard();
	}

	static final class AddedCard {
		final GrantStatus status;
		final long itemId;

		private AddedCard(GrantStatus status, long itemId) {
			this.status = status;
			this.itemId = itemId;
		}

		static AddedCard granted(long itemId) {
			return new AddedCard(GrantStatus.GRANTED, itemId);
		}

		static AddedCard failed(GrantStatus status) {
			if (status == GrantStatus.GRANTED) {
				throw new IllegalArgumentException("A failed card mutation cannot be GRANTED");
			}
			return new AddedCard(status, Item.ITEM_ID_UNASSIGNED);
		}
	}

	private static final class GrantAttempt {
		private GrantStatus status = GrantStatus.FAILED;
		private boolean cardAdded;
		private boolean interrupted;
		private long itemId = Item.ITEM_ID_UNASSIGNED;
	}

	private static final class PaidGrantAttempt {
		private GrantStatus status = GrantStatus.FAILED;
		private PortalCommerceEntitlement entitlement;
		private boolean cardAdded;
		private boolean interrupted;
		private long itemId = Item.ITEM_ID_UNASSIGNED;
		private long claimedAtMs;
	}

	private static final class RedeemAttempt {
		private RedeemStatus status = RedeemStatus.FAILED;
		private boolean cardRemoved;
		private boolean interrupted;
		private boolean wasActive;
		private long previousExpiry;
		private long expiresAt;
	}

	private static final class TransactionInterruptedException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private static final class EntitlementClaimLostException extends Exception {
		private static final long serialVersionUID = 1L;
	}

	private abstract static class PlayerTransactionPort implements TransactionPort {
		final Player player;
		final GameDatabase database;
		final Inventory inventory;
		final UUID lifecycleId;
		private Item addedSubscriptionCard;

		private PlayerTransactionPort(Player player) {
			this.player = player;
			this.database = player.getWorld().getServer().getDatabase();
			this.inventory = player.getCarriedItems().getInventory();
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
		public AtomicTransactionOutcome atomically(TransactionBody body, TransactionVerifier verifier) {
			return database.atomicallySettled(body::run, verifier::verify);
		}

		@Override
		public void saveInventory() throws Exception {
			database.savePlayerInventory(player);
		}

		@Override
		public void quarantineUnknownOutcome(String operation, long itemId) {
			LOGGER.fatal("Unknown durable outcome for {} by {} (playerID={}, itemID={}); "
				+ "fencing the session without another save until operator restart/reconciliation",
				operation, player.getUsername(), player.getDatabaseID(), itemId);
			player.setAttribute("subscription_transaction_uncertain", true);
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

		final AddedCard addSubscriptionCard() {
			final List<Item> items = inventory.getItems();
			synchronized (items) {
				// Inventory.add normally drops unsupported/full items. Both conditions are
				// checked while holding the same inventory monitor so a paid or free card
				// can never be materialized as a ground item.
				if (player.getClientLimitations().maxItemId < VoidSubscription.CARD_ITEM_ID) {
					return AddedCard.failed(GrantStatus.CLIENT_UNSUPPORTED);
				}
				if (items.size() >= Inventory.MAX_SIZE) {
					return AddedCard.failed(GrantStatus.INVENTORY_FULL);
				}
				final int insertedIndex = items.size();
				if (!inventory.add(new Item(VoidSubscription.CARD_ITEM_ID), false)) {
					return AddedCard.failed(GrantStatus.FAILED);
				}
				final Item tentative = inventory.get(insertedIndex);
				if (items.size() != insertedIndex + 1
					|| tentative == null
					|| tentative.getCatalogId() != VoidSubscription.CARD_ITEM_ID
					|| tentative.getAmount() != 1
					|| tentative.getNoted()
					|| tentative.getItemId() == Item.ITEM_ID_UNASSIGNED) {
					while (items.size() > insertedIndex) {
						items.remove(items.size() - 1);
					}
					return AddedCard.failed(GrantStatus.FAILED);
				}
				addedSubscriptionCard = tentative;
				return AddedCard.granted(tentative.getItemId());
			}
		}

		final void recordCardOrigin(String source, String extra, long itemId) throws Exception {
			database.addItemProvenanceEvent(player, player, "item_origin", source,
				"player_inventory", "subscription_card_grant", VoidSubscription.CARD_ITEM_ID,
				1, false, itemId, player.getX(), player.getY(), extra);
		}

		final boolean persistedInventoryContainsSubscriptionCard(long itemId) throws Exception {
			for (PlayerInventory item : database.queryLoadPlayerInvItems(player.getDatabaseID())) {
				if (item.itemId == itemId
					&& item.item != null
					&& item.item.getCatalogId() == VoidSubscription.CARD_ITEM_ID
					&& item.item.getAmount() == 1
					&& !item.item.getNoted()) {
					return true;
				}
			}
			return false;
		}

		final void rollbackAddedSubscriptionCard() {
			if (addedSubscriptionCard != null) {
				inventory.remove(addedSubscriptionCard, false);
				addedSubscriptionCard = null;
			}
		}
	}

	private static final class PlayerGrantPort extends PlayerTransactionPort implements GrantPort {
		private PlayerGrantPort(Player player) {
			super(player);
		}

		@Override
		public Integer loadMarker(EntitlementMarker marker) throws Exception {
			if (marker.scope == MarkerScope.GLOBAL) {
				return database.queryLoadGlobalCacheInt(marker.key);
			}
			for (PlayerCache cache : database.queryLoadPlayerCache(player)) {
				if (!marker.key.equals(cache.key)) {
					continue;
				}
				if (cache.type != PLAYER_CACHE_INT_TYPE) {
					throw new IllegalStateException("Subscription-card player marker is not an integer: " + marker.key);
				}
				return Integer.parseInt(cache.value);
			}
			return null;
		}

		@Override
		public AddedCard addCard() {
			return addSubscriptionCard();
		}

		@Override
		public void saveMarker(EntitlementMarker marker) throws Exception {
			if (marker.scope == MarkerScope.GLOBAL) {
				database.querySaveGlobalCacheInt(marker.key, marker.claimedState);
				return;
			}
			database.querySavePlayerCacheValue(player.getDatabaseID(), PLAYER_CACHE_INT_TYPE,
				marker.key, Integer.toString(marker.claimedState));
		}

		@Override
		public void recordGrantProvenance(String source, String extra, long itemId) throws Exception {
			recordCardOrigin(source, extra, itemId);
		}

		@Override
		public AtomicTransactionOutcome verifyGrantSettlement(EntitlementMarker marker, long itemId)
			throws Exception {
			final Integer markerState = loadMarker(marker);
			final boolean cardPersisted = persistedInventoryContainsSubscriptionCard(itemId);
			if (markerState != null && markerState == marker.claimedState && cardPersisted) {
				return AtomicTransactionOutcome.COMMITTED;
			}
			if (markerState != null && markerState == marker.availableState && !cardPersisted) {
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public void markerCommitted(EntitlementMarker marker) {
			if (marker.scope == MarkerScope.PLAYER) {
				player.getCache().set(marker.key, marker.claimedState);
			}
		}

		@Override
		public void rollbackAddedCard() {
			rollbackAddedSubscriptionCard();
		}
	}

	private static final class PlayerPaidGrantPort extends PlayerTransactionPort implements PaidGrantPort {
		private final int accountId;

		private PlayerPaidGrantPort(Player player) {
			super(player);
			accountId = VoidSubscription.getAccountId(player);
		}

		@Override
		public PortalCommerceEntitlement loadOldestPendingEntitlement() throws Exception {
			final PortalCommerceEntitlement entitlement =
				database.queryOldestPendingPortalCommerceEntitlement(accountId);
			if (entitlement != null && (entitlement.accountId != accountId
				|| !"pending".equals(entitlement.state))) {
				throw new IllegalStateException("Paid entitlement query returned a mismatched row");
			}
			return entitlement;
		}

		@Override
		public AddedCard addCard() {
			return addSubscriptionCard();
		}

		@Override
		public long currentTimeMillis() {
			return System.currentTimeMillis();
		}

		@Override
		public boolean claimEntitlement(PortalCommerceEntitlement entitlement, long claimedAtMs)
			throws Exception {
			return database.queryClaimPortalCommerceEntitlement(entitlement.id, accountId,
				player.getDatabaseID(), VoidSubscription.CARD_ITEM_ID, claimedAtMs) == 1;
		}

		@Override
		public void recordPaidGrantProvenance(PortalCommerceEntitlement entitlement, long itemId)
			throws Exception {
			final String extra = "grant=paid_card entitlement_id=" + entitlement.id
				+ " provider=" + entitlement.provider
				+ " tx_ref=" + paidTransactionReference(entitlement.transactionId);
			recordCardOrigin("subscription_tebex", extra, itemId);
		}

		@Override
		public AtomicTransactionOutcome verifyPaidGrantSettlement(
			PortalCommerceEntitlement attempted, long itemId) throws Exception {
			if (attempted == null) return AtomicTransactionOutcome.UNKNOWN;
			final PortalCommerceEntitlement durable =
				database.queryPortalCommerceEntitlement(attempted.id);
			final boolean cardPersisted = persistedInventoryContainsSubscriptionCard(itemId);
			if (durable != null
				&& durable.accountId == accountId
				&& "claimed".equals(durable.state)
				&& durable.claimedPlayerId == player.getDatabaseID()
				&& durable.claimedItemId == VoidSubscription.CARD_ITEM_ID
				&& cardPersisted) {
				return AtomicTransactionOutcome.COMMITTED;
			}
			if (durable != null
				&& durable.accountId == accountId
				&& "pending".equals(durable.state)
				&& durable.claimedPlayerId == 0
				&& durable.claimedItemId == 0L
				&& durable.claimedAtMs == 0L
				&& !cardPersisted) {
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
			return AtomicTransactionOutcome.UNKNOWN;
		}

		@Override
		public void rollbackAddedCard() {
			rollbackAddedSubscriptionCard();
		}
	}

	private static final class PlayerRedeemPort extends PlayerTransactionPort implements RedeemPort {
		private final Item clickedCard;
		private final String subscriptionCacheKey;
		private final boolean accountWide;
		private Item removedCard;
		private int removedIndex = -1;

		private PlayerRedeemPort(Player player, Item clickedCard, String subscriptionCacheKey) {
			super(player);
			this.clickedCard = clickedCard;
			this.subscriptionCacheKey = subscriptionCacheKey;
			this.accountWide = VoidSubscription.hasLinkedAccount(player);
		}

		@Override
		public boolean isAccountWide() {
			return accountWide;
		}

		@Override
		public boolean removeExactCard() {
			final List<Item> items = inventory.getItems();
			synchronized (items) {
				for (int index = 0; index < items.size(); index++) {
					final Item candidate = items.get(index);
					if (candidate.getItemId() != clickedCard.getItemId()
						|| candidate.getCatalogId() != VoidSubscription.CARD_ITEM_ID
						|| candidate.getAmount() != 1
						|| candidate.getNoted()) {
						continue;
					}
					removedCard = candidate;
					removedIndex = index;
					return inventory.remove(candidate, false) == candidate.getItemId();
				}
			}
			return false;
		}

		@Override
		public long currentTimeMillis() {
			return System.currentTimeMillis();
		}

		@Override
		public Long loadSubscriptionExpiry() throws Exception {
			return database.queryLoadGlobalCacheLong(subscriptionCacheKey);
		}

		@Override
		public void saveSubscriptionExpiry(long expiresAt) throws Exception {
			database.querySaveGlobalCacheLong(subscriptionCacheKey, expiresAt);
		}

		@Override
		public void recordRedeemProvenance(boolean accountWide, boolean wasActive, long expiresAt)
			throws Exception {
			final String extra = "scope=" + (accountWide ? "account" : "character")
				+ " was_active=" + wasActive
				+ " expires_at=" + expiresAt;
			database.addItemProvenanceEvent(player, player, "item_transfer", "player_inventory",
				"subscription_ledger", "subscription_redeem", VoidSubscription.CARD_ITEM_ID,
				1, false, removedCard.getItemId(), player.getX(), player.getY(), extra);
		}

		@Override
		public AtomicTransactionOutcome verifyRedeemSettlement(long previousExpiry, long intendedExpiry)
			throws Exception {
			final boolean cardPersisted = persistedInventoryContains(removedCard.getItemId());
			final Long stored = loadSubscriptionExpiry();
			final long durableExpiry = stored == null ? 0L : stored;
			if (!cardPersisted && durableExpiry == intendedExpiry) {
				return AtomicTransactionOutcome.COMMITTED;
			}
			if (cardPersisted && durableExpiry == previousExpiry) {
				return AtomicTransactionOutcome.ROLLED_BACK;
			}
			return AtomicTransactionOutcome.UNKNOWN;
		}

		private boolean persistedInventoryContains(long itemId) throws Exception {
			for (PlayerInventory item : database.queryLoadPlayerInvItems(player.getDatabaseID())) {
				if (item.itemId == itemId
					&& item.item != null
					&& item.item.getCatalogId() == VoidSubscription.CARD_ITEM_ID
					&& item.item.getAmount() == 1
					&& !item.item.getNoted()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public long removedCardItemId() {
			return removedCard == null ? Item.ITEM_ID_UNASSIGNED : removedCard.getItemId();
		}

		@Override
		public void subscriptionCommitted(long expiresAt) {
			VoidSubscription.cacheCommittedSubscriptionExpiresAt(player, expiresAt);
		}

		@Override
		public void rollbackRemovedCard() {
			if (removedCard == null) {
				return;
			}
			final List<Item> items = inventory.getItems();
			synchronized (items) {
				for (Item item : items) {
					if (item.getItemId() == removedCard.getItemId()) {
						return;
					}
				}
				items.add(Math.min(Math.max(removedIndex, 0), items.size()), removedCard);
			}
		}
	}
}
