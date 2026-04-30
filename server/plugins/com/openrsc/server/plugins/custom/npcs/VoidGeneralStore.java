package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.ItemId;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.net.rsc.ActionSender;
import com.openrsc.server.plugins.AbstractShop;

import static com.openrsc.server.plugins.Functions.multi;
import static com.openrsc.server.plugins.Functions.npcsay;
import static com.openrsc.server.plugins.Functions.say;

public final class VoidGeneralStore extends AbstractShop {

	private static final int VOID_QUARTERMASTER_ID = 843;

	// Stock intentionally uses only ItemDefs with isMembersOnly: 0.
	private final Shop shop = new Shop(new Shop(true, 12400, 130, 40, 3,
		new Item(ItemId.KNIFE.id(), 10),
		new Item(ItemId.TINDERBOX.id(), 10),
		new Item(ItemId.ROPE.id(), 5),
		new Item(ItemId.BREAD.id(), 10),
		new Item(ItemId.COOKEDMEAT.id(), 10),
		new Item(ItemId.BEER.id(), 10),
		new Item(ItemId.BLACK_CAPE.id(), 5),
		new Item(ItemId.BLACK_ROBE.id(), 5),
		new Item(ItemId.BLACK_WIZARDSHAT.id(), 5),
		new Item(ItemId.BLACK_SKIRT.id(), 5),
		new Item(ItemId.MONKS_ROBE_TOP.id(), 3),
		new Item(ItemId.MONKS_ROBE_BOTTOM.id(), 3),
		new Item(ItemId.STAFF.id(), 5),
		new Item(ItemId.MAGIC_STAFF.id(), 2),
		new Item(ItemId.AIR_RUNE.id(), 500),
		new Item(ItemId.WATER_RUNE.id(), 500),
		new Item(ItemId.EARTH_RUNE.id(), 500),
		new Item(ItemId.FIRE_RUNE.id(), 500),
		new Item(ItemId.MIND_RUNE.id(), 500),
		new Item(ItemId.BODY_RUNE.id(), 500),
		new Item(ItemId.CHAOS_RUNE.id(), 100),
		new Item(ItemId.BRONZE_ARROWS.id(), 200),
		new Item(ItemId.BLACK_DAGGER.id(), 2),
		new Item(ItemId.BLACK_MACE.id(), 1),
		new Item(ItemId.BLACK_SCIMITAR.id(), 1),
		new Item(ItemId.MEDIUM_BLACK_HELMET.id(), 1),
		new Item(ItemId.BLACK_CHAIN_MAIL_BODY.id(), 1),
		new Item(ItemId.BLACK_SQUARE_SHIELD.id(), 1),
		new Item(ItemId.SKULL.id(), 5),
		new Item(ItemId.SILK.id(), 10)),
		"Void Enclave", VOID_QUARTERMASTER_ID);

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isQuartermaster(npc);
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop};
	}

	@Override
	public boolean isMembers() {
		return false;
	}

	@Override
	public Shop getShop() {
		return shop;
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (!isQuartermaster(npc)) return;

		npcsay(player, npc, "If you came through the web, you came unprepared.");
		int option = multi(player, npc,
			"Show me your stock.",
			"What do you sell here?",
			"Never mind.");

		if (option == 0) {
			say(player, npc, "Show me your stock.");
			player.setAccessingShop(shop);
			ActionSender.showShop(player, shop);
		} else if (option == 1) {
			say(player, npc, "What do you sell here?");
			npcsay(player, npc,
				"Blades for the web. Robes for the altar.",
				"Runes, black steel, food, rope, and other things",
				"people remember they need after it is too late.");
		} else {
			npcsay(player, npc, "Then keep your pack light.");
		}
	}

	private boolean isQuartermaster(Npc npc) {
		return npc != null && npc.getID() == VOID_QUARTERMASTER_ID;
	}
}
