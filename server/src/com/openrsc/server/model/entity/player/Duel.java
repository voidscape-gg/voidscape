package com.openrsc.server.model.entity.player;

import com.openrsc.server.content.dueljournal.DuelJournalService;
import com.openrsc.server.content.dueljournal.DuelJournalSession;
import com.openrsc.server.content.duelproof.DuelProofService;
import com.openrsc.server.content.duelproof.DuelProofSession;
import com.openrsc.server.database.impl.mysql.queries.logging.DeathLog;
import com.openrsc.server.model.container.ContainerListener;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemContainer;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.net.rsc.ActionSender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class Duel implements ContainerListener {
	public static Logger LOGGER = LogManager.getLogger();
	private Player player;
	private Player duelRecipient;

	private boolean[] duelOptions = new boolean[4];

	private boolean duelAccepted;

	private boolean duelConfirmAccepted;

	private boolean duelActive;

	private ItemContainer duelOffer;

	private DuelJournalSession journalSession;

	private DuelProofSession proofSession;

	private boolean duelCombatStarted;

	public Duel(Player player) {
		this.player = player;
		this.duelOffer = new ItemContainer(player, 12, false);
	}


	public Player getPlayer() {
		return player;
	}


	public void setPlayer(Player player) {
		this.player = player;
	}


	public Player getDuelRecipient() {
		return duelRecipient;
	}


	public void setDuelRecipient(Player duelRecipient) {
		this.duelRecipient = duelRecipient;
	}


	public boolean isDuelAccepted() {
		return duelAccepted;
	}


	public void setDuelAccepted(boolean duelAccepted) {
		this.duelAccepted = duelAccepted;
	}


	public boolean isDuelConfirmAccepted() {
		return duelConfirmAccepted;
	}


	public void setDuelConfirmAccepted(boolean duelConfirmAccepted) {
		this.duelConfirmAccepted = duelConfirmAccepted;
	}


	public boolean isDuelActive() {
		return duelActive;
	}


	public void setDuelActive(boolean duelActive) {
		this.duelActive = duelActive;
	}


	public ItemContainer getDuelOffer() {
		return duelOffer;
	}


	public void setDuelOffer(ItemContainer duelOffer) {
		this.duelOffer = duelOffer;
	}

	public DuelJournalSession getJournalSession() {
		return journalSession;
	}

	public void setJournalSession(final DuelJournalSession journalSession) {
		this.journalSession = journalSession;
	}

	public void clearJournalSession(final DuelJournalSession expected) {
		if (journalSession == expected) {
			journalSession = null;
		}
	}

	public DuelProofSession getProofSession() {
		return proofSession;
	}

	public void setProofSession(final DuelProofSession proofSession) {
		this.proofSession = proofSession;
	}

	public void clearProofSession(final DuelProofSession expected) {
		if (proofSession == expected) {
			proofSession = null;
		}
	}

	public boolean hasDuelCombatStarted() {
		return duelCombatStarted;
	}

	public void setDuelCombatStarted(final boolean duelCombatStarted) {
		this.duelCombatStarted = duelCombatStarted;
	}


	@Override
	public void fireItemChanged(int slot) {
		// TODO Auto-generated method stub

	}

	public void clearDuelOptions() {
		for (int i = 0; i < 4; i++) {
			duelOptions[i] = false;
		}
	}

	public boolean getDuelSetting(int i) {
		return duelOptions[i];
	}

	public void setDuelSetting(int i, boolean b) {
		duelOptions[i] = b;
	}

	@Override
	public void fireItemsChanged() {
		// TODO Auto-generated method stub

	}

	@Override
	public void fireContainerFull() {
		// TODO Auto-generated method stub

	}

	public void resetAll() {
		final DuelProofSession interruptedProof = proofSession;
		proofSession = null;
		duelCombatStarted = false;
		if (interruptedProof != null) {
			DuelProofService.onDuelReset(interruptedProof);
		}
		if (duelRecipient != null) {
			final Player duelRecipient = this.duelRecipient;

			this.duelRecipient = null;

			if (player.equals(duelRecipient.getDuel().getDuelRecipient())) {
				duelRecipient.getDuel().resetAll();
			}
		}

		if (isDuelActive()) {
			ActionSender.sendDuelWindowClose(player);
		}

		setDuelActive(false);
		setDuelAccepted(false);
		setDuelConfirmAccepted(false);
		journalSession = null;

		resetDuelOffer();
		clearDuelOptions();
	}

	public void resetDuelOffer() {
		duelOffer.clear();
	}

	public void addToDuelOffer(Item tItem) {
		duelOffer.add(tItem);
	}

	public boolean checkDuelItems() {
		for (Item i : getDuelOffer().getItems()) {
			Item affectedItem = player.getCarriedItems().getInventory().get(
				player.getCarriedItems().getInventory().getLastIndexById(i.getCatalogId(), Optional.of(i.getNoted())));
			if (affectedItem == null || affectedItem.getAmount() < i.getAmount()) {
				return false;
			}
		}
		return true;
	}

	public void dropOnDeath() {
		Player duelOpponent = getDuelRecipient();
		final DuelProofSession settlementProof = proofSession != null
			? proofSession : (duelOpponent == null ? null : duelOpponent.getDuel().getProofSession());
		if (settlementProof != null
			&& !DuelProofService.canSettle(settlementProof, duelOpponent, player)) {
			DuelProofService.failCombat(settlementProof,
				new IllegalStateException("stake settlement reached without a verified terminal capture"));
			return;
		}
		DeathLog log = new DeathLog(player, duelRecipient, true);
		synchronized(getDuelOffer().getItems()) {
			for (Item item : getDuelOffer().getItems()) {
				Item affectedItem = player.getCarriedItems().getInventory().get(
					player.getCarriedItems().getInventory().getLastIndexById(item.getCatalogId(), Optional.of(item.getNoted())));
				if (affectedItem == null || item.getAmount() > affectedItem.getAmount()) {
					if (player.getConfig().WANT_EQUIPMENT_TAB && item.getAmount() == 1 && player.getCarriedItems().getEquipment().hasEquipped(item.getCatalogId())) {
						player.updateWornItems(item.getDef(player.getWorld()).getWieldPosition(),
							player.getSettings().getAppearance().getSprite(item.getDef(player.getWorld()).getWieldPosition()),
							item.getDef(player.getWorld()).getWearableId(), false);
						if (player.getCarriedItems().getEquipment().remove(item, item.getAmount()) != -1) {
							log.addDroppedItem(item);
							player.getWorld().registerItem(new GroundItem(duelOpponent.getWorld(), item.getCatalogId(), player.getX(), player.getY(), item.getAmount(), duelOpponent));
						}
					}
					LOGGER.info("Missing staked item [" + item.getCatalogId() + ", " + item.getAmount()
						+ "] from = " + player.getUsername() + "; to = " + duelRecipient.getUsername() + ";");
				} else {
					if (player.getCarriedItems().remove(new Item(item.getCatalogId(), item.getAmount(), item.getNoted(), affectedItem.getItemId())) != -1) {
						log.addDroppedItem(item);
						player.getWorld().registerItem(new GroundItem(duelOpponent.getWorld(), item.getCatalogId(), player.getX(), player.getY(), item.getAmount(), duelOpponent, item.getNoted()));
					}
				}
			}
		}
		log.build();
		player.getWorld().getServer().getGameLogger().addQuery(log);

		if (player != null && duelOpponent != null) {
			player.save();
			duelOpponent.save();
			if (settlementProof != null) {
				if (!DuelProofService.completeSettlement(settlementProof, duelOpponent, player)) {
					LOGGER.error("Verified duel settlement for players {} and {} was not queued",
						duelOpponent.getDatabaseID(), player.getDatabaseID());
				}
			} else {
				DuelJournalService.complete(journalSession, duelOpponent, player);
			}
		}
	}

	/**
	 * Check if the player is dueling.
	 *
	 * Returns true if both the player and opponent have confirmed the duel.
	 *
	 * @return true if player is dueling, otherwise returns false.
	 */
	public boolean isDueling() {
		return duelActive && duelConfirmAccepted && duelRecipient != null && duelRecipient.getDuel().isDuelConfirmAccepted();
	}
}
