package com.openrsc.server.content;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.entity.GroundItem;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;

import static com.openrsc.server.plugins.Functions.createGroundItemDelayedRemove;
import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;

/**
 * "What's new in Voidscape?" — the veteran onboarding track. The Void Archivist
 * gives two live demos (a full rested-XP pool, a loot-beamed dragonstone) and
 * then an "ask me about..." topic menu. Demos are one-time; the menu is
 * repeatable forever, including at the Archivist's Lumbridge spawn.
 */
public final class VoidVeteranTour {
	public static final String RESTED_GIFT_CACHE_KEY = "void_vet_rested_gift";
	public static final String TOUR_SEEN_CACHE_KEY = "void_vet_tour_seen";
	public static final String BEAM_DEMO_ATTRIBUTE = "void_vet_beam_demo";
	private static final String RUNNING_ATTRIBUTE = "void_vet_tour_running";
	// Well under the 64s window in which owner-scoped ground items stay private.
	private static final int BEAM_DEMO_TTL_MS = 20_000;

	private VoidVeteranTour() {
	}

	/** Entry point after the council lore: just points at the Archivist. */
	public static void start(Player player) {
		if (player == null || VoidPath.hasChosen(player)) {
			return;
		}
		player.message("@mag@The Void Archivist waits on the path north. Speak to them before you reach the Herald.");
	}

	public static boolean needsRequiredTour(Player player) {
		return player != null
			&& !VoidPath.hasChosen(player)
			&& VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_VETERAN
			&& !player.getCache().hasKey(TOUR_SEEN_CACHE_KEY);
	}

	public static void run(Player player, Npc archivist) {
		if (player == null || archivist == null || player.getAttribute(RUNNING_ATTRIBUTE, false)) {
			return;
		}

		player.setAttribute(RUNNING_ATTRIBUTE, true);
		player.resetPath();
		try {
			if (VoidOnboarding.getTrack(player) == VoidOnboarding.TRACK_VETERAN
				&& !player.getCache().hasKey(TOUR_SEEN_CACHE_KEY)) {
				npcsay(player, archivist,
					"Ah - a survivor who remembers the old world",
					"Then I will skip the basics and show you what the void changed");
				restedDemo(player, archivist);
				beamDemo(player, archivist);
				player.getCache().store(TOUR_SEEN_CACHE_KEY, true);
				player.save(false, true);
			} else if (player.getCache().hasKey(TOUR_SEEN_CACHE_KEY)) {
				npcsay(player, archivist, "Welcome back. Ask, and I will answer");
			} else {
				npcsay(player, archivist, "The archive is open. Ask, and I will answer");
			}
			topicMenu(player, archivist);
		} finally {
			player.setAttribute(RUNNING_ATTRIBUTE, false);
		}
	}

	public static boolean isBeamDemoItem(GroundItem item) {
		return item != null && item.getAttribute(BEAM_DEMO_ATTRIBUTE, false);
	}

	public static void onBeamDemoTake(Player player, GroundItem item) {
		if (player == null || item == null) {
			return;
		}
		player.getWorld().unregisterItem(item);
		player.message("@mag@The dragonstone dissolves into void mist. Only an echo.");
	}

	private static void restedDemo(Player player, Npc npc) {
		npcsay(player, npc,
			"First: time away is not wasted here",
			"After half an hour offline you earn rested XP - one second per second, up to 45 minutes",
			"While it lasts, everything you do earns half again as much experience");

		if (player.getCache().hasKey(RESTED_GIFT_CACHE_KEY)) {
			npcsay(player, npc, "I have already given you my welcome gift");
			return;
		}

		player.getCache().store(RESTED_GIFT_CACHE_KEY, true);
		RestedExperience.grantFull(player);
		player.save(false, true);
		player.message("@gre@You feel rested.");
		player.message(RestedExperience.status(player));
		npcsay(player, npc, "Consider your pool filled - a welcome-back gift. Check it anytime with ::rested");
	}

	private static void beamDemo(Player player, Npc npc) {
		npcsay(player, npc, "Second: valuable drops announce themselves here. Watch");

		GroundItem demo = new GroundItem(player.getWorld(), ItemId.DRAGONSTONE.id(),
			npc.getX() - 1, npc.getY(), 1, player);
		demo.setAttribute(BEAM_DEMO_ATTRIBUTE, true);
		createGroundItemDelayedRemove(demo, BEAM_DEMO_TTL_MS);

		npcsay(player, npc,
			"That beam marks rares: dragonstone, rune, dragon, void relics, party hats",
			"Tune the list with ::lootbeam - add, remove, or run your own",
			"The beams themselves toggle under Advanced Settings");
	}

	private static void topicMenu(Player player, Npc npc) {
		while (true) {
			// npc = null so concurrent talkers can't steal each other's menu via the NPC.
			int choice = multi(player, null, false,
				"How fast is experience here?",
				"What are these paths?",
				"How do I trade and earn coins?",
				"How do people talk? And what are titles?",
				"Where is the PvP?",
				"Any other tricks I should know?",
				"That's everything - point me onward");

			switch (choice) {
				case 0:
					npcsay(player, npc,
						"Combat experience flows ten times faster than the old world, skilling one and a half times",
						"Melee and ranged pay out on every hit, not just the kill",
						"And fatigue died with the old world - no beds, no rest, just play",
						"Stack that with rested XP - ::rested - and levels come quickly");
					break;
				case 1:
					npcsay(player, npc,
						"The Herald north of here offers three paths: Warrior, Forager, Arcanist",
						"Your path earns double experience in its skills until they reach 50",
						"Each comes with a starter kit. Choose what suits you - it cannot be changed");
					break;
				case 2:
					npcsay(player, npc,
						"The Void Auctioneer south of Edgeville bank runs a marketplace",
						"Post up to six listings; sales pay a 5% tax",
						"Subscription cards are tradable - seven days of +1x experience",
						"A free starter card waits with the Subscription Vendor in Lumbridge");
					break;
				case 3:
					npcsay(player, npc,
						"::g speaks to every survivor at once; ::pk is the wilderness channel",
						"The chat strip has a Global tab so you never lose the thread",
						"Titles mark your deeds - ::titles shows what you can claim",
						"Some are unique: first to earn them keeps them");
					break;
				case 4:
					npcsay(player, npc,
						"The wilderness works as you remember - but watch for the bounty mark, ::bounty",
						"Kill the marked player for bounty points and a void key",
						"For clean duels, ::arena runs ranked deathmatches",
						"And when you feel strong: challenge Sir Charles there. Few survive");
					break;
				case 5:
					npcsay(player, npc,
						"Click anywhere on the world map and you will walk there",
						"::b opens the bank when you stand in one; the bank itself holds presets",
						"::commands lists everything else",
						"Purists can shed the conveniences with ::qoloptout");
					break;
				case 6:
				default:
					if (choice == 6) {
						farewell(player, npc);
					}
					return;
			}
		}
	}

	private static void farewell(Player player, Npc npc) {
		if (VoidPath.hasChosen(player)) {
			npcsay(player, npc, "Go well, survivor. The void archive is always open");
			return;
		}
		npcsay(player, npc, "Then take our world back");
		player.message("@mag@The Void Herald waits at the island's north tip. Choose your path.");
	}
}
