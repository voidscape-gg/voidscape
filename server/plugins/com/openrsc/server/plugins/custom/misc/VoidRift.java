package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;

import java.util.Arrays;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.displayTeleportBubble;
import static com.openrsc.server.plugins.Functions.multi;

public class VoidRift implements OpLocTrigger {

	private static final int VOID_RIFT_ID = 1306;
	private static final int CITY_RIFT_X = 139;
	private static final int CITY_RIFT_Y = 636;
	private static final int VOID_ARENA_RIFT_X = 113;
	private static final int VOID_ARENA_RIFT_Y = 321;
	private static final RiftDestination[] DESTINATIONS = new RiftDestination[] {
		new RiftDestination("Void Enclave", 116, 312, 113, 314),
		new RiftDestination("Edgeville", 192, 443, 194, 443),
		new RiftDestination("Varrock", 123, 502, 120, 504),
		new RiftDestination("Falador", 316, 552, 312, 552),
		new RiftDestination("Ardougne", 591, 621, 588, 621)
	};

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isVoidArenaRift(obj) || isVoidRift(obj);
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (isVoidArenaRift(obj)) {
			enterVoidArena(player);
			return;
		}
		if (!isVoidRift(obj)) return;

		if (player.inCombat()) {
			player.message("You cannot enter the Void Rift while fighting.");
			return;
		}

		RiftMenu menu = buildMenu(obj);
		if (menu.options.length == 0) return;

		player.message("The Void Rift hums with distant paths.");
		int option = multi(player, menu.options);
		if (option < 0 || option >= menu.destinations.length || menu.destinations[option] == null) {
			player.message("You step away from the rift.");
			return;
		}
		RiftDestination destination = menu.destinations[option];

		if (player.inCombat()) {
			player.message("You cannot enter the Void Rift while fighting.");
			return;
		}

		player.message("You step into the Void Rift.");
		displayTeleportBubble(player, player.getX(), player.getY(), false);
		delay(2);
		player.teleport(destination.arrivalX, destination.arrivalY, true);
		player.message("The void folds around you and opens near " + destination.name + ".");
	}

	private void enterVoidArena(Player player) {
		if (player.inCombat()) {
			player.message("You cannot enter the Void Rift while fighting.");
			return;
		}

		player.message("The Void Rift opens toward the Void Arena.");
		int option = multi(player, "Enter Void Arena", "Stay here");
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
		player.getWorld().getVoidArena().enterFromVoidEnclaveRift(player);
	}

	private boolean isVoidRift(GameObject obj) {
		if (obj.getID() != VOID_RIFT_ID) return false;
		if (obj.getX() == CITY_RIFT_X && obj.getY() == CITY_RIFT_Y) return true;
		return destinationForRift(obj) != null;
	}

	private boolean isVoidArenaRift(GameObject obj) {
		return obj.getID() == VOID_RIFT_ID
			&& obj.getX() == VOID_ARENA_RIFT_X
			&& obj.getY() == VOID_ARENA_RIFT_Y;
	}

	private RiftDestination destinationForRift(GameObject obj) {
		for (RiftDestination destination : DESTINATIONS) {
			if (obj.getX() == destination.riftX && obj.getY() == destination.riftY) {
				return destination;
			}
		}
		return null;
	}

	private RiftMenu buildMenu(GameObject obj) {
		RiftDestination current = destinationForRift(obj);
		String[] options = new String[DESTINATIONS.length];
		RiftDestination[] menuDestinations = new RiftDestination[DESTINATIONS.length];
		int count = 0;
		for (RiftDestination destination : DESTINATIONS) {
			if (destination == current) continue;
			options[count] = destination.name;
			menuDestinations[count] = destination;
			count++;
		}
		if (current != null && count < options.length) {
			options[count] = "Stay here";
			menuDestinations[count] = null;
			count++;
		}
		return new RiftMenu(Arrays.copyOf(options, count), Arrays.copyOf(menuDestinations, count));
	}

	private static final class RiftDestination {
		final String name;
		final int riftX;
		final int riftY;
		final int arrivalX;
		final int arrivalY;

		RiftDestination(String name, int riftX, int riftY, int arrivalX, int arrivalY) {
			this.name = name;
			this.riftX = riftX;
			this.riftY = riftY;
			this.arrivalX = arrivalX;
			this.arrivalY = arrivalY;
		}
	}

	private static final class RiftMenu {
		final String[] options;
		final RiftDestination[] destinations;

		RiftMenu(String[] options, RiftDestination[] destinations) {
			this.options = options;
			this.destinations = destinations;
		}
	}
}
