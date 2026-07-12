package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.voiddungeon.VoidDungeonLayout;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.entity.player.PlayerDeathContext;
import com.openrsc.server.plugins.ImmediatePlayerLogin;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.PlayerDeathDropTrigger;
import com.openrsc.server.plugins.triggers.PlayerLoginTrigger;

import static com.openrsc.server.plugins.Functions.delay;
import static com.openrsc.server.plugins.Functions.displayTeleportBubble;
import static com.openrsc.server.plugins.Functions.multi;

/**
 * Void Dungeon entry/exit portals.
 *
 * The three traversal stages are shared Wilderness. Admission persists until an underground
 * death, and reaching stages two and three unlocks surface-rift shortcuts for the current
 * admission.
 */
public class VoidDungeon implements OpLocTrigger, PlayerDeathDropTrigger, PlayerLoginTrigger,
	ImmediatePlayerLogin {
	// Retained only as a login migration for characters saved in the removed fourth stage.
	private static final int[][] RETIRED_WYRM_STAGE_RECTS = {
		{106, 1358, 120, 1368},
		{102, 1342, 132, 1354},
		{108, 1326, 136, 1338},
		{116, 1305, 138, 1320},
		{114, 1352, 120, 1360},
		{114, 1336, 120, 1344},
		{118, 1318, 124, 1328},
	};

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return isWildernessRift(obj) || isDungeonRift(obj) || transitionAt(obj) != null;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (!player.atObject(obj)) {
			return;
		}
		if (isWildernessRift(obj)) {
			final Point interactionOrigin = snapshotLocation(player);
			final int interactionInstance = player.getInstanceId();
			if (!player.getConfig().WANT_VOID_DUNGEON) {
				player.message("The Void Dungeon rift is dormant for now.");
				return;
			}
			if (!canUseRift(player)) {
				return;
			}
			final boolean alreadyAdmitted = VoidDungeonAdmission.isAdmitted(player);
			if (!alreadyAdmitted) {
				if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id())
					< VoidDungeonAdmission.ENTRY_FEE_COINS) {
					player.message("You need 100,000 coins to enter the Void Dungeon.");
					return;
				}
				player.message("The rift demands 100,000 coins to enter the Void Dungeon.");
			} else {
				player.message("The rift recognizes your Void Dungeon admission.");
			}
			final int targetStage = chooseEntryStage(player, alreadyAdmitted);
			if (targetStage == VoidDungeonLayout.STAGE_NONE || !canUseRift(player)
				|| !canContinueTravel(player, obj, interactionOrigin, interactionInstance)) {
				return;
			}
			if (!VoidDungeonAdmission.isAdmitted(player)) {
				if (player.getCarriedItems().getInventory().countId(ItemId.COINS.id())
					< VoidDungeonAdmission.ENTRY_FEE_COINS) {
					player.message("You need 100,000 coins to enter the Void Dungeon.");
					return;
				}
				if (!VoidDungeonAdmission.purchaseAndPersist(player)) {
					return;
				}
				player.message("The rift takes 100,000 coins.");
			}
			if (!canContinueTravel(player, obj, interactionOrigin, interactionInstance)) {
				return;
			}
			travel(player, obj, entryX(targetStage), entryY(targetStage),
				"The void swallows you and opens into the "
					+ VoidDungeonLayout.stageName(targetStage) + ".");
		} else if (isDungeonRift(obj)) {
			final Point interactionOrigin = snapshotLocation(player);
			final int interactionInstance = player.getInstanceId();
			if (!canUseRift(player)) {
				return;
			}
			player.message("The rift leads back to the Wilderness above.");
			int option = multi(player, "Leave the Void Dungeon", "Stay here");
			if (option != 0 || !canUseRift(player)
				|| !canContinueTravel(player, obj, interactionOrigin, interactionInstance)) {
				return;
			}
			travel(player, obj, VoidDungeonLayout.SURFACE_RETURN_X,
				VoidDungeonLayout.SURFACE_RETURN_Y,
				"The void releases you back into the Wilderness.");
		} else {
			final VoidDungeonLayout.Transition transition = transitionAt(obj);
			if (transition != null) {
				if (!canUseRift(player)) {
					return;
				}
				climb(player, obj, transition);
			}
		}
	}

	@Override
	public void onPlayerDeathDrop(Player player, PlayerDeathContext context) {
		handleDeath(context.getDeathPoint(), () -> VoidDungeonAdmission.clear(player),
			context::requestPersistence);
	}

	@Override
	public boolean blockPlayerDeathDrop(Player player, PlayerDeathContext context) {
		return shouldClearAdmissionOnDeath(context.getDeathPoint());
	}

	static boolean handleDeath(Point deathPoint, Runnable clearAdmission,
		Runnable requestPersistence) {
		if (!shouldClearAdmissionOnDeath(deathPoint)) {
			return false;
		}
		clearAdmission.run();
		requestPersistence.run();
		return true;
	}

	static boolean shouldClearAdmissionOnDeath(Point deathPoint) {
		return deathPoint != null && deathPoint.inVoidDungeonUnderground();
	}

	@Override
	public boolean blockPlayerLogin(Player player) {
		return player != null && (inRetiredWyrmStage(player.getLocation())
			|| !player.getConfig().WANT_VOID_DUNGEON
				&& player.getLocation().inVoidDungeonUnderground());
	}

	@Override
	public void onPlayerLogin(Player player) {
		if (!blockPlayerLogin(player)) {
			return;
		}
		player.setInstanceId(0);
		if (inRetiredWyrmStage(player.getLocation()) && player.getConfig().WANT_VOID_DUNGEON) {
			player.setInitialLocation(Point.location(VoidDungeonLayout.NULL_SANCTUM_ARRIVAL_X,
				VoidDungeonLayout.NULL_SANCTUM_ARRIVAL_Y));
			player.message("The retired threshold folds back into the Null Sanctum.");
			return;
		}
		player.setInitialLocation(Point.location(VoidDungeonLayout.SURFACE_RETURN_X,
			VoidDungeonLayout.SURFACE_RETURN_Y));
		player.message("The dormant Void Dungeon releases you to the Wilderness above.");
	}

	private boolean inRetiredWyrmStage(Point point) {
		if (point == null) {
			return false;
		}
		for (int[] rectangle : RETIRED_WYRM_STAGE_RECTS) {
			if (point.getX() >= rectangle[0] && point.getX() <= rectangle[2]
				&& point.getY() >= rectangle[1] && point.getY() <= rectangle[3]) {
				return true;
			}
		}
		return false;
	}

	private boolean canUseRift(Player player) {
		if (player.inCombat()) {
			player.message("You cannot enter the rift while fighting.");
			return false;
		}
		return true;
	}

	private void travel(Player player, GameObject source, int x, int y, String arriveMessage) {
		final Point origin = snapshotLocation(player);
		final int instanceId = player.getInstanceId();
		player.message("You step into the Void Rift.");
		displayTeleportBubble(player, player.getX(), player.getY(), false);
		delay(2);
		if (!canContinueTravel(player, source, origin, instanceId)) {
			messageInterruptedTravel(player, "The rift's pull breaks before it can carry you.");
			return;
		}
		player.teleport(x, y, true);
		player.message(arriveMessage);
	}

	private void climb(Player player, GameObject source, VoidDungeonLayout.Transition transition) {
		final Point origin = snapshotLocation(player);
		final int instanceId = player.getInstanceId();
		player.message("You climb the void-stained ladder.");
		delay(1);
		if (!canContinueTravel(player, source, origin, instanceId)) {
			messageInterruptedTravel(player, "You lose your hold on the void-stained ladder.");
			return;
		}
		player.teleport(transition.getTargetX(), transition.getTargetY(), false);
		if (transition.getTargetStage() > transition.getSourceStage()
			&& VoidDungeonAdmission.discoverDepth(player, transition.getTargetStage())) {
			player.requestPersistentSave();
			if (transition.getTargetStage() <= VoidDungeonLayout.STAGE_NULL_SANCTUM) {
				player.message("You can now resume from the "
					+ VoidDungeonLayout.stageName(transition.getTargetStage())
					+ " while this admission lasts.");
			}
		}
		player.message("You enter the " + VoidDungeonLayout.stageName(transition.getTargetStage()) + ".");
	}

	private int chooseEntryStage(Player player, boolean alreadyAdmitted) {
		if (!alreadyAdmitted) {
			return multi(player, "Enter the Void Dungeon", "Stay here") == 0
				? VoidDungeonLayout.STAGE_RIFTWORKS
				: VoidDungeonLayout.STAGE_NONE;
		}

		final int discovered = VoidDungeonAdmission.getDiscoveredDepth(player);
		if (discovered >= VoidDungeonLayout.STAGE_NULL_SANCTUM) {
			switch (multi(player, "Enter the Riftworks", "Resume at the Broken Menagerie",
				"Resume at the Null Sanctum", "Stay here")) {
				case 0: return VoidDungeonLayout.STAGE_RIFTWORKS;
				case 1: return VoidDungeonLayout.STAGE_BROKEN_MENAGERIE;
				case 2: return VoidDungeonLayout.STAGE_NULL_SANCTUM;
				default: return VoidDungeonLayout.STAGE_NONE;
			}
		}
		if (discovered >= VoidDungeonLayout.STAGE_BROKEN_MENAGERIE) {
			final int option = multi(player, "Enter the Riftworks",
				"Resume at the Broken Menagerie", "Stay here");
			return option == 0 ? VoidDungeonLayout.STAGE_RIFTWORKS
				: option == 1 ? VoidDungeonLayout.STAGE_BROKEN_MENAGERIE
				: VoidDungeonLayout.STAGE_NONE;
		}
		return multi(player, "Enter the Riftworks", "Stay here") == 0
			? VoidDungeonLayout.STAGE_RIFTWORKS
			: VoidDungeonLayout.STAGE_NONE;
	}

	private int entryX(int stage) {
		switch (stage) {
			case VoidDungeonLayout.STAGE_BROKEN_MENAGERIE:
				return VoidDungeonLayout.BROKEN_MENAGERIE_ARRIVAL_X;
			case VoidDungeonLayout.STAGE_NULL_SANCTUM:
				return VoidDungeonLayout.NULL_SANCTUM_ARRIVAL_X;
			default:
				return VoidDungeonLayout.RIFTWORKS_ARRIVAL_X;
		}
	}

	private int entryY(int stage) {
		switch (stage) {
			case VoidDungeonLayout.STAGE_BROKEN_MENAGERIE:
				return VoidDungeonLayout.BROKEN_MENAGERIE_ARRIVAL_Y;
			case VoidDungeonLayout.STAGE_NULL_SANCTUM:
				return VoidDungeonLayout.NULL_SANCTUM_ARRIVAL_Y;
			default:
				return VoidDungeonLayout.RIFTWORKS_ARRIVAL_Y;
		}
	}

	private Point snapshotLocation(Player player) {
		return Point.location(player.getX(), player.getY());
	}

	private boolean canContinueTravel(Player player, GameObject source, Point origin, int instanceId) {
		return player.loggedIn()
			&& !player.isRemoved()
			&& !player.isLoggingOut()
			&& !player.isUnregistering()
			&& !player.killed
			&& !player.inCombat()
			&& player.atObject(source)
			&& player.getInstanceId() == instanceId
			&& player.getX() == origin.getX()
			&& player.getY() == origin.getY();
	}

	private void messageInterruptedTravel(Player player, String message) {
		if (player.loggedIn() && !player.isRemoved() && !player.isUnregistering()) {
			player.message(message);
		}
	}

	private boolean isWildernessRift(GameObject obj) {
		return obj.getID() == VoidDungeonLayout.VOID_RIFT_ID
			&& obj.getX() == VoidDungeonLayout.SURFACE_RIFT_X
			&& obj.getY() == VoidDungeonLayout.SURFACE_RIFT_Y;
	}

	private boolean isDungeonRift(GameObject obj) {
		return obj.getID() == VoidDungeonLayout.VOID_RIFT_ID
			&& VoidDungeonLayout.isExitRift(obj.getX(), obj.getY());
	}

	private VoidDungeonLayout.Transition transitionAt(GameObject obj) {
		return VoidDungeonLayout.transitionAt(obj.getID(), obj.getX(), obj.getY());
	}
}
