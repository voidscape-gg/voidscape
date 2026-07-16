package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidSubscription;
import com.openrsc.server.database.GameDatabase;
import com.openrsc.server.database.GameDatabaseException;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Objects;

import static com.openrsc.server.plugins.Functions.inputBox;

public final class VoidSubscriptionVendor implements TalkNpcTrigger, OpNpcTrigger {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final int NPC_ID = NpcId.VOID_SUBSCRIPTION_VENDOR.id();
	private static final String SUBSCRIBE_COMMAND = "Subscribe";
	private static final Object CLAIM_LOCK = new Object();

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVendor(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVendor(npc)) return;
		checkReservedCard(player, npc);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isVendor(npc)
			&& command != null
			&& command.equalsIgnoreCase(SUBSCRIBE_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) return;
		checkReservedCard(player, npc);
	}

	private void checkReservedCard(Player player, Npc npc) {
		synchronized (CLAIM_LOCK) {
			if (claimReservedCard(player)) {
				return;
			}
		}
		player.message("@mag@The vendor checks the void ledger.");
		player.message("@whi@No subscription card is ready for this character.");
		promptSignupCode(player, npc);
	}

	/**
	 * The code popup can sit open for up to 60 seconds while the plugin blocks in
	 * inputBox. Release the vendor before prompting so other players can talk to
	 * him meanwhile — redemption correctness comes from CLAIM_LOCK plus the
	 * single-use ledger state, not from NPC exclusivity.
	 */
	private void releaseVendor(Player player, Npc npc) {
		if (npc == null) return;
		npc.setBusy(false);
		if (npc.getInteractingPlayer() == player) {
			npc.setNpcInteraction(null);
			npc.setInteractingPlayer(null);
		}
	}

	private boolean claimReservedCard(Player player) {
		final boolean linkedAccount = VoidSubscription.hasLinkedAccount(player);
		final String starterKey = linkedAccount ? VoidSubscription.starterCardCacheKey(player) : "";
		final Integer starterState;
		final Integer launchState = launchCardState(player);
		try {
			starterState = starterKey.isEmpty() ? null : player.getWorld().getServer().getDatabase()
				.queryLoadGlobalCacheInt(starterKey);
		} catch (RuntimeException ex) {
			player.message("@or1@The vendor can't check the void ledger right now. Try again shortly.");
			return true;
		}
		if (!VoidSubscription.isCardClaimState(starterState)
			|| !VoidSubscription.isCardClaimState(launchState)) {
			player.message("@or1@The vendor found an invalid card record. Ask staff to check the void ledger.");
			return true;
		}

		final VoidSubscription.CardClaimPlan plan = VoidSubscription
			.planReservedCardClaim(starterState, launchState);
		if (!plan.hasWork()) {
			return false;
		}
		if (plan.grantsCard() && !canDeliverSubscriptionCard(player)) {
			return true;
		}

		final ClaimMutation mutation = new ClaimMutation();
		mutation.grantCard = plan.grantsCard();
		mutation.launchExpected = launchState;
		mutation.checkLaunch = true;
		mutation.claimLaunch = plan.claimsLaunch();
		if (!starterKey.isEmpty()) {
			mutation.starterKey = starterKey;
			mutation.starterExpected = starterState;
			mutation.checkStarter = true;
			mutation.claimStarter = plan.claimsStarter();
		}

		final ClaimCommit commit = persistClaim(player, mutation);
		if (!commit.committed) {
			player.message("@or1@The vendor could not update the void ledger. Try again shortly.");
			return true;
		}
		if (!plan.grantsCard()) {
			log(player, " reconciled overlapping founder and launch card routes without granting another card.");
			return false;
		}

		final boolean founderGrant = plan.claimsStarter();
		final String source = "subscription_vendor";
		final String extra = founderGrant ? "grant=starter_card" : "grant=launch_24h_card";
		recordSubscriptionCardGrant(player, source, commit.card.getItemId(), extra);
		ActionSender.sendInventory(player);
		player.message(founderGrant
			? "@mag@The vendor hands you your founder subscription card."
			: "@mag@The vendor hands you your launch subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
		log(player, founderGrant
			? " claimed a founder subscription card from the Lumbridge vendor."
			: " claimed a launch subscription card from the Lumbridge vendor.");
		return true;
	}

	private void promptSignupCode(Player player, Npc npc) {
		if (!ActionSender.supportsInputBox(player)) {
			player.message("@whi@Signed up on the website? Update your client to enter your code here.");
			return;
		}
		releaseVendor(player, npc);
		String entered = inputBox(player, "Got a signup code from the website?%Enter it and press enter");
		if (entered == null || entered.trim().isEmpty()) {
			return;
		}
		String normalized = VoidSubscription.normalizeSignupCode(entered);
		String cacheKey = VoidSubscription.signupCodeCacheKey(normalized);
		if (cacheKey.isEmpty()) {
			player.message("@or1@That doesn't look like a signup code. Check it and try again.");
			return;
		}
		synchronized (CLAIM_LOCK) {
			redeemSignupCode(player, normalized, cacheKey);
		}
	}

	private void redeemSignupCode(Player player, String code, String cacheKey) {
		final String baseTagKey = VoidSubscription.baseCodeTagCacheKey(code);
		final String baseAccountKey = VoidSubscription.baseCodeAccountCacheKey(code);
		final Integer state;
		final Integer baseTag;
		final Integer baseAccountId;
		try {
			state = player.getWorld().getServer().getDatabase().queryLoadGlobalCacheInt(cacheKey);
			baseTag = player.getWorld().getServer().getDatabase().queryLoadGlobalCacheInt(baseTagKey);
			baseAccountId = player.getWorld().getServer().getDatabase()
				.queryLoadGlobalCacheInt(baseAccountKey);
		} catch (RuntimeException ex) {
			player.message("@or1@The vendor can't check the void ledger right now. Try again shortly.");
			return;
		}
		if (state == null) {
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That code isn't valid. Check it matches your signup code exactly.");
			log(player, " tried to redeem unknown signup code " + code);
			return;
		}
		if (baseTag == null && baseAccountId == null) {
			redeemReferralCode(player, code, cacheKey, baseTagKey, baseAccountKey, state);
			return;
		}
		if (baseTag == null || baseAccountId == null) {
			player.message("@or1@The vendor found an incomplete founder-code record. Ask staff to check the void ledger.");
			log(player, " found incomplete founder classifier state for code " + code);
			return;
		}
		redeemFounderCode(player, code, cacheKey, baseTagKey, baseAccountKey,
			state, baseTag, baseAccountId);
	}

	private void redeemReferralCode(Player player, String code, String cacheKey, String baseTagKey,
									String baseAccountKey, Integer state) {
		if (state == VoidSubscription.SIGNUP_CODE_REDEEMED) {
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That signup code has already been redeemed.");
			log(player, " tried to re-redeem referral code " + code);
			return;
		}
		if (state != VoidSubscription.SIGNUP_CODE_AVAILABLE) {
			invalidCodeState(player, code, state);
			return;
		}
		if (!canDeliverSubscriptionCard(player)) {
			return;
		}

		final ClaimMutation mutation = new ClaimMutation();
		mutation.grantCard = true;
		mutation.signupCodeKey = cacheKey;
		mutation.signupCodeExpected = state;
		mutation.checkSignupCode = true;
		mutation.redeemSignupCode = true;
		// Absence of this tag is what classifies a referral; verify it in the transaction.
		mutation.baseTagKey = baseTagKey;
		mutation.baseTagExpected = null;
		mutation.checkBaseTag = true;
		mutation.baseAccountKey = baseAccountKey;
		mutation.baseAccountExpected = null;
		mutation.checkBaseAccount = true;
		final ClaimCommit commit = persistClaim(player, mutation);
		if (!commit.committed) {
			player.message("@or1@The vendor could not update the void ledger. Try again shortly.");
			return;
		}
		finishCodeCardGrant(player, code, commit.card, "grant=referral_code");
		log(player, " redeemed referral code " + code + " for a subscription card.");
	}

	private void redeemFounderCode(Player player, String code, String cacheKey, String baseTagKey,
								 String baseAccountKey, Integer state, Integer baseTag,
								 Integer baseAccountId) {
		if (baseTag < VoidSubscription.BASE_CODE_UNASSIGNED
			|| baseAccountId < VoidSubscription.BASE_CODE_UNASSIGNED) {
			player.message("@or1@The vendor found an invalid founder-code record. Ask staff to check the void ledger.");
			log(player, " found founder code " + code + " in unexpected classifier state.");
			return;
		}
		final int currentAccountId = VoidSubscription.getAccountId(player);
		final String starterKey = currentAccountId > 0
			? VoidSubscription.starterCardCacheKey(currentAccountId, player.getDatabaseID()) : "";
		final Integer starterState;
		final Integer launchState = launchCardState(player);
		try {
			starterState = !starterKey.isEmpty() ? player.getWorld().getServer().getDatabase()
				.queryLoadGlobalCacheInt(starterKey) : null;
		} catch (RuntimeException ex) {
			player.message("@or1@The vendor can't check the void ledger right now. Try again shortly.");
			return;
		}
		final boolean hasCompositeLedger = starterState != null;
		final boolean linkedFounderRoute = baseAccountId > 0;
		if (linkedFounderRoute) {
			if (!VoidSubscription.linkedBaseCodeRouteMatches(baseAccountId, baseTag,
				currentAccountId, player.getDatabaseID(), hasCompositeLedger)) {
				player.message("@mag@The vendor checks your code against the void ledger.");
				player.message("@whi@That founder code does not match this character's frozen route.");
				log(player, " tried to redeem founder code " + code + " outside its frozen route.");
				return;
			}
		} else {
			if (state == VoidSubscription.SIGNUP_CODE_AVAILABLE
				&& baseTag != VoidSubscription.BASE_CODE_UNASSIGNED) {
				player.message("@or1@The vendor found an invalid code-only founder binding. Ask staff to check the void ledger.");
				return;
			}
			if (!VoidSubscription.baseCodeMayBeRedeemedBy(baseTag, player.getDatabaseID())) {
				player.message("@mag@The vendor checks your code against the void ledger.");
				player.message("@whi@That founder code was already bound to a different character.");
				return;
			}
		}
		if ((hasCompositeLedger && !VoidSubscription.isCardClaimState(starterState))
			|| !VoidSubscription.isCardClaimState(launchState)) {
			player.message("@or1@The vendor found an invalid card record. Ask staff to check the void ledger.");
			return;
		}
		if (state == VoidSubscription.SIGNUP_CODE_REDEEMED) {
			if (linkedFounderRoute) {
				reconcileRedeemedFounderCode(player, code, cacheKey, baseTagKey, baseAccountKey,
					baseTag, baseAccountId, starterKey, starterState, launchState);
				return;
			}
			player.message("@mag@The vendor checks your code against the void ledger.");
			player.message("@whi@That founder code has already been redeemed.");
			log(player, " tried to re-redeem code-only founder code " + code);
			return;
		}
		if (state != VoidSubscription.SIGNUP_CODE_AVAILABLE) {
			invalidCodeState(player, code, state);
			return;
		}

		final VoidSubscription.CardClaimPlan plan = VoidSubscription
			.planFounderCodeClaim(hasCompositeLedger, starterState, launchState);
		if (plan.grantsCard() && !canDeliverSubscriptionCard(player)) {
			return;
		}

		final ClaimMutation mutation = new ClaimMutation();
		mutation.grantCard = plan.grantsCard();
		mutation.signupCodeKey = cacheKey;
		mutation.signupCodeExpected = state;
		mutation.checkSignupCode = true;
		mutation.redeemSignupCode = true;
		mutation.baseTagKey = baseTagKey;
		mutation.baseTagExpected = baseTag;
		mutation.checkBaseTag = true;
		mutation.bindBaseTag = true;
		mutation.baseAccountKey = baseAccountKey;
		mutation.baseAccountExpected = baseAccountId;
		mutation.checkBaseAccount = true;
		mutation.launchExpected = launchState;
		mutation.checkLaunch = true;
		mutation.claimLaunch = plan.claimsLaunch();
		if (hasCompositeLedger) {
			mutation.starterKey = starterKey;
			mutation.starterExpected = starterState;
			mutation.checkStarter = true;
			mutation.claimStarter = plan.claimsStarter();
		}

		final ClaimCommit commit = persistClaim(player, mutation);
		if (!commit.committed) {
			player.message("@or1@The vendor could not update the void ledger. Try again shortly.");
			return;
		}
		if (plan.grantsCard()) {
			finishCodeCardGrant(player, code, commit.card, "grant=founder_base_code");
			log(player, " redeemed founder code " + code + " for a subscription card.");
			return;
		}
		player.message("@mag@The vendor reconciles your founder code with the void ledger.");
		player.message("@whi@This character's founder or launch card was already issued, so no second card is created.");
		log(player, " retired founder code " + code + " because this character already received a card.");
	}

	private void reconcileRedeemedFounderCode(Player player, String code, String cacheKey,
											String baseTagKey, String baseAccountKey,
											Integer baseTag, Integer baseAccountId,
											String starterKey, Integer starterState,
											Integer launchState) {
		final boolean claimStarter = starterState == VoidSubscription.STARTER_CARD_AVAILABLE;
		final boolean claimLaunch = launchState != null
			&& launchState == VoidSubscription.LAUNCH_CARD_AVAILABLE;
		if (claimStarter || claimLaunch) {
			final ClaimMutation mutation = new ClaimMutation();
			mutation.signupCodeKey = cacheKey;
			mutation.signupCodeExpected = VoidSubscription.SIGNUP_CODE_REDEEMED;
			mutation.checkSignupCode = true;
			mutation.baseTagKey = baseTagKey;
			mutation.baseTagExpected = baseTag;
			mutation.checkBaseTag = true;
			mutation.baseAccountKey = baseAccountKey;
			mutation.baseAccountExpected = baseAccountId;
			mutation.checkBaseAccount = true;
			mutation.launchExpected = launchState;
			mutation.checkLaunch = true;
			mutation.claimLaunch = claimLaunch;
			mutation.starterKey = starterKey;
			mutation.starterExpected = starterState;
			mutation.checkStarter = true;
			mutation.claimStarter = claimStarter;
			if (!persistClaim(player, mutation).committed) {
				player.message("@or1@The vendor could not update the void ledger. Try again shortly.");
				return;
			}
			log(player, " reconciled previously redeemed founder code " + code + " with this character's card routes.");
		}
		player.message("@mag@The vendor checks your code against the void ledger.");
		player.message("@whi@That founder code has already been redeemed by this character.");
	}

	private void invalidCodeState(Player player, String code, Integer state) {
		player.message("@mag@The vendor checks your code against the void ledger.");
		player.message("@whi@That code isn't valid. Check it matches your signup code exactly.");
		log(player, " tried to redeem signup code " + code + " in unexpected state " + state);
	}

	private void finishCodeCardGrant(Player player, String code, Item card, String grantType) {
		recordSubscriptionCardGrant(player, "subscription_signup_code", card.getItemId(),
			grantType + " code_suffix=" + codeSuffix(code));
		ActionSender.sendInventory(player);
		player.message("@mag@The vendor accepts your signup code and hands you a subscription card.");
		player.message("@whi@Redeem it when you're ready to start your 7 days of subscription time.");
	}

	private void log(Player player, String message) {
		player.getWorld().getServer().getGameLogger().addQuery(
			new GenericLog(player.getWorld(), player.getUsername() + message));
	}

	private boolean canDeliverSubscriptionCard(Player player) {
		if (player.getCarriedItems().getInventory().getFreeSlots() <= 0) {
			player.message("@mag@Your subscription card is ready, but you need a free inventory slot.");
			return false;
		}
		if (player.getClientLimitations().maxItemId < VoidSubscription.CARD_ITEM_ID) {
			player.message("@or1@Update your client before claiming this subscription card.");
			return false;
		}
		if (player.getConfig().RESTRICT_ITEM_ID >= 0
			&& player.getConfig().RESTRICT_ITEM_ID < VoidSubscription.CARD_ITEM_ID) {
			player.message("@or1@This world cannot issue subscription cards yet. Ask staff to check its item limit.");
			return false;
		}
		return true;
	}

	private ClaimCommit persistClaim(final Player player, final ClaimMutation mutation) {
		final Inventory inventory = player.getCarriedItems().getInventory();
		final boolean hadLaunchState = player.getCache().hasKey(VoidSubscription.LAUNCH_CARD_CACHE_KEY);
		final Object previousLaunchState = hadLaunchState
			? player.getCache().getCacheMap().get(VoidSubscription.LAUNCH_CARD_CACHE_KEY) : null;
		if (mutation.checkLaunch
			&& !Objects.equals(launchCardState(player), mutation.launchExpected)) {
			return ClaimCommit.failed();
		}
		if (mutation.claimLaunch) {
			player.getCache().set(VoidSubscription.LAUNCH_CARD_CACHE_KEY,
				VoidSubscription.LAUNCH_CARD_CLAIMED);
		}

		Item card = null;
		if (mutation.grantCard) {
			final int inventorySizeBeforeGrant = inventory.size();
			if (!inventory.add(new Item(VoidSubscription.CARD_ITEM_ID), false)) {
				restoreLaunchState(player, mutation.claimLaunch, hadLaunchState, previousLaunchState);
				return ClaimCommit.failed();
			}
			card = addedSubscriptionCard(inventory, inventorySizeBeforeGrant);
			if (card == null) {
				rollbackSubscriptionCardsAddedSince(inventory, inventorySizeBeforeGrant);
				restoreLaunchState(player, mutation.claimLaunch, hadLaunchState, previousLaunchState);
				return ClaimCommit.failed();
			}
		}

		final GameDatabase database = player.getWorld().getServer().getDatabase();
		final boolean committed = database.atomically(() -> {
			if (mutation.checkStarter) {
				requireGlobalState(database, mutation.starterKey, mutation.starterExpected);
			}
			if (mutation.checkSignupCode) {
				requireGlobalState(database, mutation.signupCodeKey, mutation.signupCodeExpected);
			}
			if (mutation.checkBaseTag) {
				requireGlobalState(database, mutation.baseTagKey, mutation.baseTagExpected);
			}
			if (mutation.checkBaseAccount) {
				requireGlobalState(database, mutation.baseAccountKey, mutation.baseAccountExpected);
			}

			if (mutation.grantCard) {
				database.savePlayerInventory(player);
			}
			if (mutation.claimLaunch) {
				database.querySavePlayerCache(player);
			}
			if (mutation.claimStarter) {
				database.querySaveGlobalCacheInt(mutation.starterKey,
					VoidSubscription.STARTER_CARD_CLAIMED);
			}
			if (mutation.redeemSignupCode) {
				database.querySaveGlobalCacheInt(mutation.signupCodeKey,
					VoidSubscription.SIGNUP_CODE_REDEEMED);
			}
			if (mutation.bindBaseTag) {
				database.querySaveGlobalCacheInt(mutation.baseTagKey, player.getDatabaseID());
			}
		});
		if (!committed) {
			if (card != null && inventory.remove(card, false) < 0) {
				LOGGER.error("Failed to roll back subscription card {} for {}",
					card.getItemId(), player.getUsername());
			}
			restoreLaunchState(player, mutation.claimLaunch, hadLaunchState, previousLaunchState);
			return ClaimCommit.failed();
		}
		return new ClaimCommit(true, card);
	}

	private Item addedSubscriptionCard(Inventory inventory, int previousSize) {
		for (int index = previousSize; index < inventory.size(); index++) {
			final Item item = inventory.get(index);
			if (item != null && item.getCatalogId() == VoidSubscription.CARD_ITEM_ID) {
				return item;
			}
		}
		return null;
	}

	private void rollbackSubscriptionCardsAddedSince(Inventory inventory, int previousSize) {
		for (int index = inventory.size() - 1; index >= previousSize; index--) {
			final Item item = inventory.get(index);
			if (item != null && item.getCatalogId() == VoidSubscription.CARD_ITEM_ID) {
				inventory.remove(item, false);
			}
		}
	}

	private void requireGlobalState(GameDatabase database, String key, Integer expected) {
		final Integer actual = database.queryLoadGlobalCacheInt(key);
		if (!Objects.equals(expected, actual)) {
			throw new GameDatabaseException(VoidSubscriptionVendor.class,
				"Subscription claim state changed for " + key);
		}
	}

	private Integer launchCardState(Player player) {
		if (player == null || !player.getCache().hasKey(VoidSubscription.LAUNCH_CARD_CACHE_KEY)) {
			return null;
		}
		try {
			return player.getCache().getInt(VoidSubscription.LAUNCH_CARD_CACHE_KEY);
		} catch (RuntimeException ex) {
			return Integer.MIN_VALUE;
		}
	}

	private void restoreLaunchState(Player player, boolean changed, boolean existed, Object previous) {
		if (!changed) {
			return;
		}
		if (existed) {
			player.getCache().put(VoidSubscription.LAUNCH_CARD_CACHE_KEY, previous);
		} else {
			player.getCache().remove(VoidSubscription.LAUNCH_CARD_CACHE_KEY);
		}
	}

	private void recordSubscriptionCardGrant(final Player player, final String source, final long itemID,
											 final String extra) {
		final int x = player.getX();
		final int y = player.getY();
		player.getWorld().getServer().submitSqlLogging(() -> {
			try {
				player.getWorld().getServer().getDatabase().addItemProvenanceEvent(player, player, "item_origin",
					source, "player_inventory", "subscription_card_grant", VoidSubscription.CARD_ITEM_ID, 1,
					false, itemID, x, y, extra);
			} catch (final GameDatabaseException ex) {
				LOGGER.catching(ex);
			}
		});
	}

	private static final class ClaimMutation {
		private boolean grantCard;
		private String starterKey;
		private Integer starterExpected;
		private boolean checkStarter;
		private boolean claimStarter;
		private Integer launchExpected;
		private boolean checkLaunch;
		private boolean claimLaunch;
		private String signupCodeKey;
		private Integer signupCodeExpected;
		private boolean checkSignupCode;
		private boolean redeemSignupCode;
		private String baseTagKey;
		private Integer baseTagExpected;
		private boolean checkBaseTag;
		private boolean bindBaseTag;
		private String baseAccountKey;
		private Integer baseAccountExpected;
		private boolean checkBaseAccount;
	}

	private static final class ClaimCommit {
		private final boolean committed;
		private final Item card;

		private ClaimCommit(boolean committed, Item card) {
			this.committed = committed;
			this.card = card;
		}

		private static ClaimCommit failed() {
			return new ClaimCommit(false, null);
		}
	}

	private String codeSuffix(final String code) {
		if (code == null || code.length() <= 4) {
			return "";
		}
		return code.substring(code.length() - 4);
	}

	private boolean isVendor(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}
}
