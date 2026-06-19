package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import static com.openrsc.server.plugins.Functions.*;

public class VoidEnclaveWaystone implements OpLocTrigger {

	private static final int WAYSTONE_ID = 1303;
	private static final int MIN_X = 98, MAX_X = 128;
	private static final int MIN_Y = 300, MAX_Y = 330;
	private static final int EDGEVILLE_X = 217;
	private static final int EDGEVILLE_Y = 449;

	private static boolean isEnclaveWaystone(GameObject obj) {
		if (obj.getID() != WAYSTONE_ID) return false;
		int x = obj.getX(), y = obj.getY();
		return x >= MIN_X && x <= MAX_X && y >= MIN_Y && y <= MAX_Y;
	}

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isEnclaveWaystone(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!isEnclaveWaystone(obj)) return;

		mes("The waystone hums under your hand");
		delay(2);
		player.teleport(EDGEVILLE_X, EDGEVILLE_Y, true);
		player.message("The void releases you near Edgeville.");
	}
}
