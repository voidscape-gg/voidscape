package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.dueljournal.DuelJournalService;
import com.openrsc.server.content.duelproof.DuelProofService;
import com.openrsc.server.content.duelproof.DuelProofSession;
import com.openrsc.server.event.rsc.impl.combat.CombatEvent;
import com.openrsc.server.event.rsc.impl.combat.CombatFormula;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.container.Equipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerSettings;
import com.openrsc.server.model.struct.UnequipRequest;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.PlayerDuelStruct;
import com.openrsc.server.util.rsc.CertUtil;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.Formulae;
import com.openrsc.server.util.rsc.MessageType;
import com.voidscape.duelproof.DuelProofSpec;

import java.util.List;
import java.util.Optional;

public class PlayerDuelHandler implements PayloadProcessor<PlayerDuelStruct, OpcodeIn> {

	private boolean busy(Player player) {
		return player.inCombat() || player.isBusy() || player.isRanging() || player.accessingBank() || player.getTrade().isTradeActive();
	}

	public void process(PlayerDuelStruct payload, Player player) throws Exception {
		Player affectedPlayer = player.getDuel().getDuelRecipient();
		if (rejectVoidArenaDuel(player, affectedPlayer)) {
			return;
		}

		if (player == affectedPlayer) {
			unsetOptions(player);
			unsetOptions(affectedPlayer);
			return;
		}

		if (!player.getWorld().getServer().getConfig().MEMBER_WORLD) {
			unsetOptions(player);
			unsetOptions(affectedPlayer);
			return;
		}

		if (player.isIronMan(IronmanMode.Ironman.id()) || player.isIronMan(IronmanMode.Ultimate.id())
			|| player.isIronMan(IronmanMode.Hardcore.id()) || player.isIronMan(IronmanMode.Transfer.id())) {
			player.message("You are an Ironman. You stand alone.");
			unsetOptions(player);
			unsetOptions(affectedPlayer);
			return;
		}

		if (player.getDuel().isDuelConfirmAccepted() && affectedPlayer != null
			&& affectedPlayer.getDuel().isDuelConfirmAccepted()) {
			return;
		}

		if (busy(player) || player.getLocation().inWilderness()) {
			unsetOptions(player);
			unsetOptions(affectedPlayer);
			return;
		}

		if (player.getLocation().inModRoom()) {
			player.message("You cannot duel in here!");
			unsetOptions(player);
			unsetOptions(affectedPlayer);
			return;
		}

		OpcodeIn opcode = payload.getOpcode();

		if (opcode == null)
			return;

		switch (opcode) {
			case PLAYER_DUEL:
				int playerIndex = payload.targetPlayerID;
				affectedPlayer = player.getWorld().getPlayer(playerIndex);
				if (rejectVoidArenaDuel(player, affectedPlayer)) {
					return;
				}
				if (affectedPlayer == null || affectedPlayer.getDuel().isDuelActive()
					|| !player.withinRange(affectedPlayer, 8) || player.getDuel().isDuelActive()) {
					player.getDuel().setDuelRecipient(null);
					player.getDuel().resetAll();
					return;
				}

				if (affectedPlayer.isIronMan(IronmanMode.Ironman.id()) || affectedPlayer.isIronMan(IronmanMode.Ultimate.id())
					|| affectedPlayer.isIronMan(IronmanMode.Hardcore.id()) || affectedPlayer.isIronMan(IronmanMode.Transfer.id())) {
					player.message(affectedPlayer.getUsername() + " is an Ironman. " + (affectedPlayer.isMale() ? "He" : "She") + " stands alone.");
					unsetOptions(player);
					unsetOptions(affectedPlayer);
					return;
				}

				boolean blockAll = affectedPlayer.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_DUEL_REQUESTS, affectedPlayer.isUsingCustomClient())
					== PlayerSettings.BlockingMode.All.id();
				boolean blockNonFriends = affectedPlayer.getSettings().getPrivacySetting(PlayerSettings.PRIVACY_BLOCK_DUEL_REQUESTS, affectedPlayer.isUsingCustomClient())
					== PlayerSettings.BlockingMode.NonFriends.id();
				if ((blockAll || (blockNonFriends && !affectedPlayer.getSocial().isFriendsWith(player.getUsernameHash()))
					|| affectedPlayer.getSocial().isIgnoring(player.getUsernameHash())) && !player.isMod()) {
					return;
				}

				if (!affectedPlayer.withinRange(player.getLocation(), 4) || !player.canReach(affectedPlayer)) {
					player.message("I'm not near enough");
					player.getDuel().resetAll();
					return;
				}

				if (!PathValidation.checkPath(player.getWorld(), player.getLocation(), affectedPlayer.getLocation())) {
					player.message("There is an obstacle in the way");
					player.getDuel().resetAll();
					return;
				}

				player.getDuel().setDuelRecipient(affectedPlayer);

				if (!player.getDuel().isDuelActive() && affectedPlayer.getDuel().getDuelRecipient() != null
					&& affectedPlayer.getDuel().getDuelRecipient().equals(player)
					&& !affectedPlayer.getDuel().isDuelActive()) {

					player.resetPath();
					player.getDuel().setDuelActive(true);
					player.getDuel().clearDuelOptions();
					player.resetAllExceptDueling();

					affectedPlayer.resetPath();
					affectedPlayer.getDuel().setDuelActive(true);
					affectedPlayer.getDuel().clearDuelOptions();
					affectedPlayer.resetAllExceptDueling();

					ActionSender.sendDuelWindowOpen(player);
					ActionSender.sendDuelWindowOpen(affectedPlayer);
				} else {
					ActionSender.sendMessage(player, null, MessageType.INVENTORY, affectedPlayer.getDuel().isDuelActive()
						? affectedPlayer.getUsername() + " is already in a duel" : "Sending duel request", 0, null);
					ActionSender
						.sendMessage(affectedPlayer, null, MessageType.INVENTORY,
							player.getUsername() + " "
								+ Formulae.getLvlDiffColour(
								affectedPlayer.getCombatLevel() - player.getCombatLevel())
								+ "(level-" + player.getCombatLevel() + ")@whi@ wishes to duel with you",
							player.getIcon(), null);
				}
				break;
			case DUEL_FIRST_ACCEPTED:
				affectedPlayer = player.getDuel().getDuelRecipient();
				if (affectedPlayer == null || busy(affectedPlayer) || !player.getDuel().isDuelActive()
					|| !affectedPlayer.getDuel().isDuelActive()) {
					player.setSuspiciousPlayer(true, "duel accepted null or busy player");
					player.getDuel().resetAll();

					return;
				}
				player.getDuel().setDuelAccepted(true);

				ActionSender.sendOwnDuelAcceptUpdate(player);
				ActionSender.sendOpponentDuelAcceptUpdate(affectedPlayer);
				if (affectedPlayer.getDuel().isDuelAccepted()) {
					ActionSender.sendDuelConfirmScreen(player);
					ActionSender.sendDuelConfirmScreen(affectedPlayer);
				}
				break;
			case DUEL_SECOND_ACCEPTED:
				affectedPlayer = player.getDuel().getDuelRecipient();
				if (affectedPlayer == null || busy(affectedPlayer) || !player.getDuel().isDuelActive()
					|| !affectedPlayer.getDuel().isDuelActive() || !player.getDuel().isDuelAccepted()
					|| !affectedPlayer.getDuel().isDuelAccepted()) { // This shouldn't happen
					player.setSuspiciousPlayer(true, "duel confirmed null or busy player");
					player.getDuel().resetAll();
					return;
				}
				player.getDuel().setDuelConfirmAccepted(true);

				if (affectedPlayer.getDuel().isDuelConfirmAccepted()) {
					prepareFinalDuel(player, affectedPlayer);
				}
				break;
			case DUEL_DECLINED:
				affectedPlayer = player.getDuel().getDuelRecipient();
				if (affectedPlayer == null || busy(affectedPlayer) || !player.getDuel().isDuelActive()
					|| !affectedPlayer.getDuel().isDuelActive()) {
					player.setSuspiciousPlayer(true, "duel player null or not duel active");
					player.getDuel().resetAll();
					return;
				}
				affectedPlayer.message("Other player left duel screen");
				player.getDuel().resetAll();
				break;
			case DUEL_OFFER_ITEM:
				affectedPlayer = player.getDuel().getDuelRecipient();

				if (affectedPlayer == null || busy(affectedPlayer) || !player.getDuel().isDuelActive()
					|| !affectedPlayer.getDuel().isDuelActive()
					|| (player.getDuel().isDuelAccepted() && affectedPlayer.getDuel().isDuelAccepted())
					|| player.getDuel().isDuelConfirmAccepted() || affectedPlayer.getDuel().isDuelConfirmAccepted()) {
					player.setSuspiciousPlayer(true, "receive offered item duel player null or not duel active");
					player.getDuel().resetAll();
					return;
				}

				player.getDuel().setDuelAccepted(false);
				player.getDuel().setDuelConfirmAccepted(false);

				affectedPlayer.getDuel().setDuelAccepted(false);
				affectedPlayer.getDuel().setDuelConfirmAccepted(false);

				player.getDuel().resetDuelOffer();

				final int itemCount = Math.min(payload.duelCount, 8);

				for (int i = 0; i < itemCount; i++) {
					final Item item = new Item(payload.duelCatalogIDs[i], payload.duelAmounts[i], payload.duelNoted[i]);

					if (item.getAmount() < 1) {
						player.setSuspiciousPlayer(true,
							String.format("staking invalid amount of itemId: %d", item.getCatalogId()));
						continue;
					}
					if (item.getNoted() && !player.getConfig().WANT_BANK_NOTES) {
						player.message("Notes can no longer be staked with other players.");
						player.message("You may either deposit it in the bank or sell to a shop instead.");
						ActionSender.sendDuelOpponentItems(player);
						continue;
					}
					if (item.getDef(player.getWorld()).isUntradable() && !player.getWorld().getServer().getConfig().CAN_OFFER_UNTRADEABLES) {
						player.message("This object cannot be added to a duel offer");
						ActionSender.sendDuelOpponentItems(player);
						continue;
					}
					if (item.getCatalogId() > affectedPlayer.getClientLimitations().maxItemId) {
						player.message("The other player is unable to receive the staked object");
						ActionSender.sendDuelOpponentItems(player);
						continue;
					}
					if (CertUtil.isCert(item.getCatalogId()) && (player.getCertOptOut() || affectedPlayer.getCertOptOut())) {
						if (player.getCertOptOut()) {
							player.message("You have opted out of dueling certs with other players");
						}
						if (affectedPlayer.getCertOptOut()) {
							player.message("The other player has opted out of dueling certs with players");
						}
						ActionSender.sendDuelOpponentItems(player);
						continue;
					}
					if (item.getDef(player.getWorld()).getName().toLowerCase().contains("-rune") && !player.getDuel().getDuelSetting(1)) {
						player.getDuel().setDuelSetting(1, true);
						affectedPlayer.getDuel().setDuelSetting(1, true);
						player.message("When runes are staked, magic can't be used during the duel");
						affectedPlayer.message("When runes are staked, magic can't be used during the duel");
						ActionSender.sendDuelSettingUpdate(player);
						ActionSender.sendDuelSettingUpdate(affectedPlayer);
						continue;
					}

					final int invCount = player.getCarriedItems().getInventory().countId(item.getCatalogId(), Optional.of(item.getNoted()));
					final int duelCount = player.getDuel().getDuelOffer().countId(item.getCatalogId());

					if (item.getAmount() > (invCount - duelCount)) {
						if (!(player.getConfig().WANT_EQUIPMENT_TAB && item.getAmount() == 1 && player.getCarriedItems().getEquipment().hasEquipped(item.getCatalogId()))) {
							player.setSuspiciousPlayer(true,  String.format("staking insufficient amount of itemId: %d", item.getCatalogId()));
							player.getDuel().resetAll();
							return;
						}
					}

					player.getDuel().addToDuelOffer(item);
				}

				ActionSender.sendDuelOpponentItems(affectedPlayer);
				ActionSender.sendDuelOpponentItems(player);
				break;
			case DUEL_FIRST_SETTINGS_CHANGED:
				affectedPlayer = player.getDuel().getDuelRecipient();
				if (affectedPlayer == null || busy(affectedPlayer) || !player.getDuel().isDuelActive()
					|| !affectedPlayer.getDuel().isDuelActive()
					|| (player.getDuel().isDuelConfirmAccepted() && affectedPlayer.getDuel().isDuelConfirmAccepted())
					|| player.getDuel().isDuelConfirmAccepted() || affectedPlayer.getDuel().isDuelConfirmAccepted()) { // This
					// shouldn't
					// happen
					player.setSuspiciousPlayer(true, "set duel options not confirmed or null player");
					player.getDuel().resetAll();
					return;
				}

				player.getDuel().setDuelConfirmAccepted(false);
				player.getDuel().setDuelAccepted(false);

				affectedPlayer.getDuel().setDuelConfirmAccepted(false);
				affectedPlayer.getDuel().setDuelAccepted(false);

				// Read each setting and set them accordingly.
				int[] settings = new int[] { payload.disallowRetreat, payload.disallowMagic, payload.disallowPrayer, payload.disallowWeapons };
				for (int i = 0; i < 4; i++) {
					boolean b = (byte)settings[i] == 1;
					player.getDuel().setDuelSetting(i, b);
					affectedPlayer.getDuel().setDuelSetting(i, b);
				}
				synchronized(player.getDuel().getDuelOffer().getItems()) {
					for (Item item : player.getDuel().getDuelOffer().getItems()) {
						if (item.getDef(player.getWorld()).getName().toLowerCase().contains("-rune") && !player.getDuel().getDuelSetting(1)) {
							player.getDuel().setDuelSetting(1, true);
							affectedPlayer.getDuel().setDuelSetting(1, true);
							player.message("When runes are staked, magic can't be used during the duel");
							affectedPlayer.message("When runes are staked, magic can't be used during the duel");

						}
					}
				}
				synchronized(affectedPlayer.getDuel().getDuelOffer().getItems()) {
					for (Item item : affectedPlayer.getDuel().getDuelOffer().getItems()) {
						if (item.getDef(player.getWorld()).getName().toLowerCase().contains("-rune") && !player.getDuel().getDuelSetting(1)) {
							player.getDuel().setDuelSetting(1, true);
							affectedPlayer.getDuel().setDuelSetting(1, true);
							player.message("When runes are staked, magic can't be used during the duel");
							affectedPlayer.message("When runes are staked, magic can't be used during the duel");
						}
					}
				}
				ActionSender.sendDuelSettingUpdate(player);
				ActionSender.sendDuelSettingUpdate(affectedPlayer);
				break;
			default:
				System.out.println("Somehow PlayerDuelHandler is mismanaged.");
				break;
		}
	}

	private void prepareFinalDuel(final Player first, final Player second) {
		if (rejectVoidArenaDuel(first, second)) {
			return;
		}
		first.resetAllExceptDueling();
		second.resetAllExceptDueling();
		CombatFormula.clearPvpMeleeMomentum(first);
		CombatFormula.clearPvpMeleeMomentum(second);

		if (!first.getDuel().checkDuelItems() || !second.getDuel().checkDuelItems()) {
			first.setSuspiciousPlayer(true, "duel without appropriate items in inventory");
			second.setSuspiciousPlayer(true, "duel without appropriate items in inventory");
			cancelPreparedDuel(first, second);
			return;
		}

		if (!applyFinalDuelRules(first, second)) {
			return;
		}
		if (DuelProofService.requiresProof(first, second)
			&& (proofStakeConsumesEquippedRecoil(first)
				|| proofStakeConsumesEquippedRecoil(second))) {
			first.message("An equipped Ring of recoil cannot also be staked in a verified duel.");
			second.message("An equipped Ring of recoil cannot also be staked in a verified duel.");
			cancelPreparedDuel(first, second);
			return;
		}

		ActionSender.sendDuelWindowClose(first);
		ActionSender.sendDuelWindowClose(second);
		first.setBusy(true);
		second.setBusy(true);

		if (DuelProofService.requiresProof(first, second)) {
			if (!DuelProofService.supportsProofClient(first)
				|| !DuelProofService.supportsProofClient(second)) {
				first.message("This No Magic duel needs an updated Voidscape client for melee verification.");
				second.message("This No Magic duel needs an updated Voidscape client for melee verification.");
				cancelPreparedDuel(first, second);
				return;
			}
			first.message("Preparing your duel...");
			second.message("Preparing your duel...");
			// Overlap the normal approach with the durable handshake. The final lock still
			// revalidates adjacency and falls back to the guarded walk action if needed.
			first.walkToEntity(second.getX(), second.getY());
			DuelProofService.begin(first, second, new Runnable() {
				@Override
				public void run() {
					continuePreparedProofDuel(first, second);
				}
			});
			return;
		}

		commencePreparedDuel(first, second, null);
	}

	private void continuePreparedProofDuel(final Player first, final Player second) {
		final DuelProofSession proofSession = first.getDuel().getProofSession();
		if (proofSession == null) {
			cancelPreparedDuel(first, second);
			return;
		}
		if (proofSession.getPhase() == DuelProofSession.Phase.PERSISTING_COMBAT) {
			if (isPreparedDuelReady(first, second, proofSession)
				&& first.withinRange(second, 1)
				&& PathValidation.checkAdjacentDistance(first.getWorld(), first.getLocation(),
					second.getLocation(), true, false)) {
				enterPreparedDuelCombat(first, second, proofSession);
			}
			return;
		}
		commencePreparedDuel(first, second, proofSession);
	}

	/** True when the accepted stake needs the same physical recoil ring that is worn. */
	private boolean proofStakeConsumesEquippedRecoil(final Player player) {
		if (player == null || !player.getCarriedItems().getEquipment()
			.hasEquipped(ItemId.RING_OF_RECOIL.id())) {
			return false;
		}

		long offered = 0L;
		final List<Item> offeredItems = player.getDuel().getDuelOffer().getItems();
		synchronized (offeredItems) {
			for (final Item item : offeredItems) {
				if (item != null && item.getCatalogId() == ItemId.RING_OF_RECOIL.id()
					&& !item.getNoted()) {
					offered += item.getAmount();
				}
			}
		}
		if (offered <= 0L) {
			return false;
		}

		long unworn = 0L;
		final List<Item> inventory = player.getCarriedItems().getInventory().getItems();
		synchronized (inventory) {
			for (final Item item : inventory) {
				if (item != null && item.getCatalogId() == ItemId.RING_OF_RECOIL.id()
					&& !item.getNoted() && !item.isWielded()) {
					unworn += item.getAmount();
				}
			}
		}
		return offered > unworn;
	}

	private boolean applyFinalDuelRules(final Player first, final Player second) {
		if (first.getDuel().getDuelSetting(3)) {
			if (first.getConfig().WANT_EQUIPMENT_TAB) {
				Item item;
				for (int i = 0; i < Equipment.SLOT_COUNT; i++) {
					item = first.getCarriedItems().getEquipment().get(i);
					if (item != null && !first.getCarriedItems().getEquipment().unequipItem(
						new UnequipRequest(first, item, UnequipRequest.RequestType.FROM_EQUIPMENT, false))) {
						first.message("Your inventory is full and you can't unequip your items. Cancelling duel.");
						second.message("Your opponent needs to clear his inventory. Cancelling duel.");
						cancelPreparedDuel(first, second);
						return false;
					}
				}

				for (int i = 0; i < Equipment.SLOT_COUNT; i++) {
					item = second.getCarriedItems().getEquipment().get(i);
					if (item != null && !second.getCarriedItems().getEquipment().unequipItem(
						new UnequipRequest(second, item, UnequipRequest.RequestType.FROM_EQUIPMENT, false))) {
						second.message("Your inventory is full and you can't unequip your items. Cancelling duel.");
						first.message("Your opponent needs to clear his inventory. Cancelling duel.");
						cancelPreparedDuel(first, second);
						return false;
					}
				}
			} else {
				synchronized (first.getCarriedItems().getInventory().getItems()) {
					for (final Item item : first.getCarriedItems().getInventory().getItems()) {
						if (item.isWielded()) {
							first.getCarriedItems().getEquipment().unequipItem(
								new UnequipRequest(first, item, UnequipRequest.RequestType.FROM_INVENTORY, false));
						}
					}
				}
				synchronized (second.getCarriedItems().getInventory().getItems()) {
					for (final Item item : second.getCarriedItems().getInventory().getItems()) {
						if (item.isWielded()) {
							second.getCarriedItems().getEquipment().unequipItem(
								new UnequipRequest(first, item, UnequipRequest.RequestType.FROM_INVENTORY, false));
						}
					}
				}
				ActionSender.sendSound(first, "click");
				ActionSender.sendInventory(first);
				ActionSender.sendEquipmentStats(first);
				ActionSender.sendSound(second, "click");
				ActionSender.sendInventory(second);
				ActionSender.sendEquipmentStats(second);
			}
		}

		if (first.getDuel().getDuelSetting(2)) {
			first.getPrayers().resetPrayers();
			second.getPrayers().resetPrayers();
		}
		return true;
	}

	private void commencePreparedDuel(final Player first, final Player second,
								   final DuelProofSession proofSession) {
		if (!isPreparedDuelReady(first, second, proofSession)) {
			if (proofSession != null) {
				DuelProofService.abort(proofSession, "state");
			} else {
				cancelPreparedDuel(first, second);
			}
			return;
		}
		if (!first.getDuel().checkDuelItems() || !second.getDuel().checkDuelItems()) {
			if (proofSession != null) {
				DuelProofService.abort(proofSession, "items");
			} else {
				cancelPreparedDuel(first, second);
			}
			return;
		}

		first.walkToEntity(second.getX(), second.getY());
		first.setWalkToAction(new WalkToMobAction(first, second, 1) {
			@Override
			public void executeInternal() {
				final Player opponentPlayer = (Player) mob;
				getPlayer().resetPath();
				opponentPlayer.resetPath();

				if (!isPreparedDuelReady(getPlayer(), opponentPlayer, proofSession)) {
					if (proofSession != null) {
						DuelProofService.abort(proofSession, "state");
					} else {
						cancelPreparedDuel(getPlayer(), opponentPlayer);
					}
					return;
				}
				if (!getPlayer().getDuel().checkDuelItems()
					|| !opponentPlayer.getDuel().checkDuelItems()) {
					if (proofSession != null) {
						DuelProofService.abort(proofSession, "items");
					} else {
						cancelPreparedDuel(getPlayer(), opponentPlayer);
					}
					return;
				}
				if (!getPlayer().canReach(opponentPlayer)) {
					if (proofSession != null) {
						DuelProofService.abort(proofSession, "unreachable");
					} else {
						cancelPreparedDuel(getPlayer(), opponentPlayer);
					}
					return;
				}

					if (proofSession == null) {
						if (!enterPreparedDuelCombat(getPlayer(), opponentPlayer, null)) {
						cancelPreparedDuel(getPlayer(), opponentPlayer);
					}
					return;
				}

				if (!DuelProofService.persistCombatStart(proofSession, new Runnable() {
						@Override
						public void run() {
							enterPreparedDuelCombat(getPlayer(), opponentPlayer, proofSession);
					}
				})) {
					DuelProofService.abort(proofSession, "state");
				}
			}
		});
	}

	private boolean enterPreparedDuelCombat(final Player first, final Player second,
											  final DuelProofSession proofSession) {
		if (rejectVoidArenaDuel(first, second)) {
			return false;
		}
		CombatEvent combatEvent = null;
		try {
			first.resetAllExceptDueling();
			second.resetAllExceptDueling();
			first.setLocation(second.getLocation(), false);

			first.setSprite(9);
			first.setOpponent(second);
			first.setCombatTimer();
			second.setSprite(8);
			second.setOpponent(first);
			second.setCombatTimer();

			final int starterPlayerId;
			if (proofSession == null) {
				final int firstCombatLevel = first.getCombatLevel();
				final int secondCombatLevel = second.getCombatLevel();
				final int tieBreaker = firstCombatLevel == secondCombatLevel
					? DataConversions.random(0, 1) : 0;
				starterPlayerId = DuelProofSpec.starterPlayerId(
					first.getDatabaseID(), firstCombatLevel,
					second.getDatabaseID(), secondCombatLevel, tieBreaker);
			} else {
				starterPlayerId = proofSession.chooseStarterPlayerId();
			}
			final Player attacker = starterPlayerId == first.getDatabaseID() ? first : second;
			final Player opponent = attacker == first ? second : first;
			attacker.getDuel().setDuelActive(true);
			opponent.getDuel().setDuelActive(true);

			combatEvent = new CombatEvent(first.getWorld(), attacker, opponent, proofSession);
			attacker.setCombatEvent(combatEvent);
			opponent.setCombatEvent(combatEvent);
			if (!first.getWorld().getServer().getGameEventHandler().add(combatEvent)) {
				cleanupFailedCombatEntry(first, second, combatEvent);
				return false;
			}

			DuelJournalService.begin(first, second);
			first.message("Commencing Duel!");
			second.message("Commencing Duel!");
		} catch (final RuntimeException failure) {
			cleanupFailedCombatEntry(first, second, combatEvent);
			return false;
		}
		first.setBusy(false);
		second.setBusy(false);
		first.getDuel().setDuelCombatStarted(true);
		second.getDuel().setDuelCombatStarted(true);
		return true;
	}

	private void cleanupFailedCombatEntry(final Player first, final Player second,
										final CombatEvent combatEvent) {
		if (combatEvent != null) {
			combatEvent.stop();
			if (first.getCombatEvent() == combatEvent) {
				first.setCombatEvent(null);
			}
			if (second.getCombatEvent() == combatEvent) {
				second.setCombatEvent(null);
			}
		}
		if (first.getOpponent() == second) {
			first.setOpponent(null);
		}
		if (second.getOpponent() == first) {
			second.setOpponent(null);
		}
		first.setHitsMade(0);
		second.setHitsMade(0);
		if (first.getSprite() > 7) {
			first.setSprite(4);
		}
		if (second.getSprite() > 7) {
			second.setSprite(4);
		}
		first.getDuel().setDuelCombatStarted(false);
		second.getDuel().setDuelCombatStarted(false);
	}

	private boolean isPreparedDuelReady(final Player first, final Player second,
									 final DuelProofSession proofSession) {
		if (rejectVoidArenaDuel(first, second)) {
			return false;
		}
		return first != null && second != null && first != second
			&& first.loggedIn() && second.loggedIn() && !first.isRemoved() && !second.isRemoved()
			&& first.getDuel().isDuelActive() && second.getDuel().isDuelActive()
			&& first.getDuel().isDuelAccepted() && second.getDuel().isDuelAccepted()
			&& first.getDuel().isDuelConfirmAccepted() && second.getDuel().isDuelConfirmAccepted()
			&& first.getDuel().getDuelRecipient() == second
			&& second.getDuel().getDuelRecipient() == first
			&& (proofSession == null
				? first.getDuel().getProofSession() == null && second.getDuel().getProofSession() == null
				: proofSession.isLocked() && first.getDuel().getProofSession() == proofSession
					&& second.getDuel().getProofSession() == proofSession);
	}

	private void cancelPreparedDuel(final Player first, final Player second) {
		if (first != null) {
			first.setBusy(false);
			first.resetPath();
			first.setWalkToAction(null);
			first.getDuel().resetAll();
		}
		if (second != null) {
			second.setBusy(false);
			second.resetPath();
			second.setWalkToAction(null);
			second.getDuel().resetAll();
		}
	}

	private boolean rejectVoidArenaDuel(final Player first, final Player second) {
		if (!blocksOrdinaryDuel(first) && !blocksOrdinaryDuel(second)) {
			return false;
		}
		if (first != null) {
			first.message("Regular duels are unavailable while you are involved in the Void Arena.");
		}
		if (second != null && second != first) {
			second.message("Regular duels are unavailable while you are involved in the Void Arena.");
		}
		unsetOptions(first);
		unsetOptions(second);
		return true;
	}

	private boolean blocksOrdinaryDuel(final Player player) {
		return player != null && player.getWorld().getVoidArena().blocksOrdinaryDuel(player);
	}

	private void unsetOptions(Player player) {
		if (player == null) {
			return;
		}
		player.getDuel().resetAll();
	}
}
