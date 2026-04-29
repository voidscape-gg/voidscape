package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
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

		npcsay(player, npc, "choose your path");

		if (VoidPath.hasChosen(player)) {
			player.message("You have already chosen " + VoidPath.name(VoidPath.get(player)) + ".");
			return;
		}

		int choice = multi(player, npc, false,
			"Warrior's Path - 2x XP: Attack, Defense, Strength",
			"Forager's Path - 2x XP: Fishing, Cooking, Mining",
			"Arcanist's Path - 2x XP: Ranged, Magic");
		if (choice < 0) {
			return;
		}

		int path = choice == 0 ? VoidPath.WARRIOR : choice == 1 ? VoidPath.FORAGER : VoidPath.ARCANIST;
		VoidPath.choose(player, path);
		player.message(VoidPath.name(path) + " chosen. Listed skills now earn 2x XP.");
		player.teleport(player.getConfig().RESPAWN_LOCATION_X, player.getConfig().RESPAWN_LOCATION_Y, true);
	}

}
