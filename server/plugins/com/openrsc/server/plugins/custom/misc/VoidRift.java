package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.displayTeleportBubble;
import static com.openrsc.server.plugins.Functions.multi;

public class VoidRift implements OpLocTrigger {

	private static final int VOID_RIFT_ID = 1306;
	private static final int RIFT_X = 192;
	private static final int RIFT_Y = 443;
	private static final int CITY_RIFT_X = 139;
	private static final int CITY_RIFT_Y = 636;
	private static final int ENCLAVE_X = 113;
	private static final int ENCLAVE_Y = 314;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isVoidRift(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!isVoidRift(obj)) return;

		if (player.inCombat()) {
			player.message("You cannot enter the Void Rift while fighting.");
			return;
		}

		player.message("The Void Rift pulls at the ground beneath you.");
		player.message("Are you sure you want to enter?");
		int option = multi(player, "Enter the rift", "Stay here");
		if (option != 0) {
			player.message("You step away from the rift.");
			return;
		}

		if (player.inCombat()) {
			player.message("You cannot enter the Void Rift while fighting.");
			return;
		}

		player.message("You step into the Void Rift.");
		displayTeleportBubble(player, player.getX(), player.getY(), false);
		delay(2);
		player.teleport(ENCLAVE_X, ENCLAVE_Y, true);
		player.message("The void folds around you and opens into the Void Enclave.");
	}

	private boolean isVoidRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID
			&& ((obj.getX() == RIFT_X && obj.getY() == RIFT_Y)
			|| (obj.getX() == CITY_RIFT_X && obj.getY() == CITY_RIFT_Y));
	}
}
