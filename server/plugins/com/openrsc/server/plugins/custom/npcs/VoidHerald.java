package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.minigames.undeadsiege.UndeadSiegeNpcDialogue;
import com.openrsc.server.plugins.custom.minigames.voidrush.VoidRushMinigame;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidHerald implements TalkNpcTrigger {

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.VOID_HERALD.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (npc.getID() != NpcId.VOID_HERALD.id()) {
			return;
		}

		if (VoidPath.hasChosen(player)) {
			handleActivityMenu(player, npc);
			return;
		}

		VoidPath.openPathChoice(player, npc);
	}

	private void handleActivityMenu(Player player, Npc npc) {
		npcsay(player, npc, "What do you seek in the Void?");
		int choice = multi(player, npc,
			"Enter Void Rush.",
			"Enter Undead Siege.",
			"How does Undead Siege work?",
			"Nevermind.");

		if (choice == 0) {
			VoidRushMinigame.joinQueue(player);
		} else if (choice == 1) {
			UndeadSiegeNpcDialogue.handle(player, npc);
		} else if (choice == 2) {
			UndeadSiegeNpcDialogue.explain(player, npc);
		}
	}

}
