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
	private static final String[] TALK_LINES = {
		"Ah, a challenger. How frightfully quaint.",
		"You've come to face me? Splendid. I do so enjoy a good laugh.",
		"Charles. Sir Charles. King of this arena, and shortly, the bane of your afternoon.",
		"Do come in. Mind the bloodstains, they're mostly from people like you.",
		"I'd ask your name, but I shan't remember it past the first round.",
		"You reek of ambition and cheap armour. How charming.",
		"Welcome, welcome. You're just in time to lose magnificently.",
		"One does meet the most interesting peasants in this line of work.",
		"Oh good, fresh meat. I was beginning to tire of winning.",
		"You may address me as 'Your Grace,' or simply 'sir,' or, more likely, 'mercy.'",
		"I've been the champion here longer than you've owned proper shoes.",
		"A duel? With me? How wonderfully optimistic of you.",
		"Step forward, then. The throne doesn't defend itself. Though frankly, it hardly needs to.",
		"You've heard of me. Everyone has. I'm rather the point of this place.",
		"Do try to make this entertaining. I get so bored thrashing amateurs.",
		"I have never lost. Not once. It's become something of a personality trait.",
		"They built this arena around me, you know. I'm the main attraction.",
		"My footwork alone is worth more than your entire estate.",
		"I duel before breakfast, after breakfast, and occasionally during, for the digestion.",
		"There are gravestones in this arena that simply read 'challenged Charles.'",
		"Talent is inherited, darling. And I inherited rather a lot of it.",
		"I could defeat you blindfolded, but then I couldn't watch your face.",
		"Modesty is for people with something to be modest about.",
		"I once won a duel so decisively the loser took up gardening. Far safer.",
		"My weakest hand still beats your strongest day.",
		"Skill, breeding, and impeccable cheekbones. I have all three.",
		"I don't sweat, dear boy. I glisten, victoriously.",
		"Every champion before me retired the moment I arrived. Wise of them.",
		"I've forgotten more about combat than you'll ever clumsily attempt.",
		"The crown isn't a trophy. It's simply a fact, and the fact is me.",
		"I duel the way other men breathe. Effortlessly, and rather better than you.",
		"Were I any more gifted, it would frankly be unfair. Oh wait, it is.",
		"I've slain men twice your size and half your stupidity.",
		"My reputation precedes me. As will my blade, shortly.",
		"You'll tell your grandchildren about this. The day a god deigned to thrash you.",
		"I'm not arrogant. I'm simply correct, loudly and often.",
		"I've a wall of skulls at home, all beautifully labelled. Yours wants a nice frame.",
		"Practice? I haven't practised since I was six. I peaked, and stayed there.",
		"Defeat is a foreign concept to me. Like manual labour, or your tailor.",
		"I am, quite simply, the finest blade these lands have ever produced. Ask anyone. Ask the gravestones.",
		"Are you still here? I've quite finished being impressed.",
		"I do wish the help would announce challengers properly. With trumpets.",
		"Lovely weather for a thrashing, isn't it?",
		"I've a portrait being painted, did you know? Three of them. I'm difficult to capture.",
		"Was there something else, or did you simply wish to bask?",
		"One grows weary of adoration. Said no one, because one doesn't. Carry on.",
		"Do mind the throne. People keep wanting to sit in it. The audacity.",
		"I'm told humility is a virtue. Sounds dreadful. Wouldn't know.",
		"Run along and train, little one. Come back when you're a marginally better tragedy.",
		"If you're not here to lose, I haven't the faintest idea what you're for.",
		"Charles. King. Undefeated. That's the whole story, really. You're just a footnote."
	};

	@Override
	public boolean blockTalkNpc(Player player, Npc npc) {
		return isStaticDmKing(npc);
	}

	@Override
	public void onTalkNpc(Player player, Npc npc) {
		if (isStaticDmKing(npc)) {
			npcsay(player, npc, nextTaunt(player));
			player.message("Right-click him and choose Challenge when you are ready.");
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
		int total = TALK_LINES.length;
		int choice = ThreadLocalRandom.current().nextInt(total);
		int last = player.getAttribute(LAST_TAUNT_ATTRIBUTE, -1);
		if (total > 1 && last >= 0) {
			choice = ThreadLocalRandom.current().nextInt(total - 1);
			if (choice >= last) {
				choice++;
			}
		}
		player.setAttribute(LAST_TAUNT_ATTRIBUTE, choice);
		return TALK_LINES[choice];
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
