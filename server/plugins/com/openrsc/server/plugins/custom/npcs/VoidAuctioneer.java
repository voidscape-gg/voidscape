package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.IronmanMode;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.*;

public final class VoidAuctioneer implements TalkNpcTrigger, OpNpcTrigger {

	private static final int VOID_AUCTIONEER = NpcId.VOID_AUCTIONEER.id();
	private static final String COMMAND = "Auction";

	@Override
	public boolean blockTalkNpc(final Player player, final Npc npc) {
		return npc.getID() == VOID_AUCTIONEER;
	}

	@Override
	public void onTalkNpc(final Player player, final Npc npc) {
		npcsay(player, npc, "The void carries every offering, and every coin.");
		int menu = multi(player, npc, "I'd like to browse the auction house.");
		if (menu == 0) {
			openAuctionHouse(player, npc);
		}
	}

	@Override
	public boolean blockOpNpc(final Player player, final Npc npc, final String command) {
		return npc.getID() == VOID_AUCTIONEER && command.equalsIgnoreCase(COMMAND);
	}

	@Override
	public void onOpNpc(final Player player, final Npc npc, final String command) {
		if (npc.getID() != VOID_AUCTIONEER) return;
		if (!command.equalsIgnoreCase(COMMAND)) return;
		openAuctionHouse(player, npc);
	}

	private void openAuctionHouse(final Player player, final Npc npc) {
		if (player.isIronMan(IronmanMode.Ironman.id()) || player.isIronMan(IronmanMode.Ultimate.id())
			|| player.isIronMan(IronmanMode.Hardcore.id()) || player.isIronMan(IronmanMode.Transfer.id())) {
			player.message("As an Ironman, you cannot use the Auction.");
			return;
		}
		if (!validatebankpin(player, npc)) return;
		player.setAttribute("auctionhouse", true);
		ActionSender.sendOpenAuctionHouse(player);
	}
}
