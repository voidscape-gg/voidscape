package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.QuestEquipmentUnlocks;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public final class VoidRequisitioner implements TalkNpcTrigger, OpNpcTrigger {

	private static final int NPC_ID = NpcId.VOID_REQUISITIONER.id();
	private static final String UNLOCK_COMMAND = "Unlock";

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isRequisitioner(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isRequisitioner(npc)) return;

		npcsay(player, npc,
			"I file rights for adventurers who have more coin than patience.",
			"Five hundred thousand coins buys one equipment right.");

		int option = multi(player, npc,
			"I want to buy an equipment right.",
			"What are equipment rights?",
			"Never mind.");

		if (option == 0) {
			say(player, npc, "I want to buy an equipment right.");
			openUnlockMenu(player, npc);
		} else if (option == 1) {
			say(player, npc, "What are equipment rights?");
			npcsay(player, npc,
				"A right lets you wear certain quest-locked equipment.",
				"It does not complete the quest or grant its other rewards.");
		} else {
			npcsay(player, npc, "Then keep your papers clean.");
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isRequisitioner(npc)
			&& command != null
			&& command.equalsIgnoreCase(UNLOCK_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) return;
		openUnlockMenu(player, npc);
	}

	private void openUnlockMenu(Player player, Npc npc) {
		QuestEquipmentUnlocks.Unlock[] allUnlocks = QuestEquipmentUnlocks.Unlock.values();
		QuestEquipmentUnlocks.Unlock[] purchasableUnlocks = new QuestEquipmentUnlocks.Unlock[allUnlocks.length];
		String[] options = new String[allUnlocks.length + 1];

		int optionCount = 0;
		for (QuestEquipmentUnlocks.Unlock unlock : allUnlocks) {
			if (!QuestEquipmentUnlocks.hasEquipmentRight(player, unlock)) {
				purchasableUnlocks[optionCount] = unlock;
				options[optionCount] = unlock.purchaseOption();
				optionCount++;
			}
		}

		if (optionCount == 0) {
			npcsay(player, npc, "You already hold every equipment right I can sell.");
			return;
		}

		options[optionCount] = "Never mind.";
		String[] menuOptions = new String[optionCount + 1];
		System.arraycopy(options, 0, menuOptions, 0, optionCount + 1);

		int option = multi(player, npc, menuOptions);
		if (option < 0 || option >= optionCount) {
			npcsay(player, npc, "Then keep your purse closed.");
			return;
		}

		purchaseUnlock(player, npc, purchasableUnlocks[option]);
	}

	private void purchaseUnlock(Player player, Npc npc, QuestEquipmentUnlocks.Unlock unlock) {
		say(player, npc, "I'll buy the " + unlock.displayName().toLowerCase() + ".");
		npcsay(player, npc,
			"That right costs 500,000 coins.",
			"It lets you wear the equipment, but it does not complete " + unlock.questName() + ".");

		int confirm = multi(player, npc,
			"Yes, pay 500,000 coins.",
			"No thanks.");
		if (confirm != 0) {
			npcsay(player, npc, "A wise clerk never hurries a signature.");
			return;
		}

		if (QuestEquipmentUnlocks.hasEquipmentRight(player, unlock)) {
			npcsay(player, npc, "You already have that right.");
			return;
		}

		if (!ifheld(player, ItemId.COINS.id(), QuestEquipmentUnlocks.PRICE_COINS)) {
			npcsay(player, npc, "Come back with 500,000 coins in your pack.");
			return;
		}

		if (player.getCarriedItems().remove(new Item(ItemId.COINS.id(), QuestEquipmentUnlocks.PRICE_COINS)) == -1) {
			return;
		}

		player.getCache().store(unlock.cacheKey(), true);
		player.message("You bought the " + unlock.displayName().toLowerCase() + ".");
		npcsay(player, npc, "Filed. The right is yours.");
	}

	private boolean isRequisitioner(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}
}
