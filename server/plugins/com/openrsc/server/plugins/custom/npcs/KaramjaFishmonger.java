package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

import java.util.Optional;

import static com.openrsc.server.plugins.Functions.*;

public final class KaramjaFishmonger implements TalkNpcTrigger, OpNpcTrigger {
	private static final int NPC_ID = NpcId.KARAMJA_FISHMONGER.id();
	private static final String COOK_COMMAND = "Cook fish";
	private static final String NOTE_COMMAND = "Note fish";

	private static final Fish[] FISH = {
		new Fish(ItemId.RAW_SHRIMP, ItemId.SHRIMP),
		new Fish(ItemId.RAW_ANCHOVIES, ItemId.ANCHOVIES),
		new Fish(ItemId.RAW_SARDINE, ItemId.SARDINE),
		new Fish(ItemId.RAW_HERRING, ItemId.HERRING),
		new Fish(ItemId.RAW_TROUT, ItemId.TROUT),
		new Fish(ItemId.RAW_PIKE, ItemId.PIKE),
		new Fish(ItemId.RAW_SALMON, ItemId.SALMON),
		new Fish(ItemId.RAW_TUNA, ItemId.TUNA),
		new Fish(ItemId.RAW_LOBSTER, ItemId.LOBSTER),
		new Fish(ItemId.RAW_SWORDFISH, ItemId.SWORDFISH),
		new Fish(ItemId.RAW_SHARK, ItemId.SHARK),
		new Fish(ItemId.RAW_COD, ItemId.COD),
		new Fish(ItemId.RAW_MACKEREL, ItemId.MACKEREL),
		new Fish(ItemId.RAW_BASS, ItemId.BASS),
		new Fish(ItemId.RAW_MANTA_RAY, ItemId.MANTA_RAY),
		new Fish(ItemId.RAW_SEA_TURTLE, ItemId.SEA_TURTLE)
	};

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isFishmonger(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isFishmonger(npc)) return;

		npcsay(player, npc,
			"I'll cook your raw fish for a half share.",
			"Or I can pack raw and cooked fish into notes.");

		int option = multi(player, npc,
			"Cook my raw fish.",
			"Note my fish.",
			"Never mind.");

		if (option == 0) {
			say(player, npc, "Cook my raw fish.");
			cookFish(player, npc);
		} else if (option == 1) {
			say(player, npc, "Note my fish.");
			noteFish(player, npc);
		} else {
			npcsay(player, npc, "Fair enough.");
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isFishmonger(npc)
			&& command != null
			&& (command.equalsIgnoreCase(COOK_COMMAND) || command.equalsIgnoreCase(NOTE_COMMAND));
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (!blockOpNpc(player, npc, command)) return;

		if (command.equalsIgnoreCase(COOK_COMMAND)) {
			cookFish(player, npc);
		} else {
			noteFish(player, npc);
		}
	}

	private void cookFish(Player player, Npc npc) {
		int rawTaken = 0;
		int cookedGiven = 0;

		for (Fish fish : FISH) {
			int rawCount = player.getCarriedItems().getInventory().countId(fish.rawId, Optional.of(false));
			if (rawCount < 2) {
				continue;
			}

			int cookedCount = rawCount / 2;
			if (player.getCarriedItems().remove(new Item(fish.rawId, rawCount, false)) == -1) {
				continue;
			}

			giveUnstacked(player, new Item(fish.cookedId, cookedCount, false));
			rawTaken += rawCount;
			cookedGiven += cookedCount;
		}

		if (rawTaken == 0) {
			npcsay(player, npc, "Bring me at least two raw fish of a kind.");
			return;
		}

		player.message("The fishmonger cooks " + rawTaken + " raw fish and keeps half.");
		player.message("You receive " + cookedGiven + " cooked fish.");
	}

	private void noteFish(Player player, Npc npc) {
		int notedCount = 0;

		for (Fish fish : FISH) {
			notedCount += noteItem(player, fish.rawId);
			notedCount += noteItem(player, fish.cookedId);
		}

		if (notedCount == 0) {
			npcsay(player, npc, "You do not have any raw or cooked fish I can note.");
			return;
		}

		player.message("The fishmonger packs " + notedCount + " fish into notes.");
	}

	private int noteItem(Player player, int itemId) {
		int count = player.getCarriedItems().getInventory().countId(itemId, Optional.of(false));
		if (count <= 0) {
			return 0;
		}

		if (player.getCarriedItems().remove(new Item(itemId, count, false)) == -1) {
			return 0;
		}

		player.getCarriedItems().getInventory().add(new Item(itemId, count, true));
		return count;
	}

	private void giveUnstacked(Player player, Item item) {
		if (item.getAmount() > 1 && !item.getDef(player.getWorld()).isStackable() && !item.getNoted()) {
			for (int i = 0; i < item.getAmount(); i++) {
				player.getCarriedItems().getInventory().add(new Item(item.getCatalogId(), 1, false));
			}
			return;
		}

		player.getCarriedItems().getInventory().add(item);
	}

	private boolean isFishmonger(Npc npc) {
		return npc != null && npc.getID() == NPC_ID;
	}

	private static final class Fish {
		private final int rawId;
		private final int cookedId;

		private Fish(ItemId raw, ItemId cooked) {
			this.rawId = raw.id();
			this.cookedId = cooked.id();
		}
	}
}
