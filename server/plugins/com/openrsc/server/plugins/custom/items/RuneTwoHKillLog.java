package com.openrsc.server.plugins.custom.items;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemKillLog;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerKilledPlayerTrigger;

public final class RuneTwoHKillLog implements KillNpcTrigger, PlayerKilledPlayerTrigger {

	@Override
	public void onKillNpc(Player player, Npc npc) {
		// Unused: blockKillNpc returns false so this is never dispatched.
	}

	// Why side-effect in block: PluginHandler only invokes onKillNpc when block returns true,
	// but a true return also suppresses Default (which handles loot/XP). For a passive
	// recorder we need to log the kill without taking over the kill flow.
	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		Item weapon = wieldedRuneTwoH(player);
		if (weapon != null) {
			ItemKillLog.recordNpcKillOn(weapon, npc.getID());
		}
		return false;
	}

	@Override
	public void onPlayerKilledPlayer(Player killer, Player killed) {
		// Unused: same dispatch-block pattern as KillNpcTrigger above.
	}

	@Override
	public boolean blockPlayerKilledPlayer(Player killer, Player killed) {
		if (!killed.getLocation().inWilderness()) return false;
		Item weapon = wieldedRuneTwoH(killer);
		if (weapon != null) {
			ItemKillLog.recordPvpKillOn(weapon);
		}
		return false;
	}

	private static Item wieldedRuneTwoH(Player player) {
		final int target = ItemId.RUNE_2_HANDED_SWORD.id();
		if (player.getWorld().getServer().getConfig().WANT_EQUIPMENT_TAB) {
			Item i = player.getCarriedItems().getEquipment().get(4);
			return (i != null && i.getCatalogId() == target) ? i : null;
		}
		synchronized (player.getCarriedItems().getInventory().getItems()) {
			for (Item i : player.getCarriedItems().getInventory().getItems()) {
				if (i.isWielded()
					&& i.getCatalogId() == target
					&& i.getDef(player.getWorld()).getWieldPosition() == 4) {
					return i;
				}
			}
		}
		return null;
	}
}
