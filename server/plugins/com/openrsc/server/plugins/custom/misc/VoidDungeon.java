package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.container.Item;
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
 * persistent Wilderness area on the black-void underground (Floors 3 and 2) — a place players gather to
 * grind the void mobs (ids 854-860 + Void Knight 853) on a room-by-room difficulty gradient up to
 * the Void Demon. There is no instancing: the engine handles NPC respawns (respawnTime in the
 * NpcDef) and everyone shares the same dungeon.
 */
public class VoidDungeon implements OpLocTrigger {

	private static final int VOID_RIFT_ID = 1306;
	private static final int LADDER_UP_ID = 5;
	private static final int LADDER_DOWN_ID = 6;
	private static final int ENTRY_FEE_COINS = 100000;

	// Unsafe Wilderness entry rift, just north of the Void Enclave safe-zone wall.
	private static final int WILDERNESS_RIFT_X = 112;
	private static final int WILDERNESS_RIFT_Y = 296;
	// Where players arrive inside the dungeon entry vestibule.
	private static final int ARRIVAL_X = 72;
	private static final int ARRIVAL_Y = 3252;

	// Dungeon-side exit rifts: entrance fallback, lower checkpoint, upper landing, boss room.
	private static final int[][] DUNGEON_RIFTS = {
		{72, 3250},
		{68, 3204},
		{68, 2308},
		{78, 2251}
	};
	private static final int LOWER_STAIRS_X = 72;
	private static final int LOWER_STAIRS_Y = 3204;
	private static final int UPPER_STAIRS_X = 72;
	private static final int UPPER_STAIRS_Y = 2308;
	// Return players to the unsafe surface side of the entrance, not back into the Enclave.
	private static final int RETURN_X = 112;
	private static final int RETURN_Y = 297;

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isWildernessRift(obj) || isDungeonRift(obj) || isLowerStairs(obj) || isUpperStairs(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isWildernessRift(obj)) {
			if (!player.getConfig().WANT_VOID_DUNGEON) {
				player.message("The Void Dungeon rift is dormant for now.");
				return;
			}
			if (!canUseRift(player)) {
				return;
			}
			if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) < ENTRY_FEE_COINS) {
				player.message("You need 100,000 coins to enter the Void Dungeon.");
				return;
			}
			player.message("The rift demands 100,000 coins to enter the Void Dungeon.");
			int option = multi(player, "Enter the Void Dungeon", "Stay here");
			if (option != 0 || !canUseRift(player)) {
				return;
			}
			if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id()) < ENTRY_FEE_COINS) {
				player.message("You need 100,000 coins to enter the Void Dungeon.");
				return;
			}
			player.getCarriedItems().remove(new Item(ItemId.COINS.id(), ENTRY_FEE_COINS));
			player.message("The rift takes 100,000 coins.");
			travel(player, ARRIVAL_X, ARRIVAL_Y, "The void swallows you and opens onto a black abyss.");
		} else if (isDungeonRift(obj)) {
			if (!canUseRift(player)) {
				return;
			}
			player.message("The rift leads back to the Wilderness above.");
			int option = multi(player, "Leave the Void Dungeon", "Stay here");
			if (option != 0 || !canUseRift(player)) {
				return;
			}
			travel(player, RETURN_X, RETURN_Y, "The void releases you back into the Wilderness.");
		} else if (isLowerStairs(obj)) {
			climb(player, UPPER_STAIRS_X, UPPER_STAIRS_Y, "You climb into the upper gallery of the Void Dungeon.");
		} else if (isUpperStairs(obj)) {
			climb(player, LOWER_STAIRS_X, LOWER_STAIRS_Y, "You climb back down to the lower floor.");
		}
	}

	private boolean canUseRift(Player player) {
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

	private void climb(Player player, int x, int y, String arriveMessage) {
		player.message("You climb the void-stained ladder.");
		delay(1);
		player.teleport(x, y, false);
		player.message(arriveMessage);
	}

	private boolean isWildernessRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && obj.getX() == WILDERNESS_RIFT_X && obj.getY() == WILDERNESS_RIFT_Y;
	}

	private boolean isDungeonRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID && isAtAny(obj, DUNGEON_RIFTS);
	}

	private boolean isLowerStairs(GameObject obj) {
		return obj.getID() == LADDER_UP_ID && obj.getX() == LOWER_STAIRS_X && obj.getY() == LOWER_STAIRS_Y;
	}

	private boolean isUpperStairs(GameObject obj) {
		return obj.getID() == LADDER_DOWN_ID && obj.getX() == UPPER_STAIRS_X && obj.getY() == UPPER_STAIRS_Y;
	}

	private boolean isAtAny(GameObject obj, int[][] points) {
		for (int[] point : points) {
			if (obj.getX() == point[0] && obj.getY() == point[1]) {
				return true;
			}
		}
		return false;
	}
}
