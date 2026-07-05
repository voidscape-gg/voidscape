package com.openrsc.server.plugins.custom.onboarding;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.VoidOnboarding;
import com.openrsc.server.content.VoidPath;
import com.openrsc.server.content.VoidTutorialIsle;
import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerLogoutTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import static com.openrsc.server.plugins.Functions.give;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

/**
 * "I'm new to Classic" — the guided onboarding track, on the dedicated
 * Tutorial Isle west of Void Island. Three gated chambers, one teacher at a
 * time: the Landing (camp: chop, burn, fish, cook), the Ring (rats + a
 * scripted spar), and the Scar (a Void Rogue ambush). Fights are
 * consequence-free via VoidGuidedFight. Gate passage is enforced by
 * VoidTutorialIsle from the walk handlers; stage state lives there too.
 */
public final class VoidGuidedTour implements TalkNpcTrigger, OpNpcTrigger, KillNpcTrigger, PlayerLogoutTrigger {
	public static final String KIT_CACHE_KEY = "void_guided_kit";

	static final int STAGE_NONE = VoidTutorialIsle.STAGE_NONE;
	static final int STAGE_CAMP = VoidTutorialIsle.STAGE_CAMP;
	static final int STAGE_SPAR = VoidTutorialIsle.STAGE_SPAR;
	static final int STAGE_SCOUT = VoidTutorialIsle.STAGE_SCOUT;
	static final int STAGE_DONE = VoidTutorialIsle.STAGE_DONE;

	private static final String WATCHER_ATTRIBUTE = "void_guided_scout_watcher";
	// The rogue strikes once the player crosses into the Scar (third chamber).
	private static final int AMBUSH_TRIGGER_Y = 22;
	private static final int AMBUSH_MIN_Y = 18;

	/** Rift the player from the council clearing to the isle's landing pad. */
	public static void start(Player player) {
		if (player == null || VoidPath.hasChosen(player)) {
			return;
		}
		if (getStage(player) == STAGE_NONE) {
			setStage(player, STAGE_CAMP);
			player.save();
		}
		player.message("@mag@The council tears open a rift - the proving grounds await.");
		player.teleport(VoidTutorialIsle.LANDING_X, VoidTutorialIsle.LANDING_Y, true);
		player.message("@mag@The Void Guide waits just up the path. Speak to them.");
	}

	/** Login resume: put the player in the right chamber and re-hint it. */
	public static void resume(Player player) {
		if (player == null || VoidPath.hasChosen(player)) {
			return;
		}

		int stage = getStage(player);
		if (stage == STAGE_NONE) {
			start(player);
			return;
		}
		if (stage >= STAGE_DONE) {
			if (player.getLocation().inVoidTutorialIsle()) {
				player.teleport(VoidPath.VOID_ISLAND_X, VoidPath.VOID_ISLAND_Y, true);
			}
			player.message("@mag@The Void Herald waits near the island's heart. Choose your path.");
			return;
		}

		// Mid-tour: covers old-flow saves parked on Void Island and strays.
		if (!player.getLocation().inVoidTutorialIsle()) {
			Point p = VoidTutorialIsle.resumePoint(player);
			if (p != null) {
				player.message("@mag@The rift draws you back to the proving grounds.");
				player.teleport(p.getX(), p.getY(), true);
			}
		}

		switch (stage) {
			case STAGE_CAMP:
				player.message("@mag@The Void Guide waits just up the path. Speak to them.");
				break;
			case STAGE_SPAR:
				player.message("@mag@The first gate is open. The Void Adept waits in the ring beyond it.");
				break;
			case STAGE_SCOUT:
			default:
				armScoutWatcher(player);
				player.message("@mag@Something stalks the Scar, past the second gate. Go and see.");
				break;
		}
	}

	public static int getStage(Player player) {
		return VoidTutorialIsle.getStage(player);
	}

	private static void setStage(Player player, int stage) {
		player.getCache().set(VoidTutorialIsle.STAGE_CACHE_KEY, stage);
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return npc.getID() == NpcId.VOID_GUIDE.id() || npc.getID() == NpcId.VOID_ADEPT.id();
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (npc.getID() == NpcId.VOID_GUIDE.id()) {
			talkToGuide(player, npc);
		} else if (npc.getID() == NpcId.VOID_ADEPT.id()) {
			talkToAdept(player, npc);
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return npc.getID() == NpcId.VOID_ADEPT.id() && "spar".equalsIgnoreCase(command);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (npc.getID() == NpcId.VOID_ADEPT.id() && "spar".equalsIgnoreCase(command)) {
			beginSpar(player, npc);
		}
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		// Scripted fight NPCs never drop loot.
		return npc.getID() == NpcId.VOID_ADEPT_FIGHTER.id() || npc.getID() == NpcId.VOID_ROGUE.id();
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		VoidGuidedFight.Kind kind = VoidGuidedFight.completeWin(player, npc);
		if (kind == VoidGuidedFight.Kind.SPAR) {
			onSparWon(player);
		} else if (kind == VoidGuidedFight.Kind.AMBUSH) {
			onAmbushWon(player);
		}
	}

	@Override
	public boolean blockPlayerLogout(Player player) {
		// Must return true for onPlayerLogout to run (it never blocks the logout
		// itself) — otherwise a mid-fight logout leaks the instanced fight NPC.
		return VoidGuidedFight.isFighting(player);
	}

	@Override
	public void onPlayerLogout(Player player) {
		VoidGuidedFight.abort(player);
	}

	private void talkToGuide(Player player, Npc npc) {
		if (VoidPath.hasChosen(player)) {
			npcsay(player, npc, "You carry a path now. Go make the old world proud");
			return;
		}
		if (VoidOnboarding.getTrack(player) != VoidOnboarding.TRACK_GUIDED) {
			npcsay(player, npc, "These are the proving grounds - not your road, survivor");
			return;
		}

		switch (getStage(player)) {
			case STAGE_NONE:
			case STAGE_CAMP:
				if (getStage(player) == STAGE_NONE) {
					setStage(player, STAGE_CAMP);
					player.save();
				}
				campDialogue(player, npc);
				break;
			case STAGE_SPAR:
				npcsay(player, npc,
					"The first gate stands open",
					"Warm up on the rats in the ring, then ask the Void Adept to spar",
					"You cannot truly be hurt here - the void veil catches us all");
				break;
			case STAGE_SCOUT:
				armScoutWatcher(player);
				npcsay(player, npc,
					"One lesson remains, and I cannot teach it",
					"The second gate is open. Something stalks the Scar at the isle's far end",
					"Go and see. Come back alive");
				break;
			case STAGE_DONE:
			default:
				npcsay(player, npc,
					"You have fed yourself, fought, and survived an ambush",
					"That is the whole of the old world, in miniature",
					"The rift will carry you to the Herald. Choose your path");
				break;
		}
	}

	private void campDialogue(Player player, Npc npc) {
		boolean firstVisit = !player.getCache().hasKey(KIT_CACHE_KEY);
		if (firstVisit) {
			npcsay(player, npc,
				"Welcome to the proving grounds, survivor",
				"First lesson: no one feeds you here. Take these");
			grantCampKit(player);
			npcsay(player, npc,
				"Chop one of the trees west of the path and light the logs with your tinderbox",
				"Then cast your net off the western shore and cook the catch on your fire",
				"Eating heals you. Remember that - you will need it soon");
		}

		while (true) {
			int choice = multi(player, npc, false,
				"How do I feed myself again?",
				"I've eaten. Teach me to fight.",
				"I'll get to it.");
			if (choice == 0) {
				npcsay(player, npc,
					"Chop a tree west of the path - your axe does the work",
					"Use your tinderbox on the logs for a fire",
					"Net shrimp off the western shore, then use them on the fire to cook",
					"Burnt a few? Everyone does. Catch more");
			} else if (choice == 1) {
				setStage(player, STAGE_SPAR);
				player.save();
				npcsay(player, npc,
					"Then the first gate is open. Follow the path north",
					"Warm up on the rats in the ring, then ask the Void Adept to spar",
					"No stakes, no loss - the void veil catches us all");
				return;
			} else {
				return;
			}
		}
	}

	private void grantCampKit(Player player) {
		if (player.getCache().hasKey(KIT_CACHE_KEY)) {
			return;
		}
		player.getCache().store(KIT_CACHE_KEY, true);
		give(player, ItemId.BRONZE_AXE.id(), 1);
		give(player, ItemId.TINDERBOX.id(), 1);
		give(player, ItemId.NET.id(), 1);
		give(player, ItemId.BRONZE_SHORT_SWORD.id(), 1);
		give(player, ItemId.BREAD.id(), 2);
		player.save();
		player.message("The Void Guide hands you an axe, tinderbox, net, sword and bread.");
	}

	private void talkToAdept(Player player, Npc npc) {
		if (VoidPath.hasChosen(player)) {
			npcsay(player, npc, "You have outgrown sparring, pathbearer");
			return;
		}

		npcsay(player, npc,
			"Care to spar? Fists, blades - whatever you carry",
			"No stakes. The void veil catches whoever falls");
		int choice = multi(player, npc, false, "Let's spar.", "Not now.");
		if (choice == 0) {
			beginSpar(player, npc);
		}
	}

	private void beginSpar(Player player, Npc npc) {
		if (VoidPath.hasChosen(player)) {
			player.message("You have outgrown sparring.");
			return;
		}
		if (VoidGuidedFight.isFighting(player) || player.inCombat()) {
			player.message("You are already fighting.");
			return;
		}

		if (VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_GUIDED
			&& getStage(player) < STAGE_SPAR) {
			// Sequence-breakers who spar before visiting the guide still get
			// the camp kit (idempotent) — it would be unreachable past CAMP.
			grantCampKit(player);
			setStage(player, STAGE_SPAR);
			player.save();
		}

		player.message("@mag@The world greys at the edges - the void veil settles over the ring.");
		VoidGuidedFight.begin(player, VoidGuidedFight.Kind.SPAR, NpcId.VOID_ADEPT_FIGHTER.id(),
			npc.getX(), Math.max(AMBUSH_MIN_Y, npc.getY() - 1), "Show me what you carry, survivor!");
	}

	private void onSparWon(Player player) {
		player.message("@whi@The adept yields - and rises again, grinning. The veil catches us all.");
		player.message("@whi@That was a duel, void-style. Out there, right-click another survivor to challenge them.");
		player.message("@whi@Real duels can stake items and forbid retreating, magic, prayer, or weapons.");

		if (VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_GUIDED
			&& getStage(player) < STAGE_SCOUT) {
			setStage(player, STAGE_SCOUT);
			player.save();
			armScoutWatcher(player);
			player.message("@mag@The second gate grinds open. Something stalks the Scar - go and see.");
		}
	}

	private void onAmbushWon(Player player) {
		player.message("@whi@The rogue collapses into mist. That is player killing.");
		player.message("@whi@In the wilderness, north of the mainland, players hunt players.");
		player.message("@whi@Die out there and you keep only your three most valuable items. Your killer takes the rest.");
		finishScout(player);
		// After finishScout the player stands beside the Herald — drop the
		// prize at their feet, never on the isle they can no longer reach.
		World world = player.getWorld();
		world.registerItem(new GroundItem(world, ItemId.COINS.id(),
			VoidPath.VOID_ISLAND_X, VoidPath.VOID_ISLAND_Y, 25, player));
		player.message("@whi@A coin purse tumbles through the rift at your feet - take it.");
	}

	static void onFightLost(Player player, VoidGuidedFight.Kind kind) {
		if (kind == VoidGuidedFight.Kind.SPAR) {
			player.message("@whi@Down - but the void veil catches you. No harm done.");
			player.message("@whi@Eat something to heal, then challenge the Void Adept again.");
		} else {
			player.message("@whi@Dead - and here is the lesson: in the wilderness, your killer keeps your pack.");
			player.message("@whi@The void veil saved your things this once. Out there, nothing will.");
			player.message("@mag@The rogue still stalks the Scar. Return when you are ready.");
			// Stage stays SCOUT; walking into the Scar again retries the ambush.
			armScoutWatcher(player);
		}
	}

	static void onFightEscaped(Player player, VoidGuidedFight.Kind kind) {
		if (kind == VoidGuidedFight.Kind.SPAR) {
			player.message("@whi@The adept lowers their fists. Ask again when you want another round.");
		} else {
			player.message("@whi@You escaped the rogue. Remember that - in the wilderness, running is often the right call.");
			finishScout(player);
		}
	}

	private static void finishScout(Player player) {
		if (VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_GUIDED
			&& getStage(player) < STAGE_DONE) {
			setStage(player, STAGE_DONE);
			player.save();
		}
		player.teleport(VoidPath.VOID_ISLAND_X, VoidPath.VOID_ISLAND_Y, true);
		player.message("@mag@The veil carries you back. The Void Herald waits - choose your path.");
	}

	private static void armScoutWatcher(Player player) {
		if (player.getAttribute(WATCHER_ATTRIBUTE, false)) {
			return;
		}
		player.setAttribute(WATCHER_ATTRIBUTE, true);
		World world = player.getWorld();
		world.getServer().getGameEventHandler().add(new ScoutWatcher(world, player));
	}

	/** Watches for the player entering the Scar, then springs the rogue. */
	private static final class ScoutWatcher extends GameTickEvent {
		private final Player player;

		private ScoutWatcher(World world, Player player) {
			super(world, player, 1, "Void Scout Watcher", DuplicationStrategy.ALLOW_MULTIPLE);
			this.player = player;
		}

		@Override
		public void run() {
			if (!player.loggedIn()
				|| VoidPath.hasChosen(player)
				|| getStage(player) != STAGE_SCOUT) {
				player.removeAttribute(WATCHER_ATTRIBUTE);
				stop();
				return;
			}
			// Defer while in any combat (e.g. a rat) — begin() would silently
			// refuse and the arming would be consumed for nothing.
			if (VoidGuidedFight.isFighting(player) || player.inCombat()) {
				return;
			}
			if (player.getY() <= AMBUSH_TRIGGER_Y && player.getLocation().inVoidTutorialIsle()) {
				player.removeAttribute(WATCHER_ATTRIBUTE);
				stop();
				// The Scar narrows to x37-43; clamp the spawn onto its spine
				// so the rogue never lands in the ocean.
				int rogueX = Math.max(38, Math.min(42, player.getX()));
				int rogueY = Math.max(AMBUSH_MIN_Y + 1, player.getY() - 2);
				VoidGuidedFight.begin(player, VoidGuidedFight.Kind.AMBUSH, NpcId.VOID_ROGUE.id(),
					rogueX, rogueY, "A fresh pack, all alone? It's mine now!");
			}
		}
	}
}
