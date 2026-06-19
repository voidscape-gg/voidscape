package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.external.ItemDefinition;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.triggers.UseBoundTrigger;
import com.openrsc.server.plugins.triggers.OpBoundTrigger;
import com.openrsc.server.util.rsc.Formulae;

import static com.openrsc.server.plugins.Functions.*;

public class CutWeb implements UseBoundTrigger,
	OpBoundTrigger {

	private static final int WEB = 24;
	private static final int VOID_WEB = 217;   // voidscape: AI-textured perimeter web at Void Enclave gates

	private static boolean isWeb(int id) {
		return id == WEB || id == VOID_WEB;
	}

	private static String targetName(GameObject obj) {
		return obj.getID() == VOID_WEB ? "void web" : "web";
	}

	private static String successMessage(GameObject obj) {
		return obj.getID() == VOID_WEB
			? "You slice through the void web"
			: "You slice through the web";
	}

	@Override
	public boolean blockUseBound(Player player, GameObject obj, Item item) {
		return isWeb(obj.getID());
	}

	@Override
	public void onUseBound(Player player, GameObject obj, Item item) {
		if (isWeb(obj.getID())) {
			ItemDefinition def = item.getDef(player.getWorld());
			String name = def.getName().toLowerCase();
			if (name.contains("staff") || name.contains("bow") || name.contains("cythe") ||
					(def.getWieldPosition() != 4 &&
					item.getCatalogId() != ItemId.KNIFE.id())) {
				player.message("Nothing interesting happens");
				return;
			}
			mes("You try to destroy the " + targetName(obj) + "...");
			delay(3);
			if (Formulae.cutWeb()) {
				player.message(successMessage(obj));
				ActionSender.sendSound(player, "combat1");
				changeloc(obj, 30000, 16);
			} else {
				player.message("You fail to cut through it");
				delay();
			}
		}
	}

	@Override
	public boolean blockOpBound(Player player, GameObject obj, Integer click) {
		return isWeb(obj.getID())
			&& (player.getConfig().WANT_LEFTCLICK_WEBS || obj.getID() == VOID_WEB);
	}

	@Override
	public void onOpBound(Player player, GameObject obj, Integer click) {
		boolean canCut = false;
		//First, check their equipment for an appropriate weapon
		if (config().WANT_EQUIPMENT_TAB) {
			Item weapon = player.getCarriedItems().getEquipment().get(4);
			if (weapon != null)
				canCut = true;
		}

		if (!canCut) {
			//Next check their inventory for an appropriate weapon
			Item inventoryItem = null;
			for (int i = 0; i < player.getCarriedItems().getInventory().size(); i++) {
				inventoryItem = player.getCarriedItems().getInventory().get(i);
				if (inventoryItem != null && inventoryItem.getDef(player.getWorld()).getWieldPosition() == 4)
					canCut = true;
			}

			if (!canCut) {
				//Lastly, check if they have a knife
				if (player.getCarriedItems().hasCatalogID(ItemId.KNIFE.id())) {
					canCut = true;
				}
			}
		}

		if (canCut) {
			mes("You try to destroy the " + targetName(obj) + "...");
			delay(3);
			if (Formulae.cutWeb()) {
				player.message(successMessage(obj));
				delay();
				delloc(obj);
				addloc(obj.getWorld(), obj.getLoc(), 30000);
			} else {
				player.message("You fail to cut through it");
				delay();
			}
		} else {
			player.message("Nothing interesting happens");
			delay();
		}
	}
}
