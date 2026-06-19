package com.openrsc.server.plugins.custom.minigames.voidrush;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidRushNpcDialogue {
	public static void handle(Player player, Npc npc) {
		npcsay(player, npc, "The Void is restless. Do you think you can survive the rush?");

		int choice = multi(player, npc,
			"I want to enter Void Rush.",
			"How does it work?",
			"Nevermind.");

		if (choice == 0) {
			VoidRushMinigame.joinQueue(player);
		} else if (choice == 1) {
			npcsay(player, npc,
				"Void waves will tear across the arena.",
				"Each wave has a gap.",
				"Stand in the gap or be swallowed.",
				"Last survivor wins.");
		}
	}

	private VoidRushNpcDialogue() {
	}
}
