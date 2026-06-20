package com.openrsc.server.plugins.custom.minigames.undeadsiege;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class UndeadSiegeNpcDialogue {
	public static void handle(Player player, Npc npc) {
		npcsay(player, npc,
			"The dead stir beneath the Void.",
			"Fight them in waves, earn points, and buy supplies between rounds.");

		int choice = multi(player, npc,
			"Start solo",
			"Start with nearby party",
			"How does it work?",
			"Nevermind.");

		if (choice == 0) {
			UndeadSiegeMinigame.startSolo(player);
		} else if (choice == 1) {
			UndeadSiegeMinigame.startParty(player);
		} else if (choice == 2) {
			npcsay(player, npc,
				"You enter with a temporary safe kit.",
				"Undead kills earn points only for this run.",
				"After each wave, spend points on food, potions, arrows, or runes.",
				"When the run ends, your real gear returns and you receive a payout.");
		}
	}

	public static void explain(Player player, Npc npc) {
		npcsay(player, npc,
			"You enter with a temporary safe kit.",
			"Undead kills earn points only for this run.",
			"After each wave, spend points on food, potions, arrows, or runes.",
			"When the run ends, your real gear returns and you receive a payout.");
	}

	private UndeadSiegeNpcDialogue() {
	}
}
