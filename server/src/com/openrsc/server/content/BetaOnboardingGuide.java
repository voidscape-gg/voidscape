package com.openrsc.server.content;

import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;

import static com.openrsc.server.plugins.Functions.multi;

public final class BetaOnboardingGuide {
	public static final String SEEN_CACHE_KEY = "void_beta_guide_seen";

	private BetaOnboardingGuide() {
	}

	public static boolean showFirstTime(Player player) {
		if (!enabled(player) || player.getCache().hasKey(SEEN_CACHE_KEY)) {
			return false;
		}
		player.getCache().store(SEEN_CACHE_KEY, true);
		player.save(false, true);
		player.message("@mag@Beta guide: choose a topic. You can reopen this with ::beta.");
		show(player);
		return true;
	}

	public static void show(Player player) {
		if (!enabled(player)) {
			player.message("The beta guide is disabled on this world.");
			return;
		}

		int choice = multi(player, null, false,
			"Quickstart: commands and first goals",
			"Places: coordinates to test",
			"Items: cards, keys, and void gear",
			"Features: what beta needs checked",
			"Close beta guide");

		switch (choice) {
			case 0:
				ActionSender.sendBox(player, quickstartText(), true);
				break;
			case 1:
				ActionSender.sendBox(player, placesText(), true);
				break;
			case 2:
				ActionSender.sendBox(player, itemsText(), true);
				break;
			case 3:
				ActionSender.sendBox(player, featuresText(), true);
				break;
			default:
				player.message("@whi@Use @gre@::beta@whi@ if you want to open the beta guide again.");
				break;
		}
	}

	private static boolean enabled(Player player) {
		return player != null && player.getConfig().WANT_BETA_ONBOARDING_GUIDE;
	}

	private static String quickstartText() {
		return "@yel@Beta quickstart% %"
			+ "@whi@Use @gre@::g <message>@whi@ for global chat once eligible.%"
			+ "@whi@Use @gre@::rested@whi@ for rested XP status.%"
			+ "@whi@Use @gre@::titles@whi@ to browse and equip titles.%"
			+ "@whi@Use @gre@::lootbeam list@whi@ and @gre@::lootbeam defaults@whi@ for rare beams.%"
			+ "@whi@Use @gre@::coords@whi@ to report your exact tile.% %"
			+ "@lre@Reopen this menu any time with ::beta.";
	}

	private static String placesText() {
		return "@yel@Places and coordinates% %"
			+ "@whi@Lumbridge home: @gre@120 648@whi@.%"
			+ "@whi@Subscription Vendor: @gre@126 649@whi@.%"
			+ "@whi@Edgeville bank: @gre@217 449@whi@.%"
			+ "@whi@Void Auctioneer: @gre@217 460@whi@.%"
			+ "@whi@Void Rift: @gre@192 443@whi@.%"
			+ "@whi@Void Enclave: @gre@113 314@whi@.%"
			+ "@whi@Void Dungeon entrance: @gre@112 296@whi@.";
	}

	private static String itemsText() {
		return "@yel@Items to try% %"
			+ "@whi@Subscription card: @gre@1602@whi@. Redeem adds 7 days of account subscription.%"
			+ "@whi@Void Key: @gre@1601@whi@. Use it with Void Chest rewards.%"
			+ "@whi@Void Scimitar: @gre@1593@whi@.%"
			+ "@whi@Void Shortbow: @gre@1594@whi@.% %"
			+ "@lre@Report missing sprites, odd prices, or broken right-click actions.";
	}

	private static String featuresText() {
		return "@yel@Features to test% %"
			+ "@whi@Starter kits and 2x path XP until level 50.%"
			+ "@whi@Auction House browsing, listing, and buying in Edgeville.%"
			+ "@whi@Titles, overhead names, and rare drop beams.%"
			+ "@whi@Void Enclave boss, Void Rush, PK Catching, and Wilderness spawns.%"
			+ "@whi@Subscription card claiming and redemption.% %"
			+ "@lre@Bug reports need character, coords, action, result, and expected result.";
	}
}
