package com.openrsc.server.model.action;

import com.openrsc.server.model.PathValidation;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;

public abstract class WalkToMobAction extends WalkToAction {
	protected final Mob mob;
	private final int radius;
	private final boolean ignoreProjectileAllowed;
	private final ActionType actionType;

	public WalkToMobAction(final Player owner, final Mob mob, final int radius) {
		this(owner, mob, radius, true, ActionType.OTHER);
	}

	public WalkToMobAction(final Player owner, final Mob mob, final int radius, final boolean ignoreProjectileAllowed, final ActionType actionType) {
		super(owner, mob.getLocation());
		this.mob = mob;
		this.radius = radius;
		this.ignoreProjectileAllowed = ignoreProjectileAllowed;
		this.actionType = actionType;
	}

	public Mob getMob() {
		return mob;
	}

	public ActionType getActionType() {
		return actionType;
	}

	@Override
	public boolean shouldExecuteInternal() {
		/*
		This was seriously weird in RSC. Interactions were 1 tile, but the game would check one tile ahead to see if that point is in range.
		However, the pathing validation didn't consider diagonal blocking for non-projectile based actions.
		This causes authentic weird behaviour like being able to walk through scenery if there is a gap one tile either side of the mob for attacking.
		Some interactions work like this in RS2 as well.
		 */
		boolean actionExecutedThisTick = isWithinInteractionReach(
			getPlayer(), mob, radius, ignoreProjectileAllowed);
		if (actionType == ActionType.ATTACKMAGIC && getPlayer().inCombat() && !actionExecutedThisTick) {
			//If the player attempted to cast magic, is in combat, and was not able to cast it, we should clear it since it was unsuccessful.
			getPlayer().setWalkToAction(null);
		}
		return actionExecutedThisTick;
	}

	/**
	 * The authoritative RSC walk-to-mob reach check. Interactions inspect the
	 * player's next legal queued movement for melee-style actions, then apply the
	 * same terrain boundary validation used by normal PvP.
	 */
	public static boolean isWithinInteractionReach(
			Player player, Mob mob, int radius, boolean ignoreProjectileAllowed) {
		Point checkedPoint = ignoreProjectileAllowed
			? player.getWalkingQueue().getNextMovement()
			: player.getLocation();
		return isWithinInteractionReach(
			player.getWorld(), checkedPoint, mob.getLocation(), radius, ignoreProjectileAllowed);
	}

	public static boolean isWithinInteractionReach(
			World world, Point checkedPoint, Point mobPoint, int radius,
			boolean ignoreProjectileAllowed) {
		return checkedPoint.withinRange(mobPoint, radius)
			&& PathValidation.checkAdjacentDistance(
				world, checkedPoint, mobPoint,
				ignoreProjectileAllowed, !ignoreProjectileAllowed);
	}

	@Override
	public boolean isPvPAttack() {
		return mob.isPlayer() && (actionType == ActionType.ATTACK || actionType == ActionType.ATTACKMAGIC);
	}
}
