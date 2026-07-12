package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;
import static com.openrsc.server.plugins.Functions.say;

public final class VoidAcolyte implements TalkNpcTrigger, OpNpcTrigger {

	private static final int VOID_ACOLYTE_ID = 842;
	private static final String COMMUNE_COMMAND = "Commune";

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isVoidAcolyte(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isVoidAcolyte(npc)) return;
		commune(player, npc);
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isVoidAcolyte(npc)
			&& command != null
			&& command.equalsIgnoreCase(COMMUNE_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) return;
		commune(player, npc);
	}

	private void commune(Player player, Npc npc) {
		npcsay(player, npc,
			"The web parts for steel, not for faith.",
			"Speak softly. The stones remember blood.");

		int option = multi(player, npc, false,
			"What is this place?",
			"Is it safe inside?",
			"Why seal the gates with webs?",
			"Never mind");

		if (option == 0) {
			say(player, npc, "What is this place?");
			npcsay(player, npc,
				"A wound in the Wilderness.",
				"We built walls around it, not to keep danger out.",
				"To keep pilgrims from walking straight in.");
		} else if (option == 1) {
			say(player, npc, "Is it safe inside?");
			npcsay(player, npc,
				"Inside the walls, blades are stilled.",
				"Past the web, the Wilderness takes its due.");
		} else if (option == 2) {
			say(player, npc, "Why seal the gates with webs?");
			npcsay(player, npc,
				"Stone invites armies. Webs invite knives.",
				"Anyone desperate enough to cut through belongs here for a moment.");
		} else {
			npcsay(player, npc, "Then keep your prayer short.");
		}
	}

	private boolean isVoidAcolyte(Npc npc) {
		return npc != null && npc.getID() == VOID_ACOLYTE_ID;
	}
}
