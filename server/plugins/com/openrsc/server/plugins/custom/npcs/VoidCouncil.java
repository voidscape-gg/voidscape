package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.content.VoidOnboarding;
import com.openrsc.server.content.VoidStarterIntro;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.onboarding.VoidWelcome;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class VoidCouncil implements TalkNpcTrigger {
	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return VoidStarterIntro.isCouncil(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		// Dismissed-welcome recovery: council talk re-opens the welcome menu.
		if (VoidOnboarding.needsWelcome(player)) {
			VoidWelcome.showWelcomeMenu(player);
			return;
		}

		boolean neededIntro = VoidStarterIntro.needsIntro(player);
		VoidStarterIntro.startDialogue(player, npc);
		if (neededIntro && !VoidStarterIntro.needsIntro(player)) {
			// Lore just completed (e.g. after a mid-lore logout) — continue the chosen track.
			VoidWelcome.continueAfterLore(player);
		}
	}
}
