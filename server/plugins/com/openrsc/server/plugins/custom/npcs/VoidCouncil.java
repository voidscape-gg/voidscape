package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.content.VoidStarterIntro;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class VoidCouncil implements TalkNpcTrigger {
	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return VoidStarterIntro.isCouncil(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		VoidStarterIntro.startDialogue(player, npc);
	}
}
