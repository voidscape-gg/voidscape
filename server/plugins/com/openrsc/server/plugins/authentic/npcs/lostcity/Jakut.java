package com.openrsc.server.plugins.authentic.npcs.lostcity;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.Shop;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.world.World;
import com.openrsc.server.plugins.AbstractShop;

import static com.openrsc.server.plugins.Functions.*;

public final class Jakut extends AbstractShop {

	private final Shop shop = new Shop(false, 3000, 100, 60, 2);

	@Override
	public void onTalkNpc(Player player, final Npc n) {
		npcsay(player, n, "I don't sell dragon swords anymore",
			"Try the sword shop in Varrock");
	}

	@Override
	public boolean blockTalkNpc(Player player, Npc n) {
		return n.getID() == NpcId.JAKUT.id();
	}

	@Override
	public Shop[] getShops(World world) {
		return new Shop[]{shop};
	}

	@Override
	public boolean isMembers() {
		return true;
	}

	@Override
	public Shop getShop() {
		return shop;
	}
}
