package com.openrsc.server.plugins.custom.misc;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.content.PlayerTitle;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.GameObject;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpLocTrigger;
import com.openrsc.server.plugins.triggers.UseLocTrigger;
import com.openrsc.server.util.rsc.DataConversions;

import static com.openrsc.server.plugins.Functions.changeloc;

public class VoidChest implements OpLocTrigger, UseLocTrigger {
	private static final int VOID_CHEST_CLOSED = 1304;
	private static final int VOID_CHEST_OPEN = 1305;
	private static final int OPEN_RESPAWN_MS = 3000;

	// Common supplies intentionally carry the extra weight so jackpot rows stay rare.
	private static final Reward[] REWARDS = {
		new Reward(ItemId.COINS.id(), 25000, 50000, 8),
		new Reward(ItemId.IRON_ORE.id(), 100, 200, 16, true),
		new Reward(ItemId.COAL.id(), 80, 170, 16, true),
		new Reward(ItemId.RAW_SWORDFISH.id(), 80, 150, 16, true),
		new Reward(ItemId.RAW_LOBSTER.id(), 80, 150, 16, true),
		new Reward(ItemId.DEATH_RUNE.id(), 150, 300, 8),
		new Reward(ItemId.LAW_RUNE.id(), 65, 130, 6),
		new Reward(ItemId.NATURE_RUNE.id(), 65, 130, 6),
		new Reward(ItemId.BIG_BONES.id(), 80, 130, 6, true),
		new Reward(ItemId.AIR_RUNE.id(), 2000, 2000, 9),
		new Reward(ItemId.FIRE_RUNE.id(), 2000, 2000, 9),
		new Reward(ItemId.MITHRIL_ARROW_HEADS.id(), 300, 300, 5),
		new Reward(ItemId.ADAMANTITE_ARROW_HEADS.id(), 250, 250, 5),
		new Reward(ItemId.RED_SPIDERS_EGGS.id(), 50, 90, 8, true),
		new Reward(ItemId.UNICORN_HORN.id(), 50, 90, 5, true),
		new Reward(ItemId.LIMPWURT_ROOT.id(), 50, 90, 7, true),
		new Reward(ItemId.SNAPE_GRASS.id(), 75, 125, 4, true),
		new Reward(ItemId.UNCUT_SAPPHIRE.id(), 15, 20, 7, true),
		new Reward(ItemId.UNCUT_EMERALD.id(), 10, 15, 6, true),
		new Reward(ItemId.UNCUT_RUBY.id(), 5, 9, 5, true),
		new Reward(ItemId.UNCUT_DIAMOND.id(), 3, 6, 3, true),
		new Reward(ItemId.RUNE_SCIMITAR.id(), 2, 2, 4, true),
		new Reward(ItemId.RUNE_DAGGER.id(), 6, 6, 2, true),
		new Reward(ItemId.SHARK.id(), 150, 150, 2, true),
		new Reward(ItemId.SWORDFISH.id(), 300, 300, 3, true),
		new Reward(ItemId.DRAGON_BONES.id(), 50, 125, 2, true),
		new Reward(ItemId.UNCUT_DRAGONSTONE.id(), 2, 2, 1, true),
		new Reward(ItemId.RUNE_PLATE_MAIL_BODY.id(), 2, 2, 1, true),
		new Reward(ItemId.RUNE_PLATE_MAIL_LEGS.id(), 2, 2, 1, true),
		new Reward(ItemId.COINS.id(), 200000, 200000, 1),
		new Reward(ItemId.DRAGON_SWORD_BLADE.id(), 1, 1, 1),
		new Reward(ItemId.DRAGON_SWORD_HILT.id(), 1, 1, 1),
		new Reward(ItemId.DRAGON_SWORD_TIP.id(), 1, 1, 1),
		new Reward(ItemId.DRAGON_MEDIUM_HELMET.id(), 1, 1, 1)
	};

	@Override
	public boolean blockOpLoc(Player player, GameObject obj, String command) {
		return obj.getID() == VOID_CHEST_CLOSED;
	}

	@Override
	public void onOpLoc(Player player, GameObject obj, String command) {
		if (obj.getID() == VOID_CHEST_CLOSED) {
			openChest(player, obj);
		}
	}

	@Override
	public boolean blockUseLoc(Player player, GameObject obj, Item item) {
		return obj.getID() == VOID_CHEST_CLOSED && item.getCatalogId() == ItemId.VOID_KEY.id();
	}

	@Override
	public void onUseLoc(Player player, GameObject obj, Item item) {
		if (obj.getID() == VOID_CHEST_CLOSED && item.getCatalogId() == ItemId.VOID_KEY.id()) {
			openChest(player, obj);
		}
	}

	private void openChest(Player player, GameObject obj) {
		if (player.getCarriedItems().getInventory().countId(ItemId.VOID_KEY.id()) < 1) {
			player.message("The void chest is locked.");
			player.message("You need a Void Key from wilderness creatures.");
			return;
		}

		Reward reward = rollReward();
		int amount = reward.rollAmount();
		Item rewardItem = new Item(reward.itemId, amount, reward.noted);
		if (!player.getCarriedItems().getInventory().canHold(rewardItem, 1)) {
			player.message("You need more inventory space to open the void chest.");
			return;
		}

		if (player.getCarriedItems().remove(new Item(ItemId.VOID_KEY.id())) == -1) {
			return;
		}

		player.message("The Void Key dissolves into the lock.");
		changeloc(obj, OPEN_RESPAWN_MS, VOID_CHEST_OPEN);
		giveReward(player, rewardItem);
		PlayerTitle.incrementCounter(player, PlayerTitle.COUNTER_VOID_CHESTS);
		player.message("Inside the chest you find " + describeReward(player, rewardItem) + ".");
	}

	private Reward rollReward() {
		int totalWeight = 0;
		for (Reward reward : REWARDS) {
			totalWeight += reward.weight;
		}

		int roll = DataConversions.random(1, totalWeight);
		for (Reward reward : REWARDS) {
			roll -= reward.weight;
			if (roll <= 0) {
				return reward;
			}
		}
		return REWARDS[0];
	}

	private void giveReward(Player player, Item item) {
		if (item.getAmount() > 1 && !item.getDef(player.getWorld()).isStackable() && !item.getNoted()) {
			for (int i = 0; i < item.getAmount(); i++) {
				player.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), 1));
			}
			return;
		}
		player.getCarriedItems().getInventory().add(item);
	}

	private String describeReward(Player player, Item item) {
		String name = item.getDef(player.getWorld()).getName();
		String notePrefix = item.getNoted() ? "noted " : "";
		if (item.getAmount() == 1) {
			return "a " + notePrefix + name;
		}
		return item.getAmount() + " " + notePrefix + name;
	}

	private static class Reward {
		private final int itemId;
		private final int minAmount;
		private final int maxAmount;
		private final int weight;
		private final boolean noted;

		private Reward(int itemId, int minAmount, int maxAmount, int weight) {
			this(itemId, minAmount, maxAmount, weight, false);
		}

		private Reward(int itemId, int minAmount, int maxAmount, int weight, boolean noted) {
			this.itemId = itemId;
			this.minAmount = minAmount;
			this.maxAmount = maxAmount;
			this.weight = weight;
			this.noted = noted;
		}

		private int rollAmount() {
			return DataConversions.random(minAmount, maxAmount);
		}
	}
}
