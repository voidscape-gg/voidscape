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

import java.util.concurrent.ThreadLocalRandom;

import static com.openrsc.server.plugins.Functions.npcsay;

public final class DmKing implements TalkNpcTrigger, OpNpcTrigger, AttackNpcTrigger,
	KillNpcTrigger, PlayerRangeNpcTrigger, SpellNpcTrigger {
	private static final String CHALLENGE_COMMAND = "Challenge";
	private static final String LAST_TAUNT_ATTRIBUTE = "dmking_last_taunt";
	private static final String[] TAUNT_OPENERS = {
		"I have ended longer streaks during lag spikes.",
		"I can hear your food count panicking already.",
		"You brought hope into my cage. Bold mistake.",
		"My warmup clicks are cleaner than your best round.",
		"I do not predict mistakes. I schedule them.",
		"The arena keeps score because mercy got boring.",
		"Your rune plate is dressed up for a funeral.",
		"I have eaten swordfish with better timing than your switches.",
		"Your prayer book opens slower than my victory lap.",
		"I cast between your thoughts.",
		"Every challenger thinks they found the rhythm.",
		"I have seen panic-eating with more dignity.",
		"Your best combo is my idle animation.",
		"I do not need luck. I brought math.",
		"That rune 2h looks heavier than your confidence.",
		"I have perfect ticks and a terrible attitude.",
		"The cage is not locked to keep me in.",
		"You are not undergeared, just underprepared.",
		"My Fire Blast has better manners than your footwork.",
		"You are about to donate another lesson to history.",
		"Your clicks have a combat level of three.",
		"I can smell the missed eat from here.",
		"I have beaten people before their pot boosted.",
		"The only random part of this fight is your plan.",
		"I hope your recorder is on.",
		"My prayers last longer than your confidence.",
		"I have a 2h and an appointment with your hitpoints.",
		"You look like a highlight reel in reverse.",
		"I respect the courage. I do not respect the timing.",
		"I have seen bank standers with sharper instincts.",
		"You walked in like a challenger and clicked like a clue scroll.",
		"Bring your best. I need something to test my boredom."
	};
	private static final String[] TAUNT_FINISHES = {
		"Try not to make my record look padded.",
		"I will leave you enough time to type excuses.",
		"Your food will be gone before my hair moves.",
		"Even the Void Knight thinks this is one-sided.",
		"I will sip once and still outwork your whole inventory.",
		"When you panic, do it on tick.",
		"The spectators deserve cleaner mechanics than yours.",
		"You may challenge me again after the lesson."
	};

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isDmKing(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (isStaticDmKing(npc)) {
			npcsay(player, npc, nextTaunt(player));
			player.message("Right-click him and choose Challenge when you are ready.");
		} else if (isDynamicDmKing(player, npc)) {
			npcsay(player, npc, nextTaunt(player));
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

	private String nextTaunt(Player player) {
		int total = TAUNT_OPENERS.length * TAUNT_FINISHES.length;
		int choice = ThreadLocalRandom.current().nextInt(total);
		int last = player.getAttribute(LAST_TAUNT_ATTRIBUTE, -1);
		if (total > 1 && last >= 0) {
			choice = ThreadLocalRandom.current().nextInt(total - 1);
			if (choice >= last) {
				choice++;
			}
		}
		player.setAttribute(LAST_TAUNT_ATTRIBUTE, choice);
		return TAUNT_OPENERS[choice / TAUNT_FINISHES.length]
			+ " " + TAUNT_FINISHES[choice % TAUNT_FINISHES.length];
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
