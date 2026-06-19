package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class VoidArenaHerald implements TalkNpcTrigger, OpNpcTrigger {
	private static final String LEADERBOARD_COMMAND = "Leaderboard";

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isHerald(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (isHerald(npc)) {
			player.getWorld().getVoidArena().sendLeaderboard(player);
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isHerald(npc) && command != null && command.equalsIgnoreCase(LEADERBOARD_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (blockOpNpc(player, npc, command)) {
			player.getWorld().getVoidArena().sendLeaderboard(player);
		}
	}

	private boolean isHerald(Npc npc) {
		return npc != null && npc.getID() == NpcId.VOID_ARENA_HERALD.id();
	}
}
