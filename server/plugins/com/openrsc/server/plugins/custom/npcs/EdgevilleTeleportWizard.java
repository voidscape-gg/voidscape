package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;

public final class EdgevilleTeleportWizard implements OpNpcTrigger {

	private static final int EDGAR_NPC_ID = 836;
	private static final String COMMAND = "Teleport to Edgeville";

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (npc.getID() != EDGAR_NPC_ID) return;
		if (!command.equalsIgnoreCase(COMMAND)) return;
		player.teleport(217, 449, true);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return npc.getID() == EDGAR_NPC_ID && command.equalsIgnoreCase(COMMAND);
	}
}
