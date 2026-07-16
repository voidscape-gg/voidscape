package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.content.dueljournal.DuelJournalService;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.CommandTrigger;

/** Opens the authenticated player's private duel journal. */
public final class DuelJournal implements CommandTrigger {
	@Override
	public boolean blockCommand(final Player player, final String command, final String[] args) {
		return command.equalsIgnoreCase("duel");
	}

	@Override
	public void onCommand(final Player player, final String command, final String[] args) {
		if (args.length > 1) {
			player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::duel [receipt-id]");
			return;
		}
		Long selectedDuelId = null;
		if (args.length == 1) {
			try {
				selectedDuelId = Long.parseLong(args[0]);
			} catch (final NumberFormatException ex) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::duel [receipt-id]");
				return;
			}
			if (selectedDuelId <= 0) {
				player.message(player.getConfig().BAD_SYNTAX_PREFIX + "::duel [receipt-id]");
				return;
			}
		}
		DuelJournalService.request(player, selectedDuelId);
	}
}
