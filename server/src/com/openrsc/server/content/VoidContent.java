package com.openrsc.server.content;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;

public final class VoidContent {
	public static final double VOID_SCIMITAR_MELEE_MULTIPLIER = 1.15D;
	public static final double VOID_MACE_MELEE_MULTIPLIER = 1.20D;
	public static final double VOID_BOW_RANGED_MULTIPLIER = 1.15D;
	public static final double VOID_AMULET_STACKABLE_DROP_MULTIPLIER = 1.50D;

	private VoidContent() {
	}

	public static boolean isVoidNpc(Mob mob) {
		return mob instanceof Npc && isVoidNpc(((Npc) mob).getID());
	}

	public static boolean isVoidNpc(int npcId) {
		return npcId == NpcId.VOID_KNIGHT.id()
			|| npcId == NpcId.VOID_KNIGHT_ARENA.id()
			|| npcId == NpcId.VOID_COLOSSUS.id()
			|| npcId == NpcId.VOID_KNIGHT_VOIDBORN.id()
			|| npcId == NpcId.VOID_SPIDER.id()
			|| npcId == NpcId.VOID_GIANT.id()
			|| npcId == NpcId.VOID_WOLF.id()
			|| npcId == NpcId.VOID_DEMON.id()
			|| npcId == NpcId.VOID_OGRE.id()
			|| npcId == NpcId.VOID_WIZARD.id()
			|| npcId == NpcId.VOID_UNICORN.id();
	}
}
