package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.custom.minigames.voidrush.VoidRushNpcDialogue;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

public final class VoidHerald implements TalkNpcTrigger {

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.VOID_HERALD.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (npc.getID() != NpcId.VOID_HERALD.id()) {
			return;
		}

		if (VoidPath.hasChosen(player)) {
			VoidRushNpcDialogue.handle(player, npc);
			return;
		}

		npcsay(player, npc,
			"choose your path",
			"each path gives 2x experience in its listed skills until level " + VoidPath.BOOST_LEVEL_CAP,
			"I will also give you a starter kit suited to that path");

		int choice = multi(player, npc, false,
			"Warrior's Path - 2x XP: Attack, Defense, Strength to 50 + melee kit",
			"Forager's Path - 2x XP: Fishing, Cooking, Mining to 50 + gathering kit",
			"Arcanist's Path - 2x XP: Ranged, Magic to 50 + arcane kit");
		if (choice < 0) {
			return;
		}

		int path = choice == 0 ? VoidPath.WARRIOR : choice == 1 ? VoidPath.FORAGER : VoidPath.ARCANIST;
		VoidPath.choose(player, path);
		boolean kitGranted = VoidPath.grantStarterKit(player, path);
		ActionSender.sendGameSettings(player);
		player.save();
		player.message(VoidPath.name(path) + " chosen. " + VoidPath.boostedSkillSummary(path) + " now earn " + VoidPath.boostLimitSummary() + ".");
		if (kitGranted) {
			player.message("Your starter kit has been placed in your backpack.");
		}
		player.teleport(player.getConfig().RESPAWN_LOCATION_X, player.getConfig().RESPAWN_LOCATION_Y, true);
		ActionSender.sendBox(player, "@yel@" + VoidPath.name(path) + " chosen.% %"
			+ "@whi@" + VoidPath.boostLimitSummary() + ": @gre@" + VoidPath.boostedSkillSummary(path) + "@whi@.%"
			+ "@whi@Starter kit: @cya@" + VoidPath.starterKitSummary(path) + "@whi@.% %"
			+ "@whi@You have arrived in Lumbridge. Open your backpack, equip anything useful, and start exploring.", true);
	}

}
