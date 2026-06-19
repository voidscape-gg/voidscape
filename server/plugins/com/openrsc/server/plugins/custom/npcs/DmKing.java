package com.openrsc.server.plugins.custom.npcs;

import com.openrsc.server.constants.NpcId;
import com.openrsc.server.content.voidarena.VoidArena;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.AttackNpcTrigger;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;
import com.openrsc.server.plugins.triggers.OpNpcTrigger;
import com.openrsc.server.plugins.triggers.PlayerRangeNpcTrigger;
import com.openrsc.server.plugins.triggers.SpellNpcTrigger;
import com.openrsc.server.plugins.triggers.TalkNpcTrigger;

public final class DmKing implements TalkNpcTrigger, OpNpcTrigger, AttackNpcTrigger,
	KillNpcTrigger, PlayerRangeNpcTrigger, SpellNpcTrigger {
	private static final String CHALLENGE_COMMAND = "Challenge";

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isDmKing(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (isStaticDmKing(npc)) {
			player.message("DM King waits for a ranked-kit challenger.");
			player.message("Right-click him and choose Challenge when you are ready.");
		} else if (isDynamicDmKing(player, npc)) {
			player.message("DM King is focused on the fight.");
		}
	}

	@Override
	public boolean blockOpNpc(Player player, Npc npc, String command) {
		return isStaticDmKing(npc) && command != null && command.equalsIgnoreCase(CHALLENGE_COMMAND);
	}

	@Override
	public void onOpNpc(Player player, Npc npc, String command) {
		if (blockOpNpc(player, npc, command)) {
			player.getWorld().getVoidArena().challengeDmKing(player);
		}
	}

	@Override
	public boolean blockAttackNpc(Player player, Npc npc) {
		VoidArena.AttackCheck check = player.getWorld().getVoidArena().checkDmKingNpcAction(player, npc, false);
		return check.applies && !check.allowed;
	}

	@Override
	public void onAttackNpc(Player player, Npc npc) {
		sendDeniedMessage(player, npc, false);
	}

	@Override
	public boolean blockKillNpc(Player player, Npc npc) {
		return isDynamicDmKing(player, npc);
	}

	@Override
	public void onKillNpc(Player player, Npc npc) {
		if (isDynamicDmKing(player, npc)) {
			player.getWorld().getVoidArena().handleDmKingNpcKilled(player, npc);
		}
	}

	@Override
	public boolean blockPlayerRangeNpc(Player player, Npc npc) {
		VoidArena.AttackCheck check = player.getWorld().getVoidArena().checkDmKingNpcAction(player, npc, true);
		return check.applies;
	}

	@Override
	public void onPlayerRangeNpc(Player player, Npc npc) {
		sendDeniedMessage(player, npc, true);
	}

	@Override
	public boolean blockSpellNpc(Player player, Npc npc) {
		VoidArena.AttackCheck check = player.getWorld().getVoidArena().checkDmKingNpcAction(player, npc, false);
		return check.applies && !check.allowed;
	}

	@Override
	public void onSpellNpc(Player player, Npc npc) {
		sendDeniedMessage(player, npc, false);
	}

	private void sendDeniedMessage(Player player, Npc npc, boolean missile) {
		VoidArena.AttackCheck check = player.getWorld().getVoidArena().checkDmKingNpcAction(player, npc, missile);
		if (check.applies && !check.allowed && check.message != null) {
			player.message(check.message);
		}
	}

	private boolean isDmKing(Npc npc) {
		return isStaticDmKing(npc) || npc != null && npc.getID() == NpcId.DM_KING_ARENA.id();
	}

	private boolean isStaticDmKing(Npc npc) {
		return npc != null && npc.getID() == NpcId.DM_KING.id();
	}

	private boolean isDynamicDmKing(Player player, Npc npc) {
		return npc != null && player != null
			&& npc.getID() == NpcId.DM_KING_ARENA.id()
			&& player.getWorld().getVoidArena().isDmKingChallengeNpc(npc);
	}
}
