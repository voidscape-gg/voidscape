package com.openrsc.server.net.rsc.handlers;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Inventory;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.container.ItemKillLog;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.net.rsc.PayloadProcessor;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.ItemExamineRequestStruct;

/**
 * Resolves an inventory slot to an Item instance and renders examine text.
 * For Rune 2H instances with a populated kill_log, augments the static
 * description with the per-instance kill summary; everything else falls
 * through to {@link com.openrsc.server.external.ItemDefinition#getDescription()}.
 */
public class ItemExamineRequest implements PayloadProcessor<ItemExamineRequestStruct, OpcodeIn> {

	@Override
	public void process(final ItemExamineRequestStruct payload, final Player player) throws Exception {
		final Inventory inv = player.getCarriedItems().getInventory();
		if (payload.slot < 0 || payload.slot >= inv.size()) return;
		final Item item = inv.get(payload.slot);
		if (item == null) return;

		final String staticDesc = item.getDef(player.getWorld()).getDescription();
		if (item.getCatalogId() != ItemId.RUNE_2_HANDED_SWORD.id()) {
			ActionSender.sendMessage(player, staticDesc);
			return;
		}

		final ItemKillLog log = ItemKillLog.parse(item.getItemStatus().getKillLog());
		if (log.isEmpty()) {
			ActionSender.sendMessage(player, staticDesc);
			return;
		}

		int npcTotal = 0;
		for (Integer count : log.getNpcKills().values()) npcTotal += count;
		final int pvpTotal = log.getPvpKills();

		final String summary = staticDesc + " Has slain " + npcTotal
			+ " NPC" + (npcTotal == 1 ? "" : "s")
			+ " and " + pvpTotal + " player" + (pvpTotal == 1 ? "" : "s")
			+ " in combat.";
		ActionSender.sendMessage(player, summary);
	}
}
