package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidVeteranTour;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.TakeObjTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class VoidArchivist implements TalkNpcTrigger, TakeObjTrigger {
	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.VOID_ARCHIVIST.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		VoidVeteranTour.run(player, npc);
	}

	@Override
	public boolean blockTakeObj(Player player, GroundItem groundItem) {
		return VoidVeteranTour.isBeamDemoItem(groundItem);
	}

	@Override
	public void onTakeObj(Player player, GroundItem groundItem) {
		VoidVeteranTour.onBeamDemoTake(player, groundItem);
	}
}
