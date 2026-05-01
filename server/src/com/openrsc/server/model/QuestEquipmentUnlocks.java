package com.openrsc.server.model;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.Quests;
import com.openrsc.server.model.entity.player.Player;

public final class QuestEquipmentUnlocks {

	public static final int PRICE_COINS = 500000;
	public static final String SELLER_NAME = "Void Requisitioner";
	public static final String SELLER_LOCATION = "Varrock";

	private QuestEquipmentUnlocks() {
	}

	public enum Unlock {
		RUNE_PLATE(
			"Rune plate right",
			"Dragon Slayer",
			Quests.DRAGON_SLAYER,
			"quest_equipment_right_dragon_slayer",
			ItemId.RUNE_PLATE_MAIL_BODY.id(),
			ItemId.RUNE_PLATE_MAIL_TOP.id()
		),
		DRAGON_SWORD(
			"Dragon sword right",
			"Lost City",
			Quests.LOST_CITY,
			"quest_equipment_right_lost_city",
			ItemId.DRAGON_SWORD.id()
		),
		DRAGON_BATTLE_AXE(
			"Dragon battle axe right",
			"Hero's Quest",
			Quests.HEROS_QUEST,
			"quest_equipment_right_heros_quest",
			ItemId.DRAGON_AXE.id()
		),
		DRAGON_SQUARE_SHIELD(
			"Dragon square shield right",
			"Legends' Quest",
			Quests.LEGENDS_QUEST,
			"quest_equipment_right_legends_quest",
			ItemId.DRAGON_SQUARE_SHIELD.id(),
			2254
		);

		private final String displayName;
		private final String questName;
		private final int questId;
		private final String cacheKey;
		private final int[] itemIds;

		Unlock(String displayName, String questName, int questId, String cacheKey, int... itemIds) {
			this.displayName = displayName;
			this.questName = questName;
			this.questId = questId;
			this.cacheKey = cacheKey;
			this.itemIds = itemIds;
		}

		public String displayName() {
			return displayName;
		}

		public String questName() {
			return questName;
		}

		public int questId() {
			return questId;
		}

		public String cacheKey() {
			return cacheKey;
		}

		public String purchaseOption() {
			return displayName + " - 500,000gp";
		}

		private boolean includesItem(int itemId) {
			for (int unlockItemId : itemIds) {
				if (unlockItemId == itemId) {
					return true;
				}
			}
			return false;
		}
	}

	public static Unlock forItem(int itemId) {
		for (Unlock unlock : Unlock.values()) {
			if (unlock.includesItem(itemId)) {
				return unlock;
			}
		}
		return null;
	}

	public static boolean hasEquipmentRight(Player player, Unlock unlock) {
		return player.getConfig().EQUIP_QUEST_ITEMS_WITHOUT_QUESTS
			|| player.getQuestStage(unlock.questId()) == -1
			|| player.getCache().hasKey(unlock.cacheKey());
	}

	public static Unlock missingForItem(Player player, int itemId) {
		Unlock unlock = forItem(itemId);
		if (unlock == null || hasEquipmentRight(player, unlock)) {
			return null;
		}
		return unlock;
	}
}
