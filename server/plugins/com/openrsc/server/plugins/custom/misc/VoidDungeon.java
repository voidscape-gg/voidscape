package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.displayTeleportBubble;
import static com.openrsc.server.plugins.Functions.multi;

/**
 * Void Dungeon entry/exit portals.
 *
 * Unlike the Void Colossus arena (a private per-player instance), the Void Dungeon is a SHARED,
 * persistent area on the black-void underground (Floor 2) — a place players gather to grind the
 * void mobs (ids 854-860 + Void Knight 853) on a south->north difficulty gradient up to the Void
 * Demon. There is no instancing: the engine handles NPC respawns (respawnTime in the NpcDef) and
 * everyone shares the same dungeon. So this plugin is just the two rifts that teleport in and out.
 */
public class VoidDungeon implements OpLocTrigger {

	private static final int VOID_RIFT_ID = 1306;

	// Entry rift, standing in the Void Enclave (distinct from the Colossus hub rift at 113,321).
	private static final int ENCLAVE_RIFT_X = 108;
	private static final int ENCLAVE_RIFT_Y = 322;
	// Where players arrive inside the dungeon (south entrance hall).
	private static final int ARRIVAL_X = 72;
	private static final int ARRIVAL_Y = 2552;

	// Exit rift, at the dungeon's south entrance.
	private static final int DUNGEON_RIFT_X = 72;
	private static final int DUNGEON_RIFT_Y = 2554;
	// Where players return to in the Void Enclave.
	private static final int RETURN_X = 108;
	private static final int RETURN_Y = 323;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isEnclaveRift(obj) || isDungeonRift(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isEnclaveRift(obj)) {
			if (!confirmTravel(player, "The rift seethes with the howls of the void below.",
				"Descend into the Void Dungeon?")) {
				return;
			}
			travel(player, ARRIVAL_X, ARRIVAL_Y, "The void swallows you and opens onto a black abyss.");
		} else if (isDungeonRift(obj)) {
			if (!confirmTravel(player, "The rift leads back to the safety of the Void Enclave.",
				"Leave the dungeon?")) {
				return;
			}
			travel(player, RETURN_X, RETURN_Y, "The void releases you back into the Enclave.");
		}
	}

	private boolean confirmTravel(Player player, String intro, String confirm) {
		if (player.inCombat()) {
			player.message("You cannot enter the rift while fighting.");
			return false;
		}
		player.message(intro);
		player.message(confirm);
		int option = multi(player, "Enter the rift", "Stay here");
		if (option != 0) {
			player.message("You step away from the rift.");
			return false;
		}
		if (player.inCombat()) {
			player.message("You cannot enter the rift while fighting.");
			return false;
		}
		return true;
	}

	private void travel(Player player, int x, int y, String arriveMessage) {
		player.message("You step into the Void Rift.");
		displayTeleportBubble(player, player.getX(), player.getY(), false);
		delay(2);
		player.teleport(x, y, true);
		player.message(arriveMessage);
	}

	private boolean isEnclaveRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && obj.getX() == ENCLAVE_RIFT_X && obj.getY() == ENCLAVE_RIFT_Y;
	}

	private boolean isDungeonRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && obj.getX() == DUNGEON_RIFT_X && obj.getY() == DUNGEON_RIFT_Y;
	}
}
