package com.openrsc.server.plugins.authentic.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.database.impl.mysql.queries.logging.GenericLog;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.authentic.npcs.Bankers;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import com.openrsc.server.plugins.triggers.UseNpcTrigger;
import com.openrsc.server.plugins.triggers.UsePlayerTrigger;
import com.openrsc.server.util.rsc.DataConversions;
import com.openrsc.server.util.rsc.MessageType;

import static com.openrsc.server.plugins.Functions.inArray;

/**
 * Voidscape Christmas cracker: a server-authoritative, self-opened holiday roll.
 *
 * The result is committed before the player's inventory is synchronized. A
 * versioned hidden server message drives the custom-client reel; it is visual
 * only, and the ordinary result message remains the accessible fallback/log.
 */
public class ChristmasCracker implements OpInvTrigger, UsePlayerTrigger, UseNpcTrigger {

	private static final String COMMAND_OPEN = "Open";
	private static final String CLIENT_REEL_PREFIX = "@vscracker@";
	private static final int CLIENT_REEL_VERSION = 10129;
	private static final String NEXT_OPEN_AT_ATTRIBUTE = "void_christmas_cracker_next_open_at";
	private static final String FIXTURE_CATEGORY_ROLL_ATTRIBUTE = "void_christmas_cracker_fixture_category_roll";
	private static final String FIXTURE_REWARD_ROLL_ATTRIBUTE = "void_christmas_cracker_fixture_reward_roll";
	private static final long OPEN_COOLDOWN_MILLIS = 6000L;

	private static final int CATEGORY_NOTHING = 0;
	private static final int CATEGORY_PARTY_HAT = 1;
	private static final int CATEGORY_HOLIDAY_RARE = 2;

	// Preserve the original Christmas-cracker party-hat colour weights.
	private static final int[] PARTY_HAT_IDS = {
		ItemId.PINK_PARTY_HAT.id(),
		ItemId.BLUE_PARTY_HAT.id(),
		ItemId.GREEN_PARTY_HAT.id(),
		ItemId.WHITE_PARTY_HAT.id(),
		ItemId.RED_PARTY_HAT.id(),
		ItemId.YELLOW_PARTY_HAT.id()
	};
	private static final int[] PARTY_HAT_WEIGHTS = {10, 15, 20, 23, 32, 28};

	private static final int[] HOLIDAY_RARE_IDS = {
		ItemId.PUMPKIN.id(),
		ItemId.EASTER_EGG.id(),
		ItemId.GREEN_HALLOWEEN_MASK.id(),
		ItemId.RED_HALLOWEEN_MASK.id(),
		ItemId.BLUE_HALLOWEEN_MASK.id(),
		ItemId.SANTAS_HAT.id()
	};
	private static final int[] HOLIDAY_RARE_WEIGHTS = {2, 2, 1, 1, 1, 1};

	@Override
	public void onOpInv(Player player, Integer invIndex, Item item, String command) {
		if (!isOpenCommand(item, command)) {
			return;
		}
		if (item.getNoted()) {
			player.message("You need to unnote the Christmas cracker before opening it.");
			return;
		}
		if (!isExactInventoryItem(player, invIndex, item)) {
			return;
		}

		long now = System.currentTimeMillis();
		long nextOpenAt = player.getAttribute(NEXT_OPEN_AT_ATTRIBUTE, 0L);
		if (now < nextOpenAt) {
			player.message("Your last Christmas cracker is still opening.");
			return;
		}

		CrackerResult result = roll(player);
		long consumedItemId = player.getCarriedItems().remove(item, false);
		if (consumedItemId == -1) {
			return;
		}

		clearFixture(player);
		player.setAttribute(NEXT_OPEN_AT_ATTRIBUTE, now + OPEN_COOLDOWN_MILLIS);

		boolean deliveredToInventory = true;
		if (result.itemId != ItemId.NOTHING.id()) {
			deliveredToInventory = player.getCarriedItems().getInventory().add(new Item(result.itemId), false);
		}

		sendClientReelResult(player, result, now);
		sendFallbackResultMessage(player, result, deliveredToInventory);
		ActionSender.sendInventory(player);
		logResult(player, result, consumedItemId, deliveredToInventory);
	}

	@Override
	public boolean blockOpInv(Player player, Integer invIndex, Item item, String command) {
		return item != null && item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id();
	}

	/**
	 * The legacy pull paths deliberately award nothing. Keeping the triggers lets
	 * old clients receive a useful explanation instead of silently bypassing the
	 * new 60/20/20 table with the old guaranteed-party-hat implementation.
	 */
	@Override
	public void onUsePlayer(Player player, Player otherPlayer, Item item) {
		if (item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id()) {
			player.message("Open the Christmas cracker from your inventory instead.");
		}
	}

	@Override
	public boolean blockUsePlayer(Player player, Player otherPlayer, Item item) {
		return item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id();
	}

	@Override
	public void onUseNpc(Player player, Npc npc, Item item) {
		if (item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id()) {
			player.message("Open the Christmas cracker from your inventory instead.");
		}
	}

	@Override
	public boolean blockUseNpc(Player player, Npc npc, Item item) {
		return inArray(npc.getID(), Bankers.BANKERS)
			&& item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id();
	}

	/**
	 * Queues deterministic raw rolls for the next cracker opened by an admin.
	 * categoryRoll uses the production 0..99 boundaries; rewardRoll selects from
	 * the same weighted production tables. The values are transient and one-shot.
	 */
	public static boolean queueAdminFixture(Player player, int categoryRoll, int rewardRoll) {
		if (player == null || !player.isAdmin() || categoryRoll < 0 || categoryRoll > 99
			|| rewardRoll < 0 || rewardRoll >= totalWeight(PARTY_HAT_WEIGHTS)) {
			return false;
		}
		player.setAttribute(FIXTURE_CATEGORY_ROLL_ATTRIBUTE, categoryRoll);
		player.setAttribute(FIXTURE_REWARD_ROLL_ATTRIBUTE, rewardRoll);
		return true;
	}

	private static boolean isOpenCommand(Item item, String command) {
		return item != null
			&& item.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id()
			&& command != null
			&& COMMAND_OPEN.equalsIgnoreCase(command);
	}

	private static boolean isExactInventoryItem(Player player, Integer invIndex, Item item) {
		if (invIndex == null || invIndex < 0 || invIndex >= player.getCarriedItems().getInventory().size()) {
			return false;
		}
		Item current = player.getCarriedItems().getInventory().get(invIndex);
		return current != null
			&& current.getCatalogId() == ItemId.CHRISTMAS_CRACKER.id()
			&& current.getItemId() == item.getItemId()
			&& current.getNoted() == item.getNoted();
	}

	private static CrackerResult roll(Player player) {
		int forcedCategoryRoll = player.getAttribute(FIXTURE_CATEGORY_ROLL_ATTRIBUTE, -1);
		int forcedRewardRoll = player.getAttribute(FIXTURE_REWARD_ROLL_ATTRIBUTE, -1);
		int categoryRoll = forcedCategoryRoll >= 0 ? forcedCategoryRoll : DataConversions.random(0, 99);

		if (categoryRoll < 60) {
			return new CrackerResult(CATEGORY_NOTHING, ItemId.NOTHING.id());
		}

		if (categoryRoll < 80) {
			int rewardRoll = forcedRewardRoll >= 0
				? forcedRewardRoll
				: DataConversions.random(0, totalWeight(PARTY_HAT_WEIGHTS) - 1);
			return new CrackerResult(CATEGORY_PARTY_HAT,
				weightedChoice(PARTY_HAT_IDS, PARTY_HAT_WEIGHTS, rewardRoll));
		}

		int rewardRoll = forcedRewardRoll >= 0
			? forcedRewardRoll
			: DataConversions.random(0, totalWeight(HOLIDAY_RARE_WEIGHTS) - 1);
		return new CrackerResult(CATEGORY_HOLIDAY_RARE,
			weightedChoice(HOLIDAY_RARE_IDS, HOLIDAY_RARE_WEIGHTS, rewardRoll));
	}

	private static int weightedChoice(int[] ids, int[] weights, int rawRoll) {
		int normalizedRoll = Math.floorMod(rawRoll, totalWeight(weights));
		int cumulativeWeight = 0;
		for (int i = 0; i < ids.length; i++) {
			cumulativeWeight += weights[i];
			if (normalizedRoll < cumulativeWeight) {
				return ids[i];
			}
		}
		throw new IllegalStateException("Christmas cracker reward table is invalid");
	}

	private static int totalWeight(int[] weights) {
		int total = 0;
		for (int weight : weights) {
			total += weight;
		}
		return total;
	}

	private static void clearFixture(Player player) {
		player.removeAttribute(FIXTURE_CATEGORY_ROLL_ATTRIBUTE);
		player.removeAttribute(FIXTURE_REWARD_ROLL_ATTRIBUTE);
	}

	private static void sendFallbackResultMessage(Player player, CrackerResult result,
												 boolean deliveredToInventory) {
		if (result.category == CATEGORY_NOTHING) {
			player.playerServerMessage(MessageType.QUEST,
				"The Christmas cracker snaps open... but there is nothing inside.");
			return;
		}

		String delivery = deliveredToInventory ? "" : " It drops at your feet.";
		String prize = articleFor(result.itemId) + " " + displayName(result.itemId);
		if (result.category == CATEGORY_PARTY_HAT) {
			player.playerServerMessage(MessageType.QUEST,
				"The Christmas cracker contains " + prize + "!" + delivery);
		} else {
			player.playerServerMessage(MessageType.QUEST,
				"Holiday rare! The Christmas cracker contains " + prize + "!" + delivery);
		}
	}

	private static void sendClientReelResult(Player player, CrackerResult result, long openedAtMillis) {
		if (!player.isUsingCustomClient() || player.getClientVersion() < CLIENT_REEL_VERSION) {
			return;
		}
		long rollId = openedAtMillis;
		long seed = openedAtMillis
			^ ((long)player.getUsername().hashCode() << 32)
			^ ((long)result.itemId << 11)
			^ result.category;
		String payload = CLIENT_REEL_PREFIX + "v1|" + rollId + "|" + result.category
			+ "|" + result.itemId + "|" + seed;
		ActionSender.sendMessage(player, null, MessageType.QUEST, payload, 0, null);
	}

	private static String articleFor(int itemId) {
		return itemId == ItemId.EASTER_EGG.id() ? "an" : "a";
	}

	private static void logResult(Player player, CrackerResult result, long consumedItemId,
								  boolean deliveredToInventory) {
		String category = result.category == CATEGORY_NOTHING ? "nothing"
			: result.category == CATEGORY_PARTY_HAT ? "party_hat" : "holiday_rare";
		String delivery = result.itemId == ItemId.NOTHING.id() ? "none"
			: deliveredToInventory ? "inventory" : "fallback";
		player.getWorld().getServer().getGameLogger().addQuery(new GenericLog(player.getWorld(),
			player.getUsername() + " opened Christmas cracker itemId=" + consumedItemId
				+ " result=" + category + " rewardId=" + result.itemId + " delivery=" + delivery));
	}

	private static String displayName(int itemId) {
		switch (itemId) {
			case 576: return "red party hat";
			case 577: return "yellow party hat";
			case 578: return "blue party hat";
			case 579: return "green party hat";
			case 580: return "pink party hat";
			case 581: return "white party hat";
			case 422: return "pumpkin";
			case 677: return "Easter egg";
			case 828: return "green Halloween mask";
			case 831: return "red Halloween mask";
			case 832: return "blue Halloween mask";
			case 971: return "Santa's hat";
			default: return "holiday prize";
		}
	}

	private static final class CrackerResult {
		private final int category;
		private final int itemId;

		private CrackerResult(int category, int itemId) {
			this.category = category;
			this.itemId = itemId;
		}
	}
}
