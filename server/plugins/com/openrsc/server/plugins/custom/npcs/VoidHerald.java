package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.content.VoidVeteranTour;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.shared.PlayerTitleMenu;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;
import static com.openrsc.server.plugins.Functions.say;

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

		if (VoidVeteranTour.needsRequiredTour(player)) {
			npcsay(player, npc, "The Archivist waits south of here", "Hear what changed, then return to choose your path");
			return;
		}

		VoidPath.openPathChoice(player, npc);
	}

	private void handleActivityMenu(Player player, Npc npc) {
		npcsay(player, npc, "I keep the realm's title register as well as its notices.");
		int option = multi(player, npc, false,
			"Manage my titles and honorifics.",
			"What Void activities are open?",
			"Never mind.");
		if (option == 0) {
			say(player, npc, "Manage my titles and honorifics.");
			PlayerTitleMenu.openManage(player);
		} else if (option == 1) {
			say(player, npc, "What Void activities are open?");
			npcsay(player, npc,
				"The deeper Void trials are not open yet.",
				"For now, the nearby rift leads only to the Void Arena.");
		}
	}

}
