package com.openrsc.server.plugins.custom.commands;

import com.openrsc.server.external.GameObjectDef;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.action.ActionType;
import com.openrsc.server.model.action.WalkToMobAction;
import com.openrsc.server.model.action.WalkToObjectAction;
import com.openrsc.server.model.action.WalkToPointAction;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.npc.NpcInteraction;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.CommandTrigger;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TakeObjTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

/**
 * Voidscape dev/testing commands. These drive the REAL action paths (attack, operate-object, take,
 * talk, npc-option, drop) by id/coords instead of pixel-precise mouse clicks, so headless and
 * workbench testing of custom content (the Void Colossus arena, portals, drops, etc.) doesn't depend
 * on finding sprites on screen or hitting menu rows. Each command reproduces the same WalkTo action +
 * trigger dispatch the packet handlers use, so plugins fire authentically. Dev-only.
 *
 * Commands:
 *   ::atnpc <npcId>            walk to + melee-attack the nearest NPC of that id
 *   ::atobject <objId> [2]     walk to + operate the nearest scenery of that id (2 = right-click cmd)
 *   ::walkto <x> <y>           walk to world tile (x,y) through real pathing
 *   ::npcinfo [npcId]          print HP / combat / coords of the nearest matching NPC
 *   ::damagenpc <npcId> <amt>  drain HP from the nearest NPC of that id
 *   ::killnpc <npcId>          kill the nearest NPC of that id through the real death path
 *   ::take <itemId>            walk to + take the nearest ground item of that id (TakeObjTrigger)
 *   ::grounditems [radius]     list nearby ground items (id, name, coords, amount, FFA/owner)
 *   ::talknpc <npcId>          walk to + talk to the nearest NPC of that id (TalkNpcTrigger)
 *   ::opnpc <npcId> [cmd|2]    walk to + operate the nearest NPC's menu command (OpNpcTrigger)
 *   ::dropinv <itemId>         drop the first matching inventory item (real drop path)
 *   ::colossus                 enter a fresh Void Colossus solo instance (real spawn/teleport path)
 *   ::colossuspeace [on|off]   toggle the boss dealing no damage (inspect angles/size safely)
 */
public final class VoidTestCommands implements CommandTrigger {

	@Override
	public boolean blockCommand(Player player, String command, String[] args) {
		return player.isDev();
	}

	@Override
	public void onCommand(Player player, String command, String[] args) {
		switch (command.toLowerCase()) {
			case "atnpc":
			case "attacknpc":
				attackNpc(player, args);
				break;
			case "atobject":
			case "atobj":
			case "oploc":
			case "usescenery":
				operateObject(player, args);
				break;
			case "walkto":
				walkTo(player, args);
				break;
			case "npcinfo":
				npcInfo(player, args);
				break;
			case "damagenpc":
				damageNpc(player, args, false);
				break;
			case "killnpc":
				damageNpc(player, args, true);
				break;
			case "take":
			case "takeitem":
			case "pickup":
				takeItem(player, args);
				break;
			case "grounditems":
			case "gi":
				groundItems(player, args);
				break;
			case "talknpc":
				talkNpc(player, args);
				break;
			case "opnpc":
				opNpc(player, args);
				break;
			case "dropinv":
			case "dropitem":
				dropInv(player, args);
				break;
			case "colossus":
			case "voidcolossus":
				if (com.openrsc.server.plugins.custom.minigames.voidcolossus.VoidColossusArena.devEnter(player)) {
					player.message("@gre@Entering a fresh Void Colossus instance.");
				} else {
					player.message("@or1@Void Colossus arena is not loaded.");
				}
				break;
			case "colossuspeace":
				boolean peace = args.length >= 1
					? (args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("true"))
					: !com.openrsc.server.plugins.custom.minigames.voidcolossus.ColossusBossEvent.PEACEFUL;
				com.openrsc.server.plugins.custom.minigames.voidcolossus.ColossusBossEvent.PEACEFUL = peace;
				player.message("@gre@Void Colossus peaceful mode: " + (peace ? "ON (no damage)" : "OFF"));
				break;
			default:
				break;
		}
	}

	/** Walk to + take the nearest visible ground item of an id, via the real TakeObjTrigger path. */
	private void takeItem(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::take <itemId>");
			return;
		}
		final int id = safeInt(args[0], -1);
		if (id < 0) {
			player.message("@or1@Bad item id: " + args[0]);
			return;
		}
		GroundItem best = null;
		int bestDist = Integer.MAX_VALUE;
		for (GroundItem gi : player.getViewArea().getItemsInView()) {
			if (gi == null || gi.getID() != id || gi.isRemoved() || gi.isInvisibleTo(player)) {
				continue;
			}
			int dist = player.getLocation().getDistancePythagoras(gi.getLocation());
			if (dist < bestDist) {
				bestDist = dist;
				best = gi;
			}
		}
		if (best == null) {
			player.message("@or1@No visible ground item with id " + id + ".");
			return;
		}
		final GroundItem item = best;
		player.resetAll();
		player.setWalkToAction(new WalkToPointAction(player, item.getLocation(), 0) {
			public void executeInternal() {
				if (item.isInvisibleTo(getPlayer()) || item.isRemoved() || !getPlayer().canReach(item)) {
					return;
				}
				getPlayer().resetAll();
				getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(
					TakeObjTrigger.class, getPlayer(), new Object[]{getPlayer(), item}, this);
			}
		});
		player.message("@gre@Taking item id " + id + " at (" + item.getX() + "," + item.getY() + ").");
	}

	/** List ground items near the player (default radius 15). */
	private void groundItems(Player player, String[] args) {
		int radius = args.length >= 1 ? safeInt(args[0], 15) : 15;
		int count = 0;
		for (GroundItem gi : player.getViewArea().getItemsInView()) {
			if (gi == null || gi.isRemoved() || gi.isInvisibleTo(player)) {
				continue;
			}
			if (player.getLocation().getDistancePythagoras(gi.getLocation()) > radius) {
				continue;
			}
			boolean ffa = gi.getOwnerUsernameHash() == 0;
			player.message("@cya@" + gi.getDef().getName()
				+ " @whi@id=" + gi.getID() + " x" + gi.getAmount()
				+ " @whi@(" + gi.getX() + "," + gi.getY() + ") "
				+ (ffa ? "@gre@FFA" : "@or1@owned"));
			count++;
		}
		if (count == 0) {
			player.message("@or1@No ground items within " + radius + " tiles.");
		} else {
			player.message("@gre@" + count + " ground item(s) listed.");
		}
	}

	/** Walk to + talk to the nearest NPC of an id, via the real TalkNpcTrigger path. */
	private void talkNpc(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::talknpc <npcId>");
			return;
		}
		final int id = safeInt(args[0], -1);
		Npc npc = id < 0 ? null : nearestNpc(player, id);
		if (npc == null) {
			player.message("@or1@No visible npc with id " + id + ".");
			return;
		}
		player.resetAll();
		player.setFollowing(npc, 1, true, true);
		player.setWalkToAction(new WalkToMobAction(player, npc, 1) {
			public void executeInternal() {
				if (getPlayer().isBusy() || getPlayer().isRanging()) {
					return;
				}
				getPlayer().resetAll(true, false);
				NpcInteraction.setInteractions((Npc) mob, getPlayer(), NpcInteraction.NPC_TALK_TO);
				getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(
					TalkNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob});
			}
		});
		player.message("@gre@Talking to " + npc.getDef().getName() + " (id " + id + ").");
	}

	/** Walk to + operate the nearest NPC's menu command (OpNpcTrigger). Arg can be a command word or "2". */
	private void opNpc(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::opnpc <npcId> [command|2]");
			return;
		}
		final int id = safeInt(args[0], -1);
		Npc npc = id < 0 ? null : nearestNpc(player, id);
		if (npc == null) {
			player.message("@or1@No visible npc with id " + id + ".");
			return;
		}
		final boolean second = args.length >= 2 && args[1].equals("2");
		final String explicit = (args.length >= 2 && !second) ? args[1].toLowerCase() : null;
		player.resetAll();
		player.click = second ? 1 : 0;
		player.setFollowing(npc, 1, true, true);
		player.setWalkToAction(new WalkToMobAction(player, npc, 1) {
			public void executeInternal() {
				if (getPlayer().isBusy() || getPlayer().isRanging()) {
					return;
				}
				getPlayer().resetAll(true, false);
				NpcInteraction.setInteractions((Npc) mob, getPlayer(), NpcInteraction.NPC_OP);
				String command = explicit != null ? explicit
					: (getPlayer().click == 0 ? ((Npc) mob).getDef().getCommand1() : ((Npc) mob).getDef().getCommand2()).toLowerCase();
				getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(
					OpNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob, command}, this);
			}
		});
		player.message("@gre@Operating " + npc.getDef().getName() + " (id " + id + ").");
	}

	/** Drop the first inventory item matching an id, via the real drop event path. */
	private void dropInv(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::dropinv <itemId>");
			return;
		}
		final int id = safeInt(args[0], -1);
		int slot = id < 0 ? -1 : player.getCarriedItems().getInventory().getLastIndexById(id);
		if (slot == -1) {
			player.message("@or1@No inventory item with id " + id + ".");
			return;
		}
		Item invItem = player.getCarriedItems().getInventory().get(slot);
		Item drop = new Item(invItem.getCatalogId(), invItem.getAmount(), invItem.getNoted(), invItem.getItemId());
		player.resetAll();
		player.setDropItemEvent(slot, drop);
		player.runDropEvent(true);
		player.message("@gre@Dropping item id " + id + ".");
	}

	/** Drain HP from (or outright kill) the nearest NPC of an id through the real death path. */
	private void damageNpc(Player player, String[] args, boolean kill) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::" + (kill ? "killnpc <npcId>" : "damagenpc <npcId> <amount>"));
			return;
		}
		final int id;
		try {
			id = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			player.message("@or1@Bad npc id: " + args[0]);
			return;
		}
		Npc npc = nearestNpc(player, id);
		if (npc == null) {
			player.message("@or1@No visible npc with id " + id + ".");
			return;
		}
		int hits = npc.getSkills().getLevel(3);
		int amount = kill ? hits : (args.length >= 2 ? safeInt(args[1], 0) : 0);
		int newHits = Math.max(0, hits - amount);
		npc.getSkills().setLevel(3, newHits, false);
		if (newHits <= 0) {
			// Real death path: fires KillNpcTrigger + default drops + respawn.
			npc.killedBy(player);
			player.message("@gre@Killed " + npc.getDef().getName() + " (id " + id + ").");
		} else {
			player.message("@gre@" + npc.getDef().getName() + " HP " + newHits + "/" + npc.getSkills().getMaxStat(3));
		}
	}

	private int safeInt(String s, int fallback) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void attackNpc(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::atnpc <npcId>");
			return;
		}
		final int id;
		try {
			id = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			player.message("@or1@Bad npc id: " + args[0]);
			return;
		}
		Npc npc = nearestNpc(player, id);
		if (npc == null) {
			player.message("@or1@No visible npc with id " + id + ".");
			return;
		}
		if (player.inCombat()) {
			player.message("@or1@You are already busy fighting!");
			return;
		}
		player.resetAll();
		player.setFollowing(npc, 0, false, true);
		// Mirror AttackHandler's melee branch: a WalkToMobAction that fires the real
		// AttackNpcTrigger chain once adjacent (so custom boss carve-outs are exercised).
		player.setWalkToAction(new WalkToMobAction(player, npc, player.getConfig().PVM_CATCHING_DISTANCE, true, ActionType.ATTACK) {
			public void executeInternal() {
				getPlayer().resetFollowing();
				if (mob.inCombat() && getPlayer().getRangeEquip() < 0 && getPlayer().getThrowingEquip() < 0) {
					getPlayer().message("I can't get close enough");
					return;
				}
				if (getPlayer().isBusy() || mob.isBusy() || !getPlayer().checkAttack(mob, false)) {
					return;
				}
				NpcInteraction.setInteractions((Npc) mob, getPlayer(), NpcInteraction.NPC_ATTACK);
				getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(
					AttackNpcTrigger.class, getPlayer(), new Object[]{getPlayer(), (Npc) mob}, this);
			}
		});
		player.message("@gre@Attacking " + npc.getDef().getName() + " (id " + id + ").");
	}

	private void operateObject(Player player, String[] args) {
		if (args.length < 1) {
			player.message("@or1@Syntax: ::atobject <objId> [2]");
			return;
		}
		final int id;
		try {
			id = Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			player.message("@or1@Bad object id: " + args[0]);
			return;
		}
		final boolean secondCommand = args.length >= 2 && args[1].equals("2");
		GameObject object = nearestObject(player, id);
		if (object == null) {
			player.message("@or1@No visible scenery with id " + id + ".");
			return;
		}
		player.resetAll();
		player.click = secondCommand ? 1 : 0;
		// Mirror GameObjectAction: WalkToObjectAction that fires the real OpLocTrigger chain.
		player.setWalkToAction(new WalkToObjectAction(player, object) {
			public void executeInternal() {
				getPlayer().resetPath();
				GameObjectDef def = object.getGameObjectDef();
				if (getPlayer().isBusy() || !getPlayer().atObject(object) || getPlayer().isRanging() || def == null) {
					return;
				}
				getPlayer().resetAll();
				String cmd = (getPlayer().click == 0 ? def.getCommand1() : def.getCommand2()).toLowerCase();
				int playerDirection = getPlayer().getSprite();
				if (getPlayer().getWorld().getServer().getPluginHandler().handlePlugin(
						OpLocTrigger.class, getPlayer(), new Object[]{getPlayer(), object, cmd}, this)) {
					getPlayer().setSprite(playerDirection);
				}
			}
		});
		player.message("@gre@Operating object id " + id + " at (" + object.getX() + "," + object.getY() + ").");
	}

	private void walkTo(Player player, String[] args) {
		if (args.length < 2) {
			player.message("@or1@Syntax: ::walkto <x> <y>");
			return;
		}
		final int x;
		final int y;
		try {
			x = Integer.parseInt(args[0]);
			y = Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			player.message("@or1@Bad coordinates.");
			return;
		}
		player.resetAll();
		player.walkToEntity(x, y);
		player.message("@gre@Walking to (" + x + "," + y + ").");
	}

	private void npcInfo(Player player, String[] args) {
		Npc npc;
		if (args.length >= 1) {
			int id;
			try {
				id = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				player.message("@or1@Bad npc id: " + args[0]);
				return;
			}
			npc = nearestNpc(player, id);
		} else {
			npc = nearestNpc(player, -1);
		}
		if (npc == null) {
			player.message("@or1@No matching npc in view.");
			return;
		}
		player.message("@cya@" + npc.getDef().getName() + " id=" + npc.getID()
			+ " @whi@HP " + npc.getSkills().getLevel(3) + "/" + npc.getSkills().getMaxStat(3)
			+ " @whi@at (" + npc.getX() + "," + npc.getY() + ")"
			+ " @whi@combat=" + npc.inCombat());
	}

	private Npc nearestNpc(Player player, int id) {
		Npc best = null;
		int bestDist = Integer.MAX_VALUE;
		for (Npc npc : player.getViewArea().getNpcsInView()) {
			if (npc == null || npc.isRemoved() || npc.isRespawning()) {
				continue;
			}
			if (id >= 0 && npc.getID() != id) {
				continue;
			}
			int dist = player.getLocation().getDistancePythagoras(npc.getLocation());
			if (dist < bestDist) {
				bestDist = dist;
				best = npc;
			}
		}
		return best;
	}

	private GameObject nearestObject(Player player, int id) {
		GameObject best = null;
		int bestDist = Integer.MAX_VALUE;
		for (GameObject obj : player.getViewArea().getGameObjectsInView()) {
			if (obj == null || obj.getID() != id) {
				continue;
			}
			int dist = player.getLocation().getDistancePythagoras(Point.location(obj.getX(), obj.getY()));
			if (dist < bestDist) {
				bestDist = dist;
				best = obj;
			}
		}
		return best;
	}
}
